<!-- iOS style-engine: effects/clip -->
<!-- Mirrors src/main/kotlin/app/irmodels/properties/effects/ -->

# effects/clip/ — iOS style engine

Mirrors `converter/src/main/kotlin/app/irmodels/properties/effects/`.
See `CLAUDE.md` → *Per-property contract*.

## Owned properties

- ClipPath (every `<basic-shape>`, `<geometry-box>`, `url()`, `none`)
- ClipPathGeometryBox (reserved — the parser folds it into ClipPath)
- Clip (legacy CSS 2.1 `rect()`, gated to absolute/fixed boxes)
- ClipRule

## Files

| file | role |
|---|---|
| `ClipConfig.swift` | value structs: `ClipShape`, `ClipInsetSides`, `ClipGeometryBox`, `ClipBoxMetrics` |
| `ClipExtractor.swift` | IR → `ClipConfig` (shape wires, clip-rule, legacy clip) |
| `ClipBoxMetricsExtractor.swift` | the `<geometry-box>` keyword + the element's margins / borders / paddings / radii |
| `ClipApplier.swift` | `.clipShape` modifier + `ClipRef` (reference-box selector) |
| `ClipShapes.swift` | the SwiftUI `Shape` implementations, all in reference-box space |
| `ClipReferenceBox.swift` | css-masking-1 §7.1 reference-box resolution (margin/border/padding/content box + corner curves) |
| `SvgPathParser.swift` | SVG 1.1 §8.3 path data → `Path` for `path()` |

## Status

Wave 46 (lane Y4): reference boxes honoured (`circle(…) content-box`,
`polygon(…) margin-box`, the bare keyword form with corner curves),
percent `inset()` sides, full SVG `path()` grammar with the function's
own fill rule. `url(#id)` is still a no-op (no SVG clipPath lookup).
