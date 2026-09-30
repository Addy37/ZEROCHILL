package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** One account-scoped foreground poll. No push service or realtime subscription. */
final class SocialActivityCoordinator {
    interface Loader {
        void load(Context context, ZeroChillSocialRepository.Callback<ArrayList<ZeroChillSocialRepository.SocialActivity>> callback);
    }
    interface Session { String current(Context context); }
    interface Presenter { void show(Activity activity, UpdateInboxStore.Entry entry); void hide(); }

    private static final long POLL_MS = 12_000L;
    private static SocialActivityCoordinator instance;
    private final Context context;
    private final Loader loader;
    private final Session session;
    private final Presenter presenter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LinkedHashSet<String> seen = new LinkedHashSet<>();
    private WeakReference<Activity> host = new WeakReference<>(null);
    private String account = "";
    private boolean baselined;
    private long newestSeenTimestamp;
    private boolean loading;
    private long lastPoll = -POLL_MS;
    private int generation;
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (host.get() == null) return;
            refresh();
            handler.postDelayed(this, POLL_MS);
        }
    };

    SocialActivityCoordinator(Context context, Loader loader, Session session, Presenter presenter) {
        this.context = context.getApplicationContext();
        this.loader = loader;
        this.session = session;
        this.presenter = presenter;
    }

    private static SocialActivityCoordinator get(Context context) {
        if (instance == null) instance = new SocialActivityCoordinator(context,
                ZeroChillSocialRepository::loadCommentActivity,
                ZeroChillSessionStore::currentUserId, new SocialActivityBanner());
        return instance;
    }

    static void onResumed(Activity activity) {
        if (eligible(activity)) get(activity).resume(activity);
    }
    static void onPaused(Activity activity) {
        if (instance != null) instance.pause(activity);
    }
    static void requestRefresh(Activity activity) {
        get(activity).refresh();
    }
    static boolean canNavigate(Activity activity) { return instance == null || instance.host.get() == activity; }
    static boolean isResumed(Activity activity) {
        return instance != null && instance.host.get() == activity;
    }

    private static boolean eligible(Activity activity) {
        return activity instanceof NativeMainActivity || activity instanceof MainActivity
                || activity instanceof VideoDetailActivity || activity instanceof BunkrGalleryActivity
                || activity instanceof PlayerActivity || activity instanceof UpdateInboxActivity;
    }

    void resume(Activity activity) {
        host = new WeakReference<>(activity);
        handler.removeCallbacks(poll);
        handler.post(poll);
    }

    void pause(Activity activity) {
        if (host.get() != activity) return;
        host.clear();
        presenter.hide();
        handler.removeCallbacks(poll);
        // In-flight read may still fill history, but cannot display while no host is resumed.
    }

    void refresh() {
        Activity activity = host.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        String current = session.current(context);
        if (current == null) current = "";
        if (!account.equals(current)) {
            generation++;
            loading = false;
            lastPoll = -POLL_MS;
            account = current;
            baselined = false;
            newestSeenTimestamp = 0L;
            seen.clear();
            presenter.hide();
        }
        if (account.isEmpty() || loading || SystemClock.elapsedRealtime() - lastPoll < POLL_MS) return;
        ZeroChillNotificationPreferences.refreshIfNeeded(context);
        final String requestedAccount = account;
        final int requestGeneration = generation;
        loading = true;
        lastPoll = SystemClock.elapsedRealtime();
        loader.load(context, (activityItems, error) -> handler.post(() -> {
            if (requestGeneration != generation) return;
            loading = false;
            if (!requestedAccount.equals(session.current(context))) {
                refresh();
                return;
            }
            if (error != null || activityItems == null) return;
            List<UpdateInboxStore.Entry> inserted = UpdateInboxStore.recordSocialActivities(context, requestedAccount, activityItems);
            boolean announce = baselined;
            baselined = true;
            UpdateInboxStore.Entry latest = null;
            long newestInPoll = newestSeenTimestamp;
            for (ZeroChillSocialRepository.SocialActivity event : activityItems) {
                String key = requestedAccount + "|" + event.eventId;
                boolean first = seen.add(key);
                long eventTime = timestamp(event.createdAt);
                newestInPoll = Math.max(newestInPoll, eventTime);
                // The bounded history can evict older events still returned by the 30-day query.
                // A backfill must not become a fresh banner when its ID falls out of the seen set.
                if (!announce || !first || eventTime < newestSeenTimestamp) continue;
                for (UpdateInboxStore.Entry entry : inserted) {
                    // Each inserted event has a stable account/event fingerprint, regardless of read state.
                    if (entry.commentId.equals(event.commentId)
                            && entry.socialType.equals(event.type)
                            && entry.timestamp == timestamp(event.createdAt)
                            && (latest == null || entry.timestamp > latest.timestamp)) latest = entry;
                }
            }
            newestSeenTimestamp = newestInPoll;
            while (seen.size() > 1000) seen.remove(seen.iterator().next());
            Activity resumed = host.get();
            if (latest == null || resumed == null || resumed instanceof UpdateInboxActivity
                    || resumed.isFinishing() || resumed.isDestroyed()
                    || InlineCommentsDialog.isOpenFor(resumed, latest.pageUrl)) return;
            android.view.View focus = resumed.getCurrentFocus();
            if (focus instanceof android.widget.EditText) return;
            if (!ZeroChillNotificationPreferences.cachedForAccount(context, requestedAccount).allowsSocial(latest.socialType)) return;
            presenter.show(resumed, latest);
        }));
    }

    private static long timestamp(String value) {
        try { return java.time.Instant.parse(value).toEpochMilli(); }
        catch (Exception ignored) { return 0L; }
    }
}

