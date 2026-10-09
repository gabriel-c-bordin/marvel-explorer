package com.marvelexplorer;

import android.app.Application;

/** Initializes Firebase when the project configuration file is present. */
public class MarvelExplorerApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        FirebaseProfileRepository.initialize(this);
    }
}
