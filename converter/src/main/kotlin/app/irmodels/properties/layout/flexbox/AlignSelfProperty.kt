package app.irmodels.properties.layout.flexbox

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

// `align-self` as a typed IR keyword (css-align-3 §6.1). Serialized as the
// bare enum name ({"type":"AlignSelf","data":"CENTER"}); the wire schema has
// no AlignSelf enum (schema/ir-v2.schema.json), so new keywords are additive
// and every runtime tolerates an unknown string (schema/spec/05-versioning.md).
@Serializable
data class AlignSelfProperty(
    // The single resolved keyword.
    val value: AlignSelf
) : IRProperty {
    // The CSS longhand this property models.
    override val propertyName = "align-self"

    enum class AlignSelf {
        // §6.1 keywords plus the §4.3 <self-position> set.
        AUTO, FLEX_START, FLEX_END, CENTER, BASELINE,
        STRETCH, START, END, SELF_START, SELF_END,
        // CSS Anchor Positioning Level 1 §6 — when there is no default
        // anchor in scope (the only case SC supports today, since we have
        // no anchor-positioning runtime), `anchor-center` resolves to
        // `center`. Per-platform Appliers fold ANCHOR_CENTER → CENTER.
        // Mirrors JustifySelfValue.AnchorCenter on the justify-self side.
        ANCHOR_CENTER,
        // Wave 52 (lane L7, T3) — css-align-3 §6.1 `normal`, previously a
        // Generic passthrough the web runtime dropped.
        NORMAL,
        // Wave 52 (lane L7, T3) — css-align-3 §4.2 `last baseline` (fallback
        // alignment `safe self-end`). `first baseline` is BASELINE.
        LAST_BASELINE
    }
}
