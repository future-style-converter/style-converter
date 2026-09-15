# wave50-S4 — SKEPTIC lane: the SwiftUI claims (B8 6(d), B4 lists, B11
# visibility, B10 css-gaps bands, B9 no-port)

Lane S4 of wave 50. **No devices, no browsers.** Every number below is a
Catalyst execution, a byte differential over frozen wave49-final
artifacts, or PNG arithmetic on committed captures/refs. Anything I could
not execute is called UNVERIFIED, not "holds".

All build commands ran from the repo root as

```
xcodebuild test -scheme StyleConverterRuntime \
  -destination "platform=macOS,variant=Mac Catalyst,arch=arm64" [-only-testing:…]
```

Throwaway probe sources (deleted from the tree before reporting; kept
here verbatim as evidence):

* `Skeptic50OverflowOrderProbeTests.swift.txt`
* `Skeptic50CssGapsReplayTests.swift.txt`
* `Skeptic50ListsAndVisibilityProbeTests.swift.txt`

Re-runnable censuses written by this lane (never a builder's script):

* `census-transform-overflow.mjs` — every wave49-final per-test-IR
  component declaring a transform AND an overflow, with the
  css-overflow-3 §3.1 used-value coercion applied.
* `census-visibility-motion-cooccurrence.mjs` — the two co-declarations
  B8's blast radius did NOT censuse (Visibility+transform,
  offset-path+overflow).
* `css-gaps-raster-differential.txt` — all 48 css-gaps per-test IR
  documents rendered through the real SwiftUI painter, SHA-256 per root
  component, with and without `GapDecorationBands.resolve`.

---

## MUST FIX BEFORE THE GATE — the 6(d) fix leaves a STALE ORACLE WAIVER

`fixtures/combinations/radius-overflow-transform.json`
`ROT_SelfRotate_ClippedChild._expect.waive.iOS` is still present. The
fixture is line 29 of `tools/visual/gate-fixtures.txt`, so the gate set
runs it; `tools/visual/spec-oracle.mjs:325-330` files a waiver whose
platform passes BOTH checks under `stale`, and
`tools/visual/compare-screenshots.mjs:619` turns a non-empty `stale`
into `process.exit(EXIT_STALE_EXPECTATION)`. The waiver's own text says
so: *"This waiver is the red test that the fix turns stale."*

Measured (Catalyst, `Skeptic50OverflowOrderProbeTests.testProbeSelfRotateBBox`,
independently authored IR, NOT B8's string):

| chain order | red bbox at the oracle's own fillTolerance 2 | oracle verdict |
|---|---|---|
| wave-50 (clip INSIDE transform) | **100×100** at (14,4), 4998 px | box \|100−99\|=1 ≤ 6 → PASS, fill Δ0 → PASS ⇒ **stale waiver ⇒ exit 5** |
| mutated back (clip outside) | 80×60 at (24,24), 4800 px | box violation ⇒ waived (today's green) |

`_expect.note` on the same component ("RED on iOS today: clip outside
the transform reads [80,60]") is stale prose in the same object.

---

## B8 / queue 6(d) — the reorder itself: CONFIRMED, with two gaps

**The defect was real.** PNG arithmetic on the frozen artifacts for
`css-backgrounds/background-attachment-fixed-inside-transform-1`
(non-white ink bbox, 390-wide canvases):

```
web      inkcols=[13,389]  inkrows=[330,918]  inkpx=115281
iOS      inkcols=[216,389] inkrows=[330,915]  inkpx=73886
Android  inkcols=[33,389]  inkrows=[501,1068] inkpx=106424
ref      inkcols=[16,373]  inkrows=[346,919]  inkpx=108678
```

exactly B8's cited numbers, and the manifest's `browserRef.diffs.ios-ref`
is `ssim 0.9843 wptPass true` — a visibly wrong PASS.

**The pin can fail.** Mutating `.engineVisibility(style.visibility)` back
to after `.engineMotionOffset(…)` (copy/sha256/restore; StyleBuilder.swift
sha 7a10196fc822cd6304c00b8caa59df8ddbde446905b273c36b21454e6322fb52 before
and after) makes
`OverflowClipTransformOrderTests.testSelfRotateClipsInsideTheTransform`
fail with `("80") is not equal to ("99")` and `("60") … ("99")`, while
`testAncestorClipStillAppliesAfterAChildTransform` stays green.

**The blast radius reproduces exactly.** `census-transform-overflow.mjs`
over all 1435 per-test IR documents: 33 components / 25 tests declare a
transform and an overflow; **7 components / 5 tests** carry a clipping
used value — the same five B8 names.

**The involution argument HOLDS.** Rasterising every ROOT of the three
at-risk `rotateY(180deg)` carriers plus `clip-opacity-out-of-flow` from
VERBATIM wave49-final per-test IR, at 390×600 through `ComponentRenderer`
+ `ImageRenderer`, under both chain orders:

| test | root | sha256 (fixed) | sha256 (mutated) |
|---|---|---|---|
| backface-visibility-hidden-006 | …006__0-063 | fff6a31a…dea82 | **identical** |
| composited-under-rotateY-180deg-clip | …__0-083 | 2cbdcc55…c1121 | **identical** |
| composited-under-rotateY-180deg-clip | …__1-085 | fff6a31a…dea82 | **identical** |
| composited-under-rotateY-180deg-clip-perspective | …__0-079 | 2cbdcc55…c1121 | **identical** |
| css-color/clip-opacity-out-of-flow | …__0-092 | ae82957e…6fb6 | **identical** |
| css-backgrounds/background-attachment-fixed-inside-transform-1 | …__0-036 | b45dd922…b59eb (ink 302×302 @ 88,298) | cb3264ed…0f30 (ink 190×261 @ 200,298) — **the target, and the only one that moves** |

(Whole per-test IR documents render fine — no component-alone fallback
was needed.)

**Phase-8 comment audit.** The rewritten five-item list matches the live
chain (`.engineMask` → `.engineFilter` → `.engineClipPath` →
`.engineVisibility` → `.engineTransforms`). Two things the list does not
say: the move is of the WHOLE `.engineVisibility` modifier — so
`visibility: hidden`'s `.opacity(0)` and `collapse`'s `.frame(0,0)
.hidden()` also changed side of the transform — and `.engineMotionOffset`
went from inside the clip to outside it. `census-visibility-motion-
cooccurrence.mjs`: **0** corpus components declare Visibility together
with a transform, **0** declare an offset-path property together with an
overflow, so nothing in the corpus can move on either; the claim is
under-stated, not wrong. `StyleBuilder.hoistedOutline`'s slot is
unaffected (outline was, and still is, outside `.engineTransforms`).

**Evidence hygiene defect.** `tools/titan/results/wave50-B8/_note.md` is
titled "queue 6(b) + 6(c) + 6(d)" and documents only 6(c) plus the
deferred Compose text-shadow patch. The shipped 6(d) change has NO
artifact in that directory — which is how the stale waiver was missed.

---

## B4 / lists — KoreanHangulFormal: CONFIRMED, banner is wrong

Executed (`Skeptic50ListsAndVisibilityProbeTests.testPrintKoreanMarkers1to12`):

```
1=일, 2=이, 3=삼, 4=사, 5=오, 6=육, 7=칠, 8=팔, 9=구, 10=일십, 11=일십일, 12=일십이,
expand: 9999=구천구백구십구  10000=10000  0=영  -1=-1  -5=-5  suffix=[,]
```

Checked by hand against css-counter-styles-3 **§7.1**'s
`korean-hangul-formal` `additive-symbols` (the brief's "§6.2" is the
wrong section; the file's §7.1 is right, and
`node tools/visual/spec-cite-validate.mjs` exits 0 with 0 unknown
sections): `10 일십` (formal writes the digit before the place) and
`11 = 10 일십 + 1 일 = 일십일` both reproduce; 9999 → 구천구백구십구 is the
four-place expansion; 10000 bottoms out to decimal per §3.2 `range`.

The wire really does need the runtime table:
`counter-suffix__0__3-620` carries `ListStyleType "korean-hangul-formal"`
and its two `<li>` children carry **no** `meta.markerText` (every other
list in that document does).

Mutations (each: cp → sha256 → edit → run → restore → sha256 verified):

| mutation | result |
|---|---|
| `KoreanHangulFormal.suffix` `","` → `", "` | KoreanHangulFormalTests 4 tests / **2 failures** (`"일, "` ≠ `"일,"`) |
| drop `case "korean-hangul-formal"` from `ListMarkerResolver.markerType` | 4 tests / **6 failures** across tests 2 and 3 — exactly B4's claimed proof |

`ListMarkerStyle.swift` is **212 lines** (B4's number is right; 12 over
the ≤200 target, under the ~300 split threshold).

### The three Compose edits the TWIN DIVERGENCE banner owes — NAMED WRONG

`KoreanHangulFormal.swift` (and `ListMarkerText.swift`'s echo of it) send
the fix lane to `runtimes/compose/.../lists/ListStyleType.kt`. **That file
does not exist** (`find runtimes/compose -name ListStyleType.kt` → empty).
The real three edits are:

1. `runtimes/compose/src/main/java/com/styleconverter/runtime/lists/ListStyleConfig.kt:42`
   — the `ListStyleType` enum (`KATAKANA_IROHA` is its last member); add
   `KOREAN_HANGUL_FORMAL`.
2. `…/lists/ListStyleExtractor.kt:175` — `typeFromKeyword` lowercases and
   **replaces `-` with `_`**, so the new row's key must be
   `"korean_hangul_formal"`, not the hyphenated wire spelling.
3. `…/lists/ListStyleApplier.kt:46` — `getMarker`'s `when`, next to
   `ListStyleType.KATAKANA_IROHA -> "${toKatakanaIroha(index)}."`.

### The suffix decision — reference-fitted, and the measurement is misquoted

`KoreanHangulFormal.suffix` drops the descriptor's literal trailing space.
The comment's justification is architectural (`ListMarkerRow.gapPt == 4`
supplies the gap once) plus a ref measurement. Re-measured on the frozen
ref `…/css-counter-styles/counter-suffix.png` (dark-ink column runs per
text band):

```
band0 rows  21- 33  marker [46,58]  text [64,87]   <- decimal "1."
band1 rows  46- 57  marker [46,58]  text [65,88]   <- decimal "2."
band4 rows 117-130  marker [33,52]  text [64,87]   <- cjk-decimal
band6 rows 164-180  marker [42,58]  text [64,87]   <- korean "일,"
band7 rows 189-204  marker [42,58]  text [65,88]   <- korean "이,"
```

The CONCLUSION survives and is stronger than stated — the korean marker
run ends at the SAME column 58 as the decimal one and the text starts at
the same 64/65, so the descriptor's trailing space costs no advance in
the reference. But the comment's quoted bands (`46–58/65–88 vs
42–57/64–87`) are wrong in two ways: korean measures 42–**58**, not
42–57, and it pairs band 1's text against band 6's text. And the decision
is presented as renderer-architecture, not LABELLED reference-fitted; the
spec argument that would make it derivable — css-text-3 §4.1.3 trims a
collapsible trailing space at the end of a line box, and an `outside`
marker box is one line box — is not made.

### A pinned-wrong negative

`expand(-1)` → `"-1"` and `expand(-5)` → `"-5"`. `range: -9999 9999`
INCLUDES those, and the style declares `negative: "마이너스 "`, so the
correct strings are 마이너스 일 / 마이너스 오. The `- Returns:` doc calls
this "the DECIMAL spelling when the value falls outside the declared
range" — it is not out of range — and
`KoreanHangulFormalTests.testExpandCoversTheAdditiveTable` pins
`expand(-1) == "-1"` as if correct. Unreachable from the marker path
(`index + 1 ≥ 1`) and zero corpus carriers, so this is a comment/pin
honesty defect, not a render defect.

---

## B11 / visibility — `collapse` semantics: CONFIRMED

Executed (`Skeptic50ListsAndVisibilityProbeTests.testCollapseTreatmentBothWays`,
wire-shaped payloads through the real `VisibilityExtractor`):

```
div (Display BLOCK, Visibility COLLAPSE)              track=false treatment=inkSuppressed touched=true
row (TABLE_ROW)                                        track=true  treatment=boxRemoved   touched=true
colgroup, HYPHENATED "table-column-group"              track=true  treatment=boxRemoved   touched=true
cell (TABLE_CELL)                                      track=false treatment=inkSuppressed touched=true
Display TABLE_ROW alone, no visibility/overflow        config = nil
```

CSS 2.2 §11.2 exactly: removal only on row/row-group/column/column-group,
`hidden` treatment everywhere else including a CELL, both wire spellings
accepted, and the new `isTableTrackBox` input provably does not set
`touched`. No silent fallthrough: `treatment` is an exhaustive switch over
a three-case enum and `declaredVisibility` returns nil (inherit) for an
unrecognised keyword.

Mutation: `treatment`'s collapse branch forced to `.boxRemoved` →
`VisibilityBoxRulesTests` 8 tests / **1 failure**
(`testCollapseOffATableTrackIsTreatedAsHidden`).

Suites that touch `VisibilityConfig` — the full grep, not B11's two:
`OverflowAxisClipTests` (8), `BorderRadiusOverflowFixtureTests` (4),
`Wave18CleanupParityTests` (16), `VisibilityBoxRulesTests` (8),
`OverflowClipTransformOrderTests` (2), `EffectsTests`. **No suite compares
a whole `VisibilityConfig` value against extractor output** — the only
two constructing sites use the empty memberwise init and assign fields —
so the new Equatable field cannot silently flip an assertion. All green.

---

## B10 / css-gaps bands — blast radius CONFIRMED by byte differential

`css-gaps-raster-differential.txt`: every one of the 48 wave49-final
css-gaps per-test IR documents rendered root-by-root through the REAL
SwiftUI painter (`ComponentRenderer` + `ImageRenderer`, 390×600, scale 1),
SHA-256 per raster, with `GapDecorationBands.resolve` live and with its
body short-circuited to `return lines` (GapDecorationBands.swift sha
3d97a79d4b8fca6604422d1969ed5e02167af7b035f29281d20ff0b222279456 before
and after the mutation):

```
43,44c43,44
< S4GAPS 045 c19f1458939a4bf2 55354e2bf8ed689f
< S4GAPS 046 83cfef0c51948e18 4027c7d8461ddbb9
---
> S4GAPS 045 c19f1458939a4bf2 ab622f9f1bd8a7c3
> S4GAPS 046 83cfef0c51948e18 df00a336629c1d71
```

**Exactly 045 and 046 change; the other 46 tests are byte-identical.**
That covers the adversarial list empirically — nowrap, positioning
`align-content`, auto cross size and zero leftover are all inert on the
real corpus, not merely by inspection.

Structurally: the struct defaults are `rowGapPx = nil` / `columnGapPx = nil`
(NOT 0) and `resolve` bails with a `PropertyTracker.logOnce` breadcrumb on
a nil cross gap; the extractor sets 0 for an ABSENT property only, and
does not set `touched` or claim a registry row.

Mutation: containment gate forced to always accept →
`GapDecorationBandsTests` 11 tests / **1 failure**
(`testBandsThatDisagreeWithPlacedItemsAreRejected`), exactly B10's claim.

### Swift-vs-Kotlin rule diff — one real divergence, understated in the source

`GapDecorationBands.swift`'s header says "Byte-parallel with Compose's …
GapDecorationBands.kt: same `resolve` entry point, same gates, same
fallback." Gates and fallback ARE identical (n<2, !stretches, nil gap,
`sizes == base`, containment ε 0.5, breadcrumb-then-union). The
ARITHMETIC is not:

| step | Swift | Kotlin |
|---|---|---|
| base line sizes | `crossEnd − crossStart` (CGFloat) | `cross.size.roundToInt()` |
| gap / container cross | CGFloat | `roundToInt()` |
| §9.4 step 8 share | `FlexWrapPlan.stretchLines` — `leftover / n` to EVERY line | `FlexWrapLines.stretchLines` — integer `share` + `+1` to the first `remainder` lines |
| band cursor origin | starts at `0` (content-relative) | starts at `cross.start` |

For 046 (3 lines of 50 in a 180 cross box, gap 5, leftover 20) that is
Swift `[0,56.67] [61.67,118.33] [123.33,180]` against Kotlin
`[0,57] [62,119] [124,180]`. B10's `_note.md` predicts the resulting 1px
Android offset; the source header does not, and a reader who trusts
"byte-parallel" will look for a bug when the two natives differ by a
pixel. Each side does match its OWN layout (the same pure function the
platform's wrap layout calls), which is the property that actually
matters.

---

## B9 — no ring port slipped into SwiftUI: CONFIRMED

`git status --porcelain -- runtimes/swiftui/` lists **nothing** under
`StyleEngine/typography/inline/` this wave (the directory holds only
`InlineRunFlow.swift` and `InlineSpanRing.swift`, both unmodified), and
none of B9's new Compose files (`LineBoxCensus*`, `LineBoxChildMetrics`,
`LineClampCapResolve`) has a Swift counterpart. The frozen wave49-final
manifest still records
`css-overflow/line-clamp/block-ellipsis-032.tentative.html`
`ios-ref ssim 0.9577 wptPass true` (web 0.9834 P, Android 0.9451 f).
That number is a frozen artifact read, not a re-capture — with no devices
this lane cannot re-render it.

---

## FULL CATALYST SUITE (S1 asked for this number)

```
xcodebuild test -scheme StyleConverterRuntime \
  -destination "platform=macOS,variant=Mac Catalyst,arch=arm64"
→ Executed 2004 tests, with 0 failures (0 unexpected)   ** TEST SUCCEEDED **
```

Run with the `_skeptic50` probes already DELETED, so 2004 is the tree's
own count. Cross-check: CLAUDE.md records 1979 at 747b28e4; wave 50 adds
KoreanHangulFormalTests 4 + VisibilityBoxRulesTests 8 +
GapDecorationBandsTests 11 + OverflowClipTransformOrderTests 2 = 25.
1979 + 25 = 2004 — the new pins are all actually running.

---

## WHAT THIS LANE COULD NOT TEST (no devices, no browsers)

* Whether the iOS HARNESS capture (on-device render, downscaled to the
  390-wide canvas) reproduces the ~99×99 diamond inside the oracle's
  strict fillTolerance-2 band. I measured `ImageRenderer` at scale 1 under
  Catalyst: 100×100 with zero blend pixels. The fixture's own note warns
  the strict bbox can under-read by ~5 px on a real antialiased capture;
  ±6 absorbs that, so the stale-waiver prediction is robust, but it is a
  PREDICTION until the gate runs.
* Whether the rebuilt 045/046 bands land on the frozen refs (B10's
  ≥0.99 / ≥0.995 predictions) — needs captures.
* Whether the Swift-fractional vs Kotlin-integral band split moves a cell.
* `background-attachment-fixed-inside-transform-1`'s post-fix score.
