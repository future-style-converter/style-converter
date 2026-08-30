//
//  CurrentColorBackground.swift
//  StyleEngine/color — wave-49 lane A5.
//
//  `background-color: currentcolor` resolution (css-color-4 §6.4).
//
//  WHY THIS FILE EXISTS
//  §6.4 defines `currentcolor` as "the value of the `color` property on the
//  SAME element" — the element's own computed colour, not its parent's. The
//  wire ships the keyword srgb-less (`{"original":"currentColor"}`, verbatim
//  in tools/titan/runs/wave48-final/sections/css-color/per-test-ir/
//  wpt__css-color__currentcolor-001.json), so `extractColor` classifies it
//  `.dynamic(.currentColor, …)` and `ColorValue.toSwiftUIColor()` returns nil
//  for it — ColorApplier.fillView then skips the paint entirely. Nothing was
//  painted, the ancestor's own `background-color: red` showed through, and the
//  runtime rendered the test's FAIL square: css-color/currentcolor-001 scored
//  ssim 0.9988 with `colorFailed` and labDeltaE.mean 11.24 on the wave-48 gate
//  while the reference is a plain green square.
//
//  THE RESOLUTION TARGET
//  `ColorConfig.foreground` is read from the `Color` entry of the list
//  StyleBuilder hands the extractor, and that list is already merged —
//  ComponentRenderer runs `InheritedText.merge(own: resolvingCurrentColorOnColor
//  (own), inherited:)` before styling, so "own declaration, else the nearest
//  ancestor's" is exactly what `foreground` holds. That is the computed value
//  §6.4 asks for. Byte-parallel twin of the Compose
//  `color/CurrentColorBackground.kt`.
//

import Foundation

enum CurrentColorBackground {

    /// Substitute the element's own computed colour for a `currentcolor`
    /// background, and evaluate a `color-mix()` that contains one. Every
    /// other `ColorValue` — static sRGB, and the remaining dynamic flavours
    /// — is returned untouched, so the change can only affect cases that
    /// previously painted nothing.
    static func resolved(_ config: ColorConfig) -> ColorConfig {
        // css-color-5 §3 — a `color-mix()` the static decoder refused. §6.4
        // makes `currentcolor` resolve against the element the mix is USED
        // on, so the element's own computed colour is the substitution and
        // this is the only place that has it. Nil (an unsupported space, an
        // unreadable endpoint, invalid percentages) leaves the marker in
        // place, i.e. exactly the previous unpainted behaviour.
        if case .dynamic(kind: .colorMix, raw: let raw)? = config.background,
           let call = StaticColorMix.payload(raw),
           let mixed = StaticColorMix.resolve(call, currentColor: ownSrgb(config)) {
            var out = config
            out.background = mixed
            return out
        }
        // Only the currentcolor marker is ours beyond that. `.srgb` is
        // already paintable; `.lightDark` / `.relative` / `.varFn` /
        // `.unknown` keep their existing (unpainted) behaviour.
        guard case .dynamic(kind: .currentColor, raw: _)? = config.background else {
            return config
        }
        // §6.4's target. A non-sRGB foreground (itself dynamic, or absent)
        // means the currentcolor chain bottomed out with nothing to resolve
        // against; that bottom-out is capture-mode split and belongs to the
        // renderer, not to this extractor, so leave the marker in place.
        guard case .srgb(let r, let g, let b, let a)? = config.foreground else {
            PropertyTracker.logOnce(
                key: "bg-currentcolor-no-color",
                message: "background-color: currentcolor with no resolvable "
                    + "`color` on the merged list — left unpainted "
                    + "(css-color-4 §6.4 bottom-out belongs to the renderer)")
            return config
        }
        // Copy-on-write struct: replace only the background channel.
        var out = config
        out.background = .srgb(r: r, g: g, b: b, a: a)
        return out
    }

    /// The element's own computed `color` as plain sRGB components, or nil
    /// when it is itself unresolvable (the currentcolor chain bottomed out).
    private static func ownSrgb(
        _ config: ColorConfig
    ) -> (r: Double, g: Double, b: Double, a: Double)? {
        guard case .srgb(let r, let g, let b, let a)? = config.foreground else { return nil }
        return (r: r, g: g, b: b, a: a)
    }
}

// WHY THE MIX IS SAFE TO RESOLVE HERE — it was not, before wave 49's
// `inherit` pass. css-color/color-mix-currentcolor-001 nests a child with
// `background-color: inherit` exactly over the parent that owns the mix:
// resolving only the PARENT's mix would paint a brown square where the
// reference is green, and the corpus colour veto misses it (web renders
// exactly that today at ssim 1.0000, mean ΔE 1.954, below the 2.3 JND
// threshold in tools/titan/inject-wpt-block.mjs). BackgroundColorInheritance
// now hands the child the parent's UNRESOLVED mix, so the child re-resolves
// `currentcolor` against its own green and the top box paints green — the two
// changes are a pair and must land together.
