package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

final class BunkrMaintenanceUi {
    private BunkrMaintenanceUi() { }

    static boolean showIfConfirmed(Activity activity, String page, Throwable error, Runnable retry) {
        if (!BunkrMaintenance.isConfirmed(error) && !BunkrMaintenance.isKnown(page)) return false;
        new ZeroChillDialog.Builder(activity)
                .setTitle(R.string.bunkr_maintenance_title)
                .setMessage(R.string.bunkr_maintenance_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.bunkr_maintenance_retry, (dialog, which) -> {
                    BunkrMaintenance.retry(page);
                    retry.run();
                })
                .show();
        return true;
    }

    static Badge addBadge(FrameLayout frame, int gravity) {
        Badge badge = new Badge(frame.getContext());
        int size = dp(frame.getContext(), 22);
        int margin = dp(frame.getContext(), 6);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size, gravity);
        params.setMargins(margin, margin, margin, margin);
        frame.addView(badge, params);
        return badge;
    }

    static final class Badge extends ImageView {
        private final Runnable expiry = this::refresh;
        private String page;

        private Badge(Context context) {
            super(context);
            setVisibility(GONE);
            setContentDescription(context.getText(R.string.bunkr_maintenance_title));
            int padding = dp(context, 4);
            setPadding(padding, padding, padding, padding);
            setElevation(dp(context, 6));
        }

        void bind(NativeContentItem item) {
            page = item != null && item.isVideo() ? item.url : null;
            refresh();
        }

        void refresh() {
            if (getVisibility() == VISIBLE) removeCallbacks(expiry);
            long remaining = page == null ? 0L : BunkrMaintenance.remainingMillis(page);
            if (remaining > 0L) {
                if (getDrawable() == null) {
                    setImageResource(R.drawable.ic_bunkr_maintenance);
                    setBackground(BrowseUi.rounded(getContext(), Color.argb(220, 0, 0, 0), 7));
                }
                if (getVisibility() != VISIBLE) setColorFilter(AppearanceTheme.accent(getContext()));
                setVisibility(VISIBLE);
                if (isAttachedToWindow()) postDelayed(expiry, remaining);
            } else {
                setVisibility(GONE);
            }
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            refresh();
        }

        @Override protected void onDetachedFromWindow() {
            removeCallbacks(expiry);
            super.onDetachedFromWindow();
        }

        @Override protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility);
            if (visibility == View.VISIBLE) refresh();
        }
    }

    private static int dp(Context context, int size) {
        return Math.round(size * context.getResources().getDisplayMetrics().density);
    }
}
