# Usage: python3 census.py <PROJECT_ROOT> [extra root …]. Read-only: signals nothing.
# Skeptic's own census of Gradle daemon ownership (independent of the lane's gradle-daemon-census.sh).
import os, re, glob, subprocess, collections, sys
ROOT = os.path.realpath(sys.argv[1])
extra = [os.path.realpath(p) if os.path.exists(p) else p.rstrip('/') for p in sys.argv[2:]]
home = os.environ.get('GRADLE_USER_HOME', os.path.expanduser('~/.gradle'))
rx = re.compile(r'currentDir=([^}]*)')
def inside(d, r):
    if not (d == r or d.startswith(r + '/')): return False
    x = d
    while x.startswith(r + '/'):
        if os.path.exists(os.path.join(x, '.git')): return False
        x = x.rsplit('/', 1)[0]
    return True
def classify(dirs, roots):
    if not dirs: return 'empty'
    ins = [any(inside(d, r) for r in roots) for d in dirs]
    if all(ins): return 'ours'
    if any(inside(d, ROOT) for d in dirs): return 'mixed-with-root'
    return 'foreign'
counts = collections.Counter(); per = {}
for log in sorted(glob.glob(f'{home}/daemon/9.6.1/daemon-*.out.log')):
    dirs = set(rx.findall(open(log, errors='replace').read()))
    c = classify(dirs, [ROOT]); counts[c] += 1; per[log] = (c, dirs)
print('9.6.1 logs:', sum(counts.values()), dict(counts))
for log, (c, dirs) in per.items():
    if c != 'ours': print(' ', c, os.path.basename(log), sorted(re.sub(r'^/private/tmp/claude-[0-9]+/[^/]+/[^/]+/scratchpad', '<SP>', d) for d in dirs))
# live daemons
out = subprocess.run(['pgrep', '-f', 'org.gradle.launcher.daemon.bootstrap.GradleDaemon '], capture_output=True, text=True).stdout.split()
for p in out:
    cmd = subprocess.run(['ps', '-o', 'command=', '-p', p], capture_output=True, text=True).stdout
    m = re.search(r'GradleDaemon ([0-9][0-9A-Za-z.+-]*)', cmd); ver = m.group(1) if m else None
    log = f'{home}/daemon/{ver}/daemon-{p}.out.log'
    dirs = set(rx.findall(open(log, errors='replace').read())) if os.path.exists(log) else None
    print('LIVE', p, ver, 'log' if dirs is not None else 'NO-LOG', 'default-call:', classify(dirs or set(), [ROOT]) if dirs is not None else 'left', '| with extra roots:', classify(dirs or set(), [ROOT] + extra) if dirs is not None else 'left')
    if dirs: print('   ', sorted(re.sub(r'^/private/tmp/claude-[0-9]+/[^/]+/[^/]+/scratchpad', '<SP>', d) for d in dirs))
