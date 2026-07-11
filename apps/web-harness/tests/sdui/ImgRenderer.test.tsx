// ImgRenderer.test — pins the issue-#36 web slice: components whose
// `meta.sourceTag` is 'img' render as a REAL <img> replaced element with
// the deterministic inline placeholder src (nothing on today's wire
// carries an image source — the wave-9 IR gap documented in
// ComponentRenderer.tsx), while every other tag keeps its existing path.

import { describe, it, expect, vi, afterEach } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { ComponentRenderer } from '../../src/sdui/ComponentRenderer';
import type { ComposedNode } from '../../src/sdui/Composer';
import type { IRComponent } from '@style-converter/web/core/ir/IRModels';

const node = (component: IRComponent, children: ComposedNode[] = []): ComposedNode =>
  ({ component, children });

afterEach(() => vi.restoreAllMocks());

describe('meta.sourceTag img → real <img> element', () => {
  const imgComp: IRComponent = {
    id: 'img-001',
    name: 'Img_Basic',
    properties: [
      { type: 'Width', data: { type: 'length', px: 120 } },
      { type: 'Height', data: { type: 'length', px: 80 } },
    ],
    meta: { sourceTag: 'img' },
  };

  it('renders an <img> with the deterministic placeholder data-URI src', () => {
    const html = renderToStaticMarkup(<ComponentRenderer node={node(imgComp)} />);
    expect(html.startsWith('<img')).toBe(true);
    // Fixed bytes → fixed pixels → stable captures. The src is inline
    // (no network fetch can vary a capture) and never changes run-to-run.
    expect(html).toContain('src="data:image/svg+xml,');
    // Void element: no placeholder span, no children markup.
    expect(html).not.toContain('<span');
    // Style pipeline unchanged: the IR-declared size reaches the element.
    expect(html).toContain('width:120px');
    expect(html).toContain('height:80px');
    // Identity/capture attributes preserved (Puppeteer filename contract).
    expect(html).toContain('data-component-id="img-001"');
    expect(html).toContain('class="sc-img-001"');
  });

  it('uses the component text as alt, empty alt otherwise', () => {
    expect(renderToStaticMarkup(<ComponentRenderer node={node({ ...imgComp, text: 'a kitten' })} />))
      .toContain('alt="a kitten"');
    expect(renderToStaticMarkup(<ComponentRenderer node={node(imgComp)} />))
      .toContain('alt=""');
  });

  it('warns loudly and drops composed children (an <img> is void)', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const child: IRComponent = { id: 'stray', name: 'Stray', properties: [] };
    const html = renderToStaticMarkup(
      <ComponentRenderer node={node({ ...imgComp }, [node(child)])} />,
    );
    expect(html).not.toContain('stray'); // children not rendered inside a void element
    expect(warn).toHaveBeenCalledWith(expect.stringContaining("sourceTag 'img'"));
  });

  it('does not disturb the non-img tag paths (allowlist unchanged)', () => {
    const p: IRComponent = { ...imgComp, id: 'p-001', meta: { sourceTag: 'p' } };
    expect(renderToStaticMarkup(<ComponentRenderer node={node(p)} />).startsWith('<p')).toBe(true);
    const plain: IRComponent = { ...imgComp, id: 'd-001', meta: undefined };
    expect(renderToStaticMarkup(<ComponentRenderer node={node(plain)} />).startsWith('<div')).toBe(true);
  });
});
