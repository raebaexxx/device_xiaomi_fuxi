LOCAL_PATH := $(call my-dir)

# This module installs nothing. It only exists so that
# LOCAL_OVERRIDES_MODULES can drop the listed packages from the product.
include $(CLEAR_VARS)

LOCAL_MODULE := RemovePackages
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_TAGS := optional

LOCAL_OVERRIDES_MODULES := \
    AppLocker \
    AxSandbox \
    Contacts \
    DeskClock \
    Dialer \
    Etar \
    ExactCalculator \
    GameSpace \
    Glimpse \
    Jelly \
    OmniJaws \
    Recorder \
    Twelve \
    messaging

LOCAL_UNINSTALLABLE_MODULE := true
LOCAL_SRC_FILES := README.md

include $(BUILD_PREBUILT)
