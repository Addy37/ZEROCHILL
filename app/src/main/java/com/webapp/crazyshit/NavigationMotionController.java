package com.webapp.crazyshit;

/** Owns the bottom bar target. Page changes never alter the requested size. */
final class NavigationMotionController {
    interface Target {
        void setCollapsed(boolean collapsed);
    }

    private final Target target;
    private final int threshold;
    private int activePage;
    private int gesturePage = -1;
    private int accumulated;
    private boolean collapsed;

    NavigationMotionController(Target target, int threshold, int initialPage) {
        this.target = target;
        this.threshold = threshold;
        this.activePage = initialPage;
    }

    void setActivePage(int page) {
        if (activePage == page) return;
        activePage = page;
        gesturePage = -1;
        accumulated = 0;
    }

    void beginGesture(int page) {
        if (page == activePage) gesturePage = page;
    }

    void endGesture() {
        gesturePage = -1;
        accumulated = 0;
    }

    void onScroll(int sourcePage, int dy, boolean atTop, boolean userDriven) {
        if (sourcePage != activePage || !userDriven ||
                (gesturePage != sourcePage && sourcePage != MainPagerAdapter.PAGE_CHAOS)) return;
        if (dy == 0) return;

        if (dy < 0 && atTop) {
            accumulated = 0;
            request(false);
            return;
        }
        if ((dy > 0 && accumulated < 0) || (dy < 0 && accumulated > 0)) accumulated = 0;
        accumulated += dy;
        if (accumulated >= threshold) {
            accumulated = 0;
            request(true);
        } else if (accumulated <= -threshold) {
            accumulated = 0;
            request(false);
        }
    }

    private void request(boolean compact) {
        if (collapsed == compact) return;
        collapsed = compact;
        target.setCollapsed(compact);
    }

    boolean isCollapsed() {
        return collapsed;
    }
}
