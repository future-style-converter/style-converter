# CLAUDE Android Debug

Debug folder for capturing screenshots and logs from the Style Converter Android test app.

## Usage

### Capture screenshot + logcat
```bash
./capture.sh
```

### Capture screenshot only
```bash
./capture.sh screenshot
```

### Capture logcat only
```bash
./capture.sh logcat
```

### Clear logcat before testing
```bash
./capture.sh clear
```

## Workflow

1. **Clear logcat** before testing: `./capture.sh clear`
2. **Run the app** and scroll to the components you want to verify
3. **Capture** when ready: `./capture.sh`
4. **Share with Claude** - Claude can read the screenshot and logcat files

## Folder Structure

```
CLAUDE_android_debug/
├── screenshots/       # PNG screenshots from device
├── logcat/           # Logcat text files
├── capture.sh        # Capture script
└── README.md         # This file
```

## Requirements

- Android device connected via USB or emulator running
- `adb` available in PATH (Android SDK platform-tools)
- USB debugging enabled on device
