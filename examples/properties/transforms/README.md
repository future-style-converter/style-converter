# Transforms fixtures (Phase 8)

CSS fixtures exercising every value variant accepted by the transform parsers
in `src/main/kotlin/app/parsing/css/properties/longhands/transforms/`.

Run via:

```bash
./gradlew :converter:run --args="convert --from css --to ir -i examples/properties/transforms/<file>.json -o /tmp/p8"
```

All 10 fixtures convert with `(0 generic)` on every component.

## Coverage map

| Fixture | Components | Properties / variants |
|---|---:|---|
| transform-functions.json | 28 | `transform`: none, translateX/Y/Z, translate(1), translate(2), translate3d, rotate(deg/turn/rad), rotateX/Y/Z, rotate3d, scale(1), scale(2), scaleX/Y/Z, scale3d, skew(2), skewX, skewY, matrix, matrix3d, perspective(+rotate), 2-chain, 4-chain |
| rotate-longhand.json | 10 | `rotate`: none, angle (deg/turn/rad/grad), `x <angle>`, `y <angle>`, `z <angle>`, `<x> <y> <z> <angle>`, negative angle |
| scale-longhand.json | 8 | `scale`: none, uniform number, uniform percentage, shrink, two-axis numbers, two-axis percentages, three-axis, negative |
| translate-longhand.json | 8 | `translate`: none, 1-axis px, 1-axis %, 2-axis px, 2-axis %, mixed px/%, 3-axis with z-length, negative |
| transform-origin.json | 9 | center, `50% 50%`, `top left`, `top right`, `bottom center`, length pair, length+% pair, 3-value (with z-length), single keyword (`left`) |
| transform-box.json | 5 | content-box / border-box / fill-box / stroke-box / view-box |
| transform-style.json | 2 | flat / preserve-3d |
| perspective.json | 4 | none / 500px / 1000px / 0 |
| perspective-origin.json | 6 | center, center center, top left, bottom right, percentage pair, length pair |
| backface-visibility.json | 2 | visible / hidden |

## Totals

- 10 fixture files
- 82 components total
- 0 `generic` rows across the entire suite

## Parser gaps / notes for platform-agent handoff

All properties listed in the scope convert cleanly. Flagged behaviours:

- `RotatePropertyParser` accepts a 2-part form `x 45deg` / `y 45deg` /
  `z 45deg` and the 4-part form `1 1 0 45deg`. It does **not** support an
  axis-vector form without the trailing angle or pure keyword `x` alone.
- `ScalePropertyParser` accepts percentages (`150%` → `1.5`). Negative values
  are accepted (e.g. `-1 1` for horizontal flip) but produce a mirrored box.
- `TranslatePropertyParser` rejects `<length> <length> <length>` for the
  third component if it's a percentage — the z-component is length-only per
  CSS spec.
- `TransformOriginPropertyParser` falls back to `Raw(string)` for anything
  containing `calc(`/`var(`. The fixture avoids those deliberately.
- `PerspectivePropertyParser` accepts `0` as an alias for `0px`. No `none`
  variant check is needed — it's handled explicitly.
- `TransformPropertyParser` supports the full MDN function set
  (translate/scale/rotate/skew/matrix/perspective in 2D and 3D). Expressions
  (`var`/`calc` etc.) become an opaque `Expression(string)` IR node — not
  fixtured here.
- `matrix3d(16 vals)` is comma-separated inside the function; the fixture
  uses the spec-required identity-ish matrix with translation.

## Properties not directly fixtured (intentional)

None — all 10 transform-category properties plus the `FilterProperty` are
fixtured above. `FilterProperty` lives at `irmodels/properties/color/` by
historical accident but is treated as effects in Phase 8 and is fixtured under
`examples/properties/effects/filter-functions.json` and `filter-url.json`.
