TREES: /private/tmp/claude-501/-Users-dranak-Documents-Projects-Style-Converter--claude-worktrees-trusting-bohr-bd6fbf/0c47cad8-074d-4821-bf4c-b5997f23f535/scratchpad/wave55-build-L7

# L7 · instruments — wave-55 builder lane note

Lane worktree (the `TREES:` line above): `git worktree add --detach … 611c9f93`, provisioned before any build (PLAN §0
"Provisioning"): `apps/android-harness/local.properties` and the root `local.properties` copied, `node_modules` symlinked.
Two more gitignored inputs are SYMLINKED from the shared tree so the probes and the census read the real runs and the
frozen refs: `tools/titan/runs` and `tools/wpt` (`.gitignore`'s `tools/wpt/` / `tools/titan/runs/` patterns carry a
trailing slash, so git lists the two symlinks as `??`; no patch names them). Every Swift build passed
`-derivedDataPath <wt>/.dd`. Nothing was committed, checked out, stashed or reset outside the lane worktree; the only
shared-tree path written is this lane dir. No device, no Chromium, no emulator/simulator, no gate script was run.

**What L7 delivers.** Eight patches (U2, U3, U4, U5, U6-compose, U6-swift, U6-web, U6-tooling); U1 is the optional unit
with NO patch (its re-execution left the tracked copy byte-identical). No seam patch (`landing-manifest.json` L7 seams:
none). No corpus picture and no wire byte is predicted to change; every L7 cell is a must-not-move.

Test-count deltas for the single-writer sweep / restamp (orchestrator): tooling `node --test` +11 (wire-differential 9,
label-chrome-check 2; bidi-bake's count is unchanged — S6-2 is one test that gained two rows) · web vitest +9 (1381 →
1390 measured on this worktree: `npm -w runtimes/web run test` → `Tests 1390 passed (1390)`) · Compose +8 · SwiftUI +6
· web-harness 0 · converter 0 · conformance 39/39 unchanged.

## U1 — the flex-gap probe's wave-55 copy (optional unit: NO PATCH)

Paths (tracked at HEAD since a2a37a8e, unchanged): `tools/titan/results/wave55-plan/flex-zero-gap-rules.geometry.py`
(sha256 bd8d5c7f…), `flex-zero-gap-rules.fakes-run.sh` (d92563fa…), `flex-zero-gap-rules.geometry.out.txt` (999f22a7…).
- Re-executed `flex-zero-gap-rules.fakes-run.sh` in the lane worktree twice — under its own `python3` (/usr/bin, 3.9.6)
  and under the pinned `/opt/homebrew/bin/python3.12 -B` — over the 3 runs, the 14 recorded fakes and the 6 new ones.
  Both outputs differ from the committed `.geometry.out.txt` in line 1 ONLY (the timestamp and `HEAD 0d0d3a3d` →
  `HEAD 611c9f93`); every verdict line is byte-identical (`u1/fakes-run.rerun.python3.out.txt`,
  `u1/fakes-run.rerun.python312.out.txt`). The committed output was restored byte-exact (sha256 999f22a7… before and
  after), so the landing's "optional, no patch: files tracked at HEAD" check applies (`land-units.sh` row 1).
- Self-test of L7's registered probes on the run of record (task rule 4):
  `/opt/homebrew/bin/python3.12 -B tools/titan/results/wave55-plan/geometry-gate.py wave54-final --base wave54-open --lanes L7 --self-test`
  → exit 0, `self-test: HOLDS — 0 violation(s)`; 12 keys, all CONTROL, all PASS (the three
  `flex/flex-gap-decorations-033` keys of the wave-55 copy and the nine `web-out-of-flow-hyphen-box` keys); L7 has no
  gating or report key, so its ability to fail is the fake table (`flex-zero-gap-rules.geometry.out.txt`, the copy's
  `GEOMETRY WRONG` on short5 / lshort4 / lshort2 / split1 / moved5 / hshift2 / blue-none ×3) — record
  `geometry-selftest.wave54-final.out.txt`.

## U2 — `tools/titan/wire-differential.{sh,mjs,test.mjs}` (unit-U2.patch)

Paths: `tools/titan/wire-differential.sh` (NEW, mode 100755, 144 lines), `tools/titan/wire-differential.mjs` (NEW, 180),
`tools/titan/wire-differential.test.mjs` (NEW, 173).
- `wire-differential.mjs:classify` — the classifier. Walks the UNION of the PRE and POST section dirs (defect d), orders
  each section by its `tests.list` mapped through the shared stem rule (`safe-name.mjs` `fixtureStem` / `safe`, so the
  file names are split-combined-ir's own), and classifies IDENTICAL / RENUMBERED (strip-equal AND one uniform shift
  equal to the running component-count delta of the changed documents before it — the wave-53 rule, defect e) /
  RENUMBERED-ANOMALOUS (a leak) / CONTENT-CHANGED / ONE-SIDED (a document, a section, or a section's per-test-ir).
  `readStatus` turns any combine / convert / split rc ≠ 0, extract rc or FAIL sets that differ between sides, or 0
  extracted documents into PIPELINE (exit 3); a symmetric FAIL is a NOTE (defect f). `readExpect` refuses an unreadable
  expect line (exit 2: a typo must never read as "no change"). A read that compared nothing is exit 3 (vacuity guard).
  `--no-status` prints `pipeline status NOT READ`. Exit: highest of 3 / 4 / 5 wins, 2 at once.
  One rule beyond the brief: an expected entry whose document or section is ONE-SIDED prints `UNREAD …` instead of
  `MISSING …` (exit 4 already fails the read; counting it again as 5 would hide the cause) — this is what makes the
  brief's "mis-invocation shape → exit 4" hold with the wave-54 expect file (it read 5 without it).
- `wire-differential.sh` — resolves PRE / POST / OUT / EXPECT to absolute physical paths BEFORE any `cd` (defect b; the
  dry run resolves OUT without creating it, `abs_nocreate`); refuses (exit 2) an unresolvable path, PRE == POST, PRE and
  POST in one checkout, either tree being its own checkout or not a checkout top, a tree missing `node_modules`,
  `tools/wpt` or `tools/titan/wpt-buckets.json` (beyond the brief: without the bucket index extract-fixture silently
  drops its lossy and wall tags — post-load extraction changes — so both sides would agree on a NON-gate IR), a missing
  `<run>/sections`, a requested section without `tests.list`, an out dir that already holds `pre/` or `post/`, an OUT
  with whitespace (gradle `--args` splits it). Runs the gate's four steps verbatim per tree and section
  (`section-runner.sh:375/416/435/620`), each rc recorded in `status.tsv` (defect c), the FAIL lines in
  `fail-<tag>-<sec>.txt`; the verdict is the classifier's exit code, passed through. `--classify-only` writes
  `classify-only.<expect>.txt` and never overwrites the run's `classify.txt`. `--run` takes an id (this checkout's
  `tools/titan/runs/<id>`) or a run DIRECTORY — [W-L7a] runs the script from a third throwaway that has no runs/.
  A defect the P7 pin found and fixed during the build: the `tests.list` read loop dropped an unterminated last line.
- Pins (`wire-differential.test.mjs`, node --test, synthetic trees under os.tmpdir()): P1 identical → 0 · P2 expected → 0,
  unexpected → 5 `UNEXPECTED`, expected-but-absent → 5 `MISSING` · P3 uniform +2 after +2 → RENUMBERED 0; non-uniform
  {2,3} → ANOMALOUS 5; uniform but wrong (+1 vs delta 2) → ANOMALOUS 5 · P4 one-sided document → 4; post-only section → 4
  · P5 rc_convert=1 → 3; asymmetric FAIL → 3; symmetric FAIL → 0 + NOTE · P5b the mis-invocation shape → 4, an empty
  read → 3, `--no-status` printed, a bad expect line → 2 · P6 `--dry-run` from another cwd with a relative OUT prints
  `OUT <realpath>` and creates nothing; PRE == POST → 2; PRE = own checkout → 2 · P7 (beyond the brief) the RUN path on
  stub trees (stub extract / combine / gradlew / split): status rows + exit 0; a failing convert on POST names
  `PIPELINE s1: post convert rc 1` (exit 4: POST has no per-test-ir, ONE-SIDED outranks PIPELINE); a reused out dir → 2;
  `--classify-only` with a pre-registered change → 5 and `classify.txt` untouched ([W-L7a] step 3's shape). 9/9 green.
- Mutations (executed on the DELIVERED bytes, bracketed below, log `u2/mutations.out.txt`): M1 exit always 0 → P2 P3 P4
  P5 P5b P7 red · M2 sections from PRE only → P4 red · M3 uniform-shift check dropped → P3 red · M4 absolutization
  dropped (and its absolute-path guard line, which would otherwise catch it first) → P6 dry-run red · M5 own-checkout
  refusal dropped → P6 own-checkout red. Each restored byte-exact, then green.
- Calibration on real data (`u2/calibrate.sh` → `u2/calibration.out.txt`, Chromium-free, gradle-free):
  (1) parked wave-54 trees + the wave-54 expect file → `identical 1397 · content-changed 23 · renumbered 15 · one-sided 0`,
  exit 0 (the 15 renumbered all `shift +6 = the running delta`); (2) counter-suffix deleted from the expect file → exit 5,
  `UNEXPECTED CONTENT-CHANGED css-counter-styles/wpt__css-counter-styles__counter-suffix.json`; (3) the mis-invocation
  shape → exit 4, with the wave-54 expect file (38 UNREAD) and with an empty one.

## U3 — `ab-arms55.sh` + self-test: reviewed, AMENDED (unit-U3.patch)

Paths: `tools/titan/results/wave55-gate/ab-arms55.sh` (319 lines, +2), `tools/titan/results/wave55-gate/ab-arms55.selftest.sh`
(248, +15), `tools/titan/results/wave55-gate/ab-arms55.selftest.out.txt` (the re-run record).
- Review of the delivered runner (sha256 5cd6fccb…, D19): its self-test HOLDS on the base bytes in the lane worktree
  (`u3/selftest.base-bytes.out.txt`, every check as required). Findings:
  - **R1 (amended).** Step 8 ran `ab-diff.mjs` and discarded its exit code; step 9 only checked the read-out file is
    NON-EMPTY — and a failing ab-diff writes its error into it (`no run dir for <id>`, exit 2, measured). So an arm whose
    read-out failed printed `records verified` and could exit 0: an instrument that cannot fail. Amendment: the rc is
    kept (`ADRC[i]`), and step 9 fails (exit 7) on a non-zero one, naming it. The header's exit-7 line says so; `--help`
    still prints the whole header (2-66).
  - R2 (noted, not amended). `--run` with an `--against` run that does not exist yet is only NOTED (the dry run's
    "has not run yet" line); the arms would run and the read-out fail — now loudly, exit 7, with every arm's capture and
    hash block kept, so the read-out can be re-run by hand. Refusing it in `--run` needs a stub against-run in four
    self-test repos; not done.
  - R3 (noted). `on_signal` does not truncate an UNTRACKED build-hashes.txt; this wave's file is tracked.
  - R4 (noted). Exit 7 from step 9 masks an earlier 1 / 3 (still non-zero).
  - Everything else reviewed as the toolkit stage describes it: records written once after the last verified restore,
    `--against` corpus-launch blocks, cumulative revert check, untracked-code refusal, the dry run writing nothing.
- Self-test additions: T6c (a stub ab-diff that exits 2 on drop-V, `STUB_ABDIFF_FAIL`) → exit 7, the failure named,
  both blocks once, tree restored; mutant M-abdiff (the step-9 rc check deleted) → the same failed read-out exits 0 —
  RED. Re-run on the amended bytes: `ab-arms55.selftest: every check as required (mutants M-L18, M-per-arm, M-dry,
  M-abdiff RED)`, 0 `NOT as required`. The record's header cites runner sha256 021c7845…; it was run WITHOUT a scratch
  argument (a `mktemp -d` under the system temp dir), so — unlike the base record — it carries no scratchpad path.
- Consequence for the orchestrator: `../wave55-gate/toolkit-proofs.txt`, `ab-arms55.against-proof.out.txt` and
  `ab-arms55.revert-check-proof.out.txt` cite the BASE runner (5cd6fccb…). The amendment touches steps 8-9 only (not the
  preconditions those proofs exercise); re-run them on the landed bytes if their citation must match (window requests).

## U4 — spec 03 §4.1 renderer-latitude paragraph + the four citations (+ U4c, D10) (unit-U4.patch)

Paths: `schema/spec/03-children.md` (one unnumbered paragraph after rule 7, before `## 5.`; 263 lines),
`runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts` (:32 comment; 191 lines), `runtimes/web/src/renderer/InlineRuns.ts`
(:52 / :167 comments, :174 the warn STRING; 185 lines), `runtimes/web/tests/renderer/InlineRunsLatitudeCitation.test.ts`
(NEW, 80 lines), `apps/web-harness/src/ui/ComposedRootSeparator.ts` (U4c: one TODO line, D5 the separator-font limit;
110 lines).
- The paragraph is the brief's §4 U4 text, each clause re-checked against code on this tree: web predicate
  `InertOutOfFlowWordJoin.ts:98-120`, Compose `InertOutOfFlowMember.kt:89-114` (`admits`), `TEXT_MEMBER_TAGS`
  `InlineRunFold.kt:177`, the Compose drop `InlineRunFold.kt:460-467` (counted `droppedOutOfFlow`), the field
  `InlineRunFold.Folded.droppedOutOfFlowMembers` (`:165`), the Compose breadcrumb `ComponentRenderer.kt:3905-3906`, web
  `RunsPlacement.joinedOutOfFlowMembers` (`InlineRuns.ts:58`). Not a freeze event (`05-versioning.md` "Change process":
  no emitted byte changes); `node schema/conformance/run.mjs` → all 39 documents valid, no golden or schema file touched.
- The warn string now ends `reader deviation from spec 03 §4.1 rule 2 (renderer latitude)`; the wave-54 test (i)
  (`InertOutOfFlowWordJoin.test.tsx`) matches a prefix and stays green.
- Pins (`InlineRunsLatitudeCitation.test.ts`, on hyphens-out-of-flow-002 box 4 VERBATIM from wave54-final, sha256
  0f1d9251…): (a) the one `[InlineRuns]` breadcrumb ends `spec 03 §4.1 rule 2 (renderer latitude)` (and the member really
  joined: 1); (b) §4.1 has exactly the numbered rules [1..7], the paragraph sits after rule 7 once, says
  `reader-side exception to rule 2 (no wire change)` and names both counters.
- Mutations (bracketed below, log `u4/mutations.out.txt`): U4-a the warn string back to "rule 1" → (a) red · U4-b1 the
  paragraph moved in as a numbered rule 3 (rules renumbered) → (b) red (`[1,2,3,4,6,7,8]`) · U4-b2
  `droppedOutOfFlowMembers` renamed in the spec → (b) red.
- Captures: none can move — comments, one console string (the manifest records no console output), one prose paragraph
  and one harness comment (census (2): every executed U4 diff is COMMENTS ONLY or STRING LITERALS ONLY).

## U5 — `mask-image` / `mix-blend-mode` in the marker paint decline (unit-U5.patch; lands only behind [W-L7a])

Paths: `tools/titan/bidi-marker-paint.mjs` (169 lines, +10), `tools/titan/bidi-bake.test.mjs` (1403, +1 — the disclosed
0(d) size exception).
- `MARKER_PAINT_INITIAL` gains `maskImage: 'none'` (css-masking-1 #the-mask-image) and `mixBlendMode: 'normal'`
  (compositing-1 #mix-blend-mode), each commented; the group-effect list in the header names both; the `TODO(S6 residue)`
  paragraph is replaced by the census sentence and the landing condition ([W-L7a]).
- `bidi-bake.test.mjs` S6-2 `effects` gains `maskImage: 'linear-gradient(rgb(0, 0, 0), rgba(0, 0, 0, 0))'` and
  `mixBlendMode: 'multiply'`; its key-equality line forces both into every decline row (`::marker` and `li`). The
  synthetic chains (`CS_CHAIN`, the fake `getComputedStyle`) spread `MARKER_PAINT_INITIAL`, so every other test reads the
  new keys at their initial spelling. `node --test tools/titan/bidi-bake.test.mjs` → 68 pass, 6 skipped (the Chromium
  `proving:` tests, skipped at base too), 0 fail.
- Mutation (bracketed below, log `u5/mutations.out.txt`): `markerPaintLoss` skips the two keys → S6-2 red, restore green.
- Its only risk is R1 (the spellings): Chromium not exposing `maskImage` / `mixBlendMode` on `::marker`, or spelling the
  initials otherwise, declines EVERY baked marker (counter-suffix 29 → 23 components). Not measurable on a builder lane:
  [W-L7a] below decides, and U5 lands only on its exit 0 (C3: U5's claim is wire-only).

## U6-compose — pins C1-C7 (unit-U6-compose.patch)

Paths: `runtimes/compose/src/test/java/com/styleconverter/runtime/typography/wrapping/HyphenateCharacterExtractorTest.kt`
(+1 test, 110 lines), NEW `…/typography/wrapping/PreBreakPipelineClampHyphenTest.kt` (55), NEW
`…/typography/wrapping/SoftHyphenCutsTest.kt` (34), NEW `…/layout/position/OutOfFlowContainingBlockBranchesTest.kt` (67).
- C1 `[{string "/-/"}, {string "•"}]` → `HyphenateCharacterConfig("•")`. C2 (string DERIVED by running, as the brief
  asked): `ab cd<SHY>ef ghijkl<SHY>mnop`, 70 px of 10-px cells, `hyphenChar "•"` → unclamped fires
  `ab cdef\nghijkl•\nmnop`; `clampLines = 1` → `ab cd•…`. C3 `took("im<SHY>ple", ["im‐ple","x"], "‐")` true AND
  `walkAligns` false. C4 Filter `{"url":"#f"}` establishes. C5 Contain `"paint"` (string) establishes. C6 Contain
  `{"paint":{"bad":1}}` → false + `CONTAIN_DECODE_BREADCRUMB` — DEVIATION: the brief's `[{"bad":1}]` cannot reach the catch
  (PerformanceExtractor's array branch drops non-primitive elements with `as? JsonPrimitive`, so nothing throws;
  code-traced, not executed); the object branch's `jsonPrimitive` throws `IllegalArgumentException`. C7 the VERBATIM
  component `content-visibility-auto-shared-element__3__2__0-092` (wave54-final css-view-transitions per-test-ir, sha256
  fae6d09f…) → not an establisher, `IMPLIED_CONTAINMENT_BREADCRUMB` set. `@Before PropertyTracker.reset()`.
- Verbatim note: C4-C6 are synthetic leaves BY NECESSITY — at wave54-final the corpus carries 0 `Filter` object leaves
  and only array-form `Contain` (105 leaves, `["PAINT"]` ×50 …); that is why these branches were never pinned.
- Mutations (bracketed below, log `u6/mutations.compose.out.txt`), each red on exactly its pin, then green: C1
  `lastOrNull` → `firstOrNull` · C2 `hyphenChar = hyphenChar` dropped from the clampLines call · C3 `?: legacyCount(…)` →
  `?: false` · C4 `is JsonObject -> false` · C5 PerformanceExtractor's JsonPrimitive branch returns emptySet() · C6 the
  catch's markUnhandled deleted · C7 the IMPLIED_TYPES markUnhandled deleted.

## U6-swift — pins S1-S6 (unit-U6-swift.patch)

Paths: NEW `runtimes/swiftui/Tests/StyleConverterRuntimeTests/HyphenateCharacterCascadeTests.swift` (36), NEW
`runtimes/swiftui/Tests/StyleConverterRuntimeTests/OutOfFlowContainingBlockBranchesTests.swift` (68).
- S1 two declarations → the later (and reversed → the other). S2 Contain `"paint"` string → true. S3 Filter
  `{"url":"#f"}` → true. S4 `{"paint":{"bad":1}}` → false + decode crumb already seen. S5 the verbatim
  content-visibility component (as C7) → false + implied-containment crumb seen. S6 ABSOLUTE + backdrop-filter → false +
  positioned-establisher crumb seen (never asserted on Swift before). `setUp` → `PropertyTracker._resetForTests()`;
  "seen" = `!PropertyTracker.logOnce(key:message:)`.
- Mutations (bracketed below, log `u6/mutations.swift.out.txt`): S1 first-wins (`if cfg == nil`) · S2 the string branch
  answers false · S3 the object branch of filterListIsUsed → false · S4 / S5 / S6 each logOnce call deleted — each red
  (`** TEST FAILED **`, rc 65) on its own test, then green.

## U6-web — pins W1-W6 (unit-U6-web.patch)

Path: NEW `runtimes/web/tests/renderer/InertOutOfFlowWordJoin.branches.test.tsx` (85; the wave-54 file is exactly 200).
- On box 4 VERBATIM (as U4), one fact patched per pin: control joins 1 · W1 a child / own runs / own decorations →
  joined 0 · W2 FIXED → 1 · W3 `[hi, M, gh, "way rest"]` → `[high, way, M, " rest"]` · W4 alpha 0.3 → 0 · W5
  `srgb.a: "0"` → 1 · W6 `Position {"keyword":"ABSOLUTE"}` → 1. `tsc --noEmit` (src + tests) clean.
- Mutations (bracketed below, log `u6/mutations.web.out.txt`), in `InertOutOfFlowWordJoin.ts`: W1 structural refusal
  deleted · W2 FIXED dropped · W3 the pushText continuation disabled · W4 `a < 0.5` · W5 the decimal-string branch
  dropped · W6 keywordOf ignores objects — each turns exactly its own test red.

## U6-tooling — pins T1-T2 (unit-U6-tooling.patch)

Path: NEW `tools/visual/label-chrome-check.test.mjs` (51).
- T1 the brief's fixture (`P1:{Box:{Kid}}`, `P2:{Box}`), "container", all three PNGs committed → red, `is a leaf, entry
  says container`; control: both Box containers → verified. T2 synthetic 390×40 ground buffers: Android paint on HALF
  the would-be glyph pixels → no `(iv)`; control: ALL of them → `(iv) Android: …`.
- Mutations (bracketed below, log `u6/mutations.tooling.out.txt`): T1 `hits.slice(0, 1)` → T1 red · T2 `lit > 0` → T2 red.

## Not pins (deferred, unchanged from the brief §4)
L3 N1 (hyphenate-character inheritance — a seam behaviour change), the SK-X3 residue, L6 note-D3, wave-54 L7 S3 and
X4 / N3; S2 is closed in the records; the `ContainingBlock[dark-stage-content-box-bands]` crumb pin is L2's (C8).

## Census (my own; `census.mjs` → `census.out.txt`, run on the DELIVERED patches)
Written independently of the brief's `instruments.census.mjs` and run on the eight DELIVERED patches
(`node tools/titan/results/wave55-instruments/census.mjs` → `census.out.txt`):
- (1) gate reach: my own crawl of `gate-driver.sh`, `section-runner.sh`, `test-all.sh` through `node|bash|sh|python3`
  references and static / dynamic imports reaches 45 scripts (the brief's crawl: 43 — a different crawl, same verdicts on
  L7's paths). Of the 21 touched paths exactly one gate TOOL is executed: `tools/titan/bidi-marker-paint.mjs` (U5);
  three runtime / harness sources are built into every web capture (U4: `InlineRuns.ts`, `InertOutOfFlowWordJoin.ts`,
  `ComposedRootSeparator.ts`); the rest are tests, prose, records or tools no gate script names (`wire-differential`,
  `ab-arms55`, `flex-zero-gap-rules`: named by none).
- (2) what those executed bytes are: `ComposedRootSeparator.ts`, `InertOutOfFlowWordJoin.ts` COMMENTS ONLY;
  `InlineRuns.ts` STRING LITERALS ONLY (the console.warn text); `bidi-marker-paint.mjs` CODE (+2 map entries).
- (3) U5's radius on wave54-final (manifest `bidiBaked`, WPT sources from `tools/wpt/`): 17 bidi-baked documents, 0
  `marker-not-baked` stamps; 4 have a list item in their source (counter-suffix and css3-counter-styles-101/-102/-103) —
  the [W-L7a] spelling radius; 0 of them mention mask / mix-blend-mode; over ALL 1435 sources, 0 have both a list item and
  mask / mix-blend-mode — so the unit's intended behaviour change reaches no corpus document.
- (4) carriers: mine `captures {web: [], ios: [], android: []} · wire []` = `expectations.json` lanes["L7-instruments"]
  `captureCarriers` / `wireCarriers`; delivered unit patches U2 U3 U4 U5 U6-compose U6-swift U6-web U6-tooling, U1 optional
  with no patch, no unknown unit. **Census delta: IDENTICAL** — no plan regeneration needed.
- Must-not-move: the 42 named cells of `watchlist.txt` block L7 (below); counter-suffix ios / android and L3's three
  Android carriers are other lanes' (C3), read with the union carrier set.

## Predictions (wave54-final → the closing gate, read against wave55-open; `expectations.json` L7 must-not-move, 42 cells)
Every row: identical (capture and per-test IR byte-identical to wave55-open, |Δ| = 0; the rule-2 −0.002 band is the
alarm), confidence HIGH — U1 / U2 / U3 / U6 are off the gate route, U4 changes comments and a console string, U5 is
wire-identical by [W-L7a] or does not land. Flips 0 · lost 0 · newly measured / unmeasured-now 0.
- `wave54-final css-counter-styles/arabic-indic/css3-counter-styles-101 android P 0.9736` → identical · HIGH · U5
- `wave54-final css-counter-styles/arabic-indic/css3-counter-styles-101 ios P 0.9943` → identical · HIGH · U5
- `wave54-final css-counter-styles/arabic-indic/css3-counter-styles-101 web P 0.9932` → identical · HIGH · U5
- `wave54-final css-counter-styles/arabic-indic/css3-counter-styles-102 web P 0.9791` → identical · HIGH · U5
- `wave54-final css-counter-styles/arabic-indic/css3-counter-styles-103 android P 0.9913` → identical · HIGH · U5
- `wave54-final css-counter-styles/arabic-indic/css3-counter-styles-103 ios P 0.9978` → identical · HIGH · U5
- `wave54-final css-counter-styles/arabic-indic/css3-counter-styles-103 web P 0.9986` → identical · HIGH · U5
- `wave54-final css-counter-styles/counter-suffix web P 1` → identical · HIGH · U5
- `wave54-final css-gaps/flex/flex-gap-decorations-033 android P 1` → identical · HIGH · U1 (control key)
- `wave54-final css-gaps/flex/flex-gap-decorations-033 ios P 0.9993` → identical · HIGH · U1 (control key)
- `wave54-final css-gaps/flex/flex-gap-decorations-033 web P 1` → identical · HIGH · U1 (control key)
- `wave54-final css-text/bidi/bidi-lines-001 android P 0.9629` → identical · HIGH · U5
- `wave54-final css-text/bidi/bidi-lines-001 ios P 0.9926` → identical · HIGH · U5
- `wave54-final css-text/bidi/bidi-lines-001 web P 0.9998` → identical · HIGH · U5
- `wave54-final css-text/bidi/bidi-lines-002 android P 0.9818` → identical · HIGH · U5
- `wave54-final css-text/bidi/bidi-lines-002 ios P 0.989` → identical · HIGH · U5
- `wave54-final css-text/bidi/bidi-lines-002 web P 0.993` → identical · HIGH · U5
- `wave54-final css-text/bidi/bidi-tab-001 ios P 0.9829` → identical · HIGH · U5
- `wave54-final css-text/bidi/bidi-tab-001 web P 0.9978` → identical · HIGH · U5
- `wave54-final css-text/boundary-shaping/boundary-shaping-009 android f 0.7531` → identical · HIGH · U5
- `wave54-final css-text/boundary-shaping/boundary-shaping-009 ios f 0.8919` → identical · HIGH · U5
- `wave54-final css-text/boundary-shaping/boundary-shaping-009 web f 0.9482` → identical · HIGH · U5
- `wave54-final css-text/hyphens/hyphenate-character-005 ios f 0.9428` → identical · HIGH · U5
- `wave54-final css-text/hyphens/hyphenate-character-005 web P 0.974` → identical · HIGH · U5
- `wave54-final css-text/hyphens/hyphens-out-of-flow-001 web P 1` → identical · HIGH · U4 (control key)
- `wave54-final css-text/hyphens/hyphens-out-of-flow-002 android P 0.9943` → identical · HIGH · U4 (control key)
- `wave54-final css-text/hyphens/hyphens-out-of-flow-002 ios P 0.9971` → identical · HIGH · U4 (control key)
- `wave54-final css-text/hyphens/hyphens-out-of-flow-002 web P 1` → identical · HIGH · U4 (control key)
- `wave54-final css-writing-modes/bidi-plaintext-br-001 android f 0.9482` → identical · HIGH · U5
- `wave54-final css-writing-modes/bidi-plaintext-br-001 ios P 0.9572` → identical · HIGH · U5
- `wave54-final css-writing-modes/bidi-plaintext-br-001 web P 1` → identical · HIGH · U5
- `wave54-final selectors/dir-selector-change-003 android P 0.9994` → identical · HIGH · U5
- `wave54-final selectors/dir-selector-change-003 ios P 0.9996` → identical · HIGH · U5
- `wave54-final selectors/dir-selector-change-003 web P 1` → identical · HIGH · U5
- `wave54-final selectors/dir-selector-change-004 android P 0.9944` → identical · HIGH · U5
- `wave54-final selectors/dir-selector-change-004 ios P 0.9958` → identical · HIGH · U5
- `wave54-final selectors/dir-selector-change-004 web P 1` → identical · HIGH · U5
- `wave54-final selectors/dir-style-02a ios f 0.9455` → identical · HIGH · U5
- `wave54-final selectors/dir-style-02a web P 1` → identical · HIGH · U5
- `wave54-final selectors/dir-style-03a android P 0.9974` → identical · HIGH · U5
- `wave54-final selectors/dir-style-03a ios P 0.9986` → identical · HIGH · U5
- `wave54-final selectors/dir-style-03a web P 1` → identical · HIGH · U5
The control keys that read the first three groups at the gate: `flexgap` (flex-gap-decorations-033 web / ios / android,
GEOMETRY OK ×3 at wave54-final) and `web-out-of-flow-hyphen-box` (hyphens-out-of-flow-001/-002, hyphens-span-002 ×3,
GEOMETRY OK ×9). Looked at: `look/flex-gap-decorations-033.wave54-final.png` (two blue row rules across, red column
segments interrupted by them, on ref / web / ios / android; iOS saturated colours), `look/counter-suffix.wave54-final.png`
(web = ref; iOS rows 3-4 read `.א foo` / `.ב bar` against the ref's `א. foo` — L5's 0(b)/(j) residue, not L7's; the
CJK rows sit slightly left on both natives), `look/hyphens-out-of-flow-002.wave54-final.png` (every orange box `high-` /
`way` on all four) — the pictures the brief describes; none of them is L7's to change.

## ORCHESTRATOR WINDOW REQUESTS

Placeholders: `<SH>` the shared tree root, `<S>` an orchestrator scratch dir outside it, `<LD>` =
`<SH>/tools/titan/results/wave55-instruments`. L7 needs NO device window of its own (stage1Decision: none).

**[W-L7a] — Chromium + converter, no device; BEFORE the landing (PLAN §8 step 5, D12), no gate running on the host.**
Decides whether L7 U5 lands at its manifest place (seq 9) or under `land-units.sh --hold L7:U5` (C3: U5's claim is
wire-only). Any change to either patch's bytes later re-runs this window.
1. Record the two patches the window proves (must equal this note's `patch:` lines):
   `shasum -a 256 <LD>/unit-U2.patch <LD>/unit-U5.patch`
2. Three throwaways at HEAD (§0 "Worktree names"), provisioned:
   ```
   cd <SH> && H=$(git rev-parse HEAD)
   for w in pre post diff; do git worktree add --detach <S>/wave55-wl7a-$w "$H"; done
   for w in pre post; do T=<S>/wave55-wl7a-$w; ln -s <SH>/node_modules $T/node_modules; ln -s <SH>/tools/wpt $T/tools/wpt; ln -s <SH>/tools/titan/wpt-buckets.json $T/tools/titan/wpt-buckets.json; cp <SH>/local.properties $T/; done
   git -C <S>/wave55-wl7a-post apply <LD>/unit-U5.patch
   git -C <S>/wave55-wl7a-diff apply <LD>/unit-U2.patch
   ```
   Expect: three `HEAD is now at …` lines, both `git apply` silent (exit 0).
3. Step 1 — the CDP reads (Chromium):
   ```
   node <LD>/wl7a-marker-paint-probe.mjs
   node <S>/wave55-wl7a-post/tools/titan/results/wave54-rtl-marker-bake/marker-probe.mjs
   ```
   Expect: the first ends `WL7A MARKER PAINT: every value none / normal`, exit 0 (every `<li>` of counter-suffix, its
   `::marker` and each ancestor below `<body>`: `maskImage="none" mixBlendMode="normal"`; the script is NEW and was NOT
   executed on the lane — Chromium is not a lane tool; its page load is marker-probe.mjs half B verbatim). The second is
   the wave-54 [W1] probe run FROM THE POST TREE, i.e. the real bake path WITH U5: `PASS baked — 2 roots, 10 runs` and
   last line `MARKER PROBE: ALL PASS`, exit 0 (a declined marker would read `… roots, 4 runs` and FAIL).
4. Step 2 — the wire differential (Chromium + converter, ~12 section pipelines):
   ```
   : > <S>/wl7a-expect-empty.txt
   bash <S>/wave55-wl7a-diff/tools/titan/wire-differential.sh <S>/wave55-wl7a-pre <S>/wave55-wl7a-post <SH>/tools/titan/runs/wave55-wl7a --expect <S>/wl7a-expect-empty.txt --run <SH>/tools/titan/runs/wave55-open --sections css-anchor-position,css-backgrounds,css-counter-styles,css-text,css-writing-modes,selectors
   ```
   Expect: exit 0; `status.tsv` 12 rows `<tag> <sec> 0 0 0 0 extracted=48 fail=0`; last two lines
   `identical 288 · content-changed 0 · renumbered 0 · one-sided 0 · renumbered-anomalous 0` and
   `verdict: exit 0 — nothing unexpected` (6 sections × 48 documents; no NOTE: the corpus's one symmetric extract FAIL is
   in css-pseudo, not in these sections).
5. Step 3 — the instrument fails on this real run when it must:
   ```
   printf 'CONTENT-CHANGED css-counter-styles/wpt__css-counter-styles__counter-suffix.json\n' > <S>/wl7a-expect-cs.txt
   bash <S>/wave55-wl7a-diff/tools/titan/wire-differential.sh <S>/wave55-wl7a-pre <S>/wave55-wl7a-post <SH>/tools/titan/runs/wave55-wl7a --expect <S>/wl7a-expect-cs.txt --run <SH>/tools/titan/runs/wave55-open --sections css-anchor-position,css-backgrounds,css-counter-styles,css-text,css-writing-modes,selectors --classify-only
   ```
   Expect: exit 5, `MISSING CONTENT-CHANGED css-counter-styles/wpt__css-counter-styles__counter-suffix.json (expected,
   not observed)`, written to `classify-only.wl7a-expect-cs.txt.txt` beside the untouched `classify.txt`.
6. Records (named paths, after the run): the two probe outputs and `status.tsv`, `classify.txt`,
   `classify-only.wl7a-expect-cs.txt.txt`, `fail-*.txt` of `<SH>/tools/titan/runs/wave55-wl7a/` copied as `.txt` under
   `tools/titan/results/wave55-gate/wl7a/` with the step-1 sha256 pair; then `git worktree remove --force` the three
   throwaways.
   **Decision:** steps 3, 4 and 5 exactly as expected → U5 lands at seq 9. Anything else (a FAIL value, a declined
   marker, any CONTENT-CHANGED / RENUMBERED / ONE-SIDED / PIPELINE line, a step-5 exit other than 5) → `--hold L7:U5`,
   and the cause goes into PLAN §10.

**Stage 1 (`wave55-pre`) / stage 2 (`wave55-probe`).** Nothing of L7's: no stage-1 section; at stage 2 its three
`flex/flex-gap-decorations-033` CONTROL keys (css-gaps) and its css-text / css-counter-styles must-not-move rows are read
with the union carrier set. Expect the three keys `→ GEOMETRY OK` (they are today, `geometry-selftest.wave54-final.out.txt`);
a FAIL there is a leak of another lane's unit (rule 5 / R4), never an L7 revert — no L7 unit is on that path.

**The A/B arms (L4 probe-time `drop-L4-U2`, closing `drop-NB-ios` / `drop-L6-U2`) — the runner is L7 U3, AMENDED.**
Before the first arm (PLAN §8 step 6), on the landed bytes:
`bash tools/titan/results/wave55-gate/ab-arms55.selftest.sh` (no argument: its scratch is a `mktemp -d` outside every
checkout) → expect last line `ab-arms55.selftest: every check as required (mutants M-L18, M-per-arm, M-dry, M-abdiff
RED)`, exit 0, and a header citing the sha256 of `ab-arms55.sh` as landed (021c7845… if U3 lands as delivered).
Decision: an arm runs only on a runner whose record cites its bytes. Optional, records only:
`bash tools/titan/results/wave55-gate/ab-arms55.against-proof.sh` and `… ab-arms55.revert-check-proof.sh` (their records
cite the base runner 5cd6fccb…; the amendment does not touch the preconditions they prove). New behaviour to read: an
arm whose `ab-diff.mjs` fails now ends `RECORDS NOT VERIFIED: ab-diff.mjs exited <n> for <arm> …`, exit 7 (was exit 0
with the error text as its "read-out"); its capture and hash block are kept, so re-run the read-out by hand:
`node tools/titan/results/wave52-gate/ab-diff.mjs wave55-ab-<arm> <against> --threshold 0.002 --platforms <p>`.

**L6 [W1].** Not L7's.

## Suites run on the lane worktree (focused; the plan's L7 "may run" list)
- `node --test tools/titan/wire-differential.test.mjs tools/titan/bidi-bake.test.mjs tools/visual/label-chrome-check.test.mjs`
  → tests 85 · pass 79 · skipped 6 (bidi-bake's Chromium `proving:` tests, skipped at base too) · fail 0.
- `npm -w runtimes/web run test` → `Test Files 96 passed (96)`, `Tests 1390 passed (1390)`; `tsc --noEmit` (src + tests) clean.
- `apps/web-harness`: `npx vitest run tests/ui/ComposedRootSeparator.test.ts tests/ui/ComposedRootSeparatorWire.test.tsx`
  → 14 passed (U4c's comment line).
- `node schema/conformance/run.mjs` → all 39 documents valid; no golden, schema or converter file touched.
- Compose `:runtime:testDebugUnitTest` over HyphenateCharacterExtractorTest (7), PreBreakPipelineClampHyphenTest (2),
  SoftHyphenCutsTest (1), OutOfFlowContainingBlockBranchesTest (4) → 14 / 14, BUILD SUCCESSFUL (JUnit XML read).
- Catalyst `xcodebuild test … -derivedDataPath <wt>/.dd` over HyphenateCharacterCascadeTests (1) and
  OutOfFlowContainingBlockBranchesTests (5) → `** TEST SUCCEEDED **`, 6 tests, 0 failures.
- `bash tools/titan/results/wave55-gate/ab-arms55.selftest.sh` → every check as required (4 mutants RED).
- `geometry-gate.py wave54-final --base wave54-open --lanes L7 --self-test` → HOLDS (12 controls PASS).
Not run (single-writer rule / not in the list): the full converter, Compose, Swift, web-harness and tooling sweeps;
`land-units.sh --check` (orchestrator).

## Deviations from the brief / plan, and process notes (all disclosed)
- U2: beyond the brief — the UNREAD rule (expected entries in one-sided scope), the vacuity guard (exit 3 on nothing
  compared), the expect-line refusal (exit 2), the `wpt-buckets.json` prerequisite, `--run <dir>`, the out-dir reuse
  refusal, the classify-only output name, and pin P7 (the run path on stub tools). The brief's §2.2 "symmetric FAIL" rule
  and every listed refusal / exit code are as specified.
- U3: the plan expected a review "and a patch only for amendments"; R1 is an amendment, so U3 ships a patch (the
  optional row then lands as a normal unit). The base self-test record carried a scratchpad path; the re-run record does not.
- U6 C6 / S4: input `{"paint":{"bad":1}}` instead of the brief's `[{"bad":1}]`, which cannot reach the catch (traced in
  PerformanceExtractor's array branch; not executed). C2: the brief left the string to be derived — derived by running.
- U4: `InlineRuns.ts` comment blocks re-wrapped where the citation lengthened them (comment bytes only).
- Mutation records: the first five U2 pairs were VOID (rc 127, the invoking zsh passed the test command as one word —
  each restored, post == pre); the first U4-a pair ran but kept no failing-test line; the U2 set was re-executed twice,
  the last time on the delivered bytes. Every MUTATING line has its RESTORED pair with post == pre.
- Scratch hygiene: this run's lanes share one session scratchpad; L7's mutation sources first sat in its shared `mut/`
  dir (no collision observed: each mutation asserted its own anchor and turned exactly its own pin red), then moved to an
  L7-private dir; copies are in `mutations-src/`. A mistyped command created an EMPTY `~/.zshenv.l7` in the home dir; it
  was removed at once (zsh never reads that name).
- Scratchpad pointers: none in any patch (unit-U3.patch REMOVES one — the base record's scratch path) or in any lane-dir
  record except this note's `TREES:` line; the Swift mutation log's absolute worktree paths were rewritten to `<wt>`.

## revertStatesCompiled (PLAN §0 "Revert states compile"; `revert-states.sh <wt>` → `revert-states.out.txt`)
Every L7 revertOrder chain is the unit alone, so the states are FULL and FULL − U for each unit (U1: no patch, so FULL − U1
= FULL). Each state was materialised on the lane worktree from 611c9f93 (`git checkout -- .`, `git clean` of the lane's
11 new paths only, `git apply` of the kept patches in landing order) and compiled + focused-tested: tooling node --test,
the U3 runner's syntax (its self-test in FULL and FULL − U3), web `tsc --noEmit` + vitest, schema conformance, Compose
`:runtime:testDebugUnitTest` (FULL − U6-compose: the base HyphenateCharacterExtractorTest + OutOfFlowContainingBlockTest),
Catalyst xcodebuild where the runtimes/swiftui tree differs from the last one built (FULL, FULL − U6-swift with the base
OutOfFlowContainingBlockTests, FULL − U6-web back on the full tree); the other states print the Swift tree hash equal to
the last built one. Compose "UP-TO-DATE" = Gradle's content-hash check found the same inputs as its last successful
execution. The run ended in FULL and every owned file's sha256 equals its pre-cut value (21 / 21 OK).
revertStatesCompiled: FULL: tooling[# pass 79 # fail 0 rc 0] U3-syntax[rc 0] U3-selftest[ every check as required (mutants M-L18, M-per-arm, M-dry, M-abdiff RED rc 0] web-tsc[rc 0] web[Tests 21 passed (21)rc 0] schema[39 ok, rc 0] compose[compileDebugUnitTestKotlin UP-TO-DATE testDebugUnitTest UP-TO-DATE BUILD SUCCESSFUL rc 0; 4 classes] swift[tree a86501f57ca00d7c built:  Executed 6 tests, with 0 failures (0 unexpected) ** TEST SUCCEEDED ** rc 0]
revertStatesCompiled: FULL-U1: tooling[# pass 79 # fail 0 rc 0] U3-syntax[rc 0] web-tsc[rc 0] web[Tests 21 passed (21)rc 0] schema[39 ok, rc 0] compose[compileDebugUnitTestKotlin UP-TO-DATE testDebugUnitTest UP-TO-DATE BUILD SUCCESSFUL rc 0; 4 classes] swift[tree a86501f57ca00d7c = the last built tree, byte-identical]
revertStatesCompiled: FULL-U2: tooling[# pass 70 # fail 0 rc 0] U3-syntax[rc 0] web-tsc[rc 0] web[Tests 21 passed (21)rc 0] schema[39 ok, rc 0] compose[compileDebugUnitTestKotlin UP-TO-DATE testDebugUnitTest UP-TO-DATE BUILD SUCCESSFUL rc 0; 4 classes] swift[tree a86501f57ca00d7c = the last built tree, byte-identical]
revertStatesCompiled: FULL-U3: tooling[# pass 79 # fail 0 rc 0] U3-syntax[rc 0] U3-selftest[ every check as required (mutants M-L18, M-per-arm, M-dry RED) rc 0] web-tsc[rc 0] web[Tests 21 passed (21)rc 0] schema[39 ok, rc 0] compose[compileDebugUnitTestKotlin UP-TO-DATE testDebugUnitTest UP-TO-DATE BUILD SUCCESSFUL rc 0; 4 classes] swift[tree a86501f57ca00d7c = the last built tree, byte-identical]
revertStatesCompiled: FULL-U4: tooling[# pass 79 # fail 0 rc 0] U3-syntax[rc 0] web-tsc[rc 0] web[Tests 19 passed (19)rc 0] schema[39 ok, rc 0] compose[compileDebugUnitTestKotlin UP-TO-DATE testDebugUnitTest BUILD SUCCESSFUL rc 0; 4 classes] swift[tree a86501f57ca00d7c = the last built tree, byte-identical]
revertStatesCompiled: FULL-U6-compose: tooling[# pass 79 # fail 0 rc 0] U3-syntax[rc 0] web-tsc[rc 0] web[Tests 21 passed (21)rc 0] schema[39 ok, rc 0] compose[compileDebugUnitTestKotlin testDebugUnitTest BUILD SUCCESSFUL rc 0; 2 classes] swift[tree a86501f57ca00d7c = the last built tree, byte-identical]
revertStatesCompiled: FULL-U6-swift: tooling[# pass 79 # fail 0 rc 0] U3-syntax[rc 0] web-tsc[rc 0] web[Tests 21 passed (21)rc 0] schema[39 ok, rc 0] compose[compileDebugUnitTestKotlin testDebugUnitTest BUILD SUCCESSFUL rc 0; 4 classes] swift[tree e23b8cde9640ef61 built:  Executed 8 tests, with 0 failures (0 unexpected) ** TEST SUCCEEDED ** rc 0]
revertStatesCompiled: FULL-U6-web: tooling[# pass 79 # fail 0 rc 0] U3-syntax[rc 0] web-tsc[rc 0] web[Tests 14 passed (14)rc 0] schema[39 ok, rc 0] compose[compileDebugUnitTestKotlin UP-TO-DATE testDebugUnitTest UP-TO-DATE BUILD SUCCESSFUL rc 0; 4 classes] swift[tree a86501f57ca00d7c built:  Executed 6 tests, with 0 failures (0 unexpected) ** TEST SUCCEEDED ** rc 0]
revertStatesCompiled: FULL-U6-tooling: tooling[# pass 77 # fail 0 rc 0] U3-syntax[rc 0] web-tsc[rc 0] web[Tests 21 passed (21)rc 0] schema[39 ok, rc 0] compose[compileDebugUnitTestKotlin UP-TO-DATE testDebugUnitTest UP-TO-DATE BUILD SUCCESSFUL rc 0; 4 classes] swift[tree a86501f57ca00d7c = the last built tree, byte-identical]
revertStatesCompiled: FULL-U5: tooling[# pass 79 # fail 0 rc 0] U3-syntax[rc 0] web-tsc[rc 0] web[Tests 21 passed (21)rc 0] schema[39 ok, rc 0] compose[compileDebugUnitTestKotlin UP-TO-DATE testDebugUnitTest UP-TO-DATE BUILD SUCCESSFUL rc 0; 4 classes] swift[tree a86501f57ca00d7c = the last built tree, byte-identical]
revertStatesCompiled: FULL: tooling[# pass 79 # fail 0 rc 0] U3-syntax[rc 0] U3-selftest[ every check as required (mutants M-L18, M-per-arm, M-dry, M-abdiff RED rc 0] web-tsc[rc 0] web[Tests 21 passed (21)rc 0] schema[39 ok, rc 0] compose[compileDebugUnitTestKotlin UP-TO-DATE testDebugUnitTest UP-TO-DATE BUILD SUCCESSFUL rc 0; 4 classes] swift[tree a86501f57ca00d7c = the last built tree, byte-identical]

## Mutation log (bracketed: MUTATING before the edit, RESTORED after the byte-exact restore; PLAN §0)
MUTATING tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # U2-M1
RESTORED tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # U2-M1 (== pre)
MUTATING tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # U2-M2
RESTORED tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # U2-M2 (== pre)
MUTATING tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # U2-M3
RESTORED tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # U2-M3 (== pre)
MUTATING tools/titan/wire-differential.sh sha256 d050bb924195a82e9592ec79052f027bf2750a0d66ceda3717a0e3044e7458a5   # U2-M4
RESTORED tools/titan/wire-differential.sh sha256 d050bb924195a82e9592ec79052f027bf2750a0d66ceda3717a0e3044e7458a5   # U2-M4 (== pre)
MUTATING tools/titan/wire-differential.sh sha256 d050bb924195a82e9592ec79052f027bf2750a0d66ceda3717a0e3044e7458a5   # U2-M5
RESTORED tools/titan/wire-differential.sh sha256 d050bb924195a82e9592ec79052f027bf2750a0d66ceda3717a0e3044e7458a5   # U2-M5 (== pre)
VOID: the five U2 pairs above ran no test (rc 127: the invoking zsh passed the test command as one word); each was restored byte-exact (post == pre); re-executed below.
MUTATING tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # U2-M1
RESTORED tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # U2-M1 (== pre)
MUTATING tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # U2-M2
RESTORED tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # U2-M2 (== pre)
MUTATING tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # U2-M3
RESTORED tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # U2-M3 (== pre)
MUTATING tools/titan/wire-differential.sh sha256 d050bb924195a82e9592ec79052f027bf2750a0d66ceda3717a0e3044e7458a5   # U2-M4
RESTORED tools/titan/wire-differential.sh sha256 d050bb924195a82e9592ec79052f027bf2750a0d66ceda3717a0e3044e7458a5   # U2-M4 (== pre)
MUTATING tools/titan/wire-differential.sh sha256 d050bb924195a82e9592ec79052f027bf2750a0d66ceda3717a0e3044e7458a5   # U2-M5
RESTORED tools/titan/wire-differential.sh sha256 d050bb924195a82e9592ec79052f027bf2750a0d66ceda3717a0e3044e7458a5   # U2-M5 (== pre)
MUTATING tools/titan/bidi-marker-paint.mjs sha256 f602e3c18435c1bb8f228e767966ddd6e09cd85ea9ced0d13b8071de42d32748   # U5-M
RESTORED tools/titan/bidi-marker-paint.mjs sha256 f602e3c18435c1bb8f228e767966ddd6e09cd85ea9ced0d13b8071de42d32748   # U5-M (== pre)
MUTATING runtimes/web/src/renderer/InlineRuns.ts sha256 0c6d8c46527a37733be52b30f80595f6ffcba5631b7b806accf6507a66c73898   # U4-a
RESTORED runtimes/web/src/renderer/InlineRuns.ts sha256 0c6d8c46527a37733be52b30f80595f6ffcba5631b7b806accf6507a66c73898   # U4-a (== pre)
NOTE: the U4-a pair above ran red rc=1 / green rc=0 but its log kept no failing-test line (the harness grep missed vitest's format); re-executed below with the widened grep.
MUTATING runtimes/web/src/renderer/InlineRuns.ts sha256 0c6d8c46527a37733be52b30f80595f6ffcba5631b7b806accf6507a66c73898   # U4-a
RESTORED runtimes/web/src/renderer/InlineRuns.ts sha256 0c6d8c46527a37733be52b30f80595f6ffcba5631b7b806accf6507a66c73898   # U4-a (== pre)
MUTATING schema/spec/03-children.md sha256 ff34337fa11b553705b58137f5c51aaae92985ce5cdefcd302ec34ec112bb088   # U4-b1
RESTORED schema/spec/03-children.md sha256 ff34337fa11b553705b58137f5c51aaae92985ce5cdefcd302ec34ec112bb088   # U4-b1 (== pre)
MUTATING schema/spec/03-children.md sha256 ff34337fa11b553705b58137f5c51aaae92985ce5cdefcd302ec34ec112bb088   # U4-b2
RESTORED schema/spec/03-children.md sha256 ff34337fa11b553705b58137f5c51aaae92985ce5cdefcd302ec34ec112bb088   # U4-b2 (== pre)
MUTATING runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts sha256 e3982ec6d652cf0df4926b458b70adab47cf017a9306dd4d2aa45c63c2028ac0   # U6-W1
RESTORED runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts sha256 e3982ec6d652cf0df4926b458b70adab47cf017a9306dd4d2aa45c63c2028ac0   # U6-W1 (== pre)
MUTATING runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts sha256 e3982ec6d652cf0df4926b458b70adab47cf017a9306dd4d2aa45c63c2028ac0   # U6-W2
RESTORED runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts sha256 e3982ec6d652cf0df4926b458b70adab47cf017a9306dd4d2aa45c63c2028ac0   # U6-W2 (== pre)
MUTATING runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts sha256 e3982ec6d652cf0df4926b458b70adab47cf017a9306dd4d2aa45c63c2028ac0   # U6-W3
RESTORED runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts sha256 e3982ec6d652cf0df4926b458b70adab47cf017a9306dd4d2aa45c63c2028ac0   # U6-W3 (== pre)
MUTATING runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts sha256 e3982ec6d652cf0df4926b458b70adab47cf017a9306dd4d2aa45c63c2028ac0   # U6-W4
RESTORED runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts sha256 e3982ec6d652cf0df4926b458b70adab47cf017a9306dd4d2aa45c63c2028ac0   # U6-W4 (== pre)
MUTATING runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts sha256 e3982ec6d652cf0df4926b458b70adab47cf017a9306dd4d2aa45c63c2028ac0   # U6-W5
RESTORED runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts sha256 e3982ec6d652cf0df4926b458b70adab47cf017a9306dd4d2aa45c63c2028ac0   # U6-W5 (== pre)
MUTATING runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts sha256 e3982ec6d652cf0df4926b458b70adab47cf017a9306dd4d2aa45c63c2028ac0   # U6-W6
RESTORED runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts sha256 e3982ec6d652cf0df4926b458b70adab47cf017a9306dd4d2aa45c63c2028ac0   # U6-W6 (== pre)
MUTATING tools/visual/label-chrome-check.mjs sha256 fad373782a0de0eda2664e2a5cad0d4ea7b85b3bdd759fb91b39388e40dd7b65   # U6-T1
RESTORED tools/visual/label-chrome-check.mjs sha256 fad373782a0de0eda2664e2a5cad0d4ea7b85b3bdd759fb91b39388e40dd7b65   # U6-T1 (== pre)
MUTATING tools/visual/label-chrome-check.mjs sha256 fad373782a0de0eda2664e2a5cad0d4ea7b85b3bdd759fb91b39388e40dd7b65   # U6-T2
RESTORED tools/visual/label-chrome-check.mjs sha256 fad373782a0de0eda2664e2a5cad0d4ea7b85b3bdd759fb91b39388e40dd7b65   # U6-T2 (== pre)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/typography/wrapping/HyphenateCharacterExtractor.kt sha256 968d957fb9656965c36708e60feb2c527c25843de0669547052e6801c5cb2e31   # U6-C1
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/typography/wrapping/HyphenateCharacterExtractor.kt sha256 968d957fb9656965c36708e60feb2c527c25843de0669547052e6801c5cb2e31   # U6-C1 (== pre)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/typography/wrapping/PreBreakPipeline.kt sha256 18bafc7bb18a0bee96f8723678c5996b03ab49e5e4456eeea8d7e84f3d4a5304   # U6-C2
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/typography/wrapping/PreBreakPipeline.kt sha256 18bafc7bb18a0bee96f8723678c5996b03ab49e5e4456eeea8d7e84f3d4a5304   # U6-C2 (== pre)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/typography/wrapping/SoftHyphenCuts.kt sha256 d5cd45df0130558c5f7faa75ed6064c5649bd83b0ded704199883ca641b5a49a   # U6-C3
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/typography/wrapping/SoftHyphenCuts.kt sha256 d5cd45df0130558c5f7faa75ed6064c5649bd83b0ded704199883ca641b5a49a   # U6-C3 (== pre)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/OutOfFlowContainingBlock.kt sha256 ed0d29ac7577f7e56a34c7002cddbf662617db144908dfc8f42223b60f1b86d6   # U6-C4
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/OutOfFlowContainingBlock.kt sha256 ed0d29ac7577f7e56a34c7002cddbf662617db144908dfc8f42223b60f1b86d6   # U6-C4 (== pre)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/performance/PerformanceExtractor.kt sha256 9c31018c1217d3ad1afc6cd50c0553d6cd4d05d1b5be883f0b6817fa95581ef9   # U6-C5
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/performance/PerformanceExtractor.kt sha256 9c31018c1217d3ad1afc6cd50c0553d6cd4d05d1b5be883f0b6817fa95581ef9   # U6-C5 (== pre)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/OutOfFlowContainingBlock.kt sha256 ed0d29ac7577f7e56a34c7002cddbf662617db144908dfc8f42223b60f1b86d6   # U6-C6
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/OutOfFlowContainingBlock.kt sha256 ed0d29ac7577f7e56a34c7002cddbf662617db144908dfc8f42223b60f1b86d6   # U6-C6 (== pre)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/OutOfFlowContainingBlock.kt sha256 ed0d29ac7577f7e56a34c7002cddbf662617db144908dfc8f42223b60f1b86d6   # U6-C7
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/OutOfFlowContainingBlock.kt sha256 ed0d29ac7577f7e56a34c7002cddbf662617db144908dfc8f42223b60f1b86d6   # U6-C7 (== pre)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/wrapping/HyphenateCharacterExtractor.swift sha256 8a0ba398da67358eb3b2965dfb4da83217ead6513f73042bea43fd863f563958   # U6-S1
NOTE: the U2 pairs above ran on the pre-final bytes (before the .sh's unterminated-last-line fix and the P7 pin); re-executed below on the delivered bytes.
MUTATING tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # U2-M1
RESTORED tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # U2-M1 (== pre)
MUTATING tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # U2-M2
RESTORED tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # U2-M2 (== pre)
MUTATING tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # U2-M3
RESTORED tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # U2-M3 (== pre)
MUTATING tools/titan/wire-differential.sh sha256 8a53d3c180ed672a0b0798f61091c0739bd2ca978ed0ba015d8068e15a11ff34   # U2-M4
RESTORED tools/titan/wire-differential.sh sha256 8a53d3c180ed672a0b0798f61091c0739bd2ca978ed0ba015d8068e15a11ff34   # U2-M4 (== pre)
MUTATING tools/titan/wire-differential.sh sha256 8a53d3c180ed672a0b0798f61091c0739bd2ca978ed0ba015d8068e15a11ff34   # U2-M5
RESTORED tools/titan/wire-differential.sh sha256 8a53d3c180ed672a0b0798f61091c0739bd2ca978ed0ba015d8068e15a11ff34   # U2-M5 (== pre)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/wrapping/HyphenateCharacterExtractor.swift sha256 8a0ba398da67358eb3b2965dfb4da83217ead6513f73042bea43fd863f563958   # U6-S1 (== pre)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/layout/position/OutOfFlowContainingBlock.swift sha256 2682c94c607c3a73734043060e416d7b7f732e4aa9bbc7add4159ac8bfe14ea8   # U6-S2
NOTE: the .sh then gained the wpt-buckets.json prerequisite and the classify-only output name, and the test the [W-L7a] step-3 shape; the five U2 mutations are re-executed below on the DELIVERED bytes (the record of U2).
MUTATING tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # U2-M1
RESTORED tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # U2-M1 (== pre)
MUTATING tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # U2-M2
RESTORED tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # U2-M2 (== pre)
MUTATING tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # U2-M3
RESTORED tools/titan/wire-differential.mjs sha256 97fc097b64cc7964800441c96e0760d3831d41f7eb151fd3fdd3f377cf63394b   # U2-M3 (== pre)
MUTATING tools/titan/wire-differential.sh sha256 f8a73de0f99566cc41a0669cd2b0f41f554c27308f9e9d4e2a5bf8bb315348ce   # U2-M4
RESTORED tools/titan/wire-differential.sh sha256 f8a73de0f99566cc41a0669cd2b0f41f554c27308f9e9d4e2a5bf8bb315348ce   # U2-M4 (== pre)
MUTATING tools/titan/wire-differential.sh sha256 f8a73de0f99566cc41a0669cd2b0f41f554c27308f9e9d4e2a5bf8bb315348ce   # U2-M5
RESTORED tools/titan/wire-differential.sh sha256 f8a73de0f99566cc41a0669cd2b0f41f554c27308f9e9d4e2a5bf8bb315348ce   # U2-M5 (== pre)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/layout/position/OutOfFlowContainingBlock.swift sha256 2682c94c607c3a73734043060e416d7b7f732e4aa9bbc7add4159ac8bfe14ea8   # U6-S2 (== pre)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/layout/position/OutOfFlowContainingBlock.swift sha256 2682c94c607c3a73734043060e416d7b7f732e4aa9bbc7add4159ac8bfe14ea8   # U6-S3
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/layout/position/OutOfFlowContainingBlock.swift sha256 2682c94c607c3a73734043060e416d7b7f732e4aa9bbc7add4159ac8bfe14ea8   # U6-S3 (== pre)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/layout/position/OutOfFlowContainingBlock.swift sha256 2682c94c607c3a73734043060e416d7b7f732e4aa9bbc7add4159ac8bfe14ea8   # U6-S4
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/layout/position/OutOfFlowContainingBlock.swift sha256 2682c94c607c3a73734043060e416d7b7f732e4aa9bbc7add4159ac8bfe14ea8   # U6-S4 (== pre)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/layout/position/OutOfFlowContainingBlock.swift sha256 2682c94c607c3a73734043060e416d7b7f732e4aa9bbc7add4159ac8bfe14ea8   # U6-S5
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/layout/position/OutOfFlowContainingBlock.swift sha256 2682c94c607c3a73734043060e416d7b7f732e4aa9bbc7add4159ac8bfe14ea8   # U6-S5 (== pre)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/layout/position/OutOfFlowContainingBlock.swift sha256 2682c94c607c3a73734043060e416d7b7f732e4aa9bbc7add4159ac8bfe14ea8   # U6-S6
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/layout/position/OutOfFlowContainingBlock.swift sha256 2682c94c607c3a73734043060e416d7b7f732e4aa9bbc7add4159ac8bfe14ea8   # U6-S6 (== pre)

## Delivered patches (sha256 of the file bytes, written after the final restore)
patch: unit-U2.patch sha256 6a9d7edf795c0b0e34d572e925ff701f0b43e347ae484e3a092d4c931d516251
patch: unit-U3.patch sha256 5887ec66badca2aa904564729bb7854467347200c7dbd769ead1d69e4fe195c7
patch: unit-U4.patch sha256 10b2b61835838c2cbae20995603684dc8e85ada7d4213cf84d3abe83020e35bf
patch: unit-U5.patch sha256 0ff38b3b35750ad5577b5f731d301601a99e1a5a605255d553602b0e7d378421
patch: unit-U6-compose.patch sha256 936064d9915cf75714d00ea86b1bec6cb89616a7ffc04b57857f2ae4bcfb17c9
patch: unit-U6-swift.patch sha256 5f5607c2c8410c7ccb459e37690a23e4ed872b37affa4d6746c74ce7d3f5cbc8
patch: unit-U6-tooling.patch sha256 04a06a9feefd547b1bf50ec9316eb2fbc90e1ac93281749f913091dea3f2a80a
patch: unit-U6-web.patch sha256 d4539574c62fa32d411aadc1a91419fe17c203e75c54c6fb5ad717fd30c14c56

STATUS: COMPLETE
