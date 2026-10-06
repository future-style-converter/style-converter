//
//  ComposedRootStack.swift
//  StyleEngine/spacing — RC-A4 (wave 19): declared-margin collapse between
//  the composed WPT canvas's STACKED ROOTS.
//
//  UABlockMargin (TITAN Round 4) folds the UA-INJECTED block margins of the
//  stacked roots with the CSS 2.1 §8.3.1 max rule — but it DEFERS on any
//  IR-declared side (author > UA), so a declared margin renders in full on
//  the root via MarginApplier. When BOTH adjacent roots declare block
//  margins, the two painted margins STACK where the browser collapses them:
//  flex-abspos-staticpos-align-self-safe-001's four `margin: 20px` flex
//  containers rendered at a 96px pitch (56px box + 20 + 20) vs the ref's
//  collapsed 76px (56 + max(20,20)), drifting every later root +20/40/60px.
//
//  Fix: statically-resolvable declared block margins are folded INTO the
//  same collapsed-gap math as the UA defaults (rootStackMargin below) and
//  STRIPPED from the root's own render through the runtime's §8.3.1
//  override channel (marginCollapseOverride — the exact substitution
//  MarginApplier already performs for collapsing block children), which the
//  composedRootBlockMarginStrip bridge exposes to the harness canvas.
//
//  Everything here is PURE (pinned in ComposedRootStackTests) and
//  byte-parallel with the Kotlin twin (apps/android-harness
//  UaBlockMargins.rootStackMargin) — the shared R1–R7 pin table asserts
//  IDENTICAL expected values on both platforms. ONLY the composed WPT
//  canvas consumes it: the 327-pair dark-stage baseline never calls in.
//

// SwiftUI for the strip bridge's environment write; CGFloat rides along.
import SwiftUI

public extension UABlockMargin {

    /// One stacked root's resolved BLOCK margins for the composed vertical
    /// flow, plus whether the root's DECLARED block margins must be
    /// STRIPPED from its own render (they moved into the collapsed gaps).
    struct RootStackMargin: Equatable {
        /// Effective top margin feeding the collapsed gap ABOVE this root, px.
        public let top: CGFloat
        /// Effective bottom margin feeding the gap BELOW this root, px.
        public let bottom: CGFloat
        /// True → zero the root's block margins on render (inline margins
        /// untouched — §8.3.1 collapses vertical margins only).
        public let stripDeclared: Bool
        /// Wave-19 follow-up (collapse-through): true for a flow-stack root
        /// with ZERO flow footprint — on iOS that is the wave-18 RC1
        /// static-position root the canvas mounts behind StaticPositionAnchor
        /// (0×0 report; canvas-hoisted roots never reach the flow list, so
        /// they collapse through by ABSENCE already). CSS 2.1 §8.3.1
        /// collapses vertical margins THROUGH a box that takes no flow
        /// space ("margins collapse through it" for empty boxes; out-of-flow
        /// boxes are absent from the adjoining chain, §9.3.1) — the fold
        /// keeps the adjoining set OPEN across this root so two 20px
        /// neighbors share ONE 20px gap, not max(20,0)+max(0,20)=40.
        /// Defaulted false: every pre-existing caller/pin stays byte-stable.
        public let marginTransparent: Bool
        /// Public memberwise init (the synthesized one is internal-only).
        public init(top: CGFloat, bottom: CGFloat, stripDeclared: Bool,
                    marginTransparent: Bool = false) {
            self.top = top; self.bottom = bottom; self.stripDeclared = stripDeclared
            self.marginTransparent = marginTransparent
        }
    }

    /// The root's declared (top, bottom) block margins as plain non-negative
    /// px, through the SAME §8.3.1 px classifier the runtime's collapse plan
    /// uses (MarginCollapse.staticEdge, reused inside StaticEmMargin.edgePx)
    /// — so the strip and the render can never disagree about what a margin
    /// is worth. Nil when any vertical edge is auto / negative / % / vw /
    /// calc (out of the static scope — the caller bails to the Round 4
    /// behavior via rootStackMargin's R4/R5 branch).
    ///
    /// Wave 22 (B-RC2): `em` is now RESOLVED rather than bailed, against the
    /// component's OWN declared FontSize (css-values-4 §6.1.1 — the base
    /// rides the same property list). dotted-001's `margin: .5em` over
    /// `font-size: 92px` is 46px per edge, which the fold then collapses to
    /// ONE 46px inter-root gap instead of the two stacked 46s the natives
    /// painted (measured Android inter-div 92px vs the ref's 46px). The
    /// widened classifier is a strict SUPERSET on non-em wires — every
    /// wave-19 R/S/T pin keeps its value — and lives in StaticEmMargins.swift
    /// (byte-parallel with the Kotlin twin apps/android-harness
    /// StaticEmMargins.kt) so the runtime's OWN collapse machinery
    /// (MarginCollapsePlanner / MarginCollapseChildGates) keeps calling the
    /// narrow MarginCollapse.staticVerticalEdges and the 327-pair dark-stage
    /// baseline cannot move.
    ///
    /// Wave 45 (H0): an em margin on a root with NO declared FontSize no
    /// longer bails either — StaticEmMargin.ownFontSizePx resolves the UA
    /// default ladder (MonospaceUAFontSize's 13px fixed default, else 16px),
    /// because the pre-fix bail sent such roots down R4/R5 where the fold
    /// emitted the UA gap AND MarginApplier rendered the full declared
    /// margin — a measured +16px double-space on floats-clear-multicol-002
    /// and discard-multicol-001. Declared var()/calc()/relative sizes still
    /// bail (E4 kept, narrowed).
    static func staticDeclaredEdges(_ properties: [IRProperty])
        -> (top: CGFloat, bottom: CGFloat)? {
        StaticEmMargin.verticalEdges(properties)
    }

    /// Resolve one in-flow root's stack contribution — the Kotlin twin's
    /// rootStackMargin, pin-for-pin (R1–R7 in ComposedRootStackTests).
    ///
    /// - Parameters:
    ///   - tag: the root's source tag (UA default lookup, `vertical(forTag:)`).
    ///   - declaresTop/declaresBottom: whether the IR declares that block
    ///     side (declaresBlockMarginTop/Bottom).
    ///   - staticDeclaredEdges: the classifier's declared px, or nil to bail
    ///     (out-of-scope value flavors, or an OUT-OF-FLOW root — its margins
    ///     never collapse, §8.3.1's in-flow precondition).
    ///   - ownFontSizePx: wave-46 lane H1 (Y8's iOS twin): the root's own
    ///     computed font-size, the em basis of its UA default
    ///     (`UABlockMarginFontBasis.ownFontSizePx`); nil (no own font
    ///     signal — every R1-R7 pin, every corpus root without a FontSize)
    ///     keeps the 16px-root table byte-identical. Rounded to whole px
    ///     by `rootVertical`, as the Kotlin harness table is.
    static func rootStackMargin(tag: String?,
                                declaresTop: Bool,
                                declaresBottom: Bool,
                                staticDeclaredEdges edges: (top: CGFloat, bottom: CGFloat)?,
                                ownFontSizePx: CGFloat? = nil)
        -> RootStackMargin {
        // UA defaults for this tag (per-side deferral handled below),
        // resolved against the root's own font basis when it has one.
        let ua = rootVertical(forTag: tag, ownFontSizePx: ownFontSizePx)
        // R1 — no declared block margin: pure UA contribution (Round 4 as-is).
        if !declaresTop && !declaresBottom {
            return RootStackMargin(top: ua.top, bottom: ua.bottom, stripDeclared: false)
        }
        // R4/R5 — declared but not statically resolvable (or out of flow):
        // today's behavior — declared sides render via MarginApplier
        // (UA zeroed, stacking uncorrected), undeclared sides keep UA.
        guard let e = edges else {
            return RootStackMargin(top: declaresTop ? 0 : ua.top,
                                   bottom: declaresBottom ? 0 : ua.bottom,
                                   stripDeclared: false)
        }
        // R2/R3 — static declared margins: each declared side contributes its
        // DECLARED px to the gap fold (author > UA, css-cascade-4 §6.1) and
        // the root strips; undeclared sides keep the UA default.
        return RootStackMargin(top: declaresTop ? e.top : ua.top,
                               bottom: declaresBottom ? e.bottom : ua.bottom,
                               stripDeclared: true)
    }

    /// Wave 26 (lane RES residual 3a) — the HOIST BAND one composed ROOT will
    /// emit, exposed to the harness so its stack fold can own that spacing
    /// instead of letting it stack on top (see `withHoistBand`).
    ///
    /// PUBLIC because the iOS harness cannot see `MarginCollapse` /
    /// `ComponentStyle` (module-internal); a thin accessor keeps the planner
    /// itself unexported. The style is built through the SAME
    /// `StyleBuilder.build(from:)` the renderer uses, so the number returned
    /// here is the number the renderer would have painted — one decision,
    /// two consumers. Returns (0, 0) whenever no plan exists (childless root,
    /// bailed container, closed edge gates), i.e. "this root emits no band".
    ///
    /// ## Why the component is UNBOXED first
    /// The harness SUPPRESSES the renderer's band unconditionally for every
    /// composed root, so this accessor must return EXACTLY what the renderer
    /// would have painted — under-reporting deletes real spacing just as
    /// over-reporting adds phantom spacing. `ComponentRenderer.init` runs
    /// `ContentsUnboxing.resolve` before anything reads the child list, so a
    /// `display: contents` first child is SPLICED OUT and the plan sees the
    /// grandchild. Planning over the raw component saw the wrapper instead:
    /// `<div><div style="display:contents"><h5></div><p></div>` reported a
    /// 0px band here while the renderer planned 27 (the `<h5>`'s UA margin),
    /// so the suppression deleted 27px the fold never added. Identity for the
    /// whole contents-free corpus (`resolve` returns the same instance).
    ///
    /// The block-only / grid / row-gap pre-conditions need no repetition
    /// here: unlike the Compose twin, `MarginCollapsePlanner.containerPlan`
    /// carries them INSIDE the planner (GATE 1/2/4), so both consumers on
    /// this platform already read one gated number.
    ///
    /// Twin of Compose's `ComponentRenderer.composedRootHoistBand`.
    static func composedRootHoistBand(_ component: IRComponent,
                                      uaBlockMargins: Bool)
        -> (top: CGFloat, bottom: CGFloat) {
        // Same `display: contents` resolution the renderer's init performs.
        let resolved = ContentsUnboxing.resolve(component)
        // Same builder the renderer runs — never a re-derivation.
        let style = StyleBuilder.build(from: resolved.properties)
        guard let plan = MarginCollapse.containerPlan(component: resolved,
                                                      style: style,
                                                      uaBlockMargins: uaBlockMargins)
        else { return (0, 0) }
        return (plan.hoistTop, plan.hoistBottom)
    }

    /// Wave 26 (lane RES residual 3a) — fold a root's HOIST BAND into its
    /// stack contribution. Byte-parallel twin of the Kotlin
    /// `withHoistBand(plan:band:)` in apps/android-harness UaBlockMargins.kt.
    ///
    /// ## The double count
    /// A composed root's outer block spacing had TWO independent owners: this
    /// stack fold (the per-root leading/trailing pads) and the renderer's own
    /// `MarginCollapse.Plan.hoistTop/hoistBottom` band (transparent padding
    /// outside the root's styled box, carrying the first/last child's margin
    /// that escapes through an open parent edge). Both are outer spacing in
    /// the SAME adjoining-margin region, so they ADDED where CSS 2.1 §8.3.1
    /// takes ONE max() over the whole chain: with a previous root ending in a
    /// 40px bottom margin, a `<blockquote>` root (UA 16) whose first child is
    /// an `<h5>` (UA 27) rendered max(40,16) = 40 plus max(16,27) − 16 = 11
    /// → 51px against the browser's max(40, 16, 27) = 40.
    ///
    /// ## The model (ONE owner)
    /// The band is `max(rootOwnEdge, childEdge) − rootOwnEdge`, so
    /// `rootOwnEdge + band` is exactly the root's COLLAPSED-THROUGH edge.
    /// Adding it here lets the existing n-ary max resolve the whole chain,
    /// and the renderer is told to emit no band for this root (the
    /// `hoistBandSuppressedFor` channel). Subtracting the band from the pad
    /// instead CANNOT work: two adjacent band-carrying roots (A's last child
    /// bottom 16, B's first child top 16) would need pad = 16 − 16 − 16 =
    /// −16, which floors at 0 and renders 32.
    ///
    /// Margin-TRANSPARENT roots return unchanged: they occupy no flow space,
    /// so their subtree's band never displaces flow siblings.
    ///
    /// - Parameter band: the root's `(hoistTop, hoistBottom)` from the
    ///   runtime's own `MarginCollapse.containerPlan` — the SAME plan the
    ///   renderer would have painted, never a re-derivation.
    static func withHoistBand(_ plan: RootStackMargin,
                              band: (top: CGFloat, bottom: CGFloat)) -> RootStackMargin {
        // Zero-flow roots never contribute to the stack pads at all.
        guard !plan.marginTransparent else { return plan }
        // Opaque root: its stack edges become the collapsed-through values.
        return RootStackMargin(top: plan.top + band.top,
                               bottom: plan.bottom + band.bottom,
                               stripDeclared: plan.stripDeclared,
                               marginTransparent: false)
    }

    /// Wave-19 follow-up — the ONE §8.3.1 gap fold, now aware of margin-
    /// TRANSPARENT roots (see RootStackMargin.marginTransparent). The Round 4
    /// tuple `stackedSpacing` delegates here with every entry opaque, so the
    /// two entry points can never drift.
    ///
    /// CSS 2.1 §8.3.1 model (byte-parallel with the Kotlin twin's
    /// collapsedRootStackGapsPx — the shared T1–T6 pin table asserts it):
    ///  - Adjoining vertical margins collapse to the MAX of the set
    ///    (positive-only scope; negatives floored at 0 — §8.3.1's negative
    ///    rules are not emulated, same lane boundary as Round 4).
    ///  - A zero-flow-footprint root does NOT close the adjoining set: the
    ///    previous opaque root's bottom margin, every transparent root's own
    ///    (usually zero) margins, and the next opaque root's top margin form
    ///    ONE set resolving to ONE max() gap.
    ///  - Gap PLACEMENT follows §8.3.1's collapse-through position rule
    ///    ("the position of the element's top border edge is the same as it
    ///    would have been if ... its top margin were collapsed only with
    ///    PRECEDING margins"): the leading pad above each transparent root
    ///    is the set's prefix max through its own top margin minus what the
    ///    set already emitted — its slot (= its static-position ink anchor)
    ///    lands exactly where the hypothetical in-flow box would, and the
    ///    pads sum to the full set max by the next opaque root.
    ///  - First/last edges stay preserved (the canvas's 16px padding blocks
    ///    parent↔child collapse), including when the stack starts/ends with
    ///    transparent roots.
    ///
    /// Returns the Round 4 contract shape: index-aligned `leading` pads plus
    /// the `trailing` pad below the last root. The `plans:` label keeps the
    /// Round 4 unlabeled tuple entry point unambiguous at empty-literal call
    /// sites (the Kotlin twin uses a distinct name for the same reason).
    static func stackedSpacing(plans: [RootStackMargin])
        -> (leading: [CGFloat], trailing: CGFloat) {
        // Empty stack: the Round 4 contract's empty shape, unchanged.
        guard !plans.isEmpty else { return ([], 0) }
        // One pad above each root; the trailing pad is computed after.
        var leading = [CGFloat](repeating: 0, count: plans.count)
        // Max of the currently-OPEN adjoining-margin set (§8.3.1 "maximum
        // of the adjoining margin widths"); the canvas padding edge opens
        // the first set with no margin of its own.
        var runningMax: CGFloat = 0
        // Portion of the open set's max ALREADY emitted as pads — nonzero
        // only while collapsing THROUGH transparent roots, so the set's
        // total contribution is emitted exactly once.
        var emitted: CGFloat = 0
        for i in plans.indices {
            // This root's top margin joins the open set (negatives floored).
            runningMax = max(runningMax, max(plans[i].top, 0))
            // Pad above root i: prefix max through its top margin, minus
            // what the set already emitted (collapse-through position rule).
            // Opaque-after-opaque: emitted is 0 → exactly the Round 4
            // collapsed(prev.bottom, own.top).
            leading[i] = runningMax - emitted
            if plans[i].marginTransparent {
                // Zero-flow root: the set stays OPEN (§8.3.1 collapse-
                // through / §9.3.1 absence); its own bottom margin joins it.
                emitted += leading[i]
                runningMax = max(runningMax, max(plans[i].bottom, 0))
            } else {
                // Opaque root: its border box separates the margins — the
                // set closes and a fresh one opens with its bottom margin.
                runningMax = max(plans[i].bottom, 0)
                emitted = 0
            }
        }
        // Trailing pad: whatever the still-open set has not yet emitted —
        // the last opaque root's full bottom margin (padding-preserved edge)
        // less anything emitted through trailing transparent roots.
        return (leading, runningMax - emitted)
    }
}

public extension View {
    /// RC-A4 strip bridge for the composed canvas: when `strip` is true the
    /// hosted ROOT renders with its block margins substituted to 0 through
    /// the runtime's §8.3.1 override channel (marginCollapseOverride — read
    /// by ComponentRenderer, applied by MarginCollapse.applying), because
    /// the declared values now live in the canvas's collapsed gap paddings.
    /// Inline (left/right) margins pass through untouched. The renderer
    /// rewrites the channel for every child, so the strip is root-only.
    /// `strip == false` writes the default nil — byte-identical behavior.
    func composedRootBlockMarginStrip(_ strip: Bool) -> some View {
        environment(\.marginCollapseOverride,
                    strip ? MarginCollapseOverride(top: 0, bottom: 0) : nil)
    }

    /// Wave 26 (lane RES residual 3a) — band-suppression bridge for the
    /// composed canvas: the hosted ROOT with this `id` renders WITHOUT its
    /// §8.3.1 hoist band, because the canvas's stack fold already folded that
    /// band into its gaps (`UABlockMargin.withHoistBand`). Keyed on the id so
    /// the suppression cannot reach a descendant container (hierarchical ids
    /// — see HoistBandSuppressedForKey). Twin of the Compose harness's
    /// `LocalHoistBandSuppressedFor provides root.id`.
    func composedRootHoistBandSuppressed(_ id: String) -> some View {
        environment(\.hoistBandSuppressedFor, id)
    }
}

// ─────────────────────────────────────────────────────────────────────────
// wave-52 lane L2 — the per-root COMPOSED-STACK PLAN, extracted from
// CaptureCanvas.swift's `rootPlans(for:)` closure so it is Catalyst-pinnable
// on the verbatim per-test IR (ComposedRootStackTests U1–U6), byte-parallel
// with the Kotlin twin (apps/android-harness UaBlockMargins.kt
// `composedRootStackPlan`).
// ─────────────────────────────────────────────────────────────────────────
public extension UABlockMargin {

    /// A px leaf that is ABSENT, or present and exactly 0 (`{px:0}` /
    /// `{original:{px:0}}`). A present-but-unresolvable leaf (`auto`, `em`,
    /// `%`) is NOT zero — the predicate below stays conservative on it.
    private static func zeroOrAbsent(_ properties: [IRProperty], _ type: String) -> Bool {
        // Absent ⇒ the initial value (0 for the sizing/edge properties asked).
        guard let data = properties.first(where: { $0.type == type })?.data else { return true }
        // Present: it must resolve to a concrete 0 px.
        let px = data["px"]?.doubleValue ?? data["original"]?["px"]?.doubleValue
        return px == 0
    }

    /// The block-axis edges that must be zero for a box to self-collapse
    /// (CSS 2.1 §8.3.1: "no top or bottom border, no top or bottom
    /// padding") — physical AND the horizontal-tb logical spellings.
    private static let selfCollapseZeroEdges = [
        "PaddingTop", "PaddingBottom", "PaddingBlockStart", "PaddingBlockEnd",
        "BorderTopWidth", "BorderBottomWidth", "BorderBlockStartWidth", "BorderBlockEndWidth",
    ]

    /// wave-52 lane L2 (T2-roots) — CSS 2.1 §8.3.1: "If the top and bottom
    /// margins of a box are adjoining, then its margins collapse through it"
    /// — zero (or auto) height, no in-flow children, no line boxes, no block
    /// border or padding. The composed stack marked only RC1/hoisted roots
    /// margin-transparent, so an EMPTY in-flow root (css-text-decor/
    /// text-decoration-propagation-shadow's post-load `<p>` with `height: 0;
    /// margin: 16px 0`) CLOSED the adjoining set and iOS emitted 16 + 16
    /// where Chrome emits ONE 16-px gap (the underline sat 16 px too low;
    /// wave51-fix ios f 0.9484, web P 1.0000).
    ///
    /// Deliberately NARROW (the blast radius is the whole composed corpus):
    /// not out-of-flow (their transparency is the hoist / static-position
    /// rule), not the `body-root` (its margins are `resolvedMargin`'s — M1),
    /// no text / children / generated content, `Display` absent or BLOCK (a table / flex /
    /// inline box is not a §8.3.1 self-collapsing block), `Height` and
    /// `MinHeight` absent or exactly 0 px, every block padding and border
    /// width absent or exactly 0 px (gradient-hue-direction's baked `<hr>`
    /// has a 1-px inset border; has-style-sharing-002's root has
    /// `padding: 1em` — both stay opaque).
    static func isSelfCollapsingRoot(_ root: IRComponent) -> Bool {
        // Out-of-flow boxes never take part in §8.3.1 here.
        if ComponentRenderer.isOutOfFlow(root) { return false }
        // The synthetic html+body bag is not a stacked block box.
        if root.meta?.role == "body-root" { return false }
        // Any content ⇒ line boxes / in-flow children ⇒ not self-collapsing.
        if let t = root.text, !t.isEmpty { return false }
        if let kids = root.children, !kids.isEmpty { return false }
        // Generated content (`::before` / `::after`) puts line boxes in the
        // box — 35 corpus roots match every other clause and carry a pseudo
        // (counter-name-case-sensitive's `content: "1-5"` divs). Conservative:
        // ANY pseudo payload keeps the root opaque (the wave-51 behaviour).
        if root.pseudos != nil { return false }
        // Only a block box (absent Display = a block-level root in the stack).
        if let d = root.properties.first(where: { $0.type == "Display" })?.data.stringValue,
           d.uppercased() != "BLOCK" { return false }
        // Zero (or absent) height floor and used height.
        guard zeroOrAbsent(root.properties, "Height"),
              zeroOrAbsent(root.properties, "MinHeight") else { return false }
        // No block border, no block padding.
        return selfCollapseZeroEdges.allSatisfy { zeroOrAbsent(root.properties, $0) }
    }

    /// wave-52 lane L2 — one composed FLOW root's stack plan, the pure body of
    /// what `rootPlans(for:)` in CaptureCanvas.swift used to inline (waves
    /// 17 → 46 in its history). Hoisted roots never reach this list
    /// (FixedHoist.split removes them), so two shapes remain:
    ///
    ///  1. RC1 STATIC-POSITION root (absolute, NO inset — the only
    ///     out-of-flow root `split` leaves in the flow) — wave-52 L2 T1:
    ///     (0, 0), transparent, and NOT stripped. Through wave 51 its own
    ///     declared margins were joined into the collapse set and stripped
    ///     from the box ("§8.3.1's empty-box model"), so css-masking/
    ///     clip-path/clip-path-ellipse-006's `margin: 50px` abspos root
    ///     landed at max(UA 16, 50) = 50 below the `<p>` where Chrome puts
    ///     it at 16 + 50 = 66 (16 px too high; wave51-fix ios f 0.9488).
    ///     §8.3.1: an abspos box's margins do not collapse — not even with
    ///     the preceding sibling's bottom margin — and §10.6.4's static
    ///     position is where the box's top MARGIN edge would be, so the
    ///     preceding margin resolves in full ABOVE the slot and the box's
    ///     own margin then offsets its ink. The slot anchor takes the
    ///     neighbours' collapsed gap alone; the box keeps rendering its
    ///     declared margins through MarginApplier (no strip).
    ///  2. IN-FLOW root: the wave-19/22/45/46 plan verbatim (UA + static
    ///     declared edges through the SAME classifier the renderer paints
    ///     with, em basis, hoist band folded in) — plus wave-52 L2 T2-roots:
    ///     a self-collapsing empty root whose block margins are all in the
    ///     fold (stripped, or none declared) is margin-TRANSPARENT, so the
    ///     set stays open across it and its neighbours share ONE max() gap
    ///     (§8.3.1 collapse-through). A root that must render its own
    ///     margins (R4/R5 bail) stays opaque.
    static func composedRootStackPlan(_ root: IRComponent) -> RootStackMargin {
        // Shape 1 — the RC1 static-position slot (wave-52 L2 T1).
        if FixedHoist.rendersInFlowAsStaticPosition(root) {
            return RootStackMargin(top: 0, bottom: 0, stripDeclared: false, marginTransparent: true)
        }
        // wave-46 H1: the UA default's em basis is the root's OWN computed
        // font-size; the tag gates the monospace fixed-default rung.
        let uaBasisPx = UABlockMarginFontBasis.ownFontSizePx(
            root.properties, sourceTag: root.meta?.sourceTag)
        // Which block sides the IR declares.
        let declaresTop = declaresBlockMarginTop(root.properties)
        let declaresBottom = declaresBlockMarginBottom(root.properties)
        // The R1–R5 plan (author > UA per edge, css-cascade-4 §6.1); a
        // flow-sized out-of-flow root (defensive — split hoists them all)
        // bails to R4/R5 exactly as before.
        let base = rootStackMargin(
            tag: root.meta?.sourceTag,
            declaresTop: declaresTop,
            declaresBottom: declaresBottom,
            staticDeclaredEdges: ComponentRenderer.isOutOfFlow(root)
                ? nil : staticDeclaredEdges(root.properties),
            ownFontSizePx: uaBasisPx)
        // wave-52 L2 T2-roots: collapse THROUGH an empty root whose block
        // margins all live in the fold — stripped (R2/R3) or never declared (R1).
        let blockMarginsInFold = base.stripDeclared || (!declaresTop && !declaresBottom)
        let plan = RootStackMargin(
            top: base.top, bottom: base.bottom, stripDeclared: base.stripDeclared,
            marginTransparent: isSelfCollapsingRoot(root) && blockMarginsInFold)
        // wave-26 (lane RES residual 3a): fold in the HOIST BAND the root's
        // own §8.3.1 plan would otherwise paint outside its styled box —
        // from the runtime's own planner, so the number folded here is the
        // number suppressed on the render. Transparent roots return unchanged.
        return withHoistBand(plan, band: composedRootHoistBand(root, uaBlockMargins: true))
    }

    /// IR property types whose mere PRESENCE makes a box paint as its own
    /// layer at CSS 2.1 Appendix E step 8 or later — a z-ordered box or a
    /// stacking context (css-transforms-1 §6, css-masking-1 §1, filter-
    /// effects-1 §2, compositing-1 §3/§4, css-contain-2 §3, css-will-change-1
    /// §2, css-view-transitions-1). Presence alone counts (`opacity: 1` still
    /// blocks): a missed lift keeps the wave-51 VStack order, a wrong lift
    /// paints the wrong box on top. Twin of the Kotlin PAINT_LAYER_TYPES.
    private static let paintLayerTypes: Set<String> = [
        "ZIndex", "Opacity", "Transform", "Translate", "Rotate", "Scale", "Perspective",
        "TransformStyle", "Filter", "BackdropFilter", "ClipPath", "MaskImage", "MixBlendMode",
        "Isolation", "WillChange", "Contain", "ContainerType", "ViewTransitionName",
    ]

    /// True when `c` or any composed descendant paints at Appendix E step
    /// 8+: a positioned box (`position` other than static, css-position-3
    /// §2) or a `paintLayerTypes` layer. Such content follows an earlier RC1
    /// root in TREE order at step 8, so it must stay ABOVE it — which a
    /// VStack-level `.zIndex` on the RC1 root cannot express (it lifts past
    /// the whole later subtree: CSS2/abspos/static-inside-inline-001's
    /// nested abspos green).
    private static func paintsAsLayer(_ c: IRComponent) -> Bool {
        // The root-level Position leaf — the wire is the bare enum string.
        if let p = c.properties.first(where: { $0.type == "Position" })?.data.stringValue?.uppercased(),
           p != "STATIC" { return true }
        // A layer-creating property anywhere on this box.
        if c.properties.contains(where: { paintLayerTypes.contains($0.type) }) { return true }
        // Recurse through the composed subtree.
        return (c.children ?? []).contains(where: paintsAsLayer)
    }

    /// wave-52 lane L2 (T3, skeptic must-fix) — per FLOW root (the
    /// `FixedHoist.split` flow half, the list the composed VStack walks),
    /// whether CaptureCanvas lifts its DRAW above the in-flow roots with
    /// `.zIndex(1)`. CSS 2.1 Appendix E step 8 paints a positioned box with
    /// `z-index: auto` AFTER in-flow, non-positioned content, so css-flexbox/
    /// align-items-007's abspos green must cover the later red `<img>`
    /// (wave51-fix ios f 0.9974, colour-vetoed). Lifted only when:
    ///  1. the root is the RC1 static-position slot
    ///     (`FixedHoist.rendersInFlowAsStaticPosition`);
    ///  2. it declares NO z-index (the reader ComponentRenderer partitions
    ///     positioned children with) — a declared value is its own stacking
    ///     level (step 3 BELOW in-flow blocks when negative, step 9
    ///     otherwise) and an outer `.zIndex` would shadow the box's own. The
    ///     first cut lifted every RC1 root, so 11 corpus tests' red
    ///     `z-index: -1` box (css-tables/height-distribution/extra-height-
    ///     given-to-all-row-groups-001 …) rose above the green it hides behind;
    ///  3. it is a single painted box — no children, text or generated
    ///     content (deliberately NARROW: the one target is such a box; a
    ///     content-bearing root brings its descendants' own paint-order and
    ///     display questions — css-cascade/unset-val-002's `display: unset` span);
    ///  4. no LATER flow root carries step-8+ content (`paintsAsLayer`)
    ///     unless it is itself lifted (equal `.zIndex` keeps tree order) —
    ///     hence the back-to-front walk.
    /// Every unlifted root keeps the wave-51 VStack order, byte-identical.
    /// Twin of the Kotlin harness `composedRootsPaintingAboveFlow`.
    static func composedRootsPaintingAboveFlow(_ flow: [IRComponent]) -> [Bool] {
        // Filled back to front: rule 4 reads the verdicts of LATER roots.
        var lifted = [Bool](repeating: false, count: flow.count)
        for i in flow.indices.reversed() {
            let root = flow[i]
            // Rules 1–3: the RC1 slot, no declared z, a single painted box.
            let candidate = FixedHoist.rendersInFlowAsStaticPosition(root)
                && ItemPlacementExtractor.extract(from: root.properties).paint.zIndex == nil
                && (root.children ?? []).isEmpty && (root.text ?? "").isEmpty
                && root.pseudos == nil
            // Rule 4: every later flow root is lifted too, or plain flow.
            lifted[i] = candidate && !flow.indices.filter { $0 > i }.contains { j in
                !lifted[j] && paintsAsLayer(flow[j])
            }
        }
        // Index-aligned with `flow`, exactly as the VStack walks it.
        return lifted
    }
}

// ─────────────────────────────────────────────────────────────────────────
// wave-52 lane L2 (M1) — the composed canvas's BODY MARGIN, as pure runtime
// helpers so the iOS twin of the web `resolveCanvasMargin` / Compose
// `resolveComposedCanvasMargin` is Catalyst-pinnable (the harness target
// needs a simulator), and so the one-owner strip can rebuild an IRComponent
// (its memberwise init is internal to this module).
// ─────────────────────────────────────────────────────────────────────────
public extension UABlockMargin {

    /// The body-root's resolved margin per PHYSICAL side, in CSS px.
    struct CanvasBodyMargin: Equatable {
        public let top: CGFloat
        public let right: CGFloat
        public let bottom: CGFloat
        public let left: CGFloat
        /// Explicit public init — the synthesized memberwise one is internal.
        public init(top: CGFloat, right: CGFloat, bottom: CGFloat, left: CGFloat) {
            self.top = top; self.right = right; self.bottom = bottom; self.left = left
        }
        /// No body-root, no concrete margin, or a table-internal body.
        public static let zero = CanvasBodyMargin(top: 0, right: 0, bottom: 0, left: 0)
    }

    /// The four physical body-margin longhands the canvas owns.
    private static let canvasMarginTypes = ["MarginTop", "MarginRight", "MarginBottom", "MarginLeft"]

    /// css-display-3 table-internal boxes — CSS 2.1 §8.3: margins do not apply.
    private static let tableInternalDisplays: Set<String> = [
        "TABLE_CELL", "TABLE_ROW", "TABLE_ROW_GROUP", "TABLE_HEADER_GROUP",
        "TABLE_FOOTER_GROUP", "TABLE_COLUMN", "TABLE_COLUMN_GROUP",
    ]

    /// The raw ABSOLUTE-px leaf of one margin longhand (`{px:N}` or the
    /// wrapped `{original:{px:N}}`, the two shapes `resolvedPadding` reads),
    /// or nil when absent or runtime-dependent (`auto`, `em`, `%`, `calc`).
    private static func bodyMarginLeafPx(_ body: IRComponent, _ type: String) -> CGFloat? {
        // The first declaration of that longhand.
        guard let data = body.properties.first(where: { $0.type == type })?.data else { return nil }
        // `{px:N}` first, then the typed wrapper; anything else is not concrete.
        guard let px = data["px"]?.doubleValue ?? data["original"]?["px"]?.doubleValue else { return nil }
        return CGFloat(px)
    }

    /// wave-52 L2 (M1) — the body-root's DECLARED margin. The extractor
    /// emits html+body as ONE synthetic `body-root` whose element children
    /// are SIBLINGS, and the canvas read it for background and PADDING only,
    /// so `body { margin-left: 200px }` never moved the flow stack
    /// (css-gaps/flex/flex-gap-decorations-027: 200 px left on every
    /// platform; wave51-fix ios f 0.9030) while the ref keeps it (its
    /// zero-specificity `:where(html, body) { margin: 0 }` loses to the
    /// author's rule). Concrete px only, per side, negatives kept (CSS allows
    /// negative margins); `auto`/`em` → 0. CSS 2.1 §8.3: a table-internal
    /// body (s-11-1-1b-005's `display: table-cell`) has no used margin → zero.
    static func canvasBodyMargin(_ components: [IRComponent]) -> CanvasBodyMargin {
        // Same lookup rule as the background/padding resolvers.
        guard let body = components.first(where: { $0.meta?.role == "body-root" }) else { return .zero }
        // §8.3 applicability: no used margin on a table-internal box.
        if let d = body.properties.first(where: { $0.type == "Display" })?.data.stringValue,
           tableInternalDisplays.contains(d.uppercased()) { return .zero }
        // Per side; absent / unresolvable ⇒ 0.
        return CanvasBodyMargin(top: bodyMarginLeafPx(body, "MarginTop") ?? 0,
                                right: bodyMarginLeafPx(body, "MarginRight") ?? 0,
                                bottom: bodyMarginLeafPx(body, "MarginBottom") ?? 0,
                                left: bodyMarginLeafPx(body, "MarginLeft") ?? 0)
    }

    /// wave-52 L2 (M1) — ONE OWNER for the body margin. The body-root also
    /// stacks as the first composed root, and its declared block margins fed
    /// the root-stack fold — so once the canvas pads the flow stack by the
    /// margin, leaving them on that root applies it TWICE (collapsed-border-
    /// *-rtl-overflow: the fold's 60 + 60 put the table at 136 where the ref
    /// has 76). Returns `roots` with exactly the sides `canvasBodyMargin`
    /// resolved removed from the body-root; `auto`/`em` sides stay on the
    /// box. Identity (the same array) when the canvas owns no margin.
    static func withCanvasOwnedBodyMargin(_ roots: [IRComponent],
                                          _ margin: CanvasBodyMargin) -> [IRComponent] {
        // Nothing owned ⇒ untouched roots (byte-identical captures).
        guard margin != .zero else { return roots }
        return roots.map { root in
            // Only the synthetic html+body bag is rewritten.
            guard root.meta?.role == "body-root" else { return root }
            // The longhands the resolver read (a concrete px leaf on that side).
            let owned = Set(canvasMarginTypes.filter { bodyMarginLeafPx(root, $0) != nil })
            // Rebuild minus those declarations; id/meta/children ride verbatim.
            return IRComponent(
                id: root.id, name: root.name,
                properties: root.properties.filter { !owned.contains($0.type) },
                selectors: root.selectors, media: root.media,
                children: root.children, slot: root.slot,
                text: root.text, pseudos: root.pseudos,
                meta: root.meta, variables: root.variables)
        }
    }
}

// wave-52 lane L2 (T6) — the UA block margin of a CANVAS-HOISTED root.
public extension UABlockMargin {

    /// A hoisted root (fixed, or absolute with an inset — the roots
    /// `FixedHoist.split` moves to the overlay) keeps its tag's UA block
    /// margin. CSS 2.1 §9.3.2 / §10.6.4: `top` offsets the box's MARGIN edge,
    /// so an abspos `<p style="top:0">` paints its border box 1em below the
    /// containing block's edge. The composed canvas emits UA block margins
    /// ONLY through the root-stack fold, which hoisted roots never enter, so
    /// such a root rendered with NO margin: CSS2/css21-errata/s-11-1-1b-006's
    /// prose sat 16 px high (wave51-fix ios f 0.9321; web P — the browser
    /// applies the UA sheet). The overlay gets the UA margin as two ordinary
    /// declared longhands, which it already renders like any author margin.
    /// Identity unless the root hoists AND its tag has a UA block margin AND
    /// it declares no block margin (author > UA, css-cascade-4 §6.1). Census
    /// over the 1435 wave51-fix docs: ONE carrier (006 root 3). Kotlin twin:
    /// apps/android-harness UaBlockMargins.kt `withUaBlockMarginOnHoistedRoot`.
    static func withUaBlockMarginOnHoistedRoot(_ root: IRComponent) -> IRComponent {
        // Only the overlay's roots: out of flow and NOT the RC1 static slot.
        guard ComponentRenderer.isOutOfFlow(root),
              !FixedHoist.rendersInFlowAsStaticPosition(root) else { return root }
        // Any author block margin wins over the UA sheet.
        guard !declaresBlockMarginTop(root.properties),
              !declaresBlockMarginBottom(root.properties) else { return root }
        // The tag's UA block margin at the root's own em basis (wave 46 H1).
        let tag = root.meta?.sourceTag
        let ua = vertical(forTag: tag, ownFontSizePx: UABlockMarginFontBasis.ownFontSizePx(
            root.properties, sourceTag: tag))
        // A tag with no UA block margin (div, span, …) is untouched.
        guard ua.top != 0 || ua.bottom != 0 else { return root }
        // Two absolute-px longhands in the wire's own leaf shape (`{"px": N}`).
        let added = [IRProperty(type: "MarginTop", data: .object(["px": .double(Double(ua.top))])),
                     IRProperty(type: "MarginBottom", data: .object(["px": .double(Double(ua.bottom))]))]
        // Rebuild with the two longhands appended; every other field verbatim.
        return IRComponent(
            id: root.id, name: root.name, properties: root.properties + added,
            selectors: root.selectors, media: root.media,
            children: root.children, slot: root.slot,
            text: root.text, pseudos: root.pseudos,
            meta: root.meta, variables: root.variables)
    }
}
