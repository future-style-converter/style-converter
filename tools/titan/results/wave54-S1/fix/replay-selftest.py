#!/usr/bin/env python3
# wave54-S1/fix/replay-selftest.py — re-runs the recorded inline self-test of rtl-marker-bake.geometry.out.txt (its
# last line) with the FIXED m_check: the three Android pixel replays of rtl-marker-bake.replay.py must keep their
# verdicts (android-P WRONG; android-Mp-dev / android-Mp-ref OK), i.e. the left-edge rule does not reject the M'+P shape.
# Usage: python3 rtl-marker-bake.replay.py <dir> && python3 tools/titan/results/wave54-S1/fix/replay-selftest.py <dir>
import os, sys
R = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..', '..'))   # repo root
PROBE = f'{R}/tools/titan/results/wave54-plan/rtl-marker-bake.geometry.py'
REPLAY = os.path.abspath(sys.argv[1])                                                        # the replay PNG dir
src = open(PROBE, encoding='utf-8').read()
ns = {'__file__': PROBE, '__name__': 'defs'}                                                 # its helpers import path
sys.argv = ['probe']                                                                         # the probe reads argv at import
exec(src.split('\nP_CASES =')[0], ns)                                                        # definitions only, no run loop
_, rpx = ns['load'](dict(ns['paths']('wave53-final', 'css-counter-styles', 'counter-suffix'))['ref'])  # the frozen ref
ref_rows = ns['m_rows'](rpx)                                                                 # the ref's four RTL text rows
out = []
for name in ('android-P', 'android-Mp-dev', 'android-Mp-ref'):                               # the three replays
    _, px = ns['load'](os.path.join(REPLAY, f'{name}.png'))
    why = ns['m_check'](px, ns['m_rows'](px), ref_rows)                                      # the fixed [M] rule
    out.append(f'{name} → ' + ('GEOMETRY OK' if why is None else f'GEOMETRY WRONG ({why})'))
print('self-test (inline m_check over rtl-marker-bake.replay.py outputs): ' + '; '.join(out))
