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
per wave, `_note` carries the full story). Current: **corpus-v6.15**
(wave 49 — web 1205/1379 87.4%, iOS 1081/1366 79.1%, Android 1058/1366
77.5%; 32 cells gained, zero lost, 4111 scored cells with exact column
parity — the zero-lost figure is the per-cell diff wave48-final →
wave49-final, the method the standing rule below now mandates). All deltas
attribute against the most recent snapshot, never across the PR-#126
instrument change.
**The retrospective's device gate did NOT complete** (2026-09-05 → 09-07,
17 attempts). Every attempt failed on a different symptom of one cause —
the host had no memory headroom (15 GB used, ≤230 MB free, 5–6 GB in the
compressor, load 13–25 with the user's browsers open): adb-server wedges,
`adb install` "success" with no package, feeder timeouts, a wedged
fixture whose `am force-stop` could not finish in 210 s, vite dependency
re-optimisation taking 20 min, and finally a section overrunning the 50-min
watchdog. The one section that completed (attempt 9, css-position, at host
load 9) was clean: 48/48/48 with the Android column present. **Current
therefore stays corpus-v6.15**; the retro's rendering changes (R1–R6,
F1–F3) are corpus-UNMEASURED on device and wave 50's opening gate is their
measurement (see obligation #1). Nine harness defects found by those
attempts are fixed in this PR (Operational recipes → "Gate on a quiet
host"), so the next gate fails loudly and early, never silently.

This revision is the **2026-09-04 retrospective refill** (12 audit lanes
A1–A12 over waves 1–49, 195 executed-evidence findings — 28 must-fix,
114 should-fix, 53 notes; fix lanes R1–R13 + sweeps P2a–P2e), re-checked
2026-09-05 against the INTEGRATED tree after six executed-repro skeptics
(S1–S6: 49 defects, 3 must-fix) and the round-2 fix lanes. Every entry
below that says "retro Rn" names the lane whose change is in the retro
PR; "pending the device gate" means the code is in the tree and its
predicted cell flips are to be confirmed by the retro gate (corpus-v6.16)
or, failing that, the wave-50 gate. Every "landed"/"not landed" sentence
was re-verified against the final tree on 2026-09-05 (S6 found six that
the lane-time verification had left stale — the ship-time rule in the
standing constraints below is the consequence).

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
  nothing ran, so exit 5 could never reach them.
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

## Next-wave obligations (wave 50 opens with these)

1. **Deltas attribute against corpus-v6.15** (the wave-49 gate). The retro
   gate was not run (host starvation, see the Current: block); **wave 50
   opens with the full gate on a QUIET host** (idle load < 6 with all of our
   processes stopped; one emulator; browsers closed) — it doubles as the
   retro's measurement, so score it FIRST as "retro-final" (per-cell diff vs
   wave49-final, snapshot corpus-v6.16, `_note` = the retro story) and only
   then run wave-50 lanes. **First act: VERIFY,
   do not re-fix, `tools/titan/results/corpus-v6-15.json`** — its
   `artifact` is `tools/titan/runs/wave49-final (gitignored)` and its
   `reproduce` recipe says `--run-id wave49-final` (A2#0/A1#4 was a
   copy-forward error naming `wave48-final`; retro R11 corrected both
   fields; re-read 2026-09-05 and correct as they stand). An opener that
   "fixes" them again would be editing a correct snapshot. The retro's
   rendering changes (R1 transforms, R2 flex/sizing, R3 typography, R4 iOS
   borders/clip, R5 iOS fallbacks, R6 effects, R10/R13 instrument+ledger)
   predict cell flips listed per item below; the retro gate is their
   confirmation, and refreshes the fixture baselines they move
   (`UPDATE_BASELINE=1` only after LOOKING at every changed PNG). **R4's
   iOS clip fix moves every committed iOS baseline carrying a rounded box
   with content past the curve** — 12 ledger lines are pre-marked EXPECTED
   STALE for it and for the Android blur pair (obligation #7), so budget an
   exit-5 pass whose only action is deleting them — AND an exit-1 pass on
   the iOS column that is expected, not a regression (obligation #7 names
   the components).
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
3. **The pass column is ~35% degenerate and that is the campaign's biggest
   known problem — and its per-cell evidence is LOST.** Wave-49 lane I1
   opened 78 randomly-drawn PASSING cells by eye and called 27 visibly
   wrong (wrong colour, wrong position, wrong line breaking, missing glyphs
   — "PASS" rendered "ASS", "A. B. C." rendered "0. 1. 2."). The verdict
   file lived only in that session's scratchpad and exists nowhere on the
   host or in git (A10#0, A2#1: `find` over 18 session dirs and `git
   ls-files` both empty). **The 27/78 figure is therefore unverifiable; the
   successor instrument lane's FIRST act is to re-draw a random sample of
   passing cells, verify by eye, and COMMIT the verdicts under
   `tools/titan/results/wave50-<lane>/handverdicts.json` before any
   instrument design.** The red-square class is one visible slice, now by
   the committed predicate `node tools/titan/red-square-census.mjs
   wave49-final` (strict red r>200&&g<80&&b<80, capture >0.5%, ref 0%;
   retro R8b, A1#3): **179 passing red cells (web 23 / iOS 66 / Android
   90, 105 tests) and 87 failing (15/35/37, 51 tests)**. The wave-49 PR's
   "179 (26/67/86, 102 tests) / 89 (13/37/39, 53 tests)" does not
   reproduce from any red definition and its predicate was never
   committed; the census script is the number of record. A ready-made
   instance of the class outside red squares: all 8
   `CSS2/floats-clear/floats-clear-multicol-*` cells PASS (0.984–1.000 on
   every platform) while the per-test IR carries ZERO `Clear` wires for a
   source with `<br clear="all">` (queue 3a). **No colour-based check
   reaches the rest**, so the next instrument step is NOT another colour
   veto.
4. **The novel-ink veto is SHIPPED DISARMED** (`TITAN_NOVEL_INK_VETO=1`
   to arm; stamped on every diff for triage regardless). It missed its
   pre-registered 95% recall bar — 66.7% on the census-selected defect
   set, **16.7% on an unbiased sample**. Do NOT arm it by lowering the
   threshold. The two measured miss mechanisms are (a) wrong-colour-AND-
   displaced renders, whose divergent region fills with the reference's
   own palette, and (b) small marks that clear the fraction bar but not
   the mass bar. The named successor is a **displacement-aware
   denominator** (exclude divergent pixels explained by a rigid
   translation of reference ink before computing the ratio) — a
   redesign needing its own pre-registered decision rule, and a fresh
   hand-verified defect set NOT selected by any rule correlated with the
   check's own bars (obligation #3's committed sample is that set). The 13
   extraction-wall tests whose raw metrics pass on all three
   (`tools/titan/results/retro-2026-09-04/excl-detail-wave49-final.out`)
   are a calibration set for it — A12#9 opened them and found mostly the
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
   into exit 4), never a silent extension. Four lines are scoped to
   `composition-test.json` — a fixture the OLD single-fixture gate never
   ran, so they could not go stale at all; the two `filter-sepia-amounts.json`
   lines were in the same position until the retro looked at them and
   deleted them. `--gate-set` (standing constraint above) is what makes
   exit 5 able to reach fixture-scoped lines, and the Sepia adjudication
   was the first result of looking.

## Decided this retrospective — the first PRs of wave 50, in this order

These are their OWN PRs because each moves every committed baseline of a
platform; they are not folded into a rendering wave.

- **(A) Harness debug-label chrome drawn OUTSIDE the paint chain on all
  three platforms** (A11#8). The shared spec promises byte-identical label
  rects at (8,6), but today the label is composited by the component:
  iOS draws it INSIDE the element's `.blendMode` group
  (`runtimes/swiftui/.../Renderer/ComponentRenderer.swift:4065`) —
  blend-isolation `003_wrapper` iOS (97,193,242) == 0.3·bg + 0.7·(237·bg/255)
  exactly, Android/web (196,227,242) plain src-over → SSIM 0.9492, exit 4;
  Android places `BlockLabelPlaceholder` inside the content slot
  (`runtimes/compose/.../core/renderer/ComponentRenderer.kt:6191`, the
  `BlockLabelPlaceholder(displayText)` call in the block-font label branch;
  line re-resolved 2026-09-05), so `align-items`/`text-align` move it
  (Layout_C01_AlignContent bottom-RIGHT, Layout_TextBlock right of the box
  0.89); web's zero-footprint absolute svg with `componentWidth ?? Infinity`
  (`apps/web-harness/src/sdui/ComponentRenderer.tsx:1343`, the
  `layoutBlockLabel(...)` call) spills past
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
  `align-items:end` HORIZONTALLY to a non-flex Box
  (`ComponentRenderer.kt:2845` `contentAlignment =
  displayConfig.alignItems.toBoxAlignment()` — cite the call site by that
  expression; the line moved 2820→2845 between R12 and ship) — queue 9(g).
- **(B) Drop the web-harness `width: fit-content` initialiser for
  flex/grid items whose cross axis is `auto`** (A11#12; the initialiser is
  the unconditional `width: 'fit-content'` at
  `apps/web-harness/src/sdui/ComponentRenderer.tsx:512`, rationale at
  :417-476 (re-resolved 2026-09-05) — note it is ALREADY skipped for `aspect-ratio` and under
  `WPT_MODE`, so a third exemption is the established shape, not a new
  mechanism). `align-self: stretch` / default stretch
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

## Ranked queue (wave 50+)

0. **Wave-49 discoveries + retro corrections, ranked above the older
   queue:**
   (a) **`tools/titan/extract-fixture.mjs` collapses competing
   declarations last-wins with no validity oracle** (`props[k] = v` at
   line 830, re-verified 2026-09-04). This is why
   `css-values/angle-units-001` cannot be fixed in the converter: all five
   competing `background-image` declarations are gone before conversion,
   so the four INVALID ones cannot be dropped in favour of the valid green
   one (wave49-final css-values/angle-units-001: web f 0.9990 cF · iOS f
   0.9974 cF · android f 0.9966 cF). Wave-49 lane A1 proved it and refused
   to claim the flip; **its prepared patch (+148) and test are LOST with
   the session scratchpad (A10#0) — the fix must be re-derived**: a
   per-declaration validity oracle at the seam, taken only with a
   differential re-extraction over the full corpus (corpus-wide
   capture-pipeline blast radius for 3 cells; the file is shared with the
   hr-100×100 item 5a). Commit the patch under
   `tools/titan/results/wave50-<lane>/` if it is deferred again.
   (b) **Compose `LocalContainingBlock` should republish for
   non-block-container ancestors** (CSS 2.1 §10.1). `PercentInsetResolve`
   correctly declines when both axes are null because it cannot tell
   CSS-indefinite from a channel gap; the iOS twin flips because
   SwiftUI's `LayoutAggregate` publishes one. Blocks
   `css-position/position-relative-006` (wave49-final android f 0.9966 cF;
   web/iOS P) — the wave's only prediction miss — and any percentage inset
   under such an ancestor. TODO is in place at `PercentInsetResolve.kt`.
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
   (d) `clip-path-contentBox-1d/1e` still fail on Android (wave49-final
   0.8775 / 0.8875 cF cvF; 1d web/iOS P 0.9585/0.9585, 1e web/iOS P
   0.9696/0.9695 — per-cell values re-read 2026-09-05) despite the wave-49
   extractor repair — the crash is fixed and the centre decodes correctly,
   but the rendered shape still diverges from the browser ref.
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
   against the cached ED tables of contents): `PseudoBucketExtractor.kt:115,142`
   cite css-display-3 §2.5 for `display:none` suppression — §2.5 IS "Box
   Generation: the none and contents keywords" (correct, as A10 suspected);
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
   leaves is this item, and the durable fix is a cite-validate check in
   the doc-staleness job (S6's is ~40 lines) so the sweep cannot regress.
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
   v6-14 note by the docs lane). (b) `css-writing-modes/direction-upright-002`
   is an **ALL-THREE failure, not Android-only** (A10#3): wave49-final
   frames vs the 390×954 ref — web 390×**2805** f 0.5826, iOS 390×2544 f
   0.5758, Android 390×2316 f 0.5946. The reference-grade web runtime blows
   the canvas up 2.9×, so the mechanism is shared (extractor/bake or the
   upright text-flow model) and an Android-only brief cannot find it.
   **First diagnostic: diff the web composed DOM against the ref's layout
   (why 2805px?) before touching either native.** (c) **`css-text-decor/
   text-decoration-inset-025`** — Android composes 9470px vs web 750 /
   iOS 600 (wave49-final android f 0.0000 cvF, frames [390,9470] vs ref
   [390,1230]), the corpus's second true degenerate-canvas positive
   (wave-48 S6 IHDR scan of all 4304 captures); undiagnosed Android layout
   blow-up.
2. **Counter/marker follow-ups from the Rule-43 unexclusion** (W2 wave 48
   unexcluded 15 of 28; the refusal list in `tools/titan/inject-wpt-block.mjs`
   `NATIVE_FONT_PARITY_REFUSED_TESTS` carries per-key measured scores):
   (a) **Android blank-paint bug**: `counter-cjk-decimal` renders ZERO
   ink from IR iOS paints perfectly (wave49-final android f 0.9407 pF cvF
   vs iOS P 0.9934) — a real generated-content drop, now an honest scored
   fail dragging the column; (b) **both-native zero-width wrap**:
   `css-lists/counter-004` lays the georgian counter string one glyph per
   line on BOTH natives (0.7532/0.7531, canvas 1784px tall vs ref 600; web
   fails separately 0.7779); (c) ~~adjudicate the three self-declared
   re-label candidates~~ **DECIDED (A12#5, retro R13 — pending in the retro
   PR)**: `armenian/css3-counter-styles-008`, `counter-suffix` and
   `css-lists/counter-004` leave the refusal list — the PNGs show non-font
   defects (armenian-008: both natives print "10000. 10000" on ONE line
   where the ref wraps the §7.1.4 fallback row; counter-suffix: every
   item's text painted twice "foo / foo" + Korean and RTL marker rows
   missing on ALL THREE; counter-004: the zero-width wrap above). Accept
   the 6 honest native fails (armenian 0.9420/0.9423, counter-suffix
   0.8883/0.8901, counter-004 as in b). The other 10 keys stay
   (cjk-decimal-001 and arabic-indic-102 spot-checked: same glyph rows,
   plausibly face-bound). Queued from it: **natives — zero-width wrap of
   long non-Latin counter strings** (b), **natives — armenian fallback-row
   line breaking**, **shared — counter-suffix doubled item text + missing
   korean/RTL markers** (an extraction/marker-channel defect, all three
   platforms; also `name-case-sensitivity` missing marker content on all
   three, wave49-final f 0.9593/0.9535/0.9542 cvF).
3. **Multicol float residuals** (W3 wave 48 closed seam 2 — the iOS
   slice-replay renders ref-exact rows): (a) the converter/inline bake
   **drops `<br clear="all">`** — per-test IR for
   floats-clear-multicol-000/001/balancing-000/001 has no Clear wire, so
   both natives put the orange at col-0 vs the ref's col-2 and PASS ONLY
   on float-slice dominance (all 8 cells P 0.984–1.000 — the
   degenerate-pass instance named in obligation #3); (b) Compose renders
   the 003/balancing-003 orange 3px high (y158-160 vs ref 161-163; iOS is
   now ref-exact; wave49-final android P 0.9881/0.9857) — measured-height
   rounding in the Kotlin strip walk.
4. **Inline-run wall, corrected map** (wave-48 W4 measured the briefed
   rings as no-ops: the br ring shipped in wave 47; the 75 tagless
   members are correctly-stacked BLOCK boxes. The UA-styled ring —
   i/em/cite/var/dfn + sup/sub with the Blink parent/3+1 / parent/5+1
   shift, twin `InlineSpanRing.shiftPx` helper, pinned both platforms —
   shipped instead). Remaining, in value order: (a) the
   **br-stacked-equivalent wall** — lifting it flips 29 hosts across 27
   tests, ~16 currently passing: needs a device A/B, never a blind lift;
   (b) `subelements-003` (wave49-final iOS f 0.9043 / android f 0.9084):
   needs block-level text-decoration propagation (the wrapper's blue
   underline is missing from EVERY render) AND per-range decoration colour
   diverging from text ink — both outside the fold; (b′) **the
   underline-inset wall — the wave-48 prediction misses were untracked**
   (A10#9): `text-decoration-subelements-002` iOS f 0.7974 / android f
   0.9040 (Android improved 0.765→0.904 in wave 48 without flipping),
   `text-decoration-inset-005` iOS 0.8995 / android 0.9000,
   `text-decoration-inset-006` 0.8987 / 0.8993 (web P on all three; flat
   since wave48-final). corpus-v6-14: "the underline-inset wall dominates;
   the UA-styled ring is correct but insufficient"; (c) `block-ellipsis-032`
   (android f 0.9451): hanging-whitespace ring (WhiteSpace:PRE members
   through the pre-break); (d) Compose line-clamp-006/007 (android f
   0.9446/0.9409; iOS P 0.9822): missing cross-block line-box census (the
   wave-46 iOS twin has it) — retro R3 closed the sibling gap that Compose
   ignored the `ellipsis` marker component (`LineClampWire`,
   LineClampMarkerSuppressionTest; zero pixel movers today); (e) Compose
   baseline-shift is face-ascent-dependent (hard-coded Inter 0.96875) while
   iOS is exact points — watch sup/sub cells on device for the ~2px class
   (named limitation in `InlineSpanContent.kt`).
5. **Web tail residuals** (W5 wave 48 measured +14 web: contain-body ×8,
   lch/oklch %, degenerate calc, image(), contain-intrinsic bridge):
   (a) `tools/titan/extract-fixture.mjs` stamps 100×100 on `<hr>`,
   spreading gradient-hue-direction off-canvas (wave49-final all-three
   0.6241/0.6235/0.6230, canvas 894 vs ref 600); (b) `none` colour
   components need an IR wire shape (gradient-none-interpolation,
   all-three 0.8199/0.8125/0.8125 cF); (c) gradient-eval-predefined-
   color-spaces: script-templated test extracts zero styled components
   (0.9247 cF pF cvF ×3); (d) fallbacks-005 is UNPASSABLE AS SCORED (five
   tests share one solid-green ref whose prose is 001's — WPT authoring
   artifact; exclusion-lane decision, never a code carve-out; web honest
   loss 0.9144→0.9038 recorded); (e) native powerless-hue/lch gradients
   (0.65–0.86) need polar interpolation with alpha-carrying stops; (f)
   color-mix in lch needs a static mixer (srgb→lch in ColorConversion);
   (g) contain-content-004 / contain-html-overflow-002: all three paint
   blue-FILLED blocks where the ref wants blue-BORDERED hollow cells
   (0.8448/0.7940/0.8286 and 0.8709/0.8705/0.8698, cF cvF); (h) ~~native
   image() carries only the FIRST src~~ FIXED wave 49 (A3,
   `ImageCandidateChain` both natives; fallbacks-and-annotations
   002/003/004 flipped on both, 6 cells; 004's winning `1x1-green.gif` has
   GCT (0,127,0) vs ref (0,128,0) — CONFIRMED by GCT byte read, one step
   off, still passing; 005 remains 5d); (i) **mask-image wire gaps**
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
   49 — clip-path-blending-offset Android 0.9566→1.0000. (c) Android
   box-shadow ~2.4× over-blur (`MultipleShadowApplier.kt`). (c′) **FIXED
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
   move toward iOS (0.9855). (d) iOS overflow clip applied OUTSIDE the
   transform — confirmed structurally (A10): `StyleBuilder.swift`
   `.engineTransforms(style.transforms)` (:1329) is wrapped by
   `.engineVisibility(style.visibility)` (:1337), the overflow-clip carrier
   (lines re-resolved 2026-09-05; cite the two modifier calls by name). (e) **Out-of-flow rule-table parity: iOS
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
7. **css-gaps residuals** (W7 wave 48 built column-wrap on both natives +
   iOS percent flex-basis; gate arbitrates 025/043/045/046 — wave49-final:
   025 P ×3, 043 P ×3, 045 web P 1.0 / natives P 0.9549, 046 P
   1.0/0.9951/0.9908): (a) the **gap-decoration line-band painter** models
   lines as the UNION of item rects, so §9.4-stretched lines paint rules
   ~3.5px off — the named next step if 045/046 slip under 0.95 (thread
   renderer-computed line bands into GapDecorationSegments on both
   platforms); (b) `background-clip-content-box-002` iOS 0.9992 cF F: a
   decoded `.percent` flex-basis no nowrap path reads — likely free fix
   when CSSFlexLayout honours percent; (c) iOS column-wrap stretch
   injection (no corpus carrier yet); (d) wrap-reverse ordering both
   natives (pre-existing TODO, now load-bearing for the routing gates);
   (e) css-gaps distribution defects: 006 natives f 0.8266/0.8223 (web P);
   **untracked until the retro (A10#10)**: 027 all-three f
   0.9012/0.9030/0.9085, 034 f 0.9458/0.9466/0.9466, 035 f
   0.9206/0.9220/0.9220, 033 web f 0.9400 (natives P 1.0); (f) **Compose
   bare-number `FlexBasis` wire read as PERCENT — PORTED retro R2 (A7#0)**:
   the Swift rule (`FlexboxExtractor.swift:238-240`, bare `.double/.int` →
   percent) now has its Compose twin in `ItemPlacementExtractor` +
   `FlexPercentBasisTest`; pending the gate.
8. Vertical mechanism completion: multi-child balancing, clone-under-
   vertical, §8.3.1 horizontal collapse (wave-47 Z2 logged bails); the
   upright intrinsic Int-overflow at ~2.1e9px summed advances (wave-48
   S4 probe; coerceAtMost candidate).
9. Smaller: (a) `visibility: collapse|hidden` semantics on both natives;
   (b) `text-decoration` shorthand colour-function drop; (c) ~~the web test
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
   (f) **Android placeholder-floor under-size** (A10#8, A12#8): the
   committed baselines read Android 390×58 vs iOS/web 390×62 for
   `Button_Outline` and `Edge_DeepNesting`, 390×60 vs 62 for `Input_Field`
   (`StyleApplier.kt:890 placeholderFloorMinSize` → `:933
   borderBoxFloorMins` — lines re-resolved 2026-09-05; the two function
   names are the pointer — which converts web's border-box `min-height: 30px`
   into a Compose content-box `defaultMinSize` by subtracting padding +
   band); six ledger lines call it "REAL BUG (Android)" and expire
   2026-09-30 (obligation #7). The ledger's own re-diagnosis narrows it:
   with real text pinned (`fixtures/visual-test-controls.json`) removing the
   border moves the Android box by EXACTLY 2× its width, and `Input_Field`'s
   explicit `width: 200px` is exact on all three — so only the
   unconstrained, floor-driven height is wrong. Harness scaffolding, but
   ledgered as a bug — fix with a measured before/after (STATUS caveat: the
   same arithmetic regressed twice, waves 1 and 4); (g) Android `align-items` applied as Box
   `contentAlignment` on NON-flex containers (`ComponentRenderer.kt:2845`,
   the `displayConfig.alignItems.toBoxAlignment()` call site;
   A11#8 — Layout_C01_AlignContent label lands bottom-right); (h) **iOS
   `calc(var(--u)*10)` standalone** resolves to 50px where web/Android give
   `auto` (358) — `fixtures/fidelity/tokens/calc-units.json` 008_tokenMath
   (A11#13; align the calc(var()) fallback semantics across twins); (i)
   **iOS `offset-rotate: reverse`** sign — `layout.combos` 013 (`offset-path:
   ray(45deg)`) iOS 0.93 vs Android==web 0.9982 (A11#13); (j) **Compose
   `offset-path` shapes other than `ray()`** are dropped SILENTLY
   (`StyleApplier.kt:565-567`, re-resolved 2026-09-05 — the motion-path
   block guards on
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
11. **Untracked native/web fails with named mechanisms** (A10#10 — named
    as deferred in corpus notes or commit bodies, never queued; all
    wave49-final, flat since wave47-final unless noted):
    (a) `css-lists/counter-list-item` iOS f 0.7518 / android f 0.7407
    (canvas 1990 vs ref 1028) — "Android missing ::after + spacing
    dominate" (corpus-v6-9); (b) `css-flexbox/align-items-007` natives f
    0.9974/0.9966 cF (web P 0.9990) — the wave-44 honest loss with a real
    colour divergence the flip's skeptic flagged; (c)
    `counter-style-at-rule/broken-symbols` now fails on BOTH natives (iOS
    0.9486, Android 0.9490; wave 41 recorded only the Android −1); (d)
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
  census corrected to match). Remaining, each still needing WIRE-or-DELETE
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
- **Gate script** (the ENOSPC-mid-gate recipe: df preflight ≥10G or abort
  and the <8G watchdog — now enforced by `section-runner.sh` itself,
  obligation #6; the APFS `tmutil thinlocalsnapshots` pruning step is
  there too), kill all emulators +
  extra sims first (`provision-devices.sh` now reuses Shutdown
  `titan-pool-*` sims instead of creating new ones every run, and only
  kills emulators it launched unless `--restart-fleet`; retro A9#6),
  clear `/tmp/titan-device-pool/provisioned-*`, provision via
  `tools/titan/provision-devices.sh` (it `pm clear`s after install —
  API-36.1 reinstalls break the app's external dir without it), 30
  sections × `--max-tests 48 --run-id waveNN-final`, 50-min watchdog that
  kills AND reprovisions, then **`BASELINE=1 ./test-all.sh --gate-set`**
  (retro A12#3: one child run per line of `tools/visual/gate-fixtures.txt`
  — visual-test, composition-test, filter-sepia-amounts, nested-transforms,
  radius-overflow-transform, blend-isolation, opacity-blend — so every
  fixture-scoped ledger line and every `_expect.waive` is actually
  exercised and CAN go stale; a fixture with no committed baselines runs
  gate-only until `UPDATE_BASELINE=1 ./test-all.sh <fixture>` seeds it; the
  first non-zero child exit is re-raised, exit 7 = a missing/short column;
  set `ANDROID_SERIAL` when more than one device is attached or the run
  refuses with exit 2). Score with the standard idiom (`x.ssim` +
  `!x.scoreExcluded`, pass = `wptPass===true`); verify CAPTURE COUNTS per
  column against tests.list, never process liveness; derive "lost" from
  the per-cell diff of the two run dirs; open the PNG of every headline
  cell.
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
