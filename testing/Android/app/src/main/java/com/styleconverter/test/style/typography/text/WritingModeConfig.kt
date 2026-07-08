package com.styleconverter.test.style.typography.text

/**
 * Configuration for CSS writing mode and text orientation properties.
 *
 * ## Supported Properties
 * - writing-mode: horizontal-tb, vertical-rl, vertical-lr, sideways-rl, sideways-lr
 * - text-orientation: mixed, upright, sideways
 * - direction: ltr, rtl
 * - unicode-bidi: normal, embed, isolate, etc.
 *
 * ## Compose Mapping
 * - writing-mode: Use CompositionLocalProvider with LocalLayoutDirection
 * - vertical text: Limited support via custom layouts
 */
data class WritingModeConfig(
    val writingMode: WritingModeValue = WritingModeValue.HORIZONTAL_TB,
    val textOrientation: TextOrientationValue = TextOrientationValue.MIXED,
    val direction: DirectionValue = DirectionValue.LTR,
    val unicodeBidi: UnicodeBidiValue = UnicodeBidiValue.NORMAL
) {
    val hasWritingMode: Boolean
        get() = writingMode != WritingModeValue.HORIZONTAL_TB ||
                direction != DirectionValue.LTR ||
                unicodeBidi != UnicodeBidiValue.NORMAL

    val isVertical: Boolean
        get() = writingMode in listOf(
            WritingModeValue.VERTICAL_RL,
            WritingModeValue.VERTICAL_LR,
            WritingModeValue.SIDEWAYS_RL,
            WritingModeValue.SIDEWAYS_LR
        )

    val isRtl: Boolean
        get() = direction == DirectionValue.RTL

    companion object {
        val Default = WritingModeConfig()
        val Rtl = WritingModeConfig(direction = DirectionValue.RTL)
        val VerticalRl = WritingModeConfig(writingMode = WritingModeValue.VERTICAL_RL)
        val VerticalLr = WritingModeConfig(writingMode = WritingModeValue.VERTICAL_LR)
    }
}

/**
 * CSS writing-mode property values.
 */
enum class WritingModeValue {
    /** Horizontal text, top to bottom blocks (default) */
    HORIZONTAL_TB,
    /** Vertical text, right to left blocks (e.g., traditional Chinese) */
    VERTICAL_RL,
    /** Vertical text, left to right blocks */
    VERTICAL_LR,
    /** Sideways vertical text, right to left */
    SIDEWAYS_RL,
    /** Sideways vertical text, left to right */
    SIDEWAYS_LR
}

/**
 * CSS text-orientation property values.
 */
enum class TextOrientationValue {
    /** Rotate horizontal scripts 90° in vertical text */
    MIXED,
    /** Set all characters upright in vertical text */
    UPRIGHT,
    /** Lay out characters sideways (rotated 90°) */
    SIDEWAYS
}

/**
 * CSS direction property values.
 */
enum class DirectionValue {
    /** Left-to-right (default) */
    LTR,
    /** Right-to-left */
    RTL
}

/**
 * CSS unicode-bidi property values.
 */
enum class UnicodeBidiValue {
    /** No additional embedding */
    NORMAL,
    /** Create embedding level */
    EMBED,
    /** Isolate text from surrounding */
    ISOLATE,
    /** Override bidirectional algorithm */
    BIDI_OVERRIDE,
    /** Isolate and override */
    ISOLATE_OVERRIDE,
    /** Plain text treatment */
    PLAINTEXT
}
