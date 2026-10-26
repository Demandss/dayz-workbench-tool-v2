package com.github.demandss.dayz_workbench_tool.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateCheckerTest {
    @Test
    fun `a higher version is newer and an equal or lower one is not`() {
        assertTrue(UpdateChecker.isNewer("26.11.0", "26.10.1"))
        assertTrue(UpdateChecker.isNewer("26.10.2", "26.10.1"))
        assertTrue(UpdateChecker.isNewer("27.0.0", "26.10.1"))
        assertFalse(UpdateChecker.isNewer("26.10.1", "26.10.1"))
        assertFalse(UpdateChecker.isNewer("26.1.7", "26.10.1"))
        assertFalse(UpdateChecker.isNewer("1.2.4", "26.1.0"))
    }

    @Test
    fun `versions are compared as numbers and a missing part is zero`() {
        assertTrue(UpdateChecker.isNewer("26.10.0", "26.9.9"))
        assertFalse(UpdateChecker.isNewer("1.2", "1.2.0"))
        assertTrue(UpdateChecker.isNewer("v1.3", "1.2.9"))
    }

    @Test
    fun `a build that is not a number is never told to update`() {
        assertFalse(UpdateChecker.isNewer("26.10.1", "dev"))
        assertFalse(UpdateChecker.isNewer("nightly", "26.10.1"))
    }

    @Test
    fun `the tag and the page are taken from the answer of GitHub`() {
        val release = UpdateChecker.parse("""{"tag_name":"v26.11.0","html_url":"https://example.org/r","draft":false,"assets":[]}""")
        assertEquals(ReleaseInfo("26.11.0", "https://example.org/r"), release)
    }

    @Test
    fun `an answer without a tag is not a release`() {
        assertNull(UpdateChecker.parse("""{"message":"Not Found"}"""))
        assertEquals(UpdateChecker.RELEASES_URL, UpdateChecker.parse("""{"tag_name":"v1.0.0"}""")?.url)
    }
}
