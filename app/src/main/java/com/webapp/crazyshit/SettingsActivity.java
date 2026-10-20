package com.webapp.crazyshit;

import androidx.activity.ComponentActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;


public class SettingsActivity extends ComponentActivity {
    public static final String EXTRA_CHECK_FOR_UPDATES = "check_for_updates";

    private SharedPreferences prefs;
    private AppUpdater appUpdater;
    private AppBackupController backup;
    private SettingsCompose presentation;
    private boolean notificationReceiverRegistered;

    private final BroadcastReceiver notificationCheckReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (presentation != null) {
                presentation.setNotificationStatus(
                        NotificationCoordinator.statusSummary(SettingsActivity.this)
                );
            }
            if (intent != null
                    && intent.getBooleanExtra(NotificationCoordinator.EXTRA_MANUAL_CHECK, false)) {
                ZeroChillToast.makeText(
                        SettingsActivity.this,
                        "Creator update check finished.",
                        ZeroChillToast.LENGTH_SHORT
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
        java.util.List<SettingsCompose.Group> groups = new java.util.ArrayList<>();
        SettingsCompose.Group notifications = addGroup(groups, "Notifications & updates");
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
        addAction(
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

        SettingsCompose.Group playback = addGroup(groups, "Playback");
        addAction(
                playback,
                "ShitTok preloading",
                "",
                ChaosPreloadPolicy.summary(this),
                this::showChaosPreloadChoices
        );
        addAction(
                playback,
                "Blocked ShitTok creators",
                "Unblock OnlyFap creators hidden from ShitTok.",
                ShitTokBlockedCreatorStore.summary(this),
                this::showBlockedShitTokCreators
        );

        SettingsCompose.Group appearance = addGroup(groups, "Appearance");
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

        SettingsCompose.Group library = addGroup(groups, "Library & data");
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

        SettingsCompose.Group privacy = addGroup(groups, "Privacy");
        addAction(
                privacy,
                "Clear site data",
                "Sign out of websites and remove cookies and local storage.",
                "",
                this::confirmClearSiteData
        );

        SettingsCompose.Group advanced = addGroup(groups, "Advanced");
        addAction(
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

        if (presentation == null) presentation = new SettingsCompose(this);
        presentation.show(groups);
    }

    private SettingsCompose.Group addGroup(java.util.List<SettingsCompose.Group> groups, String title) {
        SettingsCompose.Group group = new SettingsCompose.Group(title);
        groups.add(group);
        return group;
    }

    private void addSwitch(SettingsCompose.Group group, String title, String subtitle,
            String key, boolean defaultValue) {
        group.toggle(title, subtitle, prefs.getBoolean(key, defaultValue), checked -> {
            prefs.edit().putBoolean(key, checked).apply();
            haptic(getWindow().getDecorView());
            if (NotificationCoordinator.isNotificationPreference(key)) {
                NotificationCoordinator.onPreferencesChanged(this);
                if (checked && (NotificationCoordinator.PREF_NEW_VIDEO_ALERTS.equals(key)
                        || NotificationCoordinator.PREF_UPDATE_ALERTS.equals(key))) {
                    NotificationCoordinator.requestPermissionFromSettings(this);
                }
            }
            buildUi();
        });
    }

    private void addAction(SettingsCompose.Group group, String title, String subtitle,
            String trailing, Runnable action) {
        group.action(title, subtitle, trailing, () -> {
            haptic(getWindow().getDecorView());
            action.run();
        });
    }

    private void checkNotificationsNow() {
        if (presentation != null) {
            presentation.setNotificationStatus("Checking favorite creators…");
        }
        NotificationCoordinator.checkNow(this);
        ZeroChillToast.makeText(
                this,
                "Checking supported creator sources.",
                ZeroChillToast.LENGTH_SHORT
        ).show();
    }

    private void showBackupRestore() {
        String[] actions = {"Export backup", "Restore backup"};
        new ZeroChillDialog.Builder(this)
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
        new ZeroChillDialog.Builder(this)
                .setTitle("Manage history & Watch Later")
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        confirmDestructive(
                                "Clear watch history?",
                                "This removes watched and Continue Watching state from this device.",
                                () -> {
                                    PlaybackHistoryStore.clear(this);
                                    ZeroChillToast.makeText(
                                            this,
                                            "Watch history cleared.",
                                            ZeroChillToast.LENGTH_SHORT
                                    ).show();
                                }
                        );
                    } else {
                        confirmDestructive(
                                "Clear Watch Later?",
                                "This removes every item saved to Watch Later on this device.",
                                () -> {
                                    FavoriteStore.clear(this);
                                    ZeroChillToast.makeText(
                                            this,
                                            "Watch Later cleared.",
                                            ZeroChillToast.LENGTH_SHORT
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
                    ZeroChillToast.makeText(this, "Site data cleared.", ZeroChillToast.LENGTH_SHORT).show();
                }
        );
    }

    private void confirmDestructive(String title, String message, Runnable action) {
        new ZeroChillDialog.Builder(this).destructive()
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> action.run())
                .show();
    }

    private void showPerformanceDetails() {
        String report = ShitTokPoolDiagnostics.userReport();
        TextView details = new TextView(this);
        details.setText(report);
        details.setTextColor(UiPalette.TEXT_PRIMARY);
        details.setTextSize(12);
        details.setTextIsSelectable(true);
        details.setPadding(dp(20), dp(12), dp(20), dp(12));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(details);
        new ZeroChillDialog.Builder(this)
                .setTitle("Performance details")
                .setView(scroll)
                .setNeutralButton("Copy diagnostics", (dialog, which) -> {
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                            getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("ZEROCHILL diagnostics", report));
                        ZeroChillToast.makeText(this, "Diagnostics copied.", ZeroChillToast.LENGTH_SHORT).show();
                    }
                })
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
        new ZeroChillDialog.Builder(this)
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
        if (presentation != null) {
            presentation.setSourceStatus("Refreshing source configuration…");
        }
        RemoteSourceConfigManager.refreshNow(this, success -> {
            if (presentation != null) {
                presentation.setSourceStatus(sourceConfigSummary());
            }
            ZeroChillToast.makeText(
                    this,
                    success
                            ? "Source configuration refreshed."
                            : "Source configuration refresh failed.",
                    ZeroChillToast.LENGTH_SHORT
            ).show();
        });
    }

    private void showBlockedShitTokCreators() {
        java.util.List<ShitTokBlockedCreatorStore.BlockedCreator> blocked =
                ShitTokBlockedCreatorStore.all(this);
        if (blocked.isEmpty()) {
            ZeroChillToast.makeText(
                    this,
                    "No creators are blocked from ShitTok.",
                    ZeroChillToast.LENGTH_SHORT
            ).show();
            return;
        }

        String[] labels = new String[blocked.size()];
        for (int i = 0; i < blocked.size(); i++) labels[i] = blocked.get(i).label;
        new ZeroChillDialog.Builder(this)
                .setTitle("Blocked ShitTok creators")
                .setItems(labels, (dialog, which) -> {
                    if (which < 0 || which >= blocked.size()) return;
                    ShitTokBlockedCreatorStore.BlockedCreator creator = blocked.get(which);
                    ShitTokBlockedCreatorStore.unblock(this, creator.key);
                    ZeroChillToast.makeText(
                            this,
                            "Unblocked " + creator.label + ".",
                            ZeroChillToast.LENGTH_SHORT
                    ).show();
                    dialog.dismiss();
                    recreate();
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showChaosPreloadChoices() {
        String[] choices = {
                "Full",
                "Wi-Fi / unmetered only",
                "Minimal"
        };
        new ZeroChillDialog.Builder(this)
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

        new ZeroChillDialog.Builder(this)
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
        buildUi();
        if (appUpdater != null) {
            appUpdater.onHostResume();
        }
        if (presentation != null) {
            presentation.setNotificationStatus(
                    NotificationCoordinator.statusSummary(this)
            );
        }
        if (presentation != null) {
            presentation.setSourceStatus(sourceConfigSummary());
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
