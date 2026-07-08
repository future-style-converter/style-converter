//
//  FlexboxExtractor.swift
//  StyleEngine/layout/flexbox — Phase 7 step 2 (flexbox).
//
//  Folds the 11 flexbox-family longhands (plus BoxOrient, skipped below)
//  into the shared LayoutAggregate. The aggregate is consumed once by
//  LayoutApplier / ComponentRenderer to pick the SwiftUI container kind
//  (HStack / VStack / ZStack / FlowLayout) and the per-child modifier
//  chain — see LayoutAggregate.swift for the rationale on why these are
//  fused rather than one-modifier-per-property.
//
//  Scope (task: pedantic-bhabha, Phase 7b flexbox slice):
//    Display, FlexDirection, FlexWrap,
//    FlexGrow, FlexShrink, FlexBasis,
//    JustifyContent, AlignItems, AlignContent, AlignSelf, Order.
//  BoxOrient: skipped — parser is not registered per Phase 7 fixtures
//  README, so the property never reaches the iOS runtime in practice.
//

// Foundation is enough — no SwiftUI types escape the aggregate.
import Foundation

enum FlexboxExtractor {

    /// Single linear pass. Later occurrences of the same property win —
    /// matches the CSS last-wins cascade within a single rule, same
    /// convention every other extractor in this tree follows.
    /// Returns the aggregate unchanged when none of the 11 properties
    /// are present; the caller (`LayoutExtractor`) decides whether the
    /// aggregate was `touched`.
    static func extract(from properties: [IRProperty], into agg: inout LayoutAggregate) {
        // Walk once; dispatch by property type string. Unknown names in
        // the flex family (BoxOrient today) are silently ignored to keep
        // the extractor honest about what it actually supports.
        for prop in properties {
            switch prop.type {
            case "Display":
                // IR shape: plain string "FLEX" / "GRID" / "BLOCK" / …
                // or the parser-shell object {keyword: …}. `extractKeyword`
                // handles both; `normalize` uppercases + hyphen→underscore.
                if let kw = ValueExtractors.extractKeyword(prop.data) {
                    agg.display = mapDisplay(ValueExtractors.normalize(kw))
                    agg.touched = true
                }
            case "FlexDirection":
                // IR shape: plain string "ROW" / "ROW_REVERSE" / "COLUMN"
                // / "COLUMN_REVERSE". See FlexDirectionPropertyParser.
                if let kw = ValueExtractors.extractKeyword(prop.data) {
                    agg.flexDirection = mapFlexDirection(ValueExtractors.normalize(kw))
                    agg.touched = true
                }
            case "FlexWrap":
                // IR shape: plain string "NOWRAP" / "WRAP" / "WRAP_REVERSE".
                if let kw = ValueExtractors.extractKeyword(prop.data) {
                    agg.flexWrap = mapFlexWrap(ValueExtractors.normalize(kw))
                    agg.touched = true
                }
            case "JustifyContent":
                // IR shape: keyword. Covers flex-start / flex-end / center
                // / space-between / space-around / space-evenly + CSS
                // logical start/end aliases.
                if let kw = ValueExtractors.extractKeyword(prop.data) {
                    agg.justifyContent = mapAlignment(ValueExtractors.normalize(kw))
                    agg.touched = true
                }
            case "AlignItems":
                // IR shape: keyword. Covers stretch / flex-start / flex-end
                // / center / baseline.
                if let kw = ValueExtractors.extractKeyword(prop.data) {
                    agg.alignItems = mapAlignment(ValueExtractors.normalize(kw))
                    agg.touched = true
                }
            case "AlignContent":
                // IR shape: keyword. Superset of AlignItems plus
                // space-between / around / evenly for multi-line packing.
                if let kw = ValueExtractors.extractKeyword(prop.data) {
                    agg.alignContent = mapAlignment(ValueExtractors.normalize(kw))
                    agg.touched = true
                }
            case "AlignSelf":
                // IR shape: keyword including "AUTO" for "inherit from
                // parent's align-items". We record auto so the applier
                // can skip emitting an override.
                if let kw = ValueExtractors.extractKeyword(prop.data) {
                    agg.alignSelf = mapAlignment(ValueExtractors.normalize(kw))
                    agg.touched = true
                }
            case "FlexGrow":
                // IR shape: {value: {type: "…Number", value: N}, normalizedValue: N}.
                // Reading normalizedValue is cheapest — the parser already
                // clamped negatives to 0 per the CSS spec.
                if let n = extractNormalizedNumber(prop.data) {
                    agg.flexGrow = n
                    agg.touched = true
                }
            case "FlexShrink":
                // Same IR shape as FlexGrow. SwiftUI has no analogue (noted
                // in the applier); we still fold so downstream selftests
                // can inspect the value.
                if let n = extractNormalizedNumber(prop.data) {
                    agg.flexShrink = n
                    agg.touched = true
                }
            case "FlexBasis":
                // IR shapes observed in fixtures:
                //   • plain string "auto" / "content"
                //   • {value: {px: N}, normalizedPixels: N} (absolute length)
                //   • {keyword: "auto"} (parser-shell fallback)
                if let v = extractFlexBasis(prop.data) {
                    agg.flexBasis = v
                    agg.touched = true
                }
            case "Order":
                // IR shape: raw integer (observed in fixtures). Fall back
                // to ValueExtractors.extractInt which also handles boxed
                // {value: N} shapes that other parsers occasionally emit.
                if let i = ValueExtractors.extractInt(prop.data) {
                    agg.order = i
                    agg.touched = true
                }
            case "BoxOrient":
                // Out of scope per task brief — parser not registered.
                // Leaving the case explicit so grep-coverage stays honest.
                break
            default:
                // Not a flexbox property — other extractors own it.
                continue
            }
        }
    }

    // MARK: - Keyword → enum maps

    /// Maps normalized (upper/underscore) CSS `display` keywords to the
    /// subset SwiftUI can represent. Unknown values fall back to `.block`
    /// because that's the CSS default for initial value of `display`.
    private static func mapDisplay(_ kw: String) -> DisplayKeyword {
        switch kw {
        // `inline-flex` renders the same as `flex` in our SDUI runtime —
        // we don't model inline run context for flex containers.
        case "FLEX", "INLINE_FLEX":     return .flex
        case "GRID", "INLINE_GRID":     return .grid
        case "INLINE", "INLINE_BLOCK":  return .inline
        case "NONE":                    return .none
        case "CONTENTS":                return .contents
        default:                        return .block
        }
    }

    /// Maps `flex-direction` keyword. Unknown → row (CSS initial value).
    private static func mapFlexDirection(_ kw: String) -> FlexDirectionKeyword {
        switch kw {
        case "ROW_REVERSE":    return .rowReverse
        case "COLUMN":         return .column
        case "COLUMN_REVERSE": return .columnReverse
        default:               return .row
        }
    }

    /// Maps `flex-wrap` keyword. Unknown → nowrap (CSS initial value).
    private static func mapFlexWrap(_ kw: String) -> FlexWrapKeyword {
        switch kw {
        case "WRAP":         return .wrap
        case "WRAP_REVERSE": return .wrapReverse
        default:             return .nowrap
        }
    }

    /// Maps the common CSS alignment keyword space used by justify-content
    /// / align-items / align-content / align-self. Unknown → normal so the
    /// applier can treat it as "no override".
    private static func mapAlignment(_ kw: String) -> AlignmentKeyword {
        switch kw {
        // Start / end family + physical aliases the parser normalises to
        // keyword strings. `LEFT`/`RIGHT` collapse into start/end because
        // SwiftUI stacks don't have separate physical alignments.
        case "FLEX_START", "START", "LEFT":   return .start
        case "FLEX_END",   "END",   "RIGHT":  return .end
        case "CENTER":                        return .center
        case "STRETCH":                       return .stretch
        case "BASELINE", "FIRST_BASELINE", "LAST_BASELINE":
            return .baseline
        case "SPACE_BETWEEN":                 return .spaceBetween
        case "SPACE_AROUND":                  return .spaceAround
        case "SPACE_EVENLY":                  return .spaceEvenly
        case "SELF_START":                    return .selfStart
        case "SELF_END":                      return .selfEnd
        case "AUTO":                          return .auto
        case "NORMAL":                        return .normal
        // CSS Anchor Positioning Level 1 §6: `anchor-center` collapses to
        // `center` whenever no default anchor is in scope. SwiftUI has no
        // anchor-positioning runtime so this fold is always correct here.
        // Mirrors Android's parseAlignment + Web's AlignSelfApplier.
        case "ANCHOR_CENTER":                 return .center
        default:                              return .normal
        }
    }

    // MARK: - Numeric / length helpers

    /// FlexGrow/FlexShrink IR: `{value: {value: N}, normalizedValue: N}`.
    /// Prefer `normalizedValue` when present (cheapest path), fall back
    /// to the nested `value.value` tuple, then to a raw number.
    private static func extractNormalizedNumber(_ v: IRValue) -> Double? {
        if case .object(let o) = v {
            if let n = o["normalizedValue"]?.doubleValue { return n }
            if let inner = o["value"]?.objectValue,
               let n = inner["value"]?.doubleValue { return n }
        }
        // Raw-number fallback covers the "we parsed it as a bare number"
        // path some shorthand expansions use.
        switch v {
        case .double(let d): return d
        case .int(let i):    return Double(i)
        default:             return nil
        }
    }

    /// FlexBasis IR shapes: plain "auto"/"content" strings, or
    /// `{value: {px: N}, normalizedPixels: N}` for lengths, or
    /// `{keyword: "auto"}` via the parser-shell fallback.
    private static func extractFlexBasis(_ v: IRValue) -> FlexBasisValue? {
        // String-form shortcut — fixtures use this for `auto` and `content`.
        if case .string(let s) = v {
            switch s.lowercased() {
            case "auto":    return .auto
            case "content": return .content
            default:        return nil
            }
        }
        // Object form — check for length first, then keyword.
        if case .object(let o) = v {
            // Normalized-pixels is populated for absolute lengths.
            if let n = o["normalizedPixels"]?.doubleValue {
                return .px(CGFloat(n))
            }
            // Nested length carrier: {value: {px: N}}.
            if let inner = o["value"]?.objectValue,
               let px = inner["px"]?.doubleValue {
                return .px(CGFloat(px))
            }
            // Direct {px: N} shape (rare but safe to handle).
            if let px = o["px"]?.doubleValue {
                return .px(CGFloat(px))
            }
            // Keyword parser-shell fallback.
            if let kw = o["keyword"]?.stringValue {
                switch kw.lowercased() {
                case "auto":    return .auto
                case "content": return .content
                default:        return nil
                }
            }
        }
        return nil
    }
}
