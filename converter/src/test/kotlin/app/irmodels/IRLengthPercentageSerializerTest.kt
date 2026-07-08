package app.irmodels

// Pins the wire format of IRLengthPercentage so the runtime extractors
// (web/Android/iOS) can rely on a stable JSON shape. The asymmetric
// encoding (percent → raw number, length → IRLength object) is the
// pillar of backward compatibility with the pre-fix clip-path polygon
// points format and is documented on IRLengthPercentageSerializer.

import app.irmodels.properties.effects.ClipPathProperty
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IRLengthPercentageSerializerTest {

    private val json = Json { encodeDefaults = true }

    @Test
    fun `percentage encodes as raw number primitive`() {
        // Percentage branch must emit a JSON number to preserve the
        // legacy `{x: 50.0, y: 50.0}` polygon-point wire shape that
        // every platform extractor was originally coded against.
        val v = IRLengthPercentage.Percentage(IRPercentage(50.0))
        val encoded = json.encodeToString(IRLengthPercentageSerializer, v)
        assertEquals("50.0", encoded, "percent → primitive number")
    }

    @Test
    fun `length encodes as IRLength object`() {
        // Length branch delegates to IRLengthSerializer → object with
        // `px` (absolute) or `original` (relative) keys.
        val v = IRLengthPercentage.Length(IRLength.fromPx(100.0))
        val encoded = json.parseToJsonElement(
            json.encodeToString(IRLengthPercentageSerializer, v)
        ).jsonObject
        assertEquals(100.0, encoded["px"]?.jsonPrimitive?.double, "px value preserved")
    }

    @Test
    fun `percentage round-trips`() {
        val original = IRLengthPercentage.Percentage(IRPercentage(75.5))
        val encoded = json.encodeToString(IRLengthPercentageSerializer, original)
        val decoded = json.decodeFromString(IRLengthPercentageSerializer, encoded)
        assertTrue(decoded is IRLengthPercentage.Percentage)
        assertEquals(75.5, decoded.percentage.value)
    }

    @Test
    fun `length round-trips for px`() {
        val original = IRLengthPercentage.Length(IRLength.fromPx(42.5))
        val encoded = json.encodeToString(IRLengthPercentageSerializer, original)
        val decoded = json.decodeFromString(IRLengthPercentageSerializer, encoded)
        assertTrue(decoded is IRLengthPercentage.Length)
        assertEquals(42.5, decoded.length.pixels)
    }

    @Test
    fun `length round-trips for em with original block`() {
        // EM is a relative unit — IRLengthSerializer encodes via the
        // `original: {v, u}` block (no `px`). Make sure that survives.
        val original = IRLengthPercentage.Length(
            IRLength.fromRelative(1.5, IRLength.LengthUnit.EM)
        )
        val encoded = json.encodeToString(IRLengthPercentageSerializer, original)
        val decoded = json.decodeFromString(IRLengthPercentageSerializer, encoded)
        assertTrue(decoded is IRLengthPercentage.Length)
        assertEquals(IRLength.LengthUnit.EM, decoded.length.unit)
        assertEquals(1.5, decoded.length.value)
    }

    /**
     * End-to-end through ClipPathProperty.Point: a polygon whose points
     * mix px and percent serializes to a points array with mixed primitives
     * + objects, and round-trips losslessly.
     */
    @Test
    fun `polygon points list round-trips with mixed kinds`() {
        val pts = listOf(
            ClipPathProperty.Point(
                IRLengthPercentage.Percentage(IRPercentage(0.0)),
                IRLengthPercentage.Percentage(IRPercentage(0.0))
            ),
            ClipPathProperty.Point(
                IRLengthPercentage.Length(IRLength.fromPx(100.0)),
                IRLengthPercentage.Length(IRLength.fromPx(0.0))
            ),
            ClipPathProperty.Point(
                IRLengthPercentage.Length(IRLength.fromPx(100.0)),
                IRLengthPercentage.Percentage(IRPercentage(50.0))
            ),
        )
        val ser = ListSerializer(ClipPathProperty.Point.serializer())
        val encoded = json.encodeToString(ser, pts)
        val decoded = json.decodeFromString(ser, encoded)

        assertEquals(3, decoded.size)

        // Vertex 0 — both axes percent, both raw-number primitives.
        assertTrue(decoded[0].x is IRLengthPercentage.Percentage)
        assertTrue(decoded[0].y is IRLengthPercentage.Percentage)

        // Vertex 1 — both axes length, both IRLength objects.
        val v1x = decoded[1].x as IRLengthPercentage.Length
        assertEquals(100.0, v1x.length.pixels)

        // Vertex 2 — mixed: x length, y percent.
        assertTrue(decoded[2].x is IRLengthPercentage.Length)
        assertTrue(decoded[2].y is IRLengthPercentage.Percentage)
    }

    /**
     * Legacy compatibility: a polygon serialized by the OLD code path
     * (raw numbers only) must still deserialize cleanly into the new
     * Percentage branch. This protects committed baselines and any
     * golden-file fixtures that pre-date the IRLengthPercentage rollout.
     */
    @Test
    fun `legacy raw-number wire format deserializes as Percentage`() {
        val legacyJson = """{"x": 50.0, "y": 25.0}"""
        val point = json.decodeFromString(ClipPathProperty.Point.serializer(), legacyJson)
        assertTrue(point.x is IRLengthPercentage.Percentage, "legacy x → Percentage")
        assertTrue(point.y is IRLengthPercentage.Percentage, "legacy y → Percentage")
        assertEquals(50.0, (point.x as IRLengthPercentage.Percentage).percentage.value)
        assertEquals(25.0, (point.y as IRLengthPercentage.Percentage).percentage.value)
    }
}
