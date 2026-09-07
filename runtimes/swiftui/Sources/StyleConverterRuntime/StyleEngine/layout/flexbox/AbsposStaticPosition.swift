//
//  AbsposStaticPosition.swift
//  StyleConverterRuntime — wave 19 (lane FLEX): the FULL static-position
//  resolver for an absolutely-positioned child of a flex container.
//
//  The wave-18 AbsposStaticAlignment half handled only the CROSS axis
//  and only under the runtime's LTR horizontal-tb normalization
//  (row-reverse folded to row, writing-mode ignored) — the WPT proof
//  being flex-abspos-staticpos-align-self-safe-001/002/003, where the
//  refs anchor vertical-rl containers at the RIGHT edge and row-reverse
//  containers at their main-END edge while both natives painted four
//  identical top-left boxes (natives 0.87–0.92 vs web).
//
//  This file adds the ONE axis-mapping table (css-flexbox-1 §5: main
//  axis = inline for row/row-reverse and block for column/column-reverse,
//  each direction derived from writing-mode + direction per
//  css-writing-modes-4 §2) and resolves BOTH physical axes:
//    • the CROSS axis from the child's align-self (both wire channels —
//      typed AlignSelf and the Generic safe/unsafe escape hatch, the
//      wave-18 readers);
//    • the MAIN axis from the container's justify-content applied to
//      the sole hypothetical item (css-position-3 §3.5.3 /
//      css-flexbox-1 §4.1).
//
//  Shared-semantics contract: AbsposStaticPosition.kt carries the
//  byte-parallel twin (same types, same table, same axisOffset math);
//  both are pinned by unit tables with IDENTICAL inputs/expected offsets
//  (AbsposStaticPositionTests / AbsposStaticPositionTest) — the exact
//  per-sub-container offsets of the three failing WPT fixtures.
//

// Foundation for the String keyword normalizers (replacingOccurrences);
// the CGFloat/CGSize view-level half lives in AbsposStaticOffset.swift.
import Foundation

enum AbsposStaticPosition {

    /// One PHYSICAL axis of a resolved static position: the LOGICAL
    /// claim (base + safe, wave-18 semantics) plus whether the logical
    /// axis runs AGAINST the physical +x/+y direction — reversal flips
    /// start/end at offset time, and flips the `safe` overflow fallback
    /// to the physical END edge (logical start of a reversed axis).
    struct AxisSpec: Equatable {
        // The requested logical position on the axis.
        let base: AbsposStaticAlignment.Base
        // True for `safe <pos>` — falls back to logical start on overflow.
        let safe: Bool
        // True when the logical axis runs against physical +x/+y.
        let reversed: Bool
    }

    /// Both physical axes of the static position. A nil axis means "no
    /// claim" — either an explicit inset replaces the static position
    /// there (css-position-3 §3.5) or nothing aligns that axis (the
    /// caller keeps its legacy anchor). `justifyTyped` is true when the
    /// container declared justify-content on the TYPED wire — consumed
    /// only by the Compose loops (their Row/Column arrangement already
    /// moves the reported box for typed keywords, so internal placement
    /// there would double-count); the iOS overlay path has no
    /// arrangement and ignores the flag.
    struct StaticPos: Equatable {
        // Physical horizontal claim (from the container's LEFT edge).
        let x: AxisSpec?
        // Physical vertical claim (from the container's TOP edge).
        let y: AxisSpec?
        // Typed justify-content declared on the container (see above).
        let justifyTyped: Bool
    }

    /// The css-flexbox-1 §5 axis derivation, collapsed to physical
    /// booleans: which physical axis is MAIN, and whether each flex axis
    /// runs against its physical +direction. Cross is always the other
    /// physical axis, so only its reversal is carried.
    struct AxisMap: Equatable {
        // Main axis physical orientation (true = x, false = y).
        let mainIsHorizontal: Bool
        // Main axis runs against its physical +direction.
        let mainReversed: Bool
        // Cross axis runs against its physical +direction.
        let crossReversed: Bool
    }

    /// The ONE axis-mapping table. Inputs are the RAW wire keywords
    /// (never folded enums — reversal must survive). Wire shapes pinned
    /// against the LIVE converter: FlexDirection "ROW"/"ROW_REVERSE"/
    /// "COLUMN"/"COLUMN_REVERSE"; WritingMode "HORIZONTAL_TB"/
    /// "VERTICAL_RL"/"VERTICAL_LR" (sideways-* folds to the matching
    /// vertical-* — the runtime's documented approximation); Direction
    /// "LTR"/"RTL". Nil keywords take the CSS initial values (row /
    /// horizontal-tb / ltr — css-flexbox-1 §5.1, css-writing-modes-4 §2).
    static func axisMap(flexDirection: String?,
                        writingMode: String?,
                        direction: String?) -> AxisMap {
        // Normalize wire spellings once (typed UPPER_SNAKE + raw hyphen).
        let fd = flexDirection?.uppercased().replacingOccurrences(of: "-", with: "_")
        let wm = writingMode?.uppercased().replacingOccurrences(of: "-", with: "_")
        let dir = direction?.uppercased()
        // Vertical writing: the inline axis is physical-vertical and the
        // block axis physical-horizontal (css-writing-modes-4 §2.4).
        let verticalWriting = wm == "VERTICAL_RL" || wm == "VERTICAL_LR" ||
            wm == "SIDEWAYS_RL" || wm == "SIDEWAYS_LR"
        // Block axis runs right→left (against +x) for the *-rl modes.
        let blockReversed = wm == "VERTICAL_RL" || wm == "SIDEWAYS_RL"
        // Inline axis runs against its +direction under direction: rtl.
        let inlineReversed = dir == "RTL"
        // Row-like: main axis = inline axis (css-flexbox-1 §5.1); the
        // CSS initial (absent wire) is row.
        let rowLike = fd == nil || fd == "ROW" || fd == "ROW_REVERSE"
        // *_REVERSE flips the MAIN axis direction only (§5.1) — the
        // cross axis direction comes from the writing mode alone
        // (flex-wrap: wrap-reverse would flip it; the runtime's abspos
        // static position does not model wrap-reverse — documented gap).
        let dirReverse = fd == "ROW_REVERSE" || fd == "COLUMN_REVERSE"
        // Main physical orientation: inline is horizontal exactly when
        // the writing mode is horizontal; block is the complement.
        let mainIsHorizontal = rowLike ? !verticalWriting : verticalWriting
        // Main reversal composes the axis's own physical direction with
        // the *_REVERSE flip (two flips cancel — column-reverse in
        // vertical-rl runs left→right, the safe-002 C3 ref pin).
        let mainReversed = (rowLike ? inlineReversed : blockReversed) != dirReverse
        // Cross axis = the other logical axis, never *_REVERSE-flipped.
        let crossReversed = rowLike ? blockReversed : inlineReversed
        return AxisMap(mainIsHorizontal: mainIsHorizontal,
                       mainReversed: mainReversed,
                       crossReversed: crossReversed)
    }

    /// Resolve the FULL physical static position claims for one abspos
    /// child of a flex container. Container side: raw FlexDirection /
    /// WritingMode / Direction + justify-content (both wire channels).
    /// Child side: align-self (both channels, wave-18 readers) + the
    /// per-PHYSICAL-axis inset gate (css-position-3 §3.5: a non-auto
    /// inset on an axis replaces the static position there).
    static func resolveStatic(containerProperties: [IRProperty],
                              childProperties: [IRProperty]) -> StaticPos {
        // The raw wire keywords — folded enums would erase reversal.
        let map = axisMap(
            flexDirection: keyword(containerProperties, "FlexDirection"),
            writingMode: keyword(containerProperties, "WritingMode"),
            direction: keyword(containerProperties, "Direction"))
        // MAIN claim: declared justify-content as sole-item fallback, or
        // the synthesized default start (justify-content: normal behaves
        // as flex-start in flex layout — css-align-3 §5.1). The default
        // claim is what anchors reverse containers at their main-END
        // edge (safe-002) — reversal lives in the AxisSpec, not here.
        let justify = justifySpec(containerProperties)
        let mainSpec = justify ?? AbsposStaticAlignment.Spec(base: .start, safe: false)
        // CROSS claim: the child's align-self via the wave-18 readers.
        let crossSpec = AbsposStaticAlignment.resolveCross(from: childProperties)
        // §3.5 inset gates are PHYSICAL — check the axis each claim
        // actually lands on (main is vertical when not horizontal).
        // Auto-tolerant reader (wave-19 skeptic fix): an EXPLICIT
        // `top: auto` KEEPS the static position (§3.5 only replaces it
        // for non-auto values), so it must not trip the gate.
        let main: AxisSpec? = hasNonAutoInset(
                childProperties, vertical: !map.mainIsHorizontal)
            ? nil
            : AxisSpec(base: mainSpec.base, safe: mainSpec.safe, reversed: map.mainReversed)
        let cross: AxisSpec? = (crossSpec == nil || hasNonAutoInset(
                childProperties, vertical: map.mainIsHorizontal))
            ? nil
            : AxisSpec(base: crossSpec!.base, safe: crossSpec!.safe, reversed: map.crossReversed)
        // Physical assignment: main lands on x when horizontal, else y.
        let typed = typedJustifyDeclared(containerProperties)
        return map.mainIsHorizontal
            ? StaticPos(x: main, y: cross, justifyTyped: typed)
            : StaticPos(x: cross, y: main, justifyTyped: typed)
    }

    /// The container's justify-content as a SOLE-ITEM alignment claim.
    /// Typed wire first (JustifyContentPropertyParser keywords), then
    /// the Generic escape hatch for `safe|unsafe <pos>` values (same
    /// grammar AbsposStaticAlignment.parseRaw pins for align-self —
    /// css-align-3 treats the two longhands as one grammar on two axes).
    /// Sole-item folds per css-align-3 §5.1: space-between behaves as
    /// flex-start and space-around/evenly center the lone item.
    static func justifySpec(_ containerProperties: [IRProperty]) -> AbsposStaticAlignment.Spec? {
        // Channel 1 — typed keyword ("CENTER", "SPACE_BETWEEN", …).
        if let kw = keyword(containerProperties, "JustifyContent") {
            switch kw.uppercased().replacingOccurrences(of: "-", with: "_") {
            // start-family + the LTR left fold (css-align-3 §5.2).
            case "FLEX_START", "START", "LEFT", "NORMAL", "STRETCH":
                return AbsposStaticAlignment.Spec(base: .start, safe: false)
            case "CENTER":
                return AbsposStaticAlignment.Spec(base: .center, safe: false)
            // end-family + the LTR right fold.
            case "FLEX_END", "END", "RIGHT":
                return AbsposStaticAlignment.Spec(base: .end, safe: false)
            // Sole item: space-between → start; around/evenly → center
            // (css-align-3 §5.1 distribution fallback alignments).
            case "SPACE_BETWEEN":
                return AbsposStaticAlignment.Spec(base: .start, safe: false)
            case "SPACE_AROUND", "SPACE_EVENLY":
                return AbsposStaticAlignment.Spec(base: .center, safe: false)
            // Unknown typed value → treat as undeclared (no claim).
            default:
                break
            }
        }
        // Channel 2 — Generic `justify-content: safe|unsafe <pos>`.
        for p in containerProperties where p.type == "Generic" {
            // Only Generics for THIS longhand participate.
            guard case .object(let o) = p.data,
                  o["propertyName"]?.stringValue == "justify-content",
                  let raw = o["rawValue"]?.stringValue else { continue }
            // Shared safe/unsafe grammar reader (wave-18, pinned).
            return AbsposStaticAlignment.parseRaw(raw)
        }
        // Undeclared → the caller synthesizes the default start claim.
        return nil
    }

    /// True when justify-content shipped on the TYPED wire — see
    /// [StaticPos.justifyTyped] (Compose-arrangement double-count gate;
    /// carried in the shared shape so the twins stay byte-parallel).
    static func typedJustifyDeclared(_ containerProperties: [IRProperty]) -> Bool {
        containerProperties.contains { $0.type == "JustifyContent" }
    }

    /// The PHYSICAL offset (px) of the child's margin box from the
    /// container's physical start (left/top) edge on one axis:
    ///
    ///   logical = crossOffset(child, container, base, safe)  — wave-18
    ///   physical = reversed ? free − logical : logical
    ///
    /// The reversal maps logical-start to the physical END edge, so the
    /// `safe` overflow fallback (logical start) correctly anchors a
    /// vertical-rl container's child at its RIGHT edge with the excess
    /// spilling left (negative x — the safe-001 C2/C3 ref geometry).
    /// Byte-identical Doubles to the Compose twin for the same inputs.
    static func axisOffset(childPx: Double, containerPx: Double, spec: AxisSpec) -> Double {
        // The wave-18 logical math (start 0 / center ½ / end 1 + safe
        // fallback), unchanged and already twin-pinned.
        let logical = AbsposStaticAlignment.crossOffset(
            childPx: childPx, containerPx: containerPx,
            spec: AbsposStaticAlignment.Spec(base: spec.base, safe: spec.safe))
        // Physical flip for reversed axes: mirror inside the free space.
        return spec.reversed ? (containerPx - childPx) - logical : logical
    }

    /// css-position-3 §3.5 inset gate for the WPT resolver — like
    /// `AbsposStaticAlignment.hasCrossInset`, but an EXPLICIT `auto`
    /// inset does NOT count: the live converter ships `top: auto` as
    /// {"type":"Top","data":"auto"} (pinned 2026-07-26) and §3.5's
    /// replacement of the static position only happens for NON-auto
    /// inset values — an auto inset keeps the static position. The
    /// wave-18 reader stays byte-untouched for the dark-stage path
    /// (frozen-baseline stability); byte-parallel twin of the Kotlin
    /// AbsposStaticPosition.hasNonAutoInset.
    static func hasNonAutoInset(_ properties: [IRProperty], vertical: Bool) -> Bool {
        // The IR type names that pin this axis (physical + logical) —
        // same table as the wave-18 reader.
        let axisTypes: Set<String> = vertical
            ? ["Top", "Bottom", "InsetBlockStart", "InsetBlockEnd"]
            : ["Left", "Right", "InsetInlineStart", "InsetInlineEnd"]
        // Only NON-auto values replace the static position (§3.5);
        // px-shaped objects extract no `auto` keyword and thus count.
        return properties.contains { p in
            axisTypes.contains(p.type) &&
                ValueExtractors.extractKeyword(p.data)?.lowercased() != "auto"
        }
    }

    /// First matching typed keyword for `type` — the same tolerant
    /// reader every extractor lane uses (primitive/object carriers).
    private static func keyword(_ properties: [IRProperty], _ type: String) -> String? {
        properties.first { $0.type == type }
            .flatMap { ValueExtractors.extractKeyword($0.data) }
    }
}
