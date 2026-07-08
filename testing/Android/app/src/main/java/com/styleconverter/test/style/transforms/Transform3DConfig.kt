package com.styleconverter.test.style.transforms

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Transform style value.
 */
enum class TransformStyleValue {
    FLAT,
    PRESERVE_3D
}

/**
 * Backface visibility value.
 */
enum class BackfaceVisibilityValue {
    VISIBLE,
    HIDDEN
}

/**
 * Configuration for CSS 3D transform properties.
 */
data class Transform3DConfig(
    val perspective: Dp? = null,
    val perspectiveOriginX: Float = 50f,
    val perspectiveOriginY: Float = 50f,
    val transformStyle: TransformStyleValue = TransformStyleValue.FLAT,
    val backfaceVisibility: BackfaceVisibilityValue = BackfaceVisibilityValue.VISIBLE
) {
    val hasTransform3D: Boolean
        get() = perspective != null ||
                perspectiveOriginX != 50f ||
                perspectiveOriginY != 50f ||
                transformStyle != TransformStyleValue.FLAT ||
                backfaceVisibility != BackfaceVisibilityValue.VISIBLE
}
