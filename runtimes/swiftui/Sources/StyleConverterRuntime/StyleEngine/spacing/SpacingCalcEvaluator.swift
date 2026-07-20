//
//  SpacingCalcEvaluator.swift
//  StyleEngine/spacing — wave-18 cleanup (skeptic B2): the real calc() evaluator.
//
//  Line-for-line port of the Compose evaluator (spacing/SpacingResolve.kt:
//  evalCalc / CalcParser / convertToPx) so identical IR resolves to identical
//  pixels on both natives. The old SpacingResolver.naiveCalcPx handled ONLY
//  two-term px/pt forms, so the LIVE wire shapes
//    {"type":"PaddingLeft","data":{"expr":"calc(1em + 4px)"}}
//    {"type":"Width","data":{"type":"expression","expr":"calc(2ch + 4px)"}}
//  (pinned via a converter run on minimal fixtures) resolved to .skip/0 on
//  iOS while Compose resolved the full 20px-style values.
//
//  Grammar (css-values-4 §10.1): + - * /, parentheses, nested calc(); the
//  ADDITIVE operators require whitespace around them (§10.1: "the + and -
//  operators must be surrounded by whitespace"). Units mirror the pin-table
//  arms of SpacingResolver.resolveRelative: px/pt/em/rem/%/ch/ex/ic/cap/lh/
//  rlh + the viewport, logical (vi/vb) and container-query axes.
//
//  calc-% asymmetry (skeptic B3 — shared with Compose BY DESIGN): the
//  appliers' WPT percent gates (Compose usesContainingBlockPercent, the iOS
//  percentBasisPx call sites) inspect BARE percent values only, so a
//  calc-with-% never enters the WPT tri-state lane and its % operand rides
//  each platform's LEGACY static base — Compose: parentWidthPx ?? viewport;
//  iOS: SpacingContext.containingBlockWidth. Keep the two sides in lockstep;
//  never "fix" one alone (the Compose twin carries the mirror comment).
//  EXCEPTION (skeptic follow-up): the INTRINSIC-sizing lane — Compose's
//  percentIndefiniteAsZero contexts zero the base inside evalCalc too, so
//  the iOS twin helpers (StyleBuilder horizontal/verticalPaddingPx) thread
//  SpacingContext.calcPercentBasisPx to match (see the % arm below).
//

// CoreGraphics for the CGFloat containingBlockWidth accessor; Foundation
// for the String trimming API.
import CoreGraphics
import Foundation

// Named SpacingCalcEvaluator (not CalcEvaluator) because core/variables/
// CalcEvaluator.swift already owns that name for the DYNAMIC-VALUES lane:
// a strict css-values-3 §8.1-typed evaluator that REFUSES unknown units
// and indefinite % bases (wave 6, issue #32). This one is the Compose-
// parity spacing/sizing lane — permissive where Compose is permissive
// (unknown unit → 0 operand, % → legacy static base) so the two natives
// stay byte-parallel. Do not merge the two without merging the Compose
// twin's semantics first.
enum SpacingCalcEvaluator {

    /// Public entry point — expression string in, absolute px out.
    /// Returns nil on malformed input; the caller (SpacingResolver) routes
    /// nil to the tracked `.skip`, which renders 0 — numerically identical
    /// to the Compose catch-arm's 0f, but auditable instead of silent.
    static func evalPx(_ raw: String, ctx: SpacingContext) -> Double? {
        // Strip exactly ONE `calc( … )` wrapper when present (nested calls
        // land inside already-stripped text during recursion) — mirrors
        // Compose evalCalc's single-layer strip.
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        var inner = trimmed
        if trimmed.lowercased().hasPrefix("calc("), trimmed.hasSuffix(")") {
            inner = String(trimmed.dropFirst(5).dropLast(1))
        }
        // Character array gives O(1) indexing — the Compose parser walks a
        // String by integer position; this is the Swift equivalent.
        var parser = Parser(src: Array(inner), ctx: ctx)
        return parser.parseExpression()
    }

    // Recursive-descent parser — the same grammar walk as Compose's
    // CalcParser, with nil replacing thrown exceptions for malformed input.
    private struct Parser {
        // Expression characters (already calc-stripped).
        let src: [Character]
        // Unit-resolution context — same struct the bare-unit resolver uses,
        // so a unit inside calc() can never disagree with the bare unit.
        let ctx: SpacingContext
        // Cursor into src.
        var pos = 0

        // Top level: + and - left-to-right (lowest precedence).
        mutating func parseExpression() -> Double? {
            // A malformed leading term poisons the whole expression.
            guard var left = parseTerm() else { return nil }
            while true {
                skipWs()
                // End of input — expression complete.
                guard pos < src.count else { break }
                let op = src[pos]
                // Only additive operators continue the loop at this level.
                if op != "+" && op != "-" { break }
                // css-values-4 §10.1: + and - MUST be whitespace-surrounded.
                // Without it, "20px-5px" is a single (malformed) token, not
                // a subtraction — same tie-break as the Compose parser.
                if !hasWsAround(pos) { break }
                pos += 1
                // Right operand must parse; otherwise the whole calc fails.
                guard let right = parseTerm() else { return nil }
                left = (op == "+") ? left + right : left - right
            }
            return left
        }

        // Term: * and / (higher precedence than + and -).
        private mutating func parseTerm() -> Double? {
            guard var left = parseFactor() else { return nil }
            while true {
                skipWs()
                guard pos < src.count else { break }
                let op = src[pos]
                if op != "*" && op != "/" { break }
                pos += 1
                guard let right = parseFactor() else { return nil }
                // Division by zero yields 0 (Compose parity — never traps).
                left = (op == "*") ? left * right
                                   : (right != 0 ? left / right : 0)
            }
            return left
        }

        // Factor: parenthesised group, nested calc(), or a length/number.
        private mutating func parseFactor() -> Double? {
            skipWs()
            // Plain parenthesised sub-expression.
            if pos < src.count, src[pos] == "(" {
                pos += 1  // consume '('
                guard let v = parseExpression() else { return nil }
                skipWs()
                if pos < src.count, src[pos] == ")" { pos += 1 }  // consume ')'
                return v
            }
            // Nested calc(...) — strip the wrapper and recurse.
            if matchesCalcPrefix() {
                pos += 5  // consume "calc("
                guard let v = parseExpression() else { return nil }
                skipWs()
                if pos < src.count, src[pos] == ")" { pos += 1 }
                return v
            }
            return parseLengthOrNumber()
        }

        // Case-insensitive "calc(" lookahead at the cursor.
        private func matchesCalcPrefix() -> Bool {
            guard pos + 5 <= src.count else { return false }
            return String(src[pos ..< pos + 5]).lowercased() == "calc("
        }

        // Length token or bare number. Bare numbers act as multipliers for
        // the surrounding * / — same convention as the Compose parser.
        private mutating func parseLengthOrNumber() -> Double? {
            skipWs()
            let start = pos
            // Optional leading sign, then digits with at most one dot
            // (Double(_:) rejects doubled dots for us below).
            if pos < src.count, src[pos] == "+" || src[pos] == "-" { pos += 1 }
            while pos < src.count, src[pos].isNumber || src[pos] == "." { pos += 1 }
            // No digits consumed (or a lone sign) → malformed token; the
            // Compose parser throws here, we surface nil.
            if pos == start || (pos == start + 1 && !src[start].isNumber) { return nil }
            guard let num = Double(String(src[start ..< pos])) else { return nil }
            // Unit: letters or the percent sign.
            let unitStart = pos
            while pos < src.count, src[pos].isLetter || src[pos] == "%" { pos += 1 }
            let unit = String(src[unitStart ..< pos]).lowercased()
            return convertToPx(num, unit)
        }

        // Unit string → px, using the surrounding SpacingContext. Mirrors
        // SpacingResolver.resolveRelative AND Compose convertToPx — keep the
        // three tables in lockstep whenever a unit lands.
        private func convertToPx(_ v: Double, _ unit: String) -> Double {
            switch unit {
            case "":    return v                       // bare number (multiplier)
            case "px":  return v                       // already canonical px
            case "pt":  return v * 4.0 / 3.0           // 1pt = 4/3 px (CSS 96dpi)
            case "em":  return v * ctx.fontSizePx      // element font size
            case "rem": return v * 16.0                // fixed 16px root (SpacingContext doc)
            // calc-% base — see the header note (skeptic B3): the iOS
            // APPLIER-lane basis is the legacy containingBlockWidth
            // (ancestor channel → root channel → canvas arithmetic), NOT
            // the WPT tri-state, like Compose's calc-% rides its legacy
            // parent-or-viewport base there. The INTRINSIC-sizing lane
            // (StyleBuilder P13 helpers) overrides via calcPercentBasisPx
            // so calc-% follows the same cyclic-percent-to-zero rule as a
            // bare % — mirroring Compose, whose evalCalc funnels % through
            // the percentBasePx tri-state in every context.
            case "%":   return v / 100.0
                             * (ctx.calcPercentBasisPx ?? Double(ctx.containingBlockWidth))
            // Pin P1 — ch: measured '0' advance (css-values-4 §6.1.3), or
            // the same section's 0.5em assumption when unmeasured.
            case "ch":  return v * (ctx.chAdvancePx ?? 0.5 * ctx.fontSizePx)
            // Pins P2–P6 — spec/UA fallback constants (see resolver docs).
            case "ex":  return v * 0.5 * ctx.fontSizePx        // x-height ≈ 0.5em
            case "ic", "cap": return v * ctx.fontSizePx        // 1em fallbacks
            case "lh":  return v * 1.2 * ctx.fontSizePx        // normal ≈ 1.2em
            case "rlh": return v * 1.2 * 16.0                  // 1.2 × root 16px
            // Viewport width family; vi/vb fold onto vw/vh in horizontal-tb
            // (pins P7/P8); cq* ride along per css-contain-3 §9's
            // no-container small-viewport fallback.
            case "vw", "svw", "lvw", "dvw", "vi", "svi", "lvi", "dvi", "cqw", "cqi":
                return v / 100.0 * ctx.viewportWidth
            // Viewport height family — same rules vertically.
            case "vh", "svh", "lvh", "dvh", "vb", "svb", "lvb", "dvb", "cqh", "cqb":
                return v / 100.0 * ctx.viewportHeight
            // vmin/vmax families, container-query variants included.
            case "vmin", "svmin", "lvmin", "dvmin", "cqmin":
                return v / 100.0 * min(ctx.viewportWidth, ctx.viewportHeight)
            case "vmax", "svmax", "lvmax", "dvmax", "cqmax":
                return v / 100.0 * max(ctx.viewportWidth, ctx.viewportHeight)
            // Unknown unit → 0 for THIS operand only (Compose parity: its
            // else-arm returns 0f and the rest of the expression stands).
            default:    return 0
            }
        }

        // Cursor helper — advance over whitespace.
        private mutating func skipWs() {
            while pos < src.count, src[pos].isWhitespace { pos += 1 }
        }

        // §10.1 whitespace rule: at least one space on either side of an
        // additive operator (Compose uses the same before-OR-after check).
        private func hasWsAround(_ at: Int) -> Bool {
            let before = at > 0 && src[at - 1].isWhitespace
            let after = at + 1 < src.count && src[at + 1].isWhitespace
            return before || after
        }
    }
}
