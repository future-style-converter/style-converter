# inline-run-wall — wave-52 plan brief (family key `inline-run-wall`)

Evidence run: `tools/titan/runs/wave51-fix` (manifests written 2026-09-16, gate tree `campaign/applier-campaign` @ 5d9ed628).
Every cell below is `manifest.wpt.results[<test>].browserRef.diffs[<platform>-ref]`; every PNG named was opened.
Census beside this file: `inline-run-wall.census.json` (1435 per-test IR docs scanned).

**The corrected map in one paragraph.** BACKLOG queue 4(c) predicted "Android folds to one `Text`, `maxLines = 1`,
ellipsised" — the fold HALF is confirmed on device (green span and `text-align: justify` now paint on one line), the
"ellipsised" half is REFUTED: **Compose never paints a block-ellipsis marker on any clamp in the corpus**, because
`ComponentRenderer.placeholderOverflow` hands a bare `line-clamp` back `declared`, and `declared` is `TextOverflow.Clip`
when no `text-overflow` is on the wire. block-ellipsis-001 (Android P 0.9836) and -011/-025 show the same missing "…".
So the remaining 032 Android miss is a renderer/`scrolling` defect, not a `typography/inline` one. The iOS 032 column
is a fold refusal (`member-prop:WhiteSpace`, the Swift ring has no glyphless arm), passing on a visibly wrong render.

## 1. Queue items covered

- BACKLOG "Inline-run wall, corrected map" — (c) *"`block-ellipsis-032` — the hanging-whitespace ring LANDED … pending
  the gate"* and *"iOS was deliberately NOT changed … the Swift twin … is queued, not shipped"*; (a) *"the
  br-stacked-equivalent wall … needs a device A/B, never a blind lift"* (not lifted here; one new member-prop wall named
  instead: `HangingPunctuation`).
- BACKLOG "Corpus expansion" 10 — *"(e) `hyphens/hyphenate-character-005` natives f 0.9450/0.9474"* (classified in §10
  as NOT this lane's).

## 2. Target cells (cheapest first)

| # | cell (wave51-fix) | platform | ssim | visible defect in the capture | what the ref shows |
|---|---|---|---|---|---|
| T1 | css-text/hanging-punctuation/hanging-punctuation-inline-001.html | android | f 0.9495 | orange `字字字字` on one line (y≈135-165); the `」` painted as a SEPARATE BLACK line below (x≈15-28, y≈185-205) — the span member was stacked as its own block and lost the inherited orange | orange `字字字字」` on ONE line, `」` at x≈150 right after the 4th glyph, same rows as the blue ref line above it |
| T1′ | same test | ios | P 0.9756 | byte-alike to Android: black `」` stacked below on its own line | same |
| T2 | css-overflow/line-clamp/block-ellipsis-032.tentative.html | ios | P 0.9577 | all three 1px boxes contain ONLY `This text is` (box 2 right-aligned, ink x150-244); 0 green px, 0 red px, no "…"; box rows 129-145/161-177/193-209 (1px taller than ref) | `This text is left-aligned…` / `…right-aligned…` flush right / `This  text  is  justified…` spread; 815 green px, 0 red; "…" immediately after the green word; rows 129-144/160-176/192-207 |
| T3 | css-overflow/line-clamp/block-ellipsis-032.tentative.html | android | f 0.9397 | fold landed: `This text is left-aligned` (683 green px), box 2 flush right, box 3 justified across the width — but **no "…" in any box** and 0 red px; right border at col **249** vs ref 244 (`29ch` resolves 5px wider); box 2/3 rows 160-175/191-206 (1px short) | as above |
| T4 | css-overflow/line-clamp/block-ellipsis-025.html | ios | f 0.9495 | line 4 = `supercalifragilisticexpialidocious…` — the 34ch word PLUS the marker, overflowing the 32.5ch box | line 4 = `…` alone (the whole word displaced); line 3 = the word overflowing |
| T4′ | same test | android | P 0.9607 | 4 lines, NO marker at all; lines 3-4 CLIPPED at the box edge (`…expialidociou`) | line 3 overflows visibly past the box; line 4 = `…` |
| T5 | css-text/hyphens/hyphens-manual-inline-012.html | android | f 0.9419 | `DNA / means / Deoxyri / bonucle / ic / acid.` — the 16-glyph word broken every 7 glyphs, no hyphen glyph, soft hyphens ignored | `DNA / means / Deoxy- / ribonu- / cleic / acid.` (iOS P 0.9905 is ref-exact) |

Not targets (wrong family, §10): hyphenate-character-005 (both natives), hyphens-punctuation-001 android,
hyphens-auto-min-content ios 0.9849, hyphens-out-of-flow/-span (both natives, ≥0.91).

## 3. Mechanism, per platform (file:line under the gate tree)

**T1 hanging-punctuation-inline-001 (both natives).** IR: host `…inline-001__2` `meta.runs = [text "字字字字", child __2__0]`,
member `<span>` with the ONE property `HangingPunctuation=["LAST"]`.
- Compose: `typography/inline/InlineRunFold.kt:467-469` → `InlineSpanRing.admit(...)`; `InlineSpanRing.kt:197` `admit`
  has no `HangingPunctuation` arm → default refusal `member-prop:HangingPunctuation` → fold bails → the wave-32 stacked
  path renders the `」` as a block. The property is PARSE-ONLY on Compose (`typography/TypographyExtractor.kt:161`, the
  "CSS-Text-4 long tail (parse-only on Compose today)" list), so admitting it loses nothing the stacked path had.
- iOS: `typography/inline/InlineRunFlow.swift:725` (`isEmpty` false → glyph arm) → `:751` `InlineSpanRing.admit` →
  `InlineSpanRing.swift:258` `return .refused("member-prop:\(prop.type)")`. Parse-only there too
  (`typography/unsupported/UnsupportedRubyEmphasisExtractor.swift:26`).
- Web (P 0.9849, capture = `字字字/字」` in orange on two lines): `engine/typography/HangingPunctuationApplier.ts`
  forwards the CSS property; Chromium does not implement `hanging-punctuation`, so the ref itself is the plain
  kinsoku result. UAX #14 LB13 (`× CL`) forbids the break before U+300D on Minikin and CoreText alike → the folded
  natives will take web's two-line shape.

**T2 block-ellipsis-032 iOS.** Members: `__N__0` (`Color`, `FontWeight` — admitted), `__N__1` text = 8×U+0020,
`WhiteSpace=PRE_WRAP`, `BackgroundColor=red`. `InlineRunFlow.swift:725` treats a whitespace-only text as a GLYPH member;
`InlineSpanRing.swift:158` `admit(tag:properties:hostProperties:)` has no `glyphless` parameter and no `WhiteSpace` /
`BackgroundColor` arm → `:258` refuses `member-prop:WhiteSpace` (pinned today by `InlineSpanRingTests.swift:173-179`).
The whole host bails to the stacked path; the `line-clamp: 1` cap (`scrolling/LineClampCap.swift`, `LineClampApplier.swift:13-14`)
keeps only the first stacked run `This text is`. The Kotlin twin already carries the arm: `InlineSpanRing.kt:201`
`glyphless: Boolean = false`, `:299-300` / `:318-319` (WhiteSpace preserving keywords, BackgroundColor).

**T3 block-ellipsis-032 Android (and every Compose clamp).** `core/renderer/ComponentRenderer.kt:6327` `maxLines =
placeholderMaxLines(properties)` (→ `TextStyleApplier.extractMaxLines` :1445, reads `LineClamp` first) and
`:6973-6981` `effectiveOverflow = placeholderOverflow(properties, effectiveMaxLines, textOverflow, unclippedLineWidths =
!wrapConfig.softWrap)`. In `placeholderOverflow` (:7760-7804): marker-suppressed → Clip (:7795-7797); explicit
`TextOverflow` → declared (:7800); clamp present → `if (unclippedLineWidths) Visible else declared` (:7801-7803).
`declared` comes from `TextStyleApplier.extractTextOverflow` :1574 `?: return TextOverflow.Clip`. So a bare
`line-clamp: N` renders `Text(maxLines = N, overflow = Clip)` (:7659-7660) — css-overflow-4 §5.1 says the omitted
`<'block-ellipsis'>` component is `auto`, i.e. a UA marker MUST be drawn. The config-driven path already knows this
(`typography/LineClampApplier.kt:95` `hasClamp || explicitEllipsis -> TextOverflow.Ellipsis`); the placeholder path never
inherited it. The wave-39 banner (:6371-6392) explains why `Ellipsis` is a LANDMINE on the `softWrap = false` path
(`finalMaxLines` collapses to 1) — which is why the fix below is gated on `ruleBSoftWrap` (:6868), not on
`wrapConfig.softWrap`. Fold path for 032: `InlineRunFold.appendCollapsed` (:275-285) keeps the 8 spaces verbatim (the
preceding run ends in `d`), `PreBreakPipeline.preBreak` (:156-161) declines (no unbreakable overflow) → Minikin
soft-wraps `…left-aligned        Clamped` at 29ch; trailing spaces hang; `Clamped` lands on line 2 and is clipped.
Red = 0 px today because the `SpanStyle.background` (`InlineSpanContent.kt:156`) covers only hanging trailing spaces.

**T4 block-ellipsis-025.** iOS: `Renderer/ComponentRenderer.swift:4744-4799` pre-breaks every wrappable run with
`GreedyLineBreaker.lines` (no clamp awareness — `GreedyLineBreaker.swift` has no `lineLimit`/clamp symbol), flags the
34ch word `overlong` (:4793) → `wptOverlongPreBrokenRun` (:4890-4891) → `.fixedSize(horizontal: true)` (:5076-5078) so the
label's width is its widest line; `.lineLimit(textConfig.lineClampLimit)` (:5071, via `LineClampCap.leafLineLimit` :884)
+ `truncationMode(.tail)` (`LineClampApplier.swift:14` → `TypographyApplier.swift:256`) then lets SwiftUI append "…"
AFTER the full word because the intrinsic width has room. css-overflow-4 §4.2 (block-ellipsis) requires the marker to
FIT the line and content to be hidden at soft wrap opportunities to make room — the test's assert: the whole word is
displaced, leaving `…`. Android: same overlong pre-break (`PreBreakPipeline.kt:163-165` fires, `ruleBSoftWrap = false`
:6868) → `placeholderOverflow` returns `declared` = Clip → no marker AND the overlong lines are clipped at the box edge
(the ref paints them past it).

**T5 hyphens-manual-inline-012 Android.** Fold engages (member `Hyphens: MANUAL` adopted). `PreBreakPipeline.preBreak`
(:154-165) computes `GreedyLineBreaker.lines` — with `WordBreakOpportunities` it yields `Deoxy-` / `ribonu-` / `cleic`
(:105-133, hyphenChar materialised) — but returns IDENTITY unless `hasUnbreakableOverflowingLine` (:156); nothing
overflows, so the raw string with U+00AD goes to Minikin with `softWrap = true`. `SoftHyphenPolicy.kt` banner: Compose's
default `TextStyle.hyphens = Hyphens.None` makes Minikin "ignore U+00AD outright" → the 16-glyph word is desperate-broken
at the constraint edge with no hyphen glyph. Same shape in the passing siblings: hyphens-manual-inline-011 Android
P 0.9739 paints `Deoxyribo / nucleic` with NO hyphen; hyphens-manual-012 (non-inline) Android P 0.9811 likewise.
iOS pre-breaks EVERY run (:4769) so it materialises the hyphen and is ref-exact.

## 4. Proposed fixes (ranked cheapest first)

**F1 (T1, both natives, effort S).** Admit `HangingPunctuation` as a paragraph-policy/inert member property in both
rings: Kotlin `InlineSpanRing.admit` (`InlineSpanRing.kt:197ff`, a `"HangingPunctuation" -> continue` arm beside
`"Hyphens"`), Swift `InlineSpanRing.swift:158ff` (`case "HangingPunctuation": continue`). CSS-correct because
css-text-3 §8.3 makes the property a LINE-END behaviour of the inline's glyph, and neither native implements it (parse-only
on both), so the stacked block the refusal produces is strictly less faithful than a folded line that merely does not
hang. Report the loss through the ring's `statedLossTypes` so it is not silent.

**F2 (T2, iOS, effort S–M).** Port the glyphless ring: `InlineRunFlow.classify` computes `glyphless = !hasNested &&
!(text ?? "").isEmpty && text.allSatisfy(\.isWhitespace)` (twin of `InlineRunFold.kt:467`) and passes it to
`InlineSpanRing.admit(tag:properties:hostProperties:glyphless:)`; new arms: `WhiteSpace` admitted only for preserving
keywords (`PRE`, `PRE_WRAP`, `BREAK_SPACES`) when glyphless (twin of `.kt:299-316`), `BackgroundColor` → `Style.background`
when glyphless (twin of `.kt:318-330`). Paint: in `styledSpanText` (`ComponentRenderer.swift:5458ff`) attach the band
through an `AttributedString` `backgroundColor` run for the segment — on 032 the range collapses to the pre-break's `\n`
(alignment op set `InlineSpanRing.swift:479-486`: first space → `\n`, the rest deleted), so nothing is painted and no
red can appear. This CHANGES A FOLD DECISION ON A PASSING HOST (0.9577); the standing rule forbids a blind lift, so
stage it as the device A/B queue 4(a) already prescribes — the render is visibly wrong and web's folded shape scores
0.9834. css-text-3 §4.1.2 (`pre-wrap` preserves the spaces) and css-backgrounds-3 §2.1 (the inline's own band).

**F3 (T3 + every Compose clamp, effort S code / L blast).** In `placeholderOverflow` (:7801-7803) return
`TextOverflow.Ellipsis` instead of `declared` when a fixed-count `LineClamp` is present, the marker is not suppressed and
the run is soft-wrapped; thread `unclippedLineWidths = !ruleBSoftWrap` (not `!wrapConfig.softWrap`) from :6980 so a
rule-B pre-broken run (025) keeps `Visible` and never trips the `finalMaxLines` landmine. css-overflow-4 §5.1
(`line-clamp: <n>` ⇒ `block-ellipsis: auto`) + §4.2 (`auto` = the UA ellipsis string). Honest caveat carried over from
B9: StaticLayout END-ellipsis over the hanging 8-space run decides whether the marker lands at col 25 (ref) or col 28;
either way the ref's "…" ink (missing in all three boxes today) appears.

**F4 (T4 both natives, effort M).** Make the greedy pre-break clamp-aware on both twins: `GreedyLineBreaker.lines(…,
clamp: (lines: N, marker: "…"))` — after folding, keep lines `1…N`; on line N, while `measure(line + marker) > maxWidth`
drop the last word (the soft wrap opportunity, css-overflow-4 §4.2); if nothing remains the line is the marker alone
(025's ref). Swift seam: `ComponentRenderer.swift:4769` passes `textConfig.lineClampLimit` and the label drops
`.truncationMode(.tail)` when the marker is baked (`TypographyApplier.swift:256`); Compose seam: `PreBreakPipeline`
fires whenever the clamp trimmed the text, caller keeps `ruleBSoftWrap = false` + `Visible`. This also fixes the
Compose pre-broken-clamp arm F3 cannot reach (025 Android's clipped lines, no marker).

**F5 (T5 Android, effort M, owner `typography/wrapping/`).** `PreBreakPipeline.preBreak` gains a second trigger: fire
when `lines` differ from `text` by a TAKEN soft-hyphen (a materialised `hyphenChar` at a line end) and
`!dictionaryHyphenation` — css-text-3 §5.3 `manual`: U+00AD IS an opportunity and the UA paints the hyphenate
character when it breaks there; Minikin under `Hyphens.None` cannot. The caller already pairs `fired` with `softWrap =
false` (:6868). Keep `hyphens: auto` runs out (their opportunities belong to Minikin's dictionary, :6300-6312).

## 5. Blast radius — census recipe + at-risk cells

Grep shapes over `tools/titan/runs/<run>/sections/*/per-test-ir/*.json` (numbers in `inline-run-wall.census.json`):
- F1: `meta.runs[].child` → member `properties[].type == "HangingPunctuation"` → **1 member** in the corpus
  (this test). Nothing else moves.
- F2: run member with `text.trim() == ""`, no children/runs, props ⊆ {WhiteSpace(preserving), BackgroundColor} →
  **3 members, all in 032**; the 4th glyphless member (`css-pseudo/first-letter-001__1__0`, 26 props) and 031's PRE
  glyph member `walked into the room` (web P 0.9993 · ios P 0.9843 · android P 0.9779) stay refused.
- F3: `LineClamp.data.type == "lines"` → **34 tests**: 20 soft-wrapped marker-drawn (F3 moves these; **19 pass on
  Android today**, lowest 004/005/006 at 0.978-0.980, 031 0.9779, 009 0.9795), 12 `PRE/NOWRAP` hosts (013/014/015/017/026,
  line-clamp-001…007 — unchanged, `Visible`), 2 marker-suppressed (023/024 — unchanged, Clip). Rule-B pre-broken hosts
  among the 20 (025 for sure) are gated out by `!ruleBSoftWrap`.
- F4: the same 34 on iOS (**33 pass**, lowest 025 f 0.9495, 032 P 0.9577, discard-multicol-001/002/004 0.9606-0.9613)
  and the pre-broken subset on Android.
- F5: any component `text` containing U+00AD → **17 tests, 9 Android-passing** (manual-011/012/013 0.981-0.982,
  manual-inline-011 0.9739, none-011 0.9717 (strip path — unchanged), none-shy-on-2nd-line-001 0.998 (unchanged),
  auto-control 0.9623 (dictionary — gated out), block-ellipsis-014 0.973 / -028 0.9847).

## 6. Predicted flips

| cell | today | prediction | confidence | falsified if |
|---|---|---|---|---|
| hanging-punctuation-inline-001 android | f 0.9495 | P ≈ 0.98 (web's two-line shape) | medium-high | Minikin breaks BEFORE `」` (LB13 violated) or the fold still bails (a second `member-prop:` in logcat) |
| hanging-punctuation-inline-001 ios | P 0.9756 | stays P, ↑ | medium | CoreText keeps `」` on line 1 overflowing — still P |
| block-ellipsis-032 ios | P 0.9577 | P ≈ 0.98 (`…left-aligned…` ref-exact in boxes 1-2, box 3 unjustified) | medium | `InlineSpanRing.alignment` returns nil (green lost, still P) or SwiftUI paints "…" on a second line |
| block-ellipsis-032 android | f 0.9397 | P, thin (0.95-0.97) | medium-low | StaticLayout puts "…" past col 28 or re-wraps the 8-space run; the +5px `29ch` box is a fixed small penalty |
| block-ellipsis-025 ios | f 0.9495 | P (line 4 = `…`) | medium | SwiftUI truncation double-marks; `fixedSize(horizontal:)` still widens the label past 32.5ch (bounded — the ref overflows too) |
| hyphens-manual-inline-012 android | f 0.9419 | P ≈ 0.98 (iOS shape) | medium | the 8ch box fits only 7 Minikin glyphs (the `ch` divergence in §9) so `cleic acid.` re-breaks differently |
| 19 passing Android clamp cells (F3) | P | stay P, most ↑ | medium-high | any `pre`-less clamp host is rule-B pre-broken without the gate → collapses to 1 line (the wave-39 landmine) |

## 7. Pins a builder must write (and how each fails by mutation)

- Compose `InlineRunFoldTest`: *hanging-punctuation-inline-001 verbatim wire folds to `字字字字」` with one plain
  span* — fails if the `HangingPunctuation` arm is removed (`member-prop:HangingPunctuation` returns).
- Swift `InlineRunFlowTests` + `InlineSpanRingTests`: the twin of the above; AND flip `InlineSpanRingTests.swift:173-179`
  (today asserts `member-prop:WhiteSpace`) into two cases — glyphless `PRE_WRAP`+red admits with `background = red`,
  a glyph member with the same props still refuses; a `NORMAL`/`NOWRAP` keyword refuses even when glyphless (twin of
  `InlineRunFoldTest.kt:749-815`). Each fails if the `glyphless` parameter is dropped or defaulted true.
- Compose `PlaceholderOverflowMarkerTest`: add *a bare `line-clamp: 1` soft-wrapped run answers `Ellipsis`* (fails if
  :7803 returns `declared`) and *the same run pre-broken (`unclippedLineWidths = true`) answers `Visible`* (fails if the
  gate reverts to `!wrapConfig.softWrap`); keep the R3 cases (:53/:66/:78/:97) green.
- `LineClampUnderPreWave39Test` *"a clamped soft-wrapping run keeps the frozen clipping"* (:155) MUST be rewritten, not
  deleted — it currently pins the defect.
- `GreedyLineBreakerTests` (Swift) / `GreedyLineBreakerTest` (Kotlin): 025 verbatim words at 32.5ch with `clamp 4` → 4
  lines, 4th == `"…"`; a 3-line clamp on a fitting paragraph → unchanged lines + `…` appended (fails if the marker is
  appended without the fit test).
- Compose `PreBreakPipelineTest`: -012 verbatim text at 8ch → `fired == true`, text `DNA\nmeans\nDeoxy-\nribonu-\ncleic\nacid.`
  (fails if the taken-hyphen trigger is removed); hyphens-auto-010 wire with `dictionaryHyphenation = true` → identity.

## 8. File ownership the lane needs

- `runtimes/compose/src/main/java/com/styleconverter/runtime/typography/inline/{InlineSpanRing,InlineRunFold}.kt`
- `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/inline/{InlineSpanRing,InlineRunFlow}.swift`
- Shared-renderer SEAMS (touch with the renderer owner): `runtimes/compose/…/core/renderer/ComponentRenderer.kt`
  :6973-6981 (`placeholderOverflow` call) and :7760-7804 (the function); `runtimes/swiftui/…/Renderer/ComponentRenderer.swift`
  :4744-4799 (pre-break closure), :5071 (`lineLimit`), :5458ff (`styledSpanText`).
- F4/F5 belong to `typography/wrapping/` (Kotlin `GreedyLineBreaker.kt`, `PreBreakPipeline.kt`) and `typography/GreedyLineBreaker.swift`
  + `typography/TypographyApplier.swift:256` — a second lane, or an explicit hand-off.
- Never touched: `scrolling/LineClampCap*` on either platform, `LineBoxCensus*`, the web runtime (P on every target).

## 9. Not verified

- No device was run: every flip is a prediction; StaticLayout's END-ellipsis arithmetic over a hanging pre-wrap space run
  and SwiftUI's truncation with a baked marker are device-only facts.
- The Android `29ch` box is 5px wider than ref/web/iOS (border col 249 vs 244) and the 8ch box in -012 fits only 7
  Minikin glyphs — a `ch` resolution vs Minikin-advance divergence I measured but did not trace (not this lane).
- No logcat/PropertyTracker breadcrumbs are in the run dir (`feed-*.log` only carry adb pushes), so the fold refusal
  reasons above are read from code paths, not from the device log.
- I did not confirm SwiftUI paints `AttributedString.backgroundColor` inside a `Text + Text` concatenation; on 032 the
  range collapses to `\n` so F2's flip does not depend on it.

## 10. Adjacent misses seen while reading — NOT this lane

- css-text/hyphens/hyphenate-character-005 ios f 0.9450 / android f 0.9474: the `<p>` and `<div dir=rtl>` arrive as
  post-load-extracted ABSOLUTE text pieces (`Position=ABSOLUTE`, `Left −52.38` on `مىغانلىقى`), no `meta.runs`; the
  hyphenate string `"\00a0\0640"` is not painted → structure-extract + `HyphenateCharacter` lanes.
- css-text/hyphens/hyphens-punctuation-001 android f 0.9380: `example`/`(example` not dictionary-hyphenated in 5ch
  (ios P 0.9556) → `AutoHyphenation` lane.
- css-text/hyphens/hyphens-auto-min-content ios f 0.9849: `width: min-content` box renders 0px wide (a lone green
  border line at x≈15) → `sizing/SizeApplierResolve.swift:101/115` lane; cheapest-looking miss in the section.
- Every Compose block-ellipsis-0xx cell passes WITHOUT the marker (001 P 0.9836 = `…room uncha` clipped) — F3 is the
  single change that gives all 20 soft-wrapped clamps their "…"; expect a wide but ref-ward pixel move.
