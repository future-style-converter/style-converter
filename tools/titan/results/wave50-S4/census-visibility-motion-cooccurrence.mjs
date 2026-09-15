// S4: the OTHER two co-declarations the .engineVisibility move also changes.
import fs from 'node:fs'; import path from 'node:path';
const ROOT='tools/titan/runs/wave49-final/sections';
const out={visTransform:[], motionOverflow:[], visOnly:[]};
for (const sec of fs.readdirSync(ROOT)) {
  const d=path.join(ROOT,sec,'per-test-ir'); if(!fs.existsSync(d)) continue;
  for (const f of fs.readdirSync(d)) { if(!f.endsWith('.json')) continue;
    const doc=JSON.parse(fs.readFileSync(path.join(d,f),'utf8'));
    for (const c of doc.components||[]) {
      const t=new Map((c.properties||[]).map(p=>[p.type,p.data]));
      const hasT=['Transform','Translate','Rotate','Scale'].some(k=>t.has(k));
      const hasVis=t.has('Visibility');
      const hasMotion=['OffsetPath','OffsetDistance','OffsetRotate','OffsetAnchor','OffsetPosition'].some(k=>t.has(k));
      const hasOF=t.has('OverflowX')||t.has('OverflowY');
      if(hasVis&&hasT) out.visTransform.push(`${sec}/${f} ${c.id} vis=${JSON.stringify(t.get('Visibility'))}`);
      if(hasMotion&&hasOF) out.motionOverflow.push(`${sec}/${f} ${c.id}`);
      if(hasVis) out.visOnly.push(`${sec}/${f} ${c.id} vis=${JSON.stringify(t.get('Visibility'))}`);
    }
  }
}
for (const [k,v] of Object.entries(out)) { console.log(`== ${k}: ${v.length}`); v.slice(0,20).forEach(x=>console.log('   ',x)); }
