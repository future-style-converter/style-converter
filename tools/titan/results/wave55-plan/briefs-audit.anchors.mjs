#!/usr/bin/env node
// tools/titan/results/wave55-plan/briefs-audit.anchors.mjs
//
// The wave-55 auditor's ANCHOR read-out: every `file:L[-M]` AND every bare `:L[-M]` in the
// seven briefs (a bare anchor binds to the nearest file token to its LEFT on the same brief
// line, else on the previous line), printed with the target line's text so a reader can
// confirm the brief's quoted identifier sits there. READ-ONLY; stdout only.
//
// Usage: node briefs-audit.anchors.mjs <tracked-list.txt> [brief-stem]
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../../..');
const PLAN = path.join(ROOT, 'tools/titan/results/wave55-plan');
const BRIEFS = (process.argv[3] ? [process.argv[3]] : ['hyphen-ios-linebox', 'cbb-android', 'positioned-parent-slot',
  'ua-heading-margin', 'counter-suffix-rows', 'android-paint-order', 'instruments']);
const tracked = fs.readFileSync(process.argv[2], 'utf8').split('\n').filter(Boolean);
// Suffix index over tracked paths (the anchors only ever point at tracked source).
const suffix = new Map();
for (const p of tracked) { const parts = p.split('/');
  for (let i = 0; i < parts.length; i++) { const s = parts.slice(i).join('/'); if (!suffix.has(s)) suffix.set(s, new Set()); suffix.get(s).add(p); } }
const cache = new Map();
const text = (p) => { if (!cache.has(p)) cache.set(p, fs.readFileSync(path.join(ROOT, p), 'utf8').split('\n')); return cache.get(p); };
const EXT = '(?:kts|kt|swift|tsx|ts|mjs|cjs|js|py|sh|md|json)';
// One combined scan: either a file token (group 1) with an optional anchor, or a bare anchor (group 4).
const RE = new RegExp(`([A-Za-z0-9_…./-]*[A-Za-z0-9_-]\\.${EXT})(?::(\\d+)(?:[-–](\\d+))?)?(?![A-Za-z0-9])|(?<![A-Za-z0-9_.)/])(?:^|(?<=[\\s(\`,/]))[:](\\d{1,5})(?:[-–](\\d{1,5}))?(?![0-9])`, 'g');
const resolve = (tok) => { let t = tok; if (t.includes('…/')) t = t.slice(t.lastIndexOf('…/') + 2);
  const c = suffix.get(t); return c && c.size === 1 ? [...c][0] : (c ? `AMBIG:${[...c].join(',')}` : null); };
for (const b of BRIEFS) {
  const lines = fs.readFileSync(path.join(PLAN, `${b}.md`), 'utf8').split('\n');
  console.log(`=== ${b}.md`);
  let lastFile = null;
  lines.forEach((line, i) => {
    let fileOnLine = null;
    for (const m of line.matchAll(RE)) {
      if (m[1]) { const r = resolve(m[1]); if (r && !r.startsWith('AMBIG')) { fileOnLine = r; lastFile = r; }
        if (m[2] && r && !r.startsWith('AMBIG')) emit(b, i + 1, r, +m[2], m[3] ? +m[3] : +m[2], 'file');
        else if (m[2]) console.log(`  L${i + 1} UNRESOLVED ${m[1]}:${m[2]}${m[3] ? `-${m[3]}` : ''} (${r})`);
        continue; }
      const f = fileOnLine || lastFile; const a = +m[4]; const z = m[5] ? +m[5] : a;
      if (!f) { console.log(`  L${i + 1} BARE :${a} (no file in context)`); continue; }
      emit(b, i + 1, f, a, z, fileOnLine ? 'bare' : 'bare-prevline');
    }
    if (!fileOnLine && line.trim() === '') lastFile = null; // a blank line ends the context
  });
}
function emit(b, ln, f, a, z, how) {
  const t = text(f); const n = t.length - 1;
  if (z > n || a < 1) { console.log(`  L${ln} OUT-OF-RANGE ${f}:${a}${z !== a ? `-${z}` : ''} (${n} lines) [${how}]`); return; }
  console.log(`  L${ln} ${f.split('/').pop()}:${a}${z !== a ? `-${z}` : ''} [${how}] | ${t[a - 1].trim().slice(0, 110)}`);
}
