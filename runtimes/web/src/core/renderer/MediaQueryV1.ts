/**
 * MediaQueryV1 — the dynamic-styling runtime-v1 media grammar
 * (schema/spec/06-dynamic-styling.md §4): a single `(feature: value)`
 * term or a conjunction of terms joined by `and`, over exactly two
 * features — `min-width`/`max-width` in px and `prefers-color-scheme`.
 *
 * Two exports, one parse:
 * - `parseMediaQueryV1` — the grammar VALIDATOR. RuleBuilder only emits
 *   @media rules for queries this accepts; anything else (`not`/`only`,
 *   comma lists, range syntax, other features, malformed text) is
 *   conservatively INACTIVE per spec 06 §4 — never handed to the
 *   browser, which would happily evaluate features runtime v1 says
 *   must stay inert (cross-platform honesty over web convenience).
 * - `evaluateMediaQueryV1` — the reference evaluator the native lanes
 *   mirror (render-surface width + platform dark-mode flag). On web
 *   the BROWSER evaluates the emitted @media text natively; this
 *   function exists for tests and as the normative tie-breaker.
 */

/** One parsed term of a runtime-v1 media query. */
export type MediaTermV1 =
  | { feature: 'min-width' | 'max-width'; px: number }               // width bound in CSS px
  | { feature: 'prefers-color-scheme'; scheme: 'light' | 'dark' };   // platform scheme signal

/** The environment a runtime-v1 query is evaluated against (spec 06 §4). */
export interface MediaEnvV1 {
  /** Render-SURFACE width in CSS px — never the device screen width. */
  surfaceWidth: number;
  /** Platform dark-mode signal (web: prefers-color-scheme: dark). */
  darkMode: boolean;
}

// (min-width: 390px) / (max-width: 250.5px) — px only at v1 (spec 06 §4).
const WIDTH_TERM = /^\(\s*(min-width|max-width)\s*:\s*(\d+(?:\.\d+)?)px\s*\)$/i;
// (prefers-color-scheme: light|dark) — the only non-width v1 feature.
const SCHEME_TERM = /^\(\s*prefers-color-scheme\s*:\s*(light|dark)\s*\)$/i;

/**
 * Parse a media query against the runtime-v1 grammar.
 *
 * Returns the term list on success, or `null` when ANY part of the
 * query falls outside the grammar — the whole-bucket-inactive rule
 * (spec 06 §4: a query the runtime cannot evaluate never applies).
 */
export function parseMediaQueryV1(query: string): MediaTermV1[] | null {
  const q = query.trim();                                            // tolerate outer whitespace only
  if (q.length === 0) return null;                                   // empty — malformed
  if (q.includes(',')) return null;                                  // comma query-lists: not v1
  const terms: MediaTermV1[] = [];                                   // parsed conjunction
  // Split on the `and` combinator. `not`/`only` prefixes and range
  // syntax never match the per-term regexes below, so they fall out as
  // parse failures without dedicated handling.
  for (const rawTerm of q.split(/\s+and\s+/i)) {
    const t = rawTerm.trim();                                        // per-term whitespace tolerance
    const width = WIDTH_TERM.exec(t);                                // width-bound term?
    if (width) {
      terms.push({
        feature: width[1].toLowerCase() as 'min-width' | 'max-width',// normalised feature name
        px: Number(width[2]),                                        // numeric px bound
      });
      continue;                                                      // term accepted
    }
    const scheme = SCHEME_TERM.exec(t);                              // scheme term?
    if (scheme) {
      terms.push({
        feature: 'prefers-color-scheme',                             // fixed feature tag
        scheme: scheme[1].toLowerCase() as 'light' | 'dark',         // normalised scheme value
      });
      continue;                                                      // term accepted
    }
    return null;                                                     // ANY unknown term poisons the query
  }
  return terms;                                                      // all terms conform
}

/**
 * Evaluate a runtime-v1 query against an environment.
 *
 * Returns `true`/`false` for conforming queries, `null` when the query
 * is outside the v1 grammar (callers treat null as "bucket inactive").
 * Width bounds are INCLUSIVE per mediaqueries-5 §4.2 — `(min-width:
 * 390px)` matches a 390 px surface.
 */
export function evaluateMediaQueryV1(query: string, env: MediaEnvV1): boolean | null {
  const terms = parseMediaQueryV1(query);                            // one shared parse
  if (terms === null) return null;                                   // un-evaluatable → caller inerts
  return terms.every((term) => {                                     // conjunction: every term holds
    switch (term.feature) {
      case 'min-width': return env.surfaceWidth >= term.px;          // inclusive lower bound
      case 'max-width': return env.surfaceWidth <= term.px;          // inclusive upper bound
      case 'prefers-color-scheme':                                   // scheme ↔ dark-mode flag
        return term.scheme === (env.darkMode ? 'dark' : 'light');
    }
  });
}
