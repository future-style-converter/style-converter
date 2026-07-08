package com.styleconverter.test.style.spacing

// Fold IR into a GapConfig. We accept the legacy single "Gap" type for
// forward-compat (converter currently splits it into RowGap+ColumnGap), but
// if it ever appears we apply it to both axes like the shorthand says.

import com.styleconverter.test.style.core.types.LengthValue
import com.styleconverter.test.style.core.types.extractLength
import kotlinx.serialization.json.JsonElement

object GapExtractor {

    /** Property-type strings owned by this extractor. */
    val PROPERTIES: Set<String> = setOf("Gap", "RowGap", "ColumnGap")

    fun extract(properties: List<Pair<String, JsonElement?>>): GapConfig {
        var rowGap: LengthValue? = null
        var columnGap: LengthValue? = null
        for ((type, data) in properties) {
            if (type !in PROPERTIES) continue
            val len = extractLength(data)
            if (len is LengthValue.Unknown) continue
            when (type) {
                "Gap" -> { rowGap = len; columnGap = len }
                "RowGap" -> rowGap = len
                "ColumnGap" -> columnGap = len
            }
        }
        return GapConfig(rowGap = rowGap, columnGap = columnGap)
    }

    fun isGapProperty(type: String): Boolean = type in PROPERTIES
}
