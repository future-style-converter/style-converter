//
//  ContainerExtractor.swift
//  StyleEngine/container — Phase 10.
//

import Foundation

enum ContainerProperty {
    static let names: [String] = [
        "Container", "ContainerName", "ContainerType",
    ]
    static var set: Set<String> { Set(names) }
}

enum ContainerExtractor {
    static func extract(from properties: [IRProperty]) -> ContainerConfig? {
        var cfg = ContainerConfig()
        let owned = ContainerProperty.set
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
