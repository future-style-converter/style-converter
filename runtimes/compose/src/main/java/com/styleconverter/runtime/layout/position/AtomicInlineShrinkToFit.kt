package com.styleconverter.runtime.layout.position

// Wave 50 (lane B2) — ATOMIC INLINE-LEVEL BOXES ARE NOT BLOCK-LEVEL, so the
// composed-WPT block-auto-width fill must not reach them (CSS 2.1 §10.3.9).
//
// ## The measured defect
// `ComponentRenderer`'s `blockFlowWidth` emulates CSS 2.1 §10.3.3 — a
// BLOCK-LEVEL non-replaced box with `width: auto` fills its containing block —
// by chaining `Modifier.fillMaxWidth()` on every composed-WPT box without a
// declared inline size. §10.3.3 is scoped to block-level boxes; §10.3.9 gives
// an ATOMIC inline-level box (inline-block, and by css-display-3 §2.1 the
// inline-flex / inline-grid outer roles) a SHRINK-TO-FIT used width instead.
// The fill was never gated on that distinction, so every declared
// inline-block stretched to the full 358 px composed canvas.
//
// Two frozen cells show it as ink (wave49-final, css-masking):
//
//   android-screenshots/wpt__css-masking__clip-path__clip-path-contentBox-1d.png
//     — GREEN 100×100 at [24,24]-[123,123] (correct) PLUS a RED band at
//       [124,24]-[365,123]: 24 200 px of the `.clipped` box's own
//       `background-color: red`, painted across a content box that runs to the
//       canvas edge. The frozen ref
//       tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/
//         white-black-ink-font-lh-imgpad-htmlpins/css-masking/
//         clip-path__clip-path-contentBox-1d.png is a bare green square, and
//       so are the web and iOS captures of the same run
//       (screenshots/…-1d.png, ios-screenshots/…-1d.png: green only, no red).
//       android f 0.8775 vs web P 0.9585 / iOS P 0.9585.
//
//   …clip-path-contentBox-1e.png — the same RED band at [124,24]-[364,123]
//     (23 034 px), and the green area measures 8 834 px where the reference
//     circle is 7 647: with the box stretched to 342 px of content the
//     `border-radius: 58px` corners land at x≈316-366, so only the LEFT pair
//     of corners still rounds the 100×100 green child. Both halves of that
//     cell are the SAME defect — android f 0.8875 vs web/iOS P 0.9696/0.9695.
//
// The clip itself is right in both: the padding band and the 4 px darkred
// border are gone from the capture, which is exactly what `clip-path:
// content-box` must do (css-masking-1 §5 + css-shapes-1 §6). Only the box's
// WIDTH is wrong. That re-scopes docs/BACKLOG.md queue 0(d) — "the rendered
// shape still diverges from the browser ref" — onto the width model.
//
// ## Scope, stated rather than hidden
//  * DECLARED display only. A box that is inline-level only through the HTML
//    UA sheet (a bare `<span>`, `<a>`, `<q>` with no `display` on its own
//    wire) is NOT claimed here — the same "author keyword or nothing" line
//    `layout/InlineBlockAtom.kt` draws.
//  * `display: inline` is NOT claimed, and it is the bulk of the corpus:
//    88 of the 123 components that declare an inline-level display and would
//    otherwise reach the fill are plain `inline` (the css-pseudo first-letter
//    family, the selectors invalidation family, the :visited tests). A
//    non-replaced inline box has no width at all (CSS 2.1 §10.3.1) and this
//    renderer models it as a block with an inline-ish text flow; changing
//    that is the inline-run lane's work, not a one-line width gate.
//  * `inline-table` is NOT claimed: `table.TableBoxTree.uaRoleOf` already
//    folds it to `Role.TABLE`, whose `shrinkToFitBox` gate suppresses the
//    same fill (CSS 2.1 §17.5.2 / css-tables-3 §5, wave 39 A6). Claiming it here
//    would put one decision in two places.
//  * HORIZONTAL writing mode only ([verticalWritingMode] = false). In a
//    vertical flow the shrink-to-fit axis is the HEIGHT, and `blockFlowWidth`
//    already has a dedicated orthogonal branch (`fillMaxHeight`, wave-47
//    lane Z2) whose corpus was measured with a device gate this lane does not
//    have. ELEVEN corpus tests carry an atomic inline box that INHERITS a
//    vertical mode and are therefore left frozen by this gate:
//    css-contain/contain-inline-size-intrinsic and css-writing-modes/
//    available-size-001 / -002 / -003 / -004 / -005 / -012 / -013 / -014 /
//    -017 / -018. TODO(next vertical-flow lane): fold the §10.3.9 inline axis
//    into that branch and re-measure those 11 on a gate.
//    (This note said "24" until wave-50 lane F1 corrected it — see the blast
//    radius below. The ch-units-vrl and css-tables collapsed-border tests it
//    used to list here are NOT in a vertical flow at the carrier and do
//    change; they are in the 22 now.)
//
// ## Blast radius, enumerated before the change
// `node tools/titan/results/wave50-B2/census.mjs inline` re-derives this from
// the frozen run; the predicate it applies is this file's, verbatim. Over the
// 30 frozen wave49-final sections it finds 33 tests carrying a component that
// declares one of the three roles above with no
// Width/MinWidth/InlineSize/MinInlineSize, no `aspect-ratio` and no
// absolute/fixed position — 11 whose carrier inherits a vertical writing mode
// (left frozen, scope note 4) and TWENTY-TWO in a horizontal one, which are
// the tests this predicate changes (23 components: baseline-with-orthogonal-
// flow-001 carries two). (A 34th, CSS2/css21-errata/s-11-1-1b-009, declares
// `inline-table` and is excluded by both the set above and the census.)
//
// LANE B2 ORIGINALLY LISTED NINE. Wave-50 skeptic S3 ran the predicate through
// the renderer's real inheritance merge and measured 22 —
// `tools/titan/results/wave50-S3/probe-output/atomic-inline-census.txt`, probe
// source `…/probe-sources/AtomicInlineCensusProbeTest.kt.txt`. The census
// script had approximated "vertical" as "this DOCUMENT mentions a vertical
// writing mode anywhere", which hid 13 tests whose vertical declaration sits
// on a sibling subtree; it now resolves the mode per component by inheritance.
// THE PER-CELL TABLE LIVES IN `tools/titan/results/wave50-B2/README.md`, not
// here (wave-50 skeptic S6-18's structural note): `tools/visual/
// doc-staleness-check.sh` derives headline numbers in `docs/`, never in a
// runtime banner, so 22 SSIM values embedded in production Kotlin would rot
// silently at the next gate with nothing to catch them. That README carries
// every test name, its wave49-final Android verdict, and which of them are
// at risk; the watchlist for the gate is
// `tools/titan/results/wave50-gate/watchlist.txt`.
//
// What belongs HERE is the shape of the risk, which does not rot: EIGHT of the
// 22 pass on Android today, so this change can only cost on those — and one of
// the eight is not really a pass at all. `css-transforms/
// backface-visibility-hidden-006` android P 1.0000 is a BLANK-VS-BLANK cell:
// the frozen reference and all three captures decode to a single uniform white,
// with no ink anywhere (wave-50 skeptic S6, `tools/titan/results/wave50-S6/
// blank-vs-blank-passes.json` — one of seven such tests, 21 cells). Its carrier
// declares neither background nor border, so shrinking its used width keeps a
// blank page blank and the 1.0000 is unmoved either way. It must NOT be counted
// as a pass this change puts at risk, and a gate that "keeps" it has measured
// nothing. That leaves SEVEN real at-risk passes, the three lowest
// (0.9709 / 0.9713 / 0.9817) with the least head-room.
// Android column only — this is Compose code, so no web or iOS cell can move,
// and the ring-fenced `filter-effects/backdrop-filter-basic-blur` declares no
// atomic inline-level box at all, so it is untouched by construction.

// The IR property record this predicate reads.
import com.styleconverter.runtime.core.ir.IRProperty
// Display arrives either as a bare JSON string or inside a {"keyword": …}
// object (both shapes are live in the corpus — see InlineBlockAtom's note).
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

object AtomicInlineShrinkToFit {

    /**
     * The atomic inline-level outer display roles (css-display-3 §2.1),
     * normalised to the wire's underscore spelling.
     *
     * `inline-table` is deliberately absent — see the file header's scope
     * note: `table.TableBoxTree` owns that box's §17.5.2 auto width.
     */
    private val ATOMIC_INLINE_DISPLAYS =
        setOf("INLINE_BLOCK", "INLINE_FLEX", "INLINE_GRID")

    /**
     * Does CSS 2.1 §10.3.9 (shrink-to-fit) own this box's used inline size,
     * so that §10.3.3's block-auto fill must be suppressed?
     *
     * @param properties the box's effective property list, as
     *   `ComponentRenderer` already holds it for its sibling width gates
     *   (`hasExplicitWidth`, `hasAspectRatio`). The LAST `Display` entry wins,
     *   matching the cascade order the bucket fold leaves in the list.
     * @param verticalWritingMode the renderer's `verticalWm` flag. True keeps
     *   the box on its frozen path — the orthogonal-flow branch owns the
     *   inline axis there (file header, scope note 4).
     */
    fun suppressesBlockAutoWidth(
        properties: List<IRProperty>,
        verticalWritingMode: Boolean,
    ): Boolean {
        // Scope gate: vertical flows keep the wave-47 Z2 geometry verbatim.
        if (verticalWritingMode) return false
        // No declared display ⇒ the box is block-level (or inline by UA tag
        // only, which this lane does not claim) ⇒ §10.3.3 still applies.
        val keyword = declaredDisplay(properties) ?: return false
        return keyword in ATOMIC_INLINE_DISPLAYS
    }

    /**
     * The box's own declared `display` keyword, upper-cased and
     * underscore-normalised, or null when it declares none.
     *
     * Reads BOTH live wire shapes: the bare string (`"data": "INLINE_BLOCK"`,
     * which is what every corpus carrier uses) and the `{"keyword": …}`
     * wrapper some fixtures emit. Anything else — an object without a
     * keyword, a number — answers null rather than guessing.
     */
    internal fun declaredDisplay(properties: List<IRProperty>): String? {
        // Last wins: a later declaration (author sheet, then the wave-7
        // selector/media bucket fold) overrides an earlier one.
        val data = properties.lastOrNull { it.type == "Display" }?.data ?: return null
        val raw = when (data) {
            // Bare string wire.
            is JsonPrimitive -> data.contentOrNull?.takeIf { data.isString }
            // {"keyword": "inline-block"} wrapper.
            is JsonObject -> (data["keyword"] as? JsonPrimitive)?.contentOrNull
            else -> null
        } ?: return null
        // `inline-block` and `INLINE_BLOCK` are the same keyword on this wire.
        return raw.uppercase().replace('-', '_')
    }
}
