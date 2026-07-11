//
//  InheritedText.swift
//  StyleConverterRuntime — fidelity wave 1.
//
//  CSS text-property inheritance channel. The converter flattens no
//  cascade, so a parent's `font-size` / `font-family` / `text-align` /
//  `letter-spacing` … never reached child renders on iOS while the web
//  reference inherits them natively (css-cascade-4, per-property
//  "Inherited: yes"). ComponentRenderer publishes each element's merged
//  inheritable declarations through the SwiftUI Environment; children
//  merge them UNDER their own declarations (own always wins — cascade
//  specificity is irrelevant inside one element) and republish the
//  merged set for grandchildren — reproducing the transitive chain.
//
//  Mirrors the Android channel (ComponentRenderer.kt
//  LocalInheritedProperties + mergeInherited) property-for-property so
//  the two native runtimes drift together, not apart.
//

import SwiftUI

enum InheritedText {

    /// CSS-inherited properties — and ONLY these — flow from parent to
    /// child (css-cascade-4 per-property "Inherited: yes" tables).
    /// Layout/box properties (Width, Padding, Background*, per-side
    /// Border*) never inherit in CSS and are deliberately absent.
    /// TextDecorationLine is absent too: decoration PROPAGATES to inline
    /// boxes rather than inheriting, and the leading-text sibling already
    /// covers the visible case.
    ///
    /// Wave 9 (#37): the original 14-property TEXT channel grows to the
    /// full inherited set the IR carries, and `Color` — deliberately
    /// excluded since wave 1 — now INHERITS. currentColor consumers
    /// (BorderSideConfig / OutlineExtractor / text-emphasis read "Color"
    /// from the MERGED list) therefore resolve against the ancestor chain
    /// exactly like the browser. LEAF placeholder glyphs still ignore an
    /// inherited-only Color: the web reference placeholder span always
    /// sets an explicit own-`color`-or-contrast-pick that beats DOM
    /// inheritance (pixel-sampled on IT_Family) — see the leaf
    /// PlaceholderLabel gate in ComponentRenderer.contentOrPlaceholder.
    /// Mirrors Android's INHERITED_PROPERTY_TYPES entry-for-entry.
    static let inheritedTypes: Set<String> = [
        // Font family/geometry (css-fonts-4).
        "FontFamily", "FontSize", "FontWeight", "FontStyle", "FontStretch",
        // Glyph-run spacing (css-text-4).
        "LetterSpacing", "LineHeight", "WordSpacing",
        // Paragraph-level text behaviour.
        "TextAlign", "TextTransform", "TextIndent",
        // Whitespace / writing direction (css-writing-modes).
        "WhiteSpace", "TabSize", "Direction",
        // css-color-4 §7: `color` inherits — the currentColor chain hangs
        // off the inherited value (leaf placeholder gate documented above).
        "Color",
        // CSS 2.1 §11.2: visibility inherits (hidden parent hides children
        // unless a child redeclares `visible`).
        "Visibility",
        // css-ui-4 §8.1: cursor inherits — no visual analogue in a static
        // capture (no-op applier) but carried for honest wire coverage.
        "Cursor",
        // css-lists-3 §4: list-style-* inherit from list container to items
        // ("ListStyle" covers a doc carrying the unexpanded shorthand).
        "ListStyleType", "ListStylePosition", "ListStyleImage", "ListStyle",
        // css-content-3 §2: quotes inherit.
        "Quotes",
        // css-text-decor-3 §4: text-shadow inherits — leaf placeholder
        // glyphs DO show it on web (the span never resets text-shadow).
        "TextShadow",
        // css-text-4 §5: line-breaking controls all inherit. WordWrap is
        // the legacy alias the parser may emit for `word-wrap`.
        "OverflowWrap", "WordWrap", "WordBreak", "Hyphens",
        // css-text-decor-3 §3: all three text-emphasis longhands inherit
        // (emphasis-color defaults to currentColor — the inherited Color
        // above keeps that chain honest).
        "TextEmphasisStyle", "TextEmphasisColor", "TextEmphasisPosition",
        // css-ruby-1 §4: ruby annotation layout properties inherit.
        "RubyAlign", "RubyPosition", "RubyMerge", "RubyOverhang",
        // CSS 2.1 §17 table model: table-scoped inherited properties.
        "CaptionSide", "BorderCollapse", "BorderSpacing", "EmptyCells",
        // CSS 2.1 §13.3.3 fragmentation: print-only no-ops on mobile,
        // carried so the values flow honestly.
        "Orphans", "Widows",
    ]

    /// Merge the inherited channel UNDER the component's own
    /// declarations. Pure (XCTest-pinned): own properties always win;
    /// inherited entries only fill types the component didn't declare.
    static func merge(own: [IRProperty],
                      inherited: [IRProperty]) -> [IRProperty] {
        // Fast path — nothing flowing down.
        guard !inherited.isEmpty else { return own }
        // Types the component declares itself (these block inheritance).
        let declared = Set(own.map { $0.type })
        // Parent values first so the child's own later entries would win
        // in any single-pass fold; extractor loops use first-match or
        // last-match per family, so keep the own list intact and only
        // PREPEND the missing inherited entries.
        return inherited.filter { !declared.contains($0.type) } + own
    }

    /// The subset of a (merged) declaration list that flows to children.
    static func inheritable(from properties: [IRProperty]) -> [IRProperty] {
        // Order-preserving filter — cheap, runs once per container.
        properties.filter { inheritedTypes.contains($0.type) }
    }
}

// MARK: - Environment plumbing

/// Environment key carrying the parent's inheritable declarations.
private struct InheritedTextPropertiesKey: EnvironmentKey {
    /// Root default: nothing inherited.
    static let defaultValue: [IRProperty] = []
}

extension EnvironmentValues {
    /// Parent-published CSS-inherited text declarations (empty at root).
    var inheritedTextProperties: [IRProperty] {
        get { self[InheritedTextPropertiesKey.self] }
        set { self[InheritedTextPropertiesKey.self] = newValue }
    }
}
