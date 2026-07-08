package com.styleconverter.test.style.performance

// CSS `isolation` creates a new stacking / blending context. On the web, the
// only observable effect is how mix-blend-mode and z-index interact. Compose's
// CompositingStrategy.Offscreen gives the closest analog: everything inside the
// modifier chain becomes a single blend-target.

/**
 * Configuration for the CSS `isolation` property.
 *
 * ## IR Shape
 * ```
 * "Isolation" -> "AUTO"        // default — no new context
 * "Isolation" -> "ISOLATE"     // opt in to offscreen compositing
 * ```
 *
 * ## Values
 * - [Value.AUTO]: default; no new stacking context.
 * - [Value.ISOLATE]: force an offscreen compositing layer.
 */
data class IsolationConfig(
    /** Selected value; defaults to AUTO so absent IR data yields a no-op. */
    val value: Value = Value.AUTO
) {
    /** True iff the applier should actually do something (i.e. isolate). */
    val hasIsolation: Boolean get() = value == Value.ISOLATE

    /** Enum mirroring the CSS keywords. Only two legal values per spec. */
    enum class Value { AUTO, ISOLATE }
}
