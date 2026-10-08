// skeptic/verdicts.mjs — wave 54 L7 SKEPTIC (not the lane's verdict-dump.mjs): HEAD's checkTriplet (extracted from
// `git show HEAD:tools/visual/label-chrome-tripwire.test.mjs`, node:test stubbed) vs the working-tree checker
// (tools/visual/label-chrome-check.mjs) on every committed stem + the 18 seeded 77fe41e8 PNGs; then the exempt path.
//   node tools/titan/results/wave54-label-chrome/skeptic/verdicts.mjs <tmpdir>
import { execSync } from 'node:child_process';            // git show of HEAD's test file
import { mkdirSync, writeFileSync, readdirSync, readFileSync, symlinkSync, existsSync } from 'node:fs'; // temp module
import path from 'node:path';                              // joins
import { fileURLToPath, pathToFileURL } from 'node:url';   // dynamic import of the temp module
const HERE = path.dirname(fileURLToPath(import.meta.url)); // skeptic dir
const ROOT = path.resolve(HERE, '../../../../..');          // repo root
const TMP = process.argv[2];                                // scratch dir for the HEAD module copy
mkdirSync(path.join(TMP, 'tools/visual'), { recursive: true }); // mirror HERE layout for HEAD's relative reads
const src = execSync('git show HEAD:tools/visual/label-chrome-tripwire.test.mjs', { cwd: ROOT }).toString(); // HEAD bytes
const stubbed = src.replace("import { test } from 'node:test';", 'const test = () => {};'); // no test registration
if (stubbed === src) throw new Error('stub anchor not found');                           // fail loudly
writeFileSync(path.join(TMP, 'tools/visual/head-tripwire.mjs'), stubbed);                // HEAD checker module
for (const [l, t] of [['tools/visual/block-font.json', 'tools/visual/block-font.json'], ['tools/visual/baseline', 'tools/visual/baseline'], ['node_modules', 'node_modules']]) {
  const p = path.join(TMP, l); if (!existsSync(p)) symlinkSync(path.join(ROOT, t), p);   // HEAD reads relative to HERE
}
const head = await import(pathToFileURL(path.join(TMP, 'tools/visual/head-tripwire.mjs')).href); // HEAD checkTriplet
const cur = await import(pathToFileURL(path.join(ROOT, 'tools/visual/label-chrome-check.mjs')).href); // U1 checker
const { PNG } = await import(pathToFileURL(path.join(ROOT, 'node_modules/pngjs/lib/png.js')).href); // decoder
const stems = (dir) => { const m = new Map(); for (const f of readdirSync(dir).filter((n) => n.endsWith('.png'))) { const r = /^(Android|iOS|web)__(\d{3}_(.+))\.png$/.exec(f); if (!r) continue; const e = m.get(r[2]) ?? { name: r[3], files: {} }; e.files[r[1]] = path.join(dir, f); m.set(r[2], e); } return m; };
const load = (e) => ['Android', 'iOS', 'web'].map((p) => PNG.sync.read(readFileSync(e.files[p]))); // triplet
let same = 0, diff = 0, green = 0;
const base = stems(path.join(ROOT, 'tools/visual/baseline'));                              // committed stems
for (const [stem, e] of base) {                                                            // HEAD vs U1 checker
  const pngs = load(e); const a = JSON.stringify(head.checkTriplet(pngs, e.name)); const b = JSON.stringify(cur.checkTriplet(pngs, e.name));
  if (a === b) same++; else { diff++; console.log('DIFF', stem, a, b); }
  if (!JSON.parse(b).problems.length) green++;
}
console.log(`committed stems ${base.size}: identical ${same}, differ ${diff}, green ${green}`);
const seed = stems(path.join(ROOT, 'tools/titan/results/wave54-plan/label-chrome-all-reset.seeded-77fe41e8')); // the 18 PNGs
for (const [stem, e] of seed) {
  const pngs = load(e); const h = head.checkTriplet(pngs, e.name); const c = cur.checkTriplet(pngs, e.name); const x = cur.checkExempt(pngs, e.name);
  console.log(`seed ${stem} HEAD ${JSON.stringify(h)} | same=${JSON.stringify(h) === JSON.stringify(c)} | exempt ${JSON.stringify(x)}`);
}
