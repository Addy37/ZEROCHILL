package com.webapp.crazyshit;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;

/** One short in-app reveal following the black Android 12+ launch window. */
public final class SplashActivity extends Activity {
    private static final long MIN_SPLASH_MS = 960L;
    private static final int[] REVEAL_LAYERS = {
            R.id.splashMascotGlow, R.id.splashHorns, R.id.splashFace, R.id.splashOutline, R.id.splashXEye,
            R.id.splashAngryEye, R.id.splashTeeth, R.id.splashTongue,
            R.id.splashWordmark, R.id.splashTagline
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable readinessRunnable = this::checkReadyToLaunch;

    private long splashStartedAt;
    private boolean leaving;
    private boolean handingOff;
    private boolean chaosHandoff;
    private boolean startupWizard;
    private boolean xmegaAccessRepair;
    private String launchAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        splashStartedAt = SystemClock.uptimeMillis();
        AppPerformance.startup("splash");
        launchAction = getIntent() == null ? null : getIntent().getAction();
        startupWizard = StartupWizardPolicy.shouldShow(this);
        xmegaAccessRepair = !startupWizard && XMegaDriveAgeSession.shouldShowRepair(this);
        chaosHandoff = shouldPreload(startupWizard || xmegaAccessRepair, launchAction);
        if (chaosHandoff) ChaosStartupPreloader.start(getApplicationContext());
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        setContentView(R.layout.activity_splash);
        int accent = AppearanceTheme.accent(this);
        MascotAccent.applyGlow(findViewById(R.id.splashMascotGlow), accent);
        MascotAccent.apply(findViewById(R.id.splashHorns),
                findViewById(R.id.splashOutline),
                findViewById(R.id.splashXEye), findViewById(R.id.splashAngryEye),
                findViewById(R.id.splashTongue), accent);
        ((TextView) findViewById(R.id.splashChill)).setTextColor(accent);

        animateSplash();
        handler.postDelayed(readinessRunnable, MIN_SPLASH_MS);
    }

    static boolean shouldPreload(boolean wizard, String action) {
        return !wizard && (!AppShortcuts.isShortcutAction(action) || AppShortcuts.isChaosAction(action));
    }

    private void animateSplash() {
        if (!ZeroChillMotion.animationsEnabled(this)) {
            for (int id : REVEAL_LAYERS) {
                View layer = findViewById(id);
                if (layer != null) layer.setAlpha(1f);
            }
            return;
        }
        reveal(R.id.splashMascotGlow, 0L, 170L);
        reveal(R.id.splashHorns, 0L, 170L);
        reveal(R.id.splashFace, 130L, 170L);
        reveal(R.id.splashOutline, 190L, 160L);
        reveal(R.id.splashXEye, 330L, 55L);
        reveal(R.id.splashAngryEye, 405L, 85L);
        reveal(R.id.splashTeeth, 495L, 80L);
        reveal(R.id.splashTongue, 565L, 75L);
        reveal(R.id.splashWordmark, 690L, 145L);
        reveal(R.id.splashTagline, 805L, 140L);

        View outline = findViewById(R.id.splashOutline);
        if (outline != null && ZeroChillMotion.animationsEnabled(this)) {
            ObjectAnimator pulse = ObjectAnimator.ofFloat(outline, View.ALPHA, 1f, 0.72f, 1f);
            pulse.setStartDelay(640L);
            pulse.setDuration(165L);
            pulse.start();
        }
    }

    private void reveal(int id, long delayMs, long durationMs) {
        View layer = findViewById(id);
        if (layer != null) layer.animate().alpha(1f).setStartDelay(delayMs)
                .setDuration(durationMs).start();
    }

    private void checkReadyToLaunch() {
        if (leaving) return;
        launchApp();
    }

    private void launchApp() {
        if (leaving) return;
        leaving = true;
        handingOff = true;
        handler.removeCallbacks(readinessRunnable);
        cancelReveals();

        Class<?> destination = startupWizard
                ? StartupWizardActivity.class
                : (xmegaAccessRepair ? XMegaDriveAccessSetupActivity.class : NativeMainActivity.class);
        Intent intent = new Intent(this, destination);
        intent.putExtra(WhatsNewPolicy.EXTRA_LAUNCHER_ENTRY,
                WhatsNewPolicy.isLauncherEntry(getIntent()));
        if (AppShortcuts.isShortcutAction(launchAction)) {
            intent.setAction(launchAction);
            if (!startupWizard && !xmegaAccessRepair) {
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            }
        }
        AppPerformance.record("Splash visible", SystemClock.uptimeMillis() - splashStartedAt);
        startActivity(intent);
        overridePendingTransition(0, 0);
        finish();
    }

    private void cancelReveals() {
        for (int id : REVEAL_LAYERS) {
            View layer = findViewById(id);
            if (layer != null) layer.animate().cancel();
        }
    }

    @Override
    protected void onDestroy() {
        leaving = true;
        handler.removeCallbacksAndMessages(null);
        cancelReveals();
        if (chaosHandoff && !handingOff) ChaosStartupHandoff.finish();
        super.onDestroy();
    }
}
