//
//  SepiaMatrix.swift
//  StyleEngine/effects/filter
//
//  The CSS sepia() colour matrix, factored into operations SwiftUI can
//  perform. Split out of FilterApplier for two reasons: that file was
//  already at its ~200-line budget, and — as with `brightnessFactor` — the
//  arithmetic that was WRONG deserves to be unit-testable without rendering.
//
//  ## The spec
//
//  filter-effects-1 §6.1 sepia() defines sepia(amount) as a colour matrix
//  interpolated toward the identity by the amount:
//
//      M(a) = I + a · (S - I)         S = the full-sepia matrix below
//
//  ## What was wrong before
//
//  sepia was approximated by eye:
//
//      v.saturation(1 - pct/200)
//       .colorMultiply(Color(red: 1.0, green: 0.9, blue: 0.7)
//                      .opacity(pct/100))
//
//  Multiplying by a colour carrying `opacity(0.8)` darkens everything,
//  which was most of the error. Measured on `sepia(80%)` over #3498db =
//  (52,152,219): CSS says (153,158,143), Android and web both render
//  exactly that, iOS rendered (74,110,113) — RGB distance 97, and COOL
//  where sepia must be warm.
//
//  ## Why not a real colour matrix
//
//  This is a CHOICE, not a platform limitation, and the distinction matters
//  because the alternatives exist:
//
//    · `View._colorMatrix(_:)` is SwiftUI SPI (underscored). It would be
//      exact. Nothing else in runtimes/swiftui/ depends on underscored
//      SwiftUI API and this file will not be the first — SPI can change or
//      vanish between OS releases with no deprecation path.
//    · `GraphicsContext.Filter.colorMatrix` is PUBLIC (iOS 15) but lives
//      inside `Canvas`, so using it means rasterising the subtree into a
//      Canvas — losing text crispness and live subviews.
//    · `.colorEffect` (a Metal shader) is iOS 17+, above this package's
//      iOS 16 floor. It is also not buildable here: the Metal toolchain is
//      a separately-downloaded Xcode component and is absent on this
//      machine, so adding a `.metal` source would break every iOS build
//      including CI.
//
//  The factorisation below stays inside public, non-rasterising API, and
//  lands within a quantisation step. That is the trade being made.
//
//  ## The factorisation
//
//  S is very nearly (but NOT exactly) rank-1 — each row is close to a
//  scalar multiple of the first:
//
//      row2 / row1 = 0.8880, 0.8921, 0.8889     (not equal → rank ≥ 2)
//      row3 / row1 = 0.6921, 0.6944, 0.6931
//
//  Approximating S ≈ t · wᵀ gives
//
//      M(a)·c ≈ (1-a)·c + a · (w·c) · t
//
//  i.e. "blend the original with a TINTED LUMINANCE". The luminance uses
//  sepia's OWN weights w = (0.393, 0.769, 0.189), which is the difficulty:
//  `.grayscale(1)` sums with Rec.709 weights, not these.
//
//  The trick is that a channel REWEIGHT before the grayscale changes the
//  effective basis:
//
//      grayscale(colorMultiply(c, k)) = Σ w709ᵢ · kᵢ · cᵢ
//
//  so kᵢ = wᵢ / w709ᵢ makes `.grayscale` sum in sepia's basis exactly.
//
//  `t` is fitted by LEAST SQUARES (tᵢ = rowᵢ·w / w·w) rather than read off
//  one column, because a column choice is arbitrary — the three columns
//  disagree (0.8880 / 0.8921 / 0.8889 for row 2). The fit halves the
//  worst-case residual, 0.83 → 0.42 per 255.
//
//  It does NOT change what this fixture renders, and saying otherwise
//  would be exactly the kind of predicted-number-quoted-as-measured this
//  file is at pains to avoid. On paper the fit puts the fixture's green at
//  157.81 (→158) against col-0's 157.36 (→157), but MEASURED the render is
//  157 either way, because each additive layer is quantised to 8 bits
//  before the sum:
//
//      round(0.2 × 152) + round(0.8 × round(159.26)) = 30 + 127 = 157
//
//  So the compositing quantisation, not the tint, is what costs that LSB.
//  The fit is still the right choice — it is principled rather than
//  arbitrary, and its smaller residual holds across the whole gamut — but
//  its benefit here is analytic, not visible.
//
//  ## The two platform facts this rests on
//
//  Both are undocumented, so both are pinned by FIXTURES rather than by
//  assertion — `fixtures/properties/effects/filter-grayscale-basis.json`
//  and `filter-sepia-amounts.json` exist so a reader can re-measure them:
//
//  1. `.grayscale(1.0)` is Rec.709 in GAMMA-encoded space. The grayscale
//     fixture puts pure primaries through `filter: grayscale(100%)`, which
//     reads the weights off directly: R→54, G→182, B→18 (and a mixed
//     colour →75). Rec.601 would give 76/150/29/79 and a linear-space sum
//     127/220/76/82, so the fixture discriminates between all three rather
//     than merely agreeing with one.
//  2. `.colorMultiply` carries EXTENDED-RANGE components through the chain.
//     The reweight drives blue to 2.618× — for pure blue at full sepia,
//     well past 1.0 — so a clamp before the grayscale would break the sum.
//     The sepia fixture includes that case for exactly this reason. The
//     brightness case in FilterApplier relies on the same property.
//
//  ## Accuracy
//
//  Not bit-exact, and the header of FilterApplier says so too. Swept over
//  the sRGB cube at 21 amounts the worst per-channel MATRIX error is
//  0.42/255. Two further sources sit outside that bound and are the reason
//  the measured figure below is 1 rather than 0:
//
//    · per-layer 8-bit quantisation in the additive composite (above), and
//    · precision lost when the reweight drives a value out of gamut and it
//      clamps — a shared, accepted class; brightness shows the same
//      residual on the same pixels.
//
//  MEASURED end to end on fixtures/properties/effects/filter-sepia-amounts
//  .json, all three runtimes, worst channel distance from the CSS value:
//
//      amount 0 · 50% · 80% · 100% · extended-range · white · over-100%
//      · translucent          → iOS max 1, Android 0, web 0
//
//  Five of the eight cases are exact on iOS; three are off by one. Before
//  this change the fixture case was off by 97.
//

import SwiftUI

/// Pure arithmetic for CSS `sepia()`. No view types — everything here is
/// testable without rendering.
enum SepiaMatrix {

    /// The full-sepia matrix from filter-effects-1 §6.1 sepia(), row-major.
    /// Verbatim so a reader can diff it against the spec text.
    static let full: [[Double]] = [
        [0.393, 0.769, 0.189],
        [0.349, 0.686, 0.168],
        [0.272, 0.534, 0.131],
    ]

    /// Rec.709 luminance weights — the basis `.grayscale(1)` sums in.
    static let rec709: [Double] = [0.2126, 0.7152, 0.0722]

    /// Sepia's own luminance weights: the first row of `full`.
    static var weights: [Double] { full[0] }

    /// Per-channel gain converting the Rec.709 basis into sepia's.
    /// ≈ (1.8485, 1.0752, 2.6177). Components exceed 1, which is why the
    /// extended-range property of `.colorMultiply` is load-bearing.
    static let reweight: [Double] = zip(full[0], rec709).map { $0 / $1 }

    /// The rank-1 tint, least-squares fitted: tᵢ = (rowᵢ·w) / (w·w).
    /// ≈ (1, 0.8911, 0.6939). See the header for why not a column ratio.
    static let tint: [Double] = {
        let w = full[0]
        let ww = zip(w, w).reduce(0.0) { $0 + $1.0 * $1.1 }
        return full.map { row in zip(row, w).reduce(0.0) { $0 + $1.0 * $1.1 } / ww }
    }()

    /// CSS amount (a percentage from the parser) → clamped 0…1 factor.
    /// filter-effects-1 §6.1 sepia(): amounts over 1 are "interpreted as 1" — unlike
    /// brightness, which is deliberately unbounded. Negative is invalid CSS
    /// and collapses to the identity rather than inverting.
    static func amount(_ pct: Double) -> Double { max(0, min(1, pct / 100)) }

    /// The reweight as a Color. `.sRGB` with components > 1 is an
    /// extended-range colour, which is exactly what the sum needs.
    static var reweightColor: Color {
        Color(.sRGB, red: reweight[0], green: reweight[1], blue: reweight[2], opacity: 1)
    }

    /// The tint as a Color. All components ≤ 1, so no extended range here.
    static var tintColor: Color {
        Color(.sRGB, red: tint[0], green: tint[1], blue: tint[2], opacity: 1)
    }

    /// Reference implementation of the SPEC matrix, used by tests as the
    /// thing the factorisation must agree with. Deliberately written as the
    /// literal interpolation `I + a(S - I)` rather than reusing the
    /// factorisation, so comparing them is a real check and not a tautology.
    static func applySpec(_ c: [Double], amount a: Double) -> [Double] {
        (0..<3).map { i in
            (0..<3).reduce(0.0) { acc, j in
                let identity = (i == j) ? 1.0 : 0.0
                return acc + (identity + a * (full[i][j] - identity)) * c[j]
            }
        }
    }

    /// What the factorisation computes: (1-a)·c + a·(w·c)·t.
    static func applyFactored(_ c: [Double], amount a: Double) -> [Double] {
        let luma = zip(weights, c).reduce(0.0) { $0 + $1.0 * $1.1 }
        return (0..<3).map { (1 - a) * c[$0] + a * luma * tint[$0] }
    }
}
