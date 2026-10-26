package com.webapp.crazyshit;

import android.app.Activity;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.List;
import java.util.Locale;

/** Compact range picker for source lists that do not expose season metadata. */
final class ShowsEpisodeRangeSheet {
    interface Listener {
        void onSelected(int index);
    }

    private ShowsEpisodeRangeSheet() {
    }

    static void show(
            Activity activity,
            List<NativeContentItem> episodes,
            int selectedStart,
            Listener listener
    ) {
        if (activity == null || activity.isFinishing() || episodes == null || episodes.isEmpty()) return;

        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        dialog.setDismissWithAnimation(true);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity, 18), dp(activity, 14), dp(activity, 18), dp(activity, 20));
        root.setBackground(ZeroChillUi.rounded(
                activity,
                Color.rgb(8, 10, 12),
                ZeroChillUi.color(activity, R.color.zc_edge),
                R.dimen.zc_radius_large
        ));

        TextView title = text(activity, "Jump to episodes", 21f, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView count = text(
                activity,
                episodes.size() + " episodes  •  groups of " + ShowsEpisodeRanges.RANGE_SIZE,
                12f,
                ZeroChillUi.color(activity, R.color.zc_text_secondary)
        );
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(-1, -2);
        countParams.topMargin = dp(activity, 3);
        root.addView(count, countParams);

        EditText search = new EditText(activity);
        search.setSingleLine(true);
        search.setTextSize(15f);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(ZeroChillUi.color(activity, R.color.zc_text_muted));
        search.setHint("Search episode titles");
        search.setPadding(dp(activity, 14), 0, dp(activity, 14), 0);
        search.setBackground(ZeroChillUi.rounded(
                activity,
                ZeroChillUi.color(activity, R.color.zc_surface_glass_strong),
                ZeroChillUi.color(activity, R.color.zc_edge),
                R.dimen.zc_radius_medium
        ));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(activity, 48));
        searchParams.topMargin = dp(activity, 14);
        root.addView(search, searchParams);

        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(0, dp(activity, 8), 0, dp(activity, 4));
        scroll.addView(rows, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, dp(activity, 430));
        scrollParams.topMargin = dp(activity, 4);
        root.addView(scroll, scrollParams);

        Runnable renderRanges = () -> renderRanges(
                activity,
                rows,
                episodes.size(),
                selectedStart,
                listener,
                dialog
        );
        renderRanges.run();

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s == null ? "" : s.toString().trim();
                if (query.isEmpty()) renderRanges.run();
                else renderMatches(activity, rows, episodes, query, listener, dialog);
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        dialog.setContentView(root);
        dialog.show();
    }

    private static void renderRanges(
            Activity activity,
            LinearLayout rows,
            int total,
            int selectedStart,
            Listener listener,
            BottomSheetDialog dialog
    ) {
        rows.removeAllViews();
        int current = ShowsEpisodeRanges.startForIndex(selectedStart, total);
        for (int start : ShowsEpisodeRanges.starts(total)) {
            String label = "Episodes " + ShowsEpisodeRanges.label(start, total);
            addRow(activity, rows, label, start == current, () -> {
                if (listener != null) listener.onSelected(start);
                dialog.dismiss();
            });
        }
    }

    private static void renderMatches(
            Activity activity,
            LinearLayout rows,
            List<NativeContentItem> episodes,
            String query,
            Listener listener,
            BottomSheetDialog dialog
    ) {
        rows.removeAllViews();
        String needle = query.toLowerCase(Locale.US);
        int shown = 0;
        for (int index = 0; index < episodes.size() && shown < 40; index++) {
            NativeContentItem item = episodes.get(index);
            String title = item == null || item.title == null ? "" : item.title.trim();
            if (!title.toLowerCase(Locale.US).contains(needle)) continue;
            final int selected = index;
            addRow(activity, rows, "#" + (index + 1) + "  " + title, false, () -> {
                if (listener != null) listener.onSelected(selected);
                dialog.dismiss();
            });
            shown++;
        }
        if (shown == 0) {
            TextView empty = text(
                    activity,
                    "No matching episode titles",
                    14f,
                    ZeroChillUi.color(activity, R.color.zc_text_secondary)
            );
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(activity, 28), 0, dp(activity, 28));
            rows.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private static void addRow(
            Activity activity,
            LinearLayout rows,
            String label,
            boolean selected,
            Runnable action
    ) {
        TextView row = text(
                activity,
                label,
                15f,
                selected ? UiPalette.PRIMARY : Color.WHITE
        );
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setTypeface(null, selected
                ? android.graphics.Typeface.BOLD
                : android.graphics.Typeface.NORMAL);
        row.setPadding(dp(activity, 14), 0, dp(activity, 14), 0);
        row.setBackground(ZeroChillUi.rounded(
                activity,
                selected
                        ? ZeroChillUi.color(activity, R.color.zc_cyan_container)
                        : ZeroChillUi.color(activity, R.color.zc_surface_glass_strong),
                selected ? UiPalette.PRIMARY : ZeroChillUi.color(activity, R.color.zc_edge),
                R.dimen.zc_radius_medium
        ));
        row.setOnClickListener(v -> action.run());
        ZeroChillMotion.installPressFeedback(row);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(activity, 50));
        params.topMargin = dp(activity, 6);
        rows.addView(row, params);
    }

    private static TextView text(Activity activity, String value, float size, int color) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
