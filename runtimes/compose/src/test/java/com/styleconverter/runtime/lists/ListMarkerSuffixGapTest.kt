package com.styleconverter.runtime.lists

// Wave 55, lane L5 (U2o-android / U2i-android) — pins ListMarkerSuffixGap:
// 0.dp for a marker string whose last code point is U+3001 (the space-less
// `cjk-decimal` family suffix, css-counter-styles-3 §6.1), the 4.dp
// stand-in for the trimmed trailing space otherwise. TWIN of the iOS suite
// runtimes/swiftui/Tests/StyleConverterRuntimeTests/
// ListMarkerSuffixGapTests.swift — same rows.
//
// Mutation EXECUTED by the lane (bracketed in tools/titan/results/
// wave55-counter-suffix-rows/_note.md): dropping the U+3001 test (always
// the stand-in) turns the two zero-gap pins red.

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ListMarkerSuffixGapTest {

    @Test
    fun `an ideographic comma suffix takes no gap`() {
        // The verbatim baked markers: counter-suffix rows 5-6 and
        // css3-counter-styles-004's two-digit "一〇、".
        assertEquals(0.dp, ListMarkerSuffixGap.gapDp("一、"))
        assertEquals(0.dp, ListMarkerSuffixGap.gapDp("一〇、"))
    }

    @Test
    fun `space suffixes keep the stand-in gap`() {
        // decimal ". " → "1."
        assertEquals(4.dp, ListMarkerSuffixGap.gapDp("1."))
        // korean-hangul-formal ", " → "일,"
        assertEquals(4.dp, ListMarkerSuffixGap.gapDp("일,"))
        // hebrew ". " → "א."
        assertEquals(4.dp, ListMarkerSuffixGap.gapDp("א."))
        // No marker string: the stand-in (callers never paint one).
        assertEquals(4.dp, ListMarkerSuffixGap.gapDp(""))
    }

    @Test
    fun `the stand-in is the shared row gap`() {
        // One constant, one home: ListMarkerRow.gapDp.
        assertEquals(ListMarkerRow.gapDp, ListMarkerSuffixGap.gapDp("1."))
    }

    @Test
    fun `only the tail counts`() {
        // A U+3001 that is not the LAST code point is not the suffix.
        assertEquals(4.dp, ListMarkerSuffixGap.gapDp("、1."))
    }
}
