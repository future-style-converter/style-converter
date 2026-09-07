package com.styleconverter.runtime.layout.position

// Wave 33 (lane C) — JVM pins for the abspos CB-HEIGHT channel
// (CSS 2.2 §10.6.3 and §10.6.4, read through css-position-3 §3.1).
//
// The rule is pure decisions + arithmetic with an identical signature on
// iOS (AbsposCbUsedHeightTests.swift pins the same table entry-for-entry
// so cross-native probes can diff the two rule tables directly), and the
// H6 pin below runs against the LIVE wave-32 IR shape
// (tools/titan/runs/wave32-final/sections/css-tables/per-test-ir/
// wpt__css-tables__absolute-tables-007.json).

import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AbsposCbUsedHeightTest {

    // ── H1: only a positioned box establishes an abspos containing block ──

    @Test fun `H1 - a static ancestor never publishes a used height`() {
        // CSS 2.2 §10.1 item 4: an abspos descendant skips past a static
        // ancestor entirely, so this box's height is not anyone's basis.
        assertNull(
            AbsposCbUsedHeight.contentHeightPx(
                ancestor = listOf(prop("Position", "\"STATIC\"")),
                ancestorTag = "div", ancestorHasOwnContent = false,
                children = listOf(listOf(prop("Height", """{"type":"length","px":100}"""))),
            )
        )
    }

    @Test fun `H1 - an ancestor with no position declaration publishes nothing`() {
        assertNull(
            AbsposCbUsedHeight.contentHeightPx(
                ancestor = emptyList(), ancestorTag = "div", ancestorHasOwnContent = false,
                children = listOf(listOf(prop("Height", """{"type":"length","px":100}"""))),
            )
        )
    }

    @Test fun `H1 - relative absolute fixed and sticky all qualify`() {
        for (kw in listOf("RELATIVE", "ABSOLUTE", "FIXED", "STICKY")) {
            assertEquals(
                "position: $kw establishes the abspos containing block", 40.0,
                AbsposCbUsedHeight.contentHeightPx(
                    ancestor = listOf(prop("Position", "\"$kw\"")),
                    ancestorTag = "div", ancestorHasOwnContent = false,
                    children = listOf(listOf(prop("Height", """{"type":"length","px":40}"""))),
                )!!, 0.0001,
            )
        }
    }

    // ── H2: the declared-size channel always wins ─────────────────────────

    @Test fun `H2 - a declared px height leaves the rule to the declared channel`() {
        // §10.6.3 IS the height:auto branch. A declared size is already
        // answered — with the author's own number — upstream.
        assertNull(
            AbsposCbUsedHeight.contentHeightPx(
                ancestor = listOf(
                    prop("Position", "\"RELATIVE\""),
                    prop("Height", """{"type":"length","px":250}"""),
                ),
                ancestorTag = "div", ancestorHasOwnContent = false,
                children = listOf(listOf(prop("Height", """{"type":"length","px":100}"""))),
            )
        )
    }

    @Test fun `H2 - a declared percentage height also defers`() {
        assertNull(
            AbsposCbUsedHeight.contentHeightPx(
                ancestor = listOf(
                    prop("Position", "\"RELATIVE\""),
                    prop("Height", """{"type":"percentage","value":50}"""),
                ),
                ancestorTag = "div", ancestorHasOwnContent = false,
                children = listOf(listOf(prop("Height", """{"type":"length","px":100}"""))),
            )
        )
    }

    @Test fun `H2 - an explicit auto height is still the auto branch`() {
        assertEquals(
            100.0,
            AbsposCbUsedHeight.contentHeightPx(
                ancestor = listOf(prop("Position", "\"RELATIVE\""), prop("Height", "\"auto\"")),
                ancestorTag = "div", ancestorHasOwnContent = false,
                children = listOf(listOf(prop("Height", """{"type":"length","px":100}"""))),
            )!!, 0.0001,
        )
    }

    @Test fun `H2 - a min or max height clamp is unmodelled so the rule refuses`() {
        // §10.7 clamps the §10.6.3 result; this file does not model it, so
        // answering would be a guess.
        for (t in listOf("MinHeight", "MaxHeight", "MinBlockSize", "MaxBlockSize")) {
            assertNull(
                t,
                AbsposCbUsedHeight.contentHeightPx(
                    ancestor = listOf(prop("Position", "\"RELATIVE\""), prop(t, """{"px":50}""")),
                    ancestorTag = "div", ancestorHasOwnContent = false,
                    children = listOf(listOf(prop("Height", """{"type":"length","px":100}"""))),
                )
            )
        }
    }

    // ── H3: block container, overflow visible, not a UA table ─────────────

    @Test fun `H3 - a declared flex or grid or table display refuses`() {
        for (kw in listOf("FLEX", "GRID", "TABLE", "INLINE_FLEX", "LIST_ITEM", "TABLE_CELL")) {
            assertNull(
                kw,
                AbsposCbUsedHeight.contentHeightPx(
                    ancestor = listOf(prop("Position", "\"RELATIVE\""), prop("Display", "\"$kw\"")),
                    ancestorTag = "div", ancestorHasOwnContent = false,
                    children = listOf(listOf(prop("Height", """{"type":"length","px":100}"""))),
                )
            )
        }
    }

    @Test fun `H3 - block flow-root and inline-block are block containers`() {
        for (kw in listOf("BLOCK", "FLOW_ROOT", "INLINE_BLOCK")) {
            assertEquals(
                kw, 100.0,
                AbsposCbUsedHeight.contentHeightPx(
                    ancestor = listOf(prop("Position", "\"RELATIVE\""), prop("Display", "\"$kw\"")),
                    ancestorTag = "div", ancestorHasOwnContent = false,
                    children = listOf(listOf(prop("Height", """{"type":"length","px":100}"""))),
                )!!, 0.0001,
            )
        }
    }

    @Test fun `H3 - a bare table tag with no declared display refuses`() {
        // The converter never serializes UA defaults, so meta.sourceTag is
        // the only sighting of `<table>` (the AbsposInsetStretch S5 lane).
        assertNull(
            AbsposCbUsedHeight.contentHeightPx(
                ancestor = listOf(prop("Position", "\"RELATIVE\"")),
                ancestorTag = "table", ancestorHasOwnContent = false,
                children = listOf(listOf(prop("Height", """{"type":"length","px":100}"""))),
            )
        )
        // css-display-3 §2 — a DECLARED display always wins over the tag.
        assertEquals(
            100.0,
            AbsposCbUsedHeight.contentHeightPx(
                ancestor = listOf(prop("Position", "\"RELATIVE\""), prop("Display", "\"BLOCK\"")),
                ancestorTag = "table", ancestorHasOwnContent = false,
                children = listOf(listOf(prop("Height", """{"type":"length","px":100}"""))),
            )!!, 0.0001,
        )
    }

    @Test fun `H3 - a non-visible overflow is the unmodelled 10-6-7 branch`() {
        for (t in listOf("OverflowX", "OverflowY", "Overflow", "OverflowBlock")) {
            assertNull(
                t,
                AbsposCbUsedHeight.contentHeightPx(
                    ancestor = listOf(prop("Position", "\"RELATIVE\""), prop(t, "\"HIDDEN\"")),
                    ancestorTag = "div", ancestorHasOwnContent = false,
                    children = listOf(listOf(prop("Height", """{"type":"length","px":100}"""))),
                )
            )
        }
        // The post-load-extracted corpus stamps VISIBLE explicitly — that
        // must NOT refuse, or the rule would never fire on a live IR.
        assertEquals(
            100.0,
            AbsposCbUsedHeight.contentHeightPx(
                ancestor = listOf(
                    prop("Position", "\"RELATIVE\""),
                    prop("OverflowX", "\"VISIBLE\""), prop("OverflowY", "\"VISIBLE\""),
                ),
                ancestorTag = "div", ancestorHasOwnContent = false,
                children = listOf(listOf(prop("Height", """{"type":"length","px":100}"""))),
            )!!, 0.0001,
        )
    }

    // ── H4: own line boxes are not statically measurable ──────────────────

    @Test fun `H4 - an ancestor carrying its own text refuses`() {
        assertNull(
            AbsposCbUsedHeight.contentHeightPx(
                ancestor = listOf(prop("Position", "\"RELATIVE\"")),
                ancestorTag = "div", ancestorHasOwnContent = true,
                children = listOf(listOf(prop("Height", """{"type":"length","px":100}"""))),
            )
        )
    }

    // ── H5: the per-child gate ────────────────────────────────────────────

    @Test fun `H5 - out-of-flow children are skipped not summed`() {
        // The asking abspos box is itself one of these (§9.3.1).
        assertEquals(
            100.0,
            AbsposCbUsedHeight.contentHeightPx(
                ancestor = listOf(prop("Position", "\"RELATIVE\"")),
                ancestorTag = "div", ancestorHasOwnContent = false,
                children = listOf(
                    listOf(
                        prop("Position", "\"ABSOLUTE\""),
                        prop("Height", """{"type":"percentage","value":100}"""),
                    ),
                    listOf(
                        prop("Position", "\"FIXED\""),
                        prop("Height", """{"type":"length","px":999}"""),
                    ),
                    listOf(prop("Height", """{"type":"length","px":100}""")),
                ),
            )!!, 0.0001,
        )
    }

    @Test fun `H5 - a relative or sticky child stays in flow and contributes`() {
        assertEquals(
            30.0,
            AbsposCbUsedHeight.contentHeightPx(
                ancestor = listOf(prop("Position", "\"RELATIVE\"")),
                ancestorTag = "div", ancestorHasOwnContent = false,
                children = listOf(
                    listOf(
                        prop("Position", "\"RELATIVE\""),
                        prop("Height", """{"type":"length","px":10}"""),
                    ),
                    listOf(
                        prop("Position", "\"STICKY\""),
                        prop("Height", """{"type":"length","px":20}"""),
                    ),
                ),
            )!!, 0.0001,
        )
    }

    @Test fun `H5 - a float leaves the overflow-visible sum so the rule refuses`() {
        assertNull(
            AbsposCbUsedHeight.contentHeightPx(
                ancestor = listOf(prop("Position", "\"RELATIVE\"")),
                ancestorTag = "div", ancestorHasOwnContent = false,
                children = listOf(
                    listOf(
                        prop("Float", "\"LEFT\""),
                        prop("Height", """{"type":"length","px":100}"""),
                    ),
                ),
            )
        )
        // `float: none` is the initial value and never disqualifies.
        assertEquals(
            100.0,
            AbsposCbUsedHeight.contentHeightPx(
                ancestor = listOf(prop("Position", "\"RELATIVE\"")),
                ancestorTag = "div", ancestorHasOwnContent = false,
                children = listOf(
                    listOf(
                        prop("Float", "\"NONE\""),
                        prop("Height", """{"type":"length","px":100}"""),
                    ),
                ),
            )!!, 0.0001,
        )
    }

    @Test fun `H5 - a display-none child refuses`() {
        assertNull(
            AbsposCbUsedHeight.contentHeightPx(
                ancestor = listOf(prop("Position", "\"RELATIVE\"")),
                ancestorTag = "div", ancestorHasOwnContent = false,
                children = listOf(
                    listOf(
                        prop("Display", "\"NONE\""),
                        prop("Height", """{"type":"length","px":100}"""),
                    ),
                ),
            )
        )
    }

    @Test fun `H5 - a non-zero vertical band refuses because margins collapse`() {
        for (t in listOf("MarginTop", "MarginBottom", "PaddingTop", "BorderTopWidth")) {
            assertNull(
                t,
                AbsposCbUsedHeight.contentHeightPx(
                    ancestor = listOf(prop("Position", "\"RELATIVE\"")),
                    ancestorTag = "div", ancestorHasOwnContent = false,
                    children = listOf(
                        listOf(prop(t, """{"px":8}"""), prop("Height", """{"type":"length","px":100}""")),
                    ),
                )
            )
        }
    }

    @Test fun `H5 - explicit zero bands are fine - the extracted corpus shape`() {
        // post-load-extract stamps every band as {"px":0}; refusing those
        // would make the rule dead on every live IR.
        val zeroBands = listOf(
            "MarginTop", "MarginBottom", "PaddingTop", "PaddingBottom",
            "BorderTopWidth", "BorderBottomWidth",
        ).map { prop(it, """{"px":0}""") }
        assertEquals(
            100.0,
            AbsposCbUsedHeight.contentHeightPx(
                ancestor = listOf(prop("Position", "\"RELATIVE\"")),
                ancestorTag = "div", ancestorHasOwnContent = false,
                children = listOf(zeroBands + prop("Height", """{"type":"length","px":100}""")),
            )!!, 0.0001,
        )
    }

    @Test fun `H5 - a percentage band is not px so the rule refuses`() {
        // The converter emits a percentage spacing value as a BARE number;
        // reading it as px would bake the wrong band into the sum.
        assertNull(
            AbsposCbUsedHeight.contentHeightPx(
                ancestor = listOf(prop("Position", "\"RELATIVE\"")),
                ancestorTag = "div", ancestorHasOwnContent = false,
                children = listOf(
                    listOf(prop("MarginTop", "10"), prop("Height", """{"type":"length","px":100}""")),
                ),
            )
        )
    }

    @Test fun `H5 - an in-flow child with no definite height refuses`() {
        for (h in listOf(null, "\"auto\"", """{"type":"percentage","value":50}""", """{"expr":"calc(1px + 2px)"}""")) {
            val child = if (h == null) emptyList() else listOf(prop("Height", h))
            assertNull(
                "height = $h",
                AbsposCbUsedHeight.contentHeightPx(
                    ancestor = listOf(prop("Position", "\"RELATIVE\"")),
                    ancestorTag = "div", ancestorHasOwnContent = false,
                    children = listOf(child),
                )
            )
        }
    }

    @Test fun `H5 - the logical BlockSize spelling resolves like Height`() {
        assertEquals(
            60.0,
            AbsposCbUsedHeight.contentHeightPx(
                ancestor = listOf(prop("Position", "\"RELATIVE\"")),
                ancestorTag = "div", ancestorHasOwnContent = false,
                children = listOf(listOf(prop("BlockSize", """{"type":"length","px":60}"""))),
            )!!, 0.0001,
        )
    }

    // ── H6/H7: the sum and the empty-box refusal ──────────────────────────

    @Test fun `H6 - several in-flow children sum in document order`() {
        assertEquals(
            75.0,
            AbsposCbUsedHeight.contentHeightPx(
                ancestor = listOf(prop("Position", "\"RELATIVE\"")),
                ancestorTag = "div", ancestorHasOwnContent = false,
                children = listOf(
                    listOf(prop("Height", """{"type":"length","px":25}""")),
                    listOf(prop("Height", """{"type":"length","px":50}""")),
                ),
            )!!, 0.0001,
        )
    }

    @Test fun `H7 - no in-flow children means no honest answer`() {
        assertNull(
            AbsposCbUsedHeight.contentHeightPx(
                ancestor = listOf(prop("Position", "\"RELATIVE\"")),
                ancestorTag = "div", ancestorHasOwnContent = false,
                children = emptyList(),
            )
        )
        // Only out-of-flow children counts as "none" too.
        assertNull(
            AbsposCbUsedHeight.contentHeightPx(
                ancestor = listOf(prop("Position", "\"RELATIVE\"")),
                ancestorTag = "div", ancestorHasOwnContent = false,
                children = listOf(
                    listOf(
                        prop("Position", "\"ABSOLUTE\""),
                        prop("Height", """{"type":"length","px":100}"""),
                    ),
                ),
            )
        )
    }

    // ── The live absolute-tables-007 wire ─────────────────────────────────

    @Test fun `the live absolute-tables-007 relative wrapper resolves to 100`() {
        // Verbatim from tools/titan/runs/wave32-final/sections/css-tables/
        // per-test-ir/wpt__css-tables__absolute-tables-007.json: a
        // `position:relative; width:100px` wrapper holding the abspos green
        // table and a 100px red block. The used content height is 100, so
        // the table's `height:100%` covers the red exactly like the ref.
        val wrapper = listOf(
            prop("Position", "\"RELATIVE\""),
            prop("Width", """{"type":"length","px":100}"""),
        )
        val greenTable = listOf(
            prop("Position", "\"ABSOLUTE\""),
            prop("Display", "\"TABLE\""),
            prop("Width", """{"type":"percentage","value":100}"""),
            prop("Height", """{"type":"percentage","value":100}"""),
        )
        val redBlock = listOf(prop("Height", """{"type":"length","px":100}"""))
        assertEquals(
            100.0,
            AbsposCbUsedHeight.contentHeightPx(
                ancestor = wrapper, ancestorTag = null, ancestorHasOwnContent = false,
                children = listOf(greenTable, redBlock),
            )!!, 0.0001,
        )
    }

    private fun prop(type: String, json: String) =
        IRProperty(type, Json.parseToJsonElement(json))
}
