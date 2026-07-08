package app.irmodels.properties.layout.flexbox

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class AlignSelfProperty(
    val value: AlignSelf
) : IRProperty {
    override val propertyName = "align-self"

    enum class AlignSelf {
        AUTO, FLEX_START, FLEX_END, CENTER, BASELINE,
        STRETCH, START, END, SELF_START, SELF_END,
        // CSS Anchor Positioning Level 1 §6 — when there is no default
        // anchor in scope (the only case SC supports today, since we have
        // no anchor-positioning runtime), `anchor-center` resolves to
        // `center`. Per-platform Appliers fold ANCHOR_CENTER → CENTER.
        // Mirrors JustifySelfValue.AnchorCenter on the justify-self side.
        ANCHOR_CENTER
    }
}
