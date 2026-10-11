// tools/titan/results/wave55-plan/instruments-flex-gap-fakes/score-fakes.mjs — wave-55 L7 (brief instruments.md §7):
// the gate's composed metric (inject-wpt-block.mjs diffWebVsRef, exactly as wave54-plan/verify-r3/gap/score-fakes.mjs)
// on the wave-55 row-rule fakes of css-gaps/flex/flex-gap-decorations-033 against the frozen ref: each discriminating
// fake must clear the WPT pass threshold 0.95 (so only the geometry key can catch it). Read-only: two PNG decodes each.
import path from 'node:path';
import { existsSync } from 'node:fs';
import { diffWebVsRef } from '../../../inject-wpt-block.mjs';
const HERE = path.dirname(new URL(import.meta.url).pathname);
const ROOT = path.resolve(HERE, '../../../../..');
const REF = `${ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-gaps/flex__flex-gap-decorations-033.png`;
for (const n of ['blue-r1-lshort2', 'blue-r1-split1', 'blue-r1-moved5', 'blue-r2-hshift2', 'blue-none', 'blue-r1-short1']) {
  const png = `${HERE}/fake-${n}/sections/css-gaps/android-screenshots/wpt__css-gaps__flex__flex-gap-decorations-033.png`;
  if (!existsSync(png)) { console.log(`fake-${n} (no capture)`); continue; }
  const d = await diffWebVsRef(png, REF);
  console.log(`wave55-plan/instruments-flex-gap-fakes/fake-${n}`.padEnd(58), `ssim ${d.ssim}  ${d.ssim >= 0.95 ? '>= pass 0.95' : '< pass 0.95'}`);
}
