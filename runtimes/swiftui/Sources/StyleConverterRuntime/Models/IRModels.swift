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
// public: decoded directly by the harness app (ContentView.loadDocument)
// and by any package consumer feeding IR into the renderer.
public struct IRDocument: Decodable {
    // public: the harness walks/flattens the component list for capture.
    public let components: [IRComponent]
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
// public: the unit the harness renders + screenshots (CaptureCanvas,
// ScreenshotCaptureView, ComponentGallery all take IRComponent).
public struct IRComponent: Decodable {
    // public: all stored fields are read by the harness gallery/capture UI
    // (id/name labels, properties list, children flattening); _text/_tag
    // are part of the decode contract pinned by IRModelsTests.
    public let id: String
    public let name: String
    public let properties: [IRProperty]
    public let selectors: [IRSelector]?
    public let media: [IRMedia]?
    public let children: [IRComponent]?
    public let _text: String?
    public let _tag: String?

    enum CodingKeys: String, CodingKey {
        case id, name, properties, selectors, media, children, _text, _tag
    }

    // public: hand-written Decodable witness on a public type must be
    // public (Swift access-control rule for protocol witnesses).
    public init(from decoder: Decoder) throws {
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
// public: the harness capture path inspects raw (type, data) pairs to
// decide paint-context suppression (ScreenshotCaptureView.parentCreatesContext).
public struct IRProperty: Decodable {
    // public: dispatch key ("Width", "BackgroundColor", ...).
    public let type: String
    // public: free-form IR payload the harness pattern-matches on.
    public let data: IRValue
}

/// Pseudo-class selector styles (e.g. :hover).
// public: exposed through IRComponent.selectors (public stored property).
public struct IRSelector: Decodable {
    public let condition: String
    public let properties: [IRProperty]
}

/// Media query scoped styles.
// public: exposed through IRComponent.media (public stored property).
public struct IRMedia: Decodable {
    public let query: String
    public let properties: [IRProperty]
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
