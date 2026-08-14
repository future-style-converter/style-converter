//
//  AutoHyphenation.swift
//  StyleEngine/typography/wrapping — wave 40 (lane T2).
//
//  css-text-3 §6.1 `hyphens: auto`, the DICTIONARY half — the wall
//  HyphensApplier and SoftHyphenPolicy have both been naming since
//  wave 37, now taken down.
//
//  ── WHAT THE OLD WALL ACTUALLY WAS ──────────────────────────────────
//  Two separate claims were being made, and only ONE of them was true:
//
//   (1) "the IR carries no language, so no dictionary can be selected".
//       TRUE until wave 37 (lane W4) shipped THE LANG WIRE — `meta.lang`,
//       the element's COMPUTED content language, already resolved by the
//       producer through HTML §3.2.6.2's own-lang → nearest-ancestor →
//       `<html>` ladder (schema/spec/04-metadata-fields.md). It is on the
//       wire, decoded by IRWireV2Reader, and was simply never read by
//       this runtime.
//
//   (2) "TextKit hyphenation needs NSParagraphStyle on a UIKit label,
//       and ImageRenderer refuses to rasterise platform views".
//       TRUE of `NSAttributedString.hyphenationFactor` and of
//       `usesDefaultHyphenation` — but those were never the only seam.
//       CoreFoundation exposes the hyphenation DICTIONARY itself as a
//       plain string query: `CFStringIsHyphenationAvailableForLocale`
//       + `CFStringGetHyphenationLocationBeforeIndex`, available since
//       iOS 4.2, no view, no layout manager, no ImageRenderer exposure.
//       Ask it where a word may break and the answer is a set of
//       offsets — exactly the input this platform's line breaker needs,
//       because on iOS the LINE BREAKER IS OURS (GreedyLineBreaker
//       pre-breaks every wrappable run with hard newlines; see its
//       banner for why TextKit's own push-out strategy forced that).
//
//  So the ImageRenderer wall is real for TextKit's hyphenator and
//  IRRELEVANT to CoreFoundation's dictionary. That is the whole finding.
//
//  ── THE GATE ────────────────────────────────────────────────────────
//  `engaged` requires BOTH `hyphens: auto` and a non-blank language tag,
//  which is §6.1's own condition ("a hyphenation resource appropriate to
//  the LANGUAGE of the text") and the one WPT css-text/hyphens-auto-001
//  asserts the contrapositive of: "automatic hyphenation must not work
//  without language tagging". `meta.lang` exists only on IR extracted
//  from real HTML — every hand-authored fixture is a CSS envelope with
//  no `_lang` key — so this file cannot move a committed baseline.
//
//  Twin: `AutoHyphenation.kt` (Compose) carries the identical three pure
//  entry points. It has no hyphenator section because Android's breaker
//  is INSIDE `Text`: there the switch is `TextStyle.hyphens = .Auto` +
//  a `LocaleList`, and Minikin consults its own `/system/usr/hyphen-data`
//  patterns. Same decision, different owner of the break.
//

import Foundation

enum AutoHyphenation {

    // MARK: - The decision (byte-parallel with AutoHyphenation.kt)

    /// Does this run ask for — and qualify for — dictionary hyphenation?
    ///
    /// - Parameters:
    ///   - hyphensMode: resolved `hyphens` keyword in either spelling the
    ///     two extractors produce (the wire authors upper-case `"AUTO"`,
    ///     this runtime's HyphensExtractor lowercases).
    ///   - lang: the run's `meta.lang`. nil/blank = the document declares
    ///     no language, which §6.1 leaves with no resource to select, so
    ///     `auto` degrades to `manual`'s explicit opportunities exactly as
    ///     before this file existed.
    static func engaged(_ hyphensMode: String?, _ lang: String?) -> Bool {
        guard hyphensMode?.lowercased() == "auto", let l = lang else { return false }
        return !l.trimmingCharacters(in: .whitespaces).isEmpty
    }

    /// The BCP-47 tag to hand the hyphenator, or nil when not engaged.
    /// Returned VERBATIM apart from surrounding whitespace: BCP-47
    /// matching is case-insensitive and subtag-truncating (RFC 4647
    /// §2.1/§3.4) and `CFLocale` canonicalises at construction, so
    /// normalising here would only lose the wire's round-trip.
    static func localeTag(_ hyphensMode: String?, _ lang: String?) -> String? {
        guard engaged(hyphensMode, lang) else { return nil }
        return lang?.trimmingCharacters(in: .whitespaces)
    }

    /// css-text-4 §6.2 `hyphenate-limit-chars: auto` — the minimum word
    /// length any engine in this comparison will hyphenate (Chromium 2+2,
    /// Minikin MIN_PREFIX 2 / MIN_SUFFIX 3; five is the shared floor).
    private static let minHyphenatableWord = 5

    /// Could a dictionary find a break inside `text`? The hyphenation
    /// half of "is this line breakable?", the companion to
    /// `DecorationOps.hasSoftWrapOpportunity`'s UAX #14 half. A digit run
    /// (`00000`) has no letters and therefore nowhere to hyphenate, so a
    /// line made of one still counts as unbreakable and keeps the §5.2
    /// overflow rule — the Compose twin measured that on
    /// css-text/hyphens-punctuation-001.
    static func hasDictionaryOpportunity(_ text: String) -> Bool {
        var run = 0
        for c in text {
            if c.isLetter {
                run += 1
                if run >= minHyphenatableWord { return true }
            } else {
                run = 0
            }
        }
        return false
    }

    // MARK: - The hyphenator (the half Minikin owns on Android)

    /// Cache of resolved locales: `CFLocale` construction plus the
    /// availability probe is not free and a corpus run asks for the same
    /// two or three tags thousands of times. Keyed by the VERBATIM tag.
    /// nil value = "this tag has no dictionary on this device" (a real,
    /// cacheable answer — never retried).
    private static var localeCache: [String: CFLocale?] = [:]

    /// The `CFLocale` for `tag` if — and only if — the device ships a
    /// hyphenation dictionary for it. `CFStringIsHyphenationAvailableForLocale`
    /// is the honest probe: iOS bundles patterns for a fixed language set,
    /// and asking for a language it lacks must degrade to `manual`, not
    /// silently produce no breaks with the switch reported as "on".
    static func locale(for tag: String) -> CFLocale? {
        if let cached = localeCache[tag] { return cached }
        // Built through NSLocale rather than `CFLocaleCreate`: CFLocale and
        // NSLocale are toll-free bridged, and Swift's importer maps
        // `CFLocaleIdentifier` to a type no String or CFString will coerce
        // to — so the ObjC initialiser is the only spelling that compiles
        // for a runtime-supplied BCP-47 tag.
        let l = NSLocale(localeIdentifier: tag) as CFLocale
        let usable: CFLocale? =
            CFStringIsHyphenationAvailableForLocale(l) ? l : nil
        localeCache[tag] = usable
        return usable
    }

    /// Every hyphenation opportunity inside `word`, as ASCENDING UTF-16
    /// offsets: a hyphen may be inserted BEFORE the character at each
    /// offset (`CFStringGetHyphenationLocationBeforeIndex`'s own contract).
    ///
    /// The API answers one point at a time, searching BACKWARDS from a
    /// given index, so the enumeration walks down from the end and
    /// reverses. Empty for a word the dictionary does not break (and for
    /// anything shorter than the §6.2 floor, which CF also refuses).
    static func breakOffsets(in word: String, locale: CFLocale) -> [Int] {
        let s = word as CFString
        let length = CFStringGetLength(s)
        guard length >= minHyphenatableWord else { return [] }
        let range = CFRange(location: 0, length: length)
        var out: [Int] = []
        var searchBefore = length
        // Bounded by construction: every accepted index is strictly
        // smaller than the previous `searchBefore`, so the walk descends.
        while searchBefore > 0 {
            let idx = CFStringGetHyphenationLocationBeforeIndex(
                s, searchBefore, range, 0, locale, nil)
            guard idx != kCFNotFound, idx > 0, idx < searchBefore else { break }
            out.append(idx)
            searchBefore = idx
        }
        return out.reversed()
    }

    /// The hyphen character this locale's dictionary asks the UA to paint
    /// (§6.1 leaves it UA-defined; css-text-4's `hyphenate-character`
    /// overrides it, and a declared value wins at the call site).
    /// U+2010 HYPHEN is what CoreFoundation reports for the Latin
    /// languages and what Chromium paints, so it is also the fallback.
    static let defaultHyphenCharacter = "\u{2010}"
}
