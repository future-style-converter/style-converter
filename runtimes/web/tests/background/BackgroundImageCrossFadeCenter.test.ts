// Wave-21 IMAGES lane — web pins for:
//   A-RC5 gradient-center pos-key fix (serializer writes "pos"; the old
//          extractor read only "position" → every gradient centered)
//   A-RC8 <length> gradient centers serialized verbatim (px / lh)
//   A-RC2 cross-fade() re-serialization + shared weight normalization
// Wire shapes pinned against the LIVE wave21-gate artifacts (e.g.
// wpt__css-images__conic-gradient-center.json: {"pos":{"x":25,"y":25}}).
import { describe, it, expect } from 'vitest';
import { extractBackgroundImage } from '../../src/engine/background/BackgroundImageExtractor';
import { applyBackgroundImage } from '../../src/engine/background/BackgroundImageApplier';
import { normalizeCrossFadeWeights } from '../../src/engine/background/CrossFadeMath';

const p = (type: string, data: unknown) => ({ type, data });

// Two-stop red/blue helper matching the converter's stop wire.
const redBlueStops = [
  { color: { srgb: { r: 1, g: 0, b: 0 }, original: 'red' }, position: null },
  { color: { srgb: { r: 0, g: 0, b: 1 }, original: 'blue' }, position: null },
];

const css = (data: unknown) =>
  applyBackgroundImage(extractBackgroundImage([p('BackgroundImage', data)])).backgroundImage!;

describe('gradient center pos-key fix (A-RC5)', () => {
  it('honours the "pos" key on conic gradients (live wire shape)', () => {
    // EXACT wire from wave21-gate conic-gradient-center: pos:{x:25,y:25}.
    const out = css([{ type: 'conic-gradient', pos: { x: 25, y: 25 }, stops: redBlueStops }]);
    expect(out).toContain('conic-gradient(at 25% 25%,');
  });

  it('honours the "pos" key on radial gradients', () => {
    const out = css([{ type: 'radial-gradient', pos: { x: 10, y: 90 }, stops: redBlueStops }]);
    expect(out).toContain('radial-gradient(at 10% 90%,');
  });

  it('still tolerates the legacy "position" key', () => {
    // Older snapshots may carry "position" — the fallback must survive.
    const out = css([{ type: 'conic-gradient', position: { x: 75, y: 25 }, stops: redBlueStops }]);
    expect(out).toContain('at 75% 25%');
  });

  it('emits no at-clause when the wire has no position', () => {
    // Regression pin: absent pos must NOT invent 'at 50% 50%' text.
    const out = css([{ type: 'conic-gradient', stops: redBlueStops }]);
    expect(out).not.toContain(' at ');
  });

  it('serializes first-class shape/size keys on radial gradients', () => {
    // Post-Phase-12 wire carries shape/size as keys (not stop-stuffed).
    const out = css([{
      type: 'radial-gradient', shape: 'circle', size: 'closest-side',
      pos: { x: 25, y: 25 }, stops: redBlueStops,
    }]);
    expect(out).toContain('radial-gradient(circle closest-side at 25% 25%,');
  });
});

describe('length gradient centers (A-RC8)', () => {
  it('serializes px centers verbatim', () => {
    // {"px":100} is the IRLengthPercentage length wire form.
    const out = css([{
      type: 'radial-gradient', pos: { x: { px: 100 }, y: { px: 50 } }, stops: redBlueStops,
    }]);
    expect(out).toContain('at 100px 50px');
  });

  it('serializes runtime-dependent lh centers verbatim for the browser to resolve', () => {
    // {"original":{"v":1,"u":"LH"}} — pixels unresolvable at convert time;
    // on web the BROWSER owns font metrics, so the unit passes through.
    const out = css([{
      type: 'conic-gradient',
      pos: { x: { original: { v: 1, u: 'LH' } }, y: { px: 50 } },
      stops: redBlueStops,
    }]);
    expect(out).toContain('at 1lh 50px');
  });

  it('mixes percent and length axes', () => {
    const out = css([{
      type: 'radial-gradient', pos: { x: 0, y: { px: 10 } }, stops: redBlueStops,
    }]);
    expect(out).toContain('at 0% 10px');
  });
});

describe('cross-fade() (A-RC2, wave-22 -webkit chain lowering)', () => {
  // wave-22: the modern unprefixed n-ary cross-fade() is parsed by NO
  // shipping engine unflagged (Chromium gates it behind
  // CSSCrossFadeFunction) — the wave-21 verbatim emission made the whole
  // background-image declaration invalid and the capture browser painted
  // NOTHING (wave21-final css-images: target-alpha web 0.901 vs natives
  // 0.98+, premultiplied-alpha box blank). The serializer now lowers the
  // n-ary form onto an equivalent nested legacy -webkit-cross-fade()
  // chain (left fold, image k+1 entering at its share of the running
  // sum; sub-100% totals close with a transparent image tail).

  it('lowers the six-arg target-alpha wire onto a nested -webkit chain with a 40% transparent tail', () => {
    // Six 10% linear-gradients (cross-fade-target-alpha.html): sum 60% →
    // five pair-folds (50/33.3333/25/20/16.6667%) + transparent 40% tail.
    const args = Array.from({ length: 6 }, () => ({
      weight: 10,
      image: { type: 'linear-gradient', stops: redBlueStops },
    }));
    const out = css([{ type: 'cross-fade', args }]);
    // 5 folds + 1 transparent tail = 6 -webkit-cross-fade() calls.
    expect(out.match(/-webkit-cross-fade\(/g)).toHaveLength(6);
    // The remainder of a 60% total stays transparent (§2.6.2).
    expect(out.endsWith('linear-gradient(transparent, transparent), 40%)')).toBe(true);
    // No unprefixed modern function may survive in the output.
    expect(out).not.toMatch(/(?<!-webkit-)cross-fade\(/);
  });

  it('fills omitted weights per §2.6.2 (two colors → 50/50 -webkit pair)', () => {
    // cross-fade-premultiplied-alpha.html: two color args, no weights.
    const out = css([{
      type: 'cross-fade',
      args: [
        { image: { type: 'color', color: { srgb: { r: 1, g: 0, b: 0, a: 0.01 } } } },
        { image: { type: 'color', color: { srgb: { r: 0, g: 1, b: 0, a: 1 } } } },
      ],
    }]);
    // Colors gradient-wrap (legacy args must be <image>s); sum 100% → no
    // transparent tail, one fold at 50%.
    expect(out).toBe(
      '-webkit-cross-fade(linear-gradient(rgba(255, 0, 0, 0.01), rgba(255, 0, 0, 0.01)), '
      + 'linear-gradient(rgba(0, 255, 0, 1), rgba(0, 255, 0, 1)), 50%)');
  });

  it('accepts the FLATTENED color-layer wire (live converter shape)', () => {
    // IRPropertySerializer.deepFlatten inlines {"type":"color","color":{…}}
    // into {"type":"color","srgb":…,"original":…} — the EXACT wire the
    // converter run on the premultiplied-alpha fixture produced. Both
    // shapes must extract identically.
    const out = css([{
      type: 'cross-fade',
      args: [
        { image: { type: 'color', srgb: { r: 1, g: 0, b: 0, a: 0.01 } } },
        { image: { type: 'color', srgb: { r: 0, g: 1, b: 0 } } },
      ],
    }]);
    expect(out).toBe(
      '-webkit-cross-fade(linear-gradient(rgba(255, 0, 0, 0.01), rgba(255, 0, 0, 0.01)), '
      + 'linear-gradient(rgba(0, 255, 0, 1), rgba(0, 255, 0, 1)), 50%)');
  });

  it('folds zero-weight images out and keeps chain percentages finite', () => {
    // A 0% image contributes nothing (would fold as 0/0) — it must be
    // skipped, not poison the chain with NaN%.
    const out = css([{
      type: 'cross-fade',
      args: [
        { weight: 0, image: { type: 'linear-gradient', stops: redBlueStops } },
        { weight: 60, image: { type: 'linear-gradient', stops: redBlueStops } },
      ],
    }]);
    expect(out).not.toContain('NaN');
    // One surviving 60% image + 40% transparent tail.
    expect(out.match(/-webkit-cross-fade\(/g)).toHaveLength(1);
    expect(out).toContain('transparent), 40%)');
  });

  it('emits -webkit-cross-fade for the legacy two-arg wire', () => {
    const out = css([{
      type: 'cross-fade', legacy: true,
      args: [
        { weight: 75, image: 'a.png' },
        { weight: 25, image: 'b.png' },
      ],
    }]);
    // Legacy form: trailing percentage names the SECOND image's share.
    expect(out).toBe('-webkit-cross-fade(url("a.png"), url("b.png"), 25%)');
  });

  it('drops the whole cross-fade when any argument is garbage', () => {
    // Partial serialization would silently re-weight the remaining args.
    const cfg = extractBackgroundImage([p('BackgroundImage', [{
      type: 'cross-fade',
      args: [{ weight: 50, image: 42 }, { weight: 50, image: 'b.png' }],
    }])]);
    expect(applyBackgroundImage(cfg)).toEqual({});
  });
});

describe('CrossFadeMath twin (pin table shared with Compose/SwiftUI)', () => {
  // PIN TABLE — the SAME rows exist in CrossFadeMathTest.kt (Compose) and
  // CrossFadeMathTests.swift (SwiftUI). Any edit here must be mirrored.
  it('six 10% layers keep 0.10 each (target-alpha: total 0.6)', () => {
    expect(normalizeCrossFadeWeights([10, 10, 10, 10, 10, 10]))
      .toEqual([0.1, 0.1, 0.1, 0.1, 0.1, 0.1]);
  });
  it('two omitted weights split 100% evenly (premultiplied-alpha: 0.5/0.5)', () => {
    expect(normalizeCrossFadeWeights([null, null])).toEqual([0.5, 0.5]);
  });
  it('legacy 25% pair resolves 0.75/0.25', () => {
    expect(normalizeCrossFadeWeights([75, 25])).toEqual([0.75, 0.25]);
  });
  it('over-100 sums scale down proportionally (150/50 → 0.75/0.25)', () => {
    expect(normalizeCrossFadeWeights([150, 50])).toEqual([0.75, 0.25]);
  });
  it('omitted weights share the floored remainder (60 + 2 omitted → 0.6/0.2/0.2)', () => {
    expect(normalizeCrossFadeWeights([60, null, null])).toEqual([0.6, 0.2, 0.2]);
  });
  it('specified over 100 with omissions gives omitted images zero', () => {
    expect(normalizeCrossFadeWeights([120, null])).toEqual([1.0, 0]);
  });
});
