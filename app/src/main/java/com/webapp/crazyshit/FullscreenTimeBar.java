package com.webapp.crazyshit;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.DefaultTimeBar;

/** Inline time bar: Media3's default bottom-bar offset does not apply inside a time-label row. */
@UnstableApi
public final class FullscreenTimeBar extends DefaultTimeBar {
    public FullscreenTimeBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override public void setLayoutParams(ViewGroup.LayoutParams params) {
        if (params instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) params).bottomMargin = 0;
        }
        super.setLayoutParams(params);
    }
}
