package app.parsing.css.properties.longhands.animations

import app.irmodels.IRProperty
import app.irmodels.properties.animations.AnimationTimelineProperty
import app.irmodels.properties.animations.AnimationTimelineValue
import app.irmodels.properties.animations.ScrollAxis
import app.irmodels.properties.animations.ScrollScroller
import app.parsing.css.properties.longhands.PropertyParser

/**
 * Parses the CSS `animation-timeline` property.
 *
 * Syntax: auto | none | <dashed-ident> | scroll() | view()
 *
 * Examples:
 * - "auto" → Auto
 * - "none" → None
 * - "--my-timeline" → Named("--my-timeline")
 * - "scroll()" → Scroll(NEAREST, BLOCK)
 * - "scroll(root)" → Scroll(ROOT, BLOCK)
 * - "scroll(inline)" → Scroll(NEAREST, INLINE)
 * - "view()" → View(BLOCK)
 * - "view(inline 10px 20px)" → View(INLINE, "10px", "20px")
 */
object AnimationTimelinePropertyParser : PropertyParser {

    private val scrollRegex = """scroll\s*\(\s*(.*?)\s*\)""".toRegex(RegexOption.IGNORE_CASE)
    private val viewRegex = """view\s*\(\s*(.*?)\s*\)""".toRegex(RegexOption.IGNORE_CASE)

    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Check for comma-separated multiple values
        if (trimmed.contains(",") && !trimmed.contains("(")) {
            val parts = trimmed.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size > 1) {
                val timelines = parts.mapNotNull { parseSingleTimeline(it) }
                if (timelines.size > 1) {
                    return AnimationTimelineProperty(AnimationTimelineValue.Multiple(timelines))
                }
            }
        }

        // Check for comma-separated with functions (need parenthesis-aware split)
        if (trimmed.contains(",")) {
            val parts = splitByComma(trimmed)
            if (parts.size > 1) {
                val timelines = parts.mapNotNull { parseSingleTimeline(it.trim()) }
                if (timelines.size > 1) {
                    return AnimationTimelineProperty(AnimationTimelineValue.Multiple(timelines))
                }
            }
        }

        val timelineValue = parseSingleTimeline(trimmed) ?: return null
        return AnimationTimelineProperty(timelineValue)
    }

    private fun parseSingleTimeline(value: String): AnimationTimelineValue? {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        return when {
            lower == "auto" -> AnimationTimelineValue.Auto
            lower == "none" -> AnimationTimelineValue.None
            lower.startsWith("scroll(") -> parseScroll(trimmed)
            lower.startsWith("view(") -> parseView(trimmed)
            trimmed.startsWith("--") -> AnimationTimelineValue.Named(trimmed)
            else -> AnimationTimelineValue.Named(trimmed)
        }
    }

    private fun parseScroll(value: String): AnimationTimelineValue {
        val match = scrollRegex.find(value) ?: return AnimationTimelineValue.Scroll()
        val args = match.groupValues[1].trim().lowercase()

        if (args.isEmpty()) {
            return AnimationTimelineValue.Scroll()
        }

        val tokens = args.split("\\s+".toRegex())
        var scroller = ScrollScroller.NEAREST
        var axis = ScrollAxis.BLOCK

        for (token in tokens) {
            when (token) {
                "nearest" -> scroller = ScrollScroller.NEAREST
                "root" -> scroller = ScrollScroller.ROOT
                "self" -> scroller = ScrollScroller.SELF
                "block" -> axis = ScrollAxis.BLOCK
                "inline" -> axis = ScrollAxis.INLINE
                "x" -> axis = ScrollAxis.X
                "y" -> axis = ScrollAxis.Y
            }
        }

        return AnimationTimelineValue.Scroll(scroller, axis)
    }

    private fun parseView(value: String): AnimationTimelineValue {
        val match = viewRegex.find(value) ?: return AnimationTimelineValue.View()
        val args = match.groupValues[1].trim()

        if (args.isEmpty()) {
            return AnimationTimelineValue.View()
        }

        val lower = args.lowercase()
        val tokens = args.split("\\s+".toRegex())

        var axis = ScrollAxis.BLOCK
        var insetStart: String? = null
        var insetEnd: String? = null

        // Parse axis first
        val axisToken = tokens.firstOrNull()?.lowercase()
        when (axisToken) {
            "block" -> axis = ScrollAxis.BLOCK
            "inline" -> axis = ScrollAxis.INLINE
            "x" -> axis = ScrollAxis.X
            "y" -> axis = ScrollAxis.Y
        }

        // Parse insets (remaining tokens after axis)
        val insetTokens = if (axisToken in listOf("block", "inline", "x", "y")) {
            tokens.drop(1)
        } else {
            tokens
        }

        if (insetTokens.isNotEmpty()) {
            insetStart = insetTokens[0]
            if (insetTokens.size > 1) {
                insetEnd = insetTokens[1]
            }
        }

        return AnimationTimelineValue.View(axis, insetStart, insetEnd)
    }

    private fun splitByComma(value: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        var current = StringBuilder()

        for (char in value) {
            when (char) {
                '(' -> { depth++; current.append(char) }
                ')' -> { depth--; current.append(char) }
                ',' -> {
                    if (depth == 0) {
                        result.add(current.toString())
                        current = StringBuilder()
                    } else {
                        current.append(char)
                    }
                }
                else -> current.append(char)
            }
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }
}
