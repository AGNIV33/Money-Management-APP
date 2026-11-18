package com.agniv.hisaab.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE = "com.agniv.hisaab"
private const val WAIT_MS = 5_000L

/**
 * Records which code runs at launch, on Home and on each bottom tab, so it is compiled ahead of time on install
 * (no first-scroll stutter, even at 120 Hz). Works signed in or out: it launches, scrolls whatever list is on screen,
 * and taps the tabs only if they are there.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() =
        rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.scrollable(true)), WAIT_MS)
            device.findObject(By.scrollable(true))?.let { list ->
                list.setGestureMargin(device.displayWidth / 5)
                list.fling(Direction.DOWN)
                device.waitForIdle()
                list.fling(Direction.UP)
            }
            // Signed in: visit every tab once, scrolling each, so their screens are in the profile too.
            TABS.forEach { label ->
                device.findObject(By.text(label))?.let {
                    it.click()
                    device.waitForIdle()
                    device.findObject(By.scrollable(true))?.fling(Direction.DOWN)
                    device.waitForIdle()
                }
            }
            device.findObject(By.text("Home"))?.click()
        }

    private companion object {
        val TABS = listOf("Insights", "Goals", "Assistant", "Settings")
    }
}
