package com.styleconverter.test.style.interactions

/**
 * Spatial navigation target value.
 */
sealed interface NavTargetValue {
    data object Auto : NavTargetValue
    data object None : NavTargetValue
    data class Selector(val selector: String) : NavTargetValue
    data class Id(val id: String, val scope: NavScope = NavScope.CURRENT) : NavTargetValue
}

/**
 * CSS nav-* scope values.
 * Defines the scope for navigation target lookup.
 */
enum class NavScope {
    /** Current document */
    CURRENT,
    /** Root of current document */
    ROOT,
    /** Specific container */
    SCOPE
}

/**
 * CSS reading-order property values.
 * Controls accessibility reading/traversal order.
 */
enum class ReadingOrderValue {
    /** Normal reading order based on visual layout */
    NORMAL,
    /** Follow source document order */
    SOURCE_ORDER,
    /** Use flex/grid order */
    FLEX_VISUAL,
    /** Use grid placement order */
    GRID_COLUMNS,
    GRID_ROWS
}

/**
 * Configuration for CSS spatial navigation properties.
 * Used for TV and game console navigation.
 */
data class SpatialNavigationConfig(
    val navUp: NavTargetValue = NavTargetValue.Auto,
    val navRight: NavTargetValue = NavTargetValue.Auto,
    val navDown: NavTargetValue = NavTargetValue.Auto,
    val navLeft: NavTargetValue = NavTargetValue.Auto,
    val readingOrder: ReadingOrderValue = ReadingOrderValue.NORMAL
) {
    val hasSpatialNavigation: Boolean
        get() = navUp != NavTargetValue.Auto ||
                navRight != NavTargetValue.Auto ||
                navDown != NavTargetValue.Auto ||
                navLeft != NavTargetValue.Auto ||
                readingOrder != ReadingOrderValue.NORMAL
}
