# wave-52 lane L7 · static-position — lane note

Tree: `campaign/wave52` (HEAD 7d9c22a7), shared with up to eleven other lanes. Plan: `tools/titan/results/wave52-plan/PLAN.md` §2 L7
(+ §3/§4/§5/§9). Brief: `tools/titan/results/wave52-plan/static-position.md` §3/§4 T1–T3 (T4 deferred, §6).

## 0. Resume record (third start)

The two killed starts left NOTHING for this lane: no results dir, every owned file `git diff`-clean, no untracked file under the owned
paths. Built from scratch on this start. L2's note (`wave52-composed-canvas/_note.md` §1/§8) delivered NO `ComponentRenderer.kt` hunk
(its RC1 z-order went harness-side), and L3's note (`wave52-failure-ink/_note.md` §2/§9) delivered NO iOS overlay-partition hunk — so the
C6 overlaps are gone; both my seam patches are cut against HEAD with L3's patches applied first (they touch `:2772` / `:4352` only).

## 1. What changed and why

| target | file(s) | change | spec |
|---|---|---|---|
| **T3** converter | `AlignSelfPropertyParser.kt`, `AlignSelfProperty.kt` | `normal` → NORMAL, `[first] baseline` → BASELINE, `last baseline` (either order, `&&`) → LAST_BASELINE; `left`/`right` (± overflow keyword) → `InvalidDeclaration` (dropped); `safe|unsafe <pos>` stays Generic (the enum cannot carry the overflow keyword; both mobile readers parse it) | css-align-3 §6.1, §4.2; css-syntax-3 §2.2 |
| **T3** web | `AlignSelfExtractor.ts` (+ comment in `AlignSelfApplier.ts`) | LAST_BASELINE → `'last baseline'` WITH the space (kebab() would emit invalid `last-baseline`); `StyleBuilder.ts` untouched (L11's; `applyGeneric` not widened) | css-align-3 §4.2 |
| **T3** mobile | `AbsposStaticAlignment.kt`, `AbsposStaticAlignment.swift` | new `baselineFallback`: BASELINE → (START, safe), LAST_BASELINE → (END, safe) on the typed AND the raw Generic align-self wire; align-self only (iOS `resolveSelf` gated on `cssName == "align-self"`, so justify-self keeps parity with Compose's `justifyBase`) | css-align-3 §4.2 fallback `safe self-start/self-end` |
| **T1** Compose | `CanvasRootHoist.kt` | `LocalStaticPositionOwner: CompositionLocal<IRComponent?>` + defaulted 5th parameter `staticPositionOwned` on `rendersInFlowAsStaticPosition` (gates BOTH clauses) | css-grid-1 §9.2 |
| **T1** Compose | `GridRenderer.kt` | the §9.2 overlay provides `LocalStaticPositionOwner provides child`; new pure `foldAlignItems` (the one align-items keyword fold, + SELF_START/SELF_END arms) | css-grid-1 §9.2; css-align-3 §6.1 |
| **T1** seam | `seam-1.patch` → `ComponentRenderer.kt` | RC1 call passes `staticPositionOwned = …LocalStaticPositionOwner.current === component`; the inline AlignItems fold delegates to `GridRenderer.foldAlignItems`; ships the source-pin test `StaticPositionSeamWiringTest.kt` | — |
| **T2** iOS | `AbsposStaticOffset.swift` | `staticOffset`: content-box alignment container, padding-start edge per owned axis, `withAlignItemsFallback` (cross axis, only when the child declares no align-self or `auto`, never over an inset); `resolveStatic` byte-parallel twin untouched | css-flexbox-1 §4.1; css-align-3 §6.1/§6.2; css-position-3 §3.5 |
| **T2** seam | `seam-2.patch` → `ComponentRenderer.swift` | the two arguments: `containerW: flexContentSize(…, false)`, `containerH: flexContentSize(…, true) ?? absposUsedPaddingBoxHeight(…) − paddingBand` (keeps the wave-33 used-height fallback, as content); ships the source-pin test `AbsposStaticOffsetSeamWiringTests.swift` | — |

**Deviations from the plan text (deliberate, each pinned):**
1. *Owner by IDENTITY, not a Boolean* (plan: "default false; GridRenderer provides true"). A CompositionLocal reaches the provider's whole
   subtree; a `true` would also cancel the zero-flow mount of a NESTED static-position box inside the overlay child. Same discipline as
   `LocalBypass`. The seam's fifth argument is still a Boolean (`=== component`).
2. *The fold lives in `GridRenderer.foldAlignItems`* (plan: two arms inline in the seam). A pure function in an L7-owned file is pinnable
   at HEAD; the seam hunk delegates to it (SW2 source pin proves the delegation, shipped inside the patch).
3. *Baseline fallbacks are `safe`* (brief pin 4 says "→ Spec(END)"): §4.2 names the fallback `safe self-end`; the base is END as briefed.
4. *NORMAL / explicit STRETCH stay claim-less on mobile* (unchanged behaviour): §6.1 says an abspos `normal` behaves as start, so a
   container's positional align-items leaking in is a DOCUMENTED GAP (pinned as-is in K3b / B2), not staffed — census: the 2 `normal`
   children of grid-abspos-staticpos-align-self-001/002 (container CENTER) are the only carriers where it matters.
5. *justify-self `baseline` fallback NOT staffed*: iOS `resolveSelf` serves justify-self too; applying §4.2 there without Compose's
   `justifyBase` twin would split the natives on grid-abspos-staticpos-justify-self-001/002 (container CENTER, child `{"type":"baseline"}`).

## 2. Census (`static-position.census.mjs` → `static-position.census.json`, all 1435 wave51-fix per-test IR docs)

Ports each decision OLD (HEAD) vs NEW (L7), joined to scored cells through `score-gate.mjs`'s own `loadRun`.
- **T1** (ABSOLUTE, no non-auto inset, GRID parent, no positioned ancestor anywhere up the chain): **31 docs / 208 components** (= the
  brief's 31); **108 components in 29 docs move** on Android (10 img children have unknown extents; START-aligned ones never move).
- **FOLD** (`AlignItems` SELF_END/SELF_START anywhere): **2 components / 2 docs** — exactly the two align-items-self-end targets; no
  flex carrier, so the fold moves nothing else.
- **T2** (ABSOLUTE child of a FLEX parent, iOS overlay): **41 docs / 172 components; 100 move in 9 docs** — justify-self-001 (28),
  margin-001/002/003 (20 each), fallback-justify-content-001 (8), containing-block-001/002 (1 each), css-position/position-absolute-
  center-003/004 (1 each; the green sits UNDER the red sibling's box on both natives — paint order, other family — so pixel-neutral).
  The three safe-00x docs carry border, no padding: **0 movers** (their pin table is unchanged, re-derived in the header).
- **T3** (Generic align-self on any component + typed BASELINE on an out-of-flow child): **130 components / 30 docs** — `last baseline`
  25, `normal` 24, `left` 20, `right` 20, `safe center` 12, `safe end` 4, `safe anchor-center` 1, typed BASELINE 24; **1 in-flow**
  carrier (css-contain/contain-inline-size-grid-indefinite-height-min-height-flex-row: its grid item's `last baseline` now reaches the
  browser). Mobile `resolveCross` changes on **88** components.
- **moves** (per platform, input of the replay): iOS 132 components / 29 docs; Android 114 / 38 (T1 + T3-on-positioned-grids).
- End-to-end wire check (`converter-e2e.json`, `./gradlew :converter:run`): `last baseline` → `{"type":"AlignSelf","data":"LAST_BASELINE"}`,
  `normal` → NORMAL, `left` → dropped, `safe center` → Generic.

## 3. PNG replay (`png-replay.mjs` → `png-replay.json`; ssim.js `fast`, same-size, as inject-wpt-block.mjs scores)

Calibration: the as-is score equals the recorded manifest ssim on every replayed cell.
- **T1 headline, 16 Android cells**: align-self/align-items center (+lbp), self-end (+lbp) → **1.0000**; end/flex-end/self-end →
  **0.9992**, their lbp twins → **0.9942** — the residual is a replay artifact (the capture's 50 / 400 px of green hidden under the
  bottom border cannot be recovered by moving visible ink; a real render paints the full box at the ref rows 67..116 / 102..151).
- **T2 iOS**: containing-block-002 **f 0.9373 → 0.9967**, containing-block-001 0.9505 → **1.0000**, justify-self-001 0.9921 → **0.9996**,
  margin-001 0.9909 → 0.9968, margin-003 0.9857 → 0.9951, fallback-justify-content-001 0.9874 → 0.9882; margin-002 unmapped.
- **T3 iOS** (grid overlay): align-self-001/002, rtl-001..004, vertWM-001..004 rise ≤ +0.0016; last-baseline-001 0.9952 → 0.9948,
  vertWM-last-baseline-001/002 0.9947 → 0.9946, -003/-004 0.9964 → **0.9950** (the brief's predicted overshoot — `last baseline` → END in
  the CONTENT box passes the ref's grid-area row until T4); last-baseline-002 / rtl-last-baseline-003/004 rise to 0.9966.
- **T3 web**: the 10 non-img `*-last-baseline-*` cells differ from the ref ONLY in mark ink (0 unexplained px) → **1.0000** once the
  browser gets the keyword; img-last-baseline-001/002 not attributable by colour (image marks); contain-inline-size-grid-indefinite-
  height-min-height-flex-row web 0.9729: 5002 differing px, 2 unexplained → ~1.0000.
- **Android multi-container docs** (rtl/vertWM/align-self-002/justify-self-002/…): marks hang outside their containers on Android, so the
  colour mapping is ambiguous (reported UNMAPPED, never guessed). Proxy: Android vs iOS captures differ ONLY in mark ink (0 unexplained px
  in 21 of 22 docs; img-002 image marks) and iOS's grid overlay already aligns by the real box, so every T1 move goes TO the iOS geometry:
  rtl-003/004 0.9588 → toward 0.9777, justify-self-002 0.974 → toward 0.9821, align-self-002 0.9737 → toward 0.9771, and **vertWM-003/004
  0.97 / 0.9711 → toward 0.9667 / 0.9682** (a DROP — the vertical-rl refs align on the other axis; still ≥ 0.95, watched).

## 4. Pins and executed mutations (`mutate.py` → `mutations.log`; RED needs an assertion-failure match, not just a non-zero exit)

| pin | file | mutation | result |
|---|---|---|---|
| P3a–P3g (7) | converter `AlignSelfPropertyParserTest.kt` (new) | M-C1 disable `last baseline` arm / M-C2 left,right → null | RED (P3a,P3f) / RED (P3d,P3g); restored |
| W1–W4 | web `tests/layout/AlignSelfBaselinePosition.test.ts` (new) | M-W1 route LAST_BASELINE through kebab | RED 4/4; restored |
| K1a–K1c | `AbsposOverflowMeasureTest.kt` (verbatim align-self-center child IR) | M-K1 ignore `staticPositionOwned` | RED (K1a,K1c); restored |
| K2a, K2b | `GridAbsposPartitionTest.kt` (verbatim align-items-self-end IR) | M-K2 drop SELF_END arm of `foldAlignItems` | RED both; restored |
| K3a, K3b | same | M-K3 drop LAST_BASELINE arm | RED (K3a); restored |
| K4 (source) | same | M-K4 provider `provides null` | RED; restored |
| S1–S4 | Swift `AbsposFlexStaticOffsetTests.swift` (new; verbatim justify-self-001 + containing-block-002 IR) | M-S1 drop padding term / M-S2 drop align-items fallback | RED (S1,S2,S4: (0,0)/(40,40)) / RED (S2,S4: y 15); restored |
| B1, B2 + grid row | `AbsposStaticAlignmentTests.swift`, `AbsposGridStaticPositionTests.swift` | M-S3 drop LAST_BASELINE arm | RED (B1 + grid row); restored |
| SW1, SW2 (source, in seam-1) | `StaticPositionSeamWiringTest.kt` | negative control: seam NOT applied | RED 2/2 (`_compose-negctl-run.log`) |
| seam-2 source pin (in seam-2) | `AbsposStaticOffsetSeamWiringTests.swift` | negative control: seam NOT applied | RED (4 asserts, `_swift-negctl-run.log`) |

Focused suites on the final files (seams NOT applied): converter 63/0 (`AlignSelf*`, `InvalidDeclaration*`, `SchemaConformance*`,
shorthands; `_jvm-xml/conv2`); web 95/0 (`tests/layout/` + conformance, `_web-focused-final.log`), `tsc --noEmit` 0; Compose 98/0
(8 classes, `_jvm-xml/final`); Swift 71/0 (5 classes incl. AbsposCbUsedHeightTests, `_swift-focused-final.log`).

## 5. Seam patches (never left applied; lock taken; restored via `git show HEAD:`; sha-compared)

- `seam-1.patch` (Compose, 2 hunks + new test file). Base: HEAD with L2's patches (none for this file) and L3's `seam-1.patch` applied.
  `git apply --check` clean on HEAD; `patch --dry-run` clean on HEAD+L3. **VERIFIED** `_seam1-verify.log` 19:07:17–19:07:58 via
  `seam-verify.sh` (L3 seam hunks `--include`d, mine whole): compileDebugKotlin re-ran, 12 classes **160/0** incl. SW1/SW2, L3's
  VerticalBlockFlowSeamGuardTest, CanvasRootHoistTest, FlexboxExtractorTest; created test file removed; sha `c4369165…e652` before = after,
  `git diff` clean, lock released.
- `seam-2.patch` (iOS, 1 hunk + new test file). Base: HEAD with L3's `seam-2.patch` applied (L2: none). **VERIFIED** `_seam2-verify.log`
  19:09:51–19:10:53: ComponentRenderer.swift recompiled, 7 classes **77/0** incl. the source pin and AbsposCbUsedHeightTests; sha
  `d2afc70d…ee63` before = after, `git diff` clean, lock released.
- **Orchestrator**: apply after L3's; both of my patches CREATE a test file, so apply them whole (not `--include`).
- **Incident (19:01:01, this lane, repaired at once):** my first verification applied L3's FULL `seam-1.patch` (L3's 18:51 re-cut also
  creates `VerticalBlockFlowSeamWiringTest.kt`); the seam restore left that file in the tree. Confirmed byte-identical to L3's patch content
  and that it did not exist before (the apply succeeded), then deleted it (19:02). `seam-verify.sh` now `--include`s the seam path for
  prior lanes' patches and removes exactly the files its own patch creates. L3's patch itself is unaffected.
- Two earlier attempts failed on other lanes' in-flight state (19:01 `BorderSideApplier.kt:454` "No value passed for parameter 'box'"
  — L6's file, fixed by 19:01:30; 19:02:38 a concurrent Gradle build's `NoSuchFileException` in `compileDebugKotlin` outputs) — retried.

## 6. Predicted flips and movers (wave51-fix → wave52 gate)

- **+1 ios MED-HIGH** `css-flexbox/abspos/position-absolute-containing-block-002` f → P (replay 0.9967). Falsifier: green not at (76,76).
- **Picture-correctness, no flip (HIGH):** 16 Android `css-grid/abspos/grid-abspos-staticpos-align-{self,items}-*` → 1.0000; 10 web
  `*-last-baseline-*` (non-img) → 1.0000; containing-block-001 ios → 1.0000. **MED-HIGH:** justify-self-001 ios → ~0.9996; margin-001/003,
  fallback-justify-content-001 ios small rises; css-contain/…-min-height-flex-row web → ~1.0. **MED:** img-last-baseline-001/002 web.
- **Movers that DROP (watched, no flip):** vertWM-003/004 android ~0.97 → ~0.967 (proxy); vertWM-last-baseline-003/004 ios 0.9964 →
  0.9950; last-baseline-001 ios −0.0004; vertWM-last-baseline-001/002 ios −0.0001. **Predicted P → f: 0.**
- Unreplayed: margin-002 ios, Android multi-container docs (proxy only), css-position/position-absolute-center-003/004 (pixel-neutral).

## 7. Not verified (honest)

- No device render: every after-number is a pixel simulation on frozen captures (§3), the Android multi-container docs only by proxy.
- Never executed in a composition: the RC1 branch with the owner set (no Robolectric) — the pure decision (K1) and the wiring (SW1, K4)
  are pinned separately. The iOS overlay's `ComponentHost.offset` with the new shift is not rendered (Catalyst unit tests only).
- Whether a positioned Compose grid routes abspos children through `RenderAbsoluteChild` (then T3's positioned-grid "moves" are inert on
  Android) was not traced; the census assumes the overlay path (upper bound).
- iOS harness/app targets not built; no device suite; full module suites not run (focused only, rule 4).

## 8. Hand-offs

- Delivered: `seam-1.patch`, `seam-2.patch` (verified, §5). No hunk for another lane; nothing received. `StyleBuilder.ts` untouched (L11).
- Watch lines not already in the plan: `watchlist-additions.txt` (4 lines; `WATCH=… watchlist-check.mjs` → unmatched 0).
- BACKLOG paragraph for the orchestrator: "Static position (wave 52 L7): Compose's §9.2 grid overlay owns its children's static position
  (the RC1 zero-flow mount stands down by identity), `align-items: self-start|self-end` fold to start/end, the iOS flex overlay aligns in
  the CONTENT box from the padding-start edge and falls back to `align-items`, and `align-self: normal | [first|last] baseline` are typed
  (web emits `last baseline`; mobile uses the §4.2 safe self-start/self-end fallback; `left|right` are dropped as invalid). Open: T4 (grid
  area as alignment container, RTL / vertical-rl axes — the last-baseline marks overshoot on mobile until then); abspos `normal`/`stretch`
  still defer to `align-items` on mobile; justify-self baseline fallback (iOS-only would split the natives)."

STATUS: COMPLETE
