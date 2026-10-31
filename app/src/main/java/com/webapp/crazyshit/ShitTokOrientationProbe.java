package com.webapp.crazyshit;

import android.content.Context;
import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.common.DataReader;
import androidx.media3.common.Format;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSourceInputStream;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;
import androidx.media3.exoplayer.dash.manifest.AdaptationSet;
import androidx.media3.exoplayer.dash.manifest.DashManifest;
import androidx.media3.exoplayer.dash.manifest.DashManifestParser;
import androidx.media3.exoplayer.dash.manifest.Representation;
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist;
import androidx.media3.exoplayer.hls.playlist.HlsPlaylist;
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser;
import androidx.media3.extractor.DefaultExtractorInput;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.TrackOutput;
import androidx.media3.extractor.mp4.Mp4Extractor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Bounded metadata extraction only. No player, renderer, codec, or sample queue is created. */
@UnstableApi
final class ShitTokOrientationProbe implements ShitTokOrientationAdmission.Probe {
    static final long MAX_BYTES = 768 * 1024;
    static final long MAX_MILLIS = 1800;
    private static final Semaphore PROBE_SLOTS = new Semaphore(3);
    private final Context context;

    ShitTokOrientationProbe(Context context) {
        Context app = context.getApplicationContext();
        this.context = app == null ? context : app;
    }

    @Override public float inspect(CrazyShitRepository.StreamInfo stream, BooleanSupplier allowed)
            throws Exception {
        if (!PROBE_SLOTS.tryAcquire()) throw new ShitTokOrientationAdmission.Deferred();
        try {
            Budget budget = new Budget(allowed);
            budget.check();
            DataSource.Factory factory = ShitTokMediaSources.factory(context, stream, budget);
            String path = Uri.parse(stream.mediaUrl).getPath();
            path = path == null ? "" : path.toLowerCase(Locale.US);
            if (path.endsWith(".m3u8")) return hls(factory, stream.mediaUrl, budget);
            if (path.endsWith(".mpd")) return dash(factory, stream.mediaUrl, budget);
            return progressive(factory, stream.mediaUrl, budget);
        } finally {
            PROBE_SLOTS.release();
        }
    }

    private static float hls(DataSource.Factory factory, String url, Budget budget) throws IOException {
        try (DataSourceInputStream input = new DataSourceInputStream(
                new BoundedSource(factory.createDataSource(), budget), new DataSpec(Uri.parse(url)))) {
            HlsPlaylist playlist = new HlsPlaylistParser().parse(Uri.parse(url), input);
            // A media playlist has no reliable display dimensions by itself.
            if (!(playlist instanceof HlsMultivariantPlaylist)) return 0f;
            HlsMultivariantPlaylist master = (HlsMultivariantPlaylist) playlist;
            ArrayList<Float> aspects = new ArrayList<>();
            for (HlsMultivariantPlaylist.Variant variant : master.variants) {
                budget.check();
                aspects.add(aspect(variant.format));
            }
            // Alternative video renditions must meet the same rule.
            for (HlsMultivariantPlaylist.Rendition video : master.videos) {
                aspects.add(aspect(video.format));
            }
            return ShitTokOrientation.conservativeAspect(aspects);
        }
    }

    private static float dash(DataSource.Factory factory, String url, Budget budget) throws IOException {
        try (DataSourceInputStream input = new DataSourceInputStream(
                new BoundedSource(factory.createDataSource(), budget), new DataSpec(Uri.parse(url)))) {
            DashManifest manifest = new DashManifestParser().parse(Uri.parse(url), input);
            ArrayList<Float> aspects = new ArrayList<>();
            for (int period = 0; period < manifest.getPeriodCount(); period++) {
                boolean hasVideo = false;
                for (AdaptationSet adaptation : manifest.getPeriod(period).adaptationSets) {
                    budget.check();
                    if (adaptation.type != C.TRACK_TYPE_VIDEO) continue;
                    if (adaptation.representations.isEmpty()) return 0f;
                    hasVideo = true;
                    for (Representation video : adaptation.representations) {
                        aspects.add(aspect(video.format));
                    }
                }
                if (!hasVideo) return 0f;
            }
            return ShitTokOrientation.conservativeAspect(aspects);
        }
    }

    private static float progressive(DataSource.Factory factory, String url, Budget budget)
            throws IOException {
        DataSource source = new BoundedSource(factory.createDataSource(), budget);
        Extractor selected = null;
        Extractor[] extractors = null;
        try {
            long length = source.open(new DataSpec(Uri.parse(url)));
            DefaultExtractorInput input = new DefaultExtractorInput(source, 0L, length);
            extractors = new DefaultExtractorsFactory()
                    .setMp4ExtractorFlags(Mp4Extractor.FLAG_OMIT_TRACK_SAMPLE_TABLE)
                    .createExtractors(Uri.parse(url), source.getResponseHeaders());
            for (Extractor extractor : extractors) {
                budget.check();
                try {
                    if (extractor.sniff(input)) { selected = extractor; break; }
                } catch (java.io.EOFException ignored) {
                    // A short unsupported resource remains unknown.
                } finally { input.resetPeekPosition(); }
            }
            if (selected == null) return 0f;
            Dimensions output = new Dimensions();
            selected.init(output);
            PositionHolder seek = new PositionHolder();
            int seeks = 0;
            for (;;) {
                budget.check();
                int result = selected.read(input, seek);
                if (output.complete) return output.aspect();
                if (result == Extractor.RESULT_END_OF_INPUT) return output.aspect();
                if (result == Extractor.RESULT_SEEK) {
                    if (++seeks > 8 || seek.position < 0) return 0f;
                    source.close();
                    long available = source.open(new DataSpec.Builder().setUri(url)
                            .setPosition(seek.position).build());
                    input = new DefaultExtractorInput(source, seek.position,
                            available == C.LENGTH_UNSET ? C.LENGTH_UNSET : seek.position + available);
                }
            }
        } finally {
            try { source.close(); } finally {
                if (extractors != null) for (Extractor extractor : extractors) extractor.release();
            }
        }
    }

    private static float aspect(Format format) {
        return ShitTokOrientation.effectiveAspect(format.width, format.height,
                format.pixelWidthHeightRatio, format.rotationDegrees);
    }

    private static final class Dimensions implements ExtractorOutput {
        final Map<Integer, Float> video = new HashMap<>();
        boolean complete;
        @Override public TrackOutput track(int id, int type) {
            if (type == C.TRACK_TYPE_VIDEO) video.put(id, 0f);
            return new TrackOutput() {
                @Override public void format(Format format) {
                    if (type == C.TRACK_TYPE_VIDEO) video.put(id, ShitTokOrientationProbe.aspect(format));
                }
                @Override public int sampleData(DataReader input, int length, boolean allowEnd, int part)
                        throws IOException {
                    byte[] scratch = new byte[Math.min(length, 4096)];
                    return input.read(scratch, 0, scratch.length);
                }
                @Override public void sampleData(ParsableByteArray data, int length, int part) {
                    data.skipBytes(length);
                }
                @Override public void sampleMetadata(long time, int flags, int size, int offset,
                                                       TrackOutput.CryptoData crypto) {}
            };
        }
        @Override public void endTracks() { complete = true; }
        @Override public void seekMap(SeekMap seekMap) {}
        float aspect() {
            return ShitTokOrientation.conservativeAspect(video.values());
        }
    }

    /** Counts read bytes and HTTP's ignored-Range skip bytes, with one budget across every seek. */
    static final class Budget implements TransferListener {
        final BooleanSupplier allowed;
        final LongSupplier nanoClock;
        final long deadline;
        long readBytes;
        long networkBytes;
        long chargedBytes;
        Budget(BooleanSupplier allowed) { this(allowed, System::nanoTime); }
        Budget(BooleanSupplier allowed, LongSupplier nanoClock) {
            this.allowed = allowed;
            this.nanoClock = nanoClock;
            deadline = nanoClock.getAsLong() + MAX_MILLIS * 1_000_000L;
        }
        void check() throws IOException {
            if (Thread.currentThread().isInterrupted() || !allowed.getAsBoolean())
                throw new DeferredIOException();
            if (nanoClock.getAsLong() >= deadline) throw new MetadataBudgetIOException("deadline");
            if (chargedBytes >= MAX_BYTES) throw new MetadataBudgetIOException("bytes");
        }
        void readCompleted(int count, long networkBeforeRead) throws IOException {
            if (count > 0) {
                readBytes += count;
                // Upstream transfer callbacks already charged network bytes. Charge
                // only cache-delivered bytes here, rather than halving the ceiling.
                chargedBytes += Math.max(0L, count - (networkBytes - networkBeforeRead));
            }
            check();
        }
        @Override public void onTransferInitializing(DataSource source, DataSpec spec, boolean network) {}
        @Override public void onTransferStart(DataSource source, DataSpec spec, boolean network) {}
        @Override public void onBytesTransferred(DataSource source, DataSpec spec, boolean network, int bytes) {
            networkBytes += bytes;
            chargedBytes += bytes;
            try { check(); } catch (IOException stopped) { throw new ProbeStopped(stopped); }
        }
        @Override public void onTransferEnd(DataSource source, DataSpec spec, boolean network) {}
    }

    private static final class BoundedSource implements DataSource {
        final DataSource delegate;
        final Budget budget;
        BoundedSource(DataSource delegate, Budget budget) { this.delegate = delegate; this.budget = budget; }
        @Override public void addTransferListener(TransferListener listener) { delegate.addTransferListener(listener); }
        @Override public long open(DataSpec spec) throws IOException { budget.check(); return delegate.open(spec); }
        @Override public int read(byte[] target, int offset, int length) throws IOException {
            budget.check();
            long networkBeforeRead = budget.networkBytes;
            int count = delegate.read(target, offset,
                    (int) Math.min(length, MAX_BYTES - budget.chargedBytes));
            budget.readCompleted(count, networkBeforeRead);
            return count;
        }
        @Override public Uri getUri() { return delegate.getUri(); }
        @Override public Map<String, List<String>> getResponseHeaders() { return delegate.getResponseHeaders(); }
        @Override public void close() throws IOException { delegate.close(); }
    }

    static final class DeferredIOException extends IOException {}
    static final class MetadataBudgetIOException extends IOException {
        final String limit;
        MetadataBudgetIOException(String limit) { super("Orientation metadata budget reached"); this.limit = limit; }
    }
    private static final class ProbeStopped extends RuntimeException {
        ProbeStopped(IOException cause) { super(cause); }
    }
}
