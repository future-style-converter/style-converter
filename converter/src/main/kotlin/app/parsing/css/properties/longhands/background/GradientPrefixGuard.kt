package app.parsing.css.properties.longhands.background

import app.parsing.css.properties.primitiveParsers.AngleParser
import app.parsing.css.properties.primitiveParsers.TokenizationUtils

// The gradient SYNTAX-PREFIX validity gate — wave-40 lane T6, split out of
// BackgroundImagePropertyParser per the ≤200-line / split-past-300 file rule
// when the strictness landed. One owner for the question "was that first
// comma-segment a legal prefix, a legal <color-stop>, or neither?".
//
// WHY THIS EXISTS (the wave-39 A5 deferral, measured)
// ---------------------------------------------------
// css-images-4 §3.1 ("Linear Gradients: the linear-gradient() notation")
// types a linear gradient's optional first segment as
//     <linear-gradient-syntax> =
//       [ [ <angle> | <zero> | to <side-or-corner> ] || <color-interpolation-method> ]? ,
//       <color-stop-list>
//     <side-or-corner> = [left | right] || [top | bottom]
// and everything after that segment as a <color-stop-list>. A segment that is
// NEITHER a prefix nor a stop makes the whole function invalid, and CSS 2.2
// §4.2 ("Rules for handling parsing errors") then drops the ENTIRE
// declaration.
//
// The parser used to do neither: an unrecognised first segment simply
// contributed ZERO color stops, and the remaining segments still produced a
// gradient. So WPT css-values/angle-units-001 —
//     background-image: linear-gradient(green, green);
//     background-image: linear-gradient(90degree, red, red);   /* invalid */
//     background-image: linear-gradient(100gradian, red, red); /* invalid */
//     background-image: linear-gradient(1.57radian, red, red); /* invalid */
//     background-image: linear-gradient(0.25turns, red, red);  /* invalid */
// — whose whole point is that `degree`/`gradian`/`radian`/`turns` are NOT in
// the css-values-4 §7.1 angle-unit table, converted to a red-red ramp on all
// three platforms. The capture browser (Chrome 151) agrees with the spec, and
// this was measured rather than assumed:
//     CSS.supports('background-image', 'linear-gradient(0.25turns, red, red)')
//       === false
//     CSS.supports('background-image', 'linear-gradient(0, red, red)')
//       === true   ← the legacy unitless zero, deliberately still accepted
//
// WAVE-49 SOUNDNESS REPAIR (skeptic S4's executed probe)
// ------------------------------------------------------
// The first cut of [isInvalidGradientLayer] reached its verdict RESIDUALLY —
// "AngleParser could not read the head token, and the head token looks like a
// dimension, therefore the layer is invalid". A residual verdict inherits
// every gap in the thing it residualises, and AngleParser has one that
// matters: its `^([+-]?\d*\.?\d+)(deg|rad|grad|turn)$` admits no
// scientific-notation exponent, even though css-values-4 §5.3 ("Real Numbers:
// the <number> type") says a number "can be concluded by the letter 'e' or
// 'E' followed by an integer indicating the base-ten exponent". Nine shapes
// the capture browser reports as CSS.supports === true were therefore
// declared invalid and DELETED, taking a rendering declaration with them:
// `4.5e1deg`, `1e2deg`, `4.5e+1deg`, `4.5e-1turn`, `45e0deg`, their
// `in oklch …` and `conic-gradient(from …)` variants, and — via a
// string-matched direction table — `to  right` / `to  right top` written
// with two spaces, which Chrome computes to `to right` / `to right top`.
//
// Every verdict below is therefore POSITIVE: it names the production the
// layer violates and proves the violation from the tokens themselves, so a
// gap in some other parser can no longer be mistaken for "not CSS". This
// file says "invalid" to exactly two shapes:
//   1. the prefix slot holds a <dimension-token> whose unit is absent from
//      the CLOSED four-entry angle-unit table of css-values-4 §7.1, or a
//      <percentage-token> (a percentage is neither an <angle> nor, on its
//      own, a <color-stop>);
//   2. the prefix slot opens with the `to` keyword and the tokens after it
//      are not a <side-or-corner> (css-images-4 §3.1).
// Everything else — a calc(), a var(), an unknown function head, a colour
// notation ColorParser declines to model — is "unknown to US", which is not
// the same as "not CSS", and keeps its historic Raw passthrough.
internal object GradientPrefixGuard {

    // css-values-4 §7.1 ("Angle Units: the <angle> type and deg, grad, rad,
    // turn units") is a CLOSED table: these four idents are the only units an
    // <angle> may carry. A dimension whose unit is NOT one of them can never
    // be an <angle>, which is what makes the verdict positive rather than a
    // restatement of "AngleParser said no".
    private val ANGLE_UNITS = setOf("deg", "grad", "rad", "turn")

    // css-images-4 §3.1: <side-or-corner> = [left | right] || [top | bottom].
    // Split into the two `||` operand sets so the "at most one of each, at
    // least one overall" shape can be checked directly against the grammar.
    private val HORIZONTAL_SIDES = setOf("left", "right")
    private val VERTICAL_SIDES = setOf("top", "bottom")

    // A <dimension-token> or <percentage-token>, per css-syntax-3 §4.3.3
    // ("Consume a numeric token"): consume a number, then EITHER an ident
    // sequence (→ <dimension-token>, group 1 = its unit) OR a `%` (→
    // <percentage-token>). Deliberately does NOT match a bare number —
    // `linear-gradient(0, …)` is the legacy <zero> form the capture browser
    // still accepts, and AngleParser resolves it long before anything here.
    //
    // The `(?:[eE][+-]?\d+)?` group is load-bearing, not decoration: it is
    // the exponent css-syntax-3 §4.3.13 ("Consume a number") folds into the
    // number, so `4.5e1deg` tokenizes as ONE dimension with unit `deg`. The
    // ident-sequence character class follows css-syntax-3 §4.3.12 ("Consume
    // an ident sequence") — letters, digits, `_`, `-`, non-ASCII — because a
    // unit spelled `turns` or `my-unit` is equally provably not an angle.
    private val DIMENSION_TOKEN =
        """^[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?([A-Za-z_\u0080-\uFFFF][A-Za-z0-9_\-\u0080-\uFFFF]*|%)$"""
            .toRegex()

    /**
     * The unit of `token` when it is a single `<dimension-token>` (or `"%"`
     * when it is a `<percentage-token>`), else null.
     *
     * Null is the "I decline to say" answer and covers every function head
     * (`calc(…)`, `var(…)`, `color-mix(…)`), every bare ident, every bare
     * number and every multi-token string — none of which this file
     * adjudicates.
     */
    private fun dimensionUnit(token: String): String? =
        DIMENSION_TOKEN.find(token)?.groupValues?.get(1)?.lowercase()

    /**
     * Is `token` a dimension/percentage that provably CANNOT be an `<angle>`?
     *
     * This is the positive test the wave-49 repair is built on: `0.25turns`
     * answers yes because `turns` is absent from the css-values-4 §7.1 table,
     * while `4.5e1deg` answers NO because its unit really is `deg` — the
     * converter merely cannot read the exponent yet (AngleParser's regex),
     * which is a modelling gap, not an author error.
     */
    private fun isNonAngleDimension(token: String?): Boolean {
        val unit = token?.let { dimensionUnit(it) } ?: return false
        return unit !in ANGLE_UNITS
    }

    /**
     * Do these tokens form a `<side-or-corner>` — `[left | right] || [top |
     * bottom]` (css-images-4 §3.1)?
     *
     * The `||` combinator means: one or both operands, in any order, each at
     * most once. So one-or-two tokens, drawn from the two side sets, with no
     * set used twice (`to left right` and `to top bottom` are both out).
     */
    private fun isSideOrCorner(tokens: List<String>): Boolean {
        if (tokens.isEmpty() || tokens.size > 2) return false
        val horizontal = tokens.count { it in HORIZONTAL_SIDES }
        val vertical = tokens.count { it in VERTICAL_SIDES }
        // Every token must have been claimed by exactly one of the two sets,
        // and neither set may be claimed twice.
        return horizontal <= 1 && vertical <= 1 && horizontal + vertical == tokens.size
    }

    /**
     * Can this UNCLAIMED first comma-segment be a `<color-stop>`?
     *
     * False for exactly two shapes:
     *  - its head token is the `to` keyword — a `<side-or-corner>` that the
     *    caller already failed to resolve (`to bottom left top`, or
     *    `to right in <unrecognised-colorspace>`). The head is taken as a
     *    TOKEN, not a `"to "` string prefix, so a tab- or double-space-
     *    separated spelling is recognised too (css-syntax-3 §4.3.1 "Consume
     *    a token" consumes a whole run of whitespace as ONE
     *    `<whitespace-token>`, so `to  right` and `to right` are the same
     *    two tokens);
     *  - its first token is a `<dimension>` or `<percentage>`, which inside a
     *    linear gradient could only ever have been an `<angle>` — and a valid
     *    one would have been consumed by the caller's AngleParser attempt.
     *
     * The head token is taken with the PAREN-AWARE tokenizer, so
     * `color-mix(in srgb, red, blue) 10%` is inspected as ONE token and can
     * never be mistaken for a dimension.
     */
    fun isColorStopSegment(segment: String): Boolean {
        val head = TokenizationUtils.tokenizeByWhitespace(segment).firstOrNull() ?: return false
        if (head.lowercase() == "to") return false
        return dimensionUnit(head) == null
    }

    /**
     * Is this whole background-image LAYER a gradient that provably violates
     * its own grammar — i.e. an INVALID declaration rather than a shape this
     * converter has not modelled yet?
     *
     * WHY A SECOND ENTRY POINT (wave-49 lane A1)
     * ------------------------------------------
     * [isColorStopSegment] answers the question for ONE comma-segment, and
     * BackgroundImagePropertyParser's gradient parsers consume that answer by
     * returning `null` — a channel they share with a dozen other refusals
     * (unbalanced parens, an empty stop list, a colour syntax ColorParser
     * declines to model). The caller therefore cannot tell "this is not CSS"
     * from "I cannot read this", and had to assume the safe latter: every
     * refusal became a `Raw` passthrough. This function re-asks the guard's
     * OWN two questions at layer granularity so the caller can drop the
     * declaration outright, per the file banner's own conclusion — CSS 2.2
     * §4.2 drops the ENTIRE declaration.
     *
     * SOUNDNESS IS THE ONLY PROPERTY THAT MATTERS: a `true` here must imply
     * the layer really is invalid, while a `false` costs nothing but the
     * historic Raw passthrough. That is why every branch is a POSITIVE
     * grammar test and why "some parser in this repo returned null" is
     * nowhere among them — see the file banner for the nine valid-CSS shapes
     * the residual formulation deleted. The two adjudicated grammars are
     * css-images-4 §3.1 (linear: the first segment is
     * `[ <angle> | <zero> | to <side-or-corner> ] || <color-interpolation-method>`)
     * and §3.3.1 (conic: `[ [ [ from [ <angle> | <zero> ] ]? [ at <position> ]? ]
     * || <color-interpolation-method> ]? , <angular-color-stop-list>`, whose
     * `from` slot admits nothing but an angle).
     *
     * radial-gradient is NOT covered: its prefix admits `<length-percentage>{1,2}`
     * radii the parser deliberately does not model (see `looksLikeRadialPrefix`),
     * so a refusal there can mean "unmodelled" and must stay a Raw passthrough.
     * url() / image() / cross-fade() / unknown function heads are not covered
     * either — an unknown head is unknown to US, which is not the same as
     * invalid. TODO: extend once those grammars are complete.
     */
    fun isInvalidGradientLayer(layer: String): Boolean {
        val t = layer.trim()
        // Function names are ASCII case-insensitive (CSS 2.2 §4.1.3
        // "Characters and case").
        val lower = t.lowercase()
        val linear = lower.startsWith("linear-gradient(") ||
                     lower.startsWith("repeating-linear-gradient(")
        val conic = lower.startsWith("conic-gradient(") ||
                    lower.startsWith("repeating-conic-gradient(")
        if (!linear && !conic) return false

        // Same extraction the parsers use — and on the SAME bytes: parseImage
        // hands `lower` to parseLinearGradient/parseConicGradient (every token
        // inside a gradient is case-insensitive per CSS Images L3/L4), so this
        // guard must inspect the lowered form too or it would judge a
        // differently-cased value by a different standard. A null here means
        // unbalanced parens — a tokenization failure we do not adjudicate.
        val funcName = lower.substringBefore('(')
        val content = TokenizationUtils.extractFunctionContent(lower, funcName) ?: return false
        val parts = TokenizationUtils.splitByComma(content)
        if (parts.isEmpty()) return false

        // Peel any <color-interpolation-method> exactly as the parsers do —
        // its presence alone marks the segment as the syntax prefix (the `||`
        // combinator of §3.1 / §3.3.1). stripInterpolationMethod rebuilds the
        // remainder from tokens, so what comes back is already
        // single-space-normalised.
        val rawFirst = parts[0].trim()
        val strippedFirst = GradientValueParsers.stripInterpolationMethod(rawFirst)
        val first = strippedFirst ?: rawFirst

        // TOKENIZE BEFORE DECIDING. Whitespace runs collapse to one
        // <whitespace-token> (css-syntax-3 §4.3.1), so `to  right` must be
        // read as the two tokens `to` `right`, exactly as Chrome does when it
        // computes that value to `to right`.
        val tokens = TokenizationUtils.tokenizeByWhitespace(first)
        val head = tokens.firstOrNull() ?: return false

        if (linear) {
            // A resolvable angle: in the grammar, nothing to say. (Also the
            // <zero> arm — AngleParser accepts the legacy unitless zero.)
            if (AngleParser.parse(first) != null) return false
            // `to <side-or-corner>`: judged against the production itself, so
            // the answer does not depend on directionToAngle's lookup table
            // being complete.
            if (head == "to") return !isSideOrCorner(tokens.drop(1))
            // Otherwise the only provable violation is a head that is a
            // dimension/percentage which cannot be an <angle>. This covers
            // both the UNCLAIMED first segment (`0.25turns, red, red`) and a
            // leftover sitting beside a RECOGNISED interpolation method
            // (`0.25turns in srgb`), which §3.1's `||` combinator forbids all
            // the same. Anything else — calc(), var(), an ident, a colour —
            // is not adjudicated here.
            return isNonAngleDimension(head)
        }

        // Conic (css-images-4 §3.3.1): only the `from <angle>` shape is
        // adjudicated. `at <position>` and a bare stop list can both fail for
        // reasons this guard does not own, so they stay Raw.
        if (head != "from") return false
        // The `from` argument runs to the `at <position>` clause if there is
        // one. Taken by TOKEN, so `from 45deg  at  50% 50%` (extra spaces)
        // splits where `indexOf(" at ")` would have missed.
        val atIndex = tokens.indexOf("at")
        val fromArg = if (atIndex >= 0) tokens.subList(1, atIndex) else tokens.subList(1, tokens.size)
        // A multi-token `from` argument is a shape we do not model, not a
        // proven violation; likewise a single token that is a function head
        // (`from calc(0.25turn)`, `from var(--start)`) — both valid CSS whose
        // value is only knowable at computed-value time. Only a lone
        // non-angle dimension is provably out of the grammar.
        if (fromArg.size != 1) return false
        return isNonAngleDimension(fromArg[0])
    }
}
