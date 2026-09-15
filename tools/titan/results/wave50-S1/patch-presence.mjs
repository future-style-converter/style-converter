import fs from 'node:fs';
const patch = process.argv[2];
const lines = fs.readFileSync(patch,'utf8').split('\n');
let file=null, hunks=[], cur=null;
for(const l of lines){
  const m=/^\+\+\+ (?:b\/)?(.+)$/.exec(l);
  if(l.startsWith('diff --git ')){ file=l.split(' b/')[1]; continue; }
  if(m && !l.startsWith('+++ /dev/null')){ file=m[1].replace(/\t.*$/,''); continue; }
  if(l.startsWith('@@')){ if(cur)hunks.push(cur); cur={file,head:l,post:[]}; continue; }
  if(!cur) continue;
  if(l.startsWith('+')) cur.post.push(l.slice(1));
  else if(l.startsWith(' ')) cur.post.push(l.slice(1));
  else if(l.startsWith('-')) {}
  else if(l===''){ /* possible trailing */ }
  else { hunks.push(cur); cur=null; }
}
if(cur)hunks.push(cur);
let allOk=true;
for(const h of hunks){
  const target=h.file;
  if(!fs.existsSync(target)){ console.log(`MISSING FILE ${target} (${h.head.slice(0,40)})`); allOk=false; continue; }
  const body=fs.readFileSync(target,'utf8');
  // strip trailing empty lines of post image
  while(h.post.length && h.post[h.post.length-1]==='') h.post.pop();
  const block=h.post.join('\n');
  const present = body.includes(block);
  if(!present){
    // try to locate how much of the block matches
    let n=h.post.length; let best=0;
    for(let len=h.post.length; len>0; len--){
      if(body.includes(h.post.slice(0,len).join('\n'))){ best=len; break; }
    }
    console.log(`ABSENT  ${target} ${h.head.split('@@')[1].trim()} postLines=${h.post.length} longestPrefixPresent=${best}`);
    allOk=false;
  } else {
    console.log(`present ${target} ${h.head.split('@@')[1].trim()} postLines=${h.post.length}`);
  }
}
console.log(allOk? 'ALL HUNKS PRESENT':'SOME HUNKS ABSENT');
