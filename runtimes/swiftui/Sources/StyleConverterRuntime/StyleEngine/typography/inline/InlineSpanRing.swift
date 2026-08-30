//
//  InlineSpanRing.swift
//  StyleEngine/typography/inline — Wave 47 (lane Z6): the STYLED-SPAN
//  RING, the third breach in the inline-run wall, iOS half.
//
//  Waves 44/45 fold text-and-plain-span hosts into one paragraph but
//  bail the moment a member carries live styling — the folded string is
//  one uniform style. Measured victims (wave46-final, browser-ref SSIM):
//  css-overflow/block-ellipsis-004/005/006 (the bold-italic-1.5em
//  bordered <span> member renders as a full-width block — ios-ref
//  0.8997/0.9093/0.9093) and css-text-decor/text-decoration-inset-014
//  (an underlined <u> member stacks on its own line — ios-ref 0.9317).
//
//  THE RING. An inline box's own `color` / `font-*` / `text-decoration-*`
//  apply to exactly its glyphs inside the shared inline formatting
//  context (CSS 2.1 §9.4.2; css-text-decor-3 §2.1). SwiftUI expresses
//  that natively as CONCATENATED per-segment Texts — `Text(a) +
//  Text(b).underline() + Text(c)` flows as ONE paragraph with per-range
//  attributes — so the fold records each styled member's RANGE over the
//  merged string plus this ring's typed Style, and PlaceholderLabel
//  splits its final display string at the (alignment-remapped)
//  boundaries and builds the concatenation. Chosen over an
//  AttributedString rebuild because the label's whole modifier chain
//  (font / kerning / line limit / pinned line boxes) already hangs off a
//  `Text`, and Text concatenation keeps every downstream stage untouched.
//
//  ADMISSION — byte-parallel with the Compose twin
//  (runtimes/compose/.../typography/inline/InlineSpanRing.kt), including
//  the walls:
//    • Color / FontSize (px, em/% factor, keyword ladder) / FontWeight /
//      FontStyle / TextDecorationLine underline+line-through ride.
//    • `overline` refuses — Text has no per-range overline.
//    • TextDecorationColor only when EQUAL to the member's effective ink
//      (own Color, else the host's): kept parallel with Compose — whose
//      SpanStyle paints decorations in the span's text color — so the
//      two folds can never disagree about a host. SwiftUI could color it
//      (`.underline(color:)`); loosening iOS alone is a deliberate
//      future divergence, not this wave's.
//    • TextDecorationStyle solid only; BoxDecorationBreak inert.
//    • Border longhands: a STATED LOSS (the fold's paragraph geometry
//      beats the stacked full-width block by the measured margins above;
//      the dropped box ink is reported for the seam's log, never silent).
//  Everything else refuses with the property named (`member-prop:` in
//  the fold's logged bail).
//
//  Pure Foundation so XCTest pins admission + alignment on the verbatim
//  wave46-final wire payloads without a render surface.
//

import Foundation

enum InlineSpanRing {

    /// Normalized sRGB 0..1 — the IR's own color space, kept SwiftUI-free
    /// so the fold stays pure (the label builds the Color).
    struct Ink: Equatable {
        let r: Double
        let g: Double
        let b: Double
        let a: Double
    }

    /// Wave 48 (lane W4) — a `sup`/`sub` member's UA baseline shift
    /// (HTML rendering §15.3.4: `sup { vertical-align: super }`,
    /// `sub { vertical-align: sub }`). Blink resolves the two keywords in
    /// LayoutBoxModelObject::VerticalPosition as PIXELS off the PARENT's
    /// computed font-size — super raises by size/3 + 1, sub lowers by
    /// size/5 + 1 — verified against the frozen subelements-002 ref
    /// (parent 32px → predicted raise 11.67px, measured ≈12px; the
    /// member's OWN size would predict 9.9px, clearly off). The parent's
    /// size travels in the same px-or-paragraph-factor encoding the
    /// font-size fields use, so the label resolves it exactly.
    struct VerticalShift: Equatable {
        /// true = super (raise), false = sub (lower).
        var up: Bool
        /// The PARENT box's absolute size in px, when the enclosing
        /// member declared one — else nil.
        var parentPx: CGFloat? = nil
        /// Else the parent's size as a factor of the paragraph's resolved
        /// size (1.0 for a direct member — its parent IS the paragraph).
        var parentEm: CGFloat = 1
    }

    /// One member's admitted span attribution. All-nil/false = plain: the
    /// member folds exactly like a wave-44 policy-only span and no span
    /// is recorded for it.
    struct Style: Equatable {
        /// The member's own `color`, or nil = inherit the paragraph's.
        var ink: Ink? = nil
        /// Absolute font-size in px (px / keyword ladder wires).
        var fontSizePx: CGFloat? = nil
        /// Relative font-size FACTOR (em / % / smaller / larger wires) —
        /// multiplied against the paragraph's resolved size in the label
        /// (the fold host is the CSS parent, css-values-4 §5.1.1).
        var fontSizeEm: CGFloat? = nil
        /// Numeric weight 100..900 (the wire is pre-normalized).
        var fontWeight: Int? = nil
        /// css-fonts-4 §2.2 italic/oblique slant.
        var italic: Bool = false
        /// Underline: `text-decoration-line: underline` or the UA `<u>`
        /// rule (HTML rendering §15.3.3) — the one UA ink this ring
        /// models, which is why `u` joins the styled tag ring.
        var underline: Bool = false
        /// `text-decoration-line: line-through`.
        var lineThrough: Bool = false
        /// Wave 48 (lane W4): the `sup`/`sub` UA baseline shift, or nil
        /// for every horizontal member — the pre-wave-48 shapes all carry
        /// nil, so `isPlain` comparisons are untouched by construction.
        var shift: VerticalShift? = nil

        /// True when no attribute differs from plain inherited text.
        var isPlain: Bool { self == Style() }
    }

    /// The admission verdict for one glyph-bearing member.
    enum Admission {
        /// The member folds; `statedLossTypes` names any box ink the ring
        /// consciously drops (border longhands) for the seam's log.
        case admitted(Style, statedLossTypes: [String])
        /// Outside the ring — the fold bails with this reason verbatim.
        case refused(String)
    }

    /// Wave 48 (lane W4) — the UA-ITALIC family (HTML rendering §15.3.4:
    /// `cite, dfn, em, i, var { font-style: italic }`). Their one UA rule
    /// is a slant the span expresses exactly, so the whole family joins
    /// the ring together — admitting only the corpus-present `i` would be
    /// a carve-out, and the rule covers all five identically.
    static let uaItalicTags: Set<String> = ["i", "em", "cite", "var", "dfn"]

    /// Wave 48 (lane W4) — the UA-shifted pair (HTML rendering §15.3.4:
    /// `sub, sup { font-size: smaller }` plus the vertical-align
    /// super/sub keywords `VerticalShift`'s banner derives).
    static let uaShiftTags: Set<String> = ["sup", "sub"]

    /// The glyph-member tags this ring styles: the wave-44 no-UA-ink
    /// trio, `u` (wave 47 — UA underline), and the wave-48 UA-italic +
    /// UA-shifted families whose UA rules the ring now models per-range.
    static let styledMemberTags: Set<String> =
        Set(["span", "time", "data", "u"]).union(uaItalicTags).union(uaShiftTags)

    /// The border-box longhands a styled member may carry as a STATED
    /// loss (banner). Colors are paint-inert without a style
    /// (css-backgrounds-3 §3.2) and join the loss when a style makes the
    /// box real.
    private static let borderLossTypes: Set<String> = [
        "BorderTopStyle", "BorderRightStyle", "BorderBottomStyle", "BorderLeftStyle",
        "BorderTopWidth", "BorderRightWidth", "BorderBottomWidth", "BorderLeftWidth",
    ]
    private static let borderColorTypes: Set<String> = [
        "BorderTopColor", "BorderRightColor", "BorderBottomColor", "BorderLeftColor",
    ]

    /// Admit one glyph-bearing member's property bag, or refuse with the
    /// first offending property named. `Hyphens` is skipped — the fold's
    /// adoption walk owns it (css-text-3 §6.1). Byte-parallel with
    /// InlineSpanRing.kt's admit.
    static func admit(tag: String,
                      properties: [IRProperty],
                      hostProperties: [IRProperty]) -> Admission {
        // The UA <u> underline (HTML rendering §15.3.3) seeds the style;
        // wave 48 (lane W4) seeds the UA-italic slant and the sup/sub
        // shift the same way (§15.3.4 — a DIRECT member's shift parent is
        // the paragraph, factor 1; composeNested rewrites nested parents).
        var style = Style(
            italic: uaItalicTags.contains(tag),
            underline: tag == "u",
            shift: tag == "sup" ? VerticalShift(up: true)
                 : tag == "sub" ? VerticalShift(up: false) : nil
        )
        // Whether the member DECLARED font-size — an author declaration
        // beats the sup/sub UA `font-size: smaller` seed (cascade origin
        // order, css-cascade-4 §6.1: author over user agent).
        var declaredFontSize = false
        // Border longhands seen — one stated loss when a style keyword
        // makes any side real (§3.2 initial `none` paints nothing).
        var borderStyleSeen = false
        var losses: [String] = []
        for prop in properties {
            switch prop.type {
            // Paragraph policy — the fold's adoption walk owns it.
            case "Hyphens":
                continue
            // The member's own text ink (css-color-4 §3.1).
            case "Color":
                guard let ink = extractInk(prop.data) else {
                    return .refused("member-prop:Color-unresolved")
                }
                style.ink = ink
            // css-fonts-4 §2.4 — absolute px or a parent-relative factor.
            case "FontSize":
                switch extractFontSize(prop.data) {
                case .px(let px): style.fontSizePx = px; declaredFontSize = true
                case .factor(let f): style.fontSizeEm = f; declaredFontSize = true
                case nil: return .refused("member-prop:FontSize-unresolved")
                }
            // css-fonts-4 §2.2 — the wire is pre-normalized 100..900.
            case "FontWeight":
                guard case .object(let o) = prop.data,
                      let w = o["weight"]?.intValue else {
                    return .refused("member-prop:FontWeight-unresolved")
                }
                style.fontWeight = w
            // css-fonts-4 §2.2 font-style — italic/oblique slant; a
            // declared `normal` RESETS the wave-48 UA-italic seed (author
            // over user agent, css-cascade-4 §6.1).
            case "FontStyle":
                switch ValueExtractors.extractKeyword(prop.data)?.lowercased() {
                case "italic", "oblique": style.italic = true
                case "normal": style.italic = false
                default: return .refused("member-prop:FontStyle-unresolved")
                }
            // css-text-decor-3 §2.1 — underline/line-through only;
            // per-range overline exists on neither platform.
            case "TextDecorationLine":
                guard let lines = extractDecorationLines(prop.data) else {
                    return .refused("member-prop:TextDecorationLine-unresolved")
                }
                for line in lines {
                    switch line {
                    case "underline": style.underline = true
                    case "line-through": style.lineThrough = true
                    case "none": break
                    default: return .refused("member-prop:TextDecorationLine:\(line)")
                    }
                }
            // §2.2 — only an ink-equal decoration color rides (banner).
            case "TextDecorationColor":
                guard let declared = extractInk(prop.data) else {
                    return .refused("member-prop:TextDecorationColor-unresolved")
                }
                let effective = style.ink ?? hostProperties
                    .first(where: { $0.type == "Color" })
                    .flatMap { extractInk($0.data) }
                guard let effective, inkEquals(declared, effective) else {
                    return .refused("member-prop:TextDecorationColor-divergence")
                }
            // §2.3 — span decorations are solid; only solid may ride.
            case "TextDecorationStyle":
                switch ValueExtractors.extractKeyword(prop.data)?.lowercased() {
                case "solid", nil: break
                default: return .refused("member-prop:TextDecorationStyle-unsolid")
                }
            // css-break-4 §5 — fragments a box paint this ring does not
            // paint; inert alongside the border stated-loss below.
            case "BoxDecorationBreak":
                continue
            default:
                // The border box — a stated loss, not a refusal (banner).
                if borderLossTypes.contains(prop.type) {
                    if prop.type.hasSuffix("Style") { borderStyleSeen = true }
                    if !losses.contains(prop.type) { losses.append(prop.type) }
                    continue
                }
                // Paint-inert alone; folded into the loss when styled.
                if borderColorTypes.contains(prop.type) { continue }
                // Everything else is outside the ring — named refusal.
                return .refused("member-prop:\(prop.type)")
            }
        }
        // Wave 48 (lane W4): sup/sub's UA `font-size: smaller` (HTML
        // rendering §15.3.4) — one ×1.2 ladder step down (Blink
        // FontSizeFunctions::SmallerFontSize), the ring's existing
        // `smaller` factor — unless the author declared a size (§6.1).
        if uaShiftTags.contains(tag), !declaredFontSize { style.fontSizeEm = 1.0 / 1.2 }
        // A styleless border never paints — only report a REAL box.
        let stated = borderStyleSeen ? ["border-box-ink(\(losses.joined(separator: ",")))"] : []
        return .admitted(style, statedLossTypes: stated)
    }

    /// Wave 48 (lane W4) — compose a ONE-LEVEL nested member's admitted
    /// style onto its enclosing member's, for the fold's per-piece span
    /// emission (subelements-002's `<i>e = mc<sup>2</sup></i>`): the
    /// nested box inherits the outer's inheritable attributes
    /// (css-cascade-4 §7.3) unless it declares its own, decorations
    /// accumulate over descendants (css-text-decor-3 §2.1 propagation),
    /// and a relative nested size resolves against the OUTER box
    /// (css-values-4 §5.1.1 — its parent is the outer member, not the
    /// paragraph). Returns nil for the one shape no flat span can
    /// express: BOTH boxes shifted (vertical-align offsets ADD box-by-
    /// box; a compound shift needs the tree the fold flattened away —
    /// zero corpus presence, named wall). Twin: InlineSpanRing.kt.
    static func composeNested(outer: Style, nested: Style) -> Style? {
        // Compound sup/sub-in-sup/sub cannot be expressed flat — refuse.
        if outer.shift != nil, nested.shift != nil { return nil }
        // The nested member's own declarations win; else the outer's
        // inheritable attributes flow through (ink, weight, slant).
        var composed = Style()
        if let px = nested.fontSizePx {
            // Nested absolute size stands on its own.
            composed.fontSizePx = px
        } else if let em = nested.fontSizeEm {
            // Nested factor resolves against the OUTER size: px parent →
            // absolute; factor/unstyled parent → factors multiply.
            if let outerPx = outer.fontSizePx {
                composed.fontSizePx = outerPx * em
            } else {
                composed.fontSizeEm = (outer.fontSizeEm ?? 1) * em
            }
        } else {
            // No nested size — the outer's own (possibly relative) size
            // inherits down unchanged.
            composed.fontSizePx = outer.fontSizePx
            composed.fontSizeEm = outer.fontSizeEm
        }
        // The nested shift's PARENT is the outer member — rewrite the
        // seed's paragraph-relative parent with the outer's size encoding
        // (see VerticalShift's banner for the Blink px rule it feeds).
        if var shift = nested.shift {
            shift.parentPx = outer.fontSizePx
            shift.parentEm = outer.fontSizeEm ?? 1
            composed.shift = shift
        } else {
            composed.shift = outer.shift
        }
        composed.ink = nested.ink ?? outer.ink
        composed.fontWeight = nested.fontWeight ?? outer.fontWeight
        // OR is the §6.1 approximation: a nested `font-style: normal`
        // cannot un-slant an italic outer here — no corpus member
        // declares one, and modeling it needs a tri-state the ring does
        // not carry (stated, not silent).
        composed.italic = outer.italic || nested.italic
        // §2.1: decorations PROPAGATE — an outer underline paints over
        // nested descendants, and a nested one adds to it.
        composed.underline = outer.underline || nested.underline
        composed.lineThrough = outer.lineThrough || nested.lineThrough
        return composed
    }

    /// Wave 48 (fix lane F5) — resolve a `VerticalShift` to Blink's PIXEL
    /// rule, in ONE place per platform (the Compose twin is
    /// InlineSpanRing.kt `shiftPx`, feeding the BaselineShift multiplier;
    /// this one feeds PlaceholderLabel's Text.baselineOffset, which takes
    /// points directly — positive raises). Extracted because the rule
    /// previously lived inline at both seams with ZERO unit coverage —
    /// S5's mutation of `/3+1 → /3` passed all 4644 native tests; the
    /// pins on this helper (InlineSpanRingTests / InlineSpanRingTest,
    /// same rows both sides) close that hole.
    ///
    /// The arithmetic (`VerticalShift`'s banner has the ref verification):
    ///   parent  = parentPx ?? parentEm × paragraph  — the px-or-factor
    ///             encoding `admit` seeds (factor 1 for a direct member)
    ///             and `composeNested` rewrites for nested members;
    ///   super  →  parent/3 + 1   (raise — positive);
    ///   sub    → −(parent/5 + 1) (lower — negative);
    /// per Blink LayoutBoxModelObject::VerticalPosition's resolution of
    /// the `super`/`sub` vertical-align keywords.
    static func shiftPx(_ shift: VerticalShift, paragraphFontSizePx: CGFloat) -> CGFloat {
        // The PARENT box's resolved size: absolute when the enclosing
        // member declared px, else its factor against the paragraph.
        let parentPx = shift.parentPx ?? shift.parentEm * paragraphFontSizePx
        // Blink's keyword resolution — the direction carries the sign
        // (positive raises, Text.baselineOffset's own convention).
        return shift.up ? parentPx / 3 + 1 : -(parentPx / 5 + 1)
    }

    /// FontSize wire → absolute px or a parent-relative factor.
    private enum FontSizeValue {
        case px(CGFloat)
        case factor(CGFloat)
    }

    /// The FontSize wire shapes, byte-parallel with the Compose twin's
    /// table (which mirrors TextStyleApplier's paragraph read, so span
    /// and paragraph resolution can never disagree about a shape).
    private static func extractFontSize(_ data: IRValue) -> FontSizeValue? {
        guard case .object(let o) = data else { return nil }
        // Resolved pixels (DynamicValueResolver adds "px" upstream).
        if let px = o["px"]?.doubleValue { return .px(CGFloat(px)) }
        if let px = o["pixels"]?.doubleValue { return .px(CGFloat(px)) }
        guard case .object(let original)? = o["original"] else { return nil }
        switch original["type"]?.stringValue {
        // css-fonts-4 §2.4 absolute-size ladder (the shared px table).
        case "absolute", "absoluteKeyword":
            switch original["keyword"]?.stringValue?.lowercased() {
            case "xx-small": return .px(9)
            case "x-small": return .px(10)
            case "small": return .px(13)
            case "medium": return .px(16)
            case "large": return .px(18)
            case "x-large": return .px(24)
            case "xx-large": return .px(32)
            case "xxx-large": return .px(48)
            default: return nil
            }
        // §2.5 smaller/larger — one ×1.2 ladder step off the parent.
        case "relative":
            switch original["keyword"]?.stringValue?.lowercased() {
            case "larger": return .factor(1.2)
            case "smaller": return .factor(1.0 / 1.2)
            default: return nil
            }
        // css-values-4 §5.1.1 — em against the parent (= the fold host),
        // rem against the 16px pinned root.
        case "length":
            guard case .object(let inner)? = original["original"],
                  let v = inner["v"]?.doubleValue else { return nil }
            switch inner["u"]?.stringValue?.uppercased() {
            case "EM": return .factor(CGFloat(v))
            case "REM": return .px(CGFloat(v) * 16)
            default: return nil
            }
        // css-fonts-4 §2.4 <percentage> — against the parent size.
        case "percentage":
            guard let pct = original["value"]?.doubleValue else { return nil }
            return .factor(CGFloat(pct) / 100)
        default:
            return nil
        }
    }

    /// The wire's sRGB color shape ({"srgb":{r,g,b[,a]}}) → Ink.
    private static func extractInk(_ data: IRValue) -> Ink? {
        guard case .object(let o) = data,
              case .object(let srgb)? = o["srgb"],
              let r = srgb["r"]?.doubleValue,
              let g = srgb["g"]?.doubleValue,
              let b = srgb["b"]?.doubleValue else { return nil }
        // Alpha is optional on the wire (opaque when absent).
        return Ink(r: r, g: g, b: b, a: srgb["a"]?.doubleValue ?? 1)
    }

    /// Component-wise equality with a 1/255 tolerance — the wire ships
    /// 0..1 floats quantized from 8-bit channels.
    private static func inkEquals(_ a: Ink, _ b: Ink) -> Bool {
        let eps = 1.0 / 255.0
        return abs(a.r - b.r) <= eps && abs(a.g - b.g) <= eps
            && abs(a.b - b.b) <= eps && abs(a.a - b.a) <= eps
    }

    /// TextDecorationLine wire: an array of keywords or one bare keyword,
    /// normalized lowercase with the wire's LINE_THROUGH underscore
    /// turned back into the css hyphen. nil = unreadable (refuse).
    private static func extractDecorationLines(_ data: IRValue) -> [String]? {
        func canon(_ s: String) -> String {
            s.lowercased().replacingOccurrences(of: "_", with: "-")
        }
        if case .array(let items) = data {
            var out: [String] = []
            for item in items {
                guard case .string(let s) = item else { return nil }
                out.append(canon(s))
            }
            return out
        }
        return ValueExtractors.extractKeyword(data).map { [canon($0)] }
    }

    /// Map span offsets from the fold's MERGED string into the label's
    /// final display string. Between the two, the label's only
    /// character-level rewrites are (verified against the actual surgery
    /// sites — SoftHyphenPolicy.displayString + GreedyLineBreaker.lines):
    ///   • U+00AD soft-hyphen DELETION (`hyphens: none` rule A, plus the
    ///     breaker's analyze() strip);
    ///   • collapsible-space DELETION or '\n' REPLACEMENT and hard-break
    ///     '\n' INSERTION (the greedy pre-break);
    ///   • hyphen-character INSERTION at a taken break (U+2010 / '-').
    /// The walk advances both strings under exactly that op set and
    /// answers nil the moment they cannot be explained by it — the label
    /// then renders the fold UN-styled (degraded style, never wrong
    /// glyphs, logged). Offsets are Character counts; returns the
    /// transformed offset for each original offset 0...count (inclusive
    /// end sentinel). Twin: InlineSpanRing.kt `alignment`.
    static func alignment(original: String, transformed: String) -> [Int]? {
        let orig = Array(original)
        let trans = Array(transformed)
        var map = [Int](repeating: 0, count: orig.count + 1)
        var i = 0
        var j = 0
        while i < orig.count {
            let oc = orig[i]
            // Verbatim character — the overwhelming common case.
            if j < trans.count, oc == trans[j] {
                map[i] = j; i += 1; j += 1
                continue
            }
            // A collapsible space rewritten as the break it became.
            if oc == " ", j < trans.count, trans[j] == "\n" {
                map[i] = j; i += 1; j += 1
                continue
            }
            // Deleted original char: collapsed space or stripped shy.
            if oc == " " || oc == "\u{00AD}" {
                map[i] = j; i += 1
                continue
            }
            // Inserted transformed char: a hard break or the
            // materialized hyphen (UA U+2010, ASCII '-').
            if j < trans.count, trans[j] == "\n" || trans[j] == "\u{2010}" || trans[j] == "-" {
                j += 1
                continue
            }
            // Anything else is surgery this map does not model.
            return nil
        }
        map[orig.count] = trans.count
        return map
    }
}
