package com.styleconverter.runtime.layout.position

// Wave 55 lane L3 — the lane's OWN blast-radius census (PLAN §0 "Skeptic census", task rule 4).
// NOT a delivered test: run.sh copies this file into the lane worktree's test tree, runs it with
// L3_CENSUS_RUN=<…/tools/titan/runs/wave54-final>, writes the report, and deletes the copy.
//
// Why a JVM test and not a port: every predicate is the RUNTIME's own function, executed on the
// VERBATIM per-test IR — IRDocumentDecoder.decode → SlotComposer.compose (the roots), the host
// activation CanvasRootHoist.hostActivates, the hoist list collectCanvasHoisted, the Box-branch test
// (RELATIVE, or establishesTransformContainingBlock with an out-of-flow child — ComponentRenderer
// :3504-3512), PositionedParentFlowSlot.applies, and the lane's own PositionedParentSlotGate.engages
// (U1) / .decide (U1+U2). The brief's census (wave55-plan/positioned-parent-slot.census.mjs) ported
// those predicates to JS; this one cannot drift from them.
//
// Stated approximations (the census cannot compose): (1) the harness's root rewrite
// (composedCanvasRoots / withCanvasOwnedBodyMargin) is not applied — it changes margins and wraps an
// in-flow table run, not Position / insets / transforms / clips; (2) Compose's used direction is
// modelled as "the box or a COMPOSITION ancestor declares Direction RTL" (ComponentRenderer provides
// Rtl for an RTL box and never re-provides Ltr; a hoisted box's composition ancestor is the canvas
// overlay, so its chain restarts); (3) every reached parent is counted whatever its display (a flex /
// grid parent may route its children elsewhere — over-counting, never under-counting); (4) the
// pixel classes are wire arithmetic over declared px sizes, refined by the PNG looks in the note.

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRDocumentDecoder
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.renderer.SlotComposer
import com.styleconverter.runtime.core.types.ValueExtractors
import com.styleconverter.runtime.typography.TextStyleApplier
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

class L3CensusRunTest {

    private fun pos(p: List<IRProperty>) = PositionExtractor.extractPositionConfig(p.map { it.type to it.data }).type
    private fun oof(p: List<IRProperty>) = pos(p).let { it == PositionType.ABSOLUTE || it == PositionType.FIXED }
    private fun px(p: List<IRProperty>, t: String): Float? =
        p.lastOrNull { it.type == t }?.let { ValueExtractors.extractDp(it.data)?.value }
    private fun kw(p: List<IRProperty>, t: String): String? =
        p.lastOrNull { it.type == t }?.let { ValueExtractors.extractKeyword(it.data) }
    private fun rtlSelf(p: List<IRProperty>) =
        TextStyleApplier.extractDirection(p) == TextStyleApplier.DirectionMode.RTL
    // A parent that paints something an auto-height collapse would change (background / border).
    private fun paints(p: List<IRProperty>): Boolean =
        p.any { it.type == "BackgroundColor" && !it.data.toString().contains("\"a\":0") } ||
            p.any { it.type.startsWith("Border") && it.type.endsWith("Width") && (ValueExtractors.extractDp(it.data)?.value ?: 0f) > 0f } ||
            p.any { it.type == "BackgroundImage" || it.type == "BoxShadow" || it.type == "Outline" }

    data class Pair2(
        val doc: String, val hostActive: Boolean, val parent: IRComponent, val child: IRComponent,
        val index: Int, val rtl: Boolean, val today: String, val u1: String, val full: String,
    )

    @Test
    fun census() {
        val run = System.getenv("L3_CENSUS_RUN")
        assumeTrue("L3_CENSUS_RUN not set", run != null)
        val out = File(System.getenv("L3_CENSUS_OUT") ?: "l3-census.tsv")
        val sections = File(run!!, "sections").listFiles().orEmpty().filter { it.isDirectory }.sortedBy { it.name }
        val rows = StringBuilder()
        var docs = 0; var inactive = 0; var boxBranch = 0
        val pairs = mutableListOf<Pair2>()
        for (sec in sections) {
            val files = File(sec, "per-test-ir").listFiles().orEmpty().filter { it.name.endsWith(".json") }.sortedBy { it.name }
            for (f in files) {
                docs++
                val roots = SlotComposer.compose(IRDocumentDecoder.decode(f.readText()))
                val host = CanvasRootHoist.hostActivates(roots)
                if (!host) inactive++
                val hoisted = if (host) CanvasRootHoist.collectCanvasHoisted(roots).map { it.id }.toSet() else emptySet()
                val doc = "${sec.name}/${f.name.removeSuffix(".json")}"
                fun walk(n: IRComponent, ancestorRtl: Boolean) {
                    // Composition direction: own RTL, or an ancestor's unless this box is hoisted.
                    val rtl = rtlSelf(n.properties) || (n.id !in hoisted && ancestorRtl)
                    val kids = n.children.orEmpty()
                    val box = pos(n.properties) == PositionType.RELATIVE ||
                        (CanvasRootHoist.establishesTransformContainingBlock(n.properties) && kids.any { oof(it.properties) })
                    if (box && kids.any { PositionedParentFlowSlot.applies(it.properties, n.properties) }) boxBranch++
                    if (!box) kids.forEachIndexed { k, c ->
                        if (!PositionedParentFlowSlot.applies(c.properties, n.properties)) return@forEachIndexed
                        val today = if (host) "MOUNT" else "IDENTITY"
                        val u1 = if (PositionedParentSlotGate.engages(host, true, rtl)) "MOUNT" else "IDENTITY"
                        val full = PositionedParentSlotGate.decide(c.properties, n.properties, host, true, rtl).name
                        pairs += Pair2(doc, host, n, c, k, rtl, today, u1, full)
                    }
                    kids.forEach { walk(it, rtl) }
                }
                roots.forEach { walk(it, false) }
            }
        }
        // Per-pair classification (wire arithmetic; see the banner's approximation 4).
        for (p in pairs) {
            val kids = p.parent.children.orEmpty()
            val classes = mutableListOf<String>()
            val u1Changed = p.today != p.u1
            val u2Changed = p.u1 != p.full
            if (u1Changed) {
                // Earlier siblings newly mounted with a non-zero (or unknown) height move this one.
                val pushers = kids.take(p.index).filter { s ->
                    val sp = pairs.firstOrNull { it.doc == p.doc && it.child.id == s.id }
                    sp != null && sp.today != sp.u1 && (px(s.properties, "Height")?.let { it > 0f } ?: true)
                }
                if (pushers.isNotEmpty()) classes += "STACK(${pushers.size})"
                val pw = px(p.parent.properties, "Width"); val ph = px(p.parent.properties, "Height")
                val cw = px(p.child.properties, "Width"); val ch = px(p.child.properties, "Height")
                if ((pw != null && cw != null && cw > pw) || (ph != null && ch != null && ch > ph)) classes += "CLAMP"
                if (cw == null && kw(p.child.properties, "Width") == null) classes += "AUTO-W"
                if (ph == null) classes += if (paints(p.parent.properties)) "AUTO-H-PAINTS" else "AUTO-H"
                if (p.rtl) classes += "RTL(U1-withheld)"
                if (classes.isEmpty()) classes += "identity"
            }
            if (u2Changed) classes += "RTL-ORIGIN(${p.u1}->${p.full})"
            if (!u1Changed && !u2Changed) classes += "unchanged"
            rows.append(listOf(
                p.doc, if (p.hostActive) "ACTIVE" else "inactive", p.parent.id, p.child.id, if (p.rtl) "RTL" else "LTR",
                kw(p.parent.properties, "Display") ?: "-", p.today, p.u1, p.full, classes.joinToString(","),
            ).joinToString("\t")).append('\n')
        }
        out.writeText(
            "#docs\t$docs\thost-inactive\t$inactive\tbox-branch-parents\t$boxBranch\tpairs\t${pairs.size}\n" +
                "#doc\thost\tparent\tchild\tdir\tdisplay\ttoday\tU1\tU1+U2\tclasses\n" + rows,
        )
    }
}
