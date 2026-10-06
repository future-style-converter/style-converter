// Read-only ink-bbox probe: per colour class, print the bounding box + pixel count of the
// ref and of each platform capture for one wave51-fix test. Usage:
//   node nnm-ink.mjs <section> <test-path-after-css/> [platforms...]
import fs from 'node:fs';
import { createRequire } from 'node:module';
const ROOT = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf';
const { PNG } = createRequire(ROOT + '/tools/visual/compare-screenshots-metrics.mjs')('pngjs');
const REFS = ROOT + '/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins';
// Each argv is a census cell name, e.g. css-masking/clip-path/clip-path-ellipse-006 (first segment = section dir).
const cells = process.argv.slice(2);
const platforms = ['web', 'ios', 'android'];
function classify(r, g, b) {
  if (r > 247 && g > 247 && b > 247) return null;               // canvas
  if (g > 100 && r < 60 && b < 60) return 'green';
  if (r > 200 && g < 80 && b < 80) return 'red';
  if (b > 200 && r < 80 && g < 160) return 'blue';
  if (Math.abs(r - g) < 12 && Math.abs(g - b) < 12) return r < 128 ? 'dark' : 'grey';
  return 'other';
}
function probe(path) {
  if (!fs.existsSync(path)) return { missing: path };
  const png = PNG.sync.read(fs.readFileSync(path));
  const out = {}; const { width, height, data } = png;
  for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) {
    const i = (y * width + x) * 4; const c = classify(data[i], data[i + 1], data[i + 2]);
    if (!c) continue;
    const o = out[c] ||= { px: 0, x0: 1e9, y0: 1e9, x1: -1, y1: -1, rows: new Set() };
    o.px++; o.x0 = Math.min(o.x0, x); o.y0 = Math.min(o.y0, y); o.x1 = Math.max(o.x1, x); o.y1 = Math.max(o.y1, y);
  }
  for (const o of Object.values(out)) delete o.rows;
  return { size: `${width}x${height}`, ...out };
}
const fmt = (o) => Object.entries(o).map(([k, v]) => typeof v === 'object' ? `${k}: ${v.px}px [${v.x0},${v.y0}→${v.x1},${v.y1}]` : `${k}=${v}`).join('  ');
for (const cell of cells) {
  const short = cell.replace(/\.html$/, '');
  const sec = short.split('/')[0];
  const refPath = `${REFS}/${sec}/${short.split('/').slice(1).join('__')}.png`;
  const capPath = (p) => `${ROOT}/tools/titan/runs/wave51-fix/sections/${sec}/${p === 'web' ? 'screenshots' : p + '-screenshots'}/wpt__${short.replace(/\//g, '__')}.png`;
  console.log('\n## ' + short);
  console.log('REF     ', fmt(probe(refPath)));
  for (const p of platforms) console.log(p.padEnd(8), fmt(probe(capPath(p))));
}
