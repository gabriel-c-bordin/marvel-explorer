package com.marvelexplorer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.text.TextWatcher;
import android.text.Editable;

import androidx.core.content.FileProvider;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProfileActivity extends Activity {
    private static final int REQUEST_GALLERY = 3101;
    private static final int REQUEST_CAMERA = 3102;
    private static final String KEY_PHOTO_URI = "photo_uri";
    private static final String STATE_CAMERA_URI = "pending_camera_uri";
    private static final String STATE_CAMERA_PATH = "pending_camera_path";

    private EditText nameField;
    private TextView titleField;
    private TextView raceField;
    private EditText teamField;
    private ImageView photoView;
    private Uri pendingCameraUri;
    private File pendingCameraFile;
    private SharedPreferences profilePrefs;
    private FirebaseAuth firebaseAuth;
    private FirebaseAuth.AuthStateListener authListener;
    private boolean loadingProfile;
    private boolean profileDirty;
    private boolean profileLoaded;
    private String profileUid;
    private final ExecutorService photoExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int photoLoadToken;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BottomNavigation.prepareWindow(this);
        setContentView(R.layout.activity_profile);
        profilePrefs = FirebaseProfileRepository.profilePreferences(this);
        nameField = findViewById(R.id.profileName);
        titleField = findViewById(R.id.profileTitle);
        ((EditText) titleField).setKeyListener(null);
        titleField.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.arrow_down_float, 0);
        raceField = findViewById(R.id.profileRace);
        teamField = findViewById(R.id.profileTeam);
        photoView = findViewById(R.id.profilePhoto);
        attachDirtyWatcher(nameField);
        attachDirtyWatcher(teamField);
        if (savedInstanceState != null) {
            String pending = savedInstanceState.getString(STATE_CAMERA_URI);
            if (pending != null) pendingCameraUri = Uri.parse(pending);
            String pendingPath = savedInstanceState.getString(STATE_CAMERA_PATH);
            if (pendingPath != null) pendingCameraFile = new File(pendingPath);
        }

        findViewById(R.id.profileChangePhoto).setOnClickListener(v -> showPhotoOptions());
        titleField.setOnClickListener(v -> chooseTitle());
        titleField.setOnLongClickListener(v -> { chooseTitle(); return true; });
        raceField.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.arrow_down_float, 0);
        raceField.setOnClickListener(v -> chooseRace());
        findViewById(R.id.profileSave).setOnClickListener(v -> saveProfile());
        attachFirebaseProfile();
        BottomNavigation.attach(this, 0);
    }

    private void attachFirebaseProfile() {
        if (!FirebaseProfileRepository.isConfigured(this)) return;
        firebaseAuth = FirebaseAuth.getInstance();
        authListener = auth -> {
            FirebaseUser user = auth.getCurrentUser();
            if (user == null) {
                setSyncStatus(FirebaseProfileRepository.authenticationFailed(this)
                        ? "Sessão não iniciada · confira se o provedor anônimo está ativo."
                        : "Conectando à sessão do Firebase…", 0xFF91AABD);
                return;
            }
            String uid = user.getUid();
            FirebaseProfileRepository.prepareUserData(this, uid);
            if (!uid.equals(profileUid)) {
                profileUid = uid;
                profilePrefs = FirebaseProfileRepository.profilePreferences(this);
                if (!profileDirty) loadLocalProfile();
            }
            setSyncStatus("Perfil vinculado à conta do Firebase.", 0xFF45E69C);
            FirebaseProfileRepository.loadProfile(this, uid, ignored -> {
                if (isFinishing() || isDestroyed()) return;
                if (!profileDirty) loadLocalProfile();
                renderProgress();
                renderAchievements();
                renderPhoto();
            });
        };
        firebaseAuth.addAuthStateListener(authListener);
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        if (pendingCameraUri != null) {
            outState.putString(STATE_CAMERA_URI, pendingCameraUri.toString());
            if (pendingCameraFile != null) outState.putString(STATE_CAMERA_PATH, pendingCameraFile.getAbsolutePath());
        }
        super.onSaveInstanceState(outState);
    }

    @Override protected void onResume() {
        super.onResume();
        String uid = FirebaseProfileRepository.currentUid(this);
        if (!profileLoaded || (uid == null ? profileUid != null : !uid.equals(profileUid))) {
            profileUid = uid;
            profilePrefs = FirebaseProfileRepository.profilePreferences(this);
            if (!profileDirty) loadLocalProfile();
            profileLoaded = true;
        }
        renderProgress();
        renderAchievements();
        renderPhoto();
        if (!FirebaseProfileRepository.isConfigured(this)) {
            setSyncStatus("Modo local · adicione o arquivo google-services.json para sincronizar.", 0xFF91AABD);
        } else if (FirebaseProfileRepository.currentUid(this) == null) {
            setSyncStatus(FirebaseProfileRepository.authenticationFailed(this)
                    ? "Sessão não iniciada · confira se o provedor anônimo está ativo."
                    : "Conectando à sessão do Firebase…", 0xFF91AABD);
            FirebaseProfileRepository.initialize(this);
        }
    }

    private void loadLocalProfile() {
        loadingProfile = true;
        nameField.setText(profilePrefs.getString("name", ""));
        String selectedTitle = GamificationManager.selectedTitle(this);
        titleField.setText(selectedTitle);
        if (!selectedTitle.equals(profilePrefs.getString("title", ""))) {
            profilePrefs.edit().putString("title", selectedTitle).apply();
        }
        raceField.setText(profilePrefs.getString("race", "Não definida"));
        teamField.setText(profilePrefs.getString("team", ""));
        loadingProfile = false;
    }

    private void attachDirtyWatcher(EditText field) {
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!loadingProfile) profileDirty = true;
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void chooseTitle() {
        java.util.List<String> titles = GamificationManager.unlockedTitles(this);
        String[] options = titles.toArray(new String[0]);
        int selected = Math.max(0, titles.indexOf(titleField.getText().toString()));
        new AlertDialog.Builder(this)
                .setTitle("Títulos conquistados")
                .setSingleChoiceItems(options, selected, (dialog, which) -> {
                    titleField.setText(options[which]);
                    profileDirty = true;
                    renderProgress();
                    dialog.dismiss();
                })
                .setNegativeButton("Fechar", null)
                .show();
    }

    private void chooseRace() {
        String[] options = {"Não definida", "Humano", "Asgardiano", "Mutante", "Inumano",
                "Kree", "Skrull", "Atlante", "Eterno", "Simbionte", "Celestial", "Outra espécie"};
        int selected = Math.max(0, java.util.Arrays.asList(options).indexOf(raceField.getText().toString()));
        new AlertDialog.Builder(this)
                .setTitle("Raça ou espécie")
                .setSingleChoiceItems(options, selected, (dialog, which) -> {
                    raceField.setText(options[which]);
                    profileDirty = true;
                    dialog.dismiss();
                })
                .setNegativeButton("Fechar", null)
                .show();
    }

    private void saveProfile() {
        String name = nameField.getText().toString().trim();
        String selectedTitle = titleField.getText().toString().trim();
        if (!GamificationManager.unlockedTitles(this).contains(selectedTitle)) {
            selectedTitle = GamificationManager.DEFAULT_TITLE;
            titleField.setText(selectedTitle);
        }
        if (name.isEmpty()) {
            nameField.setError("Informe seu nome de agente.");
            nameField.requestFocus();
            return;
        }
        profilePrefs.edit()
                .putString("name", name)
                .putString("title", selectedTitle)
                .putString("race", raceField.getText().toString().trim())
                .putString("team", teamField.getText().toString().trim())
                .apply();
        profileDirty = false;
        setSyncStatus(FirebaseProfileRepository.currentUid(this) == null
                ? FirebaseProfileRepository.isConfigured(this)
                    ? "Salvo neste aparelho · aguardando a sessão do Firebase."
                    : "Salvo neste aparelho · configure o Firebase para sincronizar."
                : "Salvo neste aparelho · sincronizando com o Firebase…", 0xFF91AABD);
        Toast.makeText(this, "Perfil salvo neste aparelho.", Toast.LENGTH_SHORT).show();
        FirebaseProfileRepository.saveProfile(this, name,
                selectedTitle,
                raceField.getText().toString().trim(),
                teamField.getText().toString().trim(),
                profilePrefs.getString("photoUrl", ""),
                synced -> {
                    if (isFinishing() || isDestroyed()) return;
                    setSyncStatus(synced ? "Perfil sincronizado com sua conta Firebase."
                            : "Perfil salvo neste aparelho · sincronização pendente.",
                            synced ? 0xFF45E69C : 0xFF91AABD);
                });
        if (FirebaseProfileRepository.isConfigured(this) && FirebaseProfileRepository.currentUid(this) == null) {
            FirebaseProfileRepository.initialize(this);
        }
    }

    private void renderProgress() {
        int xp = GamificationManager.totalXp(this);
        int level = GamificationManager.level(this);
        String title = titleField.getText().toString().trim();
        if (title.isEmpty()) title = GamificationManager.selectedTitle(this);
        ((TextView) findViewById(R.id.profileLevel)).setText(String.format(
                java.util.Locale.getDefault(), "NÍVEL %02d · %s", level, title));
        ((TextView) findViewById(R.id.profileXp)).setText(getString(R.string.game_xp, xp));
        ProgressBar progress = findViewById(R.id.profileProgress);
        progress.setMax(GamificationManager.XP_PER_LEVEL);
        progress.setProgress(GamificationManager.levelProgress(this));
        ((TextView) findViewById(R.id.profileMissions)).setText(
                getString(R.string.profile_missions, GamificationManager.totalMissions(this)));
    }

    private void renderAchievements() {
        GridLayout achievements = findViewById(R.id.profileAchievements);
        achievements.removeAllViews();
        boolean firstMission = GamificationManager.totalMissions(this) > 0;
        boolean fullTeam = GamificationManager.unlockedMissionTypes(this) >= 4;
        boolean legend = GamificationManager.level(this) >= 8;
        addAchievement(achievements, "*", GamificationManager.TITLE_WISDOM,
                GamificationManager.isTitleUnlocked(this, "wisdom"));
        addAchievement(achievements, "M", GamificationManager.TITLE_MEMORY,
                GamificationManager.isTitleUnlocked(this, "memory"));
        addAchievement(achievements, "!", GamificationManager.TITLE_REFLEX,
                GamificationManager.isTitleUnlocked(this, "reflex"));
        addAchievement(achievements, "\u2726", "Primeira miss\u00e3o", firstMission);
        addAchievement(achievements, "\u2b21", "Equipe completa", fullTeam);
        addAchievement(achievements, "\u2605", "Lenda do multiverso", legend);
    }

    private void addAchievement(GridLayout parent, String icon, String title, boolean unlocked) {
        TextView badge = new TextView(this);
        badge.setText((unlocked ? icon : "\u25c7") + "\n" + title + (unlocked ? "" : "\nBloqueada"));
        badge.setGravity(Gravity.CENTER);
        badge.setTextSize(12);
        badge.setTextColor(unlocked ? 0xFF45E6FF : 0xFF91AABD);
        badge.setBackgroundResource(unlocked ? R.drawable.profile_badge_unlocked : R.drawable.profile_badge_locked);
        badge.setPadding(dp(10), dp(12), dp(10), dp(12));
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dp(112);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(dp(4), dp(4), dp(4), dp(4));
        parent.addView(badge, params);
    }

    private void showPhotoOptions() {
        String[] options = profilePrefs.contains(KEY_PHOTO_URI)
                ? new String[]{"Escolher da galeria", "Tirar uma foto", "Remover foto"}
                : new String[]{"Escolher da galeria", "Tirar uma foto"};
        new AlertDialog.Builder(this)
                .setTitle("Foto de perfil")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) chooseGalleryPhoto();
                    else if (which == 1) takePhoto();
                    else removePhoto();
                }).show();
    }

    private void chooseGalleryPhoto() {
        Intent intent = Build.VERSION.SDK_INT >= 33
                ? new Intent(android.provider.MediaStore.ACTION_PICK_IMAGES)
                : new Intent(Intent.ACTION_OPEN_DOCUMENT);
        if (Build.VERSION.SDK_INT < 33) intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_GALLERY);
        } catch (android.content.ActivityNotFoundException exception) {
            Toast.makeText(this, "Não foi possível abrir a galeria.", Toast.LENGTH_SHORT).show();
        }
    }

    private void takePhoto() {
        File directory = new File(getFilesDir(), "profile-photos");
        if (!directory.exists() && !directory.mkdirs()) {
            Toast.makeText(this, "Não foi possível preparar a câmera.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File image = File.createTempFile("profile_", ".jpg", directory);
            pendingCameraFile = image;
            pendingCameraUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", image);
            Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, pendingCameraUri);
            intent.setClipData(android.content.ClipData.newRawUri("profile photo", pendingCameraUri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_CAMERA);
        } catch (IOException | IllegalArgumentException | android.content.ActivityNotFoundException exception) {
            clearPendingCamera();
            Toast.makeText(this, "Não foi possível iniciar a câmera.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            if (requestCode == REQUEST_CAMERA && pendingCameraUri != null) {
                clearPendingCamera();
            }
            return;
        }
        if (requestCode == REQUEST_GALLERY && data != null && data.getData() != null) {
            Uri selected = data.getData();
            if (!"content".equalsIgnoreCase(selected.getScheme())) {
                Toast.makeText(this, "A galeria retornou um endereço de imagem inválido.", Toast.LENGTH_SHORT).show();
                return;
            }
            String mimeType;
            try {
                mimeType = getContentResolver().getType(selected);
            } catch (SecurityException exception) {
                Toast.makeText(this, "Não foi possível acessar essa imagem.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (mimeType == null || !mimeType.startsWith("image/")) {
                Toast.makeText(this, "Selecione um arquivo de imagem.", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                getContentResolver().takePersistableUriPermission(selected, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
                // Some document providers grant only temporary read access; display is still attempted now.
            }
            setPhoto(selected, null);
        } else if (requestCode == REQUEST_CAMERA && pendingCameraUri != null) {
            setPhoto(pendingCameraUri, pendingCameraFile);
            pendingCameraUri = null;
            pendingCameraFile = null;
        }
    }

    private void setPhoto(Uri uri, File localFile) {
        deleteStoredCameraPhoto();
        SharedPreferences.Editor editor = profilePrefs.edit().putString(KEY_PHOTO_URI, uri.toString()).putString("photoUrl", "");
        if (localFile != null) editor.putString("camera_photo_path", localFile.getAbsolutePath());
        else editor.remove("camera_photo_path");
        editor.apply();
        renderPhoto();
        Toast.makeText(this, "Foto definida neste aparelho.", Toast.LENGTH_SHORT).show();
        // A future Cloudinary upload can store its returned HTTPS URL in the Firebase profile document.
    }

    private void removePhoto() {
        deleteStoredCameraPhoto();
        profilePrefs.edit().remove(KEY_PHOTO_URI).remove("camera_photo_path").putString("photoUrl", "").apply();
        renderPhoto();
        FirebaseProfileRepository.saveProfile(this,
                profilePrefs.getString("name", ""),
                profilePrefs.getString("title", ""),
                profilePrefs.getString("race", ""),
                profilePrefs.getString("team", ""), "", null);
    }

    private void renderPhoto() {
        String saved = profilePrefs.getString(KEY_PHOTO_URI, null);
        int token = ++photoLoadToken;
        if (saved == null) {
            photoView.setImageResource(android.R.drawable.ic_menu_camera);
            photoView.setPadding(dp(19), dp(19), dp(19), dp(19));
            return;
        }
        photoExecutor.execute(() -> {
            Bitmap bitmap = decodePhoto(saved);
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed() || token != photoLoadToken) return;
                if (bitmap == null) {
                    deleteStoredCameraPhoto();
                    profilePrefs.edit().remove(KEY_PHOTO_URI).remove("camera_photo_path").apply();
                    photoView.setImageResource(android.R.drawable.ic_menu_camera);
                    photoView.setPadding(dp(19), dp(19), dp(19), dp(19));
                } else {
                    photoView.setPadding(0, 0, 0, 0);
                    photoView.setImageBitmap(bitmap);
                }
            });
        });
    }

    private Bitmap decodePhoto(String uriValue) {
        try (InputStream input = getContentResolver().openInputStream(Uri.parse(uriValue))) {
            if (input == null) return null;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(input, null, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            BitmapFactory.Options sample = new BitmapFactory.Options();
            sample.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, 512);
            try (InputStream decode = getContentResolver().openInputStream(Uri.parse(uriValue))) {
                return decode == null ? null : BitmapFactory.decodeStream(decode, null, sample);
            }
        } catch (Exception exception) {
            return null;
        }
    }

    private void setSyncStatus(String message, int color) {
        TextView status = findViewById(R.id.profileSyncStatus);
        if (status != null) {
            status.setText(message);
            status.setTextColor(color);
        }
    }

    private int sampleSize(int width, int height, int maxSide) {
        int sample = 1;
        while (width / sample > maxSide || height / sample > maxSide) sample *= 2;
        return sample;
    }

    private void deleteStoredCameraPhoto() {
        String path = profilePrefs.getString("camera_photo_path", null);
        if (path != null) new File(path).delete();
    }

    private void clearPendingCamera() {
        if (pendingCameraFile != null) pendingCameraFile.delete();
        pendingCameraFile = null;
        pendingCameraUri = null;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        if (firebaseAuth != null && authListener != null) firebaseAuth.removeAuthStateListener(authListener);
        photoExecutor.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
