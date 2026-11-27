package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** Accent-colored curved light ribbons kept behind the splash brand stack. */
public final class SplashBrandLighting extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path ribbon = new Path();
    private int accent = Color.CYAN;

    public SplashBrandLighting(Context context, AttributeSet attrs) { super(context, attrs); }
    void setAccent(int value) { accent = value; invalidate(); }
    int getAccent() { return accent; }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w=getWidth(), h=getHeight();
        if(w<=0 || h<=0)return;
        drawRibbon(c,w,h);
        c.save();c.rotate(180,w/2,h/2);drawRibbon(c,w,h);c.restore();
    }
    private void drawRibbon(Canvas c,float w,float h) {
        // The middle remains pure OLED black; ribbons stay near the edges.
        ribbon.reset();ribbon.moveTo(-.08f*w,.19f*h);
        ribbon.cubicTo(.32f*w,.28f*h,.73f*w,.035f*h,1.08f*w,.025f*h);
        ribbon.lineTo(1.08f*w,.09f*h);
        ribbon.cubicTo(.72f*w,.105f*h,.30f*w,.31f*h,-.08f*w,.22f*h);
        ribbon.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0,0,w,h*.27f,
                new int[]{Color.TRANSPARENT,(accent & 0xffffff)|0x55000000,Color.TRANSPARENT},
                null,Shader.TileMode.CLAMP));
        c.drawPath(ribbon,paint);paint.setShader(null);
        ribbon.reset();ribbon.moveTo(-.08f*w,.19f*h);
        ribbon.cubicTo(.32f*w,.28f*h,.73f*w,.035f*h,1.08f*w,.025f*h);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(2,w*.016f));
        paint.setColor((accent&0xffffff)|0x18000000);c.drawPath(ribbon,paint);
        paint.setStrokeWidth(Math.max(1,w*.006f));paint.setColor((accent&0xffffff)|0x88000000);c.drawPath(ribbon,paint);
        paint.setStrokeWidth(Math.max(1,w*.002f));paint.setColor(accent);c.drawPath(ribbon,paint);
        paint.setStyle(Paint.Style.FILL);
    }
}
