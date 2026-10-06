#!/usr/bin/env node
// tools/titan/results/wave52-gate/control-check.mjs
//
// The control of the two wave-52 closing-gate fixes. Each fix is reachable
// only through a wire shape, so the set of captures it MAY change is known
// before the device runs:
//   ch fix   (Android only)  a per-test IR document with a `"u":"CH"` length
//   li fix   (iOS + Android) a document with an <li> (meta.sourceTag "li")
//                            whose authored Display is not LIST_ITEM
// Everything else must come out BYTE-IDENTICAL to the pre-fix run — web in
// full, iOS outside the li carriers, Android outside both carrier sets. A
// changed capture outside its carrier set means a fix leaks (or the pipeline
// is not deterministic), and that is a finding, not noise.
//
// Usage: node control-check.mjs <preFixRun> <postFixRun>
// Compares every section present in <postFixRun>. Exit 1 on any leak.
import { createHash } from 'node:crypto';
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { resolveRunDir } from '../../score-gate.mjs';

const [preId, postId] = process.argv.slice(2);
const pre = resolveRunDir(preId ?? ''), post = resolveRunDir(postId ?? '');
if (!pre || !post) { console.error('usage: control-check.mjs <preFixRun> <postFixRun>'); process.exit(2); }
const DIRS = { web: 'screenshots', ios: 'ios-screenshots', android: 'android-screenshots' };
const sha = (f) => createHash('sha1').update(readFileSync(f)).digest('hex');

let leaks = 0;
const totals = { web: [0, 0], ios: [0, 0], android: [0, 0] };   // [compared, changed]
for (const sec of readdirSync(path.join(post, 'sections')).sort()) {
  const irDir = path.join(post, 'sections', sec, 'per-test-ir');
  if (!existsSync(irDir)) continue;
  // Carrier sets, read off the post-fix run's own wire documents.
  const ch = new Set(), li = new Set();
  for (const f of readdirSync(irDir)) {
    const text = readFileSync(path.join(irDir, f), 'utf8');
    const stem = f.replace(/\.json$/, '');
    if (/"u"\s*:\s*"CH"/.test(text)) ch.add(stem);
    const doc = JSON.parse(text);
    const comps = Array.isArray(doc.components) ? doc.components : Object.values(doc.components || {});
    for (const c of comps) {
      if (c.meta?.sourceTag !== 'li') continue;
      const d = (c.properties || []).filter((p) => p.type === 'Display').at(-1);
      if (d && String(d.data).toUpperCase().replace('-', '_') !== 'LIST_ITEM') li.add(stem);
    }
  }
  const allowed = { web: new Set(), ios: li, android: new Set([...ch, ...li]) };
  for (const [platform, dir] of Object.entries(DIRS)) {
    const a = path.join(pre, 'sections', sec, dir), b = path.join(post, 'sections', sec, dir);
    if (!existsSync(a) || !existsSync(b)) continue;
    const changed = [];
    // Composed captures only (component PNGs carry a __N suffix), as the driver counts them.
    for (const f of readdirSync(b).filter((x) => x.endsWith('.png') && !/__\d+\.png$/.test(x))) {
      if (!existsSync(path.join(a, f))) { changed.push(f.replace(/\.png$/, '') + ' (absent before)'); continue; }
      totals[platform][0]++;
      if (sha(path.join(a, f)) !== sha(path.join(b, f))) changed.push(f.replace(/\.png$/, ''));
    }
    totals[platform][1] += changed.length;
    const outside = changed.filter((s) => !allowed[platform].has(s.replace(' (absent before)', '')));
    leaks += outside.length;
    console.log(`${sec.padEnd(20)} ${platform.padEnd(8)} changed ${String(changed.length).padStart(2)}  carriers ${String(allowed[platform].size).padStart(2)}  outside-carriers ${outside.length}`);
    outside.forEach((s) => console.log(`      LEAK  ${s}`));
  }
}
for (const [p, [n, c]] of Object.entries(totals)) console.log(`total ${p}: ${c} of ${n} captures changed`);
console.log(leaks ? `\nCONTROL FAILED: ${leaks} capture(s) changed outside the fixes' carrier sets` : '\nCONTROL HOLDS: every changed capture is a carrier of the fix that may change it');
process.exit(leaks ? 1 : 0);
