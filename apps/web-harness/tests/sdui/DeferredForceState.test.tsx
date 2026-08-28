// @vitest-environment jsdom
// @vitest-environment-options { "url": "http://localhost:3000/?mode=capture&forceState=hover&animationTime=0.4" }
//
// DeferredForceState.test.tsx — the transition-capture deferral
// (spec 07 §4, docs/DYNAMIC_CAPTURE.md §4).
//
// A CSS transition's timeline zero is the base→forced flip, and the
// browser only creates a CSSTransition object during a style recalc that
// CHANGES a value. Baking `force-hover` into the first className means the
// element's first-ever computed style IS the hover style — there is no
// before-value, so `document.getAnimations()` returns nothing and the
// seize has nothing to seek. Measured before this landed: transitions.json
// rendered its ENDPOINT at every sampled t on all three platforms.
//
// So when a clock is ALSO pinned, ComponentRenderer mounts in BASE state
// and capture-screenshots.mjs adds the class post-paint.
//
// The environment URL above carries BOTH knobs — that is the whole point:
// DynamicRules.test.tsx pins the same renderer with `forceState` ALONE and
// asserts the class IS present. The two files together pin both arms, so
// neither behaviour can drift without a test moving.
import { describe, it, expect } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { ComponentRenderer } from '../../src/sdui/ComponentRenderer';
import type { ComposedNode } from '../../src/sdui/Composer';
import type { IRComponent } from '@style-converter/web/core/ir/IRModels';

const srgb = (r: number, g: number, b: number) => ({ srgb: { r, g, b } });
const node = (component: IRComponent): ComposedNode => ({ component, children: [] });

const hoverComp: IRComponent = {
  id: 'ds-hover',
  name: 'DS_HoverSwap',
  properties: [{ type: 'BackgroundColor', data: srgb(0, 0, 0) }],
  selectors: [{ condition: 'hover', properties: [{ type: 'BackgroundColor', data: srgb(1, 0, 0) }] }],
};

describe('forced state is DEFERRED when an animation clock is pinned', () => {
  it('does not stamp force-<state> at mount', () => {
    const html = renderToStaticMarkup(<ComponentRenderer node={node(hoverComp)} />);
    // The rule-target class must still be there — the element has to be
    // selectable for the post-paint classList.add to have a twin rule.
    expect(html).toContain('sc-ds-hover');
    // …but NOT the force class. If this regresses, the element mounts
    // already-forced, no transition object is ever created, and every
    // sampled t silently captures the same static endpoint frame.
    expect(html).not.toContain('force-hover');
  });

  it('leaves the base declarations in the inline style', () => {
    // Mounting in base state is only useful if the base VALUE is what
    // renders — otherwise there is nothing for the transition to start
    // from. Black is the base bucket; red is the hover bucket.
    const html = renderToStaticMarkup(<ComponentRenderer node={node(hoverComp)} />);
    expect(html).toMatch(/background-color:\s*rgba\(0,\s*0,\s*0,\s*1\)/);
    expect(html).not.toMatch(/background-color:\s*rgba\(255,\s*0,\s*0,\s*1\)/);
  });
});
