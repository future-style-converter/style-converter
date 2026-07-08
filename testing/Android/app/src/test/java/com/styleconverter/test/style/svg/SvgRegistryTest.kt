package com.styleconverter.test.style.svg

import com.styleconverter.test.style.PropertyRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SvgRegistryTest {

    @Before
    fun prime() {
        SvgRegistration.hashCode()
    }

    private val props = listOf(
        "Fill", "FillOpacity", "FillRule",
        "Stroke", "StrokeOpacity", "StrokeWidth",
        "StrokeLinecap", "StrokeLinejoin", "StrokeMiterlimit",
        "StrokeDasharray", "StrokeDashoffset",
        "StopColor", "StopOpacity",
        "FloodColor", "FloodOpacity", "LightingColor",
        "Marker", "MarkerStart", "MarkerMid", "MarkerEnd", "MarkerSide",
        "PaintOrder",
        "ShapeRendering", "ColorRendering",
        "ColorInterpolation", "ColorInterpolationFilters",
        "BufferedRendering", "EnableBackground", "VectorEffect",
        "Cx", "Cy", "R", "Rx", "Ry", "X", "Y", "D"
    )

    @Test
    fun `all svg properties are registered`() {
        val missing = props.filter { !PropertyRegistry.isMigrated(it) }
        assertTrue("Missing: $missing", missing.isEmpty())
    }

    @Test
    fun `stroke and fill families are owned by svg`() {
        listOf("Fill", "Stroke", "PaintOrder", "VectorEffect").forEach {
            assertTrue(
                "$it has owner ${PropertyRegistry.ownerOf(it)}",
                PropertyRegistry.ownerOf(it) == "svg"
            )
        }
    }

    @Test
    fun `geometry attributes are registered`() {
        val geo = listOf("Cx", "Cy", "R", "Rx", "Ry", "X", "Y", "D")
        geo.forEach {
            assertTrue("$it not registered", PropertyRegistry.isMigrated(it))
        }
    }
}
