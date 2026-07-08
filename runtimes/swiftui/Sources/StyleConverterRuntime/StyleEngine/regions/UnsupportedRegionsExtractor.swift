//
//  UnsupportedRegionsExtractor.swift
//  StyleEngine/regions — Phase 10.
//

import Foundation

enum UnsupportedRegionsProperty {
    /// 10 entries — flow-into/from, region-fragment, continue,
    /// copy-into, wrap-{flow,through,before,after,inside}.
    static let names: [String] = [
        "FlowInto", "FlowFrom", "RegionFragment", "Continue", "CopyInto",
        "WrapFlow", "WrapThrough", "WrapBefore", "WrapAfter", "WrapInside",
    ]
    static var set: Set<String> { Set(names) }
}

enum UnsupportedRegionsExtractor {
    static func extract(from properties: [IRProperty]) -> UnsupportedRegionsConfig? {
        var cfg = UnsupportedRegionsConfig()
        let owned = UnsupportedRegionsProperty.set
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
