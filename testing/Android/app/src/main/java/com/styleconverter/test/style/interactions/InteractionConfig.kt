package com.styleconverter.test.style.interactions

/**
 * Configuration for visibility and interaction-related styling properties.
 *
 * ## Supported Properties
 * - Visibility: visible, hidden, collapse
 * - ContentVisibility: auto, visible, hidden
 * - PointerEvents: auto, none
 * - UserSelect: auto, none, text, all
 * - Cursor: pointer, default, etc.
 * - TouchAction: auto, none, pan-x, pan-y, etc.
 * - Appearance: none, auto, etc.
 *
 * ## Usage
 * ```kotlin
 * val config = InteractionExtractor.extractInteractionConfig(properties)
 * val modifier = InteractionApplier.applyInteraction(Modifier, config)
 * ```
 */
data class InteractionConfig(
    /** CSS visibility property */
    val visibility: VisibilityMode = VisibilityMode.VISIBLE,
    /** CSS content-visibility property */
    val contentVisibility: ContentVisibilityMode = ContentVisibilityMode.VISIBLE,
    /** CSS pointer-events property */
    val pointerEvents: PointerEventsMode = PointerEventsMode.AUTO,
    /** CSS user-select property */
    val userSelect: UserSelectMode = UserSelectMode.AUTO,
    /** CSS cursor property */
    val cursor: CursorType = CursorType.AUTO,
    /** CSS touch-action property */
    val touchAction: TouchActionMode = TouchActionMode.AUTO,
    /** CSS appearance property */
    val appearance: AppearanceMode = AppearanceMode.AUTO,
    /** CSS backface-visibility property */
    val backfaceVisibility: BackfaceVisibilityMode = BackfaceVisibilityMode.VISIBLE
) {
    /** Returns true if any interaction property is non-default */
    val hasInteraction: Boolean
        get() = visibility != VisibilityMode.VISIBLE ||
                contentVisibility != ContentVisibilityMode.VISIBLE ||
                pointerEvents != PointerEventsMode.AUTO ||
                backfaceVisibility != BackfaceVisibilityMode.VISIBLE

    /** Returns true if the element ITSELF should be hidden.
     *
     * `visibility: hidden|collapse` hides the entire element (and its
     * descendants) per CSS Display §11.2. `content-visibility: hidden`
     * is *different*: per CSS Containment 2 §4.3 it skips rendering of
     * the element's CONTENTS while keeping the element's own box (and
     * its background, borders, etc.) visible. So content-visibility
     * must NOT short-circuit the whole element to alpha 0 — doing so
     * was wiping out the bg colour on Android while web kept it.
     */
    val isHidden: Boolean
        get() = visibility == VisibilityMode.HIDDEN ||
                visibility == VisibilityMode.COLLAPSE

    /** Returns true if pointer events are disabled */
    val isPointerDisabled: Boolean
        get() = pointerEvents == PointerEventsMode.NONE
}

/**
 * CSS visibility property values.
 */
enum class VisibilityMode {
    /** Element is visible (default) */
    VISIBLE,
    /** Element is invisible but still takes up space */
    HIDDEN,
    /** Element is invisible and collapses (removes space) - same as hidden for non-table */
    COLLAPSE
}

/**
 * CSS content-visibility property values.
 */
enum class ContentVisibilityMode {
    /** Normal rendering */
    VISIBLE,
    /** Skip rendering of off-screen content */
    AUTO,
    /** Do not render content at all */
    HIDDEN
}

/**
 * CSS pointer-events property values.
 */
enum class PointerEventsMode {
    /** Element responds to pointer events (default) */
    AUTO,
    /** Element ignores pointer events */
    NONE,
    /** SVG only - various SVG-specific values */
    VISIBLE_PAINTED,
    VISIBLE_FILL,
    VISIBLE_STROKE,
    VISIBLE,
    PAINTED,
    FILL,
    STROKE,
    ALL
}

/**
 * CSS user-select property values.
 */
enum class UserSelectMode {
    /** Default selection behavior */
    AUTO,
    /** Text cannot be selected */
    NONE,
    /** Only text can be selected */
    TEXT,
    /** Click selects all content */
    ALL,
    /** Selection contained within element */
    CONTAIN
}

/**
 * CSS cursor property values (common subset).
 */
enum class CursorType {
    AUTO, DEFAULT, NONE, POINTER, HELP, WAIT, PROGRESS,
    CROSSHAIR, TEXT, VERTICAL_TEXT, ALIAS, COPY, MOVE,
    NOT_ALLOWED, GRAB, GRABBING, ZOOM_IN, ZOOM_OUT,
    N_RESIZE, E_RESIZE, S_RESIZE, W_RESIZE,
    NE_RESIZE, NW_RESIZE, SE_RESIZE, SW_RESIZE,
    EW_RESIZE, NS_RESIZE, NESW_RESIZE, NWSE_RESIZE,
    COL_RESIZE, ROW_RESIZE, ALL_SCROLL
}

/**
 * CSS touch-action property values.
 */
enum class TouchActionMode {
    /** Browser handles touch (default) */
    AUTO,
    /** Disable all touch gestures */
    NONE,
    /** Enable panning and pinch zoom */
    MANIPULATION,
    /** Enable horizontal panning */
    PAN_X,
    /** Enable vertical panning */
    PAN_Y,
    /** Enable left panning */
    PAN_LEFT,
    /** Enable right panning */
    PAN_RIGHT,
    /** Enable upward panning */
    PAN_UP,
    /** Enable downward panning */
    PAN_DOWN,
    /** Enable pinch zoom */
    PINCH_ZOOM
}

/**
 * CSS appearance property values.
 */
enum class AppearanceMode {
    /** Default appearance for the element type */
    AUTO,
    /** No special appearance */
    NONE,
    /** Platform-specific text field */
    TEXTFIELD,
    /** Menu list appearance */
    MENULIST,
    /** Button appearance */
    BUTTON
}

/**
 * CSS backface-visibility property values.
 */
enum class BackfaceVisibilityMode {
    /** Back face is visible when rotated */
    VISIBLE,
    /** Back face is hidden when rotated */
    HIDDEN
}

/**
 * Detailed touch action configuration for scroll handling.
 * Can be used by container-level scroll handling.
 */
data class TouchActionConfig(
    val allowPanX: Boolean = true,
    val allowPanY: Boolean = true,
    val allowPinchZoom: Boolean = true,
    val allowAll: Boolean = true
) {
    val hasTouchRestrictions: Boolean
        get() = !allowAll || !allowPanX || !allowPanY || !allowPinchZoom

    companion object {
        val Default = TouchActionConfig()
        val None = TouchActionConfig(
            allowPanX = false,
            allowPanY = false,
            allowPinchZoom = false,
            allowAll = false
        )
        val Manipulation = TouchActionConfig(
            allowPanX = true,
            allowPanY = true,
            allowPinchZoom = true,
            allowAll = false
        )
    }
}
