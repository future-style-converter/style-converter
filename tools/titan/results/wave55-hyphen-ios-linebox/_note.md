TREES: /private/tmp/claude-501/-Users-dranak-Documents-Projects-Style-Converter--claude-worktrees-trusting-bohr-bd6fbf/0c47cad8-074d-4821-bf4c-b5997f23f535/scratchpad/wave55-build-L1

# wave55 L1 · hyphen-ios-linebox — builder lane note (unit NB-ios + the pre-built contingency NB-ios-b)

Base tree 611c9f93 (= dev 0d0d3a3d + the committed wave-55 plan and opening-gate records). Contract: wave55-plan/PLAN.md §0,
§2 "### L1 · hyphen-ios-linebox", §3 row 6 / 6b, §8 step 3; brief wave55-plan/hyphen-ios-linebox.md. Built and verified in the
worktree on the TREES: line only; this directory is the only shared-tree path written. Nothing was committed; no device,
Chromium, emulator, simulator, gate or full suite was run.

## Pin mutation brackets (append-only; written by `mutations.py` BEFORE each edit / AFTER each byte-exact restore)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (M1: round the SUM (not each term))
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (M1)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (M2: ceil the sum)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (M2)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (M3: .toNearestOrEven (banker's) instead of lroundf)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (M3)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (M4: drop the exactness guard)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (M4)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (M5: drop the WPT guard)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (M5)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (M6: let the Blink box override a non-nil effective)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (M6)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (M7: resolve every design to Inter in faceMetrics)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (M7)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (M8: drop the horizontal-writing-mode guard (D22))
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (M8)
(attempt 1 above, M1–M8: every mutation turned its pin RED and every restore was byte-exact, but the driver's green
check counted 8/9 — the env-gated SAME-controls log test SKIPS without `TEST_RUNNER_NB_IOS_PER_TEST_IR`, which the
driver did not pass; record `mutations.attempt1.out.txt`. The driver now takes `--ir`; attempt 2 follows, after the
test file's SAME-controls walk gained the renderer's `all` reset — the census finding below.)
(attempt 2, M1–M8, driver with --ir:)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (M1: round the SUM (not each term))
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (M1)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (M2: ceil the sum)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (M2)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (M3: .toNearestOrEven (banker's) instead of lroundf)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (M3)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (M4: drop the exactness guard)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (M4)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (M5: drop the WPT guard)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (M5)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (M6: let the Blink box override a non-nil effective)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (M6)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (M7: resolve every design to Inter in faceMetrics)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (M7)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (M8: drop the horizontal-writing-mode guard (D22))
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (M8)
(contingency NB-ios-b, M9 on the NB-ios + NB-ios-b state, driver --only M9 --expect 10:)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 a5f073ee8491af36809f9dd7074049d224c68497b04968680b2954827bc806cf  (M9: NB-ios-b: verticalPads ignores blinkPinned (always symmetric))
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 a5f073ee8491af36809f9dd7074049d224c68497b04968680b2954827bc806cf  (M9)
(M9 green = 10 / 10: `mutations.M9.out.txt`. Attempt 2 of M1–M8: `mutations.out.txt` — every verdict OK, 9 / 9 green after each restore.)

## What changed and why

**The defect** (brief §1–§2, pictures re-opened: `wave55-plan/hyphen-ios-linebox.look-001.png`; ref vs wave54-final iOS of
hyphenate-character-004, boundary-shaping-001, hyphens-vertical-001, boundary-shaping-010, anonymous-table-ws-001 and
all-prop-001 composed side by side): every hyphenate-character group from the second on sits one to two rows HIGH on iOS
(`wave54-final css-text/hyphens/hyphenate-character-004 ios f 0.9477`), because a declared-`normal` run lays out on the face's
RAW metrics (DejaVu 16: 18.625 px/line, unpinned, N-line box ceiled) where Chromium and Compose use Blink's ROUNDED box (19).

**The change (unit NB-ios).**
- NEW `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift` (160 lines, every code
  line commented; enum of pure statics):
  - `NormalLineBox.blinkLineSpacing(ascender:descender:leading:)` — lroundf of each term, half away from zero, summed
    (Blink `SimpleFontData::PlatformInit`, cited from memory as in the brief; measured by the Arial 92 ref box 105).
  - `NormalLineBox.faceMetrics(fontSizePx:design:faceName:)` — the rendered face's (ascender, descender, leading) in
    `LineBoxMetrics.contentHeight`'s order: document face → Inter (else Inter's hhea table 1984 / −494 / 0) → system face.
  - `NormalLineBox.usedLineHeight(effective:declaredNormal:lineCountIsExact:wptCapture:horizontalWritingMode:fontSizePx:design:faceName:)`
    — `effective` verbatim when non-nil; Blink's box iff declared `normal` ∧ WPT capture ∧ exact line count ∧ horizontal
    (D22); nil otherwise (and nil for a non-positive box). Below the raw line height (Libertine 36, Inter 92) it logs ONCE
    (`PropertyTracker.logOnce` key `line-height:normal-blink-below-raw`: a multi-line run keeps the raw advance — SwiftUI
    `.lineSpacing` cannot be negative). That breadcrumb is the PLAN §0 "NormalLineBox log-once".
- NEW `runtimes/swiftui/Tests/StyleConverterRuntimeTests/NormalLineBoxTests.swift` (191 lines): pins P1–P8 + the SAME-controls
  log (below).
- Seam 1 (`seam-1.patch`, ComponentRenderer.swift `PlaceholderLabel.body`): cut the `leading` statement (base :5055-5069),
  insert `let usedLineHeight = NormalLineBox.usedLineHeight(…, horizontalWritingMode: horizontalWritingMode, …)` after
  `lineCountIsExact` (base :5151-5152), re-insert `leading` reading `usedLineHeight`, and `pinnedBoxHeight(lineHeightPx:
  usedLineHeight` (base :5154). One call site, one argument, one moved statement (+34 / −16 lines; 6291 → 6309). The wave-30
  pin (CSS 2.1 §10.8) then lays N line boxes of exactly 19 px; `subNaturalShift` and the frame `minHeight` read the pin.
- Spec: CSS 2.1 §10.8.1 (`normal` is UA-defined; strut), §10.6.3 (auto height = line boxes), §15.8 (`font` resets
  `line-height`); css-writing-modes-4 §7 (the vertical guard).

**Deviation from the brief's P6 wording (recorded, not hidden).** The brief's P6 compared `ascender − descender + leading`
with `LineBoxMetrics.contentHeight`. On Mac Catalyst `UIFont.systemFont(ofSize: 16).lineHeight` is 18.0 while its
`ascender − descender + leading` is 18.84375 (first run, executed), so that equality is false on the only test surface even
though both functions resolve the SAME face. P6 now pins the resolution itself: `contentHeight` equals the resolved UIFont's
`lineHeight` (or Inter's table) AND `faceMetrics` returns that same UIFont's triple (or the same table), for `.default`,
`.monospaced`, `.serif`. M7 (every design → Inter) turns it red on `.monospaced` / `.serif` (executed).

## Revert unit NB-ios (one commit) — paths
- `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift` (NEW; in `unit-NB-ios.patch`)
- `runtimes/swiftui/Tests/StyleConverterRuntimeTests/NormalLineBoxTests.swift` (NEW; in `unit-NB-ios.patch`)
- `runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift` (seam; `seam-1.patch`, header names base
  611c9f93, sha256 at base 417853ce…26f3, the content anchor)
- applyOrder: unit-NB-ios.patch, then seam-1.patch (the seam consumes `NormalLineBox`; rule 2b holds: the owned files compile
  without the seam — state "unit only" below). revertOrder [NB-ios]. Cross-wave: additive on the wave-54 U3b — revert NB-ios
  BEFORE U3b, never U3b alone (replay Rd: -004 ios P → f).
- Checked on a `git archive 611c9f93` export: each patch applies alone, the pair applies, and reverse-applies back to the
  base bytes (ComponentRenderer.swift sha256 417853ce…26f3).

## Contingency NB-ios-b (pre-built; NOT a unit until its trigger) — paths
- `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift` (+18: `verticalPads(halfLeading:blinkPinned:)`
  → `EdgeInsets`: (0, 2 × half) for a Blink-pinned run, (half, half) otherwise; 178 lines) and
  `runtimes/swiftui/Tests/StyleConverterRuntimeTests/NormalLineBoxTests.swift` (+8: P9; 199 lines) — in `unit-NB-ios-b.patch`,
  cut against the NB-ios state (a temporary index holding NB-ios, so the patch is the delta only).
- `runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift` — `seam-1b.patch` (base = NB-ios landed,
  sha256 208e41ba…c5f5, 6309 lines): `.padding(.vertical, leading.halfLeading)` (611c9f93 :5335 → :5353) →
  `.padding(NormalLineBox.verticalPads(halfLeading: leading.halfLeading, blinkPinned: effectiveLineHeight == nil && pinnedBoxHeight != nil))`.
- Lands ONLY by `land-units.sh --contingency L1:NB-ios-b` after the stage-1 trigger (`anchor: group 1 line 1 offset 1` alone on
  the 3 gating rows), then the appended `probe-decisions/contingency.json` row + regeneration (NB-ios's revertOrder becomes
  [NB-ios-b, NB-ios]) and the pre-registered re-probe `wave55-pre-r1 --sections css-text`. Delivered now so the swap needs no
  fix-lane round trip; checked on the export: applies on top of NB-ios, and the four patches reverse in [NB-ios-b, NB-ios]
  order back to the base bytes. Its only risk outside the trigger case: `.padding(EdgeInsets(h, 0, h, 0))` replaces
  `.padding(.vertical, h)` for every other run — the same insets, but a device-unverified modifier swap (it would be read by
  the re-probe's css-text identity rows before anything else).

## Pins and mutations (executed; brackets above)
| pin | test | asserts | mutation → red (executed) |
|---|---|---|---|
| P1 | testP1DejaVu16IsTheRefNineteenPixelPitch | DejaVu 16 (1901/−483/0) → 19 | — (the anchor; M1 / M2 leave it green: 18.625 rounds and ceils to 19) |
| P2 | testP2EachTermRoundsAloneNeverTheSum | Arial 92 → 105, DejaVu 20 → 24 | M1 round the sum (106 / 23), M2 ceil the sum (106) |
| P3 | testP3HalvesRoundAwayFromZero | (14.5, −4.5, 0) → 20 | M3 `.toNearestOrEven` (18); M1 / M2 also redden it (19) |
| P4 | testP4BlinkBoxOnlyUnderAllFourGuards | all four guards → the Blink box; each alone false → nil | M4 drop exactness, M5 drop WPT, M8 drop horizontal |
| P5 | testP5EffectiveValueIsReturnedVerbatim | effective 37.5 verbatim under all 16 flag sets | M6 Blink overrides a non-nil effective |
| P6 | testP6FaceResolutionMatchesLineBoxMetrics | same face as LineBoxMetrics.contentHeight (default / monospaced / serif) | M7 every design → Inter |
| P7 | testP7VerbatimHostInputs | hyphenate-character-001 `__1-177` verbatim: lineHeightIsNormal, .monospaced, effective nil, used = Blink box | input pin (held green) |
| P8 | testP8VerticalRunIsDeclinedByTheHorizontalGuard | hyphens-vertical-001 `__2-320` verbatim: vertical, singleLineText, exact, renderedLineCount 1 → nil; horizontal: true → non-nil | M8 drop the guard |
| P9 (NB-ios-b) | testP9PadSplitOnlyForABlinkPinnedRun | (0.1875, false) → (0.1875, 0.1875); (0.1875, true) → (0, 0.375) | M9 split never fires |
Driver: `mutations.py` (bracket lines written by it before every edit and after every byte-exact restore; post == pre sha256
on all 17 brackets). Records: `mutations.attempt1.out.txt` (8 × RED, restores byte-exact, green miscounted 8/9 — the
env-gated test skipped), `mutations.out.txt` (attempt 2: M1–M8 all OK, 9/9 green), `mutations.M9.out.txt` (OK, 10/10).

**The Catalyst log the plan asks for** (`catalyst-focused.log.txt`, the runtime's own predicates on the VERBATIM wave54-final
per-test IR, `TEST_RUNNER_NB_IOS_PER_TEST_IR` set): P8 `hyphens-vertical-001 __2-320 horizontal=false singleLineText=true
lineCountIsExact=true renderedLineCount=1`; SAME controls: all-prop-001 **0** declared-normal runs (its `All: INITIAL` drops the
LineHeight declaration before the merge — `GlobalExtractor.applyingAllReset`, the renderer's order); anonymous-table-ws-001 1
run (`a b`, horizontal, pre-broken, exact, 1 line); disclosure-styles 18 runs (6 horizontal + **12 vertical** — the D22 guard
declines those 12), all pre-broken, exact, 1 line; boundary-shaping-010 3 runs (single, exact, 1 line); hyphens-vertical-001 2
runs (vertical → declined). The census asserts these values (its section (5)).

## Census (`census.py` → `census.out.txt`; run: `/usr/bin/python3 -B census.py wave54-final --patches unit-NB-ios.patch seam-1.patch unit-NB-ios-b.patch seam-1b.patch`)
Written independently of the brief's `.census.mjs`: it models the delivered predicate guard by guard (the `all` reset,
InheritedText's inherited set PARSED from the Swift source, the `hyphens: auto` dictionary arm, admitsPreBreak,
hasSoftWrapOpportunity, the vertical guard), measures each run's advance and both line boxes from the FACE FILES with
fontTools (DejaVu Sans Mono, Inter, the documents' own @font-face files), joins `cells-wave54-final.json`, and asserts the
Catalyst log.
- Radius: 71 declared-normal text runs (58 components) in **18** documents (the brief: 60 components in 19).
- Capture carriers: **iOS 14 — IDENTICAL to `expectations.json` lanes["L1-hyphen-ios-linebox"].captureCarriers.ios**
  (TARGET 4: hyphenate-character-001…004, multi-line pitch 18.625 → 19 — -002 via the `hyphens: auto` dictionary arm, not the
  brief's file-name rule; MOVES 10: boundary-shaping-001…008 one-line box 42 → 41, text-decoration-dotted-001 / -002 112 → 111).
  web 0 · Android 0 · wire 0 (section (4): the four patches touch `runtimes/swiftui/` only). revertUnits NB-ios: the same 14.
- In-radius must-not-move (SAME / DECLINED): disclosure-styles (L5 U1-ios carrier by C5; 6 runs pinned 20 → 20, 12 vertical
  declined), anonymous-table-ws-001 (19 → 19), boundary-shaping-010 (44 → 44), hyphens-vertical-001 (declined).
- **Delta vs the plan: the census did NOT grow (no regeneration need).** It SHRANK by one document: `all-prop-001` is not in the
  radius at all (`all: initial` resets its `line-height` to the undeclared → calibrated row, a non-nil `effective` returned
  verbatim). Consequence for the readers: PLAN §2 L1 R1 counts all-prop-001 among the four in-radius tripwire controls; an
  iOS move there would be a LEAK from some other unit, not R1 evidence about NB-ios's snapping. The R1 tripwire is the three
  in-radius SAME controls above (anonymous-table-ws-001, boundary-shaping-010, disclosure-styles' 6 horizontal runs) plus
  hyphens-vertical-001's guard.
- What it cannot decide statically: the wrap width (the run's own `width`, else the 374-px canvas content width — every
  one-line verdict here has the run far inside it or unbreakable); the route (every text run is assumed to reach
  PlaceholderLabel — brief R6; the stage-1 css-text identity rows are the check).

## Geometry probe self-test (wave54-final)
- `geometry-gate.py wave54-final --base wave54-final --lanes L1 --self-test` → **HOLDS**, exit 0: gating 3 / 3 FAIL, control
  15 / 15 PASS, report 11 / 11 FAIL (`geometry-selftest.base-wave54-final.txt`); `--base wave55-open` → HOLDS identically
  (`geometry-selftest.base-wave55-open.txt`).
- The task text's `--base wave54-open` → FAILS, 5 violations (`geometry-selftest.base-wave54-open.txt`): the `glyphs:` rule
  compares line counts with the BASE capture, and wave54-open predates wave-54's hyphenate-character line fixes (`15 lines vs
  base 13` on the web / Android controls). The plan's cmd is `--base <base>` = wave55-open, whose self-test HOLDS; the
  wave54-open base is the wrong base for this probe, not a probe defect.

## revertStatesCompiled (each: the worktree brought to the state from the delivered patches — `git checkout -- .`, `git clean -fd -- <owned new files>`, `git apply` — then Catalyst build + focused suites)
revertStatesCompiled: NB-ios (611c9f93 + unit-NB-ios + seam-1; files byte-equal the working copies): BUILD OK · 81/81 PASS (NormalLineBoxTests 9, LineHeightNormalTests 16, LineBoxPinTests 10, WPTCaptureModeTests 38, LineHeightWireTests 8)
revertStatesCompiled: NB-ios reverted = 611c9f93 clean: BUILD OK · 72/72 PASS (the four existing suites; NormalLineBoxTests absent)
revertStatesCompiled: rule 2b (611c9f93 + unit-NB-ios, seam-1 absent): BUILD OK · 81/81 PASS
revertStatesCompiled: contingency chain [NB-ios-b, NB-ios] — NB-ios + NB-ios-b (all four patches; bytes equal the working copies): BUILD OK · 82/82 PASS
revertStatesCompiled: contingency rule 2b (NB-ios + unit-NB-ios-b, seam-1b absent): BUILD OK · 82/82 PASS
revertStatesCompiled: NB-ios-b reverted → NB-ios (bytes sha256-equal to the NB-ios state): BUILD OK · 81/81 PASS; then NB-ios reverted → 611c9f93: the 72/72 state above
Final restore: the worktree is left at the NB-ios state (unit-NB-ios + seam-1 applied; NB-ios-b not), sha256-checked.

## Predictions (from wave54-final = wave55-open; the plan's, unchanged by this lane — the census is identical)
| cell | from → predicted | confidence | geometry |
|---|---|---|---|
| `wave54-final css-text/hyphens/hyphenate-character-001 ios P 0.9611` | P ≈ 0.978 (Rm) | MED (R1 snapping) | `hyphen-ios-linebox 001 ios → LINEBOX OK` (gating) |
| `wave54-final css-text/hyphens/hyphenate-character-003 ios P 0.957` | P ≈ 0.976 | MED | gating 003 ios LINEBOX OK |
| `wave54-final css-text/hyphens/hyphenate-character-004 ios f 0.9477` | P ≈ 0.975 (flip; iOS +1) | MED | gating 004 ios LINEBOX OK |
| `wave54-final css-text/hyphens/hyphenate-character-002 ios P 0.9534` | P ≈ 0.969, stays DEGENERATE | MED | report 002 ios |
| boundary-shaping-001…008 ios (P 0.9792 / 0.9695 / 0.9781 / 0.9774 / 0.9774 / 0.9714 / 0.9764 / 0.9789) | P, same to ±0.0001 | MED-HIGH | report `stack …` STACK OK |
| `wave54-final css-text-decor/text-decoration-dotted-001 ios f 0.5318`, `-002 ios f 0.5426` | f ≈ 0.5355 / 0.5472 | LOW-MED | report `stack …` STACK OK |
| anonymous-table-ws-001 / boundary-shaping-010 / hyphens-vertical-001 ios; every web / Android cell; all-prop-001 ios (outside the radius) | byte-identical | HIGH (web / Android, vertical, all-prop: by construction) · MED (the two one-line SAME rows: R1) | the 6 web / Android LINEBOX controls + the 9 wave-54 GEOMETRY controls stay OK |
If NB-ios-b lands instead (R1 world): the same rows, the gating keys then predicted OK under both snapping models (brief §4).

## Sizes (house rules)
NormalLineBox.swift 160 (178 with NB-ios-b) ≤ 200; NormalLineBoxTests.swift 191 (199) ≤ 200; ComponentRenderer.swift (already
past 300): seam patches only — one call site, one argument, one moved statement (+34 / −16); NB-ios-b: one modifier (+7 / −1).

## ORCHESTRATOR WINDOW REQUESTS
L1 has no window of its own (PLAN §2 L1); it rides these, each preceded by the §7 `beforeEveryDeviceRun` string and its hashRead:
1. **Stage 1** — `tools/titan/gate-driver.sh wave55-pre --sections css-counter-styles,css-flexbox,css-grid,css-masking,css-text,css-text-decor,css-writing-modes,filter-effects,selectors --skip-fixture-net`
   (expectations.json `stage1Probe.cmd`; reads per `stage1Probe.readOut`). L1's own read:
   `/opt/homebrew/bin/python3.12 -B tools/titan/results/wave55-plan/hyphen-ios-linebox.geometry.py wave55-pre --base wave55-open`
   and `node tools/titan/results/wave52-gate/cells.mjs 'hyphenate-character|boundary-shaping|hyphens-vertical-001|anonymous-table-ws-001|text-decoration-dotted' wave55-open wave55-pre`.
   Expected: `hyphen-ios-linebox 001 / 003 / 004 ios … → LINEBOX OK`; the 6 web / Android controls OK; 002 ios OK (report);
   the 8 boundary-shaping and 2 dotted `stack … → STACK OK`; -001 / -003 / -004 ios ≈ 0.978 / 0.976 / 0.975 (all ≥ 0.95);
   boundary-shaping-010 and hyphens-vertical-001 ios byte-identical; no other iOS css-text cell moves ≥ 0.002.
   Decides (stage1Decision): KEEP NB-ios; or, iff the ONLY reason on the 3 gating rows is `anchor: group 1 line 1 offset 1`,
   `tools/titan/results/wave55-plan/land-units.sh --contingency L1:NB-ios-b` (patches already in this dir) + the contingency.json
   row + regeneration + `tools/titan/gate-driver.sh wave55-pre-r1 --sections css-text --skip-fixture-net`; any other WRONG →
   revert NB-ios (one commit).
2. **Stage 2** — `tools/titan/gate-driver.sh wave55-probe --sections <expectations.json probeRun.cmd>`: L1's radius rows of
   css-tables (anonymous-table-ws-001 ios byte-identical), css-counter-styles (disclosure-styles ios: L5's carrier; from L1's
   side identity — 6 runs pinned at today's 20, 12 vertical declined), css-text-decor (dotted STACK rows), css-cascade
   (all-prop-001 ios: OUTSIDE L1's radius — a move there is a leak to bisect, never R1 evidence).
3. **Closing arm `drop-NB-ios`** — `tools/titan/results/wave55-gate/ab-arms55.sh <arms-file> --run --against wave55-final`, read
   by `node tools/titan/results/wave52-gate/ab-diff.mjs wave55-ab-drop-NB-ios wave55-final --threshold 0.002 --platforms ios`.
   Expected (expectations.json abArms): exactly 6 differing cells — hyphenate-character-001…004 ios (Δ ≈ +0.017 / +0.016 /
   +0.019 / +0.027, -004 a flip) and dotted-001 / -002 ios (≈ +0.004 / +0.005); hyphens-vertical-001 byte-identical; any other
   iOS cell is a leak. Decides the NB-ios attribution at the closing gate.

## Not verified here
- Nothing on device: the snapping model (R1), the rendered boxes, the scores, the STACK rows — stage 1 decides.
- The Blink rounding rule is cited from memory (no Chromium checkout); it rests on the brief's ref measurements.
- `.padding(EdgeInsets)` ≡ `.padding(.vertical,)` pixel-identity for non-Blink runs under NB-ios-b (geometry identical by
  construction; never rendered here).
- The census's route assumption (every text run reaches PlaceholderLabel) and its static wrap-width bound.
- No full suite, no land-units.sh landing; `land-units.sh --check --only L1` was run read-only on the shared tree (below).

## Landing check (read-only)
`bash tools/titan/results/wave55-plan/land-units.sh --check --only L1` on the shared tree (record `land-units.check.L1.txt`):
exit 0, "0 error(s), 1 warning(s) — landable" — both NB-ios patches apply alone and cumulatively on 611c9f93, the 3 touched
paths are clean at HEAD, the [NB-ios] revert dry-run reverse-applies, and "L1:NB-ios-b (contingency) applies on top of the
series"; the one warning was this note's own freshness (< 120 s).

## Delivered patches (sha256 of the file bytes, written after the final restore)
patch: unit-NB-ios.patch sha256 5658918ab46c298956abb5e41ff2311e1696a2169a7e8dee5321216873663728
patch: seam-1.patch sha256 5c9f15bda9b97951ffba650a190cb9ae45d4af5a81cb63c82d35e9970f751721
patch: unit-NB-ios-b.patch sha256 8f99c2a49b96b2582d130b975c3012cf8e9a523b2ab03bbdba6d977460095aed
patch: seam-1b.patch sha256 8d83b71687417e6cb0cb4d4b913c53a830fd9caf27b90245db950233ca450c1f

STATUS: COMPLETE
