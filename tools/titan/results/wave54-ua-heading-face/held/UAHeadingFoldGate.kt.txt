// typography — wave 54 (lane L5, unit U1-android): WHEN the UA heading face
// may apply to a heading that hosts child boxes. Twin of SwiftUI
// StyleEngine/typography/UAHeadingFoldGate.swift.
//
// ## Why a gate at all (the wave-40 measurement)
// iOS's UAElementFontRule.headingAppliesTo stood the heading half down for
// EVERY heading with element children: in wave 40 such a heading painted as a
// vertical STACK of labels, and a 2em face only magnified the stack
// (inset-011 0.6999 → 0.6290, inset-014 0.9317 → 0.9150, both WORSE).
//
// ## Why the gate is now "the fold does not engage"
// Since the wave-44/45 inline-run fold, a heading whose runs fold paints as
// ONE paragraph — the shape the 2em face is right for. On wave53-final both
// natives paint inset-005/-006/-014 as one folded line (sup raised, sub
// lowered) at the 16px face, where the ref wraps a 2em bold paragraph
// (ua-heading-face.md §2). So the heading half stands down only for a
// heading that has children AND whose content would still paint stacked.
//
// ## Why evaluating the fold on the PRE-UA list is sound (brief §4 D)
// Fold admission never reads the host's FontSize or FontWeight: it reads
// TextTransform, the writing mode, Hyphens, FontVariantCaps and (through
// InlineSpanRing.admit) Color. So the verdict on the pre-UA merged list
// equals the render-time verdict on the post-UA list — pinned by
// UAHeadingFoldGateTest's invariance case on the four verbatim hosts.
//
// ## What it mirrors, and what it does not (stated, not hidden)
// It mirrors ComponentRenderer.RenderContent's `inlineRunPlan` resolution
// (runs present, host not `position: relative`) and then the fold itself.
// It does NOT mirror the later branch choice (a transform containing block
// with an out-of-flow child, flex/grid hosts, the WPT float/atom packers):
// float members, widget atoms and display overrides are refused by the
// fold's own member ring anyway, and on wave53-final every heading host
// with children (inset-005/-006/-011/-014) is a plain abspos block, so no
// corpus heading reaches an unmirrored branch. The fold here also reads the
// PRE-resolution list: a host whose admission hinges on a var()/calc()
// Color could disagree with the render-time fold (0 corpus hosts).
//
// ## Not a silent fallthrough
// A stand-down IS the fold's refusal of the same host, which RenderContent
// already logs once per host ("meta.runs fold … bail"); the gate re-runs the
// pure fold rather than logging a second, duplicate breadcrumb.
package com.styleconverter.runtime.typography

// The decoded component (tag, runs, children, lang) the gate classifies.
import com.styleconverter.runtime.core.ir.IRComponent
// The typed property the host's merged list is made of.
import com.styleconverter.runtime.core.ir.IRProperty
// The production run-plan resolver the renderer hands the fold.
import com.styleconverter.runtime.core.renderer.InlineRunPlan
// Keyword read for the Position payload (bare "RELATIVE" string on the wire).
import com.styleconverter.runtime.core.types.ValueExtractors
// The fold whose verdict decides the gate — called, never edited.
import com.styleconverter.runtime.typography.inline.InlineRunFold

/**
 * UAHeadingFoldGate — does the UA heading face stand down for [IRComponent]?
 *
 * Pure Kotlin (no Compose) so the JVM suite pins it on verbatim wire.
 */
object UAHeadingFoldGate {

    /**
     * The verdict handed to [UAElementFontRule.apply]'s `standsDown`.
     *
     * @param component the element being rendered (its `_tag`, `runs`,
     *   composed `children` and `lang` are read).
     * @param mergedPreUa the inheritance-merged list BEFORE the UA font
     *   step (the seam's ListStyleUaRule output) — the fold's host list.
     * @return false for a leaf (the face applies, as on iOS since wave 40);
     *   true for a non-heading with children (the old flag value — the rule
     *   reads it for headings only, so this is never consulted); for a
     *   heading with children, true exactly when its runs do NOT fold.
     */
    fun standsDown(component: IRComponent, mergedPreUa: List<IRProperty>): Boolean {
        // The element's composed children (null / empty = a leaf).
        val children = component.children
        // A leaf heading takes the face: the wave-40 BETTER case.
        if (children.isNullOrEmpty()) return false
        // Not a heading: no heading half to gate — keep the old answer and
        // skip the fold, so no non-heading container ever pays for it.
        if (!UAElementFontRule.isBold(component._tag)) return true
        // No runs: the children paint as a block stack — stand down.
        val runs = component.runs
        if (runs.isNullOrEmpty()) return true
        // A relative host renders through the positioned-container branch,
        // which never consumes a run plan (RenderContent's first suppression
        // rule, read here the way its private extractPositionType does:
        // first Position entry, keyword compare).
        val position = mergedPreUa.firstOrNull { it.type == "Position" }
        if (ValueExtractors.extractKeyword(position?.data)?.uppercase() == "RELATIVE") return true
        // The production plan; null (unresolvable runs) paints stacked too.
        val plan = InlineRunPlan.resolve(runs, children) ?: return true
        // The fold, with the renderer's arguments: the host's own `lang`
        // is exactly what RenderComponent provides as LocalContentLanguage
        // for its own content (provided verbatim, never ?:-chained).
        val outcome = InlineRunFold.fold(
            entries = plan.entries,
            children = children,
            hostProperties = mergedPreUa,
            hostEffectiveLang = component.lang,
        )
        // Folded → one paragraph → the face applies; any bail → stacked.
        return outcome !is InlineRunFold.Outcome.Folded
    }
}
