LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

# We only want this apk build for tests.
LOCAL_MODULE_TAGS := optional

LOCAL_STATIC_JAVA_LIBRARIES := junit android.test.legacy

LOCAL_DEX_PREOPT := false

# Include all test java files.
LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := SoundRecorderTests
LOCAL_CERTIFICATE := platform
LOCAL_INSTRUMENTATION_FOR := DreamSoundRecorder

LOCAL_SDK_VERSION := current

include $(BUILD_PACKAGE)

