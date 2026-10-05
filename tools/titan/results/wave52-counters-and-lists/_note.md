# Wave 52 · L6 counters-and-lists — lane note

Plan: `tools/titan/results/wave52-plan/PLAN.md` §2 "L6", §3 rows `:4021ff` / `:4405-4480`, §4 step 5, §5, §9 C3/C9. Brief:
`tools/titan/results/wave52-plan/counters-and-multicol.md` §4 T1/T2/T3/T5/T7 + adjudication A. Tree: campaign/wave52, HEAD 7d9c22a7.
This run RESUMED a killed run: the code of T1/T2/T3(string form)/T5(pure geometry)/T7 was already in the tree, uncommitted, with no
note, no seam patches and no logs. Everything below was re-verified this run (2026-10-05); every mutation was RE-EXECUTED
(`mutations.log`, harness `mutate.py`, sha256 byte-exact restores).

## 1. What changed and why

| target | files (all lane-owned) | what |
|---|---|---|
| T1 korean twin | compose `lists/{ListStyleConfig,ListStyleExtractor,ListStyleApplier}.kt`, new `lists/KoreanHangulFormal.kt` | `KOREAN_HANGUL_FORMAL` arm; expansion byte-parallel with iOS `KoreanHangulFormal.swift` (css-counter-styles-3 §7.1); suffix `,` |
| T2 0-tall band | compose `borders/sides/BorderSideApplier.kt` | paint-only clamp `innerEdgeStrokeCentre = max(w/2, extent − w/2)` for BOTTOM/END + `doubleGeom`; band of a 0-tall box = [0,w). Fix pass (§11): `sideGeometry` / `doubleGeom` are now internal PURE functions of (side, width\|inset, box `Size`) the painters call with `DrawScope.size` |
| T3 web marker | web `engine/lists/ListStyleTypeApplier.ts`, `renderer/NodeRenderer.ts` | baked `meta.markerText` for (1) range-limited additive styles, (2) author names; **form chosen by `bakedMarkerPlan`** (see §2) |
| T5 outside hang | new compose `lists/ListMarkerOutsideHang.kt`, swift `StyleEngine/lists/ListMarkerOutsideHang.swift`, `ListMarkerRow.{kt,swift}` (`hangsOutside`) | pure geometry + Layout; wiring = `seam-1.patch` / `seam-2.patch` (DEVICE A/B) |
| T7 author @counter-style | `tools/titan/counter-style-bake.mjs`, new `counter-style-author.mjs`, `counter-style-descriptors.mjs` | `@counter-style` lifted out of `DYNAMIC_SIGNAL_RX`; §3 descriptors parsed into the PREDEFINED shape; `<script>` judged by a CSSOM recogniser (setters that take effect → bail `requires-script-mutation`; all-invalid setters → inert) |
| T7 native half (NEW) | new compose `lists/ListMarkerEmptyItem.kt`, swift `StyleEngine/lists/ListMarkerEmptyItem.swift` | an EMPTY inside `<li>` takes the Row/HStack, not the zero-size overlay; wiring = `seam-3.patch` / `seam-4.patch` |
| adjudication A | — (L12's file) | `hunk-for-L12-1.patch` — re-add armenian-008 to `NATIVE_FONT_PARITY_REFUSED_TESTS` + flip its two pins |

Tests: compose `lists/KoreanHangulFormalTest`, `lists/ListMarkerOutsideHangTest`, `lists/ListMarkerEmptyItemTest`,
`borders/sides/BorderSideZeroTallBandTest`; swift `ListMarkerOutsideHangTests`, `ListMarkerEmptyItemTests` (+ two raster pins that
ship INSIDE seam-2/seam-4); web `tests/lists/bakedMarkerListStyleType.test.tsx`; node `counter-style-bake.test.mjs` (T7 block),
`counter-style-author.test.mjs`.

## 2. Deviations from the plan (each measured)

1. **T3 form.** The plan says "emit the `<string>` form". The PNG replay (§4) falsifies that for armenian-008: the UA sheet gives
   `::marker` `font-variant-numeric: tabular-nums`, so `"10000. "` lays digits out wider (marker x219–301 vs ref 217–293) and row 3
   `10001. 10001` wraps → 7 ink bands vs the ref's 6, plain SSIM 0.947. Shipped rule: an INSIDE + childless item takes
   `list-style-type: none` + a leading `<span style="unicode-bidi:isolate">10000. </span>` (the ref's own `<bdi>10000. </bdi>` markup;
   css-lists-3 §3.5 — an inside marker is the item's first inline box); outside items / items with children keep the `<string>`.
   Shipped-rule census: 44 components / 7 tests → 39 inline, 5 string (counter-suffix hebrew ×4 outside, marker-text-matches-armenian ×1 has a child).
2. **T7 native half (not in the plan).** Probed on Catalyst: after L5's F-E the 51 cssom `<li>` are EMPTY inside items; both natives put
   their marker in the zero-size inside overlay over a 0-tall item → all three markers on ONE band (`[(16,28)]`) where the ref has three
   rows at 20 px. The +6 native cssom flips the plan predicts cannot land on that. `ListMarkerEmptyItem` (no text, no children, no pseudos,
   no declared block size) routes them to the Row/HStack: bands `[(16,27),(32,43),(48,59)]` — pitch 16 px vs the ref's 20 (residual stated).
3. `counter-style-bake.mjs` `UNMODELLED_TYPE` is now `' unmodelled'` (leading SPACE): HEAD held a raw NUL byte while its own comment
   says "leading SPACE"; both are impossible in a trimmed CSS token, so behaviour is identical, and the file is diffable text again.
4. `counter-style-descriptors.mjs:18` cite `css-values-4 §3.2` (NO-SUCH-SECTION, reported by L5) → `§4.2` (<custom-ident>);
   `node tools/visual/spec-cite-validate.mjs` → 0 unknown.

## 3. Census (`census.mjs` → `census.json`, wave51-fix, 1435 docs / 10 708 components / 4117 scored cells)

- T1: 1 component (counter-suffix `__0__3`), 1 test.
- T2: 3 rows / 3 tests (multicol-003, balancing-003, fieldset-001 — all P). Extended (own Height/Width below own far border, END via parent
  Width): 36 rows / 10 tests — the clamp repaints only a box whose LAID-OUT extent is below its stroke width; expected unchanged, watched.
- T3: 194 markerText carriers / 26 tests; shape 1 = 44 / 7 tests; shape 2 on today's wire = 0 (the old bake bailed every @counter-style doc).
- T5: 100 outside `<li>` in 19 tests (brief 127/26 also counted 31 `Display LIST_ITEM` non-li boxes, which `ListItemMarkerGate` keeps off
  the outside path, and 4 positioned li); 9 thin native passes (<0.98).
- T7: 26 @counter-style tests → baked 12 (8 newly stamped: cssom additive/fallback/name/negative/pad/prefix-suffix/range/symbols
  `-invalid`), bailed 11 (7 = the script-mutation wall — every VALID cssom setter test in the gate: additive-symbols, fallback, name,
  negative, pad, prefix-suffix, range; 3 `counter()`/`counter-reset` docs — descriptor-suffix, disclosure-styles, name-case-sensitivity;
  1 unreadable `<script>` — override-in-shadow-dom), skipped 3 (shadow-DOM docs). The gate carries 15 cssom tests (8 `-invalid`, 7 valid)
  of the WPT dir's 19.
- T7 native empty items: 12 now (name-case-sensitivity, floated, f ×3); 63 / 16 tests after F-E (+51 cssom).

## 4. PNG replay (`png-replay.mjs` → `replay/replay.json`, capture browser Chrome/151.0.7922.47, renderRefPng's exact steps, vs the FROZEN ref)

| test | native (today's web picture) | `<string>` form | shipped plan |
|---|---|---|---|
| armenian-008 | 0.9387, 5 bands (tofu) | 0.947, 7 bands | **1.0000, 6 bands = ref** |
| armenian-006 / -007 / -009 | 0.9945 / 0.9819 / 0.9983 | same | **1.0000** each |
| counter-suffix / marker-text-matches-armenian | 0.9982 / 0.9989 | same | same (string form — unchanged) |
| marker-text-matches-georgian | 0.9989 | same | **1.0000** |
| cssom pad / prefix-suffix / negative `-invalid` (source li, i.e. post-F-E) | 0.9968 / 0.9988 / 0.9942 | same | **1.0000** each |

(ssim.js library default — a trend, not the scorer's number.) Native replays: Catalyst rasters in seam-2/seam-4 (§6): counter-suffix row 1
marker x48–57 + text x64–85 vs ref 46–58 + 64–87; cssom pad-setter-invalid three rows at 16 px pitch vs ref 20.

## 5. Pins and executed mutations (`mutations.log`)

korean-arm → 2 red (`…was:<null>`, `…was:<DECIMAL>`); band-clamp → 4 red (−3/−5/−10/−0.5) on the PRE-fix-pass helper-only pins — superseded by §11
(side-bottom 3 red, side-end 1 red, double-bottom 2 red, band-clamp 6 red on the stroke-line pins); hang-width-kt / -swift → 175≠158 red;
empty-pseudos (kt / swift) → 1 red each; web-widen 3 red, web-narrow 4 red, web-inline 3 red (13-test file); bake-inrange 4 red;
author-pad 5 red; seam-2 raster red without the seam (text x82); seam-4 raster red without the seam (one band). All restores sha-equal.
Green runs: `compose-tests-final.summary.txt` (281 tests, lists.*/borders.*/MulticolFloatStrip* incl. `MulticolFloatStripRefRowsTest` 4/4 —
untouched as T2 requires), Swift list suites (seam-*.verify.log, swift-tests-1.summary.txt), web lists+renderer 143/143 and web-harness
`tests/sdui` 192/192, node counter-style 45/45.

## 6. Seam patches (orchestrator applies; none left edited)

ORDER: **seam-3 → seam-1** (Compose), **seam-4 → seam-2** (Swift). seam-3/seam-4 apply on HEAD; seam-1/seam-2 are cut against HEAD+3 / HEAD+4.
No `apps/web-harness/src/sdui/ComponentRenderer.tsx` patch is needed (§3's conditional T3 row): the harness renders every node through
`NodeRenderer` (`ComponentRenderer.tsx:1126`), which carries the T3 change; web-harness `tests/sdui` 192/192 with it.
- `seam-3.patch` (Compose empty-item, one expression) and `seam-4.patch` (Swift twin + `ListMarkerEmptyItemRasterTests`): **land WITH L5's
  `seam-6-FE-li.patch`.** Without them, F-E alone collapses every native cssom row onto one band (probe) — the 18 passing native cssom cells
  (`additive/fallback/name/range/symbols ±invalid`, P 0.976–0.988) are then at P→f risk. Hand-off to L5 + orchestrator.
- `seam-1.patch` / `seam-2.patch` (T5 hang + `ListMarkerOutsideHangRasterTests`): DEVICE A/B — apply against the closing gate and record the
  installed `base.apk` sha1 / `.app` hash; never blind-lifted.
- Verification under the per-file locks (sha before = after, locks released): Compose `seam-1.verify.log` (seam-3 alone and seam-3+1: 368
  tests, the only 3 failures are L9's in-flight line-clamp tests, which fail identically with the seam file at HEAD); Swift
  `seam-4.verify.log` / `seam-2.verify.log`. Compose WIRING has no JVM pin (no layout on the JVM) — the device A/B is its proof.

## 7. Predicted flips (wave51-fix → closing gate)

- armenian-008 web f 0.9435 → **P ≈1.0 (HIGH — replay 1.0000, bands = ref)**.
- armenian-008 ios f 0.9420 / android f 0.9423 → UNMEASURED NOW via adjudication A (−1 each native denominator, 0 on pass; unmeasured-now 21).
- cssom pad / prefix-suffix `-invalid` web (f 0.9821 / 0.9844) → P (**MED-HIGH**: replay 1.0000; needs L5 F-E). Natives pad / prefix-suffix /
  negative `-invalid` ios+android (6 cells, f ~0.982–0.985) → P (**MED-LOW**: needs F-E + seam-3/4; rows at 16 px vs ref 20 px pitch).
- counter-suffix ios f 0.9285 → P (**MED**, needs seam-2's device A/B; row 1 replay matches the ref within 2 px; RTL rows unmodelled — (c⁴)).
- counter-suffix android stays f (T1 alone ≈0.91; RTL upstream). T2: no flip; the gate must show multicol-003 orange at rows 161–163 and
  balancing-003 at 171–175 on Android.
- Picture-correctness rises (passing): armenian-006/-007/-009 + marker-text-matches-georgian web → ≈1.0 (replay).
- Picture-correctness movers the first pass did not name (skeptic S2, measured by the skeptic's replay — plan bands = ref bands, ssim.js 1.0):
  cssom additive-symbols / fallback / name / range / symbols `-invalid` web (today P 0.9884 / 0.9879 / 0.9755 DEG / 0.9882 / 0.988) take
  T3's inline form once T7 stamps them (needs L5 F-E) → ≈1.0 (MED-HIGH). Already watched by the plan's `css-counter-styles/cssom/` line.

At risk: T5 natives (watchlist-additions.txt: counter-list-item-2/-3 P 0.981, add-inline-child-after-marker-001/-002, first-line-and-marker
f 0.943/0.945, + the plan's thin list); T3 counter-suffix web P 0.9818 (string form, hebrew rows — replay unchanged 0.9982); T2-extended
Android cells; name-case-sensitivity ×3 (seam-3/4 mover). `watchlist-additions.txt`: `unmatched 0` (watchlist-check.mjs).

## 8. Hand-offs

Delivered: **L12** `hunk-for-L12-1.patch` (applies on HEAD and on L12's in-progress file; verified in an isolated HEAD copy: 120/120 with it,
the module half alone turns the membership pin red) — ships in L6's PR after L12 merges (C3). **L5 / orchestrator**: seam-3/4 must land with
seam-6-FE-li (§6). **Orchestrator**: `requires-script-mutation` notApplicable tags for the 7 valid gate cssom setter tests (census.json
`T7_author_counter_style.scriptMutationWall`) live in
`tools/titan/wpt-not-applicable.mjs` (no owner this wave) — the bake already names the wall in `lossyReasons` (informational; the na-gate
reads tags, not lossyReasons). BACKLOG text (orchestrator pastes): item 2 (c³) "CLOSED wave 52 (L6 T1) — Compose `KoreanHangulFormal.kt`";
item 3(b) "paint-clamped (L6 T2, BorderSideApplier); the broad `height(h)` overflow fix stays a device A/B"; new "outside markers hang
(L6 T5, seam-1/2 device A/B)"; "author @counter-style resolved by the bake (L6 T7)".
Received: L5 note — F-E prerequisite (acted on: T7 native half); the spec-cite report (fixed).

## 9. Not verified

Device pictures (all of T2/T5/T7-native; Compose seam wiring has no JVM raster); the scorer's own numbers (replay SSIM is ssim.js default);
the empty-item Android pitch; RTL under the hang (Compose mirror pinned in pure geometry only); the counter-styles §-numbers in the new T7
modules follow the bake's HEAD descriptor-order convention and were not re-checked against the ED titles (the validator checks existence only);
`census.mjs` is 260 lines — a results-dir analysis script over the 200-line house limit, not split.

## 10. Blocked by

Nothing at the end. Transient: 17:05 Compose TEST compile failed in `runtimes/compose/src/test/…/core/renderer/VerticalRunIntrinsicsTest.kt`
(another lane's in-flight `onDecline` signature) — cleared by 17:16. Standing: `LineClampUnderPreWave39Test` (1) +
`PlaceholderOverflowMarkerTest` (2) red on the shared tree independent of L6 (line-clamp lane's in-flight work).

## 11. Fix pass (skeptic.md, 2026-10-05 19:00–19:10)

**M1 (must-fix) — FIXED.** The T2 pin only called `innerEdgeStrokeCentre`; the skeptic's mutation 5 (`sideGeometry` BOTTOM back to
`size.height − w/2`, helper untouched) stayed green. `BorderSideApplier.kt`: `sideGeometry(side, width, box: Size)` and
`doubleGeom(side, inset, box: Size)` are now `internal` pure functions (no DrawScope receiver); `paintSide` / `drawDouble` /
`drawGrooveOrRidge` call them with `DrawScope.size`. Behaviour byte-identical (same arithmetic, `size` → `box`). `BorderSideZeroTallBandTest`
rewritten (8 pins, 175 lines): each pin rasterises the LINE the painters stroke to whole pixel rows/columns (Butt cap fills
[c − w/2, c + w/2)) — 100×0 box, `border-bottom: 3px` → rows {0,1,2}, none < 0 (the plan's paint test); 5 px → 0..4; 10 px → 0..9;
0-wide END → columns {0,1,2}; 100×50 box all four sides = the pre-fix arithmetic (47..49 / 97..99); one-stroke boundary; double 3 px
BOTTOM and END → {0,2}; groove 4 px → {0,1} + {2,3}. Mutations EXECUTED (`mutate.py` entries `side-bottom`, `side-end`,
`double-bottom`, `band-clamp`; restores sha256 `9f806563…` byte-exact): 3 / 1 / 2 / 6 red respectively. The first `band-clamp` attempt
did not refresh the JUnit XML (rc 1, stale timestamp) and is marked INVALID in `mutations.log`; the re-run (`mutation-band-clamp-fixpass.log`)
is the record. Focused run after the fix: `compose-tests-fixpass.summary.txt` — 31 classes, 283 tests, 0 failed. No seam patch touches
`BorderSideApplier.kt`, so seam-1..4 and their lock verifications are unaffected (not re-cut).

**N1 — FIXED (comments only).** "127 items in 26 tests" → "100 `<li>` items in 19 tests" (census T5) in `ListMarkerOutsideHang.kt`,
`ListMarkerRow.kt` (`hangsOutside`) and `ListMarkerRow.swift` (`hangsOutside`); Compose re-run 283/0, Swift file `swiftc -parse` ok.
**S2 — FIXED** in §7 (note only; the cells are already watched).

NOT addressed this pass (should-fix / nits, not in the fix-pass mandate): **S1** — seam-1 / seam-3 still ship no source-level
SeamReachabilityTest-style pin; adding one means re-cutting both Compose seam patches and re-verifying under the lock — left for the
orchestrator's call. N2 (`<string>` / CSS-wide keyword guard in `bakedMarkerListStyleType`, 0 carriers today), N3 (files past ~300
lines: `BorderSideApplier.kt` now 565), N4 (Swift `ListMarkerOutsideHangLayout` silent `.zero` on a broken contract), N5 (own-list
amendment — PR record), N6 (uncommented runs in counter-style-author/descriptors).

## 12. Fix pass 2 — skeptic re-verify R1 (2026-10-05 19:50–20:00, no seam, no lock)

**R1 (must-fix) — FIXED.** `doubleGeom` sent the double's inner line (inset 5w/6) and the groove/ridge inner half (3w/4) through the
single-stroke clamp `max(inset, extent − inset)`, which MIRRORS any line with extent < 2·inset (dev `git show 5d9ed628:…` paints
`size.height − inset` / `size.width − inset`, confirmed). Prescribed fix applied in `BorderSideApplier.kt` (sha256 `7bcc1fe7…`, 636 lines):
new `farEdgeBandCentre(extent, band, inset) = max(extent, band) − inset`; `doubleGeom(side, inset, sideWidth, box)` uses it for BOTTOM/END
(no default on `sideWidth`, so a missed call site cannot compile); the painters' plans are now pure `doubleLines(side, width, box)` /
`grooveRidgeLines(side, width, box)` that pass the side's FULL width as the band — `drawDouble` / `drawGrooveOrRidge` stroke exactly those
lines (stroke widths unchanged: width/3, width/2). `sideGeometry` and `innerEdgeStrokeCentre` are byte-unchanged, so every T2 single-stroke
pin and both measured cells (multicol-003 / balancing-003 orange rows) are untouched. Doc corrected: the `innerEdgeStrokeCentre` "unchanged
whenever the box is at least one stroke tall" sentence is now scoped to inset = w/2 and names the mirror; `doubleGeom`'s doc no longer
claims the T2 clamp.

Pins: `BorderSideZeroTallBandTest` groove pin FLIPPED to outer {2,3} / inner {0,1} (it had pinned the mirror), the 0-tall double pin is
now per line (outer {2} / inner {0} on BOTTOM and END — the set {0,2} alone could not see the mirror). New `BorderSideTwoLineBandTest`
(174 lines, 10 tests): the skeptic's probe rows as pins with dev's values — 100×3 3px double [{2},{0}], 100×6 6px [{4,5},{0,1}], 100×9
6px [{7,8},{3,4}], END 3×40 3px cols [{2},{0}], 100×4 4px groove ({2,3},{0,1}), plus p7 100×2 3px double → translated band [{2},{0}];
a float-exact sweep (w ∈ 2…14 incl. 2.5/3.75, extent w…w+24 in ¼ px, BOTTOM+END, double+groove) that the fixed lines equal dev's
`extent − inset` for every box at least one band tall; `farEdgeBandCentre(e, w, w/2) == innerEdgeStrokeCentre(e, w/2)` for e 0…40 (the
single stroke cannot move); a direct helper pin; TOP/START ignore the band. Independent oracle `r1-oracle.py` → `r1-oracle.txt` (dev /
pre-R1 / fixed rows per probe) agrees with every expectation.

Mutations EXECUTED (`mutate.py` new entries, driver `r1-run-mutations.py`, per-run gradle logs `mutation-r1-<name>.log`, only JUnit XML
newer than the run start read, every restore sha256 `7bcc1fe7…` byte-exact; `--tests '*BorderSide*'` = 18 tests): **double-mirror**
(doubleGeom BOTTOM+END back to `innerEdgeStrokeCentre(extent, inset)` = max(inset, extent − inset)) **9 red**; double-mirror-bottom 8 red;
double-mirror-end 3 red; band-centre-mirror (helper body → the mirror) 10 red; double-lines-width (band = line) 2 red; groove-lines-width
(band = half) 1 red; double-dev-bottom (dev `box.height − inset`) 3 red — ONLY the 0-tall / sub-band pins, the ≥ one-band probe pins and
the sweep stay green, i.e. the fix equals dev wherever dev was right. The pre-R1 `double-bottom` entry now refuses (0 matches) — it
targets sha `9f806563…`; `double-dev-bottom` is its successor.

Durable record: `compose-tests-r1.summary.txt` (tracked; every R1 apply / result / restore line copied from the gitignored `mutations.log`).
Runs: `compose-tests-r1.log` — `*BorderSide*` 2 classes, 18 / 0 (after the mutations); `compose-tests-r1-broad.log` — `lists.*
borders.* *MulticolFloatStrip*` 32 classes, **293 / 0** (= §11's 283 + the 10 new; `MulticolFloatStripRefRowsTest` green). Gate
exposure unchanged from the skeptic's census: 0 of 53 wave51-fix two-line far-edge carriers sit in the changed range. No seam patch
touches `BorderSideApplier.kt`; seam-1..4 are not re-cut. Not verified: device pictures of a short / 0-tall double or groove/ridge box
(JVM has no draw surface; the painter → plan step is one call per painter); N3 deepens (636 lines, was 565) — a split is a follow-up,
not in this mandate.

STATUS: COMPLETE
