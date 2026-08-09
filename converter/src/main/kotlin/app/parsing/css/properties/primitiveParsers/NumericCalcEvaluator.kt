package app.parsing.css.properties.primitiveParsers

/**
 * Constant-folds a `calc()` whose operands are ALL plain, UNITLESS numbers.
 *
 * WHY THIS EXISTS. css-values-4 §10 makes `calc()` legal wherever a
 * `<number>` or `<integer>` is expected, and §10.11 ("Type Checking") says a
 * calculation of consistent type resolves at COMPUTED-VALUE time. A unitless
 * arithmetic expression carries no context — no font size, no percentage
 * basis, no viewport — so it is fully resolvable in the reader, which is
 * exactly the class of value the IR contract says must NOT be left null
 * (`null` is reserved for genuinely runtime-dependent values; see
 * schema/spec/02-values.md). Before this evaluator existed the reader
 * forwarded every such value as an unresolved expression and all three
 * runtimes dropped it.
 *
 * MEASURED CASE (the reason for the file): WPT
 * css/css-values/calc-positive-fraction-001.html declares
 * `z-index: calc(3 / 2)` on the green square and `z-index: 2` on the red one
 * it must overlap. With the calc unresolved the green box got `z-index: auto`
 * and the red painted on top — a fully red capture against an all-green
 * reference. The sibling case is
 * css/css-values/tree-counting/calc-sibling-function.html, whose
 * `z-index: calc(sibling-index())` the extractor substitutes to `calc(2)`
 * (its product folder only folds `A * B`).
 *
 * DELIBERATE SCOPE — this refuses far more than it accepts, because a
 * half-right fold is worse than none:
 *   * only `+ - * /`, parentheses, and decimal/exponent numbers;
 *   * ANY letter or `%` anywhere in the body is a refusal, which rules out
 *     every unit, every nested function (`var()`, `min()`, `sign()`,
 *     `sibling-index()`), and every keyword operand in one test;
 *   * division by zero, and any non-finite result, refuses;
 *   * an unbalanced or malformed expression refuses.
 * Every refusal returns null, which keeps the caller on its pre-existing
 * expression path byte-for-byte.
 */
object NumericCalcEvaluator {

    /**
     * Evaluate a whole-value `calc(...)` to a Double, or null when the value
     * is not a unitless arithmetic calc (see the refusal list above).
     *
     * @param value the raw declaration value (leading/trailing space is fine)
     */
    fun evaluate(value: String): Double? {
        val trimmed = value.trim()
        // Only a WHOLE-value calc() is folded. `calc(1) calc(2)` (two values)
        // and `translate(calc(1px))` (calc as an argument) are somebody else's
        // grammar, so they never reach here.
        if (!trimmed.regionMatches(0, "calc(", 0, 5, ignoreCase = true)) return null
        if (!trimmed.endsWith(")")) return null
        val body = trimmed.substring(5, trimmed.length - 1)
        // The letter/percent veto. Cheap, total, and the single guard that
        // makes "unitless" true rather than hoped-for: `2px`, `50%`, `var(--x)`
        // and `sign(-1)` all die here before any arithmetic runs.
        if (body.any { it.isLetter() || it == '%' }) return null
        // Guard the empty body (`calc()`), which the parser below would
        // otherwise report as a malformed-token refusal anyway — this is just
        // the cheaper path to the same null.
        if (body.isBlank()) return null
        val parser = Parser(body)
        val result = parser.parseSum() ?: return null
        // Trailing garbage (`calc(1 2)`) means the grammar did not consume the
        // whole body — refuse rather than silently use the prefix.
        if (!parser.atEnd()) return null
        // css-values-4 §10.11: a calculation that overflows or divides by zero
        // is invalid at computed-value time, not "some large number".
        return if (result.isFinite()) result else null
    }

    /**
     * Round a folded calculation to an `<integer>` per css-values-4 §10.11:
     * "the value is rounded to the nearest integer, with values halfway
     * between two integers rounded towards +∞". That is Kotlin's
     * `Math.floor(x + 0.5)`, NOT `Math.round` on a negative half (which also
     * rounds toward +∞ in the JVM, but says so by accident) and definitely
     * not `toInt()` (truncation toward zero — it would turn `calc(3 / 2)`
     * into 1 and fail the very test this exists for).
     */
    fun toIntegerValue(x: Double): Int? {
        val rounded = Math.floor(x + 0.5)
        // Outside the Int range there is no faithful IR integer to emit; the
        // caller keeps its expression path rather than wrapping around.
        if (rounded < Int.MIN_VALUE.toDouble() || rounded > Int.MAX_VALUE.toDouble()) return null
        return rounded.toInt()
    }

    /**
     * Minimal recursive-descent parser over the unitless grammar
     * `sum := product (('+'|'-') product)*` /
     * `product := unary (('*'|'/') unary)*` /
     * `unary := ('+'|'-')? primary` / `primary := number | '(' sum ')'`.
     * css-values-4 §10.1 requires whitespace around `+`/`-` (so `1-2` is one
     * malformed token, not a subtraction); we do NOT enforce that, because
     * accepting a stricter-than-us author's expression can only ever fold a
     * value Chromium also folds — the whitespace rule exists to disambiguate
     * signs, and our number scanner already binds a sign to the number.
     */
    private class Parser(private val src: String) {
        private var i = 0

        /** True once only whitespace remains — the "fully consumed" check. */
        fun atEnd(): Boolean { skipWs(); return i >= src.length }

        /** Additive level (lowest precedence). Null propagates a refusal. */
        fun parseSum(): Double? {
            var acc = parseProduct() ?: return null
            while (true) {
                skipWs()
                val op = peek() ?: return acc
                if (op != '+' && op != '-') return acc
                i++
                val rhs = parseProduct() ?: return null
                acc = if (op == '+') acc + rhs else acc - rhs
            }
        }

        /** Multiplicative level. */
        private fun parseProduct(): Double? {
            var acc = parseUnary() ?: return null
            while (true) {
                skipWs()
                val op = peek() ?: return acc
                if (op != '*' && op != '/') return acc
                i++
                val rhs = parseUnary() ?: return null
                if (op == '*') {
                    acc *= rhs
                } else {
                    // Division by zero is invalid at computed-value time
                    // (css-values-4 §10.11) — refuse, do not emit Infinity.
                    if (rhs == 0.0) return null
                    acc /= rhs
                }
            }
        }

        /** Optional leading sign, then a primary. */
        private fun parseUnary(): Double? {
            skipWs()
            val c = peek() ?: return null
            if (c == '+' || c == '-') {
                i++
                val v = parseUnary() ?: return null
                return if (c == '-') -v else v
            }
            return parsePrimary()
        }

        /** A parenthesised sub-expression or a bare number. */
        private fun parsePrimary(): Double? {
            skipWs()
            val c = peek() ?: return null
            if (c == '(') {
                i++
                val v = parseSum() ?: return null
                skipWs()
                if (peek() != ')') return null                  // unbalanced
                i++
                return v
            }
            return parseNumber()
        }

        /** `<number>` per CSS Syntax 3 §4.3.3 minus the sign (parseUnary owns
         *  that): digits with an optional fraction and an optional exponent. */
        private fun parseNumber(): Double? {
            val start = i
            while (i < src.length && src[i].isDigit()) i++
            if (i < src.length && src[i] == '.') {
                i++
                while (i < src.length && src[i].isDigit()) i++
            }
            // Scientific notation is part of the <number> production, and the
            // letter veto above already ran on the WHOLE body — so an `e`
            // here can only be an exponent if the body had no letters, which
            // it cannot. Left unhandled deliberately: `calc(1e3)` refuses at
            // the veto, consistently with every other letter-bearing value.
            if (i == start) return null                         // no digits at all
            return src.substring(start, i).toDoubleOrNull()
        }

        private fun peek(): Char? = if (i < src.length) src[i] else null

        private fun skipWs() { while (i < src.length && src[i].isWhitespace()) i++ }
    }
}
