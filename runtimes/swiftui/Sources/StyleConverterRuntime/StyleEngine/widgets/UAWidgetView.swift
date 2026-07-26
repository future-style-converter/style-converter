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

    /// The plan's label width probe: Inter (the harness face the runtime's
    /// real-text path uses — ComponentRenderer's `.custom("Inter")`) at
    /// the UA control size, measured through Core Text so widths are
    /// stable off the main thread. Falls back to the system font when the
    /// host app didn't register Inter (unit tests) — widths shift a few
    /// px there, which the pure pin tests avoid by injecting fake probes.
    static func measureLabel(_ s: String) -> CGFloat {
        // UIFont resolves registered custom faces by PostScript name.
        let font = UIFont(name: "Inter", size: UAWidgetsGeometry.FONT)
            ?? UIFont.systemFont(ofSize: UAWidgetsGeometry.FONT)
        // NSAttributedString measurement — same advance math CoreText
        // uses when the Canvas resolves the drawn Text below.
        return (s as NSString).size(withAttributes: [.font: font]).width.rounded(.up)
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
        // fix 4: circle strokes (the radio ring) draw at the shared
        // RING_SW (1.4px) — an AA'd 1px circle sampled ~#CCC where the
        // ref ring samples ~#9A (see the constant's ref probe).
        case let .strokeCircle(cx, cy, r, c):
            ctx.stroke(Path(ellipseIn: CGRect(x: cx - r, y: cy - r, width: 2 * r, height: 2 * r)), with: .color(color(c)), lineWidth: UAWidgetsGeometry.RING_SW)
        // Check mark / resize grip / menulist chevron arms — round caps
        // soften the joints the same way Chromium's vectors do.
        case let .line(x1, y1, x2, y2, sw, c):
            var p = Path()
            p.move(to: CGPoint(x: x1, y: y1))
            p.addLine(to: CGPoint(x: x2, y: y2))
            ctx.stroke(p, with: .color(color(c)), style: StrokeStyle(lineWidth: sw, lineCap: .round))
        // Label — Inter at FONT px, top-left anchored per the plan (the
        // runtime's real-text face, matching the ref's pinned stack).
        case let .label(text, x, y, size, c):
            ctx.draw(
                Text(verbatim: text)
                    .font(.custom("Inter", size: size))
                    .foregroundColor(color(c)),
                at: CGPoint(x: x, y: y), anchor: .topLeading)
        }
    }
}
