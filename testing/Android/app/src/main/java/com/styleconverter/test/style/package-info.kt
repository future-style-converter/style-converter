/**
 * # Style System Architecture
 *
 * This package provides a modular, maintainable architecture for applying
 * CSS properties to Compose Modifiers. The architecture follows a facade pattern
 * where each category of properties has its own module.
 *
 * ## Package Structure
 *
 * ```
 * style/
 * +-- StyleApplier.kt       # Main entry point
 * +-- PropertyTracker.kt    # Debug/coverage tracking
 * +-- core/                 # Shared utilities
 * |   +-- ValueExtractors.kt
 * |   +-- Types.kt
 * |   +-- ModifierExtensions.kt
 * +-- layout/               # Layout properties
 * |   +-- LayoutFacade.kt
 * |   +-- sizing/
 * |   +-- spacing/
 * |   +-- flex/
 * |   +-- grid/
 * |   +-- position/
 * |   +-- display/
 * +-- colors/               # Color properties
 * |   +-- ColorConfig.kt
 * |   +-- ColorExtractor.kt
 * |   +-- ColorApplier.kt
 * +-- borders/              # Border properties
 * |   +-- BordersFacade.kt
 * |   +-- sides/
 * |   +-- radius/
 * +-- effects/              # Visual effects
 * |   +-- EffectsFacade.kt
 * |   +-- filters/
 * |   +-- clip/
 * |   +-- mask/
 * |   +-- blend/
 * +-- transforms/           # Transform properties
 * |   +-- TransformConfig.kt
 * |   +-- TransformExtractor.kt
 * |   +-- TransformApplier.kt
 * +-- typography/           # Typography properties
 * +-- animations/           # Animation properties
 * +-- scroll/               # Scroll behavior
 * +-- interactions/         # User interaction
 * +-- tables/               # Table layout
 * +-- lists/                # List styling
 * +-- columns/              # Multi-column layout
 * +-- counters/             # CSS counters
 * +-- forms/                # Form styling
 * +-- overflow/             # Overflow handling
 * +-- rendering/            # Rendering hints
 * ```
 *
 * ## Usage
 *
 * ### Simple: Apply all properties at once
 * ```kotlin
 * val modifier = StyleApplier.applyProperties(component.properties)
 * Box(modifier = modifier) {
 *     // content
 * }
 * ```
 *
 * ### Advanced: Extract config for inspection
 * ```kotlin
 * val pairs = properties.map { it.type to it.data }
 * val config = StyleApplier.extractConfig(pairs)
 *
 * // Check flex container settings
 * if (config.layout.isFlexContainer) {
 *     // Use Row or Column based on flex direction
 * }
 *
 * // Apply to modifier
 * val modifier = StyleApplier.applyConfig(Modifier, config)
 * ```
 *
 * ### Debugging: Track property coverage
 * ```kotlin
 * // During development
 * properties.forEach { prop ->
 *     if (StyleApplier.isPropertySupported(prop.type)) {
 *         PropertyTracker.markHandled(prop.type)
 *     } else {
 *         PropertyTracker.logUnhandled(prop.type)
 *     }
 * }
 *
 * // View the report
 * val report = PropertyTracker.getReport()
 * println(report)
 * ```
 *
 * ## Design Principles
 *
 * 1. **Separation of Concerns**
 *    - Each category has its own Config, Extractor, and Applier
 *    - Facades aggregate related sub-modules
 *
 * 2. **Extract-then-Apply Pattern**
 *    - First extract configuration from IR JSON
 *    - Then apply configuration to Modifier
 *    - Allows inspection and modification between steps
 *
 * 3. **Compose-Idiomatic Mapping**
 *    - CSS properties map to closest Compose equivalent
 *    - Fallbacks used when exact mapping isn't possible
 *    - Limitations documented in each applier
 *
 * 4. **Type Safety**
 *    - Configs use typed data classes, not raw JSON
 *    - Enums for keyword values
 *    - Nullability indicates optional properties
 *
 * ## Adding New Properties
 *
 * 1. Identify the category (layout, colors, borders, effects, transforms, etc.)
 * 2. Add field to the appropriate Config data class
 * 3. Add extraction logic to the Extractor object
 * 4. Add application logic to the Applier object
 * 5. If new category, create Facade and register in StyleApplier
 * 6. Add to StyleApplier.getSupportedPropertyTypes()
 * 7. Write tests for the new property
 *
 * @see StyleApplier Main entry point
 * @see PropertyTracker Debugging utility
 * @see com.styleconverter.test.style.layout.LayoutFacade Layout properties
 * @see com.styleconverter.test.style.colors.ColorExtractor Color properties
 * @see com.styleconverter.test.style.borders.BordersFacade Border properties
 * @see com.styleconverter.test.style.effects.EffectsFacade Visual effects
 * @see com.styleconverter.test.style.transforms.TransformExtractor Transform properties
 */
package com.styleconverter.test.style
