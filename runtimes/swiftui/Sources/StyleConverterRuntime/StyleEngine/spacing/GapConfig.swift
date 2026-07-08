//
//  GapConfig.swift
//  StyleEngine/spacing — Phase 2.
//
//  CSS `gap` splits into RowGap + ColumnGap; the fixture converter always
//  expands shorthand to both longhands. Each side carries a Phase 1
//  LengthValue so the applier can resolve em/percent/viewport the same
//  way as padding + margin.
//

// Foundation for consistency.
import Foundation

struct GapConfig: Equatable {
    // Spacing between rows in a flex column / grid.
    var row: LengthValue = .exact(px: 0)
    // Spacing between columns in a flex row / grid.
    var column: LengthValue = .exact(px: 0)

    // True when either dimension carries a non-zero value. Callers use
    // this to decide whether to override the container's default spacing.
    var hasAny: Bool {
        !isZero(row) || !isZero(column)
    }

    private func isZero(_ v: LengthValue) -> Bool {
        if case .exact(0) = v { return true }
        return false
    }
}
