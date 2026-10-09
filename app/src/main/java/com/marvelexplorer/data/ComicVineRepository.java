package com.marvelexplorer.data;

import android.content.Context;

public final class ComicVineRepository {
    private final CharacterPageCache cache;

    public ComicVineRepository(Context context) {
        cache = new CharacterPageCache(context);
    }

    public PageResult searchMarvelCharacters(String query, int offset, String apiKey)
            throws Exception {
        CharacterPageCache.CachedPage cached = cache.read(query, offset);
        if (cached != null && cached.fresh) {
            return new PageResult(cached.page, true, false, null);
        }

        try {
            ComicVineService.SearchPage page = ComicVineService.searchMarvelCharacters(query, offset, apiKey);
            cache.write(query, offset, page);
            return new PageResult(page, false, false, null);
        } catch (Exception requestFailure) {
            if (cached != null) {
                return new PageResult(cached.page, true, true, requestFailure.getMessage());
            }
            throw requestFailure;
        }
    }

    public static final class PageResult {
        public final ComicVineService.SearchPage page;
        public final boolean fromCache;
        public final boolean stale;
        public final String warning;

        PageResult(ComicVineService.SearchPage page, boolean fromCache, boolean stale, String warning) {
            this.page = page;
            this.fromCache = fromCache;
            this.stale = stale;
            this.warning = warning;
        }
    }
}
