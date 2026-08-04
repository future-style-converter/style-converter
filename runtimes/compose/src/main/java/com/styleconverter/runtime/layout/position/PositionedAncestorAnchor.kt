package com.styleconverter.runtime.layout.position

// Wave 28 (lane NE) — END-EDGE anchoring for an out-of-flow box mounted in a
// POSITIONED ANCESTOR's overlay: the NESTED twin of the canvas-hoist slot.
//
// ## The measured defect (the wave-27 skeptic's queued find)
// The wave-22 end-inset rule table (EndInsetAnchor) was wired into exactly
// ONE mounting slot: CanvasRootHoist.canvasAnchor, whose overlay only ever
// hosts ROOT-level boxes (fixed, or absolute with no positioned ancestor).
// An absolute box that DOES have a positioned ancestor takes the wave-8/9
// path instead — ComponentRenderer.RenderAbsoluteChild →
// absposOverflowMeasure — which places EVERY child at the slot origin, i.e.
// the START anchor. The child's own PositionApplier offset is meanwhile
// `−right` / `−bottom` (PositionConfig.offsetX/offsetY: a negated inset that
// is only correct measured FROM the end edge), so a `right:20px;
// bottom:20px` child of a 300×120 `position:relative` parent painted at
// (−20,−20) instead of the css-position-3 §3.5.3 used position (220, 60).
// The live witness is fixtures/properties/layout/position-right-bottom.json
// (RB_Px / RB_Zero / RB_Negative — every variant whose only insets are
// right/bottom).
//
// ## iOS is the parity oracle — it already handles this
// PositionApplier.anchoredInsetOffset (runtimes/swiftui) wraps such a child
// in `.frame(maxWidth: .infinity, maxHeight: .infinity, alignment:
// .trailing/.bottom)` INSIDE the positioned parent's
// `.overlay(alignment:.topLeading){ ZStack … }` (ComponentRenderer stage 2),
// so the child FILLS the containing block, sits flush with its end edge, and
// the identical negated offset then pulls it inward. This file is that
// fill-and-anchor mount, mirrored for Compose — reusing EndInsetAnchor's
// A1–A6 arithmetic verbatim so the two Compose slots can never disagree
// about what "anchored from the end" means.
//
// ## Why FILL the anchored axis rather than align in the parent Box
// The mounting Box (ComponentRenderer's positioned-container branch) aligns
// its children at `Alignment.TopStart`, which is LAYOUT-DIRECTION-AWARE: any
// slack between the child's reported size and the Box would be handed to the
// RIGHT edge under LocalLayoutDirection=Rtl and silently double-count our own
// end placement. Reporting the containing block's extent on the anchored axis
// leaves NO slack, so the mount is direction-proof without touching the
// parent's alignment (and CSS insets stay physical — PositionApplier's
// absoluteOffset, wave 12). Un-anchored axes keep the wave-8 report exactly.
//
// Pure decisions + arithmetic (Int/Float), so the whole table is pinnable on
// the JVM without Robolectric — the standing constraint of this suite
// (EndInsetAnchorTest / CanvasRootHoistTest).

// Compose layout plumbing for the fill-and-anchor measurement wrapper.
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
// The raw inset wires the gate below inspects (see [anchorGate]) — read
// through the same extractor the config does, so "declared" here means
// exactly what PositionExtractor means by it.
import com.styleconverter.runtime.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement
// The wave-8 out-of-flow report rule (measured size coerced back into the
// incoming envelope) has ONE owner — reuse it instead of re-deriving, so the
// two mounts report identically for every un-anchored axis.
import com.styleconverter.runtime.core.renderer.ComponentRenderer

object PositionedAncestorAnchor {

    /**
     * Does this box need the end-anchored mount at all?
     *
     * False for every start-anchored / over-constrained / inset-free box
     * (EndInsetAnchor's A2/A3) and for every in-flow scheme — those keep
     * ComponentRenderer.absposOverflowMeasure byte-identically, which is the
     * frozen-baseline guarantee for all 363 committed captures.
     */
    fun anchors(config: PositionConfig): Boolean = config.anchorsFromEndAnyAxis

    /** The start-side inset wires per axis (physical + the LTR logical fold). */
    private val START_TYPES_X = setOf("Left", "InsetInlineStart")
    private val START_TYPES_Y = setOf("Top", "InsetBlockStart")

    /**
     * Is a START inset DECLARED on this axis but unreadable by
     * [ValueExtractors.extractDp]?
     *
     * The wire has three shapes for a declared inset: a resolved length
     * (`{"px":20.0}` / `{"original":{"v":1,"u":"EM"}}` — extractDp reads
     * both), the `auto` keyword (the CSS initial value, i.e. NOT declared
     * for anchoring purposes — css-position-3 §3.5.3 treats it as absent),
     * and a value that only the LIVE render context can compute:
     * `{"expr":"calc(10px + 5px)"}` / `{"expr":"var(--x)"}` /
     * `{"percentage":25.0}`. The third shape is the hazard this predicate
     * exists for.
     */
    private fun startInsetDeclaredButUnreadable(
        properties: List<Pair<String, JsonElement?>>,
        startTypes: Set<String>,
    ): Boolean = properties.any { (type, data) ->
        type in startTypes && data != null &&
            ValueExtractors.extractDp(data) == null &&
            // `auto` is the initial value — an all-auto axis is at its
            // static position (A3) and MUST stay eligible for the anchor.
            ValueExtractors.extractKeyword(data)?.lowercase() != "auto"
    }

    /**
     * The config this slot may anchor on — [PositionExtractor.extractPositionConfig]
     * with the end anchor WITHDRAWN from any axis whose start inset is
     * declared but unreadable from the raw wire.
     *
     * ## Why the raw config alone is not safe
     * This mount reads `child.properties`; the child's own style chain
     * (ComponentRenderer → LayoutFacade → PositionApplier) reads
     * `effectiveProperties`, i.e. the SAME list after
     * `DynamicValueResolver.resolve` has substituted `var()`, evaluated
     * `calc()` and rewritten em/rem/vw to px. For a length carrier like
     * `Left` those two disagree exactly when the declaration is an
     * `expr` wire: raw `left: calc(10px + 5px)` extracts to null, so
     * [PositionConfig.anchorsFromEndX] answers TRUE on a box that also
     * declares `right`, while the resolved chain reads left = 15dp and
     * emits `absoluteOffset(x = +15.dp)`. Anchor and offset then measure
     * from OPPOSITE edges and compose to `cb − box + 15` instead of 15 —
     * a 240px error on a 300×120 containing block, and a REGRESSION
     * against the pre-wave-28 start-anchored placement, which was right.
     *
     * Withdrawing the anchor degrades that axis to EndInsetAnchor's A4
     * (start anchor, byte-identical to wave 8), which is the honest
     * answer: an over-constrained box resolves in favour of the start
     * inset in LTR (css-position-3 §3.5.3), and a `%` start inset the
     * runtime cannot pre-compute is likewise not something this slot may
     * assume away. Not a silent fallthrough — it is the same degradation
     * A4 already names, applied one level earlier.
     */
    fun anchorGate(properties: List<Pair<String, JsonElement?>>): PositionConfig {
        var config = PositionExtractor.extractPositionConfig(properties)
        // Nulling the END inset is how an axis withdraws: both
        // anchorsFromEndX/Y read `resolvedEnd`/`resolvedBottom`, and this
        // config is consumed ONLY by this slot's anchor decision — the
        // offset half is re-extracted by the child's own style chain, so
        // nothing else can see the mask.
        if (startInsetDeclaredButUnreadable(properties, START_TYPES_X)) {
            config = config.copy(end = null, insetInlineEnd = null)
        }
        if (startInsetDeclaredButUnreadable(properties, START_TYPES_Y)) {
            config = config.copy(bottom = null, insetBlockEnd = null)
        }
        return config
    }

    /**
     * The containing-block extent this slot may anchor an axis to, in whole
     * device px — or null to keep the START anchor.
     *
     * [extentPx] is the mounting Box's own extent on that axis, which the
     * measure lambda reads off its incoming constraints (the positioned
     * parent's overlay Box is `fillMaxSize`, so its max constraint IS the
     * containing block Compose gives this child). Null when the constraint
     * is UNBOUNDED — EndInsetAnchor's A4 degradation, verbatim: with nothing
     * honest to measure from, the box keeps its pre-wave-28 start-anchored
     * placement rather than guessing. Not a silent fallthrough; the caller
     * that can supply an extent always does.
     */
    internal fun axisFillPx(anchorsFromEnd: Boolean, extentPx: Int?): Int? =
        // A2/A3 fold into the predicate (PositionConfig.anchorsFromEndX/Y);
        // A4 folds into the extent's nullability. One rule table, two slots.
        if (anchorsFromEnd) extentPx else null

    /**
     * The size this slot REPORTS to the mounting Box on one axis.
     *
     * On an anchored axis: the containing block's extent ([fillPx]) — the
     * Compose spelling of iOS's `.frame(maxWidth: .infinity)`. Filling the
     * axis is what makes the parent Box's direction-aware TopStart alignment
     * a no-op (see the file header) and costs the parent nothing: an
     * out-of-flow box never sizes its ancestors (css-position-3 §3), and the
     * mounting Box is `fillMaxSize` — already at that extent.
     *
     * On every other axis: the wave-8 rule, unchanged and un-duplicated —
     * the unbounded-measured ink coerced back into the incoming envelope.
     */
    internal fun axisReportPx(fillPx: Int?, inkPx: Int, minPx: Int, maxPx: Int): Int =
        fillPx ?: ComponentRenderer.absposReportedAxis(inkPx, minPx, maxPx)

    /**
     * Where the ink is PLACED inside this slot on one axis, in whole device
     * px — delegated to [EndInsetAnchor.placePx] so the nested mount and the
     * canvas-hoist mount share one arithmetic:
     *  - A5 anchored: `fill − ink`, putting the box's end edge flush with the
     *    containing block's end edge; the child's own PositionApplier offset
     *    (`−right` / `−bottom`) then pulls it inward to the used position
     *    `cb − box − right` (css-position-3 §3.5.3).
     *  - A6 un-anchored: 0 — the slot origin, byte-identical to the wave-8
     *    absposOverflowMeasure placement.
     * No floor at 0: an out-of-flow box may overflow its containing block
     * (§2.1) and Compose's place() draws unclipped, matching the refs.
     */
    internal fun axisPlacePx(fillPx: Int?, inkPx: Int): Int =
        EndInsetAnchor.placePx(fillPx?.toFloat(), inkPx)

    /**
     * Does the INLINE axis anchor from the end, given the containing
     * block's resolved layout direction?
     *
     * [PositionConfig] folds `inset-inline-end` into the PHYSICAL `end`
     * slot unconditionally — a documented LTR-horizontal-tb normalization
     * (see the config's class kdoc). Under `direction: rtl` that fold is
     * backwards: css-logical-1 §4.1 maps inline-end to the physical LEFT
     * edge, so `inset-inline-end: 10px` on an RTL containing block is a
     * 10px inset from the LEFT, not the right.
     *
     * The offset half of the composition is likewise physical and
     * unconditional (PositionApplier deliberately uses `absoluteOffset`,
     * wave 12), so this slot cannot RENDER the RTL case correctly — that
     * needs a direction-aware fold at extract time, which every consumer
     * of `resolvedEnd` shares (relative boxes included) and is renderer-
     * structure work, not this slot's.
     *
     * What this slot CAN do is not make it worse. Anchoring a logical-only
     * end inset at the physical RIGHT edge under RTL moves the box a full
     * containing block away from its used position. Worked out on
     * fixtures/properties/layout/audit-phase12.json's Inset_Logical_RTL
     * (300×120 parent, 80×40 child, `inset-inline-end/-block-end: 10px`) —
     * ARITHMETIC, not a capture; that fixture has no committed baseline and
     * this lane ran no device pass: the anchor would land it at x 210…290
     * where §4.1 puts it at x 10…90 and the pre-wave-28 start anchor put it
     * at x −10…70 (60 of its 80 px still overlapping the used position, vs
     * 0 for the anchored placement). So a LOGICAL-only inline-end inset withdraws
     * to the start anchor under RTL — EndInsetAnchor's A4 degradation,
     * byte-identical to wave 8. A PHYSICAL `right` is direction-independent
     * (css-position-3 §3.1) and keeps the end anchor either way.
     */
    internal fun inlineAxisAnchorsFromEnd(config: PositionConfig, isRtl: Boolean): Boolean =
        config.anchorsFromEndX &&
            // Physical `right` present ⇒ never withdrawn; only the logical
            // spelling depends on the direction the fold assumed.
            !(isRtl && config.end == null && config.insetInlineEnd != null)

    /**
     * The mount itself: the wave-8 unbounded measure (an out-of-flow box is
     * sized by its OWN properties against its containing block — §2.1, and
     * the reason absposOverflowMeasure exists) plus the per-axis fill-and-
     * anchor placement above.
     *
     * Rides the same `itemModifier` channel absposOverflowMeasure does —
     * RenderComponent installs it OUTERMOST, so the placeable measured here
     * is the child's full margin box with its own `absoluteOffset(−right,
     * −bottom)` already applied INSIDE it (absoluteOffset reports its
     * child's size unchanged, so the two compose additively and the anchor
     * lands the MARGIN edge, which is what `right` measures to per §3.5.3).
     *
     * Callers must gate on [anchors]: this modifier is only meaningful when
     * at least one axis anchors from the end, and un-anchored boxes must
     * keep absposOverflowMeasure so the frozen baselines stay byte-stable.
     */
    fun endAnchoredMeasure(config: PositionConfig): Modifier =
        Modifier.layout { measurable, constraints ->
            // Unbounded measure — the child's own width/height chain decides
            // its size (wave 8; the ink may legally overflow the parent).
            val ink = measurable.measure(Constraints())
            // The containing block per axis: the incoming max constraint,
            // which for the positioned parent's fillMaxSize overlay Box is
            // its content extent. Unbounded ⇒ null ⇒ A4 start anchor.
            val fillX = axisFillPx(
                // Direction-aware on the INLINE axis only — see
                // [inlineAxisAnchorsFromEnd]. `layoutDirection` here is the
                // one the containing block established (ComponentRenderer
                // provides LocalLayoutDirection from the parent's
                // `direction`), which is what css-logical-1 §4.1 resolves
                // an inline-* inset against.
                inlineAxisAnchorsFromEnd(
                    config,
                    layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl,
                ),
                if (constraints.hasBoundedWidth) constraints.maxWidth else null,
            )
            val fillY = axisFillPx(
                config.anchorsFromEndY,
                if (constraints.hasBoundedHeight) constraints.maxHeight else null,
            )
            // Report: the extent on an anchored axis (no slack for the
            // parent's direction-aware alignment to hand out), the wave-8
            // constraint-fitting report on every other axis.
            layout(
                axisReportPx(fillX, ink.width, constraints.minWidth, constraints.maxWidth),
                axisReportPx(fillY, ink.height, constraints.minHeight, constraints.maxHeight),
            ) {
                // Place: flush with the containing block's end edge on an
                // anchored axis (A5), at the slot origin otherwise (A6).
                ink.place(
                    x = axisPlacePx(fillX, ink.width),
                    y = axisPlacePx(fillY, ink.height),
                )
            }
        }
}
