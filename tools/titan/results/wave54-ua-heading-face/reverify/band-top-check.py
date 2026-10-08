#!/usr/bin/env python3
# Wave 54 L5 RE-VERIFIER — skeptic finding 2 (should-fix): does hunk-for-plan-1 give verdict_inset teeth?
# argv: <patched probe path (hunk applied to a scratch copy)> <fake-png dir>. (1) the patched probe's whole output on
# wave53-final vs the real probe's; (2) the frozen ref with every row from y110 lifted L px (own fakes, L = 0,2,3,4,10,17,21)
# through the PATCHED verdict_inset and, for contrast, the UNPATCHED one.
import io, os, subprocess, sys, contextlib
from PIL import Image
patched_path, fakedir = sys.argv[1], sys.argv[2]; os.makedirs(fakedir, exist_ok=True)
W = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf'
REAL = W + '/tools/titan/results/wave54-plan/ua-heading-face.geometry.py'
a = subprocess.run([sys.executable, REAL, 'wave53-final'], capture_output=True, text=True)
# the patched TEXT is exec'd with __file__ = the real probe path, so geometry_common resolves the run's captures
# exactly as the real probe does (a scratch copy run in place resolves them under the scratch dir → MISSING)
ns, buf, code = {'__file__': REAL, '__name__': '__main__'}, io.StringIO(), 0
sys.argv = [REAL, 'wave53-final']
with contextlib.redirect_stdout(buf):
    try: exec(compile(open(patched_path).read(), REAL, 'exec'), ns)
    except SystemExit as e: code = e.code or 0
print(f'(1) wave53-final: real exit {a.returncode}, patched exit {code}; stdout', 'BYTE-IDENTICAL' if a.stdout == buf.getvalue() else 'DIFFERS', f'({len(a.stdout.splitlines())} lines)')
def module(path):
    ns = {'__file__': path, '__name__': 'probe'}; src = open(path).read().split('CASES = [')[0]
    exec(compile(src, path, 'exec'), ns); return ns
P, U = module(patched_path), module(REAL)
REF = W + '/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-text-decor'
for test, args in [('text-decoration-inset-005', (30, 18, 4)), ('text-decoration-inset-006', (30, 18, 4)), ('text-decoration-inset-014', (20, 20, 6))]:
    im = Image.open(f'{REF}/{test}.png').convert('RGB'); w, h = im.size
    _, rpx = P['load'](f'{REF}/{test}.png'); rb = P['bands'](rpx, 0, w, 110, min(h, 330), P['dark'], gap=2)
    for lift in (0, 2, 3, 4, 10, 17, 21):
        fake = Image.new('RGB', (w, h), (255, 255, 255)); fake.paste(im.crop((0, 0, w, 110)), (0, 0))
        fake.paste(im.crop((0, 110, w, h)), (0, 110 - lift)); p = f'{fakedir}/{test}-lift{lift}.png'; fake.save(p)
        _, fpx = P['load'](p); fb = P['bands'](fpx, 0, w, 110, min(h, 330), P['dark'], gap=2)
        wp, wu = P['verdict_inset'](fb, rb, *args), U['verdict_inset'](fb, rb, *args)
        print(f'(2) {test} lift {lift:>2}: patched → {"OK" if not wp else "WRONG (" + wp + ")"} | unpatched → {"OK" if not wu else "WRONG (" + wu + ")"}')
