//
//  UAWidgetView.swift
//  StyleEngine/widgets — lane W2 (wave 20).
//
//  The SwiftUI EXECUTION half of the UA-widget replicas: replays the
//  pure UAWidgetsGeometry paint plan on a SwiftUI Canvas. All geometry
//  and palette decisions live in UAWidgetsGeometry/-Resolve (the
//  byte-parallel pin table); this file only maps ops onto GraphicsContext
//  calls, so nothing here can shift a widget without the pure pins moving
//  first. Reached ONLY through the WPT-capture mount hook in
//  ComponentRenderer (EnvironmentValues.wptCaptureMode) — the dark-stage
//  327 baseline never composes it.
//
//  Wave 22 lane INK (A-RC2): label WIDTHS no longer come from a device
//  face. They come from UAControlFontMetrics, the shared Arial-metric
//  advance table, because that is what Chromium's UA control font is —
//  see that file's header for the ref evidence. SwiftUI therefore only
//  supplies GLYPH SHAPES and the face's ascent; every x position in a
//  label is pinned and byte-identical to the Compose twin.
//

import SwiftUI

struct UAWidgetView: View {
    /// The resolved widget instance (identity + values + accent).
    let spec: UAWidgetsGeometry.Spec

    /// Packed-ARGB → SwiftUI color (sRGB — the plan speaks UInt32 for
    /// cross-native pin parity).
    private static func color(_ argb: UInt32) -> Color {
        Color(.sRGB,
              red: Double((argb >> 16) & 0xFF) / 255.0,
              green: Double((argb >> 8) & 0xFF) / 255.0,
              blue: Double(argb & 0xFF) / 255.0,
              opacity: Double((argb >> 24) & 0xFF) / 255.0)
    }

    /// A-RC2 — the UA control face. Helvetica is the one system face on
    /// iOS that IS Arial-metric (same 2048-upm advance set, verified
    /// glyph-for-glyph against Arial: "input-text" measures 54.844 in
    /// both), so its outlines line up with the pinned advance table the
    /// plan measures with. Guaranteed present on every iOS build, unlike
    /// the harness' bundled Inter, which the ref only applies to PROSE.
    private static let CONTROL_FACE = "Helvetica"
    /// The UA monospace face for textarea content: Courier is iOS' 0.6em
    /// advance face, matching the ref's 8.0px/char at 13.3333px.
    private static let MONO_FACE = "Courier"

    /// The plan's label width probe: the PURE Arial-metric table. No
    /// device face participates in box sizing any more (A-RC2), so the
    /// intrinsic widths are identical on both natives by construction
    /// and the pure pin suites see the same numbers the device does.
    static func measureLabel(_ s: String) -> CGFloat {
        UAControlFontMetrics.advance(s, UAWidgetsGeometry.FONT)
    }

    var body: some View {
        // Pure geometry: intrinsic border-box + ordered op list.
        let size = UAWidgetsGeometry.intrinsicSize(spec: spec, measure: Self.measureLabel)
        let ops = UAWidgetsGeometry.plan(spec: spec, measure: Self.measureLabel)
        // Nothing to paint (input type=hidden) → no box at all.
        if size.w > 0 && size.h > 0 {
            Canvas { ctx, _ in
                // Replay in plan order — ops are authored back-to-front.
                for op in ops { Self.draw(op, in: &ctx) }
            }
            // The widget box IS the component's content size.
            .frame(width: size.w, height: size.h)
        }
    }

    /// One-op executor — GraphicsContext calls only, no decisions.
    private static func draw(_ op: UAWidgetOp, in ctx: inout GraphicsContext) {
        switch op {
        // Plain fill — field interiors, color swatch, chrome slabs.
        case let .fillRect(x, y, w, h, c):
            ctx.fill(Path(CGRect(x: x, y: y, width: w, height: h)), with: .color(color(c)))
        // 1px hairline centered on the half-pixel-inset rect the plan
        // pre-computed (x.5 coords → crisp 1px, same as the ref).
        case let .strokeRect(x, y, w, h, c):
            ctx.stroke(Path(CGRect(x: x, y: y, width: w, height: h)), with: .color(color(c)), lineWidth: 1)
        case let .fillRRect(x, y, w, h, r, c):
            ctx.fill(Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: r), with: .color(color(c)))
        case let .strokeRRect(x, y, w, h, r, c):
            ctx.stroke(Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: r), with: .color(color(c)), lineWidth: 1)
        case let .fillCircle(cx, cy, r, c):
            ctx.fill(Path(ellipseIn: CGRect(x: cx - r, y: cy - r, width: 2 * r, height: 2 * r)), with: .color(color(c)))
        // A-RC4: circle strokes (the radio ring) draw at the shared
        // RING_SW (1.0px) so the ring stays INSIDE the 13px box — see
        // the constant's ref probe in UAWidgetsGeometry.
        case let .strokeCircle(cx, cy, r, c):
            ctx.stroke(Path(ellipseIn: CGRect(x: cx - r, y: cy - r, width: 2 * r, height: 2 * r)), with: .color(color(c)), lineWidth: UAWidgetsGeometry.RING_SW)
        // Check mark / resize grip / menulist chevron arms — round caps
        // soften the joints the same way Chromium's vectors do.
        case let .line(x1, y1, x2, y2, sw, c):
            var p = Path()
            p.move(to: CGPoint(x: x1, y: y1))
            p.addLine(to: CGPoint(x: x2, y: y2))
            ctx.stroke(p, with: .color(color(c)), style: StrokeStyle(lineWidth: sw, lineCap: .round))
        // Label — see drawLabel for the baseline + pinned-advance
        // contract (A-RC2/A-RC4).
        case let .label(text, x, baseline, size, mono, c):
            drawLabel(text: text, x: x, baseline: baseline, size: size,
                      mono: mono, argb: c, in: &ctx)
        }
    }

    /// A-RC2/A-RC4 label execution. Two rules, both pinned:
    ///  1. BASELINE anchoring — the op carries the ref's alphabetic
    ///     baseline, so the top-leading anchor sits at
    ///     `baseline − font.ascender` for THIS face (Helvetica's hhea
    ///     lineGap is 0, so a single-line SwiftUI Text puts its first
    ///     baseline exactly `ascender` below its top). The ascent is
    ///     measured, never assumed, which is what lets the face change
    ///     from Inter (ascender 0.969em) to Helvetica (0.770em) without
    ///     moving a single glyph off the ref's baseline.
    ///  2. PINNED ADVANCES — each character is drawn at the x the shared
    ///     Arial-metric table gives, so a label's glyph grid is identical
    ///     on both natives even though the outlines are not.
    private static func drawLabel(text: String, x: CGFloat, baseline: CGFloat,
                                  size: CGFloat, mono: Bool, argb: UInt32,
                                  in ctx: inout GraphicsContext) {
        // The face this op asks for (mono = textarea content).
        let faceName = mono ? MONO_FACE : CONTROL_FACE
        // Ascent probe. A missing face would silently retune every
        // label, so fall back to the system font of the same size and
        // take ITS ascent rather than a hard-coded number.
        let font = UIFont(name: faceName, size: size) ?? UIFont.systemFont(ofSize: size)
        let top = baseline - font.ascender
        // Walk the string by UTF-16 CODE UNIT, advancing by the PINNED
        // table, not the face. The unit matches Kotlin's `forEach` over
        // Chars, so the glyph grid and the total run length are identical
        // on both natives — including for the non-ASCII labels the wire
        // can carry, where a grapheme walk would advance a surrogate
        // pair once here and twice on Compose.
        var dx = x
        for unit in text.utf16 {
            ctx.draw(
                Text(verbatim: String(decoding: [unit], as: UTF16.self))
                    .font(.custom(faceName, size: size))
                    .foregroundColor(color(argb)),
                at: CGPoint(x: dx, y: top), anchor: .topLeading)
            dx += mono ? UAControlFontMetrics.MONO_ADVANCE_EM * size
                       : UAControlFontMetrics.charAdvance(unit, size)
        }
    }
}
