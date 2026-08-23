package com.styleconverter.runtime.typography.font

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.styleconverter.runtime.core.ir.IRFontFace
import com.styleconverter.runtime.typography.CssFontFamilyResolver
import com.styleconverter.runtime.typography.MonospaceUAFontSize
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Wave-46 lane Y7 — pins the resolution CONTRACT the monospace font-metric
 * pin pilot (`tools/titan/mono-pin.mjs`) rides on.
 *
 * The pilot delivers DejaVu Sans Mono — the free face with BYTE-IDENTICAL
 * metrics to the Menlo the frozen browser refs were rasterised with ('0'
 * advance 1233/2048, hhea 1901/-483) — as a DOCUMENT `@font-face` NAMED
 * `monospace`, relying on two existing rules rather than on new resolver code:
 *
 *  1. css-fonts-4 §5 / wave-35 lane B2: [CssFontFamilyResolver.resolveEntry]
 *     consults [DocumentFontRegistry] BEFORE the generic table, so a declared
 *     face named `monospace` wins over `FontFamily.Monospace` (Droid Sans Mono
 *     on the emulator — different advance rounding, different ink).
 *  2. wave-36 lane M8: [MonospaceUAFontSize] keys the 13px fixed-default quirk
 *     on the declared family NAME, so pinning the face must not disarm it.
 *
 * Byte-parallel twin: SwiftUI `MonoPinDocumentFaceTests.swift`. JVM-only like
 * [DocumentFontRegistryTest]: the bundled-resource font loader stands in for
 * `Font(File)`, because it is the NAME routing under test, not rasterisation.
 */
class MonoPinDocumentFaceTest {

    @Before
    fun installTestLoader() {
        DocumentFontRegistry.fontLoader = { _, weight, style ->
            Font(com.styleconverter.runtime.R.font.inter_regular, weight, style)
        }
    }

    @After
    fun tearDown() {
        DocumentFontRegistry.fontLoader = { file, weight, style ->
            Font(file = file, weight = weight, style = style)
        }
        DocumentFontRegistry.clear()
    }

    /** `<base>/_mono-pin/<name>` — the pilot's sandbox corner, exactly the join
     *  feed-android pushes to and the registry resolves `src` against. */
    private fun pinTree(): File {
        val base = File(System.getProperty("java.io.tmpdir"), "w46y7-${System.nanoTime()}")
        val dir = File(base, "_mono-pin").apply { mkdirs() }
        for (name in listOf("DejaVuSansMono-Bold.ttf", "DejaVuSansMono.ttf")) {
            File(dir, name).apply { writeText("placeholder bytes"); deleteOnExit() }
        }
        return base
    }

    /** The exact two entries mono-pin.mjs appends (Bold first, Regular last). */
    private fun pinFaces() = listOf(
        IRFontFace(family = "monospace", src = "_mono-pin/DejaVuSansMono-Bold.ttf", weight = "700", style = "normal"),
        IRFontFace(family = "monospace", src = "_mono-pin/DejaVuSansMono.ttf", weight = "400", style = "normal"),
    )

    private fun families(vararg names: String) = buildJsonArray { names.forEach { add(JsonPrimitive(it)) } }

    @Test
    fun `a document face NAMED monospace outranks the platform generic`() {
        DocumentFontRegistry.register(pinFaces(), pinTree())
        val pinned = DocumentFontRegistry.resolve("monospace")
        assertNotNull(pinned)
        // Both weights fold into ONE family (§4.1 grouping), so a bold
        // monospace run matches the 700 face instead of fake-bolding.
        assertEquals(1, DocumentFontRegistry.lastReport.registered)
        // Rule 1: the name resolves to the document face, not Droid Sans Mono.
        assertSame(pinned, CssFontFamilyResolver.resolveEntry("monospace"))
        assertNotEquals(FontFamily.Monospace, CssFontFamilyResolver.resolveEntry("monospace"))
        // The live wire shape of hyphens-manual-inline-010's bordered box.
        assertSame(pinned, CssFontFamilyResolver.resolve(families("monospace")))
    }

    @Test
    fun `the 13px fixed-default quirk still fires on a pinned document`() {
        DocumentFontRegistry.register(pinFaces(), pinTree())
        // Rule 2: no FontSize + first family is the generic ⇒ 13sp, pin or no
        // pin. The pin must never turn a 13px document into a 16px one.
        val sp = MonospaceUAFontSize.resolveSpFromPairs(listOf("FontFamily" to families("monospace")))
        assertEquals(MonospaceUAFontSize.FIXED_DEFAULT_SP, sp)
        // And stays OFF for a concrete-first list (hyphens-auto-control).
        assertNull(MonospaceUAFontSize.resolveSpFromPairs(
            listOf("FontFamily" to families("Courier New", "Courier", "monospace"))))
    }

    @Test
    fun `the §5_2 walk reaches the pin only through the generic entry`() {
        DocumentFontRegistry.register(pinFaces(), pinTree())
        // hyphens-auto-control: neither Courier name is a family Compose can
        // provide, so the walk falls to the trailing generic — which is now
        // the pin. Same walk as without the pin; only what `monospace` IS
        // changed. (The ref renders the installed Courier New here, so this
        // cell is in the pilot's stated blast radius either way.)
        assertSame(DocumentFontRegistry.resolve("monospace"),
            CssFontFamilyResolver.resolve(families("Courier New", "Courier", "monospace")))
        // A sans-serif document never touches the pin.
        assertEquals(FontFamily.SansSerif, CssFontFamilyResolver.resolve(families("Inter2", "sans-serif")))
    }

    @Test
    fun `the next face-free document drops the pin (no cross-document leak)`() {
        DocumentFontRegistry.register(pinFaces(), pinTree())
        assertNotEquals(FontFamily.Monospace, CssFontFamilyResolver.resolveEntry("monospace"))
        // mono-pin.mjs appends faces ONLY to documents that name the generic;
        // the next document arrives face-free and must get the platform
        // generic back — the pilot is per-document, never process-wide.
        DocumentFontRegistry.register(null, null)
        assertEquals(FontFamily.Monospace, CssFontFamilyResolver.resolveEntry("monospace"))
    }
}
