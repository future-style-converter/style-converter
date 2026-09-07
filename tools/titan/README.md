# TITAN — the WPT conformance runbook

TITAN scores the three runtime style engines (web / Android-Compose /
iOS-SwiftUI) against the Web Platform Tests CSS reftest corpus. For each
test: extract the DOM + CSS into an IR fixture, render it on all three
platforms, diff every capture against a frozen headless-Chromium reference
PNG, and record a per-cell verdict (`wptPass`). Waves 1–49 of the applier
campaign ran this as the device gate; the current snapshot is
`results/corpus-v6-15.json` (web 1205/1379, iOS 1081/1366, Android
1058/1366; 4111 scored cells).

Everything in this file is cross-checked against the code it names
(retro lane R8b, 2026-09-04). Where a number comes from a run artifact the
run is named. The previous README described only the Phase-0 fetch +
bucket step and is gone.

## Layout (what is actually here)

```
tools/titan/
├── README.md                    this runbook
├── WPT_REF                      one-line pin: 9b5435e5… on epochs/three_hourly (re-pin quarterly)
├── SMOKE_TESTS                  the Phase-1 100-test list run-titan.sh --smoke reads
├── fetch-wpt.sh                 partial-clone + sparse-checkout of WPT → tools/wpt/ (gitignored, ~600 MB)
├── bucket-wpt.mjs               A/B/C convertibility classifier → wpt-buckets.json (gitignored; <60 s)
├── extract-fixture.mjs          test HTML → fixtures/wpt/<section>/<stem>.json (+ __ref.json)
│   ├── post-load-extract.mjs        POST_LOAD_EXTRACT=1: settle script-mutated tests in a browser first
│   ├── bidi-bake.mjs                BIDI_BAKE=1: measured visual order for RTL tests
│   ├── view-transition-bake.mjs     VT_BAKE=1
│   └── attr-bake / counter-bake / counter-style-bake / generated-content-bake /
│       widget-appearance-bake / counter-functions / counter-style-table   (static bakes)
├── capture-browser-ref.mjs      frozen refs → tools/wpt/refs/<sha>/<CANVAS_REV>/<section>/<stem>.png
├── build-combined-fixture.mjs   per-section combined fixture  (fixtures/wpt/_section-<sec>.json)
├── split-combined-ir.mjs        combined IR → per-test IR docs (runs/<id>/sections/<sec>/per-test-ir/)
├── feed-android.mjs             native INBOX feeders: push per-test IR, wait for the composed PNG, pull it
├── feed-ios.mjs
├── feed-lib.mjs                 shared: Android arg parsing (feed-ios has its own parseArgs mirroring it), expected names, asset hops, --wpt-dir preflight
│   ├── svg-preraster.mjs            host-side SVG → PNG for the natives (default ON, TITAN_SVG_PRERASTER=0)
│   ├── woff-to-ttf.mjs              host-side WOFF1 → sfnt for Android (default ON, TITAN_WOFF_TRANSCODE=0|force)
│   ├── mono-pin.mjs                 `monospace` → DejaVu Sans Mono pin (default ON, TITAN_MONO_PIN=0)
│   └── noto-pilot.mjs               Noto delivery proof (OFF; TITAN_NOTO_PILOT=1) — parked, rejected by A/B
├── inject-wpt-block.mjs         THE SCORER: browser-ref diffs, vetoes, exclusion families, wpt: block
│   ├── wpt-not-applicable.mjs       the Rule-N tagger the exclusion families key on
│   ├── novel-ink.mjs / novel-ink-palette.mjs   the disarmed novel-ink veto (TITAN_NOVEL_INK_VETO=1)
│   └── safe-name.mjs                the ONE filename sanitiser (safe, fixtureStem) every producer/consumer shares
├── section-runner.sh            per-section orchestrator — the unit of the gate (Steps 1–7.5 below)
├── provision-devices.sh         boots + installs the device pool; writes /tmp/titan-device-pool/provisioned-*
├── run-titan.sh                 Phase-1 single-process smoke orchestrator (rides test-all.sh; legacy path)
├── aggregate-sections.mjs       runs/<id>/sections/*/manifest.json → runs/<id>/manifest.json
├── render-titan.mjs             dashboard → tools/visual/report/titan.html
├── red-square-census.mjs        the committed red-square census (see "Instrument notes")
├── fonts/mono-pin/              DejaVuSansMono(.ttf, -Bold.ttf) + LICENSE — the mono-pin faces
├── results/                     43 corpus-v*.json snapshots + smoke-latest, webmap-v1(+classification),
│                                wave37-W5-decisions, wave38-N6-direction, wave38-N7-native-tails
├── runs/                        gitignored run dirs — runs/<runId>/sections/<section>/… (see below)
└── *.test.mjs                   30 files; `node --test tools/titan/*.test.mjs`
```

Gitignored and regenerated, never committed: `tools/wpt/` (the corpus and
its `refs/`), `tools/titan/wpt-buckets.json`, `fixtures/wpt/`,
`tools/titan/runs/`, `tools/titan/investigations/`.

## First-time setup (or after `git clean`)

```bash
bash tools/titan/fetch-wpt.sh        # tools/wpt/ at the WPT_REF pin (~600 MB on disk; its header's "~1.5 GB" is stale)
node tools/titan/bucket-wpt.mjs      # tools/titan/wpt-buckets.json (buckets.A/B/C, bySpecSection, notApplicable)
```

The bucket index is fully derived from the corpus; re-running is idempotent.
`WPT_REF=<sha>` overrides the pin per run. The quarterly re-pin recipe:
`git ls-remote --heads https://github.com/web-platform-tests/wpt
epochs/three_hourly`, update `WPT_REF` (keep its comment header), re-fetch,
re-bucket, and explain any A→C / B→C moves in the PR.

## One section, end to end (`section-runner.sh`)

```bash
tools/titan/section-runner.sh <section> [--max-tests N] [--run-id ID] [--web-only|--all-platforms] [--foreground]
```

`<section>` is a `bySpecSection` key (`css-flexbox`, `css-grid`, …); the
test list is bucket-A filtered by section, capped by `--max-tests`. Steps:

| step | what | writes (under `runs/<runId>/sections/<section>/`) |
|---|---|---|
| pre-flight | corpus + buckets present; **disk ≥ `TITAN_MIN_FREE_GB` (10) or exit 2**; a 30 s **low-disk watchdog** aborts the section (exit 2, `DISK_ABORT` marker) below `TITAN_ABORT_FREE_GB` (8) | `DISK_ABORT` (only on abort) |
| 1 | `extract-fixture.mjs` with `POST_LOAD_EXTRACT=1 BIDI_BAKE=1 VT_BAKE=1` pinned inline (never inherited from the shell) | `extract.log`, `fixtures/wpt/<section>/*.json` |
| 2 | `capture-browser-ref.mjs` (cached per sha + canvas rev) | `browser-ref.log`, refs under `tools/wpt/refs/…` |
| 3 | `build-combined-fixture.mjs` | `fixtures/wpt/_section-<section>.json` |
| 4 | gradle `:converter:run … --to ir` | `out/tmpOutput.json`, `gradle-convert.log` |
| 5 | isolated vite (port hashed from the section name into 3100–3299) + `WPT_MODE=1 WPT_COMPOSED=1` web capture | `web/`, `screenshots/`, `vite.log`, `capture.log` |
| 5b | `--all-platforms` only: split into per-test IR, acquire one Android + one iOS **pool slot** (mkdir-atomic, stale-healed, 2 h cap), run both feeders CONCURRENTLY with `--composed --wpt-dir tools/wpt`, release slots | `per-test-ir/`, `android-screenshots/`, `ios-screenshots/`, `feed-*.log` |
| 6 | `compare-screenshots.mjs --no-cross-platform-gate` (exit 3 = non-sRGB capture → section exit 2) | `manifest.json` (v3), `report/`, `compare.log` |
| 6.5 | skeleton `wpt` block with `incomplete: true` (recovery beacon) | `manifest.json` |
| 7 | `inject-wpt-block.mjs` under `env $INJECT_ENV` — `TITAN_REQUIRE_ALL_COLUMNS=1` for `--all-platforms`, `SKIP_IOS=1 SKIP_ANDROID=1` for `--web-only` | `manifest.json` (v4) |
| 7.5 | verify the `wpt` block landed; one recovery re-inject otherwise | |
| summary | classifier histogram; **`NATIVE_SHORT` gate** | exit code |

**Exit codes** — `0` full success · `1` a stage failed OR (`--all-platforms`)
a native column is short/absent (`NATIVE_SHORT`; the manifest IS written
first, for diagnosis) · `2` infra (missing corpus/tools, lock contention,
disk preflight/watchdog).

**`NATIVE_SHORT`** (retro R8b, finding A9#1) is set by: no free slot for a
platform; a failed `split-combined-ir`; a missing `per-test-ir/`; a feeder
exiting non-zero; a native dir whose PNG count ≠ the per-test doc count;
inject exiting 3 under `TITAN_REQUIRE_ALL_COLUMNS` — a whole column at
zero, or (retro R13's per-test parity, `missingCells`) a PARTIAL column: a
native cell absent or error-shaped where the web-ref scored.
Before this the same conditions were `warn` lines, the runner exited 0, and
the missing cells silently shrank that platform's denominator — a missing
capture is neither pass nor fail. Recovery: the single-fixture refeed below,
then re-run inject.

Locks: `/tmp/titan-section-<section>.lock` (per section, stale-healed) and
`/tmp/titan-device-pool/<platform>/<device>.lock` (per device). Different
sections run in parallel; the same section twice does not.

## The device gate (the wave recipe, as code + the remaining prose)

What `docs/BACKLOG.md` "Operational recipes → Gate script" prescribes, with
the parts that are now enforced by tracked code marked ✓:

1. Tree: `campaign/applier-campaign` at `neworigin/dev`, `git status` clean.
2. ✓ Disk ≥ 10 G — `section-runner.sh` refuses below `TITAN_MIN_FREE_GB`
   and aborts mid-run below `TITAN_ABORT_FREE_GB`. Pruning that works:
   `xcrun simctl delete unavailable`, delete Shutdown sims except the seed,
   prune old `tools/titan/runs/*`, DerivedData, then
   `tmutil thinlocalsnapshots / 21474836480 4` (APFS parks freed space in
   local snapshots; `df` barely moves until you thin them).
3. Devices quiet: no emulators attached, only the seed iPhone booted
   (`0BB986A6-1EAD-4916-9276-079235324DA1`); `rm -f
   /tmp/titan-device-pool/provisioned-*`.
4. `node tools/titan/bucket-wpt.mjs` if `wpt-buckets.json` is missing.
5. `tools/titan/provision-devices.sh [--android 2] [--ios 3]` — builds
   both apps once, launches extra emulators as `-read-only -no-window
   -gpu swiftshader_indirect` instances of one AVD, ✓ boots existing
   Shutdown `titan-pool-*` sims before creating new (uniquely named) ones,
   installs + `pm clear`s on every Android device (API-36.1 reinstall
   wedge), writes the `provisioned-*` markers. `--restart-fleet` is the only
   path that kills emulators this script did not launch.
6. For each of the 30 sections — `CSS2 css-anchor-position css-backgrounds
   css-break css-cascade css-color css-contain css-counter-styles
   css-display css-flexbox css-gaps css-grid css-images css-lists
   css-masking css-multicol css-overflow css-position css-pseudo css-sizing
   css-tables css-text css-text-decor css-transforms css-ui css-values
   css-view-transitions css-writing-modes filter-effects selectors` —
   `tools/titan/section-runner.sh <section> --all-platforms --max-tests 48
   --run-id wave<N>-final --foreground` (depth-48 = 30 × 48 sampled
   bucket-A tests). Prose still: a 50-min per-section watchdog that kills,
   reprovisions and retries the section once, then aborts the gate.
   ✓ A section that exits 1 with `NATIVE_SHORT` is a delivery failure —
   refeed, do not average.
7. `node tools/titan/aggregate-sections.mjs tools/titan/runs/wave<N>-final`
   → `runs/wave<N>-final/manifest.json`; `node tools/titan/render-titan.mjs
   --run wave<N>-final` for the dashboard.
8. The 327-net: `BASELINE=1 ./test-all.sh fixtures/visual-test.json`
   (exit 3 non-sRGB · 4 unledgered divergence · 5 stale ledger line — DELETE
   it · 6 spec oracle).
9. Score (idiom below), diff per section against the previous
   `results/corpus-v6-<n>.json`, diagnose every LOST cell, write the new
   snapshot.

Reproduce line carried by every snapshot (`reproduce` field):
`node tools/titan/bucket-wpt.mjs; rm -f /tmp/titan-device-pool/provisioned-*;
boot one iPhone sim; provision-devices.sh; then per section:
POST_LOAD_EXTRACT=1 BIDI_BAKE=1 VT_BAKE=1 section-runner.sh <section>
--all-platforms --max-tests 48 --run-id wave<N>-final --foreground`.

## The scorer idiom (how every number is counted)

A CELL is one (test, platform) browser-ref diff. It is SCORED iff it carries
a numeric `ssim` and is not `scoreExcluded`; it PASSES iff `wptPass === true`.
Verbatim, against a section or aggregated manifest:

```js
for (const r of Object.values(manifest.wpt.results)) {
  for (const key of ['web-ref', 'ios-ref', 'android-ref']) {
    const x = r.browserRef?.diffs?.[key];
    if (!x || typeof x.ssim !== 'number' || x.scoreExcluded) continue;   // not a scored cell
    measured[key]++;
    if (x.wptPass === true) passing[key]++;
  }
}
```

`wptPass` (`inject-wpt-block.mjs computeWptPass`): `ssim ≥ 0.95` OR the
test's own `<meta name=fuzzy>` budget is met (`wptFuzzyMatch`, computed as
an exact per-channel pass in WPT-native units) — VETOED by `presenceFailed`
(semantic presence vs the white canvas), `coverageRatioFailed` (ink
coverage max/min > 2), `colorFailed` (`colorDivergent` histogram-KL > 0.1
with ΔE ≥ 2.3), and `novelInkFailed` only when `TITAN_NOVEL_INK_VETO=1`
(shipped DISARMED: 66.7 % / 16.7 % recall, BACKLOG obligation #4). The
`divergence` label on each cell is TRIAGE ONLY — it never feeds `wptPass`.

A PASS is not a correct render. Wave-49 lane I1 opened 78 randomly drawn
passing cells by eye and found 27 visibly wrong (BACKLOG obligation #3); the
committed red-square census below is the one slice of that class a rule
reaches. Look at the PNG against the frozen ref before claiming a fix.

## Snapshot conventions (`results/corpus-v6-<n>.json`)

One file per gated wave, same shape every time (verified on `corpus-v6-15`):

| field | meaning |
|---|---|
| `_snapshot` | `corpus-v6.<n>`; the major bumps are instrument boundaries, each named in its own `_note` (v4 the WHITE-CANVAS boundary; v5 the IMAGE-PAD boundary — the ref canvas contract fix; v6 the DEPTH-48 boundary — 48 bucket-A tests per section on all three platforms) |
| `_note` | the full wave story: headline, mechanisms, honest losses, prediction misses, instrument events |
| `artifact` | the run dir the numbers were computed from (`tools/titan/runs/<runId>`, gitignored) |
| `wptRef`, `devSha`, `date` | corpus pin, tree, date |
| `bucket`, `sampling` | `A`; `30 sections x 48 bucket-A tests (depth-48)` |
| `passThreshold`, `criterion` | 0.95 and the prose of `computeWptPass` + the exclusion families in force |
| `canvasBoundary` | the ref canvas contract (`CANVAS_REV`, currently `white-black-ink-font-lh-imgpad-htmlpins`) |
| `totals`, `sections` | `{web,ios,android: {passing, measured}}` overall and per section, by the idiom above |
| `reproduce` | the exact command line |

Deltas attribute against the MOST RECENT snapshot only, never across the
PR-#126 instrument change (BACKLOG). `results/smoke-latest.json` is the
Phase-1 100-test smoke; `webmap-v1*.json` the web-only full-bucket-A map
(`runs/wave35-webmap`); `wave37-W5-decisions.json` and `wave38-N6/N7` are
decision records the exclusion refusals cite.

## Exclusion families and the refusal list (`inject-wpt-block.mjs`)

Every family is an exported, frozen set with a unit pin on its exact
membership. The tags come from `wpt-not-applicable.mjs` (Rule N) and ride
`notApplicableTags` on each result.

| set | members | scope | re-admission |
|---|---|---|---|
| `SCORE_EXCLUDED_TAGS` | `requires-bundled-asset` | whole test | delivery-aware: `lossyReasons` corroborate the tag |
| `EXTRACTION_WALL_TAGS` | `requires-script-mutation` (Rule 4), `requires-script-driven-scroll` (Rule 18), `requires-anchor-positioning-runtime` (Rule 40) | whole test | `postLoadExtracted` / `structureExtracted` stamps |
| `FONT_FACE_WALL_TAGS` | `requires-font-face` (Rule 15) | whole test | `fontFacesDelivered` (the wave-35 asset hop) |
| `REF_UNACHIEVABLE_TAGS` | `browser-ref-divergent` (Rule 42) | whole test | none — Chromium does not reproduce its own ref; fix = re-render refs, delete the tag |
| `NATIVE_FONT_PARITY_TAGS` | `requires-non-latin-font-parity` (Rule 43) | `ios-ref` + `android-ref` only, and only for tests in `NATIVE_FONT_PARITY_REFUSED_TESTS` (wave-48 W2 per-test cut; 10 tests after retro R13 unexcluded three non-font residuals); stamps `scoreExcluded: 'native-font-parity'` | a test not on the per-test list is scored normally |
| `REFUSED_EXCLUSION_TAGS` | `requires-print-medium` (Rule 5), `requires-view-transitions` (Rule 16) | — | these were MEASURED and REJECTED as exclusions (precision 0.719 for the VT family); they must stay scored, and the pin fails if they ever enter a family |

Rule 44 (`requires-grid-lanes`, wave-37 W1) is a tagger rule, not an
exclusion. `scoreExcluded` is truthy in every family (a string for the
per-platform one) so every aggregator filters it with one test.

## Feeders (`feed-android.mjs`, `feed-ios.mjs`)

Both never boot a device. Flags, from `parseArgs`:

| flag | android | ios | meaning |
|---|---|---|---|
| `--fixtures <dir\|a.json,b.json>` | ✓ | ✓ | per-test IR docs (required) |
| `--out <dir>` | ✓ | ✓ (`-o`) | host capture dir (required) |
| `--timeout-per-fixture <s>` | ✓ | ✓ | SECONDS, default 30; non-positive/NaN → 30 |
| `--udid <serial\|UDID>` | ✓ | ✓ | the pool slot; default = sole/first booted |
| `--composed` | ✓ | ✓ | one `<safe(testKey)>.png` per test (what the gate uses) |
| `--wpt-dir <corpus root>` | ✓ | ✓ | the NATIVE half of the @font-face / replaced-image asset hop |
| `--skip-install` | ✓ | — | app already installed (provisioned pool) |
| `--no-build`, `--app <path>` | — | ✓ | app already installed / prebuilt .app |

**`--wpt-dir` is mandatory whenever a fixture declares assets** (retro R8b,
A9#3): both feeders pre-scan every fixture's `fontFaces[].src` and
replaced-element `meta.attrs.src` BEFORE the first device call and exit 2
with `FATAL: fixtures declare N @font-face src(s) + M replaced-image src(s)
but --wpt-dir was not given` — previously the hop skipped silently and the
captures scored bundled-face text / empty image boxes. A per-src decline for
an absent root now says so (`no --wpt-dir`) instead of "unresolvable".

A capture that fails to parse twice (the adb-pull truncation flake) is now
DELETED, not left corrupt under the compare-glob name (A9#4): the cell is
MISSING (caught by `NATIVE_SHORT`) rather than an `{error}` diff that
silently leaves the denominator.

Host pre-passes both feeders run first, all pure host work: SVG pre-raster
(the corpus has SVG `<img>` sources in exactly two of the 30 sections —
css-ui's box-sizing cluster and css-flexbox's align-items-007), WOFF→TTF
(Android only), mono-pin rewrite. Exit codes: `0` every fixture OK · `1`
partial/fatal · `2` usage or asset preflight · `3` no device (Android).

Single-fixture recovery (BACKLOG): `node tools/titan/feed-android.mjs
--fixtures <one>.json --composed --udid <dev> --skip-install --wpt-dir
tools/wpt --out <run>/sections/<sec>/android-screenshots`, then re-run
inject with the section's `--combined fixtures/wpt/_section-<sec>.json`
and the same `--refs-root`/dirs `section-runner.sh` passes. Write refeeds as
explicit per-fixture commands (zsh does not word-split unquoted vars).

## Environment knobs

| variable | read by | effect |
|---|---|---|
| `WPT_DIR`, `WPT_REF` | extract, refs, feeders, runner | corpus root (default `tools/wpt`) and pin |
| `BACKGROUND_MODE` | runner, run-titan, feed-android | `1` (default) = no GUIs during unattended runs |
| `TITAN_MIN_FREE_GB` / `TITAN_ABORT_FREE_GB` | section-runner | disk preflight floor (10) / watchdog floor (8) |
| `TITAN_REQUIRE_ALL_COLUMNS=1` | inject | a whole platform column at zero, or a PARTIAL native column (per-test parity, retro R13), is FATAL (exit 3); section-runner sets it for `--all-platforms` |
| `SKIP_IOS` / `SKIP_ANDROID` / `SKIP_WEB` `=1` | inject (and test-all.sh) | declare a platform intentionally absent; section-runner sets the first two for `--web-only` |
| `POST_LOAD_EXTRACT` / `BIDI_BAKE` / `VT_BAKE` `=1` | extract-fixture | engage the three dynamic bakes — section-runner pins them inline, never trust the ambient shell |
| `TITAN_SVG_PRERASTER=0` | svg-preraster | switch the default-ON pre-raster off |
| `TITAN_WOFF_TRANSCODE=0` / `=force` | woff-to-ttf | off / rewrite every sibling regardless of the mtime cache |
| `TITAN_MONO_PIN=0`, `TITAN_MONO_PIN_FONTS` | mono-pin | switch the default-ON pin off / alternate staged faces |
| `TITAN_NOTO_PILOT=1`, `TITAN_NOTO_PILOT_FONTS` | noto-pilot, feeders, capture-browser-ref (it suffixes `CANVAS_REV`) | the parked delivery proof (rejected by measurement, wave 45) |
| `TITAN_NOVEL_INK_VETO=1` | inject | arm the disarmed novel-ink veto (do NOT lower its threshold to arm it) |
| `TITAN_CENSUS_FULL=1` | red-square-census.test.mjs | run the full-corpus census pin (15–50 s: 14 s idle, 52 s alongside concurrent lanes) |

## Instrument notes (read before quoting a number)

**pixelmatch settings differ between the two pipelines on purpose.**
`inject-wpt-block.mjs diffWebVsRef` runs pixelmatch at `threshold: 0.25,
includeAA: true` (pre-flip); `tools/visual/compare-screenshots.mjs` runs
`0.02 / includeAA: false` since the 2026-08-29 flip. TITAN's
`pixelMismatchedPct` feeds only the triage `divergence` labels, whose series
is historical; `wptPass` and the fuzzy quantities are unaffected. An older
comment claiming parity was wrong (finding A9#7).

**The red-square census** (`red-square-census.mjs`, finding A1#3). Counts
scored cells whose capture is > 0.5 % strict red (`r>200 && g<80 && b<80`)
where the reference has 0 % red, split by `wptPass`:

```bash
node tools/titan/red-square-census.mjs wave49-final [--json out.json] [--section <sec>]
```

Measured with this exact predicate: `wave49-final` → 179 passing red cells
(web 23 / iOS 66 / Android 90, 105 tests) and 87 failing (15/35/37,
51 tests); `wave48-final` → 174 passing (23/65/86, 100 tests) and 110
failing (18/45/47, 62 tests). The wave-49 note's "179 (26/67/86, 102
tests) / 89 (13/37/39, 53 tests)" does NOT reproduce from any red
definition the retro tried (passing 174–186, failing 109–113 on
wave48-final); the total 179 is reachable on wave49-final, its split is
not. The census script is the predicate of record from here on; the
canonical cell (`css-view-transitions/hit-test-unrelated-element`, 4.27 %
red, ssim 1.0000, PASS on all three) is pinned in its test.

**Column parity**: wave49-final has 0 missing per-test cells; the guard
that made that true (the gate script's reprovision-and-retry) was prose.
`section-runner.sh` now enforces capture-count parity and the inject
column assertion itself (`NATIVE_SHORT`), and `assertPlatformColumns`
(retro R13) reports per-test `missingCells` — exit 3 under
`TITAN_REQUIRE_ALL_COLUMNS=1`, which the runner maps to `NATIVE_SHORT`.

## Operational notes (hard-won)

- **Read-only emulator adb wedge after ~85 consecutive runs** (retro A11).
  Driving `test-all.sh` fixture-by-fixture on a private `-read-only`
  instance, the 85th run (`properties/transforms/translate.json`) hung
  898 s inside a bare `adb shell ls` (iOS 3/3 captured, Android/web never
  ran) and ended only by manual kill (exit 143); a freshly rebooted
  instance wedged again at the Android stage within one fixture. Budget:
  reboot + reprovision between long sweeps; treat any adb call > 60 s as a
  wedge, not a slow device. `provision-devices.sh` wraps its long
  device calls in `_bounded` (`pm path` 10 s, `install` 120 s, `pm clear`
  60 s, `simctl boot/create/terminate/uninstall/install` 30–120 s); its
  `adb devices` and `getprop sys.boot_completed` polls are bare.
  `test-all.sh` now routes EVERY adb call through an `adb()` wrapper around
  the same watchdog (`_adb_bounded`: `ADB_TIMEOUT` 60 s, `ADB_PULL_TIMEOUT`
  120 s + component count; a killed call is recorded and
  `_adb_abort_if_hung` fails the Android stage with exit 1 instead of
  printing an empty column) — the A11 prescription, landed in the retro and
  verified in `test-all.sh` at the time of writing.
- **Simulator pool growth.** `provision-devices.sh` used to `simctl create`
  on every run that found fewer than `--ios` booted sims — the 81-sim /
  38 GB CoreSimulator incident (BACKLOG #6). It now boots existing Shutdown
  `titan-pool-*` sims first. This host still carries duplicate
  `titan-pool-1` / `titan-pool-2` pairs from before the fix; delete the
  extras by UDID (`xcrun simctl delete <udid>`) — the script does not
  delete devices.
- **API-36.1 emulator images**: a `pm install` over an existing package
  leaves the app's external-files dir broken until `pm clear`; shell-created
  dirs under the app's external files are invisible to the app (FUSE view),
  so the feeders wait for the APP to create its inbox and asset roots.
- **Writable primary emulator** blocks extra `-read-only` instances of the
  same AVD; provisioning now continues with the smaller pool and names the
  flag (`--restart-fleet`) that kills it — together with every other
  session's emulator.
- **Disk**: APFS local snapshots hold freed space — `tmutil
  thinlocalsnapshots / 21474836480 4` after pruning. `section-runner.sh`
  refuses < 10 G and aborts < 8 G mid-run (`DISK_ABORT` marker in the
  section dir).
- **Recycled worktrees** spawn on the attic branch — always `git checkout -f
  -B campaign/applier-campaign neworigin/dev` first (BACKLOG standing
  constraint). Never commit `node_modules` in any form.
- **Suite runs on a shared tree are single-writer**; focused tests only
  while lanes are live: `node --test tools/titan/<file>.test.mjs`,
  `bash -n tools/titan/*.sh`.

## Buckets (bucket-wpt.mjs)

| bucket | meaning | pipeline |
|:--:|---|---|
| **A** | cleanly convertible | extract + 3-platform capture + browser-ref score (the gate samples 48 per section) |
| **B** | partial conversion (lossy) | pipeline + `lossy: true` and `lossyReasons` on the result |
| **C** | cannot model in IR | recorded under `wpt.skipped` with its reason |

The heuristics are the `RX` table in `bucket-wpt.mjs` plus the
remote-resource → C rule; additions must be flagged in the PR.
