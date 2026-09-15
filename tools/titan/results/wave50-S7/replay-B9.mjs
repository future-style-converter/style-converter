// S7 replay of lane B9's predicted Android flips.
//  line-clamp-006 / -007: the prediction is "Android gets iOS's census", so the
//    iOS capture IS the predicted Android picture — score it as the Android cell.
//  block-ellipsis-032: the prediction is "Android lands near WEB, not on the
//    ref" — so score OUR WEB capture as the Android cell (the predicted shape).
import { validate, cell, scorePng, readPng, writePng, SCRATCH } from './replay-lib.mjs';
import { join } from 'node:path';
const pick = d => d && ({ ssim:d.ssim, wptPass:d.wptPass, frame:d.frame, colorFailed:d.colorFailed,
  coverageRatioFailed:d.coverageRatioFailed, presenceFailed:d.presenceFailed,
  novelInkFailed:d.novelInkFailed, semanticPresence:d.semanticPresence });
const out={};
for (const [sec,T,src] of [
  ['css-overflow','css/css-overflow/line-clamp/line-clamp-006.html','ios'],
  ['css-overflow','css/css-overflow/line-clamp/line-clamp-007.html','ios'],
  ['css-overflow','css/css-overflow/line-clamp/block-ellipsis-032.tentative.html','web'],
]) {
  const ca=cell(sec,T,'android'), cs=cell(sec,T,src);
  const va=await validate(sec,T,'android'), vs=await validate(sec,T,src);
  const A=await readPng(ca.capPath), S=await readPng(cs.capPath), R=await readPng(ca.refPath);
  const p=await writePng(S, join(SCRATCH,`B9-${T.split('/').pop()}-${src}-as-android.png`));
  out[T]={ harnessExactMatch:{android:va.ok, [src]:vs.ok}, mismatches:{android:va.diffs??va.reason,[src]:vs.diffs??vs.reason},
    sizes:{ref:[R.width,R.height],android:[A.width,A.height],[src]:[S.width,S.height]},
    androidNow:pick(ca.recorded), webNow:pick(cell(sec,T,'web').recorded), iosNow:pick(cell(sec,T,'ios').recorded),
    predictedPicture:src, predictedScoreAsAndroidCell:pick(await scorePng(p,ca.testKey,ca.refPath,ca.fuzzy)) };
}
console.log(JSON.stringify(out,null,1));
