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
  `tools/wpt/refs/<sha>/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/`.
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

Wide fan-outs are FILE-DRIVEN and run every lane on `model: 'opus'` from
the start (wave 52: three fan-outs died on the session usage limit
mid-run and left partial edits): each lane ends its
`tools/titan/results/<wave>-<lane>/_note.md` with a `STATUS: COMPLETE` /
`STATUS: PARTIAL — …` line, and a relaunch skips the COMPLETE ones. If
lanes still die on a limit: resume the same Workflow (`resumeFromRunId`)
— completed lanes replay from cache.

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
--rerun-tasks`, **android-harness `:app:testDebugUnitTest --rerun-tasks`**
(without the flag the task comes back UP-TO-DATE in ~1 s and stamps a count
that ran no tests — wave 54 saw it twice), swiftui (Catalyst), **`npm -w apps/web-harness run test`**, tooling (`node --test
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
attached or the run refuses with exit 2). The gate is tracked code:
`tools/titan/gate-driver.sh <run-id>`. Run it in background
(`run_in_background: true` — not a shell `&`) with an EXPLICIT
`timeout: 7200000` and as TWO launches — `--skip-fixture-net` for the
corpus (~80 min), then `rm -f /tmp/titan-device-pool/provisioned-*` and
`--skip-corpus` for the net — so neither half is near the 2-hour limit
(wave 52: a launch with a short limit was stopped 10 minutes in;
`--resume` skips the sections already OK). Then WAIT for the task
notification — no heartbeat, no polling loop — and verify **capture
counts per completed section** from the driver's summary, never
liveness. While a gate runs, edit NOTHING it executes or builds from
(the driver and runner scripts, runtime / harness / converter sources):
bash reads a script as it runs, and provisioning builds from the tree.
Prepare such changes as a patch file and apply it when the device is
idle.

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
  ref (`tools/wpt/refs/<sha>/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/<sec>/`).
  ~35% of PASS cells are visibly wrong renders and the red-square class is
  the committed proof (`node tools/titan/red-square-census.mjs <run-id>`) —
  a currently-passing cell that MOVES is not automatically a regression
  either; look (A1#0).
- **A calibration run (instrument change, same captures) carries the
  capture-hash check**: sha1 every capture PNG against the previous gate;
  identical bytes → instrument-only flip, different bytes → a render change
  adjudicated in the `-cal` snapshot's `_note` (A1#2).
- **A probe decides reverts under the plan's own rules, and the closing gate
  is pre-registered AGAINST those decisions** (wave 53): when a probe fires a
  revert rule, revert the unit, then write the decision into the generator
  (`expectations.json` → `probeDecisions`: predictions withdrawn from R4,
  the cells held to must-not-move in R5, the unit's carriers withdrawn from
  the control) and prove the amended rules fail on the probe tree itself
  before launching the gate.
- **An upstream bake that positions runs (markers, glyph boxes) gets a
  one-section DEVICE probe read with the family's geometry script before
  the closing gate** — a replay composited onto the web picture cannot
  predict native row assignment (wave 53 U2: SSIM rose 0.9547 → 0.9793 with
  every Android marker on the wrong row; only the geometry row caught it).
- **The installed-build hash of record is the CORPUS launch's read right
  after `provision rc=0`** (the one-shot `until grep 'attempt 1 starting'
  <driver log>` wait). A fixture-net launch's read is annotated, never of
  record: `test-all.sh` rebuilds and reinstalls the iOS harness per fixture,
  so even a read seconds after provisioning finds the built .app missing
  (wave 53; confirmed wave 54), and a post-net re-read comes after the
  driver's teardown.
- **A floor on a geometry-gated prediction row is the pass threshold
  (0.95), never a replay magnitude**: wave 54's two L3 iOS rows missed a
  0.965 replay estimate by ~0.02 with their geometry keys PASS and faithful
  pictures, and rule 3 read literally would have reverted three
  picture-correct units (PLAN §10 item 7). The geometry key is the
  picture-correctness gate; the magnitude is the row's `to`.
- **A landing script's `--check` / dry-run path runs no destructive step**
  (`git checkout`, `rm`, `git apply`) and is first exercised in a throwaway
  worktree: wave 54's `land-units.sh --check` rewound the shared files and
  wiped a lane's in-tree edits (restored byte-exact from its unit patches).
- **While any instrument is still writing, orchestrator commits stage named
  paths only** — a directory-wide `git add` captured 379 half-written
  per-test-IR files and a 0-byte differential output (wave 54, c7e37409);
  lane evidence of record is `.txt` (or force-added): `*.log` is gitignored
  and 104 cited logs had to be force-added before the closing gate.
- **A probe decision (a revert or a re-pin) regenerates the plan AND every
  record that pins its counts, keys or carriers, in the same stage** (wave
  55, `tools/titan/results/wave55-plan/PLAN.md` §10 items 12-15; the
  records below are under `tools/titan/results/`): `plan-build.py` is not
  the whole chain. After wave 55's stage-1 reverts and re-pins, the
  generated closing read-out had to be re-rendered
  (`wave55-gate/final55-analysis.gen.py`, then `--check`: the decision had
  left it STALE on the pre-decision expectations, aaf51b1b);
  `wave55-gate/readers-calibration.sh` had to read its vanished-carrier
  stem from `unionCaptureCarriers`, because the hard-coded one had become a
  withdrawn carrier that reads as a complement leak (its K13 comment);
  the self-check's §T harness `wave55-plan/probes-teeth.py` had to drop
  the wants on withdrawn keys, and `wave55-plan/probes-selfcheck.py` had
  to sweep §T3-§T5 only over the lanes that still carry a gating key, or
  it reports violations on keys no longer under test (07dd8453, aaf51b1b)
  (`wave55-plan/probes-selfcheck.txt` §T: 77 cases skipped, 37 withdrawn
  keys dropped); and `wave55-plan/fix-r6.zones.py --check` re-derives only
  with its recorded `--also` device sources (`WINDOWS_ALSO` in
  `wave55-plan/residual-read.py`), or it reads the windows literal as STALE
  (item 12). A regeneration that changes no probe and no gating key says so
  in its §10 item instead of re-running the self-check (item 17). Count the
  §10 items before numbering a new one: the five revert commits cite "item
  7 / 8" and the decision commit "items 4-9", written before the nine
  pre-landing items were counted; they are items 13 / 14 and 10-15, and the
  renumbering cost a second, text-only regeneration (item 15).
- **A may-move cell beyond its band whose look is right needs a generator
  input, not only a recorded look** (wave-55 PLAN §10 items 16-17):
  `wave55-probe` looked at the three L6 may-move stems beyond the 0.002
  band, found `mayMoveRule`'s first pre-registered picture ("a band now
  under its child") and kept the unit with no decision row and no
  regeneration; the closing read-out then failed adjudicate R5 on exactly
  those three cells (must-not-move 4087, moved 3, labelled complement),
  because R5 reads the complement and has no may-move class. Encode the
  look when it is made: an append-only
  `tools/titan/results/wave55-plan/probe-decisions/looks.json` row per
  cell (lane, cell, run, open / probe scores, the picture asserted to be one
  of the rule's) that `plan-build.py` turns into a non-gating, undirected
  prediction row of the owning lane and takes out of the complement; the
  band stays for every other may-move cell. Prove it like a revert: the same
  closing record read with the pre-looks expectations keeps its leak signals
  (`tools/titan/results/wave55-gate/final/probe-readout.looks-proof.txt`).
- **A same-renderer structure read whose reference is the base capture
  reads the base's own defect as the rule** (wave-55 PLAN §10 item 10;
  `tools/titan/results/wave55-plan/ship-notes.md` §1 item 6): L3's `[S]
  bidi-tab-001 android rtl-rows` counted each band's ink components against
  `wave55-open`, whose '0' sat stranded outside its box (three components);
  the fixed picture, the '0' back beside its square (1 px right of the
  ref's, touching the square where the ref's sits apart), counts two, so
  the read printed `ink:` WRONG on `wave55-pre
  css-text/bidi/bidi-tab-001.html android P 0.9929` and its rule text would
  have reverted a right unit. A read "vs the base" holds only where the base
  is right in the band it reads; otherwise give it the decided device
  picture as a second reference, additive (the band equals the base's OR the
  decided run's, under the same rules: `INK_RUN_DEFAULT` in
  `tools/titan/results/wave55-plan/positioned-parent-slot.geometry.py`),
  set after the recorded look and proven on fakes built from the device
  picture (`tools/titan/results/wave55-plan/stage1-mutations.out.txt`: 7
  fakes WRONG, unfaked OK).
- **A glyph-identity read against Chromium's rasterization is a cross-font
  read: same-font references only** (wave-55 PLAN §10 item 11): L5's `[H]
  counter-suffix ios` letter-profile read correlated the device's letter
  with the ref's and scored a right picture 0.80 against its own letter and
  0.88 against the other row's (`wave55-pre
  css-counter-styles/counter-suffix.html ios P 0.992`); against the device's
  own letters the same rows read 1.00 / 0.83, and the replay's Noto glyphs
  read 0.775 / 0.867 against the device's: the cross-font profile misjudges
  in both directions. The fix is same-font and additive: a native row is
  also accepted when it is nearer its own row's letter in the decided
  same-platform capture (`TWIN_RUN`), and the one hole that OR opens is
  closed by a same-capture duplicate read (two rows of one capture must
  differ, `DUP_TOL`). A cross-font miss is decided by the recorded look,
  never by widening a tolerance.
- **The fixture net's first attempt can lose a platform column to a
  transient: keep the attempt's log, re-run with a logcat tap, record
  both** (wave-55 PLAN §10 item 17): `wave55-final`'s net attempt 1 brought
  composition-test.json's Android column back EMPTY (0 of 32 captured, "app
  may have crashed") and exited 7 while the other eight fixtures exited 0.
  Commit that attempt's output under its own name before re-running
  (`tools/titan/results/wave55-gate/final/fixture-net.attempt1.txt`,
  `final/driver-summary.attempt1.txt`), then re-run the net with a detached
  logcat tap on the pool emulator from `provision rc=0` to the driver's
  exit. Attempt 1 is a transient only when the re-run captures the same
  fixture in full and the tap shows no harness crash (attempt 2: 32 / 32 /
  32, `final/fixture-net.attempt2.txt`); the gate note records both
  attempts and what the tap showed. A crash in the tap is a capture to fix
  under exit 7, never a re-run until green.
- **A review workflow's readers load the host: run the closing arm before
  or after the cell review, not during it** (wave-55 PLAN §10 item 18): the
  arm `drop-L6-U2`, launched while the review's readers were running, was
  refused by the quiet-host gate at load1 6.11
  (`tools/titan/results/wave55-gate/final/ab-drop-L6-U2.refused.driver.txt`)
  and ran on its re-launch at load1 2.50 (`final/ab-drop-L6-U2.driver.txt`).
  Sequence the reader fan-out and the device window; never `--force` past
  the refusal, and keep the refused attempt's records beside the arm's. A
  launch straight after a teardown is refused the same way (the closing
  gate's fixture-net launch at load1 6.25, right after the corpus run's own
  teardown: item 17, `final/net-launch.refused.driver.txt`).

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
