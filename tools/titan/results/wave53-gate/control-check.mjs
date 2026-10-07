#!/usr/bin/env node
// tools/titan/results/wave53-gate/control-check.mjs
//
// The byte-identity control of wave 52 (tools/titan/results/wave52-gate/
// control-check.mjs), generalised for wave 53 in three ways the plan asked for
// (tools/titan/results/wave53-plan/PLAN.md §6, expectations.json):
//
//   1. CARRIERS COME FROM THE PLAN. Which captures a fix may change is read
//      from expectations.json — the union of every lane's captureCarriers, or
//      one lane's with --lane <id>. Everything else must not change.
//   2. BYTES ARE NOT PIXELS ANY MORE. On the macOS 27 / Xcode 27 host the web
//      capture path re-encodes its PNGs (236 of 240 byte-different against
//      wave52-ship in five sections at planning time, nearly all of them
//      pixel-identical). So a byte difference is decoded and compared pixel
//      by pixel: identical pixels → "re-encoded" (not a leak); any differing
//      pixel → "changed", reported with its pixel count and max channel delta.
//   3. THE WIRE IS CONTROLLED TOO. The per-test IR documents (what the three
//      runtimes rendered) must be byte-identical except the plan's
//      wireCarriers — an extractor or bake change that reaches a document the
//      plan did not name is a leak even before a pixel moves.
//
// Usage: node control-check.mjs <preRun> <postRun> [--lane L1-lists-bakes] [--sections a,b,c] [--json out.json]
// Exit 1 on any leak (a changed capture or wire document outside the carriers). A wire document
// that is byte-different only because an earlier carrier renumbered the section's component ids
// is reported as "renumbered", not as a leak — see the wire block for the exact rule.
// Calibrations the plan requires before this is trusted (§6): leaks on
// wave52-calib → wave52-final; 0/0 on wave52-preview → wave52-ship; and on the
// cross-host pair wave52-ship → wave53-open (five sections) web re-encoded +
// changed = 236 with counter-suffix among the changed, iOS/Android 0.
import { createHash } from 'node:crypto';
import { createRequire } from 'node:module';
import { existsSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { resolveRunDir } from '../../score-gate.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const REPO = path.resolve(HERE, '..', '..', '..', '..');
const sharp = createRequire(path.join(REPO, 'package.json'))('sharp');
sharp.concurrency(1);   // runs beside gates; one thread
const EXP = JSON.parse(readFileSync(path.join(REPO, 'tools/titan/results/wave53-plan/expectations.json'), 'utf8'));

const args = process.argv.slice(2);
const opt = (name) => { const i = args.indexOf(name); return i < 0 ? null : args.splice(i, 2)[1]; };
const laneId = opt('--lane'), sectionsOpt = opt('--sections'), jsonOut = opt('--json');
const [preId, postId] = args;
const pre = resolveRunDir(preId ?? ''), post = resolveRunDir(postId ?? '');
if (!pre || !post) { console.error('usage: control-check.mjs <preRun> <postRun> [--lane <id>] [--sections a,b] [--json out]'); process.exit(2); }
// Carriers: a lane's, or the union across lanes; the wire set likewise.
const lane = laneId ? EXP.lanes[laneId] : null;
if (laneId && !lane) { console.error(`no lane ${laneId} in expectations.json (have ${Object.keys(EXP.lanes).join(', ')})`); process.exit(2); }
const carriers = lane ? lane.captureCarriers : EXP.unionCaptureCarriers;
const wireCarriers = new Set(lane ? lane.wireCarriers : EXP.unionWireCarriers);
const only = sectionsOpt ? new Set(sectionsOpt.split(',')) : null;
const DIRS = { web: 'screenshots', ios: 'ios-screenshots', android: 'android-screenshots' };
const sha = (f) => createHash('sha1').update(readFileSync(f)).digest('hex');

// Decode both PNGs and compare pixels; returns null when identical, else the difference's size.
async function pixelDiff(a, b) {
  const [x, y] = await Promise.all([a, b].map((f) => sharp(f).ensureAlpha().raw().toBuffer({ resolveWithObject: true })));
  if (x.info.width !== y.info.width || x.info.height !== y.info.height) return { px: Infinity, maxDelta: 255, note: `size ${x.info.width}x${x.info.height} vs ${y.info.width}x${y.info.height}` };
  let px = 0, maxDelta = 0;
  for (let i = 0; i < x.data.length; i += 4) {
    let d = 0;
    for (let c = 0; c < 4; c++) d = Math.max(d, Math.abs(x.data[i + c] - y.data[i + c]));
    if (d) { px++; if (d > maxDelta) maxDelta = d; }
  }
  return px ? { px, maxDelta } : null;
}

const out = { pre: preId, post: postId, lane: laneId ?? 'union', sections: {}, totals: {}, leaks: [], wireLeaks: [], wireRenumbered: [] };
for (const p of Object.keys(DIRS)) out.totals[p] = { compared: 0, identical: 0, reencoded: 0, changed: 0, carriers: 0 };
let wireCompared = 0, wireChanged = 0, wireRenumbered = 0;
for (const sec of readdirSync(path.join(post, 'sections')).sort()) {
  if (only && !only.has(sec)) continue;
  const row = {};
  for (const [platform, dir] of Object.entries(DIRS)) {
    const a = path.join(pre, 'sections', sec, dir), b = path.join(post, 'sections', sec, dir);
    if (!existsSync(a) || !existsSync(b)) continue;
    const r = { compared: 0, identical: 0, reencoded: 0, changed: [], carriers: [] };
    const allowed = new Set(carriers[platform] || []);
    for (const f of readdirSync(b).filter((x) => x.endsWith('.png') && !/__\d+\.png$/.test(x))) {
      const stem = f.replace(/\.png$/, '');
      const fa = path.join(a, f), fb = path.join(b, f);
      if (!existsSync(fa)) { r.changed.push({ stem, note: 'absent before' }); continue; }
      r.compared++;
      if (sha(fa) === sha(fb)) { r.identical++; continue; }
      const d = await pixelDiff(fa, fb);
      if (!d) { r.reencoded++; continue; }           // same picture, different bytes: the new host's encoder
      r.changed.push({ stem, ...d });
      if (allowed.has(stem)) r.carriers.push(stem); else out.leaks.push({ sec, platform, stem, ...d });
    }
    row[platform] = r;
    const t = out.totals[platform];
    t.compared += r.compared; t.identical += r.identical; t.reencoded += r.reencoded; t.changed += r.changed.length; t.carriers += r.carriers.length;
    console.log(`${sec.padEnd(20)} ${platform.padEnd(8)} compared ${String(r.compared).padStart(2)}  identical ${String(r.identical).padStart(2)}  re-encoded ${String(r.reencoded).padStart(2)}  changed ${String(r.changed.length).padStart(2)} (carriers ${r.carriers.length})`);
    for (const c of r.changed) if (!allowed.has(c.stem)) console.log(`      LEAK  ${c.stem}  ${c.note ?? `${c.px} px, max channel delta ${c.maxDelta}`}`);
  }
  // The wire: every per-test IR document, byte for byte, except the named carriers — with one
  // measured class set apart. The extractor numbers components with ONE counter per section run
  // (`<name>-NNN`, in tests.list order), so a carrier that gains components renumbers every later
  // document's ids without touching its content (wave 53 hh-probe: counter-suffix 23 → 29
  // components shifted the 15 cssom-*-setter docs after it by exactly +6; captures byte-identical).
  // Such a document is "renumbered": byte-different, identical once the id counter is stripped,
  // AND its shift equals the running component-count delta of the content-changed documents before
  // it in tests.list order. Anything else that is byte-different is a content change: a carrier or
  // a leak. A renumbering whose shift the earlier carriers do NOT explain is still a leak.
  const wa = path.join(pre, 'sections', sec, 'per-test-ir'), wb = path.join(post, 'sections', sec, 'per-test-ir');
  if (existsSync(wa) && existsSync(wb)) {
    const files = readdirSync(wb).filter((x) => x.endsWith('.json'));
    // tests.list order (css/<sec>/<path>.html → wpt__<sec>__<path with __>); files it does not name sort last.
    const listFile = path.join(post, 'sections', sec, 'tests.list');
    const order = new Map(existsSync(listFile) ? readFileSync(listFile, 'utf8').split('\n').filter(Boolean)
      .map((l, i) => ['wpt__' + l.replace(/^css\//, '').replace(/\.html?$/, '').split('/').join('__'), i]) : []);
    files.sort((x, y) => (order.get(x.replace(/\.json$/, '')) ?? 1e9) - (order.get(y.replace(/\.json$/, '')) ?? 1e9) || x.localeCompare(y));
    const comps = (d) => Array.isArray(d.components) ? d.components : Object.values(d.components ?? {});
    const idNum = (c) => Number((String(c.id ?? '').match(/-(\d+)$/) ?? [])[1]);
    const norm = (d) => JSON.stringify(d, (k, v) => (k === 'id' || k === 'parent') && typeof v === 'string' ? v.replace(/-\d+$/, '') : v);
    let runShift = 0; const explainedBy = [];
    for (const f of files) {
      const fa = path.join(wa, f), fb = path.join(wb, f), stem = f.replace(/\.json$/, '');
      wireCompared++;
      if (existsSync(fa) && sha(fa) === sha(fb)) continue;
      const a = existsSync(fa) ? JSON.parse(readFileSync(fa, 'utf8')) : null, b = JSON.parse(readFileSync(fb, 'utf8'));
      const ca = a ? comps(a) : [], cb = comps(b);
      // Every id shifted by the same amount, content identical → a renumbering candidate.
      const shifts = new Set(cb.map((c, i) => ca[i] ? idNum(c) - idNum(ca[i]) : NaN));
      const shift = shifts.size === 1 ? [...shifts][0] : NaN;
      if (a && norm(a) === norm(b) && Number.isFinite(shift) && shift === runShift && shift !== 0) {
        wireRenumbered++; out.wireRenumbered.push({ sec, stem, shift, explainedBy: [...explainedBy] });
        continue;                                      // ids only, accounted for by the carriers before it
      }
      wireChanged++;
      runShift += cb.length - ca.length;               // this document's component-count delta shifts the later ids
      // A leak that shifts the counter is named in the later rows too, tagged — the renumbering it
      // causes is then "explained" only in the arithmetic sense; the leak itself fails the control.
      const delta = `${cb.length - ca.length >= 0 ? '+' : ''}${cb.length - ca.length}`;
      if (!wireCarriers.has(stem)) { out.wireLeaks.push({ sec, stem }); console.log(`      WIRE LEAK  ${sec}/${stem}`); explainedBy.push(`${stem} ${delta} (LEAK)`); }
      else explainedBy.push(`${stem} ${delta}`);
    }
  }
  out.sections[sec] = row;
}
for (const [p, t] of Object.entries(out.totals)) console.log(`total ${p}: ${t.compared} compared · ${t.identical} identical · ${t.reencoded} re-encoded · ${t.changed} changed (${t.carriers} carriers)`);
console.log(`wire: ${wireCompared} documents compared · ${wireChanged} content-changed · ${wireRenumbered} renumbered (ids only, explained by earlier carriers) · ${out.wireLeaks.length} outside the plan's wire carriers`);
for (const r of out.wireRenumbered) console.log(`      renumbered ${r.sec}/${r.stem}  shift ${r.shift >= 0 ? '+' : ''}${r.shift}  by ${r.explainedBy.join(', ') || '(none!)'}`);
out.wire = { compared: wireCompared, changed: wireChanged, renumbered: wireRenumbered };
if (jsonOut) writeFileSync(jsonOut, JSON.stringify(out, null, 1));
const bad = out.leaks.length + out.wireLeaks.length;
console.log(bad ? `\nCONTROL FAILED: ${out.leaks.length} capture(s) and ${out.wireLeaks.length} wire document(s) changed outside the carriers` : '\nCONTROL HOLDS: every changed capture and wire document is a carrier the plan named');
process.exit(bad ? 1 : 0);
