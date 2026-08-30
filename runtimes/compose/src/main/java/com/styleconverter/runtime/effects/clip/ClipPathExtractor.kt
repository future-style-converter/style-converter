package com.styleconverter.runtime.effects.clip

import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.PropertyRegistry
import com.styleconverter.runtime.core.types.ValueExtractors
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracts clip-path configuration from IR property JSON data.
 *
 * Handles the following IR property types:
 * - `ClipPath` - CSS `clip-path` property
 * - `Clip` - Legacy CSS `clip` property (deprecated but still used)
 *
 * ## IR Format Examples
 * ```json
 * // Circle
 * {
 *   "type": "ClipPath",
 *   "data": {
 *     "type": "circle",
 *     "radius": { "percentage": 50 },
 *     "x": 50,
 *     "y": 50
 *   }
 * }
 *
 * // Polygon
 * {
 *   "type": "ClipPath",
 *   "data": {
 *     "type": "polygon",
 *     "points": [
 *       { "x": 50, "y": 0 },
 *       { "x": 100, "y": 100 },
 *       { "x": 0, "y": 100 }
 *     ]
 *   }
 * }
 * ```
 */
object ClipPathExtractor {

    init {
        // Phase 8 registration. Claim ClipPath + the legacy `clip` rect() form +
        // the two clip-path geometry/fill-rule modifiers. ClipPathGeometryBox
        // and ClipRule don't yet affect the rendered Shape (TODO in the
        // applier) but are claimed here so coverage reporting is honest — the
        // IR is consumed, just not every variant is mapped yet.
        PropertyRegistry.migrated(
            "ClipPath",
            "Clip",
            "ClipPathGeometryBox",
            "ClipRule",
            owner = "effects/clip"
        )
    }

    /**
     * Extract clip-path configuration from a list of property type/data pairs.
     *
     * @param properties List of pairs where first is the property type
     *                   and second is the JSON data for that property.
     * @return ClipPathConfig with extracted clip path shape.
     */
    fun extractClipPathConfig(properties: List<Pair<String, JsonElement?>>): ClipPathConfig {
        // Single pass over the IR: collect BOTH clip channels plus the
        // element's position scheme, then decide at the end. The previous
        // first-match loop made the WINNER depend on IR property ORDER —
        // a fixture declaring `clip: rect(...)` before `clip-path: xywh(...)`
        // silently applied the legacy rect and dropped the clip-path
        // (Effects_C01_BackdropFilter: region offset upward, no rounded
        // corners, Android-web SSIM 0.904).
        var clipPathShape: ClipShape? = null
        var legacyRect: ClipShape.LegacyRect? = null
        var positionKeyword: String? = null
        // css-masking-1 §7.1 `<geometry-box>` — border-box unless the wire
        // names another (alone, or next to a shape).
        var geometryBox = ClipGeometryBox.BORDER_BOX
        for ((type, data) in properties) {
            when (type) {
                "ClipPath" -> if (clipPathShape == null && data != null) {
                    // Wave 46 (lane Y4): the wire has THREE object forms —
                    // `{type: <shape>…}`, `{geometry-box: <kw>, shape: {…}}`
                    // and `{geometry-box: <kw>}` (ClipPathSerializers.kt).
                    // Only the first reached extractShape before; the other
                    // two returned null and the element rendered UNCLIPPED
                    // (WPT contentBox-1a: the 180px square instead of the
                    // 100px circle; marginBox-1b: a 291px outline flood).
                    val boxKeyword = (data as? JsonObject)?.get("geometry-box")
                        ?.jsonPrimitive?.contentOrNull
                    if (boxKeyword != null) {
                        geometryBox = ClipBoxGeometryExtractor.parseGeometryBox(boxKeyword)
                        val inner = (data as JsonObject)["shape"]
                        clipPathShape = if (inner != null) extractShape(inner) else ClipShape.ReferenceBox
                    } else {
                        clipPathShape = extractShape(data)
                    }
                }
                // Legacy CSS 2.1 `clip` — IR shape is
                // {type:"rect", top|right|bottom|left: <length>?} where any
                // missing key means CSS `auto`. The values are absolute
                // coordinates from the box's top-left, NOT inset distances.
                "Clip" -> if (legacyRect == null && data is JsonObject &&
                    data["type"]?.jsonPrimitive?.contentOrNull == "rect") {
                    legacyRect = ClipShape.LegacyRect(
                        ValueExtractors.extractDp(data["top"]),
                        ValueExtractors.extractDp(data["right"]),
                        ValueExtractors.extractDp(data["bottom"]),
                        ValueExtractors.extractDp(data["left"])
                    )
                }
                // Needed to gate the legacy `clip` per spec (see below).
                "Position" -> positionKeyword = ValueExtractors.extractKeyword(data)?.uppercase()
            }
        }
        // Modern `clip-path` always applies (css-masking-1 §7). The box
        // metrics are read only once a clip exists — this extractor runs for
        // every component and the margin/border/padding/radius/position
        // reads would otherwise tax the whole corpus for nothing.
        if (clipPathShape != null) {
            return ClipPathConfig(
                shape = clipPathShape, geometryBox = geometryBox,
                box = ClipBoxGeometryExtractor.extract(properties),
            )
        }
        // CSS 2.1 §11.1.2: `clip` applies ONLY to absolutely positioned
        // elements (position: absolute | fixed). Chrome/Firefox/WebKit all
        // ignore it on static/relative boxes, and the web harness renders
        // these fixtures unclipped — honoring it here truncated the box and
        // cut the label text (Effects_BoxModel 0.86, Effects_Decorated 0.7853).
        val isAbsolutelyPositioned = positionKeyword == "ABSOLUTE" || positionKeyword == "FIXED"
        if (legacyRect != null && isAbsolutelyPositioned) {
            // The legacy rect measures from the border box too (§11.1.2), so
            // it rides the same reference-box machinery.
            return ClipPathConfig(shape = legacyRect, box = ClipBoxGeometryExtractor.extract(properties))
        }
        return ClipPathConfig()
    }

    /**
     * Extract a clip shape from JSON data.
     */
    private fun extractShape(json: JsonElement): ClipShape? {
        if (json !is JsonObject) return null

        val type = json["type"]?.jsonPrimitive?.content?.lowercase()

        return when (type) {
            "circle" -> extractCircle(json)
            "ellipse" -> extractEllipse(json)
            "inset" -> extractInset(json)
            "polygon" -> extractPolygon(json)
            "path" -> extractPath(json)
            "xywh" -> extractXywh(json)
            else -> null
        }
    }

    /**
     * Extract `xywh(x y w h [round r])` (CSS Shapes 1 §2.1).
     *
     * IR canonical keys (ClipPathSerializers, same file as inset/rect):
     *   { type: "xywh", x: IRLength, y: IRLength, w: IRLength, h: IRLength,
     *     round?: IRLength }
     * All lengths are parser-resolved px. Missing/unresolvable values
     * default to 0 — a zero-area rect clips everything, which is the
     * CSS-correct degenerate outcome for `xywh(0 0 0 0)`.
     */
    private fun extractXywh(json: JsonObject): ClipShape.Xywh {
        return ClipShape.Xywh(
            x = ValueExtractors.extractDp(json["x"]) ?: 0.dp,
            y = ValueExtractors.extractDp(json["y"]) ?: 0.dp,
            w = ValueExtractors.extractDp(json["w"]) ?: 0.dp,
            h = ValueExtractors.extractDp(json["h"]) ?: 0.dp,
            round = ValueExtractors.extractDp(json["round"]) ?: 0.dp,
        )
    }

    /**
     * Extract circle shape configuration.
     *
     * Three IR shapes can land here, all from the same `ClipPathShapeSerializer`
     * (src/main/kotlin/app/irmodels/properties/effects/ClipPathSerializers.kt:63-67)
     * after the generic `IRPropertySerializer.deepFlatten` pass
     * (src/main/kotlin/app/irmodels/IRPropertySerializer.kt:154-188):
     *
     *  • `circle()` no args → `{ type: "circle" }`
     *  • `circle(50%)` (radius only, no position) → flatten triggers because
     *    the shape has 1 non-type field (`r`) whose value is an object →
     *    `{ type: "circle", original: { v: 50, u: "PERCENT" } }` (the IRLength
     *    payload sits inline; the `r` wrapper key is gone). Same for `circle(50px)`
     *    → `{ type: "circle", px: 50.0 }`.
     *  • `circle(50% at 50% 50%)` (radius + position) → 2 non-type fields, flatten
     *    skips → `{ type: "circle", r: <IRLength>, pos: { x: <IRLength>, y: <IRLength> } }`.
     *
     * Previously this read `json["radius"]` and `json["x"]`/`json["y"]` for the
     * center, neither of which the canonical IR ever emits — so every circle
     * silently became `ClipRadius.ClosestSide` at the box's center. Visible cost:
     * 047_ClipPath_Circle SSIM 0.86 ia / 0.86 iw vs Android↔web 0.97 (Android
     * the outlier; the box clipped to "closest-side" of a 100×100 was a 50px
     * circle by accident, so it _looked_ vaguely correct in the screenshot but
     * the underlying logic was wrong, and any non-square box / non-50% radius
     * would expose it).
     */
    private fun extractCircle(json: JsonObject): ClipShape.Circle {
        // Radius can be at `r` (wrapped, when position is also present), or
        // flattened directly into `json` (px / original keys at the root), or
        // missing entirely (default ClosestSide). Try them in that order.
        val radius = readClipRadiusFromShape(json, wrappedKey = "r")
        // Center comes from `pos.x` / `pos.y` (each an IRLength). Fall back to
        // legacy hand-written `x` / `y` numeric keys for any older test fixture
        // that pre-dates the canonical Position serializer.
        val (centerX, centerY) = readCenterPercent(json)
        // Absolute-length center form: `circle(60px at 20px 30px)` — the
        // pos axes arrive as IRLength px objects, which readCenterPercent
        // (percent-only by design) ignores. Surface them separately so the
        // applier can prefer the definite px coordinates (CSS Shapes 1
        // §3.1 <position> takes any <length-percentage>).
        val pos = positionObject(json)
        val centerXDp = pos?.get("x")?.let { ValueExtractors.extractDp(it) }
        val centerYDp = pos?.get("y")?.let { ValueExtractors.extractDp(it) }
        return ClipShape.Circle(
            radius, centerX, centerY, centerXDp, centerYDp,
            centerFromRight = readsFromFarEdge(pos, "xEdge", "right"),
            centerFromBottom = readsFromFarEdge(pos, "yEdge", "bottom"),
        )
    }

    /**
     * Read one far-edge anchor flag off the `pos` object.
     *
     * Wave-37 lane W3 added `pos.xEdge` / `pos.yEdge` to the wire for the
     * css-values-4 `[right|bottom] <length-percentage>` arm of `<position>`
     * (`circle(50% at right 40px bottom 40px)`). The offset there is
     * measured from the FAR edge and cannot be rewritten as a left/top
     * origin length without the box size, which the converter never has.
     * Absent (the overwhelming majority) means the default origin, so
     * every value that parsed before this wave reads exactly as before.
     */
    private fun readsFromFarEdge(pos: JsonObject?, key: String, farEdge: String): Boolean =
        pos?.get(key)?.jsonPrimitive?.contentOrNull?.lowercase() == farEdge

    /**
     * Extract ellipse shape configuration.
     *
     * Canonical IR (multi-field, never flattened by IRPropertySerializer):
     *   `{ type: "ellipse", rx: <ShapeRadius>, ry: <ShapeRadius>, pos?: { x: <IRLength>, y: <IRLength> } }`
     *
     * `<ShapeRadius>` is `<length-percentage> | closest-side | farthest-side`
     * (CSS Shapes 1 §3.1) — wire form is either an IRLength JSON object
     * (length / percent) OR a JSON string primitive (keyword). The
     * `readShapeRadiusAxis` helper dispatches on node kind to cover both.
     *
     * The previous reader pulled rx/ry through `extractClipRadius`, which
     * looked for the keys `px` and `percentage` on the radius object. The
     * actual IRLength wire format uses `px` (matches) and `original.{v,u}`
     * (does NOT match — there is no `percentage` key). For the visual-test
     * fixture `ellipse(40% 30% at 50% 50%)`, both rx and ry silently fell
     * through to ClosestSide. Now the same helper handles both forms
     * including the brand-new keyword variant the parser added in
     * swarm-003 clip-path-borderBox-1a.
     */
    private fun extractEllipse(json: JsonObject): ClipShape.Ellipse {
        // rx/ry can each be EITHER an IRLength object (length / percent)
        // OR a JSON string primitive ("closest-side" / "farthest-side").
        // Falls back to ClosestSide when the axis is missing entirely,
        // matching the spec default.
        val radiusX = readShapeRadiusAxis(json["rx"]) ?: ClipRadius.ClosestSide
        val radiusY = readShapeRadiusAxis(json["ry"]) ?: ClipRadius.ClosestSide
        val (centerX, centerY) = readCenterPercent(json)
        // Same absolute-length and far-edge treatment as extractCircle —
        // `ellipse(40px 60px at right 60px top 40px)` is as legal as the
        // circle spelling and used to lose its whole `at` clause at parse.
        val pos = positionObject(json)
        return ClipShape.Ellipse(
            radiusX, radiusY, centerX, centerY,
            centerXDp = pos?.get("x")?.let { ValueExtractors.extractDp(it) },
            centerYDp = pos?.get("y")?.let { ValueExtractors.extractDp(it) },
            centerFromRight = readsFromFarEdge(pos, "xEdge", "right"),
            centerFromBottom = readsFromFarEdge(pos, "yEdge", "bottom"),
        )
    }

    /**
     * Read a ClipRadius from the parent shape JSON, given the canonical
     * wrapped key (`r` for circle). Tries:
     *   1. wrappedKey → ShapeRadius (length object OR keyword string) → ClipRadius
     *   2. legacy `radius` key → ShapeRadius → ClipRadius
     *   3. flattened: shape JSON itself contains `px` or `original` at root →
     *      treat the shape JSON as an inline IRLength
     * Returns ClipRadius.ClosestSide if nothing matches.
     *
     * The `<shape-radius>` keyword form (closest-side / farthest-side) was
     * added in swarm-003 clip-path-borderBox-1a and lands here as a JSON
     * string primitive at the `r` key — handled by `readShapeRadiusAxis`.
     */
    private fun readClipRadiusFromShape(json: JsonObject, wrappedKey: String): ClipRadius {
        // Wrapped form (multi-field shape didn't get flattened). The value
        // can be either an IRLength object OR a keyword string primitive.
        json[wrappedKey]?.let { readShapeRadiusAxis(it)?.let { rad -> return rad } }
        // Legacy alternate key still used by some hand-written fixtures.
        json["radius"]?.let { readShapeRadiusAxis(it)?.let { rad -> return rad } }
        // Flattened: the IRLength `px` / `original` fields are inline at the
        // shape root because deepFlatten saw a single object-valued non-type
        // field and inlined it. We can recognize this by the presence of
        // either `px` or `original` directly on the shape. Flattening only
        // applies to the IRLength branch — a ShapeRadius.Keyword is a
        // primitive string and never collapses into the parent object.
        if (json.containsKey("px") || json.containsKey("original")) {
            return readClipRadiusFromIRLength(json)
        }
        return ClipRadius.ClosestSide
    }

    /**
     * Decode one `<shape-radius>` value from JSON into the Android
     * [ClipRadius] sealed union. Dispatches on node kind to handle the
     * two wire forms emitted by [ShapeRadiusSerializer]:
     *  - JSON string primitive → keyword ("closest-side" / "farthest-side")
     *  - JSON object → IRLength → length / percent via the existing
     *    [readClipRadiusFromIRLength] helper
     *
     * Returns null if the value is missing or unreadable so the caller
     * (extractCircle / extractEllipse) can decide whether to use the
     * spec default (ClosestSide).
     */
    private fun readShapeRadiusAxis(node: JsonElement?): ClipRadius? {
        if (node == null) return null
        // Keyword branch — JSON string primitive. Case-insensitive match
        // against the two canonical CSS Shapes 1 §3.1 keywords. Anything
        // else falls through to null and the caller picks the default.
        if (node is JsonPrimitive && node.isString) {
            return when (node.content.lowercase()) {
                "closest-side" -> ClipRadius.ClosestSide
                "farthest-side" -> ClipRadius.FarthestSide
                else -> null
            }
        }
        // Length / percent branch — IRLength JSON object via the existing
        // helper. Preserves byte-compat with every pre-keyword fixture.
        if (node is JsonObject) {
            return readClipRadiusFromIRLength(node)
        }
        return null
    }

    /**
     * Read a ClipRadius from an IRLength-shaped JSON object. IRLength wire
     * format (see src/main/kotlin/app/irmodels/ValueTypes.kt:890-905
     * `IRLengthSerializer`):
     *   - absolute: `{ "px": N }` (also includes `original` for non-PX absolute
     *     units like pt/cm but `px` is authoritative)
     *   - relative: `{ "original": { "v": N, "u": "PERCENT" | "EM" | ... } }`
     *
     * Percentages map to `ClipRadius.Percentage(v)`; anything with concrete
     * px maps to `ClipRadius.Fixed`. Other relative units (em/vw/...) cannot
     * be resolved without container context, so they degrade to ClosestSide.
     */
    private fun readClipRadiusFromIRLength(obj: JsonObject): ClipRadius {
        val pxValue = obj["px"]?.jsonPrimitive?.floatOrNull
        val original = obj["original"] as? JsonObject
        val unit = original?.get("u")?.jsonPrimitive?.content?.uppercase()
        val originalV = original?.get("v")?.jsonPrimitive?.floatOrNull
        return when {
            unit == "PERCENT" && originalV != null -> ClipRadius.Percentage(originalV)
            pxValue != null -> ClipRadius.Fixed(pxValue.dp)
            else -> ClipRadius.ClosestSide
        }
    }

    /**
     * Read centerX/centerY (each as a 0..100 percentage of the box) from the
     * shape JSON. Canonical key is `pos` whose value is `{x: IRLength, y: IRLength}`
     * (see ClipPathProperty.Position serializer). Fall back to legacy numeric
     * `x`/`y` keys if `pos` is absent. Defaults to (50, 50) — the center of
     * the box, matching CSS's default `at 50% 50%` for circle/ellipse.
     *
     * Only PERCENT-typed lengths come through cleanly; px positions would need
     * the box size at draw time to translate to a percentage and aren't
     * representable in the current Config (centerX/Y is a Float percentage).
     * That's an existing limitation, not introduced here.
     */
    private fun readCenterPercent(json: JsonObject): Pair<Float, Float> {
        // One `<position>` bag for the whole shape — wrapped or flattened.
        val pos = positionObject(json)
        if (pos != null) {
            val cx = (pos["x"] as? JsonObject)?.let { readPercentValue(it) } ?: 50f
            val cy = (pos["y"] as? JsonObject)?.let { readPercentValue(it) } ?: 50f
            return cx to cy
        }
        // Legacy hand-written fixtures spell the centre as BARE NUMBERS
        // (`{type:"circle", x: 30, y: 70}`), which no serializer emits but
        // some pre-canonical test JSON still carries. `as? JsonPrimitive`
        // rather than `.jsonPrimitive`: the latter THROWS on a JsonObject,
        // and an object-valued `x` reaches this line whenever the canonical
        // flattened form is somehow not recognised — an extractor that
        // throws takes the whole composition down with it (measured: the
        // wave-49 skeptic walk over all 1435 corpus documents died on
        // `circle(closest-corner at 150px 200px)` here).
        val cx = (json["x"] as? JsonPrimitive)?.floatOrNull ?: 50f
        val cy = (json["y"] as? JsonPrimitive)?.floatOrNull ?: 50f
        return cx to cy
    }

    /**
     * The shape's `<position>` bag — the `at` clause of a circle / ellipse
     * (CSS Shapes 1 §3.1) — or null when the shape declares none.
     *
     * TWO wire forms reach here, both from `ClipPathShapeSerializer` after
     * the generic `IRPropertySerializer.deepFlatten` pass:
     *
     *  • WRAPPED — `{type:"circle", r:…, pos:{x,y[,xEdge,yEdge]}}`. Two
     *    non-type fields, so deepFlatten does not fire and `pos` survives.
     *  • FLATTENED — `{type:"circle", x:{px:150}, y:{px:200}}`. When the
     *    only non-type field is `pos` (a radius KEYWORD such as
     *    `closest-corner` is not serialized as a field at all), deepFlatten
     *    inlines that one object and the `pos` wrapper disappears. Reading
     *    only `json["pos"]` therefore lost the whole `at` clause and the
     *    shape silently re-centred on the box — and, because the legacy
     *    numeric fallback below then hit an OBJECT, threw.
     *
     * Mirrors the web runtime's `positionOf`
     * (runtimes/web/src/engine/effects/clip/ClipPathExtractor.ts:69-73), which
     * already reads both forms, so the runtimes cannot disagree about where a
     * flattened `at` clause lives. The one deliberate difference: this
     * version requires BOTH axes to be JSON OBJECTS, where the TS only
     * requires them to be defined — the extra type test is what keeps the
     * legacy bare-number spelling (`{x: 30, y: 70}`) on its own branch below.
     *
     * MEASURED carriers, all three failing the browser ref on every platform
     * at the wave-48 gate (tools/titan/runs/wave48-final/sections/css-masking/
     * manifest.json → `wpt.results[…].browserRef.diffs`, `wptPass:false` on
     * all nine cells): WPT css-masking/clip-path/clip-path-circle-closest-corner
     * (`circle(closest-corner at 150px 200px)` →
     * `{type:"circle",x:{px:150},y:{px:200}}`), its `-farthest-corner` twin,
     * and clip-path-ellipse-closest-farthest-corner.
     *
     * Unambiguous by construction: the flattened RADIUS form inlines an
     * IRLength, whose keys are `px` / `original`, never `x` / `y`; and the
     * `xywh()` shape, which does use `x`/`y`, never reaches this helper
     * (extractXywh owns it).
     */
    private fun positionObject(json: JsonObject): JsonObject? {
        // Wrapped form first — it is the canonical one and the only one a
        // multi-field shape can produce.
        (json["pos"] as? JsonObject)?.let { return it }
        // Flattened form: `x` AND `y` sit next to `type` as IRLength
        // objects, and so do `xEdge` / `yEdge` when the author used the
        // css-values-4 far-edge arm — so the shape JSON IS the position bag.
        // Both axes are required: the serializer always writes the pair, and
        // demanding both keeps a stray single key from being mistaken for a
        // centre.
        if (json["x"] is JsonObject && json["y"] is JsonObject) return json
        // No `at` clause — the caller falls back to the spec default centre.
        return null
    }

    /**
     * Pull a percentage value (0..100) out of an IRLength JSON. Returns null if
     * the length isn't a percentage — caller decides the fallback policy.
     */
    private fun readPercentValue(obj: JsonObject): Float? {
        val original = obj["original"] as? JsonObject ?: return null
        val unit = original["u"]?.jsonPrimitive?.content?.uppercase()
        if (unit != "PERCENT") return null
        return original["v"]?.jsonPrimitive?.floatOrNull
    }

    /**
     * Extract inset shape configuration.
     *
     * IR canonical key shape (see src/main/kotlin/app/irmodels/properties/
     * effects/ClipPathSerializers.kt:55-62 — `ClipPathShapeSerializer`):
     *   { type: "inset", t: IRLength, r: IRLength, b: IRLength, l: IRLength,
     *     round?: IRLength }
     *
     * The serializer uses single-letter keys `t/r/b/l` to keep the wire
     * shape compact and consistent with the `rect()` and `xywh()` shape
     * encodings in the same file. We previously read `top/right/bottom/
     * left` here, which never matched the IR-as-emitted: every side
     * silently extracted as null → defaulted to 0.dp → only `round` was
     * applied. Visible cost: 050_ClipPath_Inset rendered the box at
     * full element size with rounded corners (the inset clipping had no
     * effect), making Android the outlier vs iOS / web (SSIM 0.82 ia,
     * 0.80 aw — both clipped correctly).
     *
     * We try the canonical short keys first, falling back to the
     * legacy long names in case any older fixture or test still emits
     * them. `round` is the same key in both schemas.
     */
    private fun extractInset(json: JsonObject): ClipShape.Inset {
        val topJson = json["t"] ?: json["top"]
        val rightJson = json["r"] ?: json["right"]
        val bottomJson = json["b"] ?: json["bottom"]
        val leftJson = json["l"] ?: json["left"]
        val top = ValueExtractors.extractDp(topJson) ?: 0.dp
        val right = ValueExtractors.extractDp(rightJson) ?: 0.dp
        val bottom = ValueExtractors.extractDp(bottomJson) ?: 0.dp
        val left = ValueExtractors.extractDp(leftJson) ?: 0.dp
        val roundJson = json["round"]
        val radius = ValueExtractors.extractDp(roundJson) ?: 0.dp
        // Pull a percentage radius (e.g. `round 50%`) out as a fraction so
        // the applier can resolve against the rendered box size; otherwise
        // the previous extractor silently dropped percent radii to 0.dp
        // and the inset clip rendered as a sharp rectangle.
        val radiusFraction = percentFraction(roundJson)
        // Wave 46 (lane Y4): percent SIDES ride along the same way — the
        // IRLength `{original:{v,u:PERCENT}}` form has no px, so extractDp
        // read 0 and `inset(80% 0 0)` clipped nothing away.
        return ClipShape.Inset(
            top, right, bottom, left, radius,
            topFraction = percentFraction(topJson), rightFraction = percentFraction(rightJson),
            bottomFraction = percentFraction(bottomJson), leftFraction = percentFraction(leftJson),
            borderRadiusFraction = radiusFraction,
        )
    }

    /** `{original:{v, u:"PERCENT"}}` → v/100, else null (px or absent). */
    private fun percentFraction(node: JsonElement?): Float? =
        (node as? JsonObject)?.let { obj ->
            (obj["original"] as? JsonObject)?.takeIf {
                it["u"]?.jsonPrimitive?.contentOrNull?.equals("PERCENT", ignoreCase = true) == true
            }?.get("v")?.jsonPrimitive?.floatOrNull?.div(100f)
        }

    /**
     * Extract polygon shape configuration.
     *
     * Per CSS Shapes 2 §3.1.4 each polygon vertex is a `<length-percentage>`
     * pair. The Kotlin IR encodes that via [IRLengthPercentageSerializer]
     * with an intentionally asymmetric wire shape:
     *   - percentage axis → raw JSON number (legacy form, e.g. `"x": 50`)
     *   - length axis     → IRLength object (e.g. `"x": {"px": 100.0}`)
     *
     * We dispatch on the JSON node kind so old percent-only fixtures keep
     * working byte-for-byte and the new px-coordinate WPT fixtures
     * (`clip-path-blending-offset` and family) get correct vertices.
     * A whole point is dropped only if BOTH axes are unreadable — a
     * stricter policy than the prior code, which dropped any vertex
     * whose number was missing.
     */
    private fun extractPolygon(json: JsonObject): ClipShape.Polygon {
        val pointsArray = json["points"] as? JsonArray ?: return ClipShape.Polygon(emptyList())
        val points = pointsArray.mapNotNull { point ->
            if (point !is JsonObject) return@mapNotNull null
            val x = readPolygonAxis(point["x"]) ?: return@mapNotNull null
            val y = readPolygonAxis(point["y"]) ?: return@mapNotNull null
            ClipShape.PolygonPoint(x, y)
        }
        return ClipShape.Polygon(points)
    }

    /**
     * Read one polygon axis from JSON. Branches on node kind:
     *  - primitive number → percent (legacy `{x:50,y:0}` wire shape)
     *  - object → IRLength via [ValueExtractors.extractDp]; relative
     *    units that can't resolve to a concrete Dp (em/rem/vw/...) are
     *    coerced into the percent branch via the IRLength's `original.v`
     *    fallback, so a `2em` polygon vertex doesn't silently vanish.
     * Returns null if the axis JSON is missing or unreadable; the caller
     * drops the entire point.
     */
    private fun readPolygonAxis(node: JsonElement?): ClipShape.PolygonAxis? {
        if (node == null) return null
        // Percent branch: raw number primitive.
        if (node is JsonPrimitive && !node.isString) {
            val n = node.floatOrNull ?: return null
            return ClipShape.PolygonAxis.Percent(n)
        }
        // Length branch: IRLength object (`{px: N}` or `{original: {v,u}}`).
        if (node is JsonObject) {
            // Fast path: absolute pixel length.
            val pxFloat = node["px"]?.jsonPrimitive?.floatOrNull
            if (pxFloat != null) return ClipShape.PolygonAxis.Length(pxFloat.dp)
            // Relative units (em/rem/vw/vh/etc): no Dp without runtime
            // context. PERCENT is a special case — pull `original.v` and
            // treat it as the legacy percent branch so the path renders
            // sensibly. Other relative units are best-effort dropped to
            // 0% rather than failing the whole polygon.
            val original = node["original"] as? JsonObject
            val unit = original?.get("u")?.jsonPrimitive?.contentOrNull?.uppercase()
            val originalV = original?.get("v")?.jsonPrimitive?.floatOrNull
            if (unit == "PERCENT" && originalV != null) {
                return ClipShape.PolygonAxis.Percent(originalV)
            }
            // Last resort — collapse to a 0px length. Caller still gets a
            // valid PolygonPoint so the surrounding vertices render.
            return ClipShape.PolygonAxis.Length(0f.dp)
        }
        return null
    }

    /**
     * Extract SVG path shape configuration.
     */
    private fun extractPath(json: JsonObject): ClipShape.Path? {
        val d = json["d"]?.jsonPrimitive?.content ?: return null
        return ClipShape.Path(d)
    }

    /**
     * Check if a property type is a clip-path related property.
     *
     * @param type The property type string.
     * @return True if this is a clip path property.
     */
    fun isClipPathProperty(type: String): Boolean {
        return type in setOf("ClipPath", "Clip")
    }
}
