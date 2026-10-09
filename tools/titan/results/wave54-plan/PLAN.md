# Wave 54 — builder-lane plan (synthesized 2026-10-08 from the ten family briefs)

**Inputs.**
- The ten briefs in this directory, each with its census, replay and geometry probe: `rtl-marker-bake.md`,
  `compose-table-body-cell.md`, `hyphenate-character-compose.md`, `oof-containing-block.md`, `compose-wpt-content-box-cb.md`,
  `flex-zero-gap-rules.md`, `ua-heading-face.md`, `web-root-separator.md`, `web-out-of-flow-hyphen-box.md`,
  `label-chrome-all-reset.md`; plus the two queue scouts that ranked the new families, `queue-scout-layout.md` and
  `queue-scout-text-web.md`.
- `docs/BACKLOG.md`: "## Standing constraints", "## Next-wave obligations (wave 54 opens with these)" 0 and 0(a)–(e), the
  ranked-queue items the scouts read, "### Wave 53 lessons".
- The wave-53 plan `tools/titan/results/wave53-plan/PLAN.md` (§0–§10) as the procedure template, and the wave-53 record
  `tools/titan/results/wave53-gate/_note.md` ("Probe": the two units the revert rules took out).

**Evidence run of record: `wave53-final`** (dev tip 7cce3b22 has exactly its content): **web 1232/1372 · iOS 1125/1362 ·
Android 1115/1362**, 4096 scored cells over 1435 per-test IR documents. Measured at planning by `snapshot-cells.mjs`
(the scorer's own `loadRun`) into `cells-wave53-final.json`; every prediction's "from" value in `expectations.json` is
asserted against that file by `plan-build.py`. Every cell below is written `wave53-final <section>/<test> <platform> <P|f>
<ssim>` as `tools/titan/results/wave52-gate/cells.mjs` reads it.

**Nothing was built, run or captured while planning.** The opening gate `wave54-open` was running on this host
(`tools/titan/runs/wave54-open/gate-driver/driver.log`: css-view-transitions started 14:18:53, section 27 of 30). The
tree is byte-identical to 7cce3b22 outside `tools/titan/results/wave54-plan/` (and the orchestrator's
`tools/titan/results/wave54-gate/`).

**Written beside this file.**
- `PLAN.md` (this file).
- `plan-build.py`, the GENERATOR of `expectations.json` and `watchlist.txt` (never hand-edited). It reads the family censuses
  and `cells-wave53-final.json`.
- `snapshot-cells.mjs`, with its outputs `cells-wave53-final.json` and `cells-wave53-open.json`.
- `geometry-gate.py`: §6 R6 and revert rule 4 as one executable read-out over every lane's geometry probes. Its two
  executed calibrations are `geometry-gate.wave53-final.out.txt` and `geometry-gate.wave53-probe.L1L2.out.txt`.
- `build-workflow.js`: the seven-lane fan-out (model opus, file-driven, resumable).
- `integrate-seams.sh`: the §3 registry in landing order, with `--dry-run`.
- `plan-build.out.txt` and `watchlist-check.wave53-final.out.txt`: the executed outputs of items 2 and 3 below.
- `block-ellipsis-br.geometry.py` (fix round 1, S3) and `fix-r1/`: the round-1 fix pass's scripts, fakes and executed
  outputs ("Fix pass (round 1)" at the end of this file).

The geometry probes the lanes are gated by are the ten family probes the briefs wrote and executed
(`<family>.geometry.py`, all importing `tools/titan/results/wave53-plan/geometry_common.py`), plus three committed wave-53
probes reused as controls (`wave53-plan/lists-bakes.geometry.py`, `display-table-body.geometry.py`,
`soft-hyphen.geometry.py`), plus `block-ellipsis-br.geometry.py` (fix round 1). `land-units.sh` and `s1-workflow.js` are written at integration from the lane notes, as in
wave 53 (their path lists are facts of the lanes, not of the plan).

**Planning-time executions (all read-only; the tree outside this directory is untouched).**
1. `node snapshot-cells.mjs wave53-final` → 4096 scored cells, the totals above. Running it on `wave53-open` gives web
   1229 · iOS 1119 · Android 1111.
2. `python3 plan-build.py` → 775 watch lines (773 at planning; +2 at fix round 1, S6), union capture carriers **web 44 · iOS 27 · Android 51**, **23** wire
   carriers, **34** gating predictions with numeric floors, **19** revert units, a 4-section stage-1 probe and a 26-section
   probe. Every assertion holds:
   - every "from" value equals the run of record;
   - every watch line names a scored cell and matches exactly ONE test under `watchCells`' substring rule;
   - every carrier has its capture PNG in `wave53-final`, and every wire carrier has its per-test IR;
   - the revert units cover each lane's carriers exactly;
   - every carrier's section is probed;
   - no stem is a carrier of two lanes on one platform;
   - no must-not-move cell is any lane's carrier.

   Three must-not-move lines collided with another lane's carrier and were excluded; the script prints them (§4).
3. `RUN=wave53-final WATCH=…/wave54-plan/watchlist.txt node tools/titan/results/wave52-plan/watchlist-check.mjs` →
   `watch-lines=775 matched-cells(distinct)=781 … unmatched 0` (the same on `wave54-open`).
4. `python3 geometry-gate.py wave53-final --base wave53-open` → 185 keys: **gating 37, all FAIL · control 112, all PASS ·
   report 36, all FAIL** (MED / LOW targets) · 0 probe self-check failures, exit 1; with `--self-test` it HOLDS (exit 0).
   The rules can fail and the controls hold. (140 keys, 33 gating, at planning; fix round 1 added 45 keys and the
   control / report split, "Fix pass (round 1)"; the 140 planning keys keep their verdict and line byte-for-byte.)
5. `python3 geometry-gate.py wave53-probe --base wave53-open --lanes L1,L2` → 21 keys: PASS 17 · FAIL 4, all gating. The
   failures are `[M] counter-suffix android` ×2 (revert `Mprime`) and `006 android` ×2 (revert `TB-android`). **On
   wave 53's own probe pictures the runner names exactly the two units wave 53 reverted.** The `[P]` rows pass: hunk P was
   right on device.
6. The `plan-build.py` probe-decision hook, self-tested (`--out` / `--reverted`, fix round 1) with `Mprime` and `TB-android` reverted:
   - it withdraws counter-suffix web/ios and 006 android, as predictions and as carriers;
   - it withdraws their 7 gating geometry keys (`[M]` ×3, lists-bakes ×2, 006 ×2), which `geometry-gate.py` then prints
     `WITHDRAWN` and never gates on (fix round 1, M3);
   - it keeps counter-suffix android carried by P, both its capture and its wire.
7. The seam anchors were re-resolved at HEAD by reading the lines:
   - `ComponentRenderer.kt` :1141 `ListStyleUaRule.apply`, :1934 `.childContainingBlock(`, :2653 `TableApplier.Table(` /
     :2670 `fabricatedCellBorder`, :7072 `PreBreakPipeline.preBreak(`;
   - `ComponentRenderer.swift` :343 `UAElementFontRule.apply(`, :5011 `hyphenChar:`, :5026
     `GreedyLineBreaker.hasUnbreakableOverflowingLine(`;
   - `ComponentRenderer.tsx` :991 `renderText`, :1099 `renderChildSeparator`;
   - `extract-fixture.mjs` :11706 `childLineCtx`.

**Opening-gate obligation (BACKLOG obligation 0).** `node tools/titan/score-gate.mjs wave53-final wave54-open --watch
tools/titan/results/wave53-plan/watchlist.txt` must print **0 gained / 0 lost / 0 movers / 0 newly measured /
0 unmeasured-now**, and the fixture net must exit 0 on all 9 gate fixtures. One clause of obligation 0 is stale (label-chrome
brief §9 R6): all-then-color has NO committed baselines since the wave-53 withdrawal. Its child must read
`(gate-only: no committed baseline)` exactly as at `wave53-open` (`fixture-net.log:1223`); that is not a missing baseline.
Any divergence is wave 54's first item: §10 restates every affected prediction against `wave54-open` BEFORE any lane lands
(`expectations.json` `openingGate`).

## 0. Rules every lane works under

Quoted from `docs/BACKLOG.md` "## Standing constraints", "### Wave 53 lessons" and `CLAUDE.md` "Hard rules for every file",
as they apply here. Everything wave 53 §0 said still holds; the wave-54 deltas are marked **new**.

- **Ring-fence**: "the WPT test `filter-effects/backdrop-filter-basic-blur` belongs to an external session. No test-specific
  code, ever. Generic mechanisms that incidentally move it are reported plainly in the wave PR … never carved out by name."
  - It is not a carrier of any lane.
  - L4's OOF rule table claims its three `backdrop-filter` leaf boxes, which have no descendant to place, so the claim is
    inert by construction.
  - L3's U3 does not reach it (hyphenate brief §6).
  - Its one watch line is report-only.
- **No silent fallthroughs**: "If a value variant isn't supported on this platform yet, log it via the PropertyTracker or emit a
  TODO + keep the cross-platform comparison honest."
- **Short**: "target ≤200 lines. If a file grows past ~300, split it."
  - Files the lanes touch that are already past 300 lines: `ComponentRenderer.kt` 8446, `.swift` 6287, `.tsx` 1457,
    `extract-fixture.mjs` 12206, `ScreenshotCaptureScreen.kt` 2564, `StyleBuilder.swift` 1534, `ComposedCaptureGallery.tsx`
    1267, `bidi-bake.mjs` 1172, `CanvasRootHoist.kt` 1072, `TableApplier.kt` 761, `DynamicValueResolver.kt` 663,
    `GreedyLineBreaker.swift` 566, `FixedHoist.swift` 538, `NodeRenderer.ts` 432.
  - **New logic goes in a NEW file of ≤ 200 lines. An oversized file gets only a call site, a defaulted parameter or a
    one-line condition.**
  - The new files this forces are listed in each lane's "own:" list: `bidi-marker-bake.mjs` (restored), `TableCellHug.kt`,
    `CssStringParser.kt`, `SoftHyphenCuts.kt`, `SpentHyphen.swift`, the Compose `HyphenateCharacter` triplet, the
    `OutOfFlowContainingBlock` twins, `UAElementFontRule.kt`, the `UAHeadingFoldGate` twins, `ComposedRootSeparator.ts`,
    `InertOutOfFlowWordJoin.ts` and `label-chrome-exempt.json`.
  - BACKLOG 0(d)'s "split when a lane next owns each file" is NOT done this wave (§9 D8).
- **Every line commented**: "Comments explain the *why* … For extractors, reference the exact CSS spec section or parser file you're
  mirroring. For appliers, cite the platform API you're calling and why."
- **Pins can fail**: "A new check must be **proven able to fail** (mutation/negative test) before it is trusted."
  - Every pin named in §2 has its mutation. The lane EXECUTES it, records red → restore → green in its note, and restores the
    file (verified by sha256).
  - **New:** every lane also runs `geometry-gate.py wave53-final --base wave53-open --lanes <id> --self-test`, which exits 0
    only when its GATING keys FAIL today, its CONTROL keys PASS and its REPORT keys (MED / LOW targets, `report` in each
    probe) FAIL. A geometry rule that cannot fail is not a gate (fix round 1, S5: "every control passes" was false for the
    25 report keys).
- **Verbatim payloads** (wave skill Phase 2): pins use the per-test IR of `tools/titan/runs/wave53-final/sections/*/per-test-ir/`.
  - The opening obligation makes it byte-identical to `wave54-open`. §8 step 1 checks that identity before any lane
    launches.
  - PNGs are read from the same run and the frozen refs (`tools/wpt/refs/9b5435e…/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/`).
- **No edits under a running gate**: "Nothing a running gate executes or builds from is edited … while a gate (or a device A/B)
  runs on this host." Lanes launch only after `wave54-open` is scored, and pause whenever the orchestrator runs a device step
  (§8).
- **Never kill a foreign process**: as wave 53. No lane adds a host-wide stop. T5 (`start_adb_detached` kills the adb server at
  every provision) stays in Known-broken (§5).
- **A pre-registered lost list**: "A lost cell outside the list pre-registered before the closing gate stops the ship until its cause
  is found in the picture AND the wire document." This wave changes no instrument, so **the lost list is EMPTY** (§6 R1).
- **Seams are never edited by a lane.** The seam files are:
  - `runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt`
  - `runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift`
  - `apps/web-harness/src/sdui/ComponentRenderer.tsx`
  - `tools/titan/extract-fixture.mjs`

  A lane that needs one delivers `tools/titan/results/wave54-<lane>/seam-<n>.patch`: one patch per revert unit that needs a
  hunk, applying clean on 7cce3b22, its header naming that tree, the seam file's sha256 at base and the line anchor. The lane's
  pins must be green with it applied. §3 lists every hunk.
- **Shared tree (wave-53 §10 item 3, kept).** Lanes work on the SHARED tree under disjoint ownership and never commit. The
  orchestrator commits each revert unit from the lane's ownership list in §4 order (`land-units.sh`).
  - **New (BACKLOG "Wave 53 lessons": "A hygiene probe on the shared tree measures the union").** An owned file may not
    depend on a symbol that only the lane's seam patch creates (an edit that consumes such a symbol rides inside the patch
    as a patch-borne hunk), so the tree compiles at every moment with every lane's unseamed edits (`build-workflow.js`
    rule 2b).
  - Every device read on this tree is pre-registered to be read with the UNION carrier set (§6, stage 1).
- **Single-writer suites**: "Fix/skeptic lanes run focused tests only; the orchestrator runs ONE sequential full sweep afterwards — and
  that sweep INCLUDES the harness suites."
  - Four lanes compile Compose `:runtime` (L2, L3, L4, L5).
  - Three compile the SwiftUI package (L3, L4, L5).
  - A lane retries on a lock, never on a foreign file's error (`build-workflow.js` rule 3).
- **Skeptic census**: "A builder lane's blast radius is re-derived by a skeptic with its OWN census before it is believed."
- **No scratchpad pointers**: every artifact goes under `tools/titan/results/wave54-<lane>/` as soon as it exists.
  - Each lane note ends in `STATUS: COMPLETE` or `STATUS: PARTIAL — <what is left>` and carries a `TREES:` line.
  - **New:** each note has one section per revert unit, naming exactly the paths of that unit's commit. `land-units.sh` is
    written from those sections.
- **Device windows only through the orchestrator.** Builder lanes run no Chromium, simulator or emulator step, no `gate-driver.sh`,
  no `test-all.sh`. They write the exact command, its expected output and the decision it feeds under
  "## ORCHESTRATOR WINDOW REQUESTS" in their note. The orchestrator runs it when the device is idle and feeds the result back
  through a follow-up agent (wave-53 §10 item 4).
- **Labels are honest.**
  - A P→P rise is "picture-correctness".
  - A pass whose picture does not earn it is "DEGENERATE"; a flip on such a picture is never claimed as a fix.
  - Before any "passes" sentence, look at the capture next to the frozen ref: "a score is not a look".
  - **New (BACKLOG "Wave 53 lessons"):** never label a native cell "picture-correct" from a replay; only a device capture
    plus the family's geometry probe earns it.
  - Say "runtime", not "engine".

## 1. Lane table

There are seven builder lanes, at the top of the 5–7 band.
- **Ten briefs:** two were already folded by their scouts (flex-zero-gap-rules and compose-wpt-content-box-cb fold into L4,
  as `queue-scout-layout.md` §0 allows), and web-out-of-flow-hyphen-box rides with web-root-separator in L6, as its brief
  allows (§9 D4).
- **No NO-GO brief:** every brief recommends GO or GO-SMALL. Their own NO-GO sub-items are in §5.
- **19 revert units:** each lane lands as one commit per unit (§4), and each unit is revertible alone, except where its
  `revertOrder` says otherwise (M′ before P; U3b before U3; U2-seed before U1).
- **Disjoint ownership**, with every collision resolved in §4.

| # | lane (dir `tools/titan/results/wave54-<lane>/`) | briefs | targets (`wave53-final`) | predicted (confidence) | revert units | effort |
|---|---|---|---|---|---|---|
| L1 | rtl-marker-bake | rtl-marker-bake (GO) | `bidi-lines-001` android f 0.8934, `-002` android P 0.9534; `counter-suffix` web P 0.9818 (wrong-side markers) / ios P 0.9802 / android P 0.9547 (DEGENERATE) | +1 android (HIGH, device-measured); counter-suffix web faithful (HIGH), ios/android RTL rows picture-correct (MED-HIGH / MED, device-gated at stage 1) | **P**, **M′** | S–M |
| L2 | table-body-cell | compose-table-body-cell (GO-SMALL) | `s-11-1-1b-006` android P 0.9944 (DEGENERATE, the square 5 px too high) | 0 flips; DEGENERATE → faithful (MED-HIGH, device-gated at stage 1) | **TB-android** | S |
| L3 | hyphenate-character | hyphenate-character-compose (GO) | `hyphenate-character-001/-003/-004` ×3 f 0.8812–0.935 (DEGENERATE by construction since wave 53); the 18-document br radius | +2 ios (MED-HIGH); +2 ios, +4 web at MED / MED-LOW (incl. clip-path-filter-order web / iOS and balance-grid-container web on replay B, fix round 2); 4 P→P picture-correct (block-ellipsis ×4 web; -002 ios is OUT and stays DEGENERATE, fix round 1 S3) | **U1**, **U2-android**, **U2-ios**, **U3**, **U3b** | M |
| L4 | oof-layout | oof-containing-block (GO), compose-wpt-content-box-cb (GO-SMALL), flex-zero-gap-rules (GO-SMALL) | `contain-content-003/-011` android f 0.9405 / 0.9284; `backdrop-filter-containing-block` ios f 0.7955 / android f 0.7506; `nested-border-radius-clip-3` android f 0.9574; `flex-gap-decorations-033` ios/android f 0.94; 17 red-ink / short-box DEGENERATE passes | +4 android, +1 ios (HIGH / MED-HIGH); +1 / +1 at MED; 17 DEGENERATE → faithful | **OOF-android**, **OOF-ios**, **CBB-android**, **GAP-android**, **GAP-ios** | M |
| L5 | ua-heading-face | ua-heading-face (GO) | `block-in-inline-015-print` android f 0.9489; `text-decoration-inset-005/-006/-014` ios/android f 0.8987–0.9238 | +1 android (HIGH); +2 ios / +2 android at MED; +1 ios / +1 android at MED-LOW | **U1-android**, **U2-ios** | S–M |
| L6 | web-tail | web-root-separator (GO), web-out-of-flow-hyphen-box (GO-SMALL) | `box-sizing-007/-008/-022` web f 0.9036 / 0.8943 / 0.9442; `semi-replaced-stretch-other` web f 0.941; `hyphens-out-of-flow-002` web f 0.9411 (never moved in 13 runs); 14 web DEGENERATE passes | +4 web (HIGH / MED-HIGH); +1 at MED; 14 DEGENERATE → faithful | **RS**, **W1** | S |
| L7 | label-chrome | label-chrome-all-reset (GO-SMALL) | no corpus cell: the tripwire's three red stems; all-then-color still gate-only | tripwire 136/136 green (HIGH); fixture net exit 0 against committed baselines (MED-HIGH) | **U1**, **U2-seed** | S |

**Expected movement against `wave54-open`** (which should equal `wave53-final`; §8 step 1).
- **HIGH and MED-HIGH flips: web +4 · iOS +3 · Android +6**, giving **web 1236/1372 · iOS 1128/1362 · Android 1121/1362**.
- With the MED and MED-LOW flips: web +5 · iOS +6 · Android +4 more, a ceiling of **web 1241 · iOS 1134 · Android 1125**
  (fix round 2, R2-S4: replay B on their own pixels adds `clip-path-filter-order` web / iOS and `balance-grid-container`
  web at MED; it was web +3 · iOS +5, 1239 / 1133).
- LOW / LOW-MED possibles, never counted: web `hyphenate-character-002/-004`; Android `hyphenate-character-001/-003` and
  `clip-path-filter-order` (replay B 0.9549). The three `backdrop-filter-*` Android rows are undirected movers now (not
  replayable: R2-S4).
- **Lost 0. Unmeasured-now 0. Newly measured 0.**

**Passing cells made picture-correct with no change of verdict: 37 in full and 2 in part** (fix round 1, S3; 38 before).
Each is claimed ONLY when its own geometry key prints `→ GEOMETRY OK` on the closing run (`expectations.json`
`pictureCorrectTally`, asserted by `plan-build.py`: every entry has a key in its lane's probes).
- L1: `counter-suffix` web in full; the counter-suffix natives in part.
- L2: 006 android.
- L3: `block-ellipsis-002/-004/-005/-006` web (the new `block-ellipsis-br.geometry.py`, gating U3). **`block-ellipsis-002`
  ios is OUT**: looked at, its capture paints Line 4 and no "…" (the iOS clamp is not applied) and U3 only removes the
  stray blank lines (replay B keeps 4 bands vs the ref's 3). It moves up and stays DEGENERATE (`stayDegenerateEvenIfPass`).
- L4: 8 OOF cells, 6 autopos and 3 nested clips.
- L6: 13 box-sizing captures (one key each now: 010, 011, 013, 014-019, 020, 021, 024, 025) and `semi-replaced-stretch-input`.

Four passing cells stay DEGENERATE even when their lane is right (`expectations.json` `stayDegenerateEvenIfPass`):
`bidi-lines-002` android (the orange `!` on the left), `counter-suffix` ios / android (rows 3–6 / 5–6) and
`block-ellipsis-002` ios (no clamp).

**What each lane may run.**
- The common floor is focused suites only, while no gate, probe or A/B runs on the host.
- Anything that starts Chromium, a simulator or an emulator is an orchestrator window: L1's CDP marker probe and converter
  hop, L3's gate-flag wire differential, L6's step-0 CDP probe and L7's seeding.
- No lane runs a full suite.

## 2. Lanes in detail

### L1 · rtl-marker-bake

Brief `rtl-marker-bake.md`. Queue: BACKLOG obligation 0(a)(1) (the reverted wave-53 L1 U2) and 2(c⁴) ("Fix it in the
bake"). **Two units, pre-registered separately; P lands first and stands on its own device evidence. M′ lands behind a
device probe and a geometry gate (stage 1).**

**Unit P — the padding kept on a bidi-bake root is zeroed.**
- **Targets.**
  - `wave53-final css-text/bidi/bidi-lines-001 android f 0.8934` and `css-text/bidi/bidi-lines-002 android P 0.9534`.
  - `css-counter-styles/counter-suffix android P 0.9547`: its RTL text moves to the ref's x, still with no marker.
- **Device evidence, not a replay.** At `wave53-probe`, P was the ONLY wire change on bidi-lines-001/-002 and
  anchor-center-safe-rtl: `rtl-marker-bake.ir-diff.out.txt` shows only `Padding*` → `{px:0}`, with component counts
  13→13 / 19→19 / 14→14.
  - Web and iOS were decoded-pixel identical (`rtl-marker-bake.capture-identity.out.txt`).
  - Android moved: `cells.mjs` gives bidi-lines-001 android f 0.8934 → P 0.9629 and -002 P 0.9534 → P 0.9818.
  - `rtl-marker-bake.geometry.py wave53-probe` prints `[P] … android … → GEOMETRY OK` on both; wave53-final prints x33 / x35.
- **Mechanism.** The bake makes its root `position: relative` with its border-box used size. CSS 2.1 §10.1 item 4 /
  css-position-3 §3.1 resolve abspos insets against the PADDING box, so zeroing the padding moves nothing on a conforming
  runtime. Android anchors at the content box (`PositionedParentFlowSlot.kt` header), so it moves every run by the padding.
- **Change.** Exactly hunk P of `a8ffd1c6` in its fix-pass state (`git show b0edb788^:tools/titan/bidi-bake.mjs`):
  - `inPageBidiWalker` records `padding`, `backgroundClip`, `backgroundOrigin` and `overflow`;
  - `export function paddingIsSpent(el)` is added;
  - `rootProperties(rect, position, el)` emits `padding: '0'`;
  - `applyBidiBakePlan` deletes `padding-*` first and overwrites an authored shorthand in place;
  - only a root whose resolved padding is NON-ZERO, and not under the `content-box` clip/origin or non-visible `overflow`
    guard, is rewritten.
- **Wire.** 6 roots in 4 documents (census `paddedRoots`): counter-suffix ×2, bidi-lines-001, -002, anchor-center-safe-rtl ×2.
  No component is added, so there is **no id shadow**.

**Unit M′ — the RTL `::marker` baked as runs owned by the bake ROOT.**
- **Targets.** `wave53-final css-counter-styles/counter-suffix web P 0.9818` (RTL markers painted on the LEFT, x46-58),
  `ios P 0.9802` and `android P 0.9547` (DEGENERATE: no RTL marker at all).
- **Why the wave-53 unit failed (traced; brief §3, `rtl-marker-bake.look.out.txt`).**
  - At `wave53-probe`, Android painted every marker run exactly ONE RUN HEIGHT (+20 px) low, and never painted the third
    run of an item (`1`, `2`).
  - The reverted M parented the runs to the out-of-flow `<li>`. In this document `CanvasRootHoist.Host` is inactive
    (`hostActivates` = `anyOutOfFlowBox`, and every box sits under the RELATIVE bake root).
  - The `<li>`'s children therefore went through the plain Column loop, where `outOfFlowSlotMount` is
    `PositionedParentFlowSlot.mount` ONLY when `CanvasRootHoist.LocalActive` is true (`ComponentRenderer.kt` :3617-3627).
  - Each run kept its Column footprint against the `<li>`'s Height 24: run 2 gets 4 px of space and paints +20; run 3 gets
    0 px and paints nowhere.
  - The bake's CDP measurement was right (`wave53-lists-bakes/marker-probe.out.txt`: `MARKER PROBE: ALL PASS`).
- **Change.** Restore `tools/titan/bidi-marker-bake.mjs` from `b0edb788^` (200 lines, fix-pass state) and its call sites,
  with ONE design change at the two call-site lines:
  - l.925: the marker runs are measured from the enclosing ROOT's padding-box origin;
  - l.974: `ownerPath` = the enclosing root path.

  The `<li>` keeps exactly one child, its text run, and still gets `list-style-type: none`. A ROOT list item (arabic-indic)
  still gets nothing.
- **Why it fixes Android without runtime code.** Root-owned runs take the RELATIVE root's `Box` branch: one
  `RenderAbsoluteChild` mount each, with no Column cursor. That path is device-proven by bidi-lines-002's root-owned
  `Hello` / `سلام` runs at wave53-probe.
- **Predicted wire.** counter-suffix goes from 23 to 29 components. The 6 runs sit at root-relative Left 116.3 / 120.59,
  Top 2 / 26 (brief §4 table). The id shadow is +6 over the 15 later `css-counter-styles/cssom/*` documents: the
  "renumbered" class (`expectations.json` `wireRenumbering.expectedShadow`).
- **Never shipped:** M′ without P. It is a pre-registered LOSS: counter-suffix android → f ≈0.947, the wave-53 replay.
  `revertOrder` P = [Mprime, P].
- **Spec.**
  - css-lists-3 (`::marker`; `outside` places it at the inline-start side; Appendix A `unicode-bidi: isolate;
    font-variant-numeric: tabular-nums; white-space: pre`).
  - css-counter-styles-3 `suffix`; css-writing-modes-4 `direction`; UAX #9.
  - CSS 2.1 §10.1 item 4 (P).

**Lane facts.**
- **own:**
  - `tools/titan/bidi-bake.mjs`: hunk P plus the two M′ call-site lines and the import. The wave-53 fix-pass state was
    +91 / −4 over today's 1172 lines (`git diff --numstat a8ffd1c6^ b0edb788^ -- tools/titan/bidi-bake.mjs`; the 95 written
    here before fix round 2 was the `--stat` line total); the lane keeps every NEW function except
    `paddingIsSpent` in `bidi-marker-bake.mjs` and reports the final delta (a 0(d) size exception, §9 D8).
  - `tools/titan/bidi-marker-bake.mjs` (restored).
  - `tools/titan/bidi-bake.test.mjs`, with the pins split per unit (758 lines at HEAD, already past the ~300-line split
    line: the lane adds its pins in per-unit `describe` blocks and reports the final count, like the 0(d) exception, §9 D8).
  - `tools/titan/results/wave54-rtl-marker-bake/`: the note, mutation logs, and an adapted COPY of
    `wave53-lists-bakes/marker-probe.mjs` asserting root-owned runs. The original asserts `kids.slice(1)` of each `<li>`,
    which must fail under M′ by design.
- **Seams:** none. `bidiBakeFixture(fixture, testRel)` keeps its signature and its call site in `extract-fixture.mjs`.
- **Read-only:**
  - `counter-style-bake.mjs` (its `BULLET_STYLES` skip of `none` is used as-is);
  - `CanvasRootHoist.kt`, `PositionedParentFlowSlot.kt`, both `ComponentRenderer`s;
  - `ListMarkerOutsideHang.swift`: its :33-41 comment re-true goes to the orchestrator's docs pass when M′ lands (§9 D18).
- **May run:**
  - `nice -n 19 node --test tools/titan/bidi-bake.test.mjs tools/titan/counter-style-bake.test.mjs tools/titan/counter-bake.test.mjs`.
  - The lane's own census over the 17 frozen `_wpt.bidiBaked` fixtures (`tools/titan/results/wave53-plan/bidi-baked-fixtures/`)
    and over `wave53-final` per-test IR.
- **ORCHESTRATOR WINDOW REQUESTS** (the lane writes them; brief §9):
  - **[W1] CDP marker probe** (Chromium): the adapted marker-probe copy on counter-suffix. Expect:
    - `MARKER PROBE: ALL PASS`, outcome `baked — 2 roots, 10 runs`;
    - each RTL `<li>` with `list-style-type: none`, ONE child and no `marker-*` stamp;
    - 6 root-owned runs at frame x ⊂ [131,148] with top = li top + 2 (±1);
    - DOMSnapshot strings `'1. ','2. ','א. ','ב. '`.

    A `marker-not-baked` stamp on any RTL item means M′ does not land.
  - **[W2] Converter hop** for counter-suffix with the gate flags (`--post-load`, `--bidi-bake`, `--vt-bake`). Expect:
    - the §M′ wire, with the 6 runs' `slot.parent` = the two `<ol>` roots;
    - the RTL roots' `PaddingTop..Left {px:0}`;
    - no converter warning.
  - [W3], the brief's one-section device probe, is **stage 1** (`wave54-pre`, §6/§8). The lane does not request it separately.
- **Carrier set** (`expectations.json` `lanes.L1-rtl-marker-bake`):
  - captures: web/ios `{counter-suffix}`; android `{counter-suffix, bidi-lines-001, bidi-lines-002, anchor-center-safe-rtl}`;
  - wire: those 4 documents, plus the +6 × 15 renumbering shadow under M′.
  - Rule: P = a bake root with NON-ZERO resolved padding (6 roots in 4 documents). M′ = a component with `meta.markerText`
    and Position ABSOLUTE|FIXED (exactly 4, all counter-suffix).
  - The three zero-padding bake-root documents (`selectors/dir-style-02a`, `dir-selector-change-003`, `-004`) must stay
    byte-identical in wire and capture (pin V3b).
- **Unit pins** (verbatim; every mutation executed, red → restore → green, sha256):
  - P:
    - V3: verbatim `counter-suffix__0__4` gives `padding: '0'` and no other `padding*` key; a `content-box` root keeps its
      padding. Mutations: remove the line; drop the content-box guard.
    - V3b: the verbatim `dir-style-02a` root comes out deep-equal with its key ORDER unchanged. Mutation: drop the
      non-zero guard.
    - The overflow guard. Mutation: drop it.
  - M′: wave 53's V1, V2, V4, V5, VF, VF1-3, ported to root ownership, plus:
    - **V6**: on the counter-suffix walk, every marker run's `ownerPath` is the enclosing root, at root-relative left
      116.3 / 120.59 (±0.05) and top 2 / 26. Mutation: owner = li → red.
    - **V7**: no `plan.boxes` entry owns more runs than it has text runs; this guards against recreating the Compose
      stack shape. Mutation: push the marker runs to the box → red.
- **Predictions** (from `expectations.json`; "gate floor" = rule 3, "gating" = rule 4. Rules 1, 2 and 5 apply to every row.)

  | cell | → predicted | confidence | gate floor · geometry | unit(s) |
  |---|---|---|---|---|
  | `bidi-lines-001` android | f 0.8934 → **P ≈0.9629** (device-measured: these bytes at wave53-probe) | HIGH | 0.955 · `[P]` gating | P |
  | `bidi-lines-002` android | P 0.9534 → P ≈0.9818 (device-measured); **stays DEGENERATE** (orange `!` on the left on all three) | HIGH | 0.975 · `[P]` gating | P |
  | `counter-suffix` android | P 0.9547 → ≈0.9815 with P (replay), ≈0.989 with M′ (replay 0.9894 / 0.9900); RTL rows picture-correct ONLY if `[M]` prints OK; **stays DEGENERATE on rows 5-6** | MED-HIGH (P floor) / MED (M′) | 0.970 · `[M]` gating (M′) | P, M′ |
  | `counter-suffix` web | P 0.9818 → **P 1** (wave53-probe measured 1 with the same marker geometry) | HIGH | 0.999 · `[M]` gating | M′ |
  | `counter-suffix` ios | P 0.9802 → P ≈0.987 (wave53-probe 0.9873); RTL rows picture-correct, **stays DEGENERATE on rows 3-6** | MED-HIGH | 0.985 · `[M]` gating | M′ |

- **Must not move (80 cells, `watchlist.txt` L1 block).**
  - All three platforms of the 13 other bidi-baked documents (31 scored cells). Of those 13,
    `attachment-local-positioning-3/-4` are unscored and are covered by the capture control alone.
  - bidi-lines-001/-002 web + ios.
  - The 15 `css-counter-styles/cssom/*` documents ×3 (id shadow only; captures identical).
  - counter-suffix rows y0-207 on all three platforms: the `rows 0-207 identical to wave54-open` crop of both probes.
- **Probe sections.** Stage 1: css-counter-styles, css-text. Probe: those plus css-anchor-position, css-backgrounds,
  css-writing-modes and selectors (every bidi-baked document's section).
- **Geometry verdicts** (`expectations.json` `lanes.L1-rtl-marker-bake.geometryProbes`; `geometry-gate.py --lanes L1`).
  - `rtl-marker-bake.geometry.py <run> wave54-open`: `[P] bidi/bidi-lines-00{1,2} android → GEOMETRY OK` (gating, P);
    `[M] counter-suffix {web,ios,android} … | rows 0-207 identical to wave54-open → GEOMETRY OK` (gating, M′).
  - `wave53-plan/lists-bakes.geometry.py <run> wave54-open`: counter-suffix ×3 OK (web and android gating), and
    counter-reset-reversed-nested ×3 OK (control: wave-53 U1 must not regress).
  - On wave53-final the gating rows print WRONG; on wave53-probe `[M] android` prints `GEOMETRY WRONG (rtl row 1 no marker
    ink on text row y214-225 (marker ink y244-245 below the line))`, the reverted unit's defect, named.
- **Stage-1 decision** (`lanes.L1-rtl-marker-bake.stage1Decision`). At `wave54-pre`, M′ is reverted on any of:
  - an `[M]` line that is not `GEOMETRY OK`, on any platform;
  - a missing `rows 0-207 identical`;
  - counter-suffix web < 0.999 or ios < 0.985;
  - a css-counter-styles change beyond the 15 renumbered cssom documents.

  P stays unless its own `[P]` rows or floors fail; then M′ goes first, then P. Before any label is written, the three
  counter-suffix PNGs are OPENED: rows 9-12 must read `foo .1 / bar .2 / foo .א / bar .ב`, with nothing below y300.
- **Lane note:** `tools/titan/results/wave54-rtl-marker-bake/_note.md`.

### L2 · table-body-cell

Brief `compose-table-body-cell.md`. Queue: obligation 0(a)(2) (the reverted wave-53 L3 B-android, `276757ea` /
`d773ff6a`), 0(b) and 0(l″). **One unit, gated on a device probe of CSS2 + css-tables (stage 1).**

- **Target.** `wave53-final CSS2/css21-errata/s-11-1-1b-006 android P 0.9944`, DEGENERATE: the square sits 5 px too high
  (rows 51-70 against the ref's 56-75), touching the text. B-ios and B-web shipped right (ios P 0.9992, web P 1).
- **What the wave-53 probe picture shows** (`wave53-probe … android P 0.9906`; brief §2, `compose-table-body-cell.corroborate.out.txt`):
  - a 1-px black OUTLINE of an empty 20×20 box at x24-43, rows 56-75 (76 = the 1-px perimeter);
  - the solid square displaced 20 px RIGHT, to x44-63.

  This corrects the wave-53 record, which had the empty box "beside" / "left of" the square.
- **Mechanism** (traced to the pixel; brief §3). The forest is right and is a line-for-line twin of the Swift one. Two
  Compose table-renderer behaviours are wrong:
  - **D1**: `ComponentRenderer.kt:2670` computes `fabricatedCellBorder` true for a table that declares `Display` and has no
    `_tag`. `TableApplier.TableCell` then paints the 1-dp demo stroke (`TableApplier.kt:455-466`), a "demo default" with
    "NO CSS basis".
  - **D2**: the anonymous cell, first in the `fillMaxWidth` `TableApplier.TableRow`, takes the whole 20-px shrink-to-fit
    table width through `blockFlowWidth`'s `fillMaxWidth()` (`ComponentRenderer.kt:1578`). The td is then measured at
    max-width 0, and `ExactWidthOverflow.measureSpec(20, 0, 0)` paints it start-anchored past its 0-wide slot, at x44-63.
  - Both behaviours are corroborated on shipped captures:
    - `css-tables/height-distribution/percentage-sizing-of-table-cell-children-003` android P 0.9954 carries a 396-px
      black rim;
    - `css-tables/baseline-empty-cell-001` android P 0.9956 loses its empty second cell.
- **Change.** Re-land `276757ea` unchanged (`git apply --check` clean on 7cce3b22). Then add two narrow pieces keyed on ONE
  marker that only the 006 forest creates:
  - (a) `TableBodyForest.kt`: `ANONYMOUS_ROLE = "anonymous-table"` on the three synthetic boxes, and `isAnonymous(c)`.
    These boxes live only in memory; the only role values read anywhere are `body-root` and `line-break`.
  - (b) NEW `TableCellHug.kt` (~60 lines): `LocalTableCellsHugContent` and a pure
    `chrome(component, fabricatedDefault) = Chrome(stroke = fabricatedDefault && !isAnonymous, hug = isAnonymous)`.
    `Modifier.hugColumn()` = `IntrinsicChannel.widthAtMaxIntrinsic` when the local is true. Its refusal is logged: "§17.5.2
    column max-content skipped — the cell keeps the fill".
  - (c) `TableApplier.kt` (~8 lines): a `cellsHugContent: Boolean = false` parameter, the provider, and `TableCell` chaining
    `.hugColumn()` before `.fillMaxHeight()`.
  - (d) seam-1 (§3): `val chrome = TableCellHug.chrome(component, fabricatedDefault = <today's expression verbatim>)`, passing
    `fabricatedCellBorder = chrome.stroke, cellsHugContent = chrome.hug`.

  On 006 the anonymous cell hugs to 0 and the td's cell hugs to 20, so the td sits at x24-43, rows 56-75, with no stroke.
- **Spec.** CSS 2.1 §17.2.1 (anonymous boxes carry no border; `border` is not inherited), §17.6.1 (a cell paints only its
  own borders), §17.5.2.2 (auto layout: each column gets its max-content width when the table has room), §8.3 (no margins
  on cells; already in 276757ea).
- **Rejected** (brief §4): eliding an ink-free anonymous cell (a fake fixup), a fabricated `Width: max-content`, D2 alone, and
  the general versions (all declared tables lose the stroke / all cells hug). The general versions move 12 D1 + 8 D2
  documents and are queued (§5).
- **own:**
  - `runtimes/compose/src/main/java/com/styleconverter/runtime/table/TableBodyForest.kt` + `src/test/…/table/TableBodyForestTest.kt`
    (the re-land plus pin m6);
  - `runtimes/compose/src/main/java/com/styleconverter/runtime/table/TableCellHug.kt` + `src/test/…/table/TableCellHugTest.kt` (new);
  - `runtimes/compose/src/main/java/com/styleconverter/runtime/table/TableApplier.kt` (parameter, provider, chain only);
  - `apps/android-harness/app/src/main/java/com/styleconverter/test/screenshot/ScreenshotCaptureScreen.kt`: ONLY the re-landed
    `composedCanvasRoots` call site. §4 notes that L4 changes `hostActive`, which this file's root fold reads; the read stays
    as it is;
  - `apps/android-harness/app/src/test/java/com/styleconverter/test/screenshot/ComposedCanvasTableBodyTest.kt` (re-land);
  - `tools/titan/results/wave54-table-body-cell/`.
- **Seams:** `ComponentRenderer.kt` → `seam-1.patch` (§3).
- **Not touched:** `runtimes/swiftui/**`, `apps/ios-harness/**`, `apps/web-harness/**`.
- **Read-only:** `TableBoxTree.kt`, `IntrinsicChannel.kt`, `ExactWidthOverflow.kt`, `SizingApplier.kt`.
- **May run:**
  - `(cd apps/android-harness && ./gradlew :runtime:testDebugUnitTest --tests '*TableBodyForest*' --tests '*TableCellHug*' --tests '*TableBoxTree*' :app:testDebugUnitTest --tests '*ComposedCanvas*' --tests '*UaBlockMargins*')`;
  - `node tools/titan/results/wave54-plan/compose-table-body-cell.replay.mjs` and `python3 …/compose-table-body-cell.geometry.py` on recorded runs.
- **Window requests:** none. The device read is stage 1 (`wave54-pre`, CSS2 + css-tables).
- **Carrier set:** android `{s-11-1-1b-006}`; web and iOS none; wire none.
  - Rule: a synthetic forest box exists only for a body-root whose last `Display` ∈ {TABLE, INLINE_TABLE}: 1 of 1435
    documents. Every other Compose table gets `chrome = (today's stroke, hug=false)`, byte-identical by construction.
- **Unit pins:**
  - `TableBodyForestTest`: the six re-landed pins (shape, m1-m5), plus **m6** `syntheticBoxesAreAnonymous`. Mutation:
    drop the role in `synthetic` → red.
  - `TableCellHugTest`: `chrome` on four verbatim wave53-final payloads:
    - the synthetic `#table` → (false, true);
    - 006's declared body-root → (true, false);
    - percentage-sizing-003's declared table → (true, false);
    - baseline-empty-cell-001's UA `<table>` with today's expression → (false, false);
    - `css-contain/contain-content-004`'s table (L4's carrier, D2 radius) with today's expression → (that expression,
      false): the pin behind §9 D15.

    Mutations: drop `!isAnonymous` (stroke red); hard-wire `hug = false` (hug red); `hug = true` for any table (the 004 row
    red).
  - A source pin in the same class: `TableApplier.Table(` passes `fabricatedCellBorder = chrome.stroke` and
    `cellsHugContent = chrome.hug`, and `TableCell` chains `hugColumn()` before `fillMaxHeight()`. Mutation: revert either
    argument → red.
  - `ComposedCanvasTableBodyTest`: the stack pin (0 gap above `#table`).
- **Prediction:**

  | cell | → predicted | confidence | gate floor · geometry | unit |
  |---|---|---|---|---|
  | `s-11-1-1b-006` android | P 0.9944 → **P ≈0.9983** (replay from both the final and the probe picture); square x24-43 rows 56-75, no stroke | MED-HIGH (geometry) / MED (score) | 0.996 · gating (both probes) | TB-android |

  SSIM cannot gate this cell: all three wrong pictures pass (0.9944 / 0.9906 / the D1-only falsifier 0.9893;
  `compose-table-body-cell.replay.out.txt`). The geometry rows gate it.
- **Must not move (97 cells).**
  - 006 web/ios.
  - `s-11-1-1b-001…005`, `-007…009` ×3.
  - The other 58 Compose-`DisplayType.TABLE` documents on android, including all 12 D1 and 8 D2 documents. This is the
    scoping proof. `contain-content-004` android is excluded: it is L4's carrier (§4).
  - `position-absolute-dynamic-static-position-table-cell` ×3, `baseline-vertical` ×3, `fixup-dynamic-anonymous-*` ×3.
- **Probe sections.** Stage 1: CSS2, css-tables (css-tables must be 48/48 android-identical). Probe: the brief's eleven
  (CSS2, css-tables, css-position, css-break, css-contain, css-display, css-values, css-writing-modes, selectors, css-text,
  css-backgrounds), which hold every Compose-table document.
- **Geometry verdicts.**
  - `compose-table-body-cell.geometry.py <run>`: `006 android … square block solid 400/400 | stray ink 0 | red px 0 →
    GEOMETRY OK`.
  - `wave53-plan/display-table-body.geometry.py <run> 006`: `006 android  square rows 56-75 (20) x 24-43 | red px 0`.
  - Both are gating. On wave53-final and wave53-probe both android rows are WRONG (executed).
- **Stage-1 decision.** TB-android is reverted unless BOTH android lines print their strings and 006 android ≥ 0.996. The
  cell then stays P 0.9944 DEGENERATE.
- **Lane note:** `tools/titan/results/wave54-table-body-cell/_note.md`.

### L3 · hyphenate-character

Brief `hyphenate-character-compose.md`. Queue: obligation 0(b) ("Compose does not honour `hyphenate-character`"), 4(f) (the
wave-53 "DEGENERATE by construction" label), 11(e). The brief's measured finding: **the glyph alone flips nothing**. The best
"glyph fixed" replay scores 0.9474, and web -004, whose glyph is already right, is f 0.9249. The pictures show four
defects. The lane fixes three of them (A, C, D) and puts the fourth (E) behind a probe-decided unit: five revert units in
all. Web needs no runtime change: it renders the wire faithfully, so fixing the wire fixes web (brief §4 B).

- **Targets** (wave53-final): `css-text/hyphens/hyphenate-character-001` web f 0.935 · ios f 0.9287 · android f 0.9301;
  `-003` f 0.8812 / f 0.9337 / f 0.9292; `-004` f 0.9249 / f 0.9134 / f 0.905. Plus `-002` and the 18-document br radius
  (§predictions).
- **Mechanism** (brief §4):
  - **A, converter.** `HyphenateCharacterPropertyParser.kt:26` drops `""` (`isEmpty() → null`, which falls back to
    `Generic _unmapped`). `:24` strips the quotes without decoding css-syntax-3 escapes, so `"\2022"` reaches the wire
    with a literal backslash and web paints `im\2022`.
  - **C, natives.** Compose bakes U+2010 in `PreBreakPipeline` with no string parameter. Its `tookSoftHyphenBreak` counts
    hyphenChars, which is 0 for `""`. iOS passes `AutoHyphenation.defaultHyphenCharacter` at `ComponentRenderer.swift:5011`;
    its `HyphenateCharacterApplier` is an identity and its Extractor reads the wrong shape. iOS also re-wraps a line that
    overflows only by its own hyphen (`GreedyLineBreaker.hasUnbreakableOverflowingLine` counts `-` as an opportunity).
  - **D, extractor.** `buildNode` seeds `childLineCtx.hasInline` once (`extract-fixture.mjs:11706`) and never re-arms it for
    text interleaved BETWEEN children. A `<br>` that ENDS a text line therefore gets the 20-px blank-line height, on all
    three platforms.
  - **E, drift.** The line-start br is 20 px while the ref's blank line is 1.2 × 16 = 19.2.
- **Change, five units:**
  - **U1, converter.** NEW `primitiveParsers/CssStringParser.kt` (≤ 200 lines, css-syntax-3 §4.3.5 / §4.3.7: 1-6 hex digits
    plus one optional whitespace; U+0000 / surrogates / > U+10FFFF → U+FFFD). The parser returns `String("")` for `""`;
    unquoted input keeps today's acceptance. The wire changes value inside an existing `string` variant, so there is no new
    byte shape and no freeze event.
  - **U2-android.** A NEW Compose triplet `typography/wrapping/HyphenateCharacter{Config,Extractor,Applier}.kt` (the
    Extractor maps `{type:auto}` → null = U+2010 and `{type:string,value}` verbatim, including `""`).
    - NEW `SoftHyphenCuts.kt`, whose `took(text, lines, hyphenChar)` walks line boundaries against U+00AD positions, so it
      works for `""`.
    - `PreBreakPipeline.kt` gains a defaulted `hyphenChar` parameter only.
    - seam-1 adds one argument at `:7072`.
    - The Applier logs via `PropertyTracker.logOnce` when a non-auto value meets a dictionary run (Minikin paints its own
      hyphen).
  - **U2-ios.** The existing Swift triplet is made real: the Config carries `value: String?`; the Extractor reads the object
    shape and does NOT lowercase. Also:
    - NEW `SpentHyphen.swift`: css-text-3 §5.5, a taken opportunity is spent;
    - `TypographyAggregate.swift` (+1 field);
    - `StyleBuilder.swift` (+1 TextConfig field, +1 line beside :525);
    - `GreedyLineBreaker.swift` (one defaulted `spentHyphen` parameter);
    - seam-2: `:5011` → `textConfig.hyphenateCharacter ?? AutoHyphenation.defaultHyphenCharacter`, `:5026` →
      `spentHyphen: textConfig.hyphenateCharacter`.

    nil keeps every other run byte-identical by construction.
  - **U3, extractor re-arm (seam-3).** In the `buildNode` children loop, set `childLineCtx.hasInline = true` when `node.runs`
    holds a non-whitespace `{text}` entry after the previous child and before this one. This is the child-scope twin of
    `:11733`; CSS 2.1 §9.5 says a br preceded by inline content on its line ends that line.
  - **U3b, probe-decided (seam-3b, a separate patch and commit).** A line-start br whose HOST (the br's parent element)
    declares `line-height` **on itself** (its own declared cascade; the `font` shorthand counts) gets that line box
    (`normal` → 1.2 × font-size) instead of 20. Radius: 12 brs in 4 documents (hyphenate-character-001…004). An INHERITED
    line-height does not trigger U3b: under that reading 23 more 20-px brs in 4 documents would move, two of them not
    carriers (`css-multicol/baseline-002`, `baseline-007`; `skeptic-r1/br-census-all.out.txt`). Fix round 1, S4.
- **Sequencing (load-bearing).** U3 never lands without U1 + U2-android + U2-ios before it. U3 alone manufactures a
  DEGENERATE -003 ios pass: replay `B` gives 0.9566 with hyphens where the ref has bullets. §4 lands L3 last, in unit
  order. A later revert of U1 or U2-<p> keeps that platform's 001/003/004 cells under `degenerateByConstruction`.
- **Spec.** css-text-4 §6.3 (`hyphenate-character: <string>` admits `""`); css-syntax-3 §4.3.5 / §4.3.7; css-text-3 §5.3 /
  §5.5; CSS 2.1 §9.5 / §10.8.
- **own:**
  - converter `HyphenateCharacterPropertyParser.kt`, NEW `primitiveParsers/CssStringParser.kt`, NEW
    `HyphenateCharacterPropertyParserTest.kt`;
  - Compose NEW `typography/wrapping/HyphenateCharacter{Config,Extractor,Applier}.kt` and `SoftHyphenCuts.kt`;
    `typography/wrapping/PreBreakPipeline.kt` (defaulted parameter + delegate only); `PreBreakPipelineTest.kt`; NEW
    `HyphenateCharacterExtractorTest.kt`;
  - Swift `StyleEngine/typography/wrapping/HyphenateCharacter{Config,Extractor,Applier}.swift`, NEW `SpentHyphen.swift`;
    `StyleEngine/typography/TypographyAggregate.swift` (+1 field); `Renderer/StyleBuilder.swift` (+1 field, +1 line;
    **no other lane may touch it**); `StyleEngine/typography/GreedyLineBreaker.swift` (one defaulted parameter);
    `Tests/…/GreedyLineBreakerTests.swift`, `TypographyTests.swift` (the `:311` pin moves to the wire's object shape);
  - NEW `tools/titan/extract-fixture-br-line-context.test.mjs`;
  - `tools/titan/results/wave54-hyphenate-character/`.
- **Seams:** `ComponentRenderer.kt` → `seam-1.patch` (U2-android); `ComponentRenderer.swift` → `seam-2.patch` (U2-ios);
  `extract-fixture.mjs` → `seam-3.patch` (U3) and `seam-3b.patch` (U3b).
- **Read-only:** `WordBreakOpportunities.{kt,swift}`, `DecorationOps.swift`, `AutoHyphenation.*`, `SoftHyphenPolicy.*`,
  `InlineRunFold.kt`, `TextStyleApplier.kt` (its dead DEFAULT U+00AD is queued, §5).
- **May run:**
  - `./gradlew :converter:test --tests '*HyphenateCharacter*' --tests '*CssString*'`;
  - `(cd apps/android-harness && ./gradlew :runtime:testDebugUnitTest --tests '*PreBreakPipelineTest' --tests '*GreedyLineBreakerTest' --tests '*HyphenateCharacter*' --tests '*WordBreakOpportunities*' --tests '*SoftHyphenPolicy*')`;
  - Catalyst `-only-testing:` `GreedyLineBreakerTests`, `TypographyTests`, `SoftHyphenPolicyTests`;
  - `node --test tools/titan/extract-fixture-br-line-context.test.mjs tools/titan/extract-fixture.test.mjs`;
  - the in-process static extractor differential over the 1435 sources (pure node; U3's radius is all statically extracted);
  - the brief's replays and `hyphenate-character.geometry.py` on recorded runs.
- **ORCHESTRATOR WINDOW REQUESTS:** **[W-L3] the gate-flag wire differential**. Re-extract and convert all 30 sections with
  the gate's own flags, pre (HEAD) and post (U1 + seam-3 + seam-3b), and diff `per-test-ir`. Expect exactly **19
  content-changed documents** (U1's 4 ∪ U3's 18) and **0 renumbered**; any other document is a leak. This is also the
  converter hop for U1's four documents.
- **Carrier set** (`lanes.L3-hyphenate-character`):
  - captures: web and android = the 15 pixel-moving br-radius documents (hyphenate-character-001…004, block-ellipsis-002 /
    -004 / -005 / -006, backdrop-filter-clip-rect / -edge-clipping / -paint-order / -plus-filter, clip-path-filter-order,
    balance-grid-container, column-height-009); iOS the same plus `hyphenate-limit-chars-001`;
  - wire: 19 documents (the 18 br documents plus `hyphenate-character-005`, whose U1 value change is pixel-invisible under
    `white-space: pre`).
  - `revert-layer-006`, `revert-val-001`, `-002` are wire carriers with pixel-invariant captures, so they are must-not-move
    (HIGH; green-on-green inside `overflow: hidden`).
- **Unit pins** (brief §10; each with its mutation):
  - **U1** `HyphenateCharacterPropertyParserTest`: `""`→`String("")`, `"\2022"`→`"•"`, `"\00a0\0640"`→`" ـ"`,
    `"\2022 x"`→`"•x"`, `'/-/'`→`"/-/"`, `auto`→`Auto`. Mutations: drop the decoding; restore `isEmpty() → null`.
  - **U2-android** `PreBreakPipelineTest` (mono measurer, CH = 19.2, verbatim run pieces):
    - (a) piece 1 at 4.5·CH with `""` → fired `im/ple/men/ta/tion`. Mutation: restore character counting → identity.
    - (b) the width case `real/iza/tion`;
    - (c) `"•"` at 5.5·CH;
    - (d) `"/-/"` at 6.5·CH (line 2 overflows);
    - (e) the default argument: every existing `assertSame` identity pin stays green.
    - The Extractor: `""`, `auto`→null, `/-/` verbatim. Mutation: lowercase / keyword path.
  - **U2-ios:**
    - `GreedyLineBreaker.lines(" ini\u{AD}tial\u{AD}iza\u{AD}tion", 6.5·CH, hyphenChar: "/-/")` → 4 lines;
    - `hasUnbreakableOverflowingLine(["tial/-/"], 6.5·CH, spentHyphen: "/-/") == true` and `== false` with nil. Mutation:
      drop the strip;
    - the Extractor on the object shape → `"/-/"`.
  - **U3 / U3b** `extract-fixture-br-line-context.test.mjs`:
    - verbatim 001 `<div>` br heights `[0,20,0,20,0,20]` (today `[0,20,20,20,20,20]`);
    - revert-val-001 all 0;
    - the five body-level brs of B-RC1 unchanged;
    - `empty-span-001` unchanged;
    - U3b: 001 → `[0,19.2,0,19.2,0,19.2]`;
    - U3b negative (fix round 1, S4): the verbatim `css-multicol/baseline-007` brs (line-height 40px declared on an
      ANCESTOR, not the host) stay 20, and `baseline-002`'s (2em on an ancestor) stay 20. Mutation: read the nearest
      ancestor's line-height (the inherited reading) → red.

    Mutation: drop the re-arm. The wire census after seam-3 changes exactly 54 brs in 18 documents (+12 in 4 for U3b).
- **Predictions** (46 rows; `expectations.json`). Floors on the six MED-HIGH rows (001 / 003 ios, block-ellipsis-002 /
  -004 / -005 / -006 web); every other row is read and labelled. Rules 1 and 5 bind every row; rule 2 binds every row
  except the **16 undirected** ones (002 ios, limit-chars-001 ios, the nine backdrop-filter rows, balance-grid-container
  ios / android, column-height-009 ×3: fix round 2, R2-S4). U3b is ADDITIVE in every L3 row's `requires` (its revert keeps
  the U3-only value the row gives first), so a U3b revert demotes nothing; a U1 / U2-<p> / U3 revert demotes the rows
  that need it (fix round 2, R2-S1).

  | cell | → predicted | confidence | gate floor · geometry | units |
  |---|---|---|---|---|
  | `hyphenate-character-001` ios | f 0.9287 → **P ≈0.979** (GBd 0.9852 − 0.006 raster allowance) | MED-HIGH | 0.965 · gating | U1, U2-ios, U3, U3b |
  | `hyphenate-character-003` ios | f 0.9337 → **P ≈0.977** (GBd 0.9831 − 0.006) | MED-HIGH | 0.965 · gating | U1, U2-ios, U3, U3b |
  | `hyphenate-character-004` ios | f 0.9134 → P ≈0.979 (needs the spent-hyphen hunk) | MED | report | U2-ios, U3, U3b |
  | `hyphenate-character-001` / `-003` web | f 0.935 / 0.8812 → P ≈0.960 / 0.956 (U3); ≈0.976-0.981 with U3b | MED-LOW | report | U1, U3, U3b |
  | `hyphenate-character-004` web | f 0.9249 → 0.947-0.966 (glyph already right: not degenerate) | LOW | report | U3, U3b |
  | `hyphenate-character-001/-003/-004` android | f 0.9301 / 0.9292 / 0.905 → ≈0.957 / 0.953 / 0.945 (U3); ≈0.976-0.981 with U3b | LOW-MED / LOW / LOW | report | (U1,) U2-android, U3, U3b |
  | `hyphenate-character-002` web / ios / android | f 0.92 / 0.9261 / 0.9223 → ≈0.96 / mover (**undirected**) / stays f (Minikin dictionary hyphen: a logged wall; replay B of its only unit U3: 0.923) | LOW-MED / LOW / LOW | report | U1, U2-ios, U3 |
  | `hyphenate-limit-chars-001` ios | f 0.8919 → mover (U+2010 → U+002D in the dictionary fold; **undirected**) | LOW | report | U2-ios |
  | `block-ellipsis-002` web | P 0.9868 → P ≈1.0 (B replay 1) | MED-HIGH | 0.995 · gating (`block-ellipsis-br`) | U3 |
  | `block-ellipsis-004/-005/-006` web | P 0.9737 / 0.9735 / 0.9735 → P ≈0.998 (B replay) | MED-HIGH | 0.99 · gating (`block-ellipsis-br`) | U3 |
  | `block-ellipsis-002` ios / android | P 0.9813 DEGENERATE → ≈0.993, stays DEGENERATE (Line 4, no "…") / P 0.9884 → identical or up | MED / LOW | report (control lines `4 bands vs ref 3` / `2 bands vs ref 3`) | U3 |
  | `block-ellipsis-004/-005/-006` natives | byte-identical expected (no stray gap there) | MED | report | U3 |
  | `backdrop-filter-clip-rect / -edge-clipping / -paint-order` web, ios | P 0.959-0.9595 / 0.956-0.9578 → moves, expected up; **undirected** (not replayable: the stray line sits under abspos boxes that do not move with the flow and text shows through their backdrop filter; fix round 2 extends R2-S4 to these six) | MED | report | U3 |
  | the same three android | f 0.9315 / 0.929 / 0.929 → moves, expected up; **undirected** (same reason, R2-S4) | LOW | report | U3 |
  | `backdrop-filter-plus-filter` ×3 | stays (the stray br is the `<p>`'s trailing one) | MED | report | U3 |
  | `clip-path-filter-order` web / ios / android | f 0.8586 / 0.8578 / 0.8406 → **P ≈1.0 / P ≈0.998 / ≈0.955** (replay B on their own pixels 1 / 0.9978 / 0.9549, `hyphenate-character.replay-b-u3f-score.out.txt`; LOOKED at: `fix-r2/look-clip-path-filter-order-ref-Bweb-Bios-Bandroid.png`) | MED / MED / LOW-MED | report | U3 |
  | `balance-grid-container` web | f 0.9207 → **P ≈1.0** (replay B 1: the two stray blank lines removed give the ref, `fix-r2/look-balance-grid-container-ref-Bweb.png`) | MED | report | U3 |
  | `balance-grid-container` ios / android, `column-height-009` ×3 | f → moves; **undirected** (not replayable: the natives paint the address on one line; column-height-009's 15 brs feed a multicol balance) | LOW | report | U3 |

  - **DEGENERATE-by-construction, honestly** (`expectations.json` `degenerateByConstruction` + `degenerateRetirement`).
    001/003/004 stay on the conservative list. A cell on platform p is relabelled faithful ONLY when U1, U2-p and U3 are all
    on the closing tree AND `hyphenate-character.geometry.py` prints `→ GEOMETRY OK` on its row. The relabel is recorded per
    cell in the gate note. Web -004 is never degenerate: its glyph is already right.
- **Must not move (74 cells):**
  - `hyphenate-character-005` ×3 (also L1's);
  - `revert-layer-006`, `revert-val-001`, `-002` ×3;
  - `hyphenate-limit-chars-001` web/android;
  - the U2 default-argument population on both natives: 17 css-text/hyphens tests + `block-ellipsis-014/-028` (fix round 1,
    S6, added `hyphens-none-shy-on-2nd-line-001` android P 0.998 / ios P 0.9988: it carries U+00AD on the wire);
  - every css-text/hyphens WEB cell except L3's four carriers and L6's `hyphens-out-of-flow-002`.
- **Probe sections:** css-text, css-overflow, filter-effects, css-cascade, css-masking, css-multicol (the U3 radius).
- **Geometry verdicts:**
  - `hyphenate-character.geometry.py <run>`: the 9 capture rows → `GEOMETRY OK` (the GB / GBd replays print 9/9 OK);
    gating on 001 ios and 003 ios.
  - Its WRONG reasons name the unit (rule 6): `glyph:` → U2-<platform> (U1 on web); `lines:` / `offset:` → U3b, then U3.
  - `wave53-plan/soft-hyphen.geometry.py <run>`: control. The wave-53 soft-hyphen pictures must survive U2's defaulted
    parameter.
  - `block-ellipsis-br.geometry.py <run>` (fix round 1, S3): band count, tops / bottoms ±3 px and right edges ±4 px of the
    ref in the top 200 rows. The four web rows are gating (U3): WRONG on wave53-final (002 band top y79 vs y59; 004-006 3
    bands vs 2), OK on the brief's U3 replay pictures, WRONG on white and on the ref shifted 6 px
    (`fix-r1/post-S3.block-ellipsis-fakes.out.txt`). The native rows are controls holding today's WRONG lines verbatim.
  - The hyphenate gating keys `requires` U1 + U2-ios + U3 (fix round 1, M3): a probe revert of any of them withdraws the key.
- **U3b decision (probe-decided; `revertUnits.U3b.probeDecided`).** U3b is IN at `wave54-probe`. It stays iff 001 ios and
  003 ios meet their 0.965 floors with no `offset:` / `lines:` reason on **those two gating iOS rows**, and rules 1/2/5 hold
  on its carriers. The report row 004 ios (MED) is read, never a trigger; a `lines:` re-wrap there from a glyph-width
  change is U2-ios's (`reasonToUnit`), not U3b's (fix round 3, R3-N2).
  Otherwise it is reverted FIRST and css-text is re-probed. That is the brief's GBn risk: iOS loses its zero-drift
  compensation, 0.947–0.958.
- **Lane note:** `tools/titan/results/wave54-hyphenate-character/_note.md`. It carries the docs hand-off: coverage-audit
  Android `real` 15 → 16 (`CLAUDE.md:347`, `docs/STATUS.md:38`), the converter test count, and BACKLOG 0(b)'s
  hyphenate-character line.

### L4 · oof-layout

Briefs `oof-containing-block.md` (GO, effort M), `compose-wpt-content-box-cb.md` (GO-SMALL, S) and `flex-zero-gap-rules.md`
(GO-SMALL, S), as ranked in `queue-scout-layout.md` §0. Queue: 0(k) (red-square class), 0(j) (static position), the 0(b)
neighbourhood, 7(e¹) (obligation 0(c)). **Five units: one per native for each two-native change, so a native that falsifies
on device goes alone (the wave-53 L4 precedent; §9 D1).**

**Units OOF-android / OOF-ios: the containing block of an out-of-flow box.**
- **Targets** (wave53-final):
  - `css-contain/contain-content-003` android f 0.9405 and `-011` android f 0.9284;
  - `filter-effects/backdrop-filter-containing-block` ios f 0.7955 / android f 0.7506;
  - red-ink DEGENERATE passes: `css-position/position-relative-003`, `-004` and
    `change-insets-inside-strict-containment-nested` ios P 0.9594 / android P 0.9589 each, and
    `CSS2/abspos/static-fixed-inside-abspos` ios P 0.9841 / android P 0.9835.
  - Seen in the pictures: every green box whose containing block is the test's subject lands at the canvas origin or the
    canvas top-right, leaving the red bare (`oof-containing-block.geometry.out.txt`: natives green (16,16,115,115), red
    7200 px; ref (16,88,115,187), red 0).
- **Mechanism.**
  - Compose: `CanvasRootHoist.shouldHoistToCanvasRoot`'s FIXED arm (:340) is `!hasTransformedAncestor` and ignores insets.
    The only non-positioned containing-block clause is `establishesTransformContainingBlock` (:222, the transform family
    only), and the RC1 static-position class (:389) is ABSOLUTE-only.
  - Swift: `FixedHoist.swift` has the same two gaps (:309, :347, :115).
  - Contain (layout | paint | strict | content), Filter and BackdropFilter never establish a containing block.
- **Change.**
  - F1: a NEW pure rule table per native, `OutOfFlowContainingBlock.{kt,swift}`, ≤ 200 lines each. `establishes` is true for
    Contain ∋ LAYOUT|PAINT|STRICT|CONTENT, a non-empty Filter or BackdropFilter, or WillChange ∋
    `filter|backdrop-filter|contain`. **It is false when the element is itself ABSOLUTE|FIXED**: that keeps the four
    positioned backdrop-filter containers inert, with a `PropertyTracker` breadcrumb
    `ContainingBlock[positioned-establisher-fixed-descendant]`.
  - F2: one OR at each choke point: Compose `CanvasRootHoist` :222, Swift `FixedHoist` :309. Every Compose consumer
    (`collectCanvasHoisted`, `anyOutOfFlowBox`, `ComponentRenderer.kt` :2010 / :3471) reads that one function, so no seam
    is needed.
  - F3: FIXED needs an inset to hoist (:340 / :347), and FIXED joins RC1 (:389 / :115).
- **Spec.** css-position-3 §2.1 and §3.5.3 (static position); css-contain-2 (layout/paint containment "make the element a
  containing block for absolutely positioned and fixed positioned descendants"); filter-effects-1 / -2; css-will-change-1.

**Unit CBB-android: Compose's WPT containing-block bands.**
- **Targets.** `filter-effects/backdrop-filter-nested-border-radius-clip-3` android f 0.9574; `-clip` / `-2` / `-4` android
  P 0.9649 / 0.9689 / 0.977 (short box); `css-flexbox/abspos/abspos-autopos-*` ×6 android P 0.9966 ×5 / 0.9869 (red-ink
  passes, green 70×80 in a 100×100 red box).
- **Mechanism.** `DynamicValueResolver.childContainingBlock` (:207-249) subtracts padding and border unconditionally. Under
  WPT capture `SizingApplier.effectiveBoxSizing` draws the parent content-box, so the out-of-flow percentage path
  (`resolveOutOfFlowPercentSizes`) consumes the wrong base.
- **Change.** `childContainingBlock(…, wptCaptureMode: Boolean = false)` subtracts only for an EFFECTIVE border-box (the same
  `effectiveBoxSizing` call). The dark stage keeps the frozen reading, breadcrumbed
  `ContainingBlock[dark-stage-content-box-bands]`. seam-1 adds one named argument at :1934. F2 of the brief (0 carriers) is
  OUT (§5).

**Units GAP-android / GAP-ios: rules on zero-width gaps.**
- **Target.** `css-gaps/flex/flex-gap-decorations-033` ios / android f 0.94. Every item is pixel-exact; the ref's 2200 red
  + 3000 blue rule pixels are missing. The simulation with them copied in scores 1.0000.
- **Mechanism.** `GapDecorationLines.kt` :138-143 / :156-165 and `GapDecorationSegments.kt` :166-169, with the Swift twins,
  drop zero-extent gaps.
- **Change.** Keep a zero-extent interval as a gap POSITION (`isPositionable = end >= start` on both sides; `isEmpty` keeps
  "cuts nothing"). Negative extents are still dropped, and paint order stays row over column at crossings.
  css-gap-decorations-1.

**Lane facts.**
- **own:**
  - NEW `runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/OutOfFlowContainingBlock.kt` and
    `src/test/…/layout/position/OutOfFlowContainingBlockTest.kt`;
  - NEW `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/layout/position/OutOfFlowContainingBlock.swift` and
    `Tests/StyleConverterRuntimeTests/OutOfFlowContainingBlockTests.swift`;
  - `layout/position/CanvasRootHoist.kt`: call-site lines :222, :340, :389 only; plus `CanvasRootHoistTest.kt` rows;
  - `Renderer/FixedHoist.swift`: call-site lines :309, :347, :115 only; plus the Swift FixedHoist test rows;
  - `core/variables/DynamicValueResolver.kt`: the signature + one condition, or NEW `core/variables/ContainingBlockBands.kt`
    (≤ 60 lines) if it grows past three lines; plus `DynamicValueResolverTest.kt`;
  - Compose `columns/GapDecoration{Lines,Segments,Geometry}.kt` and Swift `StyleEngine/columns/GapDecoration{Lines,Segments,Geometry}.swift`,
    plus their tests;
  - `tools/titan/results/wave54-oof-layout/`.
- **Seams:** `ComponentRenderer.kt` → `seam-1.patch` (CBB-android, :1934). OOF and GAP need none.
- **Read-only:**
  - `TransformContainingBlock.{kt,swift}` (the transform clause is OR-ed, never edited);
  - `PositionedParentFlowSlot.kt`, `PerformanceConfig.kt`, `SizingApplier.kt`, `BackdropModifier.kt`;
  - `ScreenshotCaptureScreen.kt` (L2's; its root fold reads `hostActive`);
  - `GapDecorationsPainter.swift`, `VerticalTextFlowLayout.kt`.
- **May run:**
  - `(cd apps/android-harness && ./gradlew :runtime:testDebugUnitTest --tests '*OutOfFlowContainingBlock*' --tests '*CanvasRootHoist*' --tests '*TransformContainingBlock*' --tests '*DynamicValueResolver*' --tests '*GapDecoration*')`;
  - Catalyst `-only-testing:` `OutOfFlowContainingBlockTests` (NEW, the lane creates it), `FixedHoistTests`,
    `TransformContainingBlockTests`, and for the Swift `GapDecorationLines` / `-Segments` edits the EXISTING classes that
    exercise them: `GapDecorationSegmentsTests`, `GapDecorationBandsTests`, `GapDecorationGrammarTests`. There is no
    `GapDecorationLinesTests` class (fix round 1, S7); a lane that wants one creates it in its own list. Every
    `-only-testing:` run records xcodebuild's `Executed N tests` line with N > 0: a filter on a missing class runs zero
    tests and is not a green run (`build-workflow.js` rule 4);
  - the census scripts of `queue-scout-layout.census.mjs`.
- **Window requests:** none. Every device read is the probe.
- **Carrier set:**
  - OOF-android: the 6 M2 tests + `static-fixed-inside-abspos`, `position-relative-003` and the unscored
    `position-fixed-scroll-nested-fixed`, 9 captures;
  - OOF-ios: change-insets, `position-relative-004`, `backdrop-filter-containing-block`, `static-fixed-inside-abspos`,
    `position-relative-003` and `position-fixed-scroll-nested-fixed`, 6 captures. **`contain-content-003/-004/-011` iOS are
    must-not-move**: the Swift change touches FIXED hoisting only (§9 D3);
  - CBB-android: the 10 cbborder carriers + `flex-gap-decorations-006` (the watched side reader);
  - GAP: 033 on each native.
  - Rule (census `oofcb`): Compose host activation flips true → false on exactly the 7 `hostFlips` documents.
- **Unit pins** (Kotlin + Swift twins, verbatim wave53-final IR):
  - **OOF:**
    - P1, the rule table: contain PAINT/STRICT/CONTENT/LAYOUT → true; SIZE/INLINE_SIZE/STYLE/NONE → false; Filter
      `[invert]`, BackdropFilter `[blur]`, WillChange `backdrop-filter` → true; WillChange `opacity` → false; BackdropFilter
      on a `Position ABSOLUTE` box → false.
    - P2, the hoist decisions: FIXED without insets → no hoist; FIXED with `Top` and no establisher → hoist; under
      contain:strict → no hoist; ABSOLUTE with `Top` under a static contain:content parent → no hoist.
    - P3, the pure walks: they hoist NOTHING for the 6 M2 and 2 scored M1 tests, and exactly what they hoist today for
      backdrop-filter-clip-rect, -edge-clipping, backface-visibility-hidden-004, anchor-name-006,
      position-fixed-dynamic-transformed-sibling, **anchor-center-safe-rtl and every one of the 26 `fixedControls`
      payloads, css-view-transitions included** (§6 "not probed").
    - P4, the host: `hostActivates` turns false for exactly the 7 `hostFlips` documents.
    - Mutations: M-a drop the contain clause; M-b drop `hasAnyInset` from F3; M-c drop F1's "not itself absolute/fixed";
      M-d revert Swift :309.
  - **CBB:**
    - WPT rows: abspos-autopos-htb-ltr container → (100,100); nested-clip `#outer` → (200,100); `#middle` → (200,100); an
      explicit `BORDER_BOX` under WPT → still subtracted.
    - The dark-stage 300 → 276 row stays.
    - Mutations: always subtract (WPT rows red); never subtract (the dark-stage row red).
    - A source pin that `ComponentRenderer.kt` passes `wptCaptureMode = wptCaptureModeForStretch`. Mutation: drop the
      argument.
  - **GAP:** rows from 033's verbatim item rects: 5 column-rule segments (2+1+2) at content-relative x45-55 / 95-105, and 2
    full-width row-rule segments; a snapped-rect pin at x = 50 / 100. Mutations: restore the `!gap.isEmpty` filter (red);
    accept negative extents (a negative-overlap pin red).
- **Predictions** (26 rows; `expectations.json`):

  | cell | → predicted | confidence | gate floor · geometry | unit |
  |---|---|---|---|---|
  | `contain-content-003` android | f 0.9405 → **P ≈0.997** (sim 0.9967) | HIGH | 0.99 · gating | OOF-android |
  | `contain-content-011` android | f 0.9284 → **P ≈0.985** (sim ≤ 0.9854); its "25" (ref "17") is wrong on ALL THREE platforms, not this lane | MED-HIGH | 0.97 · gating | OOF-android |
  | `backdrop-filter-containing-block` ios / android | f 0.7955 / 0.7506 → P (sim bracket 0.9414 … 1.0) | MED | report | OOF-ios / OOF-android |
  | `position-relative-004`, `change-insets-…-nested` ios / android | P 0.9594 / 0.9589 DEGENERATE → P ≈0.9974 / 0.9967 | MED-HIGH | 0.99 · gating | OOF-<p> |
  | `static-fixed-inside-abspos` ios / android | P 0.9841 / 0.9835 DEGENERATE → P ≈0.9974 / 0.9967 | HIGH / MED-HIGH | 0.99 · gating | OOF-<p> |
  | `position-relative-003` ios / android | P 0.9594 / 0.9589 DEGENERATE → P ≈0.9974 / 0.9967 (the relpos span chain must net 0) | MED | report | OOF-<p> |
  | `contain-content-004` android | f 0.8286 → moves, stays f (the table renders wrong on both natives); **undirected** (brief §10 R1: its in-flow `FAIL` span), exempt from rule 2 | MED | report | OOF-android |
  | `nested-border-radius-clip-3` android | f 0.9574 → **P ≈0.99** (iOS twin 0.9921) | MED-HIGH | 0.96 · gating | CBB-android |
  | `nested-border-radius-clip` / `-2` / `-4` android | P 0.9649 / 0.9689 / 0.977 → P ≈0.99 | MED-HIGH | 0.98 · gating | CBB-android |
  | `abspos-autopos-{htb,vlr,vrl}-ltr` android | P 0.9966 DEGENERATE → P 0.9967 (green 100×100) | HIGH | 0.995 · gating | CBB-android |
  | `abspos-autopos-*-rtl` android | P 0.9869 / 0.9966 / 0.9966 → ≈0.9967 | MED | report | CBB-android |
  | `flex-gap-decorations-006` android | f 0.8223 → may move, stays f; **undirected**, exempt from rule 2 | LOW | report | CBB-android |
  | `flex-gap-decorations-033` ios / android | f 0.94 → **P ≈1.000** (sim 1.0000) | HIGH | 0.99 · gating | GAP-ios / GAP-android |

- **Must not move (253 cells):**
  - every web cell of the lane's carrier tests;
  - iOS `contain-content-003/-004/-011`;
  - the 24 scored `fixedControls` ×3;
  - `backdrop-filter-zero-size` ×3;
  - `backface-visibility-hidden-001` ×3 (the wave-35 transform-CB container);
  - `cross-fade-target-alpha` ×3 (the CBB in-flow control);
  - iOS of the 10 CBB documents: the three `*-rtl` iOS rows keep their own 10-px iOS offset, verbatim;
  - every other css-gaps cell ×3.
- **Shared cells** (§4): `backdrop-filter-clip-rect` / `-edge-clipping` are L3 carriers; `anchor-center-safe-rtl` is an L1 P
  carrier. L4's invariance on them rests on pins P1/P3 and on the closing A/B, never on the capture control.
- **Probe sections:** css-position, css-contain, CSS2, filter-effects, css-transforms, css-flexbox, css-images, css-gaps,
  css-anchor-position, css-display, css-masking, css-pseudo (every `fixedControls` section except css-view-transitions).
- **Geometry verdicts:**
  - `oof-containing-block.geometry.py <run>`: every native row OK; the 8 HIGH / MED-HIGH rows are gating.
  - `compose-wpt-content-box-cb.geometry.py <run>`: the 10 android rows OK, the 7 ltr / clip rows gating. The three iOS
    `*-rtl` rows must print today's WRONG line verbatim.
  - `flex-zero-gap-rules.geometry.py <run>`: 033 ios / android OK, gating. It reads the red runs on a row through every
    flex line (fix round 2), the blue runs on a column through every item column, and the red runs on a column through
    each column-rule gap, x65 / x115, ±1 px: every segment's vertical extent, flex line by flex line (fix round 3, R3-S3).
  - Executed on wave53-final: every gating row prints WRONG, every control passes, and the report rows
    (backdrop-filter-containing-block ×2, position-relative-003 ×2, autopos rtl android ×3) print WRONG (`--self-test`).
- **Risks the lane carries** (brief R1-R5): the Compose positioned-container Box now hosts STATIC containers (004's in-flow
  `FAIL` span); host activation turns off in 7 documents (watch the paragraph pitch y≈44-83 in the probe PNGs); `contain:
  paint` does not clip (a named limitation); the backdrop two-pass must not invert green and red. All are device-only, so
  they are read at the probe.
- **Lane note:** `tools/titan/results/wave54-oof-layout/_note.md`.

### L5 · ua-heading-face

Brief `ua-heading-face.md` (via `queue-scout-text-web.md` #1). Queue: 0(g) (the Compose `UAElementFontRule` twin, "STILL
DEFERRED"), and the real mechanism under 4(b′).

- **Targets** (wave53-final): `css-break/block-in-inline-015-print` android f 0.9489 (iOS P 0.9894 is the anchor);
  `css-text-decor/text-decoration-inset-005` ios f 0.8995 / android f 0.9; `-006` f 0.8987 / f 0.8993; `-014` f 0.9238 /
  f 0.923.
- **What the pictures show** (`ua-heading-face.geometry.out.txt`):
  - Android paints the author-unsized `<h1>` at the 16-px regular body face: bands 9-12 px tall, right edges x42-53, against
    the ref's 18-25 px and x72-96.
  - inset-005 / -006 natives draw ONE 20-px band where the ref wraps "fox" to a second line.
  - inset-014 wraps right, but at half height.
  - The fold IS engaged on both natives (sup and sub are shifted inside one paragraph). What is missing is the face.
- **Mechanism.**
  - Compose has no twin of `UAElementFontRule.swift`: :1141 runs `ListStyleUaRule.apply` as the only tag-keyed UA step.
  - iOS `UAElementFontRule.headingAppliesTo(hasElementChildren:)` stands down for every heading with children. Wave 40
    measured that gate on the STACKED pre-fold render; the gate's own doc says "when it lands, this gate should be deleted,
    not widened".
  - The extractor bakes `UA_H1_PROPS` only for COLLAPSED runs, which is why inset-001…004 pass.
  - **4(b′) correction:** the decoration-colour refusal no longer limits 005/006/014. The div above carries `Color black`
    (`body-inherited-baked`).
- **Change.**
  - U1-android: NEW `typography/UAElementFontRule.kt` (≤ 200 lines; h1…h6 `2 / 1.5 / 1.17 / 1 / .83 / .67` bold; sub/sup
    `1/1.2`; the em base is the inherited FontSize, else `MonospaceUAFontSize.resolveSpFromPairs ?: 16`; the author's own
    FontSize/Font/FontWeight wins).
    - NEW `typography/UAHeadingFoldGate.kt` (≤ 120 lines): it stands down only when the heading has children AND the fold
      does not engage.
    - seam-1 wraps :1141 BEFORE `DynamicValueResolver` (:1206), so `16ch` and `em` resolve against the UA face.
  - U2-ios: NEW `UAHeadingFoldGate.swift`; seam-2 at `mergedProperties(now:)` :343-356; `UAElementFontRule.swift` gets doc
    edits only (TWIN STATUS, the re-dated `headingAppliesTo` table).
- **Why gating on "the fold engages" is sound** (brief §4 D): fold admission never reads the host's FontSize or FontWeight,
  so the pre-UA verdict equals the post-UA one. A pin fails if that invariance breaks.
- **Spec.** HTML §15.3.7 / Chromium `html.css`; css-cascade-4 §4.3 / §6.1; css-fonts-4 §2.5.
- **Rejected:** baking the face in the extractor for every runs-host heading. It is seam-heavy, edits 4 documents' IR, hands
  the face to inset-011's STACKED fallback (the wave-40 WORSE case), and leaves the runtime gap for every non-WPT heading.
- **own:**
  - NEW `runtimes/compose/src/main/java/com/styleconverter/runtime/typography/UAElementFontRule.kt` and `UAHeadingFoldGate.kt`,
    with their JVM tests NEW `runtimes/compose/src/test/java/com/styleconverter/runtime/typography/UAElementFontRuleTest.kt`
    and `UAHeadingFoldGateTest.kt` — these two files only, not the directory (it holds earlier waves' tests, and L3's
    `typography/wrapping/PreBreakPipelineTest.kt` sits under it; fix round 2, R2-N8);
  - NEW `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/UAHeadingFoldGate.swift` and
    `Tests/StyleConverterRuntimeTests/UAHeadingFoldGateTests.swift`;
  - `StyleEngine/typography/UAElementFontRule.swift` (docs only; 274 lines at HEAD, so the doc edits keep it under the
    ~300-line split line or the re-dated table moves to the lane note: fix round 2, N7) and `UAElementFontRuleTests.swift` (additions);
  - `tools/titan/results/wave54-ua-heading-face/`.
- **Seams:** `ComponentRenderer.kt` → `seam-1.patch` (U1-android, :1141); `ComponentRenderer.swift` → `seam-2.patch`
  (U2-ios, :343-356).
- **Read-only:** `InlineRunFold.kt`, `InlineRunFlow.swift`, `InlineSpanRing.{kt,swift}`, `MonospaceUAFontSize.{kt,swift}`,
  `UaBlockMarginFontBasis.kt`; not `typography/wrapping/**` (L3), `lists/**`, `table/**` (L2) or `layout/position/**` (L4).
- **May run:** `--tests '*UAElementFontRule*' --tests '*UAHeadingFoldGate*' --tests '*InlineRunFoldTest'`; Catalyst
  `-only-testing:StyleConverterRuntimeTests/UAElementFontRuleTests` and `UAHeadingFoldGateTests`.
- **Stop rule.** If the first JVM / Catalyst pins say the verbatim inset-005/-006/-014 hosts BAIL (not `Folded`), the
  005/006/014 rows are void and the lane stops at the GO-SMALL fallback, U1 leaf-only.
- **Window requests:** none.
- **Carrier set:**
  - android: block-in-inline-015-print, inset-005, -006, -014, text-decoration-color, subelements-003, counter-list-item,
    counter-reset-increment-overflow-underflow, backdrop-filter-border-radius-change, -corner-shape-change;
  - ios: inset-005, -006, -014;
  - web: none (the harness renders a real `<h1>`).
  - `block-in-inline-015-print` WEB is L6's carrier (§4).
- **Unit pins:**
  - `UAElementFontRuleTest`:
    - the block-in-inline h1 → 32 / 700;
    - inset-001's h1 → the SAME list instance. Mutation: drop the own-declaration guard;
    - inset-014 → 26. Mutation: base 16;
    - the text-decoration-color `sup` → 13.333;
    - untagged → identity.
  - `UAHeadingFoldGateTest` + Catalyst twin: inset-005/-006/-014 → applies (`Folded`); inset-011 → stands down (`Bailed
    member-prop:TextDecorationColor-divergence`); leaf → applies. Mutation: return the old `hasChildren` gate → 005 red.
- **Predictions** (13 rows):

  | cell | → predicted | confidence | gate floor · geometry | unit |
  |---|---|---|---|---|
  | `block-in-inline-015-print` android | f 0.9489 → **P ≈0.989** (the identical rule gives iOS P 0.9894 on the same IR) | HIGH | 0.97 · gating | U1-android |
  | `text-decoration-inset-005/-006` android | f 0.9 / 0.8993 → P ≈0.98 (anchors inset-001…004 android 0.984-0.9859) | MED | report | U1-android |
  | `text-decoration-inset-005/-006` ios | f 0.8995 / 0.8987 → P ≈0.965 (anchors ios 0.9634-0.9707) | MED | report | U2-ios |
  | `text-decoration-inset-014` ios / android | f 0.9238 / 0.923 → P (no monospace anchor; the probe decides) | MED-LOW | report | U2-ios / U1-android |
  | movers (android): text-decoration-color f 0.6164, counter-list-item f 0.7307, counter-reset-increment-overflow-underflow f 0.8699, subelements-003 f 0.9084, backdrop-filter-border-radius-change f 0.5977, -corner-shape-change f 0.4904 | move, stay f — look, never count. text-decoration-color is directed (≈0.67, the iOS anchor); the other five are **undirected** and exempt from rule 2 (fix round 1, S1) | LOW | report | U1-android |

- **Must not move (46 cells):** block-in-inline-015-print ios; inset-011 ios/android (the control, which must stay
  byte-identical); inset-001/002/003/004/007/008/009/012/013/015/016/024 natives; subelements-002 natives; the iOS of the six
  android-only movers (iOS already has the leaf rule); every web cell of the carrier documents except block-in-inline (L6).
- **Probe sections:** css-text-decor, css-break, css-lists, filter-effects.
- **Geometry verdicts:** `ua-heading-face.geometry.py <run>`: every row OK. block-in-inline android is gating; inset-011's
  rows print OK only while its native bands EQUAL the recorded ones. Fix round 1 (M2): block-in-inline also checks every
  band TOP within ±3 px of the ref, so a line-pitch / line-height error fails it (the skeptic's +2 px/line fake scored
  0.9709 ≥ the 0.97 floor and printed OK before; it prints `band top y107 vs ref y103` now). The iOS anchor (tops
  30/66/105/144 vs 29/65/103/142) stays OK, and every wave53-final line is byte-identical.
- **Lane note:** `tools/titan/results/wave54-ua-heading-face/_note.md`, with the docs hand-off: BACKLOG 0(g), the 4(b′)
  sentence, and `UAElementFontRule.swift`'s TWIN STATUS.

### L6 · web-tail

Briefs `web-root-separator.md` (GO, S; via `queue-scout-text-web.md` #2) and `web-out-of-flow-hyphen-box.md` (GO-SMALL, XS).
Queue: 5 (web tail, untracked web-only fails where the natives pass) and obligation 0(c). Two units, both web-only, with
disjoint files.

**Unit RS: a space between inline-level ROOTS on the web composed canvas.**
- **Targets** (wave53-final, web): `css-ui/box-sizing-007` f 0.9036, `-008` f 0.8943, `-022` f 0.9442,
  `css-position/position-absolute-semi-replaced-stretch-other` f 0.941.
- **DEGENERATE web passes:** box-sizing-010/011/014…019 P 0.9713 (pixel-identical captures), -020/021/024/025 P 0.9599,
  -013 P 0.9538, `semi-replaced-stretch-input` P 0.9596.
- **Picture** (`web-root-separator.geometry.out.txt`): the second inline-level root atom starts 5 px early on web (x146 vs
  the ref's x151; x187 vs x192), or two atoms merge into one run. The natives sit on the ref's x151 / x91.
- **Mechanism.**
  - The extractor stamps `meta.role "ws-after"` (`extract-fixture.mjs` :4883 / :4905).
  - The harness replays it only BETWEEN CHILDREN (`ComponentRenderer.tsx` `HARNESS_OPTIONS.renderChildSeparator` :1099).
  - The composed canvas maps ROOTS flush (`ComposedCaptureGallery.tsx` `flowRoots.map` :1070, `canvasRoots.map` :1079).
  - The natives pack root atoms with `ROOT_ATOM_GAP_PX` 4.5.
- **Change.**
  - NEW `apps/web-harness/src/ui/ComposedRootSeparator.ts` (≤ 120 lines): `interleaveRootSeparators(roots, render,
    container)`. It DECLINES for the table-body plan's `display: table` wrapper (CSS 2.1 §17.2.1).
  - seam-1 lifts the WWS branch of `renderChildSeparator` (:1108-1153; the widget rule at :1105-1107 stays in place) into an exported pure `wsAfterSeparator(prev, next,
    container)`, which `HARNESS_OPTIONS` calls, so the child-level DOM stays byte-identical.
  - The two gallery `.map` sites call the new file.
  - Rule 2b (the tree compiles without the seam): `ComposedRootSeparator.ts` takes the separator predicate as a PARAMETER
    and never imports `wsAfterSeparator`. The two gallery call sites that import it are delivered as patch-borne hunks
    INSIDE `seam-1.patch` (the wave-53 precedent of patch-borne files), so they enter the tree only with the lift.
- **Spec.** CSS 2.1 §16.6.1 and css-text-3 §4.1.1 (collapsible whitespace between inline-level boxes renders as one space);
  §9.2.2.1 (none between blocks; the predicate's inline-level gate).

**Unit W1: a paint-inert out-of-flow member no longer splits an `auto` word on web.**
- **Target.** `css-text/hyphens/hyphens-out-of-flow-002 web f 0.9411`, identical on all 13 runs checked since wave 35.
  Boxes 4 and 5 (`high<span abspos>abspos</span>way`) are 26-px one-line boxes that overflow the border, so boxes 6-7 sit
  40 px high (`web-out-of-flow-hyphen-box.geometry.out.txt`).
- **Mechanism.** The web runtime renders the wire's runs faithfully. With the member inside the word, the hyphenator on this
  host never sees `high|way`: CoreFoundation, asked both ways, reproduces the capture box by box
  (`web-out-of-flow-hyphen-box.cf-hyphenation.out.txt`). Which LAYER owns the split, Chromium-on-macOS (branch R) or the
  harness `<span>` wrappers (branch H), is decided by step 0.
- **Change (branch R, predicted).**
  - NEW `runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts` (≤ 100 lines): `isInertOutOfFlowMember` is a byte-for-byte
    restatement of Compose `InertOutOfFlowMember.admits`. `joinWordsAroundInertOutOfFlow` rewrites `[P, M, N]` → `[P+N, M]`
    when `hyphensAuto`, M is inert, and the split is mid-word.
  - `InlineRuns.ts`: an optional 4th argument `{hyphensAuto}` and `RunsPlacement.joinedOutOfFlowMembers` (default 0), the
    web twin of `Folded.droppedOutOfFlowMembers`. The deviation from spec 03 §4.1 rule 1 is counted, never silent.
  - `NodeRenderer.ts:339`: one line passing `{hyphensAuto: styles.hyphens === 'auto'}`.
  - Under **branch H** the runtime files are NOT touched. seam-2 changes `renderText` (:991) instead, for the same 4 hosts.
  - W1-u (ungated) is not taken: L6 owns `NodeRenderer.ts`.
- **Spec.** css-text-3 §5.1 (out-of-flow elements introduce no soft wrap opportunity; the test asserts "no effect on
  automatic hyphenation"), §5.4; CSS 2.1 §10.3.7 / §10.6.4.

**Lane facts.**
- **own:**
  - NEW `apps/web-harness/src/ui/ComposedRootSeparator.ts` and `apps/web-harness/src/ui/ComposedRootSeparator.test.ts`;
  - `apps/web-harness/src/ui/ComposedCaptureGallery.tsx` (the two `.map` call sites only, delivered inside `seam-1.patch`;
    a 1267-line size exception, 0(d));
  - NEW `runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts` and `runtimes/web/tests/renderer/InertOutOfFlowWordJoin.test.tsx`;
  - `runtimes/web/src/renderer/InlineRuns.ts`, `runtimes/web/src/renderer/NodeRenderer.ts` (one line);
  - `tools/titan/results/wave54-web-tail/`: the step-0 probe script, its output and the notes.
- **Seams:** `ComponentRenderer.tsx` → `seam-1.patch` (RS); `seam-2.patch` (W1, branch H only).
- **Not touched:** every native file, `extract-fixture.mjs`, `LabelChrome.tsx`, `CanvasTableBody.ts` (read: its `table`
  plan is the decline input).
- **May run:**
  - `npm -w apps/web-harness run test -- ComposedRootSeparator` and the existing separator tests;
  - `(cd runtimes/web && npx vitest run tests/renderer/InertOutOfFlowWordJoin.test.tsx tests/renderer/InlineRuns.test.tsx)`;
  - the brief replays.
- **ORCHESTRATOR WINDOW REQUESTS:** **[W-L6] step 0, the CDP probe** (Chromium, ~1 min; brief §5, variants V0-V6; the lane
  writes `wave54-web-tail/step0-probe.mjs` FIRST and requests it).
  - Pre-registered: V1 = 26, V3 = 46, V5 = 46. Predicted (MED): V6 boxes 4/5 = 26 and V0 = 26, i.e. branch R.
  - The decision table is `lanes.L6-web-tail.step0.decision`.
  - **V3 ≠ 46 stops W1:** its row is withdrawn and RS proceeds alone. The withdrawal is MECHANICAL (fix round 2, R2-S1):
    the orchestrator enters `{'lane': 'L6-web-tail', 'unit': 'W1', 'run': 'step0', 'rule': 'step 0: V3 != 46, W1 not built'}`
    in `plan-build.py` `REVERTED` and regenerates, which withdraws the -002 web row (gating 34 → 33), its geometry key and its
    carrier (dry run: `fix-r2/hooks/w1/`).
- **Carrier set** (web only):
  - RS: the 27 documents of `web-root-separator.census.out.txt` (26 scored + `overlay-button-appearance`).
  - W1: `hyphens-out-of-flow-002`.
- **Unit pins:**
  - **RS:** the vitest on verbatim box-sizing-007 IR: 19 separators, none after the body-root or the `<p>`; a table-body
    container → none; a call-equivalence test (`HARNESS_OPTIONS.renderChildSeparator` ≡ `wsAfterSeparator` on the existing
    separator fixtures). Mutations: drop the `isWsAfterMarked` gate (a flush-authored pair gets a separator → red); drop the
    table-body decline (red).
  - **W1 (a)-(g)** on verbatim wire (brief §8):
    - (a) box 4 → `[text "highway", child]`, joined 1;
    - (b) boxes 3/6;
    - (c) word-edge boxes 2/7 unchanged;
    - (d) -001 (`manual`) unchanged;
    - (e) a member with alpha 1 or a BackgroundColor unchanged;
    - (f) `renderToStaticMarkup` package default → one member after `highway`;
    - (g) the predicate over the 24 census members → the same 12 trues as Compose.

    Mutations: delete the join; require the dictionary point; drop the mid-word test; drop the `auto` gate; widen to "any
    abspos"; paint at the wire slot; predicate drift.
- **Predictions** (27 rows):

  | cell (web) | → predicted | confidence | gate floor · geometry | unit |
  |---|---|---|---|---|
  | `box-sizing-007` | f 0.9036 → **P 0.985** (replayed with the scorer's SSIM: 0.9852) | HIGH | 0.975 · gating | RS |
  | `box-sizing-008` | f 0.8943 → **P 0.974** (0.9736) | HIGH | 0.965 · gating | RS |
  | `box-sizing-022` | f 0.9442 → **P 0.970** (0.9704) | MED-HIGH | 0.96 · gating | RS |
  | `semi-replaced-stretch-other` | f 0.941 → P 0.967 (the space moves the next abspos box's static position, as in the ref) | MED | report | RS |
  | box-sizing-010/011/014…019 · 020/021/024/025 · 013 · `semi-replaced-stretch-input` | P 0.9713 / 0.9599 / 0.9538 / 0.9596 DEGENERATE → ≈0.982 / 0.982 / 0.970 / 0.966 | MED | report | RS |
  | `scope-pseudo-element`, `display-flow-root-list-item-001` | f 0.9353 / 0.8003 → up / mover (**undirected**, exempt from rule 2) | LOW | report | RS |
  | at-risk passes: `static-inside-inline-block` P 0.9803, `block-in-inline-015-print` P 1, `appearance-auto-input-non-widget-001` P 0.9873, `attr-style-sharing-1` P 0.9939, `baseline-with-orthogonal-flow-001` P 0.9854, `box-sizing-009` P 0.9811 | stay or rise (block-in-inline: byte-identical expected) | MED | report; rules 1/2 bind | RS |
  | `hyphens-out-of-flow-002` | f 0.9411 → **P 1** (replay W1-exact: ssim 1, 0 mismatched px) | HIGH (R) / MED-HIGH (H) | 0.995 · gating | W1 |

  The residue that caps box-sizing below 1 is the lost `<strong>` (lossy `inline-run-merged`, the corpus convention, the
  same on all three platforms). It does not make a pass degenerate.
- **Must not move (68 cells):**
  - every native cell of the 26 RS documents, except block-in-inline android (L5);
  - `hyphens-out-of-flow-001` web, `hyphens-span-002` web, `hyphens-auto-control` web, `hyphens-auto-inline-010` web;
  - the web cells of the 10 tests whose 12 out-of-flow run members W1 refuses, except `semi-replaced-stretch-other` (RS's);
  - the natives of `hyphens-out-of-flow-001/-002`.
- **Probe sections:** css-ui, css-position, css-cascade, css-display, CSS2, css-break, css-values, css-writing-modes
  (RS radius); css-text, css-pseudo, css-tables (W1).
- **Geometry verdicts:**
  - `web-root-separator.geometry.py <run>`: every web row OK; box-sizing-007/-008/-022 gating. The native rows are controls:
    box-sizing-013 and the semi-replaced natives keep today's WRONG lines verbatim. Fix round 1: after the recorded row the
    probe checks the second-atom edge on EVERY ref atom row band (S2: a fix on the probed band only — 1 of 10 on 007, 1 of 3
    on 008 — prints WRONG), and it carries one key for each of box-sizing-011, 014-019, 020, 021, 024, 025 (S3; web
    report rows WRONG today, natives controls OK).
  - `web-out-of-flow-hyphen-box.geometry.py <run>`: `hyphens/hyphens-out-of-flow-002 web  heights [46, 46, 46, 46, 46, 46,
    46] … → GEOMETRY OK` (gating), with every other row, the `hyphens-span-002` anchor included, still OK.
- **Lane note:** `tools/titan/results/wave54-web-tail/_note.md`, with the docs hand-offs: a renderer-latitude paragraph for
  `schema/spec/03-children.md` §4.1 covering both runtimes; the inherited-`hyphens` reach limit; BACKLOG 5 / 0(c).

### L7 · label-chrome

Brief `label-chrome-all-reset.md`. Queue: obligation 0(e), **whose premise the brief falsified**: the `all` reset reaches
the chrome on no platform.
- The three unlabelled canvases are exactly the ones the chrome contract (`docs/DYNAMIC_CAPTURE.md` §5 "When") says get
  none: 001 and 003 are containers, 005 is a text root.
- Label drawn ⇔ label-due by fixture structure holds on 18/18 (`label-chrome-all-reset.geometry.out.txt`).
- Label drawn ⇔ "the root declares no `all`" holds on only 3/18.

**The defect is in the ORACLE:** `tools/visual/label-chrome-tripwire.test.mjs` derives the glyph set P from the file name
for every stem, and has no clause for the contract's "iff".

- **Targets** (no corpus cell; WPT mode draws no chrome):
  - the tripwire on the 77fe41e8 bytes: 001 RED (i) 437/437, 003 **369/369** and 005 **293/293** ×3 platforms (replay R0).
    The record's "437/437 for all three" is corrected;
  - the fixture net's all-then-color child, today `exit 0 … (gate-only: no committed baseline)`.
- **Change.**
  - **U1** (tooling, device-free):
    - NEW `tools/visual/label-chrome-exempt.json`: 001/003 container, 005 text, fixture
      `fixtures/combinations/all-then-color.json`;
    - the tripwire (a) verifies every manifest entry STRUCTURALLY: the stem has a committed triplet, the named fixture has a
      node of that name, and every such node is a container / text root matching `why`. A leaf is "a leaf, which IS
      labelled" → red;
    - (b) a verified exempt stem runs `checkTriplet(pngs, '')` (P = ∅; clause (ii) band byte-identity) plus NEW clause
      (iv): per platform, NOT every pixel of the name's would-be label is non-ground;
    - (c) synthetic controls;
    - (d) a HINT on a red (i) stem whose P is fully ground ×3: a message, never a pass;
    - optionally, NEW `tools/visual/label-chrome-check.mjs` (the pure checker, so replays import it without registering
      node:test cases). Size: the tripwire is 209 lines at HEAD, so U1's new logic belongs in this file whenever adding it
      inline would take the test past the ~300-line split line (fix round 2, N7).
  - **U2-seed** (a device step on the CLOSING tree, §8 step 10):
    - `UPDATE_BASELINE=1 ./test-all.sh fixtures/combinations/all-then-color.json`, then LOOK at the six canvases ×3 (labels
      on 000/002/004 only);
    - `label-chrome-all-reset.geometry.py baseline` → 18 OK;
    - `cmp` against `label-chrome-all-reset.seeded-77fe41e8/` (any difference is named and looked at);
    - the tripwire, then the WHOLE tooling suite green on the ship tree (the step wave 53 skipped);
    - the orchestrator's doc lines in the same commit.
- **Rejected:** (R-a) "fix the chrome's isolation": there is nothing to isolate. (R-b) Change the contract: a user-level
  decision (§5). (R-c) Infer the exemption from stem names: ambiguous on `000_Sizing_Fixed`. (R-d) Infer it from pixels:
  circular.
- **own:** `tools/visual/label-chrome-tripwire.test.mjs`, NEW `tools/visual/label-chrome-exempt.json`, optional NEW
  `tools/visual/label-chrome-check.mjs` (U1); the 18 `tools/visual/baseline/{Android,iOS,web}__{000_ATC_AllThenProps,
  001_ATC_PropsThenAll_InGreenParent,002_reset,003_ATC_InitialUnderRedParent,004_span,005_ATC_DirectionSurvives}.png` —
  exactly these names (`label-chrome-all-reset.seeded-77fe41e8/`); the glob `__00{0..5}_*.png` also matches 66 committed
  baselines of four other fixtures (fix round 2, N3) — (U2-seed);
  `tools/titan/results/wave54-label-chrome/`.
- **Seams:** none.
- **Read-only:** the three drawers and predicates (`CaptureGallery.tsx`, `FixtureCanvas.tsx`, `LabelChrome.tsx`,
  `ScreenshotCaptureScreen.kt` `CaptureCanvas`, `HarnessLabelChrome.swift`, `CaptureCanvas.swift`), the runtimes' `all`
  code, the fixture.
  - The optional Android pin U3 is OUT, although L2 owns `ScreenshotCaptureScreen.kt` (§9 D20).
- **May run:** `node --test tools/visual/label-chrome-tripwire.test.mjs`; the brief's replays (`label-chrome-all-reset.replay.mjs`)
  and probe in both modes.
- **ORCHESTRATOR WINDOW REQUESTS:** **[W-L7] the seeding**, on the closing tree after the probe decisions (§8 step 10).
- **Carrier set:** none in the corpus.
- **Unit pins:** replays R0-R3, executed by the brief. The lane re-executes R1 (6/6 green, 3 entries verified), R2
  (mutations m1-m5 red) and R3 (130/130 verdict objects identical) through the committed test file, plus the synthetic
  controls (c).
- **Predictions** (non-corpus; `expectations.json` notes):
  - T1-T3 RED → green (HIGH);
  - T4 green with 224 / 80 / 65 glyph px (HIGH);
  - T5 130/130 identical verdicts (HIGH);
  - m1-m5 red (HIGH);
  - the fixture net `exit 0` with `✓ no regressions vs baseline (18 platform-comparisons ran)` (MED-HIGH, floor exit 0);
  - the fresh seed byte-identical to 77fe41e8 on 18/18 (MED).
  - Corpus: 0 / 0 / 0 attributable to this lane (HIGH).
- **Must not move (33 cells):** the eleven corpus documents that carry `All` (census (D)) ×3: css-cascade all-prop ×7,
  css-display display-contents-button / -details / -fieldset, css-ui appearance-revert-001.tentative.
- **Order (R1 of the brief):** PNGs without U1 recreate the wave-53 red in CI `test-tooling`, so U2-seed is reverted before
  U1, never after.
- **Lane note:** `tools/titan/results/wave54-label-chrome/_note.md`, with the docs hand-offs listed in `revertUnits.U2-seed`.

## 3. Seam-hunk registry

| seam file | hunk (anchor at 7cce3b22) | lane · unit | patch | note |
|---|---|---|---|---|
| `runtimes/compose/…/core/renderer/ComponentRenderer.kt` | `:1141` wrap `ListStyleUaRule.apply(…)` in `UAElementFontRule.apply(…, standsDown = UAHeadingFoldGate.standsDown(…))` | L5 · U1-android | `wave54-ua-heading-face/seam-1.patch` | before `DynamicValueResolver` (:1206), so `ch`/`em` resolve against the UA face |
| | `:1934` `.childContainingBlock(effectiveProperties, containingBlock, wptCaptureMode = wptCaptureModeForStretch)` | L4 · CBB-android | `wave54-oof-layout/seam-1.patch` | ~800 lines below L5's; `wptCaptureModeForStretch` (:1251) is already a `remember` key there |
| | `:2653-2697` `TableApplier.Table(…)` call: `val chrome = TableCellHug.chrome(component, fabricatedDefault = <:2670 expression verbatim>)`; `fabricatedCellBorder = chrome.stroke, cellsHugContent = chrome.hug` | L2 · TB-android | `wave54-table-body-cell/seam-1.patch` | disjoint from :1925-1940 (L4) and :7072 (L3) |
| | `:7072` `PreBreakPipeline.preBreak(…, hyphenChar = HyphenateCharacterApplier.preBreakString(HyphenateCharacterExtractor.extract(properties)))` | L3 · U2-android | `wave54-hyphenate-character/seam-1.patch` | needs L3's new triplet in the same commit |
| `runtimes/swiftui/…/Renderer/ComponentRenderer.swift` | `:343-356` `mergedProperties(now:)`: `hasElementChildren:` → `UAHeadingFoldGate.standsDown(component:inFlowChildren:mergedPreUA:)` | L5 · U2-ios | `wave54-ua-heading-face/seam-2.patch` | `inFlowChildren` is :781 |
| | `:5011` `hyphenChar: textConfig.hyphenateCharacter ?? AutoHyphenation.defaultHyphenCharacter`; `:5026` `spentHyphen: textConfig.hyphenateCharacter` | L3 · U2-ios | `wave54-hyphenate-character/seam-2.patch` | ~4650 lines from L5's hunk; nil keeps every other run byte-identical |
| `apps/web-harness/src/sdui/ComponentRenderer.tsx` | `:1099-1154` (the WWS branch is :1108-1153) lift the WWS branch of `renderChildSeparator` into an exported pure `wsAfterSeparator(prev, next, container)`, called by `HARNESS_OPTIONS` | L6 · RS | `wave54-web-tail/seam-1.patch` | child-level DOM byte-identical (call-equivalence pin) |
| | `:991` `renderText`: a bare string for the pieces of a host whose runs carry a mid-word inert out-of-flow member | L6 · W1 | `wave54-web-tail/seam-2.patch` | **branch H only** (step 0); under R / H2 there is no seam-2 |
| `tools/titan/extract-fixture.mjs` | `buildNode` children loop (`:11706` region): re-arm `childLineCtx.hasInline` on interleaved non-whitespace `{text}` | L3 · U3 | `wave54-hyphenate-character/seam-3.patch` | the child-scope twin of `:11727-11733`; the only lane on this seam |
| | three anchors (wave54-S1 nit N3 / should-fix 7; the patch header names the same three): the new exported helper `brHostLineBoxPx` after `rootPropsWithFontShorthand` (`:10142`); the br-height rule `const brHeight = …` (`:11457-11459`): a line-start br in a host declaring `line-height` gets that line box; the `childLineCtx` seed (`:11706`) gains `hostLineBox`, a line inside U3's registered region that U3's hunk does not touch (both patch orders apply) | L3 · U3b | `wave54-hyphenate-character/seam-3b.patch` | a SEPARATE patch so U3b reverts alone |

**Totals.**
- Registered patches: 9, plus seam-2 if step 0 says H.
- Hunks: `ComponentRenderer.kt` 4 (four lanes), `.swift` 2 (L3, L5), `.tsx` 1-2 (L6), `extract-fixture.mjs` 2 (L3).
- No two hunks overlap. `integrate-seams.sh --dry-run` applies them in §4 order in a throwaway worktree; run it before
  `land-units.sh`.
- Each patch is cut against 7cce3b22 and applies with offsets. A patch that fails `git apply --check` on the tree holding
  the patches before it is RE-CUT by its lane (a fix-lane task), never hand-merged.
- The focused suites of the touched module are re-run after each applied patch.

## 4. Landing order (and the conflicts it resolves)

1. **Commit `tools/titan/results/wave54-plan/`**: the ten briefs with their censuses, replays and probes, `PLAN.md`,
   `watchlist.txt`, `expectations.json`, `plan-build.py`, `geometry-gate.py` and its two outputs, `snapshot-cells.mjs` and
   the two cell snapshots, `build-workflow.js`, `integrate-seams.sh`. Do it once `wave54-open` is scored and §10 (if any)
   is written.
2. **Builders** (§8 step 4) on the shared tree, then skeptics and fix lanes.
3. **Integration**: `land-units.sh` (written from the lanes' per-unit note sections) commits the 19 revert units in this
   order, each seam patch applied (`git apply --check` first) right before its unit's commit:
   1. **L7 U1**: tooling only; not executed or built by any gate.
   2. **L1 P**, then **L1 M′**: bake only; M′ after P, never alone.
   3. **L2 TB-android** (+ seam-1 kt).
   4. **L6 RS** (+ seam-1 tsx), then **L6 W1** (runtime files, or seam-2 tsx under branch H).
   5. **L5 U1-android** (+ seam-1 kt), then **L5 U2-ios** (+ seam-2 swift).
   6. **L4 OOF-android**, **OOF-ios**, **CBB-android** (+ seam-1 kt), **GAP-android**, **GAP-ios**.
   7. **L3 U1**, **U2-android** (+ seam-1 kt), **U2-ios** (+ seam-2 swift), **U3** (+ seam-3), **U3b** (+ seam-3b), LAST:
      - it is the only lane that changes the wire corpus-wide, through the converter (4 documents) and the extractor
        (18 documents), so the gate-flag wire differential [W-L3] runs on the fully landed tree;
      - its U3 must follow U1 + U2 (the degenerate trap, §2 L3).
4. **[W-L3] the gate-flag wire differential** over all 30 sections on the landed tree, against HEAD-before-landing.
   Content-changed must equal the union wire carriers exactly: **23 documents** (L1's 4, L3's 19). Renumbered must equal
   **15** (`css-counter-styles/cssom/*`, +6 after counter-suffix; M′). The three zero-padding selectors documents must be
   byte-identical.
5. **One sequential full sweep** (wave skill Phase 4): converter, web runtime, compose `:runtime:testDebugUnitTest
   --rerun-tasks`, android-harness `:app:testDebugUnitTest`, swiftui Catalyst, `npm -w apps/web-harness run test`, the
   tooling suite (with L7's tripwire), IR conformance; the ios-harness XCTest in a device-idle window (30/30). The counts
   move in every lane, so restamp README / CLAUDE.md / STATUS / tree READMEs, then `tools/visual/doc-staleness-check.sh`
   exit 0. That includes L3's Android `real` 15 → 16.

**Conflicts resolved (every one is asserted by `plan-build.py`, which prints the three excluded must-not-move lines):**
- **L2 ↔ L4: `css-contain/contain-content-004`.** It is in L2's D2 radius and is an L4 OOF M2 carrier.
  - Android is L4's carrier (moves, stays f), and L2's must-not-move excludes it. L2's chrome is keyed on the anonymous
    marker only, and 004 has no synthetic box.
  - iOS is must-not-move for both lanes (§9 D3).
  - L2 owns `ScreenshotCaptureScreen.kt`, whose root fold reads `hostActive`; L4 flips `hostActive` on 7 documents. Neither
    lane edits the read.
- **L3 ↔ L4: `filter-effects/backdrop-filter-clip-rect` and `-edge-clipping`.** They are L4 positioned-establisher CONTROLS
  and L3 U3 CAPTURE carriers: their stray br moves pixels on web and iOS.
  - They are L3's carriers.
  - L4's invariance claim moves to pins P1 (last row) and P3, plus the closing A/B.
  - `backdrop-filter-zero-size` stays L4's must-not-move. `-clip-rect-zoom` is unscored.
- **L1 ↔ L4: `css-anchor-position/anchor-center-safe-rtl`** (unscored). It is an L1 P android carrier and an L4 fixed
  control. L4's claim rests on pin P3 over its verbatim payload.
- **L5 ↔ L6: `css-break/block-in-inline-015-print`.** Android is L5's target; web is in L6 RS's 27-document radius, expected
  byte-identical. Each lane's must-not-move excludes the other's platform.
- **L6 RS ↔ L6 W1: `position-absolute-semi-replaced-stretch-other` web.** It is RS's target; W1's refusal of its member is
  pinned by vitest (g), not by the capture.
- **L1 ↔ L3: `css-text/hyphens/hyphenate-character-005`.** It is bidi-baked (L1 must-not-move ×3) and an L3 U1 wire carrier
  (its value decodes; `white-space: pre` takes no break). Its captures are must-not-move for both lanes.
- **Shared sections** (carriers disjoint, so the control attributes by lane):
  - css-text: L1, L3, L6;
  - CSS2: L2, L4, L6;
  - filter-effects: L3, L4, L5;
  - css-position: L4, L6;
  - css-lists: L5;
  - css-cascade: L6 (captures) and L3 (wire only: revert-layer-006 / revert-val-001 / -002, captures must-not-move).
- **Hot shared files.** `ComponentRenderer.kt` gets four hunks and `.swift` two (§3); `StyleBuilder.swift` belongs to L3
  alone. Compose `:runtime` and the SwiftUI package are compiled by four and three lanes respectively (§0 single-writer).

## 5. Not staffed this wave, and why

- **0(j)/(j′): composed-root float rows plus writing-mode static position** (`queue-scout-layout.md` §2 rank 4; NO-GO this
  wave).
  - `abs-pos-border-offset-001` ios f 0.4519 / android f 0.4508 and `-002` f 0.3384 / 0.3383 (wave53-final).
  - Packing the roots alone does not flip: the float-rows simulation scores 0.906 / 0.84, with 6 of 36 and 0 of 64 boxes
    ref-identical. The writing-mode static position is wrong as well.
  - Staged for wave 55 as one lane, with the per-box identity count as its oracle. Watched (§7).
- **0(b′) iOS twin:** `css-position/position-relative-002` ios P 0.9539 (red-ink) and `block-in-inline-001/-002/-003` ios
  f 0.9459. BACKLOG asks for a size-aware census first; no scout wrote one.
- **0(k) F4 / F5 / F6:** F4 and F5 are correctness-only (P ×3). F6's failing carriers fail on paragraph wrap and on iOS
  vertical-in-multicol, not on §7.3.1 (`queue-scout-layout.md` §2).
- **3(a)** (`<br clear>` in multicol, 12 DEGENERATE passes): it needs the extract-fixture seam AND a device-verified
  `MulticolFloatStripMeasure` rung; applied alone, "ink moved, not ink fixed".
- **3(b):** no cell to gain.
- **7(e²)** `flex-gap-decorations-006` is priced-and-declined (4 cells up, 27 exposed). **023**'s decorations are scripted and
  absent from the wire.
- **The Compose runtime defect under L1's M′** (rtl brief §11 item 2). `PositionedParentFlowSlot.mount` is gated on
  `CanvasRootHoist.LocalActive`, so a host-inactive ABSOLUTE/FIXED parent with ≥ 2 out-of-flow children stacks them in a
  Column.
  - It costs `selectors/dir-style-02a` android f 0.9425 its three dots.
  - Radius: 10 host-inactive stack-shape parents in 6 documents.
  - M′ routes around it. A runtime queue item with that census and the anchor-center-safe-rtl control; it needs a
    `ComponentRenderer.kt` seam.
  - Also: bidi-lines-002's orange `!` on the left on all three platforms (a bake-measurement defect), and counter-suffix's
    out-of-family rows (iOS Hebrew period order, the CJK marker 4 px left, SwiftUI ignoring `tabular-nums` on the run).
- **The general Compose table chrome** (compose-table brief §5): the demo stroke on declared tables (12 D1 documents, one
  being percentage-sizing-003's visible 396-px rim on a passing cell) and the first-cell block fill (8 D2 documents). Queued
  as "Compose table chrome (CSS 2.1 §17.6.1 / §17.5.2.2)" with the census as its starting radius. Also `s-11-1-1b-009`'s
  INLINE_TABLE → BLOCK fold.
- **4(f), the iOS space-less CJK one-line pin.** `hanging-punctuation-inline-001` ios P 0.9555 is a thin pass; radius 14
  documents. It is a seam pin in the wrapping neighbourhood L3 owns this wave, so it is queued rather than added to an M-sized
  five-unit lane.
- **0(m) `::first-letter`** (`first-letter-005` ×3): an L-size extractor-seam lane for ≤ 3 flips, plus the `&pound;`
  singleton.
- **4(c)** `block-ellipsis-032` android: two missed predictions; it needs a device probe first.
- **4(b)/(b′) residue:** `text-decoration-subelements-003` needs a Compose per-range decoration draw (L).
  `subelements-002 ios f 0.7975` is an iOS em-inset layout defect.
- **4(d)/(d′):** docs-only. **2(c¹)** armenian: the natives are unscored.
- **Web-only singletons** where the natives pass (queue under 5): `css-color/border-color-currentcolor` web f 0.9038,
  `css-sizing/aspect-ratio/abspos-016` web f 0.9043, `css-tables/colspan-004` web f 0.999,
  `css-overflow/line-clamp/discard/discard-multicol-003` web f 0.8901.
- **New observations, untraced:** a negative block margin does not pull later siblings on either native
  (`css-flexbox/align-self-003/-009` ios P 0.9774 / android P 0.9767, red-ink); the iOS rtl abspos static position is 10 px
  left (`abspos-autopos-*-rtl` ios P 0.9974); `grid-abspos-staticpos-align-self-safe-001`'s `safe end` box is 6 px off.
- **Out of the briefs' own scope:**
  - hyphenate-character: the lost `<b>` (the corpus convention), `-002` android's Minikin dictionary hyphen (a logged
    wall), `-005` natives (the bake would have to bake ` ـ`), the spent-hyphen generalisation to U+2010, and
    `TextStyleApplier.extractHyphenateCharacter`'s dead DEFAULT U+00AD;
  - web-out-of-flow-hyphen-box: W2 (264 hosts, the harness `<span>` calibration) and the general element-boundary-in-an-`auto`-word
    case (0 other members);
  - oof-containing-block: paint-containment clipping; compose-wpt-content-box-cb's F2 (0 carriers);
  - label-chrome: the contract change R-b (a user-level decision, NO-GO here); seeding the six other gate-only fixtures
    (44 exempt nodes, now unblocked by U1; a later wave); the Android `harnessShowsLabel` pin U3.
- **`css-view-transitions/column-span-during-transition-doesnt-skip` ×3** (0(ad)): still no brief. Watched.
- **`anchor-position-multicol-007` android:** a recorded wall, unchanged. Watched.
- **harness-hygiene T5** (`start_adb_detached` kills the adb server at every provision): Known-broken, unchanged.
- **0(d) size-rule splits:** not done (§9 D8).
- **Orchestrator doc hand-offs**, collected from the lane notes and pasted at ship:
  - re-true 0(a)(1): "one RUN height (+20 px); the third run never painted; cause `CanvasRootHoist.LocalActive` false →
    `PositionedParentFlowSlot.mount` not applied";
  - the `ListMarkerOutsideHang.swift:33-41` comment when M′ lands;
  - 0(b), 0(c), 0(e) (discharged with the corrected cause, plus correction lines under `wave53-gate/_note.md` and
    `docs/STATUS.md:2678`);
  - 0(g), 4(b′), 4(f);
  - the stale clause of obligation 0 (all-then-color has no committed baselines until U2-seed);
  - spec 03 §4.1 renderer latitude;
  - coverage `real` 15 → 16;
  - the 0(l″) table-chrome item;
  - BACKLOG 5's web singletons.

## 6. Pre-registered closing-gate expectations (in the form `adjudicate.mjs` checks)

Written BEFORE any lane lands. The orchestrator copies `tools/titan/results/wave53-gate/adjudicate.mjs` and
`control-check.mjs` to `tools/titan/results/wave54-gate/`. The ONLY code change in `control-check.mjs` is the expectations
path, to `tools/titan/results/wave54-plan/expectations.json` (plus run-id strings in messages). `adjudicate.mjs` also gained,
at fix round 3, its input guards and its R6 retirement read (R3-S1 / R3-S2, below; calibrated by `adjudicateCalibrations`).
A changed check is calibrated before it is trusted (`expectations.json` `controlCalibrations`):
- `wave53-open → wave53-final` with the WAVE-53 expectations reproduces the wave-53 closing control verdict byte-for-byte;
- `wave53-final → wave54-open` with the wave-54 expectations HOLDS (0 changed, 0 content-changed, 0 renumbered);
- `wave53-open → wave53-final` with the wave-54 expectations FAILS with leaks (wave 53's landed changes are not wave-54
  carriers);
- `--lane L7-label-chrome` (an empty carrier set) holds on the identity pair and fails on the wave-53 pair.

Same host as wave 53, so web capture is byte-deterministic run to run (BACKLOG "Wave 53 lessons"); the "re-encoded" class
stays unused.

**Score of record** (restated at fix round 1, M1): the adjudicated JSON is produced with **`--movers 0`**:
`node tools/titan/score-gate.mjs wave54-open wave54-final --watch tools/titan/results/wave54-plan/watchlist.txt --movers 0 --json tools/titan/results/wave54-gate/score-final.json > tools/titan/results/wave54-gate/score-final.movers0.txt`,
then `python3 tools/titan/results/wave54-plan/geometry-gate.py wave54-final --base wave54-open --json tools/titan/results/wave54-gate/geometry-final.json > tools/titan/results/wave54-gate/geometry-final.out.txt`
(`geometryGate.closingCmd`; R6 is its exit), then
`node tools/titan/results/wave54-gate/adjudicate.mjs tools/titan/results/wave54-gate/score-final.json --geometry tools/titan/results/wave54-gate/geometry-final.json`
(`expectations.json` `adjudicate`; fix round 3).
- **Which JSON each reader reads** (fix round 3, R3-S1; `expectations.json` `closingGateFiles`, the one statement the
  generator, this section and §8 steps 11 / 13 share):

  | file | written by | read by | never read by |
  |---|---|---|---|
  | `wave54-gate/score-final.json` (`--movers 0`) | `scoreOfRecord` | `adjudicate.mjs` (R1–R5, the R6 read-out) | `make-corpus.mjs` |
  | `wave54-gate/score-final.movers0005.json` (`--movers 0.005`) | `scoreReadout` | `make-corpus.mjs` (`corpusSnapshotSource`), the PR mover list | `adjudicate.mjs` (exit 2) |
  | `wave54-gate/geometry-final.json` | `geometryGate.closingCmd` | R6 (the command's exit); `adjudicate.mjs --geometry` (degenerate retirement only) | — |

- **adjudicate.mjs refuses what it cannot read** (fix round 3, R3-S1; the two guards `probe-readout.mjs` already had):
  exit 2 `ORDER` when the record's `prev` is not `--base` (default `wave54-open`); exit 2 `NOT A --movers 0 RECORD`
  when gained + lost + movers + unmeasured-now differs from the cells scored on `prev` (or gained + lost + movers +
  newly-measured from those on `cur`), which is the case for `score-final.movers0005.json`; exit 2 `GEOMETRY PAIR` when the
  `--geometry` JSON's run / base are not the record's cur / prev. Before the fix a 0.005 record with three units reverted
  printed `R1–R5 hold`, exit 0, over a −0.003 must-not-move move (plan-skeptic round 3); it now exits 2.
- Why: `score-gate.mjs:125` lists a same-verdict cell only when |Δ| ≥ the threshold, and `adjudicate.mjs` R4 reads a cell
  absent from every list as "did not move". With 0.005 it failed a closing gate in which every prediction came true: the
  four P→P gating rows predicted below 0.005 (autopos-{htb,vlr,vrl}-ltr android Δ0.0001, s-11-1-1b-006 android Δ0.0039)
  and counter-suffix iOS landing on its floor (Δ0.0048). adjudicate's R5 could not see a must-not-move move in (0.002, 0.005).
- With `--movers 0` every scored cell is in a list, so adjudicate's R4 reads each gating cell's measured score against its
  floor and adjudicate's R5 applies its own 0.002 rule to every must-not-move cell. (adjudicate.mjs numbers its own rules R1–R6:
  its R1–R3 are the table's R1–R3; its R4 is this paragraph's gating-floor read, which has no row in the table below; its
  R5 is the table's R7; its R6 is the DEGENERATE clause of the table's R5. The table keeps the plan's own R1–R9.)
- The three autopos-ltr android floors (0.995) sit below today's 0.9966, so adjudicate's R4 is vacuous on them: **their
  gate is R6 alone** (the CBB-android geometry keys), said here rather than implied.
- The human-readable mover list for the PR is the same command with `--movers 0.005` and
  `--json tools/titan/results/wave54-gate/score-final.movers0005.json` (`score-final.txt`, `expectations.json`
  `scoreReadout`); it adjudicates nothing. **It is the corpus snapshot's source** (§8 step 13, `corpusSnapshotSource`):
  `make-corpus.mjs` publishes `movers: record.movers.length` under a hardcoded `--movers 0.005` scorer string, and the
  `--movers 0` record lists every same-verdict cell (4096 "movers" on the identity pair) (fix round 2, R2-S3).
- Calibrated (`expectations.json` `adjudicateCalibrations`, "Fix pass (round 1)"; re-run at fix round 2 on the regenerated
  JSON with the string-fixed adjudicate.mjs): all predictions met → adjudicate R1–R5 hold; counter-suffix iOS at exactly
  0.985 → hold; one must-not-move cell +0.003 → adjudicate R5 FAIL (with the old threshold the same picture shows R5
  moved 0); the identity pair `wave53-final → wave54-open` → adjudicate R4 FAIL, missed 31 (34 minus the 3 vacuous
  autopos floors). **Re-run at fix round 3** with the guarded adjudicate (the wave53-final-based inputs take
  `--base wave53-final`): the same four verdicts, plus every thresholded input → exit 2 and the R6 retirement read
  (`adjudicateCalibrations`, nine entries, outputs under `fix-r3/`).

| rule | expectation | on failure |
|---|---|---|
| **R1 lost** | **lost = ∅** against `wave54-open` (no instrument change). | Stops the ship until the cause is found in the picture AND the wire document; the lane is reverted or fixed, then its probe sections are re-run. Never ledgered, never excused by a re-run. |
| **R2 denominator** | unmeasured-now = 0 and newly measured = 0 (no `scoreExcluded`, test-list or ref change). | An unregistered instrument change: find it before reading any gain. |
| **R3 completeness** | 30 sections, every column 48/48/48 (css-cascade 43/43/43). | Exit-7 recipe; never `SKIP_<P>`. |
| **R4 capture control** | `control-check` (wave-54 copy) `wave54-open wave54-final` with the union carrier set, **web 44 · iOS 27 · Android 51** captures, minus every carrier withdrawn by `probeDecisions`. Every other composed capture is decoded-pixel identical. Per-lane counts are printed. | A leak is a finding: bisect by commit (revert rules 5, 6). |
| **R4b wire control** | Every `per-test-ir/*.json` byte-identical to `wave54-open` except the **23** wire carriers (L1 4, L3 19) and the **15** "renumbered" `css-counter-styles/cssom/*` documents (+6 after counter-suffix, M′). The three zero-padding selectors documents are identical. | A changed document outside them is an extraction or converter leak. Stop. |
| **R5 gains** | Every gained cell is a carrier with a prediction row (`lanes.*.predictions`; the LOW "possible flip" rows count as listed, never as claimed). Every gain is PNG-checked against the frozen ref before it is written as a pass. A gain on `hyphenate-character-001/-003/-004` is DEGENERATE by construction unless `degenerateRetirement` holds for that cell; `adjudicate.mjs` reads it (`degenerateRetirementCheck`: the cell's units on the tree, and its `hyphenate-character.geometry.py` row PASS in `geometry-final.json`) and prints the cell RETIRED (a gain) or DEGENERATE with the reason; with no `--geometry` nothing is retired (fix round 3, R3-S2). | A gain outside the list is looked at and explained in the PR, never claimed unseen. |
| **R6 picture-correctness** | `python3 tools/titan/results/wave54-plan/geometry-gate.py wave54-final --base wave54-open` exits 0: **every gating key of a unit still on the tree** PASS, no probe self-check failure. A gating key that is UNMEASURED (capture MISSING or no line) is not a pass: the gate exits 3 (fix round 2, R2-S2), so "exit 0" is "every gating key PASS, UNMEASURED-gating 0". Keys of a unit reverted at a probe print `WITHDRAWN` (`plan-build.py` `REVERTED` demotes them; the gate exits 2 on a stale JSON that still gates them). Every report key is read and labelled; every control key is expected PASS (a FAIL is diagnosed through R4/R7). **Nine control keys sit on carriers** (U3's eight block-ellipsis-002/-004/-005/-006 native freeze keys, RS's `block-in-inline-015-print web`), where R4 / R7 cannot see them: at the closing gate a control FAIL there is gated by nothing automatic, so the cell review reads every control FAIL and the PR names it (fix round 3, R3-N4; at the probes revert rules 1 / 2 bind their scores). A §1 tally cell is counted only when its `pictureCorrectTally` key is OK. | A target whose gating key is not PASS stays with its old picture: DEGENERATE, as before. Say so; do not count it. |
| **R7 must-not-move** | Every must-not-move line of `watchlist.txt` prints `prev == cur` (`adjudicate` R5: within 0.002, never lost/gained; with the `--movers 0` JSON R5 reads every one of the 651 cells). R4 implies it for every capture outside the carrier sets. | A move is an R4 leak or a carrier mislabel. Diagnose it. |
| **R8 fixture net** | `BASELINE=1 ./test-all.sh --gate-set` exits 0 on all 9 gate fixtures. **all-then-color compares against its newly committed baselines** (`✓ no regressions vs baseline (18 platform-comparisons ran)`). | Exit-code recipes (wave skill Phase 5). |
| **R9 tooling on the ship tree** (new) | The full tooling suite (`node --test tools/visual/*.test.mjs tools/titan/*.test.mjs`) is green on the tree that ships, AFTER the seeding. The tripwire covers 136 stems. | The step wave 53 skipped (label-chrome brief U2.3): a red suite is fixed before the ship, never ledgered. |

**Expected totals** (if `wave54-open` = `wave53-final`):
- HIGH / MED-HIGH: **web 1236/1372 · iOS 1128/1362 · Android 1121/1362**, i.e. gained web 4 · iOS 3 · Android 6.
- The ceiling with MED and MED-LOW: 1241 / 1134 / 1125 (fix round 2, R2-S4; was 1239 / 1133 / 1125).
- Lost 0; unmeasured-now 0; newly measured 0.

**Revert rule 2 is a probe rule** (fix round 3, R3-N5). The closing gate enforces the gating floors (adjudicate R4) and
must-not-move (R5); it prints every other prediction row's measured value but does not re-read rule 2. A directed
non-gating row (e.g. block-ellipsis-004/-005/-006 ios / android, "byte-identical expected") that falls between
`wave54-probe` and `wave54-final` should not happen on this byte-deterministic host; if it does, the cell review names it
in the PR. It is not a ship-stopper by itself.

**The ring-fenced test** is reported plainly with its number. No code names it.

### The two device probes before the closing gate (probe-then-ship)

Both run on the INTEGRATED tree (after §4 step 5 and the S1 skeptic), after §8 step 7a. Both are read with the UNION carrier
set (BACKLOG "Wave 53 lessons").

**Stage 1, `wave54-pre`** (`expectations.json` `stage1Probe`):
`tools/titan/gate-driver.sh wave54-pre --sections CSS2,css-counter-styles,css-tables,css-text --skip-fixture-net`, about 10 min.
- It is the "one-section DEVICE probe of the lane tree" that BACKLOG 0(a) and the wave-53 lesson require for L1 (counter-suffix
  in css-counter-styles; `[P]` in css-text). It is also L2's Android device probe (CSS2 + css-tables).
- It **decides L1 (P, M′) and L2 (TB-android) only**, by `stage1Decision`:
  - `python3 geometry-gate.py wave54-pre --base wave54-open --lanes L1,L2` (exit 0 = every L1 / L2 gating key PASS; 1 a
    FAIL; 3 a gating key UNMEASURED — on a stage-1-shaped run every L1 / L2 gating key is measured, `fix-r2/post-S2.geogate-stage1-shaped.out.txt`);
  - `node tools/titan/score-gate.mjs wave54-open wave54-pre --watch tools/titan/results/wave54-plan/watchlist.txt --movers 0 --json tools/titan/results/wave54-gate/score-pre.json`,
    then **`node tools/titan/results/wave54-plan/probe-readout.mjs tools/titan/results/wave54-gate/score-pre.json --stage1`**
    (revert rules 1-3 for L1 / L2, every other lane's rows recorded; fix round 2, R2-M2);
  - `node tools/titan/results/wave54-gate/control-check.mjs wave54-open wave54-pre --sections CSS2,css-counter-styles,css-tables,css-text`;
  - `node tools/titan/results/wave52-gate/cells.mjs 'counter-suffix|bidi-lines|s-11-1-1b-006' wave54-open wave54-pre`;
  - OPEN counter-suffix ×3 and 006 android.
- Every other lane's cells in these four sections are recorded and decided at stage 2.
- A unit reverted here is entered in `plan-build.py` `REVERTED` (the JSON is regenerated, never edited) before stage 2. The
  regeneration withdraws its predictions, its carriers AND its gating geometry keys (`probeDecisions.reverted[].withdrawnGeometryKeys`),
  and DEMOTES every prediction that keeps other units but `requires` the reverted one (`demotedPredictions`: gating off,
  floor none, undirected; fix round 2, R2-S1). `plan-build.py` asserts that no gating prediction needs a reverted unit
  unless `RESTATE` re-registers it with its own `gating`, `floor` and `why`. The same mechanism carries L6's step-0
  decision: W1-not-built is entered as `{'unit': 'W1', 'run': 'step0'}` (§2 L6, §8 step 3).

**Stage 2, `wave54-probe`** (`expectations.json` `probeRun`):
`tools/titan/gate-driver.sh wave54-probe --sections CSS2,css-anchor-position,css-backgrounds,css-break,css-cascade,css-contain,css-counter-styles,css-display,css-flexbox,css-gaps,css-images,css-lists,css-masking,css-multicol,css-overflow,css-position,css-pseudo,css-tables,css-text,css-text-decor,css-transforms,css-ui,css-values,css-writing-modes,filter-effects,selectors --skip-fixture-net`.
That is 26 sections, about 65 min on a quiet host.
- Then, **base first in every command** (fix round 2, R2-M2 — `ab-diff.mjs` is `<excludeRun> <includeRun>` and prints
  Δ = include − exclude, so the order written here before printed Δ = open − probe and labelled every probe gain
  `flip P→f`: wave 53's own pair printed `background-attachment-margin-root-002` web `P 1 → f 0.339 Δ-0.661`,
  `fix-r2/pre-M2.abdiff-plan-order.wave53-probe.out.txt`; `expectations.json` `probeRun.readOut`):
  - `node tools/titan/results/wave52-gate/ab-diff.mjs wave54-open wave54-probe --threshold 0.002` (Δ = probe − open; the
    same pair of wave 53 prints that cell `flip f→P f 0.339 → P 1 Δ+0.661`, `fix-r2/post-M2.abdiff-fixed-order.wave53-probe.out.txt`);
  - **the floor reader:** `node tools/titan/score-gate.mjs wave54-open wave54-probe --watch tools/titan/results/wave54-plan/watchlist.txt --movers 0 --json tools/titan/results/wave54-gate/score-probe.json`,
    then `node tools/titan/results/wave54-plan/probe-readout.mjs tools/titan/results/wave54-gate/score-probe.json`. It reads
    revert rules 1, 2 and 3 over every carrier, every prediction row and every non-carrier cell of the probed sections,
    names each unit with its `revertOrder`, REFUSES a record whose `prev` is not `wave54-open` (exit 2) or that was written
    with a mover threshold (exit 2: an unlisted gating cell would read as "unchanged", the M1 hole), and exits 3 when a
    probed section is missing;
  - the R4 / R4b controls restricted to those sections (`control-check.mjs wave54-open wave54-probe --sections …`: rule 5);
  - `geometry-gate.py wave54-probe --base wave54-open` (rule 4; exit 3 on an UNMEASURED gating key).
- **Not probed, with the check that replaces each:**
  - css-color, css-grid, css-sizing: no carrier and no named control; the closing gate's R4/R7.
  - css-view-transitions: two L4 `fixedControls` (P ×3). Replaced by L4's skeptic P3 pure-walk census over all 26
    `fixedControls` payloads (JVM + Catalyst), then the closing R4/R7. The section runs 8-30 min under swap.

**What each lane must show at its probe** (a miss reverts the commit: revert rules 1-8). Every row also requires rules 1, 2 and
5 for every carrier cell of the lane: no P → f, no "up-or-stay" prediction row down by ≥ 0.002, no leak.

| lane | probe sections | required (a miss reverts the named unit) | read and labelled (MED and below; no floor, no geometry trigger) |
|---|---|---|---|
| L1 | **stage 1:** css-counter-styles, css-text; stage 2: + css-anchor-position, css-backgrounds, css-writing-modes, selectors | **P:** bidi-lines-001 android ≥ 0.955 and -002 android ≥ 0.975, both `[P] … → GEOMETRY OK`; counter-suffix android ≥ 0.970; web/iOS of bidi-lines-001/-002 and anchor-center-safe-rtl identical. **M′:** `[M]` ×3 OK with `rows 0-207 identical to wave54-open`; counter-suffix web ≥ 0.999, ios ≥ 0.985; lists-bakes counter-suffix web/android OK; css-counter-styles otherwise identical except the 15 renumbered | counter-suffix android's M′ magnitude (≈0.989, MED); counter-suffix native rows 3-6 / 5-6 (stay DEGENERATE) |
| L2 | **stage 1:** CSS2, css-tables; stage 2: + css-position, css-break, css-contain, css-display, css-values, css-writing-modes, selectors, css-text, css-backgrounds | **TB-android:** 006 android ≥ 0.996 AND both android geometry lines; css-tables android 48/48 identical; the 58 other Compose-table documents identical | — |
| L3 | css-text, css-overflow, filter-effects, css-cascade, css-masking, css-multicol | **U2-ios (+U1, U3):** 001 / 003 ios ≥ 0.965 with `GEOMETRY OK`. **U3:** block-ellipsis-002 web ≥ 0.995; -004 / -005 / -006 web ≥ 0.99; revert-layer-006 / revert-val-001 / -002 identical; [W-L3] = 19 documents. **U3b:** its `probeDecided` rule | 004 ios, the web / android 001-004 magnitudes and geometry, 002 ×3, limit-chars ios, the backdrop-filter / multicol / clip-path movers |
| L4 | css-position, css-contain, CSS2, filter-effects, css-transforms, css-flexbox, css-images, css-gaps, css-anchor-position, css-display, css-masking, css-pseudo | **OOF-<p>:** contain-content-003 android ≥ 0.99, -011 android ≥ 0.97, position-relative-004 / change-insets / static-fixed-inside-abspos ≥ 0.99 on that native, each with `GEOMETRY OK`; the fixed controls and `backdrop-filter-zero-size` identical; iOS contain-content-003/-004/-011 identical. **CBB-android:** clip-3 ≥ 0.96, clip / -2 / -4 ≥ 0.98, autopos ltr ×3 ≥ 0.995, all OK; cross-fade-target-alpha identical. **GAP-<p>:** 033 ≥ 0.99 + OK; every other css-gaps cell identical | backdrop-filter-containing-block ×2, position-relative-003 ×2, autopos rtl ×3, contain-content-004 android, 006 android |
| L5 | css-text-decor, css-break, css-lists, filter-effects | **U1-android:** block-in-inline android ≥ 0.97 + OK; inset-011 identical (control). **U2-ios:** inset-011 ios identical; block-in-inline ios identical | inset-005/-006/-014 ×2 (MED / MED-LOW) and the six android movers |
| L6 | css-ui, css-position, css-cascade, css-display, CSS2, css-break, css-values, css-writing-modes, css-text, css-pseudo, css-tables | **RS:** box-sizing-007 ≥ 0.975, -008 ≥ 0.965, -022 ≥ 0.96, each OK; every native cell of the 26 documents identical. **W1:** hyphens-out-of-flow-002 web ≥ 0.995 + OK; every other hyphens web cell identical | semi-replaced ×2, the 13 box-sizing P→P, the at-risk passes (rules 1/2 bind them) |
| L7 | — (no corpus) | U2-seed: the seed geometry 18 OK; tripwire green; R8 / R9 at the closing gate | the fresh seed's byte-identity with 77fe41e8 (MED) |

**Revert rule** (numeric; `expectations.json` `revertRule`). Read `wave54-open` → the probe (same host). The unit of revert is
the **commit**. A commit is reverted out of the integrated tree when ANY of these holds for a cell or capture it carries:
1. **Lost at the probe.** A carrier cell (any lane, any tier, predicted or not) is P on `wave54-open` and f on the probe. This
   is R1's empty lost list applied at the probe.
2. **Moved down.** Δ ≤ −0.002 on any prediction row whose `direction` is `up-or-stay`, at any tier. No prediction in this
   plan forecasts a fall. **Twenty-four rows predict a move whose sign is not pre-registered** (`direction: "undirected"`,
   each with its `directionWhy`): hyphenate-character-002 ios, hyphenate-limit-chars-001 ios, contain-content-004 android,
   flex-gap-decorations-006 android, counter-list-item / counter-reset-increment-overflow-underflow /
   text-decoration-subelements-003 / backdrop-filter-border-radius-change / -corner-shape-change android,
   display-flow-root-list-item-001 web, and (fix round 2, R2-S4: no replay is possible on them) backdrop-filter-clip-rect /
   -edge-clipping / -paint-order ×3, balance-grid-container ios / android, column-height-009 ×3. They are **exempt from
   rule 2**, so a coin flip cannot revert a commit that carries HIGH flips (OOF-android, U1-android, RS, U2-ios, U3). A
   fall on one is looked at against the ref and named with its cause in the probe read-out and the PR (`probe-readout.mjs`
   prints it as `READ`); it reverts nothing by itself. Rules 1 and 5 still bind them: eighteen are f today, so rule 1
   cannot fire on them; the six backdrop-filter web / iOS rows are P today, so a P → f there still reverts U3. The four
   U3-radius rows with a replay (clip-path-filter-order ×3, balance-grid-container web) stay directed. A row DEMOTED by a
   probe decision (R2-S1) becomes undirected too. Same-host noise is 0 on every platform (BACKLOG "Wave 53 lessons").
3. **Below floor.** A gating prediction (every HIGH / MED-HIGH row; 34) scores below its `floor`. Read by
   `probe-readout.mjs` over the `--movers 0` probe record (a gating cell that did not move is READ and fails; fix round 2,
   R2-M2). A row demoted by a probe decision has no floor (R2-S1).
4. **Wrong geometry.** A gating key of `geometry-gate.py` is not PASS (FAIL: exit 1, the runner names the unit;
   UNMEASURED: exit 3, re-run its section — never a pass; fix round 2, R2-S2).
5. **Leak.** A composed capture outside every carrier set differs in decoded pixels (R4), or a per-test IR document outside
   the wire carriers differs in bytes and is not "renumbered" (R4b). It is bisected to a commit, one re-probe of the leaked
   section per step; never guessed.
6. **The unit is the commit** (`lanes.*.revertUnits`; `plan-build.py` asserts that each lane's units cover its carriers
   exactly). A cell carried by several commits of one lane reverts them in `revertOrder` (latest first), re-probing its
   sections between steps. Lane dependencies:
   - M′ goes before P (M′ never without P);
   - U3b before U3;
   - U2-seed before U1.

   L3's geometry reasons name the unit: `glyph:` → U2-<platform> (U1 on web); `lines:` / `offset:` → U3b, then U3.
7. **Stage 1** decides L1 and L2 (`stage1Decision`).
8. **U3b is probe-decided** (`revertUnits.U3b.probeDecided`).

**Tier.** Confidence decides only whether a cell has a floor and a geometry trigger. A MED or lower cell that rises less than
predicted, or not at all, is read and labelled, never a trigger. Rules 1 and 5 apply to every row of every tier and rule 2
to every `up-or-stay` row of every tier, so "read and labelled" never covers a P → f, and covers a fall only on the
twenty-four undirected rows (and on rows a probe decision demoted). A score that does not move is a falsification wherever the floor sits above
today's value. **The closing gate runs only on a tree whose probes hold.**

## 7. The watchlist — `tools/titan/results/wave54-plan/watchlist.txt`

- **775 watch lines covering 781 distinct cells** (fix round 1: +2, S6), generated by `plan-build.py`. Each lane has a block of targets and movers
  and a block of must-not-move cells, followed by the ring-fenced line (report only) and a not-staffed block (§5: 0(j)/(j′),
  0(b′), the align-self pair, 4(f), 4(c), 0(m), 0(ad), the web singletons, and the recorded wall), so the closing gate reads
  them out.
- Every line carries the `.html` suffix and was asserted to match exactly ONE test under `score-gate.mjs` `watchCells`'
  substring rule.
- **There is no family line this wave:** the wave-53 line `css-text/hyphens/ web` would now cover carriers of L3 and L6.
- Verified during planning: `RUN=wave53-final WATCH=tools/titan/results/wave54-plan/watchlist.txt node
  tools/titan/results/wave52-plan/watchlist-check.mjs` → `watch-lines=775 matched-cells(distinct)=781 … unmatched 0`.
  Precondition before any gate read-out: the same with `RUN=wave54-open`.
- `plan-build.py` also asserts that no capture is an allowed carrier of two lanes, and that no lane's must-not-move cell is
  an allowed carrier of any lane.

## 8. Risks, and the order of operations after the opening gate

**Risks**
1. **Code first executed on device.** None of it can run on the JVM or Catalyst, so the probes are the evidence and the
   geometry rows gate it:
   - L1 M′'s root-owned runs on Compose (gated at stage 1);
   - L2's per-cell `widthAtMaxIntrinsic` inside a `Row` (stage 1);
   - L4's positioned-container Box hosting STATIC containers and the 7-document host-activation flip (stage 2; the
     paragraph pitch is watched in the PNGs);
   - L5's abspos `<h1>` wrap width and `16ch` against the new face;
   - L3's U+2022 face in the pinned monospace and the iOS re-wrap of a line that overflows by its own hyphen;
   - L6's W1 branch (step 0).
2. **Partial landings that are pre-registered losses or wrong pictures.** All are excluded by `revertOrder` and the §4 order:
   - M′ without P → counter-suffix android f ≈0.947;
   - U3 without U1 + U2 → a DEGENERATE -003 ios pass (0.9566);
   - L7's PNGs without U1 → CI `test-tooling` red.
3. **The seam files.** `ComponentRenderer.kt` takes four hunks from four lanes. Mitigation: §3 anchors, `integrate-seams.sh
   --dry-run`, `git apply --check`, re-cut, focused suites after each patch.
4. **Module contention.** Four lanes compile Compose `:runtime` and three compile the SwiftUI package on one tree.
   Mitigation: retry on locks, never touch a foreign file; the single-writer sweep is the count of record.
5. **A corpus-wide wire change (L3).** The converter parser and the extractor br rule are each one function, but they run on
   every document. [W-L3] must show exactly the 19 documents; R4b at both probes and the closing gate.
6. **The degenerate trap of L3.** The conservative `degenerateByConstruction` list plus `degenerateRetirement`. No gain on
   those cells is written as a fix without its geometry row.
7. **Host.** It is the same host as wave 53 (macOS 27.0.1 / Xcode 27.0 / CfT 151.0.7922.47; `wave54-gate/_note.md`). **No OS,
   Xcode, simulator-runtime, emulator-image or Chrome-for-Testing update may happen between `wave54-open` and
   `wave54-final`.** `sysctl vm.swapusage` is checked as well as load before every launch (obligation 0).
8. **Blast-radius under-reporting** (the dangerous direction). Every lane's census is re-derived by its skeptic with its own
   script (`build-workflow.js`), and the S1 combined-tree skeptic replays all seven.

**Order of operations**
1. **`wave54-open` finishes.**
   - Score it: `node tools/titan/score-gate.mjs wave53-final wave54-open --watch tools/titan/results/wave53-plan/watchlist.txt`.
     Expected: 0 / 0 / 0 / 0 / 0 and fixture net exit 0 ×9, with the all-then-color child gate-only.
   - Then:
     - `RUN=wave54-open … watchlist-check.mjs` with this plan's watchlist → `unmatched 0`;
     - per-test IR `wave53-final` → `wave54-open` byte-identical;
     - `node snapshot-cells.mjs wave54-open`, and compare it with `cells-wave53-final.json`;
     - copy `adjudicate.mjs` / `control-check.mjs` to `wave54-gate/` and run the four `controlCalibrations`.
   - **Any divergence writes the §10 addendum** (every affected prediction restated through `plan-build.py` `RESTATE`)
     BEFORE any lane lands.
2. **Commit `tools/titan/results/wave54-plan/`** (§4 step 1).
3. **[W-L6] step 0** can run as soon as L6 has written its probe script (Chromium, ~1 min, no device). L6's branch follows it.
   If step 0 says V3 ≠ 46, W1 is not built: enter `{'lane': 'L6-web-tail', 'unit': 'W1', 'run': 'step0', …}` in
   `plan-build.py` `REVERTED` and regenerate (R2-S1; it withdraws the -002 web row, its geometry key and its carrier).
4. **Builder lanes L1–L7:** `build-workflow.js` (one Workflow, model opus, file-driven, resumable through `args.done` /
   `args.only`) on the shared tree, with skeptics and fix lanes inside it. The orchestrator serves the window requests in
   device-idle slots: [W1] and [W2] (L1), [W-L6], and [W-L3] after landing. Lanes pause during any device step.
5. **Plan-level check before landing.** Every `<run> <section>/<test> <platform> <P|f> <ssim>` citation in this file and the
   lane notes is re-resolved against the manifests, and `plan-build.py` is re-run (its assertions are that check for
   `expectations.json`).
6. **Integration** (§4 step 3), then [W-L3], then the sweep (§4 step 5), then **S1, the combined-tree skeptic** (an adapted
   `s1-workflow.js`). S1 does the seam-hunk audit against §3, sweep counts against the doc tables, git hygiene, the pointer
   audit, a census replay per lane, and two mutations per lane. Fix lanes follow for its must-fix items.
7. **7a. Before EVERY device run** (stage 1, stage 2, the seeding, the closing gate, the A/B):
   `source tools/titan/own-processes.sh && kill_own_gradle_daemons "$PROJECT_ROOT" <every tree on a TREES: line>`. One
   multi-root call, never `./gradlew --stop` (`expectations.json` `beforeEveryDeviceRun`).
8. **Stage 1 `wave54-pre`** (4 sections, ~10 min), decided per §6 for L1 and L2 (`stage1Probe.readOut`: base first;
   `probe-readout.mjs … --stage1`). Reverts go into `plan-build.py` `REVERTED`, the JSON is regenerated (withdrawing and
   demoting what the revert touches), and the reverted sections are re-probed if anything else in them is to be read.
9. **Stage 2 `wave54-probe`** (26 sections, ~65 min), read per §6 (`probeRun.readOut`, base first): `ab-diff.mjs
   wave54-open wave54-probe`, the `--movers 0` score + `probe-readout.mjs` (rules 1-3), the controls, `geometry-gate.py`. Every
   commit that trips revert rules 1–8 is reverted, the U3b decision is taken, the reverted sections are re-probed, and
   `REVERTED` / `RESTATE` are updated and regenerated.
10. **Seeding** (L7 U2-seed, [W-L7]) on the tree the closing gate will measure: the seed, LOOK at the canvases, the seed
    geometry (18 OK), `cmp` with the 77fe41e8 copies, the tripwire, the full tooling suite; then commit the PNGs + doc lines.
11. **Closing gate:** `tools/titan/gate-driver.sh wave54-final` on a quiet host, in two launches (`--skip-fixture-net`; then
    `rm -f /tmp/titan-device-pool/provisioned-*` and `--skip-corpus`), each `run_in_background` with `timeout: 7200000`. Wait
    for the task notification; no polling.
    - Record the build hashes right after provisioning (BACKLOG "Wave 53 lessons").
    - Score against `wave54-open` with `scoreOfRecord` (→ `score-final.json`, `--movers 0`) and `scoreReadout`
      (→ `score-final.movers0005.json`); run `geometryGate.closingCmd` (→ `geometry-final.json`, R6 = its exit); then
      `adjudicate` = `adjudicate.mjs score-final.json --geometry geometry-final.json` (R1–R5 and the R6 retirement
      read-out; it exits 2 on the 0.005 JSON). Then R4 / R4b (`control-check.mjs`), R8, R9 (fix round 3: §6 "Which JSON
      each reader reads").
    - Run the cell review: every flipped cell and every P→P target looked at against the ref, with a second, adversarial
      reader for every DEGENERATE or HONEST_FAIL verdict.
12. **A/B arms** (after 7a; `expectations.json` `abArms`), each recording the installed `base.apk` sha1 / `.app` digest
    against the build. Each arm runs as `wave54-ab-<arm>` and is read **arm first** (`abRead`, fix round 3, R3-N6):
    `node tools/titan/results/wave52-gate/ab-diff.mjs wave54-ab-<arm> wave54-final --threshold 0.002`, so Δ = final − arm
    is the dropped unit's share and a gain it made prints `+`:
    - **drop-W1** (css-text, web): the -002 web flip is W1's alone;
    - **drop-M′** if M′ is on the closing tree (css-counter-styles): attributes counter-suffix android between P and M′;
    - **drop-U3b** if U3b stays (css-text): attributes the drift gain;
    - any lane that claims a further mechanism at the gate gets its own arm under the same hash rule.
13. **Ship** per wave skill Phase 6. The corpus snapshot gets `artifact` / `reproduce --run-id` = `wave54-final`, and its
    `<record.json>` (`make-corpus.mjs`) is **`tools/titan/results/wave54-gate/score-final.movers0005.json`** (the
    `scoreReadout` JSON, `corpusSnapshotSource`), never the `--movers 0` `score-final.json` (R2-S3). BACKLOG
    gets the §5 hand-offs and the obligations for wave 55.

## 9. Decisions taken against (or beyond) a brief

| # | brief | the brief said | the plan does | why |
|---|---|---|---|---|
| D1 | oof-containing-block, compose-wpt-content-box-cb, flex-zero-gap-rules | three lanes, or one lane with ranks 2/3 folded in as units; OOF as one unit | **one lane L4 with five revert units**: OOF split per native, GAP split per native | seven lanes is the cap of the 5–7 band. The per-native split is wider than the brief: each native has its own choke point and its own device risk, so a native that falsifies goes alone (the wave-53 L4 precedent) |
| D2 | compose-wpt-content-box-cb | F2 (abspos base = padding box) optional, 0 carriers | OUT, queued | no carrier; a pin without a picture adds radius without evidence |
| D3 | oof-containing-block | `contain-content-004` ios / android "moves, stays f" | **android carrier; ios must-not-move** | the Swift change touches FIXED hoisting only (`FixedHoist` :309 / :347 / :115). 004's only M2 box is ABSOLUTE, the brief's own reason for keeping 003/011 iOS must-not-move. If iOS moves, it is a leak to explain, not an allowance |
| D4 | web-out-of-flow-hyphen-box | "one small web-only lane, effort XS; it can ride with another web lane"; the lane runs step 0 itself | rides in **L6** with web-root-separator; **W1 gated** (not W1-u); step 0 is an **orchestrator window** | disjoint files; L6 owns `NodeRenderer.ts`, so the W1-u fallback's reason does not arise; Chromium is a window by §0 |
| D5 | hyphenate-character-compose | U3b "a separable hunk on seam 3", "the probe decides" | U3b is its **own patch (`seam-3b.patch`) and its own commit**, IN at stage 2, with a numeric keep rule (iOS floors 0.965, no `offset:`/`lines:` reason on the two gating iOS rows) | a probe decision needs a revertible unit and a rule written before the probe |
| D6 | hyphenate-character-compose | A/B arms drop-U1, drop-U2-compose, drop-U2-ios, drop-U3, ±U3b | **drop-U3b only** (if kept) | U2 is already per-platform commits. The cells are products of U1 × U2 × U3, which single drops cannot separate; the geometry reasons (`glyph:` / `lines:` / `offset:`) attribute the PICTURE instead. Replay `B` already measured "U3 alone" |
| D7 | label-chrome-all-reset | seed "after the opening gate frees the host, or as part of the closing fixture net" | seed **on the closing tree, before the closing gate** (§8 step 10) | seeding inside the net would compare the net against itself; seeding earlier would let a later lane's commit invalidate the PNGs |
| D8 | (BACKLOG 0(d)) | "split when a lane next owns each file" | **not split**: L1 (`bidi-bake.mjs`), L2 (`ScreenshotCaptureScreen.kt`), L6 (`ComposedCaptureGallery.tsx`) add call sites only | a split is a byte-identity refactor with its own proof. Inside a behaviour revert unit it makes the revert impure; 0(d) is restated for a dedicated refactor lane |
| D9 | rtl-marker-bake | (wave 53 carried a P-narrow fallback) | **no P-narrow fallback** | P is now its own unit, and its web/iOS invariance was CAPTURED at wave53-probe (decoded-pixel identical on all three P documents) |
| D10 | rtl-marker-bake | [W3] one-section lane-tree probe `wave54-rmb-probe` | **stage 1 `wave54-pre`** (css-counter-styles + css-text for L1, CSS2 + css-tables for L2) on the integrated tree, read with the union carrier set | lanes share one tree, so a probe of "L1 alone" would need the other lanes' edits out of the working tree; the BACKLOG lesson says to pre-register the union read instead |
| D11 | hyphenate-character-compose | `backdrop-filter-plus-filter` and `block-ellipsis-004/-005/-006` natives "must not move" / "expected byte-identical", MED | **carriers** with MED "stays / byte-identical expected" rows | a must-not-move cell must be invariant by construction (HIGH). A MED expectation is a carrier whose falls rules 1/2 still catch |
| D12 | hyphenate-character / oof-containing-block | both claim `backdrop-filter-clip-rect` / `-edge-clipping` | **L3 carriers**; L4's invariance on them moves to pins P1/P3 | the U3 stray br moves their pixels on web and iOS; disjoint carriers are what let the control attribute |
| D13 | ua-heading-face | `block-in-inline-015-print` web "must not move" | **L6 RS carrier** (expected byte-identical); L5 keeps iOS | it is in the RS 27-document radius |
| D14 | web-out-of-flow-hyphen-box | `semi-replaced-stretch-other` web must-not-move (a refused member) | **RS target**; the refusal is pinned by vitest (g) | the same capture cannot be a target of one unit and must-not-move for another |
| D15 | compose-table-body-cell | "if `contain-content-004` moves, A/B the two lanes separately" | no A/B; disjoint carriers (L4's) | L2's chrome is keyed on the anonymous marker, which 004 does not have; the JVM pin proves `chrome(004's table) = (stroke, false)` |
| D16 | compose-table-body-cell | a device probe "of the lane tree" before the closing gate | stage 1 (CSS2 + css-tables), integrated tree | as D10 |
| D17 | (BACKLOG 0(e)) | "fix the chrome's isolation from the component's cascade, THEN seed" | **the oracle is fixed** (L7 U1), then seed | the brief falsified the premise on 18/18 pictures; a lane that made labels appear would have changed the contract |
| D18 | rtl-marker-bake | the `ListMarkerOutsideHang.swift:33-41` comment re-true goes to the orchestrator's docs pass | as the brief (wave 53's D4 put it in the bake unit) | it keeps a runtime file out of a bake unit; the docs pass runs after the probe decides M′ |
| D19 | queue-scout-text-web | 4(f)'s iOS CJK one-line pin "queue it, or hand it to that lane" (L3) | queued (§5) | L3 is M effort with five units and a probe-decided arm; a pin outside its family raises its revert radius |
| D20 | label-chrome-all-reset | optional U3 (Android `harnessShowsLabel`) only "if a lane owns that file anyway" | OUT, although L2 owns `ScreenshotCaptureScreen.kt` | it buys no cell, and in L2's TB-android commit a revert would carry an unrelated harness change |
| D21 | (all) | each brief's own probe sections | the union of 26, plus a 4-section stage 1; css-color / css-grid / css-sizing / css-view-transitions not probed, each with its replacement (§6) | the wave-53 skeptic lesson: every narrowing needs a stated replacement |

## 10. Pre-registration addendum (orchestrator, 2026-10-08, after `wave54-open` and before any lane lands)

1. **No divergence.** `wave54-open` (30/30 sections on attempt 1, fixture net exit 0 ×9, all-then-color gate-only) scores
   **0 gained / 0 lost / 0 movers / 0 newly measured / 0 unmeasured-now** against `wave53-final` over 4096 cells — 0 movers even
   at |Δ| ≥ 0.00005 — and the byte-identity control is **1435 / 1435 identical on web, iOS and Android, wire 1435 / 1435**
   (`wave54-gate/_note.md`, `control-open.txt`). `snapshot-cells.mjs wave54-open` equals `cells-wave53-final.json` in `cells`
   and `totals`. **No prediction is restated**: every "from" value in §2 and `expectations.json` reads the same against
   `wave54-open`, and the lanes' verbatim pins (per-test IR of `wave53-final`) are byte-identical to `wave54-open`'s.
2. **The four `controlCalibrations` were run** (`wave54-gate/_note.md` "Plan checks"): C2 identity HOLDS 0/0/0; C3 the wave-53
   pair under wave-54 carriers FAILS (25 + 1 leaks); C4 `--lane L7-label-chrome` HOLDS on the identity pair and FAILS on the
   wave-53 pair; C1 the wave-53 original reproduces its closing verdict with identical capture totals. The watchlist over
   `wave54-open`: `unmatched 0`.
3. **Stage 1 (`wave54-pre`) on the INTEGRATED tree is ACCEPTED** as the reading of BACKLOG 0(a)(1)'s "device probe of the lane
   tree" (§9 D10): the rule's substance is a DEVICE measurement read by a GEOMETRY gate before any closing gate, and the
   control attributes by carrier (L1's and L2's carrier sets are disjoint from each other's and from the other lanes' in the
   four stage-1 sections). Two conditions, pre-registered: (a) a `GEOMETRY WRONG` line on an L1 gating key at stage 1 reverts
   M′ whatever any other lane did (rule 4 names the unit, not the tree); (b) if the stage-1 control shows a change on a
   stage-1 section stem that is NOT an L1/L2 carrier, the section is re-probed after bisecting the leak to its unit, and M′
   is not read until that is done.
4. **Device budget accepted**: stage 1 (~10 min) + stage 2 (26 sections, ~65 min) + the closing gate (two launches) + the
   pre-registered A/B arms, each after `beforeEveryDeviceRun` and each with its installed-build hash read right after
   `provision rc=0` of the CORPUS launch (the net launch's read races test-all's rebuild; the record of value is the corpus
   launch's plus a post-net re-read — `wave54-gate/build-hashes.txt`).
5. **Plan-skeptic round 2's two must-fix items (R2-M1 the flex-gap probe's row coverage; R2-M2 the inverted `ab-diff`
   order and the missing floor reader at stage 2) and its should-fix items are applied by a second fix pass BEFORE stage 1 is
   read; the builders start on the committed plan in parallel, since no lane's ownership, units or briefs change under them.**
   Probe decisions go in `plan-build.py` `REVERTED` and are recorded here with their run, rule and picture.
6. **The lanes' plan-side hand-offs (commit `25cbd1c2`, 2026-10-08, before landing) are restated here**, because that
   commit changed only `plan-build.py` and its generated files, and the prose of §1-§6 still carries the older numbers.
   The machine checks (`geometry-gate.py`, `adjudicate.mjs`, the watchlist) read `expectations.json` and were right all
   along; **where the prose above and this item disagree, this item and `expectations.json` win.**
   - **L4 CBB-android +3 captures:** the L4 builder's behavioural census found three px-inset stretch documents the static
     predicate missed: `css-position/position-absolute-semi-replaced-stretch-{button,input,other}` android (mover, MED,
     stays f; replay 0.9843 / 0.4555 / 0.4677). CBB-android's captures are 14 (§2 L4 said 11: the 10 cbborder carriers +
     `flex-gap-decorations-006`). L4 has 29 prediction rows (§2 said 26) and android carriers 24 (was 21).
   - **`contain-content-011` android** stays gating (floor 0.97 + the geometry key on the green square) and is listed in
     `stayDegenerateEvenIfPass`: every platform paints "25" where the test needs "17", so a P is never a fix.
   - **L5 is GO-SMALL:** U1-android only (leaf headings and rendered sub / sup; `UAElementFontRule.headingStandsDown`,
     not `UAHeadingFoldGate.standsDown`). **U2-ios is HELD**: its seam-2 and `UAHeadingFoldGate.{kt,swift}` are parked
     as `.txt` / `.patch` files in `wave54-ua-heading-face/held/`. The six `text-decoration-inset-005/-006/-014` ios /
     android rows are withdrawn from the targets and are must-not-move. L5 carriers are **web 0 · iOS 0 · Android 7**
     (§2 said android 10 / iOS 3).
   - **`hyphenate-character-002` ×3** (web, iOS, Android) are in `stayDegenerateEvenIfPass` (L3 skeptic S1).
   - **Totals now** (`plan-build.out.txt`):
     - union capture carriers **web 44 · iOS 24 · Android 51** (the preamble's planning-time execution 2, §6 R4 and the
       round-1 / round-2 tallies say iOS 27); wire carriers **23** (unchanged);
     - **18 revert units** (the preamble, §1 and §4 say 19);
     - **776 watch lines** (the preamble says 775); must-not-move **655** cells (R7 says 651);
     - gating predictions with numeric floors **34**, geometry keys **185** (gating 37 · report 36 · control 112):
       unchanged.
   - **The ceiling** (§1, §6 "Expected totals"): the HIGH / MED-HIGH line is unchanged (web 1236 · iOS 1128 · Android
     1121). The MED / MED-LOW increment is web +5 · iOS +3 · Android +1 (it was +5 / +6 / +4; the six withdrawn inset
     rows were iOS 3 and Android 3), so the ceiling is **web 1241 · iOS 1131 · Android 1122** (was 1241 / 1134 / 1125).
     The web LOW tally moving 0 → 1 in `plan-build.out.txt` is `hyphenate-character-002` web's new `kind` text, which now
     contains a "P"; LOW rows are never counted.
   - **§3 is stale on L5 and on the totals:** the L5 U1-android row wraps with `standsDown = headingStandsDown(…)`; the
     L5 U2-ios `.swift` row is HELD and has no hunk. **8 patches landed** (L5 seam-2 HELD; W1 took branch R, so L6 has
     no seam-2). `.swift` has 1 registered row (L3's, which lands as two diff hunks at :5011 / :5026).
   - **The DEGENERATE list:** §1 says "Four passing cells stay DEGENERATE". After `25cbd1c2` there are 8. After item 7
     there are **10**.
7. **The wave54-S1 fix pass (2026-10-09, after landing, before stage 1)** made three plan changes: two instruments
   and one registry row. Record: `tools/titan/results/wave54-S1/fix/_note.md`.
   - **[M] geometry teeth (S1 should-fix 1).** Both [M] gating probes now require each RTL row's marker ink to START at
     the ref's '.', x133 ±2: `rtl-marker-bake.geometry.py` and `wave53-plan/lists-bakes.geometry.py`. Before this, the
     L1 skeptic's nodot fake (the '.' runs whitened) printed `GEOMETRY OK` on web (both probes) and on iOS (lists-bakes).
     It now prints WRONG on all of them.
     - The ref still prints OK, and so do wave53-probe web and iOS.
     - Every recorded invocation prints byte-identical lines (wave52-ship, wave53-open, wave53-probe, wave53-final).
     - `geometry-gate.py wave53-final --base wave53-open --self-test` still HOLDS.
     - Disabling either new check turns the fake claim red, and the restore is byte-exact.
   - **L6 honesty labels (S1 should-fix 8, L6 skeptic D4).** Two rows are added to `stayDegenerateEvenIfPass`, and their
     `kind` text names the residue:
     - `css-position/position-absolute-semi-replaced-stretch-other` web: the label paints "abel" where the ref paints
       "label";
     - `css-cascade/scope-pseudo-element` web: the B/Foo wrap defect remains.

     A new `plan-build.py` assertion keeps the list and the rows in agreement. Every list entry must name a prediction
     row, and every row whose `kind` says "DEGENERATE even if P" must be listed. It is mutation-proven red in both
     directions. On regeneration, `watchlist.txt` and `plan-build.out.txt` are byte-stable.
   - **§3's U3b row** now names the patch's three anchors.

## Fix pass (round 1)

Applies the ten defects of `plan-skeptic-round-1.md` (M1–M3, S1–S7). Written only under `tools/titan/results/wave54-plan/`;
nothing committed, no source / doc / test file touched, no device, Gradle, xcodebuild or Chromium run (`git status --short`
lists only this directory). Every check was re-run BEFORE the fix (`fix-r1/pre-*.out.txt`) and after it
(`fix-r1/post-*.out.txt`); pre-fix copies of every edited plan file are kept as `fix-r1/*.pre`. Regenerated:
`expectations.json` sha256 `50906759…`, `watchlist.txt` `2557d870…` (`plan-build.out.txt`).

| defect | action | re-check after the fix |
|---|---|---|
| **M1** score of record `--movers 0.005` fails a fully-met closing gate | `scoreOfRecord` and §6 restated: the adjudicated JSON is produced with `--movers 0`; the 0.005 run is a read-out only (`scoreReadout`). §6 now says the three autopos-ltr floors are vacuous and R6 is their whole gate. `adjudicateCalibrations` added. | Pre: `skeptic-r1/synth-score.json` → `FAIL R4 … missed 4` (`fix-r1/pre-M1.adjudicate-synth.out.txt`). Post, built with `score-gate.mjs` `diffRuns(…, {moverThreshold: 0})` over the 4096 wave53-final cells (`fix-r1/synth-movers0.mjs`): all-met → `R1–R5 hold`; counter-suffix iOS exactly 0.985 → hold; one must-not-move cell +0.003 → `FAIL R5 moved 1` (the same picture at 0.005 → `R5 moved 0`, `fix-r1/pre-M1.adjudicate-mnm3-old.out.txt`); real `score-gate.mjs wave53-final wave54-open --movers 0` → `FAIL R4 missed 31` (34 − 3 vacuous). The four `controlCalibrations` re-run on the regenerated JSON: C1 holds (5/10/13, wire 1 + 10), C2 holds 0/0/0 and its JSON is byte-identical to `wave54-gate/control-calib-identity.json`, C3 fails 25 + 1 and is byte-identical to `wave54-gate/control-calib-wave53pair.json`, C4 holds on the identity pair and fails 28 + 1 on the wave-53 pair (`fix-r1/control-calib-C*.txt`). |
| **M2** `ua-heading-face.geometry.py` never checks band tops | `verdict_015` also requires each band top within ±3 px of the ref, checked last so existing WRONG lines are unchanged. | wave53-final output byte-identical to the planning record (Android still `band height 9 vs ref 18`; iOS anchor OK). Skeptic pitch fakes: +1 OK, **+2 / +3 / +4 WRONG** (`band top y107 vs ref y103` …); ref shifted 6 px → WRONG (`fix-r1/post-M2.pitch-down6.out.txt`). |
| **M3** `geometry-gate.py` ignores probe decisions | `probe_decisions()` moves a reverted unit's gating keys into `withdrawn` (with `requires` per key: the hyphenate 001/003 iOS keys need U1 + U2-ios + U3) and records `withdrawnGeometryKeys`. `geometry-gate.py` prints `WITHDRAWN`, never gates on it, and exits 2 on a stale JSON. `plan-build.py --out / --reverted / --restate` and `geometry-gate.py --exp` added for dry runs. R6 restated: "every gating key of a unit still on the tree". | Pre (skeptic hook copy): `wave53-probe --lanes L1,L2` exits 1, naming Mprime, TB-android. Post with REVERTED = [Mprime, TB-android, U3b] (`fix-r1/hook/`): 7 keys WITHDRAWN, gating 2/2 PASS, **exit 0**; on wave53-final it names **only P** (still on the tree); the skeptic's stale JSON → `STALE … exit 2` (`fix-r1/post-M3.hook-gate.out.txt`). A U2-ios revert withdraws exactly the 001/003 iOS keys (`fix-r1/hook-u2ios/`). |
| **S1** undirected movers revert HIGH commits through rule 2 | Every prediction carries `direction`. Ten rows are `undirected`, each with a `directionWhy`, and are exempt from rule 2; rules 1 and 5 still bind them. hyphenate-character-002 Android stays directed (replay B of its only unit is 0.923 ≥ 0.9223). `revertRule` 2 and the tier text, §6 rule 2 and the lane tables restated. | `plan-build.out.txt`: "undirected prediction rows (exempt from revert rule 2): 10" — exactly the skeptic's `movers.out.txt` list minus 002 Android; no gating row is undirected (asserted). |
| **S2** RS probe reads one pixel row | After the recorded row, the probe checks the second-atom edge on every ref atom row band (band middle row, ±1 px). | The 28 planning rows are byte-identical. Skeptic partial fake: 007 / 008 web → **WRONG** (`band rows 222-321` / `272-421`, `fix-r1/post-S2.rs-partial.out.txt`); the ref on every row → 72/72 OK. |
| **S3** tally counts cells no key gates | Looked at the PNGs. block-ellipsis-002 iOS paints Line 4 and no "…", and replay B keeps 4 lines, so it is **removed** (relabelled DEGENERATE, added to `stayDegenerateEvenIfPass`). NEW `block-ellipsis-br.geometry.py` gates the four web rows on U3. The RS probe gains keys for box-sizing-011, 014-019, 020, 021, 024, 025. `pictureCorrectTally` (37 full + 2 part) is asserted key by key in `plan-build.py`. | New probe on wave53-final: web 4/4 WRONG, ref OK (exit 0). On the U3 replay: web 4/4 OK, 002 iOS still `4 bands vs ref 3`. White and down-6 → all WRONG (`fix-r1/post-S3.block-ellipsis-fakes.out.txt`). The 11 new RS web rows are WRONG today and their natives OK. |
| **S4** U3b "cascade declares" is ambiguous | carrierRule and §2 L3 say "declared on the host itself". New negative pins: baseline-007 / baseline-002 brs stay 20, killed by the inherited-reading mutation. | Census re-run byte-identical (`fix-r1/pre-S4.br-census-all.out.txt`): host-declared gives 12 brs in 4 docs (the plan's radius); inherited gives +23 in 4, of which baseline-002 / -007 are not carriers. The pin itself is the lane's to execute. |
| **S5** non-gating keys all labelled control | Each probe lists `report` keys (expected WRONG today). `geometry-gate.py` prints GATING / control / report / WITHDRAWN and has `--self-test`. `build-workflow.js` rule 5 and §0 restated. | wave53-final: gating 37 FAIL · control 112 PASS · report 36 FAIL; `--self-test` HOLDS on the whole run and lane by lane L1–L6. The 140 planning keys keep their verdict and line (`fix-r1/geometry-gate.pre-post-verdicts.out.txt`). |
| **S6** shy-on-2nd-line natives missing | `hyphens-none-shy-on-2nd-line-001` added to the U2 default population (asserted 19 members). | L3 must-not-move 72 → 74; watchlist 775 lines / 781 cells, `unmatched 0` on wave53-final and wave54-open. Adjudicate R5 now counts 651 cells. |
| **S7** `GapDecorationLinesTests` does not exist | §2 L4 names the existing classes (`GapDecorationSegmentsTests`, `-BandsTests`, `-GrammarTests`) and marks `OutOfFlowContainingBlockTests` NEW. `build-workflow.js` rule 4 requires `Executed N tests` with N > 0 for every `-only-testing:` run. | Checked the class names under `runtimes/swiftui/Tests/StyleConverterRuntimeTests`: every named existing class exists. `UAHeadingFoldGateTests` (L5) and `OutOfFlowContainingBlockTests` (L4) are NEW files in their lanes' own lists, covered by the N > 0 rule. |

**Totals after the fix.**
- Gating predictions: 34. Revert units: 19.
- Union carriers: web 44 · iOS 27 · Android 51. Wire carriers: 23.
- Must-not-move: 651.
- Geometry keys: 185 = gating 37 + control 112 + report 36.
- Picture-correct tally: 37 in full + 2 in part.
- Expected flips are unchanged.

**Not done here** (outside this directory or not in the defect list):
- `wave54-gate/adjudicate.mjs` is unchanged. The restated score of record makes its R4 / R5 correct as written; the stale comment about an absent key is the orchestrator's to fix.
- Nits N1 and N3–N7 are untouched. N2 is answered in §6 (the vacuous autopos floors).

## Fix pass (round 2)

Applies `plan-skeptic-round-2.md`: R2-M1, R2-M2, R2-S1…S4, and the nits R2-N1, R2-N3, R2-N7 (round-1 N1, N3–N7) and
R2-N8. Written only under `tools/titan/results/wave54-plan/` and, for R2-N3's strings, `tools/titan/results/wave54-gate/adjudicate.mjs`.
Nothing committed; no source, test, doc or lane file touched; no Gradle, xcodebuild, emulator, simulator or Chromium run.
`build-workflow.js` is untouched (the builders run it). Every edited file was replaced atomically (written aside, then
renamed), so a lane reading `expectations.json` or a probe mid-pass never sees a partial file. Each check was run BEFORE
the fix (`fix-r2/pre-*.out.txt`) and after it (`fix-r2/post-*.out.txt`); pre-fix copies of every edited file are
`fix-r2/*.pre`. The edits themselves are `fix-r2/patch-plan-build.py` and `fix-r2/patch-plan-md.py`.

| defect | action | executed proof |
|---|---|---|
| **R2-M1** the GAP probe passes a mis-indexed rule segment (fake SSIM 0.9919 ≥ floor 0.99) | `flex-zero-gap-rules.geometry.py` now checks the red runs on a row through EVERY flex line (y20 / y95 / y145: ref `[(61,70),(111,120)]` / `[(111,120)]` / `[(61,70),(111,120)]`) and the blue runs on a column through every item column (x40 / x90 / x140), each ±1 px against the ref, plus a ref-structure self-check (2 / 1 / 2 red runs, 2 / 2 / 2 blue) | Pre: the skeptic's `skeptic-r2/fake-gap` prints `GEOMETRY OK` on web / ios / android (`fix-r2/pre-M1.fake-gap.out.txt`). Post (`fix-r2/post-M1.runs.out.txt`): **fake WRONG ×3** (`red runs row 95 [(61, 70)] vs ref [(111, 120)]`); wave53-final and wave54-open: ref OK, web OK, ios / android WRONG (`redPx 0 vs ref 2200`), exit 0. Two more count-preserving fakes (`fix-r2/gap-fakes.py`, `fix-r2/post-M1.own-fakes.out.txt`): a blue row-rule segment moved 10 px inside its column (SSIM 0.9913) → WRONG `blue runs col 90`; line 3's left rule moved into an item (SSIM 0.9899) → WRONG `red runs row 145`. `geometry-gate.py` on the fake `--lanes L4` names **GAP-android, GAP-ios** (`fix-r2/post-M1.geogate-fake-gap-L4.out.txt`). `geometry-gate.py wave53-final --base wave53-open`: all 185 verdict lines identical to the planning record (only the flex `line:` text and the summary format differ); record refreshed (`geometry-gate.wave53-final.out.txt`, old copy `fix-r2/*.pre`); `--self-test` HOLDS on the whole run and on L4 alone. |
| **R2-M2** the stage-2 `ab-diff` order is inverted; rule 3 has no reader | §6 stage 1 / stage 2, §8 steps 8 / 9 and `expectations.json` `stage1Probe.readOut` / `probeRun.readOut` now read **base first**: `ab-diff.mjs wave54-open <probe>`; `score-gate.mjs wave54-open <probe> --movers 0 --json …/score-{pre,probe}.json`, then NEW **`probe-readout.mjs`** (rules 1, 2, 3 over every carrier, prediction row and non-carrier cell of the probed sections; units named with their `revertOrder`; refuses a record whose `prev` is not the base, or a thresholded record, exit 2; exit 3 on a missing probed section); the controls; `geometry-gate.py`. `revertRule` 3 names the reader | Pre (the plan's order on wave 53's real probe pair, `fix-r2/pre-M2.abdiff-plan-order.wave53-probe.out.txt`): `flip P→f web P 1 → f 0.339 Δ-0.661 background-attachment-margin-root-002`. Post (`ab-diff.mjs wave53-open wave53-probe`): **`flip f→P web f 0.339 → P 1 Δ+0.661`**, counter-reset-reversed-nested ×3 Δ+0.0494 / +0.0481 / +0.0343. `probe-readout.mjs` (`fix-r2/post-M2.readout-*.out.txt`): on `score-gate wave53-open wave53-probe --movers 0` it prints margin-root-002 ×3 as f → P (leak signals: not wave-54 carriers) and fires rule 3 + rule 2 on 006 android (0.9906 < 0.996, Δ-0.0038) → **TB-android**, as wave 53 itself reverted (the other wave-54 gating rows fire rule 3 there too: their changes are not on that tree); the reversed record → exit 2 `ORDER`; a `--movers 0.005` record → exit 2; the identity record (`wave53-final → wave54-open`) → **rule 3 fires 31** (34 minus the 3 vacuous autopos floors, = adjudicate R4), 6 with `--stage1`; synthetic probe records (`fix-r2/synth-probe.mjs`, the 4 non-probed sections dropped): all-met → exit 0; plus-filter web −0.003 → rule 2 names U3; contain-content-004 android −0.05 (undirected) → `READ`, exit 0; block-ellipsis-002 android P → f → rule 1 names U3; a must-not-move cell +0.003 and a free cell +0.0001 → 2 leak signals; css-gaps missing → exit 3. A stage-1-shaped run (wave53-probe's 4 sections) `--stage1`: the L1 rows ok, TB-android named. |
| **R2-S1** a partial revert leaves unmeetable gating floors (U2-ios: `FAIL R4 … missed 2`, then a rule-3 cascade into U3b / U3) | Every prediction carries `requires` (default: its units; M′ additive on counter-suffix android, U3b additive on every L3 row). `probe_decisions()` DEMOTES a row that keeps other units but requires a reverted one (gating off, floor none, undirected, `demotedFrom` kept; `probeDecisions.reverted[].demotedPredictions`); a withdrawn row gates nothing either. Asserted: no gating row needs a reverted unit unless `RESTATE` re-registers it with `gating`, `floor` and `why`. §2 L6, §6 stage 1, §8 step 3: W1-not-built is entered in `REVERTED` with `run: step0` | Pre: U2-ios reverted → 001 / 003 ios still `gating True floor 0.965`, 34 gating (`fix-r2/pre-S1.hook-u2ios.gating.out.txt`). Post (`fix-r2/hooks/*/plan-build.out.txt`): U2-ios → **32 gating**, demoted 001 / 003 / 004 / 002 ios, limit-chars ios withdrawn; U1 → 32; Mprime + TB-android → 31 (counter-suffix android stays gating at 0.970: P alone, replay 0.9815); **W1 at step0 → 33** (the -002 web row, its geometry key and carrier withdrawn); U3b → 34, nothing demoted. A `RESTATE` that re-gates 001 ios without a `why` → `AssertionError`; with `why` + floor → 33, exit 0. The skeptic's U2-ios closing score (`skeptic-r2/synth-u2ios.mjs` on the hook JSON): adjudicate **`R4 gating predictions 32, missed 0` · R1–R5 hold** (was `FAIL R4 … missed 2`), 003 ios printed DEGENERATE-BY-CONSTRUCTION; `probe-readout.mjs` on it: rule 3 fired 0 (`fix-r2/hooks/u2ios/`). |
| **R2-S2** `geometry-gate.py` exits 0 with an UNMEASURED gating key | exit **3** when no gating key FAILs but one is UNMEASURED (FAIL / self-check stay exit 1, STALE exit 2); the summary counts UNMEASURED gating and names them. R6 and revert rule 4 say "exit 0 = every gating key PASS, UNMEASURED-gating 0" | Pre: `wave53-probe --base wave53-open --lanes L5` → 15 UNMEASURED incl. `block-in-inline-015-print android [GATING -> revert U1-android]`, **exit 0** (`fix-r2/pre-S2.geogate-probe-L5.out.txt`). Post: the same → `UNMEASURED GATING KEYS (1) … [unit U1-android]`, **exit 3**. A stage-1-shaped run (CSS2, css-counter-styles, css-tables, css-text of wave53-probe) `--lanes L1,L2`: UNMEASURED gating **0** (3 UNMEASURED controls, counter-reset-reversed-nested in css-lists), exit 1 on the Mprime / TB-android FAILs; a stage-2-shaped run (the 26 probe sections of wave53-final): **0 of 185 UNMEASURED** (`fix-r2/post-S2.*`). `--self-test` unchanged (HOLDS). |
| **R2-S3** the `--movers 0` record would break the corpus snapshot | `scoreReadout` writes `--json tools/titan/results/wave54-gate/score-final.movers0005.json`; new `corpusSnapshotSource`; §6 and §8 step 13 name it as `make-corpus.mjs`'s `<record.json>` | `make-corpus.mjs:66,71` publishes `movers: record.movers.length` under `--movers 0.005`; `fix-r2/pre-S3.scoreReadout.out.txt` shows the old command had no `--json` and 0 mentions of the file. |
| **R2-S4** 12 "up or unchanged" rows without a replay bind rule 2 and can revert U3 | Replay B on their own pixels (NEW `hyphenate-character.replay-b-u3f.py` + `-score.mjs`, the gate's `diffWebVsRef`; the capture column reproduces the manifest). Replayable where everything below the stray band is in flow: **clip-path-filter-order web / ios / android f 0.8586 / 0.8578 / 0.8406 → B 1 / 0.9978 / 0.9549; balance-grid-container web f 0.9207 → B 1**. LOOKED at (`fix-r2/look-clip-path-filter-order-ref-Bweb-Bios-Bandroid.png`, `fix-r2/look-balance-grid-container-ref-Bweb.png`): web B is the ref, iOS matches, Android keeps its leading-space residual. The presence / colour / coverage vetoes are false on all four; `degenerateFailed` is true but that veto ships OFF (`inject-wpt-block.mjs` `computeWptPass`), so raw SSIM ≥ 0.95 decides. These four stay directed with the replay value (MED / MED / LOW-MED / MED). NOT replayable, so **undirected** with a `directionWhy`: column-height-009 ×3 (multicol re-balance), balance-grid-container ios / android (one-line address: no band to cut), backdrop-filter ×3 android (the stray line sits under abspos boxes, text shows through the backdrop filter). The six backdrop-filter web / iOS "up" MED rows have the same missing replay and reason, so they are undirected too (an extension beyond the skeptic's 12; rule 1 still binds them, they are P today) | `hyphenate-character.replay-b-u3f.out.txt` / `-score.out.txt`. `plan-build.out.txt`: undirected **10 → 24**; flips by tier MED / MED-LOW **web 3 → 5, iOS 5 → 6** (ceiling 1241 / 1134 / 1125, §1 and §6 restated); HIGH / MED-HIGH unchanged (4 / 3 / 6). |
| **R2-N1** stale sentences | §1 L3 row (4 P→P, -002 ios OUT), the L3 predictions header (rules 1 / 5 bind every row, rule 2 every directed row; 16 L3 rows undirected), §6 score-of-record text names "adjudicate's R4 / R5" and maps its numbering to the table's | text |
| **R2-N3** wave-53 strings in `wave54-gate/adjudicate.mjs` | header path and "Wave 54's", usage `wave54-open wave54-final --movers 0 --json`, `WITHDRAWN at <run> (unit <u>)` from the decision, the absent-key comments (an absent key is a cell scored on neither side under `--movers 0`), "back at wave54-open", a demoted row's read-out line. No rule logic changed | `node --check`; 0 `wave53` strings left. Re-run on the regenerated JSON: identity → R4 missed 31; all-met → R1–R5 hold; counter-suffix ios at 0.985 → hold; mnm +0.003 → R5 moved 1 (`fix-r2/post-N3.adjudicate-*.out.txt`), the fix-round-1 calibrations unchanged. |
| **R2-N7** round-1 nits | N1: L1 own `+91 / −4` (`git diff --numstat a8ffd1c6^ b0edb788^ -- tools/titan/bidi-bake.mjs` → `91 4`). N3: the 18 seed names spelled out in §2 L7 and in `plan-build.py` U2-seed. N4: §2 L6 `:1108-1153` and §3 `:1099-1154` (HEAD `ComponentRenderer.tsx`: `renderChildSeparator` :1099, widget rule :1105-1107, WWS :1108-1153, close :1154). N5: `compose-table-body-cell.geometry.py` prints `→ GEOMETRY WRONG`. N6: `integrate-seams.sh` header says earlier patches stay applied after a mid-run failure. N7: sizes at HEAD in §2: `bidi-bake.test.mjs` 758, `label-chrome-tripwire.test.mjs` 209, `UAElementFontRule.swift` 274 | N5: the 13 probe lines on wave52-ship / wave53-open / wave53-probe / wave53-final equal the planning record once the arrow is dropped (`fix-r2/post-N5.ctbc.out.txt`); `--self-test --lanes L2` HOLDS. N6: `bash -n` clean. |
| **R2-N8** L5 `own:` names a directory | names `UAElementFontRuleTest.kt` and `UAHeadingFoldGateTest.kt` (NEW), not `typography/` (it holds L3's `typography/wrapping/PreBreakPipelineTest.kt`) | text |

**Regenerated** (`plan-build.out.txt`): `expectations.json` sha256 `50906759…` → **`8f870f8e…`**; `watchlist.txt` **`2557d870…`
unchanged**. Byte-stable: two staged runs and the in-place `python3 plan-build.py` give identical bytes (`fix-r2/post-sha.out.txt`).
Unchanged totals: gating 34 · units 19 · carriers web 44 · iOS 27 · Android 51 · wire 23 · must-not-move 651 · geometry
keys 185 = gating 37 + report 36 + control 112 · tally 37 + 2. Changed: undirected 24; MED / MED-LOW flips web 5 · iOS 6 ·
Android 4. Re-held on the installed JSON: watchlist-check `unmatched 0` on wave53-final and wave54-open
(`fix-r2/post-watchlist-check.out.txt`); `geometry-gate.py --self-test` HOLDS; control C2 (`wave53-final → wave54-open`)
HOLDS and its JSON is byte-identical to `wave54-gate/control-calib-identity.json` (`fix-r2/control-C2-identity.*`).

**Deleted after use (regenerable):** the score / synthetic records above 300 kB, the hook dirs' `expectations.json` /
`watchlist.txt`, and the two symlinked stage-shaped run dirs. To regenerate: `node tools/titan/score-gate.mjs wave53-open
wave53-probe --movers 0 --json …/fix-r2/score-w53probe-movers0.json` (and reversed); `node …/fix-r2/synth-probe.mjs`;
`python3 …/plan-build.py --out …/fix-r2/hooks/<h> --reverted …/fix-r2/hooks/<h>.reverted.json` (the reverted / restate
JSONs are kept); `node …/skeptic-r2/synth-u2ios.mjs …/fix-r2/hooks/u2ios/expectations.json …/fix-r1/score-identity-movers0.json
…/fix-r2/hooks/u2ios/synth-closing.json`; the stage-shaped runs are `fix-r2/run-stage{1,2}/sections/<sec>` symlinks to
`tools/titan/runs/wave53-probe` (CSS2, css-counter-styles, css-tables, css-text) and `wave53-final` (the 26 probe sections).

**Not done here:** R2-N2 (the block-ellipsis-002 android control that would fail loudly on an improvement), R2-N4 (no L4 A/B
arm), R2-N5 (Gradle `--tests` filters can match nothing) and R2-N6 (an equivalence pin for U2-android) are outside this
pass's list. R2-N5 and R2-N6 belong in the lanes' notes and `build-workflow.js`, which this pass does not touch while the
builders run.

## Fix pass (round 3)

Applies `plan-skeptic-round-3.md`: R3-S1, R3-S2, R3-S3 and the text nits R3-N1, R3-N2, R3-N4, R3-N5, R3-N6. Written only
under `tools/titan/results/wave54-plan/` and `tools/titan/results/wave54-gate/adjudicate.mjs` (`control-check.mjs` is
untouched; its pre-pass copy is `fix-r3/control-check.mjs.pre`). Nothing committed; no source, test, doc or lane file
touched; no Gradle, xcodebuild, emulator, simulator or Chromium run; every probe and score run under `nice -n 19` over run
dirs. `build-workflow.js` is untouched. A killed earlier start of this pass had left `fix-r3/*.pre` copies; every
installed file was first checked byte-identical to them (`fix-r3/pre-sha.out.txt`), and every pre-fix check below was
re-run by this pass (`fix-r3/pre-*.out.txt`; post-fix: `fix-r3/post-*.out.txt`).

| defect | action | executed proof |
|---|---|---|
| **R3-S1** `adjudicate.mjs` accepts a thresholded record, and a 0.005 JSON is written beside the adjudicated one | `adjudicate.mjs` gains the two guards `probe-readout.mjs` has, plus one: exit 2 `ORDER` when `prev` ≠ `--base` (default `wave54-open`); exit 2 `NOT A --movers 0 RECORD` when gained + lost + movers + unmeasured-now ≠ the cells scored on `prev`, or gained + lost + movers + newly-measured ≠ those on `cur`; exit 2 `GEOMETRY PAIR` (R3-S2's input). New options `--base`, `--exp`, `--geometry`; the R1–R5 bodies are unchanged. One statement of which JSON each reader reads: `expectations.json` `closingGateFiles` (new), the §6 table, §8 step 11 (rewritten with the exact commands) and step 13 (unchanged): `score-final.json` (`--movers 0`) → adjudicate only; `score-final.movers0005.json` → `make-corpus.mjs` and the PR list only; `geometry-final.json` (`geometryGate.closingCmd`, new) → R6's exit and adjudicate `--geometry`. `adjudicate` now reads `… score-final.json --geometry … geometry-final.json`. `adjudicateCalibrations` re-run and rewritten (9 entries). | **Pre** (`fix-r3/pre-S1.*`): Mprime + TB-android + CBB-android reverted (`fix-r3/hooks/abc`), one must-not-move cell −0.003, the 0.005 record → **`R1–R5 hold`, exit 0** (the move unseen); the installed all-met 0.005 record → `FAIL R4 missed 4` with "scored on neither side" printed for cells scored on both sides; the identity record with prev / cur swapped was read without complaint. **Post** (`fix-r3/post-S1.*`, `post-cal.*`): all four 0.005 records (installed / abc × all-met / mnm3) → **exit 2 `NOT A --movers 0 RECORD`** (30 / 24 listed vs 4096). The `--movers 0` twins are unchanged: installed all-met `R4 34, missed 0` hold, mnm3 `FAIL R5 moved 1`; abc all-met `R4 24, missed 0` hold, mnm3 `FAIL R5 moved 1`. The identity record → exit 2 `ORDER` with the default base, `FAIL R4 missed 31` with `--base wave53-final` (unchanged); swapped → exit 2 `ORDER`; `skeptic-r1/synth-score.json` → exit 2 (`ORDER`; with its own base, `NOT A --movers 0`, 30 vs 0). fix-r1's inputs with `--base wave53-final`: all-met hold, floor hold, mnm3 `FAIL R5 moved 1`, mnm3-old (the 0.005 picture) exit 2. A stray extra argument → usage, exit 2. `probe-readout.mjs` on the identity record still fires rule 3 on 31 rows (`fix-r3/post-readout-identity.out.txt`). |
| **R3-S2** the DEGENERATE line ignores `degenerateRetirement` | New `expectations.json` `degenerateRetirementCheck`, the machine form of `degenerateRetirement`: lane L3, `hyphenate-character.geometry.py`, line ends `→ GEOMETRY OK`, units web U1 + U3 · iOS U1 + U2-ios + U3 · Android U1 + U2-android + U3, keys for 001 / 003 / 004. `plan-build.py` asserts it names exactly `degenerateByConstruction`, known units of L3, every platform, and keys and verdict strings of that probe. adjudicate's R6 RETIRES a degenerate gain (prints it as a gain) iff none of its units is in `probeDecisions.reverted` AND the `--geometry` row is PASS and ends `→ GEOMETRY OK`. Otherwise it prints DEGENERATE with the reason (units are read first). Without `--geometry`, nothing is retired. R6 still never changes the exit. | **Pre** (`fix-r3/pre-S1.installed-allmet.m0.out.txt`): the all-met tree prints `DEGENERATE-BY-CONSTRUCTION gains (not fixes): 2` (001 ios, 003 ios). **Post**, on the synthetic correct tree: `fix-r3/synth/installed-allmet.m0.json` (every gating row at its prediction) + `fix-r3/geometry-allmet.json`. That JSON is `fix-r3/synth-geometry.py`: the real wave53-final gate JSON with its nine hyphenate rows replaced by the probe's OWN lines on replay GB = U1 + U2 + U3, 9/9 OK. Result: **`RETIRED … 2`** (001 ios 0.9287 → 0.979, 003 ios 0.9337 → 0.977), no DEGENERATE line, `R1–R5 hold`, exit 0 (`post-S2.allmet-geometry`). The same record with no `--geometry` → DEGENERATE 2, "not read". With the U3-only geometry (replay B) → DEGENERATE 2 (`glyph:` WRONG). With a geometry JSON of another pair → exit 2 `GEOMETRY PAIR`. U2-ios reverted (`fix-r3/hooks/u2ios`, 003 ios at the replay-B pass 0.9566) → DEGENERATE 1, `unit U2-ios reverted`, `R4 32, missed 0`, hold. That holds with its own geometry and with the all-OK one. The abc hook + all-OK geometry → RETIRED 2. |
| **R3-S3** the flex-gap probe passes wrong vertical extents | `flex-zero-gap-rules.geometry.py` also reads the red runs on a column through each column-rule gap (x65, x115), ±1 px against the ref. That pins every segment's vertical extent, flex line by flex line (ref x65 `[(16,60),(121,165)]`, x115 `[(16,60),(71,110),(121,165)]`). The ref self-check includes the 2 / 3 runs, and a WRONG names the flex line. Recorded as `reads` on the probe (`expectations.json`) and in §2 L4. | Two new fakes, smaller than the skeptic's and on other segments (`fix-r3/gap-fakes.py`; SSIM `fix-r3/gap-fakes-score.out.txt`): `vshift3-up` (line 2's segment 3 px up, 0.9997) and `line3-short3` (line 3's left segment 3 px short, 0.9994). **Pre** (`fix-r3/pre-S3.probe.out.txt`): these two and the skeptic's `vshift5` / `line2-short4` → `GEOMETRY OK` ×3 each. **Post** (`fix-r3/post-S3.probe.out.txt`): all four → **WRONG ×3**: `red runs col 115 … flex line 2 column-rule vertical extent` ×3 fakes, and `red runs col 65 [(16, 60), (121, 162)] … flex line 3` for `line3-short3`. Every earlier fake keeps its verdict and reason (the r2 fake: row 95; fix-r2 blue: col 90; fix-r2 line3: row 145; short6 / line1-short4: redPx). wave53-final and wave54-open: ref OK (self-check, exit 0), web OK, iOS / Android WRONG (`redPx 0 vs ref 2200`). `geometry-gate.py … --lanes L4` on `vshift5`, `vshift3-up` and `line3-short3` → exit 1, `revert rule 4 names: GAP-android, GAP-ios`. The new read adds no paint-order constraint: a native painting column rules over the row rules already fails the blue count (−200 px, 6.7 %). |
| **R3-N1** stale gate record | `geometry-gate.wave53-final.out.txt` refreshed | It differs from the old record in exactly the 006 android `→` line and the two flex `line:` texts (the new `redColRuns`). 185 keys: gating 37 FAIL · control 112 PASS · report 36 FAIL, exit 1. `--self-test` HOLDS (`fix-r3/post-geogate-*.out.txt`). |
| **R3-N2** the U3b keep rule reads a report row | `revertUnits.U3b.probeDecided`, §2 L3 and D5: "no `offset:`/`lines:` reason on **the two gating iOS rows**". 004 ios is a report row (read, never a trigger), and a glyph-width `lines:` there is U2-ios's (`reasonToUnit`) | text |
| **R3-N4** controls on carriers are gated by nothing at the closing gate | Said in §6 R6 and `geometryGate.what`. These are the nine keys (U3's eight block-ellipsis native freeze keys, RS's `block-in-inline-015-print web`), and the cell review reads every control FAIL | text (the skeptic's `skeptic-r3/control-on-carriers.out.txt` lists the nine) |
| **R3-N5** the closing gate does not re-read rule 2 | §6 "Revert rule 2 is a probe rule" and `revertRule[1]` | text |
| **R3-N6** A/B arms without a command | `abRead` (new) and §8 step 12: `ab-diff.mjs wave54-ab-<arm> wave54-final --threshold 0.002`, the arm (exclude) FIRST, so Δ = final − arm is the dropped unit's share | text |

**Regenerated** (`plan-build.out.txt`):
- `expectations.json` sha256 `8f870f8e…` → **`c9d73e56…`**;
- `watchlist.txt` **`2557d870…` unchanged**;
- `plan-build.out.txt` byte-identical to round 2.
- Byte-stable: two `--out` runs and the in-place run give identical bytes (`fix-r3/post-sha.out.txt`).
- The diff is exactly the intended keys (`fix-r3/post-expectations-diff.out.txt`): `abRead`, `adjudicate`, `adjudicateCalibrations`, `closingGateFiles`, `degenerateRetirementCheck`, `geometryGate.closingCmd` / `what`, L3 `U3b.probeDecided`, the L4 flex probe's `reads`, `revertRule[1]`.
- Every prediction's (cell, gating, floor, direction) and every must-not-move line are identical. The totals are unchanged: gating 34 · undirected 24 · geometry keys 185.
- Re-held on the installed JSON: watchlist-check `unmatched 0` on wave53-final and wave54-open (`fix-r3/post-watchlist-check.out.txt`); `geometry-gate.py --self-test` HOLDS.

**Deleted after use (regenerable, each checked byte-identical on regeneration):**
- the six `--movers 0` synthetic records (≈690 kB each);
- the hook dirs' `expectations.json` / `watchlist.txt`.

To regenerate:
- the hooks: `python3 …/plan-build.py --out …/fix-r3/hooks/<h> --reverted …/fix-r3/hooks/<h>.reverted.json` (h = abc, u2ios);
- the closing records: `node …/fix-r3/synth-closing-r3.mjs <exp> …/fix-r1/score-identity-movers0.json …/fix-r3/synth <tag> wave54-final`, with tag `installed` on the installed JSON and `abc` on the abc hook's (this also rewrites the kept `.m0005` twins byte-identically);
- the U2-ios record: `node …/fix-r3/synth-u2ios-r3.mjs …/fix-r3/hooks/u2ios/expectations.json …/fix-r1/score-identity-movers0.json …/fix-r3/synth/u2ios-closing.m0.json`;
- the swapped identity record: `fix-r1/score-identity-movers0.json` with `prev` / `cur` exchanged.

The geometry JSONs are kept (≈76 kB each). They regenerate with `python3 …/fix-r3/synth-geometry.py` after `geometry-gate.py wave53-final --base wave53-open --json …/fix-r3/geogate-wave53-final.json`.

**Not done here:**
- **R3-N3** (`control-check.mjs`: an "absent before" capture is printed LEAK but not pushed to `leaks`, and a capture present before and missing after is never iterated). This is a logic change to a calibrated check: the four `controlCalibrations` must re-run with it. The skeptic finds both cases unreachable given R3's column counts. It is left to the orchestrator, with that re-run.
- **R3-N7** (commit the plan artefacts while seven lanes write on this tree) is the orchestrator's call; this pass must not commit.
- R2-N2, R2-N4, R2-N5 and R2-N6 stay open as at round 2.

### §10 item 6 — the S1 combined-tree skeptic and its fix pass (orchestrator, 2026-10-09, before stage 1)

S1 (`wave54-S1/_note.md`): MIXED, 0 must-fix, 9 should-fix, 9 nits; the seam replay 14/14 files equal, every census replayed with no
under-reported radius, 839 pointers resolve, 115/115 prediction "from" values equal `wave54-open`, the [W-L3] set equality confirmed.
The fix pass (four scoped lanes + a verifier, `wave54-S1/fix/`) took the should-fix items that bear on the probes or the record:

1. **The [M] gating probes could pass a counter-suffix capture missing the "." run** — both `rtl-marker-bake.geometry.py` and
   `wave53-plan/lists-bakes.geometry.py` now also require each RTL row's marker-ink LEFT edge at the ref's x134 ±2 (the dark-core
   read; the antialiased edge is x133 — the verifier measured ref x134 ×4 and iOS cores 134/134/135/134, so the centre is 134, giving
   iOS row 3 one pixel of headroom rather than none). Fakes: no-dot WRONG on both probes, control and ref OK; `geometry-gate.py
   --self-test` HOLDS.
2. **An instrument check pre-registered for stage 1**: M′'s new paint-chain reader (`bidi-marker-paint.mjs`, item 4) first runs
   live at `wave54-pre`. Before any [M] key is read, the stage-1 css-counter-styles per-test IR must be byte-identical to the
   [W-L3] post tree (`tools/titan/runs/wave54-wl3-diff/post/css-counter-styles/per-test-ir/`) and counter-suffix must carry no
   `marker-not-baked` stamp; if it does, an [M] FAIL is attributed to the instrument, not to M′ (bisect S6 first).
3. **L6 W1**: the production gate `{ hyphensAuto: styles.hyphens === 'auto' }` is pinned on the verbatim hyphens-out-of-flow-001 box
   4 (mutation `hyphensAuto: true` → red); the `joinedOutOfFlowMembers` count is emitted as a breadcrumb beside resolveRuns' rule-5
   warn (pinned). **L2 TB-android**: the D2 quantity is pinned at source (widthAtMaxIntrinsic, never Min; `.then(cellModifier)`
   order non-vacuous). **L1 M′**: re-parenting a marker to the bake root now DECLINES (stamp `marker-not-baked`) an item whose
   item→root chain carries a non-initial paint effect (opacity, transform, filter, clip-path, overflow, visibility, text-shadow;
   `tools/titan/bidi-marker-paint.mjs`, ≤ 200 lines, pinned with a mutation); counter-suffix's four RTL items carry none, so the
   corpus behaviour is unchanged (the lane's static check; [W-L3] is the executed proof on the pre-fix tree, item 2 re-proves it
   at stage 1). **Records**: the 72+ gitignored `*.log` evidence files of the lane dirs are force-added; the 518 KB seam copy under
   `wave54-table-body-cell/seam1-files/` is removed (regenerable); the L7 note's three elided iOS PNG names written in full;
   `docs/DYNAMIC_CAPTURE.md` §5 describes the exempt manifest and clause (iv). Two L6 rows join `stayDegenerateEvenIfPass`
   (semi-replaced-stretch-other web: "abel"; scope-pseudo-element web: the B/Foo wrap) and §3's U3b row names its three anchors.
4. **Commit procedure (the verifier's must-fix)**: the pass touches four landed units, so it lands as ONE COMMIT PER UNIT —
   `Mprime-S1`, `W1-S1`, `RS-S1`, `TB-android-S1` — then the plan/docs/records commit. **A revert of one of those units reverts
   its `-S1` commit first** (revertOrder per unit: [X-S1, X]); the four `-S1` commits are code-only and each reverts alone.
5. Not taken (queued in BACKLOG at the ship): the remaining lane-skeptic nits and untested branches S1 lists; mask-image /
   mix-blend-mode in the paint-effect decline (same class, 0 carriers); the two test files over 200 lines; `wl3-wire-differential.sh`
   always exits 0 (a nit — the set equality was checked programmatically).

