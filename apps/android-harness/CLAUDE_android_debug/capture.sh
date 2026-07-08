#!/bin/bash

# CLAUDE Android Debug Capture Script
# Captures screenshots and logcat for debugging property rendering
#
# Key features:
# - Auto-cleanup: removes old screenshots before capture (configurable)
# - Resize option: scales images to max 2000px for API compatibility
# - Keep-last: optionally keeps last N screenshots instead of deleting all

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCREENSHOTS_DIR="$SCRIPT_DIR/screenshots"
LOGCAT_DIR="$SCRIPT_DIR/logcat"
PACKAGE="com.styleconverter.test"

# Configuration
MAX_IMAGE_DIMENSION=2000  # Max dimension for API-safe images
KEEP_LAST=0               # How many old screenshots to keep (0 = delete all before capture)

# Generate timestamp
TIMESTAMP=$(date +"%Y-%m-%d_%H-%M-%S")

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Find adb - check PATH first, then common locations
if command -v adb &> /dev/null; then
    ADB="adb"
elif [ -f "$HOME/Library/Android/sdk/platform-tools/adb" ]; then
    ADB="$HOME/Library/Android/sdk/platform-tools/adb"
elif [ -f "/usr/local/bin/adb" ]; then
    ADB="/usr/local/bin/adb"
else
    echo -e "${RED}Error: adb not found. Please install Android SDK platform-tools.${NC}"
    exit 1
fi

# Check for sips (macOS image tool) for resizing
HAS_SIPS=false
if command -v sips &> /dev/null; then
    HAS_SIPS=true
fi

# Check if device is connected
check_device() {
    if ! $ADB devices | grep -q "device$"; then
        echo -e "${RED}Error: No Android device connected.${NC}"
        echo "Please connect a device or start an emulator."
        exit 1
    fi
}

# Function to cleanup old screenshots
cleanup_screenshots() {
    local keep=$1
    mkdir -p "$SCREENSHOTS_DIR"

    local count=$(ls -1 "$SCREENSHOTS_DIR"/*.png 2>/dev/null | wc -l | tr -d ' ')

    if [ "$count" -eq 0 ]; then
        echo -e "${CYAN}No old screenshots to clean up${NC}"
        return
    fi

    if [ "$keep" -eq 0 ]; then
        echo -e "${YELLOW}Cleaning up $count old screenshot(s)...${NC}"
        rm -f "$SCREENSHOTS_DIR"/*.png
        echo -e "${GREEN}Cleanup complete${NC}"
    else
        local to_delete=$((count - keep))
        if [ "$to_delete" -gt 0 ]; then
            echo -e "${YELLOW}Keeping last $keep screenshots, removing $to_delete old one(s)...${NC}"
            ls -1t "$SCREENSHOTS_DIR"/*.png | tail -n "$to_delete" | xargs rm -f
            echo -e "${GREEN}Cleanup complete${NC}"
        else
            echo -e "${CYAN}Only $count screenshots, keeping all (limit: $keep)${NC}"
        fi
    fi
}

# Function to cleanup old logcat files
cleanup_logcat() {
    local keep=$1
    mkdir -p "$LOGCAT_DIR"

    local count=$(ls -1 "$LOGCAT_DIR"/*.txt 2>/dev/null | wc -l | tr -d ' ')

    if [ "$count" -eq 0 ]; then
        return
    fi

    if [ "$keep" -eq 0 ]; then
        rm -f "$LOGCAT_DIR"/*.txt
    else
        local to_delete=$((count - keep))
        if [ "$to_delete" -gt 0 ]; then
            ls -1t "$LOGCAT_DIR"/*.txt | tail -n "$to_delete" | xargs rm -f
        fi
    fi
}

# Function to resize image for API compatibility
resize_for_api() {
    local filepath="$1"
    local max_dim="$2"

    if [ "$HAS_SIPS" = true ]; then
        # Get current dimensions
        local width=$(sips -g pixelWidth "$filepath" | tail -1 | awk '{print $2}')
        local height=$(sips -g pixelHeight "$filepath" | tail -1 | awk '{print $2}')

        # Check if resize needed
        if [ "$width" -gt "$max_dim" ] || [ "$height" -gt "$max_dim" ]; then
            echo -e "${YELLOW}Resizing from ${width}x${height} to max ${max_dim}px...${NC}"

            if [ "$width" -gt "$height" ]; then
                sips --resampleWidth "$max_dim" "$filepath" > /dev/null 2>&1
            else
                sips --resampleHeight "$max_dim" "$filepath" > /dev/null 2>&1
            fi

            # Get new dimensions
            local new_width=$(sips -g pixelWidth "$filepath" | tail -1 | awk '{print $2}')
            local new_height=$(sips -g pixelHeight "$filepath" | tail -1 | awk '{print $2}')
            echo -e "${GREEN}Resized to ${new_width}x${new_height}${NC}"
        else
            echo -e "${CYAN}Image ${width}x${height} is API-safe (< ${max_dim}px)${NC}"
        fi
    else
        echo -e "${YELLOW}Warning: sips not available, skipping resize${NC}"
    fi
}

# Function to capture screenshot
capture_screenshot() {
    local resize="${1:-false}"
    local filename="screenshot_${TIMESTAMP}.png"
    local device_path="/sdcard/$filename"
    local local_path="$SCREENSHOTS_DIR/$filename"

    mkdir -p "$SCREENSHOTS_DIR"

    echo -e "${YELLOW}Capturing screenshot...${NC}"
    $ADB shell screencap -p "$device_path"
    $ADB pull "$device_path" "$local_path" > /dev/null 2>&1
    $ADB shell rm "$device_path"

    if [ -f "$local_path" ]; then
        echo -e "${GREEN}Screenshot saved: $local_path${NC}"

        if [ "$resize" = true ]; then
            resize_for_api "$local_path" "$MAX_IMAGE_DIMENSION"
        fi
    else
        echo -e "${RED}Failed to capture screenshot${NC}"
        return 1
    fi
}

# Function to capture logcat
capture_logcat() {
    local filename="logcat_${TIMESTAMP}.txt"
    local local_path="$LOGCAT_DIR/$filename"

    mkdir -p "$LOGCAT_DIR"

    echo -e "${YELLOW}Capturing logcat...${NC}"

    # Capture logcat filtered for our package and system errors
    {
        echo "=== Logcat Capture: $TIMESTAMP ==="
        echo "=== Package: $PACKAGE ==="
        echo ""
        echo "=== Application Logs ==="
        $ADB logcat -d -v time "*:S" "$PACKAGE:V" AndroidRuntime:E System.err:W 2>/dev/null
        echo ""
        echo "=== Recent Crashes (if any) ==="
        $ADB logcat -d -v time | grep -A 50 "FATAL EXCEPTION" | tail -60
    } > "$local_path"

    echo -e "${GREEN}Logcat saved: $local_path${NC}"
}

# Function to clear logcat
clear_logcat() {
    echo -e "${YELLOW}Clearing logcat buffer...${NC}"
    $ADB logcat -c
    echo -e "${GREEN}Logcat cleared${NC}"
}

# Function to show status
show_status() {
    echo -e "${CYAN}=== Debug Capture Status ===${NC}"
    echo ""

    # Count screenshots
    local screenshot_count=$(ls -1 "$SCREENSHOTS_DIR"/*.png 2>/dev/null | wc -l | tr -d ' ')
    echo "Screenshots: $screenshot_count files"
    if [ "$screenshot_count" -gt 0 ]; then
        echo "  Latest: $(ls -1t "$SCREENSHOTS_DIR"/*.png 2>/dev/null | head -1)"
        local total_size=$(du -sh "$SCREENSHOTS_DIR" 2>/dev/null | cut -f1)
        echo "  Total size: $total_size"
    fi

    echo ""

    # Count logcat files
    local logcat_count=$(ls -1 "$LOGCAT_DIR"/*.txt 2>/dev/null | wc -l | tr -d ' ')
    echo "Logcat files: $logcat_count files"
    if [ "$logcat_count" -gt 0 ]; then
        echo "  Latest: $(ls -1t "$LOGCAT_DIR"/*.txt 2>/dev/null | head -1)"
    fi

    echo ""
    echo -e "${CYAN}Device:${NC}"
    $ADB devices -l | grep -v "^List"
}

# Function to show usage
show_usage() {
    echo "Usage: $0 [command] [options]"
    echo ""
    echo "Commands:"
    echo "  all         - Clean, capture screenshot & logcat (default)"
    echo "  screenshot  - Capture screenshot only (with cleanup)"
    echo "  logcat      - Capture logcat only"
    echo "  clear       - Clear logcat buffer"
    echo "  clean       - Delete all old screenshots and logcat files"
    echo "  status      - Show capture status (file counts, sizes)"
    echo "  help        - Show this help"
    echo ""
    echo "Options:"
    echo "  --resize    - Resize screenshot to max ${MAX_IMAGE_DIMENSION}px (API-safe)"
    echo "  --keep N    - Keep last N screenshots instead of deleting all"
    echo "  --no-clean  - Skip cleanup before capture"
    echo ""
    echo "Examples:"
    echo "  $0                    # Clean all, capture screenshot + logcat"
    echo "  $0 screenshot         # Clean all screenshots, capture new one"
    echo "  $0 screenshot --resize # Capture and resize to ${MAX_IMAGE_DIMENSION}px max"
    echo "  $0 --keep 3           # Keep last 3 screenshots"
    echo "  $0 clean              # Delete all screenshots and logcat files"
    echo "  $0 status             # Show what files exist"
}

# Parse options
RESIZE=false
NO_CLEAN=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --resize)
            RESIZE=true
            shift
            ;;
        --keep)
            KEEP_LAST="$2"
            shift 2
            ;;
        --no-clean)
            NO_CLEAN=true
            shift
            ;;
        *)
            break
            ;;
    esac
done

# Main
COMMAND="${1:-all}"

case "$COMMAND" in
    all)
        check_device
        echo -e "${CYAN}=== Full Capture ===${NC}"
        if [ "$NO_CLEAN" = false ]; then
            cleanup_screenshots "$KEEP_LAST"
            cleanup_logcat "$KEEP_LAST"
        fi
        capture_screenshot "$RESIZE"
        capture_logcat
        echo ""
        echo -e "${GREEN}Capture complete!${NC}"
        echo "Files saved with timestamp: $TIMESTAMP"
        ;;
    screenshot)
        check_device
        if [ "$NO_CLEAN" = false ]; then
            cleanup_screenshots "$KEEP_LAST"
        fi
        capture_screenshot "$RESIZE"
        ;;
    logcat)
        check_device
        capture_logcat
        ;;
    clear)
        check_device
        clear_logcat
        ;;
    clean)
        echo -e "${YELLOW}Deleting all capture files...${NC}"
        rm -f "$SCREENSHOTS_DIR"/*.png 2>/dev/null
        rm -f "$LOGCAT_DIR"/*.txt 2>/dev/null
        echo -e "${GREEN}All capture files deleted${NC}"
        ;;
    status)
        show_status
        ;;
    help|--help|-h)
        show_usage
        ;;
    *)
        echo -e "${RED}Unknown command: $COMMAND${NC}"
        show_usage
        exit 1
        ;;
esac
