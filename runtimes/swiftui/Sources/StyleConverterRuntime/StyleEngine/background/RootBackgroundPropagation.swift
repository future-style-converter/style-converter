//
//  RootBackgroundPropagation.swift
//  StyleEngine/background — wave-53 lane L3 (item A).
//
//  The ROOT's background-IMAGE layers propagate to the CANVAS
//  (css-backgrounds-3 §2.11.2). SwiftUI twin of the web runtime's
//  engine/background/RootBackgroundPropagation.ts and Compose's
//  background/RootBackgroundPropagation.kt — one rule, three canvases. The
//  composed canvas (ios-harness CaptureCanvas.ComposedCaptureCanvas)
//  propagated the body-root COLOUR only, so a root image was painted by nobody
//  (wave52-ship: display-contents-root-background ios f 0.5346,
//  background-attachment-margin-root-001/-002 ios f 0.4509 / 0.3391).
//
//  The rule: §2.11.2 the canvas paints the root's image layers and the root
//  does not paint them again (withCanvasOwnedRootBackground — it lives in the
//  runtime because IRComponent's memberwise init is internal); §3.4 a `fixed`
//  layer's tile origin is the viewport (ICB) corner, a `scroll`/`local` one
//  the root box (ICB + the canvas-owned root margin); F2 capture-frame chrome
//  — capture-browser-ref.mjs padColorFor (:728) fills the 16-px frame with the
//  ring colour only for a uniform ring, so only a stack uniform BY
//  CONSTRUCTION (every layer one sRGBA, repeating on both axes) may cover the
//  frame (RootCanvasBackground pads any other stack in to the ICB). Those
//  uniformity predicates live in RootBackgroundUniformity.swift (fix-pass split).
//

import SwiftUI

public enum RootBackgroundPropagation {

    /// One image layer's §3.4 attachment keyword.
    public enum Attachment: Equatable { case scroll, fixed, local }      // the three §3.4 keywords

    /// A layer's tile origin in pt (== CSS px at scale 1), relative to the ICB corner.
    public struct LayerOrigin: Equatable {
        public let x: CGFloat                                    // right of the ICB corner
        public let y: CGFloat                                    // below the ICB corner
        /// Explicit public init — the synthesized memberwise one is internal.
        public init(x: CGFloat, y: CGFloat) { self.x = x; self.y = y }
    }

    /// The propagation decision: per-layer attachments + origins, and F2 uniformity.
    public struct Plan: Equatable {
        public let attachments: [Attachment]                     // per image layer, source order
        public let origins: [LayerOrigin]                        // per image layer, ICB-relative (§3.4)
        public let uniform: Bool                                 // F2: one colour by construction
        /// Explicit public init (tests and twins compare whole plans).
        public init(attachments: [Attachment], origins: [LayerOrigin], uniform: Bool) {
            self.attachments = attachments; self.origins = origins; self.uniform = uniform
        }
    }

    /// The image-layer longhands the canvas takes over (the root never repaints them).
    public static let imageTypes: Set<String> = [
        "BackgroundImage", "BackgroundSize", "BackgroundRepeat", "BackgroundAttachment",
        "BackgroundPosition", "BackgroundPositionX", "BackgroundPositionY",
        "BackgroundPositionInline", "BackgroundPositionBlock",
        "BackgroundOrigin", "BackgroundClip", "BackgroundBlendMode",
    ]

    /// Knobs the origin offset does not compose with — breadcrumbed, no corpus carrier.
    private static let unmodelled = ["BackgroundPosition", "BackgroundPositionX", "BackgroundPositionY",
        "BackgroundPositionInline", "BackgroundPositionBlock", "BackgroundOrigin", "BackgroundClip",
        "BackgroundBlendMode"]

    /// The raw IR list of the LAST `type` declaration (scalar wrapped; absent → empty).
    private static func rawList(_ props: [IRProperty], _ type: String) -> [IRValue] {
        guard let d = props.last(where: { $0.type == type })?.data else { return [] }
        if case .null = d { return [] }                          // explicit null → nothing
        return d.arrayValue ?? [d]                               // scalar wrap
    }

    /// The keyword of one entry: a bare string, or the `type` of an object.
    private static func keyword(_ e: IRValue) -> String? { e.stringValue ?? e["type"]?.stringValue }

    /// A `none` layer paints nothing (internal: RootBackgroundUniformity reads it too).
    static func isNone(_ l: IRValue) -> Bool { keyword(l) == "none" && l["url"] == nil }

    /// Layer i's attachment (§2.3: shorter lists cycle; unknown → initial `scroll`).
    private static func attachmentAt(_ props: [IRProperty], _ i: Int) -> Attachment {
        let list = rawList(props, "BackgroundAttachment")        // `[{type:"scroll"},…]` on the wire
        guard !list.isEmpty else { return .scroll }              // initial value
        switch keyword(list[i % list.count])?.lowercased() {     // §2.3 cyclic pairing
        case "fixed": return .fixed                              // anchors to the viewport
        case "local": return .local                              // anchors like scroll here (no scroller)
        default: return .scroll                                  // `scroll` and anything unknown
        }
    }

    /// PNG IHDR 1×1 test, public for the pins (the rule lives in RootBackgroundUniformity).
    public static func dataPngIs1x1(_ url: String) -> Bool { RootBackgroundUniformity.dataPngIs1x1(url) }

    /// The plan, or nil when nothing propagates (no image layer, only `none`,
    /// containment — the caller's css-contain-2 §2 gate — or a layer the
    /// extractor cannot paint). `marginTop`/`marginLeft`: the canvas-owned root margin.
    public static func plan(_ props: [IRProperty], marginTop: CGFloat, marginLeft: CGFloat,
                            contained: Bool) -> Plan? {
        if contained { return nil }                              // off the propagation path
        let layers = rawList(props, "BackgroundImage")           // the raw per-layer list
        guard !layers.isEmpty, !layers.allSatisfy(isNone) else { return nil }   // nothing to paint
        // Index parity with the applier: a dropped layer would mis-pair every attachment.
        guard BackgroundImageExtractor.extract(from: props)?.layers.count == layers.count else {
            PropertyTracker.logOnce(key: "root-bg-parity", message: "root canvas background: a layer the extractor cannot paint — not propagated")
            return nil
        }
        for t in unmodelled where props.contains(where: { $0.type == t }) {   // no corpus carrier pairs these
            PropertyTracker.logOnce(key: "root-bg-\(t)", message: "root canvas background: \(t) not modelled (positioning area)")
        }
        let attachments = layers.indices.map { attachmentAt(props, $0) }
        // §3.4: fixed → the viewport (ICB) corner; scroll/local → the root box's padding edge.
        let origins = attachments.map { $0 == .fixed ? LayerOrigin(x: 0, y: 0) : LayerOrigin(x: marginLeft, y: marginTop) }
        // F2: every layer one colour AND repeating on both axes (no gaps) ⇒ the ring is uniform.
        let uniform = RootBackgroundUniformity.stackIsUniform(layers, repeats: rawList(props, "BackgroundRepeat"))
        return Plan(attachments: attachments, origins: origins, uniform: uniform)
    }

    /// §2.11.2 "not painted again": the body-root minus its image layers; identity without a plan.
    public static func withCanvasOwnedRootBackground(_ roots: [IRComponent], _ plan: Plan?) -> [IRComponent] {
        guard plan != nil else { return roots }                  // nothing owned → the same components
        return roots.map { root in
            guard root.meta?.role == "body-root" else { return root }   // only the html+body bag is rewritten
            return IRComponent(id: root.id, name: root.name,
                               properties: root.properties.filter { !imageTypes.contains($0.type) },
                               selectors: root.selectors, media: root.media, children: root.children,
                               slot: root.slot, text: root.text, pseudos: root.pseudos,
                               meta: root.meta, variables: root.variables)
        }
    }
}

/// The canvas-surface paint for a plan: each layer through the runtime's own
/// BackgroundImageApplier as a single-layer stack positioned at its §3.4
/// origin, last source layer at the bottom. Uniform → the framed surface
/// (origins offset by the frame); otherwise padded in to the ICB (origins as-is).
public struct RootCanvasBackground: View {
    let properties: [IRProperty]                                 // the UNSTRIPPED body-root bag
    let plan: RootBackgroundPropagation.Plan                     // the propagation decision
    let frame: CGFloat                                           // the capture frame (16)
    /// Public init for the harness canvas.
    public init(properties: [IRProperty], plan: RootBackgroundPropagation.Plan, frame: CGFloat) {
        self.properties = properties; self.plan = plan; self.frame = frame
    }
    public var body: some View {
        let image = BackgroundImageExtractor.extract(from: properties)?.layers ?? []   // the runtime's own readings
        let size = BackgroundSizeExtractor.extract(from: properties)?.layers ?? []
        let rep = BackgroundRepeatExtractor.extract(from: properties)?.layers ?? []
        let base = plan.uniform ? frame : 0                      // ICB corner inside the painted box
        let n = min(image.count, plan.origins.count)             // plan() pinned the parity; never index past it
        return ZStack(alignment: .topLeading) {
            // ZStack paints later children on top: walk source order backwards.
            ForEach(Array((0..<n).reversed()), id: \.self) { i in
                Color.clear.engineBackgroundImage(               // the runtime's own image applier
                    BackgroundImageConfig(layers: [image[i]]),   // one layer at a time: per-layer origins
                    size: size.isEmpty ? nil : BackgroundSizeConfig(layers: [size[i % size.count]]),   // §2.3 pairing
                    position: BackgroundPositionConfig(x: .px(Double(plan.origins[i].x + base)),
                                                       y: .px(Double(plan.origins[i].y + base))),
                    repeatCfg: rep.isEmpty ? nil : BackgroundRepeatConfig(layers: [rep[i % rep.count]]))   // §2.3
            }
        }
        .padding(plan.uniform ? 0 : frame)                       // F2: non-uniform → the ICB only
    }
}
