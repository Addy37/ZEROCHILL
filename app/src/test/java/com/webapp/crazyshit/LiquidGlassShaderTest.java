package com.webapp.crazyshit;

import android.app.Application;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

/** Native AGSL compilation and cache behavior. GPU composition needs the hardware probe. */
@RunWith(RobolectricTestRunner.class)
@Config(application=Application.class, sdk=35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LiquidGlassShaderTest {
    @Test public void compilesProductionAgslAndCachesStableEffects() {
        LiquidGlassShader glass = new LiquidGlassShader();
        RectF lens = new RectF(100, 20, 210, 100);
        RenderEffect effect = glass.update(400, 120, 0, 0, lens, 1, 0);
        assertSame("moving content reuses effect",effect,glass.update(400,120,0,0,lens,1,0));
        RenderEffect pressed = glass.update(400,120,0,0,lens,1,.3f);
        assertNotSame("pressure snapshots new shader uniforms",effect,pressed);
        assertSame(pressed,glass.update(400,120,0,0,lens,1,.3f));
        lens.offset(30,0);
        assertNotSame("selection movement snapshots new geometry",pressed,glass.update(400,120,0,0,lens,1,.3f));
        assertNotNull("header has no selected lens",glass.update(400,64,0,30,new RectF(),1,0));
    }
}
