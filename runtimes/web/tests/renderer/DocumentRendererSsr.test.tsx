// DocumentRendererSsr.test.tsx — server-side rendering path (node env,
// NO DOM): the renderer must produce markup via react-dom/server with
// zero document access, and the stylesheet must be available as a
// string via the engine's buildStylesheet — the exact recipe the
// package README's real-app example uses.
import { describe, it, expect } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { DocumentRenderer } from '../../src/renderer/DocumentRenderer';
import { buildStylesheet } from '../../src/core/renderer/RuleBuilder';
import type { IRDocument } from '../../src/core/ir/IRModels';

// Token-themed miniature: variables define the theme on the root; the
// child consumes them through the Generic var() pass-through; a
// keyframes set + selector bucket exercise the stylesheet string.
const DOC: IRDocument = {
  irVersion: 2,
  minReaderVersion: 2,
  components: [
    {
      id: 'theme-root', name: 'ThemeRoot', properties: [],
      variables: { '--accent': '#e74c3c' },
      selectors: [{ condition: 'hover', properties: [{ type: 'Opacity', data: 0.8 }] }],
    },
    {
      id: 'chip', name: 'Chip', text: 'Tokens!',
      properties: [{ type: 'Generic', data: { propertyName: 'background-color', rawValue: 'var(--accent)' } }],
      slot: { parent: 'theme-root', name: 'content' },
    },
  ],
  keyframes: { fade: [{ offset: 0, properties: [{ type: 'Opacity', data: 0 }] }] },
};

describe('DocumentRenderer — SSR (no DOM)', () => {
  it('renderToStaticMarkup produces the composed tree without a document', () => {
    // In vitest's node env there is no global document — this render
    // must not touch it (insertion effects are skipped during SSR).
    const html = renderToStaticMarkup(<DocumentRenderer document={DOC} />);
    // Composition: chip nested inside theme-root.
    expect(html.indexOf('data-component-id="theme-root"'))
      .toBeLessThan(html.indexOf('data-component-id="chip"'));
    // Token definition inline on the root, var() consumed by the child.
    expect(html).toContain('--accent:#e74c3c');
    expect(html).toContain('background-color:var(--accent)');
    expect(html).toContain('Tokens!');
  });

  it('buildStylesheet supplies the SSR stylesheet string', () => {
    const css = buildStylesheet(DOC.components, DOC.keyframes);
    // Keyframes + selector rule both present in the inline-able string.
    expect(css).toContain('@keyframes fade');
    expect(css).toContain('.sc-theme-root:hover');
  });
});
