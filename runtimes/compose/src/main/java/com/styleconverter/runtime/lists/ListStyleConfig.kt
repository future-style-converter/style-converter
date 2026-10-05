package com.styleconverter.runtime.lists

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
    // Wave 52 (lane L6, T1): css-counter-styles-3 §7.1 longhand East Asian
    // style, the iOS twin's `.koreanHangulFormal` (wave 50). The expansion
    // lives in KoreanHangulFormal; the hanja siblings stay unmodelled
    // (zero corpus carriers — see that file's scope boundary).
    KOREAN_HANGUL_FORMAL,

    // Custom
    CUSTOM
}

enum class ListStylePosition {
    INSIDE,   // Marker inside the content box
    OUTSIDE   // Marker outside the content box (default)
}
