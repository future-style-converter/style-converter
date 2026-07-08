//
//  RhythmExtractor.swift
//  StyleEngine/rhythm — Phase 10.
//

import Foundation

enum RhythmProperty {
    /// 5 entries — mirrors `app/irmodels/properties/rhythm/`.
    static let names: [String] = [
        "BlockStep", "BlockStepAlign", "BlockStepInsert",
        "BlockStepRound", "BlockStepSize",
    ]
    static var set: Set<String> { Set(names) }
}

enum RhythmExtractor {
    static func extract(from properties: [IRProperty]) -> RhythmConfig? {
        var cfg = RhythmConfig()
        let owned = RhythmProperty.set
        for p in properties where owned.contains(p.type) {
            cfg.touched = true
            if let kw = ValueExtractors.extractKeyword(p.data) {
                cfg.rawByType[p.type] = kw
            } else {
                cfg.rawByType[p.type] = String(describing: p.data)
            }
        }
        return cfg.touched ? cfg : nil
    }
}
