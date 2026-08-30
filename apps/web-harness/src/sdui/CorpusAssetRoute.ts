/**
 * CorpusAssetRoute — routes corpus-relative url() references inside a
 * background-image declaration through the vite /wpt-image/ corpus route
 * (wave-48 lane W5).
 *
 * WHY: a WPT test's `background-image: url("support/1x1-green.png")` (or the
 * url layers the converter now lowers `image()` into — ImageNotationParser)
 * carries the AUTHOR's test-relative path. The capture page's origin serves
 * vite's public/ dir, so that path 404s and the layer paints nothing —
 * measured on wave48-cal css-images/css-image-fallbacks-and-annotations002:
 * the whole square stays the forbidden background-color red (0.9999,
 * colorFailed). Replaced <img> content solved the same problem long ago via
 * meta.attrs.src + the /wpt-image/ route (wave-36 lane M1, wptImageSrc), but
 * that wire is extractor-resolved; background layers reach the browser as
 * CSS text, so the resolution has to happen here, where the corpus route is
 * a harness concept (the production renderer must keep emitting the wire's
 * own urls untouched).
 *
 * VERIFIED (wave-48 W5, section-runner web re-capture, run w48-w5-verify2):
 * css-image-fallbacks-and-annotations 002/003/004 flip 0.9999 F → 1.0000 P
 * once their image()-lowered url layers actually load; a corpus-wide IR
 * scan found ZERO other components carrying a corpus-relative
 * background-image url, so this module's blast radius is exactly those
 * lowered layers.
 *
 * HOW THE TEST DIRECTORY IS RECOVERED: composed component names are
 * `wpt__<section>__<sub>__…__<stem>__<idx>` (build-combined-fixture keys,
 * fixtureStem encoding — tools/titan/safe-name.mjs). The `__` separator is
 * verified collision-free against the corpus ("the corpus … contains NO
 * `__` in any path segment", safe-name.mjs), so splitting on it exactly
 * inverts the encoding: drop the `wpt__` prefix and the trailing numeric
 * component index, drop the test stem, and the remaining segments are the
 * test's directory chain under css/.
 */

/**
 * The corpus directory (repo-relative under tools/wpt, e.g.
 * `css/css-images/gradient`) a composed component's test lives in, or null
 * for every non-WPT component (legacy 327-pair fixtures keep their names —
 * and therefore their styles — byte-identical).
 */
export function corpusDirOfComponent(name: unknown): string | null {
  if (typeof name !== 'string' || !name.startsWith('wpt__')) return null;
  const parts = name.slice('wpt__'.length).split('__');
  // build-combined-fixture appends the per-test component index (`__0`,
  // `__1`, …) — strictly numeric, so stripping it can never eat a stem.
  if (parts.length >= 2 && /^\d+$/.test(parts[parts.length - 1])) parts.pop();
  // Need at least <section> + <stem>; the stem itself is the FILE, not a dir.
  if (parts.length < 2) return null;
  parts.pop();
  // Every corpus test key is rooted at css/ (wpt-buckets.json pin).
  return 'css/' + parts.join('/');
}

/** url() reference heads that must NEVER be rewritten: self-contained data,
 *  real network schemes, protocol-relative, origin-absolute, and same-page
 *  SVG fragment references (filter/mask/clip-path url(#id)). */
const NON_CORPUS_URL = /^(?:data:|[a-z][a-z0-9+.-]*:|\/\/|\/|#)/i;

/**
 * Rewrite every corpus-relative url() inside one CSS image list to the
 * /wpt-image/ route, resolved against [dir]. Returns the input string
 * IDENTICALLY (same reference) when nothing needed rewriting, so callers
 * can cheaply detect the no-op case.
 */
export function routeCorpusUrls(css: string, dir: string): string {
  if (!css.includes('url(')) return css;
  return css.replace(/url\((['"]?)([^'")]+)\1\)/g, (whole, _q: string, ref: string) => {
    if (NON_CORPUS_URL.test(ref)) return whole;                       // not a corpus-relative path
    // Resolve ../ and ./ segments against the test's directory — WPT
    // support files are referenced both as `support/x.png` and `../support/x.png`.
    const out: string[] = [];
    for (const seg of `${dir}/${ref}`.split('/')) {
      if (seg === '..') out.pop();
      else if (seg !== '.' && seg !== '') out.push(seg);
    }
    // Same per-segment percent-encoding contract as wptImageSrc — the route
    // decodes with decodeURIComponent, so both halves must agree.
    return `url("/wpt-image/${out.map(encodeURIComponent).join('/')}")`;
  });
}

/** Style keys that carry <image> lists a corpus test can reference assets
 *  from. Deliberately background-image ONLY today: it is the one key the
 *  measured defect exercises; mask-image/border-image-source join when a
 *  corpus cell demonstrates the need (no speculative rewrites). */
const IMAGE_STYLE_KEYS = ['backgroundImage'] as const;

/**
 * The decorateStyles composition step: rewrite corpus-relative image urls on
 * a WPT component's resolved styles. Identity (the SAME object) for
 * non-WPT components and for styles without a rewritable url, so the
 * legacy capture path stays allocation- and byte-identical.
 */
export function routeCorpusAssetStyles<T extends Record<string, unknown>>(
  styles: T,
  componentName: unknown,
): T {
  const dir = corpusDirOfComponent(componentName);
  if (dir === null) return styles;                                    // non-WPT component — untouched
  let changed: T | null = null;
  for (const key of IMAGE_STYLE_KEYS) {
    const v = styles[key];
    if (typeof v !== 'string') continue;
    const routed = routeCorpusUrls(v, dir);
    if (routed === v) continue;                                       // no corpus-relative url inside
    if (changed === null) changed = { ...styles };                    // copy-on-write
    (changed as Record<string, unknown>)[key] = routed;
  }
  return changed ?? styles;
}
