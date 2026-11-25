#
# Copyright (C) 2024 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

# Inherit common AOSP configurations
$(call inherit-product, build/make/target/product/full_base_telephony.mk)
$(call inherit-product, build/make/target/product/core_64_bit.mk)

# Inherit device-specific configurations
$(call inherit-product, device/xiaomi/fuxi/device.mk)

# Lunaris
WITH_GMS := true
TARGET_CUSTOM_UDFPS := true
TARGET_BOOT_ANIMATION_RES := 1080
PRODUCT_GMS_CLIENTID_BASE := android-xiaomi
TARGET_OPTIMIZED_DEXOPT := true

# Skip MatLog
TARGET_DISABLE_MATLOG := true

# Face unlock
TARGET_FACE_UNLOCK_SUPPORTED := true

# Inherit Lunaris configurations
$(call inherit-product, vendor/lineage/config/common_full_phone.mk)

# Russian translation overlay
PRODUCT_PACKAGE_OVERLAYS += device/xiaomi/fuxi/overlay-ru
PRODUCT_ENFORCE_RRO_EXCLUDED_OVERLAYS += device/xiaomi/fuxi/overlay-ru

-include vendor/lineage-priv/keys/keys.mk

PRODUCT_DEVICE := fuxi
PRODUCT_NAME := lineage_fuxi
PRODUCT_BRAND := Xiaomi
PRODUCT_MODEL := Xiaomi 13
PRODUCT_MANUFACTURER := Xiaomi

# SoC identity used by Build.SOC_MANUFACTURER (e.g. Device RKP / TrickyStore)
PRODUCT_VENDOR_PROPERTIES += \
    ro.soc.manufacturer=Qualcomm

BUILD_FINGERPRINT := Xiaomi/fuxi/fuxi:16/BP2A.250605.031.A3/OS3.0.307.0.WMCCNXM:user/release-keys
