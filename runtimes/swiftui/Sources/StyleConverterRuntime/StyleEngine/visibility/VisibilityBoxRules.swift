//
//  VisibilityBoxRules.swift
//  StyleEngine/visibility — wave 50, lane B11 (BACKLOG queue 9(a)).
//
//  TWIN STATUS — DEFERRED on Compose, stated here because a twin banner
//  must never name a file that does not exist (wave 50 fix lane F2, on
//  skeptic S1 finding #3). Lane B11 landed a Kotlin copy of this rule
//  table at runtimes/compose/src/main/java/com/styleconverter/runtime/
//  visibility/VisibilityBoxRules.kt with ZERO production callers, and
//  wave 50 deletes it (fix lane F1) rather than keep dead code that reads
//  as parity. The reason it had no caller is the real gap: the Compose
//  table renderer has no track-REMOVAL path, so §11.2's `boxRemoved` arm
//  (below) has nothing on that platform to drive — porting the rules needs
//  the renderer work first, which is a BACKLOG item under the Compose
//  table renderer. CONSEQUENCE while it is open: the two natives answer
//  differently for `visibility: collapse` ON A TABLE TRACK — iOS removes
//  the track (VisibilityApplier's `.boxRemoved` branch), Compose keeps it
//  and only alpha-hides it. Off a track the two agree (both treat
//  `collapse` as `hidden`), which is the case the corpus carries.
//
//  The rules themselves are two defects in one property:
//
//  1. `visibility` is INHERITED and CSS 2.2 §11.2 lets a descendant that
//     declares `visibility: visible` paint inside a hidden ancestor. Both
//     natives implement hidden as ONE subtree layer (`.opacity(0)` here,
//     `Modifier.alpha(0f)` on Compose), which no descendant can escape.
//     MEASURED on the frozen corpus: wave49-final's web capture and the
//     frozen Chromium ref for css-view-transitions/
//     capture-with-visibility-mixed-descendants both paint a green 10x10
//     square at (216,215); ios-screenshots/ and android-screenshots/ are
//     white there. Fixing that needs the renderer to push an inherited
//     visibility down instead of wrapping one layer — a seam, shipped as
//     this lane's patch; these rules are the decision half of it.
//
//  2. `collapse` has TWO behaviours in §11.2 and this applier had the wrong
//     one. On a table row / row group / column / column group the track is
//     REMOVED; "for other elements, `collapse` is treated the same as
//     `hidden`" — the box stays and keeps its layout. `VisibilityApplier`
//     used `.frame(width: 0, height: 0).hidden()` for EVERY element, so an
//     ordinary `visibility: collapse` div lost its layout on iOS while
//     Compose (which folds collapse into its `isHidden` alpha) kept it.
//     That half IS fixed live, in VisibilityApplier.swift, by consulting
//     `treatment` below.
//

import Foundation

/// What a box does once its used `visibility` is known. Three outcomes,
/// not two, because §11.2's `collapse` splits by box type.
enum VisibilityTreatment: Equatable {
    /// Paints normally.
    case painted
    /// Generates and occupies its box but paints no ink of its OWN —
    /// background, borders, shadows, own text and its ::before/::after
    /// pseudos (which inherit from it, css-pseudo-4 §2). Descendants keep
    /// deciding for themselves.
    case inkSuppressed
    /// Box removed from layout entirely — `collapse` on a table track.
    case boxRemoved
}

enum VisibilityBoxRules {

    /// The `display` keywords that make a box a TABLE TRACK — the only
    /// boxes for which `collapse` removes layout (CSS 2.2 §11.2; the
    /// internal box types are css-tables-3 §2.1).
    ///
    /// Deliberately WIDER than `TableBoxTree.roleOf`, which folds columns
    /// and column groups to `.none` because it classifies boxes that
    /// generate CELLS. A column track generates no cell boxes and yet
    /// `collapse` still removes it, so it belongs here.
    static let tableTrackDisplays: Set<String> = [
        "TABLE_ROW",            // a single row track
        "TABLE_ROW_GROUP",      // <tbody>-equivalent
        "TABLE_HEADER_GROUP",   // <thead>-equivalent
        "TABLE_FOOTER_GROUP",   // <tfoot>-equivalent
        "TABLE_COLUMN",         // <col>-equivalent
        "TABLE_COLUMN_GROUP",   // <colgroup>-equivalent
    ]

    /// True when the component's declared `Display` names a table track.
    /// Accepts both wire spellings the corpus has carried (underscore
    /// `TABLE_ROW_GROUP` and CSS hyphen `table-row-group`) through the same
    /// `ValueExtractors.normalize` TableBoxTree uses, so the two
    /// classifiers can never disagree on one component.
    static func isTableTrackBox(_ properties: [IRProperty]) -> Bool {
        // Absent `Display` ⇒ not a track (the initial value is `inline`).
        guard let raw = properties.first(where: { $0.type == "Display" })?.data else {
            return false
        }
        // Hyphen→underscore + uppercase, exactly like TableBoxTree.roleOf.
        return tableTrackDisplays.contains(
            ValueExtractors.normalize(ValueExtractors.extractKeyword(raw))
        )
    }

    /// The component's OWN declared `visibility`, or `nil` when it declares
    /// none. The distinction is the whole point: an undeclared descendant
    /// must INHERIT its ancestor's hidden, and only a declared `visible`
    /// may escape it (CSS 2.2 §11.2).
    ///
    /// An unrecognised keyword returns `nil` (= undeclared ⇒ inherit)
    /// rather than silently becoming `.visible`: inheriting is CSS's
    /// fallback for an invalid declaration, and a wrong un-hide is the
    /// visible failure mode.
    static func declaredVisibility(_ properties: [IRProperty]) -> VisibilityKind? {
        // First (and, per the converter, only) Visibility on the component.
        guard let raw = properties.first(where: { $0.type == "Visibility" })?.data else {
            return nil
        }
        // The wire keyword is an uppercase enum name ("HIDDEN", "COLLAPSE").
        switch ValueExtractors.normalize(ValueExtractors.extractKeyword(raw)) {
        case "VISIBLE":  return .visible
        case "HIDDEN":   return .hidden
        case "COLLAPSE": return .collapse
        default:         return nil
        }
    }

    /// Used value = declared, else inherited (CSS 2.2 §11.2 —
    /// `visibility` is an inherited property with initial value `visible`).
    static func resolve(declared: VisibilityKind?, inherited: VisibilityKind) -> VisibilityKind {
        declared ?? inherited
    }

    /// Used value → what the box does. `collapse` is the only branch that
    /// depends on the box type (CSS 2.2 §11.2).
    static func treatment(_ used: VisibilityKind, isTableTrackBox: Bool) -> VisibilityTreatment {
        switch used {
        // Nothing to do.
        case .visible:
            return .painted
        // Invisible, but "the generated box still affects layout".
        case .hidden:
            return .inkSuppressed
        case .collapse:
            // On a track: "the row/column is removed".
            // Off a track: "treated the same as `hidden`" — layout kept.
            return isTableTrackBox ? .boxRemoved : .inkSuppressed
        }
    }

    /// The value children inherit. `collapse` inherits as `collapse` (it is
    /// the computed value); a non-track child then reads it as `hidden`
    /// through `treatment`, which is exactly §11.2's two-step.
    static func childInherits(_ used: VisibilityKind) -> VisibilityKind { used }

    /// Is the legacy subtree-wide `.opacity(0)` / `alpha(0f)` shortcut
    /// EQUIVALENT to the per-box rule for this element?
    ///
    /// Yes exactly when the element suppresses its own ink and no
    /// descendant declares `visible`: with nothing to un-hide, "hide the
    /// whole layer" and "hide every box in it" paint identically, and the
    /// layer is the cheaper of the two. One declared-`visible` descendant
    /// makes them differ — the mixed-descendants defect measured above.
    ///
    /// A caller that gets `false` must take the per-box path (suppress this
    /// element's own ink, push `childInherits` down) and, if it cannot,
    /// must log the bail through PropertyTracker rather than paint the
    /// wrong thing silently.
    static func subtreeAlphaEquivalent(
        _ treatment: VisibilityTreatment,
        anyDescendantDeclaresVisible: Bool
    ) -> Bool {
        treatment == .inkSuppressed && !anyDescendantDeclaresVisible
    }
}
