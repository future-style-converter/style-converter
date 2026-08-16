//
//  InlineRunFlow.swift
//  StyleEngine/typography/inline — Wave 44, lane U2 (the inline-run wall,
//  iOS half).
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
//  THE FIX. When every referenced child is a metrics-uniform plain inline
//  text span, the container's inline content IS one paragraph: this fold
//  concatenates the runs — parent text and member text interleaved exactly
//  as the wire orders them — and the caller renders the result through the
//  EXACT leaf-text machinery (PlaceholderLabel: GreedyLineBreaker greedy
//  pre-break, LineBoxMetrics pinned line boxes, SoftHyphenPolicy /
//  WordBreakOpportunities soft-hyphen ops). That machinery is the whole
//  reason this shape works: a member's soft hyphens (hyphens-manual-011's
//  `Deoxy\u{00AD}ribo\u{00AD}nucleic`) survive the fold verbatim and the
//  pre-break takes them exactly as it does for leaf text.
//
//  WHY NOT A CUSTOM Layout OR CTFramesetter. SwiftUI Layout subviews are
//  atomic — no API breaks a subview's Text at a measured offset, and
//  hyphens-010's ref splits INSIDE the span's text ("Deoxyribon|ucleic") —
//  so a Layout can only place atoms, not flow text (it stays the vehicle
//  for the atom ring, deferred below). CTTypesetterSuggestLineBreak is not
//  Chromium-greedy either (the GreedyLineBreaker banner's push-out story),
//  so a CoreText re-implementation would still need the same pre-break
//  while duplicating font resolution, decorations and pins.
//
//  HONEST SCOPE — every refusal is a LOGGED bail to the wave-32 stacked
//  path (no silent fallthrough), so an engaged fold can never render less
//  than the plan did:
//    • members: plain `<span>`s with non-empty text, no own children/runs,
//      no decoration/marker wire, and no declared style except a `hyphens`
//      keyword equivalent to the container's — the metrics-uniformity that
//      makes ONE measurement font honest for the whole paragraph;
//    • paint-styled spans (own Color etc.) are the NAMED next ring — they
//      need per-segment attribution through the pre-break, not a bail-less
//      fold that would ink them with the parent's color;
//    • atoms (empty inlines with painted boxes — inherit-computed-001's
//      bordered `<em>`), `<br>` forced breaks, block-level members, and
//      out-of-flow siblings (float/abspos static position) stay deferred.
//
//  Twin: the Compose half of this wall is wave-44 lane U1's module; the
//  shared contract is "fold to one paragraph, reuse the platform's pinned
//  leaf-text path". Pure + Foundation-only so XCTest pins the decision on
//  the VERBATIM victim payloads without a render surface.
//

import Foundation

enum InlineRunFlow {

    /// The member-tag ring this wave serves: `<span>` only. It is the
    /// dominant referenced tag in the corpus (105 of ~500 references at
    /// wave43-final) and the only one whose UA stylesheet adds NOTHING the
    /// IR does not carry — `em`/`i` (UA italic), `u`/`a` (UA underline /
    /// link ink) and `sub`/`sup` (UA font-size + baseline shift) all style
    /// their glyphs in ways the wire omits, so folding them here would
    /// bake today's producer gap into the flow. Widening the ring is a
    /// per-tag audit, not a list edit.
    private static let inlineTags: Set<String> = ["span"]

    /// Fold `runs` into ONE paragraph string, or nil = "keep the stacked
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
    static func fold(runs: [IRRun]?,
                     children: [IRComponent],
                     totalChildCount: Int,
                     containerProperties: [IRProperty]) -> String? {
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
        // The container's EFFECTIVE hyphens mode, the equivalence anchor
        // for member declarations: absent = `manual`, the css-text-3 §6.1
        // initial value (HyphensExtractor lowercases the wire keyword).
        let containerHyphens = HyphensExtractor.extract(from: containerProperties)?.mode
            ?? "manual"
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
        // The fold walk: text appends verbatim (whitespace-only runs are
        // the inter-run word space and MUST survive — InlineRunPlan rule
        // 6); child refs must land in strictly increasing rendered order.
        var out = ""
        // Last claimed child index — the strictly-increasing proof.
        var lastIndex = -1
        // How many children the plan consumed (must be ALL of them).
        var claimed = 0
        for run in runs {
            // A text entry: zero-length contributes no glyphs and no box.
            if let text = run.text {
                if !text.isEmpty { out += text }
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
            // The member gate: nil = unsupported kind (logged inside).
            guard let text = memberText(children[index],
                                        containerHyphens: containerHyphens) else {
                return nil
            }
            out += text
            claimed += 1
        }
        // ALL children must be consumed — an unreferenced or out-of-flow
        // sibling would render AGAIN outside the paragraph (double glyphs)
        // or lose its flow-dependent static position (abspos, deferred).
        guard claimed == children.count, claimed == totalChildCount else {
            return bail("unclaimed", "plan claims \(claimed) of " +
                "\(children.count) in-flow / \(totalChildCount) total children")
        }
        // A glyphless fold says nothing the empty container does not.
        guard !out.isEmpty else { return nil }
        return out
    }

    /// One member's contribution to the paragraph, or nil (logged) when
    /// the member is outside this wave's ring. Each refusal names its
    /// wall so the corpus population behind it stays countable in logs.
    private static func memberText(_ member: IRComponent,
                                   containerHyphens: String) -> String? {
        // `<br>` is a FORCED line break (HTML §4.5.27), not an atom — a
        // real candidate for "\n" in a future ring, but its 123-reference
        // population is unstudied pixel-wise, so it gets its own named
        // wall instead of riding the generic atom bail.
        let tag = (member.meta?.sourceTag ?? "").lowercased()
        if tag == "br" {
            return bail("br-member", "<br> forced break member — deferred ring")
        }
        // Only the audited inline-tag ring engages; block tags (`div`,
        // `ol`…) interrupt the inline flow (CSS 2.1 §9.2.1.1 anonymous
        // blocks) and UA-styled inlines (`em`, `u`, `sub`…) would fold
        // without the ink Chromium gives them — see `inlineTags`.
        guard inlineTags.contains(tag) else {
            return bail("member-tag", "unsupported member tag '\(tag)'")
        }
        // An inline with NO text is an ATOM: its visible contribution is
        // its own box (inherit-computed-001's bordered empty `<em>` shape)
        // which a text fold cannot express — the Layout ring's job.
        guard let text = member.text, !text.isEmpty else {
            return bail("atom-member", "empty inline member — atom ring deferred")
        }
        // A member with its own children or its own runs is a nested
        // inline TREE; flattening it here would guess an order the wire
        // spells out one level down — deferred until the fold recurses.
        guard member.children?.isEmpty != false, member.meta?.runs == nil else {
            return bail("nested-member", "member carries its own children/runs")
        }
        // The decoration / marker wires attach to the member's OWN box;
        // folding its glyphs into the parent label would drop that ink
        // (PlaceholderLabel paints the PARENT's wire over the paragraph).
        guard member.meta?.decorations == nil, member.meta?.markerText == nil else {
            return bail("decorated-member", "member carries decoration/marker wire")
        }
        // Declared style ring: `hyphens` only, and only when equivalent
        // to the container's effective mode — the fold measures and
        // breaks the WHOLE paragraph with the container's TextConfig, so
        // any member declaration that changes metrics (font-*), paint
        // (Color — the styled-span ring) or breaking (a DIFFERENT
        // hyphens mode, e.g. hyphens-auto-inline-010's `auto` span in a
        // `manual` container) must refuse.
        for prop in member.properties {
            // css-text-3 §6.1: the member's keyword, normalized like the
            // container's (absent-in-IR never reaches here — presence of
            // the property is what this arm matches).
            if prop.type == "Hyphens" {
                // Same lowercasing the extractor applies to the container.
                let mode = ValueExtractors.extractKeyword(prop.data)?.lowercased()
                // Equivalent declaration (the hyphens-manual-inline shape:
                // span `manual` inside the initial-`manual` div) — inert.
                if mode == containerHyphens { continue }
                return bail("hyphens-mismatch",
                            "member hyphens '\(mode ?? "?")' vs container " +
                            "'\(containerHyphens)' — per-segment breaking deferred")
            }
            // Anything else — Color, font-*, decorations, boxes — is the
            // styled-span / atom ring; folding would mis-ink or mis-size.
            return bail("member-style",
                        "member declares '\(prop.type)' — styled-span ring deferred")
        }
        // Every gate passed: the member is plain paragraph text.
        return text
    }

    /// Log the named wall once per process and refuse. Returning the nil
    /// through one funnel keeps every bail observable (repo rule: no
    /// silent fallthrough) and greppable under one key prefix.
    private static func bail(_ key: String, _ message: String) -> String? {
        _ = PropertyTracker.logOnce(key: "inline-run-flow:\(key)",
                                    message: "inline-run flow bail — " + message)
        return nil
    }
}
