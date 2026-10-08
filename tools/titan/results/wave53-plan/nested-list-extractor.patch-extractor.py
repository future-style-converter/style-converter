import sys,re
p=sys.argv[1]
s=open(p).read()
helper='''
// ── nested-list scope for the li implied close (wave-53 brief) ───────────────
// HTML §13.2.6.4.7 "in body", start tag "li": the implied close walks the
// stack of open elements and STOPS at a special element that is not
// address/div/p — so an <li> opener inside a nested <ol>/<ul>/<menu>/<dir>
// that opened AFTER our <li> closes the INNER item, never ours. Triggers at
// nesting depth > 0 are skipped; everything else is the old first-match.
const IMPLIED_CLOSE_SCOPE = { li: ['ol', 'ul', 'menu', 'dir'] };
export function findImpliedClose(html, from, tagName, triggers) {
  const boundary = IMPLIED_CLOSE_SCOPE[tagName] ?? [];
  const names = [...new Set([...triggers, ...boundary])];
  const re = new RegExp(`<(/?)(${names.join('|')})\\\\b`, 'gi');
  re.lastIndex = from;
  let depth = 0;
  let m;
  while ((m = re.exec(html)) !== null) {
    const tag = m[2].toLowerCase();
    if (boundary.includes(tag)) {
      if (!m[1]) depth++;
      else if (depth > 0) depth--;
      continue;
    }
    if (!m[1] && depth === 0 && triggers.has(tag)) return m.index;
  }
  return -1;
}
'''
anchor='const AUTO_CLOSE_TRIGGERS = {'
assert s.count(anchor)==1
s=s.replace(anchor, helper+'\n'+anchor)
old1='''        const trigRe = new RegExp(
          `<(?:${[...triggers].join('|')})\\\\b`, 'gi',
        );
        trigRe.lastIndex = tagOpenEnd + 1;
        const t = trigRe.exec(html);
        if (t) implicitClose = t.index;'''
assert s.count(old1)==1, s.count(old1)
s=s.replace(old1,'        implicitClose = findImpliedClose(html, tagOpenEnd + 1, tagName, triggers);')
old2='''      const trigRe = new RegExp(`<(?:${[...autoCloseTriggers].join('|')})\\\\b`, 'gi');
      trigRe.lastIndex = tagOpenEnd + 1;
      const t = trigRe.exec(innerHtml);
      if (t) implicitCloseAt = t.index;'''
assert s.count(old2)==1, s.count(old2)
s=s.replace(old2,'      implicitCloseAt = findImpliedClose(innerHtml, tagOpenEnd + 1, tagName, autoCloseTriggers);')
open(p,'w').write(s)
print('patched')
