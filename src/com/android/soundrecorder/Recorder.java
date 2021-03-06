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

import android.content.Context;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaRecorder;
import android.media.MediaRecorder.OnInfoListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import com.sprd.soundrecorder.StorageInfos;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Recorder implements OnCompletionListener, OnErrorListener {
    static final String TAG = "Recorder";
    static final String SAMPLE_PREFIX = "recording";
    static final String SAMPLE_PATH_KEY = "sample_path";
    static final String SAMPLE_LENGTH_KEY = "sample_length";

    public static final int IDLE_STATE = 0;
    public static final int RECORDING_STATE = 1;
    public static final int PLAYING_STATE = 2;
    public static final int SUSPENDED_STATE = 3;  // SPRD： add
    public static final int STOP_STATE = 4;  // SPRD： add
    int mState = IDLE_STATE;

    public static final int NO_ERROR = 0;
    public static final int SDCARD_ACCESS_ERROR = 1;
    public static final int INTERNAL_ERROR = 2;
    public static final int IN_CALL_RECORD_ERROR = 3;

    /* SPRD: add @{ */
    public static final int PATH_NOT_EXIST = 4;
    public static final int RECORDING_ERROR = 5;

    public static final String DEFAULT_STORE_SUBDIR = "/recordings";
    public static final String AUDIO_3GPP = "audio/3gpp";
    public static final String AUDIO_AMR = "audio/amr";
    public static final String AUDIO_MP3 = "audio/mp3";
    public static final String AUDIO_MP4 = "audio/mp4";
    public static final String CALL_COMPOSER = "Call Record";

    private AudioManager mAudioMngr;
    private AudioManager audioManager;

    private Object mHandlerLock = new Object();
    Handler mHandler = null;
    HandlerThread mHandlerThread = null;
    private final int MSG_STARTRECORDING = 0;
    private final int MSG_STOPRECORDING = 1;
    /* @} */

    private long mPauseTime = 0;
    private long mRestartTime = 0;
    private long mDuration;
    public File mSampleDir = null;
    public String mExtension = null;

    public static String[] getSupportRecordMimeType() {
        return new String[]{AUDIO_3GPP, AUDIO_AMR, AUDIO_MP3};
    }

    public interface OnStateChangedListener {
        //public void onStateChanged(int state);
        public void onError(int error);

        /* SPRD: update @{ */
        public void onStateChanged(StateEvent event, Bundle extra);
    }

    /* SPRD: add @{ */
    public static class StateEvent {
        public int nowState;

        public StateEvent(int nowState) {
            this.nowState = nowState;
        }
    }
    /* @} */

    OnStateChangedListener mOnStateChangedListener = null;

    private Object mStopLock = new Object();  // SPRD： add
    private Object mReleaseLock = new Object();  // SPRD： add

    long mSampleStart = 0;       // time at which latest record or play operation started
    int mSampleLength = 0;      // length of current sample
    File mSampleFile = null;
    long mSuspended = 0;  // SPRD： add

    MediaRecorder mRecorder = null;
    MediaPlayer mPlayer = null;
    public boolean mPausedAuto = false;  // SPRD： add
    public String mFileName;

    public Recorder() {
    }

    /* SPRD: add @{ */
    private RecordService mRecordService;
    private SoundRecorder mSoundRecorderActivity = new SoundRecorder();
    public boolean mIsAudioFocus_Loss = false;

    public Recorder(RecordService service) {
        mRecordService = service;
    }

    public void initialize() {
        mHandlerThread = new HandlerThread("handler_thread");
        if (mHandlerThread != null) {
            mHandlerThread.start();
            synchronized (mHandlerLock) {
                mHandler = new Handler(mHandlerThread.getLooper()) {
                    public void handleMessage(Message msg) {
                        Log.d(TAG, "mHandlerThread msg=" + msg);
                        switch (msg.what) {
                            case MSG_STARTRECORDING: {
                                doStartRecording(msg.arg1);
                                break;
                            }

                            case MSG_STOPRECORDING: {
                                doStopRecording();
                                break;
                            }

                            default: {
                                Log.w(TAG, "received a piece of wrong message");
                            }
                        }
                    }
                };
            }
        }
    }

    public void uninitialize() {
        synchronized (mHandlerLock) {
            if (mHandler != null) {
                mHandler.removeMessages(MSG_STARTRECORDING);
                mHandler.removeMessages(MSG_STOPRECORDING);
                mHandler = null;
                mHandlerThread.quit();
                mHandlerThread = null;
            }
        }
    }
/* @} */

    public void saveState(Bundle recorderState) {
        // SPRD： add adjust
        if (mSampleFile != null) {
            recorderState.putString(SAMPLE_PATH_KEY, mSampleFile.getAbsolutePath());
        }
        recorderState.putInt(SAMPLE_LENGTH_KEY, mSampleLength);
    }

    public int getMaxAmplitude() {
        /* SPRD: update @{ */
        synchronized (mReleaseLock) {
            if (mRecorder == null) return 0;
            return mRecorder.getMaxAmplitude();
        }
        /* @} */
    }

    public void restoreState(Bundle recorderState) {
        String samplePath = recorderState.getString(SAMPLE_PATH_KEY);
        if (samplePath == null)
            return;
        int sampleLength = recorderState.getInt(SAMPLE_LENGTH_KEY, -1);
        if (sampleLength == -1)
            return;

        File file = new File(samplePath);
        if (!file.exists())
            return;
        if (mSampleFile != null
                && mSampleFile.getAbsolutePath().compareTo(file.getAbsolutePath()) == 0)
            return;

        delete();
        mSampleFile = file;
        mSampleLength = sampleLength;

        signalStateChanged(IDLE_STATE);
    }

    public void setOnStateChangedListener(OnStateChangedListener listener) {
        mOnStateChangedListener = listener;
    }

    public int state() {
        return mState;
    }

    /* SPRD: update @{
    public int progress() {
        if (mState == RECORDING_STATE || mState == PLAYING_STATE)
            return (int) ((System.currentTimeMillis() - mSampleStart)/1000);
        return 0;
    }*/

    public int progress() {
        if (mState == RECORDING_STATE) {
            return (int) ((SystemClock.elapsedRealtime() - mSampleStart + mSuspended) / 1000);
        } else if (mState == SUSPENDED_STATE) {
            return (int) ((mSuspended) / 1000);
        } else if (mState == PLAYING_STATE) {
            return (int) ((SystemClock.elapsedRealtime() - mSampleStart) / 1000);
        }
        return 0;
    }

    // SPRD： update method name
    public int sampleLengthMillsec() {
        return mSampleLength;
    }

    /* SPRD: add @{ */
    public int sampleLengthSec() {
        return Math.round((float) mSampleLength / 1000);
    }
/* @} */

    public File sampleFile() {
        return mSampleFile;
    }

    /**
     * Resets the recorder state. If a sample was recorded, the file is deleted.
     */
    public void delete() {
        /** SPRD:Bug 615194  com.android.soundrecorder happens JavaCrash,log:java.util.concurrent.TimeoutException ( @{ */
        //stop();
        /** @} */
        if (mSampleFile != null) {
            //bug 1209101 coverity scan: Exceptional return value of java.io.File.delete() ignored
            boolean result = mSampleFile.delete();
            if (!result)
                Log.d(TAG, "delete faild");
        }

        mSampleFile = null;
        mSampleLength = 0;

        signalStateChanged(IDLE_STATE);
    }

    /* SPRD: add @{ */
    public void resetSample() {
        synchronized (mStopLock) {
            mSampleFile = null;
        }
        mSampleLength = 0;
    }

    /* @} */
    public void setSample(File samplefile) {
        mSampleFile = samplefile;
    }

    /**
     * Resets the recorder state. If a sample was recorded, the file is left on disk and will
     * be reused for a new recording.
     */
    public void clear() {
        stop();

        mSampleLength = 0;

        signalStateChanged(IDLE_STATE);
    }

    private long maxSize = 0l;

    public void setRecordMaxSize(long size) {
        maxSize = size;
    }

    private static final int SET_STATE = 0;  // SPRD： add
    private static final int SET_ERROR = 1;  // SPRD： add
    private static final int SET_IDLE = 2;  // SPRD： add
    /* SPRD: add @{ */
    Handler recordHandler = new Handler() {
        public void handleMessage(Message msg) {
            int what = msg.what;
            switch (what) {
                case SET_STATE:
                    setState(RECORDING_STATE);
                    break;
                case SET_IDLE:
                    setState(IDLE_STATE);
                    break;
                case SET_ERROR:
                    int error = msg.arg1;
                    setError(error);
                    break;
                default:
                    throw new RuntimeException("can't handle this code:" + what);
            }
        }
    };

    /* @} */
    /* SPRD: add @{ */
    public String startRecording(final int outputfileformat, final String extension,
                                 final String selectedPath, String filenewname) {
        //stop();

        File sampleDir = null;
        if (!selectedPath.equals("")) {
            sampleDir = new File(selectedPath);
        } else {

            File pathDir = StorageInfos.getInternalStorageDirectory();
            if (pathDir == null) {
                recordHandler.obtainMessage(SET_ERROR, PATH_NOT_EXIST, 0).sendToTarget();
                resetSample();
                return null;
            }

            sampleDir = new File(pathDir.getPath()
                    + DEFAULT_STORE_SUBDIR);
        }
        if (!sampleDir.isDirectory() && !sampleDir.mkdirs()) {
            Log.e("SoundRecorder",
                    "Recording File aborted - can't create base directory "
                            + sampleDir.getPath());
            recordHandler.obtainMessage(SET_ERROR, PATH_NOT_EXIST, 0).sendToTarget();
            resetSample();
            return null;
        }

        try {
            Date date = new Date();
            SimpleDateFormat fileNameFormatter = new SimpleDateFormat("yyyyMMdd-HHmmss");
            mFileName = fileNameFormatter.format(date);
            mSampleDir = sampleDir;
            mExtension = extension;
            String recordFileName = sampleDir.getPath() + File.separator
                    + fileNameFormatter.format(date);
            int i = 0;
            do {
                mSampleFile = new File(recordFileName + (i == 0 ? "" : ("[" + i + "]")) + extension + ".tmp");
                i++;
            } while (!mSampleFile.createNewFile());
        } catch (Exception e) {
            Log.d(TAG, "mSampleFile Exception" + e);
            resetSample();
            recordHandler.obtainMessage(SET_ERROR, PATH_NOT_EXIST, 0).sendToTarget();
            return null;
        }

        synchronized (mHandlerLock) {
            if (mHandler != null) {
                mHandler.removeMessages(MSG_STARTRECORDING);//bug 623076 Internal error in the tape recorder
                mHandler.obtainMessage(MSG_STARTRECORDING, outputfileformat, -1).sendToTarget();
            }
        }
        if (mSampleFile != null) {
            return mSampleFile.getAbsolutePath();
        } else {
            return null;
        }
    }

    private void doStartRecording(int outputFileFormat) {
        try {
            mRecorder = new MediaRecorder();
            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mRecorder.setOutputFormat(outputFileFormat);
            if (outputFileFormat == MediaRecorder.OutputFormat.AMR_WB) {
                mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB);
                mRecorder.setAudioSamplingRate(16000);
                mRecorder.setAudioEncodingBitRate(SoundRecorder.BITRATE_AMR_WB);// add by  zl  for bug 130427, 2013/2/27
            } else if (outputFileFormat == MediaRecorder.OutputFormat.AMR_NB) {
                mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                mRecorder.setAudioSamplingRate(8000);
                mRecorder.setAudioEncodingBitRate(SoundRecorder.BITRATE_AMR_NB); // add by  zl  for bug 130427, 2013/2/27
            } else if (outputFileFormat == MediaRecorder.OutputFormat.THREE_GPP) {
                mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                mRecorder.setAudioSamplingRate(44100);
                mRecorder.setAudioEncodingBitRate(SoundRecorder.BITRATE_3GPP); // add by  zl  for bug 130427, 2013/2/27
            } else if (outputFileFormat == SoundRecorder.MP3) {//SPRD:Bug 626265 add mp3 type in soundrecorder
                mRecorder.setAudioEncoder(SoundRecorder.AudioEncodeMP3);
                mRecorder.setAudioSamplingRate(44100);
                mRecorder.setAudioEncodingBitRate(SoundRecorder.BITRATE_MP3);
            }
            mRecorder.setOutputFile(mSampleFile.getAbsolutePath());
        } catch (RuntimeException exception) {
            recordHandler.obtainMessage(SET_ERROR, INTERNAL_ERROR, 0).sendToTarget();
            recordHandler.obtainMessage(SET_IDLE).sendToTarget();
            if (mRecorder != null) {
                mRecorder.reset();
                synchronized (mReleaseLock) {
                    android.util.Log.d(TAG, "mReleaseLock 1");
                    mRecorder.release();
                    mRecorder = null;
                }
            }
            return;
        }

        // Handle IOException
        try {
            android.util.Log.d(TAG, "setMaxFileSize " + maxSize);
            mRecorder.setMaxFileSize(maxSize);
            mRecorder.setOnInfoListener(new OnInfoListener() {

                @Override
                public void onInfo(MediaRecorder mr, int what, int extra) {
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {

                    } else if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
                        if (mState == RECORDING_STATE) {
                            android.util.Log.d(TAG, "MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED 4");
                            if (mSoundRecorderActivity.getFromMMs()) {
                                Toast.makeText(mSoundRecorderActivity, mSoundRecorderActivity.getString(R.string.low_memory), Toast.LENGTH_SHORT).show();
                            }
                            stopRecording();
                        }
                    }

                }
            });
            mRecorder.prepare();
        } catch (IOException exception) {
            recordHandler.obtainMessage(SET_ERROR, INTERNAL_ERROR, 0).sendToTarget();
            recordHandler.obtainMessage(SET_IDLE).sendToTarget();
            if (mRecorder != null) {
                mRecorder.reset();
                synchronized (mReleaseLock) {
                    android.util.Log.d(TAG, "mReleaseLock 2");
                    //Unisoc Bug 1132815 : Potential null pointer exception from thread lock
                    if (mRecorder != null) {
                        mRecorder.release();
                        mRecorder = null;
                    }
                }
            }
            return;
        }

        try {
            audioManager = (AudioManager) mRecordService.getSystemService(Context.AUDIO_SERVICE);
            audioManager.requestAudioFocus(mAudioFocusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);

            if (!mSoundRecorderActivity.getActivityState()) {
                if (audioManager.isMusicActive()) {
                    Thread.currentThread().sleep(500);
                }
                mRecorder.start();
            } else {
                if (mRecorder != null) {
                    mRecorder.reset();
                    synchronized (mReleaseLock) {
                        android.util.Log.d(TAG, "mReleaseLock 3");
                        mRecorder.release();
                        mRecorder = null;
                    }
                }
                recordHandler.obtainMessage(SET_IDLE).sendToTarget();
                return;
            }
        } catch (Exception exception) {
            exception.printStackTrace();
            AudioManager audioMngr = (AudioManager) mRecordService
                    .getSystemService(Context.AUDIO_SERVICE);
            boolean isInCall = ((audioMngr.getMode() == AudioManager.MODE_IN_CALL) || (audioMngr
                    .getMode() == AudioManager.MODE_IN_COMMUNICATION));
            if (isInCall) {
                recordHandler.obtainMessage(SET_ERROR, IN_CALL_RECORD_ERROR, 0).sendToTarget();
            } else {
                recordHandler.obtainMessage(SET_ERROR, INTERNAL_ERROR, 0).sendToTarget();
            }
            recordHandler.obtainMessage(SET_IDLE).sendToTarget();
            if (mRecorder != null) {
                mRecorder.reset();
                synchronized (mReleaseLock) {
                    android.util.Log.d(TAG, "mReleaseLock 4");
                    mRecorder.release();
                    mRecorder = null;
                }
            }
            return;
        }
        mSampleStart = SystemClock.elapsedRealtime();
        mSuspended = 0;
        recordHandler.obtainMessage(SET_STATE).sendToTarget();
    }
    /* @} */

    /* SPRD: add @{ */
    public void pauseRecording() {
        if (mRecorder == null) {
            Log.d(TAG, "pauseRecording mRecorder = null!");
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return;
        }
        try {
            mRecorder.pause();
        } catch (IllegalStateException exception) {
            Log.e(TAG, "mRecorder pause error. " + exception);
            stopRecording();
            return;
        } catch (RuntimeException e) {
            Log.e(TAG, "mRecorder pause error. " + e);
            stopRecording();
            return;
        }
        mSuspended = SystemClock.elapsedRealtime() - mSampleStart + mSuspended;
        mSampleLength = (int) (mSampleLength + (SystemClock.elapsedRealtime() - mSampleStart));//modify for bug 142701 20130329
        setState(SUSPENDED_STATE);
    }

    public void resumeRecording() {
        if (mRecorder == null) {
            Log.d(TAG, "resumeRecording mRecorder = null!");
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return;
        }
        audioManager = (AudioManager) mRecordService.getSystemService(Context.AUDIO_SERVICE);
        audioManager.requestAudioFocus(mAudioFocusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);

        mSampleStart = SystemClock.elapsedRealtime();
        try {
            mRecorder.resume();
        } catch (IllegalStateException exception) {
            Log.e(TAG, "mRecorder resume error. " + exception);
            stopRecording();
            return;
        } catch (RuntimeException e) {
            Log.e(TAG, "mRecorder resume error. " + e);
            stopRecording();
            return;
        }
        setState(RECORDING_STATE);
    }
    /* @} */

    /* SPRD: remove @{
    public void stopRecording() {
        if (mRecorder == null)
            return;

        mRecorder.stop();
        mRecorder.release();
        mRecorder = null;

        mSampleLength = (int)( (System.currentTimeMillis() - mSampleStart)/1000 );
        setState(IDLE_STATE);
    }
    /* @} */

    /* SPRD: add @{ */
    public void stopRecording() {
        synchronized (mHandlerLock) {
            if (mHandler != null) {
                mHandler.obtainMessage(MSG_STOPRECORDING).sendToTarget();
            }
        }
    }

    private void doStopRecording() {
        synchronized (RecordService.mStopSyncLock) {
            synchronized (mStopLock) {
                synchronized (mReleaseLock) {
                    //bug 1210740 coverity sacn:Dereference after null check (FORWARD_NULL)
                    if (mRecorder == null ) {
                        Log.d(TAG, "doStopRecording mRecorder = null!");
                        return;
                    }else if (!mRecordService.isRecording()){
                        Log.d(TAG, "record service is not reording");
                        return;
                    }
                    try {
                        if (mState == RECORDING_STATE) {
                            mSampleLength = mSampleLength + (int) ((SystemClock.elapsedRealtime() - mSampleStart));
                        }
                        if (mSampleLength < 1000) {
                            Log.d(TAG, "doStopRecording recording time is short!");
                            setError(RECORDING_ERROR);
                            if (mIsAudioFocus_Loss) {
                                mRecordService.stopRecord();
                                delete();
                                if (mRecorder != null) {
                                    android.util.Log.i(TAG, "mReleaseLock 6");
                                    mRecorder.release();
                                    mRecorder = null;
                                }
                                return;
                            }
                        }
                        mRecorder.stop();
                        if (mSampleLength == 0) {
                            if (mSampleFile != null) {
                                boolean result = mSampleFile.delete();
                                if (!result)
                                    Log.d(TAG, "delete faild");
                            }
                            notifyStateChanged(STOP_STATE, null);
                        } else {
                            notifyStateChanged(STOP_STATE);
                        }
                    } catch (RuntimeException e) {
                        notifyStateChanged(STOP_STATE,null);
                        if (mSampleFile != null) {
                            mSampleFile.delete();
                            mSampleFile = null;
                            setError(RECORDING_ERROR);
                        }
                        Log.w(TAG, "did you call stop() immediately after start()?", e);
                    } finally {
                        if (mRecorder != null && mSampleLength >= 1000) {
                            android.util.Log.i(TAG, "mReleaseLock 5");
                            mRecorder.release();
                            mRecorder = null;
                        }
                    }
                }
                recordHandler.obtainMessage(SET_IDLE).sendToTarget();

                audioManager = (AudioManager) mRecordService
                        .getSystemService(Context.AUDIO_SERVICE);
                audioManager.abandonAudioFocus(mAudioFocusListener);
            }
            try {
                Log.i(TAG, "notify()");
                SoundRecorder.flag = true;
                RecordService.mStopSyncLock.notify();
            } catch (Exception e) {
                Log.e(TAG, "Exception:", e);
            }
        }
    }
    /* @} */

    /* SPRD: update @{ */
    //public void startPlayback() {
    public void startPlayback(Context context) {
        stop();
        mAudioMngr = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mAudioMngr.requestAudioFocus(mAudioFocusListener, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);
        /* @} */
        mPlayer = new MediaPlayer();
        try {
            mPlayer.setDataSource(mSampleFile.getAbsolutePath());
            mPlayer.setOnCompletionListener(this);
            mPlayer.setOnErrorListener(this);
            mPlayer.prepare();
            mPlayer.start();
        } catch (IllegalArgumentException e) {
            setError(INTERNAL_ERROR);
            mPlayer = null;
            return;
        } catch (IOException e) {
            Log.d(TAG, "IOException" + e);
            setError(SDCARD_ACCESS_ERROR);
            mPlayer = null;
            return;
        }

        //mSampleStart = System.currentTimeMillis();
        mSampleStart = SystemClock.elapsedRealtime();
        setState(PLAYING_STATE);
    }

    /* SPRD: add @{ */
    private OnAudioFocusChangeListener mAudioFocusListener = new OnAudioFocusChangeListener() {
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    if (mPlayer == null && mRecorder == null) {
                        return;
                    } else if (mPlayer != null) {
                        mPlayer.pause();
                        mPauseTime = SystemClock.elapsedRealtime();
                    } else {// Bug 647419 The end of the recording file to read the contents of the beginning of the entry
                        mIsAudioFocus_Loss = true;
                        stopRecording();
                    }
                    break;
                case AudioManager.AUDIOFOCUS_GAIN:
                    if (mPlayer == null) {
                        return;
                    } else {
                        mPlayer.start();
                        mRestartTime = SystemClock.elapsedRealtime();
                        mDuration = mRestartTime - mPauseTime;
                        if (mDuration > 0) {
                            mSampleStart = mSampleStart + mDuration;
                        }
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                    if (mPlayer == null && mRecorder == null) {
                        return;
                    } else if (mPlayer != null) {
                        //mPlayer.stop();
                        stop();
                        if (mAudioMngr != null) {
                            mAudioMngr.abandonAudioFocus(mAudioFocusListener);
                        }
                    } else {
                        /** SPRD:Bug 621711 When the recorder is suspended, the music is played, and the recording should be automatically guaranteed( @{ */
                        mIsAudioFocus_Loss = true;
                        stopRecording();
                    /* @} */
                    }
                    break;
            }

        }
    };
/* @} */

    public void stopPlayback() {
        if (mPlayer == null) // we were not in playback
            return;

        mPlayer.stop();
        mPauseTime = mRestartTime = 0;
        mPlayer.release();
        mPlayer = null;
        /* SPRD: add @{ */
        if (mAudioMngr != null) {
            mAudioMngr.abandonAudioFocus(mAudioFocusListener);
        }
/* @} */
        setState(IDLE_STATE);
    }

    public void stop() {
        stopRecording();
        stopPlayback();
    }

    public boolean onError(MediaPlayer mp, int what, int extra) {
        stop();
        setError(SDCARD_ACCESS_ERROR);
        return true;
    }

    public void onCompletion(MediaPlayer mp) {
        stop();
    }

    //private void setState(int state) {
    public void setState(int state) {
        /* SPRD: update @{
        if (state == mState)
            return;
       @} */
        mState = state;
        signalStateChanged(mState);
    }

    private void signalStateChanged(int state) {
        if (mOnStateChangedListener != null)
            // SPRD： update
            //mOnStateChangedListener.onStateChanged(state);
            notifyStateChanged(state);
    }

    private void setError(int error) {
        if (mOnStateChangedListener != null)
            mOnStateChangedListener.onError(error);
    }

    /* SPRD: add @{ */
    private void notifyStateChanged(int newState) {
        notifyStateChanged(newState, null);
    }

    private void notifyStateChanged(int newState, Bundle extra) {
        StateEvent event = new StateEvent(newState);
        mOnStateChangedListener.onStateChanged(event, extra);
    }
/* @} */

    public boolean reset() {
        boolean result = true;
        synchronized (this) {
            if (null != mRecorder) {
                try {
                    if (mState == Recorder.SUSPENDED_STATE
                            || mState == Recorder.RECORDING_STATE) {
                        mRecorder.stop();
                    }
                } catch (RuntimeException exception) {
                    exception.printStackTrace();
                    result = false;
                    Log.e(TAG,
                            "<stopRecording> recorder illegalstate exception in recorder.stop()");
                } finally {
                    if (null != mRecorder) {
                        mRecorder.reset();
                        mRecorder.release();
                        mRecorder = null;
                    }
                }
            }
        }
        mState = Recorder.IDLE_STATE;
        return result;
    }

}
