package app.parsing.css.properties.longhands.layout.grid

// Shared <grid-line> reader. Same package as every caller, so no import.
import app.irmodels.properties.layout.grid.GridLine

/**
 * The css-grid-2 `<grid-line>` grammar:
 * `auto | <custom-ident> | [ <integer> && <custom-ident>? ] | [ span && [
 * <integer> || <custom-ident> ] ]` — read into the IR [GridLine] variants.
 *
 * A6#14: five copies of this reader existed — one in each of
 * GridColumnStart / GridColumnEnd / GridRowStart / GridRowEnd (four
 * byte-identical) and a fifth in GridAreaPropertyParser. The four longhands
 * lowercase the value first; the `grid-area` copy does NOT.
 *
 * That asymmetry is a REAL divergence, not a formatting one: a line NAME is a
 * `<custom-ident>`, which css-values-4 defines as case-SENSITIVE, so the
 * longhands' `.lowercase()` corrupts `grid-column-start: MyLine` into
 * `myline` while `grid-area: MyLine / …` keeps it. Folding the five bodies
 * into one must therefore preserve the difference or it would be a silent
 * behaviour change: [caseFold] carries it as an explicit parameter, so the
 * output is byte-identical to before and the defect is now visible in ONE
 * place instead of being invisible across five. Fixing it (dropping the fold
 * from the longhands) is a behaviour change and is left to its own change.
 */
internal object GridLineParsing {

    /**
     * @param value the raw `<grid-line>` text
     * @param caseFold true to lowercase before matching — the four longhand
     *        parsers' historical behaviour; `grid-area` passes false.
     */
    fun parse(value: String, caseFold: Boolean): GridLine? {
        // The only difference between the five originals was this fold.
        val trimmed = if (caseFold) value.trim().lowercase() else value.trim()
        if (trimmed == "auto") {
            return GridLine.Auto()
        }
        if (trimmed.startsWith("span ")) {
            val spanValue = trimmed.substring(5).trim()
            val spanCount = spanValue.toIntOrNull()
            return if (spanCount != null) {
                GridLine.Span(spanCount)
            } else {
                GridLine.SpanName(spanValue)
            }
        }
        val lineNumber = trimmed.toIntOrNull()
        if (lineNumber != null) {
            return GridLine.LineNumber(lineNumber)
        }
        return GridLine.LineName(trimmed)
    }
}
