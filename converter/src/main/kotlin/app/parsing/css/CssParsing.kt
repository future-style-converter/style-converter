package app.parsing.css

import app.irmodels.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import app.parsing.css.properties.PropertiesParser
import app.parsing.css.selectors.parseSelectors
import app.parsing.css.mediaQueries.parseMedia

/**
 * Main CSS parsing entry point.
 *
 * ## Processing Pipeline
 * ```
 * JSON Input
 *   ↓ JsonInputToCssComponents()
 * CssComponents (intermediate model)
 *   ↓ cssParsing()
 * IRDocument (final IR model)
 * ```
 *
 * ## Responsibilities
 * - Parse JSON input into intermediate CssComponents model
 * - Delegate property parsing to PropertiesParser
 * - Delegate selector parsing to parseSelectors
 * - Delegate media query parsing to parseMedia
 * - Assemble final IRDocument with all parsed components
 *
 * @see PropertiesParser for CSS property parsing
 * @see parseSelectors for pseudo-class selector handling
 * @see parseMedia for media query handling
 */

/**
 * Transforms a raw JSON object into the strongly-typed CssComponents model.
 *
 * Handles JSON structure:
 * ```json
 * {
 *   "components": {
 *     "ComponentName": {
 *       "properties": { "color": "red", ... },
 *       "selectors": [{ "selector": ":hover", "properties": {...} }],
 *       "media": [{ "query": "(min-width: 768px)", "properties": {...} }]
 *     }
 *   }
 * }
 * ```
 */
fun JsonInputToCssComponents(doc: JsonObject): CssComponents {
    val componentsJson = doc["components"]?.jsonObject ?: return CssComponents(emptyMap())

    fun toValue(el: JsonElement): CssPropertyValue {
        val prim = el.jsonPrimitive
        val content = when {
            prim.isString -> prim.content
            prim.booleanOrNull != null -> prim.booleanOrNull.toString()
            prim.intOrNull != null -> prim.intOrNull.toString()  // Check int BEFORE double
            prim.doubleOrNull != null -> prim.doubleOrNull.toString()
            else -> prim.content
        }
        return CssPropertyValue(content)
    }

    fun toProperties(obj: JsonObject?): Map<String, CssPropertyValue>? {
        if (obj == null) return null
        return obj.mapValues { (_, v) -> toValue(v) }
    }

    // Recursive function to parse a component and its children
    fun parseComponent(obj: JsonObject): CssComponent {
        val props = toProperties(obj["properties"]?.jsonObject)

        val selectors = obj["selectors"]?.jsonArray?.mapNotNull { el ->
            val selObj = el.jsonObject
            val selectorStr = selObj["selector"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val selProps = toProperties(selObj["properties"]?.jsonObject) ?: emptyMap()
            CssSelector(selector = selectorStr, properties = selProps)
        }

        val media = obj["media"]?.jsonArray?.mapNotNull { el ->
            val mObj = el.jsonObject
            val query = mObj["query"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val mProps = toProperties(mObj["properties"]?.jsonObject) ?: emptyMap()
            CssMedia(query = query, properties = mProps)
        }

        // Parse nested children recursively. WPT input keeps these as a
        // name->component map (each child keyed by its extractor-assigned id
        // like `color-001__1__0`); we mirror that structure into the
        // CssComponent and let convertToIR() flatten to a list at the IR
        // boundary so the renderer-facing IRComponent stays array-shaped.
        val children = obj["children"]?.jsonObject?.mapValues { (_, childNode) ->
            parseComponent(childNode.jsonObject)
        }

        // Read optional element-text content. Source field is `_text` (the
        // leading underscore is the renderer-only metadata convention used
        // by the WPT extractor — see tools/titan/extract-fixture.mjs).
        // Strip the underscore at the parser boundary so the CssComponent
        // identifier stays idiomatic Kotlin; the IR serializer puts it back
        // on the output side. JSON null → null (treated identically to
        // "field absent"); any other primitive content is preserved as-is,
        // including the empty string (it's a meaningful "extracted, was
        // empty" signal).
        val text = obj["_text"]?.let { el ->
            if (el is kotlinx.serialization.json.JsonNull) null
            else el.jsonPrimitive.content
        }

        // Read optional `_role` marker the WPT extractor stamps on
        // synthetic / specially-treated components (today only
        // `"body-root"`, set at extract-fixture.mjs:1417). Same wire
        // convention as `_text`: underscore on the JSON side, idiomatic
        // identifier on the Kotlin side. Without this branch the Kotlin
        // convert pipeline silently dropped `_role` before it could reach
        // tmpOutput.json — see swarm-003 backdrop-filter-root-element
        // investigation for the original root-cause walk.
        val role = obj["_role"]?.let { el ->
            if (el is kotlinx.serialization.json.JsonNull) null
            else el.jsonPrimitive.content
        }

        // Read optional `_tag` (originating HTML tag, lowercase — emitted
        // by the extractor for non-generic tags). Historically the
        // converter dropped this hint entirely (spec 04 field matrix);
        // reading it here closes that lossy hop so IR v2 can forward it
        // as `meta.sourceTag`. The legacy v1 serializer still ignores it,
        // preserving v1 byte-stability.
        val tag = obj["_tag"]?.let { el ->
            if (el is kotlinx.serialization.json.JsonNull) null
            else el.jsonPrimitive.content
        }

        // Read optional `_pseudo` ({before?, after?, marker?} component-
        // shaped generated-content slots) as an OPAQUE JsonObject — the
        // extractor owns the payload shape and pseudo nodes never flatten
        // (design §4.2). Forwarded verbatim to the v2 `pseudos` field;
        // dropped by the legacy v1 serializer exactly as before.
        val pseudos = obj["_pseudo"]?.let { el ->
            if (el is kotlinx.serialization.json.JsonNull) null
            else el.jsonObject
        }

        return CssComponent(
            properties = props,
            selectors = selectors,
            media = media,
            children = children,
            text = text,
            role = role,
            tag = tag,
            pseudos = pseudos
        )
    }

    val components = componentsJson.mapValues { (_, node) ->
        parseComponent(node.jsonObject)
    }

    return CssComponents(components)
}

/**
 * Main entry point: parses JSON input into an IRDocument.
 *
 * Steps:
 * 1. Convert JSON to CssComponents intermediate model
 * 2. Parse each component's properties via PropertiesParser
 * 3. Parse selectors and media queries
 * 4. Generate unique IDs for each component
 * 5. Recursively parse children for SDUI support
 * 6. Assemble into IRDocument
 *
 * @param doc Raw JSON input containing component definitions
 * @return IRDocument with all parsed components and their properties
 */
fun cssParsing(doc: JsonObject): IRDocument {
    val components = JsonInputToCssComponents(doc)

    // Counter for generating unique component IDs
    var componentCounter = 0

    // Recursive function to convert CssComponent to IRComponent
    fun convertToIR(name: String, component: CssComponent): IRComponent {
        componentCounter++
        val id = "${name.lowercase().replace(" ", "-")}-${componentCounter.toString().padStart(3, '0')}"

        // Parse properties directly to specific property classes
        val properties = component.properties?.let { PropertiesParser.parse(it) } ?: mutableListOf()

        // Extract custom-property declarations (--name: value) from the SAME
        // base properties map into the component-level `variables` map.
        // PropertiesParser filtered these names out of the typed pipeline;
        // here they are lifted verbatim (case + raw value preserved — the
        // css-variables-1 §2 "untyped until substitution" rule) so the IR v2
        // wire can carry them as the additive `variables` envelope key.
        // null when the component declares none, so the serializer omits it.
        val variables = component.properties?.let {
            app.parsing.css.properties.CustomPropertyParser.extractVariables(it)
        }

        val selectors = parseSelectors(component.selectors)
        val media = parseMedia(component.media)

        // Recursively convert children. Map → List flattening happens here:
        // WPT input ships children as a name-keyed map (preserving stable
        // extractor IDs like `color-001__1__0`), but the renderer-facing IR
        // needs an array (matches IRComponent[] in
        // runtimes/web/src/core/ir/IRModels.ts so the JS renderer can
        // index positionally without a Map<String, IRComponent>). The
        // child's map key becomes its `name`, so the original ID survives
        // verbatim in the output.
        val children = component.children?.map { (childName, childComponent) ->
            convertToIR(childName, childComponent)
        }

        return IRComponent(
            id = id,
            name = name,
            properties = properties,
            selectors = selectors,
            media = media,
            children = children,
            // Forward the optional `_text` payload extracted by CssParsing.
            // Stays null for non-WPT fixtures (visual-test.json etc.) so the
            // IR serializer can omit the field and keep those existing
            // tmpOutput.json captures byte-stable.
            text = component.text,
            // Forward the optional `_role` marker (today only "body-root").
            // Stays null for non-WPT fixtures and for any WPT fixture whose
            // styled element isn't the document root, so the serializer can
            // omit the field and the BASELINE=1 byte-identical contract is
            // preserved.
            role = component.role,
            // Forward `_tag` / `_pseudo` for the IR v2 wire (meta.sourceTag
            // / pseudos). Both stay null on non-extractor fixtures; the
            // legacy v1 serializer ignores them regardless, so v1 output
            // bytes are untouched.
            tag = component.tag,
            pseudos = component.pseudos,
            // Forward the custom-property definitions (IR v2 `variables`
            // key). Stays null on fixtures without --* declarations; the
            // legacy v1 serializer ignores it either way, so v1 output
            // bytes are untouched.
            variables = variables
        )
    }

    val irComponents = components.components.map { (name, component) ->
        convertToIR(name, component)
    }
    return IRDocument(irComponents)
}