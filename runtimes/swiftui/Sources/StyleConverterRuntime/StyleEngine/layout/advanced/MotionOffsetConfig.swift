//
//  MotionOffsetConfig.swift
//  StyleEngine/layout/advanced — fidelity wave 2.
//
//  CSS Motion Path (motion-1) subset: offset-path / offset-position /
//  offset-rotate / offset-distance. The renderable subset is ray() —
//  the only shape the fidelity fixtures exercise (Layout_C14). path()/
//  basic-shape paths remain recorded-but-unapplied TODOs.
//

import Foundation
import CoreGraphics

/// One axis of a CSS `<position>` — keyword, absolute px, or percent of
/// the containing block. Percentages resolve at apply time against the
/// harness containing-block approximation (see MotionOffsetApplier).
enum MotionAxis: Equatable {
    /// `left`/`top` → 0% of the containing block on that axis.
    case start
    /// `center` → 50%.
    case center
    /// `right`/`bottom` → 100%.
    case end
    /// Absolute pixels from the containing-block origin.
    case px(CGFloat)
    /// Percentage (0–100) of the containing-block extent.
    case percent(CGFloat)
}

/// `offset-path` value subset.
enum MotionPath: Equatable {
    /// `ray(<angle>)` — a ray from the offset starting position at the
    /// given bearing (motion-1 §3.2: 0deg points up, clockwise-positive).
    case ray(deg: CGFloat)
}

/// `offset-rotate` value (motion-1 §3.5).
enum MotionRotate: Equatable {
    /// `auto` — element's inline axis follows the path direction.
    case auto
    /// `reverse` — auto + 180deg.
    case reverse
    /// Fixed `<angle>` in degrees, path direction ignored.
    case fixed(deg: CGFloat)
}

/// Aggregated motion-path state for one element. Nil path = property
/// family absent or unsupported shape → applier short-circuits.
struct MotionOffsetConfig: Equatable {
    /// The offset path; only ray() renders today.
    var path: MotionPath? = nil
    /// `offset-position` — the ray's starting position in the containing
    /// block. Motion-1 §3.3: `normal` (absent) means 50% 50%.
    var positionX: MotionAxis = .center
    var positionY: MotionAxis = .center
    /// `offset-distance` in px along the path (percent unsupported —
    /// a ray's 100% reference needs containing-block geometry).
    var distancePx: CGFloat = 0
    /// `offset-rotate` — element orientation on the path.
    var rotate: MotionRotate = .auto
}
