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

    @Test public void dragClipsFromEitherSideAndReversesWithoutAResidualMask() {
        PrimaryTabReveal.Page page = page();
        Bitmap frame = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(frame);
        canvas.drawColor(Color.RED);
        page.setReveal(PrimaryTabReveal.FROM_RIGHT, .5f, 0f);
        page.draw(canvas);
        assertEquals(Color.RED, frame.getPixel(10, 50));
        assertEquals(Color.BLUE, frame.getPixel(90, 50));
        assertEquals(Color.BLUE, frame.getPixel(47, 50));
        assertEquals(Color.RED, frame.getPixel(47, 5));

        canvas.drawColor(Color.RED);
        page.setReveal(PrimaryTabReveal.FROM_LEFT, .5f, 0f);
        page.draw(canvas);
        assertEquals(Color.BLUE, frame.getPixel(10, 50));
        assertEquals(Color.RED, frame.getPixel(90, 50));

        page.setReveal(0, 0f, 0f);
        page.draw(canvas);
        assertEquals(Color.BLUE, frame.getPixel(90, 50));
        frame.recycle();
    }

    @Test public void tapRevealSweepsUpFromTheWholeBottomEdgeWithADockCrest() {
        PrimaryTabReveal.Page page = page();
        Bitmap frame = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(frame);
        canvas.drawColor(Color.RED);
        page.setReveal(PrimaryTabReveal.BOTTOM, .24f, 75f);
        page.draw(canvas);
        assertEquals(Color.BLUE, frame.getPixel(75, 94));
        assertEquals(Color.RED, frame.getPixel(75, 25));
        assertEquals(Color.BLUE, frame.getPixel(5, 94));
        assertEquals(Color.BLUE, frame.getPixel(74, 74));
        assertEquals(Color.RED, frame.getPixel(5, 74));
        page.setReveal(PrimaryTabReveal.BOTTOM, 1f, 75f);
        page.draw(canvas);
        assertEquals(Color.BLUE, frame.getPixel(5, 5));
        frame.recycle();
    }

    @Test public void highlightUsesTheAccentInANarrowBand() {
        var context = RuntimeEnvironment.getApplication();
        AppearanceTheme.Accent original = AppearanceTheme.accentChoice(context);
        Bitmap frame = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        try {
            AppearanceTheme.setAccent(context, AppearanceTheme.Accent.RED);
            TabWaterline water = new TabWaterline(context);
            assertTrue(water.update(PrimaryTabReveal.BOTTOM, .5f, 50f));
            Canvas canvas = new Canvas(frame);
            canvas.drawColor(Color.BLACK);
            int save = canvas.save();
            water.clipPage(canvas, 100, 100);
            canvas.restoreToCount(save);
            water.drawHighlight(canvas, 100, 100);

            int accentPixels = 0;
            for (int y = 0; y < 100; y++) {
                for (int x = 0; x < 100; x++) {
                    int pixel = frame.getPixel(x, y);
                    if (pixel != Color.BLACK) {
                        assertTrue(Color.red(pixel) >= Color.blue(pixel));
                        accentPixels++;
                    }
                }
            }
            assertTrue(accentPixels > 25);
            assertTrue(accentPixels < 300);
            assertFalse(water.update(PrimaryTabReveal.BOTTOM, .5f, 50f));
        } finally {
            AppearanceTheme.setAccent(context, original);
            frame.recycle();
        }
    }

    @Test public void partialRevealDrawsIncomingContentOnce() {
        PrimaryTabReveal.Page page = page();
        int[] draws = {0};
        page.addView(new View(page.getContext()) {
            @Override protected void onDraw(Canvas canvas) {
                draws[0]++;
            }
        }, new ViewGroup.LayoutParams(100, 100));
        page.measure(View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY));
        page.layout(0, 0, 100, 100);
        Bitmap frame = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        page.setReveal(PrimaryTabReveal.BOTTOM, .5f, 75f);
        page.draw(new Canvas(frame));
        assertEquals(1, draws[0]);
        frame.recycle();
    }

    @Test public void immediatePagerJumpKeepsOnlyTheOutgoingAndIncomingPagesAligned() {
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
            assertEquals(incoming.getX(), outgoing.getX(), .01f);
            assertTrue(incoming.getTranslationZ() > outgoing.getTranslationZ());
            assertEquals(0f, other.getTranslationX(), 0f);

            reveal.cancel();
            assertEquals(0f, outgoing.getTranslationX(), 0f);
            assertEquals(0f, incoming.getTranslationZ(), 0f);
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
