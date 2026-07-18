package app.parsing.css.properties.longhands.typography

import app.irmodels.*
import app.irmodels.properties.typography.FontStyleProperty
import app.parsing.css.properties.longhands.PropertyParser

// Parses the `font-style` longhand per css-fonts-4 §2.4:
//   font-style = normal | italic | oblique <angle>?
// The <angle> is normalized to degrees on the wire (schema/spec/02-values.md:
// all CSS angles → degrees), so every {"oblique":{"deg":N}} payload the three
// runtimes decode carries a real numeric degree value regardless of the
// authored unit (deg / rad / grad / turn).
object FontStylePropertyParser : PropertyParser {
    // css-values-4 §6.1 <angle> grammar: a <number> immediately followed by a
    // unit ident. The unit alternation is matched as one token by the regex,
    // which fixes the historical suffix-check bug where "10grad".endsWith("rad")
    // was true and grad angles silently degraded to bare `oblique` (14deg
    // default → italic) instead of 9deg (upright below Chromium's 14deg
    // synthetic-oblique cutoff). Number part: optional sign, digits with an
    // optional fraction (or a bare ".5" fraction), optional exponent — the
    // css-values-4 §4.2 <number> shape.
    private val ANGLE = Regex("""^([+-]?(?:\d+(?:\.\d+)?|\.\d+)(?:[eE][+-]?\d+)?)(deg|grad|rad|turn)$""")

    override fun parse(value: String): IRProperty? {
        // CSS keywords are ASCII case-insensitive; normalize once up front.
        val trimmed = value.trim().lowercase()

        // Dispatch on the three css-fonts-4 §2.4 value forms.
        val style = when {
            // `normal` — the initial value, upright face.
            trimmed == "normal" -> FontStyleProperty.FontStyle.Normal()
            // `italic` — cursive face request.
            trimmed == "italic" -> FontStyleProperty.FontStyle.Italic()
            // Bare `oblique` — angle omitted; css-fonts-4 §2.4 gives it the
            // 14deg default, which the runtimes apply at decode time, so the
            // wire keeps the bare keyword (null angle) for fidelity.
            trimmed == "oblique" -> FontStyleProperty.FontStyle.Oblique()
            // `oblique <angle>` — the only form that carries a payload.
            trimmed.startsWith("oblique ") -> {
                // Everything after the "oblique " prefix (8 chars) is the
                // single <angle> token; extra inner whitespace is trimmed.
                val anglePart = trimmed.substring(8).trim()
                // css-fonts-4 §2.4 grammar: an unparseable <angle> makes the
                // WHOLE declaration invalid (CSS2 §4.2 error handling — the
                // declaration is ignored). We must NOT degrade to bare
                // `oblique`: that would flip 9deg-class requests (upright in
                // the Chromium reference) to the italic appearance. Pinned by
                // FontStylePropertyParserTest.
                val angle = parseAngle(anglePart) ?: return null
                // Valid angle → oblique with the normalized-degrees payload.
                FontStyleProperty.FontStyle.Oblique(angle)
            }
            // Anything else is not part of the §2.4 grammar → declaration
            // rejected (parser returns null, property is simply not emitted).
            else -> return null
        }

        // Wrap the parsed variant in the typed IR property.
        return FontStyleProperty(style)
    }

    // Parse one <angle> token into a normalized IRAngle, or null when the
    // token is not a valid css-values-4 §6.1 angle (caller rejects the
    // declaration — no silent fallthrough to a default slant).
    private fun parseAngle(value: String): IRAngle? {
        // Whole-token regex match: number capture + unit capture. matchEntire
        // (not find) so trailing garbage like "14degx" is rejected too.
        val match = ANGLE.matchEntire(value) ?: return null
        // The number group is regex-validated, so toDoubleOrNull only guards
        // pathological overflow-ish inputs; null still means "reject".
        val num = match.groupValues[1].toDoubleOrNull() ?: return null
        // Map the unit to the IRAngle factory that normalizes to degrees
        // (ValueTypes.kt: rad ×180/π, grad ×0.9, turn ×360 — css-values-4 §6.1).
        val angle = when (match.groupValues[2]) {
            // Degrees pass through unchanged.
            "deg" -> IRAngle.fromDegrees(num)
            // Gradians: 400grad = full circle, so 1grad = 0.9deg.
            "grad" -> IRAngle.fromGradians(num)
            // Radians: 2π rad = full circle.
            "rad" -> IRAngle.fromRadians(num)
            // Turns: 1turn = 360deg.
            "turn" -> IRAngle.fromTurns(num)
            // Unreachable — the regex alternation only admits the four units —
            // but kept explicit so a future unit addition cannot fall through.
            else -> return null
        }
        // css-fonts-4 §2.4: the oblique angle's useful range is [-90deg,
        // 90deg]; out-of-range degrees are clamped to the range boundary
        // (campaign pin: 100grad → exactly 90deg; anything beyond folds to
        // ±90). The original value/unit are preserved for CSS regeneration.
        return if (angle.degrees > 90.0 || angle.degrees < -90.0)
            // copy() keeps originalValue/originalUnit intact while pinning the
            // normalized wire "deg" inside the legal oblique band.
            angle.copy(degrees = angle.degrees.coerceIn(-90.0, 90.0))
        else
            // In-range angles ship as-is.
            angle
    }
}
