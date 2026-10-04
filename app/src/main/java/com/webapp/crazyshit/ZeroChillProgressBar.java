package com.webapp.crazyshit;

import android.content.Context;
import android.widget.ProgressBar;

/** Small three-dot indicator for indeterminate app loading states. */
final class ZeroChillProgressBar extends ProgressBar {
    ZeroChillProgressBar(Context context) {
        super(context);
        setIndeterminateDrawable(new ZeroChillDotsDrawable(context));
        setContentDescription("Loading");
    }

    ZeroChillProgressBar(Context context, android.util.AttributeSet attrs, int style) {
        super(context, attrs, style);
    }
}
