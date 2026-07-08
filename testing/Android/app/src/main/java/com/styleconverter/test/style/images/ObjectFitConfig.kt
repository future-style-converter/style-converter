package com.styleconverter.test.style.images

import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale

/**
 * Configuration for CSS object-fit and object-position properties.
 *
 * ## Supported Properties
 * - object-fit: fill, contain, cover, none, scale-down
 * - object-position: alignment within container
 *
 * ## Compose Mapping
 * - fill -> ContentScale.FillBounds
 * - contain -> ContentScale.Fit
 * - cover -> ContentScale.Crop
 * - none -> ContentScale.None
 * - scale-down -> ContentScale.Inside
 */
data class ObjectFitConfig(
    val fit: ObjectFitValue = ObjectFitValue.FILL,
    val contentScale: ContentScale = ContentScale.FillBounds,
    val alignment: Alignment = Alignment.Center
) {
    val hasObjectFit: Boolean
        get() = fit != ObjectFitValue.FILL || alignment != Alignment.Center

    companion object {
        val Default = ObjectFitConfig()
    }
}

/**
 * CSS object-fit values.
 */
enum class ObjectFitValue {
    /** Stretches to fill container, may distort aspect ratio */
    FILL,
    /** Scales to fit within container, maintaining aspect ratio (letterboxing) */
    CONTAIN,
    /** Scales to cover container, maintaining aspect ratio (may crop) */
    COVER,
    /** No scaling, uses natural size */
    NONE,
    /** Uses whichever is smaller: none or contain */
    SCALE_DOWN
}

/**
 * Convert ObjectFitValue to Compose ContentScale.
 */
fun ObjectFitValue.toContentScale(): ContentScale = when (this) {
    ObjectFitValue.FILL -> ContentScale.FillBounds
    ObjectFitValue.CONTAIN -> ContentScale.Fit
    ObjectFitValue.COVER -> ContentScale.Crop
    ObjectFitValue.NONE -> ContentScale.None
    ObjectFitValue.SCALE_DOWN -> ContentScale.Inside
}
