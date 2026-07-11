// @vitest-environment jsdom
//
// DocumentRenderer.test.tsx — the whole-document renderer + stylesheet
// LIFECYCLE (issue #41): slot composition into real DOM, variables +
// keyframes + selector rules mounted through the managed <style>
// element, and full cleanup when the renderer unmounts.
import { describe, it, expect, afterEach } from 'vitest';
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { DocumentRenderer } from '../../src/renderer/DocumentRenderer';
import { MANAGED_STYLE_ID } from '../../src/core/renderer/RuleBuilder';
import type { IRComponent, IRDocument } from '../../src/core/ir/IRModels';

// React 19 requires the act-environment opt-in for client-side act().
(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

// spec-02 pre-resolved sRGB payload helper.
const srgb = (r: number, g: number, b: number) => ({ srgb: { r, g, b } });

// A small but feature-complete document: Mode A composition (card →
// label), a variables definition on the parent, a hover selector bucket,
// and a document-level keyframes set referenced by animation-name.
const DOC: IRDocument = {
  irVersion: 2,
  minReaderVersion: 2,
  components: [
    {
      id: 'card', name: 'Card',
      properties: [{ type: 'BackgroundColor', data: srgb(0, 0, 0) }],
      variables: { '--brand': '#ff0000' },
      selectors: [{ condition: 'hover', properties: [{ type: 'BackgroundColor', data: srgb(1, 0, 0) }] }],
    },
    {
      id: 'label', name: 'Label', text: 'hi',
      properties: [{ type: 'AnimationName', data: [{ type: 'identifier', name: 'pulse' }] }],
      slot: { parent: 'card', name: 'content' },
    },
  ],
  keyframes: {
    pulse: [
      { offset: 0, properties: [{ type: 'Opacity', data: 0.2 }] },
      { offset: 1, properties: [{ type: 'Opacity', data: 1 }] },
    ],
  },
};

// One mounted root per test; torn down (and style element cleared) after.
let container: HTMLElement | null = null;
let root: Root | null = null;
afterEach(() => {
  // Unmount inside act so the insertion-effect cleanup runs deterministically.
  if (root) act(() => root!.unmount());
  container?.remove();
  root = null;
  container = null;
  document.getElementById(MANAGED_STYLE_ID)?.remove();
});

// Mount helper — renders a document into a fresh container via createRoot.
function mount(doc: IRDocument): HTMLElement {
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
  act(() => root!.render(<DocumentRenderer document={doc} />));
  return container;
}

describe('DocumentRenderer — composition into real DOM', () => {
  it('renders Mode A slot composition as nested elements', () => {
    const el = mount(DOC);
    const card = el.querySelector('[data-component-id="card"]')!;
    // The label composed INSIDE the card via its slot ref.
    expect(card.querySelector('[data-component-id="label"]')).not.toBeNull();
    // Text content reached the DOM as a bare text node.
    expect(card.textContent).toBe('hi');
    // Variables landed inline on the defining element.
    expect((card as HTMLElement).style.getPropertyValue('--brand')).toBe('#ff0000');
  });

  it('renders a Mode B zero-slot document as a flat root list', () => {
    const flat: IRDocument = {
      irVersion: 2, minReaderVersion: 2,
      components: [
        { id: 'r1', name: 'R1', properties: [] },
        { id: 'r2', name: 'R2', properties: [] },
      ] as IRComponent[],
    };
    const el = mount(flat);
    // Both roots render as siblings — nothing nested, nothing dropped.
    expect(el.querySelectorAll('[data-component-id]')).toHaveLength(2);
    expect(el.querySelector('[data-component-id="r1"]')!.children).toHaveLength(0);
  });
});

describe('DocumentRenderer — stylesheet lifecycle', () => {
  it('mounts keyframes + selector rules into the managed <style> element', () => {
    mount(DOC);
    const style = document.getElementById(MANAGED_STYLE_ID) as HTMLStyleElement;
    expect(style).not.toBeNull();
    // The full rule text is stamped for idempotency; assert on it (jsdom
    // CSSOM serialisation of @keyframes is unreliable, the stamp is not).
    const css = style.getAttribute('data-sc-css')!;
    // Wave-8 keyframes path: the document @keyframes rule is present…
    expect(css).toContain('@keyframes pulse');
    expect(css).toContain('0% { opacity: 0.2 }');
    // …and the spec-06 selector rule with its force twin.
    expect(css).toContain('.sc-card:hover, .sc-card.force-hover');
  });

  it('removes the managed <style> element on unmount (no rule leaks)', () => {
    mount(DOC);
    expect(document.getElementById(MANAGED_STYLE_ID)).not.toBeNull();
    // Unmount the renderer — the lifecycle cleanup must tear rules down.
    act(() => root!.unmount());
    root = null;
    expect(document.getElementById(MANAGED_STYLE_ID)).toBeNull();
  });

  it('remounts rules when the document identity changes', () => {
    const el = mount(DOC);
    // Same components, new keyframes name — a new document identity.
    const doc2: IRDocument = {
      ...DOC,
      keyframes: { throb: [{ offset: 0, properties: [{ type: 'Opacity', data: 0.5 }] }] },
    };
    act(() => root!.render(<DocumentRenderer document={doc2} />));
    const css = (document.getElementById(MANAGED_STYLE_ID) as HTMLStyleElement)
      .getAttribute('data-sc-css')!;
    // Old rules gone, new rules live.
    expect(css).toContain('@keyframes throb');
    expect(css).not.toContain('@keyframes pulse');
    // The rendered DOM survived the swap.
    expect(el.querySelector('[data-component-id="card"]')).not.toBeNull();
  });
});
