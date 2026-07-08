#!/usr/bin/env node
// Unit tests for tools/visual/os-matrix.mjs.
//
// Built round 73. Following the round 56 (css-to-ir) + round 72
// (a11y-audit) pattern. Pin the contract for the Tier 12 OS-matrix
// runner's pure parsing logic without needing xcrun / a real
// simulator.
//
// The runner has 4 pure helpers we can pin:
//   - parseIOSRuntimesFromSimctlJson(json) → [{label, runtimeId, device, udid}]
//   - parseAndroidAVDsFromList(stdout) → [{label, name}]
//   - SSIM_DRIFT_THRESHOLD constant (regression threshold, must match doc)
//   - The constants (FIXTURE, SNAP_ROOT, REPORT_PATH)
//
// The execSync-bound helpers (detectIOSRuntimes, detectAndroidAVDs,
// shutdownAllSims, runOnIOSRuntime) are NOT exported because they
// require mocking xcrun. The pure parsers ARE exported and are what
// can drift; this file pins their contract.

import { test } from 'node:test';
import assert from 'node:assert/strict';

import {
  parseIOSRuntimesFromSimctlJson,
  parseAndroidAVDsFromList,
  SSIM_DRIFT_THRESHOLD,
  FIXTURE,
  SNAP_ROOT,
  REPORT_PATH,
} from './os-matrix.mjs';

// ── Constants ──────────────────────────────────────────────────────────────

test('SSIM_DRIFT_THRESHOLD is 0.05 (matches TIER12 spec)', () => {
  // The 0.05 threshold is documented in TIER12_OS_MATRIX.md as the
  // "drift > 0.05 = regression" rule. Pinning ensures the constant
  // stays in sync with the doc.
  assert.equal(SSIM_DRIFT_THRESHOLD, 0.05);
});

test('FIXTURE default points at a Tier-3 component', () => {
  // The default fixture is intentionally small (1 component) so
  // os-matrix runs are fast. If someone changes the default to
  // visual-test.json (109 components) they need to think about it.
  assert.match(FIXTURE, /fixtures\/components\/[A-Z][a-zA-Z]+\.json/);
});

test('SNAP_ROOT and REPORT_PATH are under tools/visual/', () => {
  // Both should be inside tools/visual/ so .gitignore catches them and
  // they don't leak to the project root.
  assert.match(SNAP_ROOT, /^tools\/visual\//);
  assert.match(REPORT_PATH, /^tools\/visual\//);
});

// ── parseIOSRuntimesFromSimctlJson ──────────────────────────────────────────

test('parseIOSRuntimesFromSimctlJson: empty input returns []', () => {
  assert.deepEqual(parseIOSRuntimesFromSimctlJson({}), []);
  assert.deepEqual(parseIOSRuntimesFromSimctlJson(null), []);
  assert.deepEqual(parseIOSRuntimesFromSimctlJson({ devices: {} }), []);
});

test('parseIOSRuntimesFromSimctlJson: single iOS runtime + iPhone 17 Pro', () => {
  // The most common case on this dev machine.
  const json = {
    devices: {
      'com.apple.CoreSimulator.SimRuntime.iOS-26-0': [
        { name: 'iPhone 17 Pro', udid: 'AAAA-1111', isAvailable: true },
        { name: 'iPad Pro', udid: 'BBBB-2222', isAvailable: true },
      ],
    },
  };
  const out = parseIOSRuntimesFromSimctlJson(json);
  assert.equal(out.length, 1);
  assert.equal(out[0].label, 'iOS-26.0');
  assert.equal(out[0].device, 'iPhone 17 Pro');
  assert.equal(out[0].udid, 'AAAA-1111');
  assert.equal(out[0].runtimeId, 'com.apple.CoreSimulator.SimRuntime.iOS-26-0');
});

test('parseIOSRuntimesFromSimctlJson: prefers iPhone 17 Pro over other iPhones', () => {
  // The picker should consistently pick iPhone 17 Pro when available
  // so device-mismatch doesn't confound version-comparison.
  const json = {
    devices: {
      'com.apple.CoreSimulator.SimRuntime.iOS-26-0': [
        { name: 'iPhone 17', udid: 'CCCC-3333', isAvailable: true },
        { name: 'iPhone 17 Pro', udid: 'DDDD-4444', isAvailable: true },
        { name: 'iPhone 17 Pro Max', udid: 'EEEE-5555', isAvailable: true },
      ],
    },
  };
  const out = parseIOSRuntimesFromSimctlJson(json);
  assert.equal(out.length, 1);
  assert.equal(out[0].device, 'iPhone 17 Pro');
  assert.equal(out[0].udid, 'DDDD-4444');
});

test('parseIOSRuntimesFromSimctlJson: falls back to first iPhone if no Pro', () => {
  // When iPhone 17 Pro isn't available, pick the first iPhone in the list.
  const json = {
    devices: {
      'com.apple.CoreSimulator.SimRuntime.iOS-17-5': [
        { name: 'iPhone 15', udid: 'FFFF-6666', isAvailable: true },
        { name: 'iPhone 14', udid: 'GGGG-7777', isAvailable: true },
      ],
    },
  };
  const out = parseIOSRuntimesFromSimctlJson(json);
  assert.equal(out.length, 1);
  assert.equal(out[0].device, 'iPhone 15');
});

test('parseIOSRuntimesFromSimctlJson: skips non-iOS runtimes', () => {
  // tvOS / watchOS / visionOS runtimes appear in xcrun output but
  // shouldn't show up in the iOS matrix.
  const json = {
    devices: {
      'com.apple.CoreSimulator.SimRuntime.iOS-26-0': [
        { name: 'iPhone 17 Pro', udid: 'A', isAvailable: true },
      ],
      'com.apple.CoreSimulator.SimRuntime.tvOS-26-0': [
        { name: 'Apple TV', udid: 'B', isAvailable: true },
      ],
      'com.apple.CoreSimulator.SimRuntime.watchOS-26-0': [
        { name: 'Apple Watch', udid: 'C', isAvailable: true },
      ],
      'com.apple.CoreSimulator.SimRuntime.visionOS-26-0': [
        { name: 'Apple Vision Pro', udid: 'D', isAvailable: true },
      ],
    },
  };
  const out = parseIOSRuntimesFromSimctlJson(json);
  assert.equal(out.length, 1);
  assert.equal(out[0].label, 'iOS-26.0');
});

test('parseIOSRuntimesFromSimctlJson: skips runtimes with no available iPhones', () => {
  // A runtime with only unavailable iPhones (corrupted simulator) or
  // only iPad/non-iPhone devices should be dropped.
  const json = {
    devices: {
      'com.apple.CoreSimulator.SimRuntime.iOS-26-0': [
        { name: 'iPhone 17', udid: 'A', isAvailable: false }, // unavailable
      ],
      'com.apple.CoreSimulator.SimRuntime.iOS-26-2': [
        { name: 'iPad Pro', udid: 'B', isAvailable: true }, // not iPhone
      ],
      'com.apple.CoreSimulator.SimRuntime.iOS-17-5': [
        { name: 'iPhone 15', udid: 'C', isAvailable: true }, // ok
      ],
    },
  };
  const out = parseIOSRuntimesFromSimctlJson(json);
  assert.equal(out.length, 1);
  assert.equal(out[0].label, 'iOS-17.5');
});

test('parseIOSRuntimesFromSimctlJson: multiple iOS versions all returned', () => {
  // The os-matrix raison d'être: compare the SAME device across
  // different iOS versions. Both should appear in the output.
  const json = {
    devices: {
      'com.apple.CoreSimulator.SimRuntime.iOS-26-0': [
        { name: 'iPhone 17 Pro', udid: 'V1', isAvailable: true },
      ],
      'com.apple.CoreSimulator.SimRuntime.iOS-26-2': [
        { name: 'iPhone 17 Pro', udid: 'V2', isAvailable: true },
      ],
    },
  };
  const out = parseIOSRuntimesFromSimctlJson(json);
  assert.equal(out.length, 2);
  const labels = out.map((r) => r.label).sort();
  assert.deepEqual(labels, ['iOS-26.0', 'iOS-26.2']);
  // Each runtime has a distinct UDID — round 49's bug was that os-matrix
  // wasn't disambiguating these. Pin that they don't collide.
  const udids = out.map((r) => r.udid).sort();
  assert.deepEqual(udids, ['V1', 'V2']);
});

test('parseIOSRuntimesFromSimctlJson: malformed runtime ID falls back to runtimeId for label', () => {
  // Defensive: if Apple changes the runtime ID format, we should still
  // produce SOMETHING usable rather than crashing.
  const json = {
    devices: {
      'com.apple.CoreSimulator.SimRuntime.iOS-experimental-123': [
        { name: 'iPhone 17', udid: 'X', isAvailable: true },
      ],
    },
  };
  const out = parseIOSRuntimesFromSimctlJson(json);
  assert.equal(out.length, 1);
  // Falls back to the full runtimeId as the label.
  assert.equal(out[0].label, 'com.apple.CoreSimulator.SimRuntime.iOS-experimental-123');
});

// ── parseAndroidAVDsFromList ────────────────────────────────────────────────

test('parseAndroidAVDsFromList: empty input returns []', () => {
  assert.deepEqual(parseAndroidAVDsFromList(''), []);
  assert.deepEqual(parseAndroidAVDsFromList(null), []);
  assert.deepEqual(parseAndroidAVDsFromList(undefined), []);
});

test('parseAndroidAVDsFromList: single AVD line', () => {
  const out = parseAndroidAVDsFromList('Pixel_API_34\n');
  assert.deepEqual(out, [{ label: 'Pixel_API_34', name: 'Pixel_API_34' }]);
});

test('parseAndroidAVDsFromList: multiple AVDs + skips blank lines', () => {
  // `emulator -list-avds` sometimes prints blank lines between entries
  // depending on Android tools version. Filter them out.
  const stdout = 'android-24\n\nandroid-30\nandroid-34\n';
  const out = parseAndroidAVDsFromList(stdout);
  assert.equal(out.length, 3);
  assert.deepEqual(out.map((a) => a.name), ['android-24', 'android-30', 'android-34']);
});
