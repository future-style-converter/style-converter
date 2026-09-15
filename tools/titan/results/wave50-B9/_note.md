# wave50-B9 — the cross-block line-box census, the hanging-whitespace ring, and what `subelements-003` actually needs

Lane B9 of wave 50, working BACKLOG ranked-queue item 4 (b) / (c) / (d).
No devices this wave: everything below is JVM-pinned on verbatim
`wave49-final` per-test IR, PNG-measured against the frozen refs, and
corpus-simulated for blast radius. Predicted flips are predictions.

---

## 1. `compose-lineclamp-census-seam.patch` — APPLY THIS (it is the other half of the census)

The wave-50 Compose line-box census
(`runtimes/compose/src/main/java/com/styleconverter/runtime/typography/inline/LineBoxCensus*.kt`,
`LineBoxChildMetrics.kt`, `LineClampCapResolve.kt`) is **inert without this
patch**: nothing in the production render path calls
`LineClampCap.resolveCapPx` until the renderer threads the component in.

The patch touches two SHARED files (outside lane B9's ownership, hence
deferred rather than committed):

* `runtimes/compose/src/main/java/com/styleconverter/runtime/StyleApplier.kt`
  — `applyProperties` gains a 4th optional parameter `lineClampCapPx:
  Float? = null` which overrides `config.overflow.lineClampCapPx` when
  non-null. Total by construction: `resolveCapPx` returns null exactly
  when `capPx` does, so the guard can never wipe a cap the extractor
  legitimately produced, and every non-renderer call site is untouched.
* `runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt`
  — resolves the cap next to the existing `collapsedMargin` /
  `wptCaptureModeForSizing` threading (the same "the static chain cannot
  see the component" rationale) and passes it down. Wrapped in
  `runCatching` like the `applyProperties` call it feeds.

  **Corrected by wave-50 lane F1 (skeptic S3).** As this lane shipped it, that
  guard was `runCatching { … }.getOrNull()` — which is a SILENT fallthrough,
  not a guard: a wire shape the census cannot read would drop the component
  back to the wave-41 uniform cap with nothing in the report or in logcat to
  say the census never ran, exactly what docs/BACKLOG.md's "no silent
  fallthroughs" constraint forbids. The `applyProperties` call it was modelled
  on does log (`android.util.Log.w("StyleApplier", …)` in its `catch`); this
  one did not. It now carries `.onFailure { … }` raising the
  `LineClamp[census-threw]` PropertyTracker breadcrumb plus a `LineClampCap`
  logcat warning with the component id and the throwable, and is pinned by
  `LineBoxCensusTest`'s *"a census wire that throws is breadcrumbed, never
  silently dropped"* — which executes a wire that really throws out of
  `resolveCapPx` and fails if the `.onFailure` is removed.

**Verified**: applied to the tree, `:runtime:compileDebugKotlin` BUILD
SUCCESSFUL, `:runtime:testDebugUnitTest --tests '*LineBoxCensusTest*'
--tests '*LineClampCapTest*' --tests '*InlineRunFoldTest*' --tests
'*OverflowExtractor*' --tests '*StyleApplier*'` green, then reverted and
`git apply --check` re-confirmed. 73 lines, +2 imports.

### What it moves (`lineclamp-cap-blast-radius.txt`, regenerate with `node simulate-lineclamp-census.mjs`)

`simulate-lineclamp-census.mjs` replays BOTH cap models over every
`wave49-final` per-test IR document. 1435 documents scanned · 34 carry a
fixed-count `line-clamp` · 38 clamp roots · **exactly 3 roots change cap**:

| test | today | census | wave49-final android | prediction |
|---|---|---|---|---|
| `css-overflow/line-clamp-005` | 96px | **112px** | P 0.9615 | stays P, box stops being 16px short |
| `css-overflow/line-clamp-006` | 160px | **192px** | **f 0.9446** | → P (iOS, which has this census since wave 46, is P 0.9822 on the same render) |
| `css-overflow/line-clamp-007` | 96px | **192px** | **f 0.9409** | → P (iOS P 0.9822) |

The other 35 roots return the byte-identical wave-41 number: 18 are
childless (early bail), the rest either have uniform content (the
`hasNonUniformContent` gate, which is an identity not a heuristic — see
`LineBoxCensusRuns.kt`) or land on an unprovable soft-wrapping run.

### Where Compose deliberately diverges from the iOS twin

The Swift twin's `unbounded` verdict DROPS the cap. Compose maps every
non-`Capped` verdict back to the uniform cap instead, because on Compose
the cap node also carries the block-axis ink clip that makes the
discarded lines unpaintable (css-overflow-4 §5.3), and 24 of the 38 roots
land on an unprovable run while passing today. Same for the root's
explicit-`height` gate: Compose does not need it (the cap modifier
coerces into the incoming constraints), and adding it would drop
block-ellipsis-007/-008/-009's clip.

---

## 2. Committed in the lane's own files (no patch needed)

* **the census itself** — `LineBoxCensus.kt` (verdict + `exactLineCount`),
  `LineBoxCensusRuns.kt` (the document-order walk over
  `InlineRunPlan.resolve`, so census order IS paint order),
  `LineBoxChildMetrics.kt` (the per-child wire readers),
  `LineClampCapResolve.kt` (`LineClampCap.resolveCapPx` as an extension),
  plus `LineClampCap.ownBandsPx` factored out of `capPx` so both cap
  routes budget the same band.
  Pinned by `runtimes/compose/src/test/.../typography/inline/LineBoxCensusTest.kt`
  (17 tests, verbatim wire for line-clamp-005/-006/-007 and the
  no-change block-ellipsis-012/-013).
* **the hanging-whitespace ring** — `InlineSpanRing.admit(…, glyphless)`
  admits `white-space` (preserving keywords only) and `background-color`
  on a member whose text is white space and NOTHING else;
  `InlineRunFold` computes `glyphless`; `InlineSpanContent` paints the
  band as `SpanStyle.background`. Pinned in `InlineRunFoldTest`.

### Fold-decision blast radius of the hanging-whitespace ring

Corpus census (`census-whitespace-only-members.mjs` → `whitespace-only-members.txt`, 298 `meta.runs` hosts): **6**
whitespace-only glyph members exist in the whole corpus.

| member | tag | props | after the ring |
|---|---|---|---|
| `css-contain/contain-content-004` ×2 | `td` | BackgroundColor, Padding*, VerticalAlign | STILL REFUSES (tag outside the ring; Padding* outside it too) |
| `css-overflow/block-ellipsis-032` ×3 | `span` | WhiteSpace, BackgroundColor | **NOW ADMITS — the only fold decisions that change** |
| `css-pseudo/first-letter-001` | `span` | +Position, Display, BoxSizing, Margin*, Padding*, Overflow* | STILL REFUSES (13 types still outside the ring) |

So exactly three hosts change, all in `block-ellipsis-032`, where Android
is the FAILING column (wave49-final `css-overflow/block-ellipsis-032
android f 0.9451`; iOS P 0.9577, web P 0.9834) — the standing
"fold decisions change only on currently-failing hosts" rule holds.

**Prediction, and the honest caveat.** Android today paints only "This
text is" in each of the three boxes (the fold bailed, so the four run
members stacked as blocks and the 1-line cap discarded members 2-4). With
the ring the host folds to
`"This text is left-aligned        Clamped"`, one Text, `maxLines = 1`,
ellipsised — i.e. the shape our web runtime already renders and scores
0.9834 on. The ref (Chromium) additionally REMOVES the hanging white
space before placing the ellipsis (css-overflow-4 §4), which neither our
web runtime nor this ring does, so Android should land near web rather
than on the ref. Not device-verified: the exact ellipsis column depends
on Android's `StaticLayout` END-ellipsis arithmetic over the trailing
space run, which no JVM pin can reach.

**iOS was deliberately NOT changed**: its `block-ellipsis-032` column
PASSES (0.9577) on the same visibly-wrong render, and the standing rule
forbids moving a fold decision on a passing host. The Swift twin of the
glyph-less ring (`InlineSpanRing.swift` + `InlineRunFlow.swift`, plus
`AttributedString.backgroundColor` at the segment seam) is queued, not
shipped.

---

## 3. `subelements-003` (queue item 4b) — the backlog entry is WRONG, and the wall is not where it says

BACKLOG 4(b) says the wrapper's blue underline "is missing from EVERY
render". Measured against `wave49-final/sections/css-text-decor/`
(`screenshots/`, `ios-screenshots/`, `android-screenshots/`) and the
frozen ref: **web paints it, iOS paints it, only Android does not.**
Cells: `wave49-final css-text-decor/text-decoration-subelements-003 web P
0.9987 · ios f 0.9043 · android f 0.9084`.

Two independent defects, neither inside lane B9's ownership:

1. **No block-level decoration PROPAGATION channel on Compose.**
   `text-decoration` is not inherited, it propagates to in-flow
   descendants (css-text-decor-3 §2.1). On iOS this happens for free:
   `TypographyApplier.swift:275` attaches `content.underline(true,
   pattern:, color:)` at the BOX level and SwiftUI's text-styling
   environment reaches descendant `Text`s — a mechanism the tree already
   documents at `ComponentRenderer.swift:5963` ("an ancestor's PROPAGATED
   decoration … reaches descendant Texts through the ancestor box's
   modifier"). Compose has no environment-level decoration: `TextDecoration`
   is per-`Text`/per-`SpanStyle`, and `TextDecorationLine` is deliberately
   absent from `ComponentRenderer.INHERITED_PROPERTY_TYPES` (correctly —
   it does not inherit). So the wrapper's `underline blue` reaches
   nothing. **Owner: the Compose decoration lane (typography/decoration) +
   a renderer channel**, not the inline-run fold.

2. **The fold wall is `member-prop:TextDecorationColor-divergence`**, and
   the divergence is measured against a NULL base. Corpus census: only 6
   run members in the whole corpus carry `TextDecorationColor`, in 5
   tests, and ALL six refuse today — including
   `text-decoration-inset-005/-006/-014`, whose members declare
   `text-decoration-color: black` over text that is black by UA default.
   They refuse only because neither the member nor the fold host declares
   `color`, so `InlineSpanRing.admit`'s `effective` ink is null. Bottoming
   that base out at the capture mode's default ink (the RC-B1
   "WPT black / dark-stage #eee" split the borders and effects lanes
   already use) would admit exactly those three members —
   `text-decoration-inset-005 ios f 0.8995 / android f 0.9000`,
   `-006 ios f 0.8987 / android f 0.8993`, `-014 ios f 0.9238 / android
   f 0.9230`, all FAILING on both natives, so the fold-decision rule
   allows it. It needs the capture-mode flag threaded into the ring
   (a signature change through the renderer seam), which is why this lane
   did not take it.
   `text-decoration-inset-011`'s two members stay refused by a second
   rule (`TextUnderlineOffset`), matching BACKLOG 4(b′).

3. **`subelements-003`'s own member cannot be closed on Compose at all
   today**: its `<sup>` declares `text-decoration-color: green` over
   black text, and Compose's `SpanStyle` has NO decoration-colour field —
   admitting it would paint a black underline and call it green. A
   per-range green underline needs a custom draw off `TextLayoutResult`
   in the leaf seam. iOS CAN express it (`Text(seg).underline(true,
   color:)` at the per-segment concatenation seam,
   `ComponentRenderer.swift:4651`), so the iOS half is the tractable one
   and should be staffed first.

---

## Files here

| file | what |
|---|---|
| `compose-lineclamp-census-seam.patch` | the 2-file renderer seam — apply to activate the census |
| `simulate-lineclamp-census.mjs` | replays both cap models over every per-test IR; `ALL=1` prints every root |
| `lineclamp-cap-blast-radius.txt` | its output at wave49-final: 3 changed, 35 unchanged |
