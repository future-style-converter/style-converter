//  FloatAvoidPlan.swift — StyleEngine/layout, wave-53 lane L4 (float-avoid).
//  CSS 2.1 §9.5: a normal-flow box that establishes a BFC "must not overlap the margin box of any floats in the
//  same block formatting context". Pure twin of Compose layout/FloatAvoidPlan.kt (same gate, same numbers, pin for
//  pin); the adapter is Renderer/FloatAvoidLayout.swift. Why: the VStack had no float avoidance —
//  blockFloatSegments() bails on R,L,R and FloatClearance needs a Clear — so contain-inline-size-bfc-floats-001/-002
//  and display-flow-root-002 started the BFC below every stacked float (tools/titan/results/wave53-plan/
//  layout-degenerates.md §4.1). shape() is nil — the frozen path — unless every input is proven px wire; G2–G8
//  follow PLAN.md §2 L4, G1 (wptCaptureMode) is checked at the ComponentRenderer call site.

import Foundation  // Double math only — the view adapter lives in Renderer/.

enum FloatAvoidPlan {
    /// A proven sibling list: children[0..<k] float, children[k] is the BFC root.
    struct Shape: Equatable {
        let sides: [Side]                 // physical side per float (G3 pins LTR)
        let floatWidthsPx: [Double], floatHeightsPx: [Double]   // G6 px margin boxes off the wire
        var bfcInlineSizePx: Double       // G7 used inline size, CSS px (var: the P8 what-if)
        let containerWidthPx: Double?     // P7: the container's own px Width (nil = auto)
        let containerIsBfc: Bool          // §10.6.7: a BFC root's auto height encloses floats
    }
    /// Physical float sides (logical ones mapped for LTR, css-logical-1 §2.1).
    enum Side: Equatable { case left, right }
    /// Solved geometry, index-aligned with the children (floats, then the BFC); width/height = the container's.
    struct Placement: Equatable { let x: [Double]; let y: [Double]; let width: Double; let height: Double }

    /// The gated shape, or nil (identity). `containerProperties` is the container's EFFECTIVE list (resolvedProperties;
    /// Compose reads the same merged list), so an inherited writing-mode / direction refuses.
    static func shape(_ container: IRComponent, containerProperties: [IRProperty]? = nil) -> Shape? {
        let cp = containerProperties ?? container.properties          // the container's wire
        let containerBfc = establishesBfc(cp)                           // read once: G2 and §10.6.7
        // G2 — FloatClearance's attach rule: a composed root or a BFC root, so no outside float intrudes.
        if container.slot?.parent != nil && !containerBfc { return nil }
        // G3 — plain LTR horizontal-tb block container, no own text/runs/pseudos/buckets.
        guard plainBox(container), !cp.contains(where: { containerBails.contains($0.type) }) else { return nil }
        guard blockDisplays.contains(keyword(cp, "Display")), staticPositions.contains(keyword(cp, "Position")) else { return nil }
        if hasWire(container, "Clear") { return nil }                  // G5 — any Clear below is W5's
        let kids = container.children ?? []                            // G4 — floats…, then ONE BFC root, last
        guard kids.count >= 2 else { return nil }
        let floats = Array(kids.dropLast()), bfc = kids[kids.count - 1]   // the k floats; the BFC candidate
        let sides = floats.compactMap(sideOf)                          // every leading child floats
        guard sides.count == floats.count else { return nil }
        if zip(sides, sides.dropFirst()).contains(where: { $0 == $1 }) { return nil } // G5 — runs: FloatRowPacking
        // G6 — floats: plain childless px boxes, no box-model bands, block display, static/relative (CSS 2.1 §9.7).
        guard floats.allSatisfy({ plainBox($0) && ($0.children ?? []).isEmpty && !boxBails($0) }) else { return nil }
        guard floats.allSatisfy({ [nil, "BLOCK"].contains(keyword($0.properties, "Display"))       // abs/fixed: float → none
            && [nil, "STATIC", "RELATIVE"].contains(keyword($0.properties, "Position")) }) else { return nil }
        let widths = floats.compactMap { lengthPx($0, "Width") }, heights = floats.compactMap { lengthPx($0, "Height") }
        guard widths.count == floats.count, heights.count == floats.count else { return nil } // every float px-sized
        let bp = bfc.properties                                        // G4 — the in-flow BFC root itself
        guard sideOf(bfc) == nil, plainBox(bfc), !boxBails(bfc), establishesBfc(bp) else { return nil }
        guard blockDisplays.contains(keyword(bp, "Display")), staticPositions.contains(keyword(bp, "Position")) else { return nil }
        let pxWidth = lengthPx(bfc, "Width")                           // G7 — provable used inline size
        guard let used = pxWidth ?? (containedIntrinsic(bfc) ? 0 : nil) else { return nil }
        // G8 — a contain-sized BFC paints nothing of its own: natively it is measured at content width,
        // and in wire order its paint would sit ABOVE the floats (Appendix E: step 4, below them).
        if pxWidth == nil && bp.contains(where: { paints($0.type) }) { return nil }
        guard paintedDescendantsInline(bfc) else { return nil }        // G8 for descendants (step 7 only)
        return Shape(sides: sides, floatWidthsPx: widths, floatHeightsPx: heights, bfcInlineSizePx: used,
                     containerWidthPx: lengthPx(container, "Width"), containerIsBfc: containerBfc)
    }

    /// §9.5.1 float placement, then §9.5 BFC avoidance, in one unit (points == CSS px at the capture scale);
    /// `incomingWidth` is the proposal, used only when the container declares no px Width (P7).
    static func place(_ shape: Shape, floatWidths: [Double], floatHeights: [Double],
                      incomingWidth: Double, bfcHeight: Double, scale: Double = 1) -> Placement {
        let w = shape.containerWidthPx.map { $0 * scale } ?? incomingWidth // P7: the root's 400, not 358
        var rects: [Rect] = []                                         // placed float margin boxes
        var floor = 0.0                                                // §9.5.1 rule 5: not above earlier tops
        for i in shape.sides.indices {
            let fw = floatWidths[i], fh = floatHeights[i], isLeft = shape.sides[i] == .left
            var y = floor                                              // rule 8: as high as possible
            while true {
                let (l, r) = gap(rects, y, fh, w)                      // the band's float-free span
                let next = rects.map(\.b).filter { $0 > y }.min()
                if r - l >= fw || next == nil {                        // fits, or no float left to pass
                    let x = isLeft ? l : r - fw                        // rules 1-3: hug the band edge
                    rects.append(Rect(l: x, t: y, r: x + fw, b: y + fh, isLeft: isLeft))
                    break
                }
                y = next!                                              // too narrow: drop to the next bottom
            }
            floor = y
        }
        let used = shape.bfcInlineSizePx * scale                       // G7's size in the input unit
        // Candidate tops: the static position 0, then every float bottom, ascending.
        let candidates = Array(Set([0.0] + rects.map(\.b))).sorted()
        var bx = 0.0, by = candidates[candidates.count - 1]            // default: below every float
        for cy in candidates {
            let (l, r) = gap(rects, cy, bfcHeight, w)
            if r - l >= used { bx = l; by = cy; break }                // first band whose gap holds it
        }
        let bfcBottom = by + bfcHeight, floatBottom = rects.map(\.b).max() ?? 0
        // §10.6.3: floats do not grow a non-BFC container's auto height; §10.6.7: a BFC root's they do.
        let height = shape.containerIsBfc ? max(bfcBottom, floatBottom) : bfcBottom
        return Placement(x: rects.map(\.l) + [bx], y: rects.map(\.t) + [by], width: w, height: height)
    }
    /// One placed float's margin box.
    private struct Rect { let l, t, r, b: Double; let isLeft: Bool }

    /// The float-free span over the HALF-OPEN band [y, y+h): a float ending at y does not touch a band starting at y;
    /// a zero-height band is the point y.
    private static func gap(_ rects: [Rect], _ y: Double, _ h: Double, _ w: Double) -> (Double, Double) {
        let hit = rects.filter { $0.b > y && ($0.t < y + h || $0.t <= y) }   // intersecting floats
        let l = hit.filter(\.isLeft).map(\.r).max() ?? 0                      // left floats push right
        let r = hit.filter { !$0.isLeft }.map(\.l).min() ?? w                 // right floats pull left
        return (max(0, l), min(w, r))                                         // clamp to the content box
    }
    /// G7 contained arm: an intrinsic Width keyword under inline-size containment ALONE.
    private static func containedIntrinsic(_ bfc: IRComponent) -> Bool {
        let p = bfc.properties
        guard intrinsicWidths.contains(keyword(p, "Width")) else { return false } // fit-/min-/max-content
        let c = containTokens(p)                                     // Compose: PerformanceExtractor's table
        // css-contain-3 §4.2 sizes the box as if empty: fit-content = 0. Size/block-size containment
        // would zero the height and paint containment would clip — unmodeled natively, so bail.
        guard c.contains("INLINE_SIZE"), c.isDisjoint(with: ["SIZE", "BLOCK_SIZE", "PAINT"]), !clips(p) else { return false }
        return !hasText(bfc)                                         // text wraps at 0 in CSS, not natively
    }
    /// BFC roots (CSS 2.1 §9.4.1, css-display-3): flow-root, clipping overflow, layout/paint containment.
    private static func establishesBfc(_ p: [IRProperty]) -> Bool {
        keyword(p, "Display") == "FLOW_ROOT" || clips(p) || !containTokens(p).isDisjoint(with: ["LAYOUT", "PAINT"])
    }

    /// Contain tokens, shorthands expanded (css-contain-1 §2) — Kotlin PerformanceExtractor's table; the LAST wire wins.
    private static func containTokens(_ p: [IRProperty]) -> Set<String> {
        guard let d = p.last(where: { $0.type == "Contain" })?.data else { return [] }
        let raw = d.arrayValue?.compactMap(\.stringValue) ?? (d.stringValue?.split(separator: " ").map(String.init) ?? [])
        let expand = ["NONE": [], "STRICT": ["LAYOUT", "PAINT", "SIZE", "STYLE"], "CONTENT": ["LAYOUT", "PAINT", "STYLE"]]
        return Set(raw.flatMap { t in expand[t.uppercased()] ?? [t.uppercased().replacingOccurrences(of: "-", with: "_")] })
    }
    /// Any non-visible overflow axis — the Compose OverflowExtractor fold (later wires win per axis).
    private static func clips(_ p: [IRProperty]) -> Bool {
        var x = "VISIBLE", y = "VISIBLE"
        for q in p where q.type.hasPrefix("Overflow") {
            // Only the five css-overflow-3 §3 keywords count; anything else folds to the initial VISIBLE.
            let k = keyword([q], q.type) ?? "VISIBLE"
            let b = ["HIDDEN", "SCROLL", "AUTO", "CLIP"].contains(k) ? k : "VISIBLE"
            switch q.type {
            case "Overflow": x = b; y = b                              // the shorthand sets both axes
            case "OverflowX", "OverflowInline": x = b                  // logical aliases under the LTR pin
            case "OverflowY", "OverflowBlock": y = b
            default: break                                             // OverflowAnchor / ClipMargin / Wrap
            }
        }
        return x != "VISIBLE" || y != "VISIBLE"
    }
    /// Physical float side (the FloatExtractor table, LAST wire wins), logical sides mapped for LTR.
    private static func sideOf(_ c: IRComponent) -> Side? {
        let sides: [String: Side] = ["LEFT": .left, "INLINE_START": .left, "RIGHT": .right, "INLINE_END": .right]
        return keyword(c.properties.last(where: { $0.type == "Float" }).map { [$0] } ?? [], "Float").flatMap { sides[$0] }
    }
    /// No own text/runs/pseudos/live buckets, and a UA-margin-free tag (div or none).
    private static func plainBox(_ c: IRComponent) -> Bool {
        (c.text ?? "").isEmpty && (c.meta?.runs ?? []).isEmpty && c.pseudos == nil &&
            (c.selectors ?? []).isEmpty && (c.media ?? []).isEmpty && [nil, "div"].contains(c.meta?.sourceTag?.lowercased())
    }
    /// Box-model bands / axis remaps this lane's px arithmetic does not model.
    private static func boxBails(_ c: IRComponent) -> Bool {
        c.properties.contains { p in ["Margin", "Padding", "Border"].contains { p.type.hasPrefix($0) } || boxBailTypes.contains(p.type) }
    }
    /// Every painted descendant is an inline-level px-wide atom (Appendix E step 7), recursively.
    private static func paintedDescendantsInline(_ c: IRComponent) -> Bool {
        (c.children ?? []).allSatisfy { d in
            (!d.properties.contains { paints($0.type) } ||
                (inlineLevel.contains(keyword(d.properties, "Display")) && lengthPx(d, "Width") != nil)) &&
                paintedDescendantsInline(d)
        }
    }
    /// Wires that paint the box itself (outline excluded: Chromium paints it on top too).
    private static func paints(_ t: String) -> Bool { t.hasPrefix("Background") || t.hasPrefix("Border") || t == "BoxShadow" }
    /// Own text anywhere in the subtree (leaf `text` or `meta.runs`).
    private static func hasText(_ c: IRComponent) -> Bool { !(c.text ?? "").isEmpty || !(c.meta?.runs ?? []).isEmpty || (c.children ?? []).contains(where: hasText) }
    /// Any box in the subtree declaring `type`.
    private static func hasWire(_ c: IRComponent, _ t: String) -> Bool { c.properties.contains { $0.type == t } || (c.children ?? []).contains { hasWire($0, t) } }
    /// Plain-px length wire (`{"type":"length","px":N}` or `{"px":N}`); % and keywords → nil.
    private static func lengthPx(_ c: IRComponent, _ type: String) -> Double? {
        guard let d = c.properties.first(where: { $0.type == type })?.data, d.objectValue != nil else { return nil }
        if d["type"]?.stringValue == "percentage" { return nil }      // needs live context
        return d["px"]?.doubleValue
    }

    /// SHOUTY keyword of the first `type` wire — Kotlin ValueExtractors.extractKeyword's reading (a string, or an
    /// object's keyword, then value), never an object's `type`.
    private static func keyword(_ p: [IRProperty], _ type: String) -> String? {
        guard let d = p.first(where: { $0.type == type })?.data else { return nil }
        return (d.stringValue ?? d["keyword"]?.stringValue ?? d["value"]?.stringValue)
            .map { $0.uppercased().replacingOccurrences(of: "-", with: "_") }
    }

    // G3 container refusals: block-axis remaps, gaps the VStack would insert, multicol, sizing folds.
    private static let containerBails: Set<String> = ["WritingMode", "Direction", "Gap", "RowGap", "ColumnGap",
        "ColumnCount", "ColumnWidth", "BoxSizing", "Float", "MinWidth", "MaxWidth", "InlineSize", "MinInlineSize", "MaxInlineSize"]
    // G6/G7 float + BFC refusals (Margin*/Padding*/Border* prefixes are checked in boxBails).
    private static let boxBailTypes: Set<String> = ["BoxSizing", "MinWidth", "MaxWidth", "MinHeight", "MaxHeight",
        "InlineSize", "BlockSize", "MinInlineSize", "MaxInlineSize", "MinBlockSize", "MaxBlockSize", "AspectRatio",
        "WritingMode", "Direction", "Order", "Transform", "Translate", "Scale", "Rotate"]
    private static let blockDisplays: [String?] = [nil, "BLOCK", "FLOW_ROOT"]   // absent = block
    private static let staticPositions: [String?] = [nil, "STATIC"]            // absent = initial static
    private static let intrinsicWidths: [String?] = ["FIT_CONTENT", "MIN_CONTENT", "MAX_CONTENT"] // css-sizing-3 §3.2
    private static let inlineLevel: [String?] = ["INLINE", "INLINE_BLOCK", "INLINE_FLEX", "INLINE_GRID", "INLINE_TABLE"]
}
