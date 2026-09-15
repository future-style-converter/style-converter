// Corpus simulation of the proposed Compose line-box census vs today's
// uniform cap, over every wave49-final per-test IR that carries a
// fixed-count line-clamp. Mirrors the Kotlin the lane ships.
import fs from "fs"; import path from "path";
const ROOT="/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf/tools/titan/runs/wave49-final/sections";
const RATIO=1.25;

// --- ValueExtractors.extractDp (Compose) ---------------------------------
function extractDp(j){
  if(j==null||typeof j!=="object"||Array.isArray(j)) return null;
  if(typeof j.px==="number") return j.px;
  const o=j.original; if(o==null||typeof o!=="object") return null;
  if(typeof o.px==="number") return o.px;
  if(o.original&&typeof o.original.px==="number") return o.original.px;
  if(typeof o.v==="number"){
    const u=(o.u||"").toUpperCase();
    if(u==="EM"||u==="REM") return o.v*16;
    return null;
  }
  return null;
}
function extractFloat(j){ return typeof j==="number"?j:null; }
const last=(ps,t)=>{let r=null;for(const p of ps)if(p.type===t)r=p;return r;};
const first=(ps,t)=>ps.find(p=>p.type===t)||null;
function keyword(d){ return typeof d==="string"?d.toLowerCase():(d&&typeof d.keyword==="string"?d.keyword.toLowerCase():null); }

function monoUA(ps){
  if(ps.some(p=>p.type==="FontSize")) return null;
  const fam=last(ps,"FontFamily"); if(!fam) return null;
  const names=Array.isArray(fam.data)?fam.data:(fam.data&&Array.isArray(fam.data.families)?fam.data.families:null);
  if(!names||!names.length) return null;
  const f=String(names[0]).trim().replace(/^['"]|['"]$/g,"").toLowerCase();
  return (f==="monospace"||f==="ui-monospace")?13:null;
}
// TypographyExtractor.fontSize (with the wave-36 monospace UA fold)
function rootFontPx(ps){
  const fs_=last(ps,"FontSize"); const v=fs_?extractDp(fs_.data):null;
  if(v!=null) return v;
  const ua=monoUA(ps); if(ua!=null) return ua;
  return null; // capPx then bottoms out at 16
}
function rootLineBox(ps){
  const fp=rootFontPx(ps) ?? 16;
  const lh=last(ps,"LineHeight");
  if(lh){
    const pre=(()=>{const f=first(ps,"FontSize");return f?extractDp(f.data):null;})();
    if(lh.data&&typeof lh.data==="object"&&typeof lh.data.multiplier==="number") return lh.data.multiplier*(pre??16);
    const fl=extractFloat(lh.data); if(fl!=null) return fl*(pre??16);
    const dp=extractDp(lh.data); if(dp!=null) return dp;
  }
  return fp*RATIO;
}
function bands(ps){ // px-only vertical bands: padding + border width + margin
  const one=t=>{const p=last(ps,t); if(!p) return 0; const v=extractDp(p.data); return v==null?null:Math.max(0,v);};
  const vals=["PaddingTop","BorderTopWidth","MarginTop"].map(one);
  const vals2=["PaddingBottom","BorderBottomWidth","MarginBottom"].map(one);
  if(vals.includes(null)||vals2.includes(null)) return null;
  return {top:vals.reduce((a,b)=>a+b,0), bottom:vals2.reduce((a,b)=>a+b,0)};
}
function exactLineCount(text, ws){
  if(text==null||text==="") return 0;
  const k=(ws||"normal").toLowerCase().replace(/_/g,"-");
  const hard=[...text].reduce((a,c)=>c==="\n"?a+1:a,1);
  const hasSpace=text.includes(" ")||text.includes("\t");
  switch(k){
    case "pre": return hard;
    case "nowrap": return 1;
    case "pre-wrap": case "pre-line": case "break-spaces": return hasSpace?null:hard;
    default: return (hasSpace||text.includes("\n"))?null:1;
  }
}
// --- today's Compose cap -------------------------------------------------
function uniformCap(ps,lines){
  const lb=rootLineBox(ps); if(lb<=0) return null;
  const pt=(()=>{const p=last(ps,"PaddingTop");return p?Math.max(0,extractDp(p.data)??0):0;})();
  const pb=(()=>{const p=last(ps,"PaddingBottom");return p?Math.max(0,extractDp(p.data)??0):0;})();
  const bt=borderBand(ps,"Top"), bb=borderBand(ps,"Bottom");
  return lines*lb+pt+pb+bt+bb;
}
function borderBand(ps,side){
  const st=last(ps,`Border${side}Style`); const w=last(ps,`Border${side}Width`);
  const style=st?keyword(st.data):null;
  if(style==="none"||style==="hidden") return 0;
  const wv=w?extractDp(w.data):null;
  if(wv==null) return 0;
  if(!st && wv>0) return wv; // width without style: Compose's borderBandInsets gate is style-driven; approximate
  return style?wv:0;
}
// --- the proposed census -------------------------------------------------
function childRun(kid, kids, root){
  const ps=kid.properties||[];
  const tag=(kid.meta&&kid.meta.sourceTag||"").toLowerCase();
  const disp=last(ps,"Display"); if(disp&&keyword(disp.data)==="none") return {lineBox:root.lineBox,lines:0,band:0,mono:0,kind:"none"};
  if(tag==="br"||kid.meta?.role==="line-break"||kid.role==="line-break") return {lineBox:root.lineBox,lines:0,band:0,mono:0,kind:"br"};
  // typography
  const fsProp=last(ps,"FontSize");
  let fontPx;
  if(fsProp){ const v=extractDp(fsProp.data); fontPx = v!=null?v:null; }
  else { const ua=monoUA(ps); fontPx = ua!=null?ua:root.fontPx; }
  let lineBox=null;
  const lhProp=last(ps,"LineHeight");
  if(lhProp){
    const dp=extractDp(lhProp.data);
    if(dp!=null&&dp>0) lineBox=dp;
    else if(fontPx!=null&&lhProp.data&&typeof lhProp.data.multiplier==="number"&&lhProp.data.multiplier>0) lineBox=fontPx*lhProp.data.multiplier;
    else lineBox=null;
  } else if(fontPx!=null){
    if(root.declaredMultiplier!=null) lineBox=fontPx*root.declaredMultiplier;
    else if(root.declaredLineHeightPx!=null) lineBox=root.declaredLineHeightPx;
    else lineBox=fontPx*RATIO;
  }
  const b=bands(ps);
  const wsP=last(ps,"WhiteSpace"); const ws=wsP?keyword(wsP.data):root.ws;
  const box=lineBox??root.lineBox;
  const hasNested=(kids.some(c=>c.slot?.parent===kid.id))||!!(kid.meta&&kid.meta.runs);
  const lines=(!hasNested&&lineBox!=null)?exactLineCount(kid.text,ws):null;
  const h=last(ps,"Height"); const hpx=h?extractDp(h.data):null;
  if(hpx!=null) return {lineBox:box,lines:0,band:b?b.top:null,mono:b?hpx+b.top+b.bottom:null,kind:"height"};
  const ovAxes=["Overflow","OverflowX","OverflowY","OverflowBlock","OverflowInline"];
  const isScroll=ps.some(p=>ovAxes.includes(p.type)&&(keyword(p.data)||"visible")!=="visible");
  if(isScroll){
    if(lines==null||b==null) return {lineBox:box,lines:null,band:null,mono:null,kind:"scroll-unknown"};
    return {lineBox:box,lines:0,band:b.top,mono:lines*box+b.top+b.bottom,kind:"scroll"};
  }
  return {lineBox:box,lines,band:b?b.top:null,mono:null,kind:"block"};
}
function censusRuns(root, comp, comps){
  const kids=comps.filter(c=>c.slot?.parent===comp.id);
  const rootRun=t=>({lineBox:root.lineBox,lines:exactLineCount(t,root.ws),band:0,mono:null,kind:"text"});
  const runsMeta=comp.meta?.runs;
  if(!runsMeta||!runsMeta.length){
    const out=[]; if(comp.text) out.push(rootRun(comp.text));
    for(const k of kids) out.push(childRun(k,comps,root));
    return out;
  }
  const index={};
  kids.forEach((c,i)=>{ if(c.name&&index[c.name]==null) index[c.name]=i; });
  kids.forEach((c,i)=>{ if(c.id&&index[c.id]==null) index[c.id]=i; });
  const before={}; let pending=[]; let lastRef=null;
  for(const r of runsMeta){
    if(r.text!=null){ if(r.text!=="") pending.push(r.text); continue; }
    const i=index[r.child]; if(i==null||before[i]!=null||(lastRef!=null&&!(i>lastRef))) continue;
    before[i]=pending; pending=[]; lastRef=i;
  }
  const out=[];
  kids.forEach((c,i)=>{
    for(const t of (before[i]||[])) out.push(rootRun(t));
    out.push(childRun(c,comps,root));
    if(i===lastRef) for(const t of pending) out.push(rootRun(t));
  });
  if(lastRef==null) return pending.map(rootRun).concat(out);
  return out;
}
function verdict(lines, runs){
  let rem=lines, h=0;
  for(const r of runs){
    if(r.mono!=null){ h+=r.mono; continue; }
    if(r.lines==null||r.band==null) return {kind:"fallback"};
    if(r.lines===0) continue;
    h+=r.band;
    const taken=Math.min(r.lines,rem);
    h+=taken*r.lineBox; rem-=taken;
    if(rem===0) return {kind:"capped",h};
  }
  return {kind:"short"};
}
// --- walk ---------------------------------------------------------------
const secs=fs.readdirSync(ROOT).filter(d=>fs.existsSync(path.join(ROOT,d,"per-test-ir")));
let changed=0, same=0;
for(const sec of secs){
  const dir=path.join(ROOT,sec,"per-test-ir");
  for(const f of fs.readdirSync(dir).sort()){
    if(!f.endsWith(".json")) continue;
    const doc=JSON.parse(fs.readFileSync(path.join(dir,f),"utf8"));
    const comps=doc.components||[];
    for(const comp of comps){
      const lc=last(comp.properties||[],"LineClamp");
      if(!lc||lc.data?.type!=="lines"||!(lc.data.count>=1)) continue;
      const ps=comp.properties||[];
      const lhP=last(ps,"LineHeight");
      const root={
        fontPx: rootFontPx(ps) ?? 16,
        lineBox: rootLineBox(ps),
        ws: (()=>{const p=last(ps,"WhiteSpace");return p?keyword(p.data):null;})(),
        declaredLineHeightPx: lhP?extractDp(lhP.data):null,
        declaredMultiplier: (lhP&&lhP.data&&typeof lhP.data.multiplier==="number")?lhP.data.multiplier:null,
      };
      const n=Math.trunc(lc.data.count);
      const today=uniformCap(ps,n);
      const runs=censusRuns(root,comp,comps);
      const v=verdict(n,runs);
      const bandsRoot=(()=>{const pt=last(ps,"PaddingTop"),pb=last(ps,"PaddingBottom");
        return (pt?Math.max(0,extractDp(pt.data)??0):0)+(pb?Math.max(0,extractDp(pb.data)??0):0)+borderBand(ps,"Top")+borderBand(ps,"Bottom");})();
      const proposed = v.kind==="capped" ? v.h+bandsRoot : today;
      const tag=(Math.abs(proposed-today)>0.01)?"CHANGED":"same";
      if(tag==="CHANGED"){changed++;} else same++;
      if(tag==="CHANGED"||process.env.ALL)
        console.log(`${tag.padEnd(8)} ${sec}/${f.replace(/^wpt__/,"")} root=${comp.name} n=${n} rootBox=${root.lineBox} today=${today} proposed=${proposed} verdict=${v.kind} runs=[${runs.map(r=>`${r.kind}:lb${r.lineBox}/l${r.lines}/b${r.band}/m${r.mono}`).join(" ")}]`);
    }
  }
}
console.log(`\n=== ${changed} clamp roots change cap, ${same} unchanged ===`);
