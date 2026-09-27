package com.ghostmode.app.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun versionComparison() {
        assertTrue(UpdateChecker.isNewerVersion("0.2.0", "0.1.16"))
        assertTrue(UpdateChecker.isNewerVersion("0.10.0", "0.9.9"))
        assertFalse(UpdateChecker.isNewerVersion("0.2.0", "0.2.0"))
        assertFalse(UpdateChecker.isNewerVersion("0.1.16", "0.2.0"))
        assertTrue(UpdateChecker.isNewerVersion("0.2.0", "0.2.0-beta1"))
        assertFalse(UpdateChecker.isNewerVersion("0.2.0-beta1", "0.2.0"))
    }

    @Test
    fun parseRelease_readsTagAndApk() {
        val json = """
            {"tag_name":"v0.3.0","body":"notes","html_url":"https://x/r","draft":false,"prerelease":false,
             "assets":[{"name":"SHA256SUMS.txt","browser_download_url":"https://x/s"},
                       {"name":"GhostMode-v0.3.0.apk","browser_download_url":"https://x/a.apk"}]}
        """.trimIndent()

        val release = UpdateChecker.parseRelease(json)!!

        assertEquals("0.3.0", release.versionName)
        assertEquals("https://x/a.apk", release.apkUrl)
        assertEquals("https://x/r", release.pageUrl)
    }

    @Test
    fun parseRelease_ignoresPrerelease() {
        assertNull(UpdateChecker.parseRelease("""{"tag_name":"v1.0.0","prerelease":true}"""))
    }
}
