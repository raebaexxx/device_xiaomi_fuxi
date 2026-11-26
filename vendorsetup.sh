#!/bin/bash
#
# Device-specific patches for repositories that are not forked.
#
# The LTE+ carrier-aggregation fix lives in frameworks/base and cannot be
# expressed as a resource overlay, so keep it as a local patch. The device
# tree stays the source of truth and a plain `repo sync` followed by
# `. build/envsetup.sh` restores it without a separate frameworks/base fork.

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

# A composite Bluetooth gamepad (for example, a DualShock 4) can be added to
# the input stack one subdevice at a time. The touchpad-only view is briefly
# mistaken for a fake Bluetooth pointer and the whole device gets disabled;
# re-enable it once keyboard/gamepad sources show up.
_fuxi_apply_patch "frameworks/base" \
    "$_fuxi_patches/frameworks_base/0002-input-reenable-composite-gamepads.patch"

# Settings' new Compose homepage hardcodes its tile labels and search hints.
# Patch them to resource lookups so the Russian overlay can translate them.
_fuxi_apply_patch "packages/apps/Settings" \
    "$_fuxi_patches/packages_apps_Settings/0001-settings-homepage-translations.patch"

# The Singularity "essential notifications" app picker is an AlertDialog with a
# hardcoded 460dp text area, so it grows to nearly the full screen, and its
# title row lets the long localized title push the "show system apps" button
# out of view. Make the dialog wrap its content and cap the list height.
_fuxi_apply_patch "packages/apps/Settings/Singularity" \
    "$_fuxi_patches/packages_apps_Settings/0002-essential-notifications-compact-add-dialog.patch"

# DolbyAtmos still has user-facing Compose/Kotlin literals that a resource
# overlay cannot reach. Move them to string resources so the RU overlay applies.
_fuxi_apply_patch "packages/apps/DolbyAtmos" \
    "$_fuxi_patches/packages_apps_DolbyAtmos/0001-dolby-atmos-localizable-strings.patch"

# The stock WFD blobs are built against display config AIDL V16. Lunaris'
# display commonsys-intf repo only ships V1-V15, so add the frozen V16 API.
_fuxi_apply_patch "vendor/qcom/opensource/commonsys-intf/display" \
    "$_fuxi_patches/vendor_qcom_opensource_commonsys-intf_display/0001-display-config-add-frozen-V16.patch"

unset _fuxi_top _fuxi_patches
