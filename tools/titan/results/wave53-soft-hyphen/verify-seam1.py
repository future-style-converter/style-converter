#!/usr/bin/env python3
# tools/titan/results/wave53-soft-hyphen/verify-seam1.py — verify seam-1 under the per-file lock (PLAN wave 53 rule 2).
# mkdir tools/titan/runs/wave53-lock/ComponentRenderer.swift is the mutex (wait 60 s and retry while it exists); then
# sha256 the seam file (must equal HEAD), `git apply` seam-1.patch, run the lane's Catalyst classes GREEN with the patch
# applied, restore HEAD bytes (`git show HEAD:<path>`), check sha256 equal, rmdir the lock.
import hashlib, json, os, subprocess, sys, time
T = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf'
sys.path.insert(0, T + '/tools/titan/results/wave53-soft-hyphen')
from mutate import run_swift
P = 'runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift'
F = os.path.join(T, P)
LOCK = T + '/tools/titan/runs/wave53-lock/ComponentRenderer.swift'
CLASSES = ['SoftHyphenPolicyTests', 'GreedyLineBreakerTests', 'WordBreakOpportunitiesTests', 'InlineRunFlowTests']
sha = lambda b: hashlib.sha256(b).hexdigest()
os.makedirs(os.path.dirname(LOCK), exist_ok=True)
while True:
    try:
        os.mkdir(LOCK); break
    except FileExistsError:
        print('lock held, waiting 60 s', flush=True); time.sleep(60)
log = {}
try:
    head = subprocess.check_output(['git', '-C', T, 'show', 'HEAD:' + P])
    disk = open(F, 'rb').read()
    log['sha256Head'] = sha(head); log['sha256DiskBefore'] = sha(disk)
    assert disk == head, 'seam file is not at HEAD bytes — someone else has it modified'
    subprocess.check_call(['git', '-C', T, 'apply', 'tools/titan/results/wave53-soft-hyphen/seam-1.patch'])
    log['sha256Patched'] = sha(open(F, 'rb').read())
    log['greenWithPatch'] = run_swift(T, CLASSES)
finally:
    open(F, 'wb').write(head)
    log['sha256DiskAfter'] = sha(open(F, 'rb').read())
    os.rmdir(LOCK)
assert log['sha256DiskAfter'] == log['sha256Head']
json.dump(log, open(T + '/tools/titan/results/wave53-soft-hyphen/seam-1.verify.json', 'w'), indent=1)
for k, v in log.items():
    print(k, v if isinstance(v, str) else (v['exit'], v['total'], v['failed'], v['tail'][-1500:]))
