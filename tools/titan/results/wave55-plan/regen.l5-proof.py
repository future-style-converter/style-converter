#!/usr/bin/env python3
# tools/titan/results/wave55-plan/regen.l5-proof.py — the pre-landing regeneration's proof of the L5 probe edit (R1), the replay
# re-graft (R2) and the residual SPEC (R3) requested by wave55-counter-suffix-rows/_note.md "fix-r1" (regen.done.md).
#
# It re-executes the L5 fix lane's own proof (fix-r1.probe-proposal.py: the same pictures, the same builders, the same
# expectations), but against the INSTALLED bytes instead of an in-memory proposal:
#   old  = the probe / residual-read.py / replay pictures at git HEAD (the plan as committed before this regeneration);
#   new  = this tree's counter-suffix-rows.geometry.py, residual-read.py and counter-suffix-rows-replay/ (after it).
# §1 pictures (all css-counter-styles/counter-suffix ios, base wave54-final; the lane's PICS verbatim): base (the pre-fix device
#    picture: [T] ios WRONG on geometry, [K] ios OK — the self-test), correct (the skeptic's skmodel: the device capture + U3's
#    Catalyst delta: [T] ios WRONG `ink:` ONLY without a decided twin, OK with it; [K] ios OK), F1-F11 wrong pictures (each
#    rejected: by a geometry reason of [T] ios, by its `ink:`, or by [K] ios), F12 the named 2-px floor (printed, not counted).
#    Every key other than [T] / [K] ios prints the same verdict under old and new.
# §2 the standing replays: new probe on the RE-GRAFTED tags == old probe on the committed (HEAD) tags, every key, all 10 tags.
# §3 the residual read (residual-read.py read_arrays, tolerance 0) on the correct-U3 pictures: new SPEC 0 / 0 / 0, old SPEC (the new
#    one minus the two R3 rects, asserted) > 0 outside (the change is load-bearing); the U1-only Catalyst pair reads 0 under both.
# Writes nothing in the tree: a private mirror under $TMPDIR (removed). Usage: /opt/homebrew/bin/python3.12 -B regen.l5-proof.py
import hashlib, importlib.util, os, shutil, subprocess, sys, tempfile
import numpy as np
from PIL import Image

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
P55 = 'tools/titan/results/wave55-plan'
LANE = os.path.join(ROOT, 'tools/titan/results/wave55-counter-suffix-rows')
SEC = 'tools/titan/runs/wave54-final/sections/css-counter-styles'
TESTS = ('counter-suffix', 'cjk-decimal__css3-counter-styles-005')
PY = '/opt/homebrew/bin/python3.12'
TAGS = ('ok-all', 'H-broken', 'H-nodot', 'H-flush', 'H-low', 'C-shift8', 'C-nocomma', 'C-space-only', 'I-wholeline', 'K-leak')
git = lambda *a: subprocess.run(['git', '-C', ROOT] + list(a), capture_output=True, check=True).stdout
sha = lambda b: hashlib.sha256(b).hexdigest()

M = tempfile.mkdtemp(prefix='regen-l5-proof-')
for d in ('wave55-plan', 'wave53-plan'):
    os.makedirs(f'{M}/tools/titan/results/{d}')
old_probe, new_probe = git('show', f'HEAD:{P55}/counter-suffix-rows.geometry.py'), open(f'{HERE}/counter-suffix-rows.geometry.py', 'rb').read()
open(f'{M}/{P55}/old.geometry.py', 'wb').write(old_probe)
open(f'{M}/{P55}/new.geometry.py', 'wb').write(new_probe)
shutil.copy(f'{ROOT}/tools/titan/results/wave53-plan/geometry_common.py', f'{M}/tools/titan/results/wave53-plan/')
os.makedirs(f'{M}/tools/titan/runs')
os.symlink(f'{ROOT}/tools/wpt', f'{M}/tools/wpt')
os.symlink(f'{ROOT}/{SEC.rsplit("/sections", 1)[0]}', f'{M}/tools/titan/runs/wave54-final')
print('# regen.l5-proof.out.txt — the L5 fix-r1 R1 / R2 / R3 regeneration, proven on the installed bytes (regen.l5-proof.py)')
print(f'# probe old (HEAD) sha256 {sha(old_probe)[:16]}… · new (this tree) {sha(new_probe)[:16]}…; base wave54-final; '
      f'decided = the correct picture (stands in for TWIN_RUN = wave55-pre, PLAN §7 stage 1 step 8)')

ld = lambda p: np.asarray(Image.open(p).convert('RGB')).astype(np.int16)
dev = ld(f'{ROOT}/{SEC}/ios-screenshots/wpt__css-counter-styles__counter-suffix.png')
andr = ld(f'{ROOT}/{SEC}/android-screenshots/wpt__css-counter-styles__counter-suffix.png')
cw, co = ld(f'{LANE}/skeptic-catalyst-u3-with.png'), ld(f'{LANE}/skeptic-catalyst-u3-without.png')
cf, cn = ld(f'{LANE}/fix-r1.catalyst-full.png'), ld(f'{LANE}/fix-r1.catalyst-noU1.png')
correct = np.where((cw != co).any(axis=2)[..., None], cw, dev)
WHITE = 255


def erase(a, x0, y0, x1, y1):
    a = a.copy(); a[y0:y1, x0:x1] = WHITE; return a


def shift(a, x0, y0, x1, y1, dx, dy):
    b = a.copy(); patch = a[y0:y1, x0:x1].copy(); b[y0:y1, x0:x1] = WHITE
    b[y0 + dy:y1 + dy, x0 + dx:x1 + dx] = np.minimum(b[y0 + dy:y1 + dy, x0 + dx:x1 + dx], patch); return b


def copy_add(a, x0, y0, x1, y1, dx, dy):
    b = a.copy(); patch = a[y0:y1, x0:x1].copy()
    b[y0 + dy:y1 + dy, x0 + dx:x1 + dx] = np.minimum(b[y0 + dy:y1 + dy, x0 + dx:x1 + dx], patch); return b


def blob(a, x0, y0, w, h):
    b = a.copy(); b[y0:y0 + h, x0:x0 + w] = 0; return b


def grey(a, x, y):
    b = a.copy(); b[y, x] = 170; return b


def block(a, x0, y0, x1, y1):
    b = a.copy(); b[y0:y1, x0:x1] = 0; return b


PICS = [   # the lane's PICS (fix-r1.probe-proposal.py), verbatim
    ('base', dev, 'base'),
    ('correct', correct, 'ok'),
    ('F1 row9 period erased', erase(correct, 130, 208, 136, 232), 'T-geo'),
    ('F2 row10 period moved +2 px', shift(correct, 130, 232, 136, 256, 2, 0), 'T-geo'),
    ('F3 row11 period erased', erase(correct, 130, 256, 136, 280), 'T-geo'),
    ('F4 row12 period moved -2 px', shift(correct, 130, 280, 136, 304, -2, 0), 'T-geo'),
    ('F5 row10 two erased', erase(correct, 136, 232, 151, 256), 'T-geo'),
    ('F6 row10 two moved +2 px', shift(correct, 136, 232, 151, 256, 2, 0), 'T-geo'),
    ('F7 row10 two as a block', block(correct, 138, 238, 146, 250), 'T-geo'),
    ('F8 row10 two copied 1 px down (added)', copy_add(correct, 136, 232, 151, 255, 0, 1), 'T-ink'),
    ('F9 2x2 blob beside row11 period (added)', blob(correct, 131, 266, 2, 2), 'T-geo'),
    ('F10 row11 letter erased (outside the boxes)', erase(correct, 136, 256, 151, 280), 'K'),
    ('F11 row12 period copied 1 px down (added)', copy_add(correct, 130, 280, 136, 303, 0, 1), 'T-ink'),
    ('F12 one light-grey px in row9 period box (added, 1 level)', grey(correct, 131, 220), 'floor'),
]


def make_run(rid, ios):
    out = f'{M}/tools/titan/runs/{rid}/sections/css-counter-styles'
    for d in ('screenshots', 'android-screenshots', 'ios-screenshots'):
        os.makedirs(f'{out}/{d}')
        for t in TESTS:
            shutil.copy(f'{ROOT}/{SEC}/{d}/wpt__css-counter-styles__{t}.png', f'{out}/{d}/')
    Image.fromarray(ios.astype(np.uint8)).save(f'{out}/ios-screenshots/wpt__css-counter-styles__counter-suffix.png')


def read(probe, rid, twin=None):
    cmd = ['nice', '-n', '19', PY, '-B', f'{M}/{P55}/{probe}.geometry.py', rid, 'wave54-final'] + (['--twin-run', twin] if twin else [])
    r = subprocess.run(cmd, capture_output=True, text=True)
    return r.returncode, {l.split(' → ')[0].split()[0] + ' ' + l.split()[2]: l.split(' → ', 1)[1] for l in r.stdout.splitlines()
                          if l.startswith(('[T] counter-suffix', '[K] counter-suffix', '[H] counter-suffix', '[C] counter-suffix', '[I] cjk', '[L] counter-suffix'))}


make_run('decided', correct)
bad = 0
print('== §1 the lane\'s pictures through the old and the new probe')
for i, (name, pic, want) in enumerate(PICS):
    rid = f'p{i}'; make_run(rid, pic)
    rows = {(pr, tw): read(pr, rid, tw) for pr in ('old', 'new') for tw in (None, 'decided')}
    print(f'  {name} (expect: {want})')
    for (pr, tw), (rc, L) in rows.items():
        print(f'    {pr} twin={tw or "-":7} rc={rc} [T] ios → {L.get("[T] ios", "MISSING")} · [K] ios → {L.get("[K] ios", "MISSING")}')
    T0, K0 = rows[('new', None)][1].get('[T] ios', 'MISSING'), rows[('new', None)][1].get('[K] ios', 'MISSING')
    T1, K1 = rows[('new', 'decided')][1].get('[T] ios', 'MISSING'), rows[('new', 'decided')][1].get('[K] ios', 'MISSING')
    ok = lambda s: s == 'GEOMETRY OK'
    inkonly = lambda s: s.startswith('GEOMETRY WRONG (ink:')
    good = {'base': (not ok(T0)) and not inkonly(T0) and ok(K0),
            'ok': inkonly(T0) and ok(K0) and ok(T1) and ok(K1),
            'T-geo': (not ok(T1)) and not inkonly(T1) and (not ok(T0)) and not inkonly(T0),
            'T-ink': (not ok(T1)) and (not ok(T0)),
            'K': (not ok(K1)) and (not ok(K0)),
            'floor': True}[want]
    others = lambda pr: {k: v for k, v in rows[(pr, 'decided')][1].items() if k not in ('[T] ios', '[K] ios')}
    same = others('new') == others('old')
    print(f'    → {"HOLDS" if good else "VIOLATED"}; every other key identical old vs new: {same}')
    bad += (not good) + (not same)

print('== §2 the standing replays: the old probe on the committed (HEAD) tag vs the new probe on this tree\'s re-grafted tag')


def verdicts(probe, tagdir_parent, tag):
    link = f'{M}/{P55}/counter-suffix-rows-replay'
    if os.path.islink(link) or os.path.exists(link):
        os.remove(link) if os.path.islink(link) else shutil.rmtree(link)
    os.symlink(tagdir_parent, link)
    r = subprocess.run(['nice', '-n', '19', PY, '-B', f'{M}/{P55}/{probe}.geometry.py', f'replay:{tag}', 'wave54-final'], capture_output=True, text=True)
    return [(l.split(' → ')[0].split()[0] + ' ' + l.split()[1] + ' ' + l.split()[2], l.split(' → ', 1)[1].split(' (')[0])
            for l in r.stdout.splitlines() if ' → ' in l]


HEADRP = f'{M}/head-replays'
for tag in TAGS:
    os.makedirs(f'{HEADRP}/{tag}')
    for f in os.listdir(f'{HERE}/counter-suffix-rows-replay/{tag}'):
        rel = f'{P55}/counter-suffix-rows-replay/{tag}/{f}'
        open(f'{HEADRP}/{tag}/{f}', 'wb').write(git('show', f'HEAD:{rel}'))
for tag in TAGS:
    o = verdicts('old', HEADRP, tag)
    n = verdicts('new', f'{HERE}/counter-suffix-rows-replay', tag)
    ch = [f for f in sorted(os.listdir(f'{HERE}/counter-suffix-rows-replay/{tag}'))
          if open(f'{HERE}/counter-suffix-rows-replay/{tag}/{f}', 'rb').read() != open(f'{HEADRP}/{tag}/{f}', 'rb').read()]
    same = o == n
    print(f'  {tag:13} files changed vs HEAD: {ch}; {len(n)} keys, verdicts new-on-regrafted == old-on-committed: {same}'
          + ('' if same else f' DIFF {[(a, b) for a, b in zip(o, n) if a != b]}'))
    bad += not same

print('== §3 the residual read (residual-read.py read_arrays, tolerance 0) on the correct-U3 pictures: new SPEC vs old SPEC')


def rr_mod(src_bytes, name):
    p = f'{M}/{P55}/{name}.py'; open(p, 'wb').write(src_bytes)
    s = importlib.util.spec_from_file_location(name, p); m = importlib.util.module_from_spec(s); s.loader.exec_module(m); return m


rr_new = rr_mod(open(f'{HERE}/residual-read.py', 'rb').read(), 'rr_new')
CELL = 'css-counter-styles/counter-suffix.html ios'
# residual-read.py is not tracked at HEAD (fix-r6 wrote it; the orchestrator commits it): the OLD declaration is the new one minus
# exactly the two R3 rects — asserted, so no other part of the SPEC can have changed unseen.
R3 = [('rect', (130, 208, 136, 304)), ('rect', (136, 232, 151, 256))]
SPEC_NEW = rr_new.SPECS[CELL]
assert SPEC_NEW['zone'][-2:] == R3 and len(SPEC_NEW['zone']) == 5, SPEC_NEW
SPEC_OLD = dict(SPEC_NEW, zone=SPEC_NEW['zone'][:-2])
okall = ld(f'{HERE}/counter-suffix-rows-replay/ok-all/counter-suffix.ios.png')
for name, C, B, want_new in (('skmodel (device + U3 Catalyst delta) vs wave54-final', correct, dev, 0),
                             ('Catalyst U3 on vs off (one rasterizer)', cw, co, 0),
                             ('Catalyst full vs U1 reverted (U1 alone: rows 3-4)', cf, cn, 0),
                             ('ok-all (re-grafted) vs wave54-final', okall, dev, 0)):
    rn, ro = rr_new.read_arrays(C, B, SPEC_NEW), rr_new.read_arrays(C, B, SPEC_OLD)
    print(f'  {name:55} new outside / vacated / non-class {rn[0]} / {rn[1]} / {rn[2]} · old {ro[0]} / {ro[1]} / {ro[2]}')
    bad += sum(rn[:3]) != want_new
shutil.rmtree(M)
print(f'violations: {bad}')
sys.exit(1 if bad else 0)
