# wave52-docs — BACKLOG ship pass (closing gate), 2026-10-06

Editor: the BACKLOG editor closing wave 52. Only `docs/BACKLOG.md` was edited (this note is new). Read-only otherwise:
files, grep, `git show` / `git diff` / `git ls-files`, python over JSON / text, and
`tools/titan/results/wave52-gate/cells.mjs` over `wave51-fix wave52-calib wave52-final wave52-preview`. No build, test,
device, image-decoding or git-state command; nothing under `tools/titan/runs/` was written (two per-test IR JSONs of
`wave52-final` were read).

Run-id convention followed: every gate cell is LOOKED UP in `wave52-preview` and WRITTEN as `wave52-ship`; `wave52-final`
appears only for first-attempt facts (the five lost cells, the 0.9504 first-attempt ch-units pass, the T-L4 tripwire, the
per-test IR reads). After the pass: `docs/BACKLOG.md` 4097 lines; `grep -c '\[\[GATE\]\]'` = **0**; `grep -c 'scratchpad/'`
= 0; six `[[AB]]` tokens.

## 1. Each [[GATE]] token → what it became (file order of the first pass; doc-note §2 numbering)

| # | item | became | cells (wave52-ship unless stated) |
|---|---|---|---|
| 1 | 0(a⁗) L5 | HELD (all) | import-conditional-001/-002 ×6 f→P 0.999/0.9974/0.9967 FAITHFUL; color-mix-percents-02 ×3 f→P 1.0/0.9988/0.9981 FAITHFUL; gaps-024 ×3 P 0.99→1.0; 18 native cssom P stay P (0.9916–0.9999); important-prop ×3 stay P (−0.002); revert-val-002 web unmoved P 0.999 (picture claim unreviewed) |
| 2 | 0(b″) L4 b″ A/B | HELD — A/B DONE at the gate | abspos-auto-sizing-fit-content-percentage-001…004 android P 0.9984 ×4, PNGs byte-identical to wave52-open (T-L4, measured on wave52-final); position-relative-002/-008 android P 0.9967 / 0.9984 unchanged; install hash in build-hashes.txt |
| 3 | 0(c′) L4 | HELD | composited-under-rotateY-180deg-preserve-3d ios/android f 0.9565→P 1.0 FAITHFUL; css-transform-inherit-scale android f 0.9965→P 0.9967 FAITHFUL; animated-001/002 ios P 0.9646, 0(c) HOLD cells unchanged |
| 4 | 0(j′) L7 | HELD | position-absolute-containing-block-002 ios f 0.9373→P 1.0 FAITHFUL; 16 android grid align-{self,items} → P 1.0; 10 web last-baseline → 0.9998–1.0; vertWM drops stay P (0.9667/0.9682, ios 0.995) |
| 5 | 0(k) L3 | HELD | display-contents-float-001 ios/android f→P 0.9988/0.9979; abspos-containing-block-outside-spanner ios f 0.9553→P 0.9994 with ZERO red; flexbox_align-items-stretch-writing-modes ios f 0.999→P 0.9991; movers abs-pos-border-offset-003 small rises, baseline-vertical flat |
| 6 | 0(l′) lifted roots | HELD | gradient-single-stop-001…008, aspect-ratio/abspos-0*, bg-clip-content-box-001, clip-path-polygon-003, backdrop-filter-edge-pixels-2: every passing cell unchanged |
| 7 | 0(l′) L2 flips | HELD + 1 DEGENERATE flip + 1 MISS | clip-path-ellipse-006/7/8 ×6, propagation-shadow ×2, align-items-007 ×2, gaps-027 web, Fix A trio (gaps-040 web 1.0, block-ellipsis-028 web 1.0, semi-replaced-stretch-input web f→P 0.9596) all held; s-11-1-1b-006 ios/android flipped, DEGENERATE; tripwire predicted 0/0, measured 0/1 |
| 8 | 0(l′) L2 DEGENERATE labels | DEGENERATE (as labelled) | contain-inline-size-bfc-floats-001 ios/android P 0.9531/0.9519, anchor-position-multicol-007 android P 0.9519 — all DEGENERATE; gaps-034 ×3 dispute resolved FAITHFUL (P 1.0, pixel-identical to the ref) |
| 9 | 0(l′) ring-fenced basic-blur | number HELD; picture DEGENERATE (ring-fenced, reported only) | backdrop-filter-basic-blur ios f 0.9469→P 0.9526 (filter boxes 24 px right); web P 0.9943→1.0, android f 0.899→0.904 |
| 10 | 0(m) L11 | HELD | has-visited ios f 0.9469→P 0.9571 FAITHFUL (parent colours); colour cells stay P; all-prop-001 ×3, all-prop-initial-visited ×3, display-contents-{button,details,fieldset} web unmoved |
| 11 | 1(d) L8 | 6 HELD, 4 MISSED, then [[AB]] `ma` | input-range-zero-inline-size ios/android f→P 0.9993/0.9989; ch-units-vrl-003/-004 ios P 0.9535 ×2, android P 0.9507 ×2 (FAITHFUL); ch-units-vrl-005/-006 MISSED (ios f 0.942, android f 0.9347); M-A at-risk all stay P; hyphenate-character-005 ios f 0.945→0.9428 |
| 12 | 2(c¹) armenian-008 natives out | HELD | ios f 0.942 / android f 0.9423 → unscored (2 of the 21 unmeasured-now) |
| 13 | 2(c¹) armenian-008 web | HELD | web f 0.9435→P 0.9981 FAITHFUL |
| 14 | 2(d) L6 T5 | DEGENERATE flip, at-risk held, then [[AB]] `t5` | counter-suffix ios f 0.9285→P 0.9802 and (unpredicted) android f 0.9051→P 0.9547, both DEGENERATE (RTL rows draw no marker); counter-list-item-2/-3, add-inline-child-after-marker-001/002 rose; first-line-and-marker natives still f (rose) |
| 15 | 2(e) L6 T7 | HELD (MED-LOW tier included) | cssom pad / prefix-suffix -invalid web f→P 1.0; natives pad/prefix-suffix/negative -invalid ×6 f→P 0.9994–0.9998 FAITHFUL; armenian-006/-007 web 1.0, -009 web 0.9985, marker-text-matches-georgian web 1.0 |
| 16 | 3(b) L6 T2 tripwire | HELD | android orange rows 161–163 / 171–175 (T-L6); floats-clear-multicol-003 android P 0.9881→0.9968, balancing-003 P 0.9857→0.997 |
| 17 | 4(c) L9 F2 | iOS HELD, Android MISSED; F2 A/B still owed (not an [[AB]] arm) | block-ellipsis-032 ios P 0.9577→P 0.9899; android f 0.9397→f 0.9457 (predicted thin P) |
| 18 | 4(f) L9 F1 | Android HELD, iOS MISSED in magnitude, then [[AB]] `f1` | hanging-punctuation-inline-001 android f 0.9495→f 0.9452 (predicted 0.9454); ios P 0.9756→P 0.9555 (predicted 0.9685) |
| 19 | 4(f) L9 | HELD | block-ellipsis-025 ios f 0.9495→P 0.9709 FAITHFUL; hyphens-manual-inline-012 android f 0.9419→P 0.9803 FAITHFUL; every android line-clamp host stays P; block-ellipsis-023/-024 android P 0.972→P 0.9736 |
| 20 | 5(j) L1 | HELD | display-p3-linear-001/-002/-003 ×9 f→P FAITHFUL; fractional-box ×4 web + ×8 natives f→P FAITHFUL; display-p3-linear-004/-005/-006 ×9 → 0.9973–1.0; T-L1 ring stamped on exactly the 4 tests |
| 21 | 7(e³) | HELD — item CLOSED | flex-gap-decorations-027 web P 1.0 / ios P 0.9997 / android P 0.9991, FAITHFUL ×3 |
| 22 | 7(g) L10 | flips HELD, 2 mover predictions MISSED | bg-clip-content-box-002 ios f→P 0.9992 ZERO red; gaps-027 ios/android f→P; gaps-008 android P 0.9646→0.999; gaps-045 ios 0.9931→0.9996; gaps-046 android P 0.996→0.9947 (MISS); snap ≤ 0.0014 MISSED (+0.0037…+0.013, not separable from Fix A) |

By kind: 14 held outright (1, 2, 3, 4, 5, 6, 10, 12, 13, 15, 16, 19, 20, 21); 3 whose verdict is a DEGENERATE picture (8, 9,
14 — 9 is the ring-fenced report); 5 carrying a MISS (7, 11, 17, 18, 22). Three of them hand their remainder to a device
A/B: 11 → `ma`, 14 → `t5`, 18 → `f1`.

## 2. Every prediction that MISSED

1. 0(l′) L2 frame-ink tripwire: predicted overrun-right + overrun-left 0 / 0; measured 0 / 1 (one left-frame pixel,
   `filter-effects/backdrop-filter-clip-rect-2` android, cell unchanged P 0.9981).
2. 1(d) L8 `css-writing-modes/ch-units-vrl-005` ios: predicted f → P (MED-LOW); measured f 0.942.
3. 1(d) L8 `ch-units-vrl-006` ios: predicted f → P (MED-LOW); measured f 0.942.
4. 1(d) L8 `ch-units-vrl-005` android: predicted f → P (LOW); measured f 0.9347.
5. 1(d) L8 `ch-units-vrl-006` android: predicted f → P (LOW); measured f 0.9347.
6. 4(c) L9 `css-overflow/line-clamp/block-ellipsis-032.tentative` android: predicted thin P (MED-LOW); measured f 0.9457
   (the second wave running — wave 50 also predicted this flip and missed).
7. 4(f) L9 F1 `css-text/hanging-punctuation/hanging-punctuation-inline-001` ios: predicted a loss to 0.9685; measured
   P 0.9555 (direction right, three times deeper; 0.0055 over the bar).
8. 7(g) L10 `css-gaps/flex/flex-gap-decorations-046` android: predicted a rise; measured P 0.996 → P 0.9947.
9. 7(g) L10 snap movers 024/029/030/034–037/050: predicted ≤ 0.0014; measured +0.0037 … +0.013 (all upward; L2 Fix A on
   the same tests makes the split unattributable from this gate).
10. (not a [[GATE]] token) PLAN §10 pre-registered `blank-captures=45`; measured 43 (tripwire T-L12) — L4's T5 un-blanked
    the two composited-under-rotateY natives; recorded in the blank-capture Instrument bullet.

Flips that happened on the score but are DEGENERATE (not misses, never fixes): s-11-1-1b-006 ios/android (L2, MED),
counter-suffix ios (L6 T5, MED) + android (unpredicted).

## 3. Obligations removed, and why

- Old obligation **0** ("Wave 51 shipped as three PRs … wave 52 opens with the FULL gate … expected zero per-cell change,
  fixture net exit 0 ×8, the two iOS blend carriers still 1.0000"): DISCHARGED by `wave52-open` — 0 gained / 0 lost /
  0 movers / 0 newly measured / 0 unmeasured over 4117 cells against wave51-fix, fixture net exit 0 on all 8 with 0
  unexpected pairs (blend-isolation / opacity-blend included) — `tools/titan/results/wave52-gate/{_note.md,score-open.txt,fixture-net-open.txt}`.
  Its trailing "standing constraints added this wave" sentence was dropped: both rules already live in Standing
  constraints (installed-APK sha1 per device A/B; artifacts COMMITTED under `tools/titan/results/<wave>-<lane>/`) and the
  scratchpad purge is an Operational recipe. Replaced by the wave-52 obligation 0 with sub-items (a)–(e).
- Obligations 1–7 kept unchanged: still referenced by queue items (#1 the wave-50 attribution, #3/#4 the degenerate-pass
  measurement, #7 the ledger), and nothing in them was discharged by wave 52.
- Queue items closed this pass (not obligations): 7(e³) (027 ×3 flipped FAITHFUL), 11(b) (align-items-007 natives
  flipped FAITHFUL), 0(b″)'s device A/B (done at the gate).

## 4. Every [[AB]] token left, in file order

1. Obligation 0(a) — `t5`, L6 T5 outside-marker hang (line ~268).
2. Obligation 0(a) — `ma`, L8 M-A `ch` measuring face (~270).
3. Obligation 0(a) — `f1`, L9 F1 hanging-punctuation fold / `drop-F1.patch` (~273).
4. Queue 1(d) — M-A device A/B (arm `ma`) (~1717).
5. Queue 2(d) — T5 device A/B (arm `t5`), exclude now `seam-1` reversed + `t5-exclude-ios.integrated.patch` (~1997).
6. Queue 4(f) — `drop-F1.patch` applied for good only if arm `f1` confirms the drop (~2333).

## 5. What else changed, and what could not be verified

Changed besides the tokens: header (Current: corpus-v6.18; gate-less history shortened to one paragraph; wave-52 refill
paragraph replacing wave-50's, keeping the S6 / retro label legend and the "pending the gate" meaning); Standing
constraints +3 (process ownership; no edits under a running gate; lost-outside-pre-registration stops the ship); new queue
entries 0(l″), 1(e), 2(f), 2(g), 4(g); in-place additions to 2(c⁴) (counter-suffix degenerate), 5(e)
(gradient-powerless-hue `li { display: flex }`), 0(ac), 0(ad), 7(e¹) (closing-gate verdicts), 9(m) (ChUnitMetrics.kt 350,
ListItemMarkerGate 214 / 216, ChUnitMetricsTest 264 — `wc -l`), 11(h) B (the wave52-final IR carries `pseudos.marker
{content: none}` on 9 / 9 components of both tests; web still f 0.938 → the consumer), the blank-capture Instrument
bullet (43 at the gate); Known-broken: the gate-driver item rewritten to its RESIDUAL (`./gradlew --stop`, `pkill -9 -x
adb`), new `BackgroundImageURLTests.testPercentEncodedDataURIDecodes` item; Operational recipes: four new (background-task
2-hour limit / two launches; no edits under a running gate; Probe, then ship; pre-registered adjudication + every flipped
cell looked at), and the three host-wide-pkill sentences ("The preconditions are still yours", the port-guard recipe,
"Gate on a quiet host") rewritten to the own-processes rule; the stale "update SKILL.md in the ship commit" parenthetical
re-trued (the working-tree `.claude/skills/wave/SKILL.md` Phase 2 already states the opus / file-driven rule).

Could not verify, or verified only indirectly:
- Every `wave52-ship` number is the `wave52-preview` value (the gate of record is still running). The T-L4 byte-identity
  and the per-test IR reads are `wave52-final` facts carried to the ship by the control's argument (the closing fixes
  reach only `ch` / non-list-item `<li>` documents). The orchestrator's preview == ship check covers cells, not bytes.
- `tools/titan/results/corpus-v6-18.json` does not exist yet (written at ship); the header cites it.
- Cited paths that are UNTRACKED and must ship in the same commit: `tools/titan/results/wave52-gate/` (whole — 
  `adjudicate.mjs`, `adjudication.txt`, `build-hashes.txt`, `cell-review.json`, `device-ab.sh`, `tripwires.txt`,
  `tripwires.mjs`, `control-check.mjs`, `ab-diff.mjs`, `cells.mjs`, `review-sheets.mjs`, `frame-ink-census.mjs`,
  `closing-fixes-mutations.log` — IGNORED by `.gitignore:21 *.log`, so it needs `git add -f` (Fix pass 3 #1) —,
  `per-test-ir/css-lists/` (Fix pass 3 #2), …), `tools/titan/results/wave52-counters-and-lists/t5-exclude-ios.integrated.patch`,
  `tools/titan/results/wave52-vertical-wedges/ma-exclude-ios.integrated.patch`, and this directory.
- The `BackgroundImageURLTests` flake (1 of 2 full Catalyst runs failed on 2026-10-05, 2147 / 2147 on re-run) is the
  orchestrator's statement — no committed log; the `NSCache`-eviction cause is a plausible guess, untested.
- `gradient-powerless-hue-*` "label drawn inside the gradient box instead of to its right": from the brief; the PNGs were
  not decoded here (out of bounds). Scores were looked up.
- The "…" on the Android clamp hosts, `revert-val-002` web's "degenerate → honest" and `all-prop-001`'s picture: scores
  only — the gate review looked at flipped cells.
- `./gradlew --stop` stopping every daemon of that Gradle version for the user: Gradle's documented behaviour, not run.
- Fixture net of `wave52-ship`: unknown here; obligation 0 states only what `wave53-open` must print.
- `counter-reset-reversed-nested`'s extractor mechanism is NOT traced; `AUTO_CLOSE_TRIGGERS` (`li: ['li']`) is named as a
  suspect only.

## Fix pass (skeptic review of the ship-docs edits), 2026-10-06

Scope: `docs/BACKLOG.md` and `docs/STATUS.md` only (plus this section). Same bounds as above: files, grep, `sed -n`,
`git show`, python over JSON, `cells.mjs` over `wave51-fix wave52-calib wave52-final wave52-preview`; no build, test,
device, image or git-state command; nothing under `tools/titan/runs/`, no source or script file. Every skeptic check was
re-run before acting; all eleven held, none refused.

| # | sev | problem | re-check | action |
|---|---|---|---|---|
| 1 | must | 4(g) / 1(e) credit aaf676c5 with all three DEGENERATE hyphens flips | `cells.mjs`: out-of-flow-001 android `f 0.9218 → f 0.9218 → P 0.9619 → P 0.9685`, -002 `f 0.9291 → f 0.9291 → P 0.975 → P 0.982`, span-001 `f 0.9166 → f 0.9166 → f 0.945 → P 0.9532`; gate `_note.md` §3 probe rows name only span-001 among the three; `watchlist-additions.txt` lists out-of-flow-001 / span-001 as M-A movers; L8 `_note.md` at-risk list has out-of-flow-002 under "M-A (both natives, sizing ch)" (iOS, the then-passing twin); `device-ab.sh ma` reverses the Compose seam-1 and runs css-text | FIXED. 4(g): the two-step history (span-001 with aaf676c5; the out-of-flow pair already P in `wave52-final`, before aaf676c5) + a new `[[AB]]` — whether L8's M-A moved the out-of-flow pair is for arm `ma` (worded "whether M-A is what moved", not "which lane": the arm can only answer for M-A). 1(e): only span-001 is an aaf676c5 flip; the pair "had already flipped in the first attempt". Obligation 0(a)'s existing `ma` sentence gained the same question (no new token). |
| 2 | should | 1(e) "all FAITHFUL" covers three unreviewed hyphens-manual cells | python over `cell-review.json`: hyphens-manual-011 / -012 / -manual-inline-011 android absent; P 0.9811 / 0.9811 / 0.9739 at wave51-fix (P → P, `score-preview.txt` movers, not gained) | FIXED. "both FAITHFUL" now covers only hyphens-auto-010 / hyphens-punctuation-001; the three are "back above their wave51-fix scores … post-fix pictures not in the gate review (the gate note §2 geometry measures `wave52-final`)". |
| 3 | should | 0(l″) heading "Four tests" over three bullets | three tests / five cells; the sixth DEGENERATE composed-canvas cell is the ring-fenced test | FIXED: "Three tests (five cells)". The ring-fenced test stays in 0(l′) only. |
| 4 | should | STATUS prediction-miss list shorter than BACKLOG / §2 above | ORCHESTRATOR-TODO §5 L10 "rises … 045/046", "snap movers … (≤0.0014)", L2 "→ **0 / 0**"; `cells.mjs` 046 android `P 0.996 → P 0.9947`; `tripwires.txt` T-L2 `0 · 1 (expected 0 · 0)` | FIXED: STATUS now names L10's 046 android (P 0.996 → P 0.9947), L10's snap movers (≤ 0.0014 predicted, +0.0037 … +0.013 measured, not separable from Fix A) and L2's frame-ink 0 / 0 → 0 / 1. STATUS's list now matches §2 items 1–9 (item 10, blank captures 45 → 43, is already explained in STATUS's tripwire sentence). |
| 5 | should | STATUS "no longer kill processes they did not start" overclaims | `gate-driver.sh:136-137` `./gradlew --stop`; `section-runner.sh:641` and `provision-devices.sh:127` `pkill -9 -x adb`; `git show 20acb531`: "Residual … still host-wide" | FIXED: "no longer kill emulators, test browsers or web-port listeners they did not start", plus a closing sentence naming the two host-wide stops (BACKLOG Known-broken). |
| 6 | should | Recipe lists "the Gradle daemons" among what the driver stops "only" for this checkout | `own-processes.sh` defines only `own_emulator_pids` / `kill_own_emulators` / `_op_cwd_in_checkout` / `kill_own_test_browsers`, no Gradle; the Gradle stop is `gate-driver.sh` `./gradlew --stop` | FIXED: recipe now says the driver stops only the emulators and test browsers this checkout started and "still runs `./gradlew --stop` … host-wide for that Gradle version (Known-broken)". The optional reword taken too: the Standing constraint is now "must never stop … — a rule, not yet a fact" and its parenthetical names both violations. Same overclaim found and fixed in the sibling "Gate on a quiet host" recipe ("stops what this checkout started" → emulators and test browsers, plus the host-wide `./gradlew --stop`). The port-guard recipe already stated the residual — unchanged. |
| 7 | should | STATUS states the 21 / 13 exclusion as certain | BACKLOG Instrument bullet: `applyNaScoreGate` `return !delivered`, the tag turns post-load extraction on, PLAN §10's "excludes unconditionally" is the corrected error | FIXED: "up to 21 scored cells (13 passing) could have left the denominator, or moved on re-extracted fixtures, … (BACKLOG, Instrument decisions pending)". |
| 8 | nit | STATUS "no hyphen, a character break … fixed by the `ch` work" | `cell-review.json` out-of-flow-002: boxes 1, 2, 7 read `high-/way`; cells as in #1 | FIXED: "hyphens missing and the word broken at a character — only `-002`'s boxes 1, 2 and 7 hyphenate — while the box width now matches the reference; `-001` / `-002` already passed in the first attempt, `hyphens-span-001` flipped with aaf676c5". |
| 9 | nit | STATUS "0.98 → 0.89" wrong for -manual-inline-011 | `adjudication.txt`: 0.9811 → 0.8925, 0.9811 → 0.8983, 0.9739 → 0.8842 | FIXED: exact "0.9811 / 0.9811 / 0.9739 → 0.8925 / 0.8983 / 0.8842". |
| 10 | nit | Obligation 0(b) heading "Eleven DEGENERATE … first-lane material" includes the ring-fenced test | `cell-review.json` final DEGENERATE = 11 cells, basic-blur ios among them | FIXED: "Ten DEGENERATE gains and one wrong picture that passes"; the eleventh is a parenthetical "RING-FENCED — reported only, in 0(l′); never a target". |
| 11 | nit | 0(b″) "Device A/B DONE" without saying the exclude arm has no hash | `build-hashes.txt` blocks: wave52-final ×2, -probe, -probe2, -probe3, -ship (header only, gate running); no wave52-open hash anywhere under `wave52-gate/` | FIXED: "(the exclude arm predates the hash record — its side is a score; the byte-identity of the four PNGs is what carries the result)". |

Counts after the pass: `docs/BACKLOG.md` 4117 lines, `[[AB]]` **7** (the six of §4 + queue 4(g), arm `ma`), `[[GATE]]` 0,
`scratchpad/` 0; `docs/STATUS.md` 2773 lines, `[[AB]]` **1** (the three-arm sentence, unchanged), `[[GATE]]` 0. Every
new `[[AB]]` names one of the three pending arms (`ma`). The ring-fenced test appears in no new sentence; 0(b) now
keeps it out of the work list.

For the orchestrator (outside this pass's files, left untouched):
- `tools/titan/results/wave52-gate/_note.md` §5 carries the same attribution as #1 ("only the box width (the `ch` fix)
  now matches" for all three hyphens cells); `cell-review.json`'s span-001 reason ("wider boxes from the ch fix") is
  right for that cell only.
- `tools/titan/gate-driver.sh`'s `stop_our_processes` comment still says "Only OUR processes: … and the Gradle daemons of
  both build roots" — the source-side twin of #6 (a script, not edited here).

## Fix pass 2 (second skeptic review of the ship-docs edits), 2026-10-06

Scope and bounds as in the first fix pass: `docs/BACKLOG.md`, `docs/STATUS.md` and this section only; files, grep,
`sed -n`, python over JSON, `cells.mjs` over `wave51-fix wave52-calib wave52-final wave52-preview`, plus reads of the
local WPT mirror (`tools/wpt/…`, gitignored, cited in no doc). No build, test, device, image or git-state command; nothing
under `tools/titan/runs/`, no source or script file. Every check was re-run before acting. Nine of ten held and were
fixed; #5 asked for no change to these two files, and none was made.

| # | sev | problem | re-check | action |
|---|---|---|---|---|
| 1 | should | STATUS lists the two `flex-gap-decorations-033` natives among "the losses PLAN §1 had not foreseen" | `PLAN.md:59` "**Lost must be exactly 2** — the two 033 native cells, named in advance"; `cells.mjs` 033 ios/android `P 1 → f 0.94`, display-contents-root-background ×3 `P 0.9988–1 → f 0.534–0.5355`, column-span ×3 `P 0.9879 → f 0.9332 → f 0.9453` | FIXED: "the six losses PLAN §1 had not foreseen (its "lost must be exactly 2" named only the two `flex-gap-decorations-033` natives, and did not survive the calibration): display-contents ×3 … column-span ×3". The skeptic's own wording was made unambiguous (as written, "the two 033 natives … did not survive" read as if the cells were the subject). |
| 2 | should | `hyphens-auto-last-word-001` ios (f → P, FAITHFUL, an L8 M-A mover) absent from BACKLOG, untied to `ma` in STATUS | `cells.mjs` ios `f 0.9032 → f 0.9032 → P 0.9991 → P 0.9991`, android `f 0.8697 → f 0.8697 → f 0.9127 → f 0.945`; `grep last-word docs/BACKLOG.md` → none; `watchlist-additions.txt:6-7` "M-A movers … can move, cannot be lost"; `device-ab.sh:31` `ma` runs css-text; `cell-review.json` FAITHFUL ("Test example" on one line, all six boxes match) | FIXED. 1(d): a measured sentence — `wave52-ship … hyphens-auto-last-word-001 ios P 0.9991` (f 0.9032; already `wave52-final` P 0.9991, before aaf676c5; FAITHFUL with the reviewer's reason; android f 0.8697 → f 0.945), "on L8's M-A mover list … unpredicted as a flip (whether M-A moved it is arm `ma`'s question)". 0(a)'s existing `ma` sentence now asks about it too (no new token). STATUS: the cell is tagged "on L8's M-A mover list" in the unpredicted-gains list, and the attribution question sits INSIDE the existing `[[AB]]` sentence ("L8 M-A (the `ch` measuring face — and whether it is what flipped `hyphens-auto-last-word-001` ios)"), not as a bare "arm `ma`" tag outside it. |
| 3 | should | STATUS credits L9 with "hanging punctuation" | BACKLOG 4(f): F1 "CSS-correct for a non-hanging UA … Real progress there needs `hanging-punctuation: last` implemented"; `cells.mjs` ios `P 0.9756 → P 0.9555`, android `f 0.9495 → f 0.9452` | FIXED as proposed: "**L9** the hanging-punctuation fold (F1, CSS-correct for a non-hanging UA, not hanging punctuation itself; a measured loss, below)". The `[[AB]]` sentence's "L9 F1 (hanging punctuation — …)" had the same overclaim → "(the hanging-punctuation fold — …)". |
| 4 | should | 5(e) "each label drawn inside its gradient box instead of to its right" and "a SECOND, layout defect" have no committed look | grep over `tools/titan/results/` and `docs/` for the picture claim: only BACKLOG itself and this note's §5 ("from the brief; the PNGs were not decoded here"); the gate note §3 gives scores only; `cells.mjs` ios f 0.9334–0.9445, android f 0.6991–0.755 (`wave52-final` → preview +0.009…+0.012) check out | FIXED by rewording (looking at the pictures was outside this pass's bounds): the picture clause and "with a SECOND, layout defect" are gone; the item now says the four documents are `li { display: flex }` (confirmed in the WPT source, line 13), the marker fix took their stray marker away, and they still fail "(scores only; their pictures were not in the gate review — open them before naming a second defect)". |
| 5 | should | gate note §5 says all three hyphens DEGENERATE cells show "no hyphen" and "only the box width (the `ch` fix) now matches" | `cells.mjs`: out-of-flow-001 android `… → P 0.9619 → P 0.9685`, -002 `… → P 0.975 → P 0.982` (P in `wave52-final`, before aaf676c5), span-001 `… → f 0.945 → P 0.9532`; `cell-review.json` -002: "Only boxes 1, 2 and 7 show 'high-/way'"; gate `_note.md:99-100` | NO CHANGE to BACKLOG / STATUS — the skeptic is right that both already state it correctly (4(g), 1(e), STATUS's degenerate list). The gate note is not this pass's file: **for the orchestrator before commit**, correct `wave52-gate/_note.md` §5's hyphens bullet to "-001 / -002 already passed in wave52-final; span-001 flipped with aaf676c5; -002 hyphenates in boxes 1, 2 and 7" (the same hand-off the first fix pass left). |
| 6 | nit | "the quiet-host check then refuses" / "counts as load and makes it refuse" / "so the check refuses rather than kills" overclaim | `gate-driver.sh:144-151`: the only refusals are `1-min load >= $GATE_MAX_LOAD` and `free+inactive < floor`; `own-processes.sh:47,71` only echo "… not touching it" | FIXED on all three lines: a foreign process "counts toward the quiet-host load / memory check, which refuses the gate when either crosses its threshold" (standing constraint; the preconditions recipe; "Gate on a quiet host", which also keeps "never a kill"). |
| 7 | nit | heading says "this project"; the opt-in host-wide kill is unnamed | every cited mechanism is checkout-scoped; `provision-devices.sh:166` `pkill -f 'qemu-system'` under `RESTART_FLEET` only; `grep restart-fleet tools/titan/*.sh` → only provision-devices.sh itself (gate-driver does not pass it) | FIXED: "this checkout did not start"; the parenthetical adds "and `provision-devices.sh --restart-fleet`, operator-requested only, still pkills every qemu-system". |
| 8 | nit | 2(c⁴) attributes counter-suffix's score to the T5 hang without `[[AB]]` | 0(a) assigns "which list cells the hang moved on device" to `t5`; ORCHESTRATOR-TODO §6 t5 cells start with counter-suffix ios; the source is `cell-review.json` (android second reader: "the LTR markers moving … to hanging in the padding, and … korean-hangul-formal drawing 일/이"; ios: LTR markers "now hang outside") | FIXED: "the reviewers read the LTR markers now hanging in the padding (2(d)) and, on Android, Compose's korean arm (2(c³)) in the picture. [[AB]] Whether T5 is what moved the score is arm `t5`'s question (obligation 0(a))." ("on Android" added: the korean arm is Compose's, and only the Android reader named it.) +1 `[[AB]]`, arm `t5`. |
| 9 | nit | t5-list cells and the L6 seam-3/4 mover moved unrecorded | `cells.mjs` (wave51-fix → wave52-ship): first-letter-exclude-inline-marker android f 0.8554 → f 0.8466, ios f 0.8407 → f 0.8346; change-list-style-type-001 android f 0.8558 → f 0.9303, ios f 0.7671 → f 0.851; counter-list-item android f 0.7064 → f 0.7307, ios f 0.7518 → f 0.7864; name-case-sensitivity android f 0.9542 → f 0.9364, ios f 0.9535 → f 0.9354, web f 0.9593 unmoved; `watchlist.txt` 223-228, 246; L6 `_note.md:111` "name-case-sensitivity ×3 (seam-3/4 mover)" | FIXED: 2(d) gains one measured sentence (the fall named "FELL", the two rises, all still f) before its `[[AB]]`; 2(e) gains one for name-case-sensitivity (−0.018 on both natives, web unmoved, still failing; its two defects pointed to 2(c⁵)). No cause is claimed for either. |
| 10 | nit | 2(f) "the counters read 12 / 11 / 10 / 9 / 8" has no source | grep for "12 / 11" over the gate, docs and L6 notes → nothing about this test; the cell is P → P against wave51-fix (not reviewed); the WPT reference (`counter-reset-reversed-nested-ref.html`, local mirror) carries `data-marker` 3 / 2 / 11 / 9 / 8 / 1 | FIXED: "so the list is flat and its counter values are wrong, where the reference reads 3 / 2 / 11 / 9 / 8 / 1 (the painted values were not read out)". The IR half (five flat `<li>`, "One" dropped) is unchanged. |

Counts after this pass: `docs/BACKLOG.md` 4145 lines, `[[AB]]` **8** (the seven of the first fix pass + queue 2(c⁴),
arm `t5`), `[[GATE]]` 0, `scratchpad/` 0; `docs/STATUS.md` 2777 lines, `[[AB]]` **1** (the three-arm sentence, now also
naming `hyphens-auto-last-word-001` ios under M-A), `[[GATE]]` 0. Every `[[AB]]` names one of the three pending arms and
states no outcome. The ring-fenced test appears in no new or changed sentence. No DEGENERATE cell was turned into a fix;
the three new measured clauses (#2, #9) are scores, with the one reviewed picture (#2, FAITHFUL) quoted from
`cell-review.json`.

For the orchestrator (outside this pass's files, left untouched): #5's gate-note correction above.

STATUS: COMPLETE

## Fix pass 3 (third skeptic review of the ship-docs edits), 2026-10-06

Scope: `docs/BACKLOG.md`, `docs/STATUS.md`, and, because two fixes name them, two NOTES:
`tools/titan/results/wave52-gate/_note.md` §5 and `tools/titan/results/wave52-gate/corpus-v6-18.note.md`. One new
evidence directory was added: `tools/titan/results/wave52-gate/per-test-ir/css-lists/` (three files, `cp` only). Bounds:
files, grep, `sed -n`, `git check-ignore` / `git ls-files` / `git log -S` / `git diff --stat`, python over JSON, `cmp` /
`shasum` over three IR JSONs, `cells.mjs` over `wave51-fix wave52-calib wave52-final wave52-preview`. No build, test,
device, image or git-state command. Nothing under `tools/titan/runs/` was written (it was read to copy the three IR
files). No source or script file was touched. The gate of record has finished since the brief was written: its
`score-ship.json`, `tripwires-ship.txt` and `adjudication-ship.txt` exist (01:09), and gate `_note.md` §6 records
4096 / 4096 cells and 1435 / 1435 captures identical to the preview. Those files were read. Every check was re-run.
All ten held. Nine were fixed. #1 is handed off: both of its remedies are outside this pass.

| # | sev | problem | re-check | action |
|---|---|---|---|---|
| 1 | must | the cited `closing-fixes-mutations.log` is gitignored | `git check-ignore -v` → `.gitignore:21:*.log`. `git ls-files 'tools/titan/results/*.log'` → none. The file holds 9 mutations (G1, G2, C1–C3, F1–F4) and ends "RESULT: every mutation killed by exactly its named pins". `closing-fixes-mutations.py:123` (and its docstring, line 6) writes that exact name. A scan of every `tools/titan/results/wave52-*` path cited in the two docs (does it exist? is it ignored?) finds this file as the only ignored one; the only missing one is `corpus-v6-18.json`, which is written at ship. | **HANDED OFF — blocking for the commit.** `git add -f` is a git-state change, and a rename would mean editing the script's output path. Neither is allowed in this pass. The pointers in BACKLOG 1(e), STATUS and gate `_note.md` §3 stay as written: they become tracked paths after `git add -f tools/titan/results/wave52-gate/closing-fixes-mutations.log`. §5's must-ship list above now flags the ignore rule. Also found while here and fixed: 1(e) said "nine mutations" for the `ch` fix alone. Seven are the `ch` fix's (C1–C3, F1–F4) and G1 / G2 belong to the marker gate, so it now says "seven of the nine mutations … (C1–C3, F1–F4; G1 / G2 are the marker gate's)". STATUS's "Nine mutations" covers both fixes and is right as written. |
| 2 | should | 2(f) and 11(h) B point into the gitignored `tools/titan/runs/wave52-final/…/per-test-ir/` | `git check-ignore` → `.gitignore:86:tools/titan/runs/`. Python over the three files: `-list-item` and `-list-item-start` have 9 / 9 components carrying `pseudos.marker {"properties": {"content": "none"}}`. `-nested` has 7 components: Eleven / Nine / Eight have the root `<ol>` as `slot.parent`, the `<ol>` under "Two" has no children, and "One" is absent. | FIXED. The three JSONs were copied byte-verbatim to `tools/titan/results/wave52-gate/per-test-ir/css-lists/`, which is not ignored. sha-256: `e1dd5c58…` nested, `be13c651…` list-item, `fdc3c249…` -start. Each is identical in `wave52-final`, in `wave52-ship` and in the copy, which retires the old "extraction is untouched by aaf676c5" inference. 2(f) and 11(h) B now cite the copied path and that identity. They went under `results/`, not `tools/titan/fixtures/per-test-ir/`, because that tree holds pins and its README lists a consumer for each file. These three files are evidence only. |
| 3 | should | STATUS L9 "a measured loss" and BACKLOG 4(f) "the loss came" attribute the drop to F1 without `[[AB]]` | `cells.mjs` android `f 0.9495 → f 0.9495 → f 0.9452 → f 0.9452`, ios `P 0.9756 → P 0.9756 → P 0.9555 → P 0.9555`. The calibration leaves the cell unmoved, so the drop came in the render lanes. Which change caused it is the arm's question. L9 `_note.md:270-272` (drop-F1 applied only if the A/B confirms). L9 `_note.md:132`: F1 has one carrier. | FIXED. STATUS: "its one carrier fell, below". The attribution question went INSIDE STATUS's existing `[[AB]]` sentence ("L9 F1 (… — whether it is what dropped `hanging-punctuation-inline-001`; …)") and was not added as a bare "arm `f1`'s question" tag in the lane list. Fix pass 2 #2 set that precedent, and it keeps every A/B reference under its token. BACKLOG 4(f): "**Measured: the cell fell — Android to 0.9452 (predicted 0.9454), iOS 2.8× the predicted drop**". The skeptic's wording, "its predicted 0.9452", was corrected: the prediction was 0.9454. The iOS drop was 0.0201 against a predicted 0.0071, which is 2.83×. 4(f)'s existing `[[AB]]` now opens "Whether F1 is what dropped the cell is the device A/B's question (arm `f1` …)". Obligation 0(a)'s `f1` sentence asks the same question, with no new token. |
| 4 | should | STATUS L6 "the Compose multicol float-band height" contradicts 3(b) | BACKLOG 3(b): paint-clamped, "the box itself stays 3–5 px short". Counters `_note.md:14`: "paint-only clamp". `cells.mjs` multicol-003 android `P 0.9881 → P 0.9881 → P 0.9968`, balancing-003 `P 0.9857 → P 0.9857 → P 0.997`, no flip. | FIXED with the skeptic's wording: "the Compose far-edge border band of a too-short multicol float box painted inside the box (paint-clamped; the box stays 3–5 px short)". |
| 5 | should | gate `_note.md` §5's hyphens bullet ("no hyphen … only the box width (the `ch` fix) now matches") is wrong, a correction already handed off twice | `cells.mjs`: out-of-flow-001 android `f 0.9218 → f 0.9218 → P 0.9619 → P 0.9685`, -002 `f 0.9291 → f 0.9291 → P 0.975 → P 0.982`, span-001 `f 0.9166 → f 0.9166 → f 0.945 → P 0.9532`. `cell-review.json`: -002 "Only boxes 1, 2 and 7 show 'high-/way'"; -001 breaks `highwa/y`, `h/ighway`, `high/way`; span-001 all nine `highwa` / `y`. | FIXED in gate `_note.md` §5. Out-of-flow-001 and span-001 show no hyphen and break at a character; -002 hyphenates only boxes 1, 2 and 7; the box width now matches. `-001` / `-002` already passed in `wave52-final` (before aaf676c5), and `span-001` flipped with aaf676c5. The bullet does not attribute the box width to a fix; it points to BACKLOG 4(g), whose `[[AB]]` asks the `ma` question. The `s-11-1-1b-006` bullet in the same list now carries "reviewers' mechanism guess, untraced" (#9's twin). BACKLOG and STATUS were already right and are unchanged. |
| 6 | nit | STATUS rounded scores | `cells.mjs` collapsed-border web `f 0.8733` / `f 0.8741` / `f 0.8733 → P 1`; display-contents-root-background `P 1 / 0.9994 / 0.9988 → f 0.534 / 0.5346 / 0.5355` | FIXED: "0.8733 / 0.8741 / 0.8733 → 1.0" and "P ≈1.0 → f 0.534–0.5355". The snapshot note had the same "f 0.535", fixed there too (#7). |
| 7 | nit | `corpus-v6-18.note.md` "284 movers" | python: `movers` 295 in `score-ship.json` and `score-preview.json`. `score-preview.txt:4` reads `movers(|Δ|>=0.005) 295`. `make-corpus.mjs:71` writes `movers: record.movers.length`, and `:52` reads the note file as the `_note`. | FIXED: 284 → 295. The same note had two more defects that earlier passes fixed in BACKLOG, and both were fixed here: "kill a process this project did not start" → "this checkout" (Fix pass 2 #7) and "f 0.535" → "f 0.534–0.5355" (#6). BACKLOG needed no change. |
| 8 | nit | 0(b″) infers T-L4 from `wave52-final`; STATUS says "Tripwires on the first attempt" | `tripwires-ship.txt`: `ok T-L4 … byte-identical to wave52-open: same same same same`. `diff tripwires.txt tripwires-ship.txt` differs only on T-L12 (TRIP → ok against the calibration captures: same 19 / 43, the two un-blanked cells now named) and on the tripped count (2 → 1). `adjudication-ship.txt`: "all five rules hold". | FIXED. 0(b″) now reads "(tripwire T-L4, measured on the gate of record: `…/tripwires-ship.txt`)", and the inference is gone. BACKLOG's four other `tripwires.txt` citations were re-pointed to `tripwires-ship.txt`, since their values are identical: 0(l′) L2, 3(b) T-L6, 5(j) T-L1 and the Instrument T-L12 bullet. STATUS: "Tripwires (first attempt and gate of record alike): …". |
| 9 | nit | reviewer mechanism guesses written as fact | `cell-review.json` s-11-1-1b-006 ios `second.mechanismGuess` ("lays out a display:table body as a block stack…"); hyphens-out-of-flow-002 android `second.mechanismGuess` ("the hyphenation dictionary never sees the whole word") | FIXED. BACKLOG 0(l″) and 4(g) now say "Mechanism guess (reviewer, untraced): …". The CSS 2.1 §17.5 / §17.2.1 statements after the 0(l″) guess are spec facts and stay unhedged. STATUS: "(the square 5 px high — reviewers' guess: a `display: table` body laid out as blocks)". Gate `_note.md` §5 has the same label (#5). |
| 10 | nit | the `--gate-set` recipe lists 7 fixtures | `tools/visual/gate-fixtures.txt` has 9 non-comment lines. `git log -S`: `transform-abspos-pivot` was added by 747b28e4 (the retrospective) and `all-then-color` by c565d2e3 (wave 52). The skeptic's "which wave 52 added" is true of `all-then-color` only. | FIXED: all nine are listed in file order, with "(nine since wave 52 added `all-then-color`; `transform-abspos-pivot` is the retro's)". The wave-51 "exit 0 on all 8" record (line ~718) is history and was left as it is. |

Counts after this pass: `docs/BACKLOG.md` 4156 lines. `[[AB]]` is still **8**; two of them were reworded, not added (0(a) `f1` and 4(f), both now asking whether F1 caused the drop). `[[GATE]]` 0, `scratchpad/` 0. `docs/STATUS.md` 2782 lines,
`[[AB]]` **1** (the three-arm sentence, now also asking about `hanging-punctuation-inline-001` under F1), `[[GATE]]` 0.
No sentence states an A/B outcome. No new or changed sentence mentions the ring-fenced test. No DEGENERATE cell was
turned into a fix.

For the orchestrator, at commit:
- **Blocking:** run `git add -f tools/titan/results/wave52-gate/closing-fixes-mutations.log` (#1). A plain `git add` of the
  directory skips the file without a warning.
- Stage `tools/titan/results/wave52-gate/per-test-ir/` (#2). It is new and untracked, and nothing ignores it.
- `make-corpus.mjs` reads `corpus-v6-18.note.md` verbatim (`:52`). Write `corpus-v6-18.json` from the note as edited
  here (295 movers).
- Gate `_note.md` §6 cites `gate-driver/fixture-net.log`. That file lives under the gitignored
  `tools/titan/runs/wave52-ship/` and is cited nowhere in BACKLOG or STATUS. It was left alone; it is the orchestrator's section.
- Still open from Fix pass 1: the `stop_our_processes` comment in `tools/titan/gate-driver.sh` (a script, not edited).
- Observed, not touched: during this pass the working tree changed under source files this pass never opened.
  `runtimes/swiftui/…/StyleEngine/spacing/ChUnitMetrics.swift` is now modified (2 +/2 −), and
  `runtimes/swiftui/…/Renderer/ComponentRenderer.swift` no longer shows as modified. HEAD is still aaf676c5. This looks
  like a device A/B arm's exclude patch being applied. Check the tree is back to its ship state before staging any
  source file.

STATUS: COMPLETE
