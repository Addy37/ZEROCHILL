package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Standalone entry retained for shortcuts and existing callers. */
public final class SearchActivity extends Activity {
    public static final String EXTRA_QUERY = "query";
    SearchPanel panel;
    private SearchGlassOwner glassOwner;

    static Intent createBunkrSearch(Activity activity) {
        return new Intent(activity, SearchActivity.class).putExtra("scope", "bunkr");
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ZeroChillUi.applySystemBars(this);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        glassOwner = new SearchGlassOwner(this);
        panel = new SearchPanel(this, getIntent(), false, this::finish);
        panel.create(state);
        setContentView(panel.view);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> panel.back());
        }
        if (state == null && !getIntent().hasExtra(EXTRA_QUERY)) panel.focus();
    }
    @Override public void onBackPressed() { panel.back(); }
    @Override protected void onResume() { super.onResume(); panel.resume(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        panel.save(state); super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        panel.close(); glassOwner.close(); super.onDestroy();
    }
}
