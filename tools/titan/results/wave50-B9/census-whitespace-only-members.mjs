// Corpus census: glyph-bearing run MEMBERS whose text is entirely white
// space, and what property types they carry (the fold's admission gate).
import fs from "fs"; import path from "path";
const ROOT="tools/titan/runs/wave49-final/sections";
let hosts=0, wsOnly=0;
const props=new Map();
for (const sec of fs.readdirSync(ROOT)) {
  const dir=path.join(ROOT,sec,"per-test-ir"); if(!fs.existsSync(dir)) continue;
  for (const f of fs.readdirSync(dir)) {
    if(!f.endsWith(".json")) continue;
    const doc=JSON.parse(fs.readFileSync(path.join(dir,f),"utf8"));
    const comps=doc.components||[];
    const byId=new Map(comps.map(c=>[c.id,c]));
    for (const host of comps) {
      const runs=host.meta?.runs; if(!runs) continue;
      hosts++;
      const kids=comps.filter(c=>c.slot?.parent===host.id);
      for (const k of kids) {
        const t=k.text;
        if(t==null||t==="") continue;
        if(/\S/.test(t)) continue;  // has a glyph
        wsOnly++;
        const types=(k.properties||[]).map(p=>p.type).sort().join(",");
        const key=`${sec}/${f.replace(/^wpt__/,"")} member=${k.name} tag=${k.meta?.sourceTag} text=${JSON.stringify(t)} props=[${types}]`;
        console.log(key);
        for(const p of (k.properties||[])) props.set(p.type,(props.get(p.type)||0)+1);
      }
    }
  }
}
console.log(`\n--- ${hosts} runs-hosts scanned; ${wsOnly} whitespace-only glyph members`);
console.log("property histogram:", [...props].sort((a,b)=>b[1]-a[1]).map(([k,v])=>`${k}×${v}`).join(" "));
