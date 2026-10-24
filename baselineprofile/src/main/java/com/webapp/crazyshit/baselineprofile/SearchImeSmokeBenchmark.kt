package com.webapp.crazyshit.baselineprofile

import android.os.SystemClock
import androidx.benchmark.macro.*
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** PR #302 smoke only. The full release benchmark flows remain unchanged. */
@RunWith(AndroidJUnit4::class)
class SearchImeSmokeBenchmark {
    @get:Rule val benchmark = MacrobenchmarkRule()

    @Test fun openImeScrollCollapse() = benchmark.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.HOT,
        iterations = 3,
        setupBlock = {
            launchApp()
            checkNotNull(device.findObject(By.desc("Shows tab"))).click()
            device.waitForIdle(500)
        }
    ) {
        val search = device.findObject(By.desc("Search"))
        if (search != null) search.click() else {
            val bar = checkNotNull(device.findObject(By.desc("Primary top bar")))
            val bounds = bar.visibleBounds
            device.click((bounds.right - bounds.height() * 80 / 56), bounds.centerY())
        }
        checkNotNull(device.wait(Until.findObject(By.desc("Search Shows")), 3_000))
        checkNotNull(device.wait(Until.findObject(By.desc("OnlyFap search")), 2_000)).click()
        val creatorField = checkNotNull(device.wait(Until.findObject(
            By.desc("Search OnlyFap creators")), 2_000))
        // The real pill owns keyboard timing. Keep it focused and do not submit.
        creatorField.text = "an"
        val deadline = SystemClock.uptimeMillis() + 4_000
        var imeShown = false
        while (!imeShown && SystemClock.uptimeMillis() < deadline) {
            imeShown = device.executeShellCommand("dumpsys input_method")
                .contains(Regex("(?:mInputShown|isInputViewShown)=true"))
            if (!imeShown) SystemClock.sleep(100)
        }
        check(imeShown) { "Search keyboard did not become visible" }
        val list = checkNotNull(device.wait(Until.findObject(By.scrollable(true)
            .clazz("androidx.recyclerview.widget.RecyclerView")), 3_000)) {
            "Local creator results did not provide a scrollable pane"
        }
        val bounds = list.visibleBounds
        device.swipe(bounds.centerX(), bounds.bottom - 24, bounds.centerX(), bounds.top + 24, 12)
        SystemClock.sleep(250)
        device.pressBack() // IME
        SystemClock.sleep(350)
        device.pressBack() // collapse
        check(device.wait(Until.gone(By.desc("Search OnlyFap creators")), 2_000))
    }
}
