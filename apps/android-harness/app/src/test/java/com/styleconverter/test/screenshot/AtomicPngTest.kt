package com.styleconverter.test.screenshot

// junit4 (app/build.gradle's plain-JVM suite — the TitanInboxTest idiom):
// these tests run the REAL shipped write path against REAL temp files;
// only the Bitmap.compress encode is a lambda, exactly as ScreenshotManager
// injects it, so the tested code IS the shipped code (no drift twin).
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Pins the wave-48 W1 atomic-publish contract that closes the
 * direction-upright-002 truncated-PNG defect (feed-android's
 * `waitForPngs` treats the final filename's EXISTENCE as
 * capture-complete and pulls ~150 ms later, while the old path streamed
 * `Bitmap.compress` straight into that filename for seconds on a
 * degenerate canvas — both cal-run pulls were cut mid-IDAT).
 *
 * The contract, each clause tested below:
 *  1. the final name NEVER exists while the encode is in flight;
 *  2. a successful encode publishes the COMPLETE bytes under the final
 *     name and leaves no `.part` behind;
 *  3. an encoder `false` (Bitmap.compress's ignored-until-now verdict)
 *     or throw publishes NOTHING — no final file, no partial temp;
 *  4. temp names can never collide with any host-side `.png`
 *     expectation, and stranded temps are recognisable for the
 *     clearScreenshots sweep.
 */
class AtomicPngTest {

    /** Real on-disk directory per test — the FUSE rename semantics this
     *  guards are filesystem behaviour, so the pin uses a filesystem. */
    @get:Rule
    val dir = TemporaryFolder()

    // ── Clause 1+2: the happy path is invisible until complete ─────────

    @Test
    fun `final name absent during encode, complete bytes after`() {
        val final = File(dir.root, "wpt__sec__test.png")
        val payload = ByteArray(4096) { (it % 251).toByte() }
        val ok = AtomicPng.writeAtomically(final, { out ->
            // Mid-encode observation — the exact moment waitForPngs would
            // poll: the final name must NOT exist yet (the old path had
            // already created it with 0..N bytes here).
            out.write(payload, 0, 2048)
            assertFalse("final name visible mid-encode", final.exists())
            out.write(payload, 2048, 2048)
            true
        }, { })
        assertTrue(ok)
        // Published file carries the COMPLETE encode, byte for byte.
        assertEquals(payload.toList(), final.readBytes().toList())
        // No .part residue after a successful publish.
        assertEquals(listOf("wpt__sec__test.png"), dir.root.list()!!.toList())
    }

    // ── Clause 3: encoder verdicts are honored, partials never survive ─

    @Test
    fun `encoder false publishes nothing and cleans the partial`() {
        val final = File(dir.root, "cap.png")
        val ok = AtomicPng.writeAtomically(final, { out ->
            // Truncated write then a false verdict — Bitmap.compress's
            // failure shape (the Boolean the pre-fix path ignored).
            out.write(ByteArray(100))
            false
        }, { })
        assertFalse(ok)
        assertFalse("truncated bytes kept the final name", final.exists())
        assertEquals("partial temp left behind", 0, dir.root.list()!!.size)
    }

    @Test
    fun `encoder throw publishes nothing and cleans the partial`() {
        val final = File(dir.root, "cap.png")
        val logged = mutableListOf<String>()
        val ok = AtomicPng.writeAtomically(final, { out ->
            out.write(ByteArray(100))
            // OOM-mid-encode shape (huge-canvas hazard) — must not leak.
            throw RuntimeException("boom")
        }, { logged.add(it) })
        assertFalse(ok)
        assertFalse(final.exists())
        assertEquals(0, dir.root.list()!!.size)
        // The failure is LOUD (no-silent-fallthrough rule): both the
        // throw and the resulting no-publish are reported.
        assertTrue(logged.any { "threw" in it })
        assertTrue(logged.any { "FAILED" in it })
    }

    // ── Stale-file replacement: re-render of the same fixture ──────────

    @Test
    fun `a stale complete file is replaced by the new encode`() {
        val final = File(dir.root, "cap.png")
        // A previous run's complete capture already holds the name.
        final.writeBytes(ByteArray(10) { 1 })
        val fresh = ByteArray(10) { 2 }
        val ok = AtomicPng.writeAtomically(final, { out -> out.write(fresh); true }, { })
        assertTrue(ok)
        // The publish REPLACED the stale bytes (renameTo-onto-existing is
        // not portable, so the impl clears the target first — pinned).
        assertEquals(fresh.toList(), final.readBytes().toList())
    }

    // ── wave-48 S4: honest logging when the stream never opened ────────

    @Test
    fun `a failed stream open logs no phantom partial`() {
        // S4's executed missing-parent shape: the FileOutputStream open
        // itself throws, so NO temp file was ever created — yet the old
        // unconditional delete-check logged "could not delete partial
        // x.png.<t>.part" about a file that never existed, muddying the
        // failure report. The temp.exists() guard keeps it honest.
        val final = File(File(dir.root, "gone/sub"), "x.png")
        val logged = mutableListOf<String>()
        val ok = AtomicPng.writeAtomically(final, { true }, { logged.add(it) })
        assertFalse(ok)
        // The two REAL failure lines still fire (loud, attributable)...
        assertTrue(logged.any { "threw" in it })
        assertTrue(logged.any { "FAILED" in it })
        // ...but no phantom-partial line about a temp that never existed.
        assertFalse(logged.any { "could not delete partial" in it })
    }

    // ── wave-48 S4: concurrent double-write to one key ─────────────────

    @Test
    fun `concurrent writers of one key never corrupt a published file`() {
        // The EXECUTED S4 shape: two writers race on the SAME final
        // name. Under the old deterministic `<name>.png.part` temp,
        // writer B's FileOutputStream TRUNCATED writer A's in-flight
        // temp, A renamed the shared file, and B's still-open fd kept
        // appending into the PUBLISHED file — the probe watched the
        // final hold 4000 foreign bytes and then vanish, all behind A's
        // `true` return. Per-writer unique temps (tempFileFor's
        // nanoTime discriminator) keep each encode private until its
        // own rename; the latch schedule below reproduces the probe's
        // interleaving deterministically.
        val final = File(dir.root, "race.png")
        val aWroteHalf = CountDownLatch(1)     // A's first 500 bytes are on disk
        val bWroteHalf = CountDownLatch(1)     // B's first 2000 bytes are on disk
        val aPublished = CountDownLatch(1)     // A's writeAtomically returned
        val snapshotTaken = CountDownLatch(1)  // main thread inspected A's publish
        val payloadA = ByteArray(1000) { 0x41 } // 'A' — writer A's complete encode
        val payloadB = ByteArray(4000) { 0x42 } // 'B' — writer B's complete encode
        var aResult = false
        var bResult = false
        val writerA = Thread {
            aResult = AtomicPng.writeAtomically(final, { out ->
                out.write(payloadA, 0, 500)
                out.flush()
                aWroteHalf.countDown()
                // Hold until B has opened its stream and written — the
                // exact moment the old shared temp was truncated. (A
                // failed await throws AssertionError, killing this
                // thread; main then fails on aResult=false — loud.)
                assertTrue(bWroteHalf.await(5, TimeUnit.SECONDS))
                out.write(payloadA, 500, 500)
                true
            }, { })
            aPublished.countDown()
        }
        val writerB = Thread {
            bResult = AtomicPng.writeAtomically(final, { out ->
                assertTrue(aWroteHalf.await(5, TimeUnit.SECONDS))
                out.write(payloadB, 0, 2000)
                out.flush()
                bWroteHalf.countDown()
                // Keep writing AFTER A has published AND main has
                // snapshotted — the old bug landed these bytes inside
                // A's already-published file.
                assertTrue(aPublished.await(5, TimeUnit.SECONDS))
                assertTrue(snapshotTaken.await(5, TimeUnit.SECONDS))
                out.write(payloadB, 2000, 2000)
                true
            }, { })
        }
        writerA.start()
        writerB.start()
        writerA.join(10_000)
        // A returned true ⇒ the final name holds A's COMPLETE encode,
        // byte for byte, even though B is mid-encode with an open
        // stream (the shared-temp bug published a 2000-byte chimera of
        // both payloads here).
        assertTrue(aResult)
        assertEquals(payloadA.toList(), final.readBytes().toList())
        // B's in-flight temp is a SEPARATE `.part` sibling, still there.
        assertEquals(1, dir.root.list()!!.count { AtomicPng.isTempArtifact(it) })
        snapshotTaken.countDown()
        writerB.join(10_000)
        // B's later publish replaces A's WHOLESALE (last complete
        // encode wins) — never a byte-level interleaving of the two.
        assertTrue(bResult)
        assertEquals(payloadB.toList(), final.readBytes().toList())
        // No temp residue from either writer once both returned.
        assertEquals(listOf("race.png"), dir.root.list()!!.toList())
    }

    // ── Clause 4: naming rules ─────────────────────────────────────────

    @Test
    fun `temp names sit beside the final name and never match a png expectation`() {
        val final = File(dir.root, "wpt__css-break__background-image-006.png")
        val temp = AtomicPng.tempFileFor(final)
        // Same directory — rename(2) atomicity holds only within a mount.
        assertEquals(final.parentFile, temp.parentFile)
        // The feeder's want-set members all end ".png"; the temp cannot.
        assertFalse(temp.name.endsWith(".png"))
        // And the sweep recogniser matches exactly the temp shape.
        assertTrue(AtomicPng.isTempArtifact(temp.name))
        assertFalse(AtomicPng.isTempArtifact(final.name))
    }
}
