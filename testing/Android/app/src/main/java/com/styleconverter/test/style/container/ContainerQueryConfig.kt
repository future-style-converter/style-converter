package com.styleconverter.test.style.container

/**
 * Container query type for container queries.
 */
enum class ContainerQueryType {
    NORMAL,
    INLINE_SIZE,
    BLOCK_SIZE,
    SIZE
}

/**
 * Configuration for CSS container query properties.
 * Includes container-type and container-name.
 */
data class ContainerQueryConfig(
    val containerType: ContainerQueryType = ContainerQueryType.NORMAL,
    val containerName: String? = null
) {
    /**
     * Check if this config has any container query properties set.
     */
    val hasContainerQuery: Boolean
        get() = containerType != ContainerQueryType.NORMAL || containerName != null
}
