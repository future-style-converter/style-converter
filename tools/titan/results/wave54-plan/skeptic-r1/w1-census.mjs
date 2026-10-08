// Plan skeptic r1, check (2j): L6 W1 radius — every runs host with an out-of-flow (ABSOLUTE|FIXED) child member; the
// member's inertness (byte-restatement of Compose InertOutOfFlowMember.admits: no children/runs/decorations; only
// Position abs|fixed, Color alpha 0, Hyphens declared); whether the split is MID-WORD (letter before, letter after);
// and whether the HOST declares Hyphens AUTO (NodeRenderer.ts:339 passes styles.hyphens === 'auto', the host's own).
import fs from 'node:fs'; import path from 'node:path';
const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../../../..');
const SD = path.join(ROOT, 'tools/titan/runs', process.argv[2] || 'wave53-final', 'sections');
const prop = (c, t) => (c.properties || []).filter((p) => p.type === t).at(-1)?.data;
const inert = (m) => { if ((m.meta?.runs?.length) || (m.meta?.decorations?.length)) return false; let oof = false, tr = false;
  for (const p of m.properties || []) { if (p.type === 'Position') { if (['ABSOLUTE', 'FIXED'].includes(String(p.data).toUpperCase())) oof = true; else return false; }
    else if (p.type === 'Color') { const a = p.data?.srgb?.a; if (typeof a === 'number' && a <= 0) tr = true; else return false; } else if (p.type === 'Hyphens') {} else return false; } return oof && tr; };
let members = 0, inertN = 0; const rows = [];
for (const sec of fs.readdirSync(SD).sort()) {
  const d = path.join(SD, sec, 'per-test-ir'); if (!fs.existsSync(d)) continue;
  for (const f of fs.readdirSync(d).filter((x) => x.endsWith('.json')).sort()) {
    const comps = JSON.parse(fs.readFileSync(path.join(d, f), 'utf8')).components; const byName = new Map(comps.map((c) => [c.name, c]));
    const hasKids = new Set(comps.map((c) => c.slot?.parent).filter(Boolean));
    for (const h of comps) { const runs = h.meta?.runs; if (!Array.isArray(runs)) continue;
      runs.forEach((r, i) => { if (!r.child) return; const m = byName.get(r.child); if (!m || !['ABSOLUTE', 'FIXED'].includes(String(prop(m, 'Position')).toUpperCase())) return;
        members++; const isInert = inert(m) && !hasKids.has(m.id); if (isInert) inertN++;
        const before = runs[i - 1]?.text ?? '', after = runs[i + 1]?.text ?? ''; const mid = /\p{L}$/u.test(before) && /^\p{L}/u.test(after);
        const hy = String(prop(h, 'Hyphens') ?? '').toUpperCase();
        rows.push(`${sec}/${f.replace(/\.json$/, '')} ${m.name} inert=${isInert} midword=${mid} hostHyphens=${hy || '-'}${isInert && mid && hy === 'AUTO' ? '  <== W1 JOINS' : ''}`); }); }
  }
}
rows.forEach((r) => console.log(r)); console.log(`out-of-flow run members ${members}, inert ${inertN}, W1 joins ${rows.filter((r) => r.includes('W1 JOINS')).length} in ${new Set(rows.filter((r) => r.includes('W1 JOINS')).map((r) => r.split(' ')[0])).size} documents`);
