package com.webapp.crazyshit;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

/** Keeps pull-to-refresh gesture and callbacks, replacing its circular stock glyph. */
final class ZeroChillRefreshLayout extends SwipeRefreshLayout {
    ZeroChillRefreshLayout(Context context) {
        super(context);
        for (int index = 0; index < getChildCount(); index++) {
            View child = getChildAt(index);
            if (child instanceof ImageView) {
                ImageView indicator = (ImageView) child;
                indicator.setBackground(ZeroChillUi.sheetGlass(context));
                indicator.setImageDrawable(new ZeroChillDotsDrawable(context));
                indicator.setContentDescription("Refreshing");
                break;
            }
        }
    }
}
