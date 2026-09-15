# Campaign backlog — the single source of truth for wave work

This file is what a wave reads before doing anything and updates before
shipping. It replaces session-memory queues: any agent with this file, the
`/wave` skill (`.claude/skills/wave/SKILL.md`), and `docs/STATUS.md` can run
the next wave with zero conversation context. **There is no second queue**:
STATUS's "Open backlog" tables are a record, not a queue — a row that is
open lives here or nowhere (retro A10#11).

**Contract: every wave PR updates this file** — completed items removed,
new deferred/discovered items added with evidence pointers, the
next-wave obligations section refreshed. A wave PR that does not touch
this file is incomplete.

Corpus history and per-wave findings live in `docs/STATUS.md` (one dated
paragraph per wave) and `tools/titan/results/corpus-v*.json` (one snapshot
per wave, `_note` carries the full story). Current: **corpus-v6.17**
(wave 51 PR 1, device gate `wave51-fix` on a quiet host, 2026-09-15 — web
1215/1379 88.1%, iOS 1089/1369 79.5%, Android 1080/1369 78.9%; per cell
**3 gained, 0 lost, 0 movers** against wave50-final — exactly the three
cells wave 50 lost, recovered by the PercentSizeClamp intrinsic-pass fix
(queue 0(z)); 30/30 sections at 48/48/48 on the first attempt, fixture net
exit 0 on all 8; `node tools/titan/score-gate.mjs wave50-final wave51-fix`).
The wave-51 OPENING gate (`wave51-open`, the unmodified wave-50 tree) came
first and scored **0 gained / 0 lost / 0 movers over 4117 cells** against
wave50-final — the pipeline is deterministic run-to-run on this host. The
previous snapshot, **corpus-v6.16** (wave 50, `wave50-final`, 2026-09-14 —
web 1215/1379, iOS 1089/1369, Android 1077/1369; **40 gained, 3 lost, 6
newly measured**, 4117 scored cells with exact column parity), is what
v6.17 attributes against; its 3 lost cells were bisected on device and
attributed to neither the retro's nor wave 50's Compose code — a
conclusion wave 51 OVERTURNED (0(z): the bisection carried no install
evidence); its 6 newly measured are the retro-R13 unexclusions,
denominator changes by the rule. Before that, **corpus-v6.15** (wave 49 —
web 1205/1379, iOS 1081/1366, Android 1058/1366; 32 gained, zero lost).
All deltas attribute against the most recent snapshot, never across the
PR-#126 instrument change.
**Neither the retrospective's device gate nor wave 50's opening gate ran
before the lanes did.** The retro's 17 attempts (2026-09-05 → 09-07) all
failed on one cause — the host had no memory headroom (15 GB used, ≤230 MB
free, 5–6 GB in the compressor, load 13–25 with the user's browsers open):
adb-server wedges, `adb install` "success" with no package, feeder
timeouts, a wedged fixture whose `am force-stop` could not finish in 210 s,
vite dependency re-optimisation taking 20 min, and a section overrunning
the 50-min watchdog. The one section that completed (attempt 9,
css-position, at host load 9) was clean: 48/48/48 with the Android column
present. **Wave 50 was therefore built with NO device gate**: every
builder prediction below is a corpus simulation over the frozen
`wave49-final` per-test IR, a JVM / Catalyst pin, or a PNG replay against
the frozen refs — never a render (`tools/titan/results/wave50-gate/_note.md`).
The retro's rendering changes (R1–R6, F1–F3) AND wave 50's eleven applied
seam patches were both corpus-unmeasured until the wave-50 gate ran at ship
time and measured both at once (obligation #1 carries the attribution). The
nine harness defects the retro's attempts found are fixed, and since this
wave the gate itself is tracked code — `tools/titan/gate-driver.sh` refuses
a loud, reasoned exit 2 on a host that cannot hold it, instead of hanging
silently — and it ran its first gate clean: 30 sections, every column
complete, first attempt each.

This revision is the **wave-50 refill** (12 builder lanes B1–B12, eleven
applied seam patches, seven executed-repro skeptics S1–S7 and six fix
lanes F1–F6; every artifact under `tools/titan/results/wave50-*/`). It
replaces the 2026-09-04 retrospective refill (12 audit lanes A1–A12 over
waves 1–49, 195 executed-evidence findings; fix lanes R1–R13 + sweeps
P2a–P2e), whose conclusions survive in every entry that still says "retro
Rn". Skeptic S6's 18 verified corrections
(`tools/titan/results/wave50-S6/backlog-corrections.json`) are folded in
VERBATIM where it supplied a `correctedText`; S6 re-derived each from the
frozen evidence, never from a lane's prose. Every "landed" / "not landed" /
"delivered" sentence below was re-checked against the FINAL tree at ship
time by grepping the symbol and opening the file, and every `path:LINE`
pointer was re-resolved against the live line or replaced by a symbol —
wave 50 moved `ComponentRenderer.kt`, `StyleApplier.kt`,
`ComponentRenderer.tsx`, `PseudoBucketExtractor.kt` and
`extract-fixture.mjs`, so nine of the retro's line pointers had drifted.
"Pending the device gate" means the code is in the tree and its predicted
cell flips await the wave-50 gate.

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
- **Never commit `node_modules` in any form.** PR #126 accidentally
  committed it as an absolute symlink into a sibling worktree, which
  silently served THAT tree's web runtime to every vite capture (wave-48
  W5's GATE-CRITICAL find). Wave 48 removed the tracked entry and
  tightened `.gitignore` (`node_modules`, no trailing slash — the slash
  form only matches directories, which is how the symlink slipped past).
- **Suite runs on a shared mid-wave tree are single-writer**: concurrent
  gradle daemons produced spurious AGP jar-scan failures during wave-48
  skeptics and again during the retro fix lanes. Fix/skeptic lanes run
  focused tests only; the orchestrator runs ONE sequential full sweep
  afterwards — and that sweep INCLUDES the harness suites
  (`:app:testDebugUnitTest` in apps/android-harness, `npm -w
  apps/web-harness run test`): they pin the wave-45 em-margin ladder, the
  wave-40 flow-root rule and the wave-49 root-clip web rule, and were
  outside every documented sweep until the retro (A8#4;
  `doc-staleness-check.sh` now derives both counts).
- **A device A/B that claims to EXCLUDE a code change records the installed
  `base.apk` sha1 against the APK it built** (`adb shell pm path <app>` →
  `adb pull` → `shasum -a 1`, compared with
  `apps/android-harness/app/build/outputs/apk/debug/app-debug.apk`), per run,
  in the committed record — and the iOS twin hashes the installed `.app`
  container. The wave-50 lost-cells bisection recorded scores only; two of
  its four runs contradicted the code, and the mechanism it "excluded"
  (retro R2's percent clamp) was the cause (queue 0(z), wave 51). A run
  without that hash is a score, not evidence.
- **Evidence pointers in this file are repo paths or gate cells — never a
  session scratchpad.** The wave-49 refill pointed obligation #3 and queue
  0(a) at a session-scratchpad `wave49-i1/handverdicts.json` and
  `seam-A1-1.patch`; both were gone with the session before the next wave
  opened, and the campaign's "biggest known problem" lost its per-cell
  evidence (A10#0, A2#1). Grepping this file for the word scratchpad
  followed by a slash must return nothing at every ship. A pointer is one
  of: a tracked path; a
  gate cell written as `<run-id> <section>/<test> <platform> <verdict>
  <score>` (e.g. `wave49-final css-lists/counter-list-item android f
  0.7407`), which the snapshot's `reproduce` recipe regenerates; or a
  `tools/titan/results/corpus-v*.json` field. **Any lane artifact a later
  wave must act on — hand verdicts, classifications, deferred patches,
  census lists — is COMMITTED under `tools/titan/results/<wave>-<lane>/`**
  (precedent: `wave37-W5-decisions.json`, `wave38-N7-native-tails.json`;
  the retro's own artifacts land under `tools/titan/results/retro-2026-09-04/`).
  `_diag*` dirs, "the PR body" and "briefs on file" are not pointers.
- **A "zero lost" / "zero regressions" claim is derived from the per-cell
  diff of two run dirs** (the wave-48/49 method), never from totals or
  per-section pass counts. A1#1 found four waves (24, 36, 37, 39) whose own
  snapshots show per-section drops under a "zero regressions" note.
- **The scorer of record for lost/gained is `tools/titan/score-gate.mjs`**
  (wave 50; validated on history — it reproduces corpus-v6.15's +32 / 0
  from `wave48-final` → `wave49-final` exactly). It is the per-cell diff
  above, made code, and it prints five disjoint lists. **A cell whose
  `scoreExcluded` stamp was REMOVED between the two runs is NEWLY MEASURED,
  never lost**: it was absent from the old denominator and lands in the new
  one, usually as a fail. Wave 50 has two of them by construction
  (`css-counter-styles/counter-suffix` iOS + Android, unexcluded by retro
  R13), and a gate that lets them reach a "zero lost" sentence has reported
  a regression that did not happen.
- **A builder lane's blast radius is re-derived by a skeptic with its OWN
  census before it is believed.** Wave 50: B2's hunk-1 blast radius was
  reported as 9 tests and measured at **22 tests / 23 components** (S3's
  probe through the renderer's real inheritance merge — B2's script
  approximated "vertical writing mode" as "this document mentions one
  anywhere"); B7's `ua-hr` flip claim was "+3 cells" and measured at **+1
  web certain, +2 natives CONDITIONAL** on the baked rule painting (S7
  scored the full frame where B7 had scored only the gradient bands); B7's
  `table-duplicate-text` "+6" was **0 of its own** — byte-for-byte subsumed
  by B4's autoclose (S2 re-extracted all 2870 fixtures both ways and found
  0 changed). A lane's own census under-reporting its radius is the
  dangerous direction, and all three failures were in it.
- **Every "PASSES on all three" headline gets a PNG check** against the
  frozen ref before it is written (A1#0: wave 48's headline mechanism
  `background-image-006` is a red-square degenerate pass on both natives).
  ~35% of PASS cells are visibly wrong renders — a score is not a look.
- **The fixture net runs at every gate**: `BASELINE=1 ./test-all.sh
  --gate-set` iterates `tools/visual/gate-fixtures.txt` (retro R8a, A12#3),
  never `visual-test.json` alone — 6 of the 29 ledger lines at audit time
  (4 of the 27 lines now: the `composition-test.json` four; the two
  `filter-sepia-amounts.json` lines were the Sepia pair, deleted — see
  obligation #7) and all 5 `_expect.waive` blocks lived in fixtures
  nothing ran, so exit 5 could never reach them. The net works: wave 50's
  iOS overflow-clip fix (queue 6(d)) made the
  `radius-overflow-transform.json` `ROT_SelfRotate_ClippedChild` iOS waiver
  stale and it was DELETED in the same wave — 4 waive blocks remain
  (3 in `nested-transforms.json`, 1 in `filter-sepia-amounts.json`).
- **The snapshot's `artifact` and `reproduce --run-id` MUST equal the gate
  run-id.** corpus-v6-15.json SAID `wave48-final` for the wave-49 gate
  (A2#0, A1#4 — copy-forward error; following it would have re-run INTO
  the wave-48 dir); retro R11 corrected both fields to `wave49-final`
  (verified in this tree 2026-09-05). Phase 6 of the wave skill checks it.
- **Every "landed by Rn" / "not fixed" / "delivered" sentence in this file
  is re-checked against the FINAL tree at ship time, and every `path:LINE`
  pointer is re-resolved (or cited by symbol).** The retro's own BACKLOG
  refill (R12) verified its claims against a tree that concurrent lanes
  and the integrator then moved: at the round-2 skeptic pass (S6,
  2026-09-05) six landed/not-landed facts were stale (the v6-15 snapshot
  fields, two "red tests" already green, the 29-line ledger figures, an
  iOS twin file that had landed, a seam patch already applied, a counter
  already deleted) and 12 file:line pointers had drifted. Lane-time
  verification is necessary, not sufficient — the ship step (wave skill
  Phase 6 §4) re-runs the sweep on the tree that is actually committed,
  and a path-existence check (the Phase-3 S1 pointer audit resolves every
  backticked repo path against the tree; it strips `:LINE`) only proves
  the FILE exists, not the line — the line is confirmed by reading it.
- **Calibration runs (instrument change, same captures) carry a
  capture-hash check**: sha1 every capture PNG against the previous gate;
  identical bytes → an instrument-only flip, different bytes → a render
  change that must be adjudicated. corpus-v6-13-cal called three Android
  flips "pure instrument" when one (clip-path-blending-offset, 5b7657c1 →
  b1456a34) was PR #126's BlendModeApplier change (A1#2).

- **A ledger line is re-measured against a LIVE capture, never against two
  committed PNGs** (wave 50, the Sepia_Translucent lesson): the retro
  (R13) deleted both `filter-sepia-amounts.json / Sepia_Translucent.png`
  lines because the committed iOS and web baselines scored 0.9979 / 0.9986
  against each other — but the committed iOS PNG (#126, 2026-08-29) never
  matched what iOS renders (the 2026-08-28 ledger text had already
  recorded the live (95,117,129) against the spec's (90,92,95)), and the
  wave-50 device gate measured that same divergence live as exit 4. A
  baseline can be wrong; the ledger's `observed` block must come from a
  capture the gate made (`observedFrom` names the run), and a "now passing"
  verdict that did not come from a live gate is not a deletion warrant.

## Next-wave obligations (wave 51 opens with these)

1. **The wave-50 gate RAN (run `wave50-final`, 2026-09-14 21:14 → 22:44 UTC,
   quiet host, `tools/titan/gate-driver.sh`): 30/30 sections, every column
   48/48/48 on the first attempt; `score-gate.mjs` wave49-final →
   wave50-final = 40 gained / 3 lost / 6 newly measured / 46 movers;
   snapshot `tools/titan/results/corpus-v6-16.json`; the full account is
   `tools/titan/results/wave50-gate/_note.md` ("What the gate REPORTED").**
   Predictions that LANDED (every one replayed by skeptic S7 first):
   angle-units-001 ×3 (B1), gradient-hue-direction ×3 (B7 — the baked `<hr>`
   rule paints on both natives), contain-html-overflow-002 ×3 (B7),
   background-color-animation-with-table1/3 web+iOS and table4 ×3 (B4;
   +7), contain-content-004 web, broken-symbols both natives (+2 unclaimed),
   descriptor-suffix ×3 (+3 unpredicted, same auto-close), counter-suffix
   web, s-11-1-1b-005 web, position-relative-006 Android (B2 hunk 2),
   clip-path-contentBox-1d/-1e Android (B2 hunk 1), line-clamp-006/-007
   Android (B9), all five B3 ::after cells, css-gaps 045 natives +0.04 (B10,
   no flip as predicted); retro gains confirmed: currentcolor-001/-002
   Android (R3), box-sizing-026 Android (R2), css3-transform-scale-002 /
   3d-transform-incoming / backdrop-filter-transform Android (R1/R6), the
   rotate3d family +0.01 toward web. MISSES: block-ellipsis-032 Android
   0.9451 → 0.9397 still f (B9's ring, the thin claim); ch-units-vrl ×8
   Android +0.03..+0.09 no flip; direction-upright-002 web 0.58 → 0.70 /
   iOS → 0.60 no flip. **LOST — 3 cells, one family (queue 0(z) — FOUND
   AND FIXED in wave 51):** css-tables/height-distribution/
   percentage-sizing-of-table-cell-children-003/-004/-006 Android (P 0.9954
   → f 0.9966 colorFailed: the `overflow-y:auto; height:100%; min-height:
   100px` child of a `height:100%` table cell renders 0-tall and the abspos
   z-index:-1 red square shows). ~~Bisected ON DEVICE, four runs … Neither
   the retro's nor wave 50's Compose code is the cause~~ — **that bisection
   was WRONG** (`lost-cells-bisection.json` `_wave51_correction`): the cause
   is retro R2's `PercentSizeClamp` dropping the min floor in Compose's
   INTRINSIC pass, fixed in wave 51 and proven on device with the installed
   APK's sha1 verified against the build; the wave-50 A/B runs had recorded
   no install evidence. **Wave-51 opening gate — DONE** (`wave51-open`,
   2026-09-15 14:32 → 16:10 UTC, quiet host, `gate-driver.sh`): 30/30
   sections at 48/48/48 on the first attempt, fixture net **exit 0 on all
   8 fixtures** (the post-gate ledger/baseline edits and the iOS blend fix
   confirmed on device), and `score-gate.mjs wave50-final wave51-open` =
   **0 gained / 0 lost / 0 movers / 0 newly measured over 4117 cells** —
   the pipeline is deterministic run-to-run on this host, and the 3 lost
   cells reproduced byte-for-byte. Fixture net: exactly the pre-announced exits (17 stale ledger
   lines and 3 stale nested-transforms waivers DELETED, radius-overflow-
   transform passes on iOS) plus two iOS-only exit-4s — filter-sepia-amounts
   006_Sepia_Translucent (0.9931 / Δpx 17 % / ΔE95 11.5) and blend-isolation
   003_wrapper (0.9492) — resolved by the post-gate iOS lane (queue 6(x)).
   35 baselines refreshed after measurement + crop inspection; 30 of 33
   native PNGs moved TOWARD web (the retro's R1/R4/R6 and B11 on device).
   **For wave 51** the standing instruction is unchanged, with a driver
   instead of a recipe: **open with the full gate on a QUIET host** (idle
   1-min load < 6 with all of our processes stopped; ONE emulator; one
   simulator; browsers closed) — `tools/titan/gate-driver.sh <run-id>`,
   which refuses with exit 2 rather than starting on a host that cannot hold
   it — scored with `node tools/titan/score-gate.mjs <prev> <run-id> --json
   … --watch tools/titan/results/wave50-gate/watchlist.txt`. Deltas
   attribute against the most recent snapshot. ~~First act: verify
   `corpus-v6-15.json`'s `artifact` / `reproduce --run-id`~~ **DONE** — both
   read `wave49-final` in this tree (re-read at wave-50 ship time; retro R11's
   correction stands, and an opener that "fixes" them again would be editing
   a correct snapshot). The retro's rendering changes (R1 transforms, R2
   flex/sizing, R3 typography, R4 iOS borders/clip, R5 iOS fallbacks, R6
   effects, R10/R13 instrument+ledger) AND wave 50's eleven applied seam
   patches predict the cell flips listed per item below; this gate is the
   confirmation of both, and it refreshes the fixture baselines they move
   (`UPDATE_BASELINE=1` only after LOOKING at every changed PNG). **R4's
   iOS clip fix moves every committed iOS baseline carrying a rounded box
   with content past the curve** — 12 ledger lines are pre-marked EXPECTED
   STALE for it and for the Android blur pair (obligation #7), so budget an
   exit-5 pass whose only action is deleting them — AND an exit-1 pass on
   the iOS column that is expected, not a regression (obligation #7 names
   the components). **Three ANDROID baselines move too** — wave 50's
   placeholder-floor fix, obligation #7 — and a fourth changed Android PNG
   is its stop condition. The full expected-exit list, the denominator rule
   and the per-prediction confidence table are
   `tools/titan/results/wave50-gate/_note.md`; the per-cell watchlist the
   scorer reads is `tools/titan/results/wave50-gate/watchlist.txt`.
   **Gate-watch record — movers the lane reports under-listed, added by
   the round-2 skeptics (S3, S4; 2026-09-05), so the gate attributes them
   instead of debugging them:**
   (i) iOS `BASELINE=1` on `fixtures/visual-test.json` WILL flag
   iOS__019_BorderRadius_Uniform / __020_BorderRadius_Pill /
   __022_BorderRadius_Mixed / __101_Edge_VeryLargeRadius /
   __107_Edge_InsetRoundShadow as REGRESSED (exit 1) alongside the exit-5
   stale lines: the label now spills past the curve like web, and the
   committed iOS PNGs are missing that ink (S4 measured new-vs-committed
   SSIM 0.9416 / 0.9411 / 0.9465 / 0.9275 / 0.9414). Expected — PNG review,
   then `UPDATE_BASELINE=1` for the iOS column. Up to 13 more iOS radius
   captures shift by 0.2–0.9% px (BorderRadius_Circle, Card_Complete,
   Button_Primary, Button_Outline, Badge_Success, Avatar_Circle,
   Input_Field, Tag_Chip, Tooltip_Style, Glass_Effect, Neumorphic_Light,
   Edge_PercentRadius, Edge_DeepNesting); Button_Primary / Button_Outline /
   Edge_DeepNesting sit at the 0.95 edge (0.9536 / 0.9540 / 0.9510). Any
   exit-1 on an iOS component NOT in this list is a real regression.
   Gradient_Conic and Edge_GradientWithRadius keep the legacy clip (under
   background layers) and must NOT move.
   (ii) **Gradient_Radial (visual-test __029) is NOT a mover** — R5's
   lane note expected iOS to "move back onto its baseline" via the radial
   shape key; S4 measured the committed gradient FIELD as a 60×60 SQUARE
   on all three platforms (the "127×60" was the label-spill bbox), and on a
   square positioning area the farthest-corner circle and ellipse are the
   same picture. The shape-key movers are the non-square radial fixtures
   OFF the gate net (pairs-01 011, `background-image(-gradients).json`,
   `border-image.json`, `mask-image.json`, audit-phase12, `borders.combos`);
   if a gate-net witness is wanted, author a non-square
   `radial-gradient(circle, …)` component (e.g. 200×80, no label overlap)
   in a gate fixture and seed its baseline. If __029 moves, it is not the
   shape key.
   (iii) R6's Android dark-stage movers beyond the margin cells (no
   committed baselines, so no exit-5 risk — they surface as Android-moved
   rows in the fidelity/pairwise nets): `components/Toast` (bg alpha 0.85 +
   opacity 0.95), `components/Tooltip` (translucent bg),
   `components/MaterialCard` (margin 8 + shadow), the 18 fidelity
   `*_Decorated` combos (opacity .55/.7/.85 — the A11#2 class itself), the
   `fixtures/visual-test-controls.json` `*__no_background_color` shadow
   variants; and for the OutlineApplier Blink-model switch, pairs-02
   PW_Borders_Sizing_02/_04 and PW_Borders_Transforms_03 (outline
   ridge/groove with currentColor ink — `#eee` v=0.933 early-outs, so only
   the dark band moves) and fidelity `borders.combos` Borders_C14_CornerShape
   (outline ridge crimson). R6 named only `fixtures/properties/borders/outline.json`.
   (iv) The iOS BlinkBorderShade twin (0(f) below) is a RENDER change on
   iOS for every 0 < v ≤ 0.33 groove/ridge/inset/outset base: zero corpus
   carriers (S4: OutlineStyle in-corpus is SOLID ×16 / NONE ×2, 3D border
   declarations 0; S3: all 61 dark-stage 3D declarations use colours that
   early-out or pass the gate identically under old and new rules), so the
   PNG check is on fixture/fidelity baselines with dark 3D borders — look,
   do not assume.
   ~~**TWO red tests inherited from the retro**~~ **FIXED in the retro**
   (R12's two seam patches were applied by the integrator; re-run
   2026-09-05: `node --test tools/visual/gen-fidelity.test.mjs` 14 pass /
   0 fail / 1 skip, `tools/visual/cross-platform-gate.test.mjs` 33 pass /
   0 fail; ledger 27 entries). What they were, kept as the lessons:
   (i) `cross-platform-gate.test.mjs` `every seeded entry actually breaches
   the threshold it claims` failed because R13's honest re-measure brought
   `Sepia_Translucent.png` (iOS-web AND iOS-Android) back at ssim
   0.9979/0.9986, dpx 0.858%, ΔE95 0.574 — inside every threshold, so the
   lines excused a pair that was NOT diverging (the seed's 0.95 / 0.0 /
   20.0 block was round numbers, not a measurement — A12#1, A9#5).
   **Adjudicated and DONE: both lines DELETED** (29 → 27, the test's
   deliberately-hardcoded count moved to 27) — the exit-5 contract applied
   to the ledger's own numbers. If the live gate ever does diverge there it
   surfaces as a fresh exit 4 with real numbers, which beats a pre-excused
   pair.
   (ii) `gen-fidelity.test.mjs` `committed fixtures/fidelity/ files are
   byte-identical to a regeneration` failed because the seize-only header
   (A11#16) was hand-added to `fixtures/fidelity/motion/keyframes-basic.json`
   while `tools/visual/gen-fidelity.mjs` never emitted it, and
   `fixtures/fidelity/manifest.json` recorded the pre-header `"bytes":
   4279`. **DONE**: `buildMotionKeyframes()` emits `_capture`, the
   regeneration is byte-identical to the committed fixture, the manifest
   says 4463 (= file size). The wave-44 "lesson unapplied" shape in
   miniature — a generated fixture edited by hand — is the recipe entry
   "Seize-only motion fixtures" below.
2. The 327-net's fatal exit classes stay in force: exit 3 (non-sRGB
   capture), exit 4 (unledgered divergence), exit 5 (**stale
   ledger/waiver — the action is always DELETE the named line, never
   re-add**), exit 6 (spec oracle), and — since the retro (R8a, A9#0) —
   **exit 7 (a platform column MISSING or SHORT without its `SKIP_<P>=1`)**:
   fix the capture (adb pin via `ANDROID_SERIAL`, simulator, app crash,
   poll exhaustion); never "skip it away". The comparator's own halves: a
   PARTIAL column is exit 1 (every baseline the column skipped is a
   regression), an ABSENT column with committed baselines is exit 2, and a
   ledger line without a real `owner` / parseable `expires` is exit 2
   naming the `file:line` (A9#5; `compare-screenshots.mjs`
   `validateLedger`). The ledger (`tools/visual/cross-platform-expectations.json`)
   is co-maintained by every wave that touches rendering.
3. ~~**The pass column is ~35% degenerate … and its per-cell evidence is
   LOST**~~ **DISCHARGED (wave 50, lane B12) — the rate is re-measured and
   the evidence is in git.** Pointer:
   `tools/titan/results/wave50-B12/handverdicts.json` (with its committed
   sampler `draw-sample.mjs` and the method in the same directory's
   `README.md`). **35 of 100 randomly-drawn PASSING cells are visibly wrong
   renders — 35.0%, Wilson 95% [0.264, 0.448]**; dropping the two cells on
   the severity rule's 4 px boundary gives 33%, [0.246, 0.427]. The draw is
   a simple random sample without replacement (mulberry32 seed 20260914)
   over all **3344** passing scored cells of `wave49-final`, reproducible
   byte-for-byte; every verdict quotes a measured quantity, not an
   impression. Wave-49 lane I1's lost 27/78 = 34.6% sits inside the
   interval, so **the campaign's "~35% degenerate" headline reproduces** —
   this time from committed per-cell verdicts. Skeptic S6 re-read 12 of the
   100 from its own seed and agreed with all 12. Per platform: web 11/36,
   iOS 12/38, Android 12/26. Categories: wrong-colour 11, wrong-text 8,
   wrong-position 7, wrong-size 5, missing-content 4.
   **The problem itself is NOT discharged — only its measurement.** The
   ranked mechanism table that replaces the veto hunt is
   `tools/titan/results/wave50-B12/README.md` §4 and its top five families
   are queued at 0(j)–0(n). Further committed evidence of the same class,
   found by lanes B5/B6/B10/B12 and verified by S6:
   - **12** `CSS2/floats-clear/floats-clear-multicol-*` cells pass at
     0.9839–0.9890 with the orange in column 0 where the ref puts it in
     column 2, on ALL THREE platforms — the exemplar is 12 cells, not the 8
     this entry used to record (S6-04; queue 3(a)).
   - **21 blank-vs-blank PASS cells at ssim 1.0000** — 7 tests whose frozen
     reference is a single uniform colour and whose captures are that same
     colour on all three platforms; a runtime that rendered nothing at all
     would score 1.0000 on every one
     (`tools/titan/results/wave50-S6/blank-vs-blank-passes.json`).
   - **42 captures that are a single uniform colour against a reference
     carrying ink, all still SCORED**, with scores up to 0.9959 for a page
     with no ink in it
     (`tools/titan/results/wave50-S6/blank-capture-vs-inked-ref.json`) —
     the population for the blank-capture guard under "Instrument decisions
     pending".
   - `css-gaps/flex-gap-decorations-033` natives P 1.0000 is blank-vs-blank
     against an ERASED reference (queue 7(e)).
   - `css-text-decor/text-decoration-shorthands-001` passes on all three
     while painting a black underline where the test asserts a green one
     (48 px of 234 000 — queue 9(b)).
   - **~67 cells of the passing column carry no information at all**: 2 of
     the 100 sampled cannot fail (blank-on-blank, or a pass condition that
     IS the absence of a mark). Queued at 0(n).
   The red-square slice keeps its committed predicate `node
   tools/titan/red-square-census.mjs wave49-final` (strict red
   r>200&&g<80&&b<80, capture >0.5%, ref 0%; retro R8b, A1#3): **179
   passing red cells (web 23 / iOS 66 / Android 90, 105 tests) and 87
   failing (15/35/37, 51 tests)**. **No colour-based and no scalar check
   reaches the rest** — obligation #4 is now a measured result, not a
   conjecture.
4. **Obligation #4's named successor was BUILT, PRE-REGISTERED, MEASURED —
   and it FAILED, for a structural reason. Both vetoes now ship DISARMED and
   the recommendation is to stop looking for a scalar veto over the pass
   column.** (a) The wave-49 **novel-ink veto** stays disarmed
   (`TITAN_NOVEL_INK_VETO=1` to arm; stamped on every diff for triage
   regardless): 66.7% recall on the census-selected defect set, **16.7% on
   an unbiased sample**, against a 95% bar. Do NOT arm it by lowering the
   threshold. (b) The **displacement-aware denominator** this obligation
   named — exclude divergent pixels a rigid translation of reference ink
   already explains — is built as `tools/titan/degenerate-veto-probe.mjs`,
   wired into `tools/titan/inject-wpt-block.mjs` (the block is computed and
   stored on every cell) and **SHIPPED DISARMED behind
   `TITAN_DEGENERATE_VETO=1`**, pinned both ways in
   `inject-wpt-block.test.mjs`. Its rule and its 95%-recall / ≤2%-FP
   decision bars were written down BEFORE it was run, on obligation #3's
   committed sample — the only defect set not selected by any rule
   correlated with its own bars. Measured once, on those 100 cells:
   **precision 0.9286 (13 true / 1 false), recall 0.3714 (22 missed)**,
   FP 1.5% (1/65; 2.4% among the 42 cells that genuinely differ) —
   re-derived from the PNGs by skeptic S6 and recomputed from the raw
   blocks by S5, both exactly. **The pre-registered rule fails
   conclusively**: the recall bar was 95% and the UPPER end of the
   measurement's interval is 53.7%.
   **Why, and why this is the useful part.** Of the 22 missed defects 15
   bind on the FRACTION bar, not the mass bar — *for a typical wrong render
   in this corpus the MAJORITY of the disagreeing pixels are explained by a
   small rigid translation of reference ink*. A wrong render is mostly a
   locally-explicable render with a minority of novel paint, so a
   displacement-aware denominator sharpens precision and spends recall. The
   committed post-hoc sweep (`tools/titan/results/wave50-B12/README.md`
   §5.3, reported and NOT adopted) shows **no operating point of the metric
   family reaching 95% recall at usable precision** — the two rows that
   reach high recall have precision 46.7% against a 35% base rate.
   **The next step is NOT another scalar veto.** Keep the block as a triage
   stamp (precision 0.9286 means a fire is almost always a real defect — on
   this sample it would have surfaced 13 real degenerate passes for one
   gradient false alarm), spend the effort on obligation #3's ranked
   mechanism families (0(j)–0(n)), and fix the two DENOMINATOR problems no
   veto can touch: the absence-only class (0(n)) and the blank-capture
   family (Instrument decisions pending). Cost, measured, so nobody
   re-litigates it: `computeDegenerate` averages 6.8 ms/cell over the 144
   `css-tables` cells (S5's own probe 13.8 ms), worst 35 ms on that section
   and 985 ms on the 390×9470 `text-decoration-inset-025` Android capture —
   not a gate-runtime risk while the seam is dark. Known limitation, named
   not hidden: the single false positive is a smooth colour ramp, because no
   small translation carries an interpolated sample onto its neighbour;
   `novel-ink.mjs` guards that class with a palette-coverage precondition
   and this probe has none — adding one AFTER seeing which cell it fixes
   would be tuning, so it was deliberately not done. The 13 extraction-wall
   tests whose raw metrics pass on all three
   (`tools/titan/results/retro-2026-09-04/excl-detail-wave49-final.out`)
   remain a calibration set — A12#9 opened them and found mostly the
   degenerate-pass shape, not hidden wins.
5. ~~Column-presence check~~ DONE (wave 49, made CODE in the retro):
   `assertPlatformColumns` in `inject-wpt-block.mjs` (warns by default,
   fatal exit 3 under `TITAN_REQUIRE_ALL_COLUMNS=1`) — and
   `section-runner.sh` now SETS that variable for `--all-platforms`
   (`SKIP_IOS=1 SKIP_ANDROID=1` for `--web-only`) on both inject
   invocations, flags every native delivery failure (no slot, failed
   split, feeder non-zero, capture count ≠ per-test count, inject exit 3)
   as `NATIVE_SHORT`, and exits 1 after writing the manifest (retro R8b,
   A9#1; pinned in `section-runner.test.mjs`). The 327-net half —
   `test-all.sh` exit 7 + `compare-screenshots.mjs` partial/absent-column
   failure — shipped in the retro too (R8a, A9#0; proven by mutation in
   `tools/visual/test-all-guards.test.mjs` and
   `column-presence-e2e.test.mjs`). The wave-49 gate's "reprovision and
   retry once" guard lived only in the per-wave gate script and was never
   committed — the 50-min watchdog + reprovision is still prose
   (`tools/titan/README.md` "The device gate").
6. **Mid-run disk is a gate abort (<8G) — now CODE, not recipe**:
   `section-runner.sh` refuses to start below `TITAN_MIN_FREE_GB` (10)
   with exit 2 and a 30 s watchdog aborts the section (exit 2,
   `DISK_ABORT` marker in the section dir, feeders killed) below
   `TITAN_ABORT_FREE_GB` (8) (retro R8b, A10#12; pinned in
   `section-runner.test.mjs`). The first wave-49 gate attempt lost its
   Android column when the HOST hit 94% and destabilised the emulator
   into an adb "Broken pipe" during `:app:installDebug`. Start-only `df`
   is not enough — hence the watchdog. Pre-gate pruning recipe that
   worked: `xcrun simctl delete unavailable` + delete every Shutdown sim
   except the seed (81 sims → 5, CoreSimulator 38G → 11G), then `tmutil
   thinlocalsnapshots / 21474836480 4` — APFS holds freed space in local
   snapshots, so `df` barely moves until you thin them.
7. **The ledger is owned and re-measured; 12 of its lines are marked
   EXPECTED STALE and the gate's job is to DELETE them.** The retro
   rewrote `tools/visual/cross-platform-expectations.json` end to end
   (R13; A12#0/#1/#2/#8, A9#5) — verified in this tree 2026-09-05 (own
   dump of `tools/visual/cross-platform-expectations.json`): **27 lines**
   (29 at audit time; the two `Sepia_Translucent.png` lines were deleted
   in the retro — obligation #1), `validateLedger` (retro R8a;
   `compare-screenshots.mjs:868` → exit 2) reports **0 problems**, every
   `owner` is a lane/mechanism handle (`ios-borders-radius` ×10,
   `android-harness-placeholder-floor` ×6, `harness-composed-capture` ×4,
   `ios-effects-shadow-sigma` ×2, `android-effects-blur` ×2,
   `android-borders-radius-layer-painter` / `ios-borders-radius-layer-painter`
   / `android-effects-shadow-inset` ×1 each), and every `observed` block is
   a 2026-09-04 re-measurement under the shipping metric stack (the
   0.25/AA-off → 0.02/AA-on flip had left them stale: Neumorphic_Light
   pixelPct 0.47 recorded vs 10.8/11.0 measured, Edge_InsetRoundShadow
   iOS-Android 0.87 vs 3.2, and the Sepia block was round numbers rather
   than a measurement — A12#8; the working is
   `tools/titan/results/retro-2026-09-04/ledger-remeasure.json`). **The
   seed's "corner AA" story was wrong for 14 lines** — the divergence is a
   CLIP asymmetry — so the gate must expect exit 5 on the **12
   EXPECTED-STALE lines** (10 `ios-borders-radius`: BorderRadius_Uniform /
   _Pill / _Mixed, Edge_VeryLargeRadius, Edge_InsetRoundShadow — retro R4
   stopped `BorderRadiusApplier.swift` clipping in-flow content
   unconditionally; 2 `android-effects-blur`: Filter_Blur). **Exit 5's only
   action is DELETE the named line** — never re-date, never re-add. Any of
   the 10 iOS lines NOT going stale means R4's clip fix missed that
   component (S6). **The same R4 change also fires exit 1 on the iOS
   column** — the five BorderRadius/Edge components above now spill their
   label like web and score 0.93–0.95 against their committed iOS PNGs
   (S4) — expected; PNG-review, then `UPDATE_BASELINE=1` for the iOS
   column; obligation #1's gate-watch record carries the full list of iOS
   captures that may shift and the three at the 0.95 edge.
   Expiries: **6 lines on 2026-09-30**, all six the
   `android-harness-placeholder-floor` bug (Button_Outline ×2, Input_Field
   ×2, Edge_DeepNesting ×2 — queue 9(f)), and **21 on 2026-11-30**; expiry
   is warning-only, so those six need a decision (fix, or let them expire
   into exit 4), never a silent extension.
   **The six placeholder-floor lines now have a decision and a PROCEDURE,
   because the code fix is already applied** (wave 50 lane B11, in-tree in
   `StyleApplier.kt` — `placeholderFloorInsets` no longer subtracts the
   border band as well as the padding). What the gate owes, in this order
   and not out of it: (1) run the fixture net and LOOK at the PNGs — exit 1
   on exactly three Android baselines is expected,
   `Android__091_Button_Outline`, `Android__094_Input_Field`,
   `Android__105_Edge_DeepNesting`, each coming back canvas 390×62 with ink
   115×30 / 200×30 / 115×30 (they are 115×26 / 200×28 / 115×26 today), and
   091's / 105's border boxes also widening 48→50 and 46→50, which is part
   of the same one-line correction and not a fourth defect; **a FOURTH
   changed Android PNG is the STOP CONDITION — revert, do not refresh**;
   (2) `UPDATE_BASELINE=1 ./test-all.sh fixtures/visual-test.json` for those
   three; (3) **RE-MEASURE the pairs, then delete or REPLACE the six
   lines** — S7 puts the post-fix iOS-Android pairs at **091 0.9540 · 094
   0.9998 · 105 0.9510**, so 105 clears the bar by 0.001 and a pair that
   does not clear it needs a NEW line naming the REAL divergence (iOS draws
   the synthesized placeholder label at HALF width — 50 px against web's and
   Android's 115 — a width defect no ledger line names; queue 9(f)), never
   the survival of a height line whose reason has become false. Working:
   `tools/titan/results/wave50-B11/baseline-ink-boxes.json` (+ its
   re-runnable `.mjs`), `tools/titan/results/wave50-S7/_note.md`.
   **The 12 EXPECTED-STALE lines are unchanged** — exit 5, delete, nothing
   else. Four lines are scoped to
   `composition-test.json` — a fixture the OLD single-fixture gate never
   ran, so they could not go stale at all; the two `filter-sepia-amounts.json`
   lines were in the same position until the retro looked at them and
   deleted them. `--gate-set` (standing constraint above) is what makes
   exit 5 able to reach fixture-scoped lines, and the Sepia adjudication
   was the first result of looking.

## Decided, and still unstarted — the first PRs of wave 51, in this order

These are their OWN PRs because each moves every committed baseline of a
platform; they are not folded into a rendering wave.

- **(A) Harness debug-label chrome drawn OUTSIDE the paint chain on all
  three platforms** (A11#8). The shared spec promises byte-identical label
  rects at (8,6), but today the label is composited by the component:
  iOS draws it INSIDE the element's `.blendMode` group
  (`runtimes/swiftui/.../Renderer/ComponentRenderer.swift`, the `BlockLabel(`
  call inside the blend group — :4065 at wave-50 ship time) —
  blend-isolation `003_wrapper` iOS (97,193,242) == 0.3·bg + 0.7·(237·bg/255)
  exactly, Android/web (196,227,242) plain src-over → SSIM 0.9492, exit 4;
  Android places `BlockLabelPlaceholder` inside the content slot
  (`runtimes/compose/.../core/renderer/ComponentRenderer.kt`, the
  `BlockLabelPlaceholder(displayText)` call in the block-font label branch —
  cite it by that expression; the line moved 6191→**6288** in wave 50 and is
  re-resolved at ship time), so `align-items`/`text-align` move it
  (Layout_C01_AlignContent bottom-RIGHT, Layout_TextBlock right of the box
  0.89); web's zero-footprint absolute svg with `componentWidth ?? Infinity`
  (`apps/web-harness/src/sdui/ComponentRenderer.tsx`, the
  `layoutBlockLabel(visibleText, componentWidth ?? Infinity)` call — cite it
  by that expression; the line moved 1343→**1353** in wave 50) spills past
  width-less padded boxes that the natives clip — 14 WEB-LABEL-SPILL rows,
  one per `fixtures/fidelity/*.combos.json`. Together the label drives 51
  exit-4 fidelity fixtures
  (`tools/titan/results/retro-2026-09-04/fidelity-classified.csv`, column
  `class`). Design: draw the label as harness chrome after blend / opacity
  / clip / transform, pinned at (8,6) in the capture frame, never laid out
  by align-*/text-align/writing-mode; one PR, all three platforms, baseline
  refresh with PNG review. **Half of the iOS side already landed in the
  retro (R4, A12#0/A11#7) and is NOT this PR's work**: iOS was also
  CLIPPING the label, because `BorderRadiusApplier.swift` applied
  `.clipShape(BorderRadiusShape)` unconditionally where css-backgrounds-3
  §4.3 bounds only the element's own background/border painting at the
  curve and css-overflow-3 §3.1 clips descendants only when `overflow` is
  not `visible` (BorderRadius_Uniform read "BORD" on iOS vs
  "BORDERRADIUS UNIFORM" elsewhere). That fix is why 10 ledger lines are
  EXPECTED STALE (obligation #7). What remains for this PR is the
  COMPOSITING half — the blend-group, the align-driven placement and the
  web spill. Independent runtime bug surfaced alongside: Android applies
  `align-items:end` HORIZONTALLY to a non-flex Box (`ComponentRenderer.kt`,
  the `contentAlignment = displayConfig.alignItems.toBoxAlignment()` call —
  cite the call site by that expression; the line has now moved twice,
  2820→2845 between R12 and the retro ship and 2845→**2942** in wave 50,
  which is why it is a symbol pointer) — queue 9(g).
- **(B) Drop the web-harness `width: fit-content` initialiser for
  flex/grid items whose cross axis is `auto`** (A11#12; the initialiser is
  the unconditional `width: 'fit-content'` in
  `apps/web-harness/src/sdui/ComponentRenderer.tsx` — the
  `...(aspectRatioInlineUnconstrained ? {} : { width: 'fit-content' })`
  spread, :513 at wave-50 ship time with its rationale in the comment block
  immediately above; cite it by that expression, the file moved this wave —
  note it is ALREADY skipped for `aspect-ratio` and under `WPT_MODE`, so a
  third exemption is the established shape, not a new mechanism). `align-self: stretch` / default stretch
  of width-less children in COLUMN flex containers: iOS stretches
  (FC_AlignSelf child `d` 188 wide; N3_ColumnOfRows inner rows 264) —
  css-flexbox-1 §9.4 step 11 — while web and Android rendered them
  content-sized (48 / ~120): three IOS-ODD rows (0.9142–0.9667) in
  `fixtures/fidelity/trees/{flex-column,nested-3level,mixed-direction}.json`
  where the odd platform is the correct one. **The Compose half is FIXED
  (retro R2: `FlexCrossStretch` no longer gates stretch on composed-WPT
  mode; zero visual-test.json carriers, pinned)**; the web half moves every
  web baseline with a width-less flex child, hence its own PR. Re-pin the
  three trees only after both halves land.
- **(C) ~~Ledger owners~~ DONE in the retro (R13)** — all 27 lines (29 at
  audit time) carry a real mechanism-handle owner and a 2026-09-04
  re-measurement, and `validateLedger` passes clean (obligation #7). What
  is left is not a PR
  but a gate action: delete the 12 EXPECTED-STALE lines when exit 5 names
  them, and decide the six 2026-09-30 `android-harness-placeholder-floor`
  expiries (queue 9(f)) rather than extending them.

## Ranked queue (wave 51+)

0. **Wave-49/50 discoveries, retro corrections and the wave-50 skeptic
   findings, ranked above the older queue:**
   (a) ~~`tools/titan/extract-fixture.mjs` collapses competing declarations
   last-wins with no validity oracle~~ **DONE (wave 50, lane B1 — LANDED IN
   TREE, pending the gate).** `provablyInvalidDeclaration(prop, value)` is a
   deliberately CONSERVATIVE oracle (one rule: a `<dimension-token>` whose
   unit is outside the closed CSS unit tables) and `assignDeclaration`
   refuses an incoming value only when it is provably invalid AND the value
   it would overwrite is not — css-syntax-3 §2.2, "ignored, leaving the
   previously declared value in force". Every refusal is logged on the
   test's `extract.log` line; no silent fallthrough. **Blast radius: ONE
   document of 1435, ONE component of 2870 fixtures** — verified three ways
   (B1's two-runner-dir re-extract+re-convert differential
   `tools/titan/results/wave50-B1/recovert-differential.json`, skeptic S5's
   independent 15 797-declaration sweep, skeptic S2's bisected full-corpus
   differential `tools/titan/results/wave50-S2/changed-documents.json`), all
   three finding the same single test. Predicted flip:
   `wave49-final css-values/angle-units-001` web f 0.9990 cF / iOS f 0.9974
   cF / Android f 0.9966 cF → **P on all three** — skeptic S7 replayed it
   and called it the strongest flip claim in the wave (the repainted web
   capture is PIXEL-IDENTICAL to the already-passing sibling
   `angle-units-002`). Wave-50 fix lane F3 closed three classes of VALID CSS
   the first oracle refused, each found by S5's adversarial probe and each
   pinned + mutation-proved: custom properties (`--x: 3bananas`, which
   css-variables-1 §2 makes un-invalidatable), `unicode-range: U+0-7F` (the
   `<urange>` tail tokenises as number+unit), and `voice-pitch: 2st`
   (css-speech-1 `<semitones>`, which the converter types as `VoicePitch`).
   Population for everything below:
   `tools/titan/results/wave50-B1/duplicate-declaration-census.json` — 4073
   rule blocks scanned, **40 shadow sites in 36 tests**. **Scope, stated so
   nobody over-reads it** (skeptic S5): the oracle mirrors only UNIT
   validity. A value that is syntactically well-formed but wrong for its
   property is invisible to it, by design — "prove it or say nothing" — and
   widening it is a new decision with its own differential, not a tweak.
   (a′) **The IMPORTANCE half of the same collapse is still open, and S2
   sharpened it**: `div { color: red !important; color: green }` ends on
   `green` **and keeps `important: {color: true}`** — the surviving
   non-important declaration is PROMOTED to important, where css-cascade-5
   §6.4.4 says the important one wins. Zero corpus carriers; the 40-row
   census above is the population (e.g. `css-cascade/important-prop`,
   selector `from, to`, `border-color: ["green", "red !important"]`).
   (a″) **A splitter defect UPSTREAM of the oracle, pre-existing on both
   sides** (B1 §4, S2 defect 6): `url(data:image/svg+xml;base64,…)` is torn
   at the `;` by the declaration splitter, so the value becomes
   `url(data:image/svg+xml` and can silently delete a valid earlier
   `background-image`. The validity oracle cannot catch it — it is not a
   unit question. Zero corpus carriers.
   (a‴) **The LAYERED cascade path keeps the unguarded sort.**
   `resolveLayeredCascade` orders candidates by css-cascade-5 §6.4.4 and
   resolves `revert-layer` recursively, so the refusal would have to become
   a candidate filter inside that sort. All four provably-invalid corpus
   declarations sit in one UNLAYERED block, so a layered filter would move
   nothing today and could only risk the css-cascade cells. Stated in the
   code, not left as a silent gap.
   (b) **Compose percentage insets resolve against the wrong containing
   block LEVEL — FIXED in tree (wave 50, lane B2 + its applied seam),
   pending the gate.** The wave-49 diagnosis in this entry AND in
   `tools/titan/results/corpus-v6-15.json`'s `_note` ("`PercentInsetResolve`
   declines by design when the ancestor publishes no containing block on
   either axis … the code is right") **is WRONG and must not be re-derived
   from that snapshot** (S6-12, CONFIRMED). `PercentInsetPositioned` read
   `LocalContainingBlock` from inside a `Modifier.composed { }` factory,
   which materialises inside `ComponentRenderer.RenderComponentContent` —
   already inside the component's own `LocalContainingBlock provides
   childContainingBlock` — so it read the block the element publishes for
   its CHILDREN. On `css-position/position-relative-006` that is the green
   div's own (100, 100) and `top: -10000%` resolves to −10000 px; the
   element's REAL containing block is the parent's (100, null), where the
   same code correctly yields 0 px (CSS-indefinite ⇒ zero). The resolver
   never declined: neither axis is null at the level it was handed. Landed:
   `layout/position/ElementContainingBlock.kt` (the correctly-levelled
   channel, with a `PropertyTracker` breadcrumb —
   `Inset[containing-block-level-unpublished]` — on the fallback leg) + the
   read in `PercentInsetPositioned.kt` + the renderer `provides` line.
   Corpus discriminator (`node tools/titan/results/wave50-B2/census.mjs
   insets`): 7 bare-number inset carriers, the two levels differ on 6, the
   USED inset differs on exactly **1**. Predicted flip: `wave49-final
   css-position/position-relative-006 android f 0.9966 cF` → **P** (S7
   replayed 0.9967 with all vetoes clear; the Android capture is a bare RED
   100×100 square where web, iOS and the ref paint green). **Caveat the gate
   must carry**: the −006 pixel cannot by itself distinguish the two
   hypotheses — −10000% × 100 px equals the legacy number-as-pixels value —
   so the confirmation is the f→P, not the arithmetic.
   (b′) **The CSS 2.1 §10.1 republish for NON-BLOCK-CONTAINER ancestors is a
   real and SEPARATE gap**, still open, in
   `DynamicValueResolver.childContainingBlock`. It is not what blocked −006
   and it moves nothing on this corpus: its only two carriers, −002's
   `<span>` parent and −008's `<tbody>` parent, resolve to the same used
   inset with or without it (pinned as `PercentInsetResolveTest`'s L4/L5).
   (b″) **The SAME level defect exists in `spacing/MarginApplier.kt` and
   `spacing/PaddingApplier.kt`** and was NOT fixed (skeptic S3,
   `tools/titan/results/wave50-S3/probe-output/percent-spacing-levels.txt`):
   both read `LocalContainingBlock.current` from inside their own composed
   chain, i.e. the child level. Four carriers, all of them currently
   PASSING, so this is correctness rather than score:
   `wave49-final css-sizing/abspos-auto-sizing-fit-content-percentage-001`
   (MarginLeft −50%), `-002` (MarginRight −50%), `-003` (PaddingLeft 50%),
   `-004` (PaddingRight 50%) — each web P 1.0000 · iOS P 0.9992 · Android
   P 0.9984, and the element-level width is `null` on all four where the
   child level is 100. Port them onto
   `ElementContainingBlock.containingBlockFor` with an A/B, or state the
   divergence.
   (c) **`css-transforms/css-transform-3d-transform-style` (android) passes
   at 0.9577 against iOS's 0.9574 and is FRAGILE.** `OrthographicFlatten`
   still does not read `transform-style`; retro R1 narrowed it (it now
   yields to the exact 4x4 canvas route whenever the element's OWN
   perspective is in effect, `appliesTo(perspectivePx) = perspectivePx <=
   0`) but a perspective inherited from an ANCESTOR's `perspective`
   property is invisible to a Modifier — the renderer-threaded channel is
   the named seam (carriers `filter-effects/backdrop-filter-3d-transform-
   perspective`, `-nested-3d-transform-perspective`). Real preserve-3d
   compositing on Compose is the actual fix; treat the cell as a candidate
   to lose.
   (d) **`clip-path-contentBox-1d/1e` on Android is a WIDTH defect, not a
   clip defect — FIXED in tree (wave 50, lane B2 + its applied seam),
   pending the gate.** wave49-final android f 0.8775 / 0.8875 cF cvF; 1d
   web/iOS P 0.9585/0.9585, 1e web/iOS P 0.9696/0.9695. Measured from the
   frozen PNGs: the clip is CORRECT on Android (the 4 px padding band and
   the 4 px darkred border are absent, which is what `clip-path:
   content-box` must do) and the green box is at the same [24,24]-[123,123]
   as web and iOS. What is wrong is that `.clipped` (`display: inline-block`,
   no declared width) is stretched to the full 358 px composed canvas by
   `ComponentRenderer.blockFlowWidth`'s CSS 2.1 §10.3.3 emulation, so its own
   `background-color: red` paints a 24 200 px content box running to the
   canvas edge. §10.3.3 is scoped to BLOCK-LEVEL boxes; §10.3.9 gives an
   atomic inline-level box shrink-to-fit. 1e is the same defect twice —
   what Android draws is a 342×100 red pill with a green left cap, not a
   green circle, and there is no separate radius bug. Landed:
   `layout/position/AtomicInlineShrinkToFit.kt` (declared-display only;
   `inline-table` left to `TableBoxTree`, vertical writing modes left to the
   wave-47 orthogonal branch) + one `&&` clause on `blockFlowWidth`. S7
   replayed both: erasing the red band scores 1d **P 0.9585** (equal to web
   and iOS) and 1e **P 0.9601** (thin — 0.010 over the bar, the residual
   being the right-hand radius cap).
   **BLAST RADIUS — the number to trust is 22 tests / 23 components, not the
   9 the lane first reported** (skeptic S3 measured it through the renderer's
   real inheritance merge; B2's script had swept 13 tests into the frozen
   bucket by treating "this DOCUMENT mentions a vertical writing mode
   anywhere" as the per-component test). Android column only. The canonical
   table is `tools/titan/results/wave50-B2/README.md` and the watchlist is
   `tools/titan/results/wave50-gate/watchlist.txt`. **Eight currently-passing
   Android cells are marked at risk and SEVEN of them are real**:
   `inline-box-orthogonal-child-with-margins` P 0.9709 is the most exposed
   (its carrier declares `border-*-style: dashed`, so its own chrome IS
   painted and WILL move with the used width — lowest at-risk score and the
   only one whose ink is known to change), then
   `display-contents-before-after-003` P 0.9713,
   `baseline-with-orthogonal-flow-001` P 0.9817,
   `backdrop-filter-image-size-filter-size-mismatch` P 0.9841,
   `block-ellipsis-022` P 0.9863, `text-decoration-propagation-02/03` P
   0.9988. The eighth, `css-transforms/backface-visibility-hidden-006` P
   1.0000, is **blank-vs-blank** — its ref and all three captures decode to
   one uniform white with no ink anywhere, so a used-width change keeps a
   blank page blank and a gate that reports it "kept" has measured nothing
   (S6-N1). **`display-contents-before-after-003` is the one cell in the
   corpus carrying TWO wave-50 changes** — this one and lane B3's `::after`
   fold (queue 2(a)) — with 0.0213 of head-room. If it drops below 0.95, A/B the
   two SEPARATELY before blaming either: stub
   `AtomicInlineShrinkToFit.suppressesBlockAutoWidth` to `false`, re-run;
   restore it and stub `PseudoTextFold.resolve` to the identity, re-run.
   Both stubs switch off the MECHANISM — never narrow by test name.
   (e) Scientific-notation angles (`1e2deg`) survive as a Raw
   passthrough because `AngleParser.angleRegex` has no exponent branch.
   Zero corpus carriers today (grep over `tools/wpt/css/` finds none),
   so it is documented as a modelling limitation rather than treated as
   invalidity. Widening AngleParser touches transforms, colour hue
   channels, conic prefixes and gradient stops — wider than one lane.
   <!-- spec-cite-ignore-begin: this item QUOTES the wrong section numbers it records; tools/visual/spec-cite-validate.mjs --include-docs skips the block -->
   (f) **Spec-citation sweep — what the retro did and what is left.**
   The wave-49 list of "pre-existing wrong citations" was re-checked
   entry by entry on 2026-09-05 (retro P2d standardised the tree on the
   drafts.csswg.org ED numbering; S6 validated ~130 corrected numbers
   against the cached ED tables of contents): `PseudoBucketExtractor.kt`'s
   two `css-display-3 §2.5` comments for `display:none` suppression (:129
   and :156 after wave 50's B3 seam moved the file — cite them by the
   `css-display-3 §2.5` string, not by line) — §2.5 IS "Box Generation: the
   none and contents keywords" (correct, as A10 suspected);
   `BackgroundImagePropertyParser.kt:21` and `MaskImagePropertyParser.kt:63`
   cite css-images-3 §3.2 (radial gradients — correct under the ED);
   `GradientValueParsers.kt:21,31` and `BackgroundImagePropertyParser.kt:273`
   were CORRECTED by P2d to the dual-numbered "css-images-3 §3.4.3 /
   css-images-4 §3.5.3" (colour stops); `GradientRamp.kt` EXISTS (the
   wave-49 list gave no path — it is `runtimes/compose/.../color/GradientRamp.kt`)
   and P2d renumbered its interpolation cites to css-color-4 §13 /
   §13.3 / §13.4 / §13.5. Still unverified from that list:
   `PseudoTextFold.swift:21` (css-pseudo-4 §2 — that spec was not in the
   validated ToC set). **Residual, measured by S6's tree-wide validation
   of all ~3,300 `<spec> §n` citations against the same EDs: 27 (spec,§)
   combinations still name sections that do not exist under the ED, and
   two specs carry MIXED numbering** — css-backgrounds-3 border-image §5.x
   vs §6.x (×10 wrong) and background-position/-size §3.6/§3.9 (ED §2.6/§2.9);
   css-color-4 opacity §2.1 (×3 vs §3.3 ×20) and named colours §7.4 (§6.1);
   css-masking-1 §4.5/§4.8 (mask-repeat §7.3 / mask-size §7.7); css-ui-4
   §4/§4.2/§4.3 for outline (ED §3.1–3.5; §4 is resize); css-values-4 §3.2
   (case-insensitivity = §4.1) and §8.4 (<angle> = §6.3); css-images-3
   §3.5 (§3.2); css-images-4 §3.4.2 (§3.5.1); css-fonts-4 §3.4/§3.5/§3.7
   (shorthand = §2.7); css-multicol-1 §5.2 (column-rule is §4.x);
   css-lists-3 §5/§5.1 (counters are §4.x); css-position-3 §3.1.4.1
   (static position = §3.5.3); plus css-align-3 §8.1/§8.3 and css-flexbox-1
   §9.2.3/§9.7.4, which are TR numbers / algorithm STEP numbers written as
   sections. P2e's comment-only sweep ADDED seven of these
   (`BorderImageConfig.swift` §6.x for border-image, `BackgroundSizeApplier.swift`
   §3.9). The round-2 citation lane owns the renumbering; whatever it
   leaves is this item. ~~the durable fix is a cite-validate check in the
   doc-staleness job~~ **DONE** — `tools/visual/spec-cite-validate.mjs` is
   in the tree and in the doc-staleness job, and on the wave-50 tree it
   reports **0 unknown sections over 4443 citations** (skeptics S1 and S6
   both ran it independently).
   **What that check does NOT prove — and this is the residual now**
   (S5 defect, S6's own could-not-test list): `tools/visual/spec-sections.json`
   stores section NUMBERS only ("titles are one click away at url"), so the
   validator proves a section EXISTS, never that it is the section the claim
   needs. A wrong-but-real number passes silently. Two concrete re-checks
   for the first pass that has network: `images/ImageCandidateChain.kt` and
   `.swift` (shipped wave 49, not wave 50) cite **css-images-4 §2.5** for
   the `image()` candidate walk, where §2.5 is `cross-fade()` and the walk
   belongs to "Image Fallbacks and Annotations: the `image()` notation"; and
   `PseudoTextFold.swift:21`'s css-pseudo-4 §2, whose spec was never in the
   validated ToC set. The durable fix is to store TITLES beside the numbers
   so the check can be made semantic; until then a citation lane's output is
   "syntactically resolvable", not "right".
   <!-- spec-cite-ignore-end -->
   **The retro's own twin-pointer defect is CLOSED the other way round**:
   R12 recorded `BorderSideApplier.kt:477` as naming "a Swift
   BlinkBorderShade.swift" that nobody wrote — the integrator then LANDED
   R6's seam patch 03, so the file exists:
   `runtimes/swiftui/.../borders/sides/BlinkBorderShade.swift` (full
   `lightBandLifts` contrast gate), `BorderSideApplier.swift:311
   shade(_:light:)` delegates to it, and `BordersTests.swift:529
   testLightBandLiftsBlinkGate` pins (0,0,0) and (0.1,0.1,0.1). That makes
   it a RENDER change on iOS (gate-watch record (iv) in obligation #1:
   every 0 < v ≤ 0.33 groove/ridge/inset/outset base now lifts its light
   band where the old code lifted pure black only — zero corpus carriers,
   PNG-check the dark-3D-border fixture baselines). The Compose-side
   banners that P2e had written against the pre-seam tree ("no Swift file
   of that name exists", "lifts pure black only" — `BlinkBorderShade.kt`,
   `BorderSideApplier.kt`, `runtimes/compose/src/test/.../borders/BorderShadeBlinkGateTest.kt`;
   S3#3, S6#5) were re-trued by round-2 lane F1 and now name the landed
   twin (re-read at ship time 2026-09-05). S3#2's real parity residual is
   CLOSED the same round: the pure functions agreed on all 49 probe rows
   but the LIVE paths did not — Compose's `Color(r,g,b,a)` packs channels
   to 8 bits at construction while Swift's `UIColor(base).getRed` fed raw
   floats, so a raw colour straddling the contrast/early-out boundary got
   light bands up to 84/255 apart (zero carriers: every 3D-border colour in
   corpus and fixtures is k/255-exact hex/rgb()/named). Both twins now
   quantise with the same `pack8` (round-half-up k/255) before the gate —
   `BlinkBorderShade.swift` on its live `shade` path, `BlinkBorderShade.kt`
   as step 0 of `lightBandLifts` — and the flip row is pinned on both
   (`BorderShadeBlinkGateTest.kt`, `BordersTests.swift` the "8-bit-input
   flip row"); the 8-bit-input contract is stated in both banners.
   (g) **Retro finding A4#7 — the two iOS-only wave-40 T7 rules whose
   "DEFERRED Compose twin" banners never became tracked work.**
   `sizing/BorderBoxFloor.kt` is now PORTED (retro R2): explicit
   `box-sizing: border-box` floors an under-band declared size to its
   padding+border sum (css-ui-3 §3.1), wired in SizingExtractor, pinned by
   `BorderBoxFloorTest` on the verbatim box-sizing-026 IR. Predicted flip:
   `css-ui/box-sizing-026` Android 0.9966 colorFailed → PASS (the 10×10
   green dot over red becomes the 100×100 green square; iOS 0.9974 PASS
   since wave 40; exactly ONE explicit-BORDER_BOX under-band carrier in
   all 1435 wave49-final per-test IR docs, so no other cell moves).
   STILL DEFERRED, now queued: `typography/UAElementFontRule.kt` (twin of
   `UAElementFontRule.swift`) — `css-text-decor/text-decoration-color`
   Android 0.6164 FAIL; carry the iOS gate table's caveat that only
   leaf-text hosts improve (iOS 0.674 after the gated heading face). Owner
   needed; until then `UAElementFontRule.swift:80-84` should point here
   instead of promising a twin.
   (h) **Compose transforms after retro R1 (A10#1, A11#6, A11#0, A11#14) —
   what landed and what remains.** Landed, pending the gate: the skew
   route's Taylor `1 + z/P` (and the inverted `P/(P + z)` in its
   `perspective()` branch) is gone — every legacy route reads
   `TransformMatrixComposer.depthScale` = P/(P − z) (STATUS's "Medium:
   skew path" row is thereby closed); the 1000px `DEFAULT_PERSPECTIVE` is
   gone (css-transforms-2 §4.1: no perspective in effect → translateZ is
   invisible; pairs-04 040 drew 184×93 for 180×92); an exact 4x4 canvas
   route (`TransformMatrixPathApplier`, `TransformMatrix4`) draws the
   element's OWN `perspective()`/matrix3d/rotate3d/two-axis lists as the §6
   homography Skia rasterises (Perspective_500px/1000px, all 6
   PerspOrigin_*, Transform_Perspective, Audit_InlinePerspective were
   Android-odd 0.919–0.948 vs iOS≈web); the abspos pivot is conjugated by
   `PositionApplier.resolvedOffset` (`TransformPivot`; see 6b); two-keyword
   `transform-origin` resolves per axis (`TransformOriginKeywords`, iOS twin
   `TransformOriginResolver.swift`); `perspective: 0` is DECIDED as the
   spec's 1px clamp (css-transforms-2 §8 / §12.2 "smaller than 1px must be
   treated as 1px", `MIN_PERSPECTIVE_PX`) — the Perspective_Zero fixture
   will move at the gate and its baseline is refreshed after a PNG look.
   Remaining: (i) no fixture in the tree combines skew + perspective +
   translateZ (STATUS: "no fixture combined perspective() with
   translateZ"; `nested-transforms.json` has no skew member) — author one
   with shown arithmetic; (ii) pure-2D skew lists still take the legacy
   canvas route with its per-kind accumulation (6a); (iii) inherited
   ancestor perspective and preserve-3d hierarchies flatten per element
   (0c); (iv) `decomposeMatrix2D` reflections on the matrix() path
   (pinned; no corpus carrier).
   (i) **Twin-parity holes closed by the retro, with ZERO corpus carriers —
   correctness, not score** (A7#4, A7#3; verified wired in this tree
   2026-09-05, so no gate movement is predicted and none should be claimed):
   `font-size-adjust` was claimed on Compose under "no Compose analogue —
   parse-only" while the iOS twin has applied it since fidelity wave 2 and
   the web reference applies it natively — a FALSE registry claim over a
   silently-dropped property. `typography/FontSizeAdjust.kt` now ports the
   css-fonts-4 §2.6 multiply (`used = computed × number / metricRatio`)
   byte-parallel to the Swift twin, hooked at
   `TextStyleApplier.kt:203-213`, with a `PropertyTracker.markUnhandled`
   breadcrumb when the face is not the bundled Inter whose OS/2 tables
   (sCapHeight 1490 / sxHeight 1118 over unitsPerEm 2048) are the constants
   — no silent fallthrough. Separately, the 3D outline styles: iOS painted
   `groove`/`ridge`/`inset`/`outset` outlines as a flat solid ring
   (`OutlineShadedRing.swift` now draws the two-tone bevel, wired at
   `OutlineApplier.swift:123`), and Compose carried TWO different Blink
   `Color::Dark()` models in one runtime — an HSL lightness transform for
   outlines vs the subtractive max-channel model for borders, so one
   declared colour got two dark bands. `borders/sides/BlinkBorderShade.kt`
   is now the single palette and `OutlineApplier.darken` delegates to
   `BorderSideApplier.shade`. Corpus check that justifies "no movement":
   `OutlineStyle` values present in the corpus are SOLID (16 tests) and NONE
   (2), and zero `FontSizeAdjust` carriers exist in the wave49-final
   per-test IR.
   **(j)–(n) are the top of obligation #3's ranked mechanism table
   (`tools/titan/results/wave50-B12/README.md` §4), which replaces the
   abandoned scalar-veto hunt. They are ranked by measured cell count on the
   100-cell hand sample, so each number is a SAMPLE count — the class is
   larger in the corpus by roughly the sampling factor — and the per-cell
   verdicts naming them are in
   `tools/titan/results/wave50-B12/handverdicts.json`.**
   (j) **Out-of-flow static position — 7 of the 35 wrong cells, all three
   platforms, the single largest family.** The abspos child's static
   position inside a grid/flex/block container is wrong by 5–25 px, or its
   containing block has its axes transposed. Sampled cells: 000, 058, 076
   (web) · 020, 032, 036 (iOS) · 045 (Android). Owner: `layout/position`
   plus the grid/flex abspos paths on all three runtimes. Worth a lane on
   its own — and note that together with (l)'s clip/mask-origin pair and the
   one block/float vertical-placement cell, **10 of the 35 wrong cells are
   pure geometry with correct ink**: the mark is the right shape and the
   right colour, in the wrong place. That is the single largest thing the
   scorer cannot see, and (obligation #4) the class a displacement-aware
   instrument is structurally WORST at.
   (k) **The test's own failure-indicator ink reaches the canvas — 7
   cells.** The element the test expects covered, clipped or sized away is
   painted, so the capture displays the very mark that means "fail": 031
   (web) · 038, 046, 068 (iOS) · 012, 091, 098 (Android). It splits by
   mechanism rather than by platform — orthogonal-flow / available-size
   sizing (012, 031, 068), fragmentation (046, 091), anchor-position (038),
   float/clear (098). Worth a lane.
   (l) **The 16 px page-padding overrun — 3 cells, possibly ONE root cause,
   and the cheapest three in the table.** Cells 018 (Android,
   gap-decoration), 026 (Android, line-clamp) and 083 (web, line-clamp) sit
   in two different families but share one visible signature: **the block
   runs exactly 16 px wider than the reference, painting into the page's
   right padding all the way to the canvas edge** (`capInk.x1 == 389` where
   `refInk.x1 == 373`). Two platforms, three sections — check whether it is
   one cause before staffing the families separately. The same table's
   clip/mask reference-box pair (099 web, 028 Android — the mark displaced
   by exactly the 8 px padding on both axes, ink mass identical) is the
   other cheap geometric pair.
   (m) **A computed colour never reaches the text run — 3 cells.** A colour
   arriving via cascade, selector match or pseudo-element is dropped and the
   text paints in the inherited class: 087 (web) · 052, 055 (Android).
   Owner: cascade / selectors / pseudo. Distinct from the converter-side
   colour drop closed at 9(b).
   (n) **The absence-only class — a DENOMINATOR problem, ~67 cells, needing
   a decision rather than a fix.** 2 of the 100 sampled passing cells cannot
   fail: 053 is blank-on-blank (the reference paints nothing, the capture
   paints nothing, and a runtime that rendered NOTHING AT ALL would score
   1.0000) and 057's reference paints only the test's prose line, so its
   pass condition IS the absence of a mark. Extrapolated over the 3344
   passing cells that is **~67 cells of the passing column carrying no
   information**. Two neighbouring committed populations sharpen it: the 21
   literal blank-vs-blank PASS cells at ssim 1.0000
   (`tools/titan/results/wave50-S6/blank-vs-blank-passes.json`) and the 42
   uniform-colour captures scored against an INKED reference
   (`tools/titan/results/wave50-S6/blank-capture-vs-inked-ref.json`). The
   decision to take, explicitly, is whether these become `scoreExcluded` —
   which shrinks the denominator and lowers every headline percentage
   honestly — or stay counted with the caveat documented. **Do not decide it
   silently by shipping a guard**: the blank-capture guard under "Instrument
   decisions pending" addresses the 42 (a capture FAILURE), not these 21 (a
   test that cannot discriminate).
   (z) ~~**RANKED FIRST — the wave-50 gate's only losses: 3 Android cells,
   one test family, cause NOT in any runtime code.**~~ **FOUND AND FIXED
   (wave 51, commit 962effa5, measured on device with a VERIFIED install) —
   and the wave-50 bisection that "excluded" runtime code was wrong.**
   `wave50-final` css-tables/height-distribution/percentage-sizing-of-
   table-cell-children-003 / -004 / -006 android **P 0.9954 → f 0.9966**,
   colorFailed + novelInkFailed (10000 novel red px): the green `overflow-y:
   auto; height: 100%; width: 100px; min-height: 100px` child of a `display:
   table-cell; height: 100%` cell rendered 98×98 green at the wave-49 gate
   and 0-tall from wave 50 on, so the sibling `position: absolute; z-index:
   -1` red 100×100 square showed. The wave-51 opening gate (`wave51-open`,
   exit 0, ZERO per-cell diff against wave50-final over 4117 cells)
   reproduced the red byte-for-byte, so the defect was deterministic on
   this host, not timing.
   **Cause** — read from the code, then proven: retro R2's
   `runtimes/compose/…/sizing/PercentSizeClamp.kt` routes a PERCENT axis
   that also carries a min/max through ONE `Modifier.layout {}` block, and
   Compose answers an INTRINSIC query for such a block with the child's raw
   intrinsic (MeasuringIntrinsics / DefaultIntrinsicMeasurable ignores the
   `minHeight` the block passes). `TableApplier` sizes a row with
   `heightAtMinIntrinsic`, so the row asked the content-less green child for
   its min intrinsic height and got 0 — where the pre-R2 `heightIn(min =
   100)` SizeNode answered 100 — and the measure pass then clamped 100 into
   a (0, 0) band. -005 (same child WITHOUT min-height but WITH a 100px
   content child) never moved because its content IS the intrinsic; -007's
   200px child makes it inert too. The clamp file's own banner had named
   these exact three cells as carriers that "cannot move", from measure-pass
   arithmetic alone. **Fix**: the min/max SizeNode (`SizingClamps.minMaxBand`)
   is chained INSIDE the clamp on both axes — inert at measure time (the
   outer band is already tight or already (min, targetMax) and SizeNode
   constrains INTO the incoming band), the floor at intrinsic time; banner
   section "The INTRINSIC pass". Pin: `SizingMinMaxClampTest` §5, proven
   able to fail (dropping the height-axis inner node fails exactly one
   test). **A/B with the installed `base.apk` sha1 verified against the
   build** (one -read-only emulator, the provision flags, `feed-android.mjs
   --composed` over the five carriers, 2026-09-15): CONTROL (8864f33c, APK
   984c4a69…) -003/-004/-006 PNG sha1 19cda0efe724 = the wave-50 red,
   -005 8c0056dd8884, -007 e184684d71ce; FIX (APK fe4ec50a…) -003/-004/-006
   sha1 **8c0056dd8884 = the wave-49 GREEN capture byte-for-byte**, -005/-007
   unchanged; fidelity pairs-01 PW_Background_Sizing_04 still 300×80 on
   Android (the R2 gain kept). **Corpus effect MEASURED** by the full
   `wave51-fix` gate (2026-09-15 21:47 → 23:45 UTC, 30/30 at 48/48/48 first
   attempt, fixture net exit 0 ×8): `score-gate.mjs wave50-final wave51-fix`
   = **3 gained / 0 lost / 0 movers** — exactly these three cells, f 0.9966
   → P 0.9954 (the wave-49 score); Android 1077 → 1080/1369, css-tables
   34 → 37/47; snapshot `tools/titan/results/corpus-v6-17.json`. The
   lane's corpus carriers are exactly these three plus -007, whose 200px
   content child keeps the inner node inert.
   **Why the wave-50 bisection said otherwise.** Its four runs recorded
   SCORES ONLY — no APK sha1, no install evidence
   (`tools/titan/results/wave50-gate/lost-cells-bisection.json`, which now
   carries `_wave51_correction`) — and two of them (A/B 1 "lane disabled",
   A/B 4 "complete wave-49 runtime") contradict the code reading, so their
   rebuilt APKs most likely never reached the device. That is now a
   standing constraint (installed-APK sha1 in every device A/B record). The
   three hypotheses the record left open were each read against the tree
   on 2026-09-15 and none had evidence: every Gradle/npm/toolchain file is
   byte-identical between 5aa92ed6 and 8864f33c and `~/.gradle` holds ONE
   version of every Compose/Kotlin/AGP artifact (downloaded before both
   gates); the emulator launch line, AVD config and system image are
   unchanged; the capture settle is a fixed wall-clock delay with no
   host-speed term. What that reading DID show, worth keeping: the
   wave-49 gate captured an UNCOMMITTED tree (5aa92ed6 was squashed after
   the run), so "the wave-49 tree" is reconstructable only as that commit.

1. **Vertical-wedge residuals** (the wave-47 wedges are FIXED as
   mechanisms — W1 wave 48): (a) `css-break/background-image-006` **scores
   PASS on all three** (wave49-final web P 0.9990 · iOS P 0.9712 · Android
   P 0.9966, since wave48-final) **but both native cells are red-square
   degenerate passes** (A1#0: Android's 100px square is ~2/3 red with a
   small green block, iOS has a red right column plus a red stripe below;
   the ref is solid green with "no red"). Passing CORRECTLY needs multicol
   clone re-flow of content-bearing children — the documented
   `MulticolCloneMeasure.kt:102` bail, a mechanism gap with no currently
   FAILING carrier — and the fix will not move the pass column (the
   wave-48 headline "now PASSES on all three" is corrected in STATUS/the
   v6-14 note by the docs lane). (b) **`css-writing-modes/direction-upright-002` — the "why 2805px?"
   diagnostic is DONE; three named modelling gaps, two of them closed in
   tree.** wave49-final frames vs the 390×954 ref — web 390×**2805** f
   0.5826, iOS 390×2544 f 0.5758, Android 390×2316 f 0.5946; an ALL-THREE
   failure (A10#3), so the mechanism is upstream of every runtime. Lane B5
   measured it in headless Chrome on the harness's OWN composed DOM: the ref
   packs the eleven `body > div` floats into wrapped rows and we stack them
   one per row, because the first float lays out at **364×230** where
   Chromium lays it out at **98×150**. The three gaps, with the composed
   canvas each one buys (replica canvas 3045 px for the shipped tree,
   against the real capture's 2805 — good enough to attribute deltas, not to
   quote as a score): **(1)** `col` / `colgroup` / `ruby` / `rt` have no
   display model on any platform — they fall outside the harness
   `TAG_ALLOWLIST`, map to `<div>` and become BLOCK boxes, where
   css-display-3 gives them `table-column` / `table-column-group` / `ruby` /
   `ruby-text`; in a VERTICAL writing mode the block axis is horizontal, so
   every invented block box widens the element physically, which is why all
   three platforms blow the canvas by the same 2.4–2.9×. Fixed for web only,
   as the applied seam `apps/web-harness/src/sdui/UaBoxlessDisplay.ts` +
   its one-line `decorateStyles` hook → 2766. **(2)** the extractor stamped
   100×100 on the first `<col>` of each `<colgroup>` (the same "empty node"
   placeholder as 5(a)'s `<hr>`), which on a `<col>` PINS the table's column
   width — 10 components in this test; fixed in tree
   (`NON_BOX_GENERATING_TAGS` in `tools/titan/extract-fixture.mjs`,
   `extract-fixture-col-placeholder.test.mjs`, 4 pins, mutation-proved
   twice) → **1864**. **(3)** the residual 192 px against Chromium's 98 is
   ALL in the harness's per-text content wrapper — the colgroup boxes still
   carry the `display:block; padding:4px` content span, each `<rt>`'s
   content is an `inline-block` span so every annotation takes its own
   block-axis column instead of riding over its base, and the `<td>` texts
   are ALSO emitted as inline spans inside the `<tr>`. That last one is the
   duplicate-table-text class, now fixed upstream by lane B4's autoclose
   (queue 2(c)) — this test loses 13 duplicated table texts and is **the
   single most likely place in the wave for a large unpredicted move**.
   **Prediction, with numbers: (1)+(2) do NOT flip this cell** — two 192 px
   floats still do not fit the 358 px ICB, so the one-per-row stacking
   survives and the canvas goes 2805 → ~1750 against a 954 ref. Flipping it
   needs (3), which is decided-PR (A)'s territory. Where seam (1)'s value
   actually is: `CSS2/borders/border-conflict-style-107`, web pixel mismatch
   **39.276% → 10.431%** (natives 390×1404 / 390×1204 against a 390×600 ref,
   f 0.6564 / f 0.3262 — the lowest Android score of any carrier). Full
   per-test table incl. the seven passing cells the seam touches, all
   measured flat or better on web: `tools/titan/results/wave50-B5/carriers.json`.
   **Two gate-watch consequences of (2)**, both found by skeptic S2 after
   the lane's own census missed them: the predicate runs on the STATIC
   walker, where **two** tests carry the stamp, not one — the second is
   `css-tables/border-collapse-dynamic-col-001`, **web P 1.0000 · Android
   P 0.9812** (iOS f 0.9404), two currently-PASSING cells at risk; and it was
   invisible to an IR census because that test ran `[post-load:
   extracted+structure]` in wave49-final, where post-load **bailed 49× and
   declined 9×**, so bail-to-static is a live path and an IR census is not a
   safe proxy for a static-path predicate. (c) **`css-text-decor/text-decoration-inset-025` Android is a BLANK
   CAPTURE, not a tall render** (S6-14, CONFIRMED and widened). The PNG is
   390×9470 RGBA with ONE pixel value, `(0,0,0,0)` over all 3.69 M pixels,
   14 483 bytes — and it is scored like any other row: ssim 0,
   coverageRatioFailed, 99.83% mismatch (wave49-final android f 0.0000 cvF,
   frames [390,9470] vs ref [390,1230]). S6 confirmed it is the **only fully
   transparent capture among all 4305 wave49-final captures** (2870 of them
   RGBA), which is why the alpha-only predicate in
   `tools/titan/results/wave50-B5/blank-captures.json` finds exactly one. The
   boundary is the emulator's ~8192 px GPU render-target limit
   `VerticalRunIntrinsics.kt`'s own banner already records — the 7042 px
   `css-view-transitions/far-away-capture` Android capture directly above it
   is opaque white — and `ScreenshotManager.DEGENERATE_CANVAS_PX` (8192)
   logs the canvas on-device while nothing in `tools/` fails. **The 9470 px
   layout blow-up itself is still undiagnosed and could not be diagnosed
   this wave**: it needs Compose layout and `runtimes/compose` has no
   Robolectric, so the JVM suite can only exercise pure helpers. What is
   known: the container is a `columns: 2` multicol with 12 `inline-size:
   fit-content` children whose `<u>`/`<span>` chain carries unresolved `em`
   padding/margin/border; web lays it out at 750, iOS at 600, the ref at
   1230, and the Android/web height ratio of 12.63× is an outlier (next
   worst in the corpus is 4.67×). **The guard this test motivates is keyed
   on UNIFORM COLOUR, not on alpha** — see "Instrument decisions pending".
2. **Counter/marker follow-ups from the Rule-43 unexclusion** (W2 wave 48
   unexcluded 15 of 28; the refusal list in `tools/titan/inject-wpt-block.mjs`
   `NATIVE_FONT_PARITY_REFUSED_TESTS` carries per-key measured scores):
   (a) ~~Android blank-paint bug: `counter-cjk-decimal` renders ZERO ink
   from IR~~ **DIAGNOSED AND FIXED in tree (wave 50, lane B3's
   `compose-pseudo-after-textfold.patch`, APPLIED), pending the gate.** It is
   neither a counter bug nor a font bug: Compose composed an ordinary
   element's generated content as a wrapper AROUND the host
   (`ContentApplier.ContentWithPseudoElements` builds `Row { before,
   content, after }`) where CSS 2.1 §12.1 / css-pseudo-4 §4.1 put the
   `::after` box INSIDE the originating element as its last child — which is
   what web does and what iOS's `PseudoTextFold.swift` does. Outside the host
   box the `::after` had to share the line with the host's own block box,
   which in composed-WPT capture takes `fillMaxWidth()`; a Compose `Row`
   measures unweighted children in order, so the host consumed the whole line
   and the `::after` `Text` was measured at `maxWidth = 0` — it still
   occupied the row (wrapping one grapheme per line, which is where the
   canvas growth came from) but **drew nothing**. `counter-cjk-decimal`'s
   components are `::after`-ONLY (17 of them), so the whole 390×696 Android
   PNG is one colour, white, with no ink anywhere — an honest scored fail
   (wave49-final android f 0.9407 cvF, aCoveragePct **0.000** exact, vs iOS
   P 0.9934 and web P 1.0000). Landed as `content/PseudoTextFold.kt` +
   `content/PseudoTextBridge.kt` (the pure declaration gate, byte-parallel
   with the Swift twin; anything outside the gate refuses the whole bucket,
   named through `PropertyTracker`) + an `afterFolded` parameter on
   `PseudoBucketExtractor` so a folded bucket cannot ALSO render through the
   wrapper, + the renderer seam. **`::before` is deliberately NOT folded** —
   it is composed first in the same Row, already lands adjacent to the host's
   content edge and renders correctly today; 55 corpus tests carry a
   before-only bucket and moving them is a device A/B, not a lane change.
   Blast radius, replayed over all 1435 per-test IR documents and
   independently reproduced by skeptic S3: **13 tests / 78 components change
   path**; the 3 remaining `::after`-bearing tests are refused by the
   declaration gate and do not move. **Net prediction +5 Android cells** —
   `counter-cjk-decimal` f 0.9407 → P, `attr-notype-fallback` f 0.9584 → P,
   `attr-style-sharing-4` f 0.9959 → P, `counter-reset-reversed-pseudo-001`
   f 0.9884 → P, `-003` f 0.9961 → P (S7 scored the iOS picture as the
   Android cell for each: 0.9934 / 0.9993 / 1.0000 / 0.9999 / 0.9999, and
   showed the Android canvas height alone is FREE — extending the iOS
   picture with a white tail leaves the score bit-identical, so the taller
   canvas cannot keep them failing). **The thin one is `counter-cjk-decimal`:
   0.0434 of headroom against an iOS→Android raster gap that measures 0.0171
   on one passing sibling in the same directory and 0.0614 on another.**
   Four currently-passing Android cells are at risk, each ≥0.021 above
   threshold and each GAINING ink the reference has
   (`display-contents-before-after-001/-002/-003`,
   `display-contents-dynamic-before-after-001`); `-003` is the
   double-changed cell — see 0(d). Two cells to WATCH rather than predict:
   `css-lists/counter-list-item` improves (the 33 `" N"` runs come back and
   the canvas should collapse 1990 → ≈959) but does not flip, because iOS
   fails the same cell on the bold `<h2>` (queue 0(g)); and
   `display-contents-dynamic-pseudo-insertion-001` is already failing while
   **over-inking 25×**, so adding the `::after` could worsen it.
   (b) ~~both-native zero-width wrap: `css-lists/counter-004`~~ **MOVED to
   queue 4(a) as a named carrier, and the "zero-width wrap" reading is
   DELETED — no capture supports it** (S6-02, CONFIRMED; lanes B3 and B4
   measured the same thing from the Compose and SwiftUI sides). The document
   is **80 inline `<span>`s** and both natives give each one its OWN line
   box: 81 contiguous ink row-bands (1 for the `<p>` + 80) at 390×1704 (iOS)
   / 390×1784 (Android) against a 390×600 ref that has 5, and web and the
   ref agree on 5. The two five-glyph strings `ჵჰშჟთ` that COULD have
   wrapped each sit on ONE line. It is the inline-run wall (4(a)), not a
   counter, marker or font defect, and it is not fixable inside `lists/` or
   `counters/`. Both native cells are `scoreExcluded` (native-font-parity);
   only web f 0.7779 is scored; (c) ~~adjudicate the three self-declared
   re-label candidates~~ **DECIDED (A12#5, retro R13 — pending in the retro
   PR)**: `armenian/css3-counter-styles-008`, `counter-suffix` and
   `css-lists/counter-004` leave the refusal list — the PNGs show non-font
   defects (armenian-008: both natives print "10000. 10000" on ONE line
   where the ref wraps the §7.1.4 fallback row; counter-suffix: every
   item's text painted twice "foo / foo" + ~~Korean and RTL marker rows
   missing on ALL THREE~~ **NATIVE-ONLY — web renders both correctly, see
   (c³)**; counter-004: ~~the zero-width wrap above~~ **80 spans → 80 line
   boxes, see (b)**). The adjudication itself stands; two of its three
   characterisations were corrected by wave-50 skeptic S6 and the corrected
   text is (c¹)–(c⁶) below. Accept
   the 6 honest native fails (armenian 0.9420/0.9423, counter-suffix
   0.8883/0.8901, counter-004 as in b). The other 10 keys stay
   (cjk-decimal-001 and arabic-indic-102 spot-checked: same glyph rows,
   plausibly face-bound). **Queued from it, as wave 50 re-measured each:**
   (c¹) **natives — armenian fallback-row line breaking** (unchanged: both
   natives print "10000. 10000" on ONE line where the ref wraps the §7.1.4
   fallback row; armenian 0.9420/0.9423).
   (c²) ~~shared — counter-suffix doubled item text~~ **FIXED in tree (wave
   50, lane B4's autoclose patch, APPLIED).** `tools/titan/extract-fixture.mjs`'s
   `scanOwnText` never mirrored `walkChildren`'s `AUTO_CLOSE_TRIGGERS` rule
   (HTML Living Standard §13.2.6.4, optional end tags), so with no literal
   `</li>` the scan resumed INSIDE the child and the child's text was ALSO
   counted as the parent's own — every renderer painted `1. foo` then a bare
   `foo`. The doubling IS shared by all three platforms (20 ink row-bands
   against the ref's 12, so the duplicate is **8 bands, not the 12 the lane
   first estimated** — S7 measured `capBandCount` 20 / `refBandCount` 12 /
   `keptBandCount` 12 on every platform). Full-corpus differential: **10
   tests change, 51 components lose a duplicate `_text`, and the leaf-text
   sequence of all 10 fixtures is BYTE-IDENTICAL before and after** — no
   glyph is lost anywhere, only ancestor copies removed (skeptic S2 checked
   that no character present in any base fixture is absent from the tree
   fixture, over every changed document). Predicted: `counter-suffix` **web
   f 0.8867 → P ~0.98** (an unclaimed flip S7 found), natives → 0.90–0.93
   and still failing, where the reflow row is the OPTIMISTIC bound;
   `broken-symbols` **web P 0.9749 → 1.0000, iOS f 0.9486 → P 0.9713,
   Android f 0.9490 → P 0.9713** (+2 unclaimed, queue 11(c) closes);
   `background-color-animation-with-table1/3/4` **+7 — web + iOS on all
   three tests, plus ANDROID on table4** (Compose's table path suppresses
   the duplicate on table1 and table3 but NOT on table4, whose Android ink
   box is x[16–110] y[20–73] / 300 px against table1/3's 100 px; S7's column
   erasure scores P 0.9945 / 0.9943 / 0.9936 and leaves Android table1/3 at
   0.9998). **Count these cells ONCE**: lane B7's `table-duplicate-text.patch`
   is byte-for-byte subsumed by this one (S2: 0 of 2870 fixtures change when
   it is applied on top), so its "+6 measured-safe, +1–3 likely" is the SAME
   cell set. **DENOMINATOR WARNING**: retro R13 removed `counter-suffix`'s
   `native-font-parity` exclusion, so wave 50 scores two cells wave 49 did
   not — they land as NEW FAILS under `score-gate.mjs`'s NEWLY MEASURED,
   never as losses.
   (c³) **natives — the missing korean and RTL markers, which are NOT
   shared** (S6-07, CONFIRMED-WITH-CORRECTION; the old "all three platforms"
   wording was wrong). **Web paints `일, foo` / `이, bar` exactly as the ref
   and paints all four RTL markers**; both natives fall back to decimal for
   `korean-hangul-formal` and paint no marker on either `dir=rtl` list.
   Web's only residual on the RTL half is visual ORDER (the ref right-aligns
   `foo .1`; web keeps the marker left), not marker loss. **The iOS korean
   arm SHIPPED this wave** (`StyleEngine/lists/KoreanHangulFormal.swift` +
   `ListMarkerStyle.swift` + `ListMarkerText.swift`, `KoreanHangulFormalTests`,
   mutation-proved twice; exactly ONE corpus component declares a `korean-*`
   list-style-type, so exactly one test can move). **The Compose twin does
   not exist** — `grep -rn -i korean runtimes/compose/src/main/java/` returns
   nothing — and it is three edits, named by SYMBOL because the lane's first
   pointer named a file that does not exist (there is no `ListStyleType.kt`;
   skeptic S4 found it, fix lane F2 corrected the banner):
   `lists/ListStyleConfig.kt`'s `ListStyleType` enum (add
   `KOREAN_HANGUL_FORMAL` after `KATAKANA_IROHA`);
   `lists/ListStyleExtractor.kt`'s `typeFromKeyword` `when`, whose key must
   be the UNDERSCORED `"korean_hangul_formal"` because that function
   normalises `rawKeyword.lowercase().replace("-", "_")` before matching —
   the hyphenated wire spelling would never match; and
   `lists/ListStyleApplier.kt`'s `getMarker` `when`, one arm beside
   `KATAKANA_IROHA`. Until then the two natives answer differently for this
   keyword (iOS `일,`, Compose `1.`), which the Swift file's TWIN DIVERGENCE
   banner states rather than leaving silent.
   (c⁴) **RTL markers are BLOCKED UPSTREAM — do not fix them in the
   runtime.** Executed repro (Catalyst): for the verbatim
   `counter-suffix__0__4__0` `<li>`, `ComponentRenderer.isOutOfFlow(li)` is
   true, out-of-flow children go to `overlayChildren` / `positionedChildren`,
   and the marker branch lives only in the IN-FLOW children loop — so
   `meta.markerText` ("1.", present on the wire) is never painted. That half
   is a one-call-site renderer fix. **But painting it there would make the
   cell WORSE**: the same repro reads `Direction: LTR` off the RTL `<ol>`,
   because `tools/titan/bidi-bake.mjs` rewrites the bake root's `direction`
   to `ltr` on purpose ("the reorder is already in the coordinates") and the
   `::marker` is NOT part of the reordered text run — its SIDE is the one
   thing that still depends on `direction`. The ref hangs these markers on
   the RIGHT (ink x 133–145, item box ends at x 128); a renderer reading the
   wire would hang them on the left at x≈60, adding wrong ink while the
   right-hand ink stays missing. **Fix it in the bake**: it already emits
   each item's text as an explicitly positioned box and can measure the live
   geometry, so it should do the same for the item's marker, or preserve a
   marker-side signal. Census: exactly **4** components in the whole corpus
   carry `meta.markerText` together with `position: absolute|fixed`, all
   four in `counter-suffix`.
   (c⁵) **`name-case-sensitivity` is TWO DIFFERENT DEFECTS, and the old
   "missing marker content on all three" erases web's real one** (S6-06,
   PARTIALLY-CONFIRMED). wave49-final web f 0.9593 / iOS f 0.9535 / Android
   f 0.9542, cvF. **On the NATIVES it is FLOAT PLACEMENT**: all 14 `float:
   left` boxes collapse onto a single cluster at x16–35 (184 px iOS / 204 px
   Android) where the ref runs x17–344 over two line-boxes — every float is
   placed at its own container's origin instead of continuing the float line
   across sibling containers (the document is 14 floats inside 8 zero-height
   `<ol>`s). That is outside `lists/`. **On WEB the float line is correct**
   (three clusters at x[17–36] [49–123] [135–246] against the ref's [17–36]
   [49–132] [144–344]) **and the defect IS missing marker content**: 1008 px
   of ink against the ref's 2026, and no second row-band at all.
   (c⁶) **Compose `KoreanHangulFormal` negatives are unmodelled, and one pin
   asserts the wrong answer** (skeptic S4; a comment/pin honesty defect, not
   a render defect — unreachable from the marker path, where `index + 1 ≥ 1`,
   and zero corpus carriers). `expand(-1)` returns `"-1"` and `expand(-5)`
   returns `"-5"`, but `range: -9999 9999` INCLUDES those and the style
   declares `negative: "마이너스 "`, so the correct strings are 마이너스 일
   and 마이너스 오. The doc comment calls this "the DECIMAL spelling when the
   value falls outside the declared range" — it is not out of range — and
   `KoreanHangulFormalTests.testExpandCoversTheAdditiveTable` pins
   `expand(-1) == "-1"` as if correct. Model the negative, or say plainly
   that negatives are unmodelled and pin the refusal.
3. **Multicol float residuals** (W3 wave 48 closed seam 2 — the iOS
   slice-replay renders ref-exact rows):
   (a) **the converter/inline bake drops `<br clear="all">` — and it is an
   ALL-THREE defect worth 12 degenerate-pass cells, not 8 on the natives**
   (S6-04, CONFIRMED-WITH-CORRECTION; the previous entry understated the
   blast radius). Per-test IR for
   `floats-clear-multicol-000/001/balancing-000/001` carries no `Clear`
   wire, so ALL THREE platforms put the orange in column 0 where the ref
   puts it in column 2 — web y131-133 x50-119 (210 px) and y111-115 x50-119
   (350 px); both natives y131-133 x35-134 (300 px) and y111-115 x35-134
   (500 px); ref y161-163 x235-334 (300 px) and y171-175 x235-334 (500 px).
   **Web is additionally the wrong WIDTH** (210/350 against the ref's
   300/500). All 12 cells PASS on float-slice dominance (web 0.9890/0.9870,
   iOS 0.9871/0.9854, Android 0.9857/0.9839) — obligation #3's named
   exemplar, 12 cells. **The bake itself is written, differential-measured
   and PARKED**: `tools/titan/results/wave50-B6/br-clear-fold.patch`
   implements HTML Rendering §15.3's UA rule set for `<br clear>`
   (`[clear=left i]{clear:left}` / `[right i]` / `[all i],[both i]{clear:both}`,
   with `all` FOLDED to `both` because `clear: all` is not CSS and the
   converter degrades it to an untyped `GenericProperty` — verified through
   the converter CLI), in `buildNode`'s presentational-bake slot beside
   `uaLinkProps` / `uaHrProps`, with the same author-wins guard; differential
   4 fixtures of 2870, 0 `__ref` fixtures. **It must NOT be applied alone**:
   the fold puts `Clear: BOTH` on the `<br>` COMPONENT and drops its height
   20px → 0px, while Compose's `MulticolFloatStrip.factsFor` reads `Clear`
   off the strip CHILD's own property list — so a wire on the `<br>`
   grandchild (-000) or on the `<br>` trailing the floats inside
   `.container` (-001) is still ignored and the orange stays in the wrong
   column. Predicted result of applying it alone: the band moves from
   capture rows 131-133 to 111-113 and 111-115 to 91-95 — **ink moved, not
   ink fixed, with 12 passing cells at risk for no gain**. **The missing
   half is a CSS 2.1 §9.5.2 LINE-BOX clearance rung**: the `<br>`'s line is
   pushed to the relevant floats' bottom and the CONTAINING BLOCK'S HEIGHT
   grows to hold it (§10.6.3) — the box top does NOT move, which is why the
   existing `clear`-on-the-box offset jump cannot model it. It needs the
   child measured with a `minHeight` of (clearance line + the box's own
   trailing band − its offset), a `MulticolFloatStripMeasure` change that
   cannot be verified without a device. Worth having: 87 files elsewhere in
   `tools/wpt/css` carry a `<br … clear=…>`.
   (b) **Compose renders the 003/balancing-003 orange band w px too high,
   where w is the band's OWN width — and it is NOT strip-walk rounding**
   (S6-03, CONFIRMED; the previous "measured-height rounding in the Kotlin
   strip walk" is DELETED and would have led to the wrong fix). Measured on
   the wave49-final Android captures against the frozen refs: 003 Android
   y158-160 vs ref y161-163, w=3; balancing-003 Android y166-170 vs ref
   y171-175, w=5; both x235-334, both 300/500 px (wave49-final android P
   0.9881/0.9857; iOS is ref-exact). A strip error would be
   band-width-independent and would move columns 1–2, which are ref-exact,
   and the two siblings that put the border on the cleared box ITSELF
   (-002 / balancing-002, no nested child, no `height:0`) are Android
   ref-exact. **The mechanism is two Compose arms**: the shape is
   `.clear { clear:left; height:0 } > .bar { border-bottom: <w> solid orange }`,
   `SizingApplier`'s `LengthValue.Exact` arm hands the content FIXED 0
   constraints so `.bar` measures 0 tall instead of its CSS border-box w
   (css-overflow-3: `overflow: visible` content is not clamped by its box),
   and then `BorderSideApplier`'s `Side.BOTTOM -> Offset(0f, size.height -
   inset)` (inset = w/2) centres the stroke on −w/2, i.e. the half-open band
   [−w, 0). w = 3 and w = 5 reproduce the measured −3 and −5 exactly. iOS is
   exact because `.frame(height:)` positions and clips without shrinking the
   child. **The item belongs to `sizing/` + `borders/sides/`, not
   `columns/`.** Repair options and blast radius:
   `tools/titan/results/wave50-B6/queue-3b-band-shift.json` — the broad one
   (a declared height must not clamp an overflowing child;
   `wrapContentHeight(Alignment.Top, unbounded = true)` or equivalent) has a
   blast radius of EVERY explicit-height box in the Compose runtime and is
   device-gated; the narrow one (refuse to draw a side band outside the box)
   fixes the paint position but leaves the box 3–5 px short. **Do NOT shift
   `MulticolFloatStripPlan`'s offsets** — `MulticolFloatStripRefRowsTest`
   pins them at the ref's own rows, and skeptic S3 re-scanned both frozen
   refs at x=237 to confirm those pins.
4. **Inline-run wall, corrected map** (wave-48 W4 measured the briefed
   rings as no-ops: the br ring shipped in wave 47; the 75 tagless
   members are correctly-stacked BLOCK boxes. The UA-styled ring —
   i/em/cite/var/dfn + sup/sub with the Blink parent/3+1 / parent/5+1
   shift, twin `InlineSpanRing.shiftPx` helper, pinned both platforms —
   shipped instead). Remaining, in value order: (a) the
   **br-stacked-equivalent wall** — lifting it flips 29 hosts across 27
   tests, ~16 currently passing: needs a device A/B, never a blind lift.
   **Named carrier moved here from 2(b)** (S6-02): `css-lists/counter-004`
   — 80 inline `<span>` siblings each become their own block line box, 81
   contiguous ink bands at 390×1704 (iOS raw 0.7532) / 390×1784 (Android raw
   0.7531) against a 390×600 ref that has 5, web f 0.7779 scored separately;
   both native cells are `scoreExcluded` (native-font-parity). Lane B4's
   executed Catalyst repro names **three independent gaps** that all have to
   fall, none of them in `lists/`: (1) **no `meta.runs` on the wire** — the
   two `<div>`s have 40 children each and no `runs` key, so
   `InlineRunFlow.fold(runs: nil, …)` returns nil and `ComponentRenderer`
   stacks the 40 spans as blocks; that is by design upstream, since
   `scanOwnText`'s wave-34 emission condition needs own text AND a kept
   child and the only own text here is inter-sibling whitespace the
   `ws-after` channel owns; (2) **even WITH runs the fold refuses** — each
   member declares `CounterIncrement`, which is not in
   `InlineRunFlow.emptyMemberInertTypes`, so `classify` bails `member-prop`;
   (3) **even if admitted the glyphs vanish** — the control run folds to
   `text: " "` with `droppedEmptyMembers: 2`, because `classify` reads
   `member.text` only and these members' glyphs live in
   `pseudos.before._text`, while `PseudoTextFold.resolve` runs in a
   component's OWN `ComponentRenderer` init so a CHILD's pseudo text is
   invisible to the parent's fold. **Gap (3) is a genuine correctness bug
   with a reach far wider than this test**: any `::before`-only inline
   member is silently dropped from a folded paragraph today;
   (b) **`subelements-003` — "missing from EVERY render" is REFUTED; it is
   three separate pieces and only ONE platform is blank** (S6-05, CONFIRMED;
   wave49-final web **P 0.9987** · iOS f 0.9043 · Android f 0.9084). **Web
   reproduces the wrapper's blue underline byte-identically to the ref**
   (1404 blue-dominant px at rows 193-195 / 240-242, x48-324); **iOS paints
   978 px but at the wrong rows plus a spurious third band at 309-311**;
   **Android paints none at all.** The three pieces, none inside the fold:
   (1) **no block-level decoration PROPAGATION channel on Compose** —
   `text-decoration` is not inherited, it propagates to in-flow descendants
   (css-text-decor-3 §2.1), which iOS gets for free because
   `TypographyApplier` attaches `content.underline(…)` at the BOX level and
   SwiftUI's text-styling environment reaches descendant `Text`s, whereas
   Compose's `TextDecoration` is per-`Text`/per-`SpanStyle` and
   `TextDecorationLine` is (correctly) absent from
   `ComponentRenderer.INHERITED_PROPERTY_TYPES`, so the wrapper's `underline
   blue` reaches nothing. Owner: a Compose decoration lane plus a renderer
   channel; (2) **iOS's wrong rows and spurious third band**, which is a
   per-range placement defect and the tractable half; (3) **this test's own
   member cannot be closed on Compose at all today** — its `<sup>` declares
   `text-decoration-color: green` over black text and Compose's `SpanStyle`
   has NO decoration-colour field, so admitting it would paint a black
   underline and call it green. A per-range green underline needs a custom
   draw off `TextLayoutResult` in the leaf seam. **iOS CAN express it**
   (`Text(seg).underline(true, color:)` at the per-segment concatenation
   seam), so staff iOS first; (b′) **the
   underline-inset wall — the wave-48 prediction misses were untracked**
   (A10#9): `text-decoration-subelements-002` iOS f 0.7974 / android f
   0.9040 (Android improved 0.765→0.904 in wave 48 without flipping),
   `text-decoration-inset-005` iOS 0.8995 / android 0.9000,
   `text-decoration-inset-006` 0.8987 / 0.8993 (web P on all three; flat
   since wave48-final). corpus-v6-14: "the underline-inset wall dominates;
   the UA-styled ring is correct but insufficient". **Wave-50 lane B9 named
   the wall precisely and it is one line of code away from three of those
   cells**: the fold refuses on `member-prop:TextDecorationColor-divergence`,
   and the divergence is measured against a NULL base. Only 6 run members in
   the whole corpus carry `TextDecorationColor`, in 5 tests, and all six
   refuse today — including `text-decoration-inset-005/-006/-014`, whose
   members declare `text-decoration-color: black` over text that is black by
   UA default. They refuse ONLY because neither the member nor the fold host
   declares `color`, so `InlineSpanRing.admit`'s `effective` ink is null.
   Bottoming that base out at the capture mode's default ink (the RC-B1 "WPT
   black / dark-stage #eee" split the borders and effects lanes already use)
   would admit exactly those three — `-005` iOS f 0.8995 / Android f 0.9000,
   `-006` 0.8987 / 0.8993, `-014` iOS f 0.9238 / Android f 0.9230, all
   FAILING on both natives, so the standing "fold decisions change only on
   currently-failing hosts" rule allows it. It needs the capture-mode flag
   threaded into the ring — a signature change through the renderer seam.
   `text-decoration-inset-011`'s two members stay refused by a second rule
   (`TextUnderlineOffset`);
   (c) **`block-ellipsis-032` — the hanging-whitespace ring LANDED (wave 50,
   lane B9, in tree), pending the gate.** `InlineSpanRing.admit(…,
   glyphless)` now admits `white-space` (preserving keywords only) and
   `background-color` on a member whose text is white space and NOTHING
   else; `InlineRunFold` computes `glyphless`, `InlineSpanContent` paints the
   band as `SpanStyle.background`. Corpus census over all 298 `meta.runs`
   hosts: **6** whitespace-only glyph members exist, and exactly **3 fold
   decisions change — all in `block-ellipsis-032`**, where Android is the
   failing column (wave49-final android f 0.9451; iOS P 0.9577, web P
   0.9834), so the fold-decision rule holds. The `contain-content-004` pair
   still refuses (tag outside the ring, `Padding*` outside it too) and
   `first-letter-001` still refuses (13 types still outside). Prediction:
   Android folds to one `Text`, `maxLines = 1`, ellipsised — the shape our
   web runtime already renders at 0.9834 — so **f 0.9451 → P, thin at 0.0334
   of headroom against a web→Android raster gap whose p75 is 0.0222 and p90
   0.0520, with today's gap on this very test 0.0383**. Honest caveat: the
   ref REMOVES the hanging white space before placing the ellipsis
   (css-overflow-4 §4) and neither our web runtime nor this ring does, so
   Android should land near web rather than on the ref; the exact ellipsis
   column depends on `StaticLayout`'s end-ellipsis arithmetic, which no JVM
   pin can reach. **iOS was deliberately NOT changed** — its column PASSES
   (0.9577) on the same visibly-wrong render, and the standing rule forbids
   moving a fold decision on a passing host; the Swift twin
   (`InlineSpanRing.swift` + `InlineRunFlow.swift` plus
   `AttributedString.backgroundColor` at the segment seam) is queued, not
   shipped, and skeptic S4 confirmed nothing slipped into SwiftUI this wave;
   (d) **Compose line-clamp-006/007 — the cross-block line-box census
   LANDED and its renderer seam is APPLIED (wave 50, lane B9); CLOSABLE at
   the gate.** `typography/inline/LineBoxCensus.kt` + `LineBoxCensusRuns.kt`
   + `LineBoxChildMetrics.kt` + `LineClampCapResolve.kt`, threaded through
   `StyleApplier.applyProperties`'s new optional `lineClampCapPx` and
   resolved in `ComponentRenderer` beside the existing `collapsedMargin` /
   `wptCaptureModeForSizing` threading. `simulate-lineclamp-census.mjs`
   replays BOTH cap models over all 1435 per-test IR documents (skeptic S3
   reproduced it): 34 carry a fixed-count `line-clamp`, 38 clamp roots,
   **exactly 3 roots change cap** — `line-clamp-005` 96→112 px (android P
   0.9615, stays P, box stops being 16 px short), `line-clamp-006` 160→192
   px (**android f 0.9446 → P**), `line-clamp-007` 96→192 px (**android f
   0.9409 → P**); the other 35 return the byte-identical wave-41 number. S7
   scored the iOS picture as the Android cell for both flips at **P 0.9822**
   — plausible with MODERATE risk, 0.0322 of headroom against an
   iOS→Android gap whose p90 is 0.0312. The seam's guard was a silent
   `runCatching{}.getOrNull()` when the lane shipped it; **fix lane F1
   (skeptic S3) made it breadcrumb** — `LineClamp[census-threw]` on
   `PropertyTracker` plus a `LineClampCap` logcat warning naming the
   component and the throwable, pinned by a `LineBoxCensusTest` case that
   executes a wire which really throws. Retro R3's sibling fix (Compose
   ignored the `ellipsis` marker component; `LineClampWire`,
   `LineClampMarkerSuppressionTest`) stands, zero pixel movers.
   (d′) **The `LineBoxCensus` twin banner understates the divergence — TWO,
   not one, and the second is unmeasured** (S6-15, REFUTED as written; the
   banner in tree now says TWO). (1) the stated `Unprovable` → uniform-cap
   fallback where Swift's `LineClampCensus` returns `.unbounded` (Compose
   maps every non-`Capped` verdict back to the uniform cap because on
   Compose the cap node also carries the block-axis ink clip that makes the
   discarded lines unpaintable, and 24 of the 38 roots land on an unprovable
   run while passing today); and (2) **Compose's `<br>` / `role ==
   "line-break"` arm in `LineBoxCensusRuns.childRun`, which returns a
   zero-line zero-height run, has NO counterpart in the iOS
   `LineClampCensusRuns.childRun`** — which has no tag or role check at all,
   so a `<br>` with the converter's stamped height is budgeted there as a
   monolithic box. Skeptic S3 mutated the arm out and **0 corpus caps
   moved**, so it is latent on this corpus: either port it to Swift or state
   the divergence in BOTH headers — and measure it, because no cell has been
   checked either way; (e) Compose
   baseline-shift is face-ascent-dependent (hard-coded Inter 0.96875) while
   iOS is exact points — watch sup/sub cells on device for the ~2px class
   (named limitation in `InlineSpanContent.kt`).
5. **Web tail residuals** (W5 wave 48 measured +14 web: contain-body ×8,
   lch/oklch %, degenerate calc, image(), contain-intrinsic bridge):
   (a) ~~`tools/titan/extract-fixture.mjs` stamps 100×100 on `<hr>`~~
   **FIXED in tree (wave 50, lane B7's `ua-hr-separator-box.patch`,
   APPLIED), pending the gate — but the flip claim is +1 CERTAIN, +2
   CONDITIONAL, not the +3 the lane first wrote.** A rule-less `<hr>` matched
   no author rule and no own text, fell through `buildNode`'s "empty node"
   branch and shipped `{width:100px, height:100px}` where the browser paints
   a 2 px rule; `css-images/gradient/gradient-hue-direction` carries three of
   them (capture 390×**894** against the 390×600 ref, i.e. +294 = 3 × (100 −
   2) exactly; web f 0.6241 · iOS f 0.6235 · Android f 0.6230). The fix is a
   UA-origin bake (`UA_HR_PROPS`) in the same slot as the wave-30 UA link
   bake: `display:block; height:0; box-sizing:content-box; border:1px inset
   #eeeeee; margin-block:8px; overflow:hidden`. **The colours are MEASURED
   off the frozen ref, not guessed** — the rule at y=188/189, x16–373 is
   rgb(154,154,154) over rgb(238,238,238) with rgb(196,196,196) mitres,
   exactly `border: 1px inset #EEEEEE` under Blink's two-tone model, and
   skeptic S1 reproduced the `Dark(0xEE)` arithmetic independently to the
   pixel. `box-sizing: content-box` is stated explicitly because Compose only
   inflates a declared size by the padding+border band for an EXPLICIT
   content-box; without it the 0 px would be the frame height on Android and
   the rule would paint nothing. The bake is ATOMIC — any author declaration
   naming any key declines the whole box and stamps
   `ua-hr-separator-declined`, which is what protects
   `css-pseudo/active-selection-057` (the only one of the four `<hr>`
   carriers that passes today: web P 1.0000 · iOS P 0.9994 · Android P
   0.9986; its properties bag comes out byte-identical). Differential 4 tests
   of 1435, converted output validated against `schema/ir-v2.schema.json`,
   zero generic properties. **The re-scoped prediction** (fix lane F3 on
   skeptic S7's replay): removing the three 98 px displacements gives web
   **P 0.9521** but iOS **f 0.9491** and Android **f 0.9495** — the
   shift-only web copy still differs on 2148 px at maxDelta 101, exactly the
   3 × 2 × 358 px of missing separator rule. With the rule painted all three
   pass (1.0000 / 0.9970 / 0.9973), and S7's sensitivity band says ANY
   painted rule suffices (full-width 0.9952/0.9956, flat grey 0.9749/0.9753,
   one row only 0.9960/0.9963, pure black 0.9506/0.9510). **So the two native
   cells hinge ENTIRELY on the baked box actually painting on Compose and
   SwiftUI** — pinned by `UaHrSeparatorBoxTest.kt` and
   `UaHrSeparatorRuleRasterTests.swift`, but the device is the arbiter: at
   the gate, read the capture HEIGHT first (600 with a visible rule = flip;
   600 with no rule = iOS 0.9491 / Android 0.9495 and the wave gains one cell,
   not three). `direction-upright-001/-002` gain a ~2 px rule each; no flip
   claimed. The lane's earlier "zero pixels over 8, maxDelta ≤ 1" proof was
   true only over the gradient BANDS — a reminder that a band statement is
   not a frame statement; (b) `none` colour
   components need an IR wire shape (gradient-none-interpolation,
   all-three 0.8199/0.8125/0.8125 cF); (c) gradient-eval-predefined-
   color-spaces: script-templated test extracts zero styled components
   (0.9247 cF pF cvF ×3); (d) fallbacks-005 is UNPASSABLE AS SCORED (five
   tests share one solid-green ref whose prose is 001's — WPT authoring
   artifact; exclusion-lane decision, never a code carve-out; web honest
   loss 0.9144→0.9038 recorded); (e) native powerless-hue/lch gradients
   (0.65–0.86) need polar interpolation with alpha-carrying stops; (f)
   color-mix in lch needs a static mixer (srgb→lch in ColorConversion);
   (g) **contain-content-004 / contain-html-overflow-002 — both mechanisms
   FOUND and both fixes APPLIED in tree (wave 50, lane B7), pending the
   gate** (previously: "all three paint blue-FILLED blocks where the ref
   wants blue-BORDERED hollow cells", 0.8448/0.7940/0.8286 and
   0.8709/0.8705/0.8698, cF cvF). **`contain-html-overflow-002` was a
   root-scope conflict**: `propsForBodyRoot` folded `html`-scope and
   `body`-scope declarations onto ONE synthetic component in source order,
   so `html { height: 400px }` beat `body { height: 200px }`, the root came
   out 200×400 carrying body's `overflow: hidden`, the 200×200 `<p>` and the
   200×200 red `<div>` both FIT, nothing was clipped, and the capture
   painted the red square the ref forbids. Its six siblings
   `contain-{body,html}-overflow-001/003/004` are byte-identical documents
   whose `<html>` rule declares only `contain:` — no conflict — and all six
   PASS at 1.0000 / 0.9996 / 0.9989. The fix records which scope last wrote
   each key and re-decides ONLY the keys both scopes declare, per family: the
   canvas background is the ROOT element's (css-backgrounds-3 §2.11.2 — body's
   background propagates only when the root's is transparent), everything
   else on this component describes the BODY box it renders, so body scope
   wins; overwriting leaves the key's position in the bag untouched, so a
   document with no conflict emits byte-identical IR (skeptic S1 proved that
   over all 1435 tests: exactly 2 documents change, 1433 identical).
   **Predicted +3: `contain-html-overflow-002` f → P on all three at its
   siblings' scores** — the patched root IR differs from the PASSING -001
   root in exactly ONE token (`Contain=["PAINT"]` vs `["LAYOUT"]`), the two
   child components are byte-identical, and `contain: paint` adds clipping
   and never removes any (S7 replayed 1.0000 / 0.9996 / 0.9989). The second
   changed document, `CSS2/css21-errata/s-11-1-1b-005`, is a REPAIR not a
   claimed pass: **234 000 of 234 000 pixels are black on ALL THREE
   platforms** (body's `background: black` flooding the canvas) against a
   white ref with one line of text, f 0.0001 ×3; restoring the root's white
   should give a white canvas plus the `<p>`, but the document also puts
   `display: table-cell` on the root and an abspos `<p>` over it, so no flip
   is claimed. **The `*`-selector residual this item used to carry is
   WITHDRAWN as FALSE** (skeptic S2 defect 3, corrected in tree by fix lane
   F3): `parseCompound` consumes `*` as "universal — no constraint" and never
   writes `needTag`, so `rootScopeOf`'s `parsed.needTag === '*'` arm was
   unreachable — instrumented over the synthetic cases and all 1435 corpus
   tests it fired **0** times while the no-tag fallthrough took 41. Every
   `*` root-scope rule buckets as **html** scope, like `:root`, and IS
   re-decided. The dead arm and its `star` bucket are deleted rather than
   left as a lie about what the pass sees. **The honest residual that
   replaces it**: the re-decision is by FAMILY, not by Selectors-4 §17
   specificity — `*` has specificity 0 and would lose to both `html` and
   `body` in a real cascade, which this pass does not model. Corpus
   exposure nil: of the 1435 tests, 17 carry a bare `*` root-scope rule (24
   rules) and ZERO declare a property an `html`/`body` root-scope rule in the
   same document also declares; the census is complete because ZERO corpus
   tests link an external stylesheet. **`contain-content-004`'s own mechanism
   is the duplicate table text** — each `<tr>` got `[td, dup, td, dup]`, so
   `table-layout: fixed` split the declared 206 px over FOUR columns
   ((206 − 5×2 px border-spacing)/4 = 49 px, which is exactly what the web
   capture's white cells measure) and the `<table>`'s own duplicate added an
   anonymous ROW, the 66 px blue band above the real ones and the reason the
   green "PASS" sits at y 226 against the ref's y 192: **29 500 blue pixels
   on web, 34 836 Android, 34 636 iOS, against the ref's 2436**. That is
   fixed upstream by lane B4's autoclose (2(c²)); the web cell should gain
   (the table becomes two real 100 px columns and "PASS" lands at the ref's
   y) while **both natives stay blocked by the abspos containing block**
   (0(b)), so +1 likely, +3 at best. A stray-text guard with the
   complementary predicate ("the parent's text IS its children's text", which
   B4's end-tag pairing does not express) is kept as defence in depth at
   `tools/titan/results/wave50-B7/table-duplicate-text.patch` — **it adds
   ZERO cells of its own** and must never be counted alongside B4's; (h) ~~native image() carries only the FIRST src~~ **DONE (wave 49,
   `ImageCandidateChain` on both natives; fallbacks-and-annotations
   002/003/004 P on all three — web 1.0000 / iOS 0.9990 / Android 0.9982).
   Re-verified and PNG-checked at wave 50 (S6-01, CONFIRMED)**: every capture
   paints 40 000 px of green at the ref's position with ZERO rgb(255,0,0) —
   honest passes, not red-square degenerates. **On 004 ALL THREE platforms —
   web included — paint the winning gif's (0,127,0) against the ref's
   (0,128,0)**, so that one-step-off GCT is not a native-only residual, as
   this entry and lane B7's note both used to say. No work was done in wave
   50 and none is needed; 005 remains 5(d); (i) **mask-image wire gaps**
   (wave-48 F1/S6 probes): `MaskImageValue.ColorStop` drops
   `positionLength` (every px-positioned mask gradient stop), and
   ImageNotation is absent from `mapToMask` so `mask-image: image(...)`
   drops the whole property.
6. **Open runtime bugs from the harness overhaul:** (a) ~~Compose
   transform-list~~ FIXED wave 48 (`TransformListComposer`, ordered §11
   product; exit-5 deletions done). Residuals after retro R1: pure-2D
   skew-bearing lists still accumulate per-kind on the legacy canvas route
   (skew meeting a 3D rotation or matrix3d now takes the exact 4x4 route,
   `takesMatrixPath.skew3D`); `decomposeMatrix2D` mishandles reflections
   on the matrix() path (pinned; no corpus carrier); the extractor's
   largest-axis rotate3d heuristic is superseded on the matrix path by
   `Rotate3dAxis` but remains on the graphicsLayer route. (b) ~~Android
   clips a transformed child's paint to its layout slot~~ **WRONG
   MECHANISM — it was never a clip (A11#0, FIXED retro R1, pending the
   gate)**: `StyleApplier.applyConfig` chains the transform (step 2) OUTER
   to the abspos offset (step 4), so every pivot was the child's local
   centre in the PARENT frame — (20,20) instead of (80,40) for the 40×40
   child at left 60 / top 20 — and the capture canvas edge caught the
   mis-pivoted diamond. Same mechanism on WPT: `css-transform-3d-rotateY-
   positive` Android x 86..205 vs ref 96..215 = left·(1 − cos 60°) = 10px.
   Fix: `TransformPivot` conjugates every route's pivot by
   `PositionApplier.resolvedOffset`; the three `nested-transforms.json`
   waivers now carry the measured mechanism and are the red test the gate
   deletes (exit 5). Known limit inherited from resolvedOffset: a PERCENT
   inset resolves only in the composed lane. The wave-49 obligation #3
   sibling (ancestor clip-path on abspos/blended children) is FIXED wave
   49 — clip-path-blending-offset Android 0.9566→1.0000. **Wave 50
   re-checked and confirms there is NO CODE LEFT TO WRITE here** (lane B8):
   both halves landed in the retro PR `747b28e4`, which is this wave's base
   commit — `transforms/TransformPivot.kt` conjugates every route's pivot by
   `PositionApplier.resolvedOffset` and the three `nested-transforms.json`
   waivers were rewritten to carry the measured mechanism so the gate deletes
   them as stale (exit 5), with `TransformPivotTest.kt` green in the retro's
   own sweep. 6(b) is pending the DEVICE GATE, not pending code. (c) ~~Android box-shadow ~2.4× over-blur (`MultipleShadowApplier.kt`)~~
   **the DIAGNOSIS is STRUCK; 6(c) is reduced to the REACH question, which
   stays OPEN** (wave 50 lane B8, independently re-fitted by skeptic S6 —
   S6-08 CONFIRMED on both halves; the matching `docs/STATUS.md` row is
   struck with the same evidence). The file attribution was wrong:
   `MultipleShadowApplier.kt` had **ZERO call sites since the initial
   import** (954b38e8), is in the retro's own dead-code census, and is
   DELETED in wave 50 — the live path
   (`ShadowApplier.applyFullShadow` → `OutsetShadowPainter` /
   `InsetShadowPainter`) has converted through `ShadowApplier.blurMaskRadius`
   since wave 2, including at `07f75ec9`, the commit whose probe produced the
   2.4× number. The magnitude does not reproduce either: the fitted Gaussian
   σ on the committed baselines is **web 7.32 / Android 7.50 / iOS 7.30
   against a 7.5 target on `024_Shadow_Colored`, with identical reach**, and
   web 6.18 / Android 5.44 / iOS 5.26 against 5.0 on `023_Shadow_Simple`
   (`tools/titan/results/wave50-B8/shadow-sigma-baselines.json`, re-runnable
   as `measure-shadow-sigma.py`; the live map is now pinned by
   `ShadowBlurSigmaParityTest`). **What is STILL OPEN and must not be closed
   as if it were the σ claim**: the 2026-08-28 probe measured an Android
   bottom-most shadow row of 123/131/131/120 against web's 93/101/109/90
   (box rows 30–70, blur 10, offset-y 10, spread −8/0/+8/−1 px). **That probe
   fixture was never committed, so the reach can be neither re-run nor
   refuted from this tree** — re-author it as a COMMITTED fixture and
   re-probe at a device gate before re-opening or closing the item. Two
   mechanisms that do widen an Android shadow's painted extent were repaired
   after that probe and are corpus-UNMEASURED: retro R6's border-box knockout
   + opacity attenuation (6(c′)) and retro round-2 F1's margin-box stripping
   (`ShadowGeometry.borderBoxRect` / `outsetShadowRect`). (c′) **FIXED
   retro R6, pending the device gate** — the two css-backgrounds-3 §6.1 /
   css-color-4 §3.3 defects PR #95 deferred and never ledgered (A11#2 /
   A10#5): the outset shadow painted UNDER the border box and ESCAPED the
   element's opacity group (Android `*_Decorated` fill (150,110,132) / ring
   (52,152,219) vs iOS+web (139,54,54) / (40,95,141)). The outset path moved
   out of `ShadowApplier` into its own file,
   **`effects/shadow/OutsetShadowPainter.paint`** (verified in this tree
   2026-09-05): every layer now draws under `clipPath(borderBox,
   ClipOp.Difference)` — the css-backgrounds-3 **§6.1.1** knockout of the
   radius-aware border box — and its paint alpha is multiplied by the
   element's `opacity` (css-color-4 §3.3 applies opacity to the element as
   a whole, shadow included), threaded from `EffectsFacade.apply`. Pinned by
   `fixtures/properties/effects/box-shadow-opacity-knockout.json` (shown
   arithmetic) + `ShadowOpacityKnockoutTest`. Expect
   `filter-effects/backdrop-filter-box-shadow` Android (0.9823, PASS) to
   move toward iOS (0.9855). (d) ~~iOS overflow clip applied OUTSIDE the transform~~ **FIXED in tree
   (wave 50, lane B8), pending the gate.** SwiftUI's modifier chain wraps
   later modifiers around earlier ones, so `.engineVisibility` — the
   overflow-clip carrier, whose `VisibilityApplier` turns a clipping used
   value into `.clipped()` / `.clipShape(AxisClipRect)` — was applied AFTER
   `.engineTransforms`, and the clip rectangle was evaluated around the
   already-rotated content in the layout frame the transform never moved.
   CSS orders them the other way (css-overflow-3 §3 clips content to the
   element's own padding box; css-transforms-1 §6 then maps the element's
   whole rendering, clipped content included, into the parent's space). The
   shipped hunk moves `.engineVisibility(style.visibility)` to sit between
   `.engineClipPath` and `.engineTransforms` in `StyleBuilder.applyStyle`,
   and the Phase-8 comment now reads `1. mask · 2. filter · 3. clip-path ·
   4. visibility/overflow · 5. transforms`. Measured defect
   (`css-backgrounds/background-attachment-fixed-inside-transform-1`, whose
   `#outer` is 300×700 with `transform: rotate(45deg); overflow: hidden`):
   the iOS ink strip starts at cols **216–389** against web's 13–389,
   Android's 33–389 and the ref's 16–373 — exactly the element's
   un-transformed left frame edge (margin 200) instead of the rotated
   rectangle's leftmost tip — while its manifest row reads `ios-ref ssim
   0.9843 wptPass true`, a visibly wrong PASS of the A1#0 class. Blast
   radius, re-derived independently by skeptic S4 over all 1435 per-test IR
   documents with the css-overflow-3 §3.1 used-value coercion applied: **25
   tests / 33 components declare a transform AND an overflow; only 5 tests /
   7 components carry a CLIPPING used value**, and S4 RASTERISED every root
   of the four non-target tests under both chain orders and got
   **byte-identical SHA-256** on all of them (`rotateY(180deg)` is an
   involution about the frame's own centre line and `translateX(0px)` is the
   identity, so each maps the clip rect onto itself) — only the 45° target
   moves. Also moved, and worth naming because the comment list does not:
   the WHOLE `.engineVisibility` modifier changed side, so `visibility:
   hidden`'s `.opacity(0)` and `collapse`'s `.frame(0,0).hidden()` did too,
   and `.engineMotionOffset` went from inside the clip to outside it — S4
   censused both co-declarations and found **0** components declaring
   Visibility with a transform and **0** declaring an offset-path property
   with an overflow, so neither can move a cell today. Pinned by
   `OverflowClipTransformOrderTests.swift` (two Catalyst raster tests on
   converter-verbatim IR for the gate-net fixture
   `fixtures/combinations/radius-overflow-transform.json`: the self-rotate
   diamond AND the other half of the rule, that a DESCENDANT's transform
   still happens before the ancestor's clip), mutation-proved by the lane and
   again by S4. **The gate-only oracle it turns green**: that fixture's
   `ROT_SelfRotate_ClippedChild` `_expect.box` is the 99×99 diamond and its
   `_expect.waive.iOS` said in its own text "this waiver is the red test that
   the fix turns stale" — **fix lane F2 DELETED the waiver and rewrote the
   component note to past tense**, so if the gate still reports it stale the
   fixture edit did not land. **Pre-existing residual, named so it is not
   read as a regression**: `.engineFilter` and `.engineClipPath` are still
   INNER of the clip, so a `blur()` / `drop-shadow()` halo on an
   overflow-clipping element is cut at the padding edge although
   filter-effects-1 §8 lets it spill — zero wave49-final components declare
   both, so it is unmeasurable today. (e) **Out-of-flow rule-table parity: iOS
   `FixedHoist.split` deliberately diverges from Compose
   `CanvasRootHoist.shouldHoistToCanvasRoot`** on the nested
   inset-anchored ABSOLUTE clause (wave 34 "parity item", wave 35 B1;
   `FixedHoist.swift:137-160`, pinned EXPECTED-DIVERGENT in
   `FixedHoistTests`). **Its stated blocker is FALSE on the current wire**
   (A10#4, A4#0): the comment says the extractor drops `body > div
   {position: relative}` for css-writing-modes available-size-00x, but the
   wave49-final per-test IR for available-size-001/012 carries `Position`
   ×2 with `relative` (executed grep), and Compose's spec-correct clause
   PASSES both (wave49-final web P 0.9881 · iOS P 0.9771 · android P
   0.9791, flat since wave47-final — not the 0.8998 FAIL the comment
   cites). Chromium anchors the shape at the ICB (css-position-3 §3.1).
   Action: adopt the ICB clause in `FixedHoist.split` behind an A/B on the
   six frozen scan-oof2 carriers (both cells pass today, so this is a
   parity/correctness item, not a flip), correct the stale comment, flip
   the FixedHoistTests parity row from EXPECTED-DIVERGENT to parity — an
   EXPECTED_DIVERGENCE pin must carry a live reason or go.
   (x) **The two iOS exit-4s the wave-50 fixture net surfaced — resolved
   2026-09-15, one by a fix, one by an honest ledger.** (x¹)
   `fixtures/combinations/blend-isolation.json` 003_wrapper iOS-Android /
   iOS-web **SSIM 0.9492 · Δpx 0.60 % · ΔE95 0**: MECHANISM FOUND AND FIXED
   by the post-gate iOS lane — SwiftUI's `.blendMode(_:)` is an attribute
   that propagates to every leaf drawing operation, so a `mix-blend-mode`
   box blended its own label glyphs against its OWN background rect;
   compositing-1 §5.1 makes the element a stacking context whose own paint
   composites into one group first (§3.1) and only the group blends.
   `StyleEngine/effects/blend/BlendModeApplier.swift` now chains
   `.compositingGroup()` before `.blendMode`; pinned by
   `BlendGroupCompositingTests.swift` on a Catalyst raster proven
   byte-identical to the simulator captures it predicts (5/5 opacity-blend
   crops + both committed visual-test blend baselines at 0 px). Measured
   glyph colours: 003_wrapper iOS (97,193,242) vs web/Android (197,227,242),
   75 px; visual-test 087_BlendMode_Multiply iOS (23,8,10) vs web (24,19,33),
   59 px (the 087/088 iOS baselines refreshed this wave were captured
   WITH the fix in the tree and sit at 0 px vs web — the fix is on
   device). (x²)
   `fixtures/properties/effects/filter-sepia-amounts.json`
   006_Sepia_Translucent iOS-Android / iOS-web **0.9931 / 0.9930 · Δpx
   17.09 % · ΔE95 11.5**: NOT a render change — the live iOS raster is
   (95,117,129), the exact src-over composite the 2026-08-28 ledger text
   recorded, against web/Android's spec (90,92,95). The retro (R13) deleted
   the two ledger lines after re-scoring the COMMITTED iOS PNG, which reads
   (89,92,95) and never matched the live render (provenance: #126,
   2026-08-29 — a baseline seeded from a state that did not exist on
   device). Wave 50 RESTORED both lines with the live numbers (owner
   `ios-effects-filter-composite`, expires 2026-11-30) and re-baselined
   iOS__006 to the render the code declares (`FilterApplier.swift`'s
   sepia case: "plain source-over … the translucent case is a KNOWN iOS
   divergence"). THE FIX is still open and its constraint is measured: the
   complementary-opacities `.plusLighter` route fixes translucency and
   un-filters SwiftUI Text (filter-on-text.json pins that), so it needs a
   compositing route that preserves alpha WITHOUT flattening text — e.g. a
   `Canvas`/`GraphicsContext.filter(.colorMatrix)` pass over the element's
   own resolved layer. Lesson recorded in the standing constraints: a
   ledger line is re-measured against a LIVE capture, never against two
   committed PNGs.
7. **css-gaps residuals** (W7 wave 48 built column-wrap on both natives +
   iOS percent flex-basis; gate arbitrates 025/043/045/046 — wave49-final:
   025 P ×3, 043 P ×3, 045 web P 1.0 / natives P 0.9549, 046 P
   1.0/0.9951/0.9908): (a) ~~the gap-decoration line-band painter models
   lines as the UNION of item rects~~ **DONE (wave 50, lane B10, in tree on
   BOTH natives), pending the gate.** The union is the line box only while
   the items fill their line; `align-content: stretch` — the flex initial
   `normal` — grows every line into a definite container cross size
   (css-flexbox-1 §9.4 step 8) and the union then under-reports the line box
   at both ends. Measured, ref vs the wave49-final native captures
   (inclusive runs): 045's red column rule is at ref/web x76-80 and at both
   natives x72-76 — **4 px left**; 045's gold runs are 7–8 px short; 046's
   gold bands are at ref/web y73-77 and y134-138, iOS y70-73 and y131-135,
   **Android y70-73 and y132-135** — say "both natives" only on the first
   band, because their second does not agree (S6-16). The item rectangles
   are NOT in dispute: ref, iOS and Android place them identically to the
   pixel on both fixtures. The fix is a new pure module on each native
   (`columns/GapDecorationBands.kt` / `.swift`) rebuilding the §9.4-step-8
   line boxes from the SAME arithmetic the wrapping layout runs
   (`FlexWrapLines.stretchLines` / `FlexWrapPlan.stretchLines`), with
   `GapDecorationSegments.build` painting against those. **No renderer seam
   was needed** — re-running the shared pure function inside the painter
   reaches the same numbers, so nothing here is deferred behind a patch. What
   is taken on trust, stated plainly: the used cross gap, which cannot be
   cross-checked against the item frames (the identity `container = Σ
   lineSize + (n−1)·gap` holds for every gap once sizes are derived from it),
   so it is read from the same IR the layout reads and is `null` — refusing
   the whole rebuild — whenever the wire left it unresolved; everything else
   IS checked, and bands that do not contain the items they claim to describe
   fall back to the union with a `PropertyTracker` breadcrumb. **Blast
   radius: of the 48 scored css-gaps tests, exactly 2 change** — 045 and 046
   — every other inert BY CONSTRUCTION (nowrap, a positioning
   `align-content`, an auto cross size, or zero §9.4 leftover). Skeptic S4
   confirmed it EMPIRICALLY rather than by inspection, rendering all 48
   per-test IR documents through the real SwiftUI painter and SHA-256ing each
   raster with and without the rebuild: only 045 and 046 differ.
   **Predictions**: 045 natives P 0.9549 → ≥ 0.99 (margin gain, no flip);
   046 natives P 0.9951 / 0.9908 → ≥ 0.995; web unchanged and structurally
   immune (the web engine has no segment model — it emits the CSS longhands
   and the browser paints the rules). **Any movement on a css-gaps cell other
   than 045/046 is a REGRESSION**, not a side effect.
   (a′) **The two new band modules are NOT numerically parallel, and neither
   header says so** (S6-N3 + skeptic S4): Kotlin rounds every line extent,
   the gap and the container cross to `Int` before `FlexWrapLines.stretchLines`
   and distributes an integer share plus `+1` to the first `remainder` lines,
   while Swift stays in `CGFloat` through `FlexWrapPlan.stretchLines` and
   gives `leftover / n` to every line; the band cursor also starts at `0`
   (content-relative) on Swift and at `cross.start` on Kotlin. For 046 that
   is Swift `[0,56.67] [61.67,118.33] [123.33,180]` against Kotlin `[0,57]
   [62,119] [124,180]` — **up to 1 px of band edge on non-integral
   geometry**, which is exactly the Android-only second-band offset above.
   Each side matches its OWN layout, which is the property that matters, but
   the divergence is UNMEASURED on device: a gate lane should diff the two
   natives' 045/046 bands after the wave-50 gate; (b) `background-clip-content-box-002` iOS 0.9992 cF F: a
   decoded `.percent` flex-basis no nowrap path reads — likely free fix
   when CSSFlexLayout honours percent; (c) iOS column-wrap stretch
   injection (no corpus carrier yet); (d) wrap-reverse ordering both
   natives (pre-existing TODO, now load-bearing for the routing gates);
   (e) **css-gaps distribution defects — SPLIT into three different
   problems; only one of them is a runtime defect** (wave 50, lane B10;
   S6-09 CONFIRMED the instrument half from the frozen bytes).
   **(e¹) 033 / 034 / 035 are ERASED-REFERENCE victims — do NOT spend a
   runtime lane on them until the ref is re-frozen.** `capture-browser-ref.mjs`
   injects `:where(html, body) { …; background: #FFFFFF }`; canvas
   propagation already paints the page white from the ROOT's background, so
   the BODY half additionally paints an opaque box in the body's own layer,
   and CSS 2.1 Appendix E orders (1) root background, (2) NEGATIVE-z-index
   descendants, (3) in-flow block backgrounds — a reference that draws its
   decoration boxes as `position:absolute; z-index:-1` body children, which
   is exactly how the css-gaps `-ref.html` files after 032 are authored, is
   painted at step 2 and buried at step 3. Executed A/B (puppeteer, the
   repo's own launch args): chromatic pixels live vs the same sheet with the
   body half dropped — 033-ref **0 vs 5200**, 034-ref 0 vs 2500, 035-ref 0 vs
   4050, 036-ref 0 vs 2050, 037-ref 0 vs 2050,
   `hanging-punctuation-block-bound-001-ref` **0 vs 58 184**. Consequences:
   **033's native `P 1.0000` is blank-vs-blank** (S6 decoded the frozen ref
   to exactly two colours — white 211 500 + item fill (223,232,238) 22 500,
   zero rule ink — and both native captures to the SAME two colours at the
   SAME counts; web is the only platform that paints rules, 3000 blue + 2200
   red, and the only one scored f 0.9400); and **034/035 are not render
   defects at all** — all three platforms agree with each other at ≥ 0.995
   and fail the blank ref together (034 f 0.9458/0.9466/0.9466, 035 f
   0.9206/0.9220/0.9220). The instrument fix is a verified patch, deferred as
   a CALIBRATION — see "Instrument decisions pending".
   **(e²) 006 is a writing-mode flex-axis defect, PRICED and declined.**
   `flex-gap-decorations-006` (natives f 0.8266 / f 0.8223, web P) has a
   `writing-mode: vertical-lr` container, so css-flexbox-1 §4 makes the flex
   main axis the writing mode's INLINE axis — vertical — and the whole layout
   transposes; the refs and web do that, while both natives derive
   `mainHorizontal` from `flex-direction` alone (`FlexAxes.kt`'s
   `axes.mainHorizontal` on Compose, the `.gapDecorations(…, mainHorizontal:)`
   call in the SwiftUI `ComponentRenderer`), so the items lay out
   left-to-right, the red `column-rule`s stand vertically instead of lying
   horizontally, and the blue `row-rule` lies horizontally instead of
   standing. The montage is a clean TRANSPOSE of the ref, not a mis-measured
   band; Android additionally rotates the item TEXT while leaving the boxes
   horizontal, which is the same split from the other side. The fix is a
   writing-mode-aware flex axis — flexbox-wide, not `columns/`. **Priced
   before proposing** (`vertical-flex-census.mjs`, every component declaring
   `display: flex` and a vertical `writing-mode` on the same element): 11
   tests, 33 scored cells, **4 currently failing** — so the upside is AT MOST
   4 cells and the exposure is **27 cells that pass today** (4 `abspos-autopos-*`,
   3 `flex-abspos-staticpos-align-self-safe-*`, 2
   `flex-align-baseline-column-vert-*`, all P ×3, plus
   `css-writing-modes/forms/input-range-zero-inline-size` web P · iOS f
   0.9747 · Android f 0.9743). That trade is not worth taking blind: it
   belongs to a wave that OPENS with a gate.
   **(e³) 027 stays open and unexplained**: all-three f 0.9012/0.9030/0.9085,
   untracked until the retro (A10#10), not touched by the band rebuild (its
   cross size is auto) and not an erased-ref victim; (f) **Compose
   bare-number `FlexBasis` wire read as PERCENT — PORTED retro R2 (A7#0)**:
   the Swift rule (`FlexboxExtractor.swift:238-240`, bare `.double/.int` →
   percent) now has its Compose twin in `ItemPlacementExtractor` +
   `FlexPercentBasisTest`; pending the gate.
8. Vertical mechanism completion: multi-child balancing, clone-under-
   vertical, §8.3.1 horizontal collapse (wave-47 Z2 logged bails). ~~the
   upright intrinsic Int-overflow at ~2.1e9px summed advances~~ **DONE (wave
   50, lane B5, in tree)**: `core/renderer/VerticalRunIntrinsics.kt` now sums
   through `sumClampedIntrinsic` into `[0, INTRINSIC_SUM_CAP]` (262142 =
   `IntrinsicChannel.packableCap(Constraints.Infinity)`) — two
   `Constraints.Infinity` advances summed to **−2** as an Int and a negative
   intrinsic makes `Constraints` throw, killing the whole capture
   composition. Pinned by `VerticalRunIntrinsicsTest` (9 tests;
   mutation-proved — restoring the pre-fix `values.sum()` turns exactly the
   two new pins red, reproduced independently by skeptic S3). **Zero corpus
   carriers; no cell is predicted to move.**
9. Smaller: (a) **`visibility: collapse|hidden` — the iOS `collapse` half
   is FIXED in tree (wave 50, lane B11); the Compose twin is DELETED as dead
   code and re-queued; the mixed-descendants seam is SPECIFIED, not built.**
   The corpus carries **four** tests that declare `Visibility` and **zero**
   that declare `collapse` (neither in WPT nor in `fixtures/` — the only
   fixture-net `visibility` is `visual-test.json`'s childless
   `Visibility_Hidden` plus two control rows), so everything here is
   correctness and twin parity, not score. **Landed on iOS**:
   `StyleEngine/visibility/VisibilityBoxRules.swift` — the pure rule table
   (declared-vs-inherited resolution, the `collapse` box-type split, and
   `subtreeAlphaEquivalent`, the predicate the seam must consult) — plus the
   live fix, because `VisibilityApplier` used `.frame(width: 0, height:
   0).hidden()` for EVERY collapsed element, so an ordinary `visibility:
   collapse` div lost its layout on iOS while Compose kept it. CSS 2.2 §11.2
   removes only a table row / row group / column / column group; "for other
   elements, `collapse` is treated the same as `hidden`". Skeptic S4 executed
   the rule table through the real extractor on wire-shaped payloads and got
   exactly §11.2, both wire spellings of `table-column-group` accepted, a
   CELL treated as hidden, and the new box-type input provably not setting
   `touched`. **The Compose twin was written and then DELETED** (fix lane F1,
   on skeptic S1's finding that the 176-line `VisibilityBoxRules.kt` had zero
   references anywhere in the repo outside its own test while the Swift one
   IS dispatched). The reason is not tidiness: **Compose has no table-track
   removal path for the rule to dispatch INTO**, so wiring it would produce a
   `treatment` call whose `collapse` and `hidden` arms both end at the same
   `Modifier.alpha(0f)` — a dispatch that cannot render differently. **The
   Compose twin is therefore a queue item under the Compose table renderer**,
   where a real `table-row` / `row-group` / `column` / `column-group` removal
   first becomes expressible; re-deriving it is a 176-line file with a
   committed shape in this wave's git history, and the Swift file is the
   reference. **The mixed-descendants defect is NOT fixed and needs more than
   a call-site swap.** `css-view-transitions/capture-with-visibility-mixed-descendants`
   (web f 0.9215 · iOS f 0.9196 · Android f 0.9196) has a `visibility: hidden`
   red 500×500 box whose child declares `visibility: visible` and is a green
   10×10 square that CSS 2.2 §11.2 says must paint: web paints it, **both
   natives do not**, because each hides with ONE subtree layer a descendant
   cannot escape (`interactions/InteractionApplier.applyVisibility` →
   `Modifier.alpha(0f)`; `StyleEngine/visibility/VisibilityApplier` →
   `.opacity(0)`). The manifest proves the square is the only native-vs-web
   difference on that test: `pairs.iOS-web.pixelMismatchedCount` is **exactly
   100** (= 10×10) at ssim 0.9973, while `iOS-Android` is 1.0000 / 0 px. The
   seam needs (1) an inherited-visibility ambient (`CompositionLocal` /
   `Environment`), because the wire does NOT carry a computed `visibility` on
   every component — `extract-fixture.mjs` ships it only through
   `ROOT_INHERITED_TRIGGER_PROPS` — so an undeclared descendant must inherit
   hidden and only a declared `visible` may escape; and (2) **self-ink
   suppression**: when `subtreeAlphaEquivalent` is false the hidden element
   must drop its OWN background / border / shadow / own text /
   `::before`+`::after` and still render children, which is a config-level
   rewrite in `ComponentRenderer` on both platforms, not a modifier. Call
   sites for whoever takes it: Compose `InteractionApplier.applyVisibility`
   (reached from `StyleApplier.applyConfig`) and the child render loop in
   `core/renderer/ComponentRenderer.kt`; iOS `VisibilityApplier`'s
   `.opacity(0)` branch and `Renderer/ComponentRenderer.swift`. **Prediction
   if it lands**: one cell changes ink — the natives gain the 100 px square
   (0.043% of the canvas) and move 0.9196 → ~0.9215, **still `f`**, because
   web fails the same cell for an unrelated reason (the ref is 390×732 and
   paints a pink `::view-transition` ground the harness does not bake,
   `viewTransitionBaked: false`). What it buys is native parity:
   `nativeParity` iOS-web / Android-web 0.9973 → ~1.0.
   (b) ~~`text-decoration` shorthand colour-function drop~~ **DONE (wave 50,
   lane B6, LANDED in the converter), pending the gate — and its target is a
   DEGENERATE PASS, so record the visible change, not a score delta.**
   `TextDecorationExpander.kt` carried two defects in one `when`: it tested
   only the `#` / `rgb` / `hsl` prefixes, so `text-decoration: underline
   oklch(…)` produced NO colour longhand at all (the identical defect
   `RuleShorthandExpansion` fixed in wave 24 for `column-rule`/`row-rule`);
   and `<'text-decoration-thickness'>`'s keywords were absent from the
   classifier, so `auto` fell through to the legacy "any remaining bare ident
   is a named colour" net and **OVERWROTE an already-parsed colour** —
   `text-decoration: green underline auto` expanded to
   `text-decoration-color: auto`, which every platform resolves back to
   `currentColor` — while `from-font` was dropped in silence (the hyphen
   fails the bare-ident regex). Every token now routes through the ONE
   syntactic classifier `ColorSyntaxClassifier.isColorValue`, and the new
   `else` arm makes the unsupported case loud. Converter suite 514 → 526
   (`TextDecorationShorthandExpansionTest` 8 + `ClearPresentationalHintTest`
   4; skeptic S5 re-ran `:converter:test --rerun-tasks` independently and got
   the same 526). **Blast radius exactly ONE test**, from both the lane's and
   S5's independent censuses over all 1435 per-test IR documents: 70
   `TextDecorationColor` wires, exactly one lacking an `srgb` block
   (`text-decoration-shorthands-001`, `{"original":"auto"}`), and ZERO
   `auto`/`from-font` thickness wires. **That test passes on all three today
   while painting a BLACK underline where it asserts a green one**: the ref
   paints 48 px of rgb(0,128,0)±8 on row 85 and web / iOS / Android each
   paint **0**, at P 0.9998 / 0.9990 / 0.9979 — 48 px of 234 000 sits far
   under the SSIM floor. The sibling `-002` paints 4800 green px on the ref
   and on all three captures, which is the control that says the pipeline CAN
   paint this colour. **So the gate must not read these three cells as
   "unchanged" — it must CONFIRM the 48 green px appear.**
   (b′) **The CSS-wide-keyword hole in that expander is wider than the
   `inherit` pin, and one behaviour changed this wave** (skeptic S5, recorded
   and NOT endorsed; fix lane F4 widened the pin to cover it): `initial` /
   `unset` / `revert` / `revert-layer` mis-expand the same way — they expand
   to a COLOUR longhand only — and `revert-layer` changed behaviour in wave
   50 (previously dropped outright, now `text-decoration-color:
   revert-layer`). Carrier count is honest: exactly **4** `text-decoration:
   inherit` declarations in all of `tools/wpt/css/**` and **none** inside the
   1435-test gate set; zero gate carriers for the other four keywords either.
   One queue line so it is not rediscovered.
   (b″) **`clear`'s invalid-value carriers, from the same lane's converter
   probe**: `left|right|both|none|inline-start` convert to a typed `Clear`;
   `all|up|object|inherit|ALL` fall out as `Generic {_unmapped:true}`
   passthroughs (verified through the converter CLI by B6 and re-verified by
   S5). That is why 3(a)'s bake folds `all` to `both` rather than forwarding
   it, and it is the reason `clear: all` can never be modelled by
   forwarding; (c) ~~the web test
   that cannot fail~~ **FIXED retro R10 (A8#0)**:
   `runtimes/web/tests/typography/SkepticFontShorthandLh.test.ts` pinned
   `font: inherit` with `not.toBe('normal')`, which `undefined` satisfies —
   it could not tell "forwarded" from "dropped" from "wrong". It now pins
   the ABSENCE the live wire actually produces (`'lineHeight' in
   buildStyles([])` is `false` — a CSS-wide keyword is runtime-dependent so
   the converter emits no LineHeight and the inherited `body.wpt-mode`
   line-height keeps applying), with the tolerant branch kept as an
   explicitly-labelled tolerance; (d) flex cross-axis
   auto-margin exemption (TODO in `FlexCrossStretch.kt`); (e) iOS
   `InlineRunFlow.swift` decorations guards carry the []-vs-nil twin
   divergence class the runs guards had (F5 flag; zero corpus impact);
   (f) ~~Android placeholder-floor under-size~~ **the CODE FIX IS APPLIED in
   tree (wave 50, lane B11); what remains is a GATE ACTION, plus a NEW
   width-axis defect found while measuring it.** The arithmetic, derived not
   remembered: `ComponentRenderer` chains `baseModifier` (padding is its
   innermost node), then `placeholderFloorMinSize`, then
   `borderContentInset`, and Compose measures outside-in — so the band is
   already inside the floor node and the padding is added outside it:
   `Android total = padding + max(content + band, minH)` against `web total =
   max(content + band + padding, 30)`. The floor subtracted padding **and**
   the band, i.e. `minH = 30 − padding − band`, giving `30 − band` whenever
   it binds; with `minH = 30 − padding` the two sides are identical. All
   three symbols live in
   `runtimes/compose/src/main/java/com/styleconverter/runtime/StyleApplier.kt`
   and are cited BY SYMBOL because every `path:LINE` in this wave's notes had
   drifted by the round-2 skeptics: `placeholderFloorMinSize` (the modifier
   the chain calls), `placeholderFloorInsets` (the pure inset math — padding
   only, deliberately NO `borderBandInsets` term, and that comment IS the
   fix) and `borderBoxFloorMins` (the pure floor math); the last two are
   `internal` precisely so `PlaceholderFloorBorderBoxTest` can pin them
   without a device. The old model reproduces every committed number
   (`26 = 20 + max(4, 30−24)`, `28 = 24 + max(2, 30−26)`), and the control
   components are exact because their padding alone already consumes the
   30 px floor, which is also why the fix cannot move them. **Exactly 3 of
   the 390 committed baselines change** — obligation #7 carries the ordered
   gate procedure, the expected boxes and the stop condition. **Corpus
   impact: zero** — the titan feeder captures in composed mode
   (`ScreenshotCaptureScreen.kt` provides `LocalWptComposedMode = true`) and
   `ComponentRenderer` skips the floor entirely there.
   (f′) **Residual NOT fixed by that change**: `ComponentRenderer.kt`'s
   `animatedSizeFloor` calls the same `placeholderFloorMinSize` from a chain
   position OUTSIDE `baseModifier`, where padding and band are BOTH already
   inside it — so there the floor should subtract nothing and today subtracts
   the padding, under-clamping an animation-supplied width/height by it. Zero
   fixture carriers, zero corpus carriers. Needs its own call-site-aware
   entry point.
   (f″) **NEW — iOS draws the synthesized placeholder label at HALF the
   width web and Android draw it at, and NO ledger line names a width
   divergence** (skeptic S7 re-measured the committed baselines from scratch;
   S6-13 CONFIRMED). Ink boxes: `090_Button_Primary` web 115×30 / Android
   115×30 / **iOS 50×30**; `091_Button_Outline` 115×30 / 115×26 (→ 115×30
   after the fix) / **50×30**; `105_Edge_DeepNesting` the same;
   `097_Glass_Effect` 100×42 / 100×42 / **50×42**; control `094_Input_Field`
   200 wide on all three, because the label fits and is not clipped. A **65 px
   width divergence** on three components (35 px on 097): web and Android let
   the label overflow the box, iOS truncates it at the box edge ("BUT" /
   "GLAS"). It is a HARNESS placeholder, not a CSS property, so **every
   cross-platform number read off these four components is measuring the
   label rather than the style** — and it is independent of the height bug
   9(f) fixed. The ledger consequence is in obligation #7; decided-PR (A) is
   its natural home; (g) Android `align-items` applied as Box
   `contentAlignment` on NON-flex containers (`ComponentRenderer.kt`, the
   `contentAlignment = displayConfig.alignItems.toBoxAlignment()` call site —
   cite it by that expression; the line has now moved twice, 2820→2845 at the
   retro and 2845→**2942** in wave 50; A11#8 — Layout_C01_AlignContent label
   lands bottom-right); (h) **iOS
   `calc(var(--u)*10)` standalone** resolves to 50px where web/Android give
   `auto` (358) — `fixtures/fidelity/tokens/calc-units.json` 008_tokenMath
   (A11#13; align the calc(var()) fallback semantics across twins); (i)
   **iOS `offset-rotate: reverse`** sign — `layout.combos` 013 (`offset-path:
   ray(45deg)`) iOS 0.93 vs Android==web 0.9982 (A11#13); (j) **Compose
   `offset-path` shapes other than `ray()`** are dropped SILENTLY
   (`StyleApplier.kt`, the motion-path block whose `if` tests
   `config.offsetPath.offsetPath is OffsetPathValue.Ray` — :583 after wave
   50's B9 seam moved the file, so cite it by that expression — the block
   guards on
   `config.offsetPath.offsetPath is OffsetPathValue.Ray` and every other
   `offset-path` value falls out of the `if` with no tracker breadcrumb,
   though css-motion-1 **§2.1** admits `<basic-shape>`, `<url>` and
   `<coord-box>` alongside §2.1.1's `ray()`; pairs-05 015
   `polygon(...)`: natives static, web at the path start) — implement or
   log the unsupported variant via
   PropertyTracker per the no-silent-fallthrough rule (A11#10; the
   background-clip:text and min/max-clamp halves of that finding are FIXED
   retro R6/R2: `ColorExtractorBackgroundClipTextTest`,
   `SizingMinMaxClampTest`); (k) **width-less multicol containers fill the
   canvas on both natives** (`MultiColumnApplier.kt:435 fillMaxWidth`;
   `columns.combos` 004_Columns_Decorated natives 390px vs web ~60px, 0.49)
   — shrink-to-fit like every other harness component, or declare the
   convention and pin it; and **native canvas height ignores negative top
   margins** (pairs-01 034 / pairs-05 018 / spacing.combos 003: web canvas
   10–20px shorter with identical box positions) (A11#15); (l) **native
   runtime bugs hidden behind the extraction wall** (A12#6, all
   `scoreExcluded` under requires-script-mutation(+scroll), so no score
   moves): iOS draws SQUARE double rings around the green disc where the ref
   and web have circular ones (border-radius missing on the ring/clip of a
   scroller with `background-attachment: local` — attachment-local-
   clipping-color-1/2/4/5, ios raw 0.836–0.945); Android clips the ring to
   arcs (android raw 0.934–0.946); display-contents-dynamic-pseudo-
   insertion-001 iOS 0.912 / Android 0.945 colour+coverage. Evidence:
   `tools/titan/results/retro-2026-09-04/excl-detail-wave49-final.out` and
   the wave49-final manifests; (m) file-size cleanups
   (`BackgroundImagePropertyParser.kt` 420 lines, `BackgroundImageExtractor.ts`
   593, `TransformApplier.kt` 1008 after R1 — split lane).
10. **Corpus expansion — recommendation DECIDED, execution open** (wave-48
    W8 arithmetic): Option B — add css-shadow (A-pool 106), css-align
    (54), compositing (48) at depth-48 = +144 slots, +10% gate time,
    ~2.0 discriminating web cells per +1% gate vs 0.45 for depth-96;
    each section carries a named open bug (6c over-blur, align-self,
    the PR-#126-disturbed blend/clip area). Budget triage: ~250
    font-parity-tagged docs outside the current sample now arrive
    SCORED (the Rule-43 gate is per-test, decline-on-unknown). Also:
    hanging-punctuation ×2 are impossible passes (Chromium doesn't
    implement it) — exclusion candidates that would remove 2 permanent
    web fails from the honest denominator (wave49-final block-bound-001
    0.7567/0.7440/0.6796, inline-bound-001 0.8467/0.6789/0.6176).
    **Do NOT exclude `hanging-punctuation-block-bound-001` before the
    erased-ref calibration lands**: its reference is one of the six scored
    match-targets the injected body background ERASES (wave 50, lane B10 —
    58 184 chromatic pixels erased, the largest of the six), so its current
    three-platform disagreement at 0.68–0.76 is measured against a blank
    picture. After the ref is re-frozen the three platforms disagree with
    EACH OTHER, which makes it a real render defect and actionable for the
    first time rather than an exclusion candidate. See queue 7(e¹) and
    "Instrument decisions pending".
11. **Untracked native/web fails with named mechanisms** (A10#10 — named
    as deferred in corpus notes or commit bodies, never queued; all
    wave49-final, flat since wave47-final unless noted):
    (a) `css-lists/counter-list-item` iOS f 0.7518 / android f 0.7407
    (canvas 1990 vs ref 1028) — "Android missing ::after + spacing
    dominate" (corpus-v6-9). **The Android half is addressed by the wave-50
    `::after` fold (2(a)) and the cell is predicted to IMPROVE but NOT to
    flip**: the 33 `" N"` runs come back and the canvas should collapse
    1990 → ≈959, while iOS keeps failing the same cell on the bold `<h2>` —
    which is 0(g)'s `UAElementFontRule` with no Compose twin. Watch it as a
    mover, not as a flip; (b) `css-flexbox/align-items-007` natives f
    0.9974/0.9966 cF (web P 0.9990) — the wave-44 honest loss with a real
    colour divergence the flip's skeptic flagged; (c)
    `counter-style-at-rule/broken-symbols` now fails on BOTH natives (iOS
    0.9486, Android 0.9490; wave 41 recorded only the Android −1). **Both
    are predicted to FLIP by lane B4's autoclose (2(c²)) — an unclaimed +2
    the lane did not argue for**: the web capture prints `1. Should have "1."
    as bullet point.` TWICE where the ref prints it once (a degenerate pass
    at P 0.9749), and S7 erased the duplicate line and scored web **P
    1.0000**, iOS **P 0.9713**, Android **P 0.9713**; (d)
    `line-clamp/discard/discard-multicol-003` web f 0.8896 while both
    natives PASS (0.9954/0.9530) — a web fail since ≥ wave 47 that no note
    mentions; (e) `hyphens/hyphenate-character-005` natives f 0.9450/0.9474
    (wave-45 S5's "sole 10ch case"); (f) `css-color/currentcolor-001/-002`
    Android 0.8957 — FontSize `{type:percentage}` was not resolved against
    the inherited size before it became the em basis (A3#0; **retro R3's
    seam patch is APPLIED in this tree — `DynamicValueResolver.kt`'s
    percentage envelope (11 `percentage` sites) +
    `runtimes/compose/src/test/.../core/variables/FontSizePercentageEmBasisTest.kt`
    (4 tests), verified 2026-09-05 — 96×96 → 192×192 box, predicted Android
    flip pending the gate; iOS 0.9990 P / web 1.0000 P must not move; 6
    carriers, the other 4 score-excluded**); (g) the css-gaps cells in 7(e).
12. **Feature briefs that exist only in corpus notes** (A10#10): css-logical
    "residual is orthogonal-flow" + the "direction half" of orthogonal flow
    (corpus-v6-1/v6-2, W6 "deferred"); css-ruby / css-inline "briefs on
    file" (v6-1 — the files were session artifacts, gone: re-mine from the
    wave49-final PNGs before staffing). Wave-38 N7's five unrepaired
    classes — C1 margins-in-fill-width, C2 undeclared-display inline box,
    C3 Android flex min-content, C4 the four css-masking clip-path gaps, C5
    selectors text-metric — are recorded only in the surviving pointer
    `tools/titan/results/wave38-N7-native-tails.json` field `notFixed`
    (its `_diag38/N7/classification.json` is gone); re-classify from the
    current gate before acting.

## Instrument decisions pending (decide, then execute or Park — never drift)

- ~~**The successor to the novel-ink veto**~~ **DECIDED AND EXECUTED (wave
  50, lane B12): built, pre-registered, MEASURED, shipped DISARMED — and the
  recommendation recorded is to STOP pursuing a scalar veto over the pass
  column.** `tools/titan/degenerate-veto-probe.mjs` implements obligation
  #4's named displacement-aware denominator: a divergent pixel where the
  capture painted is *explained* when some translation of at most R = 4 px
  carries reference ink OF THE SAME COLOUR onto it, and symmetrically a
  divergent pixel where the reference painted is *dropped* when no such
  translation carries capture ink onto it; the canvas is DERIVED as the
  reference's modal colour, never asserted, so nothing in the module names a
  hue, a channel or a test. R = 4 px is four times the largest rasteriser
  jitter measured in the sample itself. It is wired into
  `tools/titan/inject-wpt-block.mjs` — the block is computed and stored on
  every `<platform>-ref` diff for triage — and it can change a verdict ONLY
  under `TITAN_DEGENERATE_VETO=1`, pinned in both directions in
  `inject-wpt-block.test.mjs` and mutation-proved against `isArmed()` forced
  true. Measured ONCE, on obligation #3's committed 100-cell sample:
  **precision 0.9286 (13 true / 1 false), recall 0.3714 (22 missed)**, FP
  1.5% of the 65 hand-correct cells (2.4% among the 42 that genuinely
  differ). **The pre-registered 95%-recall bar fails conclusively** — the
  UPPER end of the recall interval is 53.7%, so it is not a sample-size
  artifact. Skeptic S6 re-derived the confusion matrix from the PNGs and S5
  recomputed every published figure from the raw blocks; both reproduce
  exactly. The structural reason is the durable finding: of the 22 missed
  defects **15 bind on the FRACTION bar, not the mass bar** — for a typical
  wrong render in this corpus the MAJORITY of the disagreeing pixels are
  explained by a small rigid translation of reference ink. The committed
  post-hoc sweep (reported, explicitly NOT adopted, because a rule chosen
  after seeing the table is not a measurement) shows no operating point of
  the metric family clearing 95% recall at usable precision. **Decision:
  keep it as a triage stamp; do not arm it; do not retune the bars; spend
  the next instrument effort on the mechanism families 0(j)–0(n) and on the
  two denominator problems below.** If a later wave widens or arms it, the
  first optimisation is an early-out once `divergentInkPx` exceeds the
  threshold — the per-cell cost tracks that count almost exactly (6.8 ms
  average over the 144 `css-tables` cells, 35 ms worst there, 985 ms on the
  390×9470 `text-decoration-inset-025` Android capture).
- **The blank-capture guard — DECIDE IT, and key it on UNIFORM COLOUR, not
  on alpha.** There is no blank-capture guard anywhere in `tools/` today: a
  composed capture of a WHITE-canvas document with no ink in it is scored
  like any other render, and the only place the shape is even mentioned is
  `inject-wpt-block.mjs`'s `computeColorComposite` comment, which EXCUSES it
  ("labDeltaE is null for degenerate pairs"). The check that can be made to
  fail, and the population that proves it: **42 wave49-final captures are a
  single uniform colour against a reference that CARRIES INK and are all
  still scored**, with scores from 0.0001 to **0.9959**
  (`tools/titan/results/wave50-S6/blank-capture-vs-inked-ref.json` —
  notable single-platform rows: `counter-cjk-decimal` Android 0.9407,
  `attr-style-sharing-4` Android 0.9959, `attr-notype-fallback` Android
  0.9584, `clip-path-inset-round-rendering` iOS 0.7483,
  `broken-column-rule-1` iOS 0.9752,
  `composited-under-rotateY-180deg-preserve-3d` iOS+Android 0.9565). **The
  alpha-only predicate finds exactly ONE** — `text-decoration-inset-025`
  Android, the only fully transparent capture among all 4305
  (`tools/titan/results/wave50-B5/blank-captures.json`, queue 1(c)) — while
  the uniform-colour predicate finds forty-two. Ship it as a CAPTURE FAILURE
  in the exit-7 family, not as a score, and mutation-prove it against those
  42 rows. **It does NOT address the 21 blank-vs-blank PASS cells** — those
  are a test that cannot discriminate, queue 0(n) — and conflating the two
  would decide 0(n) silently.
- **The erased-reference body background — a CALIBRATION, not a render
  change** (wave 50, lane B10; the erasure verified from the frozen bytes by
  S6-09 / S6-10). `capture-browser-ref.mjs`'s `canvasFrameCss()` injects
  `:where(html, body) { …; background: #FFFFFF }`; canvas propagation already
  paints the page white from the ROOT's background, so the BODY half
  additionally paints an opaque box in the body's own layer and CSS 2.1
  Appendix E buries every `z-index: -1` body child under it. The fix —
  `tools/titan/results/wave50-B10/capture-browser-ref-body-background.patch`,
  `git apply --check` verified, applied/exercised/reverted in-lane — moves
  `background` off the body half and keeps it on `:where(html)`, where canvas
  propagation reads it, and replaces the now-wrong "background legitimately
  stays on both" note with the measured account. **Scope, S6-verified**: 42
  of the 8088 `*-ref.html` files in `tools/wpt/css` declare a negative
  z-index; **8** are the match target of a scored wave49-final test (24
  cells, 10 failing) and **6 of those 8 are erased** (the other two are
  css-view-transitions pairs whose negative z-index sits inside a
  `::view-transition` stacking context). Carry B10's own blind spot forward:
  `zneg-census.json`'s `unresolvedMatchLink` is **33** — 33 scored tests have
  a `rel=match` link the census could not resolve — **so 42 is a LOWER
  BOUND**; and B10's "28 of the 42 rasterise differently under the fix" is a
  live-browser measurement S6 could not re-run and calls UNVERIFIABLE.
  Because this is an instrument change the standing rule applies in full:
  **bump `CANVAS_REV`, re-freeze the affected refs, and adjudicate the
  resulting moves with the Calibration-gate recipe** (sha1 every capture PNG
  against the previous gate; identical bytes = instrument-only, different
  bytes = a render change). Expected moves, stated up front so they cannot be
  re-narrated afterwards: 034 and 035 **f → P on all three (+6)**; 036 and
  037 keep their verdicts while their scores rise; **033 web f → P (+1) and
  both natives P → f (−2)**, which is the honest direction — they paint
  nothing where Chromium paints 10 px rules in 0 px gaps, and suppressing it
  would mean keeping a degenerate pass; `hanging-punctuation-block-bound-001`
  stays `f` and becomes ACTIONABLE for the first time, because its three
  platforms disagree with EACH OTHER at 0.70–0.84 (queue 10). Net on the 18
  cells: **+5 verdicts, −2 verdicts, and one previously unreachable defect
  made visible.** Regenerate the scope with
  `node tools/titan/results/wave50-B10/census.mjs`; re-measure the erasure
  with `zneg-ab-probe.mjs` and the patch's byte-level blast radius with
  `canvas-css-ab.mjs`.
- **Threshold study C4 is half-flipped** (A10#6). `docs/STATUS.md`'s
  heading **"PROPOSAL (not flipped here — every recorded number moves)"**
  (cited by name, not line: STATUS grows every wave) derived SSIM ≥0.95
  (keep) · Δpx ≤1% · **ΔE95 ≤2.0 (from 5) · dpxStrict ≤3.5% (new fourth
  gate)** — margins under it: ΔE95 45% headroom, dpxStrict healthy max
  2.927% vs the 3.5% bar — with
  the blur acid test passing under it; only the pixelmatch half (0.02 +
  AA detection) landed. `tools/visual/cross-platform-gate.mjs:90
  DEFAULT_DELTA_E_THRESHOLD = 5.0` still gates and no dpxStrict gate exists.
  Decision: flip both after one device run confirms committed baselines ==
  live captures (the noise floor says they must; the flip is the wrong
  moment to lean on "must"), then the named follow-up — content-cropped
  metrics (crop to the non-ground bbox before scoring, ~3–5× sensitivity).
  Owner: the wave-50 calibration lane, same PR as (C) above.
- **Extraction-wall re-measure** (A9#2). 13 of the 52 wall-excluded tests
  render the delivered (pre-script) document to a ref match on ALL THREE
  with no veto — two at 1.0000 (overlay-transition-backdrop-entry,
  position-fixed-scroll-nested-fixed), the rest 0.95–0.99 (list:
  `tools/titan/results/retro-2026-09-04/excl-detail-wave49-final.out`; 39
  cells withheld from numerator and denominator). Whether they are real
  passes is decidable by the wave-48 W2 / Rule-42/43 ceiling method:
  render the TEST page post-script in the refs' own headless Chromium under
  `capture-browser-ref.mjs`'s canvas contract, diff against the committed
  ref; where Chromium's own post-script render matches (≥0.95, no veto) the
  pre-script match is a real pass → an `EXTRACTION_WALL_ADMITTED_TESTS` set
  consulted by `applyNaScoreGate` (mirror of the Rule-43 refusal list:
  index-free keys, measured ssim per line, decision rule stated in
  advance, unit-pinned listed-scored / unlisted-not). A12#9's caution
  stands: the ones opened by eye were mostly dilution false-passes, so
  expect few admissions — the value is the ceiling record plus the
  calibration set for obligation #4.
- **Standing report of excluded tests whose platforms disagree by >0.1**
  at every gate (A12#6): exclusion is formally justified when the pipeline
  cannot deliver the ref's input, but three runtimes rendering the SAME
  delivered IR differently is a runtime measurement regardless — the
  Rule-43 pattern hid 9(l) for 30 waves. One `inject-wpt-block.mjs` summary
  line per section; no scoring change.
- ~~**Compose `PropertyRegistry` is test-time theatre** (A6#8)~~ **DECIDED
  (b) and EXECUTED, 2026-09-05.** The finding: `isMigrated`/`ownerOf`/
  `allRegistered` (`PropertyRegistry.kt:77,84,95`) and all 28
  `*Registration` objects are referenced only from tests; a Kotlin
  `object`'s init runs on first touch, so in production none of the
  `migrated()` claims register and no production code consults the
  registry — dispatch is inline in ComponentRenderer/StyleApplier. iOS and
  web DO consult theirs (`StyleBuilder.swift` `PropertyRegistry.migrated`
  at :300-334; `StyleBuilder.ts:14 isLegacyProperty`). The two options were
  (a) a runtime bootstrap that touches every Registration object +
  `coverage-audit.mjs` reading `allRegistered()` from a JVM run, or (b)
  reword CLAUDE.md and delete the dead counters. **(b) is taken and all
  three halves are in the tree**: CLAUDE.md's "Registered, not dispatched
  inline" bullet now says the Compose registry is a TEST/AUDIT artifact
  whose per-category tests are the executable check (P2a docs half, applied
  by the integrator); web's `migratedCount` is deleted (P2c, A6#13 —
  `PropertyRegistry.ts:310` records the removal); iOS's `isLegacy(_:)` and
  `migratedCount` are deleted (round-2 lane F2, from P2a's seam patch —
  `PropertyRegistry.swift:220-229` records it; both had zero references in
  Sources/, Tests/ and apps/ios-harness). The Compose helpers stay, as the
  test/audit artifact CLAUDE.md now describes them to be. Do not re-open
  (a) without a production consumer for the registry.
- **Mechanisms with no production caller** (A6#11) — one DECIDED, the
  rest still open. **`CollapsedBorderConflict`: DECIDED — DELETED on both
  natives 2026-09-05** (P2a Compose, P2b Swift, coordinated): TableBoxTree
  only ever mentioned it in doc comments, the css-tables border-conflict
  cells pass by another path, and its 110 Kotlin + Swift test references
  pinned dead weight. `TableBoxTree.kt:128` / `TableBoxTree.swift:114` and
  `TableUaDisplayTest.kt:80` / `TableUaDisplayTests.swift:63` record the
  deletion; the table trees are now 6 Compose · 4 SwiftUI · 16 web source
  files (`tools/titan/wpt-not-applicable.mjs` `requires-table-layout`
  census corrected to match).
  **Two more were DELETED in wave 50 on the same principle**:
  `effects/shadow/MultipleShadowApplier.kt` (zero call sites since the
  initial import 954b38e8, yet named by queue 6(c) and by a `docs/STATUS.md`
  row as the LIVE over-blur mechanism — both corrected this wave; the live
  map is now pinned by `ShadowBlurSigmaParityTest`), and the Compose
  `visibility/VisibilityBoxRules.kt` twin lane B11 had just written, which
  had zero references outside its own test while the Swift twin IS dispatched
  — deleted because Compose has no table-track removal path for it to
  dispatch INTO, and re-queued under the Compose table renderer (9(a)). Doc
  consequence to carry: the dedicated-`*Applier` file count for Android drops
  45 → **44**, a number `README.md`, `CLAUDE.md`, `docs/STATUS.md` and
  `tools/visual/COVERAGE.md` all quote and only COVERAGE.md is
  machine-checked.
  Remaining, each still needing WIRE-or-DELETE
  with its tests: `background/RepeatingGradientHelper.kt`,
  `scrolling/ViewTimelineExtractor.kt`, the wave-42 multicol helpers
  (`columns/MulticolLineSnap.kt`, `columns/MulticolRunFragment.kt` +
  `MulticolRunFragmentMeasure.kt`, `soleFlowFragmentReplay` in
  `columns/MulticolSpannerFlow.kt`) after W3's slice replay,
  `TextStyleApplier.extractHasOverline`, and `FlexAutoMinSize.MECHANISM_ENABLED
  = false` (`FlexAutoMinSize.kt:113` — see Parked). Tests that pin code
  nothing runs are false assurance and inflate the documented suite counts.
  Beyond these named mechanisms, P2a's measured tail — **187 zero-reference
  declarations (~1,319 body lines) nested inside otherwise-live Compose
  files** — is committed at
  `tools/titan/results/retro-2026-09-04/p2a-compose-dead-remaining.json`
  (identifier-frequency scan, comment-stripped; re-derive before acting,
  and check reflection/serialization by name before deleting any
  `@Serializable` member).

## Parked (decided, do not revisit without new evidence)

- Noto corpus-wide bundling: REJECTED by measurement (wave-45 pilot, net
  zero). Binding constraints named and both since fixed — hence the
  wave-48 Rule-43 re-measure (queue #2 residuals are what remains).
- SVG pre-raster: default ON since wave 44 (earned via A/B;
  `svg-preraster.mjs:364`).
- WOFF→TTF hop: default ON since wave 42 (validated cache).
- Mono pin: default ON since wave 47 (earned via A/B; escape
  `TITAN_MONO_PIN=0`; `mono-pin.mjs:4` banner, `:88` escape hatch — lines
  re-resolved 2026-09-05).
- dev→main promotion + v0.2.0 tagging: declined by owner.
- Cross-machine noise floor: deferred until visual jobs move to CI.
- image() loadable-src-plus-colour precedence: natives resolve
  colour-first (loadability unknowable at extract), web url-stacks so a
  loadable src wins — divergent only for a shape no corpus test
  carries; cross-referenced in all three extractors. Revisit only with
  a fixture (and ledger the divergence the same day).
- **`FlexAutoMinSize` gated off** (`MECHANISM_ENABLED = false`, wave 39):
  the two-APK A/B proved zero cells won and it probed SubcomposeLayout
  intrinsics. DECIDED (retro): DELETE at the P2 dead-code sweep unless a
  carrier appears before it; the tripwire stays until then.
- **`perspective: 0`**: the 1px clamp (css-transforms-2 §8 / §12.2), as
  retro R1 implemented on Compose; web's canvas-filling projection is the
  same semantics at the limit. Ledger any residual pair after the gate,
  never a `none` fallback.
- **Compose text-shadow σ patch — PARKED until a text-carrying text-shadow
  fixture exists.** `tools/titan/results/wave50-B8/compose-text-shadow-sigma.patch`
  (compiled, `TypographyWave2Test` 12/12 green with it applied, NOT applied —
  `typography/` was outside lane B8's ownership). The live text-shadow path
  passes the raw CSS blur radius as Compose `Shadow.blurRadius`
  (`typography/TypographyConfig.kt` `toShadow()` and
  `typography/TextStyleApplier.kt` `extractTextShadow`), Compose hands that
  to `Paint.setShadowLayer`, and Skia reads it as σ = 0.57735·radius + 0.5 —
  so a 4 px CSS blur paints σ 2.81 where css-text-decor-4 §2.6 →
  css-backgrounds-3 §6.1.2 ask for 2.0, and where iOS already divides by two
  and web renders natively. **Why parked and not applied**: the
  Compose→`setShadowLayer`→Skia mapping is READ from the platform sources,
  not measured, and the two committed baselines that would show it
  (`077_TextShadow_Simple`, `078_TextShadow_Glow`) are DEGENERATE — their
  fixture components carry no text, so all three platforms capture the
  harness label only and there is zero glow ink to fit. Un-park it by
  authoring a text-carrying text-shadow fixture with shown arithmetic, then
  applying and measuring; do not apply it blind.
- **`br-clear-fold.patch` — PARKED until the line-box clearance rung
  exists.** `tools/titan/results/wave50-B6/br-clear-fold.patch` is written,
  differential-measured (4 fixtures of 2870) and deliberately NOT applied:
  alone it moves the orange band without fixing it and puts 12 passing cells
  at risk for no gain, because Compose's `MulticolFloatStrip.factsFor` reads
  `Clear` off the strip CHILD's own property list. It ships only together
  with the CSS 2.1 §9.5.2 line-box clearance rung described at queue 3(a).
  Recorded here so no future wave re-derives the bake.

## Known-broken (documented, unfixed, not blocking the pipeline)

- **swiftui runtime Release build crashes swift-frontend** (A10#7; A2#28
  RE-VERIFIED 2026-09-04, exit 65). Repro: `xcodebuild -target
  StyleConverterTest -sdk iphonesimulator -arch arm64` (Release, `-O
  -enable-default-cmo`, Swift 6.3.3) → "While silgen emitFunction for
  'styledContent(now:)'" (`Renderer/ComponentRenderer.swift:869` today;
  STATUS quotes :835, only the line drifted) — the deep nested
  `struct_type` chain of Applier types tips the optimizer over.
  Workaround, already what `test-all.sh` passes: `-configuration Debug`
  (`-Onone -disable-cmo`, ~20 s). A CI Release build or a ship would hit
  it cold. Fix direction (STATUS "Known-broken: iOS harness cold build"):
  break the Applier modifier-chain type nesting.

## Operational recipes (hard-won; read before gating)

- **Fresh-worktree provisioning** (wave-48 additions): beyond
  `local.properties` (root + apps/android-harness) — the WPT mirror
  (`tools/wpt/`, ~600M: move from the old worktree or
  `tools/titan/fetch-wpt.sh`), the iOS xcodeproj
  (`cd apps/ios-harness && xcodegen generate`), and **node_modules**: a
  real DIRECTORY of per-entry symlinks into a sibling store is fine,
  but the three workspace packages (`@style-converter/web`,
  `web-harness`, `style-converter-tools`) MUST link into THIS tree —
  a whole-dir symlink into another worktree serves that tree's web
  runtime to every vite capture (silent, proven wave 48). Clear
  `apps/web-harness/node_modules/.vite*` after repointing. Durable fix:
  a real `npm install` in-tree.
- **Gate script — the whole recipe is now CODE, and it is ONE command.**

  ```bash
  tools/titan/gate-driver.sh waveNN-final          # --resume to continue
  node tools/titan/score-gate.mjs wavePP-final waveNN-final \
       --json tools/titan/results/waveNN-gate/score.json \
       --watch tools/titan/results/waveNN-gate/watchlist.txt
  ```

  `tools/titan/gate-driver.sh` (wave 50) does everything the prose below
  used to ask a human to remember: the quiet-host refusal
  (`GATE_MAX_LOAD` 6 on the 1-min load, `GATE_MIN_FREE_MB` 2048 on
  free+inactive, exit 2 with the reason rather than a slow death), the
  detached adb server, ONE emulator + one simulator, provisioning, the 30
  sections at `--max-tests 48`, a per-section PROCESS-GROUP watchdog that
  reprovisions and retries once, per-column capture-count verification, and
  finally `BASELINE=1 ./test-all.sh --gate-set`. `tools/titan/score-gate.mjs`
  is the scorer of record (standing constraint above): the standard idiom
  (`typeof x.ssim === 'number' && !x.scoreExcluded`, pass =
  `wptPass === true`) and the per-cell diff of the two run dirs, printing
  `gained` / `lost` / `movers` / **NEWLY MEASURED** / `unmeasured-now` as
  five disjoint lists. **Validated on history: `wave48-final` →
  `wave49-final` reproduces corpus-v6.15's +32 / 0 exactly.**
  **The preconditions are still yours, not the driver's**: stop our
  processes first (`pkill -f qemu-system; pkill -f "Chrome for Testing";
  ./gradlew --stop`), close the browsers, and prune disk (the APFS
  `tmutil thinlocalsnapshots` step, and `xcrun simctl delete unavailable` +
  deleting every Shutdown sim except the seed) — see "Gate on a quiet host"
  below, which is the 17-attempt lesson the driver enforces but cannot
  create. Set `ANDROID_SERIAL` when more than one device is attached.
  What `--gate-set` buys (retro A12#3): one child run per line of
  `tools/visual/gate-fixtures.txt` — visual-test, composition-test,
  filter-sepia-amounts, nested-transforms, radius-overflow-transform,
  blend-isolation, opacity-blend — so every fixture-scoped ledger line and
  every `_expect.waive` is actually exercised and CAN go stale; a fixture
  with no committed baselines runs gate-only until `UPDATE_BASELINE=1
  ./test-all.sh <fixture>` seeds it; the first non-zero child exit is
  re-raised and exit 7 is a missing/short column. **Still human, still
  mandatory**: open the PNG of every LOST cell and every headline GAINED
  cell next to its frozen ref before writing a sentence about it, and
  `UPDATE_BASELINE=1` only after LOOKING at each changed PNG.
- **Shared-file lane protocol** (learned the hard way in wave 50, where B7,
  B1, B4 and B5 all needed `tools/titan/extract-fixture.mjs`):
  - A builder lane that must change a file another lane OWNS develops on a
    **private copy** and delivers a **patch** under
    `tools/titan/results/<wave>-<lane>/`, with the re-anchoring instructions
    in its note (each hunk anchored on a unique stable line, so a rebase is
    mechanical). B7's three patches say exactly where each block goes; that
    is the shape to copy.
  - **Never restore a shared file from a snapshot.** A lane that copies the
    file out, edits, and copies a WHOLE older version back silently reverts
    every other lane's landed work.
  - Skeptic and fix lanes mutate a production file only as
    **cp → sha256 → edit → run → cp-back → sha256 verify**, and record the
    digest in their note (wave 50 did this for `extract-fixture.mjs`
    `adb1e195…`, `StyleBuilder.swift` `7a10196f…`, `UaBoxlessDisplay.ts`
    `209913f5…`, `GapDecorationBands.swift` `3d97a79d…`,
    `degenerate-veto-probe.mjs` `e9af3a8c…`). **No lane runs `git checkout`,
    `git restore` or `git stash` on the shared tree** — the stash stack is
    shared across worktrees and the checkout would take the whole file.
  - **Disjoint build systems per concurrent lane.** One lane owns root
    gradle (`:converter`), one owns `apps/android-harness` gradle, one owns
    Catalyst `xcodebuild`, one owns vitest — concurrent gradle daemons on one
    tree produce spurious AGP jar-scan failures (standing constraint above),
    and a skeptic that needs a count from a build system it does not own asks
    the lane that does.
  - **A patch whose predicate runs on a path your census did not walk is
    unmeasured.** B5's seam-S2 censused wave49-final's per-test IR while the
    predicate runs on the STATIC walker, and missed a second carrier with two
    PASSING cells; post-load bailed 49× and declined 9× in that same run, so
    bail-to-static is live and an IR census is never a safe proxy for a
    static-path change.
- **Twin-picture flip claims have a measured noise floor** (wave-50 skeptic
  S7, over the 1366 wave49-final cells scored on all three platforms). Raw
  SSIM agreement between rasterisers: |iOS − Android| p50 0.0008 / p75
  0.0092 / **p90 0.0312** / p95 0.0479; |web − Android| p50 0.0025 / **p75
  0.0222** / p90 0.0520 / p95 0.1043; |web − iOS| p50 0.0016 / p75 0.0152 /
  p90 0.0494 / p95 0.0884. **Rule of thumb: a "the twin's picture is the
  prediction" flip claim whose headroom over 0.95 is under ~0.031 is a coin
  flip, and under ~0.022 for a web→Android twin.** Every wave-50 claim in
  that band is labelled thin at its queue item; re-run
  `tools/titan/results/wave50-S7/replay-gaps.mjs` to refresh the table after
  a gate.
- **Calibration gate** (mandated after any instrument change — scorer,
  veto, threshold, capture contract): re-run the previous gate's sections
  under the new instrument with a `-cal` run-id, then sha1 every capture
  PNG against the previous gate's (`shasum -a 1
  <run>/sections/*/{android,ios,web}-screenshots/*.png`); a flip whose
  capture bytes are IDENTICAL is instrument-only, a flip whose bytes
  differ is a render change and is adjudicated as such in the `-cal`
  snapshot's `_note` (A1#2 — corpus-v6-13-cal misfiled one). The snapshot's
  `artifact`/`reproduce` name the `-cal` run-id.
- **Single-fixture recovery** (adb-pull truncation, one missing diff):
  `feed-android.mjs --fixtures <one>.json --composed --device <dev>
  --wpt-dir tools/wpt --out <run>/android-screenshots` then re-run
  `inject-wpt-block.mjs` with the gate env
  (`POST_LOAD_EXTRACT=1 BIDI_BAKE=1 VT_BAKE=1`) and the section's
  `--combined fixtures/wpt/_section-<sec>.json`. **Refeeds must carry
  `--wpt-dir`** — and since the retro both feeders REFUSE (exit 2,
  `FATAL: fixtures declare N @font-face src(s) …`, `feed-lib.mjs`) any
  batch that declares assets without it, before the first device call
  (A9#3); a capture that fails to parse twice is now deleted so the cell
  is MISSING (caught by `NATIVE_SHORT`), never a corrupt `{error}` diff
  (A9#4).
- **Column recovery** (whole platform column dead): same recipe with
  `--fixtures <run>/per-test-ir`; for iOS use `feed-ios.mjs --udid
  0BB986A6-1EAD-4916-9276-079235324DA1` (boot it first).
- **Sim-pool exhaustion** (iOS column lost mid-gate to `no free iOS
  simulator`): the wave-47 gate lost its whole css-view-transitions iOS
  column this way (corpus-v6-12 `_note`; STATUS wave 47) and recovered it
  by section refeed — the **Column recovery** recipe above (`feed-ios.mjs
  --composed --wpt-dir tools/wpt --udid <seed>` on the section's
  `per-test-ir`, then re-run `inject-wpt-block.mjs` with the gate env).
  Cause and prevention, both now code: `provision-devices.sh` used to
  `simctl create` a fresh sim on every run that found too few booted ones
  (the 81-sim / 38 GB CoreSimulator incident, obligation #6) and the pool
  filled with Shutdown duplicates; since the retro it boots existing
  Shutdown `titan-pool-*` sims FIRST and creates only the shortfall (R8b,
  A9#6; `tools/titan/README.md` "Simulator pool growth"). Pre-gate: `xcrun
  simctl delete unavailable`, delete every Shutdown sim except the seed
  (the script never deletes devices), then check the pool with `xcrun
  simctl list devices | grep titan-pool`; a mid-run exhaustion is a
  `NATIVE_SHORT` section (exit 1 after the manifest) — refeed the section,
  never `SKIP_IOS=1` it away (exit-7 contract, obligation #2).
- **Phantom-PNG suppression asymmetry** (a device column's poll waits on
  PNGs the device never writes, then every later positional index is
  misaligned): all three device harnesses SUPPRESS the standalone capture
  of a child that depends on its backdrop (`backdrop-filter`, a non-normal
  `mix-blend-mode` — with nothing behind it the capture is meaningless),
  and the feeder must mirror that rule when it flattens the component tree
  or its manifest lists phantoms. The rule lives ONCE, in
  `tools/titan/feed-lib.mjs` (`flattenComponents` + `dependsOnBackdrop`,
  the byte-for-byte mirror of the harnesses' predicate); feed-android used
  it from the start, feed-ios kept a LOCAL flatten without it and shipped
  the round-4 Android bug anew on the iOS path (commit 6e03015c, wave-48
  intake) — fixed by making feed-ios walk with the shared predicate,
  pinned in `tools/titan/feed-ios.test.mjs` ("suppresses backdrop-dependent
  children like the device"). Symptom to recognise: capture count < manifest
  count with the poll waiting on names that never appear, and diffs
  attributed to the WRONG component from the first suppressed child on.
  Fix the mirror (one predicate, never a per-feeder copy); never lengthen
  the poll, never suppress on one platform only.
- **Read-only emulator adb wedge** (A11#17): after ~85 consecutive
  `test-all.sh` runs on one `-read-only` instance the adb shell wedged —
  `adb shell ls` hung 898 s until killed (exit 143), and a rebooted
  instance wedged again within one fixture. `test-all.sh` now bounds EVERY
  adb call (`ADB_TIMEOUT`, default 60 s; `ADB_PULL_TIMEOUT` = 120 s +
  component count; retro R8a) and fails the Android stage loudly (exit 1)
  on a hang. Recipe for long sweeps: reboot + reprovision
  (`tools/titan/provision-devices.sh`) between ~50-run blocks rather than
  waiting for the watchdog to fire.
- **Seize-only motion fixtures** (A11#16): a fixture whose live-clock
  capture is meaningless (each platform at a different animation phase —
  keyframes-basic measured 22/24 pairs "unexpected" at 0.79–0.93) declares
  a top-level `"_capture": {"seizeOnly": true, "recipe": …}` (retro R8a;
  `fixtures/fidelity/motion/keyframes-basic.json`); a bare `test-all.sh`
  run refuses it with exit 2 and the pointer to
  `tools/visual/animation-sweep.sh` (`CAPTURE_ANIMATION_TIME` +
  `SIMCTL_CHILD_CAPTURE_ANIMATION_TIME`, docs/DYNAMIC_CAPTURE.md §4); a
  seized run passes straight through. **Every fidelity fixture is
  GENERATED**: a header hand-added to the committed JSON fails
  `gen-fidelity.test.mjs`'s byte-identity pin (which regenerates all 62
  files and compares) and leaves `fixtures/fidelity/manifest.json`'s
  `bytes` field stale — so the emission belongs in
  `tools/visual/gen-fidelity.mjs`'s builder, and `node
  tools/visual/gen-fidelity.mjs` runs after it. Same rule for any future
  `_capture` declaration.
- **adb's auto-started server inherits your pipeline's fds** (retro gate,
  2026-09-05: a 5-hour silent hang). `adb devices | grep | awk | while read`
  never sees EOF when that `adb devices` call was the one that spawned the
  server daemon — the daemon keeps the pipe's write end open forever, and
  `lsof` shows awk still writing and bash still reading with no other
  child. Recipe: start the server ONCE with detached stdio before any
  pipeline — `nohup adb start-server >/dev/null 2>&1 </dev/null` — and read
  device lists through a function that redirects stdin from /dev/null
  (`adb devices </dev/null | awk '/emulator/ {print $1}'`), never a live
  `while read` over adb output. Any gate script that restarts the adb
  server (the wedge recipe above) MUST do this immediately afterwards.
- **Run the gate with ONE emulator (`WANT_ANDROID=1`)** on this host: in 4 of
  the 5 retro-gate attempts (2026-09-06) the SECOND concurrent `-read-only`
  instance of Medium_Phone_API_36.1 wedged — `adb install` reported success
  while the package never landed (now verified with `pm path`), the app
  vanished after a silent guest restart, or adbd never answered so the
  unbounded `getprop sys.boot_completed` poll hung 30 min (now `_bounded 15`).
  Sections run serially, so a second instance buys nothing at the gate.
- **Gate on a quiet host** (the 17-attempt lesson, 2026-09-05→07): the
  gate itself adds ~10 load (emulator ~4–5 cores of software GPU, vite,
  puppeteer, gradle) and ~4 GB RAM; on a 16 GB Mac with browsers open it
  swaps, and then EVERYTHING times out. Preconditions: our processes off
  (`pkill -f qemu-system; pkill -f "Chrome for Testing"; ./gradlew --stop`),
  idle 1-min load < 6, `vm_stat` free+inactive > 2 GB, ONE emulator
  (`--android 1 --ios 1`). Under swap the 50-min section watchdog is too
  tight (vite's per-section dependency re-optimisation alone took 20 min).
  Nine hardening fixes shipped with the retro: adb daemon fd inheritance
  (start the server detached, never `adb devices | while read`); `adb
  install` verified by `pm path` (rc 0 on "Failure"); every `adb devices`
  in provision-devices/section-runner/feeder bounded (`perl -e 'alarm N'` /
  execFileSync timeout) with a detached server restart + retry; the
  feeder's 1 MiB exec buffer → 64 MiB and `logcat -d -t 2000`; ETIMEDOUT
  retried 30→60→120 s; a failed app restart marks the fixture instead of
  FATAL; `WANT_ANDROID`/`WANT_IOS` overridable; a boot poll bounded to 15 s.
  A leftover puppeteer "Chrome for Testing" fleet (19 processes, from lane
  work) can hold GBs — kill it before any gate.
  **All three `provision-devices.sh` budget loops are WALL-CLOCK now** (wave
  50; skeptic S1 found the orchestrator had converted one of three and left
  the other two counting sleeps): the emulator boot poll, the `pm path`
  install verification and the iOS boot poll each bound themselves against
  bash's `SECONDS` (240 s / 120 s / 120 s). A counted-sleep budget silently
  becomes a thirty-minute budget the moment the host is slow enough to
  matter — which is exactly when a gate needs its timeout to be real.
- zsh does NOT word-split unquoted vars — write refeeds as explicit
  per-fixture commands, never `set -- $pair` loops.
- **Limit-killed workflow lanes**: resume with
  `Workflow({scriptPath, resumeFromRunId})` — completed lanes replay from
  cache; add per-lane `model:'opus'` overrides only to dead lanes. A
  resumed lane's first act is `git status`/`git diff` on its OWNED paths:
  the killed attempt may have left partial edits or an un-restored
  mutation (retro R5 found one).
- Kotlin IC-cache corruption (`Storage already registered`, `Page -N`):
  delete `runtimes/compose/build/kotlin`, retry. Gradle filtered-run
  state: final verification always `--rerun-tasks`. Concurrent daemons
  on one tree → AGP jar-scan races: treat as contention first, re-run
  single-writer before diagnosing.
- Suite counts live in doc tables (README/CLAUDE/STATUS + tree READMEs);
  doc-staleness-check enforces them — stamp AFTER the final sweep, and
  re-derive after skeptic/fix lanes add tests. The sweep is: converter,
  web runtime, compose `:runtime:`, **android-harness `:app:testDebugUnitTest`**,
  swiftui (Catalyst), **`npm -w apps/web-harness run test`**, tooling
  (`node --test`, whose count includes skipped tests), IR conformance —
  `doc-staleness-check.sh` derives the two harness counts since the retro
  (A8#4), so the README/CLAUDE/STATUS tables must carry their rows. The
  ios-harness XCTest target (`StyleConverterTestTests`, project.yml) is
  still run by nothing documented — `doc-staleness-check.sh:194-200` now
  WARNS with its declared-test count rather than letting it pass silently,
  and the retro added a file to it, so the count is growing untested. Wire
  it into the tables (it needs a simulator) or delete the target at the P2
  sweep; a warn is a placeholder, not a decision.
- iOS capture knobs travel as `SIMCTL_CHILD_*`; `FORCE_STATE` has **no**
  `CAPTURE_` prefix.
- Fixture authoring: `_expect` with shown arithmetic; solid colours
  ≥3/channel from white and outside ±8/channel of page bg rgb(26,26,46);
  transforms escaping the box need margin; tie-free animation times;
  control fixtures run `NO_CROSS_PLATFORM_GATE=1`; motion fixtures declare
  `_capture.seizeOnly`.
- Wire conventions are PINNED in `schema/spec/02-values.md` (wave 48):
  lab/lch lightness 0..100, **oklab/oklch lightness 0..1**, chroma
  absolute (lch 150 = 100%, oklch 0.4 = 100%). The web typed-original
  re-emission and the converter's percentage scaling are a COUPLED pair
  — reverting either half alone regresses gradient-powerless-hue-oklch.
