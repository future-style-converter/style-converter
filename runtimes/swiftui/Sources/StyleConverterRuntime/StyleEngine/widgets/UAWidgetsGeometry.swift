//
//  UAWidgetsGeometry.swift
//  StyleEngine/widgets — lane W2 (wave 20).
//
//  The PURE half of the UA-widget replica painter: Chromium-lookalike
//  form-control geometry as a platform-neutral paint plan. This file is
//  the CROSS-PLATFORM PIN TABLE: its Kotlin twin (runtimes/compose
//  .../widgets/UAWidgetsGeometry.kt) is byte-parallel — same constants,
//  same op order, same `pin` string encoding — and both unit suites
//  assert the IDENTICAL expected strings, so the two natives can never
//  drift apart silently.
//
//  Every number is MEASURED from the corpus-v4.1 browser-ref renders
//  (tools/wpt/refs/<hash>/white-black-ink-font-lh/css-ui/*.png, probed
//  at pixel level — appearance-auto-001 for the unchecked row,
//  accent-color-visited for the checked checkbox): Chromium's
//  light-scheme UA controls paint chrome #EFEFEF, border #767676 and
//  accent #0075FF. WPT-capture-mode-gated at the mount hooks — the
//  dark-stage 327 baseline never routes through this file.
//

import Foundation

/// Palette + geometry constants (see file header for the measurement
/// provenance) — same literals as the Kotlin twin, entry for entry.
enum UAWidgetsGeometry {
    // ── Palette (Chromium light-scheme UA controls, ref-probed) ────────
    static let CHROME: UInt32 = 0xFFEFEFEF  // button/track chrome fill
    static let BORDER: UInt32 = 0xFF767676  // hairline control border
    static let ACCENT: UInt32 = 0xFF0075FF  // default accent-color
    static let WHITE: UInt32 = 0xFFFFFFFF   // field interiors + check mark
    static let BLACK: UInt32 = 0xFF000000   // label ink (UA CanvasText)
    static let METER_GREEN: UInt32 = 0xFF00AA00 // meter optimum fill
    // fix 4: the gauge/track hairline — Chromium's 2020 form-controls
    // refresh strokes the range track and progress/meter bars in a
    // LIGHTER gray than the control border. Ref-probed on the white ref:
    // range track edge rows y134/y141 and progress edge rows y159/y166
    // both read #B2B2B2 while the checkbox border reads #767676.
    static let TRACK_EDGE: UInt32 = 0xFFB2B2B2
    // fix 4: the radio ring's effective stroke width. The ref's ring
    // samples ≈#8F8F8F-#A1A1A1 at 1px (white ref x183/x195 @ y138 — a
    // ~1.4px #767676 stroke antialiased over 2 columns), while a plain
    // 1px AA stroke sampled ≈#CCCCCC on the Android capture — visibly
    // thinner ink. Circle strokes only: the checkbox's rect stroke sits
    // on the half-px grid and rasterizes crisp at 1px, matching the ref.
    static let RING_SW: CGFloat = 1.4

    // ── Box geometry (ref-probed, CSS px) ──────────────────────────────
    // NOTE on box sizes: the BORDER-BOX constants are aligned with lane
    // W3's UAWidgetIntrinsics (the agreed cross-lane coordination point)
    // so inline-flow atom slots and painted replicas are the SAME boxes.
    // Ink bands inside them are this file's own ref measurements.
    static let CHECK: CGFloat = 13       // checkbox/radio box (ref x158..170)
    static let CONTROL_H: CGFloat = 21   // button/textfield height (y17..37)
    static let BUTTON_PAD_X: CGFloat = 7 // button label inset
    static let RADIUS: CGFloat = 2       // button/checkbox corner radius
    static let FIELD_W: CGFloat = 153    // text/search field width (x87..239)
    static let FIELD_PAD_X: CGFloat = 4  // field text inset (glyphs from x91)
    static let AREA_W: CGFloat = 184     // textarea width (W3 box; x16..198)
    static let AREA_H: CGFloat = 36      // textarea height (W3 box; y38..73)
    static let RANGE_W: CGFloat = 129    // range border box (Blink UA width)
    static let RANGE_H: CGFloat = 21     // range box height (W3 control row)
    static let TRACK_W: CGFloat = 127    // track ink width (x19.5..145.5), centered
    static let TRACK_H: CGFloat = 8      // track ink height (y134..141 incl edges)
    static let TRACK_X: CGFloat = 3      // fix 4: track inset from the box left
                                         // (ref track left x19.5 with the range
                                         // box at x16.5 → ~3px; the old x1 put
                                         // the whole track+thumb 2px left)
    static let TRACK_Y: CGFloat = 6      // fix 4: track top inset (box top
                                         // 127.5 → ink top 133.5 ≈ y6; was 6.5)
    static let THUMB_R: CGFloat = 8      // range thumb radius (d16)
    static let BAR_H: CGFloat = 16       // progress/meter BOX height (W3)
    static let BAR_INK_H: CGFloat = 8    // bar ink height (y159..166), centered
    static let BAR_R: CGFloat = 4        // bar end radius (corner AA)
    static let PROGRESS_W: CGFloat = 160 // progress width (x16..175)
    static let METER_W: CGFloat = 80     // meter width (Blink 5em; ref cut)
    static let COLOR_W: CGFloat = 50     // color input width (x204..253)
    static let COLOR_H: CGFloat = 27     // color input height (W3 box)
    // fix 4: the swatch frame is NOT the uniform 4px the first cut used —
    // ref-probed swatch band x209.5..248.5 × y131.5..145.5 inside the
    // 50×27 box at x204/y125 → horizontal inset 5.5, vertical inset 6.5
    // (a 39×14 swatch; the old 4px painted an oversized 42×19 swatch).
    static let COLOR_INSET_X: CGFloat = 5.5
    static let COLOR_INSET_Y: CGFloat = 6.5
    static let MENU_H: CGFloat = 19      // fix 4: menulist BOX height — ref
                                         // slab borders y132..150 = 19px
                                         // (probed at the white ref x280
                                         // column; the old 25px box with 21px
                                         // centered ink drew a taller slab
                                         // than Chromium's). Cross-lane: W3's
                                         // UAWidgetIntrinsics SELECT fixedHpx
                                         // pins the SAME 19 — the ink now IS
                                         // the box.
    static let LIST_H: CGFloat = 70      // listbox height (W3 box; y80..149)
    static let LIST_ROW: CGFloat = 17    // listbox row advance ((70-2)/4)
    static let FONT: CGFloat = 13.33     // UA control font-size (Chromium)
    static let LABEL_Y: CGFloat = 3.5    // label top inset in CONTROL_H
    static let FILE_GAP: CGFloat = 6     // file button ↔ status text gap

    /// Fixed file-input strings (Chromium UA, en locale — the ref's).
    static let FILE_BUTTON = "Choose File"
    static let FILE_STATUS = "No file chosen"

    /// The replica kinds — one per distinct Chromium widget painting.
    enum Kind { case checkbox, radio, button, textfield, textarea, range,
                progress, meter, color, menulist, listbox, file, image, hidden }

    /// Resolved paint inputs for one widget instance (twin of the Kotlin
    /// Spec — see that doc for field semantics).
    struct Spec {
        let kind: Kind
        var checked: Bool = false
        var fraction: CGFloat? = nil
        var label: String = ""
        var options: [String] = []
        var accent: UInt32 = UAWidgetsGeometry.ACCENT
    }

    /// Intrinsic border-box size — the component box the mount hook
    /// sizes to. `measure` is the platform's Inter width probe at FONT px
    /// (injected so this stays pure/XCTest-pinnable).
    static func intrinsicSize(spec: Spec, measure: (String) -> CGFloat) -> (w: CGFloat, h: CGFloat) {
        switch spec.kind {
        case .checkbox, .radio: return (CHECK, CHECK)
        // Button hugs its label plus the chrome padding band.
        case .button: return (measure(spec.label) + 2 * BUTTON_PAD_X, CONTROL_H)
        case .textfield: return (FIELD_W, CONTROL_H)
        case .textarea: return (AREA_W, AREA_H)
        case .range: return (RANGE_W, RANGE_H)
        case .progress: return (PROGRESS_W, BAR_H)
        case .meter: return (METER_W, BAR_H)
        case .color: return (COLOR_W, COLOR_H)
        // Menulist: label + 4px inset + 16px arrow zone (ref "select" 56).
        case .menulist: return (measure(spec.label) + FIELD_PAD_X + 16, MENU_H)
        // Listbox: widest option + 3px text inset both sides.
        case .listbox: return ((spec.options.map(measure).max() ?? 0) + 6, LIST_H)
        // File: button + gap + status text, one control-height line.
        case .file: return (measure(FILE_BUTTON) + 2 * BUTTON_PAD_X + FILE_GAP + measure(FILE_STATUS), CONTROL_H)
        // Image input with no src: alt text only (one ~16px text line).
        case .image: return (measure(spec.label), 16)
        // display:none per the UA sheet — occupies nothing.
        case .hidden: return (0, 0)
        }
    }

    /// The paint plan — ordered back-to-front ops for one widget. Pure:
    /// the SwiftUI painter (UAWidgetView) only replays it.
    static func plan(spec: Spec, measure: (String) -> CGFloat) -> [UAWidgetOp] {
        let (w, _) = intrinsicSize(spec: spec, measure: measure)
        switch spec.kind {
        case .checkbox:
            if spec.checked {
                // Checked: accent-filled rounded box + white check
                // polyline (two strokes) — accent-color-visited ref.
                return [
                    .fillRRect(x: 0, y: 0, w: CHECK, h: CHECK, r: RADIUS, color: spec.accent),
                    .line(x1: 3.25, y1: 6.75, x2: 5.25, y2: 8.75, sw: 1.6, color: WHITE),
                    .line(x1: 5.25, y1: 8.75, x2: 9.75, y2: 4.25, sw: 1.6, color: WHITE),
                ]
            }
            // Unchecked: white interior + hairline border only.
            return [
                .fillRRect(x: 0, y: 0, w: CHECK, h: CHECK, r: RADIUS, color: WHITE),
                .strokeRRect(x: 0.5, y: 0.5, w: CHECK - 1, h: CHECK - 1, r: RADIUS, color: BORDER),
            ]
        case .radio:
            if spec.checked {
                // Checked: accent donut as three concentric fills —
                // stroke-free so both natives rasterize identically.
                return [
                    .fillCircle(cx: 6.5, cy: 6.5, r: 6.5, color: spec.accent),
                    .fillCircle(cx: 6.5, cy: 6.5, r: 4.75, color: WHITE),
                    .fillCircle(cx: 6.5, cy: 6.5, r: 3, color: spec.accent),
                ]
            }
            // Unchecked: white disc + hairline ring.
            return [
                .fillCircle(cx: 6.5, cy: 6.5, r: 6.5, color: WHITE),
                .strokeCircle(cx: 6.5, cy: 6.5, r: 6, color: BORDER),
            ]
        case .button:
            // Chrome slab + border + centered-band label (pad 7).
            return [
                .fillRRect(x: 0, y: 0, w: w, h: CONTROL_H, r: RADIUS, color: CHROME),
                .strokeRRect(x: 0.5, y: 0.5, w: w - 1, h: CONTROL_H - 1, r: RADIUS, color: BORDER),
                .label(text: spec.label, x: BUTTON_PAD_X, y: LABEL_Y, size: FONT, color: BLACK),
            ]
        case .textfield:
            // White field + border + left-aligned value text.
            return [
                .fillRect(x: 0, y: 0, w: FIELD_W, h: CONTROL_H, color: WHITE),
                .strokeRect(x: 0.5, y: 0.5, w: FIELD_W - 1, h: CONTROL_H - 1, color: BORDER),
                .label(text: spec.label, x: FIELD_PAD_X, y: LABEL_Y, size: FONT, color: BLACK),
            ]
        case .textarea:
            // White area + border + top-left text + the two grip
            // diagonals hugging the bottom-right corner.
            return [
                .fillRect(x: 0, y: 0, w: AREA_W, h: AREA_H, color: WHITE),
                .strokeRect(x: 0.5, y: 0.5, w: AREA_W - 1, h: AREA_H - 1, color: BORDER),
                .label(text: spec.label, x: 3, y: 2, size: FONT, color: BLACK),
                .line(x1: AREA_W - 7, y1: AREA_H - 2, x2: AREA_W - 2, y2: AREA_H - 7, sw: 1, color: BORDER),
                .line(x1: AREA_W - 4, y1: AREA_H - 2, x2: AREA_W - 2, y2: AREA_H - 4, sw: 1, color: BORDER),
            ]
        case .range:
            // fix 4 — track ink (127x8) at (x3, y6) in the 129x21 atom
            // box (ref band x19.5..145.5 × y134..141 against the box at
            // x16.5/y127.5); thumb center travels radius-inset across
            // the TRACK, parked at the value fraction (default 0.5 →
            // ref thumb right edge x90.5).
            let cx = TRACK_X + THUMB_R + (spec.fraction ?? 0.5) * (TRACK_W - 2 * THUMB_R)
            return [
                // Full chrome track, vertically centered in the box.
                .fillRRect(x: TRACK_X, y: TRACK_Y, w: TRACK_W, h: TRACK_H, r: BAR_R, color: CHROME),
                // fix 4 — the #B2B2B2 hairline the refresh strokes
                // around the track (ref edge rows y134/y141).
                .strokeRRect(x: TRACK_X + 0.5, y: TRACK_Y + 0.5, w: TRACK_W - 1, h: TRACK_H - 1, r: BAR_R, color: TRACK_EDGE),
                // Accent fill left of the thumb (Chromium's value side)
                // — painted OVER the stroke: the ref's value side shows
                // no gray edge under the blue.
                .fillRRect(x: TRACK_X, y: TRACK_Y, w: cx - TRACK_X, h: TRACK_H, r: BAR_R, color: spec.accent),
                // Accent thumb disc riding the box center line
                // (cy 10 → ref thumb band y130..145, center 137.5).
                .fillCircle(cx: cx, cy: 10, r: THUMB_R, color: spec.accent),
            ]
        case .progress:
            // 8px bar ink centered in the 160x16 gauge box (y4). Chrome
            // track always; the accent value chunk only for determinate
            // bars (nil → indeterminate → bare track).
            var ops: [UAWidgetOp] = [.fillRRect(x: 0, y: 4, w: PROGRESS_W, h: BAR_INK_H, r: BAR_R, color: CHROME)]
            // fix 4 — the refresh's #B2B2B2 bar hairline (ref edge rows
            // y159/y166); the value chunk covers it on the left.
            ops.append(.strokeRRect(x: 0.5, y: 4.5, w: PROGRESS_W - 1, h: BAR_INK_H - 1, r: BAR_R, color: TRACK_EDGE))
            if let f = spec.fraction {
                ops.append(.fillRRect(x: 0, y: 4, w: PROGRESS_W * f, h: BAR_INK_H, r: BAR_R, color: spec.accent))
            }
            return ops
        case .meter:
            // Meter is NOT accent-driven (css-ui-4 §7.1) — optimum green.
            // 8px ink centered in the 80x16 gauge box (y4).
            return [
                .fillRRect(x: 0, y: 4, w: METER_W, h: BAR_INK_H, r: BAR_R, color: CHROME),
                // fix 4 — same refresh hairline as the progress twin
                // (meter is off-canvas in the ref; progress is the probe).
                .strokeRRect(x: 0.5, y: 4.5, w: METER_W - 1, h: BAR_INK_H - 1, r: BAR_R, color: TRACK_EDGE),
                .fillRRect(x: 0, y: 4, w: METER_W * (spec.fraction ?? 0), h: BAR_INK_H, r: BAR_R, color: METER_GREEN),
            ]
        case .color:
            // Chrome frame + border + inner swatch (default value black).
            // fix 4: per-axis insets 5.5/6.5 → the ref's 39x14 swatch
            // band (x209.5..248.5 × y131.5..145.5), not the old 42x19.
            return [
                .fillRect(x: 0, y: 0, w: COLOR_W, h: COLOR_H, color: CHROME),
                .strokeRect(x: 0.5, y: 0.5, w: COLOR_W - 1, h: COLOR_H - 1, color: BORDER),
                .fillRect(x: COLOR_INSET_X, y: COLOR_INSET_Y, w: COLOR_W - 2 * COLOR_INSET_X, h: COLOR_H - 2 * COLOR_INSET_Y, color: BLACK),
            ]
        case .menulist:
            // fix 4 — white slab (ref-probed: menulist is white, not
            // chrome) now fills the WHOLE 19px box (MENU_H doc): the
            // ref slab IS the atom box, borders y132..150.
            return [
                .fillRRect(x: 0, y: 0, w: w, h: MENU_H, r: RADIUS, color: WHITE),
                .strokeRRect(x: 0.5, y: 0.5, w: w - 1, h: MENU_H - 1, r: RADIUS, color: BORDER),
                // Label 2.5px under the slab top: 13.33px Inter's ~16px
                // text line centered in 19 → (19−16)/2 ≈ 1.5 + the ref's
                // glyph band (ink y140..145 abs, baseline ≈145.5) lands
                // with y2.5 (old 25-box put it 2px lower).
                .label(text: spec.label, x: FIELD_PAD_X, y: 2.5, size: FONT, color: BLACK),
                // fix 4 — the dropdown arrow is a STROKED CHEVRON (two
                // ~1.8px arms meeting at the apex), never a filled
                // triangle: ref rows y138-141 show two ink clusters with
                // white BETWEEN them. Ref-fit: arms from (w−13.5, 6) and
                // (w−5.5, 6) to the apex (w−9.5, 10.5) — abs x301.5/
                // x309.5 → 305.5, y138 → 142.5 on the 57px ref select.
                .line(x1: w - 13.5, y1: 6, x2: w - 9.5, y2: 10.5, sw: 1.8, color: BLACK),
                .line(x1: w - 9.5, y1: 10.5, x2: w - 5.5, y2: 6, sw: 1.8, color: BLACK),
            ]
        case .listbox:
            // White box + border + up to 4 visible option rows (size=4
            // UA default; extra options scroll out of view).
            var ops: [UAWidgetOp] = [
                .fillRect(x: 0, y: 0, w: w, h: LIST_H, color: WHITE),
                .strokeRect(x: 0.5, y: 0.5, w: w - 1, h: LIST_H - 1, color: BORDER),
            ]
            for (i, opt) in spec.options.prefix(4).enumerated() {
                ops.append(.label(text: opt, x: 3, y: 1 + CGFloat(i) * LIST_ROW, size: FONT, color: BLACK))
            }
            return ops
        case .file:
            // "Choose File" button replica + the no-selection status text
            // (appearance-auto-input-non-widget-001 ref).
            let bw = measure(FILE_BUTTON) + 2 * BUTTON_PAD_X
            return [
                .fillRRect(x: 0, y: 0, w: bw, h: CONTROL_H, r: RADIUS, color: CHROME),
                .strokeRRect(x: 0.5, y: 0.5, w: bw - 1, h: CONTROL_H - 1, r: RADIUS, color: BORDER),
                .label(text: FILE_BUTTON, x: BUTTON_PAD_X, y: LABEL_Y, size: FONT, color: BLACK),
                .label(text: FILE_STATUS, x: bw + FILE_GAP, y: LABEL_Y, size: FONT, color: BLACK),
            ]
        case .image:
            // Image input without src: Chromium falls back to the alt
            // text (value when alt is absent). The broken-image glyph is
            // deliberately NOT replicated (small ink; noted lane risk).
            return [.label(text: spec.label, x: 0, y: 1, size: FONT, color: BLACK)]
        case .hidden:
            // hidden: the UA sheet says display:none — nothing paints.
            return []
        }
    }
}
