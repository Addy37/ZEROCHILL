package com.webapp.crazyshit;

import android.app.*;
import android.graphics.*;
import androidx.core.graphics.ColorUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

/** White is the SDR upper bound: blur/refraction cannot make that backdrop brighter. */
@RunWith(RobolectricTestRunner.class)
@Config(application=Application.class,sdk=35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LiquidGlassContrastTest {
    @Test public void selectedAndInactiveInkReadOverBrightDarkAndColoredMedia() {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        LiquidGlassDrawable body=new LiquidGlassDrawable(activity,LiquidGlassDrawable.Material.NAVIGATION);
        LiquidGlassDrawable lens=new LiquidGlassDrawable(activity,LiquidGlassDrawable.Material.LENS);
        body.setBounds(0,0,400,64);lens.setBounds(8,4,98,58);
        for(int background:new int[]{Color.WHITE,Color.BLACK,0xFF557B91,0xFF777777}){
            Bitmap pixels=Bitmap.createBitmap(400,64,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(pixels);canvas.drawColor(background);body.draw(canvas);lens.draw(canvas);
            double selected=100,unselected=100;
            for(int y=12;y<=46;y++)for(int x=38;x<=68;x++){
                selected=Math.min(selected,ColorUtils.calculateContrast(activity.getColor(R.color.zc_glass_selected_ink),pixels.getPixel(x,y)));
                unselected=Math.min(unselected,ColorUtils.calculateContrast(activity.getColor(R.color.zc_glass_unselected_ink),pixels.getPixel(x+110,y)));
            }
            assertTrue("selected ink over "+Integer.toHexString(background)+": "+selected,selected>=4.5);
            assertTrue("inactive ink over "+Integer.toHexString(background)+": "+unselected,unselected>=4.5);
            pixels.recycle();
        }
    }
}
