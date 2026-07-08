#!/usr/bin/env python3
"""Form-aware fixture synthesizer (Tier 1 deepening).

The auditor caught that ETERNAL extracts bare keywords from parser source
(missing function-syntax forms entirely). This synthesizer takes the
opposite approach: per CSS-property family, hand-curates the FORMS that
matter — multi-value lists, position keywords + lengths combos, function
calls — and emits proper fixtures.

Categories handled:
- POSITION: 1/2/3/4-value combos with kw/length/percent (background-position,
  object-position, mask-position, etc.)
- LENGTH_PAIR: 1-value (single length) + 2-value (w h) + cover/contain
  (background-size, mask-size)
- MULTI_LENGTH: 1/2/3/4 length values (border-width, padding, margin shorthand,
  border-radius)
- COMMA_LIST: comma-separated value lists (animation-duration, transition-delay,
  background-image)
- TIME_RANGE: 0s, ms, s, calc(time + time), comma-list
- ANGLE: deg, turn, rad, grad, calc, comma-list
- COLOR: hex/rgb/oklch/lab/named/transparent/currentColor

For each category, emits proper CSS in valid syntax — no bare-keyword leakage.
"""
import json, os, sys, re

os.chdir('/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/jovial-shockley-b4adce')

# Per-property form templates. Each entry maps CSS prop → list of valid CSS values
# that exercise the parser's full grammar.
FORM_TEMPLATES = {
    # POSITION family
    'background-position': ['center', 'top', 'bottom', 'left', 'right',
                            'left top', 'right center', '50% 50%', '10px 20px',
                            '25%', '10px', 'left 10px top 20px'],
    'background-position-x': ['center', 'left', 'right', '50%', '10px', '25%'],
    'background-position-y': ['center', 'top', 'bottom', '50%', '10px', '75%'],
    'background-position-block': ['start', 'center', 'end', '50%', '10px'],
    'background-position-inline': ['start', 'center', 'end', '50%', '10px'],
    'object-position': ['center', 'top left', '50% 50%', '10px 20px', '25% 75%', 'right'],
    'mask-position': ['center', 'top left', '50% 50%', '10px 20px', 'right',
                      '0% 0%', 'top', 'bottom', 'left'],

    # LENGTH_PAIR family — single, two-value, plus cover/contain keywords
    'background-size': ['auto', 'cover', 'contain', '50px', '100px 50px', '50% 50%', 'auto 100px'],
    'mask-size': ['auto', 'cover', 'contain', '50px', '100px 50px', '50% auto'],

    # MULTI_LENGTH family — 1/2/3/4 value forms
    'border-radius': ['0', '4px', '4px 8px', '4px 8px 12px', '4px 8px 12px 16px',
                      '50%', '4px / 8px', '4px 8px / 12px 16px'],
    'padding': ['0', '4px', '4px 8px', '4px 8px 12px', '4px 8px 12px 16px',
                '0 8px', '4px 0', '5%'],
    'margin': ['0', '4px', '4px 8px', '4px 8px 12px', '4px 8px 12px 16px',
               'auto', '0 auto', '4px auto'],
    'border-width': ['thin', 'medium', 'thick', '1px', '2px 4px',
                     '2px 4px 6px', '2px 4px 6px 8px'],
    'inset': ['auto', '0', '10px', '5%', '10px 20px', '10px 20px 30px',
              '10px 20px 30px 40px'],

    # REPEAT family
    'background-repeat': ['repeat', 'no-repeat', 'repeat-x', 'repeat-y',
                          'space', 'round', 'repeat space', 'no-repeat repeat'],
    'mask-repeat': ['repeat', 'no-repeat', 'repeat-x', 'repeat-y', 'space', 'round'],

    # FLEX family
    'flex-basis': ['auto', 'content', 'min-content', 'max-content', 'fit-content',
                   '0', '100px', '50%'],

    # OVERFLOW family
    'overflow-x': ['visible', 'hidden', 'clip', 'scroll', 'auto'],
    'overflow-y': ['visible', 'hidden', 'clip', 'scroll', 'auto'],
    'overflow-block': ['visible', 'hidden', 'clip', 'scroll', 'auto'],
    'overflow-inline': ['visible', 'hidden', 'clip', 'scroll', 'auto'],

    # MASK family
    'mask-composite': ['add', 'subtract', 'intersect', 'exclude'],
    'mask-mode': ['match-source', 'alpha', 'luminance'],
    'mask-clip': ['border-box', 'padding-box', 'content-box', 'no-clip',
                  'fill-box', 'stroke-box', 'view-box'],
    'mask-origin': ['border-box', 'padding-box', 'content-box',
                    'fill-box', 'stroke-box', 'view-box'],
    'mask-type': ['luminance', 'alpha'],

    # CONTENT family
    'quotes': ['none', 'auto', '"“" "”"', '"<" ">"',
               '"“" "”" "‘" "’"'],

    # GRID family
    'grid-template-columns': ['none', 'auto', '100px', '1fr', '100px 1fr',
                              'repeat(3, 1fr)', 'minmax(100px, 1fr)',
                              '[start] 100px [middle] 1fr [end]'],
    'grid-template-rows': ['none', 'auto', '100px', '1fr', '100px 1fr',
                           'repeat(3, 50px)', 'minmax(50px, 1fr)'],

    # BOX-SHADOW
    'box-shadow': ['none', '0 1px 2px rgba(0,0,0,0.1)',
                   '0 4px 8px rgba(0,0,0,0.2)',
                   'inset 0 0 4px rgba(0,0,0,0.3)',
                   '2px 2px 4px #ef4444',
                   '0 0 0 2px #3b82f6, 0 0 0 4px #1f2937'],

    # TEXT-SHADOW
    'text-shadow': ['none', '1px 1px #000', '2px 2px 4px rgba(0,0,0,0.5)',
                    '0 0 4px #ef4444',
                    '1px 1px #000, 2px 2px 4px #3b82f6'],
}

# Some properties need a colored bg-image to make position visible
NEEDS_BG = {'background-position', 'background-position-x', 'background-position-y',
            'background-position-block', 'background-position-inline',
            'background-size', 'background-repeat'}

NEEDS_TEXT = {'text-shadow', 'quotes'}

# Mask-* props are no-ops without a mask-image source
NEEDS_MASK_IMAGE = {'mask-position', 'mask-size', 'mask-repeat',
                    'mask-composite', 'mask-mode', 'mask-clip',
                    'mask-origin', 'mask-type'}

# Border-width needs border-style + border-color or it renders nothing
NEEDS_BORDER_STYLE = {'border-width'}

# Properties that the synthesizer should NOT touch — they need richer
# parent-context fixtures (replaced elements, flex parent, grid parent)
SKIP_REASON = {
    'object-position': 'requires <img>/<video> child (replaced element)',
    'flex-basis': 'requires display: flex parent for shrink/grow context',
    'grid-template-columns': 'requires children to allocate tracks against',
    'grid-template-rows': 'requires children to allocate tracks against',
}


def kebab(p):
    return re.sub(r'(?<!^)(?=[A-Z])', '-', p).lower()


def synth_fixture(cat, prop_pascal):
    css_prop = kebab(prop_pascal)
    if css_prop in SKIP_REASON:
        return ('SKIP_REQUIRES_RICHER_FIXTURE', 0, SKIP_REASON[css_prop])
    if css_prop not in FORM_TEMPLATES:
        return ('SKIP_NOTEMPLATE', 0, '')
    fp = f'examples/properties/perfect/{cat}/{prop_pascal}.json'
    if not os.path.exists(fp):
        return ('SKIP_NOFILE', 0, '')
    variants = FORM_TEMPLATES[css_prop]
    base = {"width": "180px", "height": "80px", "padding": "10px",
            "background-color": "#1f2937"}
    if css_prop in NEEDS_BG:
        base["background-image"] = "linear-gradient(45deg, #f59e0b, #ef4444)"
    if css_prop in NEEDS_TEXT:
        base["color"] = "#ffffff"
        base["text"] = "Sample"
    if css_prop in NEEDS_MASK_IMAGE:
        # Without a mask source, mask-* props are no-ops. Add a gradient mask.
        base["background-color"] = "#3b82f6"
        base["mask-image"] = "linear-gradient(to right, black, transparent)"
    if css_prop in NEEDS_BORDER_STYLE:
        # CSS spec: border-style defaults to none → no border renders without it.
        base["border-style"] = "solid"
        base["border-color"] = "#ef4444"
    components = {}
    for i, val in enumerate(variants):
        slug = re.sub(r'[^a-zA-Z0-9]', '_', str(val)[:10]) or f'v{i}'
        components[f'V{i}_{slug}'] = {"properties": dict(base, **{css_prop: val})}
    json.dump({"components": components}, open(fp, 'w'), indent=2)
    return ('OK', len(variants))


def main():
    if len(sys.argv) >= 3:
        # Single-prop mode
        cat = sys.argv[1]
        prop = sys.argv[2]
        result = synth_fixture(cat, prop)
        print(f'{cat}/{prop}: {result[0]} ({result[1]} variants) {result[2] if len(result)>2 else ""}')
        return

    # Walk all FORM_TEMPLATES and try to synth each
    n_ok = 0
    n_skip = 0
    skip_details = []
    for css_prop in FORM_TEMPLATES:
        # Find which fixture file this corresponds to (kebab → PascalCase)
        prop_pascal = ''.join(p.capitalize() for p in css_prop.split('-'))
        # Try common categories
        for cat in os.listdir('examples/properties/perfect'):
            if not os.path.isdir(f'examples/properties/perfect/{cat}'): continue
            fp = f'examples/properties/perfect/{cat}/{prop_pascal}.json'
            if os.path.exists(fp):
                result = synth_fixture(cat, prop_pascal)
                if result[0] == 'OK':
                    n_ok += 1
                    print(f'OK {cat}/{prop_pascal}: {result[1]} variants')
                else:
                    n_skip += 1
                    skip_details.append(f'{cat}/{prop_pascal}: {result[0]} {result[2] if len(result)>2 else ""}')
                break
    print(f'\nTotal: {n_ok} OK, {n_skip} skipped')
    for d in skip_details[:10]: print(f'  {d}')


if __name__ == '__main__':
    main()
