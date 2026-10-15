package com.webapp.crazyshit;

import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RecordingCanvas;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.RequiresApi;

/** Shared Android 12+ backdrop renderer for floating ZeroChill glass overlays. */
final class FrostedBackdropRenderer {
    interface Drawer {
        void drawContent(Canvas canvas, View overlay, long drawingTime);
        void drawOverlay(Canvas canvas, View overlay, long drawingTime);
    }

    private Api31RenderState renderState;
    private boolean blurDisabled;

    boolean draw(
            ViewGroup layout,
            Canvas canvas,
            View overlay,
            long drawingTime,
            Drawer drawer
    ) {
        if (blurDisabled || Build.VERSION.SDK_INT < 31 || !canvas.isHardwareAccelerated()
                || overlay == null || overlay.getVisibility() != View.VISIBLE
                || overlay.getAlpha() <= 0f || layout.getWindowVisibility() != View.VISIBLE
                || overlay.getWidth() <= 0 || overlay.getHeight() <= 0) {
            return false;
        }

        try {
            if (renderState == null) renderState = new Api31RenderState();
            renderState.draw(layout, canvas, overlay, drawingTime, drawer);
            return true;
        } catch (RuntimeException rendererFailure) {
            android.util.Log.w("ZeroChillGlass", "Backdrop unavailable; using material fallback", rendererFailure);
            blurDisabled = true;
            renderState = null;
            return false;
        }
    }

    void release() {
        if (renderState != null && Build.VERSION.SDK_INT >= 31) renderState.release();
        renderState = null;
    }

    @RequiresApi(31)
    private static final class Api31RenderState {
        private final RenderNode contentNode = new RenderNode("ZeroChill content");
        private final RenderNode blurNode = new RenderNode("ZeroChill backdrop blur");
        private final Path clipPath = new Path();
        private final RectF clipBounds = new RectF();
        private RenderEffect blurEffect;
        private int blurRadius;
        private LiquidGlassShader liquidShader;
        private final RectF lens = new RectF();
        private boolean refractionDisabled;
        private RenderEffect lastRefraction, lastBlur, combinedEffect;

        void release() {
            contentNode.discardDisplayList();
            blurNode.discardDisplayList();
        }

        void draw(
                ViewGroup layout,
                Canvas canvas,
                View overlay,
                long drawingTime,
                Drawer drawer
        ) {
            int width = layout.getWidth();
            int height = layout.getHeight();
            if (width <= 0 || height <= 0) {
                drawer.drawOverlay(canvas, overlay, drawingTime);
                return;
            }

            contentNode.setPosition(0, 0, width, height);
            RecordingCanvas contentCanvas = contentNode.beginRecording(width, height);
            drawer.drawContent(contentCanvas, overlay, drawingTime);
            contentNode.endRecording();
            canvas.drawRenderNode(contentNode);

            float density = layout.getResources().getDisplayMetrics().density;
            boolean navigation = overlay instanceof ZeroChillBottomNavigationView;
            boolean optical = navigation || Boolean.TRUE.equals(overlay.getTag(R.id.zerochill_liquid_surface));
            android.app.ActivityManager manager = (android.app.ActivityManager)
                    layout.getContext().getSystemService(android.content.Context.ACTIVITY_SERVICE);
            boolean light = manager != null && manager.isLowRamDevice();
            int radius = optical ? Math.round((light ? 10f : 6f) * density)
                    : layout.getResources().getDimensionPixelSize(R.dimen.zc_navigation_blur_radius);
            if (blurEffect == null || blurRadius != radius) {
                blurRadius = radius;
                blurEffect = RenderEffect.createBlurEffect(
                        radius,
                        radius,
                        Shader.TileMode.CLAMP
                );
            }

            // Only this small crop is rasterized by the effects. The full content node is
            // a display list, not a full-screen bitmap/blur buffer.
            int padding = Math.max(1, radius * 3 + Math.round(18 * density));
            int sampleLeft = Math.max(0, overlay.getLeft() - padding);
            int sampleTop = Math.max(0, overlay.getTop() - padding);
            int sampleRight = Math.min(width, overlay.getRight() + padding);
            int sampleBottom = Math.min(height, overlay.getBottom() + padding);
            int sampleWidth = sampleRight - sampleLeft;
            int sampleHeight = sampleBottom - sampleTop;

            if (sampleWidth > 0 && sampleHeight > 0) {
                RenderEffect targetEffect = blurEffect;
                if (optical && !light && Build.VERSION.SDK_INT >= 33 && !refractionDisabled) {
                    try {
                        if (liquidShader == null) liquidShader = new LiquidGlassShader();
                        lens.setEmpty();
                        float pressure = 0f;
                        if (navigation) {
                            ZeroChillBottomNavigationView nav = (ZeroChillBottomNavigationView) overlay;
                            nav.copyOpticalLensBounds(lens);
                            pressure = nav.opticalPressure();
                        }
                        RenderEffect refraction = liquidShader.update(overlay.getWidth(), overlay.getHeight(),
                                overlay.getLeft() - sampleLeft, overlay.getTop() - sampleTop,
                                lens, density, pressure);
                        if (lastRefraction != refraction || lastBlur != blurEffect) {
                            combinedEffect = RenderEffect.createChainEffect(refraction, blurEffect);
                            if (lastRefraction == null) android.util.Log.i("ZeroChillGlass",
                                    "tier=refractive crop=" + sampleWidth + "x" + sampleHeight);
                            lastRefraction = refraction; lastBlur = blurEffect;
                        }
                        targetEffect = combinedEffect;
                    } catch (RuntimeException unsupportedShader) {
                        refractionDisabled = true;
                        liquidShader = null;
                        android.util.Log.w("ZeroChillGlass", "Refraction unavailable; using cropped blur", unsupportedShader);
                    }
                }
                blurNode.setRenderEffect(targetEffect);
                blurNode.setPosition(0, 0, sampleWidth, sampleHeight);
                blurNode.setTranslationX(sampleLeft);
                blurNode.setTranslationY(sampleTop);
                RecordingCanvas blurCanvas = blurNode.beginRecording(sampleWidth, sampleHeight);
                blurCanvas.translate(-sampleLeft, -sampleTop);
                blurCanvas.drawRenderNode(contentNode);
                blurNode.endRecording();

                float cornerRadius = optical ? overlay.getHeight() * .5f
                        : layout.getResources().getDimension(R.dimen.zc_radius_pill);
                int checkpoint = canvas.save();
                clipBounds.set(
                        overlay.getLeft(),
                        overlay.getTop(),
                        overlay.getRight(),
                        overlay.getBottom()
                );
                clipPath.rewind();
                clipPath.addRoundRect(
                        clipBounds,
                        cornerRadius,
                        cornerRadius,
                        Path.Direction.CW
                );
                canvas.clipPath(clipPath);
                // ShitTok pinch clears chrome using alpha, not visibility. Fade/skip
                // its sampled backdrop with the actual control, leaving no ghost pane.
                if (overlay.getAlpha() < 1f) canvas.saveLayerAlpha(
                        clipBounds, Math.round(overlay.getAlpha() * 255));
                canvas.drawRenderNode(blurNode);
                canvas.restoreToCount(checkpoint);
            }

            drawer.drawOverlay(canvas, overlay, drawingTime);
        }
    }
}
