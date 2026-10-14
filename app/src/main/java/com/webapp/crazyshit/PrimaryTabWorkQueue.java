package com.webapp.crazyshit;

import android.os.Handler;
import android.os.Looper;
import android.os.MessageQueue;
import android.view.View;

import java.util.LinkedHashMap;
import java.util.Map;

/** Coalesces retained-page work and runs one task after a settled page has had a frame. */
final class PrimaryTabWorkQueue {
    interface Scheduler { void schedule(String key, Runnable task); }

    private final View anchor;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Entry> pending = new LinkedHashMap<>();
    private int selected = -1;
    private boolean settled;
    private boolean resumed = true;
    private boolean closed;
    private boolean scheduled;
    private final MessageQueue.IdleHandler idle = () -> {
        scheduled = false;
        if (!ready()) return false;
        Entry next = null;
        String key = null;
        for (Map.Entry<String, Entry> entry : pending.entrySet()) {
            if (entry.getValue().page == selected) {
                key = entry.getKey();
                next = entry.getValue();
                break;
            }
        }
        if (key != null) pending.remove(key);
        if (next != null) next.task.run();
        schedule();
        return false;
    };
    private final Runnable enqueueIdle = () -> {
        if (ready()) Looper.myQueue().addIdleHandler(idle);
        else scheduled = false;
    };
    private final Runnable frame = () -> handler.post(enqueueIdle);

    PrimaryTabWorkQueue(View anchor) { this.anchor = anchor; }

    void select(int page) { selected = page; schedule(); }
    void setSettled(boolean value) {
        settled = value;
        if (!value) cancelDispatch();
        else schedule();
    }
    void setResumed(boolean value) {
        resumed = value;
        if (!value) cancelDispatch();
        else schedule();
    }
    void submit(int page, String key, Runnable task) {
        if (closed) return;
        pending.put(page + ":" + key, new Entry(page, task));
        schedule();
    }
    private boolean ready() { return !closed && resumed && settled; }
    private void schedule() {
        if (!ready() || scheduled) return;
        boolean hasWork = false;
        for (Entry entry : pending.values()) if (entry.page == selected) hasWork = true;
        if (!hasWork) return;
        scheduled = true;
        anchor.postOnAnimation(frame);
    }
    private void cancelDispatch() {
        anchor.removeCallbacks(frame);
        handler.removeCallbacks(enqueueIdle);
        Looper.myQueue().removeIdleHandler(idle);
        scheduled = false;
    }
    void close() { closed = true; cancelDispatch(); pending.clear(); }

    private static final class Entry {
        final int page;
        final Runnable task;
        Entry(int page, Runnable task) { this.page = page; this.task = task; }
    }
}
