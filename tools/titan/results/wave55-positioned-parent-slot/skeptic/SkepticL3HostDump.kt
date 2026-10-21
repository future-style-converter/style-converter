package com.styleconverter.test.screenshot

// Wave 55 L3 SKEPTIC census, JVM half (NOT the lane's census): copied into
// apps/android-harness/app/src/test/... of the skeptic's worktree by
// skeptic-census.sh, run as `:app:testDebugUnitTest --tests '*SkepticL3HostDump*'`,
// then deleted. It reads every wave54-final per-test IR exactly as the harness
// does (IRDocumentDecoder.decode -> SlotComposer.compose), applies the
// harness ROOT REWRITE ComposedCaptureCanvas applies before hostActivates
// (withCanvasOwnedBodyMargin -> withUaBlockMarginOnHoistedRoot ->
// composedCanvasRoots) and records, per document, CanvasRootHoist.hostActivates
// on BOTH the raw and the rewritten roots; and per (parent, out-of-flow child)
// pair of the rewritten tree: the runtime's own Box-branch predicate inputs
// (RELATIVE / TransformContainingBlock / OutOfFlowContainingBlock) and
// PositionedParentFlowSlot.applies. Direction, widths, insets and classes
// are computed by skeptic-census.py from the IR, independently.

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRDocumentDecoder
import com.styleconverter.runtime.core.renderer.SlotComposer
import com.styleconverter.runtime.layout.position.CanvasRootHoist
import com.styleconverter.runtime.layout.position.OutOfFlowContainingBlock
import com.styleconverter.runtime.layout.position.PositionExtractor
import com.styleconverter.runtime.layout.position.PositionType
import com.styleconverter.runtime.layout.position.PositionedParentFlowSlot
import com.styleconverter.runtime.layout.position.TransformContainingBlock
import java.io.File
import org.junit.Test

class SkepticL3HostDump {
    private fun pos(c: IRComponent) =
        PositionExtractor.extractPositionConfig(c.properties.map { it.type to it.data }).type

    @Test
    fun dump() {
        val run = System.getenv("SKL3_RUN") ?: error("SKL3_RUN unset")
        val out = File(System.getenv("SKL3_OUT") ?: error("SKL3_OUT unset"))
        val sb = StringBuilder()
        val files = File(run, "sections").listFiles().orEmpty().sortedBy { it.name }
            .flatMap { File(it, "per-test-ir").listFiles().orEmpty().filter { f -> f.name.endsWith(".json") }.sortedBy { f -> f.name } }
        var n = 0
        for (f in files) {
            val sec = f.parentFile.parentFile.name
            val raw = SlotComposer.compose(IRDocumentDecoder.decode(f.readText()))
            val margin = resolveComposedCanvasMargin(raw)
            val plan = resolveComposedCanvasRootBackground(raw)
            val rew = composedCanvasRoots(withCanvasOwnedBodyMargin(raw, margin).map(::withUaBlockMarginOnHoistedRoot), plan)
            val hRaw = CanvasRootHoist.hostActivates(raw)
            val hRew = CanvasRootHoist.hostActivates(rew)
            sb.append("DOC\t$sec\t${f.name.removeSuffix(".json")}\t$hRaw\t$hRew\n")
            n++
            fun walk(p: IRComponent, chain: String) {
                val kids = p.children.orEmpty()
                val oof = kids.any { pos(it) == PositionType.ABSOLUTE || pos(it) == PositionType.FIXED }
                if (oof) {
                    val box = pos(p) == PositionType.RELATIVE ||
                        (TransformContainingBlock.establishes(p.properties) || OutOfFlowContainingBlock.establishes(p.properties))
                    kids.forEachIndexed { i, k ->
                        sb.append("PAIR\t${f.name.removeSuffix(".json")}\t${p.id}\t$i\t${k.id}\t${pos(p)}\t${pos(k)}\t$box\t" +
                            "${PositionedParentFlowSlot.applies(k.properties, p.properties)}\t$chain\n")
                    }
                }
                kids.forEach { walk(it, "$chain/${it.id}") }
            }
            rew.forEach { walk(it, it.id) }
        }
        out.writeText(sb.toString())
        println("SKL3 docs=$n")
    }
}
