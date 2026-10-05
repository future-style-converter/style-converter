# Wave 52 · L11 · all-reset-postload-colour — lane note

Brief: `tools/titan/results/wave52-plan/colour-not-reaching-text.md` §4 F1 + F2 (F3 `::first-letter` not staffed).
PLAN.md §2 L11, §3 rows `:1134-1137` / `:330-345`, §4 "L3 before L11", §9 C5. Tree: campaign/wave52 @ 7d9c22a7.

**Resume check (2026-10-05).** No prior partial work existed: this results dir did not exist, and `git diff` /
`git status` showed no edit to any path in the L11 "own:" list (the only near file, `ContentsUnboxingTests.swift`, carried
L3's in-flight edit, outside `:175-192`, untouched by L11). Everything below was built in this session.

## 1. What changed and why

**F1 — the ORDER-AWARE `all` reset, byte-parallel on the three runtimes** (css-cascade-4 §6.4 order of appearance,
§3.1 the `direction` / `unicode-bidi` exemption, §7.3 the per-keyword inherited value). One rule: the LAST keyword `All`
at index i governs; own[0,i) drops except `Direction` / `UnicodeBidi`; own(i,end] is kept; the inherited channel is
dropped for `INITIAL` (except an inherited `Direction`) and kept for `INHERIT` / `UNSET` / `REVERT` / `REVERT_LAYER`;
every `All` entry leaves the list. Before: Compose/iOS dropped EVERYTHING (own + inherited) when any `All` appeared in
the merged list, and web emitted `all` as the LAST style key so the browser reset the declarations written before it.

- Compose — NEW `runtimes/compose/src/main/java/com/styleconverter/runtime/global/AllReset.kt` (pure; `PropertyTracker
  .markUnhandled` for an unknown keyword and for the `all: inherit` known gap — non-inherited properties cannot take the
  parent's value through the inherited channel, all-prop-002's `display: inherit`). Wiring is **seam-1.patch**
  (`ComponentRenderer.kt`: `AllReset.apply(schemeResolvedProperties, inheritedProperties)` BEFORE `ListStyleUaRule` /
  `mergeInherited`; the post-merge `allReset … emptyList()` drop removed).
- iOS — `StyleEngine/global/GlobalExtractor.swift`: NEW `applyingAllReset(own:inherited:)` (same rule, `PropertyTracker
  .logOnce` for the same two cases); `GlobalConfig.swift` doc rewritten. Wiring is **seam-2.patch** (`ComponentRenderer
  .swift` `mergedProperties`: the reset runs on `motionEffectiveProperties` + `inheritedTextProperties` BEFORE
  `InheritedText.merge`; the UA tag rules read the post-reset own list). The LEGACY `applyingAllReset(to:)` is KEPT in
  the tree (its only callers are `ContentsUnboxingTests.swift:175-192`, L3's file) and is deleted by
  **hunk-for-L3-1.patch**, which also rewrites those two pins — see §4.
- Web — `engine/global/_dispatch.ts` NEW `applyAllReset` (own-list half; the browser does the inheritance half);
  `core/renderer/StyleBuilder.ts` emits `{ all }` as the FIRST key and filters the list through `applyAllReset` before
  every other phase (the old tail `applyGlobalPhase10` call removed); `AllApplier.ts` / `AllExtractor.ts` comments.

**F2 — the post-load overlay may INTRODUCE `color` when it differs from the PARENT's computed colour**
(`tools/titan/post-load-extract.mjs`): `WRITE_RULES.color = { repairOnly: true, introduceWhenParentDiffers: true }`,
new exported `introducesOverParent` + `UA_COLOURED_TAGS` (`a, button, input, select, textarea, mark` per the brief, plus
`hr` — html.css `hr { color: gray }` — added after the executed 277-test replay showed `css-pseudo/active-selection-057`'s
empty `<hr>` as the only UA-ink introduction outside the six); `mergePostLoadIntoFixture(…, opts)` looks each record's
parent up by walk path (top level → the BODY snapshot, which `inPageWalker` now returns as `bodyStyles`); both merge call
sites pass it. Never in the absorbed-wrapper fold mode (`onlyMissing`). css-color-4 §3.2: `color` inherits, so a child
whose computed colour differs from its parent's carries a cascaded declaration — exactly what a stale pre-script bake misses.

**Found while building — seam-3.patch (`tools/titan/extract-fixture.mjs` `uaLinkProps`, :10054).** The UA link bake is
APPENDED after the author bag, so for `a { all: initial }` (css-cascade/all-prop-initial-visited) the IR reads
`[All INITIAL, TextDecorationLine UNDERLINE, …]`. Harmless under the old drop-everything; under F1 the underline FOLLOWS
`all` and would paint on all three platforms (ref: no underline). Guard: an author `all` other than `revert` /
`revert-layer` decides every UA link property (css-cascade-4 §3.1 + §6.1). Its pin rides inside the same patch (appended
to L11's `post-load-extract.test.mjs`) so no tree state is red. **seam-3 must land in the same gate as F1.**

## 2. Verification (all executed; logs in this directory)

| check | result | evidence |
|---|---|---|
| Compose `AllResetTest` (8 pins; VERBATIM wave51-fix IR of all-prop-{initial,inherit,unset,revert}-color, all-prop-001, display-contents-button) | 8/8 green | `jvm-allreset-run1.log` |
| Compose mutations M1–M5 (old drop / keep-before / no exemption / INITIAL keeps channel / first-not-last) | every one caught, sha256 restored IDENTICAL | `mutations.log`, `mutate-compose.sh` |
| iOS `AllResetTests` (7 pins, same verbatim IR; RC6 root strip check on display-contents-button) | 7/7 green (Catalyst) | `catalyst-allreset-run1.summary.log` |
| iOS mutations M1–M5 | every one caught, IDENTICAL | `mutations.log`, `mutate-swift.sh` |
| web `globalPhase10.test.ts` (8; `Object.keys(styles)[0]==='all'`, verbatim span/all-prop-001/button lists) + conformance | 8/8, 74/74 with conformance; `tsc --noEmit` clean | `vitest-global-run1.log` |
| web mutations W1–W4 (`all` back at the tail / no before-drop / no exemption / first `all`) | every one caught, IDENTICAL | `mutations.log`, `mutate-web.sh` |
| post-load F2 pins (7, incl. VERBATIM nth-child-of-class source + measured live colours) + full suite | 100 pass, 1 pre-existing env-gated skip | `node --test tools/titan/post-load-extract.test.mjs` |
| F2 mutations P1–P4 (no introduce / no equality / no tag skip / no fold guard) | every one caught, IDENTICAL | `mutations.log`, `mutate-postload.sh` |
| seam-1 (Compose) under the lock: compiles, AllResetTest + ContentsUnboxingTest, then 9 classes (Inheritance/BlockFlow/FontSize%/Multicol×2/Outline/Global) | green, seam sha IDENTICAL after restore | `seam-verification.log` |
| seam-2 (iOS) under the lock: AllResetTests + ContentsUnboxingTests (18), then the wider 7-class set (+ InheritanceWave9, IOSTextLane, Phase10, UAWidgets, FidelityWave1 — 78 tests) | green both times, seam sha IDENTICAL after each restore | `seam-verification.log` |
| hunk-for-L3-1 + seam-2 in an ISOLATED package copy (scratch; L3's file in the tree never touched) | AllResetTests 7 + ContentsUnboxingTests 11 green; 0 callers of the legacy fn left; the hunk (1-line context) applies on BOTH HEAD's and L3's in-flight test file, and its output is byte-identical to the verified copy | `seam-verification.log` |
| seam-3 under the lock: post-load suite 101 pass + extract-fixture ua-link pins 5/5; mutation S1 (guard removed) caught | green, both files IDENTICAL | `seam-verification.log`, `mutations.log`, `verify-seam-3.sh` |
| pin 4 — converter order premise (real `:converter`): `all` FIRST / LAST / after `Direction` exactly as the fixture's source order, and (fix pass) `Display` RIGHT AFTER `All` in the three box rows; `longtail.json` All_* all LAST (⇒ F1 leaves them byte-identical, brief pin 7); mutation (reversed keys) fails the check | 12/12 (re-run in the fix pass) | `order-premise.mjs`, `order-premise.json`, `order-premise.log` |
| pin 7 — the fixture's `_expect` oracle (fix pass, skeptic M1): the REAL `spec-oracle.mjs` judging (a) the CSS truth (fixture declarations per key in source order, pinned Chromium, 390-px #1A1A2E canvas) and (b) the REAL web harness (vite `?mode=capture`, converted IR served by request interception) | CSS truth 4/4 green; harness 4/4 green WITH seam-4, 2 violations at HEAD (358-px boxes — the defect seam-4 fixes); mutations M1 pre-fix / M2 display-before-all / M3 keep-before-all / M4 order-blind each caught | `fixture-oracle-probe.mjs`, `fixture-oracle-probe.json` (css), `.harness-head.json`, `.harness-patched.json`, `mutations.log` |
| seam-4 (web-harness `ComponentRenderer.tsx`) under the lock: patched vitest 5 files 65/65 (new pin + RendererParity golden + maxSizeFloor + swarm003 + ComponentRenderer); harness oracle probe green; MUTATION HEAD source + new pin → pin FAILS | green; mutation caught; seam sha IDENTICAL after restore, pin file removed | `verify-seam-4.sh`, `seam-verification.log`, `seam-4-*.out` |
| web key-order premise (brief §9 unverified item) in the pinned HeadlessChrome/151: SSR string and per-key CSSOM writes | old order `color…;all` → rgb(0,0,0); new `all;color…` → rgb(0,128,0) on both paths | `web-order-probe.mjs/.json` |

## 3. Censuses

**F1 (`census-f1.mjs` → `census-f1.json`, all 1435 wave51-fix per-test IR docs; inherited set parsed from
`ComponentRenderer.kt`, RC6 strip modelled):** 11 tests / 28 `All` components (the brief says "9 tests / 28 components" —
its own `allCarriers` list names these same 11 tests: a miscount, components agree). Natively, **6 tests / 7 components**
change effective lists: all-prop-{initial,inherit,unset,revert}-color (`[] → [Color]`), all-prop-001 (`.test`
`[] → [Direction, UnicodeBidi]`, `<bdo>` `[] → [Direction]`), all-prop-initial-visited (`[] →` the 27 post-load computed
initials + the UA underline — the underline goes away with seam-3). **Unchanged natively:** all-prop-002 (empty channel),
css-ui/appearance-revert-001 (14 carriers, `all` LAST, empty channel), display-contents-{button,details,fieldset} (the RC6
strip removes `All` first — confirmed on iOS by `testDisplayContentsButtonRootStripsAllBeforeTheReset` and by the census
model; 0 RC6 carriers change). **Web:** `all` moves from the last key to the first in all 11 tests; 130 declarations that
preceded `all` (all-prop-001 60, appearance-revert-001 70) are now filtered (CSS-identical — the browser reset them anyway);
the after-`all` lists of display-contents-* (border 10px red + `display: contents`) and all-prop-initial-visited now
follow `all` instead of being erased by it.

**F2 (`replay-f2.mjs --all-postload` → `replay-f2-all.json`, EXECUTED: the real static extraction + post-load overlay in
headless Chromium on every one of the 277 postLoadExtracted wave51-fix tests, fixtures kept in memory; the
structure-path tests re-run with the route off and subtracted):** 277/277 extracted, 0 errors (31 via the structure path); **13 tests / 27 components gain an introduced colour** (final code; the pre-`hr` run, kept as `replay-f2-all.pre-hr.json`, had 14 / 28 — the extra one was `css-pseudo/active-selection-057`'s empty `<hr>`, now skipped). Every introduced value is green except has-visited's yellowgreen; no `a`/`button`/`input`/`select`/`textarea`/`mark`/`hr` component gains one. The brief's heuristic said 83
"colour candidates"; the measured gainers are the brief's family (nth-child-of-{class, class-prefix, attr, id-prefix,
in-is, has, ids}, negated-nth-{child,last-child}-when-ancestor-changes, nth-child-containing-ancestor, class-id-attr)
plus two it did not name: `selectors/has-visited` (parent1 green, parent3 yellowgreen — `:has(:link)` /
`:has(:any-link)` matches the static parser rejects) and `css-pseudo/first-letter-block-to-inline` (the script's
`inner.style.color = "green"`). UA-tag population re-derived (`census-f2-uatags.mjs`): 17 post-load tests with one of the
six tags (= the brief), 1 with `<hr>`; none of their `a/button/input/select/textarea/mark/hr` components gains a colour.

## 4. Seam patches and hand-offs

- `seam-1.patch` — Compose `ComponentRenderer.kt` :1105-1137. On HEAD 7d9c22a7, no prior lane. Registry row `:1134-1137`.
- `seam-2.patch` — iOS `ComponentRenderer.swift` :292-345. On HEAD, no prior lane. Registry row `:330-345`.
- `seam-3.patch` — `tools/titan/extract-fixture.mjs` :10054 (+ its pin in L11's `post-load-extract.test.mjs`). Cut on HEAD;
  no L5 hunk within 300 lines; apply after L5's set (offset only). **NEW cross-lane item: L5 is told here** (PLAN §3 said L5
  is the only lane on this seam; this is a 13-line guard outside every L5 hunk).
- `seam-4.patch` (FIX PASS) — `apps/web-harness/src/sdui/ComponentRenderer.tsx` `calibrateStyles` + new helper
  `harnessDefaultsUnderAll`, and a NEW pin file `apps/web-harness/tests/sdui/ComponentRenderer.allHarnessDefaults.test.tsx`
  (3 tests, ATC_AllThenProps IR verbatim). On HEAD; no other lane patches this seam (L6's conditional T3 row was not
  needed — `wave52-counters-and-lists/_note.md:83`). **NEW registry row for the orchestrator.** Must land with L11's web F1.
- `hunk-for-orchestrator-1.patch` (FIX PASS) — `tools/visual/gate-fixtures.txt` (+1 line: `all-then-color.json`, gate-only +
  oracle until baselines are seeded) and a `fixtures/combinations/README.md` table row. Both files are owned by no lane.
  `git apply --check` on HEAD ✓; test-all's own `_gate_set_fixtures` parses the patched list and every path exists
  (the `test-all-guards.test.mjs` contract). Land only together with web F1 + seam-4.
- `hunk-for-L3-1.patch` — `ContentsUnboxingTests.swift` :175-192 rewrite (L3's file) + deletion of the legacy
  `GlobalExtractor.applyingAllReset(to:)` (L11's file). Apply AFTER L3 merges and AFTER seam-2, in L11's commit (PLAN §9 C5).
- **To L5 (FYI):** your extract+convert differential with `POST_LOAD_EXTRACT` SET runs over a tree that carries F2 — its
  "changed docs" will include F2's introduced colours on the gainer tests above; attribute those to L11, not to F-D.
- **To the orchestrator — BACKLOG 0(m) replacement text:** "0(m) **A computed colour never reaches the text run** — three
  mechanisms, two fixed in wave 52 (L11): (052) the runtimes' order-blind `all` reset (all three; now order-aware —
  css-cascade-4 §6.4/§3.1/§7.3), (055) the post-load overlay could not INTRODUCE `color` (now: introduce when the computed
  colour differs from the parent's, UA-coloured tags skipped). Still open: (087) `::first-letter` dropped at parse time
  (`extract-fixture.mjs` SUPPORTED_PSEUDO_ELEMENTS) — brief F3, ~15 colour-only carriers, extractor seam."
- Fixture `fixtures/combinations/all-then-color.json` (4 components, `_expect` per the combinations README) — CORRECTED in the
  fix pass (§7): every `all: initial` row that keeps a box re-declares `display: block` after `all`; the PropsThenAll child
  grew to 150x50 so its oracle can fail. Converts (order premise above); **baselines NOT captured** (device-free lane) —
  capture + LOOK before committing them.

## 5. Predictions (gate cells, wave51-fix → after)

PNG replay (`png-replay.mjs` → `png-replay/`; the wave51-fix capture with the named lines' ink recoloured to the ref's
colour, scored by the gate's own `diffWebVsRef` against the manifest's `browserRef.path`; every unmodified capture
re-scored to EXACTLY its manifest number first):

| cell | wave51-fix | predicted | colour residual (ΔE mean / mismatched px) |
|---|---|---|---|
| all-prop-initial-color web | P 0.9783 | P 0.9786 (serif face remains — brief §9) | 0.324→0.235 / 1350→1197 |
| all-prop-initial-color ios | P 0.9989 | P 0.9993 | 0.172→0.044 / 466→76 |
| all-prop-initial-color android | P 0.9993 | P 0.9984 | 0.159→0.059 / 263→117 |
| nth-child-of-class web / ios / android | P 0.999 / 0.9976 / 0.9965 | P 0.9999 / 0.9977 / 0.9955 | web 462→0 px; natives 819→531, 630→546 |
| has-visited web / ios / android | P 0.9854 / **f 0.9469** / P 0.9509 | P 0.9977 / **P 0.9566** / P 0.9557 — skeptic-corrected (repro 18, not re-run here): P 0.9963 / **P 0.9573–0.9574** / P 0.9552 | 2248→830 / 3995→2480 / 3351→2314 (lane's sim) |
| nth-child-of-has web / ios / android | P 0.999 / f 0.9371 / P 0.96 | P 0.9999 / f 0.9376 / P 0.9595 | (ios deficit is the line overlap — no flip, as the brief said) |
| first-letter-block-to-inline web / ios / android | P 0.9989 / 0.999 / 0.9995 | P 0.9999 / 0.9993 / 0.9986 | 440→0 / 430→87 / 218→101 |

- **Flips: `selectors/has-visited ios f 0.9469 → P` (+1 iOS, MED** — a simulated recolour 0.0066 above the bar (the skeptic's
  corrected picture: the three `<a>` carry NO `Color` in the IR and paint black today, so after F2 they INHERIT green /
  yellowgreen with their parents — parent3's wrapped "any link" line and its "." included; `png-replay.mjs` wrongly assumed
  the links keep their own ink — 0.9573, +0.0073 above the bar); its only
  failing condition today is SSIM, every veto false). No other flip. The colour cells are picture-correctness rises:
  colour residuals fall everywhere; native SSIM moves within ±0.001 (luminance-dominated metric), so "≈1.000" in the
  brief was optimistic — label these P→P picture-correctness, not score.
- **At risk (P→f): `css-cascade/all-prop-001` ×3 (MED, unchanged from the brief)** — natives newly apply `Direction RTL` +
  `UnicodeBidi BIDI_OVERRIDE` (the §3.1 exemption) to the un-baked "321" / "987 654" lines; web is CSS-identical (the
  browser already kept them). Open its PNG at the gate. `css-cascade/all-prop-initial-visited` ×3: neutral WITH seam-3;
  gains a wrong underline WITHOUT it (LOW→MED). `css-display/display-contents-{button,details,fieldset}` **web** (LOW-MED,
  NEW): `display: contents` + the 10px red border now FOLLOW `all` on web instead of being erased by it (natives unchanged
  — RC6 strip); the text should unbox the same way, but no web capture was run.
- Watchlist: every moved cell is already covered by PLAN watchlist lines 169-180 except
  `css-pseudo/first-letter-block-to-inline` → `watchlist-additions.txt` (watchlist-check: `unmatched 0`).

## 6. Not verified

- No device or web capture (lane rule): every native/web picture claim above is a simulation (PNG replay) or a unit pin.
- How Compose / SwiftUI render `Direction RTL` + `UnicodeBidi BIDI_OVERRIDE` on all-prop-001's un-baked text (the P→f risk).
- display-contents-{button,details,fieldset} web pictures after `display: contents` follows `all` (no web capture).
- The `all-then-color.json` baselines (need `UPDATE_BASELINE=1 ./test-all.sh` + a LOOK), and the fixture's NATIVE renders: the
  oracle is proven against the CSS truth and the real web harness only; Compose / SwiftUI see `[All, Display BLOCK, …]` →
  (AllReset keeps own(i,end]) `[Display BLOCK, Width, Height, …]`, which they paint as a block — unit-level reasoning, no capture.
- Cross-platform pairs of the two TEXT rows (InitialUnderRedParent, DirectionSurvives): `all: initial` resets font-family to
  its UA-dependent initial value, so the glyph faces may differ by platform; the first gate run may need a ledger line there.
- Why the ref's `all: initial` span keeps an Inter-width face (brief §9) — caps all-prop-initial-color web at ≈0.979.

Device-gated by the lane rules (handed to the orchestrator's sweep/gate, not owed here): the `all-then-color.json` baselines and every PNG claim above.

## 7. Fix pass — skeptic review (`skeptic.md`, 2026-10-05)

**M1 (must-fix) — the fixture's `_expect` was CSS-false for 3 of 4 rows. FIXED.** `all: initial` resets `display` to its
initial `inline` (css-display-3 §2), so width / height stop applying (CSS 2.1 §10.3.1 / §10.6.1): the first cut asserted
160x60 / 200x60 / 200x40 boxes the browser paints as 0x0 / 110x23 / 30x23. Every box row now re-declares `display: block`
immediately AFTER `all` — itself the §6.4 ordering under test. The `_comment` carries the hand derivation; each `note` says why.
Two further defects found while verifying, both fixed in the same pass:
- **The PropsThenAll row could never fail.** A 120x40 red child in a 160x60 green parent ties 4800 / 4800 px, and the oracle's
  mode breaks ties to the smaller packed RGB (green): a runtime that wrongly kept the before-`all` declarations PASSED. The
  child is now 150x50 (7500 red vs 2100 green if wrongly painted) — mutation M3 proves it fires.
- **The real web harness rendered two rows 358 px wide** (measured, `fixture-oracle-probe.harness-head.json`): F1 makes
  `buildStyles` emit `all` FIRST, but the harness's `calibrateStyles` creates its legacy defaults (`width: fit-content`,
  `maxWidth: 100%`) BEFORE `...styles`; the author's `width` then overwrites the default IN PLACE and keeps the default's slot
  (ES2015 own-property order), i.e. BEFORE `all`, which resets it. **seam-4.patch** drops a default the author re-declares when
  `all` is present (all-free objects untouched). Reach (`census-seam4.mjs` → `census-seam4.json`): 1435 corpus docs / 28 `All`
  components → composed-mode reach 0 (no defaults by construction), per-element WPT reach 0, legacy fixtures 3 rows (all in
  `all-then-color.json`). **No gate cell moves; no watchlist addition.**
- The off-gate-list half of M1: `hunk-for-orchestrator-1.patch` adds the fixture to `tools/visual/gate-fixtures.txt` (§4).

Verification (all executed, this pass): CSS truth 4/4 green and M1/M2/M3/M4 each caught; the real harness 4/4 green with
seam-4 applied (under the lock), 2 violations at HEAD; seam-4's own pin green patched and RED on HEAD's source; the order
premise re-run 12/12 with its mutation; seam file sha IDENTICAL after restore. The other rows' first-cut numbers stand.

Should-fix items NOT addressed in this pass (code unchanged; recorded so they are not lost):
- **S1** the "IR order IS cascade order" premise has two known exceptions — the extractor's in-place re-assignment keeps a key
  in its FIRST declaration's slot (`.a{all:initial}.b{color:red}.c{all:initial}` → `[All, Color red]`, F1 paints red where CSS
  gives black), and UA bakes appended after `all` (seam-3 fixes the link bake only; the table-cell padding bake is the same
  shape). Corpus-neutral (0 carriers, skeptic repro 15/21). The general fix (delete + re-insert on re-assignment; UA bakes
  before the author bag) is extractor-seam work for L5 / a later wave; the KDoc wording in `AllReset.kt` overclaims it.
- **S2** natives do not model non-inherited INITIAL values that differ from their defaults (above all `display: initial` =
  inline) nor REVERT's UA values, and do not log it. Corpus-neutral; the corrected fixture no longer depends on it.
- **N1** no pin exercises the seam-1 / seam-2 wiring itself (the unit tests re-implement the call order); device gate only.

STATUS: COMPLETE
