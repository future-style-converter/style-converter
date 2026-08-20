// typography/inline — wave 44 (lane U1): the first breach in the
// inline-run wall. InlineRunPlan's own banner concedes that `meta.runs`
// buys Compose "correct ORDER, not correct inline layout": every run
// member stacked as its own block box. Measured victims (wave43-final,
// android-ref SSIM): css-text/hyphens-manual-inline-010 0.6570 (three
// members "DNA " / span / "." painted as three LINES against Chromium's
// one flowing paragraph), -011 0.8480, -012 0.9107, hyphens-auto-inline-010
// 0.9303, CSS2/cascade/inherit-computed-001 0.8378.
package com.styleconverter.runtime.typography.inline

// The typed IR nodes the fold classifies (pure data, no Compose).
import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
// The resolved run plan this fold consumes — resolution rules (dangling
// keys, duplicates, rule 4 leftovers) stay in InlineRunPlan; this module
// only decides whether the RESOLVED entries collapse into one paragraph.
import com.styleconverter.runtime.core.renderer.InlineRunPlan
// SHOUTY keyword reads (Hyphens wire is a bare string, e.g. "MANUAL").
import com.styleconverter.runtime.core.types.ValueExtractors
// The writing-mode gate reuses the production extractor so the fold and
// PlaceholderContent's vertical branch can never disagree about a host.
import com.styleconverter.runtime.typography.text.TextExtractor

/**
 * InlineRunFold — collapse a resolved `meta.runs` plan into ONE paragraph
 * string when (and only when) that is a faithful rendering.
 *
 * ## The model
 * CSS lays a block container's inline content out in one inline
 * formatting context (CSS 2.1 §9.4.2): text runs and inline boxes share
 * line boxes, wrap at the container's content width, and sit on a common
 * baseline. Compose's Column has no line box, so the pre-wave-44 runs
 * path stacked each member vertically. For the corpus's dominant member
 * shape — plain text runs plus unstyled/policy-only `<span>`s — the
 * ENTIRE inline formatting context is equivalent to a single anonymous
 * text run: folding the members into one string and rendering it through
 * the EXISTING single-Text pipeline (PlaceholderContent) reproduces real
 * inline flow with every calibration that pipeline already carries
 * (greedy pre-break, hyphenation, line-box snap, half-leading baseline).
 * That equivalence is exactly why AnnotatedString/InlineTextContent is
 * NOT needed here: no supported member requires a distinct SpanStyle or
 * an inline placeholder box, so one plain string through the proven
 * pipeline beats a parallel custom Layout that would have to re-earn all
 * of those calibrations (decision recorded for lane U1; atoms/floats/
 * abspos members are the cases that WILL need placeholders, and they
 * bail below until a wave owns them).
 *
 * ## The gate (everything else bails, loudly, to the stacked fallback)
 * A fold is offered only when EVERY resolved entry is one of:
 *   • an anonymous text run (`Entry.Text`);
 *   • a TEXT member: a childless, runs-less, decoration-less child whose
 *     tag is a no-UA-styling inline tag ([TEXT_MEMBER_TAGS]) and whose
 *     properties are all paragraph policy ([PARAGRAPH_POLICY_TYPES]) or
 *     paint-inert ([BORDER_COLOR_TYPES] — border-*-color without any
 *     style/width paints nothing, css-backgrounds-3 §3.2 initial
 *     `border-style: none`);
 *   • an EMPTY member: a childless child with no text — zero glyphs, so
 *     a wider inert set applies ([EMPTY_MEMBER_INERT_TYPES]: color and
 *     decoration properties cannot paint without glyphs) over the wider
 *     [EMPTY_MEMBER_TAGS] (UA font styling is invisible on nothing).
 *     Wave 45 (lane X2) split this family in two: a PAINTED empty member
 *     — visible borders per [InlineAtomRing.resolve], which also decodes
 *     the converter's `border: inherit` wire dialect against the host —
 *     becomes an ATOM: one [InlineAtomRing.MARKER] in the merged string,
 *     one [Outcome.Folded.atoms] entry, rendered as an InlineTextContent
 *     placeholder by the seam (inherit-computed-001's `<em>` is the
 *     measured case: the ref paints a 6×29px black ring inline).
 *     A PAINT-INERT one is dropped from the string as before, counted in
 *     [Outcome.Folded.droppedEmptyMembers] so the caller can leave a
 *     breadcrumb (its strut is still a stated loss — CSS 2.1 §10.8).
 * Host-level bails: `text-transform` (a length-changing rewrite this fold
 * does not model), a vertical writing mode (the rotated branch's swapped
 * constraints are uncalibrated for merged runs), tabs (tab-size expansion
 * would shift member boundaries), and an empty merge result.
 *
 * ## Hyphens adoption (css-text-3 §6.1)
 * `hyphens` is the one policy the victims' spans DO declare. It governs
 * break opportunities inside the span's text, but Compose's line breaker
 * takes ONE mode per paragraph — so the fold adopts the members' mode
 * for the whole paragraph when the host declares none (the host text in
 * every victim is break-free, making adoption exact there; for other
 * documents it is the closest single-mode approximation and is reported
 * via [Outcome.Folded.adoptedHyphens]). Conflicting member modes, or a
 * member contradicting a host declaration, bail: no single mode is
 * faithful. `auto` adoption additionally requires the member's language
 * to match the paragraph's ([hostEffectiveLang]) — the dictionary is
 * selected per language (§6.1 "appropriate to the language of the text").
 *
 * Pure Kotlin (no Compose, no Android) so the JVM suite pins every rule
 * on the verbatim wave43-final wire payloads without a device.
 */
object InlineRunFold {

    /** One PAINTED empty member (wave 45, lane X2 — the ATOM RING): its
     *  child index and resolved box paint. The merged text carries one
     *  [InlineAtomRing.MARKER] per atom, in list order, which the seam
     *  turns into an InlineTextContent placeholder (InlineAtomContent). */
    data class Atom(val memberIndex: Int, val spec: InlineAtomRing.Spec)

    /** The fold decision — never silent: a bail always carries its reason. */
    sealed interface Outcome {
        /** The runs collapse into one faithful paragraph. */
        data class Folded(
            /** The merged paragraph, cross-boundary whitespace collapsed. */
            val text: String,
            /** Host properties, plus the adopted member Hyphens (if any). */
            val properties: List<IRProperty>,
            /** True when a member's `hyphens` now governs the paragraph. */
            val adoptedHyphens: Boolean,
            /** PAINT-INERT empty members dropped (zero glyphs, zero paint —
             *  see class banner; painted ones become [atoms] instead). */
            val droppedEmptyMembers: Int,
            /** Wave 45 (lane X2): painted empty members, one per MARKER in
             *  [text], marker order. Empty for every pre-wave-45 shape. */
            val atoms: List<Atom> = emptyList(),
        ) : Outcome

        /** Unsupported shape — caller keeps the stacked fallback and logs. */
        data class Bailed(val reason: String) : Outcome
    }

    // TEXT members: only tags whose UA stylesheet adds NO visual styling
    // (HTML rendering §15.3: em/i/cite/var/dfn are italic, strong/b bold,
    // code/samp/kbd monospace, small smaller, mark highlighted, q quoted,
    // u/s/a decorated, sub/sup baseline-shifted — none of that styling
    // rides the wire, so folding their text would silently drop it).
    private val TEXT_MEMBER_TAGS = setOf("span", "time", "data")

    // EMPTY members paint no glyphs, so UA FONT styling is invisible and
    // the tag set widens to the plain inline-text family. Deliberately
    // still excludes atoms (input/img/…, which paint widget/replaced
    // boxes), br (a forced line break is layout, not nothing), and
    // sub/sup (kept out for symmetry with the text set).
    private val EMPTY_MEMBER_TAGS = TEXT_MEMBER_TAGS + setOf(
        "em", "strong", "b", "i", "code", "small", "q", "cite", "abbr",
        "mark", "samp", "kbd", "var", "dfn",
    )

    // Paragraph policy a member may contribute (adopted, not painted).
    private val PARAGRAPH_POLICY_TYPES = setOf("Hyphens")

    // border-*-color with no border-*-style on the same box paints
    // nothing (css-backgrounds-3 §3.2: the initial style is `none`, and
    // the members here never carry a style — the gate would bail on one).
    private val BORDER_COLOR_TYPES = setOf(
        "BorderTopColor", "BorderRightColor", "BorderBottomColor", "BorderLeftColor",
    )

    // What a TEXT member may carry: policy + paint-inert colors. Any
    // visual property (Color, FontWeight, VerticalAlign, boxes, …) bails —
    // folding it flat would repaint the member in the host's style.
    private val TEXT_MEMBER_TYPES = PARAGRAPH_POLICY_TYPES + BORDER_COLOR_TYPES

    // What an EMPTY member may carry: with zero glyphs, text ink and
    // decoration properties cannot paint, so they join the inert set.
    // FontSize/LineHeight stay OUT: an empty inline still contributes its
    // strut to the line box (CSS 2.1 §10.8), which this fold drops.
    private val EMPTY_MEMBER_INERT_TYPES = TEXT_MEMBER_TYPES + setOf(
        "Color", "TextDecorationLine", "TextDecorationColor", "TextDecorationStyle",
    )

    // Wave 45 (lane X2) — what a PAINTED empty member (an ATOM) may carry
    // beyond the inert set: the box-paint longhands the atom ring renders.
    // Everything else still bails (FontSize would change the strut/ring
    // height, Padding* would open a painted content area — unmodeled).
    // BackgroundColor stays OUT deliberately: with zero content width and
    // no padding it paints nothing, so admitting it would silently drop a
    // declaration the stacked fallback at least mounts as a box.
    private val ATOM_MEMBER_TYPES = EMPTY_MEMBER_INERT_TYPES + setOf(
        "BorderTopStyle", "BorderRightStyle", "BorderBottomStyle", "BorderLeftStyle",
        "BorderTopWidth", "BorderRightWidth", "BorderBottomWidth", "BorderLeftWidth",
    )

    /** The SHOUTY keyword of a Hyphens property ("MANUAL"/"AUTO"/"NONE"). */
    private fun hyphensKeyword(prop: IRProperty): String? =
        ValueExtractors.extractKeyword(prop.data)?.uppercase()

    /**
     * Whether the merged text so far ends in a collapsible space, LOOKING
     * THROUGH any trailing atom markers: css-text-3 §4.1.1's phase-1
     * collapsing crosses element boundaries, and an empty inline BOX (the
     * atom) does not interrupt the chain — Chromium paints
     * inherit-computed-001's `… size <em></em> and …` as "size ▮and":
     * the space BEFORE the em survives, the one after it collapses.
     */
    private fun endsWithCollapsibleSpace(sb: StringBuilder): Boolean {
        // Walk back over markers (zero-glyph boxes) to the last real char.
        var i = sb.length - 1
        while (i >= 0 && sb[i] == InlineAtomRing.MARKER) i--
        return i >= 0 && sb[i] == ' '
    }

    /**
     * Append [segment] to [sb] with css-text-3 §4.1.1 phase-1 collapsing
     * across the member boundary: a collapsible space immediately
     * following another collapsible space — even across an element
     * boundary, and even across an intervening EMPTY inline — collapses.
     * (inherit-computed-001: `… size <em></em> and …` must merge to
     * "size ▮and", one space, exactly as the Chromium ref paints it.)
     * Only U+0020 is handled: the producer already collapsed runs of
     * source whitespace to single spaces inside each text node.
     */
    private fun appendCollapsed(sb: StringBuilder, segment: String) {
        // Drop the incoming segment's leading spaces when the merged text
        // already ends in one — the cross-boundary collapse. The check
        // looks through atom markers (wave 45): an empty inline box does
        // not break the collapsing chain.
        val s = if (endsWithCollapsibleSpace(sb)) segment.trimStart(' ') else segment
        // Everything else is verbatim content (soft hyphens included —
        // rule A / the manual-mode materialization live downstream).
        sb.append(s)
    }

    /**
     * Decide whether the resolved [entries] fold into one paragraph.
     *
     * @param entries the resolved plan ([InlineRunPlan.resolve] output) —
     *   dangling/duplicate references were already dropped there.
     * @param children the host's composed children ([InlineRunPlan.Entry.Child]
     *   holds indices into this list).
     * @param hostProperties the host's (inheritance-merged) property list —
     *   returned as the fold's paragraph properties, plus any adoption.
     * @param hostEffectiveLang the host's effective content language (the
     *   renderer's LocalContentLanguage at the seam) — gates `auto`
     *   adoption, whose dictionary is language-selected (§6.1).
     */
    fun fold(
        entries: List<InlineRunPlan.Entry>,
        children: List<IRComponent>,
        hostProperties: List<IRProperty>,
        hostEffectiveLang: String?,
    ): Outcome {
        // Host gate 1 — text-transform rewrites the string downstream
        // (PlaceholderContent applies it to the WHOLE merged text); a
        // length-changing transform would move member boundaries, and no
        // victim carries one, so the fold declines rather than models it.
        if (hostProperties.any { it.type == "TextTransform" }) return Outcome.Bailed("host-text-transform")
        // Host gate 2 — vertical writing modes render through the rotated
        // branch whose swapped-constraint measurement this fold has never
        // been calibrated against (direction-upright-001/002 keep their
        // frozen stacked rendering byte-identically).
        if (TextExtractor.extractWritingModeConfig(hostProperties.map { it.type to it.data }).isVertical) {
            return Outcome.Bailed("host-vertical-writing-mode")
        }
        // The merged paragraph accumulator.
        val sb = StringBuilder()
        // PAINT-INERT empty members dropped (reported, never silent).
        var dropped = 0
        // Wave 45 (lane X2) — PAINTED empty members, in marker order.
        val atoms = mutableListOf<Atom>()
        // The member-adopted Hyphens declaration (first one wins; a second
        // DIFFERENT one bails below).
        var adopted: IRProperty? = null
        // The host's own declaration, if any — adoption never overrides it.
        val hostHyphensKw = hostProperties.firstOrNull { it.type == "Hyphens" }?.let { hyphensKeyword(it) }
        // Walk the resolved entries in wire order.
        for (entry in entries) when (entry) {
            // Anonymous text run — verbatim content (spaces included).
            is InlineRunPlan.Entry.Text -> {
                // Alias guard (wave 45): a SOURCE U+FFFC would be
                // indistinguishable from an atom marker downstream, so the
                // fold declines rather than mis-binding a placeholder.
                if (entry.text.indexOf(InlineAtomRing.MARKER) >= 0) {
                    return Outcome.Bailed("contains-object-replacement")
                }
                appendCollapsed(sb, entry.text)
            }
            // A referenced child — classify against the member gate.
            is InlineRunPlan.Entry.Child -> {
                // resolve() only emits valid indices; guard anyway so a
                // future caller cannot make this throw.
                val child = children.getOrNull(entry.index)
                    ?: return Outcome.Bailed("member-index-out-of-range")
                // Tag first: unknown/none is not an inline text tag.
                val tag = child._tag?.lowercase() ?: return Outcome.Bailed("member-tag:none")
                // Structure: a member with its own children, runs or
                // decoration wire is a real inline subtree — out of scope.
                if (!child.children.isNullOrEmpty()) return Outcome.Bailed("member-has-children")
                if (!child.runs.isNullOrEmpty()) return Outcome.Bailed("member-has-runs")
                if (!child.decorations.isNullOrEmpty()) return Outcome.Bailed("member-has-decorations")
                // Empty vs text member decides the tag and property sets.
                val text = child._text
                val emptyMember = text.isNullOrEmpty()
                // Tag admission per member family (see the two sets above).
                if (tag !in (if (emptyMember) EMPTY_MEMBER_TAGS else TEXT_MEMBER_TAGS)) {
                    return Outcome.Bailed("member-tag:$tag")
                }
                // Alias guard (wave 45) — same as the Entry.Text gate.
                if (text != null && text.indexOf(InlineAtomRing.MARKER) >= 0) {
                    return Outcome.Bailed("contains-object-replacement")
                }
                // Wave 45 (lane X2) — the ATOM RING gate: a PAINTED empty
                // member (visible borders once the `border: inherit` wire
                // dialect is decoded against the host — see InlineAtomRing's
                // banner) becomes an inline placeholder instead of a drop.
                // null == paint-inert → the wave-44 drop path, unchanged.
                val atomSpec = if (emptyMember) InlineAtomRing.resolve(child.properties, hostProperties) else null
                // Property admission — the first unsupported type names
                // the bail so logcat can be audited per member. A painted
                // empty member may additionally carry the box-paint
                // longhands its ring renders (ATOM_MEMBER_TYPES).
                val offending = child.properties.firstOrNull {
                    it.type !in when {
                        atomSpec != null -> ATOM_MEMBER_TYPES
                        emptyMember -> EMPTY_MEMBER_INERT_TYPES
                        else -> TEXT_MEMBER_TYPES
                    }
                }
                if (offending != null) return Outcome.Bailed("member-prop:${offending.type}")
                // Hyphens contribution (see the class banner's adoption rules).
                val memberHyphens = child.properties.firstOrNull { it.type == "Hyphens" }
                if (memberHyphens != null) {
                    // Member modes compare as keywords, not raw JSON.
                    val kw = hyphensKeyword(memberHyphens)
                    if (hostHyphensKw != null) {
                        // Host declared: a member may only AGREE with it.
                        if (kw != hostHyphensKw) return Outcome.Bailed("hyphens-conflict")
                    } else if (adopted == null) {
                        // `auto` is dictionary-driven per language — adopt
                        // only when the member's language IS the paragraph's
                        // (a null member lang inherits the host's, which is
                        // trivially equal).
                        if (kw == "AUTO" && child.lang != null && child.lang != hostEffectiveLang) {
                            return Outcome.Bailed("hyphens-lang-divergence")
                        }
                        // First member declaration becomes the paragraph mode.
                        adopted = memberHyphens
                    } else if (hyphensKeyword(adopted) != kw) {
                        // Two members disagreeing: no single mode is faithful.
                        return Outcome.Bailed("hyphens-conflict")
                    }
                }
                // Contribute the member's glyphs — or its atom marker
                // (wave 45: a painted empty member occupies real advance
                // in the line, exactly one MARKER's placeholder) — or
                // count the paint-inert drop.
                when {
                    atomSpec != null -> {
                        // The marker the seam binds to this atom, k-th
                        // marker ↔ atoms[k] (InlineAtomContent.annotate).
                        sb.append(InlineAtomRing.MARKER)
                        atoms += Atom(entry.index, atomSpec)
                    }
                    emptyMember -> dropped++
                    else -> appendCollapsed(sb, text!!)
                }
            }
        }
        // Host gate 3 — tab-size expansion downstream rewrites '\t' into
        // spaces and would shift boundaries; no victim carries tabs.
        if (sb.indexOf("\t") >= 0) return Outcome.Bailed("contains-tab")
        // An all-empty merge has nothing to paint — the stacked fallback's
        // empty boxes are the established rendering for that shape. A
        // MARKER-only merge counts as empty too (wave 45): a glyph-less
        // host keeps its established stacked rendering rather than
        // routing a lone atom through the paragraph pipeline.
        val merged = sb.toString()
        if (merged.none { it != InlineAtomRing.MARKER }) return Outcome.Bailed("empty-merge")
        // The paragraph's property list: the host's, plus the adopted
        // member policy appended LAST (PlaceholderContent's own-list-first
        // Hyphens read finds it; an existing host declaration was never
        // overridden — adoption only happens when the host declared none).
        val properties = adopted?.let { hostProperties + it } ?: hostProperties
        // The faithful fold (atoms in marker order — see Atom's contract).
        return Outcome.Folded(merged, properties, adopted != null, dropped, atoms)
    }
}
