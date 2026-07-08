package app.irmodels

import app.parsing.css.properties.GenericProperty
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer

/**
 * Custom serializer for IRProperty that handles all specific property types.
 *
 * ## Purpose
 * Provides unified JSON serialization for all 567+ IRProperty implementations without
 * requiring manual registration of each type. Uses Kotlin reflection to dynamically
 * discover and invoke the appropriate serializer for each property type.
 *
 * ## Output Format
 * Each property is serialized as:
 * ```json
 * {
 *   "type": "PropertyTypeName",  // e.g., "Color", "Width", "FontSize"
 *   "data": { ... }              // Property-specific data (flattened)
 * }
 * ```
 *
 * ## Flattening Strategy
 * The serializer applies two levels of flattening to reduce JSON nesting:
 *
 * 1. **Single-field flattening**: If a property has only one field, unwrap it
 *    - Before: `{"value": {"srgb": {...}}}`
 *    - After: `{"srgb": {...}}`
 *
 * 2. **Deep flattening**: For sealed interfaces with type discriminator + nested object
 *    - Before: `{"type": "length", "length": {"px": 10}}`
 *    - After: `{"type": "length", "px": 10}`
 *
 * ## Special Cases
 * - **GenericProperty**: Serialized with `_unmapped: true` flag for unparsed CSS
 * - **Serialization errors**: Captured with `_serializationError` field instead of failing
 *
 * ## Example Output
 * ```json
 * // Color property
 * {"type": "Color", "data": {"srgb": {"r": 1.0, "g": 0.0, "b": 0.0}, "original": "#f00"}}
 *
 * // Width property with length
 * {"type": "Width", "data": {"type": "length", "px": 100.0}}
 *
 * // Generic/unparsed property
 * {"type": "Generic", "data": {"propertyName": "unknown-prop", "rawValue": "...", "_unmapped": true}}
 * ```
 *
 * @see IRProperty Base interface for all property types
 * @see GenericProperty Fallback for unparsed CSS properties
 */
object IRPropertySerializer : KSerializer<IRProperty> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("IRProperty") {
        element<String>("type")
        element<JsonElement>("data")
    }

    @OptIn(InternalSerializationApi::class)
    @Suppress("UNCHECKED_CAST")
    override fun serialize(encoder: Encoder, value: IRProperty) {
        require(encoder is JsonEncoder) { "This serializer only works with JSON" }

        val jsonEncoder = encoder as JsonEncoder
        val json = jsonEncoder.json

        // Serialize the specific property type to JSON
        val jsonElement = when (value) {
            // Handle GenericProperty specially
            is GenericProperty -> {
                buildJsonObject {
                    put("propertyName", value.propertyName)
                    put("rawValue", value.rawValue)
                    put("_unmapped", true)
                }
            }

            // For all other @Serializable properties, use dynamic serialization
            else -> {
                try {
                    // Get the serializer for the actual runtime type
                    val serializer = value::class.serializer() as KSerializer<Any>
                    json.encodeToJsonElement(serializer, value)
                } catch (e: Exception) {
                    // Fallback if serialization fails
                    buildJsonObject {
                        put("propertyName", value.propertyName)
                        put("_serializationError", e.message ?: "Unknown error")
                    }
                }
            }
        }

        // Flatten nested field names: if data is an object with a single key, extract its value
        val flattenedData = if (jsonElement is JsonObject && jsonElement.size == 1) {
            jsonElement.values.first()
        } else {
            jsonElement
        }

        // Deep flatten: for sealed interfaces with type discriminator + single nested object field,
        // inline the nested object's fields (e.g., {"type": "length", "length": {"v":...}} → {"type": "length", "v":...})
        val deepFlattenedData = deepFlatten(flattenedData)

        // Wrap with type information (strip redundant "Property" suffix)
        // For nested classes (e.g., TransformOriginProperty.Values), use the outer class name
        val typeName = getPropertyTypeName(value)
        val output = buildJsonObject {
            put("type", typeName)
            put("data", deepFlattenedData)
        }

        jsonEncoder.encodeJsonElement(output)
    }

    override fun deserialize(decoder: Decoder): IRProperty {
        // Deserialization not needed for our use case (we only write, not read)
        throw NotImplementedError("Deserialization of IRProperty not implemented")
    }

    /**
     * Extracts the property type name, handling nested classes properly.
     * For nested classes like TransformOriginProperty.Values, returns "TransformOrigin".
     * For top-level classes like ColorProperty, returns "Color".
     */
    private fun getPropertyTypeName(value: IRProperty): String {
        val qualifiedName = value::class.qualifiedName ?: return "Unknown"

        // Extract the class hierarchy from qualified name
        // e.g., "app.irmodels.properties.transforms.TransformOriginProperty.Values"
        val parts = qualifiedName.split(".")

        // Find the part that ends with "Property" (the outer sealed interface/class)
        val propertyPart = parts.findLast { it.endsWith("Property") }

        return if (propertyPart != null) {
            // Return the property name without "Property" suffix
            propertyPart.removeSuffix("Property")
        } else {
            // Fallback to simple name without "Property" suffix
            (value::class.simpleName ?: "Unknown").removeSuffix("Property")
        }
    }

    /**
     * Deep flattens JSON elements by inlining single nested object fields.
     * Handles sealed interface patterns like {"type": "length", "length": {"v":...}} → {"type": "length", "v":...}
     */
    private fun deepFlatten(element: JsonElement): JsonElement {
        return when (element) {
            is JsonObject -> {
                // Check if this is a sealed interface pattern: has "type" + exactly one other field that's an object
                val typeField = element["type"]
                val otherFields = element.filterKeys { it != "type" }

                if (typeField != null && otherFields.size == 1) {
                    val (fieldName, fieldValue) = otherFields.entries.first()
                    if (fieldValue is JsonObject) {
                        // Inline the nested object fields alongside the type
                        buildJsonObject {
                            put("type", typeField)
                            fieldValue.forEach { (k, v) -> put(k, deepFlatten(v)) }
                        }
                    } else {
                        // Single non-object field - keep as is but recurse
                        buildJsonObject {
                            element.forEach { (k, v) -> put(k, deepFlatten(v)) }
                        }
                    }
                } else {
                    // Not a sealed interface pattern - just recurse into children
                    buildJsonObject {
                        element.forEach { (k, v) -> put(k, deepFlatten(v)) }
                    }
                }
            }
            is JsonArray -> {
                // Recurse into array elements
                JsonArray(element.map { deepFlatten(it) })
            }
            else -> element // Primitives stay as-is
        }
    }
}
