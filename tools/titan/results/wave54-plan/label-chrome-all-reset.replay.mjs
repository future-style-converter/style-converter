#!/usr/bin/env node
// tools/titan/results/wave54-plan/label-chrome-all-reset.replay.mjs — READ-ONLY replay of the label-chrome tripwire
// (tools/visual/label-chrome-tripwire.test.mjs) for the wave-54 family "label-chrome-all-reset" (BACKLOG 0(e)).
//
// It imports the tripwire's OWN exported checker (checkTriplet / glyphPixels — the same code CI runs) and replays it on
//   R0  the 18 all-then-color PNGs seeded by 77fe41e8 and withdrawn before the wave-53 ship (extracted read-only with
//       `git show 77fe41e8:<path>` into label-chrome-all-reset.seeded-77fe41e8/) — the CURRENT rule, which assumes every
//       baseline stem carries a label: must reproduce the three wave-53 reds;
//   R1  the same 18 PNGs under the PROPOSED rule — a stem listed in a label-exempt manifest (each entry verified
//       structurally against its fixture: the named node has composed children or non-empty `_text`, i.e. the shared
//       chrome predicate of docs/DYNAMIC_CAPTURE.md §5 "When" says NO label) is checked with P = ∅ (band byte-identity
//       when the band is ground ×3) plus a new clause (iv): the name's label must NOT be drawn on any platform;
//   R2  five mutations the proposed rule must turn red;
//   R3  the 130 committed stems under tools/visual/baseline/ through both rules (none is listed → identical verdicts).
// Run (the flag keeps the imported tripwire's own 136 node:test cases from executing in this process):
//   node --test-name-pattern='^__replay_none__$' tools/titan/results/wave54-plan/label-chrome-all-reset.replay.mjs
import { readFileSync, readdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { PNG } from 'pngjs';
import { checkTriplet, glyphPixels } from '../../../visual/label-chrome-tripwire.test.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '../../../..');
const SEEDED = path.join(HERE, 'label-chrome-all-reset.seeded-77fe41e8');
const BASELINE = path.join(ROOT, 'tools/visual/baseline');
const PLATFORMS = ['Android', 'iOS', 'web'];
const GROUND = [0x1a, 0x1a, 0x2e];
const isGround = (png, x, y) => { const i = (y * png.width + x) * 4; return GROUND.every((g, c) => Math.abs(png.data[i + c] - g) <= 1); };

// The proposed manifest (what the lane would commit as tools/visual/label-chrome-exempt.json).
const MANIFEST = {
  '001_ATC_PropsThenAll_InGreenParent': { fixture: 'fixtures/combinations/all-then-color.json', why: 'container' },
  '003_ATC_InitialUnderRedParent': { fixture: 'fixtures/combinations/all-then-color.json', why: 'container' },
  '005_ATC_DirectionSurvives': { fixture: 'fixtures/combinations/all-then-color.json', why: 'text' },
};

/** Structural verification of one manifest entry against its fixture — independent of every pixel and every runtime. */
function verifyEntry(stem, entry, presentStems) {
  const problems = [];
  if (!presentStems.has(stem)) problems.push(`manifest: ${stem} has no committed PNG (stale entry)`);
  const name = /^\d{3}_(.+)$/.exec(stem)?.[1];
  const doc = JSON.parse(readFileSync(path.join(ROOT, entry.fixture), 'utf8'));
  const hits = [];
  const walk = (map) => { for (const [k, v] of Object.entries(map ?? {})) { if (k === name) hits.push(v); if (v?.children) walk(v.children); } };
  walk(doc.components);
  if (!hits.length) problems.push(`manifest: ${entry.fixture} declares no component "${name}"`);
  for (const v of hits) {
    const kids = !!(v?.children && Object.keys(v.children).length);
    const text = typeof v?._text === 'string' && v._text.length > 0;
    const why = kids ? 'container' : text ? 'text' : 'leaf';
    if (why !== entry.why) problems.push(`manifest: "${name}" in ${entry.fixture} is a ${why}, entry says ${entry.why}${why === 'leaf' ? ' — a childless textless root IS labelled' : ''}`);
  }
  return problems;
}

/** The proposed checker: unchanged for label-due stems; P = ∅ + clause (iv) for verified exempt stems. */
function checkProposed(pngs, name, exempt) {
  if (!exempt) return checkTriplet(pngs, name);
  const base = checkTriplet(pngs, '');                                    // P = ∅: (i) vacuous, (ii) band identity if clean ×3
  const Pn = glyphPixels(name, pngs[0].width);                            // where the label WOULD be
  const problems = [...base.problems];
  pngs.forEach((png, k) => {
    const lit = Pn.filter(([x, y]) => !isGround(png, x, y)).length;
    if (Pn.length && lit === Pn.length) problems.push(`(iv) ${PLATFORMS[k]}: ${lit}/${Pn.length} label px drawn on a label-exempt capture`);
  });
  return { ...base, exempt: true, problems };
}

const loadTrio = (dir, stem) => PLATFORMS.map((p) => PNG.sync.read(readFileSync(path.join(dir, `${p}__${stem}.png`))));
const clone = (png) => { const c = new PNG({ width: png.width, height: png.height }); png.data.copy(c.data); return c; };
const drawLabel = (png, name) => { for (const [x, y] of glyphPixels(name, png.width)) { const i = (y * png.width + x) * 4; for (let c = 0; c < 3; c++) png.data[i + c] = Math.round((237 * 179 + png.data[i + c] * 76) / 255); } return png; };
const eraseLabel = (png, name) => { for (const [x, y] of glyphPixels(name, png.width)) png.data.set([...GROUND, 255], (y * png.width + x) * 4); return png; };
const line = (tag, stem, v) => console.log(`${tag} ${stem.padEnd(36)} [${v.exempt ? 'exempt ' : ''}${v.mode}, ${v.glyphs} glyph px] ${v.problems.length ? 'RED ' + v.problems.join('; ') : 'ok'}`);

const seededStems = [...new Set(readdirSync(SEEDED).map((f) => /^(?:Android|iOS|web)__(\d{3}_.+)\.png$/.exec(f)?.[1]).filter(Boolean))].sort();
const present = new Set(seededStems);
let fail = 0;

console.log('R0  current rule (every stem label-due) on the 77fe41e8 PNGs');
for (const s of seededStems) line('R0', s, checkTriplet(loadTrio(SEEDED, s), s.slice(4)));

console.log('R1  proposed rule (verified exempt manifest) on the same PNGs');
for (const [s, e] of Object.entries(MANIFEST)) { const p = verifyEntry(s, e, present); console.log(`R1  manifest ${s}: ${p.length ? 'RED ' + p.join('; ') : `verified (${e.why})`}`); fail += p.length; }
for (const s of seededStems) { const v = checkProposed(loadTrio(SEEDED, s), s.slice(4), s in MANIFEST); line('R1', s, v); fail += v.problems.length; }

console.log('R2  mutations — each must be RED under the proposed rule');
const expectRed = (tag, v) => { line(`R2 ${tag}`, '', v); if (!v.problems.length) { fail += 1; console.log('    ^ MUTATION SURVIVED'); } };
const s1 = '001_ATC_PropsThenAll_InGreenParent', s3 = '003_ATC_InitialUnderRedParent', s0 = '000_ATC_AllThenProps';
expectRed('m1 label drawn ×3 on exempt 001', checkProposed(loadTrio(SEEDED, s1).map((p) => drawLabel(clone(p), s1.slice(4))), s1.slice(4), true));
expectRed('m2 label drawn on web only, exempt 003', checkProposed(loadTrio(SEEDED, s3).map((p, k) => (k === 2 ? drawLabel(clone(p), s3.slice(4)) : p)), s3.slice(4), true));
expectRed('m3 label erased ×3 on due 000', checkProposed(loadTrio(SEEDED, s0).map((p) => eraseLabel(clone(p), s0.slice(4))), s0.slice(4), false));
{ const p = verifyEntry(s0, { fixture: 'fixtures/combinations/all-then-color.json', why: 'container' }, present); console.log(`R2 m4 manifest lists leaf 000: ${p.length ? 'RED ' + p.join('; ') : 'ok'}`); if (!p.length) fail += 1; }
{ const p = verifyEntry('007_ATC_Gone', { fixture: 'fixtures/combinations/all-then-color.json', why: 'container' }, present); console.log(`R2 m5 stale manifest entry: ${p.length ? 'RED ' + p.join('; ') : 'ok'}`); if (!p.length) fail += 1; }

console.log('R3  the committed stems under tools/visual/baseline/, current vs proposed');
const stems = [...new Set(readdirSync(BASELINE).map((f) => /^(?:Android|iOS|web)__(\d{3}_.+)\.png$/.exec(f)?.[1]).filter(Boolean))].sort();
let green = 0, same = 0, listed = 0;
for (const s of stems) {
  const trio = loadTrio(BASELINE, s);
  const a = checkTriplet(trio, s.slice(4)), b = checkProposed(trio, s.slice(4), s in MANIFEST);
  if (s in MANIFEST) listed += 1;
  if (!a.problems.length) green += 1;
  if (JSON.stringify(a) === JSON.stringify(b)) same += 1;
}
console.log(`R3  ${stems.length} stems: current green ${green}/${stems.length}, proposed verdict identical ${same}/${stems.length}, manifest-listed ${listed}`);
if (same !== stems.length) fail += 1;
console.log(fail ? `REPLAY FAILED (${fail})` : 'REPLAY OK — current rule reproduces the wave-53 reds; proposed rule green on 6/6, red on 5/5 mutations, unchanged on the committed set');
process.exit(fail ? 1 : 0);
