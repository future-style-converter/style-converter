#!/usr/bin/env python3
"""Hand-templated CSS function-syntax fixture synthesizer.

Auditor round 3 caught ETERNAL emitting bare keywords (e.g. `filter: blur`)
for function-valued properties, producing false-PASS via identical parse
failure on all platforms.

This file holds curated FUNCTION_TEMPLATES per known function-valued CSS
property. Run once to (re)generate proper fixtures for all of them.

Usage: python3 testing/loops/synth_function_fixtures.py [--dry-run]
"""
import os, json, sys

os.chdir('/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/jovial-shockley-b4adce')

# Each entry: (relative-fixture-path, base-style, list-of-(name, css-value-string))
TEMPLATES = {
    'examples/properties/perfect/color/Filter.json': (
        {"width": "180px", "height": "80px", "padding": "10px", "background-color": "#3b82f6"},
        'filter',
        [('none', 'none'),
         ('blur', 'blur(2px)'),
         ('brightness', 'brightness(1.2)'),
         ('contrast', 'contrast(1.5)'),
         ('grayscale', 'grayscale(0.7)'),
         ('hue', 'hue-rotate(45deg)'),
         ('invert', 'invert(0.5)'),
         ('opacity', 'opacity(0.7)'),
         ('saturate', 'saturate(1.5)'),
        ],
    ),
    'examples/properties/perfect/effects/BackdropFilter.json': (
        {"width": "180px", "height": "80px", "padding": "10px", "background-color": "rgba(255,255,255,0.3)"},
        'backdrop-filter',
        [('none', 'none'),
         ('blur', 'blur(4px)'),
         ('brightness', 'brightness(1.5)'),
         ('grayscale', 'grayscale(50%)'),
        ],
    ),
    'examples/properties/perfect/effects/ClipPath.json': (
        {"width": "180px", "height": "80px", "padding": "10px", "background-color": "#3b82f6"},
        'clip-path',
        [('none', 'none'),
         ('inset', 'inset(10px)'),
         ('circle', 'circle(40px)'),
         ('ellipse', 'ellipse(60px 30px)'),
        ],
    ),
    'examples/properties/perfect/transforms/Transform.json': (
        {"width": "120px", "height": "60px", "padding": "10px", "background-color": "#3b82f6"},
        'transform',
        [('none', 'none'),
         ('translate', 'translate(10px, 5px)'),
         ('translateX', 'translateX(15px)'),
         ('translateY', 'translateY(8px)'),
         ('rotate', 'rotate(45deg)'),
         ('scale', 'scale(1.2)'),
         ('scaleXY', 'scale(1.2, 0.8)'),
        ],
    ),
}

dry = '--dry-run' in sys.argv
n=0
for fp, (base, prop, variants) in TEMPLATES.items():
    components = {}
    for name, val in variants:
        comp_key = f'V_{name}'
        components[comp_key] = {"properties": dict(base, **{prop: val})}
    if not dry:
        with open(fp, 'w') as f:
            json.dump({"components": components}, f, indent=2)
    print(f'{fp}: {len(variants)} variants ({"would write" if dry else "wrote"})')
    n+=1
print(f'\nTotal: {n} fixtures synthesized')
