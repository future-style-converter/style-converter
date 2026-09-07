package app.parsing.css.properties

import app.irmodels.IRProperty
import app.parsing.css.CssPropertyValue
import app.parsing.css.cssParsing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Pins the wave-43 cascade fix for WPT css/CSS2/cascade/inherit-computed-001:
 * a winning BASE `inherit` on an inherited-by-default longhand is dropped
 * (CSS22 §6.2.1 — the declaration is byte-equivalent to absence, and the
 * unresolved `{global:"inherit"}` marker shadowed the natives' inheritance
 * channels), while bucket call sites (selectors/media/keyframes) keep
 * emitting the keyword because there `inherit` OVERRIDES the base.
 */
class InheritedDefaultResolutionTest {

    // Small helper: run the parser exactly like the base-declaration site.
    // `sourceTag` mirrors the extractor's `_tag` hint; null = the untagged
    // generic element the majority of fixtures carry.
    private fun parseBase(vararg decls: Pair<String, String>, sourceTag: String? = null): List<IRProperty> =
        PropertiesParser.parse(
            decls.toMap().mapValues { CssPropertyValue(it.value) },
            resolveInheritedDefaults = true,
            sourceTag = sourceTag
        )

    // Small helper: run the parser exactly like a selector/media bucket.
    private fun parseBucket(vararg decls: Pair<String, String>): List<IRProperty> =
        PropertiesParser.parse(decls.toMap().mapValues { CssPropertyValue(it.value) })

    // ── The pinned WPT shape: span { font-size: inherit } ────────────────
    @Test
    fun `base font-size inherit is dropped — absence IS the inherited computed value`() {
        val out = parseBase("font-size" to "inherit")
        // No FontSize property may survive: the natives' channel merge must
        // see the type undeclared so the parent's computed entry flows in.
        assertTrue(out.none { it.propertyName == "font-size" }, "FontSize must be dropped, got $out")
    }

    @Test
    fun `keyword match is trimmed and case-insensitive per css-values-4 §4·1`() {
        // "  INHERIT  " is the same CSS-wide keyword — must drop identically.
        val out = parseBase("color" to "  INHERIT  ")
        assertTrue(out.none { it.propertyName == "color" }, "Color must be dropped, got $out")
    }

    @Test
    fun `non-inherit values on inherited properties are untouched`() {
        // A real value must keep parsing exactly as before the fix.
        val out = parseBase("font-size" to "16px")
        assertEquals(1, out.count { it.propertyName == "font-size" })
    }

    // ── The non-inherited half stays unresolved (inherit-computed-002) ───
    @Test
    fun `border-top-width inherit is NOT dropped — non-inherited properties keep the marker`() {
        // border-*-width is "Inherited: no": absence would mean the INITIAL
        // value (medium), not the parent's computed value, so the resolver
        // must leave the declaration alone for a future tree-pass to solve.
        val out = parseBase("border-top-width" to "inherit")
        assertTrue(out.any { it.propertyName == "border-top-width" }, "BorderTopWidth must survive, got $out")
    }

    // ── Shorthand path: font: inherit expands per-longhand, then resolves ─
    @Test
    fun `font shorthand inherit resolves through the same rule after expansion`() {
        val out = parseBase("font" to "inherit")
        // Every font longhand is inherited-by-default → the whole expansion
        // must vanish (FontExpander forwards `inherit` verbatim per longhand,
        // FontExpanderTest pins that; Step 2.5 then drops each one).
        assertTrue(
            out.none { it.propertyName in setOf("font-size", "font-family", "font-weight", "font-style", "line-height") },
            "font: inherit must fully resolve to absence, got $out"
        )
    }

    // ── Bucket call sites keep the keyword (override semantics) ──────────
    @Test
    fun `bucket parse keeps font-size inherit unresolved — inherit overrides the base there`() {
        val out = parseBucket("font-size" to "inherit")
        // Default flag=false path: the global-keyword property must survive
        // so a :hover/media bucket can still override its base declaration.
        assertEquals(1, out.count { it.propertyName == "font-size" })
    }

    // ── Set + fast-path unit behaviour ───────────────────────────────────
    @Test
    fun `resolve returns the SAME map instance when nothing is redundant`() {
        // Identity fast path — the committed-fixture stability argument.
        val input = mapOf("font-size" to "16px", "border-top-width" to "inherit")
        assertSame(input, InheritedDefaultResolution.resolve(input, sourceTag = null))
    }

    @Test
    fun `spec-inherited names OUTSIDE the runtime channels are not in the set`() {
        // color-adjust / image-rendering-quality / border-boundary are used
        // with bare `inherit` in committed fixtures (fixtures/properties/…)
        // and have NO runtime inheritance channel — the set must exclude
        // them so those fixtures' IR stays byte-identical.
        for (name in listOf("color-adjust", "image-rendering-quality", "border-boundary", "outline-width")) {
            assertFalse(
                InheritedDefaultResolution.isRedundantInherit(name, "inherit"),
                "$name must NOT be dropped"
            )
        }
    }

    // ── Guard 4: UA-styled source tags are exempt (skeptic S3) ───────────
    @Test
    fun `select keeps font-size inherit while p drops it — the UA-origin split`() {
        // THE pinned S3 shape. `<select>` carries a UA `font` declaration
        // (Chromium html.css), so on web the dropped slot would be filled by
        // the UA rule, not by inheritance — css-cascade-4 §7.3 defaulting
        // only applies when NO origin declared a winner, and the UA sheet is
        // an origin. `<p>` has no UA declaration for font-size, so there the
        // drop IS the inherited computed value.
        val kept = parseBase("font-size" to "inherit", sourceTag = "select")
        assertEquals(1, kept.count { it.propertyName == "font-size" }, "select must KEEP the inherit, got $kept")
        val dropped = parseBase("font-size" to "inherit", sourceTag = "p")
        assertTrue(dropped.none { it.propertyName == "font-size" }, "p must drop the inherit, got $dropped")
    }

    @Test
    fun `the UA exemption is whole-component, not per-declaration`() {
        // select-combobox-print declares BOTH `font: inherit` and
        // `color: inherit`; the exemption returns the map untouched so every
        // candidate survives together (no partial drop that would leave the
        // element half UA-styled, half inherited).
        val out = parseBase("font" to "inherit", "color" to "inherit", sourceTag = "select")
        assertEquals(1, out.count { it.propertyName == "color" })
        assertEquals(1, out.count { it.propertyName == "font-size" })
        assertEquals(1, out.count { it.propertyName == "font-family" })
    }

    @Test
    fun `every UA-styled group is covered and generic tags are not`() {
        // The WIDGET_TAGS mirror (runtimes/web/src/renderer/WidgetAttrs.ts)
        // plus the UA font-size/weight/align tags: headings, b/strong, th.
        for (tag in listOf(
            "a", "button", "input", "textarea", "select", "option", "meter", "progress",
            "h1", "h2", "h3", "h4", "h5", "h6", "b", "strong", "th"
        )) {
            assertTrue(InheritedDefaultResolution.isUaStyledTag(tag), "$tag must be UA-styled")
        }
        // Generic flow elements have no UA declaration for any
        // INHERITED_BY_DEFAULT property → the drop identity holds there.
        for (tag in listOf("div", "span", "p", null)) {
            assertFalse(InheritedDefaultResolution.isUaStyledTag(tag), "$tag must NOT be UA-styled")
        }
    }

    @Test
    fun `tag match is trimmed and case-insensitive per HTML §13·2·5`() {
        // HTML tag names are ASCII case-insensitive even though the
        // extractor emits them lowercase — a `_tag: "SELECT"` must not slip
        // past the guard.
        assertTrue(InheritedDefaultResolution.isUaStyledTag("  SELECT "))
        val input = mapOf("color" to "inherit")
        assertSame(input, InheritedDefaultResolution.resolve(input, sourceTag = "Button"))
    }

    // ── End-to-end: base resolves, selector bucket does not ──────────────
    @Test
    fun `cssParsing wires the flag on for base declarations and off for selector buckets`() {
        // One component: base font-size:inherit + :hover font-size:inherit.
        val doc = Json.parseToJsonElement(
            """
            {"components": {"probe": {
                "properties": {"font-size": "inherit", "color": "red"},
                "selectors": [{"selector": ":hover",
                               "properties": {"font-size": "inherit"}}]
            }}}
            """
        ).jsonObject
        val ir = cssParsing(doc)
        val component = ir.components.single()
        // Base bucket: FontSize resolved away, the real Color kept.
        assertTrue(component.properties.none { it.propertyName == "font-size" })
        assertTrue(component.properties.any { it.propertyName == "color" })
        // Selector bucket: the override keyword must SURVIVE untouched.
        val hover = component.selectors.single()
        assertEquals(1, hover.properties.count { it.propertyName == "font-size" })
    }

    @Test
    fun `cssParsing forwards _tag so a select survives and a p is dropped`() {
        // The FULL wire path the defect lived on: the extractor's `_tag`
        // hint → CssComponent.tag → PropertiesParser(sourceTag) → guard 4.
        // Shape mirrors wpt printing/select-combobox-print (select) and
        // CSS2/cascade/inherit-computed-001 (a plain flow element).
        val doc = Json.parseToJsonElement(
            """
            {"components": {
                "widget": {"_tag": "select", "properties": {"font-size": "inherit"}},
                "para":   {"_tag": "p",      "properties": {"font-size": "inherit"}}
            }}
            """
        ).jsonObject
        val ir = cssParsing(doc)
        val widget = ir.components.single { it.name == "widget" }
        val para = ir.components.single { it.name == "para" }
        // UA-styled: the declaration is a real override of html.css and must
        // reach the wire, or web falls back to the UA font.
        assertEquals(1, widget.properties.count { it.propertyName == "font-size" })
        // Not UA-styled: absence IS the parent's computed value on all three
        // runtimes, which is the whole point of the resolution.
        assertTrue(para.properties.none { it.propertyName == "font-size" })
    }
}
