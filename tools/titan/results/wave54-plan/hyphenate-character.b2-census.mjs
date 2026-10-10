#!/usr/bin/env node
// hyphenate-character.b2-census.mjs — wave-54 planning census for the B2 question (read-only): which LINE-START brs
// (meta.role line-break, Height 20, NOT preceded by interleaved text in the parent's runs — i.e. the ones the U3
// re-arm keeps at 20) sit in a parent whose own declared line height is NOT 20 px (LineHeight px, or multiplier x
// FontSize px; the converter maps `normal` to multiplier 1.2). Those are the brs a "line-start br = the host's line
// box" rule (B2) would resize. Joined to wave53-final cells with the scorer's loadRun.
import fs from 'node:fs';
import path from 'node:path';
import { loadRun, resolveRunDir } from '../../score-gate.mjs';
const runDir = resolveRunDir(process.argv[2] ?? 'wave53-final');
const run = loadRun(runDir);
const cells = new Map();
for (const s of Object.values(run.sections)) for (const [k, c] of s.cells) {
  const [test, plat] = k.split('|');
  const ir = 'wpt__' + test.replace(/^css\//, '').replace(/\.[a-z]+$/, '').split('/').join('__') + '.json';
  if (!cells.has(ir)) cells.set(ir, {});
  cells.get(ir)[plat] = `${c.pass ? 'P' : 'f'} ${c.ssim}`;
}
const out = [];
let all20 = 0, docs = 0;
for (const sec of fs.readdirSync(path.join(runDir, 'sections')).sort()) {
  const d = path.join(runDir, 'sections', sec, 'per-test-ir');
  if (!fs.existsSync(d)) continue;
  for (const f of fs.readdirSync(d).filter((x) => x.endsWith('.json'))) {
    const doc = JSON.parse(fs.readFileSync(path.join(d, f), 'utf8'));
    const byId = new Map(doc.components.map((c) => [c.id, c]));
    const byName = new Map(doc.components.map((c) => [c.name, c]));
    const stray = new Set();
    for (const c of doc.components) {
      let saw = false;
      for (const r of c.meta?.runs ?? []) {
        if (typeof r.text === 'string') { if (r.text.trim()) saw = true; continue; }
        const ch = byName.get(r.child);
        if (saw && ch?.meta?.role === 'line-break') stray.add(ch.id);
        saw = false;
      }
    }
    let hit = 0, n20 = 0;
    for (const c of doc.components) {
      if (c.meta?.role !== 'line-break' || stray.has(c.id)) continue;
      const h = (c.properties ?? []).find((q) => q.type === 'Height')?.data?.px;
      if (h !== 20) continue;
      n20++;
      const parent = byId.get(c.slot?.parent);
      const lh = parent?.properties?.find((q) => q.type === 'LineHeight')?.data;
      const fs_ = parent?.properties?.find((q) => q.type === 'FontSize')?.data?.px ?? 16;
      const px = lh?.px ?? (lh?.multiplier != null ? lh.multiplier * fs_ : null);
      if (px != null && Math.abs(px - 20) > 0.05) hit++;
    }
    all20 += n20;
    if (n20) docs++;
    if (hit) out.push({ test: `${sec}/${f}`, hit, cells: cells.get(f) ?? {} });
  }
}
console.log(`line-start 20px brs kept by U3: ${all20} in ${docs} docs; in a parent whose declared line height != 20px: ${out.reduce((a, x) => a + x.hit, 0)} in ${out.length} docs`);
for (const x of out) console.log(`  ${x.test.padEnd(78)} ${x.hit} | ${JSON.stringify(x.cells)}`);
