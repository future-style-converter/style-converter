package app.parsing.css.properties.longhands.animations

// Shared <easing-function> component parsers for the two timing-function
// longhands. Same package as both parsers, so no import is needed.
import app.irmodels.properties.animations.TimingFunction
import app.parsing.css.properties.primitiveParsers.NumberParser

/**
 * `cubic-bezier()` and `steps()` readers, shared by
 * [AnimationTimingFunctionPropertyParser] and
 * [TransitionTimingFunctionPropertyParser].
 *
 * A6#14: both parsers carried byte-identical private copies of these two
 * functions (`parseCubicBezier`, `parseSteps`). css-easing-1 defines ONE
 * `<easing-function>` grammar that `animation-timing-function` and
 * `transition-timing-function` both reference, so one body is the correct
 * shape — two copies is how a fix to one silently misses the other (the
 * wave-47 defect class). Bodies below are moved verbatim; the differential
 * over every fixture is byte-identical.
 */
internal object TimingFunctionParsing {

    /**
     * `cubic-bezier(x1, y1, x2, y2)` — css-easing-1 cubic Bézier easing.
     * Returns null unless exactly four <number>s parse.
     */
    fun parseCubicBezier(value: String): TimingFunction? {
        // Extract content between parentheses
        val content = value.substringAfter("cubic-bezier(").substringBefore(")")
        val parts = content.split(Regex("\\s*,\\s*"))

        if (parts.size != 4) return null

        val x1 = NumberParser.parse(parts[0])?.value ?: return null
        val y1 = NumberParser.parse(parts[1])?.value ?: return null
        val x2 = NumberParser.parse(parts[2])?.value ?: return null
        val y2 = NumberParser.parse(parts[3])?.value ?: return null

        return TimingFunction.fromCubicBezier(x1, y1, x2, y2)
    }

    /**
     * `steps(<integer>[, <step-position>])` — css-easing-1 step easing.
     * An unrecognised or absent step-position yields null, which the IR
     * model treats as the `end` default.
     */
    fun parseSteps(value: String): TimingFunction? {
        // Extract content between parentheses
        val content = value.substringAfter("steps(").substringBefore(")")
        val parts = content.split(Regex("\\s*,\\s*"))

        if (parts.isEmpty()) return null

        val count = NumberParser.parseInt(parts[0]) ?: return null

        // Parse optional position parameter
        val position = if (parts.size >= 2) {
            when (parts[1].trim().lowercase()) {
                "jump-start" -> TimingFunction.StepPosition.JUMP_START
                "jump-end" -> TimingFunction.StepPosition.JUMP_END
                "jump-none" -> TimingFunction.StepPosition.JUMP_NONE
                "jump-both" -> TimingFunction.StepPosition.JUMP_BOTH
                "start" -> TimingFunction.StepPosition.START
                "end" -> TimingFunction.StepPosition.END
                else -> null
            }
        } else {
            null
        }

        return TimingFunction.fromSteps(count, position)
    }
}
