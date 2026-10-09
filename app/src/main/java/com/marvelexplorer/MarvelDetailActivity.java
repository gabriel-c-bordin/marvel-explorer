package com.marvelexplorer;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Html;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.marvelexplorer.data.ImageDownloader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A focused, scrollable detail page for Comic Vine records. */
public final class MarvelDetailActivity extends Activity {
    private static final ExecutorService IMAGE_WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "marvel-detail-image");
        thread.setDaemon(true);
        return thread;
    });

    private final int ink = 0xFFEAF8FF;
    private final int muted = 0xFF9CB8CC;
    private final int cyan = 0xFF45E6FF;
    private final int panel = 0xFF0D1B2C;
    private final int raised = 0xFF152B40;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        String title = getIntent().getStringExtra(MarvelDetailDialog.EXTRA_TITLE);
        if (title == null || title.trim().isEmpty()) title = getString(R.string.detail_title_default);
        String kind = getIntent().getStringExtra(MarvelDetailDialog.EXTRA_KIND);
        boolean character = "character".equals(kind);
        boolean volume = "volume".equals(kind);
        JSONObject record;
        try {
            record = new JSONObject(getIntent().getStringExtra(MarvelDetailDialog.EXTRA_RECORD));
        } catch (Exception malformed) {
            record = new JSONObject();
        }
        render(title, record, getIntent().getStringExtra(MarvelDetailDialog.EXTRA_WARNING), kind, character, volume);
    }

    private void render(String title, JSONObject record, String warning, String kind,
                        boolean character, boolean volume) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF07111E);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(16), dp(8), dp(16), dp(8));
        toolbar.setBackgroundColor(panel);
        TextView back = text("‹", 34, cyan, true);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription(getString(R.string.back));
        back.setClickable(true);
        back.setFocusable(true);
        back.setOnClickListener(v -> finish());
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        TextView eyebrow = text("MARVEL EXPLORER · ARQUIVO", 10, cyan, true);
        TextView name = text(title, 19, ink, true);
        name.setMaxLines(2);
        heading.addView(eyebrow);
        heading.addView(name);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(0, -2, 1f);
        headingParams.setMargins(dp(8), 0, 0, 0);
        toolbar.addView(heading, headingParams);
        root.addView(toolbar);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(28));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        int rootLeft = root.getPaddingLeft();
        int rootTop = root.getPaddingTop();
        int rootRight = root.getPaddingRight();
        int rootBottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets safe = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(rootLeft + safe.left, rootTop + safe.top,
                    rootRight + safe.right, rootBottom + safe.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);

        if (warning != null && !warning.trim().isEmpty()) {
            TextView note = text(warning, 13, muted, false);
            note.setPadding(dp(12), dp(10), dp(12), dp(10));
            note.setBackgroundColor(raised);
            content.addView(note, bottom(10));
        }

        addArtwork(content, record.optJSONObject("image"), title);

        String summary = record.optString("deck", "").trim();
        if (summary.isEmpty()) summary = record.optString("description", "").trim();
        if (!volume && !summary.isEmpty()) addLongText(content, getString(R.string.detail_about), summary);

        String[] fields = character
                ? new String[]{"real_name", "aliases", "birth", "origin", "publisher",
                    "first_appeared_in_issue", "count_of_issue_appearances", "powers"}
                : "volume".equals(kind)
                    ? new String[]{"publisher", "start_year", "count_of_issues", "aliases"}
                    : "team".equals(kind)
                        ? new String[]{"publisher", "count_of_team_members", "first_appeared_in_issue", "creators", "concepts", "aliases"}
                        : new String[]{"publisher", "count_of_issue_appearances", "first_appeared_in_issue", "aliases"};
        boolean any = !volume && !summary.isEmpty();
        for (String key : fields) {
            String value = displayValue(key, record.opt(key));
            if (value.isEmpty()) continue;
            any = true;
            addField(content, fieldLabel(key), value);
        }
        if (!any) {
            TextView empty = text(getString(R.string.detail_unavailable), 14, muted, false);
            empty.setPadding(dp(14), dp(14), dp(14), dp(14));
            empty.setBackgroundColor(panel);
            content.addView(empty, bottom(10));
        }
    }

    private void addArtwork(LinearLayout parent, JSONObject imageData, String title) {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(panel);
        frame.setPadding(dp(8), dp(8), dp(8), dp(8));
        ImageView image = new ImageView(this);
        image.setContentDescription(getString(R.string.character_image, title));
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setVisibility(View.GONE);
        int maxHeight = Math.min(dp(320), Math.round(getResources().getDisplayMetrics().heightPixels * 0.42f));
        int artworkHeight = Math.max(dp(180), maxHeight);
        frame.addView(image, new FrameLayout.LayoutParams(-1, artworkHeight, Gravity.CENTER));
        TextView placeholder = text(getString(R.string.detail_image_unavailable, title), 13, muted, false);
        placeholder.setGravity(Gravity.CENTER);
        placeholder.setMinHeight(artworkHeight);
        frame.addView(placeholder, new FrameLayout.LayoutParams(-1, artworkHeight, Gravity.CENTER));
        parent.addView(frame, bottom(12));

        String url = imageUrl(imageData);
        if (url.isEmpty()) {
            return;
        }
        image.setTag(url);
        IMAGE_WORKER.execute(() -> {
            Bitmap bitmap = ImageDownloader.download(url);
            if (bitmap == null) return;
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed() && url.equals(image.getTag())) {
                    image.setImageBitmap(bitmap);
                    image.setVisibility(View.VISIBLE);
                    placeholder.setVisibility(View.GONE);
                }
            });
        });
    }

    private String imageUrl(JSONObject image) {
        if (image == null) return "";
        for (String key : new String[]{"screen_url", "medium_url", "super_url", "original_url", "small_url"}) {
            String url = image.optString(key, "").trim();
            if (!url.isEmpty()) return url.startsWith("http://") ? "https://" + url.substring(7) : url;
        }
        return "";
    }

    private String displayValue(String key, Object value) {
        if (value == null || value == JSONObject.NULL) return "";
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            if ("origin".equals(key)) return object.optString("name", "").trim();
            if ("publisher".equals(key)) return object.optString("name", "").trim();
            if ("first_appeared_in_issue".equals(key)) {
                String name = object.optString("name", "").trim();
                if (!name.isEmpty()) return name;
                return object.optString("issue_number", "").trim();
            }
            return "";
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < array.length() && i < 8; i++) {
                Object item = array.opt(i);
                String name = item instanceof JSONObject
                        ? ((JSONObject) item).optString("name", "").trim() : String.valueOf(item).trim();
                if (name.isEmpty() || "null".equals(name)) continue;
                if (result.length() > 0) result.append(" · ");
                result.append(name);
            }
            return result.toString();
        }
        String text = String.valueOf(value).trim();
        if ("null".equalsIgnoreCase(text) || text.isEmpty()) return "";
        return Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT).toString().trim();
    }

    private String fieldLabel(String key) {
        switch (key) {
            case "real_name": return "Nome real";
            case "aliases": return "Também conhecido como";
            case "birth": return "Nascimento";
            case "origin": return "Origem";
            case "publisher": return "Editora";
            case "first_appeared_in_issue": return "Primeira aparição";
            case "count_of_issue_appearances": return "Aparições em edições";
            case "powers": return "Poderes";
            case "start_year": return "Ano de início";
            case "count_of_issues": return "Número de edições";
            case "count_of_team_members": return "Integrantes";
            case "creators": return "Criadores";
            case "concepts": return "Conceitos";
            default: return key;
        }
    }

    private void addLongText(LinearLayout parent, String title, String value) {
        LinearLayout card = card();
        card.addView(text(title.toUpperCase(java.util.Locale.ROOT), 12, cyan, true));
        TextView body = text(Html.fromHtml(value, Html.FROM_HTML_MODE_COMPACT).toString().trim(), 15, ink, false);
        body.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
        bodyParams.topMargin = dp(8);
        card.addView(body, bodyParams);
        parent.addView(card, bottom(10));
    }

    private void addField(LinearLayout parent, String title, String value) {
        LinearLayout card = card();
        card.addView(text(title.toUpperCase(java.util.Locale.ROOT), 11, cyan, true));
        TextView body = text(value, 14, ink, false);
        body.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
        bodyParams.topMargin = dp(4);
        card.addView(body, bodyParams);
        parent.addView(card, bottom(8));
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackgroundColor(panel);
        return card;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private LinearLayout.LayoutParams bottom(int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(margin);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
