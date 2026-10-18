package com.webapp.crazyshit;

import android.app.Application;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application=Application.class, sdk=35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ProfileAvatarCropTest {
    @Test public void exportMatchesPositionedSquareAndResetRestoresCenter() {
        CreatorAvatarImageView image = new CreatorAvatarImageView(RuntimeEnvironment.getApplication());
        image.layout(0, 0, 100, 100);
        Bitmap source = Bitmap.createBitmap(300, 100, Bitmap.Config.ARGB_8888);
        for (int x=0;x<300;x++) for(int y=0;y<100;y++) source.setPixel(x,y,x<100?Color.RED:x<200?Color.GREEN:Color.BLUE);
        image.setImageBitmap(source); image.resetAvatarCrop();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(Color.GREEN, image.avatarBitmap(512).getPixel(256,256));
        image.dragAvatarBy(100,0);
        assertEquals(Color.RED, image.avatarBitmap(512).getPixel(256,256));
        image.setAvatarZoom(2f); assertEquals(2f, image.avatarZoom(), 0.001f);
        image.resetAvatarCrop(); assertEquals(1f, image.avatarZoom(), 0.001f);
        Bitmap out = image.avatarBitmap(512);
        assertEquals(512,out.getWidth()); assertEquals(512,out.getHeight());
        assertEquals(Color.GREEN,out.getPixel(256,256));
    }
    @Test public void localImageOpensSameEditorAndBackReturnsNoCrop() {
        Intent intent = CreatorAvatarCropActivity.createLocal(Robolectric.buildActivity(android.app.Activity.class).create().get(), Uri.parse("content://test/image"));
        CreatorAvatarCropActivity activity = Robolectric.buildActivity(CreatorAvatarCropActivity.class,intent).create().start().resume().get();
        assertNotNull(find(activity.getWindow().getDecorView(),"Adjust avatar"));
        assertNotNull(find(activity.getWindow().getDecorView(),"Reset"));
        assertNotNull(find(activity.getWindow().getDecorView(),"Save"));
        activity.onBackPressed();
        assertTrue(activity.isFinishing());
        assertEquals(android.app.Activity.RESULT_CANCELED,Shadows.shadowOf(activity).getResultCode());
        assertNull(Shadows.shadowOf(activity).getResultIntent());
    }
    @Test public void cropStateSurvivesRecreation() throws Exception {
        Intent intent = new Intent(RuntimeEnvironment.getApplication(),CreatorAvatarCropActivity.class)
                .putExtra(CreatorAvatarCropActivity.EXTRA_LOCAL_URI,"content://test/image");
        CreatorAvatarCropActivity first = Robolectric.buildActivity(CreatorAvatarCropActivity.class,intent).create().get();
        Field field=CreatorAvatarCropActivity.class.getDeclaredField("image");field.setAccessible(true);
        ((CreatorAvatarImageView)field.get(first)).setAvatarCrop(.2f,.7f,2.5f);
        Bundle state=new Bundle();first.onSaveInstanceState(state);
        CreatorAvatarCropActivity restored=Robolectric.buildActivity(CreatorAvatarCropActivity.class,intent).create(state).get();
        CreatorAvatarImageView image=(CreatorAvatarImageView)field.get(restored);
        assertEquals(.2f,image.avatarFocusX(),.001f);assertEquals(.7f,image.avatarFocusY(),.001f);assertEquals(2.5f,image.avatarZoom(),.001f);
    }
    private static View find(View view,String value) {
        if(view instanceof TextView&&value.contentEquals(((TextView)view).getText()))return view;
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++){
            View found=find(((ViewGroup)view).getChildAt(i),value);if(found!=null)return found;
        }
        return null;
    }
}
