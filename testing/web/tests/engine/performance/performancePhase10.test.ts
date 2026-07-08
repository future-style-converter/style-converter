// performancePhase10.test.ts — Phase-10 long-tail smoke tests.
import { describe, it, expect } from 'vitest';
import { applyPerformancePhase10 } from '../../../src/style/engine/performance/_dispatch';

describe('applyPerformancePhase10', () => {
  it('empty input → empty output', () => {
    expect(applyPerformancePhase10([])).toEqual({});
  });
  it('Contain → strict', () => {
    expect(applyPerformancePhase10([{ type: 'Contain', data: 'STRICT' }]))
      .toEqual({ contain: 'strict' });
  });
});
