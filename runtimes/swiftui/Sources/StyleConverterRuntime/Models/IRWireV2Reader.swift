//
//  IRWireV2Reader.swift
//  StyleConverterRuntime
//
//  STRICT decode of the IR v2 wire envelope — the Swift mirror of the
//  converter's IRWireV2.kt reader and of schema/ir-v2.schema.json.
//
//  Strictness contract (schema/spec/05-versioning.md):
//    - a reader MUST refuse a document whose minReaderVersion exceeds
//      what it implements (we implement 2);
//    - unknown ENVELOPE keys (document / component / slot / meta /
//      selector / media / property wrapper) MUST error — additional-
//      Properties:false in the schema;
//    - `children` anywhere in a v2 document is a HARD error (flat list
//      + slot refs only — spec 03);
//    - unknown property `type` VALUES are tolerated (the {type,data}
//      envelope decodes; dispatch skips them later) — spec 05 rule 1;
//    - `slot` MUST round-trip (IRSlot is a stored field, never dropped).
//
//  The tolerant dual-read decoder in IRModels.swift stays in charge of
//  v1 documents and standalone component decodes; this file only runs
//  when the document declares `irVersion`.
//

import Foundation

/// Namespace for the strict v2 envelope decode. Pure functions — no
/// state; invoked from IRDocument.init(from:).
enum IRWireV2Reader {

    /// The wire generation this reader implements (writer stamp).
    static let irVersion = 2
    /// Newest `minReaderVersion` this reader accepts.
    static let maxReaderVersion = 2

    // MARK: - Error helper

    /// Uniform DecodingError for contract violations, keyed to a path so
    /// XCTest failures and harness logs point at the offending node.
    private static func violation(_ message: String, path: [CodingKey]) -> DecodingError {
        .dataCorrupted(.init(codingPath: path, debugDescription: message))
    }

    // MARK: - Document envelope

    /// Decode the `{irVersion, minReaderVersion, components, keyframes?,
    /// fontFaces?}` envelope from an already-opened string-keyed container.
    /// Returns the FLAT component list in wire order (the sibling-order
    /// contract) plus the optional document-level keyframes map (spec 07
    /// §1.2) and @font-face list (spec 01 §5) — each nil when the wire
    /// omitted the key, which is the omit-when-empty contract.
    static func decodeEnvelope(from c: KeyedDecodingContainer<IRAnyKey>,
                               codingPath: [CodingKey]) throws
        -> (components: [IRComponent], keyframes: [String: [IRKeyframeStop]]?,
            fontFaces: [IRFontFace]?) {
        // Rule 2 (spec 05): unknown document-level keys are an error.
        // `keyframes` is the sanctioned wave-8 additive minor-revision key
        // (spec 07 §1.2; schema properties.keyframes); `fontFaces` is the
        // wave-34 one (spec 01 §5; schema properties.fontFaces).
        let allowed: Set<String> = ["irVersion", "minReaderVersion",
                                    "components", "keyframes", "fontFaces"]
        for k in c.allKeys where !allowed.contains(k.stringValue) {
            throw violation("unknown envelope key '\(k.stringValue)' in v2 document (spec 05: unknown envelope keys MUST error)", path: codingPath)
        }
        // Version pair — both mandatory in v2 (schema: required + const 2).
        let version = try c.decode(Int.self, forKey: IRAnyKey("irVersion"))
        guard c.contains(IRAnyKey("minReaderVersion")) else {
            throw violation("v2 document missing minReaderVersion", path: codingPath)
        }
        let minReader = try c.decode(Int.self, forKey: IRAnyKey("minReaderVersion"))
        // Refusal rule first: a document demanding a newer reader is
        // unreadable REGARDLESS of what irVersion claims (spec 05).
        guard minReader <= maxReaderVersion else {
            throw violation("document requires reader >= \(minReader); this reader implements \(maxReaderVersion)", path: codingPath)
        }
        // Mirror the converter's reader: only irVersion == 2 is a known
        // byte shape for this codec.
        guard version == irVersion else {
            throw violation("unsupported irVersion \(version) (reader implements \(irVersion))", path: codingPath)
        }
        // Flat component array — each entry decodes independently through
        // the strict component wire type below (no recursion by design).
        var arr = try c.nestedUnkeyedContainer(forKey: IRAnyKey("components"))
        var flat: [IRComponent] = []
        while !arr.isAtEnd {
            flat.append(try arr.decode(ComponentWire.self).component)
        }
        // Optional document-level keyframes block (spec 07 §1.2). Strict
        // shape mirrors schema $defs/keyframeSet + $defs/keyframeStop.
        var keyframes: [String: [IRKeyframeStop]]? = nil
        if c.contains(IRAnyKey("keyframes")) {
            let kf = try c.nestedContainer(keyedBy: IRAnyKey.self,
                                           forKey: IRAnyKey("keyframes"))
            // Schema: minProperties 1 — an empty map may never ride the
            // wire (omit-when-empty is the additive-key contract).
            guard !kf.allKeys.isEmpty else {
                throw violation("keyframes present but empty (schema: minProperties 1)", path: codingPath)
            }
            var sets: [String: [IRKeyframeStop]] = [:]
            for name in kf.allKeys {
                var stopArr = try kf.nestedUnkeyedContainer(forKey: name)
                var stops: [IRKeyframeStop] = []
                while !stopArr.isAtEnd {
                    stops.append(try stopArr.decode(KeyframeStopWire.self).stop)
                }
                // Schema: minItems 1 — the converter drops zero-valid-stop
                // sets whole rather than emitting an unanimatable shell.
                guard !stops.isEmpty else {
                    throw violation("keyframes['\(name.stringValue)'] is empty (schema: minItems 1)", path: codingPath)
                }
                sets[name.stringValue] = stops
            }
            keyframes = sets
        }
        // Optional document-level @font-face list (spec 01 §5). Strict shape
        // mirrors schema $defs/fontFace: a non-empty array of objects
        // carrying only the four known descriptors, `family` and `src`
        // REQUIRED and non-empty. Strict even though the SwiftUI renderer
        // does not yet register faces (no CTFontManagerRegisterFontsForURL
        // hop exists) — an envelope structure this reader claims to speak
        // must be well-formed or fail loudly, or a writer bug would hide
        // until the wave that finally wires registration.
        var fontFaces: [IRFontFace]? = nil
        if c.contains(IRAnyKey("fontFaces")) {
            var arr = try c.nestedUnkeyedContainer(forKey: IRAnyKey("fontFaces"))
            var faces: [IRFontFace] = []
            while !arr.isAtEnd {
                faces.append(try arr.decode(FontFaceWire.self).face)
            }
            // Schema: minItems 1 — omit-when-empty is the additive contract,
            // so an empty array on the wire is a writer bug.
            guard !faces.isEmpty else {
                throw violation("fontFaces present but empty (schema: minItems 1)", path: codingPath)
            }
            fontFaces = faces
        }
        return (flat, keyframes, fontFaces)
    }

    // MARK: - @font-face wire shape (spec 01 §5)

    /// Strict `@font-face` entry decoder (schema $defs/fontFace):
    /// `{family, src, weight?, style?}` only. `weight`/`style` stay STRINGS
    /// rather than typed values because css-fonts-4 §4.4 permits a weight
    /// RANGE ("400 700") and §4.5 an oblique angle — neither has a typed
    /// equivalent, and collapsing either would destroy the face-matching
    /// input the future registration hop needs.
    private struct FontFaceWire: Decodable {
        let face: IRFontFace

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: IRAnyKey.self)
            let allowed: Set<String> = ["family", "src", "weight", "style"]
            for k in c.allKeys where !allowed.contains(k.stringValue) {
                throw violation("unknown fontFaces entry key '\(k.stringValue)' (schema: additionalProperties false)",
                                path: decoder.codingPath)
            }
            let family = try c.decode(String.self, forKey: IRAnyKey("family"))
            guard !family.isEmpty else {
                throw violation("fontFaces entry 'family' must be non-empty", path: decoder.codingPath)
            }
            let src = try c.decode(String.self, forKey: IRAnyKey("src"))
            guard !src.isEmpty else {
                throw violation("fontFaces entry 'src' must be non-empty", path: decoder.codingPath)
            }
            // Absent stays nil, never defaulted to the css-fonts-4 initial
            // literal: "omitted" and "explicitly normal" must remain
            // distinguishable for the consumer that eventually matches faces.
            face = IRFontFace(
                family: family,
                src: src,
                weight: try c.decodeIfPresent(String.self, forKey: IRAnyKey("weight")),
                style: try c.decodeIfPresent(String.self, forKey: IRAnyKey("style"))
            )
        }
    }

    // MARK: - Keyframe stop wire shape (spec 07 §1.2)

    /// Strict keyframe stop decoder (schema $defs/keyframeStop):
    /// `{offset, properties}` only, offset a resolved fraction in [0, 1],
    /// properties standard {type, data} envelopes (same tolerance rule as
    /// component properties — unknown TYPE values decode fine and dispatch
    /// skips them later).
    struct KeyframeStopWire: Decodable {
        /// The decoded stop.
        let stop: IRKeyframeStop

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: IRAnyKey.self)
            // additionalProperties:false on the stop object.
            for k in c.allKeys where k.stringValue != "offset" && k.stringValue != "properties" {
                throw violation("unknown keyframe-stop key '\(k.stringValue)' (allowed: offset/properties)", path: decoder.codingPath)
            }
            // offset: REQUIRED resolved fraction — readers never re-parse
            // from/to/percent strings (spec 07 §1.2).
            let offset = try c.decode(Double.self, forKey: IRAnyKey("offset"))
            guard offset >= 0.0, offset <= 1.0 else {
                throw violation("keyframe offset \(offset) outside [0, 1] (schema: minimum 0 / maximum 1)", path: decoder.codingPath)
            }
            // properties: standard strict property envelopes.
            var propArr = try c.nestedUnkeyedContainer(forKey: IRAnyKey("properties"))
            var props: [IRProperty] = []
            while !propArr.isAtEnd {
                props.append(try propArr.decode(PropertyWire.self).property)
            }
            stop = IRKeyframeStop(offset: offset, properties: props)
        }
    }

    // MARK: - Component wire shape

    /// Strict v2 component decoder (schema $defs/component). Wraps the
    /// assembled IRComponent so JSONDecoder's machinery drives the decode
    /// while unknown-key checks run against `allKeys`.
    struct ComponentWire: Decodable {
        /// The decoded component (children always nil — v2 is flat).
        let component: IRComponent

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: IRAnyKey.self)
            // `children` gets its own diagnostic before the generic
            // unknown-key error — it is THE sanctioned break of v2.
            if c.contains(IRAnyKey("children")) {
                throw violation("`children` does not exist in IR v2 — flat list + slot refs only (spec 03 hard error)", path: decoder.codingPath)
            }
            // additionalProperties:false — anything outside the v2
            // component surface is a contract violation.
            // `variables` is the sanctioned additive minor-revision key
            // (custom-property definitions, spec 01 component table).
            let allowed: Set<String> = ["id", "name", "properties",
                                        "selectors", "media", "slot",
                                        "text", "pseudos", "meta",
                                        "variables"]
            for k in c.allKeys where !allowed.contains(k.stringValue) {
                throw violation("unknown component key '\(k.stringValue)' in v2 document", path: decoder.codingPath)
            }
            // id/name: required, non-empty (schema minLength 1).
            let id = try c.decode(String.self, forKey: IRAnyKey("id"))
            let name = try c.decode(String.self, forKey: IRAnyKey("name"))
            guard !id.isEmpty, !name.isEmpty else {
                throw violation("v2 component id/name must be non-empty", path: decoder.codingPath)
            }
            // properties: required array of strict {type,data} envelopes.
            var propArr = try c.nestedUnkeyedContainer(forKey: IRAnyKey("properties"))
            var properties: [IRProperty] = []
            while !propArr.isAtEnd {
                properties.append(try propArr.decode(PropertyWire.self).property)
            }
            // selectors/media: optional (omit-when-empty on the wire);
            // strict inner shape via their wire wrappers.
            let selectors = try c.decodeIfPresent([SelectorWire].self, forKey: IRAnyKey("selectors"))?.map(\.selector)
            let media = try c.decodeIfPresent([MediaWire].self, forKey: IRAnyKey("media"))?.map(\.media)
            // slot: strict {parent, name?}; absent name reconstructs the
            // documented default "content" (schema slot.name default).
            let slot = try c.decodeIfPresent(SlotWire.self, forKey: IRAnyKey("slot"))?.slot
            // text: plain string; empty string is a legal emitted value.
            let text = try c.decodeIfPresent(String.self, forKey: IRAnyKey("text"))
            // pseudos: strict key set {before, after, marker}, payloads
            // opaque objects (extractor-owned shape — schema: permissive
            // values, strict keys). Stored as an IRValue object.
            var pseudos: IRValue? = nil
            if c.contains(IRAnyKey("pseudos")) {
                let p = try c.nestedContainer(keyedBy: IRAnyKey.self, forKey: IRAnyKey("pseudos"))
                let slots: Set<String> = ["before", "after", "marker"]
                var dict: [String: IRValue] = [:]
                for k in p.allKeys {
                    guard slots.contains(k.stringValue) else {
                        throw violation("unknown pseudos key '\(k.stringValue)' (allowed: before/after/marker)", path: decoder.codingPath)
                    }
                    // Each slot payload must be an object per the schema.
                    let v = try p.decode(IRValue.self, forKey: k)
                    guard case .object = v else {
                        throw violation("pseudos.\(k.stringValue) must be an object", path: decoder.codingPath)
                    }
                    dict[k.stringValue] = v
                }
                pseudos = .object(dict)
            }
            // meta: strict {sourceTag?, role?, attrs?, decorations?},
            // minProperties 1 — an empty meta object may never appear on
            // the wire. `attrs` is the wave-20 widget capsule (lane W2);
            // `decorations` is the wave-22 per-line list (lane DECOR).
            var meta: IRMeta? = nil
            if c.contains(IRAnyKey("meta")) {
                let m = try c.nestedContainer(keyedBy: IRAnyKey.self, forKey: IRAnyKey("meta"))
                // `markerText` is the wave-27 baked list marker (lane CBAKE).
                // `runs` is the wave-32 ordered inline-content list (lane R).
                let members: Set<String> = ["sourceTag", "role", "attrs", "decorations",
                                            "markerText", "runs"]
                for k in m.allKeys where !members.contains(k.stringValue) {
                    throw violation("unknown meta key '\(k.stringValue)' (allowed: sourceTag/role/attrs/decorations/markerText/runs)", path: decoder.codingPath)
                }
                let tag = try m.decodeIfPresent(String.self, forKey: IRAnyKey("sourceTag"))
                let role = try m.decodeIfPresent(String.self, forKey: IRAnyKey("role"))
                // attrs: strict on the key set (the contract pins exactly
                // ten legal attributes), tolerant on value shape within it
                // — the typed coercion lives ONCE in IRAttrs.from.
                var attrs: IRAttrs? = nil
                if m.contains(IRAnyKey("attrs")) {
                    let raw = try m.decode(IRValue.self, forKey: IRAnyKey("attrs"))
                    guard case .object(let o) = raw else {
                        throw violation("meta.attrs must be an object", path: decoder.codingPath)
                    }
                    // Wave-27 added `start` for the disjoint ol/li ordinal
                    // lane (HTML §4.4.5). Wave-36 (lane M1) added `src` for
                    // the disjoint img/embed/object/video replaced-source
                    // lane — twelve legal attributes now. Admitting it here
                    // is what keeps this strict reader from REJECTING a
                    // document the web consumer needs; see IRAttrs.src for
                    // why this runtime decodes but does not yet paint it.
                    let attrKeys: Set<String> = ["type", "value", "checked", "multiple",
                                                 "size", "alt", "min", "max", "selected",
                                                 "disabled", "start", "src"]
                    for k in o.keys where !attrKeys.contains(k) {
                        throw violation("unknown meta.attrs key '\(k)' (wave-20 wire contract)", path: decoder.codingPath)
                    }
                    attrs = IRAttrs.from(object: o)
                }
                // decorations (wave-22 lane DECOR): strict on STRUCTURE —
                // non-empty array of `{line, color?}` objects with a
                // string `line` — because a malformed shape is a writer
                // bug the reader must name. NOT strict on the `line`
                // VALUE: an unknown keyword is the spec-05 tolerance-rule-1
                // case (a future §2.1 keyword), so it survives decode and
                // DecorationWire drops + logs it at paint time. Keeping it
                // raw is what lets that filter empty the list WITHOUT
                // collapsing it back to "absent" — see IRDecoration.
                var decorations: [IRDecoration]? = nil
                if m.contains(IRAnyKey("decorations")) {
                    let raw = try m.decode(IRValue.self, forKey: IRAnyKey("decorations"))
                    guard case .array(let entries) = raw else {
                        throw violation("meta.decorations must be an array", path: decoder.codingPath)
                    }
                    // Schema pins minItems 1 — the converter omits the key
                    // instead of emitting `[]`, so empty is a writer bug.
                    guard !entries.isEmpty else {
                        throw violation("meta.decorations present but empty (schema: minItems 1)", path: decoder.codingPath)
                    }
                    decorations = try entries.map { entry in
                        guard case .object(let e) = entry else {
                            throw violation("meta.decorations entries must be objects", path: decoder.codingPath)
                        }
                        // Entry envelope is closed (additionalProperties:
                        // false) — style/thickness are deliberately NOT
                        // per-entry (they are the run's, root-wins).
                        let entryKeys: Set<String> = ["line", "color"]
                        for k in e.keys where !entryKeys.contains(k) {
                            throw violation("unknown meta.decorations entry key '\(k)' (allowed: line/color)", path: decoder.codingPath)
                        }
                        guard let line = e["line"]?.stringValue else {
                            throw violation("meta.decorations entry missing string 'line'", path: decoder.codingPath)
                        }
                        // `color` optional; absent ≡ currentColor. A
                        // non-string colour is a writer bug — colour
                        // tokens are authored CSS text, never numbers.
                        var color: String? = nil
                        if let raw = e["color"] {
                            guard let s = raw.stringValue else {
                                throw violation("meta.decorations 'color' must be an authored CSS token string", path: decoder.codingPath)
                            }
                            color = s
                        }
                        return IRDecoration(line: line, color: color)
                    }
                }
                // markerText (wave-27 lane CBAKE): a plain string, so there
                // is no shape to validate beyond "string" — the schema pins
                // minLength 1 and the converter omits the key otherwise.
                let markerText = try m.decodeIfPresent(String.self, forKey: IRAnyKey("markerText"))
                // runs (wave-32 lane R): strict where the schema is strict —
                // a non-empty array whose entries are objects carrying
                // EXACTLY ONE of `text` / `child`. Every one of those is a
                // writer bug that would silently reorder painted content,
                // which is the failure this wire exists to remove. Two
                // deliberate asymmetries: `text` MAY be the empty string (a
                // producer is allowed to emit one; the renderer paints
                // nothing for it), while `child` may NOT be — an empty key
                // can never resolve. Resolving the key against the composed
                // children, including the dangling warn-and-skip of spec 03
                // §4.1 rule 5, is the RENDERER's job, not decode's.
                var runs: [IRRun]? = nil
                if m.contains(IRAnyKey("runs")) {
                    let raw = try m.decode(IRValue.self, forKey: IRAnyKey("runs"))
                    guard case .array(let entries) = raw else {
                        throw violation("meta.runs must be an array", path: decoder.codingPath)
                    }
                    guard !entries.isEmpty else {
                        throw violation("meta.runs present but empty (schema: minItems 1)", path: decoder.codingPath)
                    }
                    runs = try entries.map { entry in
                        guard case .object(let e) = entry else {
                            throw violation("meta.runs entries must be objects", path: decoder.codingPath)
                        }
                        let entryKeys: Set<String> = ["text", "child"]
                        for k in e.keys where !entryKeys.contains(k) {
                            throw violation("unknown meta.runs entry key '\(k)' (allowed: text/child)", path: decoder.codingPath)
                        }
                        guard e.count == 1 else {
                            throw violation("meta.runs entry must carry exactly one of 'text' / 'child'", path: decoder.codingPath)
                        }
                        if let rawText = e["text"] {
                            guard let t = rawText.stringValue else {
                                throw violation("meta.runs 'text' must be a string", path: decoder.codingPath)
                            }
                            return IRRun(text: t)
                        }
                        guard let key = e["child"]?.stringValue, !key.isEmpty else {
                            throw violation("meta.runs 'child' must be a non-empty authoring key", path: decoder.codingPath)
                        }
                        return IRRun(child: key)
                    }
                }
                guard tag != nil || role != nil || attrs != nil || decorations != nil
                        || markerText != nil || runs != nil else {
                    throw violation("meta present but empty (schema: minProperties 1)", path: decoder.codingPath)
                }
                meta = IRMeta(sourceTag: tag, role: role, attrs: attrs,
                              decorations: decorations, markerText: markerText,
                              runs: runs)
            }
            // variables: additive v2 key — "--name" → raw string map
            // (custom-property definitions, css-variables-1 §2). Schema
            // rules: keys match ^--., values are strings, present ⇒
            // non-empty. Round-tripped verbatim (case + bytes); var()
            // resolution is the style engine's job, never decode's.
            var variables: [String: String]? = nil
            if c.contains(IRAnyKey("variables")) {
                let decoded = try c.decode([String: String].self, forKey: IRAnyKey("variables"))
                guard !decoded.isEmpty else {
                    throw violation("variables present but empty (schema: minProperties 1)", path: decoder.codingPath)
                }
                for name in decoded.keys where !(name.hasPrefix("--") && name.count > 2) {
                    throw violation("variables key '\(name)' is not a custom-property name (^--. pattern)", path: decoder.codingPath)
                }
                variables = decoded
            }
            // Assemble — children nil BY CONSTRUCTION (composer fills the
            // in-memory tree afterwards from the slot refs).
            component = IRComponent(id: id, name: name, properties: properties,
                                    selectors: selectors, media: media,
                                    children: nil, slot: slot,
                                    text: text, pseudos: pseudos, meta: meta,
                                    variables: variables)
        }
    }

    // MARK: - Inner wire shapes

    /// Strict {type, data} property envelope (schema $defs/property).
    /// The TYPE value itself is deliberately unvalidated — unknown types
    /// are tolerated and skipped by dispatch (spec 05 rule 1).
    struct PropertyWire: Decodable {
        let property: IRProperty
        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: IRAnyKey.self)
            // additionalProperties:false on the wrapper.
            for k in c.allKeys where k.stringValue != "type" && k.stringValue != "data" {
                throw violation("unknown property-envelope key '\(k.stringValue)'", path: decoder.codingPath)
            }
            let type = try c.decode(String.self, forKey: IRAnyKey("type"))
            guard !type.isEmpty else {
                throw violation("property type must be non-empty", path: decoder.codingPath)
            }
            // data is REQUIRED (may be any JSON value including null —
            // IRValue's decodeNil handles the null case).
            guard c.contains(IRAnyKey("data")) else {
                throw violation("property '\(type)' missing data", path: decoder.codingPath)
            }
            property = IRProperty(type: type, data: try c.decode(IRValue.self, forKey: IRAnyKey("data")))
        }
    }

    /// Strict slot object (schema $defs/slot): parent required non-empty,
    /// name optional with documented default "content".
    struct SlotWire: Decodable {
        let slot: IRSlot
        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: IRAnyKey.self)
            for k in c.allKeys where k.stringValue != "parent" && k.stringValue != "name" {
                throw violation("unknown slot key '\(k.stringValue)'", path: decoder.codingPath)
            }
            let parent = try c.decode(String.self, forKey: IRAnyKey("parent"))
            guard !parent.isEmpty else {
                throw violation("slot.parent must be non-empty", path: decoder.codingPath)
            }
            // Default-name reconstruction: omitted name == "content".
            slot = IRSlot(parent: parent,
                          name: try c.decodeIfPresent(String.self, forKey: IRAnyKey("name")) ?? "content")
        }
    }

    /// Strict selector bucket (schema $defs/selector) — condition is the
    /// pseudo-class WITHOUT the leading colon, unchanged from v1.
    struct SelectorWire: Decodable {
        let selector: IRSelector
        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: IRAnyKey.self)
            for k in c.allKeys where k.stringValue != "condition" && k.stringValue != "properties" {
                throw violation("unknown selector key '\(k.stringValue)'", path: decoder.codingPath)
            }
            var arr = try c.nestedUnkeyedContainer(forKey: IRAnyKey("properties"))
            var props: [IRProperty] = []
            while !arr.isAtEnd { props.append(try arr.decode(PropertyWire.self).property) }
            selector = IRSelector(condition: try c.decode(String.self, forKey: IRAnyKey("condition")),
                                  properties: props)
        }
    }

    /// Strict media bucket (schema $defs/media) — raw query string.
    struct MediaWire: Decodable {
        let media: IRMedia
        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: IRAnyKey.self)
            for k in c.allKeys where k.stringValue != "query" && k.stringValue != "properties" {
                throw violation("unknown media key '\(k.stringValue)'", path: decoder.codingPath)
            }
            var arr = try c.nestedUnkeyedContainer(forKey: IRAnyKey("properties"))
            var props: [IRProperty] = []
            while !arr.isAtEnd { props.append(try arr.decode(PropertyWire.self).property) }
            media = IRMedia(query: try c.decode(String.self, forKey: IRAnyKey("query")),
                            properties: props)
        }
    }
}
