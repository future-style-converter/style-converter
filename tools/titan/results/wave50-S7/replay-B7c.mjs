// S7 replay of lane B7 patch 3 (table-duplicate-text) on
// css-backgrounds/animations/background-color-animation-with-table{1,3,4}.
// Measures the ink bboxes B7 quotes, then SIMULATES the fix by erasing the
// duplicate's own bbox to canvas white and re-scoring with the gate's scorer.
import { validate, cell, scorePng, readPng, writePng, SCRATCH, inkBBox, fillBox, clonePng } from './replay-lib.mjs';
import { join } from 'node:path';
const pick = d => d && ({ ssim:d.ssim, wptPass:d.wptPass, colorFailed:d.colorFailed,
  coverageRatioFailed:d.coverageRatioFailed, novelInkFailed:d.novelInkFailed,
  presenceFailed:d.presenceFailed, pixelMismatchedPct:d.pixelMismatchedPct,
  semanticPresence:d.semanticPresence });
const out={};
for (const n of ['1','2','3','4']) {
  const T=`css/css-backgrounds/animations/background-color-animation-with-table${n}.html`;
  out[n]={};
  let refBox=null;
  for (const plat of ['web','ios','android']) {
    const v=await validate('css-backgrounds',T,plat); const c=cell('css-backgrounds',T,plat);
    const cap=await readPng(c.capPath); const ref=await readPng(c.refPath);
    if(!refBox) refBox=inkBBox(ref);
    const capBox=inkBBox(cap);
    // The DUPLICATE is the ink to the RIGHT of the reference's own right edge:
    // erase columns (refBox.x1, capBox.x1] over the full capture height.
    const copy=clonePng(cap);
    const erased=fillBox(copy,refBox.x1+1,0,cap.width,cap.height,[255,255,255]);
    const p=await writePng(copy, join(SCRATCH,`B7c-t${n}-${plat}-erased.png`));
    out[n][plat]={ harnessExactMatch:v.ok, mismatches:v.diffs??v.reason,
      refInk:refBox, capInk:capBox, erasedPx:erased,
      before:pick(c.recorded), afterErase:pick(await scorePng(p,c.testKey,c.refPath,c.fuzzy)) };
  }
}
console.log(JSON.stringify(out,null,1));
