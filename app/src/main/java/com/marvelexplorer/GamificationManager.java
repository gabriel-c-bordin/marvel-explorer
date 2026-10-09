package com.marvelexplorer;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Local player profile: daily mission rewards, XP and level. */
public final class GamificationManager {
    public static final String CHARACTERS = "characters";
    public static final String BATTLE = "battle";
    public static final String TEAMS = "teams";
    public static final String STORIES = "stories";
    public static final int XP_PER_LEVEL = 250;

    public static final String DEFAULT_TITLE = "Viajante Interdimensional sem GPS";
    public static final String TITLE_WISDOM = "Portador da Sabedoria";
    public static final String TITLE_MEMORY = "Mestre da Memória";
    public static final String TITLE_REFLEX = "Reflexos do Multiverso";
    private static final String[] TITLE_IDS = {"wisdom", "memory", "reflex"};
    private static final String[] TITLE_NAMES = {TITLE_WISDOM, TITLE_MEMORY, TITLE_REFLEX};

    private static final String PREFS = "nexus_player_profile";

    private GamificationManager() {}

    public static int completeMission(Context context, String mission) {
        SharedPreferences prefs = preferences(context);
        String today = today();
        String dateKey = "daily_" + mission;
        if (today.equals(prefs.getString(dateKey, ""))) return 0;
        int reward = rewardFor(mission);
        prefs.edit()
                .putString(dateKey, today)
                .putInt("total_xp", prefs.getInt("total_xp", 0) + reward)
                .putInt("total_missions", prefs.getInt("total_missions", 0) + 1)
                .putBoolean("unlocked_" + mission, true)
                .apply();
        FirebaseProfileRepository.syncProgress(context);
        return reward;
    }

    public static int completeAndNotify(Context context, String mission) {
        int previousLevel = level(context);
        int reward = completeMission(context, mission);
        if (reward <= 0) return 0;
        Toast.makeText(context, context.getString(R.string.game_mission_toast, reward),
                Toast.LENGTH_SHORT).show();
        int currentLevel = level(context);
        if (currentLevel > previousLevel) {
            Toast.makeText(context, context.getString(R.string.game_level_up, currentLevel),
                    Toast.LENGTH_LONG).show();
        }
        return reward;
    }

    public static boolean completedToday(Context context, String mission) {
        return today().equals(preferences(context).getString("daily_" + mission, ""));
    }

    public static int totalXp(Context context) {
        return preferences(context).getInt("total_xp", 0);
    }

    public static int level(Context context) {
        return totalXp(context) / XP_PER_LEVEL + 1;
    }

    public static int levelProgress(Context context) {
        return totalXp(context) % XP_PER_LEVEL;
    }

    public static int unlockedMissionTypes(Context context) {
        SharedPreferences prefs = preferences(context);
        int count = 0;
        for (String mission : new String[]{CHARACTERS, BATTLE, TEAMS, STORIES}) {
            if (prefs.getBoolean("unlocked_" + mission, false)) count++;
        }
        return count;
    }

    public static int totalMissions(Context context) {
        return preferences(context).getInt("total_missions", 0);
    }

    public static int rewardFor(String mission) {
        if (BATTLE.equals(mission)) return 100;
        if (TEAMS.equals(mission) || STORIES.equals(mission)) return 75;
        return 50;
    }

    public static String selectedTitle(Context context) {
        String title = FirebaseProfileRepository.profilePreferences(context).getString("title", "");
        if (title == null || title.trim().isEmpty()) return DEFAULT_TITLE;
        for (String unlocked : unlockedTitles(context)) if (unlocked.equals(title)) return title;
        return DEFAULT_TITLE;
    }

    public static List<String> unlockedTitles(Context context) {
        List<String> result = new ArrayList<>();
        result.add(DEFAULT_TITLE);
        SharedPreferences prefs = preferences(context);
        for (int i = 0; i < TITLE_IDS.length; i++) {
            if (prefs.getBoolean("title_unlocked_" + TITLE_IDS[i], false)) result.add(TITLE_NAMES[i]);
        }
        return result;
    }

    public static boolean isTitleUnlocked(Context context, String id) {
        for (String titleId : TITLE_IDS) if (titleId.equals(id)) {
            return preferences(context).getBoolean("title_unlocked_" + id, false);
        }
        return false;
    }

    /** Unlocks a game title once and syncs the achievement with the user's account. */
    public static boolean unlockTitle(Context context, String id) {
        for (int i = 0; i < TITLE_IDS.length; i++) {
            if (!TITLE_IDS[i].equals(id)) continue;
            SharedPreferences prefs = preferences(context);
            if (prefs.getBoolean("title_unlocked_" + id, false)) return false;
            prefs.edit().putBoolean("title_unlocked_" + id, true).apply();
            FirebaseProfileRepository.syncProgress(context);
            Toast.makeText(context, "Novo título conquistado: " + TITLE_NAMES[i], Toast.LENGTH_LONG).show();
            return true;
        }
        return false;
    }

    static Map<String, Object> progressSnapshot(Context context) {
        SharedPreferences prefs = preferences(context);
        Map<String, String> completedToday = new HashMap<>();
        Map<String, Boolean> unlocked = new HashMap<>();
        for (String mission : new String[]{CHARACTERS, BATTLE, TEAMS, STORIES}) {
            String date = prefs.getString("daily_" + mission, "");
            if (!date.isEmpty()) completedToday.put(mission, date);
            unlocked.put(mission, prefs.getBoolean("unlocked_" + mission, false));
        }
        Map<String, Object> progress = new HashMap<>();
        progress.put("xp", prefs.getInt("total_xp", 0));
        progress.put("totalMissions", prefs.getInt("total_missions", 0));
        progress.put("completedToday", completedToday);
        progress.put("unlockedMissions", unlocked);
        Map<String, Boolean> titles = new HashMap<>();
        for (String id : TITLE_IDS) titles.put(id, prefs.getBoolean("title_unlocked_" + id, false));
        progress.put("titleAchievements", titles);
        return progress;
    }

    static void restoreProgress(Context context, Map<String, Object> data) {
        SharedPreferences.Editor editor = preferences(context).edit();
        Object xp = data.get("xp");
        Object missions = data.get("totalMissions");
        if (xp instanceof Number) editor.putInt("total_xp", ((Number) xp).intValue());
        if (missions instanceof Number) editor.putInt("total_missions", ((Number) missions).intValue());
        Object datesValue = data.get("completedToday");
        if (datesValue instanceof Map) {
            Map<?, ?> dates = (Map<?, ?>) datesValue;
            for (String mission : new String[]{CHARACTERS, BATTLE, TEAMS, STORIES}) {
                Object date = dates.get(mission);
                if (date instanceof String) editor.putString("daily_" + mission, (String) date);
            }
        }
        Object unlockedValue = data.get("unlockedMissions");
        if (unlockedValue instanceof Map) {
            Map<?, ?> unlocked = (Map<?, ?>) unlockedValue;
            for (String mission : new String[]{CHARACTERS, BATTLE, TEAMS, STORIES}) {
                Object value = unlocked.get(mission);
                if (value instanceof Boolean) editor.putBoolean("unlocked_" + mission, (Boolean) value);
            }
        }
        Object titlesValue = data.get("titleAchievements");
        if (titlesValue instanceof Map) {
            Map<?, ?> titles = (Map<?, ?>) titlesValue;
            for (String id : TITLE_IDS) {
                Object value = titles.get(id);
                if (Boolean.TRUE.equals(value)) editor.putBoolean("title_unlocked_" + id, true);
            }
        }
        editor.apply();
    }

    private static SharedPreferences preferences(Context context) {
        Context app = context.getApplicationContext();
        String uid = FirebaseProfileRepository.currentUid(app);
        String scopedName = uid == null ? PREFS : PREFS + "_" + uid;
        return app.getSharedPreferences(scopedName, Context.MODE_PRIVATE);
    }

    /** Moves progress from the original device-wide preferences into the first authenticated profile. */
    static void migrateLegacyProgress(Context context, String uid) {
        Context app = context.getApplicationContext();
        SharedPreferences markers = app.getSharedPreferences("player_profile_migrations", Context.MODE_PRIVATE);
        if (markers.contains("legacy_progress_owner")) return;
        SharedPreferences legacy = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences target = app.getSharedPreferences(PREFS + "_" + uid, Context.MODE_PRIVATE);
        if (!target.contains("total_xp")) {
            SharedPreferences.Editor editor = target.edit();
            for (java.util.Map.Entry<String, ?> entry : legacy.getAll().entrySet()) {
                Object value = entry.getValue();
                if (value instanceof String) editor.putString(entry.getKey(), (String) value);
                else if (value instanceof Integer) editor.putInt(entry.getKey(), (Integer) value);
                else if (value instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) value);
                else if (value instanceof Long) editor.putLong(entry.getKey(), (Long) value);
                else if (value instanceof Float) editor.putFloat(entry.getKey(), (Float) value);
            }
            editor.apply();
        }
        markers.edit().putString("legacy_progress_owner", uid).apply();
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }
}
