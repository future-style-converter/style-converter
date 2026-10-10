// skeptic-r3: the gate's composed metric (inject-wpt-block.mjs diffWebVsRef) on the fix-r2 fakes and on skeptic-r3's
// vshift5 / short6 / line1-short4 fakes of css-gaps/flex/flex-gap-decorations-033, against the frozen ref. The GAP
// rows' floor is 0.99 (ios / android).
import path from 'node:path';
import { existsSync } from 'node:fs';
import { diffWebVsRef } from '../../../inject-wpt-block.mjs';
const HERE = path.dirname(new URL(import.meta.url).pathname);
const ROOT = path.resolve(HERE, '../../../../..');
const REF = `${ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-gaps/flex__flex-gap-decorations-033.png`;
const fakes = [['skeptic-r2/fake-gap', `${HERE}/../skeptic-r2/fake-gap`], ['fix-r2/fake-gap-blue', `${HERE}/../fix-r2/fake-gap-blue`],
  ['fix-r2/fake-gap-line3', `${HERE}/../fix-r2/fake-gap-line3`], ...['vshift5', 'short6', 'line1-short4', 'line2-short4'].map((n) => [`skeptic-r3/fake-gap-${n}`, `${HERE}/fake-gap-${n}`])];
for (const [name, dir] of fakes) {
  const png = `${dir}/sections/css-gaps/android-screenshots/wpt__css-gaps__flex__flex-gap-decorations-033.png`;
  if (!existsSync(png)) { console.log(`${name.padEnd(30)} (no android capture)`); continue; }
  const d = await diffWebVsRef(png, REF);
  console.log(`${name.padEnd(30)} ssim ${d.ssim}  ${d.ssim >= 0.99 ? '>= floor 0.99' : '< floor 0.99'}`);
}
