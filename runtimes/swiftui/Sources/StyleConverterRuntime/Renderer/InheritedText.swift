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

    /// CSS-inherited text properties — and ONLY these — flow from parent
    /// to child (css-cascade-4 inheritance table). Layout/box properties
    /// (Width, Padding, Background*, Border*) never inherit in CSS and
    /// are deliberately absent. TextDecorationLine is absent too:
    /// decoration PROPAGATES to inline boxes rather than inheriting, and
    /// the leading-text sibling already covers the visible case.
    ///
    /// `Color` is deliberately absent even though CSS inherits it: the
    /// web harness placeholder always sets an explicit contrast-picked
    /// `color` (bg-luminance flip), and the iOS PlaceholderLabel
    /// implements the same pick — inheriting Color here would push the
    /// platforms APART on every light-card fixture, not together.
    static let inheritedTypes: Set<String> = [
        // Font family/geometry.
        "FontFamily", "FontSize", "FontWeight", "FontStyle", "FontStretch",
        // Glyph-run spacing.
        "LetterSpacing", "LineHeight", "WordSpacing",
        // Paragraph-level text behaviour.
        "TextAlign", "TextTransform", "TextIndent",
        // Whitespace / writing direction.
        "WhiteSpace", "TabSize", "Direction",
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
