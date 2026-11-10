package com.webapp.crazyshit;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Keeps a media card's overflow menu outside its click-to-open surface.
 *
 * A separate topmost sibling accepts the whole 48dp options touch target, so
 * pressing it cannot accidentally invoke the card's parent click and exit
 * the Library destination.
 */
final class LibraryOverlayActionCard {
    private LibraryOverlayActionCard() { }

    static FrameLayout wrap(Context context, LinearLayout clickableContent, TextView options) {
        FrameLayout root = new FrameLayout(context);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.addView(clickableContent, new FrameLayout.LayoutParams(-1, -2));

        FrameLayout optionsTarget = new FrameLayout(context);
        optionsTarget.setTag("library-card-options");
        optionsTarget.setClickable(true);
        optionsTarget.setFocusable(true);
        optionsTarget.setContentDescription(options.getContentDescription());
        optionsTarget.setOnClickListener(v -> options.performClick());
        FrameLayout.LayoutParams hitArea = new FrameLayout.LayoutParams(
                dp(context, 48), dp(context, 48), Gravity.TOP | Gravity.END);
        hitArea.setMargins(0, dp(context, 2), dp(context, 2), 0);
        root.addView(optionsTarget, hitArea);

        options.setGravity(Gravity.CENTER);
        optionsTarget.addView(options, new FrameLayout.LayoutParams(
                dp(context, 34), dp(context, 34), Gravity.CENTER));
        return root;
    }

    private static int dp(Context context, int n) { return BrowseUi.dp(context, n); }
}
