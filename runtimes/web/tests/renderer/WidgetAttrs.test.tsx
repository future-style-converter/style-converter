// WidgetAttrs.test.tsx — wave-20 lane W1: the wire `meta.attrs` → DOM
// policy (WidgetAttrs.ts) and its application inside the renderer core
// (NodeRenderer void/textarea/regular branches + the decorateProps hook).
//
// Pins:
//   - widgetDomProps mapping table (checked→defaultChecked, value routing
//     per element, plain passthroughs, unknown-key tracking).
//   - attrs are keyed on the RESOLVED element: a skin's <div> demotion
//     yields byte-identical DOM (the legacy-capture protection).
//   - textarea renders its wire text as value content with NO children.
//   - decorateProps is the last word on element props (harness inert).
import { describe, it, expect, vi, afterEach } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { NodeRenderer } from '../../src/renderer/NodeRenderer';
import { WIDGET_TAGS, widgetDomProps } from '../../src/renderer/WidgetAttrs';
import type { ComposedNode } from '../../src/renderer/Composer';
import type { IRComponent } from '../../src/core/ir/IRModels';

// Helpers mirror NodeRenderer.test.tsx (v2 component + composed wrapper).
function comp(overrides: Partial<IRComponent> = {}): IRComponent {
  return { id: 'w-id', name: 'Widget_Comp', properties: [], ...overrides };
}
function node(component: IRComponent, children: ComposedNode[] = []): ComposedNode {
  return { component, children };
}
const html = (n: ComposedNode, options?: Parameters<typeof NodeRenderer>[0]['options']) =>
  renderToStaticMarkup(<NodeRenderer node={n} options={options} />);

afterEach(() => vi.restoreAllMocks());

describe('widgetDomProps — the wire → React prop table', () => {
  it('maps checked to the uncontrolled defaultChecked', () => {
    expect(widgetDomProps('input', { type: 'checkbox', checked: true }))
      .toEqual({ type: 'checkbox', defaultChecked: true });
  });

  it('routes value per element: input/textarea uncontrolled, meter/progress/option plain', () => {
    // input value = INITIAL text content → defaultValue (no onChange demanded).
    expect(widgetDomProps('input', { value: 'input-text' })).toEqual({ defaultValue: 'input-text' });
    expect(widgetDomProps('textarea', { value: 'ta' })).toEqual({ defaultValue: 'ta' });
    // meter/progress value = float display attribute (HTML §4.10.13/14).
    expect(widgetDomProps('meter', { value: 0.5 })).toEqual({ value: 0.5 });
    expect(widgetDomProps('progress', { value: 0.5 })).toEqual({ value: 0.5 });
    // option value = the plain content attribute.
    expect(widgetDomProps('option', { value: 'sm' })).toEqual({ value: 'sm' });
  });

  it('button-like inputs get the CONTROLLED value (label paints, never the UA fallback)', () => {
    // HTML §4.10.5.1.20-22: submit/reset/button value IS the painted
    // label. React 19's initInput early-returns without applying
    // defaultValue for submit/reset with no `value` prop, so only the
    // controlled prop reliably paints 'input-submit' instead of the UA
    // 'Submit' (intrinsic width 77px vs 40px — every wrap point shifts).
    for (const type of ['submit', 'reset', 'button', 'SUBMIT']) {
      const props = widgetDomProps('input', { type, value: `input-${type.toLowerCase()}` });
      // Controlled prop, not the uncontrolled initial value.
      expect(props.value).toBe(`input-${type.toLowerCase()}`);
      expect(props.defaultValue).toBeUndefined();
      // The controlled contract is satisfied by a noop handler — no
      // readonly="" bytes ever reach the capture DOM.
      expect(typeof props.onChange).toBe('function');
    }
    // Editable text families keep the uncontrolled mapping untouched.
    const text = widgetDomProps('input', { type: 'text', value: 'input-text' });
    expect(text).toEqual({ type: 'text', defaultValue: 'input-text' });
  });

  it('passes multiple/size/alt/min/max/disabled/type through by name', () => {
    expect(widgetDomProps('select', { multiple: true, size: '4' }))
      .toEqual({ multiple: true, size: '4' });
    expect(widgetDomProps('input', { type: 'range', min: -1.5, max: 100, disabled: true, alt: 'x' }))
      .toEqual({ type: 'range', min: -1.5, max: 100, disabled: true, alt: 'x' });
  });

  it('returns {} for non-widget elements and missing attrs (byte-stable demotion)', () => {
    // The legacy harness demotes input→div: attrs must not leak onto it.
    expect(widgetDomProps('div', { type: 'checkbox', checked: true })).toEqual({});
    expect(widgetDomProps('input', null)).toEqual({});
    expect(widgetDomProps('input', undefined)).toEqual({});
  });

  it('exports the byte-parallel widget tag set (extractor twin)', () => {
    // Must equal tools/titan/extract-fixture.mjs WIDGET_ATTR_TAGS exactly.
    expect([...WIDGET_TAGS].sort()).toEqual(
      ['a', 'button', 'input', 'meter', 'option', 'progress', 'select', 'textarea'],
    );
  });
});

describe('NodeRenderer applies wire attrs onto real widget elements', () => {
  it('void branch: a checked checkbox input renders checked', () => {
    const out = html(node(comp({
      meta: { sourceTag: 'input', attrs: { type: 'checkbox', checked: true } },
    })));
    // Real input element with the chrome-selecting type + checked state.
    expect(out).toContain('<input');
    expect(out).toContain('type="checkbox"');
    expect(out).toContain('checked=""');
  });

  it('void branch: a submit input serializes its source label as the controlled value', () => {
    // The FIX-1 regression shape: `<input type=submit value=input-submit>`
    // must reach the DOM with the source label — the controlled `value`
    // prop serializes and, on the client, survives React 19's initInput
    // submit/reset early-return (the defaultValue mapping did not).
    const out = html(node(comp({
      meta: { sourceTag: 'input', attrs: { type: 'submit', value: 'input-submit' } },
    })));
    expect(out).toContain('type="submit"');
    expect(out).toContain('value="input-submit"');
    // No readonly attribute bytes (the controlled contract is met by the
    // noop handler, which never serializes).
    expect(out).not.toContain('readonly');
  });

  it('void branch: an explicit value attribute wins over wire text', () => {
    // text would map to defaultValue; the source `value` attr must win.
    const out = html(node(comp({
      text: 'fallback',
      meta: { sourceTag: 'input', attrs: { type: 'text', value: 'input-text' } },
    })));
    expect(out).toContain('value="input-text"');
    expect(out).not.toContain('fallback');
  });

  it('textarea: wire text becomes the value content, children are dropped loudly', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const out = html(node(
      comp({ text: 'textarea', meta: { sourceTag: 'textarea' } }),
      [node(comp({ id: 'kid', name: 'Kid' }))],
    ));
    // Value content serializes as the element text (uncontrolled default).
    expect(out).toContain('<textarea');
    expect(out).toContain('>textarea</textarea>');
    // The composed child cannot render inside character data — warned.
    expect(out).not.toContain('kid');
    expect(warn).toHaveBeenCalledWith(expect.stringContaining('textarea'));
  });

  it('select renders option children with their own attrs', () => {
    const out = html(node(
      comp({ meta: { sourceTag: 'select', attrs: { multiple: true } } }),
      [node(comp({ id: 'opt', name: 'Opt', text: 'select-multiple', meta: { sourceTag: 'option' } }))],
    ));
    // Listbox switch present on the select…
    expect(out).toContain('<select');
    expect(out).toContain('multiple=""');
    // …and the option child renders natively with its text.
    expect(out).toContain('<option');
    expect(out).toContain('select-multiple');
  });

  it('meter/progress numeric values reach the DOM', () => {
    const meter = html(node(comp({ meta: { sourceTag: 'meter', attrs: { value: 0.5 } } })));
    expect(meter).toContain('<meter');
    expect(meter).toContain('value="0.5"');
    const progress = html(node(comp({ meta: { sourceTag: 'progress', attrs: { value: 0.5 } } })));
    expect(progress).toContain('<progress');
    expect(progress).toContain('value="0.5"');
  });

  it('a skin demoting the tag to <div> gets ZERO attrs (legacy protection)', () => {
    // The non-WPT harness path: mapTag → 'div'; attrs must not appear.
    const out = html(
      node(comp({ meta: { sourceTag: 'input', attrs: { type: 'checkbox', checked: true } } })),
      { mapTag: () => 'div' },
    );
    expect(out).toBe(
      '<div data-component-id="w-id" data-component-name="Widget_Comp" class="sc-w-id"></div>',
    );
  });

  it('decorateProps is the last word (harness inert calibration shape)', () => {
    const out = html(
      node(comp({ meta: { sourceTag: 'input', attrs: { type: 'checkbox' } } })),
      {
        decorateProps: (props, elementName) =>
          WIDGET_TAGS.has(elementName) ? { ...props, inert: true, tabIndex: -1 } : props,
      },
    );
    // The React-19 inert boolean + the focusability suppression landed.
    expect(out).toContain('inert=""');
    expect(out).toContain('tabindex="-1"');
  });
});
