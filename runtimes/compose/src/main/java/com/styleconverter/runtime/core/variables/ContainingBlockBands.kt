package com.styleconverter.runtime.core.variables

// Wave 54 (lane L4, unit CBB-android) — does the containing block a box
// publishes for its children exclude its padding and border bands?
//
// CSS 2.1 §10.1 / §10.2: a child's percentage resolves against the containing
// block, i.e. this box's CONTENT box. Under box-sizing: border-box (the dark
// stage's frozen reading) that is the declared size MINUS the bands; under
// content-box (css-sizing-3 §3, the UA initial value the WPT capture
// emulates) it IS the declared size. DynamicValueResolver.childContainingBlock
// subtracted unconditionally while SizingApplier drew the WPT box content-box,
// so every out-of-flow `100%` under a padded/bordered parent came out short by
// the bands: abspos-autopos-*-ltr android painted a 70×80 green in a 100×100
// red box, nested-border-radius-clip-3 a 160×60 green for the ref's 200×100
// (brief tools/titan/results/wave54-plan/compose-wpt-content-box-cb.md §1).

// PropertyTracker: the dark-stage breadcrumb (no silent fallthrough).
import com.styleconverter.runtime.PropertyTracker
// IR model — the rule is pure over the declaration list (JVM-pinned).
import com.styleconverter.runtime.core.ir.IRProperty
// The sizing path's own box-sizing keyword and decoder (one definition).
import com.styleconverter.runtime.sizing.BoxSizingKeyword
import com.styleconverter.runtime.sizing.SizingExtractor

/** The band rule of [DynamicValueResolver.childContainingBlock]. */
object ContainingBlockBands {

    /**
     * Recorded when a dark-stage box declares `content-box` and keeps the
     * frozen border-box reading — the same latent inconsistency, named, not
     * fixed (every committed dark-stage baseline stays byte-identical).
     */
    const val DARK_STAGE_BREADCRUMB = "ContainingBlock[dark-stage-content-box-bands]"

    /**
     * True when the bands are subtracted. The EFFECTIVE box-sizing comes from
     * the sizing path's own decoder and tri-state rule (SizingExtractor →
     * SizingApplier.effectiveBoxSizing: declared wins, unset is content-box
     * under WPT capture and null on the dark stage), so the frame the applier
     * draws and the block its children see cannot disagree.
     */
    fun subtracts(properties: List<IRProperty>, wptCaptureMode: Boolean): Boolean {
        // Only the BoxSizing declarations matter (last one wins, as in the extractor).
        val effective = SizingExtractor.extractSizingConfig(
            properties.filter { it.type == "BoxSizing" }.map { it.type to it.data }, wptCaptureMode,
        ).boxSizing
        // WPT capture: a content-box frame publishes its declared size as is.
        if (wptCaptureMode) return effective != BoxSizingKeyword.CONTENT_BOX
        // Dark stage: frozen subtraction; an explicit content-box is breadcrumbed.
        if (effective == BoxSizingKeyword.CONTENT_BOX) PropertyTracker.markUnhandled(DARK_STAGE_BREADCRUMB)
        return true
    }
}
