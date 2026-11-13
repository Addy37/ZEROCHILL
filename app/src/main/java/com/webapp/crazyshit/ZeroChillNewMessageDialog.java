package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Locale;

/** On-demand username lookup for a new DM. No background polling or contact discovery. */
final class ZeroChillNewMessageDialog {
    private ZeroChillNewMessageDialog() { }

    static Dialog show(Activity activity) {
        final String owner = ZeroChillSessionStore.currentUserId(activity);
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, 20), dp(activity, 20), dp(activity, 20), dp(activity, 18));
        panel.setBackground(ZeroChillUi.rounded(
                activity, AppearanceTheme.surface(activity), AppearanceTheme.outline(activity),
                R.dimen.zc_radius_large));

        TextView title = BrowseUi.text(activity, "NEW MESSAGE", 19, AppearanceTheme.primaryText(activity));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        panel.addView(title);

        TextView info = BrowseUi.text(activity, "Find someone by their ZEROCHILL username.", 13,
                AppearanceTheme.secondaryText(activity));
        info.setPadding(0, dp(activity, 6), 0, dp(activity, 14));
        panel.addView(info);

        EditText username = new ZeroChillEditText(activity);
        username.setSingleLine(true);
        username.setHint("@username");
        username.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        username.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        username.setTextColor(AppearanceTheme.primaryText(activity));
        username.setHintTextColor(AppearanceTheme.secondaryText(activity));
        username.setTextSize(16);
        username.setFilters(new InputFilter[]{new InputFilter.LengthFilter(21)});
        username.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
        username.setBackground(ZeroChillUi.rounded(activity,
                AppearanceTheme.raised(activity), AppearanceTheme.outline(activity),
                R.dimen.zc_radius_medium));
        panel.addView(username, new LinearLayout.LayoutParams(-1, -2));

        TextView search = BrowseUi.text(activity, "SEARCH USERNAMES", 14, AppearanceTheme.onAccent(activity));
        search.setGravity(Gravity.CENTER);
        search.setTypeface(null, android.graphics.Typeface.BOLD);
        search.setBackground(ZeroChillUi.rounded(activity,
                AppearanceTheme.accent(activity), AppearanceTheme.accent(activity),
                R.dimen.zc_radius_medium));
        search.setFocusable(true);
        search.setClickable(true);
        search.setContentDescription("Search ZEROCHILL usernames");
        ZeroChillMotion.installPressFeedback(search);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(activity, 46));
        searchParams.topMargin = dp(activity, 12);
        panel.addView(search, searchParams);

        TextView status = BrowseUi.text(activity, "Enter at least 2 characters and tap Search.", 12,
                AppearanceTheme.secondaryText(activity));
        status.setPadding(0, dp(activity, 14), 0, dp(activity, 10));
        panel.addView(status);

        ScrollView scroller = new ScrollView(activity);
        scroller.setFillViewport(false);
        LinearLayout results = new LinearLayout(activity);
        results.setOrientation(LinearLayout.VERTICAL);
        scroller.addView(results);
        panel.addView(scroller, new LinearLayout.LayoutParams(-1, dp(activity, 260)));

        View.OnClickListener lookup = v -> {
            final String term = username.getText().toString().trim().replaceFirst("^@", "")
                    .toLowerCase(Locale.ROOT);
            if (!term.matches("[a-z0-9_]{2,20}")) {
                status.setText("Enter 2 to 20 username characters (letters, numbers or _).");
                results.removeAllViews();
                return;
            }
            if (!owner.equals(ZeroChillSessionStore.currentUserId(activity))) {
                dialog.dismiss();
                return;
            }
            search.setEnabled(false);
            search.setAlpha(0.5f);
            status.setText("Searching...");
            results.removeAllViews();

            ZeroChillSocialRepository.searchProfiles(activity, term, (profiles, error) ->
                    activity.runOnUiThread(() -> {
                        if (!dialog.isShowing() || activity.isFinishing() || activity.isDestroyed()
                                || !owner.equals(ZeroChillSessionStore.currentUserId(activity))) return;
                        search.setEnabled(true);
                        search.setAlpha(1f);
                        String latest = username.getText().toString().trim().replaceFirst("^@", "")
                                .toLowerCase(Locale.ROOT);
                        if (!term.equals(latest)) {
                            status.setText("Search again for the new username.");
                            return;
                        }
                        if (error != null || profiles == null) {
                            status.setText("Unable to search usernames. Try again.");
                            return;
                        }
                        if (profiles.isEmpty()) {
                            status.setText("No matching users found.");
                            return;
                        }
                        status.setText(profiles.size() == 20 ? "Showing first 20 matches."
                                : profiles.size() + (profiles.size() == 1 ? " account found" : " accounts found"));
                        renderResults(activity, dialog, owner, results, profiles);
                    }));
        };
        search.setOnClickListener(lookup);
        username.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;
            if (search.isEnabled()) lookup.onClick(search);
            return true;
        });

        dialog.setContentView(panel);
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            attributes.dimAmount = 0.7f;
            attributes.y = dp(activity, 10);
            window.setAttributes(attributes);
            int screen = activity.getResources().getDisplayMetrics().widthPixels;
            window.setLayout(Math.min(screen - dp(activity, 24), dp(activity, 560)),
                    WindowManager.LayoutParams.WRAP_CONTENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        return dialog;
    }

    private static void renderResults(Activity activity, Dialog dialog, String owner,
                                      LinearLayout results,
                                      ArrayList<ZeroChillSocialRepository.PublicProfile> profiles) {
        results.removeAllViews();
        for (ZeroChillSocialRepository.PublicProfile profile : profiles) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(activity, 14), dp(activity, 11), dp(activity, 14), dp(activity, 11));
            row.setBackground(ZeroChillUi.rounded(activity,
                    AppearanceTheme.raised(activity), AppearanceTheme.outline(activity),
                    R.dimen.zc_radius_medium));

            String name = profile.displayName == null || profile.displayName.trim().isEmpty()
                    ? "@" + profile.username : profile.displayName;
            TextView label = BrowseUi.text(activity, name, 15, AppearanceTheme.primaryText(activity));
            label.setTypeface(null, android.graphics.Typeface.BOLD);
            label.setSingleLine(true);
            row.addView(label);

            if (!name.equals("@" + profile.username)) {
                TextView handle = BrowseUi.text(activity, "@" + profile.username, 12,
                        AppearanceTheme.secondaryText(activity));
                row.addView(handle);
            }

            row.setClickable(true);
            row.setFocusable(true);
            row.setContentDescription("Message @" + profile.username);
            row.setOnClickListener(v -> {
                if (!owner.equals(ZeroChillSessionStore.currentUserId(activity))) {
                    dialog.dismiss();
                    return;
                }
                Intent intent = new Intent(activity, ZeroChillMessageActivity.class);
                intent.putExtra(ZeroChillMessageActivity.EXTRA_USER_ID, profile.userId);
                dialog.dismiss();
                activity.startActivity(intent);
            });
            ZeroChillMotion.installPressFeedback(row);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(activity, 7);
            results.addView(row, lp);
        }
    }

    private static int dp(Activity activity, int value) {
        return BrowseUi.dp(activity, value);
    }
}
