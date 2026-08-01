package com.styleconverter.runtime.widgets

// Lane W2 (wave 20) — the Compose half of the CROSS-PLATFORM PIN TABLE:
// per-widget paint-plan pins (geometry + palette as language-neutral op
// strings). The Swift twin (UAWidgetsTests.swift) asserts the IDENTICAL
// expected strings — a change that moves one side but not the other
// fails one of the two suites, which is the whole drift defense.

import com.styleconverter.runtime.widgets.UAWidgetsGeometry.Kind
import com.styleconverter.runtime.widgets.UAWidgetsGeometry.Spec
import org.junit.Assert.assertEquals
import org.junit.Test

class UAWidgetsGeometryTest {

    /** Deterministic width probe: 7px per char — a fake Inter so plans
     *  with label-dependent widths stay platform-independent in tests. */
    private val measure: (String) -> Float = { s -> s.length * 7f }

    /** Pin helper: plan → op pin strings. */
    private fun pins(spec: Spec) = UAWidgetsGeometry.plan(spec, measure).map { it.pin }

    // ── checkbox ────────────────────────────────────────────────────────

    @Test
    fun uncheckedCheckbox_whiteBoxWithHairline() {
        // 13x13 white rrect + half-pixel-inset #767676 border (ref x158..170).
        assertEquals(
            listOf(
                "fillRRect 0 0 13 13 2 #FFFFFFFF",
                "strokeRRect 0.5 0.5 12 12 2 #FF767676",
            ),
            pins(Spec(Kind.CHECKBOX))
        )
    }

    @Test
    fun checkedCheckbox_accentFillPlusWhiteCheck() {
        // The accent-color-visited target: accent-filled box + white check
        // polyline — the ONLY ink of that whole test page.
        assertEquals(
            listOf(
                "fillRRect 0 0 13 13 2 #FF0075FF",
                "line 3.25 6.75 5.25 8.75 1.6 #FFFFFFFF",
                "line 5.25 8.75 9.75 4.25 1.6 #FFFFFFFF",
            ),
            pins(Spec(Kind.CHECKBOX, checked = true))
        )
    }

    @Test
    fun checkedCheckbox_customAccentDrivesTheFill() {
        // accent-color: red (accent-color-checkbox-checked-001) — the fill
        // op takes the resolved accent, the check stays white.
        assertEquals(
            "fillRRect 0 0 13 13 2 #FFFF0000",
            pins(Spec(Kind.CHECKBOX, checked = true, accent = 0xFFFF0000L)).first()
        )
    }

    // ── radio ───────────────────────────────────────────────────────────

    @Test
    fun radio_uncheckedRing_checkedDonut() {
        // Unchecked: white disc + hairline ring.
        assertEquals(
            listOf(
                "fillCircle 6.5 6.5 6.5 #FFFFFFFF",
                "strokeCircle 6.5 6.5 6 #FF767676",
            ),
            pins(Spec(Kind.RADIO))
        )
        // Checked: three concentric fills (accent / white gap / accent dot).
        assertEquals(
            listOf(
                "fillCircle 6.5 6.5 6.5 #FF0075FF",
                "fillCircle 6.5 6.5 4.75 #FFFFFFFF",
                "fillCircle 6.5 6.5 3 #FF0075FF",
            ),
            pins(Spec(Kind.RADIO, checked = true))
        )
    }

    // ── button / textfield / textarea ───────────────────────────────────

    @Test
    fun button_chromeSlabHuggingLabel() {
        // "button" (6 chars × 7px fake) + 2×8 chrome band = 58 wide, h21.
        // Wave 22 (A-RC2/A-RC4): the pad is 8 (border 1 + padding 7) and
        // the label anchors on the ref BASELINE 15, in the 'ua' face.
        assertEquals(
            listOf(
                "fillRRect 0 0 58 21 2 #FFEFEFEF",
                "strokeRRect 0.5 0.5 57 20 2 #FF767676",
                "label 'button' 8 15 13.33 ua #FF000000",
            ),
            pins(Spec(Kind.BUTTON, label = "button"))
        )
        // Intrinsic box matches the plan's slab.
        assertEquals(58f to 21f, UAWidgetsGeometry.intrinsicSize(Spec(Kind.BUTTON, label = "button"), measure))
    }

    @Test
    fun textfield_fixedFieldWithValueText() {
        // 153x21 white + hairline + left-aligned value (ref x87..239).
        assertEquals(
            listOf(
                "fillRect 0 0 153 21 #FFFFFFFF",
                "strokeRect 0.5 0.5 152 20 #FF767676",
                "label 'input-text' 4 15 13.33 ua #FF000000",
            ),
            pins(Spec(Kind.TEXTFIELD, label = "input-text"))
        )
    }

    @Test
    fun textarea_areaWithResizeGrip() {
        // 183x36 (the wave-22 ref-probed W3 atom box) white + border +
        // MONOSPACE content on baseline 15 + the re-fitted grip
        // diagonals (ref long arm 190.5,71.5 → 196.5,65.5).
        assertEquals(
            listOf(
                "fillRect 0 0 183 36 #FFFFFFFF",
                "strokeRect 0.5 0.5 182 35 #FF767676",
                "label 'textarea' 3 15 13.33 mono #FF000000",
                "line 174.5 33.5 180.5 27.5 1 #FF767676",
                "line 178.5 33.5 180.5 31.5 1 #FF767676",
            ),
            pins(Spec(Kind.TEXTAREA, label = "textarea"))
        )
    }

    // ── range / progress / meter ────────────────────────────────────────

    @Test
    fun range_defaultValueParksThumbMidTravel() {
        // A-RC3 — 129x21 atom box (W3 table) now carrying the UA 2px
        // margins, so the track ink sits at (x1,y6) inside it: with the
        // box at abs x18 the ink lands abs x19..146 × y134..142, exactly
        // the ref bands. f=0.5 → thumb cx = 1 + 8 + 0.5×111 = 64.5, i.e.
        // abs 82.5 — the ref thumb's measured centre. Accent fill left of
        // the thumb paints OVER the stroke (no gray edge under the blue).
        assertEquals(
            listOf(
                "fillRRect 1 6 127 8 4 #FFEFEFEF",
                "strokeRRect 1.5 6.5 126 7 4 #FFB2B2B2",
                "fillRRect 1 6 63.5 8 4 #FF0075FF",
                "fillCircle 64.5 10 8 #FF0075FF",
            ),
            pins(Spec(Kind.RANGE, fraction = 0.5f))
        )
        assertEquals(129f to 21f, UAWidgetsGeometry.intrinsicSize(Spec(Kind.RANGE), measure))
    }

    @Test
    fun progress_halfValue_and_indeterminate() {
        // 160x16 gauge box; 8px bar ink centered (y4) + the fix-4
        // #B2B2B2 hairline (ref edge rows y159/y166); value=0.5 → 80px
        // accent chunk covering the stroke on the value side.
        assertEquals(
            listOf(
                "fillRRect 0 4 160 8 4 #FFEFEFEF",
                "strokeRRect 0.5 4.5 159 7 4 #FFB2B2B2",
                "fillRRect 0 4 80 8 4 #FF0075FF",
            ),
            pins(Spec(Kind.PROGRESS, fraction = 0.5f))
        )
        // No value attr → indeterminate → the bare stroked track only.
        assertEquals(
            listOf(
                "fillRRect 0 4 160 8 4 #FFEFEFEF",
                "strokeRRect 0.5 4.5 159 7 4 #FFB2B2B2",
            ),
            pins(Spec(Kind.PROGRESS, fraction = null))
        )
    }

    @Test
    fun meter_greenFill_notAccent() {
        // Meter is not accent-driven (css-ui-4 §7.1) — optimum green in
        // the 80x16 gauge box (Blink 5em meter) + the fix-4 hairline
        // (the progress twin's ref probe; meter is off-canvas there).
        assertEquals(
            listOf(
                "fillRRect 0 4 80 8 4 #FFEFEFEF",
                "strokeRRect 0.5 4.5 79 7 4 #FFB2B2B2",
                "fillRRect 0 4 40 8 4 #FF00AA00",
            ),
            pins(Spec(Kind.METER, fraction = 0.5f, accent = 0xFFFF0000L))
        )
    }

    // ── color / menulist / listbox ──────────────────────────────────────

    @Test
    fun colorInput_chromeFrameWithBlackSwatch() {
        // 50x27 (the W3 atom box) chrome + border + black swatch. fix 4:
        // per-axis insets 5.5/6.5 → the ref's 39x14 swatch band
        // (x209.5..248.5 × y131.5..145.5), not the old 4px/42x19.
        assertEquals(
            listOf(
                "fillRect 0 0 50 27 #FFEFEFEF",
                "strokeRect 0.5 0.5 49 26 #FF767676",
                "fillRect 5.5 6.5 39 14 #FF000000",
            ),
            pins(Spec(Kind.COLOR))
        )
    }

    @Test
    fun menulist_whiteSlabLabelAndArrow() {
        // "select" (6×7=42 fake) + 5 left inset + 17.42 trailing band =
        // 64.42 wide. A-RC4: the slab IS the 19px box (ref borders
        // y132..150) and the arrow is a STROKED CHEVRON — two 1.8px arms
        // from (w−12.5, 7)/(w−6.5, 7) to the apex (w−9.5, 12), matching
        // the ref's chevron band y138..144 with white between the arms
        // (a filled triangle would be solid across).
        assertEquals(
            listOf(
                "fillRRect 0 0 64.42 19 2 #FFFFFFFF",
                "strokeRRect 0.5 0.5 63.42 18 2 #FF767676",
                "label 'select' 5 14 13.33 ua #FF000000",
                "line 51.92 7 54.92 12 1.8 #FF000000",
                "line 54.92 12 57.92 7 1.8 #FF000000",
            ),
            pins(Spec(Kind.MENULIST, label = "select"))
        )
        // The intrinsic box matches the slab (cross-lane: W3's SELECT
        // AtomSpec pins the same 19px height).
        assertEquals(64.42f to 19f, UAWidgetsGeometry.intrinsicSize(Spec(Kind.MENULIST, label = "select"), measure))
    }

    @Test
    fun listbox_fourVisibleOptionRows() {
        // Width hugs the widest option (+6); height fixed 70 (the W3
        // box; size=4 UA default); rows advance 17; a 5th option scrolls
        // out of view.
        val spec = Spec(Kind.LISTBOX, options = listOf("aa", "bbbb", "c", "dd", "eee"))
        assertEquals(
            listOf(
                "fillRect 0 0 34 70 #FFFFFFFF",
                "strokeRect 0.5 0.5 33 69 #FF767676",
                "label 'aa' 3 13 13.33 ua #FF000000",
                "label 'bbbb' 3 30 13.33 ua #FF000000",
                "label 'c' 3 47 13.33 ua #FF000000",
                "label 'dd' 3 64 13.33 ua #FF000000",
            ),
            pins(spec)
        )
    }

    // ── the three non-widget input types ────────────────────────────────

    @Test
    fun fileInput_chooseFileButtonPlusStatus() {
        // "Choose File" (11×7=77 fake) + 16 chrome band = 93 button;
        // status text one FILE_GAP later at 99.
        assertEquals(
            listOf(
                "fillRRect 0 0 93 21 2 #FFEFEFEF",
                "strokeRRect 0.5 0.5 92 20 2 #FF767676",
                "label 'Choose File' 8 15 13.33 ua #FF000000",
                "label 'No file chosen' 99 15 13.33 ua #FF000000",
            ),
            pins(Spec(Kind.FILE))
        )
    }

    @Test
    fun imageAndHiddenInputs() {
        // image: alt text only (ref shows "def"); hidden: nothing at all.
        assertEquals(listOf("label 'def' 0 13 13.33 ua #FF000000"), pins(Spec(Kind.IMAGE, label = "def")))
        assertEquals(emptyList<String>(), pins(Spec(Kind.HIDDEN)))
        assertEquals(0f to 0f, UAWidgetsGeometry.intrinsicSize(Spec(Kind.HIDDEN), measure))
    }
}
