package com.styleconverter.runtime.typography.wrapping

import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * HyphenateCharacterExtractor.kt
 * typography/wrapping — wave 54 (lane L3, unit U2-android).
 *
 * `IRProperty → HyphenateCharacterConfig`, mirroring the reader's
 * `converter/.../longhands/typography/HyphenateCharacterPropertyParser.kt` and
 * its IR model `irmodels/properties/typography/HyphenateCharacterProperty.kt`:
 * the wire is `{"type":"HyphenateCharacter","data":{"type":"auto"}}` or
 * `{"data":{"type":"string","value":"<decoded string>"}}` (since wave 54 the
 * reader decodes css-syntax-3 escapes and keeps `""`).
 *
 * Why not `TextStyleApplier.extractHyphenateCharacter`: that reader is not on
 * the render path, its DEFAULT is U+00AD (a soft hyphen is invisible when
 * painted, so it could never be the UA hyphen), and it lower-cases its type
 * tag — dead code queued for removal (PLAN §5), deliberately left untouched.
 */
object HyphenateCharacterExtractor {

    /** The IR type name this extractor claims (TypographyRegistryTest lists it). */
    const val IR_TYPE = "HyphenateCharacter"

    /**
     * The element's own declaration, or null when it declares none. The LAST
     * declaration wins (css-cascade-4 §6: later declarations of equal
     * importance override earlier ones in one block).
     */
    @JvmStatic
    fun extract(properties: List<IRProperty>): HyphenateCharacterConfig? {
        // No declaration on this element → null; the caller uses the UA hyphen.
        val prop = properties.lastOrNull { it.type == IR_TYPE } ?: return null
        // The reader always emits the object form; anything else is unknown.
        val obj = prop.data as? JsonObject ?: return unreadable(prop)
        // The discriminator: "auto" or "string" (HyphenateCharacterValue).
        val tag = (obj["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        return when (tag) {
            // `auto` → the UA hyphen, represented as a null value.
            "auto" -> HyphenateCharacterConfig(null)
            // `<string>` → verbatim (no trim, no case change), "" included.
            "string" -> (obj["value"] as? JsonPrimitive)?.takeIf { it.isString }
                ?.let { HyphenateCharacterConfig(it.content) } ?: unreadable(prop)
            // An unknown tag: never guessed at.
            else -> unreadable(prop)
        }
    }

    /**
     * No silent fallthrough (repo rule): an unreadable payload is reported
     * once through the PropertyTracker breadcrumb and rendered as `auto`
     * (the initial value, css-text-4 §6.3), which is what a UA does with a
     * declaration it cannot parse.
     */
    private fun unreadable(prop: IRProperty): HyphenateCharacterConfig {
        // One breadcrumb per distinct payload shape, never per run.
        HyphenateCharacterApplier.logOnce(
            "extract:${prop.data}",
            "hyphenate-character: unreadable payload ${prop.data}; rendered as auto"
        )
        // `auto` keeps the UA hyphen, exactly the pre-wave-54 picture.
        return HyphenateCharacterConfig(null)
    }
}
