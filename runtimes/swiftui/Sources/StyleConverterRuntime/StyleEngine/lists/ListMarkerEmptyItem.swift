//
//  ListMarkerEmptyItem.swift
//  StyleEngine/lists — wave 52, lane L6 (T7's native half): the EMPTY list
//  item. BYTE-PARALLEL TWIN of runtimes/compose/src/main/java/com/
//  styleconverter/runtime/lists/ListMarkerEmptyItem.kt — same clauses, same
//  pins.
//
//  ## The defect (probed, not assumed)
//  Lane L5's F-E (tools/titan/results/wave52-extractor-cascade/
//  seam-6-FE-li.patch) stops the extractor turning a rule-less `<li></li>`
//  into a 100×100 placeholder, so the 51 `<li>` of the 15
//  css-counter-styles/cssom/ tests reach the wire with no text, no
//  children, no pseudo and no declared size — all under
//  `list-style-position: inside`, i.e. on the inside OVERLAY, which paints
//  the marker in a ZERO-size box over a 0-tall item. Probed here (Catalyst
//  raster of the post-F-E cssom-pad-setter-invalid wire, the T7 bake's
//  "001." / "002." / "003."): ONE ink band y 16–28 for all three markers,
//  where the ref paints three rows at a 20 px pitch.
//
//  ## The spec
//  css-lists-3 §3.5: an `inside` ::marker is the item's first inline box;
//  with no other content it is the ONLY content of the item's single line
//  box, and an auto-height block is as tall as its line boxes (CSS 2.1
//  §10.6.3) — so the marker must SIZE the item: the leading-inline-box
//  HStack is that layout; the zero-size overlay erases it. An `outside`
//  marker on an empty item likewise sits on a line of the item's own, so
//  the hang reports the marker's height for it
//  (`ListMarkerOutsideHang.place(itemIsEmpty:)`).
//
//  ## "Empty" (IR-provable, deliberately narrow)
//  No text, no children, no generated ::before/::after/::marker
//  (`pseudos` — counter-list-item-2/-3's items carry a baked ::before and
//  are NOT empty), no declared block size (the abspos-only counter-styles
//  items declare `height: 31.25px` and keep the overlay). Corpus: 12 today
//  (name-case-sensitivity, floated, failing ×3), +51 after F-E (cssom).
//  The renderer consults it only once seam-4.patch lands.
//

import Foundation

enum ListMarkerEmptyItem {

    /// css-sizing-3 §3 `height` / `min-height` and their logical twins —
    /// a declared block size is the item's own box.
    private static let declaredBlockSize: Set<String> = ["Height", "MinHeight", "BlockSize", "MinBlockSize"]

    /// Is `item` content-free, so that only its marker can give it a line
    /// box? (Each clause's reason is in the header.)
    static func isEmpty(_ item: IRComponent) -> Bool {
        // Own in-flow text would make the line box itself.
        guard item.text?.isEmpty ?? true else { return false }
        // Any child (in-flow or not) is content this rule does not model.
        guard (item.children ?? []).isEmpty else { return false }
        // Generated content (a baked ::before counter) is content too: any
        // pseudo bag that is not the JSON null / an empty object.
        switch item.pseudos {
        case .none, .some(.null): break
        case .some(.object(let o)) where o.isEmpty: break
        default: return false
        }
        // A declared block size keeps the item's own box (the overlay).
        return !item.properties.contains { declaredBlockSize.contains($0.type) }
    }

    /// The inside-overlay decision with emptiness folded in: the overlay
    /// only for a NON-empty item; an empty one takes the HStack, where the
    /// marker sizes the line.
    static func rendersInsideOverlay(position: ListMarkerPosition?, item: IRComponent) -> Bool {
        ListMarkerRow.rendersInsideOverlay(
            position: position,
            itemExposesTextBaseline: ListMarkerRow.itemExposesTextBaseline(item)) && !isEmpty(item)
    }
}
