package com.styleconverter.test.style.layout.flexbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Helper functions to convert flex config to Compose Arrangement/Alignment.
 */
object FlexLayoutHelper {

    /**
     * Get horizontal arrangement for Row based on justify-content.
     *
     * @param config The flex container configuration
     * @param gap Optional gap between items
     * @return Horizontal arrangement for Row composable
     */
    fun getHorizontalArrangement(config: FlexContainerConfig, gap: Dp = 0.dp): Arrangement.Horizontal {
        return when (config.justifyContent) {
            JustifyContent.FLEX_START -> if (gap > 0.dp) Arrangement.spacedBy(gap, Alignment.Start) else Arrangement.Start
            JustifyContent.FLEX_END -> if (gap > 0.dp) Arrangement.spacedBy(gap, Alignment.End) else Arrangement.End
            JustifyContent.CENTER -> if (gap > 0.dp) Arrangement.spacedBy(gap, Alignment.CenterHorizontally) else Arrangement.Center
            JustifyContent.SPACE_BETWEEN -> Arrangement.SpaceBetween
            JustifyContent.SPACE_AROUND -> Arrangement.SpaceAround
            JustifyContent.SPACE_EVENLY -> Arrangement.SpaceEvenly
        }
    }

    /**
     * Get vertical arrangement for Column based on justify-content.
     *
     * @param config The flex container configuration
     * @param gap Optional gap between items
     * @return Vertical arrangement for Column composable
     */
    fun getVerticalArrangement(config: FlexContainerConfig, gap: Dp = 0.dp): Arrangement.Vertical {
        return when (config.justifyContent) {
            JustifyContent.FLEX_START -> if (gap > 0.dp) Arrangement.spacedBy(gap, Alignment.Top) else Arrangement.Top
            JustifyContent.FLEX_END -> if (gap > 0.dp) Arrangement.spacedBy(gap, Alignment.Bottom) else Arrangement.Bottom
            JustifyContent.CENTER -> if (gap > 0.dp) Arrangement.spacedBy(gap, Alignment.CenterVertically) else Arrangement.Center
            JustifyContent.SPACE_BETWEEN -> Arrangement.SpaceBetween
            JustifyContent.SPACE_AROUND -> Arrangement.SpaceAround
            JustifyContent.SPACE_EVENLY -> Arrangement.SpaceEvenly
        }
    }

    /**
     * Get vertical alignment for Row children based on align-items.
     *
     * @param config The flex container configuration
     * @return Vertical alignment for Row's verticalAlignment parameter
     */
    fun getVerticalAlignment(config: FlexContainerConfig): Alignment.Vertical {
        return when (config.alignItems) {
            AlignItems.FLEX_START -> Alignment.Top
            AlignItems.FLEX_END -> Alignment.Bottom
            AlignItems.CENTER -> Alignment.CenterVertically
            AlignItems.BASELINE -> Alignment.Top // Compose doesn't have baseline for Row
            AlignItems.STRETCH -> Alignment.CenterVertically // fillMaxHeight on children
        }
    }

    /**
     * Get horizontal alignment for Column children based on align-items.
     *
     * @param config The flex container configuration
     * @return Horizontal alignment for Column's horizontalAlignment parameter
     */
    fun getHorizontalAlignment(config: FlexContainerConfig): Alignment.Horizontal {
        return when (config.alignItems) {
            AlignItems.FLEX_START -> Alignment.Start
            AlignItems.FLEX_END -> Alignment.End
            AlignItems.CENTER -> Alignment.CenterHorizontally
            AlignItems.BASELINE -> Alignment.Start
            AlignItems.STRETCH -> Alignment.CenterHorizontally // fillMaxWidth on children
        }
    }

    /**
     * Get vertical alignment for a flex item based on align-self.
     * Falls back to container's align-items if align-self is AUTO.
     *
     * @param itemConfig The flex item configuration
     * @param containerConfig The parent flex container configuration
     * @return Vertical alignment for the item
     */
    fun getItemVerticalAlignment(itemConfig: FlexItemConfig, containerConfig: FlexContainerConfig): Alignment.Vertical {
        return when (itemConfig.alignSelf) {
            AlignSelf.AUTO -> getVerticalAlignment(containerConfig)
            AlignSelf.FLEX_START -> Alignment.Top
            AlignSelf.FLEX_END -> Alignment.Bottom
            AlignSelf.CENTER -> Alignment.CenterVertically
            AlignSelf.BASELINE -> Alignment.Top
            AlignSelf.STRETCH -> Alignment.CenterVertically
        }
    }

    /**
     * Get horizontal alignment for a flex item based on align-self.
     * Falls back to container's align-items if align-self is AUTO.
     *
     * @param itemConfig The flex item configuration
     * @param containerConfig The parent flex container configuration
     * @return Horizontal alignment for the item
     */
    fun getItemHorizontalAlignment(itemConfig: FlexItemConfig, containerConfig: FlexContainerConfig): Alignment.Horizontal {
        return when (itemConfig.alignSelf) {
            AlignSelf.AUTO -> getHorizontalAlignment(containerConfig)
            AlignSelf.FLEX_START -> Alignment.Start
            AlignSelf.FLEX_END -> Alignment.End
            AlignSelf.CENTER -> Alignment.CenterHorizontally
            AlignSelf.BASELINE -> Alignment.Start
            AlignSelf.STRETCH -> Alignment.CenterHorizontally
        }
    }

    /**
     * Determine if children should stretch to fill cross-axis.
     *
     * @param config The flex container configuration
     * @return True if children should use fillMaxWidth/fillMaxHeight
     */
    fun shouldStretchChildren(config: FlexContainerConfig): Boolean {
        return config.alignItems == AlignItems.STRETCH
    }

    /**
     * Determine if a specific item should stretch based on align-self.
     *
     * @param itemConfig The flex item configuration
     * @param containerConfig The parent flex container configuration
     * @return True if the item should stretch
     */
    fun shouldItemStretch(itemConfig: FlexItemConfig, containerConfig: FlexContainerConfig): Boolean {
        return when (itemConfig.alignSelf) {
            AlignSelf.AUTO -> containerConfig.alignItems == AlignItems.STRETCH
            AlignSelf.STRETCH -> true
            else -> false
        }
    }
}
