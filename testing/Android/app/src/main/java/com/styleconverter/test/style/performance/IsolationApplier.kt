package com.styleconverter.test.style.performance

// Turns IsolationConfig into a Modifier. `isolation: isolate` maps to an
// offscreen-composited graphicsLayer — this matches CSS semantics closely
// enough that mix-blend-mode inside the isolated subtree stops blending
// against ancestors, which is exactly the CSS spec's observable effect.

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Applies CSS `isolation` to a Compose Modifier.
 */
object IsolationApplier {

    /**
     * Add an offscreen compositing layer when the config requests isolation.
     *
     * @param modifier Base modifier
     * @param config Extracted isolation config
     * @return Modifier unchanged (AUTO) or wrapped in an offscreen layer (ISOLATE).
     */
    fun applyIsolation(modifier: Modifier, config: IsolationConfig): Modifier {
        // AUTO is the default — return the modifier untouched to avoid paying
        // for an extra offscreen buffer on every element.
        if (!config.hasIsolation) return modifier
        // ISOLATE — wrap the subtree so blend modes within composite against
        // this layer rather than ancestors. Identical to how we handle
        // `contain: paint` in PerformanceApplier.
        return modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    }
}
