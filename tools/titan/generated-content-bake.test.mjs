//
// tools/titan/generated-content-bake.test.mjs — wave-36 lane M3.
//
// Pins for THE GENERATED-CONTENT TEXT BAKE. The contract under test is
// stated in generated-content-bake.mjs's header; these tests pin the three
// halves that can silently rot:
//   1. the REFUSAL set (a partial string would be worse than no string),
//   2. the css-content-3 §2.1 quote-depth algebra + its css-contain-1 §3.3
//      style-containment scoping,
//   3. the "byte-identical when refused / omit-when-empty" rule that keeps
//      every pre-lane fixture unchanged.

import { test } from 'node:test';
import assert from 'node:assert/strict';

import {
  unescapeCssString,
  parseContentComponents,
  parseQuotesValue,
  applyQuoteKeyword,
  declaresStyleContainment,
  bakeGeneratedContentText,
  GENERATED_CONTENT_BAKED_REASON,
} from './generated-content-bake.mjs';

// ── CSS <string> unescaping (CSS Syntax 3 §4.3.7) ───────────────────────────

test('M3 bake: hex escapes decode to code points, with the optional trailing space eaten', () => {
  // The corpus writes quote marks BOTH ways — `"\201C"` must not ship as
  // five literal characters.
  assert.equal(unescapeCssString('\\201C'), '“');
  assert.equal(unescapeCssString('\\201C x'), '“x');   // one space is the terminator
  assert.equal(unescapeCssString('\\201C  x'), '“ x'); // …only ONE
  assert.equal(unescapeCssString('\\A'), '\n');             // the newline escape
  assert.equal(unescapeCssString('a\\"b'), 'a"b');          // escaped delimiter
  assert.equal(unescapeCssString('plain'), 'plain');
  // §4.3.7: NUL, surrogates and out-of-range values become U+FFFD.
  assert.equal(unescapeCssString('\\0'), '�');
  assert.equal(unescapeCssString('\\D800'), '�');
});

// ── the refusal set ─────────────────────────────────────────────────────────

test('M3 bake: a pure <string> sequence concatenates', () => {
  assert.deepEqual(
    parseContentComponents('"a" "b"'),
    [{ kind: 'string', value: 'a' }, { kind: 'string', value: 'b' }],
  );
  // Single quotes are equally legal CSS strings.
  assert.deepEqual(parseContentComponents("'x'"), [{ kind: 'string', value: 'x' }]);
});

test('M3 bake: every unresolvable function refuses the WHOLE value', () => {
  // Each of these is a documented refusal in the module header. A partial
  // bake ("the strings I understood") would paint text the browser does not.
  for (const v of [
    'counter(c)', 'counters(c, ".")', '"pre" counter(c)',
    'url(a.png)', 'image-set("a.png" 1x)', 'linear-gradient(red, blue)',
    'attr(data-x)', 'var(--v)', 'target-counter(url(#a), page)',
    'leader(dotted)', 'contents', 'normal', 'none', 'element(#a)',
  ]) {
    assert.equal(parseContentComponents(v), null, v);
  }
  // An unterminated string is a parse error, not a guess about where it ends.
  assert.equal(parseContentComponents('"oops'), null);
});

test('M3 bake: the /alt-text tail is discarded, not refused', () => {
  // css-content-3 §2.3 alt text is never painted, so a value carrying one
  // still has a fully resolvable painted half.
  assert.deepEqual(
    parseContentComponents('"x" / "spoken"'),
    [{ kind: 'string', value: 'x' }],
  );
});

// ── the quote algebra (css-content-3 §2.1) ──────────────────────────────────

test('M3 bake: quotes values parse into auto / none / pair list', () => {
  assert.deepEqual(parseQuotesValue('auto'), { kind: 'auto', pairs: [] });
  assert.deepEqual(parseQuotesValue('none'), { kind: 'none', pairs: [] });
  assert.deepEqual(parseQuotesValue('"A" "Z" "1" "9"'), {
    kind: 'pairs',
    pairs: [{ open: 'A', close: 'Z' }, { open: '1', close: '9' }],
  });
  // An ODD number of strings is invalid per the grammar — refuse, not pad.
  assert.equal(parseQuotesValue('"A" "Z" "1"'), null);
  // A CSS-wide keyword / var() is not a pair list we can read.
  assert.equal(parseQuotesValue('inherit'), null);
});

test('M3 bake: open/close-quote walk the depth and clamp to the last pair', () => {
  const q = parseQuotesValue('"A" "Z" "1" "9"');
  const s = { depth: 0 };
  assert.equal(applyQuoteKeyword('open-quote', s, q), 'A');   // depth 0 → pair 0
  assert.equal(s.depth, 1);
  assert.equal(applyQuoteKeyword('open-quote', s, q), '1');   // depth 1 → pair 1
  assert.equal(applyQuoteKeyword('open-quote', s, q), '1');   // depth 2 → CLAMPED
  assert.equal(s.depth, 3);
  assert.equal(applyQuoteKeyword('close-quote', s, q), '9');  // → depth 2, pair 1
  assert.equal(applyQuoteKeyword('close-quote', s, q), '9');  // → depth 1, pair 1
  assert.equal(applyQuoteKeyword('close-quote', s, q), 'Z');  // → depth 0, pair 0
  assert.equal(s.depth, 0);
  // A close-quote at depth 0 paints nothing and never goes negative.
  assert.equal(applyQuoteKeyword('close-quote', s, q), '');
  assert.equal(s.depth, 0);
});

test('M3 bake: the no- forms move the depth and paint nothing', () => {
  const q = parseQuotesValue('"A" "Z"');
  const s = { depth: 0 };
  assert.equal(applyQuoteKeyword('no-open-quote', s, q), '');
  assert.equal(s.depth, 1);
  assert.equal(applyQuoteKeyword('no-close-quote', s, q), '');
  assert.equal(s.depth, 0);
});

test('M3 bake: quotes:none and quotes:auto paint no mark', () => {
  // `none` is the spec answer; `auto` is an HONEST decline — resolving it
  // needs the CLDR table keyed by content language, which this wire does
  // not carry (the lane's named deferral).
  for (const v of ['none', 'auto']) {
    const s = { depth: 0 };
    assert.equal(applyQuoteKeyword('open-quote', s, parseQuotesValue(v)), '');
    assert.equal(s.depth, 1, 'the depth still moves');
  }
});

// ── the tree walk ───────────────────────────────────────────────────────────

test('M3 bake: a string content bag gains _text and the lossy marker', () => {
  const components = {
    a: { properties: {}, _pseudo: { before: { properties: { content: '"hi"' } } } },
  };
  const r = bakeGeneratedContentText(components);
  assert.deepEqual(r, { baked: 1, refused: 0 });
  assert.equal(components.a._pseudo.before._text, 'hi');
  assert.ok(components.a._pseudo.before._lossyReasons.includes(GENERATED_CONTENT_BAKED_REASON));
});

test('M3 bake: an empty or refused bag stays BYTE-IDENTICAL', () => {
  // The whole back-compat contract: a fixture whose pseudo content this lane
  // cannot (or need not) resolve must serialize exactly as it did before.
  const components = {
    empty: { properties: {}, _pseudo: { before: { properties: { content: '""' } } } },
    counter: { properties: {}, _pseudo: { before: { properties: { content: 'counter(c)' } } } },
    image: { properties: {}, _pseudo: { after: { properties: { content: 'url(a.png)' } } } },
  };
  const before = JSON.stringify(components);
  const r = bakeGeneratedContentText(components);
  assert.equal(r.baked, 0);
  assert.equal(JSON.stringify(components), before);
});

test('M3 bake: the quote counter threads ::before → children → ::after', () => {
  // css/css-contain/quote-scoping-001's shape WITHOUT the containment: the
  // span's open-quote leaks its depth, so the div's ::after closes at
  // depth 1 and paints the SECOND pair's close mark.
  const components = {
    div: {
      properties: { quotes: '"A" "Z" "1" "9"' },
      _pseudo: {
        before: { properties: { content: 'open-quote' } },
        after: { properties: { content: 'close-quote' } },
      },
      children: {
        span: { properties: {}, _pseudo: { before: { properties: { content: 'open-quote' } } } },
      },
    },
  };
  bakeGeneratedContentText(components);
  assert.equal(components.div._pseudo.before._text, 'A');   // depth 0
  assert.equal(components.div.children.span._pseudo.before._text, '1'); // depth 1
  assert.equal(components.div._pseudo.after._text, '9');    // depth 2 → 1
});

test('M3 bake: style containment SCOPES the quote depth (inherit in, roll back out)', () => {
  // css/css-contain/quote-scoping-001 exactly — the test the reading was
  // derived from. Containment does NOT reset to 0 (that would paint 'A'
  // inside the span); it rolls the subtree's changes back on the way out,
  // so div::after closes at depth 0 and the document reads "A1Z".
  assert.equal(declaresStyleContainment({ contain: 'style' }), true);
  assert.equal(declaresStyleContainment({ contain: 'strict' }), true);
  assert.equal(declaresStyleContainment({ contain: 'layout paint' }), false);
  const components = {
    div: {
      properties: { quotes: '"A" "Z" "1" "9"' },
      _pseudo: {
        before: { properties: { content: 'open-quote' } },
        after: { properties: { content: 'close-quote' } },
      },
      children: {
        span: {
          properties: { contain: 'style' },
          _pseudo: { before: { properties: { content: 'open-quote' } } },
        },
      },
    },
  };
  bakeGeneratedContentText(components);
  assert.equal(components.div._pseudo.before._text, 'A');
  assert.equal(components.div.children.span._pseudo.before._text, '1');
  assert.equal(components.div._pseudo.after._text, 'Z');
});

test('M3 bake: quote-scoping-002/003/004 all read out of the same scoping rule', () => {
  // The three sibling assertions, each a different quote keyword inside the
  // contained subtree. Building them from one factory keeps the pin honest:
  // only the span's pseudo bag differs.
  const build = (spanPseudo) => ({
    div: {
      properties: { quotes: '"A" "Z" "1" "9"' },
      _pseudo: {
        before: { properties: { content: 'open-quote' } },
        after: { properties: { content: 'close-quote' } },
      },
      children: { span: { properties: { contain: 'style' }, _pseudo: spanPseudo } },
    },
  });
  // -002 `span::after { close-quote }` → "AZZ"
  const c2 = build({ after: { properties: { content: 'close-quote' } } });
  bakeGeneratedContentText(c2);
  assert.equal(c2.div._pseudo.before._text, 'A');
  assert.equal(c2.div.children.span._pseudo.after._text, 'Z');
  assert.equal(c2.div._pseudo.after._text, 'Z');
  // -003 `span::before { no-open-quote }` → "AZ" (the span paints nothing)
  const c3 = build({ before: { properties: { content: 'no-open-quote' } } });
  bakeGeneratedContentText(c3);
  assert.equal(c3.div.children.span._pseudo.before._text, undefined);
  assert.equal(c3.div._pseudo.after._text, 'Z');
  // -004 `span::after { no-close-quote }` → "AZ"
  const c4 = build({ after: { properties: { content: 'no-close-quote' } } });
  bakeGeneratedContentText(c4);
  assert.equal(c4.div.children.span._pseudo.after._text, undefined);
  assert.equal(c4.div._pseudo.after._text, 'Z');
});

test('M3 bake: a refused bag still advances the depth of the quotes it names', () => {
  // `content: open-quote counter(x)` is refused (counter), but the browser
  // DOES open a quote there — so a later sibling must number from depth 1.
  const components = {
    a: {
      properties: { quotes: '"A" "Z" "1" "9"' },
      _pseudo: { before: { properties: { content: 'open-quote counter(x)' } } },
      children: {
        b: { properties: {}, _pseudo: { before: { properties: { content: 'open-quote' } } } },
      },
    },
  };
  bakeGeneratedContentText(components);
  assert.equal(components.a._pseudo.before._text, undefined, 'refused ⇒ no text');
  assert.equal(components.a.children.b._pseudo.before._text, '1', 'depth carried');
});

test('M3 bake: a child `quotes` declaration shadows the inherited value', () => {
  const components = {
    outer: {
      properties: { quotes: '"A" "Z"' },
      _pseudo: { before: { properties: { content: 'open-quote' } } },
      children: {
        inner: {
          properties: { quotes: '"<" ">"' },
          _pseudo: { before: { properties: { content: 'open-quote' } } },
        },
      },
    },
  };
  bakeGeneratedContentText(components);
  assert.equal(components.outer._pseudo.before._text, 'A');
  // depth 1 clamps to the single pair of the inner element's own list.
  assert.equal(components.outer.children.inner._pseudo.before._text, '<');
});

test('M3 bake: ::marker is walked for depth ONLY — never given _text', () => {
  // The MARKER CARVE-OUT (see the module banner). wave-27's `_markerText`
  // owns the marker's resolved string and its contract forbids a second
  // synthesised marker; writing here doubled every marker in css-pseudo
  // (82 -> 78 passing, marker-content-003 ink 17.69% vs a 12.39% ref).
  // The slot is still resolved so the quote counter sees it, which is what
  // css-lists/marker-quotes (`content: open-quote counter(list-item)
  // close-quote`) needs.
  const components = {
    li: {
      properties: { quotes: '"A" "Z" "1" "9"' },
      _pseudo: {
        marker: { properties: { content: 'open-quote' } },
        before: { properties: { content: 'open-quote' } },
      },
    },
  };
  bakeGeneratedContentText(components);
  assert.equal(components.li._pseudo.marker._text, undefined, 'marker never baked');
  assert.equal(components.li._pseudo.marker._lossyReasons, undefined, 'and never marked');
  // …but the depth DID advance through it: ::before is the second quote.
  assert.equal(components.li._pseudo.before._text, '1');
});

// ── the foreign-namespace refusal ───────────────────────────────────────────

test('M3 bake: SVG / MathML hosts generate no pseudo box, so no text is baked', () => {
  // Probed against the capture browser (see the module banner): a <math> or
  // <svg> host lays out NOTHING for ::before, so baking text there paints
  // ink the browser-ref does not have (measured: attr-case-sensitivity-003,
  // 1.0000 → 0.9813).
  const components = {
    m: { _tag: 'math', properties: {}, _pseudo: { before: { properties: { content: '"X"' } } } },
    s: {
      _tag: 'svg',
      properties: {},
      _pseudo: { before: { properties: { content: '"X"' } } },
      // …and the whole subtree is in the same namespace.
      children: { r: { _tag: 'rect', properties: {}, _pseudo: { before: { properties: { content: '"Y"' } } } } },
    },
    d: { _tag: 'div', properties: {}, _pseudo: { before: { properties: { content: '"Z"' } } } },
  };
  const before = JSON.stringify(components.m) + JSON.stringify(components.s);
  bakeGeneratedContentText(components);
  assert.equal(JSON.stringify(components.m) + JSON.stringify(components.s), before);
  // The HTML sibling is untouched by the refusal.
  assert.equal(components.d._pseudo.before._text, 'Z');
});

test('M3 bake: a foreign-namespace refusal still advances the quote depth', () => {
  // Same no-desynchronisation contract the counter/url refusals hold: the
  // browser opens a quote inside <svg> even though it paints no box there.
  const components = {
    root: {
      _tag: 'div',
      properties: { quotes: '"A" "Z" "1" "9"' },
      children: {
        s: { _tag: 'svg', properties: {}, _pseudo: { before: { properties: { content: 'open-quote' } } } },
        d: { _tag: 'div', properties: {}, _pseudo: { before: { properties: { content: 'open-quote' } } } },
      },
    },
  };
  bakeGeneratedContentText(components);
  assert.equal(components.root.children.s._pseudo.before._text, undefined);
  assert.equal(components.root.children.d._pseudo.before._text, '1');
});
