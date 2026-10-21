package com.webapp.crazyshit;

import android.content.Intent;
import android.os.Bundle;
import androidx.activity.ComponentActivity;

public final class LibraryHubActivity extends ComponentActivity {
    private static final int SAVED_VIDEOS_REQUEST = 4101;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ZeroChillUi.applySystemBars(this);
        LibraryHubCompose.show(this);
    }

    void openSavedVideos(int startTab) {
        Intent intent = new Intent(this, FavoritesActivity.class)
                .putExtra(FavoritesActivity.EXTRA_START_TAB, startTab);
        startActivityForResult(intent, SAVED_VIDEOS_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != SAVED_VIDEOS_REQUEST || resultCode != RESULT_OK || data == null) return;
        String selected = data.getStringExtra(FavoritesActivity.EXTRA_SELECTED_URL);
        if (selected == null || selected.isEmpty()) return;
        setResult(RESULT_OK, data);
        finish();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
