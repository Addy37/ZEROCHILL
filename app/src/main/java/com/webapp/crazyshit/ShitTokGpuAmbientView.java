package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;
import android.os.SystemClock;
import android.view.TextureView;
import android.view.View;

/**
 * Hardware-only ambient reflection of the existing Media3 TextureView.
 *
 * The sharp player retains its original surface/decoder and sizing. The
 * background records a GPU-backed draw of that same texture at quarter size
 * into a RenderNode, then uses the platform's GPU RenderEffect blur.
 * No TextureView.getBitmap(), extra video decoder, CPU pixel copies, or
 * frame-to-frame image crossfades. If a device cannot draw the reflection,
 * the static thumbnail remains underneath this optional layer.
 */
final class ShitTokGpuAmbientView extends View {
    private TextureView source;
    private RenderNode node;
    private boolean enabled;
    private boolean failed;
    private int slowFrames;
    private long previousDrawNs;

    ShitTokGpuAmbientView(Context context) {
        super(context);
        setTag("shittok_horizontal_live_ambient");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setClickable(false);
        setFocusable(false);
        setAlpha(.52f);
        setVisibility(GONE);
    }

    static boolean supported(int api, boolean hardware, boolean textureAvailable) {
        return api >= 31 && hardware && textureAvailable;
    }

    boolean hasFailed() { return failed; }

    void bind(TextureView view) {
        if (source == view) return;
        stop();
        source = view;
        failed = false;
        slowFrames = 0;
        previousDrawNs = 0L;
    }

    void setMotionEnabled(boolean requested) {
        boolean canRender = requested && !failed && source != null
                && supported(Build.VERSION.SDK_INT, isHardwareAccelerated(),
                        source.isAvailable() && source.isAttachedToWindow());
        if (!canRender) {
            stop();
            return;
        }
        if (!enabled) {
            enabled = true;
            setVisibility(VISIBLE);
            postInvalidateOnAnimation();
        }
    }

    void stop() {
        enabled = false;
        previousDrawNs = 0L;
        if (getVisibility() != GONE) setVisibility(GONE);
        if (node != null) node.discardDisplayList();
    }

    void reset() {
        stop();
        source = null;
        failed = false;
        slowFrames = 0;
    }

    private void fail() {
        failed = true;
        stop();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!enabled || !canvas.isHardwareAccelerated() || source == null
                || !source.isAvailable() || !source.isAttachedToWindow()) return;
        int viewWidth = getWidth(), viewHeight = getHeight();
        int sourceWidth = source.getWidth(), sourceHeight = source.getHeight();
        if (viewWidth <= 0 || viewHeight <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
            postInvalidateOnAnimation();
            return;
        }
        long started = SystemClock.elapsedRealtimeNanos();
        long gap = previousDrawNs == 0 ? 0 : started - previousDrawNs;
        previousDrawNs = started;
        try {
            int renderWidth = Math.max(1, viewWidth / ShitTokAmbientSampling.DOWNSCALE);
            int renderHeight = Math.max(1, viewHeight / ShitTokAmbientSampling.DOWNSCALE);
            if (node == null) {
                node = new RenderNode("shittok_live_ambient");
                node.setRenderEffect(RenderEffect.createBlurEffect(
                        ShitTokAmbientSampling.BLUR_RADIUS_LOW_RES,
                        ShitTokAmbientSampling.BLUR_RADIUS_LOW_RES, Shader.TileMode.CLAMP));
            }
            node.setPosition(0, 0, renderWidth, renderHeight);

            RecordingCanvas recording = node.beginRecording(renderWidth, renderHeight);
            try {
                // Center-crop the same sharp texture for the unused portrait
                // background. The foreground PlayerView remains RESIZE_MODE_FIT.
                float scale = Math.max((float) renderWidth / sourceWidth,
                        (float) renderHeight / sourceHeight);
                recording.save();
                recording.translate((renderWidth - sourceWidth * scale) / 2f,
                        (renderHeight - sourceHeight * scale) / 2f);
                recording.scale(scale, scale);
                source.draw(recording);
                recording.restore();
            } finally {
                node.endRecording();
            }
            canvas.save();
            canvas.scale((float) viewWidth / renderWidth, (float) viewHeight / renderHeight);
            canvas.drawRenderNode(node);
            canvas.restore();
        } catch (RuntimeException | OutOfMemoryError ex) {
            fail();
            return;
        }
        long elapsed = SystemClock.elapsedRealtimeNanos() - started;
        if (ShitTokAmbientSampling.shouldDisable(elapsed, gap, slowFrames)) {
            fail();
            return;
        }
        slowFrames = elapsed > ShitTokAmbientSampling.MAX_FRAME_RECORD_NS
                || gap > ShitTokAmbientSampling.MISSED_VSYNC_NS
                ? slowFrames + 1 : 0;
        // Schedule on display vsync, not an arbitrary 250ms timer. The texture
        // layer always contains the latest video frame when the UI composites it.
        if (enabled) postInvalidateOnAnimation();
    }

    @Override protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }
}
