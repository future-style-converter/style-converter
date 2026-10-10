//
//  OutOfFlowContainingBlock.swift
//  StyleConverterRuntime — wave 54 (lane L4, unit OOF-ios).
//
//  The NON-transform clauses that make a box the containing block of its
//  absolutely AND fixed positioned descendants, plus the inset test that
//  gives an all-auto fixed box its static position. Until this file existed
//  FixedHoist.split stripped every fixed descendant to the canvas unless a
//  TRANSFORMED ancestor claimed it, so a fixed box under `contain: strict`
//  or `backdrop-filter` escaped to the viewport: css-position/change-insets-
//  inside-strict-containment-nested ios painted its green halves at the
//  canvas origin over the paragraph and left the red bare (wave53-final ios
//  P 0.9594, DEGENERATE; brief tools/titan/results/wave54-plan/
//  oof-containing-block.md §1).
//
//  THIS FILE IS A TWIN of Compose
//    runtimes/compose/src/main/java/com/styleconverter/runtime/layout/
//      position/OutOfFlowContainingBlock.kt
//  pinned row-for-row by OutOfFlowContainingBlockTests.swift /
//  OutOfFlowContainingBlockTest.kt. Change one, change both.
//
//  The clauses (each a spec sentence, cited at its reader below):
//    • css-contain-2 §3.2 / §3.4 — layout and paint containment; `strict`
//      and `content` include both;
//    • filter-effects-1 §5 — `filter` other than none;
//    • filter-effects-2 §2 — `backdrop-filter` other than none;
//    • css-will-change-1 §3 — a `will-change` hint naming one of the above.
//  On iOS the ONLY consumer of `establishes` is FixedHoist's fixed-descendant
//  strip (OR-ed with TransformContainingBlock at one line): nested ABSOLUTE
//  boxes were never hoisted here, so this unit moves FIXED boxes only.
//

// The decision is PURE over the wire — no SwiftUI, no view state.
import Foundation

/// The non-transform containing-block rule table (see file header).
public enum OutOfFlowContainingBlock {

    /// Logged when a clause fires on an `absolute`/`fixed` box and the claim is withheld.
    static let positionedEstablisherBreadcrumb =
        "ContainingBlock[positioned-establisher-fixed-descendant]"

    /// Logged when a `contain` leaf cannot be decoded (the claim is then false).
    static let containDecodeBreadcrumb = "ContainingBlock[contain-decode-failed]"

    /// Logged when a box declares a property that IMPLIES layout/paint
    /// containment through another rule (css-contain-2 §4 content-visibility,
    /// css-contain-3 container-type) — named here, not claimed yet.
    static let impliedContainmentBreadcrumb = "ContainingBlock[implied-containment-unclaimed]"

    /// Types that can carry a clause; necessary, not sufficient (each has a none).
    static let candidateTypes: Set<String> = ["Contain", "Filter", "BackdropFilter", "WillChange"]

    /// Types whose implied containment is named but not claimed (see breadcrumb).
    static let impliedTypes: Set<String> = ["ContentVisibility", "ContainerType"]

    /// The eight inset longhands the converter emits (PositionExtractor's list).
    static let insetTypes: Set<String> = [
        "Top", "Right", "Bottom", "Left",
        "InsetBlockStart", "InsetBlockEnd", "InsetInlineStart", "InsetInlineEnd",
    ]

    /// True when at least one clause fires AND the box is not itself
    /// `position: absolute | fixed` — the Compose twin's restriction, kept
    /// byte-parallel: the four positioned backdrop-filter containers hold
    /// ABSOLUTE children only (inert here either way), and a withheld claim
    /// costs only a FIXED descendant of such a box (0 corpus carriers), which
    /// is logged, never silent.
    public static func establishes(_ properties: [IRProperty]) -> Bool {
        // Named-but-unclaimed implied containment: breadcrumb only, no pixel.
        if properties.contains(where: { impliedTypes.contains($0.type) }) {
            PropertyTracker.logOnce(key: impliedContainmentBreadcrumb,
                                    message: "\(impliedContainmentBreadcrumb): content-visibility / container-type containment not claimed")
        }
        // Fast bail — the corpus is dominated by boxes with no candidate type.
        guard properties.contains(where: { candidateTypes.contains($0.type) }) else { return false }
        // One pass, first firing clause wins; each type has its own none test.
        let fires = properties.contains { p in
            switch p.type {
            case "Contain": return containsLayoutOrPaint(p.data)
            case "Filter", "BackdropFilter": return filterListIsUsed(p.data)
            case "WillChange": return willChangeHintsContainingBlock(p.data)
            default: return false
            }
        }
        // No clause fired: not an establisher.
        guard fires else { return false }
        // The box's own position, through the classifier FixedHoist uses.
        let own = LayoutExtractor.extract(from: properties)?.position
        // Positioned establisher: withhold the claim, leave a breadcrumb.
        if own == .absolute || own == .fixed {
            PropertyTracker.logOnce(key: positionedEstablisherBreadcrumb,
                                    message: "\(positionedEstablisherBreadcrumb): claim withheld on a positioned establisher")
            return false
        }
        // A static/relative/sticky establisher claims both out-of-flow classes.
        return true
    }

    /// Convenience over a component — the shape `FixedHoist` walks.
    public static func establishes(_ component: IRComponent) -> Bool {
        establishes(component.properties)
    }

    /// Does the box declare ANY inset other than `auto`? css-position-3
    /// §3.5.3 gives an out-of-flow box its static position only when its
    /// insets are `auto`; the wire omits an undeclared inset and serializes
    /// `auto` as the string "auto". Deliberately NOT `FixedHoist.hasAnyInset`
    /// (resolved px only): an unresolvable `calc(anchor(…))` inset
    /// (anchor-center-safe-rtl's fixed `__4`) IS declared, so it keeps its
    /// viewport hoist. Read by FixedHoist's fixed strip and RC1 class.
    public static func declaresAnyInset(_ properties: [IRProperty]) -> Bool {
        properties.contains { insetTypes.contains($0.type) && $0.data.stringValue != "auto" }
    }

    /// `contain` — css-contain-2 §3.2 (layout) / §3.4 (paint); `strict` and
    /// `content` expand to sets that include both. The three wire shapes the
    /// Compose PerformanceExtractor reads, read the same way: a bare array of
    /// keyword strings (the converter's emission, `["CONTENT"]`), a
    /// space-separated string, and a legacy `{layout:true,…}` object — whose
    /// non-primitive member is a decode failure on Compose (`jsonPrimitive`
    /// throws), so it is one here too: logged, false.
    private static func containsLayoutOrPaint(_ data: IRValue) -> Bool {
        // The keywords that include layout or paint containment.
        let claims: Set<String> = ["LAYOUT", "PAINT", "STRICT", "CONTENT"]
        switch data {
        case .array(let a):
            // Normal emission: each element one keyword.
            return a.contains { claims.contains($0.stringValue?.uppercased() ?? "") }
        case .string(let s):
            // Space-separated string form.
            return s.uppercased().split(separator: " ").contains { claims.contains(String($0)) }
        case .object(let o):
            // Legacy flag object: any non-primitive member is undecodable.
            let keys = ["layout", "paint", "size", "style"]
            if keys.contains(where: { o[$0]?.objectValue != nil || o[$0]?.arrayValue != nil }) {
                PropertyTracker.logOnce(key: containDecodeBreadcrumb,
                                        message: "\(containDecodeBreadcrumb): non-primitive contain flag")
                return false
            }
            // A flag is set when its primitive reads "true" (bool or string).
            let set = { (k: String) in o[k]?.boolValue == true || o[k]?.stringValue == "true" }
            return set("layout") || set("paint")
        default:
            // null / number / bool: no containment keyword.
            return false
        }
    }

    /// `filter` (filter-effects-1 §5) and `backdrop-filter` (filter-effects-2
    /// §2): a non-empty function array establishes (the converter serializes
    /// `backdrop-filter: none` as an EMPTY array, `filter: none` as the string
    /// "none"); a `url(…)` / unresolved-expression object establishes (the
    /// TransformContainingBlock conservatism); any primitive does not.
    private static func filterListIsUsed(_ data: IRValue) -> Bool {
        switch data {
        case .array(let a): return !a.isEmpty
        case .object: return true
        default: return false
        }
    }

    /// `will-change` — css-will-change-1 §3: a hint naming `filter`,
    /// `backdrop-filter` or `contain` (leaf `[{"type":"property-name",
    /// "name":"filter"}]`); `transform`/`perspective` are
    /// TransformContainingBlock's, `opacity` creates none.
    private static func willChangeHintsContainingBlock(_ data: IRValue) -> Bool {
        // Non-array leaves (`auto`) carry no property hint.
        guard let arr = data.arrayValue else { return false }
        // Any hint object whose name is one of the three clauses above.
        return arr.contains { hint in
            let name = hint["name"]?.stringValue
            return name == "filter" || name == "backdrop-filter" || name == "contain"
        }
    }
}
