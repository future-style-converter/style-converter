// Pure css-multicol-1 §6 spanner-flow geometry (wave-21 lane MULTICOL) — no
// Compose imports on purpose: the JUnit suite pins this math on the plain JVM
// and the IDENTICAL SP-table is pinned by the iOS twin
// (runtimes/swiftui .../columns/MulticolSpannerFlow.swift), so both natives
// sequence spanners, balance pre/post-spanner segments and anchor abspos
// static positions byte-identically.
package com.styleconverter.runtime.columns

// IRComponent only feeds the role classifier (rolesFor); the geometry API
// below is IR-free so the SP pins stay pure integers.
import com.styleconverter.runtime.core.ir.IRComponent
// The shared keyword decoder — the same lane MultiColumnExtractor's
// ColumnSpan read rides, so role classification can never drift from the
// container's config extraction.
import com.styleconverter.runtime.core.types.ValueExtractors

/**
 * The spanner-aware column flow: css-multicol-1 §6.2–§6.3. A
 * `column-span: all` element interrupts the multicol flow; the content
 * BEFORE it is distributed into columns that are BALANCED regardless of
 * `column-fill` (§6.3: "the column heights before the spanner are
 * balanced"), the spanner then spans the full content box, and the flow
 * RESUMES in a fresh row of column boxes below it. Out-of-flow (abspos /
 * fixed) children take no space but keep a STATIC POSITION at the flow
 * point where they would have been (css-position-3 §3.1) — the wave-21
 * diagnosis: an abspos following a spanner anchors in the post-spanner
 * flow of COLUMN 1, not wherever the greedy heuristic drops it.
 *
 * Balancing model: each spanner-free segment of N-column content of total
 * flow height T gets the balanced column block-size H = ceil(T / N)
 * (css-multicol-1 §7.1 pseudo-algorithm reduced to the stacked-block case
 * this corpus exercises); children then FILL SEQUENTIALLY — a child whose
 * flow offset is R starts in column floor(R / H) at block offset R mod H.
 * Whole-child placement only: a child crossing a column boundary either
 * fragments via the wave-10 clip+translate replay (the sole-flow-child
 * case, [soleFlowFragmentReplay]) or renders unfragmented from its start
 * column ([anyFlowChildCrossesBoundary] tells callers to log the bail —
 * repo no-silent-fallthrough rule).
 */
object MulticolSpannerFlow {

    /** How a multicol child participates in the spanner flow. */
    enum class Role {
        /** In-flow, span:none — distributes into column boxes (§2). */
        FLOW,
        /** In-flow, column-span:all — interrupts the flow full-width (§6.2). */
        SPANNER,
        /** Out-of-flow (abspos/fixed) — no space, static-position anchor only (css-position-3 §3.1). */
        STATIC
    }

    /** One child as the planner sees it: measured block-size + role. */
    data class Child(
        /** Measured (or statically resolved) block-size in px; STATIC heights are ignored. */
        val heightPx: Int,
        /** The child's flow participation — see [Role]. */
        val role: Role,
        /**
         * Whether the child DECLARES its block-size (Height/BlockSize in
         * the IR). The sole-flow balance gate requires it: a content-sized
         * wrapper (e.g. one hiding a nested spanner) keeps the legacy
         * render — mirrors the iOS fragment plan's explicit-C resolution.
         */
        val explicitBlockSize: Boolean = true
    )

    /**
     * [Role] plus the declared-block-size flag — what the Compose measure
     * pass needs per measurable (it sees only opaque measurables, so both
     * signals must ride in from the IR at the render hook).
     */
    data class ChildSpec(
        /** The child's flow participation — see [Role]. */
        val role: Role,
        /** True iff the IR declares Height or BlockSize on the child. */
        val explicitBlockSize: Boolean
    )

    /**
     * One child's planned position. x derives at the layout site as
     * columnIndex * (usedColumnWidth + gap) — the [MultiColumnDistribution.Slot]
     * convention (spanners render at x=0 full width; columnIndex pinned 0).
     */
    data class Slot(
        /** The child's [Role], echoed so placement loops need no zip. */
        val role: Role,
        /** 0-based used-column index (0 for spanners by convention). */
        val columnIndex: Int,
        /** Block offset from the container's content-box top, in px. */
        val yPx: Int
    )

    /** The full spanner-flow answer for one container. */
    data class Plan(
        /** One [Slot] per child, index-aligned with the input. */
        val slots: List<Slot>,
        /** Container auto block-size: spanner heights + every segment's balanced H (§6.3). */
        val containerBlockSizePx: Int,
        /**
         * The balanced column block-size H of the segment holding the
         * container's ONLY flow child — the fragmentainer the wave-10
         * clip+translate replay slices with. Null when the flow-child
         * count is not exactly 1 (multi-child segments place whole).
         */
        val soleFlowColumnBlockSizePx: Int?
    )

    /**
     * Role classification for a multicol container's rendered content, in
     * the exact order the platform layout receives its measurables:
     * leading `_text` (when present) renders FIRST as an anonymous flow
     * box (RenderContent's Bug-1 branch), then the children in order.
     * Precedence: out-of-flow beats span — css-position-3 §2.1 takes the
     * box out of flow entirely, so `column-span` on an abspos is moot.
     */
    fun rolesFor(children: List<IRComponent>?, leadingText: Boolean = false): List<Role> {
        // The anonymous leading text box participates as plain flow content.
        val head = if (leadingText) listOf(Role.FLOW) else emptyList()
        // Classify each child from its own declared IR properties.
        return head + (children ?: emptyList()).map { child ->
            // Wire shapes: Position → "ABSOLUTE"/"FIXED"/… keyword;
            // ColumnSpan → "ALL"/"NONE" keyword (converter serializers).
            val position = child.properties.firstOrNull { it.type == "Position" }
                ?.let { ValueExtractors.extractKeyword(it.data)?.uppercase() }
            val span = child.properties.firstOrNull { it.type == "ColumnSpan" }
                ?.let { ValueExtractors.extractKeyword(it.data)?.uppercase() }
            when {
                // Out-of-flow first — see the precedence note above.
                position == "ABSOLUTE" || position == "FIXED" -> Role.STATIC
                // §6.2: only `all` spans; `none` (and unknown) stays in flow.
                span == "ALL" -> Role.SPANNER
                else -> Role.FLOW
            }
        }
    }

    /** In-flow non-spanner child count — the routing gate both natives share. */
    fun flowCount(roles: List<Role>): Int = roles.count { it == Role.FLOW }

    /**
     * [rolesFor] plus the per-child declared-block-size flag ([ChildSpec])
     * — the Compose render hook's one-stop classification. The anonymous
     * leading-text box is content-sized by definition (explicit = false).
     */
    fun specsFor(children: List<IRComponent>?, leadingText: Boolean = false): List<ChildSpec> {
        // Role classification through the single shared classifier.
        val roles = rolesFor(children, leadingText)
        // The leading text spec (when present) heads the list.
        val head = if (leadingText) listOf(ChildSpec(Role.FLOW, false)) else emptyList()
        // Pair each child's role with its Height/BlockSize declaration —
        // presence is the signal (the measure pass supplies the value).
        return head + (children ?: emptyList()).mapIndexed { index, child ->
            ChildSpec(
                roles[index + head.size],
                child.properties.any { it.type == "Height" || it.type == "BlockSize" }
            )
        }
    }

    /**
     * Whether the spanner-flow plan replaces the legacy greedy/stack
     * layout for this container. Deliberately NARROW (dark-stage 327
     * protection — callers additionally gate on WPT capture mode):
     *  - any spanner present → yes (§6.2 sequencing is unconditionally
     *    wrong under the greedy heuristic);
     *  - else only the sole-flow-child auto-height balance case (§7.1:
     *    an unconstrained multicol container ALWAYS balances), and only
     *    when the child DECLARES its block-size (content-sized wrappers —
     *    e.g. one hiding a nested spanner — keep the legacy render) and
     *    balancing actually shortens the column (h > ceil(h/N)) —
     *    otherwise the legacy render is already pixel-identical.
     * Multi-child spanner-less containers keep the greedy heuristic
     * (documented Android-parity approximation, D-table).
     */
    fun engages(children: List<Child>, columnCount: Int): Boolean {
        // §6.2 — a spanner always takes the plan.
        if (children.any { it.role == Role.SPANNER }) return true
        // Sole-flow-child balance: exactly one child, in flow, with a
        // DECLARED block-size (the iOS twin gate is the explicit-C
        // resolution in ColumnsApplier.fragmentPlan).
        if (children.size != 1 || children[0].role != Role.FLOW) return false
        if (!children[0].explicitBlockSize) return false
        // Balancing needs ≥ 2 columns to differ from the identity render.
        val n = maxOf(1, columnCount)
        if (n < 2) return false
        // ceil(h / n) < h ⇔ fragmenting across columns changes geometry.
        val h = maxOf(0, children[0].heightPx)
        return h > (h + n - 1) / n
    }

    /**
     * Whether the sole-flow-child clip+translate replay (wave-10 draw
     * machinery) may fragment this container's flow child: exactly one
     * FLOW child, NO static anchors (their ink would be replayed into
     * every column), and every spanner measured at 0 height (a painted
     * spanner would ghost through the fragment clips — such containers
     * place whole children instead).
     */
    fun soleFlowFragmentReplay(children: List<Child>): Boolean =
        // The replay slices exactly one continuous child paint.
        children.count { it.role == Role.FLOW } == 1 &&
            // Static ink is part of the same draw pass — must not replay.
            children.none { it.role == Role.STATIC } &&
            // Only inkless (0-height) spanners coexist with the replay.
            children.filter { it.role == Role.SPANNER }.all { it.heightPx <= 0 }

    /**
     * True when some FLOW child crosses a balanced column boundary (or
     * overflows the last column) — the whole-child placement path is then
     * an approximation and callers must log the bail once.
     */
    fun anyFlowChildCrossesBoundary(children: List<Child>, columnCount: Int): Boolean {
        val n = maxOf(1, columnCount)
        // Walk the same segments as [plan], tracking each child's R.
        var crosses = false
        forEachSegment(children) { segment ->
            val t = segment.filter { it.role == Role.FLOW }.sumOf { maxOf(0, it.heightPx) }
            val h = if (t == 0) 0 else (t + n - 1) / n
            var r = 0
            for (c in segment) {
                if (c.role != Role.FLOW) continue
                val height = maxOf(0, c.heightPx)
                // Crossing ⇔ the child's band [R, R+h) straddles a k*H line.
                if (h > 0 && height > 0 && (r % h) + height > h) crosses = true
                r += height
            }
        }
        return crosses
    }

    /** Shared segment walk: maximal spanner-free runs, in child order. */
    private inline fun forEachSegment(children: List<Child>, body: (List<Child>) -> Unit) {
        var i = 0
        while (i < children.size) {
            if (children[i].role == Role.SPANNER) { i++; continue }
            var j = i
            // Extend the run until the next spanner (or the end).
            while (j < children.size && children[j].role != Role.SPANNER) j++
            body(children.subList(i, j))
            i = j
        }
    }

    /** The plan itself — see the class doc for the model. SP-table pinned. */
    fun plan(children: List<Child>, columnCount: Int): Plan {
        // §3.2 used counts are ≥ 1; floor defensively for direct callers.
        val n = maxOf(1, columnCount)
        val slots = ArrayList<Slot>(children.size)
        // Running container block offset (spanner tops / segment starts).
        var y = 0
        // Sole-flow bookkeeping for the replay fragmentainer.
        val soleFlow = children.count { it.role == Role.FLOW } == 1
        var soleFlowH: Int? = null
        var i = 0
        while (i < children.size) {
            val c = children[i]
            if (c.role == Role.SPANNER) {
                // §6.2: the spanner spans all columns at the current flow
                // bottom; the flow resumes below it.
                slots.add(Slot(Role.SPANNER, 0, y))
                y += maxOf(0, c.heightPx)
                i++
                continue
            }
            // Segment [i, j): the maximal spanner-free run starting here.
            var j = i
            var t = 0
            while (j < children.size && children[j].role != Role.SPANNER) {
                // Only in-flow children consume column space (§2).
                if (children[j].role == Role.FLOW) t += maxOf(0, children[j].heightPx)
                j++
            }
            // §6.3/§7.1 balanced column block-size: ceil(T / N); an empty
            // segment (all-static, or nothing) contributes no height.
            val h = if (t == 0) 0 else (t + n - 1) / n
            // Sequential fill: R is the running flow offset in the segment.
            var r = 0
            for (k in i until j) {
                // Column floor(R/H), capped at the last column (overflowing
                // content stays in column N-1, column-fill:auto overflow).
                val col = if (h > 0) minOf(r / h, n - 1) else 0
                // Block offset inside that column: R − col·H, from the
                // segment's container-level start y.
                slots.add(Slot(children[k].role, col, y + r - col * h))
                if (children[k].role == Role.FLOW) {
                    // The sole flow child's segment H is the replay's
                    // fragmentainer block-size (wave-10 machinery).
                    if (soleFlow) soleFlowH = h
                    // STATIC children never advance R (no space taken).
                    r += maxOf(0, children[k].heightPx)
                }
            }
            // The whole segment occupies exactly H of container block-size.
            y += h
            i = j
        }
        return Plan(slots, y, soleFlowH)
    }
}
