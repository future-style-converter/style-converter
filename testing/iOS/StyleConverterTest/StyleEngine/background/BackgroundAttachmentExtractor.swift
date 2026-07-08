//
//  BackgroundAttachmentExtractor.swift
//  StyleEngine/background — Phase 4.
//
//  Parses BackgroundAttachment. IR shape: array of {type: <kind>}.
//

import Foundation

enum BackgroundAttachmentProperty {
    static let names: [String] = ["BackgroundAttachment"]
}

enum BackgroundAttachmentExtractor {

    static func extract(from properties: [IRProperty]) -> BackgroundAttachmentConfig? {
        var layers: [BackgroundAttachmentMode] = []
        var seen = false
        for prop in properties where prop.type == "BackgroundAttachment" {
            seen = true
            layers = parseArray(prop.data)
        }
        guard seen, !layers.isEmpty else { return nil }
        return BackgroundAttachmentConfig(layers: layers)
    }

    private static func parseArray(_ v: IRValue) -> [BackgroundAttachmentMode] {
        guard case .array(let arr) = v else { return [] }
        return arr.map(parseLayer)
    }

    // Each entry is `{type: "scroll"|"fixed"|"local"}`. Unknown → scroll.
    private static func parseLayer(_ v: IRValue) -> BackgroundAttachmentMode {
        switch v["type"]?.stringValue?.lowercased() {
        case "fixed": return .fixed
        case "local": return .local
        default:      return .scroll
        }
    }
}
