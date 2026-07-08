package app.parsing.css.properties.shorthands

/**
 * Expands `columns` shorthand into `column-width` and `column-count` longhands.
 * Syntax: <column-width> || <column-count>
 */
object ColumnsExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val trimmed = value.trim()
        if (trimmed in setOf("inherit", "initial", "unset", "revert", "auto")) {
            return mapOf(
                "column-width" to trimmed,
                "column-count" to trimmed
            )
        }

        val tokens = trimmed.split(Regex("\\s+"))
        var width: String? = null
        var count: String? = null

        for (token in tokens) {
            val lower = token.lowercase()
            when {
                lower == "auto" -> {
                    if (width == null) width = "auto"
                    else count = "auto"
                }
                isLength(lower) -> width = token
                isInteger(lower) -> count = token
            }
        }

        val result = mutableMapOf<String, String>()
        width?.let { result["column-width"] = it }
        count?.let { result["column-count"] = it }
        return result
    }

    private fun isLength(value: String): Boolean {
        return value.matches(Regex("-?[\\d.]+[a-z]+"))
    }

    private fun isInteger(value: String): Boolean {
        return value.matches(Regex("\\d+"))
    }
}
