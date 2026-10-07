// Census: corpus tests whose source has an <li> (or <dd>/<dt>) whose FIRST
// following auto-close trigger opener lies INSIDE a nested list container
// opened after it (HTML §13.2.6.4.7 "li" start tag stops at a special
// element) — i.e. tests whose wire the extractor fix would change.
import fs from 'node:fs';
import path from 'node:path';
const ROOT = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf';
const RUN = path.join(ROOT, 'tools/titan/runs/wave52-ship/sections');
const tests = [];
for (const sec of fs.readdirSync(RUN)) {
  const tl = path.join(RUN, sec, 'tests.list');
  if (!fs.existsSync(tl)) continue;
  for (const t of fs.readFileSync(tl, 'utf8').split('\n').filter(Boolean)) tests.push({ sec, t });
}
const strip = (h) => h.replace(/<!--[\s\S]*?-->/g, '')
  .replace(/<script\b[\s\S]*?<\/script\s*>/gi, '')
  .replace(/<style\b[\s\S]*?<\/style\s*>/gi, '');
// Special elements that stop the li/dd/dt scope walk (HTML "special" category minus address/div/p).
const SPECIAL = new Set(['ol','ul','menu','dl','table','blockquote','section','article','aside','nav','header','footer','main','details','fieldset','figure','figcaption','form','center','dir','button','select','template','object','marquee','applet','td','th','caption','li','dd','dt']);
const TRIG = { li: new Set(['li']), dd: new Set(['dd','dt']), dt: new Set(['dd','dt']) };
const out = [];
for (const { sec, t } of tests) {
  const src = path.join(ROOT, 'tools/wpt', t);
  if (!fs.existsSync(src)) continue;
  const h = strip(fs.readFileSync(src, 'utf8'));
  const toks = [];
  const re = /<(\/?)([A-Za-z][A-Za-z0-9-]*)\b[^>]*>/g;
  let m;
  while ((m = re.exec(h))) toks.push({ close: !!m[1], tag: m[2].toLowerCase(), at: m.index, end: re.lastIndex });
  const hits = [];
  for (let k = 0; k < toks.length; k++) {
    const o = toks[k];
    if (o.close || !TRIG[o.tag]) continue;
    // CURRENT rule: first trigger opener after the open tag (whole doc approx).
    let cur = -1;
    for (let j = k + 1; j < toks.length; j++) if (!toks[j].close && TRIG[o.tag].has(toks[j].tag)) { cur = j; break; }
    if (cur < 0) continue;
    // Is that trigger nested inside a container opened after o and still open?
    const stack = [];
    let endedFirst = false;
    for (let j = k + 1; j < cur; j++) {
      const x = toks[j];
      if (!x.close && ['ol','ul','menu','dl','table'].includes(x.tag)) stack.push(x.tag);
      else if (x.close && ['ol','ul','menu','dl','table'].includes(x.tag)) {
        if (stack.length && stack[stack.length - 1] === x.tag) stack.pop();
        else { endedFirst = true; break; }  // closes o's parent: o ended before the trigger
      } else if (x.close && x.tag === o.tag && stack.length === 0) { endedFirst = true; break; }
    }
    if (endedFirst || stack.length === 0) continue;
    const inner = h.slice(o.end, toks[k + 1]?.at ?? o.end);
    // text before the first nested container
    let firstNested = -1;
    for (let j = k + 1; j < cur; j++) if (!toks[j].close && ['ol','ul','menu','dl','table'].includes(toks[j].tag)) { firstNested = j; break; }
    const before = h.slice(o.end, toks[firstNested].at).replace(/<[^>]*>/g, '').trim();
    hits.push({ tag: o.tag, container: toks[firstNested].tag, nestedDepthAtTrigger: stack.length, textBefore: before.slice(0, 40) });
  }
  if (hits.length) out.push({ sec, test: t, hits });
}
console.log(JSON.stringify(out, null, 1));
