//
//  MulticolCloneDecoration.swift
//  StyleEngine/columns — wave-46 lane Y3.
//
//  The IR-side half of css-break-3 §5.2 `box-decoration-break: clone`
//  for multicol fragmentation: reads a multicol child's declared
//  decoration bands off its IR property list — CONSUMING the borders /
//  padding trees' extractors read-only, never re-parsing their wire
//  shapes — and builds the per-fragment child copy the renderer's
//  fragment row re-renders (the iOS twin of MulticolCloneDecoration.kt;
//  the Kotlin twin needs no child rewrite because Compose re-measures
//  instead of re-composing).
//
//  STATIC-RESOLUTION CONTRACT — `bands(for:)` returns nil (the caller
//  logs + keeps slice) whenever a band is not a plain px value: em/%/calc
//  padding needs the child's font or containing block. An honest bail,
//  not a silent fallthrough; the corpus' clone family is px throughout
//  (WPT css-break background-image-004/007, borders-008).
//

// CoreGraphics for CGFloat; Foundation for the IR value shapes.
import CoreGraphics
import Foundation

// Namespacing enum — all members static + pure (MulticolMath pattern).
enum MulticolCloneDecoration {

    /// True iff `properties` declare `box-decoration-break: clone` — read
    /// through the borders tree's own extractor (BorderMiscExtractor owns
    /// the `BoxDecorationBreak` wire keyword, `slice` | `clone`).
    static func declaresClone(_ properties: [IRProperty]) -> Bool {
        BorderMiscExtractor.extract(from: properties)?.decorationBreak == .clone
    }

    /// The clone bands for a child, or nil when it is a slice child OR a
    /// band is not statically resolvable (see the file banner).
    ///
    /// Border bands are the USED widths: a side whose style is none/hidden
    /// has used width 0 (CSS 2.1 §8.5.3) — the same `hasBorder` gate
    /// BorderSideConfig applies when painting, so the bands agree with the
    /// rendered box by construction. Padding: PaddingExtractor already
    /// folds the logical longhands onto their physical sides
    /// (css-logical-1 §4.1 — horizontal-tb, the fragmenter's contract).
    static func bands(for properties: [IRProperty]) -> MulticolCloneGeometry.Bands? {
        // Slice (the css-break-3 §5.2 default) never builds bands — the
        // S-table owns it.
        guard declaresClone(properties) else { return nil }
        // USED border widths (nil config = no border declared at all).
        let b = BorderSideExtractor.extract(from: properties)
        let borderTop = b?.top.hasBorder == true ? Double(b?.top.effectiveWidth ?? 0) : 0
        let borderBottom = b?.bottom.hasBorder == true ? Double(b?.bottom.effectiveWidth ?? 0) : 0
        // Padding bands — only `.exact` px is statically known; anything
        // else bails the clone model (nil config = CSS initial 0).
        let p = PaddingExtractor.extract(from: properties)
        guard let paddingTop = staticPx(p?.top), let paddingBottom = staticPx(p?.bottom) else {
            return nil
        }
        return MulticolCloneGeometry.Bands(blockStartPx: borderTop + paddingTop,
                                           blockEndPx: borderBottom + paddingBottom)
    }

    /// The child re-declared at ONE clone fragment's size: the same IR
    /// component with its block-size longhand (`Height` / `BlockSize`)
    /// replaced by `declaredHeightPx` — the fragment's border-box
    /// block-size minus the bands under an effective `box-sizing:
    /// content-box` (css-sizing-3 §3: the declared slot is the CONTENT),
    /// verbatim under border-box; the caller (ColumnsApplier) resolves
    /// which. Every other property — borders, radius, backgrounds — is
    /// kept, so the re-rendered box is "independently wrapped" exactly as
    /// §5.2 asks. Identity-preserving on id/name/children/text/meta so the
    /// fragment row's ForEach identity and the child's own render stay
    /// stable.
    static func fragmentChild(_ child: IRComponent, declaredHeightPx: CGFloat) -> IRComponent {
        // The wire shape SizeExtractor reads for an absolute length —
        // the same `{type: length, px}` object the converter emits.
        let lengthPx = IRValue.object(["type": .string("length"),
                                       "px": .double(Double(declaredHeightPx))])
        // Drop BOTH block-size spellings (a child may carry either, and
        // SizeExtractor is last-write-wins in property order, so a kept
        // `BlockSize` after the rewrite would override it) and append the
        // clone fragment's physical height.
        let kept = child.properties.filter { $0.type != "Height" && $0.type != "BlockSize" }
        return IRComponent(id: child.id, name: child.name,
                           properties: kept + [IRProperty(type: "Height", data: lengthPx)],
                           selectors: child.selectors, media: child.media,
                           children: child.children, slot: child.slot,
                           text: child.text, pseudos: child.pseudos, meta: child.meta,
                           variables: child.variables)
    }

    /// A padding band as static px: absent padding is 0 (CSS initial
    /// value), an exact length is its px, everything else (em/rem/%/calc,
    /// auto) is not knowable here → nil.
    private static func staticPx(_ v: LengthValue?) -> Double? {
        // Absent longhand → the CSS initial value 0 (CSS 2.1 §8.4).
        guard let v = v else { return 0 }
        // An absolute length is the only statically-known band (negative
        // padding is invalid and clamps to 0, like SpacingResolver).
        if case .exact(let px) = v { return max(0, px) }
        // em / rem / % / calc / keywords: layout-time only → bail.
        return nil
    }
}
