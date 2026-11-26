package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;
import com.bumptech.glide.signature.ObjectKey;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Handles only a reserved synthetic host. Existing source image loaders remain untouched. */
final class EromeFixtureArtwork {
    private static boolean registered;
    static synchronized void register(Context context) {
        if (registered) return;
        Glide.get(context).getRegistry().prepend(GlideUrl.class, InputStream.class,
                new ModelLoaderFactory<GlideUrl, InputStream>() {
                    public ModelLoader<GlideUrl, InputStream> build(MultiModelLoaderFactory factory) {
                        return new ModelLoader<GlideUrl, InputStream>() {
                            public boolean handles(GlideUrl url) {
                                return EromeFixtureCatalog.isArtwork(url.toStringUrl());
                            }
                            public LoadData<InputStream> buildLoadData(GlideUrl url, int w, int h, Options options) {
                                return new LoadData<>(new ObjectKey(url.toStringUrl()), new DataFetcher<InputStream>() {
                                    public void loadData(Priority priority, DataCallback<? super InputStream> callback) {
                                        try {
                                            Bitmap bitmap = bitmap(url.toStringUrl());
                                            ByteArrayOutputStream out = new ByteArrayOutputStream();
                                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                                            bitmap.recycle();
                                            callback.onDataReady(new ByteArrayInputStream(out.toByteArray()));
                                        } catch (RuntimeException error) { callback.onLoadFailed(error); }
                                    }
                                    public void cleanup() { }
                                    public void cancel() { }
                                    public Class<InputStream> getDataClass() { return InputStream.class; }
                                    public DataSource getDataSource() { return DataSource.LOCAL; }
                                });
                            }
                        };
                    }
                    public void teardown() { }
                });
        registered = true;
    }

    static Bitmap bitmap(String url) {
        int n = Integer.parseInt(url.substring(url.lastIndexOf('/') + 1));
        int w = n % 3 == 0 ? 360 : 240, h = n % 3 == 1 ? 360 : 240;
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int a = Color.HSVToColor(new float[]{(n * 39 + 175) % 360, .7f, .25f});
        int b = Color.HSVToColor(new float[]{(n * 39 + 235) % 360, .66f, .85f});
        paint.setShader(new LinearGradient(0, 0, w, h, a, b, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
        paint.setColor(0x70FFFFFF);
        canvas.drawCircle(w * .72f, h * .28f, Math.min(w, h) * .24f, paint);
        paint.setColor(0x88202030);
        canvas.drawRoundRect(w * .08f, h * .40f, w * .70f, h * .90f, 24, 24, paint);
        paint.setColor(0xD0FFFFFF);
        paint.setTextSize(15);
        canvas.drawText("SAMPLE  " + n, 12, h - 14, paint);
        return bitmap;
    }
    private EromeFixtureArtwork() { }
}
