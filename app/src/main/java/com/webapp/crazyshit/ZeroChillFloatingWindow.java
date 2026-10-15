package com.webapp.crazyshit;

import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import java.util.function.Consumer;
import androidx.annotation.RequiresApi;

/** Native cross-window backdrop for bounded dialogs. The compositor owns capture and blur. */
final class ZeroChillFloatingWindow {
    static void install(Window window) {
        if (window == null) return;
        LiquidGlassDrawable material = new LiquidGlassDrawable(window.getContext(), LiquidGlassDrawable.Material.FLOATING);
        window.setBackgroundDrawable(material);
        if (Build.VERSION.SDK_INT >= 31) Api31.install(window, material);
    }
    @RequiresApi(31) private static final class Api31 {
        static void install(Window window, LiquidGlassDrawable material) {
            View decor = window.getDecorView();
            WindowManager manager = window.getWindowManager();
            Consumer<Boolean> blur = enabled -> {
                material.setBackdropAvailable(enabled);
                window.setBackgroundBlurRadius(enabled ? BrowseUi.dp(window.getContext(), 24) : 0);
            };
            decor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) {
                    manager.addCrossWindowBlurEnabledListener(window.getContext().getMainExecutor(), blur);
                }
                @Override public void onViewDetachedFromWindow(View v) {
                    manager.removeCrossWindowBlurEnabledListener(blur);
                }
            });
        }
    }
}
