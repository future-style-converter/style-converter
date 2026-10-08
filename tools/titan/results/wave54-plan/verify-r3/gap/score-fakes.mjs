// verify-r3: the gate's composed metric (inject-wpt-block.mjs diffWebVsRef, as fix-r3/gap-fakes-score.mjs) on the
// verifier's extra fakes of css-gaps/flex/flex-gap-decorations-033 against the frozen ref (GAP rows' floor 0.99).
import path from 'node:path';
import { existsSync } from 'node:fs';
import { diffWebVsRef } from '../../../../inject-wpt-block.mjs';
const HERE = path.dirname(new URL(import.meta.url).pathname);
const ROOT = path.resolve(HERE, '../../../../../..');
const REF = `${ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-gaps/flex__flex-gap-decorations-033.png`;
for (const n of ['ref-as-run', 'red-l1r-down2', 'red-l3r-up2', 'blue-r1-short5', 'blue-r2-lshort4']) {
  const png = `${HERE}/fake-${n}/sections/css-gaps/android-screenshots/wpt__css-gaps__flex__flex-gap-decorations-033.png`;
  if (!existsSync(png)) { console.log(`fake-${n} (no capture)`); continue; }
  const d = await diffWebVsRef(png, REF);
  console.log(`verify-r3/gap/fake-${n}`.padEnd(36), `ssim ${d.ssim}  ${d.ssim >= 0.99 ? '>= floor 0.99' : '< floor 0.99'}`);
}
