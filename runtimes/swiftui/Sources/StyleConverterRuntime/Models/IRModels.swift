//
//  IRModels.swift
//  StyleConverterRuntime
//
//  Swift equivalent of the converter's IRDocument.kt (+ IRWireV2.kt) and
//  the Android/web IR models.
//
//  A single IRDocument is loaded from tmpOutput.json at launch. Two wire
//  generations are readable (schema/spec/05-versioning.md):
//
//    v2 (default converter output): { irVersion:2, minReaderVersion:2,
//       components:[ …FLAT… ] } — every component is a standalone entry;
//       composition rides `slot:{parent,name?}` on the CHILD. The decode
//       path is STRICT at the envelope (unknown keys error, `children`
//       is a hard error) and delegates to IRWireV2Reader.swift; after
//       decode the flat list is composed back into the in-memory tree by
//       IRComposer so the renderer/harness keep one shape.
//
//    v1 (deprecation window only): { components:[ …nested… ] } — the
//       legacy tolerant decode, with the underscore metadata spellings
//       (`_text`/`_tag`/`_role`/`_pseudo`) translated into their v2
//       homes (text / meta.sourceTag / meta.role / pseudos) and a
//       deprecation warning on stderr.
//

import Foundation

// MARK: - Document

/// Root document. `components` is ALWAYS the composed tree (roots with
/// `children` populated) so every existing consumer — renderer, gallery,
/// capture pipeline — sees one in-memory shape regardless of wire version.
// public: decoded directly by the harness app (ContentView.loadDocument)
// and by any package consumer feeding IR into the renderer.
public struct IRDocument: Decodable {
    /// Wire generation this document arrived as (1 or 2).
    public let irVersion: Int
    // public: the harness walks/flattens the composed component tree for
    // capture; for v2 documents these are the slot-composed roots.
    public let components: [IRComponent]
    /// v2 only: the raw FLAT wire list in array order (the sibling-order
    /// contract of schema/spec/03-children.md). nil for v1 documents.
    public let flatComponents: [IRComponent]?
    /// Wave 8 — document-level named keyframe sets (spec 07 §1.2): the
    /// wire twin of CSS `@keyframes` at-rules, which are document-scoped
    /// in CSS too. nil when the wire omitted the key (omit-when-empty)
    /// and always nil for v1 documents (the v1 wire structurally cannot
    /// carry the field — IRDocument.kt marks it @Transient).
    public let keyframes: [String: [IRKeyframeStop]]?

    // public: hand-written Decodable witness on a public type.
    public init(from decoder: Decoder) throws {
        // String-keyed container so we can (a) sniff the version pair and
        // (b) run the v2 unknown-envelope-key check (spec 05 rule 2).
        let c = try decoder.container(keyedBy: IRAnyKey.self)
        if c.contains(IRAnyKey("irVersion")) {
            // v2+ path — strict envelope decode (IRWireV2Reader.swift).
            let envelope = try IRWireV2Reader.decodeEnvelope(from: c, codingPath: decoder.codingPath)
            irVersion = IRWireV2Reader.irVersion
            flatComponents = envelope.components
            keyframes = envelope.keyframes
            // Composition is a COMPOSER concern (spec 03): rebuild the
            // preview tree from slot refs; dangling parents become roots.
            components = IRComposer.compose(envelope.components)
        } else {
            // v1 window — the legacy tolerant decode, unchanged behavior
            // (unknown keys ignored), plus a loud deprecation warning so
            // stale pipelines surface before the window closes.
            FileHandle.standardError.write(Data(
                "[StyleConverterRuntime] WARNING: decoding a v1 IR document (no irVersion). v1 is deprecated — re-run the converter to emit IR v2.\n".utf8))
            irVersion = 1
            flatComponents = nil
            // The v1 codec structurally cannot carry keyframes (spec 07):
            // nil, never [] — same absent-vs-empty discipline as children.
            keyframes = nil
            components = try c.decode([IRComponent].self, forKey: IRAnyKey("components"))
        }
    }
}

// MARK: - Keyframe stop (wave 8 — spec 07 §1.2)

/// One resolved keyframe stop of a document-level `keyframes` set.
/// `offset` is the resolved fraction in [0, 1] (`from` → 0, `to` → 1,
/// `N%` → N/100 — resolved by the converter, never re-parsed here) and
/// `properties` carries the stop's declarations as the SAME typed
/// {type, data} envelopes component properties use. Sets arrive sorted
/// ascending by offset, stable for equal offsets (spec 07 §1.2 — readers
/// MAY rely on sortedness and MUST NOT reorder).
// public: consumed by the animations engine (AnimationResolver) and
// published by hosts through the styleKeyframes environment channel.
public struct IRKeyframeStop {
    /// Resolved fractional position in [0, 1].
    public let offset: Double
    /// The stop's typed declarations ({type, data} property envelopes).
    public let properties: [IRProperty]

    // public: constructed by the wire decoder, the harness and tests.
    public init(offset: Double, properties: [IRProperty]) {
        self.offset = offset
        self.properties = properties
    }
}

// MARK: - Slot / meta structures (v2)

/// Child→parent composition reference (schema/ir-v2.schema.json $defs/slot).
/// STRUCTURAL data that MUST round-trip (spec 05 rule 4) — consumed only
/// by composers (IRComposer / an SDUI shell); style engines ignore it.
// public: exposed through IRComponent.slot for composer consumers.
public struct IRSlot: Equatable {
    /// id of the container component this component composes into.
    public let parent: String
    /// Slot name within the parent. The wire omits it when it equals the
    /// documented default "content"; the reader reconstructs the default.
    public let name: String

    // internal: constructed by the two decode paths and by tests.
    init(parent: String, name: String = "content") {
        self.parent = parent
        self.name = name
    }
}

/// Wave-20 wire contract (lane W2) — the form/widget attribute capsule
/// the extractor emits as `meta.attrs` for the widget tags (a, button,
/// input, textarea, select, option, meter, progress). ONLY present-in-
/// source attributes among the pinned ten appear; types are pinned by
/// the contract: strings except checked/multiple/selected/disabled
/// (booleans) and min/max/value-on-meter-progress (numbers where numeric
/// — hence the separate `valueNumber` channel so a numeric wire `value`
/// survives without stringly re-parsing). Droppable meta hint like
/// `sourceTag`: ignoring it loses fidelity, never correctness.
// public: read by the widget resolve (StyleEngine/widgets) and tests.
public struct IRAttrs: Equatable {
    public let type: String?        // input type ("checkbox", "range", …)
    public let value: String?       // string-form value attribute
    public let valueNumber: Double? // numeric wire value (meter/progress)
    public let checked: Bool?       // checkbox/radio checked presence
    public let multiple: Bool?      // select multiple presence (listbox)
    public let selected: Bool?      // option selected presence
    public let disabled: Bool?      // disabled presence (not yet painted)
    public let size: String?        // select/input size attribute
    public let alt: String?         // image-input alt text
    public let min: Double?         // meter/progress/range min
    public let max: Double?         // meter/progress/range max

    // internal: constructed by the decode paths and by tests. Defaults
    // keep pre-attrs construction sites and tests terse.
    init(type: String? = nil, value: String? = nil, valueNumber: Double? = nil,
         checked: Bool? = nil, multiple: Bool? = nil, selected: Bool? = nil,
         disabled: Bool? = nil, size: String? = nil, alt: String? = nil,
         min: Double? = nil, max: Double? = nil) {
        self.type = type
        self.value = value
        self.valueNumber = valueNumber
        self.checked = checked
        self.multiple = multiple
        self.selected = selected
        self.disabled = disabled
        self.size = size
        self.alt = alt
        self.min = min
        self.max = max
    }

    /// Build from the wire's raw IRValue object — shared by the strict v2
    /// reader and the lenient path so the coercion rules live ONCE.
    /// `value` fills BOTH channels when numeric (contract: numbers on
    /// meter/progress, strings elsewhere).
    static func from(object o: [String: IRValue]) -> IRAttrs {
        IRAttrs(
            type: o["type"]?.stringValue,
            // String channel: verbatim string, or the numeric literal
            // re-rendered for numeric wire values (both channels agree).
            value: o["value"]?.stringValue ?? o["value"]?.doubleValue.map { v in
                // Trim ".0" so a wire integer prints like its literal.
                v == v.rounded() ? String(Int(v)) : String(v)
            },
            valueNumber: o["value"]?.doubleValue,
            checked: o["checked"]?.boolValue,
            multiple: o["multiple"]?.boolValue,
            selected: o["selected"]?.boolValue,
            disabled: o["disabled"]?.boolValue,
            size: o["size"]?.stringValue,
            alt: o["alt"]?.stringValue,
            min: o["min"]?.doubleValue,
            max: o["max"]?.doubleValue
        )
    }
}

/// Wave-22 wire contract (lane DECOR) — ONE entry of the
/// `meta.decorations` list a COLLAPSED inline run carries
/// (schema/spec/04-metadata-fields.md; producer: the `_decorations`
/// banner in tools/titan/extract-fixture.mjs).
///
/// The list is ORDERED OUTERMOST-FIRST — the css-text-decor-3 §2.1
/// propagation order, so a descendant's line paints over its ancestors'
/// where both land on the same row — and AUTHORITATIVE when present: it
/// is the complete line set for the run, so the painter ignores the
/// component's own `text-decoration-line` flags there.
///
/// Both members stay RAW WIRE STRINGS on purpose. `line` is one of the
/// three §2.1 keywords (unknown ones survive decode and are dropped +
/// logged at paint time, spec-05 tolerance rule 1); `color` is the CSS
/// colour token AS AUTHORED ("blue", "#00f", "rgb(0,0,255)") — the
/// converter does not normalize it to the IR sRGB leaf (see
/// 04-metadata-fields.md for why), so resolution happens in
/// `DecorationWire.decorationLines(from:)` via `CSSTokenParser`. A nil
/// `color` is `currentColor` (§2.2 initial), never a silent drop.
// public: read by the renderer's decoration overlay and by tests.
public struct IRDecoration: Equatable {
    public let line: String
    public let color: String?

    // internal: constructed by the decode paths and by tests.
    init(line: String, color: String? = nil) {
        self.line = line
        self.color = color
    }
}

/// Droppable renderer hints, grouped (v2 home of v1 `_tag` and `_role`).
/// A consumer may ignore meta without correctness loss — unlike slot.
// public: the renderer reads sourceTag for list-marker generation.
public struct IRMeta: Equatable {
    /// Originating HTML tag, lowercase ('ol', 'li', …) — v2 `sourceTag`,
    /// v1 `_tag`. Drives list-marker generation in ComponentRenderer.
    public let sourceTag: String?
    /// Role marker (today only "body-root") — v2 `role`, v1 `_role`
    /// (which the old Swift model silently dropped; now translated).
    public let role: String?
    /// Wave-20 widget-identity capsule (`meta.attrs`, the `_tag`
    /// precedent) — nil for v1 documents and non-widget components.
    public let attrs: IRAttrs?
    /// Wave-22 per-line decoration list (`meta.decorations`, same additive
    /// meta channel) — nil for v1 documents and for every run the
    /// extractor did not collapse. An EMPTY array never reaches here (the
    /// schema pins minItems 1); emptiness only arises AFTER the paint-time
    /// keyword filter, and it stays authoritative there.
    public let decorations: [IRDecoration]?

    // internal: constructed by the decode paths and by tests. The attrs /
    // decorations defaults keep every earlier construction site compiling.
    init(sourceTag: String? = nil, role: String? = nil, attrs: IRAttrs? = nil,
         decorations: [IRDecoration]? = nil) {
        self.sourceTag = sourceTag
        self.role = role
        self.attrs = attrs
        self.decorations = decorations
    }
}

// MARK: - Component

/// A single UI component with styles, selectors, media queries, slot ref
/// and composed children.
///
/// Wire vs memory: on the v2 wire `children` does not exist (flat list);
/// in memory `children` carries the slot-COMPOSED subtree so the
/// recursive renderer and the capture pipeline keep their one shape.
/// `text` (v1 `_text`) is leaf content; `pseudos` (v1 `_pseudo`) is the
/// opaque generated-content payload; `meta` groups the droppable hints.
// public: the unit the harness renders + screenshots (CaptureCanvas,
// ScreenshotCaptureView, ComponentGallery all take IRComponent).
public struct IRComponent: Decodable {
    // public: all stored fields are read by the harness gallery/capture UI
    // (id/name labels, properties list, children flattening); text/meta
    // are part of the decode contract pinned by IRModelsTests.
    public let id: String
    public let name: String
    public let properties: [IRProperty]
    public let selectors: [IRSelector]?
    public let media: [IRMedia]?
    /// Composed children — decoded from the wire for v1, rebuilt from
    /// slot refs by IRComposer for v2. nil (never []) when empty, so the
    /// renderer's `children == nil` placeholder branch is version-stable.
    public let children: [IRComponent]?
    /// v2 `slot` — kept on the composed component too so it round-trips
    /// (spec 05 rule 4) and composers can re-derive the flat form.
    public let slot: IRSlot?
    /// Element text content (v2 `text`, v1 `_text`). Empty string is a
    /// legal value ("extracted, was empty"), distinct from nil.
    public let text: String?
    /// Generated-content payload (v2 `pseudos`, v1 `_pseudo`) — opaque
    /// extractor-owned {before?, after?, marker?} object; pseudo nodes
    /// never flatten (spec 03 edge cases). Not yet rendered on iOS.
    public let pseudos: IRValue?
    /// Grouped droppable hints (v2 `meta`, v1 `_tag`/`_role`).
    public let meta: IRMeta?
    /// CSS custom-property definitions declared on this component
    /// (`--name` → RAW declaration value, verbatim). Additive IR v2 key
    /// (schema/spec/01-envelope.md): names are case-SENSITIVE and values
    /// stay untyped token streams until var() substitution
    /// (css-variables-1 §2). nil when the wire omitted the key. var()
    /// resolution (element → slot-parent chain → fallback →
    /// guaranteed-invalid, schema/spec/02-values.md) is the style
    /// engine's job — decode only round-trips the map.
    public let variables: [String: String]?

    // Dual-spelling key set: v2 names + the v1 underscore forms this
    // reader still translates during the deprecation window.
    enum CodingKeys: String, CodingKey {
        case id, name, properties, selectors, media, children
        case slot, text, pseudos, meta, variables
        case _text, _tag, _pseudo, _role
    }

    // internal: memberwise construction for the strict v2 wire decoder
    // (IRWireV2Reader) and the composer (IRComposer), which rebuilds
    // components with their children attached.
    // `variables` defaults to nil so pre-variables call sites
    // (IRComposer, tests) construct unchanged.
    init(id: String, name: String, properties: [IRProperty],
         selectors: [IRSelector]?, media: [IRMedia]?,
         children: [IRComponent]?, slot: IRSlot?,
         text: String?, pseudos: IRValue?, meta: IRMeta?,
         variables: [String: String]? = nil) {
        self.id = id
        self.name = name
        self.properties = properties
        self.selectors = selectors
        self.media = media
        self.children = children
        self.slot = slot
        self.text = text
        self.pseudos = pseudos
        self.meta = meta
        self.variables = variables
    }

    /// Copy with a different composed-children array (IRComposer builds
    /// the tree bottom-up out of immutable flat entries).
    func withChildren(_ kids: [IRComponent]?) -> IRComponent {
        IRComponent(id: id, name: name, properties: properties,
                    selectors: selectors, media: media,
                    children: kids, slot: slot,
                    text: text, pseudos: pseudos, meta: meta,
                    variables: variables)
    }

    // public: hand-written Decodable witness on a public type must be
    // public (Swift access-control rule for protocol witnesses).
    //
    // This is the TOLERANT dual-read decoder: it accepts both the v1 and
    // v2 spellings so (a) v1 documents keep decoding through the window
    // and (b) standalone component decodes in tests stay cheap. Document-
    // level v2 STRICTNESS (unknown keys, `children` hard error) is
    // enforced by IRWireV2Reader before this shape ever matters.
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decodeIfPresent(String.self, forKey: .id) ?? ""
        name = try c.decodeIfPresent(String.self, forKey: .name) ?? "Unknown"
        properties = try c.decodeIfPresent([IRProperty].self, forKey: .properties) ?? []
        selectors = try c.decodeIfPresent([IRSelector].self, forKey: .selectors)
        media = try c.decodeIfPresent([IRMedia].self, forKey: .media)
        children = try c.decodeIfPresent([IRComponent].self, forKey: .children)
        // slot: v2-only structural field ({parent, name?} — name absent
        // reconstructs the documented default "content").
        if let rawSlot = try c.decodeIfPresent(RawSlot.self, forKey: .slot) {
            slot = IRSlot(parent: rawSlot.parent, name: rawSlot.name ?? "content")
        } else {
            slot = nil
        }
        // text: v2 spelling wins; v1 `_text` translated when only it exists.
        text = try c.decodeIfPresent(String.self, forKey: .text)
            ?? c.decodeIfPresent(String.self, forKey: ._text)
        // pseudos: v2 spelling wins; v1 `_pseudo` translated (the old
        // model dropped it — the v2 reader closes that lossy hop).
        pseudos = try c.decodeIfPresent(IRValue.self, forKey: .pseudos)
            ?? c.decodeIfPresent(IRValue.self, forKey: ._pseudo)
        // meta: the v2 group wins; otherwise assemble from the v1
        // underscore hints (`_tag`/`_role`) when either is present.
        if let rawMeta = try c.decodeIfPresent(RawMeta.self, forKey: .meta) {
            meta = IRMeta(sourceTag: rawMeta.sourceTag, role: rawMeta.role,
                          // Wave-20: meta.attrs rides the lenient path too
                          // (standalone component decodes in tests) — the
                          // shared IRAttrs.from coercion, object-shaped only.
                          attrs: rawMeta.attrs?.objectValue.map(IRAttrs.from),
                          // Wave-22: same for meta.decorations. Lenient
                          // means lenient — a malformed entry is SKIPPED
                          // here (the strict v2 reader is the one that
                          // errors), and `line` is the only required part.
                          decorations: rawMeta.decorations?.arrayValue?.compactMap { entry in
                              guard let line = entry["line"]?.stringValue else { return nil }
                              return IRDecoration(line: line, color: entry["color"]?.stringValue)
                          })
        } else {
            let tag = try c.decodeIfPresent(String.self, forKey: ._tag)
            let role = try c.decodeIfPresent(String.self, forKey: ._role)
            meta = (tag != nil || role != nil) ? IRMeta(sourceTag: tag, role: role) : nil
        }
        // variables: additive v2 key — "--name" → raw string map,
        // round-tripped verbatim (keys keep their case, values their
        // bytes). Absent on every v1 document.
        variables = try c.decodeIfPresent([String: String].self, forKey: .variables)
    }

    /// Synthesized-decode helper for the wire slot object (lenient path).
    private struct RawSlot: Decodable {
        let parent: String
        let name: String?
    }

    /// Synthesized-decode helper for the wire meta object (lenient path).
    /// `attrs` stays raw IRValue here — IRAttrs.from applies the wave-20
    /// coercion rules in one shared place.
    private struct RawMeta: Decodable {
        let sourceTag: String?
        let role: String?
        let attrs: IRValue?
        // Wave-22 lane DECOR: the per-line decoration list rides the
        // lenient path too (standalone component decodes in tests). Kept
        // as an opaque IRValue here — the STRICT reader owns the shape
        // errors; this path only coerces what it recognises.
        let decorations: IRValue?
    }
}

// MARK: - Property / selector / media

/// A CSS property as (type, data) pair — byte-identical envelope in v1
/// and v2 (schema/ir-v2.schema.json $defs/property).
// public: the harness capture path inspects raw (type, data) pairs to
// decide paint-context suppression (ScreenshotCaptureView.parentCreatesContext).
public struct IRProperty: Decodable {
    // public: dispatch key ("Width", "BackgroundColor", ...).
    public let type: String
    // public: free-form IR payload the harness pattern-matches on.
    public let data: IRValue

    // internal: memberwise construction for the strict v2 wire decoder.
    init(type: String, data: IRValue) {
        self.type = type
        self.data = data
    }
}

/// Pseudo-class selector styles (e.g. :hover) — unchanged in v2.
// public: exposed through IRComponent.selectors (public stored property).
public struct IRSelector: Decodable {
    public let condition: String
    public let properties: [IRProperty]

    // internal: memberwise construction for the strict v2 wire decoder.
    init(condition: String, properties: [IRProperty]) {
        self.condition = condition
        self.properties = properties
    }
}

/// Media query scoped styles — unchanged in v2.
// public: exposed through IRComponent.media (public stored property).
public struct IRMedia: Decodable {
    public let query: String
    public let properties: [IRProperty]

    // internal: memberwise construction for the strict v2 wire decoder.
    init(query: String, properties: [IRProperty]) {
        self.query = query
        self.properties = properties
    }
}

// MARK: - IRValue (dynamic JSON)

/// A generic JSON value. The Android side uses `JsonElement`; we mirror that with
/// a recursive enum so we can peek inside arbitrary property data without a
/// dedicated Decodable type per property.
// public: the harness pattern-matches IRValue cases directly (e.g.
// `case .array(let a) = p.data`); cases of a public enum are public.
public indirect enum IRValue: Decodable {
    case null
    case bool(Bool)
    case int(Int)
    case double(Double)
    case string(String)
    case array([IRValue])
    case object([String: IRValue])

    // public: Decodable witness on a public type (see IRComponent note).
    public init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        if c.decodeNil() {
            self = .null
        } else if let b = try? c.decode(Bool.self) {
            self = .bool(b)
        } else if let i = try? c.decode(Int.self) {
            self = .int(i)
        } else if let d = try? c.decode(Double.self) {
            self = .double(d)
        } else if let s = try? c.decode(String.self) {
            self = .string(s)
        } else if let a = try? c.decode([IRValue].self) {
            self = .array(a)
        } else if let o = try? c.decode([String: IRValue].self) {
            self = .object(o)
        } else {
            throw DecodingError.typeMismatch(
                IRValue.self,
                .init(codingPath: decoder.codingPath, debugDescription: "Unknown JSON value")
            )
        }
    }

    // MARK: - Convenience accessors

    public var stringValue: String? {
        if case .string(let s) = self { return s }
        return nil
    }

    public var doubleValue: Double? {
        switch self {
        case .double(let d): return d
        case .int(let i): return Double(i)
        default: return nil
        }
    }

    public var intValue: Int? {
        switch self {
        case .int(let i): return i
        case .double(let d): return Int(d)
        default: return nil
        }
    }

    public var boolValue: Bool? {
        if case .bool(let b) = self { return b }
        return nil
    }

    public var objectValue: [String: IRValue]? {
        if case .object(let o) = self { return o }
        return nil
    }

    public var arrayValue: [IRValue]? {
        if case .array(let a) = self { return a }
        return nil
    }

    public subscript(key: String) -> IRValue? {
        objectValue?[key]
    }
}

// Wave 8 — structural equality for IR payloads. Synthesized (all payload
// types are Equatable, recursively). Used by the transition driver to
// detect which property values a state flip actually CHANGED
// (spec 07 §4 — transitions fire on effective-value change) and by tests.
// Same-file extension so the compiler can synthesize the witness.
extension IRValue: Equatable {}

// Property equality follows from type + data equality — the transition
// differ compares whole {type, data} envelopes.
extension IRProperty: Equatable {}

// MARK: - String coding key

/// Free-form string coding key for envelope-level key inspection: the v2
/// strict reader iterates `allKeys` to reject unknown envelope keys
/// (spec 05 rule 2), which a fixed CodingKeys enum cannot express.
struct IRAnyKey: CodingKey {
    let stringValue: String
    let intValue: Int? = nil
    init(_ s: String) { stringValue = s }
    init?(stringValue: String) { self.stringValue = stringValue }
    init?(intValue: Int) { return nil }
}
