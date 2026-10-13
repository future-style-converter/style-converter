import os, shutil, sys
import numpy as np
from PIL import Image
R = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf'
W54 = f'{R}/tools/titan/runs/wave54-final'
SCR = os.path.dirname(os.path.abspath(__file__))
def run_with(fid, edit, secs=('css-text', 'filter-effects')):
    rd = f'{SCR}/runs/{fid}'
    if os.path.lexists(rd): shutil.rmtree(rd)
    os.makedirs(f'{rd}/sections')
    for sec in sorted(os.listdir(f'{W54}/sections')):
        src = f'{W54}/sections/{sec}'
        if sec not in secs: os.symlink(src, f'{rd}/sections/{sec}'); continue
        os.makedirs(f'{rd}/sections/{sec}')
        for sub in sorted(os.listdir(src)):
            s = f'{src}/{sub}'
            if not os.path.isdir(s): os.symlink(s, f'{rd}/sections/{sec}/{sub}'); continue
            os.makedirs(f'{rd}/sections/{sec}/{sub}')
            for f in sorted(os.listdir(s)): os.symlink(f'{s}/{f}', f'{rd}/sections/{sec}/{sub}/{f}')
    edit(rd)
def nudge(p):
    a = np.asarray(Image.open(p).convert('RGB')).copy(); a[5, 5] = (a[5, 5].astype(int) + 37) % 256; os.remove(p); Image.fromarray(a).save(p)
T = 'sections/css-text'; F = 'sections/filter-effects'
run_with('identity', lambda rd: None)
run_with('carrier-gone', lambda rd: os.remove(f'{rd}/{T}/ios-screenshots/wpt__css-text__hyphens__hyphenate-character-001.png'))
run_with('carrier-wire-gone', lambda rd: os.remove(f'{rd}/{T}/per-test-ir/wpt__css-text__hyphens__hyphenate-character-001.json') if os.path.exists(f'{rd}/{T}/per-test-ir/wpt__css-text__hyphens__hyphenate-character-001.json') else None)
run_with('ring-gone', lambda rd: os.remove(f'{rd}/{F}/android-screenshots/wpt__filter-effects__backdrop-filter-basic-blur.png'))
run_with('nonpng', lambda rd: (os.remove(f'{rd}/{T}/android-screenshots/wpt__css-text__bidi__bidi-lines-002.png'), open(f'{rd}/{T}/android-screenshots/wpt__css-text__bidi__bidi-lines-002.png', 'w').write('not a png')))
run_with('new-capture', lambda rd: shutil.copy(f'{W54}/{T}/android-screenshots/wpt__css-text__bidi__bidi-lines-002.png', f'{rd}/{T}/android-screenshots/wpt__css-text__bidi__bidi-lines-999.png'))
run_with('numbered-name', lambda rd: shutil.copy(f'{W54}/{T}/android-screenshots/wpt__css-text__bidi__bidi-lines-002.png', f'{rd}/{T}/android-screenshots/wpt__css-text__bidi__002.png'))
run_with('no-platform-dirs', lambda rd: [shutil.rmtree(f'{rd}/{T}/{d}') for d in ('screenshots', 'ios-screenshots', 'android-screenshots')])
run_with('no-wire-dir', lambda rd: shutil.rmtree(f'{rd}/{T}/per-test-ir'))
run_with('leak', lambda rd: nudge(f'{rd}/{T}/android-screenshots/wpt__css-text__bidi__bidi-lines-002.png'))
print('ok')
