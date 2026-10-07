#!/usr/bin/env python3
# tools/titan/results/wave53-soft-hyphen/verify-seam2.py — verify seam-2 under the per-file lock (PLAN wave 53 rule 2).
# mkdir tools/titan/runs/wave53-lock/ComponentRenderer.kt is the mutex (wait 60 s and retry while it exists); then:
#   1. sha256 the seam file (must equal HEAD), `git apply` seam-2.patch;
#   2. run the focused suites GREEN with the patch applied;
#   3. M-seam2: delete the clause's two code lines (comments kept) → RunFoldBreadcrumbSeamTest RED;
#   4. restore the patched bytes → GREEN again;
#   5. restore HEAD bytes (`git show HEAD:<path>`), sha256 equal to step 1, rmdir the lock.
import hashlib, json, os, subprocess, sys, time
T = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf'
sys.path.insert(0, T + '/tools/titan/results/wave53-soft-hyphen')
from mutate import run_compose
P = 'runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt'
F = os.path.join(T, P)
LOCK = T + '/tools/titan/runs/wave53-lock/ComponentRenderer.kt'
CLAUSE = ('                                            (if (runFold.droppedOutOfFlowMembers > 0)\n'
          '                                                ", ${runFold.droppedOutOfFlowMembers} out-of-flow member(s) dropped" else "") +\n')
SUITE = ['*RunFoldBreadcrumbSeamTest', '*InlineRunFoldTest', '*PreBreakPipelineTest', '*SeamReachabilityTest']
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
    subprocess.check_call(['git', '-C', T, 'apply', 'tools/titan/results/wave53-soft-hyphen/seam-2.patch'])
    patched = open(F, 'rb').read(); log['sha256Patched'] = sha(patched)
    log['greenWithPatch'] = run_compose(T, SUITE)
    s = patched.decode()
    assert s.count(CLAUSE) == 1
    open(F, 'w').write(s.replace(CLAUSE, ''))
    log['sha256Mutated'] = sha(open(F, 'rb').read())
    log['redMseam2'] = run_compose(T, ['*RunFoldBreadcrumbSeamTest'])
    open(F, 'wb').write(patched)
    log['greenAfterMutationRestore'] = run_compose(T, ['*RunFoldBreadcrumbSeamTest'])
finally:
    open(F, 'wb').write(head)
    log['sha256DiskAfter'] = sha(open(F, 'rb').read())
    os.rmdir(LOCK)
assert log['sha256DiskAfter'] == log['sha256Head']
json.dump(log, open(T + '/tools/titan/results/wave53-soft-hyphen/seam-2.verify.json', 'w'), indent=1)
for k, v in log.items():
    print(k, v if isinstance(v, str) else (v['exit'], v['total'], v['failed']))
