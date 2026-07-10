//
//  IRComposer.swift
//  StyleConverterRuntime
//
//  The iOS COMPOSER for the IR v2 flat Slot & Placement contract
//  (schema/spec/03-children.md): rebuilds the in-memory preview tree
//  from the flat wire list's child→parent `slot` references, so the
//  recursive renderer and the capture harness keep the single composed
//  shape they had under v1.
//
//  Contract points implemented here (spec 03):
//    - Sibling-order rule: children of the same parent keep their
//      relative FLAT-ARRAY order — that order IS the composition order
//      (SwiftUI subview order), and the converter's pre-order flattener
//      makes it identical to the old nested order.
//    - Dangling slot.parent: NOT a decode error — the entry is treated
//      as a ROOT with a composer warning (never a crash, never a
//      dropped component).
//    - Mode B (zero-slot documents): every entry is a root; composition
//      is supplied externally. Zero special-casing needed — the loop
//      below degenerates to the identity.
//    - slot.name is preserved but not yet routed: every slot composes
//      into the single "content" open slot today (multi-slot containers
//      are a v2 reservation, no runtime consumes other names yet).
//
//  Style engines never call this — engines are composition-agnostic by
//  contract; only the document decode (preview path) and future SDUI
//  shells compose.
//

import Foundation

/// Pure flat-list → composed-tree transform. Public so SDUI hosts can
/// re-compose after receiving an external (Mode B) arrangement.
public enum IRComposer {

    /// Compose a flat v2 component list into root trees (children
    /// populated, nil when empty — matching the v1 in-memory shape the
    /// renderer's placeholder branch keys on).
    public static func compose(_ flat: [IRComponent]) -> [IRComponent] {
        // Empty documents compose to an empty forest.
        guard !flat.isEmpty else { return [] }
        // The id universe — used to detect dangling parent references.
        let ids = Set(flat.map(\.id))
        // Duplicate ids are a CONVERT-time invariant (IRFlattener errors);
        // a reader encountering one warns loudly but keeps composing so a
        // malformed doc still previews (spec 03: never a crash).
        if ids.count != flat.count {
            warn("duplicate component ids in document — slot references are ambiguous (converter invariant violated)")
        }
        // Bucket child INDICES by parent id, preserving flat-array order
        // (the sibling-order rule). Roots: no slot, dangling parent, or
        // degenerate self-reference.
        var childIndices: [String: [Int]] = [:]
        var rootIndices: [Int] = []
        for (i, c) in flat.enumerated() {
            if let s = c.slot, s.parent != c.id, ids.contains(s.parent) {
                childIndices[s.parent, default: []].append(i)
            } else {
                // Dangling (or self-referencing) slot → root + warning.
                if let s = c.slot {
                    warn("dangling slot.parent '\(s.parent)' on component '\(c.id)' — treating as root")
                }
                rootIndices.append(i)
            }
        }
        // Depth-first build from each root. `visited` guards against
        // reference cycles (impossible from the converter's flattener,
        // possible in hand-authored docs) so recursion always terminates.
        var visited = Set<Int>()
        func build(_ i: Int) -> IRComponent {
            visited.insert(i)
            // Children in flat order, skipping anything already consumed
            // (a cycle member reached twice) — cycle edges are dropped
            // with a warning below when the sweep finds the orphan.
            let kids = (childIndices[flat[i].id] ?? [])
                .filter { !visited.contains($0) }
                .map { build($0) }
            // nil (never []) when empty — version-stable renderer shape.
            return flat[i].withChildren(kids.isEmpty ? nil : kids)
        }
        var roots = rootIndices.map { build($0) }
        // Cycle sweep: components unreachable from any root (mutual slot
        // references) get promoted to roots rather than silently dropped.
        for i in flat.indices where !visited.contains(i) {
            warn("slot reference cycle involving component '\(flat[i].id)' — promoting to root")
            roots.append(build(i))
        }
        return roots
    }

    /// Composer warnings go to stderr — same channel as the converter's
    /// deprecation warnings, visible in harness logs without polluting
    /// captured output.
    private static func warn(_ message: String) {
        FileHandle.standardError.write(Data("[IRComposer] WARNING: \(message)\n".utf8))
    }
}
