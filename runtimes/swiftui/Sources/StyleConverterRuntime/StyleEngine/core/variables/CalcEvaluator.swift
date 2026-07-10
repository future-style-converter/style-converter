//
//  CalcEvaluator.swift
//  StyleEngine/core/variables — wave 6 (dynamic values, issue #32).
//
//  Small css-values-3 §8 calc() evaluator over the PRESERVED original
//  expression string (the converter ships var()/calc() verbatim —
//  schema/spec/02-values.md). Grammar subset:
//
//    sum     := product (('+'|'-') product)*
//    product := unary (('*'|'/') unary)*
//    unary   := ['-'|'+'] term
//    term    := NUMBER unit? | '(' sum ')' | 'calc(' sum ')'
//
//  Terms are typed: a LENGTH (canonical px) or a bare NUMBER. §8.1 type
//  checking: +/- need matching types, * needs at least one number,
//  / needs a number divisor — anything else evaluates to nil so the
//  caller treats the declaration as unresolvable (→ unset), never as a
//  guessed pixel value.
//
//  EvalContext mirrors the wave-6 design: percent resolves against the
//  containing-block width channel (wave 3), em against the inherited /
//  element font-size (inheritance channel), rem against the 16px root
//  default the web harness uses, viewport units against the
//  environment-supplied viewport (#39).
//

import Foundation

enum CalcEvaluator {

    /// Everything a calc() term can need to become absolute pixels.
    /// Built by DynamicValueResolver from the renderer's channels.
    struct EvalContext {
        /// em basis — the element's font size (which IS the inherited
        /// size unless the element declares its own, css-values-4 §6.1).
        var fontSizePx: Double = 16.0
        /// rem basis — root font size; 16 to match the web harness root.
        var rootFontSizePx: Double = 16.0
        /// % basis — containing-block width in px (wave-3 channel).
        /// nil = indefinite → any % term makes the whole calc nil.
        var percentBasisPx: Double? = nil
        /// vw / vh bases — environment-supplied viewport (#39).
        var viewportWidth: Double = 390.0
        var viewportHeight: Double = 844.0
    }

    /// Intermediate value: px-typed length or unitless number (§8.1).
    private enum Value {
        case px(Double)
        case number(Double)
    }

    /// Evaluate a preserved expression ("calc(100% - 40px)", "64px",
    /// "calc(calc(100% - 20px) / 2)") to absolute pixels. nil when any
    /// term is unresolvable in `ctx` — caller decides the degrade path.
    static func evaluatePx(_ expr: String, ctx: EvalContext) -> Double? {
        // Tokenless scanner state: work on a whitespace-tolerant cursor.
        var s = Scanner(text: expr)
        // Parse one full sum, then require end-of-input (trailing junk
        // means we mis-parsed — refuse rather than half-apply).
        guard let v = parseSum(&s, ctx: ctx), s.atEnd else { return nil }
        // A bare number is not a length — "calc(2 * 3)" can't size a box.
        guard case .px(let px) = v else { return nil }
        return px
    }

    // MARK: - Recursive-descent parser

    /// sum := product (('+'|'-') product)*   — additive terms must both
    /// be lengths or both numbers (css-values-3 §8.1 type check).
    private static func parseSum(_ s: inout Scanner, ctx: EvalContext) -> Value? {
        guard var acc = parseProduct(&s, ctx: ctx) else { return nil }
        while let op = s.peekOperator(["+", "-"]) {
            s.consumeOperator()
            guard let rhs = parseProduct(&s, ctx: ctx) else { return nil }
            switch (acc, rhs) {
            // length ± length — the common case.
            case let (.px(a), .px(b)):
                acc = .px(op == "+" ? a + b : a - b)
            // number ± number — legal inside a numeric sub-expression.
            case let (.number(a), .number(b)):
                acc = .number(op == "+" ? a + b : a - b)
            // Mixed length ± number is a type error per §8.1.
            default: return nil
            }
        }
        return acc
    }

    /// product := unary (('*'|'/') unary)* — * needs ≥1 number side,
    /// / needs a non-zero number divisor (§8.1).
    private static func parseProduct(_ s: inout Scanner, ctx: EvalContext) -> Value? {
        guard var acc = parseUnary(&s, ctx: ctx) else { return nil }
        while let op = s.peekOperator(["*", "/"]) {
            s.consumeOperator()
            guard let rhs = parseUnary(&s, ctx: ctx) else { return nil }
            if op == "*" {
                switch (acc, rhs) {
                // length × number (either order) scales the length.
                case let (.px(a), .number(b)):     acc = .px(a * b)
                case let (.number(a), .px(b)):     acc = .px(a * b)
                case let (.number(a), .number(b)): acc = .number(a * b)
                // length × length has no CSS type (§8.1) — refuse.
                default: return nil
                }
            } else {
                // Division: divisor must be a number and non-zero (§8.1).
                guard case .number(let d) = rhs, d != 0 else { return nil }
                switch acc {
                case .px(let a):     acc = .px(a / d)
                case .number(let a): acc = .number(a / d)
                }
            }
        }
        return acc
    }

    /// unary := ['-'|'+'] term — sign folds into the value.
    private static func parseUnary(_ s: inout Scanner, ctx: EvalContext) -> Value? {
        let negative = s.consumeSign()
        guard let v = parseTerm(&s, ctx: ctx) else { return nil }
        guard negative else { return v }
        switch v {
        case .px(let a):     return .px(-a)
        case .number(let a): return .number(-a)
        }
    }

    /// term := NUMBER unit? | '(' sum ')' | 'calc(' sum ')' — nesting
    /// (`calc(calc(100% - 20px) / 2)`) recurses through parseSum.
    private static func parseTerm(_ s: inout Scanner, ctx: EvalContext) -> Value? {
        s.skipSpace()
        // Nested calc( — css-values-3 §8: calc() inside calc() is legal
        // and equivalent to plain parens.
        if s.consume(keyword: "calc(") || s.consume(keyword: "(") {
            guard let inner = parseSum(&s, ctx: ctx), s.consume(keyword: ")")
            else { return nil }
            return inner
        }
        // Otherwise: a dimension token — number + optional unit.
        guard let n = s.consumeNumber() else { return nil }
        let unit = s.consumeUnit()
        return typed(value: n, unit: unit, ctx: ctx)
    }

    /// Dimension → typed Value using the context bases. nil = a unit we
    /// can't resolve statically (unknown unit, % with no basis).
    private static func typed(value: Double, unit: String,
                              ctx: EvalContext) -> Value? {
        switch unit {
        // Unitless — a number term (multiplier / divisor).
        case "":     return .number(value)
        // Absolute units at the converter's 96dpi baseline
        // (schema/spec/02-values.md normalization table).
        case "px":   return .px(value)
        case "pt":   return .px(value * 4.0 / 3.0)
        case "pc":   return .px(value * 16.0)
        case "in":   return .px(value * 96.0)
        case "cm":   return .px(value * 96.0 / 2.54)
        case "mm":   return .px(value * 96.0 / 25.4)
        case "q":    return .px(value * 96.0 / 25.4 / 4.0)
        // Font-relative — the wave-6 EvalContext bases.
        case "em":   return .px(value * ctx.fontSizePx)
        case "rem":  return .px(value * ctx.rootFontSizePx)
        // Percent — containing-block width channel; indefinite → fail
        // honestly (the whole declaration degrades to unset/auto).
        case "%":
            guard let basis = ctx.percentBasisPx else { return nil }
            return .px(value / 100.0 * basis)
        // Viewport units — environment-supplied geometry (#39).
        case "vw":   return .px(value / 100.0 * ctx.viewportWidth)
        case "vh":   return .px(value / 100.0 * ctx.viewportHeight)
        case "vmin": return .px(value / 100.0 * min(ctx.viewportWidth, ctx.viewportHeight))
        case "vmax": return .px(value / 100.0 * max(ctx.viewportWidth, ctx.viewportHeight))
        // Anything else (ch/ex/cq*/fr/deg/s/…) is outside this
        // evaluator's remit — refuse, never guess.
        default:     return nil
        }
    }

    // MARK: - Character scanner

    /// Minimal cursor over the expression string. Kept private — the
    /// public surface is evaluatePx only.
    private struct Scanner {
        /// Remaining unparsed input.
        var rest: Substring
        init(text: String) { rest = Substring(text) }

        /// True when only whitespace remains.
        var atEnd: Bool {
            rest.allSatisfy { $0 == " " || $0 == "\t" || $0 == "\n" }
        }

        /// Drop leading whitespace.
        mutating func skipSpace() {
            while let c = rest.first, c == " " || c == "\t" || c == "\n" {
                rest = rest.dropFirst()
            }
        }

        /// Peek (don't consume) the next operator if it's in `ops`.
        func peekOperator(_ ops: [Character]) -> Character? {
            var copy = self
            copy.skipSpace()
            guard let c = copy.rest.first, ops.contains(c) else { return nil }
            return c
        }

        /// Consume the operator peeked above (single char).
        mutating func consumeOperator() {
            skipSpace()
            rest = rest.dropFirst()
        }

        /// Consume a leading sign on a term. Returns true for '-'.
        /// NOTE: css-values-3 requires whitespace around binary +/- ;
        /// the parser structure already disambiguates because signs are
        /// only consumed where a TERM is expected.
        mutating func consumeSign() -> Bool {
            skipSpace()
            if rest.first == "-" { rest = rest.dropFirst(); return true }
            if rest.first == "+" { rest = rest.dropFirst() }
            return false
        }

        /// Case-insensitive keyword consume ("calc(", "(", ")").
        mutating func consume(keyword: String) -> Bool {
            skipSpace()
            guard rest.lowercased().hasPrefix(keyword) else { return false }
            rest = rest.dropFirst(keyword.count)
            return true
        }

        /// Consume a decimal number ("100", "1.5", ".5"). nil when the
        /// cursor isn't at a digit or dot.
        mutating func consumeNumber() -> Double? {
            skipSpace()
            var digits = ""
            while let c = rest.first, c.isNumber || c == "." {
                digits.append(c)
                rest = rest.dropFirst()
            }
            return digits.isEmpty ? nil : Double(digits)
        }

        /// Consume a trailing unit token ("px", "em", "%", …) — letters
        /// or the percent sign, lowercased for the unit table.
        mutating func consumeUnit() -> String {
            if rest.first == "%" { rest = rest.dropFirst(); return "%" }
            var unit = ""
            while let c = rest.first, c.isLetter {
                unit.append(c)
                rest = rest.dropFirst()
            }
            return unit.lowercased()
        }
    }
}
