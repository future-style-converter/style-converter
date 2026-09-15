// S7 — a stronger proxy for B7's table-duplicate-text flip than column erasure:
// Android's table1/table3 capture ALREADY renders without the duplicate
// (Compose's table path never paints the <table>/<tr> own text), so it IS the
// predicted post-fix picture. Score it as the web and iOS cells.
import { cell, scorePng } from './replay-lib.mjs';
const pick=d=>({ssim:d.ssim,wptPass:d.wptPass,crF:d.coverageRatioFailed,cF:d.colorFailed});
for (const n of ['1','3','4']) {
  const T=`css/css-backgrounds/animations/background-color-animation-with-table${n}.html`;
  const ca=cell('css-backgrounds',T,'android');
  for (const plat of ['web','ios']) {
    const c=cell('css-backgrounds',T,plat);
    const s=await scorePng(ca.capPath,c.testKey,c.refPath,c.fuzzy);
    console.log(`table${n}`, plat, 'now', c.recorded.ssim, c.recorded.wptPass,
      '| android picture scored as this cell ->', JSON.stringify(pick(s)));
  }
}
