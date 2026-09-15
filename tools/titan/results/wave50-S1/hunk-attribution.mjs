import fs from 'node:fs';
const txt = fs.readFileSync(process.argv[2],'utf8').split('\n');
let file=null, hunk=null, out=[];
const flush=()=>{ if(hunk) out.push(hunk); hunk=null; };
for(const l of txt){
  if(l.startsWith('diff --git ')){ flush(); file=l.split(' b/')[1]; continue; }
  if(l.startsWith('@@')){ flush(); hunk={file, head:l, add:[], del:0}; continue; }
  if(!hunk) continue;
  if(l.startsWith('+')&&!l.startsWith('+++')) hunk.add.push(l.slice(1));
  else if(l.startsWith('-')&&!l.startsWith('---')) hunk.del++;
}
flush();
const laneRe=/\b(B(?:1[0-2]|[1-9]))\b/g;
for(const h of out){
  const tags=new Set();
  for(const a of h.add){ let m; laneRe.lastIndex=0; while((m=laneRe.exec(a))) tags.add(m[1]); }
  const w50 = h.add.some(a=>/wave[- ]?50/i.test(a));
  console.log(`${h.file}\t${h.head.split('@@')[1].trim()}\t+${h.add.length}/-${h.del}\ttags=[${[...tags].join(',')}]\tw50=${w50}`);
}
