package com.marvelexplorer.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Small, bounded disk cache for Comic Vine character pages. It never stores the API key. */
public final class CharacterPageCache {
    private static final String PREFERENCES = "comic_vine_character_pages_v2";
    private static final String KEY_PREFIX = "page_";
    private static final long FRESH_FOR_MS = 6L * 60L * 60L * 1000L;
    private static final long KEEP_STALE_FOR_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final int MAX_PAGES = 40;

    private final SharedPreferences preferences;

    public CharacterPageCache(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    public CachedPage read(String query, int offset) {
        String encoded = preferences.getString(cacheKey(query, offset), null);
        if (encoded == null) return null;
        try {
            JSONObject stored = new JSONObject(encoded);
            long savedAt = stored.optLong("savedAt", 0L);
            long age = Math.max(0L, System.currentTimeMillis() - savedAt);
            if (savedAt <= 0L || age > KEEP_STALE_FOR_MS) return null;
            ComicVineService.SearchPage page = decodePage(stored.getJSONObject("page"));
            return new CachedPage(page, age <= FRESH_FOR_MS, savedAt);
        } catch (JSONException malformedCache) {
            preferences.edit().remove(cacheKey(query, offset)).apply();
            return null;
        }
    }

    public void write(String query, int offset, ComicVineService.SearchPage page) {
        long now = System.currentTimeMillis();
        JSONObject stored = new JSONObject();
        try {
            stored.put("savedAt", now);
            stored.put("page", encodePage(page));
        } catch (JSONException impossible) {
            return;
        }

        Map<String, ?> entries = preferences.getAll();
        List<String> cacheKeys = new ArrayList<>();
        String oldestKey = null;
        long oldestTime = Long.MAX_VALUE;
        SharedPreferences.Editor editor = preferences.edit();
        for (Map.Entry<String, ?> entry : entries.entrySet()) {
            if (!entry.getKey().startsWith(KEY_PREFIX)) continue;
            cacheKeys.add(entry.getKey());
            long savedAt = readSavedAt(entry.getValue());
            if (savedAt <= 0L || now - savedAt > KEEP_STALE_FOR_MS) {
                editor.remove(entry.getKey());
            } else if (savedAt < oldestTime) {
                oldestTime = savedAt;
                oldestKey = entry.getKey();
            }
        }

        String key = cacheKey(query, offset);
        if (!entries.containsKey(key) && cacheKeys.size() >= MAX_PAGES && oldestKey != null) {
            editor.remove(oldestKey);
        }
        editor.putString(key, stored.toString()).apply();
    }

    private JSONObject encodePage(ComicVineService.SearchPage page) throws JSONException {
        JSONObject object = new JSONObject();
        JSONArray characters = new JSONArray();
        for (CharacterItem item : page.characters) {
            JSONObject character = new JSONObject();
            character.put("id", item.id);
            character.put("apiDetailUrl", item.apiDetailUrl);
            character.put("name", item.name);
            character.put("realName", item.realName);
            character.put("publisher", item.publisher);
            character.put("summary", item.summary);
            character.put("imageUrl", item.imageUrl);
            character.put("firstAppearance", item.firstAppearance);
            character.put("issueCount", item.issueCount);
            JSONArray powers = new JSONArray();
            for (String power : item.powers) powers.put(power);
            character.put("powers", powers);
            characters.put(character);
        }
        object.put("characters", characters);
        object.put("hasMore", page.hasMore);
        object.put("nextOffset", page.nextOffset);
        return object;
    }

    private ComicVineService.SearchPage decodePage(JSONObject object) throws JSONException {
        JSONArray savedCharacters = object.optJSONArray("characters");
        List<CharacterItem> characters = new ArrayList<>();
        if (savedCharacters != null) {
            for (int index = 0; index < savedCharacters.length(); index++) {
                JSONObject character = savedCharacters.optJSONObject(index);
                if (character == null) continue;
                JSONArray savedPowers = character.optJSONArray("powers");
                List<String> powers = new ArrayList<>();
                if (savedPowers != null) {
                    for (int powerIndex = 0; powerIndex < savedPowers.length(); powerIndex++) {
                        powers.add(savedPowers.optString(powerIndex, ""));
                    }
                }
                characters.add(new CharacterItem(
                        character.optString("id", ""),
                        character.optString("apiDetailUrl", ""),
                        character.optString("name", "Personagem sem nome"),
                        character.optString("realName", ""),
                        character.optString("publisher", ""),
                        character.optString("summary", ""),
                        character.optString("imageUrl", ""),
                        character.optString("firstAppearance", ""),
                        character.optString("issueCount", ""),
                        powers));
            }
        }
        return new ComicVineService.SearchPage(
                characters,
                object.optBoolean("hasMore", false),
                object.optInt("nextOffset", 0));
    }

    private long readSavedAt(Object encoded) {
        if (!(encoded instanceof String)) return 0L;
        try {
            return new JSONObject((String) encoded).optLong("savedAt", 0L);
        } catch (JSONException ignored) {
            return 0L;
        }
    }

    private String cacheKey(String query, int offset) {
        String input = (query == null ? "" : query.trim()) + "|" + Math.max(0, offset);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(KEY_PREFIX);
            for (byte part : digest) hex.append(String.format(java.util.Locale.ROOT, "%02x", part & 0xff));
            return hex.toString();
        } catch (Exception unavailable) {
            return KEY_PREFIX + Integer.toHexString(input.hashCode()) + "_" + Math.max(0, offset);
        }
    }

    public static final class CachedPage {
        public final ComicVineService.SearchPage page;
        public final boolean fresh;
        public final long savedAt;

        CachedPage(ComicVineService.SearchPage page, boolean fresh, long savedAt) {
            this.page = page;
            this.fresh = fresh;
            this.savedAt = savedAt;
        }
    }
}
