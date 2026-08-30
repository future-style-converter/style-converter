package app.parsing.css.properties

import app.irmodels.IRProperty

/**
 * Sentinel a longhand parser returns when the declaration it was handed is
 * **invalid CSS**, as opposed to "valid CSS this converter does not model yet".
 *
 * WHY THE DISTINCTION IS THE WHOLE POINT
 * --------------------------------------
 * `PropertyParser.parse` has exactly one failure channel today — `null` — and
 * [PropertiesParser] turns that into a [GenericProperty] passthrough
 * (`_unmapped: true` on the wire). That is the RIGHT answer for a value the
 * converter simply has no grammar for yet (`justify-self: safe anchor-center`,
 * `hyphenate-limit-chars: auto 2 2`): the bytes survive so a runtime that DOES
 * understand them can still act, and nothing is silently eaten.
 *
 * It is the WRONG answer for a value that fails a grammar the converter fully
 * implements. css-syntax-3 §2.2 ("Error Handling") is unambiguous there —
 * "After each construct (declaration, style rule, at-rule) is parsed, the user
 * agent checks it against its expected grammar. If it does not match the
 * grammar, it's invalid, and gets ignored by the UA" — as is CSS 2.2 §4.2
 * ("Rules for handling parsing errors"): a declaration whose value does not
 * match the property's grammar is **invalid and must be dropped**, leaving
 * whatever the cascade declared before it in force. Keeping such a value alive as a passthrough
 * asserts "we could not read this" when the truth is "this is not CSS" — and
 * the passthrough then shadows the valid declaration underneath it.
 *
 * A parser that can PROVE a value is invalid returns this object instead of
 * `null`; [PropertiesParser] drops the declaration and logs it. Parsers that
 * cannot tell the two apart keep returning `null` and keep their existing
 * GenericProperty behaviour — the mechanism is strictly opt-in, so no parser
 * that has not reasoned about the distinction can accidentally start dropping.
 *
 * NEVER REACHES THE WIRE: [PropertiesParser] is the only consumer and it
 * filters the sentinel out before the result list is built. The object is
 * deliberately NOT `@Serializable` so a future leak fails loudly rather than
 * emitting a phantom property (`InvalidDeclarationDropTest` pins the absence).
 */
object InvalidDeclaration : IRProperty {
    /**
     * Not a real CSS property name — the angle brackets make it impossible to
     * collide with any kebab-case longhand, so an accidental leak is obvious
     * in a log line or a wire diff instead of masquerading as a property.
     */
    override val propertyName: String = "<invalid-declaration>"
}
