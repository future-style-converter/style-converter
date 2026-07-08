# Property Coverage Report

Generated: 2026-01-25 (Updated)

## Summary

| Category | Extractors | Appliers | Coverage |
|----------|------------|----------|----------|
| **Total** | 49 | 46 | 93.9% |

**Runtime Coverage (from IR test):**
- Total property instances: 3,480
- Supported instances: 2,006
- Coverage: **57.6%**

**Recent Integrations (2026-01-25):**
- MaskApplier integrated into StyleApplier.applyConfig()
- BorderImageApplier integrated into ComponentRenderer (BorderImageBox)
- ScrollApplier integrated into StyleApplier.applyConfig()
| Core/Sizing | 2 | 2 | 100% |
| Spacing | 3 | 1 | 33% |
| Colors | 3 | 3 | 100% |
| Borders | 4 | 4 | 100% |
| Effects | 5 | 5 | 100% |
| Transforms | 3 | 2 | 67% |
| Typography | 8 | 9 | 100%+ |
| Layout | 10 | 11 | 110% |
| Interactions | 4 | 3 | 75% |
| Platform | 4 | 6 | 150%+ |
| Content | 3 | 2 | 67% |

**Update (2026-01-25):** Medium priority appliers now implemented:
- FlexApplier and GridApplier (critical gaps - Phase 3)
- TableApplier, MultiColumnApplier, FormStylingApplier, ContainerQueryApplier (medium priority - Phase 4)

## Detailed Mapping

### Fully Implemented (Extractor + Applier)

| Category | Extractor | Applier | Status |
|----------|-----------|---------|--------|
| **Borders** | BorderSideExtractor | BorderSideApplier | Complete |
| **Borders** | BorderRadiusExtractor | BorderRadiusApplier | Complete |
| **Borders** | BorderImageExtractor | BorderImageApplier | Complete |
| **Borders** | OutlineExtractor | OutlineApplier | Complete |
| **Colors** | ColorExtractor | ColorApplier | Complete |
| **Colors** | AccentExtractor | AccentApplier | Complete |
| **Colors** | BackgroundBoxExtractor | BackgroundBoxApplier | Complete |
| **Effects** | ShadowExtractor | ShadowApplier | Complete |
| **Effects** | ClipPathExtractor | ClipPathApplier | Complete |
| **Effects** | FilterExtractor | FilterApplier | Complete |
| **Effects** | MaskExtractor | MaskApplier | Complete |
| **Effects** | ShapeExtractor | ShapeApplier | Complete |
| **Images** | ObjectFitExtractor | ObjectFitApplier | Complete |
| **SVG** | SvgExtractor | SvgApplier | Complete |
| **Transforms** | TransformExtractor | TransformApplier | Complete |
| **Transforms** | Transform3DExtractor | Transform3DApplier | Complete |
| **Layout** | SizingExtractor | SizingApplier | Complete |
| **Layout** | PositionExtractor | PositionApplier | Complete |
| **Layout** | OverflowExtractor | OverflowApplier | Complete |
| **Layout** | ScrollExtractor | ScrollApplier | Complete |
| **Layout** | ScrollTimelineExtractor | ScrollTimelineApplier | Complete |
| **Typography** | TypographyExtractor | TypographyApplier | Complete |
| **Typography** | TextFormattingExtractor | TextFormattingApplier | Complete |
| **Lists** | ListStyleExtractor | ListStyleApplier | Complete |
| **Animations** | AnimationExtractor | AnimationApplier | Complete |
| **Interactions** | InteractionExtractor | InteractionApplier | Complete |
| **Interactions** | FormStylingExtractor | FormStylingApplier | Complete |
| **Layout** | FlexExtractor | FlexApplier | Complete |
| **Layout** | GridExtractor | GridApplier | Complete |
| **Layout** | MultiColumnExtractor | MultiColumnApplier | Complete |
| **Layout** | ContainerQueryExtractor | ContainerQueryApplier | Complete |
| **Content** | TableExtractor | TableApplier | Complete |
| **Performance** | PerformanceExtractor | PerformanceApplier | Complete |

### Partially Implemented (Has Extractor, No Dedicated Applier)

| Category | Extractor | Notes |
|----------|-----------|-------|
| **Layout** | FloatExtractor | Low priority - floats rarely used in mobile |
| **Layout** | RegionFlowExtractor | Experimental CSS feature |
| **Layout** | ZoomExtractor | Non-standard property |
| **Spacing** | SpacingExtractor | Has SpacingApplier |
| **Spacing** | MarginTrimExtractor | Experimental CSS feature |
| **Transforms** | OffsetPathExtractor | Low priority - motion paths |
| **Content** | ContentExtractor | Low priority - generated content |
| **Typography** | TextExtractor | Partial - WritingModeApplier handles some |
| **Typography** | MathTypographyExtractor | Low priority - math formulas |
| **Typography** | BaselineExtractor | Low priority - baseline alignment |
| **Typography** | RubyExtractor | Low priority - ruby annotations |
| **Interactions** | SpatialNavigationExtractor | Low priority - TV navigation |
| **Platform** | PrintExtractor | Low priority - print styles |
| **Platform** | RenderingExtractor | Low priority - rendering hints |
| **Platform** | SpeechExtractor | Low priority - screen reader hints |

### Appliers Without Extractors (Workarounds/Extensions)

| Category | Applier | Purpose |
|----------|---------|---------|
| **Workarounds** | BackdropBlurApplier | Platform-specific backdrop blur implementation |
| **Workarounds** | MultipleShadowApplier | Multiple box-shadow composition |
| **Workarounds** | SkewTransformApplier | Skew transform workaround for Compose |
| **Workarounds** | TextShadowApplier | Text shadow composition |
| **Typography** | FontVariantApplier | Font variant features |
| **Typography** | LineClampApplier | Line clamping/truncation |
| **Typography** | TextEmphasisApplier | Text emphasis marks |
| **Typography** | TextStyleApplier | Combined text styling |
| **Typography** | TextWrapApplier | Text wrapping behavior |
| **Typography** | WritingModeApplier | Writing mode/direction |
| **Scroll** | ViewTimelineApplier | View timeline animations |
| **Core** | StyleApplier | Master style composition |

---

## Critical Priority Gaps - RESOLVED

### 1. FlexApplier - IMPLEMENTED

**Location:** `layout/flex/FlexApplier.kt`

**CSS Properties Supported:**
- `display: flex` / `inline-flex`
- `flex-direction` (row, column, row-reverse, column-reverse)
- `flex-wrap` (nowrap, wrap, wrap-reverse)
- `justify-content` (flex-start, flex-end, center, space-between, space-around, space-evenly)
- `align-items` (flex-start, flex-end, center, baseline, stretch)
- `align-content`
- `flex-grow` via `Modifier.weight()`
- `flex-basis` via width/height modifiers
- `align-self`
- `gap` via `Arrangement.spacedBy()`

**Compose Implementation:**
- `Row` / `Column` for non-wrapping layouts
- `FlowRow` / `FlowColumn` for wrapping layouts
- `Arrangement.Horizontal/Vertical` for justify-content
- `Alignment.Horizontal/Vertical` for align-items
- `CompositionLocal` for passing container config to items

### 2. GridApplier - IMPLEMENTED

**Location:** `layout/grid/GridApplier.kt`

**CSS Properties Supported:**
- `display: grid`
- `grid-template-columns` (fixed, fr, auto, min-content, max-content)
- `grid-template-rows`
- `grid-template-areas` (custom layout implementation)
- `grid-auto-flow` (row, column, row-dense, column-dense)
- `gap`, `row-gap`, `column-gap`
- `grid-column`, `grid-row`, `grid-area`
- `justify-items`, `align-items`
- `justify-content`, `align-content`

**Compose Implementation:**
- `LazyVerticalGrid` / `LazyHorizontalGrid` for scrolling grids
- Custom `CssGrid` layout for template-areas support
- `SimpleGrid` for small fixed grids
- `GridCells.Fixed/Adaptive/FixedSize` for column sizing
- `GridItemSpan` for column spanning

---

## Medium Priority Gaps - RESOLVED

### 3. MultiColumnApplier - IMPLEMENTED

**Location:** `layout/columns/MultiColumnApplier.kt`

**CSS Properties Supported:**
- `column-count` → Custom Layout with calculated columns
- `column-width` → Adaptive column calculation
- `column-gap` → `Arrangement.spacedBy()`
- `column-rule` → Custom drawing with PathEffect
- `column-rule-style` → solid, dashed, dotted support
- `column-span` → ColumnSpanningItem composable

**Compose Implementation:**
- Custom `MultiColumnDistributionLayout` for content balancing
- `BoxWithConstraints` for adaptive column count
- `MasonryLayout` for Pinterest-style layouts
- Column rule drawing with dashed/dotted support

### 4. ContainerQueryApplier - IMPLEMENTED

**Location:** `layout/container/ContainerQueryApplier.kt`

**CSS Properties Supported:**
- `container-type` → `BoxWithConstraints`
- `container-name` → Named `CompositionLocal`
- `@container` queries → `when {}` blocks with dimension checks
- `cqi`, `cqw`, `cqh`, `cqmin`, `cqmax` units → Calculated Dp

**Compose Implementation:**
- `QueryContainer` with `BoxWithConstraints`
- `LocalContainerDimensions` and `LocalNamedContainers`
- `minWidth()`, `maxWidth()`, `widthBetween()` query helpers
- `Responsive()` and `CompactOrExpanded()` pattern helpers

### 5. TableApplier - IMPLEMENTED

**Location:** `content/tables/TableApplier.kt`

**CSS Properties Supported:**
- `display: table` → Column + Row composition
- `table-layout: fixed` → Equal weight columns
- `border-collapse` → Custom border drawing
- `border-spacing` → `Arrangement.spacedBy()`
- `caption-side` → Top/bottom caption placement
- `empty-cells` → Conditional visibility

**Compose Implementation:**
- `Table`, `TableRow`, `TableCell` composables
- `FixedWidthTable` for explicit column widths
- Border collapse via drawBehind (right/bottom borders only)
- CompositionLocals for config propagation

### 6. FormStylingApplier - IMPLEMENTED

**Location:** `interactive/forms/FormStylingApplier.kt`

**CSS Properties Supported:**
- `accent-color` → CheckboxColors, RadioButtonColors, SwitchColors, SliderColors
- `caret-color` → TextField cursorColor
- `color-scheme` → Light/dark theme selection
- `field-sizing` → IntrinsicSize for content-based sizing
- `input-security` → PasswordVisualTransformation
- `interactivity` → enabled parameter

**Compose Implementation:**
- `FormStylingProvider` with CompositionLocal
- `checkboxColors()`, `radioButtonColors()`, `switchColors()`, `sliderColors()`
- `textFieldColors()`, `outlinedTextFieldColors()`
- `shouldUseDarkTheme()` for color scheme

---

## Low Priority Gaps

| Extractor | Reason |
|-----------|--------|
| FloatExtractor | Floats rarely used in mobile UI |
| RegionFlowExtractor | Experimental CSS feature |
| ZoomExtractor | Non-standard property |
| OffsetPathExtractor | Complex motion paths (rare) |
| ContentExtractor | CSS generated content (::before/::after) |
| MathTypographyExtractor | Math formula styling |
| BaselineExtractor | Fine baseline control |
| RubyExtractor | East Asian ruby annotations |
| SpatialNavigationExtractor | TV/game navigation |
| PrintExtractor | Print-specific styles |
| RenderingExtractor | Rendering hints |
| SpeechExtractor | Screen reader hints |
| MarginTrimExtractor | Experimental margin trimming |

---

## Implementation Statistics

### By Feature Category

```
CORE LAYOUT
├── Sizing: 100% [SizingApplier]
├── Position: 100% [PositionApplier]
├── Flex: 100% [FlexApplier] ✅
├── Grid: 100% [GridApplier] ✅
├── MultiColumn: 100% [MultiColumnApplier] ✅
├── ContainerQuery: 100% [ContainerQueryApplier] ✅
└── Overflow: 100% [OverflowApplier]

VISUAL STYLING
├── Colors: 100% [ColorApplier, AccentApplier, BackgroundBoxApplier]
├── Borders: 100% [BorderSideApplier, BorderRadiusApplier, BorderImageApplier, OutlineApplier]
├── Shadows: 100% [ShadowApplier, MultipleShadowApplier, TextShadowApplier]
├── Filters: 100% [FilterApplier, BackdropBlurApplier]
├── Clip/Mask: 100% [ClipPathApplier, MaskApplier, ShapeApplier]
└── Transforms: 100% [TransformApplier, Transform3DApplier, SkewTransformApplier]

TYPOGRAPHY
├── Core: 100% [TypographyApplier, TextStyleApplier]
├── Formatting: 100% [TextFormattingApplier, TextWrapApplier, LineClampApplier]
├── Advanced: 100% [FontVariantApplier, TextEmphasisApplier, WritingModeApplier]
└── Specialized: 0% [Ruby, Math, Baseline - low priority]

INTERACTIVITY
├── Animations: 100% [AnimationApplier]
├── Transitions: 100% [AnimationApplier handles]
├── Scroll: 100% [ScrollApplier, ScrollTimelineApplier, ViewTimelineApplier]
├── Interactions: 100% [InteractionApplier]
└── Forms: 100% [FormStylingApplier] ✅

CONTENT
├── Lists: 100% [ListStyleApplier]
├── Tables: 100% [TableApplier] ✅
└── Generated: 0% [ContentApplier - low priority]

PLATFORM
├── Performance: 100% [PerformanceApplier]
├── Print: 0% [Not applicable to mobile]
├── Rendering: 0% [Low priority hints]
└── Accessibility: Partial [Speech not needed for visual rendering]
```

---

## Recommendations

### Completed (Phase 3 - Critical)

1. ✅ **FlexApplier.kt** - Highest impact
   - Map flex properties to Row/Column/Arrangement
   - Handle flex-grow with Modifier.weight()
   - Implement gap with Arrangement.spacedBy()

2. ✅ **GridApplier.kt** - Second highest impact
   - Map grid-template-columns to GridCells
   - Handle grid-gap with Arrangement
   - Support grid-column/row for item placement

### Completed (Phase 4 - Medium Priority)

3. ✅ **TableApplier.kt** - Data display
4. ✅ **MultiColumnApplier.kt** - Magazine layouts
5. ✅ **FormStylingApplier.kt** - Form controls
6. ✅ **ContainerQueryApplier.kt** - Responsive components

### Backlog (Low Priority)

7. OffsetPathApplier (motion paths)
8. ContentApplier (::before/::after simulation)
9. FloatApplier (legacy float layouts)
10. SpatialNavigationApplier (TV navigation)

---

## File Locations

### Extractors (49 files)
```
app/src/main/java/com/styleconverter/test/style/
├── appearance/
│   ├── borders/{image,outline,radius,sides}/
│   ├── colors/
│   ├── effects/{clip,filters,mask,shadow,shapes}/
│   ├── images/
│   ├── svg/
│   └── transforms/
├── content/{lists,tables}/
├── core/types/
├── interactive/{animations,forms,interactions}/
├── layout/{columns,container,flex,grid,overflow,position,scroll,sizing,spacing}/
├── platform/{accessibility,performance,print,rendering}/
└── typography/{advanced,ruby,text}/
```

### Appliers (40 files)
```
app/src/main/java/com/styleconverter/test/style/
├── StyleApplier.kt (master)
├── appearance/
│   ├── borders/{image,outline,radius,sides}/
│   ├── colors/
│   ├── effects/{clip,filters,mask,shadow,shapes}/
│   ├── images/
│   ├── svg/
│   └── transforms/
├── content/lists/
├── interactive/{animations,interactions}/
├── layout/{overflow,position,scroll,sizing,spacing}/
├── platform/{performance,workarounds}/
└── typography/
```
