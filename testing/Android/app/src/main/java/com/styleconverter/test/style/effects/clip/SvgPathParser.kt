package com.styleconverter.test.style.effects.clip

import androidx.compose.ui.graphics.Path
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.atan2
import kotlin.math.abs
import kotlin.math.PI

/**
 * Parses SVG path data strings into Compose Path objects.
 *
 * ## Supported Commands
 * - M/m: moveTo (absolute/relative)
 * - L/l: lineTo (absolute/relative)
 * - H/h: horizontal lineTo (absolute/relative)
 * - V/v: vertical lineTo (absolute/relative)
 * - C/c: cubic bezier (absolute/relative)
 * - S/s: smooth cubic bezier (absolute/relative)
 * - Q/q: quadratic bezier (absolute/relative)
 * - T/t: smooth quadratic bezier (absolute/relative)
 * - A/a: elliptical arc (absolute/relative)
 * - Z/z: close path
 *
 * ## Usage
 * ```kotlin
 * val path = SvgPathParser.parse("M10,10 L90,10 L90,90 L10,90 Z")
 * ```
 */
object SvgPathParser {

    /**
     * Parse an SVG path data string into a Compose Path.
     *
     * @param pathData The SVG path data string (e.g., "M0,0 L100,100")
     * @return A Compose Path object, or null if parsing fails
     */
    fun parse(pathData: String): Path? {
        if (pathData.isBlank()) return null

        return try {
            val path = Path()
            val tokens = tokenize(pathData)
            var currentX = 0f
            var currentY = 0f
            var startX = 0f
            var startY = 0f
            var lastControlX = 0f
            var lastControlY = 0f
            var lastCommand = ' '

            var i = 0
            while (i < tokens.size) {
                val token = tokens[i]

                if (token.isCommand()) {
                    val command = token[0]
                    val isRelative = command.isLowerCase()
                    i++

                    when (command.uppercaseChar()) {
                        'M' -> {
                            val coords = parseCoordPairs(tokens, i)
                            i += coords.size * 2

                            if (coords.isNotEmpty()) {
                                val (x, y) = if (isRelative) {
                                    Pair(currentX + coords[0].first, currentY + coords[0].second)
                                } else {
                                    coords[0]
                                }
                                path.moveTo(x, y)
                                currentX = x
                                currentY = y
                                startX = x
                                startY = y

                                // Subsequent coordinate pairs are treated as lineTo
                                for (j in 1 until coords.size) {
                                    val (lx, ly) = if (isRelative) {
                                        Pair(currentX + coords[j].first, currentY + coords[j].second)
                                    } else {
                                        coords[j]
                                    }
                                    path.lineTo(lx, ly)
                                    currentX = lx
                                    currentY = ly
                                }
                            }
                        }

                        'L' -> {
                            val coords = parseCoordPairs(tokens, i)
                            i += coords.size * 2

                            for ((dx, dy) in coords) {
                                val (x, y) = if (isRelative) {
                                    Pair(currentX + dx, currentY + dy)
                                } else {
                                    Pair(dx, dy)
                                }
                                path.lineTo(x, y)
                                currentX = x
                                currentY = y
                            }
                        }

                        'H' -> {
                            val values = parseNumbers(tokens, i)
                            i += values.size

                            for (dx in values) {
                                val x = if (isRelative) currentX + dx else dx
                                path.lineTo(x, currentY)
                                currentX = x
                            }
                        }

                        'V' -> {
                            val values = parseNumbers(tokens, i)
                            i += values.size

                            for (dy in values) {
                                val y = if (isRelative) currentY + dy else dy
                                path.lineTo(currentX, y)
                                currentY = y
                            }
                        }

                        'C' -> {
                            val coords = parseCoordPairs(tokens, i)
                            i += coords.size * 2

                            var j = 0
                            while (j + 2 < coords.size) {
                                val (x1, y1) = if (isRelative) {
                                    Pair(currentX + coords[j].first, currentY + coords[j].second)
                                } else {
                                    coords[j]
                                }
                                val (x2, y2) = if (isRelative) {
                                    Pair(currentX + coords[j + 1].first, currentY + coords[j + 1].second)
                                } else {
                                    coords[j + 1]
                                }
                                val (x, y) = if (isRelative) {
                                    Pair(currentX + coords[j + 2].first, currentY + coords[j + 2].second)
                                } else {
                                    coords[j + 2]
                                }

                                path.cubicTo(x1, y1, x2, y2, x, y)
                                lastControlX = x2
                                lastControlY = y2
                                currentX = x
                                currentY = y
                                j += 3
                            }
                        }

                        'S' -> {
                            val coords = parseCoordPairs(tokens, i)
                            i += coords.size * 2

                            var j = 0
                            while (j + 1 < coords.size) {
                                // Reflect last control point
                                val x1 = if (lastCommand in "CcSs") {
                                    2 * currentX - lastControlX
                                } else {
                                    currentX
                                }
                                val y1 = if (lastCommand in "CcSs") {
                                    2 * currentY - lastControlY
                                } else {
                                    currentY
                                }

                                val (x2, y2) = if (isRelative) {
                                    Pair(currentX + coords[j].first, currentY + coords[j].second)
                                } else {
                                    coords[j]
                                }
                                val (x, y) = if (isRelative) {
                                    Pair(currentX + coords[j + 1].first, currentY + coords[j + 1].second)
                                } else {
                                    coords[j + 1]
                                }

                                path.cubicTo(x1, y1, x2, y2, x, y)
                                lastControlX = x2
                                lastControlY = y2
                                currentX = x
                                currentY = y
                                j += 2
                            }
                        }

                        'Q' -> {
                            val coords = parseCoordPairs(tokens, i)
                            i += coords.size * 2

                            var j = 0
                            while (j + 1 < coords.size) {
                                val (x1, y1) = if (isRelative) {
                                    Pair(currentX + coords[j].first, currentY + coords[j].second)
                                } else {
                                    coords[j]
                                }
                                val (x, y) = if (isRelative) {
                                    Pair(currentX + coords[j + 1].first, currentY + coords[j + 1].second)
                                } else {
                                    coords[j + 1]
                                }

                                path.quadraticTo(x1, y1, x, y)
                                lastControlX = x1
                                lastControlY = y1
                                currentX = x
                                currentY = y
                                j += 2
                            }
                        }

                        'T' -> {
                            val coords = parseCoordPairs(tokens, i)
                            i += coords.size * 2

                            for ((dx, dy) in coords) {
                                // Reflect last control point
                                val x1 = if (lastCommand in "QqTt") {
                                    2 * currentX - lastControlX
                                } else {
                                    currentX
                                }
                                val y1 = if (lastCommand in "QqTt") {
                                    2 * currentY - lastControlY
                                } else {
                                    currentY
                                }

                                val (x, y) = if (isRelative) {
                                    Pair(currentX + dx, currentY + dy)
                                } else {
                                    Pair(dx, dy)
                                }

                                path.quadraticTo(x1, y1, x, y)
                                lastControlX = x1
                                lastControlY = y1
                                currentX = x
                                currentY = y
                            }
                        }

                        'A' -> {
                            val values = parseNumbers(tokens, i)
                            i += values.size

                            var j = 0
                            while (j + 6 < values.size) {
                                val rx = values[j]
                                val ry = values[j + 1]
                                val xAxisRotation = values[j + 2]
                                val largeArcFlag = values[j + 3] != 0f
                                val sweepFlag = values[j + 4] != 0f
                                val (x, y) = if (isRelative) {
                                    Pair(currentX + values[j + 5], currentY + values[j + 6])
                                } else {
                                    Pair(values[j + 5], values[j + 6])
                                }

                                drawArc(path, currentX, currentY, x, y, rx, ry, xAxisRotation, largeArcFlag, sweepFlag)
                                currentX = x
                                currentY = y
                                j += 7
                            }
                        }

                        'Z' -> {
                            path.close()
                            currentX = startX
                            currentY = startY
                        }
                    }

                    lastCommand = command
                } else {
                    i++
                }
            }

            path
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Tokenize SVG path data string.
     */
    private fun tokenize(pathData: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()

        for (char in pathData) {
            when {
                char.isCommand() -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current.clear()
                    }
                    tokens.add(char.toString())
                }
                char == ',' || char.isWhitespace() -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current.clear()
                    }
                }
                char == '-' && current.isNotEmpty() && !current.endsWith("e", ignoreCase = true) -> {
                    // Negative sign starts a new number (except in scientific notation)
                    tokens.add(current.toString())
                    current.clear()
                    current.append(char)
                }
                else -> {
                    current.append(char)
                }
            }
        }

        if (current.isNotEmpty()) {
            tokens.add(current.toString())
        }

        return tokens
    }

    /**
     * Parse coordinate pairs starting at the given index.
     */
    private fun parseCoordPairs(tokens: List<String>, startIndex: Int): List<Pair<Float, Float>> {
        val pairs = mutableListOf<Pair<Float, Float>>()
        var i = startIndex

        while (i + 1 < tokens.size && !tokens[i].isCommand()) {
            val x = tokens[i].toFloatOrNull() ?: break
            if (tokens[i + 1].isCommand()) break
            val y = tokens[i + 1].toFloatOrNull() ?: break
            pairs.add(Pair(x, y))
            i += 2
        }

        return pairs
    }

    /**
     * Parse numbers starting at the given index.
     */
    private fun parseNumbers(tokens: List<String>, startIndex: Int): List<Float> {
        val numbers = mutableListOf<Float>()
        var i = startIndex

        while (i < tokens.size && !tokens[i].isCommand()) {
            tokens[i].toFloatOrNull()?.let { numbers.add(it) }
            i++
        }

        return numbers
    }

    /**
     * Check if a string is a command.
     */
    private fun String.isCommand(): Boolean {
        return length == 1 && this[0].isCommand()
    }

    /**
     * Check if a character is an SVG path command.
     */
    private fun Char.isCommand(): Boolean {
        return this in "MmLlHhVvCcSsQqTtAaZz"
    }

    /**
     * Draw an elliptical arc.
     *
     * Implementation based on the SVG arc to bezier conversion algorithm.
     */
    private fun drawArc(
        path: Path,
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        rx: Float, ry: Float,
        xAxisRotation: Float,
        largeArcFlag: Boolean,
        sweepFlag: Boolean
    ) {
        if (rx == 0f || ry == 0f) {
            path.lineTo(x2, y2)
            return
        }

        val phi = Math.toRadians(xAxisRotation.toDouble())
        val cosPhi = cos(phi)
        val sinPhi = sin(phi)

        // Step 1: Compute (x1', y1')
        val dx = (x1 - x2) / 2
        val dy = (y1 - y2) / 2
        val x1p = cosPhi * dx + sinPhi * dy
        val y1p = -sinPhi * dx + cosPhi * dy

        // Correct radii
        var rxCorrected = abs(rx.toDouble())
        var ryCorrected = abs(ry.toDouble())

        val lambda = (x1p * x1p) / (rxCorrected * rxCorrected) + (y1p * y1p) / (ryCorrected * ryCorrected)
        if (lambda > 1) {
            val sqrtLambda = sqrt(lambda)
            rxCorrected *= sqrtLambda
            ryCorrected *= sqrtLambda
        }

        // Step 2: Compute (cx', cy')
        val rxSq = rxCorrected * rxCorrected
        val rySq = ryCorrected * ryCorrected
        val x1pSq = x1p * x1p
        val y1pSq = y1p * y1p

        var sq = ((rxSq * rySq) - (rxSq * y1pSq) - (rySq * x1pSq)) /
                ((rxSq * y1pSq) + (rySq * x1pSq))
        if (sq < 0) sq = 0.0

        val coef = (if (largeArcFlag != sweepFlag) 1 else -1) * sqrt(sq)
        val cxp = coef * (rxCorrected * y1p / ryCorrected)
        val cyp = coef * -(ryCorrected * x1p / rxCorrected)

        // Step 3: Compute (cx, cy)
        val cx = cosPhi * cxp - sinPhi * cyp + (x1 + x2) / 2
        val cy = sinPhi * cxp + cosPhi * cyp + (y1 + y2) / 2

        // Step 4: Compute angles
        val theta1 = angleBetween(1.0, 0.0, (x1p - cxp) / rxCorrected, (y1p - cyp) / ryCorrected)
        var dTheta = angleBetween(
            (x1p - cxp) / rxCorrected, (y1p - cyp) / ryCorrected,
            (-x1p - cxp) / rxCorrected, (-y1p - cyp) / ryCorrected
        )

        if (!sweepFlag && dTheta > 0) {
            dTheta -= 2 * PI
        } else if (sweepFlag && dTheta < 0) {
            dTheta += 2 * PI
        }

        // Convert arc to cubic bezier curves
        arcToBezier(path, cx, cy, rxCorrected, ryCorrected, theta1, dTheta, phi)
    }

    /**
     * Calculate angle between two vectors.
     */
    private fun angleBetween(ux: Double, uy: Double, vx: Double, vy: Double): Double {
        val n = sqrt(ux * ux + uy * uy) * sqrt(vx * vx + vy * vy)
        if (n == 0.0) return 0.0

        var c = (ux * vx + uy * vy) / n
        c = c.coerceIn(-1.0, 1.0)

        val angle = kotlin.math.acos(c)
        return if (ux * vy - uy * vx < 0) -angle else angle
    }

    /**
     * Convert arc to bezier curves and add to path.
     */
    private fun arcToBezier(
        path: Path,
        cx: Double, cy: Double,
        rx: Double, ry: Double,
        theta1: Double, dTheta: Double,
        phi: Double
    ) {
        val numSegments = kotlin.math.ceil(abs(dTheta) / (PI / 2)).toInt().coerceAtLeast(1)
        val delta = dTheta / numSegments
        val t = 8.0 / 3.0 * sin(delta / 4) * sin(delta / 4) / sin(delta / 2)

        var theta = theta1
        var cosTheta = cos(theta)
        var sinTheta = sin(theta)

        val cosPhi = cos(phi)
        val sinPhi = sin(phi)

        for (i in 0 until numSegments) {
            val nextTheta = theta + delta
            val cosNextTheta = cos(nextTheta)
            val sinNextTheta = sin(nextTheta)

            // Control point 1
            val dx1 = cosTheta - t * sinTheta
            val dy1 = sinTheta + t * cosTheta
            val x1 = (cosPhi * rx * dx1 - sinPhi * ry * dy1 + cx).toFloat()
            val y1 = (sinPhi * rx * dx1 + cosPhi * ry * dy1 + cy).toFloat()

            // Control point 2
            val dx2 = cosNextTheta + t * sinNextTheta
            val dy2 = sinNextTheta - t * cosNextTheta
            val x2 = (cosPhi * rx * dx2 - sinPhi * ry * dy2 + cx).toFloat()
            val y2 = (sinPhi * rx * dx2 + cosPhi * ry * dy2 + cy).toFloat()

            // End point
            val x = (cosPhi * rx * cosNextTheta - sinPhi * ry * sinNextTheta + cx).toFloat()
            val y = (sinPhi * rx * cosNextTheta + cosPhi * ry * sinNextTheta + cy).toFloat()

            path.cubicTo(x1, y1, x2, y2, x, y)

            theta = nextTheta
            cosTheta = cosNextTheta
            sinTheta = sinNextTheta
        }
    }
}
