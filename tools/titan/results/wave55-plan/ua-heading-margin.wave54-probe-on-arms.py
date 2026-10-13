#!/usr/bin/env python3
# tools/titan/results/wave55-plan/ua-heading-margin.wave54-probe-on-arms.py — runs the COMMITTED wave-54 probe
# tools/titan/results/wave54-plan/ua-heading-face.geometry.py UNCHANGED, with geometry_common.paths patched so the
# native captures of inset-005/-006/-014/-011 come from this lane's replay arms (./ua-heading-margin-replay/
# <test>-<plat>-<ARM>.png). It answers: what do the wave-54 report rows (005/006/014 natives) and the wave-54
# inset-011 CONTROL rows print once this lane's units land? Read-only; the wave-54 file is executed, never edited.
# Usage: python3 ua-heading-margin.wave54-probe-on-arms.py <ARM>
import os
import runpy
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'wave53-plan'))
import geometry_common  # noqa: E402

ARM = sys.argv[1]
_orig = geometry_common.paths
TARGETS = {'text-decoration-inset-005', 'text-decoration-inset-006', 'text-decoration-inset-014', 'text-decoration-inset-011'}


def patched(run, section, test):
    out = []
    for label, p in _orig(run, section, test):
        if test in TARGETS and label in ('ios', 'android'):
            p = os.path.join(HERE, 'ua-heading-margin-replay', f'{test}-{label}-{ARM}.png')
        out.append((label, p))
    return out


geometry_common.paths = patched
sys.argv = ['ua-heading-face.geometry.py', 'wave54-final']
runpy.run_path(os.path.join(HERE, '..', 'wave54-plan', 'ua-heading-face.geometry.py'), run_name='__main__')
