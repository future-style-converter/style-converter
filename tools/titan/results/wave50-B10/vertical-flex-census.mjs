//
// wave50-B10/vertical-flex-census.mjs — the PRICE of making the flex axes
// writing-mode aware (the css-gaps-006 class).
//
//   node tools/titan/results/wave50-B10/vertical-flex-census.mjs
//
// css-flexbox-1 §4: the flex main axis is the writing mode's inline axis,
// so `writing-mode: vertical-lr` transposes the whole container. Both
// natives derive `mainHorizontal` from `flex-direction` alone, which is
// why 006's rules come out rotated. This script lists every wave49-final
// SCORED cell whose IR declares `display: flex` and a vertical
// `writing-mode` on the SAME component — i.e. the exposure a fix would
// carry. Measured 2026-09-14: 11 tests / 33 cells, of which 4 fail today
// and 27 pass. See _note.md §3.
//
import fs from 'node:fs';
import path from 'node:path';
const RUN = 'tools/titan/runs/wave49-final/sections';
const hits = [];
for (const sec of fs.readdirSync(RUN)) {
  const dir = path.join(RUN, sec, 'per-test-ir');
  const mf = path.join(RUN, sec, 'manifest.json');
  if (!fs.existsSync(dir) || !fs.existsSync(mf)) continue;
  const m = JSON.parse(fs.readFileSync(mf, 'utf8'));
  for (const [test, o] of Object.entries(m.wpt?.results ?? {})) {
    const d = o.browserRef?.diffs; if (!d) continue;
    const slug = test.replace(/^css\//, '').replace(/\.html$/, '').replace(/[\/\\]/g, '__');
    const f = path.join(dir, 'wpt__' + slug + '.json');
    if (!fs.existsSync(f)) continue;
    const j = JSON.parse(fs.readFileSync(f, 'utf8'));
    // A component is in scope when it declares BOTH display:flex and a
    // vertical writing mode on the same element (inherited vertical modes
    // on a flex child are a different, wider class and not counted here).
    const bad = (j.components ?? []).some(c => {
      const p = c.properties ?? [];
      return p.some(x => x.type === 'Display' && x.data === 'FLEX')
          && p.some(x => x.type === 'WritingMode' && String(x.data).startsWith('VERTICAL'));
    });
    if (!bad) continue;
    const cell = p => { const x = d[p]; if (!x || typeof x.ssim !== 'number' || x.scoreExcluded) return '-';
      return (x.wptPass === true ? 'P' : 'f') + ' ' + x.ssim.toFixed(4); };
    hits.push([sec, test.replace(/^css\//, ''), cell('web-ref'), cell('ios-ref'), cell('android-ref')]);
  }
}
hits.sort();
for (const r of hits) console.log(r[0].padEnd(18), r[1].padEnd(58), r[2].padEnd(9), r[3].padEnd(9), r[4]);
const cells = hits.flatMap(r => r.slice(2)).filter(c => c !== '-');
console.log(`tests=${hits.length} scoredCells=${cells.length} failing=${cells.filter(c=>c.startsWith('f')).length}`);
