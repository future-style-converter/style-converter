// S4 independent census: components declaring BOTH a Transform-family
// property and an overflow whose used value CLIPS, in wave49-final per-test IR.
import fs from 'node:fs';
import path from 'node:path';
const ROOT = 'tools/titan/runs/wave49-final/sections';
const CLIPPY = new Set(['HIDDEN','CLIP','SCROLL','AUTO','OVERLAY']);
const TRANSFORM_TYPES = new Set(['Transform','Translate','Rotate','Scale','TransformOrigin','TransformStyle','Perspective','PerspectiveOrigin','TransformBox','BackfaceVisibility']);
let tests=0, compsWithBoth=0;
const rows=[];
for (const sec of fs.readdirSync(ROOT)) {
  const d = path.join(ROOT, sec, 'per-test-ir');
  if (!fs.existsSync(d)) continue;
  for (const f of fs.readdirSync(d)) {
    if (!f.endsWith('.json')) continue;
    tests++;
    const doc = JSON.parse(fs.readFileSync(path.join(d,f),'utf8'));
    const comps = doc.components || [];
    for (const c of comps) {
      const props = c.properties || [];
      const byType = new Map();
      for (const p of props) byType.set(p.type, p.data);
      // transform-proper only (the ones that warp geometry)
      const hasT = ['Transform','Translate','Rotate','Scale'].some(t=>byType.has(t));
      const ox = byType.get('OverflowX'), oy = byType.get('OverflowY');
      const hasO = ox!==undefined || oy!==undefined;
      if (!hasT || !hasO) continue;
      compsWithBoth++;
      // used-value coercion css-overflow-3 3.1: visible computed with a
      // non-visible other axis becomes auto (clips).
      const norm = v => (typeof v === 'string' ? v.toUpperCase() : (v && v.type) ? String(v.type).toUpperCase() : JSON.stringify(v));
      const nx = ox===undefined ? 'VISIBLE' : norm(ox);
      const ny = oy===undefined ? 'VISIBLE' : norm(oy);
      const usedX = (nx==='VISIBLE' && ny!=='VISIBLE' && ny!=='CLIP') ? 'AUTO' : nx;
      const usedY = (ny==='VISIBLE' && nx!=='VISIBLE' && nx!=='CLIP') ? 'AUTO' : ny;
      const clips = CLIPPY.has(usedX) || CLIPPY.has(usedY);
      rows.push({sec, test:f.replace(/\.json$/,''), id:c.id, name:c.name,
                 transform: JSON.stringify(['Transform','Translate','Rotate','Scale'].map(t=>byType.get(t)).filter(Boolean)),
                 ox:nx, oy:ny, clips});
    }
  }
}
const clipRows = rows.filter(r=>r.clips);
console.log(`tests scanned=${tests}  components with transform+overflow=${compsWithBoth}  distinct tests=${new Set(rows.map(r=>r.sec+'/'+r.test)).size}`);
console.log(`CLIPPING components=${clipRows.length}  distinct tests=${new Set(clipRows.map(r=>r.sec+'/'+r.test)).size}`);
for (const r of clipRows) console.log(`  CLIP ${r.sec}/${r.test}  ${r.id} (${r.name}) ox=${r.ox} oy=${r.oy} T=${r.transform}`);
console.log('--- non-clipping tests ---');
for (const t of [...new Set(rows.filter(r=>!r.clips).map(r=>r.sec+'/'+r.test))]) console.log('  ', t);
