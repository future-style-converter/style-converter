package com.styleconverter.test.screenshot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for the TITAN inbox pure helpers — the two pieces of the Android
 * poll loop testable WITHOUT an emulator/Robolectric:
 *   1. the activation-flag parse (isInboxModeRequested), and
 *   2. the poll ordering (pickOldest, oldest-first with a name tiebreak).
 *
 * Plain junit:4.13.2 (app/build.gradle testImplementation), no Android runtime.
 */
class TitanInboxTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // ── isInboxModeRequested — activation-flag parse ────────────────────────

    @Test
    fun booleanExtraTrueEnablesInboxMode() {
        // `--ez titanInbox true` → boolean extra true. The documented transport.
        assertTrue(TitanInbox.isInboxModeRequested(boolExtra = true, stringExtra = null))
    }

    @Test
    fun stringExtraTrueEnablesInboxMode() {
        // `--es titanInbox true` → string "true" (case/space-insensitive).
        assertTrue(TitanInbox.isInboxModeRequested(false, "true"))
        assertTrue(TitanInbox.isInboxModeRequested(false, "  TRUE  "))
        assertTrue(TitanInbox.isInboxModeRequested(false, "True"))
    }

    @Test
    fun absentOrFalsyKeepsBundledMode() {
        // Default launch (no extras) and explicit falsy values stay bundled.
        assertFalse(TitanInbox.isInboxModeRequested(false, null))
        assertFalse(TitanInbox.isInboxModeRequested(false, ""))
        assertFalse(TitanInbox.isInboxModeRequested(false, "false"))
        assertFalse(TitanInbox.isInboxModeRequested(false, "0"))
        assertFalse(TitanInbox.isInboxModeRequested(false, "yes")) // only literal "true" counts
    }

    // ── isComposedModeRequested — composed sub-flag parse ───────────────────

    @Test
    fun composedModeParseMatchesInboxParse() {
        // Same two-spelling contract as inbox mode: `--ez` boolean OR `--es "true"`.
        assertTrue(TitanInbox.isComposedModeRequested(boolExtra = true, stringExtra = null))
        assertTrue(TitanInbox.isComposedModeRequested(false, "true"))
        assertTrue(TitanInbox.isComposedModeRequested(false, "  TRUE  "))
        // Absent / falsy ⇒ per-component inbox capture (composed off).
        assertFalse(TitanInbox.isComposedModeRequested(false, null))
        assertFalse(TitanInbox.isComposedModeRequested(false, "false"))
        assertFalse(TitanInbox.isComposedModeRequested(false, "0"))
    }

    // ── composedTestKey — recover the WPT key from the inbox filename ────────

    @Test
    fun composedTestKeyStripsFeederPrefixAndExtension() {
        // Feeder pushes `<NNNN>-<testKey>.json`; we recover `<testKey>`.
        assertEquals(
            "wpt__css-color__background-color-hsl-001",
            TitanInbox.composedTestKey("0000-wpt__css-color__background-color-hsl-001.json"),
        )
        // Multi-digit index prefix is stripped too.
        assertEquals(
            "wpt__css-backgrounds__background-334",
            TitanInbox.composedTestKey("0123-wpt__css-backgrounds__background-334.json"),
        )
        // No prefix (a manual push) round-trips to the bare stem.
        assertEquals(
            "wpt__css-color__background-color-rgb-001",
            TitanInbox.composedTestKey("wpt__css-color__background-color-rgb-001.json"),
        )
        // The `wpt` key never starts with a digit, so a leading digit in the
        // SECTION (there is none in WPT, but prove the anchor only eats `\d+-`)
        // is preserved once the single index prefix is gone.
        assertEquals("wpt__css-2d__thing", TitanInbox.composedTestKey("7-wpt__css-2d__thing.json"))
    }

    // ── composedPngName — inject-safe() filename in lock-step ────────────────

    @Test
    fun composedPngNameMatchesInjectSafeRule() {
        // WPT keys are already safe → identity + ".png".
        assertEquals(
            "wpt__css-color__background-color-hsl-001.png",
            TitanInbox.composedPngName("wpt__css-color__background-color-hsl-001"),
        )
        // Dots are KEPT (inject's class is [^A-Za-z0-9._-]); spaces/slashes/colons → _.
        assertEquals("a.b_c_d_e.png", TitanInbox.composedPngName("a.b/c d:e"))
    }

    // ── pickOldest — poll ordering ──────────────────────────────────────────

    @Test
    fun nullAndEmptyListingReturnNull() {
        // listFiles() returns null for a missing/unreadable dir → empty inbox.
        assertNull(TitanInbox.pickOldest(null))
        assertNull(TitanInbox.pickOldest(emptyArray()))
    }

    @Test
    fun picksOldestByModificationTime() {
        // Three fixtures written in a scrambled name order but with ascending
        // mtimes — pickOldest must return the earliest mtime regardless of name.
        val a = writeFixture("zebra.json", mtime = 1_000L)
        val b = writeFixture("alpha.json", mtime = 2_000L)
        val c = writeFixture("mango.json", mtime = 3_000L)
        val listing = arrayOf(b, c, a) // deliberately unsorted
        assertEquals(a, TitanInbox.pickOldest(listing))
    }

    @Test
    fun breaksMtimeTiesByNameForDeterminism() {
        // Two files with the SAME mtime (coarse-clock collision) — the tiebreak
        // must be lexicographic name so the result is deterministic, not
        // whatever order the filesystem happened to list.
        val bbb = writeFixture("bbb.json", mtime = 5_000L)
        val aaa = writeFixture("aaa.json", mtime = 5_000L)
        assertEquals(aaa, TitanInbox.pickOldest(arrayOf(bbb, aaa)))
        assertEquals(aaa, TitanInbox.pickOldest(arrayOf(aaa, bbb)))
    }

    /** Create a fixture file with a fixed mtime so ordering is reproducible. */
    private fun writeFixture(name: String, mtime: Long): File {
        val f = tmp.newFile(name)
        f.writeText("{}")
        // setLastModified is best-effort; assert it took so a silently-ignoring
        // filesystem fails loudly instead of making the ordering test vacuous.
        assertTrue("filesystem ignored setLastModified", f.setLastModified(mtime))
        return f
    }
}
