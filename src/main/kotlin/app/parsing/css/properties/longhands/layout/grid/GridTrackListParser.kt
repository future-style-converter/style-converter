package app.parsing.css.properties.longhands.layout.grid

import app.irmodels.IRNumber
import app.irmodels.properties.layout.grid.RepeatCount
import app.irmodels.properties.layout.grid.TrackSize
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.PercentageParser

/**
 * Helper object for parsing CSS Grid track lists.
 */
object GridTrackListParser {
    fun parseTrackList(value: String): List<TrackSize>? {
        val tracks = mutableListOf<TrackSize>()
        var remaining = value.trim()
        while (remaining.isNotEmpty()) {
            remaining = remaining.trimStart()
            if (remaining.isEmpty()) break
            val (track, rest) = parseNextTrack(remaining) ?: return null
            tracks.add(track)
            remaining = rest.trimStart()
        }
        return if (tracks.isNotEmpty()) tracks else null
    }

    private fun parseNextTrack(input: String): Pair<TrackSize, String>? {
        if (input.startsWith("repeat(")) {
            return parseRepeat(input)
        }
        if (input.startsWith("minmax(")) {
            return parseMinMax(input)
        }
        if (input.startsWith("fit-content(")) {
            return parseFitContent(input)
        }
        return parseSimpleTrack(input)
    }

    private fun parseSimpleTrack(input: String): Pair<TrackSize, String>? {
        val endIdx = input.indexOfFirst { it.isWhitespace() }.takeIf { it >= 0 } ?: input.length
        val token = input.substring(0, endIdx).trim()
        val rest = input.substring(endIdx)
        val track = when (token.lowercase()) {
            "auto" -> TrackSize.Auto()
            "min-content" -> TrackSize.MinContent()
            "max-content" -> TrackSize.MaxContent()
            else -> {
                if (token.endsWith("fr")) {
                    val frValue = token.dropLast(2).toDoubleOrNull()
                    if (frValue != null) {
                        TrackSize.Flex(IRNumber(frValue))
                    } else {
                        null
                    }
                } else {
                    val percentage = PercentageParser.parse(token)
                    if (percentage != null) {
                        TrackSize.PercentageValue(percentage)
                    } else {
                        val length = LengthParser.parse(token)
                        if (length != null) {
                            TrackSize.LengthValue(length)
                        } else {
                            null
                        }
                    }
                }
            }
        } ?: return null
        return Pair(track, rest)
    }

    private fun parseRepeat(input: String): Pair<TrackSize, String>? {
        if (!input.startsWith("repeat(")) return null
        val closeIdx = findMatchingParen(input, 6) ?: return null
        val content = input.substring(7, closeIdx)
        val rest = input.substring(closeIdx + 1)
        val commaIdx = content.indexOf(',')
        if (commaIdx < 0) return null
        val countStr = content.substring(0, commaIdx).trim()
        val trackListStr = content.substring(commaIdx + 1).trim()
        val count = when (countStr.lowercase()) {
            "auto-fill" -> RepeatCount.AutoFill()
            "auto-fit" -> RepeatCount.AutoFit()
            else -> {
                val num = countStr.toIntOrNull() ?: return null
                RepeatCount.Number(num)
            }
        }
        val tracks = parseTrackList(trackListStr) ?: return null
        return Pair(TrackSize.Repeat(count, tracks), rest)
    }

    private fun parseMinMax(input: String): Pair<TrackSize, String>? {
        if (!input.startsWith("minmax(")) return null
        val closeIdx = findMatchingParen(input, 6) ?: return null
        val content = input.substring(7, closeIdx)
        val rest = input.substring(closeIdx + 1)
        val commaIdx = content.indexOf(',')
        if (commaIdx < 0) return null
        val minStr = content.substring(0, commaIdx).trim()
        val maxStr = content.substring(commaIdx + 1).trim()
        val (minTrack, _) = parseSimpleTrack(minStr) ?: return null
        val (maxTrack, _) = parseSimpleTrack(maxStr) ?: return null
        return Pair(TrackSize.MinMax(minTrack, maxTrack), rest)
    }

    private fun parseFitContent(input: String): Pair<TrackSize, String>? {
        if (!input.startsWith("fit-content(")) return null
        val closeIdx = findMatchingParen(input, 11) ?: return null
        val content = input.substring(12, closeIdx).trim()
        val rest = input.substring(closeIdx + 1)
        val length = LengthParser.parse(content) ?: return null
        return Pair(TrackSize.FitContent(length), rest)
    }

    private fun findMatchingParen(input: String, startIdx: Int): Int? {
        var depth = 0
        for (i in startIdx until input.length) {
            when (input[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return null
    }
}
