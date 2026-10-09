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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.marvelexplorer.data.CatalogItem;
import com.marvelexplorer.data.CatalogRepository;
import com.marvelexplorer.data.ComicVineDetailService;
import com.marvelexplorer.data.ComicVineCatalogService;
import com.marvelexplorer.data.ImageDownloader;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CatalogActivity extends Activity {
    public static final String EXTRA_SECTION = "catalog_initial_section";
    private final ExecutorService workers = Executors.newFixedThreadPool(4);
    private CatalogRepository repository;
    private Spinner sectionSpinner;
    private EditText queryInput;
    private Button searchButton;
    private Button moreButton;
    private TextView status;
    private TextView scope;
    private ProgressBar progress;
    private LinearLayout results;
    private ComicVineCatalogService.Section section = ComicVineCatalogService.Section.TEAMS;
    private String query = "Avengers";
    private int nextOffset;
    private int shown;
    private boolean hasMore;
    private boolean loading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BottomNavigation.prepareWindow(this);
        setContentView(R.layout.activity_catalog);
        BottomNavigation.attach(this, -1);
        repository = new CatalogRepository(this);
        sectionSpinner = findViewById(R.id.catalogSection);
        queryInput = findViewById(R.id.catalogQuery);
        searchButton = findViewById(R.id.catalogSearch);
        moreButton = findViewById(R.id.catalogMore);
        status = findViewById(R.id.catalogStatus);
        scope = findViewById(R.id.catalogScope);
        progress = findViewById(R.id.catalogProgress);
        results = findViewById(R.id.catalogResults);
        TextView attribution = findViewById(R.id.catalogAttribution);
        String initialSection = getIntent().getStringExtra(EXTRA_SECTION);
        ComicVineCatalogService.Section initial = sectionForName(initialSection);
        if (initial != null) section = initial;
        query = defaultQuery(section);
        queryInput.setText(query);
        // These resources expose a direct publisher field, so the results can be
        // restricted to Marvel instead of mixing publishers in the UI.
        ComicVineCatalogService.Section[] sections = {
                ComicVineCatalogService.Section.TEAMS,
                ComicVineCatalogService.Section.VOLUMES,
                ComicVineCatalogService.Section.STORY_ARCS
        };
        String[] labels = new String[sections.length];
        for (int i = 0; i < sections.length; i++) labels[i] = sections[i].label;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sectionSpinner.setAdapter(adapter);
        for (int i = 0; i < sections.length; i++) {
            if (sections[i] == section) sectionSpinner.setSelection(i);
        }
        sectionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ComicVineCatalogService.Section selected = sections[position];
                if (selected == section || loading) return;
                section = selected;
                query = defaultQuery(selected);
                queryInput.setText(query);
                scope.setText(selected.marvelOnly
                        ? R.string.catalog_scope_marvel : R.string.catalog_scope_general);
                resetAndLoad();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        searchButton.setOnClickListener(v -> startSearch());
        queryInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;
            startSearch();
            return true;
        });
        moreButton.setOnClickListener(v -> loadPage(true));
        attribution.setOnClickListener(v -> openComicVine("https://comicvine.gamespot.com/"));
        loadPage(false);
    }

    private void startSearch() {
        if (loading) return;
        String entered = queryInput.getText().toString().trim();
        if (entered.isEmpty()) {
            queryInput.setError(getString(R.string.search_required));
            return;
        }
        query = entered;
        ThemeSoundPlayer.play(this, ThemeSoundPlayer.CUE_SCAN);
        resetAndLoad();
    }

    private void resetAndLoad() {
        nextOffset = 0;
        shown = 0;
        hasMore = false;
        results.removeAllViews();
        loadPage(false);
    }

    private void loadPage(boolean append) {
        if (loading) return;
        if (append) ThemeSoundPlayer.play(this, ThemeSoundPlayer.CUE_SCAN);
        loading = true;
        searchButton.setEnabled(false);
        sectionSpinner.setEnabled(false);
        queryInput.setEnabled(false);
        moreButton.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        status.setText(append ? R.string.loading_more : R.string.loading_search);
        final int offset = append ? nextOffset : 0;
        final String requestedQuery = query;
        final ComicVineCatalogService.Section requestedSection = section;
        workers.execute(() -> {
            try {
                CatalogRepository.Result result = repository.search(
                        requestedSection, requestedQuery, offset, BuildConfig.COMICVINE_API_KEY);
                runOnUiThread(() -> {
                    finishLoading();
                    if (!append) {
                        results.removeAllViews();
                        shown = 0;
                    }
                    for (CatalogItem item : result.page.items) addCard(item);
                    if (!result.page.items.isEmpty()) {
                        String mission = requestedSection == ComicVineCatalogService.Section.TEAMS
                                ? GamificationManager.TEAMS : GamificationManager.STORIES;
                        GamificationManager.completeAndNotify(this, mission);
                    }
                    if (!result.page.items.isEmpty()) ThemeSoundPlayer.play(this, ThemeSoundPlayer.CUE_CONFIRM);
                    shown += result.page.items.size();
                    nextOffset = result.page.nextOffset;
                    hasMore = result.page.hasMore;
                    moreButton.setVisibility(hasMore ? View.VISIBLE : View.GONE);
                    String message;
                    if (shown == 0) {
                        message = requestedSection.marvelOnly
                                ? getString(hasMore ? R.string.catalog_no_results_page
                                        : R.string.catalog_no_results)
                                : getString(R.string.catalog_no_results_general);
                    } else {
                        message = getResources().getQuantityString(requestedSection.marvelOnly
                                ? R.plurals.catalog_count : R.plurals.catalog_count_general, shown, shown);
                    }
                    if (result.fromCache) {
                        message += result.stale ? getString(R.string.stale_cache)
                                : getString(R.string.fresh_cache);
                    }
                    if (result.warning != null && !result.warning.isEmpty()) {
                        message += "\n" + getString(R.string.api_unavailable, result.warning);
                    }
                    status.setText(message);
                });
            } catch (Exception failure) {
                String message = failure.getMessage() == null
                        ? getString(R.string.network_error) : failure.getMessage();
                if (message.contains("secrets.properties")) {
                    message = getString(R.string.missing_key);
                } else {
                    message += getString(R.string.check_connection);
                }
                final String displayMessage = message;
                runOnUiThread(() -> {
                    ThemeSoundPlayer.play(this, ThemeSoundPlayer.CUE_ALERT);
                    finishLoading();
                    moreButton.setVisibility(hasMore ? View.VISIBLE : View.GONE);
                    status.setText(displayMessage);
                });
            }
        });
    }

    private void finishLoading() {
        loading = false;
        searchButton.setEnabled(true);
        sectionSpinner.setEnabled(true);
        queryInput.setEnabled(true);
        progress.setVisibility(View.GONE);
    }

    private String defaultQuery(ComicVineCatalogService.Section selected) {
        switch (selected) {
            case TEAMS: return "Avengers";
            case VOLUMES: return "Spider-Man";
            case STORY_ARCS: return "Civil War";
            case ISSUES: return "Amazing Spider-Man";
            case PUBLISHERS: return "Marvel";
            case POWERS: return "Flight";
            case MOVIES: return "Iron Man";
            case LOCATIONS: return "Wakanda";
            default: return "";
        }
    }

    private ComicVineCatalogService.Section sectionForName(String name) {
        if (name == null) return null;
        try {
            ComicVineCatalogService.Section candidate = ComicVineCatalogService.Section.valueOf(name);
            return candidate == ComicVineCatalogService.Section.TEAMS
                    || candidate == ComicVineCatalogService.Section.VOLUMES
                    || candidate == ComicVineCatalogService.Section.STORY_ARCS ? candidate : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void addCard(CatalogItem item) {
        View card = getLayoutInflater().inflate(R.layout.item_character, results, false);
        ImageView image = card.findViewById(R.id.characterImage);
        TextView initials = card.findViewById(R.id.characterInitials);
        TextView name = card.findViewById(R.id.characterName);
        TextView extra = card.findViewById(R.id.characterRealName);
        TextView publisher = card.findViewById(R.id.characterPublisher);
        TextView summary = card.findViewById(R.id.characterSummary);
        name.setText(item.name);
        initials.setText(item.name.isEmpty() ? "?" : item.name.substring(0, 1).toUpperCase());
        bindOptional(extra, item.detailValue.isEmpty() ? "" : item.detailLabel + ": " + item.detailValue);
        bindOptional(publisher, item.publisher);
        bindOptional(summary, stripHtml(item.summary));
        card.setOnClickListener(v -> {
            ThemeSoundPlayer.play(this, ThemeSoundPlayer.CUE_OPEN);
            showDetails(item);
        });
        results.addView(card);
        image.setContentDescription(getString(R.string.character_image, item.name));
        if (!item.imageUrl.isEmpty()) {
            image.setTag(item.imageUrl);
            workers.execute(() -> {
                Bitmap bitmap = ImageDownloader.download(item.imageUrl);
                if (bitmap != null) runOnUiThread(() -> {
                    if (item.imageUrl.equals(image.getTag())) {
                        image.setImageBitmap(bitmap);
                        image.setVisibility(View.VISIBLE);
                        initials.setVisibility(View.GONE);
                    }
                });
            });
        }
    }

    private void showDetails(CatalogItem item) {
        ComicVineCatalogService.Section requestedSection = section;
        String resource = requestedSection == ComicVineCatalogService.Section.TEAMS ? "team"
                : requestedSection == ComicVineCatalogService.Section.VOLUMES ? "volume" : "story_arc";
        String detailUrl = ComicVineDetailService.detailUrl(resource, item.apiDetailUrl, item.id);
        AlertDialog loadingDialog = MarvelDetailDialog.showLoading(this, item.name);
        workers.execute(() -> {
            try {
                org.json.JSONObject record = ComicVineDetailService.fetch(
                        detailUrl, BuildConfig.COMICVINE_API_KEY);
                runOnUiThread(() -> {
                    if (!loadingDialog.isShowing() || isFinishing()) return;
                    loadingDialog.dismiss();
                    MarvelDetailDialog.show(this, item.name, record, null, resource);
                });
            } catch (Exception failure) {
                String message = getString(R.string.detail_load_error);
                runOnUiThread(() -> {
                    if (!loadingDialog.isShowing() || isFinishing()) return;
                    loadingDialog.dismiss();
                    MarvelDetailDialog.show(this, item.name,
                            MarvelDetailDialog.catalogFallback(item, resource), message, resource);
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
        return value == null ? "" : Html.fromHtml(value, Html.FROM_HTML_MODE_COMPACT).toString().trim();
    }

    private void openComicVine(String url) {
        ThemeSoundPlayer.play(this, ThemeSoundPlayer.CUE_OPEN);
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    @Override
    protected void onDestroy() {
        workers.shutdownNow();
        super.onDestroy();
    }
}
