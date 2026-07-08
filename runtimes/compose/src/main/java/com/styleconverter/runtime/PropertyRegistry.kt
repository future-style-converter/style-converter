package com.styleconverter.runtime

/**
 * Runtime registry of migrated style-engine properties.
 *
 * [PropertyRegistry] is a live set of IR property-type strings that have been
 * migrated to the canonical category-per-folder triplet pattern (Config /
 * Extractor / Applier). The legacy monolithic [StyleApplier] dispatch switch
 * consults this registry so it can skip properties that a new extractor has
 * already claimed — avoiding double-application while we migrate categories
 * one phase at a time.
 *
 * Populated at class-load time by each migrated extractor's companion object.
 * See the Phase 5 border-category extractors for examples of the registration
 * pattern:
 *   `PropertyRegistry.migrated("BorderTopWidth", owner = "borders/sides")`
 *
 * Rules:
 *  - IR property names are matched case-sensitively (they're the IRProperty.type
 *    values the CSS parser emits, e.g. "BorderTopWidth", not "border-top-width").
 *  - Registration is idempotent; re-registering the same property is a no-op.
 *  - The `owner` string is just the folder path (e.g. "borders/sides",
 *    "effects/shadow") for introspection / `allRegistered()` reports.
 */
object PropertyRegistry {

    // Backing map: IR property type -> owning category folder (e.g. "borders/sides").
    // Using a linkedMap so allRegistered() enumerates in registration order,
    // which makes the coverage report stable across runs.
    private val migrated: MutableMap<String, String> = linkedMapOf()

    /**
     * Register one or more IR property names as migrated to the canonical
     * style-engine pattern. Called from each extractor's init block or
     * companion object so the registry is populated at class-load time.
     *
     * @param propertyTypes One or more IRProperty.type strings (CSS camelcase:
     *                      "BorderTopWidth", "BorderImageSource", etc.)
     * @param owner Folder path under style/ that owns these properties.
     *              Used only for introspection; no behavioral effect.
     */
    @Synchronized
    fun migrated(vararg propertyTypes: String, owner: String) {
        // Idempotent insert: if the property has already been claimed by
        // another extractor, keep the first owner (first-write-wins). This
        // catches accidental double-registration during the migration.
        for (t in propertyTypes) {
            migrated.putIfAbsent(t, owner)
        }
    }

    /**
     * @return true if [propertyType] has already been claimed by a migrated
     *         extractor — the legacy StyleApplier switch uses this to skip
     *         the property.
     */
    @Synchronized
    fun isMigrated(propertyType: String): Boolean = propertyType in migrated

    /**
     * @return the category folder that owns [propertyType], or null if the
     *         property has not yet been migrated.
     */
    @Synchronized
    fun ownerOf(propertyType: String): String? = migrated[propertyType]

    /**
     * @return an immutable snapshot of all registered (propertyType -> owner)
     *         pairs. Used by the debug overlay and by the Phase coverage
     *         matrix in testing/README.md to audit which properties have
     *         been migrated.
     */
    @Synchronized
    fun allRegistered(): Map<String, String> = migrated.toMap()

    /**
     * @return the number of migrated properties. Cheap way to assert progress
     *         in unit tests ("after Phase 5, expect ≥ 47 border properties").
     */
    @Synchronized
    fun size(): Int = migrated.size

    /**
     * Reset the registry. Used by tests that need to re-register extractors
     * in a clean state — never called in production code.
     */
    @Synchronized
    fun reset() {
        migrated.clear()
    }
}
