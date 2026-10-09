package com.marvelexplorer;

import android.app.Activity;
import android.os.Bundle;
import android.text.Html;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.View;

import com.google.mlkit.genai.common.FeatureStatus;
import com.marvelexplorer.data.CatalogItem;
import com.marvelexplorer.data.CatalogRepository;
import com.marvelexplorer.data.CharacterItem;
import com.marvelexplorer.data.ComicVineCatalogService;
import com.marvelexplorer.data.ComicVineRepository;
import com.marvelexplorer.data.OnDeviceMarvelAssistant;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Chat interface for NEXUS, grounded in Comic Vine when matching records are available. */
public class AssistantActivity extends Activity {
    public static final String EXTRA_SEED_QUERY = "assistant_seed_query";
    public static final String EXTRA_SEED_SECTION = "assistant_seed_section";
    private static final String STATE_HISTORY = "assistant_history";

    private final ExecutorService lookups = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "nexus-comicvine-lookup");
        thread.setDaemon(true);
        return thread;
    });
    private final ArrayList<String> history = new ArrayList<>();
    private OnDeviceMarvelAssistant ai;
    private ComicVineRepository characterRepository;
    private CatalogRepository catalogRepository;
    private LinearLayout messages;
    private ScrollView scroll;
    private EditText input;
    private Button send;
    private TextView status;
    private Button downloadButton;
    private boolean modelReady;
    private boolean busy;
    private String modelStatusText = "";
    private String seedQuery = "";
    private String seedSectionPath = "";
    private boolean battleMissionActive;
    private boolean currentRequestIsBattle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BottomNavigation.prepareWindow(this);
        setContentView(R.layout.activity_assistant);
        BottomNavigation.attach(this, 3);
        ai = new OnDeviceMarvelAssistant(this);
        seedQuery = getIntent().getStringExtra(EXTRA_SEED_QUERY);
        seedSectionPath = getIntent().getStringExtra(EXTRA_SEED_SECTION);
        if (seedQuery == null) seedQuery = "";
        if (seedSectionPath == null) seedSectionPath = "";
        battleMissionActive = seedQuery.toLowerCase(Locale.ROOT).contains("batalha");
        characterRepository = new ComicVineRepository(this);
        catalogRepository = new CatalogRepository(this);
        messages = findViewById(R.id.assistantMessages);
        scroll = findViewById(R.id.assistantScroll);
        input = findViewById(R.id.assistantInput);
        if (!seedQuery.trim().isEmpty()) input.setText(seedQuery);
        send = findViewById(R.id.assistantSend);
        status = findViewById(R.id.assistantStatus);
        downloadButton = findViewById(R.id.assistantDownloadButton);
        Button back = findViewById(R.id.assistantBack);
        Button characterSuggestion = findViewById(R.id.suggestionCharacter);
        Button teamSuggestion = findViewById(R.id.suggestionTeam);
        Button storySuggestion = findViewById(R.id.suggestionStory);

        back.setOnClickListener(v -> {
            ThemeSoundPlayer.play(this, ThemeSoundPlayer.CUE_OPEN);
            finish();
        });
        send.setOnClickListener(v -> submitInput());
        downloadButton.setOnClickListener(v -> prepareLocalModel());
        input.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEND
                    && actionId != EditorInfo.IME_ACTION_DONE) return false;
            submitInput();
            return true;
        });
        characterSuggestion.setOnClickListener(v -> sendMessage("Quem é Spider-Man?"));
        teamSuggestion.setOnClickListener(v -> sendMessage("Explique a equipe Avengers."));
        storySuggestion.setOnClickListener(v -> sendMessage("Sugira uma saga dos quadrinhos Marvel."));

        if (savedInstanceState != null) {
            ArrayList<String> restored = savedInstanceState.getStringArrayList(STATE_HISTORY);
            if (restored != null) {
                history.addAll(restored);
                renderHistory();
            }
        }
        if (history.isEmpty()) addBubble(getString(R.string.assistant_welcome), false, false);
        ThemeSoundPlayer.play(this, ThemeSoundPlayer.CUE_OPEN);
        status.setText(R.string.assistant_status_checking);
        ai.checkStatus((featureStatus, message) -> {
            modelReady = featureStatus == FeatureStatus.AVAILABLE;
            modelStatusText = message;
            downloadButton.setVisibility(featureStatus == FeatureStatus.DOWNLOADABLE
                    ? View.VISIBLE : View.GONE);
            if (modelReady) {
                status.setText(message);
            } else {
                status.setText(message);
                status.setContentDescription(message);
            }
        });
    }

    private void prepareLocalModel() {
        downloadButton.setEnabled(false);
        status.setText(R.string.assistant_status_checking);
        ai.downloadModel(new OnDeviceMarvelAssistant.DownloadListener() {
            @Override
            public void onProgress(long bytesDownloaded) {
                runOnUiThread(() -> {
                    long megabytes = Math.max(0L, bytesDownloaded / (1024L * 1024L));
                    status.setText(getString(R.string.assistant_model_download_progress, megabytes));
                });
            }

            @Override
            public void onComplete() {
                runOnUiThread(() -> {
                    modelReady = true;
                    modelStatusText = getString(R.string.assistant_status_ready);
                    status.setText(R.string.assistant_model_download_complete);
                    downloadButton.setVisibility(View.GONE);
                    ThemeSoundPlayer.play(AssistantActivity.this, ThemeSoundPlayer.CUE_CONFIRM);
                });
            }

            @Override
            public void onFailure(String message) {
                runOnUiThread(() -> {
                    modelReady = false;
                    status.setText(R.string.assistant_model_download_failed);
                    downloadButton.setEnabled(true);
                    downloadButton.setVisibility(View.VISIBLE);
                    status.setContentDescription(message);
                });
            }
        });
    }

    private void submitInput() {
        String question = input.getText().toString().trim();
        if (question.isEmpty()) {
            input.setError(getString(R.string.assistant_empty));
            return;
        }
        sendMessage(question);
    }

    private void sendMessage(String question) {
        if (busy) return;
        String normalizedQuestion = question == null ? "" : question.toLowerCase(Locale.ROOT);
        currentRequestIsBattle = battleMissionActive
                && (normalizedQuestion.contains("batalha") || normalizedQuestion.contains("compare"));
        busy = true;
        input.setText("");
        setComposerEnabled(false);
        ThemeSoundPlayer.play(this, ThemeSoundPlayer.CUE_SCAN);
        addBubble(question, true, false);
        history.add("Usuário: " + question);
        addBubble(getString(R.string.assistant_thinking), false, true);
        ArrayList<String> historySnapshot = new ArrayList<>(history);
        status.setText(R.string.assistant_thinking);

        lookups.execute(() -> {
            String evidence = lookupEvidence(question);
            if (modelReady) {
                ai.generate(question, historySnapshot, evidence, new OnDeviceMarvelAssistant.ResponseCallback() {
                    @Override
                    public void onResponse(String response) {
                        runOnUiThread(() -> finishAnswer(response, evidence, true));
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> finishAnswer(offlineAnswer(evidence), evidence, false));
                    }
                });
            } else {
                runOnUiThread(() -> finishAnswer(offlineAnswer(evidence), evidence, false));
            }
        });
    }

    private void finishAnswer(String answer, String evidence, boolean generated) {
        if (isFinishing() || isDestroyed()) return;
        removeThinkingBubble();
        String visibleAnswer = answer;
        if (!generated && !modelStatusText.isEmpty()) {
            status.setText(modelStatusText);
            status.setContentDescription(modelStatusText);
        } else if (generated) {
            status.setText(modelStatusText);
            status.setContentDescription(modelStatusText);
        }
        if (generated && evidence != null && !evidence.trim().isEmpty()) {
            visibleAnswer += "\n\n" + getString(R.string.assistant_data_source);
        }
        addBubble(visibleAnswer, false, false);
        history.add("NEXUS: " + visibleAnswer);
        trimHistory();
        if (currentRequestIsBattle && evidence != null && !evidence.trim().isEmpty()) {
            GamificationManager.completeAndNotify(this, GamificationManager.BATTLE);
            battleMissionActive = false;
        }
        currentRequestIsBattle = false;
        busy = false;
        setComposerEnabled(true);
        ThemeSoundPlayer.play(this, generated || (evidence != null && !evidence.isEmpty())
                ? ThemeSoundPlayer.CUE_CONFIRM : ThemeSoundPlayer.CUE_ALERT);
    }

    private String lookupEvidence(String question) {
        try {
            String normalized = normalize(question);
            String searchTerm = extractSearchTerm(question, normalized);
            if (normalized.contains("batalha") || normalized.contains("compare")) {
                return battleEvidence(normalized, BuildConfig.COMICVINE_API_KEY);
            }
            boolean followUp = normalized.contains("esse resultado") || normalized.contains("essa busca")
                    || normalized.contains("isso") || normalized.contains("ele")
                    || normalized.contains("ela") || normalized.contains("esse personagem");
            if (followUp && !seedQuery.trim().isEmpty()) {
                searchTerm = seedQuery;
                ComicVineCatalogService.Section seededSection = sectionForPath(seedSectionPath);
                if (seededSection != null) return catalogEvidence(seededSection, searchTerm,
                        BuildConfig.COMICVINE_API_KEY);
            }
            String key = BuildConfig.COMICVINE_API_KEY;
            if (normalized.contains("saga") || normalized.contains("arco")
                    || normalized.contains("historia")) {
                return catalogEvidence(ComicVineCatalogService.Section.STORY_ARCS, searchTerm, key);
            }
            if (normalized.contains("equipe") || normalized.contains("time")
                    || normalized.contains("vingador") || normalized.contains("avengers")
                    || normalized.contains("x-men") || normalized.contains("quarteto")) {
                return catalogEvidence(ComicVineCatalogService.Section.TEAMS, searchTerm, key);
            }
            if (normalized.contains("edição") || normalized.contains("quadrinho")
                    || normalized.contains("issue")) {
                // Issue records do not expose a consistent publisher relation for safe Marvel-only filtering.
                return "";
            }
            ComicVineRepository.PageResult result = characterRepository.searchMarvelCharacters(
                    searchTerm, 0, key);
            if (result.page.characters.isEmpty()) return "";
            StringBuilder context = new StringBuilder("Registros de personagens retornados pela Comic Vine:\n");
            int max = Math.min(4, result.page.characters.size());
            for (int i = 0; i < max; i++) {
                CharacterItem item = result.page.characters.get(i);
                context.append("- Nome: ").append(item.name)
                        .append("; nome real: ").append(valueOrUnknown(item.realName))
                        .append("; editora: ").append(valueOrUnknown(item.publisher))
                        .append("; primeira aparição: ").append(valueOrUnknown(item.firstAppearance))
                        .append("; resumo: ").append(shortText(item.summary, 420))
                        .append("; poderes listados: ").append(item.powers == null || item.powers.isEmpty()
                                ? "não informados" : joinFirst(item.powers, 5))
                        .append(".\n");
            }
            return context.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String battleEvidence(String normalized, String key) {
        ArrayList<String> names = new ArrayList<>();
        if (normalized.contains("homem aranha") || normalized.contains("homem-aranha")
                || normalized.contains("spider man")) names.add("Spider-Man");
        if (normalized.contains("wolverine") || normalized.contains("logan")) names.add("Wolverine");
        if (normalized.contains("homem de ferro") || normalized.contains("iron man")) names.add("Iron Man");
        if (normalized.contains("capitao america") || normalized.contains("captain america")) names.add("Captain America");
        if (normalized.contains("hulk")) names.add("Hulk");
        if (names.size() < 2) return "";
        StringBuilder evidence = new StringBuilder();
        for (String name : names) {
            try {
                ComicVineRepository.PageResult result = characterRepository.searchMarvelCharacters(name, 0, key);
                if (result.page.characters.isEmpty()) continue;
                CharacterItem character = result.page.characters.get(0);
                if (evidence.length() == 0) {
                    evidence.append("Participantes da batalha hipotética; comparação especulativa, sem vencedor oficial:\n");
                }
                evidence.append("- ").append(character.name).append(" (editora: ")
                        .append(valueOrUnknown(character.publisher)).append("; poderes listados: ")
                        .append(character.powers == null || character.powers.isEmpty()
                                ? "não informados" : joinFirst(character.powers, 6))
                        .append("; resumo: ").append(shortText(character.summary, 350)).append(").\n");
            } catch (Exception ignored) {
                // Keep any participant evidence that was successfully retrieved.
            }
        }
        return evidence.toString();
    }

    private String catalogEvidence(ComicVineCatalogService.Section section, String query, String key) {
        try {
            CatalogRepository.Result result = catalogRepository.search(section, query, 0, key);
            if (result.page.items.isEmpty()) return "";
            StringBuilder context = new StringBuilder("Registros da Comic Vine para ")
                    .append(section.label).append(":\n");
            int max = Math.min(4, result.page.items.size());
            for (int i = 0; i < max; i++) {
                CatalogItem item = result.page.items.get(i);
                context.append("- Nome: ").append(item.name)
                        .append("; editora: ").append(valueOrUnknown(item.publisher))
                        .append("; ").append(item.detailLabel).append(": ")
                        .append(valueOrUnknown(item.detailValue))
                        .append("; resumo: ").append(shortText(item.summary, 420)).append(".\n");
            }
            return context.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String offlineAnswer(String evidence) {
        if (evidence == null || evidence.trim().isEmpty()) {
            return getString(R.string.assistant_context_unavailable);
        }
        return getString(R.string.assistant_fallback_prefix) + evidence.trim()
                + "\n\nEste aparelho não tem o modelo generativo pronto. Os dados acima são uma consulta à Comic Vine, não uma análise gerada por IA.";
    }

    private String extractSearchTerm(String original, String normalized) {
        if (normalized.contains("sugira uma saga") || normalized.contains("indique uma saga")) return "Civil War";
        if (normalized.contains("homem-aranha") || normalized.contains("homem aranha")) return "Spider-Man";
        if (normalized.contains("vingadores")) return "Avengers";
        String result = original.trim();
        String[] prefixes = {"quem é", "quem e", "me fale sobre", "me fala sobre", "fale sobre",
                "fala sobre", "explique", "me explique", "conte sobre", "resuma", "resumo de",
                "informações sobre", "informacao sobre", "sugira uma saga", "sugira", "indique"};
        String lower = result.toLowerCase(Locale.ROOT);
        for (String prefix : prefixes) {
            if (lower.startsWith(prefix)) {
                result = result.substring(prefix.length()).trim();
                break;
            }
        }
        result = result.replaceAll("[?!.,;:]", " ")
                .replaceAll("(?i)\\b(da|do|das|dos|de|sobre|para|por|me|o|a|as|os|um|uma|na|no|na marvel|marvel)\\b", " ")
                .replaceAll("\\s+", " ").trim();
        return result.isEmpty() ? "Spider-Man" : result;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private ComicVineCatalogService.Section sectionForPath(String path) {
        if (path == null || path.isEmpty()) return null;
        for (ComicVineCatalogService.Section candidate : ComicVineCatalogService.Section.values()) {
            if (candidate.path.equals(path)) return candidate;
        }
        return null;
    }

    private String shortText(String html, int max) {
        String clean = html == null ? "" : Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString().trim();
        if (clean.isEmpty()) return "não informado";
        return clean.length() <= max ? clean : clean.substring(0, max) + "…";
    }

    private String valueOrUnknown(String value) {
        return value == null || value.trim().isEmpty() ? "não informado" : shortText(value, 160);
    }

    private String joinFirst(List<String> values, int limit) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.size() && i < limit; i++) {
            if (result.length() > 0) result.append(", ");
            result.append(values.get(i));
        }
        return result.toString();
    }

    private void addBubble(String value, boolean fromUser, boolean thinking) {
        TextView bubble = new TextView(this);
        bubble.setText(value);
        bubble.setTextSize(14);
        bubble.setTextColor(getColor(R.color.ink));
        bubble.setLineSpacing(dp(2), 1f);
        bubble.setPadding(dp(14), dp(11), dp(14), dp(11));
        bubble.setBackgroundResource(fromUser ? R.drawable.bubble_user : R.drawable.bubble_assistant);
        bubble.setMaxWidth(dp(330));
        bubble.setContentDescription((fromUser ? "Você: " : "NEXUS: ") + value);
        if (thinking) bubble.setTextColor(getColor(R.color.cyan_soft));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = fromUser ? Gravity.END : Gravity.START;
        params.setMargins(0, dp(4), 0, dp(8));
        messages.addView(bubble, params);
        bubble.setTag(thinking ? "thinking" : null);
        scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void removeThinkingBubble() {
        for (int i = messages.getChildCount() - 1; i >= 0; i--) {
            android.view.View child = messages.getChildAt(i);
            if ("thinking".equals(child.getTag())) {
                messages.removeViewAt(i);
                return;
            }
        }
    }

    private void renderHistory() {
        messages.removeAllViews();
        for (String entry : history) {
            boolean user = entry.startsWith("Usuário: ");
            String text = entry.substring(entry.indexOf(':') + 1).trim();
            addBubble(text, user, false);
        }
    }

    private void trimHistory() {
        while (history.size() > 12) history.remove(0);
    }

    private void setComposerEnabled(boolean enabled) {
        input.setEnabled(enabled);
        send.setEnabled(enabled);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putStringArrayList(STATE_HISTORY, history);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        lookups.shutdownNow();
        if (ai != null) ai.close();
        super.onDestroy();
    }
}
