// animation-eligibility.mjs — which components may move in a sweep.
//
// Extracted from animation-sweep.sh so the rule is unit-testable without
// booting three harnesses, the same reason pad-canvas.mjs and
// cross-platform-gate.mjs were extracted. The rule is small and entirely
// made of ways to be wrong:
//
//   · Too strict → components that correctly cannot move get reported as
//     dead. Measured: a state-only rule on transitions.json with
//     CAPTURE_FORCE_STATE=active flags 9 of 12 series while everything
//     works, because only MT_WidthGrow has an `:active` bucket.
//   · Too loose → the inverse assertion (a component with no bucket for
//     the forced state must NOT move) stops holding, and a forced state
//     leaking past the selector fold goes unnoticed.
//
// Both failure modes end the same way: someone weakens the check to
// silence a wrong red, and a working detector becomes decoration.

/**
 * Names of components that may legitimately change across a sweep.
 *
 * @param {object} doc          parsed fixture JSON ({ components: {...} })
 * @param {string} forcedState  CAPTURE_FORCE_STATE, or "" when unforced
 * @returns {Set<string>|null}  null = no restriction (unforced run: any
 *                              animated component may move and the fixture
 *                              alone cannot say which)
 */
export function eligibleToMove(doc, forcedState) {
  if (!forcedState) return null;
  const comps = doc?.components ?? {};
  const out = new Set();
  for (const [name, c] of Object.entries(comps)) {
    if (declaresBucketFor(c, forcedState) || declaresKeyframeAnimation(c)) out.add(name);
  }
  return out;
}

/**
 * Does this component carry a selector bucket for `state`?
 * Only such a component can start a TRANSITION when the state is forced.
 *
 * Accepts both wire spellings seen in fixtures: `selector: ":hover"` and
 * `condition: "hover"`. The leading colon is stripped so the two compare
 * equal — a mismatch here would silently empty the eligible set and turn
 * every real transition into a reported failure.
 */
export function declaresBucketFor(component, state) {
  const sels = component?.selectors ?? [];
  return sels.some((sel) => {
    const raw = sel?.selector ?? sel?.condition ?? '';
    return String(raw).replace(/^:/, '') === state;
  });
}

/**
 * Does this component declare a keyframe animation?
 *
 * Such a component moves REGARDLESS of any forced state, so it stays
 * eligible even when the forced state matches none of its buckets.
 * Without this, `keyframes-basic.json` — which declares no selectors at
 * all — reports all 8 components as ineligible under any forced state
 * while all 8 correctly animate: 24 false "leak" rows.
 *
 * Matches the `animation` shorthand and every `animation-*` longhand.
 * `animation-name: none` is deliberately NOT special-cased: a component
 * that names no animation simply never moves, which the sweep reports as
 * a dead series with a message pointing at delay/duration — a clearer
 * signal than silently dropping it from the expected set.
 */
export function declaresKeyframeAnimation(component) {
  const props = component?.properties ?? {};
  return Object.keys(props).some((k) => /^animation(-|$)/.test(k));
}

/** Capture filename → fixture component name (`003_MT_Delayed.png` → `MT_Delayed`). */
export function componentNameOf(filename) {
  return String(filename).replace(/^\d+_/, '').replace(/\.png$/, '');
}
