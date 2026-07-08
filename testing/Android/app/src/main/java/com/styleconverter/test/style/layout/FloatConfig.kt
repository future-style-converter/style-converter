package com.styleconverter.test.style.layout

/**
 * Float value options.
 */
enum class FloatValue {
    NONE,
    LEFT,
    RIGHT,
    INLINE_START,
    INLINE_END
}

/**
 * Clear value options.
 */
enum class ClearValue {
    NONE,
    LEFT,
    RIGHT,
    BOTH,
    INLINE_START,
    INLINE_END
}

/**
 * Configuration for CSS float and clear properties.
 */
data class FloatConfig(
    val float: FloatValue = FloatValue.NONE,
    val clear: ClearValue = ClearValue.NONE
) {
    /**
     * Check if this config has any float/clear properties set.
     */
    val hasFloatProperties: Boolean
        get() = float != FloatValue.NONE || clear != ClearValue.NONE
}
