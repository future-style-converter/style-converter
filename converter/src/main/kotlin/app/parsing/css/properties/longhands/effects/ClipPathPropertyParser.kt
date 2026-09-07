package app.parsing.css.properties.longhands.effects

import app.irmodels.IRLengthPercentage
import app.irmodels.IRPercentage
import app.irmodels.properties.effects.ClipPathProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.PositionParser
import app.parsing.css.properties.primitiveParsers.UrlParser

/**
 * Parser for `clip-path` property.
 *
 * Syntax: none | <url> | <basic-shape>
 * Basic shapes: inset(), circle(), ellipse(), polygon(), path(), rect(),
 * xywh(), shape()
 *
 * Examples:
 * - "none"
 * - "url(#clip)"
 * - "circle(50px)"
 * - "circle(50% at 50% 50%)"
 * - "ellipse(50px 100px)"
 * - "polygon(0% 0%, 100% 0%, 100% 100%, 0% 100%)"
 * - "inset(10px 20px 30px 40px)"
 *
 * Note: Simplified implementation - supports basic shapes without all advanced features
 */
object ClipPathPropertyParser : PropertyParser {
    override fun parse(value: String): ClipPathProperty? {
        val trimmed = value.trim().lowercase()

        val geometryBoxes = setOf("margin-box", "border-box", "padding-box", "content-box", "fill-box", "stroke-box", "view-box")

        return when {
            trimmed == "none" -> ClipPathProperty(ClipPathProperty.ClipPath.None())
            trimmed.startsWith("url(") -> {
                parseUrlWithOptionalGeometryBox(value, geometryBoxes)
            }
            trimmed.startsWith("circle(") -> {
                parseShapeWithOptionalGeometryBox(value, geometryBoxes)
            }
            trimmed.startsWith("ellipse(") -> {
                parseShapeWithOptionalGeometryBox(value, geometryBoxes)
            }
            trimmed.startsWith("polygon(") -> {
                parseShapeWithOptionalGeometryBox(value, geometryBoxes)
            }
            trimmed.startsWith("inset(") -> {
                parseShapeWithOptionalGeometryBox(value, geometryBoxes)
            }
            trimmed.startsWith("path(") -> {
                parseShapeWithOptionalGeometryBox(value, geometryBoxes)
            }
            trimmed.startsWith("rect(") -> {
                parseShapeWithOptionalGeometryBox(value, geometryBoxes)
            }
            trimmed.startsWith("xywh(") -> {
                parseShapeWithOptionalGeometryBox(value, geometryBoxes)
            }
            // css-shapes-2 §4 `shape()`. Added wave 37 (lane W3): without
            // this branch the whole declaration fell out of the IR as an
            // `_unmapped` Generic property and nothing was clipped.
            trimmed.startsWith("shape(") -> {
                parseShapeWithOptionalGeometryBox(value, geometryBoxes)
            }
            // Check for geometry-box alone or geometry-box + shape
            else -> {
                val tokens = value.trim().split(Regex("\\s+"), limit = 2)
                val firstToken = tokens[0].lowercase()
                if (firstToken in geometryBoxes) {
                    if (tokens.size > 1) {
                        // geometry-box + shape (e.g., "margin-box circle(50%)")
                        val shapeValue = tokens[1]
                        val shape = parseShapeValue(shapeValue)
                        if (shape != null) {
                            ClipPathProperty(ClipPathProperty.ClipPath.GeometryBoxShape(firstToken, shape))
                        } else null
                    } else {
                        // Just geometry-box
                        ClipPathProperty(ClipPathProperty.ClipPath.GeometryBox(firstToken))
                    }
                } else null
            }
        }
    }

    /**
     * Parse url() with optional trailing geometry-box.
     * Handles: "url(#clip)" or "url(#clip) border-box"
     */
    private fun parseUrlWithOptionalGeometryBox(value: String, geometryBoxes: Set<String>): ClipPathProperty? {
        val trimmed = value.trim()

        // Find the closing paren of the url function
        var parenDepth = 0
        var urlEndIndex = -1
        for (i in trimmed.indices) {
            when (trimmed[i]) {
                '(' -> parenDepth++
                ')' -> {
                    parenDepth--
                    if (parenDepth == 0) {
                        urlEndIndex = i
                        break
                    }
                }
            }
        }

        if (urlEndIndex == -1) return null

        val urlStr = trimmed.substring(0, urlEndIndex + 1)
        val remaining = trimmed.substring(urlEndIndex + 1).trim()

        val url = UrlParser.parse(urlStr) ?: return null

        return if (remaining.isNotEmpty() && remaining.lowercase() in geometryBoxes) {
            // URL with geometry-box - store as GeometryBoxShape with a Path containing the URL reference
            ClipPathProperty(ClipPathProperty.ClipPath.Url(url))
        } else if (remaining.isEmpty()) {
            ClipPathProperty(ClipPathProperty.ClipPath.Url(url))
        } else {
            null // Invalid trailing content
        }
    }

    /**
     * Parse shape with optional trailing geometry-box.
     * Handles: "circle(50%)" or "circle(50%) border-box"
     */
    private fun parseShapeWithOptionalGeometryBox(value: String, geometryBoxes: Set<String>): ClipPathProperty? {
        val trimmed = value.trim()

        // Find the closing paren of the shape function
        var parenDepth = 0
        var shapeEndIndex = -1
        for (i in trimmed.indices) {
            when (trimmed[i]) {
                '(' -> parenDepth++
                ')' -> {
                    parenDepth--
                    if (parenDepth == 0) {
                        shapeEndIndex = i
                        break
                    }
                }
            }
        }

        if (shapeEndIndex == -1) return null

        val shapeStr = trimmed.substring(0, shapeEndIndex + 1)
        val remaining = trimmed.substring(shapeEndIndex + 1).trim()

        val shape = parseShapeValue(shapeStr) ?: return null

        return if (remaining.isNotEmpty() && remaining.lowercase() in geometryBoxes) {
            ClipPathProperty(ClipPathProperty.ClipPath.GeometryBoxShape(remaining.lowercase(), shape))
        } else if (remaining.isEmpty()) {
            ClipPathProperty(ClipPathProperty.ClipPath.BasicShape(shape))
        } else {
            null // Invalid trailing content
        }
    }

    private fun parseShapeValue(value: String): ClipPathProperty.Shape? {
        val lower = value.lowercase()
        return when {
            lower.startsWith("circle(") -> parseCircle(value)
            lower.startsWith("ellipse(") -> parseEllipse(value)
            lower.startsWith("polygon(") -> parsePolygon(value)
            lower.startsWith("inset(") -> parseInset(value)
            lower.startsWith("path(") -> parsePath(value)
            lower.startsWith("rect(") -> parseRect(value)
            lower.startsWith("xywh(") -> parseXywh(value)
            // `shape(` must be tested AFTER the specific functions above
            // so it cannot shadow them; no other basic shape starts with
            // those five characters, so ordering is a readability point
            // rather than a correctness one.
            lower.startsWith("shape(") -> parseShapeFunction(value)
            else -> null
        }
    }

    /**
     * Parse the css-shapes-2 §4 `shape()` function as a VERBATIM string.
     *
     * `shape()` is a segment list — `shape(evenodd from 10px 10px, hline
     * by 80px, vline by 80%, close)` — whose coordinates mix absolute
     * lengths, percentages of the reference box, `<position>` keywords
     * and relative (`by`) offsets. None of the relative forms can be
     * pre-computed by a CSS *reader* that never sees a box, so the
     * honest normalisation is no normalisation: keep the text and let
     * each runtime decide (CLAUDE.md — `null`/unresolved means
     * runtime-dependent). This is exactly how `path()` already treats
     * its SVG `d` string one function above.
     *
     * The only transformation applied is whitespace collapsing: WPT
     * sources wrap these declarations over several lines and the raw
     * newlines survive extraction into the fixture, so runs of
     * whitespace fold to one space to keep the wire (and any CSS the web
     * runtime re-emits) on a single line. Whitespace is not significant
     * anywhere in the `shape()` grammar, so this is lossless.
     */
    private fun parseShapeFunction(value: String): ClipPathProperty.Shape? {
        val trimmed = value.trim()
        // Guard the shape of the call itself; `parseShapeValue` already
        // matched the prefix case-insensitively, so only the closing
        // paren is still in question.
        if (!trimmed.lowercase().startsWith("shape(") || !trimmed.endsWith(")")) return null
        val collapsed = trimmed.replace(Regex("""\s+"""), " ")
        // `shape()` with an empty argument list is not a valid basic
        // shape — reject rather than emit a clip that hides everything.
        if (collapsed.length <= "shape()".length) return null
        return ClipPathProperty.Shape.ShapeFunction(collapsed)
    }

    /**
     * Parse rect() function.
     * Format: rect(<top> <right> <bottom> <left> round <border-radius>?)
     * Each value can be a length or 'auto'.
     */
    private fun parseRect(value: String): ClipPathProperty.Shape? {
        if (!value.startsWith("rect(") || !value.endsWith(")")) return null
        val content = value.substring(5, value.length - 1).trim()

        // Split by "round" keyword if present
        val parts = content.split(Regex("\\s+round\\s+"))
        val rectPart = parts[0].trim()
        val roundPart = if (parts.size > 1) parts[1].trim() else null

        val values = rectPart.split(Regex("\\s+"))
        if (values.size < 4) return null

        // Parse each value - 'auto' becomes null
        val top = parseRectValue(values[0])
        val right = parseRectValue(values[1])
        val bottom = parseRectValue(values[2])
        val left = parseRectValue(values[3])
        val round = if (roundPart != null) LengthParser.parse(roundPart) else null

        return ClipPathProperty.Shape.Rect(top, right, bottom, left, round)
    }

    /**
     * Parse a rect() value - either 'auto' (returns null) or a length.
     */
    private fun parseRectValue(value: String): app.irmodels.IRLength? {
        return if (value.trim().lowercase() == "auto") null else LengthParser.parse(value)
    }

    /**
     * Parse xywh() function.
     * Format: xywh(<x> <y> <width> <height> round <border-radius>?)
     */
    private fun parseXywh(value: String): ClipPathProperty.Shape? {
        if (!value.startsWith("xywh(") || !value.endsWith(")")) return null
        val content = value.substring(5, value.length - 1).trim()

        // Split by "round" keyword if present
        val parts = content.split(Regex("\\s+round\\s+"))
        val xywhPart = parts[0].trim()
        val roundPart = if (parts.size > 1) parts[1].trim() else null

        val values = xywhPart.split(Regex("\\s+"))
        if (values.size < 4) return null

        val x = LengthParser.parse(values[0]) ?: return null
        val y = LengthParser.parse(values[1]) ?: return null
        val width = LengthParser.parse(values[2]) ?: return null
        val height = LengthParser.parse(values[3]) ?: return null
        val round = if (roundPart != null) LengthParser.parse(roundPart) else null

        return ClipPathProperty.Shape.Xywh(x, y, width, height, round)
    }

    /**
     * Parse circle() function.
     * Format: circle(<shape-radius>? at <position>?)
     *
     * CSS Shapes 1 §3.1 defines `<shape-radius>` as
     *   `<length-percentage> | closest-side | farthest-side`
     * — the keyword forms were silently dropped by the prior
     * `parseLengthOrPercentage`-only path. The keyword arm below routes
     * the two canonical keywords into [ClipPathProperty.ShapeRadius.Keyword]
     * so the platform extractors can emit them verbatim instead of
     * defaulting (the web extractor was hardcoding `closest-side` to mask
     * the dropped data — coincidentally correct on a square box, wrong
     * everywhere else). See swarm-003 clip-path-borderBox-1a investigation.
     */
    private fun parseCircle(value: String): ClipPathProperty.Shape? {
        val lower = value.lowercase()
        if (!lower.startsWith("circle(") || !lower.endsWith(")")) return null

        val content = value.substring(7, value.length - 1).trim()

        if (content.isEmpty()) {
            // circle() with no arguments
            return ClipPathProperty.Shape.Circle(null, null)
        }

        // Split by "at" keyword (case-insensitive)
        val parts = content.split(Regex("""(?i)\s+at\s+"""))

        val radius = if (parts[0].isNotEmpty()) {
            parseShapeRadius(parts[0].trim())
        } else {
            null
        }

        val position = if (parts.size > 1) {
            parsePosition(parts[1])
        } else {
            null
        }

        return ClipPathProperty.Shape.Circle(radius, position)
    }

    /**
     * Parse ellipse() function.
     * Format: ellipse(<shape-radius> <shape-radius>? at <position>?)
     *
     * Each axis independently accepts `<length-percentage>` OR the
     * `closest-side` / `farthest-side` keywords (same grammar reference as
     * `parseCircle` above). The prior code passed both axes through
     * `LengthParser.parse` only, so `ellipse(closest-side farthest-side)`
     * would silently drop both axes to null.
     */
    private fun parseEllipse(value: String): ClipPathProperty.Shape? {
        if (!value.startsWith("ellipse(") || !value.endsWith(")")) return null

        val content = value.substring(8, value.length - 1).trim()

        if (content.isEmpty()) {
            return ClipPathProperty.Shape.Ellipse(null, null, null)
        }

        // Split by "at" keyword
        val parts = content.split(Regex("""\s+at\s+"""))

        val radii = parts[0].trim().split(Regex("""\s+"""))
        // Per-axis dispatch through parseShapeRadius so a keyword on
        // either axis is preserved as a [ShapeRadius.Keyword] rather than
        // collapsed to null. `parseShapeRadius` returns null only when the
        // token is neither a recognised keyword nor a parseable length —
        // matching the pre-fix null-on-failure behaviour for the length
        // arm exactly.
        val radiusX = if (radii.isNotEmpty()) parseShapeRadius(radii[0]) else null
        val radiusY = if (radii.size > 1) parseShapeRadius(radii[1]) else null

        val position = if (parts.size > 1) {
            parsePosition(parts[1])
        } else {
            null
        }

        return ClipPathProperty.Shape.Ellipse(radiusX, radiusY, position)
    }

    /**
     * Parse a single `<shape-radius>` token (CSS Shapes 1 §3.1).
     *
     * Grammar:
     *   `<shape-radius>` = `<length-percentage> | closest-side | farthest-side`
     *
     * Resolution order:
     *  1. Keyword (case-insensitive) → [ClipPathProperty.ShapeRadius.Keyword].
     *     Only `closest-side` and `farthest-side` are accepted — those are
     *     the only two values the spec lists, and dropping anything else
     *     into the keyword branch would let typos silently survive parse.
     *  2. Length / percentage via [parseLengthOrPercentage] →
     *     [ClipPathProperty.ShapeRadius.Length]. Preserves the pre-fix
     *     accept-set exactly (px / em / rem / % / etc).
     *  3. Unparseable → null. Caller's `Shape.Circle(radius=null, ...)`
     *     contract treats null as "radius omitted" — same semantics as the
     *     prior code path.
     */
    private fun parseShapeRadius(value: String): ClipPathProperty.ShapeRadius? {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()
        // (1) Keyword forms — exact spec match only. Lowercased on store
        // so any case-variant collapses to the canonical form.
        if (lower == ClipPathProperty.ShapeRadius.CLOSEST_SIDE ||
            lower == ClipPathProperty.ShapeRadius.FARTHEST_SIDE) {
            return ClipPathProperty.ShapeRadius.Keyword(lower)
        }
        // (2) Length / percentage form — delegate to the existing helper
        // so we get the same px/em/rem/% coverage the prior code had.
        val length = parseLengthOrPercentage(trimmed) ?: return null
        return ClipPathProperty.ShapeRadius.Length(length)
    }

    /**
     * Parse polygon() function.
     * Format: polygon(<fill-rule>?, <point>, <point>, ...)
     * Point: <x> <y> where x,y are `<length-percentage>` per CSS Shapes 2 §3.1.4
     * (https://drafts.csswg.org/css-shapes/#funcdef-basic-shape-polygon).
     *
     * Historical bug: this used `parsePercentageValue` which rejected any
     * coordinate ending in a length unit. The WPT test
     * `css-masking/clip-path/clip-path-blending-offset.html` uses
     * `polygon(0 0, 100px 0, 100px 30px, 30px 30px, 30px 100px, 0 100px)`
     * which collapsed to a single `(0%, 0%)` vertex — 5 of 6 points silently
     * dropped, producing a degenerate clip that hid the entire green box.
     * See tools/titan/investigations/swarm-002/css-masking__clip-path-
     * blending-offset.json for the full root-cause walk.
     */
    private fun parsePolygon(value: String): ClipPathProperty.Shape? {
        if (!value.startsWith("polygon(") || !value.endsWith(")")) return null

        val content = value.substring(8, value.length - 1).trim()
        val pointStrings = content.split(",")

        val points = pointStrings.mapNotNull { pointStr ->
            val coords = pointStr.trim().split(Regex("""\s+"""))
            if (coords.size != 2) return@mapNotNull null

            // parseLengthOrPercentageValue accepts px/em/rem/vw/% / unitless
            // numerics — matching the `<length-percentage>` grammar. The
            // returned IRLengthPercentage carries the kind tag so the
            // platform extractors (web/Android/iOS) emit the correct unit.
            val x = parseLengthOrPercentageValue(coords[0]) ?: return@mapNotNull null
            val y = parseLengthOrPercentageValue(coords[1]) ?: return@mapNotNull null

            ClipPathProperty.Point(x, y)
        }

        if (points.isEmpty()) return null

        return ClipPathProperty.Shape.Polygon(points)
    }

    /**
     * Parse inset() function.
     * Format: inset(<top> <right>? <bottom>? <left>? round <border-radius>?)
     * Simplified: inset(<top> <right> <bottom> <left>)
     */
    private fun parseInset(value: String): ClipPathProperty.Shape? {
        if (!value.startsWith("inset(") || !value.endsWith(")")) return null

        val content = value.substring(6, value.length - 1).trim()

        // Split by "round" keyword if present
        val parts = content.split(Regex("""\s+round\s+"""))
        val insetPart = parts[0].trim()
        val roundPart = if (parts.size > 1) parts[1].trim() else null

        val insets = insetPart.split(Regex("""\s+"""))

        val top = LengthParser.parse(insets.getOrNull(0) ?: "0") ?: return null
        val right = LengthParser.parse(insets.getOrNull(1) ?: insets[0]) ?: return null
        val bottom = LengthParser.parse(insets.getOrNull(2) ?: insets[0]) ?: return null
        val left = LengthParser.parse(insets.getOrNull(3) ?: insets.getOrNull(1) ?: insets[0]) ?: return null

        val round = if (roundPart != null) LengthParser.parse(roundPart) else null

        return ClipPathProperty.Shape.Inset(top, right, bottom, left, round)
    }

    /**
     * Parse path() function.
     *
     * css-shapes-1 §3.1: `path( <fill-rule>? , <string> )` — the fill rule
     * (`nonzero` | `evenodd`) is an OPTIONAL first argument, comma-separated
     * from the SVG path string.
     *
     * WAVE-37 LANE W3 — WHAT THIS REPLACES. The previous body stripped
     * quotes from the ENTIRE argument list, so the two-argument form
     * `path(nonzero, 'M0,0 L100,0 L0,100 L0,0')` produced
     * `d = "nonzero, 'M0,0 L100,0 L0,100 L0,0'"` — the fill rule and both
     * inner quotes baked into the path data. The web runtime then emitted
     * `path("nonzero, 'M0,0 …'")`, which is not a valid `path()` at all, so
     * the browser dropped the declaration and the element went unclipped
     * (WPT clip-path-path-002, clip-path-path-with-zoom and the
     * path-interpolation-with-zoom animation).
     */
    private fun parsePath(value: String): ClipPathProperty.Shape? {
        val lower = value.lowercase()
        if (!lower.startsWith("path(") || !lower.endsWith(")")) return null

        val content = value.substring(5, value.length - 1).trim()

        // Split off an optional leading `<fill-rule>`. Only the first comma
        // is a separator — the path data itself is quoted and may contain
        // commas (`'M0,0 L100,0'`), so splitting on every comma would tear
        // it apart. Anything other than the two spec keywords in that slot
        // is not a fill rule, so we leave the text alone and let the quote
        // strip below decide whether it is a bare path.
        var fillRule: String? = null
        var pathPart = content
        val comma = content.indexOf(',')
        if (comma >= 0) {
            val head = content.substring(0, comma).trim().lowercase()
            if (head == "nonzero" || head == "evenodd") {
                fillRule = head
                pathPart = content.substring(comma + 1).trim()
            }
        }

        // Remove surrounding quotes if present
        val pathData = pathPart.removeSurrounding("'").removeSurrounding("\"")

        if (pathData.isEmpty()) return null

        return ClipPathProperty.Shape.Path(pathData, fillRule)
    }

    /**
     * Parse the `at <position>` clause of `circle()` / `ellipse()`.
     *
     * WAVE-37 LANE W3 — WHAT THIS REPLACES. The previous implementation
     * understood exactly three things: the literal string `center`, one
     * bare length (copied onto BOTH axes, which is not what the grammar
     * says — `at 30px` is `30px center`), and two bare lengths. Every
     * keyword form therefore fell straight through to `return null`, and
     * a null position means [ClipPathProperty.Shape.Circle] carries no
     * `at` clause at all — the shape silently re-centres. The corpus is
     * full of the dropped forms:
     *
     *   circle(50% at left bottom)               (css-shapes, 10 cells)
     *   circle(50% at right 40px bottom 40px)    (css-masking circle-00N)
     *   ellipse(40px 60px at right top)
     *   ellipse(farthest-side closest-side at top 40px left 60px)
     *
     * Position parsing now delegates to
     * [app.parsing.css.properties.primitiveParsers.PositionParser], the
     * shared css-values-4 `<position>` implementation extracted this wave
     * from the wave-36 `ObjectPositionPropertyParser` rewrite — same
     * grammar, same unordered-arm axis rules, one copy. Returning null
     * still means "not a position": the caller drops the `at` clause,
     * which is what a browser does with an invalid basic shape.
     */
    private fun parsePosition(value: String): ClipPathProperty.Position? {
        val resolved = PositionParser.parse(value) ?: return null
        return ClipPathProperty.Position(
            x = resolved.x.offset,
            y = resolved.y.offset,
            // Only emit an edge when it is NOT the default origin. The
            // parser normalises every keyword-only form to left/top (so
            // `at right top` is x=100%, y=0% with no edges and the wire
            // shape is unchanged from before this wave); an edge appears
            // only for the `[right|bottom] <length-percentage>` arm,
            // which genuinely cannot be expressed from the default
            // origin without the reference-box size.
            xEdge = if (resolved.x.edge == PositionParser.Edge.RIGHT) "right" else null,
            yEdge = if (resolved.y.edge == PositionParser.Edge.BOTTOM) "bottom" else null,
        )
    }

    /**
     * Parse length or percentage, returning IRLength.
     */
    private fun parseLengthOrPercentage(value: String): app.irmodels.IRLength? {
        val trimmed = value.trim()

        // Try percentage
        if (trimmed.endsWith("%")) {
            val numStr = trimmed.dropLast(1)
            val num = numStr.toDoubleOrNull() ?: return null
            return app.irmodels.IRLength.fromRelative(num, app.irmodels.IRLength.LengthUnit.PERCENT)
        }

        // Try length
        return LengthParser.parse(trimmed)
    }

    /**
     * Parse a `<length-percentage>` token (CSS Values 4 §5.4) and tag it
     * with its kind. Used by [parsePolygon] for polygon vertex coordinates.
     *
     * Resolution order:
     *  1. Trailing `%` → `IRLengthPercentage.Percentage`
     *  2. Unitless `0` (CSS spec exception — zero is a valid length without
     *     a unit) → `Length(IRLength.fromPx(0))`. Without this the WPT
     *     fixture's first vertex `0 0` would be misclassified as a unitless
     *     percentage and the remaining pixel vertices would land in a
     *     different unit space than the first.
     *  3. Length unit (px/em/rem/vw/etc) via [LengthParser] →
     *     `IRLengthPercentage.Length`
     *  4. Other unitless number → treat as percentage with value × 100
     *     (legacy compat; some test fixtures used 0..1 unitless points).
     *  5. Unparseable → null (caller's mapNotNull drops the whole point).
     */
    private fun parseLengthOrPercentageValue(value: String): IRLengthPercentage? {
        val trimmed = value.trim()

        // (1) Percentage: matches the legacy wire format. Keep as
        // IRPercentage so the serializer still emits a raw number and
        // existing platform extractors keep working unmodified.
        if (trimmed.endsWith("%")) {
            val numStr = trimmed.dropLast(1)
            val num = numStr.toDoubleOrNull() ?: return null
            return IRLengthPercentage.Percentage(IRPercentage(num))
        }

        // (2) Unitless zero — CSS allows `0` as a length without a unit
        // (CSS Values 4 §5.3). Treat as Length(0px) so a polygon mixing
        // `0` with `100px` lands in a consistent unit space.
        if (trimmed == "0" || trimmed == "0.0" || trimmed == "-0" || trimmed == "+0") {
            return IRLengthPercentage.Length(LengthParser.parse(trimmed) ?: return null)
        }

        // (3) Length with explicit unit (LengthParser handles px/em/rem/
        // vw/% and friends). Note LengthParser also matches trailing `%`,
        // but branch (1) already handled that and produced the canonical
        // raw-number Percentage form.
        LengthParser.parse(trimmed)?.let { return IRLengthPercentage.Length(it) }

        // (4) Last-resort: a bare unitless number. The original parser
        // treated this as a 0..1 percentage scaled to 0..100. Preserve
        // that fallback so old hand-written fixtures (if any) still parse.
        val num = trimmed.toDoubleOrNull() ?: return null
        return IRLengthPercentage.Percentage(IRPercentage(num * 100.0))
    }
}
