// DynamicValues.test.ts — wave-6 pins for verbatim var()/calc() pass-through.
//
// Wire shapes come straight from converter output for the tokens fixtures
// (fixtures/fidelity/tokens/*) and the v2 goldens
// (schema/conformance/fixtures/v2/{variables-basic,calc-mixed}.json):
//   Color / BackgroundColor      → { original: 'var(--x[, fb])' }
//   Width / Height               → { type:'expression', expr:'var(--x)' }
//   Padding* / Margin*           → { expr:'var(--x)' }
//   FontSize                     → { original:{ type:'expression', expr } }
//   border-radius via shorthand  → Generic { propertyName, rawValue }
import { describe, it, expect } from 'vitest';
import { extractColor, toCssColor } from '../../src/engine/core/types/ColorValue';
import { extractLength, toCssLength, isWholeVarExpression } from '../../src/engine/core/types/LengthValue';
import { extractTextColor } from '../../src/engine/color/ColorExtractor';
import { applyTextColor } from '../../src/engine/color/ColorApplier';
import { extractBackgroundColor } from '../../src/engine/color/BackgroundColorExtractor';
import { applyBackgroundColor } from '../../src/engine/color/BackgroundColorApplier';
import { buildStyles, buildVariables } from '../../src/core/renderer/StyleBuilder';

const p = (type: string, data: unknown) => ({ type, data });

describe('isWholeVarExpression', () => {
  it('accepts simple and nested whole-value var()', () => {
    expect(isWholeVarExpression('var(--size)')).toBe(true);
    expect(isWholeVarExpression('var(--a, var(--b, 4px))')).toBe(true);
    expect(isWholeVarExpression('var(--nope, #e67e22)')).toBe(true);
  });
  it('rejects partial expressions and non-var strings', () => {
    expect(isWholeVarExpression('var(--u) * 10')).toBe(false); // closes early
    expect(isWholeVarExpression('calc(var(--u) * 10)')).toBe(false);
    expect(isWholeVarExpression('10px')).toBe(false);
    expect(isWholeVarExpression('var(--broken')).toBe(false);  // unbalanced
  });
});

describe('ColorValue — var() references survive (classifyDynamic-null drop fixed)', () => {
  it('classifies {original: "var(--x)"} as dynamic var with verbatim raw', () => {
    const v = extractColor({ original: 'var(--tile-a)' });
    expect(v).toEqual({ kind: 'dynamic', dynamicKind: 'var', raw: 'var(--tile-a)' });
  });
  it('preserves custom-property name case exactly (css-variables-1 §2)', () => {
    const v = extractColor({ original: 'var(--Brand-Fg)' });
    expect(v.kind).toBe('dynamic');
    expect(toCssColor(v)).toBe('var(--Brand-Fg)'); // NOT lowercased
  });
  it('preserves fallbacks verbatim including nested var()', () => {
    expect(toCssColor(extractColor({ original: 'var(--nope, #e67e22)' })))
      .toBe('var(--nope, #e67e22)');
    expect(toCssColor(extractColor({ original: 'var(--top, var(--mid, #ffffff))' })))
      .toBe('var(--top, var(--mid, #ffffff))');
  });
  it('accepts a defensive bare var() string payload', () => {
    expect(extractColor('var(--x)').kind).toBe('dynamic');
  });
  it('still treats non-var strings without srgb as unknown', () => {
    expect(extractColor({ original: 'salmon' }).kind).toBe('unknown');
  });
});

describe('Color / BackgroundColor extract→apply — var() emitted verbatim', () => {
  it('color: var(--ink) flows through to the color declaration', () => {
    const cfg = extractTextColor([p('Color', { original: 'var(--ink)' })]);
    expect(applyTextColor(cfg)).toEqual({ color: 'var(--ink)' });
  });
  it('background-color: var(--nope, #e67e22) keeps its fallback', () => {
    const cfg = extractBackgroundColor([p('BackgroundColor', { original: 'var(--nope, #e67e22)' })]);
    expect(applyBackgroundColor(cfg)).toEqual({ backgroundColor: 'var(--nope, #e67e22)' });
  });
});

describe('LengthValue — verbatim var(), calc(), em and % emission', () => {
  it('whole-value var() emits BARE (no calc() wrapper)', () => {
    expect(toCssLength(extractLength({ expr: 'var(--size)' }))).toBe('var(--size)');
    expect(toCssLength(extractLength({ type: 'expression', expr: 'var(--size)' }))).toBe('var(--size)');
  });
  it('nested-fallback var() emits verbatim', () => {
    expect(toCssLength(extractLength({ expr: 'var(--a, var(--b, 4px))' })))
      .toBe('var(--a, var(--b, 4px))');
  });
  it('calc() round-trips byte-for-byte', () => {
    expect(toCssLength(extractLength({ type: 'expression', expr: 'calc(100% - 24px)' })))
      .toBe('calc(100% - 24px)');
    expect(toCssLength(extractLength({ expr: 'calc(2em + 4px)' }))).toBe('calc(2em + 4px)');
    // var() INSIDE calc keeps the calc wrapper — it is not whole-value.
    expect(toCssLength(extractLength({ expr: 'calc(var(--u) * 10)' })))
      .toBe('calc(var(--u) * 10)');
    // Nested calc — the outer wrapper is stripped+rebuilt, inner untouched.
    expect(toCssLength(extractLength({ expr: 'calc(calc(100% - 10px) / 2)' })))
      .toBe('calc(calc(100% - 10px) / 2)');
  });
  it('em and % relative lengths emit their unit verbatim', () => {
    expect(toCssLength(extractLength({ type: 'length', original: { v: 2, u: 'EM' } }))).toBe('2em');
    expect(toCssLength(extractLength({ type: 'percentage', value: 50 }))).toBe('50%');
  });
});

describe('buildStyles — end-to-end token-fixture shapes', () => {
  it('emits width/padding/background var() declarations verbatim', () => {
    const styles = buildStyles([
      p('Width', { type: 'expression', expr: 'var(--size)' }),
      p('PaddingTop', { expr: 'var(--pad)' }),
      p('BackgroundColor', { original: 'var(--tile-a)' }),
      p('FontSize', { original: { type: 'expression', expr: 'var(--type)' } }),
    ]);
    expect(styles.width).toBe('var(--size)');
    expect(styles.paddingTop).toBe('var(--pad)');
    expect(styles.backgroundColor).toBe('var(--tile-a)');
    expect(styles.fontSize).toBe('var(--type)');
  });
  it('passes dynamic Generic declarations through verbatim (border-radius: var())', () => {
    const styles = buildStyles([
      p('Generic', { propertyName: 'border-top-left-radius', rawValue: 'var(--pad)', _unmapped: true }),
      p('Generic', { propertyName: 'border-bottom-right-radius', rawValue: 'calc(var(--pad) * 2)', _unmapped: true }),
    ]);
    expect(styles.borderTopLeftRadius).toBe('var(--pad)');
    expect(styles.borderBottomRightRadius).toBe('calc(var(--pad) * 2)');
  });
  it('does NOT pass static Generic values through (honest gap, logged)', () => {
    const styles = buildStyles([
      p('Generic', { propertyName: 'border-top-left-radius', rawValue: 'squircle', _unmapped: true }),
    ]);
    expect(styles.borderTopLeftRadius).toBeUndefined();
  });
});

describe('buildVariables — custom-property definitions → inline-style keys', () => {
  it('emits --name keys verbatim, case-sensitive, empty string legal', () => {
    expect(buildVariables({ '--tile-a': '#e74c3c', '--Brand-Fg': '#ffffff', '--empty': '' }))
      .toEqual({ '--tile-a': '#e74c3c', '--Brand-Fg': '#ffffff', '--empty': '' });
  });
  it('returns {} for undefined (omit-when-empty wire key)', () => {
    expect(buildVariables(undefined)).toEqual({});
  });
  it('drops the reserved bare -- name (css-variables-1 §2)', () => {
    expect(buildVariables({ '--': 'nope', '--ok': '1' })).toEqual({ '--ok': '1' });
  });
});
