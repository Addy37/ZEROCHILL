package com.webapp.crazyshit;

import android.content.Context;
import androidx.annotation.NonNull;
import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;
import com.bumptech.glide.signature.ObjectKey;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.Objects;
import java.util.WeakHashMap;

/** Original-only model; keeps ordinary Glide artwork and decoder registration intact. */
final class PawchiveOriginalImageLoader implements ModelLoader<PawchiveOriginalImageLoader.Model, InputStream> {
    private static final WeakHashMap<Glide, Boolean> REGISTERED = new WeakHashMap<>();

    static final int MAX_GALLERY_GIF_BYTES = 4 * 1024 * 1024;
    static final int MAX_GALLERY_GIF_PIXELS = 1024 * 1024;

    static Model model(Context context, String identity, String delivery) {
        return model(context, identity, delivery, false);
    }

    static Model galleryGif(Context context, String identity, String delivery) {
        if (identity == null || !identity.toLowerCase(Locale.ROOT).endsWith(".gif"))
            throw new IllegalArgumentException("Not a GIF identity");
        return model(context, identity, delivery, true);
    }

    private static Model model(Context context, String identity, String delivery, boolean boundedGif) {
        if (!PawchiveRepository.isDeliveryFor(identity, delivery))
            throw new IllegalArgumentException("Invalid Pawchive original");
        Glide glide = Glide.get(context.getApplicationContext());
        synchronized (REGISTERED) {
            if (!REGISTERED.containsKey(glide)) {
                glide.getRegistry().append(Model.class, InputStream.class, new Factory());
                REGISTERED.put(glide, true);
            }
        }
        return new Model(identity, delivery, boundedGif);
    }

    static final class Model {
        final String identity, delivery;
        final boolean boundedGif;
        Model(String identity, String delivery, boolean boundedGif) {
            this.identity = identity;
            this.delivery = delivery;
            this.boundedGif = boundedGif;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Model)) return false;
            Model that = (Model) other;
            return identity.equals(that.identity) && delivery.equals(that.delivery) && boundedGif == that.boundedGif;
        }
        @Override public int hashCode() { return Objects.hash(identity, delivery, boundedGif); }
        @Override public String toString() {
            return "PawchiveOriginal(" + Integer.toHexString(identity.hashCode()) + ")";
        }
    }

    @Override public boolean handles(@NonNull Model model) {
        return PawchiveRepository.isDeliveryFor(model.identity, model.delivery);
    }

    @Override public LoadData<InputStream> buildLoadData(@NonNull Model model, int width, int height,
            @NonNull Options options) {
        if (!handles(model)) return null;
        return new LoadData<>(new ObjectKey((model.boundedGif ? "pawchive-gallery-gif-v1:" : "pawchive-original-v1:") + model.identity), new Fetcher(model));
    }

    private static final class Factory implements ModelLoaderFactory<Model, InputStream> {
        @NonNull @Override public ModelLoader<Model, InputStream> build(@NonNull MultiModelLoaderFactory factory) {
            return new PawchiveOriginalImageLoader();
        }
        @Override public void teardown() { }
    }

    interface ReadCheck { void check() throws IOException; }

    static byte[] readGalleryGif(InputStream input, long declaredLength, ReadCheck check) throws IOException {
        if (declaredLength > MAX_GALLERY_GIF_BYTES) throw new IOException("Gallery GIF too large");
        byte[] header = new byte[10];
        int count = 0;
        while (count < header.length) {
            check.check();
            int n = input.read(header, count, header.length - count);
            if (n <= 0) throw new IOException("Truncated GIF header");
            count += n;
        }
        String signature = new String(header, 0, 6, java.nio.charset.StandardCharsets.US_ASCII);
        int width = (header[6] & 255) | ((header[7] & 255) << 8);
        int height = (header[8] & 255) | ((header[9] & 255) << 8);
        if ((!signature.equals("GIF87a") && !signature.equals("GIF89a"))
                || width == 0 || height == 0 || (long) width * height > MAX_GALLERY_GIF_PIXELS)
            throw new IOException("Unsupported gallery GIF dimensions or signature");
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        output.write(header);
        byte[] buffer = new byte[16 * 1024];
        while (true) {
            check.check();
            int n = input.read(buffer);
            if (n < 0) break;
            if (n == 0) throw new IOException("Empty GIF read");
            if (n > MAX_GALLERY_GIF_BYTES - output.size()) throw new IOException("Gallery GIF too large");
            output.write(buffer, 0, n);
        }
        return output.toByteArray();
    }

    private static final class Fetcher implements DataFetcher<InputStream> {
        private final Model model;
        private volatile boolean canceled;
        private volatile HttpURLConnection connection;
        private volatile InputStream stream;

        Fetcher(Model model) { this.model = model; }

        @Override public void loadData(@NonNull Priority priority,
                @NonNull DataCallback<? super InputStream> callback) {
            try {
                String delivery = model.delivery;
                for (int hop = 0; hop <= 3; hop++) {
                    check();
                    if (!PawchiveRepository.isDeliveryFor(model.identity, delivery))
                        throw new IOException("Invalid original destination");
                    PawchiveMediaRequests.awaitStart(() -> canceled);
                    check();
                    HttpURLConnection current = (HttpURLConnection) new URL(delivery).openConnection();
                    connection = current;
                    current.setInstanceFollowRedirects(false);
                    current.setConnectTimeout(model.boundedGif ? 8_000 : 15_000);
                    current.setReadTimeout(model.boundedGif ? 8_000 : 20_000);
                    current.setRequestProperty("User-Agent", PawchiveMediaRequests.USER_AGENT);
                    current.setRequestProperty("Referer", PawchiveRepository.ORIGIN);
                    current.setRequestProperty("Accept", "image/avif,image/webp,image/*,*/*;q=0.8");
                    current.setRequestProperty("Accept-Encoding", "identity");
                    check();
                    int status = current.getResponseCode();
                    PawchiveMediaRequests.trace(model.identity, "original HTTP " + status);
                    if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                        String location = current.getHeaderField("Location");
                        if (location == null || hop == 3) throw new IOException("Original redirect rejected");
                        delivery = new URL(new URL(delivery), location).toExternalForm();
                        current.disconnect();
                        connection = null;
                        continue;
                    }
                    if (status != 200) throw new IOException("Original HTTP " + status);
                    String type = current.getContentType();
                    type = type == null ? "" : type.toLowerCase(Locale.ROOT);
                    if (!type.startsWith("image/") && !type.startsWith("application/octet-stream"))
                        throw new IOException("Original is not an image");
                    if (model.boundedGif) {
                        long length = current.getContentLengthLong();
                        if (length > MAX_GALLERY_GIF_BYTES) throw new IOException("Gallery GIF too large");
                        long deadline = android.os.SystemClock.elapsedRealtime() + 12_000L;
                        try (InputStream input = current.getInputStream()) {
                            stream = new java.io.ByteArrayInputStream(readGalleryGif(input, length, () -> {
                                check();
                                if (android.os.SystemClock.elapsedRealtime() > deadline)
                                    throw new InterruptedIOException("Gallery GIF timeout");
                            }));
                        }
                        current.disconnect();
                        connection = null;
                    } else stream = current.getInputStream();
                    check();
                    callback.onDataReady(stream);
                    return;
                }
                throw new IOException("Original redirect limit");
            } catch (Exception error) {
                cleanup();
                if (!canceled) {
                    PawchiveMediaRequests.trace(model.identity, "original fetch failed " + error.getClass().getSimpleName());
                    callback.onLoadFailed(error);
                }
            }
        }

        private void check() throws InterruptedIOException {
            if (canceled || Thread.currentThread().isInterrupted())
                throw new InterruptedIOException("Original request canceled");
        }

        @Override public void cleanup() {
            InputStream input = stream;
            stream = null;
            if (input != null) try { input.close(); } catch (IOException ignored) { }
            HttpURLConnection active = connection;
            connection = null;
            if (active != null) active.disconnect();
        }

        @Override public void cancel() {
            canceled = true;
            PawchiveMediaRequests.wakeWaiters();
            HttpURLConnection active = connection;
            if (active != null) active.disconnect();
        }

        @NonNull @Override public Class<InputStream> getDataClass() { return InputStream.class; }
        @NonNull @Override public DataSource getDataSource() { return DataSource.REMOTE; }
    }
}
