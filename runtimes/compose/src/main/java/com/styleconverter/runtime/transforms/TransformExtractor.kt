package com.styleconverter.runtime.transforms

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.PropertyRegistry
import com.styleconverter.runtime.core.types.ValueExtractors
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracts transform-related configuration from IR properties.
 *
 * ## Supported Properties
 * - Transform: CSS transform with multiple functions
 * - TransformOrigin: Pivot point for transformations
 * - Rotate: Standalone rotation
 * - Scale: Standalone scale
 * - Translate: Standalone translation
 *
 * ## IR Formats
 *
 * ### Transform
 * ```json
 * {
 *   "type": "functions",
 *   "list": [
 *     { "fn": "translate", "x": { "px": 50 }, "y": { "px": 100 } },
 *     { "fn": "rotate", "angle": { "degrees": 45 } }
 *   ]
 * }
 * ```
 *
 * ### TransformOrigin
 * ```json
 * { "x": { "type": "percentage", "value": 50 }, "y": { "type": "percentage", "value": 50 } }
 * // or keyword: "center", "top left", etc.
 * ```
 *
 * ### Rotate
 * ```json
 * { "degrees": 45.0 }
 * // or: 45.0
 * ```
 *
 * ### Scale
 * ```json
 * { "x": 1.5, "y": 1.5 }
 * // or: 1.5
 * ```
 *
 * ### Translate
 * ```json
 * { "x": { "px": 50 }, "y": { "px": 100 } }
 * ```
 */
object TransformExtractor {

    init {
        // Phase 8 registration. Claim the 2D transform longhands + origin so the
        // legacy dispatch switch (and any future one) defers to this extractor.
        // TransformBox is CLAIMED, NOT CONSUMED — say it plainly (retro P2e,
        // finding A6#15: this line pointed at "the TODO in TransformApplier",
        // and TransformApplier.kt / TransformConfig.kt contain no TransformBox
        // handling and no such TODO — a pointer to nothing). Nothing in
        // runtimes/compose/src/main reads the type: it is registered only so
        // PropertyRegistry.allRegistered() reports the transform family as
        // owned, and a `transform-box` declaration therefore always renders as
        // the property's initial value (css-transforms-1 §6 "Transform
        // reference box: the transform-box property" — `Initial: view-box`,
        // which on a non-SVG element is the border box) whatever the author
        // wrote. The iOS twin DOES decode it
        // (TransformsExtractor.applyBox → TransformsAggregate.box), so this is
        // a live Compose-side gap, not a shared no-op. It has no corpus
        // carrier to expose it: MEASURED zero `TransformBox` properties across
        // all 1435 wave49-final per-test IR documents — which is why the gap
        // costs no gate cell today and why closing it needs a fixture first.
        PropertyRegistry.migrated(
            "Transform",
            "TransformOrigin",
            "TransformBox",
            "Rotate",
            "Scale",
            "Translate",
            owner = "transforms"
        )
    }

    /**
     * Extract a complete TransformConfig from a list of property type/data pairs.
     *
     * @param properties List of (propertyType, data) pairs from IR
     * @return TransformConfig with all extracted values
     */
    fun extractTransformConfig(properties: List<Pair<String, JsonElement?>>): TransformConfig {
        var config = TransformConfig()

        properties.forEach { (type, data) ->
            config = when (type) {
                "Transform" -> config.copy(functions = extractTransformFunctions(data))
                "TransformOrigin" -> {
                    val origin = extractTransformOrigin(data)
                    val originDp = extractTransformOriginDp(data)
                    config.copy(
                        originX = origin?.first ?: 0.5f,
                        originY = origin?.second ?: 0.5f,
                        originXDp = originDp?.first,
                        originYDp = originDp?.second,
                    )
                }
                "Rotate" -> {
                    // RotatePropertyParser union: {"type":"angle","deg":25}
                    // (plain Z rotation, read via extractDegrees), {"type":
                    // "none"} (identity → null), or {"type":"axis-angle",
                    // "x":0,"y":0,"z":1,"angle":{"deg":45}} — css-transforms-2
                    // §5's `rotate: <axis> <angle>` form, routed to the axis
                    // whose component is 1 so `rotate: x 45deg` lands on
                    // rotateX instead of being silently dropped.
                    val axisAngle = (data as? JsonObject)
                        ?.takeIf { it["type"]?.jsonPrimitive?.contentOrNull == "axis-angle" }
                    if (axisAngle != null) {
                        val deg = ValueExtractors.extractDegrees(axisAngle["angle"]) ?: 0f
                        val ax = axisAngle["x"]?.jsonPrimitive?.floatOrNull ?: 0f
                        val ay = axisAngle["y"]?.jsonPrimitive?.floatOrNull ?: 0f
                        val az = axisAngle["z"]?.jsonPrimitive?.floatOrNull ?: 0f
                        // retro R1 (A11#6/#11): classify the axis EXACTLY.
                        // This branch used to read only x/y — `rotate: 1 1 0
                        // 45deg` fell to the planar `else` (a Z rotation:
                        // pairs-06 035/040 at 0.71–0.81) and `rotate: 1 0 1 …`
                        // to rotateX. Rotate3dAxis keeps unit axes on their
                        // legacy fields and hands a genuine diagonal to the
                        // 4x4 route via `rotate3d`.
                        when (val fn = Rotate3dAxis.classify(ax, ay, az, deg)) {
                            is TransformFunction.RotateX -> config.copy(rotateX = fn.degrees)
                            is TransformFunction.RotateY -> config.copy(rotateY = fn.degrees)
                            is TransformFunction.RotateZ -> config.copy(rotate = fn.degrees)
                            is TransformFunction.Rotate3d -> config.copy(rotate3d = fn)
                            // css-transforms-2 §12.2: a zero vector means the
                            // rotation is not applied — identity, no field set.
                            else -> config
                        }
                    } else {
                        config.copy(rotate = ValueExtractors.extractDegrees(data))
                    }
                }
                "RotateX" -> config.copy(rotateX = ValueExtractors.extractDegrees(data))
                "RotateY" -> config.copy(rotateY = ValueExtractors.extractDegrees(data))
                "Scale" -> {
                    val scaleData = extractScaleData(data)
                    config.copy(
                        scale = scaleData.uniform,
                        scaleX = scaleData.x,
                        scaleY = scaleData.y
                    )
                }
                "ScaleX" -> {
                    val x = extractFloat(data)
                    config.copy(scaleX = x)
                }
                "ScaleY" -> {
                    val y = extractFloat(data)
                    config.copy(scaleY = y)
                }
                "ScaleZ" -> {
                    val z = extractFloat(data)
                    config.copy(scaleZ = z)
                }
                "Translate" -> {
                    val translateData = extractTranslateData(data)
                    config.copy(
                        translateX = translateData.x,
                        translateY = translateData.y,
                        translateZ = translateData.z,
                        translateXFraction = translateData.xFraction,
                        translateYFraction = translateData.yFraction,
                    )
                }
                "TranslateX" -> {
                    val x = data?.let { ValueExtractors.extractDp(it) }
                    config.copy(translateX = x)
                }
                "TranslateY" -> {
                    val y = data?.let { ValueExtractors.extractDp(it) }
                    config.copy(translateY = y)
                }
                "TranslateZ" -> {
                    val z = data?.let { ValueExtractors.extractDp(it) }
                    config.copy(translateZ = z)
                }
                "Perspective" -> {
                    val perspective = data?.let { ValueExtractors.extractDp(it) }
                    config.copy(perspective = perspective)
                }
                "SkewX" -> {
                    val skewX = ValueExtractors.extractDegrees(data)
                    config.copy(skewX = skewX)
                }
                "SkewY" -> {
                    val skewY = ValueExtractors.extractDegrees(data)
                    config.copy(skewY = skewY)
                }
                else -> config
            }
        }

        return config
    }

    /**
     * Extract a float value from JSON.
     */
    private fun extractFloat(data: JsonElement?): Float? {
        return when (data) {
            is JsonPrimitive -> data.floatOrNull
            is JsonObject -> data["value"]?.jsonPrimitive?.floatOrNull
            else -> null
        }
    }

    /**
     * Extract transform origin as normalized values (0-1).
     *
     * Handles formats:
     * - Object with x/y percentages: { "x": 50, "y": 50 }
     * - Keywords: "center", "top left", etc.
     *
     * @param data JSON element containing transform-origin data
     * @return Pair of (x, y) as fractions (0-1), or null if not extractable
     */
    /**
     * Pull a length-typed transform-origin axis out as a Dp. Returns null
     * for keyword/percentage origins (those flow through extractTransformOrigin
     * as fractions). Both axes returned as a pair so the caller can copy
     * either or both into the config.
     */
    fun extractTransformOriginDp(data: JsonElement?): Pair<Dp?, Dp?>? {
        if (data !is JsonObject) return null
        // retro R5 (A11#14 twin agreement): a reordered keyword beside a <length>
        // (`top 10px`) is outside css-transforms-1 §4's grammar (§5 in the TR
        // numbering) — Chromium ignores the declaration — so the <length> must
        // not ride as a dp anchor: TransformOriginKeywords.resolve already returns
        // the initial 50% 50% for the fractions and a positional dp read here
        // would still pivot the box at y = 10px. The iOS resolver does the same.
        if (TransformOriginKeywords.dropsDeclaration(data["x"], data["y"])) return null
        fun axis(d: JsonElement?): Dp? {
            if (d !is JsonObject) return null
            val type = d["type"]?.jsonPrimitive?.contentOrNull?.lowercase()
            if (type != "length") return null
            // Length payload may be flat ({"px": 10}) or nested ({"length": {"px": 10}})
            // depending on which IR shape the parser emits. Tolerate both.
            val flatPx = d["px"]?.jsonPrimitive?.floatOrNull
            val nestedPx = (d["length"] as? JsonObject)?.get("px")?.jsonPrimitive?.floatOrNull
            return (flatPx ?: nestedPx)?.dp
        }
        val x = axis(data["x"])
        val y = axis(data["y"])
        return if (x == null && y == null) null else x to y
    }

    fun extractTransformOrigin(data: JsonElement?): Pair<Float, Float>? {
        if (data == null) return null

        return when (data) {
            is JsonObject -> {
                // retro R1 (A11#14): keywords bind to their OWN axis
                // (css-transforms-1 §4's <position>-style grammar) while the
                // converter stores the two tokens positionally, so `top
                // right` used to read x = TOP → 0, y = RIGHT → 1 — the
                // bottom-LEFT corner (both natives measured at the un-swapped
                // centroid, Chromium at the swapped one; numbers in
                // TransformOriginKeywords). Percentages/lengths keep
                // extractOriginComponent's readings through the callback.
                TransformOriginKeywords.resolve(data["x"], data["y"], ::extractOriginComponent)
            }
            is JsonPrimitive -> {
                // Handle keyword values
                when (data.contentOrNull?.lowercase()) {
                    "center" -> Pair(0.5f, 0.5f)
                    "top" -> Pair(0.5f, 0f)
                    "bottom" -> Pair(0.5f, 1f)
                    "left" -> Pair(0f, 0.5f)
                    "right" -> Pair(1f, 0.5f)
                    "top left" -> Pair(0f, 0f)
                    "top right" -> Pair(1f, 0f)
                    "bottom left" -> Pair(0f, 1f)
                    "bottom right" -> Pair(1f, 1f)
                    else -> null
                }
            }
            else -> null
        }
    }

    /**
     * Extract a single origin component (x or y) from various formats.
     */
    private fun extractOriginComponent(data: JsonElement?): Float? {
        if (data == null) return null

        return when (data) {
            is JsonPrimitive -> {
                // Direct percentage value
                data.floatOrNull?.let { it / 100f }
                    ?: when (data.contentOrNull?.uppercase()) {
                        "LEFT", "TOP" -> 0f
                        "CENTER" -> 0.5f
                        "RIGHT", "BOTTOM" -> 1f
                        else -> null
                    }
            }
            is JsonObject -> {
                // Actual IR shapes emitted by TransformOriginProperty (see
                // src/main/kotlin/app/irmodels/properties/transforms/
                // TransformOriginProperty.kt + ValueTypes.kt IRPercentageSerializer):
                //   percentage  → { "type": "percentage", "percentage": 50.0 }
                //   length      → { "type": "length",     "length": { "px": 20 } }
                //   keyword     → { "type": "keyword",    "value": "CENTER" }
                // The extractor previously read "value" for every branch,
                // which silently collapsed percent origins to nil (center).
                // Include "value" as a legacy fallback for any stream that
                // still emits the old shape.
                val type = data["type"]?.jsonPrimitive?.contentOrNull
                val percentPayload = data["percentage"] ?: data["value"]
                val lengthPayload = data["length"]

                when (type?.lowercase()) {
                    "percentage" -> percentPayload?.jsonPrimitive?.floatOrNull?.let { it / 100f }
                    // For `length` we'd need the container size to convert px
                    // into a 0-1 origin offset and we don't have it at this
                    // layer. Return null → caller falls back to the default
                    // origin (center). Preferable to guessing a bogus offset
                    // and quietly mis-rendering the transform. The previous
                    // version crashed here because extractDp returns Dp?
                    // and we were unsafely dereferencing `.value`.
                    "length" -> null
                    "keyword" -> when (data["value"]?.jsonPrimitive?.contentOrNull?.uppercase()) {
                        "LEFT", "TOP" -> 0f
                        "CENTER" -> 0.5f
                        "RIGHT", "BOTTOM" -> 1f
                        else -> null
                    }
                    else -> null
                }
            }
            else -> null
        }
    }

    /**
     * Extract transform functions from IR data.
     *
     * IR format:
     * ```json
     * {
     *   "type": "functions",
     *   "list": [
     *     { "fn": "translate", "x": { "px": 50 }, "y": { "px": 100 } },
     *     { "fn": "rotate", "angle": { "degrees": 45 } }
     *   ]
     * }
     * ```
     *
     * @param data JSON element containing transform data
     * @return List of TransformFunction objects
     */
    fun extractTransformFunctions(data: JsonElement?): List<TransformFunction> {
        val obj = (data as? JsonObject) ?: return emptyList()

        // Check for expression type (calc, var) - cannot be parsed
        val type = obj["type"]?.jsonPrimitive?.contentOrNull
        if (type == "expression") {
            return emptyList()
        }

        // Check for "none" keyword
        if (type == "none") {
            return listOf(TransformFunction.None)
        }

        // CSS-WIDE KEYWORDS, wired as {"type":"keyword","keyword":"…"} by
        // the converter's TransformPropertyParser. Handled explicitly so
        // the shape is REFUSED with a reason instead of falling out of the
        // `obj["list"]` lookup below as an empty list (a silent drop).
        //
        // `initial` / `unset` / `revert` / `revert-layer`: `transform` is
        // not an inherited property, so css-cascade-4 §7.3 makes `unset`
        // act as `initial`, and css-transforms-1 §3's initial value is
        // `none`. An empty list renders exactly that, so these need no
        // further work — returning empty here is the right answer, not a
        // fallthrough.
        //
        // `inherit` (css-cascade-4 §7.3.2 — the parent's computed value)
        // CANNOT be resolved from here: a Modifier is parents-ignorant and
        // Compose's provider would have to live in
        // core/renderer/ComponentRenderer.kt. The iOS twin resolves it
        // through an ambient channel the transforms folder owns outright
        // (StyleEngine/transforms/TransformInheritance.swift, wave 49);
        // Compose has no equivalent without that renderer seam. Sole
        // corpus carrier: css-transforms/css-transform-inherit-scale.
        if (type == "keyword") {
            val keyword = obj["keyword"]?.jsonPrimitive?.contentOrNull?.lowercase()
            if (keyword == "inherit") {
                com.styleconverter.runtime.PropertyTracker.markUnhandled("Transform")
            }
            return emptyList()
        }

        // Extract list of functions
        val listData = obj["list"] as? JsonArray ?: return emptyList()

        return listData.mapNotNull { element ->
            val fnObj = (element as? JsonObject) ?: return@mapNotNull null
            val fn = fnObj["fn"]?.jsonPrimitive?.contentOrNull

            when (fn?.lowercase()) {
                "translate" -> extractTranslateFunction(fnObj)
                "translatex" -> extractTranslateXFunction(fnObj)
                "translatey" -> extractTranslateYFunction(fnObj)
                "translatez" -> extractTranslateZFunction(fnObj)
                "translate3d" -> extractTranslate3dFunction(fnObj)
                "rotate" -> extractRotateFunction(fnObj)
                "rotatex" -> extractRotateXFunction(fnObj)
                "rotatey" -> extractRotateYFunction(fnObj)
                "rotatez" -> extractRotateZFunction(fnObj)
                "rotate3d" -> extractRotate3dFunction(fnObj)
                "scale" -> extractScaleFunction(fnObj)
                "scalex" -> extractScaleXFunction(fnObj)
                "scaley" -> extractScaleYFunction(fnObj)
                "scalez" -> extractScaleZFunction(fnObj)
                "scale3d" -> extractScale3dFunction(fnObj)
                "skew" -> extractSkewFunction(fnObj)
                "skewx" -> extractSkewXFunction(fnObj)
                "skewy" -> extractSkewYFunction(fnObj)
                "perspective" -> extractPerspectiveFunction(fnObj)
                "matrix" -> extractMatrixFunction(fnObj)
                "matrix3d" -> extractMatrix3dFunction(fnObj)
                "none" -> TransformFunction.None
                else -> null
            }
        }
    }

    // ==================== TRANSLATE FUNCTIONS ====================

    private fun extractTranslateFunction(obj: JsonObject): TransformFunction.Translate {
        val x = obj["x"]?.let { ValueExtractors.extractDp(it) } ?: 0.dp
        val y = obj["y"]?.let { ValueExtractors.extractDp(it) } ?: 0.dp
        return TransformFunction.Translate(x, y, 0.dp)
    }

    private fun extractTranslateXFunction(obj: JsonObject): TransformFunction.TranslateX {
        val x = obj["x"]?.let { ValueExtractors.extractDp(it) } ?: 0.dp
        return TransformFunction.TranslateX(x)
    }

    private fun extractTranslateYFunction(obj: JsonObject): TransformFunction.TranslateY {
        val y = obj["y"]?.let { ValueExtractors.extractDp(it) } ?: 0.dp
        return TransformFunction.TranslateY(y)
    }

    private fun extractTranslateZFunction(obj: JsonObject): TransformFunction.TranslateZ {
        val z = obj["z"]?.let { ValueExtractors.extractDp(it) } ?: 0.dp
        return TransformFunction.TranslateZ(z)
    }

    private fun extractTranslate3dFunction(obj: JsonObject): TransformFunction.Translate {
        val x = obj["x"]?.let { ValueExtractors.extractDp(it) } ?: 0.dp
        val y = obj["y"]?.let { ValueExtractors.extractDp(it) } ?: 0.dp
        val z = obj["z"]?.let { ValueExtractors.extractDp(it) } ?: 0.dp
        return TransformFunction.Translate(x, y, z)
    }

    // ==================== ROTATE FUNCTIONS ====================
    //
    // IR shape per TransformFunctionSerializer (src/main/kotlin/app/irmodels/
    // properties/transforms/TransformFunctionSerializer.kt, serialize() lines
    // 62-83): the rotate* functions emit their angle under the key "a", NOT
    // "angle". An IRAngle serializes as {"deg": N, ...} so we dig through "a"
    // then "deg" to get the degrees value. extractDegrees() tolerates both.

    private fun extractRotateFunction(obj: JsonObject): TransformFunction.Rotate {
        val angle = (obj["a"] ?: obj["angle"])?.let { ValueExtractors.extractDegrees(it) } ?: 0f
        return TransformFunction.Rotate(angle)
    }

    private fun extractRotateXFunction(obj: JsonObject): TransformFunction.RotateX {
        val angle = (obj["a"] ?: obj["angle"])?.let { ValueExtractors.extractDegrees(it) } ?: 0f
        return TransformFunction.RotateX(angle)
    }

    private fun extractRotateYFunction(obj: JsonObject): TransformFunction.RotateY {
        val angle = (obj["a"] ?: obj["angle"])?.let { ValueExtractors.extractDegrees(it) } ?: 0f
        return TransformFunction.RotateY(angle)
    }

    private fun extractRotateZFunction(obj: JsonObject): TransformFunction.RotateZ {
        val angle = (obj["a"] ?: obj["angle"])?.let { ValueExtractors.extractDegrees(it) } ?: 0f
        return TransformFunction.RotateZ(angle)
    }

    private fun extractRotate3dFunction(obj: JsonObject): TransformFunction? {
        // rotate3d(x, y, z, angle) — css-transforms-2 §12.2. retro R1
        // (A11#6): the old "largest component" heuristic drew
        // rotate3d(1,1,0,45deg) as rotateY(45deg) and rotate3d(0,0,0,θ) as
        // rotateZ(θ); Rotate3dAxis keeps unit axes exact (sign included),
        // returns null for the §12.2 zero vector (identity — mapNotNull drops
        // it), and preserves a diagonal axis for the 4x4 route.
        val angle = (obj["a"] ?: obj["angle"])?.let { ValueExtractors.extractDegrees(it) } ?: return null
        val x = obj["x"]?.jsonPrimitive?.floatOrNull ?: 0f
        val y = obj["y"]?.jsonPrimitive?.floatOrNull ?: 0f
        val z = obj["z"]?.jsonPrimitive?.floatOrNull ?: 0f
        return Rotate3dAxis.classify(x, y, z, angle)
    }

    // ==================== SCALE FUNCTIONS ====================

    private fun extractScaleFunction(obj: JsonObject): TransformFunction.Scale {
        val x = obj["x"]?.jsonPrimitive?.floatOrNull ?: 1f
        val y = obj["y"]?.jsonPrimitive?.floatOrNull ?: x // Default y to x for uniform scale
        val z = obj["z"]?.jsonPrimitive?.floatOrNull ?: 1f
        return TransformFunction.Scale(x, y, z)
    }

    private fun extractScaleXFunction(obj: JsonObject): TransformFunction.ScaleX {
        val x = obj["x"]?.jsonPrimitive?.floatOrNull ?: 1f
        return TransformFunction.ScaleX(x)
    }

    private fun extractScaleYFunction(obj: JsonObject): TransformFunction.ScaleY {
        val y = obj["y"]?.jsonPrimitive?.floatOrNull ?: 1f
        return TransformFunction.ScaleY(y)
    }

    private fun extractScaleZFunction(obj: JsonObject): TransformFunction.ScaleZ {
        val z = obj["z"]?.jsonPrimitive?.floatOrNull ?: 1f
        return TransformFunction.ScaleZ(z)
    }

    private fun extractScale3dFunction(obj: JsonObject): TransformFunction.Scale {
        val x = obj["x"]?.jsonPrimitive?.floatOrNull ?: 1f
        val y = obj["y"]?.jsonPrimitive?.floatOrNull ?: 1f
        val z = obj["z"]?.jsonPrimitive?.floatOrNull ?: 1f
        return TransformFunction.Scale(x, y, z)
    }

    // ==================== SKEW FUNCTIONS ====================

    private fun extractSkewFunction(obj: JsonObject): TransformFunction.Skew {
        val x = obj["x"]?.let { ValueExtractors.extractDegrees(it) } ?: 0f
        val y = obj["y"]?.let { ValueExtractors.extractDegrees(it) } ?: 0f
        return TransformFunction.Skew(x, y)
    }

    private fun extractSkewXFunction(obj: JsonObject): TransformFunction.SkewX {
        val x = obj["x"]?.let { ValueExtractors.extractDegrees(it) }
            ?: obj["angle"]?.let { ValueExtractors.extractDegrees(it) }
            ?: 0f
        return TransformFunction.SkewX(x)
    }

    private fun extractSkewYFunction(obj: JsonObject): TransformFunction.SkewY {
        val y = obj["y"]?.let { ValueExtractors.extractDegrees(it) }
            ?: obj["angle"]?.let { ValueExtractors.extractDegrees(it) }
            ?: 0f
        return TransformFunction.SkewY(y)
    }

    // ==================== PERSPECTIVE FUNCTION ====================
    //
    // IR shape per TransformFunctionSerializer line 99-100: the distance is
    // emitted under key "l" (for IRLength). Legacy "d"/"distance" fallbacks
    // preserved in case some older IR stream still emits them.
    private fun extractPerspectiveFunction(obj: JsonObject): TransformFunction.Perspective {
        val d = obj["l"]?.let { ValueExtractors.extractDp(it) }
            ?: obj["d"]?.let { ValueExtractors.extractDp(it) }
            ?: obj["distance"]?.let { ValueExtractors.extractDp(it) }
            ?: 1000.dp
        return TransformFunction.Perspective(d)
    }

    // ==================== MATRIX FUNCTIONS ====================
    //
    // IR shape per TransformFunctionSerializer lines 102-129: matrix and
    // matrix3d emit their coefficients as INDIVIDUAL keys (a/b/c/d/e/f for
    // matrix; a1/b1/c1/d1 through a4/b4/c4/d4 for matrix3d), NOT as a
    // "values": [...] array. The IRNumber serializer writes bare JSON
    // numbers so floatOrNull is the right extractor.
    private fun extractMatrixFunction(obj: JsonObject): TransformFunction.Matrix? {
        // Preferred shape — per-coefficient keys.
        val a = obj["a"]?.jsonPrimitive?.floatOrNull
        val b = obj["b"]?.jsonPrimitive?.floatOrNull
        val c = obj["c"]?.jsonPrimitive?.floatOrNull
        val d = obj["d"]?.jsonPrimitive?.floatOrNull
        val e = obj["e"]?.jsonPrimitive?.floatOrNull
        val f = obj["f"]?.jsonPrimitive?.floatOrNull
        if (a != null && b != null && c != null && d != null && e != null && f != null) {
            return TransformFunction.Matrix(listOf(a, b, c, d, e, f))
        }
        // Legacy/fallback shape — {"values": [a,b,c,d,e,f]}.
        val values = obj["values"] as? JsonArray ?: return null
        if (values.size < 6) return null
        val floatValues = values.mapNotNull { it.jsonPrimitive.floatOrNull }
        if (floatValues.size < 6) return null
        return TransformFunction.Matrix(floatValues.take(6))
    }

    private fun extractMatrix3dFunction(obj: JsonObject): TransformFunction.Matrix3d? {
        // Preferred shape — 16 individual keys in column-major order.
        val keys = listOf(
            "a1", "b1", "c1", "d1",
            "a2", "b2", "c2", "d2",
            "a3", "b3", "c3", "d3",
            "a4", "b4", "c4", "d4",
        )
        val named = keys.mapNotNull { obj[it]?.jsonPrimitive?.floatOrNull }
        if (named.size == 16) {
            return TransformFunction.Matrix3d(named)
        }
        // Legacy/fallback shape — {"values": [16 floats]}.
        val values = obj["values"] as? JsonArray ?: return null
        if (values.size < 16) return null
        val floatValues = values.mapNotNull { it.jsonPrimitive.floatOrNull }
        if (floatValues.size < 16) return null
        return TransformFunction.Matrix3d(floatValues.take(16))
    }

    // ==================== STANDALONE PROPERTY EXTRACTORS ====================

    /**
     * Helper class for scale extraction results.
     */
    private data class ScaleData(
        val uniform: Float? = null,
        val x: Float? = null,
        val y: Float? = null
    )

    /**
     * Extract scale data from standalone Scale property.
     */
    private fun extractScaleData(data: JsonElement?): ScaleData {
        if (data == null) return ScaleData()

        return when (data) {
            is JsonPrimitive -> {
                // Single number = uniform scale
                data.floatOrNull?.let { ScaleData(uniform = it) } ?: ScaleData()
            }
            is JsonObject -> {
                // ScalePropertyParser emits a discriminated union:
                //   {"type":"uniform","value":1.5}  — `scale: 150%` / `scale: 2`
                //   {"type":"2d","x":1.2,"y":0.8}   — `scale: 1.2 0.8`
                //   {"type":"none"}                 — `scale: none` (identity)
                // The old code only read x/y, so the UNIFORM shape silently
                // extracted as identity (Transforms_C02: `scale: 150%`
                // rendered unscaled at 180x48 while web/iOS drew 270x72).
                val uniform = data["value"]?.jsonPrimitive?.floatOrNull
                if (uniform != null) return ScaleData(uniform = uniform)

                val x = data["x"]?.jsonPrimitive?.floatOrNull
                val y = data["y"]?.jsonPrimitive?.floatOrNull

                if (x != null && y != null && x == y) {
                    ScaleData(uniform = x)
                } else {
                    ScaleData(x = x, y = y)
                }
            }
            else -> ScaleData()
        }
    }

    /**
     * Helper class for translate extraction results.
     */
    private data class TranslateData(
        val x: Dp? = null,
        val y: Dp? = null,
        val z: Dp? = null,
        // CSS percentage translates resolve against the element's own size,
        // so we surface them as a separate 0..1 fraction the applier
        // multiplies by `size.{width,height}` inside graphicsLayer.
        val xFraction: Float? = null,
        val yFraction: Float? = null,
    )

    /**
     * Extract translate data from standalone Translate property.
     */
    private fun extractTranslateData(data: JsonElement?): TranslateData {
        // retro R1 (A11#1): the longhand is a discriminated union (none / 1d
        // length / 1d percentage / 2d / 3d — TranslateProperty.kt) and only
        // the `2d` shape carries the top-level x/y keys this function used
        // to read, so every one-axis `translate: 20px` decoded as identity
        // (Translate_OneAxis_Px Android x0 = 16 vs iOS/web 36). The shapes
        // now live in TranslateLonghandWire with their verbatim wires; this
        // stays the adapter onto the config's fields.
        val d = TranslateLonghandWire.decode(data)
        return TranslateData(d.x, d.y, d.z, d.xFraction, d.yFraction)
    }
}
