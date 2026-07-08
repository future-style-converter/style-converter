//
//  AnimationsSelfTest.swift
//  StyleEngine/animations — Phase 9.
//
//  Behavioural assertions for the Phase 9 extractors. Print-only on
//  failure — no assertionFailure / fatalError (ff901e3 convention).
//
//  Qualifies every `== .none` / `!= .none` comparison with the explicit
//  enum type (e.g. `AnimationTimeline.none`) because the Optional
//  wrapper around several of these types also has a `.none` case, and
//  Swift's overload resolution will silently bind to Optional.none when
//  the context is ambiguous — giving false PASS results.
//

import Foundation

enum AnimationsSelfTest {

    static func run() {
        var f: [String] = []
        func obj(_ d: [String: IRValue]) -> IRValue { .object(d) }
        func props(_ p: [(String, IRValue)]) -> [IRProperty] {
            p.map { IRProperty(type: $0.0, data: $0.1) }
        }

        // ── Registry drift ──────────────────────────────────────────
        let owned: Set<String> = AnimationsProperty.set
            .union(ScrollTimelineProperty.set)
        let missing = owned.subtracting(PropertyRegistry.migrated).sorted()
        if !missing.isEmpty {
            f.append("Missing from PropertyRegistry.migrated: \(missing.joined(separator: ", "))")
        }
        // Expected size: 22 animations + 3 scroll = 25.
        if AnimationsProperty.set.count != 22 {
            f.append("AnimationsProperty.set count \(AnimationsProperty.set.count) ≠ 22")
        }
        if ScrollTimelineProperty.set.count != 3 {
            f.append("ScrollTimelineProperty.set count \(ScrollTimelineProperty.set.count) ≠ 3")
        }

        // ── AnimationDuration list ──────────────────────────────────
        let dur = AnimationsExtractor.extract(from: props([
            ("AnimationDuration", obj([
                "durations": .array([
                    obj(["ms": .double(2000)]),
                    obj(["ms": .double(500)]),
                ]),
            ])),
        ]))
        if dur?.duration?.count != 2 { f.append("AnimationDuration: 2-entry list lost") }
        if dur?.duration?.first?.milliseconds != 2000 {
            f.append("AnimationDuration: first entry ms mismatch")
        }

        // ── AnimationDelay negatives preserved ──────────────────────
        let dly = AnimationsExtractor.extract(from: props([
            ("AnimationDelay", .array([
                obj(["ms": .double(-1500)]),
                obj(["ms": .double(250)]),
                obj(["ms": .double(-500)]),
            ])),
        ]))
        if dly?.delay?.count != 3 { f.append("AnimationDelay: 3-entry list lost") }
        if dly?.delay?.first?.milliseconds != -1500 {
            f.append("AnimationDelay: negative not preserved")
        }

        // ── AnimationIterationCount — infinite vs numeric ──────────
        let iter = AnimationsExtractor.extract(from: props([
            ("AnimationIterationCount", .array([
                .double(1), .double(3), .string("infinite"), .double(0.5),
            ])),
        ]))
        if iter?.iterationCount?.count != 4 {
            f.append("AnimationIterationCount: count mismatch")
        }
        if iter?.iterationCount?[2] != .infinite {
            f.append("AnimationIterationCount: `infinite` did not map")
        }
        if case .count(let n) = iter?.iterationCount?[3] ?? .infinite, n == 0.5 { /* ok */ }
        else { f.append("AnimationIterationCount: fractional 0.5 mismatch") }

        // ── AnimationDirection — ALTERNATE_REVERSE ─────────────────
        let dir = AnimationsExtractor.extract(from: props([
            ("AnimationDirection", .array([.string("ALTERNATE_REVERSE"), .string("NORMAL")])),
        ]))
        if dir?.direction != [.alternateReverse, .normal] {
            f.append("AnimationDirection: ALTERNATE_REVERSE mapping")
        }

        // ── AnimationFillMode ──────────────────────────────────────
        let fm = AnimationsExtractor.extract(from: props([
            ("AnimationFillMode", .array([.string("BOTH"), .string("NONE")])),
        ]))
        if fm?.fillMode != [.both, AnimationFillModeKind.none] {
            f.append("AnimationFillMode: BOTH/NONE mismatch")
        }

        // ── AnimationPlayState ─────────────────────────────────────
        let ps = AnimationsExtractor.extract(from: props([
            ("AnimationPlayState", .array([.string("PAUSED"), .string("RUNNING")])),
        ]))
        if ps?.playState != [.paused, .running] {
            f.append("AnimationPlayState: PAUSED/RUNNING mismatch")
        }

        // ── AnimationComposition list + single ─────────────────────
        let cmp = AnimationsExtractor.extract(from: props([
            ("AnimationComposition", obj([
                "type": .string("list"),
                "values": .array([.string("replace"), .string("add"), .string("accumulate")]),
            ])),
        ]))
        if cmp?.composition != [.replace, .add, .accumulate] {
            f.append("AnimationComposition: list 3-entry mismatch")
        }
        let cmp1 = AnimationsExtractor.extract(from: props([
            ("AnimationComposition", obj(["type": .string("accumulate")])),
        ]))
        if cmp1?.composition != [.accumulate] {
            f.append("AnimationComposition: single accumulate mismatch")
        }

        // ── AnimationTimingFunction — cubic-bezier ─────────────────
        let tfc = AnimationsExtractor.extract(from: props([
            ("AnimationTimingFunction", .array([
                obj(["cb": .array([.double(0.25), .double(0.1), .double(0.25), .double(1.0)])]),
            ])),
        ]))
        if case .cubicBezier(let x1, _, _, let y2) = tfc?.timingFunction?.first ?? .steps(count: 0, position: .end),
           x1 == 0.25, y2 == 1.0 { /* ok */ }
        else { f.append("TimingFunction: cubic-bezier ease mismatch") }

        // ── AnimationTimingFunction — steps jump-start ─────────────
        let tfs = AnimationsExtractor.extract(from: props([
            ("AnimationTimingFunction", .array([
                obj(["steps": obj(["n": .int(4), "pos": .string("jump-start")])]),
            ])),
        ]))
        if case .steps(let n, let pos) = tfs?.timingFunction?.first ?? .steps(count: 0, position: .end),
           n == 4, pos == .jumpStart { /* ok */ }
        else { f.append("TimingFunction: steps(4, jump-start) mismatch") }

        // ── AnimationTimingFunction — linear(stops) ───────────────
        let tfl = AnimationsExtractor.extract(from: props([
            ("AnimationTimingFunction", .array([
                obj(["linear": .array([
                    obj(["v": .double(0)]),
                    obj(["v": .double(0.25), "p": .double(25)]),
                    obj(["v": .double(1)]),
                ])]),
            ])),
        ]))
        if case .linearStops(let stops) = tfl?.timingFunction?.first ?? .steps(count: 0, position: .end),
           stops.count == 3, stops[1].percent == 25 { /* ok */ }
        else { f.append("TimingFunction: linear(stops) mismatch") }

        // ── AnimationTimeline — none / auto / named / scroll / view
        let tlNone = AnimationsExtractor.extract(from: props([
            ("AnimationTimeline", obj(["type": .string("none")])),
        ]))
        // Explicit enum qualification — Optional<AnimationTimeline>.none would shadow.
        if tlNone?.timeline != AnimationTimeline.none {
            f.append("AnimationTimeline: none did not map (Optional<enum>.none shadow check)")
        }

        let tlAuto = AnimationsExtractor.extract(from: props([
            ("AnimationTimeline", obj(["type": .string("auto")])),
        ]))
        if tlAuto?.timeline != AnimationTimeline.auto {
            f.append("AnimationTimeline: auto did not map")
        }

        let tlNamed = AnimationsExtractor.extract(from: props([
            ("AnimationTimeline", obj(["type": .string("named"), "name": .string("--my-tl")])),
        ]))
        if case .named(let n) = tlNamed?.timeline ?? AnimationTimeline.none, n == "--my-tl" { /* ok */ }
        else { f.append("AnimationTimeline: named --my-tl mismatch") }

        let tlScroll = AnimationsExtractor.extract(from: props([
            ("AnimationTimeline", obj(["type": .string("scroll"), "axis": .string("INLINE")])),
        ]))
        if case .scroll(let axis) = tlScroll?.timeline ?? AnimationTimeline.none, axis == .inline { /* ok */ }
        else { f.append("AnimationTimeline: scroll(inline) mismatch") }

        let tlView = AnimationsExtractor.extract(from: props([
            ("AnimationTimeline", obj(["type": .string("view"), "axis": .string("Y")])),
        ]))
        if case .view(let axis, _) = tlView?.timeline ?? AnimationTimeline.none, axis == .y { /* ok */ }
        else { f.append("AnimationTimeline: view(y) mismatch") }

        // ── AnimationRangeStart / End — named + percent + length ──
        let rsNamed = AnimationsExtractor.extract(from: props([
            ("AnimationRangeStart", obj(["name": .string("cover"), "offset": .double(25)])),
        ]))
        if case .named(.cover, let off) = rsNamed?.rangeStart ?? .normal, off == 25 { /* ok */ }
        else { f.append("AnimationRangeStart: cover 25% mismatch") }

        let rsPct = AnimationsExtractor.extract(from: props([
            ("AnimationRangeStart", .double(25)),
        ]))
        if case .percent(let p) = rsPct?.rangeStart ?? .normal, p == 25 { /* ok */ }
        else { f.append("AnimationRangeStart: bare 25 percent mismatch") }

        let rePx = AnimationsExtractor.extract(from: props([
            ("AnimationRangeEnd", obj(["px": .double(200)])),
        ]))
        if case .length(let px) = rePx?.rangeEnd ?? .normal, px == 200 { /* ok */ }
        else { f.append("AnimationRangeEnd: 200px length mismatch") }

        let reNormal = AnimationsExtractor.extract(from: props([
            ("AnimationRangeEnd", .string("normal")),
        ]))
        if reNormal?.rangeEnd != AnimationRangeEnd.normal {
            f.append("AnimationRangeEnd: normal keyword did not map")
        }

        // ── TransitionProperty list ────────────────────────────────
        let tp = AnimationsExtractor.extract(from: props([
            ("TransitionProperty", .array([
                obj(["type": .string("all")]),
                obj(["type": .string("property-name"), "name": .string("opacity")]),
                obj(["type": .string("none")]),
            ])),
        ]))
        // Explicit enum qualification — TransitionPropertyEntry.none vs Optional.none.
        if tp?.transitionProperty?.count != 3 {
            f.append("TransitionProperty: 3-entry count mismatch")
        }
        if tp?.transitionProperty?.first != TransitionPropertyEntry.all {
            f.append("TransitionProperty: first entry all mismatch")
        }
        if case .propertyName(let n) = tp?.transitionProperty?[1] ?? TransitionPropertyEntry.none,
           n == "opacity" { /* ok */ }
        else { f.append("TransitionProperty: property-name opacity mismatch") }
        if tp?.transitionProperty?[2] != TransitionPropertyEntry.none {
            f.append("TransitionProperty: third entry none mismatch")
        }

        // ── TransitionBehavior list + single ───────────────────────
        let tb = AnimationsExtractor.extract(from: props([
            ("TransitionBehavior", obj([
                "type": .string("list"),
                "values": .array([.string("normal"), .string("allow-discrete")]),
            ])),
        ]))
        if tb?.transitionBehavior != [.normal, .allowDiscrete] {
            f.append("TransitionBehavior: normal/allow-discrete list mismatch")
        }

        // ── TransitionTimingFunction — ease-in (cubic-bezier form) ─
        let ttf = AnimationsExtractor.extract(from: props([
            ("TransitionTimingFunction", .array([
                obj(["cb": .array([.double(0.42), .double(0), .double(1), .double(1)])]),
            ])),
        ]))
        if case .cubicBezier(let x1, _, _, _) = ttf?.transitionTimingFunction?.first ?? .steps(count: 0, position: .end),
           x1 == 0.42 { /* ok */ }
        else { f.append("TransitionTimingFunction: ease-in mismatch") }

        // ── TransitionDelay negatives ──────────────────────────────
        let tdly = AnimationsExtractor.extract(from: props([
            ("TransitionDelay", .array([
                obj(["ms": .double(-500)]), obj(["ms": .double(250)]),
            ])),
        ]))
        if tdly?.transitionDelay?.first?.milliseconds != -500 {
            f.append("TransitionDelay: negative ms lost")
        }

        // ── ViewTimeline full / name-only / axis ──────────────────
        let vt = AnimationsExtractor.extract(from: props([
            ("ViewTimeline", obj(["name": .string("--v"), "axis": .string("INLINE")])),
        ]))
        if vt?.viewTimeline?.name != "--v" || vt?.viewTimeline?.axis != .inline {
            f.append("ViewTimeline: --v inline mismatch")
        }

        let vtn = AnimationsExtractor.extract(from: props([
            ("ViewTimelineName", .string("none")),
        ]))
        // README note 12: parser stores "none" literally, not a sentinel.
        if vtn?.viewTimelineName != "none" {
            f.append("ViewTimelineName: literal \"none\" not preserved")
        }

        let vta = AnimationsExtractor.extract(from: props([
            ("ViewTimelineAxis", .string("BLOCK")),
        ]))
        if vta?.viewTimelineAxis != .block {
            f.append("ViewTimelineAxis: BLOCK mismatch")
        }

        // ── ViewTimelineInset — percent pair ───────────────────────
        let vti = AnimationsExtractor.extract(from: props([
            ("ViewTimelineInset", obj([
                "start": obj(["type": .string("percentage"), "value": .double(10)]),
                "end":   obj(["type": .string("percentage"), "value": .double(10)]),
            ])),
        ]))
        if case .percent(let p) = vti?.viewTimelineInset?.start ?? .auto, p == 10 { /* ok */ }
        else { f.append("ViewTimelineInset: percent 10 start mismatch") }

        let vtiAuto = AnimationsExtractor.extract(from: props([
            ("ViewTimelineInset", obj([
                "start": obj(["type": .string("auto")]),
                "end":   obj(["type": .string("auto")]),
            ])),
        ]))
        if vtiAuto?.viewTimelineInset?.start != ViewTimelineInsetLeg.auto {
            f.append("ViewTimelineInset: auto auto mismatch")
        }

        // ── ViewTransitionName / Class / Group ─────────────────────
        let vtxn = AnimationsExtractor.extract(from: props([
            ("ViewTransitionName", obj(["type": .string("named"), "name": .string("--hero")])),
        ]))
        if case .named(let n) = vtxn?.viewTransitionName ?? ViewTransitionName.none, n == "--hero" { /* ok */ }
        else { f.append("ViewTransitionName: --hero mismatch") }

        let vtxc = AnimationsExtractor.extract(from: props([
            ("ViewTransitionClass", obj([
                "type": .string("classes"),
                "names": .array([.string("slide"), .string("fade")]),
            ])),
        ]))
        if case .classes(let arr) = vtxc?.viewTransitionClass ?? ViewTransitionClasses.none,
           arr == ["slide", "fade"] { /* ok */ }
        else { f.append("ViewTransitionClass: slide/fade mismatch") }

        let vtxg = AnimationsExtractor.extract(from: props([
            ("ViewTransitionGroup", obj(["type": .string("contain")])),
        ]))
        if vtxg?.viewTransitionGroup != .contain {
            f.append("ViewTransitionGroup: contain mismatch")
        }

        // ── TimelineScope none / all / names ──────────────────────
        let tsAll = AnimationsExtractor.extract(from: props([
            ("TimelineScope", obj(["type": .string("all")])),
        ]))
        // Explicit enum qualification — Optional<TimelineScopeKind>.none shadow.
        if tsAll?.timelineScope != TimelineScopeKind.all {
            f.append("TimelineScope: all mismatch")
        }
        let tsNames = AnimationsExtractor.extract(from: props([
            ("TimelineScope", obj([
                "type": .string("names"),
                "names": .array([.string("--a"), .string("--b")]),
            ])),
        ]))
        if case .names(let ns) = tsNames?.timelineScope ?? TimelineScopeKind.none, ns == ["--a", "--b"] { /* ok */ }
        else { f.append("TimelineScope: names list mismatch") }

        // ── ScrollTimeline (3 longhands) ──────────────────────────
        let st = ScrollTimelineExtractor.extract(from: props([
            ("ScrollTimeline", obj([
                "name": obj(["name": .string("--my-scroll")]),
                "axis": .string("BLOCK"),
            ])),
        ]))
        if st?.timeline?.name != "--my-scroll" || st?.timeline?.axis != .block {
            f.append("ScrollTimeline: name+axis mismatch")
        }

        let stn = ScrollTimelineExtractor.extract(from: props([
            ("ScrollTimelineName", obj(["name": .string("--page-scroll")])),
        ]))
        if stn?.name != "--page-scroll" {
            f.append("ScrollTimelineName: --page-scroll mismatch")
        }

        // README note 14: literal "none" preserved.
        let stnNone = ScrollTimelineExtractor.extract(from: props([
            ("ScrollTimelineName", obj(["name": .string("none")])),
        ]))
        if stnNone?.name != "none" {
            f.append("ScrollTimelineName: literal \"none\" not preserved")
        }

        let sta = ScrollTimelineExtractor.extract(from: props([
            ("ScrollTimelineAxis", .string("X")),
        ]))
        if sta?.axis != .x {
            f.append("ScrollTimelineAxis: X mismatch")
        }

        // Absent → nil aggregates.
        if AnimationsExtractor.extract(from: props([])) != nil {
            f.append("AnimationsExtractor: absent should yield nil aggregate")
        }
        if ScrollTimelineExtractor.extract(from: props([])) != nil {
            f.append("ScrollTimelineExtractor: absent should yield nil aggregate")
        }

        // ── PASS / FAIL ─────────────────────────────────────────────
        if f.isEmpty {
            print("[AnimationsSelfTest] PASS — animations + scroll-timeline engine green")
        } else {
            print("[AnimationsSelfTest] FAIL — \(f.count) check(s) failed:")
            f.forEach { print("  - \($0)") }
        }
    }
}
