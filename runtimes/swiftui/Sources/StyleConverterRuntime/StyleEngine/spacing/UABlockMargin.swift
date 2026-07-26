//
//  UABlockMargin.swift
//  StyleEngine/spacing — TITAN WPT Round 4 (GAP 1: UA default margins).
//
//  Native platforms carry NO user-agent stylesheet: our composed WPT
//  capture stacks each test's block roots FLUSH, while the Chromium
//  browser-ref (tools/titan/capture-browser-ref.mjs) renders the reference
//  page with its UA sheet intact — so a `<p>` keeps its 1em (16px @16px
//  root) block margins and adjacent bars COLLAPSE to a real ~16px gap.
//  A 10-`<p>` test (css-color/background-color-hsl-001, …) therefore
//  scored far below the ref (bars flush, no gaps).
//
//  This is the native EMULATION of the web fix (apps/web-harness's
//  `body.wpt-composed-mode [data-component-id] { margin: revert }`, which
//  rolls the margin cascade back to the UA origin): we reproduce the exact
//  VERTICAL block margins the CSS 2.1 UA sheet (§ Appendix D.2) declares,
//  resolved to absolute px at the element's own font size (the same
//  numbers Chromium's UA sheet produces at a 16px root) and inserted as
//  spacing between the composed roots in ComposedCaptureCanvas.
//
//  Two CSS rules are honoured so the composed layout matches the ref:
//    • An AUTHOR/IR-declared margin WINS over the UA default (the UA origin
//      is the lowest-priority cascade origin — css-cascade-4 §6.1). The IR
//      carries author margins as Margin* longhands the runtime's
//      MarginApplier already paints, so this helper DEFERS (contributes 0)
//      on any edge the IR declares, mirroring web's inline-margin priority.
//    • Adjacent vertical margins COLLAPSE (CSS 2.1 §8.3.1): two 16px `<p>`
//      margins produce a 16px gap, not 32px. `stackedSpacing` folds the
//      per-root margins with the positive-margin `max` collapse rule.
//
//  Everything here is a PURE function of the tag string + property list, so
//  UABlockMarginTests pins the classification device-free (same pattern as
//  ComponentRenderer.isOutOfFlow / .suppressesNamePlaceholder). ONLY the
//  composed WPT canvas consumes it — the bundled 327-pair baseline and the
//  product renderer never call in, so both stay byte-identical.
//

import CoreGraphics

// public: ComposedCaptureCanvas (apps/ios-harness) imports the runtime and
// calls these to space its stacked roots; the runtime test target pins them.
public enum UABlockMargin {

    /// UA default VERTICAL (block) margins for a block-level tag, in px,
    /// resolved at a 16px root font — the exact values the browser-ref's
    /// Chromium UA sheet lays out (each `<hN>`'s em resolves against its
    /// OWN font-size, e.g. h1's 0.67em over its 2em/32px face → ~21px).
    /// Horizontal UA margins (blockquote/figure 40px left+right) are
    /// omitted: they don't affect the composed VERTICAL stack's geometry.
    /// Unknown / flow-container tags (div, section, article, header,
    /// footer, main, nav, aside, …) get 0 — the UA sheet gives them none.
    public static func vertical(forTag tag: String?) -> (top: CGFloat, bottom: CGFloat) {
        switch (tag ?? "").lowercased() {
        // p: margin: 1em 0 → 16px @16px.
        case "p":                     return (16, 16)
        // h1: margin: 0.67em 0 over a 2em (32px) face → 0.67×32 ≈ 21px.
        case "h1":                    return (21, 21)
        // h2: margin: 0.83em 0 over a 1.5em (24px) face → 0.83×24 ≈ 19px.
        case "h2":                    return (19, 19)
        // h3: margin: 1.0em 0 over a 1.17em (~18.7px) face → ~16px (the
        // task-pinned value the browser-ref shows at this root size).
        case "h3":                    return (16, 16)
        // h4: margin: 1.33em 0 over a 1em (16px) face → 1.33×16 ≈ 21px.
        case "h4":                    return (21, 21)
        // h5: margin: 1.67em 0 over a 0.83em (~13.3px) face → ~27px
        // (task-pinned; matches the ref's rendered h5 gap).
        case "h5":                    return (27, 27)
        // h6: margin: 2.33em 0 over a 0.67em (~10.7px) face → ~37px
        // (task-pinned; matches the ref's rendered h6 gap).
        case "h6":                    return (37, 37)
        // ul / ol: margin-block 1em → 16px @16px (list left padding is
        // horizontal, so it's irrelevant to the vertical stack).
        case "ul", "ol":              return (16, 16)
        // blockquote: margin: 1em 40px → 16px vertical (40px l/r dropped).
        case "blockquote":            return (16, 16)
        // pre: margin: 1em 0 → 16px @16px.
        case "pre":                   return (16, 16)
        // figure: margin: 1em 40px → 16px vertical (40px l/r dropped).
        case "figure":                return (16, 16)
        // div, section, article, header, footer, main, nav, aside, and any
        // unrecognised tag: the UA sheet declares no block margin.
        default:                      return (0, 0)
        }
    }

    /// Canonical block-margin-TOP longhands (physical + LTR-TB logical).
    /// The converter expands the `margin` shorthand into these longhands,
    /// so presence of any of them means the IR declared this edge (and the
    /// runtime's MarginApplier already paints it) → the UA default defers.
    private static let topNames: Set<String> = ["MarginTop", "MarginBlockStart"]
    /// Canonical block-margin-BOTTOM longhands (physical + LTR-TB logical).
    private static let bottomNames: Set<String> = ["MarginBottom", "MarginBlockEnd"]

    /// True when the IR declares a TOP block margin (physical or logical) —
    /// in which case the UA default must NOT be added for that edge.
    public static func declaresBlockMarginTop(_ properties: [IRProperty]) -> Bool {
        properties.contains { topNames.contains($0.type) }
    }

    /// True when the IR declares a BOTTOM block margin (physical or logical).
    public static func declaresBlockMarginBottom(_ properties: [IRProperty]) -> Bool {
        properties.contains { bottomNames.contains($0.type) }
    }

    /// The EFFECTIVE UA vertical margins for one root: the tag's UA default
    /// on each edge the IR leaves undeclared, or 0 on an edge the IR sets
    /// (author > UA — the runtime's MarginApplier owns those edges). This
    /// is the per-edge deferral the browser performs when an author
    /// `margin-top` overrides only the UA `margin`'s top longhand.
    public static func effectiveVertical(tag: String?,
                                         properties: [IRProperty]) -> (top: CGFloat, bottom: CGFloat) {
        let ua = vertical(forTag: tag)
        let top = declaresBlockMarginTop(properties) ? 0 : ua.top
        let bottom = declaresBlockMarginBottom(properties) ? 0 : ua.bottom
        return (top, bottom)
    }

    /// Adjacent vertical-margin collapse for two NON-NEGATIVE margins
    /// (CSS 2.1 §8.3.1): the used gap is the LARGER of the two, not their
    /// sum. UA margins are all ≥ 0; author-declared negative margins are
    /// deferred to the runtime (they never reach this fold), so the simple
    /// `max` is exact for every edge this helper owns.
    public static func collapsed(_ a: CGFloat, _ b: CGFloat) -> CGFloat {
        max(a, b)
    }

    /// Fold per-root effective (top,bottom) block margins — given in
    /// document order — into the vertical space to place ABOVE each root
    /// (index-aligned `leading`) plus the space BELOW the last root
    /// (`trailing`). Between two roots the touching margins COLLAPSE
    /// (`collapsed`); the FIRST top and LAST bottom edges do NOT collapse
    /// because the composed canvas's 16px padding blocks parent↔child
    /// collapse — exactly like the browser-ref's padded `<body>` (a padded
    /// container keeps its first child's top margin inside it, CSS 2.1
    /// §8.3.1). Empty input → empty leading + 0 trailing.
    public static func stackedSpacing(_ margins: [(top: CGFloat, bottom: CGFloat)])
        -> (leading: [CGFloat], trailing: CGFloat) {
        // Delegate to the transparency-aware plan fold (wave-19 follow-up,
        // ComposedRootStack.swift) with every entry OPAQUE — for all-opaque
        // stacks the two rules are provably identical (each opaque root
        // closes the adjoining set: first top preserved, interior gaps
        // max(prev bottom, own top), last bottom preserved), so every
        // Round 4 pin stays byte-stable while §8.3.1 lives in ONE place.
        stackedSpacing(plans: margins.map {
            RootStackMargin(top: $0.top, bottom: $0.bottom, stripDeclared: false)
        })
    }
}
