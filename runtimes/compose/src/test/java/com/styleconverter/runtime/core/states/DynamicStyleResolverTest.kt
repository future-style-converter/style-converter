package com.styleconverter.runtime.core.states

// Wave-7 pinning tests for the dynamic-styling runtime v1 resolution step
// (schema/spec/06-dynamic-styling.md §2/§3/§6) — the StateHandler /
// DynamicStyleResolver wiring the Android lane owns:
//   - runtime-v1 activation: pressed → active, hover is pointer-only (a
//     press NEVER activates hover — the touch no-op pin), reserved and
//     unknown conditions are inert,
//   - §3 layering: base → active media buckets (array order) → active
//     selector buckets (array order), whole-value replace per type, last
//     writer wins — state beats a width-bucket recolor,
//   - §6 forced states: forcing a condition resolves BYTE-IDENTICALLY to
//     the real input state (the capture-hook guarantee).

import com.styleconverter.runtime.core.ir.IRMedia
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.ir.IRSelector
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicStyleResolverTest {

    // Shorthand: build an IRProperty from a type + raw JSON data string.
    private fun prop(t: String, j: String) = IRProperty(t, Json.parseToJsonElement(j))

    // The base list used across layering tests — mirrors the golden's
    // DynamicLayering component (Width + BackgroundColor + Color).
    private val base = listOf(
        prop("Width", """{"type":"length","px":180.0}"""),
        prop("BackgroundColor", """{"srgb":{"r":0.92,"g":0.94,"b":0.94},"original":"#ecf0f1"}"""),
        prop("Color", """{"srgb":{"r":0.07,"g":0.07,"b":0.07},"original":"#111111"}""")
    )

    // Idle / pressed / hovered / focused interaction snapshots.
    private val idle = StateHandler.InteractionState.IDLE
    private val pressed = idle.copy(isPressed = true)
    private val hovered = idle.copy(isHovered = true)
    private val focused = idle.copy(isFocused = true)

    // ── §2 activation ────────────────────────────────────────────────────

    @Test
    fun `pressed state activates the active bucket and overrides the base color`() {
        val sel = listOf(IRSelector("active", listOf(
            prop("BackgroundColor", """{"srgb":{"r":0.91,"g":0.30,"b":0.24},"original":"#e74c3c"}""")
        )))
        val out = DynamicStyleResolver.resolve(
            base,
            emptyList(),
            DynamicStyleResolver.activeSelectorBuckets(sel, pressed, emptySet())
        )
        // The pressed bucket's BackgroundColor replaces the base one whole.
        val bg = out.first { it.type == "BackgroundColor" }
        assertTrue(bg.data.toString().contains("#e74c3c"))
        // Untouched types survive.
        assertTrue(out.any { it.type == "Width" })
        assertTrue(out.any { it.type == "Color" })
    }

    @Test
    fun `a press never activates the hover bucket - the touch no-op pin`() {
        // spec 06 §2: on touch surfaces hover MUST NOT be emulated by
        // latching on tap. A press flips isPressed only; hover stays false.
        val sel = listOf(IRSelector("hover", listOf(
            prop("BackgroundColor", """{"srgb":{"r":0.75,"g":0.22,"b":0.17},"original":"#c0392b"}""")
        )))
        val active = DynamicStyleResolver.activeSelectorBuckets(sel, pressed, emptySet())
        assertTrue(active.isEmpty())
    }

    @Test
    fun `hover activates from a real pointer hover state`() {
        val sel = listOf(IRSelector("hover", listOf(prop("Opacity", "0.5"))))
        val active = DynamicStyleResolver.activeSelectorBuckets(sel, hovered, emptySet())
        assertEquals(1, active.size)
    }

    @Test
    fun `focus activates the focus bucket only`() {
        val sel = listOf(
            IRSelector("hover", listOf(prop("Opacity", "0.1"))),
            IRSelector("focus", listOf(prop("Opacity", "0.9")))
        )
        val active = DynamicStyleResolver.activeSelectorBuckets(sel, focused, emptySet())
        assertEquals(1, active.size)
        assertEquals("focus", active[0].condition)
    }

    @Test
    fun `disabled activates from the host-supplied enabled=false flag`() {
        val sel = listOf(IRSelector("disabled", listOf(prop("Opacity", "0.5"))))
        val disabledState = idle.copy(isEnabled = false)
        assertEquals(1, DynamicStyleResolver.activeSelectorBuckets(sel, disabledState, emptySet()).size)
        // …and NOT from the default interactive state.
        assertTrue(DynamicStyleResolver.activeSelectorBuckets(sel, idle, emptySet()).isEmpty())
    }

    @Test
    fun `reserved and unknown conditions are inert at runtime v1`() {
        // focus-visible / focus-within are reserved (NOT approximated by
        // focus); structural pseudo-classes are unknown — all inert.
        val sel = listOf(
            IRSelector("focus-visible", listOf(prop("Opacity", "0.1"))),
            IRSelector("focus-within", listOf(prop("Opacity", "0.2"))),
            IRSelector("enabled", listOf(prop("Opacity", "0.3"))),
            IRSelector("nth-child(2)", listOf(prop("Opacity", "0.4")))
        )
        // Even with EVERY real state on, none of these may activate.
        val allOn = StateHandler.InteractionState(
            isHovered = true, isPressed = true, isFocused = true,
            isEnabled = true, isChecked = true
        )
        assertTrue(DynamicStyleResolver.activeSelectorBuckets(sel, allOn, emptySet()).isEmpty())
    }

    @Test
    fun `pseudo-element conditions are skipped silently - ContentApplier channel`() {
        val sel = listOf(
            IRSelector("::before", listOf(prop("Content", """{"value":"x"}"""))),
            IRSelector(":after", listOf(prop("Content", """{"value":"y"}""")))
        )
        // Never activated by interaction state or forced sets…
        val active = DynamicStyleResolver.activeSelectorBuckets(
            sel, pressed, setOf("hover", "active", "focus", "disabled", "checked")
        )
        assertTrue(active.isEmpty())
        // …and classified as pseudo-elements, not unknown conditions.
        assertTrue(DynamicStyleResolver.isPseudoElement("::before"))
        assertTrue(DynamicStyleResolver.isPseudoElement(":after"))
        assertFalse(DynamicStyleResolver.isPseudoElement("hover"))
    }

    // ── §6 forced states ─────────────────────────────────────────────────

    @Test
    fun `forced active resolves byte-identically to a real press`() {
        val sel = listOf(IRSelector("active", listOf(
            prop("BackgroundColor", """{"srgb":{"r":0.95,"g":0.61,"b":0.07},"original":"#f39c12"}""")
        )))
        val real = DynamicStyleResolver.resolve(
            base, emptyList(),
            DynamicStyleResolver.activeSelectorBuckets(sel, pressed, emptySet())
        )
        val forced = DynamicStyleResolver.resolve(
            base, emptyList(),
            DynamicStyleResolver.activeSelectorBuckets(sel, idle, setOf("active"))
        )
        // The §6 guarantee: forcing {"active"} produces exactly the styles
        // a real press produces.
        assertEquals(real, forced)
    }

    @Test
    fun `forced states work without any interaction state - null state`() {
        // Components without hover/active/focus buckets never build an
        // InteractionSource; forced disabled/checked must still resolve.
        val sel = listOf(
            IRSelector("disabled", listOf(prop("Opacity", "0.5"))),
            IRSelector("checked", listOf(prop("Opacity", "0.9")))
        )
        val forcedDisabled = DynamicStyleResolver.activeSelectorBuckets(sel, null, setOf("disabled"))
        assertEquals(listOf("disabled"), forcedDisabled.map { it.condition })
        val forcedChecked = DynamicStyleResolver.activeSelectorBuckets(sel, null, setOf("checked"))
        assertEquals(listOf("checked"), forcedChecked.map { it.condition })
        // No forcing, no state → nothing active.
        assertTrue(DynamicStyleResolver.activeSelectorBuckets(sel, null, emptySet()).isEmpty())
    }

    // ── §3 layering ──────────────────────────────────────────────────────

    @Test
    fun `selector bucket beats media bucket for the same property type`() {
        // The wire-decision pin: state must beat width-bucket recolors.
        val media = listOf(IRMedia("(min-width: 200px)", listOf(
            prop("BackgroundColor", """{"srgb":{"r":0.17,"g":0.24,"b":0.31},"original":"#2c3e50"}""")
        )))
        val sel = listOf(IRSelector("active", listOf(
            prop("BackgroundColor", """{"srgb":{"r":0.91,"g":0.30,"b":0.24},"original":"#e74c3c"}""")
        )))
        val out = DynamicStyleResolver.resolve(
            base,
            media, // caller already evaluated it active
            DynamicStyleResolver.activeSelectorBuckets(sel, pressed, emptySet())
        )
        // Selector layer folds AFTER media → its value wins.
        assertTrue(out.first { it.type == "BackgroundColor" }.data.toString().contains("#e74c3c"))
    }

    @Test
    fun `active buckets fold in array order - last writer wins`() {
        // DS_StateStack pin: hover and active BOTH active (forced) → the
        // LATER bucket in selectors[] order wins, never a priority table.
        val sel = listOf(
            IRSelector("hover", listOf(
                prop("BackgroundColor", """{"srgb":{"r":0.20,"g":0.60,"b":0.86},"original":"#3498db"}""")
            )),
            IRSelector("active", listOf(
                prop("BackgroundColor", """{"srgb":{"r":0.91,"g":0.30,"b":0.24},"original":"#e74c3c"}""")
            ))
        )
        val out = DynamicStyleResolver.resolve(
            base, emptyList(),
            DynamicStyleResolver.activeSelectorBuckets(sel, idle, setOf("hover", "active"))
        )
        assertTrue(out.first { it.type == "BackgroundColor" }.data.toString().contains("#e74c3c"))

        // MW_Layered pin (media side): two active media buckets — the later
        // one in media[] order supplies the final value.
        val media = listOf(
            IRMedia("(min-width: 200px)", listOf(
                prop("BackgroundColor", """{"srgb":{"r":0.17,"g":0.24,"b":0.31},"original":"#2c3e50"}""")
            )),
            IRMedia("(max-width: 500px)", listOf(
                prop("BackgroundColor", """{"srgb":{"r":0.90,"g":0.49,"b":0.13},"original":"#e67e22"}""")
            ))
        )
        val mediaOut = DynamicStyleResolver.resolve(base, media, emptyList())
        assertTrue(mediaOut.first { it.type == "BackgroundColor" }.data.toString().contains("#e67e22"))
    }

    @Test
    fun `overlay replaces whole values in place and appends new types`() {
        val bucket = listOf(
            // Same type → replace IN PLACE (position preserved).
            prop("BackgroundColor", """{"srgb":{"r":0.0,"g":0.0,"b":0.0},"original":"#000000"}"""),
            // New type → append at the end.
            prop("Opacity", "0.5")
        )
        val out = DynamicStyleResolver.overlay(base, bucket)
        // Order pin: Width, BackgroundColor(replaced), Color, Opacity(appended).
        assertEquals(listOf("Width", "BackgroundColor", "Color", "Opacity"), out.map { it.type })
        // Whole-value replacement: the bucket's data object verbatim, no
        // deep merge with the base data.
        assertEquals(bucket[0].data, out[1].data)
    }

    @Test
    fun `no active buckets returns the base list instance - identity pin`() {
        // The static corpus must keep remember()-key stability: resolution
        // with nothing active is a pass-through, not a copy.
        val out = DynamicStyleResolver.resolve(base, emptyList(), emptyList())
        assertSame(base, out)
        // Same for an overlay with an empty bucket.
        assertSame(base, DynamicStyleResolver.overlay(base, emptyList()))
    }

    // ── efficiency gate ──────────────────────────────────────────────────

    @Test
    fun `needsInteractionSource is true only for real-input conditions`() {
        val hover = listOf(IRSelector("hover", emptyList()))
        val active = listOf(IRSelector("active", emptyList()))
        val focus = listOf(IRSelector("focus", emptyList()))
        // hover/active/focus are input-driven → the component pays.
        assertTrue(DynamicStyleResolver.needsInteractionSource(hover))
        assertTrue(DynamicStyleResolver.needsInteractionSource(active))
        assertTrue(DynamicStyleResolver.needsInteractionSource(focus))
        // disabled/checked are host-supplied flags → no input plumbing.
        assertFalse(DynamicStyleResolver.needsInteractionSource(listOf(IRSelector("disabled", emptyList()))))
        assertFalse(DynamicStyleResolver.needsInteractionSource(listOf(IRSelector("checked", emptyList()))))
        // Pseudo-elements ride the ContentApplier channel → no plumbing.
        assertFalse(DynamicStyleResolver.needsInteractionSource(listOf(IRSelector("::before", emptyList()))))
        // Unknown conditions are inert → no plumbing.
        assertFalse(DynamicStyleResolver.needsInteractionSource(listOf(IRSelector("visited", emptyList()))))
    }

    @Test
    fun `colon-prefixed spellings parse like the wire's stripped forms`() {
        // The v2 wire strips the colon ("hover"); legacy/handwritten IR may
        // carry ":hover" — both must resolve identically.
        val stripped = listOf(IRSelector("hover", listOf(prop("Opacity", "0.5"))))
        val prefixed = listOf(IRSelector(":hover", listOf(prop("Opacity", "0.5"))))
        assertEquals(
            DynamicStyleResolver.activeSelectorBuckets(stripped, hovered, emptySet()),
            DynamicStyleResolver.activeSelectorBuckets(prefixed, hovered, emptySet()).map {
                IRSelector("hover", it.properties)
            }
        )
    }
}
