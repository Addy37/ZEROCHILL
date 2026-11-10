package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

/** Exercise actual destination image factories, not a separately configured test ImageView. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LibraryThumbnailFitTest {
    @Test public void destinationPhotosCoverBothTileShapesWithoutStretchingAtDifferentWidths() {
        Activity saved = Robolectric.buildActivity(FavoritesActivity.class).get();
        Activity downloads = Robolectric.buildActivity(DownloadedActivity.class).get();
        Activity likes = Robolectric.buildActivity(LikedVideosActivity.class).get();
        for (Activity activity : List.of(saved, downloads, likes)) {
            activity.setTheme(R.style.Theme_CrazyShit);
            ImageView image = actualPhoto(activity);
            try {
                // Square, 4:3, 2:3 and ultrawide artwork all retain the same scale on both axes.
                for (int[] source : new int[][]{{400,400}, {400,300}, {300,450}, {640,240}}) {
                    Bitmap bitmap = artwork(source[0], source[1]);
                    image.setImageBitmap(bitmap);
                    for (int[] tile : new int[][]{{144,256}, {288,162}, {276,491}, {552,311}}) {
                        image.measure(exact(tile[0]), exact(tile[1]));
                        image.layout(0, 0, tile[0], tile[1]);
                        float[] matrix = new float[9];
                        image.getImageMatrix().getValues(matrix);
                        assertEquals(activity.getClass().getSimpleName(),
                                matrix[Matrix.MSCALE_X], matrix[Matrix.MSCALE_Y], .001f);
                        RectF displayed = new RectF(0, 0, image.getDrawable().getIntrinsicWidth(),
                                image.getDrawable().getIntrinsicHeight());
                        image.getImageMatrix().mapRect(displayed);
                        assertTrue("Photo must reach left/top: " + displayed,
                                displayed.left <= .5f && displayed.top <= .5f);
                        assertTrue("Photo must cover tile: " + displayed,
                                displayed.right >= tile[0] - .5f && displayed.bottom >= tile[1] - .5f);
                    }
                    image.setImageDrawable(null);
                    bitmap.recycle();
                }
            } finally { Glide.with(image).clear(image); }
        }
    }

    @Test public void missingDownloadArtworkKeepsItsIconInsideTheTile() {
        DownloadedActivity activity = Robolectric.buildActivity(DownloadedActivity.class).get();
        View root = ReflectionHelpers.callInstanceMethod(activity, "thumbnail",
                ReflectionHelpers.ClassParameter.from(VideoDownloadStore.Entry.class, entry("")));
        ImageView image = findImage(root);
        assertEquals(ImageView.ScaleType.CENTER_INSIDE, image.getScaleType());
        assertNotNull(image.getDrawable());
    }

    @Test public void renderTheExistingMixedGalleryWithFilledArtwork() throws Exception {
        FavoritesActivity activity = Robolectric.buildActivity(FavoritesActivity.class).get();
        activity.setTheme(R.style.Theme_CrazyShit);
        LinearLayout root = BrowseUi.screen(activity);
        LinearLayout header = new LinearLayout(activity);
        android.widget.TextView title = BrowseUi.text(activity, "History", 22, Color.WHITE);
        header.addView(BrowseUi.action(activity, "‹", "Back", v -> {}),
                new LinearLayout.LayoutParams(48, 48));
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(BrowseUi.action(activity, "⋮", "Section options", v -> {}),
                new LinearLayout.LayoutParams(48, 48));
        LibraryDestinationChrome.attachHeader(root, header, title);
        LibraryMixedMediaGrid grid = new LibraryMixedMediaGrid(activity);
        root.addView(grid, new LinearLayout.LayoutParams(-1, -2));
        List<View> cards = new ArrayList<>();
        List<Bitmap> bitmaps = new ArrayList<>();
        for (int[] source : new int[][]{{400,300}, {300,450}, {400,400}}) {
            LibraryAspectMediaCard card = new LibraryAspectMediaCard(activity);
            LibraryDestinationChrome.styleMediaCard(activity, card);
            ImageView image = actualPhoto(activity);
            ((ViewGroup) image.getParent()).removeView(image);
            Bitmap bitmap = artwork(source[0], source[1]);
            bitmaps.add(bitmap);
            image.setImageBitmap(bitmap);
            card.addView(image, new ViewGroup.LayoutParams(-1, -1));
            cards.add(card);
        }
        grid.setItems(cards, List.of("landscape", "portrait", "square"),
                Map.of("portrait", true, "square", true));
        try {
            for (int width : new int[]{320, 600}) {
                root.measure(exact(width), exact(1000));
                root.layout(0, 0, width, 1000);
                Bitmap render = Bitmap.createBitmap(width, 1000, Bitmap.Config.ARGB_8888);
                root.draw(new Canvas(render));
                File dir = new File("build/reports/visual-tests");
                assertTrue(dir.isDirectory() || dir.mkdirs());
                try (FileOutputStream out = new FileOutputStream(new File(dir,
                        "library-thumbnail-fit-" + width + ".png"))) {
                    assertTrue(render.compress(Bitmap.CompressFormat.PNG, 100, out));
                }
                render.recycle();
            }
        } finally {
            grid.dispose();
            for (Bitmap bitmap : bitmaps) bitmap.recycle();
        }
    }

    private static ImageView actualPhoto(Activity activity) {
        View root;
        if (activity instanceof FavoritesActivity) {
            root = ReflectionHelpers.callInstanceMethod(activity, "makeThumbnail",
                    ReflectionHelpers.ClassParameter.from(String.class, "fixture"));
        } else if (activity instanceof DownloadedActivity) {
            root = ReflectionHelpers.callInstanceMethod(activity, "thumbnail",
                    ReflectionHelpers.ClassParameter.from(VideoDownloadStore.Entry.class,
                            entry("file:///nonexistent-library-test-poster.png")));
        } else {
            RecyclerView.Adapter<?> adapter = ReflectionHelpers.getField(activity, "adapter");
            root = adapter.onCreateViewHolder(new LinearLayout(activity), 0).itemView;
        }
        ImageView image = findImage(root);
        assertNotNull(image);
        Glide.with(image).clear(image);
        return image;
    }

    private static ImageView findImage(View view) {
        if (view instanceof ImageView) return (ImageView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                ImageView image = findImage(group.getChildAt(i));
                if (image != null) return image;
            }
        }
        return null;
    }

    private static VideoDownloadStore.Entry entry(String image) {
        return new VideoDownloadStore.Entry(1, "Fixture", "fixture", image, "", "video/mp4",
                0, android.app.DownloadManager.STATUS_SUCCESSFUL, 0, 0, "", 0);
    }
    private static int exact(int n) { return View.MeasureSpec.makeMeasureSpec(n, View.MeasureSpec.EXACTLY); }
    private static Bitmap artwork(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setDensity(Bitmap.DENSITY_NONE);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(0xFF143746);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xFF207D91);
        canvas.drawRect(0, 0, width / 3f, height, paint);
        paint.setColor(0xFF7B526B);
        canvas.drawRect(width * 2f / 3f, 0, width, height, paint);
        paint.setColor(0xFFE8C078);
        canvas.drawCircle(width / 2f, height / 2f, Math.min(width, height) * .22f, paint);
        return bitmap;
    }
}
