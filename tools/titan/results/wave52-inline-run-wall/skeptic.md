# Wave 52 · L9 inline-run-wall — executed-repro skeptic (2026-10-05, 17:58–18:30)

Tree: shared `campaign/wave52` @ 7d9c22a7. Inputs read: PLAN.md "### L9 ·", §3, §5, §9; `_note.md`
(ends `STATUS: COMPLETE`); every file in this directory. All scratch work lived under the session
scratchpad `l9sk/` (purged in ~3 days; everything that matters is copied below).

## Verdict

**One must-fix.** The iOS F4 clamp trims only at U+0020, so a last kept line with no U+0020 cut
collapses to "…" alone. The corpus already has one such case: `css-overflow/line-clamp/block-ellipsis-030`
box 1. The ref paints `123…`. Today iOS paints `12…`. After the lane, iOS paints `…`. This is a new
wrong picture on a passing cell (030 ios P 0.9983), and the lane did not predict it.

Everything else checks out:
- the census reproduces;
- 109 + 117 pins are green with the seams applied;
- 6 Compose and 3 Swift mutations bite;
- every predicted picture is plausible;
- no foreign edit.

There are five should-fix items and five nits. The largest is that seam-1's call-site argument moves
two Android cells the note never lists (023/024).

## Executed repros

1. **Seam-1, Compose `ComponentRenderer.kt`**
   - `git apply --check` passes on HEAD.
   - Applied under the lock, 18:07:36–18:07:42.
   - sha256 before = after = `c4369165…e03e652`, and `git diff --quiet HEAD` passed after.
   - The six lane classes passed: InlineRunFold 37, InlineSpanRing 18, GreedyLineBreaker 19,
     PreBreakPipeline 16, PlaceholderOverflowMarker 8, LineClampUnderPreWave39 11 — **109/109**.
     This matches the note's 109.
   - Second run under the lock, 18:28:24–18:28:29, adding BlockFlowFidelityWave3Test (11),
     InlineSpanContentTest (7), WordBreakOpportunitiesTest (9) and SoftHyphenPolicyTest (9):
     **145/145**. The file was clean afterwards.
2. **Seam-2, iOS `ComponentRenderer.swift`**
   - `git apply --check` passes on HEAD.
   - Applied under the lock, 18:15:26–18:16:37.
   - sha256 before = after = `d2afc70d…06c4ee63`, clean afterwards.
   - The Catalyst log shows `SwiftCompile … ComponentRenderer.swift`.
   - Six classes passed: GreedyLineBreaker 7, IOSTextLane 14, InlineRunFlow 45, InlineSpanRing 25,
     SoftHyphenPolicy 15, WordBreakOpportunities 11 — **117/117**.
3. **Seam ordering.** I applied each seam patch to a scratch copy of the HEAD file, in both orders,
   against every other lane's seam patch that applies on HEAD. Those are:
   - L11 seam-1 and seam-2;
   - L6 seam-1, seam-3 and seam-4;
   - L3 seam-1 and seam-2;
   - L4 seam-1;
   - L8 seam-1 through seam-4.

   Every pair applies in both orders. L6 seam-2 does not apply on HEAD; that is its own lane's matter.
4. **Mutations, Compose.** I ran these in the shared tree with my own runner. It reads the JUnit XML
   rather than stdout, and each file was restored with sha256 before = after.

   | id | mutation | pins that failed |
   |---|---|---|
   | HEAD control (no seam) | seam-1 not applied | the 3 F3 pins (`a bare line-clamp…`, `…pre-R3 decision…`, `a clamped soft-wrapping run draws the UA marker`); confirms the lane's M6 claim and that the pins are not vacuous |
   | M2 re-run | `tookSoftHyphen = false` | `firesWhenASoftHyphenBreakWasTaken`, `composesWithTheSoftHyphenPolicy` |
   | M5 re-run | fired result built from untrimmed `lines` | `aFiredRunUnderADrawnMarkerClampBakesTheEllipsis` |
   | SK1 (own) | DrawnLineClamp ignores marker suppression | `DrawnLineClamp names the fixed cap…` and 2 more |
   | SK2 (own) | `hideTail` arm off | both InlineRunFold clamp-tail pins |
   | SK3 (own) | `clampLines` identity at N+1 lines | 3 GreedyLineBreaker clamp pins |

   After all six runs, the four lane files were byte-exact against my pre-run sha list.
5. **Mutations, Swift.** I ran these on an isolated copy (`Package.swift` + `runtimes/swiftui`, rsynced;
   the lane files were cmp-equal to the shared tree). Each file was restored with sha256 before = after.

   | id | result |
   |---|---|
   | baseline | 77/0 |
   | S2 re-run (`guard false`) | 3 failures, as the lane recorded |
   | S5 re-run (`?? clampedAlignment` removed) | both clamp-tail pins failed |
   | SKS1 (own: `!cfg.markerSuppressed` dropped) | `testDrawnClampOnlyForAFixedCountLineClampWithAMarker` failed |
6. **Own census.** I wrote it in node, independent of `census.py`. It joins cells through the
   manifest's `components` list (0 unjoined of 1435) and uses score-gate's `isScored`/`isPass` idiom.
   It reproduces the lane exactly:

   | fix | count |
   |---|---|
   | F1 | 1 member (`」`, `HangingPunctuation ["LAST"]`); 5 HangingPunctuation carriers overall |
   | F2 | 4 glyphless members, 3 admitted by the arms (all in 032); first-letter-001 refused |
   | F3/F4 | 35 tests: 20 drawn soft-wrapped (Android 19/20 P, iOS 19/20 P), 12 pre/nowrap, 2 suppressed (023/024), 1 bare max-lines (discard-multicol-004) |
   | F4 fold hosts | 12 |
   | F5 | 17 U+00AD tests (MANUAL 12, initial 2, NONE 2, AUTO 1), Android 9/17 P |

   It found two things the lane's census did not:
   - the seam-1 call-site movers (see should-fix S1);
   - no corpus wire carries a non-empty author `block-ellipsis` string, which confirms the note.
7. **The 030 repro.** I added a test file to the isolated copy only, then deleted it.
   - `GreedyLineBreaker.lines(text: "123\u{1680}5 789", maxWidth: 5ch, clamp: Clamp(lines: 1))` returns
     `["…"]`. That holds with the 19.2 px monospace measurer and with the real
     `GreedyLineBreaker.measurer(font: monospacedSystemFont(13))`.
   - The 029 control, `"123 5 789"` at 5.1ch, returns `["123…"]`.
8. **PNG review**, wave51-fix capture against frozen ref `9b5435e5…/white-black-ink-font-lh-imgpad-htmlpins`:
   - **025 iOS** (`super…docious…` on line 4) and **025 Android** (lines 3–4 clipped at the box edge, no
     marker): the ref is `…` alone on line 4. Both predicted pictures (`replay/*.png`) are plausible.
     On Android, F4's baked marker plus seam-1's Visible repaint line 3 past the box, as the ref does.
   - **032 Android**: the fold has landed, with no `…` and no red. Under F3, StaticLayout's END
     ellipsis keeps 28 of 33 characters, so 3, 2 and 6 red band spaces become non-trailing in boxes 1, 2
     and 3. The thin-P prediction is plausible but fragile, and the note states the risk.
   - **032 iOS**: one line, `This text is`. The fold plus clamp gives `This text is left-aligned…`.
     That is plausible for boxes 1–2; box 3 stays unjustified, as stated.
   - **hyphens-manual-inline-012 Android** (`Deoxyri/bonucle/ic`): the fired fold gives
     `Deoxy‐/ribonu‐/cleic`, which is exactly the iOS capture (P 0.9905). The flip is plausible, and MED
     may be conservative.
   - **hanging-punctuation-inline-001**: the ref hangs `」` on one line. The fold gives `字字字/字」`, which
     matches the web capture. The no-flip and small-loss call is plausible.
   - **016, 001, 012, 023, 028**: I opened these to check the at-risk reasoning (see S1).
9. **Ownership.**
   - Edits tagged "lane L9" appear only in L9's own files, plus the new `DrawnLineClamp.kt`.
   - The other lanes' edits in the tree touch no L9 file.
   - `TextStyleApplier.extractLineClampMarkerSuppressed`, `LineClampExtractor.markerSuppressed` and
     `WordSpacingApplier.kernedRun` all exist at HEAD, so no helper was edited.
10. **Rule checks.**
    - New files are within the limit: `DrawnLineClamp.kt` is 57 lines, `GreedyLineBreakerTests.swift` 143.
    - Code lines contain no test-name carve-out; the only names are stated-loss labels.
    - The backdrop-filter ring-fence is untouched.
    - `watchlist-check.mjs` reports `unmatched 0` for the plan watchlist and for
      `WATCH=watchlist-additions.txt` (7 cells).
    - `drop-F1.patch` applies with `git apply --check` to the current tree.

## Must-fix

**M1 — F4's `clampLines` cuts only at U+0020 (both twins; reached on iOS).**

- **Where:** `GreedyLineBreaker.swift` `clampLines` (`last.lastIndex(of: " ")`) and the Kotlin twin
  `lastIndexOf(' ')`.
- **Failure:** `css-overflow/line-clamp/block-ellipsis-030` box 1 is `123<U+1680>5 789` at 5ch with
  `line-clamp: 1`. U+1680 is a soft wrap opportunity (UAX #14 BA), and the ref is `123…`. The iOS
  greedy fold sees one word `123<U+1680>5`, so the clamp finds no space and bakes `…` alone.
  - Today it renders `12…` (TextKit tail truncation); after the lane it renders `…`.
  - The lane's own case of "no opportunity, hide the whole word" (025) is right only for a word that
    truly has none.
- **Executed:** repro 7.
- **Impact:** on a passing iOS cell (0.9983), the picture loses its digits. The cell is matched by
  watchlist line 156, but the note neither predicts nor names it.
- **Fix:** cut at every soft wrap opportunity the run has: the Zs separators (U+1680, U+2000–U+200A,
  U+205F, U+3000), U+200B, and break-after `-`/U+2010. Failing that, decline the bake (return nil, so
  `.lineLimit` tail truncation stays) when the last line holds a non-U+0020 opportunity. Pin the
  verbatim 030 wire to `["123…"]` on both twins.

## Should-fix

- **S1 — seam-1's call-site change has an unmeasured mover class.**
  - `unclippedLineWidths = !ruleBSoftWrap` also flips any FIRED or B-RC7 clamp from `declared` (Clip)
    to Visible, not only drawn-marker clamps.
  - My census found:
    - **023 and 024 Android P 0.972.** These are suppressed-marker clamps whose 34ch word fires rule B
      in a 32ch box. Today the word is clipped at the box edge (I opened the PNG); after the change it
      paints full width, as the ref does. The move is toward the ref and is watched by line 155.
    - **029 and 030 box 2** (`123…`). These are B-RC7 runs and change nothing visible.
  - The note says "every other run answers exactly as before", and its at-risk list names only the 19
    soft-wrapped hosts. The seam-1 header says these runs "keep Visible", but they were Clip.
  - Add 023/024 Android as named P→P movers.
- **S2 — seam-2's call-site wiring has no unit pin, and the note does not say so.**
  - The note says this for seam-1 only. Seam-2's four `blockEllipsisClamp:` call sites, the `clamp:`
    argument into the pre-break, and the `styledSpanText` band rebuild are exercised only by compile.
  - The `grep` of `runtimes/swiftui/Tests` finds no reference to `blockEllipsisClamp`, and finds
    `bandedRun` only in `InlineSpanRingTests` (the helper's own pin, not the seam's call).
- **S3 — the F2 Swift port admits `pre-line` with a wrong spec claim, and a pin locks it in.**
  - The ring says `pre-line` PRESERVES spaces. It does not: css-text-3 §4.1.1, `pre-line` =
    `white-space-collapse: preserve-breaks`, collapses them. The claim was inherited from the Kotlin
    wave-50 arm.
  - `testGlyphlessPreWrapMemberAdmitsItsPreservedSpacesAndBand` asserts `PRE_LINE` is admitted.
  - There is no corpus member, but the ring would paint 8 spaces where CSS paints at most 1.
  - Fix: move `pre-line` to the collapsing refusal on both twins.
- **S4 — the F2 device A/B has no exclude arm.**
  - F1 got `drop-F1.patch`; F2, which changes the fold decision on passing host 032 iOS, has no
    `drop-F2.patch`.
  - The note also gives the hash rule in short form only.
  - State the arms: A = wave51-fix with its `.app` hash, or a drop-F2 patch. B = the lane build, with
    its installed `.app` hash recorded against the build it claims to test.
- **S5 — the §3 registry is missing L9 hunks.**
  - Missing on Compose: seam-1 `:6848` (pre-break `clampLines`).
  - Missing on iOS: seam-2 `:3132`, `:3414`, `:3466`, `:4161`, `:4646`.
  - Repro 3 found no conflict in either order. The orchestrator should still record these hunks.

## Nits

- **N1 — new file outside the own list.** `DrawnLineClamp.kt` is not on L9's "own:" list. It is
  declared in the note and has no other owner; add it to the list.
- **N2 — silent fallthroughs live only in the note, not in code:**
  - an author `block-ellipsis: "<string>"` is drawn as `…`;
  - `max-lines` + `block-ellipsis: auto` longhands answer "no marker", because BlockEllipsis is never
    read (discard-multicol-002 carries BlockEllipsis without a cap, so nothing changes on the corpus);
  - a fired F5 run bakes U+2010 even when `HyphenateCharacter` is on the wire (iOS has the same stated
    limit);
  - the rule-B logcat breadcrumb labels F4 and F5 fires "unbreakable overflowing run".
- **N3 — invisible characters in source.** `PreBreakPipeline.kt:218` uses a literal invisible U+00AD
  in `indexOf('­')`; write `'­'`. `InlineSpanRing.kt:681` has a literal `—` escape inside a
  comment.
- **N4 — watchlist lines F5 cannot reach.** `watchlist-additions.txt` lists `hyphens-out-of-flow-001`
  and `hyphens-span-001` Android, but F5 cannot reach them: `high­way` has no U+0020, so
  PreBreakPipeline declines. This is a harmless over-watch.
- **N5 — the iOS at-risk tally is understated.** In the note, `030 ios` is not among the at-risk cells
  it names (see M1).

## The census script, key logic (node; reproducible from `tools/titan/runs/wave51-fix`)

```js
// join: per section, manifest result.components[] → key; scored = typeof ssim==='number' && !scoreExcluded; pass = wptPass===true
// F1: meta.runs child members whose properties include HangingPunctuation
// F2: run members with /^\s+$/u text, no children and no runs; armsOnly = types ⊆ {WhiteSpace, BackgroundColor} && ws ∈ {PRE, PRE_WRAP, PRE_LINE, BREAK_SPACES}
// F3/F4: components with own LineClamp or MaxLines; class = !lines ? maxLinesOnly : suppressed (no-ellipsis | "") ? suppressed
//        : inherited WhiteSpace ∈ {PRE, NOWRAP} ? preNowrap : drawnSoftWrapped
// extra: host text with no soft-wrap opportunity (B-RC7) or a word ≥ 20 chars (rule-B candidates)
// F5: components whose text contains U+00AD; hyphens mode inherited along slot.parent
```

## Re-verify (2026-10-05, 19:32–19:46) — M1 after the fix pass

Tree: shared `campaign/wave52` @ 7d9c22a7. Note still ends `STATUS: COMPLETE`. Scratch work lived under the
session scratchpad `l9rv/` (purged in ~3 days; the numbers that matter are copied here).

### Verdict

**M1 is FIXED on both twins. No regression was found. Nothing is must-fix.**

- `clampHead` walks back from the end of line N over code points. It cuts at the SP/BA space separators (U+1680
  included), at ZWSP, at an untaken U+00AD (restored by `markedLine`), and after `-`/U+2010/U+2013/U+2014. It
  declines on ID/SA.
- The verbatim 030 wire gives `["123…"]` on both twins, with real iOS fonts as well. A 2×18 023-case sweep shows
  that the fixed walk differs from the old U+0020-only loop only on the four hosts that hold another opportunity,
  and the Kotlin and Swift twins return byte-identical output.
- One new should-fix: a mutation survives in the `PreBreakPipeline` call site. Two nits.

### Executed checks

1. **Compose pins, shared tree, no seam** (JUnit XML 17:33:44Z), 94/94: GreedyLineBreakerTest 23,
   PreBreakPipelineTest 16, InlineRunFoldTest 37, InlineSpanRingTest 18. `clampHidesAtTheOghamSpaceMarkOfBlockEllipsis030`
   and `…028` are among them. The 030 pin reads the clamp off the verbatim wire through `DrawnLineClamp.cap`. Its
   text `123<U+1680>5 789` matches the code points in `wave51-fix/…/per-test-ir/…block-ellipsis-030.json` exactly.
2. **Seam-1 under the lock** (`wave52-lock/ComponentRenderer.kt`, 19:41:10–19:41:13).
   - `git apply --check` passes on HEAD.
   - sha256 before = after = `c4369165…e03e652`, `git diff --quiet HEAD` passes, and the lock was removed.
   - Six classes pass, 113/113: InlineRunFold 37, InlineSpanRing 18, GreedyLineBreaker 23, PreBreakPipeline 16,
     PlaceholderOverflowMarker 8, LineClampUnderPreWave39 11. `compileDebugKotlin` was executed.
   - Control without the seam: `19 tests completed, 3 failed`, and those 3 are exactly the F3 pins. So the seam
     run was real.
3. **Seam-2.**
   - `git apply --check` passes on HEAD.
   - Isolated copy (`git archive HEAD` + the 7 lane Swift files + seam-2), on Catalyst: 121/121.
   - Shared tree under the lock (`wave52-lock/ComponentRenderer.swift`, 19:42:51–19:43:51): `SwiftCompile …
     ComponentRenderer.swift` ran, and the same six classes passed 121/121. sha256 before = after =
     `d2afc70d…06c4ee63`, the file is clean against HEAD, and the lock was removed.
4. **The 030 repro, re-executed with real fonts** (a throwaway XCTest on the isolated copy, deleted afterwards).
   - Measurers: `GreedyLineBreaker.measurer(…, scriptFallback: true)` over monospacedSystemFont 13 and 16,
     Menlo 13 and Courier 13, plus the 19.2px mono measurer.
   - Result for every measurer: `lines(text: "123\u{1680}5 789", maxWidth: 5ch, clamp: 1)` = `["123…"]`. A
     reimplementation of the pre-fix U+0020-only loop gives `["…"]`. The 029 control gives `["123…"]`.
   - Why the old loop failed: at monospacedSystemFont 13, the width of `123<U+1680>5` is 38.0 against an
     available 40.18. The fold therefore keeps it as line 1, which has no U+0020, and the old loop had nothing to
     cut.
5. **Differential sweep (regression hunt).**
   - Input: all 22 non-empty drawn-host label texts in the corpus. The label is assembled from `meta.runs`, and a
     `<br>` becomes `\n`. Widths run from 3ch to 70ch in 0.25ch steps, with clamp ∈ {own, 1, 2, 3}. That is
     18 023 cases per measurer, run for 5 measurers.
   - Comparison: the new `clampLines` against the old loop.
   - The two differ ONLY on 028#0 (soft hyphen), 030#1 (U+1680), 032#1 and 032#2 (`left-` / `right-`).
   - At the corpus widths, only two hosts change:
     - 028 at 63.1ch: `…room uncharacteristi‐…` replaces `…room…`;
     - 030 at 5ch: `123…` replaces `…`.

     032 at 29ch is identical, because line N + `…` fits. The lane's claim that the walk is the old loop for
     U+0020-only text holds empirically.
6. **Twin parity.** The same sweep ran on both twins with a 10px-per-code-point measurer, so every width is
   exact in both Float and CGFloat:
   - Kotlin: the compiled classes, run as a `java` single-file program with kotlin-stdlib 2.4.10.
   - Swift: the isolated Catalyst copy.

   Both produce 18 023 lines. They are byte-identical: `diff` exits 0, and both sha256 values are
   `6c911289…ba201e9`.
7. **Mutations** (my own runner `rv_mutate.py`: an exact-once swap, then the original bytes are restored and the
   restore is sha256-verified each run).

   | id | where | mutation | result |
   |---|---|---|---|
   | W1r | Swift (isolated) | `isClampSeparator` narrowed to U+0020 | `testClampHidesAtTheOghamSpaceMarkOfBlockEllipsis030` FAILS (lane W1 reproduced) |
   | W2r | Swift (isolated) | `markedLine` always returns the display line | `…028` FAILS (lane W2 reproduced) |
   | SKW1 | Swift (isolated) | ZWSP arm off | `testClampHidesAfterAHyphenAndAtAZeroWidthSpace` FAILS |
   | SKW2 | Swift (isolated) | walk earliest-first instead of latest-first | `…028` (`any…`) and the decline control (`字字…`) FAIL, so "latest opportunity" is pinned |
   | K1r | Compose (shared, lane file) | `isClampSeparator` narrowed to U+0020 | the 030 pin FAILS (lane K1 reproduced) |
   | SKK2 | Compose (shared, lane file) | HY × NU guard off | the dash/ZWSP pin FAILS |
   | **SKK1** | Compose (shared, lane file) | `PreBreakPipeline`: `source = text` → `source = null` | **SURVIVES** (39/39 green) |

   Restores were exact for every run:
   - Swift `GreedyLineBreaker.swift` `f791a5f4…`, which is also the shared tree's sha;
   - Compose `GreedyLineBreaker.kt` `7b3414ba…` and `PreBreakPipeline.kt` `31b798ac…`, confirmed by
     `shasum -c` afterwards.
8. **Own census** (`rv_census.py`, independent of `census.py` and `census.json`).
   - It reproduces the lane exactly:
     - 35 clamp tests: 20 drawn soft-wrapped (24 hosts), 12 `pre`/`nowrap`, 2 suppressed, 1 bare `max-lines`;
     - iOS 19/20 P and Android 19/20 P;
     - the hosts F4 can reach that hold a non-U+0020 opportunity are 028#0 (U+00AD), 030#1 (U+1680), 032#1 and
       032#2 (`-`). That matches the lane's `M1_drawnClampHostsWithNonSpaceOpportunities`, cells included;
     - 0 ID/SA hosts, so 0 declines.
   - It also turned up one extra: block-ellipsis-012's `-` (`line-clamp`) sits in a child `<p>` of a clamp
     container that has no text of its own. `drawnClamp` and `DrawnLineClamp.cap` read the component's OWN
     `LineClamp`, so they return nil for that `<p>`, and F4 cannot reach it. The lane's count is correct.
   - The only unmodelled-class characters in drawn hosts are `,` `.` `:`. These are UAX #14 IS, and LB29 forbids
     a break before letters, so they are not opportunities. The note's "0 corpus hosts" for the unmodelled
     classes holds.
9. **PNGs.** I compared the wave51-fix captures with the frozen ref `9b5435e5…/white-black-ink-font-lh-imgpad-htmlpins`.
   - **030.**
     - The ref shows `123…` / `123…`.
     - iOS today shows `12…` / `123…`. The lane's replay shows `123…` / `123…`.
     - Box 2 holds the same string in the same face and box width, and it renders `123…` unclipped. That is the
       existence proof that the baked box-1 string fits its label. The prediction is plausible.
     - Android shows `123-5`, with the Ogham glyph visible. M1 reaches Android only if the run fires.
   - **028.** The ref is clipped by the 390px canvas at `…room unc`, and iOS today shows `…room uncha`. The fixed
     line 2 runs past the canvas, so the visible picture is unchanged. The pre-fix counterfactual (`…room…`) is
     visibly wrong. The prediction is plausible.
10. **Ownership and rules.**
    - Lane-marked edits are confined to L9's own list plus `DrawnLineClamp.kt` (N1, declared). The fix pass
      touched only `GreedyLineBreaker.{kt,swift}`, `PreBreakPipeline.kt`, `InlineSpanRing.{kt,swift}` and their
      pins. The `skeptic M1` hits in `BorderSideApplier.kt` belong to another lane (T2).
    - New files are within the limit: `GreedyLineBreakerTests.swift` is 198 lines, `DrawnLineClamp.kt` 57.
    - No code line carries a test-name carve-out (`0x1680` is a class member, not a carve-out).
    - The decline is logged once on both twins (`PropertyTracker.logOnce` / a guarded `Log.w`).
    - The backdrop-filter ring-fence is untouched.
    - `drop-F1.patch` still passes `git apply --check`.
    - `watchlist-check.mjs` reports `unmatched 0` for the plan list and for `watchlist-additions.txt`. Watchlist
      line 156 `css-overflow/line-clamp ios` covers 030 and 028.
    - The device-A/B hash sentence is present (note S4).

### New findings

- **should-fix — R1: `PreBreakPipeline.preBreak`'s `source = text` argument is unpinned.**
  - Mutation SKK1, which replaces it with `null`, survives. Every pin stays green: GreedyLineBreakerTest 23 and
    PreBreakPipelineTest 16.
  - The only fired-run clamp pin is 025, which has no soft hyphen.
  - Corpus reach today is 0. 028 Android does not fire: line 2 fits, no soft-hyphen break is taken, and there is
    no overlong word. So the render does not change, but the note's "Callers: … `PreBreakPipeline.preBreak`"
    wiring has no executable check.
  - Fix: add a PreBreakPipelineTest pin. Use a rule-B-fired run (one overlong word) under `clampLines = N`
    whose line N must hide at a soft hyphen, and assert the baked `…‐…`.
- **nit — R2: `markedLine`'s "first spelling wins" token map is per display string, not per occurrence.**
  - In `foo fo<U+00AD>o`, a plain `foo` on line N is restored as `fo<U+00AD>o`. The walk could then cut at a soft
    hyphen that this occurrence does not have.
  - Corpus reach is 0: 028 carries one U+00AD word.
- **nit — R3: two em-dash cases are not modelled.**
  - Between two U+2014 (B2 × B2, LB17) the walk treats the gap as an opportunity, which UAX #14 forbids.
  - The break BEFORE an em dash, which UAX #14 allows, is not modelled.
  - Corpus reach is 0.
- **Carried, already stated by the lane:** `GreedyLineBreaker.swift` is 566 lines and `.kt` 463, both past the
  ~300 split threshold. The proposed follow-up is a `BlockEllipsisClamp` split.
