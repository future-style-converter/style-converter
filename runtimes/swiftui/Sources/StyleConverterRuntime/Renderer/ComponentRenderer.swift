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
    // Wave 26 (lane RES residual 3a) — the id of the ONE component whose
    // §8.3.1 hoist band the HOST owns (the composed root stack folds it into
    // its gaps instead). nil everywhere else; see HoistBandSuppressedForKey.
    @Environment(\.hoistBandSuppressedFor) private var hoistBandSuppressedFor

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

    // Wave 38 (lane N1) — "my subtree is packed by the inline atom flow"
    // (InlineAtomBlockLayout.swift). The run packer already gives each atom
    // its §10.8 line box and row-baseline slot, so the UA-widget mount hook
    // must NOT add the block-context lead there; false (the default) means
    // a lone, block-stacked widget that does need it.
    @Environment(\.inlineAtomRunMember) private var inlineAtomRunMember

    // Wave 34 (lane T) — the enclosing TABLE box's used `border-spacing`
    // (CSS 2.1 §17.6.1). Declared on the table, but SPENT by the row it
    // encloses (between its cells) and by the table itself (its outer
    // band + between its rows), so it travels one level down as ambient
    // state. Nil outside any table. See TableSeparatedLayout.swift.
    @Environment(\.tableBorderSpacing) private var tableBorderSpacing

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
        // Wave 18 (RC6 companion) — the `all: <global>` sole-override
        // drop, ported from Compose (ComponentRenderer.kt's allReset) so
        // reset semantics match across natives: when `All` survives to
        // the merged list, EVERY other declaration (inherited entries
        // included — the merge runs first, exactly like Compose's
        // rawProperties ordering) drops, collapsing the element to its
        // untouched defaults — the observable web behaviour the audit
        // fixtures pinned. iOS previously extracted All into an inert
        // GlobalConfig, so `all:initial` boxes kept painting their other
        // declarations. Note: an unboxed `display:contents` element never
        // reaches here with All (the RC6 strip removes non-inherited
        // declarations first), matching Compose's evaluation order.
        //
        // Wave 25 (lane LF follow-up) — the UA `list-style-type` rule runs
        // HERE, the last point that still holds the element's OWN list and
        // the inherited channel separately. On a list container with no
        // own declaration, `ul { list-style-type: disc }` / `ol { … decimal }`
        // (HTML §15.3.9) is a declaration ON the element, so it beats any
        // ancestor value — css-cascade-4 §4.3 consults inheritance only
        // when the cascade produced nothing. Returns its input unchanged
        // for every non-container and every own-declaring container, so no
        // committed capture moves. See ListStyleUaRule for the full
        // argument and the nested-list KNOWN GAP.
        // Hoisted: the UA rule needs the element's OWN list, which the
        // merge below also consumes — evaluating the transition blend
        // twice would be pure waste.
        let ownProperties = motionEffectiveProperties(now: now)
        return ListStyleUaRule.apply(
            sourceTag: component.meta?.sourceTag,
            own: ownProperties,
            merged: GlobalExtractor.applyingAllReset(to: InheritedText.merge(
                own: InheritedText.resolvingCurrentColorOnColor(ownProperties),
                inherited: inheritedTextProperties)))
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
    ///
    /// Wave 23 — this constant is now the SIXTEEN-PIXEL SPECIALIZATION of
    /// [wptRefLineHeightRatio] (20 = 16 × 1.25), kept because every other
    /// consumer of it (FloatRowPacking's `<br clear>` strip,
    /// UAWidgetIntrinsics' control metrics) is anchored at the ref root font.
    /// Text runs read the ratio instead — see `effectiveLineHeight`.
    public static let wptRefLineBoxPx: CGFloat = 20

    /// The ref injection's UNITLESS `line-height` (capture-browser-ref.mjs
    /// REF_LINE_HEIGHT / apps/web-harness/index.html `:where(body)`), i.e. the
    /// ratio [wptRefLineBoxPx] was derived from: 20 = 16 × 1.25.
    ///
    /// Wave 23 (lane DECOR-INSET) — why the RATIO, not the px, is the pin.
    /// A unitless `line-height` recomputes against EACH element's own
    /// font-size (css-inline-3 §2.2 / CSS 2.1 §10.8), so the ref's `<h1>` at
    /// `font-size: 32px` lays out on a 40px line box, not a 20px one. Both
    /// byte-parallel twins already scale:
    ///   • web    — `lineHeight: irLineHeight ?? '1.25'` on the placeholder
    ///     span (apps/web-harness/src/sdui/ComponentRenderer.tsx), a unitless
    ///     number the browser resolves per element;
    ///   • Compose — `composedDefaultLineHeightPx(composedWpt, fontSizePx) =
    ///     fontSizePx * REF_DEFAULT_FONT_LINE_HEIGHT_RATIO` (core/renderer/
    ///     WptCaptureMode.kt).
    /// iOS alone pinned the ABSOLUTE 20px, which is only correct at the 16px
    /// root it was calibrated at. Every non-16px run therefore got a line box
    /// that was too SHORT, and the sub-natural placement model
    /// (LineBoxMetrics.subNaturalOffset) translated its glyph run — plus the
    /// decoration overlay riding along on the same `.offset` — UP by the
    /// signed half-leading `(L − contentHeight)/2`. Measured on the
    /// wave22-final css-text-decor captures (390×600, per-test-ir font sizes):
    ///   • text-decoration-inset-001/002 (`<h1>` @32px): iOS ink+underline band
    ///     sat 7px above web/Android (iOS underline rows 135-137 vs web 142-144,
    ///     Android 142-143) → iOS-web 0.94 while Android-web scored 0.98;
    ///   • text-decoration-color-recalc (@50px): iOS band 99-136 vs web 119-156
    ///     — 20px higher, the same defect scaled by the same rule.
    /// A corpus sweep of all 180 wave22-final per-test-ir documents finds
    /// exactly 4 tests carrying text with a declared font-size ≠ 16 and no
    /// line-height — the two inset tests, color-recalc, and
    /// line-through-vertical — i.e. precisely the four css-text-decor rows
    /// where iOS trails a passing Android-web pair. Nothing else in the corpus
    /// can move: at 16px this ratio reproduces [wptRefLineBoxPx] exactly.
    public static let wptRefLineHeightRatio: CGFloat = wptRefLineBoxPx / 16
    // (= 1.25 exactly — derived from the pinned 20pt box at the 16px root so
    // the two constants can never drift apart silently; wave-23 drift pin in
    // tools/titan/wpt-white-canvas.test.mjs asserts this derivation.)

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
    ///
    /// Wave 22 (lane FONT) adds the `declaredNormal` third state, WPT-GATED: an
    /// IR that explicitly declares `line-height: normal` — which css-fonts-4
    /// §4.3 makes `font: 92px Arial` reset to, now emitted by the converter's
    /// FontExpander.kt — resolves to the FACE's own metrics under WPT capture.
    /// Chromium renders that div at Arial's natural 1.1499em box (≈105.8px at
    /// 92px, hhea asc 1854 + desc 434 + gap 67 over a 2048 upem) because a
    /// directly-matching declaration beats the ref injection's inherited
    /// `:where(body){line-height:1.25}`; pinning `wptRefLineBoxPx` there also
    /// CLAMPED the single-line box (the maxHeight cap in PlaceholderLabel) to
    /// 20pt for a 92pt run. Outside WPT capture the keyword keeps the
    /// extractor's historical 1.2× number so the committed baselines never move
    /// — see LineHeightNormal.lineBoxSource for the table and the stated risk.
    /// ABSENT line-height is untouched in BOTH modes.
    ///
    /// Wave 23 (lane DECOR-INSET) — `fontSizePx` is what the CALIBRATED row
    /// multiplies by [wptRefLineHeightRatio]; it defaults to 16 so every
    /// legacy call site (and the WPTCaptureModeTests pin that reads
    /// `wptRefLineBoxPx` back out of it) keeps the exact 20pt it always
    /// returned. See [wptRefLineHeightRatio] for why a unitless ratio, not an
    /// absolute px, is the twin-parity pin.
    public static func effectiveLineHeight(declared: CGFloat?,
                                           declaredNormal: Bool = false,
                                           fontSizePx: CGFloat = 16,
                                           wptCaptureMode: Bool) -> CGFloat? {
        // The three-state pick is delegated to the shared native decision so
        // Compose's placeholder `when` and this function can never diverge.
        switch LineHeightNormal.lineBoxSource(hasDeclaredValue: declared != nil,
                                              declaredNormal: declaredNormal,
                                              wptCapture: wptCaptureMode) {
        // IR-declared line-height always wins (author > our calibration).
        // Force-unwrap is safe by construction: `.declared` is returned only
        // when `declared != nil` was passed as the predicate above.
        case .declared:
            return declared!
        // Declared `normal` under WPT capture: nil = SwiftUI's natural metrics,
        // i.e. the same font-metric lookup the browser reference performs.
        case .natural:
            return nil
        // Bare text: pin the ref line box only under WPT capture; otherwise
        // nil = SwiftUI's natural metrics (unchanged product behaviour).
        // The ref line box is the ratio × THIS run's font-size — the ref's
        // unitless 1.25 recomputes per element, exactly as web's inline
        // `lineHeight: '1.25'` and Compose's composedDefaultLineHeightPx do
        // (wave 23; identical to the old constant at the 16px default).
        case .calibrated:
            return wptCaptureMode ? fontSizePx * wptRefLineHeightRatio : nil
        }
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
    // Wave 18 (RC6) — `display: contents` unboxing happens HERE, at the
    // single entry every render path shares: an unboxable `contents`
    // component strips to an undecorated pass-through (no box to paint,
    // css-display-3 §2.5) and every unboxable `contents` CHILD is spliced
    // out of the children list, so all downstream collection paths
    // (block flow ForEach, positioned overlay, flex, grid item building)
    // see the grandchildren as direct children — the
    // display-contents-alignment-002 grid-item fix. Identity for the
    // whole contents-free corpus (ContentsUnboxing.resolve returns the
    // input untouched), keeping every committed baseline byte-stable.
    public init(component: IRComponent) {
        self.component = ContentsUnboxing.resolve(component)
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
            // Wave 18 (RC2) — abspos INSET-STRETCH sizing (css-position-3
            // §3.5): an out-of-flow axis with both opposing insets and no
            // author size sizes to cb − insets, then aspect-ratio resolves
            // inline-first (the css-sizing abspos-003/004 fix — the ideal-
            // size measure otherwise collapses an empty stretched box to
            // 0×0 and the green square never paints). The containing block
            // arrives on the SAME channels the wave-9 percent lanes use:
            // the positioned-children loop publishes the ancestor's §3.1
            // padding box, the hoist overlay the canvas. WPT-capture gated
            // (mirror of the Compose call site): the whole dark-stage /
            // 327-pair baseline path keeps today's chain byte-identical,
            // and resolveFor is additionally the identity whenever nothing
            // stretches (the S4 guard) — explicit-size boxes never move.
            if wptCaptureMode, Self.isOutOfFlow(component) {
                // Insets via the strict {"px":N}-only reader (skeptic
                // fix): the live converter emits percent insets as BARE
                // numbers, which the permissive layout7 lane reads as px
                // for the offset applier — honest to OFFSET with (the
                // pre-existing approximation) but not to SIZE with, so
                // the stretch math refuses them (pin S1's conservatism).
                let stretch = AbsposInsetStretch.resolveFor(
                    size: s.size,
                    inset: AbsposInsetStretch.strictInsets(from: component.properties),
                    cbW: containingBlockWidth.map(Double.init),
                    cbH: containingBlockHeight.map(Double.init),
                    // Wave 31 (lane T) — S5's css-tables-3 available-space
                    // ceiling. Table-only, so every non-table abspos
                    // stretch keeps the wave-18 result byte-identically.
                    // `meta.sourceTag` supplies the UA display: the live
                    // absolute-tables-008…011 IRs carry `<table>` with NO
                    // Display property, so the declared-keyword channel
                    // alone never sees them.
                    isTable: AbsposInsetStretch.isTableBox(
                        from: component.properties,
                        tag: component.meta?.sourceTag))
                // Fill ONLY the still-auto axes — resolve() never returns
                // a value for an author-sized axis, but the nil-guard here
                // keeps the invariant local and obvious.
                if let w = stretch.widthPx, s.size.width == nil {
                    s.size.width = .exact(px: w)
                }
                if let h = stretch.heightPx, s.size.height == nil {
                    s.size.height = .exact(px: h)
                }
                // Wave 31 (lane T) — CSS 2.1 §10.3.7 / §10.6.4 AUTO-MARGIN
                // resolution, one step after the stretch so the used size
                // it reads is final (the spec order too). Solves the auto
                // margins arithmetically and folds the START margin into
                // the start inset, so anchoredInsetOffset paints the used
                // position; the auto margins are then zeroed so
                // MarginApplier's frame-expanding H/VAutoFrameModifier —
                // which can only ever produce the symmetric,
                // inset-ignoring answer — cannot re-apply the same rule.
                // Identity (Fold.none) for every box whose split is 0/0 or
                // whose axis has an auto inset/size, which is the entire
                // corpus except css-tables/absolute-tables-016.
                let autoMargin = AbsposAutoMargin.resolveFor(
                    margin: s.spacing.margin,
                    size: s.size,
                    inset: AbsposInsetStretch.strictInsets(from: component.properties),
                    cbW: containingBlockWidth.map(Double.init),
                    cbH: containingBlockHeight.map(Double.init))
                // The `s.layout7 != nil` guard is not defensive noise: the
                // aggregate is where `position: absolute` itself lives, so
                // a nil one on a box the isOutOfFlow gate accepted would
                // mean the two readers disagree — synthesising an empty
                // aggregate here would silently DEMOTE the box to static.
                // Nil therefore degrades to the pre-wave-31 render, which
                // is the honest answer (and unreachable in the corpus).
                if !autoMargin.isNone, s.layout7 != nil {
                    // The inset write-back rides layout7 — the same
                    // InsetRect PositionApplier reads for the anchored
                    // offset. A nil axis is left exactly as extracted.
                    var rect = s.layout7?.inset ?? InsetRect()
                    if let l = autoMargin.leftPx { rect.left = l }
                    if let t = autoMargin.topPx { rect.top = t }
                    s.layout7?.inset = rect
                    // Zero the solved margins, PER SIDE. Deliberately ZERO
                    // rather than the solved values: a margin renders as a
                    // real outer `.padding` band here, so writing the
                    // solved 30px would grow the footprint on top of the
                    // inset fold that already carries the whole
                    // displacement — an out-of-flow box's used position
                    // depends only on the START margin, now inside `rect`.
                    //
                    // Only sides that were genuinely `auto` are cleared: a
                    // DECLARED margin keeps its value so it still paints
                    // its own band (skeptic bug 2 — clearing it moved
                    // `margin-left:10%; margin-right:auto` to x=0 where
                    // Chromium paints x=16 with margins `16px 44px`).
                    if autoMargin.clearsLeftMargin { s.spacing.margin?.left = .exact(px: 0) }
                    if autoMargin.clearsRightMargin { s.spacing.margin?.right = .exact(px: 0) }
                    if autoMargin.clearsTopMargin { s.spacing.margin?.top = .exact(px: 0) }
                    if autoMargin.clearsBottomMargin { s.spacing.margin?.bottom = .exact(px: 0) }
                }
            }
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
            // Wave-20 fix 3 — inline-ATOM guard: UA form controls carry
            // no Display property on the wire (their inline-block UA
            // default is unmodeled), so the `.inline` guard below never
            // fired for them and every width-auto widget child was
            // stretched to the containing block (appearance-auto-001:
            // sixteen 500px atoms → InlineAtomBlockLayout wrapped EVERY
            // measured-width atom onto its own row — the 0.818→0.729
            // css-ui regression). CSS 2.1 §10.3.9: an inline-block with
            // width:auto SHRINKS TO FIT, it never fills — so any child
            // InlineAtomFlow.isAtom recognizes (the same predicate that
            // routes it into the atom rows) skips the fold and keeps its
            // UAWidgetIntrinsics/measured size. Non-widget children and
            // the whole non-WPT flow are untouched.
            // Wave-38 lane N7 — RATIO guard (css-sizing-4 §4.1): a block
            // box with a preferred aspect ratio and a DEFINITE block size
            // does not take the §10.3.3 stretch-fit inline size; its
            // automatic width IS blockSize × ratio, which SizeApplierMath
            // already synthesises for the still-missing axis. Filling the
            // width here pre-empted that fill, so nine frozen wave37-final
            // cells (css-sizing block-aspect-ratio-002/006/007/014/015/
            // 016/018, css-values calc-size-aspect-ratio-001/004) painted
            // a full-canvas green bar where web, Android and the Chromium
            // ref all paint the ratio-derived square. The predicate is
            // deliberately narrow (usable ratio + auto width + `.exact`
            // height) so every other block box keeps the fill verbatim —
            // see RatioInlineSize.determinesInlineSize.
            // Wave-38 lane N2 — TABLE guard (CSS 2.1 §17.5.2): a table box
            // with `width: auto` is SHRINK-TO-FIT (the table layout
            // algorithm's max(min-content, min(max-content, available))),
            // not a §10.3.3 block-level fill. css-tables/background-clip-001
            // is one `<td>` with a 40×40 inline-block inside 30px collapsed
            // borders — a 100×100 table, 10 000 green ref pixels — and the
            // fill made iOS paint 35 800 (a 358×100 bar). box-shadow-001 and
            // extra-height-given-to-all-row-groups-00{1,2,5} are the same
            // three ink counts. See TableBoxTree.shrinkToFitBox.
            if wptCaptureMode, let w = wptBlockFlowFillWidth,
               s.size.width == nil, !Self.isOutOfFlow(component),
               s.layout.display != .inline,
               !Self.isInlineAtom(component),
               !RatioInlineSize.determinesInlineSize(s.size),
               !TableBoxTree.shrinkToFitBox(
                    TableBoxTree.roleOf(displayProperties(now: now),
                                        sourceTag: component.meta?.sourceTag)) {
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
            // Lane UAM (BD-RC3): in WPT capture the plan ALSO folds each
            // child's UA default block margins (the browser-ref renders
            // with its UA sheet intact — a `<p>` child keeps its 1em).
            // wptCaptureMode is false on every property-fixture path, so
            // the 327 dark-stage baselines take the identity branch and
            // stay byte-identical.
            let hoistPlan = MarginCollapse.containerPlan(component: component, style: style,
                                                         uaBlockMargins: wptCaptureMode)
            // Wave 26 (lane RES residual 3a): a composed ROOT's band is owned
            // by the harness's root-stack fold, which folds this exact number
            // into the same §8.3.1 max() as the root's own margin. Emitting it
            // here too would ADD where the browser takes ONE max — see
            // HoistBandSuppressedForKey for the worked example and why
            // subtraction cannot express it. The channel names one id and the
            // extractor's ids are hierarchical, so a nested container never
            // matches and keeps its band byte-identically.
            let bandSuppressed = MarginCollapse.suppressesHoistBand(
                suppressedForId: hoistBandSuppressedFor, componentId: component.id)
            let bandTop = bandSuppressed ? 0 : (hoistPlan?.hoistTop ?? 0)
            let bandBottom = bandSuppressed ? 0 : (hoistPlan?.hoistBottom ?? 0)
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

    /// Wave 24 (lane GAPS-I) — this component's css-gaps-1 gap
    /// decorations, or nil when it declares none of the family. Read by
    /// the flex container branches below (the painter) and by the child
    /// loop (the per-item anchor gate).
    private var gapDecorationsConfig: GapDecorationsConfig? {
        GapDecorationsExtractor.extract(from: component.properties)
    }

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
        // Phase 7 step 2: route flex containers through the wrap
        // FlowLayout when `flex-wrap: wrap|wrap-reverse` is set.
        if let kind = gridKind {
            // Grid path — LazyVGrid / LazyHGrid / iOS 16 Grid.
            gridContainer(kind: kind, style: style, gap: gap)
        } else if let layoutAgg = style.layout7,
           layoutAgg.display == .flex,
           layoutAgg.flexWrap == .wrap || layoutAgg.flexWrap == .wrapReverse {
            FlowLayout(
                horizontalSpacing: gap.column,
                verticalSpacing: gap.row,
                // Wave 25 (CAL-RC5): the cross-axis inputs §8.4/§9.6 need.
                // `alignItems` nil is the CSS initial `normal` → stretch,
                // so the wrap path finally sizes a width-only item to its
                // line (flex-gap-decorations-001/002 rendered empty).
                alignItems: layoutAgg.alignItems,
                // Only an explicit CSS cross size creates leftover space
                // for align-content to distribute — the same definiteness
                // test CSSFlexLayout applies on the nowrap path.
                definiteCross: style.size.height != nil,
                // …and §9.6 distributes only under normal/stretch. Nil
                // (undeclared) is the initial `normal`, so the default
                // path is unchanged; a declared center/space-* keyword
                // now leaves the lines at their hypothetical cross size,
                // matching the Compose lane's FlexWrapLines gate.
                alignContent: layoutAgg.alignContent
            ) {
                contentOrPlaceholder(style: style)
            }
            // Wave 24 (lane GAPS-I): the gap-decorations painter. This
            // is the WRAP flex path — the only one that can produce the
            // multi-line geometry the css-gaps flex tests exercise. The
            // modifier is a no-op (returns the container unchanged) for
            // every container without an active rule, which is all of
            // the committed corpus.
            .gapDecorations(gapDecorationsConfig,
                            mainHorizontal: !(layoutAgg.flexDirection == .column
                                                || layoutAgg.flexDirection == .columnReverse))
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
                // Wave 24 (lane GAPS-I): the NOWRAP row path still has
                // item gaps to decorate — flex-gap-decorations-008 paints
                // five column rules on a single overflowing line.
                .gapDecorations(gapDecorationsConfig, mainHorizontal: true)
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
                // Wave 24 (lane GAPS-I): column-direction twin. Here the
                // ITEM gaps are the ROW gaps, so the row-rule-* family
                // paints between items and column-rule-* between lines —
                // the swap lives in GapDecorationSegments.build.
                .gapDecorations(gapDecorationsConfig, mainHorizontal: false)
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
            // Lane ios-multichild-multicol — Android parity: Compose
            // flips a block container carrying ColumnCount/ColumnWidth to
            // DisplayType.MULTI_COLUMN and distributes ALL its children
            // greedily across the used columns
            // (MultiColumnDistributionLayout). Route the multi-child
            // family through the mirroring MulticolGreedyLayout; the
            // single-child family keeps the vertical stack below, where
            // the wave-9 column fill basis and the wave-10 fragmentation
            // pass already own its parity (byte-identical under the gate).
            if let track = tableTrackPlan() {
                // Wave-34 lane T (T1) — CSS 2.1 §17.6.1 separated-borders
                // TRACK placement, composed-WPT capture ONLY (the gate
                // lives in tableTrackPlan): a declared `table` /
                // `table-row-group` / `table-row` box places its children
                // on the §17.6.1 tracks instead of stacking them in the
                // VStack. The row is the one that matters — its cells run
                // in the INLINE direction, and iOS stacked them
                // vertically, which is why abspos-container-change-
                // dynamic-001's lime abspos landed at (16,38) instead of
                // the reference's (33,18). The dark-stage 327 corpus never
                // sets wptCaptureMode and declares no table display at
                // all, so its block containers keep the VStack byte-
                // identically.
                TableSeparatedLayout(
                    axis: track.axis,
                    spacing: track.spacing,
                    bandWidth: track.bandWidth,
                    bandHeight: track.bandHeight
                ) {
                    // Same content pass as every other container — the
                    // sorted in-flow children become the layout's
                    // subviews IN ORDER (abspos children ride the
                    // overlay, not this).
                    contentOrPlaceholder(style: style)
                }
                // The table box publishes its used spacing for the rows
                // below it; a row / row group publishes nothing and lets
                // its table's value flow through untouched.
                .environment(\.tableBorderSpacing, track.publish ?? tableBorderSpacing)
            } else if multicolDistributes(style: style) {
                MulticolGreedyLayout(
                    // The §3 inputs, straight from the typed config — the
                    // same fields MulticolMath consumes everywhere else.
                    requestedCount: style.columns?.count,
                    requestedWidthPx: style.columns?.widthPx,
                    // The used gap through the single shared resolver, so
                    // slots, fill basis and fragment plan agree.
                    gapPx: multicolUsedGapPx(style: style),
                    // Wave-21 wiring hook (lane MULTICOL): per-subview
                    // spanner-flow roles in contentOrPlaceholder order
                    // (leading text first) — capture only; nil keeps the
                    // dark-stage greedy layout byte-identical.
                    roles: wptCaptureMode
                        ? MulticolSpannerFlow.rolesFor(
                            children: FlexboxApplier.sorted(inFlowChildren),
                            leadingText: component.text?.isEmpty == false)
                        : nil
                ) {
                    // Same content pass as every container: leading text
                    // (if any) and the sorted in-flow children become the
                    // layout's subviews IN ORDER — exactly the measurables
                    // Android's RenderContent hands its distribution
                    // layout (leading text included).
                    contentOrPlaceholder(style: style)
                }
            } else if let floatSegments = blockFloatSegments() {
                // Wave-19 lane FLOAT — CSS 2.1 §9.5 float row packing,
                // composed-WPT capture ONLY (pin P8; the gate lives in
                // blockFloatSegments): consecutive left-floating block
                // siblings pack side-by-side instead of stacking in the
                // VStack (descendant-static-position-001's green+grey
                // pair rendered grey BELOW green here; justify-self-001
                // collapsed its 3/2/5/4 ref rows to a single column).
                // The dark-stage 327 corpus never sets wptCaptureMode,
                // so its block containers keep the VStack byte-identically.
                FloatBlockLayout(
                    // The pure segmentation over the SAME sorted child
                    // array contentOrPlaceholder renders — subview
                    // indices line up by construction (pins P1/P2).
                    segments: floatSegments,
                    // Mixed-content text renders as a leading subview
                    // BEFORE the children (contentOrPlaceholder's Bug-1
                    // branch) — the layout stacks it first.
                    leadingCount: (component.text?.isEmpty == false) ? 1 : 0,
                    // The VStack's spacing twin for the stacked items.
                    spacing: gap.row
                ) {
                    // Same content pass as every container — the sorted
                    // in-flow children become the layout's subviews in
                    // order (abspos children ride the overlay, not this).
                    contentOrPlaceholder(style: style)
                }
            } else if let inlineSegments = blockInlineAtomSegments() {
                // Wave-20 lane W3 — CSS 2.1 §9.4.2 inline atom flow,
                // composed-WPT capture ONLY (pin P19; the gate lives in
                // blockInlineAtomSegments): consecutive inline-level UA
                // widgets / text-only anchors pack into wrapped rows
                // (css-ui appearance-auto-001's sixteen controls in a
                // 500px container) instead of stacking in the VStack.
                // The dark-stage 327 corpus never sets wptCaptureMode,
                // so its block containers keep the VStack byte-
                // identically.
                InlineAtomBlockLayout(
                    // The pure segmentation over the SAME sorted child
                    // array contentOrPlaceholder renders — subview
                    // indices line up by construction (pins P15).
                    segments: inlineSegments,
                    // Per-child atom specs from the SHARED UA geometry
                    // table (UAWidgetIntrinsics — the W2/W3 coordination
                    // point), index-aligned with the sorted children.
                    // Wave-33 C2 — a DECLARED inline-block carries its
                    // geometry on the wire, so its spec comes from the same
                    // predicate that admitted it to the run; everything
                    // else resolves through the UA table as before.
                    specs: FlexboxApplier.sorted(inFlowChildren).map {
                        Self.inlineBlockAtomSpec($0, container: component.properties)
                            ?? UAWidgetIntrinsics.spec(Self.atomKindOf($0))
                    },
                    // Mixed-content text renders as a leading subview
                    // BEFORE the children — the layout stacks it first.
                    leadingCount: (component.text?.isEmpty == false) ? 1 : 0,
                    // The VStack's spacing twin for the stacked items.
                    spacing: gap.row
                ) {
                    // Atoms are OPAQUE: their content renders through the
                    // normal chain (W2's widget painting mounts inside) —
                    // this lane owns only their placement.
                    //
                    // Wave-38 lane N1: mark the subtree so the UA-widget
                    // mount hook skips the BLOCK line box (this layout
                    // already builds the run's line box — see
                    // `inlineAtomRunMember`). `.environment` is an inert
                    // modifier, so the Layout's subview list is unchanged.
                    contentOrPlaceholder(style: style)
                        .environment(\.inlineAtomRunMember, true)
                }
            } else {
                VStack(
                    alignment: .leading,
                    spacing: gap.row
                ) {
                    // Wave-32 lane R: the PLAIN block stack is the one
                    // container whose subviews nothing indexes — the three
                    // custom Layouts above (float rows, inline atoms, and the
                    // multicol/flex branches) align their measurables to the
                    // sorted child array plus `leadingCount`, so an extra
                    // anonymous-run subview would shift every one of them.
                    // Interleaving is therefore requested HERE and only here;
                    // every other branch keeps the pre-wave-32 content pass
                    // (leading text, then children) byte-for-byte. See
                    // InlineRunPlan's "HONEST SCOPE" note.
                    contentOrPlaceholder(style: style, interleaveRuns: true)
                }
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

    /// Wave 33 (lane C) — the §3.1 PADDING-box height of an AUTO-height
    /// positioned ancestor, for its abspos children only.
    ///
    /// `ContainingBlockBasis.paddingBox(vertical: true)` answers nil when
    /// this box's own `height` is indefinite, because that basis is built
    /// from the DECLARED size. CSS 2.2 §10.6.4 nonetheless resolves an
    /// abspos percentage height against the containing block's USED
    /// height, and §10.5's degradation to `auto` exempts absolutely
    /// positioned boxes by its own wording. `AbsposCbUsedHeight`
    /// evaluates §10.6.3 statically over the in-flow children and refuses
    /// (nil) everything it cannot know — so this is a pure ADDITION to
    /// what resolves, never an override.
    ///
    /// The `+ paddingBand` converts §10.6.3's CONTENT height into the
    /// §3.1 padding box the caller's channel is denominated in, through
    /// the SAME resolver `paddingBox` subtracts with, so the basis and the
    /// wave-8 overlay anchor cannot drift apart.
    ///
    /// WPT-capture gated, matching the AbsposInsetStretch / AbsposAutoMargin
    /// precedent: every dark-stage baseline render stays byte-identical.
    private func absposUsedPaddingBoxHeight(style: ComponentStyle) -> CGFloat? {
        guard wptCaptureMode else { return nil }
        guard let contentH = AbsposCbUsedHeight.contentHeightPx(
            ancestor: resolvedProperties,
            // meta.sourceTag is the only sighting of a bare `<table>` —
            // the converter never serializes UA defaults (H3's tag lane).
            ancestorTag: component.meta?.sourceTag,
            // Own text / inline runs make the content height a line-box
            // question no static rule can answer (H4).
            ancestorHasOwnContent: !(component.text ?? "").isEmpty
                || !(component.meta?.runs ?? []).isEmpty,
            children: (component.children ?? []).map(\.properties)
        ) else { return nil }
        return CGFloat(contentH) + ContainingBlockBasis.paddingBand(style: style, vertical: true)
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
        // Wave 33 (lane C) — when the DECLARED channel above has nothing
        // (this ancestor's `height` is auto), fall back to its USED
        // content height under CSS 2.2 §10.6.3 plus its own padding band,
        // i.e. the §3.1 PADDING box the declared lane would have
        // published. §10.5's percent-height degradation exempts
        // absolutely positioned boxes by its own wording, and §10.6.4
        // resolves their percentage against the used height — which is
        // knowable here because the ancestor's in-flow content is
        // measured before its out-of-flow descendants are placed. The
        // declared lane always wins (this is `??`, never an override),
        // and the rule refuses everything it cannot evaluate statically,
        // so the only corpus box it moves is absolute-tables-007's green
        // table. See AbsposCbUsedHeight's H1–H7 pin table.
        let childCBH = ContainingBlockBasis.paddingBox(style: style, vertical: true)
            ?? absposUsedPaddingBoxHeight(style: style)
        // Lane FLEX-SAFE — when THIS positioned ancestor is a flex
        // container, an inset-less abspos child sits at its STATIC
        // POSITION: the sole-flex-item hypothetical (css-flexbox-1
        // §4.1), so align-self (incl. the safe/unsafe overflow keywords
        // riding the Generic wire) shifts the child off the overlay's
        // top-leading anchor. Nil for every non-flex ancestor — their
        // children keep the block-flow anchor.
        let parentFlexDirection: FlexDirectionKeyword? =
            style.layout7?.display == .flex
                ? (style.layout7?.flexDirection ?? .row)  // CSS initial: row
                : nil
        ForEach(Array(children.enumerated()), id: \.offset) { _, child in
            // Static-position shift — computed per child (the safe
            // fallback depends on the CHILD's own size). Wave 19 (lane
            // FLEX): WPT capture routes through the FULL physical
            // resolver (AbsposStaticPosition — both axes, *_REVERSE +
            // writing modes honored, RC-A6 painted-frame extents); the
            // dark-stage corpus keeps the wave-18 cross-only shift
            // byte-identically. Zero for non-flex ancestors either way.
            let staticShift: CGSize = parentFlexDirection == nil
                ? .zero
                : (wptCaptureMode
                    // Full resolver: raw container wire (the resolved
                    // declarations — same list the flex container reads)
                    // + the §3.1 padding-box channels already computed.
                    ? AbsposStaticPosition.staticOffset(
                        containerProperties: resolvedProperties,
                        childProperties: child.properties,
                        containerW: childCB,
                        containerH: childCBH,
                        wptCaptureMode: true)
                    // Wave-18 machinery, byte-identical for dark stage.
                    : AbsposStaticAlignment.staticCrossOffset(
                        flexDirection: parentFlexDirection,
                        childProperties: child.properties,
                        containerW: childCB,
                        containerH: childCBH))
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
            // Wave-21 wiring hook (lane MULTICOL, B-RC4b) — when THIS
            // ancestor is a MULTICOL container, an inset-less abspos
            // following a column-span:all sibling anchors at its
            // POST-SPANNER static position (css-position-3 §3.1 inside
            // the css-multicol-1 §6 flow): the pure module resolves the
            // shared SP-table slot from statically-declared sibling
            // heights. Zero for every non-multicol ancestor, outside
            // capture, and when no spanner precedes the child — the
            // frozen overlay anchor everywhere else.
            let multicolShift = MulticolAbsposStatic.staticOffset(
                columns: style.columns,
                siblings: component.children ?? [],
                childId: child.id,
                contentWidthPx: flexContentSize(style: style, vertical: false),
                gapPx: multicolUsedGapPx(style: style),
                ctx: style.spacing.context,
                wptCaptureMode: wptCaptureMode)
            // v2: children render through ComponentHost (placement
            // parent-data attached; inert here — the overlay ZStack
            // reads no layout values).
            ComponentHost(component: child)
                // The static-position offset — applied on the HOST so
                // the child's own PositionApplier (which only runs for
                // explicit insets, gated off above) never composes
                // with it on the same axis. Flex, grid and multicol
                // shifts are mutually exclusive (display is exactly one
                // of flex/grid/block-multicol), so adding them keeps
                // exactly one lane's geometry.
                .offset(x: staticShift.width + gridShift.width + multicolShift.width,
                        y: staticShift.height + gridShift.height + multicolShift.height)
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
                definiteWidth: style.size.width != nil,
                // Wave-19 RC-A2: container justify-content — content
                // distribution of the track group (css-align-3 §5.3); the
                // aggregate keyword FlexboxExtractor already parses.
                // Wave-19 RC-A2: direction:rtl is NOT passed — SwiftUI's
                // ambient layoutDirection environment (set by
                // TypographyApplier from the same Direction wire, inherited
                // from ancestors otherwise) mirrors the Layout's placement
                // itself; CSSGridLayout solves in logical space (see its
                // header note + SkepticRtlGridPlacementTests).
                justifyContent: agg?.justifyContent
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
                templateAreas: agg?.gridTemplateAreas,
                // Wave-19 RC-A2: same distribution input as the plain
                // track-list path above (one behavior, two entries);
                // direction:rtl rides the ambient layoutDirection
                // environment, not a Layout input (see CSSGridLayout).
                justifyContent: agg?.justifyContent
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

    // MARK: - Wrap-flex cross stretch (wave 25, lane ISTRETCH — CAL-RC5)

    /// Static §8.4 stretch plan for a WRAPPING flex row, keyed by
    /// sorted-child index: the forced cross size each stretch item's own
    /// paint chain must adopt, or nil when no item stretches / the plan
    /// is not statically knowable.
    ///
    /// WHY THE RENDERER OWNS THIS. `FlowLayout` already sizes the LINES
    /// (§9.6) and proposes each stretch item its line's cross extent —
    /// but a SwiftUI proposal is advisory, and an IR child ignores it
    /// (it answers `sizeThatFits` with its intrinsic cross for every
    /// proposal, `.infinity` included; measured through the live path in
    /// FlexWrapStretchTests). Compose has no such gap: `FlexWrapRow`
    /// measures a stretch item with a FIXED cross band,
    /// `Constraints(0, main, lineCross, lineCross)`. The iOS equivalent
    /// of that hard constraint is the FORCED cross frame this plan
    /// injects — folded into the child's SizeConfig via the
    /// `gridStretchHeight` channel (same fold the grid row-stretch and
    /// the column-flex main size use) so the child's background/border
    /// paint at the line's cross size instead of hugging 30px.
    ///
    /// The two runs are the same arithmetic by construction: both call
    /// `FlexWrapPlan.breakLines` / `.stretchLines`. This one feeds it
    /// statically-knowable IR facts; `FlowLayout` feeds it measurements.
    /// Where a fact is NOT static (content-sized item, percent cross, a
    /// padded/bordered box whose frame depends on box-sizing) the plan
    /// returns nil for the whole container — an item painted at a size
    /// the Layout does not place it at would be worse than the wave-24
    /// hug, so nothing is guessed.
    ///
    /// Returns nil for column-direction containers on purpose: FlowLayout
    /// lays out ROWS whatever `flex-direction` says (see its header), so
    /// there is no column line geometry to stretch into — injecting the
    /// inline-axis twin here would contradict the placement.
    private func flexWrapStretchPlan(style: ComponentStyle,
                                     children: [IRComponent],
                                     column: Bool) -> [Int: CGFloat]? {
        // Row-direction wrap containers only (see the doc note above).
        guard !column, !children.isEmpty, let parentAgg = style.layout7 else { return nil }
        // §9.6 fires only under `align-content: normal | stretch` — the
        // SAME gate FlowLayout applies to the line arithmetic.
        guard FlexWrapPlan.alignContentStretches(parentAgg.alignContent) else { return nil }
        // A leading text placeholder is an extra Layout subview, so the
        // index mapping would shift — bail honestly (same rule as
        // flexMainPlan).
        guard component.text?.isEmpty != false else { return nil }
        // Both axes must be definite: the main axis to break lines
        // against, the cross axis to have leftover space at all. The
        // cross test mirrors the `definiteCross:` argument the container
        // hands FlowLayout, so the two agree on when §9.6 applies.
        guard style.size.height != nil,
              let mainAvail = flexContentSize(style: style, vertical: false),
              let crossAvail = flexContentSize(style: style, vertical: true)
        else { return nil }
        let ctx = style.spacing.context
        // Gaps through the container's own resolver — main is
        // `column-gap` for a row container, cross is `row-gap`.
        let gaps = GapApplier.resolve(style.spacing.gap, context: ctx)
        var mains: [CGFloat] = []
        var crosses: [CGFloat] = []
        var stretchy: [Bool] = []
        for child in children {
            let cs = SizeExtractor.extract(from: child.properties)
            // Flex factors — `flex-basis: <length>` wins over `width`
            // on the main axis (css-flexbox-1 §9.2.3.A).
            var agg = LayoutAggregate()
            FlexboxExtractor.extract(from: child.properties, into: &agg)
            // SKEPTIC (wave 25) — the plan's per-child index must address
            // the SAME box FlowLayout places, and two display values break
            // that 1:1 map: `none` contributes no Layout subview at all
            // (ComponentRenderer short-circuits it), and `contents`
            // splices the child's OWN children in as siblings. Either one
            // shifts every later index and changes the item COUNT the
            // §9.3 line breaking runs over, so the injected cross size
            // would belong to a different line than the Layout's. Measured
            // symptom before this guard: three items with the middle one
            // `display:none` had the plan break 2 lines of 50 while
            // FlowLayout made ONE 110pt line — the two survivors painted
            // 50pt tall inside a 110pt band. Refuse the container instead.
            guard agg.display != DisplayKeyword.none,
                  agg.display != DisplayKeyword.contents else { return nil }
            let basisPx: CGFloat? = {
                if case .px(let p)? = agg.flexBasis { return p }
                return nil
            }()
            // Main size: percent widths resolve against THIS container's
            // content box, which is the child's containing block.
            let mainExplicit = SizeApplierResolve.exact(cs.width, ctx: ctx,
                                                        parent: mainAvail)
            // A content-derived main size needs text measurement — the
            // line breaking would be a guess, so abandon the plan.
            guard let main = basisPx ?? mainExplicit else { return nil }
            mains.append(main)
            // Cross size: the §9.4-step-7 input. Percent heights are
            // refused (allowPercent: false — same conservatism as the
            // nowrap main plan).
            let crossExplicit = SizeApplierResolve.exact(cs.height, ctx: ctx,
                                                         parent: crossAvail,
                                                         allowPercent: false)
            // SKEPTIC (wave 25) — a DECLARED cross size this static lane
            // cannot turn into points (a percent, which `allowPercent:
            // false` above refuses; a calc()/var() the converter left
            // unresolved; min/max/fit-content; or the explicit `auto`
            // keyword) is NOT the same thing as an ABSENT one, and the two
            // were being conflated: `crossExplicit == nil` fed both the
            // §8.4 stretch test below and hypotheticalCross's auto branch,
            // so such an item was counted as stretching AND estimated at
            // the empty-box floor. Both are fictions — StyleBuilder's fold
            // only adopts an injected height when `size.height == nil`, so
            // the item renders at whatever its own declaration resolves to
            // while the LINE was sized from 30pt that nobody measures.
            // Measured symptom before this guard: `height: 50%` on one of
            // four items produced 55 + 10 + 50 = 115pt of lines inside a
            // declared 110pt box (it overflowed), where the pre-lane hug
            // stayed at 95. Refuse the whole container — the same
            // no-guessing rule the padding/margin/content-size arms use.
            guard cs.height == nil || crossExplicit != nil else { return nil }
            guard let cross = FlexWrapPlan.hypotheticalCross(
                explicitCrossPx: crossExplicit,
                clampedCross: cs.minHeight != nil || cs.maxHeight != nil,
                isEmptyLeaf: (child.children?.isEmpty ?? true)
                    && (child.text?.isEmpty ?? true),
                hasCrossBands: Self.declaresBoxBands(child),
                // The harness's synthetic minimum box (MinBoxFloor):
                // 30pt on the product/baseline path, dropped entirely
                // under WPT capture — so an empty leaf is 0 there.
                emptyFloorPx: wptCaptureMode ? 0 : (StyleBuilder.minFloor(for: cs).height ?? 0))
            else { return nil }
            crosses.append(cross)
            // §8.3/§8.4 stretch precondition: the resolved alignment is
            // stretch AND the item has an AUTO cross size (an explicit
            // height always wins).
            let align = CSSFlexMath.resolvedAlign(self: agg.alignSelf,
                                                  items: parentAgg.alignItems)
            stretchy.append(align == .stretch && crossExplicit == nil)
        }
        // §9.3 + §9.4 step 7/8 — identical calls to FlowLayout's.
        let lines = FlexWrapPlan.breakLines(mainSizes: mains,
                                            containerMain: mainAvail,
                                            gap: gaps.column)
        let base = lines.map { line in
            (line.first...line.last).map { crosses[$0] }.max() ?? 0
        }
        let lineCross = FlexWrapPlan.stretchLines(base: base,
                                                  containerCross: crossAvail,
                                                  gap: gaps.row)
        var plan: [Int: CGFloat] = [:]
        for (li, line) in lines.enumerated() {
            for i in line.first...line.last where stretchy[i] {
                // Only a line that actually GREW injects anything: with
                // no leftover cross space the item already measures at
                // the line's cross size, and writing the value anyway
                // would turn a `minHeight` floor into an exact frame on
                // boxes the committed corpus renders today.
                if lineCross[li] > crosses[i] { plan[i] = lineCross[li] }
            }
        }
        return plan.isEmpty ? nil : plan
    }

    /// Does this component declare padding, a border, or a margin? The
    /// wrap stretch plan refuses all three:
    ///  • padding/border inflate the frame by an amount that depends on
    ///    the effective `box-sizing` (WPT capture flips it to
    ///    content-box), so the frame extent is not derivable from the IR
    ///    alone;
    ///  • a margin makes the item's OUTER cross size (what §9.4 step 7
    ///    measures, and what FlowLayout sees when it measures the
    ///    subview) differ from the box size this plan would inject —
    ///    injecting the line cross as the BOX height would then push the
    ///    outer size past the line.
    /// Over-refusal is deliberate: `BorderRadius` paints nothing into
    /// layout but still bails. A skipped stretch leaves the wave-24
    /// hug; a wrong one paints at a size the Layout never places.
    private static func declaresBoxBands(_ component: IRComponent) -> Bool {
        component.properties.contains {
            $0.type.hasPrefix("Padding") || $0.type.hasPrefix("Border")
                || $0.type.hasPrefix("Margin")
        }
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

    // MARK: - Multicol distribution (lane ios-multichild-multicol)

    /// The USED column-gap for a multicol container, in px — the single
    /// resolver behind the wave-9 fill basis, the wave-10 fragment plan
    /// AND the greedy distribution slots (they must agree or the fill
    /// width and the slot width drift apart). A declared ColumnGap/Gap
    /// resolves through the SAME GapApplier lane the flow container's
    /// spacing uses; an UNDECLARED gap is `normal`, which for multicol
    /// containers is 1em (css-align-3 §8.3) — the element's resolved
    /// font-size, NOT the GapConfig zero default (that zero is right for
    /// flex/grid, where `normal` means no gap). Non-multicol callers get
    /// 0 without touching the resolver.
    private func multicolUsedGapPx(style: ComponentStyle) -> CGFloat {
        // Only multicol containers consume a column-gap basis.
        guard style.columns?.isMulticolContainer == true else { return 0 }
        // Declared gap (Gap shorthand expands to ColumnGap on the
        // converter, but check both — GapExtractor reads both).
        if resolvedProperties.contains(where: {
            $0.type == "ColumnGap" || $0.type == "Gap" }) {
            // Percent column-gap resolves against the inline axis — the
            // container's content-box width (the same flexContentSize
            // subtraction the childCB channel publishes).
            return GapApplier.resolve(style.spacing.gap,
                                      context: style.spacing.context,
                                      parentWidth: flexContentSize(style: style,
                                                                   vertical: false)).column
        }
        // `column-gap: normal` = 1em for multicol (css-align-3 §8.3).
        return CGFloat(style.spacing.context.fontSizePx)
    }

    /// The distribution gate: does THIS container route its children
    /// through MulticolGreedyLayout (Android's greedy multi-child column
    /// distribution) instead of the block vertical stack?
    ///
    /// Android parity map: Compose flips a BLOCK container to
    /// DisplayType.MULTI_COLUMN whenever ColumnCount/ColumnWidth is
    /// present (ComponentRenderer.extractDisplayConfig) and routes ALL
    /// its children through MultiColumnDistributionLayout. iOS keeps the
    /// SINGLE-child family on its established paths (wave-9 column fill
    /// + wave-10 fragmentation — both byte-identical under this gate),
    /// so only 2+ in-flow children distribute; a lone child in column 0
    /// is visually identical to the vertical stack anyway (D6 pins that
    /// degenerate case in the shared table).
    private func multicolDistributes(style: ComponentStyle) -> Bool {
        // §2: a multicol container needs a non-auto count or width…
        style.columns?.isMulticolContainer == true
            // …on a BLOCK container (the Android flip only rewrites
            // DisplayType.BLOCK; declared flex/grid/inline displays keep
            // their own formatting context on both platforms)…
            && style.layout.display == .block
            // …with 2+ in-flow children (absolute boxes ride the overlay
            // and never distribute — css-multicol-1 §2 in-flow only).
            // Wave-21 wiring hook (lane MULTICOL): under WPT capture,
            // spanners don't count — a sole flow child + spanner routes to
            // the vertical stack where the balanced fragment row owns it.
            && (wptCaptureMode
                ? MulticolSpannerFlow.flowCount(
                    MulticolSpannerFlow.rolesFor(children: inFlowChildren)) >= 2
                : inFlowChildren.count >= 2)
    }

    // MARK: - Float row packing (wave-19 lane FLOAT)

    /// The block container's float-run plan, or nil when this container
    /// keeps the plain VStack — the iOS twin of Compose
    /// ComponentRenderer.blockFloatSegments (pins P1/P2/P8).
    ///
    /// nil when ANY of:
    ///  • not in composed-WPT capture (P8 — the dark-stage 327 corpus
    ///    must keep the VStack byte-identically);
    ///  • the sorted in-flow children contain no ≥2 streak of
    ///    left-floating siblings (run-free containers stay frozen).
    /// The segmentation runs over the SAME `FlexboxApplier.sorted`
    /// array contentOrPlaceholder renders, so FloatBlockLayout's
    /// subview indices line up by construction.
    // MARK: - Table separated tracks (wave-34 lane T)

    /// One box's resolved CSS 2.1 §17.6.1 track plan — the parameters
    /// `TableSeparatedLayout` needs, plus what (if anything) this box
    /// publishes to its descendants.
    struct TableTrackPlan {
        /// `.vertical` for the table box and its row groups; `.horizontal`
        /// for a row, whose cells run in the inline direction.
        let axis: TableSeparatedLayout.Axis
        /// Spacing inserted between successive children.
        let spacing: CGFloat
        /// §17.6.1's outer band, horizontal half (table box only).
        let bandWidth: CGFloat
        /// §17.6.1's outer band, vertical half (table box only).
        let bandHeight: CGFloat
        /// Non-nil only for a TABLE box: the used spacing its rows read.
        let publish: TableSeparatedTracks.Spacing?
    }

    /// The §17.6.1 track plan for THIS box, or nil when it keeps the plain
    /// VStack — the iOS analogue of the `blockFloatSegments` /
    /// `blockInlineAtomSegments` gates below.
    ///
    /// nil when ANY of:
    ///  • not in composed-WPT capture — the dark-stage 327 corpus must
    ///    keep the VStack byte-identically (it declares no table display
    ///    anywhere, so this is belt-and-braces, not the load-bearing gate);
    ///  • this box's role is not a table / row-group / row;
    ///  • it has no in-flow children, so there are no tracks to place.
    ///
    /// Wave 38 (lane N2) — the role now reads the HTML UA channel
    /// (`TableBoxTree.roleOf(_:sourceTag:)`) as well as the declared
    /// keyword. The css-tables corpus is HTML markup with no author
    /// `display`, so before this the gate saw NO table anywhere in 29 of
    /// the section's 48 tests and every `<table>` stacked its cells
    /// vertically; `meta.sourceTag` is the UA sheet's only sighting on the
    /// wire. A declared `display` still wins — the fallback fires only
    /// where the wire declared none.
    ///
    /// The plan runs over the SAME `inFlowChildren` array
    /// `contentOrPlaceholder` renders, so subview indices line up by
    /// construction — the discipline every custom Layout in this file
    /// shares.
    private func tableTrackPlan() -> TableTrackPlan? {
        // Composed-WPT capture only, mirroring the float / inline-atom gates.
        guard wptCaptureMode else { return nil }
        // css-tables-3 §2.1 role: declared keyword first, HTML UA tag second.
        let role = TableBoxTree.roleOf(resolvedProperties,
                                       sourceTag: component.meta?.sourceTag)
        let arrangement = TableSeparatedTracks.arrangement(role)
        guard arrangement != .none else { return nil }
        // No children ⇒ no tracks; the box keeps its ordinary box painting.
        guard !inFlowChildren.isEmpty else { return nil }
        if role == .table {
            // The table box owns the value. A nil answer means
            // `border-collapse: collapse` — §17.6.2's model has NO
            // border-spacing ("the border-spacing property is ignored"),
            // but its rows still stack and its cells still run inline, so
            // the tracks stay and only their spacing goes to zero.
            let used = TableSeparatedTracks.usedSpacing(
                properties: resolvedProperties,
                // The HTML UA sheet's 2px is keyed on the `<table>`
                // ELEMENT, and meta.sourceTag is its only sighting — the
                // same channel AbsposCbUsedHeight's H3 lane reads.
                sourceTag: component.meta?.sourceTag) ?? .zero
            return TableTrackPlan(
                axis: .vertical,
                // §17.6.1: successive rows are separated by the VERTICAL
                // border-spacing.
                spacing: CGFloat(used.verticalPx),
                // …and the table box carries the outer band on all four
                // sides, which is what puts its first cell at +2,+2.
                bandWidth: TableSeparatedTracks.outerBandApplies(role)
                    ? CGFloat(used.horizontalPx) : 0,
                bandHeight: TableSeparatedTracks.outerBandApplies(role)
                    ? CGFloat(used.verticalPx) : 0,
                publish: used)
        }
        // Row group / row: the value was published by the enclosing table.
        // Nil (no table ancestor) degrades to the CSS initial 0 rather
        // than inventing a band — a stray `display: table-row` still lays
        // its cells out inline, which is the part §17.6.1 and §17.6.2 agree on.
        let inherited = tableBorderSpacing ?? .zero
        switch arrangement {
        case .blockStack:
            // A row group is transparent for row ordering (§2.1): it
            // stacks rows exactly like the table, but sits INSIDE the
            // table's band, so it adds none of its own.
            return TableTrackPlan(axis: .vertical,
                                  spacing: CGFloat(inherited.verticalPx),
                                  bandWidth: 0, bandHeight: 0, publish: nil)
        case .inlineRow:
            // The fix: cells of a row run in the inline direction,
            // separated by the HORIZONTAL border-spacing.
            return TableTrackPlan(axis: .horizontal,
                                  spacing: CGFloat(inherited.horizontalPx),
                                  bandWidth: 0, bandHeight: 0, publish: nil)
        case .none:
            return nil
        }
    }

    private func blockFloatSegments() -> [FloatRowPacking.Segment]? {
        // P8 — composed-WPT capture only (the environment flag the
        // capture screen sets; property fixtures / dark stage never do).
        guard wptCaptureMode else { return nil }
        // The exact render order of the child subviews (CSS `order`).
        let children = FlexboxApplier.sorted(inFlowChildren)
        // Wave-20 W3: the container's own Direction keyword decides how
        // the LOGICAL float/clear members map to physical sides
        // (css-logical-1 §2.1: rtl swaps inline-start/end). Read from
        // the container's declared properties only — the engine has no
        // inherited-direction channel, and the corpus (descendant-
        // static-position-002/004) declares physical `right` anyway.
        let rtl = component.properties.contains {
            $0.type == "Direction" &&
                ValueExtractors.extractKeyword($0.data)?.uppercased() == "RTL"
        }
        // Per-sibling facts: float side + the `<br clear>` break shape
        // (childless Clear-only marker — pin P2).
        let facts = children.map {
            FloatRowPacking.facts(from: $0.properties,
                                  hasChildren: $0.children?.isEmpty == false,
                                  rtl: rtl)
        }
        // Segment once; only a plan with an actual run leaves the VStack.
        let segments = FloatRowPacking.segment(facts)
        return segments.contains(where: { $0.isRun }) ? segments : nil
    }

    // MARK: - Inline atom flow (wave-20 lane W3)

    /// The block container's inline-atom plan, or nil when this
    /// container keeps the plain VStack — the iOS twin of Compose
    /// ComponentRenderer.blockInlineAtomSegments (pins P15/P19).
    ///
    /// nil when ANY of:
    ///  • not in composed-WPT capture (P19 — the dark-stage 327 corpus
    ///    must keep the VStack byte-identically);
    ///  • the sorted in-flow children contain no ≥2 streak of
    ///    inline-level atoms (run-free containers stay frozen).
    /// The segmentation runs over the SAME `FlexboxApplier.sorted`
    /// array contentOrPlaceholder renders, so InlineAtomBlockLayout's
    /// subview indices line up by construction.
    private func blockInlineAtomSegments() -> [InlineAtomFlow.Segment]? {
        // P19 — composed-WPT capture only, mirroring the float gate.
        guard wptCaptureMode else { return nil }
        // The exact render order of the child subviews (CSS `order`).
        let children = FlexboxApplier.sorted(inFlowChildren)
        // Per-sibling atom facts from the decoded wire: originating tag
        // (meta.sourceTag), a declared Display override, element
        // children and text presence (the text-only-anchor guard, P15).
        // One predicate, two consumers (fix 3): the SAME isInlineAtom
        // read also guards the wptBlockFlowFillWidth fold, so the set of
        // children the fold skips and the set the atom rows pack are
        // equal by construction.
        // Wave-33 lane C (C2) — the SECOND atom family: an author-declared
        // `display: inline-block` box with a definite size. Same §9.4.2
        // packing, different geometry SOURCE (the wire, not the UA table),
        // so it enters the identical run. Deliberately OR'd here and NOT
        // folded into `isInlineAtom`: that predicate's other consumer is
        // the wptBlockFlowFillWidth guard, and a declared inline-block
        // already carries its own width, so widening it there would change
        // a fold this lane has not measured.
        let atomFlags = children.map {
            Self.isInlineAtom($0) || Self.inlineBlockAtomSpec($0, container: component.properties) != nil
        }
        // Segment once; only a plan with an actual run (≥2 consecutive
        // atoms) leaves the VStack — mirror of the float gate.
        let segments = InlineAtomFlow.segment(atomFlags)
        return segments.contains(where: { $0.isRun }) ? segments : nil
    }

    /// Wave-33 lane C (C2) — the declared-inline-block AtomSpec for one
    /// child, or nil when it is not that family. Split out so the two
    /// consumers (the segmenter above and the run's spec resolution in
    /// `styledContent`) read the SAME predicate and can never disagree
    /// about which children are in the run. Static + pure so the XCTest
    /// pins it against the real wire without a render surface.
    static func inlineBlockAtomSpec(
        _ component: IRComponent,
        container: [IRProperty]
    ) -> UAWidgetIntrinsics.AtomSpec? {
        InlineBlockAtom.spec(
            properties: component.properties,
            // B6 — own text / inline runs are the statically visible
            // line-box source; either moves the baseline off the bottom
            // margin edge (§10.8.1) and the box leaves this lane.
            hasOwnText: component.text?.isEmpty == false,
            hasOwnRuns: component.meta?.runs?.isEmpty == false,
            // B7 — the strut pins are solved for the harness's default line
            // box; a declared container line-height invalidates them
            // (absolute-tables-013's `line-height: 0` <td>).
            containerDeclaresLineHeight: container.contains { $0.type == "LineHeight" }
        )
    }

    /// Wave-20 fix 3 — the per-COMPONENT twin of the per-child atom
    /// facts in blockInlineAtomSegments: does this component itself
    /// qualify as an inline-level UA-widget atom (P15)? Consumed by the
    /// wptBlockFlowFillWidth fold guard in `styledContent` — an atom is
    /// inline-block by UA default (CSS 2.1 §10.3.9 shrink-to-fit), so
    /// the §10.3.3 block fill must never stretch it. Static + pure so
    /// the XCTest pins it against the real appearance-auto-001 wire
    /// without a render surface (the suppressesNamePlaceholder pattern).
    static func isInlineAtom(_ component: IRComponent) -> Bool {
        InlineAtomFlow.isAtom(
            tag: component.meta?.sourceTag,
            // css-display-3 §2: a declared non-inline display takes the
            // box out of the inline flow — same read the segmenter does.
            displayKeyword: component.properties.first(where: { $0.type == "Display" })
                .flatMap { ValueExtractors.extractKeyword($0.data)?.uppercased() },
            hasElementChildren: component.children?.isEmpty == false,
            hasText: component.text?.isEmpty == false
        )
    }

    /// A run member's widget kind for the shared UA geometry table
    /// (UAWidgetIntrinsics). The `type`/`multiple` attributes ride the
    /// wire as `meta.attrs` (the wave-20 widget-identity contract) and
    /// are decoded into IRMeta.attrs by lane W2's capsule — an absent
    /// capsule (pre-wave-20 wires, non-widget tags) folds to the
    /// textField/menulist defaults exactly like the HTML parser's
    /// missing-attribute states.
    static func atomKindOf(_ child: IRComponent) -> UAWidgetIntrinsics.Kind {
        UAWidgetIntrinsics.kind(
            tag: child.meta?.sourceTag,
            // The wire's `type` attribute (present-in-source only).
            typeAttr: child.meta?.attrs?.type,
            // The wire's boolean `multiple` presence (listbox height).
            multiple: child.meta?.attrs?.multiple == true
        )
    }

    // MARK: - Content

    /// Wave-32 lane R — ONE anonymous inline run, painted at its slot in the
    /// child walk.
    ///
    /// Routed through `PlaceholderLabel` for the same reason the leading-text
    /// site is: SwiftUI's `Text` inherits no styling, so a bare `Text(run)`
    /// would drop the component's font, colour, line-height and text-indent
    /// and the run would not match the glyphs around it. Every argument here
    /// is the leading-text call's argument — this is the SAME run, moved.
    ///
    /// `decorations` is deliberately NOT threaded: the wave-22 inline-chain
    /// collapse drops `_runs` along with the children it flattens, so the two
    /// keys can never co-occur on one component and there is no list to pass.
    @ViewBuilder
    private func inlineRunLabel(_ text: String, style: ComponentStyle) -> some View {
        PlaceholderLabel(
            name: text,
            // Same currentColor bottom-out as the leading-text label: spec
            // BLACK under the WPT ref injection, the stage default otherwise.
            color: style.text.color
                ?? (currentColorBottomsOut
                        ? WPTCanvas.captureTextInk(
                            wptCaptureMode: wptCaptureMode,
                            defaultInk: InheritedText.defaultTextColor)
                        : nil),
            textConfig: style.text,
            backgroundColor: style.backgroundColor,
            clipTextGradient: nil,
            fillWidth: style.size.width != nil,
            wptCaptureMode: wptCaptureMode,
            wrapWidth: textWrapWidth(style: style)
        )
    }

    ///
    /// Wave-32 lane R: `interleaveRuns` is opt-in per CALL SITE, not derived
    /// from the component. Only the plain block `VStack` passes true — every
    /// other branch hands its subviews to a custom `Layout` that indexes them
    /// against the sorted child array plus a `leadingCount`, so an extra
    /// anonymous-run subview would silently shift every placement. False
    /// keeps this function byte-identical to wave 31.
    @ViewBuilder
    private func contentOrPlaceholder(style: ComponentStyle,
                                      interleaveRuns: Bool = false) -> some View {
        // ── wave-30 lane-3 OWN-DISPLAY marker (fix B1, see
        // StyleEngine/lists/ListItemMarkerGate.swift). css-lists-3 §3.1
        // attaches the ::marker to the BOX, not the tag: a
        // `display: list-item` element that is not an `<li>` under a list
        // container generates one too, and both natives painted nothing for
        // it. Emitted FIRST — ahead of the root ::before below — because CSS
        // orders ::marker before ::before (the same ordering web's
        // NodeRenderer spells out at its `markerNode` positional argument).
        // Skipped for every component that is not a self-marking `inside`
        // list item, i.e. for the whole 327-pair dark stage and every tagged
        // `<li>` (those keep the parent-loop markerPlacement path).
        if ListItemMarkerGate.rendersOwnLeadingMarker(
                sourceTag: component.meta?.sourceTag,
                properties: resolvedProperties) {
            ownListMarker(style: style)
        }
        // ── wave-28 lane-PG ROOT-SCOPE generated box (see RootPseudoBox.swift).
        // This function is the ONE content emitter every container branch
        // calls, so emitting here puts the box FIRST in the flow — exactly
        // where web's NodeRenderer puts its ::before span and where Compose's
        // RenderContent puts its twin. Nil for every component that is not a
        // body-root carrying a paintable `pseudos` bucket, i.e. for the whole
        // 327-pair dark stage and for every ordinary element.
        if let pseudoSpec = RootPseudo.specFor(component: component, role: "before") {
            RootPseudoBoxView(
                spec: pseudoSpec,
                // css-contain-1 §3.1: a contained body does not propagate its
                // direction to the viewport, so the root-owned box keeps the
                // root's own `ltr` and sits physically left.
                pinInlineStart: RootPseudo.containmentBlocksDirectionPropagation(
                    RootPseudo.containKeywords(of: component))
            )
        }
        // ── Wave-20 lane-W2 UA-widget mount hook (the ONLY renderer entry
        // for widget content). WPT capture only: a form-control component
        // (meta.sourceTag + meta.attrs, css-ui-4 §7 appearance ≠ none)
        // paints its Chromium-lookalike replica INSTEAD of text/children
        // content — painting lives in StyleEngine/widgets/UAWidget*.
        // Dark stage: wptCaptureMode is false on every 327 path → no-op.
        if wptCaptureMode,
           let spec = UAWidgetsResolve.resolve(component: component,
                                               properties: resolvedProperties) {
            // Wave-38 lane N1 — the BLOCK-context line box. A widget packed
            // into an inline atom RUN is already placed against its row
            // baseline by InlineAtomBlockLayout, so it paints flush; a LONE
            // widget is block-stacked and, before this lane, its wrapper
            // advanced by the bare border-box height (css-ui
            // appearance-revert-001 drifted 33px over 14 rows → 0.8222).
            // UAWidgetBlockLine supplies the missing §10.8 leading.
            let blockLead = inlineAtomRunMember
                ? nil
                : UAWidgetsResolve.blockLead(component: component,
                                             properties: resolvedProperties)
            // Padding OUTSIDE the replica and INSIDE the component box: the
            // widget's own background/border (already painted on the box by
            // the modifier chain) keeps its geometry, while the block
            // advance grows to the line box height. CSS px == pt at the
            // capture scale. Nil lead keeps the frozen, UNMODIFIED view —
            // an explicit branch rather than a zero padding, so every
            // pre-wave-38 widget composition is byte-identical.
            if let lead = blockLead {
                UAWidgetView(spec: spec)
                    .padding(.top, CGFloat(lead.topPx))
                    .padding(.bottom, CGFloat(lead.bottomPx))
            } else {
                UAWidgetView(spec: spec)
            }
        }
        // The placeholder only appears when the component has NO
        // children at all — a parent whose children are ALL absolutely
        // positioned still renders empty in-flow content (web parity:
        // ComponentRenderer.tsx keys the placeholder on
        // `children.length`), while its boxes arrive via the overlay.
        else if component.children?.isEmpty == false {
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
            // ── Wave-32 lane R: the ordered inline content ──────────────
            // `meta.runs` says where this component's own text sits RELATIVE
            // to its children (spec 03 §4.1) — the shape the single `text`
            // string cannot express, and the one the CSS2 static-position
            // family is decided by. When a plan resolves it is
            // AUTHORITATIVE: the leading label below is suppressed (its
            // string is the concatenation the runs were split FROM, so
            // painting both would double the glyphs) and each run is emitted
            // at its slot in the child walk instead. nil — hence completely
            // inert — for every component without the key, for every
            // non-plain container (see the `interleaveRuns` doc), and for a
            // list the fold cannot express (InlineRunPlan.resolve's proof).
            let runPlan = interleaveRuns
                ? InlineRunPlan.resolve(component.meta?.runs,
                                        children: FlexboxApplier.sorted(inFlowChildren))
                : nil
            if let t = component.text, !t.isEmpty, runPlan == nil {
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
                    wrapWidth: textWrapWidth(style: style),
                    // Wave 22 (lane DECOR): this run renders the COMPONENT's
                    // own text, so it owns the component's
                    // `meta.decorations` list too — the mixed-content
                    // sibling of the leaf site below. Nil for every
                    // non-collapsed component.
                    decorations: DecorationWire.decorationLines(
                        from: component.meta?.decorations)
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
            // Wave 24 (lane GAPS-I, css-gaps-1): does THIS container
            // paint gap decorations? Computed once for the whole child
            // loop; false for every component in the committed corpus
            // (no fixture carries a *-rule-* property that resolves to
            // ink), so the per-child anchor below attaches nowhere and
            // the baseline view tree is byte-identical.
            let gapDecorActive = gapDecorationsConfig?.isActive == true
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
            // Wave 25 (lane ISTRETCH, CAL-RC5) — the WRAP path's twin of
            // `flexStretch`: FlowLayout only PROPOSES its line cross to a
            // stretch item and an IR child ignores proposals, so the
            // forced cross size is injected into the child's own
            // SizeConfig here (see flexWrapStretchPlan). Nil — hence
            // completely inert — for every container whose lines have no
            // leftover cross space to hand out, which is all of the
            // committed corpus.
            let flexWrapStretch: [Int: CGFloat]? = isWrapFlex
                ? flexWrapStretchPlan(style: style, children: children,
                                      column: isColumn)
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
            // Lane UAM (BD-RC3): same UA-default fold as the hoist-band
            // call in styledContent — the two MUST pass the identical flag
            // or the bands and the per-child overrides would disagree.
            let collapsePlan = MarginCollapse.containerPlan(component: component,
                                                            style: style,
                                                            uaBlockMargins: wptCaptureMode)
            // Wave-9 regression fix — the USED column-gap feeding the
            // multicol fill basis (css-multicol-1 §3 via MulticolMath in
            // wptChildFillWidth). Factored into multicolUsedGapPx (lane
            // ios-multichild-multicol) because the greedy distribution
            // container in flowContainer needs the IDENTICAL used gap —
            // one resolver, three consumers (fill basis, fragment plan,
            // distribution slots) that agree by construction.
            let multicolFillGap: CGFloat = multicolUsedGapPx(style: style)
            // Lane ios-multichild-multicol — css-multicol-1 §2: when THIS
            // container distributes its children across column boxes
            // (flowContainer's MulticolGreedyLayout branch), each child's
            // containing block is the COLUMN box, so the width channel
            // must publish the §3 USED column width, not the container's
            // content width — the same override multicolFragmentRow
            // applies for the single-child fragment clones. Nil when the
            // container does not distribute (single child / non-multicol)
            // or its own width is indefinite (no invented geometry).
            let multicolColumnCB: CGFloat? = {
                // Only the distribution branch re-parents to column boxes.
                guard multicolDistributes(style: style), let cb = childCB,
                      // The same §3 fit the layout itself computes — same
                      // inputs (content width, config, used gap), so the
                      // published basis matches the laid-out column width.
                      let used = MulticolMath.usedColumns(
                        availableWidthPx: Double(cb),
                        requestedCount: style.columns?.count,
                        requestedWidthPx: style.columns?.widthPx,
                        gapPx: Double(multicolFillGap))
                else { return nil }
                // The used per-column inline size in px.
                return CGFloat(used.widthPx)
            }()
            ForEach(Array(children.enumerated()), id: \.offset) { index, child in
                // Wave-32 lane R: the anonymous inline runs that precede THIS
                // child, emitted immediately before it so the wire's document
                // order survives into the stack. Empty (hence no extra
                // subview at all) for every index the plan does not name, and
                // for every container that did not ask to interleave — so the
                // view tree outside the ~1,138-component `_runs` population is
                // untouched. Routed through PlaceholderLabel, exactly like the
                // leading-text site above, so a run picks up the parent's
                // TextConfig (font, colour, line-height) rather than SwiftUI's
                // inherited-nothing default.
                ForEach(Array((runPlan?.before[index] ?? []).enumerated()), id: \.offset) { _, runText in
                    inlineRunLabel(runText, style: style)
                }
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
                // prepend a marker. SwiftUI has no ::marker pseudo, so we
                // emit it inline via an HStack with a leading Text.
                //
                // Wave 24 (lane LF, B-RC3 parts 1+2): the marker is no
                // longer hard-coded to "1." for <ol> / a bullet for <ul>.
                // The tag supplies only the UA default (HTML §15.3.9); the
                // ITEM's own list-style-* declarations — which is where the
                // live wire puts them (tools/titan/runs/wave23-final/
                // sections/css-lists/per-test-ir/wpt__css-lists__change-
                // list-style-type-001.json carries ListStyleType square /
                // none / upper-roman / decimal on each <li> and
                // ListStylePosition INSIDE on the <ul>) — override it,
                // inheritance-aware: `childInherited` is this container's
                // already-merged inheritable set (list-style-* are
                // "Inherited: yes", css-lists-3 §3.1), so a declaration
                // made on an ancestor reaches the item too. ListMarkerText
                // then owns the full css-counter-styles-3 §6 table.
                //
                // Wave 28 (lane MC): the resolved `position` now CHANGES
                // the placement instead of only being carried. An
                // `inside` marker on an item with no in-flow text paints
                // INSIDE the item's box, through an overlay that cannot
                // move or resize it — css-lists-3 §3.2 makes it the item's
                // first inline box. Everything else keeps the HStack.
                //
                // STILL DEFERRED — the rest of B-RC3 part 3: `outside` is
                // painted as a leading inline box rather than hung in the
                // item's margin area, an `inside` marker on an item that
                // DOES have text still displaces that item's box, and the
                // gap is the fixed ListMarkerRow.gapPt rather than the
                // UA's per-counter-style marker padding. Those need a
                // custom Layout — out of scope here.
                // (v2 rename: the tag hint lives at meta.sourceTag.)
                let parentTag = (component.meta?.sourceTag ?? "").lowercased()
                let isListItem = (child.meta?.sourceTag ?? "").lowercased() == "li"
                // "" whenever no marker box exists: not a list item, not a
                // list container, or `list-style-type: none` (css-lists-3
                // §3.1 — the item then has NO marker at all, so its content
                // must start at its own content edge; the pre-wave-24 code
                // still reserved a Text + 4pt spacing for those rows).
                //
                // Wave 28 (lane MC): the config is resolved ONCE, outside
                // the string closure. It used to be computed inside it and
                // thrown away — and skipped entirely on the baked path —
                // so `list-style-position` was unreachable for exactly the
                // items that carry a baked marker, which is every item in
                // the counter-styles corpus. `rendersInsideOverlay` needs
                // it. Nil only when the parent is not a list container.
                let markerConfig = ListMarkerResolver.resolve(
                    parentTag: parentTag,
                    parentProperties: childInherited,
                    childProperties: child.properties)
                let markerText: String = {
                    // Wave 27 (lane CBAKE): a BAKED marker wins outright.
                    // `meta.markerText` is the extractor's full
                    // css-counter-styles-3 §6 resolution — the counter style
                    // AND the `<ol start>` ordinal, neither of which
                    // ListMarkerResolver can see — so re-deriving it here
                    // could only be worse. Absent ⇒ the local table below.
                    // (Deliberately NOT gated on `isListItem`: a producer
                    // may bake a marker onto an untagged child, and wave 27
                    // honours it. Unchanged.)
                    if let baked = child.meta?.markerText { return baked }
                    guard isListItem, let cfg = markerConfig else { return "" }
                    return ListMarkerText.marker(index: index, config: cfg)
                }()
                let isMarkerRow = !markerText.isEmpty
                // Wave 28 (lane MC): which of the two placements this
                // marker takes. See ListMarkerRow.rendersInsideOverlay for
                // the measured column table and why both gates matter.
                // Computed ONCE — it drives both the placement choice and,
                // in the row placement, the cross-axis alignment.
                let markerExposesBaseline =
                    ListMarkerRow.itemExposesTextBaseline(child)
                let markerInsideOverlay = ListMarkerRow.rendersInsideOverlay(
                    position: markerConfig?.position,
                    itemExposesTextBaseline: markerExposesBaseline)
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
                    // Wave-21 wiring hook (lane MULTICOL): under capture
                    // the "sole child" gate counts FLOW siblings only —
                    // a 0-height spanner sibling must not veto the
                    // balanced fragment row (always-balancing-before-
                    // column-span). Dark stage keeps the raw count.
                    siblingCount: wptCaptureMode
                        ? MulticolSpannerFlow.flowCount(
                            MulticolSpannerFlow.rolesFor(children: children))
                        : children.count,
                    contentWidthPx: childCB,
                    contentHeightPx: childCBH,
                    gapPx: multicolFillGap,
                    childProperties: child.properties,
                    ctx: style.spacing.context,
                    // Wave-21: unlocks the §7.1 auto-height balanced
                    // fragmentainer (B-RC5) in capture only.
                    wptCaptureMode: wptCaptureMode)
                Group {
                    if let plan = fragPlan {
                        // Fragment pass — F clipped+translated clones of
                        // the child, one per column (multicolFragmentRow
                        // below documents the modifier-order argument).
                        multicolFragmentRow(child: child, plan: plan)
                    } else if isMarkerRow {
                        // Wave 27 (lane NMARK, B-RC5 + B-RC6) + wave 28
                        // (lane MC). The synthesized marker box must not
                        // MOVE or RESIZE the item's principal box; the two
                        // placements and the evidence for each live in
                        // ListMarkerRow.
                        markerPlacement(child: child, markerText: markerText,
                                        insideOverlay: markerInsideOverlay,
                                        exposesBaseline: markerExposesBaseline,
                                        // Wave 30 (lane 3, fix B6) — the
                                        // resolved counter style and the
                                        // marker run's font size, the two
                                        // inputs the painted disc/circle/
                                        // square needs. `.disc` for the nil
                                        // config is the initial value of
                                        // list-style-type (css-lists-3 §3.1)
                                        // and only ever reaches a baked
                                        // marker under a non-list parent,
                                        // where the string guard in
                                        // ListMarkerSymbol.shape(for:
                                        // markerText:) declines anyway.
                                        // The font size is the CONTAINER's,
                                        // which is the same honest-scope
                                        // limitation ListMarkerTextStyle
                                        // documents for the marker's font on
                                        // BOTH natives.
                                        markerType: markerConfig?.type ?? .disc,
                                        // 16 = the browser's inherited body
                                        // default, the same bottom-out
                                        // `effectiveLineHeight(fontSizePx:)`
                                        // defaults to and the Compose twin
                                        // spells ListMarkerLineBox
                                        // .DEFAULT_FONT_SIZE_SP.
                                        markerFontSizePx: style.text.fontSize ?? 16,
                                        isCSSFlex: isCSSFlex,
                                        childAgg: childAgg, parentAgg: parentAgg)
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
                                ?? (isColumn ? flexMainSizes?[index] : flexStretch)
                                // Wrap-flex §8.4 cross stretch (row
                                // direction ⇒ the block axis, so this
                                // channel). Nil for every other parent,
                                // and the plan itself is nil unless a
                                // line actually grew.
                                ?? flexWrapStretch?[index])
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
                                isMarkerRow: isMarkerRow,
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
                // tree level (no grandparent leak). Lane
                // ios-multichild-multicol: under a DISTRIBUTING multicol
                // container the children's containing block is the COLUMN
                // box (css-multicol-1 §2), so the used column width wins
                // when the distribution branch is active (nil otherwise —
                // every non-distributing container keeps childCB).
                .environment(\.containingBlockWidth, multicolColumnCB ?? childCB)
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
                // Wave 24 (lane GAPS-I): publish this item's resolved
                // bounds so the container's gap-decoration painter can
                // group lines and place rules. A transparent modifier
                // (anchorPreference changes neither layout nor paint),
                // and only attached at all when the container declares
                // gap decorations — see gapDecorationItemFrame.
                .gapDecorationItemFrame(index: index, active: gapDecorActive)
            }
            // Wave-32 lane R: runs after the LAST referenced child. Empty for
            // every plan that ends on a child (and for every container that
            // did not interleave), so this adds no subview outside the
            // `_runs` population.
            ForEach(Array((runPlan?.trailing ?? []).enumerated()), id: \.offset) { _, runText in
                inlineRunLabel(runText, style: style)
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
                wrapWidth: textWrapWidth(style: style),
                // Wave 22 (lane DECOR): the LEAF site — where a collapsed
                // inline run lands (the extractor flattens the chain to one
                // childless text component). `meta.decorations` reaches the
                // per-line overlay from here.
                decorations: DecorationWire.decorationLines(
                    from: component.meta?.decorations),
                // Wave 35 (lane B5) — the upright-vertical gate. Reads the
                // MERGED list because `writing-mode` / `text-orientation` are
                // inherited and normally sit on an ancestor. nil for every run
                // that is not an all-upright (CJK / kana / FULLWIDTH) run in a
                // `vertical-*` mode, i.e. for every other component in the
                // frozen corpus, which therefore renders byte-identically.
                verticalUprightStack: VerticalUprightGate.stack(
                    properties: resolvedProperties, text: component.text)
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

    /// The item's principal box as the marker branch renders it, with the
    /// legacy flexbox decoration a non-`CSSFlexLayout` parent still needs.
    /// Factored out (wave 28, lane MC) so the two placements below build
    /// the SAME item view — a second copy would be free to drift.
    @ViewBuilder
    private func markerItemView(child: IRComponent, isCSSFlex: Bool,
                                childAgg: LayoutAggregate?,
                                parentAgg: LayoutAggregate?) -> some View {
        if !isCSSFlex, let ca = childAgg, let pa = parentAgg {
            // Marker rows keep the legacy decoration; the host's placement
            // inside the row is invisible to the outer Layout (layout
            // values don't cross container boundaries) — same as the
            // pre-v2 no-spec behaviour.
            ComponentHost(component: child)
                .modifier(FlexboxApplier.childModifier(for: ca, parent: pa))
        } else {
            ComponentHost(component: child)
        }
    }

    /// One `<li>`'s marker, in whichever of the two placements
    /// `ListMarkerRow.rendersInsideOverlay` chose. Wave 28, lane MC.
    ///
    /// The marker `Text` is identical in both: `.fixedSize()` because the
    /// ::marker box is inline-level shrink-to-fit content sized by its
    /// glyphs (css-lists-3 §3.2, B-RC5) — never by whatever inline space
    /// the item's declared width leaves over. Wave 30 (lane 3, fix B6)
    /// adds `.listMarkerSymbol` to both: for `disc`/`circle`/`square` it
    /// swaps the glyph's INK for the painted Chromium symbol while keeping
    /// the box the glyph measured (see ListMarkerSymbol) — a no-op for
    /// every other counter style.
    @ViewBuilder
    private func markerPlacement(child: IRComponent, markerText: String,
                                 insideOverlay: Bool, exposesBaseline: Bool,
                                 markerType: ListMarkerType,
                                 markerFontSizePx: CGFloat,
                                 isCSSFlex: Bool,
                                 childAgg: LayoutAggregate?,
                                 parentAgg: LayoutAggregate?) -> some View {
        if insideOverlay {
            // `list-style-position: inside` on an item with no in-flow
            // text: the marker is the item's FIRST INLINE BOX, so it lives
            // INSIDE the principal box and must not move it. `.overlay`
            // never participates in its host's sizing, so the item keeps
            // both its origin — which is what its absolutely positioned
            // descendants anchor to, css-position-3 §2.1 — and its
            // declared height (css-sizing-3 §5.1).
            //
            // `.topLeading` = the item's border-box origin. KNOWN
            // APPROXIMATION: §3.2 wants the CONTENT-box origin, so an item
            // with its own padding/border would offset the marker by that
            // much; no `<li>` in the counter-styles corpus declares
            // either, and closing it needs the item's resolved box metrics
            // here. Twin of the Compose overlay's identical note.
            //
            // `.overlay` (above) rather than `.background` (below): in the
            // browser an inside marker paints above the item's own
            // background but below its positioned descendants, and we
            // cannot split those layers — painting above at least keeps an
            // item with an opaque background from swallowing its marker.
            markerItemView(child: child, isCSSFlex: isCSSFlex,
                           childAgg: childAgg, parentAgg: parentAgg)
                .overlay(alignment: .topLeading) {
                    // Wave 34 (lane F1) — per-script font runs. The ::marker
                    // is exactly where the non-Latin counter systems paint
                    // (css-counter-styles-3 §6 `armenian` / `arabic-indic` /
                    // `bengali` / `cambodian`), so the bundled fallback faces
                    // have to reach THIS Text and not only the item's own
                    // content. Identity outside WPT capture and for any
                    // Latin/ASCII marker ("1.", "•"). This is the
                    // `list-style-position: inside` branch — the one the
                    // arabic-indic 101/102/103 documents take.
                    Text(ScriptFallbackFonts.annotate(markerText,
                                                      enabled: wptCaptureMode,
                                                      size: markerFontSizePx))
                        .fixedSize(horizontal: true, vertical: true)
                        .listMarkerSymbol(type: markerType,
                                          markerText: markerText,
                                          fontSizePx: markerFontSizePx)
                }
        } else {
            // `outside`, or an item whose in-flow text the marker must
            // push along the line: the leading-inline-box row. Its
            // cross-axis alignment is still chosen per item (B-RC6) —
            // SwiftUI falls back to a baseline-less view's BOTTOM edge,
            // which would hang the marker's ascent outside the item.
            HStack(alignment: ListMarkerRow.rowAlignment(
                        itemExposesTextBaseline: exposesBaseline),
                   spacing: ListMarkerRow.gapPt) {
                // Wave 34 (lane F1) — per-script font runs, same rule as the
                // inside-overlay branch above. This is the OUTSIDE marker row,
                // the branch the armenian / bengali / cambodian documents take.
                Text(ScriptFallbackFonts.annotate(markerText,
                                                  enabled: wptCaptureMode,
                                                  size: markerFontSizePx))
                    .fixedSize(horizontal: true, vertical: true)
                    .listMarkerSymbol(type: markerType,
                                      markerText: markerText,
                                      fontSizePx: markerFontSizePx)
                markerItemView(child: child, isCSSFlex: isCSSFlex,
                               childAgg: childAgg, parentAgg: parentAgg)
            }
        }
    }

    /// The `::marker` a box generates from its OWN `display: list-item`
    /// (css-lists-3 §3.1) — wave 30, lane 3 (fix B1). Twin of Compose's
    /// `ComponentRenderer.RenderOwnListMarker`.
    ///
    /// The gate, the four predicates behind it and the MEASURED reason
    /// `outside` is excluded all live in `ListItemMarkerGate`; this
    /// function is only the paint.
    ///
    /// ## Why a LEADING LINE BOX and not an HStack
    /// css-lists-3 §3.2 makes an `inside` marker the item's FIRST INLINE
    /// BOX. The item's own in-flow content on all three runtimes is
    /// BLOCK-level (`PlaceholderLabel` for text, a stack of children
    /// otherwise), so the marker can never share a line with it and owns a
    /// line box of its own at the top of the item's content — which is
    /// exactly what the web runtime produces (`1.` on one line, `text` on
    /// the next; ink rows 46–65 / 76–82 of the live
    /// change-list-style-position-003 web capture). It is NOT an
    /// `.overlay`: an overlay never participates in sizing and would leave
    /// the item's block content 20px too high, which IS the current
    /// defect. It is NOT an HStack either: that would put the marker
    /// BESIDE the item's content, which neither the browser nor web does
    /// for block content.
    ///
    /// The `.frame(height:)` pins the resolved line box rather than
    /// wrapping the glyph, so an item whose marker resolves through a
    /// fallback face with a taller natural line cannot stretch its own
    /// content down. `alignment: .leading` centres the glyph vertically in
    /// that box (SwiftUI's `.leading` is horizontal-leading +
    /// vertical-centre) and, with no width in the frame, leaves the
    /// horizontal placement to the enclosing `VStack(alignment: .leading)`.
    @ViewBuilder
    private func ownListMarker(style: ComponentStyle) -> some View {
        let markerText = ListItemMarkerGate.ownMarkerText(resolvedProperties)
        // The marker inherits from its originating element (css-lists-3
        // §3.2) — which HERE is the component itself, so this is the one
        // marker call site whose font really is the item's (the
        // honest-scope gap ListMarkerTextStyle documents for the row path
        // does not apply). 16 = the browser's inherited body default, the
        // same bottom-out `effectiveLineHeight(fontSizePx:)` defaults to.
        let fontSizePx = style.text.fontSize ?? 16
        // Wave 34 (lane F1) — per-script font runs, same rule as the two row
        // placements above (`display: list-item` on a non-`<li>` box).
        Text(ScriptFallbackFonts.annotate(markerText,
                                          enabled: wptCaptureMode,
                                          size: fontSizePx))
            // Shrink-to-fit ::marker box (css-lists-3 §3.2) — the same
            // rule both row placements above carry.
            .fixedSize(horizontal: true, vertical: true)
            .listMarkerSymbol(
                type: ListItemMarkerGate.ownMarkerConfig(resolvedProperties).type,
                markerText: markerText,
                fontSizePx: fontSizePx)
            .frame(height: ComponentRenderer.effectiveLineHeight(
                        declared: style.text.lineHeight,
                        declaredNormal: style.text.lineHeightIsNormal,
                        fontSizePx: fontSizePx,
                        wptCaptureMode: wptCaptureMode),
                   alignment: .leading)
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

    // Wave 22 (lane DECOR, B-RC4b) — the merged `meta.decorations` wire
    // for a COLLAPSED inline run: the ordered, ancestor-first list of
    // every decorating box's line and its OWN colour (css-text-decor-3
    // §2.1 propagation + §2.2 per-box colour). Wire shape
    // [{line, color?}] with `color` the AUTHORED CSS token (absent =
    // currentColor), turned into this list by
    // DecorationWire.decorationLines(from:) — see its banner for why the
    // token is resolved in the runtime and not in the converter.
    //
    // THREE STATES, all meaningful:
    //   nil   → not a collapsed run (every legacy document):
    //           `decorationRequests` synthesizes this component's own
    //           three flags exactly as before, so the dark-stage 327
    //           captures cannot move.
    //   list  → AUTHORITATIVE: it is the complete line set, the
    //           component's own flags are ignored, and the platform
    //           built-ins are fully suppressed (see `overlayOwns`).
    //   empty → ALSO authoritative, and it says "no lines": paint nothing
    //           AND still suppress the built-ins, or the merged flat bag
    //           repaints what the wire just retired.
    // The full seam is live: extractor `_decorations` → converter
    // `meta.decorations` → IRWireV2Reader → IRMeta.decorations →
    // DecorationWire → here.
    var decorations: [DecorationColorOps.DecorationLine]? = nil

    // Wave 35 (lane B5) — non-nil ⇒ this run is an UPRIGHT vertical run
    // (css-writing-modes-4 §5.1) and the value is the side line 1 stacks on.
    // Decided ONCE at the call site by `VerticalUprightGate.stack`, which
    // reads the MERGED property list; nil (the default, and every call site
    // that does not pass it) keeps the frozen horizontal chain byte for byte.
    var verticalUprightStack: LineStack? = nil

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
        let casedText = TextTransformApplier.renderString(
            visibleText,
            textCase: textConfig.textCase,
            capitalize: textConfig.capitalizeWords)
        // Wave 37 (lane W7, rule A) — `hyphens: none` (css-text-3 §6.1):
        // delete the CONDITIONAL soft hyphens so TextKit cannot break at
        // them. Applied here, in the same "rewrite the string before it
        // is measured" lane as the case fold above, for the same reason:
        // the greedy pre-break must see the exact glyphs that render, and
        // a U+00AD that survived into the measured string is a break
        // opportunity GreedyLineBreaker does not model but TextKit takes.
        // Identity for `manual`/`auto` and for any run without a soft
        // hyphen — i.e. for every committed baseline capture.
        let transformedText = SoftHyphenPolicy.displayString(
            casedText, mode: textConfig.hyphensMode)
        // Lane IOS-TEXT fix 1 — greedy pre-break. Gated on: a known wrap
        // width, wrapping not suppressed (white-space/text-wrap nowrap,
        // css-text-4 §5.1), preserved-whitespace modes OFF (wave 5
        // finding 3 — pre-wrap/break-spaces keep space runs per
        // css-text-3 §4.1.2, and the space-split below would collapse
        // them: a glyph-content rewrite; those modes take the legacy
        // soft-wrap path), and a break opportunity existing at all.
        //
        // Wave 30 (lane LINEBOX) — the closure now also reports WHETHER it
        // ran. A pre-broken string's `\n`s ARE TextKit's line breaks (every
        // line fits `avail` by construction), so downstream the rendered line
        // COUNT is exact and the line box can be pinned; on any early-return
        // path TextKit still owns the breaking and the count is a guess. Only
        // the flag is new — every `return` value below is unchanged.
        //
        // Wave 37 (lane W7, rule B) — and WHETHER any committed line is
        // WIDER than `avail`. GreedyLineBreaker leaves an overlong word
        // alone on its line to overflow (CSS 2.1 §9.5 / css-text-3 §5.2:
        // under `overflow-wrap: normal` a word with no break opportunity
        // overflows the line box), but a Text still constrained to the
        // box width hands that line straight back to TextKit, which
        // emergency-breaks it at a character boundary — the exact defect
        // the wave-36 gate captured across css-text/hyphens (ref keeps
        // "Deoxyribon|ucleic" on ONE overflowing line; iOS rendered three
        // lines and then overflowed the pinned box height, because
        // LineBoxMetrics counted the two lines we committed). The flag
        // feeds the `.fixedSize(horizontal:)` gate below, which is the
        // same vehicle wave 21 built for whole-run unbreakables.
        let broken: (text: String, preBroken: Bool, overlong: Bool) = {
            guard let cb = wrapWidth, !textConfig.noWrap,
                  !textConfig.preservesSpaces,
                  transformedText.contains(" ") else { return (transformedText, false, false) }
            // Text width available inside the label: the content box
            // minus the 4px breathing inset each side (dropped in WPT
            // capture, mirroring the padding gate below) and the
            // text-indent leading pad — both shrink the line box.
            let avail = cb - (wptCaptureMode ? 0 : 8) - (textConfig.textIndentPx ?? 0)
            guard avail > 0 else { return (transformedText, false, false) }
            // Measure with the EXACT resolved render face + spacing so
            // the fit test uses the advances TextKit renders with.
            // Bound ONCE (wave 37) so the overflow probe below re-uses the
            // identical measurer instance the fit test used — two closures
            // would measure the same string through two attribute bags.
            let measure = GreedyLineBreaker.measurer(
                font: measurementUIFont,
                letterSpacingPx: textConfig.letterSpacing,
                wordSpacingPx: textConfig.wordSpacingPx,
                // Wave 34 (lane F1) — measure with the same bundled
                // per-script faces the render installs, or the greedy
                // pre-break would fit non-Latin text against CoreText's
                // cascade advances and break where neither surface wraps.
                scriptFallback: wptCaptureMode)
            let lines = GreedyLineBreaker.lines(
                text: transformedText,
                maxWidth: avail,
                measure: measure)
            // Hard newlines force TextKit to OUR break positions — its
            // push-out strategy only relocates SOFT breaks, and every
            // pre-broken line fits `avail` by construction EXCEPT the
            // overlong-word case rule B names (a single word wider than
            // the box). Probe for it with the same measurer.
            let overlong = GreedyLineBreaker.hasUnbreakableOverflowingLine(
                lines, maxWidth: avail, measure: measure)
            return (lines.joined(separator: "\n"), true, overlong)
        }()
        // The rendered string — identical to the pre-wave-30 `displayText`.
        let displayText = broken.text
        // TITAN Round 4 (GAP 1, height half) — the line-height this run
        // lays out with: the IR-declared value when present (defer to it),
        // else the ref line box (20px — corpus-v4.1) in WPT capture, else nil (SwiftUI
        // natural metrics — the unchanged product path). Feeds BOTH the
        // leading split and the minHeight frame below so the bar height +
        // baselines track the browser-ref's default-font `<p>`.
        // (wave 22, lane FONT — `declaredNormal` routes an explicit
        // `line-height: normal` past the ref-line-box pin to the face's own
        // metrics; see ComponentRenderer.effectiveLineHeight.)
        let effectiveLineHeight = ComponentRenderer.effectiveLineHeight(
            declared: textConfig.lineHeight,
            declaredNormal: textConfig.lineHeightIsNormal,
            // Wave 23 (lane DECOR-INSET) — the CALIBRATED row scales with THIS
            // run's font-size (the ref's unitless 1.25), so a 32px `<h1>` gets
            // a 40pt line box instead of the 16px root's 20pt. 16 is the same
            // default the rest of this label uses for a size-less run.
            fontSizePx: textConfig.fontSize ?? 16,
            wptCaptureMode: wptCaptureMode)
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
        // Wave 21 (lane TEXTDECOR, B-RC7) — unbreakable runs must NOT
        // emergency-wrap in WPT capture. CSS gives a run with no
        // soft-wrap opportunity (UAX #14 approximation:
        // DecorationOps.hasSoftWrapOpportunity — no spaces, no
        // break-after punctuation, no ideographs) ONE overflowing line
        // under `overflow-wrap: normal`; SwiftUI instead character-wraps
        // at the proposal edge. The Chromium refs for
        // text-decoration-dotted-001/002 keep 'fooשלוםbaz' / 'foobarbaz'
        // @92px on ONE line overflowing the 390px canvas — the wrap, not
        // the dots, dominated those scores. `.fixedSize(horizontal:)`
        // below (the wave-2 noWrap machinery) is the entry point; gated
        // on wptCaptureMode so the 327-pair baseline path (flag false)
        // is byte-identical. Twins: Compose gates softWrap under
        // LocalWptComposedMode, web drops its span's hardcoded
        // break-word under WPT_COMPOSED_MODE.
        let wptUnbreakableRun = wptCaptureMode
            && !DecorationOps.hasSoftWrapOpportunity(displayText)
        // Wave 37 (lane W7, rule B) — the SAME contract for a run that
        // DOES have soft-wrap opportunities but still contains one word
        // wider than the box. wave 21's whole-run test misses it (a
        // single space anywhere in the run answers "breakable"), yet the
        // overlong word is just as unbreakable as a whole run with no
        // spaces at all: css-text-3 §5.2 lets it overflow, it must not be
        // emergency-broken. The greedy pre-break already put it alone on
        // its line; this flag is what stops TextKit re-breaking that line
        // at the proposal edge. Gated on `preBroken` because only then do
        // the hard newlines carry OUR break positions — without them
        // `.fixedSize(horizontal:)` would collapse the run to one line —
        // and on `wptCaptureMode` like its wave-21 sibling, so the 327
        // committed baseline captures (where the pre-break also runs, at
        // a 4px-inset `avail`) stay byte-identical.
        let wptOverlongPreBrokenRun = wptCaptureMode
            && broken.preBroken && broken.overlong
        // Wave 30 (lane LINEBOX) — the composed-WPT LINE-BOX PIN, i.e. the
        // Round-4 single-line cap above GENERALIZED to every run whose
        // rendered line count is known. See LineBoxMetrics.pinnedBoxHeight
        // for the defect it repairs (iOS drove the block advance off the
        // face's rounded natural height + the CSS leading, so a `<p>`
        // advanced 36.64px where the ref advances 36.00) and for why the
        // arm is WPT-only. The count is exact when TextKit cannot add lines
        // to what we hand it:
        //   • `singleLineText`  — no whitespace at all, the Round-4 proxy;
        //   • `textConfig.noWrap` / `wptUnbreakableRun` — wrapping is off
        //     (css-text-4 §5.1) or there is no soft-wrap opportunity;
        //   • `broken.preBroken` — the greedy pre-break rewrote every soft
        //     break as a hard one and each line fits `avail` by construction.
        // Anything else (unknown wrap width, preserved-whitespace modes)
        // stays UNPINNED — an under-counted pin would ride the next sibling
        // up, which is worse than the drift it would remove.
        let lineCountIsExact = singleLineText || textConfig.noWrap
            || wptUnbreakableRun || broken.preBroken
        let pinnedBoxHeight = LineBoxMetrics.pinnedBoxHeight(
            lineHeightPx: effectiveLineHeight,
            // `line-clamp` truncates the block to N line boxes (css-overflow-4
            // §5) — the `.lineLimit` below is what TextKit obeys, so the pin
            // must count the same lines it renders, not the pre-broken string.
            lineCount: LineBoxMetrics.renderedLineCount(
                displayText, clampLimit: textConfig.lineClampLimit),
            lineCountIsExact: lineCountIsExact,
            wptCapture: wptCaptureMode)
        // Applier campaign (sub-natural line-height placement) — the
        // signed-half-leading compensation for L < natural content
        // height (LineBoxMetrics.subNaturalOffset header for the full
        // model). Gated OFF for every PINNED box: there the frame is
        // compressed to exactly N × L and the `.center` alignment already
        // overflows the glyph band evenly above/below — i.e. the browser's
        // negative-half-leading placement — so adding the offset would
        // double-shift those calibrated captures. Every other path (the
        // whole product renderer) keeps the frame uncompressed at natural
        // height and needs the explicit translation. 0 whenever L ≥ natural.
        // (Wave 30: the gate reads the PIN instead of `wptCaptureMode &&
        // singleLineText` — the pin is a strict superset of that condition,
        // so the single-line captures keep the identical zero.)
        let subNaturalShift: CGFloat = pinnedBoxHeight != nil
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
            // Wave 35 (lane B5) — the UPRIGHT vertical run. Glyphs stand up
            // and stack DOWN each line; lines stack across the block axis.
            // The layout plans at MEASURE time (the wrap budget is the
            // block-axis proposal) and places the `fallback` slot — the two
            // frozen branches below, verbatim — whenever it declines, so a
            // run this gate admits can never render WORSE than before.
            if let stack = verticalUprightStack {
                VerticalUprightTextFlow(text: displayText, stack: stack) {
                    if let g = clipTextGradient {
                        textView.foregroundStyle(g)
                    } else {
                        textView.foregroundColor(resolvedColor)
                    }
                } glyph: { slot in
                    // One code point, styled exactly like the run it came
                    // from. `wordSpacedText` is identity on a single glyph
                    // (there is no separator to space) but is used anyway so
                    // the two paths build their `Text` the same way.
                    let g = wordSpacedText(slot).font(font)
                    if let grad = clipTextGradient {
                        g.foregroundStyle(grad)
                    } else {
                        g.foregroundColor(resolvedColor)
                    }
                }
            } else if let g = clipTextGradient {
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
            // Wave 22 (B-RC4b): the gate reads the RESOLVED request list
            // (overlayOwns) instead of this component's own flags, so a
            // merged run that inherited an ancestor's underline silences
            // the built-in too. With no merged wire the predicate is
            // identical to ownsUnderline / ownsStrikethrough.
            .modifier(OwnedDecorationSuppressor(
                underline: overlayOwns(.underline),
                strikethrough: overlayOwns(.lineThrough)))
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
            // Wave 19 — honor line-clamp's cap (css-overflow-4 §5) when
            // one is configured: this inner .lineLimit is the one SwiftUI
            // consults (innermost wins over TypographyApplier's outer
            // LineLimitMod, which the wave-19 skeptic proved inert here).
            // nil = no clamp = the historical unlimited wrap.
            .lineLimit(textConfig.lineClampLimit)
            // Wave 21 (B-RC7): OR in the WPT unbreakable-run gate — see
            // the wptUnbreakableRun declaration above for the contract.
            // Wave 37 (lane W7): OR in the overlong-word gate — same
            // contract, per-WORD instead of per-run.
            .fixedSize(horizontal: textConfig.noWrap || wptUnbreakableRun
                        || wptOverlongPreBrokenRun,
                       vertical: true)
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
                   // Wave 30 — a PINNED box sets the floor at its own N × L
                   // (a 2-line pinned run must not floor at one line box);
                   // unpinned keeps the single-line-box floor it always had.
                   minHeight: pinnedBoxHeight ?? effectiveLineHeight,
                   // Round 4 — in composed WPT capture, CAP the box to
                   // exactly N ref line boxes (glyphs overflow like the
                   // browser's line-height:18) so forced-Inter bars stop
                   // drifting; every other path keeps the floor-only frame
                   // (maxHeight nil) so nothing is clipped. Wave 30 widened
                   // the cap from "single-line run" to "known line count" —
                   // see pinnedBoxHeight above; at N = 1 it is the identical
                   // number the Round-4 expression produced.
                   maxHeight: pinnedBoxHeight,
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
        // wave-35 lane B2 — a face this DOCUMENT declared via `@font-face`
        // outranks everything below it. css-fonts-4 §5 consults the document's
        // own font database before any system or generic face, so a test
        // saying `@font-face { font-family: test; src: url(Lin.woff) }` plus
        // `font: 36px test` must shape with THAT file — the bundled-Inter
        // default under it is what made css-text/boundary-shaping's "fi"
        // ligature assertions unobservable on iOS. `fontFaceName` is the
        // PostScript name CoreText reported at registration (StyleBuilder set
        // it via DocumentFontRegistry); nil for every face-free document, so
        // the Inter/system branches below are untouched in the normal case.
        if let declaredFace = textConfig.fontFaceName {
            f = .custom(declaredFace, size: size)
            // Weight is NOT baked: the declared file provides one concrete
            // face, so a `font-weight` on the element still has to be applied
            // by CoreText's synthesis path below — the same treatment the
            // legacy `.custom("Inter")` branch gets. `interFaceName` stays nil
            // on purpose, which routes an italic request to `.italic()` rather
            // than the fixed Inter shear: a declared family MAY ship a real
            // italic face (a second @font-face with `font-style: italic`), and
            // shearing over a real italic would be a visible lie.
        } else if textConfig.fontDesign == .default {
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
        // Wave 34 (lane F1) — PER-SCRIPT FONT FALLBACK. css-fonts-4 §5.2
        // matches a font stack per CHARACTER; `.custom("Inter", size:)`
        // picks ONE face for the whole Text and leaves everything Inter
        // cannot draw to CoreText's opaque default cascade (there is no
        // cascade-list hook on `Font`). That made every Armenian /
        // Arabic-Indic / Bengali / Khmer / Hebrew run resolve Apple's system
        // faces while the browser-ref resolved Chromium-on-macOS's own pick
        // — the Rule-43 typography boundary. ScriptFallbackFonts writes a
        // `.font` attribute per script run over the bundled Noto faces,
        // which is ordinary AttributedString layout. Gated on
        // `wptCaptureMode` (false on every 327-baseline path) AND on the
        // string actually carrying a target-script scalar, so both the
        // no-fallback and the not-in-WPT cases keep the exact pre-lane
        // `Text(String)` / `Text(attr)` they had.
        let scriptFallback = wptCaptureMode && ScriptRunSegmenter.needsFallback(s)
        let size = textConfig.fontSize ?? 16
        // nil = nothing for the kern lane to do (letter-spacing alone
        // stays on the legacy box-level tracking) → plain-string Text,
        // unless the script lane has spans of its own to install.
        guard let attr = WordSpacingApplier.kernedRun(
            text: s,
            letterSpacingPx: textConfig.letterSpacing,
            wordSpacingPx: textConfig.wordSpacingPx) else {
            guard scriptFallback else { return Text(s) }
            return Text(ScriptFallbackFonts.annotate(s, enabled: true, size: size))
        }
        return Text(ScriptFallbackFonts.apply(to: attr,
                                              enabled: scriptFallback,
                                              size: size))
    }

    /// Wave 21 (lane TEXTDECOR, B-RC8) — the pure op emitter's style
    /// for this label's decorations, or nil for the two styles the
    /// overlay does NOT own (`double`/`wavy` — SwiftUI's built-in
    /// pattern rendering still beats what we can hand-paint for them,
    /// so underline/line-through keep the platform built-ins there).
    /// solid/dotted/dashed are owned: DecorationOps paints solid bands,
    /// Chromium round-cap dot runs, and Blink-fitted dash runs
    /// (wave-5's solid-only gate let SwiftUI's `.dot` pattern draw the
    /// dotted tests at built-in geometry — thickness and rhythm both
    /// wrong vs the ref; see DecorationOps' measured pin table).
    private var ownedDecorationStyle: DecorationOps.LineStyle? {
        switch textConfig.decorationStyle {
        case .solid:  return .solid
        case .dotted: return .dotted
        case .dashed: return .dashed
        // Built-ins keep double/wavy underline/line-through.
        case .double, .wavy: return nil
        }
    }

    /// Wave-5 gate follow-up (extended wave 21) — does the owned
    /// overlay draw the underline for this label? Requires the flag
    /// AND an owned style (solid/dotted/dashed — see
    /// ownedDecorationStyle for the double/wavy carve-out).
    private var ownsUnderline: Bool {
        textConfig.underline && ownedDecorationStyle != nil
    }

    /// Same ownership rule for line-through (see ownsUnderline).
    private var ownsStrikethrough: Bool {
        textConfig.strikethrough && ownedDecorationStyle != nil
    }

    /// Wave 22 (lane DECOR, B-RC4b) — the ORDERED per-line decoration
    /// list this label paints, each entry carrying ITS OWN colour
    /// (css-text-decor-3 §2.2: every decorating box paints its line in
    /// its own colour, and §2.1 propagates all of them onto the one
    /// collapsed inline run). `decorations` is the merged
    /// `meta.decorations` wire; nil (every document today, and every run
    /// that was not collapsed) falls back to this component's own three
    /// flags with nil colours — literally the pre-wave-22 behaviour, so
    /// the dark-stage 327 captures cannot move.
    private var decorationRequests: [DecorationColorOps.DecorationLine] {
        DecorationColorOps.resolve(wire: decorations,
                                   underline: ownsUnderline,
                                   overline: textConfig.overline,
                                   lineThrough: ownsStrikethrough)
    }

    /// True when the built-in for `kind` must be SUPPRESSED — i.e. when
    /// this label's decoration is owned by something other than SwiftUI's
    /// `.underline`/`.strikethrough` modifiers.
    ///
    /// TWO regimes, and conflating them was a latent double-draw:
    ///
    ///  • A PRESENT wire owns EVERYTHING, unconditionally. The wire is
    ///    authoritative for the whole run, so no built-in may add to it or
    ///    survive it. Before wave 22's seam landed, this method was gated
    ///    on `ownedDecorationStyle != nil` alone, which meant a merged wire
    ///    combined with `text-decoration-style: double|wavy` (the two
    ///    styles the overlay does not own) left the gate FALSE while the
    ///    overlay's own gate — `!decorationRequests.isEmpty`, and the wire
    ///    makes that non-empty — was TRUE: SwiftUI painted its double
    ///    underline AND the overlay painted a solid one over it. The same
    ///    misalignment swallowed the empty-wire contract: an authoritative
    ///    "no lines" would leave the built-in painting from the merged
    ///    flat bag. Returning true for every kind fixes both at once.
    ///
    ///  • No wire (the legacy path) → unchanged, byte-for-byte: the
    ///    overlay owns a kind only when it is requested AND the style is
    ///    one it can draw (solid/dotted/dashed). double/wavy keep
    ///    SwiftUI's pattern rendering, and `decorationRequests` already
    ///    excludes those kinds there (ownsUnderline/ownsStrikethrough fold
    ///    the style test in), so the overlay never draws them either.
    private func overlayOwns(_ kind: DecorationColorOps.LineKind) -> Bool {
        // Authoritative wire ⇒ total ownership, empty list included.
        if decorations != nil { return true }
        return ownedDecorationStyle != nil && decorationRequests.contains { $0.kind == kind }
    }

    /// One IR colour leaf → a SwiftUI colour in the sRGB space the IR
    /// normalizes to (schema/spec/02-values.md: colours are sRGB 0..1
    /// floats), so a `text-decoration-color` round-trips without a
    /// working-space conversion. Twin of the Compose painter's
    /// `Color(red, green, blue, alpha)` construction.
    private static func decorationColor(_ c: DecorationColorOps.Rgba) -> Color {
        Color(.sRGB, red: Double(c.r), green: Double(c.g), blue: Double(c.b),
              opacity: Double(c.a))
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
        // Wave 22 (B-RC4b): the gate is now the RESOLVED request list —
        // identical to the old `overline || ownsUnderline ||
        // ownsStrikethrough` disjunction whenever no merged wire arrived
        // (DecorationColorOps.resolve synthesizes exactly those flags),
        // and non-empty for a collapsed run whose lines came from
        // ancestors this component never declared itself.
        if !decorationRequests.isEmpty {
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
            // Wave 21 (B-RC8) — explicit `text-decoration-thickness`
            // overrides the auto rule for BOTH the band height and the
            // underline gap below (css-text-decor-4 §2.4; the dotted
            // tests declare 10/20/30px on a 92px face).
            let explicitT = textConfig.decorationThicknessPx
            // Same face + spacing as the render → identical advances.
            let segs = DecorationMetrics.segments(
                lines: lines,
                fontSizePx: textConfig.fontSize ?? 16,
                explicitThicknessPx: explicitT,
                measure: GreedyLineBreaker.measurer(
                    font: measurementUIFont,
                    letterSpacingPx: textConfig.letterSpacing,
                    wordSpacingPx: textConfig.wordSpacingPx,
                    // Wave 34 (lane F1) — measure with the same bundled
                    // per-script faces the render installs, or the greedy
                    // pre-break would fit non-Latin text against CoreText's
                    // cascade advances and break where neither surface wraps.
                    scriptFallback: wptCaptureMode))
            // Line ADVANCE = rendered content height + the CSS leading
            // split's between-lines extra (the label's `.lineSpacing`).
            //
            // KNOWN multi-line drift (wave-21 diagnosis, B-RC8 tail,
            // documented not chased): in composed WPT capture the label
            // pins its LINE BOX via effectiveLineHeight (frame minHeight
            // + `.lineSpacing` = L − content height), but this advance
            // uses measurementUIFont.lineHeight — the resolved RENDER
            // face's content height. When font resolution diverges
            // between measurement and render (e.g. an italic synthetic
            // face) or when the first line box is capped while later
            // lines are not, line 2+ decoration rows can sit a few px
            // off the glyph rows. Single-line labels (the whole wave-21
            // css-text-decor corpus after the B-RC7 no-wrap fix) are
            // unaffected — the drift needs a device capture to pin an
            // exact correction, which this lane cannot run.
            let advance = measurementUIFont.lineHeight + lineSpacing
            // Baseline anchor: the RENDER face's ascent — the same
            // number TextKit lays glyphs out with, so the empirical
            // baseline-relative offsets track any font/size.
            let ascent = measurementUIFont.ascender
            // The decoration fractions scale with the declared size
            // (16 = the label's web-body default, see `font`).
            let fontSize = textConfig.fontSize ?? 16
            // §2.2: decoration-color, initial currentColor → text color.
            // This is the CURRENTCOLOR SUBSTITUTE: a request whose own
            // colour is nil (legacy path, or a wire entry the extractor
            // left uncoloured) paints in it, exactly as before wave 22.
            let color = textConfig.decorationColor ?? resolvedColor
            // The ordered per-line list — ancestor-first, each with its
            // own §2.2 colour (see decorationRequests).
            let requests = decorationRequests
            // The op emitter's style: owned solid/dotted/dashed, or
            // solid for the overline-only double/wavy path (underline/
            // strike keep the built-ins there; an overline in those
            // styles has NO built-in, so paint it solid and SAY so —
            // no silent fallthrough).
            let opStyle = ownedDecorationStyle ?? .solid
            // No silent fallthrough, in BOTH regimes where the overlay
            // draws a style it does not own:
            //   • overline in double/wavy (no built-in exists at all), and
            //   • ANY line of a collapsed run in double/wavy — the wire is
            //     authoritative, so `overlayOwns` suppressed the built-ins
            //     and this pass is the only painter left.
            let _ = (ownedDecorationStyle == nil && (textConfig.overline || decorations != nil))
                && PropertyTracker.logOnce(
                    key: "decoration-overline-style-\(name)",
                    message: "text-decoration-style double/wavy painted solid "
                        + "(no built-in available for an overline, and a "
                        + "collapsed-run wire owns every line — no "
                        + "DecorationOps emitter for double/wavy yet)")
            ZStack(alignment: .topLeading) {
                ForEach(segs, id: \.index) { seg in
                    // This line box's top edge in the text's own space.
                    let lineTop = CGFloat(seg.index) * advance
                    // css-text-decor-3 §2.1 — each requested line paints
                    // independently at its own offset, and §2.2 — in its
                    // OWN colour (nil → the currentColor substitute
                    // above). Request order is ancestor-first, which is
                    // also the ZStack's bottom-to-top order, so a
                    // descendant's line paints OVER an ancestor's where
                    // both land on the same row. (Paint order: §5.1 pins
                    // the per-KIND order; ancestor-vs-descendant within
                    // one kind is the extractor's emission order, which
                    // this ForEach preserves. NOT §2.5 — that section is
                    // `text-underline-position`; the stale citation
                    // survived the wave-22 sweep.) Row selection is
                    // DecorationMetrics.top — a pure dispatch over the
                    // same four rules the three `if` blocks used before
                    // (wave-5 auto rows, wave-21 explicit-thickness rows).
                    // `offset` is the id because the same KIND may legally
                    // appear twice in a merged chain (two nested boxes
                    // both underlining) — keying on kind would drop one.
                    ForEach(Array(requests.enumerated()), id: \.offset) { _, req in
                        decorationRow(
                            seg, style: opStyle,
                            color: req.color.map(Self.decorationColor) ?? color,
                            y: lineTop + DecorationMetrics.top(
                                kind: req.kind, ascentPx: ascent,
                                fontSizePx: fontSize,
                                explicitThicknessPx: explicitT))
                    }
                }
            }
        }
    }

    /// One decoration band: the segment's inked advance × its resolved
    /// thickness, expanded through DecorationOps into the style's op
    /// list (solid → one rect byte-identical to the legacy Rectangle;
    /// dotted → Chromium circle runs; dashed → Blink dash runs), then
    /// aligned to its line by the CSS text-align keyword and offset to
    /// the kind's row (y is relative to the text's top-leading corner;
    /// negative for a first-line overline, which hangs above the line
    /// box like Chromium's ink-overflow paint).
    private func decorationRow(_ seg: DecorationMetrics.Segment,
                               style: DecorationOps.LineStyle,
                               color: Color, y: CGFloat) -> some View {
        // Band-local ops: left=0/top=0 → the emitter's midY lands at
        // ~thickness/2 inside a (width × thickness) canvas.
        let ops = DecorationOps.styleOps(left: 0, top: 0,
                                         width: seg.width,
                                         thicknessPx: seg.thickness,
                                         style: style)
        // SwiftUI Canvas (iOS 16 floor, matches Package.swift): fill
        // each op — antialiased like the browser's decoration paint.
        return Canvas { context, _ in
            for op in ops {
                switch op {
                case let .band(left, top, width, height):
                    // Dash/solid rect run.
                    context.fill(Path(CGRect(x: left, y: top, width: width, height: height)),
                                 with: .color(color))
                case let .dot(centerX, centerY, radius):
                    // Round-cap dot (diameter = thickness).
                    context.fill(Path(ellipseIn: CGRect(x: centerX - radius,
                                                        y: centerY - radius,
                                                        width: radius * 2,
                                                        height: radius * 2)),
                                 with: .color(color))
                }
            }
        }
        // The band frame: this line's inked advance × the resolved
        // thickness — same frame the legacy Rectangle occupied, so
        // alignment/offset behavior is unchanged.
        .frame(width: seg.width, height: seg.thickness)
        // Horizontal placement: shorter lines sit where
        // `.multilineTextAlignment` puts them, so each row spans
        // the text bounds and aligns its rect by the same keyword.
        .frame(maxWidth: .infinity, alignment: decorationRowAlignment)
        // Vertical placement: the kind's row.
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
