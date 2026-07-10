//
//  GridTrackMath.swift
//  StyleEngine/layout/grid — fidelity wave 1.
//
//  The PURE track-sizing half of the CSS-grid subset: css-grid-1 §7.2
//  column-width resolution (px / % / fr / auto / minmax, definite and
//  fit-content container widths) and row-height resolution (template
//  px tracks / grid-auto-rows / content). Mirrors Android's
//  GridRenderer.computeTrackWidths approximations so the two native
//  runtimes drift together. XCTest-pinned in FidelityWave1Tests.
//

import CoreGraphics

// MARK: - Track sizing math

enum GridTrackMath {

    /// Resolve column tracks to pixel widths — approximation of
    /// css-grid-1 §7.2 mirroring Android's computeTrackWidths:
    ///   • px literal            → px
    ///   • percent               → pct of the definite container width
    ///   • auto                  → max-content of the column's items
    ///   • fr                    → weighted share of the free space
    ///   • minmax(minPx, maxPx)  → clamp(share-of-free, min, max)
    ///   • minmax(minPx, nil=fr) → max(min, fr-share) like Android
    /// With an INDEFINITE container (`width: fit-content` — web harness
    /// default), fr/auto/percent all collapse to the column's
    /// max-content so the grid hugs like web (nested-3level/014).
    static func columnWidths(tracks: [GridTrack.Kind],
                             containerWidth: CGFloat?,
                             columnGap: CGFloat,
                             maxContent: [CGFloat]) -> [CGFloat] {
        let n = tracks.count
        // Zero tracks → nothing to size.
        guard n > 0 else { return [] }
        // Max-content lookup with a safe 0 default per column.
        func mc(_ i: Int) -> CGFloat { i < maxContent.count ? maxContent[i] : 0 }

        // Indefinite container: every intrinsic/flexible track sizes to
        // its content, fixed tracks keep their length.
        guard let w = containerWidth else {
            return tracks.enumerated().map { i, t in
                switch t {
                case .fixed(let px):            return px
                case .percent, .automatic,
                     .flexible, .adaptive:      return mc(i)
                case .minmax(let lo, let hi):
                    // Content clamped into the minmax bounds.
                    return min(max(mc(i), lo ?? 0), hi ?? .greatestFiniteMagnitude)
                }
            }
        }

        // Definite container: fixed bases first, then fr distribution.
        let gaps = columnGap * CGFloat(max(0, n - 1))
        // Base sizes for the non-flexible tracks (fr entries start at 0).
        var base: [CGFloat] = tracks.enumerated().map { i, t in
            switch t {
            case .fixed(let px):        return px
            case .percent(let p):       return p / 100 * w
            case .automatic:            return mc(i)
            case .adaptive(let lo, _):  return lo ?? mc(i)
            case .minmax(let lo, _):    return lo ?? 0
            case .flexible:             return 0
            }
        }
        // Sum of flexible weights; minmax with a nil max acts as 1fr
        // with a floor (the only minmax-fr shape the IR keeps).
        let frWeights: [CGFloat] = tracks.map { t in
            switch t {
            case .flexible(let wgt):        return wgt
            case .minmax(_, let hi):        return hi == nil ? 1 : 0
            default:                        return 0
            }
        }
        let frSum = frWeights.reduce(0, +)
        if frSum > 0 {
            // Free space = container − gaps − non-flexible bases.
            let fixed = zip(tracks, base).reduce(CGFloat(0)) { acc, pair in
                if case .flexible = pair.0 { return acc }
                if case .minmax(_, nil) = pair.0 { return acc }
                return acc + pair.1
            }
            let free = max(0, w - gaps - fixed)
            for i in 0..<n {
                // fr share proportional to weight; minmax keeps its
                // floor when the share is smaller (§7.2.4 outcome).
                if case .flexible = tracks[i] {
                    base[i] = free * frWeights[i] / frSum
                } else if case .minmax(let lo, nil) = tracks[i] {
                    base[i] = max(lo ?? 0, free * frWeights[i] / frSum)
                }
            }
        } else {
            // No fr tracks: leftover stretches auto tracks equally —
            // Chrome's `justify-content: normal` auto-track stretch.
            let autos = tracks.indices.filter {
                if case .automatic = tracks[$0] { return true }
                return false
            }
            if !autos.isEmpty {
                let used = base.reduce(0, +) + gaps
                let extra = max(0, (w - used) / CGFloat(autos.count))
                for i in autos { base[i] += extra }
            }
        }
        return base
    }

    /// Resolve row heights: an explicit `grid-template-rows` px entry
    /// wins; every other kind (auto / fr / % of an indefinite height)
    /// sizes to the tallest span-1 item in that row. Rows past the
    /// template take the first fixed `grid-auto-rows` size when
    /// declared, else content.
    static func rowHeights(template: [GridTrack.Kind]?,
                           autoRows: GridTrack.Kind?,
                           rowCount: Int,
                           items: [(cell: GridCell, height: CGFloat)]) -> [CGFloat] {
        // Content height per row = max of span-1 items anchored there.
        var content = [CGFloat](repeating: 0, count: rowCount)
        for it in items where it.cell.rowSpan == 1 && it.cell.row < rowCount {
            content[it.cell.row] = max(content[it.cell.row], it.height)
        }
        // Fold in the template / auto-rows fixed sizes.
        return (0..<rowCount).map { r in
            // Inside the explicit template?
            if let tpl = template, r < tpl.count {
                if case .fixed(let px) = tpl[r] { return px }
                // percent-of-indefinite / fr / auto → content height.
                return content[r]
            }
            // Implicit row: grid-auto-rows fixed size if present.
            if case .fixed(let px)? = autoRows { return px }
            return content[r]
        }
    }
}
