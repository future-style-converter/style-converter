# Fake POST runs for control-check.mjs's blind / refusal paths (pre = wave54-final). Symlinks to wave54-final except:
import os, shutil
from lib import *


def run_with(fid, edit):
    """Copy-of-symlinks of wave54-final for css-text and filter-effects (others symlinked whole), then edit(rd)."""
    rd = f'{SCR}/ccruns/{fid}'
    if os.path.lexists(rd):
        shutil.rmtree(rd)
    os.makedirs(f'{rd}/sections')
    for sec in sorted(os.listdir(f'{W54}/sections')):
        src = f'{W54}/sections/{sec}'
        if sec not in ('css-text', 'filter-effects'):
            os.symlink(src, f'{rd}/sections/{sec}'); continue
        os.makedirs(f'{rd}/sections/{sec}')
        for sub in sorted(os.listdir(src)):
            s = f'{src}/{sub}'
            if not os.path.isdir(s):
                os.symlink(s, f'{rd}/sections/{sec}/{sub}'); continue
            os.makedirs(f'{rd}/sections/{sec}/{sub}')
            for f in sorted(os.listdir(s)):
                os.symlink(f'{s}/{f}', f'{rd}/sections/{sec}/{sub}/{f}')
    edit(rd)
    return rd


def nudge(p):
    im = Image.open(p).convert('RGB'); a = A(im); a[5, 5] = (a[5, 5] + 37) % 256; os.remove(p); I(a).save(p)


T = 'sections/css-text'
run_with('identity', lambda rd: None)
run_with('no-css-text', lambda rd: shutil.rmtree(f'{rd}/{T}'))
run_with('no-ios-dir', lambda rd: shutil.rmtree(f'{rd}/{T}/ios-screenshots'))
# a NON-carrier capture removed (bidi-lines-002 is an L3 [C] control, not a carrier)
run_with('capture-gone', lambda rd: os.remove(f'{rd}/{T}/android-screenshots/wpt__css-text__bidi__bidi-lines-002.png'))
run_with('leak', lambda rd: nudge(f'{rd}/{T}/android-screenshots/wpt__css-text__bidi__bidi-lines-002.png'))
run_with('carrier', lambda rd: nudge(f'{rd}/{T}/ios-screenshots/wpt__css-text__hyphens__hyphenate-character-001.png'))
run_with('ring', lambda rd: nudge(f'{rd}/sections/filter-effects/android-screenshots/wpt__filter-effects__backdrop-filter-basic-blur.png'))
run_with('wire-doc-gone', lambda rd: os.remove(f'{rd}/{T}/per-test-ir/wpt__css-text__bidi__bidi-lines-002.json'))
print('ok')
