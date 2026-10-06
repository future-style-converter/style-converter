# wave52-docs — BACKLOG refill (gate-independent half), 2026-10-05

Editor: the wave-52 documentation lane. Only `docs/BACKLOG.md` was edited (+768 / −127 lines, 2953 → 3594), all
hunks inside "## Ranked queue (wave 51+)" and below (first hunk at line 781). Not touched: the header / "Current:"
block, "## Standing constraints", "## Next-wave obligations", "## Decided, and still unstarted", "## Parked". No
build, test, device, simulator or git-state command was run; facts were checked by reading files, `grep`, `wc -l`,
`git ls-files` / `git log` / `git show --stat`, and `python3` over committed JSON. Every "shipped / landed / fixed"
sentence below was grepped by symbol or file in the integrated tree (HEAD 308d11f0 + this edit) before it was written.

## 1. What changed, where, and from what

| BACKLOG heading / item | change | source |
|---|---|---|
| Ranked queue 0(a′) | replaced by CLOSED (L5 F1) — `collapseDeclaration`, `propsForElement` gate; 2 cross-rule carriers named | `wave52-extractor-cascade/_note.md` § Hand-offs; `extract-fixture.mjs` `collapseDeclaration` grep |
| 0(a″) | replaced by CLOSED (L5 F2) — `splitDeclarations` | same; `export function splitDeclarations` grep |
| 0(a‴) | replaced by CLOSED (L5 F3) — filter inside `resolveOne` | same |
| 0(a⁗) NEW | L5's F-D / F-C / F-E record, F-E ↔ L6 seam-3/4 coupling, predictions, open D2 / D4 / N1 / R2 | `wave52-extractor-cascade/_note.md` § FIX PASS, § Open; `skeptic.md` § Residual |
| 0(b′) | rewritten with L4's handed text: NOT zero-movement (8 consumers / 6 tests, block-in-inline-000…004) | `wave52-small-fixes/_note.md` § b′, § Hand-offs |
| 0(b″) | struck → PORTED (L4), device A/B owed | `wave52-small-fixes/_note.md` § b″; `SPACING_UNPUBLISHED_BREADCRUMB` in Margin/PaddingApplier.kt; `PercentSpacingContainingBlockLevelTest.kt` exists |
| 0(c′) NEW | L4 T5 (BackfaceCull) + T7 (TransformInheritance), predictions, open K4 / iOS own-face / own text | `wave52-small-fixes/_note.md`; `skeptic.md` K4; both .kt files exist; `LocalInheritedTransform provides` in ComponentRenderer.kt |
| 0(e) | replaced by CLOSED (L5 F4) + D3 non-finite-angle residual | `wave52-extractor-cascade/_note.md` § Open D3; `AngleParser.kt` regex has `[eE]`, no finite check |
| 0(j′) NEW | L7's "Static position (wave 52 L7)" paragraph + skeptic should-fix 1/2, nits 6/8, T4 | `wave52-static-position/_note.md` § 8; `skeptic.md` § 3 |
| 0(k) | appended L3's handed paragraph (F1/F2/F3 shipped, F4/F5/F6 queued) + predictions + RV-N1 / RV-N3 | `wave52-failure-ink/_note.md` § 9; `skeptic.md` RV-6 |
| 0(l) | appended status: one harness-side cause (L2 Fix A); clip/mask pair was the ref (L12 UAMARGIN, calibration numbers) | PLAN §2 L2/L12; `wave52-instrument-and-calibration/_note.md` § Calibration gate |
| 0(l′) NEW | L2's "Composed canvases (wave 52 L2)" paragraph + lift-rule scope (35 roots / 24 docs) + predictions + all open should-fix/nits | `wave52-composed-canvas/_note.md` § 8 (handed text), § 6; `skeptic.md` § 5, § R6 |
| 0(m) | replaced by L11's handed 0(m) text + seed-the-baseline instruction + predictions + S1 / S2 / N1 / N2 / RV-S1 / RV-N1 | `wave52-all-reset-postload-colour/_note.md` § 4, § 7, § 8; `skeptic.md` |
| 0(n) | appended DECIDED AND EXECUTED (L12-A, Decision A), 19 cells measured in `wave52-calib` | `wave52-instrument-and-calibration/_note.md` § Census, § Calibration gate; PLAN §2 L12 |
| 0(ac) NEW (orchestrator C2) | `display-contents-root-background` ×3 honest failure from the re-freeze | `score-gate.wave52-open.wave52-calib.txt` LOST; L12 note; WPT source read |
| 0(ad) NEW (orchestrator C2) | `column-span-during-transition-doesnt-skip` ×3 honest failure; L1's measured non-uniform ring | L12 note; `wave52-web-tail-colour-vt/_note.md` § Real drive |
| 1(d) NEW (L8 — no paragraph handed over) | "Vertical wedges (wave 52 L8)": M-A/B/C/E/G landed, predictions, M-A A/B owed, open M-G second clause / text-orientation / 007-008 / colspan / loader race | `wave52-vertical-wedges/_note.md`; `skeptic.md` § Regression sweep |
| 2(c¹) | appended adjudication A (armenian-008 back on `NATIVE_FONT_PARITY_REFUSED_TESTS`) + web T3 prediction | `wave52-counters-and-lists/_note.md` § 7; `inject-wpt-block.mjs` refusal list grep |
| 2(c³) | appended: korean half CLOSED (L6 T1); NEW defect noted — Swift `KoreanHangulFormal.swift` TWIN DIVERGENCE banner is now stale | L6 note § 8; `KOREAN_HANGUL_FORMAL` in 3 Compose files; Swift banner read |
| 2(d) NEW | "Outside markers hang (L6 T5)" — device A/B arm and cells | L6 note § 6, § 7; ORCHESTRATOR-TODO §6 |
| 2(e) NEW | "Author @counter-style resolved by the bake (L6 T7)" + native empty-item half + open S1 / N2 / N4 / N6 | L6 note § 1, § 11 |
| 3(b) | appended: narrow option LANDED (L6 T2) incl. the R1 mirror fix; tripwire | L6 note § 11–12; `farEdgeBandCentre`, `innerEdgeStrokeCentre` grep |
| 4(c) | appended: Swift twin SHIPPED (L9 F2), prediction, F2 A/B arms | `wave52-inline-run-wall/_note.md` |
| 4(f) NEW | L9's handed "(c) refuted half + (a)" text as one entry + F3/F4/F5 + predictions + R1 / R2 / R3 / S2 / seam-1 / N2 | L9 note § Hand-offs, § Not verified; `skeptic.md` § New findings |
| 5(g) | appended L5's propsForBodyRoot residual | L5 note § Hand-offs |
| 5(j) NEW | L1's F-A / F-B record + predictions + open (prophoto, shadow-aware window, 5 null rings) + nits (mint tag, rgb% rounding) | `wave52-web-tail-colour-vt/_note.md` § Hand-offs; `skeptic.md` item 3 |
| 6(c) | appended: L10 re-authored `box-shadow-reach.json` (skeptic nit 10 folded in) | `wave52-flex-nowrap-gaps/_note.md` § 1; fixture exists |
| 7(a′) | appended CLOSED in tree (L10) | L10 note § 1; `GapDecorationBands.kt` "fractional `leftover / n`" comment |
| 7(b) | appended LANDED (L10) | L10 note § 1; `CSSFlexMath.percentBasis` grep |
| 7(e¹) | appended RE-FROZEN with calibration numbers | L12 note § Calibration gate |
| 7(e³) | appended EXPLAINED (L2 M1 + L10 M2/M3) | PLAN §2 L2; L10 note |
| 7(g) NEW | L10's handed "css-gaps residuals (wave 52 L10)" paragraph + predictions + open V6/V7, #2, #3, #5, #6, #7, iOS RTL | L10 note § 8; `skeptic.md` § Remaining defects |
| 9(m) | appended the wave-52 over-size files (orchestrator C4; all `wc -l` on the integrated tree) | §8 lists of L2/L3/L6/L7/L8/L9/L10 |
| 10 | appended: calibration landed; hanging-punctuation-block-bound-001 actionable | L12 note |
| 11(b) | appended pointer (L2 T3 targets align-items-007) | L2 note |
| 11(h) NEW (third fix pass) | PLAN §6 unstaffed findings: native-near-misses T8 / T9 / T10 / T11 / T2-children, counters-and-multicol B / C | `wave52-plan/native-near-misses.md` §3.8, §5; `counters-and-multicol.md` §2–§4; tree greps (see Fix pass 3) |
| Instrument decisions pending — blank-capture guard | replaced by PLAN §2 L12 Decision-B blockquote, amended per L12 note:161-163 (39 rows / 16 tests …; `refUniform`), + D1 / D7 | PLAN §2 L12; L12 note § Hand-offs; `absence-only-census.wave51-fix.json` |
| Instrument — erased-reference body background | struck → EXECUTED with the §4 numbers, the 8 unlisted refs named, the clone-unmoved-refs rule, + display-contents-sharing-001 polluted ref, D2 / D5 / D6 / D8 | L12 note § Re-freeze, § Calibration gate, § Findings; `refreeze-apply.post.json` |
| Instrument NEW (orchestrator C1) | L6's na-tags DEFERRED — 21 scored cells / 13 passing; decide-then-execute-or-park | PLAN §10; ORCHESTRATOR-TODO §1d; `wpt-not-applicable.mjs` has no cssom tag (grep) |
| Instrument NEW | coverage ratio on dark ink (PLAN §6 says it goes here, not staffed) | `wave52-plan/native-near-misses.md` §3.9 |
| Known-broken — swift-frontend Release crash | appended L2's re-observation; the crash site is cited by the symbol `styledContent(now:)` (no line — second fix pass) | L2 note § 6; grep |
| Known-broken NEW (orchestrator C5) | ios-harness XCTest cannot run on this host | orchestrator text; see §4 below for what was verified |
| Known-broken NEW (orchestrator C3) | `smoke.sh` hardcodes port 3000 | `smoke.sh` `start_vite` (lines 55–86 read); `vite.config.ts` `server.port: 3000`; `interaction-states.mjs` / `a11y-audit.mjs` `WEB_PORT \|\| '3000'`; no `web-port-guard` / `WEB_PORT` in smoke.sh |
| Operational recipes NEW (C6 d) | seam integration: per-file locks + `integrate-seams.sh` (35 patches, `--dry-run` proves APPLY) | `integrate-seams.sh` header + `grep -c .patch` = 35; L3 note §8 lock incident |
| Operational recipes NEW (C6 a) | file-driven, resumable, opus fan-outs (supersedes "only to dead lanes") | orchestrator text; lane resume records |
| Operational recipes NEW (C6 b) | scratchpad purge ~3 days; shared scratchpad → private subdirs | L5 note § Resume record; L3 note §8 |
| Operational recipes NEW (C6 c) | port guard (sourced by test-all, section-runner and — working tree, second fix pass — probe-text-metrics) | `web-port-guard.sh` header; `test-all.sh` WEB_PORT_EXPLICIT / 3300–3399 lines; commit 308d11f0 |
| Operational recipes — suite counts | ios-harness sentences rewritten (second fix pass): row in the tables, compile-only; next = `check_suite_count` instead of the stale warn | README:217 / CLAUDE:297 / STATUS:2459 rows |

## 2. Every `[[GATE]]` token, in file order (line numbers on the edited file, refreshed after the third fix pass — 22 = `grep -c`)

| # | line | item | cells | resolved by |
|---|---|---|---|---|
| 1 | 807 | 0(a⁗) L5 | `css-cascade/import-conditional-001/-002` web/ios/android (f→P ×6); `css-color/color-mix-percents-02` web (f→P), ios/android (f→P, MED); `css-gaps/flex/flex-gap-decorations-024` ×3 (stays P, ≈1.0); `css-cascade/revert-val-002` web (stays P); the 18 native cssom P cells (`cssom-{additive-symbols,fallback,name,range}-setter(-invalid)`, `symbols-setter-invalid` ios/android) stay P; `css-cascade/important-prop` ×3 at risk | `score-gate wave52-calib wave52-final` GAINED / LOST lists + PNG check |
| 2 | 873 | 0(b″) L4 b″ | `css-sizing/abspos-auto-sizing-fit-content-percentage-001…004` android PNGs `cmp`-identical to `wave52-open`; `css-position/position-relative-002/-008` android not movers; installed `base.apk` sha1 recorded | the post-gate L4 b″ device A/B record |
| 3 | 898 | 0(c′) L4 | `css-transforms/composited-under-rotateY-180deg-preserve-3d` ios/android f→P; `css-transforms/css-transform-inherit-scale` android f→P; `backface-visibility-hidden-animated-001/002` ios stay P; `css-transform-3d-transform-style` android + two `backdrop-filter-*3d*` unchanged | GAINED list + movers |
| 4 | 1146 | 0(j′) L7 | `css-flexbox/abspos/position-absolute-containing-block-002` ios f→P; 16 android `css-grid/abspos/grid-abspos-staticpos-align-{self,items}-*` and 10 web `*-last-baseline-*` → 1.0000; vertWM-003/004 android ~0.967 and vertWM-last-baseline-003/004 ios 0.9950 stay P; LOST 0 | GAINED / movers / LOST |
| 5 | 1184 | 0(k) L3 | `css-display/display-contents-float-001` ios/android f→P; `css-multicol/abspos-containing-block-outside-spanner` ios f→P **and its PNG shows zero red**; `css-writing-modes/flexbox_align-items-stretch-writing-modes` ios f→P; movers `abs-pos-border-offset-003`, `css-tables/baseline-vertical` | GAINED + PNG check |
| 6 | 1233 | 0(l′) L2 | the 14 passing tests outside the T3 rows (watchlist lines for `gradient-single-stop-00`, `aspect-ratio/abspos-0`, `background-clip-content-box-001`, `clip-path-polygon-003`, `backdrop-filter-edge-pixels-2`) do not move | movers list (`--movers 0.005`) |
| 7 | 1235 | 0(l′) L2 | `clip-path-ellipse-006/007/008` ios/android, `text-decoration-propagation-shadow` ios/android, `css-flexbox/align-items-007` ios/android, `flex-gap-decorations-027` web, `s-11-1-1b-006` ios/android (f→P); `flex-gap-decorations-040` web → 1.0000, `block-ellipsis-028` web → 1.0000, `position-absolute-semi-replaced-stretch-input` web f→P; LOST 0; frame-ink census overrun-right + overrun-left 292 → 0/0 | GAINED / LOST + the frame-ink census on `wave52-final` |
| 8 | 1244 | 0(l′) L2 | `contain-inline-size-bfc-floats-001` ios/android, `anchor-position-multicol-007` android flip on the scorer and are labelled DEGENERATE (034 ×3 taken out in the fix pass — already flipped in `wave52-calib`) | GAINED list + PNG look at each |
| 9 | 1252 | 0(l′) L2 | `filter-effects/backdrop-filter-basic-blur` ios 0.9469 → ~0.9526 (ring-fenced; report only) | the cell's score at `wave52-final` |
| 10 | 1291 | 0(m) L11 | `selectors/has-visited` ios f→P; colour cells (all-prop-*-color, nth-child-of-*, first-letter-block-to-inline) stay P; at risk `css-cascade/all-prop-001` ×3, `all-prop-initial-visited` ×3, `css-display/display-contents-{button,details,fieldset}` web | GAINED / LOST + all-prop-001 PNG |
| 11 | 1568 | 1(d) L8 | `css-writing-modes/forms/input-range-zero-inline-size` ios/android f→P; `ch-units-vrl-003/-004` ios/android f→P; `ch-units-vrl-005/-006` ios/android f→P (low confidence); M-A at-risk P cells (hyphens-auto-001, hyphens-none-shy-on-2nd-line-001, hyphens-out-of-flow-002, hyphens-punctuation-001, hyphens-span-002, text-decoration-inset-004; bidi-lines-001/002 ios) stay P; `hyphenate-character-005` ios mover | GAINED / LOST, then the M-A device A/B |
| 12 | 1683 | 2(c¹) L6 adjudication A (third fix pass) | `css-counter-styles/armenian/css3-counter-styles-008` ios/android leave the denominator (−1 each, 0 on pass): unmeasured-now 19 → 21 | the `UNMEASURED NOW` list of `score-gate wave51-fix wave52-final` |
| 13 | 1689 | 2(c¹) L6 | `css-counter-styles/armenian/css3-counter-styles-008` web f→P | GAINED |
| 14 | 1801 | 2(d) L6 T5 | `css-counter-styles/counter-suffix` ios f→P; `counter-list-item-2/-3`, `add-inline-child-after-marker-001/002`, `first-line-and-marker` natives at risk | GAINED / LOST, then the T5 device A/B |
| 15 | 1822 | 2(e) L6 T7 | cssom `pad` / `prefix-suffix` `-invalid` web f→P; natives `pad` / `prefix-suffix` / `negative` `-invalid` ×6 f→P (MED-LOW); armenian-006/-007/-009, `marker-text-matches-georgian` web rise to ≈1.0 | GAINED + movers |
| 16 | 1911 | 3(b) L6 T2 | tripwire: Android `floats-clear-multicol-003` orange at rows 161–163, `-balancing-003` at 171–175; no flip | PNG rows of the two Android captures |
| 17 | 2022 | 4(c) L9 F2 | `css-overflow/line-clamp/block-ellipsis-032` android f→P (thin), ios P → ≈0.98 | GAINED + movers, then the F2 device A/B |
| 18 | 2081 | 4(f) L9 F1 (fix pass) | `hanging-punctuation-inline-001` replay-predicted SCORE LOSS android 0.9495 → 0.9454, ios 0.9756 → 0.9685; `drop-F1.patch` applied for good only if the F1 device A/B (`device-ab.sh f1`) confirms | LOST / movers, then the F1 device A/B |
| 19 | 2090 | 4(f) L9 | `block-ellipsis-025` ios f→P; `hyphens-manual-inline-012` android f→P; 19 Android soft-wrapped clamp hosts stay P with "…"; `block-ellipsis-023/-024` android P→P movers | GAINED / movers, then the F1 device A/B (drop-F1) |
| 20 | 2253 | 5(j) L1 | `css-color/display-p3-linear-001/-002/-003` ×3 f→P; `css-view-transitions/fractional-box-with-{shadow,overflow-children}-{new,old}` web ×4 and natives ×8 f→P; `display-p3-linear-004/-005/-006` ×3 rise; tripwire `(frame-ring rgb(255, 182, 193) stamped)` on exactly those four in `css-view-transitions/extract.log` | GAINED + the section's extract.log |
| 21 | 2593 | 7(e³) (fix pass) | the 027 mechanism (L2 M1 + L10 M2/M3) is confirmed only if `flex-gap-decorations-027` flips as predicted in 0(l′) (web) / 7(g) (ios/android) | GAINED |
| 22 | 2612 | 7(g) L10 | `css-backgrounds/background-clip-content-box-002` ios f→P (zero red); `flex-gap-decorations-027` ios/android f→P; `flex-gap-decorations-008` android P → ≈0.9996; 045 ios / 046 android rise; 024/029/030/034–037/050 move ≤ 0.0014; LOST 0 | GAINED / movers |

Statements deliberately written WITHOUT a token (measured before the gate): every `wave52-calib` number (0(l), 0(n), 0(ac),
0(ad), 7(e¹), 10, the two Instrument bullets, and — since the fix pass — the 0(l′) `flex-gap-decorations-034` ×3 calibration
flip), JVM / Catalyst pins, PNG replays, censuses, and the file sizes.

## 3. §8 items NOT queued, with the proof

| item | why not queued | proof |
|---|---|---|
| L6 R1 (must-fix, `doubleGeom` mirrored the inner line) | FIXED | `grep -n 'fun farEdgeBandCentre\|fun doubleGeom' …/borders/sides/BorderSideApplier.kt` → :306, :527; `BorderSideTwoLineBandTest.kt` exists (175 lines) and references `doubleGeom`; recorded as fixed inside 3(b) |
| L11 RV-M1 (must-fix, gate-list hunk lands a red pair) | FIXED | `tools/visual/gate-fixtures.txt` lists `fixtures/combinations/all-then-color.json` (9 fixtures); the fixture is glyph-free per the L11 note §8; commit e92683b5 |
| L12 D4 (commit the digest) | FIXED | `git ls-files tools/titan/results/wave52-instrument-and-calibration` lists `absence-only-digest.wave51-fix.json` |
| L12 D3 (PLAN §1/§7 stale numbers) | FIXED (orchestrator) | PLAN §10 reads lost ≥ 8, `blank-captures=45`, unmeasured-now 21 |
| L2 should-fix 5 (iOS-harness test bundle never compiled) | FIXED as compile; RUN is blocked — queued as Known-broken instead | commit 0dda427e "ios-harness XCTest bundle compiles with 30 tests"; 30 `func test` under `apps/ios-harness/StyleConverterTestTests/` |
| L2 S1 (note "2 docs only" vs 35 roots / 24 docs; watch lines) | watch lines FIXED; the true scope is now stated in 0(l′) (the lane note itself is not edited) | `wave52-plan/watchlist.txt` lines 338–342 carry the five family lines |
| L3 RV-N2 (hunk header omits `BakedLayoutSignature.swift`) | moot — the file is tracked and the hunk is applied | `git ls-files …/Renderer/BakedLayoutSignature.swift`; `VerticalMulticolBakeGateTests.swift` in the tree |
| L5 R1 (PLAN §2/§4 still say "F-E 0 alone" / L5 whole at step 3) | superseded — a PLAN-text nit, not queue work | `integrate-seams.sh` ORDER puts L5 seam-6 after L6 seam-3/4; PLAN §10 records the integration |
| L6 N5 (own-list amendment) and L10 nit 9 (unowned `GapDecorationBandsTest.kt` edit), L7 nit 4 (two new test files outside own-lists) | PR-record items, not queue work; the files are committed | `git ls-files` of the named files |
| L7 nit 3 (T1 movers 108 vs 112) and nit 7 (note §6 omits align-self-001/002 web rises) | lane-note counting / wording nits with no tree action; the cells are on the watchlist | L7 skeptic § 3 |
| L1 census nit (srgb row 40/12 vs 43/14, unlisted lch row) | context-row labelling in the lane note, outside F-A's radius; no tree action | L1 skeptic § Observations |
| L11 N3 (seam-3 registry row) | done by integration | `integrate-seams.sh` ORDER includes `wave52-all-reset-postload-colour/seam-3.patch` |
| L11 N4 / N5 (log evidence; all-prop-001 risk maybe overstated) | evidence-quality nits; all-prop-001 stays an at-risk cell in 0(m) | L11 skeptic |

Open items that ARE queued were each re-checked in the tree first: L5 D2 (`readIdent` still does `i += m[0].length`), L5 D3
(no finite check in `AngleParser.kt`), L12 D1 (`refUniform` appears once in `inject-wpt-block.test.mjs`, asserting null on a
structured ref), L12 D2 (`capture-browser-ref.mjs` never names `view-transition-bake`), L6 S1 (no Compose test references
`ComponentRenderer` beside `rendersInsideOverlay` / `ListMarkerOutsideHang`), L7 fixture gap (`flex-align-self.json` has only
`baseline`, `grid-align-self.json` none), and the C1 deferral (`wpt-not-applicable.mjs` has no cssom tag).

## 4. Could not place or verify

- C5 (ios-harness): the Xcode version (26.6, build 17F113 — `/Applications/Xcode.app/Contents/version.plist`) and the
  installed runtimes (CoreSimulator volumes `iOS_23A8464`, `iOS_23C54`) were read from disk; the exact error text "iOS 26.5
  is not installed" and exit 70 come from the orchestrator's sweep and have no committed log (running xcodebuild was out of
  bounds here). The 30-test count and the compile-by-target are backed by the sweep commit and a `func test` count.
- C3 (smoke.sh): the 2026-10-05 :3000 incident is recorded in `web-port-guard.sh`'s header and commit 308d11f0; the
  "Tier 5/11 fail with that server's 404s" detail is the orchestrator's. Not re-run (smoke.sh was out of bounds).
- C6 (a): "three fan-outs died on the session usage limit" is the orchestrator's statement; the lane notes' resume
  records corroborate killed starts but no file counts fan-outs.
- 0(l′): the gaps-034 ×3 label is genuinely disputed between L2 (degenerate) and L12 (honest instrument-only gain, already
  realised in `wave52-calib`) — recorded as disputed, WITHOUT a token since the fix pass (the flip is already measured);
  decide the label from the final PNG.
- The Decision-B amendment changed one more number than the hand-over named: the plan text's "permanently red on 15
  known … defects" is now "33 known cells in 14 tests", computed from the committed
  `absence-only-census.wave51-fix.json` (`blankCaptureVsInkedRef.rows`, scored rows → 14 distinct tests); the `:905`
  line pointer to `presenceFailed` was replaced by the symbol `computePresenceFailed` (it sits near :1974 now).
- 0(ac): the mechanism sentence ("`:root { display: contents; background-image: url(1x1-green.png) }` … propagated to the
  canvas") is read from the WPT source `tools/wpt/css/css-display/display-contents-root-background.html`; no runtime was
  traced for why all three paint white.
- Not placed: ORCHESTRATOR-TODO §6's device A/Bs are recorded at their items (0(b″), 1(d), 2(d), 4(c), 4(f)); the
  gate-dependent "Next-wave obligations" refresh and the v6.18 snapshot are the orchestrator's after the gate.
- Spec citations added were checked against `tools/visual/spec-sections.json` with a python read (all 11 resolve);
  `spec-cite-validate.mjs` itself was not run. `grep -c 'scratchpad/' docs/BACKLOG.md` → 0.

## Fix pass (skeptic review of this edit, 2026-10-05)

Each check was re-run read-only first (grep / sed / `git ls-files` / `git status`, and a python hunk-context check that
searches each patch hunk's pre-image — blank context lines included — in the live file; no `git apply`, no build). After the
pass: `docs/BACKLOG.md` 3636 lines, `git diff --stat` +812 / −129, first hunk still at line 781, nothing touched above the
Ranked queue; `grep -c '\[\[GATE\]\]'` = **21** (was 19; §2 table refreshed); `grep -c 'scratchpad/'` = 0.

| # | sev | problem | re-check | action |
|---|---|---|---|---|
| 1 | must-fix | 4(f) F1 score-loss prediction + F1 A/B, and 2(d) "staged as a DEVICE A/B" heading, sat before their items' only token | CONFIRMED (tokens were at 1786 / 2069; both texts above them) | 4(f): sentence now opens "[[GATE]] `hanging-punctuation-inline-001` …", second token kept before `block-ellipsis-025` (+1 token); A/B pointer `device-ab.sh f1` added. 2(d): heading shortened to "Outside markers hang (wave 52 lane L6 T5)."; "a DEVICE A/B whose include arm is the integrated tree" moved into the post-token "Owed after the gate" sentence, plus `device-ab.sh t5` |
| 2 | should-fix | 1(d) M-A exclude arm names `ma-exclude-ios.patch`, which no longer applies | CONFIRMED: hunk 2 (`@@ -205`) pre-image absent from `ChUnitMetrics.swift`; `ma-exclude-ios.integrated.patch` 2/2 hunks present; L8 `seam-1.patch` post-image present (reversible); `device-ab.sh:28` uses the integrated patch; only the lane patch is tracked | Arm rewritten per the fix (Compose `git apply -R …/seam-1.patch` + iOS `ma-exclude-ios.integrated.patch`, why the lane patch fails, applied-and-reversed / never committed applied — `ChUnitInlineAxisTests`' Inter pins go red under it, `device-ab.sh ma`). Same read-only check on `wave52-inline-run-wall/drop-F1.patch` (named in 4(f)): 12/12 hunks present — no change needed |
| 3 | should-fix | "Capture scripts never kill a process they did not start" overclaims | CONFIRMED: `gate-driver.sh:125-126,179,282` pkill host-wide, file unmodified; BACKLOG recipes "The preconditions are still yours" and "Gate on a quiet host" carry the same pkills; `own-processes.sh` untracked | Retitled "The vite port guard: capture scripts never kill a foreign listener on their web port"; appended the "Not yet true of the gate itself" sentence naming both recipes. Queued as **Known-broken NEW** "`gate-driver.sh` kills every emulator and every Chrome-for-Testing on the host" (three call sites, the `provision-devices.sh` `emulator-pids` rule, fix direction matching `own-processes.sh`'s pid-record + cwd-in-checkout rules — read from the file, not run), pointing at the working-tree `own-processes.sh` / `.test.mjs` and saying `gate-driver.sh` does not source it yet. The two older recipes were NOT rewritten (behaviour change is the fix's job) |
| 4 | nit | 0(l′) token #8 covered `flex-gap-decorations-034` ×3, already flipped in calibration | CONFIRMED: `score-gate.wave52-open.wave52-calib.txt` GAINED lines 184–186; BACKLOG Instrument bullet credits it as instrument-only | 034 ×3 taken out of the token list and written without a token (already f → P in `wave52-calib`, PNG-checked by L12, L2's label disputed; L12 note:109 puts its residual on the right-frame overrun Fix A targets — open the final PNG). Token #8 stays for the other three cells |
| 5 | nit | "23 refs move" parts sum to 24 | CONFIRMED: `refreeze-ab.json` `uaBodyMarginFired` includes `initial-background-color`; it is one of the three all-green refs (`absence-only-denominator.md:58-60`) | Inserted "(`initial-background-color` is in both the all-green and the Fix-B sets)" |
| 6 | nit | Decision-B text points rotateY at "queue 0(k)" | CONFIRMED: `composited-under-rotateY` only at 0(c′) and that line; 0(k) names cells 012/031/038/046/068/091/098 only; pointer inherited from PLAN.md:542 | Now "(queue 0(c′), wave-52 lane L4 T5)" |
| 7 | nit | 0(n) still says the guard "addresses the 42 (a capture FAILURE)" | CONFIRMED against the Instrument bullet ("exit-7 capture-failure form DECLINED", 39 / 45) | Appended the "(Superseded wave 52: …)" sentence |
| 8 | nit | 0(ac) has no next action | CONFIRMED | Appended "Next (no runtime traced yet): find, per runtime, where a `display: contents` root's `background-image` is dropped on the way to the canvas … census the other root-`background` carriers before staffing a lane" |
| 9 | nit | 7(e³) "EXPLAINED" stated as settled before the gate | CONFIRMED: composed-canvas note:115-116 (027 web 0.9723 a LOWER BOUND, natives M1-alone 0.9378 / 0.9379); flex-nowrap-gaps note §7 (`FlexNowrapLine.Line` never executed) | Now "mechanism identified in wave 52 (replay-backed, not rendered on a device)" with the replay numbers, then "[[GATE]] confirmed only if 027 flips as predicted in 0(l′) / 7(g)" (+1 token) |
| 10 | nit | old "Limit-killed workflow lanes" bullet has no pointer to its superseding recipe | CONFIRMED | Appended "(superseded wave 52: every wide fan-out runs on opus — see "Wide lane fan-outs are FILE-DRIVEN …" above)" |
| 11 | nit | six §8 items neither queued nor fixed | CONFIRMED as described | REFUSED the BACKLOG line: Standing constraints say "the PR body" is not a pointer, and a "fold into the PR body" line would be stale the day the PR merges. Accepted as recorded in §3 above; the orchestrator should fold L1 census row labels, L5 R1 PLAN wording, L7 nits 3/4/7 and L10 nit 9 into the wave-52 PR body |

Fixed 10, refused 1 (#11, accepted as recorded). For the orchestrator, before the wave-52 commit: commit
`tools/titan/results/wave52-vertical-wedges/ma-exclude-ios.integrated.patch` and `tools/titan/results/wave52-gate/` (the
BACKLOG now points at `device-ab.sh` t5 / ma / f1 and the integrated patch), and either commit `tools/titan/own-processes.sh` /
`own-processes.test.mjs` or drop their pointer from the new Known-broken item; this note directory is untracked too.

## Fix pass (second skeptic review, 2026-10-05)

Each check was re-run read-only first (grep / sed / `git show` / `git diff` / `git ls-files`, python over committed JSON and over
BACKLOG; no build, test, device or git-state command). After the pass: `docs/BACKLOG.md` 3665 lines, `git diff --stat` +850 / −138,
first hunk still at line 781, nothing touched above "## Ranked queue"; `grep -c '\[\[GATE\]\]'` = **21** (unchanged — no
gate-dependent sentence was added; §2 line numbers refreshed and each re-checked to carry a token); `grep -c 'scratchpad/'` = 0.

| # | sev | problem | re-check | action |
|---|---|---|---|---|
| 1 | should-fix | port-guard recipe names two sourcing scripts; `probe-text-metrics.sh` still kills at HEAD | CONFIRMED: `git show HEAD:…/probe-text-metrics.sh` has the two `lsof … \| xargs kill -9` lines (100, 109); the working tree sources the guard; the working-tree source pin lists three scripts. A tree-wide grep for `lsof -ti` / `xargs kill` / `fuser -k` finds no other capture script | FIXED: "sourced by `test-all.sh`, `tools/titan/section-runner.sh` and `tools/visual/probe-text-metrics.sh`". Ship dependency below (git state is out of bounds here) |
| 2 | should-fix | 5(j) dropped L1's "rest of the web tail → L5 or wave-53 §9 singletons" routing; none of the singletons is queued | CONFIRMED: L1 note:160-163; `grep -c` 0 for all six names; web-tail.md §9 read | FIXED: appended to 5(j). Two corrections to the skeptic's text, both from the tree: the serializer is cited by symbol (`inPageSerializer` returns `{ headInner, bodyOuter }`, `buildSyntheticHtml` writes a bare `<html>`) instead of web-tail.md's `post-load-extract.mjs:847-864`, which has drifted; caret-shape is written as "§9 counts ×2, `web-tail.failing-cells.json` lists `css-ui/caret-shape-block-color-001`" because the committed cell list and `gate-cells-wave51-fix.json` name only one caret-shape test. `extractInlineStyle`'s all-`<style>` regex read at `extract-fixture.mjs` (symbol cited, no line). Also named §9's unrooted rest (shadow-DOM presence → absence-only lane; hyphenation / text-decoration-inset / table / flex-gap at 0.93–0.95) |
| 3 | should-fix | stale `path:LINE` pointers (ComponentRenderer :869 + :878, `FixedHoist.swift:137-160`, `TableBoxTree.swift:114`) | CONFIRMED: `styledContent(now:)` at :878; FixedHoist "DIVERGES" at :142 (5 lines added above by L3 F3); `CollapsedBorderConflict` at TableBoxTree.swift:117 | FIXED: Known-broken cites `Renderer/ComponentRenderer.swift` `styledContent(now:)` by symbol, ":878 on the integrated tree" dropped; 6(e) cites the "PARITY NOTE — A/B PENDING" block of `FixedHoist.split`'s doc comment (by symbol rather than 142-165: the note runs :141 to :180, so a shifted 24-line range would still not be its bounds); `TableBoxTree.swift:117`. Sweep: a python scan of every `basename:LINE` pointer below the Ranked queue into the 209 files changed 5d9ed628..HEAD + working tree found 4 — `TableBoxTree.kt:128` and `StyleBuilder.ts:14` still hold; `BorderSideApplier.kt:477` (0(…) retro paragraph) is a historical quote of what R12 recorded, already off at 5d9ed628, left as attribution. Pointers wrapped across a line break are not covered by that scan |
| 4 | should-fix | six evidence pointers name untracked files | CONFIRMED: `device-ab.sh`, `installed-build-hash.sh`, `ab-diff.mjs`, `ma-exclude-ios.integrated.patch`, `own-processes.sh` / `.test.mjs` all `??` | HANDED OFF, not refused: committing is out of bounds for this lane. Pointers kept — the BACKLOG text states each arm in full (1(d): Compose `git apply -R` seam-1 + the iOS integrated patch; 2(d): the seams; 4(f): `drop-F1.patch`), so `device-ab.sh` is a convenience, and the integrated patch is the one artefact the text cannot replace. Ship dependency list below made complete (whole `wave52-gate/` directory) |
| 5 | should-fix | suite-count bullet contradicts itself ("run by nothing documented" + "the tables now carry its row") | CONFIRMED: README:217 / CLAUDE:297 / STATUS:2459 rows end `\| 30 \|`; `doc-staleness-check.sh:194-200` only warns, message now false; no `check_suite_count` for it; script untouched by 0dda427e | FIXED: ios-harness sentences replaced — row exists since wave 52, compile-only (Known-broken), the warn is stale and never compares the count; Next = `check_suite_count "ios-harness" "$IOS_HARNESS_DECLS" README.md CLAUDE.md docs/STATUS.md`. Checked by reading that it would match: `doc_quotes_suite_count` (`doc-staleness-lib.sh:28`) needs the keyword in the first cell and `\| 30 \|` later, no `\|` inside the cells; `grep -rE "func test"` = 30. The script itself was not edited (only BACKLOG is in bounds) |
| 6 | nit | host-wide `pkill -9 -x adb` missing from the gate-driver Known-broken item and the recipe | CONFIRMED: `section-runner.sh:641` (`_android_candidates`), `provision-devices.sh:127` (`_adb_devices`) | FIXED: appended to the Known-broken item, plus one clause in the port-guard recipe's "Not yet true of the gate itself" sentence. Did NOT adopt "`adb kill-server` alone restarts the shared server" as the fix: both sites bound `kill-server` with `alarm 10` because the server wedges (retro 2026-09-06 comments), so the pkill is the fallback for a server that does not answer; wrote the narrower direction (kill only the tcp:5037 listener once `kill-server` times out), labelled untested |
| 7 | nit | 0(b′) lost "it is not what blocked −006" | CONFIRMED: `git diff` shows it removed; `wave52-small-fixes/_note.md` never mentions 006, so L4 did not supersede it | FIXED: "— it is not what blocked −006, and **it is NOT zero-movement**" |

Fixed 6, refused 0, handed off 1 (#4 — git state).

**Ship dependency (orchestrator, same commit as `docs/BACKLOG.md`):**
- `tools/visual/probe-text-metrics.sh` and `tools/visual/web-port-guard.test.mjs` (working-tree edits) — the port-guard recipe now
  names the probe as a sourcer. If they do not ship, revert that recipe to two scripts and add "`probe-text-metrics.sh` still
  kills whatever holds its port (Known-broken)".
- `tools/titan/results/wave52-gate/` whole (`device-ab.sh` calls `installed-build-hash.sh`; its read-out is `ab-diff.mjs`;
  `build-hashes.txt` is the include-arm hash record) and
  `tools/titan/results/wave52-vertical-wedges/ma-exclude-ios.integrated.patch`.
- `tools/titan/own-processes.sh` + `own-processes.test.mjs`, or drop the "A wave-52 working-tree implementation exists …"
  sentence from the gate-driver Known-broken item. Committing the test file (≈5 `test(` blocks; the guard-test edit adds
  none) moves the `node --test` tooling count that `doc-staleness-check.sh` derives — restamp README/CLAUDE/STATUS after the sweep.
- This note directory (`tools/titan/results/wave52-docs/`).

## Fix pass (third skeptic review, 2026-10-05)

Each check was re-run read-only first (sed / grep / `git grep` / `git diff` / `git log`, python over committed JSON and over
the gitignored fixtures on disk; no build, test, device or git-state command; nothing under `tools/titan/runs/` read). After
the pass: `docs/BACKLOG.md` 3727 lines, `git diff --stat` +913 / −139, first hunk still at line 781, nothing touched above
"## Ranked queue"; `grep -c '\[\[GATE\]\]'` = **22** (was 21: +1 for 2(c¹)'s exclusion; §2 table refreshed, every line
re-checked to carry its token); `grep -c 'scratchpad/'` = 0.

| # | sev | problem | re-check | action |
|---|---|---|---|---|
| 1 | should-fix | Instrument bullet (L6 cssom `requires-script-mutation` tags) says the wall tag excludes unconditionally | CONFIRMED: `inject-wpt-block.mjs` `applyNaScoreGate` has `if (EXTRACTION_WALL_TAGS.has(t)) return !delivered;` with `delivered = postLoadExtracted === true \|\| structureExtracted === true`; `post-load-extract.mjs` `shouldPostLoadExtract` route 1 = `hasWallTag(naTags)`, tags from `wpt-buckets.json` `.notApplicable`. PLAN.md:704 carries the same error | FIXED with the skeptic's text, plus one verified addition: if the post-load pass delivers, the re-extracted fixtures may still carry the pre-script `@counter-style` — `inPageSerializer` returns the cloned `head.innerHTML` (no `cssRules` / `styleSheets` read anywhere in the file), and the counters brief §4 T7 already says the valid tests "need post-load CSSOM serialisation". The PLAN §10 pointer now says its "excludes unconditionally" is the error corrected here. Decide-line now asks for a full extract + capture run, not an inject-only calibration. The 21 / 13 numbers kept (they match PLAN §10 / ORCHESTRATOR-TODO §1d) |
| 2 | should-fix | PLAN §6 native-near-misses T2-children/T8/T9/T10/T11 and counters-and-multicol B/C queued nowhere | CONFIRMED: `grep -c` 0 for every carrier (one `attr-style-sharing` hit is `-4`, an unrelated 2(a) cell). Tree re-checked before writing: no CSS 2.1 §17.2.1 / anonymous fixup in `TableBoxTree.{kt,swift}` (wave 52's only edit there is L8's `columnChains`); `SELF_COLLAPSE_HEIGHT_TYPES` still in Compose `ComponentRenderer.kt`; Compose `background/` has no interpolation module, `GradientInterpolation.swift` exists; `AbsposInsetStretch.kt` exists; 0 `shadowRoot` / `attachShadow` in `extract-fixture.mjs` and `post-load-extract.mjs` | FIXED: new **11(h)** (inside "Untracked native/web fails with named mechanisms") with T8 / T9 / T10 / T11 / T2-children and B / C, cells, mechanisms, brief pointers, "census before staffing", and PLAN §6's "wave-53 lanes" for T10 / T11. T12 left out — it is L8's M-G (1(d)). One correction to the brief, from the tree: B's "never reaches the wire" is a wave51-fix observation — the gitignored `fixtures/wpt/css-lists/counter-reset-reversed-list-item(-start).json` written 2026-10-05 carry `_pseudo.marker {content: none}` on all 9 of 9 components, and the converter forwards `_pseudo` → `pseudos` verbatim (`CssParsing.kt`). So 11(h) says: re-read the `wave52-final` IR first; if the bag arrives, the defect is its consumer. No web consumer mechanism was claimed |
| 3 | should-fix | 4(c) F2 arm A points at a wave51-fix `.app` hash no record holds | CONFIRMED: `git grep` finds no `.app` digest under `tools/titan/results/wave51*`; `corpus-v6-17.json` `_note` records APK sha1s only (984c4a69… / fe4ec50a…); the claim exists only in BACKLOG, the L9 note:332, its skeptic and ORCHESTRATOR-TODO:239 | FIXED with the skeptic's text. The four F2 pieces checked by grep: `InlineSpanRing.swift` `glyphless:`, `InlineRunFlow.swift` passes `glyphless:`, `TypographyApplier.bandedRun`, and L9 note's F2 row naming seam-2 (seam-2 also carries F4's `blockEllipsisClamp` threading, hence "band hunk" only) |
| 4 | nit | 2(c¹) states armenian-008's native exclusion as fact before the item's token | CONFIRMED: `score-gate.wave52-open.wave52-calib.txt:4` `unmeasured-now 19`, armenian-008 ios/android still scored at :528-529 (f 0.942 / 0.9423); PLAN.md:717 pre-registers 21 | FIXED: "— [[GATE]] its two native cells are predicted to leave the denominator at the closing gate (−1 each, 0 on pass; unmeasured-now 19 → 21)" (+1 token → 22) |
| 5 | nit | the opus fan-out recipe supersedes a rule the wave skill still states | CONFIRMED: `.claude/skills/wave/SKILL.md:130-132` under "Phase 2 — builder lanes" | FIXED: appended "(`.claude/skills/wave/SKILL.md` "Phase 2 — builder lanes" still prescribes `model:'opus'` overrides only to the dead lanes — update it in the wave-52 ship commit.)" — the section named by its heading instead of the skeptic's "§ lane fan-out", which is not a heading there. SKILL.md itself not edited (out of bounds) |

Fixed 5, refused 0. Ship dependency added: `.claude/skills/wave/SKILL.md` Phase 2's dead-lanes sentence (item 5).

STATUS: COMPLETE
