# Wave 53 — builder-lane plan (synthesized 2026-10-07 from the seven family briefs)

Inputs: the seven briefs in this directory (`nested-list-extractor.md`, `rtl-marker-bake.md`, `spaceless-soft-hyphen.md`,
`display-table-body.md`, `contents-root-background.md`, `layout-degenerates.md`, `harness-hygiene.md`) with their censuses;
`docs/BACKLOG.md` "## Standing constraints", "## Next-wave obligations" 0(b)/0(c), queue items 0(ac), 0(ad), 0(l″), 2(c⁴), 2(f), 4(g),
7(e¹) and "## Known-broken"; wave 52's `tools/titan/results/wave52-plan/PLAN.md` §0–§4, §10 and `tools/titan/results/wave52-gate/_note.md`
§§1–7. Evidence run of record: `wave52-ship` (dev tip cdb8a845 has exactly its content): web 1229/1372 · iOS 1119/1362 · Android
1111/1362, 4096 scored cells over 1435 per-test IR documents. Every cell below is `wave52-ship <section>/<test> <platform> <P|f> <ssim>`
as `tools/titan/results/wave52-gate/cells.mjs` reads it. Nothing was built, run or captured while planning: the opening gate
`wave53-open` was running on this host.

Written beside this file: `watchlist.txt` (§7), `expectations.json` (the machine-readable §2/§6: carrier sets, predictions,
must-not-move cells, probe sections per lane), and `plan-build.py`, which generates both from the family censuses. Added at
the plan-skeptic review: the run-id geometry probes `lists-bakes.geometry.py`, `soft-hyphen.geometry.py`, `canvas-root.geometry.py`
and `float-avoid.geometry.py` (shared `geometry_common.py`), and the census scripts `spaceless-soft-hyphen.census.py` and
`rtl-marker-bake.padding-census.py` ("## Skeptic corrections"). Added at the second plan-skeptic review:
`bidi-baked-fixtures/` (a frozen copy of the 17 `_wpt.bidiBaked` fixtures, with `PROVENANCE.txt` naming the run that extracted
each), which the padding census now reads by default, and a ref self-check (exit 1) in `display-table-body.geometry.py`
("## Skeptic corrections (plan-skeptic round 2)").

**A planning-time measurement the whole wave depends on.** `wave53-open` is the first gate on macOS 27.0.1 / Xcode 27.0
(`tools/titan/results/wave53-gate/build-hashes.txt`). Its per-test IR is byte-identical to `wave52-ship` in all 16 sections it
had finished at planning time (sha1 over `per-test-ir/*.json`). The composed captures were hashed for five of those sections (CSS2,
css-lists, css-counter-styles, css-display, css-contain). iOS and Android are byte-identical: 0 of 240 differ on each platform. But
**236 of the 240 web composed captures differ in their bytes**. Nine pairs from nine sections were decoded with PIL, and all nine are
**pixel-identical**. The web PNGs were re-encoded; they were not re-rendered.
**Correction (measured while answering the plan skeptic; widened at its second review): not universally.**
`css-counter-styles/counter-suffix` web differs between `wave52-ship` and `wave53-open` in 129 decoded pixels, all text
antialiasing (bbox x42-124 y21-299, max channel delta 27), while its score is unchanged (`cells.mjs` → P 0.9818 → P 0.9818).
**The scored JSON shows that web re-rasterization is widespread** (plan-skeptic round 2, should-fix 4; a JSON-only per-cell diff,
the scorer's own `loadRun(wave52-ship)` against `loadRun(wave53-open)` over the 26 sections scored at the time). 3533 cells were
compared: **20 web scores differ, every one by ±0.0001; 0 iOS or Android scores differ; 0 flips**. The 20 sit in css-cascade 1,
css-color 1, css-counter-styles 5, css-display 1, css-pseudo 1, css-sizing 1, css-tables 4, css-text 3, css-text-decor 2 and
css-ui 1. Add counter-suffix, whose pixels moved at an unchanged score: **at least 21 web captures were re-rasterized cross-host,
with 0 flips**. Four of the 20 are watched cells:
- `bidi-lines-001` web 0.9999 → 0.9998 (L1 must-not-move);
- `display-contents-root-background` web 0.534 → 0.5341 (L3 target);
- `hyphens-manual-inline-011` web 0.9998 → 0.9997 and `hyphens-auto-001` web 0.7605 → 0.7606 (both under L2's
  `css-text/hyphens/ web` family line).

Consequences:
- The opening gate's read-out should still be 0 gained / 0 lost / 0 movers / 0 newly measured / 0 unmeasured-now. The largest
  cross-host score move is 0.0001, and no cell crossed the pass mark.
- Any byte-identity control that compares across the host change reports false leaks on web. That includes wave 52's
  `control-check.mjs` (sha1 of PNG bytes) run against `wave52-ship`.
- Wave 53's controls therefore compare `wave53-open` with runs on the same host, and they classify every byte difference by
  DECODED pixels (§6 R4).
- **The §10 pre-registration addendum is certain, not conditional.** Four watched cells already differ between `wave52-ship` and
  `wave53-open`, so §8 step 1 writes it whatever else the opening gate shows.
- The orchestrator records all of this in `tools/titan/results/wave53-gate/_note.md` once the gate completes, together with the
  capture-hash census over all 30 sections (§8 step 1).
- One question stays open: whether web capture (encoder AND rasterizer) is run-to-run deterministic on THIS host. R4, R7
  (`prev == cur`) and L1's counter-suffix crop (`rows 0-207 identical`) all rest on it.
  - `wave53-hh-probe` → `wave53-open` (empty carrier set, §4 step 2) answers it.
  - It now runs css-cascade, css-counter-styles, css-flexbox and css-text (about +5 min). The two added sections hold L1's crop,
    the bidi-lines and hyphens controls, and 8 of the 20 cross-host web score moves (9 of 20 with css-cascade's).
  - Its web captures are compared with `wave53-open` by DECODED pixels before R4/R7 are trusted on web.
  - If it shows ANY decoded-pixel difference on web, R4's web rule is restated in the §10 addendum BEFORE any further lane lands,
    never after a gate is read.

## 0. Rules every lane works under

Quoted verbatim from `docs/BACKLOG.md` "## Standing constraints" and `CLAUDE.md` "Hard rules for every file", as they apply here.

- **Ring-fence**: "the WPT test `filter-effects/backdrop-filter-basic-blur` belongs to an external session. No test-specific code,
  ever. Generic mechanisms that incidentally move it are reported plainly in the wave PR (precedent: waves 46, 47) — never carved
  out by name." No lane here targets it. The one line naming it in `watchlist.txt` is a report-only watch.
- **No silent fallthroughs**: "If a value variant isn't supported on this platform yet, log it via the PropertyTracker or emit a
  TODO + keep the cross-platform comparison honest."
- **Short**: "target ≤200 lines. If a file grows past ~300, split it (e.g. one applier per value family)."
  - How it applies this wave: most of the files the lanes touch are already past 300 lines. Examples: `bidi-bake.mjs` 1172,
    `counter-bake.mjs` 526, `InlineRunFold.kt` 707, `ColorApplier.kt` 1467, `ScreenshotCaptureScreen.kt` 2500, `CaptureCanvas.swift`
    1122, `ComposedCaptureGallery.tsx` 1176, `ComposedRootStack.swift` 674, `WptCaptureMode.kt` 448.
  - **New logic goes in a NEW file of ≤200 lines. An oversized file gets only the call site, a defaulted parameter or a one-line
    condition.**
  - The new files this forces are listed in each lane's "own:" list below: `bidi-marker-bake.mjs`, `InertOutOfFlowMember.kt`, the
    `RootBackgroundPropagation` triplet, `CanvasTableBody.ts`, the `TableBodyForest` twins and the `FloatAvoid*` files.
- **Every line commented**: "Comments explain the *why*, not just the *what*. For extractors, reference the exact CSS spec section
  or parser file you're mirroring. For appliers, cite the platform API you're calling and why."
- **Pins can fail**: "A new check must be **proven able to fail** (mutation/negative test) before it is trusted." Every pin named
  in §2 has its mutation (this was false until the plan-skeptic review added L2 (d), L3 B-shape and L4 P6/P7's M7/M8, and
  L1's V3b). The lane EXECUTES the mutation, records red → restore → green in its lane note, and restores the file
  (verified by sha256).
- **Verbatim payloads** (wave skill Phase 2): "Tests use VERBATIM corpus payloads (per-test IR from the previous gate's
  `per-test-ir/`, or converter output of a fixture)." The payloads come from `tools/titan/runs/wave52-ship/sections/*/per-test-ir/`.
  These are byte-identical to `wave53-open` wherever the gate has finished.
- **No edits under a running gate**: "Nothing a running gate executes or builds from is edited — no runtime, harness, extractor or
  tool file, no `tools/titan/runs/` content — while a gate (or a device A/B) runs on this host. A fix found mid-gate is prepared as a
  patch file and applied when the device is idle." Lanes work in private worktrees off cdb8a845. They launch only after `wave53-open`
  is scored. They run no build or suite while any gate, probe or A/B runs.
- **Never kill a foreign process**: "Capture and gate scripts must never stop a process this checkout did not start … A foreign
  process is named on stderr and left running … it is never cleared by a host-wide kill." Lane L5 retires the two BACKLOG-named
  violations in the gate path (§2 L5). T5 remains: `start_adb_detached` (`gate-driver.sh:161-165`) still runs `adb kill-server`
  unconditionally at every provision, and is recorded in Known-broken (§5). No lane adds a new one.
- **A pre-registered lost list**: "A lost cell outside the list pre-registered before the closing gate stops the ship until its
  cause is found in the picture AND the wire document — never ledgered, never excused by a re-run." This wave changes no instrument,
  so **the pre-registered lost list is EMPTY** (§6 R1).
- **Seams are never edited by a lane.** The seam files are:
  - `runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt`
  - `runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift`
  - `apps/web-harness/src/sdui/ComponentRenderer.tsx`
  - `tools/titan/extract-fixture.mjs`

  A lane that needs one delivers `tools/titan/results/wave53-<lane>/seam-<n>.patch`. The patch must apply clean on cdb8a845, its
  header must name that tree, and the lane's pins must be green with it applied. §3 lists every hunk.
- **Single-writer suites**: "Suite runs on a shared mid-wave tree are single-writer … Fix/skeptic lanes run focused tests only; the
  orchestrator runs ONE sequential full sweep afterwards — and that sweep INCLUDES the harness suites."
- **Skeptic census**: "A builder lane's blast radius is re-derived by a skeptic with its OWN census before it is believed."
- **No scratchpad pointers**: "Evidence pointers … are repo paths or gate cells — never a session scratchpad." Every artifact goes
  under `tools/titan/results/wave53-<lane>/` as soon as it exists. Each lane note ends in `STATUS: COMPLETE` or
  `STATUS: PARTIAL — <what is left>` (wave skill Phase 2: file-driven, model opus, resumable). Each builder, skeptic and fix-lane
  note also carries a `TREES:` line: the absolute path of every worktree or export tree in which it ran Gradle. §8 step 7a reads
  those lines (plan-skeptic round 2).
- **Labels are honest.**
  - A P→P rise is "picture-correctness".
  - A pass whose picture does not earn it is "DEGENERATE". A flip on such a picture is never claimed as a fix.
  - Before any "passes" sentence, look at the capture next to the frozen ref (obligation 0(b): "a score is not a look").
  - Say "runtime", not "engine".

## 1. Lane table

There are five builder lanes, one fewer than the cap. nested-list-extractor (GO-SMALL) folds into L1. display-table-body was
GO-SMALL only as a fold into an unstaffed T10 lane, so its operative verdict is NO-GO; it is staffed in L3 by decision D1 (§9). File ownership is disjoint: `plan-build.py` asserts that no capture is a carrier of two
lanes, and that no lane's must-not-move cell is another lane's carrier. Effort is per the briefs.

| # | lane (dir `tools/titan/results/wave53-<lane>/`) | briefs | targets (`wave52-ship`) | predicted (confidence) | ownership root | effort |
|---|---|---|---|---|---|---|
| L1 | lists-bakes | rtl-marker-bake (GO), nested-list-extractor (GO-SMALL) | `css-counter-styles/counter-suffix` ios P 0.9802 / android P 0.9547 (both DEGENERATE), web P 0.9818 (wrong-side markers); `css-lists/counter-reset-reversed-nested` ×3 P 0.9506–0.9509 (flat list, wrong numbers) | 6 cells picture-correct (HIGH–MED); `bidi-lines-002` android rises (MED); `bidi-lines-001` android f→P (MED-LOW) | `tools/titan/{bidi,counter}-bake*`, `PseudoTextFold.{kt,swift}`, extractor via seam-1 | M |
| L2 | soft-hyphen | spaceless-soft-hyphen (GO) | `css-text/hyphens/hyphens-span-001` android P 0.9532, `-out-of-flow-001` android P 0.9685, `-002` android P 0.982 (all DEGENERATE); `hyphens-span-001` ios f 0.8652, `-out-of-flow-001` ios f 0.8935 | +1 ios (MED-HIGH) +1 ios (MED); 3 android DEGENERATE→faithful (HIGH / MED-HIGH ×2) | Compose `typography/{wrapping,inline}`, Swift `typography/wrapping/SoftHyphenPolicy`; two seam hunks | S–M |
| L3 | canvas-root | contents-root-background (GO), display-table-body (NO-GO → staffed, §9 D1; item B) | `css-display/display-contents-root-background` ×3 f 0.534–0.5355; `css-backgrounds/background-attachment-margin-root-001` ×3 f 0.451, `-002` ×3 f 0.339; `CSS2/css21-errata/s-11-1-1b-006` ios P 0.9953 / android P 0.9944 (DEGENERATE), web P 0.9941 (wrong) | +3 (HIGH) +4 (MED) +2 (MED-LOW); 006 ×3 picture-correct (web HIGH geometry, natives MED, probe-gated) | the three composed canvases + `WptCaptureMode.{kt,swift}`, `ComposedRootStack.swift`, new `RootBackgroundPropagation` / `TableBodyForest` twins | M |
| L4 | float-avoid | layout-degenerates (GO; multicol-007 RECORD-AS-WALL) | `css-contain/contain-inline-size-bfc-floats-001` ios P 0.9531 / android P 0.9519 (DEGENERATE); `-002` ios f 0.9322 / android f 0.9311; `css-display/display-flow-root-002` ios/android P 0.9734 (DEGENERATE, newly measured) | +2 (HIGH); 4 DEGENERATE→faithful (HIGH ×2, MED ×2) | new `FloatAvoidPlan` / `FloatAvoidLayout` twins; two seam hunks | M |
| L5 | harness-hygiene | harness-hygiene (GO; T5 OUT) | ios-harness XCTest 17/30; `./gradlew --stop` ×2 and `pkill -9 -x adb` ×2 in the gate path; `smoke.sh` hard-wired :3000 | XCTest 30/30; 0 corpus cells (empty carrier set) | `tools/titan/{own-processes,gate-driver,section-runner,provision-devices}.sh`, `tools/visual/smoke.sh`, `apps/ios-harness/{project.yml,StyleConverterTestTests/}` | S–M |

**Expected movement against `wave53-open`** (which should equal `wave52-ship`; see §8 step 1). Flips, counting only HIGH and
MED-HIGH: web +1, iOS +3, Android +2, giving ≈ web 1230/1372 · iOS 1122/1362 · Android 1113/1362. Counting every predicted flip
including MED and MED-LOW: web +3, iOS +6, Android +5. That is a ceiling of web 1232 · iOS 1125 · Android 1116. Lost: 0.
Unmeasured-now: 0. Newly measured: 0.

Besides the flips, 16 passing cells become picture-correct with no change of verdict: L1 6, L2 3, L3 3, L4 4. Of the ten DEGENERATE
gains in obligation 0(b), nine are addressed (L1 2, L2 3, L3 2, L4 2). The tenth, `anchor-position-multicol-007` android, is
recorded as a wall (§5).

Each lane's §2 entry says what it may run. The common floor:
- Focused suites only, and only while the host is idle: no gate, probe or A/B is running.
- Anything that starts Chromium, a simulator or an emulator runs only in a window the orchestrator grants: the L1 CDP probe and
  gate-flag re-extraction, and every L5 device step. The ios-harness XCTest on L3's changes is run at integration (§4).
- No lane runs a full suite.
- L5's script edits (`gate-driver.sh`, `section-runner.sh`, `provision-devices.sh`, `own-processes.sh`, `smoke.sh`) are made only
  when the orchestrator says the device is idle, even in L5's private worktree. They land on the integration tree only while no
  gate, probe or A/B runs. The gate reads these scripts as it runs (harness-hygiene §0).

## 2. Lanes in detail

### L1 · lists-bakes (effort M) — briefs `rtl-marker-bake.md` (hunks M + P), `nested-list-extractor.md` (A–D)

Queue: BACKLOG 2(c⁴) ("RTL markers are BLOCKED UPSTREAM — do not fix them in the runtime … **Fix it in the bake**") and 2(f).
The lane runs as two units in this order. Each unit lands whole or not at all.

**Unit 1 — nested list (A+B+C+D, one commit; the brief: "Land all four together, or label the native cells degenerate").**
- **Targets.**
  - `wave52-ship css-lists/counter-reset-reversed-nested web P 0.9506`, `ios P 0.9508`, `android P 0.9509`.
  - What is wrong: the list is flat (rows 3–5 sit at x57, where the ref has x97); the rows carry 12/11/10/9/8 where the ref reads
    3/2/11/9/8/1; and `1. One` is missing. iOS also leaves row 2 unmarked.
- **Mechanism** (traced and reproduced in-process). Three defects:
  - `extract-fixture.mjs` `walkChildren` (l.1821-1833) and `scanOwnText` (l.3560-3570) take the FIRST `<li\b` after an `<li>` as
    its implied close. That opener sits inside the nested `<ol>`, so the nested items are hoisted, and `</ol>` ends the fragment
    (l.1792), which drops `One`.
  - `counter-bake.mjs` `setCounter`'s stop branch skips css-lists-3 §4.4.2 step 4.
  - The iOS `PseudoTextFold.swift:65` refuses every component that carries `meta.runs`.
- **Change.**
  - (A) seam-1: `findImpliedClose` beside `AUTO_CLOSE_TRIGGERS` (l.1947), called from both trigger sites. The measured draft is
    `nested-list-extractor.patch-extractor.py`. Identity holds by construction when no list container opens before the trigger.
  - (B) `counter-bake.mjs`: `inst.implied = inst.negSum + value + (inst.lastNegBy === who ? 0 : inst.lastNeg)`, with `bumpCounter`
    recording `lastNegBy`. Rewrite the "KNOWN OUTLIER `li-value-reversed-019`" paragraph.
  - (C) Swift `PseudoTextFold`: fold the `::before` run into BOTH `runs[0].text` and `text` when `runs[0]` is text. Keep the logged
    refusal for a child-first run and for `::after`.
  - (D) Compose `PseudoTextFold.kt`: the same fold, plus removing `before` from the folded component's `pseudos`, so no
    `Row { before, content }` wrapper is built.
- **Spec.**
  - HTML §13.2.6.4.7 (an `li` start tag closes only up to a special element).
  - css-lists-3 §4.4.2 steps 3.3 + 4.
  - CSS 2.1 §12.1 / css-pseudo-4 §4.1, and CSS 2.1 §9.2.1.1 (the anonymous block around "Two" ahead of the block `<ol>`).
  - The extractor's remaining special elements (blockquote, table, …) are a named KNOWN GAP in the helper's banner. The census
    found 0 carriers for them.

**Unit 2 — RTL markers (M+P, one commit; the brief: "M must never land without P").**
- **Targets.**
  - `wave52-ship css-counter-styles/counter-suffix ios P 0.9802` / `android P 0.9547` (DEGENERATE: the two `dir=rtl` lists draw no
    marker), and `web P 0.9818` (the RTL markers are painted on the left, at x46-58).
  - Movers, android only: `css-text/bidi/bidi-lines-002 android P 0.9534` and `bidi-lines-001 android f 0.8934`.
- **Mechanism.**
  - `bidi-bake.mjs` `rootProperties` (:509-531) retires the root's `direction`, but the walker collects text nodes only
    (`collectText` :671-726). The `::marker` side, which is the one fact still depending on `direction`, is therefore dropped
    silently.
  - Both natives route the now-absolute `<li>` to the positioned overlay, which has no marker branch.
  - Android also anchors abspos children at the content box (`PositionedParentFlowSlot.kt:74-76`), which puts the text +48 px.
- **Change.**
  - (M) Bake the marker the way the text is baked: string, box and glyph runs come from Chromium's `::marker`, with a probe
    self-check at 0.5 px. The box gets `list-style-type: none`. A run with no strong letter is split per grapheme. ROOT list items
    are untouched.
    - **The self-check is MARKER-scoped, not a whole-test bail** (plan-skeptic nit). Under `planBidiBake`'s `{ bail }` semantics a
      bail returns the WHOLE test to the unbaked static path (`bidi-bake.mjs:847-848`), which would un-bake counter-suffix's text
      too, an outcome nothing here pre-registers. So a > 0.5 px mismatch on one `<li>` places THAT item's marker box by the
      analytic inline-start-edge fallback below; that item still gets `list-style-type: none`; every other item, the text runs and
      hunk P are unchanged; and the `<li>` gains `marker-probe-mismatch` in its `_lossyReasons` (a stamp, never a `{ bail }`).
    - Pre-registered: a gate-time `marker-probe-mismatch` in counter-suffix's extract log does not change §2's predictions (the
      replays paste the REF's marker ink, so they are model-independent). It is named in the lane note and in the PR.
    - If CDP does not surface the marker box, use the analytic inline-start-edge fallback, labelled in the header "a MODEL of
      Chromium, not its answer".
  - (P) The bake root's padding is a spent input. `rootProperties`/`applyBidiBakePlan` emit `padding: 0` and delete every other
    `padding*` key **only on a root whose browser-resolved padding is NON-ZERO on some side**, and not where the root has
    `background-clip`/`-origin: content-box` or non-visible `overflow`. A zero-padding root is left untouched: its `padding*` keys
    stay in place and in order (plan-skeptic must-fix 1).
    - Why the guard is load-bearing: `applyBidiBakePlan` merges with `Object.assign(cmp.properties ??= {}, props)`
      (`bidi-bake.mjs:957`), so deleting four longhands and adding `padding` moves `PaddingTop…Left` from property index 11-14 to
      the end of the IR list. Re-measured over the 17 `_wpt.bidiBaked` fixtures (roots found by the `baked-bidi-visual-order`
      `_lossyReasons` stamp; committed as `rtl-marker-bake.padding-census.py`, which reads the frozen copy `bidi-baked-fixtures/`): 14 roots in 7 documents carry a `padding*` key, but only **6 roots in 4 documents are non-zero**
      (counter-suffix ×2 `0 3em`, anchor-center-safe-rtl ×2 `10px`, bidi-lines-001/-002 ×1 `0 0.5ch`). The other 8 roots
      (`selectors/dir-style-02a` ×6, `dir-selector-change-003`/`-004` ×1) carry explicit `padding-top/right/bottom/left: 0px` with
      `overflow-x/-y: visible`, so the content-box/overflow guard does not trip, and without the non-zero guard their per-test IR
      would change bytes. They are L1 must-not-move ×9 and are not wire carriers.
    - **Pre-registered fallback: P-narrow** (plan-skeptic round 2, should-fix 5). The rtl brief's §9 risk 3 reads "Hunk P's
      web/iOS invariance is argued, not captured … The fallback is P-narrow (only roots that host a marker run)". The lane
      prepares the fallback now, so nobody designs it under a running probe. `tools/titan/results/wave53-lists-bakes/u2-narrow.patch`
      adds one condition to hunk P: the root hosts a hunk-M marker run. It is pinned by V3c and applied only on the trigger.
      - Trigger: any capture read of Unit 2 (`wave53-probe`, or an earlier one) shows a decoded-pixel change against
        `wave53-open` on web or iOS of `bidi-lines-001`, `bidi-lines-002` or `anchor-center-safe-rtl`. A P → f or a fall of
        ≥ 0.002 on `bidi-lines-001`/`-002` android triggers it too (§6 rules 1, 2), because only hunk P reaches those
        marker-less documents.
      - Action: Unit 2 is re-landed as U2 + `u2-narrow.patch`. This is NOT a lane revert. Unit 1 stays, and so does the
        counter-suffix fix: counter-suffix's two roots host marker runs, so P still fires there and M never ships without P.
      - After it, the wire carriers are `counter-reset-reversed-nested` and `counter-suffix` only.
      - The android capture carriers lose `bidi-lines-001`, `bidi-lines-002` and `anchor-center-safe-rtl`. Those three become
        must-not-move on every platform, wire and capture.
      - The two bidi-lines android mover predictions (MED, MED-LOW; no floor) are withdrawn.
      - css-text, css-anchor-position, css-counter-styles and css-lists are re-probed, and the gate-flag wire differential against
        cdb8a845 must then change exactly 2 documents (`counter-reset-reversed-nested`, `counter-suffix`).
      - If U2-narrow still changes a web or iOS capture outside counter-suffix, Unit 2 is reverted (§6 rule 5). Unit 1 stays.
      - Machine-readable form: `expectations.json` `lanes.L1-lists-bakes.pNarrowFallback`.
  - Also edit `StyleEngine/lists/ListMarkerOutsideHang.swift:33-41`, comment only, which currently says "inline-END side" and "no
    `meta.markerText` reaches them". The brief handed this to the orchestrator. It moves here because this commit is what makes
    those words false (§9 D4).
- **Spec.**
  - css-lists-3 (`::marker` box; `list-style-position: outside` places it on the inline-start side; Appendix A
    `unicode-bidi: isolate; font-variant-numeric: tabular-nums; white-space: pre`).
  - css-counter-styles-3 (`suffix`; `decimal`, `hebrew`).
  - css-writing-modes-4 `direction`; UAX #9.
  - CSS 2.1 §10.1 item 4 (P: abspos descendants are positioned from the padding box, so zeroing the padding of a border-box root
    with an explicit used size moves nothing on web or iOS). The bake root's in-flow children are covered too: the only ones are
    the 5 `<br>` of bidi-lines-002, which the bake hides (`plan.hides` → `display: none`, `Width`/`Height` 0 on the wire), so they
    generate no box and the padding cannot move them.

**Lane facts.**
- **own:**
  - `tools/titan/counter-bake.mjs`, `tools/titan/counter-bake.test.mjs`
  - `tools/titan/bidi-bake.mjs` (gains only the marker-run call and hunk P's padding keys), `tools/titan/bidi-bake.test.mjs`
  - `tools/titan/bidi-marker-bake.mjs` (new; hunk M's marker facts → runs: placement, grapheme split, probe self-check)
  - `tools/titan/extract-fixture-implied-close.test.mjs` (new; the extractor pins)
  - `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/content/PseudoTextFold.swift`,
    `runtimes/swiftui/Tests/StyleConverterRuntimeTests/PseudoTextFoldTests.swift`
  - `runtimes/compose/src/main/java/com/styleconverter/runtime/content/PseudoTextFold.kt`,
    `runtimes/compose/src/test/java/com/styleconverter/runtime/content/PseudoTextFoldTest.kt`
  - `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/lists/ListMarkerOutsideHang.swift` (comment-only hunk)
  - `tools/titan/results/wave53-lists-bakes/` (the marker probe script, the differential, the replays, `_note.md`, and the prepared P-narrow fallback `u2-narrow.patch`)
- **Seams:** `tools/titan/extract-fixture.mjs` → `seam-1.patch`. L1 is the only lane on this seam. `bidiBakeFixture`'s signature
  and call site are unchanged, so Unit 2 needs no seam.
- **Read-only:**
  - `counter-style-bake.mjs`, `counter-style-table.mjs` (`BULLET_STYLES` already holds `none`), `generated-content-bake.mjs`
  - `lists/ListOrdinal.{kt,swift}`, `ContentApplier.kt`, `PseudoBoxFold.kt`, `PositionedParentFlowSlot.kt`
- **May run:**
  - `nice -n 19 node --test tools/titan/extract-fixture-implied-close.test.mjs tools/titan/extract-fixture.test.mjs tools/titan/counter-bake.test.mjs tools/titan/generated-content-bake.test.mjs tools/titan/bidi-bake.test.mjs tools/titan/counter-style-bake.test.mjs`
  - `(cd apps/android-harness && ./gradlew :runtime:testDebugUnitTest --tests '*PseudoTextFold*' --tests '*PseudoBucketExtractor*' --tests '*ListOrdinal*')`.
    `PseudoBucketExtractorTest.kt` holds the `extractBeforeAfterConfig` pins. The old filter `*ContentApplier*` matched no Compose
    test class, so it ran nothing (plan-skeptic round 2).
  - Catalyst `-only-testing:StyleConverterRuntimeTests/PseudoTextFoldTests`
  - The in-process extractor differential over the 1435 sources (pure node).
  - **Only in an orchestrator-granted host window:**
    - the CDP `::marker` probe on counter-suffix (Chromium);
    - the gate-flag wire differential: re-extract all 30 sections pre and post with the gate's own flags (`--post-load`,
      `--bidi-bake`, `--vt-bake`);
    - `./gradlew :converter:run` on counter-suffix.
- **Carrier set** (control-check; `expectations.json` `lanes.L1-lists-bakes`):
  - captures: web and ios `{counter-reset-reversed-nested, counter-suffix}`; android those two plus `bidi-lines-001`,
    `bidi-lines-002` and `anchor-center-safe-rtl`.
  - wire: the per-test IR of exactly those five documents may change. L1 is the only lane that changes extraction, so **every
    other per-test IR document in the corpus must be byte-identical to `wave53-open`**.
  - Rule, to recognise a carrier:
    - Unit 1: a post-fix component with `meta.sourceTag` ∈ {ol, ul, menu, dir, li} whose slot ancestors include an `li`, or one
      carrying both `meta.runs` and `pseudos.before._text`.
    - Unit 2 (pre-fix wire): a component with `meta.markerText` and `Position ABSOLUTE|FIXED`, or a bidi-bake root with a
      **NON-ZERO** resolved padding (6 roots in 4 documents). A bidi-bake root whose `padding*` keys are all zero is NOT a carrier:
      `dir-style-02a`, `dir-selector-change-003` and `-004` must come out byte-identical in the wire differential
      (`expectations.json` `lanes.L1-lists-bakes.carrierRule`).
- **Unit pins (each with the mutation that must turn it red).**
  - Unit 1:
    - N1 `extractFixture('css/css-lists/counter-reset-reversed-nested.html')` gives root `<ol>` → Three, Two, One; Two holds one
      `<ol>` with Eleven/Nine/Eight; the `_text` values read `3. 2. 11. 9. 8. 1.`. Mutation: make `findImpliedClose` return the
      first match.
    - N2 the run proto of `<li>A<ol><li>B</li></ol></li>tail` has exactly one element entry. Mutation: revert site 2 only.
    - N3 the bake gives `3. 2. 11. 9. 8. 1.`, and the `li-value-reversed-008b` list-1 shape gives 10 8 6 5 3.
      - Mutation A, drop the `lastNeg` term: N3 goes red.
      - Mutation B, always add it: the existing `counter-list-item` and `li-value-reversed-013` pins go red.
    - N4 (Catalyst) the post-fix `Two` folds to `runs[0].text == "2. Two "` and `text` starts with `"2. "`; the control
      `display-contents-dynamic-before-after-001__1__3` comes back unchanged. Mutation: restore the runs refusal.
    - N5 (JVM) the same payload, plus `extractBeforeAfterConfig(...)?.before == null`. Mutation: keep `before`.
  - Unit 2 (built from the VERBATIM wave52-ship geometry):
    - V1 the li gains `list-style-type: none`; the marker runs sit at li-x ≥ 64; the runs are `['.','1']` and `['א.']`
      (`direction: rtl`). Mutations: drop `none`, place at `li.left − w`, disable the grapheme split.
    - V2 the arabic-indic-101 shape (a root li) gets no marker run. Mutation: emit for roots.
    - V3 verbatim `counter-suffix__0__4` gives `padding: '0'` and no other `padding*` key, and a `content-box` root keeps its
      padding. Mutations: remove the line; remove the content-box guard. Its fixture also carries the verbatim bidi-lines-002 root
      with its 5 hidden `<br>` children, asserted `display: none`.
    - V3b (plan-skeptic must-fix 1) the verbatim `selectors/dir-style-02a` bake root (`padding-top/right/bottom/left: 0px`,
      `overflow-x/-y: visible`) comes out of `rootProperties` + `applyBidiBakePlan` with its `padding*` keys deep-equal AND
      `Object.keys` order unchanged. Mutation: drop the non-zero guard (→ the four longhands deleted, `padding` appended → red).
    - V3c (P-narrow, plan-skeptic round 2). It lives with `u2-narrow.patch` and is green in the lane with the patch applied;
      neither lands unless triggered. The verbatim `bidi-lines-002` root (non-zero `padding: 0 0.5ch`, no marker run) keeps its
      `padding*` keys deep-equal and in order, while the verbatim `counter-suffix__0__4` root still gives `padding: '0'`.
      Mutation: drop the marker-host condition (→ bidi-lines-002's padding rewritten → red).
    - V4 bidi bake then `bakeCounterStyles` gives `stamped 6, declined 2` (10/2 today). Mutation: omit `none`.
    - V5 a > 0.5 px probe mismatch on one `<li>` gives that item the analytic edge box and a `marker-probe-mismatch` stamp, while
      `planBidiBake` still returns a plan (not `{ bail }`) and the text runs and the other item's marker are deep-equal to the
      no-mismatch plan. Mutations: delete the self-check (the mismatching box is used → red); turn it into a whole-test
      `{ bail }` (plan null → red).
- **Predictions.**

  **gate floor** = `expectations.json` `floor` (plan-skeptic must-fix 2): on `wave53-probe` a cell below it, or a *geometry
  gating* cell whose probe does not print its exact verdict, reverts the commit that carries it (§6 revert rules 3/4). "report"
  means no floor and no geometry trigger: the magnitude is read and labelled. Rules 1 (P on `wave53-open` → f), 2 (Δ ≤ −0.002)
  and 5 (leak) still apply to every row, whatever its tier. 0.95 is the scorer's pass mark ("stays P").

  | cell | prediction | confidence | gate floor · geometry |
  |---|---|---|---|
  | `counter-reset-reversed-nested` web | P ≥ 0.995 | HIGH | 0.995 · gating |
  | `counter-reset-reversed-nested` ios | P ≥ 0.99 | MED-HIGH | 0.99 · gating |
  | `counter-reset-reversed-nested` android | P ≥ 0.99 | MED | report · report |
  | `counter-suffix` web | P ≥ 0.995 (replay 1.0000) | HIGH (stays P) | 0.95 · gating (wrong-side marker gone) |
  | `counter-suffix` ios | P ≈ 0.986 (replay 0.9888; capped below 0.99 by its out-of-family LTR residuals) | HIGH stays P / MED magnitude | 0.95 · report |
  | `counter-suffix` android | P ≈ 0.985 (replay 0.9900) | MED-HIGH | 0.975 · gating |
  | `bidi-lines-002` android | P 0.975–0.98 | MED | report |
  | `bidi-lines-001` android | ≈ 0.95–0.96, **f→P possible** | MED-LOW | report |

  **Pre-registered and never to be shipped: M without P loses `counter-suffix` android (replay f 0.9466).**
- **Must not move (92 cells, `watchlist.txt` L1 block).**
  - Every other `css-lists/counter-reset-reversed-*` ×3.
  - `counter-list-item`, `-2`, `-3`, `-slot-order` ×3.
  - `css-display/display-contents-dynamic-before-after-001` ×3.
  - Web and ios of `bidi-lines-001` / `-002`.
  - All three platforms of the 13 other bidi-baked documents: the three `arabic-indic/css3-counter-styles-10x`, `bidi-tab-001`,
    `boundary-shaping-009`, `hyphenate-character-005`, `bidi-plaintext-br-001`, `dir-selector-change-003`/`-004`,
    `dir-style-02a`/`-03a`, and `attachment-local-positioning-3`/`-4` (unscored; their captures are covered by the control).
  - Rows y0-208 of counter-suffix on every platform: a crop compare of pre against post.
- **Probe sections:** css-lists, css-counter-styles, css-text, css-writing-modes, selectors, css-anchor-position,
  css-backgrounds, css-pseudo, css-display — the union of the two briefs' §8 lists. `selectors` holds the three zero-padding bake
  roots (V3b); `css-anchor-position` holds the unscored wire carrier `anchor-center-safe-rtl`, whose web/iOS capture invariance
  under hunk P is otherwise argued, not captured (rtl brief risk 3). A change there triggers the pre-registered P-narrow
  re-land (§2 L1 (P)), not a lane revert.
- **Geometry verdicts:** `python3 tools/titan/results/wave53-plan/lists-bakes.geometry.py <run> wave53-open` must print
  `→ GEOMETRY OK` on all six target rows and `rows 0-207 identical to wave53-open` on the three counter-suffix rows
  (`expectations.json` `lanes.L1-lists-bakes.geometryProbe`). On wave52-ship every capture row prints `GEOMETRY WRONG` and every
  ref row `GEOMETRY OK`.
- **Lane note:** `tools/titan/results/wave53-lists-bakes/_note.md`.

### L2 · soft-hyphen (effort S–M) — brief `spaceless-soft-hyphen.md` (F1, F1-iOS, F2)

Queue 4(g). Obligation 0(b) for the three android cells.

- **Targets.**
  - `wave52-ship css-text/hyphens/hyphens-span-001 android P 0.9532`, `hyphens-out-of-flow-001 android P 0.9685`,
    `hyphens-out-of-flow-002 android P 0.982`. All three are DEGENERATE: they read `highwa`/`y`, `h`/`ighway` or `high`/`way` with
    no hyphen.
  - `hyphens-span-001 ios f 0.8652` and `hyphens-out-of-flow-001 ios f 0.8935`. The glyphs are right, but every box is 26 px tall
    where the ref box is 46 px.
- **Mechanism** (traced; the reviewers' "span is a line-break point" guess is replaced).
  - (A) `PreBreakPipeline.preBreak` (`typography/wrapping/PreBreakPipeline.kt:166`) declines every space-less run. The comment
    there (`:161-165`) says the wave-21 gate owns that case. It does not own it here: `DecorationOps.hasSoftWrapOpportunity` counts
    U+00AD, so `softWrap` stays on, and under `Hyphens.None` Minikin breaks at the box edge.
  - (B) `InlineSpanRing.admit` refuses the abspos member (`member-prop:Position`), and `InlineRunFold.kt:470` bails to the stacked
    fallback: one paragraph per text run.
  - (C) iOS `ComponentRenderer.swift:4919-4921` guards the pre-break with `transformedText.contains(" ") || hyphenLocale != nil`.
    TextKit takes the soft hyphen anyway, but `singleLineText` (:5028) pins the box to one line.
- **Change.**
  - F1: narrow `:166` to `if (text.indexOf(' ') < 0 && text.indexOf('\u00AD') < 0) return identity` and rewrite the `:161-165`
    comment.
  - F1-iOS: add `static func admitsPreBreak(_:dictionary:)` to `SoftHyphenPolicy.swift`, called by seam-1.
  - F2: before the glyph-member arm of `InlineRunFold`, admit a **paint-inert out-of-flow member**:
    - its tag is in `TEXT_MEMBER_TAGS`;
    - it has no children, runs or decorations;
    - its properties are a subset of {`Position` ABSOLUTE|FIXED, `Color` with alpha 0, `Hyphens`, border colours}.

    The member is dropped from the merged string and counted in a new `Outcome.Folded.droppedOutOfFlowMembers` (default 0).
  - **Decision against the brief (§9 D2): F2's breadcrumb (`seam-2`) is REQUIRED, not optional.** F2 removes a component from the
    mounted tree, and a removal the log does not name is a silent fallthrough.
  - **Decision (§9 D3):** if the iOS pre-break closure is reachable for a vertical-rl run (the iOS wrap width is not
    vertical-gated), gate the U+00AD clause on horizontal-tb and log the refusal. `hyphens-vertical-001` must then come out
    byte-identical on all three platforms.
- **Spec.**
  - css-text-3 §5.3 (`manual`: U+00AD is an opportunity; the hyphen is painted when the break is taken).
  - §5.5 (`overflow-wrap: normal` forbids the emergency break).
  - §5.1 (out-of-flow elements introduce no soft wrap opportunity).
  - CSS 2.1 §10.8 (line-box height).
  - Rejected, as the brief rejects it: honouring U+00AD through Compose `Hyphens.Auto`. It would turn on dictionary breaks, which
    `manual` forbids.
- **own:**
  - `runtimes/compose/src/main/java/com/styleconverter/runtime/typography/wrapping/PreBreakPipeline.kt` + `src/test/…/typography/wrapping/PreBreakPipelineTest.kt`
  - `runtimes/compose/src/main/java/com/styleconverter/runtime/typography/inline/InlineRunFold.kt` (gains only the call before the
    glyph-member arm and the `droppedOutOfFlowMembers` field) + `src/test/…/typography/inline/InlineRunFoldTest.kt`
  - `runtimes/compose/src/main/java/com/styleconverter/runtime/typography/inline/InertOutOfFlowMember.kt` (new; the F2 predicate)
  - `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/wrapping/SoftHyphenPolicy.swift`
  - `runtimes/swiftui/Tests/StyleConverterRuntimeTests/SoftHyphenPolicyTests.swift`
  - `runtimes/swiftui/Tests/StyleConverterRuntimeTests/GreedyLineBreakerTests.swift`
  - `runtimes/compose/src/test/java/com/styleconverter/runtime/core/renderer/RunFoldBreadcrumbSeamTest.kt` (new; the seam-2
    breadcrumb source-scan pin. The existing `SeamReachabilityTest.kt` in the same folder is owned by no lane and is NOT edited:
    L4 only runs it.)
  - `tools/titan/results/wave53-soft-hyphen/`
- **Seams:** `ComponentRenderer.swift` → `seam-1.patch` (required). `ComponentRenderer.kt` → `seam-2.patch` (required whenever F2
  ships).
- **Read-only:** `InlineSpanRing.kt`, `DecorationOps.kt`, `TextWrapApplier.kt`, Compose `SoftHyphenPolicy.kt`, `GreedyLineBreaker.*`,
  `typography/wrapping/WordBreakOpportunities.kt` (pin (d)'s mutation edits a private copy in the lane worktree only: cp → sha256
  → edit → run → cp back → sha256 equal, the L5 M2 precedent; it never lands).
- **May run:**
  - `(cd apps/android-harness && ./gradlew :runtime:testDebugUnitTest --tests '*PreBreakPipelineTest' --tests '*InlineRunFoldTest' --tests '*GreedyLineBreakerTest' --tests '*WordBreakOpportunitiesTest' --tests '*SoftHyphenPolicyTest' --tests '*AutoHyphenationTest')`
  - Catalyst `-only-testing:` `SoftHyphenPolicyTests`, `GreedyLineBreakerTests`, `WordBreakOpportunitiesTests`, `InlineRunFlowTests`.
  - Both seam patches applied in the lane's worktree.
- **Carrier set:**
  - android: `hyphens-span-001`, `hyphens-out-of-flow-001`, `-002`, `hyphenate-character-001`, `-003`, `-004`.
  - ios: `hyphens-span-001`, `hyphens-out-of-flow-001`, `hyphenate-character-001`, `-003`, `-004`.
  - web: none.
  - **Decision against the brief (§9 D3):** `hyphens-auto-control` android and `hyphens-vertical-001` ios are taken OUT of the
    allowed set. Both are predicted byte-identical, so a change there has to fail the control.
  - Rule: a space-less string containing U+00AD that reaches the text label (horizontal-tb, wrap width ≥ 0, not `white-space: pre`).
    On Android, also a runs host whose abspos/fixed member passes the inert predicate. The census found exactly 12 such members,
    all in `hyphens-out-of-flow-001`/`-002`.
- **Unit pins (JVM, `mono` measurer, CH = 19.2).**
  - (a) Verbatim `"high\u00ADway"` from `hyphens-span-001__1-301` at 6·CH → fired `"high‐\nway"`. Mutation: restore the space-only
    guard.
  - (b) The same at 8·CH → `assertSame` identity. Mutation: drop the `!unbreakableOverflow && !tookSoftHyphen` early return.
  - (c) `"fragilistic\u00ADexpiali"` with `dictionaryHyphenation = true` at 12·CH → identity. Mutation: remove `!dictionaryHyphenation &&` at `:187`.
  - (d) `"im\u00ADple\u00ADmen\u00ADta\u00ADtion"` at 4.5·CH → five lines. Mutation (plan-skeptic should-fix 5): make
    `WordBreakOpportunities`' word decomposition (`:87`, `if (ch == '\u00AD')`) treat U+00AD as an ordinary character, so the
    run has no opportunity → no longer five lines → red. (Restoring the space-only guard also reds it, but that is (a)'s
    mutation; (d)'s own pins the multi-opportunity split.)
  - The existing `declinesForASpacelessRunTheWholeRunGateOwns` (`:130`) stays green. It is the over-widening mutation's pin:
    deleting the guard outright turns it red.
  - InlineRunFold:
    - invert `:152`: verbatim `OUT_OF_FLOW_001_HOST3` → `Folded("high\u00ADway")` with `droppedOutOfFlowMembers == 1`;
    - add `-002` box 4 → `Folded("highway")`;
    - negative pins stay `Bailed`: `ch-unit-001`'s span, `hypothetical-inline-alone-on-second-line`'s `Line 2`, and
      `static-inside-inline-001`'s tagless member;
    - mutations: remove the F2 arm; widen the predicate to "any abspos".
  - Catalyst:
    - `admitsPreBreak("high\u{AD}way", dictionary: false)` is true;
    - `admitsPreBreak("Deoxyribonucleic", dictionary: false)` is false;
    - `GreedyLineBreaker.lines("high\u{AD}way", maxWidth: 6·mono)` gives `["high‐","way"]`;
    - mutation: drop the U+00AD clause.
  - The seam-2 breadcrumb gets a source-scan pin in the SeamReachability idiom, in L2's NEW file `RunFoldBreadcrumbSeamTest.kt`
    (plan-skeptic should-fix 6): the log string names `droppedOutOfFlowMembers`. Mutation: delete the clause.
- **Predictions.**

  **gate floor** = `expectations.json` `floor` (plan-skeptic must-fix 2): on `wave53-probe` a cell below it, or a *geometry
  gating* cell whose probe does not print its exact verdict, reverts the commit that carries it (§6 revert rules 3/4). "report"
  means no floor and no geometry trigger: the magnitude is read and labelled. Rules 1 (P on `wave53-open` → f), 2 (Δ ≤ −0.002)
  and 5 (leak) still apply to every row, whatever its tier. 0.95 is the scorer's pass mark ("stays P").

  | cell | prediction | confidence | gate floor · geometry |
  |---|---|---|---|
  | `hyphens-span-001` android | P ≈ 0.994 (= `hyphens-span-002` android 0.9943's picture) | HIGH | 0.99 · gating |
  | `hyphens-out-of-flow-001` android | P ≈ 0.994 with F1+F2 (F1 alone ≈ 0.975) | MED-HIGH | 0.985 · gating |
  | `hyphens-out-of-flow-002` android | P ≈ 0.994 with F2 | MED-HIGH | 0.985 · gating |
  | `hyphens-span-001` ios | f → **P ≈ 0.99** | MED-HIGH | 0.98 · gating |
  | `hyphens-out-of-flow-001` ios | f → **P ≈ 0.99** | MED | report · report |

  Geometry: `python3 tools/titan/results/wave53-plan/soft-hyphen.geometry.py <run>` must print `→ GEOMETRY OK` on the five rows
  above (every box as tall as the ref's 46 px, two ink lines `high‐` / `way` within ±2 px). On wave52-ship those five rows print
  `GEOMETRY WRONG` (box height 26 vs 46 on iOS; line-1 ink x25-76 or x25-54 vs x25-61 on Android).

  Movers, all f today, so none of them can be lost: `hyphenate-character-001`/`-003` android up (MED), `-004` android small (LOW),
  and the three on ios small (LOW). **A flip on any `hyphenate-character-00x` cell is DEGENERATE by construction** (U+2010 is baked
  where the ref paints nothing, `•` or `/-/`; 4(f)). It is counted, named and never claimed.
- **Must not move (37 cells in `expectations.json`; the watchlist writes each line once, so the three
  `hyphenate-character-005` lines sit in the L1 block).**
  - `css-text/hyphens/hyphenate-character-005` ×3 (bidi-baked, `white-space: pre`, declined before L2's guard; must-not-move for
    L1 too, §4 step 3).
  - android + ios of:
    - `hyphens-auto-control`, `-manual-011`/`-012`/`-013`, `-manual-inline-011`/`-012`, `-none-011`,
      `-none-shy-on-2nd-line-001`, `-span-002`;
    - `css-overflow/line-clamp/block-ellipsis-014` / `-028`.
  - `hyphens-punctuation-001` android; `hyphens-vertical-001` ios.
  - The nine F2-bail controls on android: `CSS2/abspos/static-inside-inline-001/-002/-003`,
    `hypothetical-inline-alone-on-second-line`, `between-float-and-text`, `css-pseudo/first-letter-list-item-dynamic-001`,
    `css-tables/abspos-container-change-dynamic-001`, `css-position/position-absolute-semi-replaced-stretch-other`,
    `css-values/ch-unit-001`.
  - The wave-52 thin pass `css-text/hanging-punctuation/hanging-punctuation-inline-001 ios P 0.9555`.
  - All web hyphens cells.
- **Probe sections:** css-text (every carrier), plus CSS2, css-pseudo, css-tables and css-position, which hold eight of the nine
  F2-bail controls (CSS2 ×5, one each in the others) and are probed for L1/L3/L4 anyway. Not probed: css-overflow (`block-ellipsis-014/-028`: spaced U+00AD strings, F1
  identity by construction) and css-values (`ch-unit-001`, an F2 bail). S-L2's JVM census over all 24 space-less strings and all
  21 abspos hosts replaces them before the closing gate, which reads both sections.
- **A/B owed after the closing gate:** the drop-F2 arm, Android, css-text (§8).
- **Lane note:** `tools/titan/results/wave53-soft-hyphen/_note.md`.

### L3 · canvas-root (effort M) — briefs `contents-root-background.md` (item A, F1+F2), `display-table-body.md` (item B)

Queue 0(ac) and obligation 0(c) for A; 0(l″) first bullet and obligation 0(b) for B. **A is built and pinned first. B starts only
once A's pins are green.** The two items touch the same three canvas files, which is why they share a lane: disjoint ownership
forbids splitting them.

**Item A — root background image propagates to the canvas.**
- **Targets.**
  - `wave52-ship css-display/display-contents-root-background web f 0.534` / `ios f 0.5346` / `android f 0.5355`. The captures are
    white where the ref is all (0,128,0); the text is right.
  - `css-backgrounds/background-attachment-margin-root-001` ×3 f 0.451 and `-002` ×3 f 0.339.
- **Mechanism.** Root-to-canvas propagation is implemented for the colour layer only, three times over:
  - web `resolveCanvasBackground` (`ComposedCaptureGallery.tsx:117/:128`);
  - Android `resolveComposedCanvasBackground` (`ScreenshotCaptureScreen.kt:1308-1316`);
  - iOS `ComposedCaptureCanvas.canvasBackground` (`CaptureCanvas.swift:442`).

  None of them reads the body-root's `BackgroundImage`. `display: contents` is not the cause.
- **Change.**
  - Three twins of one rule, under the same containment gate as the colour.
  - The canvas paints the body-root's image layers. The forest's body-root loses its `Background*` image keys, as the twin of
    `withCanvasOwnedBodyMargin`.
  - F1, the tile origin per layer: `fixed` → ICB origin; `scroll`/`local` → the root's padding box (ICB plus the canvas-owned
    margin). Never pass `fixed` through verbatim on web.
  - F2, the painting surface: the framed outer surface when the stack is uniform by construction; otherwise the ICB only.
    - Uniform means every layer is a single-sRGBA gradient or a `data:` PNG whose IHDR is 1×1, and every layer repeats on both
      axes.
    - F2 mirrors `capture-browser-ref.mjs:728` `padColorFor` and says so in code ("capture-frame chrome").
  - Colour handling is untouched.
- **Spec.**
  - css-backgrounds-3 §2.11.2 (the root's background becomes the canvas background and is not painted again).
  - §3.4 (`fixed` is relative to the viewport).
  - css-display-3 §2.7.
  - css-contain-2 §2 (the existing gate).
  - The natives' root-`contents` self-strip stays spec-divergent. It is recorded, not fixed (no carrier needs it).

**Item B — a `display: table` body forms a table on the canvas.**
- **Targets.**
  - `wave52-ship CSS2/css21-errata/s-11-1-1b-006 ios P 0.9953` / `android P 0.9944` (DEGENERATE: the square sits at y51-70 where
    the ref has y56-75).
  - `web P 0.9941`, also wrong (y66-85); newly measured by the brief.
- **Mechanism.**
  - Body children become sibling roots (`extract-fixture.mjs` slots them only under a body that declares a height, ~l.9590). So
    `Display TABLE` applies to an empty box.
  - The natives stack a 10-px gap and then honour the td's −15 through `MarginApplier`'s offset. The net is −5, but this is not
    margin collapsing.
  - Web wraps the td in its own anonymous table.
- **Change.**
  - A new pure helper twin, `TableBodyForest.{kt,swift}`, gated on the body-root's last `Display` ∈ {TABLE, INLINE_TABLE}.
  - It replaces the in-flow run after the body-root with a synthetic `TABLE` (carrying `BorderSpacing`/`BorderCollapse`) → one
    `TABLE_ROW` → cells, following §17.2.1 rule 2: a run of non-cell roots goes into one anonymous `TABLE_CELL`, and cells pass
    through with `Margin*` removed (§8.3).
  - Out-of-flow roots stay in place.
  - It returns the same instance when there is no trigger, when the run holds a proper table child, or when the body paints its
    own border or padding.
  - Call sites: `ScreenshotCaptureScreen.kt` in the `remember(roots, canvasMargin)` rewrite (:1705), and `CaptureCanvas.swift` as
    the input of `FixedHoist.split` (:379).
  - Web: the `data-capture-flow` wrapper becomes `display: table` with the body's `border-spacing`. Chrome does the fixup.
- **Spec:** CSS 2.1 §17.2.1 rule 2, §8.3 ("Applies to" excludes table-internal boxes), §4.2 (`display: caption` is ignored).
- **Probe-gated:** if the probe does not print `square rows 56-75 (20) x 24-43 | red px 0` on a native, that native's call site is
  reverted before the closing gate (its own commit, B-ios or B-android; §6 rule 6). Web may ship alone.
- **Decision against the brief (§9 D1):** the brief said NO-GO unless the T10 lane is staffed. It is staffed here instead.

**Lane facts.**
- **own (item A):**
  - `apps/web-harness/src/ui/ComposedCaptureGallery.tsx`
  - `apps/web-harness/tests/ui/ComposedCanvasRootBackgroundImage.test.tsx` (new)
  - `runtimes/web/src/engine/background/RootBackgroundPropagation.ts` + `runtimes/web/tests/background/RootBackgroundPropagation.test.ts` (new)
  - `apps/android-harness/app/src/main/java/com/styleconverter/test/screenshot/ScreenshotCaptureScreen.kt`
  - `apps/android-harness/app/src/test/java/com/styleconverter/test/screenshot/ComposedCanvasRootBackgroundTest.kt` (new)
  - `runtimes/compose/src/main/java/com/styleconverter/runtime/background/RootBackgroundPropagation.kt` (new; the pure rule:
    layers, uniformity, origins, strip, the containment gate reused from `WptCaptureMode`)
  - `runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/WptCaptureMode.kt` (call site only, if any)
  - `runtimes/compose/src/test/java/com/styleconverter/runtime/core/renderer/WptCanvasBackgroundTest.kt`
  - `runtimes/compose/src/main/java/com/styleconverter/runtime/color/ColorApplier.kt` — **only** a defaulted per-layer origin
    offset parameter on `applyColors`/`tileAnchor`, so every existing call is byte-identical. The brief's §5 needs it, though its
    file table omitted it (§9 D5).
  - `apps/ios-harness/StyleConverterTest/Screenshot/CaptureCanvas.swift`
  - `runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/WPTCaptureMode.swift`
  - `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/spacing/ComposedRootStack.swift`
  - `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/background/BackgroundImageApplier.swift` — **only** a public
    wrapper over the internal `engineBackgroundImage` (:214) (§9 D5).
  - `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/background/RootBackgroundPropagation.swift` (new)
  - `runtimes/swiftui/Tests/StyleConverterRuntimeTests/WPTCaptureModeTests.swift`, `ComposedRootStackTests.swift`
- **own (item B):**
  - `runtimes/compose/src/main/java/com/styleconverter/runtime/table/TableBodyForest.kt` + `src/test/…/table/TableBodyForestTest.kt` (new)
  - `apps/android-harness/app/src/test/java/com/styleconverter/test/screenshot/ComposedCanvasTableBodyTest.kt` (new; the stack pin)
  - `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/table/TableBodyForest.swift` + `Tests/StyleConverterRuntimeTests/TableBodyForestTests.swift` (new)
  - `apps/web-harness/src/ui/CanvasTableBody.ts` (new; `resolveCanvasTableBody(doc)`) + `apps/web-harness/tests/ui/ComposedCanvasTableBody.test.tsx` (new)
  - `tools/titan/results/wave53-canvas-root/`
- **Seams:** none. `ComponentRenderer.swift:758` `resolvedBackgroundColor` and `isOutOfFlow` are read, never edited.
- **Read-only:**
  - `UaBlockMargins.kt` (`composedRootStackPlan`), `StaticEmMargins.*`, `MarginApplier.*`, `TableBoxTree.{kt,swift}`
  - `CanvasRootHoist`, `FixedHoist.swift`, `ContentsUnboxing.*`, `capture-browser-ref.mjs`
- **May run:**
  - `(cd apps/web-harness && npx vitest run tests/ui/ComposedCanvas)`, `(cd runtimes/web && npx vitest run tests/background)`
  - `(cd apps/android-harness && ./gradlew :runtime:testDebugUnitTest --tests '*WptCanvas*' --tests '*TableBodyForest*' --tests '*ColorApplier*' :app:testDebugUnitTest --tests '*ComposedCanvas*' --tests '*UaBlockMargins*')`
  - Catalyst `-only-testing:` `WPTCaptureModeTests`, `ComposedRootStackTests`, `TableBodyForestTests`, `BackgroundImageURLTests`
  - `node tools/titan/results/wave52-composed-canvas/ios-source-pins.mjs`, without `--record`. It replays the 7 IcbClip source pins
    against the edited `CaptureCanvas.swift`, and must stay `pins=7/7 GREEN`.
  - The briefs' PNG replays and `display-table-body.geometry.py`.
  - Not in the lane: the ios-harness XCTest scheme needs L5's `project.yml` and a booted simulator. It is checked at integration,
    on the L5 + L3 tree (§4 steps 2 and 7), and must stay 30/30.
- **Carrier set:**
  - all three platforms: `{display-contents-root-background, background-attachment-margin-root-001, -002}` (A) and
    `{css21-errata/s-11-1-1b-006}` (B).
  - Rule:
    - A: a component with `meta.role == 'body-root'` and a `BackgroundImage` property (3 documents of 1435).
    - B: a body-root whose last `Display` is `TABLE`/`INLINE_TABLE` (1 document).
  - Outside these four documents every capture must be byte-identical. That includes the 86 colour-only and 195
    background-less body-root documents (`s-11-1-1b-005`'s `TABLE_CELL` body among them) and the 1151 documents with no body-root.
- **Unit pins (verbatim wave52-ship body-root payloads).**
  - A1 the target body-root (the data-URI layer) gives one image layer with that exact URL, `uniform = true`, the framed surface,
    and a stripped forest node. Mutations: the colour-only read; drop the strip.
  - A2 the margin-root-001 body-root gives `uniform = false`, ICB-only, origins [(50,50), (0,0)]; 002 is the mirror. Mutations:
    "every stack is uniform"; ignore attachment.
  - A3 IHDR 1×1 is uniform, 2×2 is not. Mutation: true for any url.
  - A4 the target plus `Contain ["LAYOUT"]` gives no propagation. Mutation: drop the gate.
  - A5 the verbatim `initial-background-color` / `a98rgb-003` body-roots give deep-equal canvas styles and modifiers, with no image
    key written. Mutation: always emit `backgroundImage: 'none'`.
  - A6 pins A1–A5 run on the Compose (JVM) and iOS (Catalyst) twins.
  - B-shape: verbatim 006 gives `[body-root unchanged, TABLE#table{BorderSpacing 0} → ROW → [CELL{1-142}, 2-143 with no Margin*], 3-144 unchanged]`.
    Mutation (plan-skeptic should-fix 5): make the synthetic table carry the body-root (re-parent the run under the body-root
    with `Display TABLE`) instead of a fresh `TABLE` root → the first element is no longer the unchanged body-root → red.
  - B-M1: identity (the same instance) on verbatim 005 and 001. Mutation: drop the trigger.
  - B-M2: no `MarginTop` on 2-143. Mutation: skip the strip.
  - B-M3: 3-144 stays a root. Mutation: drop the out-of-flow filter.
  - B-M4: `row.children[0]` is a `TABLE_CELL`. Mutation: put the div straight into the row.
  - B-M5: the table carries `BorderSpacing`. Mutation: don't copy it.
  - B-stack: 0 gap above `#table`. Mutation: keep 1-142 in the stack.
  - B-web: `[data-capture-flow]` is `display: table; border-spacing: 0px`. Mutation: remove the branch. The existing M1 pins stay
    green.
- **Predictions.**

  **gate floor** = `expectations.json` `floor` (plan-skeptic must-fix 2): on `wave53-probe` a cell below it, or a *geometry
  gating* cell whose probe does not print its exact verdict, reverts the commit that carries it (§6 revert rules 3/4). "report"
  means no floor and no geometry trigger: the magnitude is read and labelled. Rules 1 (P on `wave53-open` → f), 2 (Δ ≤ −0.002)
  and 5 (leak) still apply to every row, whatever its tier. 0.95 is the scorer's pass mark ("stays P").

  | cell | prediction | confidence | gate floor · geometry |
  |---|---|---|---|
  | `display-contents-root-background` web / ios / android | f → **P** (replays 1.0000 / 0.9995 / 0.9991) | HIGH | 0.99 · gating |
  | `background-attachment-margin-root-001` ×3 | f → **P** (replays 0.9996 / 0.9992 / 0.9997) | MED | report · report |
  | `background-attachment-margin-root-002` web | f → **P** | MED | report · report |
  | `background-attachment-margin-root-002` ios / android | f → **P**; the natives must translate `fixed` themselves | MED-LOW | report · report |
  | `s-11-1-1b-006` web | stays P, square at y56-75 (score ≥ 0.998) | HIGH geometry / MED score | 0.95 · gating |
  | `s-11-1-1b-006` ios / android | stays P, picture right (≈ 0.998 by analogy with 007) | MED geometry / LOW number | report · probe-gated revert of the native call site |

  Geometry: `python3 tools/titan/results/wave53-plan/canvas-root.geometry.py <run>` must print `→ GEOMETRY OK` on the nine item-A
  rows (frame ≥ 98 % green with the 16-px chrome green and the text where the ref has it; margin-root: ICB painted, frame white,
  band starts at y66… for 001 and y16… for 002), and `display-table-body.geometry.py <run> 006` must print
  `square rows 56-75 (20) x 24-43 | red px 0` ×3. On wave52-ship every capture row prints `GEOMETRY WRONG` (green 0.0; painted
  (66,66)-(323,365)) and 006 prints y66-85 (web) / y51-70 (natives).

  Falsifiers: F1 without F2 replays f 0.8785 on margin-root-001, and ICB-only painting with a white frame replays f ≈ 0.879 on the
  target.
- **Must not move (78 cells, `watchlist.txt` L3 block).** All ×3:
  - `css-contain/contain-{body,html}-bg-001…004`
  - `css-cascade/initial-background-color`, `css-color/a98rgb-003`
  - `css-masking/clip-path/clip-path-document-element{,-will-change}`
  - `filter-effects/backdrop-filter-root-element`, `css-display/display-contents-text-only-001`
  - `css-images/css-image-fallbacks-and-annotations002/003` (the same data-URI payload)
  - `CSS2/css21-errata/s-11-1-1b-001…005`, `-007…009`
  - `css-position/position-absolute-dynamic-static-position-table-cell`, `css-tables/baseline-vertical`

  The ring-fenced test is reported only.
- **Probe sections:** css-display, css-backgrounds, CSS2, plus the briefs' controls (plan-skeptic should-fix 3): css-contain,
  css-cascade, css-color, css-masking, filter-effects, css-images (contents-root-background §8: the colour-only, contained and
  clipped roots, including the web clip-path canvas variant and the same data-URI payload), and css-tables, css-position
  (display-table-body §8: the table path and the table-cell-root neighbour). filter-effects holds the ring-fenced test: read and
  reported, never a target.
- **Lane note:** `tools/titan/results/wave53-canvas-root/_note.md`. It also records the brief's §10 neighbour finding: 22 of the 24
  passing `s-11-1-1b-00x` cells don't match their ref.

### L4 · float-avoid (effort M) — brief `layout-degenerates.md` §5.1

Queue 0(l″) second bullet and obligation 0(b).

- **Targets.**
  - `wave52-ship css-contain/contain-inline-size-bfc-floats-001 ios P 0.9531` / `android P 0.9519` (DEGENERATE: the orange bar is
    at y388-407, below every float, where the ref has y288-307).
  - `-002 ios f 0.9322` / `android f 0.9311` (the ref has the bar at y88-107).
  - `css-display/display-flow-root-002 ios P 0.9734` / `android P 0.9734`. These are DEGENERATE and newly measured: the outline is
    at x16, y215-416, where the ref has x265-266, y115-316.
- **Mechanism.** Neither native has §9.5 float avoidance for a BFC sibling.
  - Compose: `blockFloatSegments` returns null on R,L,R (`FloatRowPacking.kt:148-158`), and `FloatClearance.resolve` returns null
    with no `Clear`. Each float therefore lays out as an in-flow 100-px block, and the BFC starts at y300.
  - SwiftUI: identical (`blockFloatSegments()` :3027-3053 → the plain `VStack`).
  - The wire is faithful: web renders the same IR pixel-exact.
- **Change.** A pure `FloatAvoidPlan` plus a thin `FloatAvoidLayout` adapter on both natives, dispatched FIRST in the block child
  loop under the capture gate.
  - The gate (G1–G8), all of which must hold:
    - G1 capture mode only;
    - G2 the container is a composed root or a BFC root, and no earlier root carries a Float;
    - G3 plain horizontal-tb LTR, with no text, runs or pseudos;
    - G4 k ≥ 1 floats followed by exactly one in-flow BFC root, which is last;
    - G5 no `Clear`, and no run of same-side floats;
    - G6 every float has px `Width`/`Height` and no margin, padding or border-width wire;
    - G7 the BFC's used inline size is provable: px `Width`, or `fit-content`/`min-`/`max-content` with `Contain` ∋
      INLINE_SIZE/SIZE/STRICT, which gives 0;
    - G8 the BFC paints nothing of its own unless its width is px (outline is allowed).
  - The algorithm:
    - floats are placed by §9.5.1;
    - the BFC goes at the first y ∈ {0} ∪ {float bottoms} whose band [y, y+h) is half-open and has a gap ≥ its inline size;
    - the container reports the BFC bottom (§10.6.3) and is as wide as the incoming constraint (400, not the 358 ICB).
  - Adapters:
    - Compose measures floats with `Constraints()` under `LocalSelfAlignmentHandled provides true`, and the BFC with the Column's
      constraints;
    - SwiftUI measures floats `.unspecified`, and the BFC at `ProposedViewSize(width: proposal.width, height: nil)`;
    - both place children in wire order.
- **Spec:**
  - CSS 2.1 §9.5, §9.5.1, §10.6.3, Appendix E (steps 4/5/7).
  - css-display-3 `flow-root`.
  - css-contain-3 #containment-inline-size; css-sizing-3 fit-content.
  - Rejected: offsetting only the BFC (a negative-offset channel W5 refused, and the wrong container height); general
    `contain: inline-size` sizing (25 more carriers).
- **own (all new):**
  - `runtimes/compose/src/main/java/com/styleconverter/runtime/layout/FloatAvoidPlan.kt`, `…/layout/FloatAvoidLayout.kt`
  - `runtimes/compose/src/test/java/com/styleconverter/runtime/layout/FloatAvoidPlanTest.kt`, `…/core/renderer/FloatAvoidSeamWiringTest.kt`
  - `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/layout/FloatAvoidPlan.swift`
  - `runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/FloatAvoidLayout.swift`
  - `runtimes/swiftui/Tests/StyleConverterRuntimeTests/FloatAvoidPlanTests.swift`, `FloatAvoidLayoutRasterTests.swift`
  - `tools/titan/results/wave53-float-avoid/`
- **Seams:**
  - `ComponentRenderer.kt` → `seam-1.patch`: a branch before `val floatSegments =` (:3670).
  - `ComponentRenderer.swift` → `seam-2.patch`: an `else if` before `blockFloatSegments()` (:1876) that re-publishes
    `.environment(\.floatClearancePlan, clearanceScopePlan)`.
- **Read-only:** `FloatRowPacking.kt`, `FloatClearance.kt`, `FloatBlockLayout.swift`, `PerformanceExtractor.*`, `PerformanceConfig.kt`.
- **May run:**
  - `(cd apps/android-harness && ./gradlew :runtime:testDebugUnitTest --tests '*FloatAvoid*' --tests '*FloatClearance*' --tests '*FloatRowPacking*' --tests '*SeamReachability*')`
  - Catalyst `-only-testing:` `FloatAvoidPlanTests`, `FloatAvoidLayoutRasterTests`, `FloatClearanceTests`, `FloatRowPackingTests`, with
    both seam patches applied in the lane worktree.
- **Carrier set:**
  - ios and android: `{contain-inline-size-bfc-floats-001, -002, display-flow-root-002}`; web: none.
  - Rule: `FloatAvoidPlan` returns non-null for a container.
  - **The skeptic's census (§8 step 5)** runs the real Kotlin planner over every container of all 1435 per-test IR documents in one
    JVM test. Exactly these 3 containers must come back non-null.
- **Unit pins (verbatim sibling lists; h injected).**
  - P1 001 → BFC at (0,200).
  - P2 002 → (0,0).
  - P3 flow-root-002 → (250,100).
  - P4 on 001's floats, h = 100 → (0,0) and h = 101 → (0,200).
  - P5 null on `display-flow-root-001`, `adjoining-float-new-fc`, `floats-bfc-003`, `contain-content-001` and
    `direction-upright-001`.
  - P6 reported heights 370 / 20 / 300.
  - P7 right-float x is read from the 400-px container width.
  - Mutations:
    - M1 drop containment → P1/P2 go to (0,300);
    - M2 closed bands → P4, P3;
    - M3 right floats at x = 0 → P1, P3;
    - M4 delete the G7 bail → P5;
    - M5 delete the G5 bail → P5;
    - M6 delete the seam branch → the Compose `FloatAvoidSeamWiringTest` source scan, and the iOS `FloatAvoidLayoutRasterTests`
      (ImageRenderer: orange at container rows 200-219).
    - M7 (plan-skeptic should-fix 5) report max(float bottom, BFC bottom) instead of the BFC bottom → P6 red on 002 (300 for 20;
      001 and flow-root-002 are unchanged, 370 and 300 already being the max).
    - M8 take the container width from the canvas proposal (358) instead of the root's own 400 → P7 red (001's first right float
      at x158 for x200; flow-root-002's at x108 for x150), and P1/P3's float placements with it.
- **Predictions.**

  **gate floor** = `expectations.json` `floor` (plan-skeptic must-fix 2): on `wave53-probe` a cell below it, or a *geometry
  gating* cell whose probe does not print its exact verdict, reverts the commit that carries it (§6 revert rules 3/4). "report"
  means no floor and no geometry trigger: the magnitude is read and labelled. Rules 1 (P on `wave53-open` → f), 2 (Δ ≤ −0.002)
  and 5 (leak) still apply to every row, whatever its tier. 0.95 is the scorer's pass mark ("stays P").

  | cell | prediction | confidence | gate floor · geometry |
  |---|---|---|---|
  | `contain-inline-size-bfc-floats-001` ios | P ≈ 0.998 (sim 0.9984) | HIGH | 0.99 · gating |
  | `contain-inline-size-bfc-floats-001` android | P ≈ 0.997 (sim 0.9972) | HIGH | 0.99 · gating |
  | `-002` ios | f → **P ≈ 0.998** | HIGH | 0.99 · gating |
  | `-002` android | f → **P ≈ 0.997** | HIGH | 0.99 · gating |
  | `display-flow-root-002` ios / android | P ≈ 1.000 (the 1-px vs 2-px outline is the only unknown) | MED | report · report |
  | `anchor-position-multicol-007` android | stays P 0.9519, byte-identical, still DEGENERATE (recorded wall) | HIGH | must-not-move (R4/R7) |

  Geometry: `python3 tools/titan/results/wave53-plan/float-avoid.geometry.py <run>` must print `→ GEOMETRY OK` on the six native
  rows (orange bbox, flow-root outline bbox and the blue float silhouette each within ±1 px of the ref; the silhouette catches a
  right float placed from 358 instead of 400). On wave52-ship those rows print `GEOMETRY WRONG` (orange y388-407; outline x16
  y215-416); the web and ref rows print `GEOMETRY OK`.

- **Must not move (155 cells, `watchlist.txt` L4 block).**
  - The 3 web carrier cells (P 1).
  - The 7 refused shapes ×2 natives (`adjoining-float-new-fc`, `floats-bfc-003` and
    `adjoining-float-nested-forced-clearance-002` are P on both natives).
  - The 143 passing native cells of the 92-test float control set.
  - `anchor-position-multicol-007` android.
- **Probe sections:** css-contain, css-display, CSS2 (26 float-carrying tests, incl. the floats-clear controls) and css-grid (32,
  the largest block of the 92-test control set) — the brief's §8 list (plan-skeptic should-fix 3). The Swift `FloatAvoidPlan` runs
  in every capture-mode block loop, so a Swift crash or leak there must show at the probe, not first at the closing gate.
- **Lane note:** `tools/titan/results/wave53-float-avoid/_note.md`.

### L5 · harness-hygiene (effort S–M) — brief `harness-hygiene.md` T1–T4 (T5 OUT)

Queue: "## Known-broken" (the ios-harness XCTest, smoke.sh port 3000, the two host-wide stops) and the standing constraint "Capture
and gate scripts must never stop a process this checkout did not start".

- **Targets.**
  - T1: the ios-harness XCTest goes from 17/30 to 30/30. It has two defects, not one:
    - 9 tests hit NSCocoaErrorDomain 257 from inside the simulator;
    - 4 `ComposedCanvasPaddingTests` assert the stale wave-24 rule. The shipped resolver is "frame + declared"; the observed
      16/56/20/16 are exactly its arithmetic.
  - T2: `gate-driver.sh:138-139` `./gradlew --stop` ×2.
  - T3: `section-runner.sh:641` and `provision-devices.sh:127` `pkill -9 -x adb`.
  - T4: `smoke.sh` Tier 5/11 hard-wired to :3000.
- **Change.**
  - T1-a: `apps/ios-harness/project.yml` test target `sources:` gains `CaptureCanvas.swift` and the two vendored per-test IR JSONs
    with `buildPhase: resources`. The tests read them through `Bundle(for:)` + `XCTUnwrap`, with no `#filePath` fallback and no
    skip guard.
  - T1-b: four padding tests to the wave-25 round-3 contract.
  - T2: `kill_own_gradle_daemons [root …]`. A daemon is ours iff its `daemon-<pid>.out.log` has at least one `currentDir=` line and
    every one of them is inside one of the given roots (default `$PROJECT_ROOT`), boundary-checked. It is stopped with SIGTERM;
    others are named and left. The root arguments exist so the orchestrator can stop the lane worktrees' daemons through their
    owner (§8 step 7a) without a host-wide `--stop`.
  - T3: `kill_wedged_adb_server` (only the tcp:5037 `fork-server server` listener).
  - T4: `smoke_pick_web_port`: an explicit foreign `WEB_PORT` is an error; a held default picks from the new 3400–3499 range; then
    `vite --port $WEB_PORT --strictPort`, and `export WEB_PORT` before Tier 5.
  - Spec: none. These are the standing constraint above, and, for T1-b, CSS 2.1 §8.4 plus `resolvedPadding`'s doc comment.
  - **T5 (`start_adb_detached` kills the adb server unconditionally at every provision) stays OUT** (brief default). The orchestrator
    records it in Known-broken as the third host-wide stop.
- **own:**
  - `apps/ios-harness/project.yml`
  - `apps/ios-harness/StyleConverterTestTests/ComposedCanvasIcbClipTests.swift`, `ComposedCanvasPaddingTests.swift`, `ComposedRootInlineFlowTests.swift`
  - `tools/titan/fixtures/README.md`
  - `tools/titan/own-processes.sh`, `tools/titan/own-processes.test.mjs`
  - `tools/titan/gate-driver.sh`
  - `tools/titan/section-runner.sh` (source line + the `:641` call), `tools/titan/provision-devices.sh` (source line + the `:127` call)
  - `tools/visual/smoke.sh`, `tools/visual/smoke-port.test.mjs` (new), `tools/visual/web-port-guard.sh` (header note of the 3400–3499 range only)
  - `tools/titan/results/wave53-harness-hygiene/`
- **Seams:** none.
- **Read-only:** `apps/ios-harness/StyleConverterTest/Screenshot/CaptureCanvas.swift`, which L3 owns. L5's M2 mutation edits a
  private copy in L5's own worktree (cp → sha256 → edit → run → cp back → sha256 equal), on the cdb8a845 text.
- **May run:**
  - Script edits only when the orchestrator says the device is idle (§1).
  - `node --test tools/titan/own-processes.test.mjs tools/titan/section-runner.test.mjs tools/visual/web-port-guard.test.mjs tools/visual/interaction-states.test.mjs tools/visual/smoke-port.test.mjs`.
    `section-runner.test.mjs` was added at plan-skeptic round 2: it source-pins `section-runner.sh` (:24) and
    `provision-devices.sh` (:137, :276; the qemu `pkill` pin at :290-295), and L5 edits both.
  - **Device-idle, orchestrator-granted:** `xcodegen generate` plus the pbxproj greps; the XCTest scheme on the booted simulator
    (30/30) and mutations M1–M3; the smoke foreign-listener check (`python3 -m http.server 3000`, then `smoke.sh --quick`).
  - **Exclusive window** (no gate, and no other lane's Gradle running): the "predicate → unconditional kill" mutation, which would
    SIGTERM every live Gradle daemon on the host.
  - **Never executed:** the `pkill -9 -x adb` restore mutation. It is a source pin only.
- **Carrier set: `[]` (empty) on all three platforms.**
  - The gate builds `-target StyleConverterTest` only.
  - `stop_our_processes` runs before provisioning.
  - The adb helper runs only in the timeout branch: 0 firings in wave51-fix … wave53-open.
  - `smoke.sh` is not in the gate path.
- **Unit pins.** As in brief §8.2–8.3, with throwaway `GRADLE_USER_HOME` / `ANDROID_ADB_SERVER_PORT` and fake daemons whose logs
  carry a VERBATIM 9.6.1 `Received command: Build{…currentDir=…` line.
  - Gradle cases: this checkout's daemon is stopped; another checkout's, a mixed one and a sibling-prefix one survive and are
    named; a daemon with no log survives. With two roots given, a daemon whose `currentDir=` lines span both is stopped, and one
    spanning a given root and a foreign one survives.
  - Named Gradle case (plan-skeptic round 2, should-fix 2): the measured shape of 8 of the 210 9.6.1 daemon logs on this host.
    A daemon whose log holds `currentDir=<PROJECT_ROOT>/apps/android-harness` AND `currentDir=<lane tree>/apps/android-harness`
    is **stopped** by `kill_own_gradle_daemons "$PROJECT_ROOT" <lane tree>`. The same daemon is **left and named** by
    `kill_own_gradle_daemons "$PROJECT_ROOT"` alone (the gate-driver default), which is why §8 step 7a passes both. Mutation:
    evaluate the roots one at a time, so a daemon is ours only if ONE root holds all its lines → the two-root case survives → red.
  - adb cases: the listener is killed; a foreign listener and a non-listening decoy survive.
  - Source pins: no live `gradlew --stop` or `pkill … adb` in the gate path; no live `localhost:3000`; `--strictPort`.
  - Mutations: unconditional kill (exclusive window); a prefix test without the `/` boundary; return the default port
    unconditionally; plus XCTest M1 (drop a resource entry → "not a resource"), M2 (clip after background → IcbClip red) and M3
    (padding rule → four red).
- **Predictions.**
  - XCTest 30/30: padding 4 HIGH; IcbClip 7 HIGH-MED (the node replay prints 7/7 GREEN on sha256 `3da6fc574fbc98e8`); InlineFlow 2
    MED.
  - Corpus: 0 gained / 0 lost / 0 movers (HIGH).
  - A foreign Gradle daemon survives and is named in `driver.log`.
- **Must not move:** the five iOS cells the unblocked tests pin:
  - `CSS2/abspos/static-inside-inline-block ios P 0.9974`
  - `css-cascade/scope-pseudo-element ios f 0.9668`
  - `css-flexbox/align-items-007 ios P 0.9974`
  - `css-gaps/flex/flex-gap-decorations-027 ios P 0.9997`
  - `css-masking/clip-path/clip-path-circle-007 ios P 0.9993`

  Every other capture is identical too (by decoded pixels on web, R4).
- **Probe:** its own `gate-driver.sh wave53-hh-probe --sections css-cascade,css-counter-styles,css-flexbox,css-text --skip-fixture-net`
  (`expectations.json` `hhProbe`). It runs right after L5 lands (§4), after §8 step 7a.
  - Afterwards `driver.log` must contain no `--stop`, provisioning must exit 0 and every column must be full.
  - The control against `wave53-open` with the empty carrier set must show 0 changed captures by DECODED pixels, on all three
    platforms.
  - css-counter-styles and css-text were added at the second plan-skeptic review. This read is the wave's only measurement of
    web run-to-run determinism on this host over the sections that hold L1's counter-suffix crop and the bidi-lines and hyphens
    controls (header; §8 step 7).
- **Lane note:** `tools/titan/results/wave53-harness-hygiene/_note.md`. It carries the BACKLOG corrections for the orchestrator (§5).

## 3. Seam-hunk registry

| seam file | hunk | lane | patch | note |
|---|---|---|---|---|
| `tools/titan/extract-fixture.mjs` | `findImpliedClose` + `IMPLIED_CLOSE_SCOPE` beside `AUTO_CLOSE_TRIGGERS` (l.1947); `walkChildren` trigger site l.1821-1833; `scanOwnText` trigger site l.3560-3570 | L1 | `wave53-lists-bakes/seam-1.patch` | the only lane on this seam; both sites in ONE patch, so the two scans keep agreeing (the wave-50 B4 agreement) |
| `runtimes/swiftui/…/Renderer/ComponentRenderer.swift` | `:4919-4921` pre-break guard → `SoftHyphenPolicy.admitsPreBreak(transformedText, dictionary: hyphenLocale != nil)` | L2 | `wave53-soft-hyphen/seam-1.patch` | needs L2's `SoftHyphenPolicy.swift` helper in the same commit |
| | `:1876` new `else if` FloatAvoidLayout branch before `blockFloatSegments()` | L4 | `wave53-float-avoid/seam-2.patch` | ~3000 lines from L2's hunk; no shared function |
| `runtimes/compose/…/core/renderer/ComponentRenderer.kt` | `:3821-3852` runs-fold breadcrumb: `, N out-of-flow member(s) dropped` from `runFold.droppedOutOfFlowMembers` | L2 | `wave53-soft-hyphen/seam-2.patch` | REQUIRED with F2 (§9 D2); needs L2's `InlineRunFold.kt` field in the same commit |
| | before `val floatSegments =` (`:3670`) FloatAvoidLayout branch, mutually exclusive with float runs by G5 | L4 | `wave53-float-avoid/seam-1.patch` | ~150 lines above L2's hunk, no textual overlap; apply AFTER L2's seam-2, `git apply --check` first, re-cut against the L2-applied tree if it does not apply with offset |
| `apps/web-harness/src/sdui/ComponentRenderer.tsx` | — | — | — | no lane needs it |

Five seam patches (L1 one, L2 two, L4 two) carry seven hunks; L1's extractor patch holds three of them. No two hunks overlap. The two native ComponentRenderers each get one L2 hunk and one L4
hunk, so L2 is applied before L4 on both. The JVM and Catalyst focused suites of both lanes are re-run after each applied patch.

## 4. Landing order (and the conflicts it resolves)

1. **Commit `tools/titan/results/wave53-plan/`.** That is the seven briefs, censuses and scripts, `PLAN.md`, `watchlist.txt`,
   `expectations.json` and `plan-build.py`. Do it once `wave53-open` is scored. Nothing in this directory is executed or built by
   the gate, but git state is left alone while the gate runs.
2. **L5 first.**
   - It changes the gate path itself (`gate-driver.sh`, `section-runner.sh`, `provision-devices.sh`). With L5 in first, every later
     device run uses one driver: the L5 probe, the integrated probe, the closing gate and the A/B.
   - Its own 4-section probe, `wave53-hh-probe` (css-cascade, css-counter-styles, css-flexbox, css-text), proves the driver
     change capture-neutral (empty carrier set) before anything else lands. It also measures web run-to-run determinism on this
     host. It runs after §8 step 7a: ONE `kill_own_gradle_daemons "$PROJECT_ROOT" <every tree that ran Gradle>` call, never
     `--stop`.
   - It also makes the ios-harness XCTest runnable, which L3 must then keep at 30/30.
   - **Conflict L5 ↔ L3:** L5's IcbClip and Padding XCTests read `CaptureCanvas.swift`, which L3 owns and edits. L5 lands on
     cdb8a845's text. In its lane, L3 proves `ios-source-pins.mjs` 7/7 on its edited file. At integration, the XCTest must be 30/30
     on the L5 + L3 tree. L5 never edits the file.
3. **L1.**
   - Unit 1 is ONE commit: seam-1, `counter-bake.mjs` and both `PseudoTextFold` twins.
   - Unit 2 is ONE commit: hunks M and P together.
   - Gate before each commit: the gate-flag wire differential over all 30 sections must change exactly that commit's expected
     set and nothing else (plan-skeptic round 2, nit 11; `expectations.json` `lanes.L1-lists-bakes.revertUnits.*.wire`).
     - Unit 1 against cdb8a845: {`counter-reset-reversed-nested`}. The nested-list brief §6 measured (A)+(B) changing 1 of 1435
       fixtures.
     - Unit 2 against the Unit-1 tree: {`counter-suffix`, `bidi-lines-001`, `bidi-lines-002`, `anchor-center-safe-rtl`}.
     - Cumulative, against cdb8a845: the five wire carriers.
     - Under a P-narrow re-land, Unit 2's set is {`counter-suffix`}.
     - Every time, the three zero-padding bake-root documents (`selectors/dir-style-02a`, `dir-selector-change-003`, `-004`) stay
       byte-identical (hunk P's non-zero guard, §2 L1).
   - **Conflict L1 ↔ L2:** they share the css-text section. Their carrier sets are disjoint, and `hyphenate-character-005` (bidi-baked;
     declined before L2's guard) is must-not-move for both: it is in both lanes' `mustNotMove` lists in `expectations.json`.
4. **L2.** Two commits in one PR: F1 (F1 + F1-iOS + seam-1), then F2 (F2 + seam-2). Each commit is a revert unit (§6 rule 6).
   Its seams go in before L4's.
5. **L4.** Two commits, one per native, each a revert unit (§6 rule 6): L4-android (the Compose files + seam-1) and L4-ios (the
   Swift files + seam-2). Both seams go on the L2-applied tree (§3).
6. **L3, last.**
   - Four commits, each a revert unit (§6 rule 6): item A, then item B as B-web, B-ios and B-android. Each native's call site is
     its own commit, so the probe-gated native revert is a commit revert.
   - The three canvas files are the hottest in the tree: every composed capture goes through them. B's native call sites are the
     one probe-gated change of the wave.
   - **Conflicts L3 ↔ L4:** they share the css-display and css-contain sections; the carriers are disjoint. **L3 ↔ L1:**
     `anchor-center-safe-rtl`'s body-root is `INLINE` with no image, so it is not an L3 carrier.
7. **One sequential full sweep** by the orchestrator, per wave skill Phase 4.
   - Converter, web runtime, compose `:runtime:testDebugUnitTest --rerun-tasks`, **android-harness `:app:testDebugUnitTest`**,
     swiftui Catalyst, **`npm -w apps/web-harness run test`**, the tooling suite, IR conformance.
   - **Plus the ios-harness XCTest scheme, now runnable: 30/30.** Run it in a device-idle window.
   - The counts move (new pins in every lane). Stamp README / CLAUDE.md / STATUS / tree READMEs, then
     `tools/visual/doc-staleness-check.sh` exit 0.

## 5. Not staffed this wave, and why

- **`css-anchor-position/anchor-position-multicol-007` android — RECORD-AS-WALL** (layout-degenerates §4.2/§5.2). It stays
  `wave52-ship … P 0.9519`, DEGENERATE. Three gaps stand between it and an honest pass, and no wave-53 lane can close any of them:
  - there is no native anchor-positioning runtime: `LayoutExtractor.{kt,swift}` registers the anchor properties parse-only;
  - the post-load bake cannot deliver `position-area` geometry, because `anchorInsetMismatch` (`post-load-extract.mjs:722`) never
    probes `place-self: stretch` boxes. Fixing that is an instrument change that needs Chromium measurements of resolved against
    painted offsets, which cannot run on a gated host;
  - no native fragments a relpos subtree, with its abspos descendants, inside vertical-rl multicol.

  The same population holds 24 native red-ink anchor passes. For the orchestrator:
  - re-word 0(l″)'s third bullet as a wall;
  - add to "Instrument decisions pending": widen `anchorInsetMismatch` to `position-area` boxes, pre-registered, measured in
    Chromium first;
  - add the side observation that `anchor-name-multicol-003` **web** scores SSIM 1.0000 with 1 500 strict-red px.
- **`css-view-transitions/column-span-during-transition-doesnt-skip` ×3 (0(ad); `wave52-ship … f 0.9453` ×3).** No brief was
  written. The remedy candidates are:
  - an isolation window that fits the 500×200 spanner, which is an untraced vt-bake change;
  - a group-tolerant ring rule shared with `capture-browser-ref.mjs`, which is an INSTRUMENT change.

  The second cannot ride in a wave whose lost list is pre-registered empty against `wave53-open` without its own calibration run.
  It stays a wave-54 brief, with the vt-bake bail log as its evidence.
- **`css-gaps/flex/flex-gap-decorations-033` ios + android (7(e¹); `wave52-ship … f 0.94` ×2).** No brief, no census. The work is
  native zero-width-gap rule decorations. In its own words, 7(e¹) is "the natives' missing zero-width-gap decorations … this item's
  runtime work", an L-sized feature on both natives. It stays queued.
- **harness-hygiene T5.** `start_adb_detached` runs `adb kill-server` unconditionally at every provision. It changes the
  provisioning path, and whether the gate's determinism record depends on a fresh server is untested. Not taken. The orchestrator
  adds it to Known-broken as the third host-wide stop.
- **Out-of-family residuals the briefs measured, with no lane:**
  - the iOS LTR Hebrew marker period order and the CJK trailing gap in `counter-suffix` rows 1-8 (they cap that cell near 0.99; a
    lists-runtime brief);
  - web `hyphens-out-of-flow-002` f 0.9411 (a web-harness runs issue);
  - threading `hyphenate-character` (4(f));
  - the natives' root-`contents` self-strip (css-display-3 §2.7);
  - `li-value-reversed-*` `counters()` prefixes (out of corpus);
  - the 22 wrong-picture passes among `s-11-1-1b-001…009` (display-table-body §10 → book as DEGENERATE in 0(b), wave-54 brief
    "table overflow clip / caption").
- **Obligations 0(d)/0(e)** (the `requires-script-mutation` tags; seeding `all-then-color.json`'s baselines). These are orchestrator
  instrument and baseline chores, not lanes. 0(d) is an instrument change and must NOT share a gate with this wave's render lanes:
  the empty lost list depends on that.
- **Orchestrator doc hand-offs**, written into the lane notes and pasted at ship:
  - harness-hygiene's two Known-broken corrections: 9 of the 13 failures are 257 and 4 are stale assertions; and Gradle daemons run
    in `~/.gradle/daemon/<ver>`, so ownership comes from the daemon log, not the cwd;
  - 2(c⁴) re-trued after L1;
  - 0(l″) after L3-B and L4;
  - the web re-encode finding, in Operational recipes.

## 6. Pre-registered closing-gate expectations (in the form `adjudicate.mjs` checks)

Written BEFORE any lane lands. The orchestrator copies `tools/titan/results/wave52-gate/adjudicate.mjs` and `control-check.mjs` to
`tools/titan/results/wave53-gate/`, and generalises them to read `tools/titan/results/wave53-plan/expectations.json`. Changing an
adjudication tool is a new check, and it must be **proven able to fail** first:
- the new control must report leaks on `wave52-calib → wave52-final` (747 under the old rule), and 0 on `wave52-preview → wave52-ship`;
- the wire control must report changes on `wave51-fix → wave52-ship`, and 0 on `wave52-final → wave52-ship`;
- **a third calibration that exercises the decode branch** (plan-skeptic round 2, should-fix 3). The first two cannot catch a
  control with the decode step deleted: `wave52-calib → wave52-final` holds real pixel changes, and `wave52-preview →
  wave52-ship` changes 0 bytes. So the new control also runs on `wave52-ship → wave53-open`, restricted to CSS2, css-lists,
  css-counter-styles, css-display and css-contain. That is the cross-host pair; measured at planning, it has 720 composed
  captures, 236 of them byte-different, all web. The control must report:
  - web: re-encoded + changed = 236, with "re-encoded" non-zero (about 235). "Changed" holds `css-counter-styles/counter-suffix` (129 px, max
    channel delta 27) and every other decoded-pixel difference, each listed in the gate note as a cross-host re-rasterization.
  - iOS and Android: 0 changed and 0 re-encoded (0 of 240 byte-different on each).
  - Two mutations must turn this calibration red. Each is executed and logged red → restore → green (sha256):
    - "byte rule only, no decode": 236 changed, 0 re-encoded;
    - "decode without a pixel compare": every byte difference is called re-encoded, so counter-suffix web is missed.
  - Machine-readable form: `expectations.json` `controlCalibrations`.

**Score of record:**
`node tools/titan/score-gate.mjs wave53-open wave53-final --watch tools/titan/results/wave53-plan/watchlist.txt --movers 0.005 --json tools/titan/results/wave53-gate/score-final.json`.

| rule | expectation | on failure |
|---|---|---|
| **R1 lost** | **lost = ∅.** No instrument change this wave, so lost ⊆ ∅ against `wave53-open`. | **Any lost cell stops the ship** until its cause is found in the picture AND the wire document. It is never ledgered and never excused by a re-run. A lane that caused it is reverted or fixed, then its probe sections are re-run. |
| **R2 denominator** | unmeasured-now = 0 and newly measured = 0. No `scoreExcluded` stamp, test list or ref changes this wave. | A departure is an unregistered instrument change. Find it before reading any gain. |
| **R3 completeness** | missing sections 0, short columns 0. Every section 48/48/48 (css-cascade 43/43/43). | exit-7 recipe; never `SKIP_<P>`. |
| **R4 capture control** | Run `control-check` (wave-53 version) `wave53-open wave53-final` with the union carrier set of `expectations.json`: web 6, iOS 14, Android 18 captures. Every composed capture outside its lane's set is identical. Compare **decoded pixels**: a byte-different but pixel-identical PNG is reported as "re-encoded", never as a leak, because of the planning-time measurement in the header. The per-lane count of changed carriers is printed. | A leak is a finding: bisect by commit (§6 revert rules 5 and 6). |
| **R4b wire control** | Every `per-test-ir/*.json` byte-identical to `wave53-open` except L1's five wire carriers. Named explicitly: the three zero-padding bidi-bake documents `selectors/dir-style-02a`, `dir-selector-change-003`, `-004` are identical (hunk P's non-zero guard). | A changed document outside the five means an extraction leak. Stop. |
| **R5 gains** | Every gained cell is one of the 14 predicted flips (§1, `expectations.json`). Every gained cell is PNG-checked against the frozen ref before it is written as a pass. A flip on `hyphenate-character-001/-003/-004` (any platform) is counted and labelled DEGENERATE by construction. | A gain outside the list is looked at and explained in the PR. It is never claimed as a fix unseen. |
| **R6 picture-correctness** | Every target with a picture claim (the 16 P→P targets and the flips) prints its verdict from the committed run-id probes (`expectations.json` `lanes.*.geometryProbe`, plan-skeptic must-fix 2): `lists-bakes.geometry.py wave53-final wave53-open` (L1: six rows, nested indent and marker widths; counter-suffix RTL rows with marker ink x129-150, no left-side ink, rows 0-207 identical), `soft-hyphen.geometry.py wave53-final` (L2: box heights = ref, `high‐`/`way` ink), `canvas-root.geometry.py wave53-final` (L3-A: green frame + chrome; ICB painted, frame white, band phase), `display-table-body.geometry.py wave53-final 006` (L3-B: `square rows 56-75 (20) x 24-43 \| red px 0` ×3), `float-avoid.geometry.py wave53-final` (L4: orange, outline and float silhouette ±1 px). The expected string is `→ GEOMETRY OK` (or the 006 string) on every row of `geometryProbe.expect`. | A target whose line is not its expected string stays P with the old picture: it is DEGENERATE, as before. Say so; do not count it. |
| **R7 must-not-move** | Every must-not-move line of `watchlist.txt` prints `prev == cur`. This is implied by R4 for every capture outside the carrier sets, and stated per cell for the read-out. | A move is an R4 leak or a carrier mislabel. Diagnose it. |
| **R8 fixture net** | `BASELINE=1 ./test-all.sh --gate-set` exits 0 on all 9 gate fixtures. It re-runs `xcodegen generate` from L5's edited `project.yml`. | exit-code recipes (wave skill Phase 5). |

**Expected totals** if `wave53-open` = `wave52-ship`:
- HIGH / MED-HIGH tier: web 1230/1372 · iOS 1122/1362 · Android 1113/1362, i.e. gained web 1 · iOS 3 · Android 2.
- Ceiling with MED and MED-LOW: 1232 / 1125 / 1116.
- Lost 0; unmeasured-now 0; newly measured 0.

**The ring-fenced test** is reported plainly with its number. No code names it.

**Probe-then-ship.** These run on the integrated tree after the sweep and before the closing gate:
- `tools/titan/gate-driver.sh wave53-probe --sections CSS2,css-anchor-position,css-backgrounds,css-cascade,css-color,css-contain,css-counter-styles,css-display,css-flexbox,css-grid,css-images,css-lists,css-masking,css-position,css-pseudo,css-tables,css-text,css-writing-modes,filter-effects,selectors --skip-fixture-net`
  (`expectations.json` `probeRun`). That is 20 sections, about 50 min on a quiet host (wave53-open ran ≈ 2.2 min/section). It
  was 9 sections; the plan-skeptic showed the 9 had been narrowed from the briefs' own 20 without a reason (should-fix 3).
- Then `ab-diff.mjs wave53-probe wave53-open --threshold 0.002`, and the R4/R4b controls restricted to those sections.
- Then every lane's geometry probe on `wave53-probe` (`expectations.json` `lanes.*.geometryProbe.cmd`): `lists-bakes.geometry.py
  wave53-probe wave53-open`, `soft-hyphen.geometry.py wave53-probe`, `canvas-root.geometry.py wave53-probe`,
  `display-table-body.geometry.py wave53-probe 006`, `float-avoid.geometry.py wave53-probe`. Each prints one line per (test,
  platform) ending in its exact verdict; each exits 1 if its own ref row fails, which would mean the rule, not the lane, is wrong.
  (`display-table-body.geometry.py` had no such self-check until plan-skeptic round 2, nit 6.)
- Not probed, with the check that replaces each. This list was corrected at plan-skeptic round 2 (nit 10): two of the "other
  eight" sections hold a lane's named control.
  - css-overflow and css-values: L2's controls `block-ellipsis-014`/`-028` (ios + android) and `ch-unit-001` android. Replaced by
    S-L2's JVM census over all 24 space-less strings and all 21 abspos hosts.
  - css-break: L4's float control `block-in-inline-011` android (P 0.976; the section's only `Float` document). Replaced by
    S-L4's JVM and Catalyst `FloatAvoidPlan` census over all 1435 payloads (its containers must come back null), then the closing
    gate's R4/R7.
  - css-gaps: L5's XCTest-pinned `flex-gap-decorations-027` ios (P 0.9997). Replaced by L5's empty carrier set (it edits no
    capture code, §2 L5) and `wave53-hh-probe`'s control, then the closing gate's R4/R7. css-gaps also holds the not-staffed watch
    lines `flex-gap-decorations-033` ios/android, read at the closing gate.
  - css-view-transitions: only the not-staffed watch line `column-span-during-transition-doesnt-skip` ×3, read at the closing
    gate.
  - css-multicol, css-sizing, css-text-decor, css-transforms, css-ui: no carrier, no named control, no watch line.
  - All of them: the R4b wire control over ALL 30 sections at the L1 integration step, and the closing gate's R4.

The lanes' probe sections, the union of which is the run above:

Every row also requires revert rules 1, 2 and 5 for every carrier cell of the lane: no P → f, no target or mover down by
≥ 0.002, and no leak. The last column is never a trigger on its own (plan-skeptic round 2, must-fix 1 (iv)).

| lane | probe sections | required at the probe (a miss reverts the commit, §6 rules 3-7) | read and labelled (MED and below: no floor, no geometry trigger) |
|---|---|---|---|
| L1 | css-lists, css-counter-styles, css-text, css-writing-modes, selectors, css-anchor-position, css-backgrounds, css-pseudo, css-display | **U1:** `counter-reset-reversed-nested` web ≥ 0.995 and ios ≥ 0.99, both `lists-bakes.geometry.py` → `GEOMETRY OK`; wire differential = {that document}. **U2:** `counter-suffix` web ≥ 0.95 with `GEOMETRY OK`, ios ≥ 0.95, android ≥ 0.975 with `GEOMETRY OK`; counter-suffix rows 0-207 identical to wave53-open; web/ios captures of `bidi-lines-001/-002` and `anchor-center-safe-rtl` identical (else the P-narrow re-land, rule 7); the three zero-padding selectors documents identical in wire and capture; wire differential = 5 documents | `counter-reset-reversed-nested` android magnitude and geometry (MED; rule 1 keeps it P from 0.9509); `counter-suffix` ios geometry; `bidi-lines-002` android rise and `bidi-lines-001` android f→P (MED / MED-LOW) |
| L2 | css-text, CSS2, css-pseudo, css-tables, css-position | **F1:** `hyphens-span-001` android ≥ 0.99 and ios ≥ 0.98. **F1+F2:** `hyphens-out-of-flow-001` android ≥ 0.985. **F2:** `-002` android ≥ 0.985. `soft-hyphen.geometry.py` → `GEOMETRY OK` on those four rows; `hyphens-auto-control` android, `hyphens-vertical-001` ios and the F2-bail controls identical | `hyphens-out-of-flow-001` ios flip and geometry (MED); the `hyphenate-character-00x` movers (a flip is DEGENERATE by construction) |
| L3 | css-display, css-backgrounds, CSS2, css-contain, css-cascade, css-color, css-masking, filter-effects, css-images, css-tables, css-position | **A:** `display-contents-root-background` ×3 ≥ 0.99 with `canvas-root.geometry.py` → `GEOMETRY OK`; every colour-only / contained / clipped root control identical. **B-web:** `s-11-1-1b-006` web ≥ 0.95 and the 006 string. **B-ios / B-android:** the 006 string on that native, **else that native's commit is reverted** (probe-gated) | `background-attachment-margin-root-001/-002` ×3 flips and geometry (MED / MED-LOW); the 006 native scores (rule 1 keeps them P from 0.9953 / 0.9944) |
| L4 | css-contain, css-display, CSS2, css-grid | **android / ios:** `contain-inline-size-bfc-floats-001/-002` ≥ 0.99 on that native, `float-avoid.geometry.py` → `GEOMETRY OK` on those rows; web identical; the 143 passing float-control cells identical (CSS2 26 tests, css-grid 32) | `display-flow-root-002` ios/android magnitude and geometry (MED; rule 1 keeps both P from 0.9734) |
| L5 | css-cascade, css-counter-styles, css-flexbox, css-text (its own `wave53-hh-probe`, §4 step 2) | 0 changed captures by decoded pixels on all three platforms; `driver.log` clean | web run-to-run determinism: any web decoded-pixel difference restates R4/R7 web in the §10 addendum before any further lane lands |

**Revert rule (numeric; plan-skeptic must-fix 2, rewritten at the second review's must-fix 1; `expectations.json`
`revertRule`).** Read `wave53-open` → `wave53-probe` (same host). The unit of revert is the **commit** (rule 6). A commit is
reverted out of the integrated tree when ANY of rules 1-5 holds for a cell or capture it carries:
1. **Lost at the probe.** A carrier cell is P on `wave53-open` and f on `wave53-probe`, whatever its confidence tier and whether or
   not it has a prediction. This is R1's empty lost list applied at the probe. Before this rule, nothing fired if one of the six
   carrier cells that pass today with no floor went P → f (`cells.mjs … wave53-open`):
   - `counter-reset-reversed-nested` android P 0.9509, 0.0009 above the pass mark;
   - `bidi-lines-002` android P 0.9534;
   - `display-flow-root-002` ios / android P 0.9734;
   - `s-11-1-1b-006` ios P 0.9953 / android P 0.9944.
2. **Moved down.** Δ ≤ −0.002 (the `ab-diff.mjs --threshold 0.002` threshold), `wave53-open` → `wave53-probe`, on any target or
   mover: every row of the lane's `predictions`, whatever its tier. No prediction in this plan forecasts a fall. The measured
   cross-host noise is ±0.0001 on web and 0 on the natives (header), 20× below the threshold.
3. **Below floor.** A `gating: true` prediction (every HIGH or MED-HIGH one; 17 cells) scores below its `floor`.
4. **Wrong geometry.** A `geometryGating` cell's probe line does not end in its exact `expect` string (`GEOMETRY OK`, or the 006
   string). `s-11-1-1b-006` ios and android are the probe-gated rows: a native that does not print the 006 string has its B
   commit reverted.
5. **Leak.** A composed capture outside every carrier set differs in DECODED pixels (R4), or a per-test IR document outside the
   wire carriers differs in bytes (R4b). The leak is bisected to a commit, one re-probe of the leaked section per step; it is
   never guessed. Exception: rule 7.
6. **The unit is the commit.** `expectations.json` `lanes.*.revertUnits` lists them; their captures cover each lane's carrier set
   exactly, and `plan-build.py` asserts it.
   - L1: **U1** / **U2**.
   - L2: **F1** (F1 + F1-iOS + seam-1) / **F2** (F2 + seam-2).
   - L3: **A** / **B-web** / **B-ios** / **B-android**.
   - L4: **android** / **ios**, one commit per native.
   - L5: one commit.

   A cell carried by two commits of one lane (`hyphens-out-of-flow-001` android: F1 + F2) reverts the later commit first. The
   earlier one goes only if the re-probe of its sections still fails. Reverting U2 never takes U1 with it, and the same holds
   for every other pair.
7. **P-narrow, not a revert.** A web or iOS change on `bidi-lines-001`, `bidi-lines-002` or `anchor-center-safe-rtl`, or a rule-1
   or rule-2 trigger on `bidi-lines-001` / `-002` android, re-lands U2 as U2-narrow (§2 L1 (P);
   `lanes.L1-lists-bakes.pNarrowFallback`). Hunk P alone reaches those three documents: they host no marker, so hunk M never
   touches them. U2 is reverted only if U2-narrow still leaks or still trips a rule on counter-suffix.

**Tier.** Confidence decides only whether a cell has a floor and a geometry trigger. HIGH and MED-HIGH cells must reach their
floor (rule 3). MED and lower have neither: a MED cell that rises less than predicted, or not at all, is read and labelled, never
a revert trigger. Rules 1, 2 and 5 apply to every tier, so "read and labelled" never covers a P → f or a fall.

A score that does not move is a falsification wherever the floor sits above today's value. Three floors sit at the 0.95 pass
mark:
- `counter-suffix` web and `s-11-1-1b-006` web are carried by their gating geometry line instead.
- `counter-suffix` ios is HIGH only for staying P. Its picture is labelled from its (report-only) geometry line and never counted
  unseen.

A reverted commit's sections are re-probed before the closing gate. **The closing gate runs only on a tree whose probe holds.**

## 7. The watchlist — `tools/titan/results/wave53-plan/watchlist.txt`

405 watch lines covering 436 distinct cells, generated by `plan-build.py` from the censuses and the briefs' named cells. Each lane
has a block of targets and movers, and a block of must-not-move cells. The ring-fenced line is report-only. A final block holds the
not-staffed 0(c) cells (`column-span-during-transition-doesnt-skip` ×3, `flex-gap-decorations-033` ios/android), so the closing
gate reads them out.

Every line carries the `.html` suffix, so it matches exactly one test under `score-gate.mjs` `watchCells`' substring rule. The one
deliberate family line is `css-text/hyphens/ web`. The ring-fenced report-only line was the second exception until the
plan-skeptic review; it now reads `filter-effects/backdrop-filter-basic-blur.html` and still matches exactly its 3 cells
(`cells.mjs 'backdrop-filter-basic-blur.html' wave52-ship` → web P 1, ios P 0.9526, android f 0.904).

Verified during planning: `RUN=wave52-ship WATCH=tools/titan/results/wave53-plan/watchlist.txt node tools/titan/results/wave52-plan/watchlist-check.mjs`
→ `watch-lines=405 matched-cells(distinct)=436 unmatched 0`. Precondition before any gate read-out: re-run it with `RUN=wave53-open`
→ `unmatched 0`.

`plan-build.py` also asserts two things:
- no capture is an allowed carrier of two lanes;
- no lane's must-not-move cell is an allowed carrier of any lane.

## 8. Risks, and the order of operations after the opening gate

**Risks**
1. **The host changed under the record: macOS 27.0.1 / Xcode 27.0.**
   - Measured during planning: web PNGs are byte-different against `wave52-ship` (236 of 240 web captures in five sections). Most
     are pixel-identical (9 of 9 sampled), but at least 21 web captures were re-rasterized (header). iOS and Android are
     byte-identical so far, and none of their scores moved.
   - Byte controls across that boundary are meaningless on web, so R4 compares decoded pixels, and every comparison this wave is
     `wave53-open` → a run on the same host.
   - **No OS, Xcode, simulator-runtime, emulator-image or Chrome-for-Testing update may happen between `wave53-open` and
     `wave53-final`.** Record `sw_vers`, `xcodebuild -version` and the CfT version in both gates' notes.
   - Whether web capture is deterministic run to run on this host, for the encoder AND the rasterizer, is unknown until
     `wave53-hh-probe` (4 sections, §4 step 2) is compared with `wave53-open` by decoded pixels. R4's decode absorbs an
     encoder-only difference. It does NOT absorb a rasterizer difference, which would show as false R4 leaks, false R7 moves and
     a failed counter-suffix crop. Any such difference restates R4/R7 web in the §10 addendum before any further lane lands.
2. **Code first executed on device.**
   - L4's Compose `FloatAvoidLayout` (JVM cannot run a `Layout`).
   - L3-B's synthetic native table: never executed on this shape, and the anonymous cell around an empty block may take the
     block-fill width.
   - L1's CDP `::marker` box and AX name (unverified; the analytic fallback is backed by ref ink at x133 against the edge at x128).
   - Mitigation: probe-then-ship. L3-B's native half is the one pre-declared revertible item.
3. **Partial landings that are pre-registered losses or wrong pictures.**
   - L1 hunk M without P loses `counter-suffix` android (f 0.9466 replay).
   - L1 Unit 1 without C+D leaves the natives with a different wrong picture.
   - Both units land whole (§4 step 3).
   - L1 hunk P's web/iOS invariance on the three marker-less documents is argued, not captured. The pre-registered response is the
     P-narrow re-land (§2 L1 (P), §6 rule 7), never a revert of Unit 1.
4. **Hot shared files.**
   - The three composed canvases (L3) are read by the ios-harness source pins (L5).
   - The two native ComponentRenderers each get an L2 and an L4 hunk.
   - Mitigation: §3/§4 order; `ios-source-pins.mjs` 7/7 in L3's lane and the XCTest 30/30 on the integrated L5 + L3 tree;
     `git apply --check` plus focused suites after each patch.
5. **Degenerate-by-construction flips** (`hyphenate-character-00x`, the U+2010 bake). They are pre-labelled in R5 and in
   `expectations.json`.
6. **L5's Gradle mutation kills every daemon on the host.** It runs only in an exclusive window. The adb restore mutation is never
   executed.
7. **Blast-radius under-reporting** (the dangerous direction; wave 50). Every lane's census is re-derived by a skeptic with its own
   script (step 5).

**Order of operations**
1. **`wave53-open` finishes.** Score it:
   `node tools/titan/score-gate.mjs wave52-ship wave53-open --watch tools/titan/results/wave52-plan/watchlist.txt` (obligation 0).
   - Expected: 0 gained / 0 lost / 0 movers / 0 newly measured / 0 unmeasured-now, and fixture net exit 0 ×9.
   - Run the capture-hash census of all 4305 composed captures plus the per-test IR against `wave52-ship`, classified by decoded
     pixels. Record the re-encode finding.
   - **Cells did move: 20 web cells by ±0.0001 already, 4 of them watched (header). So the §10 pre-registration addendum is wave
     53's first item.** Append "§10 pre-registration addendum" to this file BEFORE any lane lands: every watched cell's
     `wave53-open` value, and every prediction restated wherever `wave53-open` differs from `wave52-ship`.
   - Run `watchlist-check.mjs` with `RUN=wave53-open` → `unmatched 0`.
2. Commit `tools/titan/results/wave53-plan/` (§4 step 1).
3. **Builder lanes** L1–L5: one Workflow, model opus, file-driven (`STATUS:` line), private worktrees off cdb8a845, focused suites
   only. The orchestrator grants the host windows: L1's CDP probe and gate-flag re-extraction; L5's simulator, smoke and exclusive
   Gradle windows. Every pin's mutation is executed and logged in the lane dir.
4. **Plan-level check, before skeptics.** Every `<run> <section>/<test> <platform> <P|f> <ssim>` citation in this file is
   re-resolved against the manifests (the plan-skeptic-1 C8 lesson).
5. **Skeptics** (executed repros, wave skill Phase 3).
   - **S1 combined-tree:** hunk audit against §3, the full suite sweep with counts, git hygiene, pointer audit.
   - **S-L1:** its own gate-flag wire differential (exactly 5 documents); `3 2 11 9 8 1`; the 13 bidi-baked documents identical;
     the M-without-P replay.
   - **S-L2:** its own U+00AD and inert-member census, compared with the committed `spaceless-soft-hyphen.census.py` (wave52-ship:
     1435 docs, 17 shy docs, 24 space-less strings = 15 leaf `text` + 9 `meta.runs[i].text` pieces, host `text` mirrors excluded
     (6, which would make 30), 21 abspos members in runs hosts: 12 inert, 9 bail); a JVM run of `PreBreakPipeline` over all 24
     strings, and of `InlineRunFold` over all 21 abspos hosts (12 fold, 9 bail).
   - **S-L3:** its own body-root census (3 + 1 carriers out of 284); the ios-source-pins replay; containment and colour-only
     deep-equality.
   - **S-L4:** the JVM census of `FloatAvoidPlan` over every container of the 1435 documents → exactly 3 non-null, AND the same
     census of the Swift `FloatAvoidPlan` under Catalyst (one XCTest over the same 1435 payloads) → the same 3 containers, no
     crash (plan-skeptic should-fix 3: the Swift planner runs in every capture-mode block loop, and the JVM census does not run it).
   - **S-L5:** hermetic own-processes pins re-run; pbxproj greps; the smoke foreign-listener check.
6. **Fix lanes** for `mustFixBeforeGate` items; touched suites re-run.
7. **Integration** in §4 order: L5 (then `wave53-hh-probe`), L1, L2, L4, L3. Each seam patch gets `git apply --check`, and the focused
   suites are re-run after each.
   - Right after `wave53-hh-probe`, and before L1 lands: compare its captures with `wave53-open` over all four sections. iOS and
     Android must be byte-identical. The 187 web captures (css-cascade 43 + 3 × 48) are compared by DECODED pixels. Any web difference means the §10
     addendum restates R4/R7 web, and L1's counter-suffix crop rule, BEFORE L1 lands (plan-skeptic round 2, should-fix 4).
   - **7a. Before EVERY device run from here on** (`wave53-hh-probe`, `wave53-probe`, `wave53-final`, the A/B). Added at
     plan-skeptic should-fix 7, and made ONE call at the second review's should-fix 2.
     - Why it is needed: once L5 lands, `gate-driver.sh` no longer stops daemons that served the private lane worktrees. Those
       worktrees are siblings of `PROJECT_ROOT` (`…/.claude/worktrees/<name>`), not children. By T2's own predicate their daemons
       are foreign, and they count as load in the quiet-host check.
     - The call: the orchestrator stops them through their owner, in ONE multi-root call,
       `source tools/titan/own-processes.sh && kill_own_gradle_daemons "$PROJECT_ROOT" <tree> …`. Here `<tree> …` is every builder,
       skeptic and fix-lane worktree and every export tree that ran Gradle this wave, read from the `TREES:` line of each lane
       note (§0).
     - Why one call, with `PROJECT_ROOT` in it: a daemon is ours only if EVERY `currentDir=` line of its log is inside the given
       roots. Gradle reuses a compatible idle daemon across checkouts. A daemon that served the integration tree AND a lane tree
       is therefore foreign both to gate-driver's own call (default `PROJECT_ROOT` alone) and to a lanes-only call, and it
       survives both.
     - This is measured, not hypothetical. 8 of the 210 `~/.gradle/daemon/9.6.1/daemon-*.out.log` on this host are multi-root,
       and every one of them pairs `PROJECT_ROOT` with wave-52 lane, skeptic, fix or export trees. Example: `daemon-40966` served
       `PROJECT_ROOT/apps/android-harness` plus `l4/x`, `l8fix/export`, `l8sk/export` and `rv/x`. All 8 are dead now.
     - It does NOT run `./gradlew --stop` from any tree. `--stop` stops every daemon of that Gradle version for the user, whatever
       checkout it served (harness-hygiene §T2, measured), which is the very host-wide kill L5 retires.
     - Afterwards it records `./gradlew --status` (read-only) and the helper's stopped / left lines in the gate note. A daemon that
       also served a tree outside the list, such as another session's checkout, is left, named, and closed by its owner, never
       by a host-wide stop.
8. **Sweep:** §4 step 7. Single-writer, sequential, including the harness suites and the ios-harness XCTest.
9. **Probe:** `wave53-probe`, 20 sections (§6, `expectations.json` `probeRun`), after step 7a. Read it per lane against the
   floors and the geometry probes. Revert any commit that trips §6 rules 1-5: the unit is the commit (rule 6), and a U2 web/iOS
   leak re-lands U2-narrow instead (rule 7). Decide L3-B's natives (B-ios / B-android).
10. **Closing gate:** after step 7a, `tools/titan/gate-driver.sh wave53-final` on a quiet host, in two launches (`--skip-fixture-net`, then
    `--skip-corpus`), `run_in_background` with an explicit timeout. Wait for the task notification; no polling.
    - Record the build hashes.
    - Score against `wave53-open` and adjudicate R1–R8.
    - Run the cell review: every flipped cell and every P→P target looked at against the ref, with a second, adversarial reader for
      every DEGENERATE or HONEST_FAIL verdict.
11. **A/B** (after step 7a): one exclude arm. **L2 drop-F2** (Compose: `InlineRunFold`'s F2 arm plus seam-2 reversed), css-text, Android only,
    with the installed `base.apk` sha1 recorded against the build.
    - It attributes `hyphens-out-of-flow-001` android between F1 and F2 (predicted ≈ 0.975 without F2) and confirms
      `hyphens-out-of-flow-002` is F2's alone.
    - The brief's drop-F1 and drop-seam-1 arms are not run: `hyphens-span-001` carries F1 only, and iOS carries seam-1 only, so
      those cells already attribute (§9 D6).
    - Any lane that claims a further mechanism at the gate gets its own arm under the same hash rule.
12. **Ship** per wave skill Phase 6. The corpus snapshot gets `artifact`/`reproduce --run-id` = `wave53-final`; BACKLOG gets the §5
    hand-offs.

## 9. Decisions taken against (or beyond) a brief

| # | brief | the brief said | the plan does | why |
|---|---|---|---|---|
| D1 | display-table-body | GO-SMALL only folded into T10; "If T10 is not staffed, it is NO-GO" | staffed as **L3 item B**, after item A, with its native call sites probe-gated and revertible (web can ship alone) | T10 has no wave-53 brief. B's call sites are in exactly the three canvas files L3 already owns, so the marginal cost is low. Obligation 0(b) names `s-11-1-1b-006` as first-lane material. The probe protects the empty lost list. |
| D2 | spaceless-soft-hyphen | seam-2 (Compose breadcrumb) OPTIONAL | REQUIRED whenever F2 ships, with a source-scan pin | F2 removes a component from the mounted tree. An unlogged removal is a silent fallthrough. |
| D3 | spaceless-soft-hyphen | `hyphens-auto-control` android and `hyphens-vertical-001` ios in the allowed carrier set (reached, expected identical / "maybe") | both OUT of the allowed set, so they are must-not-move; vertical-rl gated off the U+00AD clause if reachable | a control that allows a cell predicted identical cannot catch it moving |
| D4 | rtl-marker-bake | `ListMarkerOutsideHang.swift:33-41` comment re-true handed to the orchestrator | L1 owns it, comment only, in Unit 2's commit | the comment becomes false in that commit; lanes own code, the orchestrator owns docs |
| D5 | contents-root-background | file table omits `ColorApplier.kt` / `BackgroundImageApplier.swift` | both owned by L3 for one defaulted parameter or public wrapper each | §5 F1 of the same brief needs them; ownership must be explicit to stay disjoint |
| D6 | spaceless-soft-hyphen | device A/B arms drop-F1, drop-F2, drop-seam-1 | drop-F2 only | the carriers are mechanism-pure except `hyphens-out-of-flow-001` android |
| D7 | nested-list-extractor | "fold into a lists/counters (or pseudo-text) lane" | folded into L1 with rtl-marker-bake as Unit 1 | both are bake-side list work; disjoint files; one lane note |
| D8 | (all) | byte-identity controls (wave 52's `control-check.mjs`) | R4 decodes pixels; the wire control R4b is new | planning-time measurement: web captures are re-encoded under macOS 27 |

## Skeptic corrections

The plan skeptic filed 14 problems (2 must-fix, 5 should-fix, 7 nits). The planner re-ran each check before acting on it. Every
re-run reproduced the skeptic's evidence, and all 14 are applied. One fix is applied in a different form from the one proposed
(#7, with the reason). Re-run evidence is light: JSON census, `cells.mjs`, and about 110 decodes of 390×600 PNGs under `nice -n 19` (the probes'
calibration and self-test runs). It was
gathered while `wave53-open` was running; nothing was built or run against the device, and nothing under `tools/titan/runs/` was
written.

| # | sev | problem (re-run evidence) | action |
|---|---|---|---|
| 1 | must-fix | Hunk P ("delete every `padding*`, emit `padding: 0`") and the Unit-2 carrier rule ("a bidi-bake root with a padding key") reach 7 documents, not 4. **Re-run** over the 17 `_wpt.bidiBaked` fixtures (roots by the `baked-bidi-visual-order` stamp): any padding key on 14 roots in 7 docs; non-zero on 6 roots in 4 docs. `dir-style-02a` ×6 and `dir-selector-change-003`/`-004` carry `padding-*: 0px` at positions 9-14 with `overflow-x/-y: visible`. `bidi-bake.mjs:957` is `Object.assign`. | Hunk P fires only on a root whose resolved padding is NON-ZERO; zero-padding roots keep their keys in place and in order (§2 L1 Change (P)). The census is committed as `rtl-marker-bake.padding-census.py` and prints exactly 17 / 14 roots in 7 docs / 6 roots in 4 docs. The Unit-2 carrier rule reads "NON-ZERO resolved padding (6 roots in 4 documents)". New pin **V3b**: the verbatim `dir-style-02a` root comes out deep-equal with its key order unchanged; mutation: drop the non-zero guard. §4 step 3, R4b and `expectations.json` `lanes.L1-lists-bakes.carrierRule` name the three documents that must stay byte-identical. `selectors` (plus the rest of the briefs' list, #3) is added to L1's probe sections. |
| 2 | must-fix | §6 is not executable for most P→P targets. Only `display-table-body.geometry.py` takes a run id; `contents-root-background.geometry.mjs:17` hard-codes `wave52-ship` and is a replay. HIGH predictions written as "≈" have no threshold. **Re-run** confirmed both. | **Four new run-id probes** are committed beside the plan: `lists-bakes.geometry.py`, `soft-hyphen.geometry.py`, `canvas-root.geometry.py` and `float-avoid.geometry.py`, with shared helpers in `geometry_common.py`. Each prints one line per (test, platform) ending in `→ GEOMETRY OK` or `→ GEOMETRY WRONG (<why>)`, and exits 1 if its own ref row fails. **Executed on wave52-ship:** every ref row prints OK. Every capture row of a broken picture prints WRONG: L1 6/6 targets; L2 the 5 targets; L3-A 9/9; L4 6/6 natives. The rows already right today print OK: web on the three L4 tests, web on L2's `hyphens-span-001` / `-out-of-flow-001`, and `hyphens-out-of-flow-002` ios. So the rules can fail and can pass. **`expectations.json`**: every HIGH / MED-HIGH prediction has `gating: true` and a numeric `floor` (17 cells; `plan-build.py` asserts it). Every lane has a `geometryProbe` {cmd, expect, geometryGating, onWave52Ship}. There is a top-level `revertRule`. §2's four prediction tables gain a "gate floor · geometry" column. §6 R6, the probe table and the revert rule cite those outputs. |
| 3 | should-fix | The probe was narrowed from the briefs' 20 sections to 9 without a reason. Consequences: `anchor-center-safe-rtl` (wire carrier, unscored) was not probed; the #1 selectors docs were not probed; css-grid (32 float tests) was absent, and S-L4 is JVM-only. **Re-run**: 32 css-grid docs carry `"Float"`; `cells.mjs 'anchor-center'` lists no safe-rtl cell. | `wave53-probe` is now the 20-section union, ~50 min (`expectations.json` `probeRun`). Per lane: L1 adds css-writing-modes, selectors, css-anchor-position, css-backgrounds, css-pseudo, css-display. L2 adds CSS2, css-pseudo, css-tables, css-position. L3 adds css-contain, css-cascade, css-color, css-masking, filter-effects, css-images, css-tables, css-position. L4 adds CSS2 and css-grid. Each section left out is named with the check that replaces it (§6). S-L4 gains a Catalyst census of the Swift `FloatAvoidPlan` over all 1435 payloads. |
| 4 | should-fix | L2's census counts had no committed method; "1494" is an `ls \| wc -l` artifact; 24 holds only when host `text` mirrors are excluded. **Re-run**: `ls …/per-test-ir/ \| wc -l` → 1494 and `ls …/*.json \| wc -l` → 1435. My replay gives 17 / 24 (15 leaf + 9 run pieces) / 6 mirrors (→ 30) / 21 / 12 / 9. | Committed `spaceless-soft-hyphen.census.py`, which prints exactly those counts on wave52-ship. The brief reads 1435 in both places. It states that 24 = leaf `text` + `meta.runs[i].text` pieces, excluding the 6 host mirrors. The mirrors all read `high­way`: more occurrences, no new string shape. §8 S-L2 cites the script and its numbers. |
| 5 | should-fix | "Every pin named in §2 has its mutation" was false for L2 (d), L3 B-shape, L4 P6 and P7. | Mutations added. **L2 (d)**: `WordBreakOpportunities:87` treats U+00AD as an ordinary character, in a private worktree copy only (sha256-restored); `WordBreakOpportunities.kt` is listed read-only for L2. **L3 B-shape**: the synthetic table carries the body-root instead of a fresh TABLE root. **L4 M7**: report max(float bottom, BFC bottom), which turns P6 red on 002 (300 for 20); checked by hand that 001 (370) and flow-root-002 (300) do not move. **L4 M8**: width from the 358 proposal, which turns P7 red (x158 for x200, x108 for x150). The §0 sentence now says when it became true. |
| 6 | should-fix | The seam-2 breadcrumb pin had no owned file. **Re-run**: `SeamReachabilityTest.kt` exists and is in no lane's own list; L4 only runs it. | New L2-owned `…/core/renderer/RunFoldBreadcrumbSeamTest.kt` (does not exist today), listed under L2 "own:". `SeamReachabilityTest.kt` is edited by no lane. |
| 7 | should-fix | After L5's T2, `gate-driver.sh` no longer stops the lane worktrees' Gradle daemons. **Re-run**: `git worktree list` shows the `wf_c3a87fe8-936-*` worktrees as siblings of `PROJECT_ROOT`. `gate-driver.sh:138-139` is today's `--stop`. The quiet-host check counts what survives. | **Applied in a different form.** The proposed `./gradlew --stop` from each lane worktree is refused. `--stop` stops every daemon of that Gradle version for the user, whatever checkout it served (harness-hygiene §T2, measured). That is the host-wide kill L5 exists to retire, and it would take down other sessions' daemons. Instead, new **§8 step 7a**, run before `wave53-hh-probe`, `wave53-probe`, `wave53-final` and the A/B: `kill_own_gradle_daemons <five lane worktrees>`. That helper is L5's predicate, now multi-root (§2 L5 T2, with a two-root pin). The step then records `./gradlew --status` (read-only) and the stopped / left lines in the gate note. A daemon shared with a foreign checkout is named and left. |
| 8 | nit | §0 said L5 "retires the last two violations"; T5 (`gate-driver.sh:161-165`) stays. | Reworded: "retires the two BACKLOG-named violations … T5 remains … recorded in Known-broken (§5)". |
| 9 | nit | `hyphenate-character-005` was must-not-move only in L1's JSON. **Re-run**: L2's list had 0 entries for it. | `plan-build.py` adds it ×3 to L2's `mustNotMove` (37 cells; the watchlist still writes each line once, 405 lines). §4 step 3 says it is in both lists. |
| 10 | nit | `expectations.json` `scoreOfRecord` lacked `--movers 0.005 --json …`. | `plan-build.py` writes the §6 command verbatim. |
| 11 | nit | §7 "every line carries `.html`" was false for the ring-fenced line. | The line now reads `filter-effects/backdrop-filter-basic-blur.html`. `cells.mjs` confirms it still matches exactly its 3 cells. The watchlist header names the family line as the one exception. The watchlist diff is that line plus the header comment. |
| 12 | nit | The hunk-P invariance premise "every descendant is abspos" is false for bidi-lines-002. **Re-run**: its root has 5 `br` children with `Display NONE`, Width/Height 0. | The CSS 2.1 §10.1 argument now covers them: they are hidden by `plan.hides` and generate no box. V3's fixture carries that root and asserts `display: none`. |
| 13 | nit | The scope of `marker-probe-mismatch` was unspecified. Under `{ bail }` (`bidi-bake.mjs:847-848`) it would un-bake all of counter-suffix. | It is now marker-scoped. That `<li>` takes the analytic inline-start edge and gets a `_lossyReasons` stamp. Nothing else changes, and the plan is never `{ bail }`. Pre-registered: a gate-time firing does not change §2's predictions (the replays paste the ref's own ink). V5 is re-specified with two mutations. |
| 14 | nit | `display-table-body` was labelled "GO-SMALL, staged as item B"; its operative verdict with T10 unstaffed is NO-GO. | §1 prose and lane table: "NO-GO → staffed, §9 D1". |

**A planner finding made while re-running #2.** It is not one of the skeptic's problems. The header claimed the web captures
were "re-encoded, not re-rendered" (9 decoded pairs). That claim is not universal. The L1 probe's crop compare showed
`counter-suffix` web differing between `wave52-ship` and `wave53-open` in 129 decoded text-antialiasing pixels (max channel delta
27), while its score was unchanged at 0.9818. Action: the header records it; the planned `wave52-ship` comparison is unaffected
(every control here is same-host); and the open question in the header now covers the rasterizer as well as the encoder. If
`wave53-hh-probe` → `wave53-open` shows any decoded web difference, R4's web rule is restated in a §10 addendum before any further
lane lands.

**Final shape after corrections:**
- **5 builder lanes** (L1 lists-bakes, L2 soft-hyphen, L3 canvas-root, L4 float-avoid, L5 harness-hygiene).
- 405 watch lines.
- Union capture carriers: web 6, iOS 14, Android 18; wire carriers: 5.
- 17 gating predictions with numeric floors.
- 5 committed geometry probes: 4 new and `display-table-body.geometry.py`.
- A 20-section `wave53-probe`.

## Skeptic corrections (plan-skeptic round 2)

The second plan-skeptic review filed 12 problems: 1 must-fix, 4 should-fix and 7 nits. The planner re-ran every check before
acting on it, and every re-run reproduced the skeptic's evidence. **All 12 are applied; none is refused.** Three are applied in a
wider or different form than proposed, each with its reason in the table (#1, #5, #12).

The re-runs stayed within the host rules, because `wave53-open` was still running. They used JSON/text censuses,
`cells.mjs`, sha1 over 720 PNGs, one PIL decode pair (counter-suffix web) and 9 decodes for the `display-table-body` self-check
and its mutation. Nothing was built or run against a device. Nothing under `tools/titan/runs/` was written, and no file outside
this directory was touched. `plan-build.py` was re-run: `watchlist.txt` is byte-identical (405 lines), and `expectations.json`
gains `revertUnits` ×5, `pNarrowFallback`, `hhProbe`, `controlCalibrations` and `beforeEveryDeviceRun`, with the list form of
`revertRule`.

| # | sev | problem (re-run evidence) | action |
|---|---|---|---|
| 1 | must-fix | The revert rule contradicted itself ("any target moves down" vs "MED … never a revert trigger"), had no number for "moves down", let a P→f on an unfloored carrier through to the closing gate, and did not say what unit is reverted. **Re-run**: `cells.mjs … wave52-ship wave53-open` and a revert-unit census over `expectations.json` find exactly six carrier cells P today with no floor: `counter-reset-reversed-nested` android P 0.9509, `bidi-lines-002` android P 0.9534, `display-flow-root-002` ios/android P 0.9734, `s-11-1-1b-006` ios P 0.9953 / android P 0.9944. | §6 revert rule rewritten as numbered rules 1-7 plus a "Tier" paragraph, mirrored as a list in `expectations.json` `revertRule`. (i) → rule 1: P on `wave53-open` → f on `wave53-probe` reverts the carrying commit, at any tier. (ii) → rule 2: Δ ≤ −0.002 on every `predictions` row, at any tier; no prediction forecasts a fall, and the cross-host noise is 0.0001. (iii) → rule 6: the unit is the commit (`lanes.*.revertUnits`; `plan-build.py` asserts that each lane's units cover its carriers exactly). The units are L1 U1/U2, L2 F1/F2, L3 A/B-web/B-ios/B-android, L4 android/ios, L5 one. **Wider than proposed:** L4 is split per native, because its two seams are already per-native patches, so a native that fails on device goes alone, as L3-B's do. A cell carried by two commits (`hyphens-out-of-flow-001` android) reverts the later commit first. (iv) → the §6 probe table now has a "required" column (rules 3-7) and a "read and labelled" column. `bidi-lines-001/-002` android and the margin-root pair moved to the second. §2's four "gate floor" paragraphs, §4 steps 4-6 and §8 step 9 were re-worded to match. |
| 2 | should-fix | Step 7a's lane-trees-only call, and gate-driver's `PROJECT_ROOT`-only default, both leave a daemon that served `PROJECT_ROOT` AND a lane tree. **Re-run**: a census of the `currentDir=` lines in `~/.gradle/daemon/9.6.1/daemon-*.out.log` finds 210 logs and 8 truly multi-root ones (a 9th spans only `PROJECT_ROOT` and its own `apps/android-harness`). All 8 pair `PROJECT_ROOT` with wave-52 lane, skeptic, fix or export trees; `daemon-40966` is the skeptic's example. All 8 are dead now (`kill -0`). | §8 step 7a is now ONE call: `kill_own_gradle_daemons "$PROJECT_ROOT" <every builder, skeptic, fix-lane worktree and export tree that ran Gradle>`. The step explains why `PROJECT_ROOT` must be in the same call. §0: every builder, skeptic and fix-lane note carries a `TREES:` line, which step 7a reads. §2 L5 gains the named pin "PROJECT_ROOT + one lane tree → stopped; PROJECT_ROOT alone → left and named", with a per-root-evaluation mutation. §4 step 2 is re-worded; `expectations.json` `beforeEveryDeviceRun`. |
| 3 | should-fix | Neither calibration exercises "byte-different but pixel-identical", so a control with the decode step deleted passes both. **Re-run**: sha1 over CSS2, css-lists, css-counter-styles, css-display and css-contain, `wave52-ship` vs `wave53-open` → 720 compared, 236 differ, all web (iOS 0/240, Android 0/240). The PIL decode of counter-suffix web → 129 px differ, max delta 27. | §6 gains a third calibration on `wave52-ship → wave53-open` over those five sections. Web: re-encoded + changed = 236, re-encoded non-zero (≈235), and "changed" ⊇ {counter-suffix}. Natives: 0 changed and 0 re-encoded. Two mutations must turn it red: the proposed "byte rule only, no decode", and a second, "decode without a pixel compare", which misses counter-suffix. `expectations.json` `controlCalibrations`. |
| 4 | should-fix | The plan treated the cross-host web change as one re-rasterized cell, and only `wave53-hh-probe`'s css-cascade and css-flexbox tested within-host determinism. **Re-run**: a JSON-only `loadRun` diff over the 26 sections then scored → 3533 compared, 20 web scores differ (all ±0.0001), 0 native, 0 flips. The per-section split matches the skeptic's. Four watched cells: `bidi-lines-001`, `display-contents-root-background`, `hyphens-manual-inline-011` and `hyphens-auto-001`, all web. | Header restated: at least 21 web captures re-rasterized cross-host (20 by score, plus counter-suffix at an unchanged score), 0 flips, the four watched cells named, and the §10 addendum certain. `wave53-hh-probe` now runs css-cascade, css-counter-styles, css-flexbox and css-text (`expectations.json` `hhProbe`; L5's `probeSections`). New §8 step 7 bullet: compare its 187 web captures with `wave53-open` by decoded pixels BEFORE L1 lands. §8 risk 1 now says the decode absorbs an encoder-only change but not a rasterizer change. §8 step 1 writes §10 unconditionally. |
| 5 | should-fix | Hunk P's web/iOS invariance is argued, not captured, and the brief's P-narrow fallback was not carried, so a 1-px leak would revert all of L1. **Re-run**: rtl-marker-bake.md §9 risk 3 confirms the text. `grep -n -i 'narrow\|fallback' PLAN.md` finds no P-narrow. The padding census reproduces 6 non-zero roots in 4 documents. | Pre-registered in §2 L1 (P), §6 rule 7 and `expectations.json` `lanes.L1-lists-bakes.pNarrowFallback`. On the trigger, U2 is re-landed with the lane's prepared `u2-narrow.patch` (new pin V3c, with a mutation). Afterwards the wire carriers are counter-reset-reversed-nested and counter-suffix only, the bidi-lines android movers are withdrawn, and the three documents become must-not-move. It never takes U1 down. **Wider than proposed:** a P→f or a fall of ≥ 0.002 on `bidi-lines-001/-002` android also triggers P-narrow rather than a U2 revert, because only hunk P reaches those marker-less documents. |
| 6 | nit | `display-table-body.geometry.py` had no ref self-check and no exit code. **Re-run**: confirmed by reading it. | Added `EXPECT['006']`: a ref row that does not print the 006 string, or a missing ref, exits 1. On `wave52-ship`, all four rows print as before and the exit is 0. A scratch copy with the expected string mutated exits 1 with `SELF-CHECK FAILED`. The §6 sentence is now true for all five probes, and says when it became true. |
| 7 | nit | `predictionsReadAgainst` said "PLAN §9". | `plan-build.py` now says "PLAN §10 pre-registration addendum", names the four cells that make it certain, and was regenerated. |
| 8 | nit | L5's focused suite omitted `section-runner.test.mjs`. **Re-run**: :24 reads `section-runner.sh`; :137 and :276 read `provision-devices.sh`; the qemu `pkill` pin is at :290-295. | Added to L5's `node --test` line, with the reason. (`view-transition-bake.test.mjs:1612` also reads `section-runner.sh`, but it pins only `VT_BAKE` / `POST_LOAD_EXTRACT=1 BIDI_BAKE=1`, which L5 does not touch.) |
| 9 | nit | `--tests '*ContentApplier*'` matches no Compose test. **Re-run**: `find runtimes/compose/src/test -name '*ContentApplier*'` → 0; `PseudoBucketExtractorTest.kt` is the file that tests `extractBeforeAfterConfig`. | Replaced with `--tests '*PseudoBucketExtractor*'` in §2 L1 "May run", with the reason. |
| 10 | nit | "The other eight unprobed sections hold no carrier and no named control" was false for css-break. **Re-run**: L4's `mustNotMove` has `css-break/block-in-inline-011.html android` (its section's only `"type": "Float"` document; P 0.976 on both runs). **Also found by the planner:** css-gaps holds L5's must-not-move `flex-gap-decorations-027` ios (P 0.9997). | The §6 "Not probed" list now goes section by section, naming the check that replaces each. css-break: S-L4's JVM and Catalyst `FloatAvoidPlan` census over all 1435 payloads, then R4/R7. css-gaps: L5's empty carrier set and the `wave53-hh-probe` control, then R4/R7. css-view-transitions and css-gaps: their not-staffed watch lines. Five sections have nothing at all. |
| 11 | nit | "Gate before both commits: … exactly the five wire carriers" cannot hold per commit. **Re-run**: nested-list-extractor.md:163-167 says "(A)+(B): 1 fixture changes". | §4 step 3 now gives the set per commit: U1 = {counter-reset-reversed-nested}; U2 on the U1 tree = the other four; cumulative = 5; under P-narrow, U2 = {counter-suffix}. `revertUnits.*.wire` carries the same sets. |
| 12 | nit | The padding census read the gitignored `fixtures/wpt/`, which `wave53-open` was rewriting. **Re-run**: by mtime, 12 of the 17 bidiBaked fixtures were rewritten by `wave53-open` (Oct 7 01:13–01:58). The 4 `selectors` files are `wave52-ship`'s (Oct 6 01:06). `css-writing-modes/bidi-plaintext-br-001` is from an extraction at Oct 6 01:49 that matches no run directory. | **Applied in a different place:** the copy lives in `tools/titan/results/wave53-plan/bidi-baked-fixtures/` (17 files, 260 KB), not `wave53-lists-bakes/`. This review may write only this directory, and this directory is committed at §4 step 1, before any lane exists. `PROVENANCE.txt` records sha1, mtime, size and the extracting run (or "unidentified") per file. The census now reads the snapshot by default and takes a directory argument for a live tree. It prints 17 / 14 roots in 7 docs / 6 roots in 4 docs on both the snapshot and the live tree. The unidentified file does not affect the answer: its bake root has no `padding*` key. |

**For the orchestrator. This is not a plan change.** `wave53-open`'s css-view-transitions section hit the 50-min watchdog on
attempt 1 (`driver.log` 00:59:55 UTC = 02:59:55 CEST: rc=124, columns 0/48 ×3). Gate-driver reprovisioned and started attempt 2
at 03:03 CEST.
css-writing-modes, filter-effects and selectors are still to run. R3 completeness and the 30-section hash census of §8 step 1
read the final state; neither this plan nor `wave53-probe` (which excludes css-view-transitions) depends on attempt 1.

**Final shape after round 2:**
- **5 builder lanes** (L1 lists-bakes, L2 soft-hyphen, L3 canvas-root, L4 float-avoid, L5 harness-hygiene), landing as 11
  revertible commits: L1 2, L2 2, L3 4, L4 2, L5 1.
- 405 watch lines (unchanged).
- Union capture carriers: web 6, iOS 14, Android 18; wire carriers: 5 (2 under P-narrow).
- 17 gating predictions with numeric floors; revert rules 1-7.
- 5 geometry probes, each with a ref self-check.
- A 20-section `wave53-probe`, and a 4-section `wave53-hh-probe` that doubles as the web determinism read.
- Three control calibrations; the new cross-host one carries two red mutations.
