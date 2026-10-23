package com.webapp.crazyshit;

import android.app.Activity;
import android.os.*;
import android.view.View;
import android.widget.*;
import java.lang.reflect.Field;
import java.util.concurrent.*;

/** Device fixture: real Library views/queue with a deliberately blocked local-data worker. */
public final class LibraryLifecycleProbeActivity extends Activity {
    private LibraryHubView library;
    private PrimaryTabWorkQueue queue;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView destination;
    private long deadline;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout shell = new LinearLayout(this); shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(0xFF000000);
        TextView heading = new TextView(this); heading.setText("Library lifecycle check"); heading.setTextColor(-1);
        shell.addView(heading);
        FrameLayout pages = new FrameLayout(this); shell.addView(pages, new LinearLayout.LayoutParams(-1, 0, 1));
        library = new LibraryHubView(this, null, true); pages.addView(library);
        destination = new TextView(this); destination.setText("Shows destination"); destination.setTextSize(24);
        destination.setTextColor(-1); destination.setVisibility(View.GONE); pages.addView(destination);
        setContentView(shell);
        queue = new PrimaryTabWorkQueue(shell); queue.select(4);
        library.setWorkScheduler((key, work) -> queue.submit(4, key, work));
        try {
            ExecutorService reader = (ExecutorService) field("refreshIo");
            reader.submit(() -> { try { Thread.sleep(2200); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } });
        } catch (Exception e) { throw new IllegalStateException(e); }
        library.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                library.getViewTreeObserver().removeOnPreDrawListener(this);
                library.post(() -> {
            try {
                check(library.findViewWithTag("library-loading-label") != null, "blank initial shell");
                check(((View) field("socialHub")).getHeight() > 0, "missing first-frame profile shell");
                android.util.Log.i("ZeroChillLibrary", "first frame has header and loading shell before activation/data");
                queue.submit(4, "activation", () -> library.setSocialActive(true));
                queue.setSettled(true);
                handler.postDelayed(LibraryLifecycleProbeActivity.this::depart, 400);
            } catch (Exception error) { fail(error); }
                });
                return true;
            }
        });
    }
    private void depart() {
        try {
            long start = SystemClock.uptimeMillis();
            queue.setSettled(false); library.setSocialActive(false); queue.select(1);
            library.setVisibility(View.GONE); destination.setVisibility(View.VISIBLE); queue.setSettled(true);
            for (RenderedThumbnailResolver resolver : (RenderedThumbnailResolver[]) field("thumbnailResolvers")) {
                Field active = RenderedThumbnailResolver.class.getDeclaredField("active"); active.setAccessible(true);
                check(!active.getBoolean(resolver), "thumbnail repair continued after departure");
            }
            destination.postOnAnimation(() -> {
                long delay = SystemClock.uptimeMillis() - start;
                check(destination.getVisibility() == View.VISIBLE, "exit did not show destination");
                check(delay < 1500, "exit blocked behind local reads: " + delay);
                android.util.Log.i("ZeroChillLibrary", "destination frame after departure=" + delay + "ms (fixture only)");
                handler.postDelayed(this::reenter, 300);
            });
        } catch (Exception error) { fail(error); }
    }
    private void reenter() {
        queue.setSettled(false); queue.select(4);
        destination.setVisibility(View.GONE); library.setVisibility(View.VISIBLE);
        queue.submit(4, "activation", () -> library.setSocialActive(true)); queue.setSettled(true);
        deadline = SystemClock.uptimeMillis() + 15000;
        handler.postDelayed(this::awaitContent, 100);
    }
    private void awaitContent() {
        try {
            if (field("displayed") != null) {
                check(!(boolean) field("loading"), "loading never reset");
                check(library.findViewWithTag("library-loading-label") == null, "placeholder never retired");
                android.util.Log.i("ZeroChillLibrary", "PASS first-frame shell, blocked-worker exit, resolver pause, return and finite publication");
                return;
            }
            check(SystemClock.uptimeMillis() < deadline, "Library stuck after returning");
            handler.postDelayed(this::awaitContent, 100);
        } catch (Exception error) { fail(error); }
    }
    private Object field(String name) throws Exception {
        Field f = LibraryHubView.class.getDeclaredField(name); f.setAccessible(true); return f.get(library);
    }
    private void check(boolean ok, String message) { if (!ok) throw new IllegalStateException(message); }
    private void fail(Exception error) { android.util.Log.e("ZeroChillLibrary", "FAIL", error); throw new IllegalStateException(error); }
    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (queue != null) queue.close();
        if (library != null) library.close();
        super.onDestroy();
    }
}
