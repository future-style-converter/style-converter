# Fix-pass Catalyst mutation runner (EXPORT only): mutate, run the focused classes, restore byte-exact, re-run.
import sys, subprocess, hashlib, json, os, re
root, classes, rel, old, new = sys.argv[1:6]
old = old.encode().decode('unicode_escape'); new = new.encode().decode('unicode_escape')
p = os.path.join(root, rel); src = open(p, encoding='utf8').read(); h = lambda s: hashlib.sha256(s.encode()).hexdigest()[:16]
assert src.count(old) == 1, f'anchor count {src.count(old)} in {rel}'
SP = os.path.dirname(root)
def run():
    args = ['nice', '-n', '10', 'xcodebuild', 'test', '-scheme', 'StyleConverterRuntime', '-destination',
            'platform=macOS,variant=Mac Catalyst,arch=arm64', '-derivedDataPath', os.path.join(SP, 'dd-swF')]
    args += [f'-only-testing:StyleConverterRuntimeTests/{c}' for c in classes.split(',')]
    r = subprocess.run(args, cwd=root, capture_output=True, text=True); out = r.stdout + r.stderr
    ex = re.findall(r'Executed \d+ tests?, with \d+ failures?', out)
    failed = sorted(set(re.findall(r"Test Case '-\[StyleConverterRuntimeTests\.(\w+ \w+)\]' failed", out)))
    return r.returncode, (ex[-1] if ex else 'BUILD ' + ('FAILED' if 'BUILD FAILED' in out or 'error:' in out else '?')), failed
open(p, 'w', encoding='utf8').write(src.replace(old, new)); mut = h(open(p, encoding='utf8').read()); a = run()
open(p, 'w', encoding='utf8').write(src); b = run()
print(json.dumps({'file': rel, 'before': h(src), 'mutated': mut, 'restored': h(open(p, encoding='utf8').read()), 'mutatedRun': a, 'restoredRun': b[:2]}))
