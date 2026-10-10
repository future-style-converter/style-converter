package com.styleconverter.runtime.typography.wrapping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

// PreBreakPipeline pin table — applier campaign wave 38, lane N8.
//
// The pipeline is the DECISION half of Compose's css-text-3 §5.5 rule B:
// which runs get pre-broken (and therefore rendered with softWrap off),
// and — far more importantly for the frozen corpus — which do not. Every
// decline is pinned by IDENTITY (`assertSame`), because "returns the input
// instance" is exactly the property that makes the 327-pair dark stage and
// the untouched WPT rows byte-stable by construction.
//
// Metrics as in GreedyLineBreakerTest: Chromium's monospace advance at the
// css-text/hyphens family's 32px font-size.
class PreBreakPipelineTest {

    private val CH = 19.2f
    private val mono: (String) -> Float = { s -> s.length * CH }

    /** The fixture string of css-text/hyphens-manual-010 + -none-011. */
    private val dna = "Deoxyribonucleic acid"

    /** All gates open, 10ch box — the configuration the two 0.7500 tests
     *  present. Every decline pin below flips exactly one argument. */
    private fun fire(
        text: String = dna,
        wrapWidthPx: Float = 10 * CH,
        enabled: Boolean = true,
        softWrapAllowed: Boolean = true,
        allowMidWordBreak: Boolean = false,
        preservesSpaces: Boolean = false
    ) = PreBreakPipeline.preBreak(
        text = text,
        wrapWidthPx = wrapWidthPx,
        enabled = enabled,
        softWrapAllowed = softWrapAllowed,
        allowMidWordBreak = allowMidWordBreak,
        preservesSpaces = preservesSpaces,
        measure = mono
    )

    // ── the firing case ──────────────────────────────────────────────

    /** The measured defect: Chromium keeps all 16 glyphs of
     *  "Deoxyribonucleic" on one overflowing line, Minikin
     *  character-breaks it. The pipeline hands back the CSS lines with a
     *  HARD newline between them — the caller pairs that with
     *  `softWrap = false`, which is the only switch that stops Minikin's
     *  emergency break. */
    @Test
    fun ruleBFiresOnTheOverlongUnbreakableWord() {
        val r = fire()
        assertTrue(r.fired)
        assertEquals("Deoxyribonucleic\nacid", r.text)
    }

    /** The wrap has to be REAL: at a box wide enough for the whole run
     *  nothing overflows, so the platform keeps ownership. */
    @Test
    fun aRunThatFitsIsUntouched() {
        val r = fire(wrapWidthPx = 40 * CH)
        assertFalse(r.fired)
        assertSame(dna, r.text)
    }

    /** An ordinary sentence that merely WRAPS is untouched — the platform
     *  breaks it greedily at spaces exactly like Chromium, and rewriting
     *  those breaks would put every wrapping row in the corpus at risk. */
    @Test
    fun anOrdinaryWrappingRunIsUntouched() {
        val text = "the characters inside of each black bordered rectangle"
        val r = fire(text = text, wrapWidthPx = 10 * CH)
        assertFalse(r.fired)
        assertSame(text, r.text)
    }

    // ── the gates, one per decline ───────────────────────────────────

    /** THE baseline guard: outside composed WPT capture the pipeline is
     *  inert, so no committed 327-pair capture can move. */
    @Test
    fun disabledOutsideComposedWptCapture() {
        val r = fire(enabled = false)
        assertFalse(r.fired)
        assertSame(dna, r.text)
    }

    /** `white-space: nowrap|pre` / `text-wrap: nowrap` already lay the run
     *  out on one line — there is no wrap to repair. */
    @Test
    fun declinesWhenWrappingIsOff() {
        assertSame(dna, fire(softWrapAllowed = false).text)
    }

    /** `overflow-wrap: break-word|anywhere` and `word-break: break-all`
     *  ASK for the mid-word break (css-text-3 §5.5), so Minikin is right
     *  and must keep the run. */
    @Test
    fun declinesWhenCssOptedIntoTheEmergencyBreak() {
        assertSame(dna, fire(allowMidWordBreak = true).text)
    }

    /** `pre-wrap` / `break-spaces` preserve space runs (css-text-3
     *  §4.1.2); the space-split breaker would collapse them, which is a
     *  glyph-content rewrite rather than a wrap fix. */
    @Test
    fun declinesForPreservedWhitespaceModes() {
        assertSame(dna, fire(preservesSpaces = true).text)
    }

    /** No wrap width known — an unbounded proposal, or the first
     *  composition before the latch has observed one. Declining keeps
     *  frame 1 byte-identical to the frozen behaviour. */
    @Test
    fun declinesUntilTheWrapWidthIsKnown() {
        assertSame(dna, fire(wrapWidthPx = -1f).text)
        assertSame(dna, fire(wrapWidthPx = 0f).text)
    }

    /** A run with NO space (and, since wave 53, no U+00AD either) is the
     *  WHOLE-RUN case wave 21's B-RC7 gate already owns (softWrap off when
     *  `hasSoftWrapOpportunity` is false). One owner per case: the pipeline
     *  stands down so those captures stay byte-identical. Also the
     *  over-widening pin for wave 53's F1: deleting the guard outright makes
     *  the fold fire rule B on this overflowing word (a new String
     *  instance), so `assertSame` fails. */
    @Test
    fun declinesForASpacelessRunTheWholeRunGateOwns() {
        val text = "Deoxyribonucleic"
        assertSame(text, fire(text = text).text)
    }

    /** hyphens-none-012: the overlong "word" carries a hyphen-minus, so it
     *  is breakable after all and the reference DOES wrap it. Rule B must
     *  not fire — see GreedyLineBreakerTest for the measured regression
     *  the per-line veto prevents. */
    @Test
    fun declinesWhenTheOverlongWordIsHyphenated() {
        val text = "regu-lation imple-menta-tion now"
        val r = fire(text = text, wrapWidthPx = 6 * CH)
        assertFalse(r.fired)
        assertSame(text, r.text)
    }

    /** Rule A composes with rule B: `hyphens: none` deletes U+00AD BEFORE
     *  this pipeline sees the string (SoftHyphenPolicy runs first at the
     *  call site), so hyphens-none-011's authored
     *  `Deoxy&shy;ribo&shy;nucleic` arrives as the bare word and fires
     *  exactly like hyphens-manual-010's. Left in place — i.e. under
     *  `manual` — the soft hyphens are opportunities the wave-41 fold
     *  SPENDS itself (`Deoxyribo‐`/`nucleic`/`acid`, every line fitting).
     *  Until wave 52 that meant "no rule-B claim, decline" — and Minikin,
     *  which ignores U+00AD under Hyphens.None, then desperate-broke the
     *  word with NO hyphen glyph (hyphens-manual-011 android P 0.9811
     *  paints `Deoxyribo` / `nucleic`, hyphen-less; the ref paints the
     *  hyphen). Wave 52 (lane L9, F5) makes the TAKEN soft hyphen a second
     *  trigger (css-text-3 §5.3), so the `manual` half now FIRES with the
     *  measured lines — hyphens-manual-011's capture is expected to move
     *  ref-ward (its hyphen appears); the flip is pre-registered in
     *  tools/titan/results/wave52-plan/watchlist.txt. */
    @Test
    fun composesWithTheSoftHyphenPolicy() {
        val authored = "Deoxy­ribo­nucleic acid"
        // hyphens: none → stripped first → fires with the CSS lines.
        val stripped = SoftHyphenPolicy.displayString(authored, "none")
        val none = fire(text = stripped)
        assertTrue(none.fired)
        assertEquals("Deoxyribonucleic\nacid", none.text)
        // hyphens: manual → the conditional characters survive; the fold
        // takes the greedy one (after `ribo`, 10ch with the hyphen) and
        // every line fits — no rule-B claim, but a soft hyphen was TAKEN,
        // so the wave-52 trigger fires with the hyphen materialised.
        val manual = fire(text = SoftHyphenPolicy.displayString(authored, "manual"))
        assertTrue(manual.fired)
        assertEquals("Deoxyribo‐\nnucleic\nacid", manual.text)
    }

    // ── Wave 52 (lane L9, F5) — the TAKEN-SOFT-HYPHEN trigger ────────
    //
    // MUTATION PROOF (executed 2026-10-05, lane L9, mutate.py M2/M3 —
    // tools/titan/results/wave52-inline-run-wall/mutations-compose.result
    // .json): M2 `val tookSoftHyphen = false` → the -012 pin below and the
    // `manual` half of composesWithTheSoftHyphenPolicy fail (identity
    // returned where `fired` is asserted); M3 the `!dictionaryHyphenation`
    // gate removed → the dictionary pin fails (fired where identity is
    // asserted). Restored byte-exact (sha256-verified) after each run.

    /** css-text/hyphens/hyphens-manual-inline-012's paragraph, VERBATIM
     *  off wave51-fix/sections/css-text/per-test-ir: the host's runs
     *  `DNA ` + span(`means Deo&shy;xy&shy;ribo&shy;nu&shy;cleic acid`,
     *  hyphens: manual) + `.`, folded by InlineRunFold into one string,
     *  in an 8ch monospace-32px box. */
    private val MANUAL_INLINE_012 = "DNA means Deo­xy­ribo­nu­cleic acid."

    /** The measured defect: Minikin broke the 16-glyph word every 7 glyphs
     *  with no hyphen (`Deoxyri` / `bonucle` / `ic`, android f 0.9419); the
     *  ref — and iOS, which pre-breaks every run — paint
     *  `Deoxy-` / `ribonu-` / `cleic`. Nothing overflows here (every line
     *  fits 8ch), so rule B alone never fired; the taken soft hyphens do. */
    @Test
    fun firesWhenASoftHyphenBreakWasTaken() {
        val r = fire(text = MANUAL_INLINE_012, wrapWidthPx = 8 * CH)
        assertTrue(r.fired)
        assertEquals("DNA\nmeans\nDeoxy‐\nribonu‐\ncleic\nacid.", r.text)
    }

    /** `hyphens: auto` with a language tag hands the opportunities to
     *  Minikin's dictionary (AutoHyphenation) — the soft-hyphen trigger
     *  stays out of such runs even when the fold took one, or firing would
     *  hard-newline the run with softWrap OFF and suppress the
     *  hyphenation that was just switched on. */
    @Test
    fun theSoftHyphenTriggerStaysOutOfDictionaryRuns() {
        val r = PreBreakPipeline.preBreak(
            text = MANUAL_INLINE_012, wrapWidthPx = 8 * CH, enabled = true,
            softWrapAllowed = true, allowMidWordBreak = false, preservesSpaces = false,
            dictionaryHyphenation = true, measure = mono)
        assertFalse(r.fired)
        assertSame(MANUAL_INLINE_012, r.text)
        // hyphens-auto-010's wire (`regulation implementation now`, 6ch,
        // `hyphens: auto`, lang en): the overlong words are dictionary
        // opportunities, so neither trigger claims the run — identity.
        val auto010 = "regulation implementation now"
        val a = PreBreakPipeline.preBreak(
            text = auto010, wrapWidthPx = 6 * CH, enabled = true,
            softWrapAllowed = true, allowMidWordBreak = false, preservesSpaces = false,
            dictionaryHyphenation = true, measure = mono)
        assertSame(auto010, a.text)
    }

    /** A soft hyphen the fold did NOT take (the word fits) changes nothing
     *  — identity, so a wide box keeps its frozen capture. */
    @Test
    fun anUntakenSoftHyphenDoesNotFire() {
        val r = fire(text = MANUAL_INLINE_012, wrapWidthPx = 40 * CH)
        assertFalse(r.fired)
        assertSame(MANUAL_INLINE_012, r.text)
    }

    // ── Wave 53 (lane L2, F1) — the SPACE-LESS soft-hyphen run ────────
    //
    // The `:166` decline used to stand down for EVERY space-less run on the
    // claim that wave 21's whole-run gate owns them. It does not own a run
    // that carries U+00AD: `DecorationOps.hasSoftWrapOpportunity` counts the
    // soft hyphen, so softWrap stays on and Minikin (Hyphens.None under
    // `manual`) emergency-breaks the word with no hyphen glyph —
    // hyphens-span-001 android painted `highwa`/`y` (wave52-ship P 0.9532,
    // DEGENERATE). Every payload below is VERBATIM off tools/titan/runs/
    // wave52-ship/sections/css-text/per-test-ir (byte-identical to
    // wave53-open: sha1 7b52d8c7… for hyphens-span-001.json on both).
    //
    // MUTATION PROOF: tools/titan/results/wave53-soft-hyphen/_note.md §3
    // (M-a … M-d, red → restore byte-exact sha256 → green, executed).

    /** hyphens-span-001 box 1 (`…hyphens-span-001__1-301`), the leaf
     *  component VERBATIM (minified only): 6ch, `hyphens: manual`. */
    private val SPAN_001_BOX1 = """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-text__hyphens__hyphens-span-001__1-301","name":"wpt__css-text__hyphens__hyphens-span-001__1","properties":[{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderRightStyle","data":"SOLID"},{"type":"BorderBottomStyle","data":"SOLID"},{"type":"BorderLeftStyle","data":"SOLID"},{"type":"BorderTopColor","data":{"srgb":{"r":1,"g":0.6470588235294118,"b":0},"original":"orange"}},{"type":"BorderRightColor","data":{"srgb":{"r":1,"g":0.6470588235294118,"b":0},"original":"orange"}},{"type":"BorderBottomColor","data":{"srgb":{"r":1,"g":0.6470588235294118,"b":0},"original":"orange"}},{"type":"BorderLeftColor","data":{"srgb":{"r":1,"g":0.6470588235294118,"b":0},"original":"orange"}},{"type":"MarginTop","data":{"px":5}},{"type":"MarginRight","data":{"px":5}},{"type":"MarginBottom","data":{"px":5}},{"type":"MarginLeft","data":{"px":5}},{"type":"Width","data":{"type":"length","original":{"v":6,"u":"CH"}}},{"type":"Hyphens","data":"MANUAL"}],"text":"high­way","meta":{"role":"ws-after"}}]}"""

    /** hyphens-auto-control box 1 (`…hyphens-auto-control__0-235`),
     *  VERBATIM: 12ch, `hyphens: auto`, lang en-us. */
    private val AUTO_CONTROL_BOX1 = """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-text__hyphens__hyphens-auto-control__0-235","name":"wpt__css-text__hyphens__hyphens-auto-control__0","properties":[{"type":"Display","data":"BLOCK"},{"type":"Hyphens","data":"AUTO"},{"type":"BorderTopWidth","data":{"px":1}},{"type":"BorderRightWidth","data":{"px":1}},{"type":"BorderBottomWidth","data":{"px":1}},{"type":"BorderLeftWidth","data":{"px":1}},{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderRightStyle","data":"SOLID"},{"type":"BorderBottomStyle","data":"SOLID"},{"type":"BorderLeftStyle","data":"SOLID"},{"type":"BorderTopColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderRightColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderBottomColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderLeftColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"FontFamily","data":["Courier New","Courier","monospace"]},{"type":"Width","data":{"type":"length","original":{"v":12,"u":"CH"}}}],"text":"fragilistic­expiali","meta":{"sourceTag":"code","role":"ws-after","lang":"en-us"}}]}"""

    /** The leaf's own `text` through the PRODUCTION decoder — so a pin
     *  that holds on a hand-typed literal but not on the wire cannot pass. */
    private fun leafText(doc: String): String =
        com.styleconverter.runtime.core.renderer.SlotComposer.compose(
            com.styleconverter.runtime.core.ir.IRDocumentDecoder.decode(doc))[0]._text!!

    /** Pin (a): the measured defect. At the ref's 6ch box `highway` does
     *  not fit, the fold takes the authored soft hyphen (greedy, §5.3) and
     *  the F5 trigger fires — the ref's `high‐`/`way`, hyphen painted. */
    @Test
    fun aSpacelessSoftHyphenRunFiresWithTheTakenHyphen() {
        val text = leafText(SPAN_001_BOX1)
        // The wire really is the space-less U+00AD string.
        assertEquals("high\u00ADway", text)
        val r = fire(text = text, wrapWidthPx = 6 * CH)
        assertTrue(r.fired)
        assertEquals("high\u2010\nway", r.text)
    }

    /** Pin (b): the same run in a box wide enough for `highway` takes no
     *  break — identity (the SAME instance), so a fitting U+00AD run keeps
     *  Minikin's frozen rendering. */
    @Test
    fun aFittingSpacelessSoftHyphenRunIsIdentity() {
        val text = leafText(SPAN_001_BOX1)
        val r = fire(text = text, wrapWidthPx = 8 * CH)
        assertFalse(r.fired)
        assertSame(text, r.text)
    }

    /** Pin (c): hyphens-auto-control's `fragilistic­expiali` now ENTERS the
     *  pipeline (it carries U+00AD), and the fold does take the soft hyphen
     *  at 12ch — but the run is `hyphens: auto` + a language tag, so its
     *  opportunities are Minikin's dictionary's (AutoHyphenation) and the
     *  taken-hyphen trigger stays out: identity, the must-not-move control. */
    @Test
    fun aDictionarySpacelessSoftHyphenRunStaysIdentity() {
        val text = leafText(AUTO_CONTROL_BOX1)
        // The production gate reads exactly this wire as engaged.
        assertTrue(AutoHyphenation.engaged("AUTO", "en-us"))
        val r = PreBreakPipeline.preBreak(
            text = text, wrapWidthPx = 12 * CH, enabled = true,
            softWrapAllowed = true, allowMidWordBreak = false, preservesSpaces = false,
            dictionaryHyphenation = true, measure = mono)
        assertFalse(r.fired)
        assertSame(text, r.text)
    }

    /** Pin (d): a multi-opportunity space-less run — hyphenate-character-
     *  001's run piece 1 VERBATIM (`…hyphenate-character-001__1-177`
     *  meta.runs[0].text) at that test's 4.5ch box — splits at EVERY usable
     *  soft hyphen into the ref's five lines (the `hyphenate-character`
     *  wire is not threaded — U+2010 is baked, BACKLOG 4(f)). */
    @Test
    fun aMultiOpportunitySpacelessRunSplitsIntoFiveLines() {
        val r = fire(text = "im\u00ADple\u00ADmen\u00ADta\u00ADtion", wrapWidthPx = 4.5f * CH)
        assertTrue(r.fired)
        assertEquals("im\u2010\nple\u2010\nmen\u2010\nta\u2010\ntion", r.text)
    }

    // ── Wave 52 (lane L9, F4) — the drawn-marker CLAMP on a fired run ──
    //
    // MUTATION PROOF (executed 2026-10-05, lane L9, mutate.py M5 —
    // mutations-compose.result.json): with the fired result built from the
    // untrimmed `lines` instead of `clamped`, the 025 pin below fails (the
    // untrimmed paragraph where 4 lines + "…" are asserted). Restored
    // byte-exact (sha256-verified).

    /** block-ellipsis-025, the Compose arm F3 cannot reach: the 34ch word
     *  fires rule B (an unbreakable overflow), the run renders with
     *  softWrap = false, and there TextOverflow.Ellipsis is the wave-39
     *  landmine — so the marker is BAKED into the fired string instead:
     *  four lines, the fourth the UA ellipsis alone (the ref's picture). */
    @Test
    fun aFiredRunUnderADrawnMarkerClampBakesTheEllipsis() {
        val text = "Test passes if there are 4 lines and the last one only contains … " +
            "supercalifragilisticexpialidocious supercalifragilisticexpialidocious Test fails: this should not be visible"
        val r = PreBreakPipeline.preBreak(
            text = text, wrapWidthPx = 32.5f * CH, enabled = true,
            softWrapAllowed = true, allowMidWordBreak = false, preservesSpaces = false,
            clampLines = 4, measure = mono)
        assertTrue(r.fired)
        assertEquals(
            "Test passes if there are 4 lines\nand the last one only contains …\n" +
                "supercalifragilisticexpialidocious\n…",
            r.text
        )
    }

    /** The clamp is consulted ONLY on a fired run: an ordinary wrapping
     *  paragraph under `line-clamp: 1` stays Minikin's (identity) and gets
     *  its marker from the placeholder's overflow decision instead — one
     *  owner per case, the frozen wrap positions untouched. */
    @Test
    fun theClampAloneNeverFires() {
        val text = "This text is left-aligned        Clamped"
        val r = PreBreakPipeline.preBreak(
            text = text, wrapWidthPx = 29 * CH, enabled = true,
            softWrapAllowed = true, allowMidWordBreak = false, preservesSpaces = false,
            clampLines = 1, measure = mono)
        assertFalse(r.fired)
        assertSame(text, r.text)
    }

    // ── Wave 54 (lane L3, U2-android) — the hyphenate-character string ──
    //
    // css-text-4 §6.3: a taken soft hyphen paints the author's string, and
    // its WIDTH decides the breaks (hyphenate-character brief §4.C). Inputs
    // are the hyphenate-character-001/-003/-004 host's meta.runs pieces
    // VERBATIM (wave53-final css-text per-test-ir, `…__1-177` / `-201`); the
    // boxes are those tests' 4.5ch / 5.5ch / 6.5ch (CH-linear, so the 16px
    // tests' ratios hold at this file's 32px CH).
    //
    // MUTATION PROOF (executed; tools/titan/results/wave54-hyphenate-character/
    // mutations.log): U2A-a `SoftHyphenCuts.took` answering by the wave-52
    // count → pin (a) and the `""` row of the width pin return identity;
    // U2A-b PreBreakPipeline dropping `hyphenChar` from the fold → (a)-(d)
    // paint U+2010. Restored byte-exact (sha256) after each.

    /** Pieces 1-3 of the host's runs, verbatim. */
    private val HC_PIECE_1 = "im­ple­men­ta­tion"
    private val HC_PIECE_2 = " ini­tial­iza­tion"
    private val HC_PIECE_3 = " re­al­iza­tion"

    /** All gates open, `hyphens: manual` (no dictionary), one hyphen string. */
    private fun fireWith(text: String, widthCh: Float, hyphenChar: String) =
        PreBreakPipeline.preBreak(
            text = text, wrapWidthPx = widthCh * CH, enabled = true,
            softWrapAllowed = true, allowMidWordBreak = false, preservesSpaces = false,
            hyphenChar = hyphenChar, measure = mono)

    /** (a) -001: `""` — the cut is TAKEN although nothing is painted, so the
     *  run fires with the ref's five lines and no hyphen glyph. Before wave 54
     *  the count saw 0 hyphens and declined: Minikin's `impl/emen/tati/on`. */
    @Test
    fun anEmptyHyphenateCharacterStillFiresAtTheTakenCuts() {
        val r = fireWith(HC_PIECE_1, 4.5f, "")
        assertTrue(r.fired)
        assertEquals("im\nple\nmen\nta\ntion", r.text)
    }

    /** (b) the width case: with `""` `real` (4ch) fits 4.5ch, so the fold
     *  breaks `real/iza/tion` (the ref); U+2010 makes `real‐` 5ch and gives
     *  `re‐/al‐/iza‐/tion` (today's Android picture). */
    @Test
    fun theHyphenStringsWidthDecidesTheBreaks() {
        assertEquals("real\niza\ntion", fireWith(HC_PIECE_3, 4.5f, "").text)
        assertEquals("re‐\nal‐\niza‐\ntion", fireWith(HC_PIECE_3, 4.5f, "‐").text)
    }

    /** (c) -003: `"\2022"` decoded by the reader → U+2022 at every cut. */
    @Test
    fun aBulletHyphenateCharacterIsPaintedAtEveryCut() {
        val r = fireWith(HC_PIECE_1, 5.5f, "•")
        assertTrue(r.fired)
        assertEquals("im•\nple•\nmen•\nta•\ntion", r.text)
    }

    /** (d) -004: the 3ch `/-/` — `tial/-/` is 7ch and overflows 6.5ch (the
     *  ref's overflowing line 2), taken by the earliest-op fallback. */
    @Test
    fun aMultiCharacterHyphenateCharacterOverflowsLikeTheRef() {
        val r = fireWith(HC_PIECE_2, 6.5f, "/-/")
        assertTrue(r.fired)
        assertEquals("ini/-/\ntial/-/\niza/-/\ntion", r.text)
    }

    /** hyphenate-limit-chars-001 (R2-N6): its 9 runs carry `"-"` into the
     *  pipeline, but `example` has no space and no U+00AD, so the early
     *  guard declines before the fold — identity, the hyphen string unread. */
    @Test
    fun aSpacelessRunWithoutSoftHyphensIgnoresTheHyphenString() {
        val text = "example"
        val r = PreBreakPipeline.preBreak(
            text = text, wrapWidthPx = 1 * CH, enabled = true,
            softWrapAllowed = true, allowMidWordBreak = false, preservesSpaces = false,
            dictionaryHyphenation = true, hyphenChar = "-", measure = mono)
        assertFalse(r.fired)
        assertSame(text, r.text)
    }

    /** Every U+00AD text on the wave53-final wire (21 distinct, 17
     *  documents — the U2 default population plus the carriers), verbatim,
     *  plus the inline-012 fold. */
    private val CORPUS_SHY_TEXTS = listOf(
        "This time, Mark, who had always been the center of attention in\nany social gathering, walked into the room uncharacteristi­cally quietly, barely speaking as he settled into a chair.\n\nWhen asked, he said that he was fine, when he wasn't really fine.",
        "This time, Mark, who had always been the center of attention in any social gathering, walked into the room uncharacteristi­cally quietly, barely speaking as he settled into a chair. When asked, he said that he was fine, when he wasn't really fine.",
        "im­ple­men­ta­tion ini­tial­iza­tion re­al­iza­tion hy­phen­ation",
        HC_PIECE_1, HC_PIECE_2, HC_PIECE_3, " hy­phen­ation",
        "قىل­", "fragilistic­expiali", "Deoxy­ribo­nucleic acid",
        "Deo­xy­ribo­nu­cleic acid", "Deoxy­ribonucleic acid",
        "means Deoxy­ribo­nucleic acid", "means Deo­xy­ribo­nu­cleic acid",
        "12345678 Deoxy­ribo­nucleic Deoxy­ribo­nucleic 12345678",
        "high­way", "igh­way", "­way", "high­", "high­wa", "hyphen­ation",
        MANUAL_INLINE_012
    )

    /** (e) R2-N6 — the default argument is identity in DECISION, not only in
     *  instance: over every corpus U+00AD text at every half-ch box from 1ch
     *  to 40ch, the wave-54 walk answers exactly what the wave-52 count did
     *  (and answers by itself — the fallback is never needed). */
    @Test
    fun theWalkAgreesWithTheWave52CountOnEveryCorpusRun() {
        var cases = 0
        for (text in CORPUS_SHY_TEXTS) for (half in 2..80) {
            val lines = GreedyLineBreaker.lines(text, half * CH / 2, mono)
            val dflt = WordBreakOpportunities.DEFAULT_HYPHEN_CHARACTER
            assertEquals("$text @ ${half / 2.0}ch", SoftHyphenCuts.legacyCount(text, lines, dflt),
                SoftHyphenCuts.took(text, lines, dflt))
            assertTrue("$text @ ${half / 2.0}ch must align", SoftHyphenCuts.walkAligns(text, lines, dflt))
            cases++
        }
        assertEquals(22 * 79, cases)
    }
}
