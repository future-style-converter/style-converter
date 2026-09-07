//
//  InlineRunFlow.swift
//  StyleEngine/typography/inline — Wave 44 lane U2 (the inline-run wall,
//  iOS half), widened to Compose-gate parity by Wave 45 lane X1.
//
//  THE WALL. `meta.runs` says a block container's own text and its kept
//  inline children INTERLEAVE in document order (schema/spec/03-children.md
//  §4.1). Wave 32's InlineRunPlan bought the correct ORDER, but the block
//  container is a VStack, so each anonymous run and each child still
//  rendered as a STACKED label — `<div>DNA <span>…</span>.</div>` painted
//  four lines where Chromium's inline formatting context (CSS 2.1 §9.4.2)
//  flows one paragraph (css-text/hyphens-manual-inline-010: ios-ref 0.6499
//  at wave43-final, the ref keeps "DNA means Deoxyribon…" on shared lines).
//
//  THE FIX. When every referenced child is a plain inline text member the
//  container's inline content IS one paragraph: this fold concatenates the
//  runs — parent text and member text interleaved exactly as the wire
//  orders them — and the caller renders the result through the EXACT
//  leaf-text machinery (PlaceholderLabel: GreedyLineBreaker greedy
//  pre-break, LineBoxMetrics pinned line boxes, SoftHyphenPolicy /
//  WordBreakOpportunities soft-hyphen ops, AutoHyphenation dictionary).
//  A member's soft hyphens (hyphens-manual-011's `Deoxy\u{00AD}ribo\u{00AD}
//  nucleic`) survive the fold verbatim and the pre-break takes them
//  exactly as it does for leaf text.
//
//  WAVE 45 (lane X1) — GATE PARITY WITH THE COMPOSE TWIN. Wave 44 shipped
//  the two folds with DIFFERENT member gates: Compose (InlineRunFold.kt,
//  lane U1) admitted `time`/`data` text members, paint-inert border-color
//  payloads, droppable EMPTY members, and member-hyphens ADOPTION, while
//  this file admitted only style-free non-empty `<span>`s whose `hyphens`
//  equaled the container's. The wave-44 gate measured the cost of that
//  asymmetry directly: hyphens-auto-inline-010 folded on Android
//  (android-ref 0.9303 → 0.9695, wptPass) and BAILED byte-identically here
//  (ios-ref 0.9264, the stacked period on its own line), and CSS2
//  inherit-computed-001's bordered empty `<em>` refused the whole fold
//  (ios-ref 0.8369 stacked vs android-ref 0.8933 folded). The Compose gate
//  is proven safe by its own 298-host corpus simulation plus the wave-44
//  gate result. The corpus-flip evidence of record is skeptic S2's
//  slot-aware 298-host replay (wave-45 scratchpad S2-replay.py; the lane's
//  own sim artifact scanned a nonexistent 'children' key and proved
//  nothing): exactly two hosts flip to Folded
//  (the two victims above), zero other corpus hosts change decision.
//
//  The widened gate, mirrored predicate-for-predicate from InlineRunFold:
//    • TEXT members: childless, runs-less, decoration-less children whose
//      tag adds no UA styling (span/time/data — HTML rendering §15.3 makes
//      em italic, u underlined, sub shifted, … none of which rides the
//      wire, so folding THOSE would silently drop visible ink) and whose
//      properties are paragraph policy (`Hyphens`) or paint-inert
//      border-*-color (css-backgrounds-3 §3.2: the initial `border-style:
//      none` paints nothing, and a member carrying a style would bail).
//    • EMPTY members: childless children with no text — zero glyphs, so
//      UA FONT styling is invisible and the tag ring widens to the plain
//      inline-text family, and text-ink properties (Color, the three
//      TextDecoration* longhands) join the inert set. Dropped from the
//      string, COUNTED in `Folded.droppedEmptyMembers` — an empty inline's
//      strut (CSS 2.1 §10.8) and border ink are a stated loss
//      (inherit-computed-001's `<em>` carries `border: inherit` COLORS
//      whose style never reached the wire, a converter gap, so nothing
//      paints on any path today; the ref's ▮ bar is the named residual).
//    • HYPHENS ADOPTION (css-text-3 §5.3): a member's `hyphens` governs
//      break opportunities inside the member's text, but the pre-break
//      takes ONE mode per paragraph — so when the container declares none
//      the first member declaration is ADOPTED for the whole paragraph
//      (exact for the victims: their host text is break-free), reported
//      via `Folded.adoptedHyphensMode`. A member contradicting a host
//      declaration, or two members disagreeing, bail: no single mode is
//      faithful. `auto` adoption additionally requires the member's
//      language to equal the paragraph's — the dictionary is selected per
//      language (§5.3 "appropriate to the language of the text").
//    • HOST gates inherited from the twin: `text-transform` (a length-
//      changing string rewrite whose member boundaries this fold does not
//      model) and a tab in the merged text (tab-size expansion downstream
//      would shift member boundaries) now bail — the corpus scan found
//      ZERO runs hosts carrying either, so both are pure safety rails.
//    • WHITESPACE: css-text-3 §4.1.1 phase-1 collapsing across member
//      boundaries — a collapsible space following another collapses, even
//      across an element boundary or an intervening dropped EMPTY member
//      (inherit-computed-001's "… size <em></em> and …" must merge to
//      "size and", one space, exactly as the Chromium ref paints it).
//
//  WAVE 47 (lane Z6) — THE STYLED-SPAN + BR RINGS. Styled glyph members
//  (span/time/data/`u` with Color / FontSize / FontWeight / FontStyle /
//  TextDecorationLine underline+line-through) now FOLD, each recorded as
//  a `Span` range over the merged text that PlaceholderLabel renders as
//  a per-segment Text concatenation (admission + walls: InlineSpanRing's
//  banner). `<br>` members fold to '\n' with css-text-3 §4.1.2 space
//  removal — but ONLY alongside a glyph member: a {text, <br>}*-only
//  host's stacked fallback already renders the forced-break line
//  structure, so engaging there would move calibrated passing pixels for
//  zero gain ("br-stacked-equivalent"; the corpus-simulated rule that
//  keeps the wave-47 flip set to exactly the 4 styled victims —
//  block-ellipsis-004/005/006 + text-decoration-inset-014, all failing
//  on both natives at wave46-final). The one admitted nested shape is a
//  {text, <br>} subtree (`flattenNestedRuns`).
//
//  WAVE 48 (lane W4) — THE UA-STYLED + NESTED-GLYPH RINGS. The styled
//  tag ring widens with the UA-italic family (i/em/cite/var/dfn) and the
//  UA-shifted pair (sup/sub — `font-size: smaller` + the super/sub
//  baseline shift, Blink's parent/3+1 / parent/5+1 px rule), modeled
//  from the HTML rendering stylesheet exactly like `u`'s underline; and
//  `flattenNestedRuns` admits ONE-level nested glyph members with
//  per-piece composed spans (subelements-002's `<i>e = mc<sup>2</sup>
//  </i>` — android-ref 0.7649 / ios-ref 0.7272 at wave48-cal, ten
//  stacked blocks where Chromium flows two lines).
//
//  HONEST SCOPE — every refusal is a LOGGED bail to the wave-32 stacked
//  path (no silent fallthrough), so an engaged fold can never render less
//  than the plan did. Still deferred, each behind its own named wall:
//  atoms with GLYPHS or painted boxes, block-level members, nested
//  member trees at depth 2+ or outside the span ring, compound sup/sub
//  shifts, b/strong (`font-weight: bolder` is inherited-relative),
//  out-of-flow siblings (float/abspos static position), member
//  TextUnderlineOffset / text-decoration-inset (inset-011's offset
//  underlines), and whitespace-only pre-wrap members with background
//  (block-ellipsis-032's hanging-whitespace ring).
//
//  Twin: runtimes/compose/.../typography/inline/InlineRunFold.kt — the
//  shared contract is "fold to one paragraph, reuse the platform's pinned
//  leaf-text path"; the tag rings, property sets, adoption rules and bail
//  reasons below are byte-parallel with its. Pure + Foundation-only so
//  XCTest pins the decision on the VERBATIM victim payloads without a
//  render surface.
//

import Foundation

enum InlineRunFlow {

    /// Wave 47 (lane Z6) — one STYLED member's range in the merged text:
    /// `start`..<`end` are CHARACTER offsets into `Folded.text` BEFORE
    /// the label's string surgery — PlaceholderLabel maps them through
    /// `InlineSpanRing.alignment` onto its final display string and
    /// builds the per-segment Text concatenation. `statedLossTypes`
    /// names box ink the ring consciously drops (border longhands — see
    /// InlineSpanRing's banner) for the seam's breadcrumb.
    struct Span: Equatable {
        let start: Int
        let end: Int
        let style: InlineSpanRing.Style
        let statedLossTypes: [String]
    }

    /// An engaged fold — the paragraph and what the gate decided on the
    /// way. nil from `fold` is the ONLY refusal shape (logged inside).
    struct Folded: Equatable {
        /// The merged paragraph: parent runs and member text interleaved
        /// exactly as the wire orders them, cross-boundary collapsed.
        let text: String
        /// The lowercased member `hyphens` keyword now governing the
        /// paragraph, or nil when no member declaration was adopted
        /// (either none existed or the host's own declaration governs).
        /// The caller injects it into the paragraph's TextConfig — never
        /// overriding a host declaration, which blocks adoption here.
        let adoptedHyphensMode: String?
        /// Admitted EMPTY members dropped from the string (zero glyphs —
        /// see the banner's stated-loss note). Reported, never silent.
        let droppedEmptyMembers: Int
        /// Wave 47 (lane Z6): styled-member ranges over `text`, wire
        /// order. Empty for every pre-wave-47 shape — the label then
        /// renders byte-identically to wave 45. Defaulted so every
        /// wave-44/45 construction site (tests included) compiles as-is.
        var spans: [Span] = []
    }

    /// TEXT-member tag ring: only tags whose UA stylesheet adds NO visual
    /// styling (HTML rendering §15.3: em/i/cite/var/dfn italic, strong/b
    /// bold, code/samp/kbd monospace, small smaller, mark highlighted,
    /// q quoted, u/s/a decorated, sub/sup baseline-shifted — none of that
    /// rides the wire, so folding their GLYPHS would silently drop ink).
    /// Byte-parallel with InlineRunFold.TEXT_MEMBER_TAGS.
    private static let textMemberTags: Set<String> = ["span", "time", "data"]

    /// EMPTY members paint no glyphs, so UA FONT styling is invisible and
    /// the ring widens to the plain inline-text family. Deliberately still
    /// excludes atoms (input/img/…, which paint widget/replaced boxes),
    /// br (a forced break is layout, not nothing), and sub/sup (kept out
    /// for symmetry with the text ring). Twin: EMPTY_MEMBER_TAGS.
    private static let emptyMemberTags: Set<String> = textMemberTags.union([
        "em", "strong", "b", "i", "code", "small", "q", "cite", "abbr",
        "mark", "samp", "kbd", "var", "dfn",
    ])

    /// Paragraph policy a member may contribute (adopted, not painted).
    private static let paragraphPolicyTypes: Set<String> = ["Hyphens"]

    /// border-*-color with no border-*-style on the same box paints
    /// nothing (css-backgrounds-3 §3.2: the initial style is `none`, and
    /// a member carrying a style bails on the admission check below).
    private static let borderColorTypes: Set<String> = [
        "BorderTopColor", "BorderRightColor", "BorderBottomColor", "BorderLeftColor",
    ]

    /// What a TEXT member may declare: policy + paint-inert colors. Any
    /// visual property (Color, FontWeight, VerticalAlign, boxes, …) bails
    /// — folding it flat would repaint the member in the host's style.
    private static let textMemberTypes = paragraphPolicyTypes.union(borderColorTypes)

    /// What an EMPTY member may declare: with zero glyphs, text ink and
    /// decoration properties cannot paint, so they join the inert set.
    /// FontSize/LineHeight stay OUT: an empty inline still contributes its
    /// strut to the line box (CSS 2.1 §10.8), which this fold drops.
    private static let emptyMemberInertTypes = textMemberTypes.union([
        "Color", "TextDecorationLine", "TextDecorationColor", "TextDecorationStyle",
    ])

    /// Wave 47 (lane Z6) — what a BR member (the forced-break ring) may
    /// carry: the converter measures the break's box and emits
    /// Width/Height on it (0×0 or 0×lineHeight — geometry the '\n'
    /// itself expresses), plus the zero-glyph inert set (nothing can
    /// paint on a break). Twin: InlineRunFold.BR_MEMBER_TYPES.
    private static let brMemberTypes = emptyMemberInertTypes.union(["Width", "Height"])

    /// One admitted member's contribution to the paragraph walk.
    private enum MemberOutcome {
        /// A glyph member — flat text or a {text, <br>} subtree the walk
        /// flattens — carrying its wave-47 span attribution (a PLAIN
        /// style + no losses is the wave-44 text member exactly).
        case glyphs(InlineSpanRing.Style, statedLossTypes: [String])
        /// Wave 47 (lane Z6): a `<br>` member — one forced '\n'.
        case forcedBreak
        /// An admitted EMPTY member — no glyphs, counted and reported.
        case droppedEmpty
        /// Outside the ring — the named wall was already logged.
        case refused
    }

    /// Append `segment` to `out` with css-text-3 §4.1.1 phase-1 collapsing
    /// across the run/member boundary: a collapsible space immediately
    /// following another — even across an element boundary, and even
    /// across an intervening dropped EMPTY inline — collapses. Only U+0020
    /// is handled: the producer already collapsed runs of source
    /// whitespace to single spaces inside each text node. Twin:
    /// InlineRunFold.appendCollapsed.
    /// Wave 47 (lane Z6): a trailing '\n' (a folded `<br>`) collapses the
    /// next segment's leading spaces too — css-text-3 §4.1.2(3) removes
    /// collapsible spaces at the START of a line, and everything after a
    /// forced break starts one. '\n' never occurs in a pre-wave-47 merge,
    /// so the old decisions are untouched by construction.
    private static func appendCollapsed(_ out: inout String, _ segment: String) {
        // Drop the incoming segment's leading spaces when the merged text
        // already ends in one — the cross-boundary collapse. Everything
        // else is verbatim content (soft hyphens included — rule A / the
        // manual-mode materialization live downstream in the label).
        out += (out.hasSuffix(" ") || out.hasSuffix("\n"))
            ? String(segment.drop(while: { $0 == " " }))
            : segment
    }

    /// Wave 47 (lane Z6) — append one forced line break (a folded `<br>`
    /// member, HTML §4.5.27). css-text-3 §4.1.2(1): collapsible spaces at
    /// the END of a line are removed — everything before a forced break
    /// ends one, so the trailing space run falls before the '\n' lands
    /// (block-ellipsis-004's `Line 2<br>` / ` Line 3` merges to
    /// "Line 2\nLine 3", the exact lines Chromium paints). The
    /// leading-space half of the rule lives in `appendCollapsed` above.
    /// Twin: InlineRunFold.appendBreak.
    private static func appendBreak(_ out: inout String) {
        while out.hasSuffix(" ") { out.removeLast() }
        out += "\n"
    }

    /// Fold `runs` into ONE paragraph, or nil = "keep the stacked
    /// InlineRunPlan path". nil is the ONLY failure mode, and every gated
    /// nil below is logged once per process — the same no-silent-
    /// fallthrough vehicle the rest of the engine uses (PropertyTracker).
    ///
    /// - Parameters:
    ///   - runs: the wire list, verbatim (`meta.runs`).
    ///   - children: the container's IN-FLOW children in the EXACT order
    ///     the caller's ForEach walks (FlexboxApplier.sorted) — the same
    ///     array InlineRunPlan resolves against, so the two folds can
    ///     never disagree about which child an entry names.
    ///   - totalChildCount: ALL composed children including out-of-flow
    ///     boxes. An abspos child's static position depends on the inline
    ///     flow this fold replaces (CSS 2.1 §10.3.7) — deferred, so any
    ///     out-of-flow sibling refuses the whole fold.
    ///   - containerProperties: the container's MERGED, inheritance-
    ///     resolved list — `writing-mode` and `hyphens` are Inherited: yes
    ///     (css-writing-modes-4 §3.2, css-text-3 §5.3) so both usually sit
    ///     on an ancestor and the component's own list would answer wrong.
    ///   - containerLang: the container's COMPUTED content language
    ///     (`meta.lang`, producer-resolved) — the §5.3 gate on `auto`
    ///     adoption, whose dictionary is language-selected. Compared
    ///     verbatim, exactly like the Compose twin's hostEffectiveLang.
    static func fold(runs: [IRRun]?,
                     children: [IRComponent],
                     totalChildCount: Int,
                     containerProperties: [IRProperty],
                     containerLang: String?) -> Folded? {
        // Absent / childless is the overwhelming common case (every
        // non-runs component) — silent nil, mirroring InlineRunPlan.
        guard let runs, !runs.isEmpty, !children.isEmpty else { return nil }
        // Vertical / sideways writing modes flow on the block axis and
        // this fold reuses the HORIZONTAL leaf machinery — refuse before
        // touching members (css-writing-modes-4 §3; the upright path owns
        // VerticalUprightTextFlowLayout).
        if WritingModeExtractor.extract(from: containerProperties)?.isVertical == true {
            return bail("vertical", "vertical writing mode — horizontal fold refused")
        }
        // Wave 45 (X1) host gate, from the Compose twin: text-transform is
        // a string rewrite the label applies to the WHOLE merged text; a
        // length-changing transform would move member boundaries, and no
        // corpus runs host carries one, so the fold declines to model it.
        if containerProperties.contains(where: { $0.type == "TextTransform" }) {
            return bail("host-text-transform", "text-transform host — merged rewrite unmodelled")
        }
        // The host's DECLARED hyphens keyword (nil = undeclared, which is
        // what arms member ADOPTION below). Read through the production
        // extractor — the same lowercased read StyleBuilder feeds the
        // label with, so the fold and the renderer can never disagree.
        let hostHyphens = HyphensExtractor.extract(from: containerProperties)?.mode
        // Two lookup keys, first-occurrence-wins — the identical contract
        // InlineRunPlan.resolve documents: the reference is the child's
        // AUTHORING KEY (spec 03 §4.1), `name` consulted before `id`.
        var byName: [String: Int] = [:]
        var byId: [String: Int] = [:]
        for (index, child) in children.enumerated() {
            // First occurrence wins on duplicates (convert-time error upstream).
            if !child.name.isEmpty, byName[child.name] == nil { byName[child.name] = index }
            if !child.id.isEmpty, byId[child.id] == nil { byId[child.id] = index }
        }
        // The fold walk: text appends with boundary collapsing (whitespace-
        // only runs are the inter-run word space and MUST survive —
        // InlineRunPlan rule 6); child refs must land in strictly
        // increasing rendered order.
        var out = ""
        // Last claimed child index — the strictly-increasing proof.
        var lastIndex = -1
        // How many children the plan consumed (must be ALL of them —
        // dropped EMPTY members count: the plan consumed their slot too).
        var claimed = 0
        // Admitted EMPTY members dropped (reported via Folded, never silent).
        var dropped = 0
        // The member-adopted hyphens keyword (first declaration wins; a
        // later DIFFERENT one bails inside the member gate).
        var adopted: String? = nil
        // Wave 47 (lane Z6) — the styled-span collectors: member ranges
        // (wire order) plus the BR / glyph counts the stacked-equivalence
        // rule below reads.
        var spans: [Span] = []
        var breakMembers = 0
        var glyphMembers = 0
        for run in runs {
            // A text entry: zero-length contributes no glyphs and no box.
            if let text = run.text {
                if !text.isEmpty { appendCollapsed(&out, text) }
                continue
            }
            // Exactly-one-member is decoder-enforced; a both-nil entry is
            // unreachable wire, skipped defensively like InlineRunPlan.
            guard let key = run.child else { continue }
            // A dangling key means glyph-bearing content this composition
            // cannot see — refuse rather than flow a paragraph with a
            // hole in it (the stacked path skips the same entry, but it
            // renders each surviving box separately so nothing merges).
            guard let index = byName[key] ?? byId[key] else {
                return bail("dangling", "child ref '\(key)' not in composition")
            }
            // Out-of-order / duplicate refs cannot be expressed as one
            // forward paragraph — the InlineRunPlan proof, same refusal.
            guard index > lastIndex else {
                return bail("order", "child refs out of rendered order")
            }
            lastIndex = index
            // The member gate (tag ring, structure, property admission,
            // hyphens adoption) — refusals are logged inside.
            let member = children[index]
            switch classify(member, hostHyphens: hostHyphens,
                            containerLang: containerLang,
                            containerProperties: containerProperties,
                            adopted: &adopted) {
            case .glyphs(let style, let losses):
                // The member's glyphs — a nested subtree flattens
                // ({text, <br>} since wave 47; wave 48/W4 adds one-level
                // nested GLYPH members, subelements-002's `<i>e = mc<sup>
                // 2</sup></i>`), flat text appends with boundary
                // collapsing — and the span(s) record exactly what landed.
                let start = out.count
                // Empty-but-present runs ([] survives decode) are NOT a
                // nested tree — the member's own text is the content
                // (matches the Compose twin's isNullOrEmpty predicate;
                // routing [] through the flatten would vanish the glyphs).
                if member.children?.isEmpty == false || member.meta?.runs?.isEmpty == false {
                    switch flattenNestedRuns(member, outerStyle: style,
                                             outerLosses: losses,
                                             hostProperties: containerProperties,
                                             into: &out, spans: &spans) {
                    // A named wall — the whole fold refuses, as ever.
                    case .bail: return nil
                    // Wave 48 (lane W4): a nested glyph member rode — the
                    // flatten emitted PER-PIECE spans (outer style over
                    // the member's own text, composed style over each
                    // nested member) so ranges carry distinct attribution.
                    case .spansEmitted: break
                    // {text, <br>}-only subtree: the wave-47 single
                    // whole-range span below, BYTE-IDENTICALLY (its '\n'
                    // coverage pins the passing block-ellipsis captures).
                    case .noNestedGlyphs:
                        if !style.isPlain || !losses.isEmpty {
                            spans.append(Span(start: start, end: out.count,
                                              style: style, statedLossTypes: losses))
                        }
                    }
                } else {
                    appendCollapsed(&out, member.text ?? "")
                    // Only real attribution (or a stated loss to report)
                    // emits a span — plain members render byte-identically
                    // to wave 44.
                    if !style.isPlain || !losses.isEmpty {
                        spans.append(Span(start: start, end: out.count,
                                          style: style, statedLossTypes: losses))
                    }
                }
                glyphMembers += 1
                claimed += 1
            case .forcedBreak:
                // Wave 47 (lane Z6): '\n', spaces falling on both sides
                // (css-text-3 §4.1.2 — appendBreak's banner).
                appendBreak(&out)
                breakMembers += 1
                claimed += 1
            case .droppedEmpty:
                // No glyphs; the slot is still consumed by the plan.
                dropped += 1
                claimed += 1
            case .refused:
                return nil
            }
        }
        // ALL children must be consumed — an unreferenced or out-of-flow
        // sibling would render AGAIN outside the paragraph (double glyphs)
        // or lose its flow-dependent static position (abspos, deferred).
        guard claimed == children.count, claimed == totalChildCount else {
            return bail("unclaimed", "plan claims \(claimed) of " +
                "\(children.count) in-flow / \(totalChildCount) total children")
        }
        // Wave 47 (lane Z6) — BR STACKED-EQUIVALENCE: a {text, <br>}*-only
        // host needs no fold: the stacked fallback ALREADY renders one
        // label per anonymous run — exactly the forced-break line
        // structure — and that shape is what every committed capture of
        // those hosts pins (block-ellipsis-002 passes on both natives
        // today). The break ring therefore only rides alongside a glyph
        // member — the corpus-simulated rule that keeps the wave-47 flip
        // set to exactly the 4 styled victims.
        // Wave 48 (lane W4) RE-MEASURED the rule over wave48-cal before
        // keeping it: lifting it flips 29 host decisions across 27
        // tests, ~16 of them PASSING, while the failing rest fail on
        // defects a fold cannot touch — the rule stands on evidence
        // (the Compose twin carries the per-test enumeration).
        if breakMembers > 0, glyphMembers == 0 {
            return bail("br-stacked-equivalent",
                        "text-and-br-only host — the stacked fallback already " +
                        "renders the forced-break line structure")
        }
        // Wave 45 (X1) host gate, from the Compose twin: tab-size
        // expansion downstream rewrites '\t' into spaces and would shift
        // member boundaries; no corpus runs host carries a tab today.
        guard !out.contains("\t") else {
            return bail("contains-tab", "tab in merged text — tab-size expansion unmodelled")
        }
        // Wave 47 (lane Z6) host gate, kept byte-parallel with the twin:
        // small-caps case synthesis is a string rewrite outside the span
        // alignment's op set (Compose's synthesizeSmallCaps; inert here —
        // iOS small-caps is a font feature — but a shared gate keeps the
        // two folds answering identically for every host).
        if !spans.isEmpty,
           containerProperties.contains(where: { $0.type == "FontVariantCaps" }) {
            return bail("styled-host-font-variant",
                        "font-variant-caps host — span alignment unmodelled")
        }
        // A glyphless fold says nothing the empty container does not —
        // the stacked fallback's empty boxes are the established shape
        // (breaks-only counts as glyphless too, wave 47).
        guard out.contains(where: { $0 != "\n" }) else {
            return bail("empty-merge", "merged paragraph is empty")
        }
        // The faithful fold (spans in wire order — see Span's contract).
        return Folded(text: out, adoptedHyphensMode: adopted,
                      droppedEmptyMembers: dropped, spans: spans)
    }

    /// The nested-flatten verdict (wave 48, lane W4). `noNestedGlyphs`
    /// keeps the caller's wave-47 single whole-range span — that span
    /// COVERS the flattened '\n's, and re-emitting {text, <br>} shapes
    /// per-piece could move the PASSING block-ellipsis-004/005/006
    /// captures (the Compose twin's fontSize-over-'\n' line-height
    /// participation; mirrored here so the two folds emit identical span
    /// lists); `spansEmitted` says a nested glyph member rode and the
    /// flatten emitted per-piece spans itself.
    private enum Flattened {
        case bail
        case noNestedGlyphs
        case spansEmitted
    }

    /// Flatten a glyph member's OWN `meta.runs` into `out`. Two nested
    /// shapes are admitted, every nested child claimed in strictly
    /// increasing order (the member's `text` is the concatenation the
    /// runs were split FROM — spec 03 §4.1 — and is deliberately
    /// ignored; the runs are authoritative):
    ///   • wave 47 (lane Z6): BR members ({text, `<br>`} —
    ///     block-ellipsis-004's `<span>Line 3<br>Line 4</span>`);
    ///   • wave 48 (lane W4): ONE-LEVEL nested GLYPH members admitted by
    ///     the span ring (subelements-002's `<i>e = mc<sup>2</sup></i>`,
    ///     inset-005's `<u>ultra-<sup>quick</sup> b<sub>row</sub>n</u>`),
    ///     each folded with `InlineSpanRing.composeNested`'s composition
    ///     of the outer member's style — deeper nesting stays a wall.
    /// When a nested glyph member rides, the flatten emits PER-PIECE
    /// spans into `spans` (outer style over the member's own text
    /// pieces, composed style over each nested member's) and answers
    /// `.spansEmitted`; otherwise `.noNestedGlyphs` and the caller keeps
    /// the wave-47 whole-range emission byte-identically. Named walls
    /// log and answer `.bail`. Twin: InlineRunFold.flattenNestedRuns.
    private static func flattenNestedRuns(_ member: IRComponent,
                                          outerStyle: InlineSpanRing.Style,
                                          outerLosses: [String],
                                          hostProperties: [IRProperty],
                                          into out: inout String,
                                          spans: inout [Span]) -> Flattened {
        // Children without a runs order is a shape the wire never emits
        // for text members — refuse with the wave-44 reason.
        guard let runs = member.meta?.runs else {
            _ = bail("nested-member", "member carries children without runs")
            return .bail
        }
        let kids = member.children ?? []
        // The child's AUTHORING KEY resolves `name` before `id` —
        // InlineRunPlan's exact contract, first occurrence wins.
        var byName: [String: Int] = [:]
        var byId: [String: Int] = [:]
        for (i, kid) in kids.enumerated() {
            if !kid.name.isEmpty, byName[kid.name] == nil { byName[kid.name] = i }
            if !kid.id.isEmpty, byId[kid.id] == nil { byId[kid.id] = i }
        }
        // The per-piece attributions, collected over the walk — emitted
        // into `spans` only when a nested glyph member makes the member's
        // attribution non-uniform (see `Flattened`'s banner).
        var pieces: [(start: Int, end: Int, style: InlineSpanRing.Style, losses: [String])] = []
        var sawNestedGlyph = false
        // Strictly-increasing claim walk — a duplicate or out-of-order
        // ref cannot be one forward paragraph (InlineRunPlan's proof).
        var last = -1
        var claimed = 0
        for run in runs {
            if let text = run.text {
                // The member's own glyphs carry the OUTER style.
                let a = out.count
                if !text.isEmpty { appendCollapsed(&out, text) }
                pieces.append((a, out.count, outerStyle, outerLosses))
                continue
            }
            // A both-nil entry is unreachable wire — skip defensively.
            guard let key = run.child else { continue }
            guard let idx = byName[key] ?? byId[key] else {
                _ = bail("nested-dangling", "nested child ref '\(key)' unresolved")
                return .bail
            }
            guard idx > last else {
                _ = bail("nested-order", "nested child refs out of order")
                return .bail
            }
            last = idx
            let kid = kids[idx]
            let kidTag = (kid.meta?.sourceTag ?? "").lowercased()
            // ── wave 47: a nested BR member — one forced '\n' ───────────
            // Fix lane F5 (S5 twin hygiene): `runs?.isEmpty != false`
            // admits an empty-but-present [] exactly like the Kotlin
            // twin's `isNullOrEmpty` (InlineRunFold's nested-BR arm) —
            // the previous `== nil` bailed on [] where Compose folded.
            // Zero corpus components carry []; alignment, not behavior.
            if kidTag == "br" || kid.meta?.role == "line-break" {
                guard kid.children?.isEmpty != false, kid.meta?.runs?.isEmpty != false,
                      kid.meta?.decorations == nil else {
                    _ = bail("br-member-structure", "nested <br> carries structure")
                    return .bail
                }
                if let offending = kid.properties.first(where: { !brMemberTypes.contains($0.type) }) {
                    _ = bail("member-prop", "nested <br> declares '\(offending.type)'")
                    return .bail
                }
                appendBreak(&out)
                claimed += 1
                continue
            }
            // ── wave 48 (lane W4): a nested GLYPH member ────────────────
            // Only span-ring tags may nest; anything else keeps the
            // nested-tree wall, with the tag named for the log.
            guard !kidTag.isEmpty, InlineSpanRing.styledMemberTags.contains(kidTag) else {
                _ = bail("nested-member",
                         "nested member tag '\(kidTag.isEmpty ? "none" : kidTag)' outside the span ring")
                return .bail
            }
            // ONE level only: a nested member with its own structure is
            // the depth-2 wall (no corpus shape needs it — refusing keeps
            // decorating-box-001-class trees on their frozen stacked path).
            // Fix lane F5 (S5 twin hygiene): `runs?.isEmpty != false`
            // treats an empty-but-present [] as NO structure, exactly the
            // Kotlin twin's `isNullOrEmpty` depth-2 guard — `== nil`
            // walled [] where Compose admitted. Zero corpus presence.
            guard kid.children?.isEmpty != false, kid.meta?.runs?.isEmpty != false,
                  kid.meta?.decorations == nil else {
                _ = bail("nested-member-depth", "nested member carries structure — depth-2 wall")
                return .bail
            }
            // A nested Hyphens declaration would need the adoption walk
            // this flatten does not run (admit() skips the type) — refuse
            // rather than drop a policy silently; zero corpus presence.
            guard !kid.properties.contains(where: { $0.type == "Hyphens" }) else {
                _ = bail("nested-member-hyphens", "nested member declares hyphens")
                return .bail
            }
            // Admission through the ring. The effective-ink chain for the
            // TextDecorationColor gate is nested → OUTER member → host,
            // so the outer's list is consulted before the host's.
            switch InlineSpanRing.admit(tag: kidTag, properties: kid.properties,
                                        hostProperties: member.properties + hostProperties) {
            case .refused(let reason):
                _ = bail("member-prop", reason)
                return .bail
            case .admitted(let kidStyle, let kidLosses):
                // Compose the nested style onto the outer's — nil is the
                // compound-shift wall (composeNested's banner).
                guard let composed = InlineSpanRing.composeNested(outer: outerStyle,
                                                                  nested: kidStyle) else {
                    _ = bail("nested-vertical-align-compound",
                             "nested sup/sub inside a shifted member — flat spans cannot add shifts")
                    return .bail
                }
                // The nested member's glyphs, boundary-collapsed like all.
                let a = out.count
                if let kidText = kid.text, !kidText.isEmpty { appendCollapsed(&out, kidText) }
                pieces.append((a, out.count, composed, outerLosses + kidLosses))
                sawNestedGlyph = true
                claimed += 1
            }
        }
        // Every nested child must be claimed — an unclaimed one would
        // silently vanish (the outer fold consumes the whole member).
        guard claimed == kids.count else {
            _ = bail("nested-unclaimed", "nested runs claim \(claimed) of \(kids.count) children")
            return .bail
        }
        // {text, <br>}-only: the caller's wave-47 whole-range span stands.
        guard sawNestedGlyph else { return .noNestedGlyphs }
        // Per-piece emission — one span per attributed piece, in string
        // order (plain, loss-less pieces emit nothing, the wave-44 rule).
        for piece in pieces where piece.start < piece.end {
            if !piece.style.isPlain || !piece.losses.isEmpty {
                spans.append(Span(start: piece.start, end: piece.end,
                                  style: piece.style, statedLossTypes: piece.losses))
            }
        }
        return .spansEmitted
    }

    /// One member through the widened gate: TEXT ring, EMPTY ring, or a
    /// named refusal. `adopted` carries the paragraph's member-adopted
    /// hyphens keyword across members (first wins; disagreement bails).
    /// Byte-parallel with the member arm of InlineRunFold.fold.
    private static func classify(_ member: IRComponent,
                                 hostHyphens: String?,
                                 containerLang: String?,
                                 containerProperties: [IRProperty],
                                 adopted: inout String?) -> MemberOutcome {
        // ── Wave 47 (lane Z6) — the BR RING ─────────────────────────────
        // `<br>` is a FORCED line break (HTML §4.5.27), i.e. '\n' in the
        // merged paragraph, spaces falling on both sides (css-text-3
        // §4.1.4 — appendBreak's banner). The extractor tags it
        // `role: line-break`, read alongside the tag so both producers
        // agree. Note the fold-level stacked-equivalence rule: a break
        // only engages alongside a glyph member.
        let tag = (member.meta?.sourceTag ?? "").lowercased()
        if tag == "br" || member.meta?.role == "line-break" {
            // A break with structure is not a break we understand.
            // Fix lane F5 (S5 twin hygiene, same defect class as the two
            // flatten-walk sites): `runs?.isEmpty != false` admits an
            // empty-but-present [] like the Kotlin twin's `isNullOrEmpty`
            // (InlineRunFold.fold's BR arm) — `== nil` refused [] here.
            guard member.children?.isEmpty != false, member.meta?.runs?.isEmpty != false,
                  member.meta?.decorations == nil else {
                _ = bail("br-member-structure", "<br> member carries structure")
                return .refused
            }
            // Width/Height are the converter's measured break box —
            // geometry the '\n' expresses; anything else refuses.
            if let offending = member.properties.first(where: { !brMemberTypes.contains($0.type) }) {
                _ = bail("member-prop", "<br> member declares '\(offending.type)'")
                return .refused
            }
            return .forcedBreak
        }
        // A tagless member is not a known inline text element — refuse
        // (mirrors the twin's `member-tag:none`). Wave 48 (lane W4)
        // MEASURED this wall before keeping it: the converter omits
        // sourceTag exactly for `<div>`s, so the corpus's 75 tagless
        // members are BLOCK boxes whose stacked anonymous-block rendering
        // is the correct CSS 2.1 §9.2.1.1 structure. Simulation semantics
        // (S5-corrected, fix lane F5): admitting a tagless glyph member
        // THROUGH THE RING AS A SPAN (the strict reading — property
        // admission included, not just plain text) flips TWO fold
        // decisions over wave48-cal — display-contents-flex-001 AND
        // block-ellipsis-003, both PASSERS — so the wall stands on
        // evidence, not caution.
        if tag.isEmpty {
            _ = bail("member-tag", "unsupported member tag 'none'")
            return .refused
        }
        // The decoration / marker wires attach to the member's OWN box;
        // folding its glyphs into the parent label would drop that ink
        // (PlaceholderLabel paints the PARENT's wire over the paragraph).
        guard member.meta?.decorations == nil, member.meta?.markerText == nil else {
            _ = bail("decorated-member", "member carries decoration/marker wire")
            return .refused
        }
        // Wave 47 (lane Z6): a member with its own children/runs is a
        // nested inline TREE — foldable ONLY as a glyph member whose
        // nested nodes are all BR members ({text, <br>} — validated by
        // `flattenNestedRuns` at the walk). EMPTY means no glyphs AND no
        // structure, exactly like the Compose twin.
        // Empty-but-present runs ([] survives decode) are NOT a nested
        // tree (the Compose twin's isNullOrEmpty predicate, mirrored).
        let hasNested = member.children?.isEmpty == false || member.meta?.runs?.isEmpty == false
        let text = member.text
        let isEmpty = text?.isEmpty != false && !hasNested
        if isEmpty {
            // ── The wave-44/45 EMPTY arm, unchanged ─────────────────────
            guard emptyMemberTags.contains(tag) else {
                _ = bail("member-tag", "unsupported member tag '\(tag)'")
                return .refused
            }
            if let offending = member.properties.first(where: { !emptyMemberInertTypes.contains($0.type) }) {
                _ = bail("member-prop", "member declares '\(offending.type)' — " +
                         "styled-span / atom ring deferred")
                return .refused
            }
        } else {
            // ── Wave 47 (lane Z6) — the GLYPH member arm ────────────────
            // The styled tag ring (the wave-44 no-UA-ink trio plus `u`,
            // whose UA underline the span models).
            guard InlineSpanRing.styledMemberTags.contains(tag) else {
                _ = bail("member-tag", "unsupported member tag '\(tag)'")
                return .refused
            }
            // Property admission through the span ring — PLAIN members
            // (Hyphens / paint-inert colors only) pass with no
            // attribution, exactly the wave-44 gate. The container's
            // property list is the effective-ink base for the
            // TextDecorationColor equality gate (the seam passes the
            // MERGED list, where an inherited `color` arrives).
            switch InlineSpanRing.admit(tag: tag, properties: member.properties,
                                        hostProperties: containerProperties) {
            case .refused(let reason):
                _ = bail("member-prop", reason)
                return .refused
            case .admitted(let style, let losses):
                // Hyphens contribution (shared with the empty arm below)
                // — a conflict refuses before anything is appended.
                guard adoptHyphens(member, hostHyphens: hostHyphens,
                                   containerLang: containerLang,
                                   adopted: &adopted) else { return .refused }
                return .glyphs(style, statedLossTypes: losses)
            }
        }
        // The EMPTY arm's Hyphens contribution (banner's adoption rules).
        guard adoptHyphens(member, hostHyphens: hostHyphens,
                           containerLang: containerLang,
                           adopted: &adopted) else { return .refused }
        // Every gate passed: a counted drop (glyph members returned above).
        return .droppedEmpty
    }

    /// The css-text-3 §5.3 hyphens ADOPTION walk, shared by the EMPTY and
    /// glyph member arms (hoisted verbatim from the wave-45 classify).
    /// Returns false after logging when no single paragraph mode is
    /// faithful; `adopted` carries the winner across members.
    private static func adoptHyphens(_ member: IRComponent,
                                     hostHyphens: String?,
                                     containerLang: String?,
                                     adopted: inout String?) -> Bool {
        // No declaration — nothing to adopt, nothing to conflict with.
        guard let memberProp = member.properties.first(where: { $0.type == "Hyphens" }) else {
            return true
        }
        // The member's declared mode (nil for unparsable wire — then
        // treated like the twin's null keyword: it participates as
        // "no recognisable declaration", conflicting with nothing).
        let mode = ValueExtractors.extractKeyword(memberProp.data)?.lowercased()
        if let host = hostHyphens {
            // Host declared: a member may only AGREE with it — a
            // different mode has no single faithful paragraph mode.
            if mode != host {
                _ = bail("hyphens-conflict",
                         "member hyphens '\(mode ?? "?")' vs host '\(host)'")
                return false
            }
        } else if adopted == nil {
            // `auto` is dictionary-driven per language — adopt only
            // when the member's language IS the paragraph's (a nil
            // member lang inherits the host's, trivially equal;
            // §5.3 "appropriate to the language of the text").
            if mode == "auto", let memberLang = member.meta?.lang,
               memberLang != containerLang {
                _ = bail("hyphens-lang-divergence",
                         "member lang '\(memberLang)' vs paragraph " +
                         "'\(containerLang ?? "nil")' — no single dictionary")
                return false
            }
            // First member declaration becomes the paragraph mode.
            adopted = mode
        } else if adopted != mode {
            // Two members disagreeing: no single mode is faithful.
            _ = bail("hyphens-conflict",
                     "members disagree: '\(adopted ?? "?")' vs '\(mode ?? "?")'")
            return false
        }
        return true
    }

    /// Log the named wall once per process and refuse. Returning through
    /// one funnel keeps every bail observable (repo rule: no silent
    /// fallthrough) and greppable under one key prefix.
    private static func bail(_ key: String, _ message: String) -> Folded? {
        _ = PropertyTracker.logOnce(key: "inline-run-flow:\(key)",
                                    message: "inline-run flow bail — " + message)
        return nil
    }
}
