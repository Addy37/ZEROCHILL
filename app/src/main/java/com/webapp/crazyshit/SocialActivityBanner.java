package com.webapp.crazyshit;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.bumptech.glide.Glide;

/** A touchable decor overlay that leaves the video and keyboard focus alone. */
final class SocialActivityBanner implements SocialActivityCoordinator.Presenter {
    private View banner;
    private ImageView avatar;
    private Activity host;
    private final Runnable timeout = () -> dismiss(true);

    @Override public void show(Activity activity, UpdateInboxStore.Entry entry) {
        hide();
        if (!entry.accountId.equals(ZeroChillSessionStore.currentUserId(activity))) return;
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(decor);
        if (insets != null && insets.isVisible(WindowInsetsCompat.Type.ime())) return;
        host = activity;
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable surface = new GradientDrawable();
        surface.setColor(Color.argb(246, 18, 26, 34));
        surface.setCornerRadius(dp(22));
        surface.setStroke(dp(1), Color.argb(90, 8, 146, 208));
        row.setBackground(surface);
        row.setElevation(dp(8));
        row.setFocusable(false);
        avatar = new ImageView(activity);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setBackground(BrowseUi.rounded(activity, Color.rgb(30, 40, 49), 20));
        avatar.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        avatar.setClipToOutline(true);
        row.addView(avatar, new LinearLayout.LayoutParams(dp(38), dp(38)));
        avatar.setImageResource(R.drawable.ic_more_account);
        AccountAvatarImages.bindUrl(avatar, entry.actorId, entry.avatarUrl);
        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(10), 0, dp(6), 0);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView name = new TextView(activity);
        name.setText(SocialUi.cleanName(entry.actorName));
        name.setTextColor(Color.WHITE);
        name.setTextSize(13);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(name);
        TextView action = new TextView(activity);
        action.setText("reply".equals(entry.socialType) ? "Replied to your comment" : "Liked your comment");
        action.setTextColor(Color.rgb(178, 192, 204));
        action.setTextSize(12);
        copy.addView(action);
        TextView arrow = new TextView(activity);
        arrow.setText("›"); arrow.setTextSize(24); arrow.setTextColor(UiPalette.PRIMARY);
        row.addView(arrow);
        int topInset = insets == null ? dp(28)
                : insets.getInsets(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout()).top;
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        params.setMargins(dp(14), topInset + dp(10), dp(14), 0);
        decor.addView(row, params);
        banner = row;
        row.setContentDescription(SocialUi.cleanName(entry.actorName) + " " + action.getText() + ". Tap to view conversation");
        row.setOnClickListener(v -> {
            if (!entry.accountId.equals(ZeroChillSessionStore.currentUserId(activity))) { hide(); return; }
            hide();
            UpdateInboxStore.markRead(activity, entry.id);
            SocialContentNavigator.open(activity, entry, false);
        });
        ZeroChillMotion.installPressFeedback(row);
        if (ZeroChillMotion.animationsEnabled(activity)) {
            row.setAlpha(0); row.setTranslationY(-dp(18));
            row.animate().alpha(1).translationY(0).setDuration(ZeroChillMotion.STANDARD_MS).start();
        }
        row.postDelayed(timeout, 5000L);
    }

    @Override public void hide() { dismiss(false); }

    private void dismiss(boolean animate) {
        View old = banner;
        if (old == null) return;
        ImageView oldAvatar = avatar;
        banner = null; avatar = null;
        old.removeCallbacks(timeout);
        old.animate().cancel();
        Runnable remove = () -> {
            if (oldAvatar != null) Glide.with(oldAvatar.getContext().getApplicationContext()).clear(oldAvatar);
            if (old.getParent() instanceof ViewGroup) ((ViewGroup) old.getParent()).removeView(old);
        };
        if (animate && ZeroChillMotion.animationsEnabled(old.getContext())) {
            old.animate().alpha(0).translationY(-BrowseUi.dp(old.getContext(), 12))
                    .setDuration(ZeroChillMotion.QUICK_MS).withEndAction(remove).start();
        } else remove.run();
        host = null;
    }
    private int dp(int value) { return BrowseUi.dp(host, value); }
}
