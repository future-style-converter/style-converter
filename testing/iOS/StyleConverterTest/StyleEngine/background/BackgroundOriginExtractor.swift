//
//  BackgroundOriginExtractor.swift
//  StyleEngine/background — Phase 4.
//
//  Parses BackgroundOrigin. IR shape: array of {type: <kind>} objects.
//

import Foundation

enum BackgroundOriginProperty {
    static let names: [String] = ["BackgroundOrigin"]
}

enum BackgroundOriginExtractor {

    static func extract(from properties: [IRProperty]) -> BackgroundOriginConfig? {
        var layers: [BackgroundOriginMode] = []
        var seen = false
        for prop in properties where prop.type == "BackgroundOrigin" {
            seen = true
            layers = parseArray(prop.data)
        }
        guard seen, !layers.isEmpty else { return nil }
        return BackgroundOriginConfig(layers: layers)
    }

    // Outer array.
    private static func parseArray(_ v: IRValue) -> [BackgroundOriginMode] {
        guard case .array(let arr) = v else { return [] }
        return arr.map(parseLayer)
    }

    // Each entry is `{type: "border-box"}` etc. Default safe.
    private static func parseLayer(_ v: IRValue) -> BackgroundOriginMode {
        let type = v["type"]?.stringValue?.lowercased() ?? "padding-box"
        switch type {
        case "border-box":  return .borderBox
        case "content-box": return .contentBox
        default:            return .paddingBox
        }
    }
}
