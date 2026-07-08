package com.styleconverter.test.style

import android.util.Log

/**
 * Tracks property handling for debugging and coverage reporting.
 *
 * This utility helps identify which CSS properties are being successfully
 * handled by the style system and which ones are falling through or
 * being ignored.
 *
 * ## Usage
 *
 * ```kotlin
 * // In your property handling code:
 * properties.forEach { prop ->
 *     if (handleProperty(prop)) {
 *         PropertyTracker.markHandled(prop.type)
 *     } else {
 *         PropertyTracker.markUnhandled(prop.type)
 *     }
 * }
 *
 * // At the end of processing (e.g., in a debug screen):
 * val report = PropertyTracker.getReport()
 * println(report)
 *
 * // Reset between sessions:
 * PropertyTracker.reset()
 * ```
 *
 * ## Thread Safety
 * The tracker uses synchronized collections and is thread-safe for
 * concurrent access from different composable recompositions.
 *
 * ## Performance
 * In release builds, you may want to disable tracking by not calling
 * markHandled/markUnhandled, as the set operations have some overhead.
 */
object PropertyTracker {

    private val handled = mutableSetOf<String>()
    private val unhandled = mutableSetOf<String>()
    private val occurrences = mutableMapOf<String, Int>()

    private const val TAG = "PropertyTracker"

    /**
     * Mark a property type as successfully handled.
     *
     * If the property was previously marked as unhandled, it will be
     * moved to the handled set.
     *
     * @param propertyType The CSS property type (e.g., "Width", "BackgroundColor")
     */
    @Synchronized
    fun markHandled(propertyType: String) {
        handled.add(propertyType)
        unhandled.remove(propertyType)
        occurrences[propertyType] = (occurrences[propertyType] ?: 0) + 1
    }

    /**
     * Mark a property type as unhandled (not supported or failed to apply).
     *
     * Only adds to unhandled if it hasn't been successfully handled elsewhere.
     * This prevents a property that's handled in one place from showing as
     * unhandled just because it wasn't handled in another code path.
     *
     * @param propertyType The CSS property type (e.g., "GridTemplateColumns")
     */
    @Synchronized
    fun markUnhandled(propertyType: String) {
        if (propertyType !in handled) {
            unhandled.add(propertyType)
        }
        occurrences[propertyType] = (occurrences[propertyType] ?: 0) + 1
    }

    /**
     * Log an unhandled property with a warning.
     *
     * This combines marking as unhandled with logging, useful during
     * development to quickly identify missing property handlers.
     *
     * @param propertyType The CSS property type
     * @param context Optional context about where the property was encountered
     */
    @Synchronized
    fun logUnhandled(propertyType: String, context: String? = null) {
        markUnhandled(propertyType)
        val message = if (context != null) {
            "Unhandled property: $propertyType (in $context)"
        } else {
            "Unhandled property: $propertyType"
        }
        Log.w(TAG, message)
    }

    /**
     * Generate a property coverage report.
     *
     * @return PropertyReport with handled, unhandled, and coverage stats
     */
    @Synchronized
    fun getReport(): PropertyReport {
        val total = handled.size + unhandled.size
        val coverage = if (total > 0) {
            handled.size.toFloat() / total
        } else {
            1f // No properties = 100% coverage
        }

        return PropertyReport(
            handled = handled.toList().sorted(),
            unhandled = unhandled.toList().sorted(),
            coverage = coverage,
            totalOccurrences = occurrences.values.sum(),
            topUnhandled = occurrences
                .filter { it.key in unhandled }
                .toList()
                .sortedByDescending { it.second }
                .take(10)
                .map { it.first to it.second }
        )
    }

    /**
     * Reset the tracker, clearing all recorded properties.
     *
     * Useful between test runs or when reloading data.
     */
    @Synchronized
    fun reset() {
        handled.clear()
        unhandled.clear()
        occurrences.clear()
    }

    /**
     * Check if a property type has been seen as unhandled.
     *
     * @param propertyType The property type to check
     * @return True if the property was marked as unhandled
     */
    @Synchronized
    fun isUnhandled(propertyType: String): Boolean {
        return propertyType in unhandled
    }

    /**
     * Check if a property type has been successfully handled.
     *
     * @param propertyType The property type to check
     * @return True if the property was marked as handled
     */
    @Synchronized
    fun isHandled(propertyType: String): Boolean {
        return propertyType in handled
    }

    /**
     * Get the number of times a property type was encountered.
     *
     * @param propertyType The property type to check
     * @return Number of occurrences
     */
    @Synchronized
    fun getOccurrences(propertyType: String): Int {
        return occurrences[propertyType] ?: 0
    }

    /**
     * Property coverage report data class.
     *
     * @property handled List of property types that were successfully handled
     * @property unhandled List of property types that were not handled
     * @property coverage Fraction of properties handled (0.0-1.0)
     * @property totalOccurrences Total number of property instances processed
     * @property topUnhandled Top 10 most frequently unhandled properties with counts
     */
    data class PropertyReport(
        val handled: List<String>,
        val unhandled: List<String>,
        val coverage: Float,
        val totalOccurrences: Int,
        val topUnhandled: List<Pair<String, Int>>
    ) {
        /**
         * Format the report as a human-readable string.
         */
        override fun toString(): String {
            return buildString {
                appendLine("=".repeat(50))
                appendLine("Property Coverage Report")
                appendLine("=".repeat(50))
                appendLine()
                appendLine("Coverage: ${(coverage * 100).toInt()}%")
                appendLine("Total property instances: $totalOccurrences")
                appendLine()

                appendLine("Handled (${handled.size} types):")
                appendLine("-".repeat(30))
                if (handled.isEmpty()) {
                    appendLine("  (none)")
                } else {
                    handled.forEach { appendLine("  + $it") }
                }

                if (unhandled.isNotEmpty()) {
                    appendLine()
                    appendLine("Unhandled (${unhandled.size} types):")
                    appendLine("-".repeat(30))
                    unhandled.forEach { appendLine("  - $it") }

                    if (topUnhandled.isNotEmpty()) {
                        appendLine()
                        appendLine("Top unhandled by frequency:")
                        appendLine("-".repeat(30))
                        topUnhandled.forEach { (type, count) ->
                            appendLine("  $count x $type")
                        }
                    }
                }

                appendLine()
                appendLine("=".repeat(50))
            }
        }

        /**
         * Format as a compact single-line summary.
         */
        fun toSummary(): String {
            return "Coverage: ${(coverage * 100).toInt()}% (${handled.size} handled, ${unhandled.size} unhandled)"
        }
    }
}
