package com.styleconverter.runtime.typography.inline

// Wave 44 (lane U1) — the inline-run FOLD pins, on the REAL WIRE.
//
// The four Folded documents below are tools/titan/runs/wave43-final/
// sections/<section>/per-test-ir/<test>.json VERBATIM (minified only — no
// key or value was altered), i.e. the exact bytes the wave-43 Android
// gate consumed for the measured victims:
//   hyphens-manual-inline-010  android-ref 0.6570  (3 members → 3 LINES)
//   hyphens-manual-inline-011  android-ref 0.8480
//   hyphens-auto-inline-010    android-ref 0.9303
//   CSS2 inherit-computed-001  android-ref 0.8378
// The two Bailed documents are the same-run payloads of hyphens-out-of-
// flow-001 (abspos member — deferred by scope) and text-decoration-
// inset-014 (a <u> member whose UA underline never rides the wire),
// trimmed to their runs host (+ its child) with the host's dangling
// slot removed; every kept byte is verbatim.
//
// Each wire test decodes + composes through the PRODUCTION path
// (IRDocumentDecoder → SlotComposer → InlineRunPlan.resolve), exactly
// like the harness screens, so a fold that works on hand-built entries
// but dies on the wire cannot pass. Synthetic cases then pin each bail
// rule in isolation.

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRDocumentDecoder
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.renderer.InlineRunPlan
import com.styleconverter.runtime.core.renderer.SlotComposer
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineRunFoldTest {

    /** Decode + compose a captured v2 document, return root [index]. */
    private fun root(doc: String, index: Int): IRComponent =
        SlotComposer.compose(IRDocumentDecoder.decode(doc))[index]

    /** Resolve the host's runs plan (production resolver), then fold. */
    private fun foldOf(host: IRComponent): InlineRunFold.Outcome {
        // The production plan the renderer seam hands the fold.
        val plan = InlineRunPlan.resolve(host.runs, host.children)
        assertNotNull("the wire must resolve a runs plan", plan)
        return InlineRunFold.fold(
            entries = plan!!.entries,
            children = host.children!!,
            hostProperties = host.properties,
            // The seam passes LocalContentLanguage.current — the host's
            // own decoded meta.lang is exactly that value here.
            hostEffectiveLang = host.lang,
        )
    }

    // ── the four measured victims, verbatim wire ─────────────────────────

    @Test
    fun `hyphens-manual-inline-010 - three members fold to one paragraph, span hyphens adopted`() {
        // Roots: [0] instruction <p>, [1] the runs host, [2] reference div.
        val outcome = foldOf(root(MANUAL_INLINE_010, 1))
        val folded = outcome as InlineRunFold.Outcome.Folded
        // "DNA " + span text + "." — ONE flowing paragraph, the exact
        // string of the reference div the ref renders identically.
        assertEquals("DNA means Deoxyribonucleic acid.", folded.text)
        // The host declares no Hyphens; the span's MANUAL governs the
        // paragraph and is appended LAST so the own-list-first read wins.
        assertTrue(folded.adoptedHyphens)
        assertEquals("Hyphens", folded.properties.last().type)
        assertEquals(JsonPrimitive("MANUAL"), folded.properties.last().data)
        // Exactly one Hyphens in the merged list (never a duplicate).
        assertEquals(1, folded.properties.count { it.type == "Hyphens" })
        assertEquals(0, folded.droppedEmptyMembers)
    }

    @Test
    fun `hyphens-manual-inline-011 - soft hyphens survive the fold verbatim`() {
        // Roots: [0] the runs host (this capture has no instruction <p>).
        val outcome = foldOf(root(MANUAL_INLINE_011, 0))
        val folded = outcome as InlineRunFold.Outcome.Folded
        // The span's two U+00AD stay in place — rule A / the manual-mode
        // hyphen materialization are downstream pipeline stages, not ours.
        assertEquals("DNA means Deoxy­ribo­nucleic acid.", folded.text)
        assertTrue(folded.adoptedHyphens)
        assertEquals(JsonPrimitive("MANUAL"), folded.properties.last().data)
    }

    @Test
    fun `hyphens-auto-inline-010 - AUTO adoption with a matching language`() {
        val outcome = foldOf(root(AUTO_INLINE_010, 0))
        val folded = outcome as InlineRunFold.Outcome.Folded
        // "There are " + "new engines now" + "." — one paragraph.
        assertEquals("There are new engines now.", folded.text)
        // Span `hyphens: auto` + lang "en" == host lang "en" → adopted;
        // the paragraph can select the English dictionary downstream.
        assertTrue(folded.adoptedHyphens)
        assertEquals(JsonPrimitive("AUTO"), folded.properties.last().data)
    }

    @Test
    fun `inherit-computed-001 - the painted empty em becomes ONE atom marker in the string`() {
        // Wave 45 (lane X2) — the ATOM RING on the verbatim wire: the em's
        // `border: inherit` (wire dialect: four Border*Color "inherit"
        // longhands, style/width dropped by BorderExpander) resolves
        // against the host's `border: medium solid` to a painted ring, so
        // the fold now emits string + one placeholder instead of a drop.
        val outcome = foldOf(root(INHERIT_COMPUTED_001, 0))
        val folded = outcome as InlineRunFold.Outcome.Folded
        // "… size " + <em/> + " and …": the space BEFORE the em survives,
        // the one after it collapses THROUGH the atom marker (css-text-3
        // §4.1.1 crosses element boundaries) — exactly the Chromium ref's
        // "size ▮and" paint order.
        assertEquals(
            "This line is all in one font size \uFFFCand there is no red.",
            folded.text)
        // One atom, bound to the em (host child index 1), no drops left.
        assertEquals(1, folded.atoms.size)
        assertEquals(0, folded.droppedEmptyMembers)
        assertEquals(1, folded.atoms[0].memberIndex)
        // The k-th marker ↔ atoms[k] contract: exactly one marker, at the
        // position the collapse math left it.
        assertEquals(1, folded.text.count { it == InlineAtomRing.MARKER })
        // The decoded ring: every side inherits the host's computed border
        // — solid, `medium` (3px, no declared width), currentColor (the
        // host's BorderColor "medium" mis-expansion is unparseable and the
        // host declares no `color`, so the sentinel defers to the ink).
        val spec = folded.atoms[0].spec
        listOf(spec.top, spec.right, spec.bottom, spec.left).forEach { side ->
            assertEquals(3f, side.widthPx)
            assertEquals("SOLID", side.style)
            assertEquals(InlineAtomRing.Paint.CurrentColor, side.paint)
        }
        // GEOMETRY PINNED — the ref's measured ring at the host's resolved
        // 19.2px (`font-size: larger` = 16 × 1.2): border-box 6px wide
        // (3+0+3), 29.23px tall (Inter (1984+494)/2048 × 19.2 + 3 + 3) —
        // the wave44-final ref rasterises it 6×29 at x=289..294, y=36..64.
        val (ringW, ringH) = InlineAtomRing.ringSizePx(spec, 19.2f)
        assertEquals(6f, ringW, 0.001f)
        assertEquals(29.231f, ringH, 0.01f)
        // No member declared Hyphens → nothing adopted, nothing appended:
        // the paragraph property list is the host's own, unchanged.
        assertEquals(false, folded.adoptedHyphens)
        assertEquals(root(INHERIT_COMPUTED_001, 0).properties.size, folded.properties.size)
    }

    // ── the two deferred shapes, verbatim wire ───────────────────────────

    @Test
    fun `hyphens-out-of-flow-001 - an abspos member bails to the stacked fallback`() {
        val outcome = foldOf(root(OUT_OF_FLOW_001_HOST3, 0))
        // Position is not in the member property sets: out-of-flow members
        // are wave-scope-deferred (they need a static-position mount, not
        // a glyph contribution) — the bail names the offending type.
        assertEquals("member-prop:Position", (outcome as InlineRunFold.Outcome.Bailed).reason)
    }

    @Test
    fun `text-decoration-inset-014 - the trimmed wire (no inherited Color) refuses on decoration-color divergence`() {
        // Wave 47 (lane Z6): <u> now joins the styled tag ring — but this
        // TRIMMED wire carries no host Color (the runtime seam passes the
        // inheritance-MERGED list, where black arrives from the body), so
        // the member's TextDecorationColor cannot be proven equal to the
        // effective ink and the equality gate refuses (span decorations
        // paint in the span's text color — a divergence would be a lie).
        val outcome = foldOf(root(INSET_014_HOST, 0))
        assertEquals(
            "member-prop:TextDecorationColor-divergence",
            (outcome as InlineRunFold.Outcome.Bailed).reason
        )
    }

    @Test
    fun `text-decoration-inset-014 - with the runtime's merged host Color the u member folds with an underline span`() {
        // The same wire, with the inherited Color the seam's merged list
        // carries (body `color: black` → div → h1) appended — the exact
        // shape the fold sees at runtime.
        val host = root(INSET_014_HOST, 0)
        val plan = InlineRunPlan.resolve(host.runs, host.children)
        val outcome = InlineRunFold.fold(
            entries = plan!!.entries,
            children = host.children!!,
            hostProperties = host.properties + IRProperty(
                "Color",
                kotlinx.serialization.json.Json.parseToJsonElement(
                    """{"srgb":{"r":0,"g":0,"b":0},"original":"black"}"""
                )
            ),
            hostEffectiveLang = host.lang,
        )
        val folded = outcome as InlineRunFold.Outcome.Folded
        // "the " + u text + " fox" — one flowing paragraph.
        assertEquals("the ultra-quick brown fox", folded.text)
        // Exactly one span: the <u>'s UA underline over its glyphs
        // (HTML rendering §15.3.3), decoration color proven equal to the
        // effective ink so the span-colored line is exact.
        assertEquals(1, folded.spans.size)
        val span = folded.spans[0]
        assertEquals("ultra-quick brown", folded.text.substring(span.start, span.end))
        assertTrue(span.style.underline)
        // No other attribution rides: ink/size/weight all inherit.
        assertEquals(null, span.style.ink)
        assertEquals(null, span.style.fontSizePx)
        assertEquals(null, span.style.fontWeight)
        // BoxDecorationBreak is inert for a box this ring does not paint
        // and the u has no border — no stated loss to report.
        assertTrue(span.statedLossTypes.isEmpty())
    }

    // ── synthetic pins for each remaining gate rule ──────────────────────

    /** Hand-built text-leaf member with [props] and optional [lang]. */
    private fun member(
        tag: String,
        text: String?,
        props: List<IRProperty> = emptyList(),
        lang: String? = null,
    ) = IRComponent(
        id = "m", name = "m", properties = props,
        _text = text, _tag = tag, lang = lang,
    )

    /** One text run + one child member, the minimal foldable shape. */
    private fun entries(runText: String) = listOf(
        InlineRunPlan.Entry.Text(runText),
        InlineRunPlan.Entry.Child(0),
    )

    @Test
    fun `cross-boundary space collapse - run trailing space eats the member's leading space`() {
        val outcome = InlineRunFold.fold(
            entries = entries("a "),
            children = listOf(member("span", " b")),
            hostProperties = emptyList(),
            hostEffectiveLang = null,
        )
        // "a " + " b" → "a b": one collapsible space survives (§4.1.1).
        assertEquals("a b", (outcome as InlineRunFold.Outcome.Folded).text)
    }

    @Test
    fun `hyphens conflict - a member contradicting the host's declaration bails`() {
        val outcome = InlineRunFold.fold(
            entries = entries("x "),
            children = listOf(member("span", "y", listOf(IRProperty("Hyphens", JsonPrimitive("MANUAL"))))),
            // Host declared NONE: adopting the member's MANUAL would strip
            // soft hyphens the member wants kept (or vice versa).
            hostProperties = listOf(IRProperty("Hyphens", JsonPrimitive("NONE"))),
            hostEffectiveLang = null,
        )
        assertEquals("hyphens-conflict", (outcome as InlineRunFold.Outcome.Bailed).reason)
    }

    @Test
    fun `hyphens conflict - two members disagreeing bail`() {
        val outcome = InlineRunFold.fold(
            entries = listOf(InlineRunPlan.Entry.Child(0), InlineRunPlan.Entry.Child(1)),
            children = listOf(
                member("span", "a", listOf(IRProperty("Hyphens", JsonPrimitive("MANUAL")))),
                member("span", "b", listOf(IRProperty("Hyphens", JsonPrimitive("AUTO"))), lang = null),
            ),
            hostProperties = emptyList(),
            hostEffectiveLang = null,
        )
        // MANUAL then AUTO: no single paragraph mode is faithful to both.
        assertEquals("hyphens-conflict", (outcome as InlineRunFold.Outcome.Bailed).reason)
    }

    @Test
    fun `auto adoption - a member language diverging from the paragraph's bails`() {
        val outcome = InlineRunFold.fold(
            entries = entries("x "),
            children = listOf(
                member("span", "y", listOf(IRProperty("Hyphens", JsonPrimitive("AUTO"))), lang = "fr"),
            ),
            // Paragraph language is English — a French dictionary cannot
            // be selected for the whole paragraph (§6.1 language-appropriate).
            hostProperties = emptyList(),
            hostEffectiveLang = "en",
        )
        assertEquals("hyphens-lang-divergence", (outcome as InlineRunFold.Outcome.Bailed).reason)
    }

    @Test
    fun `host gates - text-transform and vertical writing modes bail`() {
        // text-transform rewrites the merged string downstream.
        val tt = InlineRunFold.fold(
            entries = entries("x "),
            children = listOf(member("span", "y")),
            hostProperties = listOf(IRProperty("TextTransform", JsonPrimitive("UPPERCASE"))),
            hostEffectiveLang = null,
        )
        assertEquals("host-text-transform", (tt as InlineRunFold.Outcome.Bailed).reason)
        // A vertical host renders through the rotated branch — uncalibrated.
        val wm = InlineRunFold.fold(
            entries = entries("x "),
            children = listOf(member("span", "y")),
            hostProperties = listOf(IRProperty("WritingMode", JsonPrimitive("VERTICAL_RL"))),
            hostEffectiveLang = null,
        )
        assertEquals("host-vertical-writing-mode", (wm as InlineRunFold.Outcome.Bailed).reason)
    }

    @Test
    fun `member gates - strut-bearing empty member and tab content bail`() {
        // FontSize on an EMPTY inline still contributes its strut to the
        // line box (CSS 2.1 §10.8) — dropping it would shrink the line.
        val strut = InlineRunFold.fold(
            entries = entries("x "),
            children = listOf(member("em", null, listOf(IRProperty("FontSize", JsonPrimitive(32))))),
            hostProperties = emptyList(),
            hostEffectiveLang = null,
        )
        assertEquals("member-prop:FontSize", (strut as InlineRunFold.Outcome.Bailed).reason)
        // A tab would be rewritten by tab-size expansion downstream.
        val tab = InlineRunFold.fold(
            entries = entries("x\ty "),
            children = listOf(member("span", "z")),
            hostProperties = emptyList(),
            hostEffectiveLang = null,
        )
        assertEquals("contains-tab", (tab as InlineRunFold.Outcome.Bailed).reason)
    }

    // ── wave 45 (lane X2) — the ATOM RING gate, in isolation ─────────────

    /** A JSON border-color payload with a resolved sRGB (the wire shape). */
    private fun srgb(r: Double, g: Double, b: Double) = kotlinx.serialization.json.buildJsonObject {
        put("srgb", kotlinx.serialization.json.buildJsonObject {
            put("r", JsonPrimitive(r)); put("g", JsonPrimitive(g)); put("b", JsonPrimitive(b))
        })
    }

    @Test
    fun `atom ring - a paint-inert empty member still drops (no style anywhere)`() {
        // Color + a REAL border-color but no border-style on member or
        // host: css-backgrounds-3 §3.2 paints nothing → the wave-44 drop
        // path is preserved byte-identically (dropped and REPORTED).
        val outcome = InlineRunFold.fold(
            entries = entries("a "),
            children = listOf(member("em", null, listOf(
                IRProperty("Color", srgb(1.0, 0.0, 0.0)),
                IRProperty("BorderTopColor", srgb(0.0, 0.0, 0.0)),
            ))),
            hostProperties = emptyList(),
            hostEffectiveLang = null,
        )
        val folded = outcome as InlineRunFold.Outcome.Folded
        assertEquals("a", folded.text.trimEnd())
        assertEquals(1, folded.droppedEmptyMembers)
        assertEquals(0, folded.atoms.size)
    }

    @Test
    fun `atom ring - an empty member with its OWN styled border becomes an atom`() {
        // Declared style + width + color on the member itself (no dialect
        // decode involved): a 2px solid red top-only ring.
        val outcome = InlineRunFold.fold(
            entries = entries("a "),
            children = listOf(member("span", null, listOf(
                IRProperty("BorderTopStyle", JsonPrimitive("SOLID")),
                IRProperty("BorderTopWidth", kotlinx.serialization.json.buildJsonObject { put("px", JsonPrimitive(2)) }),
                IRProperty("BorderTopColor", srgb(1.0, 0.0, 0.0)),
            ))),
            hostProperties = emptyList(),
            hostEffectiveLang = null,
        )
        val folded = outcome as InlineRunFold.Outcome.Folded
        // The marker occupies the member's slot; nothing was dropped.
        assertEquals("a \uFFFC", folded.text)
        assertEquals(1, folded.atoms.size)
        assertEquals(0, folded.droppedEmptyMembers)
        val spec = folded.atoms[0].spec
        // The styled side: declared width + concrete declared color.
        assertEquals(2f, spec.top.widthPx)
        assertEquals(InlineAtomRing.Paint.Concrete(1f, 0f, 0f, 1f), spec.top.paint)
        // Unstyled sides compute to width 0 (§3.1) — they never paint.
        assertEquals(0f, spec.right.widthPx)
        assertEquals(0f, spec.bottom.widthPx)
        assertEquals(0f, spec.left.widthPx)
    }

    @Test
    fun `atom ring - border-inherit over an unpainted host stays a drop`() {
        // The dialect decode routes the side to the host — whose computed
        // border is styleless → the inherited side is styleless too
        // (css-cascade-4 §7.3): paint-inert, so the drop path is kept.
        val outcome = InlineRunFold.fold(
            entries = entries("a "),
            children = listOf(member("em", null, listOf(
                IRProperty("BorderTopColor", kotlinx.serialization.json.buildJsonObject { put("original", JsonPrimitive("inherit")) }),
            ))),
            hostProperties = emptyList(),
            hostEffectiveLang = null,
        )
        val folded = outcome as InlineRunFold.Outcome.Folded
        assertEquals(1, folded.droppedEmptyMembers)
        assertEquals(0, folded.atoms.size)
    }

    @Test
    fun `atom ring - a strut-bearing painted empty member still bails`() {
        // FontSize changes the strut AND the ring's content height — the
        // atom admission set excludes it, so the member bails even though
        // its border would paint.
        val outcome = InlineRunFold.fold(
            entries = entries("a "),
            children = listOf(member("em", null, listOf(
                IRProperty("BorderTopStyle", JsonPrimitive("SOLID")),
                IRProperty("FontSize", JsonPrimitive(32)),
            ))),
            hostProperties = emptyList(),
            hostEffectiveLang = null,
        )
        assertEquals("member-prop:FontSize", (outcome as InlineRunFold.Outcome.Bailed).reason)
    }

    @Test
    fun `atom ring - a source U+FFFC bails the fold (marker alias guard)`() {
        // A literal OBJECT REPLACEMENT CHARACTER in source text would be
        // indistinguishable from an atom marker — the fold declines.
        val outcome = InlineRunFold.fold(
            entries = entries("a \uFFFC "),
            children = listOf(member("span", "b")),
            hostProperties = emptyList(),
            hostEffectiveLang = null,
        )
        assertEquals("contains-object-replacement", (outcome as InlineRunFold.Outcome.Bailed).reason)
    }

    @Test
    fun `member gates - a member with text-only nested runs flattens (wave 47)`() {
        // Wave 44 bailed EVERY member with its own runs; the wave-47
        // nested ring flattens the {text, <br>} shapes — a runs list of
        // bare text entries is the degenerate (break-less) case and is
        // exactly the member's own text, so the fold is faithful.
        val nested = member("span", "y").copy(
            runs = listOf(com.styleconverter.runtime.core.ir.IRRun(text = "y")),
        )
        val outcome = InlineRunFold.fold(
            entries = entries("x "),
            children = listOf(nested),
            hostProperties = emptyList(),
            hostEffectiveLang = null,
        )
        assertEquals("x y", (outcome as InlineRunFold.Outcome.Folded).text)
    }

    // ── wave 47 (lane Z6) — the STYLED-SPAN + BR rings ───────────────────

    @Test
    fun `block-ellipsis-004 - brs fold to newlines and the styled span rides as one attributed range`() {
        // Roots: [0] the line-clamp host (brs + the bold-italic-1.5em
        // bordered span whose own runs nest one more br).
        val outcome = foldOf(root(BLOCK_ELLIPSIS_004, 0))
        val folded = outcome as InlineRunFold.Outcome.Folded
        // Four forced-break lines; every collapsible space around a break
        // falls (css-text-3 §4.1.4) — the exact lines Chromium paints.
        assertEquals("Line 1\nLine 2\nLine 3\nLine 4", folded.text)
        // ONE styled member: the span, covering its flattened subtree.
        assertEquals(1, folded.spans.size)
        val span = folded.spans[0]
        assertEquals("Line 3\nLine 4", folded.text.substring(span.start, span.end))
        // The wire's purple / bold / italic / 1.5em, typed.
        val ink = span.style.ink!!
        assertEquals(0.5019608f, ink.r, 1e-4f)
        assertEquals(0f, ink.g, 1e-4f)
        assertEquals(0.5019608f, ink.b, 1e-4f)
        assertEquals(1f, ink.a, 1e-4f)
        assertEquals(700, span.style.fontWeight)
        assertTrue(span.style.italic)
        assertEquals(1.5f, span.style.fontSizeEm!!, 1e-6f)
        assertEquals(null, span.style.fontSizePx)
        // The 2px solid blue border box is the ring's STATED LOSS —
        // reported, so the seam logs it (never silent).
        assertEquals(1, span.statedLossTypes.size)
        assertTrue(span.statedLossTypes[0].startsWith("border-box-ink("))
    }

    @Test
    fun `block-ellipsis-002 - a text-and-br-only host stays stacked (br-stacked-equivalent)`() {
        // The stacked fallback already renders one block per anonymous
        // run — exactly the forced-break line structure — and this test
        // PASSES on both natives today (wave46-final android-ref 0.9884 /
        // ios-ref 0.9813): the ring must not move its calibrated pixels.
        val outcome = foldOf(root(BLOCK_ELLIPSIS_002, 0))
        assertEquals("br-stacked-equivalent", (outcome as InlineRunFold.Outcome.Bailed).reason)
    }

    @Test
    fun `br ring - spaces around a folded break fall on both sides`() {
        val outcome = InlineRunFold.fold(
            entries = listOf(
                InlineRunPlan.Entry.Text("a "),
                InlineRunPlan.Entry.Child(0),   // <br>
                InlineRunPlan.Entry.Text(" b "),
                InlineRunPlan.Entry.Child(1),   // glyph member (arms the ring)
            ),
            children = listOf(member("br", null), member("span", "c")),
            hostProperties = emptyList(),
            hostEffectiveLang = null,
        )
        // "a " loses its line-end space, " b " loses its line-start one
        // (css-text-3 §4.1.4); the member boundary collapse is unchanged.
        assertEquals("a\nb c", (outcome as InlineRunFold.Outcome.Folded).text)
    }

    @Test
    fun `styled ring - a small-caps host refuses spans (case rewrite breaks alignment)`() {
        val outcome = InlineRunFold.fold(
            entries = entries("x "),
            children = listOf(member("span", "y", listOf(
                IRProperty("Color", kotlinx.serialization.json.Json.parseToJsonElement(
                    """{"srgb":{"r":1,"g":0,"b":0}}"""))
            ))),
            hostProperties = listOf(IRProperty("FontVariantCaps", JsonPrimitive("SMALL_CAPS"))),
            hostEffectiveLang = null,
        )
        assertEquals("styled-host-font-variant", (outcome as InlineRunFold.Outcome.Bailed).reason)
    }

    @Test
    fun `nested ring - a nested non-br member keeps the nested-tree wall`() {
        val inner = member("span", "z")
        val nested = member("span", "y z").copy(
            children = listOf(inner),
            runs = listOf(
                com.styleconverter.runtime.core.ir.IRRun(text = "y "),
                com.styleconverter.runtime.core.ir.IRRun(child = "m"),
            ),
        )
        val outcome = InlineRunFold.fold(
            entries = entries("x "),
            children = listOf(nested),
            hostProperties = emptyList(),
            hostEffectiveLang = null,
        )
        // Only {text, <br>} subtrees flatten — a nested glyph member is
        // still the wall wave 44 named, with a finer-grained reason.
        assertEquals("nested-member-tag:span", (outcome as InlineRunFold.Outcome.Bailed).reason)
    }

    @Test
    fun `nested ring - an unclaimed nested child refuses the flatten`() {
        val innerBr = member("br", null)
        val nested = member("span", "y").copy(
            children = listOf(innerBr),
            // The runs list never references the br — flattening would
            // silently vanish it (the outer fold consumes the member).
            runs = listOf(com.styleconverter.runtime.core.ir.IRRun(text = "y")),
        )
        val outcome = InlineRunFold.fold(
            entries = entries("x "),
            children = listOf(nested),
            hostProperties = emptyList(),
            hostEffectiveLang = null,
        )
        assertEquals("nested-unclaimed", (outcome as InlineRunFold.Outcome.Bailed).reason)
    }

    companion object {
        // tools/titan/runs/wave43-final/sections/css-text/per-test-ir/
        // wpt__css-text__hyphens__hyphens-manual-inline-010.json — verbatim.
        private val MANUAL_INLINE_010 = """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-text__hyphens__hyphens-manual-inline-010__0-255","name":"wpt__css-text__hyphens__hyphens-manual-inline-010__0","properties":[],"text":"Test passes if the characters inside of each black-bordered rectangles are laid out identically. Only \"ucleic\" should be outside of the black-bordered rectangles.","meta":{"sourceTag":"p","lang":"en"}},{"id":"wpt__css-text__hyphens__hyphens-manual-inline-010__1-256","name":"wpt__css-text__hyphens__hyphens-manual-inline-010__1","properties":[{"type":"BorderTopWidth","data":{"px":2}},{"type":"BorderRightWidth","data":{"px":2}},{"type":"BorderBottomWidth","data":{"px":2}},{"type":"BorderLeftWidth","data":{"px":2}},{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderRightStyle","data":"SOLID"},{"type":"BorderBottomStyle","data":"SOLID"},{"type":"BorderLeftStyle","data":"SOLID"},{"type":"BorderTopColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderRightColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderBottomColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderLeftColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"FontFamily","data":["monospace"]},{"type":"FontSize","data":{"px":32,"original":{"type":"length","px":32}}},{"type":"MarginBottom","data":{"original":{"v":0.25,"u":"EM"}}},{"type":"Width","data":{"type":"length","original":{"v":10,"u":"CH"}}}],"text":"DNA .","meta":{"role":"ws-after","lang":"en","runs":[{"text":"DNA "},{"child":"hyphens__hyphens-manual-inline-010__1__0"},{"text":"."}]}},{"id":"hyphens__hyphens-manual-inline-010__1__0-257","name":"hyphens__hyphens-manual-inline-010__1__0","properties":[{"type":"Hyphens","data":"MANUAL"}],"slot":{"parent":"wpt__css-text__hyphens__hyphens-manual-inline-010__1-256"},"text":"means Deoxyribonucleic acid","meta":{"sourceTag":"span","lang":"en"}},{"id":"wpt__css-text__hyphens__hyphens-manual-inline-010__2-258","name":"wpt__css-text__hyphens__hyphens-manual-inline-010__2","properties":[{"type":"BorderTopWidth","data":{"px":2}},{"type":"BorderRightWidth","data":{"px":2}},{"type":"BorderBottomWidth","data":{"px":2}},{"type":"BorderLeftWidth","data":{"px":2}},{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderRightStyle","data":"SOLID"},{"type":"BorderBottomStyle","data":"SOLID"},{"type":"BorderLeftStyle","data":"SOLID"},{"type":"BorderTopColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderRightColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderBottomColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderLeftColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"FontFamily","data":["monospace"]},{"type":"FontSize","data":{"px":32,"original":{"type":"length","px":32}}},{"type":"MarginBottom","data":{"original":{"v":0.25,"u":"EM"}}},{"type":"Width","data":{"type":"length","original":{"v":10,"u":"CH"}}},{"type":"Hyphens","data":"NONE"}],"text":"DNA means Deoxyribonucleic acid.","meta":{"lang":"en"}}]}"""

        // …/wpt__css-text__hyphens__hyphens-manual-inline-011.json — verbatim.
        private val MANUAL_INLINE_011 = """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-text__hyphens__hyphens-manual-inline-011__0-259","name":"wpt__css-text__hyphens__hyphens-manual-inline-011__0","properties":[{"type":"BorderTopWidth","data":{"px":2}},{"type":"BorderRightWidth","data":{"px":2}},{"type":"BorderBottomWidth","data":{"px":2}},{"type":"BorderLeftWidth","data":{"px":2}},{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderRightStyle","data":"SOLID"},{"type":"BorderBottomStyle","data":"SOLID"},{"type":"BorderLeftStyle","data":"SOLID"},{"type":"BorderTopColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderRightColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderBottomColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderLeftColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"FontFamily","data":["monospace"]},{"type":"FontSize","data":{"px":32,"original":{"type":"length","px":32}}},{"type":"Width","data":{"type":"length","original":{"v":10,"u":"CH"}}}],"text":"DNA .","meta":{"runs":[{"text":"DNA "},{"child":"hyphens__hyphens-manual-inline-011__0__0"},{"text":"."}]}},{"id":"hyphens__hyphens-manual-inline-011__0__0-260","name":"hyphens__hyphens-manual-inline-011__0__0","properties":[{"type":"Hyphens","data":"MANUAL"}],"slot":{"parent":"wpt__css-text__hyphens__hyphens-manual-inline-011__0-259"},"text":"means Deoxy­ribo­nucleic acid","meta":{"sourceTag":"span"}}]}"""

        // …/wpt__css-text__hyphens__hyphens-auto-inline-010.json — verbatim.
        private val AUTO_INLINE_010 = """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-text__hyphens__hyphens-auto-inline-010__0-240","name":"wpt__css-text__hyphens__hyphens-auto-inline-010__0","properties":[{"type":"BorderTopWidth","data":{"px":2}},{"type":"BorderRightWidth","data":{"px":2}},{"type":"BorderBottomWidth","data":{"px":2}},{"type":"BorderLeftWidth","data":{"px":2}},{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderRightStyle","data":"SOLID"},{"type":"BorderBottomStyle","data":"SOLID"},{"type":"BorderLeftStyle","data":"SOLID"},{"type":"BorderTopColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderRightColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderBottomColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderLeftColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"FontFamily","data":["monospace"]},{"type":"FontSize","data":{"px":32,"original":{"type":"length","px":32}}},{"type":"Width","data":{"type":"length","original":{"v":6,"u":"CH"}}}],"text":"There are .","meta":{"lang":"en","runs":[{"text":"There are "},{"child":"hyphens__hyphens-auto-inline-010__0__0"},{"text":"."}]}},{"id":"hyphens__hyphens-auto-inline-010__0__0-241","name":"hyphens__hyphens-auto-inline-010__0__0","properties":[{"type":"Hyphens","data":"AUTO"}],"slot":{"parent":"wpt__css-text__hyphens__hyphens-auto-inline-010__0-240"},"text":"new engines now","meta":{"sourceTag":"span","lang":"en"}}]}"""

        // tools/titan/runs/wave43-final/sections/CSS2/per-test-ir/
        // wpt__CSS2__cascade__inherit-computed-001.json — verbatim.
        private val INHERIT_COMPUTED_001 = """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css2__cascade__inherit-computed-001__0-100","name":"wpt__CSS2__cascade__inherit-computed-001__0","properties":[{"type":"FontSize","data":{"original":{"type":"relative","keyword":"larger"}}},{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderRightStyle","data":"SOLID"},{"type":"BorderBottomStyle","data":"SOLID"},{"type":"BorderLeftStyle","data":"SOLID"},{"type":"BorderTopColor","data":{"original":"medium"}},{"type":"BorderRightColor","data":{"original":"medium"}},{"type":"BorderBottomColor","data":{"original":"medium"}},{"type":"BorderLeftColor","data":{"original":"medium"}}],"text":"This line is all size and there is no red.","meta":{"sourceTag":"p","runs":[{"text":"This line is all "},{"child":"cascade__inherit-computed-001__0__0"},{"text":" size "},{"child":"cascade__inherit-computed-001__0__1"},{"text":" and there is no red."}]}},{"id":"cascade__inherit-computed-001__0__0-101","name":"cascade__inherit-computed-001__0__0","properties":[],"slot":{"parent":"wpt__css2__cascade__inherit-computed-001__0-100"},"text":"in one font","meta":{"sourceTag":"span","role":"ws-after"}},{"id":"cascade__inherit-computed-001__0__1-102","name":"cascade__inherit-computed-001__0__1","properties":[{"type":"Color","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"BorderTopColor","data":{"original":"inherit"}},{"type":"BorderRightColor","data":{"original":"inherit"}},{"type":"BorderBottomColor","data":{"original":"inherit"}},{"type":"BorderLeftColor","data":{"original":"inherit"}}],"slot":{"parent":"wpt__css2__cascade__inherit-computed-001__0-100"},"meta":{"sourceTag":"em"}}]}"""

        // tools/titan/runs/wave43-final/sections/css-text/per-test-ir/
        // wpt__css-text__hyphens__hyphens-out-of-flow-001.json — host __3 and
        // its abspos child, verbatim (siblings trimmed).
        private val OUT_OF_FLOW_001_HOST3 = """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-text__hyphens__hyphens-out-of-flow-001__3-273","name":"wpt__css-text__hyphens__hyphens-out-of-flow-001__3","properties":[{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderRightStyle","data":"SOLID"},{"type":"BorderBottomStyle","data":"SOLID"},{"type":"BorderLeftStyle","data":"SOLID"},{"type":"BorderTopColor","data":{"srgb":{"r":1,"g":0.6470588235294118,"b":0},"original":"orange"}},{"type":"BorderRightColor","data":{"srgb":{"r":1,"g":0.6470588235294118,"b":0},"original":"orange"}},{"type":"BorderBottomColor","data":{"srgb":{"r":1,"g":0.6470588235294118,"b":0},"original":"orange"}},{"type":"BorderLeftColor","data":{"srgb":{"r":1,"g":0.6470588235294118,"b":0},"original":"orange"}},{"type":"MarginTop","data":{"px":5}},{"type":"MarginRight","data":{"px":5}},{"type":"MarginBottom","data":{"px":5}},{"type":"MarginLeft","data":{"px":5}},{"type":"Width","data":{"type":"length","original":{"v":6,"u":"CH"}}},{"type":"Hyphens","data":"MANUAL"}],"text":"high­way","meta":{"role":"ws-after","runs":[{"text":"h"},{"child":"hyphens__hyphens-out-of-flow-001__3__0"},{"text":"igh­way"}]}},{"id":"hyphens__hyphens-out-of-flow-001__3__0-274","name":"hyphens__hyphens-out-of-flow-001__3__0","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0,"a":0},"original":"transparent"}}],"slot":{"parent":"wpt__css-text__hyphens__hyphens-out-of-flow-001__3-273"},"text":"abspos","meta":{"sourceTag":"span"}}]}"""

        // tools/titan/runs/wave43-final/sections/css-text-decor/per-test-ir/
        // wpt__css-text-decor__text-decoration-inset-014.json — the runs host
        // and its <u> child, verbatim (host's dangling slot removed).
        private val INSET_014_HOST = """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"text-decoration-inset-014__1__0-110","name":"text-decoration-inset-014__1__0","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"FontFamily","data":["monospace"]},{"type":"Width","data":{"type":"length","original":{"v":16,"u":"CH"}}}],"text":"the fox","meta":{"sourceTag":"h1","runs":[{"text":"the "},{"child":"text-decoration-inset-014__1__0__0"},{"text":" fox"}]}},{"id":"text-decoration-inset-014__1__0__0-111","name":"text-decoration-inset-014__1__0__0","properties":[{"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BoxDecorationBreak","data":"CLONE"}],"slot":{"parent":"text-decoration-inset-014__1__0-110"},"text":"ultra-quick brown","meta":{"sourceTag":"u"}}]}"""

        // tools/titan/runs/wave46-final/sections/css-overflow/per-test-ir/
        // wpt__css-overflow__line-clamp__block-ellipsis-004.json — verbatim
        // (wave 47, lane Z6: the styled-span + br ring's measured victim).
        private val BLOCK_ELLIPSIS_004 = """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-overflow__line-clamp__block-ellipsis-004__0-061","name":"wpt__css-overflow__line-clamp__block-ellipsis-004__0","properties":[{"type":"LineClamp","data":{"type":"lines","count":3}},{"type":"Color","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0.5019607843137255},"original":"teal"}}],"text":"Line 1 Line 2","meta":{"runs":[{"text":"Line 1"},{"child":"line-clamp__block-ellipsis-004__0__0"},{"text":" Line 2"},{"child":"line-clamp__block-ellipsis-004__0__1"},{"text":" "},{"child":"line-clamp__block-ellipsis-004__0__2"}]}},{"id":"line-clamp__block-ellipsis-004__0__0-062","name":"line-clamp__block-ellipsis-004__0__0","properties":[{"type":"Width","data":{"type":"length","px":0}},{"type":"Height","data":{"type":"length","px":0}}],"slot":{"parent":"wpt__css-overflow__line-clamp__block-ellipsis-004__0-061"},"meta":{"sourceTag":"br","role":"line-break"}},{"id":"line-clamp__block-ellipsis-004__0__1-063","name":"line-clamp__block-ellipsis-004__0__1","properties":[{"type":"Width","data":{"type":"length","px":0}},{"type":"Height","data":{"type":"length","px":20}}],"slot":{"parent":"wpt__css-overflow__line-clamp__block-ellipsis-004__0-061"},"meta":{"sourceTag":"br","role":"line-break"}},{"id":"line-clamp__block-ellipsis-004__0__2-064","name":"line-clamp__block-ellipsis-004__0__2","properties":[{"type":"Color","data":{"srgb":{"r":0.5019607843137255,"g":0,"b":0.5019607843137255},"original":"purple"}},{"type":"FontWeight","data":{"weight":700,"original":"bold"}},{"type":"FontStyle","data":"italic"},{"type":"FontSize","data":{"original":{"type":"length","original":{"v":1.5,"u":"EM"}}}},{"type":"BorderTopWidth","data":{"px":2}},{"type":"BorderRightWidth","data":{"px":2}},{"type":"BorderBottomWidth","data":{"px":2}},{"type":"BorderLeftWidth","data":{"px":2}},{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderRightStyle","data":"SOLID"},{"type":"BorderBottomStyle","data":"SOLID"},{"type":"BorderLeftStyle","data":"SOLID"},{"type":"BorderTopColor","data":{"srgb":{"r":0,"g":0,"b":1},"original":"blue"}},{"type":"BorderRightColor","data":{"srgb":{"r":0,"g":0,"b":1},"original":"blue"}},{"type":"BorderBottomColor","data":{"srgb":{"r":0,"g":0,"b":1},"original":"blue"}},{"type":"BorderLeftColor","data":{"srgb":{"r":0,"g":0,"b":1},"original":"blue"}}],"slot":{"parent":"wpt__css-overflow__line-clamp__block-ellipsis-004__0-061"},"text":"Line 3 Line 4","meta":{"sourceTag":"span","runs":[{"text":"Line 3"},{"child":"line-clamp__block-ellipsis-004__0__2__0"},{"text":" Line 4"}]}},{"id":"line-clamp__block-ellipsis-004__0__2__0-065","name":"line-clamp__block-ellipsis-004__0__2__0","properties":[{"type":"Width","data":{"type":"length","px":0}},{"type":"Height","data":{"type":"length","px":0}}],"slot":{"parent":"line-clamp__block-ellipsis-004__0__2-064"},"meta":{"sourceTag":"br","role":"line-break"}}]}"""

        // …/wpt__css-overflow__line-clamp__block-ellipsis-002.json — verbatim
        // (wave 47, lane Z6: the PASSING text-and-br-only shape the
        // br-stacked-equivalent rule protects).
        private val BLOCK_ELLIPSIS_002 = """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-overflow__line-clamp__block-ellipsis-002__0-054","name":"wpt__css-overflow__line-clamp__block-ellipsis-002__0","properties":[{"type":"LineClamp","data":{"type":"lines","count":3}},{"type":"Color","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0.5019607843137255},"original":"teal"}},{"type":"FontWeight","data":{"weight":700,"original":"bold"}},{"type":"FontStyle","data":"italic"}],"text":"Line 1 Line 2 Line 3 Line 4","meta":{"runs":[{"text":"Line 1"},{"child":"line-clamp__block-ellipsis-002__0__0"},{"text":" Line 2"},{"child":"line-clamp__block-ellipsis-002__0__1"},{"text":" Line 3"},{"child":"line-clamp__block-ellipsis-002__0__2"},{"text":" Line 4"}]}},{"id":"line-clamp__block-ellipsis-002__0__0-055","name":"line-clamp__block-ellipsis-002__0__0","properties":[{"type":"Width","data":{"type":"length","px":0}},{"type":"Height","data":{"type":"length","px":0}}],"slot":{"parent":"wpt__css-overflow__line-clamp__block-ellipsis-002__0-054"},"meta":{"sourceTag":"br","role":"line-break"}},{"id":"line-clamp__block-ellipsis-002__0__1-056","name":"line-clamp__block-ellipsis-002__0__1","properties":[{"type":"Width","data":{"type":"length","px":0}},{"type":"Height","data":{"type":"length","px":20}}],"slot":{"parent":"wpt__css-overflow__line-clamp__block-ellipsis-002__0-054"},"meta":{"sourceTag":"br","role":"line-break"}},{"id":"line-clamp__block-ellipsis-002__0__2-057","name":"line-clamp__block-ellipsis-002__0__2","properties":[{"type":"Width","data":{"type":"length","px":0}},{"type":"Height","data":{"type":"length","px":20}}],"slot":{"parent":"wpt__css-overflow__line-clamp__block-ellipsis-002__0-054"},"meta":{"sourceTag":"br","role":"line-break"}}]}"""
    }
}
