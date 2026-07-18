package com.styleconverter.runtime.typography

// Pin for the Android first-line glyph-drift fix (applier-campaign wave 6,
// ANDROID-FIRSTLINE lane). Compose's default TextMotion.Static runs glyphs
// through the hinting pass, which quantizes each advance; the per-glyph
// rounding error accumulates ALONG a line and resets at the next line, so
// long first lines drifted off the web reference by up to ±3.5px at line
// end (measured +0.09px/glyph at 20px, −0.13px/glyph at 22px — direction
// flips with font size) while iOS tracked web within ±0.5px everywhere.
// Undoing the drift in the wave-6 captures lifted Android-web SSIM
// 0.9364→0.9833 (InitialLetter_Normal 20px) and 0.8884→0.9657
// (FontWeight_Normal 22px). TextMotion.Animated switches Android to the
// linear (unhinted) metrics + subpixel positioning model Chrome and
// CoreText use, so every TextStyle the runtime builds must carry it.

import androidx.compose.ui.text.style.TextMotion
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TextStyleApplierTextMotionTest {

    @Test
    fun `bare style carries linear-advance text motion`() {
        // No IR properties at all — the flag is part of the canonical
        // base recipe (like includeFontPadding=false), not property-gated.
        assertEquals(
            TextMotion.Animated,
            TextStyleApplier.extractTextStyle(emptyList()).textMotion
        )
    }

    @Test
    fun `typography properties do not displace the text motion`() {
        // A representative cluster fixture shape (InitialLetter_Normal:
        // font-size 20px + color) — the copy() that applies parsed
        // properties must keep the motion flag alongside them.
        val style = TextStyleApplier.extractTextStyle(
            listOf(
                IRProperty(type = "FontSize", data = JsonPrimitive(20.0)),
                IRProperty(type = "TextAlign", data = JsonPrimitive("CENTER")),
            )
        )
        assertEquals(TextMotion.Animated, style.textMotion)
        // Guard the sibling line-box flags too: losing platformStyle /
        // lineHeightStyle in the same copy() would silently regress the
        // CSS line-height alignment this recipe exists for.
        assertNotNull(style.platformStyle)
        assertNotNull(style.lineHeightStyle)
    }
}
