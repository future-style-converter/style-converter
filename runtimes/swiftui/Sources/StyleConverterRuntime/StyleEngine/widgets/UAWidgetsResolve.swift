//
//  UAWidgetsResolve.swift
//  StyleEngine/widgets — lane W2 (wave 20).
//
//  The PURE identity half of the UA-widget replicas: (meta.sourceTag,
//  meta.attrs, Appearance, AccentColor, text, options) →
//  UAWidgetsGeometry.Spec. Byte-parallel twin of the Kotlin
//  UAWidgetsResolve (runtimes/compose .../widgets/); both unit suites
//  pin the same decision table so widget IDENTITY can never diverge
//  across the natives.
//

import Foundation

enum UAWidgetsResolve {

    /// css-ui-4 §7 (appearance-switching): does this element paint its
    /// NATIVE widget look? `none` → no (devolved to a plain styled box);
    /// `auto` and every compat alias ("X is an alias to auto" per the
    /// appearance-*-001 assertions) → yes. Missing Appearance → yes: the
    /// UA sheet gives widget tags `appearance: auto` and the IR carries
    /// no UA sheet. Global keywords: `initial` computes to the initial
    /// value `none`; inherit/unset/revert land back at auto for a bare
    /// widget (no styled ancestor in these tests).
    static func appearanceIsNative(_ properties: [IRProperty]) -> Bool {
        // The cascaded Appearance winner (converter emits post-cascade).
        guard let data = properties.first(where: { $0.type == "Appearance" })?.data,
              // AppearanceValue serializes as {"type":"<value>"} (+ payload
              // for keyword/raw) — pinned against the wave-19 live wire.
              let t = data["type"]?.stringValue else {
            return true // no declaration → UA-sheet auto → native
        }
        switch t {
        case "none": return false // devolve: no widget chrome
        // `initial` → the property's initial value, css-ui-4 pins `none`.
        case "keyword": return data["keyword"]?.stringValue != "initial"
        default: return true // auto, every compat alias, raw → native
        }
    }

    /// Widget identity from tag + attrs (html.css UA widget mapping).
    /// nil for non-widget elements (a, div, span …) — those keep the
    /// normal text/placeholder content path.
    static func kindFor(tag: String?, attrs: IRAttrs?) -> UAWidgetsGeometry.Kind? {
        switch tag {
        case "button": return .button
        case "textarea": return .textarea
        case "meter": return .meter
        case "progress": return .progress
        // select forks on the `multiple` attribute (menulist vs listbox).
        case "select": return attrs?.multiple == true ? .listbox : .menulist
        case "input":
            switch attrs?.type {
            // Absent type attribute defaults to "text" (HTML spec).
            case nil, "text", "search": return .textfield
            case "button", "submit", "reset": return .button
            case "checkbox": return .checkbox
            case "radio": return .radio
            case "range": return .range
            case "color": return .color
            // The three NON-widget input types (appearance-auto-input-
            // non-widget-001: their default widget type is none) — they
            // paint special content regardless of the appearance value.
            case "hidden": return .hidden
            case "image": return .image
            case "file": return .file
            default: return .textfield // unknown type → text (HTML fallback)
            }
        default: return nil // not a widget tag
        }
    }

    /// The used accent-color (css-ui-4 §7.1 widget-accent): explicit
    /// AccentColor wins; `currentColor` resolves against the element's
    /// used `color` (merged list — inheritance already folded in);
    /// absent → the Chromium default #0075FF. Packed ARGB for the plan.
    static func accentFor(_ properties: [IRProperty]) -> UInt32 {
        guard let data = properties.first(where: { $0.type == "AccentColor" })?.data else {
            return UAWidgetsGeometry.ACCENT // no declaration → UA default
        }
        // currentColor marker: {"original":"currentColor"}, no srgb —
        // resolve against the element's own used color.
        if case .dynamic(kind: .currentColor, raw: _) = extractColor(data) {
            // Used color = merged `Color`; the WPT page default ink is
            // CanvasText black when no ancestor colors it.
            if let colorData = properties.first(where: { $0.type == "Color" })?.data,
               case .srgb(let r, let g, let b, let a) = extractColor(colorData) {
                return packARGB(r: r, g: g, b: b, a: a)
            }
            return UAWidgetsGeometry.BLACK
        }
        // Concrete color (srgb payload) → packed ARGB; unparsable (auto,
        // raw var()) falls back to the UA default rather than dropping ink.
        if case .srgb(let r, let g, let b, let a) = extractColor(data) {
            return packARGB(r: r, g: g, b: b, a: a)
        }
        return UAWidgetsGeometry.ACCENT
    }

    /// sRGB floats → packed ARGB (0..255 rounding, the Compose twin's
    /// Color.toArgb convention) so pin strings match across natives.
    static func packARGB(r: Double, g: Double, b: Double, a: Double) -> UInt32 {
        // Channel clamp + round mirrors android.graphics packing exactly.
        func ch(_ v: Double) -> UInt32 { UInt32((v.clamped01 * 255).rounded()) }
        return ch(a) << 24 | ch(r) << 16 | ch(g) << 8 | ch(b)
    }

    /// Full resolve: nil unless (a) the tag+attrs identify a widget AND
    /// (b) appearance computes to a native look (non-widget input types
    /// skip the appearance gate — no widget to devolve). The caller (the
    /// WPT-capture mount hook in ComponentRenderer) paints the Spec via
    /// UAWidgetView and skips the text/children content path.
    /// `properties` is the MERGED list (inheritance folded) — accent and
    /// color read from it.
    static func resolve(component: IRComponent, properties: [IRProperty]) -> UAWidgetsGeometry.Spec? {
        let attrs = component.meta?.attrs
        guard let kind = kindFor(tag: component.meta?.sourceTag?.lowercased(), attrs: attrs) else {
            return nil // not a widget tag → normal content path
        }
        // Appearance gate — except the three specials whose painting is
        // not appearance-controlled (see kindFor).
        let special = kind == .hidden || kind == .image || kind == .file
        if !special && !appearanceIsNative(properties) { return nil }
        // Value fraction for the position-taking widgets (twin table).
        let fraction: CGFloat?
        switch kind {
        case .range:
            // HTML defaults min 0 / max 100 / value midpoint; range value
            // rides the wire as a STRING (contract) → numeric parse here.
            // HTML §2.3.5.1 valid floating-point numbers exclude NaN and
            // Infinity, but Double.init(String) accepts them — gate on
            // finiteness or the thumb geometry itself goes NaN (wave-20
            // skeptic fix; the Kotlin twin carries the identical gate).
            let min = attrs?.min ?? 0, max = attrs?.max ?? 100
            if let v = attrs?.value.flatMap(Double.init), v.isFinite, max > min {
                fraction = CGFloat(Swift.min(Swift.max((v - min) / (max - min), 0), 1))
            } else {
                fraction = 0.5 // no/invalid value → the UA midpoint
            }
        case .progress:
            // Numeric wire value / max (default 1); absent value →
            // indeterminate → nil (track-only painting).
            fraction = attrs?.valueNumber.map { v in
                // HTML §4.10.13: max must be > 0 — a zero/negative wire
                // max falls back to the default 1.0 (0.0 divided the
                // fraction to NaN geometry; wave-20 skeptic fix, twin in
                // the Kotlin resolve).
                let m = (attrs?.max).flatMap { $0 > 0 ? $0 : nil } ?? 1
                return CGFloat(Swift.min(Swift.max(v / m, 0), 1))
            }
        case .meter:
            // Value within [min, max] (defaults 0..1); absent → min.
            let min = attrs?.min ?? 0, max = attrs?.max ?? 1
            let v = attrs?.valueNumber ?? min
            fraction = max > min ? CGFloat(Swift.min(Swift.max((v - min) / (max - min), 0), 1)) : 0
        default:
            fraction = nil // not a fraction-taking widget
        }
        // Option rows (select children carrying meta.sourceTag "option").
        let optionKids = (component.children ?? [])
            .filter { $0.meta?.sourceTag?.lowercased() == "option" }
        let options = optionKids.map { $0.text ?? "" }
        // Visible label per widget family (twin table — see Kotlin doc).
        let label: String
        switch kind {
        // <button> label is its element text; input buttons the value attr.
        case .button: label = component.text ?? attrs?.value ?? ""
        case .textfield: label = attrs?.value ?? ""
        case .textarea: label = component.text ?? ""
        // Menulist shows the SELECTED option (first as UA default).
        case .menulist:
            label = optionKids.first(where: { $0.meta?.attrs?.selected == true })?.text
                ?? options.first ?? ""
        // Image input: alt text, falling back to value (ref shows "def").
        case .image: label = attrs?.alt ?? attrs?.value ?? ""
        default: label = "" // chrome-only widgets carry no label
        }
        return UAWidgetsGeometry.Spec(
            kind: kind,
            checked: attrs?.checked == true,
            fraction: fraction,
            label: label,
            options: options,
            accent: accentFor(properties)
        )
    }

    /// Wave-38 lane N1 — the IR-reading half of the block-context line box
    /// (the arithmetic lives in the byte-parallel `UAWidgetBlockLine`).
    /// Reads the three wire facts that solve it off the SAME merged
    /// property list `resolve` uses, so the lead and the replica can never
    /// disagree about which element they describe:
    ///  • an explicit `Height` (post-load-extracted computed style) → the
    ///    conservative gate, no lead at all;
    ///  • `MarginTop` / `MarginBottom` in absolute px — already applied by
    ///    the renderer's own margin modifier, so they are subtracted from
    ///    the table's lead instead of being added twice.
    /// Twin: Kotlin UAWidgetsResolve.blockLead(component).
    static func blockLead(component: IRComponent,
                          properties: [IRProperty]) -> UAWidgetBlockLine.Lead? {
        UAWidgetBlockLine.leadFor(
            tag: component.meta?.sourceTag?.lowercased(),
            typeAttr: component.meta?.attrs?.type,
            multiple: component.meta?.attrs?.multiple == true,
            // Any Height declaration counts — auto/percentage/calc pin the
            // box just as firmly as a px value once the renderer honours
            // them, and this gate is deliberately the conservative one.
            hasWireHeight: properties.contains { $0.type == "Height" },
            wireMarginTopPx: wireMarginPx(properties, "MarginTop"),
            wireMarginBottomPx: wireMarginPx(properties, "MarginBottom"),
            horizontalWritingMode: isHorizontalWritingMode(properties))
    }

    /// True when this element's block axis is the y axis — the only case the
    /// ref-probed table describes (see the axis gate in
    /// `UAWidgetBlockLine.leadFor`).
    ///
    /// `writing-mode` INHERITS (css-writing-modes-4 §3.1) and is carried in
    /// the renderer's inherited-property set, so the declaration on an
    /// ancestor wrapper — which is where css-writing-modes' forms/* tests
    /// put it — reaches this merged list. Read as the raw wire keyword
    /// rather than through WritingModeExtractor so the Swift and Kotlin
    /// halves stay byte-parallel with no per-platform config type between
    /// them. Absent ⇒ horizontal-tb, the CSS initial value.
    private static func isHorizontalWritingMode(_ properties: [IRProperty]) -> Bool {
        guard let data = properties.first(where: { $0.type == "WritingMode" })?.data
        else { return true }
        // A non-string payload is unreadable here — assume the initial value.
        guard let keyword = data.stringValue else { return true }
        return keyword.caseInsensitiveCompare("HORIZONTAL_TB") == .orderedSame
            || keyword.caseInsensitiveCompare("horizontal-tb") == .orderedSame
    }

    /// One margin longhand in ABSOLUTE px, or 0 when the wire carries no
    /// such declaration (0 is exactly what the renderer then applies).
    /// Non-absolute shapes (`auto`, `%`, unresolved `calc()`) also read 0:
    /// the runtime cannot know their used value here, and under-subtracting
    /// keeps the lead at the ref value rather than silently shrinking it.
    private static func wireMarginPx(_ properties: [IRProperty], _ type: String) -> Double {
        guard let data = properties.first(where: { $0.type == type })?.data else { return 0 }
        if case .exact(let px) = extractLength(data) { return px }
        return 0
    }
}

// Small clamp helper local to this file (kept private to avoid polluting
// the runtime's Double surface).
private extension Double {
    /// Clamp into [0, 1] — sRGB channel domain before 8-bit packing.
    var clamped01: Double { Swift.min(Swift.max(self, 0), 1) }
}
