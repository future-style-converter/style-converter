# Every accepted fake differs from the fixed picture it was built from (no no-op fakes): changed-pixel counts.
import os, sys, json, numpy as np
from PIL import Image
P = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf/tools/titan/results/wave55-plan'
R = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf/tools/titan/runs'
A = lambda p: np.asarray(Image.open(p).convert('RGB')).astype(int)
SRC = {'dir-style-02a': f'{P}/positioned-parent-slot-replay/dir-style-02a-android-U1U2.png', 'bidi__bidi-tab-001': f'{P}/positioned-parent-slot-replay/bidi-tab-001-android-U1U2.png',
       'anchor-position-multicol-nested-001': f'{P}/positioned-parent-slot-replay/anchor-position-multicol-nested-001-android-U1.png'}
out = []
for fid in sorted(os.listdir('pngs')):
    for f in sorted(os.listdir(f'pngs/{fid}')):
        plat, test = f.split('__', 1); test = test[:-4]
        cands = []
        if fid.startswith('L2-c3'): cands = [f'{P}/cbb-android-replay/android-clip-3.png']
        elif fid.startswith('L2'): sec = 'css-flexbox' if 'abspos' in test else 'filter-effects'; cands = [f'{R}/wave54-probe/sections/{sec}/android-screenshots/wpt__{sec}__{test}.png']
        elif fid.startswith('L3'): cands = [SRC.get(test, f'{P}/positioned-parent-slot-replay/{test}-android-U2.png')]
        elif fid.startswith('L4'): arm = 'U1' if '-U1-' in fid else 'C'; cands = [f'{P}/ua-heading-margin-replay/{test}-{plat}-{arm}.png']
        elif fid.startswith('L5'): cands = [f'{P}/counter-suffix-rows-replay/ok-all/{test}.{plat}.png']
        elif fid.startswith('L6'): cands = [f'{P}/android-paint-order-replay/{test}-android-U2.png']
        elif fid.startswith('L1'): cands = [f'{P}/hyphen-ios-linebox-replay/{test.split("-")[-1]}-ios-Rm.png']
        src = next((c for c in cands if os.path.exists(c)), None)
        if src is None or fid.startswith('combo'): continue
        n = int((np.abs(A(f'pngs/{fid}/{f}') - A(src)).max(axis=2) > 0).sum())
        out.append((fid, f, n))
for fid, f, n in out: print(f'{fid:40} {f:70} changed px {n}')
print('fakes with 0 changed px (no-ops):', [x[:2] for x in out if x[2] == 0 and not (x[1].startswith('web'))])
