package com.webapp.crazyshit.baselineprofile

import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import java.io.ByteArrayOutputStream
import java.util.regex.Pattern

internal const val TARGET_PACKAGE = "com.addy37.crazyshitunofficial"

internal fun MacrobenchmarkScope.launchApp() {
    pressHome()
    startActivityAndWait()
    awaitMainNavigation()
    device.waitForIdle()
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

internal fun MacrobenchmarkScope.openAndScrollChaos() {
    val shitTok = checkNotNull(awaitPrimaryTab("ShitTok tab", "ShitTok", 8_000L)) {
        "ShitTok tab was not reachable"
    }
    shitTok.click()
    device.waitForIdle()
    checkNotNull(device.wait(Until.findObject(By.desc("Play or pause video")), 12_000)) {
        "ShitTok player did not become ready"
    }
    repeat(5) {
        swipeUp()
        device.waitForIdle(350)
    }
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
    val onlyFap = checkNotNull(awaitPrimaryTab("OnlyFap tab", "OnlyFap", 8_000L)) {
        "OnlyFap tab was not reachable"
    }
    onlyFap.click()
    device.waitForIdle()

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
            creator = device.findObjects(By.desc(creatorPattern))
                .firstOrNull { it.isClickable && it.contentDescription !in attemptedCreators }
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

        checkNotNull(device.wait(Until.findObject(By.textStartsWith("All")), 12_000)) {
            "Creator gallery did not open for ${creator.contentDescription}"
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
        checkNotNull(awaitPrimaryTab("OnlyFap tab", "OnlyFap", 8_000L)) {
            "Could not return to OnlyFap after a creator without videos"
        }
        // The 640dp hero can cover the other creator shelves.
        swipeUp()
        shelfExposed = true
    }
    check(videoOpened) {
        "No playable creator video was exposed after ${attemptedCreators.size} galleries: " +
            attemptedCreators.joinToString()
    }

    // The grid tap opens BunkrGalleryActivity at the selected video. Playback starts only
    // after tapping that fullscreen page, and this viewer does not use ShitTok's
    // "Play or pause video" accessibility description.
    checkNotNull(
        device.wait(Until.findObject(By.descStartsWith("Video,")), 12_000)
    ) {
        "Creator video viewer did not open"
    }
    checkNotNull(device.wait(Until.findObject(By.desc("Download video")), 8_000)) {
        "Creator video viewer chrome did not become ready"
    }

    // The pager can rebind its page while preloading adjacent media, which invalidates
    // previously returned UiObject2 instances. Tap the stable fullscreen surface directly.
    device.click(device.displayWidth / 2, device.displayHeight / 2)

    check(device.wait(Until.gone(By.desc("Download video")), 20_000)) {
        "Creator video playback did not start"
    }
}
