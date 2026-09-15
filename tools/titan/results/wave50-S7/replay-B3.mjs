// S7 replay of lane B3's five predicted Android flips. The lane's prediction is
// "Android becomes iOS" (the same ::after text fold iOS already does), so the
// iOS capture IS the predicted Android picture. The scorer is platform-agnostic
// (diffPlatformVsRef and diffComposedVsRef both call diffWebVsRef; the only
// per-platform machinery is the native-font-parity stamp, which applies to BOTH
// natives identically), so scoring the iOS PNG under the Android cell's ref +
// fuzzy is exactly the gate's arithmetic for that cell.
//
// Second question: does the Android canvas height (696 / 1032 vs the ref's 600)
// sink the cell by itself? The honest-frame rule folds only OUT-OF-FRAME INK
// into the denominator — white tail is free — so the test is: take the iOS
// picture, extend the canvas to Android's height with WHITE, and re-score.
import { validate, cell, scorePng, readPng, writePng, SCRATCH } from './replay-lib.mjs';
import { PNG } from 'pngjs';
import { join } from 'node:path';
const pick = d => d && ({ ssim:d.ssim, wptPass:d.wptPass, frame:d.frame,
  colorFailed:d.colorFailed, coverageRatioFailed:d.coverageRatioFailed,
  presenceFailed:d.presenceFailed, novelInkFailed:d.novelInkFailed,
  semanticPresence:d.semanticPresence });
const TESTS = [
  ['css-counter-styles','css/css-counter-styles/cjk-decimal/counter-cjk-decimal.html'],
  ['css-values','css/css-values/attr-notype-fallback.html'],
  ['css-values','css/css-values/attr-style-sharing-4.html'],
  ['css-lists','css/css-lists/counter-reset-reversed-pseudo-001.html'],
  ['css-lists','css/css-lists/counter-reset-reversed-pseudo-003.html'],
  ['css-lists','css/css-lists/counter-list-item.html'],
];
function extendWhite(src, H) {
  if (src.height >= H) return src;
  const o = new PNG({ width: src.width, height: H }); o.data.fill(0xFF);
  src.data.copy(o.data, 0, 0, src.data.length); return o;
}
const out = {};
for (const [sec, T] of TESTS) {
  const ca = cell(sec,T,'android'), ci = cell(sec,T,'ios'), cw = cell(sec,T,'web');
  const va = await validate(sec,T,'android'), vi = await validate(sec,T,'ios');
  const A = await readPng(ca.capPath), I = await readPng(ci.capPath), R = await readPng(ca.refPath);
  // (1) the twin substitution: the iOS picture scored as the Android cell.
  const p1 = await writePng(I, join(SCRATCH, `B3-${T.split('/').pop()}-ios-as-android.png`));
  const s1 = await scorePng(p1, ca.testKey, ca.refPath, ca.fuzzy);
  // (2) the same picture on Android's taller canvas (white tail).
  const p2 = await writePng(extendWhite(I, A.height), join(SCRATCH, `B3-${T.split('/').pop()}-ios-on-android-canvas.png`));
  const s2 = await scorePng(p2, ca.testKey, ca.refPath, ca.fuzzy);
  out[T] = {
    harnessExactMatch: { android: va.ok, ios: vi.ok },
    harnessMismatches: { android: va.diffs ?? va.reason, ios: vi.diffs ?? vi.reason },
    sizes: { ref: [R.width,R.height], android: [A.width,A.height], ios: [I.width,I.height] },
    androidNow: pick(ca.recorded), iosNow: pick(ci.recorded), webNow: pick(cw.recorded),
    iosPictureAsAndroidCell: pick(s1),
    iosPictureOnAndroidCanvas: pick(s2),
  };
}
console.log(JSON.stringify(out,null,1));
