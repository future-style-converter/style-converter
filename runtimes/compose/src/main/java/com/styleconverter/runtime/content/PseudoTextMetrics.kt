package com.styleconverter.runtime.content

// PseudoTextMetrics — wave-42 lane W1: the PURE text-metric bottom-outs a
// ::before/::after run needs to sit on the SAME line grid as its sibling
// text in WPT capture. Split from ContentApplier so the decision is
// JVM-unit-pinnable (PseudoTextMetricsTest) and the applier file does not
// grow past the repo's size rule.
//
// WHY: the pseudos-bucket channel (PseudoBucketExtractor) carries no typed
// properties, so extractTextStyle leaves every field Unspecified and the
// pseudo Text fell to Compose's Material defaults (~14sp, theme ink,
// face-metric line box) while the sibling item text renders through
// PlaceholderContent's browser bottom-outs (16sp, WPT black ink, the
// calibrated 1.25 line box in composed capture). Same-line runs MUST agree
// on the line grid — the exact rule ListMarkerLineBox states for ::marker,
// and this helper reuses that shared resolution verbatim so pseudo and
// marker can never drift apart.

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.styleconverter.runtime.core.renderer.WPT_DEFAULT_TEXT_INK
import com.styleconverter.runtime.lists.ListMarkerLineBox

object PseudoTextMetrics {

    /**
     * The style a pseudo-element text run paints with.
     *
     * OUTSIDE WPT capture this is the identity function — the legacy
     * selectors channel (the only pre-wave-42 way into PseudoElement) keeps
     * its historical Material-default rendering, so every committed
     * dark-stage baseline is byte-identical by construction.
     *
     * IN WPT capture the three browser bottom-outs apply, each only when
     * the extracted style left the field Unspecified (a declared value
     * always wins, author > UA):
     *  - font-size → 16sp (the browser's inherited body default — the same
     *    bottom-out PlaceholderContent's effectiveFontSize applies);
     *  - color → the WPT CanvasText black ([WPT_DEFAULT_TEXT_INK], the
     *    same ink defaultTextInk hands every default-ink run);
     *  - line-height → [ListMarkerLineBox.resolve]'s three-state pick
     *    (declared wins / declared-`normal` keeps face metrics / nothing
     *    declared takes the calibrated ref box — 20px @16px composed).
     *
     * @param base the extracted style (typed properties → TextStyle).
     * @param declaredNormal did the cascade DECLARE `line-height: normal`
     *   (css-fonts-4 §4.3)? Threaded to the shared line-box resolution.
     * @param wptCapture value of LocalWptCaptureMode at the call site.
     * @param composedWpt value of LocalWptComposedMode at the call site.
     */
    fun parityStyle(
        base: TextStyle,
        declaredNormal: Boolean,
        wptCapture: Boolean,
        composedWpt: Boolean
    ): TextStyle {
        // Identity outside capture — see the KDoc's byte-stability claim.
        if (!wptCapture) return base
        // Font size: a declared value wins; otherwise the browser default.
        val fontSize = if (base.fontSize != TextUnit.Unspecified) base.fontSize
        else ListMarkerLineBox.DEFAULT_FONT_SIZE_SP.sp
        return base.copy(
            fontSize = fontSize,
            // Ink: a declared color wins; the WPT default prose ink is the
            // UA CanvasText black (the ref injects `body { color:#000 }`).
            color = if (base.color == Color.Unspecified) WPT_DEFAULT_TEXT_INK
            else base.color,
            // Line box: the SAME shared three-state resolution the ::marker
            // takes, so pseudo, marker and item text share one line grid.
            lineHeight = ListMarkerLineBox.resolve(
                declaredLineHeight = base.lineHeight,
                fontSize = fontSize,
                declaredNormal = declaredNormal,
                wptCapture = true,
                composedWpt = composedWpt
            )
        )
    }
}
