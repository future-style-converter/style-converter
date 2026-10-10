package com.styleconverter.runtime.typography.wrapping

/**
 * PreBreakPipeline.kt
 * typography/wrapping — wave 38 (lane N8). css-text-3 §5.5 rule B on
 * Compose: the emergency character break that CSS FORBIDS.
 *
 * ── THE DEFECT, measured ────────────────────────────────────────────
 * WPT `css-text/hyphens/hyphens-manual-010` (and its identical twin
 * `hyphens-none-011`) puts `Deoxyribonucleic acid` in a `width: 10ch`
 * monospace-32px box. Chromium keeps the 16-character word on ONE line
 * that OVERFLOWS the border box ("only 'ucleic' should be outside", per
 * the test's own prose) and moves only `acid` to line 2. Compose renders
 * THREE lines — `Deoxyribo` / `nucleic` / `acid` — because Minikin
 * character-breaks any word that does not fit. Frozen wave37-final
 * android-ref SSIM: 0.7500 on both tests, against iOS 0.9017 (iOS had
 * already landed rule B in wave 37, lane W7).
 *
 * css-text-3 §5.5 is explicit that this break is NOT allowed by default:
 * `overflow-wrap: normal` — "lines may break only at allowed break
 * points"; the arbitrary mid-word break is what `break-word` / `anywhere`
 * (and `word-break: break-all`) opt IN to.
 *
 * ── WHY THE STRING IS THE ONLY LEVER ────────────────────────────────
 * Compose's line breaker lives inside `Text`/Minikin. `TextStyle` can
 * pick a break STRATEGY (Simple/Balanced/HighQuality) but none of them
 * disables emergency breaking, and there is no per-run "let this word
 * overflow" switch. The one switch that does stop it — `softWrap = false`
 * — stops ALL wrapping, so applying it to `Deoxyribonucleic acid` would
 * put `acid` back on line 1 and lose a break the reference takes.
 *
 * Hence PRE-BREAKING: reproduce the CSS line breaking ourselves
 * ([GreedyLineBreaker], measuring with the render style), hand Compose a
 * string whose CSS-legal breaks are already HARD newlines, and turn
 * `softWrap` off so Minikin can add none of its own. Every line then
 * lands exactly where CSS puts it, and the one overlong line overflows
 * (the placeholder path already renders `TextOverflow.Visible`, CSS 2.1
 * §11.1.1). Identical vehicle to the iOS twin's `.fixedSize(horizontal:)`
 * over a pre-broken string; the web runtime needs nothing because the
 * browser IS the reference.
 *
 * U+200B ZERO WIDTH SPACE was the sketched vehicle for the insertion
 * ("paint-neutral break opportunities where rule B fires") and is
 * deliberately NOT used: it can only ADD break opportunities, and the
 * measured defect needs one REMOVED. A hard newline is equally
 * paint-neutral (it renders no glyph and no advance) and is the only
 * mark that both forces a break where CSS wants one and — with softWrap
 * off — forbids every other. The zero-width space stays the right tool
 * for the mirror-image case (`overflow-wrap: break-word` on a platform
 * that refuses to break at all), which no platform in this repo has.
 *
 * ── BOUNDED BY CONSTRUCTION ─────────────────────────────────────────
 * [preBreak] returns the INPUT STRING ITSELF and `fired = false` on every
 * path except the one where an unbreakable line genuinely overflows a
 * known wrap width in composed WPT capture. The 327-pair dark stage never
 * sets that gate, so those baselines cannot move; and inside WPT capture
 * a run whose words all fit is likewise untouched, so the corpus's
 * ordinary text is still broken by Minikin exactly as the frozen captures
 * recorded.
 *
 * Pure Kotlin (no Compose, no Android) so the JVM suite pins the
 * arithmetic without a device.
 */
object PreBreakPipeline {

    /**
     * The outcome of the pipeline.
     *
     * @property text the string to render — `===` the input whenever
     *   [fired] is false, which is what makes the no-op path byte-stable
     *   (the renderer's downstream `AnnotatedString` construction and its
     *   `remember` keys see the identical instance).
     * @property fired true when rule B applied: [text] carries OUR line
     *   breaks as hard newlines and the caller MUST render it with
     *   `softWrap = false`, or Minikin will re-break the overlong line at
     *   the constraint edge and the whole exercise is undone.
     */
    data class Result(val text: String, val fired: Boolean)

    /**
     * Decide and apply rule B for one text run.
     *
     * @param text the display string, AFTER text-transform, tab-size and
     *   the `hyphens: none` soft-hyphen strip ([SoftHyphenPolicy]) — i.e.
     *   exactly the glyphs that will be measured and painted. Rule A
     *   still runs first so a `none` run can never break at its authored
     *   U+00ADs; a soft hyphen that DOES survive to here (`manual`/
     *   `auto`) is, since wave 41, modeled by the fold itself
     *   ([WordBreakOpportunities]): a fired result carries the taken
     *   points as painted hyphens and no soft hyphen at all, so Minikin
     *   (softWrap off) renders exactly the measured string.
     * @param wrapWidthPx the content-box inline size this run wraps at,
     *   in px. ≤ 0 means "unknown" (an unbounded constraint, or the first
     *   composition before the width has been observed) → decline.
     * @param enabled the composed-WPT capture gate. False on the 327-pair
     *   dark stage and in the per-component inbox, where the committed
     *   baselines are frozen.
     * @param softWrapAllowed `TextWrapConfig.softWrap` — false for
     *   `white-space: nowrap|pre` and `text-wrap: nowrap`, where the run
     *   already lays out on one line and there is nothing to repair.
     * @param allowMidWordBreak `TextWrapConfig.allowMidWordBreak` —
     *   `overflow-wrap: break-word|anywhere` or `word-break: break-all`.
     *   There the emergency break is exactly what CSS asks for, so
     *   Minikin is RIGHT and we must not take the run out of its hands.
     * @param preservesSpaces `white-space: pre-wrap|break-spaces`
     *   (css-text-3 §4.1.2 keeps space runs). The greedy breaker splits
     *   on spaces and would collapse them — a glyph-content rewrite, not
     *   a wrap fix — so those modes decline.
     * @param dictionaryHyphenation wave 40 (lane T2) —
     *   [AutoHyphenation.engaged]: this run is `hyphens: auto` WITH a
     *   language tag, so Minikin's hyphenator is on for it. Rule B's
     *   premise then weakens PER LINE: a word only overflows because it
     *   has "nowhere left to break", and under dictionary hyphenation
     *   `implementation` has break points the space-split
     *   [GreedyLineBreaker] cannot see. Firing on such a line would hand
     *   Minikin a hard-newlined string with `softWrap = false` and
     *   thereby SUPPRESS the very hyphenation we just enabled (measured:
     *   css-text/hyphens-auto-010, whose 6ch box makes every word an
     *   "unbreakable overflow" to the space-split breaker — 0.8530 with
     *   rule B still claiming it). The flag is forwarded, NOT short-
     *   circuited here, because a line with no LETTERS still has nowhere
     *   to break and still needs rule B (css-text/hyphens-punctuation-001's
     *   `00000` runs — see [AutoHyphenation.hasDictionaryOpportunity]).
     * @param clampLines wave 52 (lane L9, F4) — the cap of a fixed-count
     *   `line-clamp` whose marker is DRAWN, or null (a marker-suppressed
     *   clamp, the bare `max-lines` longhand, no clamp — every pre-wave-52
     *   call). Consulted ONLY when this pipeline fires for one of the two
     *   reasons below: the fired string is then trimmed to the cap with the
     *   UA ellipsis baked in by [GreedyLineBreaker.clampLines], because a
     *   fired run renders with `softWrap = false`, where
     *   TextOverflow.Ellipsis is the wave-39 finalMaxLines landmine and
     *   Clip paints no marker (block-ellipsis-025 android: overlong lines
     *   CLIPPED at the box edge, no "…"). A run that does NOT fire keeps
     *   Minikin's own wrapping and gets its marker from the placeholder's
     *   overflow decision instead (ComponentRenderer.placeholderOverflow,
     *   wave 52 F3) — one owner per case, and the 19 passing soft-wrapped
     *   Android clamp hosts keep the wrap positions their captures froze.
     * @param hyphenChar wave 54 (lane L3, U2-android) — the css-text-4
     *   §6.3 `hyphenate-character` string a taken soft hyphen paints
     *   ([HyphenateCharacterApplier.preBreakString]); it enters the fold, so
     *   its WIDTH decides the breaks. The default is the UA hyphen the fold
     *   always used, so every run that declares nothing is byte-identical.
     * @param measure single-line advance of a candidate string, in the
     *   SAME px space as [wrapWidthPx], built over the SAME resolved
     *   style the run renders with.
     */
    @JvmStatic
    fun preBreak(
        text: String,
        wrapWidthPx: Float,
        enabled: Boolean,
        softWrapAllowed: Boolean,
        allowMidWordBreak: Boolean,
        preservesSpaces: Boolean,
        dictionaryHyphenation: Boolean = false,
        clampLines: Int? = null,
        hyphenChar: String = WordBreakOpportunities.DEFAULT_HYPHEN_CHARACTER,
        measure: (String) -> Float
    ): Result {
        // Every decline returns the SAME instance — see Result.text.
        val identity = Result(text, false)
        if (!enabled) return identity
        if (!softWrapAllowed) return identity
        if (allowMidWordBreak) return identity
        if (preservesSpaces) return identity
        if (wrapWidthPx <= 0f) return identity
        // No space AND no soft hyphen ⇒ the greedy breaker can only ever
        // return the run itself, and the WHOLE-RUN gate wave 21 built
        // (B-RC7: softWrap off when `DecorationOps.hasSoftWrapOpportunity`
        // is false) owns that case at the call site. Declining here keeps
        // exactly one owner per case and keeps those captures byte-identical
        // (the guard's population is unchanged: every space-less run that
        // declined before and carries no U+00AD still declines here).
        // Wave 53 (lane L2, F1) — a space-less run that DOES carry U+00AD
        // is NOT that gate's: `hasSoftWrapOpportunity` counts the soft
        // hyphen as an opportunity, so the wave-21 gate leaves softWrap ON
        // and, under Compose's `Hyphens.None` (TextWrapApplier maps
        // `manual` there), Minikin ignores U+00AD and emergency-breaks the
        // word at the box edge with no hyphen glyph — css-text/hyphens/
        // hyphens-span-001 android painted `highwa`/`y` in all nine boxes
        // (wave52-ship P 0.9532, DEGENERATE). css-text-3 §5.3 `manual`:
        // U+00AD IS a break opportunity and the UA paints the hyphenate
        // character when the line breaks there; §5.5 `overflow-wrap:
        // normal` forbids the emergency break. Nobody else claims such a
        // run, so it enters the fold below, whose F5 taken-soft-hyphen
        // trigger then fires with the measured `high‐`/`way` lines.
        if (text.indexOf(' ') < 0 && text.indexOf('\u00AD') < 0) return identity

        // Reproduce the CSS line breaking, then ask whether any committed
        // line is an unbreakable overflow (the rule-B trigger).
        // (wave 54: measured WITH the run's hyphenate-character string.)
        val lines = GreedyLineBreaker.lines(text, wrapWidthPx, measure, hyphenChar)
        val unbreakableOverflow = GreedyLineBreaker.hasUnbreakableOverflowingLine(
            lines, wrapWidthPx,
            dictionaryHyphenation = dictionaryHyphenation, measure = measure)
        // Wave 52 (lane L9, F5) — the SECOND trigger: a soft-hyphen break
        // was TAKEN. css-text-3 §5.3 `hyphens: manual`: U+00AD IS a break
        // opportunity and the UA paints the hyphenate character when it
        // breaks there. Minikin under Compose's default `Hyphens.None`
        // ignores U+00AD outright (SoftHyphenPolicy's banner), so it
        // desperate-breaks the word at the constraint edge with no hyphen
        // glyph — css-text/hyphens/hyphens-manual-inline-012's 16-glyph
        // word broken every 7 glyphs (android f 0.9419; the ref and iOS,
        // which pre-breaks every run, paint `Deoxy-` / `ribonu-` / `cleic`).
        // The fold above already SPENT the opportunity (a materialised
        // hyphenChar at a line end), so firing hands Minikin exactly the
        // measured string. `hyphens: auto` runs stay out: their
        // opportunities belong to Minikin's dictionary (AutoHyphenation).
        // Wave 54: the walk in SoftHyphenCuts sees a `""` cut too (a count cannot).
        val tookSoftHyphen = !dictionaryHyphenation && SoftHyphenCuts.took(text, lines, hyphenChar)
        if (!unbreakableOverflow && !tookSoftHyphen) {
            // The platform's own greedy breaking already agrees with CSS
            // here — leave the frozen behaviour alone.
            return identity
        }
        // Wave 52 (lane L9, F4) — a fired run under a drawn-marker clamp
        // carries its own ellipsis: trimmed to the cap, marker baked (see
        // the parameter's banner). Identity when the paragraph fits the cap.
        // `text` rides along so the clamp can hide at line N's soft hyphens
        // (block-ellipsis-028 — the fold's display lines dropped them).
        val clamped = clampLines?.let {
            GreedyLineBreaker.clampLines(lines, GreedyLineBreaker.Clamp(it), wrapWidthPx, measure, source = text, hyphenChar = hyphenChar)
        } ?: lines
        // Fires. Hard newlines carry OUR break positions; the caller pairs
        // this with softWrap = false.
        return Result(clamped.joinToString("\n"), true)
    }
}
