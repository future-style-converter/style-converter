# Campaign backlog — the single source of truth for wave work

This file is what a wave reads before doing anything and updates before
shipping. It replaces session-memory queues: any agent with this file, the
`/wave` skill (`.claude/skills/wave/SKILL.md`), and `docs/STATUS.md` can run
the next wave with zero conversation context.

**Contract: every wave PR updates this file** — completed items removed,
new deferred/discovered items added with evidence pointers, the
next-wave obligations section refreshed. A wave PR that does not touch
this file is incomplete.

Corpus history and per-wave findings live in `docs/STATUS.md` (one dated
paragraph per wave) and `tools/titan/results/corpus-v*.json` (one snapshot
per wave, `_note` carries the full story). Current: **corpus-v6.12**
(wave 47) — web 1187/1379 (86.1%), iOS 1038/1351 (76.8%), Android
1029/1349 (76.3%). NOTE: v6.12 numbers are **not comparable** to
post-overhaul scores (see obligations).

---

## Standing constraints (never violated, never re-proposed)

- **Never push the branch `claude/jovial-shockley-b4adce`** (the attic —
  contains AiLogs with token-like strings). Recycled worktrees silently
  spawn on it: always `git checkout -f -B campaign/applier-campaign
  neworigin/dev` and verify the tip before any work.
- **Ring-fence**: the WPT test `filter-effects/backdrop-filter-basic-blur`
  belongs to an external session. No test-specific code, ever. Generic
  mechanisms that incidentally move it are reported plainly in the wave
  PR (precedent: waves 46, 47) — never carved out by name.
- **No dev→main promotion** — explicitly declined by the owner; do not
  re-propose. Same for device-visual CI ("Skip #3").
- `gh` default repo resolves to an ARCHIVED legacy repo — every gh command
  needs `-R future-style-converter/style-converter`.
- Squash-merge wave PRs to `dev`; verify `state == MERGED` **before**
  resyncing (violated three times historically).
- Skeptic lanes must produce **executed repros** — a skeptic that only
  reads code has failed.
- A new check must be **proven able to fail** (mutation/negative test)
  before it is trusted.

## Next-wave obligations (wave 48 opens with these)

1. **Calibration gate first**: the harness overhaul (PR #126) changed
   metrics (pixelmatch 0.02+AA, TITAN fuzzy in WPT-native units, 12
   refreshed 327-net baselines). Run one full gate on the merged tree
   with ZERO lane changes → snapshot as `corpus-v6-13-cal.json`. All
   wave-48 lane deltas are attributed against v6.13-cal, never v6.12.
2. The gate inherits new fatal exit classes on the 327-net: exit 3
   (non-sRGB capture), exit 4 (unledgered divergence), exit 5 (**stale
   ledger/waiver — fixing a ledgered divergence turns the run red until
   the line is DELETED**; the action is always delete, never re-add).
   Exit 6 (spec oracle) is dormant until fixtures gain `_expect`.
3. The cross-platform ledger (`tools/visual/cross-platform-expectations
   .json`, 31 entries, reason/owner/expires, index-free keys) is
   co-maintained by every wave that touches rendering.

## Ranked queue (wave 48+)

1. **The two vertical-mode degenerate wedges** (wave-47 residual, both
   cells unmeasured, denominator 1349): `css-break/background-image-006`
   hangs in layout; `css-writing-modes/direction-upright-002` renders a
   PNG the app encoder corrupts (consistent both pulls — suspect a
   canvas-size overflow in ScreenshotManager). The IntrinsicChannel
   packing crash that accompanied them is FIXED and pinned
   (`packableCap`/`fixedBandPackable`).
2. **Rule-43 unexclusion re-measure**: the marker line-box fix (wave 47,
   baseline-claim channel split in `ListMarkerRow`), the mono pin
   (default-ON since wave 47), and the Noto verdict (net zero, wave 45)
   are all in — re-measure the 25 excluded css-counter-styles cells and
   drop the exclusion where cells now clear 0.95.
3. **iOS multicol float fragmentation** (8 cells, Android passes all 8):
   the X3 plan-parameter seam landed (wave 45); the iOS strip needs its
   measure half. Brief: wave-46 skeptic S5's adjudication.
4. **Inline-run wall, remaining rings**: `<br>` members (123 in census)
   and none-tag members (75). Unlocks css-overflow 004/005/006/032
   remainder + more.
5. **Web contain-body shift ×8 + shared converter defects** (+10–19 web
   per the wave-47 Z8 map — the web column has been flat at 1187 for
   six waves).
6. **Open runtime bugs from the harness overhaul** (each waivered/
   ledgered as a tripwire that goes stale when fixed): (a) Compose
   transform-list per-kind accumulation (fix route recorded: build the
   matrix in CSS order, reuse `decomposeMatrix2D`); (b) Android clips a
   transformed child's paint to its layout slot; (c) Android box-shadow
   ~2.4× over-blur (`MultipleShadowApplier.kt` — needs real diagnosis);
   (d) iOS overflow clip applied OUTSIDE the transform.
7. css-gaps column-direction wrap (both natives) + iOS `flex-basis:100%`
   resolving to zero.
8. Vertical mechanism completion: multi-child balancing, clone-under-
   vertical, §8.3.1 horizontal collapse (wave-47 Z2 logged bails).
9. Smaller: `visibility: collapse|hidden` semantics on both natives;
   `text-decoration` shorthand colour-function drop; the web test that
   cannot fail (`SkepticFontShorthandLh` `font: inherit` case);
   flex cross-axis auto-margin exemption (TODO in `FlexCrossStretch.kt`).
10. **Corpus-expansion decision**: several web columns saturate their
    48-sample (46–48/48). Consider depth-96 or new sections; the
    wave-47 Z8 analysis has the numbers.

## Parked (decided, do not revisit without new evidence)

- Noto corpus-wide bundling: REJECTED by measurement (wave-45 pilot, net
  zero). Binding constraints named: Compose line-pitch (fixed where it
  exists), item-vs-marker claim split (fixed wave 47) — hence queue #2.
- SVG pre-raster: default ON since wave 44 (earned via A/B).
- WOFF→TTF hop: default ON since wave 42 (validated cache).
- Mono pin: default ON since wave 47 (earned via A/B; escape
  `TITAN_MONO_PIN=0`).
- dev→main promotion + v0.2.0 tagging: declined by owner.
- Cross-machine noise floor: deferred until visual jobs move to CI.

## Operational recipes (hard-won; read before gating)

- **Gate script**: df preflight (≥10G or abort), kill all emulators +
  extra sims first, clear `/tmp/titan-device-pool/provisioned-*`,
  provision via `tools/titan/provision-devices.sh` (it `pm clear`s after
  install — API-36.1 reinstalls break the app's external dir without it),
  30 sections × `--max-tests 48 --run-id waveNN-final`, 50-min watchdog
  that kills AND reprovisions, then `BASELINE=1 ./test-all.sh
  fixtures/visual-test.json`. Score with the standard idiom
  (`x.ssim`+`!x.scoreExcluded`, pass = `wptPass===true`); verify CAPTURE
  COUNTS per column against tests.list, never process liveness.
- **Single-fixture recovery** (adb-pull truncation, one missing diff):
  `feed-android.mjs --fixtures <one>.json --composed --device <dev>
  --wpt-dir tools/wpt --out <run>/android-screenshots` then re-run
  `inject-wpt-block.mjs` with the gate env
  (`POST_LOAD_EXTRACT=1 BIDI_BAKE=1 VT_BAKE=1`) and the section's
  `--combined fixtures/wpt/_section-<sec>.json`. **Refeeds must carry
  `--wpt-dir`** (asset hops silently skip otherwise → artifact scores).
- **Column recovery** (whole platform column dead): same recipe with
  `--fixtures <run>/per-test-ir`; for iOS use `feed-ios.mjs --udid
  0BB986A6-1EAD-4916-9276-079235324DA1` (boot it first).
- zsh does NOT word-split unquoted vars — write refeeds as explicit
  per-fixture commands, never `set -- $pair` loops.
- **Limit-killed workflow lanes**: resume with
  `Workflow({scriptPath, resumeFromRunId})` — completed lanes replay from
  cache; add per-lane `model:'opus'` overrides only to dead lanes.
- Kotlin IC-cache corruption (`Storage already registered`, `Page -N`):
  delete `runtimes/compose/build/kotlin`, retry. Gradle filtered-run
  state: final verification always `--rerun-tasks`.
- Suite counts live in doc tables (README/CLAUDE/STATUS + tree READMEs);
  doc-staleness-check enforces them — stamp AFTER the final sweep, and
  re-derive after skeptic/fix lanes add tests.
- iOS capture knobs travel as `SIMCTL_CHILD_*`; `FORCE_STATE` has **no**
  `CAPTURE_` prefix.
- Fixture authoring: `_expect` with shown arithmetic; solid colours
  ≥3/channel from white and outside ±8/channel of page bg rgb(26,26,46);
  transforms escaping the box need margin; tie-free animation times;
  control fixtures run `NO_CROSS_PLATFORM_GATE=1`.
