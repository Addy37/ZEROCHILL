package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class AlbumSearchCardTest {
    @Test public void albumCoverFitsEntireArtworkAndRecycledNormalCardsKeepExistingGeometry() {
        var host = Robolectric.buildActivity(Activity.class).setup();
        GlobalSearchAdapter adapter = new GlobalSearchAdapter(item -> {});
        try {
            NativeContentItem album = new NativeContentItem(NativeContentItem.KIND_SERIES,
                    "Mountain collection", "https://example.invalid/album/1", "", "200",
                    "A long sample uploader name", "", "12 pictures · 3 videos");
            NativeContentItem normal = new NativeContentItem(NativeContentItem.KIND_CATEGORY,
                    "Ordinary category", "https://example.invalid/category/1", "", "", "", "");
            adapter.replace(Arrays.asList(
                    GlobalSearchAdapter.Entry.item(album, GlobalSearchAdapter.SOURCE_ALBUM),
                    GlobalSearchAdapter.Entry.item(normal, GlobalSearchAdapter.SOURCE_REMOTE)));
            GlobalSearchAdapter.ResultHolder holder = (GlobalSearchAdapter.ResultHolder)
                    adapter.onCreateViewHolder(new FrameLayout(host.get()), 1);
            for (int position : new int[]{0, 1, 0}) {
                adapter.onBindViewHolder(holder, position);
                boolean isAlbum = position == 0;
                assertEquals(isAlbum ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP,
                        holder.image.getScaleType());
                assertEquals(BrowseUi.dp(host.get(), isAlbum ? 96 : 100), holder.image.getLayoutParams().width);
                assertEquals(BrowseUi.dp(host.get(), isAlbum ? 120 : 75), holder.image.getLayoutParams().height);
                assertEquals(BrowseUi.dp(host.get(), isAlbum ? 136 : 104), holder.card.getMinimumHeight());
                assertEquals(isAlbum ? 3 : 1, holder.meta.getMaxLines());
                assertTrue(holder.image.getClipToOutline());
                if (isAlbum) assertTrue(holder.meta.getText().toString().startsWith("12 pictures · 3 videos"));
                for (int width : new int[]{320, 400}) {
                    holder.card.measure(View.MeasureSpec.makeMeasureSpec(BrowseUi.dp(host.get(), width), View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                    holder.card.layout(0, 0, holder.card.getMeasuredWidth(), holder.card.getMeasuredHeight());
                    View row = (View) holder.image.getParent();
                    assertEquals(holder.image.getLayoutParams().height, holder.image.getHeight());
                    assertTrue(holder.image.getTop() >= row.getPaddingTop());
                    assertTrue(holder.image.getBottom() <= row.getHeight() - row.getPaddingBottom());
                }
            }
        } finally {
            adapter.close();
            host.pause().stop().destroy();
        }
    }
}
