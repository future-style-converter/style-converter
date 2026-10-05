package com.styleconverter.runtime.transforms

// Wave 52 (lane L4, native-near-misses T7, skeptic should-fix 2) — the
// renderer-seam side of `transform: inherit` (css-cascade-4 §7.3.2).
// TransformExtractorTest pins the RESOLUTION on the verbatim wave51-fix
// css-transform-inherit-scale wire; this class pins the two ways the seam
// (tools/titan/results/wave52-small-fixes/seam-1.patch) can be mis-merged:
//   * the channel UNPROVIDED (Kotlin null) must not pose as the parent's
//     computed `none` — the keyword is left for the extractor's breadcrumb;
//   * ComponentRenderer must carry BOTH halves (the resolve read and the
//     provider) or neither — the skeptic's M6 (resolve without provider)
//     rendered today's failing picture with no breadcrumb.
//
// MUTATIONS EXECUTED (2026-10-05, this lane, each restored byte-exact and
// sha256-verified; drivers + logs in tools/titan/results/wave52-small-fixes/):
//   MU1 resolve's `inherited == null` → `resolve(properties, NONE)` (unprovided
//       collapses into `none` again) → U1 red ("unprovided must be the identity").
//   M6  seam-1 applied WITHOUT its provider hunk, under the per-file lock →
//       U3 red ("seam must provide LocalInheritedTransform"); the full seam-1
//       is green (379 / 0 with transforms.*, layout.position.*, seam tests).

// The renderer's property record: `type` + raw JSON `data`.
import com.styleconverter.runtime.core.ir.IRProperty
// Global unhandled-property log the extractor's fallback writes to.
import com.styleconverter.runtime.PropertyTracker
// Parses the verbatim wire payloads below.
import kotlinx.serialization.json.Json
// Reads ComponentRenderer.kt for the seam pin (U3).
import java.io.File
// JUnit4 assertions and the test annotation (this module's test runner).
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransformInheritanceSeamTest {

    /** IR property, as the renderer holds it. */
    private fun ir(t: String, j: String) = IRProperty(t, Json.parseToJsonElement(j))

    /** `.child` of css-transform-inherit-scale (`…__1__1__0-156`), verbatim. */
    private val child = listOf(
        ir("Position", "\"ABSOLUTE\""),
        ir("Transform", """{"type":"keyword","keyword":"inherit"}"""),
        ir("Width", """{"type":"length","px":50}"""),
        ir("Height", """{"type":"length","px":50}"""),
        ir("BackgroundColor", """{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}"""),
    )

    @Test fun `U1 - an unprovided channel leaves the keyword for the extractor's breadcrumb`() {
        // Unprovided (null): identity, same instance — nothing is guessed.
        val unprovided = TransformInheritance.resolve(child, null)
        assertTrue("unprovided must be the identity", unprovided === child)
        // …and the extractor then records the refusal (no silent drop).
        PropertyTracker.reset()
        TransformExtractor.extractTransformConfig(unprovided.map { it.type to it.data })
        assertTrue("the keyword must be breadcrumbed", PropertyTracker.isUnhandled("Transform"))
        PropertyTracker.reset()
        // Contrast: the parent's computed `none` DROPS the entry, silently and
        // correctly (§7.3.2 + css-transforms-1 §3) — no breadcrumb.
        val none = TransformInheritance.resolve(child, TransformInheritance.NONE)
        assertTrue(none.none { it.type == "Transform" })
        TransformExtractor.extractTransformConfig(none.map { it.type to it.data })
        assertFalse(PropertyTracker.isUnhandled("Transform"))
        PropertyTracker.reset()
    }

    @Test fun `U2 - published never answers unprovided, and only the keyword gates the read`() {
        // An untransformed element publishes NONE, not null.
        assertEquals(TransformInheritance.NONE, TransformInheritance.published(child.filter { it.type != "Transform" }))
        // The gate the renderer reads the local behind: true only for the keyword.
        assertTrue(TransformInheritance.carriesInherit(child))
        assertFalse(TransformInheritance.carriesInherit(child.filter { it.type != "Transform" }))
        assertFalse(TransformInheritance.carriesInherit(listOf(ir("Transform", """{"type":"keyword","keyword":"initial"}"""))))
    }

    /** A runtime source file, located by walking up from the test working dir (the S3 idiom). */
    private fun source(rel: String): String {
        // Gradle runs tests from the module dir; walk up to the repo root.
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, rel).exists()) dir = dir.parentFile
        return File(requireNotNull(dir) { "repo root ($rel) not found" }, rel).readText()
    }

    @Test fun `U3 - the renderer seam is all-or-nothing`() {
        val cr = source("runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt")
        // Whole-line comments may NAME the object; only code counts.
        val code = cr.lines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
        // Before seam-1 lands nothing names the object (vacuously true —
        // the extractor's breadcrumb is the honest fallback then).
        if (!code.contains("TransformInheritance")) return
        // Once it lands, all three pieces must be there together:
        // the gated read…
        assertTrue("seam must gate on carriesInherit", Regex("""TransformInheritance\s*\.carriesInherit\(""").containsMatchIn(code))
        // …the substitution before any extractor runs…
        assertTrue("seam must call resolve", Regex("""TransformInheritance\s*\.resolve\(""").containsMatchIn(code))
        // …and the provider every child reads (M6 drops exactly this).
        assertTrue("seam must provide LocalInheritedTransform",
            Regex("""LocalInheritedTransform\s+provides\s+[\w.\s]*TransformInheritance\s*\.published\(""").containsMatchIn(code))
    }
}
