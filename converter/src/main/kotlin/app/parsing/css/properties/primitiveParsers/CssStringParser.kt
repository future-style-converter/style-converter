package app.parsing.css.properties.primitiveParsers

/**
 * CssStringParser.kt — wave 54 (lane L3, unit U1).
 *
 * Decodes ONE CSS `<string>` token the way css-syntax-3 consumes it:
 *   - §4.3.5 "consume a string token": the token runs from the opening quote
 *     to the matching ending quote; a raw newline makes a <bad-string-token>;
 *     a backslash before a newline is a line continuation (both dropped);
 *     a backslash at EOF is dropped;
 *   - §4.3.7 "consume an escaped code point": 1–6 hex digits plus ONE
 *     optional whitespace, where 0, a surrogate or a value above U+10FFFF
 *     becomes U+FFFD; a backslash before any other code point yields that
 *     code point itself.
 *
 * WHY IT EXISTS. `hyphenate-character: "\2022"` (WPT css-text/hyphens/
 * hyphenate-character-003) reached the wire as the literal six characters
 * `\2022`: HyphenateCharacterPropertyParser stripped the quotes and decoded
 * nothing, so the web runtime faithfully painted `im\2022`. And `""`
 * (-001/-002) was rejected outright. The only other unescaper in the reader,
 * `ContentPropertyParser.unescapeString`, handles `\" \' \\ \n \t \r` only
 * (and its `\n` → LF is not CSS), so it cannot serve here.
 *
 * Input preprocessing follows css-syntax-3 §3.3 for the two facts a single
 * string token can observe: CR LF / CR / FF count as one newline.
 */
object CssStringParser {

    /** U+FFFD REPLACEMENT CHARACTER — css-syntax-3 §4.3.7's substitute. */
    private const val REPLACEMENT = 0xFFFD

    /** The largest code point Unicode defines — css-syntax-3 §4.2. */
    private const val MAX_CODE_POINT = 0x10FFFF

    /**
     * The decoded value of [input] when, after trimming the surrounding
     * whitespace, it is EXACTLY one `<string-token>`; null otherwise (no
     * opening quote, a <bad-string-token>, or anything left after the
     * ending quote). An unterminated string at EOF is still a string token
     * (§4.3.5: "parse error. Return the <string-token>").
     */
    fun parse(input: String): String? {
        // Leading/trailing whitespace is between tokens, not inside one.
        val s = input.trim()
        // An empty value carries no token at all.
        if (s.isEmpty()) return null
        // §4.3.1: only U+0022 / U+0027 open a string token.
        val quote = s[0]
        if (quote != '"' && quote != '\'') return null
        // Decoded code points accumulate here.
        val out = StringBuilder()
        // Cursor just past the opening quote (UTF-16 index).
        var i = 1
        // Walk code points until the ending quote, EOF or a bad string.
        while (i < s.length) {
            // Read by CODE POINT so astral characters stay whole.
            val c = s.codePointAt(i)
            // Advance past this code point (1 or 2 UTF-16 units).
            i += Character.charCount(c)
            when {
                // The matching quote ends the token: it must also end the input.
                c == quote.code -> return if (i == s.length) out.toString() else null
                // A raw newline inside a string makes a <bad-string-token>.
                isNewline(c) -> return null
                // A backslash starts an escape or a line continuation.
                c == '\\'.code -> {
                    // §4.3.5: "If the next input code point is EOF, do nothing."
                    if (i >= s.length) continue
                    // Look at the code point after the backslash.
                    val next = s.codePointAt(i)
                    if (isNewline(next)) {
                        // Line continuation: consume the newline (CR LF as one).
                        i += newlineLength(s, i)
                    } else {
                        // A valid escape: decode it and keep the cursor.
                        i = consumeEscape(s, i, out)
                    }
                }
                // Any other code point is part of the value verbatim.
                else -> out.appendCodePoint(c)
            }
        }
        // EOF before the ending quote: still a string token (parse error only).
        return out.toString()
    }

    /**
     * css-syntax-3 §4.3.7, starting at [start] (just past the backslash).
     * Appends the decoded code point to [out] and returns the new cursor.
     */
    private fun consumeEscape(s: String, start: Int, out: StringBuilder): Int {
        // Cursor over the escape body.
        var i = start
        // A hex escape is 1-6 hex digits.
        if (isHexDigit(s[i])) {
            // Accumulate the hex value; Long so six F's never overflow.
            var value = 0L
            // Count digits to stop at six.
            var digits = 0
            while (i < s.length && digits < 6 && isHexDigit(s[i])) {
                // Shift in one hex digit.
                value = value * 16 + Character.digit(s[i], 16)
                // One more digit consumed.
                i++
                digits++
            }
            // "If the next input code point is whitespace, consume it as well."
            if (i < s.length && isWhitespace(s[i])) i += whitespaceLength(s, i)
            // Zero, surrogates and out-of-range values become U+FFFD.
            val cp = if (value == 0L || value in 0xD800L..0xDFFFL || value > MAX_CODE_POINT) REPLACEMENT else value.toInt()
            // Append as a code point (astral values become a surrogate pair).
            out.appendCodePoint(cp)
            // The cursor now sits after the digits (and the eaten whitespace).
            return i
        }
        // "Anything else: return the current input code point" — e.g. `\"`.
        val cp = s.codePointAt(i)
        // Append it verbatim.
        out.appendCodePoint(cp)
        // Step past it (1 or 2 UTF-16 units).
        return i + Character.charCount(cp)
    }

    /** css-syntax-3 §4.2 hex digit: 0-9, A-F, a-f (ASCII only). */
    private fun isHexDigit(c: Char): Boolean = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

    /** §4.2 newline after §3.3 preprocessing: LF, CR (alone or in CR LF), FF. */
    private fun isNewline(c: Int): Boolean = c == '\n'.code || c == '\r'.code || c == 0x0C

    /** §4.2 whitespace: a newline, CHARACTER TABULATION or SPACE. */
    private fun isWhitespace(c: Char): Boolean = isNewline(c.code) || c == '\t' || c == ' '

    /** UTF-16 length of the newline at [i]: CR LF is ONE newline (§3.3). */
    private fun newlineLength(s: String, i: Int): Int =
        // CR immediately followed by LF collapses to one newline of length 2.
        if (s[i] == '\r' && i + 1 < s.length && s[i + 1] == '\n') 2 else 1

    /** UTF-16 length of the single whitespace at [i] (CR LF counts as one). */
    private fun whitespaceLength(s: String, i: Int): Int =
        // A newline may be the two-unit CR LF; tab and space are one unit.
        if (isNewline(s[i].code)) newlineLength(s, i) else 1
}
