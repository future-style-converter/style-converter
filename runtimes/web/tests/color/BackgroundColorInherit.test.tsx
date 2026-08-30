// @vitest-environment jsdom
//
// BackgroundColorInherit.test.tsx — wave-49 lane A5.
//
// css-cascade-5 §7.3.2 `inherit` on `background-color`. Every IR payload in
// this file is copied VERBATIM out of the wave-48 corpus run:
//   tools/titan/runs/wave48-final/sections/css-color/per-test-ir/
//     wpt__css-color__currentcolor-002.json
//     wpt__css-color__color-mix-currentcolor-001.json
//     wpt__css-color__color-mix-currentcolor-002.json
// Nothing here is an invented shape.
//
// Two things are pinned:
//   1. extractor+applier emit the CSS keyword rather than dropping it
//      (the drop is the bug: the child painted nothing and the parent's
//      wrong colour showed through — currentcolor-002 web scored ssim
//      0.9999 with colorFailed / labDeltaE.mean 11.13);
//   2. the renderer nests the components as DIRECT DOM parent/child, which
//      is the precondition for the browser's cascade to be the resolver.
//      An intervening wrapper element would silently break `inherit` (its
//      own computed background-color is the initial `transparent`), so this
//      is asserted on the real DocumentRenderer, not assumed.
import { describe, it, expect, afterEach } from 'vitest';
import { act, createElement } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { extractBackgroundColor } from '../../src/engine/color/BackgroundColorExtractor';
import { applyBackgroundColor } from '../../src/engine/color/BackgroundColorApplier';
import { DocumentRenderer } from '../../src/renderer/DocumentRenderer';
import { MANAGED_STYLE_ID } from '../../src/core/renderer/RuleBuilder';
import type { IRDocument } from '../../src/core/ir/IRModels';

// React 19 requires the act-environment opt-in for client-side act().
(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

// Wire shapes lifted verbatim from the per-test-ir files named above.
const INHERIT_WIRE = { original: 'inherit' };
const CURRENTCOLOR_WIRE = { original: 'currentColor' };
const MIX_SRGB_WIRE = {
  original: {
    type: 'color-mix', colorSpace: 'srgb',
    color1: 'currentColor', percent1: 50, color2: 'green',
  },
};
const MIX_LCH_WIRE = {
  original: {
    type: 'color-mix', colorSpace: 'lch',
    color1: 'currentColor', percent1: 50, color2: 'blue',
  },
};
const p = (type: string, data: unknown) => ({ type, data });

describe('background-color: inherit (css-cascade-5 §7.3.2)', () => {
  it('classifies the {original:"inherit"} envelope as the cascade keyword', () => {
    const cfg = extractBackgroundColor([p('BackgroundColor', INHERIT_WIRE)]);
    // NOT a colour: `color` must stay undefined so nothing downstream
    // mistakes the keyword for a paintable value.
    expect(cfg).toEqual({ inherit: true, color: undefined });
  });

  it('accepts the bare-string wire form too', () => {
    expect(extractBackgroundColor([p('BackgroundColor', 'inherit')]).inherit).toBe(true);
    // Case-insensitive: CSS keywords are ASCII case-insensitive (css-values-4 §4.1).
    expect(extractBackgroundColor([p('BackgroundColor', 'INHERIT')]).inherit).toBe(true);
  });

  it('emits the keyword verbatim so the DOM cascade resolves it', () => {
    const out = applyBackgroundColor(extractBackgroundColor([p('BackgroundColor', INHERIT_WIRE)]));
    expect(out.backgroundColor).toBe('inherit');
  });

  it('last-wins both ways against a real colour (single-pass cascade)', () => {
    // colour then inherit → inherit
    expect(extractBackgroundColor([
      p('BackgroundColor', { srgb: { r: 1, g: 0, b: 0 }, original: 'red' }),
      p('BackgroundColor', INHERIT_WIRE),
    ])).toEqual({ inherit: true, color: undefined });
    // inherit then colour → colour
    const after = extractBackgroundColor([
      p('BackgroundColor', INHERIT_WIRE),
      p('BackgroundColor', { srgb: { r: 1, g: 0, b: 0 }, original: 'red' }),
    ]);
    expect(after.inherit).toBeUndefined();
    expect(after.color).toMatchObject({ kind: 'srgb', r: 1 });
  });

  it('leaves currentColor and color-mix untouched (they are colours, not keywords)', () => {
    // The PARENT declarations of all three corpus tests: these must keep
    // flowing through the dynamic-colour path unchanged, because the child's
    // `inherit` needs the parent's UNRESOLVED computed value to re-resolve
    // against its own `color` (css-color-4 §6.4 "computes to itself").
    expect(applyBackgroundColor(extractBackgroundColor(
      [p('BackgroundColor', CURRENTCOLOR_WIRE)])).backgroundColor).toBe('currentColor');
    expect(applyBackgroundColor(extractBackgroundColor(
      [p('BackgroundColor', MIX_SRGB_WIRE)])).backgroundColor)
      .toBe('color-mix(in srgb, currentColor 50%, green)');
    expect(applyBackgroundColor(extractBackgroundColor(
      [p('BackgroundColor', MIX_LCH_WIRE)])).backgroundColor)
      .toBe('color-mix(in lch, currentColor 50%, blue)');
  });

  it('does NOT claim the other CSS-wide keywords', () => {
    // initial/unset/revert all compute to the initial value on a
    // non-inherited property; emitting no declaration already yields that,
    // so they must not take the `inherit` branch.
    for (const kw of ['initial', 'unset', 'revert', 'revert-layer']) {
      expect(extractBackgroundColor([p('BackgroundColor', { original: kw })]).inherit)
        .toBeUndefined();
    }
  });
});

// ---------------------------------------------------------------------------
// DOM-shape precondition: the keyword only works if the components really are
// direct parent/child in the rendered tree.
// ---------------------------------------------------------------------------

let container: HTMLElement | null = null;
let root: Root | null = null;
afterEach(() => {
  if (root) act(() => root!.unmount());
  container?.remove();
  root = null;
  container = null;
  document.getElementById(MANAGED_STYLE_ID)?.remove();
});

// The currentcolor-002 document, reduced to the three nested divs whose
// ids/slots/properties are copied from the per-test-ir file.
const DOC: IRDocument = {
  irVersion: 2,
  minReaderVersion: 2,
  components: [
    {
      id: 'outer', name: 'outer',
      properties: [
        p('Color', { srgb: { r: 1, g: 0, b: 0 }, original: 'red' }),
        p('BackgroundColor', CURRENTCOLOR_WIRE),
      ],
    },
    {
      id: 'middle', name: 'middle',
      properties: [p('BackgroundColor', INHERIT_WIRE)],
      slot: { parent: 'outer' },
    },
    {
      id: 'inner', name: 'inner', text: 'FAIL',
      properties: [
        p('Color', { srgb: { r: 0, g: 0.5019607843137255, b: 0 }, original: 'green' }),
        p('BackgroundColor', INHERIT_WIRE),
      ],
      slot: { parent: 'middle' },
    },
  ],
} as unknown as IRDocument;

describe('rendered DOM keeps the inherit chain intact', () => {
  it('nests outer > middle > inner with the keyword on the inline styles', () => {
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
    act(() => root!.render(createElement(DocumentRenderer, { document: DOC })));

    // Locate the three boxes by the inline background-color each carries.
    // jsdom normalises CSS keywords to lower case when it re-serialises the
    // inline style, so compare case-insensitively (CSS keywords are ASCII
    // case-insensitive anyway — css-values-4 §4.1).
    const bg = (e: HTMLElement) => e.style.backgroundColor.toLowerCase();
    const all = Array.from(container.querySelectorAll<HTMLElement>('*'));
    const outerEl = all.find((e) => bg(e) === 'currentcolor');
    const inheritEls = all.filter((e) => bg(e) === 'inherit');
    expect(outerEl).toBeTruthy();
    expect(inheritEls).toHaveLength(2);

    // The chain must be unbroken: middle's parent is outer, inner's is middle.
    // `inherit` reads the PARENT ELEMENT's computed value, so any element
    // interposed here (a layout wrapper with no background) would resolve the
    // keyword to `transparent` and re-break the test.
    const [middleEl, innerEl] = inheritEls;
    expect(middleEl.parentElement).toBe(outerEl);
    expect(innerEl.parentElement).toBe(middleEl);
  });
});
