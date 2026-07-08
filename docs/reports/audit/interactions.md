# Phase 12 Audit — interactions

READ-ONLY audit. Scope: the 9 IR properties under
`src/main/kotlin/app/irmodels/properties/interactions/`
(`Caret`, `CaretShape`, `Cursor`, `Interactivity`, `PointerEvents`, `Resize`,
`ScrollBehavior`, `TouchAction`, `UserSelect`).

## Inventory

- **IR properties**: 9 `*Property.kt` files present.
- **CSS parsers**: all 9 present under
  `parsing/css/properties/longhands/interactions/`, **except `interactivity`**
  — the parser rejects it: the test-all log shows
  `[CSS Parser] Removed invalid properties: interactivity` on every component
  that uses it. `Interactivity_Auto` / `Interactivity_Inert` in the fixture
  fall through to the default component (width/height/bg only).
- **Fixtures**: `examples/properties/interactions/longtail.json` (37 components)
  covers every variant listed in the prompt's edge-case grid. **No
  `audit-phase12.json` exists and, per the READ-ONLY constraint, was not
  authored.** `longtail.json` is sufficient for the audit: `cursor` (auto,
  pointer, not-allowed, grab, ew-resize, zoom-in, `url() , pointer` fallback);
  `pointer-events` (auto, none, all, visiblePainted, bounding-box);
  `user-select` (auto, none, text, all, contain); `touch-action` (auto, none,
  pan-x, pan-y pinch-zoom, manipulation); `resize` (none, both, horizontal,
  vertical, block, inline); `caret` (shape-only, colour-only, both);
  `caret-shape` (auto, bar, block, underscore).
- **Baselines**: **zero** baselines match `cursor|pointer|touch|user.select|
  resize|caret|interactiv` in `testing/baseline/`. Category has never been
  baselined — WAI for a no-visible-effect category.

## Platform triplet structure (mixed, non-canonical)

The per-IR-property contract (one `{Property}Config/Extractor/Applier` per
property) is **half-respected**:

| Platform | Shape | File count | Verdict |
|---|---|---|---|
| **Web** | Per-property triplet + `_dispatch.ts` | 8 properties × 3 files = 24 TS + dispatch (all ≤7 lines) | Canonical (though `ScrollBehavior` lives under `scrolling/`, not here — OK) |
| **Android** | Category-level `InteractionConfig/Extractor/Applier` + a `SpatialNavigation{Config,Extractor}` + `InteractionsRegistration` | 6 files, 186–217 lines each | **Non-canonical**: one monolith covers all 9 properties |
| **iOS** | Single merged `InteractionsConfig/Extractor/Applier.swift` | 3 files, 17/33/26 lines | **Non-canonical and effectively a no-op** — the applier is `static func contribute(_ cfg) { _ = cfg }` with a TODO block |

Contract violations (CLAUDE.md "Per-property contract"):

1. **iOS**: entire category is a documented no-op. The file header
   (`InteractionsApplier.swift`) enumerates *why* each property is skipped on
   iOS (no cursor on iPhone, no CSS-equivalent for touch-action axis policy,
   etc.). This is honest but the triplet should be split per property even
   when each applier is an identity — otherwise coverage by `ls` lies.
2. **Android**: `InteractionApplier` only wires `visibility` and
   `backfaceVisibility` (neither of which are interactions-category IR
   properties — they belong to `effects/` and `appearance/`). The 9 actual
   interactions properties are extracted into `InteractionConfig` but
   **none reach the Modifier chain**. See the doc comment listing "Cursor:
   Desktop only", "UserSelect: Android handles text selection differently",
   "TouchAction: Handled by gesture modifiers" — all of which are deferred.
3. **Web**: each applier is a correct 1-line `{ cssProp: value }` emit —
   the only platform that actually lands the styles in the DOM (where they
   do nothing visible on a static render either, but the CSS declaration is
   present in computed style).

## `interactivity` parser gap

The `interactivity` CSS property (CSS Inert module) is **rejected by the
parser** despite having `InteractivityProperty.kt` in the IR and
`InteractivityConfig/Extractor/Applier.ts` on the web. The CSS-parser
longhand registration is either missing or matches the wrong keyword set. The
fixture's `Interactivity_Auto` / `Interactivity_Inert` components lose that
property before it ever reaches the IR. This is the only *substantive* bug
the audit found — everything else is cosmetic or WAI.

Fix target: `src/main/kotlin/app/parsing/css/properties/longhands/
interactions/InteractivityPropertyParser.kt` (or its registration entry in
`PropertyParserRegistry.kt`). Out of scope for this READ-ONLY pass.

## Test-all run (blocked by unrelated iOS build failure)

`./test-all.sh examples/properties/interactions/longtail.json` aborted during
the iOS capture stage with `xcodebuild failed — (8 failures)`. The log was
subsequently truncated on disk (`/tmp/xcodebuild.log` is 0 bytes at report
time), but the file list shown before the abort covered
`background/*`, `effects/blend/*`, `borders/image/*`, `borders/radius/*`,
`borders/sides/*`, and `typography/decoration/*` — **none of them under
`interactions/`**. The iOS interactions triplet itself is three tiny files
that compile in isolation; the 8 failures are a **pre-existing cross-category
Swift compile issue** unrelated to this audit. Because the run aborted before
iOS captures and the Android/web capture phases, the report manifest
(`testing/report/manifest.json`) still reflects the previous run
(`typography/audit-phase12.json`, generated 2026-04-18T07:30Z). No new
screenshots, diffs, or baselines were produced.

**Recommended follow-up (separate task)**: re-run with the iOS build fixed,
or invoke `test-all.sh` with an env flag to skip iOS so Android↔web pairs
still get captured for this no-op category.

## Expected render outcome (once test-all runs)

Given the category is visually inert and the fixtures all carry identical
`width/height/background-color`, every pair should be SSIM ≥ 0.99 for every
variant. The only surprise would be a layout-shift from the Android
visibility/backface monolith accidentally firing on `interactivity` (it
can't — parser strips it) or web's presence of `touch-action: none` /
`user-select: none` affecting headless-chrome paint (it won't on a static
snapshot).

## Summary — coverage matrix row candidate

| Platform | Extract | Apply | Notes |
|---|---|---|---|
| Web | 8 of 9 (no `Interactivity` because parser drops it) | identity CSS emit | ✓ canonical |
| Android | all 9 via `InteractionExtractor` | **none of the 9** actually contribute to Modifier | config-only |
| iOS | all 9 via `InteractionsExtractor` | explicit no-op (`_ = cfg`) | documented skip |

The category is effectively **N/A on mobile** for visible rendering (matches
the CLAUDE.md "Not Applicable to Mobile" list for `Cursor` / `Resize`), and
behavioural-only on web. The realistic "done" bar for Phase 12 is:

1. Fix the `interactivity` parser drop.
2. Split the Android monolith into 9 per-property triplets, most of which
   are identity-appliers with a one-line "no mobile analog" comment — this
   restores `ls`-auditability.
3. Split the iOS monolith likewise (9 identity appliers, each citing the
   SwiftUI API that *would* apply if we wired it — `.allowsHitTesting` for
   PointerEvents=none, `.textSelection(.disabled)` for UserSelect=none).
4. Commit a baseline from `longtail.json` once test-all runs clean so
   regressions here become detectable.

No failure PNGs to copy (no captures were produced). No SSIM table to emit.
