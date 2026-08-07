package com.styleconverter.runtime.layout.position

// Wave 33 (lane C) — the CB-HEIGHT channel for AUTO-HEIGHT positioned
// ancestors, CSS 2.2 §10.6.3 read through css-position-3 §3.1.
//
// ## The measured defect
// css-tables/absolute-tables-007 is four lines of CSS:
//
//   <div style="position:relative; width:100px;">
//     <div style="position:absolute; display:table;
//                 width:100%; height:100%; background:green;"></div>
//     <div style="height:100px; background:red;"></div>
//   </div>
//
// The abspos table must cover the red block — "a positioned absolute
// table should resolve its %-height against its containing block" (the
// test's own assert). BOTH natives painted RED: the frozen wave32-final
// captures (tools/titan/runs/wave32-final/sections/css-tables/
// {android,ios}-screenshots/wpt__css-tables__absolute-tables-007.png)
// show a 100×100 RED square at (16, 88) where the Chromium ref
// (tools/wpt/refs/<sha>/white-black-ink-font-lh-imgpad-htmlpins/
// css-tables/absolute-tables-007.png) paints GREEN — Android 0.9850,
// iOS 0.9974, both FAIL on the colour veto. Web scored 0.9990 PASS.
//
// ## Why the green box had zero height
// The abspos child's `height: 100%` resolves against the containing
// block, which for an absolutely positioned box is the PADDING box of
// its nearest positioned ancestor (css-position-3 §3.1). Both natives
// derive that basis from the ancestor's DECLARED size: Compose through
// DynamicValueResolver.childContainingBlock, iOS through
// ContainingBlockBasis.paddingBox. The `position:relative` wrapper here
// declares only `width:100px` — its height is AUTO — so both channels
// answered null, the percentage never resolved, and the box collapsed
// (on Compose specifically: an unresolved percent Height reaches
// SizingApplier as fillMaxHeight(1.0), a documented no-op under the
// wave-8 unbounded abspos measure — see resolveOutOfFlowPercentSizes).
//
// But CSS 2.2 §10.5's "percentage heights against an auto-height
// containing block compute to auto" degradation is written for the
// containing block of an IN-FLOW box, which is a *content* box whose
// height genuinely is not known when the child is laid out. §3.1's
// abspos containing block is different: it is the ancestor's padding
// box, and §10.6.4 resolves an abspos percentage against its USED
// height — a value that IS known, because the ancestor's own in-flow
// content is measured before its out-of-flow descendants are placed.
// Chromium resolves it; that is what the ref shows.
//
// ## What this file publishes
// The ancestor's USED CONTENT height under the §10.6.3 auto-height
// rule: "the distance from the top content edge to the bottom margin
// edge of the last in-flow child" (block-level, non-replaced, normal
// flow, `overflow: visible`). The caller converts that to whatever box
// its own containing-block channel is denominated in and uses it ONLY
// as the fallback for the abspos lane when the declared-size channel
// answered null — the declared channel always wins, so nothing this
// file returns can override an author height.
//
// This is deliberately a STATIC evaluation of §10.6.3 over the IR, not
// a layout measurement: it is pure, testable on the JVM without
// Robolectric, and identical on both natives. The price is that it must
// REFUSE every shape whose block extent is not statically knowable —
// see H5, whose bail list is long on purpose. A refusal returns null,
// which is exactly the pre-wave-33 behaviour, so this file can only ever
// add resolution, never move a box that already resolved.
//
// ## Blast radius, enumerated before the change
// Scanning all 27 frozen wave32-final sections (324 tests, 2481
// components) for a POSITIONED ancestor with no definite block size that
// has a child able to observe a cb-height (a percentage Height /
// BlockSize / Top / Bottom / Margin* / Padding* / Min/MaxHeight, or an
// abspos both-insets block-axis stretch) yields exactly FIVE components:
//   • css-sizing abspos-auto-sizing-fit-content-percentage-005…008 —
//     their percent-height child is IN-FLOW, and the in-flow channel is
//     a different publication site on both natives (iOS's flow-children
//     `.environment(\.containingBlockHeight, …)` at the block/flex
//     branch; Compose's shared LocalContainingBlock read through the
//     NON-out-of-flow path). This file is wired ONLY into the abspos
//     lane, so those four cannot observe it.
//   • css-tables absolute-tables-007 — the one this repairs.
// Both natives additionally gate the call on WPT capture mode, so all
// 363 committed dark-stage baselines are byte-identical by construction.
//
// Pure decisions + arithmetic (Double), so the whole rule table is
// pinnable on the JVM. Twin: StyleEngine/layout/position/
// AbsposCbUsedHeight.swift.

import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.types.LengthValue
import com.styleconverter.runtime.core.types.extractLength
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

object AbsposCbUsedHeight {

    /**
     * One in-flow-candidate child, as this rule needs to see it: its own
     * declaration list. The caller passes children in DOCUMENT order —
     * §10.6.3's "last in-flow child" only means anything in that order,
     * and the sum below is order-independent only because H5 forces every
     * band to zero.
     */
    // (No wrapper type: a child IS its property list here. Keeping the
    // signature to `List<List<IRProperty>>` makes the iOS twin's
    // `[[IRProperty]]` byte-parallel with no bridging struct.)

    /**
     * The USED CONTENT height, in px, of a positioned box whose declared
     * block size is auto — or null when this rule refuses to answer.
     *
     * The pin table (mirrored on iOS byte-for-byte):
     *
     *  - H1 (positioned gate): the box must ESTABLISH an absolutely
     *    positioned containing block — `position` is one of relative /
     *    absolute / fixed / sticky (CSS 2.2 §10.1 item 4). A `static`
     *    box's height is irrelevant to any abspos descendant (they skip
     *    past it to the next positioned ancestor), so answering for one
     *    would publish a basis nobody may use.
     *
     *  - H2 (auto-height gate): any declared `Height` / `BlockSize` that
     *    is not the `auto` keyword → null. §10.6.3 IS the `height:auto`
     *    branch; a declared size is already answered — correctly, and
     *    with the author's number — by the existing declared-size
     *    channel, and this file must never race it. A declared
     *    `MinHeight` / `MaxHeight` also bails: §10.7 clamps the §10.6.3
     *    result and that clamp is not modelled here.
     *
     *  - H3 (block-container gate): §10.6.3 is written for block-level
     *    non-replaced elements in normal flow whose `overflow` is
     *    `visible`. So: a declared `Display` outside {BLOCK, FLOW_ROOT,
     *    INLINE_BLOCK} bails (flex / grid / table / multicol distribute
     *    their block axis by their own rules), a declared `OverflowX` /
     *    `OverflowY` other than `VISIBLE` bails (§10.6.7's shrink-to-fit
     *    branch), and a table-ish originating tag with NO declared
     *    display bails too — the converter does not serialize UA
     *    defaults, so `meta.sourceTag` is the only channel that sees a
     *    bare `<table>` (the same load-bearing tag lane
     *    [AbsposInsetStretch.isTableBox] documents).
     *
     *  - H4 (own-content gate): the ancestor's own text / inline runs
     *    make its content height depend on line-box layout (font metrics,
     *    wrapping, the harness's line-height pins), which no static rule
     *    can supply. `ancestorHasOwnContent` → null.
     *
     *  - H5 (per-child gate): children with `position: absolute|fixed`
     *    are OUT OF FLOW (CSS 2.2 §9.3.1) and contribute nothing — they
     *    are skipped, not bailed on (the abspos child asking the
     *    question is itself one of them). Every remaining child must be a
     *    statically-measurable block box or the WHOLE rule returns null:
     *      • a declared `Float` other than `NONE` → null. §10.6.3's
     *        overflow-visible branch explicitly does not extend to
     *        floats, and modelling float flow is out of scope.
     *      • a declared `Display: NONE` → null. It generates no box, so
     *        it should simply be skipped — but a bail is the conservative
     *        reading and no corpus box needs the skip.
     *      • any non-zero declared vertical margin, padding or border →
     *        null. Adjoining vertical margins COLLAPSE (CSS 2.2 §8.3.1),
     *        so a naive sum of margin boxes is wrong the moment a margin
     *        exists; and with a padding/border band present the declared
     *        height's box-sizing interpretation stops being neutral.
     *        Requiring all bands to be absent-or-zero makes both
     *        questions moot.
     *      • no `Height` / `BlockSize` readable as EXACT px → null. An
     *        auto / percentage / intrinsic / calc height is exactly the
     *        "not statically knowable" case (text, images, nested auto
     *        boxes all land here).
     *
     *  - H6 (the sum): with every band forced to zero by H5, §10.6.3's
     *    "top content edge → bottom margin edge of the last in-flow
     *    child" collapses to the plain sum of the in-flow children's
     *    declared px heights. absolute-tables-007: one in-flow child at
     *    100px → 100.
     *
     *  - H7 (empty-box refusal): zero in-flow children → null. §10.6.3
     *    does give 0 for a genuinely empty block, and wave 32's
     *    `zeroIsDefinite` established that a definite 0 is a real basis —
     *    but "no in-flow CHILDREN" is not the same as "no content": a box
     *    can hold pseudo-element boxes, markers, or generated content
     *    this list never sees. Refusing keeps the answer honest and keeps
     *    the blast radius at the one measured test.
     *
     * @param ancestor the positioned box's OWN resolved declarations.
     * @param ancestorTag its `meta.sourceTag`, for H3's UA-display lane.
     * @param ancestorHasOwnContent true when the box carries its own text
     *   or inline runs (H4).
     * @param children its children's declaration lists, DOCUMENT order.
     */
    fun contentHeightPx(
        ancestor: List<IRProperty>,
        ancestorTag: String?,
        ancestorHasOwnContent: Boolean,
        children: List<List<IRProperty>>,
    ): Double? {
        // H1 — the box must establish an abspos containing block.
        if (!establishesAbsposCb(ancestor)) return null
        // H2 — declared block size (or its min/max clamps) → not our rule.
        if (hasDeclaredBlockSize(ancestor)) return null
        if (hasAny(ancestor, MIN_MAX_BLOCK)) return null
        // H3 — block container, overflow visible, not a UA table box.
        if (!isAutoHeightBlockContainer(ancestor, ancestorTag)) return null
        // H4 — own line boxes are not statically measurable.
        if (ancestorHasOwnContent) return null
        // H5/H6 — sum the in-flow children, refusing anything unmeasurable.
        var sum = 0.0
        var inFlow = 0
        for (child in children) {
            // Out-of-flow children contribute nothing (§9.3.1) — skipped,
            // never a bail: the asking abspos box is one of these.
            if (isOutOfFlow(child)) continue
            // Floats, display:none, any non-zero band → refuse outright.
            if (!isStaticallyMeasurableBlock(child)) return null
            val h = exactPx(child, BLOCK_SIZE) ?: return null
            sum += h
            inFlow++
        }
        // H7 — an ancestor with no in-flow children says nothing honest.
        if (inFlow == 0) return null
        return sum
    }

    // ─────────────────────────── rule helpers ───────────────────────────

    /** Physical + logical spellings of the block-axis size (LTR
     *  horizontal-tb, the engine's normalization — css-logical-1 §4.1). */
    private val BLOCK_SIZE = listOf("Height", "BlockSize")

    /** §10.7's clamps on the §10.6.3 result — unmodelled, so H2 bails. */
    private val MIN_MAX_BLOCK =
        listOf("MinHeight", "MaxHeight", "MinBlockSize", "MaxBlockSize")

    /** Every band H5 requires to be absent-or-zero on an in-flow child. */
    private val CHILD_BLOCK_BANDS = listOf(
        "MarginTop", "MarginBottom", "MarginBlockStart", "MarginBlockEnd",
        "PaddingTop", "PaddingBottom", "PaddingBlockStart", "PaddingBlockEnd",
        "BorderTopWidth", "BorderBottomWidth",
        "BorderBlockStartWidth", "BorderBlockEndWidth",
    )

    /** Displays whose block axis IS the §10.6.3 stack. `INLINE_BLOCK` is
     *  in: css-display-3 §2 makes it a block CONTAINER (only its outer
     *  role is inline), and §10.6.3's content-height rule applies to its
     *  inside. Everything else — flex, grid, table, list-item, contents,
     *  the ruby family — distributes its block axis by other rules. */
    private val BLOCK_CONTAINER_DISPLAYS = setOf("BLOCK", "FLOW_ROOT", "INLINE_BLOCK")

    /** UA `display: table` tags (H3's tag lane). `<table>` is the only
     *  HTML element whose UA display is `table`; the internal table tags
     *  are listed too because a bare `<tr>`/`<td>` wrapper is a table box
     *  for this rule's purposes just as much as the table itself. */
    private val UA_TABLE_TAGS =
        setOf("table", "thead", "tbody", "tfoot", "tr", "td", "th", "caption", "col", "colgroup")

    /** H1 — `position` values that make this box the containing block of
     *  an absolutely positioned descendant (CSS 2.2 §10.1 item 4;
     *  `sticky` is css-position-3 §6.3's addition to the same list). */
    private fun establishesAbsposCb(properties: List<IRProperty>): Boolean {
        val kw = lastKeyword(properties, "Position") ?: return false
        return kw == "RELATIVE" || kw == "ABSOLUTE" || kw == "FIXED" || kw == "STICKY"
    }

    /** H2 — is there a declared block size that is not the `auto`
     *  keyword? Any readable shape counts (px, percent, calc, intrinsic):
     *  all of them are the DECLARED channel's business, not this one's. */
    private fun hasDeclaredBlockSize(properties: List<IRProperty>): Boolean {
        for (t in BLOCK_SIZE) {
            val data = properties.lastOrNull { it.type == t }?.data ?: continue
            // A bare/wrapped `auto` IS the initial value — keep looking.
            if (isAutoKeyword(data)) continue
            return true
        }
        return false
    }

    /** H3 — the §10.6.3 preconditions on the ancestor itself. */
    private fun isAutoHeightBlockContainer(
        properties: List<IRProperty>,
        tag: String?,
    ): Boolean {
        val display = lastKeyword(properties, "Display")
        if (display != null) {
            // A DECLARED display always wins (css-cascade order) — it also
            // overrides a table tag, per css-display-3 §2.
            if (display !in BLOCK_CONTAINER_DISPLAYS) return false
        } else if (tag?.lowercase() in UA_TABLE_TAGS) {
            // No declared display → the UA default decides. The converter
            // never serializes UA defaults, so this tag lane is the only
            // sighting of a bare <table> (same rationale as
            // AbsposInsetStretch.isTableBox's channel 2).
            return false
        }
        // §10.6.3's branch is the `overflow: visible` one; anything else
        // is §10.6.7 (shrink-to-fit / scroll container), unmodelled here.
        for (t in listOf("OverflowX", "OverflowY", "Overflow", "OverflowBlock")) {
            val kw = lastKeyword(properties, t) ?: continue
            if (kw != "VISIBLE") return false
        }
        return true
    }

    /** H5 — `position: absolute | fixed` takes a box OUT of the flow
     *  (CSS 2.2 §9.3.1), so it contributes nothing to §10.6.3's sum.
     *  `relative` and `sticky` stay in flow and DO contribute. */
    private fun isOutOfFlow(properties: List<IRProperty>): Boolean {
        val kw = lastKeyword(properties, "Position") ?: return false
        return kw == "ABSOLUTE" || kw == "FIXED"
    }

    /** H5 — can this in-flow child's block extent be summed as-is? */
    private fun isStaticallyMeasurableBlock(properties: List<IRProperty>): Boolean {
        // Floats leave the §10.6.3 overflow-visible sum entirely.
        lastKeyword(properties, "Float")?.let { if (it != "NONE") return false }
        // `display: none` generates no box — conservatively refuse.
        lastKeyword(properties, "Display")?.let { if (it == "NONE") return false }
        // Every vertical band must be absent or an explicit zero, so
        // margin collapsing (§8.3.1) and box-sizing both become moot.
        for (t in CHILD_BLOCK_BANDS) {
            val data = properties.lastOrNull { it.type == t }?.data ?: continue
            val px = (extractLength(data) as? LengthValue.Exact)?.px ?: return false
            if (px != 0.0) return false
        }
        return true
    }

    // ─────────────────────────── wire readers ───────────────────────────

    /** The LAST declaration of any of [types], read as EXACT px. Null for
     *  an absent, auto, percentage, intrinsic or calc shape — the strict
     *  read H5 needs (same honesty rule as
     *  AbsposAutoMargin.usedSizePx / AbsposInsetStretch.strictInsets). */
    private fun exactPx(properties: List<IRProperty>, types: List<String>): Double? {
        val data = properties.lastOrNull { it.type in types }?.data ?: return null
        return (extractLength(data) as? LengthValue.Exact)?.px
    }

    /** True when any of [types] is declared at all (readable or not). */
    private fun hasAny(properties: List<IRProperty>, types: List<String>): Boolean =
        properties.any { it.type in types }

    /** The LAST declaration of [type] as an UPPERCASE keyword string, or
     *  null when absent / not a keyword. Handles both the bare-primitive
     *  wire (`"data": "RELATIVE"`) and the `{"keyword": …}` wrapper. */
    private fun lastKeyword(properties: List<IRProperty>, type: String): String? {
        val data = properties.lastOrNull { it.type == type }?.data ?: return null
        val s = (data as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: ((data as? JsonObject)?.get("keyword") as? JsonPrimitive)?.contentOrNull
        return s?.uppercase()
    }

    /** Is this wire shape the `auto` keyword (bare or wrapped)? */
    private fun isAutoKeyword(data: kotlinx.serialization.json.JsonElement): Boolean {
        val s = (data as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: ((data as? JsonObject)?.get("keyword") as? JsonPrimitive)?.contentOrNull
        return s?.equals("auto", ignoreCase = true) == true
    }
}
