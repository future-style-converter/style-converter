// S7: what the 327-pair comparator's own stack scores for the three
// placeholder-floor components, pair by pair, on the COMMITTED baselines —
// and what the iOS-Android pair would score once Android's geometry matches
// web's (B11's fix), simulated by substituting the web baseline for Android.
import { readFileSync } from 'node:fs';
import { PNG } from 'pngjs';
import pixelmatch from 'pixelmatch';
import ssim from 'ssim.js';
const B='tools/visual/baseline';
const load=n=>PNG.sync.read(readFileSync(`${B}/${n}.png`));
function pad(img,W,H){ if(img.width===W&&img.height===H) return img;
  const o=new PNG({width:W,height:H});
  // PAD_SENTINEL magenta — tools/visual/pad-canvas.mjs's fill, the one the
  // 327-pair pipeline uses so a size disagreement SHOUTS.
  for(let i=0;i<o.data.length;i+=4){o.data[i]=0xFF;o.data[i+1]=0x00;o.data[i+2]=0xFF;o.data[i+3]=0xFF;}
  for(let y=0;y<Math.min(img.height,H);y++){const s=y*img.width*4;
    img.data.copy(o.data,y*W*4,s,s+Math.min(img.width,W)*4);} return o; }
function score(a,b){ const W=Math.max(a.width,b.width),H=Math.max(a.height,b.height);
  const A=pad(a,W,H),Bb=pad(b,W,H);
  const d=new PNG({width:W,height:H});
  const m=pixelmatch(A.data,Bb.data,d.data,W,H,{threshold:0.02,includeAA:false});
  const s=ssim.ssim({data:new Uint8ClampedArray(A.data),width:W,height:H},
                    {data:new Uint8ClampedArray(Bb.data),width:W,height:H},{ssim:'fast'});
  return { W,H, pixelPct:+((m/(W*H))*100).toFixed(3), ssim:+s.mssim.toFixed(4) }; }
const out={};
for (const n of ['091_Button_Outline','094_Input_Field','105_Edge_DeepNesting']) {
  const w=load(`web__${n}`), i=load(`iOS__${n}`), a=load(`Android__${n}`);
  out[n]={
    'Android-web (ledgered)': score(a,w),
    'iOS-Android (ledgered)': score(i,a),
    'iOS-web (NOT ledgered)': score(i,w),
    'iOS-Android AFTER B11 (android geometry := web)': score(i,w),
  };
}
console.log(JSON.stringify(out,null,1));

// ── ΔE95, the second gate criterion (pairRegressed: p95 > 5.0 fails) ───────
import { computeLabDeltaE } from '../../../visual/compare-screenshots-metrics.mjs';
const de={};
for (const n of ['091_Button_Outline','094_Input_Field','105_Edge_DeepNesting']) {
  const w=load(`web__${n}`), i=load(`iOS__${n}`), a=load(`Android__${n}`);
  const W=390,H=62;
  de[n]={
    'Android-web': computeLabDeltaE(pad(a,W,H),pad(w,W,H),4),
    'iOS-Android': computeLabDeltaE(pad(i,W,H),pad(a,W,H),4),
    'iOS-web (= post-B11 iOS-Android)': computeLabDeltaE(pad(i,W,H),pad(w,W,H),4),
  };
}
console.log('DELTA-E'); console.log(JSON.stringify(de,null,1));
