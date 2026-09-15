// S1: FULL HEAD-vs-tree extractFixture() differential over every wave49-final
// scored test. Measures the real blast radius of the wave-50 extractor work
// (B1 validity oracle, B4 autoclose, B5-S2 col placeholder, B7 x3).
import fs from 'node:fs'; import path from 'node:path';
const R=process.env.REPO_ROOT || process.cwd();
const cur=await import(`file://${R}/tools/titan/extract-fixture.mjs`);
const old=await import(`file://${R}/tools/titan/_skeptic50-head-extract-fixture.mjs`);
const secs=fs.readdirSync(`${R}/tools/titan/runs/wave49-final/sections`);
const tests=[];
for(const s of secs){const f=`${R}/tools/titan/runs/wave49-final/sections/${s}/tests.list`;
 if(!fs.existsSync(f))continue; for(const l of fs.readFileSync(f,'utf8').split('\n')) if(l.trim()) tests.push([s,l.trim()]);}
const LIMIT=+(process.env.LIMIT||tests.length);
let same=0,diff=0,errBoth=0,errOne=0; const changed=[];
for(const [sec,rel] of tests.slice(0,LIMIT)){
  let a,b,ea=null,eb=null;
  try{ a=JSON.stringify((await cur.extractFixture(rel)).fixture ?? await cur.extractFixture(rel)); }catch(e){ ea=String(e.message).slice(0,80); }
  try{ b=JSON.stringify((await old.extractFixture(rel)).fixture ?? await old.extractFixture(rel)); }catch(e){ eb=String(e.message).slice(0,80); }
  if(ea&&eb){errBoth++;continue;}
  if((ea&&!eb)||(!ea&&eb)){ errOne++; changed.push({sec,rel,kind:'THROW-DIFF',ea,eb}); continue; }
  if(a===b){same++;continue;}
  diff++; changed.push({sec,rel,kind:'IR-DIFF',lenHead:b.length,lenTree:a.length});
}
console.log(JSON.stringify({tested:Math.min(LIMIT,tests.length),identical:same,irChanged:diff,throwDiff:errOne,errBoth},null,1));
for(const c of changed) console.log(' •',c.kind,c.sec+'/'+path.basename(c.rel,'.html'), c.kind==='IR-DIFF'?`head=${c.lenHead}B tree=${c.lenTree}B`:`head=${c.eb} tree=${c.ea}`);
