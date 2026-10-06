#!/usr/bin/env python3
# Scratch driver for the L6 R1 fix-pass mutations: for each name, apply via
# the lane's mutate.py, run the focused '*BorderSide*' Gradle tests, read ONLY
# JUnit XML whose timestamp is newer than the run start (else INVALID), log a
# `result` line through mutate.py, then restore (mutate.py re-checks sha256).
import datetime, glob, os, re, subprocess, sys

ROOT = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf'
LANE = f'{ROOT}/tools/titan/results/wave52-counters-and-lists'
XML = f'{ROOT}/runtimes/compose/build/test-results/testDebugUnitTest'
CLASSES = ['BorderSideTwoLineBandTest', 'BorderSideZeroTallBandTest']
env = dict(os.environ)
env['JAVA_HOME'] = subprocess.check_output(['/usr/libexec/java_home', '-v', '21'], text=True).strip()

def mut(verb, name, *rest):
    subprocess.run(['python3', f'{LANE}/mutate.py', verb, name, *rest], check=True)

def run(name):
    start = datetime.datetime.now(datetime.timezone.utc).strftime('%Y-%m-%dT%H:%M:%S')
    mut('apply', name)
    try:
        log = f'{LANE}/mutation-r1-{name}.log'
        with open(log, 'w') as f:
            rc = subprocess.run(['./gradlew', ':runtime:testDebugUnitTest', '--tests', '*BorderSide*'],
                                cwd=f'{ROOT}/apps/android-harness', env=env, stdout=f, stderr=subprocess.STDOUT).returncode
        total = failed = 0; reds = []; stale = []
        for cls in CLASSES:
            path = f'{XML}/TEST-com.styleconverter.runtime.borders.sides.{cls}.xml'
            s = open(path).read() if os.path.exists(path) else ''
            m = re.search(r'tests="(\d+)" skipped="\d+" failures="(\d+)" errors="(\d+)" timestamp="([^"]+)"', s)
            if not m or m.group(4)[:19] < start:
                stale.append(cls); continue
            total += int(m.group(1)); failed += int(m.group(2)) + int(m.group(3))
            for tc in re.finditer(r'<testcase name="([^"]+)"[^>]*>\s*<failure message="([^"]*)"', s):
                reds.append(f'{cls}.{tc.group(1)} :: {tc.group(2)[:140]}')
        if stale:
            outcome = f'INVALID: gradle rc={rc}, JUnit XML not refreshed for {stale} (start {start}Z)'
        else:
            outcome = f'gradle rc={rc} start={start}Z {total} tests, {failed} failed' + ('; RED: ' + ' | '.join(reds) if reds else '; ALL GREEN')
        mut('result', name, outcome)
    finally:
        mut('restore', name)

for n in sys.argv[1:]:
    run(n)
