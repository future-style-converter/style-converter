//
//  MulticolFloatStrip.swift
//  StyleEngine/columns — wave-44 lane U8.
//
//  Pure FLOAT-STRIP classification + geometry: the iOS TWIN of the Android
//  runtime's MulticolFloatStrip.kt + MulticolFloatStripPlan.kt. The shared
//  FS pin table lives in MulticolFloatStripTests.swift and the Android
//  MulticolFloatStripTest.kt with the SAME rows (native-pair parity gate).
//
//  The model (CSS 2.1 §9.5 + §9.5.2 inside css-multicol-1 column boxes),
//  hand-derived from the frozen Chromium refs (tools/wpt/refs/9b5435…/
//  CSS2/floats-clear__floats-clear-multicol-*.png):
//   • a float takes NO block-axis space (out of flow) and paints at its
//     float position, anchored at its parent's content top for the
//     leading-float shapes this lane proves;
//   • a following `clear` sibling gets §9.5.2 CLEARANCE — its top border
//     edge lands at the relevant floats' bottom outer edge (strip 250 in
//     every fixture: 3px orange at ref rows 161-163 under fill, 5px at
//     rows 171-175 under balance);
//   • the whole flow is ONE strip fragmenting into column boxes
//     (css-break-3 §4): fill keeps the definite H (100 → 100+100+50 aqua
//     slices), balance reduces to H = ceil(C/N) (255/3 → the refs' 85).
//
//  CONSUMPTION STATUS (iOS): FULLY CONSUMED — seam 1 closed in wave 45
//  (lane X3), seam 2 in wave 48 (lane W3). The renderer's multicol
//  branch builds the composition-time engagement
//  (MulticolFloatStrip.engagedStrip), publishes the §9.5.2 zero-flow
//  plan on the floatClearancePlan environment around the strip content,
//  and composes the css-break-3 §4 slice REPLAY through the multi-child
//  clone-row seam documented since wave-42 (one clone of the whole
//  strip content per used column, each shown through
//  MulticolFloatStripSliceLayout — the measure half, the Compose
//  MulticolFloatStripMeasure mirror), so float ink taller than one
//  column slices across columns exactly like Compose's drawWithContent
//  replay. On Android the Kotlin twin is consumed by
//  MulticolFloatStripMeasure; the FS table stays native-pair-pinned on
//  both platforms.
//

// CoreGraphics for the FragmentGeometry shapes; Foundation for ceil().
import CoreGraphics
import Foundation

// Namespacing enum — all members static + pure (MulticolMath pattern).
enum MulticolFloatStrip {

    /// One proven leading float: id (zero-flow key) + side + px extent.
    struct FloatFact: Equatable {
        /// The float component's document-unique id — keys the synthetic
        /// §9.5.2 zero-flow plan the renderer publishes on the
        /// floatClearancePlan environment around the strip content. Retro
        /// P2e dropped the "once the renderer seam lands" future tense: that
        /// is seam 1 in the banner above, closed in wave 45 (lane X3).
        let componentId: String
        /// Physical side under the engine's LTR normalization (true=right).
        let rightSide: Bool
        /// Declared px height — §9.5.2 clears past top + height (margins/
        /// borders/padding bail below, so the outer edge IS the height).
        let heightPx: Double
    }

    /// One multicol child's strip-relevant facts (nil = out of scope).
    struct ChildFacts: Equatable {
        /// The child's own id — the zero-flow plan's scope membership.
        let componentId: String
        /// Leading floats anchored at this child's content top (≤1/side).
        let floats: [FloatFact]
        /// §9.5.2 clearance sides this child declares (`clear` keyword).
        let clearsLeft: Bool
        let clearsRight: Bool
        /// IR-derived paint-extent floor in px: borders + content the
        /// child paints even when its MEASURED height clamps shorter (the
        /// height:0 box whose childless child carries the orange
        /// border-bottom in floats-clear-multicol-003 — Chromium's
        /// balanced column height includes that 5px band).
        let trailingInkPx: Double
    }

    /// Project one multicol child into ChildFacts, or nil when any wire
    /// flavor is outside the proven scope — the FloatClearanceModel
    /// strict-bail contract: the strip only fires when every simulated
    /// position is exact, never as a guess.
    static func factsFor(_ child: IRComponent) -> ChildFacts? {
        // Live selector/media buckets can re-style any read below after
        // the static facts baked — bail (mirrors collapse bail B6).
        if !(child.selectors?.isEmpty ?? true) { return nil }
        if !(child.media?.isEmpty ?? true) { return nil }
        // Generated content paints boxes this projection cannot see.
        if child.pseudos != nil { return nil }
        // Per-wire bails on the child itself (same set as the Kotlin twin).
        for p in child.properties {
            let kw = keyword(p.data)
            // A floated DIRECT child of the multicol is different geometry
            // (it floats within a column box, not within a sibling).
            if p.type == "Float", let k = kw, floatSides.contains(k) { return nil }
            // Any margin breaks the flush cursor stacking the strip
            // assumes (measured heights exclude margins).
            if p.type.hasPrefix("Margin") { return nil }
            // Positioned children paint away from their flow slot
            // (CSS 2.1 §9.4.3) — relative/sticky would slip through the
            // role classifier as FLOW, so bail here.
            if p.type == "Position", kw != "STATIC" { return nil }
            // Vertical writing modes / rtl remap the axes this lane's
            // y-math and LTR side normalization assume.
            if p.type == "WritingMode" || p.type == "Direction" { return nil }
            // Transforms move ink off the strip (css-transforms-1 §3).
            if transforms.contains(p.type) { return nil }
            // A nested multicol leaves the plain block strip entirely.
            if p.type == "ColumnCount" || p.type == "ColumnWidth" { return nil }
        }
        // The LEADING run of floated boxes anchors at the child's content
        // top (CSS 2.1 §9.5 — nothing precedes them in flow).
        let kids = child.children ?? []
        var floats: [FloatFact] = []
        var i = 0
        while i < kids.count {
            // Side from the Float wire (LTR fold: inline-start → left,
            // inline-end → right — css-logical-1 §2.1, the same fold as
            // FloatClearanceModel; the Direction bail above pins LTR).
            guard let side = kids[i].properties.first(where: { $0.type == "Float" })
                .flatMap({ keyword($0.data) }),
                floatSides.contains(side) else { break }
            // The float must be fully provable: childless + textless
            // (content could overflow the declared extent), explicit px
            // height (the §9.5.2 ledger's whole outer size), and none of
            // the wires that would grow its outer edge past that height —
            // nor its own clear (§9.5.1+§9.5.2 combined is out of scope).
            let f = kids[i]
            if !(f.children?.isEmpty ?? true) || !(f.text?.isEmpty ?? true) { return nil }
            guard let h = f.properties.first(where: { $0.type == "Height" })
                .flatMap({ px($0.data) }) else { return nil }
            if f.properties.contains(where: {
                $0.type.hasPrefix("Margin") || $0.type.hasPrefix("Padding") ||
                    $0.type.hasPrefix("Border") || $0.type == "BoxSizing" ||
                    $0.type == "Clear" || $0.type == "Transform"
            }) { return nil }
            floats.append(FloatFact(
                componentId: f.id,
                rightSide: side == "RIGHT" || side == "INLINE_END",
                heightPx: h))
            i += 1
        }
        // Two same-side floats stack per §9.5.1 rules 2/3 — not modeled
        // (FloatClearance's exact one-per-side rule).
        if floats.filter({ $0.rightSide }).count > 1 { return nil }
        if floats.filter({ !$0.rightSide }).count > 1 { return nil }
        // No float may hide PAST the leading run: its anchor would not be
        // the content top this model pins.
        for j in i..<kids.count where hasFloatedDescendant(kids[j]) { return nil }
        // A float-bearing child must carry no text of its own: line boxes
        // beside floats shorten (CSS 2.1 §9.5) — not modeled.
        if !floats.isEmpty && !(child.text?.isEmpty ?? true) { return nil }
        // §9.5.2 clear sides from the child's own Clear wire (LTR fold).
        let clear = child.properties.first(where: { $0.type == "Clear" })
            .flatMap { keyword($0.data) }
        return ChildFacts(
            componentId: child.id,
            floats: floats,
            clearsLeft: clear == "LEFT" || clear == "INLINE_START" || clear == "BOTH",
            clearsRight: clear == "RIGHT" || clear == "INLINE_END" || clear == "BOTH",
            trailingInkPx: trailingInk(child))
    }

    /// IR-derived paint-extent floor: the child's block borders plus the
    /// taller of its declared height and its direct childless children's
    /// own extents — the height:0 cleared box's inner `.bar` paints its
    /// border-bottom BELOW the measured 0 (css-overflow-3 §2), and
    /// Chromium's balanced column height includes that band.
    private static func trailingInk(_ child: IRComponent) -> Double {
        // Declared height, if a plain px wire.
        let declared = child.properties.first(where: { $0.type == "Height" })
            .flatMap { px($0.data) }
        // Stacked extents of direct childless+textless non-float children
        // (block flow from the content top — CSS 2.1 §9.4.1).
        let childrenExtent = (child.children ?? [])
            .filter { ($0.children?.isEmpty ?? true) && ($0.text?.isEmpty ?? true) }
            .filter { c in
                !c.properties.contains {
                    $0.type == "Float" && keyword($0.data).map(floatSides.contains) == true
                }
            }
            .map { c in
                // Each stacked child: height + its own block borders
                // (border-box extent, css-box-4 §2).
                (c.properties.first(where: { $0.type == "Height" }).flatMap { px($0.data) } ?? 0)
                    + borderPx(c, "Top") + borderPx(c, "Bottom")
            }
            .reduce(0, +)
        // The child's own block borders wrap the content band.
        return borderPx(child, "Top") + max(declared ?? 0, childrenExtent)
            + borderPx(child, "Bottom")
    }

    /// Used block border width of one side in px: declared px, else the
    /// `medium` default 3px when only a visible style is declared
    /// (css-backgrounds-3 §4.3 — the same 3px BordersApplier paints, so
    /// the ink floor matches the paint).
    private static func borderPx(_ c: IRComponent, _ side: String) -> Double {
        // Style first: none/hidden (or absent) ⇒ used width 0 (§4.3).
        let style = c.properties.first(where: { $0.type == "Border\(side)Style" })
            .flatMap { keyword($0.data) }
        guard let s = style, s != "NONE", s != "HIDDEN" else { return 0 }
        // Declared width, else the UA `medium` default.
        return c.properties.first(where: { $0.type == "Border\(side)Width" })
            .flatMap { px($0.data) } ?? 3.0
    }

    /// Any box in the subtree declaring an actually-floated Float wire.
    private static func hasFloatedDescendant(_ c: IRComponent) -> Bool {
        if c.properties.contains(where: {
            $0.type == "Float" && keyword($0.data).map(floatSides.contains) == true
        }) { return true }
        return (c.children ?? []).contains { hasFloatedDescendant($0) }
    }

    /// IR keyword, SHOUTY-normalized like every keyword read here.
    private static func keyword(_ v: IRValue?) -> String? {
        ValueExtractors.extractKeyword(v).map { ValueExtractors.normalize($0) }
    }

    /// Plain-px wire read: `{"type":"length","px":N}` or bare `{"px":N}` —
    /// percentage / keyword flavors need live context a pure pass lacks.
    private static func px(_ v: IRValue) -> Double? {
        guard v["type"]?.stringValue != "percentage" else { return nil }
        return v["px"]?.doubleValue
    }

    /// The two floated `float` keywords, logical members folded LTR.
    private static let floatSides: Set<String> = ["LEFT", "RIGHT", "INLINE_START", "INLINE_END"]

    /// Transform-family wires (containing-block makers + ink movers).
    private static let transforms: Set<String> = ["Transform", "Translate", "Scale", "Rotate"]

    /// Whether the float strip owns this container's layout — the exact
    /// Kotlin gate: every child a plain FLOW box with proven facts, no
    /// forced breaks, and at least one actual float.
    static func engages(_ specs: [MulticolSpannerFlow.ChildSpec]?) -> Bool {
        // No specs = dark stage / text-only container — never engage.
        guard let specs, !specs.isEmpty else { return false }
        // Spanners/statics/forced-break roles have owning models; the
        // anonymous leading-text spec carries no facts and bails too.
        if specs.contains(where: { $0.role != .flow }) { return false }
        if specs.contains(where: { $0.forcedBreakContent }) { return false }
        // Every child must be proven; any nil fact disables the strip.
        if specs.contains(where: { $0.floatStrip == nil }) { return false }
        // At least one real float — otherwise this model adds nothing.
        return specs.contains { !($0.floatStrip?.floats.isEmpty ?? true) }
    }

}
