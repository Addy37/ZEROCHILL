package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.Presentation;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.inspector.frame.FrameExtractor;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Main-thread, drag-scoped optional work. At most one extraction plus one latest target. */
@UnstableApi
final class ShitTokScrubPreview {
    interface Extractor {
        ListenableFuture<Bitmap> frame(long positionMs);
        void close();
    }

    static Extractor media3(Context context, String itemUrl, CrazyShitRepository.StreamInfo stream) {
        AtomicBoolean open = new AtomicBoolean(true);
        // Reuse the existing short metadata HTTP budget, never playback's factory/timeouts.
        TransferListener previewTransfers = new TransferListener() {
            @Override public void onTransferInitializing(DataSource s, DataSpec d, boolean n) {}
            @Override public void onTransferStart(DataSource s, DataSpec d, boolean n) {}
            @Override public void onBytesTransferred(DataSource s, DataSpec d, boolean n, int b) {}
            @Override public void onTransferEnd(DataSource s, DataSpec d, boolean n) {}
        };
        DataSource.Factory upstream = ShitTokMediaSources.factory(context, stream, previewTransfers);
        FrameExtractor extractor = new FrameExtractor.Builder(context,
                ShitTokMediaSources.mediaItem(itemUrl, stream))
                .setMediaSourceFactory(new DefaultMediaSourceFactory(context)
                        .setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy(0))
                        .setDataSourceFactory(() -> cancellableSource(upstream.createDataSource(), open)))
                .setSeekParameters(SeekParameters.CLOSEST_SYNC)
                .setEffects(Collections.singletonList(Presentation.createForWidthAndHeight(
                        ShitTokInteractionPolicy.PREVIEW_WIDTH_PX,
                        ShitTokInteractionPolicy.PREVIEW_HEIGHT_PX, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)))
                .build();
        return new Extractor() {
            @Override public ListenableFuture<Bitmap> frame(long positionMs) {
                return Futures.transform(extractor.getFrame(positionMs), frame -> frame.bitmap,
                        MoreExecutors.directExecutor());
            }
            @Override public void close() { open.set(false); extractor.close(); }
        };
    }

    private static DataSource cancellableSource(DataSource source, AtomicBoolean open) {
        return new DataSource() {
            private void checkActive() throws InterruptedIOException {
                if (!open.get()) throw new InterruptedIOException("Scrub preview ended");
            }
            @Override public void addTransferListener(TransferListener listener) { source.addTransferListener(listener); }
            @Override public long open(DataSpec spec) throws IOException { checkActive(); return source.open(spec); }
            @Override public int read(byte[] data, int offset, int length) throws IOException {
                checkActive(); int read = source.read(data, offset, length); checkActive(); return read;
            }
            @Override public Uri getUri() { return source.getUri(); }
            @Override public Map<String, List<String>> getResponseHeaders() { return source.getResponseHeaders(); }
            @Override public void close() throws IOException { source.close(); }
        };
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Consumer<Bitmap> render;
    private final LinkedHashMap<Long, Bitmap> cache = new LinkedHashMap<>(4, .75f, true);
    private final Runnable pump = this::extractLatest;
    private final Runnable timeout = this::fail;
    private Supplier<Extractor> factory;
    private BooleanSupplier valid;
    private Extractor extractor;
    private ListenableFuture<Bitmap> inFlight;
    private String identity, failedIdentity;
    private long epoch, target = -1L, lastStarted;
    private boolean active;

    ShitTokScrubPreview(Consumer<Bitmap> render) { this.render = render; }

    void begin(String identity, Supplier<Extractor> factory, BooleanSupplier valid) {
        end();
        this.identity = identity;
        this.factory = factory;
        this.valid = valid;
        active = true;
        lastStarted = -ShitTokInteractionPolicy.PREVIEW_INTERVAL_MS;
        // No creation or network access until a target is requested during this drag.
    }

    void request(long positionMs) {
        if (!active) return;
        if (!valid.getAsBoolean()) { end(); return; }
        long next = Math.max(0L, positionMs) / 1_000L * 1_000L;
        if (target == next) return;
        target = next;
        render.accept(cache.get(target));
        schedule();
    }

    private void schedule() {
        main.removeCallbacks(pump);
        if (!active || inFlight != null || target < 0 || cache.containsKey(target)
                || identity.equals(failedIdentity)) return;
        main.postDelayed(pump, Math.max(0L, ShitTokInteractionPolicy.PREVIEW_INTERVAL_MS
                - (SystemClock.uptimeMillis() - lastStarted)));
    }

    private void extractLatest() {
        if (!active || inFlight != null) return;
        if (!valid.getAsBoolean()) { end(); return; }
        final long session = epoch, requested = target;
        lastStarted = SystemClock.uptimeMillis();
        try {
            if (extractor == null) extractor = factory.get();
            ListenableFuture<Bitmap> result = extractor.frame(requested);
            inFlight = result;
            main.postDelayed(timeout, ShitTokInteractionPolicy.PREVIEW_TIMEOUT_MS);
            result.addListener(() -> complete(session, requested, result), main::post);
        } catch (Exception | LinkageError failure) { fail(); }
    }

    private void complete(long session, long requested, ListenableFuture<Bitmap> result) {
        if (!active || session != epoch || result != inFlight) return;
        if (!valid.getAsBoolean()) { end(); return; }
        main.removeCallbacks(timeout);
        inFlight = null;
        try {
            Bitmap bitmap = result.get();
            if (bitmap == null) { fail(); return; }
            cache.put(requested, bitmap);
            while (cache.size() > ShitTokInteractionPolicy.PREVIEW_CACHE_SIZE)
                cache.remove(cache.keySet().iterator().next());
            if (requested == target) render.accept(bitmap);
            schedule();
        } catch (Exception failure) { fail(); }
    }

    private void fail() {
        failedIdentity = identity;
        release();
        // Keep the drag and its timestamp alive. No retries for this clip/source.
        render.accept(null);
    }

    void end() {
        active = false;
        epoch++;
        release();
        cache.clear();
        target = -1L;
        identity = null;
        factory = null;
        valid = null;
        render.accept(null);
    }

    private void release() {
        main.removeCallbacks(pump);
        main.removeCallbacks(timeout);
        ListenableFuture<Bitmap> old = inFlight;
        inFlight = null;
        if (old != null) old.cancel(true);
        if (extractor != null) {
            try { extractor.close(); } catch (Exception ignored) {}
            extractor = null;
        }
    }
}
