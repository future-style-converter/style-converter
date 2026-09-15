# wave50-S3 — skeptic lane: the Compose renderer seams

Lane S3 of wave 50's Phase-3 skeptic pass. **No devices, no browsers.** Every
number below is one of: a JVM probe run through the runtime's OWN decoder on
VERBATIM `tools/titan/runs/wave49-final/sections/*/per-test-ir/*.json`, a
mutation test on a production file (copied, sha256'd, edited, run, restored,
sha256 re-verified), PNG arithmetic on the frozen refs / committed baselines,
or a `manifest.json` read. Probe sources are archived under
`probe-sources/*.kt.txt` (the live files were deleted from
`runtimes/compose/src/test/java/com/styleconverter/runtime/_skeptic50/` before
reporting); their outputs are under `probe-output/`.

## Suite counts (S1 needs these — only this lane runs the android-harness gradle)

| suite | command | result |
|---|---|---|
| Compose runtime, FULL | `(cd apps/android-harness && ./gradlew :runtime:testDebugUnitTest --rerun-tasks)` | **3229 tests, 1 FAILED** |
| android-harness app | `(cd apps/android-harness && ./gradlew :app:testDebugUnitTest --rerun-tasks)` | **116 tests, 0 failed** |
| focused (content/core.renderer/layout.position/columns/typography.inline/scrolling/visibility/effects.shadow/PlaceholderFloorBorderBoxTest) | `--rerun-tasks --tests …` | 951 tests, 0 failed |

The single failure is **`com.styleconverter.runtime.sizing.WptBoxSizingDefaultTest >
renderer threads LocalWptCaptureMode into the style chain`** — a wiring pin
broken by lane B9's seam. See defect S3-1.

## Verdict per claim

| claim | verdict |
|---|---|
| B2 hunk 2 — 7 percentage-inset carriers, only `position-relative-006` changes | **HOLDS** (`probe-output/percent-inset-levels.txt`) |
| B2 hunk 1 — the nine named tests are exactly the blast radius | **FALSE**: 22 tests / 23 components change (`probe-output/atomic-inline-census.txt`) |
| B2 hunk 1 — the predicate's scope gates (explicit width, vertical, inline-table, inline/inline-flex/inline-grid) | HOLDS (`probe-output/atomic-inline-adversarial.txt`) |
| B3 — 13 tests / 78 components fold, 3 named refusals refuse | **HOLDS** (`probe-output/pseudo-after-fold.txt`) |
| B3 — 3 mutations kill the pins | HOLDS (2 of 3 repeated: 4 pins / 1 pin) |
| B9 — 34 clamp docs, 38 roots, exactly 3 caps change to 112/192/192 | **HOLDS** (`probe-output/lineclamp-census.txt`) |
| B9 — the ring changes exactly 3 hosts, all in `block-ellipsis-032` | **HOLDS** (diff of `probe-output/inline-fold-outcomes-{base,noglyphless}.txt`) |
| B9 — the `runCatching` seam is not a silent fallthrough | **FALSE**: no log, no breadcrumb |
| B11 — exactly 3 committed Android baselines change, to 115x30 / 200x30 / 115x30 | HOLDS on the HEIGHT axis; the note omits the WIDTH-axis move on 091/105 |
| B5 — the intrinsic clamp, longest corpus upright run = 12 glyphs | **HOLDS** (2 of 9 pins die under mutation; my own census also says 12) |
| B10 — exactly `flex-gap-decorations-045/-046` change | **HOLDS** (structural census + §9.4 leftover arithmetic) |
| B6 — `MulticolFloatStripRefRowsTest`'s ref rows 111 / 91 | **HOLDS** (PNG scan at x=237 of both frozen refs) |

Full findings, repro commands and prescriptions are in the lane's structured
report; the must-fix list is S3-1 (red suite) and S3-2 (undisclosed blast
radius).

## How to re-run a probe

The probe classes were deleted from the tree (wave rule). To re-run one:

```bash
mkdir -p runtimes/compose/src/test/java/com/styleconverter/runtime/_skeptic50
cp tools/titan/results/wave50-S3/probe-sources/AtomicInlineCensusProbeTest.kt.txt \
   runtimes/compose/src/test/java/com/styleconverter/runtime/_skeptic50/AtomicInlineCensusProbeTest.kt
(cd apps/android-harness && JAVA_HOME=$(/usr/libexec/java_home -v 21) \
   ./gradlew :runtime:testDebugUnitTest --tests '*_skeptic50.AtomicInlineCensusProbeTest*')
rm -rf runtimes/compose/src/test/java/com/styleconverter/runtime/_skeptic50
```

Each probe writes into `tools/titan/results/wave50-S3/_probe-out/` (renamed to
`probe-output/` here). They read `tools/titan/runs/wave49-final/` which is
gitignored — they cannot run on a fresh clone, which is why they are probes
and not committed tests.

## Mutation log (every production file restored + sha256-verified)

| file | mutation | tests killed |
|---|---|---|
| `content/PseudoTextFold.kt` | `pseudos["after"]` → `pseudos["afterMUT"]` | 4 in `PseudoTextFoldTest` |
| `content/PseudoBucketExtractor.kt` | drop `!afterFolded &&` | 1 (`a folded after disappears from the wrapper config`) |
| `typography/inline/InlineRunFold.kt` | `glyphless` → `false` | 3 hosts revert to `BAILED:member-prop:WhiteSpace` |
| `typography/inline/LineBoxCensusRuns.kt` | remove the `br` / `line-break` branch | **0 corpus caps move** — the branch is latent |
| `core/renderer/VerticalRunIntrinsics.kt` | `sumClampedIntrinsic` → plain Int sum | 2 of 9 in `VerticalRunIntrinsicsTest` |
