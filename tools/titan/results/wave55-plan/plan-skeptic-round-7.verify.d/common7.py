#!/usr/bin/env python3
# common7.py — round-7 verifier plumbing: the fixed pictures per cell (the pictures the plan's standing set registers as each
# key's fixed picture), the residual declarations, and the real-gate runner on symlink-tree fake runs (--base wave55-open).
import os, sys, json, subprocess, importlib.util, shutil
import numpy as np
from PIL import Image
TREE = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf'
PLAN = f'{TREE}/tools/titan/results/wave55-plan'
RUNS = f'{TREE}/tools/titan/runs'
PY = ['/opt/homebrew/bin/python3.12', '-B']
BASE = 'wave55-open'
def load(name, path):
    s = importlib.util.spec_from_file_location(name, path); m = importlib.util.module_from_spec(s); s.loader.exec_module(m); return m
RR = load('residual_read', f'{PLAN}/residual-read.py')
PT = load('probes_teeth', f'{PLAN}/probes-teeth.py')
EXP = json.load(open(f'{PLAN}/expectations.json'))
A = lambda im: np.asarray(im.convert('RGB')).astype(np.int16).copy()
png = lambda rel: A(Image.open(os.path.join(PLAN, rel)))
def cellkey(sec, plat, test):
    return f'{sec}/{test}.html {plat}'
# keys per cell (gating + tally) and tally entries
TALLY = [(part, e) for part in ('full', 'part', 'paintOrderOnly') for e in EXP['pictureCorrectTally'].get(part, [])]
TALLY_KEYS = {tuple(k.split(' ', 1)) for _, e in TALLY for k in e['keys']}
KEYS = {}    # cell -> [(lane, script, key, class)]
for lid, l in EXP['lanes'].items():
    for pr in l['geometryProbes']:
        s = os.path.basename(next(t for t in pr['cmd'].split() if t.endswith('.py')))
        for k in pr['expect']:
            g = pr.get('gating', {}).get(k)
            if g or (s, k) in TALLY_KEYS:
                KEYS.setdefault(pr['keyCells'][k], []).append((lid.split('-')[0], s, k, 'gating' if g else 'tally'))
TALLY_BY_CELL = {e['cell']: [tuple(k.split(' ', 1)) for k in e['keys']] for _, e in TALLY}
def l5_ctx():
    OK = 'counter-suffix-rows-replay/ok-all'; r = {}
    for p in ('web', 'ios', 'android'):
        r[('css-counter-styles', p, 'counter-suffix')] = png(f'{OK}/counter-suffix.{p}.png')
        r[('css-counter-styles', p, 'cjk-decimal/css3-counter-styles-005')] = png(f'{OK}/cjk-decimal__css3-counter-styles-005.{p}.png')
    return r
def fixed_sets():
    """[(lane, setname, sections, repl {(sec, plat, test): array}, the cells under test in it)] — the standing set's fixed cases."""
    out = []
    out.append(('L1', 'Rm', 'css-text', {('css-text', 'ios', f'hyphens/hyphenate-character-{t}'): png(f'hyphen-ios-linebox-replay/{t}-ios-Rm.png') for t in ('001', '003', '004')}))
    out.append(('L2', 'U1', 'css-flexbox,filter-effects', {(sec, 'android', t): PT.cap('wave54-probe', sec, t, 'android') for sec, t in PT.L2_U1}))
    out.append(('L2', 'U2', 'filter-effects', {('filter-effects', 'android', 'backdrop-filter-nested-border-radius-clip-3'): png('cbb-android-replay/android-clip-3.png')}))
    out.append(('L3', 'fixed', 'selectors,css-text,css-anchor-position,css-writing-modes',
                {('selectors', 'android', 'dir-style-02a'): PT.l3_fixed('dir-style-02a'), ('css-text', 'android', 'bidi/bidi-tab-001'): PT.l3_fixed('bidi-tab-001'),
                 ('css-anchor-position', 'android', 'anchor-position-multicol-nested-001'): PT.l3_fixed('anchor-position-multicol-nested-001'),
                 **{('css-writing-modes', 'android', d): PT.l3_fixed(d) for d in ('dynamic-offset-rtl-001', 'dynamic-offset-rtl-002', 'dynamic-offset-vrl-rtl-001')}}))
    for arm in ('C', 'U1'):
        out.append(('L4', arm, 'css-text-decor', {('css-text-decor', p, t): png(f'ua-heading-margin-replay/{t}-{p}-{arm}.png') for t in PT.L4_T for p in ('ios', 'android')}))
    out.append(('L5', 'ok-all', 'css-counter-styles', l5_ctx()))
    out.append(('L6', 'X-U2', 'css-flexbox,css-grid', {(sec, 'android', t): png(f'android-paint-order-replay/{t.replace("/", "__")}-android-U2.png') for sec, t, fl in PT.L6_X}))
    out.append(('L6', 'PO-U1', 'filter-effects', {('filter-effects', 'android', 'backdrop-filter-clip-rect'): png('android-paint-order-replay/backdrop-filter-clip-rect-android-U1.png')}))
    return out
def base_arr(cell):
    return RR.rgb(RR.capture_path(BASE, cell))
def make_run(scratch, fid, repl):
    return PT.make_run(scratch, fid, repl)
def gate(run_rel, lane, sections, out_json):
    p = subprocess.run(['nice', '-n', '19'] + PY + [f'{PLAN}/geometry-gate.py', run_rel, '--base', BASE, '--lanes', lane, '--sections', sections,
                        '--json', out_json], cwd=TREE, capture_output=True, text=True)
    rows = json.load(open(out_json))['rows'] if os.path.exists(out_json) else []
    return p.returncode, {(r['script'], r['key']): r for r in rows}, p.stdout
