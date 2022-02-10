package com.unisoc.soundrecorder.tests.functional;

import android.app.Activity;
import android.content.*;
import android.app.Instrumentation;
import android.content.Intent;
import android.test.ActivityInstrumentationTestCase;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;
import android.view.KeyEvent;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.content.ContentResolver;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.widget.ImageButton;
import android.widget.Button;
import android.view.View;

import com.sprd.soundrecorder.RecorderActivity;
import com.sprd.soundrecorder.SettingActivity;
import com.sprd.soundrecorder.service.SprdRecorder;
import com.unisoc.soundrecorder.tests.utils.Utils;
import com.sprd.soundrecorder.service.RecordingService;
import java.io.*;
import android.graphics.drawable.Drawable;
import com.android.soundrecorder.R;
import android.content.ServiceConnection;
import android.os.IBinder;

/**
 * Junit / Instrumentation test case for the RecorderActivity
 */
public class TestRecords extends ActivityInstrumentationTestCase<RecorderActivity> {
    Context context;

    private static String TAG ="TestRecords";
    private Activity mTestRecorderActivity;
    private ImageButton mTestRecordButton;
    private Button mTestStopRecordButton;
    private String recordTypeTest = "";
    private Boolean isAutoSaveTest = true;
    private RecordingService.RecordState mRecordState = null;
    private RecordingService mServiceTest = null;

    public TestRecords() {
        super("com.android.soundrecorder", RecorderActivity.class);
    }

    public final static String[] DATA_COLUMN = new String[]{
            MediaStore.Audio.Media._ID, // 0
            MediaStore.Audio.Media.DATA, //1
            MediaStore.Audio.Media.SIZE, //2
            MediaStore.Audio.Media.TITLE, //3
            MediaStore.Audio.Media.DISPLAY_NAME, //4
            MediaStore.Audio.Media.DATE_MODIFIED, //5
            MediaStore.Audio.Media.MIME_TYPE, //6
            MediaStore.Audio.Media.DURATION, //7
            MediaStore.Audio.Media.BOOKMARK //8
    };

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mTestRecorderActivity = getActivity();
        context = getInstrumentation().getTargetContext();
        mTestRecordButton = mTestRecorderActivity.findViewById(com.android.soundrecorder.R.id.recordButton);
        mTestStopRecordButton = mTestRecorderActivity.findViewById(com.android.soundrecorder.R.id.stopButton);

    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test case : satrt, pause, resume and stop recording
     * judge the record state is right or not when satrt, pause, resume and stop recording
     * judge whether the record file exists
     * check the record file type and displayname
     */
    @LargeTest
    public void testCreateRecord() throws Exception {
        Instrumentation inst = getInstrumentation();
        Log.d(TAG, "testCreateRecord: start");

        inst.runOnMainSync(new PerformClick(mTestRecordButton));
        if (context == null) {
            Log.e(TAG, "error: testCreateRecord: context is null, return");
            return;
        }

        Thread.sleep(4000);
        recordTypeTest = (String)Utils.callFuncByClassObj(SettingActivity.class.newInstance(), "getRecordSetType",
                new Class[]{Context.class}, new Object[]{context} );
        isAutoSaveTest = (Boolean) Utils.callFuncByClassObj(SettingActivity.class.newInstance(), "getIsAutoSave",
                new Class[]{Context.class}, new Object[]{context} );
        mServiceTest = (RecordingService)Utils.getFileldObjByClassObj(mTestRecorderActivity, "mService");
        Log.d(TAG," recordTypeTest: " + recordTypeTest + " isAutoSaveTest: " + isAutoSaveTest + "mServiceTest: "+ mServiceTest);

        mRecordState = (RecordingService.RecordState )Utils.getFileldObjByClassObj(mTestRecorderActivity, "mRecordState");

        String  mFileFullPathTest = mServiceTest.getRecordFileFullPath();
        String[] mDataPathTest = mFileFullPathTest.split("/");
        String mFileNmeTest =   mDataPathTest[mDataPathTest.length - 1];
        Log.d(TAG, " mFileFullPathTest: " + mFileFullPathTest + " mFileNmeTest " + mFileNmeTest);
        //test: record state
        assertEquals( "recording state,error...... ",mRecordState.mNowState  ,RecordingService.State.RECORDING_STATE  );

        Thread.sleep(1000);
        inst.runOnMainSync(new PerformClick(mTestRecordButton));
        Thread.sleep(1000);
        mRecordState = (RecordingService.RecordState )Utils.getFileldObjByClassObj(mTestRecorderActivity, "mRecordState");
        //test: record state
        assertEquals( "recording state,error...... ",mRecordState.mNowState  ,RecordingService.State.SUSPENDED_STATE  );

        Thread.sleep(1000);
        inst.runOnMainSync(new PerformClick(mTestRecordButton));
        Thread.sleep(1000);
        mRecordState = (RecordingService.RecordState )Utils.getFileldObjByClassObj(mTestRecorderActivity, "mRecordState");
        //test: record state
        assertEquals( "recording state,error...... ",mRecordState.mNowState  ,RecordingService.State.RECORDING_STATE  );

        Thread.sleep(4000);
        inst.runOnMainSync(new PerformClick(mTestStopRecordButton));
        Thread.sleep(1000);
        mRecordState = (RecordingService.RecordState )Utils.getFileldObjByClassObj(mTestRecorderActivity, "mRecordState");
        //test: record state
        assertEquals( "recording state,error...... ",mRecordState.mNowState  ,RecordingService.State.IDLE_STATE);

        //test: record file type and displaymane
        Cursor c = mTestRecorderActivity.getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                DATA_COLUMN, MediaStore.Audio.Media.DATA + "=?" , new String[]{mFileFullPathTest}, null);
        assertTrue("record file is not exist,error...... ", c != null);

        if (c != null && c.moveToFirst()) {
            assertEquals("record file type is wrong,error...... ", c.getString(6), recordTypeTest);
            if (isAutoSaveTest) {
                assertEquals("record file displayname is wrong,error...... ", c.getString(4), mFileNmeTest);
            }
        }
       Log.d(TAG, "testCreateRecord: end");

    }

    /**
     *  perform click button
     */
    private class PerformClick implements Runnable {
        View btn;
        public PerformClick(View button) {
            btn = button;
        }

        public void run() {
            btn.performClick();
        }
    }


}