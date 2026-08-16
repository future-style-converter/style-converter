//
// tools/titan/counter-bake.test.mjs — wave-37 lane W8.
//
// The pins are the css-lists reftests themselves: every expectation below is
// the literal text the matching `-ref.html` prints, quoted in the test name,
// so a change to the walk shows up as a disagreement with WPT and not with
// an opinion of ours.

import test from 'node:test';
import assert from 'node:assert/strict';

import {
  parseCounterReset, parseCounterPairs, findCounterCalls, readCssString, asCssString,
} from './counter-functions.mjs';
import { bakeCounters, COUNTER_BAKED_REASON } from './counter-bake.mjs';

// ── the value grammar ───────────────────────────────────────────────────────

test('parseCounterReset reads plain, valued and reversed entries', () => {
  assert.deepEqual(parseCounterReset('c'), [{ name: 'c', value: null, reversed: false }]);
  assert.deepEqual(parseCounterReset('c 98'), [{ name: 'c', value: 98, reversed: false }]);
  assert.deepEqual(parseCounterReset('reversed(foo)'),
    [{ name: 'foo', value: null, reversed: true }]);
  assert.deepEqual(parseCounterReset('reversed(c1) reversed(c2)'), [
    { name: 'c1', value: null, reversed: true },
    { name: 'c2', value: null, reversed: true },
  ]);
  assert.deepEqual(parseCounterReset('none'), []);
  assert.equal(parseCounterReset('var(--x)'), null);
});

test('parseCounterPairs applies the property default for an omitted integer', () => {
  assert.deepEqual(parseCounterPairs('c', 1), [{ name: 'c', value: 1 }]);
  assert.deepEqual(parseCounterPairs('c', 0), [{ name: 'c', value: 0 }]);
  assert.deepEqual(parseCounterPairs('c1 -1 c2 -2', 1),
    [{ name: 'c1', value: -1 }, { name: 'c2', value: -2 }]);
});

test('findCounterCalls locates counter() and counters() with their arguments', () => {
  assert.deepEqual(findCounterCalls('counter(c, decimal-leading-zero)'),
    [{ start: 0, end: 32, fn: 'counter', name: 'c', sep: null, style: 'decimal-leading-zero' }]);
  const [call] = findCounterCalls('counters(item, ".", lower-roman)');
  assert.equal(call.fn, 'counters');
  assert.equal(call.sep, '.');
  assert.equal(call.style, 'lower-roman');
  // No call at all is not a failure — it is the untouched common case.
  assert.deepEqual(findCounterCalls('"just a string"'), []);
  // A `counters()` without its required separator is a REFUSAL, not a guess.
  assert.equal(findCounterCalls('counters(item)'), null);
});

test('readCssString / asCssString round-trip an escaped quote', () => {
  assert.equal(readCssString('"a\\"b"'), 'a"b');
  assert.equal(asCssString('a"b'), '"a\\"b"');
});

// ── the tree walk ───────────────────────────────────────────────────────────

/** Minimal fixture shape: a component map of `{ properties, children,
 *  _pseudo }` nodes, exactly what buildComponents emits. */
const cmp = (properties = {}, extra = {}) => ({ properties, ...extra });
const before = (content, properties = {}) => ({ before: { properties: { content, ...properties } } });

function contents(components) {
  const out = [];
  const walk = (map) => {
    for (const n of Object.values(map ?? {})) {
      for (const pe of ['marker', 'before', 'after']) {
        const c = n._pseudo?.[pe]?.properties?.content;
        if (c !== undefined) out.push(c);
      }
      walk(n.children);
    }
  };
  walk(components);
  return out;
}

test('counter() resolves a plain increment chain', () => {
  const components = {
    a: cmp({ 'counter-reset': 'c' }, {
      children: {
        i1: cmp({ 'counter-increment': 'c' }, { _pseudo: before('counter(c)') }),
        i2: cmp({ 'counter-increment': 'c' }, { _pseudo: before('counter(c)') }),
      },
    }),
  };
  const r = bakeCounters({ components });
  assert.equal(r.status, 'baked');
  assert.deepEqual(contents(components), ['"1"', '"2"']);
});

test('counter-001: a NESTED reset does not leak to the following siblings', () => {
  // The reference prints …12, 99, 13 — the mid-list span's own counter and
  // then the OUTER one resuming.
  const kid = (extra) => cmp({ 'counter-increment': 'c', ...extra },
    { _pseudo: before('counter(c)') });
  const components = {
    test: cmp({ 'counter-reset': 'c' }, {
      children: { a: kid({}), b: kid({}), c: kid({ 'counter-reset': 'c 98' }), d: kid({}) },
    }),
  };
  bakeCounters({ components });
  assert.deepEqual(contents(components), ['"1"', '"2"', '"99"', '"3"']);
});

test('counter-reset-reversed-siblings-001a: reversed reaches the following sibling (4, 2)', () => {
  const components = {
    a: cmp({ 'counter-reset': 'reversed(foo)' }, {
      children: { inner: cmp({ 'counter-increment': 'foo -1' }, { _pseudo: before('counter(foo)') }) },
    }),
    b: cmp({ 'counter-increment': 'foo -2' }, { _pseudo: before('counter(foo)') }),
  };
  bakeCounters({ components });
  assert.deepEqual(contents(components), ['"4"', '"2"']);
});

test('counter-reset-reversed-siblings-003: a NESTED reversed reset still leaks (4, 2)', () => {
  const components = {
    outer: cmp({ 'counter-reset': 'foo 10' }, {
      children: {
        b: cmp({ 'counter-reset': 'reversed(foo)' }, {
          children: { c: cmp({ 'counter-increment': 'foo -1' }, { _pseudo: before('counter(foo)') }) },
        }),
        d: cmp({ 'counter-increment': 'foo -2' }, { _pseudo: before('counter(foo)') }),
      },
    }),
  };
  bakeCounters({ components });
  assert.deepEqual(contents(components), ['"4"', '"2"']);
});

test('counter-reset-reversed-display-none: a display:none item does not count (3, 2, 1)', () => {
  const item = (extra = {}) => cmp({ 'counter-increment': 'foo -1', ...extra },
    { _pseudo: before('counter(foo)') });
  const components = {
    a: cmp({ 'counter-reset': 'reversed(foo)' }, {
      children: {
        i1: item(),
        i2: item({ visibility: 'hidden' }),
        i3: item({ display: 'none' }),
        i4: item(),
      },
    }),
  };
  bakeCounters({ components });
  // The display:none bag is left untouched — the item paints nothing at all.
  assert.deepEqual(contents(components), ['"3"', '"2"', 'counter(foo)', '"1"']);
});

test('counter-reset-reversed-pseudo-001: pseudo increments count in document order (B7A5/B4A2)', () => {
  const item = () => cmp({}, {
    _pseudo: {
      before: { properties: { content: '"B" counter(foo)', 'counter-increment': 'foo -1' } },
      after: { properties: { content: '"A" counter(foo)', 'counter-increment': 'foo -2' } },
    },
  });
  const components = {
    list: cmp({ 'counter-reset': 'reversed(foo)' }, { children: { a: item(), b: item() } }),
  };
  bakeCounters({ components });
  assert.deepEqual(contents(components), ['"B" "7"', '"A" "5"', '"B" "4"', '"A" "2"']);
});

test('counters() joins the whole nesting chain with its separator', () => {
  const components = {
    l1: cmp({ 'counter-reset': 'c', 'counter-increment': 'c' }, {
      _pseudo: before('counters(c, ".")'),
      children: {
        l2: cmp({ 'counter-reset': 'c', 'counter-increment': 'c' }, {
          _pseudo: before('counters(c, ".")'),
        }),
      },
    }),
  };
  bakeCounters({ components });
  assert.deepEqual(contents(components), ['"1"', '"1.1"']);
});

test('an <ol start> seeds the implicit list-item counter', () => {
  const li = () => cmp({}, { _tag: 'li', _pseudo: before('counter(list-item)') });
  const components = {
    ol: cmp({}, { _tag: 'ol', _attrs: { start: '30' }, children: { a: li(), b: li() } }),
  };
  bakeCounters({ components });
  assert.deepEqual(contents(components), ['"30"', '"31"']);
});

// ── wave-44 lane H2: `<ol reversed>` ────────────────────────────────────────
//
// The wire half landed in lane U5 (`reversed` joined LIST_ATTR_KEYS as a
// presence-boolean); these pin the counter half. Every expectation is the
// literal run the matching `-ref.html` prints.

test('counter-list-item: <ol reversed> counts DOWN to 1 (3, 2, 1)', () => {
  const li = () => cmp({}, { _tag: 'li', _pseudo: before('counter(list-item)') });
  const components = {
    ol: cmp({}, {
      _tag: 'ol', _attrs: { reversed: true },
      children: { a: li(), b: li(), c: li() },
    }),
  };
  bakeCounters({ components });
  assert.deepEqual(contents(components), ['"3"', '"2"', '"1"']);
});

test('counter-list-item: <ol reversed start=30> starts AT 30 and counts down', () => {
  // li-value-reversed-011 (`<ol reversed start=3>` ⇒ 3,2,1) is the same rule:
  // the seed is start+1 so the first item lands on start after its −1 step —
  // the mirror of the forward seed's start−1.
  const li = () => cmp({}, { _tag: 'li', _pseudo: before('counter(list-item)') });
  const components = {
    ol: cmp({}, {
      _tag: 'ol', _attrs: { start: '30', reversed: true },
      children: { a: li(), b: li(), c: li() },
    }),
  };
  bakeCounters({ components });
  assert.deepEqual(contents(components), ['"30"', '"29"', '"28"']);
});

test('counter-list-item: <li value> ANCHORS the reversed run (32,31,30,29,35,34)', () => {
  // The test's third reversed list, and the whole reason the §4.4.2 walk
  // replaced `Σ|inc| + |last inc|`: the walk stops at the first counter-set
  // (here the `<li value=30>` presentational hint) and ADDS ITS VALUE, so
  // the implied initial is 1+1+30 = 32 (+1 pre-step) — not the item count.
  const li = (value) => cmp({}, {
    _tag: 'li', _pseudo: before('counter(list-item)'),
    ...(value === undefined ? {} : { _attrs: { value } }),
  });
  const components = {
    ol: cmp({}, {
      _tag: 'ol', _attrs: { reversed: true },
      children: { a: li(), b: li(), c: li('30'), d: li(), e: li('35'), f: li() },
    }),
  };
  bakeCounters({ components });
  assert.deepEqual(contents(components),
    ['"32"', '"31"', '"30"', '"29"', '"35"', '"34"']);
});

test('li-value-reversed-006a: POSITIVE increments give a NEGATIVE initial (-6,-4,-2)', () => {
  // The reference is `<ol reversed start="-9">` printing -6, -4, -2, and its
  // own <meta assert> states the rule: "The last counter-increment value
  // determines the start value." num = (-2-2-2) + (-2) = -8 ⇒ first item -6.
  // The pre-wave-44 `Σ|inc| + |last inc|` said +8 and painted 10, 12, 14.
  const li = () => cmp({ 'counter-increment': 'list-item 2' },
    { _tag: 'li', _pseudo: before('counter(list-item)') });
  const components = {
    ol: cmp({}, { _tag: 'ol', _attrs: { reversed: true },
      children: { a: li(), b: li(), c: li() } }),
  };
  bakeCounters({ components });
  assert.deepEqual(contents(components), ['"-6"', '"-4"', '"-2"']);
});

test('li-value-reversed-013: a <li value> with its OWN increment still prints its value (5,3,2)', () => {
  // `<li value=3 style="counter-increment: list-item -2">` — the value is a
  // counter-set applied AFTER the increment, so the item prints 3 outright;
  // the pre-wave-44 `value − 1` pre-offset let the declared −2 drag it to 0.
  const li = (extra = {}, props = {}) => cmp(props,
    { _tag: 'li', _pseudo: before('counter(list-item)'), ...extra });
  const components = {
    ol: cmp({}, { _tag: 'ol', _attrs: { reversed: true }, children: {
      a: li(),
      b: li({ _attrs: { value: '3' } }, { 'counter-increment': 'list-item -2' }),
      c: li(),
    } }),
  };
  bakeCounters({ components });
  assert.deepEqual(contents(components), ['"5"', '"3"', '"2"']);
});

test('li-value-reversed-008: counter-set is applied AFTER counter-increment', () => {
  // `<ol start=11>` with `<li style="counter-set: list-item 8">` prints 8,
  // then 9 (MEASURED in the pinned headless Chromium, and what the -008
  // reference shows). The old order subtracted the item's own +1 first and
  // printed 7.
  const li = (props = {}) => cmp(props, { _tag: 'li', _pseudo: before('counter(list-item)') });
  const components = {
    ol: cmp({}, { _tag: 'ol', _attrs: { start: '11' }, children: {
      a: li({ 'counter-set': 'list-item 8' }),
      b: li(),
    } }),
  };
  bakeCounters({ components });
  assert.deepEqual(contents(components), ['"8"', '"9"']);
});

test('an <ol> with no reversed attribute is untouched by the reversed lane', () => {
  // The blast-radius guard: presence is the whole test, so an <ol> whose bag
  // carries no `reversed` key numbers forward exactly as before.
  const li = () => cmp({}, { _tag: 'li', _pseudo: before('counter(list-item)') });
  const components = {
    ol: cmp({}, { _tag: 'ol', _attrs: { start: '30' }, children: { a: li(), b: li() } }),
  };
  bakeCounters({ components });
  assert.deepEqual(contents(components), ['"30"', '"31"']);
});

test('a counter style outside the §6 table is REFUSED, not defaulted to decimal', () => {
  const components = {
    a: cmp({ 'counter-reset': 'c', 'counter-increment': 'c' },
      { _pseudo: before('counter(c, my-author-style)') }),
  };
  const r = bakeCounters({ components });
  assert.equal(r.status, 'refused');
  assert.deepEqual(contents(components), ['counter(c, my-author-style)']);
});

test('a refusal LATE in the document leaves the EARLIER bags byte-identical', () => {
  const components = {
    a: cmp({ 'counter-reset': 'c', 'counter-increment': 'c' }, { _pseudo: before('counter(c)') }),
    b: cmp({ 'counter-increment': 'c' }, { _pseudo: before('counter(c, my-author-style)') }),
  };
  assert.equal(bakeCounters({ components }).status, 'refused');
  assert.deepEqual(contents(components), ['counter(c)', 'counter(c, my-author-style)']);
});

test('a fixture with no counter substring is skipped whole', () => {
  const components = { a: cmp({ color: 'red' }, { _pseudo: before('"x"') }) };
  const r = bakeCounters({ components });
  assert.equal(r.status, 'skipped');
  assert.deepEqual(contents(components), ['"x"']);
});

test('a document with a <slot> BAILS — the flattened tree is not this tree', () => {
  // counter-list-item-slot-order.html: three `<li slot=…>` in source order
  // 3, 2, 1 that the flattened tree renders as 1, 2, 2.1.
  const components = {
    a: cmp({}, { _tag: 'li', _pseudo: before("counters(list-item,'.') ' '") }),
  };
  const r = bakeCounters({ components }, '<div id=host><li slot="list3">x</li></div>');
  assert.equal(r.status, 'bailed');
  assert.deepEqual(contents(components), ["counters(list-item,'.') ' '"]);
});

test('the lossy stamp rides the fixture record only when something resolved', () => {
  const fixture = {
    _wpt: { lossy: false, lossyReasons: [] },
    components: {
      a: cmp({ 'counter-reset': 'c', 'counter-increment': 'c' }, { _pseudo: before('counter(c)') }),
    },
  };
  bakeCounters(fixture);
  assert.equal(fixture._wpt.lossy, true);
  assert.ok(fixture._wpt.lossyReasons.includes(COUNTER_BAKED_REASON));
});

test('a counter STYLE name is case-sensitive: `Decimal` is not `decimal`', () => {
  // counter-style-at-rule/name-case-sensitivity.html asserts exactly this.
  const mk = (style) => ({
    a: cmp({ 'counter-reset': 'c', 'counter-increment': 'c' },
      { _pseudo: before(`counter(c, ${style})`) }),
  });
  const lower = mk('decimal');
  assert.equal(bakeCounters({ components: lower }).status, 'baked');
  assert.deepEqual(contents(lower), ['"1"']);
  const upper = mk('Decimal');
  assert.equal(bakeCounters({ components: upper }).status, 'refused');
  assert.deepEqual(contents(upper), ['counter(c, Decimal)']);
});
