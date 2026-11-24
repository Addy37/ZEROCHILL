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

    static Model model(Context context, String identity, String delivery) {
        if (!PawchiveRepository.isDeliveryFor(identity, delivery))
            throw new IllegalArgumentException("Invalid Pawchive original");
        Glide glide = Glide.get(context.getApplicationContext());
        synchronized (REGISTERED) {
            if (!REGISTERED.containsKey(glide)) {
                glide.getRegistry().append(Model.class, InputStream.class, new Factory());
                REGISTERED.put(glide, true);
            }
        }
        return new Model(identity, delivery);
    }

    static final class Model {
        final String identity, delivery;
        Model(String identity, String delivery) {
            this.identity = identity;
            this.delivery = delivery;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Model)) return false;
            Model that = (Model) other;
            return identity.equals(that.identity) && delivery.equals(that.delivery);
        }
        @Override public int hashCode() { return Objects.hash(identity, delivery); }
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
        return new LoadData<>(new ObjectKey("pawchive-original-v1:" + model.identity), new Fetcher(model));
    }

    private static final class Factory implements ModelLoaderFactory<Model, InputStream> {
        @NonNull @Override public ModelLoader<Model, InputStream> build(@NonNull MultiModelLoaderFactory factory) {
            return new PawchiveOriginalImageLoader();
        }
        @Override public void teardown() { }
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
                    current.setConnectTimeout(15_000);
                    current.setReadTimeout(20_000);
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
                    stream = current.getInputStream();
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
