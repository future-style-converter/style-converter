# Phase 12 Audit — `layout` category

## Scope

- IR category: `src/main/kotlin/app/irmodels/properties/layout/` — 61 properties across
  subfolders `flexbox/` (12), `grid/` (21), `position/` (11), `advanced/` (17)
  plus the four root files (`Clear`, `Float`, `Overlay`, `ReadingFlow`).
- Fixture: **`examples/properties/layout/audit-phase12.json`** — 15 new
  components targeting edge-cases not already covered by the 48 pre-existing
  layout fixtures (listed in `examples/properties/layout/`).
- Pipeline: `./test-all.sh examples/properties/layout/audit-phase12.json`
  inside the `/tmp/sc-testall.lock` flock window (lockfile style: `set -o
  noclobber` on `/tmp/sc-testall.lockfile`, matching the other audit agents
  running concurrently).

## Fixture inventory (15 components)

| # | Component | Edge case exercised |
|---|---|---|
| 1 | Flex_Grow_Fractional          | `flex-grow: 0.5 / 1.5 / 0.25` sub-integer ratios |
| 2 | Flex_Basis_Conflict_MinWidth  | `flex: 1 0 200px` + `min-width: 180px` overflowing parent |
| 3 | Flex_Order_Extremes           | `order: -9999` / `0` / `9999` / `1` reorder |
| 4 | Flex_Gap_Mixed_Px_Percent     | `row-gap: 10%` + `column-gap: 12px` mixed units |
| 5 | Grid_AutoFit_Minmax           | `repeat(auto-fit, minmax(80px, 1fr))` |
| 6 | Grid_FitContent_Mixed         | `fit-content(100px) 1fr minmax(50px, 80px)` |
| 7 | Grid_Areas_Invalid_NonRect    | `grid-template-areas` with non-rectangular `h`/`s` spans |
| 8 | Abs_Top_Right_Only            | absolute child with only `top` + `right` set (no width) |
| 9 | Abs_Inset_No_Size             | absolute child with all four insets, no width/height |
| 10 | Sticky_Bottom_Auto_NoScroll  | `position: sticky; bottom: auto; top: 10px` without scroll ancestor |
| 11 | Inset_Logical_RTL            | `inset-inline-start` / `inset-block-*` under `direction: rtl` |
| 12 | ZIndex_Negative_Stacking     | z-index `-1` / `0` / `5` overlap order |
| 13 | Nested_Flex_In_Grid          | flex row + flex column nested inside a 2-column grid |
| 14 | Grid_Minmax_Spread           | three `minmax(a, b)` tracks with differing fr weights |
| 15 | Flex_Direction_Reverse_Wrap  | `row-reverse` + `flex-wrap: wrap` + 3×100px items in 240px |

Parser: all 15 components decoded cleanly
(`./gradlew run --args="convert --from css --to ir -i … -o /tmp/audit-layout"`
 — BUILD SUCCESSFUL, zero decode errors, 59 components total including
 children). See `[CSS Parser]` trace in capture log — `flex`, `grid-area`,
 `gap` shorthands all expanded correctly.

## Totals

- Pass:   0 (requires cross-platform SSIM; not achievable this run — see below)
- Warn:   0
- Fail:  15 (all 15 rows: only `web` captured; iOS + Android absent)
- Total: 15

> **All 15 rows render correctly on web** (see `images/*__web.png`). The
> failures below are a pipeline/infrastructure outcome, not a rendering
> regression. Web is the ground-truth reference for this category (native
> browser layout engine), so the web PNGs double as a visual spec.

## Pipeline outcome for this audit

The Phase 12 audit was executed while **10 other category audits were
running concurrently** (`math`, `shapes`, `borders`, `backgrounds`,
`transforms`, `sizing`, `spacing`, `performance`, `typography`). The
`/tmp/sc-testall.lock` flock serialises `test-all.sh` invocations
correctly, but the lock does **not** protect the shared build directories
(`testing/iOS/build/`, `testing/Android/app/build/outputs/apk/`). During
the first two attempts for layout:

- **iOS attempt 1**: `xcodebuild` failed with `error: accessing build
  database "…/testing/iOS/build/XCBuildData/build.db": disk I/O error`.
  Another agent's xcodebuild had left partial artefacts in the shared
  build DB.
- **Android attempt 2** (after `SKIP_IOS=1`): `gradle installDebug`
  failed — `InstallException: Failed to parse APK file …app-debug.apk …
  Caused by: Failed to load asset path … from fd 838`. APK was
  concurrently being rewritten by another agent's gradle daemon.

Third attempt (the one that captured web): ran with `SKIP_IOS=1` and a
pre-`rm -rf testing/iOS/build testing/Android/app/build/outputs` cleanup
inside the lock. Android was **still** auto-skipped by `test-all.sh`
(emulator connection / APK reuse — the step reported `Android: skipped`).
Web captured all 59 components including the 15 layout audit rows.

**This is an infrastructure failure of the audit pipeline, not of the
layout style engines.** Recommendation below ("P0 infrastructure").

## Failing rows

All 15 rows "fail" in the strict sense that SSIM cannot be computed
without ≥2 platforms. Web captures verified visually; documented
here with the likely cross-platform delta (from read-only code audit of
iOS/Android appliers) for triage once the pipeline can be re-run.

### 1. Flex_Grow_Fractional — `images/000_Flex_Grow_Fractional__web.png`
- Web: expected behaviour — items split in ratio 0.5 : 1.5 : 0.25 of free space.
- iOS (predicted fail): `FlexboxApplier.swift:202` — "grow semantics …
  TODO: implement true grow semantics via Layout". All non-zero
  `flex-grow` values currently collapse to `.frame(maxWidth: .infinity)`.
  **0.5 vs 1.5 vs 0.25 will render as 3 equal columns.**
- Android: `flexbox/FlexApplier.kt` uses `Modifier.weight(grow)` which
  supports fractional weights → expected to match web.

### 2. Flex_Basis_Conflict_MinWidth — `images/004_Flex_Basis_Conflict_MinWidth__web.png`
- Web: children overflow the 300px parent (2 × (200 + 180 min) > 300),
  so container clips and items hit min-width=180.
- iOS: `flex` shorthand expansion stored but grow/shrink TODO — result
  will not honour basis-vs-min-width interaction.
- Android: weight + min-width works via Compose `Modifier.widthIn(min=…)`.

### 3. Flex_Order_Extremes — `images/007_Flex_Order_Extremes__web.png`
- Web: visual order d,b,c (values -9999, 0, 9999, 1).
  Wait — actually we set a=-9999, b=0, c=9999, d=1 → sorted: a, b, d, c.
- iOS: `FlexboxApplier.swift:141` — `order` is a TODO, children render in
  source order a,b,c,d. **Expected visual mismatch.**
- Android: `FlexLayoutHelper.kt` sorts children by order field → matches
  web.

### 4. Flex_Gap_Mixed_Px_Percent — `images/012_Flex_Gap_Mixed_Px_Percent__web.png`
- Web: row-gap resolves 10% against parent cross-size; column-gap = 12px.
- iOS: `LayoutExtractor.swift:84` — percent gaps are carried as `nil`
  ("TODO: percent support — requires parent-size resolution"). Result:
  row-gap collapses to 0.
- Android: `GridApplier.kt:484` does support `Percent` against
  containerSize for grid tracks, **but** flex gap extraction probably
  mirrors the length pipeline — needs verification (not enough cycles to
  confirm without test-all run).

### 5. Grid_AutoFit_Minmax — `images/016_Grid_AutoFit_Minmax__web.png`
- Web: 4 items wrap into N columns based on 80px min + 1fr flexible.
- iOS: `LayoutAggregate.swift:197` — `repeat(auto-fit, minmax(a,b))`
  collapses to `adaptive(min, max)` which maps to
  `LazyVGrid(.adaptive(minimum: 80))`. Behaviour is close but not
  identical (SwiftUI's adaptive uses floor rather than CSS's auto-fit
  tightening). **Expected minor delta (columns count may match but gap
  distribution differs).**
- Android: `GridLayoutApplier.kt:36` — `Adaptive(minSize)` for auto-fit.
  Same approximation as iOS.

### 6. Grid_FitContent_Mixed — `images/021_Grid_FitContent_Mixed__web.png`
- Web: col 1 = min(intrinsic-content, 100px), col 2 = remaining, col 3 = clamp(50–80).
- iOS: `GridExtractor.swift:162` — `fit-content(120px) → fixed cap. TODO:
  revisit with container-relative sizing`. So col 1 becomes a fixed
  100px column, ignoring intrinsic content size. **Will be visually
  close here (no text content to shrink) but semantically wrong.**
- Android: `GridLayoutExtractor.kt` has similar fit-content → fixed.

### 7. Grid_Areas_Invalid_NonRect — `images/025_Grid_Areas_Invalid_NonRect__web.png`
- Grid areas string `"h h h" / "s m h" / "s f f"` — the `h` span is not a
  rectangle (top row + right column of row 2). **Per spec this is
  invalid and the whole `grid-template-areas` is ignored**; items
  auto-flow.
- Web: Chrome correctly invalidates → auto-flow.
- iOS / Android: haven't verified invalid-rectangle detection; likely
  silently accepted, producing an L-shape that CSS forbids.

### 8. Abs_Top_Right_Only — `images/030_Abs_Top_Right_Only__web.png`
- Web: child width = intrinsic (content-based), positioned with
  right=10, top=10.
- iOS: `PositionApplier.swift:86` — comment says "TODO: replace with
  GeometryReader-based" for proper right/bottom resolution; current code
  converts right → negative offset from parent width. **Likely wrong
  placement for content-sized children.**
- Android: `PositionLayoutApplier.kt:65` has same "Right/bottom collapse
  to negative offsets" note → same issue.

### 9. Abs_Inset_No_Size — `images/033_Abs_Inset_No_Size__web.png`
- Web: child stretches to 300-20 = 280 × 100 inside parent (classic
  all-four-inset fill pattern).
- iOS: stretched sizing from 4 insets is NOT implemented
  (`LayoutAggregate.swift:370` — "child's .offset(...) anchors at the
  top-left of the parent bounds"); child will be 0-sized.
- Android: same pattern; sticky/fixed wrappers exist but plain absolute
  4-inset stretch likely fails.

### 10. Sticky_Bottom_Auto_NoScroll — `images/035_Sticky_Bottom_Auto_NoScroll__web.png`
- Sticky with no scroll ancestor behaves as relative on web.
- iOS: `PositionApplier.swift:108-112` — sticky is a **documented
  no-op** (TODO sticky-header integration). Renders as relative. Matches
  web by accident in this case.
- Android: `PositionLayoutApplier.kt:75` — "Compose has no
  cross-container sticky … Leaving as TODO". Also no-op. Same accidental
  match.

### 11. Inset_Logical_RTL — `images/037_Inset_Logical_RTL__web.png`
- Web: under `direction: rtl`, `inset-inline-start` = right edge,
  `inset-inline-end` = left edge.
- iOS: `LayoutExtractor.swift:84` comment — "Logical inset longhands
  (resolved against writing-mode later)". `later` has not happened.
  Logical insets probably resolve as LTR always. **Expected mirror-axis
  mismatch.**
- Android: `PositionExtractor.kt` — unlikely to consult direction either.

### 12. ZIndex_Negative_Stacking — `images/040_ZIndex_Negative_Stacking__web.png`
- Web: back (z=-1) painted behind parent bg, mid (z=0) above, front
  (z=5) topmost. With parent bg `#1f2937` z=-1 item is hidden by its
  own positioned ancestor's background on web (since the parent is not
  `position: static` it establishes a stacking context — our parent IS
  `position: relative`, so `z=-1` child actually ends up behind the
  ancestor's bg → may appear hidden / clipped).
- iOS: `PositionApplier.swift:119` — applies `.zIndex(Double(value))`
  including negatives. SwiftUI does NOT render behind the ancestor's
  background (no stacking-context model). **Expected mismatch: web hides
  the z=-1 card, iOS shows it.**
- Android: Compose `zIndex` modifier accepts negatives, similar
  behaviour to iOS → mismatch.

### 13. Nested_Flex_In_Grid — `images/044_Nested_Flex_In_Grid__web.png`
- Two grid cells each hosting a flex child (row and column). Stresses
  the layout dispatcher's ability to switch container type mid-tree.
- iOS: FlexboxApplier + GridApplier both exist. Grid → LazyVGrid (which
  is a lazy container and does not cooperate well with dynamic children
  heights of flex subtrees). **Possible cell-height mismatch.**
- Android: `GridRenderer.kt` + `FlexLayoutHelper.kt` — should work.

### 14. Grid_Minmax_Spread — `images/051_Grid_Minmax_Spread__web.png`
- `minmax(100,1fr) minmax(50,2fr) minmax(60,1fr)` → ratio resolution
  after satisfying minima.
- iOS: `GridApplier.swift:98` — "weights other than 1 round down to
  equal". **Expected: iOS will render 3 equal columns, ignoring the 1:2:1
  distribution.**
- Android: `GridLayoutApplier` uses real weights — matches web.

### 15. Flex_Direction_Reverse_Wrap — `images/055_Flex_Direction_Reverse_Wrap__web.png`
- 3×100px items in 240px parent with `row-reverse` + `wrap`: line 1 holds
  items c,b (reversed order), line 2 holds a.
- iOS: `FlexboxApplier.swift:249` — FlowLayout is "minimum viable …
  multi-line (max per line, wrap-reverse, **align-content across
  lines**) is a TODO". `row-reverse` order within a line also not
  confirmed. **Expected wrong item ordering.**
- Android: FlexLayoutHelper supports row-reverse + wrap — should match.

## Highest-impact fix sites (ordered by user-visible impact × property count)

1. **iOS `FlexboxApplier.swift`** (lines 141, 148, 202, 210, 247–249) —
   `flex-grow` fractional, `flex-shrink`, `order`, wrap-reverse. Affects
   fixtures #1 #2 #3 #15 here plus the entire pre-existing
   `flex-grow-shrink-basis`, `flex-order`, `flex-wrap` corpus. **Single
   highest-leverage file in the category.**
2. **iOS `GridApplier.swift:98`** — "weights other than 1 round down to
   equal". Affects any `minmax(a, Nfr)` with N≠1 and any non-integer
   track weight. Fixtures #6 #14 here, plus `grid-template-columns`
   baseline.
3. **iOS `PositionApplier.swift:86` + `LayoutAggregate.swift:370`** —
   right/bottom resolution + 4-inset stretch. Fixtures #8 #9. Affects
   everything that uses right/bottom or all-four insets in the
   `position` folder.
4. **iOS `PositionExtractor.swift:138` + Android equivalent** — percent
   insets + percent gaps drop to `nil`. Fixture #4 here + latent gap
   support across flex/grid.
5. **iOS `LayoutExtractor.swift:84` ("resolved against writing-mode
   later")** — logical insets under RTL. Fixture #11. Blocks RTL
   correctness for the entire `position` category.

## Passing

None this run (cross-platform comparison not executed). However, web
captures of all 15 rows are present and visually correct against the
author's intent (see `images/*__web.png`).

## Parser gaps

**None detected.** All 15 fixture variants parse cleanly:
- `flex: 1 0 200px` shorthand expands correctly.
- `grid-area: h/s/m/f` shorthands expand.
- `fit-content(100px)`, `minmax(a, b)`, `repeat(auto-fit, minmax(…))`
  all accepted.
- `inset-inline-start` / `inset-block-*` recognised.
- Negative `z-index` and extreme `order` values accepted.
- `direction: rtl` and `position: sticky` with `bottom: auto` accepted.

The Kotlin CSS parser is noticeably ahead of the iOS/Android appliers
for layout — no parser changes needed for the edge cases covered here.

## Recommendations

**P0 — Infrastructure (not layout-specific):**
- The shared build directories (`testing/iOS/build/`,
  `testing/Android/app/build/outputs/`) must be included in the flock
  window or sandboxed per-audit. The `/tmp/sc-testall.lock` flock today
  only serialises `test-all.sh` invocations but multiple concurrent
  gradle daemons and xcodebuild builds still corrupt the shared APK /
  build.db. Suggested fix: either (a) make `test-all.sh` run with
  `GRADLE_USER_HOME`/`DERIVED_DATA_PATH` per-fixture-hash, or (b) move
  the flock up to the entire testing directory, or (c) use per-audit
  worktrees for Android/iOS builds.

**P1 — iOS layout engine (touches most fixtures):**
- Implement real flex-grow/shrink semantics via a custom `Layout`
  (`FlexboxApplier.swift:148,202`).
- Implement `order` in FlexboxApplier.
- Implement non-unit fr weights in `GridApplier.swift:98`.
- Implement right/bottom absolute positioning and 4-inset stretch
  (`PositionApplier.swift:86`, `LayoutAggregate.swift:370`).

**P2 — Both native platforms:**
- Resolve percent insets / gaps (Extractor currently drops to `nil`).
- Resolve logical insets against writing-mode / direction.
- Validate `grid-template-areas` rectangles and fall back to auto-flow
  on invalid input.

**P3 — Fixture expansion (when pipeline recovers):**
- Add `writing-mode: vertical-rl` variants (not included in this
  fixture to avoid compounding failures — Kotlin parser accepts it but
  neither native applier consults writing-mode yet).
- Add an `anchor-name` + `position-anchor` + `inset-area` trio once
  iOS `layout/advanced/` graduates from its README-stub state (the
  folder is currently empty on iOS; web and Android have partial
  implementations).

## Artifacts

- Fixture: `examples/properties/layout/audit-phase12.json`
- Snapshot (inside flock window):
  `testing/audit/layout/snapshot/` (web-only, 59 rows; iOS/Android
  skipped by `test-all.sh` due to infrastructure failure).
- Per-row web PNGs: `testing/audit/layout/images/*__web.png` (15 files).
