//
//  ListMarkerRow.swift
//  StyleEngine/lists — wave 27, lane NMARK (B-RC5 + B-RC6).
//
//  The two layout constants ComponentRenderer's marker branch builds its
//  HStack from, with the measurement and the spec citation that fix each.
//  They live here rather than inline in the 3970-line renderer so the
//  argument is readable and the values are unit-pinnable
//  (ListMarkerRowTests) without rasterizing.
//
//  ## The defect these constants repair
//  On the wave27-gate arabic-indic capture
//  (tools/titan/runs/wave27-gate/sections/css-counter-styles/
//   ios-screenshots/wpt__css-counter-styles__arabic-indic__
//   css3-counter-styles-101.png) iOS painted NO marker on any of the
//  nine items and laid each one at a 67px pitch against its baked
//  `height: 31.25px` — while web (browser-ref) and Compose both painted
//  a marker at the reference 31.25px pitch.
//
//  ## What was actually wrong (measured, not assumed)
//  The reported hypothesis was that the item's only child — an
//  absolutely positioned text run — was contributing to the item's
//  height. It is NOT: rendering the SAME tree with
//  `list-style-type: none` (marker suppressed, abspos child untouched)
//  gives a 32px pitch, i.e. exactly the declared height. Both symptoms
//  come from the MARKER ROW instead, and from two separate properties
//  of it:
//
//  1. The `<ol>`'s `padding-left: 8em` at the inherited 25px font is
//     200px, and the composed canvas is 358px wide — leaving EXACTLY
//     the 158px the items declare as their width. The marker's own
//     width plus the row gap therefore over-constrained the HStack, and
//     SwiftUI resolved that by compressing the only flexible subview
//     (the marker `Text`) to ~zero width. It painted nothing, and its
//     glyphs wrapped onto extra lines, stretching the row. Repaired at
//     the call site with `.fixedSize()`: the ::marker box is
//     inline-level shrink-to-fit content (css-lists-3 §3.2), sized by
//     its glyphs — never by the leftover space in its item.
//
//  2. `.firstTextBaseline` alignment applied UNCONDITIONALLY. This
//     item's principal box holds no in-flow text of its own, so it
//     exposes no first baseline, and SwiftUI's documented fallback for a
//     view without one is its BOTTOM edge. The marker's whole ascent
//     therefore hung ABOVE the item and its descent BELOW, adding the
//     marker's descent to every row even in the cases where the marker
//     did fit (measured: 37px pitch for a 31.25px item). The repair is
//     NOT "always top": for an item that DOES have text, sharing a
//     baseline with the marker is exactly right (css-lists-3 §3.2 — the
//     marker is the item's first inline box, and inline boxes align on
//     the line's baseline). So the alignment is now CHOSEN per item, by
//     whether the item exposes a first text baseline at all.
//
//  ## Why this keeps the two natives from drifting
//  Compose's twin (ComponentRenderer.RenderListItemMarker) aligns its
//  marker with `Modifier.alignByBaseline()`, and Compose's row layout
//  degrades an absent `FirstBaseline` to the cross-axis origin — i.e. to
//  TOP. `rowAlignment(itemExposesTextBaseline:)` below reproduces that
//  same two-case rule explicitly, so both runtimes top-align exactly the
//  baseline-less items and baseline-align exactly the rest.
//
//  NOT gated on `wptCaptureMode`, deliberately — the branch it changes
//  cannot reach a committed baseline. It needs a source tag on BOTH the
//  container (`ol`/`ul`/`menu`/`dir`) and the item (`li`), and:
//    • no file under `fixtures/` carries `"sourceTag"` at all, and every
//      one of the 460 files carrying its v1 spelling `"_tag"` lives under
//      `fixtures/wpt/` — the TITAN corpus (the `_section-*.json` inputs
//      AND the per-test files beside them; 62 of those carry
//      `"_tag":"li"`, in css-lists 27 / css-contain 18 /
//      css-counter-styles 12 / css-ui 2). Nothing under
//      `fixtures/properties/` or `fixtures/components/` has a tag at all;
//    • those TITAN files drive no baseline. Every name under
//      `tools/visual/baseline/` comes from `fixtures/properties/` and
//      `fixtures/components/` (AR_*, Avatar_*, BorderRadius_*, …); the
//      intersection with `_section-css-lists.json`'s component names is
//      empty, and `fixtures/properties/lists/longtail.json` — the one
//      dark-stage list fixture — declares `list-style-*` on plain boxes
//      with no tag and no `li` children, so it never builds a marker row.
//  A capture gate would therefore protect nothing and add a second,
//  untested code path.
//

import SwiftUI

enum ListMarkerRow {

    /// Cross-axis alignment for the `[marker, item]` row — see point 2 in
    /// the file header.
    ///
    /// - `.firstTextBaseline` when the item CAN expose one: the marker is
    ///   the item's first inline box and shares the line's baseline
    ///   (css-lists-3 §3.2). This is the pre-wave-27 behaviour, kept
    ///   verbatim for every item it was ever correct for.
    /// - `.top` otherwise: with no baseline to share, SwiftUI would fall
    ///   back to the item's BOTTOM edge and hang the marker's ascent
    ///   outside the item's box, stretching the row. Top alignment starts
    ///   the marker at the item's content-box origin so a marker no
    ///   taller than the item cannot grow it (css-sizing-3 §5.1: a
    ///   definite `height` IS the used height — larger content overflows
    ///   rather than stretching the box).
    static func rowAlignment(itemExposesTextBaseline: Bool) -> VerticalAlignment {
        itemExposesTextBaseline ? .firstTextBaseline : .top
    }

    /// Does this list item render any IN-FLOW text, i.e. will its view
    /// carry a first text baseline for the row to align against?
    ///
    /// Out-of-flow descendants are excluded deliberately and are the
    /// whole point of the wave-27 repair: an absolutely positioned run
    /// paints through the parent's positioned OVERLAY (css-position-3
    /// §2.1 takes it out of flow), so it contributes neither height nor a
    /// baseline to the item's principal box — which is exactly the shape
    /// the bidi bake produces for every counter-style item.
    ///
    /// Recursion is over in-flow children only, so it terminates on the
    /// same finite subtree the flow container actually lays out.
    static func itemExposesTextBaseline(_ item: IRComponent) -> Bool {
        // The item's own text run renders as a leading in-flow label.
        if item.text?.isEmpty == false { return true }
        // Otherwise any in-flow descendant's text supplies the baseline.
        return (item.children ?? []).contains { child in
            !ComponentRenderer.isOutOfFlow(child) && itemExposesTextBaseline(child)
        }
    }

    /// Inline gap between the marker box and the item's principal box.
    ///
    /// 4pt is the pre-wave-27 constant, carried over UNCHANGED — this
    /// lane repairs which box gets compressed and how the two align, not
    /// the marker padding. The real value is UA-defined and per-counter-
    /// style (css-lists-3 §3.2's marker padding), which is still the
    /// deferred B-RC3 part 3 work documented at the call site.
    static let gapPt: CGFloat = 4
}
