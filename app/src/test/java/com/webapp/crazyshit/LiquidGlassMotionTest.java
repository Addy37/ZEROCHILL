package com.webapp.crazyshit;

import android.app.Application;
import android.content.Context;
import android.graphics.*;
import android.os.Looper;
import android.view.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.util.ReflectionHelpers;
import java.time.Duration;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application=Application.class,sdk=35,qualifiers="w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LiquidGlassMotionTest {
    @Test public void logicalSelectionIsImmediateWhileLensMovesAndFastReversalSettles() {
        ActivityController<android.app.Activity> controller=org.robolectric.Robolectric.buildActivity(android.app.Activity.class).create();
        android.app.Activity activity=controller.get();
        activity.setTheme(R.style.Theme_CrazyShit);
        controller.start().resume().visible();
        activity.getSharedPreferences("app_prefs",Context.MODE_PRIVATE).edit().putBoolean("immersive_motion_enabled",true).commit();
        FrostedNavigationLayout shell=new FrostedNavigationLayout(activity);
        shell.setOrientation(android.widget.LinearLayout.VERTICAL);
        shell.addView(new View(activity),new android.widget.LinearLayout.LayoutParams(-1,0,1));
        ZeroChillBottomNavigationView nav=new ZeroChillBottomNavigationView(activity);
        int[] ids={2,4,3,6};
        for(int i=0;i<4;i++)nav.getMenu().add(0,ids[i],i,"Tab "+i).setIcon(R.drawable.ic_nav_series);
        shell.addView(nav,new android.widget.LinearLayout.LayoutParams(-1,BrowseUi.dp(activity,64)));
        StableBottomNavigationController.styleBar(nav);
        activity.setContentView(shell);
        BottomNavigationInsetsTest.layoutShell(shell);
        nav.setPagerPosition(0);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350));
        RectF first=new RectF();nav.copyOpticalLensBounds(first);
        nav.setPagerPosition(3);
        assertEquals(3f,nav.pagerPositionForTest(),0f);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(80));
        RectF middle=new RectF();nav.copyOpticalLensBounds(middle);
        assertTrue("lens moved from "+first+" to "+middle,middle.centerX()>first.centerX());
        nav.setPagerPosition(1);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350));
        RectF finalLens=new RectF();nav.copyOpticalLensBounds(finalLens);
        View target=nav.findViewById(4);
        Rect targetBounds=new Rect(0,0,target.getWidth(),target.getHeight());nav.offsetDescendantRectToMyCoords(target,targetBounds);
        assertEquals(targetBounds.exactCenterX(),finalLens.centerX(),1f);
        activity.getSharedPreferences("app_prefs",Context.MODE_PRIVATE).edit().putBoolean("immersive_motion_enabled",false).commit();
        nav.setPagerPosition(3);
        nav.copyOpticalLensBounds(finalLens);
        assertEquals(3f,nav.pagerPositionForTest(),0f);
        View last=nav.findViewById(6);
        Rect lastBounds=new Rect(0,0,last.getWidth(),last.getHeight());nav.offsetDescendantRectToMyCoords(last,lastBounds);
        assertEquals("reduced motion snaps immediately",lastBounds.exactCenterX(),finalLens.centerX(),1f);
        controller.pause().stop().destroy();
    }
}
