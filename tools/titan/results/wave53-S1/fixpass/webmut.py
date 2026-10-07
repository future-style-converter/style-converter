import sys, subprocess, hashlib, json, os, re
root, cwd, testpath, rel, old, new = sys.argv[1:7]
p = os.path.join(root, rel); src = open(p, encoding='utf8').read(); h = lambda s: hashlib.sha256(s.encode()).hexdigest()[:16]
assert src.count(old) == 1, f'anchor count {src.count(old)}'
def run():
    r = subprocess.run(['npx', 'vitest', 'run', testpath], cwd=os.path.join(root, cwd), capture_output=True, text=True)
    m = re.findall(r'Tests\s+(.*)', r.stdout); return r.returncode, (m[-1].strip() if m else r.stdout[-200:])
open(p, 'w', encoding='utf8').write(src.replace(old, new)); mut = h(open(p, encoding='utf8').read()); a = run()
open(p, 'w', encoding='utf8').write(src); b = run()
print(json.dumps({'file': rel, 'before': h(src), 'mutated': mut, 'restored': h(open(p, encoding='utf8').read()), 'mutatedRun': a, 'restoredRun': b}, ensure_ascii=False))
