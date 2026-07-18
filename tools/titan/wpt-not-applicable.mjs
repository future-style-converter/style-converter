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
    //   Source: investigations/swarm-001/css-lists__add-inline-child-after-marker-001.json
    //           investigations/swarm-001/css-position__absolute-pos-box-inside-fixed-pos-box-with-changing-height.json
    //           investigations/swarm-002/css-multicol__multicol-clip-scrolled-content-001.json (scrollTop=N)
    //           investigations/swarm-002/css-pseudo__before-dynamic-display-none.json (className mutation)
    //           investigations/swarm-003/css-overflow__block-ellipsis-001.json (.remove(), .replaceWith())
    //           investigations/swarm-003/css-flexbox__anonymous-flex-item-001.json (.remove())
    scriptDomMutation:  /<script\b(?![^>]*\bsrc=)[^>]*>[\s\S]*?(?:\.appendChild\(|\.insertBefore\(|\.replaceChild\(|\.removeChild\(|\.replaceWith\(|\.insertAdjacentHTML\(|\.innerHTML\s*=|\.outerHTML\s*=|\.setAttribute\(|\.append\(|\.prepend\(|document\.write\(|\.createElement\(|\.className\s*=|\.textContent\s*=|\.id\s*=\s*["']|\.classList\.[a-z]+\s*\(|\.style\.[A-Za-z]+\s*=|\.scrollTop\s*=|\.scrollLeft\s*=|\.remove\s*\(\s*\))/i,

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
    //   The IR has no table-layout fixup pass (anonymous-table generation,
    //   row/column algorithms, border-collapse).
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
    //   computes used heights bottom-up. The current flat IR makes this
    //   impossible.
    //   Heuristic: same rule as the source HTML containing `position:
    //   fixed|absolute` AND a `bottom:` or `right:` declaration. We deliberately
    //   require BOTH signals (not just position:absolute alone) to avoid
    //   over-matching simple top-left abspos that the engine handles fine.
    //   Source: investigations/swarm-001/css-position__absolute-pos-box-inside-fixed-pos-box-with-changing-height.json
    posFixedAbsolute:   /\bposition\s*:\s*(?:fixed|absolute)\b/i,
    bottomRightOffset:  /\b(?:bottom|right)\s*:\s*(?:0|-?\d+(?:\.\d+)?(?:px|em|rem|%|vh|vw))\b/i,

    // Rule 11 — requires-orthogonal-flow:
    //   `writing-mode: vertical-*` or `writing-mode: sideways-*` combined
    //   with descendants that have a different flow direction. The IR
    //   doesn't carry the parent tree needed to resolve "nearest ancestor
    //   scroller" max-height per CSS-Writing-Modes-3 §7.3.
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
    //   `@font-face { src: url(…) }` referencing a font asset. The IR
    //   doesn't model webfonts; the renderers can't load arbitrary .ttf/
    //   .woff at capture time.
    //   We deliberately allow `@font-face { src: local(…) }` (system font
    //   reference) because that doesn't need an asset fetch — but the
    //   browser-ref still depends on the local font being installed, so
    //   even a local reference is shaky. For now we conservatively match
    //   ANY @font-face usage.
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
    //   wave-8 NOTE — this rule now OVER-fires for one class of test:
    //   extract-fixture.mjs inlines raster support assets < 8 KB as
    //   percent-encoded data URIs (see its "Support-asset inlining"
    //   section), so url()-referenced small rasters like cat.png (1,883 B)
    //   ARE deliverable and no longer an architectural exclusion. This
    //   string-grep rule cannot stat file sizes (RULES are pure, no IO by
    //   design — classifyAll must stay a pure function), so the tag was
    //   hand-removed from the three css-break background-image-000/001/002
    //   tests (+ refs) in wpt-buckets.json after their fixtures were
    //   re-extracted with inlined assets. A future bucket-wpt.mjs
    //   regeneration will re-add the tag unless this rule grows a
    //   size-aware post-pass — if you regenerate, re-apply the removal or
    //   implement that post-pass (TODO, tracked in the wave-8 record).
    //   Source: investigations/swarm-002/css-backgrounds__attachment-local-positioning-2.json
    //           investigations/swarm-002/css-images__image-orientation-background-position.json
    //           investigations/swarm-003/css-images__image-orientation-none-cross-origin-border-image.json
    bundledSupportAsset:/(?:<(?:embed|object|img|link|source|video|audio|track|iframe)\b[^>]*\b(?:src|data|href)=["']support\/|\burl\(\s*["']?support\/|\burl\(\s*["']?(?:\.\.\/)+support\/|<(?:embed|object|img|link|source|video|audio|track|iframe)\b[^>]*\b(?:src|data|href)=["'][^"']*\{\{(?:hosts|ports|domains)\b[^"']*\}\}[^"']*\/support\/|\burl\(\s*["']?[^)]*\{\{(?:hosts|ports|domains)\b[^)]*\}\}[^)]*\/support\/)/i,

    // Rule 21 — requires-fragmentation:
    //   Multi-column / page-break / region-fragment properties that need a
    //   real fragmentation engine. CLAUDE.md marks multicol as
    //   "config extraction only" on all three platforms (Compose has no
    //   Modifier.column*, SwiftUI has no multicol, the web SDUI renders
    //   each component in its own card outside any multicol container).
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
    //   inline-size, strict, content, or any combination). Only the web
    //   has a Contain triplet (runtimes/web/src/engine/performance/
    //   Contain*); Android + iOS have none. Even on web, `contain` only
    //   has meaning when the component contains its own children in the
    //   rendered surface — and the per-component card host doesn't yet
    //   compose children that way.
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
    //   inline-flex | inline-grid` containers. None of the three SDUI
    //   style engines implement the flex/grid integration of column-rule
    //   painting; Chromium itself only paints these on multicol without
    //   the GapDecorations blink feature flag.
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
    //   Any non-trivial `float: left|right|inline-start|inline-end`. The float
    //   property is not implemented on any of the three runtime renderers;
    //   CLAUDE.md's "Not Applicable to Mobile" section enumerates the absence
    //   of float-style flow layout. Compose, SwiftUI, and the web renderer's
    //   per-component card surface all lay out children with no concept of
    //   CSS float positioning. Note `float: none` is the default and shouldn't
    //   trigger — we explicitly exclude `none`.
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
    anchorPositionProp: /\b(?:position-anchor|anchor-name|position-area|position-try-fallbacks)\s*:/i,
    anchorFunctionCall: /\banchor(?:-size)?\s*\(/i,

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
// Either a position-anchor/anchor-name/position-area/position-try-fallbacks
// property declaration OR an anchor()/anchor-size() functional notation
// anywhere in the source.
function hasAnchorPositioning(html) {
    return RX.anchorPositionProp.test(html) || RX.anchorFunctionCall.test(html);
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
        description: '@keyframes / animation property requires UA timeline execution between extraction and capture',
        swarm001Source: [
            'css-backgrounds__background-color-animation-with-table1.json',
            'css-view-transitions__animating-new-content-subset.json',
        ],
        test: (html /* , _ctx */) => RX.keyframes.test(html) || RX.animationProp.test(html),
    },
    {
        tag: 'requires-script-mutation',
        description: 'Inline <script> mutates DOM after load (appendChild/insertBefore/innerHTML/...)',
        swarm001Source: [
            'css-lists__add-inline-child-after-marker-001.json',
            'css-position__absolute-pos-box-inside-fixed-pos-box-with-changing-height.json',
        ],
        test: (html /* , _ctx */) => RX.scriptDomMutation.test(html),
    },
    {
        tag: 'requires-print-medium',
        description: '*-print.html filename, @media print, or @page — paged-media rendering',
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
        description: '<table>/<tr>/<td>/... or display:table* — table-fixup + row/column layout',
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
        description: 'position:fixed/absolute with bottom/right offsets — needs ancestor used-height resolution',
        swarm001Source: [
            'css-position__absolute-pos-box-inside-fixed-pos-box-with-changing-height.json',
        ],
        test: (html /* , _ctx */) => RX.posFixedAbsolute.test(html) && RX.bottomRightOffset.test(html),
    },
    {
        tag: 'requires-orthogonal-flow',
        description: 'writing-mode: vertical-* / sideways-* — orthogonal-flow layout pass needed',
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
        description: '@font-face — needs webfont loading, not in IR',
        swarm001Source: [
            'css-fonts__downloadable-font-print.json',
        ],
        test: (html /* , _ctx */) => RX.fontFace.test(html),
    },
    {
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
        description: 'Multi-column / page-break / region-fragment — fragmentation engine not implemented',
        swarm001Source: [],
        swarm002Source: [
            'css-break__block-max-height-004.json',
            'css-multicol__multicol-clip-scrolled-content-001.json',
        ],
        test: (html /* , _ctx */) => hasFragmentation(html),
    },
    {
        tag: 'requires-containment',
        description: 'contain: <non-none> — CSS Containment, no Android/iOS applier and per-card host has no children to contain',
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
        description: 'column-rule-* / row-rule-* on display:flex|grid — CSS Gap Decorations L1',
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
        description: 'css-multicol/* test exercising column-* layout — needs multicol fragmentation engine',
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
        description: 'float: left|right|inline-start|inline-end — float layout not implemented on any platform',
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
        tag: 'requires-anchor-positioning-runtime',
        description: 'position-anchor / anchor-name / position-area / anchor() — needs scene-reconstruction render mode',
        swarm001Source: [],
        swarm002Source: [],
        swarm003Source: [
            'css-anchor-position__auto-margins-position-area.json',
        ],
        test: (html /* , _ctx */) => hasAnchorPositioning(html),
    },
];

// Sanity: keep this in lock-step with the canonical rule count. swarm-001
// seeded 17 rules; swarm-002 added 12 more (Rules 18..29); swarm-003 added
// 11 more (Rules 30..40). A drift here means either a rule was dropped or
// a duplicate was added.
const EXPECTED_RULE_COUNT = 40;
if (RULES.length !== EXPECTED_RULE_COUNT) {
    throw new Error(`wpt-not-applicable: expected exactly ${EXPECTED_RULE_COUNT} rules, got ${RULES.length}`);
}

// ---------------------------------------------------------------------------
// Public API.
// ---------------------------------------------------------------------------

/**
 * Classify a single test against all 40 rules (17 from swarm-001 + 12 from
 * swarm-002 + 11 from swarm-003).
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
