#!/usr/bin/env python3
# tools/titan/results/wave54-rtl-marker-bake/unit-Mprime.edits.py — the call-site hunks of unit M′ on tools/titan/bidi-bake.mjs,
# authored as exact-string replacements on the unit-P state (each must match once). The code is wave-53 hunk M's call
# sites (git show b0edb788^:tools/titan/bidi-bake.mjs) with the ONE design change PLAN §2 L1 registers: the marker runs
# are measured from (l.925) and owned by (l.974) the ENCLOSING bake ROOT, not the abspos `<li>`.
import sys
P = 'tools/titan/bidi-bake.mjs'
s = open(P, encoding='utf-8').read()
pairs = [
# 1. Banner: the module contract gains its ::marker clause.
("""//   - NO PSEUDO GEOMETRY, NO PROPAGATED DECORATIONS. `_pseudo` content and
//     `text-decoration-line` propagate from boxes we are about to dissolve;
//     both bail rather than silently drop.
//
""",
"""//   - NO PSEUDO GEOMETRY, NO PROPAGATED DECORATIONS. `_pseudo` content and
//     `text-decoration-line` propagate from boxes we are about to dissolve;
//     both bail rather than silently drop.
//   - ::MARKER GEOMETRY (wave-53 hunk M, re-landed as wave-54 L1 unit M′): a
//     list item the bake turns into a BOX keeps its marker only as baked runs
//     OWNED BY ITS ENCLOSING ROOT — bidi-marker-bake.mjs reads Chromium's
//     marker string/box/glyphs, and the item gets `list-style-type: none`;
//     anything it cannot honour is stamped `marker-not-baked` on the item,
//     never dropped silently.
//
"""),
# 2. The import.
("""import { fixtureStem } from './safe-name.mjs';
""",
"""import { fixtureStem } from './safe-name.mjs';
// Wave-54 L1 unit M′: a list item's ::marker inside a bake root, baked as
// positioned runs (that module's banner: the walker sees text nodes only, so
// the marker's direction-dependent SIDE used to be dropped silently).
import { collectMarkerFacts, planMarker } from './bidi-marker-bake.mjs';
"""),
# 3. The markers are planned before any edit, measured from the ENCLOSING ROOT's padding box.
("""  const plan = { roots: [], boxes: [], hides: [], runs: [] };
  const hidden = new Set();""",
"""  // Unit M′: each kept list-item BOX's ::marker (never a root's, never a
  // hidden element's), planned before any edit so a decline costs nothing but
  // its stamp (bidi-marker-bake.mjs planMarker). Its runs are measured from —
  // and owned by — the ENCLOSING ROOT (roots never nest: selectBakeRoots), so
  // every runtime mounts them through the relative root's positioned-child
  // path, never through the abspos item's own flow (wave54-plan/
  // rtl-marker-bake.md §3: Compose's host-inactive Column loop gave a second
  // run under the `<li>` +20 px and a third no slot at all).
  const markers = new Map();
  for (const e of inScope) {
    const k = e.path.join('.');
    if (isRootPath(e.path) || e.rectCount === 0 || e.tag === 'br') continue;
    const rootPath = rootKeys.find((rp) => isDescendantPath(rp, e.path));
    const m = planMarker(walk.markers?.[k], originOf.get(rootPath.join('.')), runsByPath.get(k)?.[0]);
    if (m) markers.set(k, { ...m, rootPath });
  }

  const plan = { roots: [], boxes: [], hides: [], runs: [] };
  const hidden = new Set();"""),
# 4. A baked marker adds `list-style-type: none` (and any stamp) to its item box.
("""      plan.boxes.push({ path: e.path, props: boxProperties(e.rect, parent) });""",
"""      // Unit M′: a baked marker adds `list-style-type: none` and its stamps.
      const m = markers.get(e.path.join('.'));
      plan.boxes.push({ path: e.path, props: { ...boxProperties(e.rect, parent), ...(m?.boxProps ?? {}) },
        ...(m?.lossy?.length ? { lossy: m.lossy } : {}) });"""),
# 5. The marker runs go to the ROOT, right after the item's own text runs.
("""      plan.runs.push({ ownerPath: e.path, props: runProperties(run, style, originOf.get(key)), text: run.text });
    }
  }""",
"""      plan.runs.push({ ownerPath: e.path, props: runProperties(run, style, originOf.get(key)), text: run.text });
    }
    // Unit M′: the item's marker runs go to its enclosing ROOT, after the
    // root's own runs (pushed when the root itself was visited), so every text
    // run keeps its pre-bake child id and the item keeps ONE child; they append
    // after the root's static children (appendChildComponent numbering).
    for (const r of markers.get(key)?.runs ?? []) plan.runs.push({ ownerPath: markers.get(key).rootPath, props: r.props, text: r.text });
  }"""),
# 6. applyBidiBakePlan: a box's marker stamps reach the fixture component.
("""  for (const { path, props } of plan.boxes) {
    const cmp = componentAtPath(fixture, stem, path);
    if (!cmp) throw new Error(`bidi-bake: no component at ${path.join('.')}`);
    Object.assign(cmp.properties ??= {}, props);
    delete cmp._text;
    touched++;""",
"""  for (const { path, props, lossy } of plan.boxes) {
    const cmp = componentAtPath(fixture, stem, path);
    if (!cmp) throw new Error(`bidi-bake: no component at ${path.join('.')}`);
    Object.assign(cmp.properties ??= {}, props);
    delete cmp._text;
    // Unit M′: a modelled / mismatched / declined marker is stamped LOUDLY.
    if (lossy?.length) {
      cmp._lossy = true;
      cmp._lossyReasons = [...new Set([...(cmp._lossyReasons ?? []), ...lossy])];
    }
    touched++;"""),
# 7. bidiBakeFixture: the marker facts are read after the walk and the mapping check.
("""    if (mismatch) return { status: 'bailed', reason: `element-mapping-mismatch (${mismatch})` };
    const { bail, plan, note } = planBidiBake(walk);""",
"""    if (mismatch) return { status: 'bailed', reason: `element-mapping-mismatch (${mismatch})` };
    // Unit M′: the ::marker facts of every kept list item — CDP string/box +
    // a probe span's glyphs, read AFTER the walk measured everything
    // (bidi-marker-bake.mjs). Empty for a list-free walk.
    walk.markers = await collectMarkerFacts(page, walk.elements, { fixture, html, stem: fixtureStem(testRel) });
    const { bail, plan, note } = planBidiBake(walk);"""),
]
for i, (old, new) in enumerate(pairs):
    n = s.count(old)
    if n != 1: sys.exit(f'pair {i}: found {n} times — nothing written')
    s = s.replace(old, new)
open(P, 'w', encoding='utf-8').write(s)
print(f'{P}: {len(pairs)} unit-M′ hunks applied')
