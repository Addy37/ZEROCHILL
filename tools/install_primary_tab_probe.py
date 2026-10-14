#!/usr/bin/env python3
"""Identical temporary tap-to-settled-draw probe for both CI comparison builds."""
from pathlib import Path
p = Path('app/src/main/java/com/webapp/crazyshit/NativeMainActivity.java')
s = p.read_text()
marker = '    private void showPrimaryPage(int position, boolean smooth) {'
assert s.count(marker) == 1
s = s.replace(marker, '''    private long tabProbeStarted;
    private int tabProbePage;

''' + marker + '''
        tabProbeStarted = android.os.SystemClock.elapsedRealtimeNanos();
        tabProbePage = position;
        long dispatchStarted = tabProbeStarted;''')
marker = '        primaryPager.registerOnPageChangeCallback('
assert s.count(marker) == 1
s = s.replace(marker, '''        primaryPager.getViewTreeObserver().addOnDrawListener(() -> {
            if (tabProbeStarted != 0 &&
                    MainPagerAdapter.pageForPagerPosition(primaryPager.getCurrentItem()) == tabProbePage &&
                    primaryPager.getScrollState() == ViewPager2.SCROLL_STATE_IDLE) {
                android.util.Log.i("ZeroChillTabProbe", "TAB_VISIBLE page=" + tabProbePage +
                        " latencyMs=" + ((android.os.SystemClock.elapsedRealtimeNanos() - tabProbeStarted) / 1e6));
                tabProbeStarted = 0;
            }
        });
''' + marker)
a = s.index('    private void showPrimaryPage(')
b = s.index('    private void showPagerChrome(', a)
end = s.rfind('    }', a, b)
s = s[:end] + '''        android.util.Log.i("ZeroChillTabProbe", "TAP_DISPATCH page=" + position +
                " latencyMs=" + ((android.os.SystemClock.elapsedRealtimeNanos() - dispatchStarted) / 1e6));
''' + s[end:]
p.write_text(s)
