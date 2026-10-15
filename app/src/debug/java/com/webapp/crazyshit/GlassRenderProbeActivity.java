package com.webapp.crazyshit;

import android.app.Activity;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;

/** Debug-only renderer fixture. Never packaged in signed release candidates. */
public final class GlassRenderProbeActivity extends Activity {
    private ZeroChillBottomNavigationView nav;
    private Backdrop backdrop;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean alive;
    private int frame;
    private long start;
    private final Runnable animate = new Runnable() {
        @Override public void run() {
            if (!alive) return;
            backdrop.phase = ((SystemClock.uptimeMillis() - start) % 4000) / 4000f;
            backdrop.invalidate();
            if (++frame % 90 == 0) nav.setPagerPosition((frame / 90) % 4);
            handler.postDelayed(this, 16);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);
        FrostedNavigationLayout shell = new FrostedNavigationLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Color.BLACK);
        shell.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            shell.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        backdrop = new Backdrop();
        backdrop.mode = getIntent().getIntExtra("mode", 0);
        shell.addView(backdrop, new LinearLayout.LayoutParams(-1,0,1));
        nav = new ZeroChillBottomNavigationView(this);
        int[] ids={2,4,3,6}; int[] icons={R.drawable.ic_nav_series,R.drawable.ic_nav_chaos,R.drawable.ic_nav_onlyfap,R.drawable.ic_more_library};
        String[] labels={"Shows","ShitTok","OnlyFap","Library"};
        for(int i=0;i<4;i++) nav.getMenu().add(0,ids[i],i,labels[i]).setIcon(icons[i]);
        nav.getMenu().findItem(4).setChecked(true); nav.setPagerPosition(1);
        nav.setOnItemSelectedListener(item -> {for(int i=0;i<4;i++)if(ids[i]==item.getItemId())nav.setPagerPosition(i);return true;});
        LinearLayout.LayoutParams floating = new LinearLayout.LayoutParams(-1,dp(64));
        floating.setMargins(dp(10),-dp(70),dp(10),dp(6));
        shell.addView(nav,floating);
        StableBottomNavigationController.styleBar(nav); nav.reapplyCurrentGeometry();
        setContentView(shell);
        alive = true; start = SystemClock.uptimeMillis();
        if(getIntent().getBooleanExtra("moving",false))handler.post(animate);
    }
    @Override protected void onDestroy(){alive=false;handler.removeCallbacksAndMessages(null);super.onDestroy();}
    private int dp(int n){return BrowseUi.dp(this,n);}
    private final class Backdrop extends View {
        final Paint paint = new Paint(3); int mode; float phase;
        Backdrop(){super(GlassRenderProbeActivity.this);}
        @Override public void onDraw(Canvas c) {
            int w=getWidth(),h=getHeight();
            c.drawColor(mode==1 ? Color.BLACK : 0xFF456A80);
            if(mode!=1){
                for(int i=-2;i<10;i++){
                    paint.setColor(i%2==0?0xFFE7E9E5:0xFF24323D);
                    float x=(i+phase)*w/6f;
                    c.drawRect(x,0,x+w/14f,h,paint);
                }
                paint.setColor(0xFFBF7555);c.drawCircle(w*.72f,h*.7f,w*.26f,paint);
            }
            paint.setColor(Color.WHITE);paint.setTextSize(dp(30));
            c.drawText("ZEROCHILL",dp(24),dp(56),paint);
            paint.setTextSize(dp(14));c.drawText("Native glass renderer fixture",dp(24),dp(84),paint);
            paint.setTextSize(dp(12));c.drawText("Bright / dark / moving backdrop",dp(24),h-dp(110),paint);
        }
    }
}
