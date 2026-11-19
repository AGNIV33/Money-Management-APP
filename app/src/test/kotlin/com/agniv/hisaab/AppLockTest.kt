package com.agniv.hisaab

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppLockTest {
    @Test
    fun `debug builds always allow screenshots`() {
        assertThat(blockScreenshots(appLockOn = true, debugBuild = true)).isFalse()
        assertThat(blockScreenshots(appLockOn = false, debugBuild = true)).isFalse()
    }

    @Test
    fun `release builds block screenshots only while the app lock is on`() {
        assertThat(blockScreenshots(appLockOn = true, debugBuild = false)).isTrue()
        assertThat(blockScreenshots(appLockOn = false, debugBuild = false)).isFalse()
    }
}
