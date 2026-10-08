# wave53 L2 · soft-hyphen — lane note

Contract: `tools/titan/results/wave53-plan/PLAN.md` "### L2 · soft-hyphen" (+ §3 rows 2 and 4, §4 step 4, §6, §9 D2/D3);
brief `tools/titan/results/wave53-plan/spaceless-soft-hyphen.md` (F1, F1-iOS, F2).
Base: campaign/applier-campaign — lane start @ 762d3d30, HEAD now e330e255 (only `tools/titan/results/wave53-gate/_note.md`
changed between them). Every owned file and both seam files were byte-identical to HEAD (= dev cdb8a845) at lane start:
PreBreakPipeline.kt 31b798ac…, InlineRunFold.kt b42a1d14…, SoftHyphenPolicy.swift 0c4e59e6…, ComponentRenderer.kt e6c2a650…,
ComponentRenderer.swift ffa03357…, WordBreakOpportunities.kt 5d0dc052… (full hashes: `git show HEAD:<path> | shasum -a 256`).
Shared tree (PLAN §10.3): no worktree, no commit/stash/checkout; the seam files were never left modified.

## 1. What changed and why (revert units per PLAN §4 step 4 / §6 rule 6)

**Unit F1 (F1 + F1-iOS + seam-1)** — a space-less run that carries U+00AD is a soft-wrap opportunity nobody took.
- `runtimes/compose/src/main/java/com/styleconverter/runtime/typography/wrapping/PreBreakPipeline.kt` `preBreak` —
  the `:166` decline is now `if (text.indexOf(' ') < 0 && text.indexOf('\u00AD') < 0) return identity`; comment rewritten
  (the wave-21 whole-run gate owns only runs with NO opportunity; `DecorationOps.hasSoftWrapOpportunity` counts U+00AD,
  so softWrap stayed on and Minikin (`Hyphens.None` under `manual`) emergency-broke `highwa`/`y`). Such a run now enters the
  fold, whose wave-52 F5 taken-soft-hyphen trigger fires (`high‐\nway`, paired with softWrap = false at the call site).
  Space-less runs without U+00AD still return the identity instance (wave 21's population byte-stable by construction).
  Spec: css-text-3 §5.3 (`manual`), §5.5 (`overflow-wrap: normal`).
- `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/wrapping/SoftHyphenPolicy.swift`
  `admitsPreBreak(_:dictionary:horizontal:)` (new) — the label's pre-break precondition: the pre-wave-53
  `text.contains(" ") || dictionary` VERBATIM and first, then the new clause (space-less + U+00AD), which is
  **horizontal-only** (PLAN §9 D3) and logs its vertical refusal once (`PropertyTracker.logOnce`
  key `soft-hyphen:prebreak:vertical`). Spec: css-text-3 §5.3, CSS 2.1 §10.8 (the pin then counts 2 line boxes).
- `seam-1.patch` (ComponentRenderer.swift) — see §5.

**Unit F2 (F2 + seam-2)** — the paint-inert out-of-flow member.
- `runtimes/compose/src/main/java/com/styleconverter/runtime/typography/inline/InertOutOfFlowMember.kt` (new, 129 lines
  after the fix pass) `admits(member)` — true only when the member has no children/runs/decorations AND carries `Position`
  ABSOLUTE|FIXED (REQUIRED) AND `Color` with sRGB alpha 0 (REQUIRED) AND nothing outside {those, `Hyphens`}.
  (Fix pass D4: border-*-color is now REFUSED — the converter's `border: inherit` dialect is four `Border*Color
  {"original":"inherit"}` that paint the host's border; D7: the alpha is read with a safe cast. See "## Fix pass".)
  Spec: CSS 2.1 §9.3.1/§10.3.7 (out of flow), css-text-3 §5.1 (out-of-flow elements add no break / soft wrap opportunity),
  css-color-4 (alpha 0 paints nothing).
- `runtimes/compose/src/main/java/com/styleconverter/runtime/typography/inline/InlineRunFold.kt` — ONLY: the field
  `Outcome.Folded.droppedOutOfFlowMembers: Int = 0` (last, defaulted), the counter, the arm
  `} else if (tag in TEXT_MEMBER_TAGS && InertOutOfFlowMember.admits(child)) { droppedOutOfFlow++ }` placed before the
  glyph-member arm (after the EMPTY arm, which is untouched), and the named argument on the `Folded(...)` return.
  The member's own `Hyphens` is tolerated and NOT adopted (it governs only the member's unpainted text).
- `seam-2.patch` (ComponentRenderer.kt) — the fold breadcrumb gains `, N out-of-flow member(s) dropped` (§5).

**Where the tree disagreed with the brief/plan (the tree won):**
1. **seam-1 is 7 hunks, not the one §3 registers.** `PlaceholderLabel` (a private struct inside the seam) has no
   writing-mode input — `TextConfig` carries none and the inherited-text environment is published to CHILDREN only — so
   D3's horizontal gate cannot be expressed at `:4919-4921` alone. The patch adds a REQUIRED (no default) stored property
   `horizontalWritingMode: Bool` and passes `WritingModeExtractor.extract(from: resolvedProperties)?.isVertical != true`
   (the idiom already at `:1746`) at all four `PlaceholderLabel(` call sites (`:3175` inlineRunLabel, `:3433` fold,
   `:3494` leading text, `:4209` leaf). Hunks sit at :3196, :3482, :3538, :4280, :4827, :4901, :4918 — none near L4's
   registered `:1876` hunk; no shared function.
2. **D3 reachability, measured:** the iOS closure IS reachable for vertical-rl. `hyphens-vertical-001` box 1
   (`…__2-320`, `hyphen<U+00AD>ation`, VERTICAL_RL, Width 3em) reaches the leaf label with `wrapWidth = textWrapWidth(style)` = the
   physical 48 px (it renders today as horizontal `hyph`/`ena-`/`tion`, wave52-ship ios capture). Without the gate F1-iOS
   would have pre-broken it. With the gate it declines exactly as before.
3. **The F2 predicate is tighter than the plan's "properties ⊆ {…}" wording:** a subset test alone would admit a plain
   IN-FLOW span carrying only `Hyphens` (dropping visible glyphs). Position ABSOLUTE|FIXED and alpha-0 Color are both
   REQUIRED. The census (§2) shows the same 12 members pass.
4. Patches apply on the current tip e330e255 (`git apply --check` ✓ both), whose seam files are byte-identical to cdb8a845.

## 2. Census (blast radius) — method and result

Method A (committed, plan): `python3 tools/titan/results/wave53-plan/spaceless-soft-hyphen.census.py wave52-ship` →
1435 docs, 17 shy docs, 24 space-less U+00AD strings (15 leaf + 9 run pieces; 6 host mirrors excluded), 21 abspos/fixed
members in runs hosts: 12 inert (all `hyphens-out-of-flow-001/-002`), 9 bail. Reproduced exactly.

Method B (this lane's own, JVM, production decoder): `census-jvm.kt.txt` appended temporarily to InlineRunFoldTest.kt by
`run-census.py`, three arms, both touched sources restored byte-exact (sha256 asserted; InlineRunFoldTest.kt 02730120…,
InlineRunFold.kt 917d877d…, PreBreakPipeline.kt 0915c0a2…). Outputs: `census-jvm.f2-on.txt` (F1+F2), `census-jvm.f2-off.txt`
(F2 arm disabled), `census-jvm.f1-off.txt` (F1 guard restored):
- 1435 docs, 264 runs hosts whose plan resolves. **Fold decisions: exactly 12 hosts change** (diff f2-off → f2-on):
  `Bailed(member-prop:Position)` → `Folded`, `oof=1`, all `hyphens-out-of-flow-001__{2..7}` (`high<U+00AD>way`) and
  `-002__{2..7}` (`highway`). The other 252 hosts, including all 9 abspos controls, are decision-identical.
- **Space-less U+00AD strings: 24 (+6 F2 merges = 30).** PreBreakPipeline at each box's own ch width (19.2 px mono):
  F1-off → 0 fire; F1-on → 21 fire: span-001 ×9, out-of-flow-001 leaf ×1 + merges ×6 (+2 run pieces that F2 now folds
  away), hyphenate-character-001/-003 piece 1 → 5 lines, -004 → 3 lines (`imple‐|menta‐|tion`). Identity: auto-control ×3
  (dictionary veto), vertical-001 (wrap -1: the latch is horizontal-only), -005 (`white-space: pre`), and the four one-line
  run pieces `igh<U+00AD>way`/`high<U+00AD>wa`/`<U+00AD>way`/`high<U+00AD>`.
- iOS reach (traced, not executable on the JVM): span-001 leaves; out-of-flow-001 boxes 2-7 through the LEADING label with the
  host mirror `high<U+00AD>way` (the abspos child is out of flow, so `InlineRunFlow.fold` bails "unclaimed" and
  `InlineRunPlan.resolve` returns nil on the empty in-flow child list); hyphenate-character-001/-003/-004 piece 1 through
  `inlineRunLabel` (br-stacked); auto-control already admitted by its dictionary arm; vertical-001 refused (D3); -005
  declined earlier (pre).

**Carrier set (= expectations.json `lanes.L2-soft-hyphen.captureCarriers`, cross-checked, no difference):**
android: hyphens-span-001, -out-of-flow-001, -out-of-flow-002, hyphenate-character-001, -003, -004 (6);
ios: hyphens-span-001, -out-of-flow-001, hyphenate-character-001, -003, -004 (5); web: none. Wire carriers: none.
Passing today in the set (wave52-ship): android span-001 P 0.9532, out-of-flow-001 P 0.9685, -002 P 0.982 (all DEGENERATE);
ios none. Revert units: F1 carries span-001 (both), out-of-flow-001 (both), hyphenate-character-00x (both);
F2 carries out-of-flow-001/-002 android only (as in expectations.json).

## 3. Pins and EXECUTED mutations (red → restore byte-exact → green)

Runner: `tools/titan/results/wave53-soft-hyphen/mutate.py` (one exact-string replacement, focused suite, restore from the
in-memory original, sha256 asserted equal, green re-run). Results JSON beside it.

| id | file (sha256 before = restored) | mutation | red (failing pins) | green |
|---|---|---|---|---|
| M-a | PreBreakPipeline.kt 0915c0a2… | restore the space-only guard | (a) `aSpacelessSoftHyphenRunFiresWithTheTakenHyphen`, (d) `aMultiOpportunitySpacelessRunSplitsIntoFiveLines` | 20/20 |
| M-b | PreBreakPipeline.kt 0915c0a2… | drop the `!unbreakableOverflow && !tookSoftHyphen` return | (b) `aFittingSpacelessSoftHyphenRunIsIdentity`, (c), +6 legacy identity pins | 20/20 |
| M-c | PreBreakPipeline.kt 0915c0a2… | remove `!dictionaryHyphenation &&` | (c) `aDictionarySpacelessSoftHyphenRunStaysIdentity`, `theSoftHyphenTriggerStaysOutOfDictionaryRuns` | 20/20 |
| M-over | PreBreakPipeline.kt 0915c0a2… | delete the space-less guard outright | `declinesForASpacelessRunTheWholeRunGateOwns` | 20/20 |
| M-d | WordBreakOpportunities.kt 5d0dc052… (EXPORT TREE only — read-only for L2) | `:87` `if (false && ch == '\u00AD')` | (a), (d), `composesWithTheSoftHyphenPolicy`, `firesWhenASoftHyphenBreakWasTaken` | 20/20 |
| M-F2-off | InlineRunFold.kt 917d877d… | F2 arm → `else if (false)` | `hyphens-out-of-flow-001 - the paint-inert abspos member drops…`, `hyphens-out-of-flow-002 box 4 …` | 42/42 |
| M-F2-wide | InertOutOfFlowMember.kt 3810bd8f… | admit any abspos/fixed member | `hypothetical-inline-alone-on-second-line - a VISIBLE abspos member still bails`, `the inert predicate needs BOTH…` | 42/42 |
| M-seam2 | ComponentRenderer.kt, seam-2 applied (d251ace7…) | delete the clause's 2 code lines | `RunFoldBreadcrumbSeamTest` | 1/1, then HEAD bytes e6c2a650… restored |
| S-shy | SoftHyphenPolicy.swift 28a2f7f5… | drop the U+00AD clause | `testASpacelessSoftHyphenRunIsAdmitted` | 32/32 |
| S-d3 | SoftHyphenPolicy.swift 28a2f7f5… | drop the horizontal guard | `testAVerticalSpacelessSoftHyphenRunIsRefused` | 32/32 |

Pins (all on VERBATIM wave52-ship per-test IR; `hyphens-span-001.json` sha1 7b52d8c7… identical in wave53-open):
- Compose `PreBreakPipelineTest`: (a) `…span-001__1-301` decoded through IRDocumentDecoder+SlotComposer at 6·CH →
  fired `high‐\nway`; (b) same at 8·CH → `assertSame`; (c) `…auto-control__0-235` (`AUTO`, en-us) at 12·CH,
  dictionary → identity; (d) `im<U+00AD>ple<U+00AD>men<U+00AD>ta<U+00AD>tion` (hyphenate-character-001 `__1-177` runs[0]) at 4.5·CH → five lines.
  `declinesForASpacelessRunTheWholeRunGateOwns` kept green (the over-widening pin, M-over).
- Compose `InlineRunFoldTest` (42): the `:152` pin INVERTED — `OUT_OF_FLOW_001_HOST3` (byte-identical to wave52-ship,
  checked per component) → `Folded("high\u00ADway")`, `droppedOutOfFlowMembers == 1`; new `-002` box 4 → `Folded("highway")`;
  negatives still Bailed: hypothetical-inline-alone-on-second-line `Line 2` → `member-prop:Position`, ch-unit-001 →
  `member-prop:BackgroundColor` (empty arm; on device its RELATIVE host never folds), static-inside-inline-001 →
  `member-tag:none`; a synthetic both-facts-required predicate pin.
- Compose `RunFoldBreadcrumbSeamTest` (new, source scan, comments stripped): the Folded breadcrumb reads
  `runFold.droppedOutOfFlowMembers > 0` and labels `… out-of-flow member(s) dropped`.
  **It is RED on the shared tree until seam-2 is applied — by design (it is the check that seam-2 was spliced). The F2
  commit must carry seam-2.**
- Catalyst `SoftHyphenPolicyTests` (+5): admitted `high\u{AD}way`; refused `Deoxyribonucleic`; the pre-wave-53 clauses
  unchanged in both writing modes; vertical `hyphen\u{AD}ation` refused (D3); auto-control: `localeTag("AUTO","en-us")`,
  `AutoHyphenation.locale(for: "en-us") != nil` on Catalyst, admitted by the dictionary arm.
- Catalyst `GreedyLineBreakerTests` (+2): `lines("high\u{AD}way", 6·mono) == ["high\u{2010}","way"]`; the 5-line piece.

Focused suites (final tree): Compose `--tests` PreBreakPipelineTest, InlineRunFoldTest, GreedyLineBreakerTest,
WordBreakOpportunitiesTest, SoftHyphenPolicyTest, AutoHyphenationTest, SeamReachabilityTest, InlineSpanRingTest,
InlineSpanContentTest, InlineAtomContentTest, LineBoxCensusTest, InlineRunPlanTest, RunFoldBreadcrumbSeamTest → 171 run,
170 pass, 1 fail = RunFoldBreadcrumbSeamTest (expected without seam-2; `suite-compose.txt`). With seam-2 applied
(`seam-2.verify.json`): RunFoldBreadcrumbSeamTest + InlineRunFoldTest + PreBreakPipelineTest + SeamReachabilityTest 66/66.
Catalyst SoftHyphenPolicyTests, GreedyLineBreakerTests, WordBreakOpportunitiesTests, InlineRunFlowTests: 89/89 without
seam-1 (`suite-catalyst.txt`) and 89/89 with seam-1 applied (`seam-1.verify.json`).

Fix pass: x1-x8 (+ M-F2-off / M-F2-wide re-run on the fix-pass tree) are in "## Fix pass" below.

## 4. Predictions (against wave53-open, which equals wave52-ship on every L2 cell within ±0.0001)

| cell | from | prediction | confidence | gate floor · geometry |
|---|---|---|---|---|
| css-text/hyphens/hyphens-span-001 android | P 0.9532 DEGENERATE | P ≈ 0.994 (span-002 android's picture; `high‐`/`way` ×9) | HIGH | 0.99 · gating |
| hyphens-out-of-flow-001 android | P 0.9685 DEGENERATE | P ≈ 0.994 with F1+F2 (F1 alone ≈ 0.975: boxes 1,2,7 fixed) | MED-HIGH | 0.985 · gating |
| hyphens-out-of-flow-002 android | P 0.982 DEGENERATE | P ≈ 0.994 with F2 (F1 alone: byte-identical) | MED-HIGH | 0.985 · gating |
| hyphens-span-001 ios | f 0.8652 | P ≈ 0.99 (box 26 → 46 px) | MED-HIGH | 0.98 · gating |
| hyphens-out-of-flow-001 ios | f 0.8935 | P ≈ 0.99 | MED | report |
| hyphenate-character-001 / -003 android | f 0.9136 / 0.9168 | up (piece 1 → 5 lines) | MED | report; a flip is DEGENERATE by construction |
| hyphenate-character-004 android | f 0.9041 | small | LOW | report; DEGENERATE if it flips |
| hyphenate-character-001/-003/-004 ios | f 0.9287 / 0.9337 / 0.9134 | small either way (now pinned 5 lines, U+2010 baked) | LOW | report; DEGENERATE if it flips |

Geometry: `python3 tools/titan/results/wave53-plan/soft-hyphen.geometry.py <run>` must print `→ GEOMETRY OK` on the five
target rows — **with `hunk-for-orchestrator-1.patch` applied first** (fix pass, skeptic D1: the committed probe's ink rule
r+g+b < 300 cannot see Android's grey hyphen and prints a CORRECT Android `high‐`/`way` as WRONG). Under the restated rule
(max(r,g,b) < 200) wave52-ship and wave53-open print: ios box 1 height 26 vs 46; android span-001 / -out-of-flow-001 box 1
line 1 ink x25-77 vs x25-61, -out-of-flow-002 box 3 line 1 x25-31 vs x25-61; the four hyphens-span-002 anchor rows and
every ref row OK; exit 0 (`geometry-restated.wave52-ship.txt`, `geometry-restated.wave53-open.txt`).

## 5. Hand-offs

- `tools/titan/results/wave53-soft-hyphen/seam-1.patch` (ComponentRenderer.swift; 7 hunks, §1 item 1) — lands in unit F1
  WITH `SoftHyphenPolicy.swift`. `git apply --check` clean on e330e255. Verified under the lock
  `tools/titan/runs/wave53-lock/ComponentRenderer.swift`: HEAD ffa03357… → patched 524e3158… → 89/89 → restored ffa03357… ✓.
- `tools/titan/results/wave53-soft-hyphen/seam-2.patch` (ComponentRenderer.kt; 1 hunk at :3830) — lands in unit F2 WITH
  `InlineRunFold.kt` + `InertOutOfFlowMember.kt` + `RunFoldBreadcrumbSeamTest.kt`. Verified under the lock
  `tools/titan/runs/wave53-lock/ComponentRenderer.kt`: HEAD e6c2a650… → patched d251ace7… → 66/66, M-seam2 red, → restored
  e6c2a650… ✓. L4's Compose seam-1 goes on after it (§3).
- No hunks for other lanes. No file outside the own: list was edited (WordBreakOpportunities.kt mutated only in the export
  tree; shared-tree copy 5d0dc052… untouched).
- **Fix pass: two hunks for the ORCHESTRATOR** (plan files, not L2-owned; both `git apply --check` clean on e330e255) —
  `hunk-for-orchestrator-1.patch` (soft-hyphen.geometry.py: ink rule max(r,g,b) < 200 + the span-002 anchor self-check) and
  `hunk-for-orchestrator-2.patch` (plan-build.py + the expectations.json it regenerates: L2 `geometryProbe` gains
  `inkRule` / `selfCheck`, `onWave52Ship` restated). Both must land BEFORE L2 (see "## Fix pass" D1).
- Observation for the orchestrator (seam file, not edited): the Compose rule-B breadcrumb at ComponentRenderer.kt ~:7092
  says "pre-broke an unbreakable overflowing run" also for the wave-52 F5 taken-soft-hyphen trigger — every F1 fire will
  log under that (inaccurate) wording.

## ORCHESTRATOR WINDOW REQUESTS

None is needed before landing; all of these read the integrated probe / closing gate.
1. Geometry (R6), after `wave53-probe` and `wave53-final`, **on the probe restated by `hunk-for-orchestrator-1.patch`**
   (fix pass D1; apply it, and `hunk-for-orchestrator-2.patch` for expectations.json, BEFORE L2 lands):
   `python3 tools/titan/results/wave53-plan/soft-hyphen.geometry.py wave53-probe` → `→ GEOMETRY OK` on
   hyphens-span-001 android/ios, -out-of-flow-001 android/ios, -out-of-flow-002 android; every ref row OK, all four
   hyphens-span-002 ANCHOR rows OK, no `SELF-CHECK FAILED` line (exit 0). An ANCHOR FAILED line means the rule (or a
   must-not-move leak on span-002, which R4/R7 then name) — never an L2 revert by itself.
2. Android breadcrumb audit during the css-text capture (if logcat is kept):
   `adb -s <serial> logcat -d -s ComponentRenderer:I | grep 'out-of-flow member(s) dropped' | wc -l` → 12 (one per folded
   host, `…hyphens-out-of-flow-00{1,2}__{2..7}`); `… | grep 'meta.runs fold bail (member-prop:Position)'` → only
   hypothetical-inline-alone-on-second-line (CSS2 section).
3. iOS, if the app's stderr is captured for css-text: exactly one line containing
   `soft-hyphen run in a vertical writing mode is not pre-broken` (hyphens-vertical-001); its capture must be identical to
   wave53-open (decoded pixels).
4. The A/B owed after the closing gate (PLAN §8 step 11, drop-F2, Android, css-text): in InlineRunFold.kt replace
   `} else if (tag in TEXT_MEMBER_TAGS && InertOutOfFlowMember.admits(child)) {` with `} else if (false) {` (mutate.py
   `M-F2-off`) and `git apply -R tools/titan/results/wave53-soft-hyphen/seam-2.patch`; record `base.apk` sha1. Expect
   out-of-flow-002 android back to wave53-open's capture (P 0.982), out-of-flow-001 android ≈ 0.975 (F1 alone).

## 6. Must-not-move (expectations.json `lanes.L2-soft-hyphen.mustNotMove`, 37 cells) — why each is safe

- hyphenate-character-005 ×3: `white-space: pre` declines before the guard on Android (softWrapAllowed false) and iOS
  (noWrap/preservesSpaces precede the clause); web untouched.
- hyphens-auto-control android: enters the pipeline, identity by the dictionary veto (pin (c)); ios: admitted by its
  dictionary arm before AND after (residual: the simulator's CF en-us dictionary — §7).
- manual-011/-012/-013, manual-inline-011/-012, none-011, none-shy-on-2nd-line-001, block-ellipsis-014/-028 (android+ios):
  every string has a space (the guard already passed) — decision-identical by construction; no abspos run members.
- span-002 (android+ios), punctuation-001 android: no U+00AD in a space-less string (census).
- hyphens-vertical-001 ios: D3 refusal (pin + S-d3); android: wrap width -1.
- the nine F2-bail controls: JVM census — fold decisions identical (none passes the predicate; the census diff is exactly
  the 12 out-of-flow hosts).
- hanging-punctuation-inline-001 ios: no U+00AD. All web hyphens cells: no web file changed.

## 7. Not verified

- No device capture: every score/geometry prediction is reasoning + JVM/Catalyst pins, not pixels.
- Android: that the real-font fold breaks `highway` at the soft hyphen inside the 6ch box (Minikin already judged it does
  not fit — `highwa`/`y` today — and the fold measures through the same TextMeasurer style; HIGH but not measured), and that
  the baked U+2010 matches the ref's hyphen ink within ±2 px.
- iOS: that `CFStringIsHyphenationAvailableForLocale(en-us)` is true on the simulator runtime (pinned on Catalyst only); if it
  were false, hyphens-auto-control ios would now be pre-broken (the control-check would catch it).
- iOS: PlaceholderLabel's render path itself (private to the seam) is not unit-testable; the seam is verified by compile +
  the helper pins.
- The census's fold arm uses each host's OWN property list (the seam passes the inheritance-merged one); the F2 predicate
  reads only the member, so the F2 diff is unaffected, but absolute fold outcomes for unrelated hosts may differ on device.
- The iOS-side reach (mirror labels, br-stacked pieces) is traced from source, not executed.

## Fix pass (wave 53 L2 fix lane, after `skeptic.md`; shared tree, HEAD e330e255)

Every skeptic check was RE-RUN before acting: `soft-hyphen.geometry.py wave52-ship` (five targets WRONG, refs OK, exit 0 —
as recorded); `skeptic-geometry-anchor.py wave52-ship` → output byte-identical to `skeptic-evidence/geometry-anchor.wave52-ship.txt`;
a PIL read of the span-002 hyphen (box 1, x55-63 y110-126): ref/web darkest (59,59,59) sum 177, ios (73,73,73) sum 219,
**android (114,114,114) sum 342 — 0 pixels at sum < 300, 17 at max < 200**; and the skeptic's x5/x2/x3/x1/x4 replayed on the
shared tree with `mutate.py` (his verbatim strings): **all five survive, 42/42 green** (`mutations-fixpass-before.result.json`;
sanity: M-F2-off on the same harness went red 2/42 in the same session, so the runner does see mutations).

| defect | sev | action |
|---|---|---|
| D1 geometry probe blind to Android's hyphen | must-fix | **Handed to the orchestrator** (the probe, `plan-build.py` and `expectations.json` are plan files L2 does not own). `hunk-for-orchestrator-1.patch`: `soft-hyphen.geometry.py` gets a local `ink(p) = max(r,g,b) < 200` (the brief's luminance-200 rule, spaceless-soft-hyphen.md §2; excludes orange, r = 255, which sum < 450 would admit at 420) — `geometry_common.dark` is untouched, the other probes keep it — and `hyphens/hyphens-span-002` as an ANCHOR: all four of its rows must print `GEOMETRY OK`, else `SELF-CHECK FAILED (ANCHOR FAILED: …)` and exit 1 (a missing ref or anchor PNG also fails the self-check). Result sha256 711a5181…. **Re-proved** (patch applied to a `git show HEAD:` copy, run with the real `geometry_common`): wave52-ship AND wave53-open → anchor ×4 OK, the five targets WRONG (ios box 1 height 26 vs 46; android span-001 / -001 box 1 line 1 x25-77 vs x25-61; -002 box 3 x25-31), every ref OK, web -002 WRONG (out of family), exit 0 (`geometry-restated.wave52-ship.txt`, `.wave53-open.txt`). **Mutation G-ink** (ink() back to r+g+b < 300): span-002 android → `GEOMETRY WRONG (box 1 line 1 ink x25-54 vs ref x25-61)`, `SELF-CHECK FAILED (ANCHOR FAILED: hyphens/hyphens-span-002)`, exit 1; restored 711a5181… → exit 0 (`geometry-restated.mutation.txt`). `hunk-for-orchestrator-2.patch`: `plan-build.py` L2 `geometryProbe` gains `inkRule` + `selfCheck` and a restated `onWave52Ship`, plus the `expectations.json` it regenerates — verified in a `git archive` copy: unpatched `plan-build.py` reproduces both committed outputs byte-identically; patched, `watchlist.txt` is byte-identical and `expectations.json` changes only in that object (+13/−1). L2's predictions, floors, `expect` rows and `geometryGating` are unchanged. |
| D2 transparent-ink half unpinned | should-fix | **Fixed** (L2-owned `InlineRunFoldTest.kt`): new `the inert predicate refuses every colour that is not fully transparent` on the verbatim member — `{"srgb":{"r":1,"g":0,"b":0},"original":"red"}` (no alpha) and `{"srgb":{"r":0,"g":0,"b":0,"a":0.5}}` refused, the verbatim transparent colour admitted (positive control). x5, x2 and x3 (on the post-D7 line: x3p) now RED → GREEN. |
| D3 other predicate branches unpinned | nit | **Fixed** (tests only): `the inert predicate refuses structure and border colours, and admits fixed` (a member with a nested child, a member with `runs`; `Position FIXED` admitted) and `hyphens-out-of-flow-001 as a mark member - a UA-painted tag keeps the tag wall` (the verbatim host with the member's `sourceTag` → `mark`, whose UA yellow background never rides the wire → `Bailed("member-tag:mark")`). x1, x4 and the new x6 (FIXED dropped) RED → GREEN. |
| D4 `border: inherit` dialect admitted as inert | nit (latent) | **Fixed** by dropping the tolerance (`InertOutOfFlowMember.kt`: `BORDER_COLOR_TYPES` and its `when` arm removed; banner says why). A border colour now refuses; pinned with `BorderTopColor {"original":"inherit"}` and an authored red `BorderLeftColor`. Census-neutral: the 12 admitted members carry exactly {Color, Position}; the only 2 of 21 with a Border*Color also carry BackgroundColor/Border*Style/… and bailed already. New x8 (tolerance re-admitted) RED → GREEN. |
| D5 F1 bakes U+2010 where `hyphenate-character` is `""` / `•` / `/-/`, unlogged | nit | **Not changed — queued for the orchestrator.** The breadcrumb belongs where the fired run's `HyphenateCharacter` is visible (the Compose caller in the seam, or a new tracker file); PreBreakPipeline.kt does not see that property and is oversized (call sites only). Suggested BACKLOG line: "Compose/iOS: when the soft-hyphen pre-break fires on a run whose `HyphenateCharacter` is not `auto`, log a PropertyTracker breadcrumb (the baked U+2010 ignores it; wave-53 L2 skeptic D5)". The plan already labels the hyphenate-character-00x flips DEGENERATE. |
| D6 D3 seam wiring unpinned on the host | nit | **Not changed (no host pin is possible: `PlaceholderLabel` is private to the seam).** Restated for the probe: `hyphens-vertical-001` ios decoded-pixel identity to wave53-open is THE D3 pin (window request 3), not a formality. |
| D7 `alphaIsZero` throws on a non-primitive alpha | nit | **Fixed**: `(srgb["a"] as? JsonPrimitive)?.floatOrNull ?: return false` (import `JsonPrimitive`, `jsonPrimitive` dropped). Pinned: `{"srgb":{"r":0,"g":0,"b":0,"a":{"v":0}}}` refuses. New x7 (the throwing cast `as JsonPrimitive?`) RED → GREEN. |

**Files changed by the fix pass** (all L2-owned): `runtimes/compose/src/main/java/com/styleconverter/runtime/typography/inline/InertOutOfFlowMember.kt`
(3810bd8f… → 608ebe36…, 129 lines; `admits` + `alphaIsZero` + banner; every mutation, the census and the suite below were
re-run on these FINAL bytes after a last banner-wording edit), `runtimes/compose/src/test/java/com/styleconverter/runtime/typography/inline/InlineRunFoldTest.kt`
(02730120… → bd835388…; +3 tests, +helper `memberWithColor`, +import `IRRun`), `tools/titan/results/wave53-soft-hyphen/mutate.py`
(x1-x5, x3p, x6-x8 added), new `run-census-fixpass.py`, the two hunks, and the evidence files below. `InlineRunFold.kt` (917d877d…),
`PreBreakPipeline.kt`, the Swift files, `RunFoldBreadcrumbSeamTest.kt` and both seam patches are untouched (seam-1 / seam-2 still
`git apply --check` clean; seam files at HEAD e6c2a650… / ffa03357…; `tools/titan/runs/wave53-lock/` empty).

**Executed mutations on the fix-pass tree** (`mutate.py`, shared tree, `mutations-fixpass.result.json`; every row restored
byte-exact, sha256 before = restored; focused suite `*InlineRunFoldTest`, 45 tests):

| id | file (sha256 = restored) | mutation | red (failing pin) | green |
|---|---|---|---|---|
| x5 | InertOutOfFlowMember.kt 608ebe36… | `"Color" -> transparentInk = true` | `…refuses every colour that is not fully transparent` | 45/45 |
| x2 | same | `return a <= 0f` → `return true` | same | 45/45 |
| x3p | same | missing alpha → `?: return true` (post-D7 line) | same | 45/45 |
| x7 | same | safe cast → throwing `as JsonPrimitive?` | same (ClassCastException) | 45/45 |
| x1 | same | children/runs/decorations check deleted | `…refuses structure and border colours, and admits fixed` | 45/45 |
| x6 | same | `OUT_OF_FLOW_POSITIONS = setOf("ABSOLUTE")` | same | 45/45 |
| x8 | same | border-*-color tolerance re-admitted | same | 45/45 |
| x4 | InlineRunFold.kt 917d877d… | arm's `tag in TEXT_MEMBER_TAGS &&` dropped | `…as a mark member - a UA-painted tag keeps the tag wall` | 45/45 |
| M-F2-off | InlineRunFold.kt 917d877d… | F2 arm → `else if (false)` | out-of-flow-001 / -002 pins (2) | 45/45 |
| M-F2-wide | InertOutOfFlowMember.kt 608ebe36… | any abspos/fixed admitted | `Line 2` bail, both-facts pin, both new predicate pins (4) | 45/45 |

**Census after the fix** (`run-census-fixpass.py`: the builder's JVM census, arm f2-on, on the fix-pass tree; test file restored
bd835388…): `census-jvm.fixpass-f2-on.txt` is **byte-identical** to the builder's `census-jvm.f2-on.txt` — 12 hosts Folded with
`oof=1` (all `hyphens-out-of-flow-001/-002__{2..7}`), every other fold decision unchanged. Carrier set, predictions,
must-not-move cells: unchanged.

**Focused suites, fix-pass tree** (`suite-compose-fixpass.txt`): the 13 Compose classes of §3 → 174 run, 173 pass, 1 fail =
`RunFoldBreadcrumbSeamTest` (red BY DESIGN until seam-2 is spliced; unchanged from the builder). Catalyst not re-run: no Swift
file changed.

**Draft for the PLAN §10 addendum (the orchestrator's to write):** "6. L2 geometry probe restated before L2 lands (L2 skeptic
D1): `soft-hyphen.geometry.py`'s line ink is max(r,g,b) < 200 (was r+g+b < 300, blind to Android's (114,114,114) hyphen, which
printed the correct `hyphens-span-002` android as WRONG); `hyphens-span-002` ×4 is an anchor self-check (exit 1 on failure).
Re-proved on wave52-ship and wave53-open: anchor ×4 OK, the five L2 targets WRONG, refs OK; the old rule makes the anchor fail
(exit 1). `expectations.json` L2 `geometryProbe.onWave52Ship` restated; floors, `expect` and `geometryGating` unchanged."

**Not verified by the fix pass:** no device or simulator (as before) — in particular, that the F1-baked U+2010 on span-001 /
-out-of-flow-001/-002 android rasterises like span-002's hyphen (the skeptic's argument: same face, Minikin inserts U+2010) so the
restated probe prints OK there; the seam-2 lock re-verification was not repeated (neither `InlineRunFold.kt` nor the seam changed;
`RunFoldBreadcrumbSeamTest` is a source scan); the hunks were applied only to scratch copies, never to the plan files.

TREES: /Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf (Gradle + xcodebuild);
export tree for M-d only (Gradle, throwaway, not evidence): /private/tmp/claude-501/-Users-dranak-Documents-Projects-Style-Converter--claude-worktrees-trusting-bohr-bd6fbf/0c47cad8-074d-4821-bf4c-b5997f23f535/scratchpad/export-l2/apps/android-harness
(xcodebuild used a private -derivedDataPath beside it, `…/scratchpad/dd-l2`).
Fix pass: Gradle on the shared tree only (/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf);
no export tree, no xcodebuild.

STATUS: COMPLETE

## Re-verification (wave 53 L2 re-verifier, after "## Fix pass"; shared tree HEAD e330e255)

Scope: `skeptic.md` lists ONE must-fix (D1). D2-D7 are should-fix or nit; I also replayed the D2/D3 mutations the skeptic
recorded as survivors. Every check was EXECUTED. The shared tree was written only under this directory. Plan-file hunks
were applied only to copies. Gradle ran only in a private export tree (see TREES).

### D1 (must-fix): the geometry probe cannot see Android's hyphen — FIXED AS A HAND-OFF, NOT YET APPLIED

Re-run: `bash tools/titan/results/wave53-soft-hyphen/reverify-geometry.sh <temp dir>` (output: `reverify-geometry.txt`).

- **The skeptic's own check still reproduces on the committed probe.** It has to: the plan files are byte-identical to HEAD.
  - `soft-hyphen.geometry.py` is 5a91900a…, `plan-build.py` f42459a1…, `expectations.json` 80077c40….
  - `python3 tools/titan/results/wave53-plan/soft-hyphen.geometry.py wave52-ship` prints the five targets WRONG and the refs
    OK, exit 0.
  - `skeptic-geometry-anchor.py wave52-ship` is byte-identical to `skeptic-evidence/geometry-anchor.wave52-ship.txt`:
    - `sum<300 hyphens-span-002 android … → GEOMETRY WRONG (box 1 line 1 ink x25-54 vs ref x25-61)`;
    - `max<200 … → GEOMETRY OK`.
  - PIL: the darkest pixel of the span-002 android hyphen is (114,114,114) at (57,122), sum 342.
- **I looked at the PNGs** (ref, span-002 android, span-001 android, span-001 ios; wave52-ship box crops). They agree with the
  restated probe's verdicts:
  - ref and span-002 android both read `high‐`/`way`; Android's hyphen is visibly lighter.
  - span-001 android reads `highwa`/`y`.
  - span-001 ios has the right glyphs, but its boxes are 26 px tall and the text overflows them.
- **hunk-for-orchestrator-1.patch.**
  - `git apply --check` is clean on the shared tree.
  - Applied to a HEAD copy of `soft-hyphen.geometry.py` in a mirror (`tools/wpt` and `tools/titan/runs` symlinked, because
    `geometry_common` resolves ROOT from `__file__`): 5a91900a… → 711a5181…, the hash in the hunk header.
  - On both wave52-ship and wave53-open the output is byte-identical to the lane's `geometry-restated.<run>.txt`, apart from
    the `exit=0` line the lane appended. The rows:
    - the four span-002 anchor rows: OK;
    - every ref row: OK;
    - span-001 ios and out-of-flow-001 ios: `box 1 height 26 vs ref 46`;
    - span-001 android and out-of-flow-001 android: `box 1 line 1 ink x25-77 vs ref x25-61`;
    - out-of-flow-002 android: `box 3 line 1 ink x25-31 vs ref x25-61`;
    - out-of-flow-002 web: WRONG, out of family;
    - exit 0.
  - **The rule still fails every broken target, and now passes the correct anchor.**
- **My own executed probe mutations** (mirror copy only):

  | id | mutation | sha256 mutated | result | sha256 restored |
  |---|---|---|---|---|
  | RV-G1 | `ink()` back to r+g+b < 300 | 08b42761… | span-002 android WRONG x25-54, `SELF-CHECK FAILED (ANCHOR FAILED: hyphens/hyphens-span-002)`, **exit 1** | 711a5181… |
  | RV-G2 | RV-G1 plus the anchor flag deleted | edd7e4ab… | **exit 0**: the self-check, not luck, is what turns RV-G1 red | 711a5181… |
  | RV-G3 | unmutated probe, run id with no captures | n/a | 12 MISSING rows, ANCHOR FAILED, **exit 1** (a missing capture cannot vouch) | n/a |

  After the restore, wave52-ship and wave53-open both exit 0.
- **hunk-for-orchestrator-2.patch.** I checked it in a `git archive HEAD` copy of `wave53-plan/`.
  - Unpatched, `plan-build.py` regenerates `expectations.json` and `watchlist.txt` byte-identical to HEAD.
  - With both hunks applied, the patched `plan-build.py` regenerates exactly the patched `expectations.json`.
  - `watchlist.txt` stays byte-identical.
  - `expectations.json` changes only in `lanes.L2-soft-hyphen.geometryProbe` (14 diff lines: + `inkRule`, + `selfCheck`,
    `onWave52Ship` restated). The file still parses.
  - `wave53-gate/adjudicate.mjs` and `control-check.mjs` never read `geometryProbe`, so the new keys are inert to them.
- **Verdict: fixed.** The restated probe passes the correct picture and fails all five broken targets, and its self-check is
  proven able to fail. But the fix lives in orchestrator-owned plan files and **has not been applied**.
  - None of `soft-hyphen.geometry.py`, `expectations.json` or PLAN §10 contains it yet: grep for `max(r,g,b)` / `ANCHOR` /
    `skeptic D1` finds nothing.
  - Before `wave53-probe`, the orchestrator must:
    1. `git apply tools/titan/results/wave53-soft-hyphen/hunk-for-orchestrator-1.patch`;
    2. then `git apply tools/titan/results/wave53-soft-hyphen/hunk-for-orchestrator-2.patch`;
    3. write §10 item 6. The fix pass drafted it in "## Fix pass".
  - Without this, §6 rule 4 would revert F1 and F2 on correct Android pictures.

### D2 / D3 / D4 / D7 (should-fix, nit): replayed, all red → green

- **Set-up.**
  - Private export tree: the skeptic's export tree copied, then the shared tree's 6 L2 Compose files copied in. Those are:
    - `InertOutOfFlowMember.kt` 608ebe36…
    - `InlineRunFold.kt` 917d877d…
    - `InlineRunFoldTest.kt` bd835388…
    - `PreBreakPipeline.kt` 0915c0a2…
    - `PreBreakPipelineTest.kt`
    - `RunFoldBreadcrumbSeamTest.kt`
  - `ComponentRenderer.kt` was rebuilt as `git show HEAD:` + seam-2. Its hash is d251ace7…, equal to the lane's and the
    skeptic's.
- **Runner.** `python3 tools/titan/results/wave53-soft-hyphen/reverify-mutate.py <export root> <out.json>` (output:
  `reverify-mutations.result.json`).
- **Green before:** `*InlineRunFoldTest *RunFoldBreadcrumbSeamTest *PreBreakPipelineTest` → **66/66**, exit 0. With seam-2
  applied, the breadcrumb pin is green.
- **The skeptic's five survivors**, now with his exact texts. x3 is restated on the D7 line, because the old line no longer
  exists. Each ran against the 45-test `*InlineRunFoldTest`:

  | id | sha256 before = restored | sha256 mutated | red pin | green |
  |---|---|---|---|---|
  | x5 | 608ebe36… | 5a4bfb11… | `the inert predicate refuses every colour that is not fully transparent` | 45/45 |
  | x2 | 608ebe36… | ea016c43… | same | 45/45 |
  | x3p | 608ebe36… | 6ba66b8c… | same | 45/45 |
  | x1 | 608ebe36… | ba0e8f7b… | `the inert predicate refuses structure and border colours, and admits fixed` | 45/45 |
  | x4 | 917d877d… (InlineRunFold.kt) | 7ea06c14… | `hyphens-out-of-flow-001 as a mark member - a UA-painted tag keeps the tag wall` | 45/45 |

  The mutated hashes equal the fix pass's, so these are the same mutation bytes.
- **Green after:** 66/66.
- **D4 and D7 by reading.** `BORDER_COLOR_TYPES` is gone and every border colour refuses. `alphaIsZero` uses
  `(srgb["a"] as? JsonPrimitive)?.floatOrNull ?: return false`. The fix pass's x7 and x8 rows are in
  `mutations-fixpass.result.json`; I did not replay them.
- **D5 and D6 are not changed.** They are queued or restated as orchestrator items, as the skeptic allowed (nits).

### Hygiene (at the end of this pass)

- **Seam files.** `git status --short` over the four seam files is EMPTY. All four have sha256 equal to `git show HEAD:`:
  - ComponentRenderer.kt e6c2a650…
  - ComponentRenderer.swift ffa03357…
  - ComponentRenderer.tsx cc39ab6e…
  - extract-fixture.mjs 831aa02d…

  `seam-1.patch` and `seam-2.patch` still pass `git apply --check` on HEAD.
- **Lock dir.** `tools/titan/runs/wave53-lock/` is EMPTY (no lock left).
- **No probe leftovers.**
  - The tracked diffs of L2's 7 modified owned files and its 2 new files (InertOutOfFlowMember.kt,
    RunFoldBreadcrumbSeamTest.kt) contain no `probe`/`Probe` and no println / System.out / FIXME / XXX.
  - Every modified file carrying an L2 identifier (InertOutOfFlow / droppedOutOfFlow / admitsPreBreak / soft-hyphen) is on
    the own: list.
- **Shared-tree bytes untouched by this pass:** InertOutOfFlowMember.kt is 608ebe36… and InlineRunFold.kt is 917d877d…,
  both before and after.
- **No leftover processes.** None remain from the export tree: `--no-daemon` with in-process Kotlin.

**Not verified here:** anything on a device, as for the lane and the skeptic. In particular, I could not check that
F1's baked U+2010 on span-001 / out-of-flow-001 / -002 android rasterises like span-002's hyphen, so that the restated probe
prints OK there. I did not re-run Catalyst, because no Swift file changed since the skeptic's 89/89.

TREES: /Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf (read-only except this
directory; no Gradle); private export tree for Gradle (`--no-daemon`, in-process Kotlin):
/private/tmp/claude-501/-Users-dranak-Documents-Projects-Style-Converter--claude-worktrees-trusting-bohr-bd6fbf/0c47cad8-074d-4821-bf4c-b5997f23f535/scratchpad/rv/exp/apps/android-harness

STATUS: COMPLETE
