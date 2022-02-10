package com.sprd.soundrecorder.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.FileObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.util.SparseArray;
import android.database.ContentObserver;
import android.provider.MediaStore;
import android.widget.Toast;

import com.android.soundrecorder.R;
import com.sprd.soundrecorder.RecorderActivity;
import com.sprd.soundrecorder.RecorderReceiver;
import com.sprd.soundrecorder.SettingActivity;
import com.sprd.soundrecorder.StorageInfos;
import com.sprd.soundrecorder.Utils;
import com.sprd.soundrecorder.data.WaveDataManager;

import java.text.SimpleDateFormat;
import java.io.File;
import java.util.ArrayList;
import com.sprd.soundrecorder.data.DataOpration;

/**
 * Created by jian.xu on 2017/10/23.
 */

public class RecordingService extends Service {
    private static final String TAG = RecordingService.class.getSimpleName();
    private SoundRecorderBinder mBinder = new SoundRecorderBinder();
    private RecorderListener mRecorderListener;

    private RecordState mState = new RecordState(State.IDLE_STATE, State.IDLE_STATE);
    private HandlerThread mWorkerThread;
    private HandlerThread mCheckThread;
    private Handler mWorkerHandler;
    private Handler mCheckHandler;
    private SprdRecorder mRecorder;
    private Handler mMainHandler;
    private ArrayList<Float> mWaveDataList = new ArrayList<>();
    private SparseArray<Integer> mTagHashMap = new SparseArray<>();
    private NotificationManager mNotificationManager;
    private boolean isFromOtherApp = false;

    public static final String PAUSE_RESUME_ACTION = "com.android.soundrecorder.pause";
    public static final String SUSPENDED_ACTION = "com.android.soundrecorder.suspended";
    public static final String STOP_ACTION = "com.android.soundrecorder.stop";

    public static final String CLOSE_TIMER_ACTION = "com.android.soundrecorder.closetimer";
    public static final String CLOSERECORD_MISSTIMER_ACTION = "com.android.soundrecorder.closemisstimer";

    private static final int GET_WAVEDATA_INTERNAL = 80;
    private static final int CHECK_ENVIRONMENT_INTERVAL = 800;
    private NotificationCompat.Builder mNotificationBuilder;

    private Toast mInfoToast;
    private TimeSetChangeListener mTimeSetChangeListener;
    private AudioContentObserver mAudioObserver;
    //public static final String ON_TIMER_STOP_ACTION = "com.android.soundrecorder.ontimerstop";
    private boolean mRenameOprateCancle = false;
    private String mSavetPath;

    private boolean mIsStopSync = false;

    public enum State {
        IDLE_STATE,
        STARTING_STATE,
        RECORDING_STATE,
        SUSPENDED_STATE,
        STOPPING_STATE,
        INVALID_STATE,
    }

    public static class RecordState {
        public State mLastState;
        public State mNowState;

        public RecordState(State lastState, State nowState) {
            mLastState = lastState;
            mNowState = nowState;
        }

        @Override
        public String toString() {
            return "laststate:" + mLastState + ",nowstate:" + mNowState;
        }
    }

    //for worker thread
    private static final int START_RECORD_MSG = 1;
    private static final int STOP_RECORD_MSG = 2;
    private static final int GET_WAVE_DATA = 3;

    //for check thread
    private static final int CHECK_ENVIRONMENT = 4;

    private boolean mStorageEject;

    //for main thread
    private static final int UPDATE_STATE = 1;
    private static final int NOTIFY_ERROR = 2;
    private static final int ADD_WAVEDATA_TO_WAVEVIEW = 3;
    private static final int STOP_SUCESS = 4;

    private int mTagNumber = 0;
    private WaveDataManager mWaveDate;
    private static final String CHANNEL_ID = TAG;

    private static final int RECORD_STATUS_ID = 1;
    private static final int TIMER_STATUS_ID = 2;
    private static final int MISS_TIMER_RECORD_ID = 3;

    private TmpFileListener mTmpFileListener;

    public interface RecorderListener {
        void onStateChanged(RecordState stateCode);

        void onRecordDataReturn(Uri uri);

        void onError(SprdRecorder.ErrorInfo error);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind()");
        return mBinder;
    }

    public class SoundRecorderBinder extends Binder {
        public RecordingService getService() {
            return RecordingService.this;
        }
    }
    private class AudioContentObserver extends ContentObserver {

        public AudioContentObserver() {
            super(mMainHandler);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            Log.d(TAG, "AudioContentObserver audio change! reload data,selfchange:" + selfChange);
            if (!mRecorder.recordFileExist()&&mState.mNowState == State.RECORDING_STATE){
                stopRecord(true);
            }
        }
        public void registerObserver() {
            getApplication().getContentResolver().registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    false, this);
        }

        public void unregisterObserver() {
            getApplication().getContentResolver().unregisterContentObserver(this);
        }
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        mSavetPath = SettingActivity.getRecordSavePath(this);
        if (intent == null || Utils.isIntentFromSoundRecordWare(intent)) {
            return START_NOT_STICKY;
        }
        createNotificationChannel();
        boolean prepareRecord = intent.getBooleanExtra(RecorderReceiver.PREPARE_RECORD, false);
        boolean missRecord = intent.getBooleanExtra(RecorderReceiver.MISS_RECORD, false);
        boolean timerRecord = intent.getBooleanExtra(RecorderReceiver.TIME_RECORD, false);
        Log.d(TAG, "onStartCommand,prepareRecord:" + prepareRecord + ",missRecord:" + missRecord + ",timerRecord:" + timerRecord);
        SettingActivity.RecordTimer recordTimer = SettingActivity.getStartRecordTime(this);
        if (recordTimer != null) {
            Log.d(TAG, "current state:" + mState.mNowState + "start timer record:" + recordTimer.timerString);
            if (prepareRecord) {
                if (Utils.isInCallState(getApplication())){
                    startMissTimerSetNotification(recordTimer);
                }else {
                    startRecording(SettingActivity.getRecordSetType(this), recordTimer.duration * 60 * 1000);
                }
                resetTimerSet();
            } else if (missRecord) {
                startMissTimerSetNotification(recordTimer);
                resetTimerSet();
            } else if (timerRecord) {
                startTimerSetNotification(recordTimer);
            }
        } else {
            Log.e(TAG, "the timer is null");
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate()");
        super.onCreate();
        mRecorder = new SprdRecorder(this);
        makeMainHandler(this);
        makeWorkerHandlers();
        makeCheckHandlers();
        setState(State.IDLE_STATE);

        IntentFilter commandFilter = new IntentFilter();
        commandFilter.addAction(PAUSE_RESUME_ACTION);
        commandFilter.addAction(SUSPENDED_ACTION);
        commandFilter.addAction(STOP_ACTION);
        commandFilter.addAction(CLOSE_TIMER_ACTION);
        commandFilter.addAction("com.android.deskclock.ALARM_ALERT");
        commandFilter.addAction("android.intent.action.PHONE_STATE");
        commandFilter.addAction(Intent.ACTION_NEW_OUTGOING_CALL);
        commandFilter.addAction(Intent.ACTION_SCREEN_OFF);
        commandFilter.addAction(Intent.ACTION_SCREEN_ON);
        commandFilter.addAction(Intent.ACTION_LOCALE_CHANGED);
        commandFilter.addAction(CLOSERECORD_MISSTIMER_ACTION);
        registerReceiver(mIntentReceiver, commandFilter);
        registerStorageListener();
        mAudioObserver = new AudioContentObserver();
        mAudioObserver.registerObserver();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");
        if (mState.mNowState != State.IDLE_STATE) {
            doStopRecordSync();
        }
        mMainHandler.removeCallbacksAndMessages(null);
        mWorkerHandler.removeCallbacksAndMessages(null);
        mCheckHandler.removeCallbacksAndMessages(null);
        mWorkerThread.quit();
        mCheckThread.quit();
        resetRecord();
        unregisterReceiver(mIntentReceiver);
        unregisterReceiver(mStorageReceiver);
        mAudioObserver.unregisterObserver();
        super.onDestroy();
    }

    private void registerStorageListener() {
        IntentFilter iFilter = new IntentFilter();
        iFilter.addAction(Intent.ACTION_MEDIA_EJECT);
        iFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        iFilter.addDataScheme("file");
        registerReceiver(mStorageReceiver, iFilter);
    }

    private BroadcastReceiver mStorageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String path = intent.getData().getPath();
            Log.d(TAG, "onReceive:" + action + ",path:" + path + ",current state:" + mState.mNowState);
            if ((mState.mNowState == State.RECORDING_STATE || mState.mNowState == State.SUSPENDED_STATE)
                    && Intent.ACTION_MEDIA_EJECT.equals(action)
                    && mRecorder.getRecordFileFullName().startsWith(path)) {
                Log.i(TAG, path + " is eject,so stop recording:" + mRecorder.getRecordFileFullName());
                mStorageEject = true;
                stopRecord(true);
            }
        }
    };

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "onReceive:" + action);
            switch (action) {
                case PAUSE_RESUME_ACTION:
                    if (mState.mNowState == State.RECORDING_STATE) {
                        pauseRecord();
                    } else {
                        resumeRecord();
                    }
                    break;
                case STOP_ACTION:
                    stopRecord();
                    break;
                case CLOSE_TIMER_ACTION:
                    startTimerSetNotification(null);
                    resetTimerSet();
                    break;
                case CLOSERECORD_MISSTIMER_ACTION:
                    startMissTimerSetNotification(null);
                    break;
                case Intent.ACTION_LOCALE_CHANGED:
                    if (mState.mNowState == State.RECORDING_STATE || mState.mNowState == State.SUSPENDED_STATE) {
                        updateNotification();
                    }
                    break;
            }

        }
    };

    private void makeWorkerHandlers() {
        mWorkerThread = new HandlerThread("service worker handler");
        mWorkerThread.start();
        mWorkerHandler = new Handler(mWorkerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                Log.d(TAG, "worker thread handle msg:" + msg.what + ",state:" + mState);
                switch (msg.what) {
                    case START_RECORD_MSG:
                        SprdRecorder.ErrorInfo result = mRecorder.startRecording((String) msg.obj);
                        Log.d(TAG, "start recording:" + result);
                        if (result != SprdRecorder.ErrorInfo.NO_ERROR) {
                            mMainHandler.obtainMessage(NOTIFY_ERROR, result).sendToTarget();
                        } else {
                            /* Bug1157072 start watching record's tmp file,stop recording when it is deleted or moved @{*/
                            String tmpPath = mRecorder.getTmpFilePath();
                            if (!tmpPath.isEmpty()) {
                                mTmpFileListener = new TmpFileListener(tmpPath);
                                mTmpFileListener.startWatching();
                            }
                            /* }@ */
                            if (mWaveDate != null) {
                                mWaveDate.flushData();
                            }
                            if (!SettingActivity.getIsSavePowerMode(RecordingService.this)) {
                                mWaveDate = new WaveDataManager(RecordingService.this, mRecorder.getRecordFileName());
                                getWaveData();
                            }
                            mMainHandler.obtainMessage(UPDATE_STATE, State.RECORDING_STATE).sendToTarget();
                        }

                        break;
                    case STOP_RECORD_MSG:
                        //Bug 1156820 Only the original thread that created a view hierarchy can touch its views.
                        mMainHandler.obtainMessage(UPDATE_STATE, State.STOPPING_STATE).sendToTarget();
                        String beforeName = mWaveDate != null ? mWaveDate.getWaveFileName() : null;
                        String lastFileName = mRecorder.getRecordFileName();
                        String requstType = mRecorder.getRequstType();
                        boolean isError = (boolean) (msg.obj);
                        Log.d(TAG, "stop from error:" + isError);
                        result = mRecorder.stopRecording(isError);
                        Log.i(TAG, "stop result:" + result + ",beforename:" + beforeName + ",lastname:" + lastFileName);
                        if (result == SprdRecorder.ErrorInfo.NO_ERROR && mWaveDate != null) {
                            //Bug 1177019 check having enough space to save wave and tag data
                            if (StorageInfos.haveEnoughStorage(RecordingService.this.getFilesDir().toString())) {
                                mWaveDate.flushData();
                                mWaveDate = null;
                                String lastFileNameId = DataOpration
                                        .getRecordFileId(RecordingService.this, lastFileName, requstType);
                                Log.i(TAG, "stop to rename : " + result + ",beforename:" + beforeName
                                        + ",lastname:" + lastFileNameId);
                                if (beforeName != null && !beforeName.equals(lastFileNameId)) {
                                    WaveDataManager.rename(RecordingService.this, beforeName, lastFileNameId);
                                }
                            } else {
                                Log.d(TAG, "there is not enough space to save wave and tag");
                                result = SprdRecorder.ErrorInfo.NO_ENOUGH_SPACE_SAVE_WAVE_TAG;
                            }
                        } else if (mWaveDate != null) {
                            mWaveDate.flushData();
                            mWaveDate = null;
                            WaveDataManager.deleteOriginalWaveFile(RecordingService.this, beforeName);
                        }
                        if (result != SprdRecorder.ErrorInfo.NO_ERROR) {
                            mMainHandler.obtainMessage(NOTIFY_ERROR, result).sendToTarget();
                        } else {
                            mMainHandler.obtainMessage(STOP_SUCESS).sendToTarget();
                        }
                        break;
                    case GET_WAVE_DATA:
                        if (mState.mNowState == State.RECORDING_STATE) {
                            getWaveData();
                        }
                        break;
                }
            }
        };
    }

    private void makeCheckHandlers() {
        mCheckThread = new HandlerThread("CheckStorageHandler");
        mCheckThread.start();
        mCheckHandler = new Handler(mCheckThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                Log.d(TAG,"check thread handle msg:"+msg.what +",state:"+mState);
                switch(msg.what){
                    case CHECK_ENVIRONMENT:
                        //bug 1000197 check storage environment
                        if (mState.mNowState == State.RECORDING_STATE) {
                            if (!StorageInfos.haveEnoughStorage(mSavetPath)) {
                                stopRecord();
                            }
                        }
                        break;
                }
            }
        };
    }

    private void resetRecord() {
        resetRecord(false);
    }

    private void resetRecord(boolean isError) {
        if (mWaveDate != null) {
            mWaveDate.flushData();
            mWaveDate = null;
        }
        if (mRecorder != null) {
            mRecorder.releaseRecorder(isError);
        }
        mWaveDataList.clear();
        mTagHashMap.clear();
        mWaveDate = null;
        mTagNumber = 0;
        isFromOtherApp = false;
        mStorageEject = false;
        setState(State.IDLE_STATE);
        mState = new RecordState(State.IDLE_STATE, State.IDLE_STATE);
    }

    private void makeMainHandler(Context context) {
        mMainHandler = new Handler(context.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                Log.d(TAG, "main thread handls msg：" + msg.what + ",mstate:" + mState);
                switch (msg.what) {
                    case UPDATE_STATE:
                        setState((State) msg.obj);
                        break;
                    case NOTIFY_ERROR:
                        SprdRecorder.ErrorInfo result = (SprdRecorder.ErrorInfo) msg.obj;
                        onError(result);
                        break;
                    case ADD_WAVEDATA_TO_WAVEVIEW:
                        if (mState.mNowState == State.RECORDING_STATE) {
                            Log.d(TAG, "ADD_WAVEDATA_TO_WAVEVIEW");
                            //209101 coverity scan:Boxed value is unboxed and then immediately reboxed
                            mWaveDataList.add(Float.valueOf((float) msg.obj));
                        }
                        break;
                    case STOP_SUCESS:
                        if (isFromOtherApp && mRecorderListener != null) {
                            mRecorderListener.onRecordDataReturn(mRecorder.getUri());
                        } else {
                            showSaveSuccessDialog(mRecorder.getRecordFileName());
                        }
                        resetRecord();

                        break;
                }
            }
        };
    }

    private void createNotificationChannel() {
        Log.d(TAG, "createNotificationChannel");
        if (mNotificationManager == null) {
            mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                String nameChannel = getString(R.string.app_name);
                NotificationChannel channel;
                channel = new NotificationChannel(CHANNEL_ID, nameChannel, NotificationManager.IMPORTANCE_LOW);
                /* Bug994325 when open soundrecorder app, launch icon don't show the badge default */
                channel.setShowBadge(false);
                channel.enableLights(false);
                channel.enableVibration(false);
                mNotificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void updateNotification() {
        Log.d(TAG, "updateNotification,mstate:" + mState);

        if (mState.mNowState == State.IDLE_STATE) {
            NotificationManager mNotice = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            mNotice.cancel(RECORD_STATUS_ID);
            stopForeground(true);
            return;
        }

        if (mNotificationManager == null) {
            createNotificationChannel();
        }

        mNotificationBuilder = new NotificationCompat.Builder(this);
        mNotificationBuilder.setSmallIcon(R.drawable.stat_sys_soundrecorder)
                .setContentTitle(getString(R.string.app_name))
                .setShowWhen(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setLocalOnly(true);
        if (Build.VERSION.SDK_INT >= 26) {
            Log.d(TAG, "set channel");
            mNotificationBuilder.setChannelId(CHANNEL_ID);
        }

        Intent intent = new Intent();
        intent.setAction(PAUSE_RESUME_ACTION);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
        if (mState.mNowState == State.RECORDING_STATE) {
            mNotificationBuilder.setContentText(getString(R.string.recording_now));
            mNotificationBuilder.addAction(
                    new NotificationCompat.Action(
                            R.drawable.ic_statusbar_soundrecorder_pause,
                            getString(R.string.record_pause),
                            pendingIntent));
        } else if (mState.mNowState == State.SUSPENDED_STATE) {
            mNotificationBuilder.setContentText(getString(R.string.recording_pause));
            mNotificationBuilder.addAction(
                    new NotificationCompat.Action(
                            R.drawable.ic_statusbar_soundrecorder_pause,
                            getString(R.string.resume_record),
                            pendingIntent));
        }

        intent.setAction(STOP_ACTION);
        pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
        mNotificationBuilder.addAction(
                new NotificationCompat.Action(
                        R.drawable.stat_sys_soundrecorder,
                        getString(R.string.record_stop_and_save),
                        pendingIntent));

        if (!isFromOtherApp) {//bug 740399 the status bar entry the soundrecord
            pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, RecorderActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP), 0);
        } else {
            pendingIntent = PendingIntent.getActivity(this, 0, new Intent(), 0);
        }

        mNotificationBuilder.setContentIntent(pendingIntent);
        mNotificationBuilder.getNotification().flags |= Notification.FLAG_ONGOING_EVENT;
        try {
            startForeground(RECORD_STATUS_ID, mNotificationBuilder.build());
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "catch the IllegalArgumentException " + e);
        }

    }

    private void showErrorInfoDialog(SprdRecorder.ErrorInfo errorInfo) {
        Log.d(TAG, "showErrorInfoDialog:" + errorInfo);
        switch (errorInfo) {
            case MIC_IS_USING:
                showToastWithText(getString(R.string.same_application_running));
                break;
            case IS_IN_CALLING:
                showToastWithText(getString(R.string.phone_message));
                break;
            case NO_ENOUGH_SPACE:
            case INTERNAL_NO_ENOUGH_SPACE:
                showToastWithText(getString(R.string.storage_not_enough));
                break;
            case INTERNAL_ERROR:
                /* bug1040793 show the correct prompt in different situations @{*/
                if (mStorageEject){
                    showToastWithText(getString(R.string.path_miss_nosave));
                } else {
                    showToastWithText(getString(R.string.error_app_internal));
                }
                /* }@ */
                break;
            case PATH_NOT_EXIST:
                showToastWithText(getString(R.string.path_miss));
                break;
            case DURATION_TOO_SHORT:
                /* bug906609 show wrong message when we abandon the recording of less than 1 sec @{*/
                if (!mRenameOprateCancle) {
                    showToastWithText(getString(R.string.recording_time_short));
                } else {
                    showToastWithText(getString(R.string.recording_nosave));
                }
                mRenameOprateCancle = false;
                /* }@ */
                break;
            case OTHER_ERROR:
                /* bug1040793 show the correct prompt in different situations @{*/
                if (mStorageEject){
                    showToastWithText(getString(R.string.path_miss_nosave));
                } else {
                    showToastWithText(getString(R.string.recording_nosave));
                }
                /* }@ */
                break;
            case NO_ENOUGH_SPACE_SAVE_WAVE_TAG:
                showToastWithText(getString(R.string.storage_not_enough_save_wave_tag));
                break;
            default:
                showToastWithText(getString(R.string.record_error));
                break;

        }
    }

    /* bug906609 show wrong message when we abandon the recording of less than 1 sec @{*/
    public void renameOprateCancle(boolean renameOprate) {
         mRenameOprateCancle = renameOprate;
    }
    /* }@ */

    private void showSaveSuccessDialog(String name) {
        if (mRecorderListener == null) {
            showToastWithText(getString(R.string.recording_save));
        } else {
            String finalName = name + " " + getString(R.string.recording_save);
            showToastWithText(finalName);
        }
    }


    private void showToastWithText(String text) {
        mInfoToast = Utils.showToastWithText(this, mInfoToast, text, Toast.LENGTH_SHORT);
    }

    public void setRecorderListener(RecorderListener listener) {
        mRecorderListener = listener;
        if (mRecorderListener != null) {
            mRecorderListener.onStateChanged(mState);
        }
    }

    public RecordState getRecorderState() {
        return mState;
    }

    public int getTagSize() {
        return mTagNumber;
    }

    public long getRecordingTime() {
        if (mRecorder != null) {
            return mRecorder.getRecordDuration() / 1000;
        }
        return 0;
    }

    /*public void startRecording(String requestType) {
        mWorkerHandler.obtainMessage(START_RECORD_MSG, requestType).sendToTarget();
        mRecorderListener.onStateChanged(State.STARTING_STATE);

    }*/

    //bug:996640 not set third party flag when scheduled recording is started
    public void setFromOtherApp(boolean isOtherApp){
        isFromOtherApp = isOtherApp;
    }

    public void startRecordingFromOtherApp(String requestType, long maxFileSize) {
        if (mState.mNowState == State.IDLE_STATE) {
            if (maxFileSize != -1) {
                mRecorder.setRecordMaxSize(maxFileSize);
            }
            isFromOtherApp = true;
            mWorkerHandler.obtainMessage(START_RECORD_MSG, requestType).sendToTarget();
            setState(State.STARTING_STATE);
        }
    }

    public void startRecording(String requestType, long maxFileSize) {
        if (mState.mNowState == State.IDLE_STATE) {
            if (maxFileSize != -1) {
                mRecorder.setRecordMaxSize(maxFileSize);
            }
            mWorkerHandler.obtainMessage(START_RECORD_MSG, requestType).sendToTarget();
            setState(State.STARTING_STATE);
        }
    }

    public void startRecording(String requestType, int maxDuration) {
        if (mState.mNowState == State.IDLE_STATE) {
            if (maxDuration != -1) {
                mRecorder.setMaxDuration(maxDuration);
            }
            mWorkerHandler.obtainMessage(START_RECORD_MSG, requestType).sendToTarget();
            setState(State.STARTING_STATE);
        }
    }

    public void pauseRecord() {
        if (mState.mNowState == State.RECORDING_STATE) {
            SprdRecorder.ErrorInfo result = mRecorder.pauseRecording();
            if (result != SprdRecorder.ErrorInfo.NO_ERROR) {
                onError(result);
            } else {
                setState(State.SUSPENDED_STATE);
            }
            mWorkerHandler.removeMessages(GET_WAVE_DATA);
            mCheckHandler.removeMessages(CHECK_ENVIRONMENT);
        }
    }

    public void resumeRecord() {
        if (mState.mNowState == State.SUSPENDED_STATE) {
            SprdRecorder.ErrorInfo result = mRecorder.resumeRecording();
            if (result != SprdRecorder.ErrorInfo.NO_ERROR) {
                onError(result);
            } else {
                setState(State.RECORDING_STATE);
                mWorkerHandler.sendEmptyMessage(GET_WAVE_DATA);
                mCheckHandler.sendEmptyMessage(CHECK_ENVIRONMENT);
            }
        }
    }

    public void stopRecord() {
        stopRecord(false);
    }

    public void stopRecord(boolean isError) {
        if (mTmpFileListener != null) {
            mTmpFileListener.stopWatching();
            mTmpFileListener = null;
        }
        if (mState.mNowState == State.RECORDING_STATE || mState.mNowState == State.SUSPENDED_STATE) {
            mWorkerHandler.obtainMessage(STOP_RECORD_MSG, isError).sendToTarget();
            //Bug 1156820 Only the original thread that created a view hierarchy can touch its views.
            //setState(State.STOPPING_STATE);
        }
    }


    public void doStopRecordSync() {
        mIsStopSync = true;
        if (mTmpFileListener != null) {
            mTmpFileListener.stopWatching();
            mTmpFileListener = null;
        }
        SprdRecorder.ErrorInfo result = mRecorder.stopRecording();
        Log.d(TAG, "doStopRecordSync result:" + result);
        /*bug:996636 not save the wave file through third party app @{*/
        String beforeName = mWaveDate != null ? mWaveDate.getWaveFileName() : null;
        String lastFileName = mRecorder.getRecordFileName();
        String requstType = mRecorder.getRequstType();
        if (result == SprdRecorder.ErrorInfo.NO_ERROR && mWaveDate != null) {
            mWaveDate.flushData();
            mWaveDate = null;
            String lastFileNameId = DataOpration
                    .getRecordFileId(RecordingService.this, lastFileName, requstType);
            if (beforeName != null && !beforeName.equals(lastFileNameId)) {
                WaveDataManager.rename(RecordingService.this, beforeName,
                        lastFileNameId);
            }
        } else if (mWaveDate != null) {
            mWaveDate.flushData();
            mWaveDate = null;
            WaveDataManager.deleteOriginalWaveFile(RecordingService.this,
                    beforeName);
        }
        /*}@*/
        if (result != SprdRecorder.ErrorInfo.NO_ERROR) {
            onError(result);
        } else {
            if (isFromOtherApp && mRecorderListener != null) {
                mRecorderListener.onRecordDataReturn(mRecorder.getUri());
            } else {
                showSaveSuccessDialog(mRecorder.getRecordFileName());
            }
            mRecorder.releaseRecorder(false);
            resetRecord();
        }
    }

    public void stopRecord(String newName) {
        if (mRecorder != null) {
            mRecorder.setRecordFileName(newName);
            stopRecord();
        }

    }

    private void setState(State state) {
        mState = new RecordState(mState.mNowState, state);
        if (state == State.RECORDING_STATE ||
                state == State.SUSPENDED_STATE ||
                state == State.IDLE_STATE) {
            updateNotification();
        }
        if (state == State.IDLE_STATE) {
            startTimerSetNotification(SettingActivity.getStartRecordTime(RecordingService.this));
        }
        if (mRecorderListener != null) {
            mRecorderListener.onStateChanged(mState);
        }
    }

    private void onError(SprdRecorder.ErrorInfo errorInfo) {
        showErrorInfoDialog(errorInfo);
        if (mRecorderListener != null) {
            mRecorderListener.onError(errorInfo);
        }
        resetRecord(true);
        setState(State.IDLE_STATE);
    }

    public ArrayList getWaveDataList() {
        return mWaveDataList;
    }

    public SparseArray getTagDataList() {
        return mTagHashMap;
    }

    public void addRecordTag() {
        mTagNumber = mTagNumber + 1;
        mTagHashMap.put(mWaveDataList.size(), mTagNumber);
        if (mWaveDate != null) {
            mWaveDate.addTagData(mTagNumber, mWaveDataList.size());
        }
    }

    public void getWaveData() {
        float amplitude = mRecorder.getCurrentWaveData();
        //Log.d(TAG, "get wave data:" + amplitude);
        if (amplitude != 0) {
            if (mWaveDate != null) {
                mWaveDate.addWaveData(amplitude);
            }
            mMainHandler.obtainMessage(ADD_WAVEDATA_TO_WAVEVIEW, amplitude).sendToTarget();
        }

        mWorkerHandler.sendEmptyMessageDelayed(GET_WAVE_DATA, GET_WAVEDATA_INTERNAL);
        mCheckHandler.sendEmptyMessageDelayed(CHECK_ENVIRONMENT,CHECK_ENVIRONMENT_INTERVAL);
    }

    public String getRecordFileName() {
        if (mRecorder != null) {
            return mRecorder.getRecordFileName();
        }
        return "";
    }

    public String getRecordFileFullPath() {
        if (mRecorder != null) {
            return mRecorder.getRecordFileFullName();
        }
        return "";
    }

    public void setTimerSet(SettingActivity.RecordTimer recordTimer) {
        if (mState.mNowState != State.IDLE_STATE) {
            Log.d(TAG, "is recording , not show notificaiton");
            return;
        }
        //mRecordTimer = recordTimer;
        if (!Utils.isInCallState(getApplication())){
            startTimerSetNotification(recordTimer);
        }
    }

    private void startTimerSetNotification(SettingActivity.RecordTimer recordTimer) {

        if (recordTimer == null) {
            stopForeground(true);
            return;
        }
        Log.d(TAG, "record timer:" + recordTimer.timerString);
        Notification.Builder builder = new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.stat_sys_soundrecorder_timing);
        builder.setShowWhen(false);
        builder.setAutoCancel(false);
        builder.setOngoing(true);
        builder.setContentTitle(getString(R.string.tuned_timer_recording));
        builder.setContentText(recordTimer.timerString);
        if (Build.VERSION.SDK_INT >= 26) {
            builder.setChannelId(CHANNEL_ID);
        }

        Intent intent = new Intent();
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.setClassName(getPackageName(), SettingActivity.class.getName());
        PendingIntent pIntent = PendingIntent.getActivity(this, 0, intent, 0);

        builder.setContentIntent(pIntent);
        Intent intentclose = new Intent();
        intentclose.setAction(CLOSE_TIMER_ACTION);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intentclose, 0);
        builder.addAction(new Notification.Action(R.drawable.stat_sys_soundrecorder_timing,
                getApplicationContext().getString(R.string.close_timer), pendingIntent));
        startForeground(TIMER_STATUS_ID, builder.build());
    }

    private void startMissTimerSetNotification(SettingActivity.RecordTimer recordTimer) {
        if (recordTimer == null) {
            stopForeground(true);
            return;
        }
        if (mState.mNowState != State.IDLE_STATE) {
            Log.d(TAG, "is recording , not show notificaiton");
            return;
        }
        Notification.Builder builder = new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.stat_sys_soundrecorder_timing);
        builder.setContentTitle(getString(R.string.miss_timer_recording));
        if (Build.VERSION.SDK_INT >= 26) {
            builder.setChannelId(CHANNEL_ID);
        }

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        String contentText = String.format(getResources().getString(R.string.miss_timer_time),
                format.format(recordTimer.startTimeMillis));
        builder.setContentText(contentText);

        Intent intent = new Intent();
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.setClassName(getPackageName(), SettingActivity.class.getName());
        PendingIntent pIntent = PendingIntent.getActivity(this, 0, intent, 0);
        builder.setContentIntent(pIntent);
        Intent intentclose = new Intent();
        intentclose.setAction(CLOSERECORD_MISSTIMER_ACTION);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intentclose, 0);
        builder.addAction(new Notification.Action(R.drawable.stat_sys_soundrecorder_timing,
                getApplicationContext().getString(R.string.close_timer), pendingIntent));
        startForeground(MISS_TIMER_RECORD_ID, builder.build());
    }

    public interface TimeSetChangeListener {
        void OnCloseTimerByNotification();
    }

    public void setTimeSetChangeListener(TimeSetChangeListener listener) {
        this.mTimeSetChangeListener = listener;
    }

    public void resetTimerSet() {
        Log.d(TAG, "resetTimerSet");
        SettingActivity.saveStartRecordTime(this, null);
        if (mTimeSetChangeListener != null) {
            mTimeSetChangeListener.OnCloseTimerByNotification();
        }
    }

    /* Bug1157072 start watching record's tmp file,stop recording when it is deleted or moved @{*/
    public class TmpFileListener extends FileObserver {
        public TmpFileListener(String path) {
            super(path, FileObserver.MOVED_FROM | FileObserver.DELETE);
        }

        @Override
        public void onEvent(int event, String path) {
            Log.d(TAG, "tmpFileListener, event:" + event + " path:" + path);
            if (!mIsStopSync) {
                switch (event) {
                    case FileObserver.MOVED_FROM:
                    case FileObserver.DELETE:
                        if (path == null || path.isEmpty()) {
                            return;
                        }
                        String tmpFileName = mRecorder.getTmpFileName();
                        Log.d(TAG, "onEvent, tmpFileName:" + tmpFileName);
                        if (path.equals(tmpFileName)) {
                            stopRecord();
                        }
                        break;
                }
            }
        }
    }

    public void setmIsStopSync(boolean mIsStopSync) {
        this.mIsStopSync = mIsStopSync;
    }
    /* }@ */

}
