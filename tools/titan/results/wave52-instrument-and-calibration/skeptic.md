# wave52 L12 · instrument-and-calibration — executed-repro skeptic

Date 2026-10-05. Tree: shared worktree `trusting-bohr-bd6fbf`, branch `campaign/wave52`, HEAD `7d9c22a7`, other lanes' uncommitted work present.
Inputs read: `PLAN.md` "### L12 ·", §3, §5, §9; `_note.md` (ends `STATUS: COMPLETE`); every file in this directory.
No device, emulator or simulator was used. The only writes were: 5 mutations (each restored byte-exact, sha1 verified), the two orchestrator
hunks applied under the per-file lock and restored to `git show HEAD:` bytes (sha1 verified), and this file. No git state was changed.
Note: my throwaway scripts lived in the session scratchpad, which gets purged. §5 states every predicate they used, so any number below can be re-derived without them.

## 1. Lane completeness
`_note.md` ends with `STATUS: COMPLETE`. `corpus-v6-18.json` is owed after the closing gate, as the plan says, so it is not a gap.

## 2. Ownership (foreign-edit check)
`git status` filtered to L12's paths shows only these changes:
- `tools/titan/{inject-wpt-block,capture-browser-ref}.mjs` and their `.test.mjs` files
- `tools/titan/score-gate.test.mjs`
- `tools/titan/README.md`
- this results directory, plus the gitignored `tools/wpt/refs/…-rootbg-uamargin` tree and `tools/titan/runs/wave52-calib`

All of these are on L12's `own:` list. `score-gate.mjs` equals HEAD, as C2 requires. Every other modified titan file belongs to another lane:
- `view-transition-bake`: L1
- `counter-style-*`: L6
- `post-load-extract`: L11
- `extract-fixture.test`: L5

`wpt-white-canvas.test.mjs` and `noto-pilot.test.mjs` equal HEAD; they are delivered as hunks, not edited. **No foreign edit.**

## 3. Pins re-run
- `node --test tools/titan/{capture-browser-ref,inject-wpt-block,score-gate}.test.mjs`: **176/176 pass, 0 skipped**. The re-freeze pin and the direction pin ran, because the WPT tree is present.
- The test files that consume the shared sheet: safe-name 10, section-runner 19, wpt-not-applicable 265, view-transition-bake 140, post-load-extract 100 (1 env skip), bidi-bake 50, svg-preraster 20. **All green.**

## 4. Mutations executed independently (lock `tools/titan/runs/wave52-lock/<basename>`, restore from saved bytes, sha1 before = after)
| id | mutation | result |
|---|---|---|
| SK1 | composed path calls `computeBlankCaptureVsInkedRef(captureUniform, semanticPresence)`, dropping `diff.refUniform` | **SURVIVED** (exit 0) |
| SK2 | `applyAbsenceOnlyGate` stamps but leaves `wptPass` true | caught by pin 2 |
| SK3 | `cssDeclaresBodyMargin` no longer recurses into `@media/@supports/@layer/@container` | caught by the selector-rule pin |
| SK4 | root-relative `<link href="/…">` resolved against the page directory instead of `WPT_DIR` | **SURVIVED** (exit 0) |
| SK5 | absence-only floor widened from `0.02` to `1` | caught by pin 1, pin 7 and the score-gate replay |

All five restored: inject `086bb1eb…` and capture-browser-ref `07675107…`, the same sha before and after. `shasum -c` over all six L12 files reports OK.

## 5. Census re-derived with my own scripts (manifests + PNG decode, never the lane's JSON)
**Absence-only** (wave51-fix, 30 manifests, 4305 numeric-ssim cells). Predicate: `wptPass===true && bCoveragePct<0.02 && !scoreExcluded`.
- Result: **19 cells / 7 tests (web 7 / iOS 6 / Android 6)**. The names are identical to the note's.
- Totals: web 1215/1379 → **1208/1372**, iOS 1089/1369 → **1083/1363**, Android 1080/1369 → **1074/1363**. These match.

**Uniform colour** (my own pngjs decode of every capture and every old-tree ref):
- **13 uniform refs** and **73 uniform captures**.
- The literal predicate (uniform capture ∧ ref ≥ 0.02) gives **45**.
- The shipped rule (the literal one minus "capture == the ref's uniform fill") gives **39 cells / 16 tests: 33 scored, 6 excluded**.
- **Scored PASS among the 39: 0.** "Scoring already correct on this class" holds.
- Under the re-frozen refs (calib manifests): literal 54, shipped 45, which is 39 + the 6 `calc-in-media-queries` red-vs-green cells.
- These match the note. The plan's 36 is stale.

**Body margin** (shipped predicate plus an independent crude regex). Of 1435 tests:
- testDecl 150, refDecl 149, mismatched 19, **fire 10**, reverse 9.
- The crude regex and the shipped predicate disagree on 0 tests. 0 scheme-href and 0 root-relative stylesheet links occur in the gate's tests and refs.

**Re-frozen tree** (`cmp` of every new-tree PNG against the old tree):
- **23 moved**: the exact list in the note. The other **1412 are byte-identical**.
- The versioned `chrome-151.0.7922.47/` copy equals the legacy view for all 1435.
- The `refreeze-apply.post.json` old and new sha1 match the files on disk for 23/23.

**Calibration** (`wave52-open` → `wave52-calib`):
- **4305/4305 capture PNGs byte-identical** (separate inodes, not links).
- My per-cell compare of ssim, wptPass and scoreExcluded: **4217 unchanged; moved = 19 stamp + 69 on the 23 re-frozen refs + 0 other**.
- `score-gate wave52-open wave52-calib` re-run by me: **gained 13 · lost 8 · newly-measured 0 · unmeasured-now 19**.
- Totals: web 1211/1372 · iOS 1084/1363 · Android 1075/1363.
- The calib inject logs sum to `absence-only=19 blank-captures=45` (30 logs). The manifests carry 19 `'absence-only'` stamps, 45 `blankCaptureVsInkedRef`, and 73 `captureUniform`.
- The ring-fenced `backdrop-filter-basic-blur` does not move, even at a mover threshold of 0.0001.
- The at-risk cells `anchor-position-circular`, `absolute-pos-box-inside-fixed…`, `hypothetical-dynamic-change-003` and `css-tags-paint-order(-with-entry)` are unchanged.

## 6. Orchestrator hunks (`hunk-for-orchestrator-{1,2}.patch`)
- `git apply --check` on HEAD 7d9c22a7: **both OK** (both target files equal HEAD).
- Without the hunks, `wpt-white-canvas` + `noto-pilot` have 5 failures: 4 caused by L12's rev bump and the sheet literal, and 1 swiftui.
- With both applied under the lock, those two files plus the three L12 suites give **209/210**.
  - The single failure is `swiftui: WPTCanvas is white…`. It counts 4 `.background(canvasBackground)` in the working `CaptureCanvas.swift` against 3 at HEAD, which is **L2's in-flight file, not L12**.
- Restored with `git show HEAD:<path> > <path>`. The sha1 values equal the pre-apply values and `git diff --quiet HEAD` passes. The lock was released.

## 7. PNG judgement: wave51-fix captures vs the frozen refs (my own montage: new ref | old ref | web | iOS | Android)
| cell(s) | calib verdict | picture | judgement |
|---|---|---|---|
| `css-cascade/initial-background-color` ×3 | f 0.54 → P 1.0 | new ref is all green (old was white); all 3 captures are all green | honest gain |
| `filter-effects/backdrop-filter-root-element` ×3 | f 0.54 → P 1.0 | new ref is a green canvas with "Test"; captures are the same | honest gain, not predicted |
| `css-gaps/flex/flex-gap-decorations-033` web | f → P 1.0 | new ref has red/blue rules in the gaps; web paints them | honest gain |
| `…-033` iOS/Android | P → f 0.94 | natives paint only the pale container, no rules | honest loss, pre-registered (C1) |
| `…-034`/`…-035` ×3 | f → P 0.987–0.992 | the ref and all 3 captures carry the same rules; the captures overrun the right frame | instrument-only flip; the residual is L2's overrun |
| `…-036/037` ×3 | P, rises to 0.99+ | the rules now match | degenerate → honest |
| `css-display/display-contents-root-background` ×3 | P ~1.0 → f 0.535 | new ref is a green canvas ("Pass if the background is green"); all runtimes paint white | honest loss; **NOT pre-registered** |
| `css-view-transitions/column-span-during-transition-doesnt-skip` ×3 | P 0.988 → f 0.933 | new ref shows the green target over the pink `::view-transition`; captures show green over white | honest loss (L1 F-B is what would pay it back) |
| masking `paddingBox/contentBox-1d/-1e` ×12 | P 0.9585/0.9696 → ≥0.9997 | new ref's green sits at (24,24), the same as all captures; old ref was at (16,16) | degenerate → honest |
| `CSS2/…/s-11-1-1b-005` web | P 0.9947 → P 0.9658 | new ref text is at x+8 with the black square at (24,56); **the web capture has no black square** | stays P but is a degenerate pass (the note says so) |
| `hanging-punctuation-block-bound-001`, `calc-in-media-queries` ×6, `position-*-root-element-*` ×12, `display-contents-sharing-001` ×3 | f stays f | the new refs show the hanging 。, a green canvas (captures red), the dashed viewport border, and the injected-sheet text respectively | honest f; sharing-001's ref pollution is a pre-existing instrument finding |

## 8. Other rules
- **Ring-fence:** no added code line names `backdrop-filter-basic-blur`, and it does not move. **No test-name carve-out:** no added non-comment line names a test. The predicate is mechanism-free.
- **New files:** all ≤ 200 lines (the largest is 129). L12 stages no device A/B, so the hash sentence does not apply.
- **Hand-off to L6:** the `:1308 → :1326` pointer checks out: `NATIVE_FONT_PARITY_REFUSED_TESTS` is at `:1326` now.
- **Side-path check (not stated by the lane):** `view-transition-bake.mjs` also injects `canvasFrameCss()` into the TEST page and samples pixels through `frameRingColor`, which stamps a body-root `background-color`.
  - My census of the 48 gate VT tests found 0 with an `html`/`:root` background rule, 0 with a negative z-index, and 0 with an `<html style>`. Its isolation backdrop is the top-layer `::view-transition`.
  - Gate exposure to the body-background removal is therefore **0**. The comment "The two TEST-page bake paths … snapshot GEOMETRY" is still inaccurate (see D2).
- `post-load-extract` never records `<body>` itself (only `bodyStyles.color`), so the body background never reaches the IR there.

## 9. Defects
- **D1 (should-fix):** SK1 survived. Nothing pins the `refUniform` → `computeBlankCaptureVsInkedRef` wiring on either diff path.
  - Pin 6 (wiring) uses a structured ref, and pin 6b exercises the pure function only.
  - If this regresses on the re-frozen refs, the gate log prints `blank-captures=54`, not 45. The 3 scored **PASS** cells `initial-background-color` ×3, plus the olive and green full-bleed agreements, would carry `blankCaptureVsInkedRef:true`.
  - This is triage only; scoring is unaffected.
- **D2 (should-fix):** the comment in `capture-browser-ref.mjs` ("The two TEST-page bake paths inject this same sheet; they snapshot GEOMETRY, which a background never moves") and the note both omit `view-transition-bake.mjs`, which samples pixels.
  - The exposure is 0 by my census, so the blast radius is not wrong. It is under-stated.
- **D3 (should-fix, orchestrator):** the closing-gate oracle in PLAN §1/§7 is falsified by this lane's measurement, and the lane flagged it. Until it is amended, the gate reads as unexplained.
  - "lost = exactly 2" is now 8 (5 if L1 F-B lands).
  - The +13 instrument gains (034/035 ×3, backdrop-filter-root-element ×3, initial-bg ×3, 033 web) move out of the L2/L10 columns.
  - `blank-captures=36` is now 45.
  - The 21 `watchlist-additions.txt` lines are not yet merged into `wave52-plan/watchlist.txt` (they check `unmatched 0` on their own).
- **D4 (should-fix):** this directory is untracked (`git ls-files` gives 0). Inject pin 7 and the score-gate replay pin are both `skip: !exists(absence-only-digest.wave51-fix.json)`.
  - If the digest does not ship in the same commit as the code, both replay pins skip silently in CI.
- **D5 (nit):** SK4 survived. Root-relative linked sheets are unpinned.
  - `pageDeclaresBodyMargin` skips scheme hrefs (remote and `data:`) silently, although its doc comment says "remote or missing sheets are skipped and reported on stderr — never silently counted".
  - Corpus exposure is 0.
- **D6 (nit):** the every-line-comment house rule is not met in `pageDeclaresBodyMargin`/`bodyDeclaresMargin` (about 10 bare statements). The evidence scripts are about 25 % comment lines.
- **D7 (nit):** the banner in `inject-wpt-block.test.mjs` cites `_note.md §"Mutations"` and calls the font-gate-order mutation "M7". The ledger (`mutations.json`) names it M7a; its M7 is the `score-gate.mjs` `isScored` mutation.
- **D8 (nit):** `runs/wave52-calib/calib-logs/_status` lists 29 sections. `css-masking` ran separately, at 17:48:59 (one minute before the batch). Its log and manifest are present and consistent, but "30/30 rc 0" cannot be read off `_status`.

## Verdict
**L12 is sound and may land.** I re-derived every headline number with my own scripts and found no difference. Those numbers are:
- 19 absence-only cells and the totals;
- 13 uniform refs, 73 uniform captures, 39/45 blank captures;
- 150/149/19/10 body-margin pairs;
- 23 refs moved, 1412 byte-identical;
- 4305 identical captures, 4217 unchanged cells;
- 13/8/0/19 on the score-gate re-run.

Every predicted flip opens to the picture the lane describes. The hunks apply and turn their suites green, apart from L2's own failure.

There are no must-fix defects. D1 and D4 should be closed in L12's PR: add one wiring pin with a uniform ref, and commit the digest beside the code. D3 is the orchestrator's amendment to PLAN §1/§7 and the watchlist, and it must happen before the closing gate.
