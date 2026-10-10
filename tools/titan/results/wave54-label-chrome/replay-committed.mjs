#!/usr/bin/env node
// tools/titan/results/wave54-label-chrome/replay-committed.mjs — wave 54 lane L7 (label-chrome) U1 pins, re-executed
// THROUGH THE COMMITTED CODE: tools/visual/label-chrome-check.mjs (the checker the tripwire imports) and
// tools/visual/label-chrome-exempt.json (the manifest it reads) — not the brief's prototype
// (tools/titan/results/wave54-plan/label-chrome-all-reset.replay.mjs, whose R0-R3 this mirrors line for line).
//   R0  the pre-(iv) rule (checkTriplet on every stem) on the 18 PNGs of 77fe41e8 → the three wave-53 reds;
//   R1  the committed rule on the same PNGs, the manifest verified against the POST-SEED listing (committed baselines
//       ∪ the 18 copies, i.e. what tools/visual/baseline/ holds after U2-seed) → 3 entries verified, 6/6 green;
//   R2  m1-m5 (the brief's mutations) → each RED;
//   R3  the committed baseline stems through the committed decision on TODAY's listing (entries pending → nothing
//       exempt) → verdict objects identical to HEAD's checker (verdict-dump.head.txt, dumped before the U1 edit).
// Run: node tools/titan/results/wave54-label-chrome/replay-committed.mjs   (exit 0 = every expectation held)
import { readFileSync, readdirSync } from 'node:fs';   // PNGs, manifest, HEAD dump
import path from 'node:path';                           // joins
import { fileURLToPath } from 'node:url';               // ESM has no __dirname
import { PNG } from 'pngjs';                            // the tripwire's decoder
import { PLATFORMS, GROUND, ROOT, glyphPixels, checkTriplet, checkExempt, parseExemptManifest, verifyExemptEntry, readFixture } from '../../../visual/label-chrome-check.mjs'; // COMMITTED checker

const HERE = path.dirname(fileURLToPath(import.meta.url));                                  // this lane's results dir
const SEEDED = path.join(ROOT, 'tools/titan/results/wave54-plan/label-chrome-all-reset.seeded-77fe41e8'); // the 18 withdrawn PNGs
const BASELINE = path.join(ROOT, 'tools/visual/baseline');                                  // today's committed captures
const MANIFEST = parseExemptManifest(JSON.parse(readFileSync(path.join(ROOT, 'tools/visual/label-chrome-exempt.json'), 'utf8'))); // committed manifest
const pngNames = (dir) => readdirSync(dir).filter((f) => f.endsWith('.png')).sort();       // a directory's PNG listing
const stemsOf = (names) => [...new Set(names.map((f) => /^(?:Android|iOS|web)__(\d{3}_.+)\.png$/.exec(f)?.[1]).filter(Boolean))].sort(); // stems
const loadTrio = (dir, stem) => PLATFORMS.map((p) => PNG.sync.read(readFileSync(path.join(dir, `${p}__${stem}.png`)))); // Android, iOS, web
const clone = (png) => { const c = new PNG({ width: png.width, height: png.height }); png.data.copy(c.data); return c; }; // mutate a copy only
const drawLabel = (png, name) => { for (const [x, y] of glyphPixels(name, png.width)) { const i = (y * png.width + x) * 4; for (let c = 0; c < 3; c++) png.data[i + c] = Math.round((237 * 179 + png.data[i + c] * 76) / 255); } return png; }; // the chrome's ink, src-over
const eraseLabel = (png, name) => { for (const [x, y] of glyphPixels(name, png.width)) png.data.set([...GROUND, 255], (y * png.width + x) * 4); return png; }; // back to ground
const line = (tag, stem, v) => console.log(`${tag} ${stem.padEnd(36)} [${v.exempt ? 'exempt ' : ''}${v.mode}, ${v.glyphs} glyph px] ${v.problems.length ? 'RED ' + v.problems.join('; ') : 'ok'}`); // brief's format
const verifyAll = (listing) => new Map(Object.entries(MANIFEST.entries).map(([s, e]) => [s, verifyExemptEntry(s, e, { fixtureDoc: readFixture(ROOT, e.fixture), baselineFiles: listing })])); // (a) on a listing
const decide = (pngs, stem, verified) => (verified.get(stem)?.status === 'verified' ? checkExempt(pngs, stem.slice(4)) : checkTriplet(pngs, stem.slice(4))); // the test file's (b) branch
let fail = MANIFEST.problems.length;                                                       // a malformed manifest fails the replay
if (fail) console.log(`manifest shape RED: ${MANIFEST.problems.join('; ')}`);              // say why

const seeded = stemsOf(pngNames(SEEDED));                                                  // the 6 all-then-color stems
console.log('R0  pre-(iv) rule (every stem label-due) on the 77fe41e8 PNGs');
for (const s of seeded) line('R0', s, checkTriplet(loadTrio(SEEDED, s), s.slice(4)));      // must reproduce 001/003/005 red

console.log('R1  committed rule, manifest verified against the post-seed listing (committed ∪ 18 seeded)');
const postSeed = verifyAll([...pngNames(BASELINE), ...pngNames(SEEDED)].sort());           // what U2-seed's tree holds
for (const [s, v] of postSeed) { console.log(`R1  manifest ${s}: ${v.problems.length ? 'RED ' + v.problems.join('; ') : v.status}`); if (v.status !== 'verified') fail += 1; } // all 3 must verify
for (const s of seeded) { const v = decide(loadTrio(SEEDED, s), s, postSeed); line('R1', s, v); fail += v.problems.length; } // 6/6 green

console.log('R2  mutations — each must be RED under the committed rule');
const expectRed = (tag, v) => { line(`R2 ${tag}`, '', v); if (!v.problems.length) { fail += 1; console.log('    ^ MUTATION SURVIVED'); } }; // a green is a failure
const s1 = '001_ATC_PropsThenAll_InGreenParent', s3 = '003_ATC_InitialUnderRedParent', s0 = '000_ATC_AllThenProps'; // the stems the mutations use
expectRed('m1 label drawn ×3 on exempt 001', decide(loadTrio(SEEDED, s1).map((p) => drawLabel(clone(p), s1.slice(4))), s1, postSeed)); // (iv) ×3
expectRed('m2 label drawn on web only, exempt 003', decide(loadTrio(SEEDED, s3).map((p, k) => (k === 2 ? drawLabel(clone(p), s3.slice(4)) : p)), s3, postSeed)); // (iv) web
expectRed('m3 label erased ×3 on due 000', decide(loadTrio(SEEDED, s0).map((p) => eraseLabel(clone(p), s0.slice(4))), s0, postSeed)); // (i) ×3
const postListing = [...pngNames(BASELINE), ...pngNames(SEEDED)];                           // post-seed listing for m4/m5
const fx = { fixture: 'fixtures/combinations/all-then-color.json', why: 'container' };      // an entry pointing at the real fixture
{ const v = verifyExemptEntry(s0, fx, { fixtureDoc: readFixture(ROOT, fx.fixture), baselineFiles: postListing }); console.log(`R2 m4 manifest lists leaf 000: ${v.problems.length ? 'RED ' + v.problems.join('; ') : 'ok'}`); if (!v.problems.length) fail += 1; } // leaf → red
{ const v = verifyExemptEntry('007_ATC_Gone', fx, { fixtureDoc: readFixture(ROOT, fx.fixture), baselineFiles: postListing }); console.log(`R2 m5 stale manifest entry: ${v.problems.length ? 'RED ' + v.problems.join('; ') : 'ok'}`); if (!v.problems.length) fail += 1; } // stale + absent → red

console.log('R3  the committed stems through the committed decision on TODAY\'s listing, vs HEAD\'s checker');
const today = verifyAll(pngNames(BASELINE));                                               // pre-seed: entries pending
for (const [s, v] of today) console.log(`R3  manifest ${s}: ${v.problems.length ? 'RED ' + v.problems.join('; ') : v.status}`); // expected pending ×3
const head = new Map(readFileSync(path.join(HERE, 'verdict-dump.head.txt'), 'utf8').trim().split('\n').filter((l) => l.startsWith('baseline ')).map((l) => { const [, s, ...j] = l.split(' '); return [s, j.join(' ')]; })); // HEAD verdicts
let same = 0, green = 0, exempted = 0;                                                     // tallies
const stems = stemsOf(pngNames(BASELINE));                                                  // committed stems
for (const s of stems) {                                                                   // every committed stem
  const v = decide(loadTrio(BASELINE, s), s, today);                                       // the committed decision
  if (v.exempt) exempted += 1;                                                             // must stay 0 pre-seed
  if (!v.problems.length) green += 1;                                                      // tripwire verdict
  if (JSON.stringify(v) === head.get(s)) same += 1;                                        // byte-identical verdict object
}
console.log(`R3  ${stems.length} stems: green ${green}/${stems.length}, verdict object identical to HEAD ${same}/${stems.length} (HEAD dump ${head.size}), exempted ${exempted}`);
if (same !== stems.length || head.size !== stems.length || exempted) fail += 1;            // any drift fails
console.log(fail ? `REPLAY FAILED (${fail})` : 'REPLAY OK — committed rule: R0 reproduces the wave-53 reds; R1 6/6 green with 3 entries verified post-seed; R2 m1-m5 red; R3 verdicts identical to HEAD on the committed set');
process.exit(fail ? 1 : 0);                                                                // non-zero on any miss
