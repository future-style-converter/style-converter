# CSS to Compose Limitations

This document describes CSS properties and features that have limited or no support in the Compose implementation.

## Platform-Specific Gaps

### Shadows (box-shadow)

| Feature | CSS | Compose | Notes |
|---------|-----|---------|-------|
| Basic shadow | Yes | Partial | Uses elevation instead of offset/blur |
| Offset X/Y | Yes | No | Compose shadows use elevation only |
| Blur radius | Yes | Partial | Mapped to elevation |
| Spread radius | Yes | No | Not supported |
| Inset shadow | Yes | No | Not supported |
| Shadow color | Yes | Partial | Uses default ambient/spot colors |
| Multiple shadows | Yes | No | Only single shadow |

**Compose mapping**: `Modifier.shadow(elevation, shape)` - provides basic drop shadow effect but without fine control over offset, blur, or spread.

### Gradients

| Feature | CSS | Compose | Notes |
|---------|-----|---------|-------|
| Linear gradient | Yes | Yes | Full support |
| Radial gradient | Yes | Partial | Circle shape only |
| Conic/Angular gradient | Yes | Yes | Via sweepGradient |
| Repeating gradients | Yes | No | Not supported |
| Ellipse shape | Yes | No | Radial gradient is always circular |
| Arbitrary color stops | Yes | Partial | Compose requires even distribution |

**Compose mapping**: `Brush.linearGradient`, `Brush.radialGradient`, `Brush.sweepGradient`

### Layout

| Feature | CSS | Compose | Notes |
|---------|-----|---------|-------|
| Flexbox | Yes | Yes | Maps to Row/Column |
| Grid | Yes | Partial | LazyVerticalGrid, limited features |
| position: fixed | Yes | No | Not supported |
| position: sticky | Yes | No | Not supported |
| z-index stacking | Yes | Partial | Modifier.zIndex() works within same parent |
| Multi-column layout | Yes | No | Not directly supported |
| Grid subgrid | Yes | No | Not supported |
| Grid masonry | Yes | No | Not supported |

### Transforms

| Feature | CSS | Compose | Notes |
|---------|-----|---------|-------|
| translate | Yes | Yes | Modifier.offset() |
| rotate | Yes | Yes | Modifier.rotate() |
| scale | Yes | Yes | Modifier.scale() |
| skew | Yes | No | Not supported |
| 3D transforms | Yes | Limited | Only via graphicsLayer rotationX/Y |
| perspective | Yes | Limited | graphicsLayer.cameraDistance |
| transform-origin | Yes | Partial | graphicsLayer transformOrigin |
| matrix | Yes | Yes | graphicsLayer.transformationMatrix |

### Filters

| Feature | CSS | Compose | Notes |
|---------|-----|---------|-------|
| blur | Yes | Yes | Modifier.blur() |
| brightness | Yes | Yes | ColorMatrix |
| contrast | Yes | Yes | ColorMatrix |
| grayscale | Yes | Yes | ColorMatrix |
| hue-rotate | Yes | Yes | ColorMatrix |
| invert | Yes | Partial | ColorMatrix approximation |
| saturate | Yes | Yes | ColorMatrix |
| sepia | Yes | Partial | ColorMatrix approximation |
| drop-shadow | Yes | Partial | Modifier.shadow() |
| backdrop-filter | Yes | No | Not supported (requires window-level compositing) |

### Typography

| Feature | CSS | Compose | Notes |
|---------|-----|---------|-------|
| font-family | Yes | Yes | Via FontFamily |
| font-size | Yes | Yes | Via TextStyle |
| line-height | Yes | Yes | Via TextStyle |
| letter-spacing | Yes | Yes | Via TextStyle |
| text-decoration | Yes | Partial | Underline, line-through, no color/style |
| text-shadow | Yes | No | Not directly supported |
| writing-mode | Yes | Partial | Requires rotation workaround |
| text-orientation | Yes | No | Not supported |
| vertical-align | Yes | Partial | Different model in Compose |
| text-overflow | Yes | Yes | TextOverflow.Ellipsis |
| word-break | Yes | Limited | SoftWrap only |
| hyphenation | Yes | No | Not supported |
| hanging-punctuation | Yes | No | Not supported |
| ruby annotations | Yes | No | Not supported |

### Colors

| Feature | CSS | Compose | Notes |
|---------|-----|---------|-------|
| sRGB colors | Yes | Yes | Full support |
| HSL/HSLA | Yes | Yes | Converted to sRGB |
| OKLCH | Yes | Yes | Converted to sRGB |
| Display-P3 | Yes | No | Gamut clipped to sRGB |
| color-mix() | Yes | No | Requires runtime evaluation |
| currentColor | Yes | No | Requires color context |
| light-dark() | Yes | Partial | Via MaterialTheme |
| CSS variables | Yes | No | Not supported at runtime |

### Masks and Clip Paths

| Feature | CSS | Compose | Notes |
|---------|-----|---------|-------|
| clip-path: circle | Yes | Yes | CircleShape |
| clip-path: ellipse | Yes | Yes | Custom path |
| clip-path: inset | Yes | Yes | RoundedCornerShape variant |
| clip-path: polygon | Yes | Yes | Custom path |
| clip-path: path() | Yes | Yes | Parse SVG path data |
| mask-image | Yes | Partial | Gradient masks only |
| mask-composite | Yes | Partial | Limited blend modes |
| mask-border | Yes | No | Not supported |

### Animations and Transitions

| Feature | CSS | Compose | Notes |
|---------|-----|---------|-------|
| transition | Yes | Yes | animate*AsState |
| @keyframes | Yes | Partial | Via infinite transition |
| animation-delay | Yes | Yes | DelayMillis |
| animation-direction | Yes | Partial | RepeatMode |
| animation-fill-mode | Yes | No | State-based instead |
| animation-iteration-count | Yes | Yes | Iterations/Infinite |
| timing functions | Yes | Yes | Easing curves |
| steps() timing | Yes | No | Not supported |
| scroll-timeline | Yes | No | Not supported |
| view-timeline | Yes | No | Not supported |

### Scrolling

| Feature | CSS | Compose | Notes |
|---------|-----|---------|-------|
| overflow-x/y | Yes | Partial | scrollable() modifier |
| scroll-snap | Yes | No | Not supported |
| scroll-behavior | Yes | Partial | animateScrollTo |
| overscroll-behavior | Yes | Partial | Via nested scroll |
| scrollbar styling | Yes | No | Platform-dependent |

### SVG Properties

| Feature | CSS | Compose | Notes |
|---------|-----|---------|-------|
| fill | Yes | Yes | DrawScope.drawPath |
| stroke | Yes | Yes | DrawScope.drawPath with Stroke |
| stroke-width | Yes | Yes | Stroke.width |
| stroke-dasharray | Yes | Yes | PathEffect.dashPathEffect |
| stroke-linecap | Yes | Yes | StrokeCap |
| stroke-linejoin | Yes | Yes | StrokeJoin |
| fill-rule | Yes | Yes | PathFillType |

### Print and Accessibility

| Feature | CSS | Compose | Notes |
|---------|-----|---------|-------|
| @media print | N/A | N/A | Not applicable to mobile |
| forced-colors | Yes | No | Not supported |
| prefers-reduced-motion | Yes | Partial | Check device settings |
| prefers-color-scheme | Yes | Yes | Via isSystemInDarkTheme() |
| focus-visible | Yes | Partial | Via InteractionSource |

## Value-Specific Limitations

### Lengths

| Unit | CSS | Compose | Notes |
|------|-----|---------|-------|
| px | Yes | Yes | Maps to Dp |
| em/rem | Yes | No | Relative units not supported |
| % | Yes | Partial | fillMaxWidth/Height fractions |
| vw/vh | Yes | No | Requires LocalConfiguration |
| fr | Yes | Partial | Via weight() in flex |
| ch/ex | Yes | No | Font-relative units not supported |
| cqw/cqh | Yes | No | Container queries not supported |

### CSS Functions

| Function | CSS | Compose | Notes |
|----------|-----|---------|-------|
| calc() | Yes | No | Must be pre-computed |
| min()/max() | Yes | No | Must be pre-computed |
| clamp() | Yes | No | Must be pre-computed |
| var() | Yes | No | No CSS variable support |
| env() | Yes | No | Platform insets via WindowInsets |
| url() | Yes | Partial | Image loading required |

## Known Workarounds

### Writing Mode for CJK Text
CSS `writing-mode: vertical-rl` is approximated using `graphicsLayer { rotationZ = 90f }` combined with swapped width/height constraints in a custom Layout.

### Backdrop Blur
Not directly supported. Workaround: Use `RenderEffect.createBlurEffect()` on Android 12+ with the actual backdrop content rendered separately.

### position: fixed
No equivalent in Compose. Workaround: Use a separate composable at the root level with `Modifier.zIndex(Float.MAX_VALUE)`.

### Multiple Shadows
Not supported in single modifier. Workaround: Stack multiple Box composables with individual shadows.

### text-shadow
Not directly supported. Workaround: Draw text twice with offset using Canvas, or use `shadow` effect in Modifier (blur only).

## Recommendations

1. **Design for mobile first** - Avoid relying on features that don't have Compose equivalents
2. **Pre-compute values** - Avoid calc(), var(), and other runtime-dependent values
3. **Use sRGB colors** - Wide-gamut colors will be clipped
4. **Simplify shadows** - Use elevation-based shadows instead of complex box-shadows
5. **Avoid scroll-snap** - Design alternative UX for snap points
6. **Test animations** - CSS keyframes may behave differently than Compose transitions
