#!/usr/bin/env python3
# tools/titan/results/wave54-table-body-cell/seam1-hunk.py — builds the post-image of the ONE ComponentRenderer.kt hunk
# of seam-1.patch (PLAN.md §3 row ":2653-2697 TableApplier.Table(…)", lane L2 · unit TB-android) from the base bytes.
# Why a script: the seam file is never edited in the tree (PLAN §0); the patch is cut from HEAD bytes + this exact edit,
# so a re-cut on a moved base is one re-run. Usage: python3 seam1-hunk.py <base ComponentRenderer.kt> <out path>
import sys

src = open(sys.argv[1]).read()

# (1) the chrome decision, computed right before the call — the :2670 expression moves here VERBATIM.
OLD_CALL = """                TableApplier.Table(
                    config = tableConfig,
"""
NEW_CALL = """                // Wave 54 (lane L2, TB-android) — the chrome of the table box
                // (table/TableCellHug.kt): TableBodyForest's synthetic
                // ANONYMOUS table paints no demo cell stroke (CSS 2.1
                // §17.2.1, §17.6.1) and sizes its cells at max-content
                // (§17.5.2.2). Every other table gets `fabricatedDefault`
                // back as its stroke — the wave-38 expression below, moved
                // here verbatim — and hug = false, so it is byte-identical.
                val chrome = com.styleconverter.runtime.table.TableCellHug.chrome(
                    component,
                    fabricatedDefault = !(LocalWptCaptureMode.current &&
                        component.properties.none { it.type == "Display" } &&
                        com.styleconverter.runtime.table.TableBoxTree
                            .uaRoleOf(component._tag) ==
                            com.styleconverter.runtime.table.TableBoxTree.Role.TABLE)
                )
                TableApplier.Table(
                    config = tableConfig,
"""
assert src.count(OLD_CALL) == 1, 'anchor TableApplier.Table( matched %d' % src.count(OLD_CALL)
src = src.replace(OLD_CALL, NEW_CALL)

# (2) the stroke argument reads the decision (the wave-38 comment above it stays: it is the fabricatedDefault's why).
OLD_STROKE = """                    // decision can never disagree about where this table box
                    // came from.
                    fabricatedCellBorder = !(LocalWptCaptureMode.current &&
                        component.properties.none { it.type == "Display" } &&
                        com.styleconverter.runtime.table.TableBoxTree
                            .uaRoleOf(component._tag) ==
                            com.styleconverter.runtime.table.TableBoxTree.Role.TABLE),
"""
NEW_STROKE = """                    // decision can never disagree about where this table box
                    // came from. (Wave 54: that predicate is `chrome`'s
                    // fabricatedDefault above; only an anonymous table
                    // turns it off.)
                    fabricatedCellBorder = chrome.stroke,
"""
assert src.count(OLD_STROKE) == 1, 'anchor fabricatedCellBorder matched %d' % src.count(OLD_STROKE)
src = src.replace(OLD_STROKE, NEW_STROKE)

# (3) the hug argument, after shrinkToFit (the table's own §17.5.2 width), before the modifier.
OLD_HUG = """                            composedCapture = LocalWptComposedMode.current
                        ),
                    modifier = modifier
                ) {
                    RenderTableContent(component, textColor)
"""
NEW_HUG = """                            composedCapture = LocalWptComposedMode.current
                        ),
                    // Wave 54 (lane L2) — §17.5.2.2: the anonymous table's
                    // columns take their max-content widths (TableCellHug).
                    cellsHugContent = chrome.hug,
                    modifier = modifier
                ) {
                    RenderTableContent(component, textColor)
"""
assert src.count(OLD_HUG) == 1, 'anchor cellsHugContent matched %d' % src.count(OLD_HUG)
src = src.replace(OLD_HUG, NEW_HUG)

open(sys.argv[2], 'w').write(src)
