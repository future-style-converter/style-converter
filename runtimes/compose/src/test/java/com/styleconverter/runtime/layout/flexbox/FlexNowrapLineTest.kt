package com.styleconverter.runtime.layout.flexbox

// Wave 52 (lane L10) — pins for FlexNowrapLine: THE GATE on verbatim
// wave51-fix per-test IR (tools/titan/runs/wave51-fix/sections/…/
// per-test-ir/*.json, gitignored — hence embedded) and the §9.5 placement
// by OUTER size (css-flexbox-1 §9.2 step 3, §9.5, css-overflow-3 §2).
//
// The composable itself cannot run here (plain JVM JUnit4, no Robolectric —
// runtimes/compose/build.gradle.kts); its placement is the pure
// `mainOffsets` pinned below, and its measuring rule (main axis unbounded)
// is the one-line Constraints call next to it.
//
// EXECUTED MUTATIONS (tools/titan/results/wave52-flex-nowrap-gaps/
// mutations.log; restored byte-exact, sha-verified):
//   KT-L1 mainOffsets clamps like Row's spacedBy (min(cursor, 200 − size))
//         → `008 places six items overflowing` RED ([…,150,150] tail)
//   KT-L2 mainOffsets ignores the outer delta → `027 advances by outer` RED
//   KT-G1 gate drops the shrink-0 requirement → `a shrinkable overflow…` RED
//   KT-G2 gate drops the trigger (always true when admissible) →
//         `a fitting line without negative margin…` RED
//   (fix pass, skeptic must-fix — RTL)
//   KT-D1 gate drops `if (rtl) return false` → `an RTL line is refused…` RED
//   KT-D2 paintShiftPx ignores RTL (`if (false)`) → `the paint shift mirrors…` RED
//   KT-D3 paintShiftPx ignores the item's own Direction → `the paint shift mirrors…` RED
//   KT-D4 paintShiftPx mirrors with `-x` (a −0f) → `the paint shift mirrors…` RED

import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlexNowrapLineTest {

    /** css-gaps/flex/flex-gap-decorations-008 (android P 0.9646, squeezed). */
    private val doc008 = """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-gaps__flex__flex-gap-decorations-008__0-056","name":"wpt__css-gaps__flex__flex-gap-decorations-008__0","properties":[{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}}],"meta":{"role":"body-root"}},{"id":"wpt__css-gaps__flex__flex-gap-decorations-008__1-057","name":"wpt__css-gaps__flex__flex-gap-decorations-008__1","properties":[{"type":"BorderTopWidth","data":{"px":2}},{"type":"BorderRightWidth","data":{"px":2}},{"type":"BorderBottomWidth","data":{"px":2}},{"type":"BorderLeftWidth","data":{"px":2}},{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderRightStyle","data":"SOLID"},{"type":"BorderBottomStyle","data":"SOLID"},{"type":"BorderLeftStyle","data":"SOLID"},{"type":"BorderTopColor","data":{"srgb":{"r":0.3764705882352941,"g":0.5450980392156862,"b":0.6588235294117647},"original":{"r":96,"g":139,"b":168}}},{"type":"BorderRightColor","data":{"srgb":{"r":0.3764705882352941,"g":0.5450980392156862,"b":0.6588235294117647},"original":{"r":96,"g":139,"b":168}}},{"type":"BorderBottomColor","data":{"srgb":{"r":0.3764705882352941,"g":0.5450980392156862,"b":0.6588235294117647},"original":{"r":96,"g":139,"b":168}}},{"type":"BorderLeftColor","data":{"srgb":{"r":0.3764705882352941,"g":0.5450980392156862,"b":0.6588235294117647},"original":{"r":96,"g":139,"b":168}}},{"type":"Display","data":"FLEX"},{"type":"ColumnGap","data":{"type":"length","px":10}},{"type":"ColumnRuleStyle","data":"SOLID"},{"type":"ColumnRuleWidth","data":{"type":"length","px":10}},{"type":"ColumnRuleColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"Width","data":{"type":"length","px":200}},{"type":"FlexWrap","data":"NOWRAP"}]},{"id":"flex__flex-gap-decorations-008__0__0-058","name":"flex__flex-gap-decorations-008__0__0","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0.3764705882352941,"g":0.5450980392156862,"b":0.6588235294117647,"a":0.2},"original":{"r":96,"g":139,"b":168,"a":0.2}}},{"type":"Width","data":{"type":"length","px":50}},{"type":"Height","data":{"type":"length","px":50}},{"type":"FlexShrink","data":{"value":{"type":"app.irmodels.properties.layout.flexbox.FlexShrinkProperty.FlexShrinkValue.Number","value":0},"normalizedValue":0}}],"slot":{"parent":"wpt__css-gaps__flex__flex-gap-decorations-008__1-057"},"text":"One","meta":{"role":"ws-after"}},{"id":"flex__flex-gap-decorations-008__0__1-059","name":"flex__flex-gap-decorations-008__0__1","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0.3764705882352941,"g":0.5450980392156862,"b":0.6588235294117647,"a":0.2},"original":{"r":96,"g":139,"b":168,"a":0.2}}},{"type":"Width","data":{"type":"length","px":50}},{"type":"Height","data":{"type":"length","px":50}},{"type":"FlexShrink","data":{"value":{"type":"app.irmodels.properties.layout.flexbox.FlexShrinkProperty.FlexShrinkValue.Number","value":0},"normalizedValue":0}}],"slot":{"parent":"wpt__css-gaps__flex__flex-gap-decorations-008__1-057"},"text":"Two","meta":{"role":"ws-after"}},{"id":"flex__flex-gap-decorations-008__0__2-060","name":"flex__flex-gap-decorations-008__0__2","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0.3764705882352941,"g":0.5450980392156862,"b":0.6588235294117647,"a":0.2},"original":{"r":96,"g":139,"b":168,"a":0.2}}},{"type":"Width","data":{"type":"length","px":50}},{"type":"Height","data":{"type":"length","px":50}},{"type":"FlexShrink","data":{"value":{"type":"app.irmodels.properties.layout.flexbox.FlexShrinkProperty.FlexShrinkValue.Number","value":0},"normalizedValue":0}}],"slot":{"parent":"wpt__css-gaps__flex__flex-gap-decorations-008__1-057"},"text":"Three","meta":{"role":"ws-after"}},{"id":"flex__flex-gap-decorations-008__0__3-061","name":"flex__flex-gap-decorations-008__0__3","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0.3764705882352941,"g":0.5450980392156862,"b":0.6588235294117647,"a":0.2},"original":{"r":96,"g":139,"b":168,"a":0.2}}},{"type":"Width","data":{"type":"length","px":50}},{"type":"Height","data":{"type":"length","px":50}},{"type":"FlexShrink","data":{"value":{"type":"app.irmodels.properties.layout.flexbox.FlexShrinkProperty.FlexShrinkValue.Number","value":0},"normalizedValue":0}}],"slot":{"parent":"wpt__css-gaps__flex__flex-gap-decorations-008__1-057"},"text":"Four","meta":{"role":"ws-after"}},{"id":"flex__flex-gap-decorations-008__0__4-062","name":"flex__flex-gap-decorations-008__0__4","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0.3764705882352941,"g":0.5450980392156862,"b":0.6588235294117647,"a":0.2},"original":{"r":96,"g":139,"b":168,"a":0.2}}},{"type":"Width","data":{"type":"length","px":50}},{"type":"Height","data":{"type":"length","px":50}},{"type":"FlexShrink","data":{"value":{"type":"app.irmodels.properties.layout.flexbox.FlexShrinkProperty.FlexShrinkValue.Number","value":0},"normalizedValue":0}}],"slot":{"parent":"wpt__css-gaps__flex__flex-gap-decorations-008__1-057"},"text":"Five","meta":{"role":"ws-after"}},{"id":"flex__flex-gap-decorations-008__0__5-063","name":"flex__flex-gap-decorations-008__0__5","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0.3764705882352941,"g":0.5450980392156862,"b":0.6588235294117647,"a":0.2},"original":{"r":96,"g":139,"b":168,"a":0.2}}},{"type":"Width","data":{"type":"length","px":50}},{"type":"Height","data":{"type":"length","px":50}},{"type":"FlexShrink","data":{"value":{"type":"app.irmodels.properties.layout.flexbox.FlexShrinkProperty.FlexShrinkValue.Number","value":0},"normalizedValue":0}}],"slot":{"parent":"wpt__css-gaps__flex__flex-gap-decorations-008__1-057"},"text":"Six"}]}"""

    /** css-gaps/flex/flex-gap-decorations-027 (android f 0.9085). */
    private val doc027 = """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-gaps__flex__flex-gap-decorations-027__0-193","name":"wpt__css-gaps__flex__flex-gap-decorations-027__0","properties":[{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":200}}],"meta":{"role":"body-root"}},{"id":"wpt__css-gaps__flex__flex-gap-decorations-027__1-194","name":"wpt__css-gaps__flex__flex-gap-decorations-027__1","properties":[{"type":"BorderTopWidth","data":{"px":2}},{"type":"BorderRightWidth","data":{"px":2}},{"type":"BorderBottomWidth","data":{"px":2}},{"type":"BorderLeftWidth","data":{"px":2}},{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderRightStyle","data":"SOLID"},{"type":"BorderBottomStyle","data":"SOLID"},{"type":"BorderLeftStyle","data":"SOLID"},{"type":"BorderTopColor","data":{"srgb":{"r":0.3764705882352941,"g":0.5450980392156862,"b":0.6588235294117647},"original":{"r":96,"g":139,"b":168}}},{"type":"BorderRightColor","data":{"srgb":{"r":0.3764705882352941,"g":0.5450980392156862,"b":0.6588235294117647},"original":{"r":96,"g":139,"b":168}}},{"type":"BorderBottomColor","data":{"srgb":{"r":0.3764705882352941,"g":0.5450980392156862,"b":0.6588235294117647},"original":{"r":96,"g":139,"b":168}}},{"type":"BorderLeftColor","data":{"srgb":{"r":0.3764705882352941,"g":0.5450980392156862,"b":0.6588235294117647},"original":{"r":96,"g":139,"b":168}}},{"type":"Display","data":"FLEX"},{"type":"ColumnGap","data":{"type":"length","px":10}},{"type":"ColumnRuleStyle","data":"SOLID"},{"type":"ColumnRuleWidth","data":{"type":"length","px":10}},{"type":"ColumnRuleColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"Width","data":{"type":"length","px":200}},{"type":"FlexWrap","data":"NOWRAP"}]},{"id":"flex__flex-gap-decorations-027__0__0-195","name":"flex__flex-gap-decorations-027__0__0","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0.3764705882352941,"g":0.5450980392156862,"b":0.6588235294117647,"a":0.2},"original":{"r":96,"g":139,"b":168,"a":0.2}}},{"type":"Width","data":{"type":"length","px":50}},{"type":"Height","data":{"type":"length","px":50}},{"type":"FlexShrink","data":{"value":{"type":"app.irmodels.properties.layout.flexbox.FlexShrinkProperty.FlexShrinkValue.Number","value":0},"normalizedValue":0}},{"type":"MarginLeft","data":{"px":-150}}],"slot":{"parent":"wpt__css-gaps__flex__flex-gap-decorations-027__1-194"},"text":"One","meta":{"role":"ws-after"}},{"id":"flex__flex-gap-decorations-027__0__1-196","name":"flex__flex-gap-decorations-027__0__1","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0.3764705882352941,"g":0.5450980392156862,"b":0.6588235294117647,"a":0.2},"original":{"r":96,"g":139,"b":168,"a":0.2}}},{"type":"Width","data":{"type":"length","px":50}},{"type":"Height","data":{"type":"length","px":50}},{"type":"FlexShrink","data":{"value":{"type":"app.irmodels.properties.layout.flexbox.FlexShrinkProperty.FlexShrinkValue.Number","value":0},"normalizedValue":0}}],"slot":{"parent":"wpt__css-gaps__flex__flex-gap-decorations-027__1-194"},"text":"Two","meta":{"role":"ws-after"}},{"id":"flex__flex-gap-decorations-027__0__2-197","name":"flex__flex-gap-decorations-027__0__2","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0.3764705882352941,"g":0.5450980392156862,"b":0.6588235294117647,"a":0.2},"original":{"r":96,"g":139,"b":168,"a":0.2}}},{"type":"Width","data":{"type":"length","px":50}},{"type":"Height","data":{"type":"length","px":50}},{"type":"FlexShrink","data":{"value":{"type":"app.irmodels.properties.layout.flexbox.FlexShrinkProperty.FlexShrinkValue.Number","value":0},"normalizedValue":0}}],"slot":{"parent":"wpt__css-gaps__flex__flex-gap-decorations-027__1-194"},"text":"Three","meta":{"role":"ws-after"}},{"id":"flex__flex-gap-decorations-027__0__3-198","name":"flex__flex-gap-decorations-027__0__3","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0.3764705882352941,"g":0.5450980392156862,"b":0.6588235294117647,"a":0.2},"original":{"r":96,"g":139,"b":168,"a":0.2}}},{"type":"Width","data":{"type":"length","px":50}},{"type":"Height","data":{"type":"length","px":50}},{"type":"FlexShrink","data":{"value":{"type":"app.irmodels.properties.layout.flexbox.FlexShrinkProperty.FlexShrinkValue.Number","value":0},"normalizedValue":0}}],"slot":{"parent":"wpt__css-gaps__flex__flex-gap-decorations-027__1-194"},"text":"Four","meta":{"role":"ws-after"}},{"id":"flex__flex-gap-decorations-027__0__4-199","name":"flex__flex-gap-decorations-027__0__4","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0.3764705882352941,"g":0.5450980392156862,"b":0.6588235294117647,"a":0.2},"original":{"r":96,"g":139,"b":168,"a":0.2}}},{"type":"Width","data":{"type":"length","px":50}},{"type":"Height","data":{"type":"length","px":50}},{"type":"FlexShrink","data":{"value":{"type":"app.irmodels.properties.layout.flexbox.FlexShrinkProperty.FlexShrinkValue.Number","value":0},"normalizedValue":0}}],"slot":{"parent":"wpt__css-gaps__flex__flex-gap-decorations-027__1-194"},"text":"Five","meta":{"role":"ws-after"}},{"id":"flex__flex-gap-decorations-027__0__5-200","name":"flex__flex-gap-decorations-027__0__5","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0.3764705882352941,"g":0.5450980392156862,"b":0.6588235294117647,"a":0.2},"original":{"r":96,"g":139,"b":168,"a":0.2}}},{"type":"Width","data":{"type":"length","px":50}},{"type":"Height","data":{"type":"length","px":50}},{"type":"FlexShrink","data":{"value":{"type":"app.irmodels.properties.layout.flexbox.FlexShrinkProperty.FlexShrinkValue.Number","value":0},"normalizedValue":0}}],"slot":{"parent":"wpt__css-gaps__flex__flex-gap-decorations-027__1-194"},"text":"Six"}]}"""

    /** css-position/position-absolute-dynamic-relayout-003 — the other M2 carrier. */
    private val doc003 = """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-position__position-absolute-dynamic-relayout-003__0-077","name":"wpt__css-position__position-absolute-dynamic-relayout-003__0","properties":[{"type":"Position","data":"STATIC"},{"type":"Width","data":{"type":"length","px":358}},{"type":"Height","data":{"type":"length","px":40}},{"type":"BoxSizing","data":"CONTENT_BOX"},{"type":"MarginTop","data":{"px":16}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":16}},{"type":"MarginLeft","data":{"px":0}},{"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":0}},{"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":0}},{"type":"BorderTopWidth","data":{"px":0}},{"type":"BorderRightWidth","data":{"px":0}},{"type":"BorderBottomWidth","data":{"px":0}},{"type":"BorderLeftWidth","data":{"px":0}},{"type":"BorderTopStyle","data":"NONE"},{"type":"BorderRightStyle","data":"NONE"},{"type":"BorderBottomStyle","data":"NONE"},{"type":"BorderLeftStyle","data":"NONE"},{"type":"BorderTopColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderRightColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderBottomColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderLeftColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0,"b":0,"a":0},"original":{"r":0,"g":0,"b":0,"a":0}}},{"type":"Display","data":"BLOCK"},{"type":"OverflowX","data":"VISIBLE"},{"type":"OverflowY","data":"VISIBLE"}],"text":"Test passes if there is a filled green square and no red.","meta":{"sourceTag":"p","role":"ws-after"}},{"id":"wpt__css-position__position-absolute-dynamic-relayout-003__1-078","name":"wpt__css-position__position-absolute-dynamic-relayout-003__1","properties":[{"type":"Display","data":"FLEX"},{"type":"Position","data":"RELATIVE"},{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":100}},{"type":"Top","data":{"px":0}},{"type":"Left","data":{"px":0}},{"type":"BoxSizing","data":"CONTENT_BOX"},{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}},{"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":0}},{"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":0}},{"type":"BorderTopWidth","data":{"px":0}},{"type":"BorderRightWidth","data":{"px":0}},{"type":"BorderBottomWidth","data":{"px":0}},{"type":"BorderLeftWidth","data":{"px":0}},{"type":"BorderTopStyle","data":"NONE"},{"type":"BorderRightStyle","data":"NONE"},{"type":"BorderBottomStyle","data":"NONE"},{"type":"BorderLeftStyle","data":"NONE"},{"type":"BorderTopColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderRightColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderBottomColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderLeftColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0,"b":0,"a":0},"original":{"r":0,"g":0,"b":0,"a":0}}},{"type":"OverflowX","data":"VISIBLE"},{"type":"OverflowY","data":"VISIBLE"}]},{"id":"position-absolute-dynamic-relayout-003__1__0-079","name":"position-absolute-dynamic-relayout-003__1__0","properties":[{"type":"Order","data":2},{"type":"MarginLeft","data":{"px":-100}},{"type":"Width","data":{"type":"length","px":100}},{"type":"Position","data":"STATIC"},{"type":"Height","data":{"type":"length","px":100}},{"type":"BoxSizing","data":"CONTENT_BOX"},{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":0}},{"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":0}},{"type":"BorderTopWidth","data":{"px":0}},{"type":"BorderRightWidth","data":{"px":0}},{"type":"BorderBottomWidth","data":{"px":0}},{"type":"BorderLeftWidth","data":{"px":0}},{"type":"BorderTopStyle","data":"NONE"},{"type":"BorderRightStyle","data":"NONE"},{"type":"BorderBottomStyle","data":"NONE"},{"type":"BorderLeftStyle","data":"NONE"},{"type":"BorderTopColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderRightColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderBottomColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderLeftColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":{"r":0,"g":128,"b":0}}},{"type":"Display","data":"BLOCK"},{"type":"OverflowX","data":"VISIBLE"},{"type":"OverflowY","data":"VISIBLE"}],"slot":{"parent":"wpt__css-position__position-absolute-dynamic-relayout-003__1-078"},"meta":{"role":"ws-after"}},{"id":"position-absolute-dynamic-relayout-003__1__1-080","name":"position-absolute-dynamic-relayout-003__1__1","properties":[{"type":"Order","data":1},{"type":"Width","data":{"type":"length","px":100}},{"type":"Position","data":"STATIC"},{"type":"Height","data":{"type":"length","px":100}},{"type":"BoxSizing","data":"CONTENT_BOX"},{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}},{"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":0}},{"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":0}},{"type":"BorderTopWidth","data":{"px":0}},{"type":"BorderRightWidth","data":{"px":0}},{"type":"BorderBottomWidth","data":{"px":0}},{"type":"BorderLeftWidth","data":{"px":0}},{"type":"BorderTopStyle","data":"NONE"},{"type":"BorderRightStyle","data":"NONE"},{"type":"BorderBottomStyle","data":"NONE"},{"type":"BorderLeftStyle","data":"NONE"},{"type":"BorderTopColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderRightColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderBottomColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderLeftColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":{"r":255,"g":0,"b":0}}},{"type":"Display","data":"BLOCK"},{"type":"OverflowX","data":"VISIBLE"},{"type":"OverflowY","data":"VISIBLE"}],"slot":{"parent":"wpt__css-position__position-absolute-dynamic-relayout-003__1-078"},"meta":{"role":"ws-after"}},{"id":"position-absolute-dynamic-relayout-003__1__2-081","name":"position-absolute-dynamic-relayout-003__1__2","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Top","data":{"px":10}},{"type":"Left","data":{"px":0}},{"type":"Width","data":{"type":"length","px":0}},{"type":"Height","data":{"type":"length","px":0}},{"type":"BoxSizing","data":"CONTENT_BOX"},{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}},{"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":0}},{"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":0}},{"type":"BorderTopWidth","data":{"px":0}},{"type":"BorderRightWidth","data":{"px":0}},{"type":"BorderBottomWidth","data":{"px":0}},{"type":"BorderLeftWidth","data":{"px":0}},{"type":"BorderTopStyle","data":"NONE"},{"type":"BorderRightStyle","data":"NONE"},{"type":"BorderBottomStyle","data":"NONE"},{"type":"BorderLeftStyle","data":"NONE"},{"type":"BorderTopColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderRightColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderBottomColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderLeftColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0,"b":0,"a":0},"original":{"r":0,"g":0,"b":0,"a":0}}},{"type":"Display","data":"BLOCK"},{"type":"OverflowX","data":"VISIBLE"},{"type":"OverflowY","data":"VISIBLE"}],"slot":{"parent":"wpt__css-position__position-absolute-dynamic-relayout-003__1-078"}}]}"""

    /** (container properties, its slotted children's properties) of the FLEX root. */
    private fun line(doc: String): Pair<List<IRProperty>, List<List<IRProperty>>> {
        val comps = (Json.parseToJsonElement(doc) as JsonObject)["components"] as JsonArray
        fun props(c: JsonObject) = (c["properties"] as JsonArray).map {
            val o = it as JsonObject
            IRProperty(o["type"]!!.jsonPrimitive.content, o["data"]!!)
        }
        // The container: the component declaring Display FLEX.
        val box = comps.map { it as JsonObject }.first { c ->
            props(c).any { it.type == "Display" && it.data.toString() == "\"FLEX\"" }
        }
        val id = box["id"]!!.jsonPrimitive.content
        // Its children: every component slotted under it, in wire order.
        val kids = comps.map { it as JsonObject }.filter {
            ((it["slot"] as? JsonObject)?.get("parent"))?.jsonPrimitive?.content == id
        }
        return props(box) to kids.map { props(it) }
    }

    @Test
    fun `the gate admits 008 - six shrink-0 items overflowing a 200px row`() {
        val (box, kids) = line(doc008)
        assertEquals(6, kids.size)
        assertTrue(FlexNowrapLine.engages(box, kids, rowAxis = true, hasText = false, rtl = false))
        // The column axis reads Height (absent) — never admitted.
        assertFalse(FlexNowrapLine.engages(box, kids, rowAxis = false, hasText = false, rtl = false))
    }

    @Test
    fun `the gate admits 027 - a negative margin-left that makes the line fit exactly`() {
        val (box, kids) = line(doc027)
        assertTrue(FlexNowrapLine.engages(box, kids, rowAxis = true, hasText = false, rtl = false))
        // Outer deltas: only "One" carries one.
        assertEquals(listOf(-150f, 0f, 0f, 0f, 0f, 0f), kids.map { FlexNowrapLine.outerDeltaPx(it, true) })
        // MarginApplier's paint shift for "One" (x only).
        assertEquals(-150f to 0f, FlexNowrapLine.paintShiftPx(kids[0], inheritedRtl = false))
    }

    @Test
    fun `a shrinkable overflow is refused - that line is 9_7's job`() {
        val (box, kids) = line(doc008)
        // Drop every FlexShrink (initial 1): the line must shrink, not overflow.
        val shrinkable = kids.map { k -> k.filterNot { it.type == "FlexShrink" } }
        assertFalse(FlexNowrapLine.engages(box, shrinkable, rowAxis = true, hasText = false, rtl = false))
    }

    @Test
    fun `a fitting line without negative margin is refused - Row keeps it`() {
        val (box, kids) = line(doc008)
        // Three of the six items fit (3·50 + 2·10 = 170 ≤ 200).
        assertFalse(FlexNowrapLine.engages(box, kids.take(3), rowAxis = true, hasText = false, rtl = false))
        // An anonymous text item refuses too.
        assertFalse(FlexNowrapLine.engages(box, kids, rowAxis = true, hasText = true, rtl = false))
    }

    @Test
    fun `the other M2 carrier is refused - order and an abspos child`() {
        val (box, kids) = line(doc003)
        assertFalse(FlexNowrapLine.engages(box, kids, rowAxis = true, hasText = false, rtl = false))
    }

    @Test
    fun `an RTL line is refused - Row mirrors it to main-start at the right edge`() {
        // Skeptic must-fix: Line would draw these from the LEFT edge where Row
        // (LocalLayoutDirection Rtl) mirrors them right — RTL stays on Row.
        for (doc in listOf(doc008, doc027)) {
            val (box, kids) = line(doc)
            // The same verbatim line the LTR pins admit…
            assertTrue(FlexNowrapLine.engages(box, kids, rowAxis = true, hasText = false, rtl = false))
            // …is refused once the container's layout direction is Rtl.
            assertFalse(FlexNowrapLine.engages(box, kids, rowAxis = true, hasText = false, rtl = true))
        }
    }

    @Test
    fun `the paint shift mirrors under RTL like the Dp Modifier offset`() {
        val (_, kids) = line(doc027)
        // Inherited Rtl: One's −150 pull paints +150 (the offset mirrors x).
        assertEquals(150f to 0f, FlexNowrapLine.paintShiftPx(kids[0], inheritedRtl = true))
        // The item's OWN `direction: rtl` (its own provider) mirrors too.
        val ownRtl = kids[0] + IRProperty("Direction", JsonPrimitive("RTL"))
        assertEquals(150f to 0f, FlexNowrapLine.paintShiftPx(ownRtl, inheritedRtl = false))
        // No margin under RTL is exactly (+0, +0) — never a −0f Pair.equals rejects.
        assertEquals(0f to 0f, FlexNowrapLine.paintShiftPx(kids[1], inheritedRtl = true))
    }

    @Test
    fun `008 places six items overflowing the container instead of squeezing`() {
        // Pin 4: x [0,60,120,180,240,300]; Row measured [..,183,193,193].
        assertArrayEquals(
            intArrayOf(0, 60, 120, 180, 240, 300),
            FlexNowrapLine.mainOffsets(IntArray(6) { 50 }, IntArray(6), gap = 10)
        )
    }

    @Test
    fun `027 advances by outer size - Two starts 90px before the content edge`() {
        // Pin 3's Compose twin: offsets [0, -90, -30, 30, 90, 150].
        assertArrayEquals(
            intArrayOf(0, -90, -30, 30, 90, 150),
            FlexNowrapLine.mainOffsets(IntArray(6) { 50 }, intArrayOf(-150, 0, 0, 0, 0, 0), gap = 10)
        )
    }
}
