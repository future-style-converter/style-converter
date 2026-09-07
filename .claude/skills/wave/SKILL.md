---
name: wave
description: Run one full applier-campaign wave end-to-end — preflight, max-width builder lanes, executed-repro skeptics, fix lanes, the 30-section device gate, ship as a squash-merged PR to dev, refill docs/BACKLOG.md, stop and report. Invoke per wave ("go"); it is autonomous within a wave and stops at the end.
---

# /wave — one applier-campaign wave, end to end

You are running one wave of the Style-Converter applier campaign: find
real renderer bugs with pixel evidence, fix them across three runtimes
(web/React-DOM, Android/Compose, iOS/SwiftUI), verify adversarially,
gate on devices, ship. **`docs/BACKLOG.md` is the single source of
truth** — read it first, honor its standing constraints verbatim, and
update it before shipping. `docs/STATUS.md` carries the per-wave record;
`tools/titan/results/corpus-v*.json` the score snapshots.

The whole wave is autonomous: one invocation runs to the shipped PR and
the final report, pacing itself with ScheduleWakeup heartbeats while
background work runs. Stop the loop and report when the wave ships.

## Phase 0 — preflight (every invocation, no exceptions)

1. **Branch identity** (recycled worktrees silently spawn on the attic
   branch, which must NEVER be pushed — see BACKLOG standing
   constraints): `git fetch neworigin dev && git checkout -f -B
   campaign/applier-campaign neworigin/dev`; verify the tip is the last
   wave's merge commit and `git status --short` is empty.
2. Fresh-worktree provisioning if missing — follow BACKLOG "Operational
   recipes → Fresh-worktree provisioning" exactly: `local.properties`
   with `sdk.dir=$HOME/Library/Android/sdk` (root + apps/android-
   harness); the WPT mirror (`tools/wpt/`); the iOS xcodeproj
   (`xcodegen generate`); and `node_modules` as a DIRECTORY of
   per-entry symlinks whose three workspace packages
   (`@style-converter/web`, `web-harness`, `style-converter-tools`)
   link into THIS tree. **Never symlink the whole `node_modules` dir
   from another worktree** — its relative workspace links silently
   serve that tree's web runtime to every vite capture (proven wave
   48). Clear `apps/web-harness/node_modules/.vite*` after repointing.
3. **Disk** ≥ 10G free (`df -g /System/Volumes/Data`); if low, prune old
   `tools/titan/runs/*` (keep current + previous gate + wave35-webmap),
   erase shutdown simulators, DerivedData, sibling worktree build dirs.
4. Devices quiet: no emulators attached, no booted sims beyond the seed
   iPhone (`0BB986A6-1EAD-4916-9276-079235324DA1`).
5. Quick health: tooling suite (`node --test tools/visual/*.test.mjs
   tools/titan/*.test.mjs`) green; `bash tools/visual/doc-staleness-
   check.sh` exit 0; pilots in expected state (mono-pin/pre-raster/WOFF
   all default-ON per BACKLOG's Parked section).
6. **The fixture net is `tools/visual/gate-fixtures.txt`** (retro A12#3):
   read it now — every fixture listed runs at the gate via `./test-all.sh
   --gate-set`, never `visual-test.json` alone. Check that each listed
   fixture has committed baselines under `tools/visual/baseline/` (a
   fixture without them runs gate-only and cannot go stale; seed it with
   `UPDATE_BASELINE=1 ./test-all.sh <fixture>` after LOOKING at the PNGs),
   and that `tools/visual/cross-platform-expectations.json` has a real
   `owner` and a parseable `expires` on every line — the comparator exits 2
   on a placeholder owner, so an unowned ledger means the gate cannot run
   (BACKLOG obligation #7).
7. **Snapshot sanity**: the latest `tools/titan/results/corpus-v*.json`
   must name the previous gate's run-id in BOTH `artifact` and `reproduce
   --run-id` (retro A2#0: v6-15 pointed at wave48-final for the wave-49
   gate). Fix it before attributing any delta.
8. **Read `docs/BACKLOG.md`** — obligations first (they may mandate an
   opening step, e.g. a calibration gate after instrument changes), then
   the ranked queue. Every pointer you act on must resolve: a tracked path,
   a gate cell (`<run-id> <sec>/<test> <platform> <verdict> <score>`), or a
   snapshot field. A pointer into a session scratchpad is a defect in the
   backlog — mark the item's evidence LOST and re-measure before staffing
   it (retro A10#0: the wave-49 hand verdicts and seam patch were lost this
   way).

## Phase 1 — plan the lanes

From the backlog queue + the latest gate's run dir
(`tools/titan/runs/wave<N-1>-final/`), pick 6–9 builder lanes with
**disjoint file ownership**. Mine before staffing: every lane brief
names its target tests, current scores, and the visible defect from the
PNGs. Shared files (the two ComponentRenderers) get explicit tagged
seam regions per lane, or lanes deliver **verified seam patches** in the
scratchpad for the orchestrator to apply after audit (the proven
pattern). Device lanes are rare and explicit (private `-read-only`
emulator on a unique port, provision via `provision-devices.sh`, kill on
exit); all other lanes work from the frozen gate captures + refs.

## Phase 2 — builder lanes (Workflow tool, max width)

One Workflow call, all lanes parallel, each returning the structured
schema `{lane, findings, changes[], expectedFlips, suites, risks,
deferred}`. The COMMON preamble every lane gets:

- Repo root, branch, DO-NOT-COMMIT. Evidence base: the previous gate's
  `sections/<sec>/manifest.json` (scorer idiom: skip unless
  `typeof x.ssim==="number" && !x.scoreExcluded`; pass =
  `wptPass===true`), capture PNG dirs, `per-test-ir/`, frozen refs at
  `tools/wpt/refs/<sha>/white-black-ink-font-lh-imgpad-htmlpins/`.
- **LOOK AT THE PNGS** before claiming any fix; name the visible defect.
- Hard rules: ownership lists; every line commented (why + spec/API
  citation); ≤200-line file targets; no silent fallthroughs; registry
  pattern; unit tests for new behavior on VERBATIM corpus payloads;
  **corpus-simulate the blast radius** (enumerate which tests' paths
  change and which are currently passing).
- Suites for touched trees with exact counts — **FOCUSED runs only inside
  a lane** (the shared tree is single-writer for full sweeps; concurrent
  gradle daemons race on AGP jar scans). Compose:
  `(cd apps/android-harness && ./gradlew :runtime:testDebugUnitTest
  --tests '<Class>')`; harness code → `:app:testDebugUnitTest` likewise.
  IC-cache errors → delete `runtimes/compose/build/kotlin`, retry.
  SwiftUI (repo root): `xcodebuild test -scheme StyleConverterRuntime
  -destination 'platform=macOS,variant=Mac Catalyst,arch=arm64'
  -only-testing:StyleConverterRuntimeTests/<Class>` (the only local lane).
  Converter: `./gradlew :converter:test --tests '<filter>'`. Web: `npx
  vitest run <path>`; web-harness: `npm -w apps/web-harness run test`.
  Tooling: `node --test <file>`. The orchestrator alone runs the full
  sweep (Phase 4).
- Every new pin is PROVEN able to fail: mutate the mechanism → the test
  fails → restore, and the lane reports the mutation. Tests use VERBATIM
  corpus payloads (per-test IR from the previous gate's `per-test-ir/`, or
  converter output of a fixture).
- Probes in the session scratchpad, NEVER the repo (lanes grep their own
  diff for probe/Probe before finishing) — BUT any artifact a LATER wave
  must act on (hand verdicts, classifications, a deferred patch, a census
  list) is COMMITTED under `tools/titan/results/<wave>-<lane>/` with a
  `_note`, and BACKLOG cites that path. The session scratchpad does not
  survive the wave (retro A10#0).
- A resumed lane (usage-limit kill) opens with `git status` / `git diff`
  on its OWNED paths: the dead attempt may have left partial edits or an
  un-restored mutation (retro R5 found one).
- The ring-fence and honesty rules from BACKLOG, verbatim.
- Fixture authoring rules (BACKLOG "Operational recipes" tail) whenever
  a lane adds fixtures — `_expect` with shown arithmetic preferred.

If lanes die on a usage limit: resume the same Workflow
(`resumeFromRunId`) — completed lanes replay from cache; add per-lane
`model:'opus'` overrides only to the dead lanes.

## Phase 3 — skeptics (executed repros, non-negotiable)

A second Workflow: 5–6 adversarial lanes attacking the riskiest builder
claims, schema `{skeptic, verdict: DEFECT-CONFIRMED|CLAIM-HOLDS|MIXED,
executedRepros, defects, mustFixBeforeGate}`. Always include:

- **S1 combined-tree**: shared-file hunk audit (every hunk single-lane,
  tagged, disjoint), the FULL suite sweep with exact counts (the Phase-4
  list — harness suites included), git-status hygiene (every untracked
  file mapped to a lane; zero probe files; no stray `build.gradle` edits;
  `git stash list` empty — never stash on the shared tree), device
  hygiene, doc-staleness stale-row list, and a pointer audit: every
  evidence pointer a lane wrote into BACKLOG resolves in the tree.
- Independent replays of every lane's blast-radius simulation (never
  trust the lane's own script — one shipped a sim that scanned a
  nonexistent key and proved nothing).
- Adversarial unit probes against the shipped code (reflection/JVM
  harnesses on real per-test IR).
- Honesty audits: comments must match measurements; reference-fitted vs
  spec-derived adjudications cite the spec.

Apply `mustFixBeforeGate` items via a small fix Workflow (precise
prescriptions, opus is fine) or inline when trivial. Re-run touched
suites after.

## Phase 4 — final sweep + docs

One full sweep on the merged tree — **single-writer, sequential, run by
the orchestrator alone** (all suites, exact counts — remember skeptic/fix
lanes add tests, so counts move late). The sweep is: converter
(`--rerun-tasks`), web runtime, compose `:runtime:testDebugUnitTest
--rerun-tasks`, **android-harness `:app:testDebugUnitTest`**, swiftui
(Catalyst), **`npm -w apps/web-harness run test`**, tooling (`node --test
tools/visual/*.test.mjs tools/titan/*.test.mjs`), IR conformance (`node
schema/conformance/run.mjs --emit`). The two harness suites pin the
em-margin ladder, the flow-root rule and the root-clip web rule and were
outside every documented sweep until the retro (A8#4);
`doc-staleness-check.sh` derives their counts now. Stamp the doc tables
(README.md, CLAUDE.md, docs/STATUS.md `| N |` rows + the tree READMEs'
`(N tests)`) and require `doc-staleness-check.sh` exit 0.

## Phase 5 — the device gate

Build the gate script from the template in BACKLOG "Operational
recipes" (df preflight and the <8G watchdog are `section-runner.sh`'s
own since the retro; reprovision with pm clear; 30 sections ×48;
watchdog+reprovision; the 327-net as **`BASELINE=1 ./test-all.sh
--gate-set`** — every fixture in `tools/visual/gate-fixtures.txt`, not
visual-test alone; set `ANDROID_SERIAL` when more than one device is
attached or the run refuses with exit 2). Run it in background
(`run_in_background: true` — not a shell `&`), heartbeat every ~30 min
checking **capture counts per completed section**, never liveness.

Scoring: full per-section deltas vs the previous snapshot; capture
columns verified against tests.list; every skeptic watchlist cell
checked by name; flips identified per test (LOST cells get diagnosed —
missing-diff = instrument, recover via the single-fixture recipe;
reproducible score drops get adjudicated honest-vs-regression). Three
rules from the retrospective, none optional:

- **"Lost" and "gained" are the PER-CELL DIFF of the two run dirs**
  (`<prev>-final` vs `<this>-final`, scorer idiom `typeof x.ssim ===
  "number" && !x.scoreExcluded`, pass = `wptPass === true`), never a
  subtraction of totals or per-section counts (A1#1: four waves declared
  "zero regressions" over per-section drops their own snapshots show).
- **Every headline cell gets a PNG check**: before writing "PASSES on all
  three" or naming a mechanism's flips, open the capture next to the frozen
  ref (`tools/wpt/refs/<sha>/white-black-ink-font-lh-imgpad-htmlpins/<sec>/`).
  ~35% of PASS cells are visibly wrong renders and the red-square class is
  the committed proof (`node tools/titan/red-square-census.mjs <run-id>`) —
  a currently-passing cell that MOVES is not automatically a regression
  either; look (A1#0).
- **A calibration run (instrument change, same captures) carries the
  capture-hash check**: sha1 every capture PNG against the previous gate;
  identical bytes → instrument-only flip, different bytes → a render change
  adjudicated in the `-cal` snapshot's `_note` (A1#2).

The gate contract (post-overhaul): exit 3 aborts a section (fix the
capture, never the check); exit 4 → fix or ledger with reason/owner/expires
(a real owner — the comparator rejects placeholders); exit 5 → DELETE the
named stale line, that is the whole action; exit 6 → fix the platform or
correct `_expect` with a spec citation; exit 7 → a platform column is
MISSING or SHORT (crash / adb / simulator / poll exhaustion): fix the
capture and re-run — `SKIP_<P>=1` is only for a platform deliberately
absent from the run, never a way past exit 7; exit 2 "violates the ledger
contract" → the named ledger line lacks a real `owner` or a parseable
`expires` (fix the line, not the check). A seize-only motion fixture
(`_capture.seizeOnly`) is refused with exit 2 by a bare run — it goes
through `tools/visual/animation-sweep.sh`.

## Phase 6 — ship

1. Kill emulators/extra sims.
2. Write `tools/titan/results/corpus-v6-<n>.json` (same shape as the
   previous; `_note` carries the full wave story including honest
   losses, instrument events, and prediction misses). **`artifact` and
   `reproduce --run-id` MUST equal THIS gate's run-id** — check them
   against the run dir you scored (`ls tools/titan/runs/`), never copy them
   forward (A2#0/A1#4: v6-15 named wave48-final for the wave-49 gate, and
   following its recipe would have re-run into the previous wave's dir).
   Every "zero lost" number in the note is the per-cell diff from Phase 5;
   every "passes on all three" claim has had its PNG check.
3. Append the wave paragraph to `docs/STATUS.md` (before "## Test
   suites"); doc-staleness green. STATUS records; it is not a queue — an
   open item goes into BACKLOG or nowhere.
4. **Update `docs/BACKLOG.md`**: remove completed queue items, add every
   lane's deferred items with evidence pointers, refresh obligations,
   record new recipes/lessons. **Evidence-pointer rule**: a pointer is a
   tracked path, a gate cell (`<run-id> <sec>/<test> <platform> <verdict>
   <score>`), or a snapshot field — NEVER a session scratchpad, a `_diag*`
   dir or "the PR body". Any lane artifact a later wave must act on is
   committed under `tools/titan/results/<wave>-<lane>/` in this PR and
   cited from there (A10#0). Grep the file for the word scratchpad
   followed by a slash before committing: it must return nothing. A wave
   PR without a BACKLOG update is incomplete. **Ship-time truth check**
   (retro round 2, S6): every "landed by <lane>" / "fixed" / "not fixed" /
   "delivered" sentence in BACKLOG is re-verified against the FINAL tree
   you are about to commit — not against the lane's report, which
   describes a tree that concurrent lanes and the integrator have since
   moved (the retro's own refill shipped six stale facts and 12 drifted
   `path:LINE` pointers that lane-time verification had passed). Re-resolve
   every `path:LINE` token to the live line or cite the symbol instead
   (`ComponentRenderer.kt` `toBoxAlignment()` call site), and re-run the
   Phase-3 pointer audit on the final tree — it proves the file exists,
   not the line, so read each one.
5. Branch `campaign/wave<N>`, `git add -A` (check for strays first),
   commit with the full story + `Co-Authored-By: Claude Fable 5.1
   <noreply@anthropic.com>`, push, PR to dev with
   `-R future-style-converter/style-converter` (gh's default repo is an
   archived legacy repo).
6. CI watch via JSON (`gh pr checks N --json state` polled in a
   background loop — never whitespace-awk). On failure: pull the job log
   via `gh api .../jobs/<id>/logs`, fix, amend, force-push.
7. Squash-merge, **verify `state == MERGED` before resyncing**, then
   `git checkout -f -B campaign/applier-campaign neworigin/dev`.
8. Stop the wakeup loop. Report: the corpus table (prev → new per
   platform), the headline mechanisms, honest losses stated plainly,
   instrument events, and the refreshed queue's top items.

## Failure recipes (verbatim from BACKLOG — read them there)

ENOSPC mid-gate; API-36.1 external-dir wedge; adb-pull truncation;
sim-pool exhaustion; phantom-PNG suppression asymmetry; zsh word-split;
Kotlin IC cache; Gradle filtered-run state; limit-killed lanes. Every
one has a tested recipe in BACKLOG "Operational recipes" — follow it
rather than re-deriving.

## Cadence and honesty

One wave per invocation; stop and report when shipped. Zero-flip waves
are shipped plainly as such (precedent: wave 43) — the corpus note says
what moved sub-threshold and names the next wall. Prediction misses are
diagnosed, not buried. Every default flip (pre-raster, WOFF, mono-pin)
was earned by a measured A/B with a decision rule stated in advance —
keep that bar.
