package com.marvelexplorer.data;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.net.HttpURLConnection;
import java.net.URL;

public final class ImageDownloader {
    private ImageDownloader() {}

    public static Bitmap download(String imageUrl) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(imageUrl).openConnection();
            connection.setRequestProperty("User-Agent", "MarvelExplorerAndroid/0.1 (academic project)");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(12000);
            if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) return null;
            try (java.io.InputStream input = connection.getInputStream()) {
                return BitmapFactory.decodeStream(input);
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
