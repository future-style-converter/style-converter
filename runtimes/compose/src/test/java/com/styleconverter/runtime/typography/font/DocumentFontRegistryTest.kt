package com.styleconverter.runtime.typography.font

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.styleconverter.runtime.core.ir.IRFontFace
import com.styleconverter.runtime.typography.CssFontFamilyResolver
import com.styleconverter.runtime.typography.InterFontFamily
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import androidx.compose.ui.text.font.Font
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit pins for the wave-35 lane B2 document `@font-face` registry and its
 * hook into [CssFontFamilyResolver].
 *
 * These run on the JVM with no Android runtime, so they cannot assert that a
 * face RASTERISES — that is what the device gate measures. What they CAN pin,
 * and what a refactor is most likely to break silently, is the resolution
 * CONTRACT: which family name wins, what a missing file does, and that a
 * face-free document leaves the wave-34 resolver walk byte-for-byte intact.
 */
class DocumentFontRegistryTest {

    @Before
    fun installTestLoader() {
        // `Font(File, …)` calls Typeface.createFromFile eagerly, which is not
        // mocked on the JVM — see the fontLoader seam's own comment. Loading a
        // BUNDLED resource font instead exercises everything this file owns
        // (identity, §4.1 grouping, declines, the resolver hook) without
        // asking the JVM to rasterise anything.
        DocumentFontRegistry.fontLoader = { _, weight, style ->
            Font(com.styleconverter.runtime.R.font.inter_regular, weight, style)
        }
    }

    @After
    fun tearDown() {
        DocumentFontRegistry.fontLoader = { file, weight, style ->
            Font(file = file, weight = weight, style = style)
        }
        // The registry is document-scoped process state; leaking a face into
        // the next test would be the exact cross-document shadowing bug the
        // production `register` guards against.
        DocumentFontRegistry.clear()
    }

    /** A real (non-font) file on disk — enough for the readable-file check;
     *  Compose only parses the bytes when it resolves the face for painting. */
    private fun tempFontFile(name: String = "Face.ttf"): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "w35b2-${System.nanoTime()}")
        val nested = File(dir, "css/res")
        nested.mkdirs()
        val f = File(nested, name)
        f.writeText("placeholder bytes")
        f.deleteOnExit()
        return f
    }

    // ── registration ────────────────────────────────────────────────────────

    @Test
    fun `a declared face registers under its CSS family name`() {
        val file = tempFontFile()
        val base = file.parentFile.parentFile.parentFile   // …/<tmp>/  (src = css/res/Face.ttf)
        val n = DocumentFontRegistry.register(
            listOf(IRFontFace(family = "test", src = "css/res/Face.ttf")), base)
        assertEquals(1, n)
        assertNotNull(DocumentFontRegistry.resolve("test"))
        assertEquals(1, DocumentFontRegistry.lastReport.registered)
        assertTrue(DocumentFontRegistry.lastReport.declined.isEmpty())
    }

    @Test
    fun `family matching is ASCII case-insensitive and quote-tolerant`() {
        // css-fonts-4 §4.2 — `Test` and `test` name ONE family. A
        // case-sensitive map would register a face nothing could reference.
        val file = tempFontFile()
        val base = file.parentFile.parentFile.parentFile
        DocumentFontRegistry.register(
            listOf(IRFontFace(family = "Test", src = "css/res/Face.ttf")), base)
        assertNotNull(DocumentFontRegistry.resolve("test"))
        assertNotNull(DocumentFontRegistry.resolve("TEST"))
        assertNotNull(DocumentFontRegistry.resolve("\"test\""))
    }

    @Test
    fun `several faces of one family collapse into ONE FontFamily`() {
        // §4.1 lets a family be declared by several files (regular + bold);
        // building one family per ENTRY would let the last entry win and drop
        // every other weight.
        val file = tempFontFile()
        val base = file.parentFile.parentFile.parentFile
        val n = DocumentFontRegistry.register(listOf(
            IRFontFace(family = "test", src = "css/res/Face.ttf", weight = "400"),
            IRFontFace(family = "test", src = "css/res/Face.ttf", weight = "700"),
        ), base)
        assertEquals("both faces belong to one family", 1, n)
        assertEquals(2, DocumentFontRegistry.lastReport.declared)
    }

    // ── declines ────────────────────────────────────────────────────────────

    @Test
    fun `a missing file DECLINES instead of registering an unusable family`() {
        val base = File(System.getProperty("java.io.tmpdir"), "w35b2-absent")
        val n = DocumentFontRegistry.register(
            listOf(IRFontFace(family = "test", src = "nope.woff")), base)
        assertEquals(0, n)
        // Null, not a default: the caller is walking a §5.2 fallback list and
        // must stay free to continue. A default here would stop the walk at
        // the first name and re-introduce the wrong-face bug.
        assertNull(DocumentFontRegistry.resolve("test"))
        assertEquals(listOf("test"), DocumentFontRegistry.lastReport.declined)
    }

    @Test
    fun `a WOFF face is DECLINED, not silently defaulted`() {
        // The wave-35 device finding: Typeface.createFromFile answers the
        // DEFAULT typeface for a WOFF instead of failing, so an ungated
        // registration renders the wrong outlines with nothing in any log.
        // The gate turns that silent lie into a loud decline.
        val file = tempFontFile("Face.woff")
        val base = file.parentFile.parentFile.parentFile
        val n = DocumentFontRegistry.register(
            listOf(IRFontFace(family = "test", src = "css/res/Face.woff")), base)
        assertEquals(0, n)
        assertNull(DocumentFontRegistry.resolve("test"))
        assertEquals(listOf("test"), DocumentFontRegistry.lastReport.declined)
    }

    @Test
    fun `woff2 is declined for the same reason`() {
        val file = tempFontFile("Face.woff2")
        val base = file.parentFile.parentFile.parentFile
        assertEquals(0, DocumentFontRegistry.register(
            listOf(IRFontFace(family = "test", src = "css/res/Face.woff2")), base))
    }

    @Test
    fun `the wave-42 host TRANSCODE double extension is admitted`() {
        // wave-42 lane W9: tools/titan/woff-to-ttf.mjs repackages a WOFF1 on
        // the HOST into the raw sfnt it wraps and delivers it as a SIBLING
        // named `<stem>.woff.otf` / `<stem>.woff.ttf` — the suffix is APPENDED
        // rather than substituted so the woff stem stays legible in every adb
        // listing and cannot collide with an authored `<stem>.ttf`.
        //
        // That naming makes THIS registry's sniff load-bearing: it takes the
        // LAST dot-segment (`substringAfterLast('.')`), so `.woff.otf` reads
        // as 'otf' and passes ANDROID_LOADABLE_EXTENSIONS. PROBED REAL on a
        // private API-36.1 emulator (Medium_Phone_API_36.1, port 5678): the
        // same css-text boundary-shaping face flipped this registry's log from
        // "declined … is a 'woff' container" to "registered 1 @font-face
        // family: test" for all 48 face-bearing fixtures, and the 8 that
        // actually reference the family re-shaped from Inter to LinLibertine.
        //
        // Pinned because a plausible-looking future tightening of the sniff —
        // e.g. declining anything whose NAME contains ".woff" — would silently
        // un-deliver every transcoded face and send Android back to the
        // fallback with no feeder-side signal at all.
        for (ext in listOf("ttf", "otf")) {
            val file = tempFontFile("Face.woff.$ext")
            val base = file.parentFile.parentFile.parentFile
            assertEquals("the .woff.$ext host transcode must load", 1,
                DocumentFontRegistry.register(
                    listOf(IRFontFace(family = "test", src = "css/res/Face.woff.$ext")), base))
            assertNotNull(DocumentFontRegistry.resolve("test"))
        }
    }

    @Test
    fun `otf and ttc are admitted alongside ttf`() {
        // The table is CLOSED but not narrower than the platform: all three
        // are containers Typeface.createFromFile genuinely parses.
        for (ext in listOf("otf", "ttc")) {
            val file = tempFontFile("Face.$ext")
            val base = file.parentFile.parentFile.parentFile
            assertEquals("$ext must be admitted", 1, DocumentFontRegistry.register(
                listOf(IRFontFace(family = "test", src = "css/res/Face.$ext")), base))
        }
    }

    @Test
    fun `a null base directory declines every face`() {
        // The host had no fonts hop at all (a bundled-asset run). Degrading to
        // the wave-34 behaviour is correct; pretending to register is not.
        val n = DocumentFontRegistry.register(
            listOf(IRFontFace(family = "test", src = "css/res/Face.ttf")), null)
        assertEquals(0, n)
        assertEquals(1, DocumentFontRegistry.lastReport.declined.size)
    }

    @Test
    fun `registering a face-free document CLEARS the previous document`() {
        val file = tempFontFile()
        val base = file.parentFile.parentFile.parentFile
        DocumentFontRegistry.register(
            listOf(IRFontFace(family = "test", src = "css/res/Face.ttf")), base)
        assertNotNull(DocumentFontRegistry.resolve("test"))
        // Document N+1 declares nothing — the previous face must NOT survive,
        // or its file would shape text in a document that never asked for it.
        DocumentFontRegistry.register(null, base)
        assertNull(DocumentFontRegistry.resolve("test"))
        assertTrue(DocumentFontRegistry.isEmpty())
    }

    // ── descriptor parsing ──────────────────────────────────────────────────

    @Test
    fun `font-weight descriptors map per §4_4, ranges take the low end`() {
        assertEquals(FontWeight.Normal, DocumentFontRegistry.parseWeight(null))
        assertEquals(FontWeight.Normal, DocumentFontRegistry.parseWeight("normal"))
        assertEquals(FontWeight.Bold, DocumentFontRegistry.parseWeight("bold"))
        assertEquals(FontWeight(700), DocumentFontRegistry.parseWeight("700"))
        // A RANGE ("400 700") is a variable-font descriptor; Compose has no
        // range API at this BOM, and the low end is the face's own weight.
        assertEquals(FontWeight(400), DocumentFontRegistry.parseWeight("400 700"))
        // §4.4 clamps to [1, 1000] rather than constructing an invalid weight.
        assertEquals(FontWeight(1000), DocumentFontRegistry.parseWeight("4000"))
        assertEquals(FontWeight.Normal, DocumentFontRegistry.parseWeight("garbage"))
    }

    @Test
    fun `font-style descriptors map oblique onto italic per §4_5`() {
        assertEquals(FontStyle.Normal, DocumentFontRegistry.parseStyle(null))
        assertEquals(FontStyle.Italic, DocumentFontRegistry.parseStyle("italic"))
        // The angle is DROPPED, not approximated — Compose's FontStyle is
        // two-valued and a wrong slant would be a visible lie.
        assertEquals(FontStyle.Italic, DocumentFontRegistry.parseStyle("oblique 20deg"))
        assertEquals(FontStyle.Normal, DocumentFontRegistry.parseStyle("normal"))
    }

    // ── the resolver hook ───────────────────────────────────────────────────

    @Test
    fun `a declared family outranks the bundled Inter and the generics`() {
        // css-fonts-4 §5 consults the document's own database FIRST, so a test
        // that declared `@font-face { font-family: serif }` gets its file, not
        // the platform serif.
        val file = tempFontFile()
        val base = file.parentFile.parentFile.parentFile
        DocumentFontRegistry.register(listOf(
            IRFontFace(family = "inter", src = "css/res/Face.ttf"),
            IRFontFace(family = "serif", src = "css/res/Face.ttf"),
        ), base)
        assertTrue(CssFontFamilyResolver.resolveEntry("inter") !== InterFontFamily)
        assertEquals(DocumentFontRegistry.resolve("serif"), CssFontFamilyResolver.resolveEntry("serif"))
    }

    @Test
    fun `a face-free document leaves the wave-34 resolver walk untouched`() {
        // The universal case. An empty registry must cost one lookup on an
        // empty map and change no resolution — this is what keeps every
        // committed baseline byte-identical.
        assertTrue(DocumentFontRegistry.isEmpty())
        assertEquals(InterFontFamily, CssFontFamilyResolver.resolveEntry("inter"))
        assertNull(CssFontFamilyResolver.resolveEntry("Helvetica Neue"))
        val stack = buildJsonArray { add(JsonPrimitive("test")); add(JsonPrimitive("serif")) }
        // "test" is unknown → the walk continues to the serif tail, exactly as
        // it did before this wave.
        assertNotNull(CssFontFamilyResolver.resolve(stack))
    }

    @Test
    fun `a declared family wins the fallback walk from the FIRST name`() {
        val file = tempFontFile()
        val base = file.parentFile.parentFile.parentFile
        DocumentFontRegistry.register(
            listOf(IRFontFace(family = "test", src = "css/res/Face.ttf")), base)
        val stack = buildJsonArray { add(JsonPrimitive("test")); add(JsonPrimitive("serif")) }
        val resolved = CssFontFamilyResolver.resolve(stack)
        assertEquals(DocumentFontRegistry.resolve("test"), resolved)
        assertFalse("must not fall through to the serif tail",
            resolved === androidx.compose.ui.text.font.FontFamily.Serif)
    }
}
