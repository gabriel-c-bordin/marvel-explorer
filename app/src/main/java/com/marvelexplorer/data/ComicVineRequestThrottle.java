package com.marvelexplorer.data;

final class ComicVineRequestThrottle {
    private static long lastRequestAt;

    private ComicVineRequestThrottle() {}

    static synchronized void waitForSlot() throws InterruptedException {
        long wait = 1100L - (System.currentTimeMillis() - lastRequestAt);
        if (wait > 0L) Thread.sleep(wait);
        lastRequestAt = System.currentTimeMillis();
    }
}
