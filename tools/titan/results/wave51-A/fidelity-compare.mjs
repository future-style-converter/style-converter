// fidelity-compare.mjs — wave 51 PR 3: before/after pair scores for the fidelity
// fixtures the harness label used to contaminate. "Before" is the retrospective's
// classification of the 2026-09-04 fidelity net (tools/titan/results/
// retro-2026-09-04/fidelity-classified.csv — columns iOS-Android / iOS-web /
// Android-web hold "[ssim, pixelPct]"); "after" is a fresh three-platform
// `./test-all.sh <fixture>` run archived under tools/titan/runs/wave51-labels/
// fidelity/<stem>/manifest.json (the label-chrome tree). It prints every row of
// each captured fixture whose class named the label (WEB-LABEL-SPILL) or whose
// pair moved by ≥ 0.005 SSIM, so the PR can quote what the chrome actually moved
// and what it did not. Usage: node fidelity-compare.mjs [runDir=tools/titan/runs/wave51-labels/fidelity]
import { readFileSync, readdirSync, existsSync } from 'node:fs';
import path from 'node:path';
const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../../..');
const RUN = path.resolve(ROOT, process.argv[2] || 'tools/titan/runs/wave51-labels/fidelity');
const CSV = readFileSync(path.join(ROOT, 'tools/titan/results/retro-2026-09-04/fidelity-classified.csv'), 'utf8');
// Minimal CSV parser (quoted fields with embedded commas/quotes) — the retro CSV is regular.
function parseCsv(text) {
  const rows = []; let row = [], field = '', q = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (q) { if (c === '"') { if (text[i + 1] === '"') { field += '"'; i++; } else q = false; } else field += c; }
    else if (c === '"') q = true; else if (c === ',') { row.push(field); field = ''; }
    else if (c === '\n') { row.push(field); rows.push(row); row = []; field = ''; }
    else if (c !== '\r') field += c;
  }
  if (field || row.length) { row.push(field); rows.push(row); }
  return rows;
}
const [header, ...lines] = parseCsv(CSV);
const col = Object.fromEntries(header.map((h, i) => [h, i]));
const PAIRS = ['iOS-Android', 'iOS-web', 'Android-web'];
const before = new Map();                                                           // "fixture|component" → { class, pairs }
for (const r of lines) if (r.length >= header.length) before.set(`${r[col.fixture]}|${r[col.component]}`, { cls: r[col.class], pairs: Object.fromEntries(PAIRS.map((p) => [p, JSON.parse(r[col[p]] || 'null')])) });
const stems = readdirSync(RUN, { withFileTypes: true }).filter((d) => d.isDirectory()).map((d) => d.name).sort();
let moved = 0, labelRows = 0;
for (const stem of stems) {
  const mf = path.join(RUN, stem, 'manifest.json'); if (!existsSync(mf)) { console.log(`${stem}: no manifest`); continue; }
  const m = JSON.parse(readFileSync(mf, 'utf8'));
  const fixture = m.inputLabel && m.inputLabel.includes('fixtures/') ? m.inputLabel.replace(/^.*?(fixtures\/[^ ]+).*$/, '$1') : null;
  const key = [...before.keys()].find((k) => k.split('|')[0].endsWith(`${stem}.json`));
  const fx = fixture || (key ? key.split('|')[0] : `?${stem}`);
  console.log(`\n=== ${fx} (${m.rows.length} components)`);
  for (const r of m.rows) {
    const b = before.get(`${fx}|${r.name}`);
    const after = Object.fromEntries(PAIRS.map((p) => [p, r.pairs[p] ? [r.pairs[p].ssim, r.pairs[p].pixelMismatchedPct] : null]));
    const isLabel = b && /LABEL/.test(b.cls);
    const delta = b ? Math.max(...PAIRS.map((p) => (b.pairs[p] && after[p]) ? Math.abs(after[p][0] - b.pairs[p][0]) : 0)) : 0;
    if (!isLabel && delta < 0.005) continue;
    if (isLabel) labelRows++; if (delta >= 0.005) moved++;
    const fmt = (v) => (v ? `${v[0].toFixed(4)}/${Number(v[1]).toFixed(2)}%` : '—');
    console.log(`  ${r.name.padEnd(36)} ${(b ? b.cls : 'not in retro CSV').padEnd(16)} ` + PAIRS.map((p) => `${p} ${b ? fmt(b.pairs[p]) : '—'} → ${fmt(after[p])}`).join(' | '));
  }
}
console.log(`\nlabel-attributed rows printed: ${labelRows}; rows moved ≥ 0.005 on any pair: ${moved}`);
