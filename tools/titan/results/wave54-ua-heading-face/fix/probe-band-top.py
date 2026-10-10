#!/usr/bin/env python3
# Wave 54 lane L5 FIX PASS — verifies hunk-for-plan-1.patch (the band-top check in verdict_inset) WITHOUT touching
# wave54-plan/: the patched probe text (fix/ua-heading-face.geometry.patched.py.txt = the hunk applied to HEAD's probe)
# is exec'd with __file__ set to the real probe path (so its geometry_common import resolves), then
#   (1) on wave53-final its whole output must equal the UNPATCHED probe's output byte-for-byte (every verdict and line
#       unchanged → geometry-gate.py --self-test unchanged);
#   (2) the skeptic's fakes (the frozen ref with every row from y110 lifted 0/10/21/24 px) must print OK at lift 0 and
#       WRONG with a `band top` reason at 10/21/24 (they all printed OK before: skeptic/probe-teeth.out.txt).
import contextlib, io, os, subprocess, sys
from PIL import Image
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, *(['..'] * 5)))
PROBE = os.path.join(ROOT, 'tools/titan/results/wave54-plan/ua-heading-face.geometry.py')
PATCHED = open(os.path.join(HERE, 'ua-heading-face.geometry.patched.py.txt')).read()
def run_patched(run):
    ns = {'__file__': PROBE, '__name__': '__main__'}; sys.argv = [PROBE, run]; buf = io.StringIO()
    with contextlib.redirect_stdout(buf):
        try: exec(compile(PATCHED, PROBE, 'exec'), ns)
        except SystemExit as e: code = e.code
        else: code = 0
    return buf.getvalue(), code
orig = subprocess.run([sys.executable, PROBE, 'wave53-final'], capture_output=True, text=True)
new, code = run_patched('wave53-final')
print(f'(1) wave53-final: unpatched exit {orig.returncode}, patched exit {code}; output',
      'BYTE-IDENTICAL' if new == orig.stdout else 'DIFFERS')
if new != orig.stdout:
    for a, b in zip(orig.stdout.splitlines(), new.splitlines()):
        if a != b: print('   -', a, '\n   +', b)
# (2) the skeptic's lifted-ref fakes through the PATCHED verdict_inset / bands
ns = {'__file__': PROBE}; sys.argv = [PROBE, 'wave53-final']
exec(compile(PATCHED.split('CASES = [')[0], PROBE, 'exec'), ns)
REF = os.path.join(ROOT, 'tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-text-decor')
for test, (h1, h2, tol) in [('text-decoration-inset-005', (30, 18, 4)), ('text-decoration-inset-006', (30, 18, 4)), ('text-decoration-inset-014', (20, 20, 6))]:
    im = Image.open(os.path.join(REF, test + '.png')).convert('RGB'); w, h = im.size
    _, rpx = ns['load'](os.path.join(REF, test + '.png'))
    rb = ns['bands'](rpx, 0, w, 110, min(h, 330), ns['dark'], gap=2)
    for lift in (0, 10, 21, 24):
        fake = Image.new('RGB', (w, h), (255, 255, 255))
        fake.paste(im.crop((0, 0, w, 110)), (0, 0)); fake.paste(im.crop((0, 110, w, h)), (0, 110 - lift))
        p = os.path.join(HERE, 'png', f'fake-{test}-lift{lift}.png'); os.makedirs(os.path.dirname(p), exist_ok=True); fake.save(p)
        _, fpx = ns['load'](p)
        fb = ns['bands'](fpx, 0, w, 110, min(h, 330), ns['dark'], gap=2)
        why = ns['verdict_inset'](fb, rb, h1, h2, tol)
        print(f'(2) {test} lift {lift:>2}px: bands {fb} → ' + ('GEOMETRY OK' if not why else f'GEOMETRY WRONG ({why})'))
