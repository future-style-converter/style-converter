# Phase 12 Audit — print + paging + regions

Scope: 11 + 7 + 10 CSS paged-media / regions properties. All are **no-mobile-analog**:
they only have rendering semantics in print / paged-media contexts that none of the
three SDUI targets (Android Compose, iOS SwiftUI, Web React CSSProperties in an
on-screen viewport) reproduce. Expected behavior across all three platforms is
identical no-op rendering — a bare box styled only by width/height/background-color.

READ-ONLY audit. No fixtures authored, no test-all runs executed, no snapshots
captured. Findings below are from static inspection of existing scaffolding and
fixtures.

## Fixture coverage (existing)

| Category | Fixture | Variants | Covers parser value-flavors? |
|---|---|---:|---|
| print   | `examples/properties/print/longtail.json`   | 28 | Yes — Bleed (auto, length), Bookmark{Label,Level,State,Target} all branches, FootnoteDisplay (block/inline/compact), FootnotePolicy (auto/line/block), Leader (dotted/solid/space/string), Marks (none/crop/cross/both), Page (auto/named), Size (auto/named/landscape-pair/portrait-alone/two-length) |
| paging  | `examples/properties/paging/longtail.json`  | 23 | Yes — break-{before,after,inside} (auto, avoid, always, page, recto, verso, column, region, avoid-page, avoid-column), page-break-* legacy triplet including `always`/`left`/`right`, margin-break (auto/keep/discard) |
| regions | `examples/properties/regions/longtail.json` | 23 | Yes — FlowInto/FlowFrom (none + named), RegionFragment, Continue (auto/discard/overflow), CopyInto, WrapFlow (all 6 keywords), WrapThrough, WrapBefore/After/Inside |

Edge cases called out in the prompt:
- `page-break-after: always` — present (`PageBreakBefore_Always`, ok); `always` on
  page-break-after specifically is **not** in the paging fixture — only `left`/`right`.
  Non-blocking (all collapse to no-op).
- `break-before: avoid` + `avoid-page` — both present as separate components,
  which is the honest way to exercise parser branches one-variant-per-component.
- `bookmark-level: 1` with `bookmark-label: "title"` — not combined in a single
  component; exercised independently. Again non-blocking for no-op identity.
- `flow-into: main` — covered by `FlowInto_Named` (uses `article-flow`).
- `footnote-display: block` — covered.

Judgment: existing `longtail.json` fixtures are adequate. No need to author
`audit-phase12.json` — splitting per category is already the convention (matches
the other audit dirs: `spacing/`, `typography/`).

## Platform wiring

| Platform | print | paging | regions |
|---|---|---|---|
| Android | `style/print/` — `PrintConfig.kt`, `PrintExtractor.kt`, `PrintRegistration.kt` (claims all 14 under owner=`print`, includes break-* + page-break-* + 8 parse-only) | `style/paging/` — `PagingRegistration.kt` only (facade, IDs already owned by `print` under first-write-wins) | `style/regions/` — `RegionFlowConfig.kt`, `RegionFlowExtractor.kt`, `RegionsRegistration.kt` (claims all 10) |
| iOS     | `StyleEngine/print/` — `UnsupportedPrint{Config,Extractor,Applier}.swift` collapses 11 properties into one touched-flag config | `StyleEngine/paging/` — same Unsupported* triplet | `StyleEngine/regions/` — same Unsupported* triplet |
| Web     | `src/style/engine/print/` — **full per-property triplet** (33 files: 11 × Config/Extractor/Applier) + `_dispatch.ts` | `src/style/engine/paging/` — full per-property triplet (21 files) + `_dispatch.ts` | `src/style/engine/regions/` — full per-property triplet (30 files) + `_dispatch.ts` |

All appliers on all three platforms emit no visual output for these IR types:
- Android: registered via `PropertyRegistry.migrated(...)` — claimed for owner
  attribution only, never contributes to the Compose `Modifier` chain.
- iOS: `UnsupportedPrintApplier` etc. return an empty SwiftUI view modifier.
- Web: each `apply*` returns an empty partial `CSSProperties` (CSS print
  properties like `page-break-before` would be valid in React's typed CSS but
  the dispatchers here intentionally do not emit them — on-screen rendering is
  unaffected either way).

## Identity-render expectation

Each fixture component is a 160×80 box with a solid `background-color`. Because
every print/paging/regions property is a documented no-op, the three platform
screenshots should match the reference box SSIM ≥ 0.95 variant-for-variant.
Any regression would have to come from *outside* this category (e.g., a
spacing/sizing default leaking in) — which is exactly why the prompt asks to
flag "unexpected divergence."

## Divergences flagged

1. **Web does real per-property work; iOS collapses to a single unsupported
   handler.** This is intentional (Web has a native `CSSProperties` surface for
   some of these, though the dispatchers currently emit nothing), but it means
   the coverage shape is asymmetric. The CLAUDE.md contract says "triplet exists
   on all three platforms" — iOS's `UnsupportedPrintExtractor` is one triplet
   serving 11 properties. If strict per-property parity is required by Phase 12,
   iOS's `StyleEngine/print|paging|regions` needs to be exploded into 11/7/10
   individual triplets. **Today it is not.**

2. **Android `paging/` folder only contains `PagingRegistration.kt`** — no
   Config/Extractor/Applier triplet at all. The comment explicitly acknowledges
   this: PrintExtractor/PrintConfig handle the paging IDs, and
   PagingRegistration is a folder-parity facade. Same asymmetry as #1.

3. **Parser-side ambiguity noted in `PagingRegistration.kt` comment** —
   first-write-wins between `print` and `paging` owners means the owner label
   reported by `PropertyRegistry.allRegistered()` is load-order dependent.
   Acceptable per the comment, but should be tracked if coverage dashboards
   ever care about the owner column.

4. **No `Orphans` / `Widows`** in the `print` registration even though they
   appear in CSS Paged Media — the comment correctly notes they are owned by
   `typography/` via the Phase 6 tripwire. Not a divergence, just a pointer
   for whoever is auditing typography.

5. **`BookmarkTarget`** is in the IR + Android `print` registration + Web
   triplet + iOS Unsupported list, but **not in the prompt's property
   enumeration** (prompt lists `Bookmark{Level,Label,State}` only). Prompt-IR
   mismatch, worth confirming whether BookmarkTarget should count toward the
   print category's 11 or be scoped out.

## Verdict

- Scaffolding is in place on all three platforms under the canonical category
  paths (`print/`, `paging/`, `regions/`).
- Fixtures already exist and exercise every parser value-flavor documented in
  the registration comments.
- Per-property-triplet parity is **only fully realised on Web**. Android and
  iOS use collapsed facades. If Phase 12 requires strict triplet parity, those
  two platforms need the explosion work; if "no-op identity" is the bar,
  current state is sufficient.
- Executing `./test-all.sh` on the three fixtures would very likely produce
  clean SSIM ≥ 0.95 identity results across all platforms — nothing in these
  categories can visually diverge. Not run here (read-only).

## Relevant files

- IR: `/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/pedantic-bhabha/src/main/kotlin/app/irmodels/properties/{print,paging,regions}/`
- Fixtures: `/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/pedantic-bhabha/examples/properties/{print,paging,regions}/longtail.json`
- Android: `/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/pedantic-bhabha/testing/Android/app/src/main/java/com/styleconverter/test/style/{print,paging,regions}/`
- iOS: `/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/pedantic-bhabha/testing/iOS/StyleConverterTest/StyleEngine/{print,paging,regions}/`
- Web: `/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/pedantic-bhabha/testing/web/src/style/engine/{print,paging,regions}/`
