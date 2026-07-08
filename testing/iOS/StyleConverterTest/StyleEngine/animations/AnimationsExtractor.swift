//
//  AnimationsExtractor.swift
//  StyleEngine/animations — Phase 9.
//
//  Walks a property list once and fills in one AnimationsConfig. All
//  22 property type names are owned by the `AnimationsProperty.set`
//  which is unioned into `PropertyRegistry.migrated`.
//
//  IR shapes are documented on each `case`. The parser quirks noted in
//  examples/properties/animations/README.md (identifier lowercasing,
//  axis "last wins", ViewTransitionGroup raw catch-all, etc.) are
//  faithfully preserved here — this extractor does not second-guess
//  the parser.
//

import Foundation

/// Registry ownership: the 22 animation-family + view-transition + scope
/// property-type names that flow through AnimationsExtractor. Union into
/// `PropertyRegistry.migrated` at registration time.
enum AnimationsProperty {
    /// Explicit name list so it is diff-auditable.
    static let names: [String] = [
        // Animation longhands (13).
        "AnimationName", "AnimationDuration", "AnimationDelay",
        "AnimationIterationCount", "AnimationDirection",
        "AnimationFillMode", "AnimationPlayState",
        "AnimationComposition", "AnimationTimingFunction",
        "AnimationTimeline", "AnimationRange",
        "AnimationRangeStart", "AnimationRangeEnd",
        // Transition longhands (5).
        "TransitionProperty", "TransitionDuration",
        "TransitionDelay", "TransitionTimingFunction",
        "TransitionBehavior",
        // TimelineScope + view-timeline-longhands (5 + 1 = 6).
        "TimelineScope",
        "ViewTimeline", "ViewTimelineName",
        "ViewTimelineAxis", "ViewTimelineInset",
        // ViewTransition (3).
        "ViewTransitionName", "ViewTransitionClass",
        "ViewTransitionGroup",
    ]
    /// Set form for PropertyRegistry union.
    static var set: Set<String> { Set(names) }
}

enum AnimationsExtractor {

    /// Walk every property once. Skips anything not owned here so the
    /// call site can feed the entire property list verbatim.
    static func extract(from properties: [IRProperty]) -> AnimationsConfig? {
        var cfg = AnimationsConfig()
        let owned = AnimationsProperty.set
        for prop in properties where owned.contains(prop.type) {
            applyOne(prop, into: &cfg)
        }
        return cfg.touched ? cfg : nil
    }

    // One dispatch per property-type name. Each branch documents the
    // IR shape it expects (see the fixture dump in the task brief).
    private static func applyOne(_ p: IRProperty, into cfg: inout AnimationsConfig) {
        switch p.type {

        // IR: [{type:"identifier", name:"fade"}, {type:"none"}]
        case "AnimationName":
            cfg.name = parseNameList(p.data); cfg.touched = true

        // IR: {type:"...Durations", durations:[{ms:N}, …]}
        case "AnimationDuration":
            cfg.duration = parseDurationsWrapper(p.data); cfg.touched = true

        // IR: [{ms:N}, {ms:-500}, …]  (array of time objects, negatives allowed)
        case "AnimationDelay":
            cfg.delay = extractTimes(p.data); cfg.touched = true

        // IR: [1.0, "infinite", 0.5, 2.5, …]
        case "AnimationIterationCount":
            cfg.iterationCount = parseIterationList(p.data); cfg.touched = true

        // IR: ["NORMAL", "REVERSE", "ALTERNATE", "ALTERNATE_REVERSE"]
        case "AnimationDirection":
            cfg.direction = parseEnumList(p.data, mapper: parseDirection); cfg.touched = true

        // IR: ["NONE", "FORWARDS", "BACKWARDS", "BOTH"]
        case "AnimationFillMode":
            cfg.fillMode = parseEnumList(p.data, mapper: parseFillMode); cfg.touched = true

        // IR: ["RUNNING", "PAUSED", …]
        case "AnimationPlayState":
            cfg.playState = parseEnumList(p.data, mapper: parsePlayState); cfg.touched = true

        // IR: {type:"add"|"replace"|"accumulate"} OR
        //     {type:"list", values:["replace","add","accumulate"]}
        case "AnimationComposition":
            cfg.composition = parseCompositionList(p.data); cfg.touched = true

        // IR: [{cb:[x1,y1,x2,y2], original:…} | {steps:{n,pos}, original:…} |
        //      {linear:[{v,p?}, …], original:…}]
        case "AnimationTimingFunction":
            cfg.timingFunction = parseTimingList(p.data); cfg.touched = true

        // IR: {type:"auto"|"none"|"scroll"|"view"|"named", name?:"…"}
        case "AnimationTimeline":
            cfg.timeline = parseTimeline(p.data); cfg.touched = true

        // IR: {start: <str|double|null>, end: <str|double|null>}
        case "AnimationRange":
            cfg.range = parseAnimationRange(p.data); cfg.touched = true

        // IR: "normal" | <double> | {px:N} | {name:"cover", offset:N}
        case "AnimationRangeStart":
            cfg.rangeStart = parseAnimationRangeEnd(p.data); cfg.touched = true

        // IR: same shape as RangeStart.
        case "AnimationRangeEnd":
            cfg.rangeEnd = parseAnimationRangeEnd(p.data); cfg.touched = true

        // IR: [{type:"property-name", name:"opacity"} | {type:"all"} | {type:"none"}]
        case "TransitionProperty":
            cfg.transitionProperty = parseTransitionPropertyList(p.data); cfg.touched = true

        // IR: [{ms:N}, …]
        case "TransitionDuration":
            cfg.transitionDuration = extractTimes(p.data); cfg.touched = true

        // IR: [{ms:N}, …] — negatives allowed (README fixture covers -500ms).
        case "TransitionDelay":
            cfg.transitionDelay = extractTimes(p.data); cfg.touched = true

        // IR: same shape as AnimationTimingFunction minus linear() stops.
        case "TransitionTimingFunction":
            cfg.transitionTimingFunction = parseTimingList(p.data); cfg.touched = true

        // IR: {type:"normal"|"allow-discrete"} OR
        //     {type:"list", values:[…]}
        case "TransitionBehavior":
            cfg.transitionBehavior = parseTransitionBehaviorList(p.data); cfg.touched = true

        // IR: {type:"none"|"all"} | {type:"names", names:[…]}
        case "TimelineScope":
            cfg.timelineScope = parseTimelineScope(p.data); cfg.touched = true

        // IR: <str> | {name:…, axis:"BLOCK"|"INLINE"|"X"|"Y"}
        case "ViewTimeline":
            cfg.viewTimeline = parseViewTimeline(p.data); cfg.touched = true

        // IR: <str> — literal "none" is NOT a sentinel (README note 12).
        case "ViewTimelineName":
            cfg.viewTimelineName = p.data.stringValue; cfg.touched = true

        // IR: "BLOCK"|"INLINE"|"X"|"Y"
        case "ViewTimelineAxis":
            cfg.viewTimelineAxis = parseAxis(p.data.stringValue); cfg.touched = true

        // IR: {start: <leg>, end: <leg>}  where <leg> is
        //     {type:"auto"} | {type:"length", px:N} | {type:"percentage", value:N}
        case "ViewTimelineInset":
            cfg.viewTimelineInset = parseInsets(p.data); cfg.touched = true

        // IR: {type:"none"} | {type:"named", name:"…"}
        case "ViewTransitionName":
            cfg.viewTransitionName = parseViewTransitionName(p.data); cfg.touched = true

        // IR: {type:"none"} | {type:"classes", names:[…]}
        case "ViewTransitionClass":
            cfg.viewTransitionClass = parseViewTransitionClass(p.data); cfg.touched = true

        // IR: {type:"normal"|"nearest"|"contain"|"root"|"raw", raw?:"…"}
        case "ViewTransitionGroup":
            cfg.viewTransitionGroup = parseViewTransitionGroup(p.data); cfg.touched = true

        default:
            break
        }
    }

    // MARK: - List primitives

    /// Generic enum-keyword list parser. Used for direction/fill/play.
    private static func parseEnumList<T>(
        _ data: IRValue,
        mapper: (String) -> T?
    ) -> [T] {
        guard case .array(let arr) = data else { return [] }
        return arr.compactMap { $0.stringValue.flatMap(mapper) }
    }

    /// IR: [ {type:"identifier", name:"X"} | {type:"none"} ]
    private static func parseNameList(_ data: IRValue) -> [AnimationNameEntry] {
        guard case .array(let arr) = data else { return [] }
        return arr.compactMap { entry -> AnimationNameEntry? in
            guard case .object(let o) = entry,
                  let t = o["type"]?.stringValue else { return nil }
            switch t {
            case "none": return .none
            case "identifier":
                return .identifier(o["name"]?.stringValue ?? "")
            default: return nil
            }
        }
    }

    /// IR: {type:"…Durations", durations:[{ms:N}, …]}. Empty wrapper
    /// returns empty list (never nil) to reflect "declared but empty".
    private static func parseDurationsWrapper(_ data: IRValue) -> [TimeValue] {
        if case .object(let o) = data,
           let durs = o["durations"] { return extractTimes(durs) }
        return extractTimes(data)
    }

    /// IR: [ <double> | "infinite" ]
    private static func parseIterationList(_ data: IRValue) -> [AnimationIterationCountEntry] {
        guard case .array(let arr) = data else { return [] }
        return arr.compactMap { entry in
            if let s = entry.stringValue, s.lowercased() == "infinite" {
                return .infinite
            }
            if let d = entry.doubleValue { return .count(d) }
            return nil
        }
    }

    /// Normalise CSS direction keyword strings (parser upper-cases +
    /// underscores, e.g. "ALTERNATE_REVERSE").
    private static func parseDirection(_ s: String) -> AnimationDirectionKind? {
        switch s.uppercased() {
        case "NORMAL": return .normal
        case "REVERSE": return .reverse
        case "ALTERNATE": return .alternate
        case "ALTERNATE_REVERSE", "ALTERNATE-REVERSE": return .alternateReverse
        default: return nil
        }
    }

    /// Keyword → fill-mode enum.
    private static func parseFillMode(_ s: String) -> AnimationFillModeKind? {
        switch s.uppercased() {
        case "NONE": return .none
        case "FORWARDS": return .forwards
        case "BACKWARDS": return .backwards
        case "BOTH": return .both
        default: return nil
        }
    }

    /// Keyword → play-state enum.
    private static func parsePlayState(_ s: String) -> AnimationPlayStateKind? {
        switch s.uppercased() {
        case "RUNNING": return .running
        case "PAUSED": return .paused
        default: return nil
        }
    }

    /// Keyword → composition enum (lowercase form from parser).
    private static func parseComposition(_ s: String) -> AnimationCompositionKind? {
        switch s.lowercased() {
        case "replace": return .replace
        case "add": return .add
        case "accumulate": return .accumulate
        default: return nil
        }
    }

    /// IR: {type:"replace"|"add"|"accumulate"} OR {type:"list", values:[…]}.
    private static func parseCompositionList(_ data: IRValue) -> [AnimationCompositionKind] {
        guard case .object(let o) = data,
              let t = o["type"]?.stringValue else { return [] }
        if t == "list" {
            guard case .array(let arr) = o["values"] ?? .null else { return [] }
            return arr.compactMap { $0.stringValue.flatMap(parseComposition) }
        }
        return parseComposition(t).map { [$0] } ?? []
    }

    /// IR: {type:"normal"|"allow-discrete"} OR list wrapper.
    private static func parseTransitionBehaviorList(_ data: IRValue) -> [TransitionBehaviorKind] {
        func one(_ s: String) -> TransitionBehaviorKind? {
            switch s.lowercased() {
            case "normal": return .normal
            case "allow-discrete": return .allowDiscrete
            default: return nil
            }
        }
        guard case .object(let o) = data,
              let t = o["type"]?.stringValue else { return [] }
        if t == "list" {
            guard case .array(let arr) = o["values"] ?? .null else { return [] }
            return arr.compactMap { $0.stringValue.flatMap(one) }
        }
        return one(t).map { [$0] } ?? []
    }

    // MARK: - Timing function list

    /// IR: [ <one timing fn> ]. Each entry is an object with exactly
    /// one of `cb`, `steps`, `linear`.
    private static func parseTimingList(_ data: IRValue) -> [AnimationTimingFn] {
        guard case .array(let arr) = data else { return [] }
        return arr.compactMap(parseTimingOne)
    }

    private static func parseTimingOne(_ entry: IRValue) -> AnimationTimingFn? {
        guard case .object(let o) = entry else { return nil }
        // cubic-bezier.
        if case .array(let cb) = o["cb"] ?? .null, cb.count == 4,
           let x1 = cb[0].doubleValue, let y1 = cb[1].doubleValue,
           let x2 = cb[2].doubleValue, let y2 = cb[3].doubleValue {
            return .cubicBezier(x1: x1, y1: y1, x2: x2, y2: y2)
        }
        // steps(n, pos).
        if case .object(let s) = o["steps"] ?? .null,
           let n = s["n"]?.intValue {
            let pos = (s["pos"]?.stringValue.flatMap(StepsPosition.init(rawValue:))) ?? .end
            return .steps(count: n, position: pos)
        }
        // linear(stops).
        if case .array(let stops) = o["linear"] ?? .null {
            let parsed: [LinearStop] = stops.compactMap { stopV in
                guard case .object(let so) = stopV,
                      let v = so["v"]?.doubleValue else { return nil }
                return LinearStop(value: v, percent: so["p"]?.doubleValue)
            }
            return .linearStops(parsed)
        }
        return nil
    }

    // MARK: - Timeline / Range / Insets

    /// IR: {type: auto|none|scroll|view|named, name?:"…"}. Axis + insets
    /// for scroll/view not present in fixture — preserve nil.
    private static func parseTimeline(_ data: IRValue) -> AnimationTimeline? {
        guard case .object(let o) = data,
              let t = o["type"]?.stringValue else { return nil }
        switch t {
        case "none":   return .none
        case "auto":   return .auto
        case "named":  return .named(o["name"]?.stringValue ?? "")
        case "scroll":
            let axis = parseAxis(o["axis"]?.stringValue)
            return .scroll(axis: axis)
        case "view":
            let axis = parseAxis(o["axis"]?.stringValue)
            return .view(axis: axis, insets: [])
        default:
            return nil
        }
    }

    /// Axis keyword → enum. Parser upper-cases strings like "BLOCK".
    private static func parseAxis(_ s: String?) -> TimelineAxisKind? {
        switch s?.uppercased() {
        case "BLOCK":  return .block
        case "INLINE": return .inline
        case "X":      return .x
        case "Y":      return .y
        default:       return nil
        }
    }

    /// IR: {start: <str|double|null>, end: <str|double|null>}.
    /// We preserve the raw string form so the self-test asserts the
    /// parser's exact output (README note 5 about 3-token fall-through).
    private static func parseAnimationRange(_ data: IRValue) -> AnimationRange {
        guard case .object(let o) = data else { return AnimationRange(startRaw: nil, endRaw: nil) }
        return AnimationRange(
            startRaw: rangeSideRaw(o["start"]),
            endRaw:   rangeSideRaw(o["end"]))
    }

    /// Stringify a range side value for the struct.
    private static func rangeSideRaw(_ v: IRValue?) -> String? {
        if let s = v?.stringValue { return s }
        if let d = v?.doubleValue { return String(d) }
        return nil
    }

    /// IR: "normal" | <double(percent)> | {px:N} | {name, offset}.
    private static func parseAnimationRangeEnd(_ data: IRValue) -> AnimationRangeEnd {
        if let s = data.stringValue {
            return s.lowercased() == "normal" ? .normal : .named(parsePhase(s), offsetPercent: 0)
        }
        if let d = data.doubleValue {
            return .percent(d)
        }
        if case .object(let o) = data {
            if let px = o["px"]?.doubleValue { return .length(CGFloat(px)) }
            if let name = o["name"]?.stringValue,
               let off = o["offset"]?.doubleValue {
                return .named(parsePhase(name), offsetPercent: off)
            }
        }
        return .normal
    }

    /// Phase keyword with catch-all (README note 6).
    private static func parsePhase(_ s: String) -> RangePhase {
        switch s.lowercased() {
        case "cover":   return .cover
        case "contain": return .contain
        case "entry":   return .entry
        case "exit":    return .exit
        default:        return .other(s.lowercased())
        }
    }

    /// IR: [ {type:"property-name", name:…} | {type:"all"} | {type:"none"} ]
    private static func parseTransitionPropertyList(_ data: IRValue) -> [TransitionPropertyEntry] {
        guard case .array(let arr) = data else { return [] }
        return arr.compactMap { entry -> TransitionPropertyEntry? in
            guard case .object(let o) = entry,
                  let t = o["type"]?.stringValue else { return nil }
            switch t {
            case "none": return TransitionPropertyEntry.none
            case "all":  return .all
            case "property-name":
                return .propertyName(o["name"]?.stringValue ?? "")
            default: return nil
            }
        }
    }

    // MARK: - View-timeline / Insets

    /// IR: {type:"none"|"all"} | {type:"names", names:[…]}
    private static func parseTimelineScope(_ data: IRValue) -> TimelineScopeKind {
        guard case .object(let o) = data,
              let t = o["type"]?.stringValue else { return .none }
        switch t {
        case "none": return .none
        case "all":  return .all
        case "names":
            if case .array(let arr) = o["names"] ?? .null {
                return .names(arr.compactMap { $0.stringValue })
            }
            return .names([])
        default: return .none
        }
    }

    /// IR: <str> (plain ident) | {name:…, axis:"…"}.
    private static func parseViewTimeline(_ data: IRValue) -> ViewTimelineDeclaration? {
        if let s = data.stringValue {
            return ViewTimelineDeclaration(name: s, axis: nil)
        }
        if case .object(let o) = data {
            return ViewTimelineDeclaration(
                name: o["name"]?.stringValue,
                axis: parseAxis(o["axis"]?.stringValue))
        }
        return nil
    }

    /// IR: {start: <leg>, end: <leg>}. <leg> is {type, px?|value?}.
    private static func parseInsets(_ data: IRValue) -> ViewTimelineInsets? {
        guard case .object(let o) = data else { return nil }
        return ViewTimelineInsets(
            start: parseLeg(o["start"]) ?? .auto,
            end:   parseLeg(o["end"])   ?? .auto)
    }

    /// IR leg: {type:"auto"} | {type:"length", px:N} | {type:"percentage", value:N}.
    private static func parseLeg(_ v: IRValue?) -> ViewTimelineInsetLeg? {
        guard case .object(let o) = v ?? .null,
              let t = o["type"]?.stringValue else { return nil }
        switch t {
        case "auto": return .auto
        case "length":
            if let px = o["px"]?.doubleValue { return .length(CGFloat(px)) }
            return .length(0)
        case "percentage":
            if let p = o["value"]?.doubleValue { return .percent(p) }
            return .percent(0)
        default: return nil
        }
    }

    // MARK: - View-transition

    /// IR: {type:"none"|"named", name?:"…"}. The string "auto" is
    /// stored as `.named("auto")` by the parser — we keep that fidelity.
    private static func parseViewTransitionName(_ data: IRValue) -> ViewTransitionName? {
        guard case .object(let o) = data,
              let t = o["type"]?.stringValue else { return nil }
        switch t {
        case "none":  return ViewTransitionName.none
        case "named": return .named(o["name"]?.stringValue ?? "")
        default: return nil
        }
    }

    /// IR: {type:"none"} | {type:"classes", names:[…]}.
    private static func parseViewTransitionClass(_ data: IRValue) -> ViewTransitionClasses? {
        guard case .object(let o) = data,
              let t = o["type"]?.stringValue else { return nil }
        switch t {
        case "none": return ViewTransitionClasses.none
        case "classes":
            if case .array(let arr) = o["names"] ?? .null {
                return .classes(arr.compactMap { $0.stringValue })
            }
            return .classes([])
        default: return nil
        }
    }

    /// IR: {type:"normal"|"nearest"|"contain"|"root"|"raw", raw?:"…"}.
    /// Falls through to `.raw(original)` for anything else (README note 13).
    private static func parseViewTransitionGroup(_ data: IRValue) -> ViewTransitionGroup? {
        guard case .object(let o) = data,
              let t = o["type"]?.stringValue else { return nil }
        switch t.lowercased() {
        case "normal":  return .normal
        case "nearest": return .nearest
        case "contain": return .contain
        case "root":    return .root
        case "raw":     return .raw(o["raw"]?.stringValue ?? "")
        default:        return .raw(t)
        }
    }
}
