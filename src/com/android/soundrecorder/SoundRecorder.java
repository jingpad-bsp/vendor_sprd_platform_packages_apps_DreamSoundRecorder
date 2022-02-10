/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.soundrecorder;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.provider.MediaStore;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.text.InputFilter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.sprd.soundrecorder.PathSelect;
import com.sprd.soundrecorder.RecordListActivity;
import com.sprd.soundrecorder.RecordSetting;
import com.sprd.soundrecorder.RecordingFileListTabUtils;
import com.sprd.soundrecorder.SoundPicker;
import com.sprd.soundrecorder.StopWatch;
import com.sprd.soundrecorder.StorageInfos;
import com.sprd.soundrecorder.Utils;
import com.sprd.soundrecorder.frameworks.StandardFrameworks;
import com.sprd.soundrecorder.ui.RecordWavesView;

import java.io.File;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.lang.reflect.Field;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.Editable;

/**
 * Calculates remaining recording time based on available disk space and
 * optionally a maximum recording file size.
 * <p>
 * The reason why this is not trivial is that the file grows in blocks
 * every few seconds or so, while we want a smooth countdown.
 */

public class SoundRecorder extends Activity
        implements Button.OnClickListener, RecordService.RecorderListener,
        RecordService.OnUpdateTimeViewListener, TextWatcher,RecordService.SetResultListener {
    static final String TAG = "SoundRecorder";
    public static final String COMPOSER = "FMSoundRecorder";  // SPRD： add
    public static final String CALL_COMPOSER = "Call Record";
    static final String STATE_FILE_NAME = "soundrecorder.state";
    static final String RECORDER_STATE_KEY = "recorder_state";
    static final String SAMPLE_INTERRUPTED_KEY = "sample_interrupted";
    static final String MAX_FILE_SIZE_KEY = "max_file_size";

    private static final String ACTION_SOUNDRECORDER_PAUSE = "com.android.soundercorder.soundercorder.pause"; // SPRD： add

    public static final String AUDIO_3GPP = "audio/3gpp";
    public static final String AUDIO_AMR = "audio/amr";
    public static final String AUDIO_ANY = "audio/*";
    public static final String ANY_ANY = "*/*";
    public static final String AUDIO_MP4 = "audio/mp4";
    public static final String AUDIO_MP3 = "audio/mp3";//SPRD:Bug 626265 add mp3 type in soundrecorder
    /* SPRD: add @{ */
    //static final int BITRATE_AMR =  5900; // bits/sec
    //static final int BITRATE_3GPP = 5900;
    public static final int BITRATE_AMR_WB = 12650; // bits/sec
    public static final int BITRATE_AMR_NB = 5900;  // add by  zl  for bug 130427, 2013/2/27
    public static final int BITRATE_3GPP = 96000;//bug 632734Recording file sampling less than 10K signal
    public static final int BITRATE_MP3 = 320000;//SPRD:Bug 626265 add mp3 type in soundrecorder
    //the value get by  maxSize/ (bit_per_sec_for_anr/8) -1 = actaulTime
    //for example, maxSize is x and the actual record time is y, then can get the value
    public static final int BIT_PER_SEC_FOR_AMR = 6415;

    private static final int PATHSELECT_RESULT_CODE = 1;
    private static final int SOUNDPICKER_RESULT_CODE = 2;


    private Dialog mdialog;
    private Toast mToastTag = null;
    private Dialog mScannerDialog;
    private Dialog mSaveDialog = null;
    private AlertDialog mOnErrrDialog;
    private AlertDialog addMediaErrorDialog;
    private AlertDialog mErrorPermissionsDialog;
    /* @} */
    public static boolean flag = false;
    private boolean isScanning = false;

    WakeLock mWakeLock;
    private volatile boolean disableKeyguarFlag = false;
    String mRequestedType = AUDIO_3GPP;
    Recorder mRecorder;
    boolean mSampleInterrupted = false;
    String mErrorUiMessage = null; // Some error messages are displayed in the UI,
    public boolean mIsPaused = false;
    public Object mEmptyLock = new Object();
    long mMaxFileSize = -1;

    
    String mTimerFormat;
    public static final boolean UNIVERSEUI_SUPPORT = true;
    private RelativeLayout mRelativeLayout;
    public boolean fromMMS = false;

    public boolean getFromMMs() {
        return fromMMS;
    }

    boolean isRequestType = false;

    ImageButton mRecordButton;
    Button mTagButton;
    ImageButton mStopButton;

    TextView mRecordFileName;
    TextView mStateMessage2;
    private String mFileNameShow = "";
    TextView mTimerView;
    Typeface mTypeface;

    LinearLayout mExitButtons;
    Button mAcceptButton;
    Button mDiscardButton;
    private static int INPUT_MAX_LENGTH = 50;
    private RecordWavesView mWavesView;
    private BroadcastReceiver mSDCardMountEventReceiver = null;

    public static final int AMR = MediaRecorder.OutputFormat.AMR_NB;
    public static final int THREE_3GPP = MediaRecorder.OutputFormat.THREE_GPP;
    public static final int MP3 = 11;//SPRD:Bug 626265 add mp3 type in soundrecorder
    public static final int AudioEncodeMP3 = 7;//ps:this value is AUDIO_ENCODER_MP3 in mediarecorder.h
    private int mNowState = RecordService.STATE_IDLE;
    //private boolean mHasBreak = false;
    public static boolean mShowDialog = false;
    private long mRemainingTime = -100;
    private boolean isBind = false;
    public static boolean mCanCreateDir = false;
    //private static final boolean DEBUG = Debug.isDebug();
    private static final boolean DEBUG = true;
    private RecordService mService = null;
    long mSampleStart = 0;
    long mTagStart = 0;
    private boolean mIsShowWaveView = false;
    private boolean mIsResume = false;
    private EditText mNewName;
    private Button mOKButton, mCancelButton;
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) {
            Log.e(TAG, "<onServiceConnected> Service connected");
            mService = ((RecordService.SoundRecorderBinder) arg1).getService();
            if (mNowState == Recorder.RECORDING_STATE) {
                mService.RecorderPostUpdateTimer();
            }
            initService();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.e(TAG, "<onServiceDisconnected> Service dis connected");
            isBind = false;
            mService = null;
        }
    };


    private BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) {
                String reason = intent.getStringExtra("reason");
                if (reason != null && (reason.equals("homekey") || reason.equals("recentapps"))) {
                    Log.i(TAG, "homekey action = Intent.ACTION_CLOSE_SYSTEM_DIALOGS " + reason);
                    if (fromMMS) {
                        if (mNowState == Recorder.RECORDING_STATE || mNowState == Recorder.SUSPENDED_STATE) {
                            mIsPaused = true;
                            mService.stopRecord();
                        } else {
                            if (mSaveDialog != null && mSaveDialog.isShowing()) {
                                mService.saveSample(true);
                                mRecorder.resetSample();
                            }
                            if (mIsResume) {
                                runOnUiThread(new Runnable() {//bug 712309 soundrecord stop exception
                                    @Override
                                    public void run() {
                                        if (isBind) {
                                            unbindService(mServiceConnection);
                                            isBind = false;
                                        }
                                        stopService(new Intent(SoundRecorder.this, RecordService.class));
                                    }
                                });
                            }
                        }
                    }
                }
            }
        }
    };
     /* @} */

    @Override
    public void onCreate(Bundle icycle) {
        if (DEBUG) Log.d(TAG, "onCreate");
        super.onCreate(icycle);
        if (Utils.isIntentFromSoundRecordWare(getIntent())) {
            finish();
            return;
        }
        mNeedRequestPermissions = checkAndBuildPermissions();

        Intent i = getIntent();
        if (i != null) {
            String s = i.getType();
            if (s != null) {
                isRequestType = true;
            }
            if (AUDIO_AMR.equals(s) || AUDIO_3GPP.equals(s) || AUDIO_MP3.equals(s)
                    /*|| ANY_ANY.equals(s)*/) {//SPRD:Bug 626265 add mp3 type in soundrecorder
                mRequestedType = s;
            } else if (AUDIO_ANY.equals(s) || ANY_ANY.equals(s)) {
                if (DEBUG) Log.d(TAG, "Intent type is:" + s + ", Set mRequestedType is AUDIO_3GPP！");
                mRequestedType = AUDIO_3GPP;
            } else if (s != null) {
                setResult(RESULT_CANCELED);
                finish();
                return;
            }

            final String EXTRA_MAX_BYTES
                    = android.provider.MediaStore.Audio.Media.EXTRA_MAX_BYTES;
            mMaxFileSize = i.getLongExtra(EXTRA_MAX_BYTES, -1);
            String action = i.getAction();
            if (Intent.ACTION_GET_CONTENT.equals(action)
                    || MediaStore.Audio.Media.RECORD_SOUND_ACTION.equals(action)) {
                fromMMS = true;
                mMaxFileSize = i.getLongExtra("android.provider.MediaStore.extra.MAX_BYTES", 0);
            }
            AbortApplication.getInstance().addActivity(fromMMS, SoundRecorder.this);
        }

        if (UNIVERSEUI_SUPPORT) {
            final com.sprd.soundrecorder.StopWatch stopWatch = StopWatch.start("SoundRecorder");
            stopWatch.lap("setContentView");
            setContentView(R.layout.main_overlay);
            stopWatch.lap("setContentViewEnd");
            stopWatch.stopAndLog(TAG, 5);
        } else {
            final com.sprd.soundrecorder.StopWatch stopWatch = StopWatch.start("SoundRecorder");
            stopWatch.lap("setContentView");
            setContentView(R.layout.main);
            stopWatch.lap("setContentViewEnd");
            stopWatch.stopAndLog(TAG, 5);
        }
        View decorView = getWindow().getDecorView();
        int option = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        decorView.setSystemUiVisibility(option);
        getWindow().setStatusBarColor(Color.TRANSPARENT);


        mdialog = new AlertDialog.Builder(this)
                .setNegativeButton(getResources().getString(R.string.button_cancel), null)
                .setMessage(getResources().getString(R.string.storage_is_not_enough))
                .setCancelable(true)
                .create();
        mScannerDialog = new AlertDialog.Builder(this)
                .setNegativeButton(getResources().getString(R.string.button_cancel), null)
                .setMessage(getResources().getString(R.string.is_scanning))
                .setCancelable(false)
                .create();
        mScannerDialog.setCanceledOnTouchOutside(false);
        /* @} */

        PowerManager pm
                = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK,
                "SoundRecorder");

        initResourceRefs();

        setResult(RESULT_CANCELED);
        registerExternalStorageListener();
        if (icycle != null) {
            Bundle recorderState = icycle.getBundle(RECORDER_STATE_KEY);
            if (recorderState != null) {
                mService.getRecorder().restoreState(recorderState);
                mSampleInterrupted = recorderState.getBoolean(SAMPLE_INTERRUPTED_KEY, false);
                mMaxFileSize = recorderState.getLong(MAX_FILE_SIZE_KEY, -1);
            }
        }

        restoreRecordStateAndData();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        registerReceiver(mReceiver, intentFilter);
    }

    @Override
    protected void onStart() {
        if (DEBUG) Log.d(TAG, "onStart");
        super.onStart();
        if (mNeedRequestPermissions) {
            Log.d(TAG, "need request permissions before start RecordService!");
            return;
        }
        /* @} */
        Log.e(TAG, "<onStart> bind service");
        startService(new Intent(SoundRecorder.this, RecordService.class));
        if (!(isBind = bindService(new Intent(SoundRecorder.this, RecordService.class),
                mServiceConnection, BIND_AUTO_CREATE))) {
            Log.e(TAG, "<onStart> fail to bind service");
            finish();
            return;
        }
    }

    private void initService() {
        Log.i(TAG, "initService()");
        if (mService == null) {
            return;
        }
        mService.setStateChangedListener(SoundRecorder.this);
        mService.setUpdateTimeViewListener(SoundRecorder.this);
        mRecorder = mService.getRecorder();
        mNowState = mService.getCurrentState();
        mService.setMaxFileSize(mMaxFileSize);
        mService.setRecordingType(mRequestedType);
        //mVUMeter.setRecorder(mRecorder);
        Log.i(TAG, "fromMMS && !mService.getRecroderComeFrom()" + (fromMMS && !mService.getRecroderComeFrom()));
        //mService.setResultListener(SoundRecorder.this);
        if (fromMMS) {
            mService.setResultListener(SoundRecorder.this);
            if (!mService.getRecroderComeFrom()) {
                if (mNowState == Recorder.RECORDING_STATE || mNowState == Recorder.SUSPENDED_STATE) {
                    Log.i(TAG, "<initService> stop record when run from other ap");
                    mService.stopRecord();
                } else {
                    SoundRecorder.flag = true;
                }
                synchronized (RecordService.mStopSyncLock) {
                    try {
                        //bug1197402 CID110143 BAD_CHECK_OF_WAIT_COND
                        while (!SoundRecorder.flag) {
                            Log.i(TAG, "wait()");
                            RecordService.mStopSyncLock.wait();
                        }
                        mService.reset();
                        mService.fileListener.stopWatching();
                        mService.saveSample(false);
                    } catch (Exception e) {
                        Log.e(TAG, "Exception:", e);
                    }
                }
                saveTost();
                mRecorder.resetSample();
                if (mService.misStatusbarExist) {
                    mService.startNotification();
                }
                Log.i(TAG, "etInstance().exit()");
            }
        }
        AbortApplication.getInstance().exit();
        updateUi();
        SoundRecorder.flag = false;
        mService.setRecroderComeFrom(fromMMS);
        if (!mIsShowWaveView) {
            mWavesView.mWaveDataList = mService.mWaveDataList;
            mWavesView.mTagHashMap = mService.mTagHashMap;
            Log.d(TAG, "initService updateWavesView");
            updateWavesView();
        }
    }

    private void openDisableKeyGuard() {
        if (!disableKeyguarFlag) {
            disableKeyguarFlag = true;
        }
    }

    @Override
    protected void onResume() {
        if (DEBUG) Log.d(TAG, "onResume");
        super.onResume();
        mIsResume = true;
        if (mNeedRequestPermissions) {
            checkAndBuildPermissions();//bug 757814 after block screen it is not check premission the sound button is not click
            Log.d(TAG, "need request permissions before onResume");
            return;
        }
        setActivityState(false);
        sdCardCheck();
        if (UNIVERSEUI_SUPPORT && (mNowState == Recorder.RECORDING_STATE || mNowState == Recorder.SUSPENDED_STATE)) {
            mStopButton.setImageResource(R.drawable.custom_record_stop_btn);
            mStopButton.setBackgroundResource(R.drawable.custom_record_stop_btn);
        }
        String actualPath = "";
        if (mService == null) {
            actualPath = RecordService.getSavePath();
        } else {
            actualPath = RecordSetting.getStorePath(getApplicationContext());
        }
        if ("".equals(actualPath)) {
        } else if (!StorageInfos.haveEnoughStorage(actualPath)) {
            if (mdialog != null) {
                mdialog.show();
            }
        } else if (!StorageInfos.isPathExistAndCanWrite(actualPath)) {
            /* SPRD:fix bug 545385 ,sound record error after delete the storage folder @{*/
            RecordSetting.changeStorePath(getApplicationContext());
            Toast.makeText(this, R.string.use_default_path, Toast.LENGTH_SHORT).show();
            /* SPRD:fix bug 545385 ,sound record error after delete the storage folder @}*/
        }
        if (isScanning) {
            if (mScannerDialog != null) {
                mScannerDialog.show();
            }
        }
        if (mSaveDialog != null && mSaveDialog.isShowing()) {
            if (mService != null && mService.mIsDelFile) {
                mRecorder.resetSample();
                mSaveDialog.dismiss();
            }
            updateUi();
        }
        /** SPRD:Bug 619108 Recorder power consumption optimization ( @{ */
        if (mService != null && mNowState == Recorder.RECORDING_STATE) {
            mService.RecorderPostUpdateTimer();
        }
    }

    private void setActivityState(Boolean bool) {
        synchronized (mEmptyLock) {
            mIsPaused = bool;
        }
    }

    public boolean getActivityState() {
        return mIsPaused;
    }

    private void sdCardCheck() {
        if (!(StorageInfos.isInternalStorageMounted() || StorageInfos.isExternalStorageMounted())) {
            mErrorUiMessage = getResources().getString(R.string.insert_sd_card);
        } else {
            mErrorUiMessage = null;
        }
        updateUi();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.e(TAG, "<onSaveInstanceState> start");
        if (mService != null) {
            mService.saveRecordStateAndSetting();
        }
    }

    /*
     * Whenever the UI is re-created (due f.ex. to orientation change) we have
     * to reinitialize references to the views.
     */
    private void initResourceRefs() {
        mRecordButton = (ImageButton) findViewById(R.id.recordButton);
        mTagButton = (Button) findViewById(R.id.tagButton);
        mTagButton.setContentDescription(getResources().getString(R.string.button_set));
        mStopButton = (ImageButton) findViewById(R.id.stopButton);
        mStopButton.setContentDescription(getResources().getString(R.string.button_list));

        mRecordButton.setImageResource(R.drawable.custom_record_btn);
        mRecordButton.setBackgroundResource(R.drawable.custom_record_btn);
        mRecordButton.setContentDescription(getResources().getString(R.string.start_record));

        mRecordFileName = (TextView) findViewById(R.id.filename);
        mStateMessage2 = (TextView) findViewById(R.id.stateMessage2);
        mTimerView = (TextView) findViewById(R.id.timerView);

        mTypeface = Typeface.createFromAsset(getAssets(), "fonts/RobotoThin.ttf");
        mTimerView.setTypeface(mTypeface);

        mRelativeLayout = (RelativeLayout) findViewById(R.id.wavesLayout);
        mWavesView = new RecordWavesView(this);
        mRelativeLayout.addView(mWavesView);

        if (!UNIVERSEUI_SUPPORT) {
            mExitButtons = (LinearLayout) findViewById(R.id.exitButtons);
            mAcceptButton = (Button) findViewById(R.id.acceptButton);
            mDiscardButton = (Button) findViewById(R.id.discardButton);
            mDiscardButton.setOnClickListener(this);
            mAcceptButton.setOnClickListener(this);
        }
        mTagButton.setTypeface(mTypeface);
        mTagButton.setOnClickListener(this);
        mRecordButton.setOnClickListener(this);
        mStopButton.setOnClickListener(this);

        mTimerFormat = getResources().getString(R.string.timer_format);
    }

    /*
     * Make sure we're not recording music playing in the background, ask
     * the MediaPlaybackService to pause playback.
     */
    private void stopAudioPlayback() {
        //AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        //am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS
        // Shamelessly copied from MediaPlaybackService.java, which
        // should be public, but isn't.
        Intent i = new Intent("com.android.music.musicservicecommand");
        i.putExtra("command", "pause");

        sendBroadcast(i);
    }

    private void startRecord() {
        // mm04 bug 2844
        Intent intent = new Intent();
        intent.setAction(ACTION_SOUNDRECORDER_PAUSE);
        sendBroadcast(intent);
        //mRemainingTimeCalculator.reset();

        TelephonyManager pm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        @SuppressWarnings("WrongConstant")
        TelephonyManager pm1 = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE
                + "1");
        AudioManager audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        if (StandardFrameworks.getInstances().isAudioSourceActive(MediaRecorder.AudioSource.MIC) ||
                StandardFrameworks.getInstances().isAudioSourceActive(MediaRecorder.AudioSource.CAMCORDER) ||
                StandardFrameworks.getInstances().isAudioSourceActive(MediaRecorder.AudioSource.VOICE_RECOGNITION)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.same_application_running)
                    .setPositiveButton(R.string.button_ok, null)
                    .setCancelable(false)
                    .show();
            updateUi();
        } else if ((pm != null && (pm.getCallState() == TelephonyManager.CALL_STATE_OFFHOOK ||
                pm.getCallState() == TelephonyManager.CALL_STATE_RINGING))
                || (pm1 != null && (pm1.getCallState() == TelephonyManager.CALL_STATE_OFFHOOK ||
                pm1.getCallState() == TelephonyManager.CALL_STATE_RINGING))
                || audioManager.getMode() == AudioManager.MODE_IN_COMMUNICATION) {
            // mErrorUiMessage =
            // getResources().getString(R.string.phone_message);
            Toast.makeText(getApplicationContext(), R.string.phone_message,
                    Toast.LENGTH_SHORT).show();
            updateUi();
        } else if (!(StorageInfos.isInternalStorageMounted() || StorageInfos.isExternalStorageMounted())) {
            mSampleInterrupted = true;
            mErrorUiMessage = getResources().getString(R.string.insert_sd_card);
            updateUi();
            if (mNowState == Recorder.IDLE_STATE) {
                mRecordButton.setImageResource(R.drawable.custom_record_btn);
                mRecordButton.setBackgroundResource(R.drawable.custom_record_btn);
            }
        } else {

            if (!StorageInfos.isPathExistAndCanWrite(RecordService.getSavePath())) {
                RecordSetting.changeStorePath(getApplicationContext());//bug 697123 the phone reboot the record path is chanage external
                mRecorder.setState(Recorder.IDLE_STATE);
                updateUi();
                return;
            }
            if(AUDIO_AMR.equals(mRequestedType) || AUDIO_3GPP.equals(mRequestedType)||AUDIO_MP3.equals(mRequestedType)){//SPRD:Bug 626265 add mp3 type in soundrecorder
                mService.startRecording(mRequestedType);
                openDisableKeyGuard();
            } else {
                throw new IllegalArgumentException(
                        "Invalid output file type requested");
            }
        }
    }

    private Toast mToast = null;

    private void doRecord() {
        printLog(TAG, "isRequestType " + isRequestType);
        if (isScanning) {
            if (null != mScannerDialog) {
                mScannerDialog.show();
            }
            return;
        }
        if (mNowState == Recorder.IDLE_STATE) {
            Map<String, String> map = StorageInfos.getStorageInfo(RecordService.getSavePath());
            if (map != null) {
                if (Boolean.parseBoolean(map.get("isEnough"))) {
                    long size = Long.parseLong(map.get("availableBlocks"));
                    if (mRecorder == null) {
                        return;
                    } else {
                        mRecorder.setRecordMaxSize(size);
                    }
                } else {
                    if (mdialog != null) {
                        mdialog.show();
                        return;
                    }
                }
            }

            if (!StorageInfos.isPathExistAndCanWrite(RecordService.getSavePath())) {
                    RecordSetting.changeStorePath(getApplicationContext());
            }

             /* SPRD: fix bug 608091 SoundRecorder crash when start record and phone storage is full. @{ */
            if (!StorageInfos.haveEnoughStorage(StorageInfos.getInternalStorageDirectory().getPath())) {
                Toast.makeText(this, R.string.phone_storage_not_enough, Toast.LENGTH_SHORT).show();
                return;
            }
             /* @} */

            if (!isRequestType) {
                SharedPreferences recordSavePreferences = this.getSharedPreferences(RecordingFileListTabUtils.SOUNDREOCRD_TYPE_AND_DTA, MODE_PRIVATE);
                mRequestedType = recordSavePreferences.getString(RecordingFileListTabUtils.SAVE_RECORD_TYPE, AUDIO_3GPP);
            }
             /* SPRD: fix bug 521431 @{ */
            long startTime = System.currentTimeMillis();
            if (((startTime - mSampleStart) > 1000) || ((startTime - mSampleStart) < 0)) {
                Log.d(TAG, "doRecord startRecord.");
                startRecord();
                RecordSetting.cancelTimerRecord(getApplicationContext(),false);//bug 773352 com.android.soundrecorder crash
                mSampleStart = System.currentTimeMillis();
            } else {
                Log.d(TAG, "doRecord startRecord failed!");
                // SPRD: fix bug 558034 Record icon show wrong when FM is recording
                updateUi();
            }
             /* @} */
            Log.i(TAG, "mRequestedType is:" + mRequestedType);
            return;
        } else if (mNowState == Recorder.RECORDING_STATE && mRecorder.sampleFile() != null) {//bug 651092 com.android.soundrecorder happens NativeCrash
            mService.pauseRecord();
        } else if (mNowState == Recorder.SUSPENDED_STATE && mRecorder.sampleFile() != null) {//bug 651092 com.android.soundrecorder happens NativeCrash
            mService.resumeRecord();
        }
    }

    /* SPRD: add @{ */
    public void onClick(View button) {
        if (mNeedRequestPermissions) {//bug 754095 the record stop excepiton
            return;
        }
        if (!button.isEnabled())
            return;
        switch (button.getId()) {
            case R.id.recordButton:
                if (!UNIVERSEUI_SUPPORT) {
                    invalidateOptionsMenu();
                }
                 /* SPRD: fix bug 523373 @{ */
                if (!mNeedRequestPermissions) {
                    doRecord();
                }
                 /* @} */
                break;
            case R.id.tagButton:
                if (mNowState == Recorder.IDLE_STATE || mNowState == Recorder.STOP_STATE){
                    Intent intent = new Intent(SoundRecorder.this, RecordSetting.class);
                    startActivity(intent);
                    return ;
                }else {
                    if (null != mService && mService.mTagNumber >= 99) {//bug 739468 the tag number is error
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String title = SoundRecorder.this.getString(R.string.tag_limit);
                            if (mToastTag == null){//bug 739474 the toast show time is long
                                mToastTag = Toast.makeText(getApplicationContext(), title, Toast.LENGTH_SHORT);
                            }
                                mToastTag.show();
                        }
                    });
                    return;
                }
                long startTime = System.currentTimeMillis();
                if (((startTime - mTagStart) > 1000) || ((startTime - mTagStart) < 0)) {
                    Log.d(TAG, "tagButton");
                    if (mService!=null){
                        mService.addRecordTag();
                        String s = Integer.toString(mService.mTagNumber);
                        mTagButton.setText(s);
                        mTagStart = System.currentTimeMillis();
                    }
                    
                }
                }
                
                break;
            case R.id.stopButton:
                if (mNowState == Recorder.IDLE_STATE || mNowState == Recorder.STOP_STATE) {
                    if (UNIVERSEUI_SUPPORT) {
                        if (mNowState == Recorder.IDLE_STATE) {
                            if (mRecorder == null) {
                                return;
                            } else {
                                if (mRecorder.sampleFile() == null) {
                                    Intent intent = null;
                                    if (fromMMS) {
                                        intent = new Intent(SoundRecorder.this, SoundPicker.class);
                                        startActivityForResult(intent, SOUNDPICKER_RESULT_CODE);
                                    } else {
                                        //intent = new Intent(SoundRecorder.this, RecordingFileListTabUtils.class);
                                        intent = new Intent(SoundRecorder.this, RecordListActivity.class);
                                        startActivity(intent);
                                    }
                                    return;
                                }
                            }
                        }
                    }
                } else {
                    if (!UNIVERSEUI_SUPPORT) {
                        invalidateOptionsMenu();
                    }
                    //dialog();
                    SharedPreferences recordSavePreferences = this.getSharedPreferences(RecordSetting.SOUNDREOCRD_TYPE_AND_DTA, MODE_PRIVATE);
                    boolean ischeck = recordSavePreferences.getBoolean(RecordSetting.FRAG_TAG_SAVE_FILE_TYPE, true);
                    if (ischeck && mRecorder != null) {//bug 754095 the record stop excepiton
                        mRecorder.stop();
                    } else {
                        creatSaveDialog();
                        //bug1197402 CID225436 Dereference after null check
                        if (mNowState == Recorder.RECORDING_STATE && mRecorder != null && mRecorder.sampleFile() != null) {//bug 753046 the record is not stop record
                            mService.pauseRecord();
                        }
                    }
                    mTagButton.setText("");
                }

                break;
            case R.id.acceptButton:
                mRecorder.stop();
                mService.saveSample(true);
                mRecorder.resetSample();
                updateUi();
                if (fromMMS) {
                    finish();
                    break;
                }
                break;
            case R.id.discardButton:
                mRecorder.delete();
                noSaveTost();
                updateUi();
                break;
        }
    }
    private String stringFilter(String str) {
        String filter = "[/\\\\<>:*?|\"\n\t]";
        Pattern pattern = Pattern.compile(filter);
        Matcher matcher = pattern.matcher(str);
        StringBuffer buffer = new StringBuffer();
        boolean result = matcher.find();
        while (result) {
            buffer.append(matcher.group());
            result = matcher.find();
        }
        return buffer.toString();
    }
    /* @} */
    private void creatSaveDialog() {
        mSaveDialog = new Dialog(SoundRecorder.this, 0);
        mSaveDialog.setContentView(R.layout.rename_save_dialog);
        mNewName = (EditText) mSaveDialog.findViewById(R.id.name);
        mOKButton = (Button) mSaveDialog.findViewById(R.id.button_ok);
        mCancelButton = (Button) mSaveDialog.findViewById(R.id.button_cancel);
        mNewName.setText(mRecorder.mFileName);
        mNewName.setFilters(new InputFilter[]{new InputFilter.LengthFilter(INPUT_MAX_LENGTH)});
        mNewName.addTextChangedListener(this);
        mNewName.requestFocus();
        mCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSaveDialog.dismiss();
                mShowDialog = false;
                mRecorder.stop();
                mRecorder.delete();
                noSaveTost();
                updateUi();
            }
        });
        mOKButton.setOnClickListener(mSaveListener);
        mSaveDialog.setCancelable(false);
        mSaveDialog.show();
    }
    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (s.toString().length() >= INPUT_MAX_LENGTH) {
            Toast.makeText(this, R.string.input_length_overstep, Toast.LENGTH_SHORT).show();
        }
    }
    @Override
    public void afterTextChanged(Editable s) {
    }
    private View.OnClickListener mSaveListener = new View.OnClickListener() {
        public void onClick(View view) {
            String newName = mNewName.getEditableText().toString().trim();
            File sampleFile = null;
            String specialChar = stringFilter(newName);
            if (!specialChar.isEmpty()) {//bug 754387 the play file fail
                Toast.makeText(getApplicationContext(), R.string.illegal_chars_of_filename,Toast.LENGTH_LONG).show();
                return;
            }
           if(TextUtils.isEmpty(newName)){//bug  753946 the file name is error
                Toast.makeText(getApplicationContext(), R.string.filename_empty_error,
                                Toast.LENGTH_SHORT).show();
                return;
            } else if (mRecorder.mSampleDir != null) {
                sampleFile = mRecorder.mSampleDir;
                String recordFileName = sampleFile.getPath() + File.separator
                        + newName;
                sampleFile = new File(recordFileName + mRecorder.mExtension);
                if (sampleFile.exists()){//bug 753858 the repeat file is cover
                    Toast.makeText(getApplicationContext(), R.string.file_rename,
                                Toast.LENGTH_SHORT).show();
                    return;
                }
                mRecorder.mSampleDir = sampleFile;
                mRecorder.mFileName = newName;
            }
            mShowDialog = false;
            mService.mIsStopRecord=true;
            //mService.saveSample(true);//bug 757747 the recorde duration is error
            updateUi();
            mRecorder.stop();
            mSaveDialog.dismiss();
        }
    };

    private void saveTost() {
        if (mRecorder.sampleLengthSec() != 0) {
            /** SPRD:Bug 611642 DUT shows concatenated string in snack bar while save voice recorded file.( @{ */
            String title = mRecorder.mFileName + " " + this.getString(R.string.recording_save);
            /** @} */
            Toast.makeText(getApplicationContext(), title, Toast.LENGTH_SHORT).show();//bug 648157 the pop show is error
        }
    }

    private void noSaveTost() {
        Toast.makeText(getApplicationContext(), R.string.recording_nosave, Toast.LENGTH_SHORT)
                .show();
    }

    /*
     * Handle the "back" hardware key.
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            //switch (mRecorder.state()) {
            switch (mNowState) {
                case Recorder.IDLE_STATE:
                    /* SPRD: update @{ 
                    if (mRecorder.sampleLength() > 0)
                        saveSample();
                    finish();
                    */
                    if (mRecorder != null && mRecorder.sampleFile() != null) {
                        /** SPRD:Bug 615194  com.android.soundrecorder happens JavaCrash,log:java.util.concurrent.TimeoutException ( @{ */
                        mRecorder.stop();
                        /** @} */
                        noSaveTost();
                        updateUi();
                    } else {
                        finish();
                        if (mNowState == Recorder.IDLE_STATE || mNowState == Recorder.STOP_STATE) {
                            //stopService(new Intent(this, RecordService.class));
                        }
                    }
                    break;
                case Recorder.PLAYING_STATE:
                    mRecorder.stop();
                    //saveSample();
                    break;
                case Recorder.RECORDING_STATE:
                    //mRecorder.clear();
                    //break;
                case Recorder.SUSPENDED_STATE:
                    Log.e(TAG, "onKeyDown , Recorder.RECORDING_STATE:");
                    if (fromMMS) {
                        mRecorder.stop();
                    } else {
                        //return super.onKeyDown(keyCode, event);
                        moveTaskToBack(true);
                    }
                    break;
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK) {
            if (event.getRepeatCount() != 0) {
                return true;
            }
            doRecord();
            return true;
            /* @} */
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (UNIVERSEUI_SUPPORT) {
                return true;
            }
            return false;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public void onStop() {
        if (DEBUG) Log.d(TAG, "onStop");
        super.onStop();
        if (isBind) {
            unbindService(mServiceConnection);
            isBind = false;
        }
        if (fromMMS) {
            if (mSaveDialog != null && mSaveDialog.isShowing()) {
                mService.saveSample(true);
                mRecorder.resetSample();
                finish();
                updateUi();
                stopService(new Intent(SoundRecorder.this, RecordService.class));
            }
        }
    }

    @Override
    protected void onPause() {
        if (DEBUG) Log.d(TAG, "onPause");
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (mNowState == Recorder.IDLE_STATE && !pm.isScreenOn()) {
            if (mRecorder != null) {
                mRecorder.stop();
            }
        }
        mIsPaused = true;
        /** SPRD:Bug 619108 Recorder power consumption optimization ( @{ */
        if (mService != null) {
            mService.RecorderRemoveUpdateTimer();
        }
        super.onPause();
        mIsResume = false;
    }

    /*
     * Called on destroy to unregister the SD card mount event receiver.
     */
    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "onDestroy");
        if (mSDCardMountEventReceiver != null) {
            unregisterReceiver(mSDCardMountEventReceiver);
            mSDCardMountEventReceiver = null;
        }
        if (mService != null) {
            mService.fileListener.stopWatching();
            if (mShowDialog && mService.getSampleFile() != null && mSaveDialog != null) {
                mService.saveSample(true);
                mRecorder.resetSample();
                mService.startNotification();
            }
        }
        /* SPRD: add @{ */
        if (mdialog != null) {
            mdialog.dismiss();
        }
        if (mSaveDialog != null) {
            mSaveDialog.dismiss();
        }
        if (mScannerDialog != null) {
            mScannerDialog.dismiss();
        }
        /* SPRD: add @{ */
        if (mOnErrrDialog != null) {
            mOnErrrDialog.dismiss();
        }
        /* @} */
        if (null != mReceiver) {
            try {
                unregisterReceiver(mReceiver);
                mReceiver = null;
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "mReceiver not registered. ");
            }
        }
        if (addMediaErrorDialog != null) {
            addMediaErrorDialog.dismiss();
        }
        if (fromMMS
                && (mNowState == Recorder.RECORDING_STATE || mNowState == Recorder.SUSPENDED_STATE)) {
            Log.i(TAG, "isFinishing()= " + isFinishing() + " stop record");
            if (mService != null) {
                mService.stopRecord();
            }
        }
        mCanCreateDir = false;
        mShowDialog = false;
        mHandler.removeCallbacks(mUpdateWavesView);
        super.onDestroy();
    }

    /*
     * Registers an intent to listen for ACTION_MEDIA_EJECT/ACTION_MEDIA_MOUNTED
     * notifications.
     */
    private void registerExternalStorageListener() {
        if (mSDCardMountEventReceiver == null) {
            mSDCardMountEventReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent == null) {
                        return;
                    }
                    String action = intent.getAction();
                    if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                        /* SPRD: update for path @{ */
                        String path = intent.getData().getPath();
                        if (RecordService.getSavePath().startsWith(path)&&!RecordSetting.isShuntDown) {//bug 700827
                            notifyServicePathChanged(context, RecordSetting.changeStorePath(getApplicationContext()));
                            Toast.makeText(SoundRecorder.this, R.string.use_default_path, Toast.LENGTH_SHORT).show();
                            int state = mRecorder.state();
                            if (state == Recorder.RECORDING_STATE || state == Recorder.SUSPENDED_STATE || (state == Recorder.IDLE_STATE && mSaveDialog != null && mSaveDialog.isShowing())) {
                                mRecorder.stop();
                                mRecorder.delete();
                                mRecorder.resetSample();
                                if (mSaveDialog != null && mSaveDialog.isShowing()) {
                                    mSaveDialog.dismiss();
                                }
                                Toast.makeText(SoundRecorder.this, R.string.path_miss_nosave, Toast.LENGTH_LONG).show();
                            }
                        }
                        /* @} */
                    } else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                        SharedPreferences recordSavePreferences = SoundRecorder.this.getSharedPreferences(RecordSetting.SOUNDREOCRD_TYPE_AND_DTA, MODE_PRIVATE);
                        String StoragePath = recordSavePreferences.getString(RecordSetting.SAVE_STORAGE_PATH, null);
                        if (StoragePath == null){
                           notifyServicePathChanged(context, RecordSetting.changeStorePath(getApplicationContext()));//bug 770851 
                        }
                        mSampleInterrupted = false;
                        mErrorUiMessage = null;
                        updateUi();
                    }
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(Intent.ACTION_MEDIA_EJECT);
            iFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
            iFilter.addDataScheme("file");
            registerReceiver(mSDCardMountEventReceiver, iFilter);
        }
    }

    // SPRD: Bug632813  Record save in internal storage after insert sdcard begin
    private void notifyServicePathChanged(Context context, String path) {
        Intent intent = new Intent();
        intent.setAction(RecordService.PATHSELECT_BROADCAST);
        intent.putExtra("newPath", path + Recorder.DEFAULT_STORE_SUBDIR);
        context.sendBroadcast(intent);
    }


    @Override
    public void updateTimerView(boolean initial) {
        long time = 0;
        int state = mNowState;
        if (!initial) {
            if (null != mService) {
                boolean ongoing = state == Recorder.RECORDING_STATE || state == Recorder.PLAYING_STATE
                        || state == Recorder.SUSPENDED_STATE;
                int lenSec = mRecorder.sampleLengthSec();
                time = ongoing ? (mRecorder.progress()) : lenSec;

                if (mRecorder.progress() > lenSec && state == Recorder.PLAYING_STATE) {
                    time = lenSec;
                }
            }
        }
        long hour = 0;
        long minute = 0;
        long second = time;
        if (second > 59) {
            minute = second / 60;
            second = second % 60;
        }
        if (minute > 59) {
            hour = minute / 60;
            minute = minute % 60;
        }
        String timeStr = String.format(mTimerFormat, hour, minute, second);
        mTimerView.setText(timeStr);

        if (state == Recorder.RECORDING_STATE && mService != null) {
            updateTimeRemaining();
        }

    }

    /*
     * Called when we're in recording state. Find out how much longer we can
     * go on recording. If it's under 5 minutes, we display a count-down in
     * the UI. If we've run out of time, stop the recording.
     */
    private void updateTimeRemaining() {
        //long t = mRemainingTimeCalculator.timeRemaining();
        long t = mService.getTimeRemaining();
        if (t <= 0) {
            mSampleInterrupted = true;

            //int limit = mRemainingTimeCalculator.currentLowerLimit();
            int limit = mService.getCurrentLowerLimit();
            switch (limit) {
                case RemainingTimeCalculator.DISK_SPACE_LIMIT:
                    mErrorUiMessage
                            = getResources().getString(R.string.storage_is_full);
                    break;
                case RemainingTimeCalculator.FILE_SIZE_LIMIT:
                    mErrorUiMessage
                            = getResources().getString(R.string.max_length_reached);
                    break;
                default:
                    mErrorUiMessage = null;
                    break;
            }
            mRecorder.stop();
            return;
        }

        Resources res = getResources();
        String timeStr = "";
        if (fromMMS) {
            if (t < 60)
                timeStr = String.format(res.getString(R.string.sec_available), t);
            /*else if (t < 540)
                timeStr = String.format(res.getString(R.string.min_available), t/60 + 1);*/
            else if (t >= 60) {
                int sec = 0;
                int min = 0;
                while (t >= 60) {  // because subtraction is more efficiency than division.
                    t -= 60;
                    min++;
                }
                sec = (int) t;
                if (sec == 0) {
                    timeStr = String.format(res.getString(R.string.min_available), min);
                } else {
                    timeStr = String.format(res.getString(R.string.min_and_time_available), min, sec);
                }
            }
            // fix bug 250340 mErrorUiMessage is not appear in the center
            //mStateMessage1.setText(timeStr);
        } else {
            if (t < 540) {
                //fix bug 250340 mErrorUiMessage is not appear in the center
                //mStateMessage1.setText(getResources().getString(R.string.low_memory));
                //mStateMessage2.setVisibility(View.GONE);
            }
        }
        /* @} */
    }

    /**
     * Shows/hides the appropriate child views for the new state.
     */
    private void updateUi() {
        Resources res = getResources();
        switch (mNowState) {
            case Recorder.IDLE_STATE:
                mHandler.removeCallbacks(mUpdateTimer);
                mTimerView.setVisibility(View.VISIBLE);
                if (mService == null || mService.getSampleFile() == null || mService.getSampleLengthMillsec() == 0) {
                    mRecordButton.setEnabled(true);
                    mRecordButton.setFocusable(true);
                    if (!UNIVERSEUI_SUPPORT) {
                        mTagButton.setEnabled(false);
                        mTagButton.setFocusable(false);
                        mExitButtons.setVisibility(View.INVISIBLE);
                        mStopButton.setEnabled(false);
                        mStopButton.setFocusable(false);
                    }
                    mRecordButton.requestFocus();
                    mRecordFileName.setVisibility(View.INVISIBLE);
                    mStateMessage2.setText(res.getString(R.string.recording));
                    if (mSaveDialog != null && mSaveDialog.isShowing()) {
                        mSaveDialog.dismiss();
                    }
                } else {
                    mRecordButton.setEnabled(false);
                    mRecordButton.setFocusable(false);
                    if (!UNIVERSEUI_SUPPORT) {
                        mTagButton.setEnabled(true);
                        mTagButton.setFocusable(true);
                        if (RecordService.mIsUui_Support) {
                            mExitButtons.setVisibility(View.VISIBLE);
                        } else {
                            mExitButtons.setVisibility(View.INVISIBLE);
                            mRecordButton.setEnabled(true);
                            mRecordButton.setFocusable(true);
                            mTagButton.setEnabled(false);
                            mTagButton.setFocusable(false);
                        }
                        mStopButton.setEnabled(false);
                        mStopButton.setFocusable(false);
                    }
                    mStateMessage2.setVisibility(View.INVISIBLE);
                }
                mTagButton.setEnabled(true);
                mTagButton.setText("");//bug 752040 the tag num is not clear
                mTagButton.setBackgroundResource(R.drawable.custom_record_set_btn);
                mTagButton.setContentDescription(res.getString(R.string.button_set));
                mStopButton.setImageResource(R.drawable.custom_record_list_btn);
                mStopButton.setBackgroundResource(R.drawable.custom_record_list_btn);
                mStopButton.setContentDescription(res.getString(R.string.button_list));
                mRecordButton.setImageResource(R.drawable.custom_record_btn);
                mRecordButton.setBackgroundResource(R.drawable.custom_record_btn);
                mRecordButton.setContentDescription(res.getString(R.string.start_record));

                if (mErrorUiMessage != null) {
                    mStateMessage2.setVisibility(View.GONE);
                    mErrorUiMessage = null;
                }
                break;
            case Recorder.RECORDING_STATE:
                mHandler.removeCallbacks(mUpdateTimer);
                mTimerView.setVisibility(View.VISIBLE);
                mRecordButton.setEnabled(true);
                mRecordButton.setFocusable(true);
                mRecordButton.setImageResource(R.drawable.custom_record_suspend_btn);
                mRecordButton.setBackgroundResource(R.drawable.custom_record_suspend_btn);
                mRecordButton.setContentDescription(res.getString(R.string.pause));
                mTagButton.setVisibility(View.VISIBLE);
                mTagButton.setEnabled(true);
                mTagButton.setContentDescription(res.getString(R.string.button_tag));
                mTagButton.setBackgroundResource(R.drawable.custom_record_tag_btn);
                if (mService!=null&&mService.mTagNumber!=0){//bug 740596 when language chanage the app re-created
                    String s = Integer.toString(mService.mTagNumber);
                    mTagButton.setText(s);
                }
                mStopButton.setVisibility(View.VISIBLE);
                mStopButton.setEnabled(true);
                mStopButton.setImageResource(R.drawable.custom_record_stop_btn);
                mStopButton.setBackgroundResource(R.drawable.custom_record_stop_btn);
                mStopButton.setContentDescription(res.getString(R.string.record_stop));

                if (!UNIVERSEUI_SUPPORT) {
                    mTagButton.setEnabled(false);
                    mTagButton.setFocusable(false);
                    mExitButtons.setVisibility(View.INVISIBLE);
                    mStopButton.setEnabled(true);
                    mStopButton.setFocusable(true);
                }
                if (mRecorder != null)
                    mFileNameShow = mRecorder.mFileName;
                mStateMessage2.setVisibility(View.VISIBLE);
                mRecordFileName.setVisibility(View.VISIBLE);
                mRecordFileName.setText(mFileNameShow);
                mStateMessage2.setText(R.string.soundrecording);


                break;

            case Recorder.PLAYING_STATE:
                mRecordButton.setEnabled(false);
                mRecordButton.setFocusable(false);
                if (!UNIVERSEUI_SUPPORT) {
                    mTagButton.setEnabled(false);
                    mTagButton.setFocusable(false);
                    mExitButtons.setVisibility(View.VISIBLE);
                    mStopButton.setEnabled(true);
                    mStopButton.setFocusable(true);
                }
                //mStateMessage1.setVisibility(View.INVISIBLE);
                mStateMessage2.setVisibility(View.INVISIBLE);

                break;
            case Recorder.SUSPENDED_STATE:
                mHandler.removeCallbacks(mUpdateTimer);
                mHandler.post(mUpdateTimer);
                mRecordButton.setImageResource(R.drawable.custom_record_play_btn);
                mRecordButton.setBackgroundResource(R.drawable.custom_record_play_btn);
                mRecordButton.setContentDescription(res.getString(R.string.resume_record));
                mRecordButton.setEnabled(true);
                mRecordButton.setFocusable(false);
                mTagButton.setVisibility(View.VISIBLE);
                mTagButton.setEnabled(false);
                mTagButton.setBackgroundResource(R.drawable.custom_record_tag_btn);
                if (mService!=null&&mService.mTagNumber!=0){//bug 740596 when language chanage the app re-created
                    String numstr = Integer.toString(mService.mTagNumber);
                    mTagButton.setText(numstr);
                }
                mStopButton.setVisibility(View.VISIBLE);
                mStopButton.setEnabled(true);
                mStopButton.setImageResource(R.drawable.custom_record_stop_btn);
                mStopButton.setBackgroundResource(R.drawable.custom_record_stop_btn);
                if (!UNIVERSEUI_SUPPORT) {
                    mTagButton.setEnabled(false);
                    mTagButton.setFocusable(false);
                    mExitButtons.setVisibility(View.INVISIBLE);
                    mStopButton.setEnabled(true);
                    mStopButton.setFocusable(true);
                }
                //mStateMessage1.setVisibility(View.VISIBLE);
                if (mRecorder != null)
                    mFileNameShow = mRecorder.mFileName;
                mStateMessage2.setVisibility(View.VISIBLE);
                mStateMessage2.setText(R.string.pauserecording);
                mRecordFileName.setVisibility(View.VISIBLE);
                mRecordFileName.setText(mFileNameShow);
                break;
        }
        updateTimerView(false);
    }

    public void acquireWakeLock() {
        mSampleInterrupted = false;
        mErrorUiMessage = null;
        if (!mWakeLock.isHeld()) {
            mWakeLock.acquire();
        }
    }

    public void releaseWakeLock() {
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
    }

    /*
     * Called when MediaPlayer encounters an error.
     */
    public void onError(int error) {
        if (isFinishing()) {
            return;
        }
        Resources res = getResources();

        String message = null;
        switch (error) {
            case Recorder.SDCARD_ACCESS_ERROR:
                message = res.getString(R.string.error_sdcard_access);
                break;
            case Recorder.IN_CALL_RECORD_ERROR:
                // TODO: update error message to reflect that the recording could not be
                //       performed during a call.
            case Recorder.INTERNAL_ERROR:
                message = res.getString(R.string.error_app_internal);
                break;
            case Recorder.PATH_NOT_EXIST:
                message = res.getString(R.string.path_miss);
                updateUi();
                break;
            case Recorder.RECORDING_ERROR:
                /* SPRD: modify */
                if (mRecorder.sampleLengthMillsec() <= 1000) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            /** SPRD:Bug 614337 Less than 1 second recording is interrupted in other interface without saving prompt( @{ */
                            if (mIsPaused) {
                                Toast.makeText(SoundRecorder.this, R.string.recording_time_short, Toast.LENGTH_SHORT).show();
                            } else {
                                String title = SoundRecorder.this.getString(R.string.recording_time_short);
                                Toast.makeText(getApplicationContext(), title, Toast.LENGTH_SHORT).show();//bug 648157 the pop show is error
                            }
                            /** @} */
                        }
                    });
                    Log.w(TAG, "the recodering time is short");
                } else {
                    message = res.getString(R.string.record_error);
                }
                /* @} */
                break;
            default:
                break;
        }
        if (message != null && mOnErrrDialog == null) {
            /* SPRD: add @{ */
            mOnErrrDialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.app_name)
                    .setMessage(message)
                    .setPositiveButton(R.string.button_ok, new OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            if (mOnErrrDialog != null) {
                                mOnErrrDialog.dismiss();
                                mOnErrrDialog = null;
                            }
                        }
                    })
                    .setCancelable(false)
                    .show();
             /* @} */
        }
    }

    /* SPRD: add @{ */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (PATHSELECT_RESULT_CODE == requestCode) {
            Bundle bundle = null;
            if (data != null && (bundle = data.getExtras()) != null) {
                String selectPath = bundle.getString("file");
                mService.setStoragePath(selectPath);
            }
        }
        if (SOUNDPICKER_RESULT_CODE == requestCode) {
            if (data != null) {
                if (data.getData() != null) {
                    setResult(RESULT_OK, new Intent().setData(data.getData()));
                }
                this.finish();
            }
        }
    }


    protected void dialog() {
        AlertDialog.Builder builder = new Builder(SoundRecorder.this);
        builder.setTitle(R.string.dialog_title);
        builder.setMessage(R.string.dialog_message);
        builder.setPositiveButton(R.string.button_ok, new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                mShowDialog = false;
                mNeedRequestPermissions = checkAndBuildPermissions();
                if (!mNeedRequestPermissions) {
                    mService.saveSample(true);
                }
                mRecorder.resetSample();
                updateUi();
                if (fromMMS) {
                    finish();
                    if (mNowState == Recorder.IDLE_STATE || mNowState == Recorder.STOP_STATE) {
                        stopService(new Intent(SoundRecorder.this, RecordService.class));
                    }
                }
            }
        });
        builder.setNegativeButton(R.string.button_cancel, new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                mShowDialog = false;
                mRecorder.delete();
                noSaveTost();
                updateUi();
            }
        });
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                dialog.dismiss();
                mShowDialog = false;
                mRecorder.delete();
                noSaveTost();
                updateUi();
            }
        });
        builder.setCancelable(true);
        mSaveDialog = builder.create();
        if (mSaveDialog != null && !isFinishing()) {
            mSaveDialog.setCanceledOnTouchOutside(false);
            mSaveDialog.show();
            mShowDialog = true;
        }
    }

    private void printLog(String tag, String msg) {
        if (DEBUG) {
            android.util.Log.d(tag, msg);
        }
    }

    /* @} */
    private void changeStorePath() {
        String path = StorageInfos.getInternalStorageDirectory().getAbsolutePath().toString();
        if (mService != null) {
            mService.initRemainingTimeCalculator(path);
        }
        SharedPreferences fileSavePathShare = this.getSharedPreferences(PathSelect.DATA_BASE, MODE_PRIVATE);
        SharedPreferences.Editor edit = fileSavePathShare.edit();
        edit.putString(PathSelect.SAVE_PATH, path);
        edit.commit();
        if (mSaveDialog != null && mSaveDialog.isShowing()) {
            mSaveDialog.dismiss();
        }
    }

    @Override
    public void onStateChanged(int stateCode) {
        Log.e(TAG, "onStateChanged stateCode " + stateCode);
        mNowState = stateCode;
        switch (stateCode) {
            case Recorder.STOP_STATE:
                if (fromMMS && (mIsPaused || mService.mIsStopFromStatus || mService.mHasBreak)) {
                    mService.saveSample(true);
                    Log.e(TAG, "stopService");
                    runOnUiThread(new Runnable() {//bug 712309 soundrecord stop exception
                        @Override
                        public void run() {
                            finish();
                            if (isBind) {
                                unbindService(mServiceConnection);
                                isBind = false;
                            }
                            stopService(new Intent(SoundRecorder.this, RecordService.class));
                            mRecorder.resetSample();
                        }
                    });
                }
                if (mService.mHasBreak || mRecorder.mIsAudioFocus_Loss || mService.mIsStopFromStatus) {
                    mService.saveSample(true);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mRecorder.resetSample();
                            if (fromMMS) {
                                finish();
                                if (isBind) {
                                    unbindService(mServiceConnection);
                                    isBind = false;
                                }
                                stopService(new Intent(SoundRecorder.this, RecordService.class));
                            }
                        }
                    });
                } else {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            File file = mRecorder.sampleFile();
                            if (file == null || !file.exists()) {
                                //noSaveTost();
                                mRecorder.delete();
                                mRecorder.resetSample();
                                updateUi();
                                return;
                            } else if (mRecorder.sampleLengthMillsec() <= 1000) {
                                /** SPRD:Bug 614337 Less than 1 second recording is interrupted in other interface without saving prompt( @{ */
                                //Toast.makeText(SoundRecorder.this, R.string.recording_time_short, 1000).show();
                                /** @} */
                                mRecorder.delete();
                                updateUi();
                                return;
                            } else {
                                if (SoundRecorder.UNIVERSEUI_SUPPORT) {
                                    //dialog();
                                    saveRecord();
                                    SoundRecorder.flag = false;
                                }
                            }
                        }
                    });
                }
                break;

            case Recorder.SUSPENDED_STATE:
                //mService.resetRemainingTimeCalculator();
                break;
            case Recorder.PLAYING_STATE:
            case Recorder.RECORDING_STATE:
                //acquireWakeLock();
                openDisableKeyGuard();
                break;
            case Recorder.IDLE_STATE:
                mService.mHasBreak = false;
                mIsPaused = false;
                break;
            default:
                break;
        }
        mHandler.sendEmptyMessage(mNowState);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateUi();
            }
        });
    }

    private void restoreRecordStateAndData() {
        SharedPreferences recordSavePreferences = this.getSharedPreferences(RecordService.SOUNDREOCRD_STATE_AND_DTA, MODE_PRIVATE);
        mNowState = recordSavePreferences.getInt(RecordService.SAVE_RECORD_STATE, Recorder.IDLE_STATE);
        Log.e(TAG, "restoreRecordStateAndData mNowState" + mNowState);
    }

    @Override
    public void addMediaError() {
        addMediaErrorDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(R.string.error_mediadb_new_record)
                .setPositiveButton(R.string.button_ok, null)
                .setCancelable(false)
                .show();
    }

    @Override
    public void setResultRequest(Uri uri) {
        Log.i(TAG, "setResultRequest(Uri uri) " + uri);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                saveTost();
            }
        });
        if (fromMMS)
            setResult(RESULT_OK, new Intent().setData(uri).setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
    }

    private static final int RECORD_PERMISSIONS_REQUEST_CODE = 200;
    private boolean mNeedRequestPermissions = false;

    /**
     * @}
     */
    private boolean checkAndBuildPermissions() {
        int numPermissionsToRequest = 0;

        boolean requestMicrophonePermission = false;
        boolean requestStoragePermission = false;
        boolean requestPhoneStatePermission = false;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestMicrophonePermission = true;
            numPermissionsToRequest++;
        }

        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestStoragePermission = true;
            numPermissionsToRequest++;
        }

        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            requestPhoneStatePermission = true;
            numPermissionsToRequest++;
        }

        if (!requestMicrophonePermission && !requestStoragePermission
                && !requestPhoneStatePermission) {
            mCanCreateDir = true;
            return false;
        }
        String[] permissionsToRequest = new String[numPermissionsToRequest];
        int permissionsRequestIndex = 0;
        if (requestMicrophonePermission) {
            permissionsToRequest[permissionsRequestIndex] = Manifest.permission.RECORD_AUDIO;
            permissionsRequestIndex++;
        }
        if (requestStoragePermission) {
            permissionsToRequest[permissionsRequestIndex] = Manifest.permission.WRITE_EXTERNAL_STORAGE;
            permissionsRequestIndex++;
        }

        if (requestPhoneStatePermission) {
            permissionsToRequest[permissionsRequestIndex] = Manifest.permission.READ_PHONE_STATE;
        }
        requestPermissions(permissionsToRequest, RECORD_PERMISSIONS_REQUEST_CODE);
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case RECORD_PERMISSIONS_REQUEST_CODE: {
                boolean resultsAllGranted = true;
                if (grantResults.length > 0) {
                    for (int result : grantResults) {
                        if (PackageManager.PERMISSION_GRANTED != result) {
                            resultsAllGranted = false;
                            mCanCreateDir = false;
                        }
                    }
                } else {
                    //resultsAllGranted = false;//bug 643150 after modify language the permissions dialog is error
                    mCanCreateDir = false;
                }
                /* SPRD: fix bug 522124 @{ */
                if (mErrorPermissionsDialog != null) {
                    mErrorPermissionsDialog.dismiss();
                }
                /* @} */
                if (resultsAllGranted) {
                    mNeedRequestPermissions = false;
                    mCanCreateDir = true;
                    // Should start recordService  first.

                    Log.d(TAG, "<onRequestPermissionsResult> bind service");
                    startService(new Intent(SoundRecorder.this, RecordService.class));
                    if (!(isBind = bindService(new Intent(SoundRecorder.this, RecordService.class),
                            mServiceConnection, BIND_AUTO_CREATE))) {
                        Log.e(TAG, "<onStart> fail to bind service");
                        finish();
                        return;
                    }
                } else {
                    mErrorPermissionsDialog = new AlertDialog.Builder(this)
                            .setTitle(
                                    getResources()
                                            .getString(R.string.error_app_internal))
                            .setMessage(getResources().getString(R.string.error_permissions))
                            .setCancelable(false)
                            .setOnKeyListener(new Dialog.OnKeyListener() {
                                @Override
                                public boolean onKey(DialogInterface dialog, int keyCode,
                                                     KeyEvent event) {
                                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                                        finish();
                                    }
                                    return true;
                                }
                            })
                            .setPositiveButton(getResources().getString(R.string.dialog_dismiss),
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            finish();
                                        }
                                    })
                            .show();
                }
                return;
            }
        }
    }

    /* SPRD: add new feature @{ */
    final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "mHandler handleMessage msg.what = " + msg.what);
            super.handleMessage(msg);
            switch (msg.what) {
                case Recorder.RECORDING_STATE:
                    updateWavesView();
                    break;
                case Recorder.STOP_STATE:
                    mHandler.removeCallbacks(mUpdateWavesView);
                    break;
                case Recorder.IDLE_STATE:
                    mService.mWaveDataList.clear();
                    mService.mTagHashMap.clear();
                    mService.mTagNumber = 0;

                    mWavesView.mWaveDataList.clear();
                    mWavesView.mTagHashMap.clear();
                    mWavesView.mCount = 0;
                    mWavesView.invalidate();
                    mWavesView.mLastSize = 0;
                    mWavesView.mLastTag = 0;
                    break;
            }
        }
    };

    private final Runnable mUpdateWavesView = new Runnable() {
        public void run() {
            updateWavesView();
        }
    };

    public void updateWavesView() {
        if (mNowState == Recorder.RECORDING_STATE) {
            mIsShowWaveView = true;
            mWavesView.invalidate();
            mHandler.removeCallbacks(mUpdateWavesView);
            mHandler.postDelayed(mUpdateWavesView, 100);
        } else if (mNowState == Recorder.SUSPENDED_STATE) {
            Log.d(TAG, "updateWavesView: SUSPENDED_STATE mIsShowWaveView = " + mIsShowWaveView);
            if (!mIsShowWaveView) {
                mWavesView.invalidate();
            }
        }
    }

    private final Runnable mUpdateTimer = new Runnable() {
        public void run() {
            if (mTimerView.getVisibility() == View.VISIBLE) {
                mTimerView.setVisibility(View.INVISIBLE);
            } else {
                mTimerView.setVisibility(View.VISIBLE);
            }
            mHandler.postDelayed(mUpdateTimer, 500);
        }
    };

    protected void saveRecord() {
        mShowDialog = false;
        mNeedRequestPermissions = checkAndBuildPermissions();
        if (!mNeedRequestPermissions) {
            mService.saveSample(true);
        }
        mRecorder.resetSample();
        updateUi();
        if (fromMMS) {
            finish();
            if (mNowState == Recorder.IDLE_STATE || mNowState == Recorder.STOP_STATE) {
                stopService(new Intent(SoundRecorder.this, RecordService.class));
            }
        }
    }
    /* @} */
}
