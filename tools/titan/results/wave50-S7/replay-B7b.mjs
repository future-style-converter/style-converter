// S7 replay of lane B7 patch 2 (root-scope-conflict):
//  (a) the -001 sibling cell numbers B7 quotes (1.0000 / 0.9996 / 0.9989);
//  (b) what the -002 capture actually is, relative to the -001 capture;
//  (c) the s-11-1-1b-005 "234000 black pixels" claim.
import { validate, cell, scorePng, readPng, writePng, SCRATCH, inkBBox, deltaOver } from './replay-lib.mjs';
import { join } from 'node:path';
const pick = d => d && ({ ssim:d.ssim, wptPass:d.wptPass, colorFailed:d.colorFailed,
  coverageRatioFailed:d.coverageRatioFailed, novelInkFailed:d.novelInkFailed,
  presenceFailed:d.presenceFailed, novelPx:d.novelInk?.novelPx,
  semanticPresence:d.semanticPresence, pixelMismatchedPct:d.pixelMismatchedPct });
const out = { siblingCells:{}, twoVsOne:{}, substitution:{}, s11:{} };

for (const t of ['contain-html-overflow-001','contain-html-overflow-002']) {
  out.siblingCells[t]={};
  for (const plat of ['web','ios','android']) {
    const v = await validate('css-contain', `css/css-contain/${t}.html`, plat);
    out.siblingCells[t][plat] = { harnessExactMatch:v.ok, mismatches:v.diffs??v.reason, ...pick(v.rec) };
  }
}
// (b) how do the two captures differ, and what happens if -002's capture is
//     REPLACED by -001's (the strongest form of B7's "one-token IR" argument:
//     if the patched -002 renders like -001, it scores like -001).
for (const plat of ['web','ios','android']) {
  const c1 = cell('css-contain','css/css-contain/contain-html-overflow-001.html',plat);
  const c2 = cell('css-contain','css/css-contain/contain-html-overflow-002.html',plat);
  const a = await readPng(c1.capPath), b = await readPng(c2.capPath);
  out.twoVsOne[plat] = { size001:[a.width,a.height], size002:[b.width,b.height],
    pixelsDiffering: a.width===b.width && a.height===b.height ? deltaOver(a,b,0) : 'size mismatch',
    ink001: inkBBox(a), ink002: inkBBox(b) };
  // substitute -001's capture into the -002 cell (same ref? check first)
  const sameRef = c1.refPath !== c2.refPath;
  const p = await writePng(a, join(SCRATCH, `B7b-${plat}-001cap-as-002.png`));
  out.substitution[plat] = { refsDiffer:sameRef, score: pick(await scorePng(p, c2.testKey, c2.refPath, c2.fuzzy)) };
}
// (c) s-11-1-1b-005
for (const plat of ['web','ios','android']) {
  const c = cell('CSS2','css/CSS2/css21-errata/s-11-1-1b-005.html',plat);
  const v = await validate('CSS2','css/CSS2/css21-errata/s-11-1-1b-005.html',plat);
  const png = await readPng(c.capPath);
  let black=0, nonwhite=0;
  for (let i=0;i<png.data.length;i+=4){
    if (png.data[i]<8 && png.data[i+1]<8 && png.data[i+2]<8) black++;
    if (png.data[i]<247||png.data[i+1]<247||png.data[i+2]<247) nonwhite++;
  }
  out.s11[plat] = { harnessExactMatch:v.ok, size:[png.width,png.height], totalPx:png.width*png.height,
    blackPx:black, nonWhitePx:nonwhite, ...pick(c.recorded) };
}
console.log(JSON.stringify(out,null,1));
