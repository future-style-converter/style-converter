package app.parsing.css.properties

import app.irmodels.IRProperty
import app.parsing.css.CssPropertyValue
import app.parsing.css.properties.shorthands.ShorthandRegistry
import app.parsing.css.properties.longhands.PropertyParserRegistry
import app.parsing.css.properties.primitiveParsers.AttrNotationResolver

/**
 * Main orchestrator for CSS property parsing.
 *
 * Process:
 * 1. Validate properties (filter invalid CSS property names)
 * 1.5 Resolve decidable attr() notations; drop declarations proven invalid
 * 2. Expand shorthands to longhands (padding: 10px → padding-top/right/bottom/left)
 * 2.25 Collapse logical/physical alias pairs by cascade order (base bucket only, css-logical-1 §4)
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
    internal fun normalizePropertyName(name: String): String {
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
     * @param writingContext The component's own computed writing mode +
     *   direction (WritingContext.derive over the parent chain), or null to
     *   skip logical/physical alias collapsing. Non-null ONLY at the
     *   base-declaration call site: css-logical-1 §4 pairs a flow-relative
     *   longhand with its physical twin "using the element's own computed
     *   writing mode" and cascades the pair "together as one", so the later
     *   declaration wins — see LogicalAliasResolution. Selector/media
     *   buckets and keyframe stops pass null: a bucket's `margin-left` versus
     *   the base's `margin-inline-start` is a cross-bucket cascade this parser
     *   never sees, so it must not pretend to resolve half of it.
     * @return Mutable list of IRProperty instances (specific types from irmodels/)
     */
    fun parse(
        properties: Map<String, CssPropertyValue>,
        resolveInheritedDefaults: Boolean = false,
        sourceTag: String? = null,
        writingContext: WritingContext? = null
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

        // Step 1.5: resolve the `attr()` notation where css-values-5 §8.7
        // makes the answer decidable without the DOM (a malformed <attr-name>
        // → the declaration matches no grammar and is ignored per css-syntax-3
        // §2.2 "Error Handling"; a namespace-PREFIXED name → nothing
        // downstream can resolve it, so §8.7.1's FAILURE path applies). Runs
        // BEFORE shorthand expansion so a substituted
        // `background: attr(ns|x, green)` expands as the plain
        // `background: green` it now is — and so an INVALID shorthand takes
        // all of its longhands down with it, which is what "the whole
        // declaration is invalid" means. Every other attr() shape is passed
        // through untouched; see AttrNotationResolver for the abstention rules
        // and for why the prefixed case is decidable at all.
        val substitutedProperties = LinkedHashMap<String, CssPropertyValue>(validProperties.size)
        for ((name, cssValue) in validProperties) {
            when (val outcome = AttrNotationResolver.resolve(cssValue.value)) {
                is AttrNotationResolver.Outcome.Invalid -> {
                    // Dropped, never silent: the convert log is the audit trail
                    // for every declaration the cascade loses.
                    println("[CSS Parser] Dropped invalid declaration '$name: ${cssValue.value}' — ${outcome.reason} (css-syntax-3 §2.2)")
                }
                is AttrNotationResolver.Outcome.InvalidAtComputedValueTime -> {
                    // NOT the same event as the drop above, so it does not
                    // share its log line: css-values-5 Appendix A ("Invalid
                    // Substitution") makes the property compute as if `unset`
                    // were specified (css-cascade-5 §7.3.3). Omitting the
                    // declaration encodes exactly that HERE, because this
                    // function's input is a Map keyed by property name — there
                    // is no earlier declaration for a drop to expose, and an
                    // absent property already resolves to inherited-or-initial
                    // on all three runtimes. Anything that gives this parser a
                    // real cascade must revisit this line.
                    println("[CSS Parser] '$name: ${cssValue.value}' is invalid at computed-value time — computes to `unset` — ${outcome.reason}")
                }
                is AttrNotationResolver.Outcome.Substituted -> {
                    substitutedProperties[name] = CssPropertyValue(outcome.value)
                    println("[CSS Parser] Substituted attr() in '$name': '${cssValue.value}' → '${outcome.value}' — ${outcome.reason}")
                }
                // NotAttr / Unresolvable: byte-for-byte passthrough.
                else -> substitutedProperties[name] = cssValue
            }
        }

        // Step 2: Expand shorthands to longhands
        val expandedProperties = mutableMapOf<String, String>()
        // Cascade order per resulting longhand: the index of the SOURCE
        // declaration that last wrote it (every longhand a shorthand expands
        // to shares the shorthand's index). css-logical-1 §4 needs this to
        // pick the later of a logical/physical pair. Tracked beside the map,
        // NOT by moving keys: LinkedHashMap keeps a re-put key at its first
        // position, and that position fixes the emitted IR property order —
        // re-inserting would churn every fixture whose later shorthand
        // re-sets an earlier longhand.
        val declarationIndex = HashMap<String, Int>()
        var declarationOrdinal = 0

        for ((name, cssValue) in substitutedProperties) {
            val value = cssValue.value
            // Property names are already normalized by camelToKebab

            if (ShorthandRegistry.isShorthand(name)) {
                // Expand shorthand into multiple longhands
                val expanded = ShorthandRegistry.expand(name, value)
                expandedProperties.putAll(expanded)
                for (longhand in expanded.keys) declarationIndex[longhand] = declarationOrdinal

                println("[CSS Parser] Expanded '$name: $value' → ${expanded.keys.joinToString(", ")}")
            } else {
                // Keep longhand as-is
                expandedProperties[name] = value
                declarationIndex[name] = declarationOrdinal
            }
            declarationOrdinal++
        }

        // Step 2.25 (base declarations only): collapse logical/physical alias
        // pairs by cascade order. css-logical-1 §4: paired longhands "share a
        // computed value … derived from the specified value of the property
        // declared with higher priority in the CSS cascade" — inside one
        // declaration block, the later one. Emitting both let each runtime
        // pick its own winner (retrospective A11#4). Identity when the caller
        // passes no context or the component declares no pair — the same map
        // instance flows on, so untouched fixtures stay byte-identical.
        val aliasResult = if (writingContext != null) {
            LogicalAliasResolution.collapse(expandedProperties, declarationIndex, writingContext)
        } else {
            LogicalAliasResolution.Result(expandedProperties, emptyList(), emptyList())
        }
        val aliasResolvedProperties = aliasResult.properties
        // Both outcomes stay visible in the convert log — a drop AND an abstention.
        for (d in aliasResult.dropped) {
            println("[CSS Parser] Collapsed logical/physical alias pair under $writingContext: dropped '${d.loser}: ${d.loserValue}' — '${d.winner}: ${d.winnerValue}' is declared later and the pair shares one computed value (css-logical-1 §4)")
        }
        for (k in aliasResult.kept) {
            println("[CSS Parser] Kept both '${k.logical}' and '${k.physical}': ${k.reason}")
        }

        // Step 2.5 (base declarations only): resolve redundant `inherit`.
        // Post-expansion so `font: inherit` (per-longhand keyword forward,
        // FontExpanderTest pins it) resolves through the same rule as the
        // longhand form. Identity for every bucket call site (flag false),
        // for every UA-styled sourceTag (guard 4 — the UA sheet owns the
        // vacated slot on web), and for every map with no redundant entry —
        // see InheritedDefaultResolution for the full spec argument.
        val resolvedProperties =
            if (resolveInheritedDefaults) InheritedDefaultResolution.resolve(aliasResolvedProperties, sourceTag)
            else aliasResolvedProperties
        // Keep the drop visible in the convert log — never a silent eat.
        if (resolvedProperties.size != aliasResolvedProperties.size) {
            val dropped = aliasResolvedProperties.keys - resolvedProperties.keys
            println("[CSS Parser] Resolved redundant 'inherit' on inherited-by-default propert${if (dropped.size == 1) "y" else "ies"} (drop == natural inheritance): ${dropped.joinToString(", ")}")
        }
        // …and keep the SKIP equally visible: a UA-styled tag that carried
        // candidate `inherit` declarations kept them on purpose, so the log
        // records the exemption instead of leaving a silent non-event.
        if (resolveInheritedDefaults && InheritedDefaultResolution.isUaStyledTag(sourceTag)) {
            val kept = aliasResolvedProperties.filter { (n, v) -> InheritedDefaultResolution.isRedundantInherit(n, v) }.keys
            if (kept.isNotEmpty()) {
                println("[CSS Parser] Kept 'inherit' on <$sourceTag> (UA-styled tag: the UA sheet declares these, so absence ≠ inheritance): ${kept.joinToString(", ")}")
            }
        }

        // Step 3: Parse each longhand property into specific IRProperty
        for ((name, value) in resolvedProperties) {
            val property = PropertyParserRegistry.parse(name, value)

            when {
                // The parser PROVED the value is not CSS (not merely "a shape I
                // don't model"): css-syntax-3 §2.2 ("Error Handling") and CSS
                // 2.2 §4.2 ("Rules for handling parsing errors") say an
                // invalid declaration is ignored, leaving the previously
                // declared value in force. Emitting a passthrough instead would
                // shadow that earlier value with bytes no runtime can render.
                // Opt-in per parser — see InvalidDeclaration for why the two
                // failure modes must not share the `null` channel.
                property === InvalidDeclaration ->
                    println("[CSS Parser] Dropped invalid declaration '$name: $value' (css-syntax-3 §2.2)")

                // Successfully parsed to specific property class
                property != null -> result.add(property)

                // Fallback: valid-but-unmodelled CSS keeps its author bytes on
                // the wire (`_unmapped: true`) so a runtime that understands
                // them can still act. Unchanged behaviour.
                else -> {
                    result.add(GenericProperty(name, value))
                    println("[CSS Parser] No parser for '$name', using GenericProperty")
                }
            }
        }

        println("[CSS Parser] Parsed ${result.size} properties (${result.count { it is GenericProperty }} generic)")

        return result
    }
}
