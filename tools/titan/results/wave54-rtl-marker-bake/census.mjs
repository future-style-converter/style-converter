#!/usr/bin/env node
// tools/titan/results/wave54-rtl-marker-bake/census.mjs — wave-54 L1's OWN blast-radius census (independent of the
// brief's rtl-marker-bake.census.mjs / padding-census.py: different inputs, different method). Read-only; runs nothing.
//
// Method:
//   1. The bake's reach = every test whose manifest says `bidiBaked: true` in <run>/sections/*/manifest.json (the
//      bake changes NOTHING for a skipped / declined / bailed test: P and M′ edit only the plan of a baked test, and the
//      only new bail they could cause is the run budget — counted in step 4).
//   2. For each baked test, the LIVE extracted fixture (fixtures/wpt/<section>/<stem>.json — written by the last gate,
//      wave54-open, whose per-test IR is byte-identical to wave53-final; sha1 + mtime recorded) gives the bake roots
//      (`_lossyReasons` ∋ 'baked-bidi-visual-order'), their tag, authored padding / guard keys, and every list item in
//      scope. P reaches a root whose RESOLVED padding is non-zero: authored non-zero padding, OR a UA padding the
//      authored props do not zero (ol/ul/menu/dir: padding-inline-start 40px, HTML §15.3.8) — flagged separately.
//   3. M′ reaches a NON-root list item (`_tag` li, or authored display list-item) inside a root, not hidden, whose
//      effective list-style-type (own → ancestors → UA ol decimal / ul disc) is not `none` and has no image — bullets
//      included (planMarker bakes any marker; only the counter-style bake skips bullets).
//   4. Run budget: runs + 3 marker runs per M′ item ≤ MAX_BIDI_RUNS (300).
//   5. Cross-check against <run>'s per-test IR: each P root's Padding* data and each M′ item's meta.markerText +
//      Position; the id shadow = later tests of the same section's tests.list (the converter's id counter continues).
//   6. Today's cells (tools/titan/results/wave54-plan/cells-<run>.json) for every reached capture.
// Usage: node census.mjs [run=wave53-final]  → prints the census; writes census.<run>.json beside this file.
import { readFileSync, existsSync, statSync, readdirSync, writeFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, '..', '..', '..', '..');
const RUN = process.argv[2] ?? 'wave53-final';
const SEC = join(ROOT, 'tools', 'titan', 'runs', RUN, 'sections');
const cells = JSON.parse(readFileSync(join(ROOT, 'tools', 'titan', 'results', 'wave54-plan', `cells-${RUN}.json`), 'utf8')).cells;
const STAMP = 'baked-bidi-visual-order';
const UA_LIST = { ol: 'decimal', ul: 'disc', menu: 'disc', dir: 'disc' };
// A CSS length list with any non-zero number (e.g. "0 3em", "10px", "0 0.5ch").
const nonZero = (v) => typeof v === 'string' && /(^|[\s(])-?(\d*\.)?\d*[1-9]/.test(v.replace(/\b0+(\.0+)?(px|em|ch|rem|%)?\b/g, ''));
const out = { run: RUN, baked: [], P: [], Pua: [], Mprime: [], budget: [], shadow: [], notes: [] };
for (const section of readdirSync(SEC).sort()) {
  const mf = join(SEC, section, 'manifest.json');
  if (!existsSync(mf)) continue;
  const res = JSON.parse(readFileSync(mf, 'utf8')).wpt?.results ?? {};
  const list = readFileSync(join(SEC, section, 'tests.list'), 'utf8').trim().split('\n');
  for (const [test, r] of Object.entries(res)) {
    if (!r.bidiBaked) continue;
    const rel = test.replace(/^css\//, '').replace(/\.html?$/, '');
    const [sec, ...rest] = rel.split('/');
    const stem = rest.join('__');
    const fxPath = join(ROOT, 'fixtures', 'wpt', sec, `${stem}.json`);
    const buf = readFileSync(fxPath);
    const fx = JSON.parse(buf);
    const irName = `wpt__${rel.replaceAll('/', '__')}`;
    const ir = JSON.parse(readFileSync(join(SEC, section, 'per-test-ir', `${irName}.json`), 'utf8'));
    // IR ids carry the converter's `-<n>` counter; a TOP-level component is re-keyed `wpt__<section>__<stem>__<i>`
    // by its POSITION i in the fixture's components map (a `__body` entry takes index 0), children keep their ids.
    const irById = new Map(ir.components.map((c) => [c.id.replace(/-\d+$/, ''), c]));
    Object.keys(fx.components ?? {}).forEach((k, i) => irById.set(k, irById.get(`wpt__${sec}__${stem}__${i}`)));
    const doc = { test, section, irName, scoreEligible: r.scoreEligible, fixtureSha1: createHash('sha1').update(buf).digest('hex').slice(0, 12),
      fixtureMtime: statSync(fxPath).mtime.toISOString(), components: ir.components.length, roots: [], items: [] };
    // Walk the fixture: root → its subtree, carrying the effective list-style-type.
    const visit = (node, id, inherited, rootId, parentTag) => {
      const p = node.properties ?? {}, tag = node._tag?.toLowerCase() ?? null;
      const isRoot = (node._lossyReasons ?? []).includes(STAMP);
      const lst = p['list-style-type'] ?? p['list-style'] ?? (tag && UA_LIST[tag]) ?? inherited;
      if (isRoot) {
        const pads = Object.entries(p).filter(([k]) => k === 'padding' || k.startsWith('padding-'));
        const authoredNonZero = pads.some(([, v]) => nonZero(String(v)));
        const guard = Object.entries(p).filter(([k, v]) => /^(overflow|background-clip|background-origin)/.test(k)
          && (/content-box/.test(String(v)) || (k.startsWith('overflow') && !/^visible$/.test(String(v)))));
        const uaPad = !!(tag && UA_LIST[tag]) && !pads.some(([k]) => ['padding', 'padding-left', 'padding-inline-start', 'padding-right'].includes(k));
        const irc = irById.get(id);
        const irPad = (irc?.properties ?? []).filter((q) => /^Padding/.test(q.type)).map((q) => `${q.type}=${JSON.stringify(q.data)}`);
        doc.roots.push({ id, tag, pads: Object.fromEntries(pads), authoredNonZero, uaPad, guard: guard.map(([k, v]) => `${k}:${v}`), irPad });
        if ((authoredNonZero || uaPad) && !guard.length) (authoredNonZero ? out.P : out.Pua).push({ test, id, tag, pads: Object.fromEntries(pads), irPad });
      } else if (rootId && (tag === 'li' || p.display === 'list-item') && p.display !== 'none') {
        const img = p['list-style-image'] && p['list-style-image'] !== 'none';
        const irc = irById.get(id);
        const item = { id, tag, parentTag, lst, img, position: p.position ?? null,
          irMarkerText: irc?.meta?.markerText ?? null, irPosition: (irc?.properties ?? []).find((q) => q.type === 'Position')?.data ?? null };
        doc.items.push(item);
        if (lst !== 'none' || img) out.Mprime.push({ test, ...item });
      }
      for (const [cid, c] of Object.entries(node.children ?? {})) visit(c, cid, lst, isRoot ? id : rootId, tag);
    };
    for (const [cid, c] of Object.entries(fx.components ?? {})) visit(c, cid, null, null, null);
    const runsM = /\[bidi-bake: baked — (\d+) roots, (\d+) runs\]/.exec(readFileSync(join(SEC, section, 'extract.log'), 'utf8')
      .split('\n').find((l) => l.includes(`extracted ${test} `)) ?? '');
    doc.logRoots = runsM ? +runsM[1] : null; doc.logRuns = runsM ? +runsM[2] : null;
    const mItems = out.Mprime.filter((m) => m.test === test).length;
    if (doc.logRuns !== null) out.budget.push({ test, runs: doc.logRuns, worstWithMarkers: doc.logRuns + 3 * mItems });
    if (mItems) {
      const idx = list.indexOf(test);
      out.shadow.push({ test, section, later: list.slice(idx + 1).length });
    }
    doc.cells = ['web', 'ios', 'android'].map((pf) => `${pf} ${cells[`${rel}.html ${pf}`] ?? cells[`${rel}.htm ${pf}`] ?? 'unscored'}`);
    out.baked.push(doc);
  }
}
// ── Print ──
console.log(`census over ${RUN}: bidiBaked tests ${out.baked.length}`);
for (const d of out.baked) {
  console.log(`  ${d.test}  (fixture sha1 ${d.fixtureSha1} ${d.fixtureMtime.slice(0, 16)}; IR comps ${d.components}; log ${d.logRoots} roots / ${d.logRuns} runs)  [${d.cells.join(' · ')}]`);
  for (const r of d.roots) console.log(`     root ${r.id} <${r.tag ?? '-'}> pads ${JSON.stringify(r.pads)}${r.uaPad ? ' UA-PADDING' : ''}${r.guard.length ? ` GUARD ${r.guard}` : ''} | IR ${r.irPad.join(' ') || '(no Padding*)'}`);
  for (const i of d.items) console.log(`     item ${i.id} <${i.tag}> in <${i.parentTag}> list-style-type ${i.lst}${i.img ? ' +image' : ''} | IR markerText ${JSON.stringify(i.irMarkerText)} Position ${i.irPosition}`);
}
const docsOf = (xs) => [...new Set(xs.map((x) => x.test))];
console.log(`\nP (authored non-zero padding, unguarded): ${out.P.length} roots in ${docsOf(out.P).length} docs`);
for (const p of out.P) console.log(`  ${p.test} ${p.id} ${JSON.stringify(p.pads)} | IR ${p.irPad.join(' ')}`);
console.log(`P via UA list padding (no authored padding on an ol/ul root): ${out.Pua.length}`);
for (const p of out.Pua) console.log(`  ${p.test} ${p.id} <${p.tag}>`);
console.log(`M′ (non-root list items with a marker inside a root): ${out.Mprime.length} in ${docsOf(out.Mprime).length} docs`);
for (const m of out.Mprime) console.log(`  ${m.test} ${m.id} ${m.lst} | IR markerText ${JSON.stringify(m.irMarkerText)} Position ${m.irPosition}`);
console.log(`run budget: max runs ${Math.max(...out.budget.map((b) => b.runs))}, max with markers ${Math.max(...out.budget.map((b) => b.worstWithMarkers))} (limit 300)`);
for (const s of out.shadow) console.log(`id shadow: ${s.test} → ${s.later} later tests of ${s.section} renumbered (+3 per marker item at most; +6 predicted)`);
writeFileSync(join(HERE, `census.${RUN}.json`), JSON.stringify(out, null, 1));
