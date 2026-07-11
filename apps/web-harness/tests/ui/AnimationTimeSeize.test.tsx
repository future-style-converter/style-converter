// @vitest-environment jsdom
// @vitest-environment-options { "url": "http://localhost:3000/?mode=capture&animationTime=0.5" }
//
// AnimationTimeSeize.test — determinism pins for the CAPTURE_ANIMATION_TIME
// contract (schema/spec/07-animations.md §5 / docs/DYNAMIC_CAPTURE.md §4).
// The environment URL carries `?animationTime=0.5`, so CaptureGallery's
// module constants resolve at import time exactly the way a seized capture
// run boots (capture-screenshots.mjs → &animationTime=0.5).
//
// Pinned here:
//   1. the verification marker: every canvas carries data-animation-time
//      (capture-screenshots.mjs HARD-FAILS when it's missing, so a seized
//      run can never silently degrade to a live capture);
//   2. the window.__seizeAnimations hook exists and freezes every WAAPI
//      Animation at t·1000 ms, pause-then-seek (the seek must HOLD);
//   3. determinism: re-seizing is idempotent — same t, same state, no
//      matter how many times the capture script re-invokes it.

import { describe, it, expect, afterEach, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { CaptureGallery } from '../../src/ui/CaptureGallery';
import type { IRDocument } from '@style-converter/web/core/ir/IRModels';

afterEach(() => vi.restoreAllMocks());

// Minimal decoded v2 document — two components so the marker is asserted
// on EVERY canvas, not just the first.
const doc: IRDocument = {
  irVersion: 2,
  minReaderVersion: 2,
  components: [
    { id: 'a', name: 'A', properties: [] },
    { id: 'b', name: 'B', properties: [] },
  ],
};

// A fake WAAPI Animation: records the pause()/currentTime interaction so
// the order contract (pause FIRST, then seek) is assertable.
function fakeAnimation() {
  const calls: string[] = [];
  return {
    calls,
    currentTimeValue: null as number | null,
    pause() { calls.push('pause'); },
    set currentTime(v: number) { calls.push(`seek:${v}`); this.currentTimeValue = v; },
    get currentTime() { return this.currentTimeValue as unknown as number; },
  };
}

describe('data-animation-time verification marker', () => {
  it('stamps the seized time on every capture canvas', () => {
    const html = renderToStaticMarkup(<CaptureGallery document={doc} />);
    // One marker per canvas — the $$eval in capture-screenshots.mjs
    // asserts every() canvas carries it before screenshotting.
    expect(html.match(/data-animation-time="0\.5"/g)).toHaveLength(2);
  });
});

describe('window.__seizeAnimations hook', () => {
  it('is published when the URL carries animationTime', () => {
    // Publication happens at module import (read-once URL param), which
    // the CaptureGallery import above already triggered.
    const w = window as unknown as { __seizeAnimations?: (t: number) => number };
    expect(typeof w.__seizeAnimations).toBe('function');
  });

  it('pauses FIRST then seeks every animation to t·1000 ms, and is idempotent', () => {
    const a = fakeAnimation();
    const b = fakeAnimation();
    // jsdom has no WAAPI — install getAnimations exactly where the hook
    // reads it (document.getAnimations covers the whole surface).
    (document as unknown as { getAnimations: () => unknown[] }).getAnimations =
      () => [a, b];
    const w = window as unknown as { __seizeAnimations: (t: number) => number };
    expect(w.__seizeAnimations(0.5)).toBe(2);      // both animations seized
    expect(a.calls).toEqual(['pause', 'seek:500']); // pause-then-seek: the seek HOLDS
    expect(b.currentTimeValue).toBe(500);           // seconds → WAAPI ms
    // Determinism: the capture script re-seizes right before every
    // screenshot — repeating the call must land in the same state.
    expect(w.__seizeAnimations(0.5)).toBe(2);
    expect(a.calls).toEqual(['pause', 'seek:500', 'pause', 'seek:500']);
    expect(a.currentTimeValue).toBe(500);
  });
});
