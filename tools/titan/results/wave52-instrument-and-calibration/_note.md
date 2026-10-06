# wave52 L12 · instrument-and-calibration — lane note

Lane started fresh on 2026-10-05: the results dir did not exist and the owned files matched HEAD 7d9c22a7, so there was no earlier L12 work to resume.
Plan: `tools/titan/results/wave52-plan/PLAN.md` §2 L12 (corrections C1/C2/C3/C9 from §9 applied). Briefs: `absence-only-denominator.md` §4 A/B and
`page-padding-overrun.md` §4 Fix B, plus BACKLOG "The erased-reference body background". The lane made no runtime changes. It used no device, no
emulator and no simulator. Headless Chromium was used only for the ref re-freeze and the A/B checks.
Of the inputs, `wave52-open` was only read, never written; its manifests' sha1 values were re-checked after the calibration and all are unchanged.

## What changed and why

**Part A: scorer stamp (`tools/titan/inject-wpt-block.mjs`).**
- DECISION A: `ABSENCE_ONLY_STAMP = 'absence-only'`, `isAbsenceOnly(diff)` and `applyAbsenceOnlyGate(diffsByPlatform)`.
  - A diff is stamped when `wptPass === true && semanticPresence.bCoveragePct < WPT_PRESENCE_REF_MIN_PCT`. The stamp sets `wptPass: null`.
  - The gate covers all three platforms. It never overwrites a `scoreExcluded` stamp that is already there.
  - It is called after the font-parity gate, as `isNa ? [] :`. It records `absenceOnlyExcluded` for each test, and `scoreEligible` is unchanged.
- DECISION B: three triage fields on every diff. Scoring is UNCHANGED, and the exit-7 escalation was declined.
  - `captureUniform` and `refUniform` (`uniformColour`) are read from the RAW decoded bitmaps before `padToCanvas`.
  - `blankCaptureVsInkedRef` (`computeBlankCaptureVsInkedRef`) is stamped next to `presenceFailed` on both diff paths.
- Summary line: the summary line now ends with `absence-only=<n> blank-captures=<n>`, and the log lists every cell of both classes by name.
- One extra hunk, not in the brief: the per-test native-presence check in `assertPlatformColumns` still anchors on a web-ref stamped `'absence-only'`.
  - Without this, the 7 stamped tests would silently drop out of the "native column delivered" check.
  - The stamp is a verdict on a capture that WAS delivered, so it should not remove the test from that check.
- `score-gate.mjs` was NOT edited (C2). `score-gate.test.mjs` gained pins for the CURRENT rule:
  - a cell stamped now prints as UNMEASURED NOW;
  - a cell whose stamp is removed prints as NEWLY MEASURED, never LOST;
  - plus a replay over the committed digest.
- README "The scorer idiom" gained one paragraph covering the string stamps, the absence-only rule and the triage fields. `canvasBoundary` now names the new rev.

**Part B: reference calibration (`tools/titan/capture-browser-ref.mjs`).** One `CANVAS_REV` bump, to `white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin`.
- (1) ROOTBG: wave-50 B10's patch. The canvas `background` now lives on `:where(html)` only.
  - On the body it painted a white box (CSS 2.1 Appendix E step 3) over `z-index:-1` children and over author root canvases.
- (2) UAMARGIN: page-padding Fix B. When the TEST declares a body margin and the REF does not, the ref gets `UA_BODY_CSS`.
  - `UA_BODY_CSS` = `:where(body) { margin: 8px; display: block; min-height: auto; }`, the UA html.css values.
  - It is a SEPARATE second style tag, injected after `canvasFrameCss()`. It is never part of the shared sheet, so the bake paths and every other ref are unaffected.
  - The predicate is static: `bodyDeclaresMargin(html, linkedCss)` / `pageDeclaresBodyMargin` / `uaBodyMarginFor` read the inline `<style>`, `<body style>` and linked local sheets.
  - DEVIATION from the plan text, with the reason:
    - The plan said "drop the body margin + `min-height: calc(100vh - 16px)`". Under the frame's `flow-root` that would stack the UA 8 px on top of a first child's margin.
    - `s-11-1-1b-005-ref`'s `<p>` would then land at y=24. Unframed Chrome collapses the margins and draws it at y=16.
    - So the variant restores the full UA body box instead (`display: block`, `min-height: auto`).
    - The PNG still stays 390×600: the four masking refs were re-frozen at exactly 390×600, with the green at (24,24)-(123,123). This is pinned.
    - The option is named `uaBodyMargin`, not the plan's `testDeclaresBodyMargin`. It is true only when the test declares a margin AND the ref does not. When both declare, the ref's own rule already decides.
- `renderOne` was split: the render now lives in the exported `renderRefPng`, which the A/B scripts call. When the variant fires, the CLI log line says `(ua body margin: …)`.
- `inject-wpt-block.mjs` `LIVE_CANVAS_REV` was bumped. The old rev was added to `KNOWN_STALE_CANVAS_REVS`, so the shell scripts' literal `…/white-black-ink-font-lh` resolves to the new tree.
  - The calibration logs show the normaliser line doing this.

## Census (scripts committed here; outputs beside them)
- `absence-only-census.mjs` (on wave51-fix, using the shipped functions):
  - Agrees with the plan on most counts:
    - 13 uniform refs;
    - **19 absence-only cells / 7 tests (web 7 / iOS 6 / Android 6)**, identical to the plan's census name for name;
    - 73 uniform captures;
    - totals web 1215/1379 → 1208/1372, iOS 1089/1369 → 1083/1363, Android 1080/1369 → 1074/1363, exactly the brief's §6.
  - **blankCaptureVsInkedRef is 39 (33 scored + 6 excluded, 16 tests), not 36.**
    - The brief's literal predicate (uniform capture ∧ ref ≥ 0.02) gives 45.
    - The plan's 36 used an unstated "ref not uniform" rule, which also dropped `overlay-transition-backdrop` ×3 (a white capture against a uniform GREEN ref, a real blank).
    - The shipped rule excludes only a capture equal to the ref's own uniform fill (olive and green full-bleed agreement, 6 cells).
    - The scored population is the same 33 either way.
    - Under the re-frozen refs the count is 45: the 39 plus `calc-in-media-queries-001/002` ×3, a red fallback capture against the now-green ref.
  - Digest for the pins: `absence-only-digest.wave51-fix.json`, every cell with ref ink < 1 %.
- `body-margin-census.mjs` (shipped predicate): 150 tests declare a body margin and 149 refs do. That gives 19 mismatched pairs, and **10 fire** — the same names as `body-margin-census.json`. Linked sheets add none.
- The 19 absence-only cells, by name, for the `corpus-v6-18.json` `_note`:
  - `css-backgrounds/animations/background-color-transparent-animation-in-body` ×3
  - `css-backgrounds/animations/invalidation/background-color-animation-with-zero-alpha` ×3
  - `css-cascade/scope-implicit-crash-print` ×3
  - `css-flexbox/abspos/flexbox_inline-abspos` ×3
  - `css-images/gradient-refcrash` ×3
  - `css-transforms/backface-visibility-hidden-006` ×3
  - `css-view-transitions/animation-name-ua-prefix` web

## Re-freeze (`refreeze-ab.mjs`, `refreeze-detail.mjs`, `refreeze-apply.mjs`, `refreeze-capture.log`)
- **A byte-exact re-render of the frozen corpus is impossible on this host.** I re-rendered all 1435 refs and compared them with the frozen PNGs. This used the OLD sheet, so nothing changed except the passage of time.
  - 1003 of 1435 differed. 994 of them differ ONLY by host drift: 1–45 glyph-antialias pixels, the same Chrome 151.0.7922.47 build.
  - So a lazy re-render after the bump would have moved ~1000 cells for no contract reason.
- The contract effect was therefore measured by a SAME-SESSION A/B (old sheet vs new contract, back to back). **23 refs move:**
  - Fix B fires on 10 pairs and moves 7. `anchor-position-circular`, `absolute-pos-box-inside-fixed-pos-box-with-changing-height` and `hypothetical-dynamic-change-003` are abspos/fixed and margin-insensitive, so they do not move.
  - The root-only background moves 16 more. 15 of them reveal paint the body box used to cover. The 16th is `display-contents-sharing-001`, whose ref renders the injected sheet's TEXT (see Findings).
- The plan's list vs measured:
  - B10's 6 erased refs and the 3 all-green refs all move.
  - `css-tags-paint-order(-with-entry)` do not move, as predicted.
  - **8 refs moved that the plan's list did not name.** They are the same mechanism; the plan's predictions anticipated `column-span-during-transition-doesnt-skip` only.
    - `css-display/display-contents-root-background`
    - `css-display/display-contents-sharing-001`
    - `css-position/position-{absolute,fixed}-root-element-{flex,grid}` (4 refs)
    - `filter-effects/backdrop-filter-root-element`
    - `css-view-transitions/column-span-during-transition-doesnt-skip`
  - I re-froze them too. Keeping erasing-contract bytes in a tree keyed by the new contract would make the next cache miss move them anyway.
  - **Revert recipe if the orchestrator rules otherwise:** copy `<old tree>/<sec>/<stem>.png` over both `<new tree>/<sec>/<stem>.png` and `<new tree>/chrome-151.0.7922.47/<sec>/<stem>.png`.
- How the new tree was built:
  - The 1412 non-moved refs' frozen bytes were cloned into the new versioned slot.
  - Then `node tools/titan/capture-browser-ref.mjs <all 1435>` ran: **rendered=23 cached=1412, fail 0**.
  - Each of the 23 is pixel-identical to the A/B's new arm, so the new render is deterministic within this session.
- **The two digests for the whole set:**
  - Old tree: 21327 files, digest `617cb0b649d686bcc89d4d90fc522e2dd15c7d96`, the same before and after, so it is untouched.
  - The 1412 OTHER refs: set digest `8c55b96b567d80a2c3f5d7f2aec3f9da1ba506da`, old → new identical.
- B10 skeptic re-runs, never before re-rasterised:
  - `b10-rerasterise.mjs` (B10's method, pixel compare) reproduces **28 of 42** negative-z refs differing.
  - `zneg-ab-probe.rerun.txt` (B10's script, as-is) reproduces its 8 rows exactly (033–037 and hanging-punctuation erased; css-tags ×2 unaffected).

## Calibration gate (`calib-rescore.sh` → `tools/titan/runs/wave52-calib`; `calib-hashcheck.mjs`; `score-gate.wave52-open.wave52-calib.txt`)
- Step 7 of `section-runner.sh` was re-run (inject only, `TITAN_REQUIRE_ALL_COLUMNS=1`) over a clone of `wave52-open` with the new instrument. 30/30 sections ran with rc 0.
- The inject logs sum to `absence-only=19 blank-captures=45`.
- **Capture-hash check:** 4305/4305 capture PNGs are byte-identical, so every move below is instrument-only.
- Cells: 4217 are bit-for-bit unchanged (ssim, wptPass and stamp). All 88 moved cells are attributed: 19 to the stamp, 69 to the 23 re-frozen refs. **0 are unattributed.**
- `score-gate wave52-open → wave52-calib`: **gained 13 · lost 8 · newly measured 0 · unmeasured-now 19**.
  - Totals: web 1211/1372 (88.27 %), iOS 1084/1363 (79.53 %), Android 1075/1363 (78.87 %).
- GAINED, each PNG-checked against the re-frozen ref (montages `replay-*.png` in this directory):
  - `initial-background-color` ×3: green on green. This is the plan's MED prediction, now measured.
  - 033 web: P 1.0000, and web paints the rules.
  - 034 ×3 and 035 ×3, ssim 0.987–0.992: all four pictures carry the same rules. These are instrument-only, NOT L2's or L10's flips. The residual is the right-frame overrun, which is L2's.
  - `backdrop-filter-root-element` ×3: a green canvas on all sides. This was NOT predicted.
- LOST, each PNG-checked and all honest:
  - `flex-gap-decorations-033` iOS/Android: 1.0 → 0.94. Pre-registered (C1).
  - **`display-contents-root-background` ×3: P ~1.0 → f 0.535. NOT pre-registered.**
    - The ref now shows Chrome's green root canvas. All three runtimes paint white.
    - The old P was a degenerate pass on an erased ref.
  - **`column-span-during-transition-doesnt-skip` ×3: P 0.9879 → f 0.9332.**
    - The ref now shows the pink region and the captures do not.
    - The plan already said these "stay P only with L1's F-B".
- Movers on re-frozen refs where the verdict holds:
  - masking paddingBox/contentBox 1d/1e ×12: 0.9585/0.9696 → 0.9997–1.0000. These go from degenerate to honest, as the brief predicted.
  - `s-11-1-1b-005` web: P 0.9947 → **0.9658**. It stays P, but its capture has NO black square, so it is a degenerate pass.
  - `s-11-1-1b-006` web: 0.9656 → 0.9923.
  - Unchanged: 036/037 rising to 0.991–0.996; `hanging-punctuation-block-bound-001` (now actionable: the web and Android captures wrap the 。); `calc-in-media-queries` ×6 (0.9997 f, colour veto, honest); `position-*-root-element-*` ×12 (f, ±0.03); `display-contents-sharing-001` ×3 (f).
- At-risk cells, unchanged at P 1.0:
  - `anchor-position-circular` ×3
  - `absolute-pos-box-inside-fixed-pos-box-with-changing-height` ×3
  - `hypothetical-dynamic-change-003` ×3
  - `css-tags-paint-order(-with-entry)` ×6 (P 0.9791)
- The ring-fenced blur test does not move under this lane.

## Predicted flips at the closing gate (L12's share, against wave51-fix)
- Confidence: HIGH as measured on identical capture bytes. Render lanes will change the captures.
- gained +13: listed above.
- lost −8, of which 2 were pre-registered:
  - 033 iOS/Android: pre-registered.
  - `display-contents-root-background` ×3: new and honest.
  - `column-span` ×3: L1's F-B may pay these back.
- unmeasured-now 19 (+2 from L6's adjudication A = 21).
- **The plan's §7 expectation "lost = exactly 2" is falsified by measurement.** L12 alone loses 8, or 5 if L1 F-B restores column-span. The §1 headline also moves:
  - the +6 from 034/035 and the +3 from backdrop-filter-root-element land here, not with L2 and L10;
  - −3 come from display-contents-root-background.
- The gate's summary line will read `absence-only=19 blank-captures=45` (not 36).
- Watchlist additions: `watchlist-additions.txt`, 21 lines, `watchlist-check` reports `unmatched 0`.

## Pins and mutations (`mutate.mjs` → `mutations.json`; every mutation executed, caught and restored byte-exact)
- Mutations M1–M7a target `inject-wpt-block.mjs` and are caught by the L12-A pins 1, 2, 3, 6, 6b, 7 and 9.
- M7 targets `score-gate.mjs` `isScored` and is caught by the score-gate L12-A pins.
- MB1–MB5 target `capture-browser-ref.mjs` and are caught by the L12-B pins rootbg, uamargin, direction, selector and source.
- The re-freeze pin (masking refs 390×600, green at (24,24)-(123,123)) reads the gitignored tree and skips when it is absent.
- Suites: capture-browser-ref + inject-wpt-block + score-gate pass 176/176. Consumers pass: safe-name 10, section-runner 19, wpt-not-applicable 265, view-transition-bake 140, post-load-extract 100, bidi-bake 50.

## Hand-offs delivered (no seam patches: L12 has no seams)
- **To the orchestrator** (two files no lane owns; both apply on HEAD with `git apply --check`; both must land in the SAME commit as the bump):
  - `hunk-for-orchestrator-1.patch` (`tools/titan/wpt-white-canvas.test.mjs`): rev literal ×2, plus the B10 frame literal and a no-background-on-body guard.
  - `hunk-for-orchestrator-2.patch` (`tools/titan/noto-pilot.test.mjs`): rev literal ×3.
  - Verified in a symlinked scratch tree. Without the hunks, 4 tests fail; with them, 0 fail that L12 caused.
  - One test still fails there — `swiftui: WPTCanvas is white…`, which counts 4 vs 3 canvas paints. It fails identically with and without the hunks. It comes from L2's in-flight `CaptureCanvas.swift`, not from L12.
- Docs to update at ship (orchestrator-owned): `.claude/skills/wave/SKILL.md` lines 93 and 201 name the old ref tree path, and so does the `section-runner.sh` comment at line 377. The scorer path needs no edit.
- **To L6:** the `NATIVE_FONT_PARITY_REFUSED_TESTS` re-add point moved from `:1308` to **`:1326`**.
- **To L1:** under the re-frozen ref, `column-span-during-transition-doesnt-skip` ×3 is f 0.9332. Its pink region is now in the ref, so F-B is what pays it back.
- **BACKLOG amendment (C9):** paste the plan's §2 L12 blockquote, but replace "36 rows / 15 tests — 33 scored, 3 already excluded" with:
  > 39 rows / 16 tests on the wave51-fix refs — 33 scored (all already failing via `presenceFailed`), 6 already excluded; a uniform capture equal to a uniform full-bleed ref (olive / green agreement) is excluded from the class by a third clause, a uniform capture of a different colour (overlay-transition-backdrop: white vs green) is kept; 45 under the re-frozen refs (+6 calc-in-media-queries red fallback vs green)
  - and append "`inject-wpt-block.mjs` also stamps `refUniform`".
  - The "erased-reference" bullet becomes EXECUTED, with the numbers above. Note in it that 8 more refs moved, by name.

## Findings (new, not fixed here)
- `css-display/display-contents-sharing-001`: its ref renders this pipeline's injected stylesheet TEXT, base64 fonts included. `* { display: contents }` un-hides `<style>`.
  - This was true under BOTH contracts. Its 3 cells (f 0.68) are measured against a polluted ref.
  - The fix would hide the injected tag (an id plus `display:none !important`), which is a new instrument item.
- Host glyph-raster drift: 994 of 1435 frozen refs no longer re-render pixel-identically on this host under an unchanged contract.
  - Any future CANVAS_REV bump must clone the unmoved refs, as this lane did, or the bump will attribute drift to the contract.

## Not verified
- No device gate was run. The calibration re-scored the opening gate's captures, so closing-gate numbers will add the render lanes' effects.
- `corpus-v6-18.json` is owed AFTER the closing gate. Its criterion is "+ per-cell absence-only exclusion (v6.18)", plus the 19 names above.
- The new ref tree holds only the 30 gate sections' 1435 refs. Any other section renders lazily under the new contract, with host drift, the first time it runs.
- `extract-fixture.test.mjs` has 20 failures from L5's in-flight `wave52-L5 *` tests. These are not L12's; the L12-relevant imports pass.

STATUS: COMPLETE
