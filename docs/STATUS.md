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
  `BASELINE=1` gates against the 327 committed baseline PNGs in
  `tools/visual/baseline/`.
- **Wire contract** — the IR JSON is machine-checked against
  `schema/ir-v1.schema.json` + the normative spec in
  [`schema/spec/`](../schema/spec/); 12 golden fixtures are decoded by
  conformance tests on all four codebases (`node schema/conformance/run.mjs`).

## Coverage — two numbers, both true

| claim | number | source of truth |
|---|---|---|
| Registration coverage (triplet exists + registered) | **550 / 550 per platform** (Android · iOS · Web) | `node tools/visual/coverage-audit.mjs` → `tools/visual/COVERAGE.md` |
| Verified rendering coverage (SSIM ≥ 0.95, every variant, every platform pair) | **91 / 550 (~17%)** | converged audit campaign, round 40 (below) |

"Registered" means the triplet exists and claims the IR type; some
appliers are intentional no-op + TODO where no mobile analogue exists
(speech/, regions/, print/, …).

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

## Test suites

| suite | command | tests |
|---|---|---:|
| converter (Kotlin) | `./gradlew :converter:test` | 101 |
| web runtime (vitest) | `npm -w runtimes/web run test` | 949 |
| compose runtime (JUnit) | `(cd apps/android-harness && ./gradlew :runtime:testDebugUnitTest)` | 761 |
| swiftui runtime (XCTest) | `xcodebuild test -scheme StyleConverterRuntime -destination 'platform=macOS,variant=Mac Catalyst,arch=arm64'` | 226 |
| tooling (node --test) | `node --test tools/visual/*.test.mjs tools/titan/*.test.mjs` | 430 |
| IR conformance | `node schema/conformance/run.mjs --emit` | 12 goldens × 4 codebases |

## Roadmap

Static code **writers** (Compose / SwiftUI / Tailwind source from IR);
the **flat-IR v2** freeze with the slot/placement children contract
(`schema/spec/03-children.md`, `05-versioning.md`); repairing the known
v1 wire defects at that freeze; IR semantic model (roles/labels) to
unblock the accessibility criteria above.
