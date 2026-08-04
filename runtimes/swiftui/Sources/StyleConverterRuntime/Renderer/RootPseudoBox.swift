//
//  RootPseudoBox.swift
//  Renderer — wave-28 lane PG.
//
//  The SWIFTUI half of the root-scope generated-box path. Paints the box
//  RootPseudoSpec bridged out of the body-root's `pseudos` bucket, and
//  pins it to the physical inline-start when containment blocks the
//  body's direction propagation.
//
//  Scope is deliberately the ROOT bucket only (`meta.role == "body-root"`).
//  Ordinary elements' ::before/::after are not rendered on this platform at
//  all yet, and `pseudos.marker` belongs to the list path
//  (meta.markerText / the marker prepend in ComponentRenderer) — this file
//  must never double-render either, which is why the entry point below
//  reads `before`/`after` off a body-root and nothing else.
//
//  See RootPseudoSpec.swift for the measured divergence (zero orange px on
//  iOS/Android vs the ref's 10 000 at [16,16]-[115,115]) and the spec chain.
//

import SwiftUI

extension RootPseudo {

    /// Read a `Contain` IR leaf as its keyword list — the twin of Compose's
    /// `containKeywordsOf` and the Android harness's `containKeywords`.
    static func containKeywords(of component: IRComponent) -> [String]? {
        guard let data = component.properties.first(where: { $0.type == "Contain" })?.data else {
            return nil                                   // no `contain` declaration at all
        }
        switch data {
        // Normal emission: ContainProperty.values as a string array.
        case .array(let items): return items.compactMap { $0.stringValue }
        // Defensive: one primitive, possibly a space-separated keyword list.
        case .string(let s):
            return s.trimmingCharacters(in: .whitespaces)
                .split(whereSeparator: { $0.isWhitespace }).map(String.init)
        // Objects / null / anything else: not a keyword list ⇒ no containment.
        default: return nil
        }
    }

    /// The root-scope generated box for one role, or nil when this
    /// component hosts none. Nil for EVERY non-body-root component by
    /// design (see the file banner) and for a bucket with nothing
    /// paintable in it.
    static func specFor(component: IRComponent, role: String) -> RootPseudoSpec? {
        // Only the synthetic root component carries a root-SCOPE bucket.
        guard component.meta?.role == "body-root" else { return nil }
        // No silent fallthrough: a root-scope `::after` would have to CLOSE
        // the flow, which the renderer's single LEADING emitter cannot
        // express. Reported once, from the "before" pass so it fires exactly
        // once per component. TODO(lane PG follow-up): thread a trailing
        // emitter through the container branches when a WPT test needs it —
        // the whole css-contain family uses ::before only.
        if role == "before", component.pseudos?["after"] != nil {
            PropertyTracker.logOnce(
                key: "rootpseudo-after-\(component.id)",
                message: "[RootPseudo] root-scope ::after on '\(component.id)' is not rendered (leading emitter only)")
        }
        guard let bucket = component.pseudos?[role] else { return nil }
        guard let spec = spec(bucket: bucket, role: role), spec.paints else { return nil }
        // A non-block generated box would need inline flow this path cannot
        // model; naming it keeps the gap visible instead of silently boxing.
        if !spec.isBlock {
            PropertyTracker.logOnce(
                key: "rootpseudo-nonblock-\(role)",
                message: "[RootPseudo] root ::\(role) is not display:block — rendered as a block box anyway")
        }
        return spec
    }
}

/// One root-scope generated box in the body-root's block flow.
struct RootPseudoBoxView: View {
    /// The bridged box (see ``RootPseudo/specFor(component:role:)``).
    let spec: RootPseudoSpec
    /// When true the box is placed at the PHYSICAL inline-start of the root
    /// regardless of the ambient layout direction — the
    /// ``RootPseudo/containmentBlocksDirectionPropagation(_:)`` verdict. A
    /// SwiftUI stack aligns a narrower child by the AMBIENT layout
    /// direction, so a `direction: rtl` body-root would otherwise push the
    /// html-owned box flush right, the same divergence web showed at
    /// [116,16]-[215,115].
    let pinInlineStart: Bool

    var body: some View {
        if pinInlineStart {
            // Full-width shim so the parent stack has no free space left to
            // act on, with `.leading` resolved under a FORCED left-to-right
            // environment — the environment write must be the OUTERMOST
            // modifier, since the frame's alignment reads the environment of
            // the subtree it is installed in. (A flex/grid body-root hosting
            // a root-scope pseudo has no corpus coverage; it would need the
            // flex path instead of this block shim.)
            box
                .frame(maxWidth: .infinity, alignment: .leading)
                .environment(\.layoutDirection, .leftToRight)
        } else {
            // Uncontained body ⇒ the body's direction really does propagate
            // to the root, so the ambient placement stands (no capture moves).
            box
        }
    }

    /// css-sizing-3 §5 used inline size. CSS px map 1:1 to points on the
    /// composed WPT canvas (the same assumption WPTCanvas.canvasFramePx
    /// encodes); nil is `auto`, so the content sizes that axis.
    private var usedWidth: CGFloat? {
        guard let w = spec.widthPx else { return nil }
        return CGFloat(w)
    }

    /// css-sizing-3 §5 used block size — the twin of ``usedWidth``.
    private var usedHeight: CGFloat? {
        guard let h = spec.heightPx else { return nil }
        return CGFloat(h)
    }

    /// The box itself: used size, background color layer, literal content.
    /// Shared by both placement branches so they paint identically.
    @ViewBuilder
    private var box: some View {
        // A literal `content` string still has to render; the family's
        // `content: ""` renders nothing. Typography declarations are NOT
        // bridged (RootPseudo.spec logs each one), so this is the platform
        // default face — deliberately visible rather than silently styled.
        Group {
            if spec.text.isEmpty {
                Color.clear
            } else {
                Text(spec.text)
            }
        }
        .frame(width: usedWidth, height: usedHeight, alignment: .topLeading)
        // css-backgrounds-3 §2.11 color layer, resolved through the SAME
        // literal parser the var()-substitution path uses — never a second
        // color table here. A gradient / url() / unknown keyword parses to
        // nil and is named by the caller, not quietly painted transparent.
        .background(backgroundColor ?? Color.clear)
    }

    /// The parsed background color, or nil when the literal is not one.
    private var backgroundColor: Color? {
        guard let css = spec.backgroundCSS else { return nil }
        guard let rgba = CSSTokenParser.color(css) else {
            PropertyTracker.logOnce(
                key: "rootpseudo-bg-\(css)",
                message: "[RootPseudo] background '\(css)' is not a color literal — not painted")
            return nil
        }
        return Color(.sRGB, red: rgba.r, green: rgba.g, blue: rgba.b, opacity: rgba.a)
    }
}
