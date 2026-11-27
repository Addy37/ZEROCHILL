package com.webapp.crazyshit;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.function.IntConsumer;

/** One retained phone preview, five local scenes. No media or network dependency. */
final class CinematicTourView extends LinearLayout {
    static final String[] TITLES = {"Find your feed.", "Find what you want.", "Explore creators.", "Make it yours.", "Everything at your fingertips."};
    static final String[] COPY = {
            "ShitTok: five feeds. Swipe to the next clip, or tap to pause.",
            "Spotlight searches by source. Find Shows, albums, creators and saved content.",
            "OnlyFap galleries: open a creator or album and pinch to resize the grid.",
            "Your Library: resume, save, download and organize custom collections.",
            "Favorite creators: slide along A–Z. Hold for actions or double-tap supported videos to seek."
    };
    private final int accent;
    private final IntConsumer changed;
    private final Phone phone;
    private final TextView title, copy;
    private final TextView[] dots = new TextView[TITLES.length];
    private int scene;
    private boolean active, touched, manualNavigation;
    private float downX, downY;
    private ValueAnimator animation;
    private final Runnable advance = () -> changeScene(scene + 1);

    CinematicTourView(Context context, int accent, int initial, IntConsumer changed) {
        super(context);
        this.accent = accent;
        this.changed = changed;
        scene = Math.max(0, Math.min(TITLES.length - 1, initial));
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setPadding(dp(16), dp(8), dp(16), dp(8));
        TextView eyebrow = text("THE ZEROCHILL EXPERIENCE", 11, accent, true);
        eyebrow.setLetterSpacing(.12f);
        addView(eyebrow, new LayoutParams(-1, -2));

        LinearLayout body = new LinearLayout(context);
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        body.setOrientation(landscape ? HORIZONTAL : VERTICAL);
        body.setGravity(Gravity.CENTER);
        addView(body, new LayoutParams(-1, 0, 1));
        phone = new Phone(context);
        body.addView(phone, landscape ? new LayoutParams(0, -1, 1) : new LayoutParams(-1, 0, 1));
        LinearLayout words = new LinearLayout(context);
        words.setOrientation(VERTICAL);
        words.setGravity(Gravity.CENTER);
        words.setPadding(dp(6), dp(4), dp(6), dp(2));
        body.addView(words, landscape ? new LayoutParams(0, -2, 1) : new LayoutParams(-1, -2));
        title = text("", 25, Color.WHITE, true);
        copy = text("", 14, 0xffb8c0cc, false);
        words.addView(title, new LayoutParams(-1, -2));
        words.addView(copy, new LayoutParams(-1, -2));
        LinearLayout navigation = new LinearLayout(context);
        navigation.setGravity(Gravity.CENTER);
        addView(navigation, new LayoutParams(-1, dp(48)));
        TextView previous = text("‹", 26, accent, true);
        previous.setContentDescription("Previous preview");
        previous.setOnClickListener(v -> select(scene - 1));
        navigation.addView(previous, new LayoutParams(dp(48), -1));
        for (int i = 0; i < dots.length; i++) {
            final int index = i;
            TextView dot = text("•", 21, 0xff646b76, true);
            dot.setContentDescription("Preview " + (i + 1) + ": " + TITLES[i]);
            dot.setOnClickListener(v -> select(index));
            dot.setFocusable(true);
            navigation.addView(dot, new LayoutParams(0, -1, 1));
            dots[i] = dot;
        }
        TextView next = text("›", 26, accent, true);
        next.setContentDescription("Next preview");
        next.setOnClickListener(v -> select(scene + 1));
        navigation.addView(next, new LayoutParams(dp(48), -1));
        refresh();
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(getContext());
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    int currentScene() { return scene; }

    void select(int index) {
        manualNavigation = true;
        changeScene(index);
    }

    private void changeScene(int index) {
        scene = (index + TITLES.length) % TITLES.length;
        changed.accept(scene);
        refresh();
        playScene();
    }

    private void refresh() {
        title.setText(TITLES[scene]);
        copy.setText(COPY[scene]);
        for (int i = 0; i < dots.length; i++) {
            dots[i].setSelected(i == scene);
            dots[i].setText(i == scene ? "●" : "•");
            dots[i].setTextColor(i == scene ? accent : 0xff646b76);
        }
        phone.setContentDescription(TITLES[scene] + " " + COPY[scene] + " Swipe for the next preview.");
        phone.phase = .8f;
        phone.invalidate();
    }

    void resumeTour() {
        if (active) return;
        active = true;
        playScene();
    }

    void pauseTour() {
        active = false;
        removeCallbacks(advance);
        if (animation != null) animation.cancel();
        animation = null;
        phone.phase = .8f;
        phone.invalidate();
    }

    private void playScene() {
        removeCallbacks(advance);
        if (animation != null) animation.cancel();
        animation = null;
        if (!active || !isAttachedToWindow() || !ZeroChillMotion.animationsEnabled(getContext())) {
            phone.phase = .8f;
            phone.invalidate();
            return;
        }
        animation = ValueAnimator.ofFloat(0f, 1f);
        animation.setDuration(3400L);
        animation.addUpdateListener(a -> { phone.phase = (float)a.getAnimatedValue(); phone.invalidate(); });
        animation.start();
        android.view.accessibility.AccessibilityManager accessibility =
                (android.view.accessibility.AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (!touched && !manualNavigation && (accessibility == null || !accessibility.isTouchExplorationEnabled()))
            postDelayed(advance, 4400L);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (active) playScene();
    }

    @Override protected void onDetachedFromWindow() {
        pauseTour();
        super.onDetachedFromWindow();
    }

    private int dp(float x) { return Math.round(x * getResources().getDisplayMetrics().density); }

    private final class Phone extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF r = new RectF();
        final WizardMascotArt mascot = new WizardMascotArt(getContext(), accent);
        final int[] artworkColors = {0xff344c61,0xff574763,0xff26545a,0xff414d70};
        final String[] tabs = {"Chaos","Straight","Gay","Trans","Groups"};
        final String[] letters = {"A","F","M","S","Z"};
        final String[] collections = {"Favorites","My picks","Watch later","Saved"};
        final RadialGradient glow = new RadialGradient(110, 162, 118,
                new int[]{(accent & 0xffffff) | 0x52000000, (accent & 0xffffff) | 0x0c000000, Color.TRANSPARENT},
                new float[]{0f, .72f, 1f}, Shader.TileMode.CLAMP);
        float phase = .8f;
        Phone(Context context) { super(context); setClickable(true); setFocusable(true); }
        @Override public boolean performClick() { super.performClick(); select(scene + 1); return true; }
        @Override public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    touched = true; removeCallbacks(advance);
                    downX = event.getX(); downY = event.getY(); return true;
                case MotionEvent.ACTION_UP:
                    touched = false;
                    float delta = event.getX() - downX;
                    if (Math.abs(delta) > dp(30) && Math.abs(delta) > Math.abs(event.getY() - downY))
                        select(scene + (delta < 0 ? 1 : -1));
                    else performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL: touched = false; playScene(); return true;
                default: return true;
            }
        }
        @Override protected void onDraw(Canvas c) {
            float s = Math.min(getWidth()/220f, getHeight()/324f);
            if (s <= 0) return;
            c.save(); c.translate((getWidth()-220*s)/2,(getHeight()-324*s)/2); c.scale(s,s);
            p.setShader(glow); c.drawRect(0,0,220,324,p); p.setShader(null);
            box(c,37,5,183,319,20,0xff35414b);
            box(c,38.5f,6.5f,181.5f,317.5f,19,0xff090b10);
            box(c,94,12,126,16,2,0xff46515c);
            c.save(); c.clipRect(44,23,176,291);
            // Contents reveal inside the retained device frame.
            float t = ease(phase);
            int alpha = Math.round(90+165*Math.min(1,phase*7));
            int layer = c.saveLayerAlpha(44,23,176,291,alpha);
            c.translate((1-Math.min(1,phase*7))*13,0);
            switch(scene) {
                case 0: feed(c,t); break;
                case 1: search(c,t); break;
                case 2: gallery(c,t); break;
                case 3: library(c,t); break;
                default: creators(c,t);
            }
            c.restoreToCount(layer);
            c.restore();
            box(c,61,294,159,308,7,0xff202632);
            for(int i=0;i<5;i++) circle(c,72+i*19,301,3.5f,i==scene?accent:0xff7d8996);
            c.restore();
        }
        float ease(float t) { return t*t*(3-2*t); }
        void feed(Canvas c,float t) {
            c.save(); c.clipRect(44,42,176,287);
            float shift=Math.max(0,(t-.22f)/.78f)*245;
            art(c,44,42-shift,176,287-shift,WizardMascotArt.SWIPE);
            art(c,44,287-shift,176,532-shift,WizardMascotArt.SKATE);
            c.restore();
            box(c,44,23,176,42,4,0xff10151c);
            for(int i=0;i<5;i++) label(c,tabs[i],57+i*26,34,5.8f,i==Math.min(4,(int)(t*5))?accent:Color.WHITE);
            for(int i=0;i<4;i++) circle(c,164,155+i*23,7,0xffc6d1d9);
            box(c,50,256,111,261,2,Color.WHITE);
            box(c,50,266,92,270,2,0xffbac3cf);
            arrow(c,59,212-t*36,59,177-t*36);
        }
        void search(Canvas c,float t) {
            label(c,"Spotlight",110,39,13,Color.WHITE);
            box(c,49,48,171,70,8,accent);
            box(c,50,49,170,69,7,0xff202632);
            String query="Mascot moments"; int n=Math.min(query.length(),(int)(t*28));
            label(c,query.substring(0,n),110,62,8,Color.WHITE);
            for(int i=0;i<5;i++) {
                float v=Math.max(0,Math.min(1,(t-.18f-i*.105f)*6));
                if(v<=0)continue;
                float y=79+i*40+(1-v)*10;
                art(c,50,y,99,y+32,(i==0?WizardMascotArt.SEARCH:i+1)%6);
                box(c,105,y+4,166,y+8,2,0xffdae2eb);
                box(c,105,y+15,140,y+19,2,accent);
                box(c,105,y+25,155,y+28,1,0xff53616d);
            }
            // A larger detective rises into view while the results arrive.
            mascot.draw(c,93,185-t*8,79,WizardMascotArt.SEARCH,t);
        }
        void gallery(Canvas c,float t) {
            label(c,"OnlyFap · Gallery",110,40,11,Color.WHITE);
            float f=Math.max(0,Math.min(1,(t-.18f)*1.5f));
            for(int i=0;i<8;i++) {
                float x=lerp(50+(i%2)*60,50+(i%3)*41,f);
                float y=lerp(55+(i/2)*55,55+(i/3)*72,f);
                art(c,x,y,x+lerp(56,37,f),y+lerp(49,66,f),(i+2)%6);
            }
            mascot.draw(c,76,119+f*8,73,WizardMascotArt.GALLERY,t);
        }
        void library(Canvas c,float t) {
            label(c,"Your Library",110,40,12,Color.WHITE);
            label(c,"My collections",110,58,9,0xffa5b0bd);
            for(int i=0;i<4;i++) {
                float x=56+i%2*64,y=85+i/2*91;
                float spread=1-t;
                box(c,x+7+spread*8,y-6-spread*10,x+50+spread*8,y+48,6,0xff49606e);
                box(c,x-5-spread*5,y-2,x+39,y+54,6,0xff36485a);
                art(c,x,y,x+46,y+56,(i+3)%6);
                label(c,collections[i],x+23,y+69,8,Color.WHITE);
            }
            mascot.draw(c,78,132-t*5,76,WizardMascotArt.STACK,t);
        }
        void creators(Canvas c,float t) {
            label(c,"Favorite creators",110,40,10.5f,Color.WHITE);
            int index=Math.min(4,(int)(t*5));
            c.save();
            c.clipRect(44,48,160,287);
            float offset=(t*5-index)*48;
            label(c,letters[index],51,59-offset,9,accent);
            for(int i=0;i<12;i++) {
                float x=62+i%3*38,y=81+i/3*64-offset;
                circle(c,x,y,14,artworkColors[(i+index)%4]);
                mascot.draw(c,x-15,y-15,30,(i+index)%6,t);
                box(c,x-10,y+20,x+10,y+23,2,0xff788794);
            }
            c.restore();
            for(int i=0;i<5;i++) {
                float y=60+i*46;
                if(i==index)box(c,164,y-10,176,y+6,5,accent);
                label(c,letters[i],170,y,8,i==index?Color.BLACK:0xffa8b1bf);
            }
            float y=55+index*46;
            mascot.draw(c,85,Math.max(45,Math.min(226,y-23)),58,WizardMascotArt.SKATE,t);
            box(c,128,y-9,159,y+22,9,accent);
            label(c,letters[index],143.5f,y+12,17,Color.BLACK);
        }
        float lerp(float a,float b,float t){return a+(b-a)*t;}
        void art(Canvas c,float l,float t,float rr,float b,int variant) {
            if(b<42 || t>291)return;
            box(c,l,t,rr,b,5,artworkColors[variant%4]);
            float size=Math.min((rr-l)*1.06f,(b-t)*1.06f);
            c.save();c.clipRect(l,t,rr,b);
            mascot.draw(c,(l+rr-size)/2,(t+b-size)/2,size,variant%6,phase);
            c.restore();
        }
        void box(Canvas c,float l,float t,float rr,float b,float rad,int color){r.set(l,t,rr,b);p.setColor(color);c.drawRoundRect(r,rad,rad,p);}
        void circle(Canvas c,float x,float y,float rad,int color){p.setColor(color);c.drawCircle(x,y,rad,p);}
        void label(Canvas c,String text,float x,float y,float size,int color){p.setColor(color);p.setTextSize(size);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.DEFAULT_BOLD);c.drawText(text,x,y,p);}
        void arrow(Canvas c,float x,float y,float x2,float y2){p.setColor(accent);p.setStrokeWidth(2.5f);p.setStrokeCap(Paint.Cap.ROUND);c.drawLine(x,y,x2,y2,p);c.drawLine(x2,y2,x2-7,y2+9,p);c.drawLine(x2,y2,x2+7,y2+9,p);}
    }
}
