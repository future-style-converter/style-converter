import Foundation

/// InlineRunPlan — the wave-32 `meta.runs` resolver (lane R), the SwiftUI
/// twin of web's `renderer/InlineRuns.ts` and Compose's `InlineRunPlan.kt`.
///
/// WHAT THE WIRE SAYS (schema/spec/03-children.md §4.1): `meta.runs` is the
/// component's inline content in DOCUMENT order — entries that are each
/// exactly one of `{text}` (an anonymous inline run) or `{child}` (the
/// position of one child box). It exists because `text` is ONE string and
/// the child list has ONE order, so the wire could previously say
/// `run + children` or `children + run` but never `run / child / run` —
/// exactly what `the quick <u>brown</u> fox` needs when the `<u>` survives.
///
/// WHAT THIS FILE DOES: fold the wire list into the shape this renderer can
/// consume WITHOUT restructuring its child `ForEach` — a per-child list of
/// the text runs that precede it, plus the runs that trail the last child.
/// That fold is only valid when the plan's child references appear in the
/// SAME order as the rendered children, so `resolve` proves it and refuses
/// otherwise (see `strictlyIncreasing` below). Refusing returns nil, which
/// puts the container back on the exact pre-wave-32 path.
///
/// HONEST SCOPE ON THIS PLATFORM. The block container is a `VStack`: it has
/// no inline line box, so an anonymous text run is a STACKED label, not a
/// fragment sharing a line with the child beside it. `meta.runs` therefore
/// buys SwiftUI the correct ORDER, not correct inline layout — the same
/// approximation the leading-`text` label always was, now at the right
/// position instead of always first. The CSS2 static-position family is
/// decided purely by order, so that is a real fidelity gain; the full
/// inline-formatting-context model stays out of scope.
enum InlineRunPlan {

    /// The folded plan.
    struct Plan {
        /// Text runs that precede child `i`, keyed by that child's index.
        let before: [Int: [String]]
        /// Text runs after the last referenced child.
        let trailing: [String]
    }

    /// Resolve `runs` against the rendered `children` (already `order`-sorted,
    /// in the exact sequence the caller's `ForEach` walks).
    ///
    /// - Returns: the folded plan, or nil meaning "no usable runs — keep the
    ///   default leading-text-then-children path". nil rather than an empty
    ///   plan is what keeps every pre-wave-32 document's view tree identical:
    ///   absent, malformed, fully-dangling and out-of-order lists all land
    ///   back on the code path they used before.
    static func resolve(_ runs: [IRRun]?, children: [IRComponent]) -> Plan? {
        // Omit-when-absent is the overwhelming common case.
        guard let runs, !runs.isEmpty, !children.isEmpty else { return nil }

        // Two lookup keys, because the reference is the child's AUTHORING KEY
        // (spec 03 §4.1): on the converter-emitted wire that key is the
        // child's `name`; in the extractor-direct pipeline it is also its
        // `id`. `name` is consulted first so a document that happens to reuse
        // a name as some other component's id cannot mis-resolve.
        var byName: [String: Int] = [:]
        var byId: [String: Int] = [:]
        for (index, child) in children.enumerated() {
            // First occurrence wins on a duplicate — the composer is lenient
            // the same way (duplicate ids are a convert-time error upstream).
            if !child.name.isEmpty, byName[child.name] == nil { byName[child.name] = index }
            if !child.id.isEmpty, byId[child.id] == nil { byId[child.id] = index }
        }

        var before: [Int: [String]] = [:]
        var trailing: [String] = []
        var pending: [String] = []
        var claimedOrder: [Int] = []
        var sawChild = false

        for run in runs {
            if let text = run.text {
                // An EMPTY run contributes no glyphs and no box. A
                // WHITESPACE-ONLY run is not empty — it is the inter-run word
                // space (rule 6) and must survive.
                if !text.isEmpty { pending.append(text) }
                continue
            }
            guard let key = run.child else { continue } // decoder rejected this
            guard let index = byName[key] ?? byId[key] else {
                // Rule 5 — dangling key: skip. The named box is not in this
                // composition, so there is nothing to place; every other
                // entry keeps its position.
                continue
            }
            // A child named twice would render twice; honour the FIRST.
            if claimedOrder.contains(index) { continue }
            claimedOrder.append(index)
            sawChild = true
            if !pending.isEmpty { before[index] = pending; pending = [] }
        }
        // Whatever is still pending sits after the last referenced child.
        trailing = pending

        // Nothing to place, or nothing but text: fall back. A list with no
        // child reference says nothing the plain `text` channel does not.
        guard sawChild, !(before.isEmpty && trailing.isEmpty) else { return nil }

        // THE PROOF this fold needs: the caller walks children in index order,
        // so a plan whose child references are NOT in that order cannot be
        // expressed as "runs before child i". Rather than silently paint the
        // wrong sequence, refuse — the container keeps its pre-wave-32
        // behaviour and the fixture still carries `_runs` for the platforms
        // that can express it. (The extractor emits document order and the
        // children array is document order, so this holds for the whole
        // committed corpus; the guard is here because `FlexboxApplier.sorted`
        // may permute children by CSS `order`.)
        guard strictlyIncreasing(claimedOrder) else { return nil }
        return Plan(before: before, trailing: trailing)
    }

    /// Is every element greater than the one before it?
    private static func strictlyIncreasing(_ xs: [Int]) -> Bool {
        for i in 1..<max(xs.count, 1) where xs[i] <= xs[i - 1] { return false }
        return true
    }
}
