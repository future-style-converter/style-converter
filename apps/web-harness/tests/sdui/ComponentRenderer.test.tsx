// Tests for the three FIX-B behaviours in
// apps/web-harness/src/sdui/ComponentRenderer.tsx:
//
//   1. children recursion (swarm-001 css-overflow__clip-001)
//   2. _text rendering    (swarm-001 css-color__color-001)
//   3. aspect-ratio fit-content suppression
//      (swarm-001 css-sizing__block-aspect-ratio-032)
//
// We use renderToStaticMarkup so we don't need a JSDOM/happy-dom
// environment in vitest; the behaviour we want to pin is purely the
// rendered HTML/inline-style output. Each test also asserts a
// backwards-compat case to prove the 327-pair baseline isn't disturbed.

import { describe, it, expect } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { ComponentRenderer } from '../../src/sdui/ComponentRenderer';
import type { IRComponent, IRProperty } from '@style-converter/web/core/ir/IRModels';

// Helper: build an IRComponent without forcing every test to spell out
// all of the IR scaffolding (selectors/media/children).
function makeComp(overrides: Partial<IRComponent>): IRComponent {
  return {
    id: 'test-id',
    name: 'Test_Comp',
    properties: [],
    selectors: [],
    media: [],
    children: null,
    ...overrides,
  };
}

// Helper: the SizeApplier emits inline aspectRatio/width/etc. as strings,
// but in tests we go through the full extractor → applier pipeline so the
// IR shapes here mirror what the Kotlin converter actually produces.
function prop(type: string, data: unknown): IRProperty {
  return { type, data };
}

describe('ComponentRenderer — children recursion (swarm-001 css-overflow__clip-001)', () => {
  it('recurses into children and renders each as a nested <div>', () => {
    const child = makeComp({
      id: 'child-1',
      name: 'Child_Box',
      properties: [prop('Width', { type: 'length', px: 100 })],
    });
    const parent = makeComp({
      id: 'parent-1',
      name: 'Parent_Box',
      properties: [prop('OverflowX', 'clip')],
      children: [child],
    });
    const html = renderToStaticMarkup(<ComponentRenderer component={parent} />);
    // Both data-component-id values must appear, and child must be nested
    // inside parent (parent open tag occurs before child open tag).
    expect(html).toContain('data-component-id="parent-1"');
    expect(html).toContain('data-component-id="child-1"');
    expect(html.indexOf('data-component-id="parent-1"'))
      .toBeLessThan(html.indexOf('data-component-id="child-1"'));
  });

  it('falls back to PlaceholderContent when children is null (327-pair compat)', () => {
    const comp = makeComp({ children: null });
    const html = renderToStaticMarkup(<ComponentRenderer component={comp} />);
    // Placeholder span renders the underscore-stripped name in legacy mode.
    expect(html).toContain('<span');
    expect(html).toContain('Test Comp');
  });

  it('falls back to PlaceholderContent when children is empty array', () => {
    const comp = makeComp({ children: [] });
    const html = renderToStaticMarkup(<ComponentRenderer component={comp} />);
    expect(html).toContain('<span');
    expect(html).toContain('Test Comp');
  });
});

describe('ComponentRenderer — _text rendering (swarm-001 css-color__color-001)', () => {
  it('renders _text as the visible content when present', () => {
    const comp = makeComp({
      _text: 'Test passes if this text is green',
    });
    const html = renderToStaticMarkup(<ComponentRenderer component={comp} />);
    expect(html).toContain('Test passes if this text is green');
    // Must NOT also render the placeholder name (which would double the text).
    expect(html).not.toContain('Test Comp');
  });

  it('_text takes precedence over WPT_MODE empty-string suppression', () => {
    // WPT_MODE is read once at module load from window.location, which is
    // unset in vitest's default node environment → WPT_MODE === false.
    // We assert behaviour for the un-suppressed branch here (legacy mode);
    // the WPT_MODE branch is exercised in capture-time integration runs.
    // The key invariant tested: a non-empty _text always renders, full stop.
    const comp = makeComp({ _text: 'real content' });
    const html = renderToStaticMarkup(<ComponentRenderer component={comp} />);
    expect(html).toContain('real content');
  });

  it('empty _text falls back to placeholder name (back-compat)', () => {
    // hasText guard treats empty string as "no text"; placeholder name wins.
    const comp = makeComp({ _text: '' });
    const html = renderToStaticMarkup(<ComponentRenderer component={comp} />);
    expect(html).toContain('Test Comp');
  });

  it('absent _text renders the placeholder name (327-pair compat)', () => {
    const comp = makeComp({});
    const html = renderToStaticMarkup(<ComponentRenderer component={comp} />);
    expect(html).toContain('Test Comp');
  });
});

describe('ComponentRenderer — mixed-content fix (swarm-002 css-text-decor decorating-box)', () => {
  // Bug 1: prior renderer dropped the parent's _text when any child
  // existed, so <div>abc <span>x</span> def</div> rendered only the
  // child's 'x'. Patch emits parent _text as a leading inline node so
  // text-decoration / color inherit and the parent's glyphs are present.
  it('renders parent _text BEFORE children when both present', () => {
    const child = makeComp({
      id: 'child-1',
      name: 'Child_X',
      _text: 'x',
    });
    const parent = makeComp({
      id: 'parent-1',
      name: 'Parent_Mixed',
      _text: 'abc def',
      children: [child],
    });
    const html = renderToStaticMarkup(<ComponentRenderer component={parent} />);
    // Both the parent text and the child text must appear in the DOM.
    expect(html).toContain('abc def');
    expect(html).toContain('>x<');
    // Order: parent text comes BEFORE the child <div>.
    expect(html.indexOf('abc def')).toBeLessThan(html.indexOf('data-component-id="child-1"'));
  });

  it('emits parent _text inside an inheriting <span> (no placeholder name leak)', () => {
    // The parent's CSS (color / text-decoration / font-*) must reach the
    // text via inheritance — the wrapper <span> has no inline style,
    // which preserves that. We also must not double-render the
    // placeholder name when _text+children are both present.
    const parent = makeComp({
      id: 'parent-1',
      name: 'Parent_Mixed',
      _text: 'hello',
      children: [makeComp({ id: 'c', name: 'Child' })],
    });
    const html = renderToStaticMarkup(<ComponentRenderer component={parent} />);
    expect(html).toContain('hello');
    // Placeholder name MUST NOT also render for the parent (it would
    // appear as "Parent Mixed" with the underscore stripped).
    expect(html).not.toContain('Parent Mixed');
  });

  it('children-only fixture (no _text) renders unchanged (327-pair compat)', () => {
    // The backwards-compat case: existing fixtures with children but no
    // parent _text must produce identical DOM to before the patch.
    const child = makeComp({ id: 'c', name: 'Child' });
    const parent = makeComp({ id: 'p', name: 'Parent', children: [child] });
    const html = renderToStaticMarkup(<ComponentRenderer component={parent} />);
    expect(html).toContain('data-component-id="p"');
    expect(html).toContain('data-component-id="c"');
    // Crucially: no leading <span> with raw text.
    expect(html).not.toContain('<span>undefined</span>');
    expect(html).not.toContain('<span></span>');
  });
});

describe('ComponentRenderer — _tag element switching (swarm-002 css-counter-styles)', () => {
  // Bug 2: prior renderer ALWAYS emitted <div>, so list-style-type was
  // inert and counter-styles fixtures rendered without markers. Patch
  // switches the DOM element based on _tag (allow-list) so the browser
  // can run its native list-marker generation on <ol>/<ul>/<li>.
  it('renders as <ol> when _tag is "ol"', () => {
    const comp = makeComp({ _tag: 'ol' });
    const html = renderToStaticMarkup(<ComponentRenderer component={comp} />);
    expect(html).toMatch(/^<ol\b/);
    expect(html).toContain('</ol>');
  });

  it('renders as <li> when _tag is "li"', () => {
    const comp = makeComp({ _tag: 'li' });
    const html = renderToStaticMarkup(<ComponentRenderer component={comp} />);
    expect(html).toMatch(/^<li\b/);
    expect(html).toContain('</li>');
  });

  it('falls back to <div> when _tag is missing (327-pair compat)', () => {
    const comp = makeComp({});
    const html = renderToStaticMarkup(<ComponentRenderer component={comp} />);
    expect(html).toMatch(/^<div\b/);
  });

  it('falls back to <div> when _tag is not in the allow-list', () => {
    // Security-ish: we don't blindly emit any string the extractor
    // produced. Tags outside the allow-list (here a bogus 'script')
    // get demoted to <div>.
    const comp = makeComp({ _tag: 'script' });
    const html = renderToStaticMarkup(<ComponentRenderer component={comp} />);
    expect(html).toMatch(/^<div\b/);
  });

  it('preserves children inside the chosen tag', () => {
    const child = makeComp({ id: 'li-1', name: 'Item1', _tag: 'li' });
    const parent = makeComp({
      id: 'ol-1',
      name: 'List',
      _tag: 'ol',
      children: [child],
    });
    const html = renderToStaticMarkup(<ComponentRenderer component={parent} />);
    // The <li> must be nested inside the <ol>.
    expect(html).toMatch(/<ol\b/);
    expect(html).toMatch(/<li\b/);
    expect(html.indexOf('<ol')).toBeLessThan(html.indexOf('<li'));
  });
});

describe('ComponentRenderer — aspect-ratio fit-content suppression (swarm-001 css-sizing__block-aspect-ratio-032)', () => {
  it('skips width:fit-content + min-width when AspectRatio is set and inline axis is unconstrained', () => {
    // Mirrors block-aspect-ratio-032__2 from the WPT fixture:
    //   {AspectRatio: 4/1, Height: 300px, MaxHeight: 25px}
    // No Width / MinWidth / MaxWidth → inline axis must stay auto so the
    // browser can transfer height (25) * 4 → width 100.
    const comp = makeComp({
      properties: [
        prop('AspectRatio', { ratio: { w: 4, h: 1 }, normalizedRatio: 4 }),
        prop('Height', { type: 'length', px: 300 }),
        prop('MaxHeight', { type: 'length', px: 25 }),
      ],
    });
    const html = renderToStaticMarkup(<ComponentRenderer component={comp} />);
    // The inline `style` attribute on the wrapper <div> must NOT contain
    // `width:fit-content` and must NOT contain a `min-width:50px` floor.
    // Use a regex anchored to the first style attribute (the wrapper).
    const styleMatch = html.match(/data-component-id="test-id"[^>]*style="([^"]*)"/);
    expect(styleMatch).not.toBeNull();
    const style = styleMatch![1];
    expect(style).not.toMatch(/width:\s*fit-content/);
    expect(style).not.toMatch(/min-width:\s*50px/);
    // aspect-ratio itself must reach the DOM.
    expect(style).toMatch(/aspect-ratio:\s*4/);
  });

  it('skips height/min-height defaults when AspectRatio + width-only', () => {
    // Inverse case: width is constrained, height should be left auto for
    // the width→height transfer.
    const comp = makeComp({
      properties: [
        prop('AspectRatio', { ratio: { w: 4, h: 1 }, normalizedRatio: 4 }),
        prop('Width', { type: 'length', px: 200 }),
      ],
    });
    const html = renderToStaticMarkup(<ComponentRenderer component={comp} />);
    const styleMatch = html.match(/data-component-id="test-id"[^>]*style="([^"]*)"/);
    const style = styleMatch![1];
    expect(style).not.toMatch(/min-height:\s*30px/);
    expect(style).toMatch(/aspect-ratio:\s*4/);
    expect(style).toMatch(/width:\s*200px/);
  });

  it('keeps width:fit-content when no AspectRatio (327-pair compat)', () => {
    // Generic placeholder component — fit-content default must still apply.
    const comp = makeComp({});
    const html = renderToStaticMarkup(<ComponentRenderer component={comp} />);
    const styleMatch = html.match(/data-component-id="test-id"[^>]*style="([^"]*)"/);
    const style = styleMatch![1];
    expect(style).toMatch(/width:\s*fit-content/);
    expect(style).toMatch(/min-width:\s*50px/);
    expect(style).toMatch(/min-height:\s*30px/);
  });

  it('keeps width:fit-content when AspectRatio is set but BOTH axes constrained', () => {
    // Spec doesn't transfer when both axes are definite — leave defaults
    // alone. Mirrors block-aspect-ratio-032__0/__1 reference squares.
    const comp = makeComp({
      properties: [
        prop('AspectRatio', { ratio: { w: 1, h: 1 }, normalizedRatio: 1 }),
        prop('Width', { type: 'length', px: 100 }),
        prop('Height', { type: 'length', px: 100 }),
      ],
    });
    const html = renderToStaticMarkup(<ComponentRenderer component={comp} />);
    const styleMatch = html.match(/data-component-id="test-id"[^>]*style="([^"]*)"/);
    const style = styleMatch![1];
    // Both axes constrained → both defaults effectively no-op (the spread
    // overrides them) but the carve-out should NOT trigger; the explicit
    // width/height from IR wins via the spread.
    expect(style).toMatch(/width:\s*100px/);
    expect(style).toMatch(/height:\s*100px/);
  });
});
