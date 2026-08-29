# fixtures/combinations/ — the interaction-class corpus

Every real bug found in the 2026-08-28 audit hid in a COMBINATION no
fixture exercised: filter+text, perspective+translateZ, filter+
translucency, multi-function transform lists (docs/STATUS.md, the
2026-08-28 sections). The per-property suites under
`fixtures/properties/` exercise one property at a time, so a runtime
that gets each property right in isolation and wrong in composition
passes the whole corpus. This directory closes that class of hole.

## Principle

**One fixture per interaction class, one variable per component.**
An interaction class is a pair (or triple) of CSS features whose
composition is defined by the spec but implemented by structurally
different code paths on the three runtimes (Compose graphicsLayer vs
SwiftUI modifier chains vs the browser). Each fixture:

- derives every expected value BY HAND from the spec, with the
  arithmetic shown in the file's `_comment` (so a reviewer can re-do
  it on paper), and
- encodes the same values machine-checkably in a per-component
  `_expect` block, so the pipeline cannot pass while wrong even when
  all three platforms are wrong TOGETHER (the failure mode pairwise
  SSIM structurally cannot see — proven by filter:blur() being wrong
  on both natives while every pairwise gate passed).

Solid high-contrast colours, explicit width/height, no text (`_text`)
unless the interaction itself needs it. The page/capture background is
rgb(26,26,46) and is NEVER used as a component colour.

## `_expect` schema (v1 — consumed by the Lane A oracle)

```json
"_expect": { "fill": [r, g, b], "fillTolerance": 2,
             "box": [w, h], "boxTolerance": 1,
             "note": "spec cite" }
```

- `fill` — the DOMINANT non-page-background colour of the capture.
  Components are designed so the colour under test dominates by pixel
  count (children cover most of their parent; the harness name label
  cannot outvote a solid box). `fillTolerance` is per-channel; 2
  covers 8-bit quantisation and platform rounding-mode differences —
  the A/A noise floor is zero, so nothing larger is ever "noise".
- `box` — `[w, h]` of the bounding box of fill-coloured pixels
  (within `fillTolerance` of `fill`). `boxTolerance` conventions used
  here: **1** for axis-aligned integer-edge rects (no AA possible),
  **2** for scaled/arc edges (one AA fringe column per side), **3**
  for 45°-rotated shapes (the extreme corner pixels are the most
  antialiased, so the pure-fill bbox contracts ~1–2px per side).
- `box` is asserted ONLY where it discriminates and where the fill is
  far from the greyscale axis (a grey fill risks absorbing harness
  label antialiasing pixels into the bbox; those components assert
  `fill` only).

## What v1 `_expect` cannot express (needs for v2)

1. **Position.** `box` is `[w,h]` — it cannot see WHERE the box is.
   Transform ORDER for rigid/uniform pairs (translate+rotate,
   scale+translate) moves the centroid without changing the bbox
   size, so half of `transform-list-order.json` is invisible to v1.
   v2 needs `bbox: [x, y, w, h]` in canvas coordinates (which in turn
   needs the oracle to pin the component's layout origin — suggest
   anchoring on a no-transform control component in the same
   fixture).
2. **Probe points.** Gradients have no single fill. v1 fixtures here
   degenerate every gradient to two solid regions and assert the
   dominant one; v2 needs `probes: [{at:[x,y], rgb:[r,g,b]}]` to
   assert the interpolated MIDDLE of a gradient (and with it the
   interpolation colour space, which the hard-stop fixtures
   deliberately sidestep).
3. **Alpha.** `fill` is post-composite RGB. The sepia translucency bug
   (STATUS 2026-08-28) was partly an ALPHA error; v1 can only see it
   via the composite against a known backdrop, which is how
   `opacity-filter-stacking.json` encodes it.

## Fixtures

| file | interaction class |
|---|---|
| `transform-list-order.json` | multi-function transform list composition order (css-transforms-1 §11) |
| `nested-transforms.json` | parent transform × child transform (child moves in the PARENT's frame) |
| `perspective-translatez.json` | perspective() × translateZ() — regression pin for the 2026-08-28 both-natives fix |
| `opacity-blend.json` | mix-blend-mode × opacity on the same element (compositing-1 §5.1) |
| `blend-isolation.json` | mix-blend-mode × isolation on an intermediate wrapper (compositing-1 §3) |
| `filter-chain-order.json` | filter function chain order (filter-effects-1 §2: left-to-right) |
| `opacity-filter-stacking.json` | filter × opacity ordering (filter first, THEN group opacity) |
| `radius-overflow-transform.json` | overflow clip × border-radius × transformed content (clip applies POST-transform, in the clipper's space) |
| `gradient-stops-interp.json` | hard gradient stops at non-midpoint positions (css-images-3 §3.4.1) |

Deliberately absent: a blur-combination fixture. Gaussian tails cannot
be hand-computed to a byte, so a blur `_expect` would either restate a
platform's output (worthless) or carry a tolerance wide enough to pass
while wrong. The 2026-08-28 blur fix is pinned by its unit tests and
by `fixtures/properties/effects/filter-functions.json` instead.

## Expected to FAIL today (red tests for ledgered bugs)

These encode the SPEC value on purpose. They are the red tests that
the open-backlog fixes (docs/STATUS.md "Open backlog from the
2026-08-28 audit sweep") turn green — ledger them, don't be surprised
by them.

| fixture · component | platform | why (open backlog entry) |
|---|---|---|
| `transform-list-order.json` · `TLO_ScaleXRotate` | Android | Compose does not compose the transform list: it accumulates scalars and graphicsLayer applies a fixed T·R·S order, so `scaleX(2) rotate(45deg)` renders as its reverse — box [85,85] instead of [113,57]. (`TLO_RotateScaleX` passes degenerately for the same reason.) |
| `opacity-blend.json` · `OB_Multiply_Op50`, `OB_Screen_Op50` | iOS | mix-blend-mode is applied INSIDE the opacity compositing group, so declaring opacity neutralises the blend — predicted renders (179,204,128) / (77,77,102), the blend-normal values, matching `OB_Normal_Op50_Control` for the multiply pair. |
| `radius-overflow-transform.json` · `ROT_SelfRotate_ClippedChild` | iOS | the overflow clip is applied OUTSIDE the transform, clipping by the un-transformed axis-aligned frame — box [80,60] instead of [99,99]. The two translated-child rows exercise the parent-clips-child path and MAY also be red on iOS; unverified, so listed as possible, not predicted. |

Also wrong today but INVISIBLE to v1 `_expect` (honesty note, not a
pass): Android renders every order-swapped translate pair in
`transform-list-order.json` at the wrong POSITION with the correct
bbox size — e.g. `scale(2) translate(30px,0)` centroid at +30 instead
of +60 (STATUS "Transform order", measured web 156 vs Android 126).
Only the v2 `bbox` field can catch those.

`blend-isolation.json` is a probe, not a prediction: `isolation`
support on the natives' canonical trees is unverified (the legacy
Android list claimed it), so any platform may fail `BI_Isolate`.
