# wave 54 · L3 hyphenate-character — skeptic report

Skeptic of builder lane L3. I read `_note.md`, PLAN.md §0, §1, §2 "L3 · hyphenate-character", §3, §4, §6, the brief
`wave54-plan/hyphenate-character-compose.md` and `expectations.json` `lanes["L3-hyphenate-character"]`, plus the lane's diff
on its 19 owned paths and its 4 seam patches. Shared tree `/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf`,
branch `campaign/applier-campaign`, HEAD `029139fd` (its three seam files are byte-identical to `db6e8aa0`). Every
script and output I used is in `skeptic/`. I wrote my own runners for all of them and did not reuse the lane's
(`sk-mutate.sh`, `sk-conv.sh`, `sk-compose.sh`, `sk-catalyst.sh`, `sk-seam.sh`, `sk-u3-pins.sh`, `sk-u3-fullbytes.sh`,
`sk-br-census.py`, `sk-shy-census.py`, `sk-sheet.py`).

## Verdict: MIXED (no must-fix)

The mechanism claims hold under executed repro:
- every pin can fail;
- the radius is exactly as reported, and nothing is under-reported;
- the seams apply cleanly, compile, and the pins are green with each seam applied;
- each revert unit is complete, the units are disjoint, and each stands alone.

**One restated prediction does not hold:** the hyphenate-character-002 rows (should-fix S1). The other findings are nits.

## Executed repros (all at 2026-10-08 22:11–22:42)

**Baselines on the unseamed tree** (rule 2b: the tree compiles without any L3 seam):
- converter `HyphenateCharacterPropertyParserTest`: tests=11, failures=0 (fresh XML).
- Compose: 73 tests, 0 failures (fresh XML): GreedyLineBreakerTest 23, PreBreakPipelineTest 26,
  HyphenateCharacterExtractorTest 6, SoftHyphenPolicyTest 9, WordBreakOpportunitiesTest 9.
- Catalyst, private derived data: GreedyLineBreakerTests + TypographyTests + SoftHyphenPolicyTests, "Executed 49 tests,
  with 0 failures".

**Mutations** (`skeptic/sk-mutations.log`). For each one: sha256 before, the mutation, the run (RED), restore from a byte
copy (BYTE-EXACT), the re-run (GREEN). There are 29 in all.

| # | mutation (file) | result |
|---|---|---|
| SK-U1-a | drop hex decoding (`CssStringParser.consumeEscape`) | RED: pins 2/3/4 + §4.3.7 rules, 4 failed |
| SK-U1-b | `""` → legacy path (isEmpty → null) | RED: pin 1 + wire + end-to-end, 3 failed |
| SK-U1-c* | accept a token after the ending quote | RED 2 |
| SK-U1-d* | do not eat the whitespace after a hex escape | RED 1 |
| SK-U1-e* | a raw newline is not a bad string | RED 1 |
| SK-U2A-a | `took` answers by the wave-52 count | RED: (a), (b) |
| SK-U2A-b | fold without `hyphenChar` | RED: (a)–(d) |
| SK-U2A-c | lower-case the string | RED 1 |
| SK-U2A-d1/d2 | `""` read as absence / `auto` read as the string "auto" | RED 1 / RED 1 |
| SK-U2A-e | walk misses a taken op | RED: (a), (b), (d) + 2 wave-52 pins |
| SK-U3-a | drop the re-arm (seam-3 applied, under lock) | RED 3 |
| SK-U3-b | white-space-only runs count | RED 1 |
| SK-U3-c* | window opens at run 0 | RED 2 |
| SK-U3b-a | inherited reading (seam-3+3b) | RED 1 (baseline-007) |
| SK-U3b-b | br rule ignores the host line box | RED 1 |
| SK-U3b-c* | `normal` → 1.0 | RED 2 |
| SK-U2I-a | SpentHyphen drops the strip | RED 3 |
| SK-U2I-b | lower-case | RED 1 |
| SK-U2I-c1/c2 | `""` → nil / auto → "auto" | RED / RED 2 |
| SK-U2I-d | drop the StyleBuilder mirror | RED 1 |
| SK-U2I-e* | applier never raises `touched` | RED 2 |
| SK-U2I-f* | `>` → `>=` in SpentHyphen | RED 1 |
| SK-U2I-h* | overflow test ignores `spentHyphen` | RED 1 |
| **SK-X1*** | Compose: first declaration wins (`lastOrNull` → `firstOrNull`) | **SURVIVED** |
| **SK-X2*** | Compose: the clamp path drops `hyphenChar` | **SURVIVED** |
| **SK-X3*** | Compose: the walk's fallback → `false` (`legacyCount` never exercised) | **SURVIVED** |
| **SK-U2I-g*** | iOS: first declaration wins | **SURVIVED** |

(* = an extra mutation of mine that the lane did not run.) After all mutations, the lane's `sha256-final.txt` checks
24/24 OK.

**Seams under the per-file lock** (`skeptic/sk-seam.log`, `skeptic/sk-u3-pins.log`). Each seam file was restored
BYTE-EXACT-TO-HEAD and its lock removed; `tools/titan/runs/wave54-lock/` is empty.
- **seam-2:** applied (`417853ce…`), Catalyst "Executed 49 tests, with 0 failures", restored `905d1669…`.
- **seam-1:** applied (`0a92203a…`), Compose compile + PreBreakPipelineTest 26/0 + HyphenateCharacterExtractorTest
  6/0. `javap` of the freshly compiled `ComponentRenderer.class` shows 2 `HyphenateCharacter*` references. Restored
  `da2df0b4…`.
- **seam-3:** 490 tests, 488 pass, 2 skipped (U3b). The patch-borne test file equals the `.staged` copy (`88221750…`).
- **seam-3 + seam-3b:** 490/490 pass. Restored `845a3d26…`, and the test file is removed.

**Apply checks.**
- `git apply --check` on HEAD: all 4 patches CLEAN.
- I built a throwaway repo from HEAD's 4 seam files and applied, in §4 order, L2 seam-1 → L6 seam-1 (its non-seam
  file is absent there, so that one was skipped) → L5 seam-1 → L4 seam-1 → L3 seam-1, seam-2, seam-3, seam-3b. All
  applied cleanly.
- Reverting U3 alone, with U3b on top, is clean. Reverting U3b, then U3, gives back the base `extract-fixture.mjs`
  exactly.

**Census, my own scripts.**
1. **Wire, over all 1435 wave53-final per-test IR.** 14 hyphenate-character declarations in 6 documents. Run end to end
   through the converter (`:converter:run` on `skeptic/sk-converter-in.json` → `skeptic/sk-converter-out.json`), the new reader emits:
   - `""` → `{"type":"string","value":""}`;
   - `"\2022"` → U+2022;
   - `"\00a0\0640"` → U+00A0 U+0640;
   - `/-/`, `-` and `auto` unchanged.

   The output validates against `ir-v2.schema.json` (`{"ok":true}`). U1 wire = 001/002/003/005, which equals
   expectations.
2. **U3 over the gate-flag IR** (`sk-br-census.py`; a different method from the lane's no-flag extractor
   differential). It applies the U3 rule to each br's host `meta.runs`: **54 brs in 18 documents**, the same 18 as
   expectations. The ring-fenced test is not among them. The line-start 20 px host-scope brs number 13 in 5
   documents. Only hyphenate-character-001…004 have a host with its own `font`/`line-height`, so U3b = 12 brs in 4
   documents. (`css-multicol/baseline-005`'s host declares nothing.)
3. **Full-bytes static differential** (`sk-u3-fullbytes.sh`, every line-break height masked; the lane only listed br
   moves):
   - U3: 18 fixtures, 54 brs (20 → 0), **NON-BR changes 0**, refFixtures 22 (non-br 0);
   - U3b: 4 fixtures, 12 brs (20 → 19.2), non-br 0;
   - extraction errors 0.
4. **U+00AD population** (`sk-shy-census.py`): 17 documents, 21 distinct texts. Every one is literally in
   `PreBreakPipelineTest.CORPUS_SHY_TEXTS`, so the walk == count equivalence pin covers the whole default population.
5. **Carrier cross-check.** Captures web 15 / iOS 16 / Android 15 and wire 19 match expectations exactly. So do the
   per-unit reaches: U1 [3,3,2]/4, U2-android [0,0,3], U2-ios [0,5,0], U3 [15,15,15]/18, U3b [4,4,4]/4. Nothing is
   under-reported.

**Other checks.**
- **Verdicts with `cells.mjs` on wave53-final:** every number the note cites matches. Examples: 001 0.935/0.9287/0.9301,
  003 0.8812/0.9337/0.9292, 004 0.9249/0.9134/0.905, 002 0.92/0.9261/0.9223, block-ellipsis-002 P 0.9868/0.9813/0.9884,
  block-ellipsis-004…006 web 0.9737/0.9735/0.9735, clip-path-filter-order 0.8586/0.8578/0.8406, revert-* P.
- **Geometry self-test:** `geometry-gate.py wave53-final --base wave53-open --lanes L3 --self-test` exits 0, with 6
  gating FAIL, 19 control PASS, 7 report FAIL and `self-test: HOLDS`.
- **Web, executed** (the three triplet files copied out, type-only imports adjusted, run under `node --experimental-strip-types`; driver kept as `skeptic/sk-web-run.ts.txt`):
  `{string,""}` → `hyphenateCharacter: '""'`, `•` → `'"•"'`, `" ـ"` → `'" ـ"'`, `auto` → `'auto'`. The lane only
  read this code.
- **Hygiene:** no edits outside the owned paths. The diffs of TypographyAggregate / StyleBuilder / GreedyLineBreaker /
  the test files hold only L3 hunks. No `probe` leftovers. No `.mutate-backup` left behind. coverage-audit prints
  `real: android=16`.

**PNGs I looked at:**
- `look-001`, `look-block-ellipsis-004` (the lane's sheets);
- my own sheets `skeptic/sk-look-003.png`, `sk-look-004.png`, `sk-look-002.png`, `sk-look-revert-val-001.png`;
- the zoom `sk-zoom-001web-002web-002ios.png`.

The described geometry holds for 001/003/004 and block-ellipsis-004: stray blank lines, the iOS lone `-`, `\2022`
painted on web, natives `imple-/menta-`, natives with no stray gap. revert-val-001 is a full green square on all three
platforms. The natives render a text+br-only host as stacked boxes (an InlineRunFold BR is only folded alongside a glyph
member), so 8 text rows still overflow the 100 px box after U3, and the HIGH must-not-move reasoning holds.

## Defects, ranked

**S1 (should-fix) — the hyphenate-character-002 rows predict movers and flips whose pictures stay wrong, and they are
not marked DEGENERATE. The brief's stated reason is contradicted by the capture.** The brief (`:337`) predicts web
0.92 → ≈0.96 because "Chromium's en breaks equal the shys". The lane's note restates the row as "agree".

The wave53-final captures say otherwise:
- **Web:** Chromium's en dictionary does not split `tation`. 002 web shows `im-/ple-/men-/tation`, where 001's shys give
  `ta-/tion`. My band count gives web groups [4,4,4,3] against the ref's [5,4,3,3].
- **iOS:** TextKit emergency-breaks `tati/on` and `izat/ion`, which are not hyphenation positions.
- **Android:** Minikin paints visible hyphens (the logged wall).

U1, U2 and U3 change none of this. After U3 removes the lucky extra blank line on web, groups 2–4 will sit about 19 px
above the ref (the group tops today are −18 / +7 / +43 px against the ref).

So any 002 web or iOS pass, and an Android one, is DEGENERATE by construction. Yet 002 is neither in
`degenerateByConstruction` nor in `stayDegenerateEvenIfPass`. R5's per-gain PNG check is the only backstop.

**Fix:** have the orchestrator add 002 web/ios/android to `stayDegenerateEvenIfPass` via `plan-build.py`, with the
reasons above. The lane note should correct its 002 row. Without the dictionary reason, keep the web row's "≈0.96" as a
score-only read.

**N1 (nit) — `hyphenate-character` is not inherited on either native.** Inherited: yes in css-text-4 §6.3. `Hyphens` is
in the inherited lists (`InheritedText.swift:77`, `ComponentRenderer.kt:214`) but `HyphenateCharacter` is not. A
descendant block of the declaring element therefore paints U+2010 silently on both natives, while web inherits through
the DOM. There is no TODO or breadcrumb. No corpus case: all 14 declarations sit on the text host, and 005's host has no
text.

**N2 (nit) — branches no test reaches** (mutations survived):
- last-declaration-wins on Compose (SK-X1) and iOS (SK-U2I-g);
- the Compose clamp path's `hyphenChar` (SK-X2);
- `SoftHyphenCuts`'s `legacyCount` fallback (SK-X3). For `""` the fallback is always false, so a run the walk cannot
  align would silently not fire, with no breadcrumb;
- the Minikin-dictionary breadcrumb itself is never asserted.

None of these is on the corpus.

**N3 (nit) — `SpentHyphen` strips a trailing author string from any line that ends with it**, even a word that itself
ends with the string and was not cut there (e.g. `a/-/`). Such a line would be judged unbreakable and `.fixedSize`d
instead of TextKit breaking after its `-`. Scoped to author strings; no corpus case.

**N4 (nit) — file sizes and wording.**
- The patch-borne `extract-fixture-br-line-context.test.mjs` is a NEW 264-line file (> 200; mostly verbatim WPT HTML).
- `GreedyLineBreakerTests.swift` goes 221 → 314 (past the ~300 split mark). The plan's own list forced this and the
  note says so.
- The note says the test host JSON was "re-checked byte-for-byte". It is content-verbatim with meta dropped: I checked
  that the minified wire component equals the test literal.

## What I could not check

- **No device picture or probe.** The gating 001/003 iOS floors, the block-ellipsis web gating keys, the U3b GBn
  drift, the iOS/Android U+2022 glyph advance and the "byte-identical" block-ellipsis natives are all device-only. The
  ref measurements suggest the ref's blank line is about 19 px. iOS's text pitch is about 18.8, and its 20 px brs
  currently compensate, so U3b may cost iOS about 0.8 px per group. It is probe-decided, as planned.
- **The renderer side of the seam hunks** (PlaceholderContent with Minikin `softWrap=false`; PlaceholderLabel with
  `.fixedSize`) has no unit test. I proved it compiles in and is green with the pins, nothing more.
- **[W-L3]** (the gate-flag re-extraction + converter over the 30 sections) is an orchestrator window. My gate-flag IR
  census is a model of U3's rule on the recorded wire, not a re-extraction under the bake flags. I read
  `wl3-wire-differential.sh` against `section-runner.sh` `:375 / :416 / :435 / :620`: the steps and flags match.
- **Full suites** (orchestrator sweep).
