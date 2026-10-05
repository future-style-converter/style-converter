# wave-52 L7 · static-position — skeptic review (2026-10-05, 19:24–19:45 local)

Tree: `campaign/wave52` HEAD 7d9c22a7 (shared, other lanes' work uncommitted). Read: PLAN.md §2 L7, §3, §4, §5, §9; brief
`static-position.md`; `_note.md` (ends `STATUS: COMPLETE`); every file in this directory. Nothing committed, staged, stashed or
checked out. Every mutation restored and sha-verified; both seams applied under the per-file lock and restored via
`git show HEAD:`; created seam test files removed; locks released.

**Verdict: PASS — no must-fix.** The pins are real (four independent mutations of my own, all RED with assertion messages),
the census reproduces with an independent script (T2 to the component, T1/T3/FOLD to the doc), both seams apply on their
stated bases and on bare HEAD, compile, and keep their pins green, and the predicted flip's picture is exactly the ref in my
own replay. Two should-fix items (one undocumented semantic coupling of seam-2, one over-broad "moves nothing else" sentence)
and six nits below.

## 1. Executed repros

| # | what | result |
|---|---|---|
| R1 | converter `./gradlew :converter:test --tests '*AlignSelfPropertyParserTest*' --tests '*InvalidDeclarationDropTest*' --tests '*SchemaConformance*'` | 7/0, 15/0, 17/0 (fresh XML, 17:24Z) |
| R2 | web `npx vitest run tests/conformance.test.ts tests/layout` + `tsc --noEmit` | 95/95, tsc clean |
| R3 | Compose `:runtime:testDebugUnitTest` GridAbsposPartitionTest / AbsposOverflowMeasureTest / CanvasRootHoistTest / AbsposStaticAlignmentTest | 16/0, 13/0, 25/0, 11/0 |
| R4 | Swift Catalyst (own `-derivedDataPath`), AbsposFlexStaticOffset / AbsposStaticPosition / AbsposStaticAlignment / AbsposGridStaticPosition / AbsposCbUsedHeight | 71/0 |
| M1 | **own mutation** web `AlignSelfApplier.ts`: re-kebab the value (`out.replace(' ', '-')`) | W2, W3 RED; restored sha 23501759… = before |
| M2 | **own mutation** converter `"normal" -> return null` | P3c RED (`expected AlignSelfProperty, actual null`); restored sha 4461fbdf… = before; re-run 7/0 |
| M3 | **own mutation** Compose `baselineFallback` LAST_BASELINE `safe = false` | K3a RED (`expected Spec(END, safe=true) but was Spec(END, safe=false)`); restored sha b2142920… = before |
| M4 | **own mutation** Swift `off()`: inset axis returns the padding edge instead of 0 | S4 RED (`15.0 is not equal to 0.0`); restored sha 55df35ce… = before |
| S1 | `git apply --check` seam-1 / seam-2 on HEAD (seam files == HEAD in the tree) | both clean |
| S2 | temp repo stack: Compose HEAD + L4 seam-1 + L3 seam-1 → L7 seam-1 `--check` | clean; then L11/L10/L6×2/L8×2/L9 Compose patches all still apply on top |
| S3 | temp repo stack: iOS HEAD + L3 seam-2 → L7 seam-2 `--check` | clean; later L11/L10/L6 seam-4/L8×2/L9 apply on top; L6 seam-2 fails — it fails on BARE HEAD too (L6's defect, not L7's) |
| S4 | seam-1 under lock (L3 seam hunk `--include` + L7 whole) → 5 Compose classes incl. StaticPositionSeamWiringTest | all green (SW 2/0); `ComponentRenderer.class` rewritten 19:31:08 (compile proven); sha c4369165… before = after; created test removed; lock released 19:31:11 |
| S5 | seam-2 under lock (L3 seam hunk + L7 whole) → 6 Swift classes incl. AbsposStaticOffsetSeamWiringTests | `SwiftCompile … ComponentRenderer.swift`, 72/0; sha d2afc70d… before = after; created test removed; lock released 19:32:21 |
| C1 | own census (`skeptic_census.py`, below) over the 1435 wave51-fix per-test IR docs | see §2 |
| P1 | opened containing-block-002 iOS capture + frozen ref; own replay (erase green → container yellow, paint 100×100 at (76,76)) | capture green (69,21)–(168,120), ref (76,76)–(175,175); as-is 0.9373 (= manifest), **simulated 1.0000, 0 residual px** (lane said 0.9967 — conservative) |
| P2 | own T1 Android replays (shift full mark box) | align-self-center 0.9808 → 1.0000; align-items-self-end 0.9715 → 1.0000; align-self-end 0.9704 → 0.9990 (bottom-border artifact, as the lane said) |
| P3 | crops of rtl-last-baseline-002 web vs ref; align-self-001 web vs ref; position-absolute-center-003 iOS vs ref | last-baseline mark 14 px high on web (browser owns the fix once the keyword arrives); align-self-001 container 2 (`normal`) at CENTER on web vs START in ref → web RISES (watched by the lane's additions); center-003 iOS green hides under the red box before and after → pixel-neutral claim holds |
| E1 | converter e2e of my own input | `baseline first` → BASELINE; `LAST BASELINE` → LAST_BASELINE; `safe right`, `right` dropped; `normal` → NORMAL; `safe end` → Generic; `place-self: left` → only `JustifySelf left` |
| W1 | `WATCH=…/watchlist-additions.txt node …/watchlist-check.mjs` and the plan watchlist | unmatched 0 / unmatched 0 |
| O1 | ownership: every modified/untracked path carrying an L7 marker | only own-list files + 3 new test files (`AlignSelfPropertyParserTest.kt`, `AbsposFlexStaticOffsetTests.swift`, `AlignSelfBaselinePosition.test.ts`); `StyleBuilder.ts` diff carries only L11 markers |
| X1 | `node schema/conformance/run.mjs`; schema grep | all valid; no AlignSelf enum in either schema → no wire freeze |

## 2. Census re-derivation (own script, never the lane's JSON)

| mechanism | lane | skeptic | verdict |
|---|---|---|---|
| T1 carriers (ABSOLUTE, no inset, GRID parent, no positioned ancestor) | 31 docs / 208 | 31 / 208 | match |
| T1 movers (either axis non-START, old vs new) | 108 comps / 29 docs | 112 / 29 | doc count matches; the +4 are unknown-extent `<img>` children (img-002, img-last-baseline-002) that a real render WILL move (nit 3) |
| inset abspos under a grid w/o positioned ancestor (K1c clip-veto release) | — | 0 | zero blast radius |
| FOLD (AlignItems SELF_START/SELF_END) | 2 / 2, GRID | 2 / 2, GRID | match for WPT; fixture corpus has a flex carrier (should-fix 2) |
| T3 Generic align-self | 106 + typed BASELINE oof 24 = 130 / 30 docs, 1 in-flow | 106 + 24 = 130 / 30, 1 in-flow (contain-inline-size-…-flex-row) | match |
| T3 web movers from `normal` | align-self-00 web watched | only align-self-001/002 have a positional container `align-items` (CENTER); the 10 other `normal` docs have none → no web change | lane's watch line is exact |
| T2 iOS flex overlay carriers | 41 / 172 | 41 / 172 | match |
| T2 movers (numeric: OLD padding-box align, NEW pad + content-box align + items fallback) | 100 / 9 docs | **100 / 9 docs** (justify-self-001 28, margin-001/002/003 20 each, fallback-justify-content-001 8, containing-block-001/002, center-003/004 1 each) | exact; position-absolute-006..010 / gradient-powerless-hue-* / center-002 are non-movers (child == container, or auto-sized child → pad 0) |

## 3. Defects

**should-fix 1 — seam-2's semantic coupling is undocumented and unguarded.** `AbsposStaticOffset.swift` (non-seam, live in the
tree now) adds the padding-start edge and aligns in whatever extent it is given; until `seam-2.patch` lands, the call site still
passes the §3.1 PADDING box (`childCB`/`childCBH`). The only pin of the call site ships inside seam-2, so if seam-2 were dropped or
mis-ordered every suite stays green while iOS over-shifts centred/end-aligned abspos children of padded flex containers:
containing-block-002 green would land at x = 16 + 5 + 15 + (195 − 100)/2 = 83.5 (ref 76, HEAD 69), same on y; margin-002 /
fallback-justify-content-001 (x) and margin-003 (y) likewise. seam-1 has no such hazard (defaulted parameter, unused fold → benign
no-op). Fix: one sentence in the seam-2 header and the lane note §5 — "AbsposStaticOffset.swift REQUIRES this patch; without it the
4 docs above regress" — so the orchestrator never lands one without the other.

**should-fix 2 — "no flex carrier, so the fold moves nothing else" is WPT-scoped.** `GridRenderer.foldAlignItems` is the fold for
EVERY container in every mode (dark stage included). `fixtures/properties/layout/flex-align-items.json` `AI_SelfEnd` (in-flow flex
row, `align-items: self-end`) now bottom-aligns its two items on Android (was STRETCH → top); `AI_SelfStart` flips
`containerStretches` (no visual change, explicit heights); `fixtures/fidelity/layout.combos.json` `Layout_Decorated` is a block
(inert since wave 51 PR 2). CSS-correct direction, none of the 8 gate-set fixtures carries the keyword, 0 committed `AI_*`
baselines → no gate risk. Amend the sentence and name the fixture mover.

**nits.** (3) T1 moved count 108 vs 112 (unknown-extent img children). (4) Two new test files outside the "own:" list
(`AlignSelfPropertyParserTest.kt`, `AbsposFlexStaticOffsetTests.swift`) — new, unowned, named in the note; stage them with the lane.
(5) `AbsposStaticAlignment.swift` 232 → 279 lines and the Compose twin 170 → 209 — past the ≤ 200 target (under the ~300 split
bar). (6) `fixtures/properties/layout/flex-align-self.json` / `grid-align-self.json` do not exercise the three new parser variants
(`normal`, `first baseline`, `last baseline`) — done-definition #1 gap, BACKLOG item. (7) Note §6 omits the align-self-001/002 web
rises (on the watchlist additions, so attribution is fine). (8) K3b pins the known-wrong mobile `normal` (container CENTER leaks;
ref paints START) — labelled a documented gap; a future fix must flip the pin. Also: `place-self: left` now keeps only
`justify-self: left` (CSS drops the whole shorthand) — pre-existing expander split, no corpus carrier.

## 4. Checks that came back clean

STATUS: COMPLETE present. Ring-fence: no `backdrop-filter-basic-blur`, no test-name / stem carve-outs in any L7 diff or patch.
No device A/B staged → no hash sentence owed. New files ≤ 200 lines, commented. No silent fallthrough added (every `else` arm
documented; unknown two-token values stay Generic and visible). Converter `InvalidDeclaration` has one consumer
(`PropertiesParser`) — no wire leak. Compose/iOS/web decoders tolerate NORMAL / LAST_BASELINE (Compose `else → AUTO/STRETCH`,
iOS `mapAlignment` NORMAL/.baseline, web spaced table). No non-WPT fixture carries an abspos child with a baseline align-self, so
the dark-stage `resolveCross` change is corpus-neutral. iOS wrap-reverse: the align-items fallback ignores it exactly like the
existing resolver (consistent; the only wrap-reverse carriers use `align-items: baseline` → no claim).

## 5. Census script (durable copy of the T2 numeric core; full helpers were in the session scratchpad)

```python
# per ABSOLUTE|FIXED child of a FLEX|INLINE_FLEX parent, per axis (x, y):
#   inset on axis            -> old 0, new 0
#   else old = f(base_old) * (paddingBox - child)          (unsafe; base from child align-self only)
#        new = padStart + f(base_new) * (contentBox - child) (base_new adds container align-items when
#              the child declares no align-self / auto), 0 when child or container extent unknown
#   f: START 0, CENTER .5, END 1; main axis base from justify-content (CENTER/FLEX_END/END/SPACE_*).
# mover = any axis |old - new| > 0.01  ->  100 components / 9 docs
```
