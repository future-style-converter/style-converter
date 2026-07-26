package com.styleconverter.runtime.core.ir

// IR wire reader for the Compose runtime — the Android side of the v2
// Slot & Placement contract (schema/ir-v2.schema.json + schema/spec/01,
// 03, 05). This is THE load path: both harness screens feed asset JSON
// through decode() below, so what this file enforces is what the runtime
// actually enforces (the whole point of the spec-04 caveat fix).
//
// Version discovery (spec 05):
//   - `irVersion` present  → v2 document → strict manual decode.
//   - `irVersion` absent   → v1 document (deprecation-window only) →
//     legacy tolerant kotlinx path, plus a deprecation warning.
//
// v2 tolerance rules implemented here (spec 05 "Tolerance rules"):
//   1. unknown property TYPE   → tolerated: kept as an opaque {type,data}
//      envelope; the applier skips + logs it (PropertyTracker) at use.
//   2. unknown ENVELOPE key    → loud IllegalArgumentException. This
//      replaces the v1-era blanket `ignoreUnknownKeys = true` that
//      silently dropped `_role` (spec 04 history).
//   3. unknown data leaf shape → extractor concern, not decode concern.
//   4. `slot` MUST round-trip  → decoded into IRComponent.slot verbatim
//      (default name "content" reconstructed per the schema).

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Minimal logger shim: android.util.Log on device, stdout under plain-JVM
 * unit tests (where the android.jar stub throws "not mocked"). Keeps the
 * decoder's loud-warning contract testable without Robolectric.
 */
internal object IRLog {
    fun warn(tag: String, msg: String) {
        try {
            android.util.Log.w(tag, msg)
        } catch (t: Throwable) {
            // JVM unit-test environment — android.util.Log is a stub.
            println("W/$tag: $msg")
        }
    }
}

object IRDocumentDecoder {

    /** The wire version this reader implements (spec 05). */
    const val READER_VERSION = 2

    private const val TAG = "IRDocumentDecoder"

    // Legacy v1 path: byte-identical behaviour to what both harness
    // screens did before the v2 freeze (Json { ignoreUnknownKeys = true }
    // straight into the @Serializable model) — that tolerance IS the v1
    // contract for the deprecation window.
    private val legacyJson = Json { ignoreUnknownKeys = true }

    // Shared parser for the version sniff + v2 manual walk.
    private val json = Json

    /**
     * Decode an IR document of either wire version.
     * v2 → strict envelope walk; v1 → legacy tolerant path + warning.
     */
    fun decode(jsonString: String): IRDocument {
        val root = json.parseToJsonElement(jsonString) as? JsonObject
            ?: throw IllegalArgumentException("IR document root must be a JSON object")
        return if ("irVersion" in root) {
            decodeV2(root)
        } else {
            // Deprecation window: a document WITHOUT irVersion IS v1
            // (spec 05 version discovery). Warn loudly so stale pipelines
            // surface before the window closes.
            IRLog.warn(TAG, "v1 IR document (no irVersion) — the nested-children wire is deprecated; re-emit with the v2 converter default")
            legacyJson.decodeFromString(IRDocument.serializer(), jsonString)
        }
    }

    // ── v2 strict path ──────────────────────────────────────────────────

    // Closed key sets per schema/ir-v2.schema.json (additionalProperties:
    // false on every envelope level). Anything outside these sets is a
    // contract the reader doesn't speak → error (tolerance rule 2).
    private val DOC_KEYS = setOf(
        "irVersion", "minReaderVersion", "components",
        // additive v2 minor revision (spec 07 §1.2): document-level named
        // @keyframes sets, omit-when-empty.
        "keyframes"
    )
    private val COMPONENT_KEYS = setOf(
        "id", "name", "properties", "selectors", "media",
        "slot", "text", "pseudos", "meta",
        // additive v2 minor revision: component-level custom-property
        // definitions ("--name" → raw value; spec 01 component table).
        "variables"
    )
    private val SLOT_KEYS = setOf("parent", "name")
    // Keyframe stop envelope (spec 07 §1.2 / schema $defs/keyframeStop —
    // additionalProperties: false, exactly these two keys).
    private val KEYFRAME_STOP_KEYS = setOf("offset", "properties")
    // Wave-20 (lane W2): `attrs` joined the meta group — the widget-identity
    // capsule for form/widget tags (wire contract pinned in IRAttrs' doc).
    private val META_KEYS = setOf("sourceTag", "role", "attrs")
    // The ten attributes the wave-20 wire contract allows inside meta.attrs;
    // anything else is a writer bug and errors like every strict envelope.
    private val ATTR_KEYS = setOf(
        "type", "value", "checked", "multiple", "size",
        "alt", "min", "max", "selected", "disabled"
    )
    private val PROPERTY_KEYS = setOf("type", "data")
    private val SELECTOR_KEYS = setOf("condition", "properties")
    private val MEDIA_KEYS = setOf("query", "properties")

    /** Throw on the first key outside [allowed] — names the offender. */
    private fun requireOnlyKeys(obj: JsonObject, allowed: Set<String>, where: String) {
        val unknown = obj.keys.firstOrNull { it !in allowed }
        require(unknown == null) {
            "unknown $where key '$unknown' — IR v2 envelopes are strict (spec 05 tolerance rule 2); allowed: $allowed"
        }
    }

    private fun decodeV2(obj: JsonObject): IRDocument {
        // Document envelope: exactly {irVersion, minReaderVersion, components}.
        requireOnlyKeys(obj, DOC_KEYS, "document")
        // Version pair (spec 01): writers stamp both; this reader mirrors
        // the converter reference reader (IRWireV2.decodeDocument) —
        // refuse any irVersion other than 2 AND any minReaderVersion > 2,
        // loudly, naming both versions (spec 05: refusal is never silent).
        val irVersion = (obj["irVersion"] as? JsonPrimitive)?.intOrNull
            ?: throw IllegalArgumentException("irVersion must be an integer")
        require(irVersion == READER_VERSION) {
            "unsupported irVersion $irVersion (this reader implements $READER_VERSION)"
        }
        val minReader = (obj["minReaderVersion"] as? JsonPrimitive)?.intOrNull
            ?: throw IllegalArgumentException("v2 document missing minReaderVersion")
        require(minReader <= READER_VERSION) {
            "document requires reader >= $minReader, this reader implements $READER_VERSION"
        }
        val componentsEl = obj["components"] as? JsonArray
            ?: throw IllegalArgumentException("v2 document missing components array")
        // Flat list — every entry decodes independently, no recursion.
        return IRDocument(
            components = componentsEl.map { el ->
                decodeComponentV2(
                    el as? JsonObject ?: throw IllegalArgumentException("component entries must be objects")
                )
            },
            keyframes = decodeKeyframes(obj["keyframes"])
        )
    }

    /**
     * Decode the document-level `keyframes` envelope key (spec 07 §1.2 /
     * schema $defs/keyframeSet + keyframeStop). Strict like every other
     * envelope level: name → non-empty stop array; each stop is exactly
     * {offset, properties} with offset a number in [0, 1]. Stop property
     * payloads keep the component tolerance posture — unknown TYPES pass
     * through as opaque envelopes for the applier to skip + log.
     */
    private fun decodeKeyframes(el: kotlinx.serialization.json.JsonElement?): Map<String, List<IRKeyframeStop>>? {
        if (el == null) return null // omit-when-empty wire rule → no keyframes
        val obj = el as? JsonObject
            ?: throw IllegalArgumentException("document 'keyframes' must be an object of name → stop list")
        // Schema pins minProperties 1 — an empty map is a writer bug.
        require(obj.isNotEmpty()) { "document 'keyframes' present but empty (minProperties 1)" }
        return obj.mapValues { (name, setEl) ->
            val stops = setEl as? JsonArray
                ?: throw IllegalArgumentException("keyframes '$name' must be an array of stops")
            // Schema pins minItems 1 — fully-invalid sets are dropped by the
            // CONVERTER, so an empty set reaching a reader is a writer bug.
            require(stops.isNotEmpty()) { "keyframes '$name' present but empty (minItems 1)" }
            stops.map { stopEl ->
                val so = stopEl as? JsonObject
                    ?: throw IllegalArgumentException("keyframes '$name': stops must be objects")
                requireOnlyKeys(so, KEYFRAME_STOP_KEYS, "keyframe stop (in '$name')")
                val offset = (so["offset"] as? JsonPrimitive)?.doubleOrNull
                    ?: throw IllegalArgumentException("keyframes '$name': stop missing numeric 'offset'")
                // Offsets are RESOLVED fractions (schema: 0 ≤ offset ≤ 1);
                // out-of-range means the converter's drop rule was bypassed.
                require(offset in 0.0..1.0) {
                    "keyframes '$name': stop offset $offset outside [0, 1] (spec 07 §1.2 resolved-fraction rule)"
                }
                IRKeyframeStop(
                    offset = offset,
                    properties = decodePropertyList(so["properties"], "@keyframes $name")
                )
            }
        }
    }

    private fun decodeComponentV2(c: JsonObject): IRComponent {
        val idForError = (c["id"] as? JsonPrimitive)?.contentOrNull ?: "<no id>"
        // Dedicated error for the one key everyone will reach for first:
        // `children` does not exist in v2 (spec 03 flat-list rule). Check
        // BEFORE the generic strict walk so the message teaches the fix.
        require("children" !in c) {
            "invalid v2 component '$idForError': `children` does not exist in IR v2 — flat list + slot refs only (schema/spec/03-children.md)"
        }
        requireOnlyKeys(c, COMPONENT_KEYS, "component ('$idForError')")

        // id/name/properties are required (schema `required` list).
        val id = (c["id"] as? JsonPrimitive)?.contentOrNull
            ?: throw IllegalArgumentException("component missing required 'id'")
        val name = (c["name"] as? JsonPrimitive)?.contentOrNull
            ?: throw IllegalArgumentException("component '$id' missing required 'name'")
        val propsEl = c["properties"] as? JsonArray
            ?: throw IllegalArgumentException("component '$id' missing required 'properties' array")

        // Property wrappers: strict {type, data} envelopes. The TYPE value
        // itself is NOT validated here — unknown types must be tolerated
        // (spec 05 rule 1); the applier skips them with a PropertyTracker
        // log at use. The generic JsonElement `data` carries any leaf.
        val properties = propsEl.map { p ->
            val po = p as? JsonObject
                ?: throw IllegalArgumentException("component '$id': property entries must be objects")
            requireOnlyKeys(po, PROPERTY_KEYS, "property (in '$id')")
            IRProperty(
                type = (po["type"] as? JsonPrimitive)?.contentOrNull
                    ?: throw IllegalArgumentException("component '$id': property missing 'type'"),
                data = po["data"]
                    ?: throw IllegalArgumentException("component '$id': property missing 'data'")
            )
        }

        // selectors / media buckets: same strict two-key envelopes as v1,
        // omit-when-empty on the wire → default empty lists here.
        val selectors = (c["selectors"] as? JsonArray)?.map { s ->
            val so = s.jsonObject
            requireOnlyKeys(so, SELECTOR_KEYS, "selector (in '$id')")
            IRSelector(
                condition = (so["condition"] as? JsonPrimitive)?.contentOrNull
                    ?: throw IllegalArgumentException("component '$id': selector missing 'condition'"),
                properties = decodePropertyList(so["properties"], id)
            )
        } ?: emptyList()
        val media = (c["media"] as? JsonArray)?.map { m ->
            val mo = m.jsonObject
            requireOnlyKeys(mo, MEDIA_KEYS, "media (in '$id')")
            IRMedia(
                query = (mo["query"] as? JsonPrimitive)?.contentOrNull
                    ?: throw IllegalArgumentException("component '$id': media missing 'query'"),
                properties = decodePropertyList(mo["properties"], id)
            )
        } ?: emptyList()

        // slot: structural, MUST round-trip (tolerance rule 4). Absent
        // name reconstructs the schema-documented default "content".
        val slot = (c["slot"] as? JsonObject)?.let { s ->
            requireOnlyKeys(s, SLOT_KEYS, "slot (in '$id')")
            IRSlot(
                parent = (s["parent"] as? JsonPrimitive)?.contentOrNull
                    ?: throw IllegalArgumentException("component '$id': slot without 'parent'"),
                name = (s["name"] as? JsonPrimitive)?.contentOrNull ?: "content"
            )
        }

        // meta: droppable hints, grouped — strict AND non-empty when
        // present (schema minProperties: 1; an empty meta is a writer bug).
        val meta = (c["meta"] as? JsonObject)?.also { m ->
            requireOnlyKeys(m, META_KEYS, "meta (in '$id')")
            require(m.isNotEmpty()) { "component '$id': meta present but empty (minProperties 1)" }
        }

        return IRComponent(
            id = id,
            name = name,
            properties = properties,
            selectors = selectors,
            media = media,
            // v2 is flat by construction; SlotComposer repopulates the
            // in-memory tree from the slot refs before rendering.
            children = null,
            // v2 wire renames → v1-era in-memory field names (the renderer
            // and its pinning suite are untouched by the wire rename):
            //   text           → _text   (empty string is meaningful)
            //   meta.sourceTag → _tag
            //   meta.role      → role    (new — v1 dropped `_role` entirely)
            _text = (c["text"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull,
            _tag = (meta?.get("sourceTag") as? JsonPrimitive)?.contentOrNull,
            // meta.attrs → attrs (wave-20 widget capsule, `_tag` precedent).
            attrs = decodeAttrs(meta?.get("attrs"), id),
            slot = slot,
            // pseudos: opaque component-shaped payload forwarded verbatim —
            // generated content never flattens (design §4.2).
            pseudos = c["pseudos"] as? JsonObject,
            role = (meta?.get("role") as? JsonPrimitive)?.contentOrNull,
            // variables: "--name" → raw string map, forwarded VERBATIM —
            // names are case-sensitive and values untyped until var()
            // substitution (css-variables-1 §2; spec 02 custom-properties
            // section). Resolution (element → slot-parent chain →
            // fallback) is the style engine's job, not the decoder's.
            variables = (c["variables"] as? JsonObject)?.mapValues { (_, el) ->
                (el as? JsonPrimitive)?.contentOrNull
                    ?: throw IllegalArgumentException("component '$id': variables values must be strings")
            }
        )
    }

    /**
     * Decode the wave-20 `meta.attrs` widget capsule (lane W2). Strict on
     * the key set (ATTR_KEYS — the wire contract pins exactly ten legal
     * attributes) but tolerant on VALUE shape within the contract: the
     * boolean four decode as booleans, min/max as numbers, and `value`
     * keeps BOTH channels (string verbatim + numeric when the wire sent a
     * number — meter/progress) so no reader ever re-parses stringly.
     */
    private fun decodeAttrs(el: kotlinx.serialization.json.JsonElement?, ownerId: String): IRAttrs? {
        if (el == null) return null // meta without attrs — the common case
        val o = el as? JsonObject
            ?: throw IllegalArgumentException("component '$ownerId': meta.attrs must be an object")
        requireOnlyKeys(o, ATTR_KEYS, "meta.attrs (in '$ownerId')")
        // Primitive-or-null accessor: attrs values are always primitives
        // (strings/booleans/numbers per the contract), never nested.
        fun p(k: String) = o[k] as? JsonPrimitive
        // Non-string gate for the typed channels: kotlinx's doubleOrNull/
        // booleanOrNull ALSO parse quoted strings ("5" → 5.0, "true" →
        // true) but the iOS reader (IRAttrs.from over IRValue.doubleValue/
        // boolValue) never coerces strings — the twin decoders must agree
        // on which channels fill for every wire shape, or an off-contract
        // writer (numbers as strings) paints different widgets per native
        // (wave-20 skeptic alignment; contract pins these as JSON
        // numbers/booleans, so strings simply stay on the string channel).
        fun num(k: String) = p(k)?.takeIf { !it.isString }?.doubleOrNull
        fun bool(k: String) = p(k)?.takeIf { !it.isString }?.booleanOrNull
        return IRAttrs(
            type = p("type")?.contentOrNull,
            // String channel: verbatim wire content (numbers stringify —
            // contentOrNull yields the literal for numeric primitives).
            value = p("value")?.contentOrNull,
            // Numeric channel: only when the wire primitive IS a number
            // (meter/progress value) — see the non-string gate above.
            valueNumber = num("value"),
            checked = bool("checked"),
            multiple = bool("multiple"),
            selected = bool("selected"),
            disabled = bool("disabled"),
            size = p("size")?.contentOrNull,
            alt = p("alt")?.contentOrNull,
            min = num("min"),
            max = num("max")
        )
    }

    /** Shared strict property-list walk for selector/media buckets. */
    private fun decodePropertyList(el: kotlinx.serialization.json.JsonElement?, ownerId: String): List<IRProperty> {
        val arr = el as? JsonArray
            ?: throw IllegalArgumentException("component '$ownerId': bucket missing 'properties' array")
        return arr.map { p ->
            val po = p as? JsonObject
                ?: throw IllegalArgumentException("component '$ownerId': property entries must be objects")
            requireOnlyKeys(po, PROPERTY_KEYS, "property (in '$ownerId')")
            IRProperty(
                type = (po["type"] as? JsonPrimitive)?.contentOrNull
                    ?: throw IllegalArgumentException("component '$ownerId': property missing 'type'"),
                data = po["data"]
                    ?: throw IllegalArgumentException("component '$ownerId': property missing 'data'")
            )
        }
    }
}
