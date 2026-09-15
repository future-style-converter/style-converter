// S7 helper — prints the per-row ink band profile of a test's three captures
// and its frozen ref. Used to LOCATE the duplicate/displaced bands the replays
// erase or shift. Usage: node diag-line-profiles.mjs <section> <manifest test key>
import { cell, readPng } from './replay-lib.mjs';
const [sec, test] = process.argv.slice(2);
function lines(p){ const out=[]; let s=-1;
  for(let y=0;y<p.height;y++){ let n=0;
    for(let x=0;x<p.width;x++){const i=(y*p.width+x)*4;
      if(Math.abs(p.data[i]-255)>8||Math.abs(p.data[i+1]-255)>8||Math.abs(p.data[i+2]-255)>8)n++;}
    if(n>0&&s<0)s=y; if(n===0&&s>=0){out.push([s,y-1]);s=-1;} }
  if(s>=0)out.push([s,p.height-1]); return out; }
function bandBox(p,y0,y1){let x0=1e9,x1=-1,n=0;
  for(let y=y0;y<=y1;y++)for(let x=0;x<p.width;x++){const i=(y*p.width+x)*4;
    if(Math.abs(p.data[i]-255)>8||Math.abs(p.data[i+1]-255)>8||Math.abs(p.data[i+2]-255)>8){n++;if(x<x0)x0=x;if(x>x1)x1=x;}}
  return {x0,x1,n};}
for(const plat of ['web','ios','android']){
  const c=cell(sec,test,plat); const cap=await readPng(c.capPath);
  console.log('--',plat,cap.width+'x'+cap.height, c.capPath.split('/').slice(-3).join('/'));
  for(const [a,b] of lines(cap)) console.log('   y',a+'-'+b, JSON.stringify(bandBox(cap,a,b)));
}
const c=cell(sec,test,'web'); const ref=await readPng(c.refPath);
console.log('-- REF',ref.width+'x'+ref.height);
for(const [a,b] of lines(ref)) console.log('   y',a+'-'+b, JSON.stringify(bandBox(ref,a,b)));
