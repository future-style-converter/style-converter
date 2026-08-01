//
//  DecorationColorOps.swift
//  StyleEngine/typography/decoration — applier campaign wave 22,
//  lane DECOR (B-RC4b, per-line decoration colours).
//
//  TWIN of the Compose runtime's
//  runtimes/compose/src/main/java/com/styleconverter/runtime/typography/
//  DecorationColorOps.kt — but an HONEST, PARTIAL twin, so the shared
//  surface is spelled out instead of implied:
//
//    TWINNED (identical types, keyword table and ordering — change one,
//    change both): LineKind · Rgba · DecorationLine · lineKind(from:) ·
//    decorationLine(line:color:) · resolve(wire:underline:overline:
//    lineThrough:). That is the WIRE → REQUEST-LIST half, plus the
//    DecorationWire bridge that feeds it.
//
//    NOT TWINNED, by construction: everything downstream of the request
//    list. Compose paints a flat op list onto one Canvas, so its band
//    geometry and op expansion live in that file as `bandTop`/`bands`/
//    `ops`, anchored on TextLayoutResult BASELINES. SwiftUI builds one
//    View per band, so iOS anchors on LINE-BOX TOPS via
//    DecorationMetrics.top and expands ops per row inside
//    ComponentRenderer.decorationRow — a flat coloured-op list has
//    nowhere to carry its per-row offset here. Wave 22 first shipped
//    `ColoredBand` + `ops(bands:style:)` in this file as nominal mirrors
//    of the Kotlin ones; NO painter ever called them (only the test suite
//    did), so they were REMOVED rather than kept as twins in name only.
//
//  WHY: css-text-decor-3 §2.2 says each decorating box paints ITS line in
//  ITS OWN colour, and §2.1 propagates every ancestor's line down to the
//  same inline run. A nested chain therefore paints SEVERAL lines with
//  DIFFERENT colours over one run. Both native painters resolved exactly
//  ONE colour (`text-decoration-color` of the innermost box, else the
//  text colour) and stamped every band with it — so a chain lost every
//  ancestor line AND its colour.
//
//  WIRE CONTRACT — reconciled against lane EX2's LANDED extractor
//  (tools/titan/extract-fixture.mjs, the "_decorations" banner ~L847 and
//  its collapse tests): a collapsed inline run carries
//    _decorations → meta.decorations = [ {line, color?}, … ]
//    * ORDER outermost-first (the collapse root, then each wrapper from
//      outside in), so a descendant's line paints OVER its ancestors'
//      where both land on the same row. (PAINT ORDER — the corrected
//      citation: css-text-decor-3 §5.1 "Painting Order of Text
//      Decorations" pins the per-KIND order bottom-first as shadows →
//      underlines → overlines → TEXT → emphasis marks → line-through. It
//      says nothing about ancestor-vs-descendant within one kind; that is
//      the extractor's outermost-first emission order, which this module
//      preserves and the overlay's ZStack order honours. Earlier wave-22
//      comments cited "§2.5" — that section is `text-underline-position`.)
//    * `line` is exactly ONE keyword (an element declaring two
//      contributes two entries), lowercase CSS spelling; `lineKind(from:)`
//      also accepts the IR's screaming spelling defensively.
//    * `color` is the AUTHORED CSS token, all the way to the wire
//      ("blue", "#00f"). The converter does NOT normalize it to the IR
//      sRGB leaf — `meta` members are extractor-owned payloads forwarded
//      verbatim (the wave-20 `meta.attrs` precedent; rationale in
//      schema/spec/04-metadata-fields.md). DecorationWire.decorationLines
//      resolves the token through CSSTokenParser and hands this module
//      the finished `Rgba`. OMITTED (or unresolvable — logged, never
//      silent) = `currentColor` (§2.2 initial), which is why colours stay
//      optional all the way to the row, for the PAINTER to substitute.
//    * AUTHORITATIVE when present: the list is the COMPLETE set of lines
//      for the run, so `resolve` ignores the component's own flags there.
//      STYLE and THICKNESS are deliberately NOT per-entry — EX2 folds them
//      into the run's merged flat bag (root-wins), so every line of a
//      collapsed run shares one style and one thickness. A chain mixing
//      `dotted` and `solid` therefore paints all lines with the ROOT's
//      style: a known, contract-level limitation, not a silent drop.
//
//  PIXEL ORACLE — the live wave-21 WEB capture (the pixel oracle for this
//  section), tools/titan/runs/wave21-final/sections/css-text-decor/report/
//  images/web/wpt__css-text-decor__text-decoration-color.png, component
//  `text-decoration-color__7` (IR at …/per-test-ir/wpt__css-text-decor__
//  text-decoration-color.json: span[underline blue] > span[overline gray]
//  > span[line-through green], text on the innermost). Measured rows for
//  visual line 0 (16px default face, 1px bands, line advance 20):
//    row 219 = (128,128,128) gray  → OVERLINE
//    row 230 = (0,128,0)     green → LINE_THROUGH
//    row 237 = (0,0,255)     blue  → UNDERLINE
//  Three rows, three colours, one run — the iOS capture of the same
//  component painted only two rows and the Android capture only one.
//
//  Pure CoreGraphics/Foundation — no SwiftUI — so the XCTest suite pins
//  every branch without a raster (the DecorationOps pattern).
//

// CGFloat only — keeps the file compilable in isolation.
import CoreGraphics
import Foundation

enum DecorationColorOps {

    /// The three css-text-decor-3 §2.1 line keywords, as an OWN enum so
    /// this module stays dependency-free (no IR / SwiftUI imports) and
    /// the XCTest suite compiles it standalone — the DecorationOps rule.
    enum LineKind: Equatable {
        case underline, overline, lineThrough
    }

    /// A decoration colour in normalized sRGB 0..1 — the IR colour leaf
    /// shape (schema/spec/02-values.md), NOT a SwiftUI Color: the painter
    /// converts at draw time so this module has no graphics dependency.
    struct Rgba: Equatable {
        let r: CGFloat
        let g: CGFloat
        let b: CGFloat
        let a: CGFloat
        // Opaque default mirrors the Kotlin twin's `a: Float = 1f`.
        init(r: CGFloat, g: CGFloat, b: CGFloat, a: CGFloat = 1) {
            self.r = r; self.g = g; self.b = b; self.a = a
        }
    }

    /// One requested decoration line: which kind, in which colour. A nil
    /// `color` means `currentColor` (css-text-decor-3 §2.2 initial) — the
    /// painter substitutes the run's resolved text colour.
    struct DecorationLine: Equatable {
        let kind: LineKind
        let color: Rgba?
    }

    // NOTE: no `ColoredBand` / `ColoredOp` / `ops(bands:style:)` here.
    // They existed in the first wave-22 cut as nominal mirrors of the
    // Kotlin band+op pipeline, but the SwiftUI painter never called them:
    // it needs one View per band with its own row offset, which a flat
    // coloured-op list cannot express. The real iOS surface is
    // DecorationMetrics.top (row) + ComponentRenderer.decorationRow
    // (which expands DecorationOps.styleOps in the row's own colour).
    // See the banner's twin ledger.

    /// Wire token → `LineKind`. Accepts the CSS spelling (`line-through`)
    /// and the IR's screaming spelling (`LINE_THROUGH`, what
    /// TextDecorationLine actually ships — see the live per-test IR),
    /// case-insensitively. Returns nil for `none` / `blink` / anything
    /// unrecognised so the caller can DROP the entry rather than paint a
    /// wrong line — the no-silent-fallthrough rule (callers log).
    static func lineKind(from token: String?) -> LineKind? {
        // Normalize exactly like the Kotlin twin: lowercase, `_` → `-`.
        switch token?.lowercased().replacingOccurrences(of: "_", with: "-") {
        case "underline": return .underline
        case "overline": return .overline
        case "line-through": return .lineThrough
        default: return nil
        }
    }

    /// One `meta.decorations` entry → a `DecorationLine`, or nil when the
    /// `line` token is not one of the three paintable keywords.
    static func decorationLine(line: String?, color: Rgba?) -> DecorationLine? {
        // Unknown keyword → no line (the caller drops + logs).
        guard let kind = lineKind(from: line) else { return nil }
        return DecorationLine(kind: kind, color: color)
    }

    /// The ordered line list the painter walks.
    ///
    /// `wire` PRESENT (a collapsed inline chain) → it IS the list, order
    /// preserved: ancestor-first, so later entries paint over earlier ones
    /// where two decorating boxes put a line on the same row (the overlay
    /// ZStack paints in array order; see the banner's §5.1 note).
    ///
    /// AUTHORITATIVE-WHEN-PRESENT INCLUDES THE EMPTY LIST. A present-but-
    /// empty wire means "this run's complete line set is: nothing" — it
    /// arises when DecorationWire's known-keyword filter drops every entry
    /// — and it MUST paint nothing. Falling back to the component's flags
    /// there would re-enable the legacy path from the merged flat bag and
    /// paint lines the authoritative list just said were unpaintable.
    /// Callers must therefore ALSO suppress the platform built-ins on
    /// `decorations != nil`, not on "the resolved list is non-empty" —
    /// which is exactly what `overlayOwns` does.
    ///
    /// `wire` nil (every legacy document, and every component whose run
    /// was not collapsed) → synthesize the single decorating box's own
    /// lines in the property's own keyword order underline → overline →
    /// line-through (the SAME order the Kotlin twin synthesizes, so both
    /// pin tables are identical), all with a nil colour so the painter
    /// substitutes exactly what it substituted before.
    ///
    /// Dark-stage 327 note: on iOS this reorders the overlay ZStack's
    /// children (the wave-5 overlay emitted overline → line-through →
    /// underline). On the legacy path that is provably invisible for a
    /// different reason than the one wave 22 first wrote down: all three
    /// rows carry the SAME colour there, so overlap cannot show. The
    /// original justification — "the three rows are disjoint by
    /// construction" — is FALSE and must not be relied on. Two of the
    /// three pairs are indeed disjoint at any thickness (the overline's
    /// BOTTOM is pinned to the line-box top while the underline's TOP is
    /// pinned below the baseline, so growing the thickness moves them
    /// apart), but the LINE-THROUGH band is centre-anchored and grows both
    /// ways: at `text-decoration-thickness: 30px` on a 16px face it
    /// overlaps the overline. With a merged wire those bands can carry
    /// DIFFERENT colours, so array order is load-bearing, not cosmetic —
    /// pinned in DecorationColorOpsTests' overlapping-band case.
    static func resolve(wire: [DecorationLine]?,
                        underline: Bool,
                        overline: Bool,
                        lineThrough: Bool) -> [DecorationLine] {
        // Collapsed run: the merged list is authoritative (it already
        // encodes every ancestor's line AND colour) — empty included.
        if let wire = wire { return wire }
        // Legacy single-box path — twin-identical synthesis order.
        var out: [DecorationLine] = []
        if underline { out.append(DecorationLine(kind: .underline, color: nil)) }
        if overline { out.append(DecorationLine(kind: .overline, color: nil)) }
        if lineThrough { out.append(DecorationLine(kind: .lineThrough, color: nil)) }
        return out
    }

}
