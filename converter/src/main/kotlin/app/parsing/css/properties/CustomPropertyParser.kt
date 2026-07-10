package app.parsing.css.properties

import app.parsing.css.CssPropertyValue

/**
 * First-class handling for CSS custom properties (`--name: <value>`).
 *
 * ## Spec grounding — css-variables-1 §2
 * A custom property is any declaration whose name starts with `--`.
 * Two rules from the spec shape everything in this file:
 *
 * 1. **Names are case-SENSITIVE** (`--Main-Color` ≠ `--main-color`),
 *    unlike every standard CSS property name. Custom property names must
 *    therefore never pass through the parser's lowercase normalization.
 * 2. **Values are untyped until substitution**: the declaration value is
 *    an arbitrary token stream that only acquires a type when a `var()`
 *    reference substitutes it into a real property. The converter can
 *    therefore not normalize it (no px conversion, no sRGB conversion) —
 *    it must store the RAW value verbatim and leave resolution to the
 *    runtimes.
 *
 * ## Where the extracted values go
 * Custom properties do NOT become `{type, data}` entries in the
 * component's `properties` array (they are not typed properties). They
 * are lifted into the component-level `variables` map — an ADDITIVE IR
 * v2 envelope key (`"variables": {"--name": "raw value"}`), specified in
 * schema/spec/01-envelope.md and machine-checked by
 * schema/ir-v2.schema.json. `CssParsing.convertToIR` owns the extraction
 * call; `PropertiesParser.parse` filters the same names OUT of the typed
 * pipeline so they can never degrade into `GenericProperty` noise.
 */
object CustomPropertyParser {

    /**
     * True when [name] is a CSS custom property name.
     *
     * Per css-variables-1 §2 the production is `--` followed by at least
     * one more character: the bare name `--` is explicitly reserved by
     * the spec ("the empty name is invalid") and is rejected here, which
     * lets it fall through to the validator and be dropped like any
     * other invalid declaration.
     */
    fun isCustomProperty(name: String): Boolean =
        // `length > 2` enforces "at least one character after the dashes";
        // startsWith covers the `--` prefix itself. No case folding — see
        // the case-sensitivity rule in the class KDoc.
        name.length > 2 && name.startsWith("--")

    /**
     * Extract every custom-property declaration from an authored
     * properties map into the component-level `variables` map.
     *
     * - Keys are kept **verbatim** (case preserved — spec rule 1).
     * - Values are kept **verbatim** (untyped raw token stream — spec
     *   rule 2). The empty string is a legal custom-property value
     *   (`--x:;` is valid CSS) and is preserved as-is.
     * - Declaration order is preserved (LinkedHashMap) so emission is
     *   deterministic and mirrors the authoring order.
     *
     * @return the ordered `--name → raw value` map, or `null` when the
     *   component declares no custom properties — null lets the wire
     *   serializer omit the key entirely (omit-when-empty rule).
     */
    fun extractVariables(properties: Map<String, CssPropertyValue>): Map<String, String>? {
        // LinkedHashMap: iteration order == authoring order == wire order.
        val variables = LinkedHashMap<String, String>()
        for ((name, value) in properties) {
            // Only `--*` names are lifted; everything else stays in the
            // typed property pipeline (PropertiesParser).
            if (isCustomProperty(name)) variables[name] = value.value
        }
        // null (not an empty map) so callers can pass it straight into the
        // omit-when-null IRComponent field without an extra emptiness check.
        return variables.ifEmpty { null }
    }
}
