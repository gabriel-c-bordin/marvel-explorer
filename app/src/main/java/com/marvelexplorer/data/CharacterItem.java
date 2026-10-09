package com.marvelexplorer.data;

import java.util.List;

public final class CharacterItem {
    public final String id;
    public final String apiDetailUrl;
    public final String name;
    public final String realName;
    public final String publisher;
    public final String summary;
    public final String imageUrl;
    public final String firstAppearance;
    public final String issueCount;
    public final List<String> powers;

    public CharacterItem(
            String id,
            String apiDetailUrl,
            String name,
            String realName,
            String publisher,
            String summary,
            String imageUrl,
            String firstAppearance,
            String issueCount,
            List<String> powers) {
        this.id = id;
        this.apiDetailUrl = apiDetailUrl;
        this.name = name;
        this.realName = realName;
        this.publisher = publisher;
        this.summary = summary;
        this.imageUrl = imageUrl;
        this.firstAppearance = firstAppearance;
        this.issueCount = issueCount;
        this.powers = powers;
    }
}
