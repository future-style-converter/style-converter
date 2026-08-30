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
 *   • wave 47 (lane Z6, the STYLED-SPAN RING): a STYLED glyph member — a
 *     span/time/data/`u` member whose ink properties (Color, FontSize,
 *     FontWeight, FontStyle, TextDecorationLine underline/line-through)
 *     ride the fold as a per-range [Span] the seam overlays as SpanStyles
 *     (admission rules + stated losses: [InlineSpanRing]'s banner).
 *     Wave 48 (lane W4) widens the tag ring with the UA-italic family
 *     (i/em/cite/var/dfn) and the UA-shifted pair (sup/sub — smaller +
 *     baseline shift), both modeled from the HTML rendering stylesheet
 *     exactly like `u`'s underline (measured victims: subelements-002
 *     android-ref 0.7649 / ios-ref 0.7272 at wave48-cal, its two hosts
 *     painting ten stacked blocks where Chromium flows two lines).
 *     Nested shapes admitted by [flattenNestedRuns]: {text, `<br>`}
 *     subtrees (wave 47) and ONE-level nested glyph members (wave 48 —
 *     `<i>e = mc<sup>2</sup></i>`, per-piece spans composed via
 *     [InlineSpanRing.composeNested]); deeper trees stay a wall;
 *   • wave 47 (lane Z6, the BR RING): a `<br>` member (tag or the
 *     extractor's `role: line-break`) folds to '\n' with css-text-3
 *     §4.1.4 space removal on both sides — but ONLY alongside at least
 *     one glyph member: a {text, `<br>`}*-only host's stacked fallback
 *     already renders the forced-break line structure (one block per
 *     run), so engaging there would move calibrated pixels for zero
 *     fidelity gain ("br-stacked-equivalent"; the corpus-simulated rule
 *     that keeps the wave-47 flip set to exactly the 4 styled victims).
 * Host-level bails: `text-transform` (a length-changing rewrite this fold
 * does not model), a vertical writing mode (the rotated branch's swapped
 * constraints are uncalibrated for merged runs), tabs (tab-size expansion
 * would shift member boundaries), `font-variant-caps` when spans ride
 * (the small-caps case rewrite is not always length-preserving, which
 * would break the span alignment), and an empty merge result.
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

    /** One STYLED member's range in the merged text (wave 47, lane Z6 —
     *  the STYLED-SPAN RING): [start]..[end] (exclusive) are offsets into
     *  [Outcome.Folded.text] BEFORE the leaf pipeline's string surgery —
     *  the seam maps them through [InlineSpanRing.alignment] onto the
     *  final render string and overlays one SpanStyle per span.
     *  [statedLossTypes] names box ink the ring consciously drops
     *  (border longhands — see InlineSpanRing's banner) for the seam's
     *  no-silent-fallthrough breadcrumb. */
    data class Span(
        val memberIndex: Int,
        val start: Int,
        val end: Int,
        val style: InlineSpanRing.Style,
        val statedLossTypes: List<String> = emptyList(),
    )

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
            /** Wave 47 (lane Z6): styled-member ranges over [text], wire
             *  order. Empty for every pre-wave-47 shape — the seam then
             *  renders byte-identically to wave 45. */
            val spans: List<Span> = emptyList(),
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

    // Wave 47 (lane Z6) — what a BR member (the forced-break ring) may
    // carry: the converter measures the break's box and emits Width/Height
    // on it (0×0 or 0×lineHeight — geometry the '\n' itself expresses),
    // plus the zero-glyph inert set (nothing can paint on a break).
    private val BR_MEMBER_TYPES = EMPTY_MEMBER_INERT_TYPES + setOf("Width", "Height")

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
     * Wave 47 (lane Z6): a trailing '\n' (a folded `<br>`) answers true
     * too — css-text-3 §4.1.4(3): collapsible spaces at the START of a
     * line are removed, and everything after a forced break starts a
     * line. '\n' never occurs in a pre-wave-47 merge, so the old
     * decisions are untouched by construction.
     */
    private fun endsWithCollapsibleSpace(sb: StringBuilder): Boolean {
        // Walk back over markers (zero-glyph boxes) to the last real char.
        var i = sb.length - 1
        while (i >= 0 && sb[i] == InlineAtomRing.MARKER) i--
        return i >= 0 && (sb[i] == ' ' || sb[i] == '\n')
    }

    /**
     * Append one forced line break (a folded `<br>` member — HTML
     * §4.5.27) to [sb]. css-text-3 §4.1.4(1): collapsible spaces at the
     * END of a line are removed — everything before a forced break ends a
     * line, so the trailing space run is dropped before the '\n' lands
     * (block-ellipsis-004's `Line 2<br>` / ` Line 3` wire merges to
     * "Line 2\nLine 3", exactly the two lines Chromium paints). The
     * leading-space half of the rule lives in [endsWithCollapsibleSpace].
     */
    private fun appendBreak(sb: StringBuilder) {
        // Drop the collapsible spaces that would otherwise end the line.
        while (sb.isNotEmpty() && sb.last() == ' ') sb.deleteCharAt(sb.length - 1)
        sb.append('\n')
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
        // Wave 47 (lane Z6) — the STYLED-SPAN ring's collectors: member
        // ranges over the merged text (wire order), plus the BR / glyph
        // member counts the stacked-equivalence rule below reads.
        val spans = mutableListOf<Span>()
        var breakMembers = 0
        var glyphMembers = 0
        // The css-text-3 §6.1 adoption walk, shared by the EMPTY and the
        // glyph member arms (a lambda so `adopted` threads through both).
        // Returns the bail reason, or null when the member's declaration
        // is compatible — the exact wave-44 rules, hoisted verbatim.
        val adoptMemberHyphens: (IRComponent) -> String? = adopt@{ child ->
            val memberHyphens = child.properties.firstOrNull { it.type == "Hyphens" }
                ?: return@adopt null
            // Member modes compare as keywords, not raw JSON.
            val kw = hyphensKeyword(memberHyphens)
            if (hostHyphensKw != null) {
                // Host declared: a member may only AGREE with it.
                if (kw != hostHyphensKw) return@adopt "hyphens-conflict"
            } else if (adopted == null) {
                // `auto` is dictionary-driven per language — adopt only
                // when the member's language IS the paragraph's (a null
                // member lang inherits the host's, trivially equal).
                if (kw == "AUTO" && child.lang != null && child.lang != hostEffectiveLang) {
                    return@adopt "hyphens-lang-divergence"
                }
                // First member declaration becomes the paragraph mode.
                adopted = memberHyphens
            } else if (hyphensKeyword(adopted!!) != kw) {
                // Two members disagreeing: no single mode is faithful.
                return@adopt "hyphens-conflict"
            }
            null
        }
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
                // Wave 48 (lane W4) MEASURED this wall before keeping it:
                // the converter omits sourceTag exactly for `<div>`s, so
                // the corpus's 75 tagless members are BLOCK boxes
                // (backgrounds, min/max-heights, borders — the css-break
                // block-*-height family, line-clamp-006/007's sized
                // children), whose stacked anonymous-block rendering is
                // the CORRECT CSS 2.1 §9.2.1.1 structure. Simulation
                // semantics (S5-corrected, fix lane F5): admitting a
                // tagless glyph member THROUGH THE RING AS A SPAN (the
                // strict reading — property admission included, not just
                // plain text) flips TWO fold decisions over wave48-cal —
                // display-contents-flex-001 AND block-ellipsis-003, both
                // PASSERS — so folding them would only move calibrated
                // pixels. The wall stands on evidence, not caution.
                val tag = child._tag?.lowercase() ?: return Outcome.Bailed("member-tag:none")
                // ── Wave 47 (lane Z6) — the BR RING ─────────────────────
                // `<br>` is a FORCED line break (HTML §4.5.27), i.e. '\n'
                // in the merged paragraph — surrounding collapsible spaces
                // fall per css-text-3 §4.1.4 (appendBreak's banner). The
                // extractor tags it `role: line-break`, read alongside the
                // tag so the two producers agree.
                if (tag == "br" || child.role == "line-break") {
                    // A break with structure is not a break we understand.
                    if (!child.children.isNullOrEmpty() || !child.runs.isNullOrEmpty() ||
                        !child.decorations.isNullOrEmpty()
                    ) return Outcome.Bailed("br-member-structure")
                    // Width/Height are the converter's measured break box —
                    // geometry the '\n' expresses; anything else bails.
                    val brOffending = child.properties.firstOrNull { it.type !in BR_MEMBER_TYPES }
                    if (brOffending != null) return Outcome.Bailed("member-prop:${brOffending.type}")
                    appendBreak(sb)
                    breakMembers++
                    continue
                }
                // The decoration / marker wire attaches to the member's
                // OWN box; folding its glyphs would drop that ink.
                if (!child.decorations.isNullOrEmpty()) return Outcome.Bailed("member-has-decorations")
                // A member with its own children/runs is a nested inline
                // tree — foldable (below) ONLY when every nested node is a
                // BR member; EMPTY means no glyphs AND no structure.
                val text = child._text
                val hasNested = !child.children.isNullOrEmpty() || !child.runs.isNullOrEmpty()
                val emptyMember = text.isNullOrEmpty() && !hasNested
                // Alias guard (wave 45) — same as the Entry.Text gate.
                if (text != null && text.indexOf(InlineAtomRing.MARKER) >= 0) {
                    return Outcome.Bailed("contains-object-replacement")
                }
                if (emptyMember) {
                    // ── The wave-44/45 EMPTY / ATOM arm, unchanged ──────
                    if (tag !in EMPTY_MEMBER_TAGS) return Outcome.Bailed("member-tag:$tag")
                    // Wave 45 (lane X2) — the ATOM RING gate: a PAINTED
                    // empty member (visible borders once the `border:
                    // inherit` wire dialect is decoded against the host —
                    // see InlineAtomRing's banner) becomes an inline
                    // placeholder instead of a drop; null == paint-inert.
                    val atomSpec = InlineAtomRing.resolve(child.properties, hostProperties)
                    // Property admission — the first unsupported type
                    // names the bail so logcat can be audited per member.
                    val offending = child.properties.firstOrNull {
                        it.type !in (if (atomSpec != null) ATOM_MEMBER_TYPES else EMPTY_MEMBER_INERT_TYPES)
                    }
                    if (offending != null) return Outcome.Bailed("member-prop:${offending.type}")
                    // Hyphens contribution (class banner's adoption rules).
                    adoptMemberHyphens(child)?.let { return Outcome.Bailed(it) }
                    // Contribute the atom marker (a painted empty member
                    // occupies real advance — one MARKER's placeholder,
                    // k-th marker ↔ atoms[k]) or count the inert drop.
                    if (atomSpec != null) {
                        sb.append(InlineAtomRing.MARKER)
                        atoms += Atom(entry.index, atomSpec)
                    } else {
                        dropped++
                    }
                } else {
                    // ── Wave 47 (lane Z6) — the GLYPH member arm ────────
                    // The styled tag ring (the wave-44 no-UA-ink trio plus
                    // `u`, whose UA underline the span models).
                    if (tag !in InlineSpanRing.STYLED_MEMBER_TAGS) return Outcome.Bailed("member-tag:$tag")
                    // Property admission through the span ring — PLAIN
                    // members (Hyphens / paint-inert colors only) pass
                    // with no attribution, exactly the wave-44 gate.
                    val admission = InlineSpanRing.admit(tag, child.properties, hostProperties)
                    if (admission is InlineSpanRing.Admission.Refused) {
                        return Outcome.Bailed(admission.reason)
                    }
                    val admitted = admission as InlineSpanRing.Admission.Admitted
                    // Hyphens contribution (class banner's adoption rules).
                    adoptMemberHyphens(child)?.let { return Outcome.Bailed(it) }
                    // Contribute the member's glyphs — flattening a nested
                    // subtree ({text, <br>} since wave 47; wave 48/W4 adds
                    // one-level nested GLYPH members, subelements-002's
                    // `<i>e = mc<sup>2</sup></i>`), or the flat text — and
                    // record the span(s) over exactly what landed.
                    val start = sb.length
                    if (hasNested) {
                        when (val flat = flattenNestedRuns(child, entry.index, admitted, hostProperties, sb, spans)) {
                            // A named wall — the whole fold bails, as ever.
                            is Flattened.Bail -> return Outcome.Bailed(flat.reason)
                            // Wave 48 (lane W4): a nested glyph member rode —
                            // the flatten emitted PER-PIECE spans (outer
                            // style over the member's own text, composed
                            // style over each nested member) so the two
                            // ranges can carry different attribution.
                            Flattened.SpansEmitted -> {}
                            // {text, <br>}-only subtree: the wave-47 single
                            // whole-range span below, BYTE-IDENTICALLY (its
                            // '\n' coverage pins the passing block-ellipsis
                            // captures — see Flattened's banner).
                            Flattened.NoNestedGlyphs ->
                                if (!admitted.style.isPlain || admitted.statedLossTypes.isNotEmpty()) {
                                    spans += Span(entry.index, start, sb.length, admitted.style, admitted.statedLossTypes)
                                }
                        }
                    } else {
                        appendCollapsed(sb, text!!)
                        // Only a member with real attribution (or a stated
                        // loss to report) emits a span — plain members render
                        // byte-identically to wave 44.
                        if (!admitted.style.isPlain || admitted.statedLossTypes.isNotEmpty()) {
                            spans += Span(entry.index, start, sb.length, admitted.style, admitted.statedLossTypes)
                        }
                    }
                    glyphMembers++
                }
            }
        }
        // Wave 47 (lane Z6) — BR STACKED-EQUIVALENCE: a {text, <br>}*-only
        // host needs no fold, because the stacked fallback ALREADY renders
        // one block per anonymous run — exactly the forced-break line
        // structure — and that rendering is the calibrated shape every
        // committed capture of those hosts pins. Engaging would move
        // pixels on passing tests for zero fidelity gain, so the break
        // ring only rides alongside a glyph member (the corpus-simulated
        // rule: exactly the 4 styled victims flip, nothing else).
        // Wave 48 (lane W4) RE-MEASURED the rule over wave48-cal before
        // keeping it: lifting it flips 29 host decisions across 27 tests,
        // ~16 of them PASSING (block-ellipsis-002/003, revert-val-001/002,
        // multicol baseline-000/008, seven backdrop-filter cells, …),
        // while the failing rest (hyphenate-character-00x, multicol,
        // masking, blur) fail on defects a fold cannot touch — the rule
        // stands on evidence.
        if (breakMembers > 0 && glyphMembers == 0) {
            return Outcome.Bailed("br-stacked-equivalent")
        }
        // Host gate 3 — tab-size expansion downstream rewrites '\t' into
        // spaces and would shift boundaries; no victim carries tabs.
        if (sb.indexOf("\t") >= 0) return Outcome.Bailed("contains-tab")
        // Host gate 4 (wave 47) — small-caps synthesis rewrites the
        // string's case downstream (synthesizeSmallCaps), and `uppercase()`
        // is not always length-preserving (ß→SS), which would break the
        // span alignment; no styled victim declares font-variant-caps.
        if (spans.isNotEmpty() && hostProperties.any { it.type == "FontVariantCaps" }) {
            return Outcome.Bailed("styled-host-font-variant")
        }
        // An all-empty merge has nothing to paint — the stacked fallback's
        // empty boxes are the established rendering for that shape. A
        // MARKER-only (wave 45) or breaks-only merge counts as empty too:
        // a glyph-less host keeps its established stacked rendering.
        val merged = sb.toString()
        if (merged.none { it != InlineAtomRing.MARKER && it != '\n' }) return Outcome.Bailed("empty-merge")
        // The paragraph's property list: the host's, plus the adopted
        // member policy appended LAST (PlaceholderContent's own-list-first
        // Hyphens read finds it; an existing host declaration was never
        // overridden — adoption only happens when the host declared none).
        val properties = adopted?.let { hostProperties + it } ?: hostProperties
        // The faithful fold (atoms in marker order — see Atom's contract;
        // spans in wire order — see Span's contract).
        return Outcome.Folded(merged, properties, adopted != null, dropped, atoms, spans)
    }

    /** The nested-flatten verdict (wave 48, lane W4). [NoNestedGlyphs]
     *  keeps the caller's wave-47 single whole-range span — that span
     *  COVERS the flattened '\n's, and a fontSize-bearing span over a
     *  '\n' participates in Compose's line-height resolution, so
     *  re-emitting {text, <br>} shapes per-piece would move the PASSING
     *  block-ellipsis-004/005/006 captures; [SpansEmitted] says a nested
     *  glyph member rode and the flatten emitted per-piece spans itself. */
    private sealed interface Flattened {
        data class Bail(val reason: String) : Flattened
        object NoNestedGlyphs : Flattened
        object SpansEmitted : Flattened
    }

    /**
     * Flatten a glyph member's OWN `meta.runs` into [sb]. Two nested
     * shapes are admitted, every nested child claimed in strictly
     * increasing order (the member's `_text` is the concatenation the
     * runs were split FROM — spec 03 §4.1 — and is deliberately ignored;
     * the runs are authoritative):
     *   • wave 47 (lane Z6): BR members ({text, `<br>`} —
     *     block-ellipsis-004's `<span>Line 3<br>Line 4</span>`);
     *   • wave 48 (lane W4): ONE-LEVEL nested GLYPH members admitted by
     *     the span ring (subelements-002's `<i>e = mc<sup>2</sup></i>`,
     *     inset-005's `<u>ultra-<sup>quick</sup> b<sub>row</sub>n</u>`),
     *     each folded with [InlineSpanRing.composeNested]'s composition
     *     of the outer member's style — deeper nesting stays a wall.
     * When a nested glyph member rides, the flatten emits PER-PIECE spans
     * into [spans] (outer style over the member's own text pieces,
     * composed style over each nested member's) and answers
     * [Flattened.SpansEmitted]; otherwise [Flattened.NoNestedGlyphs] and
     * the caller keeps the wave-47 whole-range emission byte-identically.
     * Anything else returns the named bail ([Flattened.Bail]).
     */
    private fun flattenNestedRuns(
        member: IRComponent,
        memberIndex: Int,
        outer: InlineSpanRing.Admission.Admitted,
        hostProperties: List<IRProperty>,
        sb: StringBuilder,
        spans: MutableList<Span>,
    ): Flattened {
        // Children without a runs order is a shape the wire never emits
        // for text members — refuse with the wave-44 reason.
        val runs = member.runs ?: return Flattened.Bail("member-has-children")
        val kids = member.children.orEmpty()
        // The child's AUTHORING KEY resolves `name` before `id` —
        // InlineRunPlan.resolve's exact contract, first occurrence wins.
        val byName = HashMap<String, Int>()
        val byId = HashMap<String, Int>()
        kids.forEachIndexed { i, k ->
            if (k.name.isNotEmpty() && k.name !in byName) byName[k.name] = i
            if (k.id.isNotEmpty() && k.id !in byId) byId[k.id] = i
        }
        // The per-piece attributions, collected over the walk — emitted
        // into [spans] only when a nested glyph member makes the member's
        // attribution non-uniform (see [Flattened]'s banner).
        data class Piece(val start: Int, val end: Int, val style: InlineSpanRing.Style, val losses: List<String>)
        val pieces = mutableListOf<Piece>()
        var sawNestedGlyph = false
        // Strictly-increasing claim walk — a duplicate or out-of-order
        // ref cannot be one forward paragraph (InlineRunPlan's proof).
        var last = -1
        var claimed = 0
        for (run in runs) {
            val text = run.text
            if (text != null) {
                // Alias guard — same as every other text-append site.
                if (text.indexOf(InlineAtomRing.MARKER) >= 0) return Flattened.Bail("contains-object-replacement")
                // The member's own glyphs carry the OUTER style.
                val a = sb.length
                appendCollapsed(sb, text)
                pieces += Piece(a, sb.length, outer.style, outer.statedLossTypes)
                continue
            }
            // A both-nil entry is unreachable wire — skip defensively,
            // mirroring InlineRunPlan.
            val key = run.child ?: continue
            val idx = byName[key] ?: byId[key] ?: return Flattened.Bail("nested-dangling")
            if (idx <= last) return Flattened.Bail("nested-order")
            last = idx
            val kid = kids[idx]
            val kidTag = kid._tag?.lowercase()
            // ── wave 47: a nested BR member — one forced '\n' ───────────
            if (kidTag == "br" || kid.role == "line-break") {
                if (!kid.children.isNullOrEmpty() || !kid.runs.isNullOrEmpty() ||
                    !kid.decorations.isNullOrEmpty()
                ) return Flattened.Bail("br-member-structure")
                val offending = kid.properties.firstOrNull { it.type !in BR_MEMBER_TYPES }
                if (offending != null) return Flattened.Bail("member-prop:${offending.type}")
                appendBreak(sb)
                claimed++
                continue
            }
            // ── wave 48 (lane W4): a nested GLYPH member ────────────────
            // Only span-ring tags may nest; anything else keeps the
            // nested-tree wall, with the tag named for the log.
            if (kidTag == null || kidTag !in InlineSpanRing.STYLED_MEMBER_TAGS) {
                return Flattened.Bail("nested-member-tag:${kidTag ?: "none"}")
            }
            // ONE level only: a nested member with its own structure is
            // the depth-2 wall (no corpus shape needs it — refusing keeps
            // decorating-box-001-class trees on their frozen stacked path).
            if (!kid.children.isNullOrEmpty() || !kid.runs.isNullOrEmpty() ||
                !kid.decorations.isNullOrEmpty()
            ) return Flattened.Bail("nested-member-depth")
            // A nested Hyphens declaration would need the adoption walk
            // this flatten does not run (admit() skips the type) — refuse
            // rather than drop a policy silently; zero corpus presence.
            if (kid.properties.any { it.type == "Hyphens" }) {
                return Flattened.Bail("nested-member-hyphens")
            }
            // Admission through the ring. The effective-ink chain for the
            // TextDecorationColor gate is nested → OUTER member → host,
            // so the outer's list is consulted before the host's.
            val kidAdmission = InlineSpanRing.admit(kidTag, kid.properties, member.properties + hostProperties)
            if (kidAdmission is InlineSpanRing.Admission.Refused) {
                return Flattened.Bail(kidAdmission.reason)
            }
            val kidAdmitted = kidAdmission as InlineSpanRing.Admission.Admitted
            // Compose the nested style onto the outer's — null is the
            // compound-shift wall (composeNested's banner).
            val composed = InlineSpanRing.composeNested(outer.style, kidAdmitted.style)
                ?: return Flattened.Bail("nested-vertical-align-compound")
            // The nested member's glyphs, alias-guarded like every text.
            val kidText = kid._text
            if (kidText != null && kidText.indexOf(InlineAtomRing.MARKER) >= 0) {
                return Flattened.Bail("contains-object-replacement")
            }
            val a = sb.length
            if (kidText != null) appendCollapsed(sb, kidText)
            pieces += Piece(a, sb.length, composed, outer.statedLossTypes + kidAdmitted.statedLossTypes)
            sawNestedGlyph = true
            claimed++
        }
        // Every nested child must be claimed — an unclaimed one would
        // silently vanish (the outer fold consumes the whole member).
        if (claimed != kids.size) return Flattened.Bail("nested-unclaimed")
        // {text, <br>}-only: the caller's wave-47 whole-range span stands.
        if (!sawNestedGlyph) return Flattened.NoNestedGlyphs
        // Per-piece emission — one span per attributed piece, in string
        // order (plain, loss-less pieces emit nothing, the wave-44 rule).
        for (p in pieces) {
            if (p.start >= p.end) continue
            if (!p.style.isPlain || p.losses.isNotEmpty()) {
                spans += Span(memberIndex, p.start, p.end, p.style, p.losses)
            }
        }
        return Flattened.SpansEmitted
    }
}
