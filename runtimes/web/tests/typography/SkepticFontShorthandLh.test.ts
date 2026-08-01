// SkepticFontShorthandLh.test.ts — wave 22, lane font-shorthand-lh (SKEPTIC).
//
// Adversarial probes on the WEB half, driven through the FULL `buildStyles`
// pipeline rather than the extractor in isolation — the lane's claim is that
// web needed no code change, so the thing to verify is what the RENDERER
// actually receives for the live converter wire, including the invariant that
// an absent LineHeight still emits no `lineHeight` key at all (the whole WPT
// corpus and the 327 committed baselines ride that branch: the harness stage's
// inherited `body.wpt-mode { line-height: 1.25 }` must keep applying).
//
// Every payload below was captured from a real `:converter:run` on a
// purpose-built `font:` shorthand matrix fixture and on
// fixtures/wpt/css-text-decor/text-decoration-dotted-001.json.

import { describe, it, expect } from 'vitest';

import { buildStyles } from '../../src/core/renderer/StyleBuilder';

// `font: 92px Arial, sans-serif` — the live dotted-001 component.
const DOTTED_001 = [
  { type: 'FontSize', data: { px: 92.0, original: { type: 'length', px: 92.0 } } },
  { type: 'FontFamily', data: ['Arial', 'sans-serif'] },
  { type: 'LineHeight', data: { multiplier: 1.2, original: 'normal' } },
] as any;

describe('skeptic — web emission for the `font` shorthand line-height reset', () => {
  it('the live dotted-001 wire reaches the DOM as the CSS keyword', () => {
    const s = buildStyles(DOTTED_001);
    // The keyword, not 1.2: the browser then resolves Arial's own metrics
    // (~1.1499em ≈ 105.8px at 92px), which is what the Chromium ref does.
    expect(s.lineHeight).toBe('normal');
  });

  it('the sibling <p> with NO font declaration emits no lineHeight key at all', () => {
    // THE protected invariant. `in` (not `=== undefined`) so an explicit
    // `lineHeight: undefined` key — which React would still not serialise but
    // which would break the harness's `styles.lineHeight !== undefined` gate
    // in apps/web-harness/src/sdui/ComponentRenderer.tsx — also fails here.
    const s = buildStyles([] as any);
    expect('lineHeight' in s).toBe(false);
  });

  it('`font: 16px/2 Georgia` keeps the authored number, not the keyword', () => {
    const s = buildStyles([
      { type: 'FontSize', data: { px: 16.0, original: { type: 'length', px: 16.0 } } },
      { type: 'LineHeight', data: { multiplier: 2.0, original: { type: 'number', value: 2.0 } } },
      { type: 'FontFamily', data: ['Georgia'] },
    ] as any);
    expect(s.lineHeight).toBe(2);
  });

  it('`font: inherit` forwards the global keyword, never the reset', () => {
    // Live wire: the global path rides original:{type:'keyword'}, an OBJECT,
    // so it can never be mistaken for the bare-string `normal` discriminator.
    const s = buildStyles([
      { type: 'LineHeight', data: { original: { type: 'keyword', keyword: 'inherit' } } },
    ] as any);
    expect(s.lineHeight).not.toBe('normal');
  });

  it('`font: 16px/24px Arial` keeps the nested length wire', () => {
    const s = buildStyles([
      { type: 'LineHeight', data: { original: { type: 'length', px: 24.0 } } },
    ] as any);
    expect(s.lineHeight).toBe('24px');
  });

  it('an authored `line-height: 1.2` does not collapse into the keyword', () => {
    // Same multiplier as the reset's legacy compatibility value — the two are
    // told apart by `original` only.
    const s = buildStyles([
      { type: 'LineHeight', data: { multiplier: 1.2, original: { type: 'number', value: 1.2 } } },
    ] as any);
    expect(s.lineHeight).toBe(1.2);
  });

  it('cascade: the LAST declaration wins in both directions', () => {
    const normal = { type: 'LineHeight', data: { multiplier: 1.2, original: 'normal' } };
    const two = { type: 'LineHeight', data: { multiplier: 2, original: { type: 'number', value: 2 } } };
    expect(buildStyles([two, normal] as any).lineHeight).toBe('normal');
    expect(buildStyles([normal, two] as any).lineHeight).toBe(2);
  });
});
