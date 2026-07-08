//
//  WritingModeApplier.swift
//  StyleEngine/typography/writing — Phase 6.
//
//  Records the vertical-writing intent on the aggregate. No visual
//  effect is applied today: SwiftUI has no native `writing-mode` analogue,
//  and a naive `.rotationEffect(.degrees(90))` approximation was tried in
//  Phase 12 and measurably regressed SSIM (it rotates glyphs but also
//  changes the reported layout box from tall→wide, so the container shape
//  diverges from the web TALL-box reference more than silent identity
//  does — rows 022/023/024 of the typography audit fixture dropped from
//  ~0.91 to ~0.77). The flag is preserved for a future Core-Text-based
//  vertical-flow renderer; applying it is deferred. Deliberately do not
//  flip `agg.touched` — we want TypographyApplier to short-circuit when
//  writing-mode is the *only* typography signal, matching pre-Phase-12
//  behaviour.
//

import Foundation

enum WritingModeApplier {
    static func contribute(_ cfg: WritingModeConfig?, into agg: inout TypographyAggregate) {
        guard let v = cfg?.isVertical, v else { return }
        agg.verticalWritingMode = true
        // Intentionally no `agg.touched = true`. See file header.
    }
}
