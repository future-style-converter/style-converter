package com.styleconverter.runtime.typography.wrapping

/**
 * HyphenateCharacterConfig.kt
 * typography/wrapping — wave 54 (lane L3, unit U2-android).
 *
 * The typed value of css-text-4 §6.3 `hyphenate-character: auto | <string>`
 * — the string a taken hyphenation opportunity paints at the end of its line
 * (css-text-3 §5.3: "the UA must insert a hyphen … or the appropriate
 * hyphenation character", which §6.3 lets the author choose).
 *
 * @property value the author's string VERBATIM — including `""` (WPT
 *   css-text/hyphens/hyphenate-character-001: "no visible hyphens appear")
 *   and multi-character strings (`"/-/"`, -004). Never lower-cased: the
 *   string is painted, not matched. null = `auto`, which the applier maps to
 *   the UA hyphen ([WordBreakOpportunities.DEFAULT_HYPHEN_CHARACTER], the
 *   U+2010 Chromium paints and the frozen refs carry).
 */
data class HyphenateCharacterConfig(val value: String?)
