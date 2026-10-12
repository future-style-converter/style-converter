// widget-replay-score.mjs — scores the wave54-final android captures and widget-replay.py's U2 pictures with the
// gate's composed metric (inject-wpt-block.mjs diffWebVsRef). Usage: node widget-replay-score.mjs <root> <replayDir>
import { diffWebVsRef } from '../../../inject-wpt-block.mjs';
const [ROOT, OUT] = process.argv.slice(2);
const REFS = `${ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin`;
for (const [sec, name] of [['css-cascade', 'revert-layer-015'], ['css-pseudo', 'first-line-and-placeholder']]) {
  const ref = `${REFS}/${sec}/${name}.png`;
  const cap = `${ROOT}/tools/titan/runs/wave54-final/sections/${sec}/android-screenshots/wpt__${sec}__${name}.png`;
  const A = await diffWebVsRef(cap, ref), B = await diffWebVsRef(`${OUT}/${name}-android-U2.png`, ref); const a = A.ssim, b = B.ssim;
  console.log(`${sec}/${name} android  capture ${a}  U2-replay ${b}  Δ ${(b - a).toFixed(4)}  wptPass ${A.wptPass} → ${B.wptPass}`);
}
