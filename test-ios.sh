#!/usr/bin/env bash
#
# test-ios.sh — thin wrapper around test-all.sh that only runs the iOS
# capture + the comparison report. Useful when you're iterating on iOS
# code and don't want to wait for the Android emulator or vite server.
#
# Usage:
#     ./test-ios.sh                      # uses examples/visual-test.json
#     ./test-ios.sh examples/foo.json    # different input
#     SIM_DEVICE="iPhone 17 Pro" ./test-ios.sh
#
# For the full 3-platform run, use test-all.sh instead.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

SKIP_ANDROID=1 SKIP_WEB=1 exec "$SCRIPT_DIR/test-all.sh" "$@"
