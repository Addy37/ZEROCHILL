package com.webapp.crazyshit;

import android.app.*;
import android.content.Context;
import android.graphics.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import java.io.*;
import java.time.Duration;
import java.util.*;
import static org.junit.Assert.*;

/** Production Views with identical local artwork. Software output does not prove GPU glass. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LiquidGlassRenderingTest {
    private Activity activity;
    @Before public void setup() {
        activity = Robolectric.buildActivity(Activity.class).create().get();
        activity.setTheme(R.style.Theme_CrazyShit);
        activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
                .putBoolean("immersive_motion_enabled", false).commit();
    }
    @Test public void primarySurfacesWithNavigation() throws Exception {
        ShowsHubView shows = new ShowsHubView(activity, item -> {}, item -> {}, item -> {});
        shows.setCrazyShit(items(NativeContentItem.KIND_SERIES));
        shows.finishLoading();
        ImageView showImage = ReflectionHelpers.getField(shows, "heroBackdrop");
        com.bumptech.glide.Glide.with(showImage).clear(showImage);
        showImage.setImageBitmap(artwork());
        ImageView showPoster = ReflectionHelpers.getField(shows, "heroImage");
        com.bumptech.glide.Glide.with(showPoster).clear(showPoster);
        showPoster.setImageBitmap(artwork());
        save(shell(shows, 0), "5-shows");
        shows.setActive(false);
        OnlyFapHubView creators = new OnlyFapHubView(activity, new OnlyFapHubView.Listener() {
            public void onOpenCreator(NativeContentItem item) {} public void onSearch() {}
            public void onMore(View view) {} public void onViewAllFavorites() {}
        });
        ImageView creatorImage = ReflectionHelpers.getField(creators, "heroImage");
        creatorImage.setImageBitmap(artwork());
        save(shell(creators, 2), "5-onlyfap");
        creators.close();
        LibraryHubView library = new LibraryHubView(activity, new LibraryHubView.Listener() {
            public void onOpenWatchLater(NativeContentItem item) {}
            public void onOpenHistory(PlaybackHistoryStore.Item item) {}
            public void onOpenCreator(NativeContentItem item) {}
        });
        save(shell(library, 3), "5-library");
        library.close();
        ImageView media = new ImageView(activity);
        media.setImageBitmap(artwork()); media.setScaleType(ImageView.ScaleType.CENTER_CROP);
        save(shell(media, 1), "5-media-navigation");
    }
    @Test public void sharedDialogKeepsButtonsAndReadableMaterial() throws Exception {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        AlertDialog dialog = new ZeroChillDialog.Builder(activity).setTitle("Save your backup")
                .setMessage("Keep your favorites, history and settings together.")
                .setPositiveButton("Save backup", (d, which) -> {}).setNegativeButton("Cancel", null).create();
        dialog.show();
        assertNotNull(dialog.getButton(AlertDialog.BUTTON_POSITIVE));
        save(dialog.getWindow().getDecorView(), "5-dialog", 360, 300);
        dialog.dismiss();
    }
    private View shell(View page, int selected) {
        FrostedNavigationLayout shell = new FrostedNavigationLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Color.BLACK);
        shell.addView(page, new LinearLayout.LayoutParams(-1, 0, 1));
        ZeroChillBottomNavigationView nav = new ZeroChillBottomNavigationView(activity);
        int[] ids={2,4,3,6};
        int[] icons={R.drawable.ic_nav_series,R.drawable.ic_nav_chaos,R.drawable.ic_nav_onlyfap,R.drawable.ic_more_library};
        String[] labels={"Shows","ShitTok","OnlyFap","Library"};
        for(int i=0;i<4;i++) nav.getMenu().add(0,ids[i],i,labels[i]).setIcon(icons[i]);
        nav.getMenu().findItem(ids[selected]).setChecked(true);
        nav.setPagerPosition(selected);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1, BrowseUi.dp(activity,64));
        params.setMargins(BrowseUi.dp(activity,10), -BrowseUi.dp(activity,70), BrowseUi.dp(activity,10), BrowseUi.dp(activity,6));
        shell.addView(nav,params);
        StableBottomNavigationController.styleBar(nav);
        nav.reapplyCurrentGeometry();
        shell.dispatchApplyWindowInsets(new WindowInsets.Builder().setInsets(WindowInsets.Type.navigationBars(), Insets.of(0,0,0,24)).build());
        return shell;
    }
    static Bitmap artwork() {
        Bitmap art = Bitmap.createBitmap(600,800,Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(art); Paint paint = new Paint(3);
        paint.setShader(new LinearGradient(0,0,600,800,new int[]{0xFFE9EEF1,0xFF557B91,0xFF152632,0xFF6B3733},null,Shader.TileMode.CLAMP));
        c.drawPaint(paint); paint.setShader(null);
        for(int i=0;i<12;i++){paint.setColor(i%2==0?0x7759AFD4:0x774D2525);c.drawRect(i*60-60,0,i*60-27,800,paint);}
        paint.setColor(0xFFF4CFA0);c.drawCircle(420,240,125,paint);
        paint.setColor(0xFF101C26);c.drawRect(70,430,310,760,paint);
        return art;
    }
    private java.util.List<NativeContentItem> items(String kind) {
        java.util.List<NativeContentItem> rows=new ArrayList<>();
        for(int i=0;i<5;i++) rows.add(new NativeContentItem(kind,"After Hours "+(i+1),"https://example.invalid/series/"+i,"","","",""));
        return rows;
    }
    private void save(View root,String name) throws Exception {save(root,name,411,891);}
    private void save(View root,String name,int width,int height) throws Exception {
        int w=BrowseUi.dp(activity,width),h=BrowseUi.dp(activity,height);
        for(int i=0;i<2;i++){root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));root.layout(0,0,w,h);root.forceLayout();}
        Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);root.draw(new Canvas(bitmap));
        File dir=new File("build/reports/visual-tests");dir.mkdirs();
        try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
    }
}
