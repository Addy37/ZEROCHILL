package com.webapp.crazyshit;

import android.os.Bundle;
import androidx.activity.ComponentActivity;

/** Lifecycle host for Compose Appearance. Preference and backup keys stay unchanged. */
public final class AppearanceActivity extends ComponentActivity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ZeroChillUi.applySystemBars(this);
        AppearanceComposeScreen.mount(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ZeroChillUi.applySystemBars(this);
    }
}
