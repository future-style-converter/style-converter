import json, sys
E = json.load(open(sys.argv[1]))
s = json.loads(json.dumps(E)); s.setdefault('probeDecisions', {})['reverted'] = [{'lane': 'L1-hyphen-ios-linebox', 'unit': 'NB-ios'}]
json.dump(s, open(sys.argv[2] + '/exp-stale.json', 'w'))
o = json.loads(json.dumps(E)); pr = o['lanes']['L1-hyphen-ios-linebox']['geometryProbes'][0]; pr['cmd'] = pr['cmd'].replace('/opt/homebrew/bin/python3.12 -B', 'python3')
json.dump(o, open(sys.argv[2] + '/exp-offinterp.json', 'w'))
k = json.loads(json.dumps(E)); pr = k['lanes']['L1-hyphen-ios-linebox']['geometryProbes'][0]; pr['keyCells'].pop(next(iter(pr['gating'])))
json.dump(k, open(sys.argv[2] + '/exp-nokeycells.json', 'w'))
