package com.styleconverter.runtime.widgets

// UAWidgetOp — the platform-neutral paint-instruction type of the
// UA-widget replica plan, split out of UAWidgetsGeometry.kt (wave-20
// fix 4; file-size rule: keep each file ≤~300 lines) exactly like the
// Swift twin's UAWidgetOp.swift. See UAWidgetsGeometry.kt's header for
// the pin-table contract; the `pin` encoding here is byte-parallel with
// runtimes/swiftui .../StyleEngine/widgets/UAWidgetOp.swift.

/** One platform-neutral paint instruction. Coordinates are CSS px from the
 *  widget box's top-left; colors are packed ARGB (identical literals on
 *  both natives). `pin` is the language-neutral encoding the twin test
 *  suites assert on — one line per op, space-separated, %.2f-trimmed. */
sealed class UAWidgetOp(val pin: String) {
    /** Axis-aligned fill — track/field/chrome interiors. */
    class FillRect(val x: Float, val y: Float, val w: Float, val h: Float, val color: Long) :
        UAWidgetOp("fillRect ${f(x)} ${f(y)} ${f(w)} ${f(h)} ${c(color)}")
    /** 1px inside stroke — the UA controls' hairline #767676 border. */
    class StrokeRect(val x: Float, val y: Float, val w: Float, val h: Float, val color: Long) :
        UAWidgetOp("strokeRect ${f(x)} ${f(y)} ${f(w)} ${f(h)} ${c(color)}")
    /** Rounded fill — button/checkbox chrome (r≈2) and bar tracks (r=4). */
    class FillRRect(val x: Float, val y: Float, val w: Float, val h: Float, val r: Float, val color: Long) :
        UAWidgetOp("fillRRect ${f(x)} ${f(y)} ${f(w)} ${f(h)} ${f(r)} ${c(color)}")
    /** Rounded 1px stroke — the border twin of FillRRect. */
    class StrokeRRect(val x: Float, val y: Float, val w: Float, val h: Float, val r: Float, val color: Long) :
        UAWidgetOp("strokeRRect ${f(x)} ${f(y)} ${f(w)} ${f(h)} ${f(r)} ${c(color)}")
    /** Filled circle — radio rings (as concentric fills) + range thumb. */
    class FillCircle(val cx: Float, val cy: Float, val r: Float, val color: Long) :
        UAWidgetOp("fillCircle ${f(cx)} ${f(cy)} ${f(r)} ${c(color)}")
    /** Stroked circle (1px) — the unchecked radio's #767676 ring. */
    class StrokeCircle(val cx: Float, val cy: Float, val r: Float, val color: Long) :
        UAWidgetOp("strokeCircle ${f(cx)} ${f(cy)} ${f(r)} ${c(color)}")
    /** Stroked segment — checkbox check mark, textarea resize grip and
     *  (fix 4) the menulist chevron arms. The ref's menulist arrow is a
     *  STROKED chevron, not a filled triangle (white-black-ink ref rows
     *  y138-143 show two ~2px arms with a WHITE gap between them at
     *  y138-141 — a filled triangle would be solid across); the old
     *  FillTriangle op painted that solid wedge and is retired. */
    class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val sw: Float, val color: Long) :
        UAWidgetOp("line ${f(x1)} ${f(y1)} ${f(x2)} ${f(y2)} ${f(sw)} ${c(color)}")
    /** Text run, top-left anchored — executed with the platform's Inter
     *  face at [size]px (labels are the only platform-divergent ink). */
    class Label(val text: String, val x: Float, val y: Float, val size: Float, val color: Long) :
        UAWidgetOp("label '${text}' ${f(x)} ${f(y)} ${f(size)} ${c(color)}")

    companion object {
        /** Trimmed %.2f so 13.0 pins as "13" and 17.75 as "17.75" — the
         *  Swift twin formats identically (byte-parallel pin contract).
         *  Locale.ROOT: pins must be byte-stable on comma-decimal hosts. */
        fun f(v: Float): String =
            String.format(java.util.Locale.ROOT, "%.2f", v).trimEnd('0').trimEnd('.')
        /** ARGB as #AARRGGBB — the palette half of every pin assert. */
        fun c(argb: Long): String = String.format(java.util.Locale.ROOT, "#%08X", argb)
    }
}

