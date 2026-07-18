//
//  DynamicValueResolver.swift
//  StyleEngine/core/variables — wave 6 (dynamic values, issue #32).
//
//  The extraction-time resolution pass: rewrites a component's property
//  list so every preserved dynamic value — var() references and calc()
//  expressions the converter ships verbatim (null-px originals,
//  schema/spec/02-values.md) — becomes the concrete IR shape the
//  existing extractors already understand:
//
//    { "expr": "var(--pad)" }                    → { "px": 8.0 }
//    { "type":"expression","expr":"calc(…)" }    → { "px": N } / percentage
//    { "original": "var(--tile-a)" }             → { "srgb": {…}, … }
//    FontSize { "original": { expr } }           → { "px": N }
//    LineHeight { "original": {type:length,…} }  → { "px": N }
//    Generic border-*-radius rawValue var(…)     → typed radius { "px": N }
//
//  Declarations whose reference is guaranteed-invalid (undefined name,
//  no fallback — css-variables-1 §3.1) are DROPPED, which is exactly
//  `unset`: background-color falls to transparent, width to auto. The
//  token fixtures are designed so that degrade path is visible.
//
//  ComponentRenderer runs this ONCE per component, before
//  StyleBuilder.build, with the merged VariableStore (slot-parent chain)
//  and the wave-3/#39 geometry channels as the calc bases.
//

import Foundation

enum DynamicValueResolver {

    /// Generic-property escape hatch: longhands whose parser still
    /// degrades var() to Generic (border-radius corners today). Maps the
    /// raw CSS name back to the typed IR property so the resolved value
    /// re-enters the normal extractor path.
    private static let genericRetype: [String: String] = [
        "border-top-left-radius":     "BorderTopLeftRadius",
        "border-top-right-radius":    "BorderTopRightRadius",
        "border-bottom-right-radius": "BorderBottomRightRadius",
        "border-bottom-left-radius":  "BorderBottomLeftRadius",
    ]

    /// Height-family types: % inside their calc() would need a definite
    /// containing-block HEIGHT, which no channel supplies — those terms
    /// fail honestly (web parity: % height of an auto parent is auto).
    private static let heightFamily: Set<String> = [
        "Height", "MinHeight", "MaxHeight", "BlockSize",
        "MinBlockSize", "MaxBlockSize",
    ]

    /// Rewrite `properties`, resolving every dynamic value against the
    /// element's variable scope + calc bases. Pure — fully pinned by
    /// DynamicValuesTests. `inheritedFontSizePx` is the parent's
    /// computed font size (inheritance channel): it is the em basis for
    /// the element's OWN FontSize declaration (css-values-4 §6.1 — em on
    /// font-size refers to the inherited size).
    static func resolve(properties: [IRProperty],
                        variables: VariableStore,
                        calc baseCtx: CalcEvaluator.EvalContext,
                        inheritedFontSizePx: Double = 16.0) -> [IRProperty] {
        // Fast path: nothing dynamic AND nothing in scope → identity.
        // (Dynamic shapes can exist without variables: plain calc().)
        var out: [IRProperty] = []
        out.reserveCapacity(properties.count)

        // ── Pass 0: the element's font size ─────────────────────────
        // Resolve FontSize FIRST because it is the em basis for every
        // other property (css-values-4 §6.1). Last FontSize wins,
        // matching FontSizeExtractor's last-wins scan.
        var elementFontSizePx = inheritedFontSizePx
        for p in properties where p.type == "FontSize" {
            // em inside font-size resolves against the INHERITED size.
            var fsCtx = baseCtx
            fsCtx.fontSizePx = inheritedFontSizePx
            // Static px shape → adopt directly; dynamic → resolve now.
            if let px = staticPx(p.data) {
                elementFontSizePx = px
            } else if let px = resolveFontSizeWire(p.data,
                                                  inheritedFontSizePx: inheritedFontSizePx) {
                // Lane IOS-TEXT fix 4 — the converter's px-less relative
                // shapes (em/rem/% lengths, smaller/larger keywords)
                // resolve here against the inherited size; pass 1 emits
                // the resolved pixels for the extractors.
                elementFontSizePx = px
            } else if let expr = dynamicExpression(p.data),
                      let sub = VarSubstitutor.substitute(expr, store: variables),
                      let px = CalcEvaluator.evaluatePx(sub, ctx: fsCtx) {
                elementFontSizePx = px
            }
        }
        // Every other property's em basis is the element's size.
        var ctx = baseCtx
        ctx.fontSizePx = elementFontSizePx

        // ── Pass 1: rewrite each declaration ────────────────────────
        for prop in properties {
            // Generic escape hatch: retype known raw longhands whose
            // value carries a var()/calc() the parser couldn't keep.
            if prop.type == "Generic",
               case .object(let o) = prop.data,
               let cssName = o["propertyName"]?.stringValue,
               let typed = genericRetype[cssName],
               let rawValue = o["rawValue"]?.stringValue {
                // Substitute, then classify as a length token.
                if let rewritten = resolveLength(rawValue, type: typed,
                                                 variables: variables, ctx: ctx) {
                    out.append(IRProperty(type: typed, data: rewritten))
                }
                // Unresolvable → dropped (unset — same as unknown Generic).
                continue
            }

            // Lane IOS-TEXT fix 4 — FontSize relative wire shapes
            // (em/rem/% lengths, smaller/larger) are px-less objects that
            // dynamicExpression classifies as static, so they used to
            // pass through untouched and FontSizeExtractor read nil
            // (silently dropping `font-size: 1.5em` etc.). Emit the
            // resolved pixels computed against the inherited basis so
            // every downstream extractor sees the universal `{px:N}`.
            if prop.type == "FontSize",
               let px = resolveFontSizeWire(prop.data,
                                            inheritedFontSizePx: inheritedFontSizePx) {
                out.append(IRProperty(type: prop.type,
                                      data: .object(["px": .double(px)])))
                continue
            }

            // Applier campaign (line-height wire) — LineHeight LENGTH
            // values ride a THIRD-generation nested wire with NO
            // top-level px (pinned against live converter output on
            // fixtures/properties/typography/line-height.json):
            //   'line-height: 24px'  → {"original":{"type":"length","px":24.0}}
            //   'line-height: 2rem'  → {"original":{"type":"length",
            //                            "original":{"v":2.0,"u":"REM"}}}
            // LineHeightExtractor's top-level extractPx read nil on ALL
            // of these, so px/em/rem line-heights silently fell back to
            // natural metrics (pixel-proven: iOS inkTop 8 vs web/Android
            // 13 on Rem_2). Unwrap HERE — not in the extractor — because
            // em needs the font-size channel: line-height em resolves
            // against the element's OWN font size (css-values-4 §6.1),
            // which pass 0 just computed. Multiplier/percentage/normal
            // shapes return nil below and keep their extractor lane.
            if prop.type == "LineHeight",
               let px = resolveLineHeightWire(prop.data,
                                              ownFontSizePx: elementFontSizePx) {
                // Emit the universal `{px:N}` every extractor reads.
                out.append(IRProperty(type: prop.type,
                                      data: .object(["px": .double(px)])))
                continue
            }

            // Not a dynamic shape → pass through untouched (byte-stable
            // for the whole non-token fixture corpus).
            guard let expr = dynamicExpression(prop.data) else {
                out.append(prop)
                continue
            }

            // FontSize was fully handled in pass 0 — emit its resolved
            // pixels so FontSizeExtractor/SpacingContext agree with the
            // em basis used above.
            if prop.type == "FontSize" {
                out.append(IRProperty(type: prop.type,
                                      data: .object(["px": .double(elementFontSizePx)])))
                continue
            }

            // var() substitution (§2.3). Guaranteed-invalid → drop the
            // declaration = unset (§3.1) — the visible degrade path.
            // Logged per spec 02 §"Resolution order" (never silent).
            guard let substituted = VarSubstitutor.substitute(expr, store: variables)
            else {
                print("[DynamicValues] \(prop.type): guaranteed-invalid var() in '\(expr)' → unset")
                continue
            }

            // Colors ride `original` string shapes; lengths ride expr
            // shapes. Try the color lane first ONLY for original-string
            // shapes (a length can't arrive there); expr shapes go
            // straight to the length lane.
            if isOriginalStringShape(prop.data),
               let c = CSSTokenParser.color(substituted) {
                // Rebuild the converter's static color shape so
                // extractColor takes its normal srgb path.
                out.append(IRProperty(type: prop.type, data: .object([
                    "srgb": .object(["r": .double(c.r), "g": .double(c.g),
                                     "b": .double(c.b), "a": .double(c.a)]),
                    "original": .string(substituted),
                ])))
                continue
            }
            // `currentColor` via var: keep the dynamic classification —
            // extractColor's currentColor lane handles it downstream.
            if isOriginalStringShape(prop.data),
               substituted.trimmingCharacters(in: .whitespacesAndNewlines)
                   .lowercased() == "currentcolor" {
                out.append(IRProperty(type: prop.type,
                                      data: .object(["original": .string("currentColor")])))
                continue
            }

            // Length lane — px / % / keyword / calc.
            if let rewritten = resolveLengthToken(substituted, type: prop.type, ctx: ctx) {
                out.append(IRProperty(type: prop.type, data: rewritten))
            } else {
                // Unresolvable after substitution → invalid at
                // computed-value time (§3.1) → unset → dropped. Logged
                // per spec 02 §"Resolution order" (never silent).
                print("[DynamicValues] \(prop.type): unresolvable '\(substituted)' → unset")
            }
        }
        return out
    }

    // MARK: - Lanes

    /// Substitute + classify a raw length string (Generic lane).
    private static func resolveLength(_ raw: String, type: String,
                                      variables: VariableStore,
                                      ctx: CalcEvaluator.EvalContext) -> IRValue? {
        guard let substituted = VarSubstitutor.substitute(raw, store: variables)
        else { return nil }
        return resolveLengthToken(substituted, type: type, ctx: ctx)
    }

    /// Classify a substituted length token into the IR shape the
    /// existing LengthValue extractor reads.
    private static func resolveLengthToken(_ substituted: String, type: String,
                                           ctx: CalcEvaluator.EvalContext) -> IRValue? {
        guard let token = CSSTokenParser.length(substituted) else { return nil }
        switch token {
        // Absolute pixels — the universal `{ "px": N }` shape.
        case .px(let n):
            return .object(["px": .double(n)])
        // Percent stays symbolic: sizing resolves it against the live
        // containing-block channel, spacing against the parent width.
        case .percent(let v):
            return .object(["type": .string("percentage"), "value": .double(v)])
        // Keywords re-enter through the `{ "keyword": … }` legacy shape.
        case .keyword(let kw):
            return .object(["keyword": .string(kw)])
        // calc()/em/rem/vw… — evaluate with the wave-6 bases. Percent
        // terms need a width basis; the height family has none (see
        // heightFamily) so those evaluate with basis nil and fail
        // honestly when a % term appears.
        case .expression(let e):
            var exprCtx = ctx
            if heightFamily.contains(type) { exprCtx.percentBasisPx = nil }
            guard let px = CalcEvaluator.evaluatePx(e, ctx: exprCtx) else { return nil }
            return .object(["px": .double(px)])
        }
    }

    // MARK: - Shape detection

    /// The preserved-expression string inside a dynamic IR shape, or nil
    /// when the value is fully static. Recognised shapes (verified
    /// against live converter output on fixtures/fidelity/tokens):
    ///   { "expr": "…" }                             (spacing lane)
    ///   { "type": "expression", "expr": "…" }       (sizing lane)
    ///   { "original": "var(…)" } with NO srgb       (color lane)
    ///   { "original": { "type":"expression", … } }  (font-size lane)
    static func dynamicExpression(_ data: IRValue) -> String? {
        guard case .object(let o) = data else { return nil }
        // Static colors carry srgb — never dynamic, never rewritten.
        if o["srgb"] != nil { return nil }
        // Direct expr key (both spacing `{expr}` and sizing
        // `{type:expression, expr}` shapes).
        if let e = o["expr"]?.stringValue { return e }
        // original as STRING: dynamic only when it holds a var() ref —
        // plain originals ("#111827") ride next to srgb and are static.
        if let s = o["original"]?.stringValue {
            return s.contains("var(") ? s : nil
        }
        // original as OBJECT holding a nested expression (FontSize).
        if let inner = o["original"]?.objectValue,
           let e = inner["expr"]?.stringValue { return e }
        return nil
    }

    /// True for the color-family wire shape `{ "original": "<string>" }`
    /// — the only shape whose substituted value may be a color token.
    private static func isOriginalStringShape(_ data: IRValue) -> Bool {
        guard case .object(let o) = data else { return false }
        return o["original"]?.stringValue != nil
    }

    /// Static `{ "px": N }` reader for pass 0 (FontSize fast path).
    private static func staticPx(_ data: IRValue) -> Double? {
        guard case .object(let o) = data else { return nil }
        return o["px"]?.doubleValue
    }

    // MARK: - FontSize relative wire shapes (lane IOS-TEXT fix 4)

    /// Resolve the converter's px-less FontSize shapes against the
    /// inherited font size. Live wire shapes (FontSizeSerializer.kt via
    /// the IRPropertySerializer deep-flatten):
    ///   • em/rem length : { "original": { "type":"length",
    ///                       "original": { "v": 1.5, "u": "EM"|"REM" } } }
    ///   • percentage    : { "original": { "type":"percentage", "value": 120.0 } }
    ///   • smaller/larger: { "original": { "type":"relative",
    ///                       "keyword": "smaller"|"larger" } }
    /// Bases (css-values-4 §6.1 — em ON font-size refers to the
    /// INHERITED size; the renderer's inheritance channel supplies it,
    /// 16 when unset): em × inherited, rem × 16 (harness root), % of the
    /// inherited size. `smaller`/`larger` are one step down/up per
    /// CSS 2.1 §15.7 — approximated as ÷1.2 / ×1.2, the classic UA
    /// scaling-factor ratio between adjacent absolute-size keywords
    /// (documented approximation; the fixture set only checks one step).
    /// Returns nil for every other shape (static px, expressions, other
    /// units) so those keep their existing lanes. Pure — pinned by
    /// IOSTextLaneTests.
    static func resolveFontSizeWire(_ data: IRValue,
                                    inheritedFontSizePx: Double) -> Double? {
        guard case .object(let o) = data else { return nil }
        // A resolved px value is authoritative — never second-guess it.
        guard o["px"]?.doubleValue == nil else { return nil }
        // All relative shapes live under the `original` discriminator.
        guard case .object(let orig)? = o["original"] else { return nil }
        switch orig["type"]?.stringValue {
        case "length":
            // Nested IRLength: only the font-relative units resolve here
            // (vw/vh etc. ride the expression lane with real bases).
            guard case .object(let inner)? = orig["original"],
                  let v = inner["v"]?.doubleValue,
                  let u = inner["u"]?.stringValue?.uppercased() else { return nil }
            switch u {
            case "EM":  return v * inheritedFontSizePx    // §6.1 inherited basis
            case "REM": return v * 16.0                   // harness root = 16px
            default:    return nil                        // not ours — other lanes
            }
        case "percentage":
            // font-size: P% = P/100 × inherited size (css-fonts-4 §3.2).
            guard let p = orig["value"]?.doubleValue else { return nil }
            return p / 100.0 * inheritedFontSizePx
        case "relative":
            // CSS 2.1 §15.7 relative keywords: ±1 step on the UA table.
            switch orig["keyword"]?.stringValue?.lowercased() {
            case "larger":  return inheritedFontSizePx * 1.2
            case "smaller": return inheritedFontSizePx / 1.2
            default:        return nil
            }
        default:
            return nil
        }
    }

    // MARK: - LineHeight nested length wire (applier campaign)

    /// Resolve the converter's nested LineHeight LENGTH wire to pixels.
    /// Live wire shapes (LineHeightSerializer via the IRPropertySerializer
    /// deep-flatten, pinned by re-running the converter on
    /// fixtures/properties/typography/line-height.json):
    ///   • absolute px : { "original": { "type":"length", "px": 24.0 } }
    ///   • em/rem      : { "original": { "type":"length",
    ///                     "original": { "v": 2.0, "u": "EM"|"REM" } } }
    /// Bases (css-values-4 §6.1): em on line-height resolves against the
    /// element's OWN computed font size — NOT the inherited size the
    /// FontSize resolver uses, which is why this needs its own function
    /// instead of reusing resolveFontSizeWire — and rem against the
    /// 16px harness root. Returns nil for every other shape so
    /// unitless multipliers ({"multiplier":N}), percentages (which the
    /// converter already pre-folds into a multiplier), `normal`, and
    /// calc() expressions all keep their existing lanes. Pure — pinned
    /// by LineHeightWireTests.
    static func resolveLineHeightWire(_ data: IRValue,
                                      ownFontSizePx: Double) -> Double? {
        guard case .object(let o) = data else { return nil }
        // A top-level px would be authoritative — never second-guess it
        // (future-proofs against the converter flattening this wire).
        guard o["px"]?.doubleValue == nil else { return nil }
        // Only the nested LENGTH discriminator is ours; number /
        // percentage / "normal" shapes ride the multiplier lane.
        guard case .object(let orig)? = o["original"],
              orig["type"]?.stringValue == "length" else { return nil }
        // Absolute lengths carry resolved pixels one level down.
        if let px = orig["px"]?.doubleValue { return px }
        // Font-relative lengths carry the raw {v, u} IRLength deeper.
        guard case .object(let inner)? = orig["original"],
              let v = inner["v"]?.doubleValue,
              let u = inner["u"]?.stringValue?.uppercased() else { return nil }
        switch u {
        case "EM":  return v * ownFontSizePx  // §6.1 — the element's OWN size
        case "REM": return v * 16.0           // harness root font size = 16px
        default:    return nil                // vw/vh etc. — not this wire
        }
    }
}
