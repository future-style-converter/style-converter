package com.styleconverter.runtime.content

// PseudoTextFold — wave-50 lane B3: the Compose twin of iOS
// StyleEngine/content/PseudoTextFold.swift, for the ::after half only.
//
// THE MEASURED DEFECT (wave49-final captures, Android column). Compose
// composes an ordinary element's generated content as a WRAPPER AROUND the
// host — ContentApplier.ContentWithPseudoElements builds `Row { before,
// content, after }` — while CSS 2.1 §12.1 / css-pseudo-4 §4.1 put the
// ::after box INSIDE the originating element, as its LAST child (web spells
// exactly that: NodeRenderer emits the pseudo span as a positional child;
// iOS folds it into the host's text run). Outside the host box the ::after
// has to share the line with the host's own block box, and in composed-WPT
// capture that box takes `Modifier.fillMaxWidth()` (ComponentRenderer's
// blockFlowWidth — CSS 2.1 §10.3.3 stretch fit / css-sizing-3 §5 used size).
// A Compose Row measures unweighted children in order against the space its
// predecessors left, so the host consumes the whole line and the ::after Text
// is measured at maxWidth = 0: it still occupies the row (its zero-width
// layout wraps one grapheme per line) but draws NOTHING.
//
// Three capture signatures of that one cause, all in
// tools/titan/runs/wave49-final/sections/:
//   * css-lists/counter-reset-reversed-pseudo-001 — Android paints "B7"
//     and "B4" (the ::before runs) and loses "A5"/"A2"; the ink bands sit
//     at y20-31 and y60-71, a 40px row pitch, where iOS has them 20px apart
//     at y20-31 / y40-51. The doubled pitch IS the zero-width wrap of the
//     two-glyph ::after — proof the run was composed and simply drew
//     nothing (android f 0.9884 vs iOS P 0.9999).
//   * css-counter-styles/cjk-decimal/counter-cjk-decimal — every component
//     is `::after`-ONLY, so the Android capture is BLANK in the literal
//     sense: the whole 390x696 PNG decodes to ONE colour,
//     RGBA(255,255,255,255), with no ink anywhere
//     (tools/titan/results/wave50-S6/blank-capture-census.txt). That is the
//     strongest evidence for the defect. The coverage number beside it was
//     misquoted until wave-50 lane F4 (skeptic S6-18): the android-ref row's
//     semantic presence is aCoveragePct 0.000 — exact, the capture has no
//     ink — against bCoveragePct 0.816, the REF side as measured in that
//     row's own 696-tall frame; 0.946 is the web-ref / ios-ref rows' ref
//     figure and belongs to them, not here. The canvas grows 600 → 696 px on
//     the wrapped zero-width rows (android f 0.9407 · iOS P 0.9934).
//   * css-contain/contain-content-011 — the host declares `width: 100px`,
//     so there is no squeeze and the defect shows as PLACEMENT instead: the
//     "25" is painted at x≈118, to the RIGHT of the host's border box,
//     where web and iOS put it at x≈16 inside it.
//
// SCOPE — ::after ONLY, deliberately. The ::before run is composed FIRST in
// the same Row, so it already lands adjacent to the host's content edge and
// renders correctly today; folding it too would move 55 corpus tests that
// carry a before-only bucket, which is a device-A/B change, not a lane
// change. The ::after side has no such luck: it is the one the stretch-fit
// host squeezes. Blast radius of this fold, replayed over all 1435
// wave49-final per-test-ir documents with the gates in this file and in
// PseudoTextBridge: 13 tests, 78 components — of which 9 Android cells fail
// today and 4 pass (tools/titan/results/wave50-B3/_note.md has the table).
//
// ONE CLAIM, TWO CONSUMERS: a bucket this fold takes must never ALSO render
// through ContentApplier's wrapper, so [resolve] reports its decision in
// [Folded.afterFolded] and the renderer passes it to
// PseudoBucketExtractor.extractBeforeAfterConfig — the same discipline
// PseudoBoxFold and PseudoBucketExtractor share through
// PseudoGeneratedBox.claim. The decision is computed ONCE, beside the fold,
// because the fold rewrites `_text` and the styling gate below reads it.
//
// The BUCKET question — can an inline run express this payload? — lives in
// PseudoTextBridge, exactly as it does on iOS.
//
// WAVE-53 LANE L1 (nested-list-extractor D) — the ONE ::before this file
// folds: a component carrying `meta.runs` whose first run is TEXT. CSS 2.1
// §12.1 / css-pseudo-4 §4.1 make ::before the element's first inline content,
// and CSS 2.1 §9.2.1.1 puts it in the anonymous block box with the leading
// text — AHEAD of a block child such as a nested `<ol>`. The Row wrapper
// instead lays the WHOLE content column (text + nested list) out to the
// RIGHT of the ::before Text, which would push the nested rows right by the
// marker's advance. So the ::before text is prefixed to BOTH `runs[0].text`
// and `_text` (which one paints depends on whether the run plan engages) and
// `before` is DROPPED from the copy's `pseudos`, so PseudoBucketExtractor
// builds no `Row { before, content }` around it — no ComponentRenderer hunk.
// MEASURED target: wave52-ship css-lists/counter-reset-reversed-nested's
// `Two` (`runs [{text:"Two "},{child:<ol>}]`). Census over all 1435
// wave52-ship per-test-ir documents (tools/titan/results/wave53-plan/
// nested-list-extractor.wire-census.py): it is the ONLY component with runs
// plus a ::before `_text`; the other runs+pseudos component
// (display-contents-dynamic-before-after-001 __1__3) has no `_text`, so
// PseudoTextBridge returns null there and the fold is identity.

import com.styleconverter.runtime.PropertyTracker
import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRLog
import com.styleconverter.runtime.core.ir.IRRun
import kotlinx.serialization.json.JsonObject

/** Log tag for this fold's named refusals. */
private const val TAG = "PseudoTextFold"

object PseudoTextFold {

    /**
     * The fold's outcome: the (possibly rewritten) component plus whether
     * the `pseudos.after` bucket was CONSUMED here. `afterFolded = true`
     * obliges the caller to keep ContentApplier's wrapper off that bucket.
     */
    data class Folded(val component: IRComponent, val afterFolded: Boolean)

    /**
     * Fold the component's baked `::after` text onto the END of its own
     * text, per CSS 2.1 §12.1 order (element content, then ::after).
     *
     * Identity — the SAME instance back, `afterFolded = false` — for every
     * component this fold does not claim, which is every component of the
     * committed 327-fixture baseline corpus (none carries `pseudos`) and
     * every before-only bucket. Committed captures are byte-stable by
     * construction.
     *
     * ORDERING GUARDS, and their corpus weight. Two refusals below exist
     * only to keep css-pseudo-4 §4.1's paint order (::before, the element's
     * content, ::after): the `children` guard, and the box-shaped-`::before`
     * guard that follows it. Census over all 1435 `wave49-final`
     * per-test-ir documents: ONE component carries a box-shaped `::before`
     * (css-pseudo/before-as-flex-container) and it has no `::after`, so
     * ZERO documents reach the second guard — it moves no cell and exists
     * so the inversion cannot appear the moment a bucket does.
     */
    fun resolve(component: IRComponent): Folded {
        // The overwhelmingly common path: no generated content at all.
        val pseudos = component.pseudos ?: return Folded(component, false)
        // Root-scope buckets belong to RootPseudoBox (wave-28 lane PG):
        // that family is `content:"" + width/height/background`, a BOX and
        // not a text run, and folding here would double-render it. Same
        // guard direction as RootPseudoBox's banner, from the other shore.
        if (component.role == "body-root") return Folded(component, false)
        // `meta.runs` is AUTHORITATIVE over `_text` (schema/spec/03-children.md
        // §4.1): wave-53 lane L1 folds a leading ::before into both channels
        // (see the banner); the ::after keeps its named refusal here.
        if (component.runs != null) {
            // The pre-wave-53 refusal, unchanged: an ::after text bucket under
            // a run plan would vanish with the text slot (a BOX-shaped one is
            // PseudoBoxFold's, as before, and needs no line).
            val after = pseudos["after"] as? JsonObject
            if (after != null && PseudoGeneratedBox.claim(after, "after", component.role) == null) {
                PropertyTracker.markUnhandled("PseudoText::runs")
                IRLog.warn(TAG, "component carries meta.runs — ::after text not folded (runs own the content slot)")
            }
            return Folded(foldBeforeIntoRuns(component, pseudos, component.runs), afterFolded = false)
        }
        // No `::after` payload → nothing for this file to do. (`before` and
        // `marker` are deliberately untouched: the wrapper and the list
        // marker path own them — see the SCOPE note in the file banner.)
        val bucket = pseudos["after"] as? JsonObject ?: return Folded(component, false)
        // A bucket that generates a BLOCK-LEVEL BOX is PseudoBoxFold's, via
        // the SAME claim function PseudoBucketExtractor consults — so the
        // one payload can never render through two paths.
        if (PseudoGeneratedBox.claim(bucket, "after", component.role) != null) {
            return Folded(component, false)
        }
        // css-pseudo-4 §4.1 places ::after AFTER the element's children. A
        // fold appends to the element's own TEXT, which paints BEFORE the
        // child stack — the wrong order — so a component with children keeps
        // the wrapper path it has today. Named, not silently mis-ordered.
        if (!component.children.isNullOrEmpty()) {
            PropertyTracker.markUnhandled("PseudoText::after-children")
            IRLog.warn(TAG, "::after on a component with children is not folded (it must trail the children)")
            return Folded(component, false)
        }
        // …and the SAME ordering rule from the other direction, which the
        // guard above cannot see (wave-50 skeptic S3). ComponentRenderer runs
        // THIS fold FIRST and PseudoBoxFold SECOND — `PseudoTextFold.resolve(
        // ContentsUnboxing.resolve(component))`, then `PseudoBoxFold.resolve(
        // pseudoTextFold.component)` — and PseudoBoxFold splices a claimed
        // `::before` box in as the host's FIRST CHILD. That child does not
        // exist yet when the children guard runs, so a host carrying a
        // box-shaped `::before` AND a text-shaped `::after` would fold the
        // `::after` glyphs into `_text`, which the renderer paints BEFORE the
        // child stack: the `::after` ink would land AHEAD of the `::before`
        // box — the exact inversion of css-pseudo-4 §4.1 (::before first, the
        // element's content, ::after last). One claim function, now three
        // consumers (PseudoBoxFold, PseudoBucketExtractor, here): refuse and
        // keep the wrapper path, which at least keeps the two runs in order.
        //
        // CORPUS CENSUS (all 1435 `tools/titan/runs/wave49-final/sections/
        // */per-test-ir` documents, this file's own predicate replayed with
        // the real PseudoGeneratedBox.claim): exactly ONE component in the
        // corpus carries a box-shaped `::before` at all —
        // css-pseudo/before-as-flex-container — and it carries NO `::after`,
        // so ZERO documents carry the both-buckets shape this guard refuses.
        // It cannot move a single corpus cell; it is a correctness guard
        // against the ordering inversion, not a score change.
        if (PseudoGeneratedBox.claim(
                pseudos["before"] as? JsonObject, "before", component.role) != null
        ) {
            PropertyTracker.markUnhandled("PseudoText::after-before-box")
            IRLog.warn(
                TAG,
                "::after is not folded under a box-shaped ::before " +
                    "(the fold would paint it ahead of the generated box)")
            return Folded(component, false)
        }
        // The declaration gate + the baked string. Null = nothing an inline
        // run can honestly express; every refusal inside is already named.
        val run = PseudoTextBridge.inlineRun(bucket, component)
            ?: return Folded(component, false)
        // css-cascade-5 §7.3 / css-pseudo-4 §2: `component._text` is ONE
        // uniform run, so the bucket's OWN styling may only apply when the
        // pseudo is the component's SOLE ink. Otherwise the host's text
        // would be repainted in the pseudo's colour/size — a wrong render
        // where today's wrapper at least keeps the two runs separate.
        val hostText = component._text ?: ""
        if (run.styling.isNotEmpty() && hostText.isNotEmpty()) {
            PropertyTracker.markUnhandled("PseudoText::styled-nonuniform-after")
            IRLog.warn(TAG, "::after styling cannot apply — the fold is not the component's sole text run")
            return Folded(component, false)
        }
        // The fold itself. `_text` bakes its own separators (" 1" / "1 "),
        // so plain concatenation is the whole job; the run is non-empty by
        // construction (inlineRun never returns an empty string).
        return Folded(
            component.copy(
                _text = hostText + run.text,
                // Appended LAST so the pseudo's own declarations win the
                // extractors' last-wins cascade, exactly as the author's
                // `::after { color: … }` beats the inherited value.
                properties = component.properties + run.styling
            ),
            afterFolded = true
        )
    }

    /**
     * Wave-53 lane L1 — the run-plan ::before fold (see the file banner).
     * Returns the SAME instance for every shape it does not claim: no
     * `before` bucket, a child-first run list (no leading text run to
     * prefix), a box-shaped ::before (PseudoGeneratedBox's), a bucket the
     * bridge refuses, or a STYLED one (typed styling would restyle the whole
     * leading run). Each refusal is named; none is silent.
     */
    private fun foldBeforeIntoRuns(
        component: IRComponent,
        pseudos: JsonObject,
        runs: List<IRRun>
    ): IRComponent {
        // No ::before payload → nothing for this path to do.
        val before = pseudos["before"] as? JsonObject ?: return component
        // The leading run must be TEXT for a prefix to mean "first inline".
        val lead = runs.firstOrNull()?.text
        if (lead == null) {
            PropertyTracker.markUnhandled("PseudoText::runs-child-first")
            IRLog.warn(TAG, "meta.runs starts with a child — ::before not folded")
            return component
        }
        // A block-level generated box stays with PseudoBoxFold's claim.
        if (PseudoGeneratedBox.claim(before, "before", component.role) != null) return component
        // The bridge's own gates (and their named refusals) decide the text.
        val run = PseudoTextBridge.inlineRun(before, component) ?: return component
        // css-cascade-5 §7.3: one uniform run cannot carry the pseudo's own
        // styling without repainting the host's leading text in it.
        if (run.styling.isNotEmpty()) {
            PropertyTracker.markUnhandled("PseudoText::styled-nonuniform-before")
            IRLog.warn(TAG, "::before styling cannot apply — the fold is not the component's sole text run")
            return component
        }
        // The fold: the ::before text leads the first run AND `_text`, and
        // `before` leaves `pseudos` so the Row wrapper cannot paint it again
        // (the map stays non-null: `pseudos != null` gates elsewhere read it).
        return component.copy(
            _text = run.text + (component._text ?: ""),
            runs = listOf(IRRun(text = run.text + lead)) + runs.drop(1),
            pseudos = JsonObject(pseudos - "before")
        )
    }
}
