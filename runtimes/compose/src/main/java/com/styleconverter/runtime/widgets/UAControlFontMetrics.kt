package com.styleconverter.runtime.widgets

// UAControlFontMetrics — wave-22 lane INK, pin A-RC2 (UA-METRIC FONTS).
//
// WHY THIS FILE EXISTS. Through wave 21 both natives measured UA form
// control labels with their own Inter face, because Inter is what the
// composed-WPT ref pins for PROSE (capture-browser-ref.mjs injects
// `:where(body) { font-family: 'Inter', … }` at zero specificity). But
// form controls do NOT inherit that: Chromium's html.css gives
// input/button/select/textarea `font: 13.3333px <UA control face>`, and
// the UA control face is ARIAL-METRIC (Helvetica's advance set), not
// Inter. Measured on the oracle — the css-ui appearance-auto-001
// white-black-ink-font-lh ref, which the WEB capture reproduces at
// SSIM 1.000 — the `input-text` value ink spans x91..145 (55px) inside
// the field box at x87: Arial's advance sum for "input-text" at
// 13.3333px is 54.84px, Inter's is 61.43px. Inter therefore over-measured
// every label-driven box (the ref button box is x29..82 = 54px, the
// natives painted 55) and every downstream atom in the row inherited the
// error.
//
// WHAT IT PINS. The Arial/Helvetica ASCII advance table (units per em
// 2048, the standard Helvetica width set that Arial, Liberation Sans and
// Arimo all share by design) plus the 0.6em monospace advance Chromium's
// UA textarea font uses. Both natives call THIS table for the pure
// geometry's `measure` probe, so widget box sizes are now IDENTICAL on
// Android and iOS by construction — the metric half of the widget band
// no longer depends on which faces the device happens to ship. Byte-
// parallel twin: runtimes/swiftui .../StyleEngine/widgets/
// UAControlFontMetrics.swift (same numbers, same order, same helpers).
object UAControlFontMetrics {

    /** Design units per em of the pinned table (Arial/Helvetica: 2048). */
    const val UPM: Float = 2048f

    /**
     * Chromium's UA control font-size. html.css resolves `font:
     * -webkit-small-control` to 13.3333px at the default 16px root (the
     * classic 13⅓ = 16 × 5/6); the ref's control glyph bands measure to
     * that size (x-height 6.9px ≈ Arial's 1062/2048 em at 13.3333).
     */
    const val CONTROL_FONT_PX: Float = 13.3333f

    /**
     * Advance widths in 2048-unit em for printable ASCII 0x20..0x7E, read
     * out of Arial's `hmtx` table (macOS /System/Library/Fonts/
     * Supplemental/Arial.ttf, unitsPerEm 2048). Only the ASCII band is
     * pinned: every string the css-ui corpus feeds a widget label
     * ("button", "input-submit", "select-multiple", "Choose File", …) is
     * ASCII, and a table covering more would be unverifiable against the
     * ref. Out-of-band code points fall back to DEFAULT_ADVANCE below —
     * loudly documented, never silently zero-width.
     */
    private val ASCII_ADVANCE_2048 = intArrayOf(
        569, 569, 727, 1139, 1139, 1821, 1366, 391, 682, 682,      // ' ' ! " # $ % & ' ( )
        797, 1196, 569, 682, 569, 569, 1139, 1139, 1139, 1139,     // * + , - . / 0 1 2 3
        1139, 1139, 1139, 1139, 1139, 1139, 569, 569, 1196, 1196,  // 4 5 6 7 8 9 : ; < =
        1196, 1139, 2079, 1366, 1366, 1479, 1479, 1366, 1251, 1593,// > ? @ A B C D E F G
        1479, 569, 1024, 1366, 1139, 1706, 1479, 1593, 1366, 1593, // H I J K L M N O P Q
        1479, 1366, 1251, 1479, 1366, 1933, 1366, 1366, 1251, 569, // R S T U V W X Y Z [
        569, 569, 961, 1139, 682, 1139, 1139, 1024, 1139, 1139,    // \ ] ^ _ ` a b c d e
        569, 1139, 1139, 455, 455, 1024, 455, 1706, 1139, 1139,    // f g h i j k l m n o
        1139, 1139, 682, 1024, 569, 1139, 1024, 1479, 1024, 1024,  // p q r s t u v w x y
        1024, 684, 532, 684, 1196,                                 // z { | } ~
    )

    /** First code point the table covers (U+0020 SPACE). */
    private const val FIRST_CP: Int = 0x20

    /**
     * Fallback advance for anything outside the pinned ASCII band: Arial's
     * 'n'/'o' advance (1139/2048 ≈ 0.556em), the width the classic
     * Helvetica set uses for most lowercase. Chosen over 0 so a non-ASCII
     * label still reserves plausible box width instead of collapsing the
     * control; the divergence is a KNOWN, stated limit of this pin rather
     * than a silent fallthrough (no css-ui corpus label reaches it).
     */
    private const val DEFAULT_ADVANCE_2048: Int = 1139

    /**
     * Monospace advance in em. Chromium's UA textarea/`<input>`-monospace
     * font is a 0.6em-advance face (Courier New, Menlo, Cousine and Droid
     * Sans Mono all pin 0.6em); ref-checked on appearance-auto-001, whose
     * textarea content "textarea" (8 chars) inks x19..81 = 8 × 7.9px
     * against 8 × 8.0px predicted at 13.3333px.
     */
    const val MONO_ADVANCE_EM: Float = 0.6f

    /** One character's advance in CSS px at [sizePx] (table lookup). */
    fun charAdvance(ch: Char, sizePx: Float): Float {
        // Index into the ASCII band; anything else takes the documented
        // 'n'-width fallback above.
        val i = ch.code - FIRST_CP
        val units = if (i >= 0 && i < ASCII_ADVANCE_2048.size) ASCII_ADVANCE_2048[i]
                    else DEFAULT_ADVANCE_2048
        // Design units → CSS px. No hinting/rounding: Chromium lays text
        // out in fractional units and only snaps the painted box.
        return units * sizePx / UPM
    }

    /**
     * Advance width of a whole label in the UA control face. Kerning is
     * deliberately ignored: Arial/Helvetica apply no kern pairs to the
     * corpus' label strings, and the ref widths reproduce to <0.1px from
     * the plain advance sum (see the file header's 54.84 vs 55 check).
     */
    fun advance(text: String, sizePx: Float): Float {
        // Straight sum — the same loop the Swift twin runs.
        var total = 0f
        for (ch in text) total += charAdvance(ch, sizePx)
        return total
    }

    /** Monospace advance of a whole label (textarea content, 0.6em/char). */
    fun monoAdvance(text: String, sizePx: Float): Float =
        text.length * MONO_ADVANCE_EM * sizePx
}
