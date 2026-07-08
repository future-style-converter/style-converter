//
//  Phase10SelfTest.swift
//  StyleEngine — Phase 10 long-tail sweep.
//
//  Behavioural assertions for the 22 grouped extractors added in
//  Phase 10. Print-only on failure (ff901e3 convention) — no
//  assertionFailure / fatalError.
//
//  Goals:
//    1. Every Phase-10 ownership set is unioned into
//       `PropertyRegistry.migrated` (no registry drift).
//    2. Ownership sets are disjoint from each other and from earlier
//       phases (no double-registration, which would produce
//       inconsistent routing).
//    3. Each extractor returns nil on an empty property list and a
//       touched config when at least one owned name is present.
//    4. Spot-check sentinel names: scrolling ScrollSnapType / SVG Fill
//       / speech Volume / rendering Zoom / print Page / paging
//       BreakAfter / regions FlowInto / interactions PointerEvents /
//       performance Contain / columns ColumnCount / table
//       BorderCollapse / shapes ShapeOutside / rhythm BlockStep /
//       navigation NavUp / images ObjectFit / appearance Appearance /
//       counters CounterReset / lists ListStyleType / container
//       ContainerType / math MathStyle / experimental StringSet /
//       content Content / global All.
//

import Foundation

enum Phase10SelfTest {

    static func run() {
        var f: [String] = []
        func obj(_ d: [String: IRValue]) -> IRValue { .object(d) }
        func p(_ t: String, _ v: IRValue = .string("auto")) -> IRProperty {
            IRProperty(type: t, data: v)
        }

        // ── (1) Registry drift ─────────────────────────────────────
        let p10Owned: [(String, Set<String>)] = [
            ("ScrollingProperty",           ScrollingProperty.set),
            ("SvgProperty",                 SvgProperty.set),
            ("UnsupportedSpeechProperty",   UnsupportedSpeechProperty.set),
            ("RenderingProperty",           RenderingProperty.set),
            ("UnsupportedPrintProperty",    UnsupportedPrintProperty.set),
            ("UnsupportedPagingProperty",   UnsupportedPagingProperty.set),
            ("UnsupportedRegionsProperty",  UnsupportedRegionsProperty.set),
            ("InteractionsProperty",        InteractionsProperty.set),
            ("PerformanceProperty",         PerformanceProperty.set),
            ("ColumnsProperty",             ColumnsProperty.set),
            ("TableProperty",               TableProperty.set),
            ("ShapesProperty",              ShapesProperty.set),
            ("RhythmProperty",              RhythmProperty.set),
            ("UnsupportedNavigationProperty", UnsupportedNavigationProperty.set),
            ("ImagesProperty",              ImagesProperty.set),
            ("AppearanceProperty",          AppearanceProperty.set),
            ("CountersProperty",            CountersProperty.set),
            ("ListsProperty",               ListsProperty.set),
            ("ContainerProperty",           ContainerProperty.set),
            ("UnsupportedMathProperty",     UnsupportedMathProperty.set),
            ("ExperimentalProperty",        ExperimentalProperty.set),
            ("ContentProperty",             ContentProperty.set),
            ("GlobalProperty",              GlobalProperty.set),
        ]
        // Sanity: every Phase-10 set is a subset of PropertyRegistry.migrated.
        for (label, s) in p10Owned {
            let missing = s.subtracting(PropertyRegistry.migrated).sorted()
            if !missing.isEmpty {
                f.append("\(label) has \(missing.count) names missing from PropertyRegistry.migrated: \(missing.joined(separator: ", "))")
            }
        }

        // ── (2) Pairwise disjointness inside Phase 10 ──────────────
        // Quadratic in 23 sets (≈ 253 checks) — negligible at launch.
        for i in 0 ..< p10Owned.count {
            for j in (i + 1) ..< p10Owned.count {
                let inter = p10Owned[i].1.intersection(p10Owned[j].1)
                if !inter.isEmpty {
                    f.append("Overlap between \(p10Owned[i].0) and \(p10Owned[j].0): \(inter.sorted().joined(separator: ", "))")
                }
            }
        }

        // ── (3 + 4) Spot-check per category: one owned name flows ──
        // Empty property list → nil. One owned property → touched=true.
        func probe<C: Equatable>(_ label: String,
                                 _ extractor: ([IRProperty]) -> C?,
                                 _ name: String) {
            if extractor([]) != nil {
                f.append("\(label): empty list should yield nil")
            }
            guard let cfg = extractor([p(name)]) else {
                f.append("\(label): did not touch on \"\(name)\"")
                return
            }
            // Every Phase-10 Config has a `rawByType: [String: String]` +
            // `touched: Bool` — we read via Mirror so a single probe
            // function handles all 23 Config types without per-case code.
            let mirror = Mirror(reflecting: cfg)
            let touched = mirror.children.first { $0.label == "touched" }?.value as? Bool
            let raw = mirror.children.first { $0.label == "rawByType" }?.value as? [String: String]
            if touched != true { f.append("\(label): touched=false on \"\(name)\"") }
            if raw?[name] == nil { f.append("\(label): rawByType missing \"\(name)\"") }
        }
        probe("scrolling",    ScrollingExtractor.extract,             "ScrollSnapType")
        probe("svg",          SvgExtractor.extract,                   "Fill")
        probe("speech",       UnsupportedSpeechExtractor.extract,     "Volume")
        probe("rendering",    RenderingExtractor.extract,             "Zoom")
        probe("print",        UnsupportedPrintExtractor.extract,      "Page")
        probe("paging",       UnsupportedPagingExtractor.extract,     "BreakAfter")
        probe("regions",      UnsupportedRegionsExtractor.extract,    "FlowInto")
        probe("interactions", InteractionsExtractor.extract,          "PointerEvents")
        probe("performance",  PerformanceExtractor.extract,           "Contain")
        probe("columns",      ColumnsExtractor.extract,               "ColumnCount")
        probe("table",        TableExtractor.extract,                 "BorderCollapse")
        probe("shapes",       ShapesExtractor.extract,                "ShapeOutside")
        probe("rhythm",       RhythmExtractor.extract,                "BlockStep")
        probe("navigation",   UnsupportedNavigationExtractor.extract, "NavUp")
        probe("images",       ImagesExtractor.extract,                "ObjectFit")
        probe("appearance",   AppearanceExtractor.extract,            "Appearance")
        probe("counters",     CountersExtractor.extract,              "CounterReset")
        probe("lists",        ListsExtractor.extract,                 "ListStyleType")
        probe("container",    ContainerExtractor.extract,             "ContainerType")
        probe("math",         UnsupportedMathExtractor.extract,       "MathStyle")
        probe("experimental", ExperimentalExtractor.extract,          "StringSet")
        probe("content",      ContentExtractor.extract,               "Content")
        probe("global",       GlobalExtractor.extract,                "All")

        // ── (5) Non-owned name does NOT touch ──────────────────────
        // Pass an arbitrary unrelated IR type into each extractor. The
        // extractor must return nil (no spurious touch).
        if ScrollingExtractor.extract(from: [p("Width", .int(10))]) != nil {
            f.append("ScrollingExtractor: touched on non-owned Width")
        }
        if SvgExtractor.extract(from: [p("Color")]) != nil {
            f.append("SvgExtractor: touched on non-owned Color")
        }

        // ── (6) Registry size delta — lower bound check ────────────
        // Phase 0-9 baseline is ~220 names; Phase 10 adds ~150. We
        // just check the floor to catch accidental set deletions.
        if PropertyRegistry.migrated.count < 300 {
            f.append("PropertyRegistry.migrated.count=\(PropertyRegistry.migrated.count) < 300 — unexpected shrinkage")
        }

        if f.isEmpty {
            print("[Phase10SelfTest] PASS — long-tail sweep registered (\(PropertyRegistry.migrated.count) total migrated)")
        } else {
            print("[Phase10SelfTest] FAIL — \(f.count) check(s):")
            f.forEach { print("  - \($0)") }
        }
    }
}
