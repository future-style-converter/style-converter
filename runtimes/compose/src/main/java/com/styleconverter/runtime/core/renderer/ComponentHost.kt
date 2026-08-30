package com.styleconverter.runtime.core.renderer

// ComponentHost — the runtime shim of the v2 contract (design §3.1): the
// single entry point a composer (harness screen or SDUI shell) uses to
// render ONE component. For EVERY component it derives the ITEM-scoped
// placement union once and attaches it as Compose parent-data
// (Modifier.itemPlacement) before delegating to the style engine's
// ComponentRenderer. Parent-data is only read by whoever measures the
// node, so the claim is inert under a plain host Box and active under a
// placement-aware container — the child never knows its destination.
//
// The host rides RenderComponent's existing itemModifier channel (the
// outermost slot of the component's own modifier chain), adding ZERO new
// layout nodes — a parent-data modifier has no measure/draw effect of its
// own, which is what keeps this wiring pixel-identical to the pre-v2
// render path (the campaign baseline freeze).

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.placement.ItemPlacementExtractor
import com.styleconverter.runtime.core.placement.itemPlacement

object ComponentHost {

    /**
     * Render one component with its placement claims published as
     * parent-data. Children arrive already composed on the component
     * (SlotComposer output) — the renderer walks them as slot content.
     */
    @Composable
    fun Render(component: IRComponent) {
        // css-cascade-5 §7.3.2 ("Explicit Inheritance: the inherit
        // keyword") makes `background-color: inherit` compute to the
        // INHERITED VALUE, which §7.2 defines as the computed value of the
        // property on the parent element — something no extractor can see
        // (it is handed one component's declarations). Resolve it here, once, over the whole
        // composed subtree — this shim is the single root entry point, and
        // the renderer walks the children off the component it is given, so
        // one pass covers the tree. Identity pass-through for any tree that
        // does not use the keyword. See BackgroundColorInheritance.
        val prepared = remember(component) {
            com.styleconverter.runtime.color.BackgroundColorInheritance.resolve(component)
        }
        // Extract once per component identity — the claims are pure
        // functions of the IR properties.
        val placement = remember(prepared) {
            ItemPlacementExtractor.extract(prepared.properties)
        }
        ComponentRenderer.RenderComponent(
            prepared,
            // itemModifier is prepended OUTERMOST in RenderComponent's
            // style chain, so the parent-data lands on the component's
            // root layout node — the measurable its container sees.
            itemModifier = Modifier.itemPlacement(placement)
        )
    }
}
