package com.styleconverter.test.style.borders.radius

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Configuration for border radius on all four corners.
 *
 * CSS border-*-radius accepts *two* values per corner — an x-axis radius
 * followed by a y-axis radius — producing elliptical corners. The IR
 * encodes each corner as {"horizontal": IRLength, "vertical": IRLength}
 * (parsed in src/main/kotlin/app/parsing/css/properties/longhands/borders/radius/).
 *
 * We store each corner as an (x, y) pair of Dp. When x == y the corner is
 * circular (the common case), and [isCircular] is true so the applier can
 * take the fast path using [androidx.compose.foundation.shape.RoundedCornerShape].
 * When x != y the applier uses a custom Shape that draws elliptical corners.
 *
 * Start/end naming aligns with Compose's RTL-aware corner naming:
 *  - topStart = top-left in LTR, top-right in RTL
 *  - topEnd   = top-right in LTR, top-left in RTL
 *  - bottomEnd   = bottom-right in LTR, bottom-left in RTL
 *  - bottomStart = bottom-left in LTR, bottom-right in RTL
 */
data class BorderRadiusConfig(
    // Each corner is a (horizontal, vertical) radius pair. x==y means a
    // circular corner; x!=y means elliptical. Defaults are 0 (square).
    val topStart: Pair<Dp, Dp> = 0.dp to 0.dp,
    val topEnd: Pair<Dp, Dp> = 0.dp to 0.dp,
    val bottomEnd: Pair<Dp, Dp> = 0.dp to 0.dp,
    val bottomStart: Pair<Dp, Dp> = 0.dp to 0.dp
) {
    /**
     * True if any corner has a non-zero radius on either axis. The applier
     * short-circuits when this is false so we don't allocate a Shape for
     * square-cornered elements.
     */
    val hasRadius: Boolean
        get() = topStart.first > 0.dp || topStart.second > 0.dp ||
                topEnd.first > 0.dp || topEnd.second > 0.dp ||
                bottomEnd.first > 0.dp || bottomEnd.second > 0.dp ||
                bottomStart.first > 0.dp || bottomStart.second > 0.dp

    /**
     * True when every corner is circular (x == y on every corner). When
     * true the applier can use RoundedCornerShape instead of a custom Shape.
     */
    val isCircular: Boolean
        get() = topStart.first == topStart.second &&
                topEnd.first == topEnd.second &&
                bottomEnd.first == bottomEnd.second &&
                bottomStart.first == bottomStart.second

    companion object {
        val NONE = BorderRadiusConfig()
    }
}
