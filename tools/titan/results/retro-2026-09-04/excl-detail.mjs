import { readdirSync, readFileSync, existsSync } from 'node:fs';
const root = 'tools/titan/runs/wave49-final/sections';
const WALL = new Set(['requires-script-mutation','requires-script-driven-scroll','requires-anchor-positioning-runtime']);
const FONT = new Set(['requires-font-face']); const ASSET=new Set(['requires-bundled-asset']); const REF=new Set(['browser-ref-divergent']);
const cols=['web-ref','ios-ref','android-ref'];
const causal = {}; // causal family -> tests
let missingCells = {web:0,ios:0,android:0}, allThreeMissing=0, refUnavailable=0, errorCells=0;
const wallWouldPassAll3 = [], anchorDetail=[];
for (const sec of readdirSync(root)) {
  const m = JSON.parse(readFileSync(`${root}/${sec}/manifest.json`,'utf8'));
  for (const [test,r] of Object.entries(m.wpt.results)) {
    const diffs = r.browserRef?.diffs;
    if (!r.browserRef?.available) { refUnavailable++; }
    if (!diffs) { allThreeMissing++; continue; }
    for (const c of cols) { const d=diffs[c]; if (!d) missingCells[c.split('-')[0]]++; else if (d.error) errorCells++; }
    if (r.scoreEligible===false) {
      const tags=r.notApplicableTags;
      const fam = tags.some(t=>REF.has(t))?'ref-unachievable': tags.some(t=>WALL.has(t))?'extraction-wall': tags.some(t=>FONT.has(t))?'font-face-wall': tags.some(t=>ASSET.has(t))?'bundled-asset':'??';
      (causal[fam] ??= []).push(test);
      const raw = cols.map(c=>{const d=diffs[c]; return d&&typeof d.ssim==='number'?{c,ssim:d.ssim,veto:!!(d.presenceFailed||d.colorFailed||d.coverageRatioFailed)}:null;}).filter(Boolean);
      if (fam==='extraction-wall' && raw.length===3 && raw.every(x=>x.ssim>=0.95&&!x.veto)) wallWouldPassAll3.push({test, tags:tags.filter(t=>WALL.has(t)), postLoad:r.postLoadExtracted, struct:r.structureExtracted, ssim:raw.map(x=>x.ssim)});
      if (sec==='css-anchor-position') {
        // look up per-test-ir _wpt stamps
        const key = `wpt__css-anchor-position__${test.split('/').pop().replace(/\.html$/,'')}`;
        const p = `${root}/${sec}/per-test-ir/${key}.json`;
        let wpt=null; if (existsSync(p)) { try { const doc=JSON.parse(readFileSync(p,'utf8')); wpt = doc._wpt ?? doc.meta?._wpt ?? null; } catch{} }
        anchorDetail.push({test:test.split('/').pop(), tags, postLoad:r.postLoadExtracted, lossy:r.lossyReasons, ssim:raw.map(x=>`${x.c.split('-')[0]}=${x.ssim}${x.veto?'V':''}`).join(' '), wptStamp: wpt ? Object.fromEntries(Object.entries(wpt).filter(([k])=>/postLoad|bail|anchor|reason|structure/i.test(k))) : 'no-per-test-ir'});
      }
    }
  }
}
console.log('missing per-column cells (test has some diff but not this column):', missingCells, 'allThreeMissing:', allThreeMissing, 'refUnavailable:', refUnavailable, 'errorCells:', errorCells);
console.log('CAUSAL FAMILIES:', Object.fromEntries(Object.entries(causal).map(([k,v])=>[k,v.length])));
console.log('extraction-wall excluded tests whose raw metrics pass on ALL THREE with no veto:', wallWouldPassAll3.length);
for (const x of wallWouldPassAll3) console.log('  ', x.test, x.tags.join('+'), 'postLoad=',x.postLoad, 'struct=',x.struct, x.ssim.join('/'));
console.log('CSS-ANCHOR-POSITION excluded detail (', anchorDetail.length, '):');
for (const a of anchorDetail) console.log('  ', a.test, '|', a.tags.join(','), '| postLoad=',a.postLoad, '| lossy=',JSON.stringify(a.lossy), '|', a.ssim, '|', JSON.stringify(a.wptStamp));
