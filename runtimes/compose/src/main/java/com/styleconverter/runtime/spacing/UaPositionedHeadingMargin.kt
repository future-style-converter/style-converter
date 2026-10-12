// spacing — wave 55 (lane L4, unit U1-android): the UA `margin-block` of an
// OUT-OF-FLOW heading, BACKLOG 0(h). Twin of SwiftUI
// StyleEngine/spacing/UAPositionedHeadingMargin.swift.
//
// ## The defect this closes (measured on wave54-final)
// css-text-decor/text-decoration-inset-005/-006/-011/-014 each hold a
// `position: absolute` <h1> with no author margin inside a relative <div>.
// Chromium's html.css gives it `margin-block: .67em` of its own 2em face
// (HTML §15.3 "Sections and headings"; css-values-4 §6.1.1): 21.44px, or
// 17.42px for 014's 26px monospace face. Both natives painted the heading
// ~21px high, because a NESTED out-of-flow box takes none of the three UA
// margin routes: the §8.3.1 child plan (ComponentRenderer
// blockCollapsePlanFor → uaChildBlockEdges) bails the whole container on an
// out-of-flow child, and the harness root stack / wave-52 T6 rule serve
// composed ROOTS only.
//
// ## Why a cascade step is enough
// CSS 2.1 §10.6.4: with `top: auto` the static position is the hypothetical
// box's top MARGIN edge, so the margin offsets the border box; §8.3.1:
// margins of absolutely positioned boxes never collapse. The renderer already
// paints an abspos box's DECLARED margin (inset-001's extractor-baked 21.44px
// lands within 2px of the ref), so the UA value only has to reach the
// element's merged list as two ordinary longhands in the T6 leaf shape
// `{"px": m}`. Margins are not inherited, so nothing leaks to children.
//
// ## The em base: the UA-CASCADE face, not the painted one
// m = uaBlockMarginEm(tag) × F, where F for an author-unsized heading is the
// UA sheet's computed size (UAElementFontRule.sizeMultiplier × emBasePx of
// the PRE-font-step list) — independent of the face gate, so the margin does
// not move when the held fold-aware face lands or reverts.
//
// ## Scope, stated rather than discovered
// Capture-gated (`enabled` = LocalWptCaptureMode, the uaChildBlockEdges
// contract): every product render and the 327-pair corpus are identity.
// Headings only this wave; an out-of-flow p / ul / ol / blockquote / pre /
// figure, a vertical writing mode and an author size this step cannot
// resolve are each logged once and left alone (no silent fallthrough).
package com.styleconverter.runtime.spacing

// The coverage tracker every runtime breadcrumb writes to.
import com.styleconverter.runtime.PropertyTracker
// The typed IR property (type name + raw JSON payload) this step appends.
import com.styleconverter.runtime.core.ir.IRProperty
// The renderer's own out-of-flow predicate, so this step fires exactly
// where RenderAbsoluteChild mounts the box (css-position-3 §2.1).
import com.styleconverter.runtime.core.renderer.ComponentRenderer
// Keyword read for the bare-string WritingMode payload.
import com.styleconverter.runtime.core.types.ValueExtractors
// The UA font step's own table and em base: one owner of the heading face.
import com.styleconverter.runtime.typography.UAElementFontRule
// JSON builders for the `{"px": m}` leaf.
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
// The thread-safe dedupe set behind the once-per-key breadcrumbs.
import java.util.concurrent.ConcurrentHashMap

/** The UA block margin of an out-of-flow heading as a cascade step at the
 *  merge seam, after the UA font step. Pure Kotlin (no Compose) so the JVM
 *  suite pins it on verbatim wire. */
object UaPositionedHeadingMargin {

    /** The six sectioning headings: this wave's scope (R9 queues the rest). */
    private val HEADINGS: Set<String> = setOf("h1", "h2", "h3", "h4", "h5", "h6")

    /** css-writing-modes-4 §3.1 vertical modes, where `margin-block` maps to
     *  left / right: the AbsposStaticPosition set, normalised the same way. */
    private val VERTICAL_MODES: Set<String> =
        setOf("VERTICAL_RL", "VERTICAL_LR", "SIDEWAYS_RL", "SIDEWAYS_LR")

    /** Two inherited bases for the author-sized rung: a declared size that
     *  resolves differently over them is relative to the parent (em / % /
     *  larger / smaller), whose px this step does not hold. */
    private const val PROBE_BASE_A = 16f
    private const val PROBE_BASE_B = 32f

    /** Breadcrumb keys already reported this process. */
    private val logged: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Append the UA block margin of an out-of-flow heading to its merged list.
     *
     * @param sourceTag the element's `_tag` (`meta.sourceTag`).
     * @param own the element's OWN post-reset declarations: its Position and
     *   any author margin edge are read here.
     * @param preUa the merged list BEFORE the UA font step (the seam's
     *   ListStyleUaRule output): the em base of the UA face.
     * @param merged the list after the UA font step, the one this step extends.
     * @param enabled the ambient WPT-capture flag; false is the identity.
     * @return [merged] itself when nothing fires, else a copy with the UA
     *   MarginTop / MarginBottom on each edge the author left undeclared.
     */
    fun apply(
        sourceTag: String?,
        own: List<IRProperty>,
        preUa: List<IRProperty>,
        merged: List<IRProperty>,
        enabled: Boolean,
    ): List<IRProperty> {
        // Outside WPT capture no UA margin is emulated anywhere.
        if (!enabled) return merged
        // The tag's UA block-margin factor; null for div / span / … (fast path).
        val em = UaBlockMarginFontBasis.uaBlockMarginEm(sourceTag) ?: return merged
        // In-flow boxes take their UA margin from the §8.3.1 plan, not here.
        if (!ComponentRenderer.isOutOfFlowChild(own)) return merged
        // The element's own property types, for the per-edge author test.
        val types = own.map { it.type }
        // Author beats UA per edge (css-cascade-4 §6.1): uaChildBlockEdges' sets.
        val top = !declaresUaChildTop(types)
        // The block-end edge cascades independently of the block-start one.
        val bottom = !declaresUaChildBottom(types)
        // Both edges declared (the baked / declared abspos <h1>s, T6's root).
        if (!top && !bottom) return merged
        // The tag, normalised the way the UA tables compare it.
        val tag = sourceTag?.lowercase()
        // A non-heading UA-margin tag: queued (R9), so say so and leave it.
        if (tag !in HEADINGS) return skip("tag:$tag", "out-of-flow <$tag>: UA margin-block not emitted (headings only)", merged)
        // A vertical writing mode would put margin-block on the inline axis.
        if (isVertical(preUa)) return skip("vertical", "out-of-flow <$tag> in a vertical writing mode: UA margin-block not emitted", merged)
        // The UA-cascade face; null = an author size this step cannot resolve.
        val face = uaCascadeFacePx(tag, own, preUa)
            ?: return skip("size:$tag", "out-of-flow <$tag> with an unresolvable author font-size: UA margin-block not emitted", merged)
        // The UA margin: the tag's em factor over its own computed face.
        val leaf = JsonObject(mapOf("px" to JsonPrimitive(em * face)))
        // Only the undeclared edges take the UA value; the author keeps the rest.
        val added = listOfNotNull(
            // Block-start: the edge that offsets the static position (§10.6.4).
            IRProperty("MarginTop", leaf).takeIf { top },
            // Block-end: painted as the box's own margin, nothing collapses.
            IRProperty("MarginBottom", leaf).takeIf { bottom },
        )
        // A new list; [merged] itself is never mutated.
        return merged + added
    }

    /**
     * The element's computed font-size under the UA cascade, in px: for an
     * author-unsized heading the UA face (sizeMultiplier × the UA font step's
     * own em base over [preUa]), else the author's declared size when it is
     * absolute (UaBlockMarginFontBasis rungs B3 / B4). Null for the unexpanded
     * `Font` shorthand, var() / calc(), and any size relative to the parent.
     */
    internal fun uaCascadeFacePx(tag: String?, own: List<IRProperty>, preUa: List<IRProperty>): Float? {
        // No own size declaration: the UA font-size is the computed one.
        if (own.none { it.type in UAElementFontRule.SIZE_DECLARING }) {
            // The tag's UA multiplier (every heading has one).
            val multiplier = UAElementFontRule.sizeMultiplier(tag) ?: return null
            // × the em base the UA font step itself resolves (inherited px, the
            // monospace 13px default, else 16): 32 for inset-005, 26 for -014.
            return (multiplier * UAElementFontRule.emBasePx(preUa)).toFloat()
        }
        // Only the shorthand declared: its size component is not decoded here.
        if (own.none { it.type == "FontSize" }) return null
        // The declared size over one inherited base (null = var / calc / …).
        val a = UaBlockMarginFontBasis.ownFontSizePx(own, PROBE_BASE_A, tag) ?: return null
        // The same size over another base; equal only for an absolute size.
        val b = UaBlockMarginFontBasis.ownFontSizePx(own, PROBE_BASE_B, tag) ?: return null
        // Parent-relative sizes need the parent's px, which mergeInherited
        // dropped for the own declaration: unresolvable at this step.
        return a.takeIf { a == b }
    }

    /** Is the element in a vertical writing mode? (WritingMode inherits, so
     *  the merged list carries the ancestor's value.) */
    private fun isVertical(preUa: List<IRProperty>): Boolean {
        // The first WritingMode entry: own wins over inherited after the merge.
        val mode = ValueExtractors.extractKeyword(preUa.firstOrNull { it.type == "WritingMode" }?.data)
        // Normalised like AbsposStaticPosition (uppercase, '-' → '_').
        return mode?.uppercase()?.replace('-', '_') in VERTICAL_MODES
    }

    /** Leave [merged] untouched, reporting [message] under [key] once. */
    private fun skip(key: String, message: String, merged: List<IRProperty>): List<IRProperty> {
        // First sighting only: one breadcrumb per reason per process.
        if (logged.add(key)) {
            // android.util.Log is a stub on the JVM test classpath, hence runCatching.
            runCatching { PropertyTracker.logUnhandled("MarginBlock", "UaPositionedHeadingMargin: $message") }
        }
        // The identity: the SAME instance every downstream remember key reads.
        return merged
    }

    /** Test hook: was [key] reported? (Pins the logged-once branches.) */
    internal fun hasLogged(key: String): Boolean = key in logged
}
