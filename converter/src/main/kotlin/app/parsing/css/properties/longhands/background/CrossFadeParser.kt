package app.parsing.css.properties.longhands.background

// cross-fade() parsing (css-images-4 §2.6.2) — split from
// BackgroundImagePropertyParser per the ≤200-line file rule (the parser
// crossed ~440 lines when A-RC2 landed). Same package: the two objects
// call each other (the <image> grammar is mutually recursive —
// cross-fade args may themselves be gradients, urls, or cross-fades).
import app.irmodels.IRPercentage
import app.irmodels.properties.background.BackgroundImageProperty
import app.parsing.css.properties.primitiveParsers.ColorParser
import app.parsing.css.properties.primitiveParsers.PercentageParser
import app.parsing.css.properties.primitiveParsers.TokenizationUtils

internal object CrossFadeParser {

    // cross-fade() — css-images-4 §2.6.2. Two syntaxes:
    //   modern:  cross-fade( [<percentage>? && [<image>|<color>]]# )
    //   legacy:  cross-fade( <image>, <image>, <percentage> )  (the
    //            -webkit- two-argument form; percentage applies to the
    //            SECOND image, first gets the remainder)
    // Weights stay AUTHORED (null = omitted) — the spec's normalization is
    // executed by the runtimes' CrossFadeMath twins so all three platforms
    // share one pinned formula. Receives ORIGINAL bytes (url() payloads).
    // css-images-4 §2.6.2: cross-fade percentages are clamped to the
    // [0%, 100%] range at computed-value time. Clamping HERE (not in the
    // CrossFadeMath twins) keeps the wire inside the range every engine's
    // alpha APIs accept — an unclamped `cross-fade(red, blue, 150%)` put
    // weight −50/150 on the wire and Compose's drawRect(alpha=…) requires
    // 0..1 (the twins' >100-sum scale-down still guards multi-arg sums).
    private fun clamp(p: IRPercentage): IRPercentage =
        IRPercentage(p.value.coerceIn(0.0, 100.0))

    internal fun parse(value: String): BackgroundImageProperty.BackgroundImage? {
        val content = TokenizationUtils.extractFunctionContent(value, "cross-fade") ?: return null
        val parts = TokenizationUtils.splitByComma(content)
        // Zero-arg cross-fade() is invalid per the grammar.
        if (parts.isEmpty()) return null

        // Legacy detection: exactly three segments and the LAST is a bare
        // percentage (the two-arg trailing-percent -webkit shape).
        if (parts.size == 3) {
            PercentageParser.parse(parts[2].trim())?.let { raw ->
                val a = parseImageArg(parts[0].trim()) ?: return null
                val b = parseImageArg(parts[1].trim()) ?: return null
                // Clamp FIRST (§2.6.2 / -webkit- behavior) so the pair
                // below always sums to exactly 100 with both in range.
                val pct = clamp(raw)
                // Legacy weights are FULLY determined: second image gets
                // p, first gets 100−p (css-images-4 §2.6.2 legacy note).
                return BackgroundImageProperty.BackgroundImage.CrossFade(listOf(
                    BackgroundImageProperty.CrossFadeArg(IRPercentage(100.0 - pct.value), a),
                    BackgroundImageProperty.CrossFadeArg(pct, b)
                ), legacy = true)
            }
        }

        // Modern n-ary form: per-arg optional percentage before OR after
        // the image (the `&&` combinator allows both orders).
        val args = parts.map { part ->
            val tokens = TokenizationUtils.tokenizeByWhitespace(part.trim())
            if (tokens.isEmpty()) return null
            var weight: IRPercentage? = null
            var imageTokens = tokens
            // Leading percentage (`10% linear-gradient(...)` — the WPT
            // form). Clamped to [0,100] per §2.6.2 (see clamp docs above).
            PercentageParser.parse(tokens.first())?.let {
                weight = clamp(it)
                imageTokens = tokens.drop(1)
            } ?: if (tokens.size > 1) {
                // Trailing percentage (`url(x.png) 25%`), same clamping.
                PercentageParser.parse(tokens.last())?.let {
                    weight = clamp(it)
                    imageTokens = tokens.dropLast(1)
                }
            } else Unit
            // Remaining tokens re-join into the image/color value.
            if (imageTokens.isEmpty()) return null
            val image = parseImageArg(imageTokens.joinToString(" ")) ?: return null
            BackgroundImageProperty.CrossFadeArg(weight, image)
        }
        return BackgroundImageProperty.BackgroundImage.CrossFade(args, legacy = false)
    }

    // One cross-fade image argument: any <image> the background grammar
    // recognises, or a bare <color> (valid ONLY inside cross-fade — §2.6.2).
    private fun parseImageArg(value: String): BackgroundImageProperty.BackgroundImage? {
        // Try the full image grammar first (url()/gradients/nested
        // cross-fade — the grammar is recursive by definition).
        BackgroundImagePropertyParser.parseImage(value)?.let { return it }
        // Then <color> — parse from the lowered copy (color functions and
        // keywords are case-insensitive; no case-sensitive payloads here).
        ColorParser.parse(value.lowercase())?.let {
            return BackgroundImageProperty.BackgroundImage.ColorLayer(it)
        }
        return null
    }
}
