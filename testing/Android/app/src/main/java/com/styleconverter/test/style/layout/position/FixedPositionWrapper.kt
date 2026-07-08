package com.styleconverter.test.style.layout.position

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex

/**
 * Wrapper that implements CSS `position: fixed` using Compose Popup.
 *
 * ## CSS Behavior
 * In CSS, `position: fixed` positions an element relative to the viewport,
 * meaning it stays in place even when the page scrolls.
 *
 * ## Compose Implementation
 * We use [Popup] which renders content in a separate window above the app's
 * content, simulating viewport-relative positioning.
 *
 * ## Positioning
 * - `top` and `left`: Position from top-left corner
 * - `bottom` and `right`: Calculate offset from opposite edges
 * - When both `top` and `bottom` are set, `top` takes precedence (CSS spec)
 * - When both `left` and `right` are set, `left` takes precedence (CSS spec)
 *
 * ## Limitations
 * - Popup is rendered in a separate window, which may affect some interactions
 * - Some system UI elements may still render above the popup
 * - z-index within fixed elements follows popup stacking, not normal flow
 *
 * ## Usage
 * ```kotlin
 * FixedPositionWrapper(
 *     config = PositionConfig(
 *         type = PositionType.FIXED,
 *         top = 16.dp,
 *         start = 16.dp
 *     ),
 *     modifier = Modifier.background(Color.White)
 * ) {
 *     Text("Fixed content")
 * }
 * ```
 */
@Composable
fun FixedPositionWrapper(
    config: PositionConfig,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current

    // Calculate alignment based on which offsets are set
    val alignment = calculateAlignment(config)

    // Calculate the offset in IntOffset (pixels)
    val offset = with(density) {
        IntOffset(
            x = config.offsetX.roundToPx(),
            y = config.offsetY.roundToPx()
        )
    }

    Popup(
        alignment = alignment,
        offset = offset,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            clippingEnabled = false
        )
    ) {
        Box(
            modifier = modifier.zIndex(config.zIndex),
            content = content
        )
    }
}

/**
 * Simplified fixed position wrapper that renders content at a fixed viewport position.
 *
 * This version takes explicit alignment and offset parameters for simpler use cases.
 */
@Composable
fun FixedPositionWrapper(
    alignment: Alignment = Alignment.TopStart,
    offsetX: Int = 0,
    offsetY: Int = 0,
    zIndex: Float = 0f,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Popup(
        alignment = alignment,
        offset = IntOffset(offsetX, offsetY),
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            clippingEnabled = false
        )
    ) {
        Box(
            modifier = modifier.zIndex(zIndex),
            content = content
        )
    }
}

/**
 * Calculate the alignment based on which offset properties are set.
 *
 * CSS position precedence:
 * - If `top` is set, align to top edge
 * - If `bottom` is set (and no top), align to bottom edge
 * - If `left` is set, align to start edge
 * - If `right` is set (and no left), align to end edge
 */
private fun calculateAlignment(config: PositionConfig): Alignment {
    val vertical = when {
        config.resolvedTop != null -> Alignment.Top
        config.resolvedBottom != null -> Alignment.Bottom
        else -> Alignment.Top // Default to top
    }

    val horizontal = when {
        config.resolvedStart != null -> Alignment.Start
        config.resolvedEnd != null -> Alignment.End
        else -> Alignment.Start // Default to start
    }

    return when {
        vertical == Alignment.Top && horizontal == Alignment.Start -> Alignment.TopStart
        vertical == Alignment.Top && horizontal == Alignment.End -> Alignment.TopEnd
        vertical == Alignment.Bottom && horizontal == Alignment.Start -> Alignment.BottomStart
        vertical == Alignment.Bottom && horizontal == Alignment.End -> Alignment.BottomEnd
        vertical == Alignment.Top -> Alignment.TopCenter
        vertical == Alignment.Bottom -> Alignment.BottomCenter
        else -> Alignment.TopStart
    }
}

/**
 * Check if a component should use fixed position rendering.
 */
fun PositionConfig.isFixed(): Boolean = type == PositionType.FIXED
