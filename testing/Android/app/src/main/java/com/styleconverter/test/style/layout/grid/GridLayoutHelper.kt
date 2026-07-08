package com.styleconverter.test.style.layout.grid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

/**
 * Helper functions to convert GridConfig to Compose layout components.
 */
object GridLayoutHelper {

    /**
     * Convert GridConfig to Compose GridCells.
     *
     * Compose's LazyVerticalGrid/LazyHorizontalGrid uses GridCells to define
     * the number and size of columns/rows.
     *
     * @param config The grid configuration
     * @return GridCells for use with LazyVerticalGrid
     */
    fun toGridCells(config: GridConfig): GridCells {
        if (config.templateColumns.isEmpty()) {
            return GridCells.Fixed(1)
        }

        // If all columns are equal fractions, use Fixed
        val allFractions = config.templateColumns.all { it is GridTrackSize.Fraction }
        if (allFractions) {
            return GridCells.Fixed(config.templateColumns.size)
        }

        // If all columns are equal fixed sizes, use Adaptive
        val allFixed = config.templateColumns.all { it is GridTrackSize.Fixed }
        if (allFixed) {
            val firstSize = (config.templateColumns.first() as GridTrackSize.Fixed).size
            val allSame = config.templateColumns.all {
                (it as? GridTrackSize.Fixed)?.size == firstSize
            }
            if (allSame) {
                return GridCells.Adaptive(firstSize)
            }
        }

        // Default to fixed column count
        return GridCells.Fixed(config.columnCount)
    }

    /**
     * Get horizontal arrangement from grid config.
     *
     * Maps CSS justify-content to Compose Arrangement.Horizontal.
     *
     * @param config The grid configuration
     * @return Arrangement.Horizontal for use with grid layout
     */
    fun getHorizontalArrangement(config: GridConfig): Arrangement.Horizontal {
        val gap = config.columnGap ?: 0.dp
        return when (config.justifyContent) {
            GridJustifyContent.START -> Arrangement.spacedBy(gap, Alignment.Start)
            GridJustifyContent.END -> Arrangement.spacedBy(gap, Alignment.End)
            GridJustifyContent.CENTER -> Arrangement.spacedBy(gap, Alignment.CenterHorizontally)
            GridJustifyContent.SPACE_BETWEEN -> Arrangement.SpaceBetween
            GridJustifyContent.SPACE_AROUND -> Arrangement.SpaceAround
            GridJustifyContent.SPACE_EVENLY -> Arrangement.SpaceEvenly
            GridJustifyContent.STRETCH -> Arrangement.spacedBy(gap)
        }
    }

    /**
     * Get vertical arrangement from grid config.
     *
     * Maps CSS align-content to Compose Arrangement.Vertical.
     *
     * @param config The grid configuration
     * @return Arrangement.Vertical for use with grid layout
     */
    fun getVerticalArrangement(config: GridConfig): Arrangement.Vertical {
        val gap = config.rowGap ?: 0.dp
        return when (config.alignContent) {
            GridAlignContent.START -> Arrangement.spacedBy(gap, Alignment.Top)
            GridAlignContent.END -> Arrangement.spacedBy(gap, Alignment.Bottom)
            GridAlignContent.CENTER -> Arrangement.spacedBy(gap, Alignment.CenterVertically)
            GridAlignContent.SPACE_BETWEEN -> Arrangement.SpaceBetween
            GridAlignContent.SPACE_AROUND -> Arrangement.SpaceAround
            GridAlignContent.SPACE_EVENLY -> Arrangement.SpaceEvenly
            GridAlignContent.STRETCH -> Arrangement.spacedBy(gap)
        }
    }

    /**
     * Get item span from grid item config.
     *
     * Used with LazyGridScope.item() to specify how many columns an item spans.
     *
     * @param itemConfig The grid item configuration
     * @param columnCount Total number of columns in the grid
     * @return GridItemSpan for the item
     */
    fun getItemSpan(itemConfig: GridItemConfig, columnCount: Int): GridItemSpan {
        val span = itemConfig.columnSpan.coerceIn(1, columnCount)
        return GridItemSpan(span)
    }

    /**
     * Get item alignment from grid item config.
     *
     * Combines justify-self and align-self into a Compose Alignment.
     *
     * @param itemConfig The grid item configuration
     * @return Alignment for the item within its cell
     */
    fun getItemAlignment(itemConfig: GridItemConfig): Alignment {
        val horizontal = when (itemConfig.justifySelf) {
            GridJustify.START -> Alignment.Start
            GridJustify.END -> Alignment.End
            GridJustify.CENTER -> Alignment.CenterHorizontally
            else -> Alignment.CenterHorizontally
        }

        val vertical = when (itemConfig.alignSelf) {
            GridAlign.START -> Alignment.Top
            GridAlign.END -> Alignment.Bottom
            GridAlign.CENTER -> Alignment.CenterVertically
            else -> Alignment.CenterVertically
        }

        return when {
            horizontal == Alignment.Start && vertical == Alignment.Top -> Alignment.TopStart
            horizontal == Alignment.Start && vertical == Alignment.Bottom -> Alignment.BottomStart
            horizontal == Alignment.End && vertical == Alignment.Top -> Alignment.TopEnd
            horizontal == Alignment.End && vertical == Alignment.Bottom -> Alignment.BottomEnd
            horizontal == Alignment.CenterHorizontally && vertical == Alignment.Top -> Alignment.TopCenter
            horizontal == Alignment.CenterHorizontally && vertical == Alignment.Bottom -> Alignment.BottomCenter
            horizontal == Alignment.Start -> Alignment.CenterStart
            horizontal == Alignment.End -> Alignment.CenterEnd
            else -> Alignment.Center
        }
    }

    /**
     * Get default alignment from grid container config.
     *
     * Maps justify-items and align-items to a default Alignment for all items.
     *
     * @param config The grid configuration
     * @return Default Alignment for items in the grid
     */
    fun getDefaultItemAlignment(config: GridConfig): Alignment {
        val horizontal = when (config.justifyItems) {
            GridJustify.START -> Alignment.Start
            GridJustify.END -> Alignment.End
            GridJustify.CENTER -> Alignment.CenterHorizontally
            GridJustify.STRETCH -> Alignment.CenterHorizontally
        }

        val vertical = when (config.alignItems) {
            GridAlign.START -> Alignment.Top
            GridAlign.END -> Alignment.Bottom
            GridAlign.CENTER -> Alignment.CenterVertically
            GridAlign.STRETCH -> Alignment.CenterVertically
            GridAlign.BASELINE -> Alignment.Top // Baseline approximation
        }

        return when {
            horizontal == Alignment.Start && vertical == Alignment.Top -> Alignment.TopStart
            horizontal == Alignment.Start && vertical == Alignment.Bottom -> Alignment.BottomStart
            horizontal == Alignment.End && vertical == Alignment.Top -> Alignment.TopEnd
            horizontal == Alignment.End && vertical == Alignment.Bottom -> Alignment.BottomEnd
            horizontal == Alignment.CenterHorizontally && vertical == Alignment.Top -> Alignment.TopCenter
            horizontal == Alignment.CenterHorizontally && vertical == Alignment.Bottom -> Alignment.BottomCenter
            horizontal == Alignment.Start -> Alignment.CenterStart
            horizontal == Alignment.End -> Alignment.CenterEnd
            else -> Alignment.Center
        }
    }
}
