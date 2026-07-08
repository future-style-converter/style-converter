//
//  AnimationsConfig.swift
//  StyleEngine/animations — Phase 9.
//
//  Grouped config for the 22 animation + transition + view-timeline +
//  view-transition + timeline-scope longhands. SwiftUI has no single
//  animation modifier that takes every CSS knob, so we aggregate here
//  and let the applier translate into `withAnimation` / `.animation` /
//  `.transition` calls (or log TODOs for the bits SwiftUI can't model
//  yet — e.g. scroll-linked timelines pre-iOS 17).
//
//  Scope: longhands only. `animation` / `transition` / `view-timeline`
//  / `scroll-timeline` shorthands are expanded by the CSS parser
//  before the IR reaches us; the shorthand tokens never appear here.
//

import Foundation

// MARK: - AnimationName

/// Single entry in `animation-name`: either `none` or a CSS ident. The
/// parser lowercases the whole value (README note 1), so `Identifier`
/// carries an already-lowercased string.
enum AnimationNameEntry: Equatable {
    case none
    case identifier(String)
}

// MARK: - AnimationDirection / FillMode / PlayState / Composition / Behavior

/// `animation-direction` — normal / reverse / alternate / alternate-reverse.
enum AnimationDirectionKind: Equatable {
    case normal
    case reverse
    case alternate
    case alternateReverse
}

/// `animation-fill-mode` — none / forwards / backwards / both.
enum AnimationFillModeKind: Equatable {
    case none
    case forwards
    case backwards
    case both
}

/// `animation-play-state` — running / paused.
enum AnimationPlayStateKind: Equatable {
    case running
    case paused
}

/// `animation-composition` — replace / add / accumulate.
enum AnimationCompositionKind: Equatable {
    case replace
    case add
    case accumulate
}

/// `transition-behavior` — normal / allow-discrete.
enum TransitionBehaviorKind: Equatable {
    case normal
    case allowDiscrete
}

// MARK: - AnimationIterationCount

/// One entry in the comma list. `infinite` or a numeric count (can be
/// fractional or 0). The parser preserves fractional counts as-is.
enum AnimationIterationCountEntry: Equatable {
    case infinite
    case count(Double)
}

// MARK: - AnimationTimingFunction

/// A timing function: keyword (already normalised to cubic-bezier by
/// the parser for the five standard keywords), explicit cubic-bezier,
/// steps, or a linear-stops function. `TransitionTimingFunction` uses
/// the same type but the parser never produces `.linearStops` for
/// transitions (README note 8).
enum AnimationTimingFn: Equatable {
    /// Cubic bezier — four control points `(x1, y1, x2, y2)`. Keywords
    /// `linear/ease/ease-in/ease-out/ease-in-out` all normalise here
    /// via the parser's `cb` field.
    case cubicBezier(x1: Double, y1: Double, x2: Double, y2: Double)
    /// `steps(n, pos)` — pos is one of start/end/jump-start/jump-end/
    /// jump-none/jump-both. Default pos is `end`.
    case steps(count: Int, position: StepsPosition)
    /// `linear(stops)` — an ordered list of `(value, optionalPercent)`
    /// control points that describe a piecewise-linear easing.
    case linearStops([LinearStop])
}

/// Step positions.
enum StepsPosition: String, Equatable {
    case start
    case end
    case jumpStart = "jump-start"
    case jumpEnd = "jump-end"
    case jumpNone = "jump-none"
    case jumpBoth = "jump-both"
}

/// Linear stop — value in [0,1], optional percent along the curve.
struct LinearStop: Equatable {
    let value: Double
    let percent: Double?
}

// MARK: - AnimationTimeline

/// `animation-timeline` — none/auto/named-ident/`scroll(...)`/`view(...)`.
/// The parser keeps the last-seen axis token inside `scroll(...)` /
/// `view(...)` (README note 4) — we mirror that here.
enum AnimationTimeline: Equatable {
    case none
    case auto
    case named(String)
    case scroll(axis: TimelineAxisKind?)
    case view(axis: TimelineAxisKind?, insets: [ViewTimelineInsetLeg])
}

/// Timeline axis keyword. CSS accepts `block/inline/x/y`; parser upper-cases.
enum TimelineAxisKind: Equatable {
    case block
    case inline
    case x
    case y
}

/// One leg of a view-timeline inset value. We model both auto and
/// length/percent together (README note 11 — a bare `%` without a
/// number is rejected upstream).
enum ViewTimelineInsetLeg: Equatable {
    case auto
    case length(CGFloat)
    case percent(Double)
}

// MARK: - AnimationRange / RangeStart / RangeEnd

/// Phase name inside an animation-range-start / -end. The parser's
/// catch-all behaviour (README note 6) means `keyword` can contain any
/// lower-cased string — not strictly the spec set.
enum RangePhase: Equatable {
    case cover
    case contain
    case entry
    case exit
    /// Parser fallthrough — any ident that wasn't recognised above.
    case other(String)
}

/// One end of an animation-range — normal / length / percent /
/// named+offset. Matches IR shape variants seen in the fixture dump.
enum AnimationRangeEnd: Equatable {
    case normal
    case length(CGFloat)
    case percent(Double)
    case named(RangePhase, offsetPercent: Double)
}

/// `animation-range` shorthand-as-longhand object: `{ start, end }`.
/// The parser stores these as raw strings or a numeric (percent). We
/// preserve the raw IR strings so the self-test can assert them
/// without a second round of keyword parsing.
struct AnimationRange: Equatable {
    let startRaw: String?
    let endRaw: String?
}

// MARK: - TransitionProperty

/// One entry in the transition-property list.
enum TransitionPropertyEntry: Equatable {
    case none
    case all
    /// Arbitrary CSS property name, e.g. `opacity`, `background-color`,
    /// `--my-custom-var`. Parser lowercases (README note 7).
    case propertyName(String)
}

// MARK: - View Timeline / View Transition

/// `view-timeline-inset` — `{ start, end }`.
struct ViewTimelineInsets: Equatable {
    let start: ViewTimelineInsetLeg
    let end: ViewTimelineInsetLeg
}

/// `view-timeline` (single declaration) — parser discards `none`
/// silently (README note 10) so `name == nil` means "no declaration".
struct ViewTimelineDeclaration: Equatable {
    let name: String?
    let axis: TimelineAxisKind?
}

/// `view-transition-name` — `none | auto | <ident>`. Parser stores
/// `{type:"named", name:"auto"}` literally so we keep an `.auto` variant
/// only when we explicitly recognise the spelling.
enum ViewTransitionName: Equatable {
    case none
    case named(String)
}

/// `view-transition-class` — none or a list of class names.
enum ViewTransitionClasses: Equatable {
    case none
    case classes([String])
}

/// `view-transition-group` — four recognised keywords + fallthrough
/// `raw(String)` (README note 13 — `auto` / `match-element` fall here).
enum ViewTransitionGroup: Equatable {
    case normal
    case nearest
    case contain
    case root
    case raw(String)
}

/// `timeline-scope` — none / all / explicit list.
enum TimelineScopeKind: Equatable {
    case none
    case all
    case names([String])
}

// MARK: - Aggregate

/// Rolled-up animation state produced by `AnimationsExtractor`. Nil
/// fields mean "property not declared for this component". The
/// applier short-circuits when `touched == false`.
struct AnimationsConfig: Equatable {

    // Animation longhands (13).
    var name: [AnimationNameEntry]? = nil
    var duration: [TimeValue]? = nil
    var delay: [TimeValue]? = nil
    var iterationCount: [AnimationIterationCountEntry]? = nil
    var direction: [AnimationDirectionKind]? = nil
    var fillMode: [AnimationFillModeKind]? = nil
    var playState: [AnimationPlayStateKind]? = nil
    var composition: [AnimationCompositionKind]? = nil
    var timingFunction: [AnimationTimingFn]? = nil
    var timeline: AnimationTimeline? = nil
    var range: AnimationRange? = nil
    var rangeStart: AnimationRangeEnd? = nil
    var rangeEnd: AnimationRangeEnd? = nil

    // Transition longhands (5).
    var transitionProperty: [TransitionPropertyEntry]? = nil
    var transitionDuration: [TimeValue]? = nil
    var transitionDelay: [TimeValue]? = nil
    var transitionTimingFunction: [AnimationTimingFn]? = nil
    var transitionBehavior: [TransitionBehaviorKind]? = nil

    // Timeline / scope (6 — TimelineScope + 5 ViewTimeline*).
    var timelineScope: TimelineScopeKind? = nil
    var viewTimeline: ViewTimelineDeclaration? = nil
    var viewTimelineName: String? = nil
    var viewTimelineAxis: TimelineAxisKind? = nil
    var viewTimelineInset: ViewTimelineInsets? = nil

    // View-transition (3).
    var viewTransitionName: ViewTransitionName? = nil
    var viewTransitionClass: ViewTransitionClasses? = nil
    var viewTransitionGroup: ViewTransitionGroup? = nil

    /// True when at least one animation/transition longhand wrote into
    /// the aggregate. The applier short-circuits on `false`.
    var touched: Bool = false
}
