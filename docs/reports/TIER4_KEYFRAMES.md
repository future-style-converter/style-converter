# Tier 4 — Animation/transition keyframe testing

For each animatable property, snapshot at t=0%, t=50%, t=100% of a 1s
transition. ≥ 0.90 SSIM at midpoint, ≥ 0.95 at endpoints.

| # | property | snapshot (0%/50%/100%) | status | min SSIM | notes |
|---|---|---|---|---|---|
| 1 | opacity | examples/properties/keyframes/opacity.json | passing | - | |
| 2 | transform | examples/properties/keyframes/transform.json | passing | - | |
| 3 | background-color | examples/properties/keyframes/background-color.json | passing | - | |
| 4 | color | examples/properties/keyframes/color.json | passing | - | |
| 5 | width | examples/properties/keyframes/width.json | passing | - | |
| 6 | height | examples/properties/keyframes/height.json | passing | - | |
| 7 | border-radius | examples/properties/keyframes/border-radius.json | passing | - | |
| 8 | padding | examples/properties/keyframes/padding.json | passing | - | |
| 9 | margin | examples/properties/keyframes/margin.json | passing | - | |
| 10 | font-size | examples/properties/keyframes/font-size.json | passing | - | |
| 11 | border-width | examples/properties/keyframes/border-width.json | passing | - | |
| 12 | top | examples/properties/keyframes/top.json | passing | - | |
| 13 | left | examples/properties/keyframes/left.json | passing | - | |
| 14 | rotate | examples/properties/keyframes/rotate.json | passing | - | |
| 15 | scale | examples/properties/keyframes/scale.json | blocked-platform | 0.74 | DIAGNOSED: Android Modifier.scale() clips scaled content to parent bounds while iOS scaleEffect() + web CSS transform extend beyond bounds. At scale 1.1: iOS-Android 0.85 / iOS-web 0.96 / Android-web 0.84 (Android divergent). At scale 1.3: 0.75/0.94/0.74 (worse). iOS+web agree; Android impl gap on overflow handling. |
