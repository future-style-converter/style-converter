# Wave 53 brief — family `harness-hygiene`

Read-only brief, written 2026-10-07 while `wave53-open` ran. Nothing was built, run or edited outside this directory.
Evidence: the ios-harness XCTest result bundle of 2026-10-06 22:22 (read with `xcrun xcresulttool get test-results
summary`), the tree at cdb8a845 (= `wave52-ship`), one `ps` / `lsof` read of the live Gradle daemon and adb server, and
the census `harness-hygiene.census.json` beside this file. BACKLOG items covered: "## Known-broken" → "The ios-harness
XCTest scheme runs again, and 13 of its 30 tests fail", "`tools/visual/smoke.sh` still hardcodes port 3000", "Two
host-wide stops remain in the gate path"; standing constraint "Capture and gate scripts must never stop a process this
checkout did not start".

## 0. Timing — read first

Every file this lane changes is something the gate runs or builds from: `test-all.sh:491-495` deletes the xcodeproj and
re-runs `xcodegen generate` from `apps/ios-harness/project.yml` on every run (the fixture net included);
`gate-driver.sh`, `section-runner.sh`, `provision-devices.sh` and `own-processes.sh` are the gate itself. Standing
constraint "Nothing a running gate executes or builds from is edited": **write on a private worktree or private copies,
land only while no gate, probe or device A/B runs.** The XCTest verification needs the booted simulator — device-idle
only. Mutations that could reach a real process (§8) run only when no gate and no other session's Gradle build is live.

## 1. Targets

**No corpus cell.** This family cannot flip or move a WPT cell (§6), so it has no `wave52-ship` target. Its targets are:

| # | target | today | wanted |
|---|---|---|---|
| T1 | ios-harness XCTest `StyleConverterTestTests` (iPhone 17 Pro sim, iOS 26.0.1, built with macOS 27.0.1) | 17 pass / 13 fail of 30 | 30 / 30 |
| T2 | `gate-driver.sh:138-139` `./gradlew --stop` ×2 | stops every Gradle 9.6.1 daemon of the user (both wrappers pin 9.6.1, so `:139` is redundant) | stops only daemons that served this checkout |
| T3 | `section-runner.sh:641` (`_android_candidates`), `provision-devices.sh:127` (`_adb_devices`) | on an `adb devices` timeout: `kill-server; pkill -9 -x adb` — the pkill runs even when kill-server worked, and kills every process named adb (every session's clients) | kill only the wedged server's listener |
| T4 | `tools/visual/smoke.sh` Tier 5 / Tier 11 | vite and both probes hardwired to :3000 | a guarded, exported port |
| T5 (found here, optional) | `gate-driver.sh:162` `start_adb_detached` | `adb kill-server` UNCONDITIONALLY at every provision and reprovision — severs every session's adb clients at each gate start | restart only an unresponsive server |

Cells the unblocked XCTests guard (all `wave52-ship`, prev = `wave51-fix`): `CSS2/abspos/static-inside-inline-block ios
P 0.9974` (P 0.9974), `css-cascade/scope-pseudo-element ios f 0.9668` (f 0.9643), `css-flexbox/align-items-007 ios P
0.9974` (f 0.9974), `css-gaps/flex/flex-gap-decorations-027 ios P 0.9997` (f 0.903), `css-masking/clip-path/
clip-path-circle-007 ios P 0.9993` (P 0.9993). They are must-not-move cells, not targets.

## 2. The picture — the result bundle, not a PNG

There is no wrong capture in this family; the "picture" is the 2026-10-06 xcresult
(`~/Library/Developer/Xcode/DerivedData/StyleConverterTest-eihztfbwsdqyigdsngghqrkntnew/Logs/Test/Test-StyleConverterTestTests-2026.10.06_22-22-58-+0200.xcresult`):
30 tests, 17 passed, 13 failed, and **the 13 are two different defects — BACKLOG's diagnosis is right for 9 only**:

| group | failing | failure text (verbatim, shortened) |
|---|---|---|
| `ComposedCanvasIcbClipTests` (all 7) | 7 | `NSCocoaErrorDomain Code=257 "The file “CaptureCanvas.swift” couldn’t be opened because you don’t have permission to view it." … NSPOSIXErrorDomain Code=1 "Operation not permitted"` on `…/trusting-bohr-bd6fbf/apps/ios-harness/StyleConverterTest/Screenshot/CaptureCanvas.swift` |
| `ComposedRootInlineFlowTests` (2 of 3) | 2 | same 257 on `tools/titan/fixtures/per-test-ir/wave49-final/CSS2/wpt__CSS2__abspos__static-inside-inline-block.json` and `…/css-cascade/wpt__css-cascade__scope-pseudo-element.json` |
| `ComposedCanvasPaddingTests` (4 of 8) | 4 | **no file read at all** — `XCTAssertEqual failed: ("16.0") is not equal to ("0.0")` (testZeroedBodyPadLandsOnEverySide, testNegativePadClampsToZero), `("56.0") … ("40.0")` (testUndeclaredSidesKeepTheDefault), `("20.0") … ("4.0")` (testPhysicalSidesMapToLeadingTrailingCorrectly) |

The file has no `#filePath`, no `contentsOf` (grep). Its last change is the wave-24 commit 32fdece7; the resolver it
tests changed in wave 25 round 3 and the web and Compose twins were updated then (`ComposedCanvasPadding.test.tsx`
"left 56", `ComposedCanvasPaddingTest.kt` `negativePadClampsToZero_leavingTheBareFrame` → 16.dp). The iOS twin stayed stale
because until 2026-10-06 the bundle could not run at all. The 17 passes: InboxModeTests 7, OutOfFlowAnchorTests 5,
Padding 4, InlineFlow `testBodyRootLineHeightRefusesEveryRootAtom` 1 (builds its JSON inline).

## 3. The wire

The two documents the inline-flow pins decode are the vendored verbatim per-test IR (`tools/titan/fixtures/README.md`
provenance rules; 5157 B and 1701 B). For example, the first root of scope-pseudo-element:
`{"id": "wpt__css-cascade__scope-pseudo-element__0-119", "meta": {"role": "ws-after"}}` with `Display "INLINE_BLOCK"`,
`Width {"type": "length", "px": 100}`, `VerticalAlign {"type": "keyword", "value": "TOP"}`. The padding pins build their
own wire: `{"type": "PaddingLeft", "data": {"px": 40.0}}` on a `"meta": {"role": "body-root"}` component. The script
items have no wire.

## 4. Mechanism (traced)

**T1-a, 257.** The test bundle is hosted in the app inside the simulator (`project.yml:82-100`, `dependencies: target
StyleConverterTest`). `ComposedCanvasIcbClipTests.canvasSource()` (`:59-64`) and `ComposedRootInlineFlowTests.repoRoot` /
`composedRoots` (`:52-72`) build `URL(fileURLWithPath: #filePath)` → the host path under `~/Documents/Projects/…`, and the
simulator process is refused (POSIX EPERM under the Cocoa 257). The runtime's Catalyst suite reads the same tree the
same way and passed 2147/2147 on the same host the same evening — it runs as an Xcode-hosted macOS process. That is
consistent with macOS privacy protection on the Documents folder being applied to the simulator process; that it is TCC
specifically is an inference from EPERM-on-~/Documents, not a read of the TCC database.
**T1-b, stale contract.** `CaptureCanvas.swift` `ComposedCaptureCanvas.resolvedPadding` → `side(_:)`:
`return Self.padding + max(0, CGFloat(px))` (`Self.padding = WPTCanvas.canvasFramePx`, `:330`) — "frame + declared", the
wave-25 round 3 rule in its doc comment. The four failing pins assert the wave-24 rule "declared replaces frame".
Arithmetic: 16+0 = 16, 16+40 = 56, 16+4 = 20 / 16+9 = 25, 16+max(0,−8) = 16 — exactly the four observed values.
**T2.** `gate-driver.sh` `stop_our_processes` (`:124-140`) runs `./gradlew --stop` from both roots. That stops every
daemon of that Gradle version for the user, whatever checkout it served. The direction BACKLOG suggests, "stop only daemons
whose working directory is this checkout", **cannot work**. Measured: the live daemon pid 15020 (`GradleDaemon 9.6.1`)
has cwd `/Users/dranak/.gradle/daemon/9.6.1`. Every daemon runs there, so `_op_cwd_in_checkout` never matches.
A usable owner record exists: `~/.gradle/daemon/<ver>/daemon-<pid>.out.log` logs one
`Received command: Build{id=…, currentDir=<dir>` line per build served (daemon-15020: 8 lines, all
`…/trusting-bohr-bd6fbf/apps/android-harness`, i.e. this gate's own provisioning build at 01:07).
**T3.** Both sites: `perl -e 'alarm 10; exec @ARGV' "$adb" kill-server …; pkill -9 -x adb …; sleep 1` — `;`-chained, so the
pkill is unconditional inside the timeout branch and matches by process name. The adb server is identifiable: the
tcp:5037 listener is pid 15373, argv `adb -L tcp:5037 fork-server server --reply-fd 4`. The branch fired 0 times in
wave51-fix / wave52-open / wave52-calib / wave52-ship / wave53-open so far (grep of "adb devices timed out").
`feed-android.mjs:142` (kill-server on timeout, no pkill) already follows the rule.
**T4.** `smoke.sh:59-88` `start_vite` runs `npm run dev` (`vite.config.ts:162-165` `server.port: 3000`, no
`strictPort`, so vite silently moves to 3001 when :3000 is held), then polls `http://localhost:3000/` and accepts
any 2xx. So a foreign server that returns 200 passes the readiness poll by itself. Tier 5 / 11 then call
`interaction-states.mjs:29` / `a11y-audit.mjs:39` with `WEB_PORT` unset → `'3000'` → the foreign server. smoke never
sources `web-port-guard.sh`.
**T5.** `start_adb_detached` (`gate-driver.sh:161-165`) is called by `provision()` at every gate start and every watchdog
reprovision. Its reason, "a server spawned from a pipeline inherits its fds", is about a server THIS driver would
auto-start, and a healthy server someone else started holds none of the driver's fds. The kill is not needed for that
reason. Whether the gate's run-to-run determinism record depends on a fresh server is untested.

## 5. The fix (smallest), owned files, seams

No CSS: the citations are the standing constraint above and, for T1-b, CSS 2.1 §8.4 (negative padding) plus the
frame contract in `resolvedPadding`'s doc comment. **Seams: none** (no ComponentRenderer ×3, no extract-fixture.mjs).
- **T1-a** — `apps/ios-harness/project.yml`, test target `sources:` gains three entries with `buildPhase: resources`:
  `StyleConverterTest/Screenshot/CaptureCanvas.swift`, `../../tools/titan/fixtures/per-test-ir/wave49-final/CSS2/wpt__CSS2__abspos__static-inside-inline-block.json`,
  `../../tools/titan/fixtures/per-test-ir/wave49-final/css-cascade/wpt__css-cascade__scope-pseudo-element.json` (XcodeGen
  `buildPhase` on a target source — CHANGELOG #206; installed 2.45.4). Do not copy the app target's `resources:` key
  (`:48-50`): the app's fonts reach its Resources phase through its folder source anyway, and whether 2.45.4 honours
  that key is unverified. The two tests read `Bundle(for: Self.self).url(forResource:withExtension:)` through
  `XCTUnwrap` ("not a resource of StyleConverterTestTests — project.yml"). No `#filePath` fallback, no skip guard (A8#3).
  Update both file headers (`ComposedRootInlineFlowTests.swift:21-26` says "no bundle-resource wiring is needed") and the
  consumers table of `tools/titan/fixtures/README.md` ("no build-system resource wiring").
- **T1-b** — `ComposedCanvasPaddingTests.swift`: the four tests to the wave-25 contract, named after the Compose twin
  (`…KeepsTheImageFrameOnEverySide` 0 → 16 ×4; `…UndeclaredSidesKeepTheBareFrame` left 56, others 16;
  physical sides 20 / 25; `…NegativePadClampsToZeroLeavingTheBareFrame` → 16); header prose wave-24 → wave-25 round 3.
- **T2** — `tools/titan/own-processes.sh` gains `kill_own_gradle_daemons`: for each `pgrep -f 'GradleDaemon'` pid, version
  from its argv (`GradleDaemon <ver>`), log `${GRADLE_USER_HOME:-$HOME/.gradle}/daemon/<ver>/daemon-<pid>.out.log`;
  **ours iff the log has ≥ 1 `currentDir=` and every one equals `$PROJECT_ROOT` or starts with `$PROJECT_ROOT/`**
  (boundary-checked; a daemon shared with another checkout is not ours). SIGTERM ours, name the rest on stderr, `return 0`.
  `gate-driver.sh:138-139` → one `kill_own_gradle_daemons` call (keep the `:137` JAVA_HOME export — provisioning builds use it); drop the "STILL HOST-WIDE" comment (`:132-134`).
- **T3** — `own-processes.sh` gains `kill_wedged_adb_server`: for each `lsof -nP -tiTCP:${ANDROID_ADB_SERVER_PORT:-5037}
  -sTCP:LISTEN` pid whose argv contains `fork-server server`, `kill -9`; any other listener is named and left; `return 0`.
  In `section-runner.sh:641` and `provision-devices.sh:127`, replace `pkill -9 -x adb 2>/dev/null` with
  `kill_wedged_adb_server`. Both scripts `source` own-processes.sh: section-runner beside its web-port-guard source at
  `:78-79`, provision-devices after `:44`; the helper needs no `PROJECT_ROOT`.
- **T4** — `tools/visual/smoke.sh`: `source "$PROJECT_ROOT/tools/visual/web-port-guard.sh"`. Add a function
  `smoke_pick_web_port` that does what test-all.sh `:296-297` + `:1168-1176` do: an explicit `WEB_PORT` held by a foreign
  process → error, non-zero; the default 3000 held → `wpg_free_port 3400 3499` (a new range, disjoint from
  section-runner's 3100–3299 and test-all's 3300–3399; note it in web-port-guard.sh's header). Then
  `( cd apps/web-harness && exec npx vite --port "$WEB_PORT" --strictPort … )`, poll `localhost:$WEB_PORT`,
  `export WEB_PORT` before `run_tier5`. Fix the `:3000` log line and the `:55` comment.
- **T5 (optional, default OUT)** — `start_adb_detached`: a bounded `adb devices` probe first, and kill-server +
  `kill_wedged_adb_server` only if the probe times out. Take it only if the plan accepts a provisioning-path change
  with its own probe; otherwise the orchestrator records it in Known-broken as the third stop.

Owned: `apps/ios-harness/project.yml`, the three `apps/ios-harness/StyleConverterTestTests/` files above,
`tools/titan/fixtures/README.md`, `tools/titan/own-processes.sh`, `tools/titan/own-processes.test.mjs`,
`tools/titan/gate-driver.sh`, `tools/visual/smoke.sh`, `tools/visual/smoke-port.test.mjs` (new). Two-hunk edits
(source line + one call): `tools/titan/section-runner.sh`, `tools/titan/provision-devices.sh`. If another wave-53 lane
owns either one, deliver `harness-hygiene-<file>.patch` instead. Read-only: `CaptureCanvas.swift` (mutated
only in §8, restored, sha-verified).

## 6. Blast radius

**Carrier set for control-check: `[]` (empty)** — every capture must come out byte-identical. Why each path reaches no
capture: (a) the gate and fixture net build `-target StyleConverterTest` only (`provision-devices.sh:102-107`,
`test-all.sh:566`), so test-target resources never enter the app; (b) `stop_our_processes` runs before provisioning, and
Gradle outputs do not depend on which daemon builds them (section-runner converts with `--no-daemon`, `:431`); (c) the adb
helper runs only in the timeout branch (0 firings in 5 runs); (d) smoke.sh is not in the gate path. Shared-path
population: every gate start (T2), every section's device discovery (T3 normal path unchanged), every smoke run (T4). Of
the scored `wave52-ship` cells (web 1229/1372, iOS 1119/1362, Android 1111/1362 passing) none may move because of this lane.
Runtime note (no work proposed): of the 30 `runtimes/swiftui/Tests` files using `#filePath`, **17 read the repo**
(4 runtime-source scans, 4 `Inter-Regular.otf` from the harness, 4 schema goldens, 3 per-test IR including the gitignored
`tools/titan/runs/wave48-final` in IRDecodeSeamTests, 1 property fixture, 1 walk-up in BackdropContractParityTests). The
other 13 use it only as `file: StaticString = #filePath`. A simulator run of that package would need each read file as
a SwiftPM resource. SwiftPM resources must live under `Tests/StyleConverterRuntimeTests/`, which means vendored copies.
Some reads walk up with `fileExists` and `EasingReferenceTests` XCTSkips on a miss, so a denied read could show as a skip
rather than a failure. Catalyst stays the only destination.

## 7. Predictions

- XCTest 17/30 → **30/30**. Padding 4: **HIGH** (the observed values are the arithmetic of the shipped resolver).
  IcbClip 7: **HIGH-MED**. These pins have never executed as Swift, but the L2 replay of the same slicing
  (`tools/titan/results/wave52-composed-canvas/ios-source-pins.mjs`, run read-only today) prints `pins=7/7 GREEN` on
  CaptureCanvas.swift sha256 `3da6fc574fbc98e8`, and IOS-M1…M7 each turn RED. InlineFlow 2: **MED**. They have never
  executed in Swift; their Kotlin twin `ComposedRootInlineFlowTest.kt` reads the same two documents and runs in the
  android-harness suite.
- Corpus: **0 gained / 0 lost / 0 movers** attributable to this lane, byte-identical probe captures (**HIGH**).
- Gate driver: a foreign Gradle daemon survives `stop_our_processes` and is named in `driver.log` (HIGH by the hermetic
  pin; observed on device only if one is up).
- smoke `--quick` with a foreign 200-returning listener on :3000: Tier 5 and Tier 11 pass on 34xx and the foreign
  process survives (**MED-HIGH** — the vite CLI `--port` overrides `server.port`).
- Must not move: the five §1 iOS cells, and every other cell.

## 8. Verification plan

1. **XCTest (device-idle, booted sim).** `cd apps/ios-harness && xcodegen generate`, then check the generated pbxproj:
   `CaptureCanvas.swift in Sources` ×1 (app), `CaptureCanvas.swift in Resources` ×1, each `wpt__….json in Resources` ×1,
   and the app's Resources phase unchanged (Assets.xcassets, 4 Inter fonts, tmpOutput.json). Then `xcodebuild test
   -project StyleConverterTest.xcodeproj -scheme StyleConverterTestTests -destination 'platform=iOS Simulator,id=<booted>'`
   → 30/30; record the xcresulttool summary in the lane note. Mutations, one at a time, cp → sha256 → edit → run →
   cp-back → sha256 equal:
   M1 drop the scope-pseudo-element entry from project.yml → `testScopePseudoElementKeepsItsPassingRootRun` red with the
   "not a resource" message (proves the bundle is read, not the disk);
   M2 in CaptureCanvas.swift move `.clipShape(WPTCanvas.IcbClipBand…)` after `.background(canvasBackground)` →
   `testClipPrecedesTheCanvasBackground` red (the first EXECUTED Swift run of IOS-M1);
   M3 `Self.padding + max(0, CGFloat(px))` → `max(0, CGFloat(px))` → the four updated padding tests red.
2. **own-processes.test.mjs** (hermetic, same style as the file: throwaway `GRADLE_USER_HOME`, throwaway
   `ANDROID_ADB_SERVER_PORT`, `idle()` processes). Fake daemons are `idle(dir, 'GradleDaemon 9.6.1')`; their logs carry
   one VERBATIM 9.6.1 `Received command: Build{id=…, currentDir=…` line copied from a real daemon log.
   Gradle pins: log all-this-checkout → stopped; another checkout → survives and is named; mixed → survives; sibling
   prefix `<checkout>-x` → survives; no log → survives. A real host daemon has no log under the scratch home, so
   it is foreign by construction.
   adb pins: a fake listener with argv `… fork-server server` → killed; a foreign listener on that port → survives and
   is named; a non-listening `fork-server server` decoy → survives.
   Source pins: no live `gradlew --stop` in gate-driver.sh; one `kill_own_gradle_daemons` call; no live
   `pkill … adb` in section-runner.sh / provision-devices.sh / gate-driver.sh; both scripts source the library.
   Mutations: predicate → unconditional kill (red: the foreign / mixed / sibling pins; RUN ONLY with no live gate or
   foreign Gradle build — it would SIGTERM the host's real daemon); prefix test without the `/` boundary (red: sibling);
   restore `pkill -9 -x adb` (red: the source pin — never execute it, it kills the host's real adb).
3. **smoke-port.test.mjs**: `smoke_pick_web_port` against a throwaway foreign listener (default → a port in 3400–3499,
   foreign alive, named; explicit → non-zero). Source pins: no live `localhost:3000`; `--strictPort`; `export WEB_PORT`
   before `run_tier5`. Mutation: return the default unconditionally → red. Executed check (device-idle):
   `python3 -m http.server 3000` in a scratch dir → `bash tools/visual/smoke.sh --quick`. Before: Tier 5/11 FAILED.
   After: the guard names the python pid, Tier 5 prints its `captured · … failed · … skipped` line, Tier 11 its
   `fixtures scored on color-contrast` line, and the python server is still alive.
4. **Suites**: `node --test tools/titan/own-processes.test.mjs tools/visual/web-port-guard.test.mjs
   tools/visual/interaction-states.test.mjs tools/visual/smoke-port.test.mjs`, then the full tooling suite. Its count
   (2261) grows by the new pins → README / CLAUDE.md / STATUS rows through `doc-staleness-check.sh`. The ios-harness
   count stays 30.
5. **Gate probe** (after landing, quiet host): `tools/titan/gate-driver.sh wave53-hh-probe --sections
   css-cascade,css-flexbox --skip-fixture-net` → `driver.log` has no `--stop`, provision rc 0, full columns. Then
   control-check vs `wave53-open` with the EMPTY carrier set → 0 changed captures on web / iOS / Android. Both
   sections hold §1 must-not-move cells. The closing gate's fixture net re-runs `xcodegen generate` from the edited
   project.yml: it must stay exit 0 ×8.

## 9. Risks and recommendation

- XcodeGen may handle a `.swift` path with `buildPhase: resources` oddly (a duplicate file reference, or an ignored
  phase). The pbxproj greps in §8.1 catch it. The fallback is a pre-build copy script with declared input and output
  files.
- The 9 newly readable pins have never executed in Swift. A red after the fix is a finding about the harness or the pin.
  Diagnose it; never relax the pin to green.
- T2 depends on Gradle's daemon-log format. If the format changes, nothing is recognised as ours: daemons are left up,
  named, and counted by the quiet-host check. It never kills more. Leaving foreign daemons up can make the memory check
  REFUSE more often. That is by design, and the operator closes them through their owner.
- SIGTERM instead of `--stop` leaves a stale registry entry, which the Gradle client drops on its next connect
  (unverified on 9.6.1; check `./gradlew --status` after the pin run).
- T3 still `kill -9`s a shared server, but only one that ignored a 20 s `devices` and a 10 s `kill-server`. That
  repairs a broken shared resource; it is not a host-wide sweep.
- All edits are to gate-executed files. A mid-gate landing splits a gate across two trees (§0).
- The ring-fenced `filter-effects/backdrop-filter-basic-blur` is untouched.

**Recommendation: GO**: one small lane (S–M) with no seam hunks. It is device-free to write. Verification needs one
idle device window: the XCTest run, the mutation runs and a 2-section probe.
Orchestrator follow-ups (docs are orchestrator-owned):
- Correct the Known-broken ios-harness entry: 9 failures are 257, 4 are stale wave-24 assertions.
- Correct the Gradle fix direction: daemon cwd is `~/.gradle/daemon/<ver>`, so attribute ownership from the daemon log.
- Add T5 as a third host-wide stop, unless the lane takes it.

STATUS: COMPLETE
