package com.styleconverter.test.style.borders.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.styleconverter.test.style.core.images.ImageCache
import com.styleconverter.test.style.core.images.rememberCachedGradient
import com.styleconverter.test.style.core.images.rememberCachedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Applies CSS border-image using custom Canvas drawing.
 *
 * ## CSS Property
 * ```css
 * .decorative-border {
 *     border-image-source: url('border.png');
 *     border-image-slice: 30;
 *     border-image-width: 20px;
 *     border-image-outset: 5px;
 *     border-image-repeat: round;
 * }
 * ```
 *
 * ## Compose Limitation
 *
 * Compose's `Modifier.border()` only supports:
 * - Solid colors
 * - Basic shapes (rectangle, rounded rectangle, circle)
 * - No image-based borders
 *
 * ## Implementation Strategy
 *
 * CSS border-image uses a 9-slice scaling approach:
 * ```
 * ┌─────┬─────────┬─────┐
 * │  1  │    2    │  3  │  ← top slice
 * ├─────┼─────────┼─────┤
 * │  4  │    5    │  6  │  ← middle (fill if sliceFill=true)
 * ├─────┼─────────┼─────┤
 * │  7  │    8    │  9  │  ← bottom slice
 * └─────┴─────────┴─────┘
 *   ↑        ↑        ↑
 * left    middle   right
 * slice            slice
 * ```
 *
 * - Corners (1, 3, 7, 9): Drawn without scaling
 * - Edges (2, 4, 6, 8): Stretched, repeated, or rounded based on repeat value
 * - Center (5): Filled only if sliceFill is true
 *
 * ## Usage
 * ```kotlin
 * BorderImageApplier.BorderImageBox(
 *     config = borderImageConfig,
 *     modifier = Modifier.size(200.dp)
 * ) {
 *     // Content
 * }
 * ```
 *
 * ## Limitations
 * - Gradient sources require separate implementation
 * - Network images loaded asynchronously
 * - Performance: Custom drawing for each frame
 */
object BorderImageApplier {

    /**
     * Composable that renders content with a border image.
     *
     * @param config Border image configuration
     * @param modifier Modifier for the container
     * @param fallbackBorderWidth Fallback width if not specified in config
     * @param content Content to render inside the border
     */
    @Composable
    fun BorderImageBox(
        config: BorderImageConfig,
        modifier: Modifier = Modifier,
        fallbackBorderWidth: Dp = 8.dp,
        content: @Composable BoxScope.() -> Unit
    ) {
        if (!config.hasBorderImage) {
            // No border image, just render content
            Box(modifier = modifier, content = content)
            return
        }

        val density = LocalDensity.current

        // Use cached image loading
        val urlBitmap = when (val source = config.source) {
            is BorderImageSourceValue.Url -> rememberCachedImage(source.url)
            else -> null
        }

        // Use cached gradient generation
        val gradientBitmap = when (val source = config.source) {
            is BorderImageSourceValue.Gradient -> rememberCachedGradient(
                gradientString = source.gradient,
                width = 256,
                height = 256,
                generator = { str, w, h -> renderGradientToBitmap(str, w, h) }
            )
            else -> null
        }

        val imageBitmap = urlBitmap ?: gradientBitmap

        // Calculate border widths
        val borderTop = config.widthTop.toDp(fallbackBorderWidth)
        val borderRight = config.widthRight.toDp(fallbackBorderWidth)
        val borderBottom = config.widthBottom.toDp(fallbackBorderWidth)
        val borderLeft = config.widthLeft.toDp(fallbackBorderWidth)

        // Calculate outsets
        val outsetTop = config.outsetTop.toDp(0.dp)
        val outsetRight = config.outsetRight.toDp(0.dp)
        val outsetBottom = config.outsetBottom.toDp(0.dp)
        val outsetLeft = config.outsetLeft.toDp(0.dp)

        Box(
            modifier = modifier
                .padding(
                    start = outsetLeft,
                    top = outsetTop,
                    end = outsetRight,
                    bottom = outsetBottom
                )
                .drawBehind {
                    imageBitmap?.let { bitmap ->
                        drawBorderImage(
                            bitmap = bitmap,
                            config = config,
                            borderTop = borderTop.toPx(),
                            borderRight = borderRight.toPx(),
                            borderBottom = borderBottom.toPx(),
                            borderLeft = borderLeft.toPx()
                        )
                    }
                }
                .padding(
                    start = borderLeft,
                    top = borderTop,
                    end = borderRight,
                    bottom = borderBottom
                ),
            content = content
        )
    }

    /**
     * Draw the 9-slice border image.
     */
    private fun DrawScope.drawBorderImage(
        bitmap: ImageBitmap,
        config: BorderImageConfig,
        borderTop: Float,
        borderRight: Float,
        borderBottom: Float,
        borderLeft: Float
    ) {
        val imageWidth = bitmap.width.toFloat()
        val imageHeight = bitmap.height.toFloat()

        // Calculate slice sizes in image pixels
        val sliceTop = config.sliceTop.toPixels(imageHeight)
        val sliceRight = config.sliceRight.toPixels(imageWidth)
        val sliceBottom = config.sliceBottom.toPixels(imageHeight)
        val sliceLeft = config.sliceLeft.toPixels(imageWidth)

        // Destination area (full size)
        val destWidth = size.width
        val destHeight = size.height

        // Source rectangles (from the image)
        val srcTopLeft = IntRect(0, 0, sliceLeft.toInt(), sliceTop.toInt())
        val srcTopCenter = IntRect(sliceLeft.toInt(), 0, (imageWidth - sliceRight).toInt(), sliceTop.toInt())
        val srcTopRight = IntRect((imageWidth - sliceRight).toInt(), 0, imageWidth.toInt(), sliceTop.toInt())

        val srcMiddleLeft = IntRect(0, sliceTop.toInt(), sliceLeft.toInt(), (imageHeight - sliceBottom).toInt())
        val srcMiddleCenter = IntRect(sliceLeft.toInt(), sliceTop.toInt(), (imageWidth - sliceRight).toInt(), (imageHeight - sliceBottom).toInt())
        val srcMiddleRight = IntRect((imageWidth - sliceRight).toInt(), sliceTop.toInt(), imageWidth.toInt(), (imageHeight - sliceBottom).toInt())

        val srcBottomLeft = IntRect(0, (imageHeight - sliceBottom).toInt(), sliceLeft.toInt(), imageHeight.toInt())
        val srcBottomCenter = IntRect(sliceLeft.toInt(), (imageHeight - sliceBottom).toInt(), (imageWidth - sliceRight).toInt(), imageHeight.toInt())
        val srcBottomRight = IntRect((imageWidth - sliceRight).toInt(), (imageHeight - sliceBottom).toInt(), imageWidth.toInt(), imageHeight.toInt())

        // Destination rectangles
        val dstTopLeft = IntRect(0, 0, borderLeft.toInt(), borderTop.toInt())
        val dstTopCenter = IntRect(borderLeft.toInt(), 0, (destWidth - borderRight).toInt(), borderTop.toInt())
        val dstTopRight = IntRect((destWidth - borderRight).toInt(), 0, destWidth.toInt(), borderTop.toInt())

        val dstMiddleLeft = IntRect(0, borderTop.toInt(), borderLeft.toInt(), (destHeight - borderBottom).toInt())
        val dstMiddleCenter = IntRect(borderLeft.toInt(), borderTop.toInt(), (destWidth - borderRight).toInt(), (destHeight - borderBottom).toInt())
        val dstMiddleRight = IntRect((destWidth - borderRight).toInt(), borderTop.toInt(), destWidth.toInt(), (destHeight - borderBottom).toInt())

        val dstBottomLeft = IntRect(0, (destHeight - borderBottom).toInt(), borderLeft.toInt(), destHeight.toInt())
        val dstBottomCenter = IntRect(borderLeft.toInt(), (destHeight - borderBottom).toInt(), (destWidth - borderRight).toInt(), destHeight.toInt())
        val dstBottomRight = IntRect((destWidth - borderRight).toInt(), (destHeight - borderBottom).toInt(), destWidth.toInt(), destHeight.toInt())

        // Draw corners (no scaling needed for repeat, just stretch)
        drawImageRect(bitmap, srcTopLeft, dstTopLeft)
        drawImageRect(bitmap, srcTopRight, dstTopRight)
        drawImageRect(bitmap, srcBottomLeft, dstBottomLeft)
        drawImageRect(bitmap, srcBottomRight, dstBottomRight)

        // Draw edges based on repeat mode
        drawEdge(bitmap, srcTopCenter, dstTopCenter, config.repeatHorizontal, isHorizontal = true)
        drawEdge(bitmap, srcBottomCenter, dstBottomCenter, config.repeatHorizontal, isHorizontal = true)
        drawEdge(bitmap, srcMiddleLeft, dstMiddleLeft, config.repeatVertical, isHorizontal = false)
        drawEdge(bitmap, srcMiddleRight, dstMiddleRight, config.repeatVertical, isHorizontal = false)

        // Draw center if fill is enabled
        if (config.sliceFill) {
            drawImageRect(bitmap, srcMiddleCenter, dstMiddleCenter)
        }
    }

    /**
     * Helper function to draw an image from source rect to destination rect.
     */
    private fun DrawScope.drawImageRect(
        bitmap: ImageBitmap,
        srcRect: IntRect,
        dstRect: IntRect
    ) {
        drawImage(
            image = bitmap,
            srcOffset = IntOffset(srcRect.left, srcRect.top),
            srcSize = IntSize(srcRect.width, srcRect.height),
            dstOffset = IntOffset(dstRect.left, dstRect.top),
            dstSize = IntSize(dstRect.width, dstRect.height)
        )
    }

    /**
     * Draw an edge region with the specified repeat mode.
     */
    private fun DrawScope.drawEdge(
        bitmap: ImageBitmap,
        srcRect: IntRect,
        dstRect: IntRect,
        repeatMode: BorderImageRepeatValue,
        isHorizontal: Boolean
    ) {
        when (repeatMode) {
            BorderImageRepeatValue.STRETCH -> {
                // Simple stretch
                drawImageRect(bitmap, srcRect, dstRect)
            }
            BorderImageRepeatValue.REPEAT -> {
                // Tile the image
                drawRepeatedEdge(bitmap, srcRect, dstRect, isHorizontal, scaled = false)
            }
            BorderImageRepeatValue.ROUND -> {
                // Tile with scaling to fit evenly
                drawRepeatedEdge(bitmap, srcRect, dstRect, isHorizontal, scaled = true)
            }
            BorderImageRepeatValue.SPACE -> {
                // Tile with spacing (similar to round but with gaps)
                drawSpacedEdge(bitmap, srcRect, dstRect, isHorizontal)
            }
        }
    }

    /**
     * Draw repeated tiles along an edge.
     */
    private fun DrawScope.drawRepeatedEdge(
        bitmap: ImageBitmap,
        srcRect: IntRect,
        dstRect: IntRect,
        isHorizontal: Boolean,
        scaled: Boolean
    ) {
        val srcWidth = srcRect.width
        val srcHeight = srcRect.height
        val dstWidth = dstRect.width
        val dstHeight = dstRect.height

        if (isHorizontal) {
            val tileWidth = if (scaled) {
                val count = kotlin.math.max(1, kotlin.math.round(dstWidth.toFloat() / srcWidth).toInt())
                dstWidth / count
            } else {
                srcWidth
            }

            var x = dstRect.left
            while (x < dstRect.right) {
                val width = minOf(tileWidth, dstRect.right - x)
                drawImageRect(
                    bitmap,
                    srcRect,
                    IntRect(x, dstRect.top, x + width, dstRect.bottom)
                )
                x += tileWidth
            }
        } else {
            val tileHeight = if (scaled) {
                val count = kotlin.math.max(1, kotlin.math.round(dstHeight.toFloat() / srcHeight).toInt())
                dstHeight / count
            } else {
                srcHeight
            }

            var y = dstRect.top
            while (y < dstRect.bottom) {
                val height = minOf(tileHeight, dstRect.bottom - y)
                drawImageRect(
                    bitmap,
                    srcRect,
                    IntRect(dstRect.left, y, dstRect.right, y + height)
                )
                y += tileHeight
            }
        }
    }

    /**
     * Draw tiles with spacing between them.
     */
    private fun DrawScope.drawSpacedEdge(
        bitmap: ImageBitmap,
        srcRect: IntRect,
        dstRect: IntRect,
        isHorizontal: Boolean
    ) {
        val srcWidth = srcRect.width
        val srcHeight = srcRect.height
        val dstWidth = dstRect.width
        val dstHeight = dstRect.height

        if (isHorizontal) {
            val count = kotlin.math.max(1, dstWidth / srcWidth)
            if (count == 1) {
                drawImageRect(bitmap, srcRect, dstRect)
                return
            }
            val spacing = (dstWidth - count * srcWidth) / (count - 1)

            var x = dstRect.left
            repeat(count) {
                drawImageRect(
                    bitmap,
                    srcRect,
                    IntRect(x, dstRect.top, x + srcWidth, dstRect.bottom)
                )
                x += srcWidth + spacing
            }
        } else {
            val count = kotlin.math.max(1, dstHeight / srcHeight)
            if (count == 1) {
                drawImageRect(bitmap, srcRect, dstRect)
                return
            }
            val spacing = (dstHeight - count * srcHeight) / (count - 1)

            var y = dstRect.top
            repeat(count) {
                drawImageRect(
                    bitmap,
                    srcRect,
                    IntRect(dstRect.left, y, dstRect.right, y + srcHeight)
                )
                y += srcHeight + spacing
            }
        }
    }

    /**
     * Load an image from a URL with caching.
     */
    private suspend fun loadImageFromUrl(urlString: String): ImageBitmap? {
        // Use ImageCache for cached loading
        return ImageCache.loadImage(urlString)
    }

    /**
     * Load an image from a URL without caching (legacy fallback).
     */
    private suspend fun loadImageFromUrlDirect(urlString: String): ImageBitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(urlString)
                val inputStream = url.openStream()
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                bitmap?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }

    // ==================== GRADIENT RENDERING ====================

    /**
     * Render a CSS gradient string to a bitmap.
     *
     * Supports:
     * - linear-gradient(angle, colors...)
     * - radial-gradient(colors...)
     * - conic-gradient(colors...)
     * - repeating-linear-gradient, repeating-radial-gradient
     *
     * @param gradientString The CSS gradient string
     * @param width Bitmap width
     * @param height Bitmap height
     * @return ImageBitmap with the rendered gradient
     */
    private suspend fun renderGradientToBitmap(
        gradientString: String,
        width: Int,
        height: Int
    ): ImageBitmap? = withContext(Dispatchers.Default) {
        try {
            val parsed = parseGradient(gradientString)
            if (parsed == null) return@withContext null

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            val shader = when (parsed) {
                is ParsedGradient.Linear -> createLinearGradientShader(parsed, width.toFloat(), height.toFloat())
                is ParsedGradient.Radial -> createRadialGradientShader(parsed, width.toFloat(), height.toFloat())
                is ParsedGradient.Conic -> createConicGradientShader(parsed, width.toFloat(), height.toFloat())
            }

            paint.shader = shader
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

            bitmap.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parsed gradient representation.
     */
    private sealed interface ParsedGradient {
        data class Linear(
            val angle: Float, // degrees
            val colors: IntArray,
            val positions: FloatArray?,
            val repeating: Boolean
        ) : ParsedGradient

        data class Radial(
            val centerX: Float, // 0-1
            val centerY: Float, // 0-1
            val colors: IntArray,
            val positions: FloatArray?,
            val repeating: Boolean
        ) : ParsedGradient

        data class Conic(
            val centerX: Float, // 0-1
            val centerY: Float, // 0-1
            val startAngle: Float, // degrees
            val colors: IntArray,
            val positions: FloatArray?,
            val repeating: Boolean
        ) : ParsedGradient
    }

    /**
     * Parse a CSS gradient string.
     */
    private fun parseGradient(gradientString: String): ParsedGradient? {
        val normalized = gradientString.trim().lowercase()

        return when {
            normalized.startsWith("linear-gradient") ||
            normalized.startsWith("repeating-linear-gradient") -> {
                parseLinearGradient(gradientString, normalized.startsWith("repeating"))
            }
            normalized.startsWith("radial-gradient") ||
            normalized.startsWith("repeating-radial-gradient") -> {
                parseRadialGradient(gradientString, normalized.startsWith("repeating"))
            }
            normalized.startsWith("conic-gradient") ||
            normalized.startsWith("repeating-conic-gradient") -> {
                parseConicGradient(gradientString, normalized.startsWith("repeating"))
            }
            else -> null
        }
    }

    /**
     * Parse linear-gradient CSS function.
     */
    private fun parseLinearGradient(gradientString: String, repeating: Boolean): ParsedGradient.Linear? {
        val content = extractGradientContent(gradientString) ?: return null
        val parts = splitGradientParts(content)
        if (parts.isEmpty()) return null

        var angle = 180f // Default: to bottom
        var colorStartIndex = 0

        // Check if first part is an angle or direction
        val firstPart = parts[0].trim()
        if (firstPart.endsWith("deg")) {
            angle = firstPart.dropLast(3).toFloatOrNull() ?: 180f
            colorStartIndex = 1
        } else if (firstPart.startsWith("to ")) {
            angle = parseDirection(firstPart)
            colorStartIndex = 1
        }

        val colorStops = parseColorStops(parts.subList(colorStartIndex, parts.size))
        if (colorStops.isEmpty()) return null

        val colors = colorStops.map { it.first }.toIntArray()
        val positions = colorStops.map { it.second }.takeIf { it.all { pos -> pos != null } }
            ?.map { it!! }?.toFloatArray()

        return ParsedGradient.Linear(angle, colors, positions, repeating)
    }

    /**
     * Parse radial-gradient CSS function.
     */
    private fun parseRadialGradient(gradientString: String, repeating: Boolean): ParsedGradient.Radial? {
        val content = extractGradientContent(gradientString) ?: return null
        val parts = splitGradientParts(content)
        if (parts.isEmpty()) return null

        var centerX = 0.5f
        var centerY = 0.5f
        var colorStartIndex = 0

        // Check for position/shape specification
        val firstPart = parts[0].trim()
        if (firstPart.contains("at ")) {
            val posStr = firstPart.substringAfter("at ").trim()
            val (x, y) = parsePosition(posStr)
            centerX = x
            centerY = y
            colorStartIndex = 1
        }

        val colorStops = parseColorStops(parts.subList(colorStartIndex, parts.size))
        if (colorStops.isEmpty()) return null

        val colors = colorStops.map { it.first }.toIntArray()
        val positions = colorStops.map { it.second }.takeIf { it.all { pos -> pos != null } }
            ?.map { it!! }?.toFloatArray()

        return ParsedGradient.Radial(centerX, centerY, colors, positions, repeating)
    }

    /**
     * Parse conic-gradient CSS function.
     */
    private fun parseConicGradient(gradientString: String, repeating: Boolean): ParsedGradient.Conic? {
        val content = extractGradientContent(gradientString) ?: return null
        val parts = splitGradientParts(content)
        if (parts.isEmpty()) return null

        var centerX = 0.5f
        var centerY = 0.5f
        var startAngle = 0f
        var colorStartIndex = 0

        // Check for "from X at Y" specification
        val firstPart = parts[0].trim()
        if (firstPart.startsWith("from ")) {
            val fromStr = firstPart.substringAfter("from ").trim()
            if (fromStr.contains("at ")) {
                startAngle = fromStr.substringBefore("at ").trim().replace("deg", "").toFloatOrNull() ?: 0f
                val posStr = fromStr.substringAfter("at ").trim()
                val (x, y) = parsePosition(posStr)
                centerX = x
                centerY = y
            } else {
                startAngle = fromStr.replace("deg", "").toFloatOrNull() ?: 0f
            }
            colorStartIndex = 1
        } else if (firstPart.contains("at ")) {
            val posStr = firstPart.substringAfter("at ").trim()
            val (x, y) = parsePosition(posStr)
            centerX = x
            centerY = y
            colorStartIndex = 1
        }

        val colorStops = parseColorStops(parts.subList(colorStartIndex, parts.size))
        if (colorStops.isEmpty()) return null

        val colors = colorStops.map { it.first }.toIntArray()
        val positions = colorStops.map { it.second }.takeIf { it.all { pos -> pos != null } }
            ?.map { it!! }?.toFloatArray()

        return ParsedGradient.Conic(centerX, centerY, startAngle, colors, positions, repeating)
    }

    /**
     * Extract content from gradient function (everything inside parentheses).
     */
    private fun extractGradientContent(gradientString: String): String? {
        val start = gradientString.indexOf('(')
        val end = gradientString.lastIndexOf(')')
        if (start == -1 || end == -1 || end <= start) return null
        return gradientString.substring(start + 1, end)
    }

    /**
     * Split gradient parts by comma, respecting nested parentheses.
     */
    private fun splitGradientParts(content: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var current = StringBuilder()

        for (char in content) {
            when (char) {
                '(' -> {
                    depth++
                    current.append(char)
                }
                ')' -> {
                    depth--
                    current.append(char)
                }
                ',' -> {
                    if (depth == 0) {
                        parts.add(current.toString().trim())
                        current = StringBuilder()
                    } else {
                        current.append(char)
                    }
                }
                else -> current.append(char)
            }
        }
        if (current.isNotEmpty()) {
            parts.add(current.toString().trim())
        }
        return parts
    }

    /**
     * Parse direction keyword to angle.
     */
    private fun parseDirection(direction: String): Float {
        return when (direction.lowercase().trim()) {
            "to top" -> 0f
            "to top right", "to right top" -> 45f
            "to right" -> 90f
            "to bottom right", "to right bottom" -> 135f
            "to bottom" -> 180f
            "to bottom left", "to left bottom" -> 225f
            "to left" -> 270f
            "to top left", "to left top" -> 315f
            else -> 180f
        }
    }

    /**
     * Parse position keywords to fractions.
     */
    private fun parsePosition(posStr: String): Pair<Float, Float> {
        val parts = posStr.split(" ").map { it.trim().lowercase() }
        var x = 0.5f
        var y = 0.5f

        for (part in parts) {
            when (part) {
                "left" -> x = 0f
                "center" -> { /* already default */ }
                "right" -> x = 1f
                "top" -> y = 0f
                "bottom" -> y = 1f
            }
            if (part.endsWith("%")) {
                val value = part.dropLast(1).toFloatOrNull()?.div(100f)
                if (value != null) {
                    if (parts.indexOf(part) == 0) x = value else y = value
                }
            }
        }
        return x to y
    }

    /**
     * Parse color stops.
     * Returns list of (color ARGB int, position 0-1 or null).
     */
    private fun parseColorStops(parts: List<String>): List<Pair<Int, Float?>> {
        if (parts.isEmpty()) return emptyList()

        return parts.mapNotNull { part ->
            val colorAndPos = parseColorStop(part.trim())
            colorAndPos
        }.ifEmpty {
            // Fallback: black to white
            listOf(
                android.graphics.Color.BLACK to 0f,
                android.graphics.Color.WHITE to 1f
            )
        }
    }

    /**
     * Parse a single color stop (e.g., "red 50%", "#fff", "rgb(255,0,0) 25%").
     */
    private fun parseColorStop(part: String): Pair<Int, Float?>? {
        val trimmed = part.trim()
        if (trimmed.isEmpty()) return null

        // Check for position at end
        val posMatch = Regex("""(.+?)\s+(\d+(?:\.\d+)?%?)$""").find(trimmed)

        val colorStr: String
        val position: Float?

        if (posMatch != null) {
            colorStr = posMatch.groupValues[1].trim()
            val posStr = posMatch.groupValues[2]
            position = if (posStr.endsWith("%")) {
                posStr.dropLast(1).toFloatOrNull()?.div(100f)
            } else {
                posStr.toFloatOrNull()
            }
        } else {
            colorStr = trimmed
            position = null
        }

        val color = parseColor(colorStr) ?: return null
        return color to position
    }

    /**
     * Parse a CSS color string to ARGB int.
     */
    private fun parseColor(colorStr: String): Int? {
        val trimmed = colorStr.trim().lowercase()

        // Named colors
        NAMED_COLORS[trimmed]?.let { return it }

        // Hex colors
        if (trimmed.startsWith("#")) {
            return try {
                android.graphics.Color.parseColor(trimmed)
            } catch (e: Exception) {
                null
            }
        }

        // rgb/rgba
        if (trimmed.startsWith("rgb")) {
            val match = Regex("""rgba?\s*\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)(?:\s*,\s*([\d.]+))?\s*\)""").find(trimmed)
            if (match != null) {
                val r = match.groupValues[1].toIntOrNull() ?: 0
                val g = match.groupValues[2].toIntOrNull() ?: 0
                val b = match.groupValues[3].toIntOrNull() ?: 0
                val a = match.groupValues.getOrNull(4)?.toFloatOrNull() ?: 1f
                return android.graphics.Color.argb((a * 255).toInt(), r, g, b)
            }
        }

        return null
    }

    /**
     * Create LinearGradient shader from parsed data.
     */
    private fun createLinearGradientShader(
        gradient: ParsedGradient.Linear,
        width: Float,
        height: Float
    ): LinearGradient {
        val angleRad = (gradient.angle - 90) * PI.toFloat() / 180f
        val centerX = width / 2
        val centerY = height / 2
        val diag = kotlin.math.sqrt(width * width + height * height) / 2

        val startX = centerX - cos(angleRad) * diag
        val startY = centerY - sin(angleRad) * diag
        val endX = centerX + cos(angleRad) * diag
        val endY = centerY + sin(angleRad) * diag

        return if (gradient.positions != null) {
            LinearGradient(
                startX, startY, endX, endY,
                gradient.colors, gradient.positions,
                if (gradient.repeating) Shader.TileMode.REPEAT else Shader.TileMode.CLAMP
            )
        } else {
            LinearGradient(
                startX, startY, endX, endY,
                gradient.colors, null,
                if (gradient.repeating) Shader.TileMode.REPEAT else Shader.TileMode.CLAMP
            )
        }
    }

    /**
     * Create RadialGradient shader from parsed data.
     */
    private fun createRadialGradientShader(
        gradient: ParsedGradient.Radial,
        width: Float,
        height: Float
    ): RadialGradient {
        val centerX = gradient.centerX * width
        val centerY = gradient.centerY * height
        val radius = kotlin.math.max(width, height) / 2

        return if (gradient.positions != null) {
            RadialGradient(
                centerX, centerY, radius,
                gradient.colors, gradient.positions,
                if (gradient.repeating) Shader.TileMode.REPEAT else Shader.TileMode.CLAMP
            )
        } else {
            RadialGradient(
                centerX, centerY, radius,
                gradient.colors, null,
                if (gradient.repeating) Shader.TileMode.REPEAT else Shader.TileMode.CLAMP
            )
        }
    }

    /**
     * Create SweepGradient shader from parsed data.
     */
    private fun createConicGradientShader(
        gradient: ParsedGradient.Conic,
        width: Float,
        height: Float
    ): SweepGradient {
        val centerX = gradient.centerX * width
        val centerY = gradient.centerY * height

        // Note: SweepGradient starts at 3 o'clock position (0°)
        // CSS conic-gradient starts at 12 o'clock (top)
        // We need to rotate the colors array to account for this
        return if (gradient.positions != null) {
            SweepGradient(centerX, centerY, gradient.colors, gradient.positions)
        } else {
            SweepGradient(centerX, centerY, gradient.colors, null)
        }
    }

    /**
     * Common named colors.
     */
    private val NAMED_COLORS = mapOf(
        "black" to android.graphics.Color.BLACK,
        "white" to android.graphics.Color.WHITE,
        "red" to android.graphics.Color.RED,
        "green" to android.graphics.Color.GREEN,
        "blue" to android.graphics.Color.BLUE,
        "yellow" to android.graphics.Color.YELLOW,
        "cyan" to android.graphics.Color.CYAN,
        "magenta" to android.graphics.Color.MAGENTA,
        "gray" to android.graphics.Color.GRAY,
        "grey" to android.graphics.Color.GRAY,
        "orange" to 0xFFFFA500.toInt(),
        "purple" to 0xFF800080.toInt(),
        "pink" to 0xFFFFC0CB.toInt(),
        "transparent" to android.graphics.Color.TRANSPARENT
    )

    /**
     * Convert BorderImageSliceEdge to pixels.
     */
    private fun BorderImageSliceEdge?.toPixels(dimension: Float): Float {
        if (this == null) return 0f
        return if (isPercentage) {
            (value / 100f) * dimension
        } else {
            value
        }
    }

    /**
     * Convert BorderImageDimension to Dp.
     */
    private fun BorderImageDimension?.toDp(fallback: Dp): Dp {
        return when (this) {
            null -> fallback
            BorderImageDimension.Auto -> fallback
            is BorderImageDimension.Length -> value
            is BorderImageDimension.Percentage -> fallback * (value / 100f)
            is BorderImageDimension.Number -> fallback * value
        }
    }

    /**
     * Apply border image as a modifier.
     *
     * Note: This requires the image to be pre-loaded.
     *
     * @param config Border image configuration
     * @param bitmap Pre-loaded image bitmap
     * @param fallbackBorderWidth Fallback border width
     * @return Modifier with border image drawing
     */
    fun applyBorderImage(
        modifier: Modifier,
        config: BorderImageConfig,
        bitmap: ImageBitmap?,
        fallbackBorderWidth: Dp = 8.dp
    ): Modifier {
        if (!config.hasBorderImage || bitmap == null) {
            return modifier
        }

        val borderTop = config.widthTop.toDp(fallbackBorderWidth)
        val borderRight = config.widthRight.toDp(fallbackBorderWidth)
        val borderBottom = config.widthBottom.toDp(fallbackBorderWidth)
        val borderLeft = config.widthLeft.toDp(fallbackBorderWidth)

        return modifier.drawBehind {
            drawBorderImage(
                bitmap = bitmap,
                config = config,
                borderTop = borderTop.toPx(),
                borderRight = borderRight.toPx(),
                borderBottom = borderBottom.toPx(),
                borderLeft = borderLeft.toPx()
            )
        }
    }

    /**
     * CSS border-image property notes.
     */
    object Notes {
        const val GRADIENT_SUPPORT = """
            Gradient border images are not yet implemented.
            They would require parsing the gradient string and
            creating a shader-based image.
        """

        const val PERFORMANCE = """
            Border images are drawn every frame using drawBehind.
            For static borders, consider caching the result or
            using a custom CompositionLocal.
        """

        const val NETWORK_IMAGES = """
            Network images are loaded asynchronously.
            The border will not appear until the image loads.
            Consider adding a loading state or placeholder.
        """
    }
}
