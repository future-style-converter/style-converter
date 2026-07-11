// RuleBuilder.test.ts — pins for the stylesheet path (spec 06 §2-§4, §6):
// exact rule text, force-class twinning, specificity layering, media
// wrapping, runtime-v1 grammar gating, light-dark root opt-in, and the
// SSR-safe string export. These strings are CONTRACT — the JSDOM tests in
// apps/web-harness assert the same rules actually cascade.
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import type { IRComponent } from '../../src/core/ir/IRModels';
import {
  buildRules,
  buildRuleList,
  buildStylesheet,
  componentClassName,
  forceClassName,
  isRuntimeV1Condition,
  mountRules,
  RUNTIME_V1_CONDITIONS,
} from '../../src/core/renderer/RuleBuilder';
import { parseMediaQueryV1, evaluateMediaQueryV1 } from '../../src/core/renderer/MediaQueryV1';
import { cssPropertyName, declarationsToCss } from '../../src/core/renderer/CssText';
import { reset, getReport } from '../../src/engine/PropertyTracker';

// Silence the PropertyTracker's log-once console.warn — the REPORT is the
// assertion surface here, not the console side-effect.
beforeEach(() => { reset(); vi.spyOn(console, 'warn').mockImplementation(() => {}); });
afterEach(() => { vi.restoreAllMocks(); });

// srgb color payload helper — the spec-02 pre-resolved shape.
const srgb = (r: number, g: number, b: number) => ({ srgb: { r, g, b } });
// Minimal component factory; buckets supplied per test.
const comp = (over: Partial<IRComponent>): IRComponent =>
  ({ id: 'c1', name: 'C1', properties: [], ...over });

describe('componentClassName / forceClassName', () => {
  it('prefixes sc- and keeps ident-safe ids verbatim', () => {
    expect(componentClassName('button-001')).toBe('sc-button-001');
  });
  it('folds non-ident characters to underscore (no escaping needed)', () => {
    expect(componentClassName('a.b c:d')).toBe('sc-a_b_c_d');
  });
  it('force class is force-<condition>', () => {
    expect(forceClassName('hover')).toBe('force-hover');
  });
  it('runtime-v1 condition set is exactly the spec-06 §2 five', () => {
    expect([...RUNTIME_V1_CONDITIONS]).toEqual(['hover', 'active', 'focus', 'disabled', 'checked']);
    expect(isRuntimeV1Condition('hover')).toBe(true);
    expect(isRuntimeV1Condition('focus-visible')).toBe(false); // reserved, inert at v1
  });
});

describe('buildRules — selector buckets', () => {
  it('emits the pseudo-class AND its force-class twin on ONE rule, !important', () => {
    const c = comp({
      selectors: [{ condition: 'hover', properties: [{ type: 'BackgroundColor', data: srgb(1, 0, 0) }] }],
    });
    // Twin selectors share the declarations AND the (0,2,0) specificity —
    // forcing hover resolves byte-identically to a real hover (spec 06 §6).
    expect(buildRules(c)).toEqual([
      '.sc-c1:hover, .sc-c1.force-hover { background-color: rgba(255, 0, 0, 1) !important }',
    ]);
  });

  it('emits selector rules in wire array order (last writer wins on tie)', () => {
    const c = comp({
      selectors: [
        { condition: 'hover', properties: [{ type: 'BackgroundColor', data: srgb(1, 0, 0) }] },
        { condition: 'active', properties: [{ type: 'BackgroundColor', data: srgb(0, 1, 0) }] },
      ],
    });
    const rules = buildRules(c);
    expect(rules).toHaveLength(2);
    expect(rules[0]).toContain(':hover');
    expect(rules[1]).toContain(':active'); // later in doc = later bucket = wins ties
  });

  it('tolerates a stray leading colon on the wire condition', () => {
    const c = comp({
      selectors: [{ condition: ':focus', properties: [{ type: 'BackgroundColor', data: srgb(0, 0, 1) }] }],
    });
    expect(buildRules(c)[0]).toContain('.sc-c1:focus, .sc-c1.force-focus');
  });

  it('skips + logs conditions outside runtime v1 (conservatively inactive)', () => {
    const c = comp({
      selectors: [
        { condition: 'focus-visible', properties: [{ type: 'BackgroundColor', data: srgb(1, 0, 0) }] },
        { condition: 'nth-child(2)', properties: [{ type: 'BackgroundColor', data: srgb(1, 0, 0) }] },
      ],
    });
    expect(buildRules(c)).toEqual([]); // never apply-by-guess
    expect(getReport().unhandled).toContain('SelectorCondition'); // PropertyTracker-logged
  });

  it('emits no rule when the bucket yields zero declarations', () => {
    const c = comp({ selectors: [{ condition: 'hover', properties: [] }] });
    expect(buildRules(c)).toEqual([]);
  });
});

describe('buildRules — media buckets', () => {
  it('wraps declarations in @media with the query text verbatim', () => {
    const c = comp({
      media: [{ query: '(min-width: 200px)', properties: [{ type: 'BackgroundColor', data: srgb(0, 0, 1) }] }],
    });
    expect(buildRules(c)).toEqual([
      '@media (min-width: 200px) { .sc-c1 { background-color: rgba(0, 0, 255, 1) !important } }',
    ]);
  });

  it('emits media rules BEFORE selector rules (state beats width-bucket recolor)', () => {
    const c = comp({
      selectors: [{ condition: 'hover', properties: [{ type: 'BackgroundColor', data: srgb(1, 0, 0) }] }],
      media: [{ query: '(max-width: 500px)', properties: [{ type: 'BackgroundColor', data: srgb(0, 1, 0) }] }],
    });
    const rules = buildRules(c);
    // Document order media-first + selector specificity (0,2,0) over the
    // media-wrapped bare class (0,1,0) = the spec-06 §3 layering model.
    expect(rules[0].startsWith('@media')).toBe(true);
    expect(rules[1]).toContain(':hover');
  });

  it('skips + logs queries outside the v1 grammar (conservatively inactive)', () => {
    const c = comp({
      media: [
        { query: '(orientation: landscape)', properties: [{ type: 'BackgroundColor', data: srgb(1, 0, 0) }] },
        { query: 'not (min-width: 100px)', properties: [{ type: 'BackgroundColor', data: srgb(1, 0, 0) }] },
        { query: '(200px <= width)', properties: [{ type: 'BackgroundColor', data: srgb(1, 0, 0) }] },
        { query: 'screen, print', properties: [{ type: 'BackgroundColor', data: srgb(1, 0, 0) }] },
      ],
    });
    expect(buildRules(c)).toEqual([]); // browser must NOT see what natives cannot evaluate
    expect(getReport().unhandled).toContain('MediaQuery');
  });

  it('accepts prefers-color-scheme and and-conjunctions', () => {
    const c = comp({
      media: [{
        query: '(min-width: 200px) and (prefers-color-scheme: dark)',
        properties: [{ type: 'Color', data: srgb(1, 1, 1) }],
      }],
    });
    expect(buildRules(c)[0]).toBe(
      '@media (min-width: 200px) and (prefers-color-scheme: dark) { .sc-c1 { color: rgba(255, 255, 255, 1) !important } }',
    );
  });
});

describe('buildRuleList / buildStylesheet — document level', () => {
  it('returns [] for a fully static document (baseline path)', () => {
    expect(buildRuleList([comp({}), comp({ id: 'c2' })])).toEqual([]);
    expect(buildStylesheet([comp({})])).toBe('');
  });

  it('prepends the color-scheme root opt-in when light-dark() is present', () => {
    const c = comp({
      properties: [{
        type: 'BackgroundColor',
        data: { original: { type: 'light-dark', lightColor: '#ecf0f1', darkColor: '#111827' } },
      }],
    });
    // Rule 0 is the opt-in — without it the dark arm of light-dark() is
    // unreachable (css-color-5: resolved against the USED color-scheme).
    expect(buildRuleList([c])[0]).toBe(':root { color-scheme: light dark }');
  });

  it('does NOT emit the opt-in for scheme-free documents', () => {
    const c = comp({
      selectors: [{ condition: 'hover', properties: [{ type: 'BackgroundColor', data: srgb(1, 0, 0) }] }],
    });
    expect(buildRuleList([c]).some((r) => r.includes('color-scheme'))).toBe(false);
  });

  it('joins rules with newlines for the SSR string export', () => {
    const c = comp({
      selectors: [
        { condition: 'hover', properties: [{ type: 'BackgroundColor', data: srgb(1, 0, 0) }] },
        { condition: 'active', properties: [{ type: 'BackgroundColor', data: srgb(0, 1, 0) }] },
      ],
    });
    expect(buildStylesheet([c]).split('\n')).toHaveLength(2);
  });
});

describe('runtime-v1 media grammar (parse + reference evaluator)', () => {
  it('parses the five media-width.json queries', () => {
    for (const q of ['(min-width: 200px)', '(min-width: 500px)', '(max-width: 500px)', '(max-width: 300px)', '(min-width: 300px)']) {
      expect(parseMediaQueryV1(q)).not.toBeNull();
    }
  });

  it('matches the DYNAMIC_CAPTURE.md §2 truth table at 390 px', () => {
    const env = { surfaceWidth: 390, darkMode: false };
    expect(evaluateMediaQueryV1('(min-width: 200px)', env)).toBe(true);
    expect(evaluateMediaQueryV1('(max-width: 500px)', env)).toBe(true);
    expect(evaluateMediaQueryV1('(min-width: 300px)', env)).toBe(true);
    expect(evaluateMediaQueryV1('(min-width: 500px)', env)).toBe(false);
    expect(evaluateMediaQueryV1('(max-width: 300px)', env)).toBe(false);
  });

  it('flips exactly the two 300px buckets at 250 px', () => {
    const env = { surfaceWidth: 250, darkMode: false };
    expect(evaluateMediaQueryV1('(max-width: 300px)', env)).toBe(true);  // flips ON
    expect(evaluateMediaQueryV1('(min-width: 300px)', env)).toBe(false); // flips OFF
    expect(evaluateMediaQueryV1('(min-width: 200px)', env)).toBe(true);  // keeps its answer
    expect(evaluateMediaQueryV1('(min-width: 500px)', env)).toBe(false); // keeps its answer
    expect(evaluateMediaQueryV1('(max-width: 500px)', env)).toBe(true);  // keeps its answer
  });

  it('width bounds are inclusive (mediaqueries-5 §4.2)', () => {
    expect(evaluateMediaQueryV1('(min-width: 390px)', { surfaceWidth: 390, darkMode: false })).toBe(true);
    expect(evaluateMediaQueryV1('(max-width: 390px)', { surfaceWidth: 390, darkMode: false })).toBe(true);
  });

  it('maps prefers-color-scheme to the dark-mode flag', () => {
    expect(evaluateMediaQueryV1('(prefers-color-scheme: dark)', { surfaceWidth: 390, darkMode: false })).toBe(false);
    expect(evaluateMediaQueryV1('(prefers-color-scheme: dark)', { surfaceWidth: 390, darkMode: true })).toBe(true);
    expect(evaluateMediaQueryV1('(prefers-color-scheme: light)', { surfaceWidth: 390, darkMode: false })).toBe(true);
  });

  it('evaluates conjunctions as AND', () => {
    const q = '(min-width: 200px) and (max-width: 500px)';
    expect(evaluateMediaQueryV1(q, { surfaceWidth: 390, darkMode: false })).toBe(true);
    expect(evaluateMediaQueryV1(q, { surfaceWidth: 600, darkMode: false })).toBe(false);
  });

  it('returns null for everything outside the grammar', () => {
    const env = { surfaceWidth: 390, darkMode: false };
    for (const q of ['(orientation: landscape)', 'only screen and (min-width: 1px)',
      '(min-width: 20em)', 'screen, print', '(200px <= width)', '']) {
      expect(evaluateMediaQueryV1(q, env)).toBeNull();
    }
  });
});

describe('CssText serialization', () => {
  it('kebab-cases camelCase and preserves custom properties verbatim', () => {
    expect(cssPropertyName('backgroundColor')).toBe('background-color');
    expect(cssPropertyName('--Brand-Color')).toBe('--Brand-Color'); // case-SENSITIVE
    expect(cssPropertyName('WebkitMaskImage')).toBe('-webkit-mask-image');
    expect(cssPropertyName('msOverflowStyle')).toBe('-ms-overflow-style');
  });

  it('appends px to plain numbers but not to unitless properties', () => {
    expect(declarationsToCss({ width: 100, opacity: 0.5, zIndex: 3 }, false))
      .toBe('width: 100px; opacity: 0.5; z-index: 3');
  });

  it('adds !important to every declaration when asked', () => {
    expect(declarationsToCss({ color: 'red' }, true)).toBe('color: red !important');
  });

  it('skips undefined values entirely', () => {
    expect(declarationsToCss({ color: undefined, width: '10px' }, false)).toBe('width: 10px');
  });
});

describe('mountRules — SSR safety', () => {
  it('is a silent no-op without a document (node env)', () => {
    // This suite runs in the node environment: no global `document`.
    // The DOM behaviour (insertRule, idempotence, force-class cascade)
    // is covered by the JSDOM suite in apps/web-harness.
    expect(() => mountRules(['.sc-x:hover { color: red !important }'])).not.toThrow();
  });
});
