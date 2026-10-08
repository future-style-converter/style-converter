#!/usr/bin/env python3
# Plan skeptic r1, check (4b): can a GATING geometry key pass a WRONG picture? Builds four synthetic "runs" under
# skeptic-r1/fake-<variant>/sections/<sec>/<dir>/ (the probes resolve tools/titan/runs/<run>, so the run id is passed as
# ../results/wave54-plan/skeptic-r1/fake-<variant>) whose captures are, for every test a gating key reads, on all three
# platforms: white = a blank canvas; ref = the frozen ref itself (must PASS: the rule describes the asked picture);
# down6 / right6 = the ref translated 6 px (a displaced picture: a geometry rule should say WRONG). Then runs every
# lane's geometry probe through the plan's own geometry-gate.py logic (imported verbatim via subprocess) and prints, per
# gating key, the verdict under each variant. A gating key that PASSES white / down6 / right6 passes a wrong picture.
import os, sys, json, shutil, subprocess, re
from PIL import Image
HERE = os.path.dirname(os.path.abspath(__file__)); PLAN = os.path.dirname(HERE)
ROOT = os.path.abspath(os.path.join(PLAN, '..', '..', '..', '..'))
REFS = f'{ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin'
DIRS = {'web': 'screenshots', 'ios': 'ios-screenshots', 'android': 'android-screenshots'}
TESTS = [('css-text', 'bidi/bidi-lines-001'), ('css-text', 'bidi/bidi-lines-002'), ('css-counter-styles', 'counter-suffix'),
         ('css-lists', 'counter-reset-reversed-nested'),
         ('CSS2', 'css21-errata/s-11-1-1b-006'), ('css-text', 'hyphens/hyphenate-character-001'), ('css-text', 'hyphens/hyphenate-character-003'),
         ('css-text', 'hyphens/hyphenate-character-004'),
         ('CSS2', 'abspos/static-fixed-inside-abspos'), ('css-position', 'position-relative-004'), ('css-position', 'position-relative-003'),
         ('css-position', 'change-insets-inside-strict-containment-nested'), ('css-contain', 'contain-content-003'),
         ('css-contain', 'contain-content-011'), ('filter-effects', 'backdrop-filter-containing-block'),
         ('css-flexbox', 'abspos/abspos-autopos-htb-ltr'), ('css-flexbox', 'abspos/abspos-autopos-vlr-ltr'),
         ('css-flexbox', 'abspos/abspos-autopos-vrl-ltr'), ('css-flexbox', 'abspos/abspos-autopos-htb-rtl'),
         ('css-flexbox', 'abspos/abspos-autopos-vlr-rtl'), ('css-flexbox', 'abspos/abspos-autopos-vrl-rtl'),
         ('filter-effects', 'backdrop-filter-nested-border-radius-clip'),
         ('filter-effects', 'backdrop-filter-nested-border-radius-clip-2'), ('filter-effects', 'backdrop-filter-nested-border-radius-clip-3'),
         ('filter-effects', 'backdrop-filter-nested-border-radius-clip-4'), ('css-gaps', 'flex/flex-gap-decorations-033'),
         ('css-break', 'block-in-inline-015-print'), ('css-text-decor', 'text-decoration-inset-005'), ('css-text-decor', 'text-decoration-inset-006'),
         ('css-text-decor', 'text-decoration-inset-014'), ('css-text-decor', 'text-decoration-inset-011'),
         ('css-ui', 'box-sizing-007'), ('css-ui', 'box-sizing-008'), ('css-ui', 'box-sizing-010'), ('css-ui', 'box-sizing-013'), ('css-ui', 'box-sizing-022'),
         ('css-position', 'position-absolute-semi-replaced-stretch-other'), ('css-position', 'position-absolute-semi-replaced-stretch-input'),
         ('css-text', 'hyphens/hyphens-out-of-flow-002'), ('css-text', 'hyphens/hyphens-out-of-flow-001'), ('css-text', 'hyphens/hyphens-span-002'),
         ('css-text', 'hyphens/hyphens-span-001')]
def shift(im, dx, dy):
    out = Image.new(im.mode, im.size, (255, 255, 255, 255)); out.paste(im, (dx, dy)); return out
VARIANTS = {'white': lambda r: Image.new(r.mode, r.size, (255, 255, 255, 255)), 'ref': lambda r: r.copy(),
            'down6': lambda r: shift(r, 0, 6), 'right6': lambda r: shift(r, 6, 0)}
for v, fn in VARIANTS.items():
    base = os.path.join(HERE, f'fake-{v}')
    shutil.rmtree(base, ignore_errors=True)
    for sec, test in TESTS:
        ref = Image.open(f'{REFS}/{sec}/{test.replace("/", "__")}.png')
        for p, d in DIRS.items():
            dd = os.path.join(base, 'sections', sec, d); os.makedirs(dd, exist_ok=True)
            fn(ref).save(os.path.join(dd, f'wpt__{sec}__{test.replace("/", "__")}.png'))
EXP = json.load(open(os.path.join(PLAN, 'expectations.json')))
norm = lambda s: re.sub(r'\s+', ' ', s.strip())
def find_line(lines, key):   # verbatim from geometry-gate.py
    kt = key.split(); hits = []
    for l in lines:
        t = l.split()
        for i in range(0, min(3, max(0, len(t) - len(kt) + 1))):
            if t[i:i + len(kt)] == kt: hits.append(l); break
    return hits
res = {}
for v in VARIANTS:
    run = f'../results/wave54-plan/skeptic-r1/fake-{v}'
    for lane_id, lane in EXP['lanes'].items():
        for probe in lane.get('geometryProbes', []):
            cmd = probe['cmd'].replace('<run>', run).replace('<base>', 'wave53-final')
            proc = subprocess.run(['nice', '-n', '19'] + cmd.split(), cwd=ROOT, capture_output=True, text=True)
            lines = [norm(l) for l in proc.stdout.splitlines() if l.strip()]
            for key, want in probe['expect'].items():
                hits = find_line(lines, key)
                contains = probe.get('contains', {}).get(key, '').replace('<base>', 'wave53-final')
                if not hits or any('MISSING' in h for h in hits): verdict = 'UNMEAS'
                else: verdict = 'PASS' if hits[-1].endswith(norm(want)) and (not contains or contains in hits[-1]) else 'FAIL'
                res.setdefault((lane_id, os.path.basename(cmd.split()[1]), key, probe.get('gating', {}).get(key)), {})[v] = (verdict, hits[-1][-150:] if hits else '', proc.returncode)
print(f'{"lane":20} {"probe":40} {"key":56} {"gate":12} white  ref    down6  right6')
for (lane, script, key, gate), d in res.items():
    flag = ''
    if gate and any(d[v][0] == 'PASS' for v in ('white', 'down6', 'right6')): flag = '  <== GATING KEY PASSES A WRONG PICTURE'
    print(f'{lane[:20]:20} {script[:40]:40} {key[:56]:56} {str(gate)[:12]:12} ' + ' '.join(f'{d[v][0]:6}' for v in VARIANTS) + flag)
    if flag:
        for v in ('white', 'down6', 'right6'):
            if d[v][0] == 'PASS': print(f'      {v}: {d[v][1]}')
