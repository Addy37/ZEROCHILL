package com.webapp.crazyshit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

public class SettingsActivity extends Activity {
    public static final String EXTRA_CHECK_FOR_UPDATES = "check_for_updates";

    private SharedPreferences prefs;
    private AppUpdater appUpdater;
    private AppBackupController backup;
    private TextView notificationStatusView;
    private TextView sourceConfigStatusView;
    private boolean notificationReceiverRegistered;

    private final BroadcastReceiver notificationCheckReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (notificationStatusView != null) {
                notificationStatusView.setText(
                        NotificationCoordinator.statusSummary(SettingsActivity.this)
                );
            }
            if (intent != null
                    && intent.getBooleanExtra(NotificationCoordinator.EXTRA_MANUAL_CHECK, false)) {
                Toast.makeText(
                        SettingsActivity.this,
                        "Creator update check finished.",
                        Toast.LENGTH_SHORT
                ).show();
            }
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        appUpdater = new AppUpdater(this);
        backup = new AppBackupController(this);
        ZeroChillUi.applySystemBars(this);
        buildUi();

        if (getIntent().getBooleanExtra(EXTRA_CHECK_FOR_UPDATES, false)) {
            getIntent().removeExtra(EXTRA_CHECK_FOR_UPDATES);
            getWindow().getDecorView().postDelayed(() -> {
                if (appUpdater != null) appUpdater.check(true);
            }, 250L);
        }
    }

    private void buildUi() {
        int background = ZeroChillUi.background(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(background);
        scroll.setClipToPadding(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), 0, dp(12), dp(24));
        root.setBackgroundColor(background);
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(4));

        TextView back = text("‹", 31f, Color.WHITE);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Back");
        back.setClickable(true);
        back.setFocusable(true);
        back.setOnClickListener(v -> {
            haptic(v);
            finish();
        });
        ZeroChillMotion.installPressFeedback(back);
        header.addView(back, new LinearLayout.LayoutParams(dp(42), dp(48)));

        TextView title = text("Settings", 25f, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titleParams.leftMargin = dp(6);
        header.addView(title, titleParams);
        root.addView(header);

        LinearLayout notifications = addGroup(root, "Notifications & updates");
        addSwitch(
                notifications,
                "Favorite creator updates",
                "Notify when your favorite creators have new content.",
                NotificationCoordinator.PREF_NEW_VIDEO_ALERTS,
                true
        );
        addAction(
                notifications,
                "Check frequency",
                "",
                NotificationCoordinator.frequencySummary(this),
                this::showNotificationFrequencyChoices
        );
        notificationStatusView = addAction(
                notifications,
                "Check creator updates",
                NotificationCoordinator.statusSummary(this),
                "",
                this::checkNotificationsNow
        );
        addSwitch(
                notifications,
                "App update alerts",
                "Notify when a new ZeroChill build is ready.",
                NotificationCoordinator.PREF_UPDATE_ALERTS,
                true
        );
        addAction(
                notifications,
                "Check for app update",
                "Installed version " + BuildConfig.VERSION_NAME,
                "",
                () -> {
                    if (appUpdater != null) appUpdater.check(true);
                }
        );
        if (getPackageName().endsWith(".dev")) {
            addAction(
                    notifications,
                    "Preview update experience",
                    "Test the floating update card, progress bar, and ready state. No APK is installed.",
                    "",
                    () -> {
                        AppUpdater.requestPreviewOnNextResume(this);
                        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
                        if (intent != null) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                            startActivity(intent);
                        }
                        finish();
                    }
            );
        }

        LinearLayout playback = addGroup(root, "Playback");
        addAction(
                playback,
                "ShitTok preloading",
                "",
                ChaosPreloadPolicy.summary(this),
                this::showChaosPreloadChoices
        );

        LinearLayout appearance = addGroup(root, "Appearance");
        addSwitch(
                appearance,
                "Haptic feedback",
                "",
                "haptics_enabled",
                true
        );
        addAction(
                appearance,
                "Startup tour",
                "Replay the ZEROCHILL introduction without resetting your data.",
                "",
                () -> {
                    Intent intent = new Intent(this, StartupWizardActivity.class);
                    intent.putExtra(StartupWizardPolicy.EXTRA_PREVIEW, true);
                    startActivity(intent);
                }
        );

        LinearLayout library = addGroup(root, "Library & data");
        addAction(
                library,
                "Favorite creators",
                "Search and open your starred creators.",
                "",
                () -> startActivity(new Intent(this, CreatorsActivity.class))
        );
        addAction(
                library,
                "Library",
                "Continue Watching, History and Watch Later.",
                "",
                () -> startActivity(new Intent(this, FavoritesActivity.class))
        );
        addAction(
                library,
                "Backup & restore",
                "Save or restore favorites, Watch Later and settings.",
                "",
                this::showBackupRestore
        );
        addAction(
                library,
                "Manage history & Watch Later",
                "Clear local viewing or saved-item data.",
                "",
                this::showManageLibraryData
        );

        LinearLayout privacy = addGroup(root, "Privacy");
        addAction(
                privacy,
                "Clear site data",
                "Sign out of websites and remove cookies and local storage.",
                "",
                this::confirmClearSiteData
        );

        LinearLayout advanced = addGroup(root, "Advanced");
        sourceConfigStatusView = addAction(
                advanced,
                "Source configuration",
                sourceConfigSummary(),
                "",
                this::showSourceConfigDetails
        );
        addAction(
                advanced,
                "Performance details",
                "Loading and scrolling timings from this session.",
                "",
                this::showPerformanceDetails
        );
        addAction(
                advanced,
                "Test notification",
                "Preview a ZeroChill update alert and check notification access.",
                "",
                () -> NotificationCoordinator.showTestNotification(this)
        );

        TextView footer = text(
                "ZeroChill  •  " + BuildConfig.VERSION_NAME
                        + "\nCommunity Android client",
                11.5f,
                ZeroChillUi.color(this, R.color.zc_text_muted)
        );
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(8), dp(20), dp(8), 0);
        root.addView(footer);

        FrameLayout shell = new FrameLayout(this);
        shell.setBackgroundColor(background);
        shell.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        View topScrim = new View(this);
        topScrim.setClickable(false);
        topScrim.setFocusable(false);
        topScrim.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        GradientDrawable scrim = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        Color.BLACK,
                        Color.argb(105, 0, 0, 0),
                        Color.TRANSPARENT
                }
        );
        topScrim.setBackground(scrim);
        FrameLayout.LayoutParams scrimParams =
                new FrameLayout.LayoutParams(-1, dp(40));
        scrimParams.gravity = Gravity.TOP;
        shell.addView(topScrim, scrimParams);

        applyWindowInsets(scroll, topScrim);
        setContentView(shell);
    }

    private void applyWindowInsets(ScrollView scroll, View topScrim) {
        if (scroll == null) return;
        scroll.setClipToPadding(true);
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (view, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
            );
            Insets cutout = windowInsets.getInsets(
                    WindowInsetsCompat.Type.displayCutout()
            );
            Insets statusBars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.statusBars()
            );

            int safeLeft = Math.max(systemBars.left, cutout.left);
            int safeRight = Math.max(systemBars.right, cutout.right);
            view.setPadding(safeLeft, 0, safeRight, systemBars.bottom);

            if (topScrim != null) {
                // Only shade the visible status-bar strip. Do not size this from the cutout
                // inset because some devices report a much taller safe area than the icons use.
                int statusProtection = Math.min(statusBars.top, dp(30));
                android.view.ViewGroup.LayoutParams params = topScrim.getLayoutParams();
                if (params != null && params.height != statusProtection) {
                    params.height = statusProtection;
                    topScrim.setLayoutParams(params);
                }
            }
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(scroll);
    }

    private LinearLayout addGroup(LinearLayout root, String title) {
        TextView label = text(
                title.toUpperCase(java.util.Locale.US),
                11f,
                ZeroChillUi.color(this, R.color.zc_text_muted)
        );
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        label.setLetterSpacing(0.06f);
        label.setPadding(dp(4), dp(14), dp(4), dp(6));
        root.addView(label);

        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(Color.rgb(8, 8, 10));
        card.setRadius(dp(16));
        card.setCardElevation(0f);
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(Color.rgb(43, 43, 50));
        card.setRippleColor(ColorStateList.valueOf(Color.rgb(28, 28, 34)));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        card.addView(content, new MaterialCardView.LayoutParams(-1, -2));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(1);
        root.addView(card, params);
        return content;
    }

    private void addSwitch(
            LinearLayout group,
            String title,
            String subtitle,
            String key,
            boolean defaultValue
    ) {
        addDividerIfNeeded(group);

        LinearLayout row = row();
        LinearLayout copy = copy(title, subtitle);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

        MaterialSwitch toggle = new MaterialSwitch(this);
        toggle.setChecked(prefs.getBoolean(key, defaultValue));
        toggle.setContentDescription(title);
        toggle.setMinWidth(dp(48));
        toggle.setMinimumWidth(dp(48));
        toggle.setMinHeight(dp(48));
        toggle.setMinimumHeight(dp(48));

        toggle.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean(key, checked).apply();
            haptic(button);

            if (NotificationCoordinator.isNotificationPreference(key)) {
                NotificationCoordinator.onPreferencesChanged(this);
                if (checked
                        && (NotificationCoordinator.PREF_NEW_VIDEO_ALERTS.equals(key)
                        || NotificationCoordinator.PREF_UPDATE_ALERTS.equals(key))) {
                    NotificationCoordinator.requestPermissionFromSettings(this);
                }
            }
        });

        row.setOnClickListener(v -> toggle.setChecked(!toggle.isChecked()));
        row.addView(toggle);
        group.addView(row);
    }

    private TextView addAction(
            LinearLayout group,
            String title,
            String subtitle,
            String trailing,
            Runnable action
    ) {
        addDividerIfNeeded(group);

        LinearLayout row = row();
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> {
            haptic(v);
            action.run();
        });
        ZeroChillMotion.installPressFeedback(row);

        LinearLayout copy = copy(title, subtitle);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

        if (trailing != null && !trailing.trim().isEmpty()) {
            TextView value = text(
                    trailing.trim(),
                    11.5f,
                    ZeroChillUi.color(this, R.color.zc_cyan)
            );
            value.setSingleLine(true);
            value.setEllipsize(TextUtils.TruncateAt.END);
            value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            value.setMaxWidth(dp(118));
            LinearLayout.LayoutParams valueParams =
                    new LinearLayout.LayoutParams(-2, dp(38));
            valueParams.leftMargin = dp(8);
            row.addView(value, valueParams);
        }

        TextView chevron = text(
                "›",
                24f,
                ZeroChillUi.color(this, R.color.zc_text_muted)
        );
        chevron.setGravity(Gravity.CENTER);
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(22), dp(40)));

        group.addView(row);

        if (copy.getChildCount() > 1) {
            return (TextView) copy.getChildAt(1);
        }
        return null;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(6), dp(8), dp(6));
        row.setMinimumHeight(dp(52));
        return row;
    }

    private LinearLayout copy(String title, String subtitle) {
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleView = text(
                title,
                14f,
                ZeroChillUi.color(this, R.color.zc_text_primary)
        );
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        copy.addView(titleView, new LinearLayout.LayoutParams(-1, -2));

        if (subtitle != null && !subtitle.trim().isEmpty()) {
            TextView sub = text(
                    subtitle.trim(),
                    10.5f,
                    Color.rgb(148, 148, 158)
            );
            sub.setLineSpacing(0f, 1.02f);
            sub.setPadding(0, dp(1), dp(4), 0);
            sub.setMaxLines(2);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            copy.addView(sub, new LinearLayout.LayoutParams(-1, -2));
        }
        return copy;
    }

    private void addDividerIfNeeded(LinearLayout group) {
        if (group.getChildCount() == 0) return;
        View divider = new View(this);
        divider.setBackgroundColor(
                ZeroChillUi.color(this, R.color.zc_divider)
        );
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, dp(1));
        params.leftMargin = dp(12);
        params.rightMargin = dp(12);
        group.addView(divider, params);
    }

    private void checkNotificationsNow() {
        if (notificationStatusView != null) {
            notificationStatusView.setText("Checking favorite creators…");
        }
        NotificationCoordinator.checkNow(this);
        Toast.makeText(
                this,
                "Checking supported creator sources.",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void showBackupRestore() {
        String[] actions = {"Export backup", "Restore backup"};
        new AlertDialog.Builder(this)
                .setTitle("Backup & restore")
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) backup.exportFile();
                    else backup.importFile();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showManageLibraryData() {
        String[] actions = {"Clear watch history", "Clear Watch Later"};
        new AlertDialog.Builder(this)
                .setTitle("Manage history & Watch Later")
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        confirmDestructive(
                                "Clear watch history?",
                                "This removes watched and Continue Watching state from this device.",
                                () -> {
                                    PlaybackHistoryStore.clear(this);
                                    Toast.makeText(
                                            this,
                                            "Watch history cleared.",
                                            Toast.LENGTH_SHORT
                                    ).show();
                                }
                        );
                    } else {
                        confirmDestructive(
                                "Clear Watch Later?",
                                "This removes every item saved to Watch Later on this device.",
                                () -> {
                                    FavoriteStore.clear(this);
                                    Toast.makeText(
                                            this,
                                            "Watch Later cleared.",
                                            Toast.LENGTH_SHORT
                                    ).show();
                                }
                        );
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmClearSiteData() {
        confirmDestructive(
                "Clear site data?",
                "This signs you out of websites and removes their cookies and local storage.",
                () -> {
                    CookieManager.getInstance().removeAllCookies(
                            value -> CookieManager.getInstance().flush()
                    );
                    WebStorage.getInstance().deleteAllData();
                    Toast.makeText(this, "Site data cleared.", Toast.LENGTH_SHORT).show();
                }
        );
    }

    private void confirmDestructive(String title, String message, Runnable action) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> action.run())
                .show();
    }

    private void showPerformanceDetails() {
        new AlertDialog.Builder(this)
                .setTitle("Performance details")
                .setMessage(AppPerformance.summary())
                .setPositiveButton("Close", null)
                .show();
    }

    private String sourceConfigSummary() {
        RemoteSourceConfigManager.initialize(this);
        SourceConfig config = RemoteSourceConfigManager.snapshotOrNull();
        if (config == null) {
            return RemoteSourceConfigManager.statusSummary(this);
        }
        return "v" + config.configVersion
                + " • "
                + RemoteSourceConfigManager.statusSummary(this);
    }

    private String sourceConfigDetails() {
        RemoteSourceConfigManager.initialize(this);
        SourceConfig config = RemoteSourceConfigManager.snapshotOrNull();
        if (config == null) {
            return "No active source configuration.\n\nStatus: "
                    + RemoteSourceConfigManager.statusSummary(this);
        }

        return "Active config: v"
                + config.configVersion
                + " • "
                + RemoteSourceConfigManager.activeOrigin()
                + "\nKill switches: "
                + onOff(config.sourceKillSwitchesEnabled)
                + "\nFallbacks: "
                + onOff(config.fallbacksEnabled)
                + "\n\nFapello: "
                + onOff(config.fapello.enabled)
                + "\nBunkr: "
                + onOff(config.bunkr.enabled)
                + "\nWikiFeet: "
                + onOff(config.wikiFeet.enabled)
                + "\nWikiFeet X: "
                + onOff(config.wikiFeetX.enabled)
                + "\n\nStatus: "
                + RemoteSourceConfigManager.statusSummary(this);
    }

    private String onOff(boolean enabled) {
        return enabled ? "ON" : "OFF";
    }

    private void showSourceConfigDetails() {
        new AlertDialog.Builder(this)
                .setTitle("Source configuration")
                .setMessage(sourceConfigDetails())
                .setNeutralButton(
                        "Refresh now",
                        (dialog, which) -> checkSourceConfigNow()
                )
                .setPositiveButton("Close", null)
                .show();
    }

    private void checkSourceConfigNow() {
        if (sourceConfigStatusView != null) {
            sourceConfigStatusView.setText("Refreshing source configuration…");
        }
        RemoteSourceConfigManager.refreshNow(this, success -> {
            if (sourceConfigStatusView != null) {
                sourceConfigStatusView.setText(sourceConfigSummary());
            }
            Toast.makeText(
                    this,
                    success
                            ? "Source configuration refreshed."
                            : "Source configuration refresh failed.",
                    Toast.LENGTH_SHORT
            ).show();
        });
    }

    private void showChaosPreloadChoices() {
        String[] choices = {
                "Full",
                "Wi-Fi / unmetered only",
                "Minimal"
        };
        new AlertDialog.Builder(this)
                .setTitle("ShitTok preloading")
                .setSingleChoiceItems(
                        choices,
                        ChaosPreloadPolicy.selectedIndex(this),
                        (dialog, which) -> {
                            ChaosPreloadPolicy.setMode(
                                    this,
                                    ChaosPreloadPolicy.modeForIndex(which)
                            );
                            dialog.dismiss();
                            recreate();
                        }
                )
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showNotificationFrequencyChoices() {
        int selectedHours = prefs.getInt(
                NotificationCoordinator.PREF_FREQUENCY_HOURS,
                1
        );
        int selected = selectedHours >= 6 ? 2 : selectedHours >= 3 ? 1 : 0;
        String[] choices = {
                "Every hour",
                "Every 3 hours",
                "Every 6 hours"
        };

        new AlertDialog.Builder(this)
                .setTitle("Creator update frequency")
                .setSingleChoiceItems(choices, selected, (dialog, which) -> {
                    int hours = which == 2 ? 6 : which == 1 ? 3 : 1;
                    NotificationCoordinator.setFrequency(this, hours);
                    dialog.dismiss();
                    recreate();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private void haptic(View view) {
        if (!prefs.getBoolean("haptics_enabled", true)) return;
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (backup != null) {
            backup.onResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        OledThemeController.applySoon(this);
        if (appUpdater != null) {
            appUpdater.onHostResume();
        }
        if (notificationStatusView != null) {
            notificationStatusView.setText(
                    NotificationCoordinator.statusSummary(this)
            );
        }
        if (sourceConfigStatusView != null) {
            sourceConfigStatusView.setText(sourceConfigSummary());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!notificationReceiverRegistered) {
            ContextCompat.registerReceiver(
                    this,
                    notificationCheckReceiver,
                    new IntentFilter(NotificationCoordinator.ACTION_CHECK_FINISHED),
                    ContextCompat.RECEIVER_NOT_EXPORTED
            );
            notificationReceiverRegistered = true;
        }
    }

    @Override
    protected void onStop() {
        if (notificationReceiverRegistered) {
            unregisterReceiver(notificationCheckReceiver);
            notificationReceiverRegistered = false;
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (appUpdater != null) {
            appUpdater.close();
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
