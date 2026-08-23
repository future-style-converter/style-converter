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
        /**
         * In-flow with `break-after: column` (wave-42 lane W4) — flows like
         * [FLOW] but FORCES a column break after itself: css-break-3 §4.1
         * (a forced break value ends the current fragmentainer) applied to
         * the multicol case, so the NEXT flow content starts a fresh column
         * chunk. Kept as a role (not a side flag) because the renderer call
         * sites pass bare `List<Role>` through to the platform layouts and
         * must stay untouched (lane ownership: columns/ only).
         */
        FLOW_BREAK_AFTER,
        /** In-flow, column-span:all — interrupts the flow full-width (§6.2). */
        SPANNER,
        /** Out-of-flow (abspos/fixed) — no space, static-position anchor only (css-position-3 §3.1). */
        STATIC
    }

    /**
     * Both in-flow non-spanner roles — every consumer that previously asked
     * `role == FLOW` about flow PARTICIPATION (space consumption, sole-flow
     * counting) must treat a forced-break child identically; only the
     * chunk walk in [plan] cares about the break itself.
     */
    val Role.isFlow: Boolean get() = this == Role.FLOW || this == Role.FLOW_BREAK_AFTER

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
        val explicitBlockSize: Boolean,
        /**
         * True iff the child's SUBTREE declares `float: left/right`
         * anywhere (wave-42 lane W4). The multi-child run fragmenter must
         * bail on such children: the natives still lay floats out as
         * stacked in-flow boxes (the float lane owns css-break-3 §5 float
         * fragmentation + CSS 2.1 §9.5.2 clearance), so slicing that stack
         * across columns would faithfully replicate a WRONG layout into
         * every column instead of one.
         */
        val floatedContent: Boolean = false,
        /**
         * True iff a forced `break-before/after: column` sits somewhere the
         * container-level [Role] list cannot express (wave-42 lane W4):
         * the child's OWN `break-before`, or EITHER break side on any
         * descendant. (The child's own `break-after: column` is not here —
         * it becomes [Role.FLOW_BREAK_AFTER], which the plan honours.)
         *
         * The run fragmenter must bail on it: those breaks are invisible to
         * the container-level run model (css-break WPT
         * block-in-inline-013/014 put them on divs inside anonymous `span`
         * wrappers, and Chromium's one-green-box-per-column reference comes
         * ENTIRELY from honoring them), so slicing the raw stacked strip
         * would ignore real break points the greedy whole-child spread
         * currently gets right.
         *
         * Computed by [specsFor]'s `hasHiddenForcedColumnBreak`.
         */
        val forcedBreakContent: Boolean = false,
        /**
         * True iff the child box has NOTHING inside it — no IR children and
         * no text (wave-42 lane W4). Such a box has neither a class-A
         * breakpoint (between block-level siblings) nor a class-B one
         * (between line boxes) anywhere within it (css-break-3 §4.1), so it
         * is MONOLITHIC: a column boundary may not pass through it, and the
         * run fragmenter pushes it whole into the next column instead
         * ([MulticolRunFragment.runPlan]'s `monolithic` argument).
         */
        val monolithicContent: Boolean = false,
        /**
         * The wave-44 lane-U8 FLOAT-STRIP facts (leading out-of-flow
         * floats, §9.5.2 clear sides, trailing paint ink) — or null when
         * any wire flavor on this child is outside that lane's proven
         * scope ([MulticolFloatStrip.factsFor]'s strict-bail contract). A
         * single null fact disables the whole container's strip, so the
         * run fragmenter's float bail keeps owning unproven float shapes.
         */
        val floatStrip: MulticolFloatStrip.ChildFacts? = null,
        /**
         * Wave-46 lane Y3 — true iff the child declares
         * `box-decoration-break: clone` (css-break-3 §5.2). Separate from
         * [cloneBands] so the sole-child fragmenter can LOG (never
         * silently slice) a clone child whose bands did not resolve or
         * whose content it cannot re-flow per fragment.
         */
        val cloneDeclared: Boolean = false,
        /**
         * Wave-46 lane Y3 — the child's statically-resolved block-axis
         * decoration bands when it is a clone child
         * ([MulticolCloneDecoration.bandsFor]); null for every slice
         * child (the default, byte-identical S-table path) and for a
         * clone child with a non-px band (logged bail to slice).
         */
        val cloneBands: MulticolCloneGeometry.Bands? = null
    )

    /**
     * One child's planned position. x derives at the layout site as
     * columnIndex * (usedColumnWidth + gap) — the [MultiColumnDistribution.Slot]
     * convention (spanners render at x=0 full width; columnIndex pinned 0).
     */
    data class Slot(
        /** The child's [Role], echoed so placement loops need no zip. */
        val role: Role,
        /**
         * 0-based used-column index (0 for spanners by convention). May
         * EXCEED N-1 for a forced-break chunk that ran out of columns —
         * that is a css-multicol-1 §8.2 OVERFLOW column, painted past the
         * container's inline end edge exactly where i·(W+G) lands it.
         */
        val columnIndex: Int,
        /** Block offset from the container's content-box top, in px. */
        val yPx: Int,
        /**
         * True when css-overflow-4 §3 `continue: discard` dropped this
         * child: content from the first overflow column on — and EVERYTHING
         * after it in flow order, spanners included — is not rendered.
         * Placement loops must skip (or park offscreen) discarded slots.
         */
        val discarded: Boolean = false
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
            // Wave-42 lane W4: `break-after: column` forces a column break
            // after this box (css-break-3 §4.1 / §2 "column" applies to the
            // nearest multicol fragmentation context). Only the COLUMN
            // keyword is classified — page/region breaks have no multicol
            // meaning, and `avoid*` values are break AVOIDANCE, not force.
            val breakAfter = child.properties.firstOrNull { it.type == "BreakAfter" }
                ?.let { ValueExtractors.extractKeyword(it.data)?.uppercase() }
            when {
                // Out-of-flow first — see the precedence note above.
                position == "ABSOLUTE" || position == "FIXED" -> Role.STATIC
                // §6.2: only `all` spans; `none` (and unknown) stays in flow.
                span == "ALL" -> Role.SPANNER
                // Forced column break AFTER an in-flow box (wave-42).
                breakAfter == "COLUMN" -> Role.FLOW_BREAK_AFTER
                else -> Role.FLOW
            }
        }
    }

    /** In-flow non-spanner child count — the routing gate both natives share. */
    fun flowCount(roles: List<Role>): Int = roles.count { it.isFlow }

    /**
     * Index of the first IN-FLOW (non-spanner, non-static) child, or -1
     * when the container has none — the measure pass' lookup for the sole
     * flow child the wave-10 replay slices.
     *
     * Exists as a named function because the obvious spelling
     * (`indexOfFirst { it == Role.FLOW }`) became WRONG the moment wave-42
     * added [Role.FLOW_BREAK_AFTER]: [soleFlowFragmentReplay] counts with
     * [isFlow], so a container whose only flow child carries
     * `break-after: column` passed the replay gate and then indexed
     * `placeables[-1]` — an IndexOutOfBounds that would take down the whole
     * capture composition, not just one fragmentation decision.
     */
    fun firstFlowIndex(roles: List<Role>): Int = roles.indexOfFirst { it.isFlow }

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
        // Wave-44 lane U8: one float-presence read per child (shared by the
        // wave-42 flag below AND the strip-facts gate — the strip is only
        // relevant when SOME child floats, and skipping the facts walk for
        // float-less containers keeps their specs byte-identical to
        // wave-42, pinned by the R1 row).
        val floated = (children ?: emptyList()).map { hasFloatedDescendant(it) }
        val anyFloated = floated.any { it }
        // Pair each child's role with its Height/BlockSize declaration —
        // presence is the signal (the measure pass supplies the value).
        return head + (children ?: emptyList()).mapIndexed { index, child ->
            ChildSpec(
                roles[index + head.size],
                child.properties.any { it.type == "Height" || it.type == "BlockSize" },
                // Wave-42: the run fragmenter's float bail rides this flag
                // (see ChildSpec.floatedContent for why floats disqualify).
                floated[index],
                // Wave-42: the run fragmenter's FORCED-BREAK bail (see
                // ChildSpec.forcedBreakContent) — a break the container-
                // level role list cannot see, because it sits on the child
                // itself as `break-before` or anywhere below it.
                hasHiddenForcedColumnBreak(child),
                // Wave-42: empty box ⇒ no class-A/B breakpoint inside it ⇒
                // MONOLITHIC (see ChildSpec.monolithicContent). Text counts
                // as content because line boxes ARE class-B break points,
                // and generated ::before/::after content is content too
                // (the wire field is `_text` on this platform, `text` on
                // the iOS twin — same three inputs, same predicate).
                child.children.isNullOrEmpty() &&
                    child._text.isNullOrEmpty() &&
                    child.pseudos?.isNotEmpty() != true,
                // Wave-44 lane U8: the float-strip facts (or the strict
                // null bail) — computed only for containers with a float
                // somewhere (see the `anyFloated` gate above).
                if (anyFloated) MulticolFloatStrip.factsFor(child) else null,
                // Wave-46 lane Y3: the css-break-3 §5.2 clone signal + its
                // resolved decoration bands (null for slice children, so
                // every non-clone container's spec is byte-identical).
                MulticolCloneDecoration.declaresClone(child),
                MulticolCloneDecoration.bandsFor(child)
            )
        }
    }

    /**
     * True when a forced COLUMN break lives in [component]'s box tree in a
     * place the container's [rolesFor] classification cannot express, i.e.
     * everywhere except the child's own `break-after` (which becomes
     * [Role.FLOW_BREAK_AFTER]):
     *  - the child's own `break-before: column` (css-break-3 §4.1 — the
     *    break happens BEFORE this box, so the preceding sibling's column
     *    ends, which the after-only role list never encodes);
     *  - any `break-before`/`break-after: column` on a DESCENDANT — the
     *    WPT block-in-inline-013/014 shape puts them on divs inside
     *    anonymous `<span>` wrappers, so the multicol's direct children
     *    (the spans) look like plain flow while Chromium's one-green-box-
     *    per-column reference comes ENTIRELY from honoring them.
     *
     * Consumers treat this as "this child's block-axis run has break points
     * I cannot model" and keep the legacy whole-child distribution, which
     * on those two fixtures happens to place one child per column — the
     * render the forced breaks ask for.
     */
    private fun hasHiddenForcedColumnBreak(component: IRComponent): Boolean =
        // The child's OWN break-before (its own break-after is the role).
        forcedColumnBreak(component, "BreakBefore") ||
            // …then the whole subtree, both directions.
            component.children?.any { hasForcedColumnBreakDeep(it) } == true

    /** [hasHiddenForcedColumnBreak]'s subtree half — both break sides count. */
    private fun hasForcedColumnBreakDeep(component: IRComponent): Boolean =
        forcedColumnBreak(component, "BreakBefore") ||
            forcedColumnBreak(component, "BreakAfter") ||
            component.children?.any { hasForcedColumnBreakDeep(it) } == true

    /**
     * Whether [component] declares [property] (`BreakBefore`/`BreakAfter`)
     * with the `column` keyword — the only value that FORCES a multicol
     * break (css-break-3 §4.1); `page`/`region` target other fragmentation
     * contexts and `avoid*` is avoidance, not a forced break.
     */
    private fun forcedColumnBreak(component: IRComponent, property: String): Boolean =
        component.properties.firstOrNull { it.type == property }
            ?.let { ValueExtractors.extractKeyword(it.data)?.uppercase() } == "COLUMN"

    /**
     * True when [component] or ANY descendant declares `float: left/right`
     * (the IR `Float` keyword — see the converter's FloatPropertyParser).
     * `none` (and unknown keywords) do not count: only an actually floated
     * box triggers the run fragmenter's float bail.
     */
    private fun hasFloatedDescendant(component: IRComponent): Boolean =
        // The component's own declaration first (cheap, no recursion)…
        component.properties.any {
            it.type == "Float" &&
                ValueExtractors.extractKeyword(it.data)?.uppercase() in FLOAT_SIDES
        } ||
            // …then the subtree — a float any depth down still means the
            // stacked-not-floated layout defect lives inside this child.
            component.children?.any { hasFloatedDescendant(it) } == true

    /** The two floated `float` keywords (CSS 2.1 §9.5.1) — `none` excluded. */
    private val FLOAT_SIDES = setOf("LEFT", "RIGHT")

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
        // Wave-42: a forced column break always takes the plan too — the
        // greedy min-height heuristic cannot honor css-break-3 §4.1 (it
        // would pack post-break content wherever is shortest). For the
        // ≤N-chunks / one-child-per-chunk shapes the corpus's GREEN cells
        // exercise (balance-break-avoidance-001), the chunk walk provably
        // reproduces the greedy assignment (pinned BRK4), so engaging is
        // render-neutral there and correct everywhere else.
        if (children.any { it.role == Role.FLOW_BREAK_AFTER }) return true
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
        // The replay slices exactly one continuous child paint (a forced-
        // break sole child counts — its chunk is its whole extent).
        children.count { it.role.isFlow } == 1 &&
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
            // Wave-42: a segment with FORCED breaks places one whole chunk
            // per column (H = the tallest chunk), so no flow child can
            // straddle a column boundary by construction — skip it.
            if (segment.any { it.role == Role.FLOW_BREAK_AFTER }) return@forEachSegment
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

    /**
     * The plan itself — see the class doc for the model. SP-table pinned.
     *
     * Wave-42 lane W4 additions:
     *  - a segment containing [Role.FLOW_BREAK_AFTER] children is split
     *    into CHUNKS at the forced breaks (css-break-3 §4.1); chunk j owns
     *    column j whole, and the segment's used column block-size is the
     *    TALLEST chunk (css-multicol-1 §7.1: forced breaks become the only
     *    break opportunities). Chunks past column N-1 land in §8.2
     *    OVERFLOW columns (columnIndex ≥ N — painted past the inline end).
     *  - [discardOverflow] = the container declared `continue: discard`
     *    (css-overflow-4 §3): content from the FIRST overflow column on —
     *    and everything after it in flow order, later spanners and
     *    segments included — is marked [Slot.discarded] and contributes no
     *    container block-size. Default false keeps every pre-wave-42
     *    caller (and the whole SP pin table) byte-identical.
     */
    fun plan(children: List<Child>, columnCount: Int, discardOverflow: Boolean = false): Plan {
        // §3.2 used counts are ≥ 1; floor defensively for direct callers.
        val n = maxOf(1, columnCount)
        val slots = ArrayList<Slot>(children.size)
        // Running container block offset (spanner tops / segment starts).
        var y = 0
        // Sole-flow bookkeeping for the replay fragmentainer.
        val soleFlow = children.count { it.role.isFlow } == 1
        var soleFlowH: Int? = null
        // css-overflow-4 §3 latch: once discard triggers, EVERYTHING after
        // is dropped — the flag never resets within one container.
        var discarding = false
        var i = 0
        while (i < children.size) {
            val c = children[i]
            // Post-discard tail: emit an index-aligned discarded slot (the
            // y is the frozen flow bottom — placement skips it anyway).
            if (discarding) {
                slots.add(Slot(c.role, 0, y, discarded = true))
                i++
                continue
            }
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
                if (children[j].role.isFlow) t += maxOf(0, children[j].heightPx)
                j++
            }
            if (children.subList(i, j).any { it.role == Role.FLOW_BREAK_AFTER }) {
                // ── Wave-42 CHUNK walk (forced breaks present) ─────────
                // col = the chunk's column; r = flow offset INSIDE the
                // current chunk; h = tallest RETAINED (col < N) chunk.
                var col = 0
                var r = 0
                var h = 0
                for (k in i until j) {
                    val ck = children[k]
                    // css-overflow-4 §3: the first overflow column starts
                    // the discard — from here on everything drops.
                    if (discardOverflow && col >= n) discarding = true
                    if (discarding) {
                        slots.add(Slot(ck.role, 0, y, discarded = true))
                        continue
                    }
                    // The chunk owns its column whole: block offset = the
                    // running flow offset within the chunk.
                    slots.add(Slot(ck.role, col, y + r))
                    if (ck.role.isFlow) r += maxOf(0, ck.heightPx)
                    if (ck.role == Role.FLOW_BREAK_AFTER) {
                        // §7.1 forced-break balancing: only chunks that got
                        // a real column grow the container's block-size —
                        // §8.2 overflow columns never do.
                        if (col < n) h = maxOf(h, r)
                        // The forced break: next content opens a new chunk.
                        col++
                        r = 0
                    }
                }
                // The trailing (breakless) chunk, when it kept a column.
                if (!discarding && col < n) h = maxOf(h, r)
                // A sole flow child's fragmentainer is its segment H (the
                // replay gate needs C > H, which a whole chunk never is).
                if (soleFlow && children.subList(i, j).any { it.role.isFlow }) soleFlowH = h
                // The segment occupies the tallest retained chunk.
                y += h
                i = j
                continue
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
