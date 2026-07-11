// RuleBuilder.test.ts — pins for the stylesheet path (spec 06 §2-§4, §6):
// exact rule text, force-class twinning, specificity layering, media
// wrapping, runtime-v1 grammar gating, light-dark root opt-in, and the
// SSR-safe string export. These strings are CONTRACT — the JSDOM tests in
// apps/web-harness assert the same rules actually cascade.
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import type { IRComponent, IRKeyframes } from '../../src/core/ir/IRModels';
import {
  buildRules,
  buildRuleList,
  buildStylesheet,
  buildKeyframeRules,
  componentClassName,
  forceClassName,
  isRuntimeV1Condition,
  mountRules,
  RUNTIME_V1_CONDITIONS,
} from '../../src/core/renderer/RuleBuilder';
import { buildStyles } from '../../src/core/renderer/StyleBuilder';
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

describe('buildKeyframeRules — @keyframes emission (spec 07 §1.2)', () => {
  // Payload shapes lifted verbatim from the conformance golden
  // schema/conformance/fixtures/v2/keyframes.json — the bytes the Kotlin
  // converter actually puts on the wire.
  const fade: IRKeyframes = {
    fade: [
      { offset: 0, properties: [{ type: 'Opacity', data: { alpha: 0, original: { type: 'number', value: 0 } } }] },
      { offset: 1, properties: [{ type: 'Opacity', data: { alpha: 1, original: { type: 'number', value: 1 } } }] },
    ],
  };

  it('emits the exact rule text — percent selectors, engine declarations, NO !important', () => {
    // !important inside keyframes is invalid CSS (css-animations-1 §4.1);
    // keyframe values win via the animation origin, not the cascade.
    expect(buildKeyframeRules(fade)).toEqual([
      '@keyframes fade { 0% { opacity: 0 } 100% { opacity: 1 } }',
    ]);
  });

  it('serializes tier payloads through the SAME appliers as base styles', () => {
    // slide-shift stop shapes from the golden: transform function list,
    // sRGB color (authored text reconstructed as rgba by the applier),
    // and a px length — buildStyles is the single serialization engine,
    // so keyframe values can never disagree with inline base values.
    const rules = buildKeyframeRules({
      'slide-shift': [
        {
          offset: 0.5,
          properties: [
            { type: 'Transform', data: { type: 'functions', list: [{ fn: 'translateX', x: { px: 60 } }] } },
            { type: 'BackgroundColor', data: { srgb: { r: 1, g: 0, b: 0 }, original: '#ff0000' } },
            { type: 'Width', data: { type: 'length', px: 140 } },
          ],
        },
      ],
    });
    expect(rules).toHaveLength(1);
    expect(rules[0]).toContain('@keyframes slide-shift {');
    expect(rules[0]).toContain('50% {');
    expect(rules[0]).toContain('transform: translateX(60px)');
    expect(rules[0]).toContain('background-color: rgba(255, 0, 0, 1)');
    expect(rules[0]).toContain('width: 140px');
    expect(rules[0]).not.toContain('!important');
  });

  it('keeps wire stop order (pre-sorted by the converter) and trims percent zeros', () => {
    const rules = buildKeyframeRules({
      thirds: [
        { offset: 0, properties: [{ type: 'Opacity', data: { alpha: 0 } }] },
        { offset: 1 / 3, properties: [{ type: 'Opacity', data: { alpha: 0.4 } }] },
        { offset: 0.333, properties: [{ type: 'Opacity', data: { alpha: 0.5 } }] },
        { offset: 1, properties: [{ type: 'Opacity', data: { alpha: 1 } }] },
      ],
    });
    // 1/3 → toFixed(4) float bound; 0.333 → no trailing-zero padding; and
    // the emitted order is the array order (readers MUST NOT reorder).
    const idx = (s: string) => rules[0].indexOf(s);
    expect(rules[0]).toContain('33.3333% {');
    expect(rules[0]).toContain('33.3% {');
    expect(idx('0% {')).toBeLessThan(idx('33.3333% {'));
    expect(idx('33.3333% {')).toBeLessThan(idx('33.3% {'));
    expect(idx('33.3% {')).toBeLessThan(idx('100% {'));
  });

  it('emits one rule per named set in map order', () => {
    const rules = buildKeyframeRules({
      ...fade,
      pulse: [{ offset: 1, properties: [{ type: 'Opacity', data: { alpha: 0.5 } }] }],
    });
    expect(rules).toHaveLength(2);
    expect(rules[0]).toContain('@keyframes fade');
    expect(rules[1]).toContain('@keyframes pulse');
  });

  it('refuses + logs names that cannot head a @keyframes rule', () => {
    // A bad name would poison the whole rule text at insertRule time —
    // refuse-and-log (no-silent-fallthrough), never emit-and-hope. `none`
    // and the CSS-wide keywords are excluded <keyframes-name> values.
    const rules = buildKeyframeRules({
      'has space': [{ offset: 0, properties: [{ type: 'Opacity', data: { alpha: 0 } }] }],
      none: [{ offset: 0, properties: [{ type: 'Opacity', data: { alpha: 0 } }] }],
      ...fade,
    });
    expect(rules).toHaveLength(1); // only the legal name survives
    expect(rules[0]).toContain('@keyframes fade');
    expect(getReport().unhandled).toContain('KeyframesName');
  });

  it('builds nothing for an absent map (the committed-baseline path)', () => {
    expect(buildKeyframeRules(undefined)).toEqual([]);
    expect(buildKeyframeRules({})).toEqual([]);
  });

  it('buildRuleList emits keyframes BEFORE component rules', () => {
    const c = comp({
      properties: [{ type: 'AnimationName', data: [{ type: 'identifier', name: 'fade' }] }],
      selectors: [{ condition: 'hover', properties: [{ type: 'BackgroundColor', data: srgb(1, 0, 0) }] }],
    });
    const rules = buildRuleList([c], fade);
    expect(rules).toHaveLength(2);
    expect(rules[0].startsWith('@keyframes fade')).toBe(true); // document-scoped at-rule first
    expect(rules[1]).toContain(':hover');
    // A defined reference is NOT logged as dangling.
    expect(getReport().unhandled).not.toContain('KeyframesReference');
  });

  it('logs a dangling animation-name once as a defined no-op (spec 07 §1.3)', () => {
    const c = comp({
      properties: [{ type: 'AnimationName', data: [{ type: 'identifier', name: 'ghost-anim' }, { type: 'none' }] }],
    });
    // No keyframes at all: the reference dangles; rendering is unaffected
    // (no rules) and the miss lands under the dedicated tracker key so it
    // never shadows the AnimationName applier's own handled status.
    expect(buildRuleList([c])).toEqual([]);
    expect(getReport().unhandled).toContain('KeyframesReference');
    expect(getReport().unhandled).not.toContain('AnimationName');
  });

  it('scans selector/media buckets for dangling references too', () => {
    const c = comp({
      selectors: [{
        condition: 'hover',
        properties: [{ type: 'AnimationName', data: [{ type: 'identifier', name: 'bucket-ghost' }] }],
      }],
    });
    buildRuleList([c], fade);
    expect(getReport().unhandled).toContain('KeyframesReference');
  });
});

describe('transition emission — the state-flip motion recipe (spec 07 §4)', () => {
  it('buildStyles emits transition-property/duration/delay from typed IR', () => {
    // The inline base carries the transition declarations; the RuleBuilder
    // hover bucket carries the target values — together a forced-state
    // flip starts a real CSSTransition (the transitions.json fixture).
    const styles = buildStyles([
      { type: 'TransitionProperty', data: [{ type: 'property-name', name: 'background-color' }] },
      { type: 'TransitionDuration', data: [{ ms: 1000, original: { v: 1, u: 'S' } }] },
      { type: 'TransitionDelay', data: [{ ms: 250 }] },
    ]);
    expect(styles.transitionProperty).toBe('background-color');
    expect(styles.transitionDuration).toBe('1s');
    expect(styles.transitionDelay).toBe('250ms');
  });

  it('animation-name binds by ident: inline declaration matches the rule name', () => {
    // The BINDING contract: the applier emits the same ident the
    // @keyframes rule header carries — byte-equal, or nothing animates.
    const styles = buildStyles([
      { type: 'AnimationName', data: [{ type: 'identifier', name: 'fade' }] },
      { type: 'AnimationDuration', data: {
        type: 'app.irmodels.properties.animations.AnimationDurationProperty.AnimationDurationValue.Durations',
        durations: [{ ms: 1000, original: { v: 1, u: 'S' } }],
      } },
    ]);
    expect(styles.animationName).toBe('fade');
    expect(styles.animationDuration).toBe('1s');
    const rules = buildKeyframeRules({
      fade: [{ offset: 0, properties: [{ type: 'Opacity', data: { alpha: 0 } }] }],
    });
    expect(rules[0].startsWith(`@keyframes ${styles.animationName} `)).toBe(true);
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
