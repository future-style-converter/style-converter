# Wave 54 · S1 fix pass: verifier

The verifier re-checks the four S1 fixers by executing their checks again:
- the plan / docs / records fixer (`wave54-S1/fix/_note.md`);
- L6 web-tail (`wave54-web-tail/_note.md` § "S1 fix pass");
- L2 table-body-cell (`wave54-table-body-cell/_note.md` § "S1 fix pass");
- L1 rtl-marker-bake (`wave54-rtl-marker-bake/_note.md` § "S1 fix pass").

The tree is `campaign/applier-campaign` at `4f3853d7`, with the fix pass uncommitted.

**What this pass did and did not touch.**
- **This file is the only change in the shared tree.** The 72 fix-pass paths (19 tracked + 53 untracked) were hashed with
  sha256 before and after this pass, and the two hash lists are byte-identical. `git status --short` shows the same 26
  entries, `git stash list` is empty and HEAD is still `4f3853d7`.
- **Every mutation ran in a scratch export** (`<scratchpad>/v/exp`):
  - it is `git archive 4f3853d7`, overlaid with the 72 working-tree paths (`shasum -c` against the snapshot: equal);
  - the staged deletion is applied;
  - the gitignored inputs (`node_modules` ×3, `tools/wpt`, `fixtures/wpt`, `tools/titan/runs`, `wpt-buckets.json`,
    `local.properties`) are symlinked.
- **Runners copied, never edited in the tree.**
  - The L6 runner hard-codes `ROOT` to the shared tree, so it ran from a copy re-pointed at the export.
  - The L2 runner's export copy gained `--no-daemon -Pkotlin.compiler.execution.strategy=in-process`. That changes only
    how the runner is invoked. No Gradle or Kotlin daemon is left behind (`pgrep`: none).
- **Revert checks** ran in a scratch `git clone --shared` (`<scratchpad>/v/clone`), which only reads the source repo.
- **Not used:** any device, emulator, simulator or Chromium; any commit, checkout, stash or reset in the tree.
- **One side effect.** Vitest in the export wrote its result cache through the symlinked, gitignored `node_modules/.vite`
  of the shared tree. This is cache only.

The scratchpad is purged after about 3 days, so the outputs that matter are quoted below.

## Verdict: MIXED. Every fixer claim holds by execution. One must-fix: how the fixes are committed, before stage 1.

## (a) Every new pin goes red under its recorded mutation, and the restore is byte-exact

| fixer | runner, as replayed | result |
|---|---|---|
| L1 S6 (10) | `s1-fix/mutate.py s1-fix/mutations-S6.json` run in the export | all `OK`; rc 0. `mutations-S6.result.json` equals the fixer's recorded JSON (`True 10 10`). The four files' sha256 before and after are equal (`9a7850a3…` `c8e530bd…` `4d9e5359…` `f9fae712…`). The export baseline is `# tests 74 # pass 74 # skipped 0` |
| L6 S3 / S4 (4) | `mutate.py s1-fix/s1-fix-mutations.json S1-m{1..4} -- 'cd runtimes/web && npx vitest run tests/renderer/'` | each is `mutated run exit 1 → RED`, then `restored sha256 … == before`, then `restored run exit 0 → GREEN`, and `mutate.py exit 0`. The log equals `s1-fix/mutations.out.txt` with timings removed. Mutated sha256 values are `4005b9c1` / `e2db5215` / `a366948a` / `8a830589`, the same as the record |
| L2 S5 + nit 2 (5) | `s1fix-mutate.py verify GREEN X3 X6 X7 X8 X9 GREEN` (Gradle `--rerun`, fresh JUnit XML) | every mutation turns exactly the intended pin red, with errs `[]` and every restore byte-exact. The export files' sha256 values (TableCellHug `fac6c374`, TableApplier `0d58a463`, test `ea5e7a94`) are equal after the run. Detail below this table |
| plan: probe teeth (2) | `wave54-S1/fix/mutate-probes.py` | baseline GREEN. Disabling the rtl check gives `RED {'rtl:web': 'OK'}`, and disabling the lists check gives `RED {'lists:web': 'OK', 'lists:ios': 'OK'}`. Both restores are `BYTE-EXACT` (`01da3224…`, `fd8004a4…`), and both re-runs are GREEN. The output is byte-identical to `mutations.probes.out.txt` |
| plan: honesty pin (2) | `wave54-S1/fix/mutate-planbuild.py` | MUT-a: rc 1, `…omits it: ['…semi-replaced-stretch-other.html web']`. MUT-b: rc 1, `…names a cell with no prediction row: ['…scope-pseudo-element.html ios']`. Both restores are BYTE-EXACT (`abac114a…`), and the re-runs give rc 0 with outputs equal to the tree's. The output is byte-identical to `mutations.plan-build.out.txt` |

The L2 replay, test by test:

| mutation | red test | failure message |
|---|---|---|
| X3 | `hugColumnSource_…` | "the enabled hug does not read max-content" |
| X6 | `applierSource_…` | "lacks a link: then=-1 hug=2582 fill=2657" |
| X7 | `applierSource_…` | "then=2431 hug=2618 fill=-1" |
| X8 | `hugColumnSource_…` | "hugColumn reads min-content" |
| X9 | `hugColumnSource_…` | "the read is not tagged CELL_HUG_REFUSAL" |

Both GREEN runs gave rc 0 with 16 tests.

## (b) The geometry probes

**The L1 skeptic's fakes** (`skeptic/geom-fakes.py`, replayed in the export). The output is byte-identical to the fixer's
`geom-fakes.post-fix.out.txt`. Its diff against S1's pre-fix `geom-fakes.replay.out.txt` is exactly 3 lines, the three
nodot lines that used to print OK:
- rtl web: `marker left x138 vs ref x133±2 (the "." run missing or moved)`;
- lists web: the same message;
- lists iOS: the same message.

The no-dot fake is now **WRONG** wherever it used to print OK. Every ref line prints **OK**, the ctl web and iOS lines
stay **OK**, and nodigit is unchanged.

**Real runs, HEAD probes against the fixed probes** (HEAD copies taken with `git show 4f3853d7:`), over the six recorded
invocations:
- rtl on wave53-open, wave53-probe and wave53-final;
- lists on wave52-ship, wave53-probe and wave53-final.

The result is `stdout EQUAL`, with rc 0 for both versions on every one. On wave53-probe the probes print `[M] counter-suffix web
… → GEOMETRY OK` and `ios … → GEOMETRY OK`, and lists web and iOS print OK. Every `ref` line is OK.

**`geometry-gate.py wave53-final --base wave53-open --self-test`:**
- 185 keys;
- gating 37: PASS 0, FAIL 37;
- control 112: PASS 112, FAIL 0;
- report 36: PASS 0, FAIL 36;
- `self-test: HOLDS — 0 violation(s)`, rc 0.

Its output is byte-identical to S1's own `geometry-gate.selftest.out.txt`, apart from the timing line.

**`--lanes L1,L2` on wave53-probe:**
- [P] passes;
- [M] web and iOS pass;
- [M] android and lists android FAIL;
- `revert rule 4 names: Mprime, TB-android`, rc 1.

This equals the record.

## (c) `plan-build.py` and the watchlist
- **Determinism.** `plan-build.py --out` was run twice in the export, rc 0 both times. The two runs are byte-identical.
- **Against the tree.** `expectations.json` equals the shared tree's file. `watchlist.txt` equals both the tree's file and
  `4f3853d7`'s. stdout equals the committed `plan-build.out.txt`.
- **The diff of `expectations.json` against HEAD** is exactly the 2 list entries, the 2 `kind` strings and the 2 `teeth`
  strings. `stayDegenerateEvenIfPass` now has 10 entries.
- **`hooks-check.py`** (7 recorded `--reverted` hooks, HEAD vs fixed): every hook is `SAME` with rc 0, byte-identical to the
  record.
- **The watchlist**, checked with `watchlist-check.mjs`: `unmatched 0` on wave53-final and on wave54-open.
- **`pred-cells-check.py`:** `115 … on 115`.

## (d) `git diff --stat 4f3853d7` contains only owned paths. There are no strays.
- **The 19 tracked paths:**
  - L6 owns ComposedRootSeparator.ts, InlineRuns.ts, InertOutOfFlowWordJoin.ts, its test and the web-tail note;
  - L2 owns TableCellHugTest.kt and the table-body-cell note;
  - L1 owns bidi-bake.mjs, bidi-bake.test.mjs, bidi-marker-bake.mjs and the rtl-marker-bake note;
  - the plan fixer owns DYNAMIC_CAPTURE.md, both geometry probes, PLAN.md, expectations.json, plan-build.py, the L7 and L1
    notes (its two count strings), and the staged seam-copy deletion.
- **The rtl-marker-bake note.** L1 and the plan fixer both edited it, and both declared the edit. The two corrected strings are
  right: the four result JSONs hold 1 + 15 + 7 + 7 = 30 rows, all OK, and numstat gives `105 4`.
- **The index** holds only the deletion. Every untracked path sits in a fixer's list.
- **Ignored evidence.** `git status --ignored` lists 110 ignored files under `results/wave54-*`. That is the same set as the
  fixer's `ignored-evidence.all.txt` (104 `*.log` + 6 `out/`), so this pass added none.

## (e) Focused suites, run in the export

| suite | result |
|---|---|
| web `npx vitest run tests/renderer/` | 12 files, **140 / 140** |
| web-harness `tests/ui/ComposedRootSeparator.test.ts` + `…Wire.test.tsx` | 2 files, **14 / 14** |
| `npm run typecheck`, runtimes/web and apps/web-harness | rc 0, rc 0 |
| Compose TableCellHugTest + TableBodyForestTest | **16 / 16** |
| `bidi-bake.test.mjs` | **74 / 74**, 0 skipped |
| `wpt-white-canvas.test.mjs` | **21 / 21** |
| `extract-fixture.test.mjs` | **483 / 483** |

`doc-staleness-check.sh` in the export exits 1. The only drift is the expected count moves:
- tooling **2334** = 2329 + 5;
- web **1381** = 1379 + 2;
- compose **3492** = 3491 + 1.

Converter, swiftui, the harnesses, coverage and `real` all match. The orchestrator restamps after its sweep.

## (f) Docs and records
- **The DYNAMIC_CAPTURE.md hunk.** HEAD's bytes have sha256 `4258766a…` (= the patch header). `git apply --check` gives rc 0,
  and applying it gives `8cd8fca4…`, which equals the fixer's record. The one deviation from the hunk is the declared N1
  phrase:
  - it now says "returned in the verdict object (`underPaintSpread`)";
  - `label-chrome-check.mjs:98` returns `underPaintSpread` in exactly that way.

  The tree file is `53d0a09f…`.
- **The three iOS names.** `iOS__001_ATC_PropsThenAll_InGreenParent.png`, `iOS__003_ATC_InitialUnderRedParent.png` and
  `iOS__005_ATC_DirectionSurvives.png` are now written in full. All 18 PNGs of
  `wave54-plan/label-chrome-all-reset.seeded-77fe41e8/` are named in the L7 note, and no `__NNN_…` elision is left.
- **The 518 KB seam copy is gone.** It is absent from both the disk and the index. `seam1-hunk.py` regenerates it, on the
  base renderer at both `db6e8aa0` and `1cde1f48`, as sha256 `eaf9111e…`, which is `cmp`-identical to the removed blob.
- **`TableCellHugSeamWiringTest.kt` stays.** `seam-1.patch:17` points at this test file, not at the renderer copy.

## Defects

**must-fix (commit procedure, before stage 1; no lane re-work):**

**V1. The fix pass, committed as it stands, makes four landed units impossible to revert on their own.**

Four fixers edited files that belong to landed units:
- S6 edits M′'s `bidi-marker-bake.mjs` and its call-site line in `bidi-bake.mjs`;
- S3 / S4 edit W1's files;
- N4 edits RS's `ComposedRootSeparator.ts`;
- S5 edits TB-android's `TableCellHugTest.kt`.

**How it was tested.** The check ran in the scratch clone. As a control, each unit was first reverted on `4f3853d7` alone.
Then the fixes were committed per lane on top, and each unit was reverted again.

**At `4f3853d7` (control).** `git revert` of M′ / W1 / RS / TB-android alone gives rc 0 for each. P alone conflicts, which
is its registered order "M′ before P".

**After the fixes are committed per lane:**

| unit reverted alone | rc | conflicting files |
|---|---|---|
| M′ `b312c933` | 1 | bidi-bake.mjs, bidi-bake.test.mjs, bidi-marker-bake.mjs |
| W1 `8846e304` | 1 | the three W1 files |
| RS `110f4b4e` | 1 | ComposedRootSeparator.ts |
| TB-android `e86d3dd2` | 1 | TableCellHugTest.kt |

M′ and TB-android are "device-gated at stage 1", and the wave procedure there is "revert the unit". The registered
`revertOrder` (`['Mprime']`, `['W1']`, `['RS']`, `['TB-android']`) can therefore no longer be executed.

**Fix.** Commit the pass as one commit per unit:
- **Mprime-S1:** S6, the four L1 files;
- **W1-S1:** S3 and S4, the three W1 files;
- **RS-S1:** N4, `ComposedRootSeparator.ts` alone. Do not bundle it with W1;
- **TB-android-S1:** S5;
- then the plan / docs / records commit, which also takes the `git add -f` of the 110 ignored files (S1 S2).

Then record in PLAN §10 (and in `revertOrder`, if the generator is touched) that reverting a unit means reverting
`<unit>-S1` first. For P, the order becomes Mprime-S1 → M′ → P.

**Proof that the split works (executed).** Each `<unit>-S1` reverts first with rc 0, then its unit with rc 0 and no
conflicts, for all four units. The chain S6 → M′ → P gives rc 0. On the split tip, M′ alone still conflicts, so the
revert order is required.

**should-fix:**

**V2. S6's in-page chain reader first runs at stage 1.** `section-runner.sh:375` re-extracts every section with
`BIDI_BAKE=1`, and css-counter-styles is one of the 4 stage-1 sections.

If Chromium's chain for counter-suffix were misaligned or non-initial, two things would happen. M′'s four markers would be
declined (the pre-M′ wire plus `marker-not-baked`). And all three [M] gating keys would FAIL and name **Mprime**. That would
blame M′ for an S6 instrument fault. The static evidence says the chain is all-initial, but no Chromium run has confirmed it.

Fix: pre-register a check before [M] is read. The stage-1 css-counter-styles per-test IR must be byte-identical to the
[W-L3] post tree, and counter-suffix must carry no `marker-not-baked`. If it does carry one, attribute it to S6, not to M′.

**V3. The new left-edge rule has 0 px headroom on a gating iOS key.**
- **The mismatch.** `MARKER_LEFT = 133` and lists `133 ±2` are the antialiased edge (sum < 600). Both probes scan `dark`
  cores (sum < 300), where the ref reads x134.
- **The consequence.** On wave53-probe, iOS row 3 sits at x135, exactly at the +2 bound. `[M] counter-suffix ios` is
  GATING → Mprime.
- **Executed in a scratch copy:** with the centre at 134 ±2, nodot web / iOS are still WRONG (x138, 4 px off), ctl web / iOS
  and the ref stay OK, and iOS row 3 gains 1 px of headroom.

Fix: centre the rule on 134, or record the 0-px headroom as accepted in PLAN §10.

**nits:**
1. **Comment density.** S1's `comments.py` heuristic over all added source lines of this pass counts 32 "uncommented" lines.
   Nearly all are argument / assertion continuations under a block comment, or test bodies with assertion messages. Block
   commenting holds on every hunk I read.
2. **The L2 and L6 fixers mutated the shared tree in place**, not in an export (their runners' `ROOT` is the tree). Every
   restore is verified: those files equal HEAD or the recorded sha256.
3. **Fixer-flagged, carried forward:**
   - the L1 note's wave-53 "+95 / −4 → 1259" does not add up;
   - the D2 quantity is pinned at source level only;
   - the `mask-image` / `mix-blend-mode` TODO;
   - the doc restamp: tooling +5, web +2, compose +1.

## What I could not check
- No Chromium, device or simulator run. V2 is open until stage 1.
- I did not re-run the full suites, by the single-writer rule; the orchestrator's sweep is the count of record.
- `TableCellHugSeamWiringTest` was not re-run; it is unchanged.
- L6's `doc001-verbatim` byte check was not re-run.

STATUS: COMPLETE
