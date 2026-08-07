package com.styleconverter.runtime.table

// Wave 34 (lane T, T1) — JVM pins for the CSS 2.1 §17.6.1
// separated-borders track model. Byte-parallel with
// `runtimes/swiftui/Tests/StyleConverterRuntimeTests/
// TableSeparatedTracksTests.swift`: same cases, same numbers, same order,
// so a divergence between the two natives shows up as a test diff and not
// as a pixel diff three device runs later.
//
// Everything under test is pure over the IR, so the whole contract pins
// without Robolectric — the standing constraint of this suite
// (TableBoxTreeTest / CanvasRootHoistTest).
//
// The shape under test is the exact wire from the frozen wave33-final
// gate, css-tables/abspos-container-change-dynamic-001:
//   <table>  (Display TABLE, sourceTag "table", 31.5156 × 26)
//     └ tbody (Display TABLE_ROW_GROUP, 27.5156 × 22)
//        └ tr  (Display TABLE_ROW,      27.5156 × 22)
//           ├ td "A" (Display TABLE_CELL, 11.0469 + 1+1 padding)
//           └ td "B" (Display TABLE_CELL, RELATIVE, 10.4688 + 1+1)
//                └ 100×100 lime ABSOLUTE at top:0 left:0
// The reference paints the lime box at (33,18); iOS painted (16,38) and
// Android (30,37).

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TableSeparatedTracksTest {

    // ── Wire helpers ───────────────────────────────────────────────────

    /** One declaration carrying a raw JSON literal, the shape the reader emits. */
    private fun prop(type: String, json: String): Pair<String, JsonElement?> =
        type to Json.parseToJsonElement(json)

    /** A keyword-valued declaration (`"TABLE"`), the bare-string wire form. */
    private fun kw(type: String, keyword: String) = prop(type, "\"$keyword\"")

    // ── Arrangement (CSS 2.1 §17.6.1) ──────────────────────────────────

    @Test
    fun `a row arranges its cells inline`() {
        // THE fix. Everything else in this file is scaffolding for it.
        assertEquals(
            TableSeparatedTracks.Arrangement.INLINE_ROW,
            TableSeparatedTracks.arrangement(TableBoxTree.Role.ROW)
        )
    }

    @Test
    fun `table and row group stack in the block direction`() {
        assertEquals(
            TableSeparatedTracks.Arrangement.BLOCK_STACK,
            TableSeparatedTracks.arrangement(TableBoxTree.Role.TABLE)
        )
        assertEquals(
            TableSeparatedTracks.Arrangement.BLOCK_STACK,
            TableSeparatedTracks.arrangement(TableBoxTree.Role.ROW_GROUP)
        )
    }

    @Test
    fun `cell and caption form no tracks`() {
        // §2.1 — both establish a BLOCK CONTAINER for their contents.
        assertEquals(
            TableSeparatedTracks.Arrangement.NONE,
            TableSeparatedTracks.arrangement(TableBoxTree.Role.CELL)
        )
        assertEquals(
            TableSeparatedTracks.Arrangement.NONE,
            TableSeparatedTracks.arrangement(TableBoxTree.Role.CAPTION)
        )
        assertEquals(
            TableSeparatedTracks.Arrangement.NONE,
            TableSeparatedTracks.arrangement(TableBoxTree.Role.NONE)
        )
    }

    @Test
    fun `only the table box carries the outer band`() {
        // A row group sits INSIDE the table's band; adding it again there
        // would double-count §17.6.1's edge spacing.
        assertTrue(TableSeparatedTracks.outerBandApplies(TableBoxTree.Role.TABLE))
        assertFalse(TableSeparatedTracks.outerBandApplies(TableBoxTree.Role.ROW_GROUP))
        assertFalse(TableSeparatedTracks.outerBandApplies(TableBoxTree.Role.ROW))
    }

    // ── Used border-spacing ────────────────────────────────────────────

    @Test
    fun `a bare table element gets the HTML UA two pixels`() {
        // abspos-container-change-dynamic-001's exact wire: Display TABLE,
        // meta.sourceTag "table", and NO BorderSpacing declaration — the
        // converter never serializes UA defaults, so the 2px has to come
        // from the HTML rendering UA sheet (§15.3.3 Tables).
        val used = TableSeparatedTracks.usedSpacing(listOf(kw("Display", "TABLE")), "table")
        assertEquals(TableSeparatedTracks.Spacing(2.0, 2.0), used)
        assertEquals(2.0, TableSeparatedTracks.HTML_UA_BORDER_SPACING_PX, 0.0)
    }

    @Test
    fun `css-authored display table on a div gets the CSS initial zero`() {
        // The UA rule targets the `<table>` ELEMENT, not the display type,
        // and `border-spacing`'s CSS initial value is 0 (§17.6.1). A
        // `display: table` div must NOT inherit the 2px.
        assertEquals(
            TableSeparatedTracks.Spacing.ZERO,
            TableSeparatedTracks.usedSpacing(listOf(kw("Display", "TABLE")), "div")
        )
        // …and the same when the wire carries no tag at all.
        assertEquals(
            TableSeparatedTracks.Spacing.ZERO,
            TableSeparatedTracks.usedSpacing(listOf(kw("Display", "TABLE")), null)
        )
    }

    @Test
    fun `a declared two-value spacing beats the UA default`() {
        // Author beats user agent (CSS 2.1 §6.4.1). The wire shape is the
        // converter's, pinned live: border-spacing: 3px 5px →
        // {"type":"two-values","horizontal":{"px":3},"vertical":{"px":5}}.
        val declared = prop(
            "BorderSpacing",
            """{"type":"two-values","horizontal":{"px":3.0},"vertical":{"px":5.0}}"""
        )
        val used = TableSeparatedTracks.usedSpacing(
            listOf(kw("Display", "TABLE"), declared), "table"
        )
        assertEquals(TableSeparatedTracks.Spacing(3.0, 5.0), used)
    }

    @Test
    fun `a declared single spacing applies to both axes`() {
        // border-spacing: 4px → {"type":"single","px":4}.
        val declared = prop("BorderSpacing", """{"type":"single","px":4.0}""")
        val used = TableSeparatedTracks.usedSpacing(
            listOf(kw("Display", "TABLE"), declared), "table"
        )
        assertEquals(TableSeparatedTracks.Spacing(4.0, 4.0), used)
    }

    @Test
    fun `the collapsed model has no spacing at all`() {
        // §17.6.2 — "the border-spacing property is ignored". Null, not
        // zero: the caller has to decide what a collapsed table does, and
        // a null answer makes that decision explicit at the call site.
        assertNull(
            TableSeparatedTracks.usedSpacing(
                listOf(kw("Display", "TABLE"), kw("BorderCollapse", "COLLAPSE")), "table"
            )
        )
        // `separate` (and an absent declaration) keep the separated model.
        assertNotNull(
            TableSeparatedTracks.usedSpacing(
                listOf(kw("Display", "TABLE"), kw("BorderCollapse", "SEPARATE")), "table"
            )
        )
    }

    @Test
    fun `an unresolvable declared spacing falls back rather than inventing a number`() {
        // The wire's null-means-runtime-dependent contract: a calc()/em
        // border-spacing arrives with no px. Falling through to the UA
        // lane is honest; guessing a pixel count is not.
        val unresolved = prop("BorderSpacing", """{"type":"single"}""")
        val used = TableSeparatedTracks.usedSpacing(
            listOf(kw("Display", "TABLE"), unresolved), "table"
        )
        assertEquals(TableSeparatedTracks.Spacing(2.0, 2.0), used)
    }

    // ── Track arithmetic — the measured geometry ───────────────────────

    @Test
    fun `row cell origins reproduce the reference lime box x`() {
        // The row's two cells, as border boxes: 11.0469 + 1 + 1 = 13.0469
        // and 10.4688 + 1 + 1 = 12.4688 (the wire's content widths plus the
        // HTML UA `td { padding: 1px }` the converter DOES serialize).
        val origins = TableSeparatedTracks.trackOrigins(
            listOf(13.0469, 12.4688), spacing = 2.0, band = 0.0
        )
        assertEquals(2, origins.size)
        // Cell 1 flush at the row's content origin…
        assertEquals(0.0, origins[0], 0.0001)
        // …cell 2 one cell + one horizontal border-spacing along.
        assertEquals(15.0469, origins[1], 0.0001)
        // The table one level up adds its own 2px band, and the whole
        // table sits at the canvas's 16px pin: 16 + 2 + 15.0469 = 33.0469,
        // which is the reference's lime-box x of 33 after rounding.
        // Android painted 30 (no spacing at all); iOS painted 16.
        assertEquals(33.0469, 16 + 2 + origins[1], 0.0001)
    }

    @Test
    fun `the table band puts the first row at the reference y`() {
        // One row inside the table box: band first, no inter-row gap.
        val origins = TableSeparatedTracks.trackOrigins(listOf(22.0), spacing = 2.0, band = 2.0)
        assertEquals(listOf(2.0), origins)
        // 16 (canvas pin) + 2 (band) = 18 — the reference's lime-box y.
        // iOS painted 38, Android 37.
        assertEquals(18.0, 16 + origins[0], 0.0001)
    }

    @Test
    fun `track extent reproduces the declared table width`() {
        // The converter serialized the table at 31.5156 wide and 26 tall.
        // Both fall straight out of §17.6.1's arithmetic, which is the
        // strongest available cross-check that the 2px UA default is the
        // number the reference browser actually used.
        assertEquals(
            31.5157,
            TableSeparatedTracks.trackExtent(listOf(13.0469, 12.4688), 2.0, 2.0),
            0.0002
        )
        assertEquals(26.0, TableSeparatedTracks.trackExtent(listOf(22.0), 2.0, 2.0), 0.0001)
        // The ROW's own declared width, 27.5156, is the same tracks with
        // the table's band removed — the band belongs to the table only.
        assertEquals(
            27.5157,
            TableSeparatedTracks.trackExtent(listOf(13.0469, 12.4688), 2.0, 0.0),
            0.0002
        )
    }

    @Test
    fun `an empty track list is just the band`() {
        // An empty `<table>` is 2×2 in the reference, not 0×0.
        assertEquals(emptyList<Double>(), TableSeparatedTracks.trackOrigins(emptyList(), 2.0, 2.0))
        assertEquals(4.0, TableSeparatedTracks.trackExtent(emptyList(), 2.0, 2.0), 0.0001)
    }

    @Test
    fun `a single track takes no inter-track spacing`() {
        // n−1 gaps: one track means zero gaps, whatever the spacing is.
        assertEquals(50.0, TableSeparatedTracks.trackExtent(listOf(50.0), 9.0, 0.0), 0.0001)
    }

    // ── The Compose consumer (no Swift twin — TableConfig is ours) ──────

    @Test
    fun `the tagless extractor overload is byte-identical to pre-wave-34`() {
        // The regression bar for this lane on Android. Every existing call
        // site — ComponentRenderer's DisplayType.TABLE branch — passes no
        // tag, so `effectiveSpacing*` must return exactly what the declared
        // fields alone returned before `usedSpacing` existed.
        val undeclared = TableExtractor.extractTableConfig(listOf(kw("Display", "TABLE")))
        assertEquals(0.0f, undeclared.effectiveSpacingHorizontal.value, 0.0f)
        assertEquals(0.0f, undeclared.effectiveSpacingVertical.value, 0.0f)
        // …and `hasTableConfig` must not flip: it reads the DECLARED
        // fields, which a UA default never touches.
        assertFalse(undeclared.hasTableConfig)

        val declared = TableExtractor.extractTableConfig(
            listOf(
                kw("Display", "TABLE"),
                prop("BorderSpacing", """{"type":"two-values","horizontal":{"px":3.0},"vertical":{"px":5.0}}""")
            )
        )
        assertEquals(3.0f, declared.effectiveSpacingHorizontal.value, 0.0f)
        assertEquals(5.0f, declared.effectiveSpacingVertical.value, 0.0f)
        assertTrue(declared.hasTableConfig)
    }

    @Test
    fun `the tag-carrying extractor overload turns the UA lane on`() {
        // The deferred one-line ComponentRenderer wire (`component._tag`)
        // is what makes Android take this branch; the extractor half is
        // live and pinned here so that wire is a one-liner and not a
        // redesign.
        val ua = TableExtractor.extractTableConfig(listOf(kw("Display", "TABLE")), "table")
        assertEquals(2.0f, ua.effectiveSpacingHorizontal.value, 0.0f)
        assertEquals(2.0f, ua.effectiveSpacingVertical.value, 0.0f)
        // A CSS-authored `display: table` div still gets the initial 0.
        val div = TableExtractor.extractTableConfig(listOf(kw("Display", "TABLE")), "div")
        assertEquals(0.0f, div.effectiveSpacingHorizontal.value, 0.0f)
        // …and the collapsing model gets none whatever the tag says.
        val collapsed = TableExtractor.extractTableConfig(
            listOf(kw("Display", "TABLE"), kw("BorderCollapse", "COLLAPSE")), "table"
        )
        assertEquals(0.0f, collapsed.effectiveSpacingHorizontal.value, 0.0f)
        assertEquals(0.0f, collapsed.effectiveSpacingVertical.value, 0.0f)
    }
}
