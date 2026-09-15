// S7 replay of lane B2's predicted Android flips.
//  position-relative-006: the Android capture paints a RED 100x100 square where
//    the ref paints a GREEN one. B2's fix makes the green child cover the red
//    parent, so the simulation paints the ref's own green square over the
//    capture's red one, at the REF's position, and re-scores.
//  clip-path-contentBox-1d / -1e: B2's fix shrink-to-fits the atomic inline box,
//    so the over-wide RED band disappears. Simulation: erase every red pixel to
//    canvas white and re-score.
import { validate, cell, scorePng, readPng, writePng, clonePng, SCRATCH } from './replay-lib.mjs';
import { join } from 'node:path';
const pick = d => d && ({ ssim:d.ssim, wptPass:d.wptPass, colorFailed:d.colorFailed,
  coverageRatioFailed:d.coverageRatioFailed, presenceFailed:d.presenceFailed,
  novelInkFailed:d.novelInkFailed, novelPx:d.novelInk?.novelPx,
  pixelMismatchedPct:d.pixelMismatchedPct, semanticPresence:d.semanticPresence });
/** bbox + count of pixels in a colour class (max per-channel distance <= tol). */
function classBox(p, rgb, tol=20) {
  let x0=1e9,y0=1e9,x1=-1,y1=-1,n=0;
  for(let y=0;y<p.height;y++)for(let x=0;x<p.width;x++){const i=(y*p.width+x)*4;
    if(Math.abs(p.data[i]-rgb[0])<=tol&&Math.abs(p.data[i+1]-rgb[1])<=tol&&Math.abs(p.data[i+2]-rgb[2])<=tol){
      n++;if(x<x0)x0=x;if(x>x1)x1=x;if(y<y0)y0=y;if(y>y1)y1=y;}}
  return {x0,y0,x1,y1,px:n};
}
const out={};
// ── position-relative-006 ────────────────────────────────────────────────
{
  const SEC='css-position', T='css/css-position/position-relative-006.html';
  const c=cell(SEC,T,'android'); const v=await validate(SEC,T,'android');
  const cap=await readPng(c.capPath), ref=await readPng(c.refPath);
  const red=classBox(cap,[255,0,0]), green=classBox(ref,[0,128,0]);
  // Paint the ref's green square onto the capture at the ref's own position.
  const copy=clonePng(cap);
  for(let y=green.y0;y<=green.y1;y++)for(let x=green.x0;x<=green.x1;x++){
    const i=(y*ref.width+x)*4, j=(y*copy.width+x)*4;
    copy.data[j]=ref.data[i];copy.data[j+1]=ref.data[i+1];copy.data[j+2]=ref.data[i+2];copy.data[j+3]=255;}
  const p=await writePng(copy, join(SCRATCH,'B2-posrel006-android-green.png'));
  out['position-relative-006'] = { harnessExactMatch:v.ok, mismatches:v.diffs??v.reason,
    capRedBox:red, refGreenBox:green, iosNow:pick(cell(SEC,T,'ios').recorded), webNow:pick(cell(SEC,T,'web').recorded),
    before:pick(c.recorded), afterGreenOverlay:pick(await scorePng(p,c.testKey,c.refPath,c.fuzzy)) };
}
// ── clip-path-contentBox-1d / -1e ────────────────────────────────────────
for (const n of ['1d','1e']) {
  const SEC='css-masking', T=`css/css-masking/clip-path/clip-path-contentBox-${n}.html`;
  const c=cell(SEC,T,'android'); const v=await validate(SEC,T,'android');
  const cap=await readPng(c.capPath), ref=await readPng(c.refPath);
  const red=classBox(cap,[255,0,0],40);
  // Erase every reddish pixel (the over-wide band) to canvas white.
  const copy=clonePng(cap); let erased=0;
  for(let i=0;i<copy.data.length;i+=4){
    const r=copy.data[i],g=copy.data[i+1],b=copy.data[i+2];
    if (r>120 && g<110 && b<110) { copy.data[i]=255;copy.data[i+1]=255;copy.data[i+2]=255;erased++; }
  }
  const p=await writePng(copy, join(SCRATCH,`B2-clip${n}-android-nored.png`));
  out[`clip-path-contentBox-${n}`] = { harnessExactMatch:v.ok, mismatches:v.diffs??v.reason,
    capRedBox:red, erasedReddishPx:erased,
    webNow:pick(cell(SEC,T,'web').recorded), iosNow:pick(cell(SEC,T,'ios').recorded),
    before:pick(c.recorded), afterRedErased:pick(await scorePng(p,c.testKey,c.refPath,c.fuzzy)) };
}
console.log(JSON.stringify(out,null,1));
