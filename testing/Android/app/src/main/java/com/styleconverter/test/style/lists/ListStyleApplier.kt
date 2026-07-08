package com.styleconverter.test.style.lists

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

object ListStyleApplier {

    /**
     * Get the marker string for a list item at the given index.
     *
     * @param index 0-based index of the list item
     * @param config The list style configuration
     * @return The marker string to display before the list item content
     */
    fun getMarker(index: Int, config: ListStyleConfig): String {
        if (config.listStyleType == ListStyleType.NONE) return ""

        return when (config.listStyleType) {
            // Unordered
            ListStyleType.DISC -> "\u2022"
            ListStyleType.CIRCLE -> "\u25CB"
            ListStyleType.SQUARE -> "\u25A0"
            ListStyleType.NONE -> ""

            // Western ordered
            ListStyleType.DECIMAL -> "${index + 1}."
            ListStyleType.DECIMAL_LEADING_ZERO -> "${(index + 1).toString().padStart(2, '0')}."
            ListStyleType.LOWER_ALPHA, ListStyleType.LOWER_LATIN -> "${toLowerAlpha(index)}."
            ListStyleType.UPPER_ALPHA, ListStyleType.UPPER_LATIN -> "${toUpperAlpha(index)}."
            ListStyleType.LOWER_ROMAN -> "${toLowerRoman(index + 1)}."
            ListStyleType.UPPER_ROMAN -> "${toUpperRoman(index + 1)}."

            // International ordered
            ListStyleType.LOWER_GREEK -> "${toLowerGreek(index)}."
            ListStyleType.UPPER_GREEK -> "${toUpperGreek(index)}."
            ListStyleType.ARMENIAN -> "${toArmenian(index + 1)}."
            ListStyleType.GEORGIAN -> "${toGeorgian(index + 1)}."
            ListStyleType.HEBREW -> "${toHebrew(index + 1)}."
            ListStyleType.CJK_DECIMAL -> "${toCjkDecimal(index + 1)}."
            ListStyleType.HIRAGANA -> "${toHiragana(index)}."
            ListStyleType.KATAKANA -> "${toKatakana(index)}."
            ListStyleType.HIRAGANA_IROHA -> "${toHiraganaIroha(index)}."
            ListStyleType.KATAKANA_IROHA -> "${toKatakanaIroha(index)}."

            ListStyleType.CUSTOM -> "${index + 1}."
        }
    }

    /**
     * Build an annotated string with the marker prepended.
     */
    fun buildMarkedText(
        index: Int,
        config: ListStyleConfig,
        content: String,
        markerColor: Color = Color.Unspecified
    ): AnnotatedString {
        val marker = getMarker(index, config)
        val separator = if (config.listStylePosition == ListStylePosition.INSIDE) " " else "  "

        return buildAnnotatedString {
            if (marker.isNotEmpty()) {
                withStyle(SpanStyle(color = markerColor)) {
                    append(marker)
                }
                append(separator)
            }
            append(content)
        }
    }

    // Conversion helpers

    private fun toLowerAlpha(index: Int): String {
        if (index < 0) return ""
        return if (index < 26) {
            ('a' + index).toString()
        } else {
            toLowerAlpha(index / 26 - 1) + ('a' + index % 26)
        }
    }

    private fun toUpperAlpha(index: Int): String = toLowerAlpha(index).uppercase()

    private fun toLowerRoman(num: Int): String {
        if (num <= 0) return ""
        val values = listOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
        val symbols = listOf("m", "cm", "d", "cd", "c", "xc", "l", "xl", "x", "ix", "v", "iv", "i")
        var result = ""
        var remaining = num
        for (i in values.indices) {
            while (remaining >= values[i]) {
                result += symbols[i]
                remaining -= values[i]
            }
        }
        return result
    }

    private fun toUpperRoman(num: Int): String = toLowerRoman(num).uppercase()

    private fun toLowerGreek(index: Int): String {
        val greekLower = "\u03B1\u03B2\u03B3\u03B4\u03B5\u03B6\u03B7\u03B8\u03B9\u03BA\u03BB\u03BC\u03BD\u03BE\u03BF\u03C0\u03C1\u03C3\u03C4\u03C5\u03C6\u03C7\u03C8\u03C9"
        return if (index < greekLower.length) greekLower[index].toString() else "${index + 1}"
    }

    private fun toUpperGreek(index: Int): String {
        val greekUpper = "\u0391\u0392\u0393\u0394\u0395\u0396\u0397\u0398\u0399\u039A\u039B\u039C\u039D\u039E\u039F\u03A0\u03A1\u03A3\u03A4\u03A5\u03A6\u03A7\u03A8\u03A9"
        return if (index < greekUpper.length) greekUpper[index].toString() else "${index + 1}"
    }

    private fun toArmenian(num: Int): String {
        if (num <= 0 || num > 9999) return num.toString()
        val armenianDigits = arrayOf(
            arrayOf("", "\u0531", "\u0532", "\u0533", "\u0534", "\u0535", "\u0536", "\u0537", "\u0538", "\u0539"),      // 1-9
            arrayOf("", "\u053A", "\u053B", "\u053C", "\u053D", "\u053E", "\u053F", "\u0540", "\u0541", "\u0542"),      // 10-90
            arrayOf("", "\u0543", "\u0544", "\u0545", "\u0546", "\u0547", "\u0548", "\u0549", "\u054A", "\u054B"),      // 100-900
            arrayOf("", "\u054C", "\u054D", "\u054E", "\u054F", "\u0550", "\u0551", "\u0552", "\u0553", "\u0554")       // 1000-9000
        )
        var result = ""
        var n = num
        for (i in 3 downTo 0) {
            val divisor = Math.pow(10.0, i.toDouble()).toInt()
            val digit = n / divisor
            if (digit > 0) {
                result += armenianDigits[i][digit]
            }
            n %= divisor
        }
        return result
    }

    private fun toGeorgian(num: Int): String {
        if (num <= 0 || num > 19999) return num.toString()
        val georgianDigits = arrayOf(
            arrayOf("", "\u10D0", "\u10D1", "\u10D2", "\u10D3", "\u10D4", "\u10D5", "\u10D6", "\u10F0", "\u10D7"),      // 1-9
            arrayOf("", "\u10D8", "\u10D9", "\u10DA", "\u10DB", "\u10DC", "\u10F2", "\u10DD", "\u10DE", "\u10DF"),      // 10-90
            arrayOf("", "\u10E0", "\u10E1", "\u10E2", "\u10F3", "\u10E4", "\u10E5", "\u10E6", "\u10E7", "\u10E8"),      // 100-900
            arrayOf("", "\u10E9", "\u10EA", "\u10EB", "\u10EC", "\u10ED", "\u10EE", "\u10F4", "\u10EF", "\u10F0")       // 1000-9000
        )
        var result = ""
        var n = num
        if (n >= 10000) {
            result += "\u10F5"  // 10000
            n -= 10000
        }
        for (i in 3 downTo 0) {
            val divisor = Math.pow(10.0, i.toDouble()).toInt()
            val digit = n / divisor
            if (digit > 0 && i < georgianDigits.size && digit < georgianDigits[i].size) {
                result += georgianDigits[i][digit]
            }
            n %= divisor
        }
        return result
    }

    private fun toHebrew(num: Int): String {
        if (num <= 0 || num > 999) return num.toString()
        val hebrewUnits = arrayOf("", "\u05D0", "\u05D1", "\u05D2", "\u05D3", "\u05D4", "\u05D5", "\u05D6", "\u05D7", "\u05D8")
        val hebrewTens = arrayOf("", "\u05D9", "\u05DB", "\u05DC", "\u05DE", "\u05E0", "\u05E1", "\u05E2", "\u05E4", "\u05E6")
        val hebrewHundreds = arrayOf("", "\u05E7", "\u05E8", "\u05E9", "\u05EA", "\u05EA\u05E7", "\u05EA\u05E8", "\u05EA\u05E9", "\u05EA\u05EA", "\u05EA\u05EA\u05E7")

        val hundreds = num / 100
        val tens = (num % 100) / 10
        val units = num % 10

        return hebrewHundreds.getOrElse(hundreds) { "" } +
               hebrewTens.getOrElse(tens) { "" } +
               hebrewUnits.getOrElse(units) { "" }
    }

    private fun toCjkDecimal(num: Int): String {
        val cjkDigits = "\u3007\u4E00\u4E8C\u4E09\u56DB\u4E94\u516D\u4E03\u516B\u4E5D"
        return num.toString().map { cjkDigits[it - '0'] }.joinToString("")
    }

    private fun toHiragana(index: Int): String {
        val hiragana = "\u3042\u3044\u3046\u3048\u304A\u304B\u304D\u304F\u3051\u3053\u3055\u3057\u3059\u305B\u305D\u305F\u3061\u3064\u3066\u3068\u306A\u306B\u306C\u306D\u306E\u306F\u3072\u3075\u3078\u307B\u307E\u307F\u3080\u3081\u3082\u3084\u3086\u3088\u3089\u308A\u308B\u308C\u308D\u308F\u3092\u3093"
        return if (index < hiragana.length) hiragana[index].toString() else "${index + 1}"
    }

    private fun toKatakana(index: Int): String {
        val katakana = "\u30A2\u30A4\u30A6\u30A8\u30AA\u30AB\u30AD\u30AF\u30B1\u30B3\u30B5\u30B7\u30B9\u30BB\u30BD\u30BF\u30C1\u30C4\u30C6\u30C8\u30CA\u30CB\u30CC\u30CD\u30CE\u30CF\u30D2\u30D5\u30D8\u30DB\u30DE\u30DF\u30E0\u30E1\u30E2\u30E4\u30E6\u30E8\u30E9\u30EA\u30EB\u30EC\u30ED\u30EF\u30F2\u30F3"
        return if (index < katakana.length) katakana[index].toString() else "${index + 1}"
    }

    private fun toHiraganaIroha(index: Int): String {
        val iroha = "\u3044\u308D\u306F\u306B\u307B\u3078\u3068\u3061\u308A\u306C\u308B\u3092\u308F\u304B\u3088\u305F\u308C\u305D\u3064\u306D\u306A\u3089\u3080\u3046\u3090\u306E\u304A\u304F\u3084\u307E\u3051\u3075\u3053\u3048\u3066\u3042\u3055\u304D\u3086\u3081\u307F\u3057\u3091\u3072\u3082\u305B\u3059"
        return if (index < iroha.length) iroha[index].toString() else "${index + 1}"
    }

    private fun toKatakanaIroha(index: Int): String {
        val iroha = "\u30A4\u30ED\u30CF\u30CB\u30DB\u30D8\u30C8\u30C1\u30EA\u30CC\u30EB\u30F2\u30EF\u30AB\u30E8\u30BF\u30EC\u30BD\u30C4\u30CD\u30CA\u30E9\u30E0\u30A6\u30F0\u30CE\u30AA\u30AF\u30E4\u30DE\u30B1\u30D5\u30B3\u30A8\u30C6\u30A2\u30B5\u30AD\u30E6\u30E1\u30DF\u30B7\u30F1\u30D2\u30E2\u30BB\u30B9"
        return if (index < iroha.length) iroha[index].toString() else "${index + 1}"
    }
}
