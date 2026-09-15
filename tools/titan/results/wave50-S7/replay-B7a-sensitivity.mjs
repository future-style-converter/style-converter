// Sensitivity band for B7's hr flip: how wrong may the baked rule be and still pass?
import { cell, scorePng, readPng, writePng, SCRATCH, fillBox } from './replay-lib.mjs';
import { PNG } from 'pngjs';
import { join } from 'node:path';
const SEC='css-images', T='css/css-images/gradient/gradient-hue-direction.html';
const BANDS=[[0,178,0],[178,310,98],[310,442,196],[442,600,294]];
const HR=[188,320,452];
function shiftFrame(cap,W,H){const o=new PNG({width:W,height:H});o.data.fill(0xFF);
 for(const [y0,y1,dy] of BANDS) for(let y=y0;y<y1&&y<H;y++){const sy=y+dy; if(sy>=cap.height)continue;
  const s=sy*cap.width*4; cap.data.copy(o.data,y*W*4,s,s+Math.min(cap.width,W)*4);} return o;}
const variants = {
  V3_fullWidthRule: (img)=>{for(const y of HR){fillBox(img,0,y,390,y+1,[154,154,154]);fillBox(img,0,y+1,390,y+2,[238,238,238]);}},
  V4_flatGrayRule:  (img)=>{for(const y of HR){fillBox(img,16,y,374,y+2,[128,128,128]);}},
  V5_oneRowOnly:    (img)=>{for(const y of HR){fillBox(img,16,y,374,y+1,[154,154,154]);}},
  V6_blackRule:     (img)=>{for(const y of HR){fillBox(img,16,y,374,y+2,[0,0,0]);}},
};
const out={};
for(const plat of ['web','ios','android']){
  const c=cell(SEC,T,plat); const cap=await readPng(c.capPath); const ref=await readPng(c.refPath);
  out[plat]={};
  for(const [name,mut] of Object.entries(variants)){
    const img=shiftFrame(cap,ref.width,ref.height); mut(img);
    const p=await writePng(img,join(SCRATCH,`B7a-${plat}-${name}.png`));
    const d=await scorePng(p,c.testKey,c.refPath,c.fuzzy);
    out[plat][name]={ssim:d.ssim,wptPass:d.wptPass,cF:d.colorFailed,crF:d.coverageRatioFailed};
  }
}
console.log(JSON.stringify(out,null,1));
