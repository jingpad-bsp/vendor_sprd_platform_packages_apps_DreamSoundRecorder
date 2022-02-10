/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.unisoc.soundrecorder.tests;

import android.test.InstrumentationTestRunner;
import android.test.InstrumentationTestSuite;
import com.unisoc.soundrecorder.tests.functional.TestRecords;
import com.unisoc.soundrecorder.tests.utils.Utils;
import junit.framework.TestSuite;
import android.util.Log;


/**
 * Instrumentation Test Runner for all Soudrecorder tests.
 *
 * Precondition: Opened keyboard and wipe the userdata
 *
 * Running all tests:
 *
 * adb shell am instrument \
 *   -w com.unisoc.soundrecorder.tests/.SoundRecorderFunctionalTestRunner
 */

public class SoundRecorderFunctionalTestRunner extends InstrumentationTestRunner {
    private static String TAG = "SoundRecorderFunctionalTestRunner";

    @Override
    public TestSuite getAllTests() {
        TestSuite suite = new InstrumentationTestSuite(this);
        suite.addTestSuite(TestRecords.class);
        Log.d(TAG,"SoundRecorderFunctionalTestRunner getAllTests");
        return suite;
    }

    @Override
    public ClassLoader getLoader() {
        Log.d(TAG,"SoundRecorderFunctionalTestRunner getLoader");
        return SoundRecorderFunctionalTestRunner.class.getClassLoader();
    }
}



