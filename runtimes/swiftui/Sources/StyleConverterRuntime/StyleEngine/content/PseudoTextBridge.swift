//
//  PseudoTextBridge.swift
//  StyleEngine/content — wave 42, lane W2 (the iOS pseudos seam);
//  wave 43, lane V7 (the styled-text extension).
//
//  The PURE half of the ordinary-element generated-content path: one
//  `pseudos.<role>` bucket (the extractor-owned payload the wire forwards
//  VERBATIM, schema/spec/01-envelope.md `pseudos` row) → the inline TEXT
//  that bucket bakes plus the TYPED styling it declares, or nil when the
//  bucket carries nothing this bridge can honestly fold. No SwiftUI
//  types, so every rule is XCTest-pinnable without a simulator — the same
//  split RootPseudoSpec uses for the body-root box family.
//
//  ── WHY THIS EXISTS ──────────────────────────────────────────────────
//  v2 IR components carry baked ::before/::after generated content:
//  `pseudos.before._text` is the extractor's full css-content-3 §2 /
//  css-lists-3 §5 resolution. Web renders it as an inline <span>
//  (runtimes/web/src/renderer/PseudoNodeRenderer.ts); iOS rendered
//  NOTHING for every non-body-root component until wave 42 folded the
//  UNSTYLED population. Wave 43 widens the fold three measured steps:
//   • `display: contents` — css-display-3 §2.5: the pseudo generates NO
//     boxes but its contents render as normal, which is EXACTLY the
//     fold's inline flatten (measured: display-contents-before-after-002
//     renders "AS" on iOS where the ref paints "PASS" — the P/S buckets
//     were refused for this very declaration, ios-ref 0.9735).
//   • box declarations UNDER display:contents — a box-less element's
//     border/background/size do not apply (same §2.5), so the 002/
//     first-letter-001 `border: 100px solid red` is spec-dead, not a
//     refusal reason (and the ref shows no red).
//   • the styling trio (color / font-size / font-family) — converted to
//     TYPED properties by PseudoRunStyle; PseudoTextFold applies them
//     when the run is the component's sole ink (see its uniformity gate).
//
//  ── STILL STRICTLY GATED ─────────────────────────────────────────────
//  Any other declaration refuses the whole bucket (never half-rendered)
//  and every refused declaration is named in the log, per the repo's
//  no-silent-fallthrough rule. The body-root BOX population keeps its
//  dedicated RootPseudoBox path.
//

import Foundation

/// Pure bridge: one `pseudos.<role>` bucket → baked text + typed styling.
// enum: pure namespace — never instantiated (mirrors RootPseudo et al.).
enum PseudoTextBridge {

    /// One foldable pseudo run: the baked inline text plus the TYPED
    /// styling properties its bucket declared (empty for the wave-42
    /// unstyled population — the overwhelmingly common case).
    struct PseudoInlineRun {
        /// The baked glyphs — non-empty by construction.
        let text: String
        /// Typed Color/FontSize/FontFamily entries, wire-shaped for the
        /// component property extractors (see PseudoRunStyle's banner).
        let styling: [IRProperty]
    }

    /// Declarations a text fold can CONSUME or safely treat as inert:
    /// `content` is the very source of the baked `_text` (css-content-3
    /// §2), and the counter machinery (`counter-increment` / `counter-reset`
    /// / `counter-set`, css-lists-3 §4) is already RESOLVED into that text
    /// by the extractor's bake — re-running it here could only disagree.
    static let inertDeclarations: Set<String> = [
        "content", "counter-increment", "counter-reset", "counter-set",
    ]

    /// Box-model declarations that are SPEC-DEAD when the same bucket
    /// declares `display: contents` — css-display-3 §2.5: an element that
    /// generates no boxes has nothing for border/background/size to apply
    /// to (the census carries `border` ×3 in exactly this shape; the
    /// neighbours are listed so the same rule reads uniformly).
    static let boxUnderContents: Set<String> = [
        "border", "background", "background-color", "width", "height",
    ]

    /// The bucket's foldable run, or nil when there is nothing an inline
    /// text run can honestly express (empty content, an unbaked function
    /// value, or any declaration outside the consumable set).
    ///
    /// - Parameters:
    ///   - bucket: the wire object — `{ "properties": {…}, "_text": "…" }`,
    ///     the shape the WPT extractor emits and the decoder forwards
    ///     verbatim (spec 01: `pseudos` payloads are never flattened).
    ///   - role: "before" / "after", for the log lines only.
    ///   - emBasePx: the originating element's font-size in px — the
    ///     em/% base for the pseudo's own `font-size` (css-values-4
    ///     §5.1.1), threaded by PseudoTextFold from the host's properties.
    static func inlineRun(bucket: IRValue?, role: String, emBasePx: Double) -> PseudoInlineRun? {
        // A bucket is an object; anything else is not a pseudo payload.
        guard let b = bucket, b.objectValue != nil else { return nil }
        // The raw CSS declarations map — extractor-owned, spec 01. Absent
        // map = a bare text bucket; tolerated (nothing to gate on).
        let decls = b["properties"]?.objectValue ?? [:]
        // Pre-scan for `display: contents` — it flips the meaning of the
        // box set below, so it must be known before the gate walk. The
        // token test is exact: `contents` is exclusive in the display
        // grammar (css-display-3 §2 — it never pairs the way `block flow`
        // does), so any other token sequence is NOT the box-less case.
        let displayContents = decls.first(where: { entry in
            entry.key.trimmingCharacters(in: .whitespaces).lowercased() == "display"
        }).map { entry in
            (entry.value.stringValue ?? "")
                .trimmingCharacters(in: .whitespaces).lowercased() == "contents"
        } ?? false
        // Gate pass: consume what the fold can honour, name EVERYTHING
        // else, then refuse the whole bucket on any objection — a pseudo
        // asking for a real box or an unparseable style must not be
        // silently flattened into the host's text run.
        var refused = false
        // The typed styling collected from the trio (order-stable: sorted).
        var styling: [IRProperty] = []
        // Sorted so multi-key refusals log in a deterministic order.
        for prop in decls.keys.sorted() {
            // Normalise the key the way RootPseudoSpec does: CSS property
            // names are ASCII case-insensitive (css-syntax-3 §5).
            let key = prop.trimmingCharacters(in: .whitespaces).lowercased()
            // Consumable/inert → no objection from this declaration.
            if inertDeclarations.contains(key) { continue }
            // `display: contents` is the fold's own semantics (banner
            // bullet 1) — consumed; any OTHER display value still refuses
            // below (a block/none pseudo is not an inline text run).
            if key == "display" && displayContents { continue }
            // Box declarations are spec-dead under display:contents
            // (banner bullet 2) — consumed as inert, refused otherwise.
            if boxUnderContents.contains(key) && displayContents { continue }
            // The styling trio converts to typed properties (banner
            // bullet 3); an unconvertible flavour refuses — folding the
            // text with the WRONG ink/size would half-render the bucket.
            if let raw = decls[prop]?.stringValue,
               let conv = PseudoRunStyle.convert(prop: key, raw: raw, emBasePx: emBasePx) {
                switch conv {
                // Typed — collected for the fold's uniformity gate.
                case .typed(let p): styling.append(p); continue
                // CSS-wide keyword = the run's existing default. No-op.
                case .inert: continue
                // Unparseable style value → refuse, named below.
                case .unsupported: break
                }
            }
            // No silent fallthrough: the refused declaration is named once
            // per (role, property) — not per component, so a corpus-wide
            // repeat cannot flood the capture log.
            PropertyTracker.logOnce(
                key: "pseudotext-unsupported-\(role)-\(key)",
                message: "[PseudoText] ::\(role) declaration '\(prop)' is beyond the inline-text fold — bucket not folded")
            // Remember the refusal but keep scanning so every offending
            // declaration gets its own named log line.
            refused = true
        }
        // Any refusal keeps the bucket unrendered — exactly the behaviour
        // this platform had before the seam, now named instead of silent.
        guard !refused else { return nil }
        // The baked literal: `_text` is what the extractor emits today;
        // `text` is the v2 spelling — web tolerates both in exactly this
        // order (PseudoNodeRenderer.ts `p._text ?? p.text`), so mirror it.
        guard let text = b["_text"]?.stringValue ?? b["text"]?.stringValue else {
            // A `content` declaration with NO baked text means the extractor
            // could not resolve it (e.g. `content: counter(foo)` on a
            // display:none subtree that was never laid out) — web renders
            // the empty string for these too; name the gap and fold nothing.
            if decls.keys.contains(where: { $0.trimmingCharacters(in: .whitespaces).lowercased() == "content" }) {
                PropertyTracker.logOnce(
                    key: "pseudotext-unbaked-\(role)",
                    message: "[PseudoText] ::\(role) content has no baked text (_text absent) — rendered as empty")
            }
            return nil
        }
        // `content: ""` bakes an EMPTY string — a legal pseudo that paints
        // no glyphs (css-content-3 §2), so there is nothing to fold.
        return text.isEmpty ? nil : PseudoInlineRun(text: text, styling: styling)
    }
}
