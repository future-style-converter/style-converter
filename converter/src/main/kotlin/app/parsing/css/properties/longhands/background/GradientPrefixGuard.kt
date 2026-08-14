package app.parsing.css.properties.longhands.background

import app.parsing.css.properties.primitiveParsers.TokenizationUtils

// The gradient SYNTAX-PREFIX validity gate — wave-40 lane T6, split out of
// BackgroundImagePropertyParser per the ≤200-line / split-past-300 file rule
// when the strictness landed. One owner for the question "was that first
// comma-segment a legal prefix, a legal <color-stop>, or neither?".
//
// WHY THIS EXISTS (the wave-39 A5 deferral, measured)
// ---------------------------------------------------
// css-images-3 §3.1 types a linear gradient's optional first segment as
//     [ [ <angle> | to <side-or-corner> ] || <color-interpolation-method> ]
// and everything after it as a <color-stop-list>. A segment that is NEITHER
// a prefix nor a stop makes the whole function invalid, and CSS 2.2 §4.2 then
// drops the ENTIRE declaration.
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
// The gate is deliberately NARROW. It rejects only the two shapes that can
// provably never be a <color-stop>; anything else — idents, hex, colour
// functions, `color-mix(in srgb, …)` — is still handed to parseColorStops
// exactly as before, so no gradient that renders today is lost to a colour
// syntax ColorParser merely declines to model.
internal object GradientPrefixGuard {

    // A <dimension> or <percentage> token: a number immediately followed by a
    // unit ident or `%` (CSS Syntax L3 §4.3.3 / §4.3.4). Deliberately does NOT
    // match a bare number — `linear-gradient(0, …)` is the legacy form the
    // capture browser still accepts, and AngleParser resolves it as 0deg long
    // before anything reaches this test.
    private val DIMENSION_TOKEN =
        """^[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?(?:[a-z]+|%)$""".toRegex()

    /**
     * Can this UNCLAIMED first comma-segment be a `<color-stop>`?
     *
     * False for exactly two shapes:
     *  - it opens with the `to` keyword — a `<side-or-corner>` that the
     *    caller already failed to resolve (`to bottom left top`, or
     *    `to right in <unrecognised-colorspace>`);
     *  - its first token is a `<dimension>`, which inside a linear gradient
     *    could only ever have been an `<angle>` — and a valid one would have
     *    been consumed by the caller's AngleParser attempt.
     *
     * The head token is taken with the PAREN-AWARE tokenizer, so
     * `color-mix(in srgb, red, blue) 10%` is inspected as ONE token and can
     * never be mistaken for a dimension.
     */
    fun isColorStopSegment(segment: String): Boolean {
        val t = segment.trim()
        if (t.isEmpty()) return false
        if (t == "to" || t.startsWith("to ")) return false
        val head = TokenizationUtils.tokenizeByWhitespace(t).firstOrNull() ?: return false
        return !DIMENSION_TOKEN.matches(head)
    }
}
