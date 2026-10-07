# wave 53 · L5 harness-hygiene — lane note

Contract: `tools/titan/results/wave53-plan/PLAN.md` "### L5 · harness-hygiene" (T1–T4; T5 OUT), brief
`harness-hygiene.md`. Base: HEAD `762d3d30` (= dev cdb8a845 + the committed wave-53 plan/gate record; no runtime
source differs). Worked on the SHARED tree (PLAN §10 item 3). No commit, stash or checkout. Seam files untouched;
no seam patch and no hunk for another lane were needed. Host idle-checked (no gate-driver, section-runner,
provision-devices, test-all, smoke or qemu process) before every gate-script edit and before each mutation run.
The pre-edit hashes of every owned file are in `base-hashes.txt`.

## 1. What changed, and why

### T2 — `./gradlew --stop` ×2 → `kill_own_gradle_daemons` (standing constraint "never stop a process this checkout did not start")
- `tools/titan/own-processes.sh`:
  - `_op_dir_in_root` (l.100): checks the `/` boundary (`<root>-x` is a sibling). It also refuses a dir inside a
    checkout NESTED under the root: walking up from the dir while it is still strictly below the root, a `.git` on
    the way means another checkout, e.g. `.claude/worktrees/<w>` of the main checkout. This goes beyond the brief,
    and only in the conservative direction (it never kills more). Pinned, mutation G4.
  - `_op_gradle_daemon_is_ours` (l.116): ours iff the daemon log has ≥ 1 `currentDir=` AND every one of them is
    inside ONE OF the given roots. The roots are evaluated together, so one line may sit in any root.
  - `kill_own_gradle_daemons [ROOT…]` (l.133): default root `$PROJECT_ROOT`; roots normalised with `pwd -P`.
    - Candidates come from `pgrep -f 'org.gradle.launcher.daemon.bootstrap.GradleDaemon '`. The version is read
      from argv (`…GradleDaemon 9.6.1`, read off the live daemon); the log is
      `${GRADLE_USER_HOME:-~/.gradle}/daemon/<ver>/daemon-<pid>.out.log`.
    - SIGTERM goes to ours. Every other daemon gets a stderr line naming the dirs it served, or "no log at …".
    - One summary line: `kill_own_gradle_daemons roots=[…]: stopped […] left […]`.
    - `TITAN_GRADLE_PIDS` (test seam) can only NARROW the scan, never add a pid. It is what lets every Gradle
      mutation, including "unconditional kill", run on this host without reaching a real daemon (§4).
- `tools/titan/gate-driver.sh` `stop_our_processes` (l.124-145): the two `--stop` lines are replaced by
  `while IFS= read -r line; do log "$line"; done < <(kill_own_gradle_daemons 2>&1)`, so every left/summary line
  lands in `driver.log` (the `expectations.json` L5 geometry line). The `JAVA_HOME` export stays, because
  provisioning uses it. The "STILL HOST-WIDE" comment is gone.

### T3 — `pkill -9 -x adb` ×2 → `kill_wedged_adb_server`
- `own-processes.sh` `kill_wedged_adb_server` (l.170): `kill -9` only the pid that LISTENS on
  `${ANDROID_ADB_SERVER_PORT:-5037}` and whose argv contains `fork-server server`. Any other listener there is
  named and left.
- `tools/titan/section-runner.sh`: `source` at l.83, beside the web-port-guard source; call at l.645.
- `tools/titan/provision-devices.sh`: `source "$SCRIPT_DIR/own-processes.sh"` at l.48; call at l.131.
- Both calls stay `;`-chained after the bounded `kill-server`, exactly where the pkill was. A server that honoured
  `kill-server` no longer listens, so only a wedged one is hit.

### T4 — smoke.sh hard-wired to :3000
- **NEW `tools/visual/smoke-port.sh`** (43 lines), function `smoke_pick_web_port [DEFAULT=3000]`:
  - a caller-chosen `WEB_PORT` held by a foreign process → error, rc 1;
  - our own stale vite on the port → cleared through `wpg_kill_our_vite_on_port`;
  - the default port held by a foreign process → `wpg_free_port 3400 3499`, and the holder is named.
- `tools/visual/smoke.sh`:
  - sources `web-port-guard.sh` + `smoke-port.sh`;
  - `start_vite` runs `npx vite --port "$WEB_PORT" --strictPort` (was `npm run dev`) and polls
    `http://localhost:${WEB_PORT}/`;
  - one added condition, `kill -0 "$VITE_PID" || break`: a vite that exited fails at once instead of waiting
    30 s;
  - the run section picks the port, then `export WEB_PORT`, then `start_vite`, then Tier 5 / Tier 11.
  - **Orchestrator addendum (window 3)**: `build_fixtures` runs before the port pick — the probes' fixture JSONs are gitignored
    converter output smoke.sh never built; on a fresh worktree both tiers timed out on every probe until it did (results
    item 3 below).
- `tools/visual/web-port-guard.sh`: header note of the disjoint ranges: section-runner 3100–3299, test-all /
  probe-text-metrics 3300–3399, smoke 3400–3499.

### T1 — ios-harness XCTest 17/30 → 30/30 (two defects)
- **T1-a (257, 9 tests)**: `apps/ios-harness/project.yml`, test target `sources:` gains three entries.
  - `StyleConverterTest/Screenshot/CaptureCanvas.swift` goes in with **`buildPhase: copyFiles: destination:
    resources`**, not `buildPhase: resources`. That is a MEASURED correction of the brief: Xcode 27 warns
    "The Swift file … cannot be processed by a Copy Bundle Resources build phase" and silently leaves the file out
    of the bundle. The first build had no CaptureCanvas.swift in the .xctest, so all 7 IcbClip pins would have
    gone red with "not a resource".
  - The two vendored JSONs go in with `buildPhase: resources`.
  - The tests read them through `Bundle(for: Self.self).url(forResource:withExtension:)` + `XCTUnwrap("… is not a
    resource of StyleConverterTestTests — project.yml")`. There is no `#filePath` fallback and no skip guard
    (`ComposedCanvasIcbClipTests.canvasSource`, `ComposedRootInlineFlowTests.composedRoots`; the `repoRoot` helper
    is deleted). Both file headers are updated.
  - `tools/titan/fixtures/README.md`: the consumers paragraph and table name the bundle-resource exception.
- **T1-b (stale contract, 4 tests)**: `ComposedCanvasPaddingTests.swift` moves to the wave-25 round-3
  "frame + declared" rule, as `resolvedPadding`'s doc comment states it (CSS 2.1 §8.4 for the clamp). The tests
  are renamed after the Compose twin:
  - `testZeroedBodyPadKeepsTheImageFrameOnEverySide`: 16 ×4;
  - `testResolutionIsPerSideUndeclaredSidesKeepTheBareFrame`: left `refPad + 40` = 56, the others 16;
  - `testPhysicalSidesMapToLeadingTrailingCorrectly`: `refPad + 4` / `refPad + 9` = 20 / 25;
  - `testNegativePadClampsToZeroLeavingTheBareFrame`: 16.
  The header gains the wave-25 paragraph. The test count stays 30.

### Where the tree overrode the brief or plan
1. Copy Files phase for the .swift resource (above). The §8.1 pbxproj greps therefore become
   `CaptureCanvas.swift in Sources` ×1 (app) + **`CaptureCanvas.swift in CopyFiles`** ×1 + each
   `wpt__….json in Resources` ×1.
2. NEW files forced by the house rule (new logic in new files ≤ 200 lines; split past ~300):
   - smoke.sh was already 312 lines → `tools/visual/smoke-port.sh`;
   - the wave-53 pins would have made `own-processes.test.mjs` 348 lines → `tools/titan/own-gradle-daemons.test.mjs`
     (208 lines) and `tools/titan/own-adb-server.test.mjs` (95). `own-processes.test.mjs` keeps its five wave-52
     pins plus a 4-line pointer.

   No other lane names these paths. They belong to L5's single revert unit.
3. The adb decoy is a CONNECTED fork-server client, not an idle process. That makes the `-sTCP:LISTEN` filter
   load-bearing (mutation A2).
4. An empty daemon log (zero `currentDir=`) is not ours (the brief's "≥ 1 `currentDir=`"; mutation G5).

## 2. Blast-radius census (method → result) — the lane's CARRIER SET is `[]` on web, iOS and Android
- **iOS build** (`pbxproj-phases.py export.base.project.pbxproj export.project.pbxproj`, exit 0 = app target
  unchanged). Both files are generated in place in an export of HEAD (base project.yml vs L5 project.yml):
  - `StyleConverterTest` Frameworks 1, Resources 5 and Sources 9 are all SAME. (The shared tree's Resources
    phase holds 6, because it adds the untracked `tmpOutput.json`.)
  - Only `StyleConverterTestTests` changes: CopyFiles gains [CaptureCanvas.swift] and Resources gains [the 2
    JSONs].
  - The gate and the fixture net build `-target StyleConverterTest` only (`provision-devices.sh:108`,
    `test-all.sh:566`, `feed-ios.mjs:463`). So no test-target file enters the installed app.
  - Built-bundle check (device-free build-for-testing, generic simulator): the `.xctest` holds
    `CaptureCanvas.swift` (sha256 3da6fc57…, identical to the source), `wpt__CSS2__…static-inside-inline-block.json`
    (e7abf8a4…) and `wpt__css-cascade__scope-pseudo-element.json` (6a252a3c…). The .app root holds no `.swift` and
    no `wpt__*.json`.
- **Vendored payloads are the verbatim wave52-ship wire.** `shasum -a 256` of the two vendored files equals
  `tools/titan/runs/wave52-ship/sections/{CSS2,css-cascade}/per-test-ir/<same name>` AND the `wave53-open` copies.
- **Gradle** (`gradle-daemon-census.sh` → `gradle-daemon-census.out.txt`; read-only, only the predicate is called):
  - 226 9.6.1 logs on this host. 216 are ours by PROJECT_ROOT alone. 10 are not, and 9 of those pair PROJECT_ROOT
    with a foreign tree.
  - **The live daemon pid 88966 WOULD BE LEFT**: it served `PROJECT_ROOT/apps/android-harness` AND
    `/private/tmp/claude-501/…/scratchpad/export-l2/apps/android-harness` (L2's export tree, this wave). This is
    the measured shared-daemon shape of PLAN §8 step 7a, live today; see §6 hand-off 4.
  - Gradle outputs do not depend on which daemon builds them (section-runner converts with `--no-daemon`), so T2
    reaches no capture.
- **adb**: `grep -rl 'adb devices timed out'` over `tools/titan/runs/{wave50-final, wave51-fix, wave51-open,
  wave52-open, wave52-calib, wave52-final, wave52-probe, -probe2, -probe3, wave52-preview, wave52-ship,
  wave53-open}` → 0 files. section-runner's timeout branch never fired. **provision-devices' `_adb_devices`
  branch logs nothing, so its firings cannot be counted** (§6 hand-off 6). If it does fire, the new helper kills a
  subset of what the old `pkill` killed: one listener instead of every process named adb.
- **smoke.sh** is not in the gate path: nothing in gate-driver.sh, section-runner.sh, provision-devices.sh or
  test-all.sh references it.
- **Cross-check against `expectations.json` `lanes.L5-harness-hygiene`**: `captureCarriers` {web: [], ios: [],
  android: []} and `wireCarriers` [] match this census. Passing today, from the plan header (wave52-ship, equal on
  wave53-open within its 29 sections): web 1229/1372 · iOS 1119/1362 · Android 1111/1362. None of them is
  reachable by this lane.

## 3. Pictures looked at
- `css-cascade/scope-pseudo-element` ios (wave52-ship capture vs the frozen ref):
  - the three 102×102 inline-block roots pack as ONE row, which is the run the B8 pin protects;
  - the ::before/::after/::marker content (B, A, M) is missing, which is why it scores f 0.9668. The pin guards
    the row, not the pseudo content.
- `CSS2/abspos/static-inside-inline-block` ios: the green square sits at x≈121 (the middle atom of the one run), as
  in the ref. Only "no red" in bold differs (P 0.9974).
- No picture can change through this lane (empty carrier set).

## 4. Pins and EXECUTED mutations (red → restore byte-exact → green)
Runner: `mutate.py`. Log: `mutations.log` (final bytes) and `mutations.round1.log` (the first 11, before the test
split). The Gradle mutations run with `--test-skip-pattern DISCOVERY`. Every remaining Gradle pin carries
`TITAN_GRADLE_PIDS`, so even G1 ("unconditional kill") only ever saw this file's fake daemons. The live host
daemon 88966 and the adb server pid 15373 were alive after every run (checked). **The exclusive Gradle window
the plan asked for is therefore not needed.**

| id | mutation | red (tests) | restore sha256 |
|---|---|---|---|
| G1 | `_op_gradle_daemon_is_ours` → `return 0` | 5: own-checkout/foreign, mixed/sibling/nested/no-log, two-root, §8-7a, driver.log | c4bbf32c… BYTE-EXACT |
| G2 | root test without the `/` boundary | mixed/sibling/nested/no-log | c4bbf32c… BYTE-EXACT |
| G3 | roots evaluated one at a time (the plan's per-root mutation) | two-root, §8-7a | c4bbf32c… BYTE-EXACT |
| G4 | nested-checkout walk removed | mixed/sibling/nested/no-log | c4bbf32c… BYTE-EXACT |
| G5 | empty log counted as ours | mixed/sibling/nested/no-log | c4bbf32c… BYTE-EXACT |
| G6 | `TITAN_GRADLE_PIDS` ignored | 5 (narrowing pin included) | c4bbf32c… BYTE-EXACT |
| A1 | any listener taken for the server | foreign listener + connected client | c4bbf32c… BYTE-EXACT |
| A2 | `-sTCP:LISTEN` dropped | foreign listener + connected client | c4bbf32c… BYTE-EXACT |
| S1 | `pkill -9 -x adb` restored in section-runner.sh (text only, never executed) | adb source pin | c8f4a1c8… BYTE-EXACT |
| S2 | same in provision-devices.sh (text only) | adb source pin | 1ea49ed2… BYTE-EXACT |
| S3 | `./gradlew --stop` restored in gate-driver.sh (text only) | Gradle source pin | 33cfb473… BYTE-EXACT |
| S4 | driver.log routing dropped (bare `kill_own_gradle_daemons`) | driver.log call-site pin | 33cfb473… BYTE-EXACT |
| P1 | picker returns the default unconditionally | default-held → 34xx; explicit-held → error | dbfcd94e… BYTE-EXACT |
| P2 | explicit `WEB_PORT` branch disabled | explicit-held → error; explicit-free honoured | dbfcd94e… BYTE-EXACT |
| P3 | `--strictPort` dropped from smoke.sh | smoke.sh source pin | 9c139aed… BYTE-EXACT |
| P4 | readiness poll hard-wired back to :3000 | smoke.sh source pin | 9c139aed… BYTE-EXACT |

Green after every restore. The focused line (PLAN §2 L5 "May run", plus the two new files) is in
`focused-suite.out.txt`: `node --test own-processes / own-gradle-daemons / own-adb-server / section-runner /
web-port-guard / interaction-states / smoke-port` → **56/56**.

New tooling tests: own-gradle-daemons 8 + own-adb-server 3 + smoke-port 6 = **+17**. own-processes.test.mjs stays
at 5.

iOS (device-free parts, executed):
- `xctest-window.sh <dir> --build-only` (log replayed in the lane): the export compiles green; M1, M2 and M3 each
  compile; M1's bundle lacks the scope-pseudo-element JSON; every restore is BYTE-EXACT (project.yml
  22ad24a0…, CaptureCanvas.swift 3da6fc57…).
- `tools/titan/results/wave52-composed-canvas/ios-source-pins.mjs` (read-only replay of the 7 IcbClip pins):
  7/7 GREEN on 3da6fc57, and **7/7 GREEN on L3's in-flight CaptureCanvas.swift c737bc38** (shared tree,
  05:51); IOS-M1…M7 each RED. L3's diff leaves `resolvedPadding` untouched (M3's anchor occurs once).
- The XCTest RUN and the M1–M3 reds need a booted simulator: ORCHESTRATOR WINDOW, below.
- Tried and abandoned: the "My Mac (Designed for iPad)" destination. `xcodebuild test` must INSTALL the app into
  the system (installd), and an unsigned app is refused ("integrity could not be verified"). Nothing was
  installed. Signing and installing an app on the user's Mac is not a lane's call.

## 5. Predictions (confidence)
- ios-harness XCTest 17/30 → **30/30**.
  - Padding 4: **HIGH**. The pins assert exactly the shipped arithmetic (16 + max(0, px)).
  - IcbClip 7: **HIGH**, up from HIGH-MED. The bundle is now measured to carry the byte-identical source, and the
    replay is 7/7 on both the base text and L3's in-flight text.
  - InlineFlow 2: **MED**. Never executed in Swift. The payloads are in the bundle byte-identical, and the Kotlin
    twin passes on the same documents.
  - The 17 previously passing tests: HIGH.
- Corpus: **0 gained / 0 lost / 0 movers** attributable to L5 (**HIGH**). The `wave53-hh-probe` control against
  `wave53-open` with the empty carrier set should show 0 changed by decoded pixels on iOS and Android (HIGH). Web
  depends on the host's run-to-run determinism, which is what that probe measures.
- `driver.log` of every later run: 0 `gradlew --stop`, exactly 1 `kill_own_gradle_daemons roots=…` summary line,
  and one named line per daemon left (**HIGH**, behavioural pin S4).
- smoke `--quick` with a foreign 200-server on :3000: the guard names it, Tier 5/11 run on 34xx, and the server
  survives (**MED-HIGH**: vite's CLI `--port` overriding `server.port` is not run here).

## 6. Must-not-move cells (wave52-ship → wave53-open, `cells.mjs`)
`CSS2/abspos/static-inside-inline-block ios P 0.9974 → P 0.9974` · `css-cascade/scope-pseudo-element ios f 0.9668 →
f 0.9668` · `css-flexbox/align-items-007 ios P 0.9974 → P 0.9974` · `css-gaps/flex/flex-gap-decorations-027 ios
P 0.9997 → P 0.9997` · `css-masking/clip-path/clip-path-circle-007 ios P 0.9993 → P 0.9993`. Every other capture
must stay identical too (decoded pixels on web, R4).

## ORCHESTRATOR WINDOW REQUESTS
1. **XCTest + M1–M3 on the base text** (booted simulator, device idle). The script never edits the shared tree:
   it exports `git archive 762d3d30` + L5's four iOS files into `<dir>/x`.
   `bash /Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf/tools/titan/results/wave53-harness-hygiene/xctest-window.sh /tmp/wave53-l5-xctest <booted-simulator-udid>`
   Look for, in `/tmp/wave53-l5-xctest/xctest-window.log`:
   - the four pbxproj lines at "2 mentions";
   - `green: passed 30 failed 0`;
   - `M1: passed 2 failed 1` — testScopePseudoElementKeepsItsPassingRootRun, "…scope-pseudo-element.json is not a
     resource of StyleConverterTestTests — project.yml";
   - `M2: passed 6 failed 1` — testClipPrecedesTheCanvasBackground;
   - `M3: passed 4 failed 4` — the four renamed padding tests;
   - every `restored … BYTE-EXACT`;
   - `green-after: passed 30 failed 0`.

   Paste the summary lines into this note.
2. **Integrated L5 + L3 tree** (§4 step 7 sweep, in place):
   `(cd apps/ios-harness && xcodegen generate && xcodebuild test -project StyleConverterTest.xcodeproj -scheme StyleConverterTestTests -destination 'platform=iOS Simulator,id=<booted>')` → 30/30.
   Then `grep -c 'CaptureCanvas.swift in CopyFiles' apps/ios-harness/StyleConverterTest.xcodeproj/project.pbxproj` = 2.
3. **smoke foreign-listener check** (launches vite + Chromium; it also runs doc-staleness and the FULL tooling
   suite, so orchestrator only). Do it only if nothing else holds :3000
   (`lsof -nP -iTCP:3000 -sTCP:LISTEN` empty):
   `mkdir -p /tmp/wave53-l5-smoke && cd /tmp/wave53-l5-smoke && (python3 -m http.server 3000 >/dev/null 2>&1 & echo $! > pid) && cd - && bash tools/visual/smoke.sh --quick; kill -0 $(cat /tmp/wave53-l5-smoke/pid) && echo FOREIGN-ALIVE; kill $(cat /tmp/wave53-l5-smoke/pid)`
   Look for:
   - `[web-port-guard] port 3000 is held by pid <python>`;
   - `[smoke-port] port 3000 is in use by another program — using 34xx for this smoke`;
   - `starting vite on :34xx`;
   - the Tier 5 `captured · … failed · … skipped` line and the Tier 11 `fixtures scored on color-contrast` line;
   - `FOREIGN-ALIVE`.

   A doc-staleness red there is the doc counts (+17 tooling tests from L5 alone), not T4.
4. **§8 step 7a before `wave53-hh-probe`, now MEASURED necessary.** Live daemon 88966 served PROJECT_ROOT AND L2's
   export tree, so gate-driver's default call will LEAVE and name it. ONE call:
   `source tools/titan/own-processes.sh && kill_own_gradle_daemons "$PWD" <every export tree from the lanes' TREES: lines, e.g. /private/tmp/claude-501/-Users-dranak-Documents-Projects-Style-Converter--claude-worktrees-trusting-bohr-bd6fbf/0c47cad8-074d-4821-bf4c-b5997f23f535/scratchpad/export-l2>`
   Record the stopped/left line. Then run `./gradlew --status` (read-only). The brief's risk: SIGTERM instead of
   `--stop` may leave a stale registry entry. Never verified on 9.6.1; check that the status lists no dead daemon
   as BUSY/IDLE.
   Dry run first, read-only: `bash tools/titan/results/wave53-harness-hygiene/gradle-daemon-census.sh "$PWD" <trees…>`
   prints WOULD-STOP/WOULD-LEAVE per live daemon.
5. **`wave53-hh-probe`** (quiet host, after 4):
   `tools/titan/gate-driver.sh wave53-hh-probe --sections css-cascade,css-counter-styles,css-flexbox,css-text --skip-fixture-net`. Then:
   - `grep -c 'gradlew --stop' tools/titan/runs/wave53-hh-probe/gate-driver/driver.log` = 0;
   - `grep -c 'kill_own_gradle_daemons roots=' …/driver.log` = 1;
   - `grep 'provision rc=0' …/driver.log`;
   - every section `OK`, with full columns;
   - the control against `wave53-open` with the EMPTY L5 carrier set → 0 changed captures by decoded pixels on all
     three platforms.
6. Optional, device-free, safe at any time (~2 min; no exclusive window, because G1 is narrowed):
   `python3 tools/titan/results/wave53-harness-hygiene/mutate.py all`.

## Hand-offs (orchestrator-owned docs / later waves)
1. BACKLOG Known-broken, ios-harness entry:
   - 9 of the 13 failures were NSCocoaErrorDomain 257 (the simulator is refused `~/Documents`); they are fixed by
     bundle resources;
   - 4 were stale wave-24 assertions, now on the wave-25 contract;
   - an Xcode fact for the recipes: a `.swift` file cannot be a Copy Bundle Resources member (silently dropped,
     with a warning); use a Copy Files phase with destination resources.
2. BACKLOG Gradle direction: daemon cwd is `~/.gradle/daemon/<ver>`. Ownership comes from
   `daemon-<pid>.out.log` `currentDir=` lines. Done in `kill_own_gradle_daemons`.
3. T5 stays: `gate-driver.sh` `start_adb_detached` runs `adb kill-server` unconditionally at every provision. Add it
   to Known-broken as the third host-wide stop (the library header says so).
4. PLAN §8 step 7a under the shared-tree decision: the lanes' Gradle ran in PROJECT_ROOT, so PROJECT_ROOT covers
   them, but every EXPORT tree that ran Gradle must be named in the same call (live evidence above: L2's
   `export-l2`).
5. Doc counts: the tooling suite grows by 17 from L5 (own-gradle-daemons 8, own-adb-server 3, smoke-port 6). The
   ios-harness count stays 30. `tools/visual/doc-staleness-check.sh:194-200` still WARNs "no documented sweep row
   runs it", while CLAUDE.md already has the ios-harness row. That WARN text is stale; the file is not L5's.
6. provision-devices `_adb_devices` timeout branch emits no log line, so its firings cannot be counted. Suggest a
   `warn` like section-runner's (a later wave; that line is outside L5's two-hunk ownership).
7. The CaptureCanvas.swift M2 run must stay on the base text (L3 owns the file and has edited it). The window
   script exports 762d3d30, so it does.

## What I could NOT verify
- The XCTest execution: 30/30, and M1–M3 turning red. Compiled only; it needs the simulator (window 1).
- A REAL Gradle daemon receiving SIGTERM, and the daemon registry afterwards: only fake daemons were signalled
  (window 4).
- A REAL wedged adb server being killed: only fake listeners were (the T3 branch fired 0 times in 12 runs).
- smoke.sh end-to-end, i.e. vite `--strictPort` on 34xx and the Tier 5/11 probes (window 3).
- gate-driver end-to-end: provisioning, `driver.log` on a real run, capture neutrality (window 5).
- `xcodegen generate` in place in the shared tree. It ran only in exports (identical relative layout); test-all
  regenerates in place at the next gate (R8 fixture net).

TREES: /Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf (this lane ran NO Gradle anywhere; its only builds were Xcode builds in scratch exports — `…/scratchpad/l5x`, `…/scratchpad/xw/x` — with scratch DerivedData, never the shared tree's xcodeproj)

STATUS: COMPLETE

## Orchestrator window results (2026-10-07, device idle, shared tree)

4. **Gradle daemons** — `orchestrator-windows-1.out.txt`: census (dry run) found one live 9.6.1 daemon (pid 78497) that served
   only `…/trusting-bohr-bd6fbf/apps/android-harness` → WOULD-STOP; `kill_own_gradle_daemons` with the one root: `stopped [78497]
   left []`; `./gradlew --status` afterwards: "No Gradle daemons are running" — no dead daemon left in the registry (the brief's
   SIGTERM-vs-`--stop` risk did not materialise on 9.6.1).
1. **XCTest on the exported base + the four iOS files** (`xctest-window.log.txt`, booted iPhone 17 Pro): pbxproj carries the
   four expected lines at 2 mentions each; **green: 30 passed 0 failed**; M1 (resource removed from project.yml) → 2 passed 1
   failed (`testScopePseudoElementKeepsItsPassingRootRun`), M2 (CaptureCanvas.swift mutated) → 6 passed 1 failed
   (`testClipPrecedesTheCanvasBackground`), M3 → 4 passed 4 failed (the four padding tests); every mutation restored BYTE-EXACT
   (sha256); **green-after: 30 passed 0 failed**. The 13 simulator-blocked tests of the wave-52 record are unblocked by the
   resource bundling, as predicted (HIGH / HIGH-MED / MED all held).
3. **smoke foreign-listener check** (`orchestrator-windows-2.out.txt` window 3) — against the developer's own API server on
   :3000 (pid 87017, the REAL case): the guard named it and left it, smoke-port moved vite to :3400 (`--strictPort`), vite
   came up — T4's port move works. **But both tiers then failed every probe** (Tier 5: 0 captured · 90 failed; Tier 11: 15
   errored, all `fixture-ready-timeout`): `smoke.sh` has never run `npm run build-fixtures`, the gitignored
   `apps/web-harness/public/fixtures/<Name>.json` the probes fetch did not exist on this fresh worktree, and vite's SPA
   fallback served index.html for the missing JSON. A pre-existing gap the port fix exposed, not a T4 regression. **Fixed in
   this lane's ownership**: `smoke.sh` gains `build_fixtures` (JDK 21 as test-all picks it; idempotent; a failed build skips
   vite and the tiers with a named reason). Re-run: fixtures 0 built · 15 skipped; Tier 5 **90 captured · 0 failed**; Tier 11
   **15/15 scored · 15 clean**; our vite stopped, pid 87017 still listening after. smoke's rc=1 came from the doc-staleness
   step alone (uncommitted lane-test count drift, restamped after the sweep). Mutation record: attempt 1 is the step-absent
   state on the same tree.
5. **`wave53-hh-probe`** (`orchestrator-windows-2.out.txt` window 5; 11:15 → 11:29 UTC; 4 sections OK on attempt 1, every
   column full): **DEVIATION — the tree carried every lane's unseamed edits, not L5's alone**, so the probe measured same-host
   determinism plus the union of the lanes' pre-seam radii. Union control (`wave53-gate/control-hh-probe-union.json`): web
   186/187 byte-identical + 1 carrier (web is byte-deterministic run-to-run on this host; the "re-encoded" class is unused),
   iOS 186/187 + 1, Android 178/187 + 9 carriers, wire 3 content-changed carriers + 15 renumbered (the cssom id shadow of
   counter-suffix +6, PLAN §10 item 6) — **CONTROL HOLDS**. `--lane L5-harness-hygiene` on the same pair FAILS on L1+L2's
   carriers (`control-hh-probe-L5.json`), as a shared-tree probe must; L5's "no capture, no wire change" prediction is re-read
   at the closing gate. The driver's own-processes path ran (`kill_own_gradle_daemons roots=[this tree]`); pid 87017 untouched.
