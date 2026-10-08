// skeptic/geo-teeth.mjs — wave 54 L7 SKEPTIC: mutate a COPY of the 18 seeded PNGs so the PICTURE is wrong (not the probe's
// mode): draw the would-be label onto web 001 (a label on an exempt capture) and erase Android 004's label (a lost
// label on a label-due capture); then label-chrome-all-reset.geometry.py <dir> must print GEOMETRY WRONG and exit 1.
//   node geo-teeth.mjs <dir-with-the-18-copies>
import { readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../../../..');
const C = await import(pathToFileURL(path.join(ROOT, 'tools/visual/label-chrome-check.mjs')).href);
const { PNG } = await import(pathToFileURL(path.join(ROOT, 'node_modules/pngjs/lib/png.js')).href);
const dir = process.argv[2];
const edit = (file, fn) => { const p = path.join(dir, file); const png = PNG.sync.read(readFileSync(p)); fn(png); writeFileSync(p, PNG.sync.write(png)); };
edit('web__001_ATC_PropsThenAll_InGreenParent.png', (png) => { for (const [x, y] of C.glyphPixels('ATC_PropsThenAll_InGreenParent', png.width)) { const i = (y * png.width + x) * 4; for (let c = 0; c < 3; c++) png.data[i + c] = Math.round((C.INK[c] * C.INK_ALPHA + png.data[i + c] * (255 - C.INK_ALPHA)) / 255); } });
edit('Android__004_span.png', (png) => { for (const [x, y] of C.glyphPixels('span', png.width)) png.data.set(C.GROUND, (y * png.width + x) * 4); });
console.log('mutated copies: web 001 label drawn, Android 004 label erased');
