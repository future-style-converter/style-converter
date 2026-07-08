# Tier 12 — Snapshot stability across OS versions

Same fixtures rerun on multiple iOS / Android runtime versions. Each fixture
must pass within ±0.05 SSIM across versions.

## Phase 12a status (round 42 closeout)

**Cross-iOS-version slice: WORKING.** `node testing/os-matrix.mjs` detects
installed iOS Simulator runtimes, runs `test-all.sh` against an iPhone 17 Pro
on each, snapshots the captures into `testing/os-matrix-snapshots/<runtime>/`,
and computes pairwise per-fixture SSIM.

Last verified run on this machine (visual-test.json, 109 components):

| pair                       | fixtures | regressions (drift > 0.05) | min SSIM | max SSIM | result                |
|----------------------------|---------:|---------------------------:|---------:|---------:|------------------------|
| iOS-26.0 vs iOS-26.2       |     109  |                          0 |    1.000 |    1.000 | byte-identical (PASS) |

All 109 fixtures rendered identically across the iOS 26.0 → 26.2 patch bump.
That's a meaningful baseline: it proves the SwiftUI renderer + StyleEngine
are not picking up runtime-version-dependent behaviour for this codebase.

## What the original Tier 12 spec wanted

iOS 16 / 17 / 18 + Android API 24 / 30 / 34 / 35. This machine has only
iOS 26.0 + 26.2 and 0 Android AVDs. Phase 12a does the honest thing: run on
whatever IS installed, report the actual delta matrix, and mark missing
runtimes `not-installed` rather than pretending the runs happened.

Adding more runtimes is a one-time provisioning task:

```bash
# iOS — install older runtime via Xcode → Settings → Components, or:
xcodebuild -downloadPlatform iOS -buildVersion 18.5     # ~7 GB
# Android — create an AVD per API level:
sdkmanager "system-images;android-34;google_apis;arm64-v8a"
avdmanager create avd -n android-34 -k "system-images;android-34;google_apis;arm64-v8a"
```

After provisioning, re-run `node testing/os-matrix.mjs` — the script
auto-detects whatever's installed.

## Per-runtime status

| # | platform | os version | iPhone / AVD model | passing | regressions | status | notes |
|---|---|---|---|---:|---:|---|---|
| 1 | iOS | 16     | (any iPhone, runtime not installed) |  - |  - | not-installed | install via `xcodebuild -downloadPlatform iOS -buildVersion 16.x` |
| 2 | iOS | 17     | (any iPhone, runtime not installed) |  - |  - | not-installed | install via `xcodebuild -downloadPlatform iOS -buildVersion 17.x` |
| 3 | iOS | 18     | (any iPhone, runtime not installed) |  - |  - | not-installed | install via `xcodebuild -downloadPlatform iOS -buildVersion 18.x` |
| 4 | iOS | 26.0   | iPhone 17 Pro                     | 109 |  0 | **PASS** (round 42) | byte-identical with iOS 26.2 across 109 visual-test components |
| 5 | iOS | 26.2   | iPhone 17 Pro                     | 109 |  0 | **PASS** (round 42) | byte-identical with iOS 26.0 |
| 6 | Android | 24    | (no AVD installed)             |  - |  - | not-installed | `sdkmanager "system-images;android-24;..."` then `avdmanager create avd` |
| 7 | Android | 30    | (no AVD installed)             |  - |  - | not-installed | as above |
| 8 | Android | 34    | (no AVD installed)             |  - |  - | not-installed | as above |
| 9 | Android | 35    | (no AVD installed)             |  - |  - | not-installed | as above |

## How the runner works

1. **Detect installed runtimes** via `xcrun simctl list devices --json` (iOS)
   and `emulator -list-avds` (Android).
2. **Per iOS runtime**: shutdown all simulators (so `test-all.sh` doesn't
   pick the wrong one), boot the chosen UDID, run `test-all.sh` with
   `SIM_UDID=<udid> SKIP_ANDROID=1 SKIP_WEB=1` (web is host-Chrome-based and
   doesn't vary across iOS runtimes; mixing iOS-version + Android-API in the
   same matrix would confound the signal).
3. **Snapshot** `testing/iOS/screenshots/` into
   `testing/os-matrix-snapshots/<runtime-label>/` so the next iteration
   doesn't overwrite it.
4. **Cross-runtime SSIM** (n choose 2): for every fixture present in both
   runtimes, score with ssim.js. Drift > 0.05 = regression.

The new `SIM_UDID` env var was added to `test-all.sh` in this round so the
matrix runner can disambiguate same-name devices across different runtimes
(iPhone 17 Pro exists on both iOS 26.0 and 26.2 with distinct UDIDs).

## Re-running

```bash
# Full run (~90s for 2 runtimes × Alert.json; ~3min for visual-test.json).
node testing/os-matrix.mjs                                       # default: Alert.json
OS_MATRIX_FIXTURE=examples/visual-test.json node testing/os-matrix.mjs

# Compare-only (skip the iOS reruns, just re-score the cached captures —
# ~2s, useful when iterating on the SSIM logic).
node testing/os-matrix.mjs --compare-only
```

Output: `testing/os-matrix-report.json` (per-runtime status, per-pair deltas,
per-fixture SSIM). Both report and `testing/os-matrix-snapshots/` are
gitignored (regenerated each run).

## Pending work (Phase 12b)

- **Android AVD runner** (~3-4h once an AVD is detected): mirror the iOS
  loop but use `emulator -avd <name>` + `adb wait-for-device` instead of
  `xcrun simctl boot`. The detection is wired; the actual boot+test+snapshot
  loop is the missing piece.
- **Older iOS runtime installation** (one-time, ~30 min per version): grants
  Phase 12a the full iOS 16/17/18 matrix without code changes.
- **CI threshold tuning**: `SSIM_DRIFT_THRESHOLD = 0.05` is conservative for
  patch versions but might be too strict for major-version jumps (e.g.
  iOS 17 → 18 SwiftUI typography changes). Revisit when first iOS 16/17/18
  data lands.
