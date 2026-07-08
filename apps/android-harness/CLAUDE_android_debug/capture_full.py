#!/usr/bin/env python3
"""
Captures multiple screenshots while scrolling through the SDUI Component Viewer app.

Unlike the old stitching approach, this keeps screenshots separate to stay under
the 2000px API limit. With the debug-components.json (~15 components), you likely
only need 1-2 screenshots anyway.

Features:
- Auto-cleanup: deletes old screenshots before capture
- Scroll capture: captures screens while scrolling
- Chunk mode: outputs multiple API-safe images
- Optional resizing to max 2000px
"""

import subprocess
import time
import os
import sys
import argparse
from datetime import datetime

# Try to import PIL for resizing (optional)
try:
    from PIL import Image
    HAS_PIL = True
except ImportError:
    HAS_PIL = False

# Configuration
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
SCREENSHOTS_DIR = os.path.join(SCRIPT_DIR, "screenshots")
ADB_PATH = os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")

# Check common adb locations
if not os.path.exists(ADB_PATH):
    for path in ["/usr/local/bin/adb", "/opt/homebrew/bin/adb"]:
        if os.path.exists(path):
            ADB_PATH = path
            break
    else:
        # Try PATH
        ADB_PATH = "adb"

# Scroll settings
SCROLL_START_Y = 1800
SCROLL_END_Y = 400
SCROLL_DELAY = 0.5  # seconds between scroll and capture

# API limit
MAX_IMAGE_DIMENSION = 2000


def run_adb(args):
    """Run an adb command and return output."""
    cmd = [ADB_PATH] + args
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
        return result.stdout, result.stderr
    except subprocess.TimeoutExpired:
        return "", "Timeout"
    except FileNotFoundError:
        print(f"Error: adb not found at {ADB_PATH}")
        sys.exit(1)


def check_device():
    """Check if a device is connected."""
    stdout, _ = run_adb(["devices"])
    lines = stdout.strip().split('\n')
    for line in lines[1:]:
        if '\tdevice' in line:
            return True
    print("Error: No Android device connected.")
    print("Please connect a device or start an emulator.")
    sys.exit(1)


def cleanup_screenshots(keep_last=0):
    """Delete old screenshots."""
    os.makedirs(SCREENSHOTS_DIR, exist_ok=True)

    files = sorted([
        f for f in os.listdir(SCREENSHOTS_DIR)
        if f.endswith('.png')
    ], key=lambda x: os.path.getmtime(os.path.join(SCREENSHOTS_DIR, x)))

    if not files:
        print("No old screenshots to clean up")
        return

    if keep_last == 0:
        print(f"Cleaning up {len(files)} old screenshot(s)...")
        for f in files:
            os.remove(os.path.join(SCREENSHOTS_DIR, f))
    else:
        to_delete = files[:-keep_last] if keep_last < len(files) else []
        if to_delete:
            print(f"Keeping last {keep_last}, removing {len(to_delete)} old screenshot(s)...")
            for f in to_delete:
                os.remove(os.path.join(SCREENSHOTS_DIR, f))
        else:
            print(f"Only {len(files)} screenshots, keeping all (limit: {keep_last})")


def capture_screenshot(filename):
    """Capture a screenshot and pull it to local machine."""
    device_path = f"/sdcard/{filename}"
    local_path = os.path.join(SCREENSHOTS_DIR, filename)

    run_adb(["shell", "screencap", "-p", device_path])
    run_adb(["pull", device_path, local_path])
    run_adb(["shell", "rm", device_path])

    if os.path.exists(local_path):
        return local_path
    return None


def resize_image(filepath, max_dim=MAX_IMAGE_DIMENSION):
    """Resize image to stay under max dimension (requires PIL)."""
    if not HAS_PIL:
        print("Warning: PIL not available, skipping resize")
        return

    img = Image.open(filepath)
    width, height = img.size

    if width <= max_dim and height <= max_dim:
        print(f"  Image {width}x{height} is API-safe")
        return

    # Calculate new size
    if width > height:
        new_width = max_dim
        new_height = int(height * (max_dim / width))
    else:
        new_height = max_dim
        new_width = int(width * (max_dim / height))

    print(f"  Resizing {width}x{height} -> {new_width}x{new_height}")
    img_resized = img.resize((new_width, new_height), Image.Resampling.LANCZOS)
    img_resized.save(filepath, quality=95)


def scroll_down():
    """Scroll down one screen."""
    run_adb(["shell", "input", "swipe", "540", str(SCROLL_START_Y), "540", str(SCROLL_END_Y), "300"])
    time.sleep(SCROLL_DELAY)


def scroll_to_top():
    """Scroll to the top of the list."""
    print("Scrolling to top...")
    for _ in range(20):
        run_adb(["shell", "input", "swipe", "540", "500", "540", "1800", "100"])
        time.sleep(0.1)
    time.sleep(0.3)


def images_are_similar(path1, path2, threshold=0.95):
    """Check if two images are nearly identical (reached bottom)."""
    if not HAS_PIL:
        return False  # Can't compare without PIL

    try:
        img1 = Image.open(path1)
        img2 = Image.open(path2)

        # Compare bottom portion (avoid status bar)
        h = img1.height
        crop_box = (100, h - 600, img1.width - 100, h - 100)

        bottom1 = img1.crop(crop_box).resize((100, 50))
        bottom2 = img2.crop(crop_box).resize((100, 50))

        pixels1 = list(bottom1.getdata())
        pixels2 = list(bottom2.getdata())

        matching = sum(1 for p1, p2 in zip(pixels1, pixels2)
                      if sum(abs(a - b) for a, b in zip(p1[:3], p2[:3])) < 30)

        return (matching / len(pixels1)) > threshold
    except Exception:
        return False


def capture_scrolling(num_screens, resize=False):
    """Capture multiple screenshots while scrolling."""
    timestamp = datetime.now().strftime("%Y-%m-%d_%H-%M-%S")

    scroll_to_top()
    time.sleep(0.3)

    screenshots = []
    prev_path = None

    for i in range(num_screens):
        filename = f"screen_{timestamp}_{i+1:02d}.png"
        print(f"Capturing screen {i + 1}/{num_screens}...")

        path = capture_screenshot(filename)
        if path:
            if resize:
                resize_image(path)

            # Check if we've reached the bottom
            if prev_path and images_are_similar(prev_path, path):
                print("Reached bottom of list, stopping.")
                os.remove(path)
                break

            screenshots.append(path)
            prev_path = path
            print(f"  Saved: {path}")

        if i < num_screens - 1:
            scroll_down()

    return screenshots


def main():
    parser = argparse.ArgumentParser(description="Capture scrolling screenshots from Android app")
    parser.add_argument("-n", "--num-screens", type=int, default=3,
                       help="Number of screens to capture (default: 3)")
    parser.add_argument("--resize", action="store_true",
                       help=f"Resize screenshots to max {MAX_IMAGE_DIMENSION}px")
    parser.add_argument("--no-cleanup", action="store_true",
                       help="Skip cleanup of old screenshots")
    parser.add_argument("--keep", type=int, default=0,
                       help="Keep last N screenshots (default: 0 = delete all)")
    parser.add_argument("--single", action="store_true",
                       help="Capture just one screenshot (no scrolling)")

    args = parser.parse_args()

    print("=== SDUI Screenshot Capture ===\n")

    # Check device
    check_device()

    # Ensure screenshots directory exists
    os.makedirs(SCREENSHOTS_DIR, exist_ok=True)

    # Cleanup
    if not args.no_cleanup:
        cleanup_screenshots(args.keep)

    print()

    if args.single:
        # Single screenshot mode
        timestamp = datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
        filename = f"screenshot_{timestamp}.png"
        print("Capturing single screenshot...")
        path = capture_screenshot(filename)
        if path:
            if args.resize:
                resize_image(path)
            print(f"\nScreenshot saved: {path}")
        else:
            print("Failed to capture screenshot")
            sys.exit(1)
    else:
        # Multi-screen scrolling mode
        screenshots = capture_scrolling(args.num_screens, resize=args.resize)

        print(f"\n=== Capture Complete ===")
        print(f"Captured {len(screenshots)} screenshot(s)")
        for path in screenshots:
            print(f"  - {os.path.basename(path)}")

    print(f"\nFiles saved to: {SCREENSHOTS_DIR}")


if __name__ == "__main__":
    main()
