package com.webapp.crazyshit;

import android.app.*;
import android.graphics.Canvas;
import android.view.*;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application=Application.class,sdk=35)
public class LiquidGlassVisibilityTest {
    @Test public void pinchClearDisplayAndHiddenNavigationDoNotCaptureOrCreateEffects() {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root=new FrameLayout(activity);
        View overlay=new View(activity);
        root.addView(overlay);root.layout(0,0,400,800);overlay.layout(10,720,390,784);
        FrostedBackdropRenderer renderer=new FrostedBackdropRenderer();
        int[] captures={0};
        FrostedBackdropRenderer.Drawer drawer=new FrostedBackdropRenderer.Drawer(){
            public void drawContent(Canvas c,View v,long t){captures[0]++;}
            public void drawOverlay(Canvas c,View v,long t){captures[0]++;}
        };
        Canvas hardwareFlag=new Canvas(){@Override public boolean isHardwareAccelerated(){return true;}};
        overlay.setAlpha(0f);
        assertFalse(renderer.draw(root,hardwareFlag,overlay,0,drawer));
        assertEquals(0,captures[0]);
        assertNull(ReflectionHelpers.getField(renderer,"renderState"));
        overlay.setAlpha(1f);overlay.setVisibility(View.GONE);
        assertFalse(renderer.draw(root,hardwareFlag,overlay,0,drawer));
        assertEquals(0,captures[0]);
        assertNull(ReflectionHelpers.getField(renderer,"renderState"));
    }
}
