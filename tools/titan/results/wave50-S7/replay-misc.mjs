// S7 — (a) the brief's B8 item (background-attachment-fixed-inside-transform-1:
// what does the web capture score under the iOS cell?); (b) the Android-web
// pair on 090_Button_Primary, the floor-does-not-bind proxy for what the three
// placeholder-floor components' Android-web pair becomes after B11's fix;
// (c) an empirical iOS-vs-Android score gap over the whole frozen corpus, which
// calibrates every "the twin's picture is the prediction" claim (B3, B9).
import { readFileSync } from 'node:fs';
import { PNG } from 'pngjs';
import pixelmatch from 'pixelmatch';
import ssim from 'ssim.js';
import { cell, scorePng, readPng, RUN } from './replay-lib.mjs';
import { readdirSync } from 'node:fs';
import { join } from 'node:path';

// (a)
const SEC='css-backgrounds', T='css/css-backgrounds/background-attachment-fixed-inside-transform-1.html';
const ci=cell(SEC,T,'ios'), cw=cell(SEC,T,'web');
const s = await scorePng(cw.capPath, ci.testKey, ci.refPath, ci.fuzzy);
console.log('B8-item', JSON.stringify({
  recorded: { web: cw.recorded.ssim+'/'+cw.recorded.wptPass, ios: ci.recorded.ssim+'/'+ci.recorded.wptPass },
  webPictureUnderIosCell: { ssim: s.ssim, wptPass: s.wptPass, lowContentDensity: s.lowContentDensity },
}));

// (b)
const B='tools/visual/baseline';
const load=n=>PNG.sync.read(readFileSync(`${B}/${n}.png`));
function sc(a,b){const W=Math.max(a.width,b.width),H=Math.max(a.height,b.height);
  const d=new PNG({width:W,height:H});
  const m=pixelmatch(a.data,b.data,d.data,W,H,{threshold:0.02,includeAA:false});
  const r=ssim.ssim({data:new Uint8ClampedArray(a.data),width:W,height:H},
                    {data:new Uint8ClampedArray(b.data),width:W,height:H},{ssim:'fast'});
  return {pixelPct:+((m/(W*H))*100).toFixed(3), ssim:+r.mssim.toFixed(4)};}
for (const n of ['090_Button_Primary','016_Border_Solid','017_Border_Dashed']) {
  console.log('floor-not-binding proxy', n, 'Android-web', JSON.stringify(sc(load(`Android__${n}`),load(`web__${n}`))));
}

// (c) iOS-vs-Android browser-ref score gap across every scored cell.
const gaps=[];
for (const sec of readdirSync(RUN)) {
  let m; try { m=JSON.parse(readFileSync(join(RUN,sec,'manifest.json'),'utf8')); } catch { continue; }
  for (const r of Object.values(m.wpt?.results ?? {})) {
    const i=r.browserRef?.diffs?.['ios-ref'], a=r.browserRef?.diffs?.['android-ref'];
    if (!i||!a) continue;
    if (typeof i.ssim!=='number'||i.scoreExcluded) continue;
    if (typeof a.ssim!=='number'||a.scoreExcluded) continue;
    gaps.push(Math.abs(i.ssim-a.ssim));
  }
}
gaps.sort((x,y)=>x-y);
const q=p=>gaps[Math.min(gaps.length-1,Math.floor(p*gaps.length))];
console.log('ios-vs-android |Δssim| over', gaps.length, 'scored cell pairs:',
  JSON.stringify({p50:+q(0.5).toFixed(4),p75:+q(0.75).toFixed(4),p90:+q(0.90).toFixed(4),p95:+q(0.95).toFixed(4),p99:+q(0.99).toFixed(4),max:+gaps[gaps.length-1].toFixed(4)}));
