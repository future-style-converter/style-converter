#!/usr/bin/env node
// census-f1.mjs — lane L11 (F1) corpus census over the 1435 wave51-fix
// per-test IR docs: every component whose EFFECTIVE (post-reset, post-merge)
// property list differs between the pre-wave-52 native rule (any `All` in the
// merged list ⇒ drop everything) and the order-aware AllReset — carriers AND
// their descendants (the inherited channel now survives inherit/unset/revert).
// It also lists the web key-order change (where `all` used to be emitted).
//
// Model (mirrors ComponentRenderer.kt RenderComponent + seam-1, and the iOS
// twin): own list → RC6 strip for an unboxable `display: contents` element
// (ContentsUnboxing.isUnboxable: Display CONTENTS, no non-static Position —
// own list reduced to inherited types, which removes `All`) → reset → merge
// (own wins per type) → published channel = effective ∩ INHERITED types. The
// inherited-type set is READ from ComponentRenderer.kt (no copy to drift).
//
// Usage: node census-f1.mjs [out.json]   (read-only over tools/titan/runs/)
import { readdirSync, readFileSync, existsSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, '..', '..', '..', '..');
const RUN = join(ROOT, 'tools', 'titan', 'runs', 'wave51-fix', 'sections');
const out = process.argv[2] ?? join(HERE, 'census-f1.json');

// INHERITED_PROPERTY_TYPES, parsed from the Compose source of truth.
const cr = readFileSync(join(ROOT, 'runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt'), 'utf8');
const block = cr.slice(cr.indexOf('INHERITED_PROPERTY_TYPES: Set<String> = setOf('), cr.indexOf('"AccentColor"') + 14);
const INHERITED = new Set([...block.replace(/\/\/.*$/gm, '').matchAll(/"([A-Za-z]+)"/g)].map((m) => m[1]));
const EXEMPT = new Set(['Direction', 'UnicodeBidi']);                  // css-cascade-4 §3.1
const KEEPS = new Set(['INHERIT', 'UNSET', 'REVERT', 'REVERT_LAYER']);  // §7.3.2–§7.3.5

/** The keyword of an `All` entry (primitive payload), else null — Compose keywordOf. */
const kw = (p) => (p.type === 'All' && (typeof p.data === 'string' || typeof p.data === 'number'))
  ? String(p.data).toUpperCase().replace(/-/g, '_') : null;

/** The order-aware reset (AllReset.apply). */
function reset(own, inh) {
  let i = -1;
  own.forEach((p, k) => { if (kw(p) !== null) i = k; });
  if (i < 0) return { own, inh };
  const k = kw(own[i]);
  return {
    own: [...own.slice(0, i).filter((p) => EXEMPT.has(p.type)), ...own.slice(i + 1).filter((p) => p.type !== 'All')],
    inh: KEEPS.has(k) ? inh : inh.filter((p) => EXEMPT.has(p.type)),
  };
}
/** mergeInherited: own wins per type (the Color unset/inherit nuance is irrelevant here). */
const merge = (own, inh) => { const d = new Set(own.map((p) => p.type)); return [...inh.filter((p) => !d.has(p.type)), ...own]; };
/** ContentsUnboxing.isUnboxable (wave-52 L3: Float no longer blocks). */
const unboxable = (props) => props.some((p) => p.type === 'Display' && p.data === 'CONTENTS') &&
  !props.some((p) => p.type === 'Position' && p.data !== 'STATIC');
const sig = (list) => list.map((p) => `${p.type}=${JSON.stringify(p.data)}`).join('|');

// wave51-fix cells by "<section>/<manifest key>".
const CELLS = new Map();
for (const sec of readdirSync(RUN)) {
  const m = join(RUN, sec, 'manifest.json');
  if (!existsSync(m)) continue;
  for (const [key, v] of Object.entries(JSON.parse(readFileSync(m, 'utf8')).wpt?.results ?? {})) {
    const cells = {};
    for (const p of ['web', 'ios', 'android']) {
      const d = v.browserRef?.diffs?.[`${p}-ref`];
      if (d) cells[p] = `${d.wptPass ? 'P' : 'f'} ${d.ssim}`;
    }
    CELLS.set(`${sec}/${key.replace(/^css\/[^/]+\//, '').replace(/\.html?$|\.xht(ml)?$|\.svg$/, '')}`, { key, cells });
  }
}

let docs = 0;
const tests = [];
for (const sec of readdirSync(RUN)) {
  const dir = join(RUN, sec, 'per-test-ir');
  if (!existsSync(dir)) continue;
  for (const f of readdirSync(dir).filter((x) => x.endsWith('.json'))) {
    docs++;
    const raw = readFileSync(join(dir, f), 'utf8');
    if (!raw.includes('"All"')) continue;                              // cheap pre-filter
    const comps = JSON.parse(raw).components ?? [];
    const byId = new Map(comps.map((c) => [c.id, c]));
    const pubOld = new Map(), pubNew = new Map(), rows = [];
    // Flat list is parent-before-child (slot order); resolve parents first.
    const resolve = (c) => {
      if (pubNew.has(c.id)) return;
      const pid = c.slot?.parent;
      if (pid && byId.has(pid)) resolve(byId.get(pid));
      const inhOld = pid ? pubOld.get(pid) ?? [] : [];
      const inhNew = pid ? pubNew.get(pid) ?? [] : [];
      let own = c.properties ?? [];
      const rc6 = unboxable(own);
      if (rc6) own = own.filter((p) => INHERITED.has(p.type));          // RC6 self-strip
      // Old: the post-merge drop-everything.
      const mOld = merge(own, inhOld);
      const effOld = mOld.some((p) => kw(p) !== null) ? [] : mOld;
      // New: reset on own + channel, then merge.
      const r = reset(own, inhNew);
      const effNew = merge(r.own, r.inh);
      pubOld.set(c.id, effOld.filter((p) => INHERITED.has(p.type)));
      pubNew.set(c.id, effNew.filter((p) => INHERITED.has(p.type)));
      if (sig(effOld) !== sig(effNew)) {
        const allIdx = (c.properties ?? []).findLastIndex((p) => kw(p) !== null);
        rows.push({ component: c.id, tag: c.meta?.sourceTag ?? null, text: c.text ?? null, rc6,
          carrier: allIdx >= 0, keyword: allIdx >= 0 ? kw(c.properties[allIdx]) : null,
          before: allIdx >= 0 ? c.properties.slice(0, allIdx).map((p) => p.type) : null,
          after: allIdx >= 0 ? c.properties.slice(allIdx + 1).map((p) => p.type) : null,
          oldEffective: effOld.map((p) => p.type), newEffective: effNew.map((p) => p.type) });
      }
    };
    comps.forEach(resolve);
    const carriers = comps.filter((c) => (c.properties ?? []).some((p) => kw(p) !== null));
    if (!carriers.length) continue;
    const id = `${sec}/${f.replace(/^wpt__[^_]+(?:-[^_]+)*__/, '').replace(/__/g, '/').replace(/\.json$/, '')}`;
    // Web: `all` used to be the LAST key; now FIRST, with the before-list filtered out.
    const webKeysBefore = carriers.map((c) => c.properties.slice(0, c.properties.findLastIndex((p) => kw(p) !== null))
      .filter((p) => !EXEMPT.has(p.type)).length);
    tests.push({ test: id, doc: `${sec}/per-test-ir/${f}`, cells: CELLS.get(id)?.cells ?? null,
      carriers: carriers.length, webDroppedBeforeAll: webKeysBefore.reduce((a, b) => a + b, 0),
      webAfterKeptAfterAll: carriers.map((c) => c.properties.length - 1 - c.properties.findLastIndex((p) => kw(p) !== null)),
      nativeChangedComponents: rows });
  }
}
const summary = {
  generated: new Date().toISOString(), run: 'wave51-fix', perTestIrDocs: docs,
  inheritedTypes: INHERITED.size, testsWithAll: tests.length,
  allCarrierComponents: tests.reduce((n, t) => n + t.carriers, 0),
  testsWithNativeChange: tests.filter((t) => t.nativeChangedComponents.length).length,
  nativeChangedComponents: tests.reduce((n, t) => n + t.nativeChangedComponents.length, 0),
  rc6StrippedCarriers: tests.reduce((n, t) => n + t.nativeChangedComponents.filter((r) => r.rc6).length, 0),
};
writeFileSync(out, JSON.stringify({ ...summary, tests }, null, 1) + '\n');
console.log(JSON.stringify(summary));
for (const t of tests) {
  console.log(`${t.test}  carriers=${t.carriers} nativeChanged=${t.nativeChangedComponents.length} ` +
    `webDroppedBeforeAll=${t.webDroppedBeforeAll} cells=${JSON.stringify(t.cells)}`);
  for (const r of t.nativeChangedComponents) {
    console.log(`   ${r.carrier ? r.keyword : 'descendant'} ${r.tag ?? ''} ${JSON.stringify(r.text ?? '').slice(0, 30)} ` +
      `old=[${r.oldEffective.join(',')}] new=[${r.newEffective.join(',')}]`);
  }
}
