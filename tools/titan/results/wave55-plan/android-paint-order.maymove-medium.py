#!/usr/bin/env python3
# tools/titan/results/wave55-plan/android-paint-order.maymove-medium.py — the L6 may-move census with Compose's MEDIUM
# default honoured (plan-skeptic round 1, generator-truth M2; PLAN §9 D21). Read-only over one run's per-test IR and
# captures; writes nothing (its stdout is android-paint-order.maymove-medium.out.txt, which plan-build.py reads).
#
# Why: android-paint-order.crossings-scan.py's structural_docs() (the brief's may-move source, 252 documents) counts a
# border band only when Border*Width.px > 0. Compose paints more than that: BorderSideExtractor.extractBorderConfig
# (runtimes/compose/…/borders/sides/BorderSideExtractor.kt) resolves each side from its physical OR logical longhand,
# falls back to the BorderWidth / BorderStyle shorthands, reads width keywords (ValueExtractors.extractBorderWidth:
# thin 1 / medium 3 / thick 5), and applyMediumDefault (:158-171, css-backgrounds-3 §4.3 "border-width initial value:
# medium") paints a side with a VISIBLE style and NO width at 3 dp — on the painting path (BordersFacade.kt:72,
# StyleApplier.kt:879), i.e. exactly the legacy band L6 U2 moves behind content. A document whose bordered component
# has a non-<br> child or its own text is may-move under U2 whether its band width was written or defaulted.
# Prints:
#   (1) the brief's explicit-px rule over every document — must reproduce the crossings scan's 252 stems (control arm);
#   (2) the Compose rule (medium default + keywords + shorthands + logical sides) — the may-move documents;
#   (3) the documents the Compose rule adds (the may-move additions plan-build.py reads, one stem per line under
#       "MEDIUM-DEFAULT ADDITIONS") and any it drops;
#   (4) the crossing excess (android − ref, web − ref, ios − ref) of every addition, read with the scan's own counter:
#       a capture carrier needs android excess ≥ 20 with web / iOS within 5 — the additions must hold none.
# Usage: /opt/homebrew/bin/python3.12 -B tools/titan/results/wave55-plan/android-paint-order.maymove-medium.py [run-id=wave54-final]
import glob, json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
run = sys.argv[1] if len(sys.argv) > 1 else 'wave54-final'
# The crossings scan's own pixel counter and paths (everything above its scan loop: no capture is read at import).
_src = open(os.path.join(HERE, 'android-paint-order.crossings-scan.py')).read().split('\nrows = []', 1)[0]
scan = {'__file__': os.path.join(HERE, 'android-paint-order.crossings-scan.py')}
sys_argv, sys.argv = sys.argv, [sys.argv[0], run]
exec(compile(_src, 'android-paint-order.crossings-scan.py', 'exec'), scan)
sys.argv = sys_argv
RUN, REFS, DIRS, crossings, explicit_docs = scan['RUN'], scan['REFS'], scan['DIRS'], scan['crossings'], scan['structural_docs']

SIDES = {'top': ('BorderTop', 'BorderBlockStart'), 'end': ('BorderRight', 'BorderInlineEnd'),
         'bottom': ('BorderBottom', 'BorderBlockEnd'), 'start': ('BorderLeft', 'BorderInlineStart')}
KEYWORD_PX = {'thin': 1.0, 'medium': 3.0, 'thick': 5.0}       # ValueExtractors.extractBorderWidth (css-backgrounds-3 §4.3)


def width_px(data):
    """ValueExtractors.extractBorderWidth: a number, a {px|pixels} object, a width keyword (bare or typed), else None."""
    if data is None:
        return None
    if isinstance(data, (int, float)):
        return float(data)
    if isinstance(data, str):
        return KEYWORD_PX.get(data.lower())
    if isinstance(data, dict):
        for k in ('px', 'pixels'):
            if isinstance(data.get(k), (int, float)):
                return float(data[k])
        if data.get('type') == 'keyword' and isinstance(data.get('value'), str):
            return KEYWORD_PX.get(data['value'].lower())
    return None


def style_of(data):
    """ValueExtractors.extractLineStyle: the keyword, upper-cased (None when absent)."""
    if isinstance(data, str):
        return data.upper()
    if isinstance(data, dict) and isinstance(data.get('value'), str):
        return data['value'].upper()
    return None


def compose_band(props):
    """True when BorderSideExtractor resolves ≥ 1 side to a visible style with a width > 0 (medium default honoured)."""
    side = {k: {'w': None, 's': None, 'wset': False} for k in SIDES}
    shared_w = shared_s = None
    for t, d in props:                                   # declaration order: the last longhand wins, as in the Kotlin loop
        if t == 'BorderWidth':
            shared_w = width_px(d)
        elif t == 'BorderStyle':
            shared_s = style_of(d)
        for k, names in SIDES.items():
            if t in (names[0] + 'Width', names[1] + 'Width'):
                side[k]['w'] = width_px(d)
            elif t in (names[0] + 'Style', names[1] + 'Style'):
                side[k]['s'] = style_of(d)
    for k in SIDES:
        w = side[k]['w'] if side[k]['w'] is not None else shared_w
        s = side[k]['s'] if side[k]['s'] is not None else shared_s
        if w is None and s is not None and s not in ('NONE', 'HIDDEN'):
            w = 3.0                                      # applyMediumDefault: a visible style with no width paints 3 dp
        if s is not None and s not in ('NONE', 'HIDDEN') and w and w > 0:
            return True
    return False


def compose_docs():
    """(section, stem) of every document with a Compose-banded component that has a non-<br> child or its own text."""
    out = []
    for f in sorted(glob.glob(f'{RUN}/*/per-test-ir/*.json')):
        comps = json.load(open(f)).get('components', [])
        kids = {}
        for c in comps:
            p = (c.get('slot') or {}).get('parent')
            if p and (c.get('meta') or {}).get('role') != 'line-break':
                kids.setdefault(p, []).append(c)
        for c in comps:
            props = [(p['type'], p.get('data')) for p in c.get('properties', [])]
            if compose_band(props) and (kids.get(c['id']) or (c.get('text') or '').strip()):
                out.append((f.split('/')[-3], os.path.basename(f)[:-5]))
                break
    return out


def n_bordered(rule):
    """Bordered components (any child or none) under the explicit rule ('explicit') or Compose's ('compose')."""
    n = 0
    for f in sorted(glob.glob(f'{RUN}/*/per-test-ir/*.json')):
        for c in json.load(open(f)).get('components', []):
            props = [(p['type'], p.get('data')) for p in c.get('properties', [])]
            if rule == 'compose':
                n += compose_band(props)
            else:
                P = dict(props)
                n += any(isinstance(P.get(f'Border{s}Width'), dict) and (P[f'Border{s}Width'].get('px') or 0) > 0
                         and P.get(f'Border{s}Style') not in (None, 'NONE', 'HIDDEN') for s in ('Top', 'Right', 'Bottom', 'Left'))
    return n


exp, com = set(explicit_docs()), set(compose_docs())
print(f'# android-paint-order.maymove-medium.py {run} (plan-skeptic round 1 GT-M2; PLAN §9 D21)')
print(f'(1) the brief\'s explicit-px rule (crossings-scan structural_docs): {len(exp)} documents; bordered components {n_bordered("explicit")}')
print(f'(2) the Compose rule (medium default, keywords, shorthands, logical sides): {len(com)} documents; bordered components {n_bordered("compose")}')
add, drop = sorted(com - exp), sorted(exp - com)
print(f'(3) the Compose rule adds {len(add)} documents and drops {len(drop)}')
print('MEDIUM-DEFAULT ADDITIONS')
for sec, st in add:
    print(f'  {st}')
print('END ADDITIONS')
for sec, st in drop:
    print(f'  dropped (explicit rule only): {st}')
print('(4) crossing excess of each addition (android / web / ios − ref; a carrier needs android ≥ 20 with web / ios within 5):')
carriers = 0
for sec, st in add:
    test = st.split('__', 2)[2] if st.count('__') >= 2 else st
    ref = crossings(f'{REFS}/{sec}/{st[len("wpt__" + sec + "__"):]}.png')
    caps = {p: crossings(f'{RUN}/{sec}/{d}/{st}.png') for p, d in DIRS.items()}
    ex = {p: (None if v is None or ref is None else v - ref) for p, v in caps.items()}
    car = ex['android'] is not None and ex['android'] >= 20 and all(ex[p] is not None and abs(ex[p]) <= 5 for p in ('web', 'ios'))
    carriers += car
    print(f'  {st:78} ref {ref} · android {ex["android"]} · web {ex["web"]} · ios {ex["ios"]}' + ('  CARRIER' if car else ''))
print(f'carriers among the additions: {carriers}')
