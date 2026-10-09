package com.marvelexplorer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.marvelexplorer.data.CharacterItem;
import com.marvelexplorer.data.CatalogItem;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.function.Consumer;

/** Opens curated Comic Vine details in a dedicated themed screen. */
public final class MarvelDetailDialog {
    static final String EXTRA_TITLE = "detail_title";
    static final String EXTRA_RECORD = "detail_record";
    static final String EXTRA_WARNING = "detail_warning";
    static final String EXTRA_KIND = "detail_kind";

    private MarvelDetailDialog() {}

    public static AlertDialog showLoading(Activity activity, String title) {
        LinearLayout content = new LinearLayout(activity);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setOrientation(LinearLayout.HORIZONTAL);
        int padding = dp(activity, 24);
        content.setPadding(padding, padding, padding, padding);
        ProgressBar progress = new ProgressBar(activity);
        content.addView(progress, new LinearLayout.LayoutParams(dp(activity, 36), dp(activity, 36)));
        TextView message = new TextView(activity);
        message.setText(R.string.detail_loading);
        message.setTextColor(activity.getColor(R.color.ink));
        message.setTextSize(15);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        messageParams.setMargins(dp(activity, 16), 0, 0, 0);
        content.addView(message, messageParams);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(title).setView(content).setNegativeButton(R.string.close, null).create();
        dialog.show();
        return dialog;
    }

    public static void show(Activity activity, String title, JSONObject record, String warning) {
        show(activity, title, record, warning, "catalog");
    }

    public static void show(Activity activity, String title, JSONObject record, String warning, String kind) {
        openDetails(activity, title, record, warning, kind);
    }

    public static void showCharacter(Activity activity, String name, JSONObject record,
                                     Consumer<JSONObject> openCharacter) {
        showCharacter(activity, name, record, openCharacter, null);
    }

    public static void showCharacter(Activity activity, String name, JSONObject record,
                                     Consumer<JSONObject> openCharacter, String warning) {
        openDetails(activity, name, record, warning, "character");
    }

    private static void openDetails(Activity activity, String title, JSONObject record,
                                    String warning, String kind) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        Intent intent = new Intent(activity, MarvelDetailActivity.class);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_RECORD, compactRecord(record, kind).toString());
        intent.putExtra(EXTRA_WARNING, warning);
        intent.putExtra(EXTRA_KIND, kind);
        activity.startActivity(intent);
    }

    /** Keep Activity extras small and pass only the fields the detail screen can display. */
    private static JSONObject compactRecord(JSONObject source, String kind) {
        JSONObject compact = new JSONObject();
        if (source == null) return compact;
        String[] fields;
        if ("character".equals(kind)) {
            fields = new String[]{"image", "deck", "description", "real_name", "aliases", "birth",
                    "origin", "publisher", "first_appeared_in_issue", "count_of_issue_appearances", "powers"};
        } else if ("volume".equals(kind)) {
            fields = new String[]{"image", "publisher", "start_year", "count_of_issues", "aliases"};
        } else if ("team".equals(kind)) {
            fields = new String[]{"image", "deck", "description", "publisher", "count_of_team_members",
                    "first_appeared_in_issue", "creators", "concepts", "aliases"};
        } else {
            fields = new String[]{"image", "deck", "description", "publisher", "count_of_issue_appearances",
                    "first_appeared_in_issue", "aliases"};
        }
        try {
            for (String field : fields) {
                Object value = source.opt(field);
                if (value == null || value == JSONObject.NULL) continue;
                if ("image".equals(field) && value instanceof JSONObject) {
                    JSONObject image = (JSONObject) value;
                    JSONObject safeImage = new JSONObject();
                    for (String imageField : new String[]{"screen_url", "medium_url", "super_url", "original_url", "small_url"}) {
                        String url = image.optString(imageField, "");
                        if (!url.isEmpty()) safeImage.put(imageField, truncate(url, 800));
                    }
                    if (safeImage.length() > 0) compact.put(field, safeImage);
                } else if (("origin".equals(field) || "publisher".equals(field)
                        || "first_appeared_in_issue".equals(field)) && value instanceof JSONObject) {
                    JSONObject sourceObject = (JSONObject) value;
                    JSONObject safeObject = new JSONObject();
                    String name = sourceObject.optString("name", "");
                    if (!name.isEmpty()) safeObject.put("name", truncate(name, 300));
                    String issueNumber = sourceObject.optString("issue_number", "");
                    if (!issueNumber.isEmpty()) safeObject.put("issue_number", truncate(issueNumber, 80));
                    if (safeObject.length() > 0) compact.put(field, safeObject);
                } else if (value instanceof JSONArray) {
                    JSONArray sourceArray = (JSONArray) value;
                    JSONArray safeArray = new JSONArray();
                    for (int i = 0; i < sourceArray.length() && i < 8; i++) {
                        Object entry = sourceArray.opt(i);
                        if (entry instanceof JSONObject) {
                            String name = ((JSONObject) entry).optString("name", "");
                            if (!name.isEmpty()) safeArray.put(truncate(name, 180));
                        } else if (entry != null && entry != JSONObject.NULL) {
                            safeArray.put(truncate(String.valueOf(entry), 180));
                        }
                    }
                    if (safeArray.length() > 0) compact.put(field, safeArray);
                } else if (value instanceof String) {
                    int maximum = "deck".equals(field) || "description".equals(field) ? 3500 : 500;
                    String text = ((String) value).trim();
                    if (!text.isEmpty()) compact.put(field, truncate(text, maximum));
                } else {
                    compact.put(field, value);
                }
            }
        } catch (JSONException ignored) {
            // Keep whichever safe fields were copied before malformed optional data.
        }
        return compact;
    }

    private static String truncate(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    public static JSONObject characterFallback(CharacterItem character) {
        JSONObject record = new JSONObject();
        try {
            record.put("name", character.name);
            record.put("real_name", character.realName);
            record.put("publisher", character.publisher);
            record.put("deck", character.summary);
            if (character.imageUrl != null && !character.imageUrl.trim().isEmpty()) {
                JSONObject image = new JSONObject();
                image.put("medium_url", character.imageUrl);
                record.put("image", image);
            }
            record.put("first_appeared_in_issue", character.firstAppearance);
            record.put("count_of_issue_appearances", character.issueCount);
            JSONArray powers = new JSONArray();
            for (String power : character.powers) powers.put(power);
            record.put("powers", powers);
        } catch (JSONException ignored) {
            // Most successful fields remain useful when Comic Vine omits optional values.
        }
        return record;
    }

    public static JSONObject catalogFallback(CatalogItem item) {
        return catalogFallback(item, "catalog");
    }

    public static JSONObject catalogFallback(CatalogItem item, String kind) {
        JSONObject record = new JSONObject();
        try {
            record.put("name", item.name);
            record.put("publisher", item.publisher);
            if (!"volume".equals(kind)) record.put("deck", item.summary);
            if ("volume".equals(kind) && !item.detailValue.isEmpty()) {
                String[] values = item.detailValue.split("·", 2);
                record.put("count_of_issues", values[0].trim());
                if (values.length > 1 && values[1].contains(":")) {
                    record.put("start_year", values[1].substring(values[1].indexOf(':') + 1).trim());
                }
            } else if ("team".equals(kind) && !item.detailValue.isEmpty()) {
                record.put("count_of_team_members", item.detailValue);
            } else if ("story_arc".equals(kind) && !item.detailValue.isEmpty()) {
                record.put("count_of_issue_appearances", item.detailValue);
            }
        } catch (JSONException ignored) {
            // Most successful fields remain useful when Comic Vine omits optional values.
        }
        return record;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
