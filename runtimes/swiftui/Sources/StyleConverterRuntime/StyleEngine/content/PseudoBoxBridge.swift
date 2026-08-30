//
//  PseudoBoxBridge.swift
//  StyleEngine/content — wave 49, lane A2 (the ordinary-element
//  generated-BOX path). The SwiftUI twin of Compose's
//  content/PseudoGeneratedBox.kt, rule for rule.
//
//  ── THE MEASURED DEFECT ──────────────────────────────────────────────
//  WPT css-pseudo/before-as-flex-container declares
//    div         { width:200px; height:100px; background:red }
//    div::before { content:"A B"; display:flex; justify-content:space-between;
//                  width:200px; height:100px; background:green }
//  The Chromium ref is a 200×100 GREEN rectangle — the generated box is
//  the div's first child and covers its red background exactly. iOS
//  painted the whole 200×100 in RED (tools/titan/runs/wave48-final/
//  sections/css-pseudo/report/images/iOS/wpt__css-pseudo__before-as-flex-
//  container.png, ios-ref 0.9990 but wptPass FALSE: colorFailed, green
//  histogram-KL 0.515) because PseudoTextBridge refuses ANY bucket that
//  declares a box — display/width/height/background all land in its
//  "beyond the inline-text fold" branch, so nothing rendered at all.
//
//  ── WHAT THIS FILE IS ────────────────────────────────────────────────
//  The PURE half: one raw `pseudos.<role>` declarations map → the TYPED
//  IRProperty list + text of the box it generates, or nil when this path
//  does not own the bucket (then PseudoTextBridge's inline fold decides,
//  byte-identically to wave 48). No SwiftUI types, so every rule is
//  XCTest-pinnable without a simulator.
//
//  ── WHY A NARROW BRIDGE ──────────────────────────────────────────────
//  `properties` is a RAW CSS declarations map (spec 01-envelope.md
//  forwards the extractor payload verbatim) and the runtime has no CSS
//  parser, so only the declaration set below converts; ANY other
//  declaration refuses the whole claim and is named (repo rule: no silent
//  fallthroughs — a half-typed box would paint a green rectangle where the
//  author asked for a positioned or bordered one). `position` is
//  deliberately outside the set, which is what keeps the four
//  css-anchor-position buckets on their current render.
//

import Foundation

/// Pure bridge — never instantiated (mirrors PseudoTextBridge).
enum PseudoBoxBridge {

    /// A generated box this bridge can build.
    struct BoxClaim {
        /// Typed IR entries fed to a synthetic child component, so the ONE
        /// existing style pipeline paints them (never a second channel).
        let properties: [IRProperty]
        /// The box's resolved content string; may be empty for
        /// `content: ""`, which still paints a background (css-content-3 §2).
        let text: String
    }

    /// css-display-3 §2.1 BLOCK-LEVEL outer display types. Only these take
    /// this path: an inline-level pseudo participates in the host's INLINE
    /// flow, which is exactly what PseudoTextFold's text fold models —
    /// re-routing it here would move ink for buckets nothing is wrong with
    /// (css-cascade/scope-pseudo-element's `display: inline-block` pair).
    static let blockLevelDisplay: Set<String> =
        ["block", "flow-root", "list-item", "flex", "grid", "table"]

    /// css-align-3 §5.1 single `justify-content` keywords, raw → the wire's
    /// SCREAMING_SNAKE spelling (censused across wave48-final per-test-ir:
    /// FLEX_END, CENTER, SPACE_BETWEEN, END, SPACE_AROUND, SPACE_EVENLY,
    /// FLEX_START, STRETCH). Two-token `<overflow-position>
    /// <content-position>` values are absent on purpose — they refuse,
    /// named, rather than losing the safety qualifier silently.
    static let justifyContent: Set<String> = [
        "flex-start", "flex-end", "center", "space-between", "space-around",
        "space-evenly", "start", "end", "left", "right", "normal", "stretch",
    ]

    /// The generated box for one bucket, or nil when this path does not own
    /// it — in which case the caller keeps its wave-48 behaviour exactly.
    ///
    /// - Parameters:
    ///   - bucket: the wire object `{ "properties": {…}, "_text": "…" }`,
    ///     forwarded verbatim by the decoder (spec 01-envelope.md).
    ///   - role: "before" / "after" — log lines and nothing else.
    ///   - hostRole: the originating component's `meta.role`. A "body-root"
    ///     bucket is the ROOT-SCOPE box owned by RootPseudoBox (wave-28
    ///     lane PG); claiming it here would double-render that box.
    static func claim(bucket: IRValue?, role: String, hostRole: String?) -> BoxClaim? {
        // Root-scope buckets belong to RootPseudoBox — see `hostRole`.
        guard hostRole != "body-root" else { return nil }
        // No declarations map = a bare `_text` bucket: an inline run, not a
        // box. Nothing to claim, and nothing to log (this is the norm).
        guard let b = bucket, let decls = b["properties"]?.objectValue else { return nil }
        // GATE 1 — a box path needs a BLOCK-LEVEL `display`. Read before the
        // walk so an inline/undeclared bucket costs no log line at all.
        guard let displayKeyword = blockLevelDisplayKeyword(decls) else { return nil }
        // Typed accumulator, seeded with the display the gate resolved.
        var properties: [IRProperty] = [IRProperty(type: "Display", data: .string(displayKeyword))]
        // A box with neither background nor glyphs paints nothing (GATE 3).
        var paintsBackground = false
        // GATE 2 — walk EVERY declaration, key order sorted so a multi-key
        // refusal logs deterministically (same idiom as PseudoTextBridge).
        for prop in decls.keys.sorted() {
            // Declaration values are string primitives; anything else means
            // the payload is not a declarations map at all.
            guard let value = decls[prop]?.stringValue else {
                return refuse(role, prop, "value is not a primitive")
            }
            switch prop.trimmingCharacters(in: .whitespaces).lowercased() {
            // Consumed by the text resolution below / by GATE 1.
            case "content", "display":
                continue
            // css-sizing-3 §3.1.1 used inline/block size. Absolute px only — %,
            // em, calc() and var() are runtime-dependent by the IR's own
            // rule (CLAUDE.md "null means runtime-dependent").
            case "width", "height":
                guard let p = lengthProperty(
                    prop.lowercased() == "width" ? "Width" : "Height", value) else {
                    return refuse(role, prop, "is not an absolute px length")
                }
                properties.append(p)
            // css-backgrounds-3 §2.11: the only background component a box
            // this small paints is the colour layer, so the shorthand is
            // accepted exactly when it IS a bare colour.
            case "background", "background-color":
                guard let p = colorProperty(value) else {
                    return refuse(role, prop, "is not a parseable colour literal")
                }
                properties.append(p)
                paintsBackground = true
            // css-align-3 §5.1 — the box's own content distribution.
            case "justify-content":
                guard let p = keywordProperty("JustifyContent", value) else {
                    return refuse(role, prop, "is not a single justify-content keyword")
                }
                properties.append(p)
            // css-lists-3 §4 counter mutations are INPUTS to the producer's
            // bake: the `_text` read below already contains their effect.
            case "counter-increment", "counter-reset", "counter-set":
                continue
            // No silent fallthrough: a declaration outside the set is a real
            // box property this bridge cannot type (border, position,
            // anchor-name, …) — refuse and name it.
            default:
                return refuse(role, prop, "is beyond the generated-box bridge")
            }
        }
        // The producer's baked resolution wins (the `generated-content-baked`
        // lossy marker names the bake); otherwise the literal string
        // sequence, which needs no counter/attr context. Both spellings
        // tolerated in web's order (PseudoNodeRenderer.ts `_text ?? text`).
        let text = b["_text"]?.stringValue ?? b["text"]?.stringValue
            ?? decls["content"]?.stringValue.map {
                RootPseudo.contentLiteral($0, role: role)
            } ?? ""
        // GATE 3 — a box with no background and no glyphs paints nothing at
        // any size, so emitting it would only add an invisible layout box
        // that displaces real content (mirrors RootPseudoSpec.paints).
        guard paintsBackground || !text.isEmpty else { return nil }
        return BoxClaim(properties: properties, text: text)
    }

    /// Named refusal (repo rule: no silent fallthroughs), then nil.
    private static func refuse(_ role: String, _ prop: String, _ why: String) -> BoxClaim? {
        PropertyTracker.logOnce(
            key: "pseudobox-unsupported-\(role)-\(prop)",
            message: "[PseudoBox] ::\(role) declaration '\(prop)' \(why) — generated box not built, bucket falls back to the inline path")
        return nil
    }

    /// The wire `Display` keyword when this bucket's `display` generates a
    /// BLOCK-LEVEL box, else nil. Token scan rather than equality so
    /// css-display-3 §2's two-value syntax (`block flow`) resolves on its
    /// outer keyword; `none` and `contents` generate no box of their own and
    /// stay with the existing bridges.
    private static func blockLevelDisplayKeyword(_ decls: [String: IRValue]) -> String? {
        guard let raw = decls.first(where: {
            $0.key.trimmingCharacters(in: .whitespaces).lowercased() == "display"
        })?.value.stringValue else { return nil }
        let tokens = raw.trimmingCharacters(in: .whitespaces).lowercased()
            .split(whereSeparator: { $0.isWhitespace }).map(String.init)
        // `none` / `contents` are exclusive in the display grammar and mean
        // "no box of my own" — never this path.
        if tokens.contains("none") || tokens.contains("contents") { return nil }
        guard let block = tokens.first(where: { blockLevelDisplay.contains($0) }) else { return nil }
        // Wire spelling: SCREAMING_SNAKE ("FLEX", "FLOW_ROOT", …).
        return block.uppercased().replacingOccurrences(of: "-", with: "_")
    }

    /// `<length>` in absolute px → the `{"type":"length","px":N}` datum
    /// every corpus Width/Height carries; nil for every other unit.
    private static func lengthProperty(_ type: String, _ value: String) -> IRProperty? {
        let t = value.trimmingCharacters(in: .whitespaces).lowercased()
        // A bare `0` is a legal length (css-values-4 §6) and means zero px.
        let px: Double
        if t == "0" { px = 0 }
        else if t.hasSuffix("px"), let v = Double(t.dropLast(2)) { px = v }
        else { return nil }
        return IRProperty(type: type, data: .object(["type": .string("length"), "px": .double(px)]))
    }

    /// A colour literal → the typed `{"srgb":{r,g,b,a}}` block the colour
    /// extractor reads, through the SHARED post-substitution token parser
    /// (one colour vocabulary with the var() lane, never a second table).
    private static func colorProperty(_ value: String) -> IRProperty? {
        guard let c = CSSTokenParser.color(value) else { return nil }
        return IRProperty(type: "BackgroundColor", data: .object([
            "srgb": .object(["r": .double(c.r), "g": .double(c.g),
                             "b": .double(c.b), "a": .double(c.a)]),
            // The author string, mirrored the way converter output does.
            "original": .string(value.trimmingCharacters(in: .whitespaces)),
        ]))
    }

    /// A single css-align-3 keyword → its SCREAMING_SNAKE wire spelling.
    private static func keywordProperty(_ type: String, _ value: String) -> IRProperty? {
        let t = value.trimmingCharacters(in: .whitespaces).lowercased()
        guard justifyContent.contains(t) else { return nil }
        return IRProperty(type: type,
                          data: .string(t.uppercased().replacingOccurrences(of: "-", with: "_")))
    }
}
