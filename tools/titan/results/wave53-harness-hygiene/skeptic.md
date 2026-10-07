# wave 53 · L5 harness-hygiene — SKEPTIC

Tree: the shared tree at HEAD e330e255 (= dev cdb8a845 + the committed wave-53 plan, gate record and tools). I edited NOTHING
in the tree outside this lane directory. Every mutation ran on a byte-identical scratch COPY of the owned files (sha256 of
copy == tree, checked file by file before the first mutation). No gate, section-runner, provision or smoke process was live
(checked with ps). The host Gradle daemon (88966 at the start, which hit its 180-minute idle stop at 10:14; then 78497) and the
adb server 15373 were alive after every run.

Evidence files (repo paths): `skeptic/skmut.py` + `skeptic/skmut.out.txt` (mutation replays), `skeptic/census.py` +
`skeptic/census.lane-time.out.txt` / `skeptic/census.now.out.txt` (my own Gradle census), `skeptic/phases.py` +
`skeptic/phases.out.txt` (pbxproj phases, base vs L5), `skeptic/build-for-testing.out.txt` (bundle contents),
`skeptic/real-and-mini.out.txt` (real-daemon SIGTERM, errexit runs, minimal-bundle phase test, vite --strictPort probe).

## Verdict: CLAIM-HOLDS (two should-fix comment defects in an owned file, one landing hazard for the orchestrator, nits)

Every mechanism claim I could execute holds. Every pin goes red under its mutation and green after a byte-exact restore. My own
census reproduces the lane's numbers exactly. The empty carrier set is confirmed by a phase-by-phase and build-setting diff of
the generated project. I also verified two things the lane listed as NOT verified: SIGTERM on a REAL 9.6.1 daemon, and
vite's `--port` overriding `server.port`.

## Repros (executed) and outputs

1. **Focused suite, shared tree, unmutated**: `node --test own-processes / own-gradle-daemons / own-adb-server /
   section-runner / web-port-guard / interaction-states / smoke-port` → **56/56 pass**.
2. **All 16 lane mutations replayed with MY runner** (`skeptic/skmut.py`; my own replacement strings, not the lane's
   mutate.py). The Gradle mutations ran with `--test-skip-pattern DISCOVERY`, and I ASSERTED that DISCOVERY is filtered out
   (7 of 8 tests run) before G1 could reach a real daemon.

   | id | red (the test named in skmut.out.txt) | restore sha256 | green after |
   |---|---|---|---|
   | G1 predicate → `return 0` | 5 of 7 | c4bbf32c BYTE-EXACT | yes |
   | G2 no `/` boundary | mixed/sibling/nested/no-log | c4bbf32c | yes |
   | G3 roots one at a time | two-root, §8-7a | c4bbf32c | yes |
   | G4 nested walk removed | mixed/sibling/nested/no-log | c4bbf32c | yes |
   | G5 empty log = ours | mixed/sibling/nested/no-log | c4bbf32c | yes |
   | G6 TITAN_GRADLE_PIDS ignored | 5 of 7 | c4bbf32c | yes |
   | A1 any listener | foreign + connected client | c4bbf32c | yes |
   | A2 no `-sTCP:LISTEN` | foreign + connected client | c4bbf32c | yes |
   | S1 pkill back (section-runner, text) | adb source pin | c8f4a1c8 | yes |
   | S2 pkill back (provision-devices, text) | adb source pin | 1ea49ed2 | yes |
   | S3 `--stop` back (gate-driver) | Gradle source pin | 33cfb473 | yes |
   | S4 driver.log routing dropped | driver.log call-site pin | 33cfb473 | yes |
   | P1 default unconditionally | 3 of 6 | dbfcd94e | yes |
   | P2 explicit branch off | 2 of 6 | dbfcd94e | yes |
   | P3 `--strictPort` dropped | smoke.sh source pin | 9c139aed | yes |
   | P4 poll → :3000 | smoke.sh source pin | 9c139aed | yes |

   The restore hashes equal the lane's `mutations.log`.

   My extra mutations:
   - X1 `export WEB_PORT` dropped → red.
   - X4 the ROOT args ignored → red (two-root, §8-7a).
   - X5 fallback range moved to 3300–3399 → red.
   - X6 `kill` → `kill -0` → red (4).
   - X7 / X8 the `source` line dropped in section-runner / provision-devices → red.
   - X9 smoke-port.sh not sourced → red.
   - X10 the production (no-TITAN_GRADLE_PIDS) scan disabled → the DISCOVERY test red. It runs without the skip pattern,
     safely, because the mutation narrows to nothing.
   - **Unpinned (green under mutation): X2** smoke.sh's `kill -0 "$VITE_PID" || break` dropped, and **X3** the `pwd -P` root
     normalisation dropped. X3's mechanism is proven by repro 4 below, but no test pins it.
3. **Census (b), my own script** (`skeptic/census.py`). It re-implements the predicate in Python and does not call the lane's
   bash.
   - At the lane's time: **226** 9.6.1 logs, **216** ours, **9** shared with PROJECT_ROOT plus a scratch export, **1**
     foreign. The live daemon 88966 is LEFT by the default call and STOPPED with `export-l2` added. This equals the lane's
     226 / 216 / 10 (9 shared) and its WOULD-LEAVE.
   - Re-run now: 273 logs (217 ours, 9 shared, 47 new foreign single-use daemons of other sessions' `…/sk/exp`, `…/rv/exp`
     exports). The live daemon is 78497, ours (PROJECT_ROOT/apps/android-harness only), so the default call would stop it.
     88966 is gone: its log ends with the 180-minute idle stop at 10:14, so nothing L5-related stopped it.
   - **PLAN §8 step 7a's shared-daemon list must be re-read at the moment the call is made**: the dry-run first, as the
     lane's window 4 says.
   - adb: `grep -rl 'adb devices timed out'` over **all 29** run dirs under `tools/titan/runs/` (wider than the lane's 12)
     → **0** files. 0 for "restarting the adb server" too.
   - iOS (`skeptic/phases.out.txt`, xcodegen 2.45.4 in two exports, HEAD vs HEAD + L5's four files):
     - app target Frameworks 1, Resources 5 and Sources 9 are SAME;
     - the app target's build settings and the project build settings are equal;
     - only the test target gains CopyFiles(dstSubfolderSpec 7)=[CaptureCanvas.swift] and Resources=[the 2 JSONs].
   - All three gate-path builds (`provision-devices.sh:108`, `test-all.sh:566`, `feed-ios.mjs:463`) are
     `-target StyleConverterTest`.
   - The `web-port-guard.sh` diff is comment-only, and nothing in the gate path references smoke.sh.
   - **Carrier set `[]` on web, iOS and Android: confirmed.** Passing today: web 1229/1372 · iOS 1119/1362 · Android
     1111/1362 (`wave53-gate/score-open.txt`, prev = cur). Nothing reachable.
4. **Real Gradle 9.6.1 daemon, SIGTERM** (the lane could not verify this). Setup: a private `-g` user home and an empty
   project, with the helper called through a SYMLINKED `/tmp` spelling of the root.
   - Output: `stopped [43528] left []`.
   - The daemon exited in **0.59 s**. Its log shows `PersistentDaemonRegistry Removing daemon address`, so it de-registers
     itself.
   - `--status` afterwards: **No Gradle daemons are running**, so no stale registry entry.
   - The next build is rc 0.
   - gate-driver's `sleep 5` before the load check covers the exit.
   - Real 9.6.1 logs: every `currentDir=` occurrence across all of them sits in `Build{id=…, currentDir=<dir>}`; 0 captures
     contain `,` or a space. Every log has exactly one daemon-start line, so no pid-reuse appends.
5. **Errexit / bash 3.2** (`/bin/bash` is 3.2.57 here and is what `#!/usr/bin/env bash` resolves to). Two runs:
   - `kill_wedged_adb_server` under `set -euo pipefail`: with no listener it returns 0 silently; with a fake fork-server
     listener it kills it, rc 0.
   - gate-driver's `log()` + `stop_our_processes()` lifted verbatim and run under `set -uo pipefail` (its real options)
     and under `set -euo pipefail`: both lines land in driver.log, rc 0, ours gone, foreign alive.
   - The real adb server 15373 (`adb -L tcp:5037 fork-server server --reply-fd 4`) is what the selector would pick
     (read-only).
6. **Xcode phase claim** (`skeptic/real-and-mini.out.txt`). Minimal xcodegen bundle, Xcode 27:
   - `buildPhase: resources` DROPS the .swift, with the "cannot be processed by a Copy Bundle Resources build phase" warning;
   - `copyFiles: destination: resources` copies it byte-identical (3da6fc57).
   - So the lane's correction of the brief and the plan is RIGHT.
   - Removing a resource entry and rebuilding into the same DerivedData removes the file from the .xctest. Window M1 therefore
     CAN go red; I suspected a stale-resource masking and refuted it.
   - Full `build-for-testing` of the real export (HEAD + L5 files): **TEST BUILD SUCCEEDED**. The .xctest holds
     CaptureCanvas.swift 3da6fc57…, `wpt__CSS2__…` e7abf8a4… and `wpt__css-cascade__…` 6a252a3c…. The .app root holds no
     .swift or .json.
7. **Payload provenance**: sha256 of the two vendored JSONs equals `wave52-ship` and `wave53-open` `per-test-ir/`
   (e7abf8a41e086063 / 6a252a3c5b38d308).
8. **IcbClip source pins** (`ios-source-pins.mjs`, read-only): 7/7 GREEN and IOS-M1…M7 each RED, on BOTH L3's in-flight
   c737bc38 (shared tree) and the base 3da6fc57 (my scratch copy). `resolvedPadding` is unchanged by L3, and the padding
   arithmetic is 16 / 56 / 20-25 / 16 from the base text. The window script's M2/M3 anchors occur exactly once in 762d3d30's
   text, and 762d3d30's runtimes and apps equal cdb8a845.
9. **cells.mjs**, wave51-fix → wave52-ship → wave53-open: static-inside-inline-block ios P 0.9974 ×3; scope-pseudo-element
   ios f 0.9643 → f 0.9668 → f 0.9668; align-items-007 ios f 0.9974 → P 0.9974 → P 0.9974; flex-gap-decorations-027 ios
   f 0.903 → P 0.9997 → P 0.9997; clip-path-circle-007 ios P 0.9993 ×3. All the lane's quoted values are exact.
10. **PNGs looked at**:
    - scope-pseudo-element: ref vs wave52-ship ios. Three 102×102 boxes in ONE row. B / A / M are missing, the marker is
      "•" instead of "M", and the blue fills are text-width instead of line-width. The lane's description holds; the pin
      guards the row, not the pseudo content.
    - static-inside-inline-block: ref vs ios. The green square sits at x≈121, and only "no red" is not bold. Holds.
11. **vite** (`BROWSER=none`, apps/web-harness): `--port 3477` overrides `server.port: 3000`, which confirms the MED-HIGH
    sub-claim. `--strictPort` did NOT refuse a port a node server held on `[::]`: vite co-bound `[::1]:3477`. See nit 6.
    The probe's vite was reparented to launchd after my perl alarm; I stopped it (it was mine) and the port is free.
12. **Hygiene**:
    - the four seam files are clean in `git status`;
    - `tools/titan/runs/wave53-lock/` is empty;
    - no seam or hunk patch was needed (none exists);
    - no probe leftovers ("probe" in the diff only names the Tier 5/11 probes);
    - no TODO/FIXME;
    - no scratchpad EVIDENCE pointer in the note: the scratch paths there are command arguments, the TREES line and the
      export-l2 root that must be passed;
    - the lane's TREES claim (no Gradle) is consistent: no daemon log names `l5x` or `xw`;
    - no other lane's note claims an L5 path;
    - `wave52-gate/build-hashes.txt` is the orchestrator's gate record, not L5's.

## Defects, ranked

1. **should-fix**: `apps/ios-harness/StyleConverterTestTests/ComposedCanvasIcbClipTests.swift` l.23-25 (header) and l.63
   (`canvasSource()` doc) say project.yml copies CaptureCanvas.swift in with **`buildPhase: resources`**. That is exactly the
   phase that silently drops a .swift (lane-measured, and re-measured in repro 6). project.yml uses
   `buildPhase: copyFiles: destination: resources`. A maintainer who "aligns" project.yml with the comment breaks all 7 pins.
   Fix: name the Copy Files phase (dstSubfolderSpec 7), as project.yml's own comment does.
2. **should-fix (honesty)**: the same file, l.52-54, says the XCTest's "first executed Swift mutation (IOS-M1 on the real
   file) **is recorded** in …/_note.md". It is not: it is ORCHESTRATOR WINDOW 1, still unrun, and the note's §4 says so.
   Fix: "will be recorded … once the orchestrator's window 1 runs", or update it after the window.
3. **should-fix (landing hazard, orchestrator)**: three of L5's new files are NOT in the PLAN's L5 `own:` list:
   `tools/visual/smoke-port.sh`, `tools/titan/own-gradle-daemons.test.mjs` and `tools/titan/own-adb-server.test.mjs`. The
   lane's note §1 lists them, and their split is justified by the house rule. But a revert unit committed strictly from the
   PLAN list would ship a smoke.sh that `source`s a missing file (`set -e` → smoke aborts at line 41), and would drop 11 pins.
   Fix: commit them in L5's unit (`expectations.json` revertUnits.L5 "tests" should name them).
4. **nit**: full (non-`--quick`) smoke now runs `BASELINE=1 ./test-all.sh` with smoke's picked `WEB_PORT` EXPORTED, so
   test-all takes it as CALLER-CHOSEN (`WEB_PORT_EXPLICIT=1`, test-all.sh:296). It is benign in every case I can construct:
   smoke's vite is ours and gets cleared, and a 34xx port is free after stop_vite. One case changes behaviour: a foreign
   program that takes the port in between now makes test-all exit 2 instead of moving to 3300–3399. It is undocumented
   and untested. Fix: `unset WEB_PORT` after `stop_vite`, or say so in the comment.
5. **nit**: two branches are unpinned (X2, X3): the vite fast-fail `kill -0 … || break`, and the `pwd -P` root
   normalisation. The latter is proven to work by repro 4, but no test fails without it. Add a symlinked-root case.
6. **nit**: the smoke.sh comment "--strictPort: vite must bind $WEB_PORT or exit" is true, but it does not mean exclusive.
   On macOS vite co-binds `[::1]:P` beside a foreign `[::]:P` (repro 11). The guard that refuses a foreign holder is
   `smoke_pick_web_port`'s lsof check, which runs before vite starts, so the logic is right and only the comment
   over-reassures.
7. **nit**: `kill_wedged_adb_server`'s header says "only a wedged one is hit". A healthy server that another session's adb
   client auto-starts between `kill-server` and the check would also be SIGKILLed. That is still a strict subset of the old
   `pkill -x adb`.
8. **nit**: `own-gradle-daemons.test.mjs` is 208 lines (target ≤ 200; under the ~300 split line).
9. **nit**: the `xctest-window.sh --build-only` log behind §4's "the export compiles green; M1, M2 and M3 each compile; M1's
   bundle lacks …" is not under the lane dir ("log replayed in the lane"). My `skeptic/build-for-testing.out.txt` now covers
   the green build-and-bundle half; the M1–M3 compile half remains uncommitted evidence.

## Predictions re-checked

- XCTest 30/30: padding 4 HIGH (arithmetic re-derived from the base resolver); IcbClip 7 HIGH (node replay 7/7 on both texts;
  bundle carries 3da6fc57 byte-identical); InlineFlow 2 MED (compiles, payloads byte-identical; never executed). Agreed.
- Corpus 0/0/0 (HIGH): agreed — carrier set empty by phase/build-setting diff.
- driver.log: 0 `gradlew --stop`, exactly 1 `kill_own_gradle_daemons roots=` line (HIGH): agreed (repro 5, under the driver's
  own options).
- smoke foreign-listener (MED-HIGH): raise the vite-`--port` sub-claim to verified; the rest still needs window 3.
- No DEGENERATE labelling question arises: L5 claims no corpus flip.

## What I could NOT check

- The XCTest RUN and the window mutations M1–M3 going red. They need a booted simulator (rule 4). I verified the build and the
  bundle contents, the M1 stale-resource question and the anchors, but not an executed XCTest result.
- smoke.sh end-to-end (Chromium) and the window-3 foreign-listener check.
- gate-driver end-to-end (`wave53-hh-probe`): provisioning on the new driver, and capture neutrality by decoded pixels.
- A REAL wedged adb server being killed. I only selected the real one read-only and killed fakes.
- The CI (ubuntu-latest) behaviour of the 17 new tooling tests (pgrep/ps/lsof on Linux): no Linux runtime on this host
  (docker daemon down).
- In-place `xcodegen generate` in the shared tree: I ran it only in exports, as the plan reserves the in-place run for a
  device-idle window.

TREES: /Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf (read-only for runtime and
harness code). The skeptic's only Gradle was ONE throwaway 9.6.1 daemon in a private `-g` user home over an empty scratch
project (repro 4), stopped afterwards through that private registry. It does not appear in `~/.gradle/daemon/`.

STATUS: COMPLETE
