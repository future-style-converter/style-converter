package com.styleconverter.runtime.layout.grid

// Phase 7b grid style-engine extractor.
//
// Produces the grid-related subset of the aggregate
// [com.styleconverter.runtime.layout.LayoutConfig]. This is a THIN
// translation layer on top of the legacy [GridExtractor] — we parse the IR
// once via the legacy extractor (which already handles every fixture flavor
// in examples/properties/layout/grid-*.json) and then project that result
// into the style-engine's [GridTrackList] / [GridPlacement] / [GridLinePair]
// vocabulary.
//
// Rationale for two extractors side-by-side:
//   - The legacy GridExtractor is shared with the existing GridRenderer
//     rendering path which cannot be retired until ComponentRenderer is
//     rewritten (Phase 7 step 6). Keeping it untouched means zero visual
//     regressions during this phase.
//   - The new GridLayoutExtractor feeds the aggregate LayoutConfig, which is
//     the surface future phases will switch ComponentRenderer over to.
//
// Every incoming property fixture shape is parsed by GridExtractor already —
// see extractTrackList / extractTemplateAreas / extractGridLine in that file
// for the canonical shape handling (px/fr/%, auto, min/max-content,
// minmax(), repeat(N, ...), repeat(auto-fill|auto-fit, minmax(...)).

import com.styleconverter.runtime.core.types.ValueExtractors
import com.styleconverter.runtime.layout.GridLine
import com.styleconverter.runtime.layout.GridLinePair
import com.styleconverter.runtime.layout.GridPlacement
import com.styleconverter.runtime.layout.GridTrackList
import com.styleconverter.runtime.layout.Track
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Result of [GridLayoutExtractor.extract]. Bundles the container + item
 * pieces so the aggregate LayoutConfig builder can copy them onto the right
 * fields in one go.
 */
data class GridLayoutExtract(
    val templateColumns: GridTrackList? = null,
    val templateRows: GridTrackList? = null,
    val templateAreas: List<List<String>>? = null,
    val autoColumns: GridTrackList? = null,
    val autoRows: GridTrackList? = null,
    val autoFlow: com.styleconverter.runtime.layout.GridAutoFlow? = null,
    val gridArea: GridPlacement? = null,
    val gridColumn: GridLinePair? = null,
    val gridRow: GridLinePair? = null,
    val justifyItems: com.styleconverter.runtime.layout.AlignmentKeyword? = null,
    val justifySelf: com.styleconverter.runtime.layout.AlignmentKeyword? = null,
)

object GridLayoutExtractor {

    /**
     * Extract grid layout data from an IR property list.
     *
     * Unrecognised / unsupported variants fall through with a null field —
     * the aggregate config's Applier uses null to mean "inherit / initial,"
     * which in turn means the legacy rendering path keeps running for that
     * component. This keeps unsupported-value failures non-fatal: a single
     * exotic track silently degrades to legacy instead of crashing render.
     */
    fun extract(properties: List<Pair<String, JsonElement?>>): GridLayoutExtract {
        // Start from the empty shell — non-null fields accumulate as we walk.
        var out = GridLayoutExtract()
        for ((type, data) in properties) {
            out = when (type) {
                // --- explicit tracks ---
                "GridTemplateColumns" -> out.copy(templateColumns = parseTrackList(data))
                "GridTemplateRows" -> out.copy(templateRows = parseTrackList(data))
                // grid-template-areas: list of rows of area names (or "." for
                // empty). The legacy GridTemplateAreas.parse() handles the
                // name→placement map; we only need the 2-D grid here so the
                // aggregate LayoutConfig can own its own representation.
                "GridTemplateAreas" -> out.copy(templateAreas = parseAreasGrid(data))
                // --- implicit auto tracks ---
                "GridAutoColumns" -> out.copy(autoColumns = parseTrackList(data))
                "GridAutoRows" -> out.copy(autoRows = parseTrackList(data))
                "GridAutoFlow" -> out.copy(autoFlow = parseAutoFlow(data))
                // --- item placement ---
                "GridArea" -> out.copy(gridArea = parseGridArea(data))
                "GridColumnStart" -> out.copy(gridColumn = (out.gridColumn ?: GridLinePair.Auto).copy(start = parseLine(data)))
                "GridColumnEnd" -> out.copy(gridColumn = (out.gridColumn ?: GridLinePair.Auto).copy(end = parseLine(data)))
                "GridRowStart" -> out.copy(gridRow = (out.gridRow ?: GridLinePair.Auto).copy(start = parseLine(data)))
                "GridRowEnd" -> out.copy(gridRow = (out.gridRow ?: GridLinePair.Auto).copy(end = parseLine(data)))
                // --- item alignment ---
                "JustifyItems" -> out.copy(justifyItems = parseAlign(data))
                "JustifySelf" -> out.copy(justifySelf = parseAlign(data))
                // TODO(phase7b): GridTemplate shorthand, GridAutoTrack,
                // AlignTracks, JustifyTracks, MasonryAutoFlow — parse-only
                // today; no semantic support on Compose.
                else -> out
            }
        }
        return out
    }

    // --- Track list parsing ------------------------------------------------
    //
    // Handled shapes (from GridExtractor.extractTrackList reference):
    //   - JsonArray of track-size objects
    //   - Single track-size object
    //   - Primitive string "1fr" / "auto" / "min-content" / etc.
    //
    // We also peel off repeat(...) wrappers: repeat(N, ...) expands inline,
    // repeat(auto-fill|auto-fit, minmax(a,b)) becomes [Adaptive].

    private fun parseTrackList(json: JsonElement?): GridTrackList? {
        if (json == null) return null
        val tracks = when (json) {
            is JsonArray -> json.flatMap { expandOne(it) }
            else -> expandOne(json)
        }
        if (tracks.isEmpty()) return null
        // Adaptive: if there's exactly one Adaptive-capable track and no
        // others, fold into GridTrackList.Adaptive. Otherwise emit Explicit.
        if (tracks.size == 1 && tracks.first() is AdaptiveMarker) {
            val m = tracks.first() as AdaptiveMarker
            return GridTrackList.Adaptive(minSize = m.minSize, autoFit = m.autoFit)
        }
        // Strip adaptive markers in mixed lists by treating them as a single
        // auto track (degrade) — legacy fixture files don't mix adaptive +
        // explicit tracks so this path is conservative.
        val explicit = tracks.map { if (it is AdaptiveMarker) Track.Auto else it as Track }
        return GridTrackList.Explicit(explicit)
    }

    // Sentinel emitted by expandOne() for repeat(auto-fill|auto-fit, ...).
    // Bubbled up so parseTrackList can decide whether to make the whole
    // list Adaptive.
    private data class AdaptiveMarker(val minSize: Float, val autoFit: Boolean)

    /** Expand a single track element into one or more Track-or-AdaptiveMarker. */
    private fun expandOne(json: JsonElement): List<Any> = when (json) {
        is JsonObject -> when {
            // repeat(count, tracks). CANONICAL v2 wire (TrackSizeSerializer in
            // the converter): {"repeat": <count LITERAL or "auto-fill"/"auto-fit">,
            // "tracks": [<TrackSize>...]} — count and tracks are SIBLING keys on
            // the SAME object. The old code hard-cast json["repeat"] to
            // JsonObject expecting a nested {count, tracks} envelope; the wire's
            // count literal (e.g. 4 for `repeat(4, 1fr)`) then threw
            // ClassCastException and killed the whole render (pairs-01 lost
            // 28/44 Android captures to PW_Background_Layout_01's inert
            // `grid-template-rows: repeat(4, 1fr)`). We now pass the WHOLE
            // object; expandRepeat handles both the canonical flat shape and
            // the legacy nested one.
            json["repeat"] != null -> expandRepeat(json)
            // minmax(a, b). Canonical v2 wire is the FLAT {"min":…, "max":…}
            // object (TrackSize.MinMax serializer) — the "minmax" wrapper key
            // never ships but stays supported for the legacy fixtures.
            json["minmax"] != null -> {
                val mm = json["minmax"] as JsonObject
                val min = singleTrack(mm["min"]) ?: Track.Auto
                val max = singleTrack(mm["max"]) ?: Track.Auto
                listOf(Track.MinMax(min, max))
            }
            json["min"] != null && json["max"] != null -> {
                val min = singleTrack(json["min"]) ?: Track.Auto
                val max = singleTrack(json["max"]) ?: Track.Auto
                listOf(Track.MinMax(min, max))
            }
            // fit-content(limit) — canonical v2 wire key is "fit"
            // (TrackSize.FitContent serializer: {"fit": <IRLength>}); the
            // "fitContent"/"fit-content" spellings never ship but remain for
            // legacy compatibility. Limit is either a primitive (px number)
            // or an object {px: N}. Check object first because JsonPrimitive
            // access on an object throws.
            json["fit"] != null || json["fitContent"] != null || json["fit-content"] != null -> {
                val limitJson = json["fit"] ?: json["fitContent"] ?: json["fit-content"]!!
                val px = when (limitJson) {
                    is JsonObject -> limitJson["px"]?.jsonPrimitive?.floatOrNull ?: 0f
                    is JsonPrimitive -> limitJson.floatOrNull ?: 0f
                    else -> 0f
                }
                listOf(Track.FitContent(px))
            }
            else -> listOfNotNull(singleTrack(json))
        }
        // Bare NUMBER primitives are percentages on the wire (IRPercentage
        // serializes as a raw number, mirroring the web engine's trackSize()
        // "bare number → %" rule); only STRING primitives carry px/fr text.
        is JsonPrimitive ->
            if (!json.isString && json.floatOrNull != null) listOf(Track.Percent(json.floatOrNull!!))
            else listOfNotNull(singleTrackFromString(json.content))
        else -> emptyList()
    }

    /**
     * Expand a repeat() carrier object into its repetitions.
     * [obj] is the WHOLE track object. Two accepted shapes:
     *   - canonical v2 wire: {"repeat": <count>, "tracks": [...]} (flat)
     *   - legacy nested:     {"repeat": {"count": <count>, "tracks": [...]}}
     */
    private fun expandRepeat(obj: JsonObject): List<Any> {
        // Nested legacy envelope → unwrap; canonical wire → the object itself
        // carries count under "repeat" and tracks as a sibling.
        val nested = obj["repeat"] as? JsonObject
        val repeat = nested ?: obj
        val countElem = if (nested != null) nested["count"] else obj["repeat"]
        val tracksElem = repeat["tracks"]
            ?: return emptyList() // TODO: log; shouldn't happen with our parser
        // Parse the inner track list once — every repetition is a copy.
        val innerList = when (tracksElem) {
            is JsonArray -> tracksElem.flatMap { expandOne(it) }
            else -> expandOne(tracksElem)
        }
        // Resolve the count.
        val countKeyword = (countElem as? JsonPrimitive)?.contentOrNull?.lowercase()
            ?: (countElem as? JsonObject)?.get("keyword")?.jsonPrimitive?.contentOrNull?.lowercase()
        if (countKeyword == "auto-fill" || countKeyword == "auto-fit") {
            // Pull the minmax min-side as the adaptive minSize — matches
            // the CSS Grid auto-fill/auto-fit semantics used by every
            // `repeat(auto-*, minmax(a, b))` fixture.
            val firstMinMax = innerList.firstOrNull() as? Track.MinMax
            val minPx = when (val min = firstMinMax?.min) {
                is Track.Fixed -> min.px
                else -> 80f // fallback min size if we can't resolve
            }
            return listOf(AdaptiveMarker(minSize = minPx, autoFit = countKeyword == "auto-fit"))
        }
        val n = (countElem as? JsonPrimitive)?.intOrNull
            ?: (countElem as? JsonObject)?.get("count")?.jsonPrimitive?.intOrNull
            ?: 1
        // Expand N copies of innerList in sequence.
        val expanded = mutableListOf<Any>()
        repeat(n) { expanded.addAll(innerList) }
        return expanded
    }

    /** One Track from an object like {fr: 1} / {px: 80} / {percent: 25} / {keyword:"auto"}. */
    private fun singleTrack(json: JsonElement?): Track? {
        if (json == null) return null
        return when (json) {
            is JsonObject -> {
                json["fr"]?.jsonPrimitive?.floatOrNull?.let { return Track.Flexible(it) }
                json["px"]?.jsonPrimitive?.floatOrNull?.let { return Track.Fixed(it) }
                json["percent"]?.jsonPrimitive?.floatOrNull?.let { return Track.Percent(it) }
                json["pct"]?.jsonPrimitive?.floatOrNull?.let { return Track.Percent(it) }
                when (json["keyword"]?.jsonPrimitive?.content?.lowercase()) {
                    "auto" -> Track.Auto
                    "min-content" -> Track.Intrinsic(Track.IntrinsicKind.MinContent)
                    "max-content" -> Track.Intrinsic(Track.IntrinsicKind.MaxContent)
                    else -> null
                }
            }
            is JsonPrimitive -> singleTrackFromString(json.content)
            else -> null
        }
    }

    /** Parse an inline track string like "1fr", "80px", "auto", "25%". */
    private fun singleTrackFromString(raw: String): Track? {
        val s = raw.trim().lowercase()
        return when {
            s == "auto" -> Track.Auto
            s == "min-content" -> Track.Intrinsic(Track.IntrinsicKind.MinContent)
            s == "max-content" -> Track.Intrinsic(Track.IntrinsicKind.MaxContent)
            s.endsWith("fr") -> s.removeSuffix("fr").toFloatOrNull()?.let { Track.Flexible(it) }
            s.endsWith("%") -> s.removeSuffix("%").toFloatOrNull()?.let { Track.Percent(it) }
            s.endsWith("px") -> s.removeSuffix("px").toFloatOrNull()?.let { Track.Fixed(it) }
            else -> s.toFloatOrNull()?.let { Track.Fixed(it) }
        }
    }

    // --- Areas grid --------------------------------------------------------
    //
    // Accepts: JsonArray of strings, JsonObject with "rows" / "areas" key,
    // or a single primitive string with newline separators. Mirrors
    // GridExtractor.extractTemplateAreas for wire-format compatibility.
    private fun parseAreasGrid(json: JsonElement?): List<List<String>>? {
        if (json == null) return null
        val rows: List<String> = when (json) {
            is JsonArray -> json.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
                .filter { it.isNotEmpty() }
            is JsonObject -> (json["rows"] as? JsonArray ?: json["areas"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
                ?.filter { it.isNotEmpty() } ?: emptyList()
            is JsonPrimitive -> json.contentOrNull
                ?.split("\n")
                ?.map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
            else -> emptyList()
        }
        if (rows.isEmpty()) return null
        // Each row is whitespace-split into cells; "." stays as "." (our
        // convention for empty). We keep the raw 2-D so the applier can run
        // its own lookup without reparsing.
        return rows.map { it.split(Regex("\\s+")) }
    }

    // --- Auto flow ---------------------------------------------------------

    private fun parseAutoFlow(json: JsonElement?): com.styleconverter.runtime.layout.GridAutoFlow? {
        val kw = ValueExtractors.extractKeyword(json)?.lowercase() ?: return null
        return when {
            kw.contains("column") && kw.contains("dense") -> com.styleconverter.runtime.layout.GridAutoFlow.ColumnDense
            kw.contains("row") && kw.contains("dense") -> com.styleconverter.runtime.layout.GridAutoFlow.RowDense
            kw == "column" -> com.styleconverter.runtime.layout.GridAutoFlow.Column
            kw == "row" -> com.styleconverter.runtime.layout.GridAutoFlow.Row
            else -> null
        }
    }

    // --- Grid area / line parsing -----------------------------------------

    /**
     * grid-area accepts (per CSS): a single area name, OR the four-line
     * shorthand. IR may emit either as a primitive string ("header") or as
     * an object {name}/{rowStart, columnStart, rowEnd, columnEnd}.
     */
    private fun parseGridArea(json: JsonElement?): GridPlacement? {
        if (json == null) return null
        return when (json) {
            is JsonPrimitive -> {
                val s = json.contentOrNull?.trim() ?: return null
                if (s.isEmpty()) null
                else if (s.all { it.isLetterOrDigit() || it == '-' || it == '_' }) GridPlacement.Named(s)
                else null // Mixed-form strings (e.g. "1 / 2 / 3 / 4") would need
                          // richer parsing; legacy path still handles those.
            }
            is JsonObject -> {
                json["name"]?.jsonPrimitive?.contentOrNull?.let { return GridPlacement.Named(it) }
                val rs = parseLine(json["rowStart"])
                val cs = parseLine(json["columnStart"])
                val re = parseLine(json["rowEnd"])
                val ce = parseLine(json["columnEnd"])
                if (rs is GridLine.Auto && cs is GridLine.Auto && re is GridLine.Auto && ce is GridLine.Auto) null
                else GridPlacement.Lines(rs, cs, re, ce)
            }
            else -> null
        }
    }

    /**
     * Line value parsing. Accepts integers (line numbers, CSS 1-based),
     * "auto", and the object form {span: N}.
     */
    private fun parseLine(json: JsonElement?): GridLine {
        if (json == null) return GridLine.Auto
        return when (json) {
            is JsonPrimitive -> {
                val s = json.contentOrNull?.trim()?.lowercase() ?: return GridLine.Auto
                when {
                    s == "auto" -> GridLine.Auto
                    s.startsWith("span ") -> s.removePrefix("span ").trim().toIntOrNull()
                        ?.let { GridLine.Span(it) } ?: GridLine.Auto
                    else -> s.toIntOrNull()?.let { GridLine.Line(it) } ?: GridLine.Auto
                }
            }
            is JsonObject -> {
                json["span"]?.jsonPrimitive?.intOrNull?.let { return GridLine.Span(it) }
                json["line"]?.jsonPrimitive?.intOrNull?.let { return GridLine.Line(it) }
                json["value"]?.jsonPrimitive?.intOrNull?.let { return GridLine.Line(it) }
                GridLine.Auto
            }
            else -> GridLine.Auto
        }
    }

    // --- Alignment keyword --------------------------------------------------

    private fun parseAlign(json: JsonElement?): com.styleconverter.runtime.layout.AlignmentKeyword? {
        val kw = ValueExtractors.extractKeyword(json)?.lowercase() ?: return null
        return when (kw) {
            "start" -> com.styleconverter.runtime.layout.AlignmentKeyword.Start
            "end" -> com.styleconverter.runtime.layout.AlignmentKeyword.End
            "center" -> com.styleconverter.runtime.layout.AlignmentKeyword.Center
            "stretch" -> com.styleconverter.runtime.layout.AlignmentKeyword.Stretch
            "flex-start" -> com.styleconverter.runtime.layout.AlignmentKeyword.FlexStart
            "flex-end" -> com.styleconverter.runtime.layout.AlignmentKeyword.FlexEnd
            "baseline" -> com.styleconverter.runtime.layout.AlignmentKeyword.Baseline
            "normal" -> com.styleconverter.runtime.layout.AlignmentKeyword.Normal
            "auto" -> com.styleconverter.runtime.layout.AlignmentKeyword.Auto
            else -> null
        }
    }
}
