package com.styleconverter.runtime.typography.wrapping

/**
 * SoftHyphenPolicy.kt
 * typography/wrapping — wave 37 (lane W7, rule A).
 *
 * css-text-3 §6.1 `hyphens`, the SOFT HYPHEN half.
 *
 * U+00AD SOFT HYPHEN is a *conditional* character: it is invisible
 * unless the line actually breaks at it, in which case the UA paints the
 * hyphenate-character there. §6.1 defines the three keywords over
 * exactly that conditionality:
 *
 *   manual (initial) — "words are only broken at line breaks where there
 *                       are characters inside the word that explicitly
 *                       suggest break opportunities" → U+00AD IS one;
 *   none             — "words are not broken at line breaks, EVEN IF
 *                       characters inside the word suggest break points"
 *                       → U+00AD is NOT an opportunity, so it never
 *                       breaks there and never paints;
 *   auto             — manual's opportunities plus dictionary ones.
 *
 * The only vehicle either native gives us for `none` is the string
 * itself: neither SwiftUI Text nor Compose Text exposes a "ignore soft
 * hyphens" toggle on a layout object the capture pipeline can rasterise.
 * So under `none` the conditional characters are deleted before layout.
 * That is loss-free by definition — §6.1 says they must not be honoured,
 * and an unbroken soft hyphen has no advance of its own.
 *
 * PLATFORM SPLIT, measured (wave 37, lane W7). iOS NEEDED this: TextKit
 * honours U+00AD unconditionally, and css-text/hyphens-none-011 went
 * 0.8181 → 0.9017 against the frozen ref once they were stripped.
 * Compose does NOT, today: its default `TextStyle.hyphens = Hyphens.None`
 * sets Minikin's hyphenationFrequency to NONE, and Minikin then ignores
 * U+00AD outright — proven by feeding a per-test IR with the soft hyphens
 * already deleted, whose emulator capture came back byte-identical
 * (sha1 943f2b18…). The Compose call site is kept anyway as a guard with
 * a named trigger: turning `Hyphens.Auto` on (the closing move for
 * `requires-hyphenation-dictionary`) makes Minikin honour U+00AD again.
 *
 * Deliberately NOT applied to `manual`/`auto`: there the soft hyphen is a
 * real opportunity and the platform's own handling is correct.
 *
 * Twin: SoftHyphenPolicy.swift (SwiftUI), same two entry points, same
 * identity contract. Pure Kotlin so the JVM suite pins it without a
 * renderer or a device.
 */
object SoftHyphenPolicy {

    /**
     * U+00AD SOFT HYPHEN — the one conditional character §6.1 governs.
     * (U+200B ZERO WIDTH SPACE is a plain break opportunity, NOT a
     * hyphenation one: `hyphens` does not suppress it, so it is out of
     * this policy's scope.)
     */
    const val SOFT_HYPHEN: Char = '\u00AD'

    /**
     * Does this run's resolved `hyphens` keyword forbid soft-hyphen
     * breaks? Case-insensitive over the IR keyword (the wire is authored
     * upper-case `"NONE"`, iOS's extractor lowercases — both spellings
     * must answer the same). null / unknown keywords answer false: the
     * initial value is `manual`, which honours them.
     */
    @JvmStatic
    fun suppresses(mode: String?): Boolean =
        mode?.lowercase() == "none"

    /**
     * The display string for [text] under [mode]. Returns the SAME
     * instance whenever the mode honours soft hyphens or the run
     * contains none — every legacy document therefore renders
     * byte-identically (the identity contract the renderer's
     * `remember{}` keys rely on).
     */
    /**
     * Does this run ask for DICTIONARY hyphenation (§6.1 `auto`: "words
     * may be broken at appropriate hyphenation points … as determined by
     * … a hyphenation resource appropriate to the language of the text")?
     *
     * Android's Minikin ships hyphenation dictionaries and Compose can
     * ask for them (`TextStyle.hyphens = Hyphens.Auto`) — but only for a
     * LANGUAGE, and the IR wire carries no language channel, so the
     * dictionary cannot be selected. `auto` therefore degrades to
     * `manual`'s explicit opportunities on both natives. That is exactly
     * right for untagged content — WPT `hyphens-auto-001` asserts
     * "automatic hyphenation must not work without language tagging" —
     * and a genuine wall for language-tagged content, named in
     * tools/titan/wpt-not-applicable.mjs as
     * `requires-hyphenation-dictionary`.
     *
     * Used only to raise the once-per-type breadcrumb (the repo's
     * no-silent-fallthrough rule); it never changes layout.
     */
    @JvmStatic
    fun wantsDictionaryHyphenation(mode: String?): Boolean =
        mode?.lowercase() == "auto"

    @JvmStatic
    fun displayString(text: String, mode: String?): String =
        if (suppresses(mode) && text.indexOf(SOFT_HYPHEN) >= 0)
            text.filter { it != SOFT_HYPHEN }
        else text
}
