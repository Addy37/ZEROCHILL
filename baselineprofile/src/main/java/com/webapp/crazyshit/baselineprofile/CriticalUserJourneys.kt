package com.webapp.crazyshit.baselineprofile

import android.os.SystemClock
import android.util.Log
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import java.io.ByteArrayOutputStream
import java.util.regex.Pattern

internal const val TARGET_PACKAGE = "com.addy37.crazyshitunofficial"

internal fun MacrobenchmarkScope.launchApp() {
    pressHome()
    startActivityAndWait()
    completeStartupWizardIfPresent()
    awaitMainNavigation()
    device.waitForIdle()
}

private fun MacrobenchmarkScope.completeStartupWizardIfPresent() {
    val getStarted = device.wait(Until.findObject(By.text("GET STARTED")), 1_500) ?: return
    getStarted.click()

    val age = checkNotNull(
        device.wait(Until.findObject(By.text("I confirm that I am 18 or older.")), 4_000)
    ) {
        "Startup wizard age confirmation did not appear"
    }
    if (!age.isChecked) age.click()
    device.waitForIdle(150)
    checkNotNull(device.findObject(By.text("CONTINUE"))) {
        "Startup wizard age Continue action was not enabled"
    }.click()

    checkNotNull(device.wait(Until.findObject(By.text("Meet ZEROCHILL")), 4_000)) {
        "Startup wizard experience page did not appear"
    }
    checkNotNull(device.findObject(By.text("CONTINUE"))).click()

    checkNotNull(device.wait(Until.findObject(By.text("Built around gestures")), 4_000)) {
        "Startup wizard controls page did not appear"
    }
    checkNotNull(device.findObject(By.text("CONTINUE"))).click()

    checkNotNull(device.wait(Until.findObject(By.text("Make it yours")), 4_000)) {
        "Startup wizard settings page did not appear"
    }

    for (description in listOf("Favorite creator alerts", "ZEROCHILL update alerts")) {
        val toggle = device.findObject(By.desc(description))
        if (toggle != null && toggle.isChecked) toggle.click()
    }

    checkNotNull(device.findObject(By.text("ENTER ZEROCHILL"))) {
        "Startup wizard final action did not appear"
    }.click()
    device.waitForIdle(350)
}

private fun MacrobenchmarkScope.awaitMainNavigation() {
    val noticePattern = Pattern.compile("(?i).*before\\s+you\\s+continue.*")
    val acceptPattern = Pattern.compile("(?i).*understand.*")
    val notNowPattern = Pattern.compile("(?i).*not\\s+now.*")
    val deadline = SystemClock.uptimeMillis() + 30_000L

    while (SystemClock.uptimeMillis() < deadline) {
        val notice = device.findObject(By.text(noticePattern))
        if (notice != null) {
            val accept = device.findObject(By.text(acceptPattern))
            if (accept != null) {
                accept.click()
                device.waitForIdle(250)
            } else {
                SystemClock.sleep(100L)
            }
            continue
        }

        val notificationEducation = device.findObject(By.text("ZEROCHILL APP ALERTS"))
        if (notificationEducation != null) {
            val notNow = device.findObject(By.text(notNowPattern))
            if (notNow != null) {
                notNow.click()
                device.waitForIdle(250)
            } else {
                SystemClock.sleep(100L)
            }
            continue
        }

        if (
            findPrimaryTab("ShitTok tab", "ShitTok") != null ||
            findPrimaryTab("Shows tab", "Shows") != null ||
            findPrimaryTab("OnlyFap tab", "OnlyFap") != null ||
            findPrimaryTab("Library tab", "Library") != null ||
            device.findObject(By.desc("Primary top bar")) != null
        ) return
        SystemClock.sleep(100L)
    }

    if (device.findObject(By.text(noticePattern)) != null) {
        error("Access notice did not clear")
    }
    if (device.findObject(By.text("ZEROCHILL APP ALERTS")) != null) {
        error("Notification education did not clear")
    }
    error("Main navigation shell did not become ready")
}

private fun MacrobenchmarkScope.findPrimaryTab(description: String, label: String) =
    device.findObject(By.desc(description))
        ?: (if (label == "ShitTok") device.findObject(By.desc("ShitTok featured tab")) else null)
        ?: device.findObject(By.desc(label))
        ?: device.findObject(By.text(label))

private fun MacrobenchmarkScope.awaitPrimaryTab(
    description: String,
    label: String,
    timeoutMs: Long
): androidx.test.uiautomator.UiObject2? {
    val deadline = SystemClock.uptimeMillis() + timeoutMs
    while (SystemClock.uptimeMillis() < deadline) {
        val tab = findPrimaryTab(description, label)
        if (tab != null) return tab
        SystemClock.sleep(100L)
    }
    return null
}

internal fun MacrobenchmarkScope.scrollShows() {
    val shows = checkNotNull(awaitPrimaryTab("Shows tab", "Shows", 8_000L)) {
        "Shows tab was not reachable"
    }
    shows.click()
    device.waitForIdle()
    repeat(3) {
        swipeUp()
        device.waitForIdle(250)
    }
}

internal fun MacrobenchmarkScope.openAndScrollChaos(swipes: Int = 5) {
    val shitTok = checkNotNull(awaitPrimaryTab("ShitTok tab", "ShitTok", 8_000L)) {
        "ShitTok tab was not reachable"
    }
    shitTok.click()
    device.waitForIdle()
    checkNotNull(device.wait(Until.findObject(By.desc("Play or pause video")), 12_000)) {
        "ShitTok player did not become ready"
    }
    repeat(swipes) {
        swipeUp()
        device.waitForIdle(350)
    }
}

internal fun MacrobenchmarkScope.switchRetainedTabs() {
    repeat(5) {
        for (label in listOf("Shows", "OnlyFap", "Library", "ShitTok")) {
            checkNotNull(awaitPrimaryTab("$label tab", label, 8_000L)) {
                "$label tab was not reachable during repeated navigation"
            }.click()
            device.waitForIdle(350)
            check(device.currentPackageName == TARGET_PACKAGE) {
                "App left the foreground after selecting $label"
            }
        }
    }
}

internal fun MacrobenchmarkScope.browseChaosLongSession() {
    openAndScrollChaos(swipes = 0)
    // Fifty paced swipes over ten minutes, followed by a rapid swipe burst.
    // Sample process memory at fixed milestones, outside the swipe itself.
    for (swipe in 0..50) {
        if (swipe in listOf(0, 5, 10, 25, 50)) {
            Log.i("ZeroChillBenchmark", "MEMORY_SWIPE_$swipe\n" +
                device.executeShellCommand("dumpsys meminfo $TARGET_PACKAGE"))
        }
        if (swipe == 50) break
        swipeUp()
        SystemClock.sleep(12_000L)
        check(device.currentPackageName == TARGET_PACKAGE) {
            "App left the foreground at long-session swipe $swipe"
        }
    }
    repeat(15) { swipeUp() }
    device.waitForIdle(350)
    pressHome()
    SystemClock.sleep(1_000L)
    // Resume the retained player without requiring navigation chrome: ShitTok
    // fullscreen and clear-display modes intentionally hide the main shell.
    startActivityAndWait()
    check(device.currentPackageName == TARGET_PACKAGE) {
        "App did not return to the foreground after long-session browsing"
    }
    checkNotNull(device.wait(Until.findObject(By.desc("Play or pause video")), 12_000)) {
        "ShitTok controls did not return after background/foreground"
    }
    Log.i("ZeroChillBenchmark", "MEMORY_AFTER_RESUME\n" +
        device.executeShellCommand("dumpsys meminfo $TARGET_PACKAGE"))
    Log.i("ZeroChillBenchmark", "THERMAL_OBSERVATION\n" +
        device.executeShellCommand("dumpsys thermalservice"))
}

internal fun MacrobenchmarkScope.search() {
    // Route through Shows so ShitTok's optional clear-display chrome cannot hide search.
    val shows = checkNotNull(awaitPrimaryTab("Shows tab", "Shows", 8_000L)) {
        "Shows tab was not reachable before search"
    }
    shows.click()
    device.waitForIdle()

    val search = device.wait(Until.findObject(By.desc("Search")), 1_500)
    if (search != null) {
        search.click()
    } else {
        // The top bar itself is an accessibility node, which can hide its programmatic
        // ImageView children from UIAutomator. Use the stable top-bar geometry as fallback.
        val topBar = checkNotNull(
            device.wait(Until.findObject(By.desc("Primary top bar")), 5_000)
        ) {
            "Primary top bar was not reachable before search"
        }
        val bounds = topBar.visibleBounds
        val density = bounds.height().coerceAtLeast(1) / 56f
        val searchX = (bounds.right - (80f * density)).toInt()
            .coerceIn(bounds.left + 1, bounds.right - 1)
        device.click(searchX, bounds.centerY())
    }
    val field = checkNotNull(
        device.wait(Until.findObject(By.desc("Search creators, albums and videos")), 5_000)
    ) {
        "Search field did not open"
    }
    field.click()
    field.text = "mia"
    device.pressEnter()
    val terminalSearchState = Pattern.compile("(?i)(Search complete|Unavailable:.*)")
    checkNotNull(
        device.wait(Until.findObject(By.text(terminalSearchState)), 15_000)
            ?: device.wait(Until.findObject(By.desc(terminalSearchState)), 1_000)
    ) {
        "Search did not reach a terminal state"
    }
    swipeUp()
    device.waitForIdle()
}

private fun MacrobenchmarkScope.swipeUp() {
    val width = device.displayWidth
    val height = device.displayHeight
    device.swipe(
        width / 2,
        (height * 0.78f).toInt(),
        width / 2,
        (height * 0.24f).toInt(),
        18
    )
}

internal fun MacrobenchmarkScope.openCreatorProfileAndGallery() {
    openOnlyFapHub()

    val creatorPattern = Pattern.compile("(?i)^Open .+ gallery$")
    val attemptedCreators = mutableSetOf<String>()
    var videoOpened = false
    var shelfExposed = false
    for (attempt in 0 until 5) {
        // The hub resolves its live creator shelves in the background. Wait for an
        // actionable entry or the hub's explicit terminal error, not a fixed delay.
        val creatorDeadline = SystemClock.uptimeMillis() + if (attempt == 0) 90_000L else 15_000L
        var creator: androidx.test.uiautomator.UiObject2? = null
        while (creator == null && SystemClock.uptimeMillis() < creatorDeadline) {
            val candidates = device.findObjects(By.desc(creatorPattern))
                .filter { it.isClickable && it.contentDescription !in attemptedCreators }
            // Shelf cards keep their creator identity while the featured hero rotates.
            creator = candidates.firstOrNull {
                it.visibleBounds.width() < device.displayWidth * 0.7f &&
                    it.contentDescription != "Open featured creator gallery"
            } ?: candidates.firstOrNull()
            if (creator == null && device.findObject(
                    By.text("OnlyFap creators could not load right now.")
                ) != null) {
                error("OnlyFap creator shelves finished without a creator")
            }
            if (creator == null && device.findObject(By.text("Loading creators…")) == null &&
                !shelfExposed) {
                // Shelves start below the 640dp hero. If the hero has no resolved
                // artwork yet, expose a loaded shelf instead of waiting on its image.
                swipeUp()
                shelfExposed = true
            }
            if (creator == null) SystemClock.sleep(200L)
        }
        checkNotNull(creator) {
            val hierarchy = ByteArrayOutputStream().also { device.dumpWindowHierarchy(it) }
                .toString("UTF-8").replace(Regex("\\s+"), " ")
            val appNodes = Regex("<node[^>]*package=\"$TARGET_PACKAGE\"[^>]*>")
                .findAll(hierarchy)
                .map { node ->
                    Regex("(?:text|content-desc)=\"[^\"]+\"")
                        .findAll(node.value)
                        .joinToString(" ") { it.value }
                }
                .filter { it.isNotBlank() }
                .joinToString(" | ")
            "OnlyFap clickable creator gallery entry did not become available. " +
                "Foreground: ${device.currentPackageName}. App nodes: ${appNodes.take(9_000)}"
        }
        attemptedCreators.add(creator.contentDescription ?: "")

        try {
            creator.click()
        } catch (_: androidx.test.uiautomator.StaleObjectException) {
            continue
        }

        if (device.wait(Until.findObject(By.textStartsWith("All")), 12_000) == null) {
            if (findPrimaryTab("OnlyFap tab", "OnlyFap") == null) device.pressBack()
            openOnlyFapHub()
            swipeUp()
            shelfExposed = true
            continue
        }
        val videos = checkNotNull(
            device.wait(Until.findObject(By.textStartsWith("Videos")), 10_000)
        ) {
            "Creator gallery Videos tab was not reachable"
        }
        videos.click()

        // A live featured creator can have pictures only. Try another real creator
        // when this gallery reaches its explicit empty video state.
        val videoDeadline = SystemClock.uptimeMillis() + 40_000L
        var video: androidx.test.uiautomator.UiObject2? = null
        while (video == null && SystemClock.uptimeMillis() < videoDeadline) {
            video = device.findObject(By.descStartsWith("Video,"))
            if (video == null && device.findObject(
                    By.text("No videos were found for this creator.")
                ) != null) break
            if (video == null) SystemClock.sleep(250L)
        }
        if (video != null) {
            video.click()
            videoOpened = true
            break
        }
        device.pressBack()
        openOnlyFapHub()
        // The 640dp hero can cover the other creator shelves.
        swipeUp()
        shelfExposed = true
    }
    check(videoOpened) {
        "No playable creator video was exposed after ${attemptedCreators.size} galleries: " +
            attemptedCreators.joinToString()
    }

    // The grid tap opens BunkrGalleryActivity at the selected video and now starts
    // playback automatically. The viewer chrome hides when the player starts.
    checkNotNull(
        device.wait(Until.findObject(By.descStartsWith("Video,")), 12_000)
    ) {
        "Creator video viewer did not open"
    }
    check(device.wait(Until.gone(By.desc("Download video")), 20_000)) {
        "Creator video autoplay did not start"
    }
}

private fun MacrobenchmarkScope.openOnlyFapHub() {
    repeat(2) {
        val tab = checkNotNull(awaitPrimaryTab("OnlyFap tab", "OnlyFap", 8_000L)) {
            "OnlyFap navigation item was not reachable"
        }
        tab.click()
        if (device.wait(Until.findObject(By.desc("Search OnlyFap creators")), 4_000) != null) {
            return
        }
        // A returned hub may retain a scroll position below its hero search control.
        device.swipe(device.displayWidth / 2, (device.displayHeight * 0.25f).toInt(),
            device.displayWidth / 2, (device.displayHeight * 0.8f).toInt(), 18)
        if (device.wait(Until.findObject(By.desc("Search OnlyFap creators")), 3_000) != null) {
            return
        }
    }
    error("OnlyFap hub did not open after selecting its navigation item")
}
