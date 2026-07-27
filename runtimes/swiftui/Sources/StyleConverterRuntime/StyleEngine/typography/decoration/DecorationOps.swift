//
//  DecorationOps.swift
//  StyleEngine/typography/decoration — applier campaign wave 21,
//  lane TEXTDECOR (B-RC8 decoration style/thickness, B-RC7 unbreakable
//  runs).
//
//  BYTE-PARALLEL TWIN of the Compose runtime's
//  runtimes/compose/src/main/java/com/styleconverter/runtime/typography/
//  DecorationOps.kt — same functions, same constants, same pin table
//  (DecorationOpsTests ↔ DecorationOpsTest). Change one, change both.
//
//  Why this file exists: the extractors already parsed
//  `text-decoration-style` / `text-decoration-thickness` into the
//  typography aggregate, but the owned decoration overlay SILENTLY
//  dropped both — every owned line painted as a SOLID Rectangle at
//  font-derived auto thickness (the exact lossy:false loss this wave
//  exists to kill). This file turns one solid band into the styled op
//  list (rect runs / circle runs) with Chromium-matched rhythm.
//
//  GEOMETRY ORACLE — the algorithm is ported from Blink and pinned
//  against the live wave-21 ref PNGs (tools/wpt/refs/9b5435e55e0b54a6cd
//  09c1c563861eb3c999cef1/white-black-ink-font-lh/css-text-decor/):
//    Blink sources (chromium main, fetched 2026-07-26):
//      • third_party/blink/renderer/platform/graphics/styled_stroke_data.cc
//        — SelectBestDashGap + DashEffectFromStrokeStyle (dash/dot fit)
//      • third_party/blink/renderer/core/paint/decoration_line_painter.cc
//        — DrawLineAsStroke (round-cap inset, mid-row snap, odd-width
//        half-pixel shift), StrokeIsDashed (dotted ≤3px = square dashes)
//    Measured pins (text-decoration-dotted-001.png, 390×600 canvas, red
//    underline bands for thickness 10/20/30 @92px Arial, run x-start 62,
//    run width W=460 — solved from all three pitches simultaneously):
//      t=10: band rows 207-216, dot pitch 19.565 (gap 9.565)
//      t=20: band rows 363-382, dot pitch 40.000 (gap 20.000)
//      t=30: band rows 519-548, dot pitch 61.429 (gap 31.429)
//    text-decoration-dotted-002.png (same layout, W=409):
//      pitches 19.950 / 38.900 / 63.167 — same algorithm, second W. ✓
//    Underline placement (both refs): band TOP − text ink bottom row =
//    5 / 10 / 15 for t = 10 / 20 / 30 → gap below the snapped baseline
//    = max(1, ceil(t/2)), Blink's ComputeUnderlineOffsetAuto gap term
//    (third_party/blink/renderer/core/layout/text_decoration_offset.cc).
//
//  Pure CoreGraphics CGFloat only — no SwiftUI/view code — so the
//  XCTest suite pins every branch without a raster (the
//  DecorationMetrics / GreedyLineBreaker test pattern).
//

// CGFloat only — keeps the file compilable in isolation.
import CoreGraphics
import Foundation

enum DecorationOps {

    /// The five css-text-decor-3 §2.3 line styles, mirrored as an OWN
    /// enum so this file stays dependency-free (the overlay maps
    /// TextDecorationPattern at the call site; the Compose twin maps
    /// TextStyleApplier.TextDecorationStyleType the same way).
    enum LineStyle: Equatable {
        case solid, double, dotted, dashed, wavy
    }

    /// One paint op in text-run-local px space (density-1 harness).
    enum Op: Equatable {
        /// Axis-aligned filled rect — solid bands and dash runs.
        case band(left: CGFloat, top: CGFloat, width: CGFloat, height: CGFloat)
        /// Filled circle — one thick-dotted dot (Blink round cap,
        /// diameter = thickness).
        case dot(centerX: CGFloat, centerY: CGFloat, radius: CGFloat)
    }

    /// Blink kEpsilon (styled_stroke_data.cc): shaves the dot pitch so
    /// rounding along the line never drops the END dot.
    private static let dashFitEpsilon: CGFloat = 0.01

    /// Blink SelectBestDashGap (styled_stroke_data.cc), open-path form:
    /// pick the gap that deviates least from `gapLength` while fitting a
    /// whole number of dashes into `strokeLength`. Pinned against both
    /// dotted refs (file-header table): (460,10,10)→9.565,
    /// (460,20,20)→20, (460,30,30)→31.429, (409,30,30)→33.167.
    static func selectBestDashGap(strokeLength: CGFloat,
                                  dashLength: CGFloat,
                                  gapLength: CGFloat) -> CGFloat {
        // Open path: one virtual trailing gap is available beyond the end.
        let available = strokeLength + gapLength
        // Candidate dash counts bracketing the ideal-density fit.
        let minNumDashes = (available / (dashLength + gapLength)).rounded(.down)
        let maxNumDashes = minNumDashes + 1
        // Open path has one fewer gap than dashes.
        let minNumGaps = minNumDashes - 1
        let maxNumGaps = maxNumDashes - 1
        // The gap each candidate count forces.
        let minGap = (strokeLength - minNumDashes * dashLength) / minNumGaps
        let maxGap = (strokeLength - maxNumDashes * dashLength) / maxNumGaps
        // Keep whichever deviates least from the ideal (never a ≤0 gap).
        return (maxGap <= 0 || abs(minGap - gapLength) < abs(maxGap - gapLength))
            ? minGap : maxGap
    }

    /// Blink GetSnappedPointsForTextLine + DrawLineAsStroke: the stroked
    /// centerline row = floor(top + max(h/2, 0.5)), shifted +0.5 when
    /// the ROUNDED thickness is odd so an odd-width stroke aligns to the
    /// pixel grid ("For odd widths, shift the line down by 0.5").
    private static func snappedMidY(top: CGFloat,
                                    thicknessPx: CGFloat,
                                    roundedThickness: Int) -> CGFloat {
        // Integer mid row like Blink's gfx::Point snap.
        let mid = (top + max(thicknessPx / 2, 0.5)).rounded(.down)
        // Odd-width pixel-grid alignment.
        return roundedThickness % 2 == 1 ? mid + 0.5 : mid
    }

    /// Expand ONE solid band into the styled op list. SOLID emits the
    /// band itself byte-for-byte (rect(left, top, width, thickness) —
    /// the legacy Rectangle frame, so auto-path captures cannot move).
    /// DOUBLE and WAVY are NOT implemented yet — they fall back to the
    /// same solid band and the CALLER must surface `isLossyFallback`
    /// (no silent fallthroughs; neither style appears in the wave-21
    /// gate corpus).
    static func styleOps(left: CGFloat,
                         top: CGFloat,
                         width: CGFloat,
                         thicknessPx: CGFloat,
                         style: LineStyle) -> [Op] {
        switch style {
        // Chromium round-cap dot runs (≤3px: square-dash runs).
        case .dotted: return dottedOps(left: left, top: top, width: width, thicknessPx: thicknessPx)
        // Chromium butt-cap dash runs.
        case .dashed: return dashedOps(left: left, top: top, width: width, thicknessPx: thicknessPx)
        // solid, plus the DOCUMENTED double/wavy solid fallback.
        case .solid, .double, .wavy:
            return [.band(left: left, top: top, width: width, height: thicknessPx)]
        }
    }

    /// True when `styleOps` paints a lossy solid stand-in — callers log it.
    static func isLossyFallback(_ style: LineStyle) -> Bool {
        style == .double || style == .wavy
    }

    /// `text-decoration-style: dotted` — Blink DashEffectFromStrokeStyle.
    /// Thickness > 3px: zero-length dashes + round caps = CIRCLES of
    /// diameter = thickness; the line is inset t/2 per end so caps don't
    /// overhang (ref: dot LEFT edges sit exactly on the run start x=62
    /// for all three thicknesses). Thickness ≤ 3px (StrokeIsDashed):
    /// square t-on/t-off dashes, butt caps, no gap fitting.
    static func dottedOps(left: CGFloat, top: CGFloat,
                          width: CGFloat, thicknessPx: CGFloat) -> [Op] {
        // Zero inked extent → nothing (browser paints nothing).
        guard width > 0 else { return [] }
        // Blink DrawLineAsStroke: pattern lengths use the ROUNDED int
        // thickness (roundf), floored at 1 so thin lines still pattern.
        let t = max(1, Int(thicknessPx.rounded()))
        // Stroke centerline row (odd widths ride +0.5 — header helper).
        let midY = snappedMidY(top: top, thicknessPx: thicknessPx, roundedThickness: t)
        // ≤3px dotted is drawn as square dashes (StrokeIsDashed) with
        // dash = gap = t and NO SelectBestDashGap (only kDashedStroke
        // adjusts its gap in Blink).
        if t <= 3 {
            return dashRunOps(left: left, midY: midY, width: width,
                              strokeHeightPx: thicknessPx,
                              dash: CGFloat(t), idealGap: CGFloat(t), fitGap: false)
        }
        // Round caps extend t/2 beyond each endpoint → Blink insets the
        // stroked segment; the DASH PHASE then runs over width − t.
        let insetPathLength = width - CGFloat(t)
        // Blink: path_length (the PRE-inset width) too short for 2 dots
        // → a single dot at the inset start.
        if width < 2 * CGFloat(t) {
            return [.dot(centerX: left + CGFloat(t) / 2, centerY: midY, radius: thicknessPx / 2)]
        }
        // Chromium-fit gap (pinned: 9.565 / 20.0 / 31.429 at W=460).
        let gap = selectBestDashGap(strokeLength: width,
                                    dashLength: CGFloat(t), gapLength: CGFloat(t))
        // Dash interval {0, gap + t − ε}: dot centers every gap+t−ε.
        let pitch = gap + CGFloat(t) - dashFitEpsilon
        // One circle per pattern point that lands on the inset segment
        // (the ε above is exactly what keeps the END dot inside). The
        // FIRST dot is unconditional (repeat-while): at width == 2t
        // exactly, SelectBestDashGap divides by zero gaps → gap = +Inf
        // (Blink hits the same IEEE Inf) and 0·Inf = NaN would skip the
        // k=0 test — but Skia always paints the phase-0 dot of a dash
        // interval, so Blink renders ONE dot there, not zero (wave-21
        // skeptic pin; the Kotlin twin's do-while is byte-parallel).
        var ops: [Op] = []
        var k: CGFloat = 0
        repeat {
            // Center = inset start + k pitches; radius = t/2 (round cap).
            // k=0 must not touch pitch at all: 0·Inf = NaN, and the
            // phase-0 dot sits exactly at the inset start regardless.
            let dx: CGFloat = k == 0 ? 0 : k * pitch
            ops.append(.dot(centerX: left + CGFloat(t) / 2 + dx,
                            centerY: midY, radius: thicknessPx / 2))
            k += 1
        } while k * pitch <= insetPathLength
        return ops
    }

    /// `text-decoration-style: dashed` — Blink DashEffectFromStrokeStyle
    /// kDashedStroke: dash = t × (t≥3 ? 2 : 3), ideal gap = dash ×
    /// (t≥3 ? 1 : 2) (thin lines need longer dashes/gaps to stay
    /// legible — Blink's DashLengthRatio/DashGapRatio), gap then
    /// refitted via SelectBestDashGap. Butt caps, no endpoint inset.
    static func dashedOps(left: CGFloat, top: CGFloat,
                          width: CGFloat, thicknessPx: CGFloat) -> [Op] {
        // Zero inked extent → nothing.
        guard width > 0 else { return [] }
        // Rounded pattern thickness, 1px floor (as in dottedOps).
        let t = max(1, Int(thicknessPx.rounded()))
        // Stroke centerline row.
        let midY = snappedMidY(top: top, thicknessPx: thicknessPx, roundedThickness: t)
        // Blink DashLengthRatio / DashGapRatio.
        let dash = CGFloat(t) * CGFloat(t >= 3 ? 2 : 3)
        let gap = dash * CGFloat(t >= 3 ? 1 : 2)
        // Shared dash-run emitter (also serves thin dotted above).
        return dashRunOps(left: left, midY: midY, width: width,
                          strokeHeightPx: thicknessPx,
                          dash: dash, idealGap: gap, fitGap: true)
    }

    /// Butt-cap dash run — the shared tail of Blink's StrokeIsDashed
    /// branch. `fitGap` mirrors Blink: only kDashedStroke refits its gap.
    private static func dashRunOps(left: CGFloat, midY: CGFloat,
                                   width: CGFloat, strokeHeightPx: CGFloat,
                                   dash: CGFloat, idealGap: CGFloat,
                                   fitGap: Bool) -> [Op] {
        // A stroke of width h centered on midY covers [midY−h/2, +h/2].
        let bandTop = midY - strokeHeightPx / 2
        // Blink: "No space for dashes" (L ≤ 2·dash) → nullopt → the
        // stroke paints SOLID. Not a silent loss: it is Blink behavior.
        if width <= dash * 2 {
            return [.band(left: left, top: bandTop, width: width, height: strokeHeightPx)]
        }
        // Exactly-two-dashes window: both dash and gap scale down
        // proportionally so dash+gap+dash spans the line exactly.
        let twoDashesWithGap = 2 * dash + idealGap
        if width <= twoDashesWithGap {
            // Proportional multiplier (Blink: length / two_dashes_...).
            let m = width / twoDashesWithGap
            // First dash at the start, second flush with the end.
            return [
                .band(left: left, top: bandTop, width: dash * m, height: strokeHeightPx),
                .band(left: left + (dash + idealGap) * m, top: bandTop,
                      width: dash * m, height: strokeHeightPx),
            ]
        }
        // Fitted (dashed) or fixed (thin dotted) gap.
        let g = fitGap
            ? selectBestDashGap(strokeLength: width, dashLength: dash, gapLength: idealGap)
            : idealGap
        // Emit dashes from the line start; the LAST dash clips at the
        // line end exactly like Skia clips a dash interval mid-pattern.
        var ops: [Op] = []
        var x: CGFloat = 0
        while x < width {
            // Clip the final partial dash to the remaining extent.
            ops.append(.band(left: left + x, top: bandTop,
                             width: min(dash, width - x), height: strokeHeightPx))
            x += dash + g
        }
        return ops
    }

    // MARK: - explicit-thickness placement (css-text-decor-4 §2.4)

    /// Blink's gap between baseline and underline TOP when a thickness
    /// is known: max(1, ceil(T/2)) (text_decoration_offset.cc
    /// ComputeUnderlineOffsetAuto). Ref-pinned: band top − ink bottom =
    /// 5 / 10 / 15 px for T = 10 / 20 / 30 in BOTH dotted refs.
    /// (The AUTO-thickness path keeps DecorationMetrics' wave-5 capture
    /// rows — its 22px oracle and the 327-pair baseline pin them; this
    /// rule fires only when the IR declares `text-decoration-thickness`.)
    static func explicitUnderlineGapPx(thicknessPx: CGFloat) -> CGFloat {
        max(1, (thicknessPx / 2).rounded(.up))
    }

    /// Underline TOP for an EXPLICIT thickness, relative to the LINE-BOX
    /// TOP (the overlay's per-line coordinate origin — same anchor as
    /// DecorationMetrics.underlineTop): snapped baseline (= rounded
    /// ascent, the row TextKit lays glyphs on) + the Blink gap above.
    static func explicitUnderlineTop(ascentPx: CGFloat, thicknessPx: CGFloat) -> CGFloat {
        ascentPx.rounded() + explicitUnderlineGapPx(thicknessPx: thicknessPx)
    }

    /// Line-through TOP for an EXPLICIT thickness: the strike CENTER
    /// stays where the auto rule put it (DecorationMetrics.lineThroughTop
    /// + autoThickness/2 — the capture-measured mid-row is thickness-
    /// independent, mirroring the Compose twin's center-anchored rule);
    /// the explicit band straddles that center (±T/2) then snaps.
    static func explicitLineThroughTop(ascentPx: CGFloat,
                                       fontSizePx: CGFloat,
                                       thicknessPx: CGFloat) -> CGFloat {
        // The auto band's center row (top + half the auto thickness).
        let center = DecorationMetrics.lineThroughTop(ascentPx: ascentPx, fontSizePx: fontSizePx)
            + DecorationMetrics.autoThickness(fontSizePx: fontSizePx) / 2
        // Straddle and snap to a whole row like Blink's painter.
        return (center - thicknessPx / 2).rounded()
    }

    /// Overline TOP for an EXPLICIT thickness: the band's BOTTOM stays
    /// flush with the line-box top (the auto rule's anchor, ref'd in
    /// DecorationMetrics.overlineTop), so its top is one EXPLICIT
    /// thickness above — hanging into the space above like Chromium's
    /// ink-overflow paint.
    static func explicitOverlineTop(thicknessPx: CGFloat) -> CGFloat {
        -thicknessPx
    }

    // MARK: - unbreakable-run detection (B-RC7, UAX #14 approximation)

    /// True when `text` offers at least one soft-wrap opportunity — the
    /// UAX #14 approximation this wave prescribes: whitespace (class
    /// BA/SP/BK…), explicit break enablers (soft hyphen U+00AD, ZWSP
    /// U+200B), break-AFTER punctuation (hyphen-minus, U+2010 hyphen,
    /// en/em dashes — UAX #14 classes HY/BA), or any ideographic char
    /// (class ID breaks between any pair — CJK, kana, Hangul, fullwidth
    /// forms). 'fooשלוםbaz' (dotted-001) and 'foobarbaz' (dotted-002)
    /// contain NONE of these → unbreakable → the run must lay out on
    /// one overflowing line like the Chromium ref (which keeps each on
    /// ONE line at 92px inside a 390px canvas), instead of the engines'
    /// emergency mid-run wrap.
    static func hasSoftWrapOpportunity(_ text: String) -> Bool {
        text.unicodeScalars.contains { scalar in
            // Whitespace of any flavor (space, tab, newline; NBSP is in
            // no whitespace set here — correct: NBSP forbids breaks).
            // NEL U+0085 (UAX #14 class BK) IS in Swift's White_Space —
            // the Kotlin twin carves it IN explicitly because Java's
            // whitespace sets exclude it (wave-21 skeptic parity pin).
            (scalar.properties.isWhitespace && scalar.value != 0x00A0) ||
                // Soft hyphen / zero-width space: explicit opportunities.
                scalar.value == 0x00AD || scalar.value == 0x200B ||
                // Break-after hyphens and dashes (UAX #14 HY/BA).
                scalar.value == 0x2D || scalar.value == 0x2010 ||
                scalar.value == 0x2013 || scalar.value == 0x2014 ||
                // Ideographic (UAX #14 ID): breaks between any two.
                isIdeographic(scalar.value)
        }
    }

    /// UAX #14 class-ID block approximation (see caller). Twin note:
    /// the Compose twin tests Kotlin Char (UTF-16) — identical for the
    /// BMP blocks listed here, which is the whole set.
    private static func isIdeographic(_ c: UInt32) -> Bool {
        // CJK radicals … unified ideographs (incl. kana + CJK punct).
        (0x2E80...0x9FFF).contains(c) ||
            // Hangul syllables.
            (0xAC00...0xD7AF).contains(c) ||
            // CJK compatibility ideographs.
            (0xF900...0xFAFF).contains(c) ||
            // Full-width / half-width forms.
            (0xFF00...0xFFEF).contains(c)
    }
}
