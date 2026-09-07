package com.styleconverter.runtime.typography.wrapping

/**
 * AutoHyphenation.kt
 * typography/wrapping — wave 40 (lane T2).
 *
 * css-text-3 §5.3 `hyphens: auto`, the DICTIONARY half — the switch that
 * wave 37 named and wave 39's A3 finding measured as still off.
 *
 * ── WHAT WAS MISSING ────────────────────────────────────────────────
 * [com.styleconverter.runtime.typography.TextWrapApplier.getHyphens]
 * has mapped `HyphensMode.AUTO → Hyphens.Auto` since Phase 6, but
 * nothing ever read it: the renderer builds its own `TextStyle` and
 * never set `hyphens`, so every Compose run laid out at Compose's
 * default `Hyphens.None` (Minikin `hyphenationFrequency = NONE`). The
 * mapping was a function with no call site. Measured cost, wave39-final
 * android-ref: css-text/hyphens-auto-010 0.8530, hyphens-span-002
 * 0.8842, hyphens-punctuation-001 0.9385, hyphens-out-of-flow-002
 * 0.8782 — in every one the Chromium ref hyphenates ("regu-/lation",
 * "high-/way", "exam-/ple") and Android renders the whole word.
 *
 * ── WHY IT COULD NOT BE SWITCHED ON BEFORE, AND CAN NOW ─────────────
 * §5.3 makes the hyphenation resource LANGUAGE-dependent, and WPT
 * `css-text/hyphens/hyphens-auto-001` asserts the contrapositive in so
 * many words: "automatic hyphenation must not work without language
 * tagging" (its own `<div>` carries `hyphens: auto` and NO `lang`, and
 * both natives score 0.99 today precisely because they do not
 * hyphenate). Until wave 37 the IR had no language channel, so the
 * runtime could not tell a tagged run from an untagged one and took the
 * untagged reading for both — the honest choice, and the one recorded
 * in [SoftHyphenPolicy.wantsDictionaryHyphenation]'s banner.
 *
 * Wave 37 (lane W4) shipped THE LANG WIRE: `meta.lang`, the element's
 * COMPUTED content language, already resolved by the producer through
 * HTML §3.2.6.2's own-lang → nearest-ancestor → `<html>` ladder
 * (schema/spec/04-metadata-fields.md). That is exactly the missing
 * input, so the two readings can finally be separated.
 *
 * ── THE GATE, AND WHY IT IS INERT FOR EVERY COMMITTED BASELINE ──────
 * [engaged] is true only when BOTH halves hold: the resolved keyword is
 * `auto` AND the run carries a non-blank language tag. `meta.lang`
 * exists only on IR produced from real HTML (tools/titan's WPT
 * extractor sets `_lang`; CssParsing forwards it verbatim) — every
 * hand-authored fixture under `fixtures/properties/` and
 * `fixtures/components/` is a CSS envelope with no `_lang` key, so
 * `lang` is null there and this file cannot change a pixel of the 363
 * committed `tools/visual/baseline/` captures. That is a stronger,
 * spec-derived gate than a WPT-mode flag would be: it is the same
 * condition §5.3 itself imposes.
 *
 * Pure Kotlin (no Compose, no Android) so the JVM suite pins the
 * decision without a device. The Compose-typed half — `Hyphens.Auto` +
 * the `LocaleList` Minikin selects the dictionary with — lives in
 * TextWrapApplier, which already owns the `hyphens` mapping.
 *
 * Twin: `AutoHyphenation.swift` (SwiftUI), same two entry points and
 * the same gate; the platforms differ only in the hyphenator they hand
 * the decision to (Minikin here, CoreFoundation's
 * `CFStringGetHyphenationLocationBeforeIndex` there).
 */
object AutoHyphenation {

    /**
     * Does this run ask for — and qualify for — dictionary hyphenation?
     *
     * @param hyphensMode the resolved `hyphens` keyword, in either
     *   spelling the two extractors produce (the wire authors upper-case
     *   `"AUTO"`, iOS's extractor lowercases) — compared case-insensitively.
     * @param lang the run's COMPUTED content language (`meta.lang`).
     *   Null/blank means the document declares none, which §5.3 leaves
     *   with no resource to select: `auto` then degrades to `manual`'s
     *   explicit opportunities, exactly as before this file existed.
     */
    @JvmStatic
    fun engaged(hyphensMode: String?, lang: String?): Boolean =
        hyphensMode?.lowercase() == "auto" && !lang.isNullOrBlank()

    /**
     * The BCP-47 tag to hand the platform hyphenator, or null when
     * [engaged] is false.
     *
     * Returned VERBATIM apart from surrounding whitespace: BCP-47
     * matching is case-insensitive and subtag-truncating (RFC 4647
     * §2.1/§3.4), Minikin lowercases at lookup, and canonicalising here
     * would only lose the round-trip the wire deliberately preserves
     * (`meta.lang` keeps `eN-Us` as authored — see the schema note).
     * `en-us` therefore selects the same `hyph-en-us` pattern set as
     * `en-US`, and a bare `en` truncates to the same family.
     */
    @JvmStatic
    fun localeTag(hyphensMode: String?, lang: String?): String? =
        if (engaged(hyphensMode, lang)) lang!!.trim() else null

    /**
     * css-text-4 §6.3.4 `hyphenate-limit-chars: auto` — the MINIMUM word
     * length a UA will hyphenate at all. Chromium (`kMinimumPrefixLength`
     * 2 + `kMinimumSuffixLength` 2) and Minikin (`Hyphenator` MIN_PREFIX 2
     * / MIN_SUFFIX 3) both refuse anything shorter, so five is the floor
     * every engine in this repo's comparison agrees on.
     */
    private const val MIN_HYPHENATABLE_WORD = 5

    /**
     * Could a dictionary find a break inside [text]?
     *
     * This is the HYPHENATION half of the "is this line breakable?"
     * question that css-text-3 §5.5's rule B turns on
     * ([GreedyLineBreaker.hasUnbreakableOverflowingLine] owns the UAX #14
     * half through `DecorationOps.hasSoftWrapOpportunity`). Rule B exists
     * to stop the platform emergency-breaking a word that has NOWHERE to
     * break; once `hyphens: auto` is engaged a long word usually DOES have
     * somewhere, and letting rule B fire on it would pre-break the whole
     * run with `softWrap = false` — suppressing the hyphenation the same
     * wave just switched on.
     *
     * But the converse still matters, which is why this is not simply
     * `true`: a hyphenator only breaks WORDS. Measured on
     * css-text/hyphens-punctuation-001, whose `width: 5ch` boxes hold
     * `00000 example 00000` — the digit runs overflow with no dictionary
     * point anywhere in them, so rule B must still claim that run or
     * Minikin desperate-breaks `00000` into `0000`/`0` (android-ref
     * 0.9385 → 0.8971 with a blanket veto, restored by this predicate).
     *
     * Deliberately a LETTER-RUN test and nothing cleverer: the pattern
     * files are letter-only, the runtime cannot consult them from pure
     * Kotlin, and the only alternative — asking the platform to lay the
     * word out — is exactly the work rule B is trying to decide about.
     * A ≥5-letter run that the dictionary happens NOT to break is the one
     * false positive; it costs a desperate break on a word we would
     * otherwise have overflowed, which is the pre-wave-38 behaviour.
     */
    @JvmStatic
    fun hasDictionaryOpportunity(text: String): Boolean {
        var run = 0
        for (c in text) {
            if (c.isLetter()) {
                run++
                if (run >= MIN_HYPHENATABLE_WORD) return true
            } else {
                run = 0
            }
        }
        return false
    }
}
