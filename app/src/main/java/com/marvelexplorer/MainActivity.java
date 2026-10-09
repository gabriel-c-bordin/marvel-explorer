package com.marvelexplorer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.marvelexplorer.data.CharacterItem;
import com.marvelexplorer.data.ComicVineRepository;
import com.marvelexplorer.data.ComicVineDetailService;
import com.marvelexplorer.data.ComicVineService;
import com.marvelexplorer.data.ImageDownloader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int DISPLAY_PAGE_SIZE = 30;
    private final ExecutorService workers = Executors.newFixedThreadPool(4);
    private final List<CharacterItem> loadedCharacters = new ArrayList<>();
    private final List<CharacterItem> pendingCharacters = new ArrayList<>();
    private ComicVineRepository repository;
    private EditText searchInput;
    private LinearLayout resultsColumn;
    private TextView statusText;
    private ProgressBar progressBar;
    private Button nextPageButton;
    private Button searchButton;
    private int nextOffset;
    private boolean hasMore;
    private boolean loading;
    private String currentQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BottomNavigation.prepareWindow(this);
        setContentView(R.layout.activity_main);
        BottomNavigation.attach(this, -1);
        repository = new ComicVineRepository(this);
        searchInput = findViewById(R.id.searchInput);
        resultsColumn = findViewById(R.id.resultsColumn);
        statusText = findViewById(R.id.statusText);
        progressBar = findViewById(R.id.progressBar);
        nextPageButton = findViewById(R.id.nextPageButton);
        searchButton = findViewById(R.id.searchButton);
        TextView attribution = findViewById(R.id.attribution);
        Button exploreButton = findViewById(R.id.exploreButton);
        searchInput.setText(currentQuery);
        searchButton.setOnClickListener(v -> startNewSearch());
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;
            startNewSearch();
            return true;
        });
        nextPageButton.setOnClickListener(v -> loadPage(true));
        exploreButton.setOnClickListener(v -> {
            ThemeSoundPlayer.play(this, ThemeSoundPlayer.CUE_OPEN);
            startActivity(new Intent(this, CatalogActivity.class));
        });
        attribution.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://comicvine.gamespot.com/"))));
        loadPage(false);
    }

    private void startNewSearch() {
        if (loading) return;
        String entered = searchInput.getText().toString().trim();
        currentQuery = entered;
        ThemeSoundPlayer.play(this, ThemeSoundPlayer.CUE_SCAN);
        nextOffset = 0;
        hasMore = false;
        loadedCharacters.clear();
        pendingCharacters.clear();
        resultsColumn.removeAllViews();
        loadPage(false);
    }

    private void loadPage(boolean append) {
        if (loading) return;
        if (append && !pendingCharacters.isEmpty()
                && (pendingCharacters.size() >= DISPLAY_PAGE_SIZE || !hasMore)) {
            showPendingPage();
            return;
        }
        if (append) ThemeSoundPlayer.play(this, ThemeSoundPlayer.CUE_SCAN);
        loading = true;
        searchButton.setEnabled(false);
        searchInput.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        nextPageButton.setVisibility(View.GONE);
        statusText.setText(append ? R.string.loading_more : R.string.loading_search);
        final int requestOffset = append ? nextOffset : 0;
        final String requestQuery = currentQuery;
        final ArrayList<CharacterItem> startingCharacters = new ArrayList<>();
        if (append) {
            startingCharacters.addAll(pendingCharacters);
            pendingCharacters.clear();
        }
        workers.execute(() -> {
            try {
                ArrayList<CharacterItem> pageCharacters = new ArrayList<>(startingCharacters);
                ComicVineRepository.PageResult result = null;
                int offset = requestOffset;
                boolean more = true;
                do {
                    result = repository.searchMarvelCharacters(
                            requestQuery, offset, BuildConfig.COMICVINE_API_KEY);
                    ComicVineService.SearchPage page = result.page;
                    pageCharacters.addAll(page.characters);
                    offset = page.nextOffset;
                    more = page.hasMore;
                } while (pageCharacters.size() < DISPLAY_PAGE_SIZE && more);
                final ComicVineRepository.PageResult finalResult = result;
                final int followingOffset = offset;
                final boolean canContinue = more;
                final ArrayList<CharacterItem> loadedPage = pageCharacters;
                runOnUiThread(() -> {
                    finishLoading();
                    if (!append) {
                        loadedCharacters.clear();
                        pendingCharacters.clear();
                        resultsColumn.removeAllViews();
                    }
                    nextOffset = followingOffset;
                    hasMore = canContinue;
                    showCharacters(loadedPage);
                    if (!loadedPage.isEmpty()) {
                        GamificationManager.completeAndNotify(this, GamificationManager.CHARACTERS);
                    }
                    if (!loadedPage.isEmpty()) ThemeSoundPlayer.play(this, ThemeSoundPlayer.CUE_CONFIRM);
                    nextPageButton.setVisibility(hasMore || !pendingCharacters.isEmpty()
                            ? View.VISIBLE : View.GONE);
                    String message = loadedCharacters.isEmpty()
                            ? getString(R.string.no_results)
                            : getResources().getQuantityString(R.plurals.results_count,
                                    loadedCharacters.size(), loadedCharacters.size());
                    if (finalResult.fromCache) {
                        message += finalResult.stale ? getString(R.string.stale_cache) : getString(R.string.fresh_cache);
                    }
                    if (finalResult.warning != null && !finalResult.warning.trim().isEmpty()) {
                        message += "\n" + getString(R.string.api_unavailable, finalResult.warning);
                    }
                    statusText.setText(message);
                });
            } catch (Exception error) {
                String message = error.getMessage() == null
                        ? getString(R.string.network_error) : error.getMessage();
                if (message.contains("secrets.properties")) {
                    message = getString(R.string.missing_key);
                } else {
                    message += getString(R.string.check_connection);
                }
                final String displayMessage = message;
                runOnUiThread(() -> {
                    ThemeSoundPlayer.play(this, ThemeSoundPlayer.CUE_ALERT);
                    finishLoading();
                    pendingCharacters.addAll(startingCharacters);
                    nextPageButton.setVisibility(hasMore || !pendingCharacters.isEmpty()
                            ? View.VISIBLE : View.GONE);
                    statusText.setText(displayMessage);
                });
            }
        });
    }

    private void showPendingPage() {
        int count = Math.min(DISPLAY_PAGE_SIZE, pendingCharacters.size());
        ArrayList<CharacterItem> page = new ArrayList<>(pendingCharacters.subList(0, count));
        pendingCharacters.subList(0, count).clear();
        showCharacters(page);
        GamificationManager.completeAndNotify(this, GamificationManager.CHARACTERS);
        ThemeSoundPlayer.play(this, ThemeSoundPlayer.CUE_CONFIRM);
        nextPageButton.setVisibility(hasMore || !pendingCharacters.isEmpty() ? View.VISIBLE : View.GONE);
        statusText.setText(getResources().getQuantityString(R.plurals.results_count,
                loadedCharacters.size(), loadedCharacters.size()));
    }

    private void showCharacters(List<CharacterItem> characters) {
        int visibleCount = Math.min(DISPLAY_PAGE_SIZE, characters.size());
        for (int index = 0; index < visibleCount; index++) {
            CharacterItem character = characters.get(index);
            loadedCharacters.add(character);
            addCharacterCard(character);
        }
        if (characters.size() > visibleCount) {
            pendingCharacters.addAll(characters.subList(visibleCount, characters.size()));
        }
    }

    private void finishLoading() {
        loading = false;
        searchButton.setEnabled(true);
        searchInput.setEnabled(true);
        progressBar.setVisibility(View.GONE);
    }

    private void addCharacterCard(CharacterItem character) {
        View card = getLayoutInflater().inflate(R.layout.item_character, resultsColumn, false);
        ImageView image = card.findViewById(R.id.characterImage);
        TextView initials = card.findViewById(R.id.characterInitials);
        TextView name = card.findViewById(R.id.characterName);
        TextView realName = card.findViewById(R.id.characterRealName);
        TextView publisher = card.findViewById(R.id.characterPublisher);
        TextView summary = card.findViewById(R.id.characterSummary);
        initials.setText(initials(character.name));
        name.setText(character.name);
        bindOptional(realName, character.realName);
        bindOptional(publisher, character.publisher);
        bindOptional(summary, stripHtml(character.summary));
        card.setOnClickListener(v -> {
            ThemeSoundPlayer.play(this, ThemeSoundPlayer.CUE_OPEN);
            showCharacterDetails(character);
        });
        resultsColumn.addView(card);
        image.setContentDescription(getString(R.string.character_image, character.name));
        if (!character.imageUrl.isEmpty()) {
            image.setTag(character.imageUrl);
            workers.execute(() -> {
                Bitmap bitmap = ImageDownloader.download(character.imageUrl);
                if (bitmap != null) runOnUiThread(() -> {
                    if (character.imageUrl.equals(image.getTag())) {
                        image.setImageBitmap(bitmap);
                        image.setVisibility(View.VISIBLE);
                        initials.setVisibility(View.GONE);
                    }
                });
            });
        }
    }

    private void showCharacterDetails(CharacterItem character) {
        showCharacterDetails(character.name, character.apiDetailUrl, character.id,
                MarvelDetailDialog.characterFallback(character));
    }

    private void showCharacterDetails(String name, String apiDetailUrl, String id,
                                      org.json.JSONObject fallback) {
        AlertDialog loadingDialog = MarvelDetailDialog.showLoading(this, name);
        String detailUrl = ComicVineDetailService.detailUrl(
                "character", apiDetailUrl, id);
        workers.execute(() -> {
            try {
                org.json.JSONObject record = ComicVineDetailService.fetch(
                        detailUrl, BuildConfig.COMICVINE_API_KEY);
                runOnUiThread(() -> {
                    if (!loadingDialog.isShowing() || isFinishing() || isDestroyed()) return;
                    loadingDialog.dismiss();
                    MarvelDetailDialog.showCharacter(this, name, record, null);
                });
            } catch (Exception failure) {
                String message = getString(R.string.detail_load_error);
                runOnUiThread(() -> {
                    if (!loadingDialog.isShowing() || isFinishing() || isDestroyed()) return;
                    loadingDialog.dismiss();
                    MarvelDetailDialog.showCharacter(this, name, fallback, null, message);
                });
            }
        });
    }

    private void bindOptional(TextView view, String value) {
        boolean present = value != null && !value.trim().isEmpty();
        view.setVisibility(present ? View.VISIBLE : View.GONE);
        if (present) view.setText(value);
    }

    private String stripHtml(String value) {
        if (value == null) return "";
        return Html.fromHtml(value, Html.FROM_HTML_MODE_COMPACT).toString().trim();
    }

    private String join(List<String> values) {
        if (values == null) return "";
        StringBuilder joined = new StringBuilder();
        for (String value : values) {
            if (joined.length() > 0) joined.append(", ");
            joined.append(value);
        }
        return joined.toString();
    }

    private String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        String[] parts = name.trim().split("\\s+");
        return parts.length > 1
                ? (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase()
                : parts[0].substring(0, 1).toUpperCase();
    }

    @Override
    protected void onDestroy() {
        workers.shutdownNow();
        super.onDestroy();
    }
}
