package com.webapp.crazyshit;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/**
 * Opportunistic one-at-a-time category warm-up, without a background ExoPlayer.
 * A foreground READY player always wins. Results only seed the existing verified
 * creator pool, so normal blocked/hidden/history/orientation admission still applies.
 */
@UnstableApi
final class ShitTokBackgroundCategoryWarmup {
    static final int MAX_WARM_BYTES = 512 * 1024;
    private static final int READ_BYTES = 8192;
    private static final long CHUNK_PAUSE_MS = 24L;
    private static final int MAX_RESOLVED = 8;
    private static final ContentPreference[] TABS = {
            ContentPreference.ALL, ContentPreference.WOMEN, ContentPreference.MEN,
            ContentPreference.TRANS_WOMEN, ContentPreference.GROUPS
    };

    static final class WarmClip {
        final ShitTokSourceMode sources;
        final ContentPreference preference;
        final NativeContentItem item;
        final CrazyShitRepository.StreamInfo stream;
        WarmClip(ShitTokSourceMode sources, ContentPreference preference, NativeContentItem item,
                 CrazyShitRepository.StreamInfo stream) {
            this.sources = sources;
            this.preference = preference;
            this.item = item;
            this.stream = stream;
        }
    }

    interface Fetcher {
        @Nullable WarmClip fetch(ShitTokSourceMode sources, ContentPreference preference) throws Exception;
        int buffer(WarmClip clip, BooleanSupplier keepWorking) throws Exception;
    }

    /** Reuses the exact category provider used in foreground, never guesses a creator's category. */
    private static final class RemoteFetcher implements Fetcher {
        private final Context app;
        private final ShitTokFilteredInventory.Backend backend;
        RemoteFetcher(Context context) {
            app = context.getApplicationContext();
            backend = ShitTokFilteredInventory.backend(app, new ShitShowTapSource());
        }

        @Override public WarmClip fetch(ShitTokSourceMode sources, ContentPreference preference) throws Exception {
            if (!backend.available(ShitTokFilteredInventory.HAVEN)) return null;
            if (Thread.currentThread().isInterrupted()) return null;
            OnlyHavenRepository.CategoryPage page = backend.creators(preference, 0);
            if (page == null || page.creators == null) return null;
            // At most two creator media lookups; never start an entire catalog crawl.
            for (int n = 0; n < Math.min(2, page.creators.size()); n++) {
                if (Thread.currentThread().isInterrupted()) return null;
                OnlyHavenRepository.Creator creator = page.creators.get(n);
                if (creator == null) continue;
                ShitTokFilteredInventory.Cursor cursor = new ShitTokFilteredInventory.Cursor(creator);
                List<NativeContentItem> clips = backend.media(ShitTokFilteredInventory.HAVEN, cursor);
                if (clips == null) continue;
                for (NativeContentItem item : clips) {
                    if (Thread.currentThread().isInterrupted()) return null;
                    if (item == null || !item.isVideo() || item.url == null || item.url.isEmpty()) continue;
                    // Resolver accepts only the same typed candidate as normal playback.
                    CrazyShitRepository.StreamInfo stream;
                    try { stream = PlayableSourceRouter.resolve(app, item); }
                    catch (Exception invalid) { continue; }
                    if (stream != null && stream.mediaUrl != null && !stream.mediaUrl.isEmpty()) {
                        return new WarmClip(sources, preference, item, stream);
                    }
                }
            }
            return null;
        }

        @Override public int buffer(WarmClip clip, BooleanSupplier keepWorking) throws Exception {
            if (!keepWorking.getAsBoolean() || clip.stream.mediaUrl == null) return 0;
            String lower = clip.stream.mediaUrl.toLowerCase(java.util.Locale.ROOT);
            // HLS/DASH must not be treated as a progressive byte range; foreground handles those.
            if (lower.contains(".m3u8") || lower.contains(".mpd")) return 0;
            // Use the existing cookie/referer-aware Media3 disk cache. Only the first
            // 512 KiB may be requested, and playback never waits for this task.
            TransferListener timeoutProbe = new TransferListener() {
                @Override public void onTransferInitializing(DataSource dataSource, DataSpec dataSpec, boolean isNetwork) { }
                @Override public void onTransferStart(DataSource dataSource, DataSpec dataSpec, boolean isNetwork) { }
                @Override public void onBytesTransferred(DataSource dataSource, DataSpec dataSpec,
                        boolean isNetwork, int bytesTransferred) { }
                @Override public void onTransferEnd(DataSource dataSource, DataSpec dataSpec, boolean isNetwork) { }
            };
            DataSource data = ShitTokMediaSources.factory(app, clip.stream, timeoutProbe).createDataSource();
            int total = 0;
            try {
                DataSpec range = new DataSpec.Builder().setUri(Uri.parse(clip.stream.mediaUrl))
                        .setPosition(0).setLength(MAX_WARM_BYTES).build();
                data.open(range);
                byte[] chunk = new byte[READ_BYTES];
                while (total < MAX_WARM_BYTES && keepWorking.getAsBoolean()) {
                    int read = data.read(chunk, 0, Math.min(chunk.length, MAX_WARM_BYTES - total));
                    if (read == -1) break;
                    total += read;
                    if (!keepWorking.getAsBoolean()) break;
                    Thread.sleep(CHUNK_PAUSE_MS);
                }
            } catch (IOException | InterruptedException ignored) {
                if (ignored instanceof InterruptedException) Thread.currentThread().interrupt();
                // Network-limited providers still keep a resolved link; no foreground failure.
            } finally {
                try { data.close(); } catch (IOException ignored) { }
            }
            return total;
        }
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor io = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1),
            runnable -> {
                Thread thread = new Thread(runnable, "ShitTok-category-warmup");
                thread.setDaemon(true);
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            });
    private final Fetcher fetcher;
    private final Consumer<WarmClip> onWarm;
    private final Set<String> attempted = new HashSet<>();
    private final LinkedHashMap<String, CrazyShitRepository.StreamInfo> resolved =
            new LinkedHashMap<String, CrazyShitRepository.StreamInfo>(8, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, CrazyShitRepository.StreamInfo> eldest) {
                    return size() > MAX_RESOLVED;
                }
            };
    private volatile boolean mayRun;
    private volatile int generation;
    private Future<?> running;
    private boolean closed;

    ShitTokBackgroundCategoryWarmup(Context context, Consumer<WarmClip> onWarm) {
        this(new RemoteFetcher(context), onWarm);
    }

    ShitTokBackgroundCategoryWarmup(Fetcher fetcher, Consumer<WarmClip> onWarm) {
        this.fetcher = fetcher;
        this.onWarm = onWarm;
    }

    /** Called only on main thread. A new task starts only after the current clip is comfortably buffered. */
    void update(ShitTokSourceMode sources, ContentPreference current, boolean healthy) {
        if (closed) return;
        if (!healthy) { pause(); return; }
        mayRun = true;
        if (running != null && !running.isDone()) return;
        ContentPreference next = nextPreference(sources, current, attempted);
        if (next == null) return;
        final String key = sources.key(next);
        final int token = ++generation;
        try {
            running = io.submit(() -> {
                WarmClip clip = null;
                int bytes = 0;
                try {
                    if (mayRun && generation == token) clip = fetcher.fetch(sources, next);
                    if (clip != null && mayRun && generation == token) {
                        final WarmClip candidate = clip;
                        bytes = fetcher.buffer(candidate, () -> mayRun && generation == token
                                && !Thread.currentThread().isInterrupted());
                    }
                } catch (Exception ignored) {
                    // Foreground playback never depends on a speculative warm-up.
                }
                final WarmClip ready = clip;
                final int warmedBytes = bytes;
                ui.post(() -> {
                    if (closed || !mayRun || generation != token) return;
                    attempted.add(key);
                    if (ready != null && ready.item != null && ready.stream != null) {
                        synchronized (resolved) { resolved.put(ready.item.url, ready.stream); }
                        onWarm.accept(ready);
                        ShitTokPoolDiagnostics.record("categoryWarmup",
                                "category=" + key + " bytes=" + warmedBytes);
                    } else {
                        ShitTokPoolDiagnostics.record("categoryWarmupMiss", "category=" + key);
                    }
                });
            });
        } catch (RejectedExecutionException rejected) {
            // The interrupted prior fetch is still exiting; a later UI tick will retry.
        }
    }

    static ContentPreference nextPreference(ShitTokSourceMode sources, ContentPreference active, Set<String> tried) {
        for (ContentPreference candidate : TABS) {
            if (candidate == active || !sources.creatorInventory(candidate)) continue;
            if (!tried.contains(sources.key(candidate))) return candidate;
        }
        return null;
    }

    @Nullable CrazyShitRepository.StreamInfo streamFor(String url) {
        synchronized (resolved) { return resolved.get(url); }
    }

    void pause() {
        mayRun = false;
        generation++;
        if (running != null) running.cancel(true);
        io.purge();
        running = null;
    }

    void close() {
        if (closed) return;
        closed = true;
        pause();
        attempted.clear();
        synchronized (resolved) { resolved.clear(); }
        io.shutdownNow();
    }
}
