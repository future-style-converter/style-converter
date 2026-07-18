//
//  AbsposStaticAlignment.swift
//  StyleConverterRuntime — lane FLEX-SAFE.
//
//  The STATIC-POSITION alignment of an absolutely-positioned child of a
//  flex container. css-position-3 §3.1: with auto insets an abspos box
//  sits at its STATIC POSITION — for a flex-container parent that is
//  "the position the box would have if it were the sole flex item"
//  (css-flexbox-1 §4.1), i.e. align-self places a hypothetical item of
//  the child's size on the cross axis. css-align-3 §4.4 adds the
//  overflow keywords: `safe <pos>` falls back to START alignment when
//  the box OVERFLOWS its alignment container; `unsafe <pos>` (and the
//  bare keyword, whose default overflow behaviour here matches unsafe)
//  keeps the requested alignment even when it overflows both edges.
//
//  Wire shapes (pinned against the LIVE converter, 2026-07-18):
//    align-self: center        → {"type":"AlignSelf","data":"CENTER"}
//    align-self: safe center   → {"type":"Generic","data":{"propertyName":
//                                 "align-self","rawValue":"safe center",
//                                 "_unmapped":true}}
//    align-self: unsafe center → Generic, rawValue "unsafe center"
//    align-self: safe end      → Generic, rawValue "safe end"
//  (The converter's AlignSelfPropertyParser recognises only bare
//  keywords, so every overflow-modifier value ships via Generic.)
//
//  Shared-semantics contract: the Compose runtime carries the twin
//  AbsposStaticAlignment.kt; both platforms' crossOffset compute
//  byte-identical Doubles for the same inputs, pinned by unit tests
//  with IDENTICAL (childPx, containerPx, spec) → offset tables.
//

// CoreGraphics for CGFloat/CGSize in the view-level convenience.
import CoreGraphics

enum AbsposStaticAlignment {

    /// Alignment base after stripping overflow keywords — css-align-3
    /// §4.2's <self-position> space collapsed to the three physical
    /// outcomes a static position can take on one axis.
    enum Base: Equatable { case start, center, end }

    /// One resolved cross-axis claim: base position + whether the
    /// `safe` overflow keyword was present (css-align-3 §4.4).
    struct Spec: Equatable {
        // The requested position on the axis.
        let base: Base
        // True for `safe <pos>` — falls back to start on overflow.
        let safe: Bool
    }

    /// Resolve the abspos static-position CROSS alignment from a
    /// child's IR list, reading BOTH wire channels (typed AlignSelf +
    /// the Generic escape hatch — see the pinned shapes above). Nil
    /// when the child declares no align-self, or a value with no
    /// static-position mapping (auto/stretch/baseline keep the caller's
    /// legacy start anchor — css-flexbox-1 §4.1 treats them as
    /// flex-start for abspos children).
    static func resolveCross(from properties: [IRProperty]) -> Spec? {
        // Channel 1 — typed wire: bare keyword primitive ("CENTER").
        if let prop = properties.first(where: { $0.type == "AlignSelf" }) {
            // Same keyword reader the flexbox extractor uses.
            if let base = baseOf(ValueExtractors.extractKeyword(prop.data)) {
                // Typed keywords never carry an overflow modifier (the
                // parser rejects two-token values) → safe is false.
                return Spec(base: base, safe: false)
            }
            // Typed but unmappable (STRETCH/BASELINE/AUTO) → fall
            // through; the converter never emits typed AND Generic for
            // the same declaration, so this cannot double-read.
        }
        // Channel 2 — Generic escape hatch for `safe|unsafe <pos>`.
        for p in properties where p.type == "Generic" {
            // Only align-self Generics participate.
            guard case .object(let o) = p.data,
                  o["propertyName"]?.stringValue == "align-self",
                  let raw = o["rawValue"]?.stringValue else { continue }
            // First align-self Generic wins (cascade already resolved).
            return parseRaw(raw)
        }
        // No align-self claim on either channel.
        return nil
    }

    /// Parse a raw `align-self` value ("safe center", "unsafe
    /// flex-end", …). css-align-3 §4.4 grammar subset:
    /// [ safe | unsafe ]? <self-position>. Internal for direct pins.
    static func parseRaw(_ raw: String) -> Spec? {
        // Whitespace-tokenized, case-insensitive per CSS keyword rules.
        let tokens = raw.trimmingCharacters(in: .whitespaces)
            .lowercased()
            .split(whereSeparator: { $0 == " " || $0 == "\t" })
            .map(String.init)
        // Overflow modifier present iff the token appears (§4.4).
        let safe = tokens.contains("safe")
        // The base keyword is whichever token is NOT an overflow word.
        let baseToken = tokens.first { $0 != "safe" && $0 != "unsafe" }
        // Unknown/absent base → no claim (caller keeps legacy start).
        guard let base = baseOf(baseToken) else { return nil }
        return Spec(base: base, safe: safe)
    }

    /// Keyword → Base. Accepts the converter's UPPER_SNAKE typed
    /// spelling AND raw CSS hyphenated spelling so both wires share one
    /// table. self-start/self-end fold to start/end — no writing-mode
    /// divergence in the runtime's LTR horizontal-tb normalization.
    private static func baseOf(_ kw: String?) -> Base? {
        switch kw?.uppercased() {
        // css-align-3 §4.2 start-family keywords.
        case "FLEX-START", "FLEX_START", "START", "SELF-START", "SELF_START":
            return .start
        // center + the anchor-positioning fold (no anchor in scope).
        case "CENTER", "ANCHOR-CENTER", "ANCHOR_CENTER":
            return .center
        // end-family keywords.
        case "FLEX-END", "FLEX_END", "END", "SELF-END", "SELF_END":
            return .end
        // auto/stretch/baseline/unknown → no static-position claim.
        default:
            return nil
        }
    }

    /// True when the child declares an explicit inset on the given axis
    /// — css-position-3 §3.5: a non-auto inset REPLACES the static
    /// position on that axis (PositionApplier then owns the offset), so
    /// the static-position alignment must stand down to avoid a double
    /// offset. Logical insets map per the runtime's LTR horizontal-tb
    /// normalization (block → vertical, inline → horizontal — the same
    /// table PositionExtractor resolves with).
    static func hasCrossInset(_ properties: [IRProperty], vertical: Bool) -> Bool {
        // The IR type names that pin this axis (physical + logical).
        let axisTypes: Set<String> = vertical
            ? ["Top", "Bottom", "InsetBlockStart", "InsetBlockEnd"]
            : ["Left", "Right", "InsetInlineStart", "InsetInlineEnd"]
        // Any declared inset on the axis disables the static position.
        return properties.contains { axisTypes.contains($0.type) }
    }

    /// The cross-axis static-position offset (px) of the child's box
    /// from the alignment container's start edge:
    ///
    ///   free = containerPx − childPx        (negative ⇒ overflow)
    ///   safe && overflow → 0                (css-align-3 §4.4 fallback)
    ///   start → 0 · center → free/2 · end → free
    ///
    /// Negative results are the child spilling toward the start edge
    /// (unsafe/plain center overflows BOTH edges equally — the WPT
    /// flex-abspos-staticpos refs). Byte-identical to the Compose twin.
    static func crossOffset(childPx: Double, containerPx: Double, spec: Spec) -> Double {
        // Free space on the axis; < 0 means the child overflows.
        let free = containerPx - childPx
        // §4.4: `safe` swaps to START alignment on overflow.
        if spec.safe && free < 0 { return 0 }
        // Plain factor placement — start 0, center ½, end 1.
        switch spec.base {
        case .start:  return 0
        case .center: return free / 2
        case .end:    return free
        }
    }

    /// View-level convenience for ComponentRenderer's positioned-child
    /// overlay: the FULL static cross offset (as a CGSize shift from
    /// the overlay's top-leading anchor) for one abspos child of a flex
    /// container. Zero when any input is unresolvable:
    ///   • flexDirection nil — the parent isn't a flex container, so
    ///     the block-flow top-leading anchor stands (unchanged path);
    ///   • an explicit cross inset — css-position-3 §3.5 (see above);
    ///   • no align-self claim — legacy start anchor;
    ///   • child/container extent unknown — an auto-sized child cannot
    ///     be safe-checked without measuring (TODO: GeometryReader
    ///     lane); zero keeps the honest pre-lane geometry, and the
    ///     skip is visible right here rather than silently absorbed.
    /// The declared child px is read as the FRAME extent (the iOS
    /// chain's border-box status quo — SizeConfig.boxSizing docs);
    /// content-box-declared children under-count by the band (TODO).
    static func staticCrossOffset(flexDirection: FlexDirectionKeyword?,
                                  childProperties: [IRProperty],
                                  containerW: CGFloat?,
                                  containerH: CGFloat?) -> CGSize {
        // Not a flex container → static position is block-flow (no-op).
        guard let dir = flexDirection else { return .zero }
        // Cross axis: vertical for row/row-reverse (css-flexbox-1 §2),
        // horizontal for column/column-reverse.
        let vertical = (dir == .row || dir == .rowReverse)
        // Explicit inset on the axis → PositionApplier owns it (§3.5).
        guard !hasCrossInset(childProperties, vertical: vertical) else { return .zero }
        // No align-self claim → keep the legacy start anchor.
        guard let spec = resolveCross(from: childProperties) else { return .zero }
        // The alignment container extent: the positioned ancestor's
        // padding box (css-position-3 §3.1) — the same basis the
        // percent channels publish. Nil (indefinite) → honest no-op.
        guard let container = (vertical ? containerH : containerW) else { return .zero }
        // The child's declared frame extent on the cross axis; only a
        // definite px value can be safe-checked ahead of measurement.
        let size = SizeExtractor.extract(from: childProperties)
        guard case .exact(let childPx)? = (vertical ? size.height : size.width) else { return .zero }
        // The shared fallback math (pinned identically on Compose).
        let off = CGFloat(crossOffset(childPx: childPx,
                                      containerPx: Double(container),
                                      spec: spec))
        // Shift on the cross axis only — the main-axis static position
        // (justify-content derived) stays the overlay's leading anchor.
        return vertical ? CGSize(width: 0, height: off)
                        : CGSize(width: off, height: 0)
    }
}
