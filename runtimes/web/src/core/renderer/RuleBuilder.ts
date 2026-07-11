/**
 * RuleBuilder — the STYLESHEET path of the web runtime
 * (schema/spec/06-dynamic-styling.md; issues #33/#34 web side).
 *
 * buildStyles (StyleBuilder.ts) keeps emitting a component's BASE
 * properties as inline styles — unchanged, baseline-stable. This module
 * turns the component's conditional buckets into real CSS rules:
 *
 *   selectors[] → `.sc-<id>:hover, .sc-<id>.force-hover { … !important }`
 *   media[]     → `@media (min-width: 200px) { .sc-<id> { … !important } }`
 *
 * Wave 8 (schema/spec/07-animations.md §1.2) adds the document-level
 * keyframes map: `IRDocument.keyframes` → real `@keyframes` rules, each
 * stop serialized through the SAME buildStyles engine as base properties
 * (see buildKeyframeRules). The engine builds rule TEXT only; the harness
 * still owns the document and mounts everything via mountRules.
 *
 * How CSS cascade implements the spec-06 §3 layering model:
 * - Every bucket declaration carries `!important`, because author
 *   important beats the normal INLINE base declarations (css-cascade-5
 *   §6.2) — without it no bucket could override its base.
 * - Selector rules are `.class:pseudo` = specificity (0,2,0); media
 *   rules wrap a bare `.class` = (0,1,0). Among competing importants
 *   higher specificity wins, so an active STATE always beats an active
 *   width/scheme bucket — exactly the state-over-media order §3 fixes.
 * - Within each layer all rules tie on specificity, so document order
 *   decides — and rules are emitted in bucket ARRAY order, which the
 *   `v2/dynamic-styling.json` golden pins as surviving the wire.
 * - The `.sc-<id>.force-<cond>` twin selector on every selector rule is
 *   also (0,2,0), so a FORCED state (spec 06 §6) layers byte-identically
 *   to the real pseudo-class — the capture-parity hook.
 */

import type { IRComponent, IRKeyframes } from '../ir/IRModels';
import { buildStyles } from './StyleBuilder';
import { declarationsToCss } from './CssText';
import { parseMediaQueryV1 } from './MediaQueryV1';
// No-silent-fallthrough logging (CLAUDE.md): unsupported conditions /
// un-evaluatable queries are inert AND recorded, never guessed at.
import { logUnhandled } from '../../engine/PropertyTracker';

/** The runtime-v1 selector condition set (spec 06 §2), in spec order. */
export const RUNTIME_V1_CONDITIONS = ['hover', 'active', 'focus', 'disabled', 'checked'] as const;
/** One of the five runtime-v1 interaction states. */
export type RuntimeV1Condition = (typeof RUNTIME_V1_CONDITIONS)[number];

/** Type guard: is this wire condition active at runtime v1? */
export function isRuntimeV1Condition(condition: string): condition is RuntimeV1Condition {
  return (RUNTIME_V1_CONDITIONS as readonly string[]).includes(condition);
}

/**
 * The per-component CSS class the stylesheet rules target. Component
 * ids are unique document-wide (spec 01), so `sc-<id>` is collision-free;
 * characters outside the CSS-ident-safe set are folded to `_` so the
 * class never needs selector escaping (SSR string export stays trivial).
 */
export function componentClassName(id: string): string {
  return `sc-${id.replace(/[^a-zA-Z0-9_-]/g, '_')}`;
}

/** The force-class twin for a condition (spec 06 §6 capture hook). */
export function forceClassName(condition: RuntimeV1Condition): string {
  return `force-${condition}`;
}

/**
 * Build the CSS rule strings for ONE component's buckets, in cascade
 * order: media rules first (array order), then selector rules (array
 * order). Returns `[]` for the common bucket-free component — the
 * 327-pair baseline path allocates one empty array and nothing else.
 */
export function buildRules(component: IRComponent): string[] {
  const rules: string[] = [];                                        // emitted rule accumulator
  const cls = componentClassName(component.id);                      // shared selector target
  // ── media buckets (the lower cascade layer, spec 06 §3 step 2) ──────
  for (const bucket of component.media ?? []) {                      // wire array order preserved
    if (parseMediaQueryV1(bucket.query) === null) {                  // outside the v1 grammar?
      // Conservatively inactive (spec 06 §4) — the browser COULD evaluate
      // e.g. `orientation`, but natives can't, so web must not either.
      logUnhandled('MediaQuery', bucket.query);                      // log-once, never crash
      continue;                                                      // bucket contributes nothing
    }
    // Same engine as inline styles: bucket properties → CSSStyles →
    // declaration text. `!important` per the cascade model above.
    const decls = declarationsToCss(buildStyles(bucket.properties), true);
    if (!decls) continue;                                            // nothing extractable — no empty rule
    // Query text re-emitted verbatim (trimmed): it already passed the
    // grammar gate, and the browser evaluates it against the viewport —
    // which the capture harness pins to the render-surface width.
    rules.push(`@media ${bucket.query.trim()} { .${cls} { ${decls} } }`);
  }
  // ── selector buckets (the upper cascade layer, spec 06 §3 step 3) ───
  for (const bucket of component.selectors ?? []) {                  // wire array order preserved
    // Wire stores conditions colon-stripped (spec 01); tolerate a stray
    // leading colon defensively so a hand-authored doc can't crash us.
    const condition = bucket.condition.replace(/^:/, '');
    if (!isRuntimeV1Condition(condition)) {                          // outside the v1 condition set?
      // focus-visible / focus-within / nth-child(…) / visited / … are
      // preserved on the wire but INERT at runtime v1 (spec 06 §2).
      logUnhandled('SelectorCondition', condition);                  // log-once, never crash
      continue;                                                      // bucket contributes nothing
    }
    const decls = declarationsToCss(buildStyles(bucket.properties), true);
    if (!decls) continue;                                            // nothing extractable — no empty rule
    // Real pseudo-class + force-class twin ON THE SAME RULE: identical
    // declarations, identical (0,2,0) specificity — forcing `hover`
    // resolves byte-identically to a real hover (spec 06 §6).
    rules.push(`.${cls}:${condition}, .${cls}.${forceClassName(condition)} { ${decls} }`);
  }
  return rules;                                                      // may be [] — common case
}

/**
 * A keyframes name must be a CSS custom-ident excluding the CSS-wide
 * keywords and `none` (css-animations-1 §4.1 `<keyframes-name>`). The
 * converter lowercases idents and rejects garbage upstream, so this gate
 * only fires on hand-authored/corrupt documents — but a bad name inside
 * `@keyframes <name>` would poison the whole rule text handed to
 * insertRule, so refuse-and-log beats emit-and-hope.
 */
const KEYFRAMES_NAME = /^-?[A-Za-z_][A-Za-z0-9_-]*$/;
const KEYFRAMES_NAME_EXCLUDED = new Set(['none', 'initial', 'inherit', 'unset', 'revert', 'revert-layer', 'default']);

/**
 * Format a resolved stop offset (0..1 fraction, spec 07 §1.2) as the CSS
 * percent selector: `0 → "0%"`, `0.5 → "50%"`, `1/3 → "33.3333%"`.
 * toFixed(4) bounds float noise; Number() strips the trailing zeros so
 * the round offsets stay byte-stable ("50%", never "50.0000%").
 */
function offsetToPercent(offset: number): string {
  return `${Number((offset * 100).toFixed(4))}%`;
}

/**
 * Build the `@keyframes` rule strings for a document's keyframes map
 * (spec 07 §1.2), one rule per named set, map order preserved.
 *
 * Stop declarations are typed {type, data} property envelopes — the SAME
 * bytes component properties use — so each stop goes through the ENGINE's
 * buildStyles: every registered applier (opacity, transform lists, sRGB
 * colors, px lengths, …) serializes keyframe values exactly like base
 * values, and the two paths can never disagree. Web thereby emits every
 * declaration an applier recognizes — a superset of the §2 animatable
 * tier, which is spec-legal (non-tier values are "carried, not dropped";
 * the browser interpolates or steps them natively) — while unknown types
 * inside stops still log through buildStyles' PropertyTracker path.
 *
 * NO `!important` inside keyframes: css-animations-1 §4.1 makes important
 * keyframe declarations invalid, so unlike the bucket rules these are
 * plain declarations (keyframe values win via the animation origin, not
 * the cascade).
 */
export function buildKeyframeRules(keyframes?: IRKeyframes): string[] {
  const rules: string[] = [];                                        // one rule per named set
  if (!keyframes) return rules;                                      // omit-when-empty wire key
  for (const [name, stops] of Object.entries(keyframes)) {
    // Name gate (see KEYFRAMES_NAME above) — refuse-and-log, never emit
    // a rule whose header would make insertRule reject the whole set.
    if (!KEYFRAMES_NAME.test(name) || KEYFRAMES_NAME_EXCLUDED.has(name.toLowerCase())) {
      logUnhandled('KeyframesName', name);                           // no-silent-fallthrough
      continue;
    }
    // Wire stops are pre-sorted ascending (spec 07 §1.2 — readers MUST
    // NOT reorder), so array order here IS offset order.
    const stopRules = stops.map((stop) => {
      // Same engine as inline styles; plain declarations (no !important).
      const decls = declarationsToCss(buildStyles(stop.properties), false);
      // A stop whose payloads produced nothing still pins its offset —
      // an empty keyframe block is valid CSS and keeps the rule shape
      // faithful to the wire (the misses were logged by buildStyles).
      return `${offsetToPercent(stop.offset)} { ${decls} }`;
    });
    rules.push(`@keyframes ${name} { ${stopRules.join(' ')} }`);
  }
  return rules;
}

/**
 * Log (once per name, PropertyTracker-deduped) every animation-name
 * reference that no keyframes set defines — the spec 07 §1.3 dangling-
 * reference contract: a defined runtime no-op, surfaced loudly. Scans the
 * base properties AND the selector/media buckets (a state bucket may
 * start an animation). The `AnimationName` payload shape mirrors
 * AnimationNameExtractor: a list of {type:'none'} | {type:'identifier',
 * name} entries; 'none' never dangles.
 */
function logDanglingAnimationNames(components: IRComponent[], keyframes?: IRKeyframes): void {
  const defined = keyframes ?? {};                                   // absent map = nothing defined
  const check = (props: { type: string; data: unknown }[]) => {
    for (const p of props) {
      if (p.type !== 'AnimationName' || !Array.isArray(p.data)) continue;
      for (const entry of p.data as Array<Record<string, unknown>>) {
        if (!entry || typeof entry !== 'object') continue;
        if (entry.type !== 'identifier' || typeof entry.name !== 'string') continue;
        // Distinct tracker key ('KeyframesReference', not 'AnimationName')
        // so the miss never shadows the applier's real handled/registered
        // status for the AnimationName property type itself.
        if (!(entry.name in defined)) logUnhandled('KeyframesReference', entry.name);
      }
    }
  };
  for (const c of components) {                                      // whole-document scan
    check(c.properties);                                             // base declarations
    for (const s of c.selectors ?? []) check(s.properties);          // state buckets
    for (const m of c.media ?? []) check(m.properties);              // media buckets
  }
}

/**
 * Does any component carry a `light-dark()` color value? Those resolve
 * against the element's USED color-scheme (css-color-5), so the
 * document stylesheet must opt the root into `color-scheme: light dark`
 * for the dark arm to ever be reachable. Detection is a substring scan
 * over the serialised property payloads — the IR discriminator is
 * `{"type":"light-dark"}` (spec 02 dynamic colors), and text content
 * never appears inside `properties`/bucket payloads, so no false hits.
 */
function usesLightDark(components: IRComponent[]): boolean {
  for (const c of components) {                                      // any component qualifies
    if (JSON.stringify(c.properties).includes('"light-dark"')) return true;   // base properties
    if (c.selectors && JSON.stringify(c.selectors).includes('"light-dark"')) return true; // state buckets
    if (c.media && JSON.stringify(c.media).includes('"light-dark"')) return true;         // media buckets
  }
  return false;                                                      // fully static document
}

/**
 * Build the full rule list for a document: a root `color-scheme` opt-in
 * first (only when light-dark() is present — the scheme signal MUST agree
 * with prefers-color-scheme buckets, spec 06 §4), then the document's
 * `@keyframes` rules (spec 07 §1.2 — document-scoped at-rules, so they
 * belong with the document stylesheet, before any rule can reference
 * them), then every component's rules in flat-list order.
 *
 * `keyframes` is the additive v2 envelope key (IRDocument.keyframes);
 * omitting it keeps the historical single-argument call sites — and the
 * committed-baseline documents that carry no keyframes — byte-identical.
 */
export function buildRuleList(components: IRComponent[], keyframes?: IRKeyframes): string[] {
  // Dangling animation-name references are a defined no-op (spec 07 §1.3)
  // but MUST be logged once per name — do the scan whenever we build the
  // document stylesheet so the miss is visible on every render path.
  logDanglingAnimationNames(components, keyframes);
  const rules = [
    ...buildKeyframeRules(keyframes),                                // document-scoped at-rules first
    ...components.flatMap(buildRules),                               // per-component rules, doc order
  ];
  if (usesLightDark(components)) {                                   // light-dark() needs the opt-in
    // Root-level so it inherits everywhere; light default = the dark arm
    // must NOT apply in the standard light capture (DYNAMIC_CAPTURE §3).
    rules.unshift(':root { color-scheme: light dark }');
  }
  return rules;                                                      // [] for fully static docs
}

/**
 * SSR-safe string export: the whole document stylesheet as one string
 * (rules joined by newlines) — what a server renderer would inline
 * into a `<style>` tag. Pure; no DOM access.
 */
export function buildStylesheet(components: IRComponent[], keyframes?: IRKeyframes): string {
  return buildRuleList(components, keyframes).join('\n');            // deterministic join
}

/** The id of the managed <style> element mountRules owns. */
export const MANAGED_STYLE_ID = 'sc-dynamic-rules';

/**
 * Mount (or re-mount) the rule list into a document via a single
 * managed `<style>` element + CSSOM insertRule.
 *
 * - SSR-safe: no global `document` and no target → silent no-op (use
 *   `buildStylesheet` for the string path instead).
 * - Idempotent per render: the joined rule text is stamped on the
 *   element; re-mounting identical rules is a pure attribute compare.
 * - Never crashes: an insertRule rejection (malformed rule text) is
 *   logged via PropertyTracker and the remaining rules still mount.
 */
export function mountRules(rules: string[], targetDoc?: Document): void {
  // Resolve the target document; bail silently outside the DOM (SSR).
  const doc = targetDoc ?? (typeof document !== 'undefined' ? document : undefined);
  if (!doc) return;                                                  // SSR / node — string path only
  const cssText = rules.join('\n');                                  // idempotency fingerprint
  let el = doc.getElementById(MANAGED_STYLE_ID) as HTMLStyleElement | null;
  if (!el && rules.length === 0) return;                             // nothing mounted, nothing to mount
  if (el && el.getAttribute('data-sc-css') === cssText) return;      // identical render — no-op
  if (!el) {                                                         // first mount for this document
    el = doc.createElement('style');                                 // the managed element
    el.id = MANAGED_STYLE_ID;                                        // findable + collision-proof
    doc.head.appendChild(el);                                        // attach so .sheet materialises
  }
  el.setAttribute('data-sc-css', cssText);                           // record what this mount holds
  const sheet = el.sheet;                                            // CSSOM handle (post-attach)
  if (!sheet) {                                                      // exotic host without CSSOM
    el.textContent = cssText;                                        // text fallback — same rules
    return;                                                          // browser parses on its own
  }
  while (sheet.cssRules.length > 0) sheet.deleteRule(0);             // clear a previous render's rules
  for (const rule of rules) {                                        // CSSOM path — rule by rule
    try {
      sheet.insertRule(rule, sheet.cssRules.length);                 // append preserves cascade order
    } catch {
      // A rule the engine emitted but the host rejects (unknown pseudo
      // in an old browser, …) — skip it loudly, mount the rest.
      logUnhandled('CssRule', rule);                                 // no-silent-fallthrough
    }
  }
}
