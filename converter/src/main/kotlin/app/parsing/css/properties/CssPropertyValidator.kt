package app.parsing.css.properties

/**
 * Validates CSS property names against the official CSS specification.
 *
 * ## Purpose
 * Filters out invalid or custom property names before parsing to avoid
 * creating invalid IR models. This is the first step in the parsing pipeline.
 *
 * ## Validation Rules
 * 1. Standard CSS properties (420+ properties from CSS specs)
 * 2. Vendor-prefixed properties (-webkit-, -moz-, -ms-, -o-)
 * 3. Case-insensitive matching
 *
 * ## Usage
 * ```kotlin
 * CssPropertyValidator.isValidProperty("background-color")  // true
 * CssPropertyValidator.isValidProperty("backgroundColor")   // true (case-insensitive)
 * CssPropertyValidator.isValidProperty("-webkit-transform") // true (vendor prefix)
 * CssPropertyValidator.isValidProperty("invalid-prop")      // false
 * ```
 *
 * ## Categories Covered
 * - Layout & Display (30+ properties)
 * - Typography (60+ properties)
 * - Colors & Backgrounds (20+ properties)
 * - Borders & Outlines (40+ properties)
 * - Flexbox & Grid (50+ properties)
 * - Animations & Transitions (30+ properties)
 * - Transforms (10+ properties)
 * - SVG (30+ properties)
 * - And more...
 *
 * @see PropertiesParser for how validation fits in the parsing pipeline
 */
object CssPropertyValidator {

    // ===== STANDARD CSS PROPERTIES =====
    private val standardProperties = setOf(
        // Layout & Display
        "display", "position", "top", "right", "bottom", "left",
        "float", "clear", "z-index", "box-sizing",

        // Sizing
        "width", "height", "min-width", "min-height", "max-width", "max-height",
        "inline-size", "block-size", "min-inline-size", "min-block-size",
        "max-inline-size", "max-block-size",

        // Spacing
        "margin", "margin-top", "margin-right", "margin-bottom", "margin-left",
        "margin-block", "margin-block-start", "margin-block-end",
        "margin-inline", "margin-inline-start", "margin-inline-end",
        "padding", "padding-top", "padding-right", "padding-bottom", "padding-left",
        "padding-block", "padding-block-start", "padding-block-end",
        "padding-inline", "padding-inline-start", "padding-inline-end",
        "margin-trim",

        // Flexbox
        "flex", "flex-direction", "flex-wrap", "flex-flow",
        "flex-grow", "flex-shrink", "flex-basis",
        "justify-content", "align-items", "align-self", "align-content",
        "order", "gap", "row-gap", "column-gap",
        "justify-items", "justify-self",
        "align-tracks", "justify-tracks",

        // Grid
        "grid", "grid-template", "grid-template-rows", "grid-template-columns",
        "grid-template-areas", "grid-auto-rows", "grid-auto-columns", "grid-auto-flow",
        "grid-row", "grid-row-start", "grid-row-end",
        "grid-column", "grid-column-start", "grid-column-end",
        "grid-area", "grid-auto-track",
        "place-items", "place-content", "place-self",
        "masonry-auto-flow",

        // Positioning
        "inset", "inset-block", "inset-block-start", "inset-block-end",
        "inset-inline", "inset-inline-start", "inset-inline-end",
        "inset-area",

        // Borders
        "border", "border-width", "border-style", "border-color",
        "border-top", "border-top-width", "border-top-style", "border-top-color",
        "border-right", "border-right-width", "border-right-style", "border-right-color",
        "border-bottom", "border-bottom-width", "border-bottom-style", "border-bottom-color",
        "border-left", "border-left-width", "border-left-style", "border-left-color",
        "border-block", "border-block-start", "border-block-end",
        "border-block-width", "border-block-style", "border-block-color",
        "border-block-start-width", "border-block-start-style", "border-block-start-color",
        "border-block-end-width", "border-block-end-style", "border-block-end-color",
        "border-inline", "border-inline-start", "border-inline-end",
        "border-inline-width", "border-inline-style", "border-inline-color",
        "border-inline-start-width", "border-inline-start-style", "border-inline-start-color",
        "border-inline-end-width", "border-inline-end-style", "border-inline-end-color",
        "border-radius", "border-top-left-radius", "border-top-right-radius",
        "border-bottom-left-radius", "border-bottom-right-radius",
        "border-start-start-radius", "border-start-end-radius",
        "border-end-start-radius", "border-end-end-radius",
        "border-image", "border-image-source", "border-image-slice",
        "border-image-width", "border-image-outset", "border-image-repeat",
        "border-collapse", "border-spacing", "border-boundary",

        // Outline
        "outline", "outline-width", "outline-style", "outline-color", "outline-offset",

        // Background
        "background", "background-color", "background-image", "background-position",
        "background-position-x", "background-position-y", "background-size",
        // Logical-axis equivalents of background-position-x/y. Parsers + IR
        // models exist (see BackgroundPositionBlockProperty / Inline +
        // matching parsers in PropertyParserRegistry); previously they were
        // filtered out at validation time so the IR never carried them.
        "background-position-block", "background-position-inline",
        "background-repeat", "background-attachment", "background-origin",
        "background-clip", "background-blend-mode",

        // Colors
        "color", "opacity", "color-scheme", "color-adjust", "forced-color-adjust",
        "print-color-adjust",

        // Typography
        "font", "font-family", "font-size", "font-weight", "font-style",
        "font-variant", "font-stretch", "font-kerning", "font-feature-settings",
        "font-variation-settings", "font-optical-sizing", "font-size-adjust",
        "font-synthesis", "font-synthesis-weight", "font-synthesis-style",
        "font-synthesis-small-caps", "font-synthesis-position",
        "font-variant-ligatures", "font-variant-caps", "font-variant-numeric",
        "font-variant-alternates", "font-variant-east-asian", "font-variant-position",
        "font-variant-emoji", "font-palette", "font-language-override",
        "font-display", "font-min-size", "font-max-size", "font-named-instance",
        // font-smooth: registered in PropertyParserRegistry but was missing
        // here, so valid CSS was silently dropped before parsing (validator/
        // registry drift — pinned by ValidatorRegistryParityTest).
        "font-smooth",
        "line-height", "line-height-step", "letter-spacing", "word-spacing",

        // Text
        "text-align", "text-align-last", "text-align-all", "text-justify",
        "text-indent", "text-transform", "text-decoration", "text-decoration-line",
        "text-decoration-color", "text-decoration-style", "text-decoration-thickness",
        "text-decoration-skip", "text-decoration-skip-ink", "text-underline-position",
        "text-underline-offset", "text-overflow", "text-wrap", "text-wrap-mode",
        "text-wrap-style", "text-emphasis", "text-emphasis-style", "text-emphasis-color",
        "text-emphasis-position", "text-shadow", "text-rendering", "text-size-adjust",
        "text-combine-upright", "text-orientation", "text-spacing-trim", "text-autospace",
        "text-box-edge", "text-box-trim", "text-group-align", "text-space-collapse",
        "text-space-trim",
        "white-space", "white-space-collapse", "word-break", "word-wrap",
        "overflow-wrap", "line-break", "hyphens", "hyphenate-character",
        "hyphenate-limit-chars", "hyphenate-limit-lines", "hyphenate-limit-last",
        "hyphenate-limit-zone", "hanging-punctuation", "tab-size",
        "vertical-align", "vertical-align-last", "writing-mode", "direction",
        "unicode-bidi", "initial-letter", "initial-letter-align",
        "line-clamp", "line-grid", "line-snap", "string-set",
        "word-space-transform", "wrap-after", "wrap-before", "wrap-inside",

        // Transforms
        "transform", "transform-origin", "transform-style", "transform-box",
        "rotate", "scale", "translate",
        "perspective", "perspective-origin", "backface-visibility",

        // Transitions & Animations
        "transition", "transition-property", "transition-duration",
        "transition-timing-function", "transition-delay", "transition-behavior",
        "animation", "animation-name", "animation-duration", "animation-timing-function",
        "animation-delay", "animation-iteration-count", "animation-direction",
        "animation-fill-mode", "animation-play-state", "animation-timeline",
        "animation-range", "animation-range-start", "animation-range-end",
        "animation-composition",

        // Shadows
        "box-shadow", "text-shadow",

        // Overflow & Clipping
        "overflow", "overflow-x", "overflow-y", "overflow-block", "overflow-inline",
        "overflow-anchor", "overflow-clip-margin",
        "clip", "clip-path", "clip-path-geometry-box", "clip-rule",

        // Visibility & Opacity
        "visibility", "isolation", "mix-blend-mode",

        // Filters
        "filter", "backdrop-filter",

        // Masks
        "mask", "mask-image", "mask-mode", "mask-repeat", "mask-position",
        "mask-position-x", "mask-position-y", "mask-clip", "mask-origin",
        "mask-size", "mask-composite", "mask-type",
        "mask-border", "mask-border-source", "mask-border-slice", "mask-border-width",
        "mask-border-outset", "mask-border-repeat", "mask-border-mode",

        // Columns
        "columns", "column-count", "column-width", "column-gap", "column-rule",
        "column-rule-width", "column-rule-style", "column-rule-color",
        "column-span", "column-fill",

        // Gap decorations (CSS Gap Decorations Level 1). The validator runs
        // FIRST in the pipeline, so every name registered in
        // PropertyParserRegistry must appear here or the declaration is
        // silently dropped (pinned by ValidatorRegistryParityTest).
        "row-rule", "row-rule-width", "row-rule-style", "row-rule-color",
        "column-rule-break", "row-rule-break",
        "column-rule-inset", "row-rule-inset", "rule-overlap",

        // Lists
        "list-style", "list-style-type", "list-style-position", "list-style-image",

        // Tables
        "table-layout", "border-collapse", "border-spacing", "caption-side",
        "empty-cells",

        // Counters
        "counter-reset", "counter-increment", "counter-set",

        // Content
        "content", "quotes",

        // Cursor & Interactivity
        "cursor", "pointer-events", "user-select", "resize", "touch-action",
        "touch-action-delay", "caret-color", "accent-color",
        "appearance", "input-security",
        "caret", "caret-shape",
        // interactivity: had a registered parser but was missing from this
        // whitelist (validator/registry drift — pinned by
        // ValidatorRegistryParityTest).
        "interactivity",

        // Scrolling
        "scroll-behavior", "scroll-margin", "scroll-margin-top", "scroll-margin-right",
        "scroll-margin-bottom", "scroll-margin-left", "scroll-margin-block",
        "scroll-margin-block-start", "scroll-margin-block-end", "scroll-margin-inline",
        "scroll-margin-inline-start", "scroll-margin-inline-end",
        "scroll-padding", "scroll-padding-top", "scroll-padding-right",
        "scroll-padding-bottom", "scroll-padding-left", "scroll-padding-block",
        "scroll-padding-block-start", "scroll-padding-block-end", "scroll-padding-inline",
        "scroll-padding-inline-start", "scroll-padding-inline-end",
        "scroll-snap-type", "scroll-snap-align", "scroll-snap-stop",
        "scroll-snap-margin", "scroll-snap-margin-top", "scroll-snap-margin-right",
        "scroll-snap-margin-bottom", "scroll-snap-margin-left",
        "scroll-snap-margin-block", "scroll-snap-margin-block-start", "scroll-snap-margin-block-end",
        "scroll-snap-margin-inline", "scroll-snap-margin-inline-start", "scroll-snap-margin-inline-end",
        "scroll-timeline", "scroll-timeline-name", "scroll-timeline-axis",
        "scroll-start", "scroll-start-x", "scroll-start-y", "scroll-start-block", "scroll-start-inline",
        "scroll-start-target", "scroll-start-target-x", "scroll-start-target-y",
        "scroll-start-target-block", "scroll-start-target-inline",
        "overscroll-behavior", "overscroll-behavior-x", "overscroll-behavior-y",
        "overscroll-behavior-block", "overscroll-behavior-inline",
        "scrollbar-width", "scrollbar-color", "scrollbar-gutter",
        // scroll-marker-group / scroll-target-group (CSS Overflow L5):
        // registered parsers existed but the names were missing here, so the
        // values were silently dropped (validator/registry drift — pinned by
        // ValidatorRegistryParityTest).
        "scroll-marker-group", "scroll-target-group",

        // View transitions
        "view-timeline", "view-timeline-name", "view-timeline-axis",
        "view-timeline-inset", "view-transition-name",
        "view-transition-class", "view-transition-group",
        "timeline-scope",

        // Container queries
        "container", "container-name", "container-type",
        "contain", "contain-intrinsic-size", "contain-intrinsic-width",
        "contain-intrinsic-height", "contain-intrinsic-block-size",
        "contain-intrinsic-inline-size", "content-visibility",

        // Anchor positioning
        "anchor-name", "anchor-scope", "position-anchor", "position-area",
        "position-try", "position-try-options", "position-try-order",
        "position-visibility", "position-fallback", "position-try-fallbacks",

        // Shapes & Float — shape-inside added (CSS Shapes L2). Parser +
        // per-platform appliers exist; without this allowlist entry the
        // value was silently dropped before reaching the renderer.
        "shape-outside", "shape-margin", "shape-image-threshold", "shape-padding",
        "shape-inside",
        "shape-rendering", "float-defer", "float-offset", "float-reference",
        "wrap-flow", "wrap-through",

        // Rhythm (CSS Rhythmic Sizing L1). All 5 block-step-* names +
        // the shorthand were silently dropped — parser + appliers exist.
        "block-step", "block-step-size", "block-step-align",
        "block-step-insert", "block-step-round",

        // Images & Objects
        "object-fit", "object-position", "image-rendering", "image-orientation",
        "image-resolution", "image-rendering-quality",

        // Blend modes
        "background-blend-mode", "mix-blend-mode",

        // Performance
        "will-change", "aspect-ratio",

        // SVG properties
        "fill", "fill-opacity", "fill-rule", "stroke", "stroke-width",
        "stroke-dasharray", "stroke-dashoffset", "stroke-linecap", "stroke-linejoin",
        "stroke-miterlimit", "stroke-opacity", "color-interpolation",
        "color-interpolation-filters", "color-rendering", "dominant-baseline",
        "dominant-baseline-adjust", "alignment-baseline", "baseline-shift",
        "baseline-source", "stop-color", "stop-opacity", "flood-color",
        "flood-opacity", "lighting-color", "marker", "marker-start", "marker-mid",
        "marker-end", "marker-side", "paint-order", "vector-effect",
        "text-anchor", "glyph-orientation-vertical", "glyph-orientation-horizontal",
        "buffered-rendering", "enable-background", "kerning",
        "cx", "cy", "r", "rx", "ry", "x", "y", "d",

        // Printing & Page breaks
        "page", "size", "bleed", "marks",
        "page-break-before", "page-break-after", "page-break-inside",
        "break-before", "break-after", "break-inside",
        // margin-break (CSS Fragmentation L3): parser registered but the name
        // was missing here (validator/registry drift — pinned by
        // ValidatorRegistryParityTest).
        "margin-break",
        "orphans", "widows", "max-lines",
        "box-decoration-break",

        // Offset & Motion path
        "offset", "offset-path", "offset-distance", "offset-rotate",
        "offset-anchor", "offset-position",

        // Ruby — ruby-overhang (CSS Ruby L1) had a registered parser but was
        // missing here (validator/registry drift — pinned by
        // ValidatorRegistryParityTest).
        "ruby-position", "ruby-align", "ruby-merge", "ruby-overhang",

        // Regions
        "flow-into", "flow-from", "region-fragment",

        // Misc — reading-order joins reading-flow (CSS Display L4); its
        // parser was registered but the name was missing here (validator/
        // registry drift — pinned by ValidatorRegistryParityTest).
        "all", "field-sizing", "overlay", "reading-flow", "reading-order",
        "interpolate-size",
        "zoom", "corner-shape", "block-ellipsis", "dynamic-range-limit",
        "object-view-box", "text-spacing",

        // Bookmarks & Footnotes
        "bookmark-label", "bookmark-level", "bookmark-state", "bookmark-target",
        "footnote-display", "footnote-policy",

        // Running
        "running", "copy-into", "continue", "leader",

        // Nav direction
        "nav-up", "nav-down", "nav-left", "nav-right",

        // Math
        "math-style", "math-shift", "math-depth",

        // Speech & Audio
        "speak", "speak-as", "cue", "cue-before", "cue-after",
        "pause", "pause-before", "pause-after", "rest", "rest-before", "rest-after",
        "voice-balance", "voice-duration", "voice-family", "voice-pitch",
        "voice-range", "voice-rate", "voice-stress", "voice-volume",
        "azimuth", "elevation", "pitch", "pitch-range", "richness",
        "speech-rate", "stress", "volume", "presentation-level",

        // Appearance
        "appearance-variant"
    )

    // ===== VENDOR-PREFIXED PROPERTIES =====
    // Common vendor prefixes
    private val vendorPrefixes = setOf("webkit", "moz", "ms", "o")

    /**
     * Check if a property name is a valid CSS property.
     * This includes:
     * - Standard CSS properties (shorthand and longhand)
     * - Vendor-prefixed properties (-webkit-, -moz-, -ms-, -o-)
     *
     * @param propertyName The CSS property name to validate
     * @return true if the property is valid, false otherwise
     */
    fun isValidProperty(propertyName: String): Boolean {
        // CSS custom properties (`--name`, css-variables-1 §2) are valid
        // declaration targets for ANY name after the two dashes. Checked
        // BEFORE the lowercase normalization below because custom property
        // names are case-SENSITIVE, unlike standard property names. Their
        // values are routed to the component-level `variables` map by
        // CustomPropertyParser, never to the typed property pipeline.
        if (CustomPropertyParser.isCustomProperty(propertyName)) {
            return true
        }

        // CSS property names are case-insensitive
        val loweredName = propertyName.lowercase()

        // Check if it's a standard property
        if (standardProperties.contains(loweredName)) {
            return true
        }

        // Check if it's a vendor-prefixed property
        if (loweredName.startsWith("-")) {
            val parts = loweredName.substring(1).split("-", limit = 2)
            if (parts.size == 2) {
                val prefix = parts[0]
                val baseName = parts[1]

                // Valid if prefix is known and base property exists
                if (vendorPrefixes.contains(prefix)) {
                    // Check if the unprefixed version is a standard property
                    if (standardProperties.contains(baseName)) {
                        return true
                    }

                    // Some vendor-specific properties don't have standard equivalents
                    // Allow them as well (e.g., -webkit-line-clamp, -moz-osx-font-smoothing)
                    return true
                }
            }
        }

        return false
    }
}
