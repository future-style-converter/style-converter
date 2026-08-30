package com.styleconverter.test.screenshot

// java.io only — this helper is deliberately Android-free (the TitanInbox
// precedent) so the plain-JVM suite can exercise the REAL shipped write
// path against real temp files, with the one Android-only step (the
// Bitmap.compress encode) injected as a lambda by ScreenshotManager.
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Atomic PNG publication for every capture the host pulls by NAME.
 *
 * THE DEFECT THIS CLOSES (wave-48 lane W1, measured on a private
 * API-36.1 emulator): tools/titan/feed-android.mjs's `waitForPngs` polls
 * the shot directory LISTING every 150 ms and treats the expected
 * filename's EXISTENCE as capture-complete, pulling ~150 ms later —
 * while `Bitmap.compress` streams the encode INCREMENTALLY into that
 * very filename. Any encode slower than ~300 ms is pulled truncated.
 * Ordinary ~390×600 captures encode in milliseconds, which is why the
 * race stayed invisible for 47 waves; css-writing-modes/
 * direction-upright-002's degenerate 390×66404 composed canvas encodes
 * for seconds, so BOTH cal-run pulls of it were truncated mid-write
 * (32878 and 8266 bytes of a valid IHDR + cut IDAT stream — the
 * scorer's "read requests waiting on finished stream" decode error),
 * after which the feeder force-stopped the app mid-encode. The fix is
 * the standard atomic-publish idiom: encode into a `.part` sibling the
 * feeder can never match, then `File.renameTo` onto the final name —
 * rename within one directory is atomic on every Android filesystem
 * (POSIX rename(2) semantics, preserved by the FUSE emulated-storage
 * passthrough), so the final name only ever names COMPLETE bytes.
 */
internal object AtomicPng {

    /**
     * Suffix for in-progress encodes. Chosen so a temp name can NEVER
     * collide with an expected capture: every host-side expectation
     * (feed-android's `composedPngName`, `expectedPngNames`, the
     * baseline pull glob) ends in `.png`, and
     * `<name>.png.<nanoTime>.part` does not. `File.extension` of such a
     * name is "part", which also keeps it outside ScreenshotManager's
     * png/jpg counting and clearing filters by construction.
     */
    const val TEMP_SUFFIX = ".part"

    /** The `.part` sibling a [finalFile]'s encode streams into — SAME
     *  directory, because rename(2) is only atomic within one mount
     *  point and the app-owned shot dir is the one place the API-36.1
     *  FUSE view guarantees the app can both write and rename.
     *
     *  The name is UNIQUE PER CALL (`<name>.png.<nanoTime>.part`) —
     *  wave-48 S4 executed the race the old deterministic
     *  `<name>.png.part` left open: two concurrent writers of ONE key
     *  shared the temp, so writer B's `FileOutputStream` TRUNCATED
     *  writer A's in-flight bytes, A renamed the shared file, and B's
     *  still-open fd kept appending into the file A had already
     *  PUBLISHED — the published capture mutated and was finally
     *  deleted behind A's `true` return. A per-writer temp keeps every
     *  encode private until its own rename, so the final name only
     *  ever swaps between COMPLETE encodes (last rename wins
     *  wholesale). `System.nanoTime` is discriminator enough: a
     *  collision needs two writers of the SAME key on the SAME
     *  nanosecond tick, and [isTempArtifact]'s suffix match (plus the
     *  clearScreenshots sweep) is unchanged because the suffix still
     *  ends the name. */
    fun tempFileFor(finalFile: File): File =
        File(finalFile.parentFile, finalFile.name + "." + System.nanoTime() + TEMP_SUFFIX)

    /** True for leftover in-progress artifacts (a crash between encode
     *  and rename strands one) — [ScreenshotManager.clearScreenshots]
     *  sweeps these so a stale `.part` never survives into a later run. */
    fun isTempArtifact(name: String): Boolean = name.endsWith(TEMP_SUFFIX)

    /**
     * Encode-then-rename. Returns true only when [finalFile] now names a
     * COMPLETE capture; on any failure the partial `.part` is deleted so
     * no truncated bytes remain under ANY name (the cal-run corruption
     * shipped precisely because a partial file kept the final name).
     *
     * @param finalFile the name the host expects (must be in the shot dir).
     * @param encode    writes the full image to the stream and reports
     *                  success — ScreenshotManager passes
     *                  `Bitmap.compress`'s Boolean straight through,
     *                  which the old path IGNORED (a false return left a
     *                  truncated file on disk behind a "Saved" log line —
     *                  a silent fallthrough, now impossible).
     * @param log       loud failure channel (Log.e at the call site; a
     *                  lambda so this file stays JVM-testable).
     */
    fun writeAtomically(
        finalFile: File,
        encode: (OutputStream) -> Boolean,
        log: (String) -> Unit
    ): Boolean {
        val temp = tempFileFor(finalFile)
        // Track the encoder's own verdict separately from throw-paths so
        // the failure log can say WHICH way the encode died.
        val encoded = try {
            // .use closes (and flushes) the stream before the rename
            // below, so the renamed bytes are the complete encode.
            FileOutputStream(temp).use { out -> encode(out) }
        } catch (e: Exception) {
            // Encoder threw mid-stream (an I/O error, a stream-state bug):
            // report, then fall through to the shared partial-cleanup.
            // Deliberately catch(Exception), NOT Throwable: an
            // OutOfMemoryError from a degenerate-canvas encode is an
            // Error and flies past on purpose — heap exhaustion leaves
            // no recovery this handler could honestly deliver, so
            // process death (surfacing as the feeder's loud pull
            // timeout) is the accepted outcome, and the stranded `.part`
            // is swept by clearScreenshots on the next launch (wave-48
            // S4 executed exactly this: OOM propagated, the final name
            // never existed, the `.part` remained for the sweep).
            log("PNG encode threw for ${finalFile.name}: ${e.message}")
            false
        }
        if (!encoded) {
            // Encoder said no (or threw): the temp holds truncated bytes.
            // Delete them — a missing capture is a LOUD feeder timeout the
            // harness attributes correctly; a truncated one poisons the
            // scorer downstream (the exact cal-run failure mode). The
            // exists() guard (wave-48 S4): when the stream OPEN itself
            // threw (e.g. a missing parent directory) no temp was ever
            // created, and the old unconditional delete-check logged a
            // failure to delete a partial that never existed.
            if (temp.exists() && !temp.delete()) log("could not delete partial ${temp.name}")
            log("PNG encode FAILED for ${finalFile.name} — no capture published")
            return false
        }
        // A stale complete file under the final name (e.g. a re-render of
        // the same fixture) must not block the rename: renameTo onto an
        // existing name is not portable, so clear the target first. This
        // is the one window where the final name is briefly ABSENT —
        // harmless, the feeder just keeps polling.
        if (finalFile.exists() && !finalFile.delete()) {
            log("could not clear stale ${finalFile.name} before publish")
        }
        // The atomic publish: after this returns true the final name
        // refers to the complete encode, and before it the name does not
        // exist — there is no observable in-between for the puller.
        if (!temp.renameTo(finalFile)) {
            // Rename refused (exotic FUSE state): clean the temp and fail
            // loudly rather than leaving an unpullable stray.
            if (!temp.delete()) log("could not delete unrenamed ${temp.name}")
            log("PNG publish rename FAILED for ${finalFile.name}")
            return false
        }
        return true
    }
}
