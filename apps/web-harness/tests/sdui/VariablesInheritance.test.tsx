// @vitest-environment jsdom
//
// VariablesInheritance.test.tsx — proves the wave-6 custom-property contract
// end-to-end through the REAL composition pipeline: a flat IR v2 document
// with slot refs → Composer.composeTree → ComponentRenderer → live DOM in
// jsdom → getComputedStyle.
//
// What jsdom gives us (probed against jsdom 29): getComputedStyle DOES
// implement custom-property INHERITANCE — a `--name` defined on an ancestor
// element's inline style is visible on every descendant's computed style,
// and a descendant redefinition shadows it (css-variables-1 §2.3). jsdom
// does NOT substitute var() into regular longhands (that's a real-browser
// behaviour, exercised by the fixtures/fidelity/tokens/ captures), so for
// the consuming declarations we pin the VERBATIM var() text — exactly what
// a browser needs to finish the job.
import { describe, it, expect, beforeEach } from 'vitest';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { composeTree } from '../../src/sdui/Composer';
import { ComponentList } from '../../src/sdui/ComponentRenderer';
import type { IRDocument } from '@style-converter/web/core/ir/IRModels';

// Mirror of the variables-inheritance golden
// (schema/conformance/fixtures/v2/variables-inheritance.json): a three-level
// slot chain where the root defines the theme, the leaf shadows one token.
const doc: IRDocument = {
  irVersion: 2,
  minReaderVersion: 2,
  components: [
    {
      id: 'themeroot-001',
      name: 'ThemeRoot',
      properties: [
        { type: 'Width', data: { type: 'length', px: 260 } },
        { type: 'BackgroundColor', data: { original: 'var(--panel-bg)' } },
      ],
      variables: { '--panel-bg': '#1f2937', '--accent': '#e74c3c', '--Pad': '10px' },
    },
    {
      id: 'mid-002',
      name: 'mid',
      properties: [
        // Case-sensitive reference — must round-trip as --Pad, not --pad.
        { type: 'PaddingTop', data: { expr: 'var(--Pad)' } },
      ],
      slot: { parent: 'themeroot-001' },
    },
    {
      id: 'leaf-003',
      name: 'leaf',
      properties: [
        { type: 'BackgroundColor', data: { original: 'var(--accent)' } },
        { type: 'Height', data: { type: 'length', px: 40 } },
      ],
      // Shadowing: the leaf redefines --accent for itself + descendants.
      variables: { '--accent': '#2ecc71' },
      slot: { parent: 'mid-002' },
    },
  ],
};

// Render the composed forest into the jsdom document so getComputedStyle
// operates on a live DOM tree. renderToStaticMarkup keeps the test free of
// react-dom/client + act() ceremony; inheritance only needs real elements.
function mount(): void {
  const roots = composeTree(doc);
  document.body.innerHTML = renderToStaticMarkup(<ComponentList nodes={roots} />);
}

// Query helper — every rendered component carries data-component-id.
const el = (id: string): HTMLElement =>
  document.querySelector(`[data-component-id="${id}"]`) as HTMLElement;

beforeEach(mount);

describe('variables — definitions land on the defining element', () => {
  it('root inline style carries all three definitions verbatim', () => {
    const root = el('themeroot-001');
    expect(root.style.getPropertyValue('--panel-bg')).toBe('#1f2937');
    expect(root.style.getPropertyValue('--accent')).toBe('#e74c3c');
    expect(root.style.getPropertyValue('--Pad')).toBe('10px');       // case preserved
  });
  it('components without variables get no custom-property keys', () => {
    const mid = el('mid-002');
    expect(mid.style.getPropertyValue('--accent')).toBe('');         // nothing defined here
  });
});

describe('variables — a child resolves a parent-defined variable (getComputedStyle)', () => {
  it('leaf inherits --panel-bg from the slot-composed root two levels up', () => {
    const cs = getComputedStyle(el('leaf-003'));
    expect(cs.getPropertyValue('--panel-bg')).toBe('#1f2937');       // inherited, not local
  });
  it('mid inherits the case-sensitive --Pad definition', () => {
    const cs = getComputedStyle(el('mid-002'));
    expect(cs.getPropertyValue('--Pad')).toBe('10px');               // exact-case lookup hits
    expect(cs.getPropertyValue('--pad')).toBe('');                   // lowercase MISSES — names are case-sensitive
  });
  it('leaf shadows --accent per css-variables-1 §2.3 element-scope rule', () => {
    expect(getComputedStyle(el('leaf-003')).getPropertyValue('--accent')).toBe('#2ecc71');
    // …while the outer scope still sees the root's value.
    expect(getComputedStyle(el('mid-002')).getPropertyValue('--accent')).toBe('#e74c3c');
  });
});

describe('references — consuming declarations carry verbatim var() text', () => {
  it('root background references its own token verbatim', () => {
    expect(el('themeroot-001').style.backgroundColor).toBe('var(--panel-bg)');
  });
  it('mid padding keeps the case-sensitive reference verbatim', () => {
    expect(el('mid-002').style.paddingTop).toBe('var(--Pad)');
  });
  it('leaf background references the token its own definition shadows', () => {
    expect(el('leaf-003').style.backgroundColor).toBe('var(--accent)');
  });
});
