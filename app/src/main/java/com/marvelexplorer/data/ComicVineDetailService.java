package com.marvelexplorer.data;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Fetches a full supported record using its API detail URL and returns its fields for display. */
public final class ComicVineDetailService {
    private ComicVineDetailService() {}

    public static String detailUrl(String resource, String suppliedUrl, String id) {
        if (suppliedUrl != null && !suppliedUrl.trim().isEmpty()) return suppliedUrl.trim();
        String prefix;
        switch (resource) {
            case "character": prefix = "4005"; break;
            case "team": prefix = "4060"; break;
            case "volume": prefix = "4050"; break;
            case "story_arc": prefix = "4045"; break;
            default: return "";
        }
        if (id == null || id.trim().isEmpty()) return "";
        String identifier = id.trim().contains("-") ? id.trim() : prefix + "-" + id.trim();
        return "https://comicvine.gamespot.com/api/" + resource + "/" + identifier + "/";
    }

    public static JSONObject fetch(String apiDetailUrl, String apiKey) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("Configure sua chave Comic Vine em secrets.properties para carregar os detalhes.");
        }
        URI uri = new URI(apiDetailUrl);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (!(host.equals("comicvine.gamespot.com") || host.endsWith(".comicvine.gamespot.com")
                || host.equals("comicvine.com") || host.endsWith(".comicvine.com"))
                || !path.matches("/api/(character|team|volume|story_arc)/[^/]+/?")) {
            throw new IllegalArgumentException("A Comic Vine não forneceu um endereço de detalhes válido.");
        }

        String resource = path.split("/")[2];
        String fields = fieldsFor(resource);
        String baseUrl = "https://" + host + path;
        String requestUrl = baseUrl + "?api_key=" + encode(apiKey.trim())
                + "&format=json&field_list=" + encode(fields);

        ComicVineRequestThrottle.waitForSlot();
        HttpURLConnection connection = (HttpURLConnection) new URL(requestUrl).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "MarvelExplorerAndroid/0.1 (academic project)");
        connection.setRequestProperty("Accept", "application/json");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(25000);
        try {
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String body = readFully(stream);
            if (status < 200 || status >= 300) {
                throw new ComicVineService.ApiException("A Comic Vine respondeu com HTTP " + status + " ao abrir a ficha.");
            }
            JSONObject envelope = new JSONObject(body);
            if (envelope.optInt("status_code", 1) != 1
                    || !"OK".equalsIgnoreCase(envelope.optString("error", "OK"))) {
                throw new ComicVineService.ApiException("Erro da Comic Vine: "
                        + envelope.optString("error", "resposta inválida"));
            }
            JSONObject record = envelope.optJSONObject("results");
            if (record == null) throw new ComicVineService.ApiException("A Comic Vine não retornou a ficha solicitada.");

            JSONObject publisher = record.optJSONObject("publisher");
            if (publisher != null && !isMarvel(publisher)) {
                throw new ComicVineService.ApiException("Este registro não está associado à editora Marvel.");
            }
            return record;
        } finally {
            connection.disconnect();
        }
    }

    private static String fieldsFor(String resource) {
        switch (resource) {
            case "character":
                return "id,api_detail_url,name,image,deck,description,publisher,real_name,aliases,birth,origin,first_appeared_in_issue,count_of_issue_appearances,powers";
            case "team":
                return "id,api_detail_url,name,aliases,image,deck,description,publisher,count_of_team_members,first_appeared_in_issue,creators,concepts";
            case "volume":
                return "id,api_detail_url,name,aliases,image,deck,description,publisher,start_year,count_of_issues";
            case "story_arc":
                return "id,api_detail_url,name,aliases,image,deck,description,publisher,count_of_issue_appearances,first_appeared_in_issue";
            default:
                throw new IllegalArgumentException("Tipo de registro Comic Vine não suportado.");
        }
    }

    private static boolean isMarvel(JSONObject publisher) {
        String id = publisher.optString("id", "");
        String name = publisher.optString("name", "").trim().toLowerCase(Locale.ROOT);
        return "31".equals(id) || "4010-31".equals(id)
                || "marvel".equals(name) || "marvel comics".equals(name);
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    private static String readFully(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
        }
        return body.toString();
    }
}
