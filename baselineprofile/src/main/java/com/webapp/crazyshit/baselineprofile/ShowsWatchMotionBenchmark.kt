package com.webapp.crazyshit.baselineprofile

import android.graphics.Rect
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Shows artwork and retained watch Activity, with system animations enabled in CI. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class ShowsWatchMotionBenchmark {
    @get:Rule val benchmark = MacrobenchmarkRule()

    @Test fun openAndReturn() = benchmark.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric(), MemoryUsageMetric(MemoryUsageMetric.Mode.Last)),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.WARM,
        iterations = 3,
        setupBlock = {
            launchApp()
            checkNotNull(device.wait(Until.findObject(By.desc("Shows tab")), 8_000)) {
                "Shows tab was not reachable before watch motion"
            }.click()
            check(device.wait(Until.hasObject(By.res(TARGET_PACKAGE, "shows_weekly_rail")), 25_000)) {
                "Shows did not supply the This Week shelf"
            }
            UiScrollable(UiSelector().className("android.widget.ScrollView"))
                .setAsVerticalList()
                .scrollIntoView(UiSelector().resourceId("$TARGET_PACKAGE:id/shows_weekly_rail"))
            device.waitForIdle(500)
        }
    ) {
        val rail = checkNotNull(device.findObject(By.res(TARGET_PACKAGE, "shows_weekly_rail")))
        val source = checkNotNull(rail.findObject(By.descStartsWith("Open "))) {
            "This Week did not supply a visible watch card"
        }
        val description = source.contentDescription
        val sourceBounds = Rect(source.visibleBounds)
        source.click()
        check(device.wait(Until.hasObject(By.res(TARGET_PACKAGE, "exo_content_frame")), 8_000)) {
            "Shows card did not open the retained watch player"
        }
        device.waitForIdle(500)
        device.pressBack()
        check(device.wait(Until.gone(By.res(TARGET_PACKAGE, "exo_content_frame")), 8_000)) {
            "Shows Back did not finish the watch Activity"
        }
        device.waitForIdle(500)
        val returnedRail = checkNotNull(device.wait(Until.findObject(
            By.res(TARGET_PACKAGE, "shows_weekly_rail")), 4_000))
        val returned = checkNotNull(returnedRail.findObject(By.desc(description))) {
            "Shows Back lost the originating card"
        }
        check(returned.visibleBounds == sourceBounds) {
            "Shows Back moved the source: $sourceBounds -> ${returned.visibleBounds}"
        }
        check(device.currentPackageName == TARGET_PACKAGE) {
            "Shows Back left the application"
        }
    }
}
