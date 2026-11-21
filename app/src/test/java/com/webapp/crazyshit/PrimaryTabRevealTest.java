package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PrimaryTabRevealTest {
    @Test public void dragTracksAdjacentPagesAcrossSegmentsAndDirections() {
        assertEquals(RecyclerView.NO_POSITION, PrimaryTabReveal.dragTarget(1f, 1));
        assertEquals(2, PrimaryTabReveal.dragTarget(1.4f, 1));
        assertEquals(2, PrimaryTabReveal.dragTarget(1.2f, 1));
        assertEquals(3, PrimaryTabReveal.dragTarget(2.2f, 1));
        assertEquals(1, PrimaryTabReveal.dragTarget(1.8f, 2));
        assertEquals(0, PrimaryTabReveal.dragTarget(.8f, 2));
        assertEquals(RecyclerView.NO_POSITION, PrimaryTabReveal.dragTarget(0f, 2));
    }

    @Test public void timingAndDepthStayRestrained() {
        assertEquals(290L, PrimaryTabReveal.TAP_DURATION_MS);
        assertEquals(.10f, PrimaryTabReveal.enteringAlpha(0f), .001f);
        assertEquals(1f, PrimaryTabReveal.enteringAlpha(1f), .001f);
        assertEquals(.990f, PrimaryTabReveal.enteringScale(0f), .001f);
        assertEquals(1f, PrimaryTabReveal.enteringScale(1f), .001f);
        assertEquals(1f, PrimaryTabReveal.exitingScale(0f), .001f);
        assertEquals(.988f, PrimaryTabReveal.exitingScale(1f), .001f);
        assertTrue(PrimaryTabReveal.enteringAlpha(.5f) > PrimaryTabReveal.enteringAlpha(.1f));
    }

    @Test public void retainedScreenDrawsFullFrameWithoutAClipOrWipe() {
        PrimaryTabReveal.Page page = page();
        Bitmap frame = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(frame);
        canvas.drawColor(Color.RED);
        page.draw(canvas);
        assertEquals(Color.BLUE, frame.getPixel(0, 0));
        assertEquals(Color.BLUE, frame.getPixel(50, 50));
        assertEquals(Color.BLUE, frame.getPixel(99, 99));
        frame.recycle();
    }

    @Test public void immediatePagerJumpConnectsBothPagesWithoutChangingTheDock() {
        try (var activity = Robolectric.buildActivity(Activity.class).setup()) {
            ViewPager2 pager = new ViewPager2(activity.get());
            pager.setOffscreenPageLimit(MainPagerAdapter.PAGE_COUNT - 1);
            pager.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
                    PrimaryTabReveal.Page page = new PrimaryTabReveal.Page(parent.getContext());
                    page.setLayoutParams(new RecyclerView.LayoutParams(-1, -1));
                    return new RecyclerView.ViewHolder(page) {};
                }
                @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {}
                @Override public int getItemCount() { return MainPagerAdapter.PAGE_COUNT; }
            });
            PrimaryTabReveal reveal = new PrimaryTabReveal(pager);
            pager.setPageTransformer(reveal);
            activity.get().setContentView(pager);
            activity.visible();
            layout(pager);

            View dock = new View(activity.get());
            dock.layout(0, 180, 100, 200);
            assertTrue(reveal.startTap(3, dock));
            pager.setCurrentItem(3, false);
            layout(pager);
            pager.requestTransform();

            RecyclerView pages = (RecyclerView) pager.getChildAt(0);
            View incoming = pages.findViewHolderForAdapterPosition(3).itemView;
            View outgoing = pages.findViewHolderForAdapterPosition(0).itemView;
            View other = pages.findViewHolderForAdapterPosition(1).itemView;
            assertEquals(3, pager.getCurrentItem());
            assertTrue(Math.abs(incoming.getX() - outgoing.getX()) <= 24f);
            assertTrue(incoming.getTranslationZ() > outgoing.getTranslationZ());
            assertTrue(incoming.getAlpha() < 1f);
            // The outgoing screen stays opaque while the incoming page fades in.
            assertTrue(incoming.getAlpha() > 0f);
            assertEquals(1f, outgoing.getAlpha(), .001f);
            assertEquals(0f, other.getTranslationX(), .001f);

            reveal.cancel();
            assertEquals(0f, outgoing.getTranslationX(), .001f);
            assertEquals(0f, incoming.getTranslationZ(), .001f);
            assertEquals(1f, incoming.getAlpha(), .001f);
        }
    }

    private static void layout(ViewPager2 pager) {
        pager.measure(View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY));
        pager.layout(0, 0, 100, 200);
    }

    private static PrimaryTabReveal.Page page() {
        PrimaryTabReveal.Page page = new PrimaryTabReveal.Page(RuntimeEnvironment.getApplication());
        page.setBackgroundColor(Color.BLUE);
        page.layout(0, 0, 100, 100);
        return page;
    }
}
