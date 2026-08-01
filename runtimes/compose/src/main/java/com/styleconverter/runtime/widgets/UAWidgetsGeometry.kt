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
    // A-RC4 (wave 22) — the radio ring's stroke width, re-probed. On the
    // ref the ring's WIDEST row (y139, the circle's horizontal tangent)
    // carries ink in exactly ONE column, x183 at #999999, and x182/x184
    // are pure white: total covered width ≈0.75px with the OUTER edge
    // sitting on x183.0, i.e. exactly the 13px box edge. The wave-20
    // 1.4px stroke around r=6 spans radius 5.3..6.7 — it bleeds 0.2px
    // OUTSIDE the atom box and paints two saturated #767676 columns per
    // side (the wave-21 Android capture reads 229/118/228 across
    // x176..178 where the ref reads a single 153). 1.0 around r=6 spans
    // 5.5..6.5: contained in the box, one column per side, closest match
    // available without also moving the ring radius off the box centre.
    const val RING_SW = 1.0f

    // ── Box geometry (ref-probed, CSS px) ──────────────────────────────
    // NOTE on box sizes: the BORDER-BOX constants here are aligned with
    // lane W3's UAWidgetIntrinsics (runtimes/.../layout/) — the agreed
    // cross-lane coordination point — so the inline-flow atom slots and
    // the painted replicas are the SAME boxes. Ink bands inside them are
    // this file's own ref measurements.
    const val CHECK = 13f       // checkbox/radio box (13x13, ref x158..170)
    const val CONTROL_H = 21f   // button/textfield height (ref y17..37)
    // A-RC2/A-RC4: the button's total horizontal chrome band (1px border
    // + 7px padding per side). Ref-SOLVED off row 2, whose four boxes
    // chain through three gaps and pin P tightly: with Arial advances
    // (input-button 70.42, input-submit 72.62) and gap 4.5, the painted
    // border columns 204/289 (input-button), 294/382 (input-submit) and
    // 388 (input-reset) force P ∈ [15.98, 16.08] ⇒ 2 × 8. Row 1 then
    // falls out exactly: 37.07 + 16 = 53.07 wide at x29.48 → ref border
    // columns 29 and 82. Wave 20's 7 (P=14) was fitted against Inter
    // advances and under-sized every button by ~1px once the metrics
    // were corrected.
    const val BUTTON_PAD_X = 8f
    const val RADIUS = 2f       // button/checkbox corner radius (subtle in ref)
    const val FIELD_W = 153f    // text/search field width (ref x87..239)
    const val FIELD_PAD_X = 4f  // field text inset (ref glyphs from x91)
    // A-RC4: textarea border box, ref-probed borders at cols x16/x198
    // and rows y38/y73 → 183 × 36 (wave 20 read 184 off the inclusive
    // column count). Mirrors UAWidgetIntrinsics.TEXTAREA — same box.
    const val AREA_W = 183f
    const val AREA_H = 36f      // textarea height (W3 box; ref band y38..73)
    const val RANGE_W = 129f    // range border box (Blink UA default width)
    const val RANGE_H = 21f     // range box height (W3 control-row box)
    const val TRACK_W = 127f    // track ink width (ref x19..146), centered
    const val TRACK_H = 8f      // track ink height (ref y134..141 incl edges)
    // A-RC3: track inset from the box left. Wave 20 read the range box
    // as starting at the content origin and compensated with x3; the box
    // actually starts at 18 (content 16 + the UA `margin: 2px` this wave
    // restores in UAWidgetIntrinsics), and the ref's accent-fill left
    // edge probes x19.0 at row 137 → inset 1. The track is then
    // symmetric in the box: 1 + 127 + 1 = 129.
    const val TRACK_X = 1f
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
    // A-RC4: the menulist's label inset and the trailing band (right
    // padding + dropdown-arrow button + border). Ref-probed/solved on
    // the "select" menulist: the label ink starts x263 against the box
    // border col 258 → inset 5 (border 1 + padding 4), and the box must
    // be wide enough that its right border snaps to col 314 while the
    // NEXT atom (the listbox, one 4.5 gap later) snaps to col 320 —
    // which brackets the box at [57.0, 57.5). 34.83 (Arial "select") +
    // 5 + 17.42 = 57.25 sits mid-bracket. Wave 20's `label + 4 + 16`
    // produced 54.83 and shoved the listbox 2.4px left.
    const val MENU_LABEL_X = 5f
    const val MENU_TRAIL_W = 17.42f
    const val LIST_H = 70f      // listbox height (W3 box; ref y80..149)
    const val LIST_ROW = 17f    // listbox row advance ((70-2)/4)
    // A-RC2: the UA control font-size, now sourced from the shared metric
    // pin so the size the plan measures with and the size it draws with
    // can never diverge (Chromium's `-webkit-small-control` = 13.3333px).
    const val FONT = UAControlFontMetrics.CONTROL_FONT_PX
    // A-RC4: label placement is pinned as a BASELINE offset from the box
    // top, not a layout-box top offset. WHY: the top offset only lands
    // right for one face's ascent, and this wave changes the face — a
    // baseline is face-independent, and each painter subtracts its OWN
    // measured ascent. Ref-probed 15.0 on two independent controls of
    // the 21px family: the input-text value (box top 17, glyph bottoms
    // at y32, 'p' descender y33-34 → baseline 32) and the input-button
    // label (box top 59, baseline 74).
    const val LABEL_BASELINE_Y = 15f
    // A-RC4: menulist label baseline — ref select box top 132, "select"
    // body rows end y145 with the bottom AA at y146 → baseline 146 → 14.
    const val MENU_BASELINE_Y = 14f
    // A-RC4: listbox first-option baseline — ref box top 80, the
    // "select-multiple" body ends y92 with bottom AA y93 → baseline 93.
    const val LIST_BASELINE_Y = 13f
    // A-RC4: textarea content baseline — ref box top 38, "textarea"
    // body ends y52 with bottom AA y53 → baseline 53 → 15 (the same
    // 15 as the single-line controls; Blink pads both by border 1 + 2).
    const val AREA_BASELINE_Y = 15f
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
        // Menulist: label + 5px left inset + the trailing arrow band
        // (A-RC4 constants above; ref "select" → 57.25px box).
        Kind.MENULIST -> (measure(spec.label) + MENU_LABEL_X + MENU_TRAIL_W) to MENU_H
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
                // Chrome slab + border + label on the ref baseline.
                UAWidgetOp.FillRRect(0f, 0f, w, CONTROL_H, RADIUS, CHROME),
                UAWidgetOp.StrokeRRect(0.5f, 0.5f, w - 1, CONTROL_H - 1, RADIUS, BORDER),
                UAWidgetOp.Label(spec.label, BUTTON_PAD_X, LABEL_BASELINE_Y, FONT, false, BLACK),
            )
            Kind.TEXTFIELD -> listOf(
                // White field + border + left-aligned value text.
                UAWidgetOp.FillRect(0f, 0f, FIELD_W, CONTROL_H, WHITE),
                UAWidgetOp.StrokeRect(0.5f, 0.5f, FIELD_W - 1, CONTROL_H - 1, BORDER),
                UAWidgetOp.Label(spec.label, FIELD_PAD_X, LABEL_BASELINE_Y, FONT, false, BLACK),
            )
            Kind.TEXTAREA -> listOf(
                // White area + border + top-left text + resize grip (two
                // #767676 diagonals hugging the bottom-right corner).
                UAWidgetOp.FillRect(0f, 0f, AREA_W, AREA_H, WHITE),
                UAWidgetOp.StrokeRect(0.5f, 0.5f, AREA_W - 1, AREA_H - 1, BORDER),
                // A-RC2: textarea content is the UA MONOSPACE face (mono
                // = true), not the control sans — the ref's "textarea"
                // inks 8 chars across x19..81 (7.9px/char) where Arial
                // would only need 48px total.
                UAWidgetOp.Label(spec.label, 3f, AREA_BASELINE_Y, FONT, true, BLACK),
                // A-RC4: the grip diagonals, re-fitted to the 183-wide
                // box. Ref long arm runs (190.5,71.5)→(196.5,65.5) and
                // the short one (194.5,71.5)→(196.5,69.5), i.e. 8.5/2.5
                // in from the right and 2.5/8.5 up from the bottom.
                UAWidgetOp.Line(AREA_W - 8.5f, AREA_H - 2.5f, AREA_W - 2.5f, AREA_H - 8.5f, 1f, BORDER),
                UAWidgetOp.Line(AREA_W - 4.5f, AREA_H - 2.5f, AREA_W - 2.5f, AREA_H - 4.5f, 1f, BORDER),
            )
            Kind.RANGE -> run {
                // A-RC3 (wave 22) — track ink (127x8) at (TRACK_X 1,
                // TRACK_Y 6) in the 129x21 atom box. With the range's
                // restored UA `margin: 2px` the box now sits at abs
                // x18/y128, so the track spans abs x19.0..146.0 ×
                // y134.0..142.0 — the ref's painted band is cols 19..145
                // × rows 134..141, re-probed this wave (wave 20's
                // "x3 / box x16.5" note predates both the margin and
                // TRACK_X 1 and no longer describes this plan). Thumb
                // center travels radius-inset across the TRACK, parked
                // at the value fraction (default 0.5 → ref thumb band
                // y130..145).
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
                // Label at the ref baseline, 5px in from the box left
                // (A-RC4 MENU_LABEL_X / MENU_BASELINE_Y provenance).
                UAWidgetOp.Label(spec.label, MENU_LABEL_X, MENU_BASELINE_Y, FONT, false, BLACK),
                // fix 4 — the dropdown arrow is a STROKED CHEVRON (two
                // ~1.8px arms meeting at the apex), never a filled
                // triangle: ref rows y138-141 show two ink clusters with
                // white BETWEEN them. A-RC4 re-fit against the ref's
                // chevron band (abs rows y138..144, arm-top centres
                // x≈302.7/x≈308.7, apex x≈305.5 on a box at x258 w57.25
                // → rel w−12.5 / w−6.5, apex w−9.5): the arm tops sit at
                // y7 and the apex at y12 (wave 20's 6→10.5 drew the
                // chevron 1px high and 1px too wide on each arm).
                UAWidgetOp.Line(w - 12.5f, 7f, w - 9.5f, 12f, 1.8f, BLACK),
                UAWidgetOp.Line(w - 9.5f, 12f, w - 6.5f, 7f, 1.8f, BLACK),
            )
            Kind.LISTBOX -> buildList {
                // White box + border + up to 4 visible option rows
                // (size=4 UA default; extra options scroll out of view).
                add(UAWidgetOp.FillRect(0f, 0f, w, LIST_H, WHITE))
                add(UAWidgetOp.StrokeRect(0.5f, 0.5f, w - 1, LIST_H - 1, BORDER))
                spec.options.take(4).forEachIndexed { i, opt ->
                    // Baseline-pinned rows (A-RC4): first option baseline
                    // 13 under the box top, +17 per subsequent row.
                    add(UAWidgetOp.Label(opt, 3f, LIST_BASELINE_Y + i * LIST_ROW, FONT, false, BLACK))
                }
            }
            Kind.FILE -> run {
                // "Choose File" button replica + the no-selection status
                // text (appearance-auto-input-non-widget-001 ref).
                val bw = measure(FILE_BUTTON) + 2 * BUTTON_PAD_X
                listOf(
                    UAWidgetOp.FillRRect(0f, 0f, bw, CONTROL_H, RADIUS, CHROME),
                    UAWidgetOp.StrokeRRect(0.5f, 0.5f, bw - 1, CONTROL_H - 1, RADIUS, BORDER),
                    UAWidgetOp.Label(FILE_BUTTON, BUTTON_PAD_X, LABEL_BASELINE_Y, FONT, false, BLACK),
                    UAWidgetOp.Label(FILE_STATUS, bw + FILE_GAP, LABEL_BASELINE_Y, FONT, false, BLACK),
                )
            }
            // Image input without src: Chromium falls back to the alt
            // text (value when alt is absent). The broken-image glyph is
            // deliberately NOT replicated (small ink; noted lane risk).
            // Baseline 13 in the 16px alt-text line (the same 13 the
            // listbox rows use — one UA control line, border-free box).
            Kind.IMAGE -> listOf(UAWidgetOp.Label(spec.label, 0f, LIST_BASELINE_Y, FONT, false, BLACK))
            // hidden: the UA sheet says display:none — nothing paints.
            Kind.HIDDEN -> emptyList()
        }
    }
}
