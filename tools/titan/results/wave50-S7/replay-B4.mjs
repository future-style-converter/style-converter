// S7 replay of lane B4's auto-close patch predictions.
//
// broken-symbols: the ref paints ONE line (y35-51); every capture paints it
//   TWICE (the duplicate at y55-71). ERASE the duplicate band → re-score.
//   No reflow needed: the surviving line is already at the ref's y.
// counter-suffix: the ref paints 12 line-bands, every capture paints 20 — the
//   8 extra are the duplicated <li> texts, interleaved one after each real
//   item. TWO simulations, because erasing alone is not what the fix does:
//   (a) ERASE the 8 duplicate bands in place  — pessimistic (the surviving
//       lines keep their displaced y);
//   (b) REFLOW — copy each surviving band to the ref's corresponding band y,
//       which is what removing 8 lines of a uniform 24px pitch produces.
//   (b) is the optimistic bound; the truth is between them.
import { validate, cell, scorePng, readPng, writePng, SCRATCH, clonePng, fillBox } from './replay-lib.mjs';
import { PNG } from 'pngjs';
import { join } from 'node:path';
const pick = d => d && ({ ssim:d.ssim, wptPass:d.wptPass, colorFailed:d.colorFailed,
  coverageRatioFailed:d.coverageRatioFailed, novelInkFailed:d.novelInkFailed,
  presenceFailed:d.presenceFailed, pixelMismatchedPct:d.pixelMismatchedPct });
function bands(p){ const out=[]; let s=-1;
  for(let y=0;y<p.height;y++){ let n=0;
    for(let x=0;x<p.width;x++){const i=(y*p.width+x)*4;
      if(Math.abs(p.data[i]-255)>8||Math.abs(p.data[i+1]-255)>8||Math.abs(p.data[i+2]-255)>8){n++;break;}}
    if(n>0&&s<0)s=y; if(n===0&&s>=0){out.push([s,y-1]);s=-1;} }
  if(s>=0)out.push([s,p.height-1]); return out; }

const out = {};

// ── broken-symbols ────────────────────────────────────────────────────────
out.brokenSymbols = {};
{
  const SEC='css-counter-styles', T='css/css-counter-styles/counter-style-at-rule/broken-symbols.html';
  for (const plat of ['web','ios','android']) {
    const v=await validate(SEC,T,plat); const c=cell(SEC,T,plat);
    const cap=await readPng(c.capPath); const bs=bands(cap);
    const copy=clonePng(cap);
    const [y0,y1]=bs[1];                       // the duplicate is band #2
    fillBox(copy,0,y0,cap.width,y1+1,[255,255,255]);
    const p=await writePng(copy, join(SCRATCH,`B4-bs-${plat}.png`));
    out.brokenSymbols[plat]={ harnessExactMatch:v.ok, mismatches:v.diffs??v.reason,
      capBands:bs, erasedBand:[y0,y1],
      before:pick(c.recorded), afterErase:pick(await scorePng(p,c.testKey,c.refPath,c.fuzzy)) };
  }
}

// ── counter-suffix ────────────────────────────────────────────────────────
out.counterSuffix = {};
{
  const SEC='css-counter-styles', T='css/css-counter-styles/counter-suffix.html';
  const cRef=cell(SEC,T,'web'); const ref=await readPng(cRef.refPath);
  const refBands=bands(ref);
  for (const plat of ['web','ios','android']) {
    const v=await validate(SEC,T,plat); const c=cell(SEC,T,plat);
    const cap=await readPng(c.capPath); const bs=bands(cap);
    // The top block alternates real / duplicate: indices 1,3,5,...,15.
    const dupIdx=[1,3,5,7,9,11,13,15];
    const keep=bs.filter((_,i)=>!dupIdx.includes(i));
    // (a) erase in place
    const erased=clonePng(cap);
    for (const i of dupIdx) fillBox(erased,0,bs[i][0],cap.width,bs[i][1]+1,[255,255,255]);
    const pa=await writePng(erased, join(SCRATCH,`B4-cs-${plat}-erase.png`));
    // (b) reflow: surviving band k → ref band k's top y
    const re=new PNG({width:cap.width,height:cap.height}); re.data.fill(0xFF);
    for (let k=0;k<keep.length && k<refBands.length;k++){
      const [s0,s1]=keep[k], [d0]=refBands[k];
      for (let y=s0;y<=s1;y++){ const dy=d0+(y-s0); if(dy<0||dy>=cap.height) continue;
        const s=y*cap.width*4; cap.data.copy(re.data,dy*cap.width*4,s,s+cap.width*4); }
    }
    const pb=await writePng(re, join(SCRATCH,`B4-cs-${plat}-reflow.png`));
    out.counterSuffix[plat]={ harnessExactMatch:v.ok, mismatches:v.diffs??v.reason,
      capBandCount:bs.length, refBandCount:refBands.length, keptBandCount:keep.length,
      recordedScoreExcluded: c.recorded?.scoreExcluded ?? null,
      before:pick(c.recorded),
      afterEraseInPlace:pick(await scorePng(pa,c.testKey,c.refPath,c.fuzzy)),
      afterReflow:pick(await scorePng(pb,c.testKey,c.refPath,c.fuzzy)) };
  }
  out.counterSuffix._refBands=refBands;
}
console.log(JSON.stringify(out,null,1));
