#!/bin/bash

# Script to copy the latest composeOutput.json to the Android app assets

echo "Copying composeOutput.json to Android assets..."

# Check if source file exists
if [ ! -f "../../out/composeOutput.json" ]; then
    echo "❌ Error: ../../out/composeOutput.json not found"
    echo "Please run the style converter first to generate the JSON file"
    exit 1
fi

# Copy the file
cp ../../out/composeOutput.json app/src/main/assets/composeOutput.json

if [ $? -eq 0 ]; then
    echo "✅ Successfully copied composeOutput.json to assets folder"
    echo "You can now run the Android app in Android Studio"
else
    echo "❌ Error copying file"
    exit 1
fi
