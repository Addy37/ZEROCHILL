package com.webapp.crazyshit;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Local mascot cartoons. Reuses the production face layers, with drawn bodies and props. */
final class WizardMascotArt {
    static final int SWIPE=0, SEARCH=1, GALLERY=2, STACK=3, SKATE=4, POPCORN=5;
    private final Drawable[] head = new Drawable[7];
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final int accent;
    private final int dark = 0xff080d12;
    private final int soft;
    WizardMascotArt(Context context, int accent) {
        this.accent=accent;
        soft=(accent & 0x00ffffff)|0x44000000;
        int[] ids={R.drawable.zc_devil_horns,R.drawable.zc_devil_face,R.drawable.zc_devil_x_eye,
                R.drawable.zc_devil_angry_eye,R.drawable.zc_devil_teeth,R.drawable.zc_devil_tongue,R.drawable.zc_devil_outline};
        for(int i=0;i<ids.length;i++) {
            head[i]=context.getDrawable(ids[i]).mutate();
            head[i].setBounds(0,0,108,108);
        }
        float[] from={5,9,12}, cyan={8,146,208}, to={Color.red(accent),Color.green(accent),Color.blue(accent)};
        float[] matrix=new float[20];
        for(int i=0;i<3;i++){
            float scale=(to[i]-from[i])/(cyan[i]-from[i]);
            matrix[i*5+i]=scale;matrix[i*5+4]=from[i]*(1-scale);
        }
        matrix[18]=1;
        head[0].setColorFilter(new ColorMatrixColorFilter(matrix));
        for(int i:new int[]{2,3,5,6})head[i].setColorFilter(accent,PorterDuff.Mode.SRC_IN);
    }
    void draw(Canvas c,float x,float y,float size,int pose,float t) {
        if(size<=0)return;
        c.save();
        c.translate(x,y);c.scale(size/100f,size/100f);
        float bob=(float)Math.sin(t*Math.PI*4)*2f;
        float action=(float)Math.sin(t*Math.PI*2);
        c.translate(0,bob);
        if(pose==SKATE)c.rotate(-9+action*5,50,65);
        // Tail, soft halo and compact body.
        oval(c,17,19,85,83,soft);
        path.reset();path.moveTo(29,69);path.cubicTo(2,73,1,44,15,42);
        strokePath(c,accent,2.3f);
        triangle(c,8,36,21,40,13,50,accent);
        body(c,pose);
        if(pose==POPCORN) {
            oval(c,8,75,93,98,0xff14232c);
            oval(c,17,74,80,94,soft);
            limb(c,35,72,20,86);limb(c,63,72,80,86);
            oval(c,12,81,35,93,dark);oval(c,70,81,92,93,dark);
        } else if(pose==SKATE) {
            limb(c,43,72,33,85);limb(c,61,71,71,85);
            round(c,14,84,89,90,3,accent);
            circle(c,28,93,4,Color.WHITE);circle(c,74,93,4,Color.WHITE);
            line(c,4,82,23,82,accent,1.6f);line(c,1,92,16,92,soft,2);
        } else {
            limb(c,41,72,35,87);limb(c,60,72,67,87);
            oval(c,25,84,43,93,dark);oval(c,59,84,77,93,dark);
        }
        // Authentic face layers, centered and scaled up from their padded viewport.
        c.save();c.translate(50,41);c.rotate(action*5);c.translate(-50,-41);
        c.translate(-13,-15);c.scale(1.16f,1.16f);
        head[0].draw(c);head[1].draw(c);head[6].draw(c);head[2].draw(c);
        if(t>.46f&&t<.51f)line(c,61,52,73,52,accent,3);else head[3].draw(c);
        head[4].draw(c);
        c.save();c.translate(0,(float)Math.sin(t*Math.PI*6)*1.2f);head[5].draw(c);c.restore();
        c.restore();
        // Distinct readable prop and exaggerated action per pose.
        switch(pose){
            case SWIPE:
                limb(c,36,63,26,75);limb(c,62,65,76,57-action*8);
                c.save();c.rotate(-12,74,68);
                round(c,64,46,87,80,5,accent);round(c,66,48,85,78,4,0xff182a35);
                line(c,70,72,81,58,Color.WHITE,2);
                triangle(c,76,57,83,55,82,64,Color.WHITE);c.restore();
                line(c,88,60,95,48,accent,2);line(c,88,50,91,42,accent,1.5f);
                break;
            case SEARCH:
                float lx=77+action*6,ly=43+action*5;
                limb(c,63,63,lx-6,ly+14);limb(c,35,65,22,75);
                line(c,lx+6,ly+7,lx+16,ly+20,accent,5);
                circle(c,lx,ly,13,accent);circle(c,lx,ly,10,0xff294657);
                line(c,lx-6,ly-5,lx+1,ly-8,Color.WHITE,2);
                break;
            case GALLERY:
                limb(c,33,60,15,73-action*6);limb(c,65,60,85,73+action*6);
                round(c,7,73,93,89,4,0xff1b2d39);
                for(int i=0;i<3;i++){
                    round(c,10+i*28,76,33+i*28,86,2,accent);
                    circle(c,21+i*28,81,3,dark);
                }
                circle(c,16,72-action*6,5,accent);circle(c,84,72+action*6,5,accent);
                break;
            case STACK:
                limb(c,34,63,22,74);limb(c,64,63,79,57-action*7);
                for(int i=0;i<3;i++){
                    float h=59-i*7-((i==2)?Math.abs(action)*13:0);
                    mediaCard(c,59+i*4,h,24,19);
                }
                round(c,50,75,91,92,4,accent);round(c,52,77,89,90,3,0xff18313e);
                round(c,64,80,78,87,2,accent);
                break;
            case SKATE:
                limb(c,34,64,15,55);limb(c,63,63,85,52);
                line(c,7,44,17,44,accent,1.5f);line(c,4,51,12,51,soft,2);
                break;
            default:
                limb(c,33,62,23,79);limb(c,65,62,71,42+Math.abs(action)*19);
                round(c,54,63,82,91,4,0xffe6eff5);
                for(int i=0;i<3;i++)round(c,57+i*9,65,61+i*9,89,1,accent);
                for(int i=0;i<7;i++)circle(c,56+(i%4)*7,62-(i/4)*4,3,0xffffe4a1);
                circle(c,71,49-Math.abs(action)*8,3.2f,0xffffe4a1);
                break;
        }
        c.restore();
    }
    private void body(Canvas c,int pose){oval(c,28,51,73,84,accent);oval(c,30,53,71,82,dark);}
    private void limb(Canvas c,float x,float y,float xx,float yy){line(c,x,y,xx,yy,accent,8);line(c,x,y,xx,yy,dark,5);circle(c,xx,yy,4,accent);circle(c,xx,yy,2.7f,dark);}
    private void mediaCard(Canvas c,float x,float y,float w,float h){round(c,x,y,x+w,y+h,3,accent);round(c,x+1,y+1,x+w-1,y+h-1,2,0xff25394a);triangle(c,x+w*.4f,y+h*.24f,x+w*.4f,y+h*.76f,x+w*.73f,y+h*.5f,Color.WHITE);}
    private void oval(Canvas c,float l,float t,float r,float b,int color){p.setStyle(Paint.Style.FILL);p.setColor(color);rect.set(l,t,r,b);c.drawOval(rect,p);}
    private void round(Canvas c,float l,float t,float r,float b,float radius,int color){p.setStyle(Paint.Style.FILL);p.setColor(color);rect.set(l,t,r,b);c.drawRoundRect(rect,radius,radius,p);}
    private void circle(Canvas c,float x,float y,float radius,int color){p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawCircle(x,y,radius,p);}
    private void triangle(Canvas c,float x,float y,float x2,float y2,float x3,float y3,int color){path.reset();path.moveTo(x,y);path.lineTo(x2,y2);path.lineTo(x3,y3);path.close();p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawPath(path,p);}
    private void strokePath(Canvas c,int color,float width){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);p.setStrokeCap(Paint.Cap.ROUND);p.setColor(color);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);}
    private void line(Canvas c,float x,float y,float xx,float yy,int color,float width){p.setStyle(Paint.Style.FILL);p.setStrokeWidth(width);p.setStrokeCap(Paint.Cap.ROUND);p.setColor(color);c.drawLine(x,y,xx,yy,p);}

    /** Finite welcome animation. Static pose for reduced motion and when hidden. */
    static final class Welcome extends View {
        private final WizardMascotArt art;
        private ValueAnimator animator;
        private float phase=.7f;
        Welcome(Context context,int accent){super(context);art=new WizardMascotArt(context,accent);setContentDescription("ZEROCHILL mascot enjoying popcorn");}
        @Override protected void onAttachedToWindow(){super.onAttachedToWindow();start();}
        @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility==VISIBLE&&isAttachedToWindow())start();else stop();}
        private void start(){if(animator!=null||!ZeroChillMotion.animationsEnabled(getContext()))return;animator=ValueAnimator.ofFloat(0,1);animator.setDuration(2800);animator.setRepeatCount(1);animator.addUpdateListener(a->{phase=(float)a.getAnimatedValue();invalidate();});animator.start();}
        private void stop(){if(animator!=null)animator.cancel();animator=null;phase=.7f;invalidate();}
        @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float s=Math.min(getWidth(),getHeight());art.draw(c,(getWidth()-s)/2,(getHeight()-s)/2,s,POPCORN,phase);}
    }
}
