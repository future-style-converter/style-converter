package com.styleconverter.test.style.content

import com.styleconverter.test.style.core.types.ValueExtractors
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracts content-related configuration from IR properties.
 */
object ContentExtractor {

    fun extractCounterConfig(properties: List<Pair<String, JsonElement?>>): CounterConfig {
        val reset = mutableMapOf<String, Int>()
        val increment = mutableMapOf<String, Int>()
        val set = mutableMapOf<String, Int>()

        for ((type, data) in properties) {
            when (type) {
                "CounterReset" -> reset.putAll(extractCounterValues(data, 0))
                "CounterIncrement" -> increment.putAll(extractCounterValues(data, 1))
                "CounterSet" -> set.putAll(extractCounterValues(data, 0))
            }
        }

        return CounterConfig(
            reset = reset,
            increment = increment,
            set = set
        )
    }

    private fun extractCounterValues(data: JsonElement?, defaultValue: Int): Map<String, Int> {
        if (data == null) return emptyMap()

        val result = mutableMapOf<String, Int>()

        when (data) {
            is JsonArray -> {
                data.forEach { item ->
                    when (item) {
                        is JsonObject -> {
                            val name = item["name"]?.jsonPrimitive?.contentOrNull
                            val value = item["value"]?.jsonPrimitive?.intOrNull ?: defaultValue
                            if (name != null) result[name] = value
                        }
                        is JsonArray -> {
                            if (item.size >= 1) {
                                val name = item[0].jsonPrimitive.contentOrNull
                                val value = item.getOrNull(1)?.jsonPrimitive?.intOrNull ?: defaultValue
                                if (name != null) result[name] = value
                            }
                        }
                        else -> {}
                    }
                }
            }
            is JsonObject -> {
                val counters = data["counters"]?.jsonArray
                counters?.forEach { item ->
                    val obj = item.jsonObject
                    val name = obj["name"]?.jsonPrimitive?.contentOrNull
                    val value = obj["value"]?.jsonPrimitive?.intOrNull ?: defaultValue
                    if (name != null) result[name] = value
                }
            }
            else -> {}
        }

        return result
    }

    fun extractQuotesConfig(properties: List<Pair<String, JsonElement?>>): QuotesConfig {
        val quotesData = properties.find { it.first == "Quotes" }?.second
            ?: return QuotesConfig.Default

        return extractQuotes(quotesData)
    }

    private fun extractQuotes(data: JsonElement): QuotesConfig {
        val keyword = ValueExtractors.extractKeyword(data)?.lowercase()

        return when (keyword) {
            "auto" -> QuotesConfig.Auto
            "none" -> QuotesConfig.None
            else -> {
                when (data) {
                    is JsonArray -> {
                        val pairs = mutableListOf<QuotePair>()
                        var i = 0
                        while (i + 1 < data.size) {
                            val open = data[i].jsonPrimitive.contentOrNull ?: continue
                            val close = data[i + 1].jsonPrimitive.contentOrNull ?: continue
                            pairs.add(QuotePair(open, close))
                            i += 2
                        }
                        QuotesConfig(quotePairs = pairs, isAuto = false)
                    }
                    is JsonObject -> {
                        val pairs = data["pairs"]?.jsonArray?.mapNotNull { pairData ->
                            val obj = pairData.jsonObject
                            val open = obj["open"]?.jsonPrimitive?.contentOrNull
                            val close = obj["close"]?.jsonPrimitive?.contentOrNull
                            if (open != null && close != null) QuotePair(open, close) else null
                        } ?: emptyList()
                        QuotesConfig(quotePairs = pairs, isAuto = false)
                    }
                    else -> QuotesConfig.Default
                }
            }
        }
    }

    /**
     * Extract content property values.
     * Handles text, counter(), counters(), attr(), url(), and quote keywords.
     */
    fun extractContentValues(properties: List<Pair<String, JsonElement?>>): List<ContentValue> {
        val contentData = properties.find { it.first == "Content" }?.second
            ?: return emptyList()

        return parseContentValues(contentData)
    }

    /**
     * Parse content property data into ContentValue list.
     */
    private fun parseContentValues(data: JsonElement): List<ContentValue> {
        val results = mutableListOf<ContentValue>()

        when (data) {
            is JsonPrimitive -> {
                val content = data.contentOrNull?.lowercase()
                when (content) {
                    "none" -> results.add(ContentValue.None)
                    "normal" -> results.add(ContentValue.Normal)
                    "open-quote" -> results.add(ContentValue.OpenQuote)
                    "close-quote" -> results.add(ContentValue.CloseQuote)
                    "no-open-quote" -> results.add(ContentValue.NoOpenQuote)
                    "no-close-quote" -> results.add(ContentValue.NoCloseQuote)
                    else -> {
                        // Treat as text content
                        data.contentOrNull?.let { text ->
                            results.add(ContentValue.Text(text))
                        }
                    }
                }
            }
            is JsonObject -> {
                val type = data["type"]?.jsonPrimitive?.contentOrNull?.lowercase()
                when (type) {
                    "none" -> results.add(ContentValue.None)
                    "normal" -> results.add(ContentValue.Normal)
                    "text", "string" -> {
                        val value = data["value"]?.jsonPrimitive?.contentOrNull ?: ""
                        results.add(ContentValue.Text(value))
                    }
                    "url" -> {
                        val url = data["url"]?.jsonPrimitive?.contentOrNull
                            ?: data["value"]?.jsonPrimitive?.contentOrNull ?: ""
                        results.add(ContentValue.Url(url))
                    }
                    "counter" -> {
                        val name = data["name"]?.jsonPrimitive?.contentOrNull ?: ""
                        val style = parseListStyle(data["style"]?.jsonPrimitive?.contentOrNull)
                        results.add(ContentValue.Counter(name, style))
                    }
                    "counters" -> {
                        val name = data["name"]?.jsonPrimitive?.contentOrNull ?: ""
                        val separator = data["separator"]?.jsonPrimitive?.contentOrNull ?: "."
                        val style = parseListStyle(data["style"]?.jsonPrimitive?.contentOrNull)
                        results.add(ContentValue.Counters(name, separator, style))
                    }
                    "attr" -> {
                        val attrName = data["name"]?.jsonPrimitive?.contentOrNull
                            ?: data["attribute"]?.jsonPrimitive?.contentOrNull ?: ""
                        results.add(ContentValue.Attr(attrName))
                    }
                    "open-quote" -> results.add(ContentValue.OpenQuote)
                    "close-quote" -> results.add(ContentValue.CloseQuote)
                    "no-open-quote" -> results.add(ContentValue.NoOpenQuote)
                    "no-close-quote" -> results.add(ContentValue.NoCloseQuote)
                    else -> {
                        // Check for list of content items
                        data["items"]?.jsonArray?.forEach { item ->
                            results.addAll(parseContentValues(item))
                        }
                    }
                }
            }
            is JsonArray -> {
                // Array of content items
                data.forEach { item ->
                    results.addAll(parseContentValues(item))
                }
            }
            else -> {}
        }

        return results
    }

    /**
     * Parse list style type string.
     */
    private fun parseListStyle(style: String?): ListStyleType {
        return when (style?.lowercase()?.replace("-", "_")) {
            "none" -> ListStyleType.NONE
            "disc" -> ListStyleType.DISC
            "circle" -> ListStyleType.CIRCLE
            "square" -> ListStyleType.SQUARE
            "decimal" -> ListStyleType.DECIMAL
            "decimal_leading_zero" -> ListStyleType.DECIMAL_LEADING_ZERO
            "lower_roman" -> ListStyleType.LOWER_ROMAN
            "upper_roman" -> ListStyleType.UPPER_ROMAN
            "lower_greek" -> ListStyleType.LOWER_GREEK
            "lower_latin" -> ListStyleType.LOWER_LATIN
            "upper_latin" -> ListStyleType.UPPER_LATIN
            "armenian" -> ListStyleType.ARMENIAN
            "georgian" -> ListStyleType.GEORGIAN
            "lower_alpha" -> ListStyleType.LOWER_ALPHA
            "upper_alpha" -> ListStyleType.UPPER_ALPHA
            else -> ListStyleType.DECIMAL
        }
    }

    fun isContentProperty(type: String): Boolean {
        return type in CONTENT_PROPERTIES
    }

    private val CONTENT_PROPERTIES = setOf(
        "CounterReset", "CounterIncrement", "CounterSet",
        "Quotes", "Content"
    )
}
