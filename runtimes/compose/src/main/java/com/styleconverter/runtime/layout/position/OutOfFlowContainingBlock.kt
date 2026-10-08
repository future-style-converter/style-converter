package com.styleconverter.runtime.layout.position

// Wave 54 (lane L4, unit OOF-android) — the NON-transform clauses that make a
// box the containing block of its absolutely AND fixed positioned
// descendants, plus the inset test that gives an all-auto fixed box its
// static position. Until this file existed the only non-`position` clause
// either native read was the transform family (TransformContainingBlock,
// wave 35), so a fixed/abspos box under `contain: content` or
// `backdrop-filter` escaped to the canvas: css-contain/contain-content-003
// android painted its two green halves at the canvas top-right and left the
// 100×100 `contain` box bare red (wave53-final android f 0.9405; brief
// tools/titan/results/wave54-plan/oof-containing-block.md §1).
//
// THIS FILE IS A TWIN. Its Swift mirror is
//   runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/layout/
//     position/OutOfFlowContainingBlock.swift
// pinned row-for-row by OutOfFlowContainingBlockTest.kt /
// OutOfFlowContainingBlockTests.swift. Change one, change both.
//
// The clauses (each a spec sentence, cited at its reader below):
//   • css-contain-2 §3.2 / §3.4 — layout and paint containment ("the element
//     acts as a containing block for absolutely positioned and fixed
//     positioned descendants"); `strict` and `content` include both;
//   • filter-effects-1 §5 — `filter` other than none;
//   • filter-effects-2 §2 — `backdrop-filter` other than none;
//   • css-will-change-1 §3 — a `will-change` hint naming one of the above.
// [establishes] is consumed ONLY through CanvasRootHoist
// .establishesTransformContainingBlock (one OR), so the composition channel
// and both pure walks read one answer.

// PropertyTracker: the no-silent-fallthrough breadcrumbs below.
import com.styleconverter.runtime.PropertyTracker
// IR model — every decision here is PURE over the wire (JVM-pinned).
import com.styleconverter.runtime.core.ir.IRProperty
// The one existing `contain` decoder (strict/content expansion lives there).
import com.styleconverter.runtime.performance.PerformanceExtractor
// Raw wire leaf readers: "did the author declare a non-none value" is a
// question about the serialized shape, not about a resolved default.
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** The non-transform containing-block rule table (see file header). */
object OutOfFlowContainingBlock {

    /** Recorded when a clause fires on an `absolute`/`fixed` box and the claim is withheld. */
    const val POSITIONED_ESTABLISHER_BREADCRUMB =
        "ContainingBlock[positioned-establisher-fixed-descendant]"

    /** Recorded when a `contain` leaf cannot be decoded (the claim is then false). */
    const val CONTAIN_DECODE_BREADCRUMB = "ContainingBlock[contain-decode-failed]"

    /**
     * Recorded when a box declares a property that IMPLIES layout/paint
     * containment through another rule (css-contain-2 §4 content-visibility,
     * css-contain-3 container-type) — a containing-block clause this table
     * names but does not claim yet (0 corpus out-of-flow carriers).
     */
    const val IMPLIED_CONTAINMENT_BREADCRUMB = "ContainingBlock[implied-containment-unclaimed]"

    /** Types that can carry a clause; necessary, not sufficient (each has a none). */
    internal val CANDIDATE_TYPES = setOf("Contain", "Filter", "BackdropFilter", "WillChange")

    /** Types whose implied containment is named but not claimed (see breadcrumb). */
    private val IMPLIED_TYPES = setOf("ContentVisibility", "ContainerType")

    /** The eight inset longhands the converter emits (PositionExtractor's own list). */
    private val INSET_TYPES = setOf(
        "Top", "Right", "Bottom", "Left",
        "InsetBlockStart", "InsetBlockEnd", "InsetInlineStart", "InsetInlineEnd",
    )

    /**
     * True when at least one clause fires AND the box is not itself
     * `position: absolute | fixed`.
     *
     * Why the restriction: the corpus's four positioned backdrop-filter
     * containers (backdrop-filter-clip-rect / -clip-rect-zoom / -edge-clipping
     * / -zero-size) hold ABSOLUTE children only, whose containing block they
     * already are through `position`. Claiming them here would route them into
     * ComponentRenderer's positioned-container Box branch (gated on this very
     * predicate) and move cells the lane must not move. The cost is
     * spec-visible only for a FIXED descendant of such a box (it still hoists
     * to the viewport) — 0 corpus carriers — so the withheld claim is
     * breadcrumbed, never silent.
     */
    fun establishes(properties: List<IRProperty>): Boolean {
        // Named-but-unclaimed implied containment: breadcrumb only, no pixel.
        if (properties.any { it.type in IMPLIED_TYPES }) PropertyTracker.markUnhandled(IMPLIED_CONTAINMENT_BREADCRUMB)
        // Fast bail — the corpus is dominated by boxes with no candidate type.
        if (properties.none { it.type in CANDIDATE_TYPES }) return false
        // One pass, first firing clause wins; each type has its own none test.
        val fires = properties.any { p ->
            when (p.type) {
                "Contain" -> containsLayoutOrPaint(p)
                "Filter", "BackdropFilter" -> filterListIsUsed(p)
                "WillChange" -> willChangeHintsContainingBlock(p)
                else -> false
            }
        }
        // No clause fired: not an establisher.
        if (!fires) return false
        // The box's own position, through the same decoder the hoist uses.
        val own = PositionExtractor.extractPositionConfig(properties.map { it.type to it.data }).type
        // Positioned establisher: withhold the claim (kdoc), leave a breadcrumb.
        if (own == PositionType.ABSOLUTE || own == PositionType.FIXED) {
            PropertyTracker.markUnhandled(POSITIONED_ESTABLISHER_BREADCRUMB)
            return false
        }
        // A static/relative/sticky establisher claims both out-of-flow classes.
        return true
    }

    /**
     * Does the box declare ANY inset other than `auto`? css-position-3 §3.5.3
     * gives an out-of-flow box its static position only when its insets are
     * `auto`; the wire omits an undeclared inset and serializes `auto` as the
     * primitive "auto" (converter InsetValueSerializer). Deliberately NOT
     * CanvasRootHoist.hasAnyInset, which counts only RESOLVED px: an
     * unresolvable `calc(anchor(…))` inset (anchor-center-safe-rtl's fixed
     * `__4`) IS declared, so that box keeps its viewport hoist. Read by the
     * FIXED arm of the hoist and by the RC1 static-position class.
     */
    fun declaresAnyInset(properties: List<IRProperty>): Boolean =
        properties.any { it.type in INSET_TYPES && (it.data as? JsonPrimitive)?.contentOrNull != "auto" }

    /**
     * `contain` — css-contain-2 §3.2 (layout) / §3.4 (paint) each make the box
     * a containing block for absolute AND fixed descendants; `strict` and
     * `content` expand to sets that include both, `size`/`inline-size`/
     * `style`/`none` do not. Decoded through PerformanceExtractor (whose
     * wave-36 token expansion handles the bare-array wire `["CONTENT"]`).
     * Guarded: the hoist walk runs during composition with no error boundary
     * (the wave-49 ClipDecodeGuard lesson), so a malformed leaf answers false
     * with a breadcrumb instead of throwing.
     */
    private fun containsLayoutOrPaint(p: IRProperty): Boolean = try {
        // One-property decode — only the Contain leaf is read.
        val contain = PerformanceExtractor.extractPerformanceConfig(listOf(p.type to p.data)).contain
        // Layout OR paint containment establishes (§3.2 / §3.4).
        contain.layout || contain.paint
    } catch (e: IllegalArgumentException) {
        // kotlinx `jsonPrimitive` on a non-primitive leaf: not decodable → no claim.
        PropertyTracker.markUnhandled(CONTAIN_DECODE_BREADCRUMB)
        false
    }

    /**
     * `filter` (filter-effects-1 §5: "A value other than none for the filter
     * property results in the creation of a containing block for absolute and
     * fixed positioned descendants") and `backdrop-filter` (filter-effects-2
     * §2, the same clause). Wire: a function list is an array — the converter
     * serializes `backdrop-filter: none` as an EMPTY array, `filter: none` as
     * the primitive "none"; `url(…)` / an unresolved expression is an object,
     * treated as establishing (the author wrote something other than none —
     * the TransformContainingBlock conservatism); any primitive (none / a
     * CSS-wide keyword) does not.
     */
    private fun filterListIsUsed(p: IRProperty): Boolean = when (val d = p.data) {
        is JsonArray -> d.isNotEmpty()
        is JsonObject -> true
        else -> false
    }

    /**
     * `will-change` — css-will-change-1 §3: a hint naming a property whose
     * non-initial value would create a containing block must create one. The
     * leaf is `[{"type":"property-name","name":"filter"}]`; `transform` /
     * `perspective` are TransformContainingBlock's, `opacity` creates none.
     */
    private fun willChangeHintsContainingBlock(p: IRProperty): Boolean {
        // Non-array leaves (`auto`) carry no property hint.
        val arr = p.data as? JsonArray ?: return false
        // Any hint object whose name is one of the three clauses above.
        return arr.any { hint ->
            val name = ((hint as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull
            name == "filter" || name == "backdrop-filter" || name == "contain"
        }
    }
}
