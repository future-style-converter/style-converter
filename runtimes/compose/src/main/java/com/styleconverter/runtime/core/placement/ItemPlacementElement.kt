package com.styleconverter.runtime.core.placement

// ItemPlacementElement — the Compose parent-data carrier of the v2
// placement contract (design §3.1): every component gets
// `Modifier.itemPlacement(...)` applied unconditionally by ComponentHost.
// Parent-data is only read by whoever MEASURES the node, so the claim is
// inert under a plain Box/Column and active under a placement-aware
// Layout (the campaign's GridPlacedRow today; StyleGrid/StyleFlex custom
// Layouts as they land) — the child never knows its destination, the
// container never knows its arrivals until measure time.

import androidx.compose.ui.Modifier
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ParentDataModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density

/**
 * Modifier node that publishes the component's [ItemPlacement] as Compose
 * parent-data (androidx.compose.ui.node.ParentDataModifierNode — the
 * modern ModifierNodeElement form of ParentDataModifier).
 */
private class ItemPlacementNode(
    var placement: ItemPlacement
) : Modifier.Node(), ParentDataModifierNode {
    // Contract: never CLOBBER parent-data some inner modifier already
    // produced (RowScope.weight / BoxScope.align etc. fold their own
    // types through this chain). A component's own style chain carries no
    // other parent-data today, so this is a pure safety net that keeps
    // the wiring provably inert under the existing containers — the
    // pixel-freeze requirement of the v2 renderer refactor.
    override fun Density.modifyParentData(parentData: Any?): Any? =
        parentData ?: placement
}

/**
 * Element wrapper — data class so structural equality drives node reuse
 * (ModifierNodeElement requires equals/hashCode; recomposition with an
 * unchanged placement skips the update pass entirely).
 */
private data class ItemPlacementElement(
    val placement: ItemPlacement
) : ModifierNodeElement<ItemPlacementNode>() {
    override fun create(): ItemPlacementNode = ItemPlacementNode(placement)
    override fun update(node: ItemPlacementNode) {
        node.placement = placement
    }
    override fun InspectorInfo.inspectableProperties() {
        name = "itemPlacement"
        value = placement
    }
}

/**
 * Attach this component's ITEM-scoped placement claims as parent-data.
 * Applied unconditionally by ComponentHost (design §3.1) — inert unless
 * a placement-aware container measures the node.
 */
fun Modifier.itemPlacement(placement: ItemPlacement): Modifier =
    this.then(ItemPlacementElement(placement))
