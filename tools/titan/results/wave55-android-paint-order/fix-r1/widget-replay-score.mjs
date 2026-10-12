// fix-r1/widget-replay-score.mjs <root> <replayDir> — scores the wave54-final android capture of flex-gap-decorations-023
// and widget-replay.py's U2 picture with the gate's composed metric (inject-wpt-block.mjs diffWebVsRef).
import { diffWebVsRef } from '../../../inject-wpt-block.mjs';
const [ROOT, OUT] = process.argv.slice(2);
const REFS = `${ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin`;
const sec = 'css-gaps', name = 'flex__flex-gap-decorations-023';
const ref = `${REFS}/${sec}/${name}.png`;
const cap = `${ROOT}/tools/titan/runs/wave54-final/sections/${sec}/android-screenshots/wpt__${sec}__${name}.png`;
const A = await diffWebVsRef(cap, ref), B = await diffWebVsRef(`${OUT}/${name}-android-U2.png`, ref);
console.log(`${sec}/${name} android  capture ${A.ssim}  U2-replay ${B.ssim}  Δ ${(B.ssim - A.ssim).toFixed(4)}  wptPass ${A.wptPass} → ${B.wptPass}`);
