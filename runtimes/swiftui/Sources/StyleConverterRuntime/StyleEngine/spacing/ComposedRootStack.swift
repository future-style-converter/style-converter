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
    /// px, through the SAME §8.3.1 classifier the runtime's collapse plan
    /// uses (MarginCollapse.staticVerticalEdges) — so the strip and the
    /// render can never disagree about what a margin is worth. Nil when any
    /// vertical edge is auto / negative / relative / calc (out of the
    /// static scope — the caller bails to the Round 4 behavior).
    static func staticDeclaredEdges(_ properties: [IRProperty])
        -> (top: CGFloat, bottom: CGFloat)? {
        MarginCollapse.staticVerticalEdges(properties)
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
    static func rootStackMargin(tag: String?,
                                declaresTop: Bool,
                                declaresBottom: Bool,
                                staticDeclaredEdges edges: (top: CGFloat, bottom: CGFloat)?)
        -> RootStackMargin {
        // UA defaults for this tag (per-side deferral handled below).
        let ua = vertical(forTag: tag)
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
}
