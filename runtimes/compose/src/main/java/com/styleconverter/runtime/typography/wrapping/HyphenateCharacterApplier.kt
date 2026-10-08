package com.styleconverter.runtime.typography.wrapping

import com.styleconverter.runtime.PropertyTracker
import java.util.concurrent.ConcurrentHashMap

/**
 * HyphenateCharacterApplier.kt
 * typography/wrapping — wave 54 (lane L3, unit U2-android).
 *
 * `HyphenateCharacterConfig → the string the pre-break paints`.
 *
 * WHERE IT LANDS ON COMPOSE. Compose `Text` has no hyphenate-character API:
 * `TextStyle.hyphens` only toggles Minikin's dictionary, whose hyphen glyph is
 * the platform's own. The one place this runtime PAINTS a hyphen of its own is
 * the css-text-3 §5.3 fold inside [PreBreakPipeline] (via
 * [GreedyLineBreaker.lines] → [WordBreakOpportunities.split]): a taken U+00AD
 * becomes a real character at the line end, measured and rendered like any
 * other glyph. So the string must enter the FOLD, not be substituted after it —
 * its width decides the breaks (`real` fits 4.5ch only when the string is `""`;
 * `tial/-/` overflows 6.5ch). This applier hands that string to
 * `PreBreakPipeline.preBreak(hyphenChar = …)` at the ComponentRenderer call
 * site (wave-54 seam-1).
 *
 * THE WALL, logged. A `hyphens: auto` run with a language tag is Minikin's
 * ([AutoHyphenation]): the pipeline does not fire for its taken opportunities,
 * and Minikin paints its own hyphen there whatever the author asked for
 * (WPT css-text/hyphens/hyphenate-character-002 on Android). That run gets one
 * PropertyTracker breadcrumb instead of a silent degradation.
 */
object HyphenateCharacterApplier {

    /** Breadcrumb keys already reported — one log line per distinct case. */
    private val logged: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * The string a taken hyphenation opportunity paints for this run.
     *
     * @param config the element's own declaration, or null (none declared).
     * @param dictionaryHyphenation [AutoHyphenation.engaged] for this run.
     * @return the author's string verbatim, or the UA hyphen for `auto` and
     *   for no declaration — the latter is the DEFAULT argument value of
     *   `preBreak`, so every run that declares nothing is byte-identical.
     */
    @JvmStatic
    fun preBreakString(config: HyphenateCharacterConfig?, dictionaryHyphenation: Boolean = false): String {
        // `auto` / undeclared → the UA hyphen (U+2010, what Chromium paints).
        val value = config?.value ?: return WordBreakOpportunities.DEFAULT_HYPHEN_CHARACTER
        // Minikin's dictionary paints its own hyphen: say so, once.
        if (dictionaryHyphenation) {
            logOnce(
                "dictionary",
                "hyphenate-character: \"$value\" on a hyphens:auto run — Minikin " +
                    "paints its own hyphen at dictionary breaks (no Compose API)"
            )
        }
        // The author's string, verbatim ("" included).
        return value
    }

    /**
     * Report [message] under [key] at most once per process. `markUnhandled`
     * is pure Kotlin; the Logcat write inside `logUnhandled` is wrapped
     * because `android.util.Log` is a stub on the JVM unit-test classpath
     * (the same guard GreedyLineBreaker.warnUnmodelledOnce uses).
     */
    internal fun logOnce(key: String, message: String) {
        // First sighting only.
        if (!logged.add(key)) return
        // The PropertyTracker breadcrumb (no silent fallthrough).
        runCatching { PropertyTracker.logUnhandled("HyphenateCharacter", message) }
    }
}
