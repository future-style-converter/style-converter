//
//  PseudoTextBridge.swift
//  StyleEngine/content — wave 42, lane W2 (the iOS pseudos seam).
//
//  The PURE half of the ordinary-element generated-content path: one
//  `pseudos.<role>` bucket (the extractor-owned payload the wire forwards
//  VERBATIM, schema/spec/01-envelope.md `pseudos` row) → the inline TEXT
//  that bucket bakes, or nil when the bucket carries nothing this bridge
//  can honestly fold. No SwiftUI types, so every rule is XCTest-pinnable
//  without a simulator — the same split RootPseudoSpec uses for the
//  body-root box family.
//
//  ── WHY THIS EXISTS ──────────────────────────────────────────────────
//  v2 IR components carry baked ::before/::after generated content:
//  `pseudos.before._text` is the extractor's full css-content-3 §2 /
//  css-lists-3 §5 resolution (`content: "B" counter(foo)` → "B7", tagged
//  `_lossyReasons: ["generated-content-baked"]`). Web renders it as an
//  inline <span> (runtimes/web/src/renderer/PseudoNodeRenderer.ts); iOS
//  rendered NOTHING for every non-body-root component — MEASURED in
//  tools/titan/runs/wave41-final/sections/css-lists: the Chromium ref for
//  counter-reset-reversed-pseudo-001 shows "B7A5"/"B4A2" at the top-left,
//  the iOS capture is completely blank (ios-screenshots/…pseudo-001.png).
//
//  ── WHY TEXT-ONLY, AND STRICTLY GATED ────────────────────────────────
//  The natives cannot hand a raw declarations map to a DOM like web can.
//  The corpus splits pseudo buckets into two populations: TEXT pseudos
//  (content + already-baked counter-* — the css-lists/css-counter-styles
//  fail set) and BOX pseudos (display/width/height/background/position —
//  the css-contain and css-anchor-position families). This bridge folds
//  ONLY the first: a bucket declaring anything beyond the consumable set
//  is refused whole (never half-rendered) and every refused declaration
//  is named in the log, per the repo's no-silent-fallthrough rule.
//  The body-root BOX population keeps its dedicated RootPseudoBox path.
//

import Foundation

/// Pure bridge: one `pseudos.<role>` bucket → the baked inline text.
// enum: pure namespace — never instantiated (mirrors RootPseudo et al.).
enum PseudoTextBridge {

    /// Declarations a text fold can CONSUME or safely treat as inert:
    /// `content` is the very source of the baked `_text` (css-content-3
    /// §2), and the counter machinery (`counter-increment` / `counter-reset`
    /// / `counter-set`, css-lists-3 §4) is already RESOLVED into that text
    /// by the extractor's bake — re-running it here could only disagree.
    static let inertDeclarations: Set<String> = [
        "content", "counter-increment", "counter-reset", "counter-set",
    ]

    /// The inline text this bucket bakes, or nil when there is nothing an
    /// inline text run can honestly express (empty content, an unbaked
    /// function value, or any declaration outside the consumable set).
    ///
    /// - Parameters:
    ///   - bucket: the wire object — `{ "properties": {…}, "_text": "…" }`,
    ///     the shape the WPT extractor emits and the decoder forwards
    ///     verbatim (spec 01: `pseudos` payloads are never flattened).
    ///   - role: "before" / "after", for the log lines only.
    static func inlineText(bucket: IRValue?, role: String) -> String? {
        // A bucket is an object; anything else is not a pseudo payload.
        guard let b = bucket, b.objectValue != nil else { return nil }
        // The raw CSS declarations map — extractor-owned, spec 01. Absent
        // map = a bare text bucket; tolerated (nothing to gate on).
        let decls = b["properties"]?.objectValue ?? [:]
        // Gate pass: name EVERY declaration outside the consumable set,
        // then refuse the whole bucket — a pseudo asking for a box
        // (display/width/background) or per-run styling (color/font-*)
        // must not be silently flattened into the host's uniform text run.
        var refused = false
        // Sorted so multi-key refusals log in a deterministic order.
        for prop in decls.keys.sorted() {
            // Normalise the key the way RootPseudoSpec does: CSS property
            // names are ASCII case-insensitive (css-syntax-3 §5).
            let key = prop.trimmingCharacters(in: .whitespaces).lowercased()
            // Consumable/inert → no objection from this declaration.
            guard !inertDeclarations.contains(key) else { continue }
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
        return text.isEmpty ? nil : text
    }
}
