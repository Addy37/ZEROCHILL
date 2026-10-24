package com.webapp.crazyshit;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class ShowsCategoryLoaderTest {
    @Test public void inactiveDoesNotStartAndActivationDeduplicatesAndRetainsData() {
        Fixture f = new Fixture();
        f.loader.setActive(false);
        assertEquals(0, f.io.work.size());
        f.loader.setActive(true);
        f.loader.setActive(true);
        assertEquals(1, f.io.work.size());
        f.finish(0);
        assertEquals(1, f.published.size());
        f.loader.setActive(false);
        f.loader.setActive(true);
        assertEquals(1, f.io.work.size());
        assertEquals(1, f.published.size());
    }

    @Test public void departureCancelsQueuedWorkAndRejectsAlreadyPostedCompletion() {
        Fixture f = new Fixture();
        f.loader.setActive(true);
        f.loader.setActive(false);
        assertTrue(((Future<?>) f.io.work.get(0)).isCancelled());
        f.io.work.get(0).run();
        assertEquals(0, f.fetches);
        f.loader.setActive(true);
        f.io.work.get(1).run();
        f.loader.setActive(false);
        f.loader.setActive(true);
        f.main.remove(0).run();
        assertTrue(f.published.isEmpty());
        f.finish(2);
        assertEquals(1, f.published.size());
    }

    @Test public void closeRejectsCompletionAndCannotRestart() {
        Fixture f = new Fixture();
        f.loader.setActive(true);
        f.io.work.get(0).run();
        f.loader.close();
        f.main.remove(0).run();
        f.loader.setActive(true);
        f.loader.invalidate();
        assertTrue(f.published.isEmpty());
        assertEquals(1, f.io.work.size());
    }

    @Test public void explicitRefreshInvalidatesButInactiveRefreshDoesNotStart() {
        Fixture f = new Fixture();
        f.loader.setActive(true);
        f.finish(0);
        f.loader.setActive(false);
        f.loader.invalidate();
        assertEquals(1, f.io.work.size());
        f.loader.setActive(true);
        f.finish(1);
        assertEquals(2, f.published.size());
    }

    @Test public void failedSourceCanRetryOnReturnWithoutPublishingOrLooping() {
        Fixture f = new Fixture();
        f.fail = true;
        f.loader.setActive(true);
        f.finish(0);
        assertTrue(f.published.isEmpty());
        assertEquals(1, f.io.work.size());
        f.loader.setActive(false);
        f.fail = false;
        f.loader.setActive(true);
        f.finish(1);
        assertEquals(1, f.published.size());
    }

    static final class Fixture {
        final ManualIo io = new ManualIo();
        final List<Runnable> main = new ArrayList<>();
        final List<List<NativeContentItem>> published = new ArrayList<>();
        int fetches;
        boolean fail;
        final ShowsCategoryLoader loader = new ShowsCategoryLoader(io, main::add, () -> {
            fetches++;
            if (fail) throw new java.io.IOException("offline fixture");
            return Collections.singletonList(new NativeContentItem(NativeContentItem.KIND_CATEGORY,
                    "Category", "https://example.invalid/category/", "", "", "", ""));
        }, published::add);
        void finish(int index) {
            io.work.get(index).run();
            main.remove(0).run();
        }
    }

    static final class ManualIo extends AbstractExecutorService {
        final List<Runnable> work = new ArrayList<>();
        boolean stopped;
        @Override public void execute(Runnable command) { work.add(command); }
        @Override public void shutdown() { stopped = true; }
        @Override public List<Runnable> shutdownNow() { stopped = true; return Collections.emptyList(); }
        @Override public boolean isShutdown() { return stopped; }
        @Override public boolean isTerminated() { return stopped; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return stopped; }
    }
}
