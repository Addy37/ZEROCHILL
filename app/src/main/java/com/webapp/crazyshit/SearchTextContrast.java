package com.webapp.crazyshit;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** Local ink separation on clear glass, without changing text metrics or row fills. */
final class SearchTextContrast {
    private SearchTextContrast() { }

    static void apply(View view) {
        if (view instanceof TextView) {
            float density = view.getResources().getDisplayMetrics().density;
            ((TextView) view).setShadowLayer(2f * density, 0f, density, 0xE6000000);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) apply(group.getChildAt(i));
        }
    }
}
