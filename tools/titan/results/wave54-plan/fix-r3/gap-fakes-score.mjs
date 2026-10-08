// fix r3 (R3-S3): the gate's composed metric (inject-wpt-block.mjs diffWebVsRef, as skeptic-r3/gap-fakes-r3-score.mjs)
// on fix-r3's two new vertical-extent fakes of css-gaps/flex/flex-gap-decorations-033 against the frozen ref: both clear
// the GAP rows' 0.99 floor, so the geometry key is their only gate.
import path from 'node:path';
import { existsSync } from 'node:fs';
import { diffWebVsRef } from '../../../inject-wpt-block.mjs';
const HERE = path.dirname(new URL(import.meta.url).pathname);
const ROOT = path.resolve(HERE, '../../../../..');
const REF = `${ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-gaps/flex__flex-gap-decorations-033.png`;
for (const n of ['vshift3-up', 'line3-short3']) {
  const png = `${HERE}/fake-gap-${n}/sections/css-gaps/android-screenshots/wpt__css-gaps__flex__flex-gap-decorations-033.png`;
  if (!existsSync(png)) { console.log(`fix-r3/fake-gap-${n} (no capture)`); continue; }
  const d = await diffWebVsRef(png, REF);
  console.log(`fix-r3/fake-gap-${n}`.padEnd(30), `ssim ${d.ssim}  ${d.ssim >= 0.99 ? '>= floor 0.99' : '< floor 0.99'}`);
}
