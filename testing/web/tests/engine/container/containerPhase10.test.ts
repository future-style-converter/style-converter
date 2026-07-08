// containerPhase10.test.ts — Phase-10 long-tail smoke tests.
import { describe, it, expect } from 'vitest';
import { applyContainerPhase10 } from '../../../src/style/engine/container/_dispatch';

describe('applyContainerPhase10', () => {
  it('empty input → empty output', () => {
    expect(applyContainerPhase10([])).toEqual({});
  });
  it('ContainerType → inline-size', () => {
    expect(applyContainerPhase10([{ type: 'ContainerType', data: 'INLINE_SIZE' }]))
      .toEqual({ containerType: 'inline-size' });
  });
});
