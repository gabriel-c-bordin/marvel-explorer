package com.marvelexplorer.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ComicVineService {
    private static final String BASE_URL = "https://comicvine.gamespot.com/api/characters/";
    private static final int PAGE_SIZE = 30;
    private static final String USER_AGENT = "MarvelExplorerAndroid/0.1 (academic project)";

    private ComicVineService() {}

    public static SearchPage searchMarvelCharacters(String query, int offset, String apiKey)
            throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("Configure sua chave Comic Vine em secrets.properties para buscar dados reais.");
        }

        String safeQuery = query == null ? "" : query.trim();

        String fields = "id,api_detail_url,name,aliases,birth,gender,origin,real_name,image,deck,description,powers,publisher,count_of_issue_appearances,first_appeared_in_issue";
        String requestUrl = BASE_URL
                + "?api_key=" + encode(apiKey.trim())
                + "&format=json"
                + (safeQuery.isEmpty() ? "" : "&filter=" + encode("name:" + safeQuery))
                + "&field_list=" + encode(fields)
                + "&limit=" + PAGE_SIZE
                + "&offset=" + Math.max(0, offset);

        ComicVineRequestThrottle.waitForSlot();
        HttpURLConnection connection = (HttpURLConnection) new URL(requestUrl).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "application/json");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);

        try {
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String body = readFully(stream);
            if (status < 200 || status >= 300) {
                throw new ApiException("A Comic Vine respondeu com HTTP " + status + ". Confira a chave e tente novamente.");
            }
            return parsePage(body, offset);
        } finally {
            connection.disconnect();
        }
    }

    private static SearchPage parsePage(String body, int requestedOffset) throws JSONException {
        JSONObject root = new JSONObject(body);
        String apiError = root.optString("error", "");
        if (!apiError.isEmpty() && !"OK".equalsIgnoreCase(apiError)) {
            throw new ApiException("Erro da Comic Vine: " + apiError);
        }

        JSONArray results = root.optJSONArray("results");
        List<CharacterItem> characters = new ArrayList<>();
        if (results != null) {
            for (int i = 0; i < results.length(); i++) {
                JSONObject item = results.optJSONObject(i);
                if (item == null || !isMarvel(item.optJSONObject("publisher"))) continue;
                characters.add(parseCharacter(item));
            }
        }

        int total = root.optInt("number_of_total_results", characters.size());
        int pageCount = root.optInt("number_of_page_results", results == null ? 0 : results.length());
        int responseOffset = root.optInt("offset", requestedOffset);
        int limit = root.optInt("limit", PAGE_SIZE);
        boolean hasMore = responseOffset + pageCount < total;
        return new SearchPage(characters, hasMore, requestedOffset + limit);
    }

    private static boolean isMarvel(JSONObject publisher) {
        if (publisher == null) return false;
        String id = publisher.optString("id", "");
        String name = publisher.optString("name", "").trim().toLowerCase(Locale.ROOT);
        return "4010-31".equals(id)
                || "31".equals(id)
                || "marvel".equals(name)
                || "marvel comics".equals(name);
    }

    private static CharacterItem parseCharacter(JSONObject item) {
        JSONObject publisher = item.optJSONObject("publisher");
        JSONObject image = item.optJSONObject("image");
        JSONObject firstIssue = item.optJSONObject("first_appeared_in_issue");
        String summary = firstNonBlank(item.optString("deck", ""), item.optString("description", ""));
        return new CharacterItem(
                item.optString("id", ""),
                item.optString("api_detail_url", ""),
                item.optString("name", "Personagem sem nome"),
                item.optString("real_name", ""),
                publisher == null ? "" : publisher.optString("name", ""),
                summary,
                imageUrl(image),
                firstIssue == null ? "" : firstNonBlank(
                        firstIssue.optString("name", ""), firstIssue.optString("issue_number", "")),
                item.has("count_of_issue_appearances")
                        ? item.optString("count_of_issue_appearances", "") : "",
                parsePowers(item.optJSONArray("powers")));
    }

    private static List<String> parsePowers(JSONArray powers) {
        List<String> names = new ArrayList<>();
        if (powers == null) return names;
        for (int i = 0; i < powers.length(); i++) {
            JSONObject power = powers.optJSONObject(i);
            if (power != null) {
                String name = power.optString("name", "").trim();
                if (!name.isEmpty()) names.add(name);
            }
        }
        return names;
    }

    private static String imageUrl(JSONObject image) {
        if (image == null) return "";
        String url = firstNonBlank(image.optString("medium_url", ""),
                image.optString("small_url", ""), image.optString("thumb_url", ""));
        if (url.startsWith("http://")) return "https://" + url.substring("http://".length());
        return url;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    private static String readFully(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) text.append(line);
        }
        return text.toString();
    }

    public static final class SearchPage {
        public final List<CharacterItem> characters;
        public final boolean hasMore;
        public final int nextOffset;

        public SearchPage(List<CharacterItem> characters, boolean hasMore, int nextOffset) {
            this.characters = characters;
            this.hasMore = hasMore;
            this.nextOffset = nextOffset;
        }
    }

    public static final class ApiException extends RuntimeException {
        public ApiException(String message) { super(message); }
    }
}
