// Read-only text-line probe: for the ref and each capture, list the y-runs of rows carrying non-white ink
// (line boxes), each with its x-extent, ink px (any deviation > 8/channel) and DARK px (< 128 luma).
// Also recompute semanticPresence-style coverage at several ink thresholds (8/32/64/128) so the
// coverage-ratio veto's sensitivity to anti-aliasing fringe can be read off directly.
// Usage: node nnm-lines.mjs <cell> [<cell>...]
import fs from 'node:fs';
import { createRequire } from 'node:module';
const ROOT = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf';
const { PNG } = createRequire(ROOT + '/tools/visual/compare-screenshots-metrics.mjs')('pngjs');
const REFS = ROOT + '/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins';
const THRESH = [8, 32, 64, 128];
function probe(path) {
  if (!fs.existsSync(path)) return null;
  const { width, height, data } = PNG.sync.read(fs.readFileSync(path));
  const rows = [];
  const cov = Object.fromEntries(THRESH.map(t => [t, 0]));
  for (let y = 0; y < height; y++) {
    let ink = 0, dark = 0, x0 = 1e9, x1 = -1;
    for (let x = 0; x < width; x++) {
      const i = (y * width + x) * 4; const r = data[i], g = data[i + 1], b = data[i + 2];
      const dev = Math.max(255 - r, 255 - g, 255 - b);
      for (const t of THRESH) if (dev > t) cov[t]++;
      if (dev > 8) { ink++; x0 = Math.min(x0, x); x1 = Math.max(x1, x); }
      if (0.299 * r + 0.587 * g + 0.114 * b < 128) dark++;
    }
    rows.push({ y, ink, dark, x0, x1 });
  }
  const runs = [];
  for (const r of rows) {
    if (r.ink === 0) continue;
    const last = runs[runs.length - 1];
    if (last && last.y1 === r.y - 1) { last.y1 = r.y; last.ink += r.ink; last.dark += r.dark; last.x0 = Math.min(last.x0, r.x0); last.x1 = Math.max(last.x1, r.x1); }
    else runs.push({ y0: r.y, y1: r.y, ink: r.ink, dark: r.dark, x0: r.x0, x1: r.x1 });
  }
  const total = width * height;
  return { runs, cov: Object.fromEntries(THRESH.map(t => [t, +(100 * cov[t] / total).toFixed(3)])) };
}
for (const cell of process.argv.slice(2)) {
  const sec = cell.split('/')[0];
  const refPath = `${REFS}/${sec}/${cell.split('/').slice(1).join('__')}.png`;
  const cap = (p) => `${ROOT}/tools/titan/runs/wave51-fix/sections/${sec}/${p === 'web' ? 'screenshots' : p + '-screenshots'}/wpt__${cell.replace(/\//g, '__')}.png`;
  console.log(`\n## ${cell}`);
  const ref = probe(refPath);
  const sides = { REF: ref, web: probe(cap('web')), ios: probe(cap('ios')), android: probe(cap('android')) };
  for (const [name, s] of Object.entries(sides)) {
    if (!s) { console.log(name, 'missing'); continue; }
    const ratio = (t) => ref && ref.cov[t] > 0 ? (Math.max(s.cov[t], ref.cov[t]) / Math.max(1e-9, Math.min(s.cov[t], ref.cov[t]))).toFixed(2) : '-';
    console.log(`${name.padEnd(8)} cov%@8/32/64/128 = ${THRESH.map(t => s.cov[t]).join(' / ')}   ratio-vs-ref = ${THRESH.map(ratio).join(' / ')}`);
    console.log('   lines: ' + s.runs.slice(0, 14).map(r => `y${r.y0}-${r.y1} x${r.x0}-${r.x1} ink${r.ink}/dark${r.dark}`).join(' | ') + (s.runs.length > 14 ? ` … +${s.runs.length - 14}` : ''));
  }
}
