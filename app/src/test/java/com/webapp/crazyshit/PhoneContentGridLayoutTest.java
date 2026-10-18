package com.webapp.crazyshit;

import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Application;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PhoneContentGridLayoutTest {
    @Test @Config(qualifiers = "w320dp-h640dp")
    public void compactPortrait() { checkShelves(288); }

    @Test @Config(qualifiers = "w800dp-h360dp-land")
    public void landscape() { checkShelves(768); }

    @Test @Config(qualifiers = "w1000dp-h800dp")
    public void largeWindow() { checkShelves(968); }

    @Test @Config(qualifiers = "w360dp-h800dp")
    public void largerTextKeepsShowTitleOutsideArtwork() {
        Activity activity = activity();
        android.content.res.Configuration configuration = new android.content.res.Configuration(
                activity.getResources().getConfiguration());
        configuration.fontScale = 1.5f;
        activity.getResources().updateConfiguration(configuration,
                activity.getResources().getDisplayMetrics());
        ShowsHubView hub = new ShowsHubView(activity, item -> {}, item -> {}, item -> {});
        hub.setCrazyShit(items(NativeContentItem.KIND_SERIES));
        RecyclerView rail = rail(hub, "crazyShelf");
        layout(rail, dp(activity, 328));
        checkShowCard(rail.findViewHolderForAdapterPosition(0));
        hub.setActive(false);
    }

    private void checkShelves(int widthDp) {
        Activity activity = activity();
        List<NativeContentItem> clicked = new ArrayList<>();
        ShowsHubView shows = new ShowsHubView(activity, clicked::add, item -> {}, item -> {});
        List<NativeContentItem> showsItems = items(NativeContentItem.KIND_SERIES);
        shows.setCrazyShit(showsItems);
        RecyclerView showsRail = rail(shows, "crazyShelf");
        layout(showsRail, dp(activity, widthDp));
        checkGrid(showsRail);
        checkShowCard(showsRail.findViewHolderForAdapterPosition(0));
        checkShowCard(showsRail.findViewHolderForAdapterPosition(1));
        assertEquals(showsRail.findViewHolderForAdapterPosition(0).itemView.getHeight(),
                showsRail.findViewHolderForAdapterPosition(1).itemView.getHeight());
        showsRail.findViewHolderForAdapterPosition(1).itemView.performClick();
        assertSame(showsItems.get(1), clicked.get(0));
        render(showsRail, "shows-grid-" + widthDp);
        shows.setActive(false);

        OnlyFapHubView only = new OnlyFapHubView(activity, new OnlyFapHubView.Listener() {
            @Override public void onOpenCreator(NativeContentItem creator) { clicked.add(creator); }
            @Override public void onSearch() {}
            @Override public void onMore() {}
            @Override public void onViewAllFavorites() {}
        });
        RecyclerView onlyRail = rail(only, "newShelf");
        List<NativeContentItem> creators = items(NativeContentItem.KIND_CREATOR);
        ReflectionHelpers.callInstanceMethod(onlyRail.getAdapter(), "replace",
                ReflectionHelpers.ClassParameter.from(List.class, creators));
        layout(onlyRail, dp(activity, widthDp));
        checkGrid(onlyRail);
        for (int i = 0; i < 2; i++) {
            RecyclerView.ViewHolder holder = onlyRail.findViewHolderForAdapterPosition(i);
            TextView title = ReflectionHelpers.getField(holder, "title");
            checkTitle(title);
            assertEquals(creators.get(i).title, title.getText().toString());
            assertTrue(holder.itemView.getHeight() > holder.itemView.getWidth());
            assertEquals(216f / 148f,
                    holder.itemView.getHeight() / (float) holder.itemView.getWidth(), 0.01f);
            // The copy container contains only the creator name, with no helper label.
            assertEquals(1, ((android.view.ViewGroup) title.getParent()).getChildCount());
        }
        onlyRail.findViewHolderForAdapterPosition(1).itemView.performClick();
        assertSame(creators.get(1), clicked.get(1));
        render(onlyRail, "onlyfap-grid-" + widthDp);
        only.close();
        activity.finish();
    }

    private void checkGrid(RecyclerView rail) {
        GridLayoutManager manager = (GridLayoutManager) rail.getLayoutManager();
        assertEquals(2, manager.getSpanCount());
        assertTrue(manager.canScrollHorizontally());
        assertFalse(manager.canScrollVertically());
        assertTrue(rail.getAdapter().hasStableIds());
        View first = manager.findViewByPosition(0);
        View second = manager.findViewByPosition(1);
        assertNotNull(first);
        assertNotNull(second);
        assertEquals(first.getLeft(), second.getLeft());
        assertTrue(second.getTop() >= first.getBottom());
        assertTrue(second.getBottom() <= rail.getHeight() - rail.getPaddingBottom());
        assertTrue(rail.getChildCount() < rail.getAdapter().getItemCount());
        int oldFirst = first.getLeft();
        int oldSecond = second.getLeft();
        rail.scrollBy(36, 0);
        assertTrue(first.getLeft() < oldFirst);
        assertEquals(first.getLeft() - oldFirst, second.getLeft() - oldSecond);
    }

    private void checkShowCard(RecyclerView.ViewHolder holder) {
        ImageView image = ReflectionHelpers.getField(holder, "image");
        TextView title = ReflectionHelpers.getField(holder, "title");
        checkTitle(title);
        assertEquals(16f / 9f, image.getWidth() / (float) image.getHeight(), 0.02f);
        assertTrue(title.getTop() >= image.getBottom());
        assertTrue(title.getHeight() >= title.getLineHeight() * 2);
        assertTrue(title.getBottom() <= holder.itemView.getHeight());
    }

    private void checkTitle(TextView title) {
        assertEquals(2, title.getMinLines());
        assertEquals(2, title.getMaxLines());
        assertEquals(TextUtils.TruncateAt.END, title.getEllipsize());
    }

    private Activity activity() {
        return Robolectric.buildActivity(Activity.class).setup().get();
    }

    private RecyclerView rail(Object hub, String name) {
        Object shelf = ReflectionHelpers.getField(hub, name);
        return ReflectionHelpers.getField(shelf, "rail");
    }

    private void layout(RecyclerView rail, int width) {
        int height = rail.getLayoutParams().height;
        rail.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        rail.layout(0, 0, width, height);
    }

    private void render(RecyclerView rail, String name) {
        java.io.File folder = new java.io.File("build/reports/visual-tests");
        assertTrue(folder.exists() || folder.mkdirs());
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                rail.getWidth(), rail.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        canvas.drawColor(android.graphics.Color.BLACK);
        rail.draw(canvas);
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(
                new java.io.File(folder, name + ".png"))) {
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out));
        } catch (java.io.IOException error) {
            throw new AssertionError(error);
        } finally {
            bitmap.recycle();
        }
    }

    private int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private List<NativeContentItem> items(String kind) {
        List<NativeContentItem> items = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            items.add(new NativeContentItem(kind, i % 2 == 0 ? "Short" :
                    "A much longer title that needs two lines and then an ellipsis at the end",
                    "https://example.com/item/" + i, "", "", "", ""));
        }
        return items;
    }
}
