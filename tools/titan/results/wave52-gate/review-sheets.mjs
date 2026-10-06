#!/usr/bin/env node
// tools/titan/results/wave52-gate/review-sheets.mjs
//
// A score is not a look. For every cell in one list of a score-gate JSON
// (gained / lost / movers / newlyMeasured) this writes contact sheets with one
// row per cell:   re-frozen ref | the cell's capture in <prevRun> | in <curRun>
// so each flip is LOOKED AT against the reference before a sentence about it is
// written (a degenerate pass — blank vs blank, a red FAIL the scorer tolerates —
// only shows up in the picture).
//
// Usage: node review-sheets.mjs <score.json> <list> <prevRun> <curRun> <outDir> [rowsPerSheet]
// Writes <outDir>/<list>-NN.png and <outDir>/<list>-index.txt (row → cell, scores).
import { createRequire } from 'node:module';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const REPO = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', '..', '..');
// sharp lives in the workspace node_modules, resolved from the repo root.
const sharp = createRequire(path.join(REPO, 'package.json'))('sharp');
// The live reference tree (CANVAS_REV as bumped by wave-52 L12).
const REFS = path.join(REPO, 'tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin');
const DIR = { web: 'screenshots', ios: 'ios-screenshots', android: 'android-screenshots' };

const [scorePath, list, prevRun, curRun, outDir, rowsArg] = process.argv.slice(2);
if (!outDir) { console.error('usage: review-sheets.mjs <score.json> <list> <prevRun> <curRun> <outDir> [rowsPerSheet]'); process.exit(2); }
const ROWS = Number(rowsArg || 6);
const cells = JSON.parse(readFileSync(scorePath, 'utf8'))[list];
if (!Array.isArray(cells)) { console.error(`no list "${list}" in ${scorePath}`); process.exit(2); }
mkdirSync(outDir, { recursive: true });

// css/<section>/<sub>/<name>.html → section + the `sub__name` stem both trees use.
const split = (test) => { const [, sec, ...rest] = test.split('/'); return { sec, stem: rest.join('__').replace(/\.html?$/, '') }; };
const capture = (run, sec, stem, platform) => path.join(REPO, 'tools/titan/runs', run, 'sections', sec, DIR[platform], `wpt__${sec}__${stem}.png`);

const W = 300, H = 460, G = 6;   // tile box and gutter, px
// One tile: the top-left 390×600 of the picture scaled into the box; a missing
// file is a grey tile so the row still lines up (and the index says so).
async function tile(file) {
  if (!existsSync(file)) return sharp({ create: { width: W, height: H, channels: 4, background: '#888888' } }).png().toBuffer();
  const meta = await sharp(file).metadata();
  const w = Math.min(390, meta.width), h = Math.min(600, meta.height);
  return sharp(file).extract({ left: 0, top: 0, width: w, height: h })
    .resize(W, H, { fit: 'contain', position: 'left top', background: '#ffffff' }).png().toBuffer();
}

const index = [];
for (let s = 0; s * ROWS < cells.length; s++) {
  const rows = cells.slice(s * ROWS, (s + 1) * ROWS);
  const composites = [];
  for (const [r, c] of rows.entries()) {
    const { sec, stem } = split(c.test);
    const files = [path.join(REFS, sec, `${stem}.png`), capture(prevRun, sec, stem, c.platform), capture(curRun, sec, stem, c.platform)];
    for (const [i, f] of files.entries()) composites.push({ input: await tile(f), left: G + i * (W + G), top: G + r * (H + G) });
    index.push(`${list}-${String(s + 1).padStart(2, '0')} row ${r + 1}: ${c.test} [${c.platform}] ${c.prev ?? '—'} → ${c.cur ?? '—'}${files.map((f) => (existsSync(f) ? '' : ` MISSING ${path.relative(REPO, f)}`)).join('')}`);
  }
  // Magenta gutters so a white tile edge is never mistaken for the page edge.
  const out = path.join(outDir, `${list}-${String(s + 1).padStart(2, '0')}.png`);
  await sharp({ create: { width: G + 3 * (W + G), height: G + rows.length * (H + G), channels: 4, background: '#ff00ff' } }).composite(composites).png().toFile(out);
}
writeFileSync(path.join(outDir, `${list}-index.txt`), `columns: re-frozen ref | ${prevRun} | ${curRun}\n${index.join('\n')}\n`);
console.log(`${cells.length} cells → ${Math.ceil(cells.length / ROWS)} sheet(s) in ${outDir}`);
