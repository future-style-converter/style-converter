TREES: /private/tmp/claude-501/-Users-dranak-Documents-Projects-Style-Converter--claude-worktrees-trusting-bohr-bd6fbf/0c47cad8-074d-4821-bf4c-b5997f23f535/scratchpad/wave55-skeptic-L7

# L7 · instruments — wave-55 SKEPTIC report

Skeptic worktree: the `TREES:` line above (`git worktree add --detach … 611c9f93`, provisioned per PLAN §0: both
`local.properties`, `node_modules` symlinked; `tools/titan/runs` and `tools/wpt` symlinked read-only for the U1 re-run).
Every Swift build passed `-derivedDataPath <wt>/.dd`. Nothing committed, stashed or reset; the shared tree was only READ,
except this lane dir, where I wrote `skeptic*.{md,py,sh,txt}` only (the lane's note and patches are untouched: their
sha256 lines still match, re-checked at the end). No device, Chromium, emulator, simulator, gate script or full suite.
Records: `skeptic-mutations.txt` (the MUTATING / RESTORED brackets), `skeptic-mutations.out.txt` (red / green verdict per
mutation), `skeptic-mutations-src.txt` (every anchor and replacement), `skeptic-mutate.py` (the bracket runner),
`skeptic-census.py` → `skeptic-census.out.txt`, `skeptic-revert-states.sh` → `skeptic-revert-states.out.txt`.

## Verdict: CLAIM-HOLDS (no must-fix; two should-fix items on the U2 instrument and the [W-L7a] read, five nits)

Everything L7 claims that a host can check reproduces. Each pin's mutation turns its own test red and the byte-exact
restore turns it green again (45 executed brackets, 45 RESTORED == pre). My own census shows the same radius as
expectations.json (0 / 0 / 0 captures, 0 wire). The 42 named cells match cells.mjs exactly. All ten revert states compile
and pass. `land-units.sh --check --only L7` finds 0 errors on a clean 611c9f93. The open items concern instrument
honesty. U2 accepts two pipeline-failure shapes as "identical". Five of its beyond-the-brief guards are never shown
able to fail. The [W-L7a] wire read has no anchor proving the throwaway pipeline is the gate's.

## Repros executed (outputs in the named records)

1. **Patches apply** in landing order U2 → U3 → U4 → U6-compose → U6-swift → U6-web → U6-tooling → U5 on 611c9f93:
   8/8 `git apply` rc 0. Every `patch:` sha256 in `_note.md` equals the file bytes (8/8 OK, checked at the start and the
   end). `git diff HEAD -- tools/titan/results/wave55-gate/` re-cut in my tree = unit-U3.patch, byte for byte (sha256 5887ec66…).
2. **`land-units.sh --check --only L7`** on my worktree at clean 611c9f93, with the lane dir copied in. Every patch
   applies alone, the cumulative series applies, 21 touched paths are clean (11 created), all 8 revert dry-runs pass,
   U1's three files are tracked at HEAD, and the force-adds are present → `SUMMARY 0 error(s), 1 warning(s) —
   landable`. The one WARN ("written < 120 s ago") comes from my own `cp` of the lane dir. The copies were removed
   after the check.
3. **Pin mutations, re-executed with my own anchors** (`skeptic-mutations-src.txt`), each one red on exactly its own
   test and green after the restore:
   - U2: M1 (exit always 0) → P2 P3 P4 P5 P5b P7 red. M2 (PRE-only sections) → P4. M3 (uniform shift dropped) → P3.
     M4 (absolutization dropped) and M4c (absolutization + its guard) → P6 dry-run. M5 (own-checkout refusal) → P6.
   - U4: a (warn string back to "rule 1") → (a). b1 (paragraph numbered "8.") → (b). b2 (counter renamed) → (b).
   - U5: U5a (markerPaintLoss skips both keys) → S6-2 red, 67/68.
   - Compose C1-C7: each `FAIL <its class> > <its test>`, 14 executed, 1 failed; restored 14/0. The XML was read fresh
     each run: `TEST-*.xml` deleted first.
   - Swift S1-S6: each `** TEST FAILED **` rc 65 on its own `Test Case`, then `** TEST SUCCEEDED **`, 6 tests.
   - Web W1-W6: each `Tests 1 failed | 6 passed (7)` on its own `it`. My W1 deletes ONLY the decorations clause, a
     narrower cut than the lane's, and still goes red.
   - Tooling T1 (`hits.slice(0, 1)`), T2 (`lit > 0`) and T2b (`lit >= 2×`): each red on its own test.
   - U3: the amended self-test, run on my tree → `every check as required (mutants M-L18, M-per-arm, M-dry, M-abdiff
     RED)`, rc 0. T6c and the M-abdiff mutant behave as described. ab-diff.mjs exits 2 only on usage or a missing run,
     never on a difference, so the new exit-7 rule cannot mislabel a real A/B finding. I re-cut the record the self-test
     rewrote, back to the patch's bytes.
4. **Extra mutations beyond the lane's pins.** These are U2's beyond-the-brief guards:
   - Proven able to fail: X1 (asymmetric FAIL ignored) → P5 red. X2 (vacuity guard dropped) → P5b red. X5 (.sh exits
     0) → P7 red. X9 (a bad expect line read as "no change") → P5b red.
   - **They survive, green:** X3 (an extract-rc difference ignored), X4 (a one-sided document does not move the running
     delta), X6 (the `extracted 0` check dropped), X7 (the UNREAD rule dropped), X8 (the `wpt-buckets.json`
     prerequisite dropped). See D2.
   - Controls, green by design: M4b (only the absolute-path guard dropped, with absolutization intact) and U4b3 (a
     bold-markup typo).
   - **U5b, green.** A MISSPELLED initial (`maskImage: 'None'`) leaves all 68 bidi-bake tests green. The S6 suites build
     their "Chromium" chains by spreading `MARKER_PAINT_INITIAL`, so they cannot see a spelling error. This confirms that
     [W-L7a] step 3 (the real bake path in Chromium) is the ONLY guard on R1, exactly as the note and brief say.
5. **U2 calibration on real data** (the parked wave-54 trees, `--no-status`):
   - `identical 1397 · content-changed 23 · renumbered 15 · one-sided 0 · renumbered-anomalous 0`, exit 0, 0 NOTE lines.
   - With counter-suffix deleted from the expect file → `UNEXPECTED CONTENT-CHANGED …counter-suffix.json`, exit 5.
   - An adversarial tamper of my own: one id of a renumbered css-counter-styles document bumped +1 in a copy →
     `RENUMBERED-ANOMALOUS … (shifts {6, 7} vs running delta 6)` + `MISSING RENUMBERED …`, exit 5. The strict wave-53
     rule bites on real data.
6. **Census** (`skeptic-census.out.txt`; my own crawl, an over-approximating basename closure of 112 scripts):
   - Executed by the gate: only `bidi-marker-paint.mjs` (U5). Built into captures: the three U4 web files.
   - Comment-stripped emit (rolldown/oxc transform + a compress-free, mangle-free minify, which drops comments only):
     `ComposedRootSeparator.ts` and `InertOutOfFlowWordJoin.ts` are COMMENTS ONLY. `InlineRuns.ts` is STRING LITERALS
     ONLY (the warn). `bidi-marker-paint.mjs` is CODE (the frozen map).
   - My first strip, through TypeScript's JS API, was VACUOUS: TS 7 ships no `lib/typescript.js`, so both sides emitted
     "" and read as identical. I replaced it, and the strip now exits non-zero on an empty emit.
   - U5's radius: 17 bidi-baked documents in 6 sections. 4 have list items; counter-suffix is the only one with planned
     root-owned marker runs (bake log `2 roots, 10 runs` + `6 markers, 2 declined`). 0 of the 17 mention mask or
     mix-blend. 0 of all 1435 sources have both a list item and mask or mix-blend.
   - Web capture is blind to the warn: the harness logs only `msg.type() === 'error'` (`apps/web-harness/capture-screenshots.mjs:208-213`).
   - **Census delta vs expectations.json lanes["L7-instruments"]: IDENTICAL.** captureCarriers {web:[], ios:[],
     android:[]}, wireCarriers [], every revertUnit 0/0/0. No plan regeneration needed.
   - The note's 42 predicted cells are exactly `mustNotMove`. Three bidi-baked tests carry no L7 must-not-move cell
     (anchor-center-safe-rtl, attachment-local-positioning-3/-4). All three are list-free, so they are outside U5's
     behavioural radius (the paint read gates only a planned marker, `bidi-bake.mjs:947`).
   - expectations.json is being edited on the shared tree by the round-4 probe fix (PLAN.md, plan-build.py, several
     probes). Its L7 entry is byte-identical to HEAD's, and landing-manifest.json is unchanged.
7. **Scores** (`cells.mjs '<42 terms>' wave54-final wave55-open`): all 42 claimed `<verdict> <score>` values match
   wave54-final exactly and are unchanged at wave55-open (0 mismatches).
8. **Geometry self-test** (`geometry-gate.py wave54-final --base wave54-open --lanes L7 --self-test`, run read-only from
   the shared tree): exit 0, `self-test: HOLDS`, 12 keys, all CONTROL PASS. Identical to the lane's record apart from its
   `#` lines. L7 has 0 gating keys, so "every gating key fails today" is vacuous by design. The ability to fail is the
   U1 fake table.
9. **U1 re-execution** (`flex-zero-gap-rules.fakes-run.sh` in my tree): every line but the timestamp / HEAD line is
   byte-identical to the committed output. Restored byte-exact (sha256 999f22a7… before and after).
10. **Verbatim payloads**:
    - Compose C7 and Swift S5 `cvAuto` equal the wave54-final component byte for byte. The compact JSON matches;
      sha256 of the source document fae6d09f… is identical at wave55-open.
    - Web `BOX4` (U4 pin + U6-web) parses equal to hyphens-out-of-flow-002 components 289/290 (0f1d9251…, also identical
      at wave55-open).
    - The C2 / C3 strings really carry U+00AD (and U+2010 in C3's lines).
11. **PNGs looked at** (frozen ref vs wave54-final captures):
    - The lane's three `look/` strips match the files they crop.
    - counter-suffix rows 3-4, zoomed: ref, web and Android read `א. foo` / `ב. bar`, iOS reads `.א foo` / `.ב bar`.
      This is L5's residue, as the note says.
    - flex-gap-decorations-033: four agreeing pictures, iOS saturated.
    - hyphens-out-of-flow-002: `high-` / `way` in every box on all four.
    - Nothing in these pictures is L7's to change, and no flip is claimed. So no DEGENERATE label is owed, and none is
      missing.
12. **Spec-paragraph claims vs code.** The web predicate (`InertOutOfFlowWordJoin.ts:98-120`: structure refusal,
    ABSOLUTE | FIXED, alpha ≤ 0, only Position / Color / Hyphens), the tag ring `span | time | data`, the host-own
    `hyphens: auto`, the `[InlineRuns]` channel, and the Compose drop counted and printed at `ComponentRenderer.kt:3905-3906`
    all hold. The qualified name does not (D3).
13. **Other checks.**
    - 0 scratchpad pointers in any patch or lane record (the TREES lines are exempt). unit-U3.patch REMOVES one; the
      re-run record names the system `mktemp` dir.
    - 0 skipped or focused tests, 0 debug leftovers, 0 seam files, 0 paths in two units, 0 writes into a lane records dir.
    - `~/.zshenv.l7` is absent.

## revertStatesCompiled (my own re-run; `skeptic-revert-states.out.txt`)

- **Method.** Every L7 revertOrder chain is the unit alone, so the states are FULL and FULL−U for each U; U1 has no
  patch, so FULL−U1 = FULL. Each state starts from a reset of my tree to 611c9f93: tracked files restored, the 11 new L7
  paths removed. The kept patches are then applied in landing order.
- **Rebuild rule.** Compose and Swift are rebuilt and their pins run whenever their source-tree hash differs from the
  last built one. A Gradle or Xcode test build compiles the whole test source set or target, not only the classes run.
- **My first run's rc fields were wrong.** It printed four false `rc 1`, read from a stale `${PIPESTATUS[0]}` of a
  preceding `[[ -f ]]` test; its counts were all green. The script was fixed, and the run below is the second, full one
  (every rc is now the runner's own).

```
revertStatesCompiled: FULL: tooling[# pass 79 # fail 0 rc 0] U3-syntax[rc 0] web-tsc[rc 0] web[Tests 21 passed (21) rc 0] schema[39 valid rc 0] compose[tree a6a2f5d7 built: Tests 22 executed in 5 classes, 0 failed rc 0] swift[tree e9f591dd built: Executed 14 tests, with 0 failures ** TEST SUCCEEDED ** rc 0]
revertStatesCompiled: FULL-U2: tooling[# pass 70 # fail 0 rc 0] U3-syntax[rc 0] web-tsc[rc 0] web[Tests 21 passed (21) rc 0] schema[rc 0] compose[= last built] swift[= last built]
revertStatesCompiled: FULL-U3: tooling[# pass 79 # fail 0 rc 0] U3-syntax[rc 0] web-tsc[rc 0] web[Tests 21 passed (21) rc 0] schema[rc 0] compose[= last built] swift[= last built]
revertStatesCompiled: FULL-U4: tooling[# pass 79 # fail 0 rc 0] U3-syntax[rc 0] web-tsc[rc 0] web[Tests 19 passed (19) rc 0] schema[rc 0] compose[= last built] swift[= last built]
revertStatesCompiled: FULL-U6-compose: tooling[# pass 79 # fail 0 rc 0] U3-syntax[rc 0] web-tsc[rc 0] web[Tests 21 passed (21) rc 0] schema[rc 0] compose[tree 087ddef4 built: Tests 14 executed in 2 classes, 0 failed rc 0] swift[= last built]
revertStatesCompiled: FULL-U6-swift: tooling[# pass 79 # fail 0 rc 0] U3-syntax[rc 0] web-tsc[rc 0] web[Tests 21 passed (21) rc 0] schema[rc 0] compose[tree a6a2f5d7 built: Tests 22, 0 failed rc 0] swift[tree 0125d731 built: Executed 8 tests, with 0 failures ** TEST SUCCEEDED ** rc 0]
revertStatesCompiled: FULL-U6-web: tooling[# pass 79 # fail 0 rc 0] U3-syntax[rc 0] web-tsc[rc 0] web[Tests 14 passed (14) rc 0] schema[rc 0] compose[= last built] swift[tree e9f591dd built: Executed 14 tests, with 0 failures ** TEST SUCCEEDED ** rc 0]
revertStatesCompiled: FULL-U6-tooling: tooling[# pass 77 # fail 0 rc 0] U3-syntax[rc 0] web-tsc[rc 0] web[Tests 21 passed (21) rc 0] schema[rc 0] compose[= last built] swift[= last built]
revertStatesCompiled: FULL-U5: tooling[# pass 79 # fail 0 rc 0] U3-syntax[rc 0] web-tsc[rc 0] web[Tests 21 passed (21) rc 0] schema[rc 0] compose[= last built] swift[= last built]
revertStatesCompiled: FULL: (end state) every check rc 0, trees = the last built
```

These agree with the lane's ten lines: the same pass counts (79 / 70 / 77; web 21 / 19 / 14) and the same Swift 8-test
base state. My tree ends in FULL.

## Defects, ranked

**D1 — should-fix — [W-L7a] step 4 can read "identical" vacuously, and the classifier accepts two pipeline-failure
shapes in silence.**
- Executed on synthetic status.
- **Shape 1.** `status.tsv` with extract rc 1 on BOTH sides and no FAIL lines → `identical 1 …`, exit 0, NO note. The
  code's own comment says otherwise: `wire-differential.mjs:83` reads "a symmetric rc is a NOTE", but nothing is pushed.
- **Shape 2.** A tests.list document absent on both sides is only `NOTE … absent on both sides`.
  - Neither shape can happen in a clean pipeline:
    - in the gate every tests.list document has a per-test-ir file (1435 / 1435 at wave54-final, the refcrash FAIL
      included, as a placeholder);
    - `build-combined-fixture.mjs:168-206` turns a fixture that was never extracted into an identical `missing`
      placeholder on both sides.
  - So a symmetric partial extraction, or a bake that never engages on either side, reads `identical`, exit 0. Nothing
    in step 4's expectation can catch the second: `extracted=48` stays true when the bidi bake bails.
- U5's real risk (R1, the spellings) is still caught. Step 3 runs marker-probe.mjs from the POST tree, i.e. the real
  bake path with U5. That is why this is should-fix, not must-fix. But step 4 is the evidence for C3 ("U5's claim is
  wire-only"), and as written it cannot fail on that shape.
- **Fix (window request).** After step 4, anchor the PRE side to the run of record:
  `node <S>/wave55-wl7a-diff/tools/titan/wire-differential.mjs classify --pre <SH>/tools/titan/runs/wave55-open/sections --post <SH>/tools/titan/runs/wave55-wl7a/pre --expect <empty> --tests-root <SH>/tools/titan/runs/wave55-open/sections --no-status --sections css-anchor-position,css-backgrounds,css-counter-styles,css-text,css-writing-modes,selectors`
  - Expect `identical 288 · content-changed 0 · …`, exit 0. The shape is executed: wave54-final vs wave55-open gives
    exactly that.
  - Also require 17 `[bidi-bake: baked` stamps across `<out>/{pre,post}/*/extract.txt`, and counter-suffix
    `2 roots, 10 runs` on both sides.
- **Fix (tool).** Push the NOTE the comment promises for a symmetric extract rc ≠ 0. When status is read, make a
  tests.list document absent on both sides a PIPELINE problem.

**D2 — should-fix — five U2 guards are claimed but never shown able to fail.** My mutations X3, X4, X6, X7 and X8 each
leave all 9 pins green:
- X3: the `extract rc differs` check;
- X4: the one-sided document's delta;
- X6: `extracted 0` → PIPELINE, the only guard against a section that extracted nothing on both sides;
- X7: the UNREAD rule;
- X8: the `wpt-buckets.json` refusal (the note's own reason: without it both sides agree on a non-gate IR).

PLAN §0 ("Every new instrument (L7 U2 …) is proven red by a fake before it is trusted") is met for the brief's P1-P6
guards. These five are the note's "beyond the brief" additions, presented as behaviour. **Fix:** one synthetic case each
for X3 / X6 / X8 at least (status rows with `rx` 0 vs 1, `extracted=0` on both sides, a tree without
`tools/titan/wpt-buckets.json` → exit 2); or label them "untested" in the note.

**D3 — nit — the spec names a symbol path that does not exist.** schema/spec/03-children.md (new paragraph) and pin (b)
cite `InlineRunFold.Folded.droppedOutOfFlowMembers`. In Compose, `Folded` is nested in `Outcome`
(`InlineRunFold.kt:142` `sealed interface Outcome`, `:144` `data class Folded`). The real path is
`InlineRunFold.Outcome.Folded.droppedOutOfFlowMembers`, which `InertOutOfFlowMember.kt:62` already spells correctly.
The note says "each clause re-checked against code". The counter name is right, the qualified path is not, and pin (b)
now enshrines the short form. Fix: `Outcome.Folded` in the paragraph and in the pin's `toContain`.

**D4 — nit — `wl7a-marker-paint-probe.mjs` has no vacuity guard.**
- If the page yields 0 `<li>` (a wrong path, an empty load), `bad` stays 0. The script then prints `WL7A MARKER PAINT:
  every value none / normal` and exits 0. It prints `items N (rtl M)`, but the decision reads only the last line and
  the exit code.
- Fix: exit 1 unless the counts are `items 12 (rtl 4)` (counter-suffix: 6 `<ol>` × 2 `<li>`, 2 RTL lists), or add
  that line to the step-3 expectation.
- Code-read only (Chromium is not a skeptic tool).

**D5 — nit — size rule.**
- `ab-arms55.selftest.sh` grows 233 → 248 lines with new test logic (T6c + the M-abdiff mutant) in a file already over
  200. That file is not on PLAN §0's list of oversize files and what each may receive.
- The note gives the delta (`248, +15`) but does not flag it as an exception.
- `ab-arms55.sh` (317 → 319) receives only a one-line condition plus a header line, which is within the rule.

**D6 — nit — U1's `flex-zero-gap-rules.fakes-run.sh` runs the probes with bare `python3`.** It does not use the pinned
`/opt/homebrew/bin/python3.12 -B` (C14). The verdicts are identical under both (the lane's re-run and mine), and it is
a committed planning file with no U1 patch. Note it, or pin the interpreter if U1 ever ships a patch.

**D7 — nit — comment density in the new tests.** wire-differential.test.mjs has about 100 code lines with neither a
same-line comment nor a comment directly above. The two web pin files have about 30 each. Their `test(...)` / `it(...)`
names carry the why. The production files (.mjs / .sh) are commented nearly line by line.

**Info (not defects).**
- The note's mutation log interleaves brackets: `MUTATING … S1`, then U2 pairs, then `RESTORED … S1`, and the same for
  S2. Two mutations were in flight at once in the lane tree, on disjoint files with disjoint tests. Harmless, and every
  pair restored to its pre sha256.
- `.dd/` is untracked (`??`) in a lane worktree. Not gitignored, but outside every `beforeEveryDeviceRun` path.
- The `.sh` does not pin `WPT_DIR` / `WPT_FIXTURES_ROOT`. An ambient export would apply to both trees alike.

## Pins: my bracketed mutations (`skeptic-mutations.txt`, verbatim)

```
MUTATING tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # skeptic U2-M1
RESTORED tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # skeptic U2-M1 (== pre)
MUTATING tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # skeptic U2-M2
RESTORED tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # skeptic U2-M2 (== pre)
MUTATING tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # skeptic U2-M3
RESTORED tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # skeptic U2-M3 (== pre)
MUTATING tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # skeptic U2-X1
RESTORED tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # skeptic U2-X1 (== pre)
MUTATING tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # skeptic U2-X2
RESTORED tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # skeptic U2-X2 (== pre)
MUTATING tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # skeptic U2-X3
RESTORED tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # skeptic U2-X3 (== pre)
MUTATING tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # skeptic U2-X4
RESTORED tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # skeptic U2-X4 (== pre)
MUTATING tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # skeptic U2-X6
RESTORED tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # skeptic U2-X6 (== pre)
MUTATING tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # skeptic U2-X7
RESTORED tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # skeptic U2-X7 (== pre)
MUTATING tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # skeptic U2-X9
RESTORED tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # skeptic U2-X9 (== pre)
MUTATING tools/titan/wire-differential.sh sha256 f8a73de0f99566cc41a0669cd2b0f41f554c27308f9e9d4e2a5bf8bb315348ce   # skeptic U2-M4
RESTORED tools/titan/wire-differential.sh sha256 f8a73de0f99566cc41a0669cd2b0f41f554c27308f9e9d4e2a5bf8bb315348ce   # skeptic U2-M4 (== pre)
MUTATING tools/titan/wire-differential.sh sha256 f8a73de0f99566cc41a0669cd2b0f41f554c27308f9e9d4e2a5bf8bb315348ce   # skeptic U2-M4b
RESTORED tools/titan/wire-differential.sh sha256 f8a73de0f99566cc41a0669cd2b0f41f554c27308f9e9d4e2a5bf8bb315348ce   # skeptic U2-M4b (== pre)
MUTATING tools/titan/wire-differential.sh sha256 f8a73de0f99566cc41a0669cd2b0f41f554c27308f9e9d4e2a5bf8bb315348ce   # skeptic U2-M5
RESTORED tools/titan/wire-differential.sh sha256 f8a73de0f99566cc41a0669cd2b0f41f554c27308f9e9d4e2a5bf8bb315348ce   # skeptic U2-M5 (== pre)
MUTATING tools/titan/wire-differential.sh sha256 f8a73de0f99566cc41a0669cd2b0f41f554c27308f9e9d4e2a5bf8bb315348ce   # skeptic U2-X5
RESTORED tools/titan/wire-differential.sh sha256 f8a73de0f99566cc41a0669cd2b0f41f554c27308f9e9d4e2a5bf8bb315348ce   # skeptic U2-X5 (== pre)
MUTATING tools/titan/wire-differential.sh sha256 f8a73de0f99566cc41a0669cd2b0f41f554c27308f9e9d4e2a5bf8bb315348ce   # skeptic U2-X8
RESTORED tools/titan/wire-differential.sh sha256 f8a73de0f99566cc41a0669cd2b0f41f554c27308f9e9d4e2a5bf8bb315348ce   # skeptic U2-X8 (== pre)
MUTATING tools/titan/wire-differential.sh sha256 f8a73de0f99566cc41a0669cd2b0f41f554c27308f9e9d4e2a5bf8bb315348ce   # skeptic U2-M4c
RESTORED tools/titan/wire-differential.sh sha256 f8a73de0f99566cc41a0669cd2b0f41f554c27308f9e9d4e2a5bf8bb315348ce   # skeptic U2-M4c (== pre)
MUTATING tools/titan/bidi-marker-paint.mjs sha256 f602e3c18435c1bb8f228e767966ddd6e09cd85ea9ced0d13b8071de42d32748   # skeptic U5-U5a
RESTORED tools/titan/bidi-marker-paint.mjs sha256 f602e3c18435c1bb8f228e767966ddd6e09cd85ea9ced0d13b8071de42d32748   # skeptic U5-U5a (== pre)
MUTATING tools/titan/bidi-marker-paint.mjs sha256 f602e3c18435c1bb8f228e767966ddd6e09cd85ea9ced0d13b8071de42d32748   # skeptic U5-U5b
RESTORED tools/titan/bidi-marker-paint.mjs sha256 f602e3c18435c1bb8f228e767966ddd6e09cd85ea9ced0d13b8071de42d32748   # skeptic U5-U5b (== pre)
MUTATING tools/titan/bidi-marker-paint.mjs sha256 f602e3c18435c1bb8f228e767966ddd6e09cd85ea9ced0d13b8071de42d32748   # skeptic U5-U5c
RESTORED tools/titan/bidi-marker-paint.mjs sha256 f602e3c18435c1bb8f228e767966ddd6e09cd85ea9ced0d13b8071de42d32748   # skeptic U5-U5c (== pre)
MUTATING runtimes/web/src/renderer/InlineRuns.ts sha256 0c6d8c46527a37733be52b30f80595f6ffcba5631b7b806accf6507a66c73898   # skeptic U4-a
RESTORED runtimes/web/src/renderer/InlineRuns.ts sha256 0c6d8c46527a37733be52b30f80595f6ffcba5631b7b806accf6507a66c73898   # skeptic U4-a (== pre)
MUTATING schema/spec/03-children.md sha256 ff34337fa11b553705b58137f5c51aaae92985ce5cdefcd302ec34ec112bb088   # skeptic U4-U4b1
RESTORED schema/spec/03-children.md sha256 ff34337fa11b553705b58137f5c51aaae92985ce5cdefcd302ec34ec112bb088   # skeptic U4-U4b1 (== pre)
MUTATING schema/spec/03-children.md sha256 ff34337fa11b553705b58137f5c51aaae92985ce5cdefcd302ec34ec112bb088   # skeptic U4-U4b2
RESTORED schema/spec/03-children.md sha256 ff34337fa11b553705b58137f5c51aaae92985ce5cdefcd302ec34ec112bb088   # skeptic U4-U4b2 (== pre)
MUTATING schema/spec/03-children.md sha256 ff34337fa11b553705b58137f5c51aaae92985ce5cdefcd302ec34ec112bb088   # skeptic U4-U4b3
RESTORED schema/spec/03-children.md sha256 ff34337fa11b553705b58137f5c51aaae92985ce5cdefcd302ec34ec112bb088   # skeptic U4-U4b3 (== pre)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/typography/wrapping/HyphenateCharacterExtractor.kt sha256 968d957fb9656965c36708e60feb2c527c25843de0669547052e6801c5cb2e31   # skeptic C1
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/typography/wrapping/HyphenateCharacterExtractor.kt sha256 968d957fb9656965c36708e60feb2c527c25843de0669547052e6801c5cb2e31   # skeptic C1 (== pre)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/typography/wrapping/PreBreakPipeline.kt sha256 18bafc7bb18a0bee96f8723678c5996b03ab49e5e4456eeea8d7e84f3d4a5304   # skeptic C2
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/typography/wrapping/PreBreakPipeline.kt sha256 18bafc7bb18a0bee96f8723678c5996b03ab49e5e4456eeea8d7e84f3d4a5304   # skeptic C2 (== pre)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/typography/wrapping/SoftHyphenCuts.kt sha256 d5cd45df0130558c5f7faa75ed6064c5649bd83b0ded704199883ca641b5a49a   # skeptic C3
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/typography/wrapping/SoftHyphenCuts.kt sha256 d5cd45df0130558c5f7faa75ed6064c5649bd83b0ded704199883ca641b5a49a   # skeptic C3 (== pre)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/OutOfFlowContainingBlock.kt sha256 ed0d29ac7577f7e56a34c7002cddbf662617db144908dfc8f42223b60f1b86d6   # skeptic C4
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/OutOfFlowContainingBlock.kt sha256 ed0d29ac7577f7e56a34c7002cddbf662617db144908dfc8f42223b60f1b86d6   # skeptic C4 (== pre)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/performance/PerformanceExtractor.kt sha256 9c31018c1217d3ad1afc6cd50c0553d6cd4d05d1b5be883f0b6817fa95581ef9   # skeptic C5
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/performance/PerformanceExtractor.kt sha256 9c31018c1217d3ad1afc6cd50c0553d6cd4d05d1b5be883f0b6817fa95581ef9   # skeptic C5 (== pre)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/OutOfFlowContainingBlock.kt sha256 ed0d29ac7577f7e56a34c7002cddbf662617db144908dfc8f42223b60f1b86d6   # skeptic C6
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/OutOfFlowContainingBlock.kt sha256 ed0d29ac7577f7e56a34c7002cddbf662617db144908dfc8f42223b60f1b86d6   # skeptic C6 (== pre)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/OutOfFlowContainingBlock.kt sha256 ed0d29ac7577f7e56a34c7002cddbf662617db144908dfc8f42223b60f1b86d6   # skeptic C7
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/OutOfFlowContainingBlock.kt sha256 ed0d29ac7577f7e56a34c7002cddbf662617db144908dfc8f42223b60f1b86d6   # skeptic C7 (== pre)
MUTATING runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts sha256 e3982ec6d652cf0df4926b458b70adab47cf017a9306dd4d2aa45c63c2028ac0   # skeptic U6-W1
RESTORED runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts sha256 e3982ec6d652cf0df4926b458b70adab47cf017a9306dd4d2aa45c63c2028ac0   # skeptic U6-W1 (== pre)
MUTATING runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts sha256 e3982ec6d652cf0df4926b458b70adab47cf017a9306dd4d2aa45c63c2028ac0   # skeptic U6-W2
RESTORED runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts sha256 e3982ec6d652cf0df4926b458b70adab47cf017a9306dd4d2aa45c63c2028ac0   # skeptic U6-W2 (== pre)
MUTATING runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts sha256 e3982ec6d652cf0df4926b458b70adab47cf017a9306dd4d2aa45c63c2028ac0   # skeptic U6-W3
RESTORED runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts sha256 e3982ec6d652cf0df4926b458b70adab47cf017a9306dd4d2aa45c63c2028ac0   # skeptic U6-W3 (== pre)
MUTATING runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts sha256 e3982ec6d652cf0df4926b458b70adab47cf017a9306dd4d2aa45c63c2028ac0   # skeptic U6-W4
RESTORED runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts sha256 e3982ec6d652cf0df4926b458b70adab47cf017a9306dd4d2aa45c63c2028ac0   # skeptic U6-W4 (== pre)
MUTATING runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts sha256 e3982ec6d652cf0df4926b458b70adab47cf017a9306dd4d2aa45c63c2028ac0   # skeptic U6-W5
RESTORED runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts sha256 e3982ec6d652cf0df4926b458b70adab47cf017a9306dd4d2aa45c63c2028ac0   # skeptic U6-W5 (== pre)
MUTATING runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts sha256 e3982ec6d652cf0df4926b458b70adab47cf017a9306dd4d2aa45c63c2028ac0   # skeptic U6-W6
RESTORED runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts sha256 e3982ec6d652cf0df4926b458b70adab47cf017a9306dd4d2aa45c63c2028ac0   # skeptic U6-W6 (== pre)
MUTATING tools/visual/label-chrome-check.mjs sha256 fad373782a0de0eda2664e2a5cad0d4ea7b85b3bdd759fb91b39388e40dd7b65   # skeptic U6-T1
RESTORED tools/visual/label-chrome-check.mjs sha256 fad373782a0de0eda2664e2a5cad0d4ea7b85b3bdd759fb91b39388e40dd7b65   # skeptic U6-T1 (== pre)
MUTATING tools/visual/label-chrome-check.mjs sha256 fad373782a0de0eda2664e2a5cad0d4ea7b85b3bdd759fb91b39388e40dd7b65   # skeptic U6-T2
RESTORED tools/visual/label-chrome-check.mjs sha256 fad373782a0de0eda2664e2a5cad0d4ea7b85b3bdd759fb91b39388e40dd7b65   # skeptic U6-T2 (== pre)
MUTATING tools/visual/label-chrome-check.mjs sha256 fad373782a0de0eda2664e2a5cad0d4ea7b85b3bdd759fb91b39388e40dd7b65   # skeptic U6-T2b
RESTORED tools/visual/label-chrome-check.mjs sha256 fad373782a0de0eda2664e2a5cad0d4ea7b85b3bdd759fb91b39388e40dd7b65   # skeptic U6-T2b (== pre)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/wrapping/HyphenateCharacterExtractor.swift sha256 8a0ba398da67358eb3b2965dfb4da83217ead6513f73042bea43fd863f563958   # skeptic S1
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/wrapping/HyphenateCharacterExtractor.swift sha256 8a0ba398da67358eb3b2965dfb4da83217ead6513f73042bea43fd863f563958   # skeptic S1 (== pre)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/layout/position/OutOfFlowContainingBlock.swift sha256 2682c94c607c3a73734043060e416d7b7f732e4aa9bbc7add4159ac8bfe14ea8   # skeptic S2
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/layout/position/OutOfFlowContainingBlock.swift sha256 2682c94c607c3a73734043060e416d7b7f732e4aa9bbc7add4159ac8bfe14ea8   # skeptic S2 (== pre)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/layout/position/OutOfFlowContainingBlock.swift sha256 2682c94c607c3a73734043060e416d7b7f732e4aa9bbc7add4159ac8bfe14ea8   # skeptic S3
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/layout/position/OutOfFlowContainingBlock.swift sha256 2682c94c607c3a73734043060e416d7b7f732e4aa9bbc7add4159ac8bfe14ea8   # skeptic S3 (== pre)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/layout/position/OutOfFlowContainingBlock.swift sha256 2682c94c607c3a73734043060e416d7b7f732e4aa9bbc7add4159ac8bfe14ea8   # skeptic S4
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/layout/position/OutOfFlowContainingBlock.swift sha256 2682c94c607c3a73734043060e416d7b7f732e4aa9bbc7add4159ac8bfe14ea8   # skeptic S4 (== pre)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/layout/position/OutOfFlowContainingBlock.swift sha256 2682c94c607c3a73734043060e416d7b7f732e4aa9bbc7add4159ac8bfe14ea8   # skeptic S5
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/layout/position/OutOfFlowContainingBlock.swift sha256 2682c94c607c3a73734043060e416d7b7f732e4aa9bbc7add4159ac8bfe14ea8   # skeptic S5 (== pre)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/layout/position/OutOfFlowContainingBlock.swift sha256 2682c94c607c3a73734043060e416d7b7f732e4aa9bbc7add4159ac8bfe14ea8   # skeptic S6
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/layout/position/OutOfFlowContainingBlock.swift sha256 2682c94c607c3a73734043060e416d7b7f732e4aa9bbc7add4159ac8bfe14ea8   # skeptic S6 (== pre)
```

Verdicts per bracket (`skeptic-mutations.out.txt`): every pin mutation went red, and every restore went green:
- U2 M1 M2 M3 M4 M4c M5;
- U4 a b1 b2;
- U5 U5a U5c;
- C1-C7, S1-S6 (rc 65), W1-W6, T1 T2 T2b.

Probes that stayed green (findings, not pin claims):
- U2 X3 X4 X6 X7 X8 → D2;
- U5b: spelling-blind by construction (R1; [W-L7a] step 3 is the guard);
- controls: U2 M4b (guard only), U4b3 (markup).

45 MUTATING / 45 RESTORED, all `== pre`.

## What I could not check (honest limits)

- **[W-L7a] itself.** It needs Chromium and the converter, a window. So the following were not executed by me (nor by
  the lane):
  - the CDP spellings of `maskImage` / `mixBlendMode` on `::marker`. Chromium 147-151 is in the puppeteer cache, where
    unprefixed `mask-image` has long been supported, so the risk looks low;
  - `wl7a-marker-paint-probe.mjs`, marker-probe.mjs with U5, and the real-pipeline run of `wire-differential.sh`.
  - The run path of `wire-differential.sh` was exercised only through P7's stubs. The classifier was exercised on real
    parked IR (calibration plus my tamper).
- **Device captures.** No capture of any landed state exists. The "identical" predictions rest on the census (comments
  and a console string in the web build; U5 wire-only behind [W-L7a]).
- **Full sweeps.** No full suite was run: not the web vitest 1390 count, nor the Compose / Swift / tooling totals. The
  +9 / +8 / +6 / +11 deltas are consistent with the test counts in the new files, but the sweep is the orchestrator's.
- **ab-arms55 proofs.** I did not re-run `ab-arms55.against-proof.sh` / `revert-check-proof.sh` on the amended runner.
  Their records cite the base runner 5cd6fccb…, and the amendment touches steps 8-9 only.
- **U5 radius.** Whether css3-counter-styles-101/-102/-103 plan any root-owned marker run (the brief says 0 runs) is
  taken from the brief and the bake log, not from a re-bake.
