package com.styleconverter.runtime.lists

// The marker-to-content gap a marker STRING earns — wave 55, lane L5
// (U2o-android, consumed by U2i-android). BYTE-PARALLEL TWIN of
// runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/lists/
// ListMarkerSuffixGap.swift — same rule, same pins.
//
// ## What the 4 dp stands in for
// [ListMarkerRow.gapDp] (4.dp) is the natives' stand-in for the suffix's
// TRAILING SPACE: the counter bake trims the trailing U+0020 from every
// predefined suffix (tools/titan/counter-style-table.mjs SUFFIX NOTE) and
// the author bake likewise (tools/titan/counter-style-author.mjs,
// `replace(/ +$/, '')`), so the baked `meta.markerText` is "1." and the
// space is re-added as this gap. css-counter-styles-3 §3.4: the initial
// `suffix` is "\2E\20" (a full stop THEN a space); every predefined suffix
// but one family ends in U+0020 (". ", korean ", ", ethiopic "/ ", the
// bullets " ").
//
// ## The family with no space
// `cjk-decimal` (§6.1), hiragana / katakana (-iroha) (§6.2),
// cjk-earthly-branch / cjk-heavenly-stem (§6.4), japanese-* (§7.1.1) and
// chinese (§7.1.3) all use `suffix: "\3001"` — the IDEOGRAPHIC COMMA and
// nothing after it. css-lists-3 §3.4 makes the marker string the counter
// representation "followed by the suffix" and §3.5 hangs an outside marker
// flush before the item's start edge, so the ref's `、` ends AT the content
// edge. Measured (wave54-final counter-suffix rows 5–6): ref `一` x33 and
// `、` ending flush at x64; both natives x29 — exactly the 4 dp stand-in.
// The same constant pads an INSIDE `、` marker from its item's text
// (css3-counter-styles-005: natives x271 vs ref x268 at 25 px).
//
// ## Why the key is the STRING, not the list-style-type
// The 36 corpus markers that end in U+3001 are all baked strings. The 71
// `<li>` under a U+3001-suffix TYPE without a baked marker are painted from
// the native tables (ListStyleApplier.getMarker — a "." suffix, a different
// defect), so a type-keyed rule would move those rows' wrong glyphs; the
// string reaches the 36 and nothing else. Census: tools/titan/results/
// wave55-counter-suffix-rows/census-l5.out.txt (U2 sections).
//
// ## Stated limit (not modelled)
// An AUTHOR @counter-style suffix that has no trailing space (e.g. ",")
// cannot be told from ", " on the wire — the author bake trims both — so it
// keeps the stand-in gap.

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object ListMarkerSuffixGap {

    /** U+3001 IDEOGRAPHIC COMMA — the space-less predefined suffix. */
    const val IDEOGRAPHIC_COMMA: Int = 0x3001

    /**
     * The inline gap between the marker box and the item for [marker] — the
     * RAW marker string (the baked `meta.markerText` or the table's answer):
     * its last code point is the suffix's last code point.
     *
     * @return 0.dp when the marker ends in U+3001 (its suffix carries no
     *   trailing space to stand in for), else [ListMarkerRow.gapDp].
     */
    fun gapDp(marker: String): Dp =
        // `codePointBefore(length)` reads the tail as a code point (a
        // surrogate pair is never split); an empty string has no suffix.
        if (marker.isNotEmpty() && marker.codePointBefore(marker.length) == IDEOGRAPHIC_COMMA) 0.dp
        // Every other suffix stood for a trimmed U+0020: keep the stand-in.
        else ListMarkerRow.gapDp
}
