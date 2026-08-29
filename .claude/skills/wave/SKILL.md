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
2. Fresh-worktree conveniences if missing: `local.properties` with
   `sdk.dir=$HOME/Library/Android/sdk` (root + apps/android-harness),
   `node_modules` symlinked from the workspace root.
3. **Disk** ≥ 10G free (`df -g /System/Volumes/Data`); if low, prune old
   `tools/titan/runs/*` (keep current + previous gate + wave35-webmap),
   erase shutdown simulators, DerivedData, sibling worktree build dirs.
4. Devices quiet: no emulators attached, no booted sims beyond the seed
   iPhone (`0BB986A6-1EAD-4916-9276-079235324DA1`).
5. Quick health: tooling suite (`node --test tools/visual/*.test.mjs
   tools/titan/*.test.mjs`) green; `bash tools/visual/doc-staleness-
   check.sh` exit 0; pilots in expected state (mono-pin/pre-raster/WOFF
   all default-ON per BACKLOG's Parked section).
6. **Read `docs/BACKLOG.md`** — obligations first (they may mandate an
   opening step, e.g. a calibration gate after instrument changes), then
   the ranked queue.

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
- Suites for touched trees with exact counts. Compose:
  `(cd apps/android-harness && ./gradlew :runtime:testDebugUnitTest
  --rerun-tasks)` — `--rerun-tasks` MANDATORY; IC-cache errors → delete
  `runtimes/compose/build/kotlin`, retry; focused classes first, full
  once. SwiftUI (repo root): `xcodebuild test -scheme
  StyleConverterRuntime -destination 'platform=macOS,variant=Mac
  Catalyst,arch=arm64'` (the only local lane). Converter:
  `./gradlew :converter:test --rerun-tasks`.
- Probes in the session scratchpad, NEVER the repo (lanes grep their own
  diff for probe/Probe before finishing).
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
  tagged, disjoint), the FULL suite sweep with exact counts, git-status
  hygiene (every untracked file mapped to a lane; zero probe files; no
  stray `build.gradle` edits; `git stash list` empty — never stash on
  the shared tree), device hygiene, doc-staleness stale-row list.
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

One full sweep on the merged tree (all suites, exact counts — remember
skeptic/fix lanes add tests, so counts move late). Stamp the doc tables
(README.md, CLAUDE.md, docs/STATUS.md `| N |` rows + the tree READMEs'
`(N tests)`) and require `doc-staleness-check.sh` exit 0.

## Phase 5 — the device gate

Build the gate script from the template in BACKLOG "Operational
recipes" (df preflight, reprovision with pm clear, 30 sections ×48,
watchdog+reprovision, 327-net). Run it in background
(`run_in_background: true` — not a shell `&`), heartbeat every ~30 min
checking **capture counts per completed section**, never liveness.

Scoring: full per-section deltas vs the previous snapshot; capture
columns verified against tests.list; every skeptic watchlist cell
checked by name; flips identified per test (LOST cells get diagnosed —
missing-diff = instrument, recover via the single-fixture recipe;
reproducible score drops get adjudicated honest-vs-regression). The new
gate contract (post-overhaul): exit 3 aborts a section (fix the capture,
never the check); exit 4 → fix or ledger with reason/owner/expires;
exit 5 → DELETE the named stale line, that is the whole action; exit 6
→ fix the platform or correct `_expect` with a spec citation.

## Phase 6 — ship

1. Kill emulators/extra sims.
2. Write `tools/titan/results/corpus-v6-<n>.json` (same shape as the
   previous; `_note` carries the full wave story including honest
   losses, instrument events, and prediction misses).
3. Append the wave paragraph to `docs/STATUS.md` (before "## Test
   suites"); doc-staleness green.
4. **Update `docs/BACKLOG.md`**: remove completed queue items, add every
   lane's deferred items with evidence pointers, refresh obligations,
   record new recipes/lessons. A wave PR without a BACKLOG update is
   incomplete.
5. Branch `campaign/wave<N>`, `git add -A` (check for strays first),
   commit with the full story + `Co-Authored-By: Claude Fable 5
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
