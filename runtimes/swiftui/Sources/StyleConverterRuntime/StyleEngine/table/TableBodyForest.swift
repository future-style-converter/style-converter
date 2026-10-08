//
//  TableBodyForest.swift
//  StyleEngine/table — wave-53 lane L3 (item B).
//
//  A `display: table` BODY forms a table on the composed canvas (CSS 2.1
//  §17.2.1 rule 2). Twin of Compose's table/TableBodyForest.kt; the web harness
//  lets Chrome do the same fixup (apps/web-harness/src/ui/CanvasTableBody.ts).
//  Lives in the runtime because IRComponent's memberwise init is internal (the
//  same reason UABlockMargin.withCanvasOwnedBodyMargin does).
//
//  WHY: the extractor emits html+body as ONE synthetic `body-root` whose
//  element children are SIBLING ROOTS (extract-fixture.mjs slots them under the
//  body only when it declares a height), so `Display TABLE` on the body-root
//  applied to an EMPTY box and its children stacked as blocks. MEASURED on
//  wave52-ship CSS2/css21-errata/s-11-1-1b-006 ios P 0.9953 (DEGENERATE): the
//  td's square at image rows 51-70 (a 10-px caption-div gap, then the td's own
//  −15 margin-top as an offset) where the reference has 56-75.
//
//  The rewrite: the run of in-flow roots after the body-root becomes one
//  synthetic TABLE (carrying the body's BorderSpacing / BorderCollapse) → one
//  TABLE_ROW → cells, by §17.2.1 rule 2: each run of consecutive non-cell roots
//  goes into ONE anonymous TABLE_CELL, and cell roots pass through without
//  their Margin* (§8.3: margins do not apply to table-internal boxes).
//  Out-of-flow roots stay roots in place (the canvas hoists or anchors them).
//  The body-root itself is untouched — every canvas resolver reads it.
//

import Foundation

public enum TableBodyForest {

    /// The body displays that make the body a table box (css-display-3 §2.3).
    private static let tableBody: Set<String> = ["TABLE", "INLINE_TABLE"]

    /// Proper table children other than cells (§17.2.1): a run holding one bails.
    private static let properNonCell: Set<String> = ["TABLE_ROW", "TABLE_ROW_GROUP", "TABLE_HEADER_GROUP",
        "TABLE_FOOTER_GROUP", "TABLE_CAPTION", "TABLE_COLUMN", "TABLE_COLUMN_GROUP"]

    /// The margin longhands §8.3 says do not apply to a table cell.
    private static let marginTypes: Set<String> = ["MarginTop", "MarginRight", "MarginBottom", "MarginLeft",
        "MarginBlockStart", "MarginBlockEnd", "MarginInlineStart", "MarginInlineEnd"]

    /// The body's table-box properties the synthetic table inherits (§17.6).
    private static let tableBoxTypes: Set<String> = ["BorderSpacing", "BorderCollapse"]

    /// A component's LAST declared `Display` enum leaf, uppercased (nil when absent).
    private static func displayOf(_ c: IRComponent) -> String? {
        c.properties.last(where: { $0.type == "Display" })?.data.stringValue?.uppercased()   // cascade: last wins
    }

    /// A length leaf that is a concrete 0 px (`{px:0}` or the wrapped `{original:{px:0}}`).
    private static func isZeroPx(_ d: IRValue) -> Bool {
        (d["px"]?.doubleValue ?? d["original"]?["px"]?.doubleValue) == 0   // keywords → not zero
    }

    /// The body declares a non-zero padding or border width: it paints its own box, so bail.
    private static func paintsOwnEdges(_ body: IRComponent) -> Bool {
        body.properties.contains { p in
            (p.type.hasPrefix("Padding") || (p.type.hasPrefix("Border") && p.type.hasSuffix("Width")))
                && !isZeroPx(p.data)                                     // 0 carriers: bail, never guess
        }
    }

    /// One `Display` enum leaf, the shape the converter emits.
    private static func display(_ value: String) -> IRProperty {
        IRProperty(type: "Display", data: .string(value))                // read by the display extractor
    }

    /// A synthetic component: id/name derived from the body's, children attached.
    private static func synthetic(_ body: IRComponent, _ suffix: String,
                                  _ props: [IRProperty], _ kids: [IRComponent]) -> IRComponent {
        IRComponent(id: body.id + suffix, name: body.name + suffix, properties: props,
                    selectors: nil, media: nil, children: kids, slot: nil,
                    text: nil, pseudos: nil, meta: nil)                  // no role: never read as the body
    }

    /// A root copied with a filtered property list (id/meta/children verbatim).
    private static func withProperties(_ r: IRComponent, _ props: [IRProperty]) -> IRComponent {
        IRComponent(id: r.id, name: r.name, properties: props, selectors: r.selectors, media: r.media,
                    children: r.children, slot: r.slot, text: r.text, pseudos: r.pseudos,
                    meta: r.meta, variables: r.variables)
    }

    /// The rewritten forest, or `roots` unchanged when the body-root is not a
    /// table, paints its own edges, has no in-flow run after it, or that run
    /// already holds a proper non-cell table child. Out of flow = the
    /// renderer's own root classification (`ComponentRenderer.isOutOfFlow`:
    /// absolute or fixed — hoisted to the ICB or anchored at its static slot).
    public static func rewrite(_ roots: [IRComponent]) -> [IRComponent] {
        guard let bodyIdx = roots.firstIndex(where: { $0.meta?.role == "body-root" }) else { return roots }
        let body = roots[bodyIdx]                                                // one body per document
        guard let d = displayOf(body), tableBody.contains(d), !paintsOwnEdges(body) else { return roots }
        let after = Array(roots[(bodyIdx + 1)...])                               // the body's (sibling) children
        let run = after.filter { !ComponentRenderer.isOutOfFlow($0) }          // the in-flow run
        guard !run.isEmpty, !run.contains(where: { properNonCell.contains(displayOf($0) ?? "") }) else {
            return roots                                                         // 0 carriers: bail
        }
        // §17.2.1 rule 2: consecutive non-cells share ONE anonymous cell; cells pass through.
        var cells: [IRComponent] = []                                            // the row's children, in order
        var pending: [IRComponent] = []                                          // the open run of non-cells
        func flush() {                                                          // close the open anonymous cell
            guard !pending.isEmpty else { return }
            cells.append(synthetic(body, "#cell\(cells.count)", [display("TABLE_CELL")], pending))
            pending.removeAll()                                                  // the next non-cell opens a new one
        }
        for r in run {
            if displayOf(r) == "TABLE_CELL" {                                    // a proper cell
                flush()                                                          // ends any open anonymous cell
                cells.append(withProperties(r, r.properties.filter { !marginTypes.contains($0.type) }))  // §8.3
            } else { pending.append(r) }                                         // joins the open anonymous cell
        }
        flush()                                                                  // close the trailing run
        let row = synthetic(body, "#row", [display("TABLE_ROW")], cells)         // rule 2's anonymous row
        let table = synthetic(body, "#table",                                    // the body's table box
                              [display("TABLE")] + body.properties.filter { tableBoxTypes.contains($0.type) }, [row])
        // The table takes the run's first position; out-of-flow roots keep their order after it.
        return Array(roots[...bodyIdx]) + [table] + after.filter { ComponentRenderer.isOutOfFlow($0) }
    }
}
