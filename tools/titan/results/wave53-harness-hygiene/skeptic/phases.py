# Skeptic's own per-target build-phase dump of an xcodegen pbxproj (via plutil → JSON).
import json, subprocess, sys
def dump(path):
    o = json.loads(subprocess.run(['plutil', '-convert', 'json', '-o', '-', path], capture_output=True, check=True).stdout)['objects']
    res = {}
    for k, v in o.items():
        if v.get('isa') != 'PBXNativeTarget': continue
        ph = {}
        for pid in v['buildPhases']:
            p = o[pid]; files = []
            for bf in p.get('files', []):
                ref = o[o[bf]['fileRef']] if 'fileRef' in o[bf] else {'path': o[bf].get('productRef', '?')}
                files.append(ref.get('path') or ref.get('name'))
            key = p['isa'] + ('' if p['isa'] != 'PBXCopyFilesBuildPhase' else f"(dstSubfolderSpec={p.get('dstSubfolderSpec')},dstPath='{p.get('dstPath')}')")
            ph[key] = sorted(map(str, files))
        res[v['name']] = ph
    return res
a, b = dump(sys.argv[1]), dump(sys.argv[2])
for t in sorted(set(a) | set(b)):
    for k in sorted(set(a.get(t, {})) | set(b.get(t, {}))):
        x, y = a.get(t, {}).get(k), b.get(t, {}).get(k)
        print(('SAME ' if x == y else 'DIFF ') + t + ' ' + k + ' ' + (str(len(x)) if x is not None else '-') + '→' + (str(len(y)) if y is not None else '-'), '' if x == y else sorted(set(y or []) - set(x or [])))
