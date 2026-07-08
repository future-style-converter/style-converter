package app.parsing.css.properties.longhands.transforms

import app.irmodels.*
import app.parsing.css.properties.longhands.PropertyParser
import app.irmodels.IRProperty
import app.irmodels.properties.transforms.TransformFunction
import app.irmodels.properties.transforms.TransformProperty
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.NumberParser
import app.parsing.css.properties.primitiveParsers.AngleParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

/**
 * Parser for the CSS `transform` property.
 *
 * Parses transform function chains into a list of TransformFunction objects.
 * All angles are normalized to degrees; lengths are normalized to pixels when absolute.
 *
 * ## Supported Transform Functions
 * - **Translate**: translate(), translateX(), translateY(), translateZ(), translate3d()
 * - **Scale**: scale(), scaleX(), scaleY(), scaleZ(), scale3d()
 * - **Rotate**: rotate(), rotateX(), rotateY(), rotateZ(), rotate3d()
 * - **Skew**: skew(), skewX(), skewY()
 * - **Other**: perspective(), matrix(), matrix3d()
 *
 * ## Examples
 * ```
 * "none" → TransformProperty([])
 * "translateX(100px)" → TransformProperty([TranslateX(IRLength(100px))])
 * "rotate(45deg) scale(1.5)" → TransformProperty([Rotate(45°), Scale(1.5, 1.5)])
 * "var(--transform)" → TransformProperty.Expression("var(--transform)")
 * ```
 *
 * @see TransformFunction for supported transform types
 * @see TransformProperty for the IR property
 */
object TransformPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()
        if (lower == "none") {
            return TransformProperty(emptyList())
        }
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return TransformProperty(TransformProperty.TransformValue.Keyword(lower))
        }
        // Check for expressions (calc, var, clamp, min, max)
        if (containsExpression(trimmed)) {
            return TransformProperty(TransformProperty.TransformValue.Expression(trimmed))
        }
        val functions = parseTransformFunctions(value)
        if (functions.isEmpty()) return null
        return TransformProperty(functions)
    }

    private fun containsExpression(value: String): Boolean {
        val lower = value.lowercase()
        return lower.contains("calc(") || lower.contains("var(") ||
               lower.contains("clamp(") || lower.contains("min(") ||
               lower.contains("max(") || lower.contains("env(") ||
               // CSS math functions (Level 4)
               lower.contains("sin(") || lower.contains("cos(") ||
               lower.contains("tan(") || lower.contains("asin(") ||
               lower.contains("acos(") || lower.contains("atan(") ||
               lower.contains("atan2(") || lower.contains("pow(") ||
               lower.contains("sqrt(") || lower.contains("hypot(") ||
               lower.contains("log(") || lower.contains("exp(") ||
               // Infinity is a valid CSS value
               lower.contains("infinity")
    }

    private fun parseTransformFunctions(value: String): List<TransformFunction> {
        val functions = mutableListOf<TransformFunction>()
        val functionStrings = extractFunctions(value)
        for (funcStr in functionStrings) {
            val func = parseFunction(funcStr)
            if (func != null) {
                functions.add(func)
            }
        }
        return functions
    }

    private fun extractFunctions(value: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var parenDepth = 0
        for (char in value) {
            when {
                char == '(' -> {
                    parenDepth++
                    current.append(char)
                }
                char == ')' -> {
                    parenDepth--
                    current.append(char)
                    if (parenDepth == 0) {
                        result.add(current.toString().trim())
                        current = StringBuilder()
                    }
                }
                parenDepth > 0 -> current.append(char)
                !char.isWhitespace() -> current.append(char)
            }
        }
        return result
    }

    private fun parseFunction(funcStr: String): TransformFunction? {
        val openParen = funcStr.indexOf('(')
        if (openParen == -1) return null
        val funcName = funcStr.substring(0, openParen).trim().lowercase()
        val closeParen = funcStr.lastIndexOf(')')
        if (closeParen == -1) return null
        val argsStr = funcStr.substring(openParen + 1, closeParen)
        val args = argsStr.split(',').map { it.trim() }
        return when (funcName) {
            "translate" -> parseTranslate(args)
            "translatex" -> parseTranslateX(args)
            "translatey" -> parseTranslateY(args)
            "translatez" -> parseTranslateZ(args)
            "translate3d" -> parseTranslate3d(args)
            "scale" -> parseScale(args)
            "scalex" -> parseScaleX(args)
            "scaley" -> parseScaleY(args)
            "scalez" -> parseScaleZ(args)
            "scale3d" -> parseScale3d(args)
            "rotate" -> parseRotate(args)
            "rotatex" -> parseRotateX(args)
            "rotatey" -> parseRotateY(args)
            "rotatez" -> parseRotateZ(args)
            "rotate3d" -> parseRotate3d(args)
            "skew" -> parseSkew(args)
            "skewx" -> parseSkewX(args)
            "skewy" -> parseSkewY(args)
            "perspective" -> parsePerspective(args)
            "matrix" -> parseMatrix(args)
            "matrix3d" -> parseMatrix3d(args)
            else -> null
        }
    }

    private fun parseTranslate(args: List<String>): TransformFunction? {
        if (args.isEmpty()) return null
        val x = LengthParser.parse(args[0]) ?: return null
        val y = if (args.size > 1) LengthParser.parse(args[1]) ?: IRLength.fromPx(0.0) else IRLength.fromPx(0.0)
        return TransformFunction.Translate(x, y)
    }

    private fun parseTranslateX(args: List<String>): TransformFunction? {
        if (args.isEmpty()) return null
        val x = LengthParser.parse(args[0]) ?: return null
        return TransformFunction.TranslateX(x)
    }

    private fun parseTranslateY(args: List<String>): TransformFunction? {
        if (args.isEmpty()) return null
        val y = LengthParser.parse(args[0]) ?: return null
        return TransformFunction.TranslateY(y)
    }

    private fun parseTranslateZ(args: List<String>): TransformFunction? {
        if (args.isEmpty()) return null
        val z = LengthParser.parse(args[0]) ?: return null
        return TransformFunction.TranslateZ(z)
    }

    private fun parseTranslate3d(args: List<String>): TransformFunction? {
        if (args.size < 3) return null
        val x = LengthParser.parse(args[0]) ?: return null
        val y = LengthParser.parse(args[1]) ?: return null
        val z = LengthParser.parse(args[2]) ?: return null
        return TransformFunction.Translate3d(x, y, z)
    }

    private fun parseScale(args: List<String>): TransformFunction? {
        if (args.isEmpty()) return null
        val x = NumberParser.parse(args[0]) ?: return null
        val y = if (args.size > 1) NumberParser.parse(args[1]) ?: x else x
        return TransformFunction.Scale(x, y)
    }

    private fun parseScaleX(args: List<String>): TransformFunction? {
        if (args.isEmpty()) return null
        val x = NumberParser.parse(args[0]) ?: return null
        return TransformFunction.ScaleX(x)
    }

    private fun parseScaleY(args: List<String>): TransformFunction? {
        if (args.isEmpty()) return null
        val y = NumberParser.parse(args[0]) ?: return null
        return TransformFunction.ScaleY(y)
    }

    private fun parseScaleZ(args: List<String>): TransformFunction? {
        if (args.isEmpty()) return null
        val z = NumberParser.parse(args[0]) ?: return null
        return TransformFunction.ScaleZ(z)
    }

    private fun parseScale3d(args: List<String>): TransformFunction? {
        if (args.size < 3) return null
        val x = NumberParser.parse(args[0]) ?: return null
        val y = NumberParser.parse(args[1]) ?: return null
        val z = NumberParser.parse(args[2]) ?: return null
        return TransformFunction.Scale3d(x, y, z)
    }

    private fun parseRotate(args: List<String>): TransformFunction? {
        if (args.isEmpty()) return null
        val angle = AngleParser.parse(args[0]) ?: return null
        return TransformFunction.Rotate(angle)
    }

    private fun parseRotateX(args: List<String>): TransformFunction? {
        if (args.isEmpty()) return null
        val angle = AngleParser.parse(args[0]) ?: return null
        return TransformFunction.RotateX(angle)
    }

    private fun parseRotateY(args: List<String>): TransformFunction? {
        if (args.isEmpty()) return null
        val angle = AngleParser.parse(args[0]) ?: return null
        return TransformFunction.RotateY(angle)
    }

    private fun parseRotateZ(args: List<String>): TransformFunction? {
        if (args.isEmpty()) return null
        val angle = AngleParser.parse(args[0]) ?: return null
        return TransformFunction.RotateZ(angle)
    }

    private fun parseRotate3d(args: List<String>): TransformFunction? {
        if (args.size < 4) return null
        val x = NumberParser.parse(args[0]) ?: return null
        val y = NumberParser.parse(args[1]) ?: return null
        val z = NumberParser.parse(args[2]) ?: return null
        val angle = AngleParser.parse(args[3]) ?: return null
        return TransformFunction.Rotate3d(x, y, z, angle)
    }

    private fun parseSkew(args: List<String>): TransformFunction? {
        if (args.isEmpty()) return null
        val x = AngleParser.parse(args[0]) ?: return null
        val y = if (args.size > 1) AngleParser.parse(args[1]) ?: IRAngle.fromDegrees(0.0) else IRAngle.fromDegrees(0.0)
        return TransformFunction.Skew(x, y)
    }

    private fun parseSkewX(args: List<String>): TransformFunction? {
        if (args.isEmpty()) return null
        val angle = AngleParser.parse(args[0]) ?: return null
        return TransformFunction.SkewX(angle)
    }

    private fun parseSkewY(args: List<String>): TransformFunction? {
        if (args.isEmpty()) return null
        val angle = AngleParser.parse(args[0]) ?: return null
        return TransformFunction.SkewY(angle)
    }

    private fun parsePerspective(args: List<String>): TransformFunction? {
        if (args.isEmpty()) return null
        val length = LengthParser.parse(args[0]) ?: return null
        return TransformFunction.Perspective(length)
    }

    private fun parseMatrix(args: List<String>): TransformFunction? {
        if (args.size < 6) return null
        val values = args.take(6).mapNotNull { NumberParser.parse(it) }
        if (values.size < 6) return null
        return TransformFunction.Matrix(values[0], values[1], values[2], values[3], values[4], values[5])
    }

    private fun parseMatrix3d(args: List<String>): TransformFunction? {
        if (args.size < 16) return null
        val values = args.take(16).mapNotNull { NumberParser.parse(it) }
        if (values.size < 16) return null
        return TransformFunction.Matrix3d(
            values[0], values[1], values[2], values[3],
            values[4], values[5], values[6], values[7],
            values[8], values[9], values[10], values[11],
            values[12], values[13], values[14], values[15]
        )
    }
}
