# Phase 12 audit — shapes + rhythm + table

Scope: 15 properties across three "mostly-identity-on-mobile" categories.
Fixture inputs (already present): `examples/properties/{shapes,rhythm,table}/longtail.json`.

## Runtime status

- iOS: `xcodebuild` fails repo-wide (`all-product-headers.yaml: No such file or
  directory`) — infra issue, unrelated to fixtures. SKIP_IOS.
- Web: `test-all.sh` aborts at line 400 on `WEB_PORT` under `set -u`
  (`WEB_PORT\u00a0: unbound variable`) — NBSP contamination in the variable
  name in test-all.sh. Report never generated, web images never collected.
- Android: captures ran (20/12/14 components rendered). A concurrent Phase 12
  worktree (spacing/transforms) overwrote `testing/report/` between my runs
  and snapshots; Android PNGs in `testing/audit/{cat}/snapshot/Android/` ended
  up being the other worktree's typography stills, not mine. Lock file was
  held only during test-all.sh invocation; no contention-safe snapshot
  happened because the script's own exit-before-manifest-write races with
  sibling runs.

Given those blockers, I pivoted to a **parser + applier code audit** — the
Phase 12 spec's explicit "most will be identity on mobile" expectation means
the load-bearing signal here is "does the parser accept the value and does a
platform applier exist", not SSIM.

## Pass / fail per sub-category

### shapes/ — **FAIL (1 of 5 properties)**
- `shape-outside` OK (parser + validator + Android `ShapeApplier` + web triplet)
- `shape-margin`, `shape-padding`, `shape-image-threshold` OK (validator-listed,
  parsers registered, web triplets present, Android/iOS treat as parse-only —
  acceptable identity behavior).
- **`shape-inside` — BROKEN.** Parser exists
  (`ShapeInsidePropertyParser.kt`) and is registered
  (`PropertyParserRegistry.kt:596`), but the property name is **missing from
  `CssPropertyValidator.kt`** (see the shapes block at lines 246–248, which
  lists `shape-outside/margin/image-threshold/padding` but not
  `shape-inside`). Every `shape-inside` declaration in a fixture is silently
  dropped with `[CSS Parser] Removed invalid properties: shape-inside`. Web
  has a full ShapeInside triplet at `testing/web/src/style/engine/shapes/`
  that can never be exercised.

### rhythm/ — **FAIL (all 5 properties)**
- All five `block-step*` properties parse (parsers in
  `BlockStepPropertyParsers.kt`, registered lines 560–564), but **none** of
  them appear in `CssPropertyValidator.kt` — no `block-step` string anywhere
  in the validator. Every `block-step*` declaration and the `block-step`
  shorthand is silently dropped at the validator.
- Confirmed at runtime: `[CSS Parser] Removed invalid properties:
  block-step-size` and `… block-step`.
- Android has no appliers (only a `RhythmRegistration.kt` that registers the
  names as `migrated` parse-only). iOS has a single-file RhythmExtractor/
  Applier that will never receive values. Web has full per-property triplets,
  also unreachable.

### table/ — **PASS**
- All five properties validator-listed (lines 191–192), parsers registered
  (table block), full Android `TableApplier.kt` (560 lines — real
  CompositionLocal-driven table container), iOS module, web triplets.
- Android runtime: table properties are only active when the IR produces a
  table container, which the SDUI fixture harness (one box per component)
  does not exercise. Identity rendering on our fixtures is expected and
  correct — the applier is gated behind a `Table()` composable call.

## Highest-impact finding

**Validator allowlist has two holes that silently kill 6 properties
(40% of this audit):** `shape-inside` and the entire `block-step*` family.
Fix: add them to `src/main/kotlin/app/parsing/css/properties/CssPropertyValidator.kt`
(shapes block around line 246 needs `"shape-inside"`; needs a new rhythm
block with `"block-step", "block-step-align", "block-step-insert",
"block-step-round", "block-step-size"`). Until then any downstream work
(iOS/web/Android appliers already written for these) is dead code from the
IR's perspective.

Secondary finding: `test-all.sh` has a non-ASCII character embedded in
`WEB_PORT` on (or near) line 400 which breaks every audit run under
`set -u`. Web capture has been silently unavailable for all Phase 12
longtail audits that use the default invocation. Grep the file for the
variable and replace the bad byte with an ASCII underscore.
