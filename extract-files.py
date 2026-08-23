#!/usr/bin/env -S PYTHONPATH=../../../tools/extract-utils python3
#
# SPDX-FileCopyrightText: 2024 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

import os
import stat
from extract_utils.fixups_blob import (
    BlobFixupCtx,
    File,
    blob_fixup,
    blob_fixups_user_type,
)
from extract_utils.fixups_lib import (
    lib_fixup_vendorcompat,
    lib_fixups_user_type,
    libs_proto_3_9_1,
)
from extract_utils.main import (
    ExtractUtils,
    ExtractUtilsModule,
)
from extract_utils.tools import (
    llvm_objdump_path,
)
from extract_utils.utils import (
    run_cmd,
)

namespace_imports = [
    'device/xiaomi/sm8550-common',
    'hardware/qcom-caf/sm8550',
    'hardware/xiaomi',
    'vendor/qcom/opensource/commonsys-intf/display',
    'vendor/xiaomi/sm8550-common',
]


def blob_fixup_graphic_buffer_size(
    ctx: BlobFixupCtx,
    file: File,
    file_path: str,
    *args,
    **kwargs,
):
    # The A17 framework grew GraphicBuffer from 0x100 to 0xd30 bytes
    # (DependencyMonitor). These blobs allocate it via operator new with
    # the old compile-time size before calling the new constructor, which
    # corrupts the heap and produces null buffer handles in the MIVI
    # capture pipeline. Patch every "mov w0, #0x100" that is followed by
    # a call to operator new.
    patch_offset = None

    for line in run_cmd([llvm_objdump_path, '-d', file_path]).splitlines():
        parts = line.split(maxsplit=3)
        if len(parts) < 4:
            continue

        offset, _, instruction, operands = parts

        if patch_offset is not None:
            if '_Znwm@plt' in line:
                with open(file_path, 'rb+') as f:
                    f.seek(patch_offset)
                    f.write(b'\x00\xa6\x81\x52')  # AArch64 mov w0, #0xd30
            patch_offset = None
        elif instruction == 'mov' and operands.startswith('w0, #0x100'):
            patch_offset = int(offset[:-1], 16)


lib_fixups: lib_fixups_user_type = {
    libs_proto_3_9_1: lib_fixup_vendorcompat,
}

blob_fixups: blob_fixups_user_type = {
    (
        'odm/etc/camera/enhance_motiontuning.xml',
        'odm/etc/camera/night_motiontuning.xml',
        'odm/etc/camera/motiontuning.xml'
    ): blob_fixup()
        .regex_replace('xml=version', 'xml version'),
    (
        'odm/etc/camera/mihal_overlap/overlap_config.json',
        'odm/etc/camera/mihal_overlap/proj_overlap_config.json'
    ): blob_fixup()
        .regex_replace('com.instagram.android', ''),
    (
        'odm/lib64/libcamxcommonutils.so',
        'odm/lib64/hw/com.qti.chi.override.so',
        'odm/lib64/libchifeature2.so',
        'odm/lib64/libmialgoengine.so'
    ): blob_fixup()
        .add_needed('libprocessgroup_shim.so'),
    (
        'odm/lib64/libMiVideoFilter.so',
    ): blob_fixup()
        .clear_symbol_version('AHardwareBuffer_allocate')
        .clear_symbol_version('AHardwareBuffer_describe')
        .clear_symbol_version('AHardwareBuffer_lockPlanes')
        .clear_symbol_version('AHardwareBuffer_release')
        .clear_symbol_version('AHardwareBuffer_unlock'),
    (
        'odm/lib64/libailab_rawhdr.so',
        'odm/lib64/libxmi_high_dynamic_range_cdsp.so',
    ): blob_fixup()
        .strip_debug_sections(),
    'odm/lib64/hw/camera.xiaomi.so': blob_fixup()
        .add_needed('libprocessgroup_shim.so')
        .replace_needed('libui.so', 'libui-v34.so'),
    (
        'odm/lib64/libcom.xiaomi.grallocutils.so',
        'odm/lib64/libmis_plugin_vidhance.so',
        'odm/lib64/libcom.xiaomi.mawutils.so',
    ): blob_fixup()
        .call(blob_fixup_graphic_buffer_size),
}

module = ExtractUtilsModule(
    'fuxi',
    'xiaomi',
    blob_fixups=blob_fixups,
    lib_fixups=lib_fixups,
    namespace_imports=namespace_imports,
    check_elf=True,
    add_firmware_proprietary_file=True,
)

if __name__ == '__main__':
    utils = ExtractUtils.device_with_common(
        module, 'sm8550-common', module.vendor
    )
    utils.run()
