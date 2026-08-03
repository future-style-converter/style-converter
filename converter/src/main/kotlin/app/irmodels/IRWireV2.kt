package app.irmodels

// IR v2 wire codec — the flat-list Slot & Placement contract.
//
// This file owns the v2 byte shapes the three runtimes consume:
//   { "irVersion": 2, "minReaderVersion": 2, "components": [ ...flat... ] }
//
// Normative prose: schema/spec/01-envelope.md (envelope) and
// schema/spec/03-children.md (flat slot + placement contract).
// Machine contract: schema/ir-v2.schema.json.
//
// v2 vs v1 component deltas (all riding the one sanctioned wire break):
//   - components is a FLAT array; `children` does not exist (hard error)
//   - child→parent composition via `slot: {parent, name?}` on the CHILD
//   - `_text`  → `text`    (structural content, not a droppable hint)
//   - `_pseudo`→ `pseudos` (generated content stays embedded, renamed)
//   - `_tag` + `_role` → `meta: {sourceTag?, role?}` (droppable hints,
//     grouped so the component surface stops accreting underscore keys)
//   - `_attrs` → `meta.attrs` (wave-20) and `_decorations` →
//     `meta.decorations` (wave-22): later additive meta members, both
//     forwarded VERBATIM (spec 05 additive meta-key rule)
//
// The legacy v1 codec (IRComponentSerializer in IRDocument.kt) stays
// untouched for the deprecation window behind `--emit-ir v1`.

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.int

/** Version constants for the v2 envelope (schema/spec/05-versioning.md). */
object IRWireV2 {
    /** What this writer produces. */
    const val IR_VERSION = 2
    /** Oldest reader that can safely consume what this writer produces. */
    const val MIN_READER_VERSION = 2

    // Shared Json instance for element-level encode/decode inside the
    // custom serializer (per-call Json {} construction is needless churn).
    private val json = Json

    /**
     * Encode a FLAT IRDocument (post-IRFlattener) as the complete v2 wire
     * envelope. Fails loudly if any component still carries nested
     * children — that means the flattener was skipped, and silently
     * emitting a nested tree under a v2 version stamp would be a lie.
     */
    fun encodeDocument(doc: IRDocument): JsonObject {
        // Guard: v2 documents are flat BY CONSTRUCTION. A children list
        // here is a converter bug, not an input problem — hence error.
        doc.components.forEach { c ->
            require(c.children.isNullOrEmpty()) {
                "IR v2 encode requires a flat document — component '${c.id}' still has nested children (run IRFlattener first)"
            }
        }
        return buildJsonObject {
            // Version discriminator pair, always first (spec 01):
            // writers stamp both; readers refuse minReaderVersion > self.
            put("irVersion", IR_VERSION)
            put("minReaderVersion", MIN_READER_VERSION)
            // Flat component array — sibling order in this array IS the
            // composition order (spec 03 sibling-order rule).
            put("components", json.encodeToJsonElement(
                kotlinx.serialization.builtins.ListSerializer(IRComponentV2Serializer),
                doc.components
            ))
            // keyframes: document-level named keyframe sets (wave 8 —
            // schema/spec/07-animations.md). ADDITIVE minor-revision key
            // (spec 05): omit-when-empty so every pre-motion document
            // stays byte-identical. Each set is already offset-sorted by
            // the converter (CssParsing.convertKeyframesToIR); the stop
            // shape {offset: 0..1, properties: [{type,data}…]} comes from
            // IRKeyframeStop's plugin serializer + IRPropertySerializer —
            // the same property-envelope bytes components carry.
            if (!doc.keyframes.isNullOrEmpty()) {
                put("keyframes", buildJsonObject {
                    doc.keyframes.forEach { (name, stops) ->
                        put(name, json.encodeToJsonElement(
                            kotlinx.serialization.builtins.ListSerializer(IRKeyframeStop.serializer()),
                            stops
                        ))
                    }
                })
            }
        }
    }

    /**
     * Decode a v2 envelope back into a flat IRDocument. Structural fields
     * (id/name/slot/text/pseudos/meta) round-trip; property payload
     * decoding remains the documented IRPropertySerializer stub, same as
     * the v1 reader (tools that need full property round-trip read the
     * JSON directly).
     */
    fun decodeDocument(obj: JsonObject): IRDocument {
        // The version pair is mandatory in v2 — a missing irVersion means
        // this is a v1 document and belongs to the legacy reader.
        val irVersion = obj["irVersion"]?.jsonPrimitive?.int
            ?: throw IllegalArgumentException("not a v2 document: missing irVersion")
        require(irVersion == IR_VERSION) { "unsupported irVersion $irVersion (reader implements $IR_VERSION)" }
        val minReader = obj["minReaderVersion"]?.jsonPrimitive?.int
            ?: throw IllegalArgumentException("v2 document missing minReaderVersion")
        // Reader MUST refuse documents demanding a newer reader (spec 05).
        require(minReader <= IR_VERSION) { "document requires reader >= $minReader, this reader implements $IR_VERSION" }
        // Every entry decodes independently — flat list, no recursion.
        val components = obj["components"]?.jsonArray?.map { el ->
            json.decodeFromJsonElement(IRComponentV2Serializer, el)
        } ?: emptyList()
        // keyframes: structural fields (set names, stop offsets) round-trip;
        // stop property payloads remain the documented IRPropertySerializer
        // stub, exactly like component properties in this codec (tools that
        // need full property round-trip read the JSON directly).
        val keyframes = obj["keyframes"]?.jsonObject?.mapValues { (_, stopsEl) ->
            stopsEl.jsonArray.map { stopEl ->
                val stopObj = stopEl.jsonObject
                IRKeyframeStop(
                    offset = stopObj["offset"]?.jsonPrimitive?.content?.toDouble()
                        ?: throw IllegalArgumentException("keyframe stop without offset"),
                    properties = mutableListOf() // property decode stub — see class note
                )
            }
        }
        return IRDocument(components, keyframes = keyframes)
    }
}

/**
 * v2 component codec. Emission key order (spec 01, v2 table):
 * id, name, properties, variables, selectors, media, slot, text, pseudos,
 * meta. Omit-when-empty rules carry over from v1 for selectors/media; the
 * new optional fields are omit-when-null (slot/text/pseudos), meta is
 * omitted when BOTH of its members are null, and variables (additive
 * minor-revision key — custom-property definitions) is omit-when-empty.
 */
object IRComponentV2Serializer : KSerializer<IRComponent> {
    // Flat descriptor — slot/text/pseudos/meta are written dynamically in
    // serialize() exactly like the v1 serializer handles its optionals.
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("IRComponentV2") {
        element<String>("id")
        element<String>("name")
        element<kotlin.collections.List<IRProperty>>("properties")
    }

    override fun serialize(encoder: Encoder, value: IRComponent) {
        require(encoder is JsonEncoder) { "This serializer only works with JSON" }
        val json = encoder.json
        // A v2 entry must be flat — nested children may never reach the
        // wire under this codec (defense-in-depth with IRWireV2.encodeDocument).
        require(value.children.isNullOrEmpty()) {
            "v2 component '${value.id}' carries nested children — v2 is flat-only"
        }
        encoder.encodeJsonElement(buildJsonObject {
            // id/name/properties: identical semantics to v1 — always present.
            put("id", value.id)
            put("name", value.name)
            put("properties", json.encodeToJsonElement(
                kotlinx.serialization.builtins.ListSerializer(IRPropertySerializer),
                value.properties
            ))
            // variables: component-level custom-property definitions
            // ("--name" → RAW declaration value, verbatim — names keep
            // their case, values keep their untyped token stream per
            // css-variables-1 §2). ADDITIVE v2 envelope key (spec 05
            // minor-revision process); omit-when-empty so documents
            // without --* declarations stay byte-identical to the
            // pre-variables emission. Insertion order == authoring order.
            if (!value.variables.isNullOrEmpty()) {
                put("variables", buildJsonObject {
                    value.variables.forEach { (name, raw) -> put(name, raw) }
                })
            }
            // selectors/media: omit-when-empty, exactly the v1 rule.
            if (value.selectors.isNotEmpty()) {
                put("selectors", json.encodeToJsonElement(
                    kotlinx.serialization.builtins.ListSerializer(IRSelector.serializer()),
                    value.selectors
                ))
            }
            if (value.media.isNotEmpty()) {
                put("media", json.encodeToJsonElement(
                    kotlinx.serialization.builtins.ListSerializer(IRMedia.serializer()),
                    value.media
                ))
            }
            // slot: structural child→parent reference, roots omit it.
            // `name` is omitted when it is the default "content" — the
            // schema documents the default so readers reconstruct it.
            value.slot?.let { slot ->
                put("slot", buildJsonObject {
                    put("parent", slot.parent)
                    if (slot.name != "content") put("name", slot.name)
                })
            }
            // text: v2 rename of `_text`. Same omit-when-null + empty
            // string is a legal value ("extracted, was empty").
            if (value.text != null) put("text", value.text)
            // pseudos: v2 rename of `_pseudo`. Opaque component-shaped
            // {before?, after?, marker?} payload forwarded verbatim —
            // pseudo nodes never flatten (design §4.2).
            value.pseudos?.let { put("pseudos", it) }
            // meta: droppable renderer hints, grouped. Omitted entirely
            // when no member has a value so hint-free fixtures stay lean.
            if (value.tag != null || value.role != null || value.attrs != null ||
                value.decorations != null || value.markerText != null
            ) {
                put("meta", buildJsonObject {
                    // sourceTag: v2 home of the extractor's `_tag` hint.
                    value.tag?.let { put("sourceTag", it) }
                    // role: v2 home of `_role` (today only "body-root").
                    value.role?.let { put("role", it) }
                    // attrs (wave-20 W1): v2 home of `_attrs` — the widget-
                    // identity attribute object, forwarded VERBATIM (the
                    // extractor owns keys and value typing; spec 05 additive
                    // meta-key rule).
                    value.attrs?.let { put("attrs", it) }
                    // decorations (wave-22 lane DECOR): v2 home of
                    // `_decorations` — the collapsed inline run's ordered
                    // per-line list, forwarded VERBATIM (the extractor owns
                    // entry shape and colour-token spelling; spec 05
                    // additive meta-key rule, the meta.attrs precedent).
                    value.decorations?.let { put("decorations", it) }
                    // markerText (wave-27 lane CBAKE): v2 home of
                    // `_markerText` — the resolved list-marker string for a
                    // `<li>`, forwarded VERBATIM (the extractor owns the
                    // css-counter-styles-3 §6 spelling; spec 05 additive
                    // meta-key rule, the meta.attrs precedent).
                    value.markerText?.let { put("markerText", it) }
                })
            }
        })
    }

    override fun deserialize(decoder: Decoder): IRComponent {
        require(decoder is JsonDecoder) { "This serializer only works with JSON" }
        val obj = decoder.decodeJsonElement().jsonObject
        // HARD ERROR on `children` — spec 03: a v2 document containing a
        // children key anywhere is invalid, not quietly tolerated.
        require("children" !in obj) {
            "invalid v2 component '${obj["id"]?.jsonPrimitive?.content}': `children` does not exist in IR v2 (flat list + slot refs only)"
        }
        // Optional slot object → IRSlot; absent name reconstructs the
        // documented default "content".
        val slot = obj["slot"]?.jsonObject?.let { s ->
            IRSlot(
                parent = s["parent"]?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("slot without parent on '${obj["id"]?.jsonPrimitive?.content}'"),
                name = s["name"]?.jsonPrimitive?.content ?: "content"
            )
        }
        // meta group → individual Kotlin fields (grouped only on the wire).
        val meta = obj["meta"]?.jsonObject
        return IRComponent(
            id = obj["id"]?.jsonPrimitive?.content ?: "",
            name = obj["name"]!!.jsonPrimitive.content,
            // Property payload decoding remains a stub (see the v1 reader
            // note in IRDocument.kt) — structural fields are the contract
            // this codec round-trips.
            properties = mutableListOf(),
            selectors = emptyList(),
            media = emptyList(),
            children = null,
            // `text`: missing/JSON-null → null; empty string preserved.
            text = obj["text"]?.let { el -> if (el is JsonNull) null else el.jsonPrimitive.content },
            role = meta?.get("role")?.let { el -> if (el is JsonNull) null else el.jsonPrimitive.content },
            slot = slot,
            tag = meta?.get("sourceTag")?.let { el -> if (el is JsonNull) null else el.jsonPrimitive.content },
            // attrs (wave-20 W1): opaque round-trip — JSON null ≡ absent,
            // any object comes back byte-verbatim (readers key on the tag).
            attrs = meta?.get("attrs")?.let { el -> if (el is JsonNull) null else el.jsonObject },
            // decorations (wave-22 lane DECOR): opaque round-trip — JSON
            // null ≡ absent, any array comes back byte-verbatim (the
            // runtimes, not this codec, interpret line + colour tokens).
            decorations = meta?.get("decorations")?.let { el ->
                if (el is JsonNull) null else el.jsonArray
            },
            // markerText (wave-27 lane CBAKE): opaque round-trip — JSON
            // null ≡ absent, any string comes back byte-verbatim (the
            // runtimes render it; this codec never re-derives it).
            markerText = meta?.get("markerText")?.let { el ->
                if (el is JsonNull) null else el.jsonPrimitive.content
            },
            pseudos = obj["pseudos"]?.jsonObject,
            // variables: "--name" → raw string map, round-tripped verbatim
            // (both key case and value bytes). Missing key → null so a
            // re-encode omits it identically.
            variables = obj["variables"]?.jsonObject?.mapValues { (_, el) -> el.jsonPrimitive.content }
        )
    }
}
