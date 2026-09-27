package com.webapp.crazyshit.baselineprofile

import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
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
    val deadline = SystemClock.uptimeMillis() + 20_000L

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
            val notNow = device.findObject(By.text("Not now"))
            if (notNow != null) {
                notNow.click()
                device.waitForIdle(250)
            } else {
                SystemClock.sleep(100L)
            }
            continue
        }

        if (device.findObject(By.desc("ShitTok tab")) != null) return
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

internal fun MacrobenchmarkScope.scrollShows() {
    val shows = checkNotNull(device.wait(Until.findObject(By.desc("Shows tab")), 8_000)) {
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
    val shitTok = checkNotNull(device.wait(Until.findObject(By.desc("ShitTok tab")), 8_000)) {
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
    val search = checkNotNull(device.wait(Until.findObject(By.desc("Search")), 10_000)) {
        "Search action was not reachable"
    }
    search.click()
    val field = checkNotNull(
        device.wait(Until.findObject(By.desc("Search creators, albums and videos")), 5_000)
    ) {
        "Search field did not open"
    }
    field.click()
    field.text = "mia"
    device.pressEnter()
    checkNotNull(device.wait(Until.findObject(By.textContains("Search complete")), 15_000)) {
        "Search did not complete"
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
    val onlyFap = checkNotNull(device.wait(Until.findObject(By.desc("OnlyFap tab")), 8_000)) {
        "OnlyFap tab was not reachable"
    }
    onlyFap.click()
    device.waitForIdle()

    val creator = checkNotNull(
        device.wait(Until.findObject(By.descContains("gallery")), 30_000)
    ) {
        "OnlyFap creator gallery entry did not become available"
    }
    creator.click()

    checkNotNull(device.wait(Until.findObject(By.textStartsWith("All")), 20_000)) {
        "Creator gallery did not open"
    }
    val videos = checkNotNull(
        device.wait(Until.findObject(By.textStartsWith("Videos")), 10_000)
    ) {
        "Creator gallery Videos tab was not reachable"
    }
    videos.click()

    val video = checkNotNull(
        device.wait(Until.findObject(By.descStartsWith("Video,")), 20_000)
    ) {
        "Creator gallery did not expose a video"
    }
    video.click()
    checkNotNull(device.wait(Until.findObject(By.desc("Play or pause video")), 15_000)) {
        "Creator video player did not become ready"
    }
}
