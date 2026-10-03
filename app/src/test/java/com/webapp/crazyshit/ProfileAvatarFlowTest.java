package com.webapp.crazyshit;

import android.app.Application;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import java.io.File;
import java.nio.file.Files;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application=Application.class,sdk=35,shadows={ProfileAvatarFlowTest.Accounts.class,ProfileAvatarFlowTest.Sessions.class})
public class ProfileAvatarFlowTest {
    static final String OWNER="00000000-0000-0000-0000-000000000001";
    static String user=OWNER;
    static boolean failUpload;
    static int uploads;
    static ZeroChillAccountRepository.AccountState state(String path) {
        return new ZeroChillAccountRepository.AccountState(true,false,OWNER,"test@invalid.example","avatarfixture","Addy",path,"2026-10-03","");
    }
    @Implements(value=ZeroChillAccountRepository.class,isInAndroidSdk=false)
    public static class Accounts {
        @Implementation protected static boolean isConfigured(){return true;}
        @Implementation protected static boolean hasStoredSession(Context context){return true;}
        @Implementation protected static void current(Context context,ZeroChillAccountRepository.Callback<ZeroChillAccountRepository.AccountState> cb){cb.complete(state(OWNER+"/avatar.jpg"),null);}
        @Implementation protected static void uploadAvatar(Context context,String expected,byte[] jpeg,ZeroChillAccountRepository.Callback<ZeroChillAccountRepository.AccountState> cb){
            uploads++; assertEquals(OWNER,expected); assertArrayEquals(new byte[]{1,2,3},jpeg);
            cb.complete(failUpload?null:state(OWNER+"/avatar-new.jpg"),failUpload?new Exception("Upload failed"):null);
        }
    }
    @Implements(value=ZeroChillSessionStore.class,isInAndroidSdk=false)
    public static class Sessions {
        @Implementation protected static String currentUserId(Context context){return user;}
    }
    private ZeroChillAccountActivity create() {
        uploads=0;failUpload=false;user=OWNER;
        ZeroChillAccountActivity activity=Robolectric.buildActivity(ZeroChillAccountActivity.class).create().start().resume().get();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        return activity;
    }
    @Test public void pickerSelectionOpensEditorAndCancelNeverUploads() throws Exception {
        ZeroChillAccountActivity activity=create();
        find(activity.getWindow().getDecorView(),"CHANGE AVATAR").performClick();
        assertEquals(Intent.ACTION_OPEN_DOCUMENT,Shadows.shadowOf(activity).getNextStartedActivity().getAction());
        activity.onActivityResult(6401,Activity.RESULT_OK,new Intent().setData(Uri.parse("content://test/picked")));
        Intent editor=Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(CreatorAvatarCropActivity.class.getName(),editor.getComponent().getClassName());
        assertEquals("content://test/picked",editor.getStringExtra(CreatorAvatarCropActivity.EXTRA_LOCAL_URI));
        activity.onActivityResult(6403,Activity.RESULT_CANCELED,null);
        assertEquals(0,uploads); assertEquals(OWNER+"/avatar.jpg",account(activity).avatarPath);
    }
    @Test public void savedCropUpdatesAccountImmediatelyAndFailureKeepsOldAvatar() throws Exception {
        ZeroChillAccountActivity activity=create();
        find(activity.getWindow().getDecorView(),"CHANGE AVATAR").performClick();
        activity.onActivityResult(6403,Activity.RESULT_OK,crop(activity));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(1,uploads); assertEquals(OWNER+"/avatar-new.jpg",account(activity).avatarPath);
        failUpload=true;
        activity.onActivityResult(6403,Activity.RESULT_OK,crop(activity));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(2,uploads); assertEquals(OWNER+"/avatar-new.jpg",account(activity).avatarPath);
    }
    @Test public void switchingAccountDuringPickerCannotUploadForOtherUser() throws Exception {
        ZeroChillAccountActivity activity=create();
        find(activity.getWindow().getDecorView(),"CHANGE AVATAR").performClick();
        user="00000000-0000-0000-0000-000000000002";
        activity.onActivityResult(6403,Activity.RESULT_OK,crop(activity));
        assertEquals(0,uploads);assertEquals(OWNER+"/avatar.jpg",account(activity).avatarPath);
    }
    private Intent crop(ZeroChillAccountActivity activity) throws Exception {
        File folder=new File(activity.getCacheDir(),"profile-avatar-crops");folder.mkdirs();
        File file=File.createTempFile("avatar-",".jpg",folder);Files.write(file.toPath(),new byte[]{1,2,3});
        return new Intent().putExtra(CreatorAvatarCropActivity.EXTRA_CROPPED_FILE,file.getAbsolutePath());
    }
    private ZeroChillAccountRepository.AccountState account(ZeroChillAccountActivity activity)throws Exception{
        Field field=ZeroChillAccountActivity.class.getDeclaredField("account");field.setAccessible(true);return (ZeroChillAccountRepository.AccountState)field.get(activity);
    }
    private View find(View view,String value){
        if(view instanceof TextView&&value.contentEquals(((TextView)view).getText()))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View result=find(((ViewGroup)view).getChildAt(i),value);if(result!=null)return result;}
        return null;
    }
}
