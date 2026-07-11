#
# Copyright (C) 2024 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

# Inherit common AOSP configurations
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base_telephony.mk)

# Inherit device-specific configurations
$(call inherit-product, device/xiaomi/fuxi/device.mk)

# Inherit LineageOS configurations
$(call inherit-product, vendor/lineage/config/common_full_phone.mk)

# Toggle Google Apps (NikGapps) inclusion. true = gapps in ROM, false = no gapps.
# Before enabling: run vendor/addons/gapps/extract.sh. Default: false.
WITH_GAPPS := true

ifeq ($(WITH_GAPPS),true)
    $(call inherit-product, vendor/addons/gapps/gapps.mk)
endif

# Camera
$(call inherit-product-if-exists, vendor/xiaomi/camera/miuicamera.mk)

# Rom flags
PRODUCT_GMS_CLIENTID_BASE := android-xiaomi
TARGET_HAS_UDFPS := true
TARGET_INCLUDES_OEM_APP := true
TARGET_INCLUDES_DolbyVision := true

# Device identifier
PRODUCT_DEVICE := fuxi
PRODUCT_NAME := lineage_fuxi
PRODUCT_BRAND := Xiaomi
PRODUCT_MODEL := Xiaomi 13
PRODUCT_MANUFACTURER := Xiaomi

BUILD_FINGERPRINT := Xiaomi/fuxi/fuxi:16/BP2A.250605.031.A3/OS3.0.2.0.WMCCNXM:user/release-keys
