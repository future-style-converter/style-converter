package app.parsing.css.properties.primitiveParsers

// CalcSizeParser — css-values-5 §11 `calc-size(<calc-size-basis>, <calc-sum>)`.
//
// Wave 42 (lane W3). Before this parser existed every `calc-size()` sizing
// declaration fell through the sizing longhand parsers to LengthParser (which
// cannot read it) and reached the runtimes inside the Generic degradation
// envelope: web replayed the raw string into Chromium and PASSED, while
// Android DROPPED the declaration (calc-size-flex-001..006 painted no green
// at all — the flex item collapsed to nothing in a zero-width container) and
// iOS fell back to the intrinsic size (calc-size-min-max-sizes-001..006
// painted a ~20px sliver against a 100px ref). The typed value this parser
// produces is what lets the native runtimes resolve `size` against their own
// computed basis.
//
// ## What we type vs what stays Generic
// The css-values-5 grammar admits an arbitrary <calc-sum> over the `size`
// keyword. Because CSS calc() type-checking only allows ONE length power per
// product, every expression that type-checks as a <length> is AFFINE in
// `size`: `factor * size + offset`. We reduce exactly that affine family
// (sums of `size`-terms and absolute-length terms, each optionally scaled by
// `*`/`/` numeric factors) and refuse everything else — nested functions
// (`min(size, 100px)`), percentages, relative units — by returning null so
// the caller keeps today's Generic passthrough (web still replays the raw
// declaration verbatim; natives keep their documented drop). Refusing is the
// honest fallthrough: a wrong native guess would move pixels away from spec.
//
// ## Parse-time evaluation
// Per the lane brief the expression is evaluated HERE only when it is
// basis-independent: a pure-length basis (`calc-size(50px, size * 2)` →
// 100px) or a factor of zero (`calc-size(auto, 30px)` → 30px; also the only
// valid shape for the `any` basis, which forbids `size` outright). Everything
// else keeps the basis symbolic for the runtimes to resolve.
object CalcSizeParser {

    /** Parse outcome — see the branch notes on [parse]. */
    sealed interface Result {
        /** Basis-independent: the whole declaration collapsed to absolute px. */
        data class Resolved(val px: Double) : Result

        /**
         * Basis-dependent: `factor * <basis size> + offsetPx`, where [basis]
         * is one of the css-values-5 keyword bases (auto | min-content |
         * max-content | fit-content | stretch | content) the runtimes resolve
         * against their own layout, and [original] is the verbatim trimmed
         * declaration so the web runtime can replay it into the browser
         * byte-for-byte (the same fidelity the Generic envelope provided).
         */
        data class Dynamic(
            val basis: String,
            val factor: Double,
            val offsetPx: Double,
            val original: String,
        ) : Result
    }

    // css-values-5 <calc-size-basis> keyword bases. `any` is handled apart —
    // it forbids `size` in the expression, so it can never become Dynamic.
    private val KEYWORD_BASES = setOf(
        "auto", "min-content", "max-content", "fit-content", "stretch", "content",
    )

    /**
     * Parse a whole `calc-size(...)` declaration. Returns null when the value
     * is not a calc-size() call we can type — the caller must then fall
     * through to its existing behavior (Generic), never guess.
     */
    fun parse(value: String): Result? {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()
        // Function shell: `calc-size(` ... `)` with balanced parens.
        if (!lower.startsWith("calc-size(") || !lower.endsWith(")")) return null
        // Interior between the function's own parens, original case kept for
        // the verbatim `original` replay field (units/keywords are ASCII
        // case-insensitive, so lowercasing for MATCHING below is safe).
        val interior = trimmed.substring("calc-size(".length, trimmed.length - 1)
        // Split basis from expression at the single top-level comma
        // (css-values-5: exactly two arguments; commas inside nested
        // functions sit at depth > 0 and must not split).
        val parts = splitTopLevel(interior, ',') ?: return null
        if (parts.size != 2) return null
        val basisRaw = parts[0].trim()
        val exprRaw = parts[1].trim()
        // Reduce the expression to affine (sizeFactor, offsetPx) or refuse.
        val affine = reduceAffine(exprRaw) ?: return null
        val (factor, offsetPx) = affine
        val basisLower = basisRaw.lowercase()
        return when {
            // `any` basis: css-values-5 forbids `size` in the expression —
            // a size-free expression is just its offset; anything else is
            // invalid CSS and stays Generic (the browser will drop it too).
            basisLower == "any" ->
                if (factor == 0.0) Result.Resolved(offsetPx) else null
            // Keyword basis: evaluate now when the expr never reads `size`
            // (basis-independent per the lane brief), else keep it symbolic
            // for the runtimes to resolve against their computed basis.
            basisLower in KEYWORD_BASES ->
                if (factor == 0.0) Result.Resolved(offsetPx)
                else Result.Dynamic(basisLower, factor, offsetPx, trimmed)
            else -> {
                // <calc-sum> basis: only an ABSOLUTE length is pre-computable
                // (LengthParser normalises pt/cm/… to px; a null `pixels`
                // means percent/relative — runtime-dependent, stays Generic).
                val basisLen = LengthParser.parse(basisLower) ?: return null
                val basisPx = basisLen.pixels ?: return null
                Result.Resolved(factor * basisPx + offsetPx)
            }
        }
    }

    /**
     * Split [s] on [sep] at paren depth 0. Returns null on unbalanced parens
     * (a malformed declaration must refuse, not mis-split).
     */
    private fun splitTopLevel(s: String, sep: Char): List<String>? {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var depth = 0
        for (ch in s) {
            when {
                ch == '(' -> { depth++; cur.append(ch) }
                ch == ')' -> { depth--; if (depth < 0) return null; cur.append(ch) }
                ch == sep && depth == 0 -> { out.add(cur.toString()); cur.clear() }
                else -> cur.append(ch)
            }
        }
        if (depth != 0) return null
        out.add(cur.toString())
        return out
    }

    /**
     * Reduce a <calc-sum> over `size` to `(sizeFactor, offsetPx)` or refuse.
     *
     * Additive terms are split on `+` / `-` operators that CSS requires to be
     * WHITESPACE-SEPARATED (css-values-4 §10.9: "the + and - operators must
     * be surrounded by whitespace"), which is also what keeps a signed unit
     * value like the `-50px` in `size - 50px` unambiguous: the operator eats
     * the sign, the term parser sees a plain length.
     */
    private fun reduceAffine(expr: String): Pair<Double, Double>? {
        // Nested parens anywhere in the sum ⇒ a function term (min(), calc())
        // we do not reduce — refuse the whole expression (Generic keeps it).
        if (expr.contains('(') || expr.contains(')')) return null
        // Tokenise on whitespace: `size + 20px` → [size, +, 20px]. Terms are
        // the maximal runs between top-level additive operators.
        val tokens = expr.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        var factor = 0.0   // accumulated multiplier on `size`
        var offset = 0.0   // accumulated absolute px addend
        var sign = 1.0     // sign carried by the pending additive operator
        var term = mutableListOf<String>() // factors of the term being built
        // Flush one multiplicative term into the affine accumulators.
        fun flush(): Boolean {
            val t = reduceTerm(term) ?: return false
            factor += sign * t.first
            offset += sign * t.second
            term = mutableListOf()
            return true
        }
        for (tok in tokens) {
            when (tok) {
                // Additive operator: close the current term, set the next sign.
                "+", "-" -> {
                    if (term.isEmpty()) return null      // `+ +` / leading op
                    if (!flush()) return null            // unreducible term
                    sign = if (tok == "+") 1.0 else -1.0
                }
                // Anything else is a factor of the current term. `*` and `/`
                // may arrive glued (`size*2`) or spaced (`size * 2`) — the
                // term reducer re-splits, so we just collect.
                else -> term.add(tok)
            }
        }
        if (term.isEmpty()) return null                  // trailing operator
        if (!flush()) return null
        return factor to offset
    }

    /**
     * Reduce one multiplicative term (factors possibly glued to `*`/`/`) to
     * `(sizeCoefficient, px)`. Exactly one symbolic factor (`size` or an
     * absolute length) is allowed per term — the CSS calc() type system's
     * "one length power per product" rule; a bare-number term is not a
     * length and refuses.
     */
    private fun reduceTerm(factors: List<String>): Pair<Double, Double>? {
        // Re-split each collected token on `*` / `/`, KEEPING the operators
        // ("size*2" → [size, *, 2]) so spacing variants normalise to one list.
        val flat = mutableListOf<String>()
        for (f in factors) {
            var cur = StringBuilder()
            for (ch in f) {
                if (ch == '*' || ch == '/') {
                    if (cur.isNotEmpty()) { flat.add(cur.toString()); cur = StringBuilder() }
                    flat.add(ch.toString())
                } else cur.append(ch)
            }
            if (cur.isNotEmpty()) flat.add(cur.toString())
        }
        var coeff = 1.0            // numeric product applied to the symbol
        var isSize = false         // saw the `size` keyword
        var lengthPx: Double? = null // saw an absolute length (px-normalised)
        var op = '*'               // pending multiplicative operator
        var expectOperand = true   // grammar position: operand vs operator
        for (tok in flat) {
            if (!expectOperand) {
                // Operator slot: only `*` or `/` may appear here.
                if (tok != "*" && tok != "/") return null
                op = tok[0]
                expectOperand = true
                continue
            }
            // Operand slot: number, `size`, or an absolute length.
            val num = tok.toDoubleOrNull()
            when {
                num != null -> {
                    // css-values-4 §10.9: division requires a NUMBER divisor;
                    // multiplication by a number is always fine.
                    if (op == '/') {
                        if (num == 0.0) return null      // division by zero
                        coeff /= num
                    } else coeff *= num
                }
                tok.equals("size", ignoreCase = true) -> {
                    // `size` may appear once, and never as a divisor
                    // (1/size is not a <length>).
                    if (isSize || lengthPx != null || op == '/') return null
                    isSize = true
                }
                else -> {
                    // Absolute length factor (20px, 2pt, …). Percent and
                    // font/viewport-relative units have no px and refuse —
                    // they are runtime-dependent and stay Generic.
                    if (isSize || lengthPx != null || op == '/') return null
                    val len = LengthParser.parse(tok.lowercase()) ?: return null
                    lengthPx = len.pixels ?: return null
                }
            }
            expectOperand = false
        }
        if (expectOperand) return null                    // dangling operator
        return when {
            isSize -> coeff to 0.0                        // k · size
            lengthPx != null -> 0.0 to coeff * lengthPx   // k · <length>
            else -> null                                  // bare number ≠ length
        }
    }
}
