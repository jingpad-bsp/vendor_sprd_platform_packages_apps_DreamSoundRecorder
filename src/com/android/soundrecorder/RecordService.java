package com.android.soundrecorder;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDiskIOException;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.FileObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.support.v4.app.NotificationCompat;
import android.telephony.TelephonyManager;
import android.Manifest;
import android.content.pm.PackageManager;
import android.util.Log;
import android.util.SparseArray;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.android.soundrecorder.Recorder.StateEvent;
import com.sprd.soundrecorder.AlarmReceiver;
import com.sprd.soundrecorder.PathSelect;
import com.sprd.soundrecorder.RecordSetting;
import com.sprd.soundrecorder.RecordingFileListTabUtils;
import com.sprd.soundrecorder.StorageInfos;
import com.sprd.soundrecorder.Utils;
import com.sprd.soundrecorder.data.WaveDataManager;
import com.sprd.soundrecorder.frameworks.StandardFrameworks;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class RecordService extends Service implements Recorder.OnStateChangedListener, RecordSetting.OnTimerStateChangedListener {
    static final String TAG = "RecordService";
    public final static String SAVE_RECORD_STATE = "recordState";
    public final static String SOUNDREOCRD_STATE_AND_DTA = "soundrecord.state.and.data";
    public static final String HANDLER_THREAD_NAME = "SoundRecorderServiceHandler";
    public static final String PAUSE_ACTION = "com.android.soundrecorder.pause";
    public static final String SUSPENDED_ACTION = "com.android.soundrecorder.suspended";
    public static final String STOP_ACTION = "com.android.soundrecorder.stop";
    public static final String CLOSERECORD_ACTION = "com.android.soundrecorder.closetimer";
    public static final String CLOSERECORD_MISSTIMER_ACTION = "com.android.soundrecorder.closemisstimer";
    public static final String PATHSELECT_BROADCAST = "com.sprd.soundrecorder.pathselet";
    public static final String ON_TIMER_STOP_ACTION = "com.android.soundrecorder.ontimerstop";
    public static final int STATE_IDLE = 0;
    public static final int START_REOCRD = 1;
    public static final int START_PLAY = 2;
    public static final int SUSPENDED_REOCRD = 3;
    public static final int STOP_REOCRD = 4;
    public static final int PAUSE_REOCRD = 5;
    public static final int GET_WAVE = 6;//bug 626962 the music is ANR
    private int mCurrentState = STATE_IDLE;
    private Recorder mRecorder;
    public static final Object mStopSyncLock = new Object();

    public static final int NO_ERROR = 0;
    public static final int SDCARD_ACCESS_ERROR = 1;
    public static final int INTERNAL_ERROR = 2;
    public static final int IN_CALL_RECORD_ERROR = 3;
    public static final int PATH_NOT_EXIST = 4;
    public static final int RECORDING_ERROR = 5;

    //public static boolean mIsNotification = false;
    private SoundRecorderServiceHandler mSoundRecorderServiceHandler;
    private HandlerThread mHandlerThread = null;
    private String mExtension;
    //private String mPath;
    private String mCurrentFilePath = null;
    private int mOutputfileformat;

    private RemoteViews views = null;
    private Notification notice = new Notification();
    private static final int RECORD_STATUS = 1;
    private static final int TIMER_STATUS = 2;
    private static final int MISS_TIMER_RECORD = 3;
    private boolean IsChangeState = false;
    private boolean IsRecord = false;
    public boolean mIsStopFromStatus = false;
    private boolean isStopFromOnDestroy = false;
    public static boolean mIsUui_Support = true;
    public boolean mIsStopRecord = false;//bug 739882 the soundrecord pop crash
    public boolean mIsDelFile = false;
    public boolean misStatusbarExist = false;
    String timeStr = "";
    String mTimerFormat = "";
    String mTimeStr = "";

    FileListener fileListener = null;

    public boolean mHasBreak = false;

    private RecorderListener mRecorderListener = null;
    private SetResultListener mSetResultListener = null;
    private OnUpdateTimeViewListener mOnUpdateTimeViewListener = null;

    private SoundRecorderBinder mBinder = new SoundRecorderBinder();
    private static RemainingTimeCalculator mRemainingTimeCalculator;
    private final Handler mHandler = new Handler();
    private final Handler mHandler_status = new Handler();
    public static final int ADD_DATA_TO_WAVEVIEW = 7;

    private static String mSelectPath = "";
    private long mMaxFileSize = -1;
    private int mCurrentBitRate;
    public static String mRequestedType;
    private int mBitRate = -1;
    private boolean mFromMms = false;
    /**
     * SPRD:Bug 619108 Recorder power consumption optimization( @{
     */
    public boolean mIsUpdateTimer = true;
    public boolean mIsUpdateStatusBarTime = true;
    /**
     * @}
     */
    public static ArrayList<Activity> activityList = new ArrayList<Activity>();
    public List<Float> mWaveDataList;
    private WaveDataManager mWaveDate = null;//bug 626962 the music is ANR
    public SparseArray<Integer> mTagHashMap;//bug 626962 the music is ANR
    public int mTagNumber;
    private int BASE = 200;
    public int mLastTag = 0;
    public int mRecordDuration;

    private NotificationCompat.Builder mNotificationBuilder;
    private NotificationManager mNotificationManager;
    private static final String CHANNEL_ID = TAG;

    private final Runnable mUpdateTimer = new Runnable() {
        public void run() {
            if (null != mOnUpdateTimeViewListener) {
                try {
                    mOnUpdateTimeViewListener.updateTimerView(false);
                } catch (IllegalStateException e) {
                    Log.i(TAG, "run()-IllegalStateException");
                    return;
                }
            }
            /** SPRD:Bug 619108 Recorder power consumption optimization( @{ */
            if (mIsUpdateTimer) {
                mHandler.postDelayed(mUpdateTimer, 300);
            }
        }
    };
    private final Runnable mUpdateTime = new Runnable() {
        public void run() {
            //SPRD:Bug 627402 com.android.soundrecorder happens JavaCrash,log:java.lang.NullPointerException begin
            if (mCurrentState != Recorder.STOP_STATE && mCurrentState != Recorder.IDLE_STATE) {
                //startNotification();
                updateNotification();
                Log.d(TAG, "mUpdateTime startNotification");//bug 621315 com.android.systemui happens ANR
                //updateTimerRecord();
            }
            //SPRD:Bug 627402 end
            if (mIsUpdateStatusBarTime) {
                //mHandler_status.postDelayed(mUpdateTime, 600);
            }
        }
    };
    /** @} */
    /**
     * SPRD:Bug 619108 Recorder power consumption optimization ( @{
     */
    public void RecorderRemoveUpdateTimer() {
        mIsUpdateTimer = false;
        mHandler.removeCallbacks(mUpdateTimer);
    }

    public void RecorderPostUpdateTimer() {
        mIsUpdateTimer = true;
        mHandler.post(mUpdateTimer);
    }

    /**
     * @}
     */
    public class SoundRecorderBinder extends Binder {
        RecordService getService() {
            return RecordService.this;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e(TAG, "onStartCommand");
        if (intent == null || Utils.isIntentFromSoundRecordWare(intent)) {//Bug 659198 it has the commont reject server leak
            return START_NOT_STICKY;
        }
        initRemainingTimeCalculator(null);
        createNotificationChannel();//bug 694965 failed to post notification on channel
        /* SPRD: add new feature @{ */
        boolean prepareRecord = intent.getBooleanExtra(AlarmReceiver.PREPARE_RECORD, false);
        long recordTime = intent.getLongExtra(RecordSetting.TIMER_RECORD_TIME, 0);//bug 766440 
        mRecordDuration = intent.getIntExtra(RecordSetting.TIMER_RECORD_DURATION, mRecordDuration);
        Log.d(TAG, "onStartCommand prepareRecord is " + prepareRecord + ", mRecordDuration = " + mRecordDuration+" recordTime="+recordTime);
        //SPRD:Bug 626651 Timing recording settings will not automatically shut down after long begin
        if (mRecordDuration != 0 && prepareRecord == true) {//bug 682759 the timersound is not stop
            onTimerStopRecorder();
        }
        //SPRD:Bug 626651 end
        if (prepareRecord) {
            RecordSetting.cancelTimerRecord(this,true);
            mSelectPath = RecordSetting.getStorePath(getApplicationContext());
            if (!StorageInfos.isPathExistAndCanWrite(mSelectPath)) {
                mSelectPath = RecordSetting.changeStorePath(getApplicationContext());
                mSelectPath = RecordSetting.createStorePath(mSelectPath);
            }
            if (mCurrentState == Recorder.IDLE_STATE) {
                /* SPRD: bug597340 The timer recording function works abnormally while recording video. @{ */
                AudioManager audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
                if (StandardFrameworks.getInstances().isAudioSourceActive(MediaRecorder.AudioSource.MIC) ||
                        StandardFrameworks.getInstances().isAudioSourceActive(MediaRecorder.AudioSource.CAMCORDER) ||
                        StandardFrameworks.getInstances().isAudioSourceActive(MediaRecorder.AudioSource.VOICE_RECOGNITION)) {
                    Toast.makeText(getApplicationContext(), R.string.reset_timer_recording,
                            Toast.LENGTH_LONG).show();
                } else if (isPhoneCalling() || audioManager.getMode() == AudioManager.MODE_IN_COMMUNICATION) {
                    Toast.makeText(getApplicationContext(), R.string.phone_message,
                            Toast.LENGTH_LONG).show();
                    startMissTimerNotification(recordTime);//bug 766440 
                } else if (!StorageInfos.haveEnoughStorage(mSelectPath)) {
                    Toast.makeText(getApplicationContext(), R.string.storage_not_enough,
                            Toast.LENGTH_LONG).show();
                } else {
                    startRecording(getResuqestType());
                }
                /* @} */
            }
        }

        boolean missRecord = intent.getBooleanExtra(AlarmReceiver.MISS_RECORD, false);
        Log.d(TAG, "onStartCommand missRecord is " + missRecord);
        if (missRecord) {//bug 766440 
            RecordSetting.cancelTimerRecord(this,true);
            startMissTimerNotification(recordTime);
        }
        /* @} */
        //bug 645211 After the restart time recording time has not yet to the phone status bar regular recording marks disappear
        boolean timerRecord = intent.getBooleanExtra(AlarmReceiver.TIME_RECORD, false);
        if (timerRecord) {
            String time = getTimerTime();
            startTimerNotification(time);
            Log.d(TAG, "onStartCommand timerRecord is " + timerRecord);
        }
        //bug 645211 end
        return START_NOT_STICKY;
    }
    /*private void startMissTimerWhenCall(long recordtime){
        startMissTimerNotification(recordtime);
    }*/
    /* SPRD: bug600292 The timer recording shouldn't start while the phone is calling. @{ */
    private boolean isPhoneCalling() {
        TelephonyManager pm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        @SuppressWarnings("WrongConstant")
        TelephonyManager pm1 = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE
                + "1");
        if ((pm != null && (pm.getCallState() == TelephonyManager.CALL_STATE_OFFHOOK ||
                pm.getCallState() == TelephonyManager.CALL_STATE_RINGING))
                || (pm1 != null && (pm1.getCallState() == TelephonyManager.CALL_STATE_OFFHOOK ||
                pm1.getCallState() == TelephonyManager.CALL_STATE_RINGING))) {
            return true;
        }
        return false;
    }
    /* @} */

    public interface RecorderListener {
        public void onStateChanged(int stateCode);

        public void onError(int error);

        void addMediaError();
    }

    public void setStateChangedListener(RecorderListener listener) {
        mRecorderListener = listener;
    }

    public interface SetResultListener {
        void setResultRequest(Uri uri);
    }

    public void setResultListener(SetResultListener listener) {
        mSetResultListener = listener;
    }

    public void setUpdateTimeViewListener(OnUpdateTimeViewListener listener) {
        mOnUpdateTimeViewListener = listener;
    }

    private void setState(int stateCode) {
        mCurrentState = stateCode;
        if (mRecorderListener != null) {
            mRecorderListener.onStateChanged(stateCode);
        } else {
            Log.e(TAG, "<setState> mCurrentState = "
                    + mCurrentState);
            /*SPRD:add for bug595637 Time recording can not be saved @{*/
            if (stateCode == Recorder.STOP_STATE) {
                saveSample(true);
                mRecorder.resetSample();
            }
            /*@} */

        }
    }

    public void updateTimerView() {
        int state = mCurrentState;
        boolean ongoing = state == Recorder.RECORDING_STATE || state == Recorder.PLAYING_STATE
                || state == Recorder.SUSPENDED_STATE;
        int lenSec = mRecorder.sampleLengthSec();
        long time = ongoing ? (mRecorder.progress()) : lenSec;

        if (mRecorder.progress() > lenSec && state == Recorder.PLAYING_STATE) {
            time = lenSec;
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
        timeStr = String.format(mTimerFormat, hour, minute, second);

    }

    private void createNotificationChannel() {//bug 694965 failed to post notification on channel
        Log.d(TAG, "createNotificationChannel");
        if (mNotificationManager == null) {
            mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                final String nameChannel = getString(R.string.app_name);
                final NotificationChannel channel;
                channel = new NotificationChannel(CHANNEL_ID, nameChannel, NotificationManager.IMPORTANCE_LOW);
                channel.setShowBadge(true);
                channel.enableLights(false);
                channel.enableVibration(false);
                mNotificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void buildNotificationBuilder(CharSequence title,
                                          CharSequence text) {
        mNotificationBuilder = new NotificationCompat.Builder(this);
        mNotificationBuilder.setSmallIcon(R.drawable.stat_sys_soundrecorder)
                .setContentTitle(title)
                .setContentText(text)
                //.setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setLocalOnly(true);
        if (Build.VERSION.SDK_INT >= 26) {
            Log.d(TAG, "set channel");
            mNotificationBuilder.setChannelId(CHANNEL_ID);//bug 773628
        }
    }

    protected void startNotification() {

        if (mCurrentState == Recorder.SUSPENDED_STATE) {
            buildNotificationBuilder(getString(R.string.app_name), getString(R.string.recording_pause));
        } else {
            buildNotificationBuilder(getString(R.string.app_name), getString(R.string.recording_now));
        }
        Intent intent = new Intent();
        intent.setAction(PAUSE_ACTION);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
        if (mCurrentState == Recorder.SUSPENDED_STATE) {
            mNotificationBuilder.addAction(
                    new android.support.v4.app.NotificationCompat.Action(
                            R.drawable.ic_statusbar_soundrecorder_pause,
                            getString(R.string.recording),
                            pendingIntent));
        } else {
            mNotificationBuilder.addAction(
                    new android.support.v4.app.NotificationCompat.Action(
                            R.drawable.ic_statusbar_soundrecorder_pause,
                            getString(R.string.record_pause),
                            pendingIntent));
        }

        intent.setAction(STOP_ACTION);
        pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
        mNotificationBuilder.addAction(
                new android.support.v4.app.NotificationCompat.Action(
                        R.drawable.stat_sys_soundrecorder,
                        getString(R.string.recording_stopped),
                        pendingIntent));
        if (!mFromMms) {//bug 740399 the status bar entry the soundrecord
            pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, SoundRecorder.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP), 0);
        } else {
            pendingIntent = PendingIntent.getActivity(this, 0, new Intent(), 0);
        }
        mNotificationBuilder.setContentIntent(pendingIntent);
        notice = mNotificationBuilder.getNotification();
        notice.flags |= Notification.FLAG_ONGOING_EVENT;

        try {
            startForeground(RECORD_STATUS, mNotificationBuilder.build());
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "catch the IllegalArgumentException " + e);
        }
    }


    private void updateNotification() {
        Log.d(TAG, "updateNotification");
        //new Exception().printStackTrace();
        if (views != null && mNotificationManager != null && mNotificationBuilder != null) {
            updateTimerView();
            views.setTextViewText(R.id.appname, timeStr);
            if (!isRecording()) {
                views.setImageViewResource(R.id.status_record, R.drawable.ic_statusbar_soundrecorder_on);
                mNotificationBuilder.setSmallIcon(R.drawable.stat_sys_soundrecorder_stop);
                views.setTextViewText(R.id.appname, getString(R.string.recording_pause));
            } else {
                views.setImageViewResource(R.id.status_record, R.drawable.ic_statusbar_soundrecorder_pause);
                mNotificationBuilder.setSmallIcon(R.drawable.stat_sys_soundrecorder_on);
                views.setTextViewText(R.id.appname, getString(R.string.recording_now));
            }
            mNotificationManager.notify(RECORD_STATUS, mNotificationBuilder.build());
        } else if (mCurrentState != Recorder.STOP_STATE) {//bug 702173 cleartask soundrecord killed
            startNotification();
        }
    }

    public interface OnUpdateTimeViewListener {
        public void updateTimerView(boolean initial);
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate()");
        super.onCreate();
        clearRecordStateAndSetting();
        mHandlerThread = new HandlerThread(HANDLER_THREAD_NAME);
        mHandlerThread.start();
        mSoundRecorderServiceHandler = new SoundRecorderServiceHandler(
                mHandlerThread.getLooper());

        mRecorder = new Recorder(this);
        mRecorder.initialize();
        mRecorder.setOnStateChangedListener(this);
        RecordSetting.setOnTimerStateChangedListener(this);

        mTimerFormat = getResources().getString(R.string.timer_format);

        IntentFilter commandFilter = new IntentFilter();
        commandFilter.addAction(PAUSE_ACTION);
        commandFilter.addAction(SUSPENDED_ACTION);
        commandFilter.addAction(STOP_ACTION);
        commandFilter.addAction(Intent.ACTION_SHUTDOWN);
        commandFilter.addAction("com.android.deskclock.ALARM_ALERT");
        commandFilter.addAction("android.intent.action.PHONE_STATE");
        commandFilter.addAction(Intent.ACTION_NEW_OUTGOING_CALL);
        commandFilter.addAction(PATHSELECT_BROADCAST);
        /** SPRD:Bug 619108 Recorder power consumption optimization( @{ */
        commandFilter.addAction(Intent.ACTION_SCREEN_OFF);
        commandFilter.addAction(Intent.ACTION_SCREEN_ON);
        commandFilter.addAction(Intent.ACTION_LOCALE_CHANGED);
        commandFilter.addAction(CLOSERECORD_MISSTIMER_ACTION);
        /** @} */
        commandFilter.addAction(ON_TIMER_STOP_ACTION);//SPRD:Bug 626651 Timing recording settings will not automatically shut down after long
        registerReceiver(mIntentReceiver, commandFilter);
        mSelectPath = RecordSetting.getStorePath(getApplicationContext());
        fileListener = new FileListener(mSelectPath);
        Log.d(TAG, "onCreate mSelectPath = " + mSelectPath);

        mWaveDataList = new ArrayList<Float>();
        mTagHashMap = new SparseArray<>();//bug 626962 the music is ANR
    }

    public int getSampleLengthSec() {
        return mRecorder.sampleLengthSec();
    }

    @Override
    public void onStart(Intent intent, int startId) {
        Log.d(TAG, "onStart()");
        super.onStart(intent, startId);
    }

    protected void stopNotification() {
        //if(mIsNotification){
        NotificationManager mNotice = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotice.cancel(RECORD_STATUS);
        stopForeground(true);
        //mIsNotification = false;
        //}
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");
        if (mRecorder != null && (mCurrentState == Recorder.RECORDING_STATE || mCurrentState == Recorder.SUSPENDED_STATE)) {
            mRecorder.stopRecording();
            isStopFromOnDestroy = true;
        }
        if (null != mSoundRecorderServiceHandler) {
            mSoundRecorderServiceHandler.getLooper().quit();
        }
        mIsUpdateStatusBarTime = false;
        mHandler_status.removeCallbacks(mUpdateTime);
        clearRecordStateAndSetting();
        unregisterReceiver(mIntentReceiver);
        if (mRecorder != null) {
            mRecorder.uninitialize();
        }
        misStatusbarExist = false;
        AbortApplication.getInstance().exitAll();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind()");
        return mBinder;
    }

    public void startRecording(final String requestType) {
        Log.e(TAG, "startRecording() requestType= " + requestType + ", mSelectPath = " + mSelectPath);
         if (getApplicationContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED){//bug 730258
            Log.e(TAG, "startRecording() Permission error ");
            return;
        }
        mRemainingTimeCalculator.reset();
        mRequestedType = requestType;
        String filename = getApplicationContext().getResources().getString(R.string.new_record);
        if (SoundRecorder.AUDIO_AMR.equals(mRequestedType)) {
            mBitRate = SoundRecorder.BIT_PER_SEC_FOR_AMR;
            mRemainingTimeCalculator.setBitRate(SoundRecorder.BIT_PER_SEC_FOR_AMR); // fix  bug 130427, 2013/2/27
            mCurrentFilePath = mRecorder.startRecording(SoundRecorder.AMR,
                    ".amr", mSelectPath, filename);
        } else if (SoundRecorder.AUDIO_3GPP.equals(mRequestedType)) {
            mBitRate = SoundRecorder.BITRATE_3GPP;
            mRemainingTimeCalculator.setBitRate(SoundRecorder.BITRATE_3GPP);
            mCurrentFilePath = mRecorder.startRecording(SoundRecorder.THREE_3GPP, ".3gpp", mSelectPath, filename);
        } else if (SoundRecorder.AUDIO_MP3.equals(mRequestedType)) {//SPRD:Bug 626265 add mp3 type in soundrecorder
            mBitRate = SoundRecorder.BITRATE_MP3;
            mRemainingTimeCalculator.setBitRate(SoundRecorder.BITRATE_MP3);
            mCurrentFilePath = mRecorder.startRecording(SoundRecorder.MP3, ".mp3", mSelectPath, filename);
        } else {
            throw new IllegalArgumentException(
                    "Invalid output file type requested");
        }
        fileListener.startWatching();
        //startNotification();
        /** SPRD:Bug 619108 Recorder power consumption optimization( @{ */
        mIsUpdateStatusBarTime = true;
        /** @} */
        mHandler_status.post(mUpdateTime);
        if (mMaxFileSize != -1 && mMaxFileSize != 0) {
            mRemainingTimeCalculator.setFileSizeLimit(
                    mRecorder.sampleFile(), mMaxFileSize);
        }
    }

    public void setRecordingType(String type) {
        mRequestedType = type;
    }

    public Recorder getRecorder() {
        return mRecorder;
    }

    public void pauseRecord() {
        sendThreadHandlerMessage(PAUSE_REOCRD);
        /** SPRD:Bug 619108 Recorder power consumption optimization( @{ */
        mIsUpdateStatusBarTime = false;
        /** @} */
        mHandler_status.removeCallbacks(mUpdateTime);
    }

    public void resumeRecord() {
        sendThreadHandlerMessage(Recorder.SUSPENDED_STATE);
        /** SPRD:Bug 619108 Recorder power consumption optimization( @{ */
        mIsUpdateStatusBarTime = true;
        /** @} */
        //mHandler_status.post(mUpdateTime);
    }

    public void stopRecord() {
        sendThreadHandlerMessage(Recorder.STOP_STATE);
        /** SPRD:Bug 619108 Recorder power consumption optimization( @{ */
        mIsUpdateStatusBarTime = false;
        mIsUpdateTimer = false;
        /** @} */
        mHandler.removeCallbacks(mUpdateTimer);
        mHandler_status.removeCallbacks(mUpdateTime);
    }

    private void sendThreadHandlerMessage(int what) {
        mSoundRecorderServiceHandler.removeCallbacks(mHandlerThread);
        if (isStopFromOnDestroy) {
            return;
        } else {
            mSoundRecorderServiceHandler.sendEmptyMessage(what);
        }
    }

    public class SoundRecorderServiceHandler extends Handler {
        public SoundRecorderServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            Log.e(TAG, "SoundRecorderServiceHandler msg=" + msg);
            switch (msg.what) {
                case Recorder.RECORDING_STATE:
                   /*mRecorder.startRecording(mOutputfileformat, mExtension,
                            mPath);*/
                    IsChangeState = false;
                    break;
                case PAUSE_REOCRD:
                    mRecorder.pauseRecording();
                    IsChangeState = true;
                    break;
                case Recorder.SUSPENDED_STATE:
                    mRecorder.resumeRecording();
                    IsChangeState = false;
                    break;
                case Recorder.STOP_STATE:
                    mRecorder.stopRecording();
                    IsChangeState = true;
                    mRecordDuration = 0;
                    break;
                case Recorder.IDLE_STATE:
                    stopNotification();
                    break;
                case START_PLAY:
                    // startPlayback();
                    break;
                case GET_WAVE: //bug 626962 the music is ANR
                    getWaveData();
                    break;
                default:
                    break;
            }
            // SPRD: bug602472 SoundRecord shouldn't display notification when record stoped.
            /*if (msg.what != Recorder.STOP_STATE && msg.what != Recorder.IDLE_STATE && msg.what != GET_WAVE) {
                //startNotification();
                updateNotification();
            }*/
        }
    }

    public boolean isRecording() {
        return IsRecord;
    }

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            TelephonyManager pm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            @SuppressWarnings("WrongConstant")
            TelephonyManager pm1 = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE
                    + "1");
            //AudioManager audioManager = (AudioManager) RecordService.this.getSystemService(Context.AUDIO_SERVICE);
            if (PAUSE_ACTION.equals(action)) {
                if (mCurrentState == Recorder.IDLE_STATE) {
                    if (SoundRecorder.mShowDialog) {
                        return;
                    }
                    if (StandardFrameworks.getInstances().isAudioSourceActive(MediaRecorder.AudioSource.MIC) ||
                            StandardFrameworks.getInstances().isAudioSourceActive(MediaRecorder.AudioSource.CAMCORDER) ||
                            StandardFrameworks.getInstances().isAudioSourceActive(MediaRecorder.AudioSource.VOICE_RECOGNITION)) {
                        Toast.makeText(getApplicationContext(), R.string.same_application_running,
                                Toast.LENGTH_SHORT).show();
                        return;
                    } else if ((pm != null && pm.getCallState() == TelephonyManager.CALL_STATE_OFFHOOK)
                            || (pm1 != null && pm1.getCallState() == TelephonyManager.CALL_STATE_OFFHOOK)) {
                        Toast.makeText(getApplicationContext(), R.string.phone_message,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    mRequestedType = getResuqestType();
                    if (mFromMms && mRequestedType.equals("audio/3gpp")) {
                        mRequestedType = "audio/amr";
                    }
                    mSelectPath = RecordSetting.getStorePath(getApplicationContext());
                    Log.i(TAG, "mRequestedType is:" + mRequestedType);
                    if (!StorageInfos.isPathExistAndCanWrite(mSelectPath)) {
                        return;
                    }
                    startRecording(mRequestedType);
                    IsChangeState = false;
                    IsRecord = false;
                } else if (mCurrentState == Recorder.RECORDING_STATE) {
                    pauseRecord();
                } else if (mCurrentState == Recorder.SUSPENDED_STATE) {
                    resumeRecord();
                }
            } else if (STOP_ACTION.equals(action)) {
                if (SoundRecorder.mShowDialog || mCurrentState == Recorder.IDLE_STATE) {
                    return;
                }
                if (mCurrentState == Recorder.RECORDING_STATE || mCurrentState == Recorder.SUSPENDED_STATE) {
                    mIsStopFromStatus = true;
                }
                mIsUui_Support = false;
                stopRecord();
            } else if (action.equals("com.android.deskclock.ALARM_ALERT") || action.equals(Intent.ACTION_SHUTDOWN)) {
                if ((mCurrentState == Recorder.RECORDING_STATE || mCurrentState == Recorder.SUSPENDED_STATE)) {
                    mRecorder.stop();
                    mHasBreak = true;
                }
            } else if (action.equals("android.intent.action.PHONE_STATE")) {
                TelephonyManager tManager = (TelephonyManager) context.getSystemService(Service.TELEPHONY_SERVICE);
                if (tManager.getCallState() == TelephonyManager.CALL_STATE_RINGING
                        && (mCurrentState == Recorder.RECORDING_STATE || mCurrentState == Recorder.SUSPENDED_STATE)) {
                    mRecorder.stop();
                    mHasBreak = true;
                }
            } else if (action.equals(Intent.ACTION_NEW_OUTGOING_CALL)) {
                if ((mCurrentState == Recorder.RECORDING_STATE || mCurrentState == Recorder.SUSPENDED_STATE)) {
                    mRecorder.stop();
                    mHasBreak = true;
                } else if (mCurrentState == Recorder.IDLE_STATE) {
                    try {
                        if (mRecorder != null && (mRecorder.sampleFile() != null || mRecorder.sampleFile().exists())) {
                            fileListener.stopWatching();
                            saveSample(true);
                            SoundRecorder.mShowDialog = false;
                            mRecorder.delete();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } else if (PATHSELECT_BROADCAST.equals(action)) {
                //selectPath was changed
                if (mCurrentState != Recorder.RECORDING_STATE && mCurrentState != Recorder.SUSPENDED_STATE) {
                    Log.i(TAG, "heheheh");
                    mSelectPath = intent.getStringExtra("newPath");
                    fileListener = new FileListener(mSelectPath);
                    setStoragePath(mSelectPath);
                    Log.e(TAG, "PATHSELECT_BROADCAST mSelectPath=" + mSelectPath);
                }//bug 671654 the insert and pull out T card the soundrecord exception
                /** SPRD:Bug 619108 Recorder power consumption optimization( @{ */
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                if (isRecording()) {
                    mIsUpdateStatusBarTime = false;
                    mHandler_status.removeCallbacks(mUpdateTime);
                }
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                if (isRecording()) {
                    mIsUpdateStatusBarTime = true;
                    //mHandler_status.post(mUpdateTime);
                }
            } else if (ON_TIMER_STOP_ACTION.equals(action)) {
                updateTimerRecord();
            } else if (Intent.ACTION_LOCALE_CHANGED.equals(action)) {
                if (mCurrentState == Recorder.RECORDING_STATE || mCurrentState == Recorder.SUSPENDED_STATE) {//bug 752920 when change language the update notify is error
                    updateNotification();
                }
            }else if (CLOSERECORD_MISSTIMER_ACTION.equals(action)){
                cancelTimerNotification(MISS_TIMER_RECORD);
            }
        }
    };

    private void setError(int error) {
        if (mRecorderListener != null)
            mRecorderListener.onError(error);
    }

    public int getCurrentProgressInSecond() {
        int progress = (int) (getCurrentProgressInMillSecond());
        return progress;
    }

    public long getCurrentProgressInMillSecond() {
        if (mCurrentState == Recorder.PLAYING_STATE) {
        } else if (mCurrentState == Recorder.SUSPENDED_STATE || mCurrentState == Recorder.RECORDING_STATE) {
            return mRecorder.progress();
        }
        return 0;
    }

    @Override
    public void onStateChanged(StateEvent event, Bundle extra) {
        switch (event.nowState) {
            case Recorder.RECORDING_STATE:
                setState(Recorder.RECORDING_STATE);
                mIsUpdateTimer = true;
                mHandler.post(mUpdateTimer);
                IsChangeState = false;
                IsRecord = true;
                break;
            case Recorder.SUSPENDED_STATE:
                mRemainingTimeCalculator.reset();
                setState(Recorder.SUSPENDED_STATE);
                IsRecord = false;
                //bug1190089 CID 232138 Return value ignored, but method has no side effect
                /*getSampleFile();
                getSampleLengthMillsec();*/
                break;
            case Recorder.IDLE_STATE:
                setState(Recorder.IDLE_STATE);
                IsRecord = false;
                mIsStopFromStatus = false;
                mIsUui_Support = true;
                mRecorder.mIsAudioFocus_Loss = false;
                rebootTimerRecord();//bug 759120 the timerrecord notify cancel
                break;
            case Recorder.STOP_STATE:
                setState(Recorder.STOP_STATE);
                IsRecord = false;
                IsChangeState = true;
                break;
            default:
                break;
        }
        saveRecordStateAndSetting();
        if (event.nowState == Recorder.IDLE_STATE) {
            stopNotification();
        } else if (mRecorder.mSampleFile != null) {
            //startNotification();
            updateNotification();
        }
        mHandler_Wave.sendEmptyMessage(event.nowState);
    }
    private void rebootTimerRecord(){//bug 759120 the timerrecord notify cancel
        SharedPreferences timerRecordPreferences = this.getSharedPreferences(RecordSetting.SOUNDREOCRD_TYPE_AND_DTA, Context.MODE_PRIVATE);
        long recordTime = timerRecordPreferences.getLong(RecordSetting.TIMER_RECORD_TIME, 0);
        String duration = timerRecordPreferences.getString(RecordSetting.TIMER_RECORD_DURATION, null);
        if (duration ==null||recordTime==0){
            return;
        }
        int recordDuration = (int) (Float.parseFloat(duration) * 60);
        Log.d(TAG,"sprdrebootTimerRecord= recordTime="+recordTime+" System.currentTimeMillis()="+System.currentTimeMillis());
        if (recordTime > System.currentTimeMillis()){
            RecordSetting.resetTimerRecord(this, recordTime, recordDuration);
            SharedPreferences.Editor edit = timerRecordPreferences.edit();
            edit.putBoolean(RecordSetting.TIMER_RECORD_STATUS, true);
            edit.commit();
            Intent timeRecord = new Intent(this, RecordService.class);
            timeRecord.putExtra(AlarmReceiver.TIME_RECORD, true);
            if (Build.VERSION.SDK_INT >= 26) {
                Log.d(TAG,"startForegroundService");
                this.startForegroundService(timeRecord);
            }else {
                this.startService(timeRecord);
            }
        }
    }
    @Override
    public void onError(int error) {
        switch (error) {
            case Recorder.SDCARD_ACCESS_ERROR:
                setError(SDCARD_ACCESS_ERROR);
                break;
            case Recorder.IN_CALL_RECORD_ERROR:
            case Recorder.INTERNAL_ERROR:
                setError(INTERNAL_ERROR);
                break;
            case Recorder.PATH_NOT_EXIST:
                setError(PATH_NOT_EXIST);
                break;
            case Recorder.RECORDING_ERROR:
                setError(RECORDING_ERROR);
                break;
            default:
                break;
        }

    }

    public File getSampleFile() {
        return mRecorder.sampleFile();
    }

    public int getSampleLengthMillsec() {
        return mRecorder.sampleLengthMillsec();
    }

    public class FileListener extends FileObserver {

        public FileListener(String path) {
            super(path, FileObserver.MOVED_FROM | FileObserver.DELETE);
        }

        public void onEvent(int event, String path) {
            Log.e(TAG, "onEvent");
            String currentRecordFilePath = "";
            currentRecordFilePath = mCurrentFilePath.substring(mCurrentFilePath.lastIndexOf("/") + 1);
            switch (event) {
                case FileObserver.MOVED_FROM:
                case FileObserver.DELETE:
                    if (mCurrentState == Recorder.RECORDING_STATE || mCurrentState == Recorder.SUSPENDED_STATE ||
                            (mCurrentState == Recorder.IDLE_STATE && SoundRecorder.mShowDialog && !mFromMms)) {
                        if (path.equals(currentRecordFilePath)) {
                            Log.e(TAG, "delete file");
                            if (mCurrentState != Recorder.IDLE_STATE) {
                                mRecorder.stop();
                            } else {
                                SoundRecorder.mShowDialog = false;
                            }
                            mRecorder.delete();
                            mRecorder.resetSample();
                            fileListener.stopWatching();
                        }
                    }
                    mIsDelFile = true;
                    break;
                default:
                    break;
            }
            mIsDelFile = false;
        }
    }

    public int getCurrentState() {
        return mCurrentState;
    }

    public boolean reset() {
        Log.e(TAG, "reset()");
        mRecorder.reset();
        stopNotification();
        setState(STATE_IDLE);
        return true;
    }

    public File hasFileWaitToSave() {
        if (mCurrentFilePath != null) {
            File file = new File(mCurrentFilePath);
            if (file != null && file.exists()) {
                return file;
            }
        }
        return null;
    }

    /**
     * Try to get the store path from the sharedPreferces. If the path has in the sdcard, should
     * judge that the sdcard state is remount, all right?
     *
     * @return store path
     */
    public String getStorePath() {
        String path = "";
        SharedPreferences fileSavePathShare = this.getSharedPreferences(PathSelect.DATA_BASE, MODE_PRIVATE);
        path = fileSavePathShare.getString(PathSelect.SAVE_PATH, "");
        if ("".equals(path)) {
            File pathDir = null;
            if (StorageInfos.isExternalStorageMounted()) {
                pathDir = StorageInfos.getExternalStorageDirectory();
            } else {
                pathDir = StorageInfos.getInternalStorageDirectory();
            }
            if (pathDir != null) {
                File pathFile = new File(pathDir.getPath() + Recorder.DEFAULT_STORE_SUBDIR);
                if (pathFile.isDirectory()) {
                    return pathFile.toString();
                } else if (SoundRecorder.mCanCreateDir && (!pathFile.exists() && pathFile.mkdirs())) {
                    path = pathFile.toString();
                    mSelectPath = path;
                    initRemainingTimeCalculator(null);
                }
            }
        }
        return path;
    }

    private String getTimerTime() {
        SharedPreferences recordSavePreferences = this.getSharedPreferences(
                RecordingFileListTabUtils.SOUNDREOCRD_TYPE_AND_DTA, MODE_PRIVATE);
        String Hour = recordSavePreferences.getString(RecordSetting.TIMER_RECORD_HOUR, "0");
        String Minute = recordSavePreferences.getString(RecordSetting.TIMER_RECORD_MINUTE, "0");
        String Duration = recordSavePreferences.getString(RecordSetting.TIMER_RECORD_DURATION, "0");
        String[] durationHours = getResources().getStringArray(R.array.duration_hours);
        int duration = (int) (Float.parseFloat(Duration) * 60);
        int index = duration > 60 ? 1 : 0;
        String StartTime = String.format(
                getResources().getString(
                        R.string.timer_recording_detail), Hour,
                Minute, Duration + durationHours[index]);
        return StartTime;
    }

    //bug 645211 end
    private String getResuqestType() {
        String requestedType = null;
        SharedPreferences recordSavePreferences = this.getSharedPreferences(RecordingFileListTabUtils.SOUNDREOCRD_TYPE_AND_DTA, MODE_PRIVATE);
        requestedType = recordSavePreferences.getString(RecordingFileListTabUtils.SAVE_RECORD_TYPE, SoundRecorder.AUDIO_3GPP);//bug 739579 the soundrecord path is 3gpp
        return requestedType;
    }

    public void setMaxFileSize(long size) {
        mMaxFileSize = size;
    }

    public int getCurrentLowerLimit() {
        return mRemainingTimeCalculator.currentLowerLimit();
    }

    public long getTimeRemaining() {
        return mRemainingTimeCalculator.timeRemaining();
    }

    public void setStoragePath(String path) {
        mRemainingTimeCalculator.setStoragePath(path);
    }

    public void initRemainingTimeCalculator(String path) {
        if (path != null) {
            mSelectPath = path;
        }
        mRemainingTimeCalculator = new RemainingTimeCalculator(mSelectPath);
        mRemainingTimeCalculator.setStoragePath(mSelectPath);
        if (mBitRate != -1) {
            mRemainingTimeCalculator.setBitRate(mBitRate);
        }
        if (mMaxFileSize != -1 && mMaxFileSize != 0) {
            mRemainingTimeCalculator.setFileSizeLimit(
                    mRecorder.sampleFile(), mMaxFileSize);
        }
    }

    public void saveRecordStateAndSetting() {
        SharedPreferences recordSavePreferences = this.getSharedPreferences(SOUNDREOCRD_STATE_AND_DTA, MODE_PRIVATE);
        SharedPreferences.Editor edit = recordSavePreferences.edit();
        edit.putInt(RecordService.SAVE_RECORD_STATE, mCurrentState);
        edit.putString(RecordingFileListTabUtils.SOUNDREOCRD_TYPE_AND_DTA, mRequestedType);
        edit.commit();
    }

    public void clearRecordStateAndSetting() {
        Log.e(TAG, "clearRecordStateAndSetting");
        SharedPreferences recordSavePreferences = this.getSharedPreferences(SOUNDREOCRD_STATE_AND_DTA, MODE_PRIVATE);
        SharedPreferences.Editor edit = recordSavePreferences.edit();
        edit.putInt(RecordService.SAVE_RECORD_STATE, Recorder.IDLE_STATE);
        edit.commit();
    }

    public static String getSavePath() {
        return mSelectPath;
    }

    public static void setSavePath(String path) {
        mSelectPath = path;
    }

    public void saveSample(boolean save) {

        if (mRecorder.sampleLengthMillsec() <= 1000)
            return;
        Uri uri = null;
        File tempdatefile = null;
        File targetFile = null;
        try {
            SharedPreferences recordSavePreferences = this.getSharedPreferences(RecordSetting.SOUNDREOCRD_TYPE_AND_DTA, MODE_PRIVATE);
            boolean ischeck = recordSavePreferences.getBoolean(RecordSetting.FRAG_TAG_SAVE_FILE_TYPE, true);
            File sampleFile = mRecorder.sampleFile();
            String finalName = null;
            if (ischeck || !mIsStopRecord) {//bug 739882 the soundrecord pop crash
                finalName = sampleFile.getPath().substring(0, sampleFile.getPath().lastIndexOf(".tmp"));
                targetFile = new File(finalName);
            } else {
                String finalTempName = sampleFile.getPath().substring(0, sampleFile.getPath().lastIndexOf(".tmp"));
                tempdatefile = new File(finalTempName);
                finalName = mRecorder.mSampleDir.getPath();
                targetFile = new File(finalName);
                if (fileListener != null) {
                    fileListener.stopWatching();
                }
            }
            //bug1190089 CID118552 Exceptional return value of java.io.File.renameTo(File) ignored
            if (!sampleFile.renameTo(targetFile)){
                return;
            }
            sampleFile = targetFile;
            uri = this.addToMediaDB(sampleFile);
            if (mWaveDate != null) {
                mWaveDate.flushData();
                Log.d(TAG, " ischeck=" + ischeck + " mIsStopRecord=" + mIsStopRecord);
                if (!ischeck && mIsStopRecord) {
                    String oldName = tempdatefile.getName().substring(0, tempdatefile.getName().lastIndexOf("."));
                    String newName = targetFile.getName().substring(0, targetFile.getName().lastIndexOf("."));
                    mWaveDate.rename(getApplicationContext(), oldName, newName);
                }
                mIsStopRecord = false;
                mWaveDate = null;
                views = null;//bug 699941 the cleartask the soundrecord stop
            }
        } catch (UnsupportedOperationException ex) {
            return;
        } catch (SQLiteDiskIOException ex) {
            return;
        } catch (NullPointerException ex) {
            return;
        } catch (SecurityException ex){
            return;
        }

        if (uri == null) {
            return;
        }
        if (mRecorderListener == null) {
            Toast.makeText(getApplicationContext(), R.string.recording_save, Toast.LENGTH_SHORT)
                    .show();
        } else {
            if (save && mSetResultListener != null&&mFromMms) {
                mSetResultListener.setResultRequest(uri);
                mRecorderListener = null;
            } else {
                if (!mFromMms) {
                    String title = mRecorder.mFileName + " " + this.getString(R.string.recording_save);
                    Toast.makeText(getApplicationContext(), title, Toast.LENGTH_SHORT)
                            .show();
                }
            }
        }
    }

    /*
     * Adds file and returns content uri.
     */
    private Uri addToMediaDB(File file) {
        // SPRD: add
        if (file == null) return null;

        Resources res = getResources();
        ContentValues cv = new ContentValues();
        long current = SystemClock.elapsedRealtime();
        long modDate = file.lastModified();
        String title = null;
        String fileName = file.getName();
        if (fileName.lastIndexOf(".") < 0) {
            title = fileName;
            Log.d(TAG, "addToMediaDBtitle =" + title);
        } else {
            Log.d(TAG, "addToMediaDBfileName =" + fileName);
            title = fileName.substring(0, fileName.lastIndexOf("."));
        }
        long sampleLengthMillis = mRecorder.sampleLengthMillsec();

        cv.put(MediaStore.Audio.Media.IS_MUSIC, "1");
        cv.put(MediaStore.Audio.Media.TITLE, title);
        cv.put(MediaStore.Audio.Media.DATA, file.getAbsolutePath());
        cv.put(MediaStore.Audio.Media.DATE_ADDED, (int) (current / 1000));
        cv.put(MediaStore.Audio.Media.DATE_MODIFIED, (int) (modDate / 1000));
        cv.put(MediaStore.Audio.Media.DURATION, sampleLengthMillis);
        cv.put(MediaStore.Audio.Media.MIME_TYPE, mRequestedType);
        cv.put(MediaStore.Audio.Media.ARTIST,
                res.getString(R.string.audio_db_artist_name));
        cv.put(MediaStore.Audio.Media.ALBUM,
                res.getString(R.string.audio_db_album_name));
        cv.put(StandardFrameworks.getInstances().getMediaStoreAlbumArtist(),
                res.getString(R.string.audio_db_artist_name));
        cv.put(MediaStore.Audio.Media.COMPOSER, SoundRecorder.COMPOSER);
        //SPRD: add tag to MediaDB
        cv.put(MediaStore.Audio.Media.BOOKMARK, mTagNumber);
        Log.d(TAG, "Inserting audio record: " + cv.toString());
        ContentResolver resolver = getContentResolver();
        Uri base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Log.d(TAG, "ContentURI: " + base);

        Uri result = null;
        final String[] ids = new String[]{MediaStore.Audio.Playlists._ID};
        final String where = MediaStore.Audio.AudioColumns.DATA + "=?";
        final String[] args = new String[]{file.getAbsolutePath()};
        Cursor cursor = null;
        try {
            cursor = this.getContentResolver().query(base, ids, where, args, null);
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
        if (cursor != null) {
            int id = -1;
            if (cursor.getCount() >= 1) {
                Log.v(TAG, "database has this record");
                cursor.moveToFirst();
                id = cursor.getInt(0);
                result = ContentUris.withAppendedId(base, id);
                resolver.update(result, cv, where, args);
            } else {
                Log.v(TAG, "insert this record");
                result = resolver.insert(base, cv);
            }
            cursor.close();
        } else {
            Log.v(TAG, "cursor null insert this record");
            result = resolver.insert(base, cv);
        }

        if (result == null) {
            if (mRecorderListener != null) {
                mRecorderListener.addMediaError();
            } else {
                Toast.makeText(getApplicationContext(), R.string.error_mediadb_new_record, Toast.LENGTH_SHORT)
                        .show();
            }
            return null;
        }
        if (getPlaylistId(res) == -1) {
            createPlaylist(res, resolver);
        }
        int audioId = Integer.valueOf(result.getLastPathSegment());
        addToPlaylist(resolver, audioId, getPlaylistId(res));

        // Notify those applications such as Music listening to the
        // scanner events that a recorded audio file just created.
        sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, result));
        return result;
    }

    /*
     * Obtain the id for the default play list from the audio_playlists table.
     */
    private int getPlaylistId(Resources res) {
        Uri uri = MediaStore.Audio.Playlists.getContentUri("external");
        final String[] ids = new String[]{MediaStore.Audio.Playlists._ID};
        final String where = MediaStore.Audio.Playlists.NAME + "=?";
        final String[] args = new String[]{"My recordings"};
        Cursor cursor = this.getContentResolver().query(uri, ids, where, args, null);
        //bug:1190003 Coverity scan: Calling a method on null object cursor
        int id = -1;
        if (cursor != null) {
            cursor.moveToFirst();
            if (!cursor.isAfterLast()) {
                id = cursor.getInt(0);
            }
            cursor.close();
        } else {
            Log.d(TAG, "query returns null");
        }

        return id;
    }

    /*
     * Create a playlist with the given default playlist name, if no such playlist exists.
     */
    private Uri createPlaylist(Resources res, ContentResolver resolver) {
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Audio.Playlists.NAME, "My recordings");
        Uri uri = resolver.insert(MediaStore.Audio.Playlists.getContentUri("external"), cv);
        if (uri == null) {
            Toast.makeText(getApplicationContext(), R.string.error_mediadb_new_record, Toast.LENGTH_SHORT)
                    .show();
        }
        return uri;
    }

    /*
     * Add the given audioId to the playlist with the given playlistId; and maintain the
     * play_order in the playlist.
     */
    private void addToPlaylist(ContentResolver resolver, int audioId, long playlistId) {
        String[] cols = new String[]{
                "count(*)"
        };
        Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId);
        Cursor cur = resolver.query(uri, cols, null, null, null);
        cur.moveToFirst();
        final int base = cur.getInt(0);
        cur.close();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, Integer.valueOf(base + audioId));
        values.put(MediaStore.Audio.Playlists.Members.AUDIO_ID, audioId);
        resolver.insert(uri, values);
    }

    public void setRecroderComeFrom(boolean fromMms) {
        mFromMms = fromMms;
    }

    public boolean getRecroderComeFrom() {
        return mFromMms;
    }

    public final Handler mHandler_Wave = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case Recorder.RECORDING_STATE:
                    if (mWaveDate == null) {//bug 688955
                        mWaveDate = new WaveDataManager(getApplicationContext(), mRecorder.mFileName);
                    }
                    getWaveData();
                    break;
                case Recorder.IDLE_STATE:
                    mHandler_Wave.removeCallbacks(mGetWaveData);
                    mWaveDataList.clear();
                    mTagHashMap.clear();
                    mLastTag = 0;
                    mWaveDate = null;//bug 753995 the soundrecord file is not wave
                    mTagNumber = 0;
                    break;
                case ADD_DATA_TO_WAVEVIEW:
                    if (mCurrentState == Recorder.RECORDING_STATE) {
                        mWaveDataList.add(msg.getData().getFloat("data"));
                    }
                    break;
            }
        }
    };

    public final Runnable mGetWaveData = new Runnable() {
        public void run() {
            getWaveData();
        }
    };

    public void getWaveData() {
        if (mCurrentState == Recorder.RECORDING_STATE) {
            float amplitude = (float) mRecorder.getMaxAmplitude() / BASE;
            Log.d(TAG, "getWaveData amplitude = " + amplitude);
            if (amplitude != 0) {
                float db = (float) (20 * Math.log10(amplitude));
                Log.d(TAG, "getWaveData db = " + db);
                if (db < 2) {
                    db = 2;
                }
                Bundle bundle = new Bundle();
                bundle.putFloat("data", db);
                Message msg = mHandler_Wave.obtainMessage(ADD_DATA_TO_WAVEVIEW);
                msg.setData(bundle);
                mHandler_Wave.sendMessage(msg);
                if (mWaveDate != null && mLastTag == mTagNumber) {
                    mWaveDate.addWaveData(db);
                } else if (mWaveDate != null) {
                    mWaveDate.addWaveData(db);
                    mLastTag = mTagNumber;
                }
            }
            mSoundRecorderServiceHandler.removeMessages(GET_WAVE);
            mSoundRecorderServiceHandler.sendEmptyMessageDelayed(GET_WAVE, (long) 80);
        }
    }

    public void addRecordTag() {
        if (mCurrentState == Recorder.RECORDING_STATE) {
            mTagNumber = mTagNumber + 1;
            mTagHashMap.put(mWaveDataList.size(), mTagNumber);
            if (mWaveDate != null) {
                mWaveDate.addTagData(mTagNumber, mWaveDataList.size());
            }
            Log.d(TAG, "sprdaddtag mTagNumber=end" + mTagNumber);
        }
    }

    private long calTheTime() {
        Calendar c = Calendar.getInstance();
        int nowHour = c.get(Calendar.HOUR_OF_DAY);
        nowHour += mRecordDuration / 60;
        int nowMinute = c.get(Calendar.MINUTE);
        nowMinute += mRecordDuration % 60;
        c.set(Calendar.HOUR_OF_DAY, nowHour);
        c.set(Calendar.MINUTE, nowMinute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);

        return c.getTimeInMillis();
    }

    private void onTimerStopRecorder() {
        Intent intent = new Intent(ON_TIMER_STOP_ACTION);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        long timeInMillis = calTheTime();
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent);//bug 691001 the timerrecord is not stop
    }

    private void updateTimerRecord() {
        if (mRecordDuration != 0) {
            if (mCurrentState == Recorder.RECORDING_STATE || mCurrentState == Recorder.SUSPENDED_STATE) {
                Log.d(TAG, "Timer record time up, stop record!");
                stopRecord();
            }
        }
    }

    public void startTimerNotification(String time) {
        if (mCurrentState == Recorder.RECORDING_STATE || mCurrentState == Recorder.SUSPENDED_STATE) {
            Log.d(TAG, "is recording , not show notificaiton");
            return;
        }
        Notification.Builder builder = new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.stat_sys_soundrecorder_timing);
        builder.setShowWhen(false);
        builder.setAutoCancel(false);
        builder.setOngoing(true);
        builder.setContentTitle(getString(R.string.tuned_timer_recording));
        builder.setContentText(time);
        if (Build.VERSION.SDK_INT >= 26) {
            builder.setChannelId(CHANNEL_ID);
        }

        Intent intent = new Intent();
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.setClassName(getPackageName(), RecordSetting.class.getName());
        PendingIntent pIntent = PendingIntent.getActivity(this, 0, intent, 0);

        builder.setContentIntent(pIntent);
        Intent intentclose = new Intent();
        intentclose.setAction(CLOSERECORD_ACTION);
        intentclose.setClassName("com.android.soundrecorder", "com.sprd.soundrecorder.AlarmReceiver");//bug 740432 the close timersound button is invalid
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intentclose, 0);
        builder.addAction(new Notification.Action(R.drawable.stat_sys_soundrecorder_timing,
                getApplicationContext().getString(R.string.close_timer), pendingIntent));
        startForeground(TIMER_STATUS, builder.build());
    }

    public void cancelTimerNotification(int id) {
        /*NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(id);*/
        stopForeground(true);
    }

    public void onTimerStateChanged(boolean state, String time) {
        if (state) {
            startTimerNotification(time);
        } else {
            cancelTimerNotification(TIMER_STATUS);
        }
    }

    public void startMissTimerNotification(long time) {
        if (mCurrentState == Recorder.RECORDING_STATE || mCurrentState == Recorder.SUSPENDED_STATE) {
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
        String contentText = String.format(getResources().getString(R.string.miss_timer_time), format.format(time));
        builder.setContentText(contentText);

        Intent intent = new Intent();
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.setClassName(getPackageName(), RecordSetting.class.getName());
        PendingIntent pIntent = PendingIntent.getActivity(this, 0, intent, 0);
        builder.setContentIntent(pIntent);
        Intent intentclose = new Intent();
        intentclose.setAction(CLOSERECORD_MISSTIMER_ACTION);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intentclose, 0);
        builder.addAction(new Notification.Action(R.drawable.stat_sys_soundrecorder_timing,
                getApplicationContext().getString(R.string.close_timer), pendingIntent));
        startForeground(MISS_TIMER_RECORD, builder.build());
    }
}

class AbortApplication {
    private static final String IS_FROM_MMS = "isfromMMs";
    private static final String ACTIVITY = "activity";
    private LinkedList<HashMap<String, Object>> mList = new LinkedList<HashMap<String, Object>>();
    private static AbortApplication instance;

    private AbortApplication() {
    }

    public synchronized static AbortApplication getInstance() {
        if (null == instance) {
            instance = new AbortApplication();
        }
        return instance;
    }

    // add Activity
    public void addActivity(boolean isFromMMs, Activity activity) {
        Log.d(RecordService.TAG, "addActivity() activity = " + activity + " fromMms=" + isFromMMs);
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put(IS_FROM_MMS, isFromMMs);
        map.put(ACTIVITY, activity);
        mList.add(map);
    }

    public void exit() {
        try {
            for (int i = 0; i < mList.size() - 1; i++) {
                HashMap<String, Object> map = mList.get(i);
                Activity activity = (Activity) map.get(ACTIVITY);
                if (activity != null) {
                    Log.e(RecordService.TAG, "activity:" + activity + " isDestroyed() " + activity.isDestroyed());
                    if (!activity.isDestroyed()) {
                        Log.d(RecordService.TAG, "exit() finish activity" + activity);
                        activity.finish();
                    }
                    mList.remove(map);
                }
            }
            for (int j = 0; j < RecordService.activityList.size(); j++) {
                Activity soundPickerActivity = RecordService.activityList.get(j);
                if (soundPickerActivity != null) {
                    if (!soundPickerActivity.isDestroyed()) {
                        Log.d(RecordService.TAG, "exit() finish activity" + soundPickerActivity);
                        soundPickerActivity.finish();
                    }
                    RecordService.activityList.remove(j);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void exitAll() {
        try {
            Iterator<HashMap<String, Object>> it = mList.iterator();
            Log.e(RecordService.TAG, "exitAll() mList" + mList.size());
            while (it.hasNext()) {
                HashMap<String, Object> map = it.next();
                Activity activity = (Activity) map.get(ACTIVITY);
                if (activity != null) {
                    Log.d(RecordService.TAG, "exitAll() finish activity" + activity);
                    activity.finish();
                    it.remove();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
