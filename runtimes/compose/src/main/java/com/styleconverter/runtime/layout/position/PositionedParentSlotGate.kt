package com.styleconverter.runtime.layout.position

// Wave 55 (lane L3, positioned-parent-slot) — WHEN the positioned-parent
// static-position flow slot (PositionedParentFlowSlot) mounts.
//
// ## The measured defect (wave54-final selectors/dir-style-02a android f 0.9425)
// The mount was gated on CanvasRootHoist.LocalActive alone, which only an
// ACTIVATED Host provides (CanvasRootHoist.hostActivates: some box hoists
// or is RC1). A composed capture whose host stayed inactive kept the full
// Column footprint for every out-of-flow child of an `absolute | fixed |
// sticky` parent: dir-style-02a's row-7 dot (a `left: 0` run after a
// 20-px-tall run) got 0 px of remaining height and never painted. The lane
// census finds 53 such parents in 17 host-inactive documents.
//
// ## The fix (U1) — engage on the composed capture itself
// CSS 2.1 §9.3.1 / §9.6, css-position-3 §2.1: an absolutely positioned box
// is out of flow whatever else the document holds, so the gate is "a
// composed WPT capture" (LocalWptComposedMode), not "the host activated".
// LocalActive implies it (both are provided only inside the harness's
// ComposedCaptureCanvas), so the dark stage, the inbox path and every
// committed baseline see both false: the identity modifier, byte for byte.
//
// ## The RTL guard (why `!parentRtl`)
// The mount reports 0×0 and an RTL Column aligns a 0-wide slot at its END
// edge (Alignment.Start under Rtl = `space − size`): a mounted child of an
// RTL parent lands at W + left (device-measured on the host-ACTIVE
// dynamic-offset-rtl-001: magenta x186 = 16 + 100 + 70, ref x86). Mounting
// RTL parents without a correction ("PLAIN") passes dir-style-02a
// DEGENERATE at 0.9663 with rows 6/9 blank — never shipped. So a
// host-inactive RTL parent stays UNMOUNTED, with a PropertyTracker
// breadcrumb (no silent fallthrough); an active host keeps today's mount.
//
// ## U2 — the RTL branch
// An RTL parent whose slot PositionedParentRtlOrigin can correct (a px
// Width, one physical inline inset) takes the mount WITH the physical-left
// origin, under any host state of a composed capture: host-inactive
// (dir-style-02a rows 6/9, bidi-tab-001) and host-active
// (dynamic-offset-*rtl*, which mounted at W + left until now). An RTL parent
// the origin declines keeps today's slot (mounted under an active host,
// withheld otherwise) and records the decline's own breadcrumb.
//
// Pure decisions only (JVM-pinned by PositionedParentSlotGateTest, no
// Robolectric in this suite); the measurement is PositionedParentFlowSlot
// .mount → CanvasRootHoist.zeroFlowAnchor, unchanged.

import androidx.compose.ui.Modifier
import com.styleconverter.runtime.PropertyTracker
import com.styleconverter.runtime.core.ir.IRProperty

object PositionedParentSlotGate {

    /**
     * The breadcrumb an RTL parent's withheld mount records. Namespaced like
     * `ContainingBlock[…]` (OutOfFlowContainingBlock) so it can never collide
     * with a real IR property type in `PropertyTracker.getReport()`.
     */
    const val RTL_UNMOUNTED_BREADCRUMB = "PositionedParentSlot[rtl-parent-unmounted]"

    /**
     * What the seam installs on one out-of-flow child's itemModifier channel.
     * Named `SlotMount`, never a generic `Slot`: SeamReachabilityTest resolves
     * every declared type name corpus-wide, so a common nested name would
     * make this file look reachable with seam 1 un-applied (executed: the
     * first cut's `enum class Slot` kept that pin green — lane note).
     */
    enum class SlotMount {
        /** The identity Modifier: the child keeps its legacy Column footprint. */
        IDENTITY,

        /** PositionedParentFlowSlot.mount: zero footprint at the slot origin. */
        MOUNT,

        /** The mount plus the RTL parent's physical-left origin (U2). */
        MOUNT_RTL_ORIGIN,
    }

    /**
     * Does the zero-footprint mount engage for a child that
     * [PositionedParentFlowSlot.applies] to?
     *
     * Truth table (pinned row by row in PositionedParentSlotGateTest):
     *  - an active host mounts every parent, as before this wave;
     *  - a host-inactive composed capture mounts an LTR parent;
     *  - an RTL parent under an inactive host is withheld (file header);
     *  - no composed capture (dark stage, inbox path) never mounts.
     */
    fun engages(hostActive: Boolean, composedCapture: Boolean, parentRtl: Boolean): Boolean =
        // `hostActive` first: it keeps every pre-wave-55 mount unchanged.
        hostActive || (composedCapture && !parentRtl)

    /**
     * The whole per-child decision, pure over the IR and three flags read at
     * the seam: [parentRtl] is the PARENT's laid-out direction
     * (`LocalLayoutDirection` inside its content scope), never the child's —
     * a `direction: rtl` child under an LTR parent provides Rtl only around
     * its own content, and its slot is placed by the LTR parent's Column.
     */
    fun decide(
        childProperties: List<IRProperty>,
        parentProperties: List<IRProperty>,
        hostActive: Boolean,
        composedCapture: Boolean,
        parentRtl: Boolean,
    ): SlotMount {
        // Not an out-of-flow child of a Column-loop containing block: the
        // slot has nothing to say (the frozen loop owns it).
        if (!PositionedParentFlowSlot.applies(childProperties, parentProperties)) return SlotMount.IDENTITY
        // U2: an RTL parent's slot the origin can correct, in any host state.
        if (rtlOriginEngages(hostActive, composedCapture, parentRtl) &&
            PositionedParentRtlOrigin.applies(parentRtl, parentProperties, childProperties)
        ) return SlotMount.MOUNT_RTL_ORIGIN
        // Otherwise the engagement table above decides.
        return if (engages(hostActive, composedCapture, parentRtl)) SlotMount.MOUNT else SlotMount.IDENTITY
    }

    /**
     * U2's engagement row: an RTL parent of a composed capture, whatever the
     * host (an active host implies a composed capture; the OR keeps the
     * pre-wave-55 mount's host row explicit). Never the dark stage.
     */
    internal fun rtlOriginEngages(
        hostActive: Boolean,
        composedCapture: Boolean,
        parentRtl: Boolean,
    ): Boolean =
        // Physical-left origin only where an RTL Column places the slot.
        parentRtl && (hostActive || composedCapture)

    /**
     * True when the RTL guard — not the host or the capture mode — withheld
     * a mount the slot applies to: the case the breadcrumb names.
     */
    internal fun withheldByRtlGuard(
        hostActive: Boolean,
        composedCapture: Boolean,
        parentRtl: Boolean,
    ): Boolean =
        // Exactly the one row of the truth table that the guard flips.
        !hostActive && composedCapture && parentRtl

    /**
     * The call-site helper ComponentRenderer's renderBlockChild rides on its
     * itemModifier channel: [decide], then the mount or the identity, plus
     * the breadcrumb when the RTL guard withheld the mount.
     */
    fun slotModifier(
        childProperties: List<IRProperty>,
        parentProperties: List<IRProperty>,
        hostActive: Boolean,
        composedCapture: Boolean,
        parentRtl: Boolean,
    ): Modifier = when (decide(childProperties, parentProperties, hostActive, composedCapture, parentRtl)) {
        // U2: the mount with its RTL physical-left origin.
        SlotMount.MOUNT_RTL_ORIGIN -> PositionedParentFlowSlot.mount(childProperties, rtlOrigin = true)
        // The wave-46 mount, unchanged (PositionedParentFlowSlot.mount) —
        // after naming why an RTL parent's origin declined, if one did.
        SlotMount.MOUNT -> {
            recordRtlOriginDecline(childProperties, parentProperties, hostActive, composedCapture, parentRtl)
            PositionedParentFlowSlot.mount(childProperties)
        }
        // Identity — and, when it was the RTL guard that withheld an
        // applying mount, say so (no silent fallthrough).
        SlotMount.IDENTITY -> {
            // An RTL parent's declined origin is named first (U2).
            recordRtlOriginDecline(childProperties, parentProperties, hostActive, composedCapture, parentRtl)
            // Only an applying child is a withheld mount; a non-applying
            // child was never the slot's to mount.
            if (PositionedParentFlowSlot.applies(childProperties, parentProperties) &&
                withheldByRtlGuard(hostActive, composedCapture, parentRtl)
            ) {
                PropertyTracker.markUnhandled(RTL_UNMOUNTED_BREADCRUMB)
            }
            // The identity Modifier keeps today's Column footprint.
            Modifier
        }
    }

    /**
     * U2's no-silent-fallthrough: an applying child of an RTL parent whose
     * origin DECLINED (PositionedParentRtlOrigin's reasons) records that
     * reason's breadcrumb — the slot keeps today's placement there.
     */
    private fun recordRtlOriginDecline(
        childProperties: List<IRProperty>,
        parentProperties: List<IRProperty>,
        hostActive: Boolean,
        composedCapture: Boolean,
        parentRtl: Boolean,
    ) {
        // Only the RTL-origin row can decline; every other row is silent.
        if (!rtlOriginEngages(hostActive, composedCapture, parentRtl)) return
        // A non-applying child was never the slot's.
        if (!PositionedParentFlowSlot.applies(childProperties, parentProperties)) return
        // The decline's own key (NONE / NOT_RTL carry none).
        PositionedParentRtlOrigin.decline(parentRtl, parentProperties, childProperties)
            .breadcrumb?.let { PropertyTracker.markUnhandled(it) }
    }
}
