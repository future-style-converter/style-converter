package com.styleconverter.runtime.layout.position

// Wave 54 (lane L4, unit OOF-android) — pins for OutOfFlowContainingBlock (the
// non-transform containing-block rule table) and for the two hoist call sites
// it feeds (CanvasRootHoist :222 OR, :340 FIXED inset clause, :389 FIXED RC1).
// Twin: runtimes/swiftui/Tests/StyleConverterRuntimeTests/
// OutOfFlowContainingBlockTests.swift — same rows, same order.
//
//   P1  the rule table, on the wire shapes the converter emits;
//   P3  the pure walks over VERBATIM per-test IR (four payloads inline for CI;
//       the whole wave53-final corpus when present locally);
//   P4  host activation flips true → false on exactly the 7 hostFlips documents.
// The corpus pin compares against the census of the UNCHANGED code, executed
// before the rule table existed and committed beside the lane note:
// tools/titan/results/wave54-oof-layout/census-compose.base.txt.

import com.styleconverter.runtime.PropertyTracker
import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRDocumentDecoder
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.renderer.ComponentRenderer
import com.styleconverter.runtime.core.renderer.SlotComposer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class OutOfFlowContainingBlockTest {

    // One wire property from a JSON literal (the converter's exact shapes).
    private fun prop(type: String, json: String) = IRProperty(type, Json.parseToJsonElement(json))

    // Decode + slot-compose exactly as the harness screen does.
    private fun roots(json: String): List<IRComponent> = SlotComposer.compose(IRDocumentDecoder.decode(json))

    // The rule table over a one-property list.
    private fun est(type: String, json: String) = OutOfFlowContainingBlock.establishes(listOf(prop(type, json)))

    // ── P1: the rule table ─────────────────────────────────────────────────

    @Test fun `P1 contain layout, paint, strict and content establish`() {
        // css-contain-2 §3.2 / §3.4; strict and content include both.
        for (k in listOf("PAINT", "STRICT", "CONTENT", "LAYOUT")) assertTrue(k, est("Contain", """["$k"]"""))
    }

    @Test fun `P1 contain size, inline-size, style and none do not`() {
        // Neither layout nor paint containment: no containing block.
        for (k in listOf("SIZE", "INLINE_SIZE", "STYLE", "NONE")) assertFalse(k, est("Contain", """["$k"]"""))
    }

    @Test fun `P1 filter, backdrop-filter and their will-change hints establish`() {
        // filter-effects-1 §5 / filter-effects-2 §2 / css-will-change-1 §3.
        assertTrue(est("Filter", """[{"fn":"invert","v":100}]"""))
        assertTrue(est("BackdropFilter", """[{"fn":"blur","r":{"px":10}}]"""))
        assertTrue(est("WillChange", """[{"type":"property-name","name":"backdrop-filter"}]"""))
        assertTrue(est("WillChange", """[{"type":"property-name","name":"filter"}]"""))
        assertTrue(est("WillChange", """[{"type":"property-name","name":"contain"}]"""))
    }

    @Test fun `P1 none forms and will-change opacity do not establish`() {
        // The converter's none shapes: "none" for filter, [] for backdrop-filter.
        assertFalse(est("Filter", "\"none\""))
        assertFalse(est("BackdropFilter", "[]"))
        // opacity promotes but creates no containing block; transform is the twin table's.
        assertFalse(est("WillChange", """[{"type":"property-name","name":"opacity"}]"""))
        assertFalse(est("WillChange", """[{"type":"property-name","name":"transform"}]"""))
    }

    @Test fun `P1 a positioned establisher withholds the claim and leaves a breadcrumb`() {
        // backdrop-filter-clip-rect's container shape: ABSOLUTE + backdrop-filter.
        PropertyTracker.reset()
        val abs = listOf(prop("Position", "\"ABSOLUTE\""), prop("BackdropFilter", """[{"fn":"blur","r":{"px":10}}]"""))
        assertFalse(OutOfFlowContainingBlock.establishes(abs))
        assertTrue(PropertyTracker.isUnhandled(OutOfFlowContainingBlock.POSITIONED_ESTABLISHER_BREADCRUMB))
        // FIXED is withheld too; RELATIVE (not absolute/fixed) still claims.
        assertFalse(OutOfFlowContainingBlock.establishes(listOf(prop("Position", "\"FIXED\""), prop("Contain", """["PAINT"]"""))))
        assertTrue(OutOfFlowContainingBlock.establishes(listOf(prop("Position", "\"RELATIVE\""), prop("Contain", """["PAINT"]"""))))
    }

    @Test fun `P1 declared insets - auto and absence are not, px and unresolved expressions are`() {
        // css-position-3 §3.5.3: only all-auto insets give the static position.
        assertFalse(OutOfFlowContainingBlock.declaresAnyInset(listOf(prop("Position", "\"FIXED\""))))
        assertFalse(OutOfFlowContainingBlock.declaresAnyInset(listOf(prop("Top", "\"auto\""))))
        assertTrue(OutOfFlowContainingBlock.declaresAnyInset(listOf(prop("Top", """{"px":0}"""))))
        // anchor-center-safe-rtl `__4-480` verbatim: both insets unresolvable, still declared.
        val anchored = listOf(prop("Top", """{"expr":"calc(anchor(right) + 5px)"}"""), prop("Right", """{"expr":"calc(anchor(left) + 5px)"}"""))
        assertTrue(OutOfFlowContainingBlock.declaresAnyInset(anchored))
        assertFalse(CanvasRootHoist.hasAnyInset(anchored))
    }

    // ── P3: the pure walks on verbatim payloads (CI-resident) ──────────────
    // tools/titan/runs/wave53-final/sections/<sec>/per-test-ir/<file>, byte-
    // identical to wave54-open (cmp), minified only by the converter itself.

    /** css-contain/…contain-content-003.json (sha1 d01c3bcf6713b7e877fb9b82cb908cda5d04ff56). */
    private val containContent003 = """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-contain__contain-content-003__0-044","name":"wpt__css-contain__contain-content-003__0","properties":[],"text":"Test passes if there is a filled green square and no red.","meta":{"sourceTag":"p"}},{"id":"wpt__css-contain__contain-content-003__1-045","name":"wpt__css-contain__contain-content-003__1","properties":[{"type":"Width","data":{"type":"length","px":100}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"Contain","data":["CONTENT"]},{"type":"Height","data":{"type":"length","px":100}}]},{"id":"contain-content-003__1__0-046","name":"contain-content-003__1__0","properties":[{"type":"Width","data":{"type":"length","px":100}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}},{"type":"Height","data":{"type":"length","px":50}},{"type":"Position","data":"ABSOLUTE"},{"type":"Right","data":{"px":0}},{"type":"Top","data":{"px":0}}],"slot":{"parent":"wpt__css-contain__contain-content-003__1-045"},"meta":{"role":"ws-after"}},{"id":"contain-content-003__1__1-047","name":"contain-content-003__1__1","properties":[{"type":"Width","data":{"type":"length","px":100}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}},{"type":"Height","data":{"type":"length","px":50}},{"type":"Position","data":"ABSOLUTE"},{"type":"Right","data":{"px":0}},{"type":"Bottom","data":{"px":0}}],"slot":{"parent":"wpt__css-contain__contain-content-003__1-045"}}]}"""

    /** CSS2/…abspos__static-fixed-inside-abspos.json (sha1 d6cc16b49924976b00c6d6fce2f0fcd4fe48831a). */
    private val staticFixedInsideAbspos = """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css2__abspos__static-fixed-inside-abspos__0-015","name":"wpt__CSS2__abspos__static-fixed-inside-abspos__0","properties":[],"text":"Test passes if there is a filled green square and no red.","meta":{"sourceTag":"p","role":"ws-after"}},{"id":"wpt__css2__abspos__static-fixed-inside-abspos__1-016","name":"wpt__CSS2__abspos__static-fixed-inside-abspos__1","properties":[{"type":"Generic","data":{"propertyName":"display","rawValue":"absolute","_unmapped":true}},{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":100}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}]},{"id":"abspos__static-fixed-inside-abspos__1__0-017","name":"abspos__static-fixed-inside-abspos__1__0","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Width","data":{"type":"length","px":50}},{"type":"Height","data":{"type":"length","px":50}},{"type":"MarginLeft","data":{"px":50}},{"type":"MarginTop","data":{"px":50}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}}],"slot":{"parent":"wpt__css2__abspos__static-fixed-inside-abspos__1-016"}},{"id":"abspos__static-fixed-inside-abspos__1__0__0-018","name":"abspos__static-fixed-inside-abspos__1__0__0","properties":[{"type":"Position","data":"FIXED"},{"type":"Width","data":{"type":"length","px":50}},{"type":"Height","data":{"type":"length","px":50}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"slot":{"parent":"abspos__static-fixed-inside-abspos__1__0-017"}}]}"""

    /** filter-effects/…backdrop-filter-containing-block.json (sha1 20d48b00652c95a8cc30e30c4fee2c1d9d989282). */
    private val backdropContainingBlock = """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__filter-effects__backdrop-filter-containing-block__0-087","name":"wpt__filter-effects__backdrop-filter-containing-block__0","properties":[],"text":"Expected: one green square and one red square, both 200px by 200px.","meta":{"sourceTag":"p","role":"ws-after"}},{"id":"wpt__filter-effects__backdrop-filter-containing-block__1-088","name":"wpt__filter-effects__backdrop-filter-containing-block__1","properties":[{"type":"Width","data":{"type":"length","px":200}},{"type":"BackdropFilter","data":[{"fn":"invert","v":100}]}]},{"id":"backdrop-filter-containing-block__1__0-089","name":"backdrop-filter-containing-block__1__0","properties":[{"type":"Position","data":"FIXED"},{"type":"Top","data":{"px":0}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}},{"type":"Width","data":{"type":"percentage","value":100}},{"type":"Height","data":{"type":"length","px":200}}],"slot":{"parent":"wpt__filter-effects__backdrop-filter-containing-block__1-088"},"meta":{"role":"ws-after"}},{"id":"backdrop-filter-containing-block__1__1-090","name":"backdrop-filter-containing-block__1__1","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Top","data":{"px":0}},{"type":"Left","data":{"px":210}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"Width","data":{"type":"percentage","value":100}},{"type":"Height","data":{"type":"length","px":200}}],"slot":{"parent":"wpt__filter-effects__backdrop-filter-containing-block__1-088"}}]}"""

    /** css-position/…position-relative-003.json (sha1 456044b91e90339d84191fc455c0e961920d80e1). */
    private val positionRelative003 = """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-position__position-relative-003__0-197","name":"wpt__css-position__position-relative-003__0","properties":[],"text":"Test passes if there is a filled green square and no red.","meta":{"sourceTag":"p","role":"ws-after"}},{"id":"wpt__css-position__position-relative-003__1-198","name":"wpt__css-position__position-relative-003__1","properties":[{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":100}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}}]},{"id":"position-relative-003__1__0-199","name":"position-relative-003__1__0","properties":[{"type":"Position","data":"RELATIVE"},{"type":"Top","data":100},{"type":"Left","data":100}],"slot":{"parent":"wpt__css-position__position-relative-003__1-198"},"meta":{"sourceTag":"span"}},{"id":"position-relative-003__1__0__0-200","name":"position-relative-003__1__0__0","properties":[{"type":"Position","data":"RELATIVE"},{"type":"Top","data":{"px":-100}},{"type":"Left","data":{"px":-100}}],"slot":{"parent":"position-relative-003__1__0-199"},"meta":{"sourceTag":"span"}},{"id":"position-relative-003__1__0__0__0-201","name":"position-relative-003__1__0__0__0","properties":[{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":100}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}},{"type":"Position","data":"FIXED"}],"slot":{"parent":"position-relative-003__1__0__0-200"}}]}"""

    @Test fun `P3 the carriers hoist nothing and the M2 hosts stay inactive`() {
        // M2 (contain:content, backdrop-filter) and M1 (all-auto fixed): no box
        // escapes to the canvas any more, so the host takes its identity path…
        for (doc in listOf(containContent003, backdropContainingBlock, positionRelative003)) {
            assertTrue(CanvasRootHoist.collectCanvasHoisted(roots(doc)).isEmpty())
            assertFalse(CanvasRootHoist.hostActivates(roots(doc)))
        }
        // …except static-fixed-inside-abspos, whose abspos parent is RC1 (host
        // active) and whose fixed child now rides PositionedParentFlowSlot.
        val sf = roots(staticFixedInsideAbspos)
        assertTrue(CanvasRootHoist.collectCanvasHoisted(sf).isEmpty())
        assertTrue(CanvasRootHoist.hostActivates(sf))
    }

    // ── P3 / P4 over the whole corpus (local-only: tools/titan/runs is gitignored) ──

    // The repo root (the directory holding tools/titan/runs/wave53-final).
    private fun repoRoot(): File? {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, "tools/titan/runs/wave53-final/sections").isDirectory) dir = dir.parentFile
        return dir
    }

    // One line per document: every decision the OOF unit can move (same format as the base file).
    private fun censusLine(name: String, rs: List<IRComponent>): String {
        val h = mutableListOf<String>(); val r = mutableListOf<String>(); val b = mutableListOf<String>()
        val t = mutableListOf<String>(); val s = mutableListOf<String>()
        fun walk(n: IRComponent, pos: Boolean, tf: Boolean, clip: Boolean, mc: Boolean?) {
            if (CanvasRootHoist.shouldHoistToCanvasRoot(n.properties, pos, tf, clip)) h += n.id
            if (CanvasRootHoist.rendersInFlowAsStaticPosition(n.properties, pos, tf, clip)) r += n.id
            val type = PositionExtractor.extractPositionConfig(n.properties.map { it.type to it.data }).type
            val kids = n.children.orEmpty()
            if (type == PositionType.RELATIVE || (CanvasRootHoist.establishesTransformContainingBlock(n.properties) &&
                    kids.any { ComponentRenderer.isOutOfFlowChild(it.properties) })) b += n.id
            if (ComponentRenderer.isOutOfFlowChild(n.properties) && tf) t += n.id
            kids.filter { PositionedParentFlowSlot.applies(it.properties, n.properties) }.forEach { s += it.id }
            val cp = com.styleconverter.runtime.columns.MulticolSpannerContainingBlock.childPositionedAncestor(pos, mc, n.properties)
            val ct = tf || CanvasRootHoist.establishesTransformContainingBlock(n.properties)
            val cc = clip || CanvasRootHoist.establishesUnescapableClip(n.properties)
            val cm = com.styleconverter.runtime.columns.MulticolSpannerContainingBlock.childPositionedAtMulticol(mc, cp, n.properties)
            kids.forEach { walk(it, cp, ct, cc, cm) }
        }
        rs.forEach { walk(it, false, false, false, null) }
        return "$name host=${CanvasRootHoist.hostActivates(rs)} H=$h R=$r B=$b T=$t S=$s"
    }

    @Test fun `P3 P4 the corpus moves in exactly the 9 OOF-android carriers and the host in exactly 7`() {
        val root = repoRoot()
        assumeTrue("wave53-final corpus not present", root != null)
        // Every per-test IR document, in the base file's order (file name).
        val files = File(root, "tools/titan/runs/wave53-final/sections").listFiles().orEmpty()
            .flatMap { File(it, "per-test-ir").listFiles().orEmpty().toList() }
            .filter { it.name.endsWith(".json") }.sortedBy { it.name }
        val base = File(root, "tools/titan/results/wave54-oof-layout/census-compose.base.txt").readLines()
        val now = files.map { censusLine(it.name, roots(it.readText())) }
        // Written to the build dir so a red run can be read (and a base re-cut) line by line.
        File(root, "runtimes/compose/build/oof-census.now.txt").writeText(now.joinToString("\n", postfix = "\n"))
        assertEquals(1435, now.size)
        assertEquals(base.size, now.size)
        // Documents whose decisions moved, and those whose host turned off.
        val moved = base.indices.filter { base[it] != now[it] }.map { now[it].substringBefore(' ') }
        val hostOff = base.indices.filter { "host=true" in base[it] && "host=false" in now[it] }.map { now[it].substringBefore(' ') }
        assertEquals(listOf(
            "wpt__CSS2__abspos__static-fixed-inside-abspos.json",
            "wpt__css-contain__contain-content-003.json", "wpt__css-contain__contain-content-004.json",
            "wpt__css-contain__contain-content-011.json",
            "wpt__css-position__change-insets-inside-strict-containment-nested.json",
            "wpt__css-position__position-fixed-scroll-nested-fixed.json",
            "wpt__css-position__position-relative-003.json", "wpt__css-position__position-relative-004.json",
            "wpt__filter-effects__backdrop-filter-containing-block.json",
        ), moved)
        assertEquals(moved - setOf(
            "wpt__CSS2__abspos__static-fixed-inside-abspos.json",
            "wpt__css-position__position-fixed-scroll-nested-fixed.json",
        ), hostOff)
    }
}
