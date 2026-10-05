package com.styleconverter.runtime.spacing

// Wave 52 (lane L4, queue 0(b″)) — JVM pins for the LEVEL of the containing
// block a percentage MARGIN / PADDING resolves against (CSS 2.1 §8.3 / §8.4 +
// §10.1, css-sizing-3 §5.2.1).
//
// Every payload below is copied VERBATIM out of the frozen wave51-fix per-test
// IR the Android capture consumed:
//   tools/titan/runs/wave51-fix/sections/css-sizing/per-test-ir/
//     wpt__css-sizing__abspos-auto-sizing-fit-content-percentage-00{1,2,3,4}.json
// `.abs` = `abspos-auto-sizing-fit-content-percentage-00N__1__0`, `.child` =
// `…__1__0__0`. The four cells are PASSING-WRONG (android P 0.9984 ×4): the
// picture equals the ref because the `.child` paints nothing and has no
// children, while the used margin/padding was −50 / +50 px where the spec
// says 0. The port predicts ZERO pixel movement; these pins fix the NUMBER.
//
// NEGATIVE CONTROL EXECUTED (2026-09-25, this lane): with MarginApplier.kt and
// PaddingApplier.kt restored to their pre-port HEAD bytes (`git show HEAD:…`)
// and this class run alone, `S3 - …` FAILS on the MarginApplier assertion
// ("MarginApplier's composed lane must read the element-level channel") —
// the ONLY `LocalContainingBlock.current` in the old file is the bare
// child-level read. Both files re-applied, sha-verified, class green.

import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.types.LengthUnit
import com.styleconverter.runtime.core.types.LengthValue
import com.styleconverter.runtime.core.variables.ContainingBlock
import com.styleconverter.runtime.core.variables.DynamicValueResolver
import com.styleconverter.runtime.layout.position.ElementContainingBlock
import com.styleconverter.runtime.sizing.SizingExtractor
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PercentSpacingContainingBlockLevelTest {

    /** IR property, as the renderer holds it. */
    private fun ir(type: String, json: String) = IRProperty(type, Json.parseToJsonElement(json))

    /** (type, data) pairs, as the extractors consume them. */
    private fun pairs(props: List<IRProperty>): List<Pair<String, JsonElement?>> =
        props.map { it.type to it.data }

    /** The harness root channel: 358 dp canvas content width, height unknown. */
    private val root = ContainingBlock(widthPx = 358f, heightPx = null)

    /** The WPT spacing context (P11 tri-state on) for one containing-block width. */
    private fun ctx(widthPx: Float?) = SpacingContext(parentWidthPx = widthPx, percentIndefiniteAsZero = true)

    // ── The corpus components, verbatim ──────────────────────────────────
    /** `.abs` (identical on 001–004): fit-content abspos, green, no size. */
    private val abs = listOf(
        ir("Position", "\"ABSOLUTE\""),
        ir("BackgroundColor", """{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}"""),
    )

    /** `.child` of -001: `margin-left: -50%` (a BARE NUMBER is the percent wire). */
    private val child001 = listOf(
        ir("Width", """{"type":"length","px":100}"""),
        ir("Height", """{"type":"length","px":100}"""),
        ir("MarginLeft", "-50"),
    )

    /** `.child` of -003: `padding-left: 50%`. */
    private val child003 = listOf(
        ir("Width", """{"type":"length","px":100}"""),
        ir("Height", """{"type":"length","px":100}"""),
        ir("PaddingLeft", "50"),
    )

    // ── S1: the level discriminator ───────────────────────────────────────
    @Test fun `S1 - the two levels differ on every fit-content carrier`() {
        // ELEMENT level: the block `.child` is laid out in is what `.abs`
        // publishes — no declared size on either axis → (null, null).
        val elementCb = DynamicValueResolver.childContainingBlock(abs, root)
        assertNull(elementCb.widthPx); assertNull(elementCb.heightPx)
        // CHILD level (what the appliers read before the port): the block the
        // `.child` publishes for ITS children — its own 100 × 100. The
        // bare-number padding is NOT subtracted (pxOf needs a `px` object).
        for (child in listOf(child001, child003)) {
            val ambientCb = DynamicValueResolver.childContainingBlock(child, elementCb)
            assertEquals(100f, ambientCb.widthPx)
            assertEquals(100f, ambientCb.heightPx)
            assertNotEquals(elementCb.widthPx, ambientCb.widthPx)
        }
    }

    // ── S2: the used value at each level ─────────────────────────────────
    @Test fun `S2 - a percentage against an indefinite block resolves to zero under WPT`() {
        // css-sizing-3 §5.2.1 (cyclic percentage → 0) via SpacingResolve's
        // P11 tri-state: indefinite base + percentIndefiniteAsZero → 0 px.
        val wpt = SpacingContext(parentWidthPx = null, percentIndefiniteAsZero = true)
        // Compared as floats: −50 % of a zero base is IEEE −0.0, which `Dp`
        // equality distinguishes from +0.0 while every layout consumer does not.
        assertEquals(0f, resolveToDp(LengthValue.Relative(-50.0, LengthUnit.PERCENT, null), wpt).value, 0f)
        assertEquals(0f, resolveToDp(LengthValue.Relative(50.0, LengthUnit.PERCENT, null), wpt).value, 0f)
        // The child-level number the capture used: −50 % / +50 % of the
        // child's OWN 100 px.
        val childLevel = SpacingContext(parentWidthPx = 100f, percentIndefiniteAsZero = true)
        assertEquals((-50).dp, resolveToDp(LengthValue.Relative(-50.0, LengthUnit.PERCENT, null), childLevel))
        assertEquals(50.dp, resolveToDp(LengthValue.Relative(50.0, LengthUnit.PERCENT, null), childLevel))
    }

    // ── S3: the wiring pin (source-level — this module has no Compose UI test) ──
    /** A runtime source file, located by walking up from the test working dir. */
    private fun source(rel: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, rel).exists()) dir = dir.parentFile
        return File(requireNotNull(dir) { "repo root ($rel) not found above ${System.getProperty("user.dir")}" }, rel).readText()
    }

    /** The `Modifier.composed { … }` percent lane of one applier, brace-matched. */
    private fun composedLane(src: String): String {
        val start = src.indexOf("modifier.composed {")
        assertTrue("applier must still have a composed percent lane", start >= 0)
        var depth = 0
        for (i in (start + "modifier.composed ".length) until src.length) {
            when (src[i]) { '{' -> depth++; '}' -> if (--depth == 0) return src.substring(start, i + 1) }
        }
        error("unbalanced composed lane")
    }

    @Test fun `S3 - both spacing appliers read the element-level channel inside their composed lane`() {
        val base = "runtimes/compose/src/main/java/com/styleconverter/runtime/spacing/"
        for ((file, name) in listOf("MarginApplier.kt" to "MarginApplier", "PaddingApplier.kt" to "PaddingApplier")) {
            val lane = composedLane(source(base + file))
            // The read goes through the level selector…
            assertTrue("$name's composed lane must read the element-level channel", lane.contains("ElementContainingBlock.containingBlockFor("))
            assertTrue("$name must pass the element channel", lane.contains("element = ElementContainingBlock.LocalElementContainingBlock.current"))
            assertTrue("$name must file its fallback under the spacing breadcrumb", lane.contains("breadcrumb = ElementContainingBlock.SPACING_UNPUBLISHED_BREADCRUMB"))
            // …and the ONLY child-level read is the `ambient =` fallback argument.
            val ambientReads = Regex("""ambient = LocalContainingBlock\.current""").findAll(lane).count()
            val allReads = Regex("""LocalContainingBlock\.current""").findAll(lane).count()
            assertEquals("$name: every LocalContainingBlock.current must be the ambient= argument", allReads, ambientReads)
            assertEquals("$name: exactly one ambient read", 1, ambientReads)
        }
    }

    // ── S4: the general case, where the picture WOULD move ───────────────
    @Test fun `S4 - parent 400 with a 200-wide child resolves margin-left 10 percent to 40 not 20`() {
        val cfg = SpacingExtractor.extractMarginConfig(pairs(listOf(ir("Width", """{"type":"length","px":200}"""), ir("MarginLeft", "10"))))
        val element = ContainingBlock(widthPx = 400f, heightPx = null)   // the parent's content box
        val ambient = ContainingBlock(widthPx = 200f, heightPx = null)   // the child's own published box
        val ported = MarginApplier.resolvedInsets(cfg,
            ctx(ElementContainingBlock.containingBlockFor(element = element, ambient = ambient).widthPx))
        // CSS 2.1 §8.3: 10 % of the CONTAINING BLOCK's 400 px.
        assertEquals(40.dp, ported.left)
        // Swapping the arguments reproduces the old child-level 20 px — the
        // number the port exists to stop producing.
        val swapped = MarginApplier.resolvedInsets(cfg,
            ctx(ElementContainingBlock.containingBlockFor(element = ambient, ambient = element).widthPx))
        assertEquals(20.dp, swapped.left)
        assertNotEquals(ported.left, swapped.left)
    }

    // ── S5: the breadcrumb is the spacing lane's own ─────────────────────
    @Test fun `S5 - the unpublished fallback is filed under Spacing not Inset`() {
        com.styleconverter.runtime.PropertyTracker.reset()
        ElementContainingBlock.containingBlockFor(null, ContainingBlock(), ElementContainingBlock.SPACING_UNPUBLISHED_BREADCRUMB)
        assertTrue(com.styleconverter.runtime.PropertyTracker.isUnhandled(ElementContainingBlock.SPACING_UNPUBLISHED_BREADCRUMB))
        assertFalse(com.styleconverter.runtime.PropertyTracker.isUnhandled(ElementContainingBlock.UNPUBLISHED_BREADCRUMB))
        com.styleconverter.runtime.PropertyTracker.reset()
        // The published path marks neither (mirror of the inset lane's L2).
        ElementContainingBlock.containingBlockFor(ContainingBlock(1f, 2f), ContainingBlock(), ElementContainingBlock.SPACING_UNPUBLISHED_BREADCRUMB)
        assertFalse(com.styleconverter.runtime.PropertyTracker.isUnhandled(ElementContainingBlock.SPACING_UNPUBLISHED_BREADCRUMB))
        assertFalse(com.styleconverter.runtime.PropertyTracker.isUnhandled(ElementContainingBlock.UNPUBLISHED_BREADCRUMB))
        com.styleconverter.runtime.PropertyTracker.reset()
        // The inset lane's default argument is untouched (wave-50 pins).
        assertEquals("Inset[containing-block-level-unpublished]", ElementContainingBlock.UNPUBLISHED_BREADCRUMB)
        assertEquals("Spacing[containing-block-level-unpublished]", ElementContainingBlock.SPACING_UNPUBLISHED_BREADCRUMB)
    }

    // ── S6: the two lanes of one runtime now agree ───────────────────────
    @Test fun `S6 - on the 003 payload the frame band and the padding lane both say zero`() {
        // SizingExtractor's content-box frame inflation (P13): under the WPT
        // content-box default the 50 % padding contributes 0 to the frame.
        val sizing = SizingExtractor.extractSizingConfig(pairs(child003), wptCaptureMode = true)
        assertEquals(0f, sizing.contentBoxInflateX)
        // The ported padding lane's context: element-level block (null) → 0.
        val elementCb = DynamicValueResolver.childContainingBlock(abs, root)
        val ambientCb = DynamicValueResolver.childContainingBlock(child003, elementCb)
        val pad = SpacingExtractor.extractPaddingConfig(pairs(child003)).resolve(isRtl = false)
        val ported = resolveToDp(pad.left,
            ctx(ElementContainingBlock.containingBlockFor(element = elementCb, ambient = ambientCb).widthPx))
        assertEquals(0.dp, ported)
        assertEquals(sizing.contentBoxInflateX, ported.value)
        // Reverting the applier to the child level puts 50 px of padding
        // INSIDE the 100 px box (content 50) while the frame lane said 100.
        val reverted = resolveToDp(pad.left, ctx(ambientCb.widthPx))
        assertEquals(50.dp, reverted)
        assertNotEquals(sizing.contentBoxInflateX, reverted.value)
    }
}
