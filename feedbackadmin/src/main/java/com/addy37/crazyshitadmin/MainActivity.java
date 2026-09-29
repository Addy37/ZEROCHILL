package com.addy37.crazyshitadmin;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity {
    static final String EXTRA_DESTINATION = "destination";
    private static final String DASHBOARD = "dashboard";
    private static final String FEEDBACK = "feedback";
    private static final String SOURCES = "sources";

    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final List<AdminRepository.Item> allItems = new ArrayList<>();
    private final List<MaterialButton> filterButtons = new ArrayList<>();
    private FeedbackAdapter adapter;
    private SwipeRefreshLayout swipe;
    private TextView count;
    private String filter = "all";
    private String currentDestination = DASHBOARD;
    private boolean resumedOnce;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        showStartScreen();
        if (android.os.Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (resumedOnce && FEEDBACK.equals(currentDestination) && swipe != null) load();
        resumedOnce = true;
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (!SecureTokenStore.read(this).isEmpty()) route(intent.getStringExtra(EXTRA_DESTINATION));
    }

    @Override public void onBackPressed() {
        if (!DASHBOARD.equals(currentDestination) && !SecureTokenStore.read(this).isEmpty()) {
            showDashboard();
        } else {
            super.onBackPressed();
        }
    }

    private void showStartScreen() {
        if (SecureTokenStore.read(this).isEmpty()) showPairing();
        else route(getIntent().getStringExtra(EXTRA_DESTINATION));
    }

    private void route(String destination) {
        if (FEEDBACK.equals(destination)) showInbox();
        else if (SOURCES.equals(destination)) showSourceControl();
        else showDashboard();
    }

    private void showPairing() {
        currentDestination = DASHBOARD;
        LinearLayout root = vertical(24);
        root.setGravity(Gravity.CENTER_VERTICAL);

        ImageView mascot = new ImageView(this);
        mascot.setImageResource(R.drawable.ic_admin);
        mascot.setContentDescription(null);
        root.addView(mascot, new LinearLayout.LayoutParams(dp(88), dp(88)));

        TextView eyebrow = label("PRIVATE CONTROL CENTER");
        TextView title = text("ZeroChill Admin", 32, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        TextView detail = text("Connect once to manage analytics, sources, and feedback.",
                15, color(R.color.app_on_surface_variant));
        detail.setPadding(0, dp(8), 0, dp(26));

        EditText token = new EditText(this);
        token.setHint("Admin token");
        token.setSingleLine(true);
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        token.setTextColor(color(R.color.app_on_surface));
        token.setHintTextColor(color(R.color.app_on_surface_variant));

        MaterialButton connect = primaryButton("Connect securely");
        connect.setMinHeight(dp(52));
        ProgressBar progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);

        root.addView(eyebrow);
        root.addView(title);
        root.addView(detail);
        root.addView(token);
        root.addView(connect, matchWrap(dp(10), 0));
        root.addView(progress);
        setContentView(root);

        connect.setOnClickListener(v -> {
            String value = token.getText().toString().trim();
            if (value.length() < 24) { token.setError("Paste the complete admin token"); return; }
            connect.setEnabled(false);
            progress.setVisibility(View.VISIBLE);
            network.execute(() -> {
                try {
                    AdminRepository.list(value);
                    SecureTokenStore.save(this, value);
                    runOnUiThread(() -> {
                        ((AdminApplication) getApplication()).scheduleNotifications();
                        showDashboard();
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> {
                        connect.setEnabled(true);
                        progress.setVisibility(View.GONE);
                        toast(message(error));
                    });
                }
            });
        });
    }

    private void showDashboard() {
        currentDestination = DASHBOARD;
        LinearLayout shell = screenShell();
        LinearLayout content = column(0);
        content.setPadding(dp(14), dp(12), dp(14), dp(6));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = column(0);
        TextView eyebrow = label("ZEROCHILL ADMIN");
        TextView title = text("Dashboard", 26, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        TextView subtitle = text("Live control center", 12, color(R.color.app_on_surface_variant));
        titles.addView(eyebrow);
        titles.addView(title);
        titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        MaterialButton lock = compactButton("Lock");
        header.addView(lock);
        content.addView(header);

        TextView dashboardStatus = text("Refreshing live data…", 11, color(R.color.app_on_surface_variant));
        dashboardStatus.setPadding(0, dp(5), 0, dp(2));
        content.addView(dashboardStatus);

        LinearLayout userMetrics = new LinearLayout(this);
        userMetrics.setOrientation(LinearLayout.HORIZONTAL);
        TextView today = metricNumber("–", "Today");
        TextView week = metricNumber("–", "Week");
        TextView month = metricNumber("–", "Month");
        userMetrics.addView(metricBlock(today), weighted());
        userMetrics.addView(metricBlock(week), weighted());
        userMetrics.addView(metricBlock(month), weighted());
        content.addView(panel("ACTIVE USERS", null, userMetrics));

        LinearLayout overview = column(0);
        TextView topCreator = text("Loading…", 18, Color.WHITE);
        topCreator.setTypeface(null, Typeface.BOLD);
        TextView creatorDetail = text("Trending creator", 11, color(R.color.app_on_surface_variant));
        creatorDetail.setPadding(0, dp(1), 0, dp(8));
        TextView feedbackSummary = text("Feedback  ·  loading", 13, color(R.color.app_on_surface));
        feedbackSummary.setPadding(0, dp(5), 0, dp(6));
        TextView sourceSummary = text("Sources  ·  loading", 12, color(R.color.app_on_surface_variant));
        overview.addView(topCreator);
        overview.addView(creatorDetail);
        overview.addView(feedbackSummary);
        overview.addView(sourceSummary);
        content.addView(panel("AT A GLANCE", null, overview));

        LinearLayout quickBody = new LinearLayout(this);
        quickBody.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton analytics = compactButton("Analytics");
        MaterialButton manageSources = compactButton("Sources");
        quickBody.addView(analytics, weighted());
        quickBody.addView(manageSources, weighted());
        content.addView(panel("QUICK ACTIONS", null, quickBody));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        shell.addView(bottomNav(DASHBOARD));
        setContentView(shell);

        lock.setOnClickListener(v -> confirmLock());
        analytics.setOnClickListener(v -> openAnalytics());
        manageSources.setOnClickListener(v -> showSourceControl());

        String token = SecureTokenStore.read(this);
        network.execute(() -> {
            try {
                AdminRepository.AnalyticsDashboard dashboard = AdminRepository.analytics(token);
                List<AdminRepository.Item> feedbackItems = AdminRepository.list(token);
                org.json.JSONObject configItem = AdminRepository.currentConfig(token);
                runOnUiThread(() -> {
                    today.setText(String.format(Locale.US, "%,d", dashboard.dailyUsers));
                    week.setText(String.format(Locale.US, "%,d", dashboard.weeklyUsers));
                    month.setText(String.format(Locale.US, "%,d", dashboard.monthlyUsers));

                    if (dashboard.creators.isEmpty()) {
                        topCreator.setText("No trend yet");
                        creatorDetail.setText("Creator interest will appear as users browse.");
                    } else {
                        AdminRepository.AnalyticsRow top = dashboard.creators.get(0);
                        topCreator.setText(top.value);
                        creatorDetail.setText(String.format(Locale.US,
                                "%,d users this week · %,d opens", top.uniqueUsers, top.eventCount));
                    }

                    int submitted = 0;
                    for (AdminRepository.Item item : feedbackItems) {
                        if ("submitted".equals(item.status)) submitted++;
                    }
                    feedbackSummary.setText(String.format(Locale.US,
                            "Feedback  ·  %,d new  ·  %,d total", submitted, feedbackItems.size()));
                    sourceSummary.setText("Sources  ·  " + dashboardSourceStatus(configItem).replace("\n", "  ·  "));
                    dashboardStatus.setText("Updated now");
                });
            } catch (SecurityException error) {
                runOnUiThread(() -> {
                    SecureTokenStore.clear(this);
                    toast(error.getMessage());
                    showPairing();
                });
            } catch (Exception error) {
                runOnUiThread(() -> dashboardStatus.setText("Refresh failed: " + message(error)));
            }
        });
    }

    private void showInbox() {
        currentDestination = FEEDBACK;
        LinearLayout shell = screenShell();
        LinearLayout page = column(0);
        page.setPadding(dp(14), dp(12), dp(14), 0);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = column(0);
        TextView eyebrow = label("USER VOICE");
        TextView title = text("Feedback", 26, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        count = text("", 12, color(R.color.app_on_surface_variant));
        titles.addView(eyebrow);
        titles.addView(title);
        titles.addView(count);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        MaterialButton lock = compactButton("Lock");
        header.addView(lock);
        page.addView(header);

        filterButtons.clear();
        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        filters.setPadding(0, dp(8), 0, dp(6));
        addFilter(filters, "All", "all");
        addFilter(filters, "Unread", "unread");
        addFilter(filters, "Bugs", "bug_report");
        addFilter(filters, "Requests", "feature_request");
        page.addView(filters);

        RecyclerView list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, dp(6));
        adapter = new FeedbackAdapter(this::showDetail);
        list.setAdapter(adapter);
        swipe = new SwipeRefreshLayout(this);
        swipe.setColorSchemeColors(color(R.color.app_primary));
        swipe.addView(list);
        swipe.setOnRefreshListener(this::load);
        page.addView(swipe, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        shell.addView(page, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        shell.addView(bottomNav(FEEDBACK));
        setContentView(shell);
        lock.setOnClickListener(v -> confirmLock());
        styleFilterButtons();
        load();
    }

    private void showSourceControl() {
        currentDestination = SOURCES;
        LinearLayout shell = screenShell();
        LinearLayout page = column(0);
        page.setPadding(dp(14), dp(12), dp(14), dp(6));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = column(0);
        TextView eyebrow = label("REMOTE CONTROL");
        TextView title = text("Source Control", 26, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        TextView subtitle = text("Remote switches and source configuration",
                12, color(R.color.app_on_surface_variant));
        titles.addView(eyebrow);
        titles.addView(title);
        titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        MaterialButton lock = compactButton("Lock");
        header.addView(lock);
        page.addView(header);

        TextView status = text("Loading published configuration…", 12,
                color(R.color.app_on_surface_variant));
        status.setPadding(0, dp(6), 0, dp(5));
        page.addView(status);

        SourceConfigEditor editor = new SourceConfigEditor(this);
        ScrollView editorScroll = new ScrollView(this);
        editorScroll.setFillViewport(true);
        editorScroll.setClipToPadding(false);
        editorScroll.addView(editor, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(editorScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(4), 0, 0);
        MaterialButton validate = compactButton("Validate");
        MaterialButton publish = primaryButton("Publish");
        MaterialButton history = compactButton("History");
        publish.setEnabled(false);
        actions.addView(validate, weighted());
        actions.addView(publish, weighted());
        actions.addView(history, weighted());
        page.addView(actions);

        shell.addView(page, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        shell.addView(bottomNav(SOURCES));
        setContentView(shell);
        lock.setOnClickListener(v -> confirmLock());

        final long[] currentVersion = {0L};
        final String[] validatedText = {""};
        editor.setOnChangedListener(() -> {
            if (!validatedText[0].isEmpty()) status.setText("Changed · validate again before publishing");
            validatedText[0] = "";
            publish.setEnabled(false);
        });

        network.execute(() -> {
            try {
                org.json.JSONObject item = AdminRepository.currentConfig(SecureTokenStore.read(this));
                runOnUiThread(() -> {
                    if (item == null) {
                        org.json.JSONObject defaults = bundledDefaults();
                        editor.setConfig(defaults);
                        status.setText("No published config · bundled defaults loaded");
                        return;
                    }
                    currentVersion[0] = item.optLong("config_version");
                    org.json.JSONObject config = item.optJSONObject("config");
                    editor.setConfig(config);
                    status.setText(configStatus(item).replace("\n", "  ·  "));
                });
            } catch (Exception error) {
                runOnUiThread(() -> status.setText("Load failed: " + message(error)));
            }
        });

        validate.setOnClickListener(v -> {
            validate.setEnabled(false);
            publish.setEnabled(false);
            status.setText("Validating…");
            network.execute(() -> {
                try {
                    org.json.JSONObject candidate = editor.getConfig();
                    candidate.put("configVersion", currentVersion[0] + 1L);
                    candidate.put("updatedAt", isoNow());
                    AdminRepository.validateConfig(SecureTokenStore.read(this), candidate);
                    validatedText[0] = candidate.toString();
                    runOnUiThread(() -> {
                        editor.setConfig(candidate);
                        status.setText("Version " + (currentVersion[0] + 1L) + " validated · ready to publish");
                        validate.setEnabled(true);
                        publish.setEnabled(true);
                    });
                } catch (Exception error) {
                    validatedText[0] = "";
                    runOnUiThread(() -> {
                        status.setText("Rejected: " + message(error));
                        validate.setEnabled(true);
                    });
                }
            });
        });

        publish.setOnClickListener(v -> {
            try {
                if (!editor.getConfig().toString().equals(validatedText[0])) {
                    publish.setEnabled(false);
                    status.setText("Changed after validation · validate again");
                    return;
                }
            } catch (Exception error) {
                publish.setEnabled(false);
                status.setText("Could not read changes · validate again");
                return;
            }
            publish.setEnabled(false);
            status.setText("Publishing…");
            network.execute(() -> {
                try {
                    long version = AdminRepository.publishConfig(SecureTokenStore.read(this),
                            new org.json.JSONObject(validatedText[0]));
                    currentVersion[0] = version;
                    validatedText[0] = "";
                    runOnUiThread(() -> status.setText("Version " + version + " published · live now"));
                } catch (Exception error) {
                    runOnUiThread(() -> {
                        status.setText("Publish failed: " + message(error));
                        publish.setEnabled(true);
                    });
                }
            });
        });

        history.setOnClickListener(v -> loadConfigHistory(status, currentVersion[0]));
    }

    private LinearLayout bottomNav(String selected) {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(4), dp(4), dp(4), dp(5));
        nav.setBackgroundColor(color(R.color.app_surface));

        nav.addView(navButton("Dashboard", DASHBOARD, selected, v -> showDashboard()), weighted());
        nav.addView(navButton("Analytics", "analytics", selected, v -> openAnalytics()), weighted());
        nav.addView(navButton("Sources", SOURCES, selected, v -> showSourceControl()), weighted());
        nav.addView(navButton("Feedback", FEEDBACK, selected, v -> showInbox()), weighted());
        return nav;
    }

    private MaterialButton navButton(String title, String destination, String selected, View.OnClickListener click) {
        boolean active = destination.equals(selected);
        MaterialButton button = new MaterialButton(this);
        button.setText(title);
        button.setTextSize(10);
        button.setAllCaps(false);
        button.setSingleLine(true);
        button.setMaxLines(1);
        button.setMinWidth(0);
        button.setPadding(dp(2), 0, dp(2), 0);
        button.setMinHeight(dp(38));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setCornerRadius(dp(11));
        button.setBackgroundTintList(ColorStateList.valueOf(active
                ? color(R.color.app_surface_raised) : color(R.color.app_surface)));
        button.setStrokeColor(ColorStateList.valueOf(active
                ? color(R.color.app_primary) : color(R.color.app_surface)));
        button.setStrokeWidth(active ? dp(1) : 0);
        button.setTextColor(active ? color(R.color.app_primary) : color(R.color.app_on_surface_variant));
        button.setOnClickListener(click);
        return button;
    }

    private void openAnalytics() {
        startActivity(new Intent(this, AnalyticsActivity.class));
    }

    private void confirmLock() {
        new AlertDialog.Builder(this)
                .setTitle("Lock admin app?")
                .setMessage("You will need the admin token to connect again.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Lock", (dialog, which) -> {
                    SecureTokenStore.clear(this);
                    showPairing();
                })
                .show();
    }

    private void loadConfigHistory(TextView status, long activeVersion) {
        status.setText("Loading configuration history…");
        network.execute(() -> {
            try {
                List<AdminRepository.ConfigVersion> versions =
                        AdminRepository.configHistory(SecureTokenStore.read(this));
                runOnUiThread(() -> showHistoryDialog(versions, activeVersion));
            } catch (Exception error) {
                runOnUiThread(() -> status.setText("History failed: " + message(error)));
            }
        });
    }

    private void showHistoryDialog(List<AdminRepository.ConfigVersion> versions, long activeVersion) {
        LinearLayout rows = vertical(8);
        for (AdminRepository.ConfigVersion version : versions) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            String label = "Version " + version.version + (version.active ? "  ACTIVE" : "") +
                    "\n" + version.action.toUpperCase(Locale.US) + "  ·  " + formatDate(version.updatedAt);
            row.addView(text(label, 14, color(R.color.app_on_surface)),
                    new LinearLayout.LayoutParams(0, -2, 1));
            MaterialButton rollback = compactButton("Roll back");
            rollback.setEnabled(!version.active && version.version < activeVersion);
            rollback.setOnClickListener(v -> confirmRollback(version.version));
            row.addView(rollback);
            rows.addView(row);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(rows);
        new AlertDialog.Builder(this).setTitle("Published versions").setView(scroll)
                .setPositiveButton("Close", null).show();
    }

    private void confirmRollback(long version) {
        new AlertDialog.Builder(this)
                .setTitle("Roll back source configuration?")
                .setMessage("This republishes version " + version + " as a new higher version for every app.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Roll back", (dialog, which) -> network.execute(() -> {
                    try {
                        long published = AdminRepository.rollbackConfig(SecureTokenStore.read(this), version);
                        runOnUiThread(() -> {
                            toast("Rolled back as version " + published);
                            showSourceControl();
                        });
                    } catch (Exception error) {
                        runOnUiThread(() -> toast("Rollback failed: " + message(error)));
                    }
                })).show();
    }

    private String dashboardSourceStatus(org.json.JSONObject item) {
        if (item == null) return "No published configuration";
        org.json.JSONObject config = item.optJSONObject("config");
        org.json.JSONObject sources = config == null ? null : config.optJSONObject("sources");
        if (sources == null) return "Published configuration loaded";
        return sourceDot(sources, "fapello", "Fapello") + "    " +
                sourceDot(sources, "bunkr", "Bunkr") + "\n" +
                sourceDot(sources, "wikifeet", "WikiFeet") + "    " +
                sourceDot(sources, "wikifeetx", "WikiFeet X");
    }

    private String sourceDot(org.json.JSONObject sources, String id, String label) {
        org.json.JSONObject source = sources.optJSONObject(id);
        return (source != null && source.optBoolean("enabled") ? "● " : "○ ") + label;
    }

    private String configStatus(org.json.JSONObject item) {
        org.json.JSONObject config = item.optJSONObject("config");
        org.json.JSONObject sources = config == null ? null : config.optJSONObject("sources");
        if (sources == null) return "Published version " + item.optLong("config_version");
        return "Published version " + item.optLong("config_version") + "\n" +
                sourceState(sources, "fapello", "Fapello") + "  ·  " +
                sourceState(sources, "bunkr", "Bunkr") + "  ·  " +
                sourceState(sources, "wikifeet", "WikiFeet") + "  ·  " +
                sourceState(sources, "wikifeetx", "WikiFeet X");
    }

    private String sourceState(org.json.JSONObject sources, String id, String label) {
        org.json.JSONObject source = sources.optJSONObject(id);
        return label + ": " + (source != null && source.optBoolean("enabled") ? "ON" : "OFF");
    }

    private static String isoNow() {
        SimpleDateFormat value = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        value.setTimeZone(TimeZone.getTimeZone("UTC"));
        return value.format(new Date());
    }

    private static String message(Exception error) {
        return error.getMessage() == null ? "Request failed" : error.getMessage();
    }

    private org.json.JSONObject bundledDefaults() {
        try (java.io.InputStream input = getAssets().open("source_config_defaults.json")) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            return new org.json.JSONObject(output.toString(java.nio.charset.StandardCharsets.UTF_8.name()));
        } catch (Exception error) {
            return null;
        }
    }

    private void addFilter(LinearLayout row, String label, String value) {
        MaterialButton item = compactButton(label);
        item.setTag(value);
        item.setTextSize(10);
        item.setMinHeight(dp(34));
        item.setOnClickListener(v -> {
            filter = value;
            applyFilter();
        });
        filterButtons.add(item);
        row.addView(item, weighted());
    }

    private void styleFilterButtons() {
        for (MaterialButton item : filterButtons) {
            boolean selected = String.valueOf(item.getTag()).equals(filter);
            item.setBackgroundTintList(ColorStateList.valueOf(selected
                    ? color(R.color.app_surface_raised) : color(R.color.app_surface)));
            item.setStrokeColor(ColorStateList.valueOf(selected
                    ? color(R.color.app_primary) : color(R.color.app_divider)));
            item.setStrokeWidth(dp(1));
            item.setTextColor(selected ? color(R.color.app_primary) : color(R.color.app_on_surface_variant));
        }
    }

    private void load() {

    private void load() {
        if (swipe == null) return;
        swipe.setRefreshing(true);
        String token = SecureTokenStore.read(this);
        network.execute(() -> {
            try {
                List<AdminRepository.Item> items = AdminRepository.list(token);
                runOnUiThread(() -> {
                    allItems.clear();
                    allItems.addAll(items);
                    applyFilter();
                    swipe.setRefreshing(false);
                });
            } catch (SecurityException error) {
                runOnUiThread(() -> {
                    swipe.setRefreshing(false);
                    SecureTokenStore.clear(this);
                    showPairing();
                    toast(error.getMessage());
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    swipe.setRefreshing(false);
                    toast(message(error));
                });
            }
        });
    }

    private void applyFilter() {
        if (adapter == null || count == null) return;
        List<AdminRepository.Item> shown = new ArrayList<>();
        for (AdminRepository.Item item : allItems) {
            if ("unread".equals(filter)) {
                if (item.unreadCount > 0) shown.add(item);
            } else if (filter.equals("all") || filter.equals(item.type)) {
                shown.add(item);
            }
        }
        adapter.setItems(shown);
        count.setText(shown.size() + (shown.size() == 1 ? " thread" : " threads"));
        styleFilterButtons();
    }

    private void showDetail(AdminRepository.Item item) {
        startActivity(new Intent(this, FeedbackThreadActivity.class)
                .putExtra(FeedbackThreadActivity.EXTRA_FEEDBACK_ID, item.id));
    }

    private LinearLayout screenShell() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(color(R.color.app_background));
        return layout;
    }

    private LinearLayout vertical(int paddingDp) {
        LinearLayout layout = column(paddingDp);
        layout.setBackgroundColor(color(R.color.app_background));
        return layout;
    }

    private LinearLayout column(int paddingDp) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(paddingDp), dp(paddingDp), dp(paddingDp), dp(paddingDp));
        return layout;
    }

    private MaterialCardView panel(String title, String subtitle, View body) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(color(R.color.app_surface));
        card.setStrokeColor(color(R.color.app_divider));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(14));
        card.setCardElevation(0);

        LinearLayout wrapper = column(12);
        TextView heading = label(title);
        wrapper.addView(heading);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView detail = text(subtitle, 11, color(R.color.app_on_surface_variant));
            detail.setPadding(0, dp(1), 0, dp(7));
            wrapper.addView(detail);
        } else {
            heading.setPadding(0, 0, 0, dp(7));
        }
        wrapper.addView(body);
        card.addView(wrapper);
        LinearLayout.LayoutParams params = matchWrap(dp(5), dp(5));
        card.setLayoutParams(params);
        return card;
    }

    private LinearLayout metricBlock(TextView value) {
        LinearLayout block = column(1);
        block.setGravity(Gravity.CENTER);
        block.addView(value);
        return block;
    }

    private TextView metricNumber(String value, String caption) {
        TextView result = text(value + "\n" + caption, 13, color(R.color.app_on_surface_variant));
        android.text.SpannableString text = new android.text.SpannableString(value + "\n" + caption);
        text.setSpan(new android.text.style.RelativeSizeSpan(1.55f), 0, value.length(), 0);
        text.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), 0, value.length(), 0);
        result.setText(text);
        result.setGravity(Gravity.CENTER);
        return result;
    }

    private TextView label(String value) {
        TextView result = text(value, 10, color(R.color.app_on_surface_variant));
        result.setTypeface(null, Typeface.BOLD);
        result.setLetterSpacing(0.07f);
        return result;
    }

    private MaterialButton button(String value) {
        MaterialButton button = new MaterialButton(this);
        button.setText(value);
        button.setTextColor(color(R.color.app_on_surface));
        button.setAllCaps(false);
        button.setCornerRadius(dp(10));
        button.setMinHeight(dp(38));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setBackgroundTintList(ColorStateList.valueOf(color(R.color.app_surface_raised)));
        button.setStrokeColor(ColorStateList.valueOf(color(R.color.app_divider)));
        button.setStrokeWidth(dp(1));
        return button;
    }

    private MaterialButton primaryButton(String value) {
        MaterialButton button = button(value);
        button.setTextColor(color(R.color.app_on_primary));
        button.setBackgroundTintList(ColorStateList.valueOf(color(R.color.app_primary)));
        button.setStrokeWidth(0);
        return button;
    }

    private MaterialButton compactButton(String value) {
        MaterialButton button = button(value);
        button.setMinHeight(dp(36));
        return button;
    }

    private TextView text(String value, int size, int color) {

    private TextView text(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        return text;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(2), 0, dp(2), 0);
        return params;
    }

    private LinearLayout.LayoutParams matchWrap(int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, top, 0, bottom);
        return params;
    }

    private int color(int id) { return getColor(id); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String value) {
        Toast.makeText(this, value == null ? "Something went wrong" : value, Toast.LENGTH_LONG).show();
    }

    private static String displayType(String type) {
        if ("bug_report".equals(type)) return "Bug report";
        if ("feature_request".equals(type)) return "Feature request";
        return "General feedback";
    }

    private static String stars(int rating) {
        if (rating < 1) return "";
        StringBuilder value = new StringBuilder("  ");
        for (int i = 0; i < rating; i++) value.append('★');
        return value.toString();
    }

    private static String formatDate(String raw) {
        try {
            SimpleDateFormat source = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            source.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date parsed = source.parse(raw);
            return new SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(parsed);
        } catch (ParseException ignored) {
            return raw;
        }
    }

    private final class FeedbackAdapter extends RecyclerView.Adapter<FeedbackAdapter.Holder> {
        private final List<AdminRepository.Item> items = new ArrayList<>();
        private final ItemClick click;

        FeedbackAdapter(ItemClick click) { this.click = click; }

        void setItems(List<AdminRepository.Item> replacement) {
            items.clear();
            items.addAll(replacement);
            notifyDataSetChanged();
        }

        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            MaterialCardView card = new MaterialCardView(MainActivity.this);
            card.setCardBackgroundColor(color(R.color.app_surface));
            card.setStrokeColor(color(R.color.app_divider));
            card.setStrokeWidth(dp(1));
            card.setRadius(dp(12));
            card.setCardElevation(0);

            LinearLayout body = column(0);
            body.setPadding(dp(12), dp(9), dp(12), dp(9));
            TextView title = MainActivity.this.text("", 12, color(R.color.app_on_surface));
            title.setTypeface(null, Typeface.BOLD);
            TextView preview = MainActivity.this.text("", 14, color(R.color.app_on_surface));
            preview.setMaxLines(2);
            preview.setEllipsize(TextUtils.TruncateAt.END);
            preview.setPadding(0, dp(2), 0, dp(3));
            TextView meta = MainActivity.this.text("", 10, color(R.color.app_on_surface_variant));
            meta.setSingleLine(true);
            meta.setEllipsize(TextUtils.TruncateAt.END);
            body.addView(title);
            body.addView(preview);
            body.addView(meta);
            card.addView(body);

            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, -2);
            params.setMargins(0, 0, 0, dp(7));
            card.setLayoutParams(params);
            return new Holder(card, title, preview, meta);
        }

        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            AdminRepository.Item item = items.get(position);
            String preview = item.lastMessage == null ? "" : item.lastMessage.trim();
            String unread = item.unreadCount > 0 ? "  ·  " + item.unreadCount + " new" : "";
            holder.title.setText(displayType(item.type) + stars(item.rating) + unread);
            holder.title.setTextColor(item.unreadCount > 0
                    ? color(R.color.app_primary) : color(R.color.app_on_surface));

            String sender = "developer".equals(item.lastSender) ? "You" : "User";
            holder.preview.setText(sender + ": " + preview);
            holder.meta.setText(item.status.replace('_', ' ').toUpperCase(Locale.US) +
                    "  ·  " + formatDate(item.lastMessageAt));
            holder.itemView.setOnClickListener(v -> click.open(item));
        }

        @Override public int getItemCount() { return items.size(); }

        final class Holder extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView preview;
            final TextView meta;

            Holder(View view, TextView title, TextView preview, TextView meta) {
                super(view);
                this.title = title;
                this.preview = preview;
                this.meta = meta;
            }
        }
    }

    private interface ItemClick { void open(AdminRepository.Item item); }
}
