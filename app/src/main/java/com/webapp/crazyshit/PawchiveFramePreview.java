package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.effect.Presentation;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.inspector.frame.FrameExtractor;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/** Uses the playback extractor with the gallery's paced, byte-capped random access. */
@UnstableApi
final class PawchiveFramePreview {
    private PawchiveFramePreview() { }

    static Bitmap extract(Context context, String url, BunkrPreviewDataSource source, long timeoutMs)
            throws Exception {
        Uri uri = Uri.parse(url);
        DefaultMediaSourceFactory factory = new DefaultMediaSourceFactory(context)
                .setDataSourceFactory(() -> new BoundedSource(uri, source))
                .setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy(0));
        try (FrameExtractor extractor = new FrameExtractor.Builder(context, MediaItem.fromUri(uri))
                .setMediaSourceFactory(factory)
                .setSeekParameters(SeekParameters.CLOSEST_SYNC)
                .setEffects(Collections.singletonList(Presentation.createForHeight(BunkrMediaPreview.SIZE)))
                .build()) {
            ListenableFuture<FrameExtractor.Frame> frame = extractor.getFrame(1000L);
            try {
                return frame.get(timeoutMs, TimeUnit.MILLISECONDS).bitmap;
            } finally {
                if (!frame.isDone()) frame.cancel(true);
            }
        }
    }

    static final class BoundedSource extends BaseDataSource {
        private final Uri uri;
        private final BunkrPreviewDataSource source;
        private long position;
        private long remaining;
        private boolean opened;

        BoundedSource(Uri uri, BunkrPreviewDataSource source) {
            super(true);
            this.uri = uri;
            this.source = source;
        }

        @Override public long open(DataSpec spec) throws IOException {
            transferInitializing(spec);
            synchronized (source) {
                if (source.getSize() < 0) source.readAt(0, new byte[1], 0, 1);
                long available = source.getSize() - spec.position;
                if (available < 0) throw new IOException("Preview position outside media");
                remaining = spec.length == C.LENGTH_UNSET ? available : Math.min(available, spec.length);
            }
            position = spec.position;
            opened = true;
            transferStarted(spec);
            return remaining;
        }

        @Override public int read(byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) return 0;
            if (remaining == 0) return C.RESULT_END_OF_INPUT;
            int count;
            synchronized (source) {
                count = source.readAt(position, buffer, offset, (int) Math.min(length, remaining));
            }
            if (count < 0) return C.RESULT_END_OF_INPUT;
            position += count;
            remaining -= count;
            bytesTransferred(count);
            return count;
        }

        @Override public Uri getUri() { return opened ? uri : null; }

        @Override public void close() {
            if (!opened) return;
            opened = false;
            transferEnded();
        }
    }
}
