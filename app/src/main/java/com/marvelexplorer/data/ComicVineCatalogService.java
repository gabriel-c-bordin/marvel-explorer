package com.marvelexplorer.data;

import org.json.JSONArray;
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

public final class ComicVineCatalogService {
    private static final String BASE_URL = "https://comicvine.gamespot.com/api/";
    private static final int PAGE_SIZE = 20;

    public enum Section {
        TEAMS("Equipes", "teams", "id,api_detail_url,name,deck,description,image,publisher,site_detail_url,count_of_team_members", true),
        VOLUMES("Volumes", "volumes", "id,api_detail_url,name,deck,description,image,publisher,site_detail_url,count_of_issues,start_year", true),
        STORY_ARCS("Arcos", "story_arcs", "id,api_detail_url,name,deck,description,image,publisher,site_detail_url,count_of_issue_appearances", true),
        ISSUES("Edições", "issues", "id,name,deck,description,image,site_detail_url,issue_number,volume", false),
        PUBLISHERS("Editoras", "publishers", "id,name,deck,description,image,site_detail_url", false),
        POWERS("Poderes", "powers", "id,name,description,site_detail_url", false),
        MOVIES("Filmes", "movies", "id,name,deck,description,image,site_detail_url,release_date", false),
        LOCATIONS("Locais", "locations", "id,name,deck,description,image,site_detail_url,start_year", false);

        public final String label;
        public final String path;
        public final String fields;
        public final boolean marvelOnly;

        Section(String label, String path, String fields, boolean marvelOnly) {
            this.label = label;
            this.path = path;
            this.fields = fields;
            this.marvelOnly = marvelOnly;
        }
    }

    private ComicVineCatalogService() {}

    public static String fetchJson(Section section, String query, int offset, String apiKey)
            throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("Configure sua chave Comic Vine em secrets.properties para buscar dados reais.");
        }
        String url = BASE_URL + section.path + "/?api_key=" + encode(apiKey.trim())
                + "&format=json&filter=" + encode("name:" + query.trim())
                + "&field_list=" + encode(section.fields)
                + "&limit=" + PAGE_SIZE
                + "&offset=" + Math.max(0, offset);
        ComicVineRequestThrottle.waitForSlot();
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestProperty("User-Agent", "MarvelExplorerAndroid/0.1 (academic project)");
        connection.setRequestProperty("Accept", "application/json");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        try {
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String body = readFully(stream);
            if (status < 200 || status >= 300) {
                throw new ComicVineService.ApiException("A Comic Vine respondeu com HTTP " + status + ".");
            }
            JSONObject envelope = new JSONObject(body);
            if (envelope.optInt("status_code", 1) != 1
                    || !"OK".equalsIgnoreCase(envelope.optString("error", "OK"))) {
                throw new ComicVineService.ApiException("Erro da Comic Vine: "
                        + envelope.optString("error", "resposta inválida"));
            }
            return body;
        } finally {
            connection.disconnect();
        }
    }

    public static Page parsePage(Section section, String json, int requestedOffset)
            throws Exception {
        JSONObject envelope = new JSONObject(json);
        JSONArray results = envelope.optJSONArray("results");
        List<CatalogItem> items = new ArrayList<>();
        if (results != null) {
            for (int i = 0; i < results.length(); i++) {
                JSONObject record = results.optJSONObject(i);
                if (record == null || (section.marvelOnly
                        && !isMarvel(record.optJSONObject("publisher")))) continue;
                JSONObject image = record.optJSONObject("image");
                JSONObject publisher = record.optJSONObject("publisher");
                String detailLabel;
                String detailValue;
                switch (section) {
                    case TEAMS:
                        detailLabel = "Integrantes";
                        detailValue = record.optString("count_of_team_members", "");
                        break;
                    case VOLUMES:
                        detailLabel = "Edições";
                        detailValue = record.optString("count_of_issues", "");
                        String year = record.optString("start_year", "");
                        if (!year.isEmpty()) detailValue += " · Início: " + year;
                        break;
                    case STORY_ARCS:
                        detailLabel = "Aparições";
                        detailValue = record.optString("count_of_issue_appearances", "");
                        break;
                    case ISSUES:
                        detailLabel = "Número";
                        detailValue = record.optString("issue_number", "");
                        break;
                    case MOVIES:
                        detailLabel = "Lançamento";
                        detailValue = record.optString("release_date", "");
                        break;
                    case LOCATIONS:
                        detailLabel = "Ano inicial";
                        detailValue = record.optString("start_year", "");
                        break;
                    default:
                        detailLabel = "";
                        detailValue = "";
                }
                String name = record.optString("name", "").trim();
                if (name.isEmpty() && section == Section.ISSUES) {
                    JSONObject volume = record.optJSONObject("volume");
                    name = (volume == null ? "Edição" : volume.optString("name", "Edição"))
                            + (detailValue.isEmpty() ? "" : " #" + detailValue);
                }
                if (name.isEmpty()) name = "Sem nome";
                items.add(new CatalogItem(
                        record.optString("id", ""),
                        name,
                        publisher == null ? "" : publisher.optString("name", ""),
                        firstNonBlank(record.optString("deck", ""), record.optString("description", "")),
                        imageUrl(image),
                        record.optString("site_detail_url", ""),
                        record.optString("api_detail_url", ""),
                        detailLabel,
                        detailValue));
            }
        }
        int pageCount = envelope.optInt("number_of_page_results", results == null ? 0 : results.length());
        int total = envelope.optInt("number_of_total_results", pageCount);
        int responseOffset = envelope.optInt("offset", requestedOffset);
        int limit = envelope.optInt("limit", PAGE_SIZE);
        return new Page(items, responseOffset + pageCount < total, responseOffset + limit);
    }

    private static boolean isMarvel(JSONObject publisher) {
        if (publisher == null) return false;
        String id = publisher.optString("id", "");
        String name = publisher.optString("name", "").trim().toLowerCase(Locale.ROOT);
        return "31".equals(id) || "4010-31".equals(id)
                || "marvel".equals(name) || "marvel comics".equals(name);
    }

    private static String imageUrl(JSONObject image) {
        if (image == null) return "";
        String value = firstNonBlank(image.optString("medium_url", ""),
                image.optString("small_url", ""), image.optString("thumb_url", ""));
        return value.startsWith("http://") ? "https://" + value.substring(7) : value;
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
        StringBuilder value = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) value.append(line);
        }
        return value.toString();
    }

    public static final class Page {
        public final List<CatalogItem> items;
        public final boolean hasMore;
        public final int nextOffset;

        Page(List<CatalogItem> items, boolean hasMore, int nextOffset) {
            this.items = items;
            this.hasMore = hasMore;
            this.nextOffset = nextOffset;
        }
    }
}
