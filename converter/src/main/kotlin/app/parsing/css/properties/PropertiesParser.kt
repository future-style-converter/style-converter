package app.parsing.css.properties

import app.irmodels.IRProperty
import app.parsing.css.CssPropertyValue
import app.parsing.css.properties.shorthands.ShorthandRegistry
import app.parsing.css.properties.longhands.PropertyParserRegistry

/**
 * Main orchestrator for CSS property parsing.
 *
 * Process:
 * 1. Validate properties (filter invalid CSS property names)
 * 2. Expand shorthands to longhands (padding: 10px → padding-top/right/bottom/left)
 * 3. Parse each longhand into specific IRProperty (background-color → BackgroundColorProperty)
 * 4. Fallback to GenericProperty for properties without specific parsers
 *
 * Example:
 * Input: {
 *   "padding": "10px 20px",
 *   "background-color": "#FF0000",
 *   "opacity": "0.5"
 * }
 *
 * Output: [
 *   PaddingTopProperty(Length(10px)),
 *   PaddingRightProperty(Length(20px)),
 *   PaddingBottomProperty(Length(10px)),
 *   PaddingLeftProperty(Length(20px)),
 *   BackgroundColorProperty(IRColor.Hex("#FF0000")),
 *   GenericProperty("opacity", "0.5")  // No parser yet
 * ]
 */
object PropertiesParser {

    /**
     * Normalize CSS property name to lowercase kebab-case.
     * - Converts camelCase to kebab-case: "backgroundColor" → "background-color"
     * - Lowercases all: "DISPLAY" → "display", "DiSpLaY" → "display"
     */
    private fun normalizePropertyName(name: String): String {
        // If already contains hyphens, just lowercase
        if (name.contains("-")) {
            return name.lowercase()
        }

        // Check if it looks like camelCase (lowercase start, has uppercase)
        val isCamelCase = name.isNotEmpty() &&
                name[0].isLowerCase() &&
                name.any { it.isUpperCase() }

        return if (isCamelCase) {
            // Convert camelCase to kebab-case
            name.replace(Regex("([a-z])([A-Z])")) { matchResult ->
                "${matchResult.groupValues[1]}-${matchResult.groupValues[2].lowercase()}"
            }.lowercase()
        } else {
            // Just lowercase (handles DISPLAY, DiSpLaY, etc.)
            name.lowercase()
        }
    }

    /**
     * Parse a map of CSS properties into a list of specific IRProperty instances.
     *
     * @param properties Map of property name → CssPropertyValue
     * @param resolveInheritedDefaults Opt-in cascade resolution: when true,
     *   a winning `inherit` on an inherited-by-default longhand is DROPPED
     *   after shorthand expansion (CSS22 §6.2.1 — for those properties the
     *   declaration is byte-equivalent to absence, and the unresolved
     *   `{global:"inherit"}` marker shadows the natives' inheritance
     *   channels; see InheritedDefaultResolution). ONLY the base-declaration
     *   call site (CssParsing.convertToIR) passes true: in selector/media
     *   buckets and keyframe stops `inherit` OVERRIDES the base declaration
     *   when the bucket applies, so absence would change the render — those
     *   call sites keep the default false and emit the keyword unresolved.
     * @param sourceTag The component's originating HTML tag (the extractor's
     *   `_tag` hint → IR v2 `meta.sourceTag`), or null when the input has
     *   none. Consulted ONLY when resolveInheritedDefaults is true: on a
     *   UA-styled tag (InheritedDefaultResolution.UA_STYLED_TAGS) the drop
     *   is not identity — Chromium's UA sheet declares those properties, so
     *   the vacated slot is filled by the UA rule instead of by inheritance
     *   (css-cascade-4 §7.3 defaulting applies only when NO origin declared
     *   a winner). Bucket call sites leave it null; they don't drop anyway.
     * @return Mutable list of IRProperty instances (specific types from irmodels/)
     */
    fun parse(
        properties: Map<String, CssPropertyValue>,
        resolveInheritedDefaults: Boolean = false,
        sourceTag: String? = null
    ): MutableList<IRProperty> {
        val result = mutableListOf<IRProperty>()

        // Step 0: route custom properties (--*) OUT of the typed pipeline.
        // They are untyped until substitution (css-variables-1 §2) and are
        // lifted into the component-level `variables` map by
        // CustomPropertyParser (CssParsing.convertToIR owns that call for
        // base declarations). Filtered FIRST because normalizePropertyName
        // below lowercases, and custom property names are case-sensitive.
        // Without this filter they would degrade into GenericProperty noise.
        val declaredProperties = properties.filterKeys { !CustomPropertyParser.isCustomProperty(it) }
        val customCount = properties.size - declaredProperties.size
        if (customCount > 0) {
            // Base declarations are re-extracted at the component level;
            // selector/media buckets have no variables surface yet (spec 02
            // custom-properties section), so the log keeps the drop honest.
            println("[CSS Parser] $customCount custom propert${if (customCount == 1) "y" else "ies"} (--*) routed to the component-level variables map")
        }

        // Normalize property names (camelCase to kebab-case, mixed case to lowercase)
        val normalizedProperties = declaredProperties.mapKeys { (name, _) -> normalizePropertyName(name) }

        // Step 1: Validate CSS properties (filter out invalid property names)
        val validProperties = normalizedProperties.filter { (name, _) ->
            CssPropertyValidator.isValidProperty(name)
        }

        // Optional: Log removed invalid properties for debugging
        val invalidProperties = normalizedProperties.keys - validProperties.keys
        if (invalidProperties.isNotEmpty()) {
            println("[CSS Parser] Removed invalid properties: ${invalidProperties.joinToString(", ")}")
        }

        // Step 2: Expand shorthands to longhands
        val expandedProperties = mutableMapOf<String, String>()

        for ((name, cssValue) in validProperties) {
            val value = cssValue.value
            // Property names are already normalized by camelToKebab

            if (ShorthandRegistry.isShorthand(name)) {
                // Expand shorthand into multiple longhands
                val expanded = ShorthandRegistry.expand(name, value)
                expandedProperties.putAll(expanded)

                println("[CSS Parser] Expanded '$name: $value' → ${expanded.keys.joinToString(", ")}")
            } else {
                // Keep longhand as-is
                expandedProperties[name] = value
            }
        }

        // Step 2.5 (base declarations only): resolve redundant `inherit`.
        // Post-expansion so `font: inherit` (per-longhand keyword forward,
        // FontExpanderTest pins it) resolves through the same rule as the
        // longhand form. Identity for every bucket call site (flag false),
        // for every UA-styled sourceTag (guard 4 — the UA sheet owns the
        // vacated slot on web), and for every map with no redundant entry —
        // see InheritedDefaultResolution for the full spec argument.
        val resolvedProperties =
            if (resolveInheritedDefaults) InheritedDefaultResolution.resolve(expandedProperties, sourceTag)
            else expandedProperties
        // Keep the drop visible in the convert log — never a silent eat.
        if (resolvedProperties.size != expandedProperties.size) {
            val dropped = expandedProperties.keys - resolvedProperties.keys
            println("[CSS Parser] Resolved redundant 'inherit' on inherited-by-default propert${if (dropped.size == 1) "y" else "ies"} (drop == natural inheritance): ${dropped.joinToString(", ")}")
        }
        // …and keep the SKIP equally visible: a UA-styled tag that carried
        // candidate `inherit` declarations kept them on purpose, so the log
        // records the exemption instead of leaving a silent non-event.
        if (resolveInheritedDefaults && InheritedDefaultResolution.isUaStyledTag(sourceTag)) {
            val kept = expandedProperties.filter { (n, v) -> InheritedDefaultResolution.isRedundantInherit(n, v) }.keys
            if (kept.isNotEmpty()) {
                println("[CSS Parser] Kept 'inherit' on <$sourceTag> (UA-styled tag: the UA sheet declares these, so absence ≠ inheritance): ${kept.joinToString(", ")}")
            }
        }

        // Step 3: Parse each longhand property into specific IRProperty
        for ((name, value) in resolvedProperties) {
            val property = PropertyParserRegistry.parse(name, value)

            if (property != null) {
                // Successfully parsed to specific property class
                result.add(property)
            } else {
                // Fallback: create generic property wrapper
                result.add(GenericProperty(name, value))
                println("[CSS Parser] No parser for '$name', using GenericProperty")
            }
        }

        println("[CSS Parser] Parsed ${result.size} properties (${result.count { it is GenericProperty }} generic)")

        return result
    }
}
