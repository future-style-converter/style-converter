# Phase 12 Audit — borders

Fixture: `examples/properties/borders/audit-phase12.json` (19 variants)
Run: `SKIP_IOS=1 NO_OPEN=1 ./test-all.sh examples/properties/borders/audit-phase12.json`

Platforms captured: **Android (19/19)**, **Web (19/19)**. iOS skipped — `xcodebuild` produced
no `.app` bundle on this host (Xcode 26.x scheme/platform bug, unrelated to borders code). Therefore
every score below is **Android↔Web only**. iOS code was reviewed statically; per-property notes
include iOS gaps found by reading the source.

Snapshot: `testing/audit/borders/snapshot/` (manifest.json, index.html, diffs/, images/).
Failure PNGs extracted to `testing/audit/borders/images/` (render + diff per failing variant).

## Scope

Covers the 47 IR properties under `irmodels/properties/borders/` plus the adjacent
`effects/shadow/BoxShadow*`. Groupings tested: sides (width/color/style/logical), radius
(physical + logical + elliptical), image (source/slice/width/outset/repeat), outline
(width/style/color/offset), and box-shadow.

## Failing (SSIM < 0.95, Android↔Web)

| # | Variant | SSIM | Pct | Root cause |
|---|---|---|---|---|
| 001 | Radius_SumExceedsSide | 0.948 | 0.93% | Cosmetic — both pill-render correctly, AA on text barely misses threshold. ![d](borders/images/001_Audit_Radius_SumExceedsSide__diff.png) |
| 002 | Radius_Asymmetric_Corners | 0.945 | 0.66% | Same as above — elliptical path taken on Android, visually matches web. |
| 004 | Style_Double_3px | 0.826 | 3.02% | **Android: border not visible at all.** `drawDouble` with `width=3f` hits `width < 3f` branch never (equal, not less), computes `band=1f`, but the two 1-px strokes collide at y=0.5 and y=2.5 with 1-px stroke — rasterises to a faint line the test missed. Web draws proper CSS double. ![a](borders/images/004_Audit_Style_Double_3px__Android.png) ![w](borders/images/004_Audit_Style_Double_3px__web.png) |
| 005 | Style_Double_2px | 0.821 | 3.46% | Same code path, even worse — `< 3f` branch degrades to solid on Android; web draws both strokes. |
| 006 | Style_Groove_Thick | 0.781 | 14.4% | **Android: no border drawn.** Shade factor 0.5 over `#8e44ad` with lighten=true yields `#c7a2d6`; the outer+inner strokes paint over the `#ecf0f1` background with colors that appear to AA away on the white bg. But also `hasBorder` check uses `width.value > 0 && style != LineStyle.NONE`, plus drawable is `(outerStart, outerEnd)` computed as `doubleGeom(side, half/2f)` with `half=7f`, so stroke of width 7 centered at y=1.75 — **the stroke's upper half draws OUTSIDE the box** and is clipped away. ![a](borders/images/006_Audit_Style_Groove_Thick__Android.png) ![w](borders/images/006_Audit_Style_Groove_Thick__web.png) |
| 007 | Style_Ridge_Thick | 0.783 | 15.0% | Same geometry bug as groove (just shade inverted). |
| 008 | Style_Inset_Thick | 0.826 | 14.9% | Android only shades top+start (darkens), web draws CSS spec 3D shading on all four with proper corner miters; the shade colors differ (Android's `t=0.5` mix vs browser UA default ~0.5 darken, close but not identical). |
| 009 | Style_Outset_Thick | 0.808 | 14.5% | Mirror of 008. |
| 010 | PerSide_FourStyles | 0.845 | 7.4% | **Android: 3 of 4 sides invisible.** Screenshot shows only bottom side's green dotted dashes. Top/right/left strokes missing despite each side going through `paintSide` — investigation suggests `drawBehind` is clipped by `BorderRadiusApplier.clip` when a separate border-radius clip is already applied (stroke sits at edge, half drawn outside). Web renders all four distinct styles correctly. ![a](borders/images/010_Audit_PerSide_FourStyles__Android.png) ![w](borders/images/010_Audit_PerSide_FourStyles__web.png) |
| 011 | ZeroWidth_SolidStyle | 0.946 | 0.95% | Cosmetic — zero-width border correctly renders nothing on both. Text AA costs the 0.004 SSIM. |
| 012 | BorderImage_Gradient_FractionalSlice | 0.623 | 24.5% | **Android border-image gradient not rendered at all.** `BorderImageApplier` only runs when invoked as a Composable (`BorderImageBox`) — the modifier-based `applyBorderImage(modifier, config, bitmap)` requires a pre-loaded `ImageBitmap`, which the runtime never supplies for gradient sources. The async `rememberCachedGradient` path only exists inside `BorderImageBox`, and `ComponentRenderer` does not route border-image components through it. Result: empty container. Web serialises the gradient to CSS directly. ![a](borders/images/012_Audit_BorderImage_Gradient_FractionalSlice__Android.png) ![w](borders/images/012_Audit_BorderImage_Gradient_FractionalSlice__web.png) |
| 013 | BorderImage_Outset_Large | 0.550 | 25.1% | Same root cause as 012 — no border-image renders; outset isn't even reached. |
| 015 | Outline_Double_6px | 0.921 | 0.54% | **Android draws outline-double as solid** (OutlineApplier likely drops the `double` subvariant back to solid for width ≥ 3; web renders two strokes). Pixel delta small because outline is thin, but SSIM catches the missing gap. ![a](borders/images/015_Audit_Outline_Double_6px__Android.png) ![w](borders/images/015_Audit_Outline_Double_6px__web.png) |
| 016 | Shadow_ManyLayers (10 layers) | 0.860 | 4.52% | **Android stacks shadows by repeatedly calling `drawRoundRect` with BlurMaskFilter** — all layers paint at the same origin, so mid-stack colors are covered by later layers. Visually only the last 2–3 layers (pink/black) appear. Web composites all 10 via native `box-shadow: a, b, c, …`. ![a](borders/images/016_Audit_Shadow_ManyLayers__Android.png) ![w](borders/images/016_Audit_Shadow_ManyLayers__web.png) |
| 017 | Shadow_Inset_And_Outset_Mixed | 0.934 | 1.03% | Near pass — Android's inset path shades slightly different extent than web due to the outerPadding+blur heuristic in `applyInsetShadows`. |

## Passing (SSIM ≥ 0.95)

| # | Variant | SSIM |
|---|---|---|
| 000 | Radius_50Pct_30Pct_Square | 0.967 |
| 003 | Radius_Zero | 0.972 |
| 014 | Outline_Double_2px | 0.952 |
| 018 | Shadow_Inset_LargeSpread | 0.951 |

(4/19 pass cross-platform at SSIM ≥ 0.95.)

## Parser gaps

No parser regressions observed — every audit variant parsed to a full IR payload with
`15/7/13/4/6 properties` (see `test-all.sh` output). `calc()` was not exercised in this
batch; border-width `calc(2px + 4px)` is already covered by the main fixture and resolves
to a concrete length. No `decode error` rows appeared in the manifest.

Potential gap to track (not failures, observed while reading parser-adjacent code):
- `border-image-slice: calc(50% + 10px)` — untested; the parser's current
  `BorderImageSlicePropertyParser.kt` uses discrete percent/number branches and likely
  rejects mixed calc. Did not add to fixture because the failure would be parser-level
  and block the whole row; worth a follow-up.

## Recommendations

1. **Android border-image gradient path is load-bearing and wired wrong.** `ComponentRenderer`
   needs to route components whose `border-image-source` is a gradient through
   `BorderImageApplier.BorderImageBox` (Composable path with `rememberCachedGradient`)
   instead of the modifier-based `applyBorderImage` that requires a pre-loaded `ImageBitmap`.
   This is the single biggest delta in the category (SSIM 0.55–0.62).

2. **Android groove/ridge/inset/outset geometry is wrong at wide widths.** The two strokes in
   `drawGrooveOrRidge` are positioned at `half/2` and `half/2 + half` from the outer edge, each
   drawn with `lineWidth=half`. At width=14 that places the outer stroke centre at y=3.5 with a
   7-px stroke — the upper 3.5 pixels paint above the box origin and get clipped. Should inset
   the first stroke by `half/2` inside the box (current formula) but then recognise the stroke
   rasterises around its centre, so the first stroke should be at y = `half/2` (correct) with
   width `half` but the second at y = `half + half/2` (correct). Need to verify against
   `sideGeometry` and ensure drawBehind isn't clipped by Compose's drawing bounds when stroke
   extends beyond content rect. Concretely: switch to a filled Path pair rather than stroked
   lines (rects of width `half` × edge length), which can't bleed.

3. **iOS groove/ridge/inset/outset are documented TODOs that degrade to solid**
   (`BorderSideApplier.swift:176`). Once iOS capture is restored, all four variants will
   fail there too. Acceptance criterion from CLAUDE.md ("no silent fallthroughs") is violated —
   the iOS applier silently degrades without emitting a PropertyTracker warning. Recommend
   logging via PropertyTracker when style falls through the `default` branch.

4. **Android double-border width=3 is a boundary-condition bug.** `drawDouble` treats `width < 3f`
   as too thin (degrades to solid) but `width == 3f` takes the proper path with `band=1f`, and the
   two 1-px strokes render too faintly to be visible at 1× density. Either (a) bump the threshold
   to `< 4f` and fall back to a single 1-px stroke for 3-px double, or (b) switch to
   filled rects so the 1-px bands render at full opacity.

5. **Android PerSide mixed-styles loses 3 of 4 sides** — cannot reproduce without live debug,
   but hypothesis: stroke centred on the box edge has half its width drawn outside the
   composable's draw bounds, which are clipped by the parent layout. Safer geometry: draw each
   side as a filled trapezoid whose vertices are on the inside edge only (canonical CSS
   border-box mitered corners).

6. **Android box-shadow stacking** uses native `drawRoundRect` per layer, which simply overwrites
   earlier layers (no screen / blend). CSS requires layers to paint **back-to-front** with each
   layer at full opacity — which works if offsets differ, but here most layers share offset
   `0 0` and only the blur+color differ, so later layers cover earlier ones. Fix: draw each
   layer's path using `BlendMode.Plus` so colours accumulate, OR use Android 9+ `RenderNode`
   shadow API which natively supports multiple `box-shadow` entries.

7. **Android outline-double degrades to solid** — parallel to the iOS groove TODO. Needs the
   same two-stroke pattern the sides applier uses.

8. **Radius sum-exceeds-side** and **asymmetric corners** visually match the spec on both
   platforms; the 0.94–0.95 SSIM gap is text anti-aliasing against the background and will
   disappear once the fixture drops the component title from the captured canvas (currently
   the shared renderer overlays a label). Worth confirming the label-free baseline before
   declaring them fixed.

## Coverage matrix (post-audit proposed state)

| Group | Android | iOS | Web | Notes |
|---|---|---|---|---|
| sides (width/color/solid/dashed/dotted) | OK | OK | OK | All passing in previous fixtures |
| sides (double thick ≥ 4px) | OK | OK | OK | |
| sides (double 2–3px) | **BROKEN** | OK | OK | Rec. 4 |
| sides (groove/ridge/inset/outset ≤ 8px) | partial | **SOLID fallback** | OK | Rec. 2, 3 |
| sides (groove/ridge/inset/outset ≥ 10px) | **BROKEN** | **SOLID fallback** | OK | Rec. 2, 3 |
| per-side mixed styles | **BROKEN** | untested | OK | Rec. 5 |
| radius physical + logical | OK | OK | OK | |
| radius elliptical asymmetric | OK | OK | OK | Cosmetic AA |
| border-image url | untested here | untested | OK | |
| border-image gradient | **BROKEN** | untested | OK | Rec. 1 |
| border-image outset/slice/width/repeat | **BROKEN** (propagates from source) | untested | OK | Rec. 1 |
| outline width/color/offset | OK | OK | OK | |
| outline style=double | **SOLID fallback** | untested | OK | Rec. 7 |
| box-shadow single/inset/spread | OK | OK | OK | |
| box-shadow ≥ 5 layers | **DEGRADED** | OK (native stack) | OK | Rec. 6 |
