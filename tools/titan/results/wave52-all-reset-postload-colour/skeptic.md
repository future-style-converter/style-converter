# Wave 52 · L11 · all-reset-postload-colour — skeptic review (2026-10-05)

Tree: campaign/wave52 @ 7d9c22a7 (shared; other lanes' uncommitted work present). Inputs read: PLAN.md §2 L11, §3, §5, §9;
`_note.md` (ends `STATUS: COMPLETE`); every artifact in this directory; the brief `colour-not-reaching-text.md`.
Everything below was EXECUTED by the skeptic with its own scripts (scratch copies under the session scratchpad; the
load-bearing numbers and the scripts' logic are recorded here because the scratchpad is purged).

## Verdict

The runtime half (F1 on three platforms) and the post-load half (F2) do what the note says on the corpus: pins green,
mutations caught, censuses reproduce EXACTLY with independent scripts, seams apply on their stated bases and after every
other lane's seam patches, and the predicted flip survives a corrected simulation. **One must-fix**: the new fixture
`fixtures/combinations/all-then-color.json` encodes a CSS-false oracle for 3 of its 4 components (it would bless the
natives' wrong box and flag the browser's correct render). Several should-fix items are corpus-neutral gaps the lane did
not log or overclaimed (the extractor's bag order is NOT always cascade order; natives do not model `display: initial`
= inline under `all: initial`).

## Executed repros

| # | what | result |
|---|---|---|
| 1 | web pins `npx vitest run tests/global/globalPhase10.test.ts` | 8/8 pass |
| 2 | post-load suite `node --test tools/titan/post-load-extract.test.mjs` | 101 tests, 100 pass, 1 skip (the 8 L11 pins all `ok`) |
| 3 | Compose pins `:runtime:testDebugUnitTest --tests …global.AllResetTest --rerun` (JDK 21) | XML `tests="8" failures="0" errors="0"` (the lane's `jvm-allreset-run1.log` shows no counts — Gradle prints none; the 8/8 claim is now evidenced) |
| 4 | MUTATION web: `_dispatch.ts` before-filter exemption → `false` | `all-prop-001 … survive` FAILS (`expected undefined to be 'rtl'`); restored sha256 b86105d1… IDENTICAL |
| 5 | MUTATION web: `StyleBuilder.ts` `all` back at the tail (`{}` start + `Object.assign(styles, globalStyles)` at the old site) | 3/8 FAIL (initial-color first-key, all-prop-001, display-contents-button); restored 080af5f7… IDENTICAL |
| 6 | MUTATION post-load: `introducesOverParent` `return value !== parent` → `return true` | 2 FAIL (`EQUAL … not introduced`, `nth-child-of-class …`); restored ac3ae096… IDENTICAL |
| 7 | MUTATION Compose: `AllReset.kt` INITIAL channel `inherited.filter{EXEMPT}` → `emptyList()` (not one of the lane's M1–M5) | 2/8 FAIL (`initial keeps an inherited direction`, `all-prop-001 …`); restored 55f4572f… IDENTICAL |
| 8 | MUTATION iOS (isolated package copy, never the shared tree): keep-channel keywords filter the channel to exempt types | `testInitialDropsTheChannelAndTheOthersKeepIt` (5 asserts) + `testLastAllGoverns` FAIL — 6 failures / 7 tests |
| 9 | `git apply --check` on stated bases (scratch copy of HEAD files) | seam-1 ✓, seam-2 ✓, seam-3 ✓ (test-file hunk at offset +1 — the lane's test file moved 1 line after the cut), hunk-for-L3-1 ✓ on HEAD's AND on L3's in-flight `ContentsUnboxingTests.swift` |
| 10 | landing-order check: every other lane's ComponentRenderer.kt / .swift / extract-fixture.mjs seam applied first, then L11's | seam-1 ✓, seam-2 ✓ (offset 4), seam-3 ✓ after L5's six + L1's hunk (offset 411). No hunk overlap with any registry row |
| 11 | seam-1 UNDER LOCK (`wave52-lock/ComponentRenderer.kt`), twice: pre sha1 0a437851 = HEAD; `compileDebugKotlin` executed; AllResetTest 8, ContentsUnboxingTest 11, InheritanceAndFlexGatingTest 12, BackgroundColorInheritanceTest 11, TransformInheritanceSeamTest 3 — 0 failures; `git show HEAD:… >` → 0a437851 IDENTICAL; unlocked |
| 12 | seam-2 UNDER LOCK (`wave52-lock/ComponentRenderer.swift`, taken after L6 released it): pre f3be3161 = HEAD; private DerivedData pre-warmed; `SwiftCompile … ComponentRenderer.swift` in the log; AllResetTests 7/0, ContentsUnboxingTests 11/0, `TEST SUCCEEDED`; restored f3be3161 IDENTICAL; unlocked |
| 13 | seam-2 + hunk-for-L3-1 in an ISOLATED copy (Package.swift + runtimes/swiftui, HEAD ComponentRenderer.swift, L3's in-flight test file) | 0 remaining callers of `applyingAllReset(to:)`; AllResetTests 7/0 + ContentsUnboxingTests 11/0 |
| 14 | seam-3 UNDER LOCK (`wave52-lock/extract-fixture.mjs`): pre 022edcd0 = HEAD; post-load suite 102 tests / 101 pass / 0 fail (new pin `ok 102`); extract-fixture link pins 9/9; MUTATION guard lets `initial` through → the new pin FAILS; extract-fixture restored from HEAD 022edcd0, test file restored from backup 78ba1fb1 — both IDENTICAL; unlocked |
| 15 | OWN F1 census (python, every `tools/titan/runs/wave51-fix/sections/*/per-test-ir/*.json`): carriers = any `All`; old = drop-everything, new = exempt-before + after-minus-All; `Display CONTENTS` ⇒ RC6-stripped | 1435 docs · **11 tests / 28 components** (keywords INITIAL 9, INHERIT 3, REVERT 15, UNSET 1; 0 keyword-less) · 5 RC6 carriers (all unchanged) · **6 tests / 7 components change natively** (the same seven the note lists) · web: **130** declarations before `all` now filtered (all-prop-001 60, appearance-revert-001 70). Inherited-channel side (ancestors' inherited types, Compose 42-type set and iOS `InheritedText.inheritedTypes`): only the four colour spans receive a non-empty channel (Color red), and all-prop-001's `<bdo>` (INITIAL ⇒ only Direction flows). All equal to the lane's numbers |
| 16 | OWN F2 census: own manifest scan (277 `postLoadExtracted`), and for EVERY test (not only the structure path) two full passes — route OFF then ON — diffing every component's `color` | 277 extracted, 0 errors, **13 tests / 27 components** gain a colour (26 × rgb(0,128,0), 1 × rgb(154,205,50) on has-visited's parent3); tags: p / span / (div); no a/button/input/select/textarea/mark/hr. Identical to `replay-f2-all.json`'s 13 / 27 |
| 17 | order premise: real `:converter:run` on `fixtures/combinations/all-then-color.json` | `[All, Width, Height, BackgroundColor]`, child `[…, All]`, `[Direction, All, …]` — source order preserved |
| 18 | PNG — predicted flip `selectors/has-visited ios f 0.9469`: opened ref + ios/web/android wave51-fix captures + the lane's predicted PNGs; own simulation with `diffWebVsRef` (re-score of the unmodified capture reproduces 0.9469 / 0.9509 / 0.9854) | lane's bands-1-and-5 recolour: ios 0.9566. **Corrected** (whole inheriting subtree: the `<a>` components carry NO Color in the IR and paint black today, so after F2 they inherit green / yellowgreen, plus parent3's "any link" line and its "."): ios **0.9573–0.9574**, android 0.9552 (lane 0.9557), web 0.9963 (lane 0.9977). The flip survives (+0.0073 above the bar) |
| 19 | PNG — at-risk `all-prop-001` natives (opened ref + all three captures): simulate the likely RTL picture (both digit lines right-aligned at x 374) | ios 0.9640 → 0.9644, android 0.9641 → 0.9644 — moving ink inside a region the ref leaves white costs nothing; the P→f risk is lower than "MED" unless the natives paint something larger |
| 20 | browser probe (pinned puppeteer Chromium, per-key CSSOM writes in the new `all`-first order) of the fixture's shapes | `ATC_AllThenProps`: `display: inline`, rect **0×0**; `ATC_InitialUnderRedParent` child: inline **110×23** (not 200×60); `ATC_DirectionSurvives`: inline **30×23** |
| 21 | extractor bag-order probe (real `extractFixture` with `htmlOverride`, nothing written) | `.a{all:initial}.b{color:red}.c{all:initial}` → bag `{all, color: red}`; `.a{color:red}.b{all:initial}.c{color:green}` → `{color: green, all}`; `td{all:initial}` → `{all, padding-*: 1px}` (UA cell-padding bake appended AFTER `all`) |
| 22 | ownership | `git status` / `git diff --name-only` filtered to files mentioning L11: exactly the "own:" list (AllReset.kt + test, GlobalExtractor/GlobalConfig.swift, AllResetTests.swift, AllApplier/AllExtractor/_dispatch.ts, StyleBuilder.ts, globalPhase10.test.ts, post-load-extract.mjs + test, all-then-color.json). L3's `ContentsUnboxingTests.swift` diff is at :34, :42, :193+ only — :175-192 untouched. No seam file left modified |
| 23 | rules | new files ≤ 200 lines (121 / 198 / 168 / 67); dense why-comments; no test-name carve-outs in any added code line (grep for test names / `.html` / `backdrop` in non-comment `+` lines: none); ring-fence untouched; no device A/B staged by L11 (hash sentence n/a) |

## Defects

### MUST-FIX

**M1 — `fixtures/combinations/all-then-color.json`: the `_expect` oracle is CSS-false for 3 of 4 components.**
`all: initial` resets `display` to its initial value `inline` (css-cascade-4 §3.1 + css-display-3 initial value), so
`width` / `height` do not apply. Executed (repro 20): `ATC_AllThenProps` renders 0×0 (nothing paints), not the asserted
green 160×60; `ATC_InitialUnderRedParent`'s child is an inline 110×23 box (the blue 200×60 parent dominates), not green
200×60; `ATC_DirectionSurvives` is an inline 30×23 box, not green 200×40. The `_comment` ("the box survives") is wrong in
the same way. Consequence: the browser (correct) fails the oracle; the natives — which do not model `display: initial` =
inline (S2) — paint the boxes and PASS it. The pin blesses the wrong render. (Only `ATC_PropsThenAll_InGreenParent` is
right.) The fixture is also not in `tools/visual/gate-fixtures.txt` (README: a fixture off that list is never executed),
so as shipped the brief's pin 7 is vacuous until the orchestrator's owed baseline capture — at which point it goes red.
Fix: give each `all: initial` component an explicit `display: block` AFTER `all` (that is the CSS way to keep the box,
and it exercises exactly the §6.4 ordering), or re-derive `_expect` to the inline truth; re-state the comment.

### SHOULD-FIX

**S1 — the F1 premise "the IR list order IS the cascade order" (AllReset.kt KDoc, GlobalExtractor doc, `_note.md` §1)
is false for two extractor shapes.** (a) `assignDeclaration` re-assigns an existing key IN PLACE (`target[key] = value`),
so a key keeps the slot of its FIRST declaration: `.a{all:initial}.b{color:red}.c{all:initial}` → `[All, Color red]` →
under F1 all three runtimes paint RED where CSS (and the old drop) gives black — a regression shape; and
`.a{color:red}.b{all:initial}.c{color:green}` → `[Color green, All]` → F1 still paints black (truth green). (b) UA bakes
APPENDED after the author bag land after `all`: seam-3 fixes the link bake only; the UA table-cell `padding: 1px` bake
does the same for `td { all: initial }` (repro 21). Corpus-neutral today (repro 15: no carrier has either shape), so not
a gate risk — but the KDoc overclaims and nothing logs it. Fix: state the limit in the KDoc / note, and hand L5 (the
extractor seam owner) the general fix (delete + re-insert on re-assignment so the bag order is the WINNING order; UA bakes
before the author bag or suppressed under an author non-revert `all`, generalising seam-3).

**S2 — natives do not model the non-inherited INITIAL values that differ from their own defaults (above all
`display: initial` = inline) and do not log it.** Before F1 the drop-everything made this invisible (nothing after `all`
survived); after F1 any box declarations that FOLLOW `all: initial` paint on Compose/iOS where the browser makes the
element inline and ignores them (repro 20 is exactly this). Likewise `all: revert` should hand non-inherited properties
the UA-origin value. The KDoc logs only the `all: inherit` gap. Corpus-neutral (every corpus INITIAL carrier with box
props after `all` is either RC6-stripped or carries an explicit `Display INLINE`), so add a `PropertyTracker` line / KDoc
KNOWN GAP for INITIAL/UNSET non-inherited initials and REVERT's UA values ("no silent fallthrough").

**S3 — the has-visited predicted picture is wrong in detail.** `png-replay.mjs` assumes the links "keep their own
(chromatic, untouched) ink"; in the wave51-fix IR the three `<a>` components carry no `Color` and every capture paints
them black, so after F2 they inherit green / yellowgreen (and parent3's wrapped "any link" line and its "." go
yellowgreen too). Corrected simulation (repro 18): ios 0.9573–0.9574 (flip holds), android 0.9552, web 0.9963. Update the
§5 table and the residual description.

### NIT

- **N1** No pin exercises the seam wiring itself: `AllResetTest.resolved()` / `AllResetTests.resolved` re-implement the
  seam's call order, so a mis-wire (e.g. passing the un-reset `inheritedProperties` to `mergeInherited`) keeps every
  JVM / Catalyst pin green; only the device gate would see it.
- **N2** Compose ordering change: `ListStyleUaRule` now runs on the post-reset own list, so `ol { all: initial }` gets the
  UA `decimal` (old Compose: dropped; CSS: initial `disc` and `display: inline`). Brings Compose to iOS parity; 0 carriers.
- **N3** seam-3 is a new hunk on L5's seam outside the §3 registry; it MUST land in the same gate as seam-1/seam-2/web F1
  (without it all-prop-initial-visited gains a UA underline on all three — confirmed against the ref PNG, which has none).
  The note says so; the orchestrator must add the registry row.
- **N4** `jvm-allreset-run1.log` is not evidence of "8/8" (no counts in Gradle output) — now evidenced by repro 3's XML.
- **N5** all-prop-001's P→f risk is likely overstated (repro 19: right-aligned RTL picture 0.9644 on both natives);
  still open its PNG at the gate.

## Numbers the orchestrator can take as re-derived

F1: 11 tests / 28 `All` components; natively 6 tests / 7 components change; web 130 pre-`all` declarations filtered.
F2: 277 post-load tests; 13 tests / 27 components gain a colour (A/B over every test). Flip: has-visited ios f → P (MED,
corrected sim 0.9573). Watchlist: every moved cell matches a PLAN watchlist line except
`css-pseudo/first-letter-block-to-inline` (in `watchlist-additions.txt`).

## Re-verify (2026-10-05, after the lane's fix pass — `_note.md` §7, still ends `STATUS: COMPLETE`)

Scope: the one must-fix (M1, `fixtures/combinations/all-then-color.json` `_expect` CSS-false + off the gate list) and
regressions introduced by the fix pass (the corrected fixture, `seam-4.patch`, `hunk-for-orchestrator-1.patch`).
Every check below was EXECUTED by the skeptic with its own scripts (session scratchpad; load-bearing numbers recorded
here because the scratchpad is purged). Shared tree, HEAD 7d9c22a7; nothing in the tree left modified by the skeptic.

### M1 — CONFIRMED FIXED

| # | check | result |
|---|---|---|
| R1 | real `:converter:run` on a scratch copy of the corrected fixture (sha256 87e19d8f…) | `[All INITIAL, Display BLOCK, Width, Height, BackgroundColor]`; child `reset` `[Width, Height, BackgroundColor, All]`; `span` `[All, Display BLOCK, Color, …]`; `[Direction RTL, All, Display BLOCK, …]` — `Display` immediately after `All` in all three box rows; IR property lists IDENTICAL to the lane's `convert-out/` |
| R2 | OWN CSS-truth probe — a different mechanism from the lane's (one STYLESHEET class rule per component, declarations in source order, NOT CSSOM per-key writes), pinned puppeteer Chromium, 390-px #1A1A2E canvas, judged by the REAL `spec-oracle.mjs` `parseExpectations` + `measureCapture` | **4/4 green**: AllThenProps block 160×60 fill green(9600) box [160,60]; PropsThenAll child `inline 0×0`, transparent, box [160,60]; InitialUnderRedParent span `block 200×60`, white, box [200,60]; DirectionSurvives `block 200×40 dir=rtl`, box [200,40] |
| R3 | MUTATIONS of R2 (scratch fixture copies; mine, not the lane's set) | X1 every `display` deleted (= the pre-fix fixture) → 3 FAIL (0×0 / blue [110,23] / [30,23] — exactly my first-review repro 20); X2 `all` moved last in every box row → 3 FAIL; X3 `all` removed (keep-before) → PropsThenAll FAILS, red(7500) out-votes green — the 150×50 child does make the row failable |
| R4 | the REAL web harness (private vite, `?mode=capture`, legacy path), MY converter output served by interception, OWN probe script, real oracle — HEAD harness source | 2 violations: AllThenProps box [358,60], DirectionSurvives [358,40] (the defect seam-4 targets — reproduced) |
| R5 | seam-4 UNDER LOCK (`wave52-lock/ComponentRenderer.tsx`): pre sha1 020b0d14 = HEAD; `git apply` seam-4; `npx vitest run tests/sdui` (whole directory, 23 files) | **23 files / 195 tests pass** (incl. the new 3-test pin, RendererParity golden, maxSizeFloor, swarm003) |
| R6 | R4's harness probe with seam-4 applied (same lock) | **0 violations** — all four rows green (AllThenProps [160,60], DirectionSurvives [200,40]) |
| R7 | MUTATIONS of seam-4 (mine; same lock): MUT-B guard inverted (`styles.all !== undefined`) | pin 1 + pin 3 FAIL (2/3) |
| R8 | MUT-A2 over-broad drop (`if (false) kept[key] = value` — every default dropped under `all`) | exactly pin 2 FAILS (`expected -1 to be ≥ 0`, max-width:100% gone). (My first MUT-A attempt was malformed — a stray `void value;` outside the loop threw a ReferenceError — so its red is NOT counted; R8 is the valid re-run.) |
| R9 | restore | `git show HEAD:… >` → sha1 020b0d14 IDENTICAL, `git diff --quiet` vs HEAD, new pin file removed, lock dir removed — after both locked sessions |
| R10 | `git apply --check` on HEAD | seam-4 ✓ (both files); hunk-for-orchestrator-1 ✓ (`gate-fixtures.txt` + `combinations/README.md`); both target files and `ComponentRenderer.tsx` are byte-identical to HEAD in the tree (L11 did not edit them directly) |
| R11 | OWN seam-4 reach census (python; every wave51-fix per-test IR + every `fixtures/**/*.json` component incl. nested children / buckets) | WPT 1435 docs / 28 `All` components; width/max-width AFTER the last `All`: **0** (all-prop-001's two carry them BEFORE `all`, filtered by F1); legacy fixtures: 9 `all` carriers, **3** with width after `all`, all in `all-then-color.json` (longtail All_* have `all` last). = the lane's `census-seam4.json` |
| R12 | lane pins re-run | the lane's `fixture-oracle-probe.mjs --mode css` (output redirected to scratch; its committed JSON sha unchanged 9944d081): PROBE OK, M1–M4 each caught; `globalPhase10.test.ts` 8/8; `post-load-extract.test.mjs` 101 / 100 pass / 1 skip |
| R13 | runtime files untouched by the fix pass | AllReset.kt 55f4572f, `_dispatch.ts` b86105d1, StyleBuilder.ts 080af5f7, post-load-extract.mjs ac3ae096 (sha256 prefixes) = the hashes my first-review mutations restored to; post-load test sha1 78ba1fb1 = my first-review backup. Only the fixture changed (18:39) — the Compose/Catalyst pins verified in the first review stand |
| R14 | ownership | L11-attributable tree changes = exactly its "own:" list; `Renderer/StyleBuilder.swift` is L8's (M-B `ch` comment), `ContentsUnboxingTests.swift` hunks at :34/:42/:193+ are L3's (:175-192 untouched); no seam file left modified |

### NEW — fix-pass regressions

**RV-M1 (must-fix) — the gate-list hunk lands a fixture predicted to turn the gate set red (cross-platform pair gate, exit 4).**
`hunk-for-orchestrator-1.patch` puts `all-then-color.json` on `tools/visual/gate-fixtures.txt` with no baseline, i.e.
gate-only: the cross-platform pair gate (SSIM < 0.95 | Δpx > 2 % | ΔE95 > 5) still runs. `all: initial` resets
`font-family` to the UA initial face, so the two text rows split serif (web) vs sans (natives). Executed:
- the REAL harness (R4 run, computed style): `span-005` and `atc_directionsurvives-006` paint in **Times**; every other
  component paints in `Inter, -apple-system, …`. The natives drop the inherited channel under INITIAL and draw their
  system sans.
- proxy captures (CSS truth; web = Times, "iOS" = `-apple-system`/SF, "Android" = Roboto/Arial, the face re-declared
  after `all` on the text nodes only) run through the REAL `compare-screenshots.mjs` (`--input
  fixtures/combinations/all-then-color.json`, scratch REPORT_DIR/MANIFEST_OUT): **exit 4**, 2 unexpected —
  `ATC_InitialUnderRedParent` iOS-web **SSIM 0.9415** (Δpx 1.59 %), Android-web **0.9420** (Δpx 1.52 %); iOS-Android 0.9971.
  DirectionSurvives passes (0.984). The spec oracle is 0 violations on all 12 checks. The standalone `span` child capture
  carries the same text, so it is likely a further red pair.
Real native glyph rasterisation differs from Chromium's Times by at least as much as the proxy's sans does, so the proxy is
optimistic, not pessimistic. As handed off, the closing gate's fixture net (`BASELINE=1 ./test-all.sh --gate-set`
exit 0 ×8, PLAN §4.6) would re-raise exit 4. The note and the hunk header say only "may need a ledger line": an
unmeasured blast radius, now measured as failing. Fix (any one): (a) drop `_text` from `ATC_InitialUnderRedParent`
— the v1 oracle cannot see glyph colour anyway (the `_comment` says so), and the README says "no text unless the
interaction itself needs it"; (b) re-declare the harness's `font-family` AFTER `all` on both text nodes, which is the same §6.4 ordering;
(c) ship a ledger line (reason / owner / expiry) inside `hunk-for-orchestrator-1.patch`. Then re-run the proxy.

**RV-S1 (should-fix / nit) — `ATC_DirectionSurvives` cannot fail on direction.** Proxy natives that DROP `direction: rtl`
(digits at the left): oracle 0 violations, pair gate iOS-web 0.9541 / Android-web 0.9542, so it passes. The row pins
only the box (display:block after `all`; X1/X2/M4 catch that). The `_expect.note` admits "position is v2". The direction
exemption is pinned only at unit level (AllReset pins), and the natives' RTL painting stays device-gated (the all-prop-001
risk). Do not count this row as direction evidence, and LOOK at its first capture.

**RV-N1 (nit, reasoned, NOT measured).** Web F1 filters the declarations before `all` out of `styles`, so
`calibrateStyles`' post-`...styles` floors (`minWidth: styles.width || '50px'`, same for height) now see no width/height
for `all`-LAST components. For longtail `All_Inherit` / `All_Revert` / `All_RevertLayer` (display stays block) the
min-height floor becomes 30px instead of 60px. Those boxes are transparent (or ground-coloured) and the rows are neither
on the gate list nor baselined, so no gate can move. Recorded only so the census is complete.

### Re-verify verdict
M1 is fixed and proven: CSS truth by two independent mechanisms, the real harness with seam-4, mutations on both, and an
order premise on the real converter. Seam-4 is sound and lock-verified. No runtime regression. One new must-fix in the fix
pass's own hand-off: the gate-list hunk would land a predicted-red cross-platform pair (exit 4), so the fixture needs its
text-face dependence removed, or a ledger line, before that hunk lands.
