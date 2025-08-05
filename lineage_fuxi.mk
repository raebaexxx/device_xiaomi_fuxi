#
# Copyright (C) 2025 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

# Inherit from those products. Most specific first.
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base_telephony.mk)

# Inherit from fuxi device
$(call inherit-product, device/xiaomi/fuxi/device.mk)

# Inherit from common lineage configuration
$(call inherit-product, vendor/lineage/config/common_full_phone.mk)

TARGET_DISABLE_EPPE := true

# UDFPS
TARGET_HAS_UDFPS := true

# disable/enable blur support, default is false
TARGET_ENABLE_BLUR := true

PRODUCT_NAME := lineage_fuxi
PRODUCT_DEVICE := fuxi
PRODUCT_MANUFACTURER := Xiaomi
PRODUCT_BRAND := Xiaomi
PRODUCT_MODEL := Xiaomi 13 5G

PRODUCT_GMS_CLIENTID_BASE := android-xiaomi

PRODUCT_BUILD_PROP_OVERRIDES += \
    BuildFingerprint=Xiaomi/fuxi/fuxi:15/AQ3A.240912.001/OS2.0.200.2.VMBEUXM:user/release-keys
