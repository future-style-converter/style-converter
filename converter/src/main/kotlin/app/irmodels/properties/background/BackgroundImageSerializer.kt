package app.irmodels.properties.background

// Wire serializer for BackgroundImageProperty.BackgroundImage — split from
// BackgroundImageProperty.kt (≤200-line rule) when cross-fade() landed.
//
// WIRE CONTRACT (frozen shapes, see schema/spec/05-versioning.md):
//   "none"                                  — bare string
//   {"url": …} / bare string url            — via IRUrl.serializer()
//   {"type":"linear-gradient","angle":…,"stops":[…],"interp":"in oklch"?}
//   {"type":"radial-gradient","shape":…,"size":…,"pos":{x,y},"stops":[…],"interp":…?}
//   {"type":"conic-gradient","angle":…,"pos":{x,y},"stops":[…],"interp":…?}
//   {"type":"color","color":{…IRColor…}}    — cross-fade color argument
//   {"type":"cross-fade","args":[{"weight":10,"image":…},…],"legacy":true?}
//   {"raw": …}                              — unparseable fallback
// Position axes serialize through IRLengthPercentage: percent = raw number
// (byte-identical to the historical {"x":25,"y":25}), length = object.
// "interp" (wave-37) is the authored <color-interpolation-method>; the key is
// OMITTED whenever the author wrote none, so every pre-wave-37 gradient's
// bytes are unchanged.
import app.irmodels.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

object BackgroundImageSerializer : KSerializer<BackgroundImageProperty.BackgroundImage> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("BackgroundImage")

    override fun serialize(encoder: Encoder, value: BackgroundImageProperty.BackgroundImage) {
        require(encoder is JsonEncoder)
        // Delegate to the shared element builder so cross-fade args reuse
        // the exact same per-layer encoding (one grammar, one wire).
        encoder.encodeJsonElement(toJson(encoder.json, value))
    }

    // BackgroundImage → JsonElement. Public within the module because the
    // CrossFade branch recurses through it for nested image args.
    private fun toJson(json: Json, value: BackgroundImageProperty.BackgroundImage): JsonElement = when (value) {
        // Bare "none" keyword — historical primitive shape.
        is BackgroundImageProperty.BackgroundImage.None -> JsonPrimitive("none")
        // url() delegates to IRUrl (bare string or {url,data} object).
        is BackgroundImageProperty.BackgroundImage.Url -> json.encodeToJsonElement(IRUrl.serializer(), value.url)
        is BackgroundImageProperty.BackgroundImage.LinearGradient -> buildJsonObject {
            // Discriminator carries the repeating flavour (frozen shape).
            put("type", if (value.repeating) "repeating-linear-gradient" else "linear-gradient")
            // Angle omitted when unspecified (CSS default 180deg is applied
            // runtime-side, keeping absent-vs-explicit distinguishable).
            value.angle?.let { put("angle", json.encodeToJsonElement(IRAngle.serializer(), it)) }
            put("stops", json.encodeToJsonElement(ListSerializer(BackgroundImageProperty.ColorStop.serializer()), value.colorStops))
            // Optional <color-interpolation-method> — key absent when unwritten.
            value.interp?.let { put("interp", it) }
        }
        is BackgroundImageProperty.BackgroundImage.RadialGradient -> buildJsonObject {
            put("type", if (value.repeating) "repeating-radial-gradient" else "radial-gradient")
            // Shape/size keywords as plain lowercase strings (frozen shape).
            value.shape?.let { put("shape", it.name.lowercase()) }
            value.size?.let { put("size", it.name.lowercase().replace("_", "-")) }
            // NOTE: key is "pos" — runtimes read (pos ?? position).
            value.position?.let { put("pos", json.encodeToJsonElement(BackgroundImageProperty.Position.serializer(), it)) }
            put("stops", json.encodeToJsonElement(ListSerializer(BackgroundImageProperty.ColorStop.serializer()), value.colorStops))
            value.interp?.let { put("interp", it) }
        }
        is BackgroundImageProperty.BackgroundImage.ConicGradient -> buildJsonObject {
            put("type", if (value.repeating) "repeating-conic-gradient" else "conic-gradient")
            // `from <angle>` — serialized like the linear angle.
            value.angle?.let { put("angle", json.encodeToJsonElement(IRAngle.serializer(), it)) }
            value.position?.let { put("pos", json.encodeToJsonElement(BackgroundImageProperty.Position.serializer(), it)) }
            put("stops", json.encodeToJsonElement(ListSerializer(BackgroundImageProperty.ColorStop.serializer()), value.colorStops))
            value.interp?.let { put("interp", it) }
        }
        // <color> as a cross-fade image argument (css-images-4 §2.6.2).
        is BackgroundImageProperty.BackgroundImage.ColorLayer -> buildJsonObject {
            put("type", "color")
            put("color", json.encodeToJsonElement(IRColor.serializer(), value.color))
        }
        // image() notation (wave-48 lane W5, css-images-4 §2.5). ADDITIVE
        // shape: a brand-new discriminator, so no existing wire bytes move;
        // readers that predate it fall into their unknown-type branch and
        // paint nothing — exactly what they painted when this was Raw.
        is BackgroundImageProperty.BackgroundImage.ImageNotation -> buildJsonObject {
            put("type", "image")
            put("srcs", JsonArray(value.srcs.map { json.encodeToJsonElement(IRUrl.serializer(), it) }))
            // Fallback colour omitted when the author wrote none.
            value.color?.let { put("color", json.encodeToJsonElement(IRColor.serializer(), it)) }
        }
        is BackgroundImageProperty.BackgroundImage.CrossFade -> buildJsonObject {
            put("type", "cross-fade")
            // Each arg: authored weight (omitted key = omitted percentage —
            // the runtimes' CrossFadeMath twins fill it per §2.6.2) plus a
            // recursively-encoded image layer.
            put("args", buildJsonArray {
                for (arg in value.args) add(buildJsonObject {
                    arg.weight?.let { put("weight", it.value) }
                    put("image", toJson(json, arg.image))
                })
            })
            // Only present when true — keeps modern-syntax wire minimal.
            if (value.legacy) put("legacy", true)
        }
        // Global keyword — bare string (frozen shape).
        is BackgroundImageProperty.BackgroundImage.Keyword -> JsonPrimitive(value.keyword)
        // Unparseable layer — original author bytes under "raw".
        is BackgroundImageProperty.BackgroundImage.Raw -> buildJsonObject { put("raw", value.value) }
    }

    override fun deserialize(decoder: Decoder): BackgroundImageProperty.BackgroundImage {
        require(decoder is JsonDecoder)
        return fromJson(decoder.json, decoder.decodeJsonElement())
    }

    // JsonElement → BackgroundImage. Round-trips every shape `toJson` can
    // emit — the pre-cross-fade version decoded radial/conic to None,
    // which silently destroyed gradients on converter-side re-reads; the
    // full decode keeps IR→JSON→IR lossless for tests and tooling.
    private fun fromJson(json: Json, element: JsonElement): BackgroundImageProperty.BackgroundImage {
        val stopsSer = ListSerializer(BackgroundImageProperty.ColorStop.serializer())
        return when {
            element is JsonPrimitive && element.content == "none" -> BackgroundImageProperty.BackgroundImage.None()
            element is JsonPrimitive && element.content in setOf("inherit", "initial", "unset", "revert", "revert-layer") ->
                BackgroundImageProperty.BackgroundImage.Keyword(element.content)
            element is JsonObject && element.containsKey("raw") ->
                BackgroundImageProperty.BackgroundImage.Raw(element["raw"]!!.jsonPrimitive.content)
            element is JsonObject && element.containsKey("type") -> {
                val type = element["type"]!!.jsonPrimitive.content
                // Shared optional sub-decoders (absent key → null).
                val angle = element["angle"]?.let { json.decodeFromJsonElement(IRAngle.serializer(), it) }
                val pos = element["pos"]?.let { json.decodeFromJsonElement(BackgroundImageProperty.Position.serializer(), it) }
                // Optional interpolation method — absent key decodes to null.
                val interp = element["interp"]?.jsonPrimitive?.contentOrNull
                when {
                    type.endsWith("linear-gradient") -> BackgroundImageProperty.BackgroundImage.LinearGradient(
                        angle, json.decodeFromJsonElement(stopsSer, element["stops"]!!), type.startsWith("repeating"), interp)
                    type.endsWith("radial-gradient") -> BackgroundImageProperty.BackgroundImage.RadialGradient(
                        // Shape/size decode from their lowercase keyword forms.
                        element["shape"]?.jsonPrimitive?.content?.let { BackgroundImageProperty.GradientShape.valueOf(it.uppercase()) },
                        element["size"]?.jsonPrimitive?.content?.let { BackgroundImageProperty.GradientSize.valueOf(it.uppercase().replace("-", "_")) },
                        pos, json.decodeFromJsonElement(stopsSer, element["stops"]!!), type.startsWith("repeating"), interp)
                    type.endsWith("conic-gradient") -> BackgroundImageProperty.BackgroundImage.ConicGradient(
                        angle, pos, json.decodeFromJsonElement(stopsSer, element["stops"]!!), type.startsWith("repeating"), interp)
                    // Accept BOTH color-layer wire shapes: the nested
                    // {"color":{…}} this serializer emits AND the flattened
                    // {"type":"color","srgb":…,"original":…} produced by
                    // IRPropertySerializer.deepFlatten on the real wire
                    // (IRColorSerializer only needs srgb/original keys, so
                    // the layer object itself decodes in the flat case).
                    type == "color" -> BackgroundImageProperty.BackgroundImage.ColorLayer(
                        json.decodeFromJsonElement(IRColor.serializer(), element["color"] ?: element))
                    // image() notation round-trip (wave-48 lane W5).
                    type == "image" -> BackgroundImageProperty.BackgroundImage.ImageNotation(
                        (element["srcs"] as? JsonArray)?.map { json.decodeFromJsonElement(IRUrl.serializer(), it) } ?: emptyList(),
                        element["color"]?.let { json.decodeFromJsonElement(IRColor.serializer(), it) })
                    type == "cross-fade" -> BackgroundImageProperty.BackgroundImage.CrossFade(
                        // Recursively decode each image arg via fromJson.
                        (element["args"] as? JsonArray)?.map { argEl ->
                            val obj = argEl.jsonObject
                            BackgroundImageProperty.CrossFadeArg(
                                obj["weight"]?.jsonPrimitive?.doubleOrNull?.let { IRPercentage(it) },
                                fromJson(json, obj["image"]!!))
                        } ?: emptyList(),
                        element["legacy"]?.jsonPrimitive?.booleanOrNull ?: false)
                    // Unknown discriminator — preserve nothing rather than
                    // invent pixels; None matches the legacy fallback.
                    else -> BackgroundImageProperty.BackgroundImage.None()
                }
            }
            // Object without "type" — IRUrl's {url,data} shape.
            element is JsonObject -> BackgroundImageProperty.BackgroundImage.Url(json.decodeFromJsonElement(IRUrl.serializer(), element))
            // Bare string primitive (non-none, non-keyword) — IRUrl's
            // plain-url wire form; decoding it keeps Url layers lossless
            // (the pre-split serializer collapsed them to None on re-read).
            element is JsonPrimitive && element.isString ->
                BackgroundImageProperty.BackgroundImage.Url(json.decodeFromJsonElement(IRUrl.serializer(), element))
            else -> BackgroundImageProperty.BackgroundImage.None()
        }
    }
}
