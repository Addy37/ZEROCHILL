package com.addy37.crazyshitadmin;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Aggregate-only product analytics. No per-install or per-account activity history is exposed here. */
public final class AnalyticsActivity extends AppCompatActivity {
    private static final int COLLAPSED_ROWS = 5;

    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final Set<String> expandedCards = new HashSet<>();
    private LinearLayout content;
    private TextView status;
    private ProgressBar progress;
    private MaterialButton refresh;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        load();
    }

    private void buildUi() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(color(R.color.app_background));

        LinearLayout page = vertical(0);
        page.setPadding(dp(14), dp(12), dp(14), 0);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = vertical(0);
        TextView eyebrow = label("AUDIENCE INSIGHTS");
        TextView title = text("Analytics", 26, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        TextView detail = text("Aggregate usage, social activity, devices, and content interest",
                12, color(R.color.app_on_surface_variant));
        titles.addView(eyebrow);
        titles.addView(title);
        titles.addView(detail);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        refresh = compactButton("Refresh");
        refresh.setOnClickListener(v -> load());
        header.addView(refresh);
        page.addView(header);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(0, dp(5), 0, dp(3));
        status = text("Loading live analytics…", 11, color(R.color.app_on_surface_variant));
        statusRow.addView(status, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        progress = new ProgressBar(this);
        statusRow.addView(progress, new LinearLayout.LayoutParams(dp(22), dp(22)));
        page.addView(statusRow);

        content = vertical(0);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        shell.addView(page, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        shell.addView(bottomNav());
        setContentView(shell);
    }

    private void load() {
        String token = SecureTokenStore.read(this);
        if (token.isEmpty()) {
            toast("Admin token required.");
            finish();
            return;
        }
        progress.setVisibility(View.VISIBLE);
        refresh.setEnabled(false);
        status.setText("Loading live analytics…");
        network.execute(() -> {
            try {
                AdminRepository.AnalyticsDashboard dashboard = AdminRepository.analytics(token);
                runOnUiThread(() -> render(dashboard));
            } catch (SecurityException error) {
                runOnUiThread(() -> {
                    SecureTokenStore.clear(this);
                    toast(error.getMessage());
                    finish();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    refresh.setEnabled(true);
                    status.setText("Could not load analytics: " + message(error));
                });
            }
        });
    }

    private void render(AdminRepository.AnalyticsDashboard dashboard) {
        progress.setVisibility(View.GONE);
        refresh.setEnabled(true);
        status.setText("Updated now");
        content.removeAllViews();

        LinearLayout users = new LinearLayout(this);
        users.setOrientation(LinearLayout.HORIZONTAL);
        users.addView(metric("Today", dashboard.dailyUsers), weighted());
        users.addView(metric("Week", dashboard.weeklyUsers), weighted());
        users.addView(metric("Month", dashboard.monthlyUsers), weighted());
        content.addView(card("ACTIVE USERS", "Anonymous active installs", users));

        addReleaseCard(dashboard.releaseAdoption);
        addSocialCards(dashboard.social);

        addRankingCard("creators", "TRENDING CREATORS", "Who users are opening most",
                dashboard.creators, true,
                "Creator interest will appear after users open OnlyFap creator galleries.");
        addRankingCard("sections", "MOST USED SECTIONS", "Where users spend their time",
                dashboard.sections, false,
                "Section usage will appear after analytics-enabled app sessions begin.");
        addRankingCard("sources", "SOURCE INTEREST", "Which content sources attract attention",
                dashboard.sources, false,
                "Source usage will appear after users open source-specific content.");
        addRankingCard("versions", "APP VERSIONS", "How quickly users adopt releases",
                dashboard.versions, false,
                "Version adoption will appear after analytics-enabled users open the app.");
        addDeviceCard("devices", "TOP DEVICES", "Phone models active this week",
                withoutEmulators(dashboard.deviceModels),
                "Device models will appear as users open the updated ZEROCHILL app.", true);
        addDeviceCard("brands", "DEVICE BRANDS", "Active users by manufacturer",
                dashboard.deviceManufacturers,
                "Device brands will appear as users open the updated ZEROCHILL app.", false);
        addDeviceCard("android", "ANDROID VERSIONS", "Android versions active this week",
                dashboard.androidVersions,
                "Android versions will appear as users open the updated ZEROCHILL app.", false);

        if (dashboard.dailyUsers == 0 && dashboard.weeklyUsers == 0 && dashboard.monthlyUsers == 0) {
            TextView waiting = text(
                    "No analytics-enabled users have reported yet. Data begins filling in as users open the latest app.",
                    14, color(R.color.app_on_surface_variant));
            waiting.setPadding(dp(4), dp(12), dp(4), dp(20));
            content.addView(waiting);
        }
    }

    private void addReleaseCard(AdminRepository.ReleaseAdoption release) {
        if (release == null || release.version == null || release.version.trim().isEmpty()) return;
        LinearLayout body = vertical(0);

        TextView version = text("ZeroChill " + release.version, 18, Color.WHITE);
        version.setTypeface(null, Typeface.BOLD);
        body.addView(version);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, 0);
        row.addView(metricWithPercent("Today", release.usersToday, release.percentToday), weighted());
        row.addView(metricWithPercent("Week", release.usersWeek, release.percentWeek), weighted());
        row.addView(metricWithPercent("Month", release.usersMonth, release.percentMonth), weighted());
        body.addView(row);

        content.addView(card("LATEST RELEASE", "Adoption among active installs", body));
    }

    private void addSocialCards(AdminRepository.SocialSummary social) {
        if (social == null) return;

        LinearLayout snapshot = new LinearLayout(this);
        snapshot.setOrientation(LinearLayout.HORIZONTAL);
        snapshot.addView(metric("Accounts", social.accountsTotal), weighted());
        snapshot.addView(metric("Social", social.socialUsersTotal), weighted());
        snapshot.addView(metric("Messages", social.messagesTotal), weighted());
        snapshot.addView(metric("Comments", social.commentsTotal), weighted());
        content.addView(card("4.3 SOCIAL", "ZEROCHILL ID and social adoption", snapshot));

        LinearLayout accounts = vertical(0);
        accounts.addView(detailRow("New accounts today", social.accountsToday, "accounts"));
        accounts.addView(detailRow("New accounts this week", social.accountsWeek, "accounts"));
        accounts.addView(detailRow("Profiles with avatar", social.profilesWithAvatar, "accounts"));
        accounts.addView(detailRow("Profiles with bio", social.profilesWithBio, "accounts"));
        accounts.addView(detailRow("Accounts with creator favorites", social.creatorFavoriteAccounts, "accounts"));
        content.addView(card("ACCOUNT ADOPTION", "How users are setting up ZEROCHILL ID", accounts));

        LinearLayout activity = vertical(0);
        activity.addView(detailRow("Comments", social.commentsTotal,
                String.format(Locale.US, "%,d this week", social.commentsWeek)));
        activity.addView(detailRow("Replies", social.repliesTotal,
                String.format(Locale.US, "%,d this week", social.repliesWeek)));
        activity.addView(detailRow("Video likes", social.videoLikesTotal,
                String.format(Locale.US, "%,d this week", social.videoLikesWeek)));
        activity.addView(detailRow("Comment likes", social.commentLikesTotal,
                String.format(Locale.US, "%,d this week", social.commentLikesWeek)));
        activity.addView(detailRow("Direct messages", social.messagesTotal,
                String.format(Locale.US, "%,d today · %,d this week",
                        social.messagesToday, social.messagesWeek)));
        content.addView(card("SOCIAL ACTIVITY", "Current engagement across 4.3 social features", activity));

        LinearLayout messaging = vertical(0);
        messaging.addView(detailRow("Active conversations this week", social.conversationsWeek, "conversations"));
        messaging.addView(detailRow("People messaging this week", social.messageSendersWeek, "senders"));
        messaging.addView(detailRow("Read messages", social.messagesRead, formatPercent(social.messageReadRate) + " read rate"));
        messaging.addView(detailRow("Unread messages", social.messagesUnread, "messages"));
        messaging.addView(detailRow("Social users this week", social.socialUsersWeek,
                String.format(Locale.US, "%,d today", social.socialUsersToday)));
        content.addView(card("MESSAGING HEALTH", "Aggregate messaging and social reach", messaging));

        LinearLayout preferences = vertical(0);
        preferences.addView(detailRow("Creator favorites", social.creatorFavoritesTotal,
                social.creatorFavoriteAccounts == 0
                        ? "No favoriting accounts yet"
                        : String.format(Locale.US, "%.1f average per favoriting account",
                                social.averageFavoritesPerAccount)));
        preferences.addView(detailRow("User blocks", social.blocksTotal, "blocks"));
        preferences.addView(detailRow("Notification settings customized",
                social.notificationPreferenceUsers, "accounts"));
        preferences.addView(detailRow("DM alerts disabled", social.directMessagesDisabled, "accounts"));
        preferences.addView(detailRow("Reply / like alerts disabled",
                social.repliesDisabled + social.likesDisabled,
                String.format(Locale.US, "%,d replies · %,d likes",
                        social.repliesDisabled, social.likesDisabled)));
        content.addView(card("PREFERENCES & SAFETY", "Aggregate controls only, never individual activity", preferences));
    }

    private void addRankingCard(
            String key,
            String title,
            String subtitle,
            List<AdminRepository.AnalyticsRow> rows,
            boolean creator,
            String emptyText
    ) {
        LinearLayout body = vertical(0);
        if (rows.isEmpty()) {
            body.addView(text(emptyText, 13, color(R.color.app_on_surface_variant)));
        } else {
            long max = Math.max(1L, rows.get(0).uniqueUsers);
            ArrayList<View> overflow = new ArrayList<>();
            for (int index = 0; index < rows.size(); index++) {
                AdminRepository.AnalyticsRow row = rows.get(index);
                LinearLayout block = vertical(0);
                block.setPadding(0, dp(4), 0, dp(4));

                LinearLayout line = new LinearLayout(this);
                line.setGravity(Gravity.CENTER_VERTICAL);
                String nameValue = creator ? (index + 1) + ". " + row.value : friendly(row.value);
                TextView name = text(nameValue, 14, color(R.color.app_on_surface));
                if (index < 3) name.setTypeface(null, Typeface.BOLD);
                line.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

                TextView counts = text(String.format(Locale.US, "%,d users · %,d opens",
                        row.uniqueUsers, row.eventCount), 10, color(R.color.app_on_surface_variant));
                counts.setGravity(Gravity.END);
                line.addView(counts);
                block.addView(line);

                ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
                bar.setMax(1000);
                bar.setProgress((int) Math.min(1000L, (row.uniqueUsers * 1000L) / max));
                bar.setProgressTintList(ColorStateList.valueOf(color(R.color.app_primary)));
                bar.setProgressBackgroundTintList(ColorStateList.valueOf(color(R.color.app_surface_variant)));
                LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
                barParams.setMargins(0, dp(3), 0, 0);
                block.addView(bar, barParams);

                if (index >= COLLAPSED_ROWS) {
                    block.setVisibility(expandedCards.contains(key) ? View.VISIBLE : View.GONE);
                    overflow.add(block);
                }
                body.addView(block);
            }
            addExpandControl(body, key, rows.size(), overflow);
        }
        content.addView(card(title, subtitle, body));
    }

    private List<AdminRepository.AnalyticsRow> withoutEmulators(
            List<AdminRepository.AnalyticsRow> rows) {
        List<AdminRepository.AnalyticsRow> filtered = new ArrayList<>();
        for (AdminRepository.AnalyticsRow row : rows) {
            if (!isEmulatorDevice(row.value) && !isUnknownDevice(row.value)) filtered.add(row);
        }
        return filtered;
    }

    private boolean isEmulatorDevice(String value) {
        if (value == null || value.isEmpty()) return false;
        String normalized = value.toLowerCase(Locale.US);
        return normalized.contains("android sdk built for x86")
                || normalized.contains("sdk_gphone")
                || normalized.contains("generic_x86")
                || normalized.contains("aosp_x86")
                || normalized.contains("emulator")
                || normalized.contains("goldfish")
                || normalized.contains("ranchu");
    }

    private boolean isUnknownDevice(String value) {
        return value == null || value.trim().isEmpty() || "unknown".equalsIgnoreCase(value.trim());
    }

    private String friendlyDeviceName(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "Unknown";
        String normalized = raw.trim().toLowerCase(Locale.US);

        if (normalized.contains("cph2655")) return "OnePlus 13";

        if (normalized.contains("sm-s948")) return "Samsung Galaxy S26 Ultra";
        if (normalized.contains("sm-s947")) return "Samsung Galaxy S26+";
        if (normalized.contains("sm-s942")) return "Samsung Galaxy S26";
        if (normalized.contains("sm-s938")) return "Samsung Galaxy S25 Ultra";
        if (normalized.contains("sm-s937")) return "Samsung Galaxy S25 Edge";
        if (normalized.contains("sm-s936")) return "Samsung Galaxy S25+";
        if (normalized.contains("sm-s931")) return "Samsung Galaxy S25";
        if (normalized.contains("sm-s928")) return "Samsung Galaxy S24 Ultra";
        if (normalized.contains("sm-s926")) return "Samsung Galaxy S24+";
        if (normalized.contains("sm-s921")) return "Samsung Galaxy S24";
        if (normalized.contains("sm-s918")) return "Samsung Galaxy S23 Ultra";
        if (normalized.contains("sm-s916")) return "Samsung Galaxy S23+";
        if (normalized.contains("sm-s911")) return "Samsung Galaxy S23";

        if (normalized.contains("sm-f966")) return "Samsung Galaxy Z Fold7";
        if (normalized.contains("sm-f766")) return "Samsung Galaxy Z Flip7";
        if (normalized.contains("sm-f956")) return "Samsung Galaxy Z Fold6";
        if (normalized.contains("sm-f741")) return "Samsung Galaxy Z Flip6";

        if (normalized.contains("moto g power") && normalized.contains("2025")) {
            return "Motorola Moto G Power (2025)";
        }

        return raw.trim();
    }

    private void addDeviceCard(String key, String title, String subtitle,
            List<AdminRepository.AnalyticsRow> rows, String emptyText, boolean friendlyModels) {
        LinearLayout body = vertical(0);
        if (rows.isEmpty()) {
            body.addView(text(emptyText, 13, color(R.color.app_on_surface_variant)));
        } else {
            long max = Math.max(1L, rows.get(0).uniqueUsers);
            ArrayList<View> overflow = new ArrayList<>();
            for (int index = 0; index < rows.size(); index++) {
                AdminRepository.AnalyticsRow row = rows.get(index);
                LinearLayout block = vertical(0);
                block.setPadding(0, dp(4), 0, dp(4));
                TextView name = text(friendlyModels ? friendlyDeviceName(row.value) : row.value,
                        14, color(R.color.app_on_surface));
                name.setTypeface(null, Typeface.BOLD);
                block.addView(name);
                block.addView(text(String.format(Locale.US,
                        "%,d today · %,d week · %,d events",
                        row.usersToday, row.uniqueUsers, row.eventCount),
                        10, color(R.color.app_on_surface_variant)));
                ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
                bar.setMax(1000);
                bar.setProgress((int) Math.min(1000L, (row.uniqueUsers * 1000L) / max));
                bar.setProgressTintList(ColorStateList.valueOf(color(R.color.app_primary)));
                bar.setProgressBackgroundTintList(ColorStateList.valueOf(color(R.color.app_surface_variant)));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
                params.setMargins(0, dp(3), 0, 0);
                block.addView(bar, params);

                if (index >= COLLAPSED_ROWS) {
                    block.setVisibility(expandedCards.contains(key) ? View.VISIBLE : View.GONE);
                    overflow.add(block);
                }
                body.addView(block);
            }
            addExpandControl(body, key, rows.size(), overflow);
        }
        content.addView(card(title, subtitle, body));
    }

    private void addExpandControl(LinearLayout body, String key, int count, List<View> overflow) {
        if (count <= COLLAPSED_ROWS || overflow.isEmpty()) return;
        MaterialButton toggle = compactButton(expandedCards.contains(key)
                ? "Show less ↑" : "View all " + count + " ↓");
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
        params.topMargin = dp(7);
        body.addView(toggle, params);
        toggle.setOnClickListener(v -> {
            boolean expand = !expandedCards.contains(key);
            if (expand) expandedCards.add(key);
            else expandedCards.remove(key);
            for (View row : overflow) row.setVisibility(expand ? View.VISIBLE : View.GONE);
            toggle.setText(expand ? "Show less ↑" : "View all " + count + " ↓");
        });
    }

    private LinearLayout detailRow(String label, long value, String suffix) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(5), 0, dp(5));

        TextView name = text(label, 13, color(R.color.app_on_surface));
        row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout values = vertical(0);
        values.setGravity(Gravity.END);
        TextView number = text(String.format(Locale.US, "%,d", value), 14, Color.WHITE);
        number.setTypeface(null, Typeface.BOLD);
        number.setGravity(Gravity.END);
        values.addView(number);
        TextView detail = text(suffix, 9, color(R.color.app_on_surface_variant));
        detail.setGravity(Gravity.END);
        values.addView(detail);
        row.addView(values);
        return row;
    }

    private LinearLayout metric(String label, long value) {
        LinearLayout block = vertical(2);
        block.setGravity(Gravity.CENTER);
        TextView number = text(String.format(Locale.US, "%,d", value), 22, Color.WHITE);
        number.setTypeface(null, Typeface.BOLD);
        number.setGravity(Gravity.CENTER);
        TextView caption = text(label, 10, color(R.color.app_on_surface_variant));
        caption.setGravity(Gravity.CENTER);
        block.addView(number);
        block.addView(caption);
        return block;
    }

    private LinearLayout metricWithPercent(String label, long value, double percent) {
        LinearLayout block = metric(label, value);
        TextView rate = text(formatPercent(percent), 9, color(R.color.app_primary));
        rate.setGravity(Gravity.CENTER);
        block.addView(rate);
        return block;
    }

    private String formatPercent(double value) {
        if (!Double.isFinite(value)) value = 0d;
        return String.format(Locale.US, "%.0f%%", Math.max(0d, Math.min(100d, value)));
    }

    private MaterialCardView card(String title, String subtitle, LinearLayout body) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(color(R.color.app_surface));
        card.setStrokeColor(color(R.color.app_divider));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(12));
        card.setCardElevation(0);

        LinearLayout wrapper = vertical(12);
        TextView heading = label(title);
        wrapper.addView(heading);
        TextView detail = text(subtitle, 11, color(R.color.app_on_surface_variant));
        detail.setPadding(0, dp(1), 0, dp(6));
        wrapper.addView(detail);
        wrapper.addView(body);
        card.addView(wrapper);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(4), 0, dp(4));
        card.setLayoutParams(params);
        return card;
    }

    private LinearLayout bottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(4), dp(4), dp(4), dp(5));
        nav.setBackgroundColor(color(R.color.app_surface));
        nav.addView(navButton("Dashboard", "dashboard", false), weighted());
        nav.addView(navButton("Analytics", "analytics", true), weighted());
        nav.addView(navButton("Sources", "sources", false), weighted());
        nav.addView(navButton("Feedback", "feedback", false), weighted());
        return nav;
    }

    private MaterialButton navButton(String title, String destination, boolean active) {
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
        if (!active) button.setOnClickListener(v -> navigate(destination));
        return button;
    }

    private void navigate(String destination) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_DESTINATION, destination);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(2), 0, dp(2), 0);
        return params;
    }

    private LinearLayout vertical(int paddingDp) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(paddingDp), dp(paddingDp), dp(paddingDp), dp(paddingDp));
        return layout;
    }

    private TextView label(String value) {
        TextView result = text(value, 10, color(R.color.app_on_surface_variant));
        result.setTypeface(null, Typeface.BOLD);
        result.setLetterSpacing(0.07f);
        return result;
    }

    private MaterialButton compactButton(String value) {
        MaterialButton button = new MaterialButton(this);
        button.setText(value);
        button.setTextColor(color(R.color.app_on_surface));
        button.setAllCaps(false);
        button.setCornerRadius(dp(10));
        button.setMinHeight(dp(36));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setBackgroundTintList(ColorStateList.valueOf(color(R.color.app_surface_raised)));
        button.setStrokeColor(ColorStateList.valueOf(color(R.color.app_divider)));
        button.setStrokeWidth(dp(1));
        return button;
    }

    private TextView text(String value, int size, int color) {
        TextView result = new TextView(this);
        result.setText(value);
        result.setTextSize(size);
        result.setTextColor(color);
        return result;
    }

    private String friendly(String raw) {
        if (raw == null || raw.isEmpty()) return "Unknown";
        switch (raw.toLowerCase(Locale.US)) {
            case "home": return "Home";
            case "collections": return "Shows";
            case "library": return "Library";
            case "chaos": return "ShitTok";
            case "categories": return "Categories";
            case "search": return "Search";
            case "favorites": return "Favorites";
            case "downloads": return "Downloads";
            case "settings": return "Settings";
            case "profile": return "Profile";
            case "creator_gallery": return "Creator galleries";
            case "crazyshit": return "CrazyShit source";
            case "kaotic": return "Kaotic";
            case "efukt": return "EFukt";
            case "fapzone": return "OnlyFap";
            case "fapello": return "Fapello";
            case "onlyhaven": return "OnlyHaven";
            case "bunkr": return "Bunkr";
            case "wikifeet": return "WikiFeet";
            case "wikifeetx": return "WikiFeet X";
            default: return raw;
        }
    }

    private String message(Exception error) {
        return error.getMessage() == null ? "Request failed" : error.getMessage();
    }

    private int color(int id) { return getColor(id); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }

    @Override protected void onDestroy() {
        network.shutdownNow();
        super.onDestroy();
    }
}
