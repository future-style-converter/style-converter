package com.styleconverter.test.style.layout.position

// Phase 7b position style-engine applier.
//
// Consumes the position + inset + zIndex fields of the aggregate
// [com.styleconverter.test.style.layout.LayoutConfig] and returns a Compose
// [Modifier] chain. The legacy [PositionApplier] remains authoritative for
// the existing ComponentRenderer path — this applier exists so the new
// style-engine LayoutConfig surface is runnable end-to-end, even though we
// haven't flipped the renderer over yet.

import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.styleconverter.test.style.layout.InsetRect
import com.styleconverter.test.style.layout.LayoutConfig
import com.styleconverter.test.style.layout.PositionKind

object PositionLayoutApplier {

    /**
     * Produce the Compose [Modifier] contribution for the child-side
     * position properties (z-index + offset for relative/absolute/fixed).
     *
     * Modifier composition order (chained left→right):
     *   1. zIndex — must wrap whatever offset follows so the stacking
     *      order is established at this node (not the offsetted one).
     *   2. offset/absoluteOffset — physical displacement from the
     *      parent's position.
     *
     * Sticky is intentionally a no-op: Compose has stickyHeader only for
     * LazyColumn, and cross-cutting sticky requires a custom Layout that's
     * out of scope here.
     */
    fun childModifier(config: LayoutConfig): Modifier {
        var m: Modifier = Modifier

        // zIndex first — applies regardless of position kind per CSS.
        config.zIndex?.let { m = m.zIndex(it.toFloat()) }

        // Offset logic depends on the position kind.
        val kind = config.position ?: PositionKind.Static
        val inset = config.inset
        if (inset == null || inset == InsetRect.Auto) return m

        when (kind) {
            PositionKind.Static -> {
                // CSS spec: offsets are ignored on static — no-op.
            }
            PositionKind.Relative -> {
                // Relative: offset from normal flow. left > right, top > bottom
                // (left/top take precedence when both specified). Negative
                // values (from right/bottom) are the convention for
                // "distance from the far edge."
                val x = (inset.left ?: inset.right?.let { -it } ?: 0f).dp
                val y = (inset.top ?: inset.bottom?.let { -it } ?: 0f).dp
                if (x.value != 0f || y.value != 0f) m = m.offset(x = x, y = y)
            }
            PositionKind.Absolute, PositionKind.Fixed -> {
                // Approximation: parent must be a Box for true absolute
                // positioning. Here we emit absoluteOffset so the child
                // ignores layoutDirection (CSS `left` means physical left,
                // not start). Right/bottom collapse to negative offsets,
                // acceptable for top-left alignment within a Box parent.
                val x = (inset.left ?: inset.right?.let { -it } ?: 0f).dp
                val y = (inset.top ?: inset.bottom?.let { -it } ?: 0f).dp
                if (x.value != 0f || y.value != 0f) m = m.absoluteOffset(x = x, y = y)
                // TODO(phase7b): a wrapping Box needs to be injected by the
                // ComponentRenderer when any child has Absolute/Fixed.
                // Tracked in absolute-positioned-children fixture.
            }
            PositionKind.Sticky -> {
                // TODO(phase7b): Compose has no cross-container sticky.
                // stickyHeader only exists inside LazyColumn. Leaving as
                // no-op + legacy fall-through for now.
            }
        }

        return m
    }
}
