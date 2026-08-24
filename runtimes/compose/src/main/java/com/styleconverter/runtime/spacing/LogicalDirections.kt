package com.styleconverter.runtime.spacing

// Wave-47 lane Z2 — the css-writing-modes-4 §6 abstract-to-physical side
// mapping, extracted PURE so Margin/PaddingConfig resolve logical sides
// correctly under vertical writing modes and the JUnit suite can pin the
// whole table on the plain JVM (the iOS twin is
// StyleEngine/spacing/LogicalDirections.swift — same table, same names).
//
// Before this file both configs hard-coded the horizontal-tb mapping
// (blockStart→top, blockEnd→bottom, inline→left/right), which transposed
// every logical margin/padding under `writing-mode: vertical-rl/lr` — the
// css-break background-image-001/002 wall: `margin-block-end: 20px` on a
// vertical-rl box is a LEFT margin (block-end points left), not a bottom one.

import com.styleconverter.runtime.typography.text.DirectionValue
import com.styleconverter.runtime.typography.text.TextExtractor
import com.styleconverter.runtime.typography.text.WritingModeConfig
import com.styleconverter.runtime.typography.text.WritingModeValue
import kotlinx.serialization.json.JsonElement

/** The four physical sides a logical side can land on. */
enum class PhysicalSide { TOP, RIGHT, BOTTOM, LEFT }

/**
 * One writing-mode+direction's complete logical→physical assignment:
 * which physical side each of the four flow-relative sides maps to
 * (css-writing-modes-4 §6.4 "Abstract-to-Physical Mappings" table).
 */
data class LogicalSides(
    val blockStart: PhysicalSide,
    val blockEnd: PhysicalSide,
    val inlineStart: PhysicalSide,
    val inlineEnd: PhysicalSide,
) {
    companion object {
        /**
         * horizontal-tb + ltr — the mapping BOTH configs hard-coded before
         * this file. Kept as the named default so `resolve(isRtl=false)`
         * call sites stay byte-identical by construction.
         */
        val HORIZONTAL_LTR = LogicalSides(
            blockStart = PhysicalSide.TOP, blockEnd = PhysicalSide.BOTTOM,
            inlineStart = PhysicalSide.LEFT, inlineEnd = PhysicalSide.RIGHT,
        )

        /** horizontal-tb + rtl — inline sides swap (§6.4 row 2). */
        val HORIZONTAL_RTL = HORIZONTAL_LTR.copy(
            inlineStart = PhysicalSide.RIGHT, inlineEnd = PhysicalSide.LEFT)

        /**
         * The §6.4 table. `vertical-rl`: block flows right→left so
         * block-start is the RIGHT edge; with ltr the inline (line-progress)
         * axis runs top→bottom so inline-start is TOP. `vertical-lr`
         * mirrors the block axis only. `sideways-rl` lays boxes exactly
         * like vertical-rl (§4: it differs only in glyph orientation);
         * `sideways-lr` runs its inline axis bottom→top under ltr, so its
         * inline sides invert relative to vertical-lr.
         */
        fun of(writingMode: WritingModeValue, direction: DirectionValue): LogicalSides {
            // rtl flips the inline sides of whichever base mapping applies
            // (§6.4 pairs each writing-mode row with an ltr and an rtl column).
            val rtl = direction == DirectionValue.RTL
            return when (writingMode) {
                WritingModeValue.HORIZONTAL_TB ->
                    if (rtl) HORIZONTAL_RTL else HORIZONTAL_LTR
                // vertical-rl and sideways-rl share one box mapping (§4).
                WritingModeValue.VERTICAL_RL, WritingModeValue.SIDEWAYS_RL ->
                    LogicalSides(
                        blockStart = PhysicalSide.RIGHT, blockEnd = PhysicalSide.LEFT,
                        inlineStart = if (rtl) PhysicalSide.BOTTOM else PhysicalSide.TOP,
                        inlineEnd = if (rtl) PhysicalSide.TOP else PhysicalSide.BOTTOM,
                    )
                WritingModeValue.VERTICAL_LR ->
                    LogicalSides(
                        blockStart = PhysicalSide.LEFT, blockEnd = PhysicalSide.RIGHT,
                        inlineStart = if (rtl) PhysicalSide.BOTTOM else PhysicalSide.TOP,
                        inlineEnd = if (rtl) PhysicalSide.TOP else PhysicalSide.BOTTOM,
                    )
                // sideways-lr: blocks flow left→right, lines run bottom→top
                // under ltr (§4.1) — the one mode whose ltr inline-start is
                // the BOTTOM edge.
                WritingModeValue.SIDEWAYS_LR ->
                    LogicalSides(
                        blockStart = PhysicalSide.LEFT, blockEnd = PhysicalSide.RIGHT,
                        inlineStart = if (rtl) PhysicalSide.TOP else PhysicalSide.BOTTOM,
                        inlineEnd = if (rtl) PhysicalSide.BOTTOM else PhysicalSide.TOP,
                    )
            }
        }

        /**
         * The mapping for a component's OWN merged property list, or null
         * for every horizontal writing mode.
         *
         * Null (not HORIZONTAL_*) on the horizontal modes is deliberate
         * blast-radius control: the legacy `resolve(isRtl)` path — which
         * ignores the `Direction` property entirely — stays the authority
         * for horizontal content, so every existing fixture and WPT capture
         * (including rtl ones the legacy path renders "ltr-blind") is
         * byte-identical by construction. Only VERTICAL/SIDEWAYS modes,
         * where the legacy mapping is simply transposed, take the table.
         * `WritingMode` and `Direction` both inherit (css-writing-modes-4
         * §3.1, CSS2 §9.10) and ride the renderer's merged-list channel,
         * so an ancestor's declaration reaches this read.
         */
        fun verticalOrNull(properties: List<Pair<String, JsonElement?>>): LogicalSides? {
            // One shared decoder — the same read MultiColumnExtractor uses,
            // so the spacing mapping and the multicol vertical gate can
            // never disagree about what mode a component is in.
            val cfg: WritingModeConfig = TextExtractor.extractWritingModeConfig(properties)
            if (!cfg.isVertical) return null
            return of(cfg.writingMode, cfg.direction)
        }
    }
}
