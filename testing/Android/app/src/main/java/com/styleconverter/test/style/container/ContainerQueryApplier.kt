package com.styleconverter.test.style.container

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Applies CSS container query properties to Compose layouts.
 *
 * ## CSS Properties
 * ```css
 * .card-container {
 *     container-type: inline-size;
 *     container-name: card;
 * }
 *
 * @container card (min-width: 400px) {
 *     .card { flex-direction: row; }
 * }
 *
 * @container (min-width: 300px) {
 *     .item { font-size: 1.2rem; }
 * }
 * ```
 *
 * ## Compose Mapping
 *
 * | CSS | Compose | Notes |
 * |-----|---------|-------|
 * | container-type | BoxWithConstraints | Measure container |
 * | container-name | CompositionLocal | Named containers |
 * | @container query | when {} blocks | Conditional rendering |
 * | cqi/cqw units | Calculated Dp | Container-relative |
 *
 * ## Limitations
 *
 * - No CSS-like syntax; use Compose conditionals
 * - Container queries must be defined in code
 * - Block-size queries require explicit height
 * - Named containers use CompositionLocals
 *
 * ## Usage
 * ```kotlin
 * ContainerQueryApplier.QueryContainer(
 *     config = containerConfig,
 *     modifier = Modifier.fillMaxWidth()
 * ) {
 *     // Access container dimensions
 *     val containerWidth = containerWidth
 *
 *     // Responsive content based on container
 *     if (containerWidth >= 400.dp) {
 *         WideLayout()
 *     } else {
 *         NarrowLayout()
 *     }
 * }
 *
 * // Named container
 * ContainerQueryApplier.NamedContainer(name = "card") {
 *     // Child can access parent container by name
 *     val cardWidth = LocalContainerDimensions.current["card"]?.width
 * }
 * ```
 */
object ContainerQueryApplier {

    // =========================================================================
    // COMPOSITION LOCALS
    // =========================================================================

    /**
     * Container dimensions for the nearest QueryContainer.
     */
    data class ContainerDimensions(
        val width: Dp,
        val height: Dp,
        val name: String? = null
    ) {
        /** 1cqi = 1% of container inline size (width in horizontal writing mode) */
        fun cqi(percent: Float): Dp = width * (percent / 100f)

        /** 1cqw = 1% of container width */
        fun cqw(percent: Float): Dp = width * (percent / 100f)

        /** 1cqh = 1% of container height */
        fun cqh(percent: Float): Dp = height * (percent / 100f)

        /** 1cqb = 1% of container block size (height in horizontal writing mode) */
        fun cqb(percent: Float): Dp = height * (percent / 100f)

        /** 1cqmin = 1% of smaller dimension */
        fun cqmin(percent: Float): Dp = minOf(width, height) * (percent / 100f)

        /** 1cqmax = 1% of larger dimension */
        fun cqmax(percent: Float): Dp = maxOf(width, height) * (percent / 100f)
    }

    /**
     * Current container dimensions.
     */
    val LocalContainerDimensions = compositionLocalOf<ContainerDimensions?> { null }

    /**
     * Named containers map (name -> dimensions).
     */
    val LocalNamedContainers = compositionLocalOf<Map<String, ContainerDimensions>> { emptyMap() }

    // =========================================================================
    // QUERY CONTAINERS
    // =========================================================================

    /**
     * A container that provides its dimensions to descendants.
     *
     * @param config Container query configuration
     * @param modifier Modifier for the container
     * @param content Content with access to container dimensions
     */
    @Composable
    fun QueryContainer(
        config: ContainerQueryConfig = ContainerQueryConfig(),
        modifier: Modifier = Modifier,
        content: @Composable BoxWithConstraintsScope.() -> Unit
    ) {
        BoxWithConstraints(modifier = modifier) {
            val dimensions = remember(maxWidth, maxHeight, config.containerName) {
                ContainerDimensions(
                    width = maxWidth,
                    height = maxHeight,
                    name = config.containerName
                )
            }

            // Update named containers if this has a name
            val namedContainers = LocalNamedContainers.current
            val updatedContainers = if (config.containerName != null) {
                namedContainers + (config.containerName to dimensions)
            } else {
                namedContainers
            }

            CompositionLocalProvider(
                LocalContainerDimensions provides dimensions,
                LocalNamedContainers provides updatedContainers
            ) {
                content()
            }
        }
    }

    /**
     * A named container for named container queries.
     *
     * @param name Container name for @container name queries
     * @param modifier Modifier for the container
     * @param content Content
     */
    @Composable
    fun NamedContainer(
        name: String,
        modifier: Modifier = Modifier,
        content: @Composable BoxWithConstraintsScope.() -> Unit
    ) {
        QueryContainer(
            config = ContainerQueryConfig(
                containerType = ContainerQueryType.INLINE_SIZE,
                containerName = name
            ),
            modifier = modifier,
            content = content
        )
    }

    /**
     * A size container (container-type: size).
     *
     * @param modifier Modifier for the container
     * @param content Content
     */
    @Composable
    fun SizeContainer(
        modifier: Modifier = Modifier,
        content: @Composable BoxWithConstraintsScope.() -> Unit
    ) {
        QueryContainer(
            config = ContainerQueryConfig(containerType = ContainerQueryType.SIZE),
            modifier = modifier,
            content = content
        )
    }

    /**
     * An inline-size container (container-type: inline-size).
     *
     * @param modifier Modifier for the container
     * @param content Content
     */
    @Composable
    fun InlineSizeContainer(
        modifier: Modifier = Modifier,
        content: @Composable BoxWithConstraintsScope.() -> Unit
    ) {
        QueryContainer(
            config = ContainerQueryConfig(containerType = ContainerQueryType.INLINE_SIZE),
            modifier = modifier,
            content = content
        )
    }

    // =========================================================================
    // QUERY HELPERS
    // =========================================================================

    /**
     * Get dimensions of the nearest container.
     */
    @Composable
    fun containerDimensions(): ContainerDimensions? {
        return LocalContainerDimensions.current
    }

    /**
     * Get dimensions of a named container.
     *
     * @param name Container name
     */
    @Composable
    fun namedContainerDimensions(name: String): ContainerDimensions? {
        return LocalNamedContainers.current[name]
    }

    /**
     * Container width of nearest container.
     */
    val containerWidth: Dp
        @Composable
        get() = LocalContainerDimensions.current?.width ?: Dp.Unspecified

    /**
     * Container height of nearest container.
     */
    val containerHeight: Dp
        @Composable
        get() = LocalContainerDimensions.current?.height ?: Dp.Unspecified

    // =========================================================================
    // QUERY FUNCTIONS
    // =========================================================================

    /**
     * Check if container width meets minimum width.
     *
     * @param minWidth Minimum width threshold
     */
    @Composable
    fun minWidth(minWidth: Dp): Boolean {
        val width = LocalContainerDimensions.current?.width ?: return false
        return width >= minWidth
    }

    /**
     * Check if container width is at most maxWidth.
     *
     * @param maxWidth Maximum width threshold
     */
    @Composable
    fun maxWidth(maxWidth: Dp): Boolean {
        val width = LocalContainerDimensions.current?.width ?: return false
        return width <= maxWidth
    }

    /**
     * Check if container height meets minimum height.
     *
     * @param minHeight Minimum height threshold
     */
    @Composable
    fun minHeight(minHeight: Dp): Boolean {
        val height = LocalContainerDimensions.current?.height ?: return false
        return height >= minHeight
    }

    /**
     * Check if container width is within range.
     *
     * @param min Minimum width
     * @param max Maximum width
     */
    @Composable
    fun widthBetween(min: Dp, max: Dp): Boolean {
        val width = LocalContainerDimensions.current?.width ?: return false
        return width >= min && width <= max
    }

    /**
     * Get a breakpoint category based on container width.
     *
     * @param breakpoints Map of name to minimum width
     */
    @Composable
    fun breakpoint(breakpoints: Map<String, Dp>): String? {
        val width = LocalContainerDimensions.current?.width ?: return null

        return breakpoints.entries
            .filter { width >= it.value }
            .maxByOrNull { it.value }
            ?.key
    }

    // =========================================================================
    // RESPONSIVE COMPOSABLES
    // =========================================================================

    /**
     * Render content based on container width breakpoints.
     *
     * @param small Content for small containers (< smallBreak)
     * @param medium Content for medium containers (smallBreak <= w < largeBreak)
     * @param large Content for large containers (>= largeBreak)
     * @param smallBreak Threshold for small/medium (default 400.dp)
     * @param largeBreak Threshold for medium/large (default 800.dp)
     */
    @Composable
    fun Responsive(
        small: @Composable () -> Unit,
        medium: @Composable () -> Unit = small,
        large: @Composable () -> Unit = medium,
        smallBreak: Dp = 400.dp,
        largeBreak: Dp = 800.dp
    ) {
        val width = LocalContainerDimensions.current?.width ?: 0.dp

        when {
            width >= largeBreak -> large()
            width >= smallBreak -> medium()
            else -> small()
        }
    }

    /**
     * Render one of two layouts based on container width.
     *
     * @param compact Content for compact containers
     * @param expanded Content for expanded containers
     * @param breakpoint Width threshold (default 600.dp)
     */
    @Composable
    fun CompactOrExpanded(
        compact: @Composable () -> Unit,
        expanded: @Composable () -> Unit,
        breakpoint: Dp = 600.dp
    ) {
        if (minWidth(breakpoint)) {
            expanded()
        } else {
            compact()
        }
    }

    // =========================================================================
    // CONTAINER UNIT HELPERS
    // =========================================================================

    /**
     * Convert cqi units (1cqi = 1% of inline size).
     */
    @Composable
    fun cqi(value: Float): Dp {
        return LocalContainerDimensions.current?.cqi(value) ?: 0.dp
    }

    /**
     * Convert cqw units (1cqw = 1% of container width).
     */
    @Composable
    fun cqw(value: Float): Dp {
        return LocalContainerDimensions.current?.cqw(value) ?: 0.dp
    }

    /**
     * Convert cqh units (1cqh = 1% of container height).
     */
    @Composable
    fun cqh(value: Float): Dp {
        return LocalContainerDimensions.current?.cqh(value) ?: 0.dp
    }

    /**
     * Convert cqmin units (1cqmin = 1% of smaller dimension).
     */
    @Composable
    fun cqmin(value: Float): Dp {
        return LocalContainerDimensions.current?.cqmin(value) ?: 0.dp
    }

    /**
     * Convert cqmax units (1cqmax = 1% of larger dimension).
     */
    @Composable
    fun cqmax(value: Float): Dp {
        return LocalContainerDimensions.current?.cqmax(value) ?: 0.dp
    }

    // =========================================================================
    // NOTES
    // =========================================================================

    object Notes {
        const val CONTAINER_TYPE = """
            CSS container-type defines what can be queried:

            - normal: Not a query container
            - inline-size: Query inline dimension (width)
            - block-size: Query block dimension (height)
            - size: Query both dimensions

            In Compose, BoxWithConstraints always provides both,
            but container-type affects containment behavior.
        """

        const val CONTAINER_NAME = """
            CSS container-name allows targeting specific containers:

            @container sidebar (min-width: 200px) { ... }

            In Compose, we use CompositionLocals to track named
            containers. Children can access parent containers by name.
        """

        const val CONTAINER_UNITS = """
            CSS container query units:

            - cqi: 1% of container inline size
            - cqb: 1% of container block size
            - cqw: 1% of container width
            - cqh: 1% of container height
            - cqmin: 1% of smaller dimension
            - cqmax: 1% of larger dimension

            Use the cqi(), cqw(), etc. functions to convert.
        """

        const val RESPONSIVE_PATTERN = """
            Common container query pattern in CSS:

            @container (min-width: 400px) {
                .card { flex-direction: row; }
            }

            In Compose:

            QueryContainer {
                if (containerWidth >= 400.dp) {
                    Row { CardContent() }
                } else {
                    Column { CardContent() }
                }
            }
        """
    }
}
