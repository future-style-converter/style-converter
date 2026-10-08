#!/usr/bin/env node
// tools/titan/results/wave54-plan/web-root-separator.census.mjs
//
// Census for queue-scout-text-web.md §B (web-only fails). READ-ONLY over a
// gate run's per-test IR + scored cells (the scorer's own loader).
//
// Question: which documents carry two ADJACENT ROOT components (no `slot`,
// i.e. children of the composed canvas, not of a component) where the
// earlier one is stamped `meta.role: "ws-after"` and BOTH are inline-level
// (sourceTag in the web harness's INLINE_LEVEL_SOURCE_TAGS, or a declared
// inline* Display)? Those are exactly the pairs between which the web
// composed root list (ComposedCaptureGallery.tsx `canvasRoots.map` /
// `flowRoots.map`) emits NO separator, while ComponentRenderer.tsx's
// `renderChildSeparator` would emit ' ' for the same pair one level down.
//
// Usage: node web-root-separator.census.mjs [run-id=wave53-final]
import fs from 'node:fs';
import path from 'node:path';
import { loadRun, resolveRunDir } from '../../score-gate.mjs';

const runId = process.argv[2] ?? 'wave53-final';
const runDir = resolveRunDir(runId);
const run = loadRun(runDir);
// Byte-copy of apps/web-harness/src/sdui/ComponentRenderer.tsx INLINE_LEVEL_SOURCE_TAGS (wave53-final tree).
const INLINE = new Set(['span', 'a', 'b', 'i', 'em', 'strong', 'code', 'small', 'sub', 'sup',
  'u', 's', 'q', 'abbr', 'cite', 'time', 'label', 'mark', 'bdi', 'bdo',
  'samp', 'kbd', 'var', 'img', 'input', 'select', 'button', 'textarea',
  'output', 'meter', 'progress', 'ruby', 'rt', 'rb']);
const displayOf = (c) => {
  const p = (c.properties ?? []).find((q) => q.type === 'Display');
  if (!p) return null;
  const d = p.data; return String(typeof d === 'string' ? d : (d?.keyword ?? d?.type ?? '')).toLowerCase();
};
const inlineLevel = (c) => {
  const d = displayOf(c);
  if (d) return d.startsWith('inline');
  return INLINE.has((c.meta?.sourceTag ?? '').toLowerCase());
};
function keyIndex(sec) {
  const m = JSON.parse(fs.readFileSync(path.join(runDir, 'sections', sec, 'manifest.json'), 'utf8'));
  const idx = new Map();
  for (const k of Object.keys(m.wpt?.results ?? {})) idx.set('wpt__' + k.replace(/^css\//, '').replace(/\.[a-z]+$/, '').split('/').join('__'), k);
  return idx;
}
const out = [];
for (const sec of Object.keys(run.sections).sort()) {
  const irDir = path.join(runDir, 'sections', sec, 'per-test-ir');
  if (!fs.existsSync(irDir) || !run.sections[sec].hasManifest) continue;
  const idx = keyIndex(sec);
  for (const f of fs.readdirSync(irDir).filter((x) => x.endsWith('.json')).sort()) {
    const doc = JSON.parse(fs.readFileSync(path.join(irDir, f), 'utf8'));
    const roots = (doc.components ?? []).filter((c) => !c.slot?.parent && c.meta?.role !== 'body-root');
    let pairs = 0; const tags = new Set();
    for (let i = 0; i + 1 < roots.length; i++) {
      const a = roots[i], b = roots[i + 1];
      if (a.meta?.role === 'ws-after' && inlineLevel(a) && inlineLevel(b)) { pairs++; tags.add(`${a.meta?.sourceTag ?? displayOf(a)}>${b.meta?.sourceTag ?? displayOf(b)}`); }
    }
    if (!pairs) continue;
    const key = idx.get(f.replace(/\.json$/, ''));
    const cells = {};
    for (const p of ['web', 'ios', 'android']) { const x = key && run.sections[sec].cells.get(`${key}|${p}`); cells[p] = x ? `${x.pass ? 'P' : 'f'} ${x.ssim}` : '—'; }
    out.push({ test: (key ?? f).replace(/^css\//, ''), pairs, tags: [...tags].join(' '), cells });
  }
}
console.log(`run ${runId}: ${out.length} documents carry ≥1 root-level inline-level ws-after pair`);
for (const r of out) console.log(r.test.padEnd(70), String(r.pairs).padStart(3), r.tags.slice(0, 28).padEnd(28), `web ${r.cells.web} | ios ${r.cells.ios} | android ${r.cells.android}`);
const webP = out.filter((r) => r.cells.web.startsWith('P')).length, webF = out.filter((r) => r.cells.web.startsWith('f')).length;
console.log(`web cells: ${webP} P (at risk) / ${webF} f (candidates)`);
