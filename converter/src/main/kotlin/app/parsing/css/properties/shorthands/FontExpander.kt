package app.parsing.css.properties.shorthands

/**
 * Expands `font` shorthand into font-* longhands.
 * Syntax: [font-style] [font-variant] [font-weight] [font-stretch] font-size[/line-height] font-family
 * Also handles system fonts: caption, icon, menu, message-box, small-caption, status-bar
 */
object FontExpander : ShorthandExpander {
    private val systemFonts = setOf("caption", "icon", "menu", "message-box", "small-caption", "status-bar")
    private val globalKeywords = setOf("inherit", "initial", "unset", "revert", "revert-layer")
    private val styleKeywords = setOf("normal", "italic", "oblique")
    private val variantKeywords = setOf("normal", "small-caps")
    private val weightKeywords = setOf("normal", "bold", "lighter", "bolder", "100", "200", "300", "400", "500", "600", "700", "800", "900")
    private val stretchKeywords = setOf("normal", "ultra-condensed", "extra-condensed", "condensed", "semi-condensed", "semi-expanded", "expanded", "extra-expanded", "ultra-expanded")
    private val angleUnits = setOf("deg", "grad", "rad", "turn")

    override fun expand(value: String): Map<String, String> {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        if (lower in globalKeywords) {
            // css-cascade-4 §3 ("Shorthand Properties"): "If a shorthand is
            // specified as one of the CSS-wide keywords, it sets all of its
            // sub-properties to that keyword, including any that are
            // reset-only sub-properties." The keyword is forwarded VERBATIM
            // (as authored — the longhand parsers own case-folding) to every
            // sub-property this expander models, in the css-fonts-4 §2.7
            // grammar order.
            //
            // `font-variant` is NOT a longhand: it is itself a shorthand
            // (css-fonts-4 §6.11) whose seven sub-properties are the real
            // sub-properties of `font`. Forwarding the keyword under the
            // shorthand NAME (the pre-retrospective behaviour) put an
            // unmapped Generic{propertyName:"font-variant"} on the wire —
            // no longhand parser is registered for a shorthand name — which
            // finding A8#0 measured on `font: inherit`. Delegating to
            // FontVariantExpander's CSS-wide branch keeps every key leaving
            // this expander a longhand, exactly as the non-keyword path
            // below emits `font-variant-caps`, never `font-variant`.
            //
            // Tracked consequence, not a silent one: none of the seven
            // font-variant longhand parsers has a CSS-wide arm today, so
            // `font: inherit` puts seven `_unmapped: true` Generics on the
            // wire where it used to put one — every key now NAMEABLE by a
            // future longhand parser, and every one visible to PropertyTracker
            // instead of unreachable behind a shorthand name. (Zero corpus
            // carriers: no fixture under fixtures/ declares `font` or
            // `font-variant` with a CSS-wide keyword — retro R9 grep.)
            //
            // Known, deliberate gap: §2.7 also makes `font` RESET the
            // sub-properties with no slot in its grammar
            // (font-feature-settings, font-kerning, font-language-override,
            // font-optical-sizing, font-size-adjust, font-variation-settings
            // — and, on the non-keyword path below, the six font-variant
            // longhands other than -caps). This expander never writes those:
            // `font: 12px Arial` leaves them alone. The CSS-wide path here
            // does reach all seven font-variant longhands, because it
            // delegates to the §6.11 shorthand. Closing the reset gap is a
            // separate change; PropertiesParser's "Expanded 'font: …' → …"
            // log line lists exactly which longhands were written, so the
            // omission is visible in every convert log.
            val out = LinkedHashMap<String, String>()
            out["font-style"] = trimmed
            out.putAll(FontVariantExpander.expand(trimmed)) // the seven §6.11 sub-properties
            out["font-weight"] = trimmed
            out["font-stretch"] = trimmed
            out["font-size"] = trimmed
            out["line-height"] = trimmed
            out["font-family"] = trimmed
            return out
        }

        // System fonts - pass through as raw
        if (lower in systemFonts) {
            return mapOf("font-family" to trimmed)
        }

        val result = mutableMapOf<String, String>()
        val tokens = joinSlashRuns(tokenize(trimmed))

        var i = 0
        var foundSize = false

        // Parse optional values before size
        while (i < tokens.size && !foundSize) {
            val token = tokens[i]
            val tokenLower = token.lowercase()

            when {
                tokenLower == "oblique" -> {
                    // Check if next token is an angle (oblique <angle> syntax)
                    if (i + 1 < tokens.size && isAngleValue(tokens[i + 1])) {
                        result["font-style"] = "$token ${tokens[i + 1]}"
                        i += 2
                    } else {
                        result["font-style"] = token
                        i++
                    }
                }
                tokenLower in styleKeywords && !result.containsKey("font-style") -> {
                    result["font-style"] = token
                    i++
                }
                tokenLower in variantKeywords && !result.containsKey("font-variant-caps") && tokenLower != "normal" -> {
                    // css-fonts-4 §2.7: the shorthand's variant slot is
                    // `<font-variant-css2> = normal | small-caps` and the
                    // longhand it sets explicitly is `font-variant-caps`
                    // (§2.7 "Set Explicitly" list) — `font-variant` itself is
                    // a shorthand (§6.11) and has no longhand parser, so
                    // writing it here leaked `Generic{propertyName:
                    // "font-variant", rawValue:"small-caps"}` onto the wire
                    // (wave49-final css-cascade/all-prop-001, `font: bold
                    // italic small-caps 20px monospace`).
                    result["font-variant-caps"] = token
                    i++
                }
                tokenLower in weightKeywords && !result.containsKey("font-weight") -> {
                    result["font-weight"] = token
                    i++
                }
                tokenLower in stretchKeywords && !result.containsKey("font-stretch") && tokenLower != "normal" -> {
                    result["font-stretch"] = token
                    i++
                }
                isSizeValue(tokenLower) -> {
                    foundSize = true
                }
                else -> i++
            }
        }

        // Parse font-size and optional line-height
        if (i < tokens.size) {
            val sizeToken = tokens[i]
            if (sizeToken.contains("/")) {
                val parts = sizeToken.split("/", limit = 2)
                result["font-size"] = parts[0]
                result["line-height"] = parts[1]
            } else {
                result["font-size"] = sizeToken
            }
            i++
        }

        // Rest is font-family
        if (i < tokens.size) {
            result["font-family"] = tokens.subList(i, tokens.size).joinToString(" ")
        }

        // css-fonts-4 §4.3 ("Font shorthand"): the `font` shorthand RESETS every
        // longhand it can set, including the ones omitted from the declaration —
        // "the `font` shorthand property … resets `line-height` to `normal`" when
        // no `/<line-height>` component is present. Before this line the expander
        // simply never mentioned `line-height`, so `font: 92px Arial` left the
        // INHERITED value in force: on the WPT stage that is the harness's
        // `:where(body){line-height:1.25}` calibration (index.html wpt rules /
        // capture-browser-ref.mjs REF_LINE_HEIGHT) → a 115px line box at 92px,
        // while Chromium's own render of the very same test resets to `normal`
        // and lays Arial out at its natural ~1.149em box ≈ 105.7px
        // (text-decoration-dotted-001/002). A directly-matching declaration
        // always beats an inherited one regardless of the source rule's zero
        // specificity, so the reset is what the browser reference does — emitting
        // it here is what lets the runtimes see it at all.
        //
        // Two guards keep the reset honest:
        //   • only when the shorthand actually PARSED as the size/family form
        //     (`font-size` present) — a malformed value must not synthesize a
        //     longhand out of nothing (CSS2 §4.2: an invalid declaration is
        //     dropped whole);
        //   • only when the `<size>/<line-height>` slash form did NOT already
        //     write `line-height` above (that explicit value wins, obviously).
        // The system-font (`caption`, `menu`, …) and global-keyword (`inherit`,
        // …) paths return earlier and are untouched: system fonts resolve their
        // whole font description from the platform, and the global path already
        // forwards the keyword to `line-height` verbatim.
        if (result.containsKey("font-size") && !result.containsKey("line-height")) {
            result["line-height"] = "normal"
        }

        return result
    }

    /**
     * Re-joins a `<font-size> / <line-height>` run that `tokenize` split apart.
     *
     * The `/` in the `font` shorthand is a CSS *delimiter token*
     * (css-fonts-4 §4.3 grammar `<font-size> [ / <line-height> ]?`), and
     * css-syntax-3 §5 lets arbitrary whitespace sit on either side of it — all
     * four of `16px/2`, `16px/ 2`, `16px /2` and `16px / 2` are the SAME valid
     * declaration. `tokenize` only splits on spaces, so the last three arrived
     * here as separate tokens: the size branch below then saw a bare `16px`,
     * wrote no `line-height`, and the REST — `/ 2 Georgia` — became the
     * font-family. Before wave 22 that merely lost the author's line-height to
     * inheritance; once the §4.3 reset landed it actively wrote
     * `line-height: normal` OVER a declaration that said `2`, i.e. turned a
     * silent omission into a confident wrong answer (`font: 16px / 2 Georgia`
     * → `{"original":"normal"}`, verified against a live `:converter:run`).
     *
     * Joining is done on TOKENS, not on the raw string, so it can never reach
     * inside a quoted family name: `tokenize` keeps `"Foo/Bar"` in one token
     * (quotes are tracked), and this pass only ever merges across a token
     * BOUNDARY. An unquoted family cannot contain `/` — it is not a valid
     * ident character — so there is no other way a `/` reaches this list.
     */
    private fun joinSlashRuns(tokens: List<String>): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            var token = tokens[i]
            i++
            // `16px /2` and `16px / 2`: a token that STARTS with the delimiter
            // belongs to the size that precedes it.
            while (token.startsWith("/") && out.isNotEmpty()) {
                out[out.size - 1] = out.last() + token
                token = ""
                break
            }
            if (token.isEmpty()) {
                // The merge above consumed it; a lone `/` still needs its
                // right-hand side pulled in (the `16px / 2` case).
                if (out.isNotEmpty() && out.last().endsWith("/") && i < tokens.size) {
                    out[out.size - 1] = out.last() + tokens[i]
                    i++
                }
                continue
            }
            // `16px/ 2`: a token that ENDS with the delimiter is missing its
            // right-hand side.
            if (token.endsWith("/") && i < tokens.size) {
                token += tokens[i]
                i++
            }
            out.add(token)
        }
        return out
    }

    private fun isSizeValue(value: String): Boolean {
        val sizeKeywords = setOf("xx-small", "x-small", "small", "medium", "large", "x-large", "xx-large", "xxx-large", "smaller", "larger")
        val base = value.split("/")[0]
        // Exclude angle values (deg, grad, rad, turn) from being treated as size
        if (isAngleValue(base)) return false
        return base in sizeKeywords || base.matches(Regex("-?[\\d.]+[a-z%]*"))
    }

    private fun isAngleValue(value: String): Boolean {
        val lower = value.lowercase()
        return angleUnits.any { lower.endsWith(it) && lower.dropLast(it.length).toDoubleOrNull() != null }
    }

    private fun tokenize(value: String): List<String> {
        val tokens = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var quoteChar = ' '
        var depth = 0

        for (char in value) {
            when {
                (char == '"' || char == '\'') && depth == 0 -> {
                    if (!inQuotes) {
                        inQuotes = true
                        quoteChar = char
                    } else if (char == quoteChar) {
                        inQuotes = false
                    }
                    current.append(char)
                }
                char == '(' -> { depth++; current.append(char) }
                char == ')' -> { depth--; current.append(char) }
                char == ' ' && depth == 0 && !inQuotes -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current = StringBuilder()
                    }
                }
                else -> current.append(char)
            }
        }
        if (current.isNotEmpty()) tokens.add(current.toString())
        return tokens
    }
}
