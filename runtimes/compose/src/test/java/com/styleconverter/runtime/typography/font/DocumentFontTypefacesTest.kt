package com.styleconverter.runtime.typography.font

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.styleconverter.runtime.core.ir.IRFontFace
import com.styleconverter.runtime.spacing.ChUnitMetrics
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Wave-47 lane Z4 (SEAM 1 of the monospace pin) — pins the measuring-handle
 * contract [DocumentFontTypefaces] adds to the wave-35 registry: which FACE
 * of a registered family the `ch` basis measures, how the handle expires
 * with the document, and that the JVM (no android.graphics) degrades to the
 * css-values-4 §6.1.1 spec fallback rather than crashing or lying.
 *
 * Like [DocumentFontRegistryTest] these are JVM pins of the CONTRACT — the
 * device gate (the wave-47 mono-pin A/B on css-text) measures whether the
 * Typeface actually rasterises and the `10ch` box lands on the frozen ref's
 * 197px instead of Roboto's 184px.
 */
class DocumentFontTypefacesTest {

    /** Files handed to the typeface loader seam, so a test can assert WHICH
     *  face was picked and HOW OFTEN the loader ran (memoization). */
    private val loaderCalls = mutableListOf<File>()

    @Before
    fun installTestLoaders() {
        // Same reasoning as DocumentFontRegistryTest: Font(File) and
        // Typeface.createFromFile are unmocked on the JVM, so both seams get
        // JVM-safe stand-ins. The typeface loader RECORDS and answers null —
        // the honest JVM answer (no rasteriser) that routes ChUnitMetrics to
        // the spec fallback.
        DocumentFontRegistry.fontLoader = { _, weight, style ->
            Font(com.styleconverter.runtime.R.font.inter_regular, weight, style)
        }
        DocumentFontTypefaces.typefaceLoader = { file ->
            loaderCalls += file
            null
        }
    }

    @After
    fun tearDown() {
        // Restore both production loaders and clear the process-scoped
        // registry so no face (or handle) leaks into the next test.
        DocumentFontRegistry.fontLoader = { file, weight, style ->
            Font(file = file, weight = weight, style = style)
        }
        DocumentFontTypefaces.typefaceLoader = { file ->
            runCatching { android.graphics.Typeface.createFromFile(file) }.getOrNull()
        }
        DocumentFontRegistry.clear()
    }

    /** `<base>/_mono-pin/<names>` — the pilot's sandbox corner, one real file
     *  per name (content is irrelevant on the JVM; the registry only checks
     *  readability + extension). */
    private fun pinTree(vararg names: String): File {
        val base = File(System.getProperty("java.io.tmpdir"), "w47z4-${System.nanoTime()}")
        val dir = File(base, "_mono-pin").apply { mkdirs() }
        for (name in names) File(dir, name).apply { writeText("bytes"); deleteOnExit() }
        return base
    }

    /** The exact two entries mono-pin.mjs appends (Bold FIRST, Regular last —
     *  the order that exposed SwiftUI's last-wins mapping in wave 46). */
    private fun pinFaces() = listOf(
        IRFontFace(family = "monospace", src = "_mono-pin/DejaVuSansMono-Bold.ttf", weight = "700", style = "normal"),
        IRFontFace(family = "monospace", src = "_mono-pin/DejaVuSansMono.ttf", weight = "400", style = "normal"),
    )

    // ── the measuring-face pick ─────────────────────────────────────────────

    @Test
    fun `the Regular face is the measuring face whatever the wire order`() {
        // Bold is declared FIRST (the pilot's real order); §6.1.1 measures a
        // plain run's '0', so the 400 face must win the pick.
        DocumentFontRegistry.register(pinFaces(), pinTree("DejaVuSansMono-Bold.ttf", "DejaVuSansMono.ttf"))
        val family = DocumentFontRegistry.resolve("monospace")
        assertNotNull(family)
        val face = DocumentFontTypefaces.measuringFace(family)
        assertNotNull(face)
        assertEquals("DejaVuSansMono.ttf", face!!.file.name)
        assertEquals(400, face.weight.weight)
    }

    @Test
    fun `an upright face outranks an italic one at equal weight distance`() {
        // §4.5: the italic file must not become the ch basis of a normal run.
        DocumentFontRegistry.register(listOf(
            IRFontFace(family = "test", src = "_mono-pin/Italic.ttf", weight = "400", style = "italic"),
            IRFontFace(family = "test", src = "_mono-pin/Upright.ttf", weight = "400", style = "normal"),
        ), pinTree("Italic.ttf", "Upright.ttf"))
        val face = DocumentFontTypefaces.measuringFace(DocumentFontRegistry.resolve("test"))
        assertEquals("Upright.ttf", face!!.file.name)
    }

    @Test
    fun `an unregistered family and the generics have no measuring face`() {
        // The universal (face-free) document: everything answers null and
        // ChUnitMetrics keeps its historical generic/DEFAULT mapping.
        assertNull(DocumentFontTypefaces.measuringFace(null))
        assertNull(DocumentFontTypefaces.measuringFace(FontFamily.Monospace))
        assertNull(DocumentFontTypefaces.typefaceFor(FontFamily.SansSerif))
    }

    // ── memoization + generation expiry ─────────────────────────────────────

    @Test
    fun `typefaceFor loads once per family even when the platform answers null`() {
        DocumentFontRegistry.register(pinFaces(), pinTree("DejaVuSansMono-Bold.ttf", "DejaVuSansMono.ttf"))
        val family = DocumentFontRegistry.resolve("monospace")
        // Three consults (three recompositions) — ONE loader call: a null
        // (unparseable/JVM) result is memoized exactly like a real Typeface.
        assertNull(DocumentFontTypefaces.typefaceFor(family))
        assertNull(DocumentFontTypefaces.typefaceFor(family))
        assertNull(DocumentFontTypefaces.typefaceFor(family))
        assertEquals(1, loaderCalls.size)
        // And it asked for the MEASURING face's file, not the bold sibling.
        assertEquals("DejaVuSansMono.ttf", loaderCalls.single().name)
    }

    @Test
    fun `re-registration retires the previous document's handles and keys`() {
        DocumentFontRegistry.register(pinFaces(), pinTree("DejaVuSansMono-Bold.ttf", "DejaVuSansMono.ttf"))
        val pinned = DocumentFontRegistry.resolve("monospace")
        val g1 = DocumentFontTypefaces.generation
        val k1 = ChUnitMetrics.cacheName(pinned)
        // Document N+1 declares nothing: the handle must go with the family
        // (a stale handle would measure a face the document can't resolve).
        DocumentFontRegistry.register(null, null)
        assertTrue(DocumentFontTypefaces.generation > g1)
        assertNull(DocumentFontTypefaces.measuringFace(pinned))
        assertNull(DocumentFontTypefaces.typefaceFor(pinned))
        // Document N+2 re-registers the same faces: same-looking family, NEW
        // generation-stamped cache key — document N's memoized advance can
        // never answer for it.
        DocumentFontRegistry.register(pinFaces(), pinTree("DejaVuSansMono-Bold.ttf", "DejaVuSansMono.ttf"))
        val k3 = ChUnitMetrics.cacheName(DocumentFontRegistry.resolve("monospace"))
        assertNotEquals(k1, k3)
    }

    // ── the ChUnitMetrics hook ──────────────────────────────────────────────

    @Test
    fun `ChUnitMetrics keys a document family off the shared default key`() {
        DocumentFontRegistry.register(pinFaces(), pinTree("DejaVuSansMono-Bold.ttf", "DejaVuSansMono.ttf"))
        val pinned = DocumentFontRegistry.resolve("monospace")
        // The pinned family gets its own generation-stamped key: sharing
        // "default" would let a Roboto-measured advance answer for the pin
        // (the exact wave-46 Y7 seam) via the memo instead of the engine.
        assertTrue(ChUnitMetrics.cacheName(pinned).startsWith("doc:"))
        assertEquals("default", ChUnitMetrics.cacheName(null))
        assertEquals("monospace", ChUnitMetrics.cacheName(FontFamily.Monospace))
    }

    @Test
    fun `on the JVM a registered family still answers the spec fallback`() {
        // No android.graphics on the JVM: the loader answers null, the
        // DEFAULT-typeface Paint construction throws, and measure() must
        // answer null — the css-values-4 §6.1.1 "assume 0.5em" route — never
        // zero and never a crash.
        DocumentFontRegistry.register(pinFaces(), pinTree("DejaVuSansMono-Bold.ttf", "DejaVuSansMono.ttf"))
        assertNull(ChUnitMetrics.measure(DocumentFontRegistry.resolve("monospace"), 32f))
    }
}
