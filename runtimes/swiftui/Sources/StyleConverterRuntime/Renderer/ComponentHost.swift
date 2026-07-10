//
//  ComponentHost.swift
//  Renderer — IR v2 Slot & Placement contract (design §3.2).
//
//  The runtime shim between composition and the engine: EVERY component
//  renders through ComponentHost, which unconditionally attaches the
//  component's self-extracted ItemPlacement as a SwiftUI layout value.
//  Parent-data is only read by whoever MEASURES the node, so the
//  placement is inert under a plain VStack/ZStack and active under
//  CSSGridLayout/CSSFlexLayout — the child never knows its destination,
//  the container never knows its arrivals until measure time.
//
//  Note on the design's `.zIndex(…)` mention: paint order is already
//  applied by the component's own PositionApplier inside
//  ComponentRenderer.body (a ZIndex declaration acts among siblings
//  wherever the view lands), so the host does NOT apply it a second
//  time — doubling the modifier would change the campaign-pinned paint
//  order. The claim still rides ItemPlacement.paint for introspection.
//

import SwiftUI

/// Composition-side wrapper: ComponentRenderer + placement parent-data.
/// Use this (not ComponentRenderer directly) wherever a component enters
/// a view tree — the renderer's child loop, the harness gallery and the
/// capture canvas all route through it.
// public: the harness (CaptureCanvas, ComponentGallery) hosts root
// components through this shim, same as the engine hosts children.
public struct ComponentHost: View {
    /// The component to render — placement is derived from ITS OWN
    /// properties only (no parent participation, design §2.2).
    let component: IRComponent

    // public: explicit memberwise init — the synthesized one is
    // internal, so cross-module callers (the harness) need it spelled out.
    public init(component: IRComponent) {
        self.component = component
    }

    // public: View protocol witness on a public type must be public.
    public var body: some View {
        ComponentRenderer(component: component)
            // The v2 parent-data channel: ITEM-scoped declarations
            // (align-self, flex-*, grid placement, z-index — the frozen
            // PropertyScope.item list) packaged for whatever container
            // this component lands in.
            .stylePlacement(ItemPlacementExtractor.extract(from: component.properties))
    }
}
