package com.styleconverter.test.style.rendering

/**
 * Configuration for CSS zoom property.
 */
data class ZoomConfig(
    val zoom: Float = 1.0f,
    val isNormal: Boolean = true
) {
    val hasZoom: Boolean
        get() = !isNormal || zoom != 1.0f
}
