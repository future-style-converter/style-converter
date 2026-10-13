# Wave 55: cross-lane audit of the seven lane briefs (2026-10-11)

**Tree.** `…/trusting-bohr-bd6fbf`, branch `campaign/applier-campaign`, HEAD `0d0d3a3d` (the wave-54 merge).

**Contract.** `tools/titan/results/wave54-plan/PLAN.md` §0 and §2, with the model `wave54-plan/hyphenate-character-compose.md`.

**Briefs audited** (all in this directory):

| lane | brief |
|---|---|
| L1 | `hyphen-ios-linebox.md` |
| L2 | `cbb-android.md` |
| L3 | `positioned-parent-slot.md` |
| L4 | `ua-heading-margin.md` |
| L5 | `counter-suffix-rows.md` |
| L6 | `android-paint-order.md` |
| L7 | `instruments.md` |

**Nothing was built, edited or captured.**
- The opening gate `wave55-open` was capturing throughout (`tools/titan/runs/wave55-open/gate-driver/driver.log`: css-text
  started at 11:35:31 UTC, section 22 of 30).
- Every script below only reads files, under `nice -n 19`.
- No git state changed. The tree outside `tools/titan/results/wave55-plan/` is byte-identical to 0d0d3a3d
  (`git status --short` shows only the three untracked dirs that were already there:
  `wave53-plan/__pycache__/`, `wave55-gate/`, `wave55-plan/`).

**Written by this audit:**
- this file and `seam-registry.md`;
- `briefs-audit.pointers.mjs` → `.pointers.out.txt` (every path token, with its range);
- `briefs-audit.anchors.mjs` → `.anchors.out.txt` (every `file:L` and bare `:L`, printed with the target line's text);
- `briefs-audit.cells.mjs` → `.cells.out.txt` (187 transcribed table cells against their runs, using the scorer's own
  `loadRun`).

The two `.mjs` read-outs take `git ls-files` output as their first argument. Regenerate it with
`git ls-files > <file>` at 0d0d3a3d.

**Verdict.**
- The briefs are individually sound:
  - 0 pointer failures;
  - every "from" value equals `wave54-final`;
  - every target cell is scored;
  - all seven briefs end in `STATUS: COMPLETE`.
- They are **not yet jointly consistent.** No file is owned by two lanes, and the seam regions are disjoint, but:
  - each lane wrote its must-not-move set and its probe-decision rules against its OWN carriers;
  - six of those rules collide with another lane's carriers, or with L6's 252-stem may-move set (§5);
  - three hand-offs have no receiving owner (§1);
  - three geometry-gated prediction rows carry no floor (§4).
- All of it can be resolved at plan synthesis (§8). No brief needs a rewrite. `ok = false` until §8 is applied.

---

## 1. Ownership disjointness (every §3 path of every brief)

**Owned paths, by lane.** NEW = absent at HEAD. Every NEW path was confirmed absent: no tracked file and no file on disk
under `runtimes/`, `tools/` or `apps/`.

- **L1** (`hyphen-ios-linebox.md:180-184`):
  - NEW `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift`
  - NEW `runtimes/swiftui/Tests/StyleConverterRuntimeTests/NormalLineBoxTests.swift`
  - `tools/titan/results/wave55-hyphen-ios-linebox/`
  - seam 1 (`ComponentRenderer.swift`), and seam 1b as a contingency.
- **L2** (`cbb-android.md:179-211`), all under `runtimes/compose/src/`:
  - NEW `main/java/com/styleconverter/runtime/core/variables/ContainingBlockBands.kt` (= `git show 5b333c8a:…`, 53 lines)
  - `main/…/core/variables/DynamicValueResolver.kt` (sha `d6943e20…` = 5b333c8a^, re-verified)
  - `test/…/core/variables/DynamicValueResolverTest.kt` (sha `169e2cb8…` = 5b333c8a^, re-verified)
  - NEW `main/…/sizing/PercentContentBoxBand.kt`
  - `main/…/sizing/SizingApplier.kt` (561 lines; two call sites only)
  - NEW `test/…/sizing/PercentContentBoxBandTest.kt`
  - NEW, patch-borne `test/…/core/renderer/ChildContainingBlockSeamWiringTest.kt`
  - `tools/titan/results/wave55-cbb-android/`
  - seam 1 (`ComponentRenderer.kt`).
- **L3** (`positioned-parent-slot.md:221-243`), all under `runtimes/compose/src/`:
  - NEW `main/…/layout/position/PositionedParentSlotGate.kt`
  - NEW `main/…/layout/position/PositionedParentRtlOrigin.kt`
  - `main/…/layout/position/PositionedParentFlowSlot.kt` (169 lines, sha `67d69d61…`)
  - NEW `test/…/layout/position/PositionedParentSlotGateTest.kt`
  - NEW `test/…/layout/position/PositionedParentRtlOriginTest.kt`
  - `test/…/core/renderer/SeamReachabilityTest.kt` (shared; one line per unit)
  - NEW, patch-borne `test/…/core/renderer/PositionedParentSlotSeamWiringTest.kt`
  - `tools/titan/results/wave55-positioned-parent-slot/`
  - seam 1 (`ComponentRenderer.kt`)
  - `CanvasRootHoist.kt` is "reserved" to L3, but read-only.
- **L4** (`ua-heading-margin.md:320-363`).
  - Compose:
    - NEW `spacing/UaPositionedHeadingMargin.kt` and its test;
    - NEW, patch-borne `spacing/UaPositionedHeadingMarginSeamWiringTest.kt`;
    - `typography/UAElementFontRule.kt` (199 lines, sha `c78f3557…`);
    - `typography/UAElementFontRuleTest.kt` (243 lines, `4c9dd125…`);
    - patch-borne edits to `typography/UAElementFontRuleSeamWiringTest.kt` (`1c67fe44…`);
    - RESTORED `typography/UAHeadingFoldGate.kt` (held copy, 105 lines, `e0e3186d…`) and its test (held copy, 130 lines,
      `0d99cd9a…`).
  - Swift:
    - NEW `StyleEngine/spacing/UAPositionedHeadingMargin.swift` and its tests;
    - NEW, patch-borne `UAPositionedHeadingMarginSeamWiringTests.swift`;
    - `StyleEngine/typography/UAElementFontRule.swift` (286 lines, `27cd64a9…`; docs only);
    - `Tests/…/UAElementFontRuleTests.swift` (`58b3f1fa…`);
    - RESTORED `UAHeadingFoldGate.swift` (held copy, 85 lines, `2ca760a6…`) and `UAHeadingFoldGateTests.swift` (held copy, 214
      lines, `5c275b88…`).
  - `tools/titan/results/wave55-ua-heading-margin/`.
  - Seams 1–4.
  - `held/SHA256SUMS.txt` is 12/12 OK (re-run). Every held file is tracked.
- **L5** (`counter-suffix-rows.md:240-264`).
  - Swift:
    - NEW `StyleEngine/lists/ListMarkerBidi.swift`;
    - NEW `StyleEngine/lists/ListMarkerSuffixGap.swift`;
    - `StyleEngine/lists/ListMarkerRow.swift` (230 lines, doc only);
    - `StyleEngine/typography/font-variant/FontVariantNumericApplier.swift`;
    - `StyleEngine/typography/TypographyAggregate.swift` (238 lines, +1 field);
    - `Renderer/StyleBuilder.swift` (1543 lines, +2 lines);
    - comment-only edits to `ListMarkerOutsideHang.swift` (sha `b50d9bd1…`), `KoreanHangulFormal.swift`,
      `ListMarkerText.swift` and `ListMarkerStyle.swift`;
    - three NEW test files.
  - Compose:
    - NEW `lists/ListMarkerSuffixGap.kt`;
    - `lists/ListMarkerRow.kt` (290 lines, KDoc only);
    - `lists/ListMarkerOutsideHang.kt` (comment only);
    - NEW `lists/ListMarkerSuffixGapTest.kt`.
  - The lane dir.
  - Seams 1–6.
- **L6** (`android-paint-order.md:274-302`), all under `runtimes/compose/src/`:
  - NEW `main/…/borders/BehindContentBorder.kt`
  - `borders/radius/BorderRadiusApplier.kt` (281 lines, `18fc3849…`)
  - NEW `test/…/borders/BehindContentBorderTest.kt`
  - `test/…/borders/BorderRadiusSelfPaintTest.kt` (`0fde0df8…`)
  - `borders/sides/BorderSideApplier.kt` (636 lines, `7bcc1fe7…`)
  - `borders/BordersFacade.kt` (129 lines, `5c8ff455…`)
  - **`StyleApplier.kt`** (1188 lines, `ae930e03…`)
  - NEW `test/…/borders/sides/BorderSidesBehindContentTest.kt`
  - NEW `test/…/effects/EffectsClipFilterOrderTest.kt`
  - No seam. Every sha prefix was re-computed and matches.
- **L7** (`instruments.md:234-263`):
  - wave55-plan U1 files (`flex-zero-gap-rules.geometry.py`, `.fakes-run.sh`, `.geometry.out.txt`, `instruments-flex-gap-fakes/`)
  - NEW `tools/titan/wire-differential.{sh,mjs,test.mjs}`
  - NEW `tools/titan/results/wave55-gate/ab-arms55.{sh,selftest.sh,selftest.out.txt}`
  - `schema/spec/03-children.md`
  - `runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts`
  - `runtimes/web/src/renderer/InlineRuns.ts`
  - NEW `runtimes/web/tests/renderer/InlineRunsLatitudeCitation.test.ts`
  - optional `apps/web-harness/src/ui/ComposedRootSeparator.ts`
  - `tools/titan/bidi-marker-paint.mjs`
  - `tools/titan/bidi-bake.test.mjs`
  - U6 tests:
    - `HyphenateCharacterExtractorTest.kt` (existing);
    - NEW `PreBreakPipelineClampHyphenTest.kt`, `SoftHyphenCutsTest.kt`, `OutOfFlowContainingBlockBranchesTest.kt`,
      `HyphenateCharacterCascadeTests.swift`, `OutOfFlowContainingBlockBranchesTests.swift`,
      `InertOutOfFlowWordJoin.branches.test.tsx` and `tools/visual/label-chrome-check.test.mjs`
  - the lane note.
  - The name `wire-differential.sh` already exists as the wave-53 record `tools/titan/results/wave53-lists-bakes/wire-differential.sh`.
    L7's new path is different, so they do not collide. The brief does not mention that predecessor.

**Result: 0 paths owned by two lanes.** The near-collisions each brief raised all resolve one way:

| path | owner | other lane's relation | resolution |
|---|---|---|---|
| `runtimes/compose/src/main/java/com/styleconverter/runtime/StyleApplier.kt` | L6 (U2, three call-site lines at :753-754) | L2 reads it: U2's argument quotes the chain :702 → :758 → :786 | L6 owns it, and `u2-styleapplier.patch` is NOT needed. But L2's chain-order claim must be re-anchored by content after L6 U2 lands, because L6 adds lines above :758. L6 keeps the borders and colours outer of the overflow clip at :758, so L2's order (sizing → overflow → padding) survives. clip-3 declares no border and is not in L6's 252-stem may-move set (checked), so L6 U2 is identity on it. |
| `…/core/variables/DynamicValueResolver.kt` | L2 | L4 calls `fontSizePxOf` through `UAElementFontRule.emBasePx` (read-only) | L4 re-runs its pins if L2 changes semantics (L4 says so). |
| `Renderer/StyleBuilder.swift`, `StyleEngine/typography/TypographyAggregate.swift` | L5 (U3) | L1 lists both read-only (`hyphen-ios-linebox.md:208-210`) | L5's conditional (`counter-suffix-rows.md:304-306`, §9 R5) is void. L1's pin P7 reads `StyleBuilder.build(…).text`, and L5's fields are additive. |
| `tools/titan/bidi-bake.test.mjs`, `bidi-marker-paint.mjs` | L7 (U5) | L5 lists `bidi-marker-bake.mjs` read-only | L7's conditional (`instruments.md:292`, §9 R2) is void: L5 claims neither file. |
| `…/typography/UAElementFontRuleSeamWiringTest.kt` | L4 (patch-borne) | L7 claims no shared test-pin file | L4's conditional (`ua-heading-margin.md:330`) is void. |
| `…/core/renderer/SeamReachabilityTest.kt` | L3 only | — | See `seam-registry.md` §5. Three more seam-only Compose modules (L4 ×2, L5 ×1) have no line. If the plan extends the wave-52 rule, they ride patch-borne, landing L4 → L3 → L5. |
| `CanvasRootHoist.kt` | reserved to L3, no hunk | read-only for L4 (`layout/position/**`) and L6 | no edit anywhere |

**Orphan hand-offs** (a lane hands work to an owner who does not list it):
- **O1.** `tools/titan/counter-style-table.mjs:21-23`, the stale SUFFIX NOTE.
  - L5 hands it to "L7's tree" (`counter-suffix-rows.md:234-236`, `:308`). L7's ownership list (`instruments.md:234-263`)
    does not include it.
  - Its corrected text depends on L5 U2 ("a U+3001 suffix has no stand-in gap").
  - **Proposed:** L5 owns it as a comment-only path in unit D, landed after U2o. It is on the extract path (the counter
    bake) but a comment changes no wire byte.
- **O2.** The pin for `ContainingBlock[dark-stage-content-box-bands]`.
  - L7 says it "rides with L2's CBB unit" (`instruments.md:541-542`).
  - L2 names the breadcrumb in U1's rule (`cbb-android.md:234-235`), but none of its U1 pins asserts it (`:280-286`).
  - **Proposed:** add a breadcrumb-assertion row, with its mutation (delete the `markUnhandled`), to L2 U1's restored
    `DynamicValueResolverTest` rows.
- **O3.** The stale "layout-time add is future work" phrase in `ContentBoxRelativeInflationTest.kt`.
  - L2 hands it to "the docs / L7 lane" (`cbb-android.md:218`). L7 does not list it.
  - **Proposed:** the closing docs pass, or L2 U2 as a one-line comment edit to a test file it already depends on.

## 2. Seam registry

Written to **`seam-registry.md`**. Summary:
- **`ComponentRenderer.kt`** (sha `2445d9ea…`, 8488 lines), top to bottom:
  - L4 seam 1 (:1157-1165) and seam 3 (:1161), one shared context, strict order 1 → 3;
  - L2 seam 1 (:1950-1951);
  - L3 seam 1 (:3633-3661);
  - L5 seam 4 (:4482) and seam 5 (:4665).
- **`ComponentRenderer.swift`** (sha `417853ce…`, 6291 lines):
  - L4 seam 2 (:340-356) and seam 4 (:352-356), overlapping, strict order 2 → 4;
  - L5 seam 1 (:3973, :4561, :4602 / :4626 / :4650), seam 2 (:4618) and seam 3 (:4646). Seam 3 sits 4 lines above
    seam 1's :4650 hunk, so the lane must dry-run reverting U1-ios with U2i-ios applied;
  - L1 seam 1 (:5055-5160), plus the contingency seam 1b (:5335);
  - L5 seam 6 (:5537, which becomes about :5538 after L1).
- **`ComponentRenderer.tsx`** (sha `c780ac1c…`, 1479 lines) and **`extract-fixture.mjs`** (sha `6ab6f2b8…`, 12268 lines):
  no hunks.

Every anchor was re-read at HEAD and its quoted text is there (`briefs-audit.anchors.out.txt`). No two lanes touch the same
region, so the only ORDERING constraints are inside L4 and inside L5. Two more facts:
- No existing test pins renderer source text inside any wave-55 region, apart from L4's own
  `UAElementFontRuleSeamWiringTest.kt`.
- L2's §3 / R4 worry ("L3's seam may sit near :1979") is moot: L3's seam is at :3633.

## 3. Pointer audit (tracked paths, `:LINE` anchors, cells)

**Paths** (`briefs-audit.pointers.out.txt`): 812 path tokens; 761 resolved directly. Each of the other 51 was checked by
hand, and **none is a failure**:

| class | count | examples (brief:line) |
|---|---|---|
| NEW or RESTORED files, correctly absent at HEAD | 27 | `hyphen-ios-linebox:219` NormalLineBox.swift · `ua-heading-margin:341-342,359-360` UAHeadingFoldGate.{kt,swift} + tests (the held copies exist and are tracked) · `counter-suffix-rows:314-319` · `instruments:259,332,352,381,389,393,421` |
| shorthand for existing files beside the brief | 7 | `hyphen-ios-linebox:33` `.look-001…004.png`, `:447` `.replays.out.txt` / `.fakes.out.txt` · `counter-suffix-rows:12,510` `.wave54-open.out.txt` · `instruments:16,18` `.out.txt` |
| paths relative to a run, a ref or the WPT mirror (all exist) | 5 | `hyphen-ios-linebox:80` ref `css-text/hyphens__hyphenate-character-001.png` · `:125` per-test IR · `cbb-android:109` per-test IR · `positioned-parent-slot:75` ref `selectors/dir-style-02a.png` · `counter-suffix-rows:141` `tools/wpt/css/css-counter-styles/counter-suffix-ref.html` |
| lane deliverables not yet written | 5 | `cbb-android:208` seam-1.patch · `android-paint-order:296` u2-styleapplier.patch (contingent) · `instruments:262` lane note · `:433` self-test outputs ×2 |
| shell placeholders | 4 | `instruments:161` `$HERE/build-hashes.txt` (= `wave54-gate/build-hashes.txt`, which exists) · `:404,405,415` `$STAGE/<arm>.*` |
| absent by the brief's own statement | 2 | `instruments:53` `tools/titan/wl3-wire-differential.sh` (the correction) · `:541` `ContainingBlockBands.kt` ("not on HEAD") |
| external, not a repo path | 1 | `counter-suffix-rows:329` DerivedBidiClass.txt (Unicode data) |

**Anchors** (`briefs-audit.anchors.out.txt`: 314 lines).
- Every `file:L` and every bare `:L` lies inside its file, and the quoted identifier sits on the line, with one near-miss:
  - `ua-heading-margin.md:316` gives `inFlowChildren` as `ComponentRenderer.swift:780`. That is its doc line; the
    declaration is at :781. Not a failure.
- The read-out's "OUT-OF-RANGE" lines (`positioned-parent-slot:39`, `ua-heading-margin:391`) and its BARE lines are its own
  context mis-binding. Each was re-read by hand at the file the brief means, and resolves:
  - Compose :3504-3512 `transformCbHostsOutOfFlowChild`;
  - :1161 / :1162 `standsDown` / `)`;
  - Swift :5175 / :5295 / :5335 / :5365 / :5385;
  - `SizingApplier.kt:276`;
  - `SizingExtractor.kt:219`;
  - `CanvasRootHoist.kt:806` `zeroFlowAnchor`;
  - `UAElementFontRule.kt:109` / `:124`, `.swift:200` / `:160-171`;
  - `EffectsFacade.kt:162` / `:168` / `:192` / `:205` / `:225` / `:234`;
  - `wl3-wire-differential.sh:35` / `:39-40` / `:43` / `:48` / `:50`;
  - `ab-arms54.sh:5` / `:32` / `:35` / `:36` / `:39`;
  - `03-children.md:191` / `:210-214` / `:216`;
  - `wave54-web-tail/_note.md:77`;
  - `SoftHyphenCuts.kt:77`;
  - `bidi-marker-paint.mjs:150-152`.

**Patch claims, re-executed with `git apply --check`.** All five match their briefs:
- `wave54-oof-layout/seam-1.patch`: rc 0 (L2 re-cuts it; header offsets stale, as stated);
- `held/seam-1.foldgate.patch`: rc 1 at :1138 (L4: "does NOT apply");
- `held/seam-2.foldgate.patch`: rc 0;
- `held/UAElementFontRuleTests.swift.foldgate.diff`: rc 0;
- `held/UAElementFontRule.swift.foldgate.diff`: rc 1 at :77 (L4: "does NOT apply");
- `wave54-rtl-marker-bake/hunk-for-orchestrator-1.patch`: rc 0 (L5).

**Cells.**
- `briefs-audit.cells.out.txt`: 187 / 187 transcribed §1 / §5 / §6 / §9 cells equal their named runs (`wave54-final`,
  `wave54-open`, `wave54-probe`, `wave54-ab-drop-U3b`), compared at the precision written.
- The 44 fully written inline cells in `.pointers.out.txt` match as well.
- The unscored claims hold in the manifests:
  - `cjk-decimal/css3-counter-styles-001` / `-004` ios + android: `scoreExcluded: native-font-parity`, ssim 0.9653 /
    0.9039 and 0.7098 / 0.6715. Their web cells ARE scored (P 0.9933 / P 0.9878) and are not L5 carriers.
  - `backdrop-filter-clip-rect-zoom` and `anchor-center-safe-rtl`: `scoreEligible: false`.

**One environment claim is not reproduced.** `cbb-android.md:509-511` (R7) says `/usr/bin/python3` has no PIL on this host.
In this shell, `/usr/bin/python3` (Xcode 3.9) imports PIL 11.3.0 from `~/Library/Python/3.9`, even with `env -i`. All seven
wave-55 probes, plus `compose-wpt-content-box-cb.geometry.py` and `geometry-gate.py`, parse under it. The expectations
`cmd` lines should still name ONE interpreter for every probe.

## 4. Prediction sanity

**"From" values.** Every §6 "from" equals the `wave54-final` cell (§3). The partial `wave55-open` already agrees: 21 of its
30 sections are scored (CSS2 … css-tables), and 2816 / 2816 of their cells are identical to `wave54-final`, 0 differ, 0
one-sided. The sections still pending at audit time hold most targets: css-text, css-text-decor, css-transforms, css-ui,
css-values, css-view-transitions, css-writing-modes, filter-effects and selectors.

**Floors.** Every geometry-gated row floors at 0.95 in L1, L2, L5, L6 and L7. **Three rows deviate:**
- **F1** · `positioned-parent-slot.md:428` ("same, U1 only", gated by `row7`) and `:434`
  (`anchor-position-multicol-nested-001 android`, gated by `[W] … stack`) have floor `—`. Both are predicted to stay f.
- **F2** · `ua-heading-margin.md:481-482` (`inset-005` / `-006 android`, `[M]` gating, predicted **P ≈0.967** DEGENERATE)
  have floor `—`. This is a geometry-gated row predicted to pass with no 0.95 floor.
- **F3** · `ua-heading-margin.md:483-484` (`inset-011 ios` / `android`, `[M]` gating) have floor `—`. Both stay f.

The proposal for each is in §8 (C11).

**Targets are scored.**
- Every target and gating cell is scored at `wave54-final` under the manifest idiom.
- The only unscored carriers are capture-only, and their briefs say so: L5 `-001` / `-004` natives, L6 `clip-rect-zoom`
  (report key).

**Union movement against `wave55-open`** (which should equal `wave54-final`: web 1243 / iOS 1131 / Android 1128; re-derived
from the briefs' §6):

| platform | flips (unit, tier) | total |
|---|---|---|
| web | none (L7 U4 changes a console string only) | 1243 |
| iOS | +1 L1 `hyphenate-character-004` (MED); +3 L4 `inset-005` / `-006` (MED), `-014` (MED-LOW) | **1135** |
| Android | +1 L2 `nested-border-radius-clip-3` (MED-HIGH); +1 L3 `dir-style-02a` (MED-HIGH); +1 L4 `inset-014` (MED) — plus L4 `inset-005` / `-006` P **DEGENERATE** (MED-LOW, never claimed, but counted by the scorer) | **1131 faithful (1133 at the scorer)**; 1130 under L4's GO-SMALL fallback (U2-android held) |

- **Lost 0 · newly measured 0 · unmeasured-now 0** in every brief, so the pre-registered lost list is EMPTY.
- Pre-registered downward movers:
  - L5 `css3-counter-styles-005 android` −0.0018 (stays P);
  - L3 `anchor-position-multicol-004 android` undirected, |Δ| ≤ 0.01, 0.95 must-not-lose;
  - L6 `grid-abspos` rows ±0.0005;
  - L6 `backdrop-filter-basic-blur android` undirected (ring-fenced, reported plainly).

**P→P picture-correct claims** (each only on its own key at the closing gate):

| lane | claims |
|---|---|
| L1 | 2 |
| L2 | 6 |
| L3 | 2 gating, plus `dynamic-offset-vrl-rtl-001` on a report key |
| L5 | 4 |
| L6 | 7 |

**`stayDegenerateEvenIfPass` union:**
- L1 `hyphenate-character-002 ios`;
- L3 `dynamic-offset-rtl-002` / `-vrl-rtl-002 android` and `anchor-position-multicol-004 android`;
- L4 `inset-005` / `-006 android`;
- the counter-suffix natives keep the label unless L5's `[H] [C] [T] [K]` (iOS) or `[C] [K]` (Android) print OK.

**Revert units: 28 commits** (+1 contingency):

| lane | units | count |
|---|---|---|
| L1 | NB-ios (+ NB-ios-b contingency) | 1 |
| L2 | U1, U2 | 2 |
| L3 | U1, U2 | 2 |
| L4 | U1-android, U1-ios, U2-android, U2-ios | 4 |
| L5 | U1-ios, U2o-ios, U2i-ios, U2o-android, U2i-android, U3-ios, D | 7 |
| L6 | U1, U2, U3 | 3 |
| L7 | U1, U2, U3, U4, U5, U6-compose, U6-swift, U6-web, U6-tooling | 9 |

Every revert order is inside one lane, with one exception: L1 NB-ios must revert before the wave-54 U3b, never U3b alone.

**Gating geometry keys: 43.**

| lane | gating keys |
|---|---|
| L1 | 3 |
| L2 | 8 (7 reused + 1 supplementary) |
| L3 | 7 |
| L4 | 12 |
| L5 | 6 |
| L6 | 7 |
| L7 | 0 |

The executed calibrations on `wave54-final` hold the self-test shape. Tallies of the outputs beside each brief:
- L6 38 OK / 8 WRONG;
- L5 gating 6 FAIL / control 10 OK / report 2 FAIL;
- L3: all 7 gating WRONG. The 16 WRONG lines include 7 iOS lines that are not keys;
- L4: 14 native `[M]` / `[F]` WRONG;
- L1: 3 gating WRONG / 6 controls OK;
- L2: (a) 7 gating FAIL, (b) 1.

## 5. Carrier union and must-not-move collisions

**Capture carriers, union** (no stem is a carrier of two lanes on one platform):
- **iOS 23:**
  - L1 14: `hyphenate-character-001…004`, `boundary-shaping-001…008`, `text-decoration-dotted-001` / `-002`;
  - L4 4: `inset-005` / `-006` / `-011` / `-014`;
  - L5 5: `counter-suffix`, `counter-style-at-rule__disclosure-styles` (identity predicted), `css3-counter-styles-001` /
    `-004` / `-005`.
- **Android 58:**
  - L2 14: `abspos-autopos-{htb,vlr,vrl}-{ltr,rtl}`, `nested-border-radius-clip{,-2,-3,-4}`, `semi-replaced-stretch-{button,input,other}`,
    `flex-gap-decorations-006`;
  - L3 9: `dir-style-02a`, `anchor-position-multicol-nested-001` / `-004`, `bidi-tab-001`, `hyphenate-character-005`,
    `dynamic-offset-{rtl,vrl-rtl}-00{1,2}`;
  - L4 4;
  - L5 4: `counter-suffix`, `css3-counter-styles-001` / `-004` / `-005`;
  - L6 27: `clip-rect`, `clip-rect-zoom`, and the 25 listed at `android-paint-order.crossings-scan.out.txt:39-64`.
- **web 0 · wire 0.** L7 U5 is predicted wire-identical and is gated by [W-L7a].
- **L6 may-move:** 252 Android stems (`.crossings-scan.out.txt:66-`). A move of |Δ| ≤ 0.002 is allowed there, so these
  stems are not byte-identity.

**Collisions** (a lane's must-not-move list or identity read contains another lane's carrier or may-move stem). The
pictures were looked at for the shared stems: ref | Android | iOS sheets of `abspos-autopos-htb-rtl`, `disclosure-styles` and
`anchor-center-safe-rtl`.

| # | lane rule (brief:line) | collides with | evidence |
|---|---|---|---|
| X1 | L1 in-radius tripwire: `disclosure-styles ios` must not move (`hyphen-ios-linebox.md:358`) | L5 U1-ios carrier, identity predicted (`counter-suffix-rows.md:402`) | both predict byte-identity, but a move would be attributed to two units |
| X2 | L5: "the other 44 Android css-counter-styles captures decoded-pixel identical" (`counter-suffix-rows.md:420, 556`) | L6 U2 may-move `counter-style-at-rule__disclosure-styles` (Android) | looked at: bordered boxes whose marker ink touches the right band (`closed rtl•`) |
| X3 | L7 U5: `counter-suffix` ×3 and 30 bidi-baked cells identical (`instruments.md:588-590`); L7 probe 2 registers `rtl-marker-bake [M] counter-suffix` with `contains: rows 0-207 identical` (`:640-646`) | L5 carriers (`counter-suffix` ios / android); L3 carriers (`dir-style-02a`, `bidi-tab-001`, `hyphenate-character-005` android) | L7 makes its own claim conditional (`:588`, `:646`). L5 does carry counter-suffix, so the condition fires |
| X4 | wave-54 keys still in `wave54-plan/expectations.json`: `rtl-marker-bake [M] counter-suffix` web / ios / android (gating) and `lists-bakes counter-suffix` web / android (gating) and ios (report), all with `contains: rows 0-207 identical to <base>` | L5 U1 / U2o move rows 3–6 (iOS) and 5–6 (Android) by design | L5 flags it (`counter-suffix-rows.md:536-538`, R6) |
| X5 | wave-54 `ua-heading-face.geometry.py` CONTROL keys `text-decoration-inset-011 ios` / `android` (`expectations.json`) and the wave-54 must-not-move rows for inset-005 / 006 / 011 / 014 natives | L4 carriers (U1 moves inset-011 by 21 px) | L4 flags it (`ua-heading-margin.md:448-450, 557-560`) |
| X6 | wave-54 `flex-zero-gap-rules` gating keys `flex-gap-decorations-033 ios` / `android` | L7 U1 moves them to CONTROL on the wave-55 copy | L7 flags it (`instruments.md:309-310`) |
| X7 | L3: "every Android capture outside the 9 carriers: 1426" must not move, including "the 40 host-active LTR documents mounted today" (`positioned-parent-slot.md:441-443`) | L2's 14, L4's 4, L5's 4, L6's 27 Android carriers. L3's own census lists `backdrop-filter-clip-rect` / `-clip-rect-zoom` (L6 U1 carriers), `-edge-clipping` (L6 may-move) and `-basic-blur` (ring-fenced, moved by L6 U2) as host-active (`positioned-parent-slot.census.json` `parents`) | union |
| X8 | L3 `[C] anchor-center-safe-rtl android` "identical to <base>" (`positioned-parent-slot.md:487`) | L6 U2 may-move | looked at: on Android two bordered abspos boxes cross ("…nchored element with justify-self" under the align-self box). A band-behind-content change can move their pixels |
| X9 | L2 probe read 4: "filter-effects … Android differs on exactly the 4 nested-clip stems" (`cbb-android.md:471-472`) | L6 U1 carriers `clip-rect`, `clip-rect-zoom`; L6 may-move `edge-behavior`, `edge-clipping`, `inline-positioning` | union |
| X10 | L6 decisions: revert U2 on "an Android mover outside the may-move set" or "a may-move non-carrier with \|Δ\| > 0.002" (`android-paint-order.md:561-566`) | L2: `nested-border-radius-clip{,-2,-3,-4}` are outside the may-move set and move by +0.02 to +0.04. L2: `autopos-htb-rtl` is may-move and moves +0.0097, `semi-replaced-stretch-*` +0.01 to +0.04. L3: `anchor-position-multicol-nested-001` is may-move and moves +0.012 | the rule would revert L6 U2 for L2's or L3's moves. Looked at `autopos-htb-rtl`: the borders are `solid white` (the red is `.flex`'s background), and L2 U1 moves the green child. That is not a band under a child |
| X11 | L1 probe KEEP: "every web and Android css-text capture is byte-identical to wave55-open" (`hyphen-ios-linebox.md:501`) | L3 U2 carriers `bidi-tab-001`, `hyphenate-character-005` (Android, css-text); 21 L6 may-move css-text Android stems | union |
| X12 | L3 probe expect: "every other selectors capture × 3 byte-identical" (`positioned-parent-slot.md:541`) | L6 may-move `invalidation__nth-child-of-attr-largedom` (Android) | union |
| X13 | L2: "every Android cell outside the 14 documents" must not move (`cbb-android.md:362`); L1: "every web and Android cell (all 2734)" (`hyphen-ios-linebox.md:365`); L5: "every capture outside css-counter-styles" (`counter-suffix-rows.md:423`); L6: "Android outside the 252-stem may-move set" (`android-paint-order.md:455`) | each one includes the other lanes' carriers | union |

**Checked, and not a collision:**
- L2's must-not-move `cross-fade-target-alpha` ×3: no other lane's carrier, and not L6 may-move.
- L4's `[C]` set (inset-001…024 natives, block-in-inline-015-print, s-11-1-1b-005): L6 may-move holds only `inset-025`.
- L6's 10 `[ID]` stems: none is another lane's carrier.
- L7's `flex-gap-decorations-033` and `hyphens-out-of-flow-001` / `-002`: no other lane's carrier.
- L2's U1 moves the `semi-replaced-stretch-input` / `-other` Android captures. They are wave-54 `web-root-separator`
  CONTROL keys whose expect is an exact WRONG string. I executed that probe on `wave54-probe` (HEAD + the old U1, on device):
  its four lines are verbatim the `wave54-final` lines, so U1 does not break them.

## 6. Device-probe plan (§8 of each brief)

| lane | §8 sections | lane's own run id |
|---|---|---|
| L1 | css-text | (unnamed probe run) |
| L2 | filter-effects | `wave55-cbb-probe` |
| L3 | selectors (optional extension: css-text, css-writing-modes) | `wave55-pre` |
| L4 | css-text-decor (+ an A/B arm dropping U2-ios and U2-android, same section) | (unnamed) |
| L5 | css-counter-styles | `wave55-l5-probe` |
| L6 | filter-effects, css-masking, css-flexbox, css-grid | `wave55-l6-probe` |
| L7 | none (U5 is proven by the Chromium-plus-converter wire differential [W-L7a] over css-anchor-position, css-backgrounds, css-counter-styles, css-text, css-writing-modes and selectors: no device) | — |

**Union for the ONE stage-1 probe:**
- css-text
- css-text-decor
- css-counter-styles
- selectors
- filter-effects
- css-masking
- css-flexbox
- css-grid
- **css-writing-modes**

That is 9 sections, about 22 min on a quiet host at the 2.5 min/section rate.
- **css-writing-modes** is L3's own §8 extension. I include it because L3 U2 positions boxes on Android and its
  `[S] dynamic-offset-rtl-001` / `[W] dynamic-offset-rtl-002` gating keys live there. Without it, U2's host-active origin is
  first measured at the closing gate (L3 R3), against the wave-53 lesson (native probe before gate).
- **The union buys reads the lanes did not plan.**
  - css-flexbox (L6) measures L2 U1's three `autopos-*-ltr` gating keys and three report keys before the gate.
  - css-text (L1) measures L3 U2's `[S] bidi-tab-001 android rtl-rows` gating key.
  - css-text-decor (L4) measures L1's `text-decoration-dotted` STACK report keys.
- **Left for the closing gate, as the briefs say:**
  - L2's css-position / css-gaps carriers (device captures already exist at `wave54-probe`, tree diff = the unit);
  - L3's `anchor-position-multicol-*` (css-anchor-position);
  - L6's `[ID]` rows in css-break, css-backgrounds and CSS2;
  - L1's radius controls in css-cascade / css-tables.
- **Other orchestrator windows:**
  - L6 [W1] (`SKIP_IOS=1 ./test-all.sh fixtures/properties/effects/filter-functions.json`, evidence only);
  - L7 [W-L7a] (Chromium plus converter);
  - the closing-gate A/B arms: L1 drop-NB-ios (css-text, css-text-decor; iOS), L6 drop-U2, and L4's probe-time drop-U2 arm.
  - L7 U3's `ab-arms55.sh` is the runner for all of them.

**Readout constraint.** `geometry-gate.py --lanes` narrows by lane, not by section, so on a 9-section run every key in an
unprobed section prints UNMEASURED (exit 3; noted by L3 at `positioned-parent-slot.md:528-530`). The stage-1 readout needs a
section filter, or the readout must treat UNMEASURED as expected for keys outside the probe's sections.

## 7. STATUS lines

All seven briefs end in exactly one `STATUS: COMPLETE`, as their last non-blank line:
- `hyphen-ios-linebox.md:548`
- `cbb-android.md:527`
- `positioned-parent-slot.md:584`
- `ua-heading-margin.md:725`
- `counter-suffix-rows.md:617`
- `android-paint-order.md:629`
- `instruments.md:698`

## 8. Conflicts to resolve at plan synthesis (with the proposal for each)

**C1 · One probe run, read with the union carrier set.**
- Five lanes name five probe runs: `wave55-cbb-probe`, `wave55-pre`, `wave55-l5-probe`, `wave55-l6-probe`, and L1 / L4
  unnamed.
- **Proposal:**
  - one stage-1 run (for example `wave55-probe`) over the 9 sections of §6, on the integrated tree;
  - restate every lane's §8 read command with that id;
  - restate every identity or "outside carriers" rule as: outside the UNION carriers (iOS 23 / Android 58), and for
    Android outside L6's 252 may-move stems, which have their own |Δ| ≤ 0.002 band.
- This one restatement settles X7, X9, X11, X12 and X13.

**C2 · L6's U2 revert rules must not fire on L2 or L3 movers (X10).**
- **Proposal:** L6's "outside the may-move set" and "may-move non-carrier |Δ| > 0.002" rules exclude the union carriers of
  L2 and L3. Attribution of those stems goes to the L6 drop-U2 arm.
- If the arm is not run before the decision: L2's 14 and L3's anchor-position stems are read by their own geometry keys
  only.

**C3 · L7 U5's counter-suffix claim is wire-only (X3).**
- **Proposal:**
  - do NOT register L7 probe 2 (`rtl-marker-bake [M] counter-suffix … rows 0-207 identical`);
  - L7 U5 is gated by [W-L7a]'s wire identity alone;
  - L7's capture must-not-move list drops `counter-suffix` ios / android and L3's `dir-style-02a`, `bidi-tab-001` and
    `hyphenate-character-005` Android cells. They stay wire-identity rows.

**C4 · Retire or reclassify the wave-54 keys the wave-55 units break by design (X4, X5, X6).**
- `rtl-marker-bake [M] counter-suffix ios / android` and `lists-bakes counter-suffix ios / android`: drop the
  `rows 0-207 identical` `contains` clause (L5's `[K]` replaces it). Web keeps it.
- `ua-heading-face … inset-011 ios / android`: control → superseded by L4 `[M] text-decoration-inset-011`.
- The wave-54 must-not-move rows for the 8 inset natives are removed.
- `flex-zero-gap-rules … 033 ios / android`: gating → control, read from L7's wave-55 copy.

**C5 · `disclosure-styles` (X1, X2).**
- **Proposal:**
  - iOS: it is L5's U1-ios carrier (identity predicted, "read the picture if it moves"). It leaves L1's R1 tripwire, which
    keeps 4 controls, and a move is attributed by L5's U1 revert or L1's drop-NB-ios arm.
  - Android: it leaves L5's 44-capture identity read, as an L6 may-move stem.

**C6 · L3 control `[C] anchor-center-safe-rtl android` (X8).**
- **Proposal:** drop it from L3's control set. It is unscored, and it is L6 may-move.
- Or keep it, attributed to L6 by the drop-U2 arm, and never read as an L3 failure without that arm.

**C7 · Orphan O1:** `tools/titan/counter-style-table.mjs:21-23` SUFFIX NOTE → L5 unit D, after U2o. It is comment-only.

**C8 · Orphan O2:** the `ContainingBlock[dark-stage-content-box-bands]` breadcrumb pin → L2 U1's `DynamicValueResolverTest`
rows, with its mutation.

**C9 · Orphan O3:** the `ContentBoxRelativeInflationTest.kt` "future work" phrase → the docs pass, or L2 U2.

**C10 · `SeamReachabilityTest.kt`.**
- L3 is its sole claimant.
- Decide whether L4's `UaPositionedHeadingMargin.kt` / `UAHeadingFoldGate.kt` and L5's `ListMarkerSuffixGap.kt` (all
  seam-only Compose modules) also get lines.
- If so, each line rides patch-borne in its own seam patch, landing L4 → L3 → L5 (`seam-registry.md` §5).

**C11 · Floors (F1–F3).**
- **Proposal:**
  - L4 `inset-005` / `-006 android` are predicted P and `[M]`-gated, so they take floor **0.95** per the wave-54 lesson.
    Their `stayDegenerateEvenIfPass` label still stops any claim.
  - The f→f gated rows take an explicit `floor: n/a (stays f; direction-only, rule 2)`, so the adjudicator does not read a
    missing floor as 0. These are L3 "U1 only" and `multicol-nested-001`, and L4 `inset-011` ×2.

**C12 · Intra-lane seam revert dry-runs.**
- L4 already proves both orders, for seam 3 / seam 1 and seam 4 / seam 2.
- L5 must add one: revert U1-ios (`ComponentRenderer.swift` :4650) with U2i-ios (:4646) applied, in an export tree.

**C13 · Re-anchor L2 U2's StyleApplier chain claims after L6 U2.**
- L2 quotes :702 / :758 / :786. L6 adds lines at :753-754, before :758.
- **Proposal:** land L6 U2 before L2 U2's pins are finalised, or cite the chain by symbol, not by line.

**C14 · Interpreter.** Every expectations `cmd` should name the same python. `/usr/bin/python3` (Xcode 3.9) has PIL here,
which contradicts L2 R7. Pin one interpreter in the plan.

**Not conflicts, recorded:**
- The L6 [W1] and L7 [W-L7a] windows need Chromium or an emulator, but not the gate.
- L4's GO-SMALL fallback (U2-android held) is an orchestrator decision: Android 1130 instead of 1131 / 1133.
- Docs restamps move four suites: SwiftUI, Compose, web vitest and tooling.
- `tools/titan/results/wave53-plan/__pycache__/` (untracked, 12:56, not this audit's) is never staged (L5 R10).

STATUS: COMPLETE
