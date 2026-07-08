#!/usr/bin/env python3
"""Systematic degenerate-fixture detector.

For each property in fixtures/perfect/, check whether its
fixture has the context the property needs (children for flex/grid,
position:absolute child for top/right/etc., text content for typography,
SVG context for SVG props, etc.).

Outputs categorized list of degenerate fixtures.
"""
import os, re, json, glob

os.chdir(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))

NEEDS_CHILDREN = {'flex-direction', 'flex-wrap', 'flex-flow', 'justify-content',
    'align-items', 'align-content', 'place-content', 'place-items', 'gap',
    'row-gap', 'column-gap', 'grid', 'grid-template', 'grid-template-columns',
    'grid-template-rows', 'grid-template-areas', 'grid-auto-flow',
    'grid-auto-columns', 'grid-auto-rows'}
NEEDS_POSITIONED_CHILD = {'top', 'right', 'bottom', 'left', 'inset',
    'inset-block', 'inset-inline', 'inset-block-start', 'inset-block-end',
    'inset-inline-start', 'inset-inline-end', 'z-index'}
NEEDS_TEXT_CONTENT = {'font-feature-settings', 'font-kerning',
    'font-language-override', 'font-variant-alternates', 'font-variant-east-asian',
    'font-variant-ligatures', 'font-variant-numeric', 'font-variant-position',
    'hyphens', 'letter-spacing', 'line-break', 'tab-size', 'text-decoration',
    'text-decoration-line', 'text-decoration-style', 'text-decoration-color',
    'text-decoration-thickness', 'text-underline-offset', 'text-underline-position',
    'text-decoration-skip-ink', 'text-orientation', 'text-rendering', 'word-break',
    'word-spacing', 'word-wrap', 'white-space', 'white-space-collapse',
    'overflow-wrap'}
NEEDS_SVG_CONTEXT = {'fill', 'fill-opacity', 'fill-rule', 'stroke', 'stroke-width',
    'stroke-opacity', 'stroke-linecap', 'stroke-linejoin', 'stroke-miterlimit',
    'stroke-dasharray', 'stroke-dashoffset', 'cx', 'cy', 'r', 'rx', 'ry', 'x',
    'y', 'd', 'paint-order', 'vector-effect', 'shape-rendering',
    'color-interpolation', 'color-interpolation-filters', 'flood-color',
    'flood-opacity', 'lighting-color', 'stop-color', 'stop-opacity', 'marker',
    'marker-start', 'marker-mid', 'marker-end', 'mask', 'dominant-baseline',
    'alignment-baseline', 'baseline-shift', 'text-anchor',
    'glyph-orientation-horizontal', 'glyph-orientation-vertical',
    'enable-background', 'kerning'}
NEEDS_LIST_CONTEXT = {'list-style', 'list-style-type', 'list-style-image',
    'list-style-position'}
NEEDS_TABLE_CONTEXT = {'table-layout', 'border-collapse', 'border-spacing',
    'caption-side', 'empty-cells'}
NEEDS_SCROLLABLE = {'scroll-snap-type', 'scroll-snap-align', 'scroll-snap-stop',
    'scroll-padding', 'scroll-margin', 'overscroll-behavior',
    'overscroll-behavior-x', 'overscroll-behavior-y', 'scroll-behavior',
    'scrollbar-color', 'scrollbar-width', 'scrollbar-gutter'}
NEEDS_ANIMATION_TIMELINE = {'animation-name', 'animation-duration',
    'animation-delay', 'animation-direction', 'animation-fill-mode',
    'animation-iteration-count', 'animation-play-state',
    'animation-timing-function', 'animation-composition', 'animation-timeline',
    'animation-range', 'animation-range-start', 'animation-range-end',
    'transition-property', 'transition-duration', 'transition-delay',
    'transition-timing-function', 'transition-behavior', 'view-transition-name',
    'view-transition-class', 'view-transition-group'}


def kebab(p):
    return re.sub(r'(?<!^)(?=[A-Z])', '-', p).lower()


def main():
    degen = {}
    for fp in sorted(glob.glob('fixtures/perfect/*/*.json')):
        name = os.path.basename(fp)[:-5]
        cat = fp.split('/')[-2]
        css = kebab(name)
        try:
            d = json.load(open(fp))
        except Exception:
            continue
        need = None
        if css in NEEDS_CHILDREN: need = 'children'
        elif css in NEEDS_POSITIONED_CHILD: need = 'positioned-child'
        elif css in NEEDS_TEXT_CONTENT: need = 'text-content'
        elif css in NEEDS_SVG_CONTEXT: need = 'svg-context'
        elif css in NEEDS_LIST_CONTEXT: need = 'list-context'
        elif css in NEEDS_TABLE_CONTEXT: need = 'table-context'
        elif css in NEEDS_SCROLLABLE: need = 'scrollable'
        elif css in NEEDS_ANIMATION_TIMELINE: need = 'animation-timeline'
        if need is None:
            continue
        has_context = False
        for c in d['components'].values():
            props = c.get('properties', {})
            children = c.get('children', {})
            if need == 'children' and len(children) > 0:
                has_context = True
            elif need == 'positioned-child' and any(
                ch.get('properties', {}).get('position') == 'absolute'
                for ch in children.values()):
                has_context = True
            elif need == 'text-content' and 'text' in props:
                has_context = True
        if not has_context:
            degen.setdefault((cat, need), []).append(name)
    total = sum(len(v) for v in degen.values())
    print(f'TOTAL DEGENERATE: {total}')
    for (cat, need), props in sorted(degen.items(), key=lambda x: -len(x[1])):
        print(f'  {cat}/{need}: {len(props)}')
        for p in props[:3]:
            print(f'    - {p}')
        if len(props) > 3:
            print(f'    ... +{len(props)-3} more')


if __name__ == '__main__':
    main()
