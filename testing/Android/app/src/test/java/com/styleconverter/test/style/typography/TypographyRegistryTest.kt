package com.styleconverter.test.style.typography

// Phase 6 tripwire: every IR property file under
// src/main/kotlin/app/irmodels/properties/typography/ must be claimed in
// PropertyRegistry by one of the typography-family extractors. If a new
// typography property lands in the IR tree without a matching registration
// this test goes red immediately — preventing the legacy PropertyApplier
// switch-case dispatch from silently reclaiming coverage.

import com.styleconverter.test.style.PropertyRegistry
import com.styleconverter.test.style.typography.advanced.BaselineExtractor
import com.styleconverter.test.style.typography.ruby.RubyExtractor
import com.styleconverter.test.style.typography.text.TextExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TypographyRegistryTest {

    // Force every typography extractor's companion `init {}` block to run.
    // Kotlin `object`s are lazy — without at least one reference, the init
    // block never fires in a test JVM and the registry stays empty.
    @Before
    fun primeExtractors() {
        TypographyExtractor.hashCode()
        TextFormattingExtractor.hashCode()
        BaselineExtractor.hashCode()
        RubyExtractor.hashCode()
        TextExtractor.hashCode()
    }

    // The canonical list of typography IR property names. Each name is the
    // camelcase IRProperty.type string emitted by the CSS parser (one entry
    // per file in src/main/kotlin/app/irmodels/properties/typography/).
    // Keep alphabetized so diffs are readable when the IR grows.
    private val typographyProperties: List<String> = listOf(
        "AlignmentBaseline",
        "BaselineShift",
        "BaselineSource",
        "BlockEllipsis",
        "CaretColor",
        "Direction",
        "DominantBaseline",
        "DominantBaselineAdjust",
        "FontDisplay",
        "FontFamily",
        "FontFeatureSettings",
        "FontKerning",
        "FontLanguageOverride",
        "FontMaxSize",
        "FontMinSize",
        "FontNamedInstance",
        "FontOpticalSizing",
        "FontPalette",
        "FontSize",
        "FontSizeAdjust",
        "FontSmooth",
        "FontStretch",
        "FontStyle",
        "FontSynthesisPosition",
        "FontSynthesisSmallCaps",
        "FontSynthesisStyle",
        "FontSynthesisWeight",
        "FontVariantAlternates",
        "FontVariantCaps",
        "FontVariantEastAsian",
        "FontVariantEmoji",
        "FontVariantLigatures",
        "FontVariantNumeric",
        "FontVariantPosition",
        "FontVariationSettings",
        "FontWeight",
        "GlyphOrientationHorizontal",
        "GlyphOrientationVertical",
        "HangingPunctuation",
        "HyphenateCharacter",
        "HyphenateLimitChars",
        "HyphenateLimitLast",
        "HyphenateLimitLines",
        "HyphenateLimitZone",
        "Hyphens",
        "InitialLetter",
        "InitialLetterAlign",
        "Kerning",
        "LetterSpacing",
        "LineBreak",
        "LineClamp",
        "LineGrid",
        "LineHeight",
        "LineHeightStep",
        "LineSnap",
        "MaxLines",
        "Orphans",
        "OverflowWrap",
        "RubyAlign",
        "RubyMerge",
        "RubyOverhang",
        "RubyPosition",
        "TabSize",
        "TextAlign",
        "TextAlignAll",
        "TextAlignLast",
        "TextAnchor",
        "TextAutospace",
        "TextBoxEdge",
        "TextBoxTrim",
        "TextCombineUpright",
        "TextDecorationColor",
        "TextDecorationLine",
        "TextDecorationSkip",
        "TextDecorationSkipInk",
        "TextDecorationStyle",
        "TextDecorationThickness",
        "TextEmphasis",
        "TextEmphasisColor",
        "TextEmphasisPosition",
        "TextEmphasisStyle",
        "TextGroupAlign",
        "TextIndent",
        "TextJustify",
        "TextOrientation",
        "TextOverflow",
        "TextRendering",
        "TextShadow",
        "TextSizeAdjust",
        "TextSpaceCollapse",
        "TextSpaceTrim",
        "TextSpacing",
        "TextSpacingTrim",
        "TextTransform",
        "TextUnderlineOffset",
        "TextUnderlinePosition",
        "TextWrap",
        "TextWrapMode",
        "TextWrapStyle",
        "UnicodeBidi",
        "VerticalAlign",
        "VerticalAlignLast",
        "WhiteSpace",
        "WhiteSpaceCollapse",
        "Widows",
        "WordBreak",
        "WordSpaceTransform",
        "WordSpacing",
        "WordWrap",
        "WritingMode"
    )

    @Test
    fun `typography property list is the canonical 110`() {
        // Guard against typos in the list above — if the count drifts the
        // assertion message points straight at this file, not a failing
        // registry lookup ten layers down.
        assertEquals(
            "Expected 110 typography IR property names (one per file under " +
                "src/main/kotlin/app/irmodels/properties/typography/).",
            110,
            typographyProperties.size
        )
    }

    @Test
    fun `every typography IR property is registered`() {
        // Collect misses first so a single test run reports the full gap.
        val missing = typographyProperties.filter { !PropertyRegistry.isMigrated(it) }
        assertTrue(
            "Typography properties not registered with PropertyRegistry:\n" +
                missing.joinToString("\n") { "  - $it" },
            missing.isEmpty()
        )
    }

    @Test
    fun `every typography registration has a typography-family owner`() {
        // Owner must be one of the five typography sub-folders. This catches
        // accidental owner typos like "typograhpy" that the boolean
        // isMigrated() check would silently accept.
        val validOwners = setOf(
            "typography",
            "typography/advanced",
            "typography/ruby",
            "typography/text"
        )
        val badOwners = typographyProperties
            .mapNotNull { name -> PropertyRegistry.ownerOf(name)?.let { name to it } }
            .filter { (_, owner) -> owner !in validOwners }
        assertTrue(
            "Typography properties with non-typography owners:\n" +
                badOwners.joinToString("\n") { (n, o) -> "  - $n -> $o" },
            badOwners.isEmpty()
        )
    }
}
