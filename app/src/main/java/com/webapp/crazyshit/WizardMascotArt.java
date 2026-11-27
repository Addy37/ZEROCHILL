package com.webapp.crazyshit;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * Full character rig based on the owner-provided round-bodied mascot reference.
 * This is deliberately independent of the minimal launcher/splash insignia.
 * Coordinates follow the reference's proportions; only the pose and props move.
 */
final class WizardMascotArt {
    static final int SWIPE=0, SEARCH=1, GALLERY=2, STACK=3, SKATE=4, POPCORN=5;
    private static final int FACE=0xff0b1016, BODY=0xff121920, FEET=0xff161c25;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final int accent, rim, soft;

    WizardMascotArt(Context context, int accent) {
        this.accent=accent;
        rim=Color.rgb((Color.red(accent)+255)/2,(Color.green(accent)+255)/2,(Color.blue(accent)+255)/2);
        soft=(accent & 0x00ffffff)|0x24000000;
    }

    void draw(Canvas c,float x,float y,float size,int pose,float t) {
        if(size<=0)return;
        c.save();
        c.translate(x,y);c.scale(size/400f,size/400f);
        // Leave space for moving horns, tail and props at all phases.
        c.translate(10,22);c.scale(.94f,.94f);
        float action=(float)Math.sin(t*Math.PI*2);
        float bob=(float)Math.sin(t*Math.PI*4)*5;
        c.translate(0,bob);
        if(pose==SKATE)c.rotate(-6+action*4,200,260);
        else if(pose==SWIPE)c.rotate(action*3,200,240);

        if(pose==POPCORN){
            oval(c,49,252,350,342,0xff1c2c35);
            oval(c,64,264,335,337,soft);
        }
        if(pose==SKATE){
            round(c,65,324,341,340,8,accent);
            circle(c,108,348,9,Color.WHITE);circle(c,294,348,9,Color.WHITE);
            line(c,10,331,51,331,accent,4);line(c,16,345,67,345,soft,5);
        }

        // Rounded body and feet, matching the supplied character silhouette.
        path.reset();path.moveTo(105,269);
        path.cubicTo(31,270,29,235,45,180+action*8);
        strokePath(c,accent,8);
        triangle(c,30,186+action*8,52,148+action*8,72,189+action*8,accent);
        outlinedOval(c,105,205,292,326,BODY,3);
        foot(c,126,308,pose==SKATE?-7:0);
        foot(c,271,308,pose==SKATE?7:0);

        // Head is a separate articulated layer, not the existing insignia.
        c.save();c.rotate(action*2.5f,199,156);
        head(c,t);
        c.restore();

        // The hands are large, filled oval mitts like the reference. No stick limbs.
        switch(pose) {
            case SWIPE:
                phone(c,229,198,-12);
                hand(c,111,246,-26);
                hand(c,258,237-action*22,24);
                line(c,302,199,302,179,accent,4);
                triangle(c,294,184,302,173,310,184,accent);
                break;
            case SEARCH:
                float lx=274+action*11,ly=151+action*8;
                line(c,lx+22,ly+24,lx+43,ly+61,accent,16);
                line(c,lx+22,ly+24,lx+43,ly+61,BODY,10);
                circle(c,lx,ly,39,rim);circle(c,lx,ly,34,0xff193542);
                line(c,lx-18,ly-15,lx-4,ly-24,Color.WHITE,5);
                hand(c,111,246,-26);
                hand(c,lx+34,ly+55,30);
                break;
            case GALLERY:
                float panelY=244+action*7;
                round(c,63,panelY,337,panelY+77,12,accent);
                round(c,67,panelY+4,333,panelY+73,9,0xff182935);
                for(int i=0;i<3;i++){
                    float xx=76+i*85;
                    round(c,xx,panelY+12,xx+75,panelY+65,6,0xff2c4658);
                    triangle(c,xx+28,panelY+22,xx+28,panelY+53,xx+51,panelY+37,Color.WHITE);
                }
                hand(c,111,panelY+1,-26);
                hand(c,289,panelY+1,26);
                break;
            case STACK:
                float cardY=187-Math.max(0,action)*26;
                mediaCard(c,232,cardY,67,47,-10);
                mediaCard(c,261,219,65,47,12);
                mediaCard(c,213,234,65,47,-8);
                round(c,206,266,337,322,10,accent);
                round(c,210,270,333,318,8,0xff172e39);
                round(c,255,283,290,305,5,accent);
                hand(c,111,246,-26);
                hand(c,271,cardY+30,26);
                break;
            case SKATE:
                hand(c,99,237-action*9,-37);
                hand(c,294,218+action*11,14);
                line(c,16,221,57,221,accent,4);
                line(c,5,238,37,238,soft,6);
                break;
            default:
                hand(c,111,246,-26);
                round(c,218,242,297,317,10,0xfff1f7fa);
                for(int i=0;i<3;i++)round(c,225+i*24,247,237+i*24,312,3,accent);
                for(int i=0;i<7;i++)circle(c,225+(i%4)*20,242-(i/4)*12,10,0xffffe6ad);
                hand(c,289,272,30);
                float kernelY=242-Math.max(0,action)*44;
                circle(c,251-Math.max(0,action)*47,kernelY,7,0xffffe6ad);
                break;
        }
        c.restore();
    }

    private void head(Canvas c,float t){
        // Exact visual cues from the full character: tall curved horns, round
        // black head, white X and angry eye, oversized jagged grin and cyan tongue.
        path.reset();path.moveTo(101,14);path.cubicTo(61,53,68,92,96,119);
        path.lineTo(132,80);path.cubicTo(110,65,97,38,101,14);path.close();
        fillStroke(c,accent,rim,2.5f);
        path.reset();path.moveTo(295,17);path.cubicTo(333,49,327,88,304,119);
        path.lineTo(267,80);path.cubicTo(289,62,304,37,295,17);path.close();
        fillStroke(c,accent,rim,2.5f);
        outlinedOval(c,86,57,312,254,FACE,6);

        line(c,116,115,162,160,Color.WHITE,17);
        line(c,162,115,116,160,Color.WHITE,17);
        path.reset();
        if(t>.46f&&t<.51f){
            path.moveTo(213,137);path.lineTo(280,111);path.lineTo(277,126);
            path.lineTo(239,148);path.close();
        }else{
            path.moveTo(212,136);path.lineTo(281,96);
            path.cubicTo(281,139,266,159,241,162);path.close();
        }
        fillPath(c,Color.WHITE);

        path.reset();path.moveTo(104,179);path.lineTo(131,190);path.lineTo(124,190);
        path.lineTo(150,222);path.lineTo(171,202);path.lineTo(198,234);
        path.lineTo(224,202);path.lineTo(249,219);path.lineTo(274,187);
        path.lineTo(258,187);path.lineTo(296,171);
        path.cubicTo(268,219,249,251,199,252);
        path.cubicTo(153,247,124,219,104,179);path.close();
        fillPath(c,Color.WHITE);

        float tongueBob=(float)Math.sin(t*Math.PI*6)*3;
        c.save();c.translate(0,tongueBob);
        path.reset();path.moveTo(189,225);
        path.cubicTo(207,216,228,220,230,239);
        path.cubicTo(230,265,220,275,207,270);
        path.lineTo(196,266);path.close();fillPath(c,accent);
        c.restore();
    }

    private void hand(Canvas c,float x,float y,float rotation){
        c.save();c.translate(x,y);c.rotate(rotation);
        outlinedOval(c,-42,-26,42,26,BODY,2.6f);
        c.restore();
    }
    private void foot(Canvas c,float x,float y,float rotation){
        c.save();c.translate(x,y);c.rotate(rotation);
        outlinedOval(c,-50,-27,50,27,FEET,2.6f);
        c.restore();
    }
    private void phone(Canvas c,float x,float y,float angle){
        c.save();c.rotate(angle,x+39,y+55);
        round(c,x,y,x+78,y+111,13,accent);
        round(c,x+4,y+4,x+74,y+107,10,0xff1c303c);
        triangle(c,x+26,y+35,x+26,y+72,x+56,y+53,Color.WHITE);
        round(c,x+28,y+97,x+50,y+100,2,accent);
        c.restore();
    }
    private void mediaCard(Canvas c,float x,float y,float w,float h,float angle){
        c.save();c.rotate(angle,x+w/2,y+h/2);
        round(c,x,y,x+w,y+h,6,accent);
        round(c,x+2,y+2,x+w-2,y+h-2,4,0xff25394a);
        triangle(c,x+w*.4f,y+h*.24f,x+w*.4f,y+h*.76f,x+w*.73f,y+h*.5f,Color.WHITE);
        c.restore();
    }
    private void outlinedOval(Canvas c,float l,float t,float r,float b,int color,float width){
        oval(c,l,t,r,b,color);p.setColor(accent);p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);c.drawOval(rect,p);p.setStyle(Paint.Style.FILL);
    }
    private void oval(Canvas c,float l,float t,float r,float b,int color){p.setStyle(Paint.Style.FILL);p.setColor(color);rect.set(l,t,r,b);c.drawOval(rect,p);}
    private void round(Canvas c,float l,float t,float r,float b,float radius,int color){p.setStyle(Paint.Style.FILL);p.setColor(color);rect.set(l,t,r,b);c.drawRoundRect(rect,radius,radius,p);}
    private void circle(Canvas c,float x,float y,float radius,int color){p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawCircle(x,y,radius,p);}
    private void triangle(Canvas c,float x,float y,float x2,float y2,float x3,float y3,int color){path.reset();path.moveTo(x,y);path.lineTo(x2,y2);path.lineTo(x3,y3);path.close();fillPath(c,color);}
    private void fillPath(Canvas c,int color){p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawPath(path,p);}
    private void fillStroke(Canvas c,int color,int stroke,float width){fillPath(c,color);strokePath(c,stroke,width);}
    private void strokePath(Canvas c,int color,float width){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setColor(color);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);}
    private void line(Canvas c,float x,float y,float xx,float yy,int color,float width){p.setStyle(Paint.Style.FILL);p.setStrokeWidth(width);p.setStrokeCap(Paint.Cap.SQUARE);p.setColor(color);c.drawLine(x,y,xx,yy,p);}

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
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);float s=Math.min(getWidth(),getHeight());
            art.draw(c,(getWidth()-s)/2,(getHeight()-s)/2,s,POPCORN,phase);
        }
    }
}
