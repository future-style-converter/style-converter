// S7 independent re-measurement of lane B11's committed-baseline ink boxes.
// Written from scratch (never runs the lane's own census script). The 327-pair
// baselines are captured on the DARK harness canvas, so "ink" here is deviation
// from the modal background colour of each PNG, not from white.
import { promises as fsp } from 'node:fs';
import { join } from 'node:path';
import { PNG } from 'pngjs';
const BASE = 'tools/visual/baseline';
const NAMES = ['091_Button_Outline','094_Input_Field','105_Edge_DeepNesting','016_Border_Solid','017_Border_Dashed','090_Button_Primary','097_Glass_Effect'];
function modal(p){ const h=new Map();
  for(let i=0;i<p.data.length;i+=4){const k=(p.data[i]<<16)|(p.data[i+1]<<8)|p.data[i+2]; h.set(k,(h.get(k)||0)+1);} 
  let best=0,bk=0; for(const [k,v] of h) if(v>best){best=v;bk=k;} return {rgb:[(bk>>16)&255,(bk>>8)&255,bk&255],count:best}; }
function box(p,bg,tol){ let x0=1e9,y0=1e9,x1=-1,y1=-1,n=0;
  for(let y=0;y<p.height;y++)for(let x=0;x<p.width;x++){const i=(y*p.width+x)*4;
    if(Math.abs(p.data[i]-bg[0])>tol||Math.abs(p.data[i+1]-bg[1])>tol||Math.abs(p.data[i+2]-bg[2])>tol){
      n++;if(x<x0)x0=x;if(x>x1)x1=x;if(y<y0)y0=y;if(y>y1)y1=y;}}
  return n? {x:x0,y:y0,w:x1-x0+1,h:y1-y0+1,inkPx:n} : {inkPx:0}; }
const out={};
for (const n of NAMES){ out[n]={};
  for (const plat of ['web','iOS','Android']){
    const f=join(BASE,`${plat}__${n}.png`);
    let buf; try{ buf=await fsp.readFile(f);}catch{ out[n][plat]='MISSING'; continue; }
    const p=PNG.sync.read(buf); const bg=modal(p);
    out[n][plat]={ canvas:`${p.width}x${p.height}`, bg:bg.rgb, bgPct:+(100*bg.count/(p.width*p.height)).toFixed(2),
      tol8:box(p,bg.rgb,8), tol20:box(p,bg.rgb,20) };
  }
}
console.log(JSON.stringify(out,null,1));
