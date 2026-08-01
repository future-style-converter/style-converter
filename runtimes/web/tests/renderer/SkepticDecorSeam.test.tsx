// SkepticDecorSeam.test.tsx — wave-22 lane DECOR ADVERSARIAL review.
//
// The lane claims "non-decorated components are BYTE-IDENTICAL". Its own
// suite asserts that against hand-written expectations authored AFTER the
// change, which cannot detect an injected attribute or style key.
//
// ONE-SHOT PROOF, executed during the review and NOT vendored here: the
// pre-change renderer was extracted with
//
//   git show <pre-wave-22>:runtimes/web/src/renderer/NodeRenderer.ts
//
// (import paths rewritten, nothing else), and all nine shapes below were
// rendered through BOTH renderers and string-compared — identical. That
// copy is deliberately not kept in the tree: a vendored 400-line renderer
// snapshot fails spuriously on every later, legitimate NodeRenderer edit.
// What IS kept is the drift-free invariant the proof established: the
// decoration hook must be a total no-op — no wrapper, no injected
// `text-decoration-line`, and the SAME ReactNode reference back — for
// every component without `meta.decorations`.
//
// The suite also drives the LIVE golden (schema/conformance/fixtures/v2/
// decorations.json) through the REAL decode path into the renderer, so
// the DOM snapshot is pinned to shipped bytes rather than a literal.
import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { renderToStaticMarkup } from 'react-dom/server';
import { NodeRenderer } from '../../src/renderer/NodeRenderer';
import {
  decorationHostStyle, withDecorationSpans,
} from '../../src/renderer/DecorationSpans';
import { decodeIRDocument } from '../../src/core/ir/IRDecode';
import { composeTree } from '../../src/renderer/Composer';
import type { ComposedNode } from '../../src/renderer/Composer';
import type { IRComponent } from '../../src/core/ir/IRModels';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(HERE, '..', '..', '..', '..');

function comp(overrides: Partial<IRComponent> = {}): IRComponent {
  return { id: 's-id', name: 'Skeptic_Comp', properties: [], ...overrides };
}
function node(component: IRComponent, children: ComposedNode[] = []): ComposedNode {
  return { component, children };
}
const now = (n: ComposedNode) => renderToStaticMarkup(<NodeRenderer node={n} />);

describe('S1 — the decoration hook is a total no-op without a wire', () => {
  // A spread of shapes that exercise every branch the decoration hook
  // sits next to: styles, variables, meta.sourceTag, widget attrs,
  // children, text, void elements, pseudos.
  const shapes: Array<[string, ComposedNode]> = [
    ['bare', node(comp())],
    ['text only', node(comp({ text: 'hello' }))],
    ['styled text', node(comp({
      text: 'styled',
      properties: [
        { type: 'TextDecorationLine', data: { underline: true, overline: false, lineThrough: false } },
        { type: 'TextDecorationColor', data: { srgb: { r: 0, g: 0, b: 1, a: 1 }, original: 'blue' } },
        { type: 'TextDecorationStyle', data: 'DOTTED' },
      ],
    }))],
    ['sourceTag span', node(comp({ text: 'x', meta: { sourceTag: 'span' } }))],
    ['sourceTag input (widget attrs)', node(comp({
      meta: { sourceTag: 'input', attrs: { type: 'checkbox' } },
    }))],
    ['meta without decorations', node(comp({ text: 'x', meta: { sourceTag: 'u', role: 'note' } }))],
    ['with children', node(comp({ text: 'parent' }), [node(comp({ id: 'kid', text: 'child' }))])],
    ['variables', node(comp({ text: 'v', variables: { '--brand': '#0f0' } }))],
    ['void element', node(comp({ meta: { sourceTag: 'br' } }))],
  ];

  for (const [label, n] of shapes) {
    it(`untouched: ${label}`, () => {
      const out = now(n);
      // Sanity: the renderer really did produce markup for this shape.
      expect(out.length).toBeGreaterThan(0);
      // No synthetic wrapper…
      expect(out).not.toContain('data-decoration');
      // …and no injected host override. The 'styled text' shape declares
      // `text-decoration-line: underline` from its own properties, so the
      // assertion is specifically that the wire's `none` never appears.
      expect(out).not.toContain('text-decoration-line:none');
      // The hook itself: null style patch, identical node reference.
      expect(decorationHostStyle(n.component.meta?.decorations)).toBeNull();
      const sentinel = 'CONTENT';
      expect(withDecorationSpans(sentinel, n.component.meta?.decorations))
        .toBe(sentinel);
    });
  }
});

describe('S2 — the LIVE golden through the real decode path', () => {
  const doc = decodeIRDocument(
    JSON.parse(readFileSync(
      path.join(REPO_ROOT, 'schema/conformance/fixtures/v2/decorations.json'), 'utf8')),
  );
  const roots = composeTree(doc);
  const find = (id: string): ComposedNode => {
    const stack = [...roots];
    while (stack.length) {
      const n = stack.pop()!;
      if (n.component.id === id) return n;
      stack.push(...n.children);
    }
    throw new Error(`component ${id} not composed`);
  };

  it('rebuilds the three-colour chain outermost-first, host line off', () => {
    const out = now(find('decor-chain-002'));
    // Host stops painting its own merged line…
    expect(out).toContain('text-decoration-line:none');
    // …and the wrappers nest outermost-first, each with its own colour.
    const order = [...out.matchAll(/data-decoration="([a-z-]+)"/g)].map((m) => m[1]);
    expect(order).toEqual(['underline', 'overline', 'line-through']);
    expect(out).toContain('text-decoration-line:underline;text-decoration-color:blue');
    expect(out).toContain('text-decoration-line:overline;text-decoration-color:gray');
    expect(out).toContain('text-decoration-line:line-through;text-decoration-color:green');
    // The run's text is INSIDE the innermost wrapper (one text node).
    expect(out).toMatch(
      /data-decoration="line-through"[^>]*>Black text with blue underline/);
    // Exactly three wrappers — no duplicate paint from the flat bag.
    expect(order).toHaveLength(3);
  });

  it('an uncoloured entry emits no colour declaration (currentColor)', () => {
    const out = now(find('decor-currentcolor-003'));
    expect(out).toContain('data-decoration="underline"');
    // The wrapper must NOT invent a colour — §2.2 initial is currentColor.
    const wrapper = out.match(/<span data-decoration="underline" style="([^"]*)"/)![1];
    expect(wrapper).not.toContain('text-decoration-color');
  });

  it('the functional/hex golden keeps the AUTHORED token verbatim', () => {
    const out = now(find('decor-functional-colour-004'));
    // Web hands the token straight to Chromium, so the two tokens the
    // natives disagree about are both correct here.
    expect(out).toContain('text-decoration-color:#00ff00');
    expect(out).toContain('text-decoration-color:rgb(0, 0, 255)');
  });
});

describe('S3 — authoritative-but-empty and unpaintable entries', () => {
  it('a fully-filtered wire paints nothing but still silences the host', () => {
    const out = now(node(comp({
      text: 'x',
      properties: [{ type: 'TextDecorationLine', data: { underline: true, overline: false, lineThrough: false } }],
      meta: { decorations: [{ line: 'blink' }, { line: 'none' }] } as never,
    })));
    expect(out).not.toContain('data-decoration');
    expect(out).toContain('text-decoration-line:none');
  });
});
