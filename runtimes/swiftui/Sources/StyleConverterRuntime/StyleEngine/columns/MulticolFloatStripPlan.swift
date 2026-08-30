//
//  MulticolFloatStripPlan.swift
//  StyleEngine/columns — wave-44 lane U8 (the geometry half).
//
//  The strip GEOMETRY of the float-strip twin — split from
//  MulticolFloatStrip.swift (classification) exactly like the Kotlin pair
//  MulticolFloatStrip.kt / MulticolFloatStripPlan.kt, keeping both under
//  the repo's file-size rule. Same FS pin rows, same banner: see
//  MulticolFloatStrip.swift for the model + the iOS CONSUMPTION STATUS
//  (pure module + pins only until the two renderer seams land).
//

// CoreGraphics for the FragmentGeometry shapes; Foundation for ceil().
import CoreGraphics
import Foundation

// The geometry half lives in an extension so call sites read identically
// to the Kotlin twin's MulticolFloatStripPlan facade.
extension MulticolFloatStrip {

    /// The strip answer: child offsets + used column size + replay slices.
    struct StripPlan: Equatable {
        /// Block offset of each child in the CONTINUOUS strip (column-0
        /// coordinates) — flush cursor stacking except §9.5.2 clearance.
        let yOffsetsPx: [Double]
        /// The used column block-size: the definite H under fill:auto
        /// (§7.2), min(H, ceil(C/N)) under balance (§7.1 → the refs' 85).
        let columnBlockSizePx: Double
        /// C — the strip's ink extent (flow ∨ float bottoms ∨ ink floor).
        let stripInkPx: Double
        /// The css-break-3 §4 slices (empty when the strip fits a column).
        let fragments: [FragmentGeometry.Fragment]
    }

    /// Build the strip plan — the Kotlin MulticolFloatStripPlan.plan twin,
    /// same FS rows. Measured heights are the children's IN-FLOW extents
    /// — on both platforms the published zero-flow plan guarantees that
    /// (Android: MulticolFloatStripMeasure; iOS since wave 48:
    /// MulticolFloatStripSliceLayout's zero-flow measure).
    static func plan(specs: [MulticolSpannerFlow.ChildSpec],
                     measuredHeightsPx: [Double],
                     columnBlockSize: Double,
                     columnFillAuto: Bool,
                     columnWidth: Double,
                     gapPx: Double,
                     columnCount: Int) -> StripPlan? {
        // Defensive re-gate: engagement + aligned inputs + a real
        // fragmentainer + ≥2 columns (N==1 keeps the overflow-not-clip
        // semantics of the legacy path — the run twin's rationale).
        guard engages(specs) else { return nil }
        guard specs.count == measuredHeightsPx.count else { return nil }
        guard columnBlockSize > 0, columnCount > 1 else { return nil }
        // The strip walk: flush cursor stacking (CSS 2.1 §9.4.1) with
        // §9.5.2 clearance jumps and per-side float-bottom ledgers.
        var cursor = 0.0
        var leftBottom = 0.0
        var rightBottom = 0.0
        var ink = 0.0
        var offsets: [Double] = []
        offsets.reserveCapacity(specs.count)
        for (i, s) in specs.enumerated() {
            // Engage proved every fact non-nil.
            let f = s.floatStrip!
            // §9.5.2: the cleared box's top border edge lands at the
            // relevant floats' bottom outer edge (greater-of rule).
            var y = cursor
            if f.clearsLeft { y = max(y, leftBottom) }
            if f.clearsRight { y = max(y, rightBottom) }
            offsets.append(y)
            // Anchor the leading floats at this child's content top and
            // extend the per-side ledgers (§9.5: out of flow — only the
            // ledgers, never the cursor, grow).
            for fl in f.floats {
                let bottom = y + fl.heightPx.rounded()
                if fl.rightSide { rightBottom = max(rightBottom, bottom) }
                else { leftBottom = max(leftBottom, bottom) }
            }
            // The cursor advances by the measured in-flow extent only.
            cursor = y + max(0, measuredHeightsPx[i])
            // Ink floor: measured extent or the IR paint floor, whichever
            // is taller (the height:0 clamp — css-overflow-3 §2).
            ink = max(ink, y + max(measuredHeightsPx[i], f.trailingInkPx.rounded()))
        }
        // C: flow bottom ∨ float bottoms ∨ trailing ink.
        let c = max(ink, cursor, leftBottom, rightBottom)
        // Used column block-size: §7.2 sequential fill keeps the definite
        // H; §7.1 balance reduces to ceil(C/N), capped at the definite H
        // (content that cannot balance falls back to fill and clips like
        // the wave-10 N cap).
        let h = columnFillAuto ? columnBlockSize
            : min(columnBlockSize, ceil(c / Double(columnCount)))
        // A degenerate strip has no geometry.
        guard h > 0 else { return nil }
        // Slices via the shared wave-10 geometry (S-table): F = ceil(C/H)
        // capped at N; each clip is the full column band, so trailing ink
        // just past C still paints inside the last column (no browser
        // clips it — css-overflow-3 §2). nil (fits) maps to the empty
        // identity list, same decision as the Kotlin twin.
        let fragments = FragmentGeometry.fragments(childBlockSize: c,
                                                   columnBlockSize: h,
                                                   columnWidth: columnWidth,
                                                   gapPx: gapPx,
                                                   columnCount: columnCount) ?? []
        return StripPlan(yOffsetsPx: offsets, columnBlockSizePx: h,
                         stripInkPx: c, fragments: fragments)
    }
}
