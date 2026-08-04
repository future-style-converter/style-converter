//
//  ListMarkerSymbol.swift
//  StyleEngine/lists — wave 30, lane 3 (fix B6).
//
//  The GEOMETRY of a `disc` / `circle` / `square` marker. BYTE-PARALLEL
//  TWIN of Compose lists/ListMarkerSymbol.kt: same constants, same names,
//  same guard, same deferral. Change one, change both.
//
//  ## The defect (measured on the LIVE wave29-final css-lists section)
//  Both natives paint the three UA symbol markers as TEXT: `•`, `○`, `■`
//  through the marker `Text` (see ListMarkerText.marker). A browser paints
//  a SHAPE instead — Chromium's `ListMarker::RelativeSymbolMarkerRect`
//  sizes it `(ascent * 2 / 3 + 1) / 2`, which at the ref's 16px root font
//  (ascent 15) is 5.5px — and the glyph is 2.2–2.6× larger than that. Ink
//  extents of the `square` marker of
//  `wpt__css-lists__change-list-style-type-001` (`font-size: 16px`):
//
//      platform | marker ink                  | ratio vs web
//      web      | x 56–60, y 41–45  (5 × 5)   | 1.00
//      Android  | x 17–27, y 36–46  (11 × 11) | 2.20
//      iOS      | x 17–29, y 36–48  (13 × 13) | 2.60
//
//  The two natives do not even agree with EACH OTHER, because a glyph's
//  size is a property of whichever fallback face resolved it. A painted
//  shape is font-independent, so this closes the native-parity gap in the
//  same edit as the web gap.
//
//  ## Why 0.35em
//  Chromium's `(ascent * 2 / 3 + 1) / 2` is 5.5px at the ref's
//  16px/ascent-15 root = 0.34375em; the runtimes have no ascent at this
//  call site, so the em-relative approximation `sizeEm` reproduces it to
//  within 0.1px there and scales the way the glyph it replaces did. It is
//  deliberately a ratio, not the absolute 5.5px: the ref's line box is a
//  unitless 1.25 that recomputes per element (see
//  `ComponentRenderer.wptRefLineHeightRatio` for the twin argument), and a
//  marker on a 25px item must scale with it.
//
//  ## What this does NOT change (deferred, measured)
//  The marker box's ADVANCE — marker origin to the item's first glyph — is
//  untouched: the shape is painted INSIDE the box the marker `Text`
//  already measures (`.hidden()` keeps layout, `.overlay` never
//  participates in sizing), so no item moves. That advance is still wrong
//  (web puts the first glyph of the `inside` square row at x 79, i.e. 23px
//  after the marker origin; the natives put it at 17px) and is the UA's
//  per-counter-style marker padding (css-lists-3 §3.2), the still-deferred
//  B-RC3 part 3 called out at both renderers' marker branches. Shrinking
//  the advance ALONG WITH the shape would have moved every symbol row a
//  further ~5px away from web, so it is explicitly not done here.
//

import SwiftUI

enum ListMarkerSymbol {

    /// The three UA symbol markers css-counter-styles-3 §6.1 defines and a
    /// browser PAINTS rather than sets in a font.
    enum Shape: Equatable {
        /// `disc` — filled circle.
        case filledCircle
        /// `circle` — hollow circle, stroked at `strokeEm`.
        case hollowCircle
        /// `square` — filled square.
        case filledSquare
    }

    /// See "Why 0.35em" in the file header.
    static let sizeEm: CGFloat = 0.35

    /// Stroke width of the hollow `circle`, as a fraction of the font
    /// size. Chromium strokes it at 1 device pixel at the 16px root; 1/16
    /// of the em keeps that at the ref root and thickens proportionally on
    /// a larger item, which is what the glyph it replaces did.
    static let strokeEm: CGFloat = 1.0 / 16.0

    /// Which shape (if any) this counter style paints.
    ///
    /// Everything else returns nil and keeps its TEXT rendering — that
    /// includes every numeric/alphabetic style AND every counter style
    /// this runtime failed to resolve, which is the important case: the
    /// live `marker-text-matches-disc` / `-circle` fixtures declare
    /// `list-style-type: my-disc` / `my-circle` (custom `@counter-style`
    /// names the IR wire cannot carry), so `ListMarkerResolver.markerType`
    /// returns nil for them and the container's `<ol>` UA `decimal`
    /// stands. They therefore paint `1.` as text on all three runtimes
    /// (0.99 across every pair in wave29-final) and this table must not
    /// reach them.
    static func shape(for type: ListMarkerType) -> Shape? {
        switch type {
        case .disc: return .filledCircle
        case .circle: return .hollowCircle
        case .square: return .filledSquare
        default: return nil
        }
    }

    /// `shape(for:)` narrowed by the string the marker box is ACTUALLY
    /// about to paint — nil unless that string is exactly the symbol glyph
    /// this runtime's own table produces for `type`.
    ///
    /// The guard exists for `meta.markerText`: wave 27 made a BAKED marker
    /// win outright over the local table (it carries the converter's full
    /// css-counter-styles-3 §6 resolution, including the `<ol start>`
    /// ordinal), and a producer is free to bake ANY string onto an item
    /// whose resolved `list-style-type` still reads `disc`. Replacing that
    /// string's ink with a painted circle would silently delete content the
    /// wire asked for, so the shape only fires when the two agree.
    static func shape(for type: ListMarkerType, markerText: String) -> Shape? {
        guard let s = shape(for: type) else { return nil }
        let ownGlyph = ListMarkerText.marker(index: 0,
                                             config: ListMarkerConfig(type: type))
        return markerText == ownGlyph ? s : nil
    }

    /// The painted shape's side length in points for a run at
    /// `fontSizePx`. Floored at zero so an unresolved font size paints
    /// nothing rather than an inverted rect — the marker `Text` box is
    /// still measured either way, so an item never moves because of it.
    static func sizePt(fontSizePx: CGFloat) -> CGFloat {
        fontSizePx <= 0 ? 0 : fontSizePx * sizeEm
    }

    /// Stroke width in points for `.hollowCircle` at `fontSizePx`.
    static func strokePt(fontSizePx: CGFloat) -> CGFloat {
        fontSizePx <= 0 ? 0 : fontSizePx * strokeEm
    }
}

/// Replace a marker `Text`'s INK with the painted shape, keeping the box
/// it measured — the SwiftUI spelling of Compose's
/// `ListMarkerSymbol.paint` (a `Modifier.drawWithContent` that never calls
/// `drawContent()`).
///
/// `.hidden()` keeps the view in the layout and removes only its pixels;
/// `.overlay` never participates in its host's sizing. Together they leave
/// the glyph's advance, line box and first baseline driving the row while
/// changing what is drawn — which is the whole point (see "What this does
/// NOT change" in the file header).
///
/// `alignment: .leading` = horizontally at the marker box's start edge,
/// vertically CENTRED. Chromium aligns the symbol against the font's
/// ascent rather than the line-box centre, but the two coincide within a
/// pixel on the corpus: the reference `square` occupies rows 41–45 of a
/// line box spanning 33–53 (centre 43, shape centre 43). Centring is
/// chosen over an ascent-relative offset because this call site has the
/// box but no font metrics — the same decision Compose's
/// `ListMarkerSymbol.topPx` documents.
struct ListMarkerSymbolPaint: ViewModifier {
    let shape: ListMarkerSymbol.Shape?
    let sizePt: CGFloat
    let strokePt: CGFloat

    @ViewBuilder
    func body(content: Content) -> some View {
        if let shape, sizePt > 0 {
            content
                .hidden()
                .overlay(alignment: .leading) { symbol(shape) }
        } else {
            content
        }
    }

    @ViewBuilder
    private func symbol(_ shape: ListMarkerSymbol.Shape) -> some View {
        switch shape {
        // A bare `Circle()` / `Rectangle()` fills with the current
        // foreground style, i.e. exactly the ink the glyph it replaces
        // would have taken — no colour has to be threaded here (the
        // Compose twin must resolve one explicitly because its draw scope
        // cannot read a CompositionLocal).
        case .filledCircle:
            Circle().frame(width: sizePt, height: sizePt)
        // `strokeBorder` insets the stroke so the drawn extent still spans
        // `sizePt`, matching the filled disc's footprint — which is what a
        // browser does with the two styles.
        case .hollowCircle:
            Circle().strokeBorder(lineWidth: strokePt)
                .frame(width: sizePt, height: sizePt)
        case .filledSquare:
            Rectangle().frame(width: sizePt, height: sizePt)
        }
    }
}

extension View {
    /// Attach `ListMarkerSymbolPaint` for a resolved counter style, or
    /// leave the view untouched when it paints text.
    func listMarkerSymbol(type: ListMarkerType,
                          markerText: String,
                          fontSizePx: CGFloat) -> some View {
        modifier(ListMarkerSymbolPaint(
            shape: ListMarkerSymbol.shape(for: type, markerText: markerText),
            sizePt: ListMarkerSymbol.sizePt(fontSizePx: fontSizePx),
            strokePt: ListMarkerSymbol.strokePt(fontSizePx: fontSizePx)))
    }
}
