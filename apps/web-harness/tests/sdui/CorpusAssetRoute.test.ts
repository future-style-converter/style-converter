// CorpusAssetRoute.test.ts — wave-48 lane W5.
//
// Pins the corpus-relative background-image url router: the name→directory
// inversion of the fixtureStem encoding (tools/titan/safe-name.mjs — `__` is
// verified collision-free against the corpus), the /wpt-image/ prefix
// contract shared with wptImageSrc, and the identity guarantees that keep
// every non-WPT capture byte-stable.
import { describe, it, expect } from 'vitest';
import {
  corpusDirOfComponent,
  routeCorpusUrls,
  routeCorpusAssetStyles,
} from '../../src/sdui/CorpusAssetRoute';

describe('corpusDirOfComponent — inverting the fixtureStem encoding', () => {
  it('top-level test: section dir', () => {
    // VERBATIM wave48-cal component name (css-image-fallbacks-and-annotations002).
    expect(corpusDirOfComponent('wpt__css-images__css-image-fallbacks-and-annotations002__1'))
      .toBe('css/css-images');
  });
  it('nested test: subdirectory chain joins back with slashes', () => {
    expect(corpusDirOfComponent('wpt__css-images__gradient__gradient-decreasing-hue-lch__2'))
      .toBe('css/css-images/gradient');
  });
  it('composed test key without a component index still resolves', () => {
    expect(corpusDirOfComponent('wpt__css-contain__contain-body-w-m-001'))
      .toBe('css/css-contain');
  });
  it('non-WPT names are null — the legacy 327-pair path must not move', () => {
    expect(corpusDirOfComponent('Neumorphic_Light')).toBeNull();
    expect(corpusDirOfComponent(undefined)).toBeNull();
    expect(corpusDirOfComponent(42)).toBeNull();
  });
});

describe('routeCorpusUrls — which refs move and which never do', () => {
  const dir = 'css/css-images';
  it('routes the VERBATIM 002/005 support path', () => {
    expect(routeCorpusUrls('url("support/1x1-green.png")', dir))
      .toBe('url("/wpt-image/css/css-images/support/1x1-green.png")');
  });
  it('resolves ../ against the test directory', () => {
    expect(routeCorpusUrls('url("../support/red-rect.svg")', 'css/css-images/gradient'))
      .toBe('url("/wpt-image/css/css-images/support/red-rect.svg")');
  });
  it('rewrites every layer of a comma list, leaving gradients alone', () => {
    // The lowered image() shape from BackgroundImageExtractor (003/004):
    const input = 'url("1x1-green.svg"), url("support/1x1-green.png"), linear-gradient(rgba(0, 128, 0, 1), rgba(0, 128, 0, 1))';
    expect(routeCorpusUrls(input, dir)).toBe(
      'url("/wpt-image/css/css-images/1x1-green.svg"), url("/wpt-image/css/css-images/support/1x1-green.png"), linear-gradient(rgba(0, 128, 0, 1), rgba(0, 128, 0, 1))',
    );
  });
  it('never touches data:/http(s)/protocol-relative/absolute/#fragment refs', () => {
    for (const ref of ['data:image/png;base64,AA==', 'https://x/y.png', '//x/y.png', '/already/rooted.png', '#svg-filter']) {
      const css = `url("${ref}")`;
      expect(routeCorpusUrls(css, dir)).toBe(css);
    }
  });
});

describe('routeCorpusAssetStyles — identity guarantees', () => {
  it('returns the SAME object for non-WPT components', () => {
    const styles = { backgroundImage: 'url("support/x.png")' };
    expect(routeCorpusAssetStyles(styles, 'Legacy_Component')).toBe(styles);
  });
  it('returns the SAME object when nothing needed routing', () => {
    const styles = { backgroundImage: 'linear-gradient(red, blue)', color: 'red' };
    expect(routeCorpusAssetStyles(styles, 'wpt__css-images__t__0')).toBe(styles);
  });
  it('copy-on-write rewrites backgroundImage only', () => {
    const styles = { backgroundImage: 'url("support/x.png")', color: 'red' };
    const out = routeCorpusAssetStyles(styles, 'wpt__css-images__t__0');
    expect(out).not.toBe(styles);
    expect(out.backgroundImage).toBe('url("/wpt-image/css/css-images/support/x.png")');
    expect(out.color).toBe('red');
    // The input object itself is never mutated.
    expect(styles.backgroundImage).toBe('url("support/x.png")');
  });
});
