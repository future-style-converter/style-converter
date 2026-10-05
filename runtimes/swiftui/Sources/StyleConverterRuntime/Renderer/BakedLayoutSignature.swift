//
//  BakedLayoutSignature.swift
//  StyleConverterRuntime — wave 52 (lane L3 · failure-ink, fix F1).
//
//  The POST-LOAD-EXTRACTION SIGNATURE: one predicate for every native seam
//  that must decline to re-lay-out a box whose wire already carries the
//  browser's USED layout. Byte-parallel twin of Compose
//  core/renderer/BakedLayoutSignature.kt — the two rule tables are pinned
//  together by VerticalBlockFlowSeamGuardTest(s) over the VERBATIM
//  wave51-fix payloads (authored squares → false, baked box → true).
//
//  ## The measured defect
//  `ComponentRenderer.verticalBlockFlowZ2()` (the wave-47 lane-Z2 seam that
//  stacks a vertical-writing-mode container's block children horizontally,
//  css-writing-modes-4 §6) declined any container with a child declaring
//  BOTH Width and Height — the wave-47 "baked-layout" heuristic. That is
//  NOT the extractor's signature: an ordinary author rule such as
//  `.square { width: 50px; height: 50px }` trips it, so on
//  css-writing-modes/flexbox_align-items-stretch-writing-modes the seam kept
//  the frozen VStack for the two vertical-writing-mode flex items: their
//  squares stacked VERTICALLY, each item came out 50px wide instead of 100,
//  and the test's own RED failure ink (a 250×100 `z-index:-1` probe) showed
//  through the right 100px — wave51-fix ios f 0.999 (colorFailed) against
//  a ref that is one 250×100 green rectangle.
//
//  ## The real signature (what the extractor writes on EVERY box it bakes)
//  `tools/titan/post-load-extract.mjs` (POST_LOAD_PROPERTIES, lines 160-181)
//  writes `width` + `height` + `box-sizing` ("rides along so the basis is
//  always explicit"), the four `margin-*`, the four `padding-*`, and the
//  four `border-*-{width,style,color}` — IR types Width, Height, BoxSizing,
//  Padding{Top,…}, Border{Top,…}{Width,Style,Color}. Census over the 1435
//  wave51-fix per-test IR docs (tools/titan/results/wave52-failure-ink/
//  census.json, F1): every Width+Height child of the 9 post-load-extracted
//  carrier docs (12 containers: the anchor-position-multicol family ×7,
//  -colspan-003, input-range-zero-inline-size ×4) carries all of BoxSizing +
//  PaddingTop + BorderTopStyle; every Width+Height child of the 5 authored
//  carrier docs (8 containers) carries NONE of the three. The used-mode
//  pass (census.json f1Used) adds authored table cells that DO carry
//  padding and a border style — never BoxSizing, the load-bearing conjunct.
//  The conjunction below separates the populations with a margin:
//  BoxSizing AND at least one decoration-band longhand, so an authored
//  `box-sizing: border-box` alone can never re-trip a guard.
//

import Foundation

enum BakedLayoutSignature {

    /// The used physical inline size the extractor bakes (`width`).
    private static let width = "Width"

    /// The used physical block size the extractor bakes (`height`).
    private static let height = "Height"

    /// The sizing basis that "rides along" with every baked size pair.
    private static let boxSizing = "BoxSizing"

    /// One of the four baked padding bands (top is the fixed probe side).
    private static let paddingTop = "PaddingTop"

    /// One of the twelve baked border longhands (style is never absent).
    private static let borderTopStyle = "BorderTopStyle"

    /// True iff `properties` carries the post-load extractor's signature —
    /// a box whose used physical layout is ALREADY on the wire, so a native
    /// layout seam must keep its frozen render rather than lay it out again.
    ///
    /// Presence is the signal, never the value: the extractor writes the
    /// longhands whatever they resolve to (`padding-top: 0px`,
    /// `border-top-style: none` are on every baked box), while an authored
    /// document only carries what the author declared.
    static func bakedPhysicalBox(_ properties: [IRProperty]) -> Bool {
        // One pass over the declaration list — the presence set of IR types.
        let types = Set(properties.map(\.type))
        // Width AND Height (the wave-47 half of the rule) …
        return types.contains(width) && types.contains(height)
            // … AND the sizing basis the extractor always writes with them …
            && types.contains(boxSizing)
            // … AND at least one decoration band (padding or border style).
            && (types.contains(paddingTop) || types.contains(borderTopStyle))
    }
}
