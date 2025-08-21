#!/bin/bash

# Logcat Debug Script pour MSI Decoder
# Affiche les logs en temps réel filtrés

DEVICE_ID="P112AC000688"
ADB_PATH="/mnt/c/Users/User/AppData/Local/Android/Sdk/platform-tools/adb.exe"

echo "🔍 Starting MLKit ROI Debug Logcat..."
echo "📱 Device: $DEVICE_ID"
echo "🏷️  Filtering: MSIScanner, MLKit ROI, RoiOverlayView"
echo "💡 Press Ctrl+C to stop"
echo "=========================================="

# Clear logcat buffer first
"$ADB_PATH" -s "$DEVICE_ID" logcat -c

# Stream logcat with filters for our debug logs
"$ADB_PATH" -s "$DEVICE_ID" logcat \
  -s "MSIScanner:D" \
  -s "MSIScanner:I" \
  -s "MSIScanner:W" \
  -s "MSIScanner:E" \
  -s "RoiOverlayView:D" \
  -s "RoiOverlayView:I" \
  -s "RoiOverlayView:W" \
  | grep -E "(MLKit|ROI|BoundingBox|Rotation)" \
  | while read line; do
    echo "[$(date +'%H:%M:%S')] $line"
  done