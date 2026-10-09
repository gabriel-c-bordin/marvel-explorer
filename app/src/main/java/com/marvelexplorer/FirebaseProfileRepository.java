package com.marvelexplorer;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

/** UID-scoped profile and progression persistence. Local preferences remain the offline fallback. */
public final class FirebaseProfileRepository {
    private static final String PROFILE_PREFS = "player_profile_local";
    private static final String MIGRATION_PREFS = "player_profile_migrations";
    private static final String STATE_PREFS = "firebase_profile_state";
    private static final String COLLECTION_USERS = "users";
    private static boolean authRequestInFlight;

    public interface SaveCallback { void onComplete(boolean synced); }

    private FirebaseProfileRepository() {}

    public static void initialize(Context context) {
        Context app = context.getApplicationContext();
        try {
            if (FirebaseApp.getApps(app).isEmpty()) FirebaseApp.initializeApp(app);
            if (FirebaseApp.getApps(app).isEmpty()) return;
            FirebaseAuth auth = FirebaseAuth.getInstance();
            FirebaseUser user = auth.getCurrentUser();
            if (user != null) {
                app.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).edit().putString("auth", "ready").apply();
                activateUser(app, user.getUid());
                return;
            }
            synchronized (FirebaseProfileRepository.class) {
                if (authRequestInFlight) return;
                authRequestInFlight = true;
            }
            app.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).edit().putString("auth", "connecting").apply();
            auth.signInAnonymously().addOnCompleteListener(task -> {
                synchronized (FirebaseProfileRepository.class) { authRequestInFlight = false; }
                if (task.isSuccessful() && task.getResult() != null && task.getResult().getUser() != null) {
                    app.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).edit().putString("auth", "ready").apply();
                    activateUser(app, task.getResult().getUser().getUid());
                } else {
                    app.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).edit().putString("auth", "failed").apply();
                }
            });
        } catch (IllegalStateException ignored) {
            // No default Firebase app exists until the app receives its Firebase console configuration.
        }
    }

    public static boolean isConfigured(Context context) {
        try {
            return !FirebaseApp.getApps(context.getApplicationContext()).isEmpty();
        } catch (IllegalStateException exception) {
            return false;
        }
    }

    public static boolean authenticationFailed(Context context) {
        return "failed".equals(context.getApplicationContext()
                .getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).getString("auth", ""));
    }

    public static String currentUid(Context context) {
        if (!isConfigured(context)) return null;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user == null ? null : user.getUid();
    }

    public static SharedPreferences profilePreferences(Context context) {
        String uid = currentUid(context);
        if (uid == null) return context.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE);
        return preferencesForUid(context, uid);
    }

    public static SharedPreferences preferencesForUid(Context context, String uid) {
        return context.getSharedPreferences(PROFILE_PREFS + "_" + uid, Context.MODE_PRIVATE);
    }

    private static void activateUser(Context context, String uid) {
        prepareUserData(context, uid);
        loadProfile(context, uid, profile -> syncProgress(context));
    }

    public static void prepareUserData(Context context, String uid) {
        migrateLegacyProfile(context, uid);
        GamificationManager.migrateLegacyProgress(context, uid);
    }

    private static void migrateLegacyProfile(Context context, String uid) {
        SharedPreferences markers = context.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE);
        if (markers.contains("legacy_profile_owner")) return;
        SharedPreferences legacy = context.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE);
        SharedPreferences target = preferencesForUid(context, uid);
        SharedPreferences.Editor editor = target.edit();
        for (String key : new String[]{"name", "title", "race", "team", "photo_uri", "photoUrl", "camera_photo_path"}) {
            if (!target.contains(key) && legacy.contains(key)) {
                editor.putString(key, legacy.getString(key, ""));
            }
        }
        editor.apply();
        markers.edit().putString("legacy_profile_owner", uid).apply();
    }

    public static void loadProfile(Context context, String uid, java.util.function.Consumer<Map<String, Object>> callback) {
        if (!isConfigured(context)) { callback.accept(localProfile(context, uid)); return; }
        FirebaseFirestore.getInstance().collection(COLLECTION_USERS).document(uid).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        Map<String, Object> data = snapshot.getData();
                        if (data != null) {
                            applyToLocal(context, uid, data);
                            GamificationManager.restoreProgress(context, data);
                            syncProgress(context);
                        }
                        callback.accept(data == null ? localProfile(context, uid) : data);
                    } else {
                        syncProfile(context, uid, null);
                        callback.accept(localProfile(context, uid));
                    }
                })
                .addOnFailureListener(error -> callback.accept(localProfile(context, uid)));
    }

    public static void saveProfile(Context context, String name, String title, String race,
                                   String team, String photoUrl, SaveCallback callback) {
        String uid = currentUid(context);
        if (uid == null || !isConfigured(context)) {
            if (callback != null) callback.onComplete(false);
            return;
        }
        Map<String, Object> fields = new HashMap<>();
        fields.put("name", name);
        fields.put("title", title);
        fields.put("race", race);
        fields.put("team", team);
        fields.put("photoUrl", photoUrl == null ? "" : photoUrl);
        fields.put("updatedAt", FieldValue.serverTimestamp());
        FirebaseFirestore.getInstance().collection(COLLECTION_USERS).document(uid)
                .set(fields, SetOptions.merge())
                .addOnSuccessListener(unused -> { if (callback != null) callback.onComplete(true); })
                .addOnFailureListener(error -> { if (callback != null) callback.onComplete(false); });
    }

    public static void syncProgress(Context context) {
        String uid = currentUid(context);
        if (uid == null || !isConfigured(context)) return;
        Map<String, Object> fields = new HashMap<>();
        fields.put("xp", GamificationManager.totalXp(context));
        fields.put("level", GamificationManager.level(context));
        fields.put("title", GamificationManager.selectedTitle(context));
        fields.put("totalMissions", GamificationManager.totalMissions(context));
        fields.put("unlockedMissionTypes", GamificationManager.unlockedMissionTypes(context));
        fields.putAll(GamificationManager.progressSnapshot(context));
        fields.put("achievements", achievements(context));
        fields.put("updatedAt", FieldValue.serverTimestamp());
        FirebaseFirestore.getInstance().collection(COLLECTION_USERS).document(uid)
                .set(fields, SetOptions.merge());
    }

    private static void syncProfile(Context context, String uid, SaveCallback callback) {
        SharedPreferences local = preferencesForUid(context, uid);
        Map<String, Object> fields = new HashMap<>();
        fields.put("name", local.getString("name", ""));
        fields.put("title", GamificationManager.selectedTitle(context));
        fields.put("race", local.getString("race", ""));
        fields.put("team", local.getString("team", ""));
        fields.put("photoUrl", local.getString("photoUrl", ""));
        fields.put("xp", GamificationManager.totalXp(context));
        fields.put("level", GamificationManager.level(context));
        fields.put("totalMissions", GamificationManager.totalMissions(context));
        fields.put("unlockedMissionTypes", GamificationManager.unlockedMissionTypes(context));
        fields.putAll(GamificationManager.progressSnapshot(context));
        fields.put("achievements", achievements(context));
        fields.put("updatedAt", FieldValue.serverTimestamp());
        FirebaseFirestore.getInstance().collection(COLLECTION_USERS).document(uid)
                .set(fields, SetOptions.merge())
                .addOnSuccessListener(unused -> { if (callback != null) callback.onComplete(true); })
                .addOnFailureListener(error -> { if (callback != null) callback.onComplete(false); });
    }

    private static Map<String, Object> localProfile(Context context, String uid) {
        SharedPreferences local = preferencesForUid(context, uid);
        Map<String, Object> data = new HashMap<>();
        data.put("name", local.getString("name", ""));
        data.put("title", GamificationManager.selectedTitle(context));
        data.put("race", local.getString("race", ""));
        data.put("team", local.getString("team", ""));
        data.put("photoUrl", local.getString("photoUrl", ""));
        data.put("xp", GamificationManager.totalXp(context));
        data.put("level", GamificationManager.level(context));
        data.put("totalMissions", GamificationManager.totalMissions(context));
        data.put("achievements", achievements(context));
        data.putAll(GamificationManager.progressSnapshot(context));
        return data;
    }

    private static void applyToLocal(Context context, String uid, Map<String, Object> data) {
        SharedPreferences.Editor editor = preferencesForUid(context, uid).edit();
        putString(data, editor, "name");
        putString(data, editor, "title");
        putString(data, editor, "race");
        putString(data, editor, "team");
        putString(data, editor, "photoUrl");
        editor.apply();
    }

    private static void putString(Map<String, Object> data, SharedPreferences.Editor editor, String key) {
        Object value = data.get(key);
        if (value instanceof String) editor.putString(key, (String) value);
    }

    private static Map<String, Boolean> achievements(Context context) {
        Map<String, Boolean> result = new HashMap<>();
        result.put("firstMission", GamificationManager.totalMissions(context) > 0);
        result.put("fullRoster", GamificationManager.unlockedMissionTypes(context) >= 4);
        result.put("multiverseLegend", GamificationManager.level(context) >= 8);
        return result;
    }
}
