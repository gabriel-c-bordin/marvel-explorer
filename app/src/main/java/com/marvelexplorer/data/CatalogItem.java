package com.marvelexplorer.data;

public final class CatalogItem {
    public final String id;
    public final String name;
    public final String publisher;
    public final String summary;
    public final String imageUrl;
    public final String siteUrl;
    public final String apiDetailUrl;
    public final String detailLabel;
    public final String detailValue;

    public CatalogItem(String id, String name, String publisher, String summary, String imageUrl,
                       String siteUrl, String apiDetailUrl, String detailLabel, String detailValue) {
        this.id = id;
        this.name = name;
        this.publisher = publisher;
        this.summary = summary;
        this.imageUrl = imageUrl;
        this.siteUrl = siteUrl;
        this.apiDetailUrl = apiDetailUrl;
        this.detailLabel = detailLabel;
        this.detailValue = detailValue;
    }
}
