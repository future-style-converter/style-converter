package com.styleconverter.runtime.lists

/**
 * Wave 25 (lane LF follow-up) — the `list-style` SHORTHAND, css-lists-3
 * §3.5: `<'list-style-position'> || <'list-style-image'> ||
 * <'list-style-type'>`.
 *
 * ## Why this exists — and why it is a DEFENSIVE path
 * [ListStyleExtractor.isListStyleProperty] has always claimed the
 * `ListStyle` IR type (it feeds `StyleApplier.isPropertySupported`,
 * `ComponentRenderer.INHERITED_PROPERTY_TYPES` and the marker's
 * parent-property filter), but `extractListStyleConfig` had NO branch for
 * it: a wire doc carrying the unexpanded shorthand claimed the property
 * and then applied nothing. This file makes the claim honest.
 *
 * The branch is nonetheless UNREACHABLE from this repo's own converter,
 * which was checked rather than assumed (wave-25 live run,
 * `--from css --to ir` on a fixture declaring `list-style: square inside
 * url(bullet.png)` / `upper-roman` / `none` / `inherit`):
 *
 *  - `ShorthandRegistry.kt` maps `"list-style"` to `ListStyleExpander`,
 *    which ALWAYS expands — the converter logged `Expanded 'list-style:
 *    square inside url(bullet.png)' → list-style-type, list-style-position,
 *    list-style-image` for every input;
 *  - the emitted IR carried only `ListStyleType` / `ListStylePosition` /
 *    `ListStyleImage` (plus `Generic` for the values no longhand parser
 *    models), never `{"type":"ListStyle"}`;
 *  - there is no `ListStyleProperty.kt` in the converter's irmodels tree
 *    and no `"list-style"` entry in `PropertyParserRegistry`, so the type
 *    cannot be produced at all.
 *
 * `schema/spec/05-versioning.md` says unknown property types are
 * TOLERATED, so a FOREIGN producer may legitimately put the shorthand on
 * the wire — which is exactly the case this handles. Twin of iOS
 * `ListStyleShorthand.swift`.
 */
object ListStyleShorthand {

    /** css-lists-3 §3.2 — the two `list-style-position` keywords. */
    private val POSITION_KEYWORDS = setOf("inside", "outside")

    /**
     * Fold a `list-style` shorthand value onto [base].
     *
     * Shorthand semantics (css-cascade-4 §3): components the value omits
     * are set to their INITIAL values, not left at whatever the element
     * inherited — so `list-style: square` also forces `outside` / no
     * image. That is why this returns a fully rebuilt config rather than
     * a partial copy.
     *
     * @param raw the wire value as a keyword string (the extractor pulls
     *   it through `ValueExtractors.extractKeyword`, which accepts a bare
     *   string as well as `{keyword|value: …}` objects).
     * @return [base] UNCHANGED whenever the declaration is invalid or
     *   carries a global keyword we cannot expand — CSS drops an invalid
     *   declaration wholesale (css-syntax-3 §9), so keeping the running
     *   value is the faithful behaviour, not a silent fallthrough.
     */
    fun apply(base: ListStyleConfig, raw: String?): ListStyleConfig {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return base

        when (value.lowercase()) {
            // css-cascade-4 §7.3: on an INHERITED property (all three
            // list-style longhands are "Inherited: yes", css-lists-3 §3.1)
            // both `inherit` and `unset` mean "take the parent's computed
            // value" — which the inheritance merge has already put into
            // [base]. Keeping it IS the resolution.
            "inherit", "unset" -> return base
            // `revert`/`revert-layer` roll back to the previous cascade
            // ORIGIN, which the runtime does not model (we hold no
            // per-origin declaration stack). Documented gap: the running
            // value stands, which for a list container is the UA default
            // that ListStyleUaRule already installed — the right answer in
            // the common `revert`-to-UA case, wrong only when an author
            // rule in an earlier layer should have re-emerged.
            "revert", "revert-layer" -> return base
            // Every longhand back to its initial value (css-lists-3 §3.1:
            // disc / outside / none).
            "initial" -> return base.copy(
                listStyleType = ListStyleType.DISC,
                listStylePosition = ListStylePosition.OUTSIDE,
                listStyleImage = null
            )
        }

        var type: ListStyleType? = null
        var position: ListStylePosition? = null
        var image: String? = null
        // `none` is ambiguous between the type and image components; count
        // the occurrences and assign them after the explicit ones (below).
        var nones = 0

        for (token in tokenize(value)) {
            val lower = token.lowercase()
            when {
                lower == "none" -> nones++
                lower in POSITION_KEYWORDS -> {
                    // A repeated component makes the whole shorthand invalid.
                    if (position != null) return base
                    position = if (lower == "inside") ListStylePosition.INSIDE
                    else ListStylePosition.OUTSIDE
                }
                lower.startsWith("url(") && lower.endsWith(")") -> {
                    if (image != null) return base
                    // Slice by INDEX, not removeSurrounding("url(", …):
                    // css-syntax-3 §4.3 makes the function name
                    // case-insensitive, and the prefix test above already
                    // ran on the lowercased token, so `URL(a.png)` matched
                    // here but removeSurrounding compared the original
                    // casing and stripped nothing — the image string kept
                    // the whole `URL(a.png)` wrapper while the iOS twin
                    // (which drops 4 chars positionally) produced `a.png`.
                    // Caught by the wave-25 skeptic twin-dump diff.
                    image = unquote(token.substring(4, token.length - 1).trim())
                }
                else -> {
                    // Anything left must be a counter-style name. An
                    // unknown one (a custom @counter-style ident the wire
                    // cannot carry — same gap ListStyleExtractor documents)
                    // invalidates the declaration rather than half-applying it.
                    val parsed = ListStyleExtractor.typeFromKeyword(lower) ?: return base
                    if (type != null) return base
                    type = parsed
                }
            }
        }

        // css-lists-3 §3.5: a single `none` sets BOTH list-style-type and
        // list-style-image to none; two `none`s set one each. Fill the
        // still-empty slots in that order. (Image's initial value IS none,
        // so filling it is a no-op on the config — the loop below exists to
        // count slots, which is what makes a third `none` detectably invalid.)
        var remaining = nones
        if (remaining > 0 && type == null) { type = ListStyleType.NONE; remaining-- }
        if (remaining > 0 && image == null) { remaining-- }
        if (remaining > 0) return base

        return base.copy(
            listStyleType = type ?: ListStyleType.DISC,
            listStylePosition = position ?: ListStylePosition.OUTSIDE,
            // Omitted ⇒ initial ⇒ `none` ⇒ null on this config.
            listStyleImage = image
        )
    }

    /**
     * Strip ONE layer of matching quotes from a `url()` body — exactly
     * what the iOS twin's `unquote` does. (The first draft chained
     * `removeSurrounding("\"")` then `removeSurrounding("'")`, which
     * peels TWO layers off `url("'a'")` where iOS peels one.)
     */
    private fun unquote(raw: String): String {
        val trimmed = raw.trim()
        for (quote in listOf("\"", "'")) {
            if (trimmed.length >= 2 && trimmed.startsWith(quote) && trimmed.endsWith(quote)) {
                return trimmed.substring(1, trimmed.length - 1)
            }
        }
        return trimmed
    }

    /**
     * Whitespace split that does NOT break inside parentheses, so
     * `url(a b.png)` survives as one token. Mirrors the converter's
     * `ListStyleExpander.tokenize` so reader and runtime agree on where a
     * component ends.
     */
    private fun tokenize(value: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        for (char in value) {
            when {
                char == '(' -> { depth++; current.append(char) }
                char == ')' -> { depth--; current.append(char) }
                char.isWhitespace() && depth == 0 -> {
                    if (current.isNotEmpty()) { tokens.add(current.toString()); current.setLength(0) }
                }
                else -> current.append(char)
            }
        }
        if (current.isNotEmpty()) tokens.add(current.toString())
        return tokens
    }
}
