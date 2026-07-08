//
//  MarginTrimExtractor.swift
//  StyleEngine/spacing — Phase 2.
//
//  Reads the single `MarginTrim` IR property — a bare JSON string in
//  SCREAMING_SNAKE_CASE — into a MarginTrimConfig. Returns nil when no
//  `MarginTrim` longhand appears.
//

// Foundation only.
import Foundation

enum MarginTrimProperty {
    // Single-member list so PropertyRegistry.migrated and StyleBuilder
    // keep one source of truth. Keeps the API shape symmetric with the
    // other spacing extractors.
    static let names: [String] = ["MarginTrim"]
}

enum MarginTrimExtractor {

    // Extract. Treats an unrecognised or non-string payload as `.none`.
    static func extract(from properties: [IRProperty]) -> MarginTrimConfig? {
        // Find the first MarginTrim (there can only be one per element).
        for prop in properties where prop.type == "MarginTrim" {
            if case .string(let s) = prop.data,
               let mode = MarginTrimMode(rawValue: s.uppercased()) {
                return MarginTrimConfig(mode: mode)
            }
            // Unknown literal — still count it as a touched property so
            // the registry ledger reflects it, but fall back to `.none`.
            return MarginTrimConfig(mode: .none)
        }
        return nil
    }
}
