package com.styleconverter.runtime.layout.flexbox

// AbsposStaticAlignment — the STATIC-POSITION alignment of an
// absolutely-positioned child of a flex container (lane FLEX-SAFE).
//
// css-position-3 §3.1: when an abspos box has auto insets, its position
// is its STATIC POSITION — for a flex-container parent, "the position
// the box would have if it were the sole flex item" (css-flexbox-1
// §4.1), i.e. the container's align/justify values place a hypothetical
// item of the child's size. css-align-3 §4.4 then defines the overflow
// keywords: `safe <pos>` falls back to START alignment when the box
// OVERFLOWS its alignment container; `unsafe <pos>` (and the bare
// keyword, whose default overflow behaviour here matches unsafe) keeps
// the requested alignment even when that overflows both edges.
//
// Wire shapes (pinned against the LIVE converter, 2026-07-18):
//   align-self: center        → {"type":"AlignSelf","data":"CENTER"}
//   align-self: safe center   → {"type":"Generic","data":{"propertyName":
//                                "align-self","rawValue":"safe center",
//                                "_unmapped":true}}
//   align-self: unsafe center → Generic, rawValue "unsafe center"
//   align-self: safe end      → Generic, rawValue "safe end"
// (AlignSelfPropertyParser recognises only bare keywords, so every
// overflow-modifier value ships through the Generic escape hatch.)
//
// The math half (crossOffset) is deliberately platform-free Double
// arithmetic: the SwiftUI runtime carries the byte-equivalent
// AbsposStaticAlignment.swift and both are pinned by unit tests with
// IDENTICAL inputs/expected offsets, so the two natives cannot drift.

import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.types.ValueExtractors
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

object AbsposStaticAlignment {

    /** Alignment base after stripping overflow keywords — css-align-3
     *  §4.2's <self-position> space collapsed to the three physical
     *  outcomes a static position can take on one axis. */
    enum class Base { START, CENTER, END }

    /** One resolved cross-axis claim: the base position plus whether the
     *  `safe` overflow keyword was present (css-align-3 §4.4). */
    data class Spec(val base: Base, val safe: Boolean)

    /**
     * Resolve the abspos static-position CROSS alignment from a child's
     * IR list, reading BOTH wire channels: the typed AlignSelf keyword
     * (plain values) and the Generic escape hatch (safe/unsafe values —
     * see the pinned shapes in the header). Returns null when the child
     * declares no align-self, or a value with no static-position mapping
     * (auto/stretch/baseline keep the caller's legacy behaviour:
     * css-flexbox-1 §4.1 treats them as flex-start for abspos children).
     */
    fun resolveCross(properties: List<IRProperty>): Spec? {
        // Channel 1 — typed wire: bare keyword primitive ("CENTER").
        properties.firstOrNull { it.type == "AlignSelf" }?.let { prop ->
            // extractKeyword tolerates primitive/object carriers, same
            // reader ItemPlacementExtractor.alignSelf uses.
            val kw = ValueExtractors.extractKeyword(prop.data)
            // A typed keyword never carries an overflow modifier (the
            // parser rejects two-token values), so safe is false.
            baseOf(kw)?.let { return Spec(it, safe = false) }
            // Typed but unmappable (STRETCH/BASELINE/AUTO) → no claim;
            // the converter emits either typed OR Generic, never both,
            // so falling through to channel 2 can't double-read.
        }
        // Channel 2 — Generic escape hatch for `safe|unsafe <pos>`.
        properties.forEach { p ->
            // Only Generic carriers whose propertyName is align-self.
            if (p.type != "Generic") return@forEach
            val obj = p.data as? JsonObject ?: return@forEach
            val name = (obj["propertyName"] as? JsonPrimitive)?.contentOrNull
            if (name != "align-self") return@forEach
            // The raw declaration value, verbatim from the stylesheet.
            val raw = (obj["rawValue"] as? JsonPrimitive)?.contentOrNull ?: return@forEach
            return parseRaw(raw)
        }
        // No align-self claim on either channel.
        return null
    }

    /**
     * Parse a raw `align-self` value string ("safe center", "unsafe
     * flex-end", …) into a Spec. css-align-3 §4.4 grammar subset:
     * [ safe | unsafe ]? <self-position>. Internal for direct unit pins.
     */
    internal fun parseRaw(raw: String): Spec? {
        // Whitespace-tokenized, case-insensitive per CSS keyword rules.
        val tokens = raw.trim().lowercase().split(Regex("\\s+"))
        // Overflow modifier present iff the token appears (§4.4).
        val safe = "safe" in tokens
        // The base keyword is whichever token is NOT an overflow word.
        val baseToken = tokens.firstOrNull { it != "safe" && it != "unsafe" }
        // Unknown/absent base → no claim (caller keeps legacy start).
        return baseOf(baseToken)?.let { Spec(it, safe) }
    }

    /**
     * Keyword → Base. Accepts both the converter's UPPER_SNAKE typed
     * spelling and raw CSS hyphenated spelling so both wires share one
     * table. self-start/self-end fold to start/end — no writing-mode
     * divergence in the runtime's LTR horizontal-tb normalization.
     */
    private fun baseOf(kw: String?): Base? = when (kw?.uppercase()) {
        // css-align-3 §4.2 start-family keywords.
        "FLEX-START", "FLEX_START", "START", "SELF-START", "SELF_START" -> Base.START
        // center + the anchor-positioning fold ItemPlacementExtractor pins.
        "CENTER", "ANCHOR-CENTER", "ANCHOR_CENTER" -> Base.CENTER
        // end-family keywords.
        "FLEX-END", "FLEX_END", "END", "SELF-END", "SELF_END" -> Base.END
        // auto/stretch/baseline/unknown → no static-position claim.
        else -> null
    }

    /**
     * True when the child declares an explicit inset on the given axis —
     * css-position-3 §3.5: a non-auto inset REPLACES the static position
     * on that axis (the child's own PositionApplier offsets from the
     * containing-block corner), so the static-position alignment must
     * stand down to avoid double-offsetting. Logical insets map per the
     * runtime's LTR horizontal-tb normalization (block → vertical,
     * inline → horizontal — same table as PositionExtractor).
     */
    fun hasCrossInset(properties: List<IRProperty>, vertical: Boolean): Boolean {
        // The IR type names that pin this axis (physical + logical).
        val axisTypes = if (vertical)
            setOf("Top", "Bottom", "InsetBlockStart", "InsetBlockEnd")
        else
            setOf("Left", "Right", "InsetInlineStart", "InsetInlineEnd")
        // Any declared inset on the axis disables the static position.
        return properties.any { it.type in axisTypes }
    }

    /**
     * The cross-axis static-position offset (px) of the child's box
     * from the alignment container's START edge contribution:
     *
     *   free = containerPx − childPx        (negative ⇒ overflow)
     *   safe && overflow → 0                (css-align-3 §4.4 fallback)
     *   START → 0 · CENTER → free/2 · END → free
     *
     * Negative results are the child spilling toward the start edge
     * (unsafe/plain center overflows BOTH edges equally — the WPT
     * flex-abspos-staticpos refs). Shared-semantics contract: the
     * SwiftUI twin computes byte-identical Doubles for the same inputs
     * (pinned by AbsposStaticAlignmentTest / AbsposStaticAlignmentTests).
     */
    fun crossOffset(childPx: Double, containerPx: Double, spec: Spec): Double {
        // Free space on the axis; < 0 means the child overflows.
        val free = containerPx - childPx
        // §4.4: `safe` swaps to START alignment on overflow.
        if (spec.safe && free < 0.0) return 0.0
        // Plain factor placement — start 0, center ½, end 1.
        return when (spec.base) {
            Base.START -> 0.0
            Base.CENTER -> free / 2.0
            Base.END -> free
        }
    }
}
