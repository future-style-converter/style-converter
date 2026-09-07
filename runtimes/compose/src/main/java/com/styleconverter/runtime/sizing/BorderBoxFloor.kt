package com.styleconverter.runtime.sizing

// BorderBoxFloor — retro R2 (A4#7), the Compose twin of
// runtimes/swiftui/.../StyleEngine/sizing/BorderBoxFloor.swift (wave 40, T7).
//
// css-ui-3 §2.1 `box-sizing: border-box`: "The content width and height are
// calculated by subtracting the border and padding widths of the respective
// sides from the specified width and height properties. As the content width
// and height cannot be negative, this computation is floored at 0." A
// declared size SMALLER than its own padding+border bands therefore does not
// shrink the box — it yields a zero-content box whose used border-box size
// IS the band sum. (The iOS twin's header cites "css-ui-3 §5"; the box-sizing
// clause is §2.1 "Changing the Box Model: the box-sizing property".)
//
// ## The defect this closes (measured, not theoretical)
// wave49-final css-ui/box-sizing-026 — `#test { box-sizing: border-box;
// border: 50px green solid; width: 10px; height: 10px }` over an abspos
// `z-index: -1` red 100×100 square. The ref (and web, PASS 0.999) paints a
// solid 100×100 GREEN square: 10px floors up to the 100px band sum and the
// green border fills it, hiding the red. Android took the 10px literally
// (SizingApplier → exactWidth(10)/height(10), border painted inside a 10×10
// frame) so the red showed through: android-ref 0.9966, `colorFailed: true`,
// wptPass false — the identical shape the iOS floor fixed at wave 40 (iOS
// 0.9974 PASS since). PNG looked at: a 10×10 green dot in the top-left of the
// red square.
//
// ## Trigger scope, enumerated rather than assumed (same rule as the twin)
// ONLY an EXPLICIT `box-sizing: border-box` arms the floor. SizingConfig.
// boxSizing is a tri-state: null = "the IR declared nothing", which the
// dark-stage chain treats as border-box for FRAME purposes while the CSS
// default is content-box — and WPT capture resolves null to CONTENT_BOX
// (SizingApplier.effectiveBoxSizing), whose inflation path is the correct
// treatment there. Flooring the null case would inflate content-box-sized
// boxes; the twin measured a nil-inclusive predicate firing on 46 passing iOS
// cells. Executed scan of all 30 wave49-final sections' per-test IR for
// explicit BORDER_BOX + Exact size below the band: exactly ONE carrier
// (box-sizing-026's `#test`), a failing one. Dark-stage fixtures (converter
// IR of every non-WPT fixtures/** doc, 435 scanned, fixtures/visual-test.json
// included): zero carriers — the only under-band explicit border-box in the
// tree is the fixtures/wpt/css-ui mirror of that same test.
//
// ## Why in SizingExtractor and not SizingApplier
// The floor needs the padding AND used-border bands; SizingExtractor already
// computes exactly those for the content-box inflation
// (`contentBoxInflation`, with the CSS 2.1 §8.5.3 `border-style: none` →
// used width 0 gate), so the floor reuses that one helper and the Applier
// stays a pure SizingConfig → Modifier function.

import com.styleconverter.runtime.core.types.LengthValue

internal object BorderBoxFloor {

    /**
     * The floored declared length for one axis.
     *
     * @param declared the axis's declared LengthValue, or null when absent.
     * @param bandPx that axis's padding + used-border sum, in px.
     * @return the value to use. Only an [LengthValue.Exact] declaration BELOW
     *   the band sum changes — everything else (auto, intrinsic, percent,
     *   calc, fraction, none, null) is returned verbatim, because a floor
     *   needs a definite number on both sides to be a floor and not a guess
     *   (a percent resolves later against a containing block this helper
     *   never sees).
     */
    internal fun floored(declared: LengthValue?, bandPx: Float): LengthValue? {
        // No band to floor against (the overwhelming case: no border, no
        // padding) — the identity keeps every existing capture bit-stable.
        if (bandPx <= 0f) return declared
        // Only a definite px declaration is comparable.
        val exact = declared as? LengthValue.Exact ?: return declared
        // At or above the band the content box is ≥ 0 — nothing to floor.
        if (exact.px >= bandPx.toDouble()) return declared
        // Below the band: content floors at 0, the border box IS the band.
        return LengthValue.Exact(bandPx.toDouble())
    }

    /**
     * Apply the floor to an extracted config. Fires only on an EXPLICIT
     * [BoxSizingKeyword.BORDER_BOX] (see the header's scope note); every
     * other config — including every config that declared no box-sizing —
     * is returned unchanged. Logical slots (inlineSize/blockSize) are not
     * touched because SizingExtractor already folded them into the physical
     * width/height slots before this runs.
     */
    internal fun apply(cfg: SizingConfig, bandX: Float, bandY: Float): SizingConfig {
        if (cfg.boxSizing != BoxSizingKeyword.BORDER_BOX) return cfg
        return cfg.copy(
            width = floored(cfg.width, bandX),
            height = floored(cfg.height, bandY),
        )
    }
}
