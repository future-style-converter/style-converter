# teeth/lib.py — plan-skeptic instrument-teeth (wave 55, round 1): build FAKE RUNS under the scratchpad and read them
# through the REAL tools/titan/results/wave55-plan/geometry-gate.py (the generated expectations.json, the pinned
# interpreter), never by re-implementing a probe. A fake run is a directory of symlinks to tools/titan/runs/wave54-final
# with only the faked captures replaced; its run id is the relative path from tools/titan/runs/ (every probe resolves
# f'{ROOT}/tools/titan/runs/{run}/sections/...'). Nothing is written outside the scratchpad.
import json, os, shutil, subprocess
import numpy as np
from PIL import Image

ROOT = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf'
SCR = os.path.dirname(os.path.abspath(__file__))
RUNS = f'{ROOT}/tools/titan/runs'
W54 = f'{RUNS}/wave54-final'
P55 = f'{ROOT}/tools/titan/results/wave55-plan'
REFS = f'{ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin'
DIRS = {'web': 'screenshots', 'ios': 'ios-screenshots', 'android': 'android-screenshots'}
PY = ['/opt/homebrew/bin/python3.12', '-B']
# fakes and fake runs are written beside this file: never run it from inside the repository (copy the .d/ dir to a scratch dir)
assert not (SCR + '/').startswith(ROOT + '/'), f'{SCR} lies inside the repository: copy this directory to a scratch dir and run it there'


def cap_name(sec, test):
    return f'wpt__{sec}__{test.replace("/", "__")}.png'


def cap(run, sec, test, plat):
    return Image.open(f'{RUNS}/{run}/sections/{sec}/{DIRS[plat]}/{cap_name(sec, test)}').convert('RGB')


def ref(sec, test):
    return Image.open(f'{REFS}/{sec}/{test.replace("/", "__")}.png').convert('RGB')


def A(im):
    return np.asarray(im.convert('RGB')).astype(np.int16).copy()


def I(a):
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), 'RGB')


def make_run(fid, repl):
    """repl: {(sec, plat, test): PIL image}. Returns the run id (relative to tools/titan/runs/)."""
    rd = f'{SCR}/fakeruns/{fid}'
    if os.path.lexists(rd):
        shutil.rmtree(rd)
    os.makedirs(f'{rd}/sections')
    touched = {}
    for (sec, plat, test), im in repl.items():
        touched.setdefault(sec, {}).setdefault(DIRS[plat], {})[cap_name(sec, test)] = im
    os.makedirs(f'{SCR}/fakes/{fid}', exist_ok=True)
    for sec in sorted(os.listdir(f'{W54}/sections')):
        src = f'{W54}/sections/{sec}'
        if sec not in touched:
            os.symlink(src, f'{rd}/sections/{sec}'); continue
        os.makedirs(f'{rd}/sections/{sec}')
        for sub in sorted(os.listdir(src)):
            s = f'{src}/{sub}'
            if sub not in touched[sec]:
                os.symlink(s, f'{rd}/sections/{sec}/{sub}'); continue
            os.makedirs(f'{rd}/sections/{sec}/{sub}')
            for f in sorted(os.listdir(s)):
                if f not in touched[sec][sub]:
                    os.symlink(f'{s}/{f}', f'{rd}/sections/{sec}/{sub}/{f}')
            for f, im in touched[sec][sub].items():
                im.save(f'{rd}/sections/{sec}/{sub}/{f}')
                im.save(f'{SCR}/fakes/{fid}/{sub}__{f}')          # a flat copy to look at
    return os.path.relpath(rd, RUNS)


def gate(run_rel, lanes, sections, tag):
    """The real geometry-gate.py on the fake run; returns (exit, {(script, key): row})."""
    out = f'{SCR}/out/{tag}.json'
    os.makedirs(f'{SCR}/out', exist_ok=True)
    p = subprocess.run(['nice', '-n', '19'] + PY + [f'{P55}/geometry-gate.py', run_rel, '--base', 'wave54-final',
                        '--lanes', lanes, '--sections', sections, '--json', out], cwd=ROOT, capture_output=True, text=True)
    open(f'{SCR}/out/{tag}.txt', 'w').write(p.stdout + p.stderr)
    rows = json.load(open(out))['rows'] if os.path.exists(out) else []
    return p.returncode, {(r['script'], r['key']): r for r in rows}


def shift(a, dx, dy, region=None, bg=(255, 255, 255)):
    """Translate the pixels of `region` (x0,y0,x1,y1; default whole) by (dx, dy), refilling the vacated area with bg."""
    h, w, _ = a.shape
    x0, y0, x1, y1 = region or (0, 0, w, h)
    src = a[y0:y1, x0:x1].copy()
    out = a.copy()
    out[y0:y1, x0:x1] = bg
    ys0, xs0 = max(0, y0 + dy), max(0, x0 + dx)
    ys1, xs1 = min(h, y1 + dy), min(w, x1 + dx)
    out[ys0:ys1, xs0:xs1] = src[ys0 - (y0 + dy):ys1 - (y0 + dy), xs0 - (x0 + dx):xs1 - (x0 + dx)]
    return out
