package com.styleconverter.runtime.layout.position

import com.styleconverter.runtime.PropertyTracker
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// Wave 55 (lane L7, unit U6-compose, pins C4-C7 — wave-54 L4 skeptic K-X4, S-X7's Compose twin, and the two
// breadcrumbs no test read). OutOfFlowContainingBlock's rule table (wave 54, lane L4) is pinned on the converter's
// EMITTED shapes by OutOfFlowContainingBlockTest P1; these pins reach the branches the corpus never carries, so a
// regression there would pass every corpus read:
//   C4 filter-effects-1 §5 — a `filter` leaf that is an OBJECT (`url(…)` / an unresolved expression: the author
//      wrote something other than none) establishes. 0 corpus Filter object leaves at wave54-final.
//   C5 css-contain-2 §3.4 — `contain: paint` as a bare string PRIMITIVE (PerformanceExtractor's string branch, the
//      space-separated form; the converter emits arrays) establishes.
//   C6 no silent fallthrough — a Contain leaf the decoder THROWS on answers false AND leaves CONTAIN_DECODE_BREADCRUMB.
//   C7 a declared content-visibility (css-contain-2 §4: implied layout/paint containment, named but not claimed)
//      leaves IMPLIED_CONTAINMENT_BREADCRUMB — read on the VERBATIM component
//      content-visibility-auto-shared-element__3__2__0-092 (tools/titan/runs/wave54-final/sections/
//      css-view-transitions/per-test-ir/wpt__css-view-transitions__content-visibility-auto-shared-element.json,
//      sha256 fae6d09f…, byte-identical at wave55-open), which carries no candidate type: the crumb fires on the
//      fast-bail path too.
// MUTATIONS (executed red → restore → green, sha256 in tools/titan/results/wave55-instruments/_note.md):
//   C4 `is JsonObject -> false` in filterListIsUsed · C5 PerformanceExtractor's JsonPrimitive branch returns
//   emptySet() · C6 the catch's markUnhandled deleted · C7 the IMPLIED_TYPES markUnhandled deleted.
class OutOfFlowContainingBlockBranchesTest {

    // One wire property from a JSON literal.
    private fun prop(type: String, json: String) = IRProperty(type, Json.parseToJsonElement(json))

    // The tracker is process-global: every pin starts from an empty one.
    @Before fun resetTracker() = PropertyTracker.reset()

    @Test fun `C4 a filter object leaf (url or unresolved expression) establishes`() {
        assertTrue(OutOfFlowContainingBlock.establishes(listOf(prop("Filter", """{"url":"#f"}"""))))
    }

    @Test fun `C5 contain paint as a bare string primitive establishes`() {
        assertTrue(OutOfFlowContainingBlock.establishes(listOf(prop("Contain", "\"paint\""))))
    }

    @Test fun `C6 an undecodable contain leaf answers false and leaves the decode breadcrumb`() {
        // `jsonPrimitive` throws on the object under "paint" (PerformanceExtractor's object branch).
        assertFalse(OutOfFlowContainingBlock.establishes(listOf(prop("Contain", """{"paint":{"bad":1}}"""))))
        assertTrue(PropertyTracker.isUnhandled(OutOfFlowContainingBlock.CONTAIN_DECODE_BREADCRUMB))
    }

    /** content-visibility-auto-shared-element__3__2__0-092, verbatim. */
    private val cvAuto = """{"id":"content-visibility-auto-shared-element__3__2__0-092","name":"content-visibility-auto-shared-element__3__2__0","properties":[{"type":"ContentVisibility","data":"AUTO"},{"type":"ContainIntrinsicSize","data":{"type":"length","px":500}},{"type":"Position","data":"STATIC"},{"type":"Width","data":{"type":"length","px":98}},{"type":"Height","data":{"type":"length","px":500}},{"type":"BoxSizing","data":"CONTENT_BOX"},{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}},{"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":0}},{"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":0}},{"type":"BorderTopWidth","data":{"px":0}},{"type":"BorderRightWidth","data":{"px":0}},{"type":"BorderBottomWidth","data":{"px":0}},{"type":"BorderLeftWidth","data":{"px":0}},{"type":"BorderTopStyle","data":"NONE"},{"type":"BorderRightStyle","data":"NONE"},{"type":"BorderBottomStyle","data":"NONE"},{"type":"BorderLeftStyle","data":"NONE"},{"type":"BorderTopColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderRightColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderBottomColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderLeftColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0,"b":0,"a":0},"original":{"r":0,"g":0,"b":0,"a":0}}},{"type":"Display","data":"BLOCK"},{"type":"OverflowX","data":"VISIBLE"},{"type":"OverflowY","data":"VISIBLE"}],"slot":{"parent":"content-visibility-auto-shared-element__3__2-091"},"text":"descendant c-v"}"""

    @Test fun `C7 a declared content-visibility leaves the implied-containment breadcrumb`() {
        // The verbatim component's own properties, in wire order.
        val props = Json.parseToJsonElement(cvAuto).jsonObject["properties"]!!.jsonArray.map {
            IRProperty(it.jsonObject["type"]!!.jsonPrimitive.content, it.jsonObject["data"]!!)
        }
        // No Contain / Filter / BackdropFilter / WillChange: not an establisher …
        assertFalse(OutOfFlowContainingBlock.establishes(props))
        // … but the implied containment is named, never silent.
        assertTrue(PropertyTracker.isUnhandled(OutOfFlowContainingBlock.IMPLIED_CONTAINMENT_BREADCRUMB))
    }
}
