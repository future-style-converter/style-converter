//
//  IRModels.swift
//  StyleConverterTest
//
//  Swift equivalent of the Android IRModels.kt / web IRModels.ts.
//
//  A single IRDocument is loaded from tmpOutput.json at launch.
//  Each IRProperty carries a `type` (e.g. "Width", "BackgroundColor")
//  and a free-form `data` blob (IRValue) decoded from arbitrary JSON.
//

import Foundation

/// Root document: a list of components.
struct IRDocument: Decodable {
    let components: [IRComponent]
}

/// A single UI component with styles, selectors, media queries, and children.
///
/// `_text` and `_tag` carry renderer-only metadata from the WPT extractor:
///   - `_text` is the styled element's inner text content; rendered as a
///     leading inline node alongside children for mixed-content layouts
///     (Bug 1, swarm-002/css-text-decor__text-decoration-decorating-box-thickness-001).
///   - `_tag` is the originating HTML element name (lowercase: 'ol', 'li',
///     'p', 'h1', etc.); lets the renderer wire up list-marker generation
///     and other tag-default behaviour (Bug 2, swarm-002/css-counter-styles__css3-counter-styles-101).
/// Both are optional — when absent the renderer falls back to its
/// pre-existing behaviour, keeping the 327-pair visual-test baseline stable.
struct IRComponent: Decodable {
    let id: String
    let name: String
    let properties: [IRProperty]
    let selectors: [IRSelector]?
    let media: [IRMedia]?
    let children: [IRComponent]?
    let _text: String?
    let _tag: String?

    enum CodingKeys: String, CodingKey {
        case id, name, properties, selectors, media, children, _text, _tag
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decodeIfPresent(String.self, forKey: .id) ?? ""
        name = try c.decodeIfPresent(String.self, forKey: .name) ?? "Unknown"
        properties = try c.decodeIfPresent([IRProperty].self, forKey: .properties) ?? []
        selectors = try c.decodeIfPresent([IRSelector].self, forKey: .selectors)
        media = try c.decodeIfPresent([IRMedia].self, forKey: .media)
        children = try c.decodeIfPresent([IRComponent].self, forKey: .children)
        _text = try c.decodeIfPresent(String.self, forKey: ._text)
        _tag = try c.decodeIfPresent(String.self, forKey: ._tag)
    }
}

/// A CSS property as (type, data) pair.
struct IRProperty: Decodable {
    let type: String
    let data: IRValue
}

/// Pseudo-class selector styles (e.g. :hover).
struct IRSelector: Decodable {
    let condition: String
    let properties: [IRProperty]
}

/// Media query scoped styles.
struct IRMedia: Decodable {
    let query: String
    let properties: [IRProperty]
}

// MARK: - IRValue (dynamic JSON)

/// A generic JSON value. The Android side uses `JsonElement`; we mirror that with
/// a recursive enum so we can peek inside arbitrary property data without a
/// dedicated Decodable type per property.
indirect enum IRValue: Decodable {
    case null
    case bool(Bool)
    case int(Int)
    case double(Double)
    case string(String)
    case array([IRValue])
    case object([String: IRValue])

    init(from decoder: Decoder) throws {
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

    var stringValue: String? {
        if case .string(let s) = self { return s }
        return nil
    }

    var doubleValue: Double? {
        switch self {
        case .double(let d): return d
        case .int(let i): return Double(i)
        default: return nil
        }
    }

    var intValue: Int? {
        switch self {
        case .int(let i): return i
        case .double(let d): return Int(d)
        default: return nil
        }
    }

    var boolValue: Bool? {
        if case .bool(let b) = self { return b }
        return nil
    }

    var objectValue: [String: IRValue]? {
        if case .object(let o) = self { return o }
        return nil
    }

    var arrayValue: [IRValue]? {
        if case .array(let a) = self { return a }
        return nil
    }

    subscript(key: String) -> IRValue? {
        objectValue?[key]
    }
}
