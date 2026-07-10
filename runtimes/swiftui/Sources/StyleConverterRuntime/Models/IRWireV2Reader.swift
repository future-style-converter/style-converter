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

    /// Decode the `{irVersion, minReaderVersion, components}` envelope
    /// from an already-opened string-keyed container. Returns the FLAT
    /// component list in wire order (the sibling-order contract).
    static func decodeEnvelope(from c: KeyedDecodingContainer<IRAnyKey>,
                               codingPath: [CodingKey]) throws -> [IRComponent] {
        // Rule 2 (spec 05): unknown document-level keys are an error.
        let allowed: Set<String> = ["irVersion", "minReaderVersion", "components"]
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
        return flat
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
            // meta: strict {sourceTag?, role?}, minProperties 1 — an
            // empty meta object may never appear on the wire.
            var meta: IRMeta? = nil
            if c.contains(IRAnyKey("meta")) {
                let m = try c.nestedContainer(keyedBy: IRAnyKey.self, forKey: IRAnyKey("meta"))
                let members: Set<String> = ["sourceTag", "role"]
                for k in m.allKeys where !members.contains(k.stringValue) {
                    throw violation("unknown meta key '\(k.stringValue)' (allowed: sourceTag/role)", path: decoder.codingPath)
                }
                let tag = try m.decodeIfPresent(String.self, forKey: IRAnyKey("sourceTag"))
                let role = try m.decodeIfPresent(String.self, forKey: IRAnyKey("role"))
                guard tag != nil || role != nil else {
                    throw violation("meta present but empty (schema: minProperties 1)", path: decoder.codingPath)
                }
                meta = IRMeta(sourceTag: tag, role: role)
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
