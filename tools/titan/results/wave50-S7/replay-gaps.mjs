// S7 — empirical native/web raster gap distributions over the frozen
// wave49-final run. These calibrate every "the twin's picture is the
// prediction" flip claim: a prediction whose headroom above 0.95 is smaller
// than the gap the corpus actually shows for that content class is not safe.
// RAW ssim is used (scoreExcluded rows included, flagged separately) because
// the question is rasteriser agreement, not gate eligibility.
import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { RUN } from './replay-lib.mjs';
const rows=[];
for (const sec of readdirSync(RUN)) {
  let m; try { m=JSON.parse(readFileSync(join(RUN,sec,'manifest.json'),'utf8')); } catch { continue; }
  for (const [k,r] of Object.entries(m.wpt?.results ?? {})) {
    const d=r.browserRef?.diffs; if(!d) continue;
    const w=d['web-ref'], i=d['ios-ref'], a=d['android-ref'];
    if (typeof w?.ssim!=='number'||typeof i?.ssim!=='number'||typeof a?.ssim!=='number') continue;
    rows.push({ test:k, sec, w:w.ssim, i:i.ssim, a:a.ssim,
      excl: !!(w.scoreExcluded||i.scoreExcluded||a.scoreExcluded) });
  }
}
function stats(vals){ vals=[...vals].sort((x,y)=>x-y);
  const q=p=>vals[Math.min(vals.length-1,Math.floor(p*vals.length))];
  return {n:vals.length,p50:+q(.5).toFixed(4),p75:+q(.75).toFixed(4),p90:+q(.9).toFixed(4),p95:+q(.95).toFixed(4),max:+vals[vals.length-1].toFixed(4)}; }
const scored = rows.filter(r=>!r.excl);
console.log('ALL scored cells');
console.log('  |ios-android|', JSON.stringify(stats(scored.map(r=>Math.abs(r.i-r.a)))));
console.log('  |web-android|', JSON.stringify(stats(scored.map(r=>Math.abs(r.w-r.a)))));
console.log('  |web-ios|    ', JSON.stringify(stats(scored.map(r=>Math.abs(r.w-r.i)))));
// CJK / non-Latin content class — the raster gap is biggest here.
const cjk = rows.filter(r=>/cjk|japanese|korean|hanyu|trad-chinese|simp-chinese/.test(r.test));
console.log('CJK-family cells (raw ssim, exclusions included):', cjk.length);
console.log('  |ios-android|', JSON.stringify(stats(cjk.map(r=>Math.abs(r.i-r.a)))));
for (const r of cjk.slice(0,40)) console.log('   ', r.test.replace('css/',''), 'w',r.w,'ios',r.i,'and',r.a,'|Δ|',+Math.abs(r.i-r.a).toFixed(4), r.excl?'(excluded)':'');
