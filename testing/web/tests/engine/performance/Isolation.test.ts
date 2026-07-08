// Isolation — 'AUTO' / 'ISOLATE' enum.
import { describe, it, expect } from 'vitest';
import { extractIsolation } from '../../../src/style/engine/performance/IsolationExtractor';
import { applyIsolation } from '../../../src/style/engine/performance/IsolationApplier';

const p = (type: string, data: unknown) => ({ type, data });

describe('Isolation', () => {
  it('lowercases AUTO -> auto', () => {
    expect(applyIsolation(extractIsolation([p('Isolation', 'AUTO')])).isolation).toBe('auto');
  });

  it('lowercases ISOLATE -> isolate', () => {
    expect(applyIsolation(extractIsolation([p('Isolation', 'ISOLATE')])).isolation).toBe('isolate');
  });

  it('rejects unknown values', () => {
    expect(applyIsolation(extractIsolation([p('Isolation', 'NOPE')]))).toEqual({});
  });

  it('emits nothing when unset', () => {
    expect(applyIsolation(extractIsolation([]))).toEqual({});
  });
});
