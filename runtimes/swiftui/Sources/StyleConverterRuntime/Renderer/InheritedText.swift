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
        // css-writing-modes-4 §3.2: `writing-mode` is Inherited: yes.
        // Wave 12 — without this entry a vertical mode declared on an
        // ANCESTOR never reached a multicol container's merged list, so
        // ColumnsApplier.fragmentPlan's vertical-mode bail (and its
        // PropertyTracker.logOnce breadcrumb) could never fire.
        "WritingMode",
        // css-color-4 §3.2: `color` inherits — the currentColor chain hangs
        // off the inherited value (leaf placeholder gate documented above).
        "Color",
        // CSS 2.1 §11.2: visibility inherits (hidden parent hides children
        // unless a child redeclares `visible`).
        "Visibility",
        // css-ui-4 §5.1.1: cursor inherits — no visual analogue in a static
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
        // css-ui-4 §7.1: accent-color is Inherited: yes — a parent's
        // declaration must reach form-control descendants (wave-20 lane
        // W2: accent-color-parent-currentcolor declares it on the DIV and
        // asserts the checkbox inside turns red). The widget painter
        // reads it off the merged list (UAWidgetsResolve.accentFor).
        "AccentColor",
    ]

    /// Merge the inherited channel UNDER the component's own
    /// declarations. Pure (XCTest-pinned): own properties always win;
    /// inherited entries only fill types the component didn't declare.
    static func merge(own: [IRProperty],
                      inherited: [IRProperty]) -> [IRProperty] {
        // Fast path — nothing flowing down.
        guard !inherited.isEmpty else { return own }
        // Wave-20 (lane W2) — css-cascade-4 §7.3: `unset` on an INHERITED
        // property "acts as inherit", and `inherit` says so outright. An
        // own `Color: unset|inherit` keyword must NOT block the ancestor
        // value (accent-color-parent-currentcolor's checkbox declares
        // `color: unset` and must resolve red through the parent). Same
        // Color-only scope as the Compose twin's mergeInherited fix.
        let effectiveOwn = own.filter { p in
            // Keep everything that isn't an inherit-taking Color keyword
            // (wire shape: {"original":"unset"|"inherit"}, no srgb).
            guard p.type == "Color", p.data["srgb"] == nil,
                  let orig = p.data["original"]?.stringValue?.lowercased(),
                  orig == "unset" || orig == "inherit" else { return true }
            return false
        }
        // Types the component declares itself (these block inheritance).
        let declared = Set(effectiveOwn.map { $0.type })
        // Parent values first so the child's own later entries would win
        // in any single-pass fold; extractor loops use first-match or
        // last-match per family, so keep the own list intact and only
        // PREPEND the missing inherited entries.
        return inherited.filter { !declared.contains($0.type) } + effectiveOwn
    }

    /// The subset of a (merged) declaration list that flows to children.
    static func inheritable(from properties: [IRProperty]) -> [IRProperty] {
        // Order-preserving filter — cheap, runs once per container.
        properties.filter { inheritedTypes.contains($0.type) }
    }

    /// Lane IOS-TEXT (color channel) — css-color-4 §7.3: `currentColor`
    /// used ON the `color` property itself "is treated as `inherit`".
    /// The declaration therefore must not block the inherited Color from
    /// flowing in through `merge` (own-wins would keep the unresolvable
    /// dynamic marker, extractColor would yield no paintable value, and
    /// the leaf placeholder fell back to the contrast pick — the
    /// pixel-verified drop this fixes). Removing the own declaration IS
    /// the resolution: the ancestor's Color takes its place in the
    /// merged list, so the existing inherited-color channel that the
    /// border/outline currentColor consumers already read (they resolve
    /// against the MERGED list's "Color") sees the right value, and the
    /// republished child channel carries the resolved ancestor color.
    /// Only the `color` property is touched — currentColor on OTHER
    /// properties (border-color etc.) keeps its dynamic marker and its
    /// existing consumers. Pure — pinned by IOSTextLaneTests.
    static func resolvingCurrentColorOnColor(_ own: [IRProperty]) -> [IRProperty] {
        own.filter { prop in
            // Keep everything that isn't `color: currentColor`.
            guard prop.type == "Color" else { return true }
            // extractColor classifies the wire's dynamic marker shapes
            // ({"original":"currentColor"} and bare strings) — reuse it
            // so this filter can never drift from the color decoder.
            if case .dynamic(kind: .currentColor, raw: _) = extractColor(prop.data) {
                return false
            }
            return true
        }
    }

    /// Wave-5 gate follow-up (currentColor bottom-out) — true when the
    /// element declared `color: currentColor` but NO ancestor `Color`
    /// exists in the inherited channel to resolve it against. In that
    /// case `resolvingCurrentColorOnColor` dropped the declaration and
    /// nothing flowed in, so the merged list carries no Color at all —
    /// the device gate measured the label then falling to the
    /// 70%-alpha contrast pick (~171 blended gray on the dark stage)
    /// while web resolved the harness BODY's `color: #eee` (opaque
    /// 238,238,238) — a 0.856 diverging pair. The honest bottom-out is
    /// `defaultTextColor` below, not the contrast pick. Pure —
    /// XCTest-pinned alongside resolvingCurrentColorOnColor.
    static func currentColorBottomsOut(own: [IRProperty],
                                       inherited: [IRProperty]) -> Bool {
        // The own list must actually declare `color: currentColor`
        // (same extractColor classification as the resolver above, so
        // the two can never disagree about what "currentColor" is).
        let declaresCurrentColor = own.contains { prop in
            guard prop.type == "Color" else { return false }
            // Dynamic currentColor marker = the wire shapes extractColor
            // recognises ({"original":"currentColor"}, bare string).
            if case .dynamic(kind: .currentColor, raw: _) = extractColor(prop.data) {
                return true
            }
            return false
        }
        // …and the ancestor chain must offer nothing to resolve it
        // against (an inherited Color would have taken the dropped
        // declaration's place in the merged list — no bottom-out).
        return declaresCurrentColor && !inherited.contains { $0.type == "Color" }
    }

    /// The runtime's DEFAULT TEXT COLOR — what an undeclared-`color`
    /// element ultimately computes to on the harness stage. The
    /// web-harness stage contract is `body { color: #eee }` on the
    /// `#1a1a2e` background, so a currentColor chain with no author
    /// ancestor bottoms out in the BODY's #eee on web (opaque
    /// 238,238,238). `Color(white: 0.93)` is 237/255 — within 1/255 of
    /// #eee — and OPAQUE, unlike the 70%-alpha placeholder contrast
    /// pick (which exists to keep dev-chrome labels subtle, not to
    /// stand in for a real computed color).
    static let defaultTextColor = Color(white: 0.93)
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
