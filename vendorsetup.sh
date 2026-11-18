#!/bin/bash
#
# Device-specific patches for repositories that are not forked.
#
# The LTE+ carrier-aggregation fix lives in frameworks/base, but the fuxi
# device tree is the source of truth for it. Apply the patch here so a
# plain `repo sync` followed by `. build/envsetup.sh` restores it without
# maintaining a separate frameworks/base fork.

_fuxi_top="$(gettop 2>/dev/null)"
if [ -z "$_fuxi_top" ]; then
    _fuxi_top="$PWD"
    while [ "$_fuxi_top" != "/" ] && [ ! -d "$_fuxi_top/.repo" ]; do
        _fuxi_top="$(dirname "$_fuxi_top")"
    done
fi

_fuxi_patches="$_fuxi_top/device/xiaomi/fuxi/patches"

_fuxi_apply_patch() {
    local repo="$1"
    local patch="$2"

    [ -d "$_fuxi_top/$repo/.git" ] || return 0
    [ -f "$patch" ] || return 0

    # Already applied (or committed locally).
    if git -C "$_fuxi_top/$repo" apply --check --reverse "$patch" >/dev/null 2>&1; then
        return 0
    fi

    if git -C "$_fuxi_top/$repo" apply --check "$patch" >/dev/null 2>&1; then
        echo "fuxi: applying $(basename "$patch") to $repo"
        git -C "$_fuxi_top/$repo" apply "$patch"
    else
        echo "fuxi: WARNING: $(basename "$patch") does not apply cleanly to $repo"
    fi
}

_fuxi_apply_patch "frameworks/base" \
    "$_fuxi_patches/frameworks_base/0001-telephony-report-real-hardware-CA.patch"

unset _fuxi_top _fuxi_patches
