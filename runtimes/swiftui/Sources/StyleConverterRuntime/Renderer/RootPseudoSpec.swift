//
//  RootPseudoSpec.swift
//  Renderer — wave-28 lane PG.
//
//  The PURE half of the root-scope generated-box path: raw `pseudos`
//  declarations → a tiny paintable box spec, plus the containment gate
//  that decides whether the body's own `direction` may place that box.
//  No SwiftUI types here, so every rule is XCTest-pinnable without a
//  simulator. Twinned 1:1 by Compose's RootPseudoSpec.kt.
//
//  ── WHY THIS EXISTS ──────────────────────────────────────────────────
//  The WPT extractor merges every `html` / `body` / `:root` / `*` rule
//  into ONE synthetic component tagged `meta.role: "body-root"`, and
//  since wave-27 A-RC2 it routes root-scope PSEUDO-ELEMENT rules
//  (`html::before { content:""; width:100px; height:100px;
//  background:orange; display:block }`) into that component's `pseudos`
//  bucket instead of vandalising its flat declaration bag. This runtime
//  decoded the bucket and rendered NOTHING from it, so the whole
//  css-contain/contain-body-dir family painted no orange square —
//  MEASURED in tools/titan/runs/wave27-final/sections/css-contain: the
//  Chromium ref has 10 000 orange px at image [16,16]-[115,115], the iOS
//  and Android captures have ZERO.
//
//  ── WHY A NARROW BRIDGE AND NOT THE FULL ENGINE ──────────────────────
//  The bucket's `properties` is a RAW CSS declarations map (spec 01: the
//  extractor's payload, forwarded verbatim), not a typed IR property
//  list — this runtime has no CSS parser to turn `"100px"` into a typed
//  Length. Web escapes that by handing the map to the DOM as inline
//  styles; the natives cannot. So this bridges the SMALL declaration set
//  the root-scope family actually uses and LOGS everything else, per the
//  repo's no-silent-fallthrough rule.
//

import Foundation

/// A root-scope generated box, reduced to what a native runtime can paint.
struct RootPseudoSpec: Equatable {
    /// Used inline size in CSS px; nil for `auto`.
    var widthPx: Double?
    /// Used block size in CSS px; nil for `auto`.
    var heightPx: Double?
    /// The raw `background` / `background-color` literal, left as a STRING
    /// so the caller resolves it through the platform's own color parser
    /// (`CSSTokenParser.color`) rather than this module growing a second
    /// color table.
    var backgroundCSS: String?
    /// The literal `content` string, already unquoted. Empty for
    /// `content: ""` (the whole contain family) and for unresolvable
    /// `counter()` / `attr()` / `url()` values, which are logged.
    var text: String = ""
    /// Whether `display` generates a block-level box — the only display
    /// this bridge can place in the block flow.
    var isBlock: Bool = false

    /// Is there anything to draw? A box with neither a background nor a
    /// content string paints nothing at any size, so the renderer skips it
    /// rather than emitting an invisible full-width shim.
    var paints: Bool { backgroundCSS != nil || !text.isEmpty }
}

enum RootPseudo {

    /// Bridge one `pseudos.<role>` bucket to a ``RootPseudoSpec``, or nil
    /// when the bucket carries no declarations at all.
    ///
    /// - Parameters:
    ///   - bucket: the wire object — `{ "properties": { … } }`, the shape
    ///     the extractor emits and the decoder forwards verbatim.
    ///   - role: "before" / "after", for the log lines only.
    static func spec(bucket: IRValue?, role: String) -> RootPseudoSpec? {
        // Declarations live one level down; anything else is not a bucket.
        guard let decls = bucket?["properties"]?.objectValue, !decls.isEmpty else { return nil }
        var out = RootPseudoSpec()
        for (prop, raw) in decls {
            // Only string primitives are declaration values here; a nested
            // object means the payload is not a declarations map at all.
            guard let value = raw.stringValue else {
                log(key: "rootpseudo-nonprimitive-\(prop)",
                    message: "::\(role) declaration '\(prop)' is not a string — dropped")
                continue
            }
            switch prop.trimmingCharacters(in: .whitespaces).lowercased() {
            // css-sizing-3 §5 used size. Absolute px only — %, em, calc()
            // and var() are runtime-dependent by the IR's own rule.
            case "width":  out.widthPx = px(value, prop: prop, role: role)
            case "height": out.heightPx = px(value, prop: prop, role: role)
            // css-backgrounds-3 §2.11: the color layer is the only
            // background component this box path paints.
            case "background", "background-color":
                out.backgroundCSS = value.trimmingCharacters(in: .whitespaces)
            // css-display-3 §2: only a block-level box can be placed in the
            // root's block flow by the box builder.
            case "display": out.isBlock = isBlockDisplay(value)
            // css-content-3 §2: the literal string is the only content type
            // resolvable without a counter/attr context.
            case "content": out.text = contentLiteral(value, role: role)
            // No silent fallthrough: everything else is a real declaration
            // this bridge cannot honour, so it is named in the log.
            default:
                log(key: "rootpseudo-unsupported-\(prop)",
                    message: "::\(role) declaration '\(prop): \(value)' unsupported by the root-scope box bridge")
            }
        }
        return out
    }

    /// `<length>` in absolute px, or nil (with a log) for every other unit.
    /// A bare `0` is legal CSS (css-values-4 §5.2) and resolves to zero.
    static func px(_ value: String, prop: String, role: String) -> Double? {
        let t = value.trimmingCharacters(in: .whitespaces).lowercased()
        if t == "0" { return 0 }
        if t.hasSuffix("px"), let n = Double(t.dropLast(2)) { return n }
        log(key: "rootpseudo-length-\(prop)-\(t)",
            message: "::\(role) '\(prop): \(value)' is not an absolute px length — treated as auto")
        return nil
    }

    /// Does this `display` value generate a BLOCK-LEVEL box? Token scan
    /// rather than equality so css-display-3 §2's two-value syntax
    /// (`block flow`) passes too; `list-item` is block-level by §2.1.
    static func isBlockDisplay(_ value: String) -> Bool {
        let tokens = value.trimmingCharacters(in: .whitespaces).lowercased()
            .split(whereSeparator: { $0.isWhitespace }).map(String.init)
        return tokens.contains("block") || tokens.contains("list-item") || tokens.contains("flow-root")
    }

    /// The literal `content` string with its quotes removed. `none` /
    /// `normal` generate no box (css-content-3 §2.1) and every function
    /// form (counter() / attr() / url()) needs context this bridge does not
    /// have — all resolve to the empty string, the functions loudly.
    static func contentLiteral(_ value: String, role: String) -> String {
        let t = value.trimmingCharacters(in: .whitespaces)
        if t.lowercased() == "none" || t.lowercased() == "normal" { return "" }
        if t.count >= 2,
           (t.hasPrefix("\"") && t.hasSuffix("\"")) || (t.hasPrefix("'") && t.hasSuffix("'")) {
            return String(t.dropFirst().dropLast())
        }
        log(key: "rootpseudo-content-\(t)",
            message: "::\(role) 'content: \(t)' is not a literal string — rendered as empty")
        return ""
    }

    /// wave-28 lane PG — does this body-root's containment take the body
    /// OFF the writing-mode/direction propagation path?
    ///
    /// css-writing-modes-4 §3.2 propagates the BODY's `direction` to the
    /// viewport (the ONLY channel by which a body declaration can reach a
    /// box generated on the root), and css-contain-1 §3.1 removes a
    /// contained body from that channel. So on a contained body-root the
    /// generated box keeps the root's own inline direction — the initial
    /// `ltr`, i.e. physically LEFT, which is exactly what the family's
    /// shared reference (contain-body-w-m-001-ref.html, "Test passes if the
    /// orange square is in the upper-left corner") asserts.
    ///
    /// Delegates to ``WPTCanvas/containmentBlocksPropagation(_:)`` so the
    /// two propagation questions can never drift apart: neither spec grades
    /// propagation by containment KIND, and the corpus proves it —
    /// contain-body-dir-001..004 declare layout / paint / size / style
    /// (VERIFIED against the corpus sources, wave-28 skeptic — an earlier
    /// draft of this comment said "content / strict" for 003/004, which the
    /// test files do not declare)
    /// and all four match the same reference. The merged html+body caveat
    /// documented there applies here verbatim (one bag means `html::before`
    /// and `body::before` are indistinguishable too).
    static func containmentBlocksDirectionPropagation(_ containTokens: [String]?) -> Bool {
        WPTCanvas.containmentBlocksPropagation(containTokens)
    }

    /// Every diagnostic from this bridge goes through the shared dedupe so
    /// a repeated render cannot flood the capture log.
    private static func log(key: String, message: String) {
        PropertyTracker.logOnce(key: key, message: "[RootPseudo] \(message)")
    }
}
