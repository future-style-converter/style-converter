# IRModels - Intermediate Representation Models

## Overview

The `irmodels` folder contains a comprehensive type-safe representation of CSS properties for the Style-Converter project. This Intermediate Representation (IR) serves as a platform-agnostic data model that can be converted to/from CSS, Jetpack Compose, and SwiftUI.

## Architecture

### Folder Structure

```
irmodels/
├── IRProperty.kt           # Base interface for all properties
├── ValueTypes.kt           # Common value types (IRLength, IRColor, IRAngle, etc.)
├── platform/               # Platform-specific properties
└── properties/             # All CSS property implementations (170 files)
    ├── animations/         # Animation properties (12 files)
    ├── background/         # Background properties (8 files)
    ├── borders/            # Border & outline properties (26 files)
    ├── color/              # Color & blend mode properties (7 files)
    ├── columns/            # Multi-column layout properties (6 files)
    ├── content/            # Content properties (1 file)
    ├── effects/            # Effects & visibility properties (7 files)
    ├── images/             # Image & object properties (2 files)
    ├── interactions/       # Interaction properties (6 files)
    ├── layout/             # Layout properties (34 files)
    │   ├── flexbox/        # Flexbox layout (13 files)
    │   ├── grid/           # Grid layout (13 files)
    │   └── position/       # Positioning & float (8 files)
    ├── lists/              # List style properties (3 files)
    ├── performance/        # Performance hint properties (3 files)
    ├── scrolling/          # Scrolling & snap properties (5 files)
    ├── sizing/             # Sizing properties (4 files)
    ├── spacing/            # Spacing & sizing properties (13 files)
    ├── table/              # Table layout properties (4 files)
    ├── transforms/         # Transform properties (2 files)
    └── typography/         # Typography properties (29 files)
```

### Design Principles

#### 1. **ONE Property Per File**
Each CSS property gets its own dedicated Kotlin file. No exceptions.

✅ **Correct**:
- `FontSizeProperty.kt` handles ONLY `font-size`
- `ColorProperty.kt` handles ONLY `color`
- `PaddingProperty.kt` handles ONLY `padding`

❌ **Incorrect**:
- ~~`FontProperties.kt`~~ containing multiple font properties
- ~~`TypographyProperties.kt`~~ containing all typography properties

#### 2. **Pure Data Representation**
IR classes contain ONLY data structures. No conversion logic, no business logic.

✅ **Correct**:
```kotlin
@Serializable
data class FontSizeProperty(
    val size: FontSize
) : IRProperty {
    override val propertyName = "font-size"
}
```

❌ **Incorrect**:
```kotlin
data class FontSizeProperty(...) : IRProperty {
    fun toCompose(): String { ... }  // ❌ NO conversion methods
    fun toSwiftUI(): String { ... }  // ❌ NO business logic
}
```

#### 3. **Precise Type Constraints**
Use enums and sealed interfaces to enforce only valid CSS values.

✅ **Correct**:
```kotlin
enum class TextAlignment {
    START, END, LEFT, RIGHT, CENTER, JUSTIFY, MATCH_PARENT
}
```

❌ **Incorrect**:
```kotlin
data class TextAlign(val value: String)  // ❌ Accepts ANY string
```

#### 4. **Maximum 100 Lines Per File**
Forces modular, maintainable code. Current average: ~42 lines/file.

#### 5. **Fully Serializable**
All types must be annotated with `@Serializable` for JSON persistence.

```kotlin
@Serializable  // ✅ Required
data class FontSizeProperty(...) : IRProperty
```

#### 6. **Organized By Category**
Properties are grouped in logical folders (typography, layout, colors, etc.).

## Base Types

### IRProperty
Base interface that all property classes implement. Defines the `propertyName` field.

### Value Types (ValueTypes.kt)
- **IRLength**: Length values with units (px, em, rem, %, vw, vh, etc.)
- **IRColor**: Colors in various formats (hex, rgb, hsl, named, currentColor, transparent)
- **IRAngle**: Angle values (deg, rad, grad, turn)
- **IRTime**: Time durations (s, ms)
- **IRPercentage**: Percentage values
- **IRNumber**: Unitless numbers
- **IRKeyword**: Generic keyword values
- **IRUrl**: URL references

## Property Categories

### Typography (29 properties)
Font, text, and writing system properties.

**Core**: `font-family`, `font-size`, `font-weight`, `font-style`
**Text**: `text-align`, `text-decoration-*`, `text-transform`, `text-overflow`
**Spacing**: `line-height`, `letter-spacing`, `word-spacing`, `text-indent`
**Advanced**: `font-variant-numeric`, `font-feature-settings`, `writing-mode`, `direction`

### Layout - Flexbox (13 properties)
Flexible box layout system.

`display`, `flex-direction`, `flex-wrap`, `justify-content`, `align-items`, `align-content`, `align-self`, `flex-grow`, `flex-shrink`, `flex-basis`, `order`

### Layout - Grid (13 properties)
CSS Grid layout system.

`grid-template-columns`, `grid-template-rows`, `grid-template-areas`, `grid-auto-*`, `grid-column-*`, `grid-row-*`, `grid-area`, `justify-items`, `justify-self`

### Layout - Position (8 properties)
Element positioning and floating.

`position`, `top`, `right`, `bottom`, `left`, `z-index`, `float`, `clear`

### Spacing & Sizing (17 properties)
Dimensions, padding, margin, and gaps.

**Sizing**: `width`, `height`, `min-*`, `max-*`, `block-size`, `inline-size`
**Spacing**: `padding`, `margin`, `gap`, `row-gap`, `column-gap`
**Special**: `aspect-ratio`

### Borders & Outlines (26 properties)
Border and outline styling.

**Core**: `border-width/style/color/radius`, `border-image`
**Directional**: `border-top/right/bottom/left-*` (width, style, color)
**Corners**: `border-*-radius` (top-left, top-right, bottom-left, bottom-right)
**Outline**: `outline-width/style/color/offset`
**Shadows**: `box-shadow`

### Background (8 properties)
Background images, gradients, and styling.

`background-color`, `background-image`, `background-size`, `background-position`, `background-repeat`, `background-attachment`, `background-clip`, `background-origin`

### Colors & Effects (7 properties)
Colors, opacity, and blend modes.

`color`, `background-color`, `opacity`, `mix-blend-mode`, `background-blend-mode`, `filter`, `backdrop-filter`

### Animations & Transitions (12 properties)
Animation and transition properties.

**Animations**: `animation-name/duration/timing-function/delay/iteration-count/direction/fill-mode/play-state`
**Transitions**: `transition-property/duration/timing-function/delay`

### Transforms (2 properties)
2D and 3D transformations.

`transform` (with 20+ transform functions), `transform-origin`

### Effects & Visibility (7 properties)
Visibility, overflow, and clipping.

`visibility`, `overflow`, `overflow-x/y`, `clip-path`, `mask`, `backdrop-filter`

### Interactions (6 properties)
User interaction properties.

`cursor`, `pointer-events`, `user-select`, `touch-action`, `scroll-behavior`, `resize`

### Scrolling & Snap Points (5 properties)
Scroll behavior and snap points.

`overflow-anchor`, `scroll-snap-type`, `scroll-snap-align`, `scroll-snap-stop`, `overscroll-behavior`

### Content & Lists (4 properties)
Generated content and list styling.

`content`, `list-style-type`, `list-style-position`, `list-style-image`

### Table Layout (4 properties)
Table-specific properties.

`table-layout`, `border-collapse`, `border-spacing`, `caption-side`

### Multi-Column Layout (6 properties)
Multi-column text layout.

`column-count`, `column-width`, `column-gap`, `column-rule-width/style/color`

### Images & Objects (2 properties)
Image and replaced element properties.

`object-fit`, `object-position`

### Performance & Optimization (3 properties)
Performance hints.

`will-change`, `contain`, `isolation`

## Usage Example

### Creating an IR Property

```kotlin
import app.irmodels.*
import app.irmodels.properties.typography.FontSizeProperty

// Create a font-size property with pixel value
val fontSize = FontSizeProperty(
    size = FontSizeProperty.FontSize.Length(
        IRLength(16.0, IRLength.LengthUnit.PX)
    )
)

// Property name: "font-size"
println(fontSize.propertyName)  // "font-size"
```

### Pattern Matching on Sealed Interfaces

```kotlin
when (val size = fontSize.size) {
    is FontSizeProperty.FontSize.Length -> {
        println("Length: ${size.value.value}${size.value.unit}")
    }
    is FontSizeProperty.FontSize.Percentage -> {
        println("Percentage: ${size.value.value}%")
    }
    is FontSizeProperty.FontSize.Keyword -> {
        println("Keyword: ${size.value}")
    }
    is FontSizeProperty.FontSize.Relative -> {
        println("Relative: ${size.value}")
    }
}
```

## Implementation Guidelines

### For New Properties

1. **Choose the correct folder** based on property category
2. **Create one file per property** named `{PropertyName}Property.kt`
3. **Follow the naming convention**: PascalCase for class, kebab-case for `propertyName`
4. **Use sealed interfaces** for values with multiple type options
5. **Use enums** for fixed keyword sets
6. **Keep it under 100 lines** - if it's longer, reconsider the design
7. **Add comprehensive KDoc comments** including:
   - CSS property description
   - Syntax documentation
   - Value types explanation
   - Usage examples
   - Platform support notes
   - MDN link reference

### Example Template

```kotlin
package app.irmodels.properties.category

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `property-name` property.
 *
 * ## CSS Property
 * **Syntax**: `property-name: <value-type>`
 *
 * ## Description
 * Brief description of what this property does.
 *
 * ## Value Types
 * - **Type1**: Description
 * - **Type2**: Description
 *
 * ## Examples
 * ```kotlin
 * // Example usage
 * PropertyNameProperty(...)
 * ```
 *
 * ## Platform Support
 * - **CSS**: Support details
 * - **Jetpack Compose**: Equivalent
 * - **SwiftUI**: Equivalent
 *
 * @property value The property value
 * @see [MDN property-name](https://developer.mozilla.org/...)
 */
@Serializable
data class PropertyNameProperty(
    val value: ValueType
) : IRProperty {
    override val propertyName = "property-name"

    @Serializable
    sealed interface ValueType {
        // Value type implementations
    }
}
```

## Statistics

- **Total Properties**: 170 files
- **Total Lines**: ~7,000 (estimated)
- **Average File Size**: ~42 lines
- **Coverage**: ~99% of commonly-used CSS properties
- **Categories**: 19 major categories

## Property Reference

For cross-platform property mappings and conversion details, see the `` folder in the project root.

## Next Steps

1. **Parsers**: Implement parsers to convert CSS → IR
2. **Generators**: Implement generators to convert IR → Compose/SwiftUI/CSS
3. **Validation**: Add property value validation logic
4. **Tests**: Create comprehensive test suite
5. **Documentation**: Generate API documentation with Dokka

## Contributing

When adding new properties:

1. Check `` for property specifications
2. Follow the ONE property per file rule
3. Use precise type constraints (enums/sealed interfaces)
4. Keep files under 100 lines
5. Add comprehensive comments
6. Update this README with the new property
7. Update `docs/IRMODELS_PROGRESS.md` with progress

## References

- [CSS Specifications (W3C)](https://www.w3.org/Style/CSS/specs.en.html)
- [MDN CSS Reference](https://developer.mozilla.org/en-US/docs/Web/CSS/Reference)
- [Jetpack Compose Modifiers](https://developer.android.com/jetpack/compose/modifiers-list)
- [SwiftUI View Modifiers](https://developer.apple.com/documentation/swiftui/view-modifiers)
