//
//  IsolationExtractor.swift
//  StyleEngine/performance — Phase 4.
//
//  Reads the `Isolation` property. IR shape is a bare string "AUTO" or
//  "ISOLATE".
//

import Foundation

enum IsolationProperty {
    static let names: [String] = ["Isolation"]
}

enum IsolationExtractor {

    // Nil when no Isolation property in IR.
    static func extract(from properties: [IRProperty]) -> IsolationConfig? {
        var cfg = IsolationConfig()
        var touched = false
        for prop in properties where prop.type == "Isolation" {
            // String case is the documented shape; defend against object-
            // wrapping just in case. Unknown strings fall through to auto.
            let raw = prop.data.stringValue
                ?? prop.data["value"]?.stringValue
                ?? "AUTO"
            cfg.mode = (raw.uppercased() == "ISOLATE") ? .isolate : .auto
            cfg.hasAny = true
            touched = true
        }
        return touched ? cfg : nil
    }
}
