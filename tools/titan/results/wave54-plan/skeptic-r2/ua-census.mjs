// skeptic-r2 OWN census of L5 U1-android: every h1-h6 / sub / sup component on the wire (meta.sourceTag), with
// whether it declares its own FontSize / Font / FontWeight (the author wins) and whether it has element children
// (the fold gate). A heading/sub/sup lacking FontSize OR FontWeight is reached by the Compose rule (size and/or bold).
import fs from 'node:fs'; import path from 'node:path';
const SEC = '../../runs/wave53-final/sections';
const EXP = JSON.parse(fs.readFileSync('expectations.json', 'utf8'));
const L5 = EXP.lanes['L5-ua-heading-face'];
const and = new Set(L5.captureCarriers.android), mnmA = new Set(L5.mustNotMove.filter((c) => c.endsWith(' android')).map((c) => c.slice(0, -8)));
const TAGS = /^(h[1-6]|sub|sup)$/;
const docs = new Map();
for (const sec of fs.readdirSync(SEC).sort()) {
  const dir = path.join(SEC, sec, 'per-test-ir'); if (!fs.existsSync(dir)) continue;
  for (const f of fs.readdirSync(dir).sort()) {
    const doc = JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8')); const stem = f.replace(/\.json$/, '');
    const kids = new Set(doc.components.filter((c) => c.slot && c.slot.parent).map((c) => c.slot.parent));
    for (const c of doc.components) {
      const tag = c.meta && c.meta.sourceTag && c.meta.sourceTag.toLowerCase(); if (!tag || !TAGS.test(tag)) continue;
      const t = new Set((c.properties || []).map((p) => p.type));
      const size = t.has('FontSize') || t.has('Font'), weight = t.has('FontWeight') || t.has('Font');
      const reach = !(size && weight);
      if (!docs.has(stem)) docs.set(stem, []);
      docs.get(stem).push(`${tag}${kids.has(c.id) ? '+kids' : ''}${size ? ' S' : ''}${weight ? ' W' : ''}${reach ? ' REACHED' : ''}`);
    }
  }
}
let reached = 0;
for (const [s, v] of docs) {
  const r = v.some((x) => x.endsWith('REACHED')); if (r) reached++;
  const test = s.split('__').slice(1).join('/') + '.html';
  const cls = and.has(s) ? 'android CARRIER' : mnmA.has(test) ? 'android must-not-move' : 'NOT IN L5 LISTS';
  console.log(`${r ? '*' : ' '} ${s.padEnd(70)} ${cls.padEnd(22)} ${v.join(', ')}`);
}
console.log(`documents with h1-h6/sub/sup: ${docs.size}; reached (some element lacks own size or weight): ${reached}`);
