package app.irmodels.properties.layout.grid

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class GridColumnStartProperty(
    val value: GridLine
) : IRProperty {
    override val propertyName = "grid-column-start"
}

@Serializable
sealed interface GridLine {
    @Serializable
    @kotlinx.serialization.SerialName("auto")
    data class Auto(val unit: Unit = Unit) : GridLine

    @Serializable
    @kotlinx.serialization.SerialName("number")
    data class LineNumber(val number: Int) : GridLine

    @Serializable
    @kotlinx.serialization.SerialName("name")
    data class LineName(val name: String) : GridLine

    @Serializable
    @kotlinx.serialization.SerialName("span")
    data class Span(val count: Int) : GridLine

    @Serializable
    @kotlinx.serialization.SerialName("span-name")
    data class SpanName(val name: String) : GridLine
}
