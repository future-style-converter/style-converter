//
//  ReplacedImageContent.swift
//  StyleEngine/images — wave-39 lane A2
//
//  The SwiftUI paint half of the replaced-element image channel: a component
//  that IS a replaced element paints its delivered raster instead of the
//  text/placeholder content every other leaf paints.
//
//  Kept beside the registry that feeds it (rather than inside the already
//  4 850-line ComponentRenderer.swift) so the whole channel — deliver, decode,
//  size, paint — reads as one folder. The renderer's only involvement is a
//  short mount branch next to the UA-widget one, which is the same shape for
//  the same reason: a replaced element's content REPLACES the normal path.
//
//  Byte-parallel twin of Kotlin images/ReplacedImageContent.kt.
//

import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

enum ReplacedImageContent {

    /// The `meta.sourceTag` values that carry replaced content, mirroring the
    /// producer's table (tools/titan/extract-fixture.mjs REPLACED_SRC_TAGS) and
    /// the feeder's corroborating copy in feed-lib.mjs.
    ///
    /// `<input type=image>` is deliberately absent: `input` is a WIDGET tag
    /// owned by the UA-widget lane, whose mount branch runs first — so
    /// admitting it here could only ever create an ordering dependency between
    /// two branches that today have none.
    static let replacedTags: Set<String> = ["img", "embed", "object", "video"]

    /// Does this component paint replaced image content? True only when the
    /// wire carries BOTH halves of the identity — a replaced `meta.sourceTag`
    /// and a non-blank `meta.attrs.src`.
    ///
    /// A tag with no src is NOT a candidate: an `<img>` with no source renders
    /// as its alt text in a browser, which is the existing text/placeholder
    /// path, so claiming it here would paint nothing where something was
    /// painted before.
    static func isCandidate(_ component: IRComponent) -> Bool {
        guard let tag = component.meta?.sourceTag?.lowercased(),
              replacedTags.contains(tag) else { return false }
        let src = component.meta?.attrs?.src?.trimmingCharacters(in: .whitespacesAndNewlines)
        return !(src ?? "").isEmpty
    }

    /// css-images-3 §4.5 `object-fit`, resolved from the element's own
    /// properties. Defaults to `.fill` — the property's initial value — so a
    /// replaced element that declares nothing behaves exactly as CSS says.
    enum Fit: String {
        case fill, contain, cover, none, scaleDown
    }

    /// Read `object-fit` off the property list. Kept here rather than in
    /// ImagesExtractor because that extractor is the identity/raw-string shape
    /// Phase 10 froze, and widening it would change a config every registered
    /// property reads. This is the one consumer that needs the KEYWORD.
    static func fit(of properties: [IRProperty]) -> Fit {
        guard let p = properties.first(where: { $0.type == "ObjectFit" }),
              let kw = ValueExtractors.extractKeyword(p.data)?.lowercased() else { return .fill }
        switch kw {
        case "contain": return .contain
        case "cover": return .cover
        case "none": return .none
        case "scale-down", "scaledown", "scale_down": return .scaleDown
        default: return .fill   // `fill` and every unrecognised keyword
        }
    }

    /// css-values-4 `<position>` → SwiftUI `Alignment`, for `object-position`.
    ///
    /// Keyword axes only. A percent/length offset falls back to `.center`, and
    /// that is a DOCUMENTED limit rather than an approximation: SwiftUI's
    /// `Alignment` has no per-axis pixel channel at this level, and inventing
    /// one by nudging with `.offset` would move the raster outside the clip the
    /// box already established. The Compose twin makes the same call for the
    /// same reason (ObjectFitExtractor.axisKeyword's own note).
    static func position(of properties: [IRProperty]) -> Alignment {
        guard let p = properties.first(where: { $0.type == "ObjectPosition" }) else { return .center }
        var horizontal: HorizontalAlignment = .center
        var vertical: VerticalAlignment = .center
        // Both the object shape ({x,y}) and a bare keyword string are accepted,
        // matching the two wire flavours the Compose extractor documents.
        var tokens: [String] = []
        if case .object(let o) = p.data {
            tokens = [axisKeyword(o["x"]), axisKeyword(o["y"])].compactMap { $0 }
        } else if let kw = ValueExtractors.extractKeyword(p.data)?.lowercased() {
            tokens = kw.split(separator: " ").map(String.init)
        }
        // Keywords bind to their OWN axis regardless of token order — the
        // css-values-4 rule that makes "bottom right" and "right bottom" the
        // same position.
        for t in tokens {
            switch t {
            case "left": horizontal = .leading
            case "right": horizontal = .trailing
            case "top": vertical = .top
            case "bottom": vertical = .bottom
            default: break  // "center" and any offset keep the axis default
            }
        }
        return Alignment(horizontal: horizontal, vertical: vertical)
    }

    /// Wave-42 W6 — the wire half of §10.4: read the min/max bounds off the
    /// finished ComponentStyle, convert each to CONTENT-BOX px, and hand them
    /// to `ReplacedBoxSizing.constrainAutoSize`. Pure (no view building) so
    /// XCTest pins the border-box band arithmetic without a device.
    ///
    /// Bound conversion (css-sizing-3 §3, mirroring the sizing chain's own
    /// semantics so the box and its content can never disagree):
    ///   * effective `content-box` (the renderer folds
    ///     SizeApplierMath.effectiveBoxSizing BEFORE this runs, so the WPT
    ///     unset-defaults-to-content-box decision is already on the config) —
    ///     the declared bound IS the content bound;
    ///   * everything else (explicit `border-box`, or unset = the chain's
    ///     border-box status quo) — content bound = declared − (padding +
    ///     used border) band on that axis via StyleBuilder's ONE band
    ///     definition (paddingAndBorderBands), floored at 0 (css-ui-3 §3.1).
    /// Only `.exact` bounds participate: `none` means unbounded and a %/em
    /// bound has no resolvable px here (documented narrowing — the chain
    /// still clamps the BOX by it at layout time). Twin of the Kotlin
    /// ReplacedImageContent.constrainedAutoContentSize.
    static func constrainedAutoContentSize(
        style: ComponentStyle,
        intrinsicWidthPx: Double,
        intrinsicHeightPx: Double,
        aspectRatio: Double?
    ) -> ReplacedBoxSizing.UsedSize {
        // Effective content-box needs no band; border-box (explicit or the
        // unset status quo) subtracts the padding+border band per axis.
        let bands: (h: CGFloat, v: CGFloat) =
            style.size.boxSizing == .contentBox ? (0, 0)
            : StyleBuilder.paddingAndBorderBands(style)
        // One declared bound → content-box px, or nil when unresolvable.
        func bound(_ v: LengthValue?, band: CGFloat) -> Double? {
            guard case .some(.exact(let px)) = v else { return nil }
            return max(px - Double(band), 0)
        }
        // The pure §10.4 table, in content-box px throughout.
        return ReplacedBoxSizing.constrainAutoSize(
            intrinsicWidthPx: intrinsicWidthPx,
            intrinsicHeightPx: intrinsicHeightPx,
            aspectRatio: aspectRatio,
            minWidthPx: bound(style.size.minWidth, band: bands.h),
            maxWidthPx: bound(style.size.maxWidth, band: bands.h),
            minHeightPx: bound(style.size.minHeight, band: bands.v),
            maxHeightPx: bound(style.size.maxHeight, band: bands.v))
    }

    /// One `<position>` axis → lowercase keyword, or nil for an offset.
    private static func axisKeyword(_ value: IRValue?) -> String? {
        guard let value else { return nil }
        if case .string(let s) = value { return s.lowercased() }
        guard case .object(let o) = value else { return nil }
        // {"type":"keyword","value":"CENTER"} — the modern parser shape.
        if case .string(let t)? = o["type"] {
            if t == "keyword", case .string(let v)? = o["value"] { return v.lowercased() }
            // Direct serialName keywords, matching PerspectiveOrigin's shape.
            if ["left", "right", "top", "bottom", "center"].contains(t) { return t }
        }
        return nil
    }
}

#if canImport(UIKit)
/// Paints one replaced element's delivered raster at the used content size.
///
/// A `View` rather than a function because the sizing modes need different
/// modifier CHAINS, and SwiftUI's `some View` makes a switch that returns
/// different chains awkward outside a `@ViewBuilder` body.
struct ReplacedImageView: View {
    let decoded: DocumentImageRegistry.DecodedImage
    let mode: ReplacedBoxSizing.Mode
    let fit: ReplacedImageContent.Fit
    let alignment: Alignment
    /// Wave-42 W6 — the §10.4-resolved used content size for the `.intrinsic`
    /// row (nil falls back to the raster's own size, keeping every
    /// pre-wave-42 construction site byte-identical). Computed by
    /// `ReplacedImageContent.constrainedAutoContentSize`, which is the
    /// identity when the wire declares no min/max bounds.
    var resolved: ReplacedBoxSizing.UsedSize? = nil

    var body: some View {
        // The §10.3.2 used CONTENT box, expressed as a frame. `.infinity`
        // fills the box the style chain already sized; an explicit intrinsic
        // number makes the surrounding box hug the raster.
        switch mode {
        case .fillBoth:
            scaled.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: alignment)
        case .widthFillsRatioHeight:
            // §10.6.2 rule 2 — the free axis follows the intrinsic ratio.
            // `aspectRatio(_:contentMode:)` is SwiftUI's own expression of it.
            scaled
                .aspectRatio(decoded.aspectRatio ?? 1, contentMode: .fit)
                .frame(maxWidth: .infinity, alignment: alignment)
        case .heightFillsRatioWidth:
            scaled
                .aspectRatio(decoded.aspectRatio ?? 1, contentMode: .fit)
                .frame(maxHeight: .infinity, alignment: alignment)
        case .intrinsic:
            // The intrinsic size run through §10.4's constraint table when
            // the caller supplied bounds (wave-42 W6); the raster's own size
            // otherwise. CSS px == pt at the capture scale, the identity
            // every geometry constant in this runtime assumes.
            scaled.frame(width: CGFloat(resolved?.widthPx ?? decoded.intrinsicWidthPx),
                         height: CGFloat(resolved?.heightPx ?? decoded.intrinsicHeightPx),
                         alignment: alignment)
        }
    }

    /// The raster with its css-images-3 §4.5 `object-fit` behaviour applied.
    @ViewBuilder
    private var scaled: some View {
        switch fit {
        case .fill:
            // Stretch to the frame, aspect ratio be damned — §5.5's initial.
            Image(uiImage: decoded.image).resizable()
        case .contain:
            Image(uiImage: decoded.image).resizable().aspectRatio(contentMode: .fit)
        case .cover:
            // `.fill` overflows the frame, so the clip is part of the
            // behaviour, not a nicety: without it the raster paints over the
            // element's siblings.
            Image(uiImage: decoded.image).resizable().aspectRatio(contentMode: .fill).clipped()
        case .none:
            // Natural size, may overflow — clipped to the content box exactly
            // as a browser clips `object-fit: none`.
            Image(uiImage: decoded.image).clipped()
        case .scaleDown:
            // "the smaller of none and contain": cap at the intrinsic size,
            // then fit. When the box is larger the cap wins (== none); when it
            // is smaller the fit wins (== contain).
            Image(uiImage: decoded.image)
                .resizable()
                .aspectRatio(contentMode: .fit)
                .frame(maxWidth: CGFloat(decoded.intrinsicWidthPx),
                       maxHeight: CGFloat(decoded.intrinsicHeightPx))
        }
    }
}
#endif
