#!/usr/bin/env python3
# tools/titan/results/wave53-lists-bakes/make-u2-narrow.py — writes the P-narrow fallback (PLAN §2 L1 (P),
# expectations.json lanes.L1-lists-bakes.pNarrowFallback) into COPIES of tools/titan/bidi-bake.mjs and
# tools/titan/bidi-bake.test.mjs under <dir>/tools/titan/; the caller diffs them against the U2 tree to cut
# u2-narrow.patch. One condition in planBidiBake (hunk P fires only on a root that hosts a hunk-M marker run)
# and the pin V3c, which replaces V3's bidi-lines-002 case (under P-narrow that root keeps its padding).
import sys
d = sys.argv[1]
p = f'{d}/tools/titan/bidi-bake.mjs'
s = open(p, encoding='utf-8').read()
old = '''      // Hunk P: the walk record carries the resolved padding paddingIsSpent reads.
      plan.roots.push({ path: e.path, props: rootProperties(e.rect, e.position, e) });'''
assert s.count(old) == 1
new = '''      // Hunk P: the walk record carries the resolved padding paddingIsSpent reads.
      // U2-NARROW (PLAN §2 L1 (P), the pre-registered fallback): only a root
      // that HOSTS a hunk-M marker run has its padding treated as spent, so
      // the marker-less bidi-lines-001/-002 and anchor-center-safe-rtl roots
      // keep their padding and their per-test IR byte-identical.
      const hostsMarker = [...markers].some(([k, m]) => m.runs?.length
        && isDescendantPath(e.path, k.split('.').map(Number)));
      plan.roots.push({ path: e.path, props: rootProperties(e.rect, e.position, hostsMarker ? e : null) });'''
s = s.replace(old, new)
open(p, 'w', encoding='utf-8').write(s)
p = f'{d}/tools/titan/bidi-bake.test.mjs'
s = open(p, encoding='utf-8').read()
a = s.index("test('V3 hunk P: bidi-lines-002\\'s root (0 0.5ch) with its 5 hidden <br>', () => {")
b = s.index("test('V3b hunk P: a ZERO-padding root")
body = s[a:b]
body = body.replace("test('V3 hunk P: bidi-lines-002\\'s root (0 0.5ch) with its 5 hidden <br>', () => {",
  "test('V3c U2-narrow: a marker-LESS root (bidi-lines-002, 0 0.5ch) keeps its padding; counter-suffix still zeroes', () => {")
old_tail = '''  const out = fx.components['bidi__bidi-lines-002__1'];
  assert.equal(out.properties.padding, '0');
  assert.deepEqual(Object.keys(out.properties).filter((k) => k.startsWith('padding')), ['padding']);'''
assert body.count(old_tail) == 1
body = body.replace(old_tail, '''  const out = fx.components['bidi__bidi-lines-002__1'];
  // P-narrow: no hunk-M marker run under this root → padding untouched, keys in order.
  assert.equal('padding' in plan.roots[0].props, false);
  assert.equal(out.properties.padding, '0 0.5ch');
  assert.deepEqual(Object.keys(out.properties), ['direction', 'unicode-bidi', 'text-align', 'font-size',
    'width', 'border', 'padding', 'height', 'box-sizing', 'position']);
  // …while counter-suffix's roots, which host marker runs, still get hunk P.
  const cs = planBidiBake(counterSuffixWalk(CS_MARKERS())).plan;
  for (const r of cs.roots) assert.equal(r.props.padding, '0');
  // MUTATION (executed, mutations.log): dropping the marker-host condition → red.''')
s = s[:a] + body + s[b:]
open(p, 'w', encoding='utf-8').write(s)
print('narrow copies written')
