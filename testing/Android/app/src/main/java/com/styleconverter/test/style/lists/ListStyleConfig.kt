package com.styleconverter.test.style.lists

import androidx.compose.ui.graphics.Color

data class ListStyleConfig(
    val listStyleType: ListStyleType = ListStyleType.DISC,
    val listStylePosition: ListStylePosition = ListStylePosition.OUTSIDE,
    val listStyleImage: String? = null,
    val markerColor: Color? = null
) {
    val hasListStyle: Boolean
        get() = listStyleType != ListStyleType.NONE || listStyleImage != null
}

enum class ListStyleType {
    // Unordered list markers
    DISC,           // (default)
    CIRCLE,
    SQUARE,
    NONE,           // No marker

    // Ordered list markers - Western
    DECIMAL,        // 1, 2, 3
    DECIMAL_LEADING_ZERO, // 01, 02, 03
    LOWER_ALPHA,    // a, b, c
    UPPER_ALPHA,    // A, B, C
    LOWER_ROMAN,    // i, ii, iii
    UPPER_ROMAN,    // I, II, III
    LOWER_LATIN,    // a, b, c (alias)
    UPPER_LATIN,    // A, B, C (alias)

    // Ordered list markers - International
    LOWER_GREEK,
    UPPER_GREEK,
    ARMENIAN,
    GEORGIAN,
    HEBREW,
    CJK_DECIMAL,
    HIRAGANA,
    KATAKANA,
    HIRAGANA_IROHA,
    KATAKANA_IROHA,

    // Custom
    CUSTOM
}

enum class ListStylePosition {
    INSIDE,   // Marker inside the content box
    OUTSIDE   // Marker outside the content box (default)
}
