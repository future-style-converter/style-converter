package com.styleconverter.test.style.background

/**
 * Background box value options.
 */
enum class BackgroundBoxValue {
    BORDER_BOX,
    PADDING_BOX,
    CONTENT_BOX,
    TEXT
}

/**
 * Configuration for CSS background-clip and background-origin properties.
 */
data class BackgroundBoxConfig(
    val backgroundClip: BackgroundBoxValue = BackgroundBoxValue.BORDER_BOX,
    val backgroundOrigin: BackgroundBoxValue = BackgroundBoxValue.PADDING_BOX
) {
    val hasBackgroundBox: Boolean
        get() = backgroundClip != BackgroundBoxValue.BORDER_BOX ||
                backgroundOrigin != BackgroundBoxValue.PADDING_BOX
}
