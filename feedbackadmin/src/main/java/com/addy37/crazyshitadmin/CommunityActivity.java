package com.addy37.crazyshitadmin;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
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

/** Private read-only account, social activity, and reports console. */
public final class CommunityActivity extends AppCompatActivity {
    private static final String ACCOUNTS = "accounts";
    private static final String SOCIAL = "social";
    private static final String REPORTS = "reports";

    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final List<MaterialButton> sectionButtons = new ArrayList<>();
    private final List<MaterialButton> filterButtons = new ArrayList<>();

    private SwipeRefreshLayout swipe;
    private LinearLayout rows;
    private LinearLayout filters;
    private EditText search;
    private TextView status;
    private TextView accountsMetric;
    private TextView newMetric;
    private TextView activityMetric;
    private TextView reportsMetric;
    private String section = ACCOUNTS;
    private String filter = "all";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (SecureTokenStore.read(this).isEmpty()) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        buildScreen();
        refresh();
    }

    @Override protected void onDestroy() {
        network.shutdownNow();
        super.onDestroy();
    }

    private void buildScreen() {
        LinearLayout shell = column(0);
        shell.setBackgroundColor(color(R.color.app_background));

        LinearLayout content = column(0);
        content.setPadding(dp(14), dp(12), dp(14), 0);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = column(0);
        titles.addView(label("COMMUNITY ADMIN"));
        TextView title = text("Accounts & Social", 26, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        titles.addView(title);
        titles.addView(text("Read-only account and community visibility", 12,
                color(R.color.app_on_surface_variant)));
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        MaterialButton lock = compactButton("Lock");
        header.addView(lock);
        content.addView(header);

        LinearLayout metricRow = new LinearLayout(this);
        metricRow.setOrientation(LinearLayout.HORIZONTAL);
        accountsMetric = metric("–", "Accounts");
        newMetric = metric("–", "New 7d");
        activityMetric = metric("–", "Activity");
        reportsMetric = metric("–", "Reports");
        metricRow.addView(metricBlock(accountsMetric), weighted());
        metricRow.addView(metricBlock(newMetric), weighted());
        metricRow.addView(metricBlock(activityMetric), weighted());
        metricRow.addView(metricBlock(reportsMetric), weighted());
        content.addView(panel(metricRow));

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(0, dp(5), 0, dp(4));
        addSection(tabs, "Accounts", ACCOUNTS);
        addSection(tabs, "Social", SOCIAL);
        addSection(tabs, "Reports", REPORTS);
        content.addView(tabs);

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search username, email, or account ID");
        search.setTextColor(color(R.color.app_on_surface));
        search.setHintTextColor(color(R.color.app_on_surface_variant));
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchRow.addView(search, new LinearLayout.LayoutParams(0, dp(48), 1));
        MaterialButton searchButton = primaryButton("Search");
        searchRow.addView(searchButton);
        content.addView(searchRow);

        filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        filters.setPadding(0, dp(3), 0, dp(4));
        content.addView(filters);

        status = text("Loading community data…", 11, color(R.color.app_on_surface_variant));
        status.setPadding(0, dp(2), 0, dp(6));
        content.addView(status);

        rows = column(0);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.addView(rows, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        swipe = new SwipeRefreshLayout(this);
        swipe.setColorSchemeColors(color(R.color.app_primary));
        swipe.addView(scroll);
        swipe.setOnRefreshListener(this::refresh);
        content.addView(swipe, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        shell.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        shell.addView(bottomNav());
        setContentView(shell);

        lock.setOnClickListener(v -> confirmLock());
        searchButton.setOnClickListener(v -> refresh());
        search.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                refresh();
                return true;
            }
            return false;
        });
        rebuildFilters();
        styleSections();
    }

    private void addSection(LinearLayout row, String title, String value) {
        MaterialButton button = compactButton(title);
        button.setTag(value);
        button.setOnClickListener(v -> {
            if (value.equals(section)) return;
            section = value;
            filter = "all";
            search.setText("");
            if (ACCOUNTS.equals(section)) search.setHint("Search username, email, or account ID");
            else if (SOCIAL.equals(section)) search.setHint("Search people, comments, or content");
            else search.setHint("Search reporter, reported user, or reason");
            rebuildFilters();
            styleSections();
            refresh();
        });
        sectionButtons.add(button);
        row.addView(button, weighted());
    }

    private void rebuildFilters() {
        filters.removeAllViews();
        filterButtons.clear();
        if (ACCOUNTS.equals(section)) {
            addFilter("All", "all");
            addFilter("Active", "active");
            addFilter("Unconfirmed", "unconfirmed");
            addFilter("Banned", "banned");
        } else if (SOCIAL.equals(section)) {
            addFilter("All", "all");
            addFilter("Comments", "comments");
            addFilter("Likes", "likes");
            addFilter("Messages", "messages");
        } else {
            addFilter("All", "all");
            addFilter("Spam", "spam");
            addFilter("Harassment", "harassment");
            addFilter("Other", "other");
        }
        styleFilters();
    }

    private void addFilter(String title, String value) {
        MaterialButton button = compactButton(title);
        button.setTag(value);
        button.setTextSize(9);
        button.setMinHeight(dp(32));
        button.setOnClickListener(v -> {
            filter = value;
            styleFilters();
            refresh();
        });
        filterButtons.add(button);
        filters.addView(button, weighted());
    }

    private void styleSections() {
        for (MaterialButton button : sectionButtons) {
            boolean active = section.equals(String.valueOf(button.getTag()));
            styleSelectable(button, active);
        }
    }

    private void styleFilters() {
        for (MaterialButton button : filterButtons) {
            boolean active = filter.equals(String.valueOf(button.getTag()));
            styleSelectable(button, active);
        }
    }

    private void styleSelectable(MaterialButton button, boolean active) {
        button.setBackgroundTintList(ColorStateList.valueOf(active
                ? color(R.color.app_surface_raised) : color(R.color.app_surface)));
        button.setStrokeColor(ColorStateList.valueOf(active
                ? color(R.color.app_primary) : color(R.color.app_divider)));
        button.setStrokeWidth(dp(1));
        button.setTextColor(active ? color(R.color.app_primary) : color(R.color.app_on_surface_variant));
    }

    private void refresh() {
        if (swipe == null) return;
        swipe.setRefreshing(true);
        status.setText("Refreshing " + section + "…");
        String token = SecureTokenStore.read(this);
        String query = search == null ? "" : search.getText().toString().trim();
        String requestedSection = section;
        String requestedFilter = filter;
        network.execute(() -> {
            try {
                CommunityRepository.Overview overview = CommunityRepository.overview(token);
                if (ACCOUNTS.equals(requestedSection)) {
                    List<CommunityRepository.Account> items =
                            CommunityRepository.accounts(token, query, requestedFilter);
                    runOnUiThread(() -> {
                        if (!requestedSection.equals(section) || !requestedFilter.equals(filter)) return;
                        updateOverview(overview);
                        showAccounts(items);
                        finishRefresh(items.size(), query);
                    });
                } else if (SOCIAL.equals(requestedSection)) {
                    List<CommunityRepository.SocialActivity> items =
                            CommunityRepository.activity(token, query, requestedFilter);
                    runOnUiThread(() -> {
                        if (!requestedSection.equals(section) || !requestedFilter.equals(filter)) return;
                        updateOverview(overview);
                        showActivity(items);
                        finishRefresh(items.size(), query);
                    });
                } else {
                    List<CommunityRepository.Report> items =
                            CommunityRepository.reports(token, query, requestedFilter);
                    runOnUiThread(() -> {
                        if (!requestedSection.equals(section) || !requestedFilter.equals(filter)) return;
                        updateOverview(overview);
                        showReports(items);
                        finishRefresh(items.size(), query);
                    });
                }
            } catch (SecurityException error) {
                runOnUiThread(() -> {
                    swipe.setRefreshing(false);
                    SecureTokenStore.clear(this);
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    swipe.setRefreshing(false);
                    status.setText("Refresh failed: " + message(error));
                    toast(message(error));
                });
            }
        });
    }

    private void updateOverview(CommunityRepository.Overview overview) {
        accountsMetric.setText(metricText(overview.accounts, "Accounts"));
        newMetric.setText(metricText(overview.newAccounts7d, "New 7d"));
        long activity = overview.comments + overview.totalLikes() + overview.messages;
        activityMetric.setText(metricText(activity, "Activity"));
        reportsMetric.setText(metricText(overview.reports, "Reports"));
    }

    private void finishRefresh(int count, String query) {
        swipe.setRefreshing(false);
        String suffix = query.isEmpty() ? "" : " matching “" + query + "”";
        status.setText(String.format(Locale.US, "%,d result%s%s", count, count == 1 ? "" : "s", suffix));
        if (count == 0) {
            TextView empty = text("Nothing to show for this filter.", 14, color(R.color.app_on_surface_variant));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(50), 0, dp(50));
            rows.addView(empty);
        }
    }

    private void showAccounts(List<CommunityRepository.Account> items) {
        rows.removeAllViews();
        for (CommunityRepository.Account account : items) {
            MaterialCardView card = card();
            LinearLayout body = column(0);
            body.setPadding(dp(12), dp(10), dp(12), dp(10));

            TextView name = text(account.name(), 15, Color.WHITE);
            name.setTypeface(null, Typeface.BOLD);
            body.addView(name);

            String identity = account.handle();
            if (!account.email.isEmpty()) identity += (identity.isEmpty() ? "" : "  ·  ") + account.email;
            TextView sub = text(identity, 12, color(R.color.app_on_surface_variant));
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            body.addView(sub);

            TextView meta = text(account.status.toUpperCase(Locale.US) + "  ·  Joined " + formatDate(account.createdAt),
                    10, "active".equals(account.status) ? color(R.color.app_primary) : color(R.color.app_on_surface_variant));
            meta.setPadding(0, dp(5), 0, 0);
            body.addView(meta);
            card.addView(body);
            card.setOnClickListener(v -> loadAccountDetail(account));
            rows.addView(card);
        }
    }

    private void showActivity(List<CommunityRepository.SocialActivity> items) {
        rows.removeAllViews();
        for (CommunityRepository.SocialActivity item : items) {
            MaterialCardView card = card();
            LinearLayout body = column(0);
            body.setPadding(dp(12), dp(10), dp(12), dp(10));
            TextView title = text(activityTitle(item), 13, Color.WHITE);
            title.setTypeface(null, Typeface.BOLD);
            body.addView(title);
            if (!item.text.trim().isEmpty()) {
                TextView preview = text(item.text.trim(), 14, color(R.color.app_on_surface));
                preview.setMaxLines(3);
                preview.setEllipsize(TextUtils.TruncateAt.END);
                preview.setPadding(0, dp(3), 0, dp(3));
                body.addView(preview);
            }
            if (!item.secondary.trim().isEmpty()) {
                TextView secondary = text(item.secondary.trim(), 11, color(R.color.app_on_surface_variant));
                secondary.setSingleLine(true);
                secondary.setEllipsize(TextUtils.TruncateAt.END);
                body.addView(secondary);
            }
            body.addView(text(formatDate(item.createdAt), 10, color(R.color.app_on_surface_variant)));
            card.addView(body);
            rows.addView(card);
        }
    }

    private void showReports(List<CommunityRepository.Report> items) {
        rows.removeAllViews();
        for (CommunityRepository.Report item : items) {
            MaterialCardView card = card();
            LinearLayout body = column(0);
            body.setPadding(dp(12), dp(10), dp(12), dp(10));
            TextView title = text(cleanName(item.reporterName, item.reporterId) + " reported " +
                    cleanName(item.reportedName, item.reportedUserId), 13, Color.WHITE);
            title.setTypeface(null, Typeface.BOLD);
            body.addView(title);
            body.addView(text(item.reason.toUpperCase(Locale.US), 11, color(R.color.app_primary)));
            if (!item.details.trim().isEmpty()) {
                TextView details = text(item.details.trim(), 14, color(R.color.app_on_surface));
                details.setMaxLines(4);
                details.setEllipsize(TextUtils.TruncateAt.END);
                details.setPadding(0, dp(3), 0, dp(3));
                body.addView(details);
            }
            String meta = formatDate(item.createdAt);
            if (!item.directMessageId.isEmpty()) meta += "  ·  DM report";
            body.addView(text(meta, 10, color(R.color.app_on_surface_variant)));
            card.addView(body);
            rows.addView(card);
        }
    }

    private String activityTitle(CommunityRepository.SocialActivity item) {
        String actor = item.actor();
        if ("comment".equals(item.type)) return actor + " commented";
        if ("video_like".equals(item.type)) return actor + " liked a video";
        if ("comment_like".equals(item.type)) return actor + " liked a comment";
        if ("message".equals(item.type)) return actor + " sent a message";
        return actor + " · " + item.type.replace('_', ' ');
    }

    private void loadAccountDetail(CommunityRepository.Account account) {
        status.setText("Loading " + account.name() + "…");
        String token = SecureTokenStore.read(this);
        network.execute(() -> {
            try {
                CommunityRepository.AccountDetail detail = CommunityRepository.accountDetail(token, account.id);
                runOnUiThread(() -> {
                    status.setText("Account details loaded");
                    showAccountDetail(detail);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    status.setText("Account details failed: " + message(error));
                    toast(message(error));
                });
            }
        });
    }

    private void showAccountDetail(CommunityRepository.AccountDetail detail) {
        CommunityRepository.Account account = detail.account;
        LinearLayout body = column(12);
        body.setPadding(dp(18), dp(4), dp(18), dp(8));

        body.addView(sectionTitle("ACCOUNT"));
        body.addView(detailLine("Name", account.name()));
        if (!account.handle().isEmpty()) body.addView(detailLine("Username", account.handle()));
        body.addView(detailLine("Email", account.email));
        body.addView(detailLine("Status", account.status.toUpperCase(Locale.US)));
        body.addView(detailLine("User ID", account.id));
        body.addView(detailLine("Joined", formatDate(account.createdAt)));
        body.addView(detailLine("Last sign-in", formatDateOrNever(account.lastSignInAt)));
        body.addView(detailLine("Email confirmed", formatDateOrNever(account.emailConfirmedAt)));
        if (!account.bannedUntil.isEmpty()) body.addView(detailLine("Banned until", formatDate(account.bannedUntil)));
        if (!account.deletedAt.isEmpty()) body.addView(detailLine("Deleted", formatDate(account.deletedAt)));
        if (!account.bio.trim().isEmpty()) body.addView(detailLine("Bio", account.bio.trim()));
        body.addView(detailLine("Avatar", account.avatarPath.isEmpty() ? "Not set" : "Set"));

        body.addView(sectionTitle("SOCIAL TOTALS"));
        body.addView(detailLine("Comments", number(detail.comments)));
        body.addView(detailLine("Video likes", number(detail.videoLikes)));
        body.addView(detailLine("Comment likes", number(detail.commentLikes)));
        body.addView(detailLine("Messages", number(detail.messagesSent) + " sent · " + number(detail.messagesReceived) + " received"));
        body.addView(detailLine("Reports", number(detail.reportsFiled) + " filed · " + number(detail.reportsReceived) + " received"));
        body.addView(detailLine("Blocks", number(detail.blocksMade) + " made · " + number(detail.blocksReceived) + " received"));

        appendActivities(body, "RECENT COMMENTS", detail.recentComments);
        appendActivities(body, "RECENT LIKES", detail.recentLikes);
        appendActivities(body, "RECENT MESSAGE METADATA", detail.recentMessages);
        appendReports(body, "RECENT REPORTS", detail.recentReports);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        new AlertDialog.Builder(this)
                .setTitle(account.name())
                .setView(scroll)
                .setPositiveButton("Close", null)
                .show();
    }

    private void appendActivities(LinearLayout body, String title,
                                  List<CommunityRepository.SocialActivity> items) {
        if (items.isEmpty()) return;
        body.addView(sectionTitle(title));
        for (CommunityRepository.SocialActivity item : items) {
            String value = activityTitle(item);
            if (!item.text.trim().isEmpty()) value += "\n" + item.text.trim();
            if (!item.secondary.trim().isEmpty()) value += "\n" + item.secondary.trim();
            value += "\n" + formatDate(item.createdAt);
            TextView line = text(value, 12, color(R.color.app_on_surface));
            line.setPadding(0, 0, 0, dp(10));
            body.addView(line);
        }
    }

    private void appendReports(LinearLayout body, String title, List<CommunityRepository.Report> items) {
        if (items.isEmpty()) return;
        body.addView(sectionTitle(title));
        for (CommunityRepository.Report item : items) {
            String value = cleanName(item.reporterName, item.reporterId) + " → " +
                    cleanName(item.reportedName, item.reportedUserId) + " · " + item.reason;
            if (!item.details.trim().isEmpty()) value += "\n" + item.details.trim();
            value += "\n" + formatDate(item.createdAt);
            TextView line = text(value, 12, color(R.color.app_on_surface));
            line.setPadding(0, 0, 0, dp(10));
            body.addView(line);
        }
    }

    private TextView sectionTitle(String value) {
        TextView title = label(value);
        title.setTextColor(color(R.color.app_primary));
        title.setPadding(0, dp(8), 0, dp(5));
        return title;
    }

    private TextView detailLine(String label, String value) {
        String safe = value == null || value.trim().isEmpty() ? "None" : value.trim();
        TextView line = text(label + "\n" + safe, 12, color(R.color.app_on_surface));
        android.text.SpannableString styled = new android.text.SpannableString(label + "\n" + safe);
        styled.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), 0, label.length(), 0);
        styled.setSpan(new android.text.style.ForegroundColorSpan(color(R.color.app_on_surface_variant)),
                0, label.length(), 0);
        line.setText(styled);
        line.setTextIsSelectable(true);
        line.setPadding(0, 0, 0, dp(8));
        return line;
    }

    private LinearLayout bottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(4), dp(4), dp(4), dp(5));
        nav.setBackgroundColor(color(R.color.app_surface));
        nav.addView(navButton("Dashboard", false, v -> openMain("dashboard")), weighted());
        nav.addView(navButton("Analytics", false, v -> startActivity(new Intent(this, AnalyticsActivity.class))), weighted());
        nav.addView(navButton("Community", true, v -> {}), weighted());
        nav.addView(navButton("Sources", false, v -> openMain("sources")), weighted());
        nav.addView(navButton("Feedback", false, v -> openMain("feedback")), weighted());
        return nav;
    }

    private void openMain(String destination) {
        startActivity(new Intent(this, MainActivity.class)
                .putExtra(MainActivity.EXTRA_DESTINATION, destination)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }

    private MaterialButton navButton(String title, boolean active, View.OnClickListener click) {
        MaterialButton button = new MaterialButton(this);
        button.setText(title);
        button.setTextSize(9);
        button.setAllCaps(false);
        button.setSingleLine(true);
        button.setMaxLines(1);
        button.setMinWidth(0);
        button.setPadding(dp(1), 0, dp(1), 0);
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

    private void confirmLock() {
        new AlertDialog.Builder(this)
                .setTitle("Lock admin app?")
                .setMessage("You will need the admin token to connect again.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Lock", (dialog, which) -> {
                    SecureTokenStore.clear(this);
                    startActivity(new Intent(this, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
                    finish();
                })
                .show();
    }

    private MaterialCardView panel(View content) {
        MaterialCardView card = card();
        LinearLayout body = column(0);
        body.setPadding(dp(8), dp(9), dp(8), dp(9));
        body.addView(content);
        card.addView(body);
        return card;
    }

    private MaterialCardView card() {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(color(R.color.app_surface));
        card.setStrokeColor(color(R.color.app_divider));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(13));
        card.setCardElevation(0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(4), 0, dp(4));
        card.setLayoutParams(params);
        return card;
    }

    private LinearLayout metricBlock(TextView value) {
        LinearLayout block = column(0);
        block.setGravity(Gravity.CENTER);
        block.addView(value);
        return block;
    }

    private TextView metric(String value, String caption) {
        TextView result = text(value + "\n" + caption, 11, color(R.color.app_on_surface_variant));
        result.setGravity(Gravity.CENTER);
        return result;
    }

    private CharSequence metricText(long value, String caption) {
        String number = number(value);
        android.text.SpannableString text = new android.text.SpannableString(number + "\n" + caption);
        text.setSpan(new android.text.style.RelativeSizeSpan(1.45f), 0, number.length(), 0);
        text.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), 0, number.length(), 0);
        return text;
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
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        return text;
    }

    private LinearLayout column(int paddingDp) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(paddingDp), dp(paddingDp), dp(paddingDp), dp(paddingDp));
        return layout;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(2), 0, dp(2), 0);
        return params;
    }

    private int color(int id) { return getColor(id); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void toast(String value) {
        Toast.makeText(this, value == null ? "Something went wrong" : value, Toast.LENGTH_LONG).show();
    }

    private static String message(Exception error) {
        return error.getMessage() == null ? "Request failed" : error.getMessage();
    }

    private static String cleanName(String name, String fallback) {
        return name == null || name.trim().isEmpty() ? fallback : name.trim();
    }

    private static String number(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    private static String formatDateOrNever(String raw) {
        return raw == null || raw.trim().isEmpty() ? "Never" : formatDate(raw);
    }

    private static String formatDate(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "Unknown";
        try {
            SimpleDateFormat source = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            source.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date parsed = source.parse(raw);
            return new SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(parsed);
        } catch (ParseException ignored) {
            return raw;
        }
    }
}
