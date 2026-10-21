#!/usr/bin/env node
// tools/titan/results/wave55-gate/control-check.mjs — the wave-54 copy (tools/titan/results/wave54-gate/control-check.mjs) with the
// expectations path → wave55-plan and TWO capture classes the wave-55 plan adds (PLAN §6, expectations.json `readerChanges`):
//   MAY-MOVE     a changed capture whose stem is in expectations.json unionMayMoveCaptures[<platform>] (L6's legacy-border radius,
//                C1 / C2 / D2): printed, NOT a leak — its score is bound by adjudicate.mjs R5 (within 0.002) through
//                mustNotMoveComplement, and a move beyond 0.002 is read by probe-readout.mjs as a look-first leak signal;
//   RING-FENCED  a changed capture of expectations.json ringFenced.captures: "report plainly", never a leak, never a carrier
//                (read BEFORE may-move: basic-blur android is both, D2 — the ring-fence decides how it is reported).
// With --lane <id>, the lane's own mayMove (lanes.<id>.mayMove) is the may-move set. Everything else is the wave-53/54 rule, verbatim.
// Plan-skeptic round 1 (instrument-teeth S1): the PRE run's files are iterated too — a capture or a per-test IR document that
// DISAPPEARS is a counted leak ("absent after" / WIRE LEAK), never a silent skip; and a scope that compares nothing is refused
// (exit 2): a --sections name absent from either run, a section (or, with --sections, a listed section) present in one run
// only, or a listed section that compares 0 captures.
// Calibrated before it is trusted (tools/titan/results/wave55-gate/readers-calibration.txt).
//
// (wave-53 header follows)
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
// Usage: node control-check.mjs <preRun> <postRun> [--lane L6-android-paint-order] [--sections a,b,c] [--json out.json]
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
const EXP = JSON.parse(readFileSync(path.join(REPO, 'tools/titan/results/wave55-plan/expectations.json'), 'utf8'));

const args = process.argv.slice(2);
// Round 2 (S5): a flag given without its value is a usage error (exit 2).
const opt = (name) => {
  const i = args.indexOf(name); if (i < 0) return null;
  const v = args[i + 1];
  if (v === undefined || v.startsWith('--')) { console.error(`usage: control-check.mjs <preRun> <postRun> [--lane <id>] [--sections a,b] [--json out]\n  REFUSED — ${name} given without a value`); process.exit(2); }
  args.splice(i, 2); return v;
};
const laneId = opt('--lane'), sectionsOpt = opt('--sections'), jsonOut = opt('--json');
const [preId, postId] = args;
const pre = resolveRunDir(preId ?? ''), post = resolveRunDir(postId ?? '');
if (!pre || !post) { console.error('usage: control-check.mjs <preRun> <postRun> [--lane <id>] [--sections a,b] [--json out]'); process.exit(2); }
// Carriers: a lane's, or the union across lanes; the wire set likewise.
const lane = laneId ? EXP.lanes[laneId] : null;
if (laneId && !lane) { console.error(`no lane ${laneId} in expectations.json (have ${Object.keys(EXP.lanes).join(', ')})`); process.exit(2); }
// Units the probe reverted (expectations.probeDecisions) give their carriers back: a change on one of those
// stems at the closing gate is a leak again, because the code that moved them is no longer in the tree.
const withdrawn = ((EXP.probeDecisions && EXP.probeDecisions.reverted) || []).map((d) => d.carriersWithdrawn || {});
const withdrawnCap = (platform) => new Set(withdrawn.flatMap((w) => (w.captures || {})[platform] || []));
const withdrawnWire = new Set(withdrawn.flatMap((w) => w.wire || []));
const rawCarriers = lane ? lane.captureCarriers : EXP.unionCaptureCarriers;
const carriers = Object.fromEntries(Object.entries(rawCarriers).map(([pf, stems]) => [pf, (stems || []).filter((s) => !withdrawnCap(pf).has(s))]));
const wireCarriers = new Set((lane ? lane.wireCarriers : EXP.unionWireCarriers).filter((s) => !withdrawnWire.has(s)));
if (withdrawn.length) console.log(`carriers withdrawn by the probe's reverts: ${withdrawn.flatMap((w) => Object.values(w.captures || {}).flat()).length} capture stem(s), ${withdrawnWire.size} wire stem(s)`);
const only = sectionsOpt ? new Set(sectionsOpt.split(',')) : null;
// Round 1 (S1): the sections compared are the union of both runs' (or the listed ones); one present in one run only, or a
// listed one absent from either, refuses the read — a missing section must never read as "nothing changed".
const secsOf = (r) => new Set(existsSync(path.join(r, 'sections')) ? readdirSync(path.join(r, 'sections')) : []);
const preSecs = secsOf(pre), postSecs = secsOf(post);
const scope = only ? [...only] : [...new Set([...preSecs, ...postSecs])].sort();
const scopeBad = scope.filter((sec) => !preSecs.has(sec) || !postSecs.has(sec));
if (scopeBad.length) {
  console.error(`REFUSED — section(s) ${scopeBad.join(', ')} absent from ${scopeBad.map((sec) => [!preSecs.has(sec) ? preId : null, !postSecs.has(sec) ? postId : null].filter(Boolean).join(' and ')).join(' / ')}: a section that cannot be compared is not "unchanged" (pass --sections for a partial run; exit 2)`);
  process.exit(2);
}
// wave 55: the may-move and ring-fence classes (a lane's own mayMove with --lane, else the union).
const mayMoveSrc = lane ? (lane.mayMove || {}) : (EXP.unionMayMoveCaptures || {});
const mayMove = (platform) => new Set(mayMoveSrc[platform] || []);
const ringCaps = (platform) => new Set(((EXP.ringFenced && EXP.ringFenced.captures) || {})[platform] || []);
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

const out = { pre: preId, post: postId, lane: laneId ?? 'union', sections: {}, totals: {}, leaks: [], wireLeaks: [], wireRenumbered: [], missingCarriers: [] };
out.mayMove = []; out.ringFenced = [];
for (const p of Object.keys(DIRS)) out.totals[p] = { compared: 0, identical: 0, reencoded: 0, changed: 0, carriers: 0, mayMove: 0, ringFenced: 0 };
let wireCompared = 0, wireChanged = 0, wireRenumbered = 0;
const comparedPerSec = {};
for (const sec of scope) {
  const row = {};
  comparedPerSec[sec] = 0;
  for (const [platform, dir] of Object.entries(DIRS)) {
    const a = path.join(pre, 'sections', sec, dir), b = path.join(post, 'sections', sec, dir);
    if (!existsSync(a) && !existsSync(b)) continue;
    const r = { compared: 0, identical: 0, reencoded: 0, changed: [], carriers: [] };
    const allowed = new Set(carriers[platform] || []);
    const pngs = (d) => (existsSync(d) ? readdirSync(d).filter((x) => x.endsWith('.png') && !/__\d+\.png$/.test(x)) : []);
    // Round 1 (S1): the union of both sides — a capture present on one side only is a change, whichever side lacks it.
    for (const f of [...new Set([...pngs(a), ...pngs(b)])].sort()) {
      const stem = f.replace(/\.png$/, '');
      const fa = path.join(a, f), fb = path.join(b, f);
      // Round 2 (S3): a CARRIER may change, never vanish — one present on one side only is MISSING and refuses the read (exit 2),
      // as a missing section does; it was counted "changed (carrier)" and the control HELD on a capture that was not taken.
      if (!existsSync(fa)) { r.changed.push({ stem, note: 'absent before' }); if (!allowed.has(stem)) out.leaks.push({ sec, platform, stem, note: 'absent before' }); else out.missingCarriers.push({ sec, platform, stem, note: 'absent before' }); continue; }
      if (!existsSync(fb)) { r.changed.push({ stem, note: 'absent after' }); if (!allowed.has(stem)) out.leaks.push({ sec, platform, stem, note: 'absent after' }); else out.missingCarriers.push({ sec, platform, stem, note: 'absent after' }); continue; }
      r.compared++;
      if (sha(fa) === sha(fb)) { r.identical++; continue; }
      const d = await pixelDiff(fa, fb);
      if (!d) { r.reencoded++; continue; }           // same picture, different bytes: the new host's encoder
      r.changed.push({ stem, ...d });
      if (allowed.has(stem)) r.carriers.push(stem);
      else if (ringCaps(platform).has(stem)) { r.ringFenced = (r.ringFenced || 0) + 1; out.ringFenced.push({ sec, platform, stem, ...d }); }   // first: report only
      else if (mayMove(platform).has(stem)) { r.mayMove = (r.mayMove || 0) + 1; out.mayMove.push({ sec, platform, stem, ...d }); }
      else out.leaks.push({ sec, platform, stem, ...d });
    }
    row[platform] = r;
    comparedPerSec[sec] += r.compared;
    const t = out.totals[platform];
    t.compared += r.compared; t.identical += r.identical; t.reencoded += r.reencoded; t.changed += r.changed.length; t.carriers += r.carriers.length;
    t.mayMove += r.mayMove || 0; t.ringFenced += r.ringFenced || 0;
    console.log(`${sec.padEnd(20)} ${platform.padEnd(8)} compared ${String(r.compared).padStart(2)}  identical ${String(r.identical).padStart(2)}  re-encoded ${String(r.reencoded).padStart(2)}  changed ${String(r.changed.length).padStart(2)} (carriers ${r.carriers.length})`);
    for (const c of r.changed) {
      if (allowed.has(c.stem) && c.note) { console.log(`      MISSING  ${c.stem}  ${c.note} (a carrier: it may change, never vanish)`); continue; }
      if (allowed.has(c.stem)) continue;
      const what = c.note ?? `${c.px} px, max channel delta ${c.maxDelta}`;
      if (c.note) console.log(`      LEAK  ${c.stem}  ${what}`);                                   // a capture on one side only (round 1)
      else if (ringCaps(platform).has(c.stem)) console.log(`      RING-FENCED  ${c.stem}  ${what}  — report plainly in the PR`);
      else if (mayMove(platform).has(c.stem)) console.log(`      may-move  ${c.stem}  ${what}  (L6 band: its score is bound by R5)`);
      else console.log(`      LEAK  ${c.stem}  ${what}`);
    }
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
  if (existsSync(wa) || existsSync(wb)) {
    // Round 1 (S1): a document present before and absent after is a WIRE LEAK ("absent after"); one absent before is read
    // by the content rule below as a content change (a carrier, or a leak).
    const filesA = existsSync(wa) ? readdirSync(wa).filter((x) => x.endsWith('.json')) : [];
    for (const f of filesA) {
      if (existsSync(path.join(wb, f))) continue;
      const stem = f.replace(/\.json$/, ''); wireCompared++; wireChanged++;
      if (!wireCarriers.has(stem)) { out.wireLeaks.push({ sec, stem, note: 'absent after' }); console.log(`      WIRE LEAK  ${sec}/${stem}  absent after`); }
    }
    const files = existsSync(wb) ? readdirSync(wb).filter((x) => x.endsWith('.json')) : [];
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
// Round 1 (S1): a listed (or any) section that compared NO capture read nothing — a refusal, not a hold.
const empty = scope.filter((sec) => comparedPerSec[sec] === 0 && !(out.leaks.some((l) => l.sec === sec)));
if (empty.length) { console.error(`REFUSED — section(s) ${empty.join(', ')} compared 0 captures: nothing was read there (exit 2)`); process.exit(2); }
for (const [p, t] of Object.entries(out.totals)) console.log(`total ${p}: ${t.compared} compared · ${t.identical} identical · ${t.reencoded} re-encoded · ${t.changed} changed (${t.carriers} carriers, ${t.mayMove} may-move, ${t.ringFenced} ring-fenced)`);
console.log(`wire: ${wireCompared} documents compared · ${wireChanged} content-changed · ${wireRenumbered} renumbered (ids only, explained by earlier carriers) · ${out.wireLeaks.length} outside the plan's wire carriers`);
for (const r of out.wireRenumbered) console.log(`      renumbered ${r.sec}/${r.stem}  shift ${r.shift >= 0 ? '+' : ''}${r.shift}  by ${r.explainedBy.join(', ') || '(none!)'}`);
out.wire = { compared: wireCompared, changed: wireChanged, renumbered: wireRenumbered };
if (jsonOut) writeFileSync(jsonOut, JSON.stringify(out, null, 1));
if (out.missingCarriers.length) {                       // round 2 (S3): refused before any verdict
  console.error(`REFUSED — ${out.missingCarriers.length} carrier capture(s) present on one side only (${out.missingCarriers.map((m) => `${m.platform} ${m.stem} ${m.note}`).join('; ')}): a carrier that was not captured was not compared (exit 2)`);
  process.exit(2);
}
const bad = out.leaks.length + out.wireLeaks.length;
console.log(bad ? `\nCONTROL FAILED: ${out.leaks.length} capture(s) and ${out.wireLeaks.length} wire document(s) changed outside the carriers` : '\nCONTROL HOLDS: every changed capture and wire document is a carrier the plan named');
process.exit(bad ? 1 : 0);
