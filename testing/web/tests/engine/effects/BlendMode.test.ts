// BlendMode — MixBlendMode (scalar) and BackgroundBlendMode (list).
import { describe, it, expect } from 'vitest';
import { extractBlendMode } from '../../../src/style/engine/effects/blend/BlendModeExtractor';
import { applyBlendMode } from '../../../src/style/engine/effects/blend/BlendModeApplier';

const p = (type: string, data: unknown) => ({ type, data });

describe('MixBlendMode', () => {
  it('lowercases simple enum', () => {
    const out = applyBlendMode(extractBlendMode([p('MixBlendMode', 'MULTIPLY')]));
    expect(out.mixBlendMode).toBe('multiply');
  });

  it('hyphenates snake_case', () => {
    const out = applyBlendMode(extractBlendMode([p('MixBlendMode', 'PLUS_LIGHTER')]));
    expect(out.mixBlendMode).toBe('plus-lighter');
  });

  it('handles NORMAL', () => {
    const out = applyBlendMode(extractBlendMode([p('MixBlendMode', 'NORMAL')]));
    expect(out.mixBlendMode).toBe('normal');
  });
});

describe('BackgroundBlendMode', () => {
  it('reads single-element list', () => {
    const out = applyBlendMode(extractBlendMode([p('BackgroundBlendMode', ['MULTIPLY'])]));
    expect(out.backgroundBlendMode).toBe('multiply');
  });

  it('joins multiple modes with comma', () => {
    const out = applyBlendMode(extractBlendMode([
      p('BackgroundBlendMode', ['MULTIPLY', 'SCREEN', 'HARD_LIGHT']),
    ]));
    expect(out.backgroundBlendMode).toBe('multiply, screen, hard-light');
  });
});

describe('combined', () => {
  it('emits both mix + background when both are set', () => {
    const out = applyBlendMode(extractBlendMode([
      p('MixBlendMode', 'NORMAL'),
      p('BackgroundBlendMode', ['MULTIPLY']),
    ]));
    expect(out.mixBlendMode).toBe('normal');
    expect(out.backgroundBlendMode).toBe('multiply');
  });
});
