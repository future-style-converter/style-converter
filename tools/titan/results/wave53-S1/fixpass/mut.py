# Fix-pass mutation runner: mutate one file in the EXPORT, run the named node test files, restore byte-exact.
import sys, subprocess, hashlib, json, os
root, rel, old, new, *tests = sys.argv[1:]
old = old.encode().decode('unicode_escape'); new = new.encode().decode('unicode_escape')
p = os.path.join(root, rel); src = open(p, encoding='utf8').read()
h = lambda s: hashlib.sha256(s.encode()).hexdigest()[:16]
assert src.count(old) == 1, f'anchor count {src.count(old)}'
mut = src.replace(old, new); open(p, 'w', encoding='utf8').write(mut)
r = subprocess.run(['node', '--test', *tests], cwd=root, capture_output=True, text=True)
red = [l for l in r.stdout.splitlines() if l.startswith('not ok')]
fails = [l for l in r.stdout.splitlines() if l.startswith('# fail')]
open(p, 'w', encoding='utf8').write(src)
r2 = subprocess.run(['node', '--test', *tests], cwd=root, capture_output=True, text=True)
g = [l for l in r2.stdout.splitlines() if l.startswith(('# pass', '# fail'))]
print(json.dumps({'file': rel, 'before': h(src), 'mutated': h(mut), 'restored': h(open(p, encoding='utf8').read()),
                  'mutatedRun': fails, 'red': red[:6], 'restoredRun': g}))
