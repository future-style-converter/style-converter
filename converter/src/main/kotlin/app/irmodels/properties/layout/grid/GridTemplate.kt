package app.irmodels.properties.layout.grid

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

/** Grid template value (none | auto | subgrid | track list | expression) */
@Serializable(with = GridTemplateSerializer::class)
sealed interface GridTemplate {
    @Serializable data class None(val unit: Unit = Unit) : GridTemplate
    @Serializable data class TrackList(val tracks: List<TrackSize>) : GridTemplate
    @Serializable data class Auto(val unit: Unit = Unit) : GridTemplate
    @Serializable data class Subgrid(val lineNames: List<String>? = null) : GridTemplate
    @Serializable data class Expression(val expr: String) : GridTemplate
}

object GridTemplateSerializer : KSerializer<GridTemplate> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("GridTemplate")

    override fun serialize(encoder: Encoder, value: GridTemplate) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(when (value) {
            is GridTemplate.None -> JsonPrimitive("none")
            is GridTemplate.Auto -> JsonPrimitive("auto")
            is GridTemplate.Subgrid -> if (value.lineNames != null) {
                buildJsonObject {
                    put("subgrid", JsonArray(value.lineNames.map { JsonPrimitive(it) }))
                }
            } else {
                JsonPrimitive("subgrid")
            }
            is GridTemplate.Expression -> buildJsonObject { put("expr", value.expr) }
            is GridTemplate.TrackList -> encoder.json.encodeToJsonElement(
                ListSerializer(TrackSizeSerializer), value.tracks
            )
        })
    }

    override fun deserialize(decoder: Decoder): GridTemplate {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()
        return when {
            element is JsonPrimitive && element.content == "none" -> GridTemplate.None()
            element is JsonPrimitive && element.content == "auto" -> GridTemplate.Auto()
            element is JsonPrimitive && element.content == "subgrid" -> GridTemplate.Subgrid()
            element is JsonObject && element.containsKey("subgrid") -> {
                val names = element["subgrid"]?.jsonArray?.map { it.jsonPrimitive.content }
                GridTemplate.Subgrid(names)
            }
            element is JsonObject && element.containsKey("expr") ->
                GridTemplate.Expression(element["expr"]!!.jsonPrimitive.content)
            element is JsonArray -> GridTemplate.TrackList(
                decoder.json.decodeFromJsonElement(ListSerializer(TrackSizeSerializer), element)
            )
            else -> GridTemplate.None()
        }
    }
}
