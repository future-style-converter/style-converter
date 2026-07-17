# Project status

One page, honest. This file replaces the retired `docs/reports/` campaign
tree (3,500+ per-round audit reports); the durable conclusions live here,
the full history lives in git history.

## What works

- **Converter** (`converter/`, Gradle `:converter`) — parses CSS
  declarations (JSON envelope in) into the typed IR and emits it as JSON
  (`--to ir`, the only output target today).
- **Three runtime style engines** render that IR live:
  `runtimes/web` (React → DOM/CSS), `runtimes/compose` (Jetpack Compose),
  `runtimes/swiftui` (SwiftUI). Every property ships as a
  Config/Extractor/Applier triplet at the same canonical path on all three.
- **Visual harness** — `./test-all.sh` renders any fixture on all three
  platforms and compares captures with SSIM;
  `BASELINE=1` gates against the 363 committed baseline PNGs in
  `tools/visual/baseline/`.
- **Wire contract** — the converter emits IR v2 by default (the flat-list
  slot/placement wire); the JSON is machine-checked against
  `schema/ir-v2.schema.json` (`schema/ir-v1.schema.json` is the deprecated
  legacy contract for the `--emit-ir v1` compat wire) + the normative spec
  in [`schema/spec/`](../schema/spec/); 31 golden fixtures (12 v1 + 19 v2)
  are decoded by conformance tests on all four codebases
  (`node schema/conformance/run.mjs`).

## Coverage — three numbers, all true

| claim | number | source of truth |
|---|---|---|
| Registration coverage — triplet exists + claims the IR type (a string-presence facade; not native rendering) | **550 / 550 per platform** (Android 550 / 550 · iOS 550 / 550 · Web 550 / 550) | `node tools/visual/coverage-audit.mjs` (`registered:`) → `tools/visual/COVERAGE.md` |
| Real-applier floor — a dedicated `<Name>Applier` file exists (under-counts grouped appliers) | **Android 18 / 550 · iOS 73 / 550 · Web 508 / 550** | `coverage-audit.mjs` (`real:` line) |
| Verified rendering coverage — SSIM ≥ 0.95, every variant, every platform pair | **91 / 550 (~17%)** | converged audit campaign, round 40 (below) |

"Registered" is only a string-presence facade — a triplet exists and
claims the IR type; it does NOT mean a dedicated applier renders the
property natively. The stricter real-applier floor counts a dedicated
`<Name>Applier.<ext>` file per property; it under-counts grouped appliers
(one file — e.g. Compose `LayoutApplier.kt`, iOS `FlexboxApplier.swift`,
web `ScrollMarginApplier.ts` — renders many properties but its basename
matches at most one IR name, so the raw dedicated-applier file counts
Android 59 · iOS 115 · Web 522 sit above the per-property floor). Some
registered appliers are intentional no-op + TODO where no mobile analogue
exists (speech/, regions/, print/, …).

### The verified-coverage headline (audit campaign, converged round 40)

A 70-round auditor-driven campaign drove the per-property tracker from a
dishonest "548/548 passing" (~85% degenerate fixtures — empty boxes
matching empty boxes) to a substantively honest classification:

| | Start (round 0) | Converged (round 40) |
|---|---|---|
| Total rows | 548 | 550 |
| Passing | 548 (100%) | **91 (16.5%)** |
| Blocked-platform | 0 | 419 |
| Failing | 0 | 3 |
| Exhausted | 0 | 37 |
| **Honest meaningful coverage** | **unstated** | **~91/550 (~17%)** |

- **Passing (91)** — SSIM ≥ 0.95 on every value variant on every
  platform pair against committed baselines.
- **Blocked-platform (419)** — cannot manifest in static-mobile
  fixtures: needs animation state, scroll context, real tables/lists,
  aural rendering, paged media, cursor/input state, etc. Each row
  carried a root-cause note.
- **Exhausted (37)** — no meaningful visual test exists.
- **Failing (3)** — known real cross-platform divergences (e.g. Android
  `Modifier.scale()` clips to parent bounds at scale ≥ 1.5 where
  iOS/web extend beyond).

The campaign also produced 31 gold-standard fixture rewrites and 4 real
renderer fixes (iOS mask-image:none short-circuit, Android
gradient-brush size, iOS FilterApplier .thinMaterial removal, a
test-all.sh octal-arithmetic crash).

## Testing-tier record

Where each testing axis landed when the campaign closed. Tiers whose
bespoke drivers were retired in the repo prune keep their last verified
result here as the historical record.

| # | axis | status |
|---|---|---|
| 1 | Variant depth (every parser branch) | partial — the 91/419/37/3 classification above |
| 2 | Category-pair combos (33×33) | partial — 526/561 cells passed, but ~99 used no-op defaults (shallow); needs combo-fixture redesign |
| 3 | Realistic components | partial — 8/15 passing; 6 share an Android flex+text+border-radius root cause, 1 iOS font-metrics |
| 4 | Keyframe snapshots (t=0/50/100%) | partial — 14/15 passing; scale keyframe blocked on the Android clip-to-parent gap |
| 5 | Interaction states (:hover/:focus/:active) | web slice done — 90/90 captures via `tools/visual/interaction-states.mjs`; captures browser-default state changes only (IR has no pseudo-class support yet); iOS/Android harnesses were stubs |
| 6 | Layout under constraint changes | complete (5/5) |
| 7 | Parser fuzz (malformed CSS never panics) | complete (15/15) |
| 8 | SSIM history tracking | retired — history tool pruned; BASELINE=1 regression gating supersedes it |
| 9 | Real-page conversion | retired — best result: twitter 73%, tailwind 41% (0 deep failures, mean SSIM 0.952); adapter + scraped CSS dumps pruned |
| 10 | Performance benchmarks | retired — web capture batching fix landed (test-all.sh keeps it); bench driver pruned |
| 11 | Accessibility (WCAG 2.1 AA) | web color-contrast slice done — 15/15 fixtures scored via `tools/visual/a11y-audit.mjs`, 2 real AA contrast failures found; 7 other criteria blocked on missing IR semantics (no IRRole/IRAriaLabel/IRAlt) |
| 12 | OS-version snapshot matrix | retired — last result: 109/109 fixtures byte-identical across iOS 26.0 ↔ 26.2; driver pruned |

Still-live harnesses: `tools/visual/smoke.sh` (unit tests + Tier 5 +
Tier 11 + BASELINE=1 + baseline classifier), `tools/titan/` (WPT-corpus
pipeline), and the full `./test-all.sh` visual pipeline.

### WPT fidelity — composed 3-platform corpus

The `tools/titan/` pipeline diffs each WPT test rendered on all three
runtimes against the **same** upstream Chromium browser-ref (composed
per-test capture, SSIM). Two committed, hand-pinned snapshots (the run
dirs + WPT corpus are gitignored):

- `tools/titan/results/smoke-latest.json` — 33-test gated smoke: web 0.935
  (16/33 ≥ 0.95), iOS 0.916 (13/33), Android 0.918 (14/33).
- `tools/titan/results/corpus-v1.json` — first real **multi-section**
  corpus (7 sections × 12 bucket-A tests = 84, via `section-runner.sh
  --all-platforms`): web 0.915 (40/82 ≥ 0.95), iOS 0.895 (29/82), Android
  0.892 (28/75). Honest holes it surfaced: `css-break` background-images
  don't render in fragmentation contexts (SSIM 0.16–0.38), `css-flexbox`
  mobile hits 0/12 ≥ 0.95, and one `css-transforms` fixture
  (`3d-rendering-context-and-z-ordering-003`) wedges the Android composed
  capture. (That last one made `css-transforms` Android read 5/12 in this
  snapshot: the wedge cascade-timed-out the 6 fixtures queued behind it —
  since fixed by restart-on-timeout recovery in the native feeders, so a
  re-feed now yields 11/12.) Full 10,944-test bucket-A remains the
  long-horizon target; this is a stratified sample.

## Test suites

| suite | command | tests |
|---|---|---:|
| converter (Kotlin) | `./gradlew :converter:test` | 135 |
| web runtime (vitest) | `npm -w runtimes/web run test` | 959 |
| compose runtime (JUnit) | `(cd apps/android-harness && ./gradlew :runtime:testDebugUnitTest)` | 770 |
| swiftui runtime (XCTest) | `xcodebuild test -scheme StyleConverterRuntime -destination 'platform=macOS,variant=Mac Catalyst,arch=arm64'` | 248 |
| tooling (node --test) | `node --test tools/visual/*.test.mjs tools/titan/*.test.mjs` | 510 |
| IR conformance | `node schema/conformance/run.mjs --emit` | 31 goldens (12 v1 + 19 v2) × 4 codebases |

## Roadmap

Static code **writers** (Compose / SwiftUI / Tailwind source from IR); a
future leaf-strictness revision closing the still-permissive per-property
`data` leaves; retiring the deprecated `--emit-ir v1` path after its
one-release deprecation window; IR semantic model (roles/labels) to
unblock the accessibility criteria above. The **flat-IR v2** wire already
shipped (PR #30) — it is the current default, with the slot/placement
children contract frozen in `schema/spec/03-children.md` +
`05-versioning.md` and the known v1 wire defects repaired at that freeze.
