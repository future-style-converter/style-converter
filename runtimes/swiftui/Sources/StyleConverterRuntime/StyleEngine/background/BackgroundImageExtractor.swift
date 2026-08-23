//
//  BackgroundImageExtractor.swift
//  StyleEngine/background — Phase 4; wave-21 IMAGES lane additions.
//
//  Parses the `BackgroundImage` IR into an ordered layer list. The IR is
//  always an array (even for a single layer). Each element is one of:
//    - Bare string "none"
//    - Object { url: String, data: Bool } — URL or data URI
//    - Object { type: "linear-gradient"|"radial-gradient"|"conic-gradient"
//              |"repeating-linear-gradient"|... , angle?: {deg}, stops: [...] }
//    - Object { type: "cross-fade", args: [{weight?, image}] } (A-RC2)
//    - Object { type: "color", color: {...} } — cross-fade color argument
//  Quirks:
//    * Radial shape-keywords (circle/ellipse/closest-side) appear as the
//      FIRST entry inside `stops` with no srgb — brief says detect and
//      skip these; we also capture the keyword for shape hinting.
//    * Stop position is 0..100 percentage (may be null); we normalise to
//      0..1 Double here so the applier doesn't need to remember. The
//      <length> arm (`positionLength: {px}`) rides as absolute px
//      (wave 46) — resolved against the gradient line at render time.
//    * `interp` (the <color-interpolation-method>) is parsed per layer
//      and stamped on each stop (wave 46, see BackgroundImageStop).
//    * Gradient centers (`pos: {x, y}`) are per-axis <length-percentage>
//      (A-RC8): raw number = percent; {px: N} = absolute; typed lh/em
//      resolve HERE against the component's own FontSize / LineHeight
//      properties — the extract site is where font metrics live.
//

import Foundation

enum BackgroundImageProperty {
    static let names: [String] = ["BackgroundImage"]
}

enum BackgroundImageExtractor {

    /// Font metrics for lh/em center resolution — byte-parallel with the
    /// Compose extractor's FontContext (ColorExtractor.kt).
    struct FontContext {
        /// Element font-size in px (CSS initial 16px).
        var fontSizePx: Double
        /// Used line-height in px: LineHeight multiplier × fontSize, an
        /// explicit px value, or `normal` ≈ 1.2 × fontSize — the same
        /// pinned ratio SpacingResolver uses for `lh` (pin P5).
        var lineHeightPx: Double
        /// CSS initials: font-size 16px, line-height normal ≈ 1.2em.
        static let `default` = FontContext(fontSizePx: 16, lineHeightPx: 19.2)
    }

    /// Build the FontContext from the component's property list.
    /// FontSize wire: {"px": N, …}; LineHeight wire: {"multiplier": M}
    /// or {"px": N} — pinned against the live wave21-gate
    /// conic-gradient-line-height-relative-units artifacts
    /// (FontSize {"px":50} + LineHeight {"multiplier":2} → lh = 100px).
    static func fontContext(of properties: [IRProperty]) -> FontContext {
        // Element font-size in px; absent → CSS initial 16px.
        let fontPx = properties.first { $0.type == "FontSize" }?
            .data.objectValue?["px"]?.doubleValue ?? 16
        let lh = properties.first { $0.type == "LineHeight" }?.data.objectValue
        let lineHeightPx = lh?["multiplier"]?.doubleValue.map { $0 * fontPx }
            ?? lh?["px"]?.doubleValue
            // `normal` computes to ≈1.2 × font-size — SpacingResolver pin P5.
            ?? (1.2 * fontPx)
        return FontContext(fontSizePx: fontPx, lineHeightPx: lineHeightPx)
    }

    // Entry point. Returns nil when no `BackgroundImage` property exists
    // or the array was empty / all-none.
    static func extract(from properties: [IRProperty]) -> BackgroundImageConfig? {
        // Last-wins cascade — capture the final BackgroundImage seen.
        var layers: [BackgroundImageLayer] = []
        var seen = false
        // Font context resolves lh/em centers at extract time (A-RC8).
        let ctx = fontContext(of: properties)
        for prop in properties where prop.type == "BackgroundImage" {
            seen = true
            layers = parseLayerArray(prop.data, ctx: ctx)
        }
        // Return nil in both "absent" and "[]" cases — neither needs the
        // applier to do anything.
        guard seen, !layers.isEmpty else { return nil }
        return BackgroundImageConfig(layers: layers)
    }

    // Parse the outer array. Defensive: wrap a single-object IR in an
    // array just in case an older IR shape slips in.
    private static func parseLayerArray(_ v: IRValue, ctx: FontContext) -> [BackgroundImageLayer] {
        // Normal case — outer array.
        if case .array(let arr) = v {
            return arr.compactMap { parseLayer($0, ctx: ctx) }
        }
        // Fallback single-layer path.
        if let one = parseLayer(v, ctx: ctx) { return [one] }
        return []
    }

    // Decode one layer entry. Returns nil to drop unrecognised entries
    // rather than render a blank "none" in their place.
    private static func parseLayer(_ v: IRValue, ctx: FontContext) -> BackgroundImageLayer? {
        // `none` literal — bare CSS keyword, IR keeps it as a string.
        if case .string(let s) = v, s.lowercased() == "none" {
            return BackgroundImageLayer.none
        }
        // Must be an object for any structured layer.
        guard case .object(let o) = v else { return nil }

        // URL layer: `{url: "...", data: Bool}`.
        if let url = o["url"]?.stringValue {
            return .url(url)
        }

        // Gradient layer — switch on the `type` discriminator.
        guard let t = o["type"]?.stringValue?.lowercased() else { return nil }
        let angle = readAngle(o["angle"])
        let rawStops = o["stops"]?.arrayValue ?? []
        // Wave 46: the authored <color-interpolation-method> rides the
        // layer as the optional `interp` key (BackgroundImageProperty.kt,
        // canonical spelling like "in hsl longer hue"); parsed once per
        // layer and stamped on each stop (see BackgroundImageStop.interp).
        let interp = GradientInterpolation.parse(o["interp"]?.stringValue)
        // Parse stops via the shared helper; it also yields any shape
        // keyword captured from a malformed first entry.
        let parsed = parseStops(rawStops, interp: interp)
        // CSS `at <position>` for radial / conic gradients lands in the
        // IR under `pos: {x, y}` — per-axis <length-percentage> since
        // A-RC8 (raw number = percent, object = length; lh/em resolve
        // through the font context). Mirrors the Compose extractor.
        let pos = o["pos"]?.objectValue
        let cx = readCoord(pos?["x"], ctx: ctx)
        let cy = readCoord(pos?["y"], ctx: ctx)

        switch t {
        case "linear-gradient":
            return .linear(angleDeg: angle, stops: parsed.stops)
        case "radial-gradient":
            return .radial(shape: parsed.shapeKeyword, stops: parsed.stops, cx: cx, cy: cy)
        case "conic-gradient":
            return .conic(fromDeg: angle, stops: parsed.stops, cx: cx, cy: cy)
        case "repeating-linear-gradient":
            return .repeating(kind: .linear, angleDeg: angle, stops: parsed.stops)
        case "repeating-radial-gradient":
            return .repeating(kind: .radial, angleDeg: nil, stops: parsed.stops)
        case "repeating-conic-gradient":
            return .repeating(kind: .conic, angleDeg: angle, stops: parsed.stops)
        case "cross-fade":
            // cross-fade() (css-images-4 §2.6.2) — see parseCrossFade.
            return parseCrossFade(o, ctx: ctx)
        case "color":
            // A bare <color> image (cross-fade argument). TWO live wire
            // shapes: the nested {"type":"color","color":{srgb,…}} the
            // serializer builds, AND the flattened {"type":"color",
            // "srgb":…,"original":…} that IRPropertySerializer.deepFlatten
            // emits on the real wire (it inlines any type+single-object-
            // field pattern — pinned by the converter run on the
            // premultiplied-alpha fixture). extractColor reads `srgb`
            // either way, so the flattened form passes the object itself.
            return .color(extractColor(o["color"] ?? .object(o)))
        default:
            return nil
        }
    }

    /// One cross-fade() layer. Wire (BackgroundImageSerializer.kt):
    /// {"type":"cross-fade","args":[{"weight":10,"image":<layer>},…]} —
    /// absent "weight" = the author omitted the percentage. Weights
    /// normalize through the shared CrossFadeMath twin (§2.6.2 rules +
    /// the cross-platform pin table live in that file). ANY unparseable
    /// arg drops the WHOLE function — partially-kept args would silently
    /// re-weight the rest (no-silent-fallthrough).
    private static func parseCrossFade(_ o: [String: IRValue], ctx: FontContext) -> BackgroundImageLayer? {
        guard let args = o["args"]?.arrayValue, !args.isEmpty else { return nil }
        // Authored weights: nil = omitted (key absent on the wire).
        let weights = args.map { $0.objectValue?["weight"]?.doubleValue }
        // Sub-images recurse through the same per-layer parser.
        let images = args.map { $0.objectValue?["image"].flatMap { parseLayer($0, ctx: ctx) } }
        if images.contains(where: { $0 == nil }) { return nil }
        let fractions = CrossFadeMath.normalizeWeights(weights)
        return .crossFade(zip(fractions, images).map { CrossFadeArg(weight: $0, layer: $1!) })
    }

    /// One gradient-center axis from the IRLengthPercentage wire
    /// (ValueTypes.kt §IRLengthPercentageSerializer):
    ///   raw number          → percentage → .fraction(n / 100)
    ///   {"px": N}           → absolute length → .px(N)
    ///   {"original":{v,u}}  → runtime-dependent unit, resolved HERE
    ///     against the component's font metrics (lh/rlh/em/rem — the
    ///     same ratios SpacingResolver pins; `normal` ≈ 1.2 × font-size).
    ///     Unsupported units (vw/ch/…) fall back to `.center` with the
    ///     fallthrough documented here — they cannot resolve without a
    ///     viewport, and the CSS default center is the least-wrong answer.
    /// Absent axis → `.center` (the CSS `at` default, css-images-3 §3.5).
    static func readCoord(_ v: IRValue?, ctx: FontContext) -> GradientCoord {
        guard let v = v else { return .center }
        // Raw number = percent (legacy wire; also the keyword mappings).
        if let n = v.doubleValue { return .fraction(n / 100) }
        guard case .object(let o) = v else { return .center }
        // Absolute length — the converter normalized the unit to px.
        if let px = o["px"]?.doubleValue { return .px(px) }
        // Runtime-dependent unit: typed original with pixels ABSENT is
        // the "null means runtime-dependent" IR convention.
        if let orig = o["original"]?.objectValue,
           let val = orig["v"]?.doubleValue,
           let unit = orig["u"]?.stringValue {
            switch unit {
            // lh = used line-height (multiplier × font-size when the
            // component declares one; `normal` ≈ 1.2em otherwise).
            case "LH": return .px(val * ctx.lineHeightPx)
            // rlh anchors to the ROOT line-height; without a root context
            // here we use the CSS-initial 16px × 1.2 — the same lockstep
            // ratio SpacingResolver applies.
            case "RLH": return .px(val * 19.2)
            // em/rem — font-relative (css-values-4 §5.2).
            case "EM": return .px(val * ctx.fontSizePx)
            case "REM": return .px(val * 16)
            // Viewport/other units need context this engine doesn't
            // have — documented fallback to the CSS default center.
            default: return .center
            }
        }
        return .center
    }

    // Angle helper. IR shape: `{deg: Double}`.
    private static func readAngle(_ v: IRValue?) -> Double? {
        guard case .object(let o) = v else { return nil }
        return o["deg"]?.doubleValue
    }

    // Stop parser with shape-keyword recovery for radial gradients.
    // Any stop whose `color` object has no `srgb` AND is not a dynamic
    // colour is treated as a shape keyword leakage; we capture the
    // `original` string and skip that stop.
    private static func parseStops(_ raw: [IRValue], interp: GradientInterpolation = .legacy)
        -> (stops: [BackgroundImageStop], shapeKeyword: String?) {

        var out: [BackgroundImageStop] = []
        var shape: String? = nil

        for entry in raw {
            guard case .object(let o) = entry else { continue }
            // Each stop is {color: IRValue, position: Double?|null}.
            let colorValue = o["color"] ?? .null
            let positionRaw = o["position"]

            // Detect the malformed "shape-as-stop" entry: color has only
            // `original` (a string keyword) and no `srgb` / no `type`.
            if case .object(let co) = colorValue,
               co["srgb"] == nil,
               co["original"]?.stringValue != nil,
               co["type"] == nil {
                if shape == nil {
                    shape = co["original"]?.stringValue
                }
                continue
            }

            // Normal stop: colour + optional position.
            let color = extractColor(colorValue)
            let pos: Double? = {
                if let d = positionRaw?.doubleValue { return d / 100.0 }
                return nil
            }()
            // Wave 46: the <length> arm — `positionLength: {px: N}`
            // (absolute, reader-normalised). Runtime-dependent units
            // ({original:{v,u}} with no px) stay unpositioned here: the
            // gradient line has no font context of its own, and a
            // breadcrumb beats a guessed period.
            let lenObj = o["positionLength"]?.objectValue
            let posPx = lenObj?["px"]?.doubleValue
            if lenObj != nil && posPx == nil {
                PropertyTracker.logOnce(key: "gradient-stop-length-unit",
                    message: "gradient stop <length> in a runtime-dependent unit — treated as unpositioned on iOS")
            }
            out.append(BackgroundImageStop(color: color, position: pos,
                                           positionPx: posPx, interp: interp))
        }
        return (out, shape)
    }
}
