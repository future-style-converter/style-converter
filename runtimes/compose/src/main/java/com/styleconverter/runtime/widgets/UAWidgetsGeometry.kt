package com.styleconverter.runtime.widgets

// Lane W2 (wave 20) — the PURE half of the UA-widget replica painter:
// Chromium-lookalike form-control geometry as a platform-neutral paint
// plan. This file is the CROSS-PLATFORM PIN TABLE: its Swift twin
// (runtimes/swiftui .../StyleEngine/widgets/UAWidgetsGeometry.swift) is
// byte-parallel — same constants, same op order, same `pin` string
// encoding — and both unit suites assert the IDENTICAL expected strings,
// so the two natives can never drift apart silently.
//
// Every number below is MEASURED from the corpus-v4.1 browser-ref renders
// (tools/wpt/refs/<hash>/white-black-ink-font-lh/css-ui/*.png, probed at
// pixel level — appearance-auto-001 for the unchecked row, accent-color-
// visited for the checked checkbox): Chromium's light-scheme UA controls
// paint chrome #EFEFEF, border #767676 and accent #0075FF (html.css +
// the 2020 form-controls refresh). WPT-capture-mode-gated at the mount
// hooks — the dark-stage 327 baseline never routes through this file.

/** Palette + geometry constants (see file header for the measurement
 *  provenance). Object-scoped so tests and the painter share one source. */
object UAWidgetsGeometry {
    // ── Palette (Chromium light-scheme UA controls, ref-probed) ────────
    const val CHROME = 0xFFEFEFEFL  // button/track chrome fill
    const val BORDER = 0xFF767676L  // hairline control border
    const val ACCENT = 0xFF0075FFL  // default accent-color (checked/fill ink)
    const val WHITE = 0xFFFFFFFFL   // field interiors + checked check mark
    const val BLACK = 0xFF000000L   // label ink (UA CanvasText, light scheme)
    const val METER_GREEN = 0xFF00AA00L // meter optimum fill (not accent-driven)
    // fix 4: the gauge/track hairline — Chromium's 2020 form-controls
    // refresh strokes the range track and progress/meter bars in a
    // LIGHTER gray than the control border. Ref-probed on the white ref:
    // range track edge rows y134/y141 and progress edge rows y159/y166
    // both read #B2B2B2 while the checkbox border reads #767676.
    const val TRACK_EDGE = 0xFFB2B2B2L
    // fix 4: the radio ring's effective stroke width. The ref's ring
    // samples ≈#8F8F8F-#A1A1A1 at 1px (white ref x183/x195 @ y138 — a
    // ~1.4px #767676 stroke antialiased over 2 columns), while a plain
    // 1px AA stroke sampled ≈#CCCCCC on the Android capture — visibly
    // thinner ink. Circle strokes only: the checkbox's rect stroke sits
    // on the half-px grid and rasterizes crisp at 1px, matching the ref.
    const val RING_SW = 1.4f

    // ── Box geometry (ref-probed, CSS px) ──────────────────────────────
    // NOTE on box sizes: the BORDER-BOX constants here are aligned with
    // lane W3's UAWidgetIntrinsics (runtimes/.../layout/) — the agreed
    // cross-lane coordination point — so the inline-flow atom slots and
    // the painted replicas are the SAME boxes. Ink bands inside them are
    // this file's own ref measurements.
    const val CHECK = 13f       // checkbox/radio box (13x13, ref x158..170)
    const val CONTROL_H = 21f   // button/textfield height (ref y17..37)
    const val BUTTON_PAD_X = 7f // button label inset ("button" 54px @ label 40px)
    const val RADIUS = 2f       // button/checkbox corner radius (subtle in ref)
    const val FIELD_W = 153f    // text/search field width (ref x87..239)
    const val FIELD_PAD_X = 4f  // field text inset (ref glyphs from x91)
    const val AREA_W = 184f     // textarea width (W3 box; ref band x16..198)
    const val AREA_H = 36f      // textarea height (W3 box; ref band y38..73)
    const val RANGE_W = 129f    // range border box (Blink UA default width)
    const val RANGE_H = 21f     // range box height (W3 control-row box)
    const val TRACK_W = 127f    // track ink width (ref x19.5..145.5), centered
    const val TRACK_H = 8f      // track ink height (ref y134..141 incl edges)
    const val TRACK_X = 3f      // fix 4: track inset from the box left (ref
                                // track left x19.5 with the range box at
                                // x16.5 → ~3px; the old x1 put the whole
                                // track+thumb 2px left of the ref)
    const val TRACK_Y = 6f      // fix 4: track top inset (box top 127.5 →
                                // ink top 133.5 ≈ y6; was 6.5)
    const val THUMB_R = 8f      // range thumb radius (d16, ref y130..145)
    const val BAR_H = 16f       // progress/meter BOX height (W3 gauge box)
    const val BAR_INK_H = 8f    // bar ink height (ref y159..166), centered
    const val BAR_R = 4f        // bar end radius (ref corner AA)
    const val PROGRESS_W = 160f // progress width (ref x16..175)
    const val METER_W = 80f     // meter width (Blink 5em gauge; ref cut at x314+)
    const val COLOR_W = 50f     // color input width (ref x204..253)
    const val COLOR_H = 27f     // color input height (W3 box; ref y125..151)
    // fix 4: the swatch frame is NOT the uniform 4px the first cut used —
    // ref-probed swatch band x209.5..248.5 × y131.5..145.5 inside the
    // 50×27 box at x204/y125 → horizontal inset 5.5, vertical inset 6.5
    // (a 39×14 swatch; the old 4px painted an oversized 42×19 swatch).
    const val COLOR_INSET_X = 5.5f
    const val COLOR_INSET_Y = 6.5f
    const val MENU_H = 19f      // fix 4: menulist BOX height — ref slab
                                // borders y132..150 = 19px (probed at the
                                // white ref x280 column; the old 25px box
                                // with 21px centered ink drew a taller
                                // slab than Chromium's). Cross-lane: W3's
                                // UAWidgetIntrinsics SELECT fixedHpx pins
                                // the SAME 19 — the ink now IS the box.
    const val LIST_H = 70f      // listbox height (W3 box; ref y80..149)
    const val LIST_ROW = 17f    // listbox row advance ((70-2)/4)
    const val FONT = 13.33f     // UA control font-size (Chromium 13.333px)
    const val LABEL_Y = 3.5f    // label top inset centering FONT in CONTROL_H
    const val FILE_GAP = 6f     // gap between file button and status text

    /** Fixed file-input strings (Chromium UA, en locale — the ref's). */
    const val FILE_BUTTON = "Choose File"
    const val FILE_STATUS = "No file chosen"

    /** The replica kinds — one per distinct Chromium widget painting. */
    enum class Kind { CHECKBOX, RADIO, BUTTON, TEXTFIELD, TEXTAREA, RANGE,
                      PROGRESS, METER, COLOR, MENULIST, LISTBOX, FILE, IMAGE, HIDDEN }

    /** Resolved paint inputs for one widget instance. [fraction] is the
     *  value position for range/progress/meter (null = indeterminate
     *  progress → track only); [label] is the visible string (button
     *  label / field value / textarea text / menulist selection / image
     *  alt); [options] are the listbox rows; [accent] the used
     *  accent-color (css-ui-4 §7.1 widget-accent). */
    data class Spec(
        val kind: Kind,
        val checked: Boolean = false,
        val fraction: Float? = null,
        val label: String = "",
        val options: List<String> = emptyList(),
        val accent: Long = ACCENT,
    )

    /** Intrinsic border-box size — the component box the mount hook sizes
     *  to. [measure] is the platform's Inter width probe at FONT px
     *  (injected so this stays pure/JVM-pinnable). */
    fun intrinsicSize(spec: Spec, measure: (String) -> Float): Pair<Float, Float> = when (spec.kind) {
        Kind.CHECKBOX, Kind.RADIO -> CHECK to CHECK
        // Button hugs its label plus the chrome padding band.
        Kind.BUTTON -> (measure(spec.label) + 2 * BUTTON_PAD_X) to CONTROL_H
        Kind.TEXTFIELD -> FIELD_W to CONTROL_H
        Kind.TEXTAREA -> AREA_W to AREA_H
        Kind.RANGE -> RANGE_W to RANGE_H
        Kind.PROGRESS -> PROGRESS_W to BAR_H
        Kind.METER -> METER_W to BAR_H
        Kind.COLOR -> COLOR_W to COLOR_H
        // Menulist: label + 4px inset + 16px arrow zone (ref "select" 56px).
        Kind.MENULIST -> (measure(spec.label) + FIELD_PAD_X + 16f) to MENU_H
        // Listbox: widest option + 3px text inset both sides.
        Kind.LISTBOX -> ((spec.options.maxOfOrNull(measure) ?: 0f) + 6f) to LIST_H
        // File: button + gap + status text, one control-height line.
        Kind.FILE -> (measure(FILE_BUTTON) + 2 * BUTTON_PAD_X + FILE_GAP + measure(FILE_STATUS)) to CONTROL_H
        // Image input with no src: alt text only (one ~16px text line).
        Kind.IMAGE -> measure(spec.label) to 16f
        // display:none per the UA sheet — occupies nothing.
        Kind.HIDDEN -> 0f to 0f
    }

    /** The paint plan — ordered back-to-front ops for one widget. Pure:
     *  the platform painters only replay it. */
    fun plan(spec: Spec, measure: (String) -> Float): List<UAWidgetOp> {
        val (w, h) = intrinsicSize(spec, measure)
        return when (spec.kind) {
            Kind.CHECKBOX ->
                if (spec.checked)
                    // Checked: accent-filled rounded box + white check
                    // polyline (two strokes) — accent-color-visited ref.
                    listOf(
                        UAWidgetOp.FillRRect(0f, 0f, CHECK, CHECK, RADIUS, spec.accent),
                        UAWidgetOp.Line(3.25f, 6.75f, 5.25f, 8.75f, 1.6f, WHITE),
                        UAWidgetOp.Line(5.25f, 8.75f, 9.75f, 4.25f, 1.6f, WHITE),
                    )
                else
                    // Unchecked: white interior + hairline border only.
                    listOf(
                        UAWidgetOp.FillRRect(0f, 0f, CHECK, CHECK, RADIUS, WHITE),
                        UAWidgetOp.StrokeRRect(0.5f, 0.5f, CHECK - 1, CHECK - 1, RADIUS, BORDER),
                    )
            Kind.RADIO ->
                if (spec.checked)
                    // Checked: accent donut as three concentric fills
                    // (outer accent, white gap, accent dot) — stroke-free
                    // so both natives rasterize identically.
                    listOf(
                        UAWidgetOp.FillCircle(6.5f, 6.5f, 6.5f, spec.accent),
                        UAWidgetOp.FillCircle(6.5f, 6.5f, 4.75f, WHITE),
                        UAWidgetOp.FillCircle(6.5f, 6.5f, 3f, spec.accent),
                    )
                else
                    // Unchecked: white disc + hairline ring.
                    listOf(
                        UAWidgetOp.FillCircle(6.5f, 6.5f, 6.5f, WHITE),
                        UAWidgetOp.StrokeCircle(6.5f, 6.5f, 6f, BORDER),
                    )
            Kind.BUTTON -> listOf(
                // Chrome slab + border + centered-band label (pad 7).
                UAWidgetOp.FillRRect(0f, 0f, w, CONTROL_H, RADIUS, CHROME),
                UAWidgetOp.StrokeRRect(0.5f, 0.5f, w - 1, CONTROL_H - 1, RADIUS, BORDER),
                UAWidgetOp.Label(spec.label, BUTTON_PAD_X, LABEL_Y, FONT, BLACK),
            )
            Kind.TEXTFIELD -> listOf(
                // White field + border + left-aligned value text.
                UAWidgetOp.FillRect(0f, 0f, FIELD_W, CONTROL_H, WHITE),
                UAWidgetOp.StrokeRect(0.5f, 0.5f, FIELD_W - 1, CONTROL_H - 1, BORDER),
                UAWidgetOp.Label(spec.label, FIELD_PAD_X, LABEL_Y, FONT, BLACK),
            )
            Kind.TEXTAREA -> listOf(
                // White area + border + top-left text + resize grip (two
                // #767676 diagonals hugging the bottom-right corner).
                UAWidgetOp.FillRect(0f, 0f, AREA_W, AREA_H, WHITE),
                UAWidgetOp.StrokeRect(0.5f, 0.5f, AREA_W - 1, AREA_H - 1, BORDER),
                UAWidgetOp.Label(spec.label, 3f, 2f, FONT, BLACK),
                UAWidgetOp.Line(AREA_W - 7, AREA_H - 2, AREA_W - 2, AREA_H - 7, 1f, BORDER),
                UAWidgetOp.Line(AREA_W - 4, AREA_H - 2, AREA_W - 2, AREA_H - 4, 1f, BORDER),
            )
            Kind.RANGE -> run {
                // fix 4 — track ink (127x8) at (x3, y6) in the 129x21
                // atom box (ref band x19.5..145.5 × y134..141 against
                // the box at x16.5/y127.5); thumb center travels
                // radius-inset across the TRACK, parked at the value
                // fraction (default 0.5 → ref thumb right edge x90.5).
                val cx = TRACK_X + THUMB_R + (spec.fraction ?: 0.5f) * (TRACK_W - 2 * THUMB_R)
                listOf(
                    // Full chrome track, vertically centered in the box.
                    UAWidgetOp.FillRRect(TRACK_X, TRACK_Y, TRACK_W, TRACK_H, BAR_R, CHROME),
                    // fix 4 — the #B2B2B2 hairline the refresh strokes
                    // around the track (ref edge rows y134/y141).
                    UAWidgetOp.StrokeRRect(TRACK_X + 0.5f, TRACK_Y + 0.5f, TRACK_W - 1, TRACK_H - 1, BAR_R, TRACK_EDGE),
                    // Accent fill left of the thumb (Chromium's value
                    // side) — painted OVER the stroke: the ref's value
                    // side shows no gray edge under the blue.
                    UAWidgetOp.FillRRect(TRACK_X, TRACK_Y, cx - TRACK_X, TRACK_H, BAR_R, spec.accent),
                    // Accent thumb disc riding the box center line
                    // (cy 10 → ref thumb band y130..145, center 137.5).
                    UAWidgetOp.FillCircle(cx, 10f, THUMB_R, spec.accent),
                )
            }
            Kind.PROGRESS -> buildList {
                // 8px bar ink centered in the 160x16 gauge box (y4).
                // Chrome track always paints; the accent value chunk only
                // for determinate bars (no value attr → indeterminate →
                // static capture shows the bare track).
                add(UAWidgetOp.FillRRect(0f, 4f, PROGRESS_W, BAR_INK_H, BAR_R, CHROME))
                // fix 4 — the refresh's #B2B2B2 bar hairline (ref edge
                // rows y159/y166); the value chunk covers it on the left.
                add(UAWidgetOp.StrokeRRect(0.5f, 4.5f, PROGRESS_W - 1, BAR_INK_H - 1, BAR_R, TRACK_EDGE))
                spec.fraction?.let { add(UAWidgetOp.FillRRect(0f, 4f, PROGRESS_W * it, BAR_INK_H, BAR_R, spec.accent)) }
            }
            Kind.METER -> listOf(
                // Meter is NOT accent-driven (css-ui-4 §7.1 lists the
                // accent-taking widgets; meter keeps its optimum green).
                // 8px ink centered in the 80x16 gauge box (y4).
                UAWidgetOp.FillRRect(0f, 4f, METER_W, BAR_INK_H, BAR_R, CHROME),
                // fix 4 — same refresh hairline as the progress twin
                // (meter is off-canvas in the ref; progress is the probe).
                UAWidgetOp.StrokeRRect(0.5f, 4.5f, METER_W - 1, BAR_INK_H - 1, BAR_R, TRACK_EDGE),
                UAWidgetOp.FillRRect(0f, 4f, METER_W * (spec.fraction ?: 0f), BAR_INK_H, BAR_R, METER_GREEN),
            )
            Kind.COLOR -> listOf(
                // Chrome frame + border + the inner swatch (default value
                // #000000 — the tests never set a value attr). fix 4:
                // per-axis insets 5.5/6.5 → the ref's 39x14 swatch band
                // (x209.5..248.5 × y131.5..145.5), not the old 42x19.
                UAWidgetOp.FillRect(0f, 0f, COLOR_W, COLOR_H, CHROME),
                UAWidgetOp.StrokeRect(0.5f, 0.5f, COLOR_W - 1, COLOR_H - 1, BORDER),
                UAWidgetOp.FillRect(COLOR_INSET_X, COLOR_INSET_Y, COLOR_W - 2 * COLOR_INSET_X, COLOR_H - 2 * COLOR_INSET_Y, BLACK),
            )
            Kind.MENULIST -> listOf(
                // fix 4 — white slab (ref-probed: menulist is white, not
                // chrome) now fills the WHOLE 19px box (MENU_H doc): the
                // ref slab IS the atom box, borders y132..150.
                UAWidgetOp.FillRRect(0f, 0f, w, MENU_H, RADIUS, WHITE),
                UAWidgetOp.StrokeRRect(0.5f, 0.5f, w - 1, MENU_H - 1, RADIUS, BORDER),
                // Label 2.5px under the slab top: 13.33px Inter's ~16px
                // text line centered in 19 → (19−16)/2 ≈ 1.5 + the ref's
                // glyph band (ink y140..145 abs, baseline ≈145.5) lands
                // with y2.5 (old 25-box put it 2px lower).
                UAWidgetOp.Label(spec.label, FIELD_PAD_X, 2.5f, FONT, BLACK),
                // fix 4 — the dropdown arrow is a STROKED CHEVRON (two
                // ~1.8px arms meeting at the apex), never a filled
                // triangle: ref rows y138-141 show two ink clusters with
                // white BETWEEN them. Ref-fit: arms from (w−13.5, 6) and
                // (w−5.5, 6) to the apex (w−9.5, 10.5) — abs x301.5/
                // x309.5 → 305.5, y138 → 142.5 on the 57px ref select.
                UAWidgetOp.Line(w - 13.5f, 6f, w - 9.5f, 10.5f, 1.8f, BLACK),
                UAWidgetOp.Line(w - 9.5f, 10.5f, w - 5.5f, 6f, 1.8f, BLACK),
            )
            Kind.LISTBOX -> buildList {
                // White box + border + up to 4 visible option rows
                // (size=4 UA default; extra options scroll out of view).
                add(UAWidgetOp.FillRect(0f, 0f, w, LIST_H, WHITE))
                add(UAWidgetOp.StrokeRect(0.5f, 0.5f, w - 1, LIST_H - 1, BORDER))
                spec.options.take(4).forEachIndexed { i, opt ->
                    add(UAWidgetOp.Label(opt, 3f, 1f + i * LIST_ROW, FONT, BLACK))
                }
            }
            Kind.FILE -> run {
                // "Choose File" button replica + the no-selection status
                // text (appearance-auto-input-non-widget-001 ref).
                val bw = measure(FILE_BUTTON) + 2 * BUTTON_PAD_X
                listOf(
                    UAWidgetOp.FillRRect(0f, 0f, bw, CONTROL_H, RADIUS, CHROME),
                    UAWidgetOp.StrokeRRect(0.5f, 0.5f, bw - 1, CONTROL_H - 1, RADIUS, BORDER),
                    UAWidgetOp.Label(FILE_BUTTON, BUTTON_PAD_X, LABEL_Y, FONT, BLACK),
                    UAWidgetOp.Label(FILE_STATUS, bw + FILE_GAP, LABEL_Y, FONT, BLACK),
                )
            }
            // Image input without src: Chromium falls back to the alt
            // text (value when alt is absent). The broken-image glyph is
            // deliberately NOT replicated (small ink; noted lane risk).
            Kind.IMAGE -> listOf(UAWidgetOp.Label(spec.label, 0f, 1f, FONT, BLACK))
            // hidden: the UA sheet says display:none — nothing paints.
            Kind.HIDDEN -> emptyList()
        }
    }
}
