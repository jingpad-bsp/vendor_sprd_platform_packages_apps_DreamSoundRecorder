LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := isoparser:app/libs/isoparser-1.1.7.jar
include $(BUILD_MULTI_PREBUILT)

include $(CLEAR_VARS)
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := aspectjrt:app/libs/aspectjrt-1.8.2.jar
include $(BUILD_MULTI_PREBUILT)

include $(CLEAR_VARS)
LOCAL_MODULE_TAGS := optional

LOCAL_PACKAGE_NAME := DreamSoundRecorder
LOCAL_OVERRIDES_PACKAGES := SoundRecorder

LOCAL_CERTIFICATE := platform
#LOCAL_JAVA_LIBRARIES += sprd-framework
LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res
LOCAL_AAPT_FLAGS := --auto-add-overlay

LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_USE_AAPT2 := true

LOCAL_PRIVATE_PLATFORM_APIS := true

LOCAL_STATIC_ANDROID_LIBRARIES := \
          $(ANDROID_SUPPORT_DESIGN_TARGETS) \
          android-support-v13 \
          android-support-v4 \
          android-support-v7-appcompat \

LOCAL_STATIC_JAVA_LIBRARIES := \
        isoparser \
        aspectjrt

LOCAL_PROGUARD_ENABLED := disabled
include $(BUILD_PACKAGE)

# Use the folloing include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))
