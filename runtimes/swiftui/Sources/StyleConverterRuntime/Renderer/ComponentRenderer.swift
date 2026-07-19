//
//  ComponentRenderer.swift
//  StyleConverterTest
//
//  Recursive SwiftUI renderer: IRComponent → View.
//
//  Picks the right layout container based on `Display` / `FlexDirection`, then
//  applies the rest of the style via StyleBuilder + .applyStyle(). Children
//  render recursively; leaves fall back to a placeholder label showing the
//  component's name.
//

import SwiftUI

// public: this is the package's primary product surface — the harness
// (CaptureCanvas, ComponentGallery) and any SDUI consumer instantiate it
// to turn an IRComponent into a SwiftUI view.
public struct ComponentRenderer: View {
    let component: IRComponent

    // Fidelity wave 1 — grid stretch injection. css-align-3 §9: a grid
    // item whose block size is `auto` and whose align-self resolves to
    // stretch fills its row track. The parent grid computes the row
    // height statically (explicit grid-template-rows) and passes it via
    // this environment slot; we fold it into SizeConfig BEFORE the style
    // chain so the child's own background/border paint at the stretched
    // height (an outer .frame can't reach the child's paint chain).
    @Environment(\.gridStretchHeight) private var gridStretchHeight

    // Fidelity wave 2 — column-flex analogue of gridStretchHeight:
    // css-flexbox-1 §8.3 stretch on the INLINE axis. A column-flex
    // parent with a definite width publishes its content width here for
    // children whose align resolves to stretch and whose width is auto;
    // folded into SizeConfig below so the child's own paint chain
    // (background/borders) covers the stretched width.
    @Environment(\.flexStretchWidth) private var flexStretchWidth

    // Fidelity wave 3 — containing-block width channel (css-sizing-3
    // §5.1). Parents with a statically-definite width publish their
    // CONTENT-BOX width here; this component resolves its own percent
    // width against it (threaded into SpacingContext below) instead of
    // against the capture canvas. Nil → canvas fallback (root
    // components, children of fit-content parents). See
    // ContainingBlock.swift for the full rationale.
    @Environment(\.containingBlockWidth) private var containingBlockWidth

    // Wave 9 — the HEIGHT twin of the channel above (CSS 2.1 §10.5):
    // parents with a statically-definite height publish a basis here so
    // percent heights (notably `height: 100%` on absolutely-positioned
    // children, css-position-3 §3.1) can resolve. Nil → indefinite →
    // percent heights keep degrading to auto (the ScrollView rationale
    // documented in SizeApplier). See ContainingBlock.swift.
    @Environment(\.containingBlockHeight) private var containingBlockHeight

    // Lane IOS-COLLAPSE — the CSS 2.1 §8.3.1 margin-collapse channel:
    // a BLOCK parent statically folds its children's vertical margins
    // (sibling max() collapse + first/last hoist-through) and sends each
    // child its USED top/bottom here; the child folds them into its
    // MarginConfig below so its own MarginApplier paints the collapsed
    // values. Nil = no plan (root, non-block parent, ineligible margins)
    // → declared margins apply untouched. See MarginCollapse.swift.
    @Environment(\.marginCollapseOverride) private var marginCollapseOverride

    // TITAN Round 4 (GAP 1, WIDTH half) — the composed-WPT block-flow
    // fill-width channel (WPTCaptureMode.swift). nil everywhere but the
    // composed canvas, which publishes the 358px content-box width on its
    // stacked ROOTS so an auto-width block box paints full-bleed like the
    // browser-ref (the iOS analogue of web's WPT width:auto carve-out). We
    // fold it into SizeConfig below (same path as flexStretchWidth) and
    // reset it to nil for our children so block-fill stays root-scoped.
    @Environment(\.wptBlockFlowFillWidth) private var wptBlockFlowFillWidth

    // Fidelity wave 1 — CSS text inheritance channel. The converter
    // flattens no cascade, so a parent's font-size/family/weight/
    // letter-spacing/text-align never reached child renders on iOS while
    // the web reference inherits them natively (inheritance-typography
    // IT_* trees). Parents publish their inheritable declarations here;
    // we merge them UNDER the component's own (own always wins) before
    // building the style, then re-publish the merged set for
    // grandchildren — reproducing the transitive chain. Mirrors the
    // Android LocalInheritedProperties channel 1:1.
    @Environment(\.inheritedTextProperties) private var inheritedTextProperties

    // Wave 6 (#32) — custom-property scope channel. Parents publish
    // their MERGED VariableStore (their own `variables` map layered
    // over their inherited chain); this component layers its own map on
    // top before resolving var() references (css-variables-1 §2:
    // custom properties inherit like any other inherited property).
    @Environment(\.cssVariables) private var inheritedVariables

    // Wave 6 (#39) — host-published surface geometry. Replaces the
    // hardcoded 390×844 capture-canvas numbers inside SpacingContext:
    // the harness pins its capture frame, a real app publishes its own
    // window. nil keeps the legacy defaults (see StyleViewport.swift).
    @Environment(\.styleViewport) private var styleViewport

    // Wave 7 (#33/#34) — dynamic-styling inputs. The platform dark-mode
    // signal drives BOTH prefers-color-scheme buckets and light-dark()
    // values (spec 06 §4: one surface, one scheme answer)…
    @Environment(\.colorScheme) private var platformColorScheme
    // …the spec 06 §6 forced-state set arrives from the host (the
    // capture harness's -forceState launch argument)…
    @Environment(\.forcedStyleStates) private var forcedStyleStates
    // …and `:disabled` reads SwiftUI's .disabled() propagation channel
    // directly (spec 06 §2: the host supplies the flag).
    @Environment(\.isEnabled) private var isEnabled

    // Wave 7 (#33) — real interaction state, written by the
    // InteractionBridge listeners attached in body (only when this
    // component carries a selector bucket needing them). @State keeps
    // view identity stable across restyles (spec 06 §5).
    @State private var isHovered = false
    @State private var isPressed = false
    // Focus rides SwiftUI's own focus system (spec 06 §2 `focus`).
    @FocusState private var isFocused: Bool

    // WPT reftest capture parity — an ambient host flag (WPTCaptureMode.swift),
    // same shape as styleViewport/styleKeyframes. Default false = product +
    // every committed baseline unchanged; the harness inbox/WPT capture path
    // turns it ON so the SYNTHESIZED component-name placeholder is dropped for
    // nameless-empty leaves (browser-ref parity). Real text still renders.
    // Mirrors the web harness's ?wpt=1 WPT_MODE empty-visible-text path.
    @Environment(\.wptCaptureMode) private var wptCaptureMode

    // Wave 8 (#35) — motion inputs. The document keyframes map arrives
    // from the host (spec 07 §1.2 — @keyframes are document-scoped)…
    @Environment(\.styleKeyframes) private var styleKeyframes
    // …and the spec 07 §5 pinned capture clock: non-nil = evaluate every
    // animation at absolute second t, paused (CAPTURE_ANIMATION_TIME).
    @Environment(\.animationCaptureTime) private var animationCaptureTime

    // Wave 8 — the live animation clock's zero point: this component's
    // mount. CSS starts an animation when it first applies to an element
    // (css-animations-1 §5.1), which for the render tree is view mount.
    @State private var appearDate = Date()
    // Wave 8 — transition flip tracking (spec 07 §4): the pre-flip
    // interaction state (to re-run the pure StateResolver fold for the
    // "old" effective list) and the in-flight snapshot the blend runs
    // against. Both stay nil for the whole static corpus — the motion
    // body branch is gated on hasMotion below.
    @State private var lastInteractionState: ComponentState? = nil
    @State private var transitionSnapshot: TransitionSnapshot? = nil

    /// One in-flight transition: the OLD effective declarations (the
    /// blend's from-side) and the flip instant (elapsed-time zero).
    private struct TransitionSnapshot {
        let base: [IRProperty]
        let startedAt: Date
    }

    /// The spec 06 §4 evaluation environment: render-SURFACE width (the
    /// host-published styleViewport — the capture canvas / app window,
    /// never the device screen; 390 = the legacy canvas default) plus
    /// the platform scheme signal.
    private var mediaEnvironment: MediaQueryEvaluator.Environment {
        MediaQueryEvaluator.Environment(
            surfaceWidthPx: styleViewport?.width ?? 390.0,
            prefersDark: platformColorScheme == .dark)
    }

    /// This component's interaction-state snapshot for StateResolver —
    /// real bridge flags plus the host's forced set.
    private var componentState: ComponentState {
        ComponentState(hovered: isHovered, pressed: isPressed,
                       focused: isFocused, disabled: !isEnabled,
                       // No generic checkable analogue on iOS (spec 06
                       // §2 "checked-if-cheap") — forced-only.
                       checked: false,
                       forced: forcedStyleStates)
    }

    /// Wave 7 — the component's OWN declarations after the spec 06 §3
    /// fold (base → active media buckets → active selector buckets,
    /// wire order, last writer wins) and the light-dark() rewrite.
    /// Bucket-free components return `component.properties` untouched.
    private var effectiveProperties: [IRProperty] {
        effective(for: componentState)
    }

    /// Wave 8 — the same §3 fold parameterized by interaction state:
    /// the transition driver re-runs it with the PRE-flip state to get
    /// the blend's from-side (StateResolver is pure, so old and new
    /// effective lists are both exactly reproducible).
    private func effective(for state: ComponentState) -> [IRProperty] {
        // §3 layering against the given state + live environment.
        let layered = StateResolver.resolve(base: component.properties,
                                            selectors: component.selectors,
                                            media: component.media,
                                            state: state,
                                            environment: mediaEnvironment,
                                            componentName: component.name)
        // css-color-5 light-dark() arms resolve against the same scheme
        // signal — buckets may introduce light-dark values, so this
        // pass runs on the layered output.
        return LightDarkResolver.resolve(layered,
                                         prefersDark: mediaEnvironment.prefersDark)
    }

    /// Wave 8 — the effective list with any IN-FLIGHT transition blended
    /// in (spec 07 §4): after a state flip, tier properties interpolate
    /// old → new over transition-duration/-delay/-timing. `now == nil`
    /// (no motion clock — the whole static corpus) short-circuits to the
    /// plain effective list, allocation-identical to pre-wave-8.
    private func motionEffectiveProperties(now: Date?) -> [IRProperty] {
        guard let now = now, let snap = transitionSnapshot else {
            return effectiveProperties
        }
        return TransitionResolver.blend(from: snap.base,
                                        to: effectiveProperties,
                                        elapsedSeconds: now.timeIntervalSince(snap.startedAt),
                                        componentName: component.name).properties
    }

    /// The component's declarations with the parent's inheritable text
    /// properties merged underneath (css-cascade-4 inheritance).
    /// Wave 7: `own` is the state/media-RESOLVED list, so an active
    /// bucket's values flow into extraction and into the inheritance
    /// channel republished to children exactly like base declarations.
    private var mergedProperties: [IRProperty] {
        mergedProperties(now: nil)
    }

    /// Wave 8 — clock-parameterized variant: the transition blend runs
    /// UNDER the inheritance merge so blended values extract exactly
    /// like base declarations. nil clock = byte-identical legacy path.
    /// Lane IOS-TEXT (color channel): `color: currentColor` computes to
    /// `inherit` (css-color-4 §7.3), so the own declaration is dropped
    /// BEFORE the merge — the ancestor's Color then flows in and every
    /// merged-list consumer (glyph color, border/outline currentColor)
    /// resolves against it instead of the placeholder contrast pick.
    private func mergedProperties(now: Date?) -> [IRProperty] {
        InheritedText.merge(
            own: InheritedText.resolvingCurrentColorOnColor(
                motionEffectiveProperties(now: now)),
            inherited: inheritedTextProperties)
    }

    /// Wave 9 (#37) — true when the merged list's `Color` arrived ONLY
    /// through the inheritance channel (no own declaration, buckets
    /// included). Gates the LEAF PlaceholderLabel color back to the
    /// contrast pick for web parity: the reference placeholder span
    /// always pins its own color, so DOM inheritance never reaches leaf
    /// placeholder glyphs on web. Mirrors Android's colorIsInheritedOnly
    /// + LocalColorIsInheritedOnly pair.
    private var colorIsInheritedOnly: Bool {
        inheritedTextProperties.contains { $0.type == "Color" } &&
            !effectiveProperties.contains { $0.type == "Color" }
    }

    /// Wave-5 gate follow-up — `color: currentColor` declared with NO
    /// ancestor Color to resolve against (see
    /// InheritedText.currentColorBottomsOut). The label then paints the
    /// runtime default text color (web-harness body `#eee`, opaque)
    /// instead of the 70%-alpha contrast pick the device gate measured
    /// diverging (0.856). Reads the same effective/inherited lists as
    /// colorIsInheritedOnly above so the two gates stay in one frame.
    private var currentColorBottomsOut: Bool {
        InheritedText.currentColorBottomsOut(own: effectiveProperties,
                                             inherited: inheritedTextProperties)
    }

    /// Does any selector bucket resolve to `condition` at runtime v1?
    /// Gates the InteractionBridge listeners so ONLY selector-carrying
    /// components pay for input tracking.
    private func wantsCondition(_ condition: String) -> Bool {
        component.selectors?.contains {
            StateResolver.normalize($0.condition) == condition
        } ?? false
    }

    /// This element's full custom-property scope: own definitions
    /// shadowing the slot-parent chain (css-variables-1 §2 nearest-wins).
    private var mergedVariables: VariableStore {
        VariableStore.merge(inherited: inheritedVariables,
                            own: component.variables)
    }

    /// Wave 6 — the inheritance-merged declarations with every dynamic
    /// value RESOLVED: var() references substituted from the scope
    /// chain (§2.3, guaranteed-invalid ⇒ dropped = unset) and preserved
    /// calc()/relative expressions evaluated against the wave-3
    /// containing-block channel + the inheritance channel's font size.
    /// Static declarations pass through byte-identical, so variable-free
    /// fixtures render exactly as before.
    private var resolvedProperties: [IRProperty] {
        resolvedProperties(now: nil)
    }

    /// Wave 8 — clock-parameterized variant of the wave-6 resolution
    /// chain (transition blend rides underneath via mergedProperties).
    /// Inheritance PUBLICATION to children intentionally keeps reading
    /// the nil-clock list: animated values do not flow into the
    /// inheritance channel at motion runtime v1 (documented limitation —
    /// the motion fixtures animate leaf boxes only).
    private func resolvedProperties(now: Date?) -> [IRProperty] {
        // em/rem base: the PARENT's computed font size, read from the
        // inheritance channel (the parent publishes resolved pixels).
        let inheritedFs = inheritedTextProperties
            .last(where: { $0.type == "FontSize" })
            .flatMap { ValueExtractors.extractPx($0.data) }
            .map(Double.init) ?? 16.0
        // calc() bases — same geometry the style chain itself uses:
        // host viewport (#39, legacy 390×844 when unpublished) and the
        // wave-3 containing block (root fallback mirrors
        // SpacingContext.containingBlockWidth exactly).
        var ctx = CalcEvaluator.EvalContext()
        ctx.viewportWidth = styleViewport?.width ?? 390.0
        ctx.viewportHeight = styleViewport?.height ?? 844.0
        ctx.percentBasisPx = containingBlockWidth.map(Double.init)
            ?? styleViewport?.rootContainingBlock
            ?? (ctx.viewportWidth - 32)
        return DynamicValueResolver.resolve(properties: mergedProperties(now: now),
                                            variables: mergedVariables,
                                            calc: ctx,
                                            inheritedFontSizePx: inheritedFs)
    }

    /// Wave 8 — THE style-chain input at motion runtime v1: the resolved
    /// chain with the keyframe-animation overlay applied at clock time t
    /// (spec 07 §2/§3). Precedence mirrors the CSS cascade origins:
    /// transitions blend inside the chain (mergedProperties), keyframe
    /// animations overlay LAST (the animation origin sits above author
    /// styles, css-cascade-5 §6.2). Keyframe-free documents and nil-clock
    /// renders return the resolved list untouched.
    private func displayProperties(now: Date?) -> [IRProperty] {
        let resolved = resolvedProperties(now: now)
        guard let kf = styleKeyframes, !kf.isEmpty else { return resolved }
        // Clock selection (spec 07 §5): the pinned capture time WINS over
        // everything (including play-state paused — the resolver handles
        // that composition); a live clock counts from view mount; no
        // clock at all (hasMotion false but keyframes present — e.g. a
        // dangling name) evaluates the initial frame at t = 0.
        let t: Double
        let pinned: Bool
        if let cap = animationCaptureTime {
            t = cap; pinned = true
        } else if let now = now {
            t = max(0, now.timeIntervalSince(appearDate)); pinned = false
        } else {
            t = 0; pinned = false
        }
        return AnimationResolver.resolve(properties: resolved, keyframes: kf,
                                         atSeconds: t, pinnedClock: pinned,
                                         componentName: component.name)
    }

    // MARK: - Motion gate (wave 8)

    /// Does this component need the motion body branch at all? True only
    /// when (a) the document defines keyframes AND the component (base or
    /// any bucket) references animation-name, or (b) the component
    /// declares transitions AND carries interactive buckets to flip.
    /// CONSTANT for a given component + document, so the body's branch
    /// identity never switches mid-life (state stays stable) — and false
    /// for the entire pre-wave-8 corpus, which keeps every committed
    /// baseline on the exact legacy view tree.
    private var hasMotion: Bool {
        if styleKeyframes?.isEmpty == false, containsPropertyType("AnimationName") {
            return true
        }
        if component.selectors?.isEmpty == false, containsPropertyType("TransitionDuration") {
            return true
        }
        return false
    }

    /// Scan base + selector + media declarations for a property type
    /// (buckets can introduce animation/transition longhands too).
    private func containsPropertyType(_ type: String) -> Bool {
        if component.properties.contains(where: { $0.type == type }) { return true }
        if component.selectors?.contains(where: { $0.properties.contains { $0.type == type } }) == true { return true }
        if component.media?.contains(where: { $0.properties.contains { $0.type == type } }) == true { return true }
        return false
    }

    /// Wave 8 — flip handler (spec 07 §4): snapshot the pre-flip
    /// effective list as the blend's from-side and (re)start the clock.
    /// A flip DURING an in-flight transition starts the new leg from the
    /// CURRENT blended value (css-transitions-1 §3 reversing behavior,
    /// v1 approximation: no shortened reverse duration).
    private func beginTransition(to newState: ComponentState) {
        let oldState = lastInteractionState
            ?? ComponentState(forced: forcedStyleStates) // pre-onAppear fallback: base flags
        lastInteractionState = newState
        let oldEffective = effective(for: oldState)
        // Post-flip list governs whether anything animates at all
        // (css-transitions-1 §3: after-change style's transition-*).
        guard TransitionResolver.hasTransitions(effective(for: newState)) else {
            transitionSnapshot = nil
            return
        }
        // From-side: mid-flight flips depart from the blended value.
        let from: [IRProperty]
        if let snap = transitionSnapshot {
            from = TransitionResolver.blend(from: snap.base, to: oldEffective,
                                            elapsedSeconds: Date().timeIntervalSince(snap.startedAt),
                                            componentName: component.name).properties
        } else {
            from = oldEffective
        }
        transitionSnapshot = TransitionSnapshot(base: from, startedAt: Date())
    }

    // MARK: - WPT placeholder suppression

    /// WPT capture mode (mirror of the web harness's `?wpt=1` path):
    /// should this component's SYNTHESIZED component-name placeholder be
    /// suppressed for browser-ref parity? Pure + static so the XCTest
    /// pins the classification without a render surface, exactly like
    /// `isOutOfFlow` below.
    ///
    /// The rule, matching web's PlaceholderContent visible-text priority
    /// (apps/web-harness/src/sdui/ComponentRenderer.tsx):
    ///   • flag OFF → NEVER suppress. This is the product path and the
    ///     whole committed baseline corpus, so those renders are byte-
    ///     identical to before.
    ///   • a component WITH children renders no synthesized name at all
    ///     (children — or leading real text — are the content) → nothing
    ///     to suppress.
    ///   • a leaf WITH real element text (`text`, the IR `_text`/rawText
    ///     channel) → that text is actual content and always renders; web
    ///     gives it precedence over the WPT empty-string suppression.
    ///   • a leaf WITHOUT real text in WPT mode → the placeholder would
    ///     paint only the debug NAME the browser-ref never shows → suppress.
    static func suppressesNamePlaceholder(_ component: IRComponent,
                                          wptCaptureMode: Bool) -> Bool {
        // Product / baseline path: the flag is off → keep the placeholder.
        guard wptCaptureMode else { return false }
        // With children there is no synthesized name placeholder to drop.
        guard component.children?.isEmpty ?? true else { return false }
        // Leaf: suppress ONLY when there is no real element text. `text`
        // nil/empty ⇒ the leaf branch would fall back to component.name.
        return component.text?.isEmpty ?? true
    }

    // MARK: - WPT composed line-box calibration (TITAN Round 4 GAP 1, height half)

    /// The Chromium browser-ref's `<p>` line box at a 16px root — 20px,
    /// recalibrated at the corpus-v4.1 LINE-HEIGHT sub-boundary. The
    /// Round-4 value was 18: back then capture-browser-ref.mjs forced NO
    /// font and Chromium's default-SERIF `line-height: normal` box was
    /// ~18px @16px, ~2px SHORTER than the harness Inter box — pinning 18
    /// removed the per-bar compounding drift down stacked tests. From
    /// corpus-v4.1 the ref pins the harness Inter face AND an explicit
    /// deterministic `line-height: 1.25` (capture-browser-ref.mjs
    /// REF_LINE_HEIGHT — 20px @16px, Chromium's measured natural Inter
    /// rhythm: default ref paragraphs advance 36px top-to-top), so the OLD
    /// 18px calibration became the drift (captures advanced 34px — 2px
    /// re-accumulating per paragraph, the css-color/css-break/css-flexbox
    /// collapse of the first v4.1 run). 20 mirrors the ref pin at the
    /// 16px default root; web pins the same unitless 1.25
    /// (index.html wpt rules + ComponentRenderer.tsx), Compose the same
    /// ratio (REF_DEFAULT_FONT_LINE_HEIGHT_RATIO 1.25).
    public static let wptRefLineBoxPx: CGFloat = 20

    /// The line-height a placeholder text run should lay out with. Outside
    /// WPT capture (the product path + the whole committed baseline corpus)
    /// this is IDENTITY — the IR's declared line-height, or nil for
    /// SwiftUI's natural `line-height: normal` metrics — so every existing
    /// render is byte-unchanged. In WPT capture it DEFERS to any
    /// IR-declared line-height (a test that sets its own keeps it) and
    /// otherwise pins the ref line box (20px @16px since the corpus-v4.1
    /// REF_LINE_HEIGHT pin) so a forced-Inter bar matches the browser-ref's
    /// pinned `<p>` height, removing the compounding drift. Pure + static
    /// so WPTCaptureModeTests pins it without a render surface (same
    /// pattern as suppressesNamePlaceholder).
    public static func effectiveLineHeight(declared: CGFloat?,
                                           wptCaptureMode: Bool) -> CGFloat? {
        // IR-declared line-height always wins (author > our calibration).
        if let declared { return declared }
        // Bare text: pin the ref line box only under WPT capture; otherwise
        // nil = SwiftUI's natural metrics (unchanged product behaviour).
        return wptCaptureMode ? wptRefLineBoxPx : nil
    }

    // MARK: - WPT block-child auto-width fill (wave 9)

    /// The `wptBlockFlowFillWidth` value a parent publishes for its
    /// IN-FLOW children — the wave-9 extension of the Round-4 root-only
    /// fill. CSS 2.1 §10.3.3: a block-level box with `width: auto` fills
    /// its containing block; the composed-WPT roots already do (the
    /// canvas publishes the channel), but every CHILD block got zero /
    /// intrinsic width because the channel was hard-reset to nil at each
    /// level — so a complete url-background pipeline painted nothing on
    /// the css-break tests (the box it painted had no width). Pure +
    /// static so the XCTest pins the scoping without a render surface.
    ///
    /// Scope guards, in order:
    ///   • wptCaptureMode only — the property-fixture path has its own
    ///     width conventions (fit-content hug), so the 327 committed
    ///     baselines (flag OFF) are untouched.
    ///   • BLOCK containers only — flex/grid parents size their items
    ///     via the flex/grid algorithms (css-flexbox-1 §9 / css-grid-1
    ///     §11), and inline parents establish no block context; they
    ///     all keep publishing nil (the pre-wave-9 reset).
    ///   • never for ol/ul marker rows — the synthesized marker HStack
    ///     already holds the row; a full-width li would overflow it by
    ///     the marker's advance.
    ///   • the value is the parent's CONTENT-box width (the containing
    ///     block, same childCB the width channel publishes) — nil when
    ///     the parent's own width is not statically definite.
    ///   • MULTICOL parents (wave-9 regression fix): css-multicol-1 §2 —
    ///     the containing block of a multicol element's children is the
    ///     COLUMN box, not the container. Publishing the full container
    ///     width stretched a block child across all columns (css-break
    ///     background-image-001: iOS SSIM 0.140 → 0.040), so when the
    ///     parent's computed column-count/column-width establishes a
    ///     multicol context, the basis is the §3 USED column width
    ///     (MulticolMath — the same used-value math the Android multicol
    ///     wedge fix pinned), computed from the identical content-box
    ///     width plus the used column-gap the caller resolves.
    /// The receiving fold in styledContent re-checks wptCaptureMode +
    /// width:auto + in-flow + non-inline before consuming the value.
    static func wptChildFillWidth(parentDisplay: LayoutConfig.DisplayType,
                                  wptCaptureMode: Bool,
                                  isMarkerRow: Bool,
                                  parentContentWidth: CGFloat?,
                                  parentColumns: ColumnsConfig?,
                                  columnGapPx: CGFloat) -> CGFloat? {
        // Never outside WPT capture: baselines + product stay byte-stable.
        guard wptCaptureMode else { return nil }
        // Only block containers establish the §10.3.3 fill context (a
        // multicol container IS a block container — css-multicol-1 §1).
        guard parentDisplay == .block else { return nil }
        // Marker rows lay the li out inside an HStack — no fill.
        guard !isMarkerRow else { return nil }
        // The containing block itself (nil = indefinite parent → no fill).
        guard let contentWidth = parentContentWidth else { return nil }
        // Multicol parent → the child's containing block is the COLUMN
        // box (§2): fill basis = the §3 used per-column width. usedColumns
        // returns nil when neither count nor width is non-auto (not a
        // multicol container — e.g. column-rule alone), falling through
        // to the plain block basis below.
        if let used = MulticolMath.usedColumns(
                availableWidthPx: Double(contentWidth),
                requestedCount: parentColumns?.count,
                requestedWidthPx: parentColumns?.widthPx,
                gapPx: Double(columnGapPx)) {
            return CGFloat(used.widthPx)
        }
        // Plain block container: the content-box width IS the basis.
        return contentWidth
    }

    /// Resolve the background color a property list paints, via the SAME
    /// engine the renderer uses (StyleBuilder), returning nil when none is
    /// declared. Public so the harness's ComposedCaptureCanvas can read a
    /// document body-root's background to paint the composed canvas (TITAN
    /// Round 4 GAP 2) byte-identically to how the runtime would render it —
    /// never a re-implemented color parser. Mirrors web's
    /// `buildStyles(bodyRoot.properties).backgroundColor`.
    public static func resolvedBackgroundColor(from properties: [IRProperty]) -> Color? {
        StyleBuilder.build(from: properties).backgroundColor
    }

    // MARK: - Flow membership (fidelity wave 3)

    /// True when a child is removed from normal flow — css-position-3
    /// §2.1: `position: absolute | fixed` boxes are absolutely
    /// positioned, do not participate in block flow, and are not
    /// flex/grid items (css-flexbox-1 §4 / css-grid-1 §6). Pure so the
    /// wave-3 XCTest pins the classification.
    ///
    /// public: the iOS harness's CaptureCanvas reuses this to give a
    /// ROOT-level absolute component the same card treatment the web
    /// canvas produces (collapsed flow height + overflow clip) — one
    /// classification, two consumers.
    public static func isOutOfFlow(_ child: IRComponent) -> Bool {
        let p = LayoutExtractor.extract(from: child.properties)?.position
        return p == .absolute || p == .fixed
    }

    /// Children that participate in the parent's normal flow (block /
    /// flex / grid layout). Everything the flow container renders.
    private var inFlowChildren: [IRComponent] {
        (component.children ?? []).filter { !Self.isOutOfFlow($0) }
    }

    /// Absolutely-positioned children. Rendered as a ZStack overlay on
    /// TOP of the in-flow content — CSS 2.1 Appendix E paint order:
    /// positioned descendants (step 8) paint after in-flow blocks and
    /// inlines (steps 4–7). The previous renderer put ALL children in
    /// one ZStack, so (a) in-flow siblings lost their block stacking
    /// and (b) a later in-flow sibling painted OVER the absolute box
    /// (trees/block-flow B_RelativeAnchor: the red top:10/left:20 box
    /// showed only a sliver below the orange in-flow sibling).
    private var outOfFlowChildren: [IRComponent] {
        (component.children ?? []).filter { Self.isOutOfFlow($0) }
    }

    /// Wave 5 — NEGATIVE z-index positioned children (CSS 2.1 Appendix E
    /// step 3: they paint BEFORE the in-flow content of their stacking
    /// context, i.e. visually BEHIND this element's own background). The
    /// overlay ZStack put them on top, so `z-index: -1` boxes showed
    /// through the parent's opaque background where web hides them
    /// (PL_ZNegative). They render via `.background` OUTSIDE the paint
    /// chain instead — behind everything this element paints.
    private var negativeZChildren: [IRComponent] {
        outOfFlowChildren.filter {
            (ItemPlacementExtractor.extract(from: $0.properties).paint.zIndex ?? 0) < 0
        }
    }

    /// The overlay half of the split: positioned children with
    /// non-negative z-index — Appendix E step 8, above in-flow content.
    private var overlayChildren: [IRComponent] {
        outOfFlowChildren.filter {
            (ItemPlacementExtractor.extract(from: $0.properties).paint.zIndex ?? 0) >= 0
        }
    }

    // public: explicit memberwise init — the synthesized one is internal,
    // so cross-module callers need this spelled out.
    public init(component: IRComponent) {
        self.component = component
    }

    // public: View protocol witness on a public type must be public.
    public var body: some View {
        // Wave 8 — the motion branch is gated on hasMotion, which is
        // CONSTANT per component + document: the static corpus renders
        // through the identical pre-wave-8 tree (right branch), while
        // animated/transitioning components get a TimelineView frame
        // clock. The pinned capture clock (spec 07 §5) pauses the
        // schedule outright — one deterministic frame at t, byte-stable
        // under ImageRenderer.
        if hasMotion {
            TimelineView(.animation(minimumInterval: nil,
                                    paused: animationCaptureTime != nil)) { timeline in
                styledContent(now: timeline.date)
            }
            // Transition flip tracking (spec 07 §4): seed the pre-flip
            // state at mount, snapshot the old effective list per flip.
            // v1 note: the live schedule keeps ticking after finite
            // animations settle (the schedule's `paused` is evaluated at
            // body time, not per frame) — fill-mode frames re-render
            // identically, so this costs battery in the live gallery,
            // never correctness. The capture path is always paused.
            .onAppear { if lastInteractionState == nil { lastInteractionState = componentState } }
            .onChange(of: componentState) { beginTransition(to: $0) }
        } else {
            styledContent(now: nil)
        }
    }

    /// The full pre-wave-8 body, parameterized by the motion clock.
    /// `now == nil` (static corpus) renders byte-identically to the
    /// legacy path — displayProperties collapses to resolvedProperties.
    @ViewBuilder
    private func styledContent(now: Date?) -> some View {
        // Build the style from the inheritance-merged, variable-RESOLVED
        // declarations (wave 6 — extraction sees concrete values), then
        // fold in the grid-stretch height (if the parent injected one and
        // the IR declared no explicit height — an explicit height always
        // wins per css-align-3 §9's "auto block size" precondition).
        let style: ComponentStyle = {
            var s = StyleBuilder.build(from: displayProperties(now: now))
            // Wave 6 (#39) — adopt the host-published surface geometry
            // FIRST so every resolver lane below (vw/vh, percent bases,
            // GeometryReader fallbacks) uses the capture canvas / app
            // window instead of the old hardcoded 390×844 literals.
            if let vp = styleViewport {
                s.spacing.context.viewportWidth = vp.width
                s.spacing.context.viewportHeight = vp.height
                s.spacing.context.rootContainingBlockPx = vp.rootContainingBlock
            }
            // Fidelity wave 3 — thread the parent-published containing
            // block into the resolver context FIRST so every percent
            // width below (own width, flex plans, child publication)
            // resolves against the parent's content box (css-sizing-3
            // §5.1) instead of the canvas.
            s.spacing.context.containingBlockWidthPx = containingBlockWidth.map(Double.init)
            // Wave 9 — thread the parent-published containing-block
            // HEIGHT the same way, so this box's own percent height
            // (and its children's bases, via ContainingBlockBasis)
            // resolve against a definite ancestor basis (CSS 2.1
            // §10.5); nil keeps the percent-height skip.
            s.spacing.context.containingBlockHeightPx = containingBlockHeight.map(Double.init)
            // TITAN WPT lane (wave 11) — the box-sizing UA default.
            // css-sizing-3 §3: the INITIAL value of box-sizing is
            // content-box, and WPT refs are authored against that UA
            // default, while SizeConfig.boxSizing nil deliberately means
            // "the border-box status quo" (the whole dark-stage corpus is
            // captured against the web harness's `* { box-sizing:
            // border-box }` reset). In WPT capture mode ONLY, an
            // UNDECLARED box-sizing therefore defaults to contentBox —
            // the iOS twin of the web harness's `body.wpt-mode
            // [data-component-id] { box-sizing: content-box }` override
            // and Compose's SizingApplier.effectiveBoxSizing. A DECLARED
            // keyword always wins verbatim; the pure decision is pinned
            // in WPTCaptureModeTests (wptCaptureMode false → nil stays
            // nil, so the dark-stage path is byte-identical). Folded
            // BEFORE the size-injection folds below so each can convert
            // its border-box FRAME extent to the declared-content slot.
            // Wave 12 closed the former scoped limitation here: the
            // percent-basis lanes (flexContentSize / ContainingBlockBasis
            // contentBox+paddingBox) now honour the effective keyword —
            // under content-box the declared size passes through as the
            // content box instead of losing the bands twice.
            s.size.boxSizing = SizeApplierMath.effectiveBoxSizing(
                declared: s.size.boxSizing, wptCaptureMode: wptCaptureMode)
            if let h = gridStretchHeight, s.size.height == nil {
                // The injected row height is a border-box FRAME extent
                // from the parent's stretch plan (css-align-3 §9 sizes
                // the item's margin/border box into the track). Under
                // the WPT content-box default above the declared slot
                // means CONTENT, so subtract this box's own bands
                // (contentBoxInflation — 0 unless effective contentBox,
                // keeping every non-WPT render byte-identical) so the
                // painted frame still equals the injected track height.
                s.size.height = .exact(px: SizeApplierMath.declaredFromFrame(
                    h, inflate: StyleBuilder.contentBoxInflation(s).v))
            }
            // Column-flex stretch (fidelity wave 2): same fold as the
            // grid/row channel but on the inline axis. An explicit CSS
            // width always wins (css-align-3 §9 auto-size precondition).
            if let w = flexStretchWidth, s.size.width == nil {
                // Same frame→declared conversion as the grid fold above
                // (identity whenever the effective box-sizing is not
                // contentBox — i.e. everywhere outside WPT capture).
                s.size.width = .exact(px: SizeApplierMath.declaredFromFrame(
                    w, inflate: StyleBuilder.contentBoxInflation(s).h))
            }
            // TITAN Round 4 (GAP 1, WIDTH half) — composed-WPT block-flow
            // fill. A block box with width:auto fills its containing block
            // (CSS 2.1 §10.3.3); iOS hugs by default, so in composed WPT
            // capture we fold the canvas-published content-box width into an
            // auto-width, IN-FLOW box so its background paints full-bleed
            // like the browser-ref (mirror of web's WPT width:auto carve-out).
            // Gated four ways so nothing else moves: wptCaptureMode (never
            // the product/baseline), a non-nil channel (the composed canvas
            // sets it on ROOTS, and — wave 9 — a BLOCK container re-publishes
            // its content-box width for its in-flow children, see the flow
            // ForEach's wptChildFillWidth publication), an auto width (an IR
            // width always wins), and in-flow only (absolute/fixed boxes
            // size to their offsets). Wave 9 adds the inline guard: CSS 2.1
            // §10.3.3 fills BLOCK-LEVEL boxes only — a `display: inline`
            // box sizes to its content (§10.3.1) and must never fill.
            if wptCaptureMode, let w = wptBlockFlowFillWidth,
               s.size.width == nil, !Self.isOutOfFlow(component),
               s.layout.display != .inline {
                // Wave 11: the published fill width is the containing
                // block's content width — the box's target FRAME extent
                // (CSS 2.1 §10.3.3: margin+border+padding+content fill
                // the containing block, so content = CB − bands). Under
                // the WPT content-box default the declared slot means
                // CONTENT, so the frame→declared conversion IS exactly
                // that §10.3.3 subtraction; without it a padded/bordered
                // block filled CB + bands and overflowed the browser-ref.
                s.size.width = .exact(px: SizeApplierMath.declaredFromFrame(
                    w, inflate: StyleBuilder.contentBoxInflation(s).h))
            }
            // Fidelity wave 3 — multicol full-width default
            // (Columns_Decorated, 0.556 → worst wave-3 row). A multicol
            // container is a BLOCK container (css-multicol-1 §1) whose
            // max-content inline size is N × content-max-content +
            // (N−1) × gap — for any real content with N ≥ 2 that
            // overflows the canvas, so the web reference (fit-content
            // capped by `max-width: 100%`, ComponentRenderer.tsx) lays
            // it out exactly containing-block wide while iOS hugged the
            // placeholder into a ~210px pill. Fold the containing-block
            // width in as the used width; an explicit CSS width always
            // wins. TODO: small-content multicol boxes (N × max-content
            // < canvas) would need static text measurement to hug.
            if s.size.width == nil, let n = s.columns?.count, n >= 2 {
                // Wave 11: same frame→declared conversion as the folds
                // above — the containing-block width is the multicol
                // box's target FRAME extent (identity outside WPT mode).
                s.size.width = .exact(px: Double(SizeApplierMath.declaredFromFrame(
                    s.spacing.context.containingBlockWidth,
                    inflate: StyleBuilder.contentBoxInflation(s).h)))
            }
            // Lane IOS-COLLAPSE (CSS 2.1 §8.3.1) — fold the parent's
            // collapse override into this box's margin BEFORE the style
            // chain: the used top/bottom (sibling max() collapse, 0 for
            // hoisted edges) replace the declared ones so MarginApplier
            // paints the collapsed geometry. Identity when nil (no plan)
            // — every override-free render is byte-unchanged.
            s.spacing.margin = MarginCollapse.applying(marginCollapseOverride,
                                                       to: s.spacing.margin)
            return s
        }()

        if style.layout.display == .none {
            EmptyView()
        } else {
            // Wave 8 (lane IOS paint-order) — the styled box builds in
            // THREE stages so positioned descendants slot in at the CSS
            // 2.1 Appendix E boundary. Stage 1: the flow container plus
            // applyBoxDecoration — the element's own background/border
            // paint (Appendix E steps 2–4), the LOWEST layers of this
            // element. Previously the absolute-child ZStack sat INSIDE
            // the whole applyStyle chain, so the container's border
            // stroke (an `.overlay` in BorderSideApplier) painted ABOVE
            // its absolutely-positioned children — inverting Appendix E
            // (positioned descendants are step 8, after the ancestor's
            // border) and stroking e.g. a 3px black border across an
            // overlapping abspos child.
            let decoratedBox = AnyView(
                flowContainer(style: style).applyBoxDecoration(style))
            // Stage 2: non-negative-z positioned children attach via
            // `.overlay` ON the painted box — SwiftUI overlays paint
            // above everything applied so far, which is exactly step 8
            // (above the ancestor's background/border). The inner inset
            // pads the child ZStack from the border box down to the
            // PADDING box: css-position-3 §3.1 makes the positioned
            // ancestor's padding box the containing block, so `top:0 /
            // left:0` lands just INSIDE the border band. As a bonus the
            // overlay never feeds back into the container's measured
            // size — out-of-flow boxes don't size their ancestors
            // (css-position-3 §2.1), where the old shared ZStack let a
            // large abspos child inflate an auto-sized parent.
            let withOverlay = overlayChildren.isEmpty
                ? decoratedBox
                : AnyView(decoratedBox.overlay(alignment: .topLeading) {
                    ZStack(alignment: .topLeading) {
                        absoluteOverlay(style: style)
                    }
                    // Border-band inset → padding-box anchoring. Shares
                    // the exact band computation the content inset uses
                    // (AllBordersConfig.bandInsets), so children and
                    // borders can never disagree about the band.
                    .padding(AllBordersConfig.bandInsets(style.borderSides))
                })
            // Stage 3: the group half of the style chain wraps box AND
            // overlay — parent opacity/filter/transform/margin apply to
            // positioned descendants too (they create stacking and
            // containing contexts, css-transforms-1 §3 / css-color-4
            // §2.1), which is why the overlay attaches BETWEEN the
            // halves and not after the finished chain.
            let groupedBox = AnyView(withOverlay.applyGroupEffects(style))
            // Phase 7 step 4: apply per-child positioning after container
            // selection so absolute/relative offsets stack on top of the
            // fully-styled element. Identity when position/zindex unset.
            // Wave 5: negative-z positioned children attach OUTSIDE the
            // styled box via `.background` — SwiftUI paints an outer
            // .background behind everything applied so far, which is
            // exactly Appendix E step 3 (behind this element's own
            // background). Their PositionApplier offsets anchor at the
            // same top-leading origin the overlay uses.
            let styledBox = negativeZChildren.isEmpty
                ? groupedBox
                : AnyView(groupedBox
                    .background(alignment: .topLeading) {
                        ZStack(alignment: .topLeading) {
                            positionedChildren(style: style,
                                               children: negativeZChildren)
                        }
                    })
            // Lane IOS-COLLAPSE (CSS 2.1 §8.3.1) — the hoisted bands: a
            // first-top / last-bottom child margin that collapses THROUGH
            // this unpadded/unbordered parent becomes TRANSPARENT outer
            // spacing attached AFTER applyStyle, so the parent's
            // background/borders (painted inside applyStyle) can never
            // cover the escaped region — the browser's collapse-through
            // geometry. The plan already BAKES the §8.3.1 max() rule
            // against the parent's DECLARED own margin (containerPlan reads
            // component.properties, never the override-folded style — the
            // fix for the nested double-count), so we consume the final
            // band directly. (nil plan / 0,0 → view tree unchanged.)
            let hoistPlan = MarginCollapse.containerPlan(component: component, style: style)
            let bandTop = hoistPlan?.hoistTop ?? 0
            let bandBottom = hoistPlan?.hoistBottom ?? 0
            let positioned = PositionApplier.apply(
                bandTop > 0 || bandBottom > 0
                    ? AnyView(styledBox.padding(EdgeInsets(top: bandTop, leading: 0,
                                                           bottom: bandBottom, trailing: 0)))
                    : styledBox,
                aggregate: style.layout7
            )
            // Wave 5 — `float: right | inline-end` (LTR: both anchor to
            // the containing block's right edge, CSS 2.1 §9.5.1 rule 1).
            // The greedy frame claims the containing block's width and
            // parks the styled box at its trailing edge — the harness-
            // scale half of float semantics (no sibling wrap-around).
            // left/inline-start floats already sit at the left edge in
            // this block-flow renderer, so they need no wrap.
            let fl = style.layout7?.float
            // Wave 7 (#33) — attach the interaction listeners on the
            // finished box. Gated per condition: a component without
            // hover/active/focus buckets attaches NOTHING (identity
            // branches inside InteractionBridge), keeping the
            // pre-wave-7 view hierarchy for the whole static corpus.
            Group {
                if fl == .right || fl == .inlineEnd {
                    positioned.frame(maxWidth: .infinity, alignment: .topTrailing)
                } else {
                    positioned
                }
            }
            .modifier(InteractionBridge(wantsHover: wantsCondition("hover"),
                                        wantsActive: wantsCondition("active"),
                                        wantsFocus: wantsCondition("focus"),
                                        hovered: $isHovered,
                                        pressed: $isPressed,
                                        focused: $isFocused))
        }
    }

    // MARK: - Container selection

    // Wave 8 (lane IOS paint-order): the old `layoutContainer` wrapper
    // — which ZStacked absoluteOverlay INSIDE the style chain — is
    // gone. Absolutely-positioned children now attach in styledContent
    // as an `.overlay` BETWEEN applyBoxDecoration and applyGroupEffects
    // so they paint above the container's background/border (CSS 2.1
    // Appendix E step 8 vs steps 2–4); see the three-stage build there.

    /// The normal-flow container for this component's IN-FLOW children
    /// (block / flex / grid selection). Split out of the container
    /// build by the wave-3 absolute-positioning fix so the overlay wrap
    /// composes around any container kind.
    @ViewBuilder
    private func flowContainer(style: ComponentStyle) -> some View {
        // Phase 2: resolve gap via the new GapApplier. Row gap for vertical
        // stacks, column gap for horizontal. Zero when no gap/row/col-gap
        // is set on the IR.
        let gap = GapApplier.resolve(style.spacing.gap, context: style.spacing.context)
        // Phase 7 step 3: grid container selection. Runs ahead of the
        // flex-wrap branch so grid containers with wrap hints still
        // route to the LazyVGrid / Grid path.
        //
        // BUT: skip the grid path entirely when the component has no
        // children. SwiftUI's `LazyVGrid(columns: [.adaptive(...)])` is
        // greedy — with zero items it still expands to fill its parent's
        // available width (358 px in CaptureCanvas). Android's
        // LazyVerticalGrid + `wrapContentSize()` hugs content tight on
        // empty input, and web (post fit-content fix) does the same. The
        // visual-test fixtures Grid_Simple / Grid_ThreeCol /
        // Grid_FixedTracks have `display: grid` set but no children, so
        // they rendered ~358 px on iOS vs ~80–200 px on Android/web,
        // dragging the cross-platform SSIM down to 0.35–0.49.
        //
        // For an empty grid, "grid layout" has nothing to express anyway —
        // there are no items to place into tracks. Routing through the
        // block path (VStack(alignment: .leading)) makes iOS hug the
        // PlaceholderLabel just like the other platforms.
        // Wave 3: only IN-FLOW children count — a container whose only
        // children are absolutely positioned has zero flex/grid items
        // (css-flexbox-1 §4 / css-grid-1 §6) and lays out like an empty
        // block, exactly as the web reference does.
        let hasChildren = !inFlowChildren.isEmpty
        let gridKind: ContainerDecision.ContainerKind? = {
            guard hasChildren, let agg = style.layout7 else { return nil }
            // Explicit templates / auto-flow pick their container kind;
            // a bare `display: grid` with children (no template at all)
            // still IS a grid per css-grid-1 — a single auto column —
            // so route it through the CSS grid layout too instead of
            // falling to the legacy 2-col adaptive LazyVGrid.
            if let k = GridApplier.containerKind(for: agg) { return k }
            return agg.display == .grid ? .lazyVGrid : nil
        }()
        // Phase 7 step 2: route flex containers through FlexboxApplier's
        // FlowLayout when `flex-wrap: wrap|wrap-reverse` is set.
        if let kind = gridKind {
            // Grid path — LazyVGrid / LazyHGrid / iOS 16 Grid.
            gridContainer(kind: kind, style: style, gap: gap)
        } else if let layoutAgg = style.layout7,
           layoutAgg.display == .flex,
           layoutAgg.flexWrap == .wrap || layoutAgg.flexWrap == .wrapReverse {
            FlowLayout(
                horizontalSpacing: gap.column,
                verticalSpacing: gap.row
            ) {
                contentOrPlaceholder(style: style)
            }
        } else {
        switch style.layout.display {
        case .flexRow:
            // Fidelity wave 2 — real CSS flexbox via CSSFlexLayout. The
            // legacy HStack ignored flex-grow/shrink/basis (children
            // stayed at intrinsic size, FR_GrowBasis/FR_ShrinkBasis),
            // couldn't distribute justify-content free space, and never
            // stretched an auto-height child (FR_AlignSelf `d`). The
            // custom Layout implements css-flexbox-1 §8–§9 directly;
            // per-child parameters ride the ItemPlacement layout value
            // attached by ComponentHost (v2 placement contract).
            //
            // BUT only when there ARE children (css-flexbox-1 §4: flex
            // items are the container's in-flow children — an empty
            // container has zero items, so align-items/justify-content
            // have nothing to act on and its own box must not change).
            // The harness's PlaceholderLabel is NOT CSS content: the web
            // reference (apps/web-harness ComponentRenderer.tsx) rewrites
            // display→block for childless flex/grid containers so the
            // label lays out in normal block flow. Routing it through
            // CSSFlexLayout made `align-items: center` vertically centre
            // the label inside the declared height — the 055
            // Flex_AlignCenter regression (baseline/web keep it at the
            // top). Same guard as the empty-grid case below.
            if hasChildren {
                CSSFlexLayout(
                    axis: .horizontal,
                    reverse: style.layout7?.flexDirection == .rowReverse,
                    justify: style.layout7?.justifyContent,
                    alignItems: style.layout7?.alignItems,
                    gap: gap.column,
                    // Only an explicit CSS main/cross size lets the layout
                    // flex against the proposal; otherwise it hugs content
                    // exactly like the web harness's fit-content box.
                    definiteMain: style.size.width != nil,
                    definiteCross: style.size.height != nil
                ) {
                    contentOrPlaceholder(style: style)
                }
            } else {
                // Childless flex → block path, exactly like the empty
                // grid: the placeholder hugs top-leading and the outer
                // style chain still paints the declared box (height /
                // padding / background), matching web's display→block.
                VStack(alignment: .leading, spacing: gap.row) {
                    contentOrPlaceholder(style: style)
                }
            }
        case .flexColumn:
            // Column axis: main = vertical. Same engine as .flexRow —
            // this is what makes `justify-content: space-between`
            // finally act in column direction (FC_SpaceBetween).
            // Same childless guard as .flexRow above: zero flex items →
            // block path so the placeholder is never aligned/justified.
            if hasChildren {
                CSSFlexLayout(
                    axis: .vertical,
                    reverse: style.layout7?.flexDirection == .columnReverse,
                    justify: style.layout7?.justifyContent,
                    alignItems: style.layout7?.alignItems,
                    gap: gap.row,
                    definiteMain: style.size.height != nil,
                    definiteCross: style.size.width != nil
                ) {
                    contentOrPlaceholder(style: style)
                }
            } else {
                // Web parity (display→block rewrite for empty containers);
                // see the .flexRow comment for the full rationale.
                VStack(alignment: .leading, spacing: gap.row) {
                    contentOrPlaceholder(style: style)
                }
            }
        case .grid:
            // Grid subset: render as an adaptive LazyVGrid with 2 columns
            // when there ARE children. Empty grids fall through to the
            // block-style VStack path: `LazyVGrid(columns: [.adaptive(...)])`
            // is greedy and expands to fill the parent's available width
            // even with zero items, which mismatches Android (wrap content)
            // and web (fit-content) for the empty Grid_Simple /
            // Grid_ThreeCol / Grid_FixedTracks fixtures. See the gridKind
            // computation above for the matching guard.
            if hasChildren {
                LazyVGrid(
                    columns: [GridItem(.adaptive(minimum: 80), spacing: gap.column)],
                    spacing: gap.row
                ) {
                    contentOrPlaceholder(style: style)
                }
            } else {
                VStack(alignment: .leading, spacing: gap.row) {
                    contentOrPlaceholder(style: style)
                }
            }
        case .inline:
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                contentOrPlaceholder(style: style)
            }
        case .none:
            EmptyView()
        case .block:
            VStack(
                alignment: .leading,
                spacing: gap.row
            ) {
                contentOrPlaceholder(style: style)
            }
        }
        }
    }

    // MARK: - Absolute overlay (fidelity wave 3)

    /// Render the absolutely-positioned children on top of the in-flow
    /// content. Each child renders through ComponentRenderer as usual —
    /// its own body applies PositionApplier, whose flexible
    /// top-leading frame + offset resolves top/left against this
    /// ZStack's corner. Declaration order breaks paint ties, matching
    /// CSS tree order within paint step 8 (CSS 2.1 Appendix E).
    ///
    /// Anchoring (wave 8): the caller attaches this as an `.overlay` on
    /// the fully-painted border box, inset by the border band, so
    /// offsets anchor at the PADDING-box corner — exactly the
    /// containing block css-position-3 §3.1 assigns to absolutely
    /// positioned boxes (the ancestor's padding stays INSIDE the
    /// containing block, so `top: 0` overlaps it — matching web).
    /// This replaces the pre-wave-8 content-box approximation, which
    /// also let the container's border stroke paint OVER the children.
    @ViewBuilder
    private func absoluteOverlay(style: ComponentStyle) -> some View {
        // Overlay half only — negative-z children paint behind the
        // styled box via the `body` background split (wave 5).
        positionedChildren(style: style, children: overlayChildren)
    }

    /// Shared renderer for positioned children — used by BOTH halves of
    /// the wave-5 z-split (overlay ≥ 0, background < 0) so environment
    /// resets stay identical.
    @ViewBuilder
    private func positionedChildren(style: ComponentStyle,
                                    children: [IRComponent]) -> some View {
        // Inheritance flows into positioned children exactly like flow
        // children (css-cascade-4 — inheritance is by tree, not flow).
        // Wave 6: publish the RESOLVED declarations so children inherit
        // computed values (a var()-valued font-size flows down as px).
        let childInherited = InheritedText.inheritable(from: resolvedProperties)
        // Wave 9 — percent sizes of absolute children resolve against
        // the positioned ancestor's PADDING box (css-position-3 §3.1),
        // matching the wave-8 overlay anchor which already insets by the
        // border band only. The pre-wave-9 content-box approximation
        // subtracted the padding band too, under-resolving `width: 100%`
        // on children of padded ancestors (basis and anchor disagreed).
        let childCB = ContainingBlockBasis.paddingBox(style: style, vertical: false)
        // Wave 9 — the HEIGHT basis (same padding-box rule): definite
        // ancestor height − painted borders, or nil when indefinite so
        // the child's percent height keeps degrading to auto. This is
        // the fix for the gate capture's zero-area `height: 100%` child.
        let childCBH = ContainingBlockBasis.paddingBox(style: style, vertical: true)
        // Lane FLEX-SAFE — when THIS positioned ancestor is a flex
        // container, an inset-less abspos child sits at its STATIC
        // POSITION: the sole-flex-item hypothetical (css-flexbox-1
        // §4.1), so align-self (incl. the safe/unsafe overflow keywords
        // riding the Generic wire) shifts the child off the overlay's
        // top-leading anchor on the CROSS axis. Nil for every non-flex
        // ancestor — their children keep the block-flow anchor.
        let parentFlexDirection: FlexDirectionKeyword? =
            style.layout7?.display == .flex
                ? (style.layout7?.flexDirection ?? .row)  // CSS initial: row
                : nil
        ForEach(Array(children.enumerated()), id: \.offset) { _, child in
            // Static-position cross shift (0 unless flex parent + an
            // align-self claim + definite extents — the helper's docs
            // spell out each honest no-op). Computed per child: the
            // safe fallback depends on the CHILD's own size.
            let staticShift = AbsposStaticAlignment.staticCrossOffset(
                flexDirection: parentFlexDirection,
                childProperties: child.properties,
                containerW: childCB,
                containerH: childCBH)
            // Wave 11 (lane IOS grid-abspos, css-grid-1 §9.2) — when
            // THIS positioned ancestor is a GRID container, an
            // inset-less abspos child sits at its static position "as
            // if it were the sole grid item in a grid area whose edges
            // coincide with the CONTENT edges of the grid container":
            // the shift moves the child from the overlay's padding-box
            // anchor to the content-box origin and applies the
            // sole-item align-self/justify-self (defaulting to the
            // container's align-items/justify-items) alignment there.
            // Zero for every non-grid ancestor, and per axis whenever
            // an explicit inset owns that axis (css-position-3 §3.5) —
            // mutually exclusive with the flex shift above (display is
            // never flex AND grid), so the sum below never composes.
            let gridShift = AbsposGridStaticPosition.staticOffset(
                parentStyle: style,
                childProperties: child.properties)
            // v2: children render through ComponentHost (placement
            // parent-data attached; inert here — the overlay ZStack
            // reads no layout values).
            ComponentHost(component: child)
                // The static-position offset — applied on the HOST so
                // the child's own PositionApplier (which only runs for
                // explicit insets, gated off above) never composes
                // with it on the same axis. Flex and grid shifts are
                // mutually exclusive (see above), so adding them keeps
                // exactly one lane's geometry.
                .offset(x: staticShift.width + gridShift.width,
                        y: staticShift.height + gridShift.height)
                // Reset the size-injection channels: an absolute child
                // never stretch-inherits grid/flex geometry (it is not
                // an item of the parent's formatting context).
                .environment(\.gridStretchHeight, nil)
                .environment(\.flexStretchWidth, nil)
                // Round 4: block-fill is root-scoped — a child never inherits
                // the stacked root's fill width (it has its own box).
                .environment(\.wptBlockFlowFillWidth, nil)
                // Margin collapse (lane IOS-COLLAPSE): out-of-flow boxes
                // never collapse (css-position-3 §2.1 / CSS 2.1 §8.3.1
                // "in-flow" precondition) — reset so a grandparent's
                // plan can't reach a positioned child's margins.
                .environment(\.marginCollapseOverride, nil)
                .environment(\.containingBlockWidth, childCB)
                // Wave 9 — the height basis, same always-write reset
                // discipline as the width channel (nil for indefinite
                // ancestors, so a grandparent's basis never leaks).
                .environment(\.containingBlockHeight, childCBH)
                .environment(\.inheritedTextProperties, childInherited)
                // Custom-property scope (wave 6): positioned children
                // sit in the same slot-parent chain as flow children —
                // the merged store flows down uncut (css-variables-1 §2).
                .environment(\.cssVariables, mergedVariables)
        }
    }

    // MARK: - Grid container (Phase 7 step 3)

    /// Render a grid container — routes to LazyVGrid / LazyHGrid for plain
    /// track-list grids or to iOS 16's Grid/GridRow for template-areas
    /// grids. Spanning within LazyVGrid isn't supported by SwiftUI, so
    /// template-areas components go through the Grid path.
    @ViewBuilder
    private func gridContainer(
        kind: ContainerDecision.ContainerKind,
        style: ComponentStyle,
        gap: (row: CGFloat, column: CGFloat)
    ) -> some View {
        let agg = style.layout7  // safe — gridKind only fires when non-nil
        switch kind {
        case .lazyVGrid:
            // Fidelity wave 1: real CSS-grid subset via CSSGridLayout.
            // LazyVGrid centred items, ignored placement/spans, ignored
            // grid-template-rows, and expanded greedily to the canvas
            // width — all four flagged in the grid-2col/nested-3level
            // wave. v2: per-item placement arrives WITH the subviews via
            // ItemPlacementKey (attached by ComponentHost); this call
            // passes ONLY the container's own policy (tracks, gaps,
            // *-items defaults) — zero child knowledge (design §2.1).
            CSSGridLayout(
                // Column tracks from the template; a template-less grid
                // is a single auto column per css-grid-1 §7.1.
                tracks: agg?.gridTemplateColumns?.tracks.map(\.kind) ?? [.automatic],
                // Row template feeds fixed row heights (60px tracks in
                // grid-2col/010_G2_AlignSelf).
                rowTemplate: agg?.gridTemplateRows?.tracks.map(\.kind),
                // Implicit rows take the first grid-auto-rows size.
                autoRows: agg?.gridAutoRows?.tracks.first?.kind,
                // Container-side alignment defaults — each arriving
                // item's justify/align-self resolves against these
                // inside the Layout (css-align-3 §6).
                justifyItems: agg?.justifyItems,
                alignItems: agg?.alignItems,
                rowGap: gap.row,
                columnGap: gap.column,
                // Explicit CSS width ⇒ fr/% tracks split the proposal;
                // otherwise fit-content hug (web harness parity).
                definiteWidth: style.size.width != nil
            ) {
                contentOrPlaceholder(style: style)
            }
        case .lazyHGrid:
            // Column auto-flow — rows drive the LazyHGrid layout.
            let items = GridApplier.gridItems(
                for: agg?.gridTemplateRows,
                columnGap: gap.row
            )
            LazyHGrid(rows: items, spacing: gap.column) {
                contentOrPlaceholder(style: style)
            }
        case .grid:
            // Template-areas grids (wave 5) — SAME CSSGridLayout engine
            // as plain track-list grids. The old iOS 16 Grid/GridRow
            // path matched children to cells BY NAME (child "box1" only
            // rendered if an area was literally called "box1"): children
            // whose name matched no area were DROPPED, and a child whose
            // area spanned N cells rendered N duplicate copies. Instead,
            // each child's own grid-area claim (a NAMED line riding
            // ItemPlacement) is resolved against this template inside
            // the Layout — browser-verified semantics in
            // GridPlacer.resolveNames.
            CSSGridLayout(
                // Column tracks: explicit template wins; otherwise the
                // area template's width defines that many auto columns
                // (css-grid-1 §7.3: each column in the areas grammar
                // creates an explicit auto track).
                tracks: agg?.gridTemplateColumns?.tracks.map(\.kind)
                    ?? Array(repeating: GridTrack.Kind.automatic,
                             count: max(1, agg?.gridTemplateAreas?.map(\.count).max() ?? 1)),
                // Row template / implicit-row sizing identical to the
                // plain-grid path above.
                rowTemplate: agg?.gridTemplateRows?.tracks.map(\.kind),
                autoRows: agg?.gridAutoRows?.tracks.first?.kind,
                justifyItems: agg?.justifyItems,
                alignItems: agg?.alignItems,
                rowGap: gap.row,
                columnGap: gap.column,
                definiteWidth: style.size.width != nil,
                // The named-area map — consumed only by the claims
                // resolver (css-grid-1 §8.3).
                templateAreas: agg?.gridTemplateAreas
            ) {
                contentOrPlaceholder(style: style)
            }
        default:
            // Fallback — vertical stack. Keeps the switch exhaustive.
            VStack(alignment: .leading, spacing: gap.row) {
                contentOrPlaceholder(style: style)
            }
        }
    }

    // MARK: - Grid stretch pre-pass (fidelity wave 1, v2-refactored)

    /// Static stretch-height map for a `display: grid` container, keyed
    /// by SORTED-CHILD index — the paint-chain half of grid stretch
    /// (css-align-3 §9): the row height is injected into the child's
    /// environment so its OWN background/border paint at the stretched
    /// height (an outer .frame can't reach the child's paint chain).
    ///
    /// v2 note: this is a RENDERER pre-pass over this component's own
    /// children, not container-side child inspection — the placement
    /// requests it simulates come from the exact same
    /// ItemPlacementExtractor claims ComponentHost attaches for
    /// CSSGridLayout, so the simulated assignment provably matches what
    /// the Layout resolves at measure time. Returns nil for non-grid
    /// parents so contentOrPlaceholder skips the env injection entirely.
    private func gridStretchHeights(style: ComponentStyle) -> [Int: CGFloat]? {
        // Only grid containers with children get a plan. Mirrors the
        // gridKind decision in flowContainer: an explicit template
        // routes to the grid path even without `display: grid` (legacy
        // behaviour of GridApplier.containerKind).
        // Wave 3: plan over IN-FLOW children only — absolute children
        // are not grid items (css-grid-1 §6) and render via the overlay.
        let rawChildren = inFlowChildren
        // Wave 5: template-areas grids (.grid kind) now flow through the
        // same CSSGridLayout, so they take the same stretch plan.
        let kind = style.layout7.flatMap { GridApplier.containerKind(for: $0) }
        guard let parentAgg = style.layout7,
              parentAgg.display == .grid
                || kind == .lazyVGrid || kind == .grid,
              !rawChildren.isEmpty else { return nil }
        // Same ordering contentOrPlaceholder renders with (CSS `order`).
        let children = FlexboxApplier.sorted(rawChildren)
        // A parent that carries text AND children renders the text as a
        // leading placeholder — CSS wraps loose grid text in an
        // anonymous grid ITEM, so it participates in placement (as a
        // nil-placement subview: auto-placed, start-aligned).
        let hasLeadingText = (component.text?.isEmpty == false)
        var requests: [GridItemRequest] = []
        // Track per-child "block size may stretch" flags for the env map.
        var stretchFlags: [Bool] = []
        if hasLeadingText {
            // The anonymous text item auto-places at the first free cell.
            requests.append(GridItemRequest())
        }
        // Same template facts CSSGridLayout.claims() resolves against —
        // keeping this simulation bit-identical to the Layout (wave 5:
        // named lines + negative indices resolve before assignment).
        let areas = parentAgg.gridTemplateAreas
        let explicitRows = max(parentAgg.gridTemplateRows?.tracks.count ?? 0,
                               areas?.count ?? 0)
        let cols = parentAgg.gridTemplateColumns?.tracks.count
            ?? max(1, areas?.map(\.count).max() ?? 1)
        for child in children {
            // The child's v2 placement claims — SAME extraction the
            // ComponentHost attaches, so this simulation and the
            // CSSGridLayout resolution can never drift.
            let claim = ItemPlacementExtractor.extract(from: child.properties)
            requests.append(GridPlacer.resolveNames(claim.grid.request,
                                                    areas: areas,
                                                    columnCount: cols,
                                                    explicitRowCount: explicitRows))
            // Stretch candidate: effective block-axis keyword is
            // stretch/normal/auto (the grid default) AND the child has
            // no explicit block size in the IR (css-align-3 §9 auto-size
            // precondition — the explicitHeight fact on the claim).
            let effAlign: AlignmentKeyword? = {
                if let s = claim.grid.alignSelf, s != .auto { return s }
                return parentAgg.alignItems
            }()
            let stretchy = effAlign == nil || effAlign == .stretch || effAlign == .normal
            stretchFlags.append(stretchy && !claim.explicitHeight)
        }
        // Resolve stretch heights: every row the item covers must be a
        // statically-knowable FIXED track — a `grid-template-rows` px
        // entry inside the template, or the `grid-auto-rows` px size for
        // implicit rows (wave 5: the old plan handled only span-1 items
        // inside the template, so LineNumbers items hugged 30px in 40px
        // auto-rows and LineSpans `d` never covered its 2-row span).
        var stretchHeights: [Int: CGFloat] = [:]
        let rowTracks = parentAgg.gridTemplateRows?.tracks
        let autoRow = parentAgg.gridAutoRows?.tracks.first?.kind
        // Row gap joins the summed tracks for multi-row spans — same
        // resolver lane the container's own layout call uses.
        let rowGapPx = GapApplier.resolve(style.spacing.gap,
                                          context: style.spacing.context).row
        let (cells, _) = GridPlacer.assign(requests, columnCount: cols)
        let offset = hasLeadingText ? 1 : 0
        for i in 0..<children.count where stretchFlags[i] {
            let cell = cells[i + offset]
            // Sum the fixed heights of every covered row; bail on the
            // first non-fixed one (content-sized rows stretch to content
            // — an identity the paint chain already renders).
            var total: CGFloat = 0
            var allFixed = true
            for r in cell.row..<(cell.row + cell.rowSpan) {
                if let tpl = rowTracks, r < tpl.count {
                    // Explicit template row — only a px literal is static.
                    if case .fixed(let px) = tpl[r].kind { total += px }
                    else { allFixed = false; break }
                } else if case .fixed(let px)? = autoRow {
                    // Implicit row — grid-auto-rows px size (§7.6).
                    total += px
                } else {
                    allFixed = false; break
                }
            }
            // Spanning items cover the (span−1) gaps too (css-align-3
            // gutters are part of the spanned area, browser-verified:
            // rows 3→5 of 36px tracks + 6px gap stretch to 78px).
            if allFixed {
                stretchHeights[i] = total + rowGapPx * CGFloat(cell.rowSpan - 1)
            }
        }
        return stretchHeights
    }

    // MARK: - Flex stretch geometry (fidelity wave 2)

    /// Definite CONTENT size of a flex container on one axis: declared
    /// border-box size minus the padding band and painted border widths
    /// (CSS 2.1 §8.1 box model; the harness renders border-box). Nil when
    /// the size is not declared/resolvable — indefinite containers
    /// can't statically size children (depends on measurement; deferred).
    private func flexContentSize(style: ComponentStyle, vertical: Bool) -> CGFloat? {
        // Wave 9 — the arithmetic moved verbatim to ContainingBlockBasis
        // (pure, XCTest-pinnable): declared border-box size − padding
        // band − painted borders. The height axis additionally gained
        // the wave-9 definite-basis rule there (an ancestor-published
        // containing-block height lets a percent height resolve, CSS
        // 2.1 §10.5, instead of the old unconditional skip).
        ContainingBlockBasis.contentBox(style: style, vertical: vertical)
    }

    /// Static main-axis flex plan (fidelity wave 2). CSSFlexLayout places
    /// children at their §9.7-resolved main sizes, but a flexed child's
    /// own paint chain (background/border) still hugs its intrinsic size
    /// — the resolved size has to be folded into the child's SizeConfig
    /// the same way grid/flex stretch is (env injection). This pre-pass
    /// re-runs the pure CSSFlexMath with statically-knowable inputs and
    /// returns one main size per sorted child, or nil when any input is
    /// dynamic (auto basis with no explicit main size, indefinite
    /// container, anonymous leading text) — those fall back to the
    /// Layout-only path where positions are right but paint hugs.
    private func flexMainPlan(style: ComponentStyle,
                              children: [IRComponent],
                              column: Bool) -> [CGFloat]? {
        // Definite main-axis content size or bail.
        guard let available = flexContentSize(style: style, vertical: column)
        else { return nil }
        // A leading text placeholder participates in the Layout as an
        // extra item — index mapping would shift; no wave fixture mixes
        // text with flexed children, so bail honestly.
        guard component.text?.isEmpty != false else { return nil }
        let ctx = style.spacing.context
        // Main-axis gap through the same resolver as the container.
        let g = GapApplier.resolve(style.spacing.gap, context: ctx)
        let gap = column ? g.row : g.column
        var items: [CSSFlexMath.ItemInput] = []
        for child in children {
            // Flex factors from the child's aggregate.
            var a = LayoutAggregate()
            FlexboxExtractor.extract(from: child.properties, into: &a)
            let basisPx: CGFloat? = {
                if case .px(let p)? = a.flexBasis { return p }
                return nil
            }()
            // Explicit main size fallback (css-flexbox-1 §9.2.3.A —
            // basis auto defers to the main-size property).
            let cs = SizeExtractor.extract(from: child.properties)
            let explicit: CGFloat? = column
                ? SizeApplierResolve.exact(cs.height, ctx: ctx,
                                           parent: CGFloat(ctx.viewportHeight),
                                           allowPercent: false)
                // Wave 3: child percent widths resolve against THIS
                // container's containing block for the static plan —
                // close enough for the plan's purposes (the child's own
                // paint pass re-resolves against the container's
                // content box via the environment channel).
                : SizeApplierResolve.exact(cs.width, ctx: ctx,
                                           parent: ctx.containingBlockWidth)
            // Content-derived basis (text measurement) is not statically
            // knowable — bail to the dynamic path.
            guard let basis = basisPx ?? explicit else { return nil }
            // The web harness's 50×30 min floor applies per axis when
            // the child declares nothing there (StyleBuilder.minFloor)
            // — it clamps flexed sizes in the browser too (a browser
            // min-width beats flex shrink/grow, §9.7 min violation).
            let floor = StyleBuilder.minFloor(for: cs)
            let minMain = (column ? floor.height : floor.width) ?? 0
            items.append(.init(basis: basis, min: minMain,
                               grow: CGFloat(a.flexGrow ?? 0),
                               shrink: CGFloat(a.flexShrink ?? 1)))
        }
        return CSSFlexMath.mainSizes(items: items, available: available, gap: gap)
    }

    // MARK: - Text wrap width (lane IOS-TEXT fix 1)

    /// The CONTENT-BOX inline size a real-text run wraps at — the input
    /// to the greedy pre-break in PlaceholderLabel. An explicit IR width
    /// resolves through the SAME subtraction lane the flex plan uses
    /// (flexContentSize: declared border-box width − padding band −
    /// painted borders); a fit-content box wraps at the containing-block
    /// cap (css-sizing-3 §5.1 — the widest the box can lay out before
    /// text must wrap), minus this element's own padding + borders. Nil
    /// only when the arithmetic degenerates (≤ 0).
    private func textWrapWidth(style: ComponentStyle) -> CGFloat? {
        // Declared width → definite content box, same lane as flex.
        if let w = flexContentSize(style: style, vertical: false) { return w }
        // Fit-content: the proposal cap is the containing block…
        var w = style.spacing.context.containingBlockWidth
        // …minus the element's own padding band (same resolver lane as
        // the paint chain, StyleBuilder.horizontalPaddingPx)…
        w -= StyleBuilder.horizontalPaddingPx(style)
        // …and its painted border widths (border-style:none = used 0,
        // CSS 2.1 §8.5.3 — the same hasBorder gate flexContentSize uses).
        if let b = style.borderSides {
            w -= b.start.hasBorder ? (b.start.effectiveWidth ?? 0) : 0
            w -= b.end.hasBorder   ? (b.end.effectiveWidth ?? 0)   : 0
        }
        return w > 0 ? w : nil
    }

    // MARK: - Content

    @ViewBuilder
    private func contentOrPlaceholder(style: ComponentStyle) -> some View {
        // The placeholder only appears when the component has NO
        // children at all — a parent whose children are ALL absolutely
        // positioned still renders empty in-flow content (web parity:
        // ComponentRenderer.tsx keys the placeholder on
        // `children.length`), while its boxes arrive via the overlay.
        if component.children?.isEmpty == false {
            // Bug 1 mixed-content fix — see
            // testing/titan/investigations/swarm-002/css-text-decor__text-decoration-decorating-box-thickness-001.json
            //
            // When the parent carries text AND children, render the text
            // as a leading sibling so the parent's text-decoration / color
            // / font has a glyph stream to attach to. SwiftUI's Text
            // inherits no styling so we route through PlaceholderLabel to
            // pick up the parent's TextConfig (font, color, line-height,
            // text-indent) exactly the way the leaf-text branch below does.
            // Without this fix, components shaped like
            // <div>abc <span>x</span> def</div> only rendered the child's
            // "x" — the parent's underline had nothing to draw under.
            // Known limitation: leading-only; full inline-flow ordering
            // would need an interleaved inlineRuns IR shape.
            // (v2 rename: the wire field is `text`, formerly `_text`.)
            if let t = component.text, !t.isEmpty {
                PlaceholderLabel(
                    name: t,
                    // Wave-5 gate follow-up — same currentColor
                    // bottom-out as the leaf label below: web resolves
                    // the leading-text run's currentColor to the
                    // harness body's #eee when no ancestor declares
                    // color (stage contract), so the honest fallback is
                    // the opaque default text color, not the pick.
                    // corpus-v4.1 ink sub-boundary: in WPT capture the
                    // inherit chain ends at the ref injection's
                    // `:where(body) { color:#000 }` instead, so the
                    // bottom-out routes through captureTextInk — spec
                    // BLACK in WPT mode, the near-white stage default
                    // verbatim everywhere else (327 baselines).
                    color: style.text.color
                        ?? (currentColorBottomsOut
                                ? WPTCanvas.captureTextInk(
                                    wptCaptureMode: wptCaptureMode,
                                    defaultInk: InheritedText.defaultTextColor)
                                : nil),
                    textConfig: style.text,
                    backgroundColor: style.backgroundColor,
                    clipTextGradient: nil,
                    // text-align only has room to act when the box is
                    // wider than the glyph run — i.e. when the IR set an
                    // explicit width (see fillWidth doc on the label).
                    fillWidth: style.size.width != nil,
                    // Thread the parent renderer's resolved WPT flag so the
                    // composed-mode line-box pin + padding-0 fire (Round 4).
                    wptCaptureMode: wptCaptureMode,
                    // Lane IOS-TEXT fix 1 — the content-box wrap width for
                    // the greedy pre-break (leading text wraps exactly like
                    // leaf text: same box, same containing block).
                    wrapWidth: textWrapWidth(style: style)
                )
            }
            // Phase 7 step 2: sort children by CSS `order` BEFORE rendering.
            // SwiftUI has no runtime analogue, so the reordering happens
            // at build time. When no child carries Order, this is a no-op.
            // Wave 3: in-flow children only — absolute/fixed boxes render
            // via the styledContent overlay (see absoluteOverlay).
            let children = FlexboxApplier.sorted(inFlowChildren)
            // Parent aggregate for flex-child decoration. Nil fallback
            // keeps us on the legacy-layout path when Phase 7 has not
            // touched this component.
            let parentAgg = style.layout7
            // Grid stretch map (fidelity wave 1): nil for non-grid
            // parents. Every child gets the env value SET explicitly —
            // including nil — so a grandparent's injection can never
            // leak past its own children.
            let stretchHeights = gridStretchHeights(style: style)
            // Inheritable text declarations for the children — computed
            // from the MERGED, variable-RESOLVED list so grandparents'
            // values ride through parents that don't redeclare them
            // (transitive cascade) and children inherit COMPUTED values
            // (css-cascade-4 §7.3 — a var()-valued font-size flows down
            // as the resolved pixels, keeping chained em bases honest).
            let childInherited = InheritedText.inheritable(from: resolvedProperties)
            // Fidelity wave 2 — flex parents route through CSSFlexLayout
            // (single-line) which consumes per-child ItemPlacement.flex
            // claims. FlowLayout (wrap) keeps the legacy FlexChildModifier.
            let isWrapFlex = parentAgg?.flexWrap == .wrap
                || parentAgg?.flexWrap == .wrapReverse
            let isCSSFlex = parentAgg?.display == .flex && !isWrapFlex
            // Column flex? Decides which axis counts as "cross" for the
            // stretch checks below.
            let isColumn = parentAgg?.flexDirection == .column
                || parentAgg?.flexDirection == .columnReverse
            // Definite cross-axis CONTENT size of this flex container —
            // needed to stretch auto-cross-size children with painted
            // backgrounds (css-flexbox-1 §8.3 / css-align-3 §9). Like the
            // wave-1 grid fix, an outer .frame can't reach the child's
            // paint chain, so the value is injected via the environment
            // and folded into the child's own SizeConfig.
            let flexLineCross: CGFloat? = isCSSFlex
                ? flexContentSize(style: style, vertical: !isColumn)
                : nil
            // Static §9.7 main sizes for paint-chain injection (nil when
            // any input is dynamic — Layout still positions correctly).
            let flexMainSizes: [CGFloat]? = isCSSFlex
                ? flexMainPlan(style: style, children: children, column: isColumn)
                : nil
            // Fidelity wave 3 — containing-block publication
            // (css-sizing-3 §5.1): children resolve percent widths
            // against THIS box's content width. Definite only when our
            // own width is statically known (flexContentSize subtracts
            // the padding band + painted borders); nil resets the
            // channel for fit-content parents so a grandparent's basis
            // never leaks past its own children.
            let childCB: CGFloat? = flexContentSize(style: style, vertical: false)
            // Wave 9 — the HEIGHT twin (CSS 2.1 §10.5): in-flow children
            // resolve percent heights against this box's CONTENT-box
            // height when it is statically definite; nil (indefinite —
            // auto-height parents, the root ScrollView) keeps the
            // percent-height skip. Same publication + reset discipline
            // as the width channel above.
            let childCBH: CGFloat? = flexContentSize(style: style, vertical: true)
            // Lane IOS-COLLAPSE (CSS 2.1 §8.3.1) — the block container's
            // margin-collapse plan: one used-(top,bottom) override per
            // child of THIS ForEach (containerPlan derives them from the
            // identical sorted in-flow array, so indices line up by
            // construction). Nil for non-block/ineligible containers —
            // children then keep their declared margins untouched.
            let collapsePlan = MarginCollapse.containerPlan(component: component,
                                                            style: style)
            // Wave-9 regression fix — the USED column-gap feeding the
            // multicol fill basis (css-multicol-1 §3 via MulticolMath in
            // wptChildFillWidth). Declared ColumnGap/Gap resolves through
            // the SAME GapApplier the flow container's spacing uses (one
            // resolver, two consumers); an UNDECLARED gap is `normal`,
            // which for multicol containers is 1em (css-align-3 §8.3) —
            // the element's resolved font-size, NOT the GapConfig zero
            // default (that zero is right for flex/grid, where `normal`
            // means no gap). Non-multicol parents never read the value —
            // 0 short-circuits without touching the resolver.
            let multicolFillGap: CGFloat = {
                // Only multicol containers consume a column-gap basis.
                guard style.columns?.isMulticolContainer == true else { return 0 }
                // Declared gap (Gap shorthand expands to ColumnGap on the
                // converter, but check both — GapExtractor reads both).
                if resolvedProperties.contains(where: {
                    $0.type == "ColumnGap" || $0.type == "Gap" }) {
                    // Percent column-gap resolves against the inline axis
                    // — the container's content-box width (childCB).
                    return GapApplier.resolve(style.spacing.gap,
                                              context: style.spacing.context,
                                              parentWidth: childCB).column
                }
                // `column-gap: normal` = 1em for multicol (css-align-3 §8.3).
                return CGFloat(style.spacing.context.fontSizePx)
            }()
            ForEach(Array(children.enumerated()), id: \.offset) { index, child in
                // Build the child's aggregate once so FlexChildModifier
                // (legacy wrap path) and the stretch env computation can
                // read align-self / flex-basis / flex-grow without
                // re-parsing. This is a duplicated pass over the child's
                // property list, but it's cheap (string-compare loop).
                // (The CSSFlexLayout inputs themselves now ride the v2
                // ItemPlacement layout value attached by ComponentHost —
                // extracted from the same properties, so identical.)
                let childAgg: LayoutAggregate? = {
                    guard parentAgg?.display == .flex else { return nil }
                    var a = LayoutAggregate()
                    FlexboxExtractor.extract(from: child.properties, into: &a)
                    return a.touched ? a : nil
                }()
                // Fidelity wave 2 — crossAuto mirrors css-flexbox-1
                // §8.3's stretch precondition: no explicit cross-axis
                // size in the IR (used by the stretch env injection).
                let crossAuto = !child.properties.contains {
                    isColumn ? ($0.type == "Width" || $0.type == "InlineSize")
                             : ($0.type == "Height" || $0.type == "BlockSize")
                }
                // Stretch injection value: only when this child's resolved
                // alignment is stretch AND its cross size is auto AND the
                // container's cross content size is definite.
                let flexStretch: CGFloat? = {
                    guard isCSSFlex, crossAuto,
                          CSSFlexMath.resolvedAlign(self: childAgg?.alignSelf,
                                                    items: parentAgg?.alignItems) == .stretch
                    else { return nil }
                    return flexLineCross
                }()
                // Bug 2 list-marker — when the parent's source tag is an
                // ordered/unordered list and this child is an <li>,
                // prepend a numeric/bullet marker. SwiftUI has no
                // ::marker pseudo, so we emit it inline via an HStack
                // with a leading Text. Markers follow the simple-numeric
                // algorithm: 1-based index + ". " for <ol>, "• " for
                // <ul>. Honors only the common cases; full CSS Counter
                // Styles L3 (arabic-indic, lower-roman, etc.) requires
                // the ListStyleType property on the <li> which existing
                // iOS appliers already extract — we leave that to a
                // follow-up. (v2 rename: the hint now lives at
                // meta.sourceTag, formerly `_tag`.)
                let parentTag = (component.meta?.sourceTag ?? "").lowercased()
                let isListItem = (child.meta?.sourceTag ?? "").lowercased() == "li"
                // Wave 10 — the fragmentation contract (css-break-3 §4):
                // a multicol container's single in-flow child whose
                // block-size C exceeds the column block-size H breaks
                // into per-column fragments. The DECISION + geometry are
                // pure (ColumnsApplier.fragmentPlan: the §2 multicol
                // gate, the horizontal-tb bail, the §3 used columns via
                // the SAME childCB/childCBH/multicolFillGap inputs the
                // wave-9 fill basis reads, and the C > H overflow test).
                // Nil = every current non-overflowing multicol fixture
                // keeps the pre-wave-10 path byte-identically.
                let fragPlan = ColumnsApplier.fragmentPlan(
                    columns: style.columns,
                    // Wave 12 — read the writing mode from the MERGED,
                    // inheritance-resolved list, not the typography
                    // aggregate: `writing-mode` is Inherited: yes
                    // (css-writing-modes-4 §3.1) so it usually sits on
                    // an ANCESTOR (now flowing in via InheritedText's
                    // "WritingMode" entry), and the aggregate is nil
                    // whenever writing-mode is the only typography
                    // signal (WritingModeApplier deliberately never
                    // flips `touched` — see its file header) — both
                    // paths silently defeated the vertical-mode bail.
                    verticalWritingMode:
                        WritingModeExtractor.extract(
                            from: resolvedProperties)?.isVertical == true,
                    siblingCount: children.count,
                    contentWidthPx: childCB,
                    contentHeightPx: childCBH,
                    gapPx: multicolFillGap,
                    childProperties: child.properties,
                    ctx: style.spacing.context)
                Group {
                    if let plan = fragPlan {
                        // Fragment pass — F clipped+translated clones of
                        // the child, one per column (multicolFragmentRow
                        // below documents the modifier-order argument).
                        multicolFragmentRow(child: child, plan: plan)
                    } else if isListItem && (parentTag == "ol" || parentTag == "ul") {
                        HStack(alignment: .firstTextBaseline, spacing: 4) {
                            Text(parentTag == "ol" ? "\(index + 1)." : "•")
                            if !isCSSFlex, let ca = childAgg, let pa = parentAgg {
                                // Marker rows keep the legacy decoration;
                                // the host's placement inside the HStack
                                // is invisible to the outer Layout (layout
                                // values don't cross container boundaries)
                                // — same as the pre-v2 no-spec behaviour.
                                ComponentHost(component: child)
                                    .modifier(FlexboxApplier.childModifier(for: ca, parent: pa))
                            } else {
                                ComponentHost(component: child)
                            }
                        }
                    } else if isCSSFlex {
                        // CSSFlexLayout parent — the flex claims ride the
                        // ItemPlacement layout value ComponentHost
                        // attaches; the legacy frame-based child modifier
                        // would fight the Layout's placement.
                        ComponentHost(component: child)
                    } else if let ca = childAgg, let pa = parentAgg {
                        // FlowLayout (wrap) keeps the legacy decoration.
                        ComponentHost(component: child)
                            .modifier(FlexboxApplier.childModifier(for: ca, parent: pa))
                    } else {
                        // Block/grid parents — CSSGridLayout reads the
                        // host-attached grid claims; block parents leave
                        // the placement inert.
                        ComponentHost(component: child)
                    }
                }
                // Grid + flex size injection (css-align-3 §9 stretch and
                // css-flexbox-1 §9.7 flexed main sizes) — the values are
                // ALWAYS written (nil when nothing to inject) so the
                // environment resets at every tree level. Height channel:
                // grid row stretch, row-flex cross stretch, column-flex
                // MAIN size. Width channel: column-flex cross stretch,
                // row-flex MAIN size. The fold in `body` only fires when
                // the child declared no explicit size on that axis.
                .environment(\.gridStretchHeight,
                             stretchHeights?[index]
                                ?? (isColumn ? flexMainSizes?[index] : flexStretch))
                .environment(\.flexStretchWidth,
                             isColumn ? flexStretch : flexMainSizes?[index])
                // Wave 9 (extending Round 4): the block-fill channel is
                // still ALWAYS written (reset discipline), but a BLOCK
                // container in WPT capture now re-publishes its own
                // content-box width for its in-flow children instead of
                // hard nil — CSS 2.1 §10.3.3's width:auto fill applies
                // at EVERY block-flow level, not just the composed
                // roots (the css-break url-background boxes are child
                // blocks). Flex/grid/inline parents and marker rows
                // still publish nil (see wptChildFillWidth's guards),
                // and the flag-off product path is nil by construction.
                .environment(\.wptBlockFlowFillWidth,
                             Self.wptChildFillWidth(
                                parentDisplay: style.layout.display,
                                wptCaptureMode: wptCaptureMode,
                                isMarkerRow: isListItem && (parentTag == "ol"
                                                            || parentTag == "ul"),
                                parentContentWidth: childCB,
                                // Wave-9 regression fix: multicol parents
                                // publish the §3 USED column width as the
                                // fill basis (css-multicol-1 §2 — the
                                // children's containing block is the
                                // column box), never the container width.
                                parentColumns: style.columns,
                                columnGapPx: multicolFillGap))
                // Containing block (wave 3): always written — definite
                // content width or nil — so the channel resets at every
                // tree level (no grandparent leak).
                .environment(\.containingBlockWidth, childCB)
                // Wave 9 — the height basis: always written (definite
                // content height or nil), same reset discipline.
                .environment(\.containingBlockHeight, childCBH)
                // Margin collapse (lane IOS-COLLAPSE, §8.3.1): the used
                // vertical margins for THIS child, or nil when no plan
                // applies. ALWAYS written so a grandparent's override
                // can never leak past its own children (same reset
                // discipline as every channel above).
                .environment(\.marginCollapseOverride, collapsePlan?.overrides[index])
                // Text inheritance (css-cascade-4): publish this
                // element's merged inheritable declarations for the
                // child. Always written so each level's channel is
                // exactly its parent's merged set — no accumulation
                // beyond the CSS-inherited property list.
                .environment(\.inheritedTextProperties, childInherited)
                // Custom-property scope (wave 6, css-variables-1 §2):
                // publish this element's merged VariableStore — own
                // definitions shadowing the inherited chain — so every
                // child resolves var() against exactly its slot-parent
                // chain (TK_TwoLevelShadow's mid `--accent` repaint).
                .environment(\.cssVariables, mergedVariables)
            }
        } else if Self.suppressesNamePlaceholder(component,
                                                 wptCaptureMode: wptCaptureMode) {
            // WPT capture mode, nameless-empty leaf: the only thing the
            // leaf branch below would paint is the SYNTHESIZED component
            // name — a harness debug label the Chromium browser-ref (that
            // native captures are SSIM-compared against) never renders. Drop
            // it entirely for parity. Mirrors web's ?wpt=1 empty-visible-text
            // path (ComponentRenderer.tsx PlaceholderContent). The outer
            // styled box (background/border/size) still paints via the style
            // chain in `styledContent`; only the inner name glyphs vanish.
            // Unreachable for the whole static baseline corpus (flag OFF),
            // so every committed capture keeps its placeholder untouched.
            // Real element text takes the leading-text branch above (with
            // children) or the leaf branch below (component.text present).
            EmptyView()
        } else if component.text?.isEmpty ?? true {
            // Applier campaign (block font) — a leaf with NO real element
            // text renders only the SYNTHESIZED component-name label, a
            // harness debug artifact. Route it to the shared 5x7 block
            // font (BlockLabel.swift) instead of PlaceholderLabel's Text:
            // real font stacks antialias differently per platform and
            // capped ~50 label-bearing fixtures at SSIM 0.90-0.949, while
            // integer-rect glyphs rasterize byte-identically on all three.
            // The input string is EXACTLY what the Text showed before
            // (name, underscores → spaces); color is pinned to the legacy
            // default rgba(237,237,237,0.7) — the luminance contrast pick
            // and bg-clip:text gradient fill intentionally do NOT apply
            // here (the shared spec fixes ONE color so the three
            // platforms can be byte-identical; both only ever affected
            // this debug label, never real content). Real element text
            // (leaf `text` below, leading text above) keeps the full
            // PlaceholderLabel typography path — fonts are the actual
            // subject under test there. The WPT gate above already
            // dropped this label in capture mode, so the block path is
            // product/baseline-only, exactly like the Text it replaces.
            // Truncation width = the USED inline size: CSS resolves it as
            // max(min-width, min(width, max-width)) — the min-* floor only
            // ever WIDENS the box, so the binding value is the smallest of
            // the declared width and its max-* cap (mirrors web's
            // usedPxWidth in apps/web-harness ComponentRenderer.tsx; both
            // resolve through the same containing-block channel the flex
            // plan uses). nil = content-sized: the box hugs the label, so
            // nothing can overflow and no truncation applies.
            let ctx = style.spacing.context
            let usedWidth: CGFloat? = [
                SizeApplierResolve.exact(style.size.width, ctx: ctx,
                                         parent: ctx.containingBlockWidth),
                SizeApplierResolve.constraint(style.size.maxWidth, ctx: ctx,
                                              parent: ctx.containingBlockWidth),
            ].compactMap { $0 }.min()
            BlockLabel(
                label: component.name.replacingOccurrences(of: "_", with: " "),
                componentWidth: usedWidth
            )
        } else {
            // CSS `background-clip: text` + a `background-image`
            // gradient: render the gradient as the text fill instead
            // of as the rectangular bg. SwiftUI exposes
            // `.foregroundStyle(<ShapeStyle>)` which accepts a
            // LinearGradient directly, so we hand the gradient stops
            // to PlaceholderLabel and let it pick between gradient and
            // solid-colour foreground.
            let clipText: LinearGradient? = {
                guard style.backgroundClip?.mode == .text else { return nil }
                if let layers = style.backgroundImage?.layers,
                   let first = layers.first,
                   case .linear(_, let stops) = first {
                    let colors = stops.compactMap { $0.color.toSwiftUIColor() ?? Color.clear }
                    if colors.count >= 2 {
                        return LinearGradient(colors: colors,
                                              startPoint: .leading,
                                              endPoint: .trailing)
                    }
                }
                // Wave 5: a SOLID background-color clips to the glyphs
                // too (css-backgrounds-4 §2.2) — the browser paints no
                // rectangular box, just bg-coloured text
                // (PW_Background_Effects_01: web shows a #3498db label
                // on the bare canvas). StyleBuilder suppresses the rect
                // paint; here the colour becomes the glyph fill via the
                // same foregroundStyle path (a two-stop constant
                // gradient keeps PlaceholderLabel's API unchanged).
                if let bg = style.backgroundColor {
                    return LinearGradient(colors: [bg, bg],
                                          startPoint: .leading,
                                          endPoint: .trailing)
                }
                return nil
            }()
            // Bug 1 leaf-text: when the component carries `text` (the
            // v2 rename of `_text`), render it verbatim instead of the
            // underscore-stripped component name. Mirrors web
            // PlaceholderContent.text and brings iOS leaf-text rendering
            // in parity with the web fix (swarm-001/css-color__color-001).
            // Absent text no longer reaches this branch — the synthesized
            // name label renders via BlockLabel above (applier campaign).
            //
            // Wave 9 (#37): `Color` rides the inheritance channel now, so
            // style.text.color may be an ANCESTOR's color. The web leaf
            // placeholder never paints a DOM-inherited color (its span
            // pins own-declared-or-contrast-pick — pixel-sampled on
            // IT_Family), so an inherited-only color is gated back to nil
            // here and resolvedColor falls to the luminance pick.
            // currentColor consumers (border/outline) keep reading the
            // MERGED list and DO resolve against the inherited color —
            // the exact split the browser implements. The leading-text
            // label above keeps style.text.color un-gated: web renders
            // leading `_text` through a plain inheriting <span>.
            PlaceholderLabel(
                name: component.name,
                rawText: component.text,
                // Wave-5 gate follow-up — a currentColor declaration
                // with no ancestor to resolve it bottoms out into the
                // runtime DEFAULT TEXT COLOR (web-harness stage
                // contract: body color:#eee on #1a1a2e — the value the
                // browser's currentColor chain reaches), NOT the
                // contrast pick (see currentColorBottomsOut). Mutually
                // exclusive with colorIsInheritedOnly (which requires
                // an inherited Color to exist).
                // corpus-v4.1 ink sub-boundary: in WPT capture the
                // chain ends at the ref injection's `:where(body)
                // { color:#000 }` instead, so the bottom-out routes
                // through captureTextInk — spec BLACK in WPT mode, the
                // near-white stage default verbatim everywhere else
                // (the 327 baselines depend on that side never moving).
                color: colorIsInheritedOnly ? nil
                    : style.text.color
                        ?? (currentColorBottomsOut
                                ? WPTCanvas.captureTextInk(
                                    wptCaptureMode: wptCaptureMode,
                                    defaultInk: InheritedText.defaultTextColor)
                                : nil),
                textConfig: style.text,
                backgroundColor: style.backgroundColor,
                clipTextGradient: clipText,
                // CSS `text-align: right|center` positions the line box
                // inside the element's CONTENT box. SwiftUI's
                // .multilineTextAlignment only aligns lines against each
                // other, so a single-line label in a wider explicit-width
                // box stayed flush left (background/004_TextBlock,
                // borders/015_TextBlock). Fill the proposed width only
                // when the IR declared a width — otherwise the box hugs
                // (fit-content) and alignment is a no-op anyway.
                fillWidth: style.size.width != nil,
                // Thread the parent renderer's resolved WPT flag so the
                // composed-mode line-box pin + padding-0 fire (Round 4).
                wptCaptureMode: wptCaptureMode,
                // Lane IOS-TEXT fix 1 — content-box wrap width for the
                // greedy pre-break: multi-line real text must break where
                // Chromium/Compose break (greedy), not where TextKit's
                // push-out moves the soft break (see GreedyLineBreaker).
                wrapWidth: textWrapWidth(style: style)
            )
        }
    }

    // MARK: - Multicol fragmentation (wave 10, css-break-3 §4)

    /// The clip+translate draw pass for one overflowing multicol child:
    /// F clones of the child's full view, each clipped to its COLUMN
    /// rect and translated so fragment i exposes the child's continuous
    /// paint band [i·H, min((i+1)·H, C)) — box-decoration-break:slice
    /// (css-break-3 §5.2, the initial value): backgrounds paint as ONE
    /// unfragmented C-tall box, then each column shows a slice of it,
    /// so stripes/gradients continue seamlessly across columns.
    ///
    /// Modifier-order argument (inside-out):
    ///   ComponentHost                 — the FULL child (W×C layout).
    ///   .offset(y: −i·H)              — PAINT-only block shift: slides
    ///                                   band i up to the slot's top.
    ///                                   Offset does not change layout
    ///                                   size, so the frame below still
    ///                                   sees the W×C child.
    ///   .frame(W, H, .topLeading)     — the COLUMN rect: a fixed W×H
    ///                                   slot whose topLeading alignment
    ///                                   pins the child's top edge to
    ///                                   the slot's top edge (alignment
    ///                                   works on LAYOUT bounds — the
    ///                                   offset above is paint-only).
    ///   .clipped()                    — clips to the frame's bounds =
    ///                                   the column rect. Because the
    ///                                   offset sits INSIDE the clip,
    ///                                   the out-of-band paint (above /
    ///                                   below the slice) is cut; the
    ///                                   reverse nesting (.clipped()
    ///                                   before .frame, i.e. clipping
    ///                                   the child to its own W×C
    ///                                   bounds) would clip nothing and
    ///                                   let every fragment paint the
    ///                                   full child over its neighbours.
    /// The geometry's INLINE translation (+x_i = i·(W+G)) is realized
    /// STRUCTURALLY by the HStack: each slot is exactly W wide with G
    /// spacing, so slot i's origin is i·(W+G) by construction — adding
    /// .offset(x:) on top would double-shift the paint. The block
    /// translate comes straight from FragmentGeometry so the render
    /// consumes the exact S-table-pinned values.
    @ViewBuilder
    private func multicolFragmentRow(child: IRComponent,
                                     plan: ColumnsApplier.FragmentPlan) -> some View {
        // .top alignment: every fragment's block start sits at the
        // container's content-box top (column boxes share one row —
        // css-multicol-1 §2's column row).
        HStack(alignment: .top, spacing: plan.gapPx) {
            // columnIndex is unique by construction (0..<F) — a stable
            // ForEach identity for the clones.
            ForEach(plan.fragments, id: \.columnIndex) { frag in
                ComponentHost(component: child)
                    // css-multicol-1 §2: the child's containing block
                    // is the COLUMN box — override the container-width
                    // channel the outer ForEach publishes (the inner,
                    // leaf-closer .environment wins) so the child's
                    // percent widths resolve against W, consistent
                    // with the wave-9 wptChildFillWidth column basis.
                    .environment(\.containingBlockWidth, plan.columnWidthPx)
                    // Block-axis slice shift (−i·H) — see the modifier-
                    // order argument in the doc comment above.
                    .offset(y: frag.translate.height)
                    // The column rect as a fixed frame; topLeading pins
                    // the child's top-left to the slot's top-left.
                    .frame(width: frag.clipRect.width,
                           height: frag.clipRect.height,
                           alignment: .topLeading)
                    // Clip to the column rect — the pass' clip step.
                    .clipped()
            }
        }
    }
}

// MARK: - Placeholder

/// Mirrors the web/Android placeholder: the component's name, centered, small,
/// with smart light/dark contrast based on background luminance.
///
/// `rawText` (Bug 1, swarm-001/css-color__color-001 + swarm-002 mixed-content):
/// when non-nil/non-empty, this string is rendered verbatim instead of the
/// underscore-stripped `name`. Used for the IR's `_text` channel, which
/// carries the actual element text content (a green sentence, an
/// arabic-indic glyph, the parent's "abc def" surrounding a styled
/// child). Absent rawText → existing placeholder behaviour (component
/// name with underscores → spaces), preserving the 327-pair baseline.
private struct PlaceholderLabel: View {
    let name: String
    var rawText: String? = nil
    let color: Color?
    let textConfig: TextConfig
    let backgroundColor: Color?
    // Non-nil → CSS `background-clip: text` is in effect; the gradient
    // paints inside the glyph shape via `.foregroundStyle`. Wins over
    // `color` when set so the rendered output matches web's clipped
    // text only (no rectangular bg fill).
    var clipTextGradient: LinearGradient? = nil
    // Fidelity wave 1 — when true, the label expands to the full
    // proposed width so `text-align` (multilineTextAlignment + frame
    // alignment below) can position the glyph run inside a box that is
    // wider than the text. ONLY set when the IR declared an explicit
    // width: expanding unconditionally would defeat the fit-content
    // hugging every placeholder-only fixture depends on.
    var fillWidth: Bool = false

    // TITAN Round 4 (GAP 1, height half) — the WPT capture flag, threaded
    // EXPLICITLY from ComponentRenderer (which owns the @Environment) so the
    // value is unambiguous at this single call site. Default false = product
    // + every committed baseline unchanged; the composed WPT path passes
    // true. When on, the placeholder pins the ref line box (20px @16px —
    // the corpus-v4.1 REF_LINE_HEIGHT pin, see wptRefLineBoxPx) and
    // drops its 4px breathing room so a text bar is as tight as the
    // browser-ref's `<p>` (see ComponentRenderer.effectiveLineHeight + the
    // padding gate below). The 50×30 MinBoxFloor is dropped in the same mode
    // (StyleBuilder.MinBoxFloor) so this line box actually sets the height.
    var wptCaptureMode: Bool = false

    // Lane IOS-TEXT fix 1 — the CONTENT-BOX inline size this run wraps
    // at (ComponentRenderer.textWrapWidth). When set and the run can
    // wrap, the text is PRE-BROKEN greedily (GreedyLineBreaker) so line
    // breaks land where Chromium/Compose put them instead of where
    // TextKit's push-out orphan avoidance moves them. Nil = no wrap
    // geometry known → the legacy soft-wrap path, byte-identical.
    var wrapWidth: CGFloat? = nil

    var body: some View {
        // Resolve the visible string: rawText wins when present (the IR
        // carried explicit element text content), otherwise fall back to
        // the legacy name-with-underscores-stripped placeholder.
        let visibleText: String = {
            if let t = rawText, !t.isEmpty { return t }
            return name.replacingOccurrences(of: "_", with: " ")
        }()
        // Lane IOS wave 5 (finding 1) — text-transform is a STRING
        // rewrite applied BEFORE the greedy break so measurement sees
        // the exact glyphs that render (css-text-3 §2.1; case folds and
        // titlecasing change advances). Previously only `capitalize`
        // was folded here while uppercase/lowercase rode the box-level
        // `.textCase` environment applied AFTER measurement — committed
        // lines overflowed once rendered and TextKit re-broke them
        // (push-out included). The `.textCase(nil)` on this label's own
        // chain below suppresses the environment transform so measure
        // and render share ONE string (mirror of Compose's
        // placeholderDisplayText, which transforms before layout).
        let transformedText = TextTransformApplier.renderString(
            visibleText,
            textCase: textConfig.textCase,
            capitalize: textConfig.capitalizeWords)
        // Lane IOS-TEXT fix 1 — greedy pre-break. Gated on: a known wrap
        // width, wrapping not suppressed (white-space/text-wrap nowrap,
        // css-text-4 §5.1), preserved-whitespace modes OFF (wave 5
        // finding 3 — pre-wrap/break-spaces keep space runs per
        // css-text-3 §4.1.2, and the space-split below would collapse
        // them: a glyph-content rewrite; those modes take the legacy
        // soft-wrap path), and a break opportunity existing at all.
        let displayText: String = {
            guard let cb = wrapWidth, !textConfig.noWrap,
                  !textConfig.preservesSpaces,
                  transformedText.contains(" ") else { return transformedText }
            // Text width available inside the label: the content box
            // minus the 4px breathing inset each side (dropped in WPT
            // capture, mirroring the padding gate below) and the
            // text-indent leading pad — both shrink the line box.
            let avail = cb - (wptCaptureMode ? 0 : 8) - (textConfig.textIndentPx ?? 0)
            guard avail > 0 else { return transformedText }
            // Measure with the EXACT resolved render face + spacing so
            // the fit test uses the advances TextKit renders with.
            let lines = GreedyLineBreaker.lines(
                text: transformedText,
                maxWidth: avail,
                measure: GreedyLineBreaker.measurer(
                    font: measurementUIFont,
                    letterSpacingPx: textConfig.letterSpacing,
                    wordSpacingPx: textConfig.wordSpacingPx))
            // Hard newlines force TextKit to OUR break positions — its
            // push-out strategy only relocates SOFT breaks, and every
            // pre-broken line fits `avail` by construction.
            return lines.joined(separator: "\n")
        }()
        // TITAN Round 4 (GAP 1, height half) — the line-height this run
        // lays out with: the IR-declared value when present (defer to it),
        // else the ref line box (20px — corpus-v4.1) in WPT capture, else nil (SwiftUI
        // natural metrics — the unchanged product path). Feeds BOTH the
        // leading split and the minHeight frame below so the bar height +
        // baselines track the browser-ref's default-font `<p>`.
        let effectiveLineHeight = ComponentRenderer.effectiveLineHeight(
            declared: textConfig.lineHeight, wptCaptureMode: wptCaptureMode)
        // Fidelity wave 3 — CSS line-box leading split (CSS 2.1 §10.8):
        // `spacing` makes each line ADVANCE exactly line-height px;
        // `halfLeading` restores the band above the first / below the
        // last line that browsers paint and SwiftUI's between-lines-only
        // `.lineSpacing` dropped (IH_LineHeight band-sits-high channel).
        // Both are 0 when the effective line-height is nil.
        let leading = LineBoxMetrics.leading(lineHeightPx: effectiveLineHeight,
                                             fontSizePx: textConfig.fontSize ?? 16,
                                             design: textConfig.fontDesign)
        // TITAN Round 4 (GAP 1, height half) — single-line proxy for the
        // WPT line-box CAP below. A run with NO internal whitespace can never
        // wrap, so in composed WPT capture we can pin its box to EXACTLY one
        // ref line box (20px @16px — the corpus-v4.1 REF_LINE_HEIGHT pin;
        // the ref no longer lays `<p>`s out on font-`normal` metrics), while
        // the Inter face's own natural metrics could report a different line
        // and every bar would drift down a 10-bar test. Capping
        // the single-line box to the ref line box removes that drift. Text
        // that MAY wrap (any whitespace) is left to grow to N line boxes so
        // multi-line content (e.g. a full-sentence `<p>`) is never clipped.
        // (Reads the DISPLAY string so a greedily pre-broken run — which
        // contains \n — always counts as multi-line, same as before.)
        let singleLineText = !displayText.contains { $0.isWhitespace }
        // Applier campaign (sub-natural line-height placement) — the
        // signed-half-leading compensation for L < natural content
        // height (LineBoxMetrics.subNaturalOffset header for the full
        // model). Gated OFF for the WPT single-line path: there the
        // Round-4 maxHeight cap compresses the frame to exactly L and
        // the `.center` alignment already overflows the glyph band
        // evenly above/below — i.e. the browser's negative-half-leading
        // placement — so adding the offset would double-shift those
        // calibrated captures. Every other path (the whole product
        // renderer) keeps the frame uncompressed at natural height and
        // needs the explicit translation. 0 whenever L ≥ natural.
        let subNaturalShift: CGFloat = (wptCaptureMode && singleLineText)
            ? 0
            : LineBoxMetrics.subNaturalOffset(lineHeightPx: effectiveLineHeight,
                                              fontSizePx: textConfig.fontSize ?? 16,
                                              design: textConfig.fontDesign)
        // Multi-line sub-natural line boxes stay UNCOMPRESSED: SwiftUI's
        // `.lineSpacing` cannot go negative, so the per-line ADVANCE
        // remains the natural content height while a browser advances
        // exactly L — only the first-line placement is compensated.
        // Honest limitation, surfaced once per process (repo no-silent-
        // fallthrough rule; same vehicle as the soft-wrap breadcrumb).
        let _ = (subNaturalShift < 0 && displayText.contains("\n"))
            && PropertyTracker.logOnce(
                key: "line-height:sub-natural-multiline",
                message: "sub-natural line-height on multi-line text: " +
                    "placement compensated, advance stays natural " +
                    "(SwiftUI lineSpacing cannot be negative)")
        let textView = wordSpacedText(displayText)
            .font(font)
        // SwiftUI's `.foregroundStyle` accepts ANY ShapeStyle including
        // LinearGradient, so when bg-clip:text is on we replace the
        // foreground colour with the gradient.
        return Group {
            if let g = clipTextGradient {
                textView.foregroundStyle(g)
            } else {
                textView.foregroundColor(resolvedColor)
            }
        }
            // Lane IOS wave 5 (finding 1) — suppress the box-level
            // `.textCase` environment for this label's own Text: the
            // transform is already folded into the STRING above (so the
            // greedy measurement saw the rendered glyphs), and letting
            // the environment re-case the run would leave nothing for
            // measure/render to disagree on only by accident. Innermost
            // environment write wins over TypographyApplier's
            // TextCaseMod; an INHERITED text-transform still reaches
            // this label through the merged property list → TextConfig,
            // so no declared transform is ever dropped.
            .textCase(nil)
            // Wave-5 gate follow-up (decoration ownership) — suppress
            // the PLATFORM built-in underline/strikethrough on this
            // label's own Text when (and only when) the owned overlay
            // below draws them: the built-ins are ~1px lines at
            // platform offsets (the device gate measured Underline
            // 0.809 / UnderOver 0.740 / Triple 0.737 against Chromium's
            // 2px bands) and double-drawing would smear both. The
            // box-level UnderlineMod/StrikethroughMod (TypographyApplier)
            // still fire from the SAME aggregate flags — the innermost
            // `.underline(false)`/`.strikethrough(false)` here wins for
            // this Text only. Non-label Text paths (list markers, an
            // ANCESTOR's propagated decoration reaching a child label
            // whose own flags are false) keep the built-ins — CSS
            // decoration propagation (css-text-decor-3 §2.1) still
            // rides the box-level modifier for them.
            .modifier(OwnedDecorationSuppressor(
                underline: ownsUnderline,
                strikethrough: ownsStrikethrough))
            // Fidelity wave 2 — text-shadow paints behind the GLYPHS
            // (css-text-decor-3 §4), so the `.shadow` chain attaches
            // right here on the text, before any frame/background can
            // widen the shadow caster. One call per CSS layer; SwiftUI
            // radius is the gaussian σ ≈ CSS blur-radius / 2.
            .modifier(GlyphShadows(layers: textConfig.shadows))
            // Lane IOS wave 5 (finding 4) + wave-5 gate follow-up —
            // `text-decoration-line` (css-text-decor-3 §2.1). The owned
            // decoration pass: one Rectangle per rendered line per
            // declared line (underline / overline / line-through), with
            // Chromium-measured geometry (DecorationMetrics header for
            // the capture-derived rows): width = the line's measured
            // advance (the SAME measurer the greedy fit test used),
            // y offsets = the empirical baseline-relative fractions,
            // thickness = max(1, round(fontSize/11)). Attached HERE —
            // before padding/frame modifiers — so the overlay's
            // coordinate space is the text's own bounds, and painted
            // AFTER the glyphs like the browser's decoration paint
            // order (over-position lines paint over ink).
            .overlay(alignment: .topLeading) {
                decorationOverlay(displayText: displayText,
                                  lineSpacing: leading.spacing)
            }
            .multilineTextAlignment(textConfig.textAlign)
            // No hard line cap — let the text wrap to fit the available
            // width and rely on the parent box's height to clip overflow.
            // SwiftUI by default truncates at one line when the layout
            // pass can't determine a height; the `.fixedSize(vertical:)`
            // tells the layout to grow vertically as needed instead, so
            // wrapping behaves like web's natural overflow on
            // placeholder-only fixtures (Typography_TextShadow_*,
            // Typography_FontWeight_Boundary_1000 etc.).
            //
            // Fidelity wave 2 — horizontal is `noWrap` when white-space/
            // text-wrap declared nowrap (css-text-4 §5.1): the run lays
            // out on ONE line at full intrinsic width, overflowing the
            // box to the right exactly like the web reference
            // (Typography_C20/C21 previously wrapped to 2 lines).
            .lineLimit(nil)
            .fixedSize(horizontal: textConfig.noWrap, vertical: true)
            // Fidelity wave 3 — first/last half-leading (CSS 2.1
            // §10.8.1): browsers centre each line's glyphs inside a
            // line box `line-height` tall, so half the leading paints
            // ABOVE the first line and BELOW the last. Adding it as
            // real vertical padding (a) grows an auto-height multi-line
            // box to exactly N × line-height like the web reference and
            // (b) keeps the single-line case byte-identical — content
            // + 2 × half-leading = line-height, the same box the
            // minHeight frame below already produced (glyphs were
            // already centred in it). Zero when no line-height set.
            .padding(.vertical, leading.halfLeading)
            // CSS `line-height` — total line-box height. SwiftUI's
            // `.lineSpacing` adds EXTRA between lines, which is invisible
            // for a single-line placeholder. Force the text frame to be
            // at least `lineHeight` tall so the surrounding box grows
            // to match web/Android's line-box height (e.g. font-size 14
            // + line-height 2 = 28px text box, not the bare 14px iOS
            // would otherwise allocate).
            //
            // fillWidth (fidelity wave 1): `maxWidth: .infinity` accepts
            // the parent's proposal so text-align has room to act; the
            // frame's horizontal alignment mirrors the CSS keyword
            // (right → trailing, center → center).
            //
            // Fidelity wave 2 — minHeight is nil (NOT 0) when the IR set
            // no line-height. Passing 0 made this frame ADOPT the parent
            // height proposal (FrameLayout: a constrained axis clamps
            // the proposal, not the child), so in a fixed-height box
            // shorter than the wrapped text the frame shrank to the
            // proposal and the `.center` alignment spilled glyphs ABOVE
            // the box top — impossible in CSS block flow (Typography_C04
            // /C19, Spacing_C08). The trailing `.fixedSize(vertical:)`
            // pins the frame at its ideal (text) height so overflow now
            // hangs BELOW the box like web/Android; `.center` remains
            // only for the single-line half-leading case where the
            // line box (minHeight) exceeds the glyph height.
            .frame(maxWidth: fillWidth ? .infinity : nil,
                   minHeight: effectiveLineHeight,
                   // Round 4 — in composed WPT capture, CAP a single-line box
                   // to exactly the ref line box (glyphs overflow like the
                   // browser's line-height:18) so forced-Inter bars stop
                   // drifting; multi-line/other paths keep the floor-only
                   // frame (maxHeight nil) so nothing is clipped.
                   maxHeight: (wptCaptureMode && singleLineText)
                       ? effectiveLineHeight : nil,
                   alignment: Alignment(horizontal: fillHorizontal,
                                        vertical: .center))
            .fixedSize(horizontal: false, vertical: true)
            // Fidelity wave 3 — line ADVANCE = line-height exactly.
            // The extra between-lines space is `line-height − content
            // area height` (ascent + descent of the rendered face), NOT
            // `line-height − font-size` as before: font-size undershoots
            // the content area by ~21% of an em for Inter, so every
            // advance overshot the browser's by that difference while
            // the box total still came out short (no first/last band).
            .lineSpacing(leading.spacing)
            // Applier campaign (sub-natural line-height placement) —
            // translate the glyph run (plus its owned decoration
            // overlay, attached above so it rides along) UP by the
            // signed half-leading when L < natural. `.offset` is the
            // same out-of-flow-safe vehicle BlockLabel uses: a pure
            // render translation that never re-enters layout, so the
            // line BOX stays uncompressed at natural height and only
            // the PAINT position matches the browser's negative-half-
            // leading model (web inkTop = 8 + (L − natural)/2, pixel-
            // verified). Exactly 0 for every L ≥ natural render —
            // byte-stable for the whole non-sub-natural corpus.
            .offset(y: subNaturalShift)
            // CSS `text-indent` — push the text right by the indent
            // amount. SwiftUI lacks a first-line-only API, so we use
            // leading padding which inherits to wrapped lines too. For
            // the placeholder fixture set (mostly single-line) this
            // matches web/Android pixel-for-pixel; multi-line cases
            // diverge but no fixture exercises that today.
            .padding(.leading, textConfig.textIndentPx ?? 0)
            // TITAN Round 4 (GAP 1, height half) — the 4px label breathing
            // room drops to 0 in WPT capture so a text bar is exactly one
            // line box tall, matching the browser-ref's native `<p>` (which
            // has no such inset). Mirrors web's composed-mode `padding: 0`
            // on PlaceholderContent. Every non-WPT path keeps the 4px, so
            // the product renderer + 327-pair baseline are byte-identical.
            .padding(wptCaptureMode ? 0 : 4)
    }

    /// Horizontal frame alignment mirroring the CSS text-align keyword.
    /// Leading when not filling (identity — the frame hugs the text).
    private var fillHorizontal: HorizontalAlignment {
        guard fillWidth else { return .leading }
        switch textConfig.textAlign {
        case .center:   return .center
        case .trailing: return .trailing
        case .leading:  return .leading
        }
    }

    private var font: Font {
        // Build via `.system(size:design:)` so generic-family signals
        // (`font-family: serif/monospace/ui-rounded`) survive — placing
        // `.font(...)` directly on Text overrides any container-level
        // font, so the design has to be baked in here. See TextConfig
        // header for the full chain.
        //
        // Placeholder default size = 16pt to mirror the web browser's
        // inherited body default (16px). The web placeholder
        // (`PlaceholderContent` in `ComponentRenderer.tsx`) sets no
        // explicit font-size and therefore renders at the harness body's
        // size, which CapturePage doesn't override → 16px. Previously this
        // path defaulted to 11pt, leaving placeholder boxes ~30% smaller
        // than their web counterparts on every placeholder-only fixture
        // without an explicit `font-size` (Card_Complete, Input_Field,
        // Outline_Solid, Shadow_Simple, Neumorphic_Light, Tag_Chip, …).
        // Box dimensions follow text size for `width: fit-content` /
        // `wrapContentSize` containers, so the 11→16 bump propagates to
        // overall component geometry, which is what SSIM is comparing.
        // Use bundled Inter as the default placeholder face (cross-platform
        // parity with the Android InterFontFamily and the web @font-face
        // declaration). Only fall back to the system font when the CSS
        // explicitly requested a generic-family signal (.serif / .mono /
        // .rounded) — those paths legitimately want SF Pro variants.
        let size = textConfig.fontSize ?? 16
        var f: Font
        // Lane IOS-TEXT fix 5 — when the §5.2 face pick lands, the
        // weight is BAKED into the concrete face and `.weight()` must
        // not run (it would hand the pick back to CoreText's heuristic).
        var weightBaked = false
        // Wave 6 (font-style oblique) — remember the concrete Inter face
        // name so the synthetic-italic branch below can rebuild it as a
        // matrix-skewed UIFont (SwiftUI's `.italic()` can't slant a
        // family that ships no italic face — it silently no-ops).
        var interFaceName: String? = nil
        if textConfig.fontDesign == .default {
            // css-fonts-4 §5.2 concrete-face selection over the installed
            // Inter faces (Regular/Medium/Bold/Black in the harness):
            // CoreText's `.weight()` nearest-face heuristic rounds DOWN
            // at 600/800 (FontWeight_800 rendered Bold-visual) while
            // Chromium and Compose select the next face UP — §5.2's
            // ">500: heavier weights first, ascending" clause.
            if let n = textConfig.fontWeightNumeric,
               let face = FontFaceMatcher.faceName(family: "Inter",
                                                   desiredWeight: n) {
                f = .custom(face, size: size)
                weightBaked = true
                interFaceName = face
            } else {
                // No numeric weight / family not registered (unit-test
                // bundle) → the legacy Regular + `.weight()` path.
                f = .custom("Inter", size: size)
                interFaceName = "Inter"
            }
        } else {
            f = Font.system(size: size, design: textConfig.fontDesign)
        }
        if !weightBaked, let w = textConfig.fontWeight { f = f.weight(w) }
        if textConfig.fontItalic {
            // Wave 6 (font-style oblique) — the bundled Inter family ships
            // roman faces only, so `.italic()`'s symbolic-trait lookup
            // finds nothing and silently no-ops: the wave-6 iOS captures
            // rendered plain `italic` fully upright while web and Android
            // both SYNTHESIZED a slant. Mirror their font synthesis
            // (css-fonts-4 §6, font-synthesis-style) with the same fixed
            // matrix shear both use (Skia textSkewX -0.25 ≈ 14deg).
            // Guard: only when the resolved face carries the final weight
            // (weightBaked, or no weight requested) — a UIFont-backed
            // Font can't take the `.weight()` chain applied above.
            if let name = interFaceName,
               textConfig.fontWeight == nil || weightBaked,
               let base = UIFont(name: name, size: size) {
                // CTFont-backed Font keeps the descriptor matrix alive
                // through SwiftUI's Text layout (Font.init(_: CTFont)).
                f = Font(Self.syntheticObliqueUIFont(base) as CTFont)
            } else {
                // SF system designs DO carry real italic faces, and the
                // unit-test bundle (Inter unregistered) keeps the legacy
                // behavior — `.italic()` works or degrades exactly as
                // before, never worse than the pre-fix render.
                f = f.italic()
            }
        }
        // Fidelity wave 2 — `font-variant-caps: small-caps` composes on
        // the label's own font. The box-level FontMod can't reach this
        // Text (a direct `.font` wins over container fonts, Apple docs),
        // so the caps variant must be baked in here (Typography_C06).
        if textConfig.smallCaps { f = f.smallCaps() }
        return f
    }

    // MARK: - Lane IOS-TEXT helpers

    /// The UIKit twin of `font` above — the EXACT face the label renders
    /// with, used by the greedy pre-break's measurement lane so fit
    /// tests and rendering share one set of glyph advances (fix 1).
    private var measurementUIFont: UIFont {
        // Same 16pt web-body default as `font`.
        let size = textConfig.fontSize ?? 16
        if textConfig.fontDesign == .default {
            // §5.2 face pick first (fix 5), mirroring `font` exactly.
            if let n = textConfig.fontWeightNumeric,
               let face = FontFaceMatcher.faceName(family: "Inter",
                                                   desiredWeight: n),
               let f = UIFont(name: face, size: size) {
                return f
            }
            // Regular Inter when registered (harness app process).
            if let f = UIFont(name: "Inter", size: size) { return f }
        }
        // System fallback: weight + generic-family design mapped onto
        // the SF descriptor — the same face `Font.system(size:design:)`
        // resolves to, so measurement still matches rendering.
        var f = UIFont.systemFont(ofSize: size,
                                  weight: Self.uiKitWeight(textConfig.fontWeight))
        if let d = Self.uiKitDesign(textConfig.fontDesign),
           let desc = f.fontDescriptor.withDesign(d) {
            f = UIFont(descriptor: desc, size: size)
        }
        // Wave 6 — italic composes as the SAME fixed-matrix synthetic
        // oblique the render path uses (was `.traitItalic`, which returns
        // nil for Inter and a different face for SF). A pure horizontal
        // shear leaves glyph advances untouched, so the Inter early-
        // returns above stay measurement-correct without the shear.
        if textConfig.fontItalic {
            f = Self.syntheticObliqueUIFont(f)
        }
        return f
    }

    /// Struct-local alias so existing `Self.` call sites read naturally;
    /// the real (unit-tested) implementation is the file-scope
    /// `syntheticObliqueUIFont` below — PlaceholderLabel is private, so
    /// the skew math must live at module scope to be pinnable from
    /// TypographyTests via @testable import.
    private static func syntheticObliqueUIFont(_ f: UIFont) -> UIFont {
        StyleConverterRuntime.syntheticObliqueUIFont(f)
    }

    /// SwiftUI Font.Weight → UIFont.Weight (identical 9-step ladders).
    private static func uiKitWeight(_ w: Font.Weight?) -> UIFont.Weight {
        switch w {
        case .ultraLight: return .ultraLight
        case .thin:       return .thin
        case .light:      return .light
        case .medium:     return .medium
        case .semibold:   return .semibold
        case .bold:       return .bold
        case .heavy:      return .heavy
        case .black:      return .black
        default:          return .regular   // nil / .regular → regular
        }
    }

    /// SwiftUI Font.Design → UIFontDescriptor.SystemDesign (nil for
    /// `.default` — the plain SF face needs no descriptor rewrite).
    private static func uiKitDesign(_ d: Font.Design) -> UIFontDescriptor.SystemDesign? {
        switch d {
        case .serif:      return .serif
        case .monospaced: return .monospaced
        case .rounded:    return .rounded
        default:          return nil
        }
    }

    /// Lane IOS-TEXT fix 2 / wave 5 (findings 2 + 6) — the Text view for
    /// `displayText`, with CSS `word-spacing` applied as an
    /// AttributedString `.kern` on each word separator — SPACE and NBSP
    /// (css-text-3 §8.1: word-spacing ADDS to each word-separator's
    /// advance, and NBSP is a word separator; kern after a glyph is
    /// exactly that advance adjustment). When letter-spacing is ALSO
    /// declared, the box-level `.tracking()` would SUPPRESS these kerns
    /// (SwiftUI documents tracking as overriding kerning on a Text), so
    /// TypographyApplier skips the TrackingMod in that combination and
    /// the letter-spacing is baked here as `.kern` on EVERY character
    /// (separators get letter + word) — the exact attribute model
    /// `GreedyLineBreaker.measurer` fits with, so measurement and render
    /// share one spacing model. Identity `Text(String)` when no
    /// word-spacing is in effect, keeping every existing render
    /// byte-stable. The attribute construction lives in
    /// `WordSpacingApplier.kernedRun` (pure, XCTest-pinned).
    private func wordSpacedText(_ s: String) -> Text {
        // nil = nothing for the kern lane to do (letter-spacing alone
        // stays on the legacy box-level tracking) → plain-string Text.
        guard let attr = WordSpacingApplier.kernedRun(
            text: s,
            letterSpacingPx: textConfig.letterSpacing,
            wordSpacingPx: textConfig.wordSpacingPx) else { return Text(s) }
        return Text(attr)
    }

    /// Wave-5 gate follow-up — does the owned overlay draw the
    /// underline for this label? Requires the flag AND a solid
    /// decoration style: dashed/dotted/wavy/double keep the platform
    /// built-in (its pattern rendering is closer to web than a solid
    /// owned rect would be), so those paths lose nothing.
    private var ownsUnderline: Bool {
        textConfig.underline && textConfig.decorationStyle == .solid
    }

    /// Same ownership rule for line-through (see ownsUnderline).
    private var ownsStrikethrough: Bool {
        textConfig.strikethrough && textConfig.decorationStyle == .solid
    }

    /// Lane IOS wave 5 (finding 4) + wave-5 gate follow-up — the
    /// per-line decoration overlay, now owning ALL THREE decoration
    /// lines. EmptyView unless a decoration the overlay owns was
    /// declared, so every other render is byte-identical. Geometry:
    /// the display string's hard-broken lines (a non-pre-broken label
    /// is a single line — covered by the same measurer, per the lane
    /// prescription), each line's inked width from the SAME TextKit
    /// measurer the greedy fit test used, line top = index × (content
    /// line height + CSS inter-line spacing) — exactly the advance
    /// `.lineSpacing` makes TextKit lay out — and the per-kind y
    /// offsets from DecorationMetrics (the Chromium-capture oracle,
    /// anchored on the render face's own ascent so the geometry is
    /// font-metric-parameterized, not hardcoded to 22px). Color:
    /// text-decoration-color when declared, else the resolved text
    /// color (css-text-decor-3 §2.2 initial `currentColor`).
    @ViewBuilder
    private func decorationOverlay(displayText: String,
                                   lineSpacing: CGFloat) -> some View {
        if textConfig.overline || ownsUnderline || ownsStrikethrough {
            // The rendered lines this label draws (pre-broken runs carry
            // hard \n breaks; everything else is one visual line).
            let lines = displayText.components(separatedBy: "\n")
            // One un-ownable case: a multi-word run that was NOT
            // pre-broken (unknown wrap width, or a preserved-whitespace
            // mode gating the pre-break off) MAY still soft-wrap under a
            // narrower proposal, and this overlay cannot see TextKit's
            // soft breaks. Surfaced via logOnce — no silent drop — and
            // painted best-effort (the dominant case, fit-content
            // hugging, never soft-wraps).
            let softWrapUnknown = !textConfig.noWrap && lines.count == 1
                && lines[0].contains(" ")
                && (wrapWidth == nil || textConfig.preservesSpaces)
            let _ = softWrapUnknown && PropertyTracker.logOnce(
                key: "decoration-softwrap-\(name)",
                message: "text-decoration: line geometry unknown for a "
                    + "soft-wrappable un-pre-broken run — overlay assumes "
                    + "a single line")
            // Same face + spacing as the render → identical advances.
            let segs = DecorationMetrics.segments(
                lines: lines,
                fontSizePx: textConfig.fontSize ?? 16,
                measure: GreedyLineBreaker.measurer(
                    font: measurementUIFont,
                    letterSpacingPx: textConfig.letterSpacing,
                    wordSpacingPx: textConfig.wordSpacingPx))
            // Line ADVANCE = rendered content height + the CSS leading
            // split's between-lines extra (the label's `.lineSpacing`).
            let advance = measurementUIFont.lineHeight + lineSpacing
            // Baseline anchor: the RENDER face's ascent — the same
            // number TextKit lays glyphs out with, so the empirical
            // baseline-relative offsets track any font/size.
            let ascent = measurementUIFont.ascender
            // The decoration fractions scale with the declared size
            // (16 = the label's web-body default, see `font`).
            let fontSize = textConfig.fontSize ?? 16
            // §2.2: decoration-color, initial currentColor → text color.
            let color = textConfig.decorationColor ?? resolvedColor
            ZStack(alignment: .topLeading) {
                ForEach(segs, id: \.index) { seg in
                    // This line box's top edge in the text's own space.
                    let lineTop = CGFloat(seg.index) * advance
                    // css-text-decor-3 §2.1 — each declared line paints
                    // independently at its own measured offset.
                    if textConfig.overline {
                        decorationRow(seg, color: color, y: lineTop
                            + DecorationMetrics.overlineTop(fontSizePx: fontSize))
                    }
                    if ownsStrikethrough {
                        decorationRow(seg, color: color, y: lineTop
                            + DecorationMetrics.lineThroughTop(
                                ascentPx: ascent, fontSizePx: fontSize))
                    }
                    if ownsUnderline {
                        decorationRow(seg, color: color, y: lineTop
                            + DecorationMetrics.underlineTop(
                                ascentPx: ascent, fontSizePx: fontSize))
                    }
                }
            }
        }
    }

    /// One decoration band: the segment's inked advance × the auto
    /// thickness, aligned to its line by the CSS text-align keyword and
    /// offset to the kind's measured row (y is relative to the text's
    /// top-leading corner; negative for a first-line overline, which
    /// hangs above the line box like Chromium's ink-overflow paint).
    private func decorationRow(_ seg: DecorationMetrics.Segment,
                               color: Color, y: CGFloat) -> some View {
        Rectangle()
            .fill(color)
            // The band: this line's inked advance × the measured auto
            // thickness (max(1, round(fontSize/11)) — DecorationMetrics).
            .frame(width: seg.width, height: seg.thickness)
            // Horizontal placement: shorter lines sit where
            // `.multilineTextAlignment` puts them, so each row spans
            // the text bounds and aligns its rect by the same keyword.
            .frame(maxWidth: .infinity, alignment: decorationRowAlignment)
            // Vertical placement: the kind's measured row.
            .offset(y: y)
    }

    /// The overlay-row alignment mirroring CSS text-align — the same
    /// keyword `.multilineTextAlignment` positions the glyph lines with,
    /// so each decoration rect tracks its own line horizontally.
    private var decorationRowAlignment: Alignment {
        switch textConfig.textAlign {
        case .center:   return .center
        case .trailing: return .trailing
        case .leading:  return .leading
        }
    }

    private var resolvedColor: Color {
        if let c = color { return c }
        // corpus-v4.1 ink sub-boundary (WPT mode only): a real WPT page's
        // default prose is the UA `color: CanvasText` BLACK, and the
        // browser-ref now injects the same spec black
        // (capture-browser-ref.mjs `:where(body) { color:#000 }`). The
        // near-white contrast fallback below vanished into the v4 white
        // canvas exactly like the ref's old white ink, so default-ink text
        // tests passed VACUOUSLY. WPT capture therefore bottoms out at
        // WPTCanvas.textInk (opaque #000), in lock-step with web
        // (index.html wpt-mode rule + PlaceholderContent WPT_MODE ink) and
        // Compose (WPT_DEFAULT_TEXT_INK). The luminance pick below is the
        // 327-pair stage contract and stays byte-identical — the flag is
        // false on every baseline path.
        if wptCaptureMode { return WPTCanvas.textInk }
        // Web default: inherit #eee @ 0.7 on dark bg; dark text on light bg.
        // Note: alpha is intentionally NOT folded into the luminance here.
        // For translucent fills like rgba(255,255,255,0.2) the raw RGB is
        // white (luminance 1.0) so we pick dark text — which is what iOS
        // renders correctly anyway because `backdrop-filter: blur(...)` is
        // implemented via `.thinMaterial`, lightening the captured surface
        // to a mid-grey. Dark-on-mid-grey is the legible pick. The earlier
        // Glass_Effect bug on Android was caused by a different mechanism
        // (Modifier.blur on the foreground destroyed the placeholder text
        // outright); see FilterApplier.applyBackdropFilters for the fix.
        if let bg = backgroundColor,
           let components = bg.rgbComponents {
            let luminance = 0.299 * components.r + 0.587 * components.g + 0.114 * components.b
            return luminance > 0.6
                ? Color(white: 0.2).opacity(0.7)
                : Color(white: 0.93).opacity(0.7)
        }
        return Color(white: 0.93).opacity(0.7)
    }
}

/// Wave-5 gate follow-up (decoration ownership) — turns OFF the
/// platform's built-in underline/strikethrough for the label's own Text
/// when the owned Rectangle overlay draws those lines instead. SwiftUI's
/// `.underline(_:)`/`.strikethrough(_:)` are text-styling writes where
/// the value CLOSEST to the Text wins (the same innermost-wins rule the
/// label already relies on for `.textCase(nil)`), so an explicit `false`
/// here beats the box-level UnderlineMod/StrikethroughMod that
/// TypographyApplier attaches from the same aggregate flags. Identity
/// when neither line is owned — an ancestor's PROPAGATED decoration
/// (css-text-decor-3 §2.1 reaches descendant Texts through the ancestor
/// box's modifier) and every non-label Text keep their built-ins.
private struct OwnedDecorationSuppressor: ViewModifier {
    /// True = the owned overlay draws the underline → kill the built-in.
    let underline: Bool
    /// True = the owned overlay draws the line-through → kill built-in.
    let strikethrough: Bool
    func body(content: Content) -> some View {
        // Conditional chain: only the OWNED lines are suppressed, so a
        // label owning just an underline keeps e.g. an ancestor's
        // propagated strikethrough built-in. AnyView keeps the two
        // independent conditions from exploding the generic signature.
        var v = AnyView(content)
        // Explicit inactive underline write — innermost wins over the
        // box-level `.underline(true, …)`.
        if underline { v = AnyView(v.underline(false)) }
        // Explicit inactive strikethrough write — same rule.
        if strikethrough { v = AnyView(v.strikethrough(false)) }
        return v
    }
}

// MARK: - Glyph shadows (fidelity wave 2)

/// Chains one `.shadow(...)` per CSS text-shadow layer directly on the
/// glyph view (css-text-decor-3 §4 — the shadow caster is the text, not
/// the element box). CSS blur-radius ≈ 2σ while SwiftUI's radius is the
/// gaussian σ, hence the ÷2. Empty layer list → identity (no modifier).
private struct GlyphShadows: ViewModifier {
    /// CSS-ordered shadow layers bridged from TypographyAggregate.
    let layers: [TextShadowLayer]
    func body(content: Content) -> some View {
        // Fold the layers left-to-right; later layers wrap the already-
        // shadowed view which visually approximates CSS's painted-behind
        // stacking for the small offsets the fixtures use.
        layers.reduce(AnyView(content)) { acc, l in
            AnyView(acc.shadow(color: l.color ?? .black.opacity(0.5),
                               radius: l.radius / 2, x: l.x, y: l.y))
        }
    }
}

// MARK: - Alignment bridges

private extension LayoutConfig.Align {
    var verticalAlignment: VerticalAlignment {
        switch self {
        case .flexStart: return .top
        case .flexEnd:   return .bottom
        case .center:    return .center
        case .baseline:  return .firstTextBaseline
        // css-flexbox-1 §8.3: `stretch` only stretches items whose cross
        // size is `auto`; items with a definite cross size are aligned
        // as `flex-start`. Our items are intrinsically sized (fit-content
        // harness parity), so the visible behaviour of the default
        // `align-items: stretch` is TOP alignment — web/Android agree;
        // the old `.center` mapping floated shorter items mid-row.
        case .stretch:   return .top
        }
    }

    var horizontalAlignment: HorizontalAlignment {
        switch self {
        case .flexStart: return .leading
        case .flexEnd:   return .trailing
        case .center:    return .center
        case .baseline:  return .leading
        case .stretch:   return .leading
        }
    }
}

// MARK: - Color luminance helper

private extension Color {
    /// Reasonable sRGB component extraction via UIKit bridge. Fails for
    /// named / pattern colors but all our IR colors go through
    /// Color(.sRGB, ...) so this works for us.
    var rgbComponents: (r: Double, g: Double, b: Double, a: Double)? {
        let ui = UIColor(self)
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        guard ui.getRed(&r, green: &g, blue: &b, alpha: &a) else { return nil }
        return (Double(r), Double(g), Double(b), Double(a))
    }
}

// MARK: - Synthetic oblique (wave 6, lane FONT-STYLE-OBLIQUE)

/// Blink/Android-parity synthetic oblique. Neither SwiftUI's `.italic()`
/// nor `.traitItalic` can slant the bundled Inter (the family ships no
/// italic face, so the symbolic-trait lookup fails and silently no-ops —
/// the wave-6 iOS captures rendered plain `italic` fully upright). Skew
/// the roman outlines with the fixed 0.25 slope both reference platforms
/// synthesize instead (Skia textSkewX -0.25 on Chromium and Android;
/// atan(0.25) ≈ 14.04deg — css-fonts-4 §2.5's default oblique angle).
/// UIKit glyph space is y-up, so a POSITIVE `c` shear maps
/// x' = x + 0.25·y and leans glyph TOPS to the RIGHT, matching the
/// wave-6 web capture's lean direction. Internal (not private) so
/// TypographyTests can pin the matrix via @testable import.
func syntheticObliqueUIFont(_ f: UIFont) -> UIFont {
    // Pure horizontal shear — glyph advances are untouched, so measurement
    // code sharing this font stays line-break-identical to the roman.
    let shear = CGAffineTransform(a: 1, b: 0, c: 0.25, d: 1, tx: 0, ty: 0)
    // Rebuild by PostScript name + matrix; UIFont(descriptor:size:)
    // reapplies the point size on top of the sheared descriptor.
    let desc = UIFontDescriptor(name: f.fontName, matrix: shear)
    return UIFont(descriptor: desc, size: f.pointSize)
}
