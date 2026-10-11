# geometry-gate.py refusal / blind paths by execution (mutated expectations under the scratchpad; fake runs from lib).
import json, os, subprocess, copy
from lib import *

E = json.load(open(f'{P55}/expectations.json'))
def wexp(name, f):
    e = copy.deepcopy(E); f(e); p = f'{SCR}/rec/exp-{name}.json'; json.dump(e, open(p, 'w'), indent=1); return p

def gg(args, tag):
    p = subprocess.run(['nice', '-n', '19'] + PY + ['-B', f'{P55}/geometry-gate.py'] + args, cwd=ROOT, capture_output=True, text=True)
    out = (p.stdout + p.stderr).strip().splitlines()
    summ = [l for l in out if l.startswith(('geometry-gate', 'STALE', 'REFUSED', 'usage', 'UNMEASURED GATING', 'revert rule 4'))] or out[-2:]
    print(f'{tag:44} exit {p.returncode} :: ' + ' | '.join(s[:190] for s in summ[:3]))
    return p.returncode

def stale(e): e['probeDecisions'] = {'reverted': [{'lane': 'L1-hyphen-ios-linebox', 'unit': 'NB-ios'}]}
def offi(e): e['lanes']['L5-counter-suffix-rows']['geometryProbes'][0]['cmd'] = e['lanes']['L5-counter-suffix-rows']['geometryProbes'][0]['cmd'].replace('/opt/homebrew/bin/python3.12 -B', 'python3')
def nokc(e): pr = e['lanes']['L3-positioned-parent-slot']['geometryProbes'][0]; pr['keyCells'].pop(next(iter(pr['gating'])))
gg(['wave54-final', '--base', 'wave54-final', '--exp', wexp('stale', stale), '--lanes', 'L1'], 'STALE (probeDecisions reverts NB-ios)')
gg(['wave54-final', '--base', 'wave54-final', '--exp', wexp('offinterp', offi), '--lanes', 'L5'], 'cmd off the pinned interpreter')
gg(['wave54-final', '--base', 'wave54-final', '--exp', wexp('nokeycell', nokc), '--lanes', 'L3'], 'a gating key without keyCells')
gg([], 'usage (no run id)')
gg(['wave54-final', '--base', 'wave54-final', '--lanes', 'L1', '--sections', 'css-text'], 'pre-fix run, L1, --sections css-text')
gg(['wave54-final', '--base', 'wave54-final', '--lanes', 'L1', '--sections', 'css-txt'], '--sections typo css-txt (L1)')
gg(['wave54-final', '--base', 'wave54-final', '--sections', 'css-txt'], '--sections typo css-txt (all lanes)')
gg(['wave54-final', '--base', 'wave54-final', '--lanes', 'L9'], '--lanes typo L9')
gg(['wave54-final', '--base', 'wave54-final', '--lanes', 'L1-hyphen', '--sections', 'css-text'], '--lanes L1-hyphen (prefix)')
# UNMEASURED: the L1 fixed fake run with hyphenate-character-003 ios removed → every other gating key PASSes
rid = make_run('L1-fixed-003gone', {('css-text', 'ios', f'hyphens/hyphenate-character-{t}'): Image.open(f'{P55}/hyphen-ios-linebox-replay/{t}-ios-Rm.png').convert('RGB') for t in ('001', '003', '004')})
os.remove(f'{SCR}/fakeruns/L1-fixed-003gone/sections/css-text/ios-screenshots/wpt__css-text__hyphens__hyphenate-character-003.png')
gg([rid, '--base', 'wave54-final', '--lanes', 'L1', '--sections', 'css-text'], 'gating capture missing (003 ios) → exit 3?')
rid = make_run('L1-fixed-only', {('css-text', 'ios', f'hyphens/hyphenate-character-{t}'): Image.open(f'{P55}/hyphen-ios-linebox-replay/{t}-ios-Rm.png').convert('RGB') for t in ('001', '003', '004')})
gg([rid, '--base', 'wave54-final', '--lanes', 'L1', '--sections', 'css-text'], 'L1 fixed (control: exit 0)')
# a whole section absent from the run: every in-scope key UNMEASURED
import shutil
rd = f'{SCR}/fakeruns/L1-fixed-only-nosec'; shutil.rmtree(rd, ignore_errors=True); shutil.copytree(f'{SCR}/fakeruns/L1-fixed-only', rd, symlinks=True)
shutil.rmtree(f'{rd}/sections/css-text')
gg([os.path.relpath(rd, RUNS), '--base', 'wave54-final', '--lanes', 'L1', '--sections', 'css-text'], 'section absent from the run → exit 3?')
