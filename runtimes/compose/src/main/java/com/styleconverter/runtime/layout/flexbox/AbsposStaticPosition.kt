package com.styleconverter.runtime.layout.flexbox

// AbsposStaticPosition — wave 19 (lane FLEX): the FULL static-position
// resolver for an absolutely-positioned child of a flex container.
//
// The wave-18 AbsposStaticAlignment half handled only the CROSS axis and
// only under the runtime's LTR horizontal-tb normalization (row-reverse
// folded to row, writing-mode ignored) — the WPT proof being
// flex-abspos-staticpos-align-self-safe-001/002/003, where the refs
// anchor vertical-rl containers at the RIGHT edge and row-reverse
// containers at their main-END edge while both natives painted four
// identical top-left boxes (natives 0.87–0.92 vs web).
//
// This file adds the ONE axis-mapping table (css-flexbox-1 §5: main axis
// = inline for row/row-reverse and block for column/column-reverse, each
// direction derived from writing-mode + direction per css-writing-modes-4
// §2) and resolves BOTH physical axes:
//   • the CROSS axis from the child's align-self (both wire channels —
//     typed AlignSelf and the Generic safe/unsafe escape hatch, exactly
//     the wave-18 readers);
//   • the MAIN axis from the container's justify-content applied to the
//     sole hypothetical item (css-position-3 §3.5.3 / css-flexbox-1
//     §4.1: the abspos child is positioned as if it were the sole flex
//     item, so justify-content's fallback alignment places it).
//
// Shared-semantics contract: AbsposStaticPosition.swift carries the
// byte-parallel twin (same types, same table, same axisOffset math) and
// both are pinned by unit tables with IDENTICAL inputs/expected offsets
// (AbsposStaticPositionTest / AbsposStaticPositionTests) — the exact
// per-sub-container offsets of the three failing WPT fixtures.

import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.types.ValueExtractors
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

object AbsposStaticPosition {

    /** One PHYSICAL axis of a resolved static position: the LOGICAL
     *  claim (base + safe, wave-18 semantics) plus whether the logical
     *  axis runs AGAINST the physical +x/+y direction — reversal flips
     *  start/end at offset time, and flips the `safe` overflow fallback
     *  to the physical END edge (logical start of a reversed axis). */
    data class AxisSpec(
        val base: AbsposStaticAlignment.Base,
        val safe: Boolean,
        val reversed: Boolean
    )

    /** Both physical axes of the static position. A null axis means "no
     *  claim" — either an explicit inset replaces the static position
     *  there (css-position-3 §3.5) or nothing aligns that axis (the
     *  caller keeps its legacy anchor / container-alignment inheritance).
     *  [justifyTyped] is true when the container declared justify-content
     *  on the TYPED wire — the Compose loops must then leave the
     *  arrangement axis to the Row/Column arrangement (which already
     *  moves the reported box for typed keywords) to avoid a double
     *  offset; Generic-only claims never reach the arrangement, so they
     *  are safe to place internally. The iOS overlay path has no
     *  arrangement and ignores the flag. */
    data class StaticPos(
        val x: AxisSpec?,
        val y: AxisSpec?,
        val justifyTyped: Boolean
    )

    /** The css-flexbox-1 §5 axis derivation, collapsed to physical
     *  booleans: which physical axis is MAIN, and whether each flex axis
     *  runs against its physical +direction. Cross is always the other
     *  physical axis, so only its reversal is carried. */
    data class AxisMap(
        val mainIsHorizontal: Boolean,
        val mainReversed: Boolean,
        val crossReversed: Boolean
    )

    /**
     * The ONE axis-mapping table. Inputs are the RAW wire keywords
     * (never the renderer's folded enums — the ComponentRenderer fold of
     * *_REVERSE at extractDisplayConfig stays for composable choice, but
     * the resolver must see reversal). Wire shapes pinned against the
     * LIVE converter: FlexDirection "ROW"/"ROW_REVERSE"/"COLUMN"/
     * "COLUMN_REVERSE"; WritingMode "HORIZONTAL_TB"/"VERTICAL_RL"/
     * "VERTICAL_LR" (sideways-* folds to the matching vertical-* — the
     * runtime's documented approximation); Direction "LTR"/"RTL".
     * Null keywords take the CSS initial values (row / horizontal-tb /
     * ltr — css-flexbox-1 §5.1, css-writing-modes-4 §2).
     */
    fun axisMap(flexDirection: String?, writingMode: String?, direction: String?): AxisMap {
        // Normalize wire spellings once (typed UPPER_SNAKE + raw hyphen).
        val fd = flexDirection?.uppercase()?.replace('-', '_')
        val wm = writingMode?.uppercase()?.replace('-', '_')
        val dir = direction?.uppercase()
        // Vertical writing: the inline axis is physical-vertical and the
        // block axis physical-horizontal (css-writing-modes-4 §2.4).
        val verticalWriting = wm == "VERTICAL_RL" || wm == "VERTICAL_LR" ||
            wm == "SIDEWAYS_RL" || wm == "SIDEWAYS_LR"
        // Block axis runs right→left (against +x) for the *-rl modes.
        val blockReversed = wm == "VERTICAL_RL" || wm == "SIDEWAYS_RL"
        // Inline axis runs against its +direction under direction: rtl.
        val inlineReversed = dir == "RTL"
        // Row-like: main axis = inline axis (css-flexbox-1 §5.1); the
        // CSS initial (absent wire) is row.
        val rowLike = fd == null || fd == "ROW" || fd == "ROW_REVERSE"
        // *_REVERSE flips the MAIN axis direction only (§5.1) — the
        // cross axis direction comes from the writing mode alone
        // (flex-wrap: wrap-reverse would flip it; the runtime's abspos
        // static position does not model wrap-reverse — documented gap).
        val dirReverse = fd == "ROW_REVERSE" || fd == "COLUMN_REVERSE"
        // Main physical orientation: inline is horizontal exactly when
        // the writing mode is horizontal; block is the complement.
        val mainIsHorizontal = if (rowLike) !verticalWriting else verticalWriting
        // Main reversal composes the axis's own physical direction with
        // the *_REVERSE flip (two flips cancel — column-reverse in
        // vertical-rl runs left→right, the safe-002 C3 ref pin).
        val mainReversed = (if (rowLike) inlineReversed else blockReversed) != dirReverse
        // Cross axis = the other logical axis, never *_REVERSE-flipped.
        val crossReversed = if (rowLike) blockReversed else inlineReversed
        return AxisMap(mainIsHorizontal, mainReversed, crossReversed)
    }

    /**
     * Resolve the FULL physical static position claims for one abspos
     * child of a flex container. Container side: raw FlexDirection /
     * WritingMode / Direction + justify-content (both wire channels).
     * Child side: align-self (both channels, wave-18 readers) + the
     * per-PHYSICAL-axis inset gate (css-position-3 §3.5: a non-auto
     * inset on an axis replaces the static position there).
     */
    fun resolveStatic(
        containerProperties: List<IRProperty>,
        childProperties: List<IRProperty>
    ): StaticPos {
        // The raw wire keywords — folded enums would erase reversal.
        val map = axisMap(
            flexDirection = keyword(containerProperties, "FlexDirection"),
            writingMode = keyword(containerProperties, "WritingMode"),
            direction = keyword(containerProperties, "Direction")
        )
        // MAIN claim: declared justify-content as sole-item fallback, or
        // the synthesized default start (justify-content: normal behaves
        // as flex-start in flex layout — css-align-3 §5.1). The default
        // claim is what anchors reverse containers at their main-END
        // edge (safe-002) — reversal lives in the AxisSpec, not here.
        val justify = justifySpec(containerProperties)
        val mainSpec = justify ?: AbsposStaticAlignment.Spec(
            AbsposStaticAlignment.Base.START, safe = false)
        // CROSS claim: the child's align-self via the wave-18 readers.
        val crossSpec = AbsposStaticAlignment.resolveCross(childProperties)
        // §3.5 inset gates are PHYSICAL — check the axis each claim
        // actually lands on (main is vertical when not horizontal).
        // Auto-tolerant reader (wave-19 skeptic fix): an EXPLICIT
        // `top: auto` KEEPS the static position (§3.5 only replaces it
        // for non-auto values), so it must not trip the gate.
        val main = if (hasNonAutoInset(
                childProperties, vertical = !map.mainIsHorizontal)) null
            else AxisSpec(mainSpec.base, mainSpec.safe, map.mainReversed)
        val cross = if (crossSpec == null || hasNonAutoInset(
                childProperties, vertical = map.mainIsHorizontal)) null
            else AxisSpec(crossSpec.base, crossSpec.safe, map.crossReversed)
        // Physical assignment: main lands on x when horizontal, else y.
        return if (map.mainIsHorizontal)
            StaticPos(x = main, y = cross, justifyTyped = typedJustifyDeclared(containerProperties))
        else
            StaticPos(x = cross, y = main, justifyTyped = typedJustifyDeclared(containerProperties))
    }

    /**
     * The container's justify-content as a SOLE-ITEM alignment claim.
     * Typed wire first (JustifyContentPropertyParser keywords), then the
     * Generic escape hatch for `safe|unsafe <pos>` values (same grammar
     * AbsposStaticAlignment.parseRaw pins for align-self — css-align-3
     * treats the two longhands as one grammar on two axes). Sole-item
     * folds per css-align-3 §5.1: space-between behaves as flex-start
     * and space-around/evenly center the lone item.
     */
    internal fun justifySpec(containerProperties: List<IRProperty>): AbsposStaticAlignment.Spec? {
        // Channel 1 — typed keyword ("CENTER", "SPACE_BETWEEN", …).
        keyword(containerProperties, "JustifyContent")?.let { kw ->
            when (kw.uppercase().replace('-', '_')) {
                // start-family + the LTR left fold (css-align-3 §5.2).
                "FLEX_START", "START", "LEFT", "NORMAL", "STRETCH" ->
                    return AbsposStaticAlignment.Spec(AbsposStaticAlignment.Base.START, false)
                "CENTER" ->
                    return AbsposStaticAlignment.Spec(AbsposStaticAlignment.Base.CENTER, false)
                // end-family + the LTR right fold.
                "FLEX_END", "END", "RIGHT" ->
                    return AbsposStaticAlignment.Spec(AbsposStaticAlignment.Base.END, false)
                // Sole item: space-between → start; around/evenly → center
                // (css-align-3 §5.1 distribution fallback alignments).
                "SPACE_BETWEEN" ->
                    return AbsposStaticAlignment.Spec(AbsposStaticAlignment.Base.START, false)
                "SPACE_AROUND", "SPACE_EVENLY" ->
                    return AbsposStaticAlignment.Spec(AbsposStaticAlignment.Base.CENTER, false)
                // Unknown typed value → treat as undeclared (no claim).
                else -> Unit
            }
        }
        // Channel 2 — Generic `justify-content: safe|unsafe <pos>`.
        containerProperties.forEach { p ->
            if (p.type != "Generic") return@forEach
            val obj = p.data as? JsonObject ?: return@forEach
            if ((obj["propertyName"] as? JsonPrimitive)?.contentOrNull != "justify-content") return@forEach
            val raw = (obj["rawValue"] as? JsonPrimitive)?.contentOrNull ?: return@forEach
            // Shared safe/unsafe grammar reader (wave-18, pinned).
            return AbsposStaticAlignment.parseRaw(raw)
        }
        // Undeclared → the caller synthesizes the default start claim.
        return null
    }

    /** True when justify-content shipped on the TYPED wire — the only
     *  channel FlexboxExtractor feeds the Row/Column arrangement from,
     *  hence the only case where internal placement would double-count
     *  the arrangement's own displacement (see [StaticPos]). */
    internal fun typedJustifyDeclared(containerProperties: List<IRProperty>): Boolean =
        containerProperties.any { it.type == "JustifyContent" }

    /**
     * The PHYSICAL offset (px) of the child's margin box from the
     * container's physical start (left/top) edge on one axis:
     *
     *   logical = crossOffset(child, container, base, safe)  — wave-18
     *   physical = reversed ? free − logical : logical
     *
     * The reversal maps logical-start to the physical END edge, so the
     * `safe` overflow fallback (logical start) correctly anchors a
     * vertical-rl container's child at its RIGHT edge with the excess
     * spilling left (negative x — the safe-001 C2/C3 ref geometry).
     * Byte-identical Doubles to the SwiftUI twin for the same inputs.
     */
    fun axisOffset(childPx: Double, containerPx: Double, spec: AxisSpec): Double {
        // The wave-18 logical math (start 0 / center ½ / end 1 + safe
        // fallback), unchanged and already twin-pinned.
        val logical = AbsposStaticAlignment.crossOffset(
            childPx, containerPx,
            AbsposStaticAlignment.Spec(spec.base, spec.safe))
        // Physical flip for reversed axes: mirror inside the free space.
        return if (spec.reversed) (containerPx - childPx) - logical else logical
    }

    /**
     * css-position-3 §3.5 inset gate for the WPT resolver — like
     * [AbsposStaticAlignment.hasCrossInset], but an EXPLICIT `auto`
     * inset does NOT count: the live converter ships `top: auto` as
     * {"type":"Top","data":"auto"} (pinned 2026-07-26) and §3.5's
     * replacement of the static position only happens for NON-auto
     * inset values — an auto inset keeps the static position. The
     * wave-18 reader stays byte-untouched for the dark-stage path
     * (frozen-baseline stability); this WPT-only twin is mirrored in
     * AbsposStaticPosition.swift.
     */
    internal fun hasNonAutoInset(properties: List<IRProperty>, vertical: Boolean): Boolean {
        // The IR type names that pin this axis (physical + logical) —
        // same table as the wave-18 reader.
        val axisTypes = if (vertical)
            setOf("Top", "Bottom", "InsetBlockStart", "InsetBlockEnd")
        else
            setOf("Left", "Right", "InsetInlineStart", "InsetInlineEnd")
        // Only NON-auto values replace the static position (§3.5);
        // px-shaped objects extract no keyword and therefore count.
        return properties.any { p ->
            p.type in axisTypes &&
                ValueExtractors.extractKeyword(p.data)?.equals("auto", ignoreCase = true) != true
        }
    }

    /**
     * The container's definite CONTENT extent (px) on one axis, read
     * from the typed length wire ({"type":"length","px":N} — the frozen
     * sizing shape) or the LOGICAL longhand's bare {"px":N} shape (the
     * live converter emits InlineSize/BlockSize without the "length"
     * discriminator — pinned 2026-07-26). Null for auto/percent/keyword
     * sizes — the caller then keeps the legacy fits-fallback (align
     * modifier) instead of inventing a basis. The logical→physical
     * mapping is WRITING-MODE aware (css-logical-1 §4.1: inline = width
     * only under horizontal writing; vertical writing swaps the pair) —
     * the physical Width/Height wires always map to their own axis.
     * Under the WPT content-box default the declared slot IS the content
     * extent; the resolver is only activated in WPT capture mode
     * (ComponentRenderer), so the border-box dark-stage interpretation
     * never reaches this read.
     */
    fun definiteExtentPx(properties: List<IRProperty>, vertical: Boolean): Double? {
        // Which logical size lands on this physical axis: inline is
        // vertical exactly under vertical writing modes (§2.4).
        val wm = keyword(properties, "WritingMode")?.uppercase()?.replace('-', '_')
        val verticalWriting = wm == "VERTICAL_RL" || wm == "VERTICAL_LR" ||
            wm == "SIDEWAYS_RL" || wm == "SIDEWAYS_LR"
        val logical = if (vertical == verticalWriting) "InlineSize" else "BlockSize"
        val types = setOf(if (vertical) "Height" else "Width", logical)
        properties.forEach { p ->
            if (p.type !in types) return@forEach
            val obj = p.data as? JsonObject ?: return@forEach
            val discriminator = (obj["type"] as? JsonPrimitive)?.contentOrNull
            // Definite = typed exact length, or the bare {"px":N} shape
            // (no discriminator at all). percentage/expression/original
            // wires are indefinite here.
            if (discriminator != "length" && !(discriminator == null && "px" in obj)) return@forEach
            return (obj["px"] as? JsonPrimitive)?.doubleOrNull
        }
        return null
    }

    /** First matching typed keyword for [type] — the same tolerant
     *  reader every extractor lane uses (primitive/object carriers). */
    private fun keyword(properties: List<IRProperty>, type: String): String? =
        properties.firstOrNull { it.type == type }
            ?.let { ValueExtractors.extractKeyword(it.data) }
}
