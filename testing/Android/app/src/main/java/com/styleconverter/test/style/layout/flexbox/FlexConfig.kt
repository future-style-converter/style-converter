package com.styleconverter.test.style.layout.flexbox

/**
 * Configuration for a flex container.
 * Maps CSS flexbox container properties to Compose-compatible values.
 */
data class FlexContainerConfig(
    val display: DisplayType = DisplayType.BLOCK,
    val direction: FlexDirection = FlexDirection.ROW,
    val wrap: FlexWrap = FlexWrap.NO_WRAP,
    val justifyContent: JustifyContent = JustifyContent.FLEX_START,
    val alignItems: AlignItems = AlignItems.STRETCH,
    val alignContent: AlignContent = AlignContent.STRETCH
) {
    /** Whether this is a flex container (display: flex or inline-flex) */
    val isFlex: Boolean get() = display == DisplayType.FLEX || display == DisplayType.INLINE_FLEX

    /** Whether the main axis is horizontal (row or row-reverse) */
    val isRow: Boolean get() = direction == FlexDirection.ROW || direction == FlexDirection.ROW_REVERSE

    /** Whether the direction is reversed */
    val isReverse: Boolean get() = direction == FlexDirection.ROW_REVERSE || direction == FlexDirection.COLUMN_REVERSE
}

/**
 * Configuration for a flex item (child of a flex container).
 * Maps CSS flexbox item properties to Compose-compatible values.
 */
data class FlexItemConfig(
    val flexGrow: Float = 0f,
    val flexShrink: Float = 1f,
    val flexBasis: FlexBasis = FlexBasis.Auto,
    val alignSelf: AlignSelf = AlignSelf.AUTO,
    val order: Int = 0
)

/**
 * CSS display property values.
 */
enum class DisplayType {
    BLOCK,
    FLEX,
    INLINE_FLEX,
    GRID,
    INLINE_GRID,
    INLINE,
    NONE,
    CONTENTS
}

/**
 * CSS flex-direction property values.
 */
enum class FlexDirection {
    ROW,
    ROW_REVERSE,
    COLUMN,
    COLUMN_REVERSE
}

/**
 * CSS flex-wrap property values.
 */
enum class FlexWrap {
    NO_WRAP,
    WRAP,
    WRAP_REVERSE
}

/**
 * CSS justify-content property values.
 */
enum class JustifyContent {
    FLEX_START,
    FLEX_END,
    CENTER,
    SPACE_BETWEEN,
    SPACE_AROUND,
    SPACE_EVENLY
}

/**
 * CSS align-items property values.
 */
enum class AlignItems {
    FLEX_START,
    FLEX_END,
    CENTER,
    BASELINE,
    STRETCH
}

/**
 * CSS align-content property values (for multi-line flex containers).
 */
enum class AlignContent {
    FLEX_START,
    FLEX_END,
    CENTER,
    SPACE_BETWEEN,
    SPACE_AROUND,
    STRETCH
}

/**
 * CSS align-self property values (for flex items).
 */
enum class AlignSelf {
    AUTO,
    FLEX_START,
    FLEX_END,
    CENTER,
    BASELINE,
    STRETCH
}

/**
 * CSS flex-basis property values.
 */
sealed interface FlexBasis {
    /** flex-basis: auto - use the item's intrinsic size */
    data object Auto : FlexBasis

    /** flex-basis: <length> - absolute size in dp */
    data class Length(val dp: Float) : FlexBasis

    /** flex-basis: <percentage> - fraction of container (0.0-1.0) */
    data class Percentage(val fraction: Float) : FlexBasis

    /** flex-basis: content - use the item's content size */
    data object Content : FlexBasis
}
