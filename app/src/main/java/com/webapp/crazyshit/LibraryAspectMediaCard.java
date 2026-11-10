package com.webapp.crazyshit;

import android.content.Context;
import android.view.View;

import com.google.android.material.card.MaterialCardView;

/** Still-image media tile with a size driven by its detected artwork orientation. */
final class LibraryAspectMediaCard extends MaterialCardView {
    private boolean portrait;

    LibraryAspectMediaCard(Context context) {
        super(context);
    }

    void setPortrait(boolean portrait) {
        if (this.portrait == portrait) return;
        this.portrait = portrait;
        requestLayout();
    }

    boolean isPortrait() { return portrait; }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = View.MeasureSpec.getSize(widthMeasureSpec);
        if (width > 0 && View.MeasureSpec.getMode(widthMeasureSpec) != View.MeasureSpec.UNSPECIFIED) {
            // Landscape 16:9, portrait 9:16. ImageView fills the tile with a proportional center crop.
            int height = Math.max(BrowseUi.dp(getContext(), 64),
                    Math.round(width * (portrait ? 16f / 9f : 9f / 16f)));
            super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }
}
