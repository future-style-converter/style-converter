# Wave 52 · L9 inline-run-wall — lane note

Brief: `tools/titan/results/wave52-plan/inline-run-wall.md` §4 F1–F5; plan: `PLAN.md` §2 "L9", §3 (two seam rows), §4 step 5, §9.
Tree: `campaign/wave52` @ 7d9c22a7, shared with other lanes. No devices, no `test-all.sh`, no feeds.

## Resume record (this run, 2026-10-05)

The working tree already held an earlier run's uncommitted L9 edits, which were killed by a usage limit. There was no
`_note.md`, there were no logs, and `seam-1.patch` was the only file in this directory. I read every owned diff against
§2 and kept what was right:

- F1 on both rings, the F2 ring + fold predicate on Swift, F3 (`seam-1.patch`), F4 on both `GreedyLineBreaker` twins
  and in `PreBreakPipeline` (`clampLines`), F5 in `PreBreakPipeline`, the new `DrawnLineClamp.kt`, and every pin.
- The earlier test headers said "MUTATION PROOF (executed 2026-09-25 …)", but this directory held no record of any such
  run. I treated those claims as unverified, ran every mutation again (see "Mutations"), and rewrote each header to
  cite the run that was actually executed.

Added in this run:

- The clamp hidden-tail alignment on both twins.
- The Swift drawn-clamp reader.
- The Swift band helper.
- `seam-2.patch` (iOS).
- Two alignment pins, a band pin and a drawn-clamp pin.
- The census, the PNG replay and the mutation runner.
- `drop-F1.patch`.
- Two corrected F1 comments: they had claimed web paints the folded glyphs "on one line", but web wraps them.

## Fix pass (2026-10-05 19:00–19:27) — skeptic.md M1 + the cheap should-fix/nit items

**M1 (must-fix) — FIXED on both twins.** `clampLines` cut line N only at U+0020 (`lastIndex(of: " ")` /
`lastIndexOf(' ')`), so a last line whose soft wrap opportunity is not a U+0020 collapsed to `…` alone.
css-overflow-4 §4.2 hides content at soft wrap opportunities. The skeptic's case is block-ellipsis-030 box 1,
`123<U+1680>5 789` at 5ch: the ref is `123…`, today's iOS is `12…`, and the pre-fix F4 gave `…`.

The fix replaces the word loop with `clampHead`, a walk from the end of line N over code points. The first
boundary whose kept text fits beside the marker wins, so the cut is always the latest one. Opportunities:

| class | rule (UAX #14 approximation) | kept text |
|---|---|---|
| space separators | before a run of U+0020 / U+0009 / U+1680 / U+2000–U+2006 / U+2008–U+200A / U+205F / U+3000 (SP + BA Zs; NBSP U+00A0, U+2007, U+202F excluded as GL) | prefix, separators hidden (css-text-3 §4.1.3) |
| ZWSP | before U+200B (ZW) | prefix (the ZWSP is inkless) |
| soft hyphen | at an untaken U+00AD (`manual`) | prefix + the hyphenate character (§5.3), unless a literal hyphen already ends it |
| hyphen / dash | after `-`, U+2010, U+2013, U+2014 — not word-initial (LB20.1), not HY × NU | prefix including the dash |
| ID / SA (ideographs, Hangul, fullwidth forms; Thai/Lao/Myanmar/Khmer/Tai) | NOT modelled | **decline**: the hidden tail holds one → `clampLines` returns the lines untouched (the wave-51 behaviour: iOS `.lineLimit` tail truncation; Compose fired run with no baked marker), logged once (`PropertyTracker.logOnce` / guarded `Log.w`) |

The soft-hyphen class needed a second input. The fold's display lines have already dropped U+00AD, so
`clampLines(…, source:, hyphenChar:)` now receives the run's text, and `markedLine` restores the soft hyphens
of line N's whole words. Callers: `lines(text:clamp:)` on both twins and `PreBreakPipeline.preBreak`. A word
split across lines N-1/N keeps its display text, which is a stated loss.

**The census found a second unpredicted mover with the same root cause.** In block-ellipsis-028, line 2 is
exactly 63ch in a 63.1ch box, so the marker needs room. Both refs hide `cally` at the soft hyphen and paint
`uncharacteristi‐…`. The pre-fix F4 cut at the space and showed `…room…`. The fixed walk paints the ref's
line. That line runs past the 390px canvas, so the visible picture is today's capture.

**Should-fix / nits handled in this pass:**

- **S3 FIXED on both twins.** `pre-line` moved from the glyph-less preserving arm to the collapsing refusal
  (css-text-3 §4.1.1: `pre-line` = `white-space-collapse: preserve-breaks`). The wave-50 Kotlin comment that
  claimed otherwise was corrected. Pins: `testCollapsingWhiteSpaceKeywordRefusesEvenWhenGlyphless` (Swift)
  and `a collapsing white-space keyword refuses even on a glyph-less member` (Kotlin) now include PRE_LINE.
  Mutations W6/K6 bite. Corpus reach: 0 (the corpus's one PRE_LINE component,
  display-contents-text-inherit-002 `Two lines`, is glyph-bearing and not a fold member).
- **N3 FIXED.** `PreBreakPipeline.kt` `indexOf('­')` is now an escape, not an invisible literal.
  `InlineSpanRing.kt:681` now has a real `—` instead of the `—` text. The new code carries no invisible
  literal.
- **S1, S2, S4, S5, N1, N2, N4, N5** are recorded below (At risk / Not verified / Hand-offs). They are
  note-only, or orchestrator-owned.

**House-rule deviation, stated.** `GreedyLineBreaker.swift` is 566 lines (333 at HEAD) and
`GreedyLineBreaker.kt` is 463 (226 at HEAD), past the ~300 split threshold. Splitting the clamp into its own
file would create a path outside L9's own list, so the code stayed in the owned files. Proposed follow-up:
move `Clamp` / `clampLines` / `clampHead` / `markedLine` / `drawnClamp` into `BlockEllipsisClamp.{swift,kt}`
beside the breaker. The new `GreedyLineBreakerTests.swift` is 198 lines, within the 200-line rule for new files.

## What changed and why (owned files only)

| fix | Compose | iOS | spec |
|---|---|---|---|
| F1 `hanging-punctuation` inert member | `InlineSpanRing.kt` arm + stated loss `hanging-punctuation(<kw>)` | `InlineSpanRing.swift` twin | css-text-3 §8.3 (parse-only on both natives) |
| F2 hanging-whitespace ring | (since wave 50) | `InlineSpanRing.swift` `glyphless:` param + `WhiteSpace` (preserving only) / `BackgroundColor` arms + `Style.background`; `InlineRunFlow.swift` predicate; paint = `TypographyApplier.bandedRun` via seam-2 | css-text-3 §3/§4.1.1, css-backgrounds-3 §2.1 |
| F3 drawn marker on soft-wrapped clamps | `seam-1.patch`: `placeholderOverflow` → `Ellipsis` when `DrawnLineClamp.cap != null`; call site `unclippedLineWidths = !ruleBSoftWrap` | n/a | css-overflow-4 §5.1 + §4.2 |
| F4 clamp-aware greedy break | `GreedyLineBreaker.kt` `Clamp`/`clampLines`/`lines(clamp:)`, cut by `clampHead` at every modelled opportunity (fix pass: separators, ZWSP, soft hyphen via `markedLine(source)`, dashes; ID/SA decline); `PreBreakPipeline.preBreak(clampLines:)` trims a FIRED run (passes `source = text`); seam-1 passes `DrawnLineClamp.cap(properties)` | `GreedyLineBreaker.swift` twin (same `clampHead`/`markedLine`) + `drawnClamp(limit:properties:)` (excludes bare `max-lines`, discard-multicol-004); seam-2 threads `blockEllipsisClamp` through the four label call sites into the pre-break | css-overflow-4 §4.2 (hide at soft wrap opportunities until the marker fits); UAX #14 classes SP/BA/ZW/HY/B2; css-text-3 §5.3 |
| F4 alignment | `InlineSpanRing.kt` `alignment` → `walk(…) ?: clampedAlignment(…)` (hidden tail maps to the marker offset; end sentinel before the marker) | twin | css-overflow-4 §4.2 (marker = block's own inline) |
| F5 taken soft hyphen fires the pre-break | `PreBreakPipeline.kt` `tookSoftHyphenBreak`, gated `!dictionaryHyphenation` | n/a (iOS pre-breaks every run) | css-text-3 §5.3 `manual` |

- `DrawnLineClamp.kt` (`runtimes/compose/…/typography/wrapping/`) is a NEW file that is not on any lane's own list. This
  lane created it and no other lane touches it, so commit it with L9. It is the ONE reader of "fixed-count clamp whose
  marker is drawn", shared by F3 (seam) and F4 (pipeline).
- `TypographyApplier.swift`: the brief suggested dropping `.truncationMode(.tail)` when the marker is baked. I did not
  make that change, because SwiftUI's default truncation mode already IS `.tail`, so removing the modifier changes
  nothing. Instead the clamp leaves exactly N hard lines, which makes `.lineLimit(N)` inert. The only edit to this file
  is the `bandedRun` helper, placed here because `InlineSpanRing.swift` is deliberately SwiftUI-free.
- `LineClampUnderPreWave39Test` `:155`: rewritten, not deleted. It now asserts `Ellipsis` and carries a comment saying
  it used to pin the defect.
- `InlineRunFold.kt` needed no change.

## Seam patches and their verification

- **`seam-1.patch`** (Compose `ComponentRenderer.kt`): `git apply --check` passes on HEAD and needs no prior lane patch.
  Hunks are at :6848, :6977 and :7799, outside every other registered Compose hunk.
  - Verified 2026-10-05 17:20:58–17:21:02 in the shared tree under the lock `tools/titan/runs/wave52-lock/ComponentRenderer.kt`.
  - sha256 before = after = `c4369165…e03e652`, and the file was clean at both ends.
  - The six focused classes passed with the patch applied: 109 tests (`logs/seam1-shared-verify.log`).
  - On HEAD, without the patch, the 3 F3 pins fail. That is expected, and it is mutation M6 below.
- **`seam-2.patch`** (iOS `ComponentRenderer.swift`): `git apply --check` passes on HEAD and needs no prior lane patch.
  Hunks are at :3132, :3414, :3466, :4161, :4646, :4784 and :5514, none inside L11 :330, L3 :765/:2050/:4354, L7 :2098,
  L6 :4405–4480 or L8 :4969–4987.
  - Verified 17:23:11–17:24:30 under the lock `…/ComponentRenderer.swift`.
  - sha256 before = after = `d2afc70d…06c4ee63`, and the file was clean at both ends.
  - Seven classes passed on Catalyst: 145 tests (`logs/seam2-shared-verify.summary.log`).
- **Isolated copies** (HEAD + lane files + the seam patch): Compose `typography.* / core.renderer.* / scrolling.*` 747/747
  green; Catalyst 17 related classes 278/278 green.
- No stale locks were found.
- **Fix pass re-verification (the seams are unchanged; the lane files under them are not):**
  - seam-1 under the lock 19:24:03–19:24:10: sha256 before = after = `c4369165…e03e652`, clean after. Six classes
    113/113 (`InlineRunFold` 37, `InlineSpanRing` 18, `GreedyLineBreaker` 23, `PreBreakPipeline` 16,
    `PlaceholderOverflowMarker` 8, `LineClampUnderPreWave39` 11). Log: `logs/fixpass-seam1-shared-verify.log`.
  - seam-2 under the lock 19:25:32–19:26:52: sha256 before = after = `d2afc70d…06c4ee63`, clean after.
    `ComponentRenderer.swift` was recompiled. Six classes 121/121 on Catalyst. Log:
    `logs/fixpass-seam2-shared-verify.summary.log`.
  - Without seam-1, the shared tree shows exactly the 3 expected F3 failures (`logs/fixpass-compose-green.log`).
  - Isolated Catalyst copy (HEAD + lane + seam-2): 121/121 (`logs/fixpass-swift-iso-final.summary.log`).
  - `git apply --check` passes on HEAD for both patches. `drop-F1.patch` was regenerated by `make_drop_f1.py`;
    it is identical apart from hunk line numbers and passes `git apply --check`.

## Census (`census.py` → `census.json`, 1435 docs, wave51-fix; cells replay score-gate.mjs:50-51)

| fix | scope | cells today |
|---|---|---|
| F1 | 1 HangingPunctuation member (hanging-punctuation-inline-001) | — |
| F2 | 4 glyph-less members: 3 admitted by the arms alone (all in 032); first-letter-001's 26-prop member stays refused | — |
| F3/F4 | 35 clamp tests: 20 marker-drawn soft-wrapped, 12 `pre`/`nowrap`, 2 marker-suppressed (023/024), 1 bare `max-lines` (discard-multicol-004, excluded by both readers) | soft-wrapped: Android 19/20 P, iOS 19/20 P |
| F4 alignment | 12 of the 20 soft-wrapped hosts are fold hosts (styled spans ride the clamp) | — |
| F5 | 17 U+00AD tests: 12 `manual`, 2 initial-`manual`, 2 `none` (strip path, unchanged), 1 `auto` (gated out) | 9/17 Android P |

These numbers reproduce the brief's 1/3/34 (20+12+2)/17/9 exactly.

**Fix-pass census** (`census.py` → `census.json` summary keys `M1_*`; every earlier key is byte-identical):

- 4 drawn soft-wrapped clamp hosts hold a non-U+0020 opportunity. These are the only hosts the `clampHead`
  walk can treat differently from the pre-fix loop:
  - 028 (soft hyphen);
  - 030 box 1 (U+1680);
  - 032 boxes 1 and 2 (the hyphen in `left-aligned` / `right-aligned`). Line N + `…` fits there (26ch / 27ch
    in 29ch), so nothing is cut and nothing changes.
- 0 hosts decline (no ID/SA character in any clamp host).
- All other 16 drawn hosts carry only U+0020 opportunities. For those, the walk is provably the old loop:
  same candidate set, same order, same strip.

iOS at-risk is narrower than the brief's "33": the iOS pre-break skips the `pre`/`nowrap` hosts (`noWrap` /
`preservesSpaces`), `lineClampLimit` is nil for 023/024, and `drawnClamp` is nil for 004-multicol. So F4 reaches
19 passing iOS cells.

## Mutations (executed 2026-10-05; `mutate.py`, isolated copies, sha256 restore verified per run)

Compose (`mutations-compose.result.json`) — 8 of 8 proven:

| id | mutation | pins that failed |
|---|---|---|
| M1 | HangingPunctuation arm removed | both hanging tests |
| M2 | `tookSoftHyphen = false` | -012 pin and the `manual` half |
| M3 | dictionary gate removed | dictionary pin |
| M4 | fit loop disabled | 025, drops-words, custom-marker and the fired-run clamp pins |
| M5 | fired run left untrimmed | fired-run clamp pin |
| M6 | seam-1 Ellipsis arm → `declared` | 3 F3 pins |
| M7 | `DrawnLineClamp` consults all properties | max-lines and cap pins |
| M8 | `?: clampedAlignment` removed | both alignment pins |

Swift (`mutations-swift.result.json`) — 9 of 9 proven:

| id | mutation | pins that failed |
|---|---|---|
| S1 | HangingPunctuation arm removed | ring and fold pins |
| S2 | `guard glyphless` → false | glyphless, collapsing-keyword and 032 fold pins |
| S3 | `glyphless` default true | the default-relying glyph-member pin |
| S4 | fold predicate → false | 032 fold |
| S5 | `?? clampedAlignment` removed | both alignment pins |
| S6 | band write removed | band pin |
| S7 | drawnClamp guard removed | drawnClamp pin |
| S8 | fit loop off | 3 clamp pins |
| S9 | trim skipped | 5 clamp pins |

Fix pass (executed 2026-10-05 19:02–19:22, `mutate.py`, every restore sha256-verified):

- Compose ran in the shared tree, on this lane's own file only (`mutations-m1-compose.result.json`,
  `mutations-s3-compose.result.json`).
- Swift ran on the isolated Catalyst copy (`mutations-m1-swift.result.json`, `mutations-s3-swift.result.json`).
- 12 of 12 proven.

| id | mutation | pin that failed |
|---|---|---|
| K1 / W1 | separator set narrowed to U+0020 | the verbatim 030 pin (`123…` → `…`) |
| K2 / W2 | soft hyphens not restored (`markedLine` off) | the verbatim 028 pin (`…room…`) |
| K3 / W3 | ID/SA decline off | the decline pin |
| K4 / W4 | hyphen/dash break-after arm off | the dash/ZWSP pin |
| K5 / W5 | fit test forced true: M4/S8 RE-CUT, since the old fit loop no longer exists | 025 / drops / custom-marker / 030 / 028 (+ the fired-run pipeline pin on Compose) |
| K6 / W6 | `pre-line` re-admitted (S3) | the collapsing-keyword pin |

Run notes:

- K2's first cut (`if (true) return line`) broke the null smart cast and did not compile, so it was re-cut to
  `< Int.MAX_VALUE`.
- One K-run hit a transient foreign test-compile failure. The full spec was re-run green (the final result
  file).
- S9, M5 and M7 still match the source unchanged.
- A throwaway real-font probe on the isolated copy (deleted after the run) used `GreedyLineBreaker.measurer`
  over SF Mono / Menlo / Courier 16pt. All three give 030 → `["123…"]`, 029 → `["123…"]` and 028 line 2 →
  `…uncharacteristi‐…`. Summary: `logs/m1-swift-realfont-probe.summary.txt`.

**No unit pin** covers seam-1's call-site argument `unclippedLineWidths = !ruleBSoftWrap`, which replaced
`!wrapConfig.softWrap`. It is an argument inside the composable. The function-level pin (`the same run pre-broken
answers Visible`) covers the answer once that flag is passed. This is stated in the `PlaceholderOverflowMarkerTest`
header.

## PNG replay (`replay_sim.py` → `replay/*.png`, scored by `replay.mjs` → `replay.json`)

- **Recipe check:** the campaign's SSIM recipe (`fitToRefFrame` and `countOverflowInk` imported from
  inject-wpt-block.mjs; ssim.js `fast`) reproduces all 9 manifest cells EXACTLY. Those are the 3 tests × 3 platforms.
- **T4 block-ellipsis-025 (supported):**
  - iOS f 0.9495 → **0.9708**. Line 4 is the capture's own "…" alone, at the ref's x 16.
  - Android P 0.9607 → **0.9806**. This is a lower bound: line 3 is still clipped in the simulation, because the
    unclipped "us" glyphs of F4's Visible cannot be invented from pixels.
- **T1 hanging-punctuation-inline-001 (REFUTED as a flip):**
  - The fold makes the natives wrap like web (`字字字` / orange `字」`: 4em = 128px holds 4 ideographs, and LB13
    forbids a break before U+300D).
  - The simulation built from each native's own glyphs scores **android 0.9495 → 0.9454 (f)** and
    **ios 0.9756 → 0.9685 (P)**.
  - Control on web's capture: the stacked wrong shape scores 0.9942, web's own folded shape 0.9849, and the hung
    one-line shape 1.0000.
  - Conclusion: the ref HANGS the bracket, so the CSS-faithful non-hanging fold is pixel-farther from the ref than the
    stacked bug. F1 is still more correct (inherited orange instead of black, kinsoku instead of a block box), but it
    is not a flip.
- **Fix pass, T5 block-ellipsis-030 iOS.** The recipe check now covers 15 cells (5 tests × 3), all exact.
  - Predicted: box 1 gets box 2's own `123…` glyph rows (the identical string in the same label face), giving
    **0.9983 → 0.9989**.
  - Counterfactual: the pre-fix F4's `…` alone scores **0.9974**. That is the skeptic's M1 loss, quantified.
    It stayed P but was the wrong picture.
- **Fix pass, T6 block-ellipsis-028 iOS.**
  - The fixed line 2 runs off the 390px canvas, so the predicted picture is today's capture (0.9906).
  - Counterfactual: the pre-fix F4's `…room…`, using the 025 capture's own `…`, scores **0.9895**. That loss is
    now avoided.
  - Files: `replay/block-ellipsis-030.ios.{predicted,counterfactual-preFixF4}.png` and
    `replay/block-ellipsis-028.ios.counterfactual-preFixF4.png`.
- 032 has no pixel simulation: the iOS capture has no green glyphs to move. Web's folded capture (0.9834) is the proxy
  for the shape.

## Predicted flips (revised by the replay)

| cell | today | prediction | confidence |
|---|---|---|---|
| block-ellipsis-025 ios | f 0.9495 | P ≈ 0.97 | MED-HIGH (replay) |
| block-ellipsis-025 android | P 0.9607 | P ≈ 0.98 | picture-correctness, HIGH (replay lower bound) |
| hyphens-manual-inline-012 android | f 0.9419 | P | MED |
| block-ellipsis-032 android | f 0.9397 | thin P | MED-LOW |
| block-ellipsis-032 ios | P 0.9577 | ≈ 0.98 | MED |
| hanging-punctuation-inline-001 | android f 0.9495 / ios P 0.9756 | NO flip; ≈ −0.004 / −0.007 | — |
| block-ellipsis-030 ios (fix pass) | P 0.9983 (`12…`) | P ≈ 0.9989 (`123…`, the ref) | MED-HIGH (replay + real-font probe) |
| block-ellipsis-028 ios (fix pass) | P 0.9906 | unchanged 0.9906 (the pre-fix F4 would have cost −0.0011) | HIGH (visible region identical) |

Notes on those predictions:

- hyphens-manual-inline-012 android: the pre-break measures with the exact render style, so `Deoxy‐` / `ribonu‐` /
  `cleic` fit by construction even if 8ch holds only 7 Minikin glyphs.
- block-ellipsis-032 android: StaticLayout END-ellipsis keeps up to 28 of the 33 chars, so the "…" may land at col 28
  and leave 3 red band spaces visible. The ref has 0 red, but web also paints red and still passes at 0.9834.
- block-ellipsis-032 ios: boxes 1 and 2 are ref-shaped with a host-styled "…". Box 3 stays unjustified, because
  TextKit does not justify a hard-broken last line.
- hanging-punctuation-inline-001 is a correctness change gated behind the device A/B. **`drop-F1.patch`** removes F1
  exactly: it applies cleanly to the tree, and it was verified at Compose 745/745 and Catalyst 75/75 with it applied.
  The orchestrator applies it if the A/B confirms the drop.

P→P picture-correctness movers expected:

- 19 Android soft-wrapped clamp hosts gain their "…" (F3; StaticLayout ellipsizes at a character, the ref at a word —
  001's marker lands off-canvas, so it does not move).
- iOS 001 "room…" (word-level, matching the ref).
- iOS 004/005/006: the "…" is now host-styled (teal, 16px), as the ref draws it, instead of SwiftUI's span-styled
  purple "…".
- hyphens-manual-011/012/013 and manual-inline-011 android materialise their hyphen (F5).

## At risk / watch

- Android: 19 P soft-wrapped clamp cells. The lowest are 031 0.9779, 005/006 0.9789 and 009 0.9795.
- iOS: 19 P soft-wrapped clamp cells. The lowest are 004 0.9704 and 005/006 0.9700.
- F5 Android P carriers: manual-011/012/013, manual-inline-011 0.9739, block-ellipsis-014/-028.
- hanging-punctuation-inline-001 ios P 0.9756 is predicted to fall to ≈0.969 and stays P.
- iOS (N5, corrected): 030 ios P 0.9983 and 028 ios P 0.9906 are F4-reached cells that the first pass did not
  name. With the fix, both move toward the ref or stay put (see the flips table). Both are matched by plan
  watchlist line 156 (`css-overflow/line-clamp ios`).
- Android (S1, named): seam-1's call-site argument `unclippedLineWidths = !ruleBSoftWrap` flips every FIRED or
  B-RC7 clamp from `declared` (Clip) to Visible, not only the drawn-marker ones.
  - **block-ellipsis-023 and -024 android P 0.972** are named P→P movers. They are suppressed-marker clamps
    whose 34ch word fires rule B in a 32ch box. Today the word is clipped at the box edge; after the change it
    paints full width, as the ref does.
  - 029 and 030 box 2 (B-RC7 `123…`) change nothing visible.
  - The first pass's "every other run answers exactly as before" was wrong for these.
- Every one of these is already matched by `wave52-plan/watchlist.txt` lines 151–158.
- New F5 movers (all f today) are in `watchlist-additions.txt`: hyphenate-character-00x, hyphens-out-of-flow-001 and
  hyphens-span-001, android. `watchlist-check.mjs` → `unmatched 0`.

## Not verified

- No device was run. Every flip above is a prediction.
- The `.app` hash / `base.apk` sha1 device A/B owed for F2 (and now F1) is an orchestrator gate item. No lane may boot a
  device.
- StaticLayout END-ellipsis placement over the hanging pre-wrap spaces (032 android) is unverified.
- SwiftUI drawing `AttributedString.backgroundColor` inside a `Text + Text` concatenation is unverified. It is
  paint-neutral on the corpus, because 032's band collapses into the line break and the hidden tail.
- Android's character-level END ellipsis against the refs' word-level marker on the 19 soft-wrapped hosts is
  unmeasured.
- A non-empty author `block-ellipsis` string would still be drawn as "…". The corpus has none (census tally of
  `LineClamp` wires), and today's iOS truncation does the same.
- The seam-1 call-site threading has no unit pin (see "Mutations").
- **S2:** seam-2's call-site wiring also has no unit pin. That covers the four `blockEllipsisClamp:`
  arguments, the `clamp:` argument into the pre-break, and the `styledSpanText` band rebuild. They are
  exercised by compile only; the helpers they call are pinned.
- **Fix pass, unmodelled opportunities:** UAX #14 ID/SA opportunities, plus `/`, `?`, `!` and other
  class-pair breaks, are not modelled by the clamp walk. ID/SA DECLINE (logged once). Rarer pair classes would
  be hidden whole, a stated loss with 0 corpus hosts.
- Dictionary points (`hyphens: auto`) are not cut points for the clamp, and soft hyphens of a word split
  across lines N-1/N are lost. Both are stated losses with 0 corpus hosts.
- **N2:** these remain note-only, with no code breadcrumb:
  - an author `block-ellipsis: "<string>"` is drawn as `…`;
  - `max-lines` + `block-ellipsis: auto` longhands answer "no marker";
  - a fired F5 run bakes U+2010 even when `HyphenateCharacter` is on the wire;
  - the rule-B logcat breadcrumb names F4/F5 fires "unbreakable overflowing run".

  The corpus has none of the first three.
- **S4:** the F2 device A/B arms.
  - Arm A is the wave51-fix capture with its recorded `.app` hash, or the lane build minus F2. There is no
    `drop-F2.patch`: F2 spans the Swift ring, the fold predicate, the band helper and a seam-2 hunk.
  - Arm B is the lane build, with its installed `.app` hash recorded against the build it claims to test.
  - The same rule holds for F1, with `drop-F1.patch` as arm A.

## Hand-offs

- Delivered: none. Received: none. No other lane's file was edited (fix pass included).
- **For the orchestrator (S5): the PLAN §3 seam registry is missing these L9 hunks.**
  - Compose seam-1: `:6848` (the pre-break `clampLines` argument), besides the registered `:6977` / `:7799`.
  - iOS seam-2: `:3132`, `:3414`, `:3466`, `:4161`, `:4646`, besides `:4784` / `:5514`.
  - The skeptic applied every pair against every other lane's HEAD-applicable seam patch, in both orders, with
    no conflict.
- **For the orchestrator (N1): new files with no other owner, to commit with L9.**
  - `runtimes/compose/src/main/java/com/styleconverter/runtime/typography/wrapping/DrawnLineClamp.kt`
  - `runtimes/swiftui/Tests/StyleConverterRuntimeTests/GreedyLineBreakerTests.swift`
- N4: `watchlist-additions.txt` keeps `hyphens-out-of-flow-001` and `hyphens-span-001` android. F5 cannot reach
  them (no U+0020), so this is a harmless over-watch, left in. `watchlist-check.mjs` reports `unmatched 0` for
  both lists.
- Blocked-by (transient): at 17:05 the shared-tree test compile failed in `VerticalRunIntrinsicsTest.kt`, another lane's
  in-flight edit (`No value passed for parameter 'onDecline'`). It compiled clean on retry at 17:20, so nothing was
  blocked.
- For the BACKLOG (orchestrator-owned), queue 4 "Inline-run wall, corrected map":
  > (c) refuted half: "Compose never paints a block-ellipsis marker on any clamp in the corpus" — `placeholderOverflow` handed a bare `line-clamp` back `declared` = Clip. Wave 52 L9 F3 draws it on soft-wrapped runs, and F4 bakes it into pre-broken runs (both twins, css-overflow-4 §4.2 word-level placement on iOS). (a) `hanging-punctuation-inline-001`: the fold (F1) is CSS-correct for a non-hanging UA but the replay predicts a SCORE LOSS on both natives (the ref hangs the bracket). Real progress there needs `hanging-punctuation: last` implemented (measure the paragraph without the trailing closing bracket; Compose would need a wider Text constraint, iOS a fixed-size label), not a fold admission.

STATUS: COMPLETE
