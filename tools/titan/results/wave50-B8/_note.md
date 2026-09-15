# wave50-B8 — BACKLOG queue 6(c) + 6(d) (6(b) needed no code)

Lane B8 of wave 50. Everything a later wave must act on is in this
directory; nothing of this lane lives in a session scratchpad.

## Files

| file | what it is |
|---|---|
| `measure-shadow-sigma.py` | Re-runnable measurement (`python3 tools/titan/results/wave50-B8/measure-shadow-sigma.py`). Fits the Gaussian σ of a box-shadow penumbra on the COMMITTED baselines by least squares on the separable normal-CDF model. numpy + scipy + pillow, host-side only; no pipeline code imports it. |
| `shadow-sigma-baselines.json` | Its output at tree 747b28e4, with the input PNG sha1s and the `_note` that settles queue 6(c). |
| `compose-text-shadow-sigma.patch` | A NEW defect found while diagnosing 6(c), OUT of this lane's ownership (typography/) — delivered as a patch, not applied. See below. |

Tree changes this lane shipped (not files in this directory — listed here
because the artifact must name everything a later wave has to find):

| tree path | what it is |
|---|---|
| `runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/StyleBuilder.swift` | the 6(d) fix: `.engineVisibility` moved ahead of `.engineTransforms` in `applyStyle`, with the measurement and the blast radius in the source comment. |
| `runtimes/swiftui/Tests/StyleConverterRuntimeTests/OverflowClipTransformOrderTests.swift` | the 6(d) raster pin — 2 tests, both halves of the rule (see the 6(d) section). |
| `runtimes/compose/src/main/java/com/styleconverter/runtime/effects/shadow/MultipleShadowApplier.kt` | **DELETED** — the file 6(c) and the docs/STATUS.md row named, dead since the initial import (954b38e8) with zero call sites. Its deletion is the code half of 6(c); the live map is pinned by `ShadowBlurSigmaParityTest`. |

## 6(c) — Android box-shadow "~2.4× over-blur": the file attribution was wrong

`MultipleShadowApplier.kt` (the file queue 6(c) and the docs/STATUS.md row
name) passed the raw CSS blur radius to `BlurMaskFilter` — but it had **zero
call sites** since the initial import (954b38e8) and is in the retro's own
dead-code census (`../retro-2026-09-04/p2a-compose-dead-remaining.json`).
The LIVE path (`ShadowApplier.applyFullShadow` → `OutsetShadowPainter` /
`InsetShadowPainter`) has converted through `ShadowApplier.blurMaskRadius`
since wave 2 (0b2313a8, 2026-07-17) — verified present at 07f75ec9, the
commit whose probe produced the 2.4× number. Measured σ on the committed
baselines (target = blur/2, css-backgrounds-3 §6.1.2):

| baseline | CSS | σ target | web | Android | iOS |
|---|---|---:|---:|---:|---:|
| `024_Shadow_Colored` (conditioned) | `0 4px 15px rgba(52,152,219,.5)` | 7.5 | 7.09 | **7.49** | 7.07 |
| `107_Edge_InsetRoundShadow` | `inset 0 0 20px rgba(0,0,0,.5)` | 10.0 | 8.50 | **9.69** | 8.27 |
| `023_Shadow_Simple` (ill-conditioned) | `5px 5px 10px rgba(0,0,0,.3)` | 5.0 | 5.65 | **4.61** | 4.41 |

The dead file is deleted in wave 50 and the live map is pinned by
`runtimes/compose/src/test/java/com/styleconverter/runtime/effects/shadow/ShadowBlurSigmaParityTest.kt`.

**Still open for a later wave** (do not treat 6(c) as a closed *reach*
question): the 2026-08-28 probe measured an Android bottom-most shadow row
of 123/131/131/120 against web 93/101/109/90 (box rows 30–70, blur 10,
offset-y 10, spread −8/0/+8/−1px). That probe fixture was never committed,
so it cannot be re-run from this tree, and the excess is **not** a blur-σ
error. The two mechanisms that do widen an Android shadow's painted extent
were both repaired after that probe and are corpus-UNMEASURED: retro R6's
border-box knockout + opacity attenuation, and retro round-2 F1's margin-box
stripping (`ShadowGeometry.borderBoxRect` / `outsetShadowRect`). Re-probe the
reach at the wave-50 device gate before re-opening the item, and strike the
docs/STATUS.md "Android over-blurs box-shadow by ~2.4×" row with this
artifact as the evidence (its file attribution is wrong as written).

## 6(b) — no code in this lane, and none needed

The title of this file used to claim 6(b). It should not have: BACKLOG
6(b) ("Android clips a transformed child's paint to its layout slot") was
re-diagnosed in the 2026-09-04 retrospective as a WRONG MECHANISM finding
(A11#0) — it was never a clip. `StyleApplier.applyConfig` chained the
transform OUTER to the abspos offset, so every pivot was the child's local
centre taken in the PARENT frame. **Both halves already landed in the
retro PR (747b28e4)**, which is this wave's base commit:
`transforms/TransformPivot.kt` conjugates every route's pivot by
`PositionApplier.resolvedOffset`, and the three
`fixtures/combinations/nested-transforms.json` waivers were rewritten to
carry the measured mechanism so the gate deletes them as stale (exit 5).
Its pins (`TransformPivotTest.kt`) are in the tree and ran green in the
retro's own sweep. Nothing was left for wave 50 to write; 6(b) is
**pending the device gate**, not pending code, and this lane touched none
of it. Known limit, inherited and unchanged: a PERCENT inset resolves only
in the composed lane.

## 6(d) — iOS overflow clip moved inside the transform

**The defect.** SwiftUI's modifier chain wraps later modifiers around
earlier ones, so "inner" = applied first. `StyleBuilder.applyStyle` had
`.engineVisibility(style.visibility)` — the overflow-clip carrier;
`VisibilityApplier` turns a clipping used value into `.clipped()` /
`.clipShape(AxisClipRect)` — AFTER `.engineTransforms(style.transforms)`.
The clip rectangle was therefore evaluated around the already-rotated
content in the layout frame the transform never moved (`rotationEffect` /
`offset` do not change layout). CSS orders the two the other way:
css-overflow-3 §3 clips the element's content to its own padding box, and
css-transforms-1 §6 then maps the element's whole rendering — clipped
content included — into the parent's space.

**The shipped hunk** (`runtimes/swiftui/Sources/StyleConverterRuntime/
Renderer/StyleBuilder.swift`, `extension View` → `applyStyle`):

```
             .engineClipPath(style.clipPath)
+            .engineVisibility(style.visibility)
             .engineTransforms(style.transforms)
             .engineMotionOffset(style.motionOffset, size: style.size,
                                 context: style.spacing.context)
-            .engineVisibility(style.visibility)
```

plus the Phase-8 comment block, which now reads
`1. mask · 2. filter · 3. clip-path · 4. visibility/overflow · 5.
transforms` and carries the measurement below. Note what moved: the WHOLE
`.engineVisibility` modifier, so `visibility: hidden`'s `.opacity(0)` and
`collapse`'s `.frame(0,0).hidden()` also changed side of the transform,
and `.engineMotionOffset` went from inside the clip to outside it. Skeptic
S4 censused both co-declarations against the corpus
(`../wave50-S4/census-visibility-motion-cooccurrence.mjs`): **0**
components declare Visibility with a transform and **0** declare an
offset-path property with an overflow, so neither can move a cell today.
`StyleBuilder.hoistedOutline`'s slot is unaffected — outline was, and
still is, outside `.engineTransforms`.

**The measurement** (wave49-final, frozen captures, 390-wide canvases) —
`css-backgrounds/background-attachment-fixed-inside-transform-1`, whose
`#outer` is 300×700 with `transform: rotate(45deg); overflow: hidden`:

| capture | ink cols | ink rows | ink px |
|---|---|---|---:|
| web | 13–389 | 330–918 | 115281 |
| **iOS** | **216–389** | 330–915 | 73886 |
| Android | 33–389 | 501–1068 | 106424 |
| ref | 16–373 | 346–919 | 108678 |

The iOS strip starts exactly at the element's un-rotated left frame edge
(margin 200) instead of at the rotated rectangle's leftmost tip — the
signature of a clip evaluated outside the transform. Its manifest row is
`browserRef.diffs.ios-ref ssim 0.9843 wptPass true`: a visibly wrong
PASS, the A1#0 class. S4 re-derived every number above independently.

**The pin.**
`runtimes/swiftui/Tests/StyleConverterRuntimeTests/OverflowClipTransformOrderTests.swift`
— two Catalyst raster tests over converter-verbatim IR for the gate-net
fixture `fixtures/combinations/radius-overflow-transform.json`:
`testSelfRotateClipsInsideTheTransform` (the 80×60 clipped content rotates
to a (80+60)·cos45° = 98.99 diamond) and
`testAncestorClipStillAppliesAfterAChildTransform` (the other half of the
rule — a DESCENDANT's transform still happens before the ancestor's clip;
70×72 at x 74). Mutation-proved by S4 as well as by the lane: reverting
`.engineVisibility` to after `.engineMotionOffset` (cp → sha256 → edit →
run → restore, StyleBuilder.swift sha256
`7a10196fc822cd6304c00b8caa59df8ddbde446905b273c36b21454e6322fb52`
unchanged either side) fails the first test with `("80") is not equal to
("99")` / `("60") … ("99")` while the second stays green.

**Blast radius** (`../wave50-S4/census-transform-overflow.mjs`, all 1435
wave49-final per-test IR documents, with the css-overflow-3 §3.1
used-value coercion applied): **25 tests / 33 components** declare a
transform AND an overflow on the same element; only **5 tests / 7
components** carry a CLIPPING used value and therefore change path. The
other 20 tests declare `overflow: visible` on both axes, where
`OverflowClipRules.axisClips` is false and no clip is installed at all.

**The five no-movers, rasterised rather than argued.** S4 rendered every
ROOT of the four non-target tests from VERBATIM wave49-final per-test IR
at 390×600 through `ComponentRenderer` + `ImageRenderer` under BOTH chain
orders and compared SHA-256:

| test | root | fixed vs mutated |
|---|---|---|
| `css-transforms/backface-visibility-hidden-006` | `…006__0-063` | identical |
| `composited-under-rotateY-180deg-clip` | `…__0-083` | identical |
| `composited-under-rotateY-180deg-clip` | `…__1-085` | identical |
| `composited-under-rotateY-180deg-clip-perspective` | `…__0-079` | identical |
| `css-color/clip-opacity-out-of-flow` | `…__0-092` | identical |
| `css-backgrounds/…-fixed-inside-transform-1` | `…__0-036` | **moves** — ink 302×302 @ (88,298) vs 190×261 @ (200,298) |

The reason is geometric, and the rasters confirm it: `rotateY(180deg)` is
an involution about the frame's own centre line and `translateX(0px)` is
the identity, so each maps the clip rect onto itself — the two orders
cannot differ. The target's 45° rotation does not.

**The gate-only oracle.** The same rule has an executable check in the
fixture net: `fixtures/combinations/radius-overflow-transform.json`
`ROT_SelfRotate_ClippedChild` (line 29 of
`tools/visual/gate-fixtures.txt`), `_expect.box` = the 99×99 diamond,
`boxTolerance` 6. It was RED on iOS and this change turns it green —
S4 measured the post-fix Catalyst raster at 100×100 with the fill exactly
`#e74c3c`, so BOTH oracle checks pass. Its `_expect.waive.iOS` ("This
waiver is the red test that the fix turns stale") therefore became STALE,
which `tools/visual/spec-oracle.mjs` files under `stale` and
`tools/visual/compare-screenshots.mjs` turns into
`EXIT_STALE_EXPECTATION`. **Wave 50 fix lane F2 deleted that waiver and
rewrote the component's `note` to past tense**; the fixture has no
committed baseline (it is gate-only + oracle), so nothing else moves with
it. The iOS harness capture is a downscaled on-device render, so the
diamond landing inside the strict fillTolerance-2 band remains a
PREDICTION until the gate runs.

**Pre-existing residual, named so it is not read as a regression.**
`.engineFilter` and `.engineClipPath` are still INNER of the clip, so a
`blur()` / `drop-shadow()` halo on an overflow-clipping element is cut at
the padding edge although filter-effects-1 §8 lets it spill. Zero
wave49-final components declare both, so it is unmeasurable today and was
not folded into this change.

## Deferred patch — Compose text-shadow blurs 1.25–1.4× too wide

Found while auditing every Compose blur consumer for 6(c). The LIVE
text-shadow path passes the raw CSS blur radius as Compose
`Shadow.blurRadius` (`typography/TypographyConfig.kt` `toShadow()` and
`typography/TextStyleApplier.kt` `extractTextShadow`). Compose hands that
field to `android.graphics.Paint.setShadowLayer`, and Skia reads it as
σ = 0.57735·radius + 0.5 — so a 4px CSS blur paints σ 2.81 where
css-text-decor-4 §2.6 → css-backgrounds-3 §6.1.2 ask for 2.0, and where iOS
already divides by two (`ComponentRenderer.GlyphShadows`: `radius: l.radius / 2`)
and web renders natively. `compose-text-shadow-sigma.patch` routes both call
sites through the existing `ShadowApplier.blurMaskRadius` and updates the one
pin that asserted the raw value (`TypographyWave2Test`, 4f → 2.598f, with the
arithmetic in the comment).

Status: **compiled and the focused typography suite passes with it applied**
(`:runtime:testDebugUnitTest --tests 'com.styleconverter.runtime.typography.*'`,
12/12 in TypographyWave2Test). NOT applied — `typography/` is outside lane
B8's ownership — and NOT device-verified: the Compose→`setShadowLayer`→Skia
mapping is read from the platform sources, not measured, and the committed
`077_TextShadow_Simple` / `078_TextShadow_Glow` baselines are degenerate
(their fixture components carry no text, so all three platforms capture the
harness label only — zero glow ink to fit). A gate lane should apply it with
a text-carrying text-shadow fixture, or park it with that reason.
