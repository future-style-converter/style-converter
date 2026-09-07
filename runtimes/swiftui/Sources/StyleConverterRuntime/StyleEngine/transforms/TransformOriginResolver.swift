//
//  TransformOriginResolver.swift
//  StyleEngine/transforms — retro R5 (audit A11#14, iOS half).
//
//  css-transforms-1 §4 "The transform-origin Property" (drafts.csswg.org
//  Editor's Draft numbering, the repo standard since retro P2d; the W3C
//  TR/CR text numbers the same section §5 — retro F2 re-cited this file
//  from that TR number), the two-value grammar:
//      [ left | center | right | <length-percentage> ]
//      [ top | center | bottom | <length-percentage> ]
//    | [ [ center | left | right ] && [ center | top | bottom ] ] <length>?
//  The `&&` production lets two KEYWORDS appear in either order: the
//  horizontal keyword always names the x offset and the vertical keyword
//  the y offset (only when "no value is a keyword, or the only used
//  keyword is center" does slot order alone decide). The converter
//  (TransformOriginPropertyParser.kt) stores the tokens POSITIONALLY —
//  `top right` → x:TOP, y:RIGHT — and this extractor read them
//  positionally too, so `top right` rotated about the BOTTOM-LEFT corner
//  and `bottom center` about the right-middle.
//
//  MEASURED (A11 run properties__transforms__transform-origin: 120×80 box
//  at (16,16), rotate(15deg), 390×112 capture; ink bbox [x0,y0,w,h]):
//      Origin_TopRight      web [0,0,136,94]    iOS [16,19,137,93]
//      Origin_BottomCenter  web [18,3,137,109]  iOS [9,0,138,95]
//  Rotating the box about the top-right corner reproduces the web bbox
//  exactly; about the bottom-left corner it reproduces the iOS one
//  (TransformOriginKeywordTests carries the arithmetic). Android shared
//  the positional read; lane R1 applies this same rule there.
//

import SwiftUI

/// Pure resolution of the two `transform-origin` wire slots into a
/// TransformOriginValue. No views — XCTest pins it directly.
enum TransformOriginResolver {

    /// Which axis a token names. `neutral` = center / percent / length,
    /// which §4 accepts in either slot.
    enum AxisKind: Equatable { case horizontal, vertical, neutral }

    /// One decoded slot: the axis its keyword names, the 0…1 fraction it
    /// resolves to (keyword / percent), and the px override of a <length>.
    struct Axis: Equatable {
        var kind: AxisKind = .neutral
        var frac: CGFloat = 0.5
        var px: CGFloat? = nil
        /// True only for a token the wire typed `keyword` (retro round-2
        /// lane F2, skeptic S4 defect 0). §4's `&&` production is
        /// keyword-only, so THIS flag — not the resolved fraction — is what
        /// separates the valid `top center` from the invalid `top 50%`: both
        /// decode to (neutral, 0.5, nil), and the converter emits them as
        /// distinct wire shapes ({type:keyword,value:CENTER} vs
        /// {type:percentage,percentage:50}). The Compose twin carries the
        /// same flag (TransformOriginKeywords.Component.keyword).
        var isKeyword: Bool = false
        /// The implicit `center` the grammar supplies for a vacated slot —
        /// an OUTPUT of `resolve`, never a decoded token, so its flag is
        /// never consulted.
        static let center = Axis()
    }

    /// Decode one slot. Wire shapes (TransformOriginProperty.OriginValue):
    ///   { "type": "keyword",    "value": "LEFT|CENTER|RIGHT|TOP|BOTTOM" }
    ///   { "type": "percentage", "percentage": N }
    ///   { "type": "length",     "px": N }
    static func axis(_ v: IRValue?) -> Axis {
        guard let v = v, case .object(let o) = v else { return .center }
        var a = Axis()
        switch o["type"]?.stringValue {
        case "keyword":
            // Only the keyword arm sets the flag: a percentage / length /
            // unknown type — and an ABSENT slot (`.center` above) — stays
            // a non-keyword, exactly as Compose's `component()` flags
            // anything its `keyword()` reader rejects with `keyword = false`.
            a.isKeyword = true
            switch o["value"]?.stringValue?.uppercased() {
            // Horizontal keywords name the x offset in whichever slot.
            case "LEFT":   a.kind = .horizontal; a.frac = 0
            case "RIGHT":  a.kind = .horizontal; a.frac = 1
            // Vertical keywords name the y offset in whichever slot.
            case "TOP":    a.kind = .vertical;   a.frac = 0
            case "BOTTOM": a.kind = .vertical;   a.frac = 1
            // `center` (and anything unknown) is legal on both axes.
            default:       a.kind = .neutral;    a.frac = 0.5
            }
        case "percentage":
            // 50 → 0.5; the fraction resolves against the reference box.
            if let p = o["percentage"]?.doubleValue { a.frac = CGFloat(p / 100.0) }
        case "length":
            // Absolute px ride separately — the applier anchors by pixels.
            if let p = o["px"]?.doubleValue { a.px = CGFloat(p) }
        default: break
        }
        return a
    }

    /// Resolve the (first, second) wire slots to (x, y) per §4.
    /// Positional unless a keyword sits on the axis it does not name; then
    /// that keyword moves to its own axis and the vacated slot takes the
    /// other token — or `center` when that token was not the other axis's
    /// keyword (the converter duplicates a lone `top` into BOTH slots, and
    /// §4 makes the omitted second value `center`). The same rule lane R1
    /// applies on Compose (TransformOriginKeywords.kt), so the natives agree.
    static func resolve(first: IRValue?, second: IRValue?) -> TransformOriginValue {
        let a = axis(first), b = axis(second)
        var x = a, y = b
        if a.kind == .vertical || b.kind == .horizontal {
            // Any NON-KEYWORD token beside a swapped keyword (`top 10px`,
            // `30% right`, and `top 50%` / `50% right` too) is outside the
            // §4 grammar — the two-value form is
            // `[left|center|right|<lp>] [top|center|bottom|<lp>]` and only
            // the KEYWORD pair may reorder — so Chromium drops the whole
            // declaration and the computed value is the initial `50% 50%`.
            // Do the same (both natives; web replays the string and gets
            // this from Chromium itself) and leave a breadcrumb rather than
            // guess which axis the number meant. Retro F2 (skeptic S4
            // defect 0): this gate used to test `!= Axis.center`, which read
            // a `50%` as the implicit centre keyword and resolved `top 50%`
            // to (0.5, 0) and `50% right` to (1, 0.5) while Compose
            // (TransformOriginKeywords.dropsDeclaration, `keyword = false`)
            // gave the initial (0.5, 0.5) for both. The wire DOES distinguish
            // them (CENTER keyword vs percentage 50), so the flag is the gate.
            let aNumeric = !a.isKeyword
            let bNumeric = !b.isKeyword
            if (a.kind == .vertical && bNumeric) || (b.kind == .horizontal && aNumeric) {
                PropertyTracker.logOnce(
                    key: "transform-origin-grammar",
                    message: "transform-origin: keyword + <length-percentage> in the wrong slot order is not in the css-transforms-1 §4 grammar — declaration ignored, origin stays at the initial 50% 50%")
                x = Axis.center
                y = Axis.center
            } else {
                // `&&` form: the horizontal word is x, the vertical word is y.
                x = b.kind == .horizontal ? b : (a.kind == .vertical ? Axis.center : a)
                y = a.kind == .vertical ? a : (b.kind == .horizontal ? Axis.center : b)
            }
        }
        var origin = TransformOriginValue()
        // UnitPoint fractions are what the SwiftUI anchors consume; px
        // offsets ride alongside for the GeometryReader path.
        origin.unit = UnitPoint(x: x.frac, y: y.frac)
        origin.xPx = x.px
        origin.yPx = y.px
        return origin
    }
}
