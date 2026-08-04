/**
 * PseudoNodeRenderer — the ::before/::after/::marker span renderer split
 * out of NodeRenderer (wave-20 W1 file-size split; behaviour verbatim).
 * Pseudo rendering is a semantic choice shared by every skin, not a
 * capture calibration — hence a core module, not a RendererOptions hook.
 *
 * Deliberately JSX-free (.ts, createElement only) like the rest of the
 * renderer core.
 */

import { createElement } from 'react';
import type { CSSProperties, ReactElement } from 'react';
import type { IRPseudoNode } from '../core/ir/IRModels';
import { buildStyles } from '../core/renderer/StyleBuilder';

/**
 * Bridge a RAW CSS declarations map (the extractor-owned `pseudos`
 * payload shape, spec 01 — e.g. `{ display: 'block', 'font-weight':
 * 'bold' }`) to a React inline-style object: kebab-case keys camelise
 * (`-webkit-mask` → `WebkitMask` falls out of the same replace), custom
 * properties (`--x`) pass through verbatim (React sets them via
 * setProperty), and non-primitive values are dropped loudly — a nested
 * object here means the payload isn't a declarations map at all.
 */
export function styleFromRawDeclarations(decls: Record<string, unknown>): CSSProperties {
  const out: Record<string, unknown> = {};
  for (const [prop, value] of Object.entries(decls)) {
    if (typeof value !== 'string' && typeof value !== 'number') {
      console.warn(`[NodeRenderer] raw declaration "${prop}" has non-primitive value — dropped`);
      continue;
    }
    if (prop.startsWith('--')) { out[prop] = value; continue; }         // custom property, verbatim
    out[prop.replace(/-([a-z])/g, (_, c: string) => c.toUpperCase())] = value;
  }
  return out as CSSProperties;
}

/**
 * Render one pseudo-element node as an inline <span> (shared verbatim
 * with the old harness renderer — pseudo rendering is a semantic choice,
 * not a capture calibration). The span carries the pseudo rule's styles
 * inline (including `content:` for browser-evaluated counter()/attr()),
 * and materialises the literal `content` string as text. The marker role
 * gets `inline-block` + a trailing 0.5em gap approximating the native
 * marker-side spacing (CSS Lists 3 §4.3).
 *
 * `placement` (wave-28 lane PG) is the ROOT-SCOPE placement pin computed by
 * RootPseudoPlacement.rootPseudoPlacementStyle — null/undefined for every
 * ordinary element's pseudo, so those spans stay byte-identical. It sits
 * between the marker gap and the author declarations: a calibration the
 * author's own rule can still override, never one that overrides it.
 */
export function renderPseudoNode(
  p: IRPseudoNode,
  role: 'before' | 'after' | 'marker',
  placement?: CSSProperties | null,
): ReactElement {
  // Same engine as component styles: the pseudo rule's declarations
  // (color, font-*, AND content) reach the inline style attribute.
  // The wire forwards the extractor's payload VERBATIM (spec 01), and
  // the WPT extractor emits `properties` as a RAW declarations map
  // ({ display: 'block', background: 'green' }), not a typed IR list —
  // bridge that shape straight to inline styles; typed lists keep the
  // engine path. (Pre-bridge, the raw map crashed buildStyles and took
  // the whole capture page down with it.)
  const ps = Array.isArray(p.properties)
    ? buildStyles(p.properties)
    : (p.properties && typeof p.properties === 'object')
      ? styleFromRawDeclarations(p.properties as Record<string, unknown>)
      : {};
  // Literal `content:` string — both wire spellings tolerated (`_text`
  // is what the extractor emits today; `text` is the v2 spelling).
  const pText = typeof p._text === 'string' ? p._text : (typeof p.text === 'string' ? p.text : '');
  // Marker-side gap (see the function doc above); other roles inherit flow.
  const markerStyle: CSSProperties = role === 'marker'
    ? { display: 'inline-block', marginInlineEnd: '0.5em' }
    : {};
  // <span> preserves the inline flow ::before/::after participate in.
  return createElement(
    'span',
    {
      key: `pseudo-${role}-${p.id}`,                     // stable per role+id
      'data-pseudo': role,                               // role marker for tooling
      'data-component-id': p.id,                         // debug identity (may be absent)
      // Order = precedence: marker gap, then the root-scope placement pin
      // (wave-28 lane PG — see RootPseudoPlacement), then the author's own
      // declarations, which win over both.
      style: { ...markerStyle, ...(placement ?? {}), ...(ps as CSSProperties) },
    },
    pText,                                               // literal content string (may be '')
  );
}
