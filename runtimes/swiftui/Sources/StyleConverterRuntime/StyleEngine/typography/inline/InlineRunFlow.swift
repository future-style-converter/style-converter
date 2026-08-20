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
//    • HYPHENS ADOPTION (css-text-3 §6.1): a member's `hyphens` governs
//      break opportunities inside the member's text, but the pre-break
//      takes ONE mode per paragraph — so when the container declares none
//      the first member declaration is ADOPTED for the whole paragraph
//      (exact for the victims: their host text is break-free), reported
//      via `Folded.adoptedHyphensMode`. A member contradicting a host
//      declaration, or two members disagreeing, bail: no single mode is
//      faithful. `auto` adoption additionally requires the member's
//      language to equal the paragraph's — the dictionary is selected per
//      language (§6.1 "appropriate to the language of the text").
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
//  HONEST SCOPE — every refusal is a LOGGED bail to the wave-32 stacked
//  path (no silent fallthrough), so an engaged fold can never render less
//  than the plan did. Still deferred, each behind its own named wall:
//  paint-styled TEXT members (own Color etc. — need per-segment
//  attribution through the pre-break), atoms with GLYPHS or painted boxes,
//  `<br>` forced breaks, block-level members, nested member trees, and
//  out-of-flow siblings (float/abspos static position).
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

    /// One admitted member's contribution to the paragraph walk.
    private enum MemberOutcome {
        /// A TEXT member's glyphs, appended with boundary collapsing.
        case contributes(String)
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
    private static func appendCollapsed(_ out: inout String, _ segment: String) {
        // Drop the incoming segment's leading spaces when the merged text
        // already ends in one — the cross-boundary collapse. Everything
        // else is verbatim content (soft hyphens included — rule A / the
        // manual-mode materialization live downstream in the label).
        out += out.hasSuffix(" ")
            ? String(segment.drop(while: { $0 == " " }))
            : segment
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
    ///     (css-writing-modes-4 §3.1, css-text-3 §6.1) so both usually sit
    ///     on an ancestor and the component's own list would answer wrong.
    ///   - containerLang: the container's COMPUTED content language
    ///     (`meta.lang`, producer-resolved) — the §6.1 gate on `auto`
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
            switch classify(children[index], hostHyphens: hostHyphens,
                            containerLang: containerLang, adopted: &adopted) {
            case .contributes(let text):
                // The member's glyphs, boundary-collapsed like run text.
                appendCollapsed(&out, text)
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
        // Wave 45 (X1) host gate, from the Compose twin: tab-size
        // expansion downstream rewrites '\t' into spaces and would shift
        // member boundaries; no corpus runs host carries a tab today.
        guard !out.contains("\t") else {
            return bail("contains-tab", "tab in merged text — tab-size expansion unmodelled")
        }
        // A glyphless fold says nothing the empty container does not —
        // the stacked fallback's empty boxes are the established shape.
        guard !out.isEmpty else {
            return bail("empty-merge", "merged paragraph is empty")
        }
        // The faithful fold.
        return Folded(text: out, adoptedHyphensMode: adopted,
                      droppedEmptyMembers: dropped)
    }

    /// One member through the widened gate: TEXT ring, EMPTY ring, or a
    /// named refusal. `adopted` carries the paragraph's member-adopted
    /// hyphens keyword across members (first wins; disagreement bails).
    /// Byte-parallel with the member arm of InlineRunFold.fold.
    private static func classify(_ member: IRComponent,
                                 hostHyphens: String?,
                                 containerLang: String?,
                                 adopted: inout String?) -> MemberOutcome {
        // `<br>` is a FORCED line break (HTML §4.5.27), not an atom — a
        // real candidate for "\n" in a future ring, but its reference
        // population is unstudied pixel-wise, so it keeps its own named
        // wall instead of riding the generic tag bail.
        let tag = (member.meta?.sourceTag ?? "").lowercased()
        if tag == "br" {
            _ = bail("br-member", "<br> forced break member — deferred ring")
            return .refused
        }
        // A tagless member is not a known inline text element — refuse
        // (mirrors the twin's `member-tag:none`).
        if tag.isEmpty {
            _ = bail("member-tag", "unsupported member tag 'none'")
            return .refused
        }
        // A member with its own children or its own runs is a nested
        // inline TREE; flattening it here would guess an order the wire
        // spells out one level down — deferred until the fold recurses.
        guard member.children?.isEmpty != false, member.meta?.runs == nil else {
            _ = bail("nested-member", "member carries its own children/runs")
            return .refused
        }
        // The decoration / marker wires attach to the member's OWN box;
        // folding its glyphs into the parent label would drop that ink
        // (PlaceholderLabel paints the PARENT's wire over the paragraph).
        guard member.meta?.decorations == nil, member.meta?.markerText == nil else {
            _ = bail("decorated-member", "member carries decoration/marker wire")
            return .refused
        }
        // Empty vs text member decides the tag ring and property set
        // (empty string is "extracted, was empty" — same zero glyphs).
        let text = member.text
        let isEmpty = text?.isEmpty != false
        // Tag admission per member family (see the two rings above).
        guard (isEmpty ? emptyMemberTags : textMemberTags).contains(tag) else {
            _ = bail("member-tag", "unsupported member tag '\(tag)'")
            return .refused
        }
        // Property admission — the first unsupported type names the bail
        // so the log can be audited per member (twin: `member-prop:`).
        let allowed = isEmpty ? emptyMemberInertTypes : textMemberTypes
        if let offending = member.properties.first(where: { !allowed.contains($0.type) }) {
            _ = bail("member-prop", "member declares '\(offending.type)' — " +
                     "styled-span / atom ring deferred")
            return .refused
        }
        // Hyphens contribution (the banner's adoption rules; css-text-3
        // §6.1). The keyword is lowercased exactly like HyphensExtractor
        // lowercases the container's, so the two compare in one spelling.
        if let memberProp = member.properties.first(where: { $0.type == "Hyphens" }) {
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
                    return .refused
                }
            } else if adopted == nil {
                // `auto` is dictionary-driven per language — adopt only
                // when the member's language IS the paragraph's (a nil
                // member lang inherits the host's, trivially equal;
                // §6.1 "appropriate to the language of the text").
                if mode == "auto", let memberLang = member.meta?.lang,
                   memberLang != containerLang {
                    _ = bail("hyphens-lang-divergence",
                             "member lang '\(memberLang)' vs paragraph " +
                             "'\(containerLang ?? "nil")' — no single dictionary")
                    return .refused
                }
                // First member declaration becomes the paragraph mode.
                adopted = mode
            } else if adopted != mode {
                // Two members disagreeing: no single mode is faithful.
                _ = bail("hyphens-conflict",
                         "members disagree: '\(adopted ?? "?")' vs '\(mode ?? "?")'")
                return .refused
            }
        }
        // Every gate passed: glyphs for the paragraph, or a counted drop.
        return isEmpty ? .droppedEmpty : .contributes(text ?? "")
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
