package app.parsing.css.properties.primitiveParsers

/**
 * Declaration-level resolution of the `attr()` notation (css-values-5 §8.7,
 * "Attribute References: the attr() notation").
 *
 *     attr() = attr( <attr-name> <attr-type>? , <declaration-value>? )
 *     <attr-name> = [ <ident-token>? '|' ]? <ident-token>
 *
 * `attr()` is an arbitrary-substitution function: its value depends on a DOM
 * attribute the converter cannot see, so the converter normally leaves it
 * alone and lets the extractor's attr bake (which DOES have the DOM) or the
 * web runtime resolve it. This resolver handles only the sub-cases whose
 * outcome is decidable **without** the DOM, and reports "leave it alone" for
 * everything else:
 *
 *  1. **Malformed `<attr-name>`** — e.g. `attr(*|bar type(*))`. `*` is not an
 *     `<ident-token>` (css-syntax-3 §4.3.4 "Consume an ident-like token" /
 *     §4.3.12 "Consume an ident sequence"); the universal-namespace wildcard
 *     exists in Selectors, not in the `<attr-name>` production. The function
 *     therefore does not match `attr()`, so the declaration matches no
 *     grammar and is invalid → css-syntax-3 §2.2 ("Error Handling") ignores
 *     it. (WPT css-values/attr-namespace-wildcard states this in its own
 *     `<meta name=assert>`: "Wildcard not supported in attr() function".)
 *
 *  2. **A namespace-PREFIXED `<attr-name>`** — e.g. `attr(foo|bar type(*),
 *     green)` — resolves to the `attr()` FAILURE path, so §8.7.1's fallback
 *     rules apply. See WHY THAT IS SOUND below; it is the one rule in this
 *     file that is not pure grammar, and it is stated exactly.
 *
 * Everything else — an un-prefixed `attr(bar)`, an explicitly null-namespaced
 * `attr(|bar)`, an `attr()` embedded in a larger value, an escaped ident we
 * decline to decode — returns [Outcome.Unresolvable] and is passed through
 * byte-for-byte, exactly as before this file existed. That is the "no silent
 * fallthrough" contract: we act only where the answer is certain.
 *
 * WHY THE PREFIXED CASE TAKES THE FAILURE PATH (wave-49, skeptic S4)
 * ------------------------------------------------------------------
 * The first cut justified the substitution with "a prefix can never be
 * declared in this pipeline". That claim is UNPROVABLE from here: whether
 * `foo` is bound is a fact about the author's stylesheet (css-namespaces-3
 * §2, "Declaring namespaces: the @namespace rule") and `CssComponent` — a
 * flat bag of declarations — does not carry it. WPT
 * css-values/attr-namespace-valid.xhtml is a real document that DOES declare
 * `@namespace color "http://www.example.com/"`.
 *
 * What IS provable is weaker and sufficient: **no consumer of this IR can
 * ever resolve a prefixed `attr()`.** The wire carries no `@namespace` table
 * and no arbitrary attribute values (`CssComponent.attrs` is a fixed
 * widget-identity whitelist), and the extractor's attr bake — the one stage
 * that does hold the DOM — bails on ANY `|` before consulting a namespace
 * table (tools/titan/attr-bake.mjs, "ANY '|' bails"). So §8.7.1's lookup
 * step ("If attr name exists as an attribute on el … otherwise jump to
 * FAILURE") can only ever reach FAILURE downstream of us; emitting the
 * FAILURE result here is that same answer, computed earlier.
 *
 * WHERE THAT IS STILL THE WRONG PIXEL, STATED PLAINLY: a document that
 * declares the prefix AND carries the attribute (exactly
 * attr-namespace-valid.xhtml) should paint the ATTRIBUTE's value; we paint
 * the fallback. Abstaining does not fix that — the attribute is absent from
 * the wire either way, so a Raw passthrough reaches runtimes that also
 * cannot resolve it — and abstaining WOULD lose attr-namespace-non-existing,
 * whose fallback is the reference's own green. The real fix is upstream, in
 * the extractor's attr bake. `AttrNotationResolverTest` pins
 * attr-namespace-valid.xhtml's declaration as a KNOWN-WRONG outcome so
 * admitting that test cannot regress silently.
 *
 * NOT VALIDATED HERE: the optional `<attr-type>` beyond its PRESENCE, which
 * §8.7.1's FAILURE step needs. A malformed type would make the declaration
 * invalid too, but mis-reading one could only ever turn a drop into a
 * substitution, so refusing to model it is the conservative choice.
 * TODO(css-values-5 §8.7): fold the `<attr-type>` grammar in when a corpus
 * test needs it.
 */
object AttrNotationResolver {

    /** What the caller ([app.parsing.css.properties.PropertiesParser]) should do. */
    sealed interface Outcome {
        /** The value is not a whole-declaration `attr()` — leave it untouched. */
        data object NotAttr : Outcome

        /** A real `attr()`, but its result is a DOM fact — leave it untouched. */
        data object Unresolvable : Outcome

        /**
         * The `attr()` grammar is violated: the declaration matches no
         * grammar, so css-syntax-3 §2.2 ignores it — drop it and let whatever
         * the cascade declared before it stand.
         */
        data class Invalid(val reason: String) : Outcome

        /** Replace the declaration's value with these bytes. */
        data class Substituted(val value: String, val reason: String) : Outcome

        /**
         * §8.7.1's FAILURE path produced the **guaranteed-invalid value**, or
         * an empty substitution: the declaration is *invalid at computed-value
         * time*, which css-values-5 Appendix A ("Arbitrary Substitution
         * Functions" → "Invalid Substitution") defines as computing "as if the
         * property's value had been specified as the unset keyword"
         * (css-cascade-5 §7.3.3, "Erasing All Declarations: the unset
         * keyword").
         *
         * DISTINCT FROM [Invalid] on purpose. A parse-time drop leaves an
         * EARLIER declaration in force; IACVT does not — it erases the
         * property back to inherited-or-initial. The two only coincide in
         * this pipeline because [app.parsing.css.properties.PropertiesParser]
         * receives a `Map` keyed by property name, so there is no earlier
         * declaration to fall back to and "absent" already means
         * inherited-or-initial. Keeping the outcomes separate means the audit
         * log states the real reason, and a future cascade-aware caller gets
         * the distinction for free instead of inheriting a silent conflation.
         */
        data class InvalidAtComputedValueTime(val reason: String) : Outcome
    }

    /**
     * An `<ident-token>` in its common, escape-free form. Two alternatives,
     * both taken straight from css-syntax-3 §4.3.9 ("Check if three code
     * points would start an ident sequence"), which admits a leading U+002D
     * when the code point after it is either an ident-start code point *or a
     * second U+002D*:
     *
     *  - `--` + any run of ident code points — the custom-property shape.
     *    `attr(ns|--x, green)` is well-formed CSS and must NOT be dropped
     *    (wave-49, skeptic S4: it was, because the old pattern consumed the
     *    first `-` with `-?` and then demanded a letter).
     *  - an optional single `-`, then an ident-start code point (letter, `_`,
     *    non-ASCII), then any run of ident code points (§4.3.12 "Consume an
     *    ident sequence").
     *
     * A lone `-` matches neither, correctly: §4.3.9 needs a second code point
     * after the hyphen, so `-` on its own is a `<delim-token>`.
     *
     * Escapes (`\2d`) are still deliberately NOT matched — see [identVerdict],
     * which routes them to "uncertain" instead of "invalid" so this resolver
     * can never drop a declaration it merely failed to lex.
     */
    private val SIMPLE_IDENT =
        """^(?:--|-?[A-Za-z_\u0080-\uFFFF])[A-Za-z0-9_\-\u0080-\uFFFF]*$""".toRegex()

    /** Three-valued ident test — YES / NO / "I decline to say". */
    private enum class Ident { YES, NO, UNCERTAIN }

    private fun identVerdict(token: String): Ident = when {
        // An escape sequence means the real ident differs from these bytes.
        // Decoding CSS escapes is out of scope (css-syntax-3 §4.3.7 "Consume
        // an escaped code point"), and guessing here could drop a valid
        // declaration, so we abstain (caller → Unresolvable).
        token.contains('\\') -> Ident.UNCERTAIN
        SIMPLE_IDENT.matches(token) -> Ident.YES
        else -> Ident.NO
    }

    /**
     * Resolve one declaration VALUE.
     *
     * Scope guard: only a value that is *entirely* one `attr()` call is
     * considered. An `attr()` riding inside a larger value (`attr(x) no-repeat`)
     * would need the substitution spliced back into a token stream we do not
     * model, and no corpus test needs it — such values return [Outcome.NotAttr]
     * and keep their existing behaviour.
     */
    fun resolve(value: String): Outcome {
        val t = value.trim()
        // Function names are ASCII case-insensitive (CSS 2.2 §4.1.3,
        // "Characters and case"), so match the head on a lowered copy; the
        // payload keeps author bytes.
        if (!t.startsWith("attr(", ignoreCase = true)) return Outcome.NotAttr
        // The matching close paren must be the LAST character, otherwise this
        // is `attr(...) something-else` — outside the scope guard above.
        val close = TokenizationUtils.findMatchingCloseParen(t, "attr".length)
        if (close != t.length - 1) return Outcome.NotAttr
        val inner = t.substring("attr(".length, close)

        // css-values-5 §8.7 splits the arguments at the FIRST top-level comma:
        // everything before it is `<attr-name> <attr-type>?`, everything after
        // is the fallback `<declaration-value>` — which may itself contain
        // commas (`attr(x type(*), 1px, 2px)`), so we must NOT use a general
        // comma splitter here.
        val commaIdx = firstTopLevelComma(inner)
        val head = (if (commaIdx < 0) inner else inner.substring(0, commaIdx)).trim()
        // null = no comma at all ("second arg is null" in §8.7.1's algorithm);
        // "" = a BARE comma, which §8.7 explicitly calls passing a fallback:
        // "attr(foo) does not pass a fallback value, but attr(foo,) does (the
        // fallback is just empty)". The two take different FAILURE branches.
        val fallback = if (commaIdx < 0) null else inner.substring(commaIdx + 1).trim()

        // `<attr-name> <attr-type>?` — "Whitespace is not allowed between any
        // of the components of <attr-name>" (§8.7), so the FIRST token is the
        // whole attr-name and anything after it is the `<attr-type>`.
        // `tokenizeByWhitespace` is paren-aware, so a `type(*)` stays one
        // separate token instead of splitting on the `(`.
        val headTokens = TokenizationUtils.tokenizeByWhitespace(head)
        val attrName = headTokens.firstOrNull()
            ?: return Outcome.Invalid("attr() with an empty <attr-name>")
        // Presence only — §8.7.1's FAILURE step branches on whether a syntax
        // was given, not on which one.
        val hasAttrType = headTokens.size > 1

        val pipe = attrName.indexOf('|')
        // No `|` at all → the null/default namespace. Whether that attribute
        // exists is a DOM fact; abstain.
        if (pipe < 0) return Outcome.Unresolvable

        val prefix = attrName.substring(0, pipe)
        val local = attrName.substring(pipe + 1)

        // The local name must be an ident in every form of the production.
        when (identVerdict(local)) {
            Ident.NO -> return Outcome.Invalid("attr() local name '$local' is not an <ident-token>")
            Ident.UNCERTAIN -> return Outcome.Unresolvable
            Ident.YES -> Unit
        }

        // `|bar` — the production's `<ident-token>?` is empty, which explicitly
        // selects the NULL namespace (WPT css-values/attr-null-namespace pins
        // that semantic). Same DOM dependency as the unprefixed form; abstain.
        if (prefix.isEmpty()) return Outcome.Unresolvable

        when (identVerdict(prefix)) {
            // `*|bar` lands here: the wildcard is a Selectors production, not
            // an <attr-name> one, so the whole declaration is invalid
            // (css-syntax-3 §2.2).
            Ident.NO -> return Outcome.Invalid("attr() namespace prefix '$prefix' is not an <ident-token>")
            Ident.UNCERTAIN -> return Outcome.Unresolvable
            Ident.YES -> Unit
        }

        // A well-formed prefix whose attribute nothing downstream can find
        // (see the file banner). css-values-5 §8.7.1's FAILURE step, in its
        // own order:
        //   1. second arg null AND syntax omitted → an empty CSS <string>;
        //   2. second arg null                    → the guaranteed-invalid value;
        //   3. otherwise                          → the second arg.
        val why = "namespace prefix '$prefix' is unresolvable in this pipeline"
        return when {
            // Step 3, the ordinary case: a real fallback is substituted whole.
            !fallback.isNullOrEmpty() ->
                Outcome.Substituted(fallback, "$why — css-values-5 §8.7.1 fallback")

            // Step 3 with a BARE comma: the fallback is the empty token
            // sequence. Substituting nothing leaves the declaration with no
            // value, which can match no property's grammar — and because
            // §8.7 syntax-checks only AFTER substitution ("It is only
            // syntax-checked at computed-value time"), that is IACVT, not a
            // parse-time drop.
            fallback != null -> Outcome.InvalidAtComputedValueTime(
                "$why and the fallback is empty — nothing substituted (css-values-5 §8.7.1)"
            )

            // Step 1: no comma and no <attr-type> → an empty CSS <string>.
            // Emitted literally, because for the properties that do take a
            // <string> (`content`, `quotes`) `""` is the right VALUE; for
            // every other property it will fail its grammar downstream, which
            // is the same channel any other unmodellable value takes.
            !hasAttrType -> Outcome.Substituted(
                "\"\"",
                "$why and no <attr-type> — fallback defaults to the empty string (css-values-5 §8.7.1)"
            )

            // Step 2: a syntax WAS given, so the omitted fallback defaults to
            // the guaranteed-invalid value (css-variables-1 §2.2,
            // "Guaranteed-Invalid Values").
            else -> Outcome.InvalidAtComputedValueTime(
                "$why and no fallback — guaranteed-invalid value (css-values-5 §8.7.1)"
            )
        }
    }

    /**
     * Index of the first comma at nesting depth 0, or -1. Quotes are tracked
     * because a `<string>` may legally contain a comma (css-syntax-3 §4.3.5,
     * "Consume a string token") and a fallback such as `attr(x, "a,b")` must
     * stay whole.
     */
    private fun firstTopLevelComma(s: String): Int {
        var depth = 0
        var quote: Char? = null
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                // Inside a string: only the matching quote ends it; a
                // backslash escapes the next character (css-syntax-3 §4.3.7).
                quote != null -> {
                    if (c == '\\') i++
                    else if (c == quote) quote = null
                }
                c == '"' || c == '\'' -> quote = c
                c == '(' -> depth++
                c == ')' -> depth--
                c == ',' && depth == 0 -> return i
            }
            i++
        }
        return -1
    }
}
