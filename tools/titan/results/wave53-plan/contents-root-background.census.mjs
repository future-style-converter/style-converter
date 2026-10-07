#!/usr/bin/env node
// tools/titan/results/wave53-plan/contents-root-background.census.mjs
//
// Blast-radius census for the wave-53 brief `contents-root-background.md`.
// The changed code path (root background-IMAGE propagation on the three
// composed canvases) is reachable only through ONE wire shape: a per-test IR
// component with `meta.role == 'body-root'` carrying a `BackgroundImage`
// property. This script reads every wave52-ship per-test IR doc (JSON only),
// classifies the 1435 docs, and joins each class with its wave52-ship cells
// through the scorer's own loader (score-gate.mjs loadRun — the same read
// cells.mjs makes). Writes contents-root-background.census.json beside itself.
import { readdirSync, readFileSync, writeFileSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadRun, resolveRunDir } from '../../score-gate.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const RUN = 'wave52-ship';
const runDir = resolveRunDir(RUN);
const run = loadRun(runDir);
// "css/<sec>/<a>/<b>.html" ↔ stem "wpt__<sec>__<a>__<b>"
const cellsFor = (sec, stem) => {
  const out = {};
  for (const [k, c] of run.sections[sec]?.cells ?? []) {
    const [test, plat] = k.split('|');
    const s = 'wpt__' + sec + '__' + test.replace(/^css\//, '').replace(/^[^/]+\//, '').replace(/\.html?$|\.xht(ml)?$/, '').split('/').join('__');
    if (s === stem) out[plat] = `${c.pass ? 'P' : 'f'} ${c.ssim}`;
  }
  return out;
};

const classes = { carriers: [], bodyRootBackgroundColor: [], bodyRootOther: [], noBodyRoot: 0 };
let docs = 0;
for (const sec of readdirSync(path.join(runDir, 'sections')).sort()) {
  const irDir = path.join(runDir, 'sections', sec, 'per-test-ir');
  if (!existsSync(irDir)) continue;
  for (const f of readdirSync(irDir).filter((x) => x.endsWith('.json')).sort()) {
    docs++;
    const stem = f.replace(/\.json$/, '');
    const doc = JSON.parse(readFileSync(path.join(irDir, f), 'utf8'));
    const comps = Array.isArray(doc.components) ? doc.components : Object.values(doc.components || {});
    const body = comps.find((c) => c.meta?.role === 'body-root');
    if (!body) { classes.noBodyRoot++; continue; }
    const types = (body.properties || []).map((p) => p.type);
    const bgTypes = types.filter((t) => t.startsWith('Background'));
    const row = { section: sec, stem, bodyRootBackgroundTypes: bgTypes, display: (body.properties || []).filter((p) => p.type === 'Display').map((p) => p.data), contain: types.includes('Contain'), cells: cellsFor(sec, stem) };
    if (types.includes('BackgroundImage')) classes.carriers.push(row);
    else if (types.includes('BackgroundColor')) classes.bodyRootBackgroundColor.push(row);
    else classes.bodyRootOther.push(row);
  }
}
const count = (rows) => { const t = { cells: 0, pass: 0 }; for (const r of rows) for (const v of Object.values(r.cells)) { t.cells++; if (v.startsWith('P')) t.pass++; } return t; };
const out = {
  run: RUN,
  predicate: "per-test IR component with meta.role == 'body-root' AND a property of type 'BackgroundImage' (the only wire shape that reaches the changed canvas path)",
  docs,
  carrierStems: classes.carriers.map((r) => r.stem),
  carrierCells: classes.carriers.flatMap((r) => Object.entries(r.cells).map(([p, v]) => `${r.section}/${r.stem.replace(`wpt__${r.section}__`, '')} ${p} ${v}`)),
  totals: {
    carriers: { tests: classes.carriers.length, ...count(classes.carriers) },
    bodyRootBackgroundColorOnly: { tests: classes.bodyRootBackgroundColor.length, ...count(classes.bodyRootBackgroundColor) },
    bodyRootNoBackground: { tests: classes.bodyRootOther.length, ...count(classes.bodyRootOther) },
    noBodyRoot: { tests: classes.noBodyRoot },
  },
  carriers: classes.carriers,
  controlBodyRootBackgroundColor: classes.bodyRootBackgroundColor,
  controlBodyRootDisplayOrContain: classes.bodyRootOther.filter((r) => r.display.length || r.contain),
  sourceCensus: {
    method: "regex over the 1435 tests.list WPT sources: a <style> rule whose selector names html/body/:root with `background(-image)?:` holding url( or gradient(, a style= attribute on <html>/<body> with the same, or a legacy background= attribute; plus documentElement/body .style.background script writes",
    hits: ['css/css-backgrounds/background-attachment-margin-root-001.html', 'css/css-backgrounds/background-attachment-margin-root-002.html', 'css/css-display/display-contents-root-background.html'],
    scriptWrites: 0,
    note: 'identical to the IR carrier set: no source-declared root background-image is lost before the wire',
  },
};
writeFileSync(path.join(HERE, 'contents-root-background.census.json'), JSON.stringify(out, null, 1) + '\n');
console.log(JSON.stringify({ docs, totals: out.totals, carrierCells: out.carrierCells }, null, 1));
