package app.parsing.css.properties.longhands.interactions

import app.irmodels.IRProperty
import app.irmodels.IRUrl
import app.irmodels.properties.interactions.CursorProperty
import app.irmodels.properties.interactions.CursorProperty.Cursor.CursorKeyword
import app.parsing.css.properties.longhands.PropertyParser

object CursorPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()

        // Check if it contains url() - handle URL cursor with fallback
        if (trimmed.lowercase().contains("url(")) {
            // Split by comma to get cursor sources and fallback
            val parts = splitByComma(trimmed)

            // Find first url() and the fallback keyword
            var urlValue: String? = null
            var fallbackKeyword: CursorKeyword? = null

            for (part in parts) {
                val p = part.trim()
                if (p.lowercase().startsWith("url(")) {
                    if (urlValue == null) {
                        // Extract URL (handles url('path') or url("path") or url(path))
                        urlValue = extractUrl(p)
                    }
                } else {
                    // Try to parse as keyword fallback
                    parseKeyword(p)?.let { fallbackKeyword = it }
                }
            }

            urlValue?.let { url ->
                return CursorProperty(CursorProperty.Cursor.Url(IRUrl(url), fallbackKeyword))
            }
            return null
        }

        // Standard keyword cursor
        val keyword = parseKeyword(trimmed.lowercase()) ?: return null
        return CursorProperty(CursorProperty.Cursor.Keyword(keyword))
    }

    private fun extractUrl(value: String): String? {
        val urlMatch = Regex("""url\(\s*['"]?([^'")\s]+)['"]?\s*\)""", RegexOption.IGNORE_CASE)
            .find(value)
        return urlMatch?.groupValues?.get(1)
    }

    private fun splitByComma(value: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var parenDepth = 0

        for (char in value) {
            when {
                char == '(' -> { parenDepth++; current.append(char) }
                char == ')' -> { parenDepth--; current.append(char) }
                char == ',' && parenDepth == 0 -> {
                    result.add(current.toString())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }

    private fun parseKeyword(value: String): CursorKeyword? {
        return when (value.trim().lowercase()) {
            "auto" -> CursorKeyword.AUTO
            "default" -> CursorKeyword.DEFAULT
            "none" -> CursorKeyword.NONE
            "context-menu" -> CursorKeyword.CONTEXT_MENU
            "help" -> CursorKeyword.HELP
            "pointer" -> CursorKeyword.POINTER
            "progress" -> CursorKeyword.PROGRESS
            "wait" -> CursorKeyword.WAIT
            "cell" -> CursorKeyword.CELL
            "crosshair" -> CursorKeyword.CROSSHAIR
            "text" -> CursorKeyword.TEXT
            "vertical-text" -> CursorKeyword.VERTICAL_TEXT
            "alias" -> CursorKeyword.ALIAS
            "copy" -> CursorKeyword.COPY
            "move" -> CursorKeyword.MOVE
            "no-drop" -> CursorKeyword.NO_DROP
            "not-allowed" -> CursorKeyword.NOT_ALLOWED
            "grab" -> CursorKeyword.GRAB
            "grabbing" -> CursorKeyword.GRABBING
            "all-scroll" -> CursorKeyword.ALL_SCROLL
            "col-resize" -> CursorKeyword.COL_RESIZE
            "row-resize" -> CursorKeyword.ROW_RESIZE
            "n-resize" -> CursorKeyword.N_RESIZE
            "e-resize" -> CursorKeyword.E_RESIZE
            "s-resize" -> CursorKeyword.S_RESIZE
            "w-resize" -> CursorKeyword.W_RESIZE
            "ne-resize" -> CursorKeyword.NE_RESIZE
            "nw-resize" -> CursorKeyword.NW_RESIZE
            "se-resize" -> CursorKeyword.SE_RESIZE
            "sw-resize" -> CursorKeyword.SW_RESIZE
            "ew-resize" -> CursorKeyword.EW_RESIZE
            "ns-resize" -> CursorKeyword.NS_RESIZE
            "nesw-resize" -> CursorKeyword.NESW_RESIZE
            "nwse-resize" -> CursorKeyword.NWSE_RESIZE
            "zoom-in" -> CursorKeyword.ZOOM_IN
            "zoom-out" -> CursorKeyword.ZOOM_OUT
            else -> null
        }
    }
}
