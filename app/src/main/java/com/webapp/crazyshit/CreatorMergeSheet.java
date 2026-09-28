package com.webapp.crazyshit;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import java.util.Map;
import java.util.Set;

/** Compact local-only name and saved-avatar selector for manual creator relationships. */
final class CreatorMergeSheet {
    interface Selection { void onConfirm(String nameKey, String avatarKey); }

    private CreatorMergeSheet() { }

    static void show(Activity activity, String title, Map<String, NativeContentItem> members,
                     String defaultName, String defaultAvatar, String action, Selection confirm) {
        if (activity.isFinishing() || activity.isDestroyed() || members.size() < 2) return;
        String[] name = {members.containsKey(defaultName) ? defaultName : members.keySet().iterator().next()};
        String[] avatar = {members.containsKey(defaultAvatar) ? defaultAvatar : name[0]};
        if (members.get(avatar[0]).imageUrl.isEmpty()) {
            for (Map.Entry<String, NativeContentItem> entry : members.entrySet()) {
                if (!entry.getValue().imageUrl.isEmpty()) { avatar[0] = entry.getKey(); break; }
            }
        }
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        dialog.setDismissWithAnimation(true);
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, 20), dp(activity, 18), dp(activity, 20), dp(activity, 24));
        panel.setBackground(background(activity, Color.rgb(16, 19, 23), 24, 1));
        TextView heading = label(activity, title, 20, Color.WHITE);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        panel.addView(heading);
        TextView hint = label(activity,
                "Tap a name for the primary identity. Tap an avatar to choose its saved artwork.",
                13, Color.rgb(169, 181, 193));
        hint.setPadding(0, dp(activity, 6), 0, dp(activity, 14));
        panel.addView(hint);
        ScrollView scroll = new ScrollView(activity);
        LinearLayout choices = new LinearLayout(activity);
        choices.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(choices);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        Runnable[] redraw = new Runnable[1];
        redraw[0] = () -> {
            choices.removeAllViews();
            for (Map.Entry<String, NativeContentItem> entry : members.entrySet()) {
                String key = entry.getKey();
                NativeContentItem item = entry.getValue();
                LinearLayout row = new LinearLayout(activity);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(activity, 10), dp(activity, 8), dp(activity, 10), dp(activity, 8));
                row.setBackground(background(activity, Color.rgb(23, 28, 34), 16,
                        key.equals(name[0]) ? dp(activity, 1) : 0));
                ImageView picture = new ImageView(activity);
                picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
                picture.setBackgroundColor(Color.rgb(28, 33, 39));
                picture.setContentDescription("Use " + item.title + " avatar");
                if (item.imageUrl.isEmpty()) picture.setImageResource(R.drawable.ic_more_account);
                else Glide.with(picture).load(item.imageUrl).onlyRetrieveFromCache(true)
                        .circleCrop().dontAnimate().error(R.drawable.ic_more_account).into(picture);
                row.addView(picture, new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)));
                picture.setOnClickListener(v -> { if (!item.imageUrl.isEmpty()) {
                    avatar[0] = key; redraw[0].run();
                }});
                TextView text = label(activity, item.title + (key.equals(name[0]) ? "  ·  Primary" : "")
                        + (key.equals(avatar[0]) && !item.imageUrl.isEmpty() ? "  ·  Avatar" : ""),
                        14, Color.WHITE);
                text.setPadding(dp(activity, 12), 0, 0, 0);
                row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
                row.setOnClickListener(v -> { name[0] = key; redraw[0].run(); });
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
                params.bottomMargin = dp(activity, 7);
                choices.addView(row, params);
            }
        };
        redraw[0].run();
        LinearLayout buttons = new LinearLayout(activity);
        buttons.setGravity(Gravity.END);
        TextView cancel = label(activity, "Cancel", 14, Color.rgb(190, 200, 209));
        cancel.setPadding(dp(activity, 17), dp(activity, 14), dp(activity, 17), dp(activity, 14));
        cancel.setOnClickListener(v -> dialog.dismiss());
        buttons.addView(cancel);
        TextView accept = label(activity, action, 14, Color.WHITE);
        accept.setTypeface(null, android.graphics.Typeface.BOLD);
        accept.setPadding(dp(activity, 22), dp(activity, 12), dp(activity, 22), dp(activity, 12));
        accept.setBackground(background(activity, UiPalette.PRIMARY, 16, 0));
        accept.setOnClickListener(v -> { dialog.dismiss(); confirm.onConfirm(name[0], avatar[0]); });
        buttons.addView(accept);
        panel.addView(buttons);
        dialog.setContentView(panel);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        }
        View bottom = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottom != null) bottom.setBackgroundColor(Color.TRANSPARENT);
    }

    private static GradientDrawable background(Activity activity, int color, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(activity, radius));
        if (stroke > 0) drawable.setStroke(stroke, UiPalette.PRIMARY);
        return drawable;
    }

    private static TextView label(Activity activity, String text, int size, int color) {
        TextView view = new TextView(activity);
        view.setText(text); view.setTextSize(size); view.setTextColor(color);
        return view;
    }

    private static int dp(Activity activity, int value) { return BrowseUi.dp(activity, value); }
}
