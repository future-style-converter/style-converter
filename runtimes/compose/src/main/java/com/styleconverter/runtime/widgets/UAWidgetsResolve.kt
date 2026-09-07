package com.styleconverter.runtime.widgets

// Lane W2 (wave 20) — the PURE identity half of the UA-widget replicas:
// (meta.sourceTag, meta.attrs, Appearance, AccentColor, text, options) →
// UAWidgetsGeometry.Spec. Byte-parallel twin of the Swift
// UAWidgetsResolve (StyleEngine/widgets/); both unit suites pin the same
// decision table so widget IDENTITY can never diverge across natives.

import androidx.compose.ui.graphics.toArgb
import com.styleconverter.runtime.core.ir.IRAttrs
import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.types.ValueExtractors
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

object UAWidgetsResolve {

    /**
     * css-ui-4 §7 (appearance-switching): does this element paint its
     * NATIVE widget look? `none` → no (devolved to a plain styled box —
     * exactly what the appearance-*-001 tests' `appearance: none` first
     * declaration asserts on the devolved side); `auto` and every compat
     * alias (button/checkbox/…/textfield — "X is an alias to auto" per
     * the appearance-*-001 assertions) → yes. Missing Appearance → yes:
     * the UA sheet gives widget tags `appearance: auto` and the IR
     * carries no UA sheet. Global keywords: `initial` computes to the
     * property's initial value `none`; inherit/unset/revert land back at
     * auto for a bare widget (no styled ancestor in these tests).
     */
    fun appearanceIsNative(properties: List<com.styleconverter.runtime.core.ir.IRProperty>): Boolean {
        // The cascaded Appearance winner is the LAST declaration (the
        // converter emits post-cascade lists; first match is the winner).
        val data = properties.firstOrNull { it.type == "Appearance" }?.data as? JsonObject
            ?: return true // no declaration → UA-sheet auto → native
        // AppearanceValue serializes as {"type":"<value>"} (+ payload for
        // keyword/raw) — pinned against the wave-19 live wire.
        return when ((data["type"] as? JsonPrimitive)?.contentOrNull) {
            "none" -> false // devolve: no widget chrome
            // `initial` → the initial value, which css-ui-4 pins as none.
            "keyword" -> (data["keyword"] as? JsonPrimitive)?.contentOrNull != "initial"
            else -> true // auto, every compat alias, raw → native widget
        }
    }

    /**
     * Widget identity from tag + attrs (html.css UA widget mapping).
     * Returns null for non-widget elements (a, div, span …) — those keep
     * the normal text/placeholder content path.
     */
    fun kindFor(tag: String?, attrs: IRAttrs?): UAWidgetsGeometry.Kind? = when (tag) {
        "button" -> UAWidgetsGeometry.Kind.BUTTON
        "textarea" -> UAWidgetsGeometry.Kind.TEXTAREA
        "meter" -> UAWidgetsGeometry.Kind.METER
        "progress" -> UAWidgetsGeometry.Kind.PROGRESS
        // select forks on the `multiple` attribute (menulist vs listbox).
        "select" -> if (attrs?.multiple == true) UAWidgetsGeometry.Kind.LISTBOX
                    else UAWidgetsGeometry.Kind.MENULIST
        "input" -> when (attrs?.type) {
            // Absent type attribute defaults to "text" (HTML spec).
            null, "text", "search" -> UAWidgetsGeometry.Kind.TEXTFIELD
            "button", "submit", "reset" -> UAWidgetsGeometry.Kind.BUTTON
            "checkbox" -> UAWidgetsGeometry.Kind.CHECKBOX
            "radio" -> UAWidgetsGeometry.Kind.RADIO
            "range" -> UAWidgetsGeometry.Kind.RANGE
            "color" -> UAWidgetsGeometry.Kind.COLOR
            // The three NON-widget input types (appearance-auto-input-
            // non-widget-001: "the default widget type for these
            // elements is none") — they paint their own special content
            // regardless of the appearance value.
            "hidden" -> UAWidgetsGeometry.Kind.HIDDEN
            "image" -> UAWidgetsGeometry.Kind.IMAGE
            "file" -> UAWidgetsGeometry.Kind.FILE
            else -> UAWidgetsGeometry.Kind.TEXTFIELD // unknown type → text (HTML fallback)
        }
        else -> null // not a widget tag
    }

    /**
     * The used accent-color (css-ui-4 §7.1 widget-accent): an explicit
     * AccentColor wins; `currentColor` resolves against the element's
     * used `color` (the merged list — inheritance already folded in);
     * absent → the Chromium default #0075FF. Returned as packed ARGB for
     * the pure plan.
     */
    fun accentFor(properties: List<com.styleconverter.runtime.core.ir.IRProperty>): Long {
        val accentData = properties.firstOrNull { it.type == "AccentColor" }?.data
            ?: return UAWidgetsGeometry.ACCENT // no declaration → UA default
        // currentColor marker: the converter keeps {"original":"currentColor"}
        // with no srgb payload — resolve against the element's own color.
        val original = ((accentData as? JsonObject)?.get("original") as? JsonPrimitive)?.contentOrNull
        if (original?.equals("currentColor", ignoreCase = true) == true) {
            // Used color = merged `Color` (inherited or own); the WPT page
            // default ink is CanvasText black when no ancestor colors it.
            val used = properties.firstOrNull { it.type == "Color" }?.data
                ?.let { ValueExtractors.extractColor(it) }
            return (used?.toArgb()?.toLong()?.and(0xFFFFFFFFL))
                ?: UAWidgetsGeometry.BLACK
        }
        // Concrete color (srgb payload) → packed ARGB; unparsable (auto,
        // raw var()) falls back to the UA default rather than dropping ink.
        val c = ValueExtractors.extractColor(accentData)
        return c?.toArgb()?.toLong()?.and(0xFFFFFFFFL) ?: UAWidgetsGeometry.ACCENT
    }

    /**
     * Full resolve: null unless (a) the tag+attrs identify a widget AND
     * (b) appearance computes to a native look (non-widget input types
     * skip the appearance gate — they have no widget to devolve). The
     * caller (the WPT-capture mount hook) paints the Spec via
     * UAWidgetsGeometry.plan and skips the text/children content path.
     */
    fun resolve(component: IRComponent): UAWidgetsGeometry.Spec? {
        val kind = kindFor(component._tag?.lowercase(), component.attrs) ?: return null
        // Appearance gate — except for the three specials whose painting
        // is not appearance-controlled (see kindFor).
        val special = kind == UAWidgetsGeometry.Kind.HIDDEN ||
            kind == UAWidgetsGeometry.Kind.IMAGE || kind == UAWidgetsGeometry.Kind.FILE
        if (!special && !appearanceIsNative(component.properties)) return null
        val a = component.attrs
        // Value fraction for the position-taking widgets. Range: HTML
        // default min 0 / max 100 / value midpoint; value rides the wire
        // as a STRING (contract) → parse numerically here.
        val fraction: Float? = when (kind) {
            UAWidgetsGeometry.Kind.RANGE -> {
                val min = (a?.min ?: 0.0); val max = (a?.max ?: 100.0)
                // HTML §2.3.4.3 valid floating-point numbers exclude
                // NaN/Infinity, but BOTH natives' Double parsers accept
                // those strings — gate on finiteness or the thumb
                // geometry itself goes NaN (wave-20 skeptic fix; the
                // Swift twin carries the identical isFinite gate).
                val v = a?.value?.toDoubleOrNull()?.takeIf { it.isFinite() }
                // No/invalid value → the UA midpoint default (0.5).
                if (v == null || max <= min) 0.5f
                else (((v - min) / (max - min)).coerceIn(0.0, 1.0)).toFloat()
            }
            // Progress: numeric wire value / max (default 1); absent
            // value → indeterminate → null (track-only painting).
            UAWidgetsGeometry.Kind.PROGRESS -> a?.valueNumber?.let { v ->
                // HTML §4.10.13: max must be > 0 — a zero/negative wire
                // max falls back to the default 1.0 (0.0 divided the
                // fraction to NaN geometry; wave-20 skeptic fix, twin in
                // the Swift resolve).
                val m = a.max?.takeIf { it > 0.0 } ?: 1.0
                (v / m).coerceIn(0.0, 1.0).toFloat()
            }
            // Meter: value within [min, max] (defaults 0..1); absent → 0.
            UAWidgetsGeometry.Kind.METER -> {
                val min = a?.min ?: 0.0; val max = a?.max ?: 1.0
                val v = a?.valueNumber ?: min
                if (max <= min) 0f else (((v - min) / (max - min)).coerceIn(0.0, 1.0)).toFloat()
            }
            else -> null // not a fraction-taking widget
        }
        // Option rows (select children carrying meta.sourceTag "option").
        val options = component.children.orEmpty()
            .filter { it._tag?.lowercase() == "option" }
            .map { it._text.orEmpty() }
        // Visible label per widget family (doc on Spec.label).
        val label = when (kind) {
            // <button> label is its element text; input buttons use the
            // value attr (Chromium defaults "Submit"/"Reset" only matter
            // off-corpus — the tests always set value).
            UAWidgetsGeometry.Kind.BUTTON -> component._text ?: a?.value.orEmpty()
            UAWidgetsGeometry.Kind.TEXTFIELD -> a?.value.orEmpty()
            UAWidgetsGeometry.Kind.TEXTAREA -> component._text.orEmpty()
            // Menulist shows the SELECTED option (first as UA default).
            UAWidgetsGeometry.Kind.MENULIST -> {
                val sel = component.children.orEmpty()
                    .firstOrNull { it._tag?.lowercase() == "option" && it.attrs?.selected == true }
                sel?._text ?: options.firstOrNull().orEmpty()
            }
            // Image input: alt text, falling back to value (ref shows "def").
            UAWidgetsGeometry.Kind.IMAGE -> a?.alt ?: a?.value.orEmpty()
            else -> "" // chrome-only widgets carry no label
        }
        return UAWidgetsGeometry.Spec(
            kind = kind,
            checked = a?.checked == true,
            fraction = fraction,
            label = label,
            options = options,
            accent = accentFor(component.properties)
        )
    }

    /**
     * Wave-38 lane N1 — the IR-reading half of the block-context line box
     * (the arithmetic lives in the byte-parallel
     * [com.styleconverter.runtime.layout.UAWidgetBlockLine]). Reads the
     * three wire facts that solve it off the SAME merged property list
     * [resolve] uses, so the lead and the replica can never disagree about
     * which element they describe:
     *  • an explicit `Height` (post-load-extracted computed style) → the
     *    conservative gate, no lead at all;
     *  • `MarginTop` / `MarginBottom` in absolute px — already applied by
     *    the renderer's own margin modifier, so they are subtracted from
     *    the table's lead instead of being added twice.
     * Twin: Swift UAWidgetsResolve.blockLead(component:properties:).
     */
    fun blockLead(component: IRComponent): com.styleconverter.runtime.layout.UAWidgetBlockLine.Lead? =
        com.styleconverter.runtime.layout.UAWidgetBlockLine.leadFor(
            tag = component._tag?.lowercase(),
            typeAttr = component.attrs?.type,
            multiple = component.attrs?.multiple == true,
            // Any Height declaration counts — auto/percentage/calc pin the
            // box just as firmly as a px value once the renderer honours
            // them, and this gate is deliberately the conservative one.
            hasWireHeight = component.properties.any { it.type == "Height" },
            wireMarginTopPx = wireMarginPx(component, "MarginTop"),
            wireMarginBottomPx = wireMarginPx(component, "MarginBottom"),
            horizontalWritingMode = isHorizontalWritingMode(component),
        )

    /**
     * True when this element's block axis is the y axis — the only case the
     * ref-probed table describes (see the axis gate in
     * [com.styleconverter.runtime.layout.UAWidgetBlockLine.leadFor]).
     *
     * `writing-mode` INHERITS (css-writing-modes-4 §3.2) and is carried in
     * ComponentRenderer's inherited-property set, so the declaration on an
     * ancestor wrapper — which is where the css-writing-modes `forms`
     * tests put it — reaches this merged list. (Written without a glob:
     * Kotlin block comments NEST, so a literal slash-star inside this
     * KDoc would open a comment that swallows the rest of the file.)
     * Read as the raw wire keyword
     * rather than through WritingModeExtractor so the Kotlin and Swift
     * halves stay byte-parallel with no per-platform config type between
     * them. Absent ⇒ horizontal-tb, the CSS initial value.
     */
    private fun isHorizontalWritingMode(component: IRComponent): Boolean {
        val data = component.properties.firstOrNull { it.type == "WritingMode" }?.data
            ?: return true
        val keyword = (data as? JsonPrimitive)?.contentOrNull
            ?: return true   // a non-string payload is unreadable here — assume the initial value
        return keyword.equals("HORIZONTAL_TB", ignoreCase = true) ||
            keyword.equals("horizontal-tb", ignoreCase = true)
    }

    /**
     * One margin longhand in ABSOLUTE px, or 0.0 when the wire carries no
     * such declaration (0 is exactly what the renderer then applies).
     * Non-absolute shapes (`auto`, `%`, unresolved `calc()`) also read 0:
     * the runtime cannot know their used value here, and under-subtracting
     * keeps the lead at the ref value rather than silently shrinking it.
     */
    private fun wireMarginPx(component: IRComponent, type: String): Double {
        val data = component.properties.firstOrNull { it.type == type }?.data ?: return 0.0
        return (com.styleconverter.runtime.core.types.extractLength(data)
            as? com.styleconverter.runtime.core.types.LengthValue.Exact)?.px ?: 0.0
    }
}
