package app.parsing.css.properties

import app.irmodels.IRProperty
import app.parsing.css.CssPropertyValue
import app.parsing.css.cssParsing
import app.parsing.css.properties.longhands.PropertyParserRegistry
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * Retrospective finding A11#4 — logical/physical alias pairs collapse by
 * cascade order in the converter.
 *
 * css-logical-1 §4: paired flow-relative and physical longhands "share a
 * computed value … derived from the specified value of the property declared
 * with higher priority in the CSS cascade"; within one declaration block that
 * is the LATER declaration, and the pairing uses "the element's own computed
 * writing mode" (css-writing-modes-4 §6.4 table). The converter used to emit
 * both members and every runtime picked its own winner (natives 182px canvas
 * vs web 112px on the same IR — the finding's evidence).
 *
 * One test per alias family (the finding asked for exactly that), then the
 * writing-context rules, then the VERBATIM corpus carriers the audit named
 * (fixtures/fidelity/pairwise/pairs-06.json PW_Sizing_Spacing_03,
 * fixtures/fidelity/sizing.combos.json Sizing_BoxModel,
 * fixtures/fidelity/spacing.combos.json Spacing_C03_MarginInlineEnd) read
 * from the repo so the payloads cannot drift from the fixtures.
 *
 * Proven able to fail (retro R9, executed): (M1) flipping `li < pi` to
 * `li > pi` in LogicalAliasResolution.collapse — 7 tests fail, every family
 * among them; (M2) deleting the `overflow-block` row from buildTable — the
 * family test AND the table-hygiene count fail; (M6c) hard-coding
 * `writingContext = WritingContext("vertical-rl", "ltr")` at the CssParsing
 * call site (every component abstains) — all three corpus-carrier tests and
 * the inheritance test fail; (M7, retro F4) restoring the pre-S5 out-of-scope
 * breadcrumb (`Kept(l, physicalNames().filter { it in expanded }.joinToString(","))`
 * for every logical longhand present) — both `out-of-scope Kept …` tests fail.
 */
class LogicalAliasResolutionTest {

    // ── helpers ─────────────────────────────────────────────────────────────

    /** Base-bucket parse under the harness root context (horizontal-tb / ltr). */
    private fun parseBase(ctx: WritingContext = WritingContext.ROOT, vararg decls: Pair<String, String>): List<IRProperty> =
        PropertiesParser.parse(
            decls.toMap().mapValues { CssPropertyValue(it.value) },
            resolveInheritedDefaults = true, sourceTag = null, writingContext = ctx
        )

    private fun names(props: List<IRProperty>) = props.map { it.propertyName }

    /**
     * Both orders of one pair: whichever member is declared later must be the
     * only survivor, and its own value must be the one kept.
     */
    private fun assertLaterWins(logical: String, physical: String, logicalValue: String, physicalValue: String) {
        val physicalLater = parseBase(WritingContext.ROOT, logical to logicalValue, physical to physicalValue)
        assertEquals(listOf(physical), names(physicalLater), "$logical then $physical → $physical wins")
        val logicalLater = parseBase(WritingContext.ROOT, physical to physicalValue, logical to logicalValue)
        assertEquals(listOf(logical), names(logicalLater), "$physical then $logical → $logical wins")
    }

    // ── one test per alias family (horizontal-tb / ltr) ─────────────────────

    @Test
    fun `sizing block-size and inline-size pair with height and width`() {            // css-logical-1 §4.1
        assertLaterWins("block-size", "height", "auto", "150px")
        assertLaterWins("inline-size", "width", "250px", "240px")
    }

    @Test
    fun `min and max sizing pair with their physical twins`() {                       // css-logical-1 §4.1
        assertLaterWins("min-block-size", "min-height", "80px", "10px")
        assertLaterWins("max-block-size", "max-height", "80px", "10px")
        assertLaterWins("min-inline-size", "min-width", "80px", "10px")
        assertLaterWins("max-inline-size", "max-width", "80px", "10px")
    }

    @Test
    fun `margin sides pair block to top-bottom and inline to left-right`() {          // css-logical-1 §4.2
        assertLaterWins("margin-block-start", "margin-top", "1px", "2px")
        assertLaterWins("margin-block-end", "margin-bottom", "1px", "2px")
        assertLaterWins("margin-inline-start", "margin-left", "40px", "4px")
        assertLaterWins("margin-inline-end", "margin-right", "1px", "2px")
    }

    @Test
    fun `padding sides pair like margins`() {                                          // css-logical-1 §4.4
        assertLaterWins("padding-block-start", "padding-top", "1px", "2px")
        assertLaterWins("padding-block-end", "padding-bottom", "1px", "2px")
        assertLaterWins("padding-inline-start", "padding-left", "1px", "2px")
        assertLaterWins("padding-inline-end", "padding-right", "1px", "2px")
    }

    @Test
    fun `inset sides pair with top right bottom left`() {                              // css-logical-1 §4.3
        assertLaterWins("inset-block-start", "top", "1px", "2px")
        assertLaterWins("inset-block-end", "bottom", "1px", "2px")
        assertLaterWins("inset-inline-start", "left", "1px", "2px")
        assertLaterWins("inset-inline-end", "right", "1px", "2px")
    }

    @Test
    fun `border widths pair per side`() {                                              // css-logical-1 §4.5.1
        for ((l, p) in sides) assertLaterWins("border-$l-width", "border-$p-width", "1px", "3px")
    }

    @Test
    fun `border styles pair per side`() {                                              // css-logical-1 §4.5.2
        for ((l, p) in sides) assertLaterWins("border-$l-style", "border-$p-style", "solid", "dashed")
    }

    @Test
    fun `border colors pair per side`() {                                              // css-logical-1 §4.5.3
        for ((l, p) in sides) assertLaterWins("border-$l-color", "border-$p-color", "red", "blue")
    }

    @Test
    fun `border radii pair start-start with top-left and so on`() {                   // css-logical-1 §4.6
        assertLaterWins("border-start-start-radius", "border-top-left-radius", "1px", "2px")
        assertLaterWins("border-start-end-radius", "border-top-right-radius", "1px", "2px")
        assertLaterWins("border-end-start-radius", "border-bottom-left-radius", "1px", "2px")
        assertLaterWins("border-end-end-radius", "border-bottom-right-radius", "1px", "2px")
    }

    @Test
    fun `scroll-margin sides pair per side`() {                                        // css-scroll-snap-1 Appendix A
        for ((l, p) in sides) assertLaterWins("scroll-margin-$l", "scroll-margin-$p", "1px", "2px")
    }

    @Test
    fun `scroll-padding sides pair per side`() {                                       // css-scroll-snap-1 Appendix A
        for ((l, p) in sides) assertLaterWins("scroll-padding-$l", "scroll-padding-$p", "1px", "2px")
    }

    @Test
    fun `overflow-block and overflow-inline pair with overflow-y and overflow-x`() {   // css-overflow-3 §3.1
        assertLaterWins("overflow-block", "overflow-y", "hidden", "scroll")
        assertLaterWins("overflow-inline", "overflow-x", "hidden", "scroll")
    }

    @Test
    fun `overscroll-behavior logical axes pair with y and x`() {                       // css-overscroll-1 §4.3 / §4.4
        assertLaterWins("overscroll-behavior-block", "overscroll-behavior-y", "contain", "none")
        assertLaterWins("overscroll-behavior-inline", "overscroll-behavior-x", "contain", "none")
    }

    @Test
    fun `contain-intrinsic logical sizes pair with height and width`() {               // css-sizing-4 §5.2
        assertLaterWins("contain-intrinsic-block-size", "contain-intrinsic-height", "100px", "50px")
        assertLaterWins("contain-intrinsic-inline-size", "contain-intrinsic-width", "100px", "50px")
    }

    @Test
    fun `background-position logical axes pair with y and x`() {                       // css-backgrounds-4 §2.6
        assertLaterWins("background-position-block", "background-position-y", "10px", "20px")
        assertLaterWins("background-position-inline", "background-position-x", "10px", "20px")
    }

    @Test
    fun `scroll-start logical axes pair with y and x`() {                              // 2023 css-scroll-snap-2 ED longhands
        assertLaterWins("scroll-start-block", "scroll-start-y", "10px", "20px")
        assertLaterWins("scroll-start-inline", "scroll-start-x", "10px", "20px")
    }

    @Test
    fun `scroll-start-target logical axes pair with y and x`() {                       // 2023 css-scroll-snap-2 ED longhands
        assertLaterWins("scroll-start-target-block", "scroll-start-target-y", "auto", "none")
        assertLaterWins("scroll-start-target-inline", "scroll-start-target-x", "auto", "none")
    }

    private val sides = listOf(
        "block-start" to "top", "block-end" to "bottom", "inline-start" to "left", "inline-end" to "right"
    )

    // ── table hygiene ───────────────────────────────────────────────────────

    @Test
    fun `every table name is accepted by the validator and has a registered parser`() {
        // A name the validator rejects never reaches the collapse (Step 1
        // filters it), and one without a parser would ride as a Generic; both
        // would make a table entry dead code. Mirrors ValidatorRegistryParityTest.
        val all = LogicalAliasResolution.logicalNames() + LogicalAliasResolution.physicalNames()
        val registered = PropertyParserRegistry.registeredPropertyNames()
        for (n in all) {
            assertTrue(CssPropertyValidator.isValidProperty(n), "$n rejected by CssPropertyValidator")
            assertTrue(n in registered, "$n has no registered longhand parser")
        }
        // 18 logical property groups, 54 flow-relative longhands:
        //   3 sizing groups × 2 axes  (block-size/inline-size, min-, max-)      =  6
        //   9 side groups × 4 sides   (margin, inset, padding, border-width /
        //                              -style / -color, scroll-margin,
        //                              scroll-padding, corner radius)           = 36
        //   6 axis groups × 2 axes    (overflow, overscroll-behavior,
        //                              contain-intrinsic-size, background-
        //                              position, scroll-start, scroll-start-
        //                              target)                                  = 12
        // Pinning the total keeps a silently-dropped table row visible.
        assertEquals(54, LogicalAliasResolution.logicalNames().size, "18 logical groups → 54 flow-relative longhands")
    }

    // ── writing context ─────────────────────────────────────────────────────

    @Test
    fun `under rtl inline-start pairs with right and inline-start plus left are not a pair`() {
        // css-logical-1 §4's own rtl example: margin-inline-end shares with
        // margin-left. Here: margin-inline-start ↔ margin-right, and
        // margin-inline-start + margin-left are DIFFERENT physical sides.
        val rtl = WritingContext("horizontal-tb", "rtl")
        assertEquals(listOf("margin-right"), names(parseBase(rtl, "margin-inline-start" to "40px", "margin-right" to "4px")))
        assertEquals(
            listOf("margin-inline-start", "margin-left"),
            names(parseBase(rtl, "margin-inline-start" to "40px", "margin-left" to "4px")),
            "not a pair under rtl → both kept"
        )
        // Corners follow: start-start is top-RIGHT under rtl (css-logical-1 §4.6).
        assertEquals(listOf("border-top-right-radius"), names(parseBase(rtl, "border-start-start-radius" to "1px", "border-top-right-radius" to "2px")))
    }

    @Test
    fun `vertical writing modes abstain and keep both declarations`() {
        // Scope guard 2: the natives resolve logical sides under vertical
        // modes themselves today; the converter must not half-collapse.
        val vertical = WritingContext("vertical-rl", "ltr")
        assertEquals(listOf("block-size", "height"), names(parseBase(vertical, "block-size" to "auto", "height" to "150px")))
        val undecidable = WritingContext(WritingContext.UNDECIDABLE, "ltr")
        assertEquals(listOf("block-size", "height"), names(parseBase(undecidable, "block-size" to "auto", "height" to "150px")))
    }

    // ── out-of-scope breadcrumb names the twin, never the whole block (retro S5) ──

    @Test
    fun `out-of-scope Kept record names the logical longhand's twin only`() {
        // Retro S5's css-break repro, VERBATIM names from its convert-log diff:
        // the old branch printed "Kept both 'block-size' and 'border-top-width,
        // border-top-style,border-bottom-width,…,border-right-style'" — every
        // physical longhand in the block, none of them block-size's twin.
        val vertical = WritingContext("vertical-rl", "ltr")
        val expanded = linkedMapOf(
            "block-size" to "auto", "border-top-width" to "1px", "border-top-style" to "solid",
            "border-bottom-width" to "1px", "border-right-style" to "solid", "height" to "150px"
        )
        val index = expanded.keys.withIndex().associate { (i, k) -> k to i }
        val r = LogicalAliasResolution.collapse(expanded, index, vertical)
        assertSame(expanded, r.properties, "abstention leaves the map untouched")
        assertTrue(r.dropped.isEmpty(), "abstention drops nothing")
        assertEquals(listOf("block-size" to "height"), r.kept.map { it.logical to it.physical }, "the twin, and only the twin")
        assertTrue(r.kept.none { "border" in it.physical }, "no border longhand may be reported as block-size's pair")
        // The rtl candidate is a twin too: margin-inline-start ↔ margin-right
        // (css-writing-modes-4 §6.4 — the inline axis follows direction).
        val rtlTwin = LogicalAliasResolution.collapse(
            linkedMapOf("margin-inline-start" to "1px", "margin-right" to "2px"),
            mapOf("margin-inline-start" to 0, "margin-right" to 1), vertical
        )
        assertEquals(listOf("margin-inline-start" to "margin-right"), rtlTwin.kept.map { it.logical to it.physical })
    }

    @Test
    fun `a lone logical longhand out of scope produces no Kept record`() {
        // Retro S5's css-contain repro, VERBATIM names: the old branch printed
        // "Kept both 'contain-intrinsic-inline-size' and 'height,width'" —
        // the twin (contain-intrinsic-width) is absent, so there is no pair.
        val undecidable = WritingContext(WritingContext.UNDECIDABLE, "ltr")
        val expanded = linkedMapOf("contain-intrinsic-inline-size" to "100px", "height" to "10px", "width" to "20px")
        val r = LogicalAliasResolution.collapse(expanded, mapOf("contain-intrinsic-inline-size" to 0, "height" to 1, "width" to 2), undecidable)
        assertSame(expanded, r.properties)
        assertTrue(r.kept.isEmpty(), "no pair → no Kept record, got ${r.kept}")
        assertTrue(r.dropped.isEmpty())
        // Same under a vertical mode with unrelated physical longhands present.
        val lone = LogicalAliasResolution.collapse(
            linkedMapOf("block-size" to "auto", "border-top-width" to "1px"),
            mapOf("block-size" to 0, "border-top-width" to 1), WritingContext("vertical-rl", "ltr")
        )
        assertTrue(lone.kept.isEmpty() && lone.dropped.isEmpty(), "lone logical longhand → nothing to report, got ${lone.kept}")
    }

    @Test
    fun `out-of-scope abstention reports one record per real pair present`() {
        // Two pairs and one lone logical longhand in one block: exactly two
        // records, each naming its own twin, in declaration order.
        val expanded = linkedMapOf(
            "block-size" to "auto", "height" to "150px",
            "margin-inline-start" to "1px", "margin-left" to "2px",
            "padding-block-end" to "3px"
        )
        val index = expanded.keys.withIndex().associate { (i, k) -> k to i }
        val r = LogicalAliasResolution.collapse(expanded, index, WritingContext("sideways-rl", "ltr"))
        assertEquals(listOf("block-size" to "height", "margin-inline-start" to "margin-left"), r.kept.map { it.logical to it.physical })
        assertTrue(r.kept.all { "outside the horizontal-tb collapse scope" in it.reason })
    }

    @Test
    fun `no writing context means no collapse (bucket call sites)`() {
        val out = PropertiesParser.parse(mapOf("block-size" to CssPropertyValue("auto"), "height" to CssPropertyValue("150px")))
        assertEquals(listOf("block-size", "height"), names(out))
    }

    @Test
    fun `a component without any pair returns the identical map instance`() {
        // Byte-stability argument: untouched fixtures flow through unchanged.
        val expanded = linkedMapOf("width" to "10px", "color" to "red", "margin-inline-start" to "1px")
        val r = LogicalAliasResolution.collapse(expanded, mapOf("width" to 0, "color" to 1, "margin-inline-start" to 2), WritingContext.ROOT)
        assertSame(expanded, r.properties)
        assertTrue(r.dropped.isEmpty() && r.kept.isEmpty())
    }

    @Test
    fun `equal values still collapse to one wire property`() {
        val out = parseBase(WritingContext.ROOT, "margin-inline-start" to "4px", "margin-left" to "4px")
        assertEquals(listOf("margin-left"), names(out))
    }

    @Test
    fun `a later shorthand outranks an earlier logical longhand and vice versa`() {
        // `margin` expands to four physical longhands that all carry the
        // shorthand's declaration index (css-cascade-4 §3: "exactly as if
        // expanded in place").
        val shorthandLater = parseBase(WritingContext.ROOT, "margin-inline-start" to "40px", "margin" to "4px")
        assertEquals(listOf("margin-top", "margin-right", "margin-bottom", "margin-left"), names(shorthandLater))
        val logicalLater = parseBase(WritingContext.ROOT, "margin" to "4px", "margin-inline-start" to "40px")
        assertEquals(listOf("margin-top", "margin-right", "margin-bottom", "margin-inline-start"), names(logicalLater),
            "only margin-left (the paired side) yields; the other three physical sides stay")
    }

    @Test
    fun `WritingContext derive follows own declarations and inherits the rest`() {
        val root = WritingContext.ROOT
        assertSame(root, WritingContext.derive(root, null))
        assertSame(root, WritingContext.derive(root, mapOf("color" to "red")))
        assertEquals(WritingContext("vertical-rl", "ltr"), WritingContext.derive(root, mapOf("writing-mode" to "vertical-rl")))
        assertEquals(WritingContext("vertical-rl", "ltr"), WritingContext.derive(root, mapOf("writing-mode" to "tb-rl")), "css-writing-modes-4 §3.2.1.1 obsolete-alias table")
        assertEquals(WritingContext("horizontal-tb", "rtl"), WritingContext.derive(root, mapOf("direction" to "RTL")), "keywords are case-insensitive")
        assertEquals(WritingContext("horizontal-tb", "rtl"), WritingContext.derive(root, mapOf("writingMode" to "lr-tb", "direction" to "rtl")), "camelCase name normalised")
        val parent = WritingContext("vertical-lr", "rtl")
        assertSame(parent, WritingContext.derive(parent, mapOf("writing-mode" to "inherit", "direction" to "unset")), "css-cascade-4 §7.3.2/§7.3.3 on inherited properties")
        assertEquals(root, WritingContext.derive(parent, mapOf("writing-mode" to "initial", "direction" to "initial")), "css-cascade-4 §7.3.1")
        assertFalse(WritingContext.derive(root, mapOf("writing-mode" to "var(--wm)")).isDecidable)
        assertFalse(WritingContext.derive(root, mapOf("direction" to "revert")).isDecidable, "revert needs the UA origin (dir attribute)")
        assertNull(LogicalAliasResolution.physicalFor("block-size", WritingContext("vertical-rl", "ltr")))
        assertEquals("height", LogicalAliasResolution.physicalFor("block-size", root))
        assertEquals("margin-right", LogicalAliasResolution.physicalFor("margin-inline-start", WritingContext("horizontal-tb", "rtl")))
    }

    // ── the tree: writing-mode inherits down children ───────────────────────

    @Test
    fun `a child inherits its ancestor's writing-mode for the pairing`() {
        // Parent declares vertical-rl; the child declares only the pair. Under
        // the parent's mode block-size ↔ WIDTH, so height is not its twin and
        // — being out of the horizontal-tb scope — both are kept. A sibling
        // subtree with no such ancestor collapses normally.
        val doc = Json.parseToJsonElement(
            """
            {"components":{
              "Vertical":{"properties":{"writing-mode":"vertical-rl"},
                          "children":{"Kid":{"properties":{"block-size":"auto","height":"150px"}}}},
              "Plain":{"properties":{"color":"red"},
                       "children":{"Kid":{"properties":{"block-size":"auto","height":"150px"}}}}
            }}
            """.trimIndent()
        ).jsonObject
        val ir = cssParsing(doc)
        val verticalKid = ir.components[0].children!!.single()
        assertEquals(listOf("block-size", "height"), names(verticalKid.properties), "vertical ancestor → abstain")
        val plainKid = ir.components[1].children!!.single()
        assertEquals(listOf("height"), names(plainKid.properties), "horizontal ancestor → later wins")
    }

    @Test
    fun `selector buckets keep both members`() {
        val doc = Json.parseToJsonElement(
            """{"components":{"A":{"properties":{"color":"red"},
                 "selectors":[{"selector":":hover","properties":{"block-size":"auto","height":"150px"}}]}}}"""
        ).jsonObject
        val comp = cssParsing(doc).components.single()
        assertEquals(listOf("block-size", "height"), names(comp.selectors.single().properties))
    }

    // ── VERBATIM corpus carriers named by the audit ─────────────────────────

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null && !File(dir, "fixtures/fidelity/pairwise/pairs-06.json").exists()) dir = dir.parentFile
        return assertNotNull(dir, "repo root (fixtures/) not found above ${System.getProperty("user.dir")}")
    }

    /** Convert one whole fidelity fixture and return the named component's property names. */
    private fun fixtureComponentNames(relPath: String, component: String): List<String> {
        val doc = Json.parseToJsonElement(File(repoRoot(), relPath).readText()).jsonObject
        val ir = cssParsing(doc)
        val c = ir.components.single { it.name == component }
        return names(c.properties)
    }

    @Test
    fun `pairs-06 PW_Sizing_Spacing_03 keeps the later height and width`() {
        // Fixture order: block-size:auto, inline-size:150px, min-block-size:80px,
        // height:150px, width:10em, background-color. The natives rendered a
        // 182px canvas (height won) and web 112px (block-size won) from the
        // wave-49 IR that carried both — the finding's headline cell.
        assertEquals(
            listOf("min-block-size", "height", "width", "background-color"),
            fixtureComponentNames("fixtures/fidelity/pairwise/pairs-06.json", "PW_Sizing_Spacing_03")
        )
    }

    @Test
    fun `sizing combos Sizing_BoxModel keeps the later block-size and inline-size`() {
        // Fixture order: width:240px, height:48px, padding, margin, border,
        // background-color, block-size:auto, inline-size:250px → the LOGICAL
        // pair is later here, so Width/Height are the members that yield.
        val out = fixtureComponentNames("fixtures/fidelity/sizing.combos.json", "Sizing_BoxModel")
        assertTrue("block-size" in out && "inline-size" in out, out.toString())
        assertTrue("width" !in out && "height" !in out, out.toString())
    }

    @Test
    fun `spacing combos Spacing_C03_MarginInlineEnd keeps the later margin-left`() {
        // Fixture order: margin-inline-end:20px, margin-inline-start:40px,
        // margin-left:4px, width, height, background-color. iOS/web placed the
        // box at x=56 (inline-start won) and Android at x=20 (margin-left won).
        assertEquals(
            listOf("margin-inline-end", "margin-left", "width", "height", "background-color"),
            fixtureComponentNames("fixtures/fidelity/spacing.combos.json", "Spacing_C03_MarginInlineEnd")
        )
    }
}
