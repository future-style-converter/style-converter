package com.styleconverter.runtime.core.renderer

// RootPseudoBox — the COMPOSE half of the wave-28 lane-PG root-scope
// generated-box path. Paints the box RootPseudoSpec bridged out of the
// body-root's `pseudos` bucket, and pins it to the physical inline-start
// when containment blocks the body's direction propagation.
//
// Scope is deliberately the ROOT bucket only (`component.role ==
// "body-root"`). Ordinary elements' ::before/::after still ride the
// selectors channel through ContentApplier, and `pseudos.marker` still
// belongs to the list path (RenderListItemMarker / meta.markerText) — this
// file must never double-render either, which is why the entry point below
// reads `before`/`after` off a body-root and nothing else.
//
// See RootPseudoSpec.kt for the measured divergence (zero orange px on
// Android/iOS vs the ref's 10 000 at [16,16]-[115,115]) and the spec chain.

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRLog
import com.styleconverter.runtime.core.types.ValueExtractors
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Log tag for this file's unsupported-shape reports. */
private const val TAG = "RootPseudoBox"

/**
 * Read a `Contain` IR leaf as its keyword list — the twin of the Android
 * harness's `containKeywords`, kept here so the runtime can answer the
 * propagation question without a harness round-trip.
 */
internal fun containKeywordsOf(component: IRComponent): List<String>? =
    when (val data = component.properties.firstOrNull { it.type == "Contain" }?.data) {
        // Normal emission: ContainProperty.values serialized as a string array.
        is JsonArray -> data.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        // Defensive: one primitive, possibly a space-separated keyword list.
        is JsonPrimitive -> data.contentOrNull?.trim()?.split(Regex("\\s+"))
        // Objects / null / anything else: not a keyword list ⇒ no containment.
        else -> null
    }

/**
 * The root-scope generated box for one role, or null when this component
 * hosts none. Null for EVERY non-body-root component by design (see the
 * file banner) and for a bucket with nothing paintable in it.
 */
internal fun rootPseudoSpecFor(component: IRComponent, role: String): RootPseudoSpec? {
    // Only the synthetic root component carries a root-SCOPE bucket.
    if (component.role != "body-root") return null
    // No silent fallthrough: a root-scope `::after` would have to CLOSE the
    // flow, which the renderer's single LEADING emitter cannot express (the
    // block/flex/grid branches each end their own children loop). Reported
    // once, from the "before" pass so it fires exactly once per component.
    // TODO(lane PG follow-up): thread a trailing emitter through the
    // branches when a WPT test needs it — the whole css-contain family uses
    // ::before only, so nothing measured is waiting on it today.
    if (role == "before" && component.pseudos?.get("after") != null) {
        IRLog.warn(TAG, "root-scope ::after on '${component.id}' is not rendered (leading emitter only)")
    }
    val bucket = component.pseudos?.get(role) as? JsonObject ?: return null
    val spec = rootPseudoSpec(bucket, role) ?: return null
    // Nothing to draw ⇒ render nothing (never an invisible full-width shim).
    if (!spec.paints) return null
    // A non-block generated box would need inline flow this path cannot
    // model; naming it keeps the gap visible instead of silently boxing it.
    if (!spec.isBlock) {
        IRLog.warn(TAG, "root ::$role is not display:block — rendered as a block box anyway (no inline-flow path)")
    }
    return spec
}

/**
 * Paint one root-scope generated box in the body-root's block flow.
 *
 * @param spec the bridged box (see [rootPseudoSpecFor]).
 * @param pinInlineStart when true the box is placed at the PHYSICAL
 *   inline-start of the root regardless of the ambient layout direction —
 *   the [containmentBlocksDirectionPropagation] verdict. A Compose Column
 *   aligns a narrower child by ITS OWN layout direction, so a `direction:
 *   rtl` body-root would otherwise push the html-owned box flush right,
 *   the same divergence web showed at [116,16]-[215,115].
 */
@Composable
internal fun RootPseudoBox(spec: RootPseudoSpec, pinInlineStart: Boolean) {
    if (pinInlineStart) {
        // Force LTR for the shim so `Alignment.TopStart` below resolves to
        // the PHYSICAL left — the provider must sit OUTSIDE the Box, since
        // an Alignment resolves against the layout direction of the box
        // that applies it, not of its content.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            // Full-width shim: the parent Column's own horizontalAlignment
            // then has no free space left to act on, so the pin holds for
            // every ambient direction. (A flex/grid body-root hosting a
            // root-scope pseudo has no corpus coverage; it would need the
            // flex path instead of this block shim.)
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
                RootPseudoBoxBody(spec)
            }
        }
    } else {
        // Uncontained body ⇒ the body's direction really does propagate to
        // the root, so the ambient placement stands (and no capture moves).
        RootPseudoBoxBody(spec)
    }
}

/**
 * The box itself: used size, background color layer, literal content.
 * Split from the placement wrapper so both branches paint identically.
 */
@Composable
private fun RootPseudoBoxBody(spec: RootPseudoSpec) {
    // css-sizing-3 §5 used size. CSS px map 1:1 to dp on the composed WPT
    // canvas (the same assumption WPT_CANVAS_FRAME_DP encodes); an `auto`
    // axis stays unconstrained so the content sizes it.
    var modifier: Modifier = Modifier
    val w = spec.widthPx
    val h = spec.heightPx
    modifier = when {
        w != null && h != null -> modifier.size(w.dp, h.dp)
        w != null -> modifier.width(w.dp)
        h != null -> modifier.height(h.dp)
        else -> modifier
    }
    // css-backgrounds-3 §2.11 color layer, resolved through the SAME literal
    // parser the color-mix path uses — never a second color table here.
    val bg: Color? = spec.backgroundCss?.let { css ->
        ValueExtractors.parseCssColorLiteral(css)
            // No silent fallthrough: a gradient / url() / unknown keyword is
            // named, not quietly dropped to transparent.
            ?: run { IRLog.warn(TAG, "root pseudo background '$css' is not a color literal — not painted"); null }
    }
    if (bg != null) modifier = modifier.background(bg)
    Box(modifier = modifier) {
        // A literal `content` string still has to render; the family's
        // `content: ""` renders nothing. Typography declarations are NOT
        // bridged (rootPseudoSpec logs each one), so this is the platform
        // default face — deliberately visible rather than silently styled.
        if (spec.text.isNotEmpty()) Text(text = spec.text)
    }
}
