//
//  ComponentState.swift
//  StyleEngine/core/dynamic — wave 7 (dynamic styling, issues #33/#34).
//
//  The interaction-state snapshot StateResolver resolves selector
//  buckets against (schema/spec/06-dynamic-styling.md §2), plus the
//  forced-state Environment channel required by spec 06 §6: an explicit
//  set of condition names treated as active regardless of real input —
//  the deterministic capture instrument (docs/DYNAMIC_CAPTURE.md §1).
//

import SwiftUI

/// One component's live interaction state at style-resolution time.
/// Each flag maps 1:1 to a runtime-v1 selector condition (spec 06 §2);
/// ComponentRenderer fills them from its SwiftUI bridges:
///   hovered  ← .onHover        (pointer surfaces only — iPadOS pointer,
///                                Catalyst; never fires on touch, which
///                                IS the spec's defined no-op)
///   pressed  ← DragGesture(0) press bridge (`:active` = press-in-progress)
///   focused  ← @FocusState     (the platform focus system)
///   disabled ← !isEnabled      (SwiftUI .disabled() propagation)
///   checked  ← no generic SwiftUI analogue — always false today; the
///              bucket is a defined no-op + PropertyTracker log
///              (spec 06 §2 "checked-if-cheap"), forcible for capture.
// internal: consumed only by StateResolver + ComponentRenderer; the
// public surface for hosts is the forcedStyleStates environment key.
struct ComponentState: Equatable {
    /// `:hover` — pointer designates the component (css-selectors-4 §7.1).
    var hovered = false
    /// `:active` — press gesture in progress (css-selectors-4 §7.2).
    var pressed = false
    /// `:focus` — component holds input focus (css-selectors-4 §7.3).
    var focused = false
    /// `:disabled` — host disabled the subtree (css-selectors-4 §12.1.2).
    var disabled = false
    /// `:checked` — checkable analogue state (css-selectors-4 §12.2.1).
    var checked = false
    /// Spec 06 §6 forced set: condition names treated as active no
    /// matter what the real flags above say. Fed from the
    /// `forcedStyleStates` environment (harness launch argument).
    var forced: Set<String> = []
}

/// Environment key carrying the forced-state set down the render tree.
/// Default empty = normal interactive resolution (zero forced states) —
/// pre-wave-7 hosts render byte-identically without any setup.
private struct ForcedStyleStatesKey: EnvironmentKey {
    static let defaultValue: Set<String> = []
}

public extension EnvironmentValues {
    /// The spec 06 §6 forced-state hook: hosts (the capture harness, a
    /// test rig) publish condition names ("active", "hover", …) that
    /// style resolution MUST treat as active on every component below.
    /// Names outside the runtime-v1 set are ignored by the resolver
    /// (an unsupported condition stays inert even when forced).
    // public: the harness app sets this from its -forceState launch
    // argument (docs/DYNAMIC_CAPTURE.md §1, iOS transport row).
    var forcedStyleStates: Set<String> {
        get { self[ForcedStyleStatesKey.self] }
        set { self[ForcedStyleStatesKey.self] = newValue }
    }
}
