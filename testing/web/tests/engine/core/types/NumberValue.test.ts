// Tests for NumberExtractors — one per numeric envelope in examples/primitives/numbers.json.
import { describe, it, expect } from 'vitest';
import { NumberExtractors } from '../../../../src/style/engine/core/types/NumberValue';

describe('NumberExtractors.opacity', () => {
  it('reads alpha key', () => {
    expect(NumberExtractors.opacity({ alpha: 0.5 })).toEqual({ value: 0.5 });
  });
  it('accepts bare number', () => {
    expect(NumberExtractors.opacity(0.25)).toEqual({ value: 0.25 });
  });
  it('returns null when alpha missing', () => {
    expect(NumberExtractors.opacity({ value: 0.5 })).toBeNull();
  });
});

describe('NumberExtractors.lineHeightMultiplier', () => {
  it('reads multiplier key', () => {
    expect(NumberExtractors.lineHeightMultiplier({ multiplier: 1.5 })).toEqual({ value: 1.5 });
  });
  it('accepts bare number (forward-compat)', () => {
    expect(NumberExtractors.lineHeightMultiplier(2)).toEqual({ value: 2 });
  });
});

describe('NumberExtractors.flexGrow', () => {
  it('reads normalizedValue key', () => {
    expect(NumberExtractors.flexGrow({ normalizedValue: 1.0 })).toEqual({ value: 1.0 });
  });
  it('accepts bare number', () => {
    expect(NumberExtractors.flexGrow(2)).toEqual({ value: 2 });
  });
});

describe('NumberExtractors.zIndex', () => {
  it('reads value key and rounds', () => {
    expect(NumberExtractors.zIndex({ value: 10 })).toEqual({ value: 10 });
  });
  it('rounds bare floats to int', () => {
    expect(NumberExtractors.zIndex(9.7)).toEqual({ value: 10 });
  });
});

describe('NumberExtractors.fontWeight', () => {
  it('accepts bare integer (documented fixture shape)', () => {
    expect(NumberExtractors.fontWeight(700)).toEqual({ value: 700 });
  });
  it('accepts { weight:N } defensively', () => {
    expect(NumberExtractors.fontWeight({ weight: 400 })).toEqual({ value: 400 });
  });
});

describe('NumberExtractors.fontSize', () => {
  it('delegates to extractLength for px form', () => {
    expect(NumberExtractors.fontSize({ px: 16 })).toEqual({ kind: 'exact', px: 16 });
  });
  it('delegates to extractLength for rem form', () => {
    const out = NumberExtractors.fontSize({ original: { v: 2, u: 'REM' } });
    expect(out).toEqual({ kind: 'relative', value: 2, unit: 'rem' });
  });
});
