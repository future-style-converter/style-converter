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
        "keyframes",
        // wave-34 lane F2, same additive rule (spec 01 §5): document-level
        // @font-face declarations, omit-when-empty.
        "fontFaces"
    )
    // The four descriptors ONE `fontFaces` entry may carry (schema
    // ir-v2.schema.json $defs/fontFace — additionalProperties:false).
    private val FONT_FACE_KEYS = setOf("family", "src", "weight", "style")
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
    // Wave-22 (lane DECOR): `decorations` joined it too — the collapsed
    // inline run's ordered per-line list (contract in IRDecoration's doc).
    // Wave-27 (lane CBAKE): `markerText` joined it — the resolved list-
    // marker string for one `<li>` (contract in [IRComponent.markerText]).
    // Wave-32 (lane R): `runs` joined it — the ordered inline-content list
    // for a component whose own text interleaves with its kept children
    // (contract in [IRRun] + schema/spec/03-children.md §4.1).
    // Wave-37 (lane W4): `lang` joined it — the element's COMPUTED content
    // language, already resolved by the producer (contract in
    // [IRComponent.lang] + schema/spec/04-metadata-fields.md).
    private val META_KEYS = setOf(
        "sourceTag", "role", "attrs", "decorations", "markerText", "lang", "runs"
    )
    // The two keys ONE `meta.decorations` entry may carry (schema
    // ir-v2.schema.json meta.decorations.items, additionalProperties:false).
    private val DECORATION_KEYS = setOf("line", "color")
    // The two keys ONE `meta.runs` entry may carry, of which exactly one is
    // present (schema ir-v2.schema.json meta.runs.items — minProperties 1,
    // maxProperties 1, additionalProperties false).
    private val RUN_KEYS = setOf("text", "child")
    // The attributes the wire contract allows inside meta.attrs; anything
    // else is a writer bug and errors like every strict envelope.
    // Wave-27 (lane CBAKE) added `start` for the disjoint ol/li ordinal
    // lane (HTML §4.4.5) — same envelope, verbatim-string typing.
    // Wave-36 (lane M1) added `src` for the disjoint img/embed/object/video
    // replaced-source lane — same envelope, verbatim-string typing. Adding
    // it here is what keeps the strict decoder from REJECTING a document the
    // web consumer needs; see IRAttrs.src for why Compose decodes but does
    // not yet paint it.
    private val ATTR_KEYS = setOf(
        "type", "value", "checked", "multiple", "size",
        "alt", "min", "max", "selected", "disabled", "start", "src"
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
            keyframes = decodeKeyframes(obj["keyframes"]),
            fontFaces = decodeFontFaces(obj["fontFaces"])
        )
    }

    /**
     * Decode the document-level `fontFaces` envelope key (spec 01 §5 /
     * schema $defs/fontFace). Strict like every other v2 envelope level:
     * a non-empty array of objects carrying only the four known descriptors,
     * of which `family` and `src` are REQUIRED non-blank strings.
     *
     * Strictness is deliberate even though the runtime does not yet USE the
     * result. The tolerance rule that lets a reader skip an unknown property
     * TYPE (spec 05) is about the 550-property value surface; an envelope
     * structure this reader claims to speak must either be well-formed or
     * fail loudly, or a writer bug would sit undetected until the wave that
     * finally wires face registration.
     */
    private fun decodeFontFaces(el: kotlinx.serialization.json.JsonElement?): List<IRFontFace>? {
        if (el == null) return null // omit-when-empty wire rule → no faces
        val arr = el as? JsonArray
            ?: throw IllegalArgumentException("document 'fontFaces' must be an array of @font-face entries")
        // Schema pins minItems 1 — an empty array is a writer bug.
        require(arr.isNotEmpty()) { "document 'fontFaces' present but empty (minItems 1)" }
        return arr.mapIndexed { i, faceEl ->
            val f = faceEl as? JsonObject
                ?: throw IllegalArgumentException("fontFaces[$i] must be an object")
            requireOnlyKeys(f, FONT_FACE_KEYS, "fontFaces[$i]")
            val family = (f["family"] as? JsonPrimitive)?.contentOrNull
            require(!family.isNullOrBlank()) { "fontFaces[$i] missing non-empty 'family'" }
            val src = (f["src"] as? JsonPrimitive)?.contentOrNull
            require(!src.isNullOrBlank()) { "fontFaces[$i] missing non-empty 'src'" }
            IRFontFace(
                family = family,
                src = src,
                // Absent = the css-fonts-4 initial `normal`; never defaulted
                // to a literal here, so "omitted" and "explicitly normal"
                // stay distinguishable for the future registration hop.
                weight = (f["weight"] as? JsonPrimitive)?.contentOrNull,
                style = (f["style"] as? JsonPrimitive)?.contentOrNull
            )
        }
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
            // meta.decorations → decorations (wave-22 per-line list, the
            // same additive meta channel). Absent stays null — which is
            // what makes "present but empty" a DIFFERENT state downstream.
            decorations = decodeDecorations(meta?.get("decorations"), id),
            // meta.markerText → markerText (wave-27 baked list marker, the
            // same additive meta channel as attrs/decorations). A plain
            // string forwarded verbatim; absence keeps the renderer on its
            // own counter-style table.
            markerText = (meta?.get("markerText") as? JsonPrimitive)?.contentOrNull,
            // meta.lang → lang (wave-37 computed content language, the same
            // additive meta channel as attrs/decorations/markerText). A plain
            // string like markerText, so the primitive read IS the decode —
            // deliberately NOT lowercased here: RFC 4647 matching is
            // case-insensitive and consumers normalise at lookup, so
            // canonicalising would destroy the wire's round-trip.
            lang = (meta?.get("lang") as? JsonPrimitive)?.contentOrNull,
            // meta.runs → runs (wave-32 ordered inline content, the same
            // additive meta channel as attrs/decorations/markerText).
            runs = decodeRuns(meta?.get("runs"), id),
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
            max = num("max"),
            // wave-27 lane CBAKE: the ordered-list counter origin. Verbatim
            // string lane (the extractor never coerces it) — Compose does
            // not count from it; only the web runtime does, natively.
            start = p("start")?.contentOrNull,
            // wave-36 lane M1: the replaced element's image source. Verbatim
            // string lane, same as `start` — a path (or data: URI), never a
            // payload, and never coerced.
            src = p("src")?.contentOrNull
        )
    }

    /**
     * Decode the wave-22 `meta.decorations` per-line list (lane DECOR).
     * Strict on STRUCTURE exactly like every other v2 envelope level — the
     * value must be a non-empty array of `{line, color?}` objects, `line`
     * must be a string — because a malformed shape is a writer bug the
     * reader must name, not paper over.
     *
     * Deliberately NOT strict on the `line` VALUE: an unknown keyword is
     * the spec-05 tolerance-rule-1 case (a future §2.1 keyword this reader
     * doesn't paint), so the entry is kept raw here and the PAINT-time
     * filter in DecorationWire drops + logs it. Keeping the raw string is
     * what lets that filter empty the list WITHOUT collapsing it back to
     * "absent" — see [IRComponent.decorations].
     *
     * Colour tokens stay AUTHORED (04-metadata-fields.md): resolving them
     * here would need a Compose `Color`, and the decoder is the wrong
     * layer to own a colour space. DecorationWire does it at paint time.
     */
    private fun decodeDecorations(
        el: kotlinx.serialization.json.JsonElement?,
        ownerId: String
    ): List<IRDecoration>? {
        if (el == null) return null // meta without decorations — the norm
        val arr = el as? JsonArray
            ?: throw IllegalArgumentException("component '$ownerId': meta.decorations must be an array")
        // Schema pins minItems 1 — the wire never carries an empty array
        // (the converter omits the key instead), so an empty one is a
        // writer bug and errors like every other strict envelope.
        require(arr.isNotEmpty()) {
            "component '$ownerId': meta.decorations present but empty (schema: minItems 1)"
        }
        return arr.map { entryEl ->
            val o = entryEl as? JsonObject
                ?: throw IllegalArgumentException("component '$ownerId': meta.decorations entries must be objects")
            requireOnlyKeys(o, DECORATION_KEYS, "meta.decorations entry (in '$ownerId')")
            IRDecoration(
                // `line` is required by the schema; a missing/non-string
                // one leaves the entry unpaintable and unnameable.
                line = (o["line"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
                    ?: throw IllegalArgumentException(
                        "component '$ownerId': meta.decorations entry missing string 'line'"
                    ),
                // `color` is optional; absent ≡ currentColor (§2.2 initial).
                // Non-string colour is a writer bug — colour tokens are
                // authored CSS text, never numbers.
                color = (o["color"] as? JsonPrimitive)?.let { p ->
                    require(p.isString) {
                        "component '$ownerId': meta.decorations 'color' must be an authored CSS token string"
                    }
                    p.contentOrNull
                }
            )
        }
    }

    /**
     * Decode the wave-32 `meta.runs` ordered inline-content list (lane R).
     *
     * Strict where the schema is strict — an array, non-empty, entries are
     * objects carrying EXACTLY ONE of `text` / `child` — because every one
     * of those is a writer bug that would silently reorder painted content,
     * which is the failure this whole wire exists to remove. Note the two
     * asymmetries, both deliberate:
     *   - `text` may be the EMPTY STRING (a producer is allowed to emit one;
     *     the renderer simply paints nothing for it), so the presence test
     *     is on the key, not on the content;
     *   - `child` may NOT be empty: an empty key can never resolve, so it is
     *     a bug rather than a no-op.
     * Resolution of `child` against the composed children — including the
     * dangling case, which spec 03 §4.1 rule 5 makes a warn-and-skip — is
     * the RENDERER's job, not the decoder's.
     */
    private fun decodeRuns(
        el: kotlinx.serialization.json.JsonElement?,
        ownerId: String
    ): List<IRRun>? {
        if (el == null) return null // meta without runs — the norm
        val arr = el as? JsonArray
            ?: throw IllegalArgumentException("component '$ownerId': meta.runs must be an array")
        // Schema pins minItems 1 — the producer omits the key rather than
        // emitting an empty list, so an empty one is a writer bug.
        require(arr.isNotEmpty()) {
            "component '$ownerId': meta.runs present but empty (schema: minItems 1)"
        }
        return arr.map { entryEl ->
            val o = entryEl as? JsonObject
                ?: throw IllegalArgumentException("component '$ownerId': meta.runs entries must be objects")
            requireOnlyKeys(o, RUN_KEYS, "meta.runs entry (in '$ownerId')")
            val textEl = o["text"] as? JsonPrimitive
            val childEl = o["child"] as? JsonPrimitive
            require((textEl != null) != (childEl != null)) {
                "component '$ownerId': meta.runs entry must carry exactly one of 'text' / 'child'"
            }
            if (textEl != null) {
                require(textEl.isString) {
                    "component '$ownerId': meta.runs 'text' must be a string"
                }
                IRRun(text = textEl.contentOrNull ?: "")
            } else {
                val key = childEl!!.takeIf { it.isString }?.contentOrNull
                require(!key.isNullOrEmpty()) {
                    "component '$ownerId': meta.runs 'child' must be a non-empty authoring key"
                }
                IRRun(child = key)
            }
        }
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
