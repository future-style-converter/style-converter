// S1 independent census: over the 1435 wave49-final scored tests, which
// documents declare the SAME property at html/:root scope and at body scope
// with DIFFERENT values? Written from scratch; does not import any lane code.
import fs from 'node:fs'; import path from 'node:path';
const ROOT='tools/wpt';
const secs=fs.readdirSync('tools/titan/runs/wave49-final/sections');
const tests=[];
for(const s of secs){
  const f=`tools/titan/runs/wave49-final/sections/${s}/tests.list`;
  if(!fs.existsSync(f))continue;
  for(const l of fs.readFileSync(f,'utf8').split('\n')) if(l.trim()) tests.push([s,l.trim()]);
}
const stripComments=(s)=>s.replace(/\/\*[\s\S]*?\*\//g,'');
// crude but conservative rule splitter over <style> blocks
function rules(css){
  const out=[]; let i=0;
  css=stripComments(css);
  // drop at-rule blocks wholesale except their inner rules for @media (keep inner)
  while(i<css.length){
    const b=css.indexOf('{',i); if(b<0)break;
    let sel=css.slice(i,b).trim();
    // find matching close brace
    let d=1,j=b+1; while(j<css.length&&d>0){ if(css[j]==='{')d++; else if(css[j]==='}')d--; j++; }
    const body=css.slice(b+1,j-1);
    if(sel.startsWith('@')){ // descend into nested (media/supports/layer)
      if(/^@(media|supports|layer|scope|container)/i.test(sel)) out.push(...rules(body));
    } else out.push([sel,body]);
    i=j;
  }
  return out;
}
const decls=(body)=>{
  const o={};
  for(const d of body.split(';')){
    const c=d.indexOf(':'); if(c<0)continue;
    const k=d.slice(0,c).trim().toLowerCase(); let v=d.slice(c+1).trim();
    if(!k||k.startsWith('--')||!v)continue;
    if(/[{}]/.test(k))continue;
    o[k]=v.replace(/\s*!important\s*$/i,'').trim();
  }
  return o;
};
const scopeOf=(sel)=>{
  const parts=sel.split(',').map(s=>s.trim().toLowerCase());
  const scopes=new Set();
  for(const p of parts){
    if(p==='html'||p===':root'||p==='html:root'||p===':root:root') scopes.add('html');
    else if(p==='body') scopes.add('body');
    else if(p==='*') scopes.add('star');
    else scopes.add(null);
  }
  return scopes;
};
const hits=[];
let scanned=0,missing=0;
for(const [sec,rel] of tests){
  const p=path.join(ROOT,rel);
  if(!fs.existsSync(p)){ missing++; continue; }
  scanned++;
  const html=fs.readFileSync(p,'utf8');
  const htmlScope={}, bodyScope={};
  for(const m of html.matchAll(/<style[^>]*>([\s\S]*?)<\/style>/gi)){
    for(const [sel,body] of rules(m[1])){
      const sc=scopeOf(sel);
      const d=decls(body);
      if(sc.has('html')) Object.assign(htmlScope,d);
      if(sc.has('body')) Object.assign(bodyScope,d);
    }
  }
  const conflicts=[];
  for(const k of Object.keys(bodyScope)) if(htmlScope[k]!==undefined && htmlScope[k]!==bodyScope[k]) conflicts.push([k,htmlScope[k],bodyScope[k]]);
  if(conflicts.length) hits.push({sec,rel,conflicts});
}
console.log('tests listed:',tests.length,'scanned:',scanned,'missing source:',missing);
console.log('documents with an html-vs-body SAME-PROPERTY DIFFERENT-VALUE conflict:',hits.length);
for(const h of hits) console.log(' •',h.sec+'/'+path.basename(h.rel,'.html'),JSON.stringify(h.conflicts));
