package com.marvelexplorer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import com.marvelexplorer.data.ComicVineCatalogService;

public class HomeActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        BottomNavigation.prepareWindow(this);
        setContentView(R.layout.activity_home);
        BottomNavigation.attach(this, 1);

        findViewById(R.id.homeCharacters).setOnClickListener(view -> openCharacters());
        findViewById(R.id.homeTeams).setOnClickListener(view -> openCatalog(ComicVineCatalogService.Section.TEAMS));
        findViewById(R.id.homeStories).setOnClickListener(view -> openCatalog(ComicVineCatalogService.Section.VOLUMES));
        TextView attribution = findViewById(R.id.homeAttribution);
        attribution.setOnClickListener(view -> startActivity(new Intent(Intent.ACTION_VIEW,
                android.net.Uri.parse("https://comicvine.gamespot.com/"))));

    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshMissions();
    }

    private void refreshMissions() {
        bindMission(R.id.homeCharactersDescription, R.string.home_characters_page_description,
                GamificationManager.CHARACTERS);
        bindMission(R.id.homeTeamsDescription, R.string.home_teams_description,
                GamificationManager.TEAMS);
        bindMission(R.id.homeStoriesDescription, R.string.home_stories_description,
                GamificationManager.STORIES);
    }

    private void bindMission(int viewId, int descriptionId, String mission) {
        boolean complete = GamificationManager.completedToday(this, mission);
        int state = complete ? R.string.game_mission_reward : R.string.game_mission_open;
        ((TextView) findViewById(viewId)).setText(getString(state,
                getString(descriptionId), GamificationManager.rewardFor(mission)));
    }

    private void openCharacters() {
        ThemeSoundPlayer.play(this, ThemeSoundPlayer.CUE_OPEN);
        startActivity(new Intent(this, MainActivity.class));
    }

    private void openCatalog(ComicVineCatalogService.Section section) {
        ThemeSoundPlayer.play(this, ThemeSoundPlayer.CUE_OPEN);
        Intent intent = new Intent(this, CatalogActivity.class);
        intent.putExtra(CatalogActivity.EXTRA_SECTION, section.name());
        startActivity(intent);
    }

}
