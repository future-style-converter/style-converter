//
//  BackgroundClipExtractor.swift
//  StyleEngine/background — Phase 4.
//
//  Parses BackgroundClip. IR shape: array of UPPERCASE strings.
//

import Foundation

enum BackgroundClipProperty {
    static let names: [String] = ["BackgroundClip"]
}

enum BackgroundClipExtractor {

    // Single-pass; last-wins. Returns nil when no property was present
    // so the applier is identity (equivalent to border-box default).
    static func extract(from properties: [IRProperty]) -> BackgroundClipConfig? {
        var layers: [BackgroundClipMode] = []
        var seen = false
        for prop in properties where prop.type == "BackgroundClip" {
            seen = true
            layers = parseArray(prop.data)
        }
        guard seen, !layers.isEmpty else { return nil }
        // Collapse to the first layer's mode for the render path.
        return BackgroundClipConfig(mode: layers[0], layers: layers, hasAny: true)
    }

    // Parse the outer array.
    private static func parseArray(_ v: IRValue) -> [BackgroundClipMode] {
        guard case .array(let arr) = v else { return [] }
        return arr.map(parseLayer)
    }

    // Layer entry → enum case. Unknown strings default to border-box.
    private static func parseLayer(_ v: IRValue) -> BackgroundClipMode {
        let s = v.stringValue?.uppercased() ?? "BORDER_BOX"
        switch s {
        case "BORDER_BOX":  return .borderBox
        case "PADDING_BOX": return .paddingBox
        case "CONTENT_BOX": return .contentBox
        case "TEXT":        return .text
        default:            return .borderBox
        }
    }
}
