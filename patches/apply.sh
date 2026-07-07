#!/bin/bash
set -e

# Apply vibration hotfix patches for Xiaomi fuxi (sm8550 QTI vibrator)
# Run this from the root of your ROM source tree (same dir as packages/, device/, etc.)
# Usage: bash device/xiaomi/fuxi/patches/apply.sh

PATCHES_DIR="device/xiaomi/fuxi/patches"

if [ ! -d "packages/SystemUI" ]; then
    echo "ERROR: Run this from the ROM source root (where packages/ exists)."
    exit 1
fi

echo "==> Applying fuxi vibration patches..."

for p in "$PATCHES_DIR"/0001-*.patch "$PATCHES_DIR"/0002-*.patch; do
    name=$(basename "$p")
    echo -n "  $name ... "
    if patch -p1 -N -r /dev/null < "$p" 2>/dev/null; then
        echo "OK"
    else
        echo "SKIP (already applied or N/A)"
    fi
done

echo "==> Done!"
