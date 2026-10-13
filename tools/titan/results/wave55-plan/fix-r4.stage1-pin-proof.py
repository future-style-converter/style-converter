#!/usr/bin/env python3
# tools/titan/results/wave55-plan/fix-r4.stage1-pin-proof.py — proves the round-4 stage-1 mechanism (PLAN §7 stage 1 step 8, D39):
# FACE_RUN (ua-heading-margin.geometry.py --face-run) and TWIN_RUN (counter-suffix-rows.geometry.py --twin-run) make a key read a
# DECIDED device picture instead of another rasterizer's, and keep their teeth there. A stand-in "device run" (a symlink-tree
# fake run of wave54-final under --scratch, probes-teeth.make_run) holds pictures that are NOT the frozen ref / Android's '1':
#   L4: the C-arm heading with its light antialiasing (min channel 200-249) darkened to grey 190 — another rasterizer's AA on
#       the same glyph cores (dark components and the column profile unchanged, `face xor:` vs the ref > 2, its only reason);
#       read as the capture → WRONG against the ref, OK with --face-run <device run>; + a 2×2 blob beside a glyph → WRONG
#       with --face-run too.
#   L5: ok-all iOS with the REF's tabular '1' in the digit window (a "device '1'" that is not Android's: XOR 22) → [T] ios WRONG
#       without, OK with --twin-run <device run>; + a 2×2 blob beside it → WRONG with --twin-run.
# Writes only under --scratch. Usage: python3 fix-r4.stage1-pin-proof.py --scratch <abs dir>   (from the repo root)
import importlib.util, os, re, subprocess, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__)); ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
SC = sys.argv[sys.argv.index('--scratch') + 1]; assert os.path.isabs(SC)
PY = ['/opt/homebrew/bin/python3.12', '-B']
_s = importlib.util.spec_from_file_location('pt', os.path.join(HERE, 'probes-teeth.py')); pt = importlib.util.module_from_spec(_s); _s.loader.exec_module(pt)
def probe(args):
    return subprocess.run(PY + [os.path.join(HERE, args[0])] + args[1:], cwd=ROOT, capture_output=True, text=True).stdout.splitlines()
def show(lines, pat):
    for l in lines:
        if re.match(pat, l):
            l = re.sub(r' bands \[[^]]*\]| row\d:\S+| y\d+:marker[^)]*\)[^y]*', '', l)
            print('    ' + re.sub(r'\s+', ' ', l)[:210])
fails = 0
def want(lines, pat, verdict, label):
    global fails
    hit = [l for l in lines if re.match(pat, l)]
    ok = bool(hit) and all(l.rstrip().endswith('GEOMETRY OK') == (verdict == 'OK') for l in hit)
    fails += not ok
    print(f'  {"as required" if ok else "!! NOT as required"}  {label}: {len(hit)} line(s) {verdict}')

print('# fix-r4.stage1-pin-proof.out.txt — the FACE_RUN / TWIN_RUN stage-1 mechanism, proven on stand-in device runs (scratch fake runs)')
T3 = ['text-decoration-inset-005', 'text-decoration-inset-006', 'text-decoration-inset-014']
C = {('css-text-decor', p, t): pt.png(f'ua-heading-margin-replay/{t}-{p}-C.png') for t in T3 for p in ('ios', 'android')}
def nudge(a, t):        # the heading's light AA darkened (another rasterizer's antialiasing on the same glyph cores)
    o = a.copy(); m = np.zeros(a.shape[:2], bool); m[96:400] = True; aa = (a.min(axis=2) >= 200) & (a.min(axis=2) < 250) & m
    o[aa] = 190; return o
dev = {k: nudge(v, k[2]) for k, v in C.items()}
def blob(a, t):         # a 2×2 blob beside line 1's first glyph
    o = a.copy(); _, _, b = pt.l4_region(a, t); top, bot, x0, x1 = b[0]; o[top + 5:top + 7, x0 - 2:x0] = 0; return o
dev_run = pt.make_run(SC, 'stage1-pin-L4-device', dev)
cap_run = pt.make_run(SC, 'stage1-pin-L4-capture', dev)
bad_run = pt.make_run(SC, 'stage1-pin-L4-capture-blob', {k: blob(v, k[2]) for k, v in dev.items()})
pat = r'\[(M|F)\] text-decoration-inset-0(05|06|14) +(ios|android) '
print('\n## L4 — the stand-in device face as the capture')
l = probe(['ua-heading-margin.geometry.py', cap_run, '--base', 'wave54-final']); show(l, pat)
want(l, pat, 'WRONG', 'against the frozen ref (FACE_RUN None): another rasterizer\'s AA — `face xor:` its only reason')
l = probe(['ua-heading-margin.geometry.py', cap_run, '--base', 'wave54-final', '--face-run', dev_run]); show(l, pat)
want(l, pat, 'OK', '--face-run <the decided device run>: the same picture reads XOR 0')
l = probe(['ua-heading-margin.geometry.py', bad_run, '--base', 'wave54-final', '--face-run', dev_run]); show(l, pat)
want(l, pat, 'WRONG', '--face-run <device run>, capture + a 2×2 blob beside line 1\'s first glyph: still rejected (xor 8)')

print('\n## L5 — a stand-in device tabular \'1\' (the ref\'s, XOR 22 from Android\'s) in ok-all iOS\'s digit window')
OK = 'counter-suffix-rows-replay/ok-all'
ref = pt.A(__import__('PIL.Image', fromlist=['Image']).open(f'{pt.REFS}/css-counter-styles/counter-suffix.png'))
ios = pt.png(f'{OK}/counter-suffix.ios.png'); d1 = ios.copy(); d1[208:232, 136:151] = ref[208:232, 136:151]
X = ('css-counter-styles', 'ios', 'counter-suffix')
dev5 = pt.make_run(SC, 'stage1-pin-L5-device', {X: d1})
cap5 = pt.make_run(SC, 'stage1-pin-L5-capture', {X: d1})
b5 = d1.copy(); ys, xs = np.nonzero(b5[208:232, 136:151].min(axis=2) < 200); b5[208 + int(np.median(ys)):210 + int(np.median(ys)), 136 + xs.max() + 2:138 + xs.max() + 2] = 0
bad5 = pt.make_run(SC, 'stage1-pin-L5-capture-blob', {X: b5})
pat5 = r'\[T\] counter-suffix ios '
l = probe(['counter-suffix-rows.geometry.py', cap5, 'wave54-final']); show(l, pat5)
want(l, pat5, 'WRONG', '[T] ios against Android\'s \'1\' (TWIN_RUN None): another rasterizer\'s glyph')
l = probe(['counter-suffix-rows.geometry.py', cap5, 'wave54-final', '--twin-run', dev5]); show(l, pat5)
want(l, pat5, 'OK', '--twin-run <the decided device run>: the same \'1\' reads 0')
l = probe(['counter-suffix-rows.geometry.py', bad5, 'wave54-final', '--twin-run', dev5]); show(l, pat5)
want(l, pat5, 'WRONG', '--twin-run <device run>, + a 2×2 blob beside the \'1\': still rejected')
print(f'\nstage-1 pin proof: {"every case as required" if not fails else f"{fails} case(s) NOT as required"}')
sys.exit(1 if fails else 0)
