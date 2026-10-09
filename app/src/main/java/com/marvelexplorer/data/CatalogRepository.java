package com.marvelexplorer.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;

public final class CatalogRepository {
    private static final long FRESH_MS = 6L * 60L * 60L * 1000L;
    private static final long STALE_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final int MAX_PAGES = 40;
    private final SharedPreferences preferences;

    public CatalogRepository(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences("comic_vine_catalog_pages_v1", Context.MODE_PRIVATE);
    }

    public Result search(ComicVineCatalogService.Section section, String query, int offset,
                         String apiKey) throws Exception {
        String key = cacheKey(section, query, offset);
        JSONObject stored = null;
        long savedAt = 0L;
        try {
            String encoded = preferences.getString(key, null);
            if (encoded != null) {
                stored = new JSONObject(encoded);
                savedAt = stored.optLong("savedAt", 0L);
            }
        } catch (Exception invalidCache) {
            preferences.edit().remove(key).apply();
        }
        long age = System.currentTimeMillis() - savedAt;
        boolean usable = stored != null && savedAt > 0L && age >= 0L && age <= STALE_MS;
        if (usable && age <= FRESH_MS) {
            return new Result(ComicVineCatalogService.parsePage(
                    section, stored.getString("response"), offset), true, false, null);
        }
        try {
            String response = ComicVineCatalogService.fetchJson(section, query, offset, apiKey);
            ComicVineCatalogService.Page page =
                    ComicVineCatalogService.parsePage(section, response, offset);
            save(key, response);
            return new Result(page, false, false, null);
        } catch (Exception failure) {
            if (usable) {
                return new Result(ComicVineCatalogService.parsePage(
                        section, stored.getString("response"), offset),
                        true, true, failure.getMessage());
            }
            throw failure;
        }
    }

    private void save(String key, String response) throws Exception {
        long now = System.currentTimeMillis();
        JSONObject stored = new JSONObject();
        stored.put("savedAt", now);
        stored.put("response", response);
        Map<String, ?> entries = preferences.getAll();
        String oldest = null;
        long oldestTime = Long.MAX_VALUE;
        int kept = 0;
        SharedPreferences.Editor editor = preferences.edit();
        for (Map.Entry<String, ?> entry : entries.entrySet()) {
            if (!entry.getKey().startsWith("page_")) continue;
            long savedAt = 0L;
            try {
                savedAt = new JSONObject(String.valueOf(entry.getValue())).optLong("savedAt", 0L);
            } catch (Exception ignored) {
                // Invalid entries are removed below.
            }
            if (savedAt <= 0L || now - savedAt > STALE_MS) {
                editor.remove(entry.getKey());
            } else {
                kept++;
                if (savedAt < oldestTime) {
                    oldestTime = savedAt;
                    oldest = entry.getKey();
                }
            }
        }
        if (!entries.containsKey(key) && kept >= MAX_PAGES && oldest != null) {
            editor.remove(oldest);
        }
        editor.putString(key, stored.toString()).apply();
    }

    private String cacheKey(ComicVineCatalogService.Section section, String query, int offset)
            throws Exception {
        String input = section.path + "|" + query.trim().toLowerCase(Locale.ROOT)
                + "|" + Math.max(0, offset);
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder("page_");
        for (byte value : digest) hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return hex.toString();
    }

    public static final class Result {
        public final ComicVineCatalogService.Page page;
        public final boolean fromCache;
        public final boolean stale;
        public final String warning;

        Result(ComicVineCatalogService.Page page, boolean fromCache,
               boolean stale, String warning) {
            this.page = page;
            this.fromCache = fromCache;
            this.stale = stale;
            this.warning = warning;
        }
    }
}
