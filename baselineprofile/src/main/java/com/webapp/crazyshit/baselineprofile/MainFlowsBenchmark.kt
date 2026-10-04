package com.webapp.crazyshit.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class MainFlowsBenchmark {
    private fun playbackMetrics() = listOf(
        FrameTimingMetric(), MemoryUsageMetric(MemoryUsageMetric.Mode.Last),
        TraceSectionMetric("zc.shittok.page_selected", TraceSectionMetric.Mode.Sum),
        TraceSectionMetric("zc.shittok.prepare", TraceSectionMetric.Mode.Sum),
        TraceSectionMetric("zc.shittok.detach", TraceSectionMetric.Mode.Sum),
        TraceSectionMetric("zc.shittok.release", TraceSectionMetric.Mode.Sum),
        TraceSectionMetric("zc.shittok.decoder_release", TraceSectionMetric.Mode.Sum),
        TraceSectionMetric("zc.shittok.batch_fetch", TraceSectionMetric.Mode.Sum),
        TraceSectionMetric("zc.shittok.resolve_bg", TraceSectionMetric.Mode.Sum)
    )
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartupUncompiled() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric(), MemoryUsageMetric(MemoryUsageMetric.Mode.Last)),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.COLD,
        iterations = 5,
        setupBlock = { pressHome() }
    ) {
        launchApp()
    }

    @Test
    fun warmStartup() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric(), MemoryUsageMetric(MemoryUsageMetric.Mode.Last)),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = { launchApp(); pressHome() }
    ) {
        launchApp()
    }

    @Test
    fun retainedTabSwitching() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric(), MemoryUsageMetric(MemoryUsageMetric.Mode.Last)),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.WARM,
        iterations = 3,
        setupBlock = { launchApp() }
    ) {
        switchRetainedTabs()
    }

    @Test
    fun chaosLongSession() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = playbackMetrics(),
        compilationMode = CompilationMode.None(),
        // Retained background/foreground testing must not clear the task on launch.
        startupMode = StartupMode.HOT,
        iterations = 1,
        setupBlock = { launchApp() }
    ) {
        // Framework warmup only needs a ready player. Keep the ten-minute soak
        // in the measured iteration rather than duplicating it before capture.
        if (iteration == null) openAndScrollChaos(swipes = 0)
        else browseChaosLongSession()
    }

    @Test
    fun showsScroll() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric(), MemoryUsageMetric(MemoryUsageMetric.Mode.Last)),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.WARM,
        iterations = 3,
        setupBlock = { launchApp() }
    ) {
        scrollShows()
    }

    @Test
    fun chaosScroll() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = playbackMetrics(),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.WARM,
        iterations = 3,
        setupBlock = { launchApp() }
    ) {
        openAndScrollChaos()
    }

    @Test
    fun searchFlow() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric(), MemoryUsageMetric(MemoryUsageMetric.Mode.Last)),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.WARM,
        iterations = 3,
        setupBlock = { launchApp() }
    ) {
        search()
    }

    @Test
    fun creatorGalleryAndPlayback() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric(), MemoryUsageMetric(MemoryUsageMetric.Mode.Last)),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.WARM,
        iterations = 2,
        setupBlock = { launchApp() }
    ) {
        openCreatorProfileAndGallery()
    }
}
