package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.FrameLayout;

/** One-time upgrade repair that unifies existing 18+ acceptance with XMegaDrive access. */
public final class XMegaDriveAccessSetupActivity extends Activity {
    private XMegaDriveAgePrompt prompt;
    private boolean leaving;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ZeroChillUi.applySystemBars(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);

        if (!XMegaDriveAgeSession.shouldShowRepair(this)) {
            completeLaunch();
            return;
        }
        showPrompt();
    }

    private void showPrompt() {
        prompt = new XMegaDriveAgePrompt(
                this,
                active -> {
                    XMegaDriveAgeSession.markSetupComplete(this);
                    XMegaDriveAgeSession.authorize(this, ready -> runOnUiThread(() -> {
                        if (leaving || isFinishing() || isDestroyed()) return;
                        if (!ready) {
                            active.showFailure();
                            return;
                        }
                        active.showReady();
                        active.rootForTest().postDelayed(this::completeLaunch, 220L);
                    }));
                },
                () -> {
                    XMegaDriveAgeSession.markRepairHandled(this);
                    completeLaunch();
                }
        );
        prompt.show();
    }

    private void completeLaunch() {
        if (leaving) return;
        leaving = true;
        if (prompt != null) prompt.dismiss();
        Intent app = new Intent(this, NativeMainActivity.class);
        Intent entry = getIntent();
        if (WhatsNewPolicy.allowsAutomaticDisplay(entry)) {
            app.putExtra(WhatsNewPolicy.EXTRA_LAUNCHER_ENTRY, true);
        }
        String action = entry == null ? null : entry.getAction();
        if (AppShortcuts.isShortcutAction(action)) {
            app.setAction(action);
            app.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        }
        startActivity(app);
        overridePendingTransition(0, 0);
        finish();
    }

    @Override
    public void onBackPressed() {
        XMegaDriveAgeSession.markRepairHandled(this);
        completeLaunch();
    }
}
