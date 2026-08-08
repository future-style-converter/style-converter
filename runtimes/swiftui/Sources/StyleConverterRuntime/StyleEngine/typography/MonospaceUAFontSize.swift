//
//  MonospaceUAFontSize.swift
//  StyleEngine/typography — wave 36, lane M8.
//
//  The UA **fixed-default font size** quirk.
//
//  ── The defect this closes (measured, not theoretical) ──────────────────────
//  `css/css-overflow/line-clamp/block-ellipsis-001` declares only
//  `line-clamp: 2; width: 63.1ch; font-family: monospace; border: 1px solid`.
//  On the browser reference the box lays out 46 characters per line and the
//  two-line border box measures 35px tall; on both natives it laid out 37
//  characters per line and measured 42px tall — a pure metric divergence with
//  the CORRECT clamp, the CORRECT ellipsis and the CORRECT box model.
//
//  Two independent measurements over the wave-35 depth-48 captures pin the
//  cause to ONE number:
//   * advance — ref 358px / 46 chars = 7.78px per glyph; a 0.6em monospace
//     advance puts the ref font size at 12.97px. The natives measured
//     9.6px per glyph = 0.6 × 16px.
//   * line pitch — ref (50 − 16 − 2) / 2 = 16.25px per line, which is exactly
//     `REF_LINE_HEIGHT` 1.25 × 13px (capture-browser-ref.mjs pins that ratio).
//     The natives measured (57 − 16 − 2) / 2 = 19.5 ≈ 1.25 × 16px.
//
//  ── The rule ───────────────────────────────────────────────────────────────
//  Every engine keeps TWO UA default font sizes: `defaultFontSize` (16px) and
//  `defaultFixedFontSize` (13px in Chromium, and the value the frozen refs
//  were captured with). CSS Fonts 4 §3.5 resolves the `font-size` keyword
//  `medium` — the INITIAL value, i.e. what an element with no `font-size`
//  declaration computes to — against whichever of the two matches the used
//  generic family. Blink implements this in `FontBuilder::UpdateComputedSize`
//  gated on `FontDescription::GenericFamily() == kMonospaceFamily`, and that
//  generic is taken from the **first** entry of the declared family list only.
//
//  So: no `font-size` declaration + first declared family is the `monospace`
//  (or `ui-monospace`) generic ⇒ the element's font size is `fixedDefaultPx`,
//  not 16. Web needs no twin — it emits `font-family: monospace` verbatim and
//  the browser applies its own quirk, which is precisely why the web column
//  already passes every cell this gate is aimed at.
//
//  ── Why the FIRST entry, and only the generic ──────────────────────────────
//  `font-family: "Courier New", Courier, monospace` (css-text hyphens) names a
//  concrete face first, so Blink's generic stays `kStandardFamily` and the size
//  stays 16 — even though the list ends in the monospace generic and even
//  though the face this device resolves may well be monospaced. Keying on
//  "the list mentions monospace" instead of "the first entry IS the monospace
//  generic" would move `hyphens-auto-control`, an Android cell that PASSES at
//  16px today. The narrow rule is also the CSS-correct one.
//
//  ── Known limit, stated rather than hidden ─────────────────────────────────
//  A child that re-declares `font-family: sans-serif` under a monospace parent
//  inherits the parent's COMPUTED 13px in a browser (the keyword is not
//  re-resolved on the child), while this gate hands it 16. No corpus cell
//  exercises that shape; closing it needs the inherited-size channel to carry
//  "this size came from a keyword", which is a wire change, not a runtime one.
//
//  Byte-parallel twin: Compose
//  `typography/MonospaceUAFontSize.kt`.
//

// Foundation for String case-folding/trimming only — the gate is pure.
import Foundation

enum MonospaceUAFontSize {

    /// The IR type names this gate reads (no magic literals at call sites).
    static let familyPropertyType = "FontFamily"
    static let sizePropertyType = "FontSize"

    /// The UA `defaultFixedFontSize`, in the runtime's px space.
    ///
    /// 13 is Chromium's shipped default (and Safari's); it is the number the
    /// frozen `tools/wpt/refs/<sha>/…` captures were rasterised with, so it is
    /// the only value that can make a native capture land on the reference.
    static let fixedDefaultPx: Double = 13.0

    /// The family-name list carried by `data`, in declaration order, or nil
    /// when the payload is not a family list at all (so "absent" stays
    /// distinguishable from "declared but empty").
    ///
    /// Mirrors the shapes `FontFamilyExtractor` accepts — an array of bare
    /// strings, an array of `{ "name": … }` / `{ "keyword": … }` objects, or a
    /// single string — so this gate and the family the label actually installs
    /// can never disagree about the list.
    static func families(_ data: IRValue?) -> [String]? {
        guard let data = data else { return nil }
        switch data {
        case .array(let entries):
            // Object entries keep declaration order across the two key
            // spellings: whichever of name/keyword this entry carries IS the
            // family at this position.
            return entries.compactMap { entry -> String? in
                if let o = entry.objectValue {
                    return o["name"]?.stringValue ?? o["keyword"]?.stringValue
                }
                return entry.stringValue
            }
        case .string(let s):
            // Legacy single-name payload — a one-entry list, so it goes
            // through exactly the same first-entry test below.
            return [s]
        default:
            // Any other shape (number, bool, object, null) names no family;
            // returning nil keeps the drop visible instead of guessing.
            return nil
        }
    }

    /// True iff `familyData` declares the `monospace` / `ui-monospace` generic
    /// as its FIRST family — the exact condition Blink keys the quirk on.
    static func appliesToFamily(_ familyData: IRValue?) -> Bool {
        guard let names = families(familyData) else { return false }
        // Quoting is stripped by the converter, but hand-authored IR and the
        // conformance goldens may still carry it — normalise the same way the
        // family resolvers do so the two agree character-for-character.
        guard let first = names.first?
            .trimmingCharacters(in: .whitespaces)
            .trimmingCharacters(in: CharacterSet(charactersIn: "\"'"))
            .lowercased() else { return false }
        return first == "monospace" || first == "ui-monospace"
    }

    /// The element's font size in px when the quirk fires, else nil (meaning
    /// "leave the caller's own default alone").
    ///
    /// Cascade shape mirrors the extractors that fold the same list into a
    /// style: the LAST `FontFamily` declaration wins, and the presence of ANY
    /// `FontSize` declaration disarms the gate (a declared size is never the
    /// `medium` keyword this quirk resolves).
    static func resolvePx(from properties: [IRProperty]) -> Double? {
        // A declared font-size — of any flavour, including one that fails to
        // parse — means the author supplied a specified value, so the keyword
        // resolution this gate models never runs.
        if properties.contains(where: { $0.type == sizePropertyType }) { return nil }
        let family = properties.last(where: { $0.type == familyPropertyType })?.data
        return appliesToFamily(family) ? fixedDefaultPx : nil
    }
}
