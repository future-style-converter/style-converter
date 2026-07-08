package app.parsing.css.properties.shorthands

/**
 * Registry of CSS shorthand property expanders.
 *
 * ## Purpose
 * Central mapping of CSS shorthand properties to their expander implementations.
 * Called by PropertiesParser before longhand parsing to normalize all properties.
 *
 * ## Processing Flow
 * ```
 * Input: "padding: 10px 20px"
 *     ↓ ShorthandRegistry.isShorthand("padding") → true
 *     ↓ ShorthandRegistry.expand("padding", "10px 20px")
 * Output: {
 *   "padding-top": "10px",
 *   "padding-right": "20px",
 *   "padding-bottom": "10px",
 *   "padding-left": "20px"
 * }
 * ```
 *
 * ## Registered Expanders (50+)
 * - **Spacing**: padding, margin, padding-block/inline, margin-block/inline
 * - **Borders**: border, border-top/right/bottom/left, border-radius, border-color/width/style
 * - **Flexbox**: flex, flex-flow, gap
 * - **Grid**: grid, grid-row/column/area/template
 * - **Positioning**: inset, inset-block/inline
 * - **Background**: background, background-position
 * - **Animation**: animation, transition
 * - **Typography**: font, font-variant, font-synthesis
 * - **And more**: overflow, outline, mask, scroll-margin/padding, etc.
 *
 * @see ShorthandExpander interface that all expanders implement
 * @see PropertiesParser for the orchestration layer
 */
object ShorthandRegistry {

    private val expanders = mapOf<String, ShorthandExpander>(
        // Spacing
        "padding" to PaddingExpander,
        "margin" to MarginExpander,
        "padding-block" to PaddingBlockExpander,
        "padding-inline" to PaddingInlineExpander,
        "margin-block" to MarginBlockExpander,
        "margin-inline" to MarginInlineExpander,

        // Borders
        "border" to BorderExpander,
        "border-top" to BorderTopExpander,
        "border-right" to BorderRightExpander,
        "border-bottom" to BorderBottomExpander,
        "border-left" to BorderLeftExpander,
        "border-block" to BorderBlockExpander,
        "border-inline" to BorderInlineExpander,
        "border-radius" to BorderRadiusExpander,
        "border-color" to BorderColorExpander,
        "border-width" to BorderWidthExpander,
        "border-style" to BorderStyleExpander,
        "border-image" to BorderImageExpander,

        // Outline
        "outline" to OutlineExpander,

        // Flexbox
        "flex" to FlexExpander,
        "flex-flow" to FlexFlowExpander,
        "gap" to GapExpander,

        // Grid
        "grid-row" to GridRowExpander,
        "grid-column" to GridColumnExpander,
        "grid-area" to GridAreaExpander,
        "grid-template" to GridTemplateExpander,
        "grid-gap" to GridGapExpander,

        // Place
        "place-content" to PlaceContentExpander,
        "place-items" to PlaceItemsExpander,
        "place-self" to PlaceSelfExpander,

        // Inset (positioning)
        "inset" to InsetExpander,
        "inset-block" to InsetBlockExpander,
        "inset-inline" to InsetInlineExpander,

        // Overflow
        "overflow" to OverflowExpander,

        // Scroll spacing
        "scroll-margin" to ScrollMarginExpander,
        "scroll-margin-block" to ScrollMarginBlockExpander,
        "scroll-margin-inline" to ScrollMarginInlineExpander,
        "scroll-padding" to ScrollPaddingExpander,
        "scroll-padding-block" to ScrollPaddingBlockExpander,
        "scroll-padding-inline" to ScrollPaddingInlineExpander,

        // Text
        "text-decoration" to TextDecorationExpander,

        // Background
        "background" to BackgroundExpander,
        "background-position" to BackgroundPositionExpander,

        // Animation & Transition
        "animation" to AnimationExpander,
        "transition" to TransitionExpander,

        // Columns
        "column-rule" to ColumnRuleExpander,

        // Font
        "font" to FontExpander,
        "font-variant" to FontVariantExpander,
        "font-synthesis" to FontSynthesisExpander,

        // Columns
        "columns" to ColumnsExpander,

        // List style
        "list-style" to ListStyleExpander,

        // Mask
        "mask" to MaskExpander,
        "mask-border" to MaskBorderExpander,

        // Grid (main shorthand)
        "grid" to GridShorthandExpander,

        // Offset
        "offset" to OffsetExpander,

        // Scroll start
        "scroll-start" to ScrollStartExpander,
        "scroll-start-target" to ScrollStartTargetExpander,

        // Logical border shorthands
        "border-block-start" to BorderBlockStartExpander,
        "border-block-end" to BorderBlockEndExpander,
        "border-inline-start" to BorderInlineStartExpander,
        "border-inline-end" to BorderInlineEndExpander
    )

    /**
     * Check if a property name is a shorthand.
     */
    fun isShorthand(propertyName: String): Boolean {
        return propertyName in expanders
    }

    /**
     * Expand a shorthand property into its longhand equivalents.
     *
     * @param propertyName The shorthand property name (e.g., "padding")
     * @param value The shorthand value (e.g., "10px 20px")
     * @return Map of longhand property names to values
     */
    fun expand(propertyName: String, value: String): Map<String, String> {
        val expander = expanders[propertyName] ?: return emptyMap()
        return expander.expand(value)
    }
}
