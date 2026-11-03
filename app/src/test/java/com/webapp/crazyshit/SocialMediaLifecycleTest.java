package com.webapp.crazyshit;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.*;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.request.Request;
import com.bumptech.glide.request.target.DrawableImageViewTarget;
import com.bumptech.glide.signature.ObjectKey;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = {33, 34, 35}, shadows = {
        SocialMediaLifecycleTest.Sessions.class, SocialMediaLifecycleTest.Accounts.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SocialMediaLifecycleTest {
    private static volatile String owner, login, access;
    private static volatile CountDownLatch authStarted, authRelease;
    @Implements(value = ZeroChillSessionStore.class, isInAndroidSdk = false)
    public static class Sessions {
        @Implementation protected static String read(Context c) {
            return owner.isEmpty() ? "" : "{\"access_token\":\"" + access + "\"}";
        }
        @Implementation protected static String currentUserId(Context c) { return owner; }
    }
    @Implements(value = ZeroChillAccountRepository.class, isInAndroidSdk = false)
    public static class Accounts {
        @Implementation protected static boolean hasStoredSession(Context c) { return !owner.isEmpty(); }
        @Implementation protected static String accessTokenBlocking(Context c) {
            String captured = access;
            CountDownLatch started = authStarted, release = authRelease;
            if (started != null) {
                authStarted = null;
                started.countDown();
                boolean done = false;
                while (!done) try { release.await(); done = true; } catch (InterruptedException ignored) { }
            }
            return captured;
        }
    }
    private ActivityController<Activity> controller;
    private Activity activity;
    private FrameLayout root;
    private final Map<String, byte[]> bytes = new HashMap<>();
    private final Set<String> held = ConcurrentHashMap.newKeySet();
    private final Map<String, DataFetcher.DataCallback<? super InputStream>> pending = new ConcurrentHashMap<>();
    private final Set<String> denied = ConcurrentHashMap.newKeySet();
    private final List<GlideUrl> fetched = new CopyOnWriteArrayList<>();
    private final List<ActivityController<?>> otherActivities = new ArrayList<>();

    @Before public void setup() throws Exception {
        owner = "alice"; login = "login-1"; access = token(owner, login, "first");
        authStarted = authRelease = null;
        controller = Robolectric.buildActivity(Activity.class).setup().visible();
        activity = controller.get(); activity.setTheme(R.style.Theme_CrazyShit);
        activity.getSharedPreferences("zerochill_account_secure_state", 0).edit()
                .putString("session_value", "test-encrypted").apply();
        root = new FrameLayout(activity); activity.setContentView(root); layout(root);
        for (String name : new String[]{"a.png", "b.jpg", "c.webp"}) {
            Bitmap bitmap = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(name.startsWith("a") ? Color.RED : name.startsWith("b") ? Color.GREEN : Color.BLUE);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(name.endsWith("jpg") ? Bitmap.CompressFormat.JPEG
                    : name.endsWith("webp") ? Bitmap.CompressFormat.WEBP : Bitmap.CompressFormat.PNG, 95, out);
            bitmap.recycle(); bytes.put(name, out.toByteArray());
        }
        bytes.put("animated.gif", getClass().getResourceAsStream("/social-media/animated.gif").readAllBytes());
        Glide.get(activity).getRegistry().prepend(GlideUrl.class, InputStream.class,
                new ModelLoaderFactory<GlideUrl, InputStream>() {
                    @Override public ModelLoader<GlideUrl, InputStream> build(MultiModelLoaderFactory factory) {
                        return new ModelLoader<GlideUrl, InputStream>() {
                            @Override public boolean handles(GlideUrl url) { return true; }
                            @Override public LoadData<InputStream> buildLoadData(GlideUrl url, int w, int h,
                                    com.bumptech.glide.load.Options options) {
                                return new LoadData<>(new ObjectKey(url.toStringUrl()), new DataFetcher<InputStream>() {
                                    @Override public void loadData(Priority priority, DataCallback<? super InputStream> callback) {
                                        fetched.add(url);
                                        if (denied.contains(url.getHeaders().get("Authorization"))) {
                                            callback.onLoadFailed(new IOException("Storage denied this session"));
                                            return;
                                        }
                                        String name = url.toStringUrl().substring(url.toStringUrl().lastIndexOf('/') + 1);
                                        if (held.contains(name)) pending.put(name, callback);
                                        else callback.onDataReady(new ByteArrayInputStream(bytes.get(name)));
                                    }
                                    @Override public void cleanup() { }
                                    @Override public void cancel() { } // Deliberately allow late results.
                                    @Override public Class<InputStream> getDataClass() { return InputStream.class; }
                                    @Override public DataSource getDataSource() { return DataSource.REMOTE; }
                                });
                            }
                        };
                    }
                    @Override public void teardown() { }
                });
    }
    @After public void teardown() throws Exception {
        if (authRelease != null) authRelease.countDown();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        if (dialog != null && dialog.isShowing()) dialog.dismiss(); ShadowLooper.idleMainLooper();
        SocialMedia.WORK.submit(() -> { }).get(5, TimeUnit.SECONDS);
        for (ActivityController<?> c : otherActivities) c.stop().destroy();
        controller.pause().stop().destroy();
        ShadowLooper.idleMainLooper(250, TimeUnit.MILLISECONDS); Glide.tearDown();
    }

    @Test public void unchangedBindingKeepsRequestDrawableAndDoesNotRestartLoading() throws Exception {
        ImageView image = inline("a.png", true); await(() -> ready(image));
        Drawable drawable = image.getDrawable(); Request request = request(image);
        int count = fetched.size();
        for (int i = 0; i < 20; i++) SocialMedia.bind(image, "social-photos", "alice/a.png");
        drain(); assertSame(drawable, image.getDrawable()); assertSame(request, request(image));
        assertEquals(count, fetched.size());
        assertEquals(2f, image.getDrawable().getIntrinsicWidth() / (float) image.getDrawable().getIntrinsicHeight(), .01f);
    }

    @Test public void uncachedViewerHasBoundedViewportAndLoadsAllPhotoFormatsThenReopensFromMemory() throws Exception {
        for (String name : new String[]{"a.png", "b.jpg", "c.webp"}) {
            held.add(name);
            SocialMedia.showViewer(activity, "social-photos", "alice/" + name);
            AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
            ImageView large = viewer(dialog);
            layout(dialog.getWindow().getDecorView());
            assertTrue("Unloaded viewer must have height", large.getMeasuredHeight() > 0);
            await(() -> pending.containsKey(name));
            pending.remove(name).onDataReady(new ByteArrayInputStream(bytes.get(name)));
            await(() -> ready(large));
            assertTrue(large.getDrawable().getIntrinsicWidth() > large.getDrawable().getIntrinsicHeight());
            assertEquals(ImageView.ScaleType.FIT_CENTER, large.getScaleType());
            int count = fetched.size(); dialog.dismiss(); ShadowLooper.idleMainLooper();
            assertTrue(request(large) == null || request(large).isCleared()); assertNull(large.getDrawable());
            held.remove(name);
            for (int i = 0; i < 3; i++) {
                SocialMedia.showViewer(activity, "social-photos", "alice/" + name);
                AlertDialog again = ShadowAlertDialog.getLatestAlertDialog();
                ImageView next = viewer(again); await(() -> ready(next));
                assertEquals(count, fetched.size()); again.dismiss(); ShadowLooper.idleMainLooper();
            }
        }
    }

    @Test public void dmAndCommentGifsAnimateInlineAndInRepeatedViewers() throws Exception {
        for (boolean dm : new boolean[]{true, false}) {
            ImageView image = inline("animated.gif", dm);
            await(() -> image.getDrawable() instanceof GifDrawable && ((GifDrawable) image.getDrawable()).isRunning());
            GifDrawable gif = (GifDrawable) image.getDrawable(); assertTrue(gif.getFrameCount() > 1);
            for (int i = 0; i < 2; i++) {
                image.performClick(); AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
                ImageView large = viewer(dialog);
                await(() -> large.getDrawable() instanceof GifDrawable && ((GifDrawable) large.getDrawable()).isRunning());
                GifDrawable enlarged = (GifDrawable) large.getDrawable();
                assertTrue(enlarged.getFrameCount() > 1);
                dialog.dismiss(); ShadowLooper.idleMainLooper(); assertNull(large.getDrawable());
                assertFalse(enlarged.isRunning()); assertTrue(gif.isRunning());
            }
            SocialMedia.clear(image);
        }
    }

    @Test public void recycledViewIgnoresLateImageAndClearsOldPixelsBeforeReplacement() throws Exception {
        held.add("a.png"); ImageView image = inline("a.png", true);
        await(() -> pending.containsKey("a.png"));
        SocialMedia.bind(image, "social-photos", "alice/b.jpg"); await(() -> ready(image));
        SocialMedia.bind(image, "social-photos", "alice/c.webp");
        assertNull(image.getDrawable()); await(() -> ready(image));
        Drawable selected = image.getDrawable();
        pending.remove("a.png").onDataReady(new ByteArrayInputStream(bytes.get("a.png")));
        drain(); assertSame(selected, image.getDrawable());
        SocialMedia.clear(image); assertNull(image.getDrawable()); assertNull(request(image));
    }

    @Test public void oldTokenPreparationCannotWinAnAToBToABindingRace() throws Exception {
        authStarted = new CountDownLatch(1); authRelease = new CountDownLatch(1);
        CountDownLatch started = authStarted;
        ImageView image = inline("a.png", true); assertTrue(started.await(5, TimeUnit.SECONDS));
        SocialMedia.bind(image, "social-photos", "alice/b.jpg");
        SocialMedia.bind(image, "social-photos", "alice/a.png");
        access = token(owner, login, "refreshed");
        authRelease.countDown(); await(() -> ready(image));
        assertEquals(1, fetched.size());
        assertEquals("Bearer " + access, fetched.get(0).getHeaders().get("Authorization"));
        assertTrue(fetched.get(0).toStringUrl().contains("/storage/v1/object/authenticated/social-photos/alice/a.png"));
    }

    @Test public void sessionChangeClearsDisplayedAndPendingMediaAndDoesNotReusePrivateCache() throws Exception {
        ImageView loaded = inline("a.png", true); await(() -> ready(loaded));
        held.add("b.jpg"); ImageView waiting = inline("b.jpg", true); await(() -> pending.containsKey("b.jpg"));
        owner = "bob"; login = "login-2"; access = token(owner, login, "new"); publishSession(); drain();
        assertNull(loaded.getDrawable()); assertNull(waiting.getDrawable());
        pending.remove("b.jpg").onDataReady(new ByteArrayInputStream(bytes.get("b.jpg"))); drain();
        assertNull(waiting.getDrawable()); int before = fetched.size();
        SocialMedia.bind(loaded, "social-photos", "alice/a.png"); await(() -> ready(loaded));
        assertEquals(before + 1, fetched.size());
        assertEquals("Bearer " + access, fetched.get(fetched.size() - 1).getHeaders().get("Authorization"));
    }

    @Test public void tokenRefreshKeepsImageButLogoutAndSameUserLoginCannotReuseOldScope() throws Exception {
        ImageView image = inline("a.png", true); await(() -> ready(image));
        Drawable shown = image.getDrawable(); Request original = request(image); int before = fetched.size();
        access = token(owner, login, "refresh"); publishSession(); drain();
        SocialMedia.bind(image, "social-photos", "alice/a.png");
        assertSame(shown, image.getDrawable()); assertSame(original, request(image));
        String sameToken = access;
        owner = ""; activity.getSharedPreferences("zerochill_account_secure_state", 0).edit().clear().apply();
        owner = "alice"; access = sameToken; publishSession(); drain();
        assertNull(image.getDrawable());
        SocialMedia.bind(image, "social-photos", "alice/a.png"); await(() -> ready(image));
        assertEquals(before + 1, fetched.size());
    }

    @Test public void dismissedViewerCancelsPendingFetchAndNeverReplacesNextAttachment() throws Exception {
        held.add("a.png"); SocialMedia.showViewer(activity, "social-photos", "alice/a.png");
        AlertDialog first = ShadowAlertDialog.getLatestAlertDialog(); ImageView old = viewer(first);
        await(() -> pending.containsKey("a.png")); first.dismiss(); ShadowLooper.idleMainLooper();
        SocialMedia.showViewer(activity, "social-photos", "alice/c.webp");
        ImageView selected = viewer(ShadowAlertDialog.getLatestAlertDialog()); await(() -> ready(selected));
        Drawable correct = selected.getDrawable();
        pending.remove("a.png").onDataReady(new ByteArrayInputStream(bytes.get("a.png"))); drain();
        assertNull(old.getDrawable()); assertSame(correct, selected.getDrawable());
    }

    @Test public void realDmRefreshRetainsHolderAndScrollingAndConversationReturnUseCorrectMedia() throws Exception {
        android.content.Intent intent = new android.content.Intent(activity, ZeroChillMessageActivity.class)
                .putExtra(ZeroChillMessageActivity.EXTRA_USER_ID, "other");
        ActivityController<ZeroChillMessageActivity> dm = Robolectric.buildActivity(ZeroChillMessageActivity.class, intent)
                .create().start().visible();
        otherActivities.add(dm);
        settleKeyboard(dm.get());
        RecyclerView recycler = ReflectionHelpers.getField(dm.get(), "recycler");
        ((androidx.recyclerview.widget.LinearLayoutManager) recycler.getLayoutManager()).setStackFromEnd(false);
        RecyclerView.Adapter adapter = recycler.getAdapter(); assertTrue(adapter.hasStableIds());
        List<ZeroChillSocialRepository.DirectMessage> messages = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            String name = new String[]{"a.png", "b.jpg", "c.webp"}[i % 3];
            messages.add(new ZeroChillSocialRepository.DirectMessage(new JSONObject()
                    .put("id", "message-" + i).put("sender_id", "other").put("body", "Photo")
                    .put("created_at", "2026-11-02T12:00:00Z")
                    .put("attachment_bucket", "social-photos").put("attachment_path", "alice/" + name)));
        }
        java.lang.reflect.Method replace = adapter.getClass().getDeclaredMethod("replace", List.class);
        replace.setAccessible(true); replace.invoke(adapter, messages);
        recycler.scrollToPosition(0);
        layout(dm.get().getWindow().getDecorView()); ShadowLooper.idleMainLooper();
        RecyclerView.ViewHolder holder = recycler.findViewHolderForAdapterPosition(0); assertNotNull(holder);
        ImageView image = ReflectionHelpers.getField(holder, "media"); await(() -> ready(image));
        Drawable shown = image.getDrawable(); Request original = request(image); long id = adapter.getItemId(0);
        replace.invoke(adapter, new ArrayList<>(messages)); layout(dm.get().getWindow().getDecorView());
        ShadowLooper.idleMainLooper();
        assertSame(holder, recycler.findViewHolderForAdapterPosition(0));
        assertSame(shown, image.getDrawable()); assertSame(original, request(image)); assertEquals(id, adapter.getItemId(0));
        for (int position : new int[]{23, 3, 18, 0}) {
            recycler.scrollToPosition(position); layout(dm.get().getWindow().getDecorView()); ShadowLooper.idleMainLooper();
            await(() -> {
                recycler.scrollToPosition(position); layout(dm.get().getWindow().getDecorView());
                return recycler.findViewHolderForAdapterPosition(position) != null;
            });
            RecyclerView.ViewHolder visible = recycler.findViewHolderForAdapterPosition(position); assertNotNull(visible);
            ImageView media = ReflectionHelpers.getField(visible, "media"); await(() -> ready(media));
            String name = new String[]{"a.png", "b.jpg", "c.webp"}[position % 3];
            Object binding = media.getTag(R.id.social_media_binding);
            assertEquals("alice/" + name, ReflectionHelpers.getField(binding, "path"));
        }
        assertTrue("Repeated rows must use bounded memory cache", fetched.size() <= 3);
        // The actual recycler callback cancels loading and invalidates row click state.
        RecyclerView.ViewHolder recycled = adapter.createViewHolder(recycler, 0);
        adapter.bindViewHolder(recycled, 0); ImageView detached = ReflectionHelpers.getField(recycled, "media");
        adapter.onViewRecycled(recycled); assertNull(detached.getTag(R.id.social_media_binding));
        assertNull(detached.getDrawable()); assertFalse(detached.hasOnClickListeners());
        int count = fetched.size();
        dm.stop().destroy(); otherActivities.remove(dm);
        // Open a different real conversation, then return to the first conversation.
        for (String partner : new String[]{"another", "other"}) {
            ActivityController<ZeroChillMessageActivity> conversation = Robolectric.buildActivity(
                    ZeroChillMessageActivity.class, new android.content.Intent(activity, ZeroChillMessageActivity.class)
                            .putExtra(ZeroChillMessageActivity.EXTRA_USER_ID, partner)).create().start().visible();
            otherActivities.add(conversation);
            settleKeyboard(conversation.get());
            RecyclerView list = ReflectionHelpers.getField(conversation.get(), "recycler");
            ((androidx.recyclerview.widget.LinearLayoutManager) list.getLayoutManager()).setStackFromEnd(false);
            RecyclerView.Adapter rows = list.getAdapter();
            java.lang.reflect.Method publish = rows.getClass().getDeclaredMethod("replace", List.class);
            publish.setAccessible(true);
            String path = partner.equals("another") ? "alice/c.webp" : "alice/a.png";
            publish.invoke(rows, Collections.singletonList(new ZeroChillSocialRepository.DirectMessage(new JSONObject()
                    .put("id", "return-" + partner).put("sender_id", "alice").put("body", "Photo")
                    .put("created_at", "2026-11-02T12:00:00Z").put("attachment_bucket", "social-photos")
                    .put("attachment_path", path))));
            layout(conversation.get().getWindow().getDecorView()); ShadowLooper.idleMainLooper();
            ImageView returned = ReflectionHelpers.getField(list.findViewHolderForAdapterPosition(0), "media");
            await(() -> ready(returned));
            assertEquals(path, ReflectionHelpers.getField(returned.getTag(R.id.social_media_binding), "path"));
            assertEquals(count, fetched.size());
            conversation.stop().destroy(); otherActivities.remove(conversation);
        }
    }

    @Test public void activityDestructionCancelsHeaderPreparationBeforeAnyRequestCanStart() throws Exception {
        authStarted = new CountDownLatch(1); authRelease = new CountDownLatch(1);
        CountDownLatch started = authStarted;
        ImageView image = inline("a.png", true); assertTrue(started.await(5, TimeUnit.SECONDS));
        controller.pause().stop().destroy();
        authRelease.countDown(); drain();
        assertNull(image.getTag(R.id.social_media_binding)); assertNull(image.getDrawable());
        assertEquals(0, fetched.size());
        controller = Robolectric.buildActivity(Activity.class).setup().visible();
    }

    @Test public void privateCacheCannotBypassNewUsersStorageDenialAndFailedBindCanRetry() throws Exception {
        ImageView image = inline("a.png", true); await(() -> ready(image));
        Drawable old = image.getDrawable();
        owner = "bob"; login = "bob-login"; access = token(owner, login, "denied");
        denied.add("Bearer " + access); publishSession(); drain();
        SocialMedia.bind(image, "social-photos", "alice/a.png");
        await(() -> fetched.size() == 2 && request(image) != null && !request(image).isRunning()
                && image.getDrawable() != null);
        assertFalse(ready(image)); assertNotSame(old, image.getDrawable());
        denied.clear(); SocialMedia.bind(image, "social-photos", "alice/a.png");
        await(() -> ready(image)); assertEquals(3, fetched.size());
        java.io.File disk = Glide.getPhotoCacheDir(activity);
        assertTrue("Private attachments must not persist to Glide disk cache",
                disk == null || disk.listFiles() == null || disk.listFiles().length == 0);
    }

    @Test public void commentPhotoStillRendersAndOpensItsOwnViewer() throws Exception {
        ImageView comment = inline("a.png", false); await(() -> ready(comment));
        Request original = request(comment); Drawable shown = comment.getDrawable();
        comment.performClick(); AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        ImageView large = viewer(dialog); await(() -> ready(large));
        assertEquals(2f, large.getDrawable().getIntrinsicWidth() / (float) large.getDrawable().getIntrinsicHeight(), .01f);
        dialog.dismiss(); ShadowLooper.idleMainLooper();
        assertSame(original, request(comment)); assertSame(shown, comment.getDrawable());
    }

    private ImageView inline(String name, boolean dm) {
        ImageView view = SocialMedia.inline(activity, name.endsWith("gif") ? "social-gifs" : "social-photos", "alice/" + name, dm);
        root.addView(view); layout(root); return view;
    }
    private void settleKeyboard(Activity host) {
        View decor = host.getWindow().getDecorView();
        decor.setFocusableInTouchMode(true); decor.requestFocus();
        ShadowLooper.idleMainLooper(200, TimeUnit.MILLISECONDS);
    }
    private void publishSession() {
        activity.getSharedPreferences("zerochill_account_secure_state", 0).edit()
                .putString("session_value", UUID.randomUUID().toString()).apply();
    }
    private static String token(String user, String sid, String suffix) {
        String json = "{\"sub\":\"" + user + "\",\"session_id\":\"" + sid + "\"}";
        return "header." + Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8)) + "." + suffix;
    }
    private Request request(ImageView image) { return new DrawableImageViewTarget(image).getRequest(); }
    private boolean ready(ImageView image) { Request r = request(image); return r != null && r.isComplete() && image.getDrawable() != null; }
    private ImageView viewer(AlertDialog dialog) { return find(dialog.getWindow().getDecorView()); }
    private ImageView find(View view) {
        if (view instanceof ImageView && "Attachment viewer".equals(view.getContentDescription())) return (ImageView) view;
        if (view instanceof ViewGroup) for (int i=0; i<((ViewGroup)view).getChildCount(); i++) {
            ImageView found = find(((ViewGroup)view).getChildAt(i)); if (found != null) return found;
        }
        return null;
    }
    private void layout(View view) {
        int w = view.getResources().getDisplayMetrics().widthPixels;
        int h = view.getResources().getDisplayMetrics().heightPixels;
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        view.layout(0,0,w,h);
    }
    private void await(BooleanSupplier check) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < end) { ShadowLooper.idleMainLooper(); if (check.getAsBoolean()) return; Thread.sleep(10); }
        List<String> state = new ArrayList<>(); describe(root, state);
        fail("Media handoff did not finish: " + state);
    }
    private void describe(View view, List<String> state) {
        if (view instanceof ImageView) {
            ImageView i = (ImageView) view; Drawable d = i.getDrawable();
            state.add("drawable=" + d + " request=" + request(i) + " attached=" + i.isAttachedToWindow()
                    + " binding=" + i.getTag(R.id.social_media_binding)
                    + " visibility=" + i.getVisibility() + " gifRunning=" + (d instanceof GifDrawable && ((GifDrawable)d).isRunning()));
        }
        if (view instanceof ViewGroup) for (int n=0;n<((ViewGroup)view).getChildCount();n++) describe(((ViewGroup)view).getChildAt(n),state);
    }
    private void drain() throws Exception { SocialMedia.WORK.submit(() -> {}).get(5, TimeUnit.SECONDS); ShadowLooper.idleMainLooper(); }
}
