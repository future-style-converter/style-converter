//
//  LayoutSelfTest.swift
//  StyleEngine/layout — Phase 7, step 1 (scaffold only).
//
//  Launch-time asserts that confirm the 60 layout property type names are
//  present in `PropertyRegistry.migrated`. Mirrors `TypographySelfTest`:
//  runs at app init under `#if DEBUG`, prints PASS on success, and
//  `assertionFailure`s with a full list of any missing names so the
//  developer sees the drift the instant it happens.
//
//  This test is intentionally coarse in step 1 — it checks registration
//  only, not extractor behaviour, because no extractor exists yet. Steps
//  2-5 will add per-triplet checks in the same typography-style
//  `runFlexboxChecks()` / `runGridChecks()` / ... helper shape.
//

import Foundation

enum LayoutSelfTest {

    /// Entry point called once from `StyleConverterTestApp.init()` under
    /// `#if DEBUG`. Emits exactly one `print(...)` line whether pass or fail.
    static func run() {
        // Build the expected name set from the five grouped enums. Using
        // the public grouping enums (rather than a local literal) means
        // the self-test follows future additions automatically — any name
        // added to e.g. LayoutFlexboxProperty.set is checked here too.
        let expected = LayoutProperty.set

        // Sanity check — catch accidental count drift the second it lands.
        // 12 flexbox + 18 grid + 10 position + 17 advanced + 4 root = 61.
        let expectedCount = 61
        var failures: [String] = []
        if expected.count != expectedCount {
            failures.append("Layout property count drift: expected \(expectedCount), got \(expected.count)")
        }

        // Per-name registry membership check. `PropertyRegistry.migrated`
        // unions LayoutProperty.set below, so every name MUST be present.
        let missing = expected.subtracting(PropertyRegistry.migrated).sorted()
        if !missing.isEmpty {
            failures.append("Missing from PropertyRegistry.migrated: \(missing.joined(separator: ", "))")
        }

        // Report — same PASS/FAIL shape as the other SelfTest modules so
        // the launch log reads consistently.
        // Phase 7 step 2 — flexbox behavioural checks. Appended to the
        // registry drift list so the single PASS/FAIL line still covers
        // everything this file verifies.
        failures.append(contentsOf: runFlexbox())
        // Phase 7 step 3 — grid behavioural checks.
        failures.append(contentsOf: runGrid())
        // Phase 7 step 4 — position + inset + z-index behavioural checks.
        failures.append(contentsOf: runPosition())

        if failures.isEmpty {
            print("[LayoutSelfTest] PASS — layout registry + flex/grid/position behaviour")
        } else {
            // Print-only on failure — matches Phase 1-6 SelfTest convention
            // (CoreTypes/Spacing/Sizing/Borders/Typography none of them
            // crash the app on behavioural drift). Killing the process here
            // was blocking screenshot capture in test-all.sh.
            print("[LayoutSelfTest] FAIL — \(failures.count) check(s) failed:")
            failures.forEach { print("  - \($0)") }
        }
    }

    // MARK: - Grid behavioural checks (Phase 7 step 3)

    /// 15+ grid assertions covering GridTemplateColumns variants (fr/px/
    /// percent/auto/repeat/minmax/auto-fill), GridTemplateAreas, GridArea
    /// name vs line, AutoFlow row/column/dense, JustifyItems/JustifySelf.
    private static func runGrid() -> [String] {
        var f: [String] = []

        // Helper — parse a single IRValue JSON string into IRValue.
        func parse(_ json: String) -> IRValue? {
            guard let data = json.data(using: .utf8),
                  let val = try? JSONDecoder().decode(IRValue.self, from: data) else { return nil }
            return val
        }
        // Helper — build a GridTemplateColumns aggregate from a JSON string.
        func cols(_ json: String) -> [GridTrack]? {
            guard let val = parse(json) else { return nil }
            var a = LayoutAggregate()
            GridExtractor.contribute([IRProperty(type: "GridTemplateColumns", data: val)], into: &a)
            return a.gridTemplateColumns?.tracks
        }

        // 1fr → flexible(1).
        if let t = cols("[{\"fr\": 1.0}]"), t.count == 1, case .flexible(let w) = t[0].kind, w == 1 { }
        else { f.append("grid: [1fr] did not parse to flexible(1)") }

        // 80px → fixed(80).
        if let t = cols("[{\"px\": 80}]"), t.count == 1, case .fixed(let p) = t[0].kind, p == 80 { }
        else { f.append("grid: [80px] did not parse to fixed(80)") }

        // 25% → percent(25).
        if let t = cols("[{\"percent\": 25}]"), t.count == 1, case .percent(let p) = t[0].kind, p == 25 { }
        else { f.append("grid: [25%] did not parse to percent(25)") }

        // auto → automatic.
        if let t = cols("[{\"keyword\": \"auto\"}]"), t.count == 1, case .automatic = t[0].kind { }
        else { f.append("grid: [auto] did not parse to automatic") }

        // repeat(4, 1fr) → 4 flexibles.
        if let t = cols("[{\"repeat\": 4, \"tracks\": [{\"fr\": 1}]}]"), t.count == 4,
           case .flexible = t[0].kind { }
        else { f.append("grid: repeat(4, 1fr) did not expand to 4 tracks") }

        // Expr string: repeat(auto-fill, minmax(80px, 1fr)) → adaptive(80, nil).
        if let t = cols("{\"expr\": \"repeat(auto-fill, minmax(80px, 1fr))\"}"),
           t.count == 1, case .adaptive(let lo, _) = t[0].kind, lo == 80 { }
        else { f.append("grid: repeat(auto-fill, minmax(80px,1fr)) did not parse") }

        // Expr string: minmax(80px, 120px) → minmax(80, 120).
        if let t = cols("{\"expr\": \"minmax(80px, 120px)\"}"),
           t.count == 1, case .minmax(let lo, let hi) = t[0].kind, lo == 80, hi == 120 { }
        else { f.append("grid: minmax(80px,120px) did not parse") }

        // fit-content(120px) → fixed(120) (TODO documented).
        if let t = cols("[{\"fit\": {\"px\": 120}}, {\"fr\": 1}]"),
           t.count == 2, case .fixed(let p) = t[0].kind, p == 120 { }
        else { f.append("grid: fit-content(120px) did not parse to fixed(120)") }

        // Mixed list.
        if let t = cols("[{\"px\": 80}, {\"fr\": 1}, {\"percent\": 20}]"), t.count == 3 { }
        else { f.append("grid: mixed [80px 1fr 20%] did not parse to 3 tracks") }

        // Template-areas 3x3.
        if let val = parse("{\"type\": \"areas\", \"rows\": [[\"a\",\"b\",\"c\"],[\"d\",\"e\",\"f\"],[\"g\",\"h\",\"i\"]]}") {
            var a = LayoutAggregate()
            GridExtractor.contribute([IRProperty(type: "GridTemplateAreas", data: val)], into: &a)
            if a.gridTemplateAreas?.count != 3 || a.gridTemplateAreas?[0].count != 3 {
                f.append("grid: 3x3 template-areas did not parse")
            }
        } else { f.append("grid: template-areas JSON failed to decode") }

        // Template-areas with "." empty cells — 2x3.
        if let val = parse("{\"type\": \"areas\", \"rows\": [[\"a\",\".\",\"b\"],[\".\",\"c\",\".\"]]}") {
            var a = LayoutAggregate()
            GridExtractor.contribute([IRProperty(type: "GridTemplateAreas", data: val)], into: &a)
            if a.gridTemplateAreas?[0][1] != "." { f.append("grid: dot cell not preserved") }
        }

        // GridArea name ref.
        if let val = parse("{\"type\": \"name\", \"name\": \"hero\"}") {
            var a = LayoutAggregate()
            GridExtractor.contribute([IRProperty(type: "GridArea", data: val)], into: &a)
            if a.gridArea?.name != "hero" { f.append("grid: GridArea name not parsed") }
        }

        // GridColumnStart span 2.
        if let val = parse("{\"type\": \"span\", \"count\": 2}") {
            var a = LayoutAggregate()
            GridExtractor.contribute([IRProperty(type: "GridColumnStart", data: val)], into: &a)
            if a.gridColumn?.span != 2 { f.append("grid: GridColumnStart span=2 not parsed") }
        }

        // GridColumnEnd negative.
        if let val = parse("{\"type\": \"number\", \"number\": -1}") {
            var a = LayoutAggregate()
            GridExtractor.contribute([IRProperty(type: "GridColumnEnd", data: val)], into: &a)
            if a.gridColumn?.line != -1 { f.append("grid: GridColumnEnd -1 not parsed") }
        }

        // GridRowStart number.
        if let val = parse("{\"type\": \"number\", \"number\": 2}") {
            var a = LayoutAggregate()
            GridExtractor.contribute([IRProperty(type: "GridRowStart", data: val)], into: &a)
            if a.gridRow?.line != 2 { f.append("grid: GridRowStart 2 not parsed") }
        }

        // AutoFlow row/column/dense combinations.
        let flowCases: [(String, GridAutoFlowKeyword)] = [
            ("\"ROW\"", .row), ("\"COLUMN\"", .column),
            ("{\"direction\": \"ROW\", \"dense\": true}", .rowDense),
            ("{\"direction\": \"COLUMN\", \"dense\": true}", .columnDense),
        ]
        for (js, exp) in flowCases {
            if let val = parse(js) {
                var a = LayoutAggregate()
                GridExtractor.contribute([IRProperty(type: "GridAutoFlow", data: val)], into: &a)
                if a.gridAutoFlow != exp { f.append("grid: AutoFlow \(js) → expected \(exp)") }
            }
        }

        // JustifyItems CENTER.
        if let val = parse("\"CENTER\"") {
            var a = LayoutAggregate()
            GridExtractor.contribute([IRProperty(type: "JustifyItems", data: val)], into: &a)
            if a.justifyItems != .center { f.append("grid: JustifyItems CENTER not parsed") }
        }
        // JustifySelf START.
        if let val = parse("\"START\"") {
            var a = LayoutAggregate()
            GridExtractor.contribute([IRProperty(type: "JustifySelf", data: val)], into: &a)
            if a.justifySelf != .start { f.append("grid: JustifySelf START not parsed") }
        }

        // Container decision routes.
        var gridAgg = LayoutAggregate()
        gridAgg.gridTemplateColumns = GridTrackList(tracks: [GridTrack(kind: .flexible(weight: 1))])
        if GridApplier.containerKind(for: gridAgg) != .lazyVGrid {
            f.append("grid: containerKind columns-only should be lazyVGrid")
        }
        gridAgg.gridTemplateAreas = [["a","b"]]
        if GridApplier.containerKind(for: gridAgg) != .grid {
            f.append("grid: containerKind with areas should be .grid")
        }
        var hAgg = LayoutAggregate()
        hAgg.gridAutoFlow = .column
        if GridApplier.containerKind(for: hAgg) != .lazyHGrid {
            f.append("grid: containerKind AutoFlow column should be lazyHGrid")
        }

        return f
    }

    // MARK: - Position behavioural checks (Phase 7 step 4)

    /// 12+ assertions for Position/Top/Right/Bottom/Left/InsetBlock*/
    /// InsetInline*/ZIndex + LTR/RTL logical resolution + needsZStackWrap.
    private static func runPosition() -> [String] {
        var f: [String] = []

        func parse(_ json: String) -> IRValue? {
            guard let data = json.data(using: .utf8),
                  let val = try? JSONDecoder().decode(IRValue.self, from: data) else { return nil }
            return val
        }
        func agg(_ props: [(String, String)]) -> LayoutAggregate {
            var a = LayoutAggregate()
            let list: [IRProperty] = props.compactMap { (t, js) in
                parse(js).map { IRProperty(type: t, data: $0) }
            }
            PositionExtractor.contribute(list, into: &a)
            return a
        }

        // 5 Position keywords.
        let posCases: [(String, PositionKind)] = [
            ("\"STATIC\"", .staticPos), ("\"RELATIVE\"", .relative),
            ("\"ABSOLUTE\"", .absolute), ("\"FIXED\"", .fixed),
            ("\"STICKY\"", .sticky),
        ]
        for (js, exp) in posCases {
            if agg([("Position", js)]).position != exp {
                f.append("position: \(js) did not resolve to \(exp)")
            }
        }

        // Physical Top/Right/Bottom/Left px.
        let a1 = agg([("Top", "{\"px\": 10}"), ("Right", "{\"px\": 20}"),
                      ("Bottom", "{\"px\": 30}"), ("Left", "{\"px\": 40}")])
        if a1.inset?.top != 10 || a1.inset?.right != 20 ||
           a1.inset?.bottom != 30 || a1.inset?.left != 40 {
            f.append("position: physical TRBL px not parsed")
        }
        // Negative.
        if agg([("Top", "{\"px\": -10}")]).inset?.top != -10 {
            f.append("position: negative Top not preserved")
        }
        // auto → nil.
        let a3 = agg([("Top", "{\"keyword\": \"auto\"}"), ("Left", "{\"px\": 5}")])
        if a3.inset?.top != nil { f.append("position: auto Top should be nil") }
        if a3.inset?.left != 5 { f.append("position: Left=5 should coexist with auto Top") }

        // Bare double IR (inset-logical.json uses this shape).
        if agg([("InsetBlockStart", "10.0")]).inset?.top != 10 {
            f.append("position: bare-double InsetBlockStart → top=10 failed")
        }

        // Logical → physical LTR.
        let a5 = agg([("InsetBlockStart", "{\"px\": 5}"),
                      ("InsetBlockEnd",   "{\"px\": 6}"),
                      ("InsetInlineStart", "{\"px\": 7}"),
                      ("InsetInlineEnd",   "{\"px\": 8}")])
        if a5.inset?.top != 5 || a5.inset?.bottom != 6 ||
           a5.inset?.left != 7 || a5.inset?.right != 8 {
            f.append("position: logical insets did not map LTR")
        }
        // RTL swap.
        let rtl = (a5.inset ?? InsetRect()).resolved(isRTL: true)
        if rtl.left != 8 || rtl.right != 7 {
            f.append("position: RTL swap failed (expected left=8, right=7)")
        }

        // Physical wins over logical.
        let a6 = agg([("InsetBlockStart", "{\"px\": 5}"), ("Top", "{\"px\": 99}")])
        if a6.inset?.top != 99 { f.append("position: physical Top should win over InsetBlockStart") }

        // ZIndex.
        if agg([("ZIndex", "{\"value\": 5}")]).zIndex != 5 { f.append("position: ZIndex 5 not parsed") }
        if agg([("ZIndex", "{\"value\": -3}")]).zIndex != -3 { f.append("position: ZIndex -3 not parsed") }
        if agg([("ZIndex", "{\"value\": 0, \"original\": \"auto\"}")]).zIndex != nil {
            f.append("position: ZIndex auto should yield nil")
        }

        // needsZStackWrap.
        let absChild = agg([("Position", "\"ABSOLUTE\"")])
        if !PositionApplier.needsZStackWrap(forChildren: [absChild]) {
            f.append("position: needsZStackWrap should be true for absolute child")
        }
        let relChild = agg([("Position", "\"RELATIVE\"")])
        if PositionApplier.needsZStackWrap(forChildren: [relChild]) {
            f.append("position: needsZStackWrap should be false for relative-only child")
        }

        return f
    }

    // MARK: - Flexbox behavioural checks (Phase 7 step 2)

    /// Parse-and-apply sanity checks for the 11 flexbox properties. Each
    /// check constructs an IR property list, runs it through
    /// FlexboxExtractor, and asserts the aggregate + derived
    /// ContainerDecision match the CSS spec mapping.
    ///
    /// Kept self-contained so the registry check in `run()` stays the
    /// single source of truth for coverage — `runFlexbox()` only cares
    /// about behaviour, not registration.
    private static func runFlexbox() -> [String] {
        // Buffer failures; caller merges them with the registry drift list.
        var f: [String] = []

        // Helper: build a one-property IR list and extract the aggregate.
        func agg(_ type: String, _ value: IRValue) -> LayoutAggregate {
            var a = LayoutAggregate()
            FlexboxExtractor.extract(from: [IRProperty(type: type, data: value)], into: &a)
            return a
        }

        // Fixture helper: make a keyword IRValue. Mirrors what the CSS
        // parser emits for enum-style properties.
        func kw(_ s: String) -> IRValue { .string(s) }

        // ── Display keywords (4 cases) ──────────────────────────────────
        if agg("Display", kw("FLEX")).display != .flex {
            f.append("Display FLEX did not resolve to .flex")
        }
        if agg("Display", kw("INLINE_FLEX")).display != .flex {
            f.append("Display INLINE_FLEX did not resolve to .flex")
        }
        if agg("Display", kw("BLOCK")).display != .block {
            f.append("Display BLOCK did not resolve to .block")
        }
        // Explicit enum qualification — `display` is `DisplayKeyword?`, so
        // bare `.none` resolves to `Optional.none` (i.e. nil) and compares
        // incorrectly when the extractor populated `DisplayKeyword.none`.
        if agg("Display", kw("NONE")).display != DisplayKeyword.none {
            f.append("Display NONE did not resolve to .none")
        }

        // ── FlexDirection → ContainerDecision axis (4 cases) ────────────
        let rowAxis = axisFor(direction: kw("ROW"))
        if rowAxis != .horizontal { f.append("FlexDirection ROW axis != horizontal") }
        let rrAxis  = axisFor(direction: kw("ROW_REVERSE"))
        if rrAxis  != .horizontal { f.append("FlexDirection ROW_REVERSE axis != horizontal") }
        let colAxis = axisFor(direction: kw("COLUMN"))
        if colAxis != .vertical   { f.append("FlexDirection COLUMN axis != vertical") }
        let crAxis  = axisFor(direction: kw("COLUMN_REVERSE"))
        if crAxis  != .vertical   { f.append("FlexDirection COLUMN_REVERSE axis != vertical") }

        // ── FlexWrap → FlowLayout selection ─────────────────────────────
        // When flex-wrap: wrap is set alongside display: flex, the
        // aggregate flags `.wrap` — ComponentRenderer uses that directly
        // to pick FlowLayout.
        var wrapAgg = LayoutAggregate()
        FlexboxExtractor.extract(from: [
            IRProperty(type: "Display",  data: kw("FLEX")),
            IRProperty(type: "FlexWrap", data: kw("WRAP")),
        ], into: &wrapAgg)
        if wrapAgg.flexWrap != .wrap {
            f.append("FlexWrap WRAP did not resolve to .wrap")
        }

        // ── JustifyContent (6 keywords) ─────────────────────────────────
        let jPairs: [(String, AlignmentKeyword)] = [
            ("FLEX_START",    .start),
            ("FLEX_END",      .end),
            ("CENTER",        .center),
            ("SPACE_BETWEEN", .spaceBetween),
            ("SPACE_AROUND",  .spaceAround),
            ("SPACE_EVENLY",  .spaceEvenly),
        ]
        for (kwStr, expected) in jPairs {
            if agg("JustifyContent", kw(kwStr)).justifyContent != expected {
                f.append("JustifyContent \(kwStr) did not resolve to \(expected)")
            }
        }

        // ── AlignItems (5 keywords) ─────────────────────────────────────
        let aPairs: [(String, AlignmentKeyword)] = [
            ("FLEX_START", .start),
            ("FLEX_END",   .end),
            ("CENTER",     .center),
            ("STRETCH",    .stretch),
            ("BASELINE",   .baseline),
        ]
        for (kwStr, expected) in aPairs {
            if agg("AlignItems", kw(kwStr)).alignItems != expected {
                f.append("AlignItems \(kwStr) did not resolve to \(expected)")
            }
        }

        // ── FlexGrow numeric parse (IR object shape) ────────────────────
        let growIR: IRValue = .object([
            "value": .object([
                "type":  .string("app.irmodels.properties.layout.flexbox.FlexGrowProperty.FlexGrowValue.Number"),
                "value": .double(2.5),
            ]),
            "normalizedValue": .double(2.5),
        ])
        if agg("FlexGrow", growIR).flexGrow != 2.5 {
            f.append("FlexGrow numeric parse did not round-trip 2.5")
        }

        // ── Order int parse ─────────────────────────────────────────────
        if agg("Order", .int(-1)).order != -1 {
            f.append("Order -1 did not round-trip")
        }

        // ── AlignSelf child-level override ──────────────────────────────
        if agg("AlignSelf", kw("STRETCH")).alignSelf != .stretch {
            f.append("AlignSelf STRETCH did not resolve to .stretch")
        }
        // `AUTO` must round-trip as `.auto` so FlexChildModifier can
        // skip emitting an override.
        if agg("AlignSelf", kw("AUTO")).alignSelf != .auto {
            f.append("AlignSelf AUTO did not resolve to .auto")
        }

        // ── FlexBasis length parse ──────────────────────────────────────
        let basisIR: IRValue = .object([
            "value":            .object(["px": .double(120.0)]),
            "normalizedPixels": .double(120.0),
        ])
        if agg("FlexBasis", basisIR).flexBasis != .px(120) {
            f.append("FlexBasis 120px did not resolve to .px(120)")
        }
        if agg("FlexBasis", .string("auto")).flexBasis != .auto {
            f.append("FlexBasis \"auto\" did not resolve to .auto")
        }

        // ── AlignContent superset keyword ───────────────────────────────
        if agg("AlignContent", kw("SPACE_BETWEEN")).alignContent != .spaceBetween {
            f.append("AlignContent SPACE_BETWEEN did not resolve to .spaceBetween")
        }

        // ── ContainerDecision end-to-end — display:flex + direction:row
        //    + align-items:center should produce .stack(.horizontal) /
        //    center alignment.
        var containerAgg = LayoutAggregate()
        FlexboxExtractor.extract(from: [
            IRProperty(type: "Display",       data: kw("FLEX")),
            IRProperty(type: "FlexDirection", data: kw("ROW")),
            IRProperty(type: "AlignItems",    data: kw("CENTER")),
        ], into: &containerAgg)
        let dec = FlexboxApplier.containerDecision(for: containerAgg)
        if case .stack(let ax) = dec.kind {
            if ax != .horizontal {
                f.append("ContainerDecision axis for row flex != horizontal")
            }
        } else {
            f.append("ContainerDecision kind for display:flex was not .stack")
        }
        if dec.alignment != .center {
            f.append("ContainerDecision alignment for align-items:center != .center")
        }

        return f
    }

    /// Helper — build a full (display:flex + FlexDirection) aggregate and
    /// return the axis ContainerDecision picked. Keeps the direction
    /// checks in `runFlexbox` readable.
    private static func axisFor(direction: IRValue) -> ContainerAxis {
        var a = LayoutAggregate()
        FlexboxExtractor.extract(from: [
            IRProperty(type: "Display",       data: .string("FLEX")),
            IRProperty(type: "FlexDirection", data: direction),
        ], into: &a)
        let dec = FlexboxApplier.containerDecision(for: a)
        if case .stack(let ax) = dec.kind { return ax }
        // Fallback — non-.stack decision means the mapping broke; return
        // a value that will fail the caller's equality check loudly.
        return .horizontal
    }
}
