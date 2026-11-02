package com.webapp.crazyshit;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.io.ByteArrayOutputStream;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application=Application.class,sdk=35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SocialMediaTest {
 @Test public void preparesJpegPngWebpWithinDimensionsAndSize() throws Exception {
  for(Bitmap.CompressFormat format:new Bitmap.CompressFormat[]{Bitmap.CompressFormat.JPEG,Bitmap.CompressFormat.PNG,Bitmap.CompressFormat.WEBP}) {
   Bitmap bitmap=Bitmap.createBitmap(3200,1600,Bitmap.Config.ARGB_8888);bitmap.eraseColor(0xff2080ff);
   ByteArrayOutputStream out=new ByteArrayOutputStream();bitmap.compress(format,90,out);bitmap.recycle();
   SocialMedia.Prepared prepared=SocialMedia.prepareBytes(RuntimeEnvironment.getApplication(),out.toByteArray(),null);
   assertTrue(prepared.bytes.length<=SocialMedia.PHOTO_LIMIT);
   Bitmap result=BitmapFactory.decodeByteArray(prepared.bytes,0,prepared.bytes.length);
   assertEquals(2560,result.getWidth());assertEquals(1280,result.getHeight());result.recycle();
  }
 }
 @Test public void gifBytesPassThroughUnchanged() throws Exception {
  byte[] gif=java.util.Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7");
  SocialMedia.Prepared result=SocialMedia.prepareBytes(RuntimeEnvironment.getApplication(),gif,"image/gif");
  assertArrayEquals(gif,result.bytes);assertEquals("GIF",result.fallback());assertEquals("social-gifs",result.bucket());
 }
 @Test public void animationBytesAndExifOrientationArePreserved() throws Exception {
  byte[] gif=getClass().getResourceAsStream("/social-media/animated.gif").readAllBytes();
  assertArrayEquals(gif,SocialMedia.prepareBytes(RuntimeEnvironment.getApplication(),gif,"image/gif").bytes);
  byte[] jpeg=getClass().getResourceAsStream("/social-media/orientation-6.jpg").readAllBytes();
  SocialMedia.Prepared prepared=SocialMedia.prepareBytes(RuntimeEnvironment.getApplication(),jpeg,"image/jpeg");
  Bitmap result=BitmapFactory.decodeByteArray(prepared.bytes,0,prepared.bytes.length);
  assertEquals(20,result.getWidth());assertEquals(40,result.getHeight());result.recycle();
 }
 @Test public void rejectsOversizeAndSpoofedMime() throws Exception {
  byte[] large=new byte[SocialMedia.PHOTO_SOURCE_LIMIT+1];large[0]=(byte)255;large[1]=(byte)216;large[2]=(byte)255;
  reject(large,"image/jpeg","20 MB");
  byte[] gif=new byte[SocialMedia.GIF_LIMIT+1];System.arraycopy("GIF89a".getBytes(),0,gif,0,6);reject(gif,"image/gif","10 MB");
  reject("<svg/>".getBytes(),"image/png","JPEG");
  reject(new byte[]{(byte)255,(byte)216,(byte)255},"image/png","JPEG");
 }
 @Test public void commentAndDmMediaPreserveAspectRatioWithinSharedLimits() {
  android.app.Activity activity=org.robolectric.Robolectric.buildActivity(android.app.Activity.class).setup().get();
  for(boolean message:new boolean[]{false,true})for(int[] size:new int[][]{{1200,600},{600,1800},{80,60}}){
   SocialMedia.MediaView image=(SocialMedia.MediaView)SocialMedia.inline(activity,"","",message);
   Bitmap bitmap=Bitmap.createBitmap(size[0],size[1],Bitmap.Config.ARGB_8888);image.setImageBitmap(bitmap);
   image.measure(android.view.View.MeasureSpec.makeMeasureSpec(500,android.view.View.MeasureSpec.AT_MOST),android.view.View.MeasureSpec.makeMeasureSpec(0,android.view.View.MeasureSpec.UNSPECIFIED));
   assertTrue(image.getMeasuredHeight()<=image.maximumHeight);assertTrue(image.getMeasuredWidth()<=image.maximumWidth);
   assertEquals(size[0]/(float)size[1],image.getMeasuredWidth()/(float)image.getMeasuredHeight(),0.02f);assertTrue(image.getClipToOutline());bitmap.recycle();
  }
 }
 private void reject(byte[] bytes,String mime,String text) throws Exception {
  try {SocialMedia.prepareBytes(RuntimeEnvironment.getApplication(),bytes,mime);fail("Accepted unsupported image");}
  catch(IllegalArgumentException expected) {assertTrue(expected.getMessage(),expected.getMessage().contains(text));}
 }
}
