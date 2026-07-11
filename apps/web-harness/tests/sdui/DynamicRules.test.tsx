// @vitest-environment jsdom
// @vitest-environment-options { "url": "http://localhost:3000/?mode=capture&forceState=hover" }
//
// DynamicRules.test.tsx — JSDOM end-to-end for the stylesheet path
// (spec 06 / docs/DYNAMIC_CAPTURE.md §1): the harness renderer stamps the
// per-component class + force class, mountRules injects the RuleBuilder
// rules via CSSOM, and the FORCED state actually cascades — the hover
// declarations beat the inline base styles through the force-class twin.
//
// The environment URL above carries `?forceState=hover`, so the
// ComponentRenderer module constant resolves 'hover' at import time —
// exactly how a forced capture run boots (capture-screenshots.mjs →
// &forceState=hover → CaptureGallery stamp + renderer force classes).
//
// JSDOM evaluates selector cascade (incl. !important-over-inline) but NOT
// media queries; media buckets are asserted structurally (CSSOM shape)
// with the runtime's reference evaluator supplying the 390/250 semantics
// — the real-browser behaviour is gated by the visual pipeline.
import { describe, it, expect, afterEach } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { ComponentRenderer } from '../../src/sdui/ComponentRenderer';
import type { ComposedNode } from '../../src/sdui/Composer';
import type { IRComponent } from '@style-converter/web/core/ir/IRModels';
import {
  buildRuleList,
  mountRules,
  MANAGED_STYLE_ID,
} from '@style-converter/web/core/renderer/RuleBuilder';
import { evaluateMediaQueryV1 } from '@style-converter/web/core/renderer/MediaQueryV1';

// srgb payload helper (spec-02 pre-resolved color shape).
const srgb = (r: number, g: number, b: number) => ({ srgb: { r, g, b } });
// Leaf ComposedNode factory — the renderer's input shape.
const node = (component: IRComponent): ComposedNode => ({ component, children: [] });

// A DS_HoverSwap-alike: dark base, red hover bucket (max pixel contrast).
const hoverComp: IRComponent = {
  id: 'ds-hover',
  name: 'DS_HoverSwap',
  properties: [{ type: 'BackgroundColor', data: srgb(0, 0, 0) }],
  selectors: [{ condition: 'hover', properties: [{ type: 'BackgroundColor', data: srgb(1, 0, 0) }] }],
};

// Reset the managed <style> element between tests — mountRules is
// idempotent per document, and tests must not leak rules into each other.
afterEach(() => { document.getElementById(MANAGED_STYLE_ID)?.remove(); document.body.innerHTML = ''; });

describe('ComponentRenderer class stamping (forced run)', () => {
  it('applies sc-<id> plus the force-<state> class from the URL', () => {
    const html = renderToStaticMarkup(<ComponentRenderer node={node(hoverComp)} />);
    // Both classes on the component element: the rule target AND the
    // forced-state activation (the URL above forces hover).
    expect(html).toContain('class="sc-ds-hover force-hover"');
  });
});

describe('forced hover cascades over the inline base', () => {
  it('getComputedStyle resolves the hover bucket color via the force class', () => {
    // Render the component the way the harness does (inline base styles
    // + classes), inject into the JSDOM document, mount the stylesheet.
    document.body.innerHTML = renderToStaticMarkup(<ComponentRenderer node={node(hoverComp)} />);
    mountRules(buildRuleList([hoverComp]), document);
    const el = document.querySelector('[data-component-id="ds-hover"]') as HTMLElement;
    // Sanity: the inline BASE background is present on the element
    // (JSDOM's CSSOM normalises opaque rgba() to rgb()).
    expect(el.style.backgroundColor).toBe('rgb(0, 0, 0)');
    // The force-hover twin (same rule as :hover, !important) must win the
    // cascade against the normal inline declaration (css-cascade-5 §6.2).
    expect(getComputedStyle(el).backgroundColor).toBe('rgb(255, 0, 0)');
  });

  it('without the force class the base survives (rule is conditional)', () => {
    document.body.innerHTML = '<div class="sc-ds-hover" style="background-color: rgb(0, 0, 0)"></div>';
    mountRules(buildRuleList([hoverComp]), document);
    const el = document.querySelector('.sc-ds-hover') as HTMLElement;
    // No :hover, no force class → the hover bucket stays dormant.
    expect(getComputedStyle(el).backgroundColor).toBe('rgb(0, 0, 0)');
  });
});

describe('media buckets mount as @media CSSOM rules', () => {
  // MW_MinNarrowOn-alike: matches at 390, still matches at 250.
  const mediaComp: IRComponent = {
    id: 'mw-min',
    name: 'MW_MinNarrowOn',
    properties: [{ type: 'BackgroundColor', data: srgb(0, 0, 0) }],
    media: [
      { query: '(min-width: 200px)', properties: [{ type: 'BackgroundColor', data: srgb(0, 0, 1) }] },
      { query: '(min-width: 300px)', properties: [{ type: 'BackgroundColor', data: srgb(0, 1, 0) }] },
    ],
  };

  it('mounts one CSSMediaRule per bucket, query text verbatim, order preserved', () => {
    mountRules(buildRuleList([mediaComp]), document);
    const sheet = (document.getElementById(MANAGED_STYLE_ID) as HTMLStyleElement).sheet!;
    expect(sheet.cssRules).toHaveLength(2);
    // Array order survives to CSSOM — the §3 within-layer tiebreaker.
    expect(sheet.cssRules[0].cssText).toContain('@media (min-width: 200px)');
    expect(sheet.cssRules[1].cssText).toContain('@media (min-width: 300px)');
    // Inner rule targets the component class with !important declarations.
    expect(sheet.cssRules[0].cssText).toContain('.sc-mw-min');
    expect(sheet.cssRules[0].cssText).toContain('!important');
  });

  it('the reference evaluator answers the two capture widths correctly', () => {
    // JSDOM cannot evaluate @media in its cascade; the browser does that
    // natively against the capture viewport. The runtime evaluator is the
    // normative tie-breaker (spec 06 §4) — pin the two-width recipe here.
    expect(evaluateMediaQueryV1('(min-width: 200px)', { surfaceWidth: 390, darkMode: false })).toBe(true);
    expect(evaluateMediaQueryV1('(min-width: 300px)', { surfaceWidth: 390, darkMode: false })).toBe(true);
    expect(evaluateMediaQueryV1('(min-width: 200px)', { surfaceWidth: 250, darkMode: false })).toBe(true);
    expect(evaluateMediaQueryV1('(min-width: 300px)', { surfaceWidth: 250, darkMode: false })).toBe(false);
  });
});

describe('dynamic-sizing floor carve-out', () => {
  // MW_LayoutFlip-alike: base 200px wide, a media bucket narrows to 120px.
  const base = [
    { type: 'Width', data: { type: 'length', px: 200 } },
    { type: 'Height', data: { type: 'length', px: 48 } },
  ];

  it('drops the width-derived min floor when a bucket redeclares sizing', () => {
    const c: IRComponent = {
      id: 'mw-flip', name: 'MW_LayoutFlip', properties: base,
      media: [{ query: '(max-width: 300px)', properties: [{ type: 'Width', data: { type: 'length', px: 120 } }] }],
    };
    const html = renderToStaticMarkup(<ComponentRenderer node={node(c)} />);
    // min-width must be 0, otherwise the stale base-width floor re-clamps
    // the bucket's `width: 120px !important` back to 200 (used width =
    // max(min-width, width)) — the MW_LayoutFlip 250 px regression.
    expect(html).toContain('min-width:0');
    expect(html).not.toContain('min-width:200px');
  });

  it('keeps the base-size floor for bucket-free components (baseline path)', () => {
    const c: IRComponent = { id: 'static', name: 'Static', properties: base };
    const html = renderToStaticMarkup(<ComponentRenderer node={node(c)} />);
    // The 327-pair baseline contract: floor == declared size, unchanged.
    expect(html).toContain('min-width:200px');
  });
});

describe('mountRules lifecycle', () => {
  const rules = ['.sc-a:hover, .sc-a.force-hover { color: rgba(255, 0, 0, 1) !important }'];

  it('is idempotent: re-mounting identical rules is a no-op', () => {
    mountRules(rules, document);
    mountRules(rules, document);
    const els = document.querySelectorAll(`#${MANAGED_STYLE_ID}`);
    expect(els).toHaveLength(1); // ONE managed element per document
    expect((els[0] as HTMLStyleElement).sheet!.cssRules).toHaveLength(1); // no duplicate rules
  });

  it('replaces rules on a changed render (hot reload path)', () => {
    mountRules(rules, document);
    mountRules(['.sc-b:active, .sc-b.force-active { color: rgba(0, 255, 0, 1) !important }'], document);
    const sheet = (document.getElementById(MANAGED_STYLE_ID) as HTMLStyleElement).sheet!;
    expect(sheet.cssRules).toHaveLength(1); // old rule cleared, not appended-to
    expect(sheet.cssRules[0].cssText).toContain('.sc-b'); // the NEW rule
  });

  it('mounts nothing for an empty rule list (baseline documents)', () => {
    mountRules([], document);
    expect(document.getElementById(MANAGED_STYLE_ID)).toBeNull(); // zero DOM footprint
  });
});
