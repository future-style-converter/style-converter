#!/usr/bin/env node
//
// tools/titan/wpt-not-applicable.mjs — auto-bucketer for "test-not-applicable"
// classification of WPT reftests that exercise web-platform features which
// Style-Converter's static IR / SDUI pipeline cannot represent by design.
//
// Background — why this module exists:
//
//   The swarm-001 round of investigations (tools/titan/investigations/swarm-001/)
//   sampled 29 representative `structural-divergence` failures from the
//   Phase-2 swarm-2b-w2 run and root-caused them. ~16/29 turned out NOT to
//   be Style-Converter regressions — they are tests of web-platform features
//   the project explicitly does not model (Shadow DOM, declarative inline
//   formatting context, `<script>`-driven DOM mutation, browser print
//   medium, `<table>` layout, native form-control rendering, …).
//
//   The brief is to encode those 17 architectural exclusions as cheap
//   regex heuristics that run on the WPT source HTML (and the matched
//   ref) BEFORE the swarm spends capture / compare cycles on them. The
//   rules surface a `tags: [...]` list per test and a top-level
//   `notApplicable` map in tools/titan/wpt-buckets.json so the dashboard can
//   report a separate `test-not-applicable` bucket instead of inflating
//   the structural-divergence count.
//
// Hard rules (per the FIX-D caller spec):
//
//   - String-grep based, no DOM parse — must scale to ~24k WPT files in
//     the same <60s budget bucket-wpt.mjs already meets.
//   - Each rule documents its match pattern in code comments AND cites the
//     swarm-001 investigation file that surfaced it (so `git blame` can
//     answer "why this regex").
//   - Rules run against test source HTML AND the matched reference HTML
//     (some tags only appear in the ref — e.g. crash-test-blank-ref).
//   - Tests pinning each rule live in wpt-not-applicable.test.mjs (≥3
//     per rule: positive / negative / edge case).
//
// Public API:
//
//   tagsForTest({ html, refHtml, testRel, refPath }) -> string[]
//
//     Returns the list of `requires-*` / `crash-test-*` tags that match.
//     Empty array means "no architectural-exclusion tag fires" — the test
//     remains in its existing A/B/C bucket. A non-empty array means the
//     bucketer should mark the test `notApplicable: { test: tags[] }` and
//     downstream consumers should skip SSIM gating.
//
//   classifyAll(records) -> { notApplicable, tagHistogram }
//
//     Bulk-classify a list of records `{ rel, html, refHtml }` and return
//     the assembled `notApplicable` map plus a per-tag count histogram.
//     Pure function — no IO. Consumed by bucket-wpt.mjs at the end of its
//     classify pass and by unit tests.
//
//   RULES — exported array of `{ tag, description, swarm001Source, test }`
//     so the test file can iterate them generically and pin every rule
//     without manually listing all 17 again.
//

// ---------------------------------------------------------------------------
// Per-rule regex panel.
//
// Conventions:
//   - Each `test(html, ctx)` callback receives the raw test HTML string and
//     a context bag `{ refHtml, testRel, refPath }`. Rules that only need
//     the test source can ignore `ctx`; rules that inspect the ref (e.g.
//     crash-test-blank-ref) read `ctx.refHtml` and `ctx.refPath`.
//   - Returns `true` when the architectural-exclusion tag should fire.
//   - Comments cite the swarm-001 investigation file under
//     tools/titan/investigations/swarm-001/ that originally surfaced the
//     tag. That source-of-truth lives in version control so the rationale
//     survives regex evolution.
// ---------------------------------------------------------------------------

// Compile rule regexes once at module load (hot path: ~24k invocations
// during a full `node tools/titan/bucket-wpt.mjs` run).

const RX = {
    // Rule 1 — requires-tree-nesting:
    //   The IR is a flat component list; the WPT extractor only preserves
    //   the first ~5 levels of DOM nesting (FIX-A is widening this to 5;
    //   tests that need MORE than 5-level nesting still can't be evaluated).
    //   Heuristic: count `<div`/`<span`/etc. depth-pushing tags in the
    //   source. We can't actually walk the DOM with a regex, so the proxy
    //   is "more than 5 nested opens of a flow tag with no matching close
    //   between them". A simpler & cheaper proxy used here: count the
    //   maximum chain of consecutive `<div…><div…><div…><div…><div…><div…>`
    //   (or other block tags) without an intervening `</div>`. The regex
    //   counts six-or-more chained opens.
    //   Source: investigations/swarm-001/css-transforms__3d-rendering-context-and-inline.json
    //           investigations/swarm-001/css-grid__grid-abspos-staticpos-align-items-center.json
    //           investigations/swarm-001/css-overflow__contain-body-overflow-001.json (via 'requires-tree-structure')
    //   Note the brief explicitly mentions FIX-A landing 5 levels — we
    //   require >5 (i.e. ≥6) here so tests in the 1-5 range that FIX-A
    //   covers are NOT pre-emptively excluded.
    nestedOpenChain: /(?:<(?:div|span|section|article|header|footer|main|aside|nav|p|ul|ol|li|figure|figcaption|details|summary|template|slot)\b[^>]*>\s*){6,}/i,

    // Rule 2 — requires-inline-FC / requires-text-runs:
    //   Tests that depend on inline-formatting-context layout: text-runs,
    //   <br>, hypothetical-static-position of an inline abspos child,
    //   bidi reordering, anonymous-table inline-fixup, etc. The IR is
    //   block-component-only.
    //   Heuristic: the test source contains `<br`, OR a CSS rule mentioning
    //   `unicode-bidi`, `direction` (with non-trivial value), `bidi-override`,
    //   `dir=`, or any text-decoration property tied to glyph layout
    //   (text-underline-offset, text-decoration-thickness when applied with
    //   inline children).
    //   A more targeted signal: presence of `<br` together with `position:absolute`
    //   inside an inline element — but the cheap-and-broad version below
    //   matches strongly enough on the swarm-001 corpus.
    //   Source: investigations/swarm-001/CSS2__hypothetical-inline-alone-on-second-line.json
    //           investigations/swarm-001/css-text__empty-span-001.json
    //           investigations/swarm-001/css-text-decor__text-decoration-decorating-box-thickness-001.json
    //           investigations/swarm-001/css-writing-modes__available-size-001.json (also tags requires-orthogonal-flow)
    inlineBr:           /<br\b/i,
    unicodeBidi:        /\bunicode-bidi\s*:/i,
    bidiOverride:       /\bbidi-override\b/i,
    dirAttr:            /\bdir=["']?(?:rtl|ltr|auto)\b/i,
    inlineAbsposSpan:   /<span\b[^>]*\bstyle=["'][^"']*\bposition\s*:\s*absolute/i,
    hypotheticalKeyword:/hypothetical[- ]?(?:inline|static)/i,

    // Rule 3 — requires-animation-runtime:
    //   `@keyframes` declarations or `animation: name …` shorthand that
    //   depends on UA executing the keyframe timeline before screenshot.
    //   The IR captures animation config, but the SDUI runtimes do not
    //   execute the animation between extraction and capture.
    //   Source: investigations/swarm-001/css-backgrounds__background-color-animation-with-table1.json
    //           investigations/swarm-001/css-view-transitions__animating-new-content-subset.json
    keyframes:          /@keyframes\b/i,
    animationProp:      /\banimation(?:-name)?\s*:\s*[a-zA-Z_-]/i,
    //   wave-35 B7 — the WEB ANIMATIONS API half of the same wall. The two
    //   regexes above only see CSS-declared animations; a test that builds its
    //   timeline in script (`el.animate(…)`, `new Animation(…)`,
    //   `new KeyframeEffect(…)`, `document.getAnimations()`) needs the very
    //   same UA runtime and is just as unreachable for a static extractor,
    //   yet carried no animation tag at all.
    //   MEASURED PRECISION (whole corpus, the Rule 42/43 device — all 46,937
    //   `tools/wpt/css/**/*.{html,xht}` documents):
    //     - 340 documents carry a Web-Animations signal;
    //     - 151 of those carry NO CSS `@keyframes`/`animation:` at all, i.e.
    //       Rule 3 missed every one of them before this wave;
    //     - 151/151 contain a real `<script>` element, so not one hit is a
    //       stray mention in prose, a comment, or an attribute value;
    //     - 10 of the 151 are bucket-A (the only ones whose tags ever surface
    //       in a run): css-forms 2, css-pseudo 2, css-text 4,
    //       css-view-transitions 2. All ten were hand-read and all ten drive
    //       rendering through the timeline — e.g.
    //       css-pseudo/backdrop-animate-002.html animates `::backdrop` with
    //       `target.animate({opacity:[0.1,0.1], …}, {duration: Infinity})`,
    //       and css-text/letter-spacing/letter-spacing-animating-letter-spacing.html
    //       drives `target.animate({letterSpacing:['0px','40px']}, 40)` then
    //       pauses it at a set time. Two of the ten
    //       (letter-spacing-animating-letter-spacing, word-spacing-animating-
    //       word-spacing) previously carried ZERO architectural tags — they
    //       were presented as fully applicable while being unreachable.
    //   Deliberately NOT matched: the SVG `<animate>` ELEMENT (no `.animate(`
    //   call shape), and `animation` CSS declarations (already covered above).
    //   Bucketing is untouched: notApplicable is an axis orthogonal to A/B/C
    //   (bucket-wpt.mjs's own note), so this adds a label, never a demotion.
    waapiTimeline:      /(?:\.\s*animate\s*\(|new\s+(?:Animation|KeyframeEffect)\s*\(|\bgetAnimations\s*\(\s*\))/,

    // Rule 4 — requires-script-mutation:
    //   Inline `<script>` that runs after load and mutates the DOM
    //   (insertBefore, appendChild, document.body.offsetHeight to force
    //   layout, etc.). The static extractor captures the pre-script DOM,
    //   so the comparison ref's post-script state is unreachable.
    //   Heuristic: a `<script>` tag containing one of the canonical DOM-
    //   mutating call patterns. We deliberately exclude `<script src=…>`
    //   external loads (those go through the bucket-C remote-resource
    //   rule already in bucket-wpt.mjs) and benign `requestAnimationFrame`
    //   one-shots that just call `document.documentElement.classList.remove(
    //   'reftest-wait')` (those are already bucket-C via reftest-wait).
    //
    //   Widened by swarm-002 (css-multicol__multicol-clip-scrolled-content-001
    //   and css-pseudo__before-dynamic-display-none) to also match
    //   PROPERTY-ASSIGNMENT mutation patterns (`.className = '…'`,
    //   `.innerHTML = …`, `.style.<prop> = …`, `.scrollTop = N`,
    //   `.id = '…'`, `.classList.add(…)`). The previous regex only matched
    //   `.appendChild(`-style method calls; the L5 swarm investigations
    //   showed dozens of tests that set className or scrollTop/scrollLeft
    //   from script and escaped the heuristic.
    //
    //   F-G-TAGS-2 widening (swarm-003): adds `.remove(` for bare-node
    //   removal and `.replaceWith(` so the css-overflow / css-flexbox
    //   "remove this child" pattern (and the css-pseudo "replace this
    //   element" pattern) are caught. `.replaceWith(` was already covered
    //   by the swarm-002 regex; `.remove(` is the genuinely new pattern.
    //   Without this, dozens of bucket-A tests that mutate the DOM via
    //   the bare-node `child.remove()` call escape the heuristic.
    //
    //   wave-15 TOP-LAYER widening: adds the top-layer promotion APIs
    //   `.showPopover(` / `.hidePopover(` / `.togglePopover(` (HTML
    //   §popover), `.showModal(` (<dialog>), `.requestFullscreen(`
    //   (Fullscreen API). These calls do not mutate child nodes but they
    //   DO mutate the rendered tree — the element is promoted into (or
    //   removed from) the browser's top layer, and `::backdrop` boxes
    //   appear/disappear as a side effect. The static extractor captures
    //   the pre-script DOM, so the entire visual output of such a test is
    //   unreachable exactly like an appendChild mutation. Measured
    //   wave-15: css-position/overlay/overlay-transition-backdrop.html
    //   (showPopover()+hidePopover() drive a green ::backdrop; the ref is
    //   solid green) carried notApplicableTags=[] and its blank-vs-green
    //   diff was SCORED at 0.54 — the css-position lane's worst row — as
    //   if it were renderer divergence.
    //   Source: investigations/swarm-001/css-lists__add-inline-child-after-marker-001.json
    //           investigations/swarm-001/css-position__absolute-pos-box-inside-fixed-pos-box-with-changing-height.json
    //           investigations/swarm-002/css-multicol__multicol-clip-scrolled-content-001.json (scrollTop=N)
    //           investigations/swarm-002/css-pseudo__before-dynamic-display-none.json (className mutation)
    //           investigations/swarm-003/css-overflow__block-ellipsis-001.json (.remove(), .replaceWith())
    //           investigations/swarm-003/css-flexbox__anonymous-flex-item-001.json (.remove())
    //           wave-15 css-position lane finding (overlay-transition-backdrop, top-layer APIs)
    scriptDomMutation:  /<script\b(?![^>]*\bsrc=)[^>]*>[\s\S]*?(?:\.appendChild\(|\.insertBefore\(|\.replaceChild\(|\.removeChild\(|\.replaceWith\(|\.insertAdjacentHTML\(|\.innerHTML\s*=|\.outerHTML\s*=|\.setAttribute\(|\.append\(|\.prepend\(|document\.write\(|\.createElement\(|\.className\s*=|\.textContent\s*=|\.id\s*=\s*["']|\.classList\.[a-z]+\s*\(|\.style\.[A-Za-z]+\s*=|\.scrollTop\s*=|\.scrollLeft\s*=|\.remove\s*\(\s*\)|\.(?:showPopover|hidePopover|togglePopover|showModal|requestFullscreen)\s*\()/i,

    // Rule 5 — requires-print-medium:
    //   `*-print.html` filename suffix, `@media print` rules, or `@page`
    //   declarations. The SDUI pipeline targets screen rendering only;
    //   page-box layout / print-color-adjust / page breaks have no analogue.
    //   Heuristic: filename ends `-print.html`, OR source contains
    //   `@media print`, OR source contains `@page` (with optional selector).
    //   We DON'T match `@media not print` here because that's "render this
    //   on screen, suppress on print" — irrelevant to our screen-mode capture.
    //   The negative-lookahead trick `(?<!not\s+)` would do this elegantly
    //   but lookbehind isn't supported on every JS runtime we target; we
    //   use a two-step check in code below: match the @media-print regex
    //   THEN explicitly reject if the matched substring is preceded by
    //   `not` (handled in the rule's test() function).
    //   Source: investigations/swarm-001/css-page__background-image-only-for-print.json
    //           investigations/swarm-001/css-multicol__auto-fill-auto-size-001-print.json
    //           investigations/swarm-001/css-fonts__downloadable-font-print.json
    printFilename:      /-print\.html$/i,
    mediaPrint:         /@media\s+([^{]*?)\bprint\b/gi,
    atPage:             /@page\b/i,

    // Rule 6 — requires-table-layout:
    //   Native `<table>`/`<thead>`/`<tbody>`/`<tr>`/`<td>`/`<th>` elements
    //   OR `display: table`/`table-row`/`table-cell`/`table-caption`/etc.
    //   RE-TIERED 2026-09-04 (retro A9#8): the IR had no table-layout fixup
    //   pass when swarm-001 wrote this; the runtimes have one now
    //   (runtimes/*/table/ on all three — the box tree landed wave 32 on
    //   compose and wave 34 on swiftui, with separated/collapsed track
    //   sizing). 182 of 255 tagged cells PASS at wave 49, so the tag marks a
    //   TIER (residual CSS 2.1 §17 fixup), not an absence.
    //   Source: investigations/swarm-001/css-backgrounds__background-color-animation-with-table1.json
    //           investigations/swarm-001/css-tables__anonymous-table-ws-001.json
    tableTag:           /<(?:table|thead|tbody|tfoot|tr|td|th|caption|colgroup|col)\b/i,
    displayTable:       /\bdisplay\s*:\s*(?:inline-)?table(?:-(?:row|cell|caption|column|column-group|row-group|header-group|footer-group))?\b/i,

    // Rule 7 — requires-form-control-rendering:
    //   Native form controls AND native UA-widget elements: `<input>`,
    //   `<select>`, `<textarea>`, `<button>`, `<progress>`, `<meter>`,
    //   `<output>`, plus `<details>`, `<summary>`, `<li>`, `<ul>`, `<ol>`,
    //   `<hr>`, `<dialog>`, `<marquee>`. Their visual rendering is driven
    //   by the UA stylesheet + native widget code, neither of which the
    //   SDUI runtimes simulate (no disclosure triangle on <details>, no
    //   list-marker glyph on <li>, no UA HR rule, etc.).
    //
    //   Widened by swarm-002 (css-ui__appearance-auto-details-list-item)
    //   to include the disclosure / list / hr / dialog families — these
    //   exhibit identical UA-widget-rendering failure modes to the
    //   form-control set but were escaping the regex because the original
    //   swarm-001 corpus only sampled <input>-family tests.
    //   Source: investigations/swarm-001/css-ui__accent-color-parent-currentcolor.json
    //           investigations/swarm-002/css-ui__appearance-auto-details-list-item.json
    formControlTag:     /<(?:input|select|textarea|button|progress|meter|output|fieldset|legend|datalist|optgroup|option|details|summary|li|ul|ol|hr|dialog|marquee)\b/i,

    // Rule 8 — requires-runtime-selection:
    //   `::selection`, `::target-text`, `::highlight()`, `::spelling-error`,
    //   `::grammar-error` pseudo-elements, OR programmatic Selection/Range
    //   APIs (`window.getSelection()`, `document.createRange()`,
    //   `Selection.addRange`, `getSelection().selectAllChildren`). These all
    //   require live UA Selection state that the static pipeline doesn't
    //   carry.
    //   Source: investigations/swarm-001/css-pseudo__active-selection-056.json
    selectionPseudo:    /::(?:selection|target-text|highlight\(|spelling-error|grammar-error)\b/i,
    // Note: no \b around the call patterns — `\b` requires a word/non-word
    // transition, and `)` is non-word followed by `.`/`(` which are also
    // non-word, so `\b` after `\)` would never match. The function-call
    // syntax itself is the boundary.
    selectionApi:       /(?:getSelection\(\)|createRange\(\)|Selection\.addRange|selectAllChildren\()/,

    // Rule 9 — requires-shadow-dom:
    //   Declarative shadow roots (`<template shadowrootmode>`), shadow-DOM-
    //   only selectors (`::slotted(`, `:host`, `:host-context`, `::part(`),
    //   or imperative attachShadow calls. The IR has no shadow tree.
    //   Source: investigations/swarm-001/css-values__attr-in-slotted.json
    declarativeShadow:  /<template\b[^>]*\bshadowrootmode\b/i,
    slottedPseudo:      /::slotted\s*\(/i,
    hostPseudo:         /:host(?:-context)?\b/i,
    partPseudo:         /::part\s*\(/i,
    attachShadow:       /\.attachShadow\s*\(/,

    // Rule 10 — requires-containing-block-layout:
    //   `position: fixed` / `position: absolute` with bottom/right offsets
    //   that need to be resolved against an ancestor's USED height — which
    //   only works when the IR carries the parent tree AND the engine
    //   computes used heights bottom-up.
    //   RE-TIERED 2026-09-04 (retro A9#8): "the current flat IR makes this
    //   impossible" is no longer true — IR v2's slot/placement channel
    //   carries the parent relation (schema/spec/03-children.md) and the
    //   natives resolve the block (ContainingBlock{,Basis}.swift,
    //   TransformContainingBlock.{kt,swift}). 289 of 345 tagged cells PASS
    //   at wave 49: a tier, not a wall.
    //   Heuristic: same rule as the source HTML containing `position:
    //   fixed|absolute` AND a `bottom:` or `right:` declaration. We deliberately
    //   require BOTH signals (not just position:absolute alone) to avoid
    //   over-matching simple top-left abspos that the engine handles fine.
    //   Source: investigations/swarm-001/css-position__absolute-pos-box-inside-fixed-pos-box-with-changing-height.json
    posFixedAbsolute:   /\bposition\s*:\s*(?:fixed|absolute)\b/i,
    bottomRightOffset:  /\b(?:bottom|right)\s*:\s*(?:0|-?\d+(?:\.\d+)?(?:px|em|rem|%|vh|vw))\b/i,

    // Rule 11 — requires-orthogonal-flow:
    //   `writing-mode: vertical-*` or `writing-mode: sideways-*` combined
    //   with descendants that have a different flow direction. The residual
    //   class is the "nearest ancestor scroller" max-height resolution of
    //   css-writing-modes-4 §7.3 (Orthogonal Flows).
    //   RE-TIERED 2026-09-04 (retro A9#8): the flat-IR premise this banner
    //   used to give ("the IR doesn't carry the parent tree needed") expired
    //   with IR v2's slot/placement channel; all three carry a WritingMode
    //   triplet and both natives run VerticalBlockFlowLayout.{kt,swift}
    //   (wave 47, #123). 221 of 315 tagged cells PASS at wave 49 (70% — the
    //   weakest of the eight re-tiered tags, so this one still has the most
    //   work behind it).
    //   Heuristic: writing-mode keyword in source — we accept any vertical-*
    //   /sideways-* OR an explicit `text-orientation` setting.
    //   Source: investigations/swarm-001/css-writing-modes__available-size-001.json
    verticalWritingMode:/\bwriting-mode\s*:\s*(?:vertical-rl|vertical-lr|sideways-rl|sideways-lr)\b/i,
    textOrientation:    /\btext-orientation\s*:\s*(?:upright|sideways|mixed)\b/i,

    // Rule 12 — requires-visited-pseudo:
    //   `:visited` selector or `:link`+`:visited` pair. Privacy-restricted
    //   in real browsers; not modelable without a history database.
    //   Source: investigations/swarm-001/selectors__caret-color-visited-inheritance.json
    visitedPseudo:      /:visited\b/i,

    // Rule 13 — requires-caret-rendering:
    //   `caret-color` property (only meaningful with editable text), OR
    //   `cursor: text` on something that should display a caret. The SDUI
    //   runtimes don't render text carets in screenshots.
    //   Source: investigations/swarm-001/selectors__caret-color-visited-inheritance.json
    caretColor:         /\bcaret-color\s*:/i,

    // Rule 14 — requires-contenteditable:
    //   `contenteditable` attribute (any value). Editable regions need a
    //   focus/caret-aware text editor on each platform; out of scope.
    //   Source: investigations/swarm-001/selectors__caret-color-visited-inheritance.json
    contenteditable:    /\bcontenteditable(?:=["']?(?:true|""|plaintext-only)["']?)?\b/i,

    // Rule 15 — requires-font-face:
    //   `@font-face { src: url(…) }` referencing a font asset.
    //   NOT the rule's predicate any more — wave-34 lane F2 replaced the
    //   whole-document token scan with declaresFontFaceRule() below, which
    //   requires an actual at-RULE (`@font-face` followed by `{`) rather
    //   than the word. Kept here as the table's documentation of the token,
    //   and pinned by the RX-shape unit test; the rule body no longer calls
    //   it. The 9 documents that mention the at-rule only in a <title>, an
    //   assertion string or a commented-out JS line used to be excluded by
    //   this pattern for a face they never create.
    //   Source: investigations/swarm-001/css-fonts__downloadable-font-print.json
    fontFace:           /@font-face\b/i,

    // Rule 16 — requires-view-transitions:
    //   `document.startViewTransition()` API call OR `::view-transition*`
    //   pseudo-element selectors OR `view-transition-name` property. The
    //   View Transitions API is a coordinated cross-frame DOM-snapshot/
    //   compositing dance the static pipeline can't simulate.
    //   Source: investigations/swarm-001/css-view-transitions__animating-new-content-subset.json
    startViewTransition:/\.startViewTransition\s*\(/,
    viewTransitionPseudo:/::view-transition(?:-(?:group|image-pair|new|old))?\b/i,
    viewTransitionName: /\bview-transition-name\s*:/i,

    // ── swarm-002 additions (Rules 18..29) ──────────────────────────────────
    //
    // The first FIX-D round (Rules 1..17) was seeded by swarm-001 (29 reports
    // over Phase-2 swarm-2b-w2). swarm-002 re-investigated 26 follow-up tests
    // after FIX-A/A2/B/C/D/E landed and surfaced 12 NEW architectural-exclusion
    // patterns that the original rules missed. Each rule below cites the
    // swarm-002 investigation JSON that surfaced it.
    //
    // Design note: the swarm-002 reports each include a `fixProposal.details`
    // block with EXACT regex recommendations. The regexes below mirror those
    // recommendations and intentionally match nothing more — we want this
    // batch to be additive and low-risk on the existing wpt-buckets.json
    // shape.

    // Rule 18 — requires-script-driven-scroll:
    //   `.scrollTop = N`, `.scrollLeft = N`, `.scrollTo(`, `.scrollBy(`
    //   (script-driven scroll mutation). Distinct from Rule 4
    //   (requires-script-mutation) because the swarm-002 attachment-
    //   local-positioning test depends specifically on POST-script scroll
    //   offsets being baked into the comparison ref — even if the static
    //   pipeline tolerated DOM mutation in general, it has no scroll-
    //   position model. Rule 4 was widened to also fire here, but this
    //   narrower tag is more actionable for the dashboard.
    //   Source: investigations/swarm-002/css-backgrounds__attachment-local-positioning-2.json
    //           investigations/swarm-002/css-multicol__multicol-clip-scrolled-content-001.json
    scrollDriven:       /\.(?:scrollTop|scrollLeft)\s*=|\.(?:scrollTo|scrollBy|scrollIntoView)\s*\(/,

    // Rule 19 — requires-background-attachment-local-runtime:
    //   `background-attachment: local` requires a runtime scroll-tracking
    //   pipeline to paint the background relative to the scrolled origin
    //   (the painted bg moves with overflow content). No SDUI applier
    //   implements this; the extractor captures the keyword but no
    //   renderer honours it.
    //   Source: investigations/swarm-002/css-backgrounds__attachment-local-positioning-2.json
    bgAttachmentLocal:  /\bbackground-attachment\s*:\s*[^;{}]*\blocal\b|\bbackground\s*:[^;{}]*\blocal\b/i,

    // Rule 20 — requires-bundled-asset:
    //   References to RELATIVE `support/<...>.<png|jpg|jpeg|gif|webp|svg|
    //   bmp|woff|woff2|ttf|otf|mp4|webm|css|js|json|xml>` assets via
    //   `<embed src=`, `<object data=`, `<img src=`, `<link href=`, or
    //   `url(...)`. These live in the WPT support tree and aren't bundled
    //   into the SDUI per-section public dirs (Android assets, iOS
    //   resources, web public/), so any test that references them will
    //   render as a missing-image / 404 placeholder.
    //
    //   F-G-TAGS-2 widening (swarm-003): also fire on ABSOLUTE-path
    //   references that embed WPT sub-template tokens like
    //   `{{hosts[*][*]}}/...` or `{{ports[http][0]}}/...` followed by a
    //   `support/` (or `/<asset>.png`) component. These appear in
    //   `.sub.html` server-side substitution files (the WPT runner
    //   rewrites `{{hosts[]['ws']}}` → an actual host:port at serve time)
    //   and are equally unreachable from the SDUI harness, which has no
    //   token substitution AND no bundled support tree. Estimated
    //   ~100-150 more `.sub.html` tests caught by this widening.
    //
    //   wave-8 NOTE — this rule OVER-fires for one class of test:
    //   extract-fixture.mjs inlines raster support assets < 8 KB as
    //   percent-encoded data URIs (see its "Support-asset inlining"
    //   section), so url()-referenced small rasters like cat.png (1,883 B)
    //   ARE deliverable and no longer an architectural exclusion. This
    //   string-grep rule cannot stat file sizes (RULES are pure, no IO by
    //   design — classifyAll must stay a pure function), so the tag was
    //   hand-removed from the three css-break background-image-000/001/002
    //   tests (+ refs) in wpt-buckets.json after their fixtures were
    //   re-extracted with inlined assets.
    //   wave-13 RESOLUTION — the size-aware post-pass the wave-8 TODO
    //   tracked now exists, in the layer where the delivery truth already
    //   lives: inject-wpt-block.mjs's applyNaScoreGate is DELIVERY-AWARE —
    //   it only score-excludes on this tag when the extractor's own
    //   lossyReasons (stamped by inlineFixtureAssets when an asset is
    //   genuinely missing / ≥ 8 KB / non-raster) corroborate it. So this
    //   rule stays pure (no IO), the tag remains an honest STATIC hint in
    //   wpt-buckets.json, and regenerating the buckets no longer silently
    //   un-scores tests whose assets the inliner delivers (measured wave-13:
    //   css-backgrounds background-color-animation-with-images /
    //   background-334 / background-attachment-350, assets 218–961 B, were
    //   stale-excluded by the raw tag; scoring denominator moved 9 → 12).
    //   Source: investigations/swarm-002/css-backgrounds__attachment-local-positioning-2.json
    //           investigations/swarm-002/css-images__image-orientation-background-position.json
    //           investigations/swarm-003/css-images__image-orientation-none-cross-origin-border-image.json
    bundledSupportAsset:/(?:<(?:embed|object|img|link|source|video|audio|track|iframe)\b[^>]*\b(?:src|data|href)=["']support\/|\burl\(\s*["']?support\/|\burl\(\s*["']?(?:\.\.\/)+support\/|<(?:embed|object|img|link|source|video|audio|track|iframe)\b[^>]*\b(?:src|data|href)=["'][^"']*\{\{(?:hosts|ports|domains)\b[^"']*\}\}[^"']*\/support\/|\burl\(\s*["']?[^)]*\{\{(?:hosts|ports|domains)\b[^)]*\}\}[^)]*\/support\/)/i,

    // Rule 21 — requires-fragmentation:
    //   Multi-column / page-break / region-fragment properties that need a
    //   real fragmentation engine.
    //   RE-TIERED 2026-09-04 (retro A9#8): swarm-002 wrote this when CLAUDE.md
    //   marked multicol "config extraction only". A real column pass exists
    //   now — runtimes/*/columns/ is 28 compose / 27 swiftui / 46 web source
    //   files, and the FRAGMENTATION half is campaign work: MulticolFloatStrip*
    //   (wave 44, #119), MulticolClone*/FragmentGeometry (wave 46, #122),
    //   MulticolDescendantSpanner beside them. 341 of 429 tagged cells PASS at
    //   wave 49. The residual class is css-break-3 §3 break controls, not the
    //   engine's absence.
    //   Heuristic fires when ANY of:
    //     - `column-fill|column-count|column-width|columns:` in the source
    //     - `break-(before|after|inside): <non-auto>`
    //     - `page-break-*: <non-auto>`
    //     - `region-fragment:`
    //   Source: investigations/swarm-002/css-break__block-max-height-004.json
    //           investigations/swarm-002/css-multicol__multicol-clip-scrolled-content-001.json
    columnFragmentation:/\b(?:column-fill|column-count|column-width|columns)\s*:/i,
    breakNonAuto:       /\bbreak-(?:before|after|inside)\s*:\s*(?!auto\b)[a-zA-Z-]+/i,
    pageBreakNonAuto:   /\bpage-break-(?:before|after|inside)\s*:\s*(?!auto\b)[a-zA-Z-]+/i,
    regionFragment:     /\bregion-fragment\s*:/i,

    // Rule 22 — requires-containment:
    //   `contain:` with any non-`none` value (layout, paint, style, size,
    //   inline-size, strict, content, or any combination). Web has the
    //   dedicated Contain triplet (runtimes/web/src/engine/performance/
    //   Contain*); the natives REGISTER the property and deliberately no-op
    //   it (PerformanceRegistration.kt: "contain / content-visibility:
    //   Compose auto-composes … No-op"; the PerformanceApplier containment
    //   branch is legacy and the renderer never chains it, as its own comment
    //   says). css-contain-2 §2 defines the property.
    //   RE-TIERED 2026-09-04 (retro A9#8): unlike the other seven re-tiered
    //   tags this one's ABSENCE claim still holds on the natives — what was
    //   wrong is the implied consequence. MEASURED: 164 of 204 tagged cells
    //   PASS at wave 49 (80%), because the rule fires on ANY `contain:`
    //   declaration and containment rarely changes what a single per-card
    //   component paints. So the tag marks a TIER of the TEST SET, not a
    //   wall: do not read it as "these cells are lost".
    //   Source: investigations/swarm-002/css-contain__contain-body-overflow-001.json
    containNonNone:     /\bcontain\s*:\s*(?!none\b)(?:layout|paint|style|size|inline-size|strict|content|\s)+(?:[;{}]|$)/i,

    // Rule 23 — requires-document-tree:
    //   Tests that depend on cascade/inheritance walking the DOM tree —
    //   typically the css/CSS2/cascade/* family. The IR is a flat map of
    //   per-component property bags with no parent/child cascade engine,
    //   so any test that uses `inherit` / `unset` / `revert` / `revert-layer`
    //   as the SPECIFIED value of a property cannot resolve the keyword
    //   against the parent's computed value (because there is no parent).
    //   This is the css-cascade family's canonical failure mode.
    //   Source: investigations/swarm-002/CSS2__inherit-computed-001.json
    cssWideKeyword:     /\b(?:[\w-]+)\s*:\s*(?:inherit|unset|revert|revert-layer)\b/i,

    // Rule 24 — requires-viewport-canvas:
    //   Reftests that depend on a single composed viewport snapshot
    //   (body→ICB propagation, sibling-margins between top-level boxes,
    //   negative-margin shift off-viewport, etc.) rather than per-card
    //   composition. The SDUI capture model uses one canvas per top-level
    //   IR component (390px wide, auto-sized vertically); inter-card
    //   spatial relationships (margins, offsets, abspos against ICB) are
    //   not modeled.
    //   Heuristic: the test matches `css/reference/blank.html` AND has
    //   visible IR content (i.e. the pass criterion is a NEGATIVE
    //   assertion — "render nothing"), OR the test depends on body /
    //   html scope rules that propagate to the viewport (covered by
    //   wm-propagation / overflow-propagation patterns).
    //   We use a conservative two-signal proxy: (a) `position: absolute`
    //   or `position: fixed` combined with a negative margin/offset
    //   value (e.g. `-20em`, `-100vh`, `-9999px`), OR (b) `body { …
    //   overflow }` / `html { … overflow }` style declarations that the
    //   spec says propagate to the viewport.
    //   Source: investigations/swarm-002/css-flexbox__flexbox_inline-abspos.json
    //           investigations/swarm-002/css-overflow__clip-002.json
    //           investigations/swarm-002/css-contain__contain-body-overflow-001.json
    negativeOffshift:   /\b(?:margin-(?:top|left|right|bottom)|top|left|right|bottom)\s*:\s*-\s*\d+(?:\.\d+)?(?:px|em|rem|%|vh|vw|ex|ch|cm|mm|in|pt|pc)/i,
    bodyHtmlOverflow:   /\b(?:body|html)\b[^{]*\{[^}]*\boverflow(?:-[xy])?\s*:/i,

    // Rule 25 — requires-gap-decorations:
    //   CSS Gap Decorations Level 1 — `column-rule-*` (or upcoming
    //   row-rule-* / gap-rule shorthand) on `display: flex | grid |
    //   inline-flex | inline-grid` containers. Chromium itself only paints
    //   these on multicol without the GapDecorations blink feature flag.
    //   RE-TIERED 2026-09-04 (retro A9#8): "None of the three SDUI style
    //   engines implement the flex/grid integration" was refuted by the
    //   wave-24/25 gap lanes (compose GapDecorationPainter.kt landed in #89,
    //   swiftui GapDecorationsPainter.swift in #90) — the painters now sit in
    //   compose/swiftui columns/ beside web's ColumnRule*/RowRule* triplets
    //   in engine/columns/. 126 of 138 tagged cells PASS at wave 49 (91%, the
    //   strongest of the eight re-tiered tags).
    //   Heuristic: source contains `display: flex | grid` AND
    //   `column-rule-` (style/width/color) on the same rule or sibling
    //   rules. Conservatively fire if BOTH signals are present anywhere
    //   in the file.
    //   Source: investigations/swarm-002/css-gaps__flex-gap-decorations-007.json
    displayFlexGrid:    /\bdisplay\s*:\s*(?:inline-)?(?:flex|grid)\b/i,
    columnRuleAny:      /\bcolumn-rule(?:-style|-width|-color)?\s*:/i,
    rowRuleAny:         /\brow-rule(?:-style|-width|-color)?\s*:/i,

    // Rule 26 — requires-3d-rendering-context-tree:
    //   `transform-style: preserve-3d` (or any 3D-transform function:
    //   rotate3d, rotateX/Y, perspective, translateZ, translate3d,
    //   matrix3d) co-occurring with `display: inline` (or an
    //   intermediate flat-context block descendant). The spec rule under
    //   test is "blocks inside of inlines participate in 3D Rendering
    //   Contexts based on their parent, not their containing block" —
    //   evaluating it requires walking the parent chain, which the flat
    //   IR + per-card capture surface cannot do consistently against the
    //   browser-ref viewport snapshot.
    //   Source: investigations/swarm-002/css-transforms__3d-rendering-context-and-inline.json
    transformStyle3d:   /\btransform-style\s*:\s*preserve-3d\b/i,
    transformFn3d:      /\b(?:rotate3d|rotateX|rotateY|perspective|translateZ|translate3d|matrix3d)\s*\(/i,
    displayInline:      /\bdisplay\s*:\s*inline\b(?!-)/i,

    // Rule 27 — requires-multicol-fragmentation:
    //   Targeted subset of Rule 21: tests under `css/css-multicol/` that
    //   exercise column-* layout (column-width, column-count, columns,
    //   column-gap, column-rule, column-span, column-fill) and depend on
    //   column-box geometry rather than pure column-* property
    //   serialization. Pair with the existing requires-print-medium rule
    //   which already covers multicol-print.
    //   Note: this rule fires in ADDITION to requires-fragmentation when
    //   both apply, so the dashboard can split the multicol-specific
    //   subset cleanly from the broader fragmentation family.
    //   Source: investigations/swarm-002/css-multicol__multicol-clip-scrolled-content-001.json
    multicolPath:       /(?:^|\/)css-multicol\//i,
    multicolProp:       /\bcolumn-(?:width|count|gap|rule|span|fill|rule-style|rule-width|rule-color)\b/i,

    // Rule 28 — requires-nested-overflow-clip:
    //   Three or more `overflow: scroll | auto | hidden | clip` (or
    //   `overflow-x:` / `overflow-y:` variants) declared in a single
    //   nesting chain. The SDUI surface flattens each component into an
    //   isolated card with no parent/child clip composition, so the
    //   CSS-Overflow §6.1 clip-rect composition algorithm has nothing
    //   to walk.
    //   Heuristic: count distinct `overflow(-x|-y)?: scroll|auto|hidden
    //   |clip` declarations anywhere in <style>; if ≥3, fire.
    //   Source: investigations/swarm-002/css-multicol__multicol-clip-scrolled-content-001.json
    overflowClipAny:    /\boverflow(?:-x|-y)?\s*:\s*(?:scroll|auto|hidden|clip)\b/gi,

    // Rule 29 — requires-attr-function:
    //   `attr(...)` functional notation anywhere in source CSS. attr() is
    //   a runtime substitution function that needs the live DOM element +
    //   the named HTML attribute + a string→typed-value cast at style-
    //   resolution time. Style-Converter's IR is fully static; the
    //   ContentValue.Attr variant exists but is single-arg with no
    //   fallback channel and no DOM bridge. Three orthogonal architectural
    //   gaps stack here: attr() runtime, ::before/::after generated content,
    //   HTML attribute preservation in the extractor.
    //   Source: investigations/swarm-002/css-values__attr-notype-fallback.json
    //           investigations/swarm-001/css-values__attr-in-slotted.json
    attrFunction:       /\battr\s*\(/i,

    // ── swarm-003 additions (Rules 30..40) ──────────────────────────────────
    //
    // The third FIX-D round (Rules 30..40) was seeded by swarm-003 (21 reports
    // re-investigating tests that survived swarm-001/002 fixes but remain
    // structural-divergence). Each rule below cites the swarm-003 investigation
    // JSON that surfaced it, plus the architectural-gap class it represents.

    // Rule 30 — requires-shared-inline-FC:
    //   Sibling-div inline-FC geometry assertions. The harness renders each
    //   IR component as an INDEPENDENT card with its own card-chrome (padding,
    //   background, rounded border) and STITCHES per-component PNGs vertically
    //   before diffing against browser-ref. The WPT assertion pattern "the up
    //   and down arrows below are aligned" / "the two lines are equal-width"
    //   is a CROSS-ELEMENT inline-formatting-context claim that depends on the
    //   two sibling `<div>`s sharing a parent's content-box left edge in the
    //   browser. Once the harness explodes them into two cards, each card has
    //   its own inner padding plus the card's border radius — adjacent
    //   typography glyphs end up at different x-coordinates in the stitched
    //   composite regardless of which engine renders them.
    //
    //   Heuristic: source contains 2+ sibling `<div>` (or `<p>`) elements AND
    //   any of the inline-typography properties from the css-text-3 inline-FC
    //   primitive set (white-space, word-break, hyphens, letter-spacing,
    //   line-break, word-spacing, text-align, text-indent, hanging-punctuation,
    //   tab-size, text-justify, overflow-wrap, word-space-transform,
    //   text-transform, text-autospace). Conservatively requires BOTH signals.
    //   Source: investigations/swarm-003/css-text__hanging-punctuation-first-002.json
    //           (Track-1 fix proposal — ~140 css-text tests covered)
    inlineFcTypographyProp: /\b(?:white-space|word-break|hyphens|letter-spacing|line-break|word-spacing|text-align|text-indent|hanging-punctuation|tab-size|text-justify|overflow-wrap|word-space-transform|text-transform|text-autospace)\s*:/i,
    siblingBlockTagOpen:    /<(?:div|p)\b/gi,

    // Rule 31 — requires-float-layout:
    //   Any non-trivial `float: left|right|inline-start|inline-end` (CSS 2.1
    //   §9.5). Note `float: none` is the default and shouldn't trigger — we
    //   explicitly exclude `none`.
    //   RE-TIERED 2026-09-04 (retro A9#8): swarm-003 wrote "not implemented on
    //   any of the three runtime renderers" in wave ~10; wave 19 (#83) landed
    //   FloatRowLayout.kt / FloatRowPacking.swift on both natives beside web's
    //   pre-existing engine/layout/Float* triplet, and waves 44/46 added the
    //   multicol float strip (MulticolFloatStrip*). 9 compose / 8 swiftui / 4
    //   web source files today, and 239 of 282 tagged cells PASS at wave 49.
    //   The residual class is the clear/<br> wall (BACKLOG #3).
    //   Source: investigations/swarm-003/CSS2__float-nowrap-5.json
    floatNonTrivial:    /\bfloat\s*:\s*(?:left|right|inline-start|inline-end)\b/i,

    // Rule 32 — requires-anonymous-box-generation:
    //   `display: flex|grid|table*|ruby*|inline-flex|inline-grid` parents with
    //   bare text-node children (not all element-wrapped). The CSS 2.1 §9.2.1.1
    //   anonymous block box generation algorithm wraps bare-text-node children
    //   of flex / grid / table containers in implicit anonymous flex / grid /
    //   table items. The IR has no anonymous-box pass — bare text inside a
    //   flex/grid parent gets lost or attached to the wrong component.
    //
    //   Hard to perfectly grep for without a DOM parser. Positive proxy:
    //   inline-style display:flex|grid|table on the OPEN tag of an element
    //   whose contents include text directly between tag boundaries (i.e.
    //   `<div style="display:flex">text<span>` or `<div style="display:grid">
    //   text</div>`). We use a non-greedy match that requires text characters
    //   (not just whitespace) appearing between the > and the next <.
    //   Source: investigations/swarm-003/css-flexbox__anonymous-flex-item-001.json
    //   The regex is intentionally conservative — it only catches the inline-
    //   style case. Stylesheet selectors that bind display:flex to an ID/class
    //   require a DOM walk to correlate the selector with the element's
    //   children, which the string-grep budget can't afford.
    anonymousFlexItem:  /<[a-z][a-z0-9]*\b[^>]*\bstyle=["'][^"']*\bdisplay\s*:\s*(?:inline-)?(?:flex|grid|table(?:-(?:row|cell|caption|column|column-group|row-group|header-group|footer-group))?|ruby(?:-(?:base|text|base-container|text-container))?)[^"']*["'][^>]*>[^<>]*?[\p{L}\p{N}][^<>]*<(?!\/)/iu,

    // Rule 33 — requires-pseudo-element-rendering:
    //   Selectors that target generated content or per-element pseudo-element
    //   boxes: ::before, ::after (covered by extractor work but tests that
    //   *depend* on the pseudo's box geometry still fail), ::marker (list /
    //   counter glyph), ::first-line, ::first-letter (first-formatted-line
    //   selectors that need an inline-FC pass), ::selection (live UA selection
    //   state, also covered by Rule 8), ::placeholder (native form-control
    //   widget pseudo). Each is a separate architectural gap; this single tag
    //   is a fallback for the rendered-state-of-the-pseudo tests that survive
    //   even after the extractor learns to surface the pseudo's properties.
    //   Source: investigations/swarm-003/css-pseudo__before-preceding-whitespace-dynamic.json
    //           investigations/swarm-003/css-text-decor__ruby-text-decoration-01.json
    //   Note: F-G-EXTRACTOR is concurrently adding extraction support for
    //   ::before, ::after, ::marker — so this rule fires as a fallback for
    //   the other pseudos plus tests that depend on pseudo BOX geometry
    //   NOT implemented even after extraction lands.
    pseudoElementSel:   /::(?:before|after|marker|first-line|first-letter|placeholder)\b/i,

    // Rule 34 — requires-bundled-font:
    //   Body text contains complex-script Unicode ranges that need bundled
    //   fonts not shipped with the SDUI test harness. The browser-ref renders
    //   the glyphs using OS-bundled or CDN-fetched system fonts (Noto family
    //   on Linux WPT bots), but the SDUI capture surface uses only the
    //   default sans-serif and falls back to "□" / ".notdef" tofu for any
    //   codepoint the default font doesn't cover. The mismatch produces
    //   structural-divergence regardless of which engine renders.
    //
    //   Heuristic: source text (between tag boundaries OR inside
    //   `content: "…"`) contains a codepoint in one of the complex-script
    //   Unicode blocks the default sans-serif fonts on macOS/iOS Simulator
    //   /Android Emulator do not cover: Tibetan U+0F00-U+0FFF, CJK
    //   U+3000-U+9FFF, Hiragana U+3040-U+309F, Katakana U+30A0-U+30FF,
    //   Hangul U+AC00-U+D7A3, Arabic U+0600-U+06FF, Hebrew U+0590-U+05FF,
    //   Devanagari U+0900-U+097F, Bengali U+0980-U+09FF, Tamil
    //   U+0B80-U+0BFF, Khmer U+1780-U+17FF, Lao U+0E80-U+0EFF, Myanmar
    //   U+1000-U+109F, Ethiopic U+1200-U+137F.
    //   Source: investigations/swarm-003/css-fonts__font-feature-settings-tibetan.json
    //           investigations/swarm-003/css-counter-styles__counter-cjk-decimal.json
    complexScriptCodepoint: /[֐-׿؀-ۿऀ-ॿঀ-৿஀-௿຀-໿ༀ-࿿က-႟ሀ-፿ក-៿　-鿿가-힣]/,

    // Rule 35 — requires-calc-size:
    //   `calc-size(` token in source CSS. css-values-5 §10 introduced
    //   calc-size() in 2024 — a function that lets length-percentage values
    //   interpolate to/from intrinsic keywords like `auto`, `min-content`,
    //   `max-content`. The LengthParser's allowlist of recognised function
    //   names does NOT include `calc-size(`, so every width/height
    //   declaration using calc-size() falls through to GenericProperty
    //   { _unmapped: true } and the renderer drops the constraint silently.
    //   Source: investigations/swarm-003/css-values__calc-size-aspect-ratio-001.json
    calcSizeFn:         /\bcalc-size\s*\(/i,

    // Rule 36 — requires-sub-template:
    //   `{{hosts[*][*]}}` / `{{ports[*][*]}}` / `{{domains[*]}}` WPT server-
    //   side substitution tokens. These appear in `.sub.html` files that the
    //   WPT runner pre-processes at serve time, substituting the tokens for
    //   actual host:port URLs based on the runner's wptserve config. The
    //   SDUI harness has no token substitution — the literal `{{hosts...}}`
    //   string flows into href / src attributes unchanged, producing 404
    //   resource loads.
    //
    //   Also fires when the filename suffix is `.sub.html` (the canonical
    //   WPT convention that REQUIRES the runner to do template substitution).
    //   Source: investigations/swarm-003/css-images__image-orientation-none-cross-origin-border-image.json
    subTemplateToken:   /\{\{(?:hosts|ports|domains)\b[^}]*\}\}/i,
    subFilenameSuffix:  /\.sub\.html$/i,

    // Rule 37 — requires-cross-origin:
    //   References to other origins. Matches the sub-template signal above
    //   (those tokens always resolve to cross-origin hosts) plus any
    //   `https?://` URL in a runtime-loading element (script / img /
    //   source / iframe / video / audio / object / embed). Cross-origin
    //   resources fail with CORS errors / mixed-content blocks / DNS
    //   failures in the SDUI harness (no network at capture time), so the
    //   test ref-comparison fails for reasons unrelated to rendering
    //   correctness.
    //
    //   Distinct from bucket-C's `remote resource (https?://) in runtime-
    //   loading href/src` rule (bucket-wpt.mjs) because that rule only
    //   demotes the bucket; this tag surfaces in notApplicable so the
    //   dashboard can split bucket-C remote-resource failures into
    //   "self-cdn / spec link / cross-origin asset" classes.
    //
    //   CRITICAL: we deliberately EXCLUDE plain `<link rel="author|help|
    //   reviewer|match|mismatch|manifest" href="https://…">` — these are
    //   pure metadata and have no runtime rendering effect. WPT tests
    //   routinely carry `<link rel="help" href="https://drafts.csswg.org/
    //   css-foo/...">` pointing at the spec; matching those would over-
    //   bucket the entire corpus (~27k tests). For <link> we only fire
    //   when the rel attribute is a runtime-loading kind (stylesheet,
    //   preload, prefetch, etc.) — mirrors bucket-wpt.mjs's narrow rule.
    //   For all other tags (script/img/source/iframe/etc.) any https:// in
    //   src/data fires the rule.
    //   Source: investigations/swarm-003/css-images__image-orientation-none-cross-origin-border-image.json
    crossOriginUrl:     /<(?:script|img|source|iframe|video|audio|object|embed)\b[^>]*\b(?:src|data)=["']https?:\/\/(?!self\b|same-origin\b)[^"']/i,
    crossOriginLink:    /<link\b[^>]*?\brel=["']?(?:stylesheet|preload|prefetch|icon|shortcut icon|preconnect|dns-prefetch|modulepreload)\b[\s\S]{0,200}?\bhref=["']https?:\/\/(?!self\b|same-origin\b)[^"']/i,
    crossOriginLinkAlt: /<link\b[^>]*?\bhref=["']https?:\/\/(?!self\b|same-origin\b)[\s\S]{0,200}?\brel=["']?(?:stylesheet|preload|prefetch|icon|shortcut icon|preconnect|dns-prefetch|modulepreload)\b/i,

    // Rule 38 — requires-viewport-sized-text-ref:
    //   Source has `line-clamp`, `max-lines`, `text-overflow: ellipsis`, or
    //   `block-ellipsis` AND the ref content is text-heavy. The ref would
    //   render as a viewport-sized scrollable text block — the SDUI per-
    //   component card surface can't model "render only the first N lines
    //   and ellipsize the rest" because each card auto-sizes to its content
    //   height (no fixed viewport, no max-block-size constraint inherited
    //   from a parent).
    //
    //   Heuristic: source contains line-clamp / max-lines / block-ellipsis
    //   OR `text-overflow: ellipsis`. The ref-content-heavy check is implicit
    //   — if the test declares these properties at all, the ref pattern
    //   matches (the WPT author's intent is to assert a specific clamp
    //   behaviour, which requires a multi-line text input).
    //   Source: investigations/swarm-003/css-overflow__block-ellipsis-001.json
    lineClampProp:      /\b(?:line-clamp|-webkit-line-clamp|max-lines|block-ellipsis)\s*:/i,
    textOverflowEllipsis:/\btext-overflow\s*:\s*[^;{}]*\bellipsis\b/i,

    // Rule 39 — wpt-canvas-shape-sensitive:
    //   3D-camera signal (3D-transform fn / `transform-style: preserve-3d`
    //   / `perspective: <length>`) WITHOUT `display: inline` (complement
    //   to Rule 26 `requires-3d-rendering-context-tree`). When 3D occurs
    //   on a block-level element the spec resolves the rendering inside
    //   the element's own stacking-context box — but the SDUI per-card
    //   capture surface clips the rendering to the card's content box,
    //   producing a different bounding box than the browser-ref's viewport
    //   snapshot. Should batch-clear ~22 css-transforms tests.
    //
    //   Heuristic: source has preserve-3d / 3D-transform-fn / perspective:
    //   <length> AND does NOT also have display:inline (which would route
    //   to Rule 26 instead).
    //   Source: investigations/swarm-003/css-transforms__backface-visibility-hidden-001.json
    perspectiveLengthProp: /\bperspective\s*:\s*\d+(?:\.\d+)?(?:px|em|rem|%|vh|vw|cm|mm|in|pt|pc)\b/i,

    // Rule 40 — requires-anchor-positioning-runtime:
    //   Any `position-anchor:`, `anchor-name:`, `anchor()` function,
    //   `position-area:`, `position-try-fallbacks:`. CSS Anchor Positioning
    //   L1 requires a scene-reconstruction render mode (the .anchor + all
    //   .anchored boxes must live in the same DOM subtree so the browser-
    //   native anchor impl can resolve position-anchor/position-area at
    //   paint time). The SDUI per-component card surface explodes each
    //   IRComponent into its own isolated DOM strip, so anchor binding is
    //   lost and the scene renders as a collapsed row of disconnected
    //   framelets.
    //   Source: investigations/swarm-003/css-anchor-position__auto-margins-position-area.json
    //
    //   wave-29 DETECTOR HONESTY (lane ANCHOR): the original two regexes
    //   below only saw anchor positioning declared as a PROPERTY NAME or a
    //   FUNCTION CALL. CSS Anchor Positioning L1 also lands as
    //     (a) a VALUE keyword — `align-self: anchor-center`,
    //         `place-self: anchor-center`, `justify-self: safe anchor-center`
    //         (css-align-3 §4.1 self-alignment + anchor-pos-1 §6), and
    //     (b) two further PROPERTY names the original alternation missed:
    //         `anchor-scope:` (anchor-pos-1 §3.2 — scopes which anchor names
    //         a subtree can see; anchor-center-overflow-00{1..5} all declare
    //         it) and `position-visibility:` (anchor-pos-1 §7 — hides the
    //         anchored box when the anchor scrolls out).
    //   Measured miss before this fix (wave28-final css-anchor-position
    //   section): anchor-center-002.html and anchor-center-no-default.html —
    //   both pure `align-self: anchor-center` — carried NO anchor tag at all,
    //   so the wall gate and post-load activation could never see them.
    //   Split into three named regexes (not one mega-alternation) so a unit
    //   pin can assert each signal family independently.
    anchorPositionProp: /\b(?:position-anchor|anchor-name|position-area|position-try-fallbacks|anchor-scope|position-visibility)\s*:/i,
    anchorFunctionCall: /\banchor(?:-size)?\s*\(/i,
    //   VALUE-side signal: the `anchor-center` self/content-alignment
    //   keyword, optionally preceded by the css-align-3 `safe`/`unsafe`
    //   overflow-alignment qualifier, on any of the six alignment
    //   longhands/shorthands that accept it. Anchored to a `:` + the
    //   property name so a bare mention of the word in prose/title text
    //   (e.g. `<title>… 'anchor-center' behaves as 'center' …`) does NOT
    //   fire — titles are the single most common false-positive source in
    //   this corpus.
    anchorCenterValue: /\b(?:place-self|place-items|align-self|align-items|justify-self|justify-items)\s*:\s*(?:[a-z-]+\s+)?anchor-center\b/i,

    // Rule 17 — crash-test-blank-ref:
    //   WPT crash regression tests (`*-crash.html`, `*-refcrash.html`)
    //   whose match ref resolves to `css/reference/blank.html` or
    //   `about:blank`. Their pass criterion is "engine survived parsing/
    //   layout"; the visual ref is intentionally blank because the test
    //   markup is expected to paint nothing in a real browser. The SDUI
    //   harness sizes the test component to fit a card, so it ALWAYS paints
    //   visible pixels — guaranteed structural-divergence on every such
    //   test. The 'pass' is implicit in "we got a capture without crashing."
    //   Source: investigations/swarm-001/css-images__gradient-refcrash.json
    crashFilename:      /-(?:crash|refcrash)\.html$/i,
    blankRefName:       /(?:^|\/)reference\/blank\.html$/i,
    aboutBlankHref:     /about:blank/i,

    // Rule 42 — browser-ref-divergent:
    //   `color: transparent` declared anywhere in the stylesheet — signal 1
    //   of the ref-unachievable OS-default-highlight shape (full rationale
    //   at hasUnreachableOsDefaultSelection's banner). The `(?<![-\w])`
    //   lookbehind is the same ident-boundary guard the bidi probes use: a
    //   plain `\b` would also match after a hyphen, so `background-color:
    //   transparent` (which is IRRELEVANT here — a transparent selection
    //   BACKGROUND still paints its glyphs) would fire signal 1 spuriously.
    //   Non-global so `.test()` is stateless on this shared panel.
    colorTransparentDecl: /(?<![-\w])color\s*:\s*transparent\b/i,

    // Rule 43 — requires-non-latin-font-parity:
    //   The three declaration shapes that USE a predefined counter style
    //   (full rationale at hasNonLatinPredefinedCounterStyle's banner). All
    //   three are GLOBAL because `String.matchAll` requires it — and that is
    //   still stateless on this shared panel: matchAll CLONES the regex and
    //   iterates the clone, so the panel entry's own `lastIndex` never moves
    //   (unlike a `.test()` on a /g/ regex, which is why every other entry
    //   here is deliberately non-global). All three are ident-bounded on the
    //   left so a longhand never matches inside a longer property name.
    //     * `list-style-type` and the `list-style` shorthand — and NOTHING
    //       else in that family. `list-style-image: url(georgian.png)` must
    //       not fire, which is why the optional group is `(?:-type)?` and not
    //       `(?:-\w+)?`: after `list-style` the pattern demands either `-type`
    //       or the colon itself, so `-image`/`-position` fall through.
    //     * `counter()` / `counters()` — css-lists-3 §4.3 puts the style name
    //       in the LAST argument. `[^()]*` keeps the match inside one
    //       functional notation (it can never cross a nested paren, which is
    //       also why an `attr()`/`var()` nested inside simply declines).
    //     * `@counter-style … system: extends <name>` — css-counter-styles-3
    //       §3.1: an extending style inherits the base style's SYMBOLS, so it
    //       paints exactly the same non-Latin glyphs.
    listStyleTypeDecl:  /(?<![-\w])list-style(?:-type)?\s*:\s*([^;{}]+)/gi,
    counterFunctionCall:/(?<![-\w])counters?\s*\(([^()]*)\)/gi,
    counterStyleExtends:/(?<![-\w])system\s*:\s*extends\s+([A-Za-z][\w-]*)/gi,
    //     * an AUTHOR `@counter-style <name> { … }` block — name + body. The
    //       body class is `[^{}]*` because css-counter-styles-3 §3's
    //       <declaration-list> can never contain a nested block, so one
    //       brace-free run is the whole rule. See the shadow decline below.
    counterStyleBlock:  /@counter-style\s+([A-Za-z][\w-]*)\s*\{([^{}]*)\}/gi,
    //     * the two SYMBOL-bearing descriptors (§3.2 `symbols`, §3.3
    //       `additive-symbols`) — read only to ask whether a redefinition's
    //       glyphs are provably outside Inter's coverage.
    counterStyleSymbols:/(?<![-\w])(?:additive-)?symbols\s*:\s*([^;}]*)/gi,

    // Rule 44 — requires-grid-lanes:
    //   The CSS Grid Level 3 "lanes" (formerly masonry) LAYOUT MODE, in the
    //   three spellings the spec has passed through and the corpus still
    //   mixes (full rationale + the wave-37 W1 measurement at
    //   declaresGridLanesLayout's banner).
    //
    //   ANCHORED ON THE DISPLAY TYPE (and the one track-list keyword that
    //   turns an ordinary grid into a lanes grid) — deliberately NOT on the
    //   lanes-only PROPERTY names (`masonry-auto-flow`, `item-pack`,
    //   `item-flow`, `flow-tolerance`, `lane-gap`, …). Two reasons, one
    //   measured and one about mechanism:
    //     * measured — css-cascade/all-prop-revert-layer.html enumerates
    //       EVERY registered property, `masonry-auto-flow: ordered` among
    //       them, while asserting `revert-layer`. A property-name alternation
    //       tags it; the display anchor declines. That was the only corpus
    //       false positive the loose form added (654 files vs 634).
    //     * mechanism — a lanes property on a box that is not a lanes
    //       container changes NOTHING about the render, so it cannot be
    //       evidence for a lanes capability tier.
    //   Coverage is not the cost: over the 437 scored css-grid/grid-lanes
    //   cells in the committed web map the display anchor fires on 436 —
    //   byte-identical recall to the loose alternation. The single miss is
    //   grid-lanes-intrinsic-sizing-rows-007-ref.html, a REF file WPT also
    //   ships as a chained test, which declares only `grid-lanes-track`
    //   custom elements.
    //
    //   `(?:inline[\s-]+)?` covers both `display: inline grid-lanes` (the
    //   css-display-3 two-value form the corpus overwhelmingly uses) and a
    //   hyphenated `inline-masonry` legacy spelling; the `\b` on each keyword
    //   keeps `grid-lanes-track` (an element name in the refs) from arming a
    //   `display:` match it never appears in.
    displayGridLanes:   /display\s*:\s*(?:inline[\s-]+)?grid-lanes\b/i,
    displayMasonry:     /display\s*:\s*(?:inline[\s-]+)?masonry\b/i,
    //   The ORIGINAL css-grid-3 spelling: `masonry` as a whole track list on
    //   one axis. Scoped to a single declaration (`[^;{}]*` can never cross a
    //   `;` or a block boundary) so an unrelated `masonry` word later in the
    //   sheet cannot be dragged into the match.
    gridTemplateMasonry:/grid-template-(?:rows|columns)\s*:[^;{}]*\bmasonry\b/i,
};

// Helper: detect ANY runtime-selection signal (pseudo-element OR API call).
function hasRuntimeSelection(html) {
    return RX.selectionPseudo.test(html) || RX.selectionApi.test(html);
}

// Helper: detect ANY shadow-DOM signal.
function hasShadowDom(html) {
    return RX.declarativeShadow.test(html)
        || RX.slottedPseudo.test(html)
        || RX.hostPseudo.test(html)
        || RX.partPseudo.test(html)
        || RX.attachShadow.test(html);
}

// Helper: detect ANY view-transitions signal.
function hasViewTransitions(html) {
    return RX.startViewTransition.test(html)
        || RX.viewTransitionPseudo.test(html)
        || RX.viewTransitionName.test(html);
}

// Helper: detect ANY fragmentation signal (multicol / break / page-break /
// region-fragment). Used by Rule 21 (requires-fragmentation).
function hasFragmentation(html) {
    return RX.columnFragmentation.test(html)
        || RX.breakNonAuto.test(html)
        || RX.pageBreakNonAuto.test(html)
        || RX.regionFragment.test(html);
}

// Helper: detect ANY 3D-rendering-context tree signal — preserve-3d (or any
// 3D-transform fn) combined with display:inline (the spec scenario where a
// flat inline parent terminates the propagated 3D context for its block
// descendants). Used by Rule 26.
function has3dRenderingContextTree(html) {
    const has3d = RX.transformStyle3d.test(html) || RX.transformFn3d.test(html);
    if (!has3d) return false;
    return RX.displayInline.test(html);
}

// Helper: count overflow:scroll|auto|hidden|clip declarations in the source.
// Used by Rule 28 (requires-nested-overflow-clip). Uses a /g regex so a
// single .test() won't suffice — we run a fresh matchAll-style loop each
// invocation (the panel regex is module-shared; reset lastIndex defensively).
function countOverflowClipDeclarations(html) {
    RX.overflowClipAny.lastIndex = 0;
    let count = 0;
    while (RX.overflowClipAny.exec(html) !== null) {
        count++;
        // Guard against pathological zero-width matches (shouldn't happen
        // with the current regex, but defensive).
        if (RX.overflowClipAny.lastIndex === 0) break;
    }
    return count;
}

// Helper: count sibling <div>/<p> opens in the source. Used by Rule 30
// (requires-shared-inline-FC) to detect the sibling-elements pattern.
// We don't try to walk the DOM — just count open tags; tests that pin
// inline-FC typography assertions virtually always have 2+ same-type
// siblings at the same nesting level (test div + ref div, or two
// stacked-alignment paragraphs). Uses a /g regex so a single .test()
// won't suffice; reset lastIndex defensively because the panel is shared.
function countSiblingBlockOpens(html) {
    RX.siblingBlockTagOpen.lastIndex = 0;
    let count = 0;
    while (RX.siblingBlockTagOpen.exec(html) !== null) {
        count++;
        if (RX.siblingBlockTagOpen.lastIndex === 0) break;
    }
    return count;
}

// Helper: detect shared-inline-FC pattern. Used by Rule 30.
// Pattern: 2+ sibling <div>/<p> opens AND any inline-typography property.
// Conservative — requires BOTH signals so simple single-element tests
// (one div with letter-spacing) don't fire; the rule only catches the
// cross-element-alignment pattern that the harness's per-card render
// model fundamentally cannot reproduce.
function hasSharedInlineFcPattern(html) {
    if (!RX.inlineFcTypographyProp.test(html)) return false;
    return countSiblingBlockOpens(html) >= 2;
}

// Helper: detect anchor-positioning signal. Used by Rule 40.
// THREE independent signal families, any one of which means the test's
// geometry is decided by CSS Anchor Positioning L1 (see the RX comment for
// the wave-29 honesty audit that added families 1b and 3):
//   1. a property NAME declaration — position-anchor / anchor-name /
//      position-area / position-try-fallbacks / anchor-scope /
//      position-visibility;
//   2. an anchor() / anchor-size() functional notation in any value;
//   3. the `anchor-center` alignment VALUE keyword (optionally `safe`/
//      `unsafe` qualified) on an alignment longhand/shorthand.
// Deliberately OR-ed, never AND-ed: a test needs only one of these for its
// used box geometry to depend on machinery the runtimes do not implement.
function hasAnchorPositioning(html) {
    return RX.anchorPositionProp.test(html)
        || RX.anchorFunctionCall.test(html)
        || RX.anchorCenterValue.test(html);
}

// Helper: detect viewport-sized-text-ref signal. Used by Rule 38.
// Either line-clamp / max-lines / block-ellipsis property OR
// text-overflow:ellipsis. The "ref content is text-heavy" condition
// is implicit — these properties only get authored when the test
// declares a clamp/ellipsize assertion that needs multi-line text.
function hasViewportSizedTextRef(html) {
    return RX.lineClampProp.test(html) || RX.textOverflowEllipsis.test(html);
}

// Helper: detect cross-origin signal. Used by Rule 37.
// Sub-template tokens always resolve to cross-origin hosts (the WPT
// runner substitutes wptserve's alternate-origin host), OR any direct
// https?:// URL in a runtime-loading element (script/img/source/iframe
// /video/audio/object/embed), OR a <link> with a runtime-loading rel
// (stylesheet, preload, prefetch, …) pointing at https://.
// Deliberately excludes metadata-only <link rel="author|help|reviewer|
// match|mismatch|manifest"> — WPT tests carry tens of thousands of those
// pointing at the spec, and they have NO runtime rendering effect.
function hasCrossOrigin(html) {
    return RX.subTemplateToken.test(html)
        || RX.crossOriginUrl.test(html)
        || RX.crossOriginLink.test(html)
        || RX.crossOriginLinkAlt.test(html);
}

// Helper: detect canvas-shape-sensitive 3D signal. Used by Rule 39.
// Fires when ANY 3D signal (preserve-3d / 3D-transform fn / perspective
// length) is present AND display:inline is NOT also present. The
// display:inline branch routes to Rule 26 (requires-3d-rendering-context-
// tree) instead, so this rule is the complement.
function hasCanvasShapeSensitive3d(html) {
    const has3d = RX.transformStyle3d.test(html)
                || RX.transformFn3d.test(html)
                || RX.perspectiveLengthProp.test(html);
    if (!has3d) return false;
    // If display:inline is present too, Rule 26 owns this test.
    return !RX.displayInline.test(html);
}

// ── wave-29 S-RC3: the REF-UNACHIEVABLE detector (Rule 42) ──────────────────
//
// Every other rule in this file names something OUR pipeline cannot do. This
// one names something CHROMIUM cannot do — a reftest whose committed
// browser-ref PNG is a target the very browser that rasterised the ref does
// not hit when it renders the TEST page. Scoring a runtime against such a ref
// measures a browser bug, not the runtime, and no amount of runtime work can
// clear the 0.95 gate.
//
// MEASURED (wave-29, headless Chromium 151, the pipeline's own canvas
// contract — capture-browser-ref.mjs's canvasFrameCss + REF_RENDER_WIDTH +
// padPngBuffer — diffed against the committed
// refs/<sha>/white-black-ink-font-lh-imgpad/css-pseudo/*.png with
// inject-wpt-block.mjs's diffWebVsRef):
//
//   active-selection-051  ssim 0.9394   ← Chromium's OWN render of the test
//   active-selection-052  ssim 0.9394
//   active-selection-053  ssim 0.9394
//   active-selection-054  ssim 0.9394
//   active-selection-056  ssim 1.0000   ← same family, NOT divergent
//   active-selection-057  ssim 0.9543   ← same family, NOT divergent
//
// The ceiling for 051..054 is 0.9394, i.e. UNDER the 0.95 gate, so a
// perfectly Chrome-faithful renderer still fails. 056/057 clear it, which is
// why the rule below must not fire on them.
//
// WHY the four diverge (pixel-level, from the same measurement): the ref
// (active-selection-051-ref.html) is byte-for-byte the test MINUS the
// `color: transparent` and the `::selection` block, so its selected div
// paints BLACK glyphs on the OS highlight. The test sets `color: transparent`
// on the div and gives `div::selection` a declaration block with no usable
// `color` (an unknown property `foo: bar` in -051, an EMPTY block in -052, an
// invalid value `color: foo` in -053, an invalid `background-color: bar` in
// -054). Per css-pseudo-4 §highlight-cascade the UA must then fall back to
// its OS-DEFAULT highlight colours — including the highlight FOREGROUND,
// which is what would make the transparent text visible again. Chromium does
// not do that: it keeps the originating element's used `color` (transparent)
// and paints only the highlight background. Measured colour histograms of the
// 390x600 captures: ref = 3849 black px + 14009 highlight px, Chromium's test
// render = 911 black px (the unselected instruction prose only) + 18540
// highlight px — the glyphs are simply absent, replaced by highlight fill.
// All four tests carry WPT's `should` flag, i.e. the assertion is SHOULD-level
// and a UA is permitted to fail it, which is exactly what Chromium does.
//
// THE TAG IS NOT AN EXTRACTION WALL. There is no post-load bake, no asset
// inlining and no future runtime work that re-admits these tests: the target
// itself is wrong. It therefore joins inject-wpt-block.mjs's
// REF_UNACHIEVABLE_TAGS (a third, unconditional exclusion family) rather than
// EXTRACTION_WALL_TAGS or SCORE_EXCLUDED_TAGS.

/** CSS Color 4 §6.1 named colours + the two colour-ish keywords, as a CLOSED
 *  spec table. Used ONLY to answer "is this `color:` value a colour at all?"
 *  — the rule needs to tell -053's invalid `color: foo` apart from a real but
 *  unusual keyword like `rebeccapurple`, and a partial list would silently
 *  reclassify a valid colour as invalid and over-fire the exclusion. */
const CSS_NAMED_COLORS = new Set(('aliceblue antiquewhite aqua aquamarine azure beige bisque black ' +
    'blanchedalmond blue blueviolet brown burlywood cadetblue chartreuse chocolate coral ' +
    'cornflowerblue cornsilk crimson cyan darkblue darkcyan darkgoldenrod darkgray darkgreen ' +
    'darkgrey darkkhaki darkmagenta darkolivegreen darkorange darkorchid darkred darksalmon ' +
    'darkseagreen darkslateblue darkslategray darkslategrey darkturquoise darkviolet deeppink ' +
    'deepskyblue dimgray dimgrey dodgerblue firebrick floralwhite forestgreen fuchsia gainsboro ' +
    'ghostwhite gold goldenrod gray green greenyellow grey honeydew hotpink indianred indigo ivory ' +
    'khaki lavender lavenderblush lawngreen lemonchiffon lightblue lightcoral lightcyan ' +
    'lightgoldenrodyellow lightgray lightgreen lightgrey lightpink lightsalmon lightseagreen ' +
    'lightskyblue lightslategray lightslategrey lightsteelblue lightyellow lime limegreen linen ' +
    'magenta maroon mediumaquamarine mediumblue mediumorchid mediumpurple mediumseagreen ' +
    'mediumslateblue mediumspringgreen mediumturquoise mediumvioletred midnightblue mintcream ' +
    'mistyrose moccasin navajowhite navy oldlace olive olivedrab orange orangered orchid ' +
    'palegoldenrod palegreen paleturquoise palevioletred papayawhip peachpuff peru pink plum ' +
    'powderblue purple rebeccapurple red rosybrown royalblue saddlebrown salmon sandybrown ' +
    'seagreen seashell sienna silver skyblue slateblue slategray slategrey snow springgreen ' +
    'steelblue tan teal thistle tomato turquoise violet wheat white whitesmoke yellow yellowgreen ' +
    'transparent currentcolor').split(' '));

/** CSS Color 4 §7 SYSTEM colours + the CSS-wide keywords. Both are valid
 *  `color` values, so a `::selection { color: Highlight }` or
 *  `{ color: inherit }` must count as "the author DID specify a colour" and
 *  keep the rule from firing. */
const CSS_COLOR_KEYWORDS = new Set(('canvas canvastext linktext visitedtext activetext buttonface ' +
    'buttontext buttonborder field fieldtext highlight highlighttext selecteditem selecteditemtext ' +
    'mark marktext graytext accentcolor accentcolortext ' +
    'inherit initial unset revert revert-layer').split(' '));

/** Concatenate every <style>…</style> block and strip CSS comments. The rule
 *  below MUST run on stylesheet text, not raw HTML: all four tests spell the
 *  selector out in their `<meta name="assert" content="… div::selection …">`
 *  prose, so a whole-document scan matches the PROSE and then walks forward
 *  into the first unrelated `{ … }` it finds. */
function styleSheetTextOf(html) {
    const blocks = [];
    for (const m of String(html ?? '').matchAll(/<style\b[^>]*>([\s\S]*?)<\/style>/gi)) {
        blocks.push(m[1]);
    }
    // Single space per comment — css-syntax-3 treats a comment as a token
    // separator, same convention as extract-fixture.mjs's stripComments.
    return blocks.join('\n').replace(/\/\*[\s\S]*?\*\//g, ' ');
}

/** Is `value` a COLOUR (or a CSS-wide keyword standing in for one)?
 *  Deliberately PERMISSIVE — every "maybe" answers true, because a false
 *  "valid" only makes Rule 42 decline (safe), while a false "invalid" would
 *  score-exclude a test that is genuinely reachable. Accepts any hex token,
 *  any functional notation (rgb() hsl() oklch() color-mix() light-dark()
 *  var() — including ones this table has never heard of), and the two closed
 *  keyword tables above. */
function isColorValue(value) {
    const s = String(value ?? '').trim().toLowerCase().replace(/\s*!important$/, '');
    if (s === '') return false;                     // `color:` with no value
    if (/^#[0-9a-f]{3,8}$/.test(s)) return true;    // #rgb #rgba #rrggbb #rrggbbaa
    if (/^[a-z-]+\(/.test(s)) return true;          // ANY functional notation
    return CSS_NAMED_COLORS.has(s) || CSS_COLOR_KEYWORDS.has(s);
}

// Helper: detect the ref-unachievable OS-default-highlight-foreground shape.
// Used by Rule 42. Both signals are REQUIRED (see the banner for why each is
// load-bearing):
//   1. `color: transparent` somewhere in the stylesheet — without it the
//      selected text is visible from its own colour and the OS highlight
//      foreground never decides the render.
//   2. at least one `::selection` rule, and NO `::selection` rule anywhere
//      supplying a usable `color` — which is what hands the foreground to the
//      UA's OS default, the step Chromium skips.
// Coarse-grained by design, exactly like every other rule here: it does NOT
// verify that the `color: transparent` rule and the `::selection` rule select
// the SAME element (classifyAll is a pure whole-corpus string pass with no
// selector engine). Measured precision at that coarseness: over all 33,643
// corpus documents the pair of signals co-occurs in exactly four files —
// active-selection-051..054 — and in none of the other 146 files that use
// ::selection (highlight-paired-cascade-001, target-text-005 and
// selection-background-painting-order all declare a real `::selection`
// colour and are declined by signal 2).
function hasUnreachableOsDefaultSelection(html) {
    const css = styleSheetTextOf(html);
    // Signal 1 — the lookbehind refuses `background-color` / `-webkit-color`.
    if (!RX.colorTransparentDecl.test(css)) return false;
    // Signal 2 — collect every ::selection declaration block. `[^{}]*`
    // between the pseudo and its `{` keeps the match inside one rule prelude
    // (it can never cross a brace), so a selector list like
    // `div#a::selection , hr#b::selection { … }` is matched once, correctly.
    const blocks = [...css.matchAll(/::selection[^{}]*\{([^{}]*)\}/gi)].map((m) => m[1]);
    if (blocks.length === 0) return false;
    for (const block of blocks) {
        for (const decl of block.matchAll(/(?<![-\w])color\s*:\s*([^;}]+)/gi)) {
            // Any usable colour anywhere means the author DID specify the
            // highlight foreground — no OS fallback, no divergence, decline.
            if (isColorValue(decl[1])) return false;
        }
    }
    return true;
}

// ── wave-30 B4(b): the NON-LATIN FONT-BOUNDARY detector (Rule 43) ───────────
//
// A NATIVE-ONLY exclusion, and the first tag in this file that is neither a
// harness gap nor a ref defect: it names a FONT BOUNDARY. The pipeline pins
// one text face end to end (capture-browser-ref.mjs REF_FONT_STACK + the
// embedded Inter Regular/Bold, mirrored by the web harness's html/body stack,
// Compose InterFontFamily and the iOS registered "Inter"). Inter covers
// Latin, Greek and Cyrillic — and NOTHING else. The moment a test paints a
// glyph outside that coverage, each of the four surfaces silently resolves a
// DIFFERENT fallback face:
//   * the ref and the web harness both run Chromium on macOS and land on the
//     same CoreText fallback, so web-vs-ref stays a fair comparison;
//   * Compose on the Android emulator falls back to the platform's own
//     Noto subset, and SwiftUI on the iOS simulator to Apple's system faces.
// Different faces mean different advance widths, different glyph shapes and
// different vertical metrics for the SAME correct string, so the native SSIM
// against a Chromium-macOS-rasterised ref is bounded by typography, not by
// anything the runtimes compute. No geometry fix reaches the 0.95 gate.
//
// MEASURED (tools/titan/runs/wave29-final/sections/css-counter-styles/
// manifest.json — all 12 scored tests of the section, web/ios/android-ref):
//
//   arabic-indic 101   web 0.9932 T · ios 0.9791 T · android 0.9287 F
//   arabic-indic 102   web 0.9791 T · ios 0.8807 F · android 0.7791 F
//   arabic-indic 103   web 0.9986 T · ios 0.9917 T · android 0.9658 T
//   armenian     006   web 0.9950 T · ios 0.9471 F · android 0.9455 F
//   armenian     007   web 0.9829 T · ios 0.8040 F · android 0.8040 F
//   armenian     008   web 0.9435 F · ios 0.9310 F · android 0.9190 F
//   armenian     009   web 0.9971 T · ios 0.9844 T · android 0.9674 T
//   bengali      116   web 0.9922 T · ios 0.9557 T · android 0.9388 F
//   bengali      117   web 0.9675 T · ios 0.8133 F · android 0.7380 F
//   bengali      118   web 0.9971 T · ios 0.9876 T · android 0.9756 T
//   cambodian    158   web 0.9917 T · ios 0.9569 T · android 0.9409 F
//   cambodian    159   web 0.9623 T · ios 0.8365 F · android 0.7244 F
//
// The shape is the argument. WEB clears the gate on 11 of 12 — it renders the
// same glyphs with the same face as the ref, so the counter ALGORITHM is
// provably right on our side (and 008 failing on web too is a REAL, separate
// divergence: the armenian 10000 §7.1.4 fallback. It is not font-bound and it
// stays scored on web, exactly as this tag intends). The natives degrade
// monotonically with GLYPH COUNT: the "1-9" members (103/009/118) pass, the
// "10+" members (102/007/117/159 — three-and-four-digit ordinals, the most
// glyphs per line) collapse to 0.72–0.88. That is a per-glyph typographic
// residue accumulating, not a wrong marker string.
//
// SCOPE — DELIBERATELY THE WHOLE FAMILY, PASSES INCLUDED. The tag fires on
// every test using a non-Latin predefined system, not only the failures, and
// that costs 9 currently-PASSING native diffs (ios 101/103/009/116/118/158,
// android 103/009/118) alongside the 15 failing ones. Keeping the passes
// would be the dishonest half-measure: whether a given test clears 0.95 is
// decided by how many fallback glyphs it happens to paint, not by runtime
// correctness, so a pass here is the same measurement as a failure and must
// leave the denominator with it.
//
// PER-PLATFORM, NOT PER-TEST. web-ref keeps scoring — the boundary is only
// between Chromium-macOS and the two native rasterisers. inject-wpt-block.mjs
// therefore does NOT put this tag in any of the three whole-test exclusion
// families; it gets its own per-platform gate (applyNativeFontParityGate),
// and `scoreEligible` stays true because the test IS still honestly scored on
// one platform.
//
// HOW IT CLOSES: bundle the same non-Latin faces (a Noto subset covering the
// §6 scripts) in ALL FOUR pipelines — ref injection (@font-face data URIs
// alongside the Inter payload in capture-browser-ref.mjs's
// EMBEDDED_FONT_WEIGHTS), the web harness's /fonts, Compose res/font and the
// iOS registered-face list — then bump CANVAS_REV again so every ref
// rasterised against the Latin-only stack retires. At that point the four
// surfaces share a face again, the boundary is gone, and this tag is DELETED
// (not weakened). Until then it is the honest label.
//
// ── wave-31 lane F: THE CLOSING MOVE WAS ATTEMPTED AND MEASURED. It does not
// close from the ref side, and the four steps above are NECESSARY BUT NOT
// SUFFICIENT. Recorded here so the next wave starts from the measurement
// instead of re-deriving it. Everything below was executed end-to-end on the
// wave30-final corpus and then REVERTED; nothing of it is in the tree.
//
// (a) THE CORPUS SURFACE IS SMALLER THAN THE TABLE. Re-deriving the fire list
//     over all 312 wave-30 docs: Rule 43 fires on 13, spanning FOUR systems
//     only — armenian (5 docs), arabic-indic (3), bengali (3), cambodian (2).
//     `georgian`, `hebrew`, the kana orders, every CJK system and the rest of
//     the §6 table are NEVER exercised by this corpus. Six further docs carry
//     literal non-Latin INK without naming a §6 style, and those ARE scored on
//     the natives today: css-text/bidi/bidi-lines-001+002 (Arabic/PERSIAN
//     prose — "فارسی"/"سلام", NOT Hebrew as the wave-23 ledger records),
//     bidi/empty-span-001 + selectors/dir-selector-change-004 +
//     css-text-decor/text-decoration-dotted-001 (Hebrew),
//     selectors/dir-selector-auto-direction-change-001 (Arabic), and
//     css-text-decor/ruby-text-decoration-01 (CJK + kana).
//
// (b) THE FACES EXIST AND FIT. Noto Sans Arabic / Armenian / Bengali / Hebrew
//     / Khmer Regular, OFL 1.1, ~550 KB total, every needed codepoint present
//     (cmap-verified with fontTools). Reproducible:
//       curl -L -o NotoSans<S>-Regular.ttf \
//         https://raw.githubusercontent.com/notofonts/notofonts.github.io/main/fonts/NotoSans<S>/hinted/ttf/NotoSans<S>-Regular.ttf
//     sha256: Arabic bdff3e56…ef48 · Armenian 720df88c…ce1e · Bengali
//     b55c62ee…3193 · Hebrew cdefaf8e…02ee · Khmer e66675f2…e605.
//
// (c) THE DIAGNOSIS IN THIS BANNER IS WRONG FOR ARMENIAN — it is not "a
//     per-glyph typographic residue accumulating". Measured on armenian 007
//     (ref vs the frozen Android capture, per-row ink extents): median row
//     ADVANCE is 31px on all four surfaces, median glyph INK HEIGHT is 18px on
//     all four, and rows 0–19 agree to within 2–4px of width. The face is
//     already effectively the same. The entire 0.78 comes from ONE wrap point:
//     the ref measures row 20 at ~158px against ~156px of available width and
//     wraps it to two lines; Android measures the same string ~3px narrower
//     (~2%), keeps it on one line, and every row below shifts by exactly one
//     31px advance — 27 ref rows vs 26 native rows, 885px vs 854px of canvas.
//     So the lever is ADVANCE WIDTH at a single threshold, not glyph shape.
//     Bundling should still fix it (same outlines → same advances, and
//     Chromium and Android share Skia), but the failure mode to verify against
//     is "does row 20 wrap the same way", not "do the glyphs look closer".
//
// (d) PINNING THE REF ALONE MAKES THE CORPUS WORSE, which is why this must
//     land as ONE four-pipeline change or not at all. With the five faces in
//     REF_FONT_STACK + EMBEDDED_FONT_WEIGHTS and CANVAS_REV bumped to
//     '…-htmlpins-nlfonts', the SCORED literal-ink docs of (a) moved:
//       bidi-lines-001   ios 0.9926 → 0.9559 (−0.037)   web 0.9999 → 0.9604
//       bidi-lines-002   ios 0.9358 → 0.9344            web 0.9930 → 0.9846
//       dir-selector-change-004  ios 0.9958 → 0.9938    web 1.0000 → 0.9930
//     The web deltas are an artifact (frozen captures vs a moved ref; they
//     recover once the harness re-renders). The iOS delta is REAL and does not
//     recover: Chromium-on-macOS and iOS resolve Arabic to the SAME system
//     face today, so the un-pinned status quo gives iOS an accidental parity
//     that pinning Noto in the ref destroys. Armenian barely moved at all
//     (web 0.9829 → 0.9835), confirming macOS already falls back to a
//     Noto-equivalent Armenian face.
//
// (e) THE NATIVE HALF HAS NO MECHANISM YET, and this is the actual blocker.
//     A CSS font stack matches PER CHARACTER (css-fonts-4 §5.2); neither
//     native runtime has an equivalent, and neither has any cascade machinery
//     today (grep: no CustomFallbackBuilder / kCTFontCascadeListAttribute
//     anywhere in runtimes/ or apps/). Compose's `FontFamily(Font…)` selects
//     ONE face by weight/style and leaves per-glyph fallback to the system
//     chain, so pinning needs `Typeface.CustomFallbackBuilder` behind a custom
//     `AndroidFont`/`TypefaceLoader` — and that API is **API 29** while
//     runtimes/compose is `minSdk = 24`, so the pin cannot be unconditional
//     without an API gate that reopens the boundary on older devices. iOS
//     needs a `kCTFontCascadeListAttribute` descriptor threaded through
//     ComponentRenderer's `.custom("Inter", size:)` resolution. Until BOTH
//     exist, bundling faces changes only the ref and regresses (d).
//
// NET: Rule 43 is NOT narrowed by wave 31, and no system is removed from the
// table below — nothing is bundled in the engines, so nothing is closable.
// The table stays as it is until the (e) machinery lands on both natives.

/** css-counter-styles-3 §6 predefined counter styles whose SYMBOLS fall
 *  OUTSIDE the bundled Inter face's Latin/Greek/Cyrillic coverage — i.e. the
 *  closed set of style names that force each surface onto its own fallback.
 *  Transcribed from the spec's §6.2 (simple numeric / alphabetic / additive)
 *  and §6.3 (complex, algorithmic) tables and cross-checked name-for-name
 *  against tools/titan/counter-style-bake.mjs's PREDEFINED table (which is
 *  the same §6 transcription, minus the §6.3 complex styles it declines to
 *  bake) — so a name here that the bake also knows is spelled identically.
 *
 *  DELIBERATELY ABSENT, each for a stated reason — this is a boundary, not a
 *  blanket:
 *    * `decimal`, `decimal-leading-zero`, `lower/upper-alpha`,
 *      `lower/upper-latin`, `lower/upper-roman` — ASCII. Inter covers them.
 *    * `lower-greek` — Greek IS in Inter's coverage, so all four surfaces
 *      keep the same face and the comparison stays fair. Including it would
 *      exclude tests that are not font-bound at all.
 *    * §6.1 `disc`/`circle`/`square`/`disclosure-open`/`disclosure-closed` —
 *      ordinal-independent bullets, drawn (not text-shaped) on the natives.
 *    * `urdu` — NOT a §6 predefined style. It appears in older lists drafts
 *      and in some UA keyword tables, but css-counter-styles-3 spells the
 *      extended-Arabic-Indic digits `persian`; there is no `urdu` entry in
 *      §6 and none in counter-style-bake.mjs's table. Corpus check: the only
 *      four-letter "urdu" matches in tools/wpt/css are inside the word
 *      "tURDUcken" (css-gcpm/using-strings-003.html ipsum text) — which is
 *      itself the reason this rule reads STYLE BLOCKS ONLY and matches whole
 *      idents, never a substring of running prose. */
const NON_LATIN_PREDEFINED_COUNTER_STYLES = new Set([
    // §6.2 simple numeric — one non-ASCII 0-9 digit block each.
    'arabic-indic', 'bengali', 'cambodian', 'khmer', 'cjk-decimal', 'devanagari',
    'gujarati', 'gurmukhi', 'kannada', 'lao', 'malayalam', 'mongolian', 'myanmar',
    'oriya', 'persian', 'tamil', 'telugu', 'thai', 'tibetan',
    // §6.2 simple alphabetic — the four kana orders (no kana in Inter).
    'hiragana', 'hiragana-iroha', 'katakana', 'katakana-iroha',
    // §6.2 simple additive — Armenian / Georgian / Hebrew letter numerals.
    'armenian', 'upper-armenian', 'lower-armenian', 'georgian', 'hebrew',
    // §6.3 complex — CJK/Ethiopic algorithmic systems.
    'cjk-earthly-branch', 'cjk-heavenly-stem', 'cjk-ideographic', 'ethiopic-numeric',
    'japanese-formal', 'japanese-informal',
    'korean-hangul-formal', 'korean-hanja-formal', 'korean-hanja-informal',
    'simp-chinese-formal', 'simp-chinese-informal',
    'trad-chinese-formal', 'trad-chinese-informal',
]);
// Exported so the unit pins can assert the exact membership (a silent
// widening would score-exclude native diffs that are genuinely comparable)
// and so a future font-boundary wave can diff it against the bake's table.
export { NON_LATIN_PREDEFINED_COUNTER_STYLES };

/** Does this declaration VALUE name a non-Latin predefined counter style?
 *
 *  Tokenised into whole CSS idents rather than substring-matched, because a
 *  substring match on this table is a false-positive machine: `list-style:
 *  url(georgian-bullet.png) none` names no counter style at all. `url(…)` and
 *  quoted strings are stripped FIRST for exactly that reason — css-lists-3
 *  §3 lets the `list-style` shorthand carry a `<image>` and
 *  css-counter-styles-3 §6 lets `list-style-type` be a `<string>`, and
 *  neither is a style-name position. */
function valueNamesNonLatinCounterStyle(value, shadowed = null) {
    const cleaned = String(value ?? '')
        .replace(/url\([^)]*\)/gi, ' ')          // <image> position, never a style name
        .replace(/"[^"]*"|'[^']*'/g, ' ');       // <string> marker, never a style name
    for (const [ident] of cleaned.matchAll(/[A-Za-z][\w-]*/g)) {
        const name = ident.toLowerCase();
        // wave-30 fix-T4: the author took this name over with a definition we
        // could not prove non-Latin — the glyphs are no longer the §6 ones.
        if (shadowed?.has(name)) continue;
        if (NON_LATIN_PREDEFINED_COUNTER_STYLES.has(name)) return true;
    }
    return false;
}

/** wave-30 fix-T4: is THIS `@counter-style` body provably outside Inter's
 *  Latin/Greek/Cyrillic coverage? Only two signals count, both positive and
 *  both cheap — the question is never "is it Latin?" (unanswerable from a
 *  string) but "can we PROVE it is not?":
 *
 *    1. `system: extends <non-latin §6 name>` (css-counter-styles-3 §3.1) —
 *       the extending style reuses the base's symbols verbatim.
 *    2. a `symbols` / `additive-symbols` descriptor (§3.2 / §3.3) carrying a
 *       non-ASCII codepoint, or ANY `\` escape (`\0995` is how a sheet spells
 *       U+0995 without relying on the charset, and a CSS escape in a symbol
 *       position is overwhelmingly a non-ASCII codepoint — treated as proof
 *       in the FIRE direction, which is the conservative side here).
 *
 *  Everything else answers false, i.e. "not proven", which makes the caller
 *  DECLINE the name. */
function counterStyleRedefinitionIsNonLatin(body) {
    for (const m of String(body).matchAll(RX.counterStyleExtends)) {
        if (NON_LATIN_PREDEFINED_COUNTER_STYLES.has(m[1].toLowerCase())) return true;
    }
    for (const m of String(body).matchAll(RX.counterStyleSymbols)) {
        // eslint-disable-next-line no-control-regex
        if (/[^\x00-\x7F]/.test(m[1]) || m[1].includes('\\')) return true;
    }
    return false;
}

/** wave-30 fix-T4: §6 predefined names this sheet's AUTHOR has taken over
 *  with a redefinition we cannot prove paints non-Latin glyphs.
 *
 *  css-counter-styles-3 §5 is explicit that an author `@counter-style`
 *  competes in the cascade with the predefined styles and, being later in
 *  cascade order, WINS. Chromium agrees (verified): a page carrying
 *  `@counter-style bengali { system: numeric; symbols: "0" "1" … "9" }` and
 *  `list-style-type: bengali` renders ASCII digits identical to `decimal` —
 *  every surface shapes them from Inter, there is no fallback face, and the
 *  font boundary Rule 43 names simply does not exist for that document. Firing
 *  there would score-exclude two perfectly comparable native diffs.
 *
 *  Deliberately ASYMMETRIC, and this is the honest half: proving a
 *  redefinition non-Latin is easy (see above), proving it Latin is not — a
 *  `system: numeric` with symbols we cannot classify, or an INVALID rule the
 *  UA drops entirely (§3: an invalid @counter-style is ignored, so the
 *  predefined style survives), both land in the same "unknown" bucket. We
 *  DECLINE on unknown: an over-decline costs a native diff that may be
 *  font-bound (visible as a low score, investigable), an over-fire costs a
 *  measurement we can never get back. An empty body is skipped outright —
 *  a block with no descriptor at all cannot be a valid counter style and so
 *  cannot shadow anything. */
function shadowedCounterStyleNames(css) {
    const out = new Set();
    for (const m of String(css).matchAll(RX.counterStyleBlock)) {
        const name = m[1].toLowerCase();
        // Only §6 non-Latin names matter — a `@counter-style my-disc { … }`
        // never armed the rule in the first place.
        if (!NON_LATIN_PREDEFINED_COUNTER_STYLES.has(name)) continue;
        if (!m[2].includes(':')) continue;                    // no descriptors
        if (counterStyleRedefinitionIsNonLatin(m[2])) continue; // still non-Latin
        out.add(name);
    }
    return out;
}
// Exported so the unit pins can assert the decline set directly, without
// having to infer it from a whole-rule boolean.
export { shadowedCounterStyleNames };

/** Rule 43 predicate — does this test USE a non-Latin predefined counter
 *  style? Three use sites, all read off STYLE BLOCKS ONLY (styleSheetTextOf,
 *  the same precision device Rule 42 uses): every one of these tests spells
 *  the style name in its `<title>` ("arabic-indic, 10+") and in its
 *  `<meta name="assert">` prose, so a whole-document scan would fire on the
 *  PROSE of any test that merely mentions a script name.
 *
 *  KNOWN LIMIT, stated rather than hidden: a style named only in an inline
 *  `style=` attribute is not seen. Measured over the corpus this costs
 *  nothing — every non-Latin counter-style use in tools/wpt/css sits in a
 *  `<style>` block — and the alternative (scanning attributes too) re-opens
 *  the prose false-positive the style-block scope closes. */
function hasNonLatinPredefinedCounterStyle(html) {
    const css = styleSheetTextOf(html);
    if (css === '') return false;
    // wave-30 fix-T4: names the author redefined out of the §6 glyph set.
    // Computed ONCE and threaded through all three use sites, so a shadowed
    // name cannot arm the rule from any of them.
    const shadowed = shadowedCounterStyleNames(css);
    // Use 1 — `list-style-type: <name>` / the `list-style` shorthand.
    for (const m of css.matchAll(RX.listStyleTypeDecl)) {
        if (valueNamesNonLatinCounterStyle(m[1], shadowed)) return true;
    }
    // Use 2 — `counter(name, <style>)` / `counters(name, sep, <style>)`
    // (css-lists-3 §4.3: the style is the LAST argument, and defaults to
    // `decimal` when omitted). Only the last argument is examined, so a
    // COUNTER literally named `hebrew` cannot arm the rule from the first
    // argument position.
    for (const m of css.matchAll(RX.counterFunctionCall)) {
        const args = m[1].split(',');
        if (args.length < 2) continue;                    // no style argument
        if (valueNamesNonLatinCounterStyle(args[args.length - 1], shadowed)) return true;
    }
    // Use 3 — `@counter-style X { system: extends <name> }` (§3.1: the
    // extending style reuses the base style's symbols, so it paints the same
    // non-Latin glyphs even though its own name is arbitrary).
    for (const m of css.matchAll(RX.counterStyleExtends)) {
        const base = m[1].toLowerCase();
        // fix-T4: extending a name the author redefined inherits the AUTHOR's
        // symbols, not §6's — same decline, same reason.
        if (shadowed.has(base)) continue;
        if (NON_LATIN_PREDEFINED_COUNTER_STYLES.has(base)) return true;
    }
    return false;
}

// ── wave-34 lane F2: Rule 15 requires an actual at-RULE ─────────────────────
//
// WHAT CHANGED IN THE PIPELINE, AND WHAT DID NOT. Wave 34 opened the
// @font-face delivery channel: the extractor scans the sheet, resolves the
// `src` url() against the corpus and emits a DOCUMENT-level `fontFaces` list
// (schema/spec/01-envelope.md §5), and the web harness turns each entry into
// a real `@font-face` rule served off its /wpt-font/ route. That closes the
// WEB half of the wall — web-vs-ref now shapes the same outlines the ref does
// (css-text/boundary-shaping-001…010 assert on "fi"/"ffi" LIGATURES that only
// the declared LinLibertine face carries; before this wave the assertion was
// unobservable on our side of the diff).
//
// IT DOES NOT CLOSE THE NATIVE HALF, so this tag still fires and still
// excludes the test WHOLE. Neither native runtime has a face-registration
// hook (Compose's `FontFamily(Font…)` picks one bundled face; SwiftUI resolves
// `.custom("Inter", size:)`) — the SAME missing machinery Rule 43's wave-31
// note (e) measured for the non-Latin boundary. Admitting these tests now
// would score two platforms that provably cannot paint the author's glyphs:
// dishonest in the direction that flatters us.
//
// THE CLOSING MOVE, spelled out so the next wave does not re-derive it:
//   1. land face registration on both natives — `Typeface.Builder` fed from
//      IRDocument.fontFaces[].src on Compose, CTFontManagerRegisterFontsForURL
//      threaded into ComponentRenderer's font resolution on SwiftUI. Both
//      decoders already carry the list; only the registration hop is missing.
//   2. RE-TAG per-platform, exactly as Rule 43 is: add this tag to
//      inject-wpt-block.mjs's NATIVE_FONT_PARITY_TAGS so web-ref keeps
//      scoring and only the natives are stamped — a ONE-LINE set membership
//      change, in a file wave-34 lane F2 does not own, which is why the
//      whole-test arm survives this wave.
//   3. only then delete the whole-test exclusion.
//
// WHAT WAS NARROWED, and it is a PURE narrowing — MEASURED on the corpus
// basis Rule 43's notes use (all 33,643 `tools/wpt/css/**/*.html` documents):
// 1639 → 1630 fires, 9 declined, ZERO widened. Widening the sweep to `.xht`
// and `.htm` as well (47,065 documents) gives 2027 → 2018 — the SAME nine
// documents, so the narrowing is basis-independent. The old
// predicate was `/@font-face\b/i` over the whole document: it fired on the
// WORD, wherever it appeared. The 9 it should never have fired on create no
// face at all — they name the at-rule in a `<title>` ("CSS Values and Units
// Test: lh depending on @font-face"), in a testharness assertion string
// ("Line-height and lh before @font-face loads"), or in a commented-out JS
// line (css-fonts/test_font_family_parsing). `@font-face` without a following
// `{` is not an at-rule (css-syntax-3 §5.4 consumes an at-rule only up to a
// `{` block or a `;`), so this arm is DECIDABLE, not a heuristic.
//
// Everything else keeps the pre-wave-34 fire, deliberately:
//   * a face built from SCRIPT (16 corpus documents: `sheet.insertRule(
//     "@font-face …")`, css-cascade/layer-font-face-override's per-case
//     template literals, the css-fonts/font-display generator) still fires —
//     the token+brace test sees the string in the JS source and cannot tell
//     it from markup, which is the conservative answer here;
//   * `src: local(…)` still fires. It needs no asset fetch, but the ref
//     depends on the named face being INSTALLED on the capture machine while
//     the harnesses have no way to reach it — the boundary is real, only its
//     cause differs.
//
// ── A NARROWING THAT WAS BUILT, MEASURED AND REVERTED (recorded so the next
// wave does not rebuild it). The obvious second arm is "a face the document
// DECLARES BUT NEVER USES paints nothing, so every surface stays on its own
// default and there is no boundary". It was implemented in full — @font-face
// blocks parsed for their `font-family` descriptors, blocks excised from the
// sheet so a descriptor could not count as its own use, then every
// `font-family`/`font` value in stylesheet text and in inline `style=`
// attributes scanned for those names with ident boundaries. Corpus impact:
// ZERO documents. WPT declares faces in order to use them, so the arm was
// pure surface with no measurement behind it and it is not in the tree. One
// finding from the attempt IS load-bearing and is preserved above: any future
// scan of font-selecting VALUES must admit quotes, because `font: 20px/1
// "orientation"` (the ~700-document css-writing-modes text-orientation
// family) puts the family in a `<string>` and a quote-excluding value class
// silently declined every one of them.

/** Rule 15 predicate: does this document declare an actual `@font-face`
 *  at-RULE (the token followed by its block), as opposed to merely naming the
 *  at-rule in prose? Exported so the unit pins can exercise the boundary
 *  directly and so the wave that re-tags this rule per-platform has one
 *  named predicate to move. */
export function declaresFontFaceRule(html) {
    // css-syntax-3 §5.4: an at-rule's prelude runs to a `{` block or a `;`.
    // `@font-face` with neither is a mention, not a rule — and a mention
    // creates no face for any surface to diverge on.
    return /@font-face\s*\{/i.test(String(html ?? ''));
}

/** wave-37 W1 — Rule 44 `requires-grid-lanes`: does this test establish a CSS
 *  Grid Level 3 LANES (formerly "masonry") container?
 *
 *  WHAT THE TAG NAMES. css-grid-3's lanes algorithm flows items into the
 *  shortest track rather than onto a fixed row/column grid. The corpus
 *  spells it three ways and this predicate accepts all three:
 *  `display: grid-lanes` / `display: inline grid-lanes` (the current
 *  spelling — 620 of the 1089 files under css/css-grid/grid-lanes/),
 *  `display: masonry` (the intermediate one) and `grid-template-rows|columns:
 *  masonry` (the original).
 *
 *  THE MEASUREMENT (wave-37 lane W1). The capture Chromium — 151.0.7922.47,
 *  the SAME build that rasterised every committed ref — implements NONE of
 *  them. Probed under capture-browser-ref.mjs's own launch flags:
 *  `CSS.supports` is false for display:grid-lanes, display:inline grid-lanes,
 *  display:masonry, grid-template-rows:masonry, item-pack, item-flow and
 *  masonry-auto-flow, and a `display: grid-lanes` element computes to
 *  `display: block` — the declaration is dropped as invalid. The reftest REFS
 *  do not use the syntax at all: they hand-build the masonry picture out of
 *  `display: inline-grid` plus per-column `display: flex` stacks, which is
 *  why they show the lanes geometry while the TEST page shows a block/inline
 *  fallback.
 *
 *  So the target is unreachable — but only MOSTLY, and that is the whole
 *  reason this tag is INFORMATIONAL. Applying the Rule 42/43 ceiling method
 *  (render the TEST in the refs' own Chromium under the ref canvas contract,
 *  diff with inject-wpt-block's diffWebVsRef + the four veto stamps) to all
 *  438 scored css-grid/grid-lanes cells of the committed web map:
 *
 *    437 measured (row-track-sizing-001 is unmeasurable — its page is
 *        14484 px tall, past Chromium's screenshot limit)
 *    363 UNREACHABLE — Chromium cannot reproduce its own ref
 *     74 REACHABLE   — of which 52 are cells the pipeline ALREADY PASSES
 *                      and 22 are ordinary failures with a ≥0.95 ceiling
 *
 *  Precision of a whole-tag exclusion: 362/436 = 0.830 for this predicate,
 *  0.842 for the best narrowing measured (also require the matched REF not to
 *  declare the syntax — 14 refs do, and both sides then fall back together).
 *  Against 1.000 for Rule 42 and the font-face wall, and with 48–54 currently
 *  PASSING cells inside the tagged set, that is the wave-8 denominator failure
 *  again: excluding would silence 74 reachable targets — including 22 real
 *  bugs the map had buried under "masonry" — to hide 363 unreachable ones.
 *  The tag therefore stays SCORED; see inject-wpt-block.mjs's
 *  REFUSED_EXCLUSION_TAGS discipline, and the pin in this module's test file
 *  that asserts membership in none of the five exclusion families.
 *
 *  WHAT THE 22 REACHABLE FAILURES ACTUALLY ARE (they are not grid work):
 *  14 are `contain-intrinsic-size` + `contain: size` on a fallback block box
 *  (the ceiling is 1.0000 and our capture is BLANK — css-contain, not
 *  css-grid), 5 are harness-canvas gaps (capture clipped at the 600 px floor
 *  while the ref grows to 832; `html` background not propagated; a `body`
 *  font shorthand not reaching the component card), 2 are contenteditable and
 *  1 is subgrid margin. None is a converter grid parser or a web grid
 *  emission defect — measured, our capture sits at the Chromium ceiling on
 *  the unreachable set (median ceilSsim − curSsim = 0.0008, 230 of 363 within
 *  0.02).
 *
 *  HOW THIS CLOSES. Not by weakening the gate: either the corpus is re-pinned
 *  to a Chromium that ships Grid L3 lanes (then the refs are re-rendered, the
 *  ceiling rises, and the tag becomes an ordinary capability label), or a
 *  runtime grows a real lanes layout — which would BEAT the passthrough
 *  ceiling, since the refs encode geometry a lanes-capable renderer can hit
 *  and Chromium-on-the-test cannot.
 *
 *  Pure + exported so the unit pins can hold each spelling independently. */
export function declaresGridLanesLayout(html) {
    const s = String(html ?? '');
    return RX.displayGridLanes.test(s)
        || RX.displayMasonry.test(s)
        || RX.gridTemplateMasonry.test(s);
}

// ── wave-37 lane W7: the HYPHENATION-DICTIONARY boundary (Rule 45) ──────────
//
// A NATIVE-ONLY, INFORMATIONAL tag. It names the one half of css-text-3 §5.3
// that neither native runtime can reach, and it is deliberately narrow: only
// `hyphens: auto` ON LANGUAGE-TAGGED CONTENT.
//
// WHY THE LANGUAGE TAG IS THE WHOLE RULE. §5.3 defines `auto` as breaking
// "at appropriate hyphenation points … as determined by … a hyphenation
// resource appropriate to the LANGUAGE of the text", and WPT asserts the
// contrapositive directly: css-text/hyphens-auto-001's own title is "no
// automatic hyphenation without language tagging". So an `auto` declaration
// with no language in scope must render EXACTLY like `manual` — which is what
// both natives do — and tagging it would be a false positive that costs a
// real, earned pass. Measured on the private-sim gate for this lane:
//
//   hyphens-auto-001         (NO lang)  ios 0.7451 → 0.9950  ← now PASSES
//   hyphens-auto-min-content (NO lang)  ios 0.9849 · android 0.9696 (passes)
//
// Both are excluded by the `lang` half of the predicate and keep scoring.
//
// WHAT THE NATIVES ACTUALLY LACK. Not the dictionaries — the SELECTOR for
// them. Android's Minikin ships hyphenation resources and Compose can ask for
// them (`TextStyle.hyphens = Hyphens.Auto`), but only for a language, and the
// IR wire carries no language channel at all (no `lang` on the component, no
// document-level locale): the converter never emits one, so nothing can pick
// a dictionary. iOS has the second wall on top: TextKit's hyphenation lives
// on NSParagraphStyle/CTTypesetter, reachable only through a UIKit label, and
// ImageRenderer refuses to rasterise platform views (see the SwiftUI
// GreedyLineBreaker banner for that measurement). So `auto` degrades to
// `manual`'s explicit opportunities on both, which each runtime now says out
// loud (PropertyTracker breadcrumb, wave-37 lane W7).
//
// MEASURED — the nine language-tagged `auto` cells in the wave-36 depth-48
// gate (runs/wave36-final/sections/css-text/manifest.json; the ios column is
// this lane's post-fix re-capture, android is the frozen gate):
//
//   hyphenate-character-002    web 0.9350 F · ios 0.8808 F · android 0.8917 F
//   hyphenate-limit-chars-001  web 0.9073 F · ios 0.8548 F · android 0.8523 F
//   hyphens-auto-010           web 0.9990 T · ios 0.8497 F · android 0.9444 F
//   hyphens-auto-control       web 1.0000 T · ios 0.8778 F · android 0.9553 T
//   hyphens-auto-inline-010    web 0.9990 T · ios 0.9246 F · android 0.9110 F
//   hyphens-auto-last-word-001 web 0.9459 F · ios 0.9112 F · android 0.9053 F
//   hyphens-out-of-flow-002    web 0.9412 F · ios 0.8876 F · android 0.8586 F
//   hyphens-punctuation-001    web 1.0000 T · ios 0.9386 F · android 0.8878 F
//   hyphens-span-002           web 1.0000 T · ios 0.8876 F · android 0.8664 F
//
// The shape is the argument, exactly as for Rule 43: WEB clears the gate on
// 5 of 9 with the ref's own hyphenation, so the surrounding layout is provably
// right on our side (and the four web failures are separate, non-hyphenation
// divergences that must stay scored). The natives are 0/9 and 1/9 — bounded
// by a resource, not by anything the runtimes compute.
//
// INFORMATIONAL, and that is a decision, not an oversight. Wiring it would
// need a per-platform exclusion family in inject-wpt-block.mjs (the shape
// `requires-non-latin-font-parity` uses: keep `scoreEligible` true, null
// `wptPass` on the two native diffs only, stamp `scoreExcluded`). This lane
// owns neither that file's families nor the denominators they move, so the
// tag ships as a NAME with its measurement attached and changes no score.
// The next wave that wants to spend it needs exactly three things: a
// `NATIVE_HYPHENATION_TAGS` set holding this tag, its own stamp string, and a
// gate call beside `applyNativeFontParityGate` — plus a re-measurement,
// because rule B (wave-37 W7's unbreakable-word overflow) moved four of the
// nine cells and may move more once Compose grows the same rule.
//
// HOW IT CLOSES for real: put a language on the wire (an IR `lang` channel
// the extractor fills from the nearest `lang`/`xml:lang` ancestor), then
// Android can switch `TextStyle.hyphens` on and iOS can consult
// CFStringGetHyphenationLocationBeforeIndex from inside GreedyLineBreaker —
// both are dictionary lookups the platforms already ship. At that point the
// tag is DELETED, not weakened.
//
// Pure + exported so the unit pins can hold each half independently.
export function needsHyphenationDictionary(html) {
    const s = String(html ?? '');
    // Half 1 — an `auto` hyphenation declaration. Tolerates any whitespace
    // around the colon (the corpus authors both `hyphens:auto` and
    // `hyphens: auto`); `-webkit-hyphens`/`-ms-hyphens` prefixed spellings
    // are covered by the same match because the suffix is what anchors.
    if (!/\bhyphens\s*:\s*auto\b/i.test(s)) return false;
    // Half 2 — a language in scope. `lang=`/`xml:lang=` on ANY element (the
    // WPT idiom is `<body lang="en">` or a per-div `lang`), or a `:lang()`
    // selector, which is how the i18n subdirectory tags its cases. Requiring
    // a letter after the `=` rejects `lang=""` (explicitly UNKNOWN language,
    // which per §5.3 must NOT hyphenate — same reading as no tag at all).
    return /\b(?:xml:)?lang\s*=\s*["']?[a-zA-Z]/.test(s)
        || /:lang\(/i.test(s);
}

// ---------------------------------------------------------------------------
// Rule definitions, in evaluation order. ALL rules run per-test; multiple
// tags may fire (a test that imports a webfont AND uses `@media print`
// gets BOTH `requires-font-face` and `requires-print-medium`).
//
// Each entry:
//   tag             — the canonical `requires-X` / `crash-test-X` label
//   description     — one-line reason for git blame
//   swarm001Source  — the swarm-001 investigation file(s) that surfaced it
//   test(html, ctx) — boolean predicate; ctx = { refHtml, testRel, refPath }
//
// DESCRIPTIONS ARE TIER STATEMENTS, NOT CAPABILITY OBITUARIES (retro A9#8,
// 2026-09-04). `description` is copied VERBATIM onto every tagged row
// (bucket-wpt.mjs → notApplicable → the manifests' notApplicableTags), so a
// dashboard reader takes it as the current state of the runtimes. Eight of
// these were written in swarm-001/002/003 (waves ~8-12) as absences — "not
// implemented on any platform", "None of the three SDUI style engines
// implement…", "fragmentation engine not implemented" — and waves 19-47 then
// SHIPPED seven of those capabilities (float wave 19, gap decorations 24/25,
// tables 32/34, multicol fragmentation 44/46, vertical block flow 47) without
// anyone rewriting the sentence; the eighth, requires-containment, is still a
// deliberate native no-op and its rewrite says so. Same
// shape as the Rule-43 premise that expired unnoticed: harmless to the
// numbers (verified: none of the eight is in ANY exclusion family in
// inject-wpt-block.mjs — SCORE_EXCLUDED_TAGS / EXTRACTION_WALL_TAGS /
// FONT_FACE_WALL_TAGS / REF_UNACHIEVABLE_TAGS / NATIVE_FONT_PARITY_TAGS —
// so every tagged cell stays scoreEligible and IS counted; the wave-49
// per-tag scored counts below are the proof), but a future wave proposing an
// exclusion family from a stale sentence would be arguing from a false
// premise. So: when a tag names a capability that now EXISTS, the
// description states the TIER — the implementing files and the measured
// pass rate of its tagged cells at the last full corpus run — and the tag
// keeps naming the residual class, not a void. Re-measure with the census
// idiom (scored = typeof ssim === 'number' && !scoreExcluded; pass =
// wptPass === true) over tools/titan/runs/<run>/sections/*/manifest.json
// when you change one.
// ---------------------------------------------------------------------------

export const RULES = [
    {
        tag: 'requires-tree-nesting',
        description: 'DOM nesting depth >5 — beyond what FIX-A flat extractor preserves',
        swarm001Source: [
            'css-transforms__3d-rendering-context-and-inline.json',
            'css-grid__grid-abspos-staticpos-align-items-center.json',
            'css-overflow__clip-001.json',
        ],
        test: (html /* , _ctx */) => RX.nestedOpenChain.test(html),
    },
    {
        tag: 'requires-inline-FC',
        description: 'Inline formatting context required: <br>, bidi, hypothetical-static-position, inline abspos',
        swarm001Source: [
            'CSS2__hypothetical-inline-alone-on-second-line.json',
            'css-text__empty-span-001.json',
            'css-text-decor__text-decoration-decorating-box-thickness-001.json',
        ],
        test: (html /* , _ctx */) => (
            RX.inlineBr.test(html)
            || RX.unicodeBidi.test(html)
            || RX.bidiOverride.test(html)
            || RX.dirAttr.test(html)
            || RX.inlineAbsposSpan.test(html)
            || RX.hypotheticalKeyword.test(html)
        ),
    },
    {
        tag: 'requires-animation-runtime',
        description: '@keyframes / animation property / Web Animations API requires UA timeline execution between extraction and capture',
        swarm001Source: [
            'css-backgrounds__background-color-animation-with-table1.json',
            'css-view-transitions__animating-new-content-subset.json',
        ],
        // wave-35 B7 adds the script-built-timeline arm — see RX.waapiTimeline
        // for the corpus-wide precision audit behind it. The tag stays a
        // CEILING, not a verdict: a document can carry it and still extract to
        // an exact static render, because extract-fixture.mjs's @keyframes
        // sampler resolves time-stable CSS animations (negative delay, paused,
        // steps() dwell, degenerate endpoints) and stamps those fixtures
        // 'sampled-animation'. Read the two together — this tag says "a
        // timeline exists", `_lossyReasons: ['sampled-animation']` says "and it
        // was statically resolved". Narrowing the tag itself would require the
        // bucketer to run the extractor, inverting the pipeline's layering.
        test: (html /* , _ctx */) => RX.keyframes.test(html) || RX.animationProp.test(html)
            || RX.waapiTimeline.test(html),
    },
    {
        tag: 'requires-script-mutation',
        description: 'Inline <script> mutates DOM after load (appendChild/insertBefore/innerHTML/top-layer showPopover|showModal/...)',
        swarm001Source: [
            'css-lists__add-inline-child-after-marker-001.json',
            'css-position__absolute-pos-box-inside-fixed-pos-box-with-changing-height.json',
        ],
        test: (html /* , _ctx */) => RX.scriptDomMutation.test(html),
    },
    {
        // ── wave-36 M6: THE PRINT/PAGED CAPABILITY DECISION — REFUSED ────
        //
        // The wave-36 mining map ranked "print medium: page boxes, page
        // margins, paginated fragmentation" at #5 (136 failing cells) and
        // asked whether this tag should join an inject-wpt-block.mjs
        // EXCLUSION FAMILY the way animation-runtime and scroll-state
        // effectively have — i.e. whether paged media is a CAPABILITY TIER
        // the static composed canvas cannot deliver.
        //
        // IT IS NOT, and the measurement is unambiguous. THE ANSWER TURNS
        // ON A FACT ABOUT THE REFS, not about pagination: capture-browser-
        // ref.mjs never calls `emulateMediaType('print')`. Every committed
        // ref PNG is a SCREEN rasterisation of the reftest's `*-ref.html`.
        // For most `-print` tests that ref-html states the paged outcome in
        // screen-inert markup (a `page-break-before: always` that does
        // nothing at screen), so the acceptance target is a plain screen
        // render any faithful renderer can hit — pagination never enters
        // the comparison at all.
        //
        // MEASURED (wave-36 M6, all 275 scored tests corpus-wide carrying
        // this tag, from runs/wave35-webmap). Method — the Rule 42/43
        // ceiling method: render the TEST page in the refs' own headless
        // Chromium under capture-browser-ref.mjs's exact canvas contract
        // (canvasFrameCss + REF_RENDER_WIDTH/REF_RENDER_MIN_HEIGHT +
        // padPngBuffer) and diff it against the committed ref with
        // inject-wpt-block.mjs's diffWebVsRef. Chromium is the ceiling for
        // any Chrome-faithful renderer:
        //
        //     Chromium ceiling ≥ 0.95 (ref REACHABLE at screen) : 193 / 275
        //     Chromium ceiling <  0.95 (ref unreachable)        :  82 / 275
        //     …of the 141 tests we ALREADY PASS, ceiling ≥ 0.95 : 140 / 141
        //
        // So a blanket exclusion on this tag would score-exclude 193 tests
        // whose targets Chromium proves are reachable — 141 of them cells
        // we currently PASS. PRECISION 82/275 = 0.298. Every cheap static
        // narrowing was measured against the same ground truth and is no
        // better: `@page` present 0.383, `@page { size }` 0.380, a forced
        // `break-before/after` 0.268, and even a full normalised
        // test-vs-ref SOURCE DELTA (which achieves recall 1.000) lands at
        // 0.304. There is no textual signature; the 82 are separable only
        // by per-test measurement, which this module's <60s / 24k-file
        // budget cannot run.
        //
        // THE WAVE-8 RULE THEREFORE STANDS UNCHANGED: broad capability tags
        // describe tests the harness DOES deliver and render, and they stay
        // SCORED. inject-wpt-block.mjs carries the matching negative pin
        // (`requires-print-medium` must be in NO exclusion family) so this
        // decision cannot be silently reversed.
        //
        // WHAT WOULD CHANGE THE ANSWER — and it is a real closing move, not
        // a formality: give the pipeline a print medium on BOTH sides
        // (emulateMediaType('print') + a page-box canvas in
        // capture-browser-ref.mjs, mirrored by a paged capture stage) and
        // bump CANVAS_REV. Then the 82 become reachable and the tag becomes
        // ordinary. Until then their failures are honestly OURS, and the 53
        // failures with a ≥0.95 ceiling are ordinary bugs — 8 of which
        // wave-36 M6 fixed outright (the `<table border=1>` presentational
        // mapping in extract-fixture.mjs; see htmlTablePresentationProps).
        //
        // wave-37 W5 — the SIMULATION half of the same question, refused on
        // the same fact. If the tag cannot be excluded, could the harness
        // instead honour `@page { size / margin }` on the composed canvas?
        // No: ALL 157 css-page ref PNGs are 390 px wide (CANVAS_WIDTH) even
        // where the test declares `size: 293px / 300px 50px / 400px 300px /
        // a5 / portrait`, because the refs are screen-medium renders. The
        // refs instead hand-encode the expected PAGED result as ordinary DOM
        // at that width (page-margin-007-print-ref.html: seven 300 px
        // `.pagebox` divs for a 400×200 page with 50 px margins), so a
        // page-sized canvas would move us AWAY from the target and would
        // relayout the 12 currently-passing cells that declare a non-canvas
        // `@page size`. Classification of the 86 css-page failures (a test
        // may need several): fragmentation 58, the §5.3 sixteen-box margin
        // grid 25, page-box painting 19, orthogonal flow 6, print-medium
        // media-query resolution 1, script mutation 1; 12 `margin-boxes/*`
        // cells need the margin grid alone — the largest bounded
        // sub-mechanism, and still a feature rather than a canvas tweak. Full
        // table on inject-wpt-block.mjs's REFUSED_EXCLUSION_TAGS.
        tag: 'requires-print-medium',
        description: '*-print.html filename, @media print, or @page — paged-media rendering. SCORED, NOT EXCLUDED: measured over all 275 scored tests carrying it, the refs\' own Chromium reaches the committed ref at screen medium on 193 (and on 140 of the 141 we already pass), so an exclusion would have precision 0.298. See the wave-36 M6 banner above.',
        swarm001Source: [
            'css-page__background-image-only-for-print.json',
            'css-multicol__auto-fill-auto-size-001-print.json',
            'css-fonts__downloadable-font-print.json',
        ],
        test: (html, ctx) => {
            // Filename-suffix and @page checks are unambiguous.
            if (RX.printFilename.test(ctx?.testRel ?? '')) return true;
            if (RX.atPage.test(html)) return true;
            // @media print check: scan all @media-print matches in the
            // source and reject those preceded by `not` (which means
            // "render on screen, suppress in print" — irrelevant to our
            // screen-mode capture). The mediaPrint regex is /g so
            // exec() iterates; we reset lastIndex defensively because the
            // panel is a module-level shared object.
            RX.mediaPrint.lastIndex = 0;
            let m;
            while ((m = RX.mediaPrint.exec(html)) !== null) {
                // m[1] is the media-query prefix between `@media\s+` and
                // `print` — if it ends with `not\s+`, this is a not-print
                // query and we skip it. Otherwise it's a real print rule.
                if (!/\bnot\s+$/i.test(m[1])) return true;
            }
            return false;
        },
    },
    {
        tag: 'requires-table-layout',
        // TIER, not absence (retro A9#8): table layout ships on all three —
        // runtimes/{compose,swiftui,web}/…/table/ (6 · 4 · 16 source files at
        // the 2026-09-05 retro census: TableBoxTree from wave 32 (#103) on
        // compose and wave 34 (#105) on swiftui, TableSeparatedTracks on both
        // natives, web's TableLayout{Config,Extractor,Applier}).
        // CollapsedBorderConflict, which the first draft of this comment
        // listed as shipped, was DELETED on both natives at the retro's
        // P2a/P2b dead-code sweep — it had no production caller (retro S4#4;
        // docs/BACKLOG.md "Mechanisms with no production caller"). The file
        // census is descriptive only: the tag's test() and its exclusion
        // semantics do not read it. What the tag still names is the residual
        // CSS 2.1 §17 fixup class, not a void.
        description: '<table>/<tr>/<td>/... or display:table* — table-fixup + row/column layout (CSS 2.1 §17). TIER, not absence: implemented on all three (runtimes/*/table/, 6 compose · 4 swiftui · 16 web files); 182/255 tagged cells PASS at wave 49 (71%)',
        swarm001Source: [
            'css-backgrounds__background-color-animation-with-table1.json',
            'css-tables__anonymous-table-ws-001.json',
        ],
        test: (html /* , _ctx */) => RX.tableTag.test(html) || RX.displayTable.test(html),
    },
    {
        tag: 'requires-form-control-rendering',
        description: 'Native form controls (<input>/<select>/<button>/...) — UA widget rendering',
        swarm001Source: [
            'css-ui__accent-color-parent-currentcolor.json',
        ],
        test: (html /* , _ctx */) => RX.formControlTag.test(html),
    },
    {
        tag: 'requires-runtime-selection',
        description: '::selection / ::target-text / ::highlight() / getSelection — live Selection state',
        swarm001Source: [
            'css-pseudo__active-selection-056.json',
        ],
        test: (html /* , _ctx */) => hasRuntimeSelection(html),
    },
    {
        tag: 'requires-shadow-dom',
        description: '<template shadowrootmode> / ::slotted / :host / ::part — Shadow DOM scoping',
        swarm001Source: [
            'css-values__attr-in-slotted.json',
        ],
        test: (html /* , _ctx */) => hasShadowDom(html),
    },
    {
        tag: 'requires-containing-block-layout',
        // TIER, not absence (retro A9#8): the ancestor resolution the banner
        // at RX.posFixedAbsolute called impossible under the flat IR is
        // implemented — swiftui Renderer/ContainingBlock.swift +
        // ContainingBlockBasis.swift, layout/position/TransformContainingBlock
        // .{kt,swift}, compose columns/MulticolSpannerContainingBlock.kt —
        // because IR v2's slot/placement channel carries the parent relation
        // (schema/spec/03-children.md); ContainingBlock.swift dates to the
        // fidelity-wave-3 tree work (#28). CSS 2.1 §10.1 defines the block.
        description: 'position:fixed/absolute with bottom/right offsets — ancestor used-height resolution (CSS 2.1 §10.1). TIER, not absence: resolved on the natives (ContainingBlock{,Basis}.swift, TransformContainingBlock.{kt,swift}); 289/345 tagged cells PASS at wave 49 (84%)',
        swarm001Source: [
            'css-position__absolute-pos-box-inside-fixed-pos-box-with-changing-height.json',
        ],
        test: (html /* , _ctx */) => RX.posFixedAbsolute.test(html) && RX.bottomRightOffset.test(html),
    },
    {
        tag: 'requires-orthogonal-flow',
        // TIER, not absence (retro A9#8): all three carry a WritingMode
        // triplet (compose typography/text/, swiftui typography/writing/, web
        // engine/typography/) and both natives run a vertical block-flow pass
        // (VerticalBlockFlowLayout.{kt,swift}, wave 47). css-writing-modes-4 §7.3
        // (Orthogonal Flows) is the residual class this tag still names — and
        // at 70% it is the WEAKEST of the eight re-tiered tags, i.e. the one
        // with the most renderer work left behind it.
        description: 'writing-mode: vertical-* / sideways-* — orthogonal-flow layout (css-writing-modes-4 §7.3). TIER, not absence: WritingMode triplet on all three + VerticalBlockFlowLayout.{kt,swift}; 221/315 tagged cells PASS at wave 49 (70%, the weakest of the re-tiered eight)',
        swarm001Source: [
            'css-writing-modes__available-size-001.json',
        ],
        test: (html /* , _ctx */) => RX.verticalWritingMode.test(html) || RX.textOrientation.test(html),
    },
    {
        tag: 'requires-visited-pseudo',
        description: ':visited selector — privacy-restricted, not modelable in static pipeline',
        swarm001Source: [
            'selectors__caret-color-visited-inheritance.json',
        ],
        test: (html /* , _ctx */) => RX.visitedPseudo.test(html),
    },
    {
        tag: 'requires-caret-rendering',
        description: 'caret-color on editable text — text caret not painted in screenshots',
        swarm001Source: [
            'selectors__caret-color-visited-inheritance.json',
        ],
        test: (html /* , _ctx */) => RX.caretColor.test(html),
    },
    {
        tag: 'requires-contenteditable',
        description: 'contenteditable attribute — focus/caret-aware editor required',
        swarm001Source: [
            'selectors__caret-color-visited-inheritance.json',
        ],
        test: (html /* , _ctx */) => RX.contenteditable.test(html),
    },
    {
        tag: 'requires-font-face',
        description: 'An actual @font-face at-rule. Wave-34 delivers the file to the WEB (the IR now carries a document-level `fontFaces` list — spec 01 §5 — and the harness injects real @font-face rules off /wpt-font/), but NEITHER native runtime can register a face, so the four surfaces still shape different outlines and the exclusion stays whole-test. Closable by native face registration + a per-platform re-tag (NATIVE_FONT_PARITY_TAGS) — see the banner above declaresFontFaceRule.',
        swarm001Source: [
            'css-fonts__downloadable-font-print.json',
        ],
        // wave-34 narrowing: the token must introduce an actual at-RULE.
        // A <title>/assert-string/comment mention creates no face, so there
        // is no boundary to exclude for (9 corpus documents; see the banner).
        test: (html /* , _ctx */) => declaresFontFaceRule(html),
    },
    {
        // wave-37 W5 — MEASURED AND REFUSED as an exclusion family. The wall
        // this tag names is real and unreachable by construction (the
        // ::view-transition pseudo tree is UA-generated, top-layer, and holds
        // snapshot IMAGES — it is not in the DOM, so neither the static
        // extractor nor post-load-extract.mjs's computed-style bake can ever
        // serialize it), but 50 of the 192 scored css-view-transitions cells
        // PASS anyway: a transition frozen at `.ready` with
        // `animation-play-state: paused` shows the OLD snapshot, which IS the
        // pre-script DOM. Excluding on the tag = precision 0.740, and ten
        // narrowings all top out at ~0.90. Full table on
        // inject-wpt-block.mjs's REFUSED_EXCLUSION_TAGS, enforced by the
        // negative pin in inject-wpt-block.test.mjs. The tag is a LABEL only:
        // it must keep firing (the dashboard reads it) and must never join an
        // exclusion set without beating 0.740 first.
        tag: 'requires-view-transitions',
        description: 'document.startViewTransition() / ::view-transition-* / view-transition-name',
        swarm001Source: [
            'css-view-transitions__animating-new-content-subset.json',
        ],
        test: (html /* , _ctx */) => hasViewTransitions(html),
    },
    {
        tag: 'crash-test-blank-ref',
        description: '*-crash.html / *-refcrash.html with blank ref — pass = "engine did not crash"',
        swarm001Source: [
            'css-images__gradient-refcrash.json',
        ],
        test: (_html, ctx) => {
            // Two-part match: filename suffix AND ref points at blank ref.
            // Also accept about:blank in the rel="match" href as
            // a degenerate blank ref.
            const rel = ctx?.testRel ?? '';
            if (!RX.crashFilename.test(rel)) return false;
            const ref = ctx?.refPath ?? '';
            const refRel = ctx?.refRel ?? '';
            return (
                RX.blankRefName.test(ref)
                || RX.blankRefName.test(refRel)
                || RX.aboutBlankHref.test(ref)
                || RX.aboutBlankHref.test(refRel)
            );
        },
    },

    // ── swarm-002 Rules 18..29 ──────────────────────────────────────────────
    // Each rule below was surfaced by a specific swarm-002 investigation file
    // (cited in swarm002Source). See the per-RX comment above for the spec
    // citation and regex rationale.

    {
        tag: 'requires-script-driven-scroll',
        description: '.scrollTop=N / .scrollLeft=N / .scrollTo(/.scrollBy( — script-driven scroll',
        swarm001Source: [], // not surfaced by swarm-001
        swarm002Source: [
            'css-backgrounds__attachment-local-positioning-2.json',
            'css-multicol__multicol-clip-scrolled-content-001.json',
        ],
        test: (html /* , _ctx */) => RX.scrollDriven.test(html),
    },
    {
        tag: 'requires-background-attachment-local-runtime',
        description: 'background-attachment:local — needs runtime scroll-tracking applier',
        swarm001Source: [],
        swarm002Source: [
            'css-backgrounds__attachment-local-positioning-2.json',
        ],
        test: (html /* , _ctx */) => RX.bgAttachmentLocal.test(html),
    },
    {
        tag: 'requires-bundled-asset',
        description: 'References to support/<asset> not bundled into SDUI harness assets',
        swarm001Source: [],
        swarm002Source: [
            'css-backgrounds__attachment-local-positioning-2.json',
            'css-images__image-orientation-background-position.json',
        ],
        test: (html /* , _ctx */) => RX.bundledSupportAsset.test(html),
    },
    {
        tag: 'requires-fragmentation',
        // TIER, not absence (retro A9#8): "fragmentation engine not
        // implemented" was true when swarm-002 wrote it and false since the
        // wave-44/46 multicol lanes — runtimes/*/columns/ holds 28 compose /
        // 27 swiftui / 46 web source files (MultiColumn{Config,Extractor,
        // Applier}, MulticolClone*, MulticolFloatStrip*, FragmentGeometry).
        // The residual is the css-break-3 §3 break-controls class.
        description: 'Multi-column / page-break / region-fragment (css-break-3 §3). TIER, not absence: column fragmentation ships (runtimes/*/columns/, 28 compose · 27 swiftui · 46 web files; the clone/strip geometry landed waves 44-46); 341/429 tagged cells PASS at wave 49 (79%)',
        swarm001Source: [],
        swarm002Source: [
            'css-break__block-max-height-004.json',
            'css-multicol__multicol-clip-scrolled-content-001.json',
        ],
        test: (html /* , _ctx */) => hasFragmentation(html),
    },
    {
        tag: 'requires-containment',
        // TIER, not absence (retro A9#8) — and the ONLY one of the eight
        // whose absence half survives verification: the natives register
        // Contain and deliberately NO-OP it (PerformanceRegistration.kt),
        // while web has the real Contain* triplet. What the old text implied
        // and the measurement refutes is that the tagged cells are therefore
        // lost: 80% of them pass, because the rule fires on any `contain:`
        // declaration and containment seldom alters a single card's paint.
        description: 'contain: <non-none> — CSS Containment (css-contain-2 §2). Web has the Contain* triplet; the natives register the property and deliberately no-op it (PerformanceRegistration.kt). TIER of the TEST SET, not a wall: 164/204 tagged cells PASS at wave 49 (80%) — the rule fires on any contain: declaration and containment seldom changes what a single card paints',
        swarm001Source: [],
        swarm002Source: [
            'css-contain__contain-body-overflow-001.json',
        ],
        test: (html /* , _ctx */) => RX.containNonNone.test(html),
    },
    {
        tag: 'requires-document-tree',
        description: 'CSS-wide keyword (inherit/unset/revert) — needs cascade walk against parent computed values',
        swarm001Source: [],
        swarm002Source: [
            'CSS2__inherit-computed-001.json',
        ],
        test: (html /* , _ctx */) => RX.cssWideKeyword.test(html),
    },
    {
        tag: 'requires-viewport-canvas',
        description: 'position:absolute|fixed with negative offset, or body/html overflow propagation — needs viewport ICB',
        swarm001Source: [],
        swarm002Source: [
            'css-flexbox__flexbox_inline-abspos.json',
            'css-overflow__clip-002.json',
            'css-contain__contain-body-overflow-001.json',
        ],
        test: (html /* , _ctx */) => {
            // Fire on EITHER negative-shift-off-viewport OR body/html overflow
            // propagation. Both architectural gaps share the same "no
            // viewport ICB / no shared body canvas" root cause.
            if (RX.bodyHtmlOverflow.test(html)) return true;
            // Negative-shift requires BOTH position:absolute|fixed AND a
            // negative margin/offset — alone, position:absolute is too broad
            // (covers many simple top-left abspos tests the renderer handles).
            return RX.posFixedAbsolute.test(html) && RX.negativeOffshift.test(html);
        },
    },
    {
        tag: 'requires-gap-decorations',
        // TIER, not absence (retro A9#8): the RX banner's "None of the three
        // SDUI style engines implement the flex/grid integration" was refuted
        // by the wave-24/25 gap lanes — GapDecoration* painters in
        // compose/swiftui columns/, web's ColumnRule*/RowRule* triplets in
        // engine/columns/. At 91% this is the STRONGEST of the eight
        // re-tiered tags: it now labels a family we largely render.
        description: 'column-rule-* / row-rule-* on display:flex|grid — CSS Gap Decorations L1. TIER, not absence: painted on all three since waves 24-25 (GapDecoration* in compose/swiftui columns/, ColumnRule*/RowRule* in web engine/columns/); 126/138 tagged cells PASS at wave 49 (91%, the strongest of the re-tiered eight)',
        swarm001Source: [],
        swarm002Source: [
            'css-gaps__flex-gap-decorations-007.json',
        ],
        test: (html /* , _ctx */) => {
            // Fire when display:flex|grid co-occurs with column-rule-* OR
            // row-rule-* anywhere in the source. Conservatively requires
            // BOTH signals; a multicol-only test that uses column-rule on
            // a non-flex/grid container is handled by Rule 27 (multicol)
            // and is NOT a gap-decorations failure.
            const hasFlexGrid = RX.displayFlexGrid.test(html);
            if (!hasFlexGrid) return false;
            return RX.columnRuleAny.test(html) || RX.rowRuleAny.test(html);
        },
    },
    {
        tag: 'requires-3d-rendering-context-tree',
        description: 'transform-style:preserve-3d or 3D-transform fn with display:inline — needs parent-chain walk',
        swarm001Source: [],
        swarm002Source: [
            'css-transforms__3d-rendering-context-and-inline.json',
        ],
        test: (html /* , _ctx */) => has3dRenderingContextTree(html),
    },
    {
        tag: 'requires-multicol-fragmentation',
        // TIER, not absence (retro A9#8): the multicol column-box geometry
        // this tag says is "needed" is implemented (MultiColumnDistribution,
        // MulticolCloneGeometry + FragmentGeometry from wave 46 (#122),
        // MulticolDescendantSpanner, in runtimes/*/columns/) — css-multicol-1
        // §3's column model. The tag keeps its dashboard job: splitting the
        // multicol family out of the broader Rule-21 fragmentation bucket.
        description: 'css-multicol/* test exercising column-* layout (css-multicol-1 §3). TIER, not absence: column-box geometry ships (MultiColumn*/Multicol* in runtimes/*/columns/); 110/126 tagged cells PASS at wave 49 (87%)',
        swarm001Source: [],
        swarm002Source: [
            'css-multicol__multicol-clip-scrolled-content-001.json',
        ],
        test: (html, ctx) => {
            // Only fire when BOTH (a) the test path is under css-multicol/ AND
            // (b) the source declares a column-* property. This deliberately
            // narrows the scope vs Rule 21 (requires-fragmentation) so the
            // dashboard can report the multicol family separately.
            const rel = ctx?.testRel ?? '';
            if (!RX.multicolPath.test(rel)) return false;
            return RX.multicolProp.test(html);
        },
    },
    {
        tag: 'requires-nested-overflow-clip',
        description: '3+ stacked overflow:scroll|auto|hidden|clip in one chain — clip composition not modeled',
        swarm001Source: [],
        swarm002Source: [
            'css-multicol__multicol-clip-scrolled-content-001.json',
            'css-overflow__clip-002.json',
        ],
        test: (html /* , _ctx */) => countOverflowClipDeclarations(html) >= 3,
    },
    {
        tag: 'requires-attr-function',
        description: 'attr() functional notation — runtime style substitution not modeled',
        swarm001Source: [
            'css-values__attr-in-slotted.json',
        ],
        swarm002Source: [
            'css-values__attr-notype-fallback.json',
        ],
        test: (html /* , _ctx */) => RX.attrFunction.test(html),
    },

    // ── swarm-003 Rules 30..40 ─────────────────────────────────────────────
    // Each rule below was surfaced by a specific swarm-003 investigation file
    // (cited in swarm003Source). See the per-RX comment above for the spec
    // citation and regex rationale.

    {
        tag: 'requires-shared-inline-FC',
        description: '2+ sibling <div>/<p> with inline-FC typography props — cross-element-alignment WPT pattern',
        swarm001Source: [],
        swarm002Source: [],
        swarm003Source: [
            'css-text__hanging-punctuation-first-002.json',
        ],
        test: (html /* , _ctx */) => hasSharedInlineFcPattern(html),
    },
    {
        tag: 'requires-float-layout',
        // TIER, not absence (retro A9#8): the natives grew float layout in
        // wave 19 (#83: FloatRowLayout.kt / FloatRowPacking.swift) next to
        // web's engine/layout/Float* triplet, plus columns/MulticolFloatStrip*
        // in waves 44/46 — 9 compose · 8 swiftui · 4 web source files. CSS 2.1
        // §9.5 is the model; the residual class the tag still names is the
        // clear/<br> wall (BACKLOG #3).
        description: 'float: left|right|inline-start|inline-end (CSS 2.1 §9.5). TIER, not absence: implemented on all three since wave 19 (layout/Float*; multicol strips in waves 44/46); 239/282 tagged cells PASS at wave 49 (85%); residual = the clear/<br> wall',
        swarm001Source: [],
        swarm002Source: [],
        swarm003Source: [
            'CSS2__float-nowrap-5.json',
        ],
        test: (html /* , _ctx */) => RX.floatNonTrivial.test(html),
    },
    {
        tag: 'requires-anonymous-box-generation',
        description: 'display:flex|grid|table parent with bare text-node child — anonymous-box generation pass not implemented',
        swarm001Source: [],
        swarm002Source: [],
        swarm003Source: [
            'css-flexbox__anonymous-flex-item-001.json',
        ],
        test: (html /* , _ctx */) => RX.anonymousFlexItem.test(html),
    },
    {
        tag: 'requires-pseudo-element-rendering',
        description: '::before / ::after / ::marker / ::first-line / ::first-letter / ::placeholder — pseudo-element box rendering',
        swarm001Source: [],
        swarm002Source: [],
        swarm003Source: [
            'css-pseudo__before-preceding-whitespace-dynamic.json',
            'css-text-decor__ruby-text-decoration-01.json',
        ],
        test: (html /* , _ctx */) => RX.pseudoElementSel.test(html),
    },
    {
        tag: 'requires-bundled-font',
        description: 'Body text contains complex-script Unicode (CJK/Tibetan/Arabic/Hebrew/...) — needs bundled font for parity',
        swarm001Source: [],
        swarm002Source: [],
        swarm003Source: [
            'css-fonts__font-feature-settings-tibetan.json',
            'css-counter-styles__counter-cjk-decimal.json',
        ],
        test: (html /* , _ctx */) => RX.complexScriptCodepoint.test(html),
    },
    {
        tag: 'requires-calc-size',
        description: 'calc-size() function — CSS Values 5 §10 size interpolation, not implemented in LengthParser',
        swarm001Source: [],
        swarm002Source: [],
        swarm003Source: [
            'css-values__calc-size-aspect-ratio-001.json',
        ],
        test: (html /* , _ctx */) => RX.calcSizeFn.test(html),
    },
    {
        tag: 'requires-sub-template',
        description: '{{hosts|ports|domains}} sub-template token or .sub.html filename — WPT server-side substitution',
        swarm001Source: [],
        swarm002Source: [],
        swarm003Source: [
            'css-images__image-orientation-none-cross-origin-border-image.json',
        ],
        test: (html, ctx) => {
            // Filename suffix is unambiguous — `.sub.html` files require the
            // wptserve runner to do template substitution.
            if (RX.subFilenameSuffix.test(ctx?.testRel ?? '')) return true;
            return RX.subTemplateToken.test(html);
        },
    },
    {
        tag: 'requires-cross-origin',
        description: 'Cross-origin URL (https?:// in src/href/data) or {{hosts}} sub-template — no CORS / no DNS in harness',
        swarm001Source: [],
        swarm002Source: [],
        swarm003Source: [
            'css-images__image-orientation-none-cross-origin-border-image.json',
        ],
        test: (html /* , _ctx */) => hasCrossOrigin(html),
    },
    {
        tag: 'requires-viewport-sized-text-ref',
        description: 'line-clamp / max-lines / block-ellipsis / text-overflow:ellipsis — needs viewport-sized scrollable text block',
        swarm001Source: [],
        swarm002Source: [],
        swarm003Source: [
            'css-overflow__block-ellipsis-001.json',
        ],
        test: (html /* , _ctx */) => hasViewportSizedTextRef(html),
    },
    {
        tag: 'wpt-canvas-shape-sensitive',
        description: '3D-transform fn / preserve-3d / perspective WITHOUT display:inline — per-card capture clip vs viewport-ref',
        swarm001Source: [],
        swarm002Source: [],
        swarm003Source: [
            'css-transforms__backface-visibility-hidden-001.json',
        ],
        test: (html /* , _ctx */) => hasCanvasShapeSensitive3d(html),
    },
    {
        // wave-29 (lane ANCHOR): this tag is now an EXTRACTION WALL tag
        // (inject-wpt-block.mjs EXTRACTION_WALL_TAGS). Rationale, and why
        // no SECOND `requires-anchor-positioning` tag was minted alongside
        // it: the wall this tag names and the wall the lane asked for are
        // the same wall — none of the three runtimes implements anchor
        // positioning, so the anchored box's used position is unreachable
        // for them, exactly as post-script DOM state is unreachable for the
        // static extractor. Minting a near-duplicate tag would double-tag
        // all 469 already-tagged tests, split the histogram, and leave two
        // detectors free to drift; instead the DETECTOR was widened (see the
        // RX comment) so the tag now covers the anchor-center value form it
        // used to miss. Post-load re-admission comes for free from
        // applyNaScoreGate's generic wall branch: the 34-property state bake
        // snapshots USED insets, which IS the anchored geometry.
        tag: 'requires-anchor-positioning-runtime',
        description: 'position-anchor / anchor-name / anchor-scope / position-area / anchor() / anchor-center — anchored geometry no runtime computes (extraction-wall tag; post-load inset bake re-admits)',
        swarm001Source: [],
        swarm002Source: [],
        swarm003Source: [
            'css-anchor-position__auto-margins-position-area.json',
            // wave-29 detector-honesty finding: pure `align-self:
            // anchor-center` tests the old two-regex detector never saw.
            'wave28-final css-anchor-position anchor-center-002 / anchor-center-no-default',
        ],
        test: (html /* , _ctx */) => hasAnchorPositioning(html),
    },
    {
        // Rule 41 (wave-21 bookkeeping): `.sub.html` filename — the wptserve
        // SERVER contract, not just the substitution templating Rule 38
        // (requires-sub-template) covers. A `.sub.html` file is only
        // meaningful when served by the WPT server (it performs the
        // server-side {{host}}/{{ports}}/{{domains}} rewrite AND serves the
        // cross-origin peers those tokens name); rendered from file:// the
        // raw tokens reach the parser as literal text and every resource
        // they name 404s. The wave-21 gate run surfaced
        // css/css-images/cross-fade-cross-origin-orientation.sub.html
        // reaching extraction with a fixture emitted — the tag makes the
        // server dependency explicit on the dashboard.
        //
        // WHAT IT DOES NOT DO (corrected 2026-09-04, retro A12#7): this
        // banner used to claim "the dashboard's denominator can never
        // quietly include a test our file://-based pipeline cannot run
        // faithfully". It cannot deliver that: `requires-wpt-server` is in
        // NO exclusion family in inject-wpt-block.mjs (not
        // SCORE_EXCLUDED_TAGS / EXTRACTION_WALL_TAGS / FONT_FACE_WALL_TAGS /
        // REF_UNACHIEVABLE_TAGS / NATIVE_FONT_PARITY_TAGS), so a tagged test
        // stays scoreEligible and IS in the denominator. The tag is
        // INFORMATIONAL — it labels the row, it does not gate it.
        // MEASURED (wave49-final, the single tagged member of the depth-48
        // sample): css/css-images/cross-fade-cross-origin-orientation.sub
        // scores web 0.9717 / iOS 0.9698 / Android 0.9477, three permanent
        // FAILs. It is unpassable AS SCORED, not merely hard: the test's
        // only image is `url("http://{{host}}:{{ports[http][1]}}/…/exif-
        // orientation-6-ru.jpg")` while its ref loads the SAME file
        // same-origin, so under file:// the ref paints the photo and the
        // test paints an empty 50×100 box for every renderer, ours or
        // Chromium's. Same class as css-values fallbacks-005 (BACKLOG 5d,
        // "UNPASSABLE AS SCORED").
        // THE EXCLUSION DECISION IS DELIBERATELY NOT TAKEN HERE. Precision
        // is not the blocker — over the pinned mirror every `*.sub.html`
        // REFTEST is genuinely server-bound: 38 of 38 (36 place a
        // substitution token inside a src/href/data/url() resource URL;
        // css-masking/mask-image-cors-001 builds its origins from
        // {{domains}} in script and css-view-transitions/root-element-
        // transition-iframe-cross-origin pulls a cross-origin frame through
        // /common/get-host-info.sub.js — both equally undeliverable). What
        // is missing is the OTHER half of every exclusion family's contract:
        // a delivery record that can re-admit the test the day the pipeline
        // does serve it. requires-bundled-asset re-admits on the extractor's
        // lossyReasons; here lossyReasons records only 'percentage' for this
        // test — the inliner never even noticed the absolute http:// URL, so
        // there is nothing to corroborate the textual tag with and nothing
        // to retire it by. Adding the tag to SCORE_EXCLUDED_TAGS would drop
        // 3 failing cells from a 4111-cell denominator (81.34% → 81.40%) on
        // a filename alone. Route: fix the inliner to stamp an
        // undeliverable-absolute-URL reason, then decide this tag and
        // fallbacks-005 together in one exclusion lane.
        // Deliberately filename-only (unambiguous, spec'd
        // by the WPT file-name-flags contract); token-in-body detection
        // stays Rule 38's job, so the two rules overlap on typical
        // `.sub.html` files — a benign double tag, like the existing
        // cross-origin + sub-template overlap.
        tag: 'requires-wpt-server',
        description: '.sub.html filename — needs the wptserve server (substitution + multi-origin serving); file:// rendering is unfaithful. INFORMATIONAL: in no exclusion family, so tagged tests stay in the scored denominator (retro A12#7)',
        swarm001Source: [],
        swarm002Source: [],
        swarm003Source: [
            // wave-21 gate finding (css-images section): the .sub test
            // extracted + scored despite the server dependency.
            'wave-21 css-images cross-fade-cross-origin-orientation.sub',
        ],
        test: (html, ctx) => RX.subFilenameSuffix.test(ctx?.testRel ?? ''),
    },
    {
        // Rule 42 (wave-29 S-RC3): the first REF-UNACHIEVABLE rule — see the
        // long banner above hasUnreachableOsDefaultSelection for the measured
        // evidence (Chromium's own render of these four tests tops out at
        // ssim 0.9394 against the committed ref, under the 0.95 gate) and for
        // why 056/057 of the same family must NOT fire (1.0000 / 0.9543).
        //
        // The tag deliberately does NOT start with `requires-`: every other
        // tag in this file names a capability the HARNESS lacks, and reading
        // this one as a harness gap would be exactly backwards. It names a
        // defect in the ACCEPTANCE TARGET. inject-wpt-block.mjs gives it its
        // own exclusion family (REF_UNACHIEVABLE_TAGS) for the same reason:
        // there is no delivery stamp and no post-load bake that re-admits it,
        // because nothing about our pipeline is what is wrong.
        tag: 'browser-ref-divergent',
        description: 'Chromium itself cannot reach the committed ref: author color:transparent + a ::selection block with no valid color, whose pass condition is the OS-default highlight FOREGROUND that Chromium does not apply (css-pseudo-4 §highlight-cascade; WPT `should` flag). Measured Chrome-vs-ref ceiling 0.9394 < 0.95.',
        swarm001Source: [],
        swarm002Source: [],
        swarm003Source: [
            // wave-29 selection-diagnosis measurement, re-derivable with
            // headless Chromium + capture-browser-ref.mjs's canvas contract
            // + inject-wpt-block.mjs's diffWebVsRef against
            // refs/<sha>/white-black-ink-font-lh-imgpad/css-pseudo/*.png.
            'wave-29 css-pseudo active-selection-051..054 (0.9394 Chrome-vs-ref ceiling)',
        ],
        test: (html /* , _ctx */) => hasUnreachableOsDefaultSelection(html),
    },
    {
        // Rule 43 (wave-30 B4b): the first PER-PLATFORM tag — see the long
        // banner above hasNonLatinPredefinedCounterStyle for the measured
        // css-counter-styles table (web clears the gate on 11 of 12 with the
        // same face as the ref; the two natives, on their own fallback faces,
        // degrade monotonically with glyph count down to 0.72).
        //
        // Like `browser-ref-divergent` the tag does NOT start with
        // `requires-`… except that it does, and deliberately: this one IS a
        // harness gap, just a per-platform one. What the natives lack is the
        // FACE, not a layout capability — hence `-font-parity` rather than a
        // capability noun. inject-wpt-block.mjs keeps it out of all three
        // whole-test exclusion families and gives it a per-platform gate, so
        // `scoreEligible` stays true and web-ref keeps scoring honestly.
        tag: 'requires-non-latin-font-parity',
        description: 'Uses a css-counter-styles-3 §6 predefined counter style outside the bundled Inter face coverage; the ref and web share a Chromium-macOS fallback face while Compose/SwiftUI resolve their own, so native-vs-ref SSIM is font-bound (measured 0.72–0.98 across css-counter-styles while web holds 0.96–1.00). NATIVE-ONLY exclusion; closable by bundling Noto faces in all four pipelines + a CANVAS_REV bump.',
        swarm001Source: [],
        swarm002Source: [],
        swarm003Source: [
            // wave-30 B4(b) measurement, re-derivable from the committed run.
            'wave-30 css-counter-styles 12/12 (runs/wave29-final/sections/css-counter-styles/manifest.json)',
        ],
        test: (html /* , _ctx */) => hasNonLatinPredefinedCounterStyle(html),
    },
    {
        // Rule 44 (wave-37 W1): the CSS Grid Level 3 LANES layout mode — the
        // corpus' single largest coherent failing pool (384 of 438 scored
        // css-grid/grid-lanes cells in the committed web map, the mining
        // map's rank-2 opportunity).
        //
        // INFORMATIONAL, and that is a MEASURED decision, not an oversight:
        // the capture Chromium implements none of the syntax (a probe of its
        // own launch flags has CSS.supports false on every spelling and
        // `display:grid-lanes` computing to `block`), so 363 of the 437
        // measurable cells are unreachable by any Chrome-faithful renderer —
        // but 74 are reachable and 52 of THOSE already pass. Precision 0.830
        // (0.842 for the best narrowing) is below the 1.000 the accepted
        // exclusion families sit at and the exclusion would cost real passes,
        // so the tag names the tier and changes no score. Full argument, the
        // ceiling table and the closing move: declaresGridLanesLayout's
        // banner above; the refusal is pinned in wpt-not-applicable.test.mjs
        // against all five families exported by inject-wpt-block.mjs.
        tag: 'requires-grid-lanes',
        description: 'Establishes a CSS Grid L3 lanes/masonry container (display:grid-lanes | display:masonry | grid-template-rows|columns:masonry). The capture Chromium 151 implements none of these — the declaration is dropped and the box falls back to block/inline — while the reftest refs hand-build the lanes geometry from inline-grid + flex, so 363 of 437 scored cells are unreachable for any Chrome-faithful renderer. INFORMATIONAL ONLY: 74 cells ARE reachable (52 already passing), so the tag is in no exclusion family and never changes scoreEligible.',
        swarm001Source: [],
        swarm002Source: [],
        swarm003Source: [
            // wave-37 W1 measurement, re-derivable from the committed map +
            // the frozen refs (recipe in declaresGridLanesLayout's banner).
            'wave-37 css-grid grid-lanes 437/438 ceiling (results/webmap-v1.json rank 2)',
        ],
        test: (html /* , _ctx */) => declaresGridLanesLayout(html),
    },
    {
        // Rule 45 (wave-37 W7): the hyphenation-dictionary boundary — see
        // the long banner above needsHyphenationDictionary for the measured
        // nine-cell table (web 5/9 with the ref's own hyphenation, iOS 0/9,
        // Android 1/9) and for why the `lang` half of the predicate is the
        // whole rule (untagged `auto` must render like `manual`, which is
        // what both natives do — and the untagged cells now PASS).
        //
        // NATIVE-ONLY and INFORMATIONAL: like `requires-grid-lanes` it is in
        // no exclusion family, so `scoreEligible` is untouched and every
        // platform keeps scoring. The wiring recipe for a future wave that
        // wants to spend it is in the banner.
        tag: 'requires-hyphenation-dictionary',
        description: 'Declares `hyphens: auto` on language-tagged content (lang= / xml:lang= / :lang()). css-text-3 §5.3 makes the break points a LANGUAGE-dependent dictionary lookup; the IR wire carries no language channel, so neither native can select a dictionary (and iOS additionally has no ImageRenderer-safe TextKit hyphenation seam) — `auto` degrades to `manual` there. Measured on the wave-36 depth-48 gate: web 5/9, iOS 0/9, Android 1/9 across the language-tagged css-text/hyphens cells. NATIVE-ONLY and INFORMATIONAL: in no exclusion family, never changes scoreEligible. Untagged `auto` is deliberately NOT matched — it must render like `manual` (WPT hyphens-auto-001), and it does.',
        swarm001Source: [],
        swarm002Source: [],
        swarm003Source: [
            // wave-37 W7 measurement: the frozen gate manifest for the
            // android/web columns, plus this lane's private-sim re-capture
            // for the iOS column (recipe in the banner above).
            'wave-37 css-text/hyphens 9 language-tagged auto cells (runs/wave36-final/sections/css-text/manifest.json)',
        ],
        test: (html /* , _ctx */) => needsHyphenationDictionary(html),
    },
];

// Sanity: keep this in lock-step with the canonical rule count. swarm-001
// seeded 17 rules; swarm-002 added 12 more (Rules 18..29); swarm-003 added
// 11 more (Rules 30..40); wave-21 added Rule 41 (requires-wpt-server);
// wave-29 added Rule 42 (browser-ref-divergent); wave-30 added Rule 43
// (requires-non-latin-font-parity); wave-37 added Rule 44
// (requires-grid-lanes — INFORMATIONAL, in no exclusion family) and Rule 45
// (requires-hyphenation-dictionary — NATIVE-ONLY, also INFORMATIONAL).
// A drift here means either a rule was dropped or a duplicate was added.
const EXPECTED_RULE_COUNT = 45;
if (RULES.length !== EXPECTED_RULE_COUNT) {
    throw new Error(`wpt-not-applicable: expected exactly ${EXPECTED_RULE_COUNT} rules, got ${RULES.length}`);
}

// ---------------------------------------------------------------------------
// Public API.
// ---------------------------------------------------------------------------

/**
 * Classify a single test against all 45 rules (17 swarm-001 + 12 swarm-002 +
 * 11 swarm-003 + Rule 41 requires-wpt-server (wave-21) + Rule 42
 * browser-ref-divergent (wave-29) + Rule 43 requires-non-latin-font-parity
 * (wave-30) + Rule 44 requires-grid-lanes (wave-37) + Rule 45
 * requires-hyphenation-dictionary (wave-37)).
 *
 * The count is NOT prose: EXPECTED_RULE_COUNT above is 45 and the module
 * throws on load if RULES.length disagrees, so this sentence is the one
 * place the number could drift — and it had, since wave-37 (retro A5#1: the
 * docstring still said 44 and omitted Rule 45 while the guard counted 45).
 * Keep the two in lock-step when a rule lands.
 *
 * @param {object} args
 * @param {string} args.html       — raw test HTML source
 * @param {string} [args.refHtml]  — raw ref HTML source (some rules consult)
 * @param {string} [args.testRel]  — repo-relative test path (some rules
 *                                   consult, e.g. *-print.html suffix or
 *                                   css-multicol/* path matching)
 * @param {string} [args.refPath]  — absolute or repo-relative ref path
 *                                   (used by crash-test-blank-ref)
 * @param {string} [args.refRel]   — repo-relative ref path; if not provided
 *                                   refPath is used
 * @returns {string[]} matched tags, empty array if no rule fires
 */
export function tagsForTest({ html, refHtml = '', testRel = '', refPath = '', refRel = '' }) {
    const ctx = { refHtml, testRel, refPath, refRel };
    const tags = [];
    for (const rule of RULES) {
        // Wrap the predicate in try/catch so a single broken regex doesn't
        // abort the whole bucket pass — degrade gracefully and skip the
        // misbehaving rule.
        try {
            if (rule.test(html, ctx)) tags.push(rule.tag);
        } catch (err) {
            // Surface to stderr but keep going. The bucket-wpt.mjs caller
            // will see this in its log.
            process.stderr?.write?.(
                `wpt-not-applicable: rule '${rule.tag}' threw on ${testRel}: ${err.message}\n`
            );
        }
    }
    return tags;
}

/**
 * Bulk-classify a list of records. Pure function (no IO); the caller
 * is responsible for reading test/ref HTML off disk.
 *
 * @param {Array<{rel: string, html: string, refHtml?: string, refPath?: string, refRel?: string}>} records
 * @returns {{
 *   notApplicable: Object<string, string[]>,
 *   tagHistogram:  Object<string, number>,
 *   matchedCount:  number
 * }}
 */
export function classifyAll(records) {
    const notApplicable = {};
    const tagHistogram = {};
    let matchedCount = 0;
    for (const rec of records) {
        const tags = tagsForTest({
            html:    rec.html,
            refHtml: rec.refHtml,
            testRel: rec.rel,
            refPath: rec.refPath,
            refRel:  rec.refRel,
        });
        if (tags.length === 0) continue;
        notApplicable[rec.rel] = tags;
        matchedCount++;
        for (const t of tags) {
            tagHistogram[t] = (tagHistogram[t] ?? 0) + 1;
        }
    }
    return { notApplicable, tagHistogram, matchedCount };
}

// Export the regex panel for unit tests that want to pin individual
// regex behaviour without going through the full RULES array.
export { RX };
