//
//  InteractionBridge.swift
//  Renderer — wave 7 (dynamic styling, issue #33).
//
//  The SwiftUI input bridges that feed ComponentState's real flags
//  (spec 06 §2). Attached by ComponentRenderer ONLY when the component
//  carries a selector bucket needing that flag — selector-free
//  components pay zero gesture/hover/focus overhead and keep their
//  exact pre-wave-7 view hierarchy (every bridge is an identity branch
//  when disabled).
//
//  The §5 re-evaluation contract rides SwiftUI's own machinery: each
//  bridge mutates renderer @State/@FocusState, which recomputes body —
//  a restyle with stable view identity (no recomposition), applied on
//  the next frame.
//

import SwiftUI

/// Composite bridge: hover + press + focus listeners, each individually
/// gated. Disabled state needs no bridge (it reads \.isEnabled straight
/// off the environment) and checked has no generic analogue (§2).
struct InteractionBridge: ViewModifier {
    /// Attach the .onHover listener (component has a hover bucket).
    let wantsHover: Bool
    /// Attach the press gesture (component has an active bucket).
    let wantsActive: Bool
    /// Attach the focus binding (component has a focus bucket).
    let wantsFocus: Bool
    /// Renderer-owned state the bridges write into.
    @Binding var hovered: Bool
    @Binding var pressed: Bool
    /// FocusState binding — SwiftUI's focus system writes it directly.
    var focused: FocusState<Bool>.Binding

    func body(content: Content) -> some View {
        content
            // Order is irrelevant — the three listeners are independent
            // input channels; compose them innermost-out for clarity.
            .modifier(HoverBridge(enabled: wantsHover, hovered: $hovered))
            .modifier(PressBridge(enabled: wantsActive, pressed: $pressed))
            .modifier(FocusBridge(enabled: wantsFocus, focused: focused))
    }
}

/// `:hover` — css-selectors-4 §7.1 via SwiftUI .onHover (iOS 13.4+).
/// Fires only on pointer surfaces (iPadOS pointer, Mac Catalyst); on
/// touch-only surfaces it never fires, which IS the spec 06 §2 defined
/// no-op — hover MUST NOT be emulated by latching on first tap.
private struct HoverBridge: ViewModifier {
    let enabled: Bool
    @Binding var hovered: Bool

    @ViewBuilder
    func body(content: Content) -> some View {
        if enabled {
            // Direct designation signal: true on pointer entry, false on
            // exit — exactly the :hover activation window.
            content.onHover { hovered = $0 }
        } else {
            // No hover bucket → identity (no listener allocated).
            content
        }
    }
}

/// `:active` — css-selectors-4 §7.2 via the classic zero-distance
/// DragGesture press bridge: touch-down sets pressed, release/cancel
/// clears it ("deactivates on release/cancel", spec 06 §2).
/// simultaneousGesture keeps scroll/tap recognizers working around it.
private struct PressBridge: ViewModifier {
    let enabled: Bool
    @Binding var pressed: Bool

    @ViewBuilder
    func body(content: Content) -> some View {
        if enabled {
            content.simultaneousGesture(
                // minimumDistance 0 → recognizes at touch-down, the
                // :active begin edge; onEnded fires on lift OR cancel.
                DragGesture(minimumDistance: 0)
                    // Guard the write: onChanged streams every move —
                    // only the false→true edge should invalidate body.
                    .onChanged { _ in if !pressed { pressed = true } }
                    .onEnded { _ in pressed = false }
            )
        } else {
            // No active bucket → identity (no recognizer allocated).
            content
        }
    }
}

/// `:focus` — css-selectors-4 §9.3 via @FocusState (the platform focus
/// system, spec 06 §2 — v1 implements `focus` only). On views the
/// platform cannot focus (plain boxes on touch iOS) the binding simply
/// never becomes true — captures use the forced-state hook instead.
private struct FocusBridge: ViewModifier {
    let enabled: Bool
    var focused: FocusState<Bool>.Binding

    @ViewBuilder
    func body(content: Content) -> some View {
        if enabled {
            // Bind this view's focus membership into the renderer state.
            content.focused(focused)
        } else {
            // No focus bucket → identity (no focus participation).
            content
        }
    }
}
