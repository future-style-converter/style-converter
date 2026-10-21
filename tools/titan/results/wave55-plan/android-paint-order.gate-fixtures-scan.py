#!/usr/bin/env python3
# tools/titan/results/wave55-plan/android-paint-order.gate-fixtures-scan.py — the fixture net's exposure to U1 / U2.
#
# Why: the closing gate's fixture net (`BASELINE=1 ./test-all.sh --gate-set`, list tools/visual/gate-fixtures.txt)
# compares Android captures against COMMITTED baselines, so any pixel U1 / U2 moves there is a regression flag. The
# units move a pixel only where a descendant (or the box's own text) paints into a border band, or — U2 — where a
# bordered box carries opacity < 1 (its band moves into the group). This lists, per gate fixture, every component
# whose declarations give it a visible border band (any `border*` width > 0 with a style other than none/hidden),
# with its children, padding, opacity and text, so the exposure is read, not assumed.
# Read-only over the fixture JSON. Usage: python3 android-paint-order.gate-fixtures-scan.py
import json
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
LIST = f'{ROOT}/tools/visual/gate-fixtures.txt'
PX = re.compile(r'(-?\d+(?:\.\d+)?)px')
STYLES = {'solid', 'dashed', 'dotted', 'double', 'groove', 'ridge', 'inset', 'outset'}


def band(props):
    """True when the declarations paint a border band: a px width > 0 together with a visible style."""
    width = style = False
    for k, v in props.items():
        k, v = k.lower(), str(v).lower()
        if not k.startswith('border') or 'radius' in k or 'collapse' in k or 'spacing' in k or 'image' in k:
            continue
        if any(float(n) > 0 for n in PX.findall(v)) and (k.endswith('width') or not k.endswith(('color', 'style'))):
            width = True
        if any(s in v.split() for s in STYLES):
            style = True
    return width and style


def walk(name, node, out):
    """Depth-first over `children` (dict or list), collecting every bordered component."""
    props = node.get('properties') or {}
    kids = node.get('children') or {}
    kids = list(kids.items()) if isinstance(kids, dict) else [(f'{name}[{i}]', k) for i, k in enumerate(kids)]
    if isinstance(props, dict) and band(props):
        out.append((name, len(kids), props.get('padding'), props.get('opacity'), bool(node.get('text'))))
    for kn, k in kids:
        if isinstance(k, dict):
            walk(kn, k, out)


for line in open(LIST):
    f = line.split('#')[0].strip()
    if not f:
        continue
    comps = json.load(open(f'{ROOT}/{f}')).get('components', {})
    out = []
    for n, c in (comps.items() if isinstance(comps, dict) else enumerate(comps)):
        if isinstance(c, dict):
            walk(str(n), c, out)
    with_kids = [o for o in out if o[1]]
    with_op = [o for o in out if o[3] not in (None, '1', 1)]
    print(f'{f}: bordered {len(out)} · with children {len(with_kids)} · with opacity {len(with_op)} · with text {sum(o[4] for o in out)}')
    for o in with_kids + [o for o in with_op if o not in with_kids]:
        print(f'    {o[0]}  children {o[1]}  padding {o[2]}  opacity {o[3]}')
