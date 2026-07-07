#!/bin/bash
set -e

# Revert vibration hotfix patches for Xiaomi fuxi
# Run this from the root of your ROM source tree
# Usage: bash device/xiaomi/fuxi/patches/revert.sh

PATCHES_DIR="device/xiaomi/fuxi/patches"

if [ ! -d "packages/SystemUI" ]; then
    echo "ERROR: Run this from the ROM source root (where packages/ exists)."
    exit 1
fi

echo "==> Reverting fuxi vibration patches..."

for p in "$PATCHES_DIR"/0001-*.patch "$PATCHES_DIR"/0002-*.patch; do
    name=$(basename "$p")
    echo -n "  $name ... "
    if patch -p1 -R -r /dev/null < "$p" 2>/dev/null; then
        echo "REVERTED"
    else
        echo "SKIP (not applied or N/A)"
    fi
done

echo "==> Done!"
