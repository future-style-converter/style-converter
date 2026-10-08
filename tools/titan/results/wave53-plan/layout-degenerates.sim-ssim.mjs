import { createRequire } from 'node:module';
const require = createRequire('/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf/tools/titan/inject-wpt-block.mjs');
const { ssim } = require('ssim.js');
const { PNG } = require('pngjs');
import fs from 'node:fs';
const ROOT='/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf/';
const REF=ROOT+'tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/';
const RUN=ROOT+'tools/titan/runs/wave52-ship/sections/';
const load=(p)=>PNG.sync.read(fs.readFileSync(p));
const score=(a,b)=>+ssim({data:new Uint8ClampedArray(a.data),width:a.width,height:a.height},{data:new Uint8ClampedArray(b.data),width:b.width,height:b.height},{ssim:'fast'}).mssim.toFixed(4);
function fill(img,x0,y0,x1,y1,rgb){for(let y=y0;y<=y1;y++)for(let x=x0;x<=x1;x++){const i=(y*img.width+x)*4;img.data[i]=rgb[0];img.data[i+1]=rgb[1];img.data[i+2]=rgb[2];img.data[i+3]=255;}}
function px(img,x,y){const i=(y*img.width+x)*4;return [img.data[i],img.data[i+1],img.data[i+2]];}
const W=[255,255,255];
const cases=[
 ['css-contain','contain-inline-size-bfc-floats-001',(c)=>{const o=px(c,100,395);fill(c,16,388,215,407,W);fill(c,16,288,215,307,o);}],
 ['css-contain','contain-inline-size-bfc-floats-002',(c)=>{const o=px(c,100,395);fill(c,16,388,315,407,W);fill(c,16,88,315,107,o);}],
 ['css-display','display-flow-root-002',(c)=>{const k=px(c,16,300);const blue=px(c,100,150);fill(c,16,216,16,416,W);fill(c,16,215,16,215,blue);fill(c,265,115,266,316,k);}],
];
for (const [sec,stem,edit] of cases){
  const ref=load(REF+sec+'/'+stem+'.png');
  for (const d of ['ios-screenshots','android-screenshots']){
    const cap=load(RUN+sec+'/'+d+'/wpt__'+sec+'__'+stem+'.png');
    const before=score(cap,ref); edit(cap); const after=score(cap,ref);
    console.log(stem, d.slice(0,3), 'now', before, 'simulated-fix', after);
  }
}
