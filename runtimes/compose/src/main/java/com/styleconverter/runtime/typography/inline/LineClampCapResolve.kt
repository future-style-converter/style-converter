// typography/inline — wave 50 (lane B9): the COMPONENT-AWARE line-clamp cap
// resolver — the seam between the wave-41 uniform cap
// (`scrolling/LineClampCap.capPx`, a property-list function) and the
// wave-50 cross-block census in this folder. Twin of the iOS
// `extension LineClampCap { resolveCapPx(component:…) }` that lives in
// `StyleEngine/scrolling/LineClampRootMetrics.swift`; on Compose it is an
// extension declared here so `scrolling/LineClampCap.kt` stays inside the
// ≤200-line file target.
//
// WIRING: the production call site is ComponentRenderer, which is the only
// place holding both the component and the resolved property list. That
// two-file seam (ComponentRenderer + StyleApplier.applyProperties's
// `lineClampCapPx` parameter) ships as
// `tools/titan/results/wave50-B9/compose-lineclamp-census-seam.patch`
// because both files are outside lane B9's ownership — until it is
// applied this resolver is reachable only from LineBoxCensusTest, and the
// renderer keeps taking the wave-41 uniform `LineClampCap.capPx`.
package com.styleconverter.runtime.typography.inline

// The clamp root as it will be composed — its `runs` and `children` ARE
// the census's input, and they exist nowhere in a property list.
import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.renderer.composedDefaultLineHeightPx
import com.styleconverter.runtime.scrolling.LineClampCap
import com.styleconverter.runtime.typography.TypographyExtractor
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull

/**
 * The cap height in px for this clamp root, or null when no fixed-count
 * clamp is on the wire.
 *
 * ## Why the component and not just its properties
 * css-overflow-4 §5.2 counts LINE BOXES, and a line box is as tall as the
 * run that produced it (CSS 2.1 §10.8). `LineClampCap.capPx` budgets
 * N × the ROOT's line box, which is exact only while every line box in the
 * container is the root's — a block child carrying its own `font` /
 * `line-height`, or an independent formatting context whose lines are not
 * counted at all (css-overflow-3 §3), closes the clamp at a different
 * edge. Those children live on the component, so the decision has to be
 * made where the component is.
 *
 * ## Safety contract: the census can only move what it PROVES
 * Every branch that is not a proven `Capped` verdict returns the
 * byte-identical wave-41 number, and so does every container whose runs
 * are uniform on the root's line box. Two consequences, both deliberate:
 *  • the Swift twin drops the cap entirely on its `unbounded` verdict;
 *    Compose does NOT — the cap node also carries the block-axis ink clip
 *    that makes the discarded lines unpaintable (§5.3), and 24 of the 38
 *    fixed-count clamp roots in the wave49-final corpus land on an
 *    unprovable soft-wrapping text run, all but one of them PASSING on
 *    Android;
 *  • an explicit `height:` on the root is not gated here either: the cap
 *    modifier coerces its result back into the incoming constraints, so a
 *    declared used-height already wins over max-lines (block-ellipsis-007
 *    / -008 / -009 keep their `height: 50/90px` boxes AND their clip).
 *
 * @param component the clamp root as composed (`runs` + `children`).
 * @param properties the root's inheritance-merged property pairs — the
 *   very list `capPx` and the leaf Text are resolved from.
 */
fun LineClampCap.resolveCapPx(
    component: IRComponent,
    properties: List<Pair<String, JsonElement?>>,
): Float? {
    // No fixed-count clamp on the wire → no cap at all. The overwhelmingly
    // common path, one list scan (the same gate capPx opens with).
    val lines = linesCount(properties) ?: return null
    // The wave-41 answer, and the value every unproven branch returns.
    val uniform = capPx(properties) ?: return null
    // A childless container has no cross-block content by construction —
    // skip the walk entirely (18 of the corpus's 38 fixed-count roots).
    if (component.children.isNullOrEmpty()) return uniform
    // The root's metrics off the SAME TypographyExtractor fold capPx uses,
    // so the census inherits exactly what the root lays out with: the
    // monospace-UA 13px quirk and the multiplier × font-size resolution are
    // already folded in, both in sp, and the runtime's px==sp==dp space
    // makes `.value` the px count.
    val typo = TypographyExtractor.extractTypographyConfig(properties)
    val fontPx = typo.fontSize?.takeIf { it.isSp }?.value ?: 16f
    // One line box: a DECLARED line-height verbatim (css-inline-3 §4.2),
    // else the composed-WPT default ratio × this run's size.
    val lineBox = typo.lineHeight?.takeIf { it.isSp }?.value
        ?: composedDefaultLineHeightPx(composedWpt = true, fontSizePx = fontPx)
    // A non-positive box can only come from a broken wire — capPx already
    // refused it, so this is belt-and-braces before dividing the budget.
    if (lineBox <= 0f) return uniform
    // How the root's line-height INHERITS decides each child's box: a
    // unitless multiplier inherits as the NUMBER and recomputes against the
    // child's own size, a length inherits as that length (CSS 2.1 §10.8).
    // Read off the RAW wire because the extractor has already resolved the
    // multiplier to px for the root's own label.
    val declared = properties.lastOrNull { it.first == "LineHeight" }?.second
    val multiplier = (declared as? JsonObject)?.get("multiplier")
        ?.let { (it as? JsonPrimitive)?.floatOrNull }?.takeIf { it > 0f }
    val root = LineBoxRootMetrics(
        fontSizePx = fontPx,
        lineBoxPx = lineBox,
        // The extractor's enum name ("PRE_WRAP"); the census normalises the
        // underscore and hyphen spellings alike.
        whiteSpace = typo.whiteSpace?.name,
        declaredLineHeightPx = if (multiplier == null) typo.lineHeight?.value else null,
        declaredMultiplier = multiplier,
    )
    // The container's in-flow content, in the order the renderer paints it.
    val runs = LineBoxCensusRuns.runs(component, root)
    // Uniform content → N × the root's box IS the Nth line box's edge
    // whatever the counts are: keep the calibrated number untouched.
    if (!LineBoxCensusRuns.hasNonUniformContent(runs, root)) return uniform
    return when (val verdict = LineBoxCensus.verdict(lines, runs)) {
        // Proven: the Nth line box's bottom edge, plus the container's own
        // bands (which nest inside the node this cap measures).
        is LineBoxCensus.Verdict.Capped -> verdict.contentPx + ownBandsPx(properties)
        // Proven short — nothing to discard. The uniform cap is already a
        // no-op on content that fits (cappedHeight keeps any measurement
        // within slack), so returning it changes no pixel and keeps the
        // block-axis ink clip the §5.3 discard rule needs.
        LineBoxCensus.Verdict.Short -> uniform
        // Not proven: never guess an edge — an under-count would clip a
        // visible line. Breadcrumbed the way the other inline-run readers
        // breadcrumb (this is a per-INSTANCE fact about one document, not a
        // per-PROPERTY coverage gap, so it must not enter PropertyTracker's
        // property report), guarded because the JVM suite has no
        // android.util.Log — the same runCatching guard InlineRunPlan uses.
        LineBoxCensus.Verdict.Unprovable -> {
            runCatching {
                android.util.Log.w(
                    "LineClampCap",
                    "line-clamp census could not prove line box #$lines for " +
                        "'${component.name}' (non-uniform content, unprovable run) — " +
                        "keeping the uniform ${uniform}px cap",
                )
            }
            uniform
        }
    }
}
