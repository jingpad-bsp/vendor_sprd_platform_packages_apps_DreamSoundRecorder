package com.sprd.soundrecorder.service;

import android.content.Context;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;

import com.android.soundrecorder.R;
import com.android.soundrecorder.RemainingTimeCalculator;
import com.sprd.soundrecorder.SettingActivity;
import com.sprd.soundrecorder.StorageInfos;
import com.sprd.soundrecorder.Utils;
import com.sprd.soundrecorder.data.DataOpration;
import com.sprd.soundrecorder.frameworks.StandardFrameworks;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by jian.xu on 2017/10/23.
 */

public class SprdRecorder {

    private static final String TAG = SprdRecorder.class.getSimpleName();

    public static final String DEFAULT_STORE_SUBDIR = "/recordings";
    public static final String PLAYLIST_NAME = "My recordings";
    public static final String AUDIO_3GPP = "audio/3gpp";
    public static final String AUDIO_AMR = "audio/amr";
    public static final String AUDIO_MP3 = "audio/mp3";
    public static final String AUDIO_MP4 = "audio/mp4";
    public static final String AUDIO_ANY = "audio/*";
    public static final String ANY_ANY = "*/*";

    private static final int WAVE_BASE = 200;
    private final int MSG_UPDATE_DURATION_ON_DB = 1;
    private final int CHANNEL_STEREO = 2;

    private final static HashMap<String, String> FILE_EXTENSION = new HashMap<String, String>() {
        {
            put(AUDIO_AMR, ".amr");
            put(AUDIO_3GPP, ".3gpp");
            put(AUDIO_MP3, ".mp3");
        }
    };

    private final static HashMap<String, Integer> FILE_FORMAT = new HashMap<String, Integer>() {
        {
            put(AUDIO_AMR, MediaRecorder.OutputFormat.AMR_NB);
            put(AUDIO_3GPP, MediaRecorder.OutputFormat.THREE_GPP);
            put(AUDIO_MP3, 12);//Bug 1012386 modify mp3 parameter
        }

    };

    private final static HashMap<String, Integer> RECORDER_MAP = new HashMap<String, Integer>() {
        {
            put(AUDIO_AMR, MediaRecorder.AudioEncoder.AMR_NB);
            put(AUDIO_3GPP, MediaRecorder.AudioEncoder.AAC);
            put(AUDIO_MP3, 8);//Bug 1012386 modify mp3 parameter
        }

    };

    private final static HashMap<String, Integer> BITRATE_MAP = new HashMap<String, Integer>() {
        {
            put(AUDIO_AMR, 5900);
            put(AUDIO_3GPP, 96000);
            put(AUDIO_MP3, 320000);
        }
    };

    private final static HashMap<String, Integer> SAMPLERATE_MAP = new HashMap<String, Integer>() {
        {
            put(AUDIO_AMR, 8000);
            put(AUDIO_3GPP, 44100);
            put(AUDIO_MP3, 44100);
        }
    };

    public static final String DEFAULT_TYPE = AUDIO_3GPP;

    public static final String CALL_COMPOSER = "Call Record";
    public static final String SAMPLE_PREFIX = "recording";


    //public static final int AMR = MediaRecorder.OutputFormat.AMR_NB;
    //public static final int THREE_3GPP = MediaRecorder.OutputFormat.THREE_GPP;
    //public static final int MP3 = 11;

    private String mRequstType = DEFAULT_TYPE;
    private String mFileName = "";
    private String mParentPath = "";
    private File mRecordFile;

    private RemainingTimeCalculator mRemainingTimeCalculator;
    private MediaRecorder mRecorder;
    private long mMaxSize = -1;
    private long mStartTime;
    private long mRecordDuration;
    private int mMaxDuration;
    private Uri mUri;


    public enum ErrorInfo {
        NO_ERROR,
        SDCARD_ACCESS_ERROR,
        INTERNAL_ERROR,
        NO_SDCARD,
        PATH_NOT_EXIST,
        INTERNAL_NO_ENOUGH_SPACE,
        NO_ENOUGH_SPACE,
        NO_ENOUGH_SPACE_SAVE_WAVE_TAG,
        MIC_IS_USING,
        IS_IN_CALLING,
        NOT_SUPPORT_TYPE,
        NO_INIT,
        DURATION_TOO_SHORT,
        OTHER_ERROR

    }

    private RecordingService mService;
    private AudioManager mAudioManager;

    public SprdRecorder(RecordingService service) {
        mService = service;
        mAudioManager = (AudioManager) mService.getSystemService(Context.AUDIO_SERVICE);
    }

    public static String[] getSupportRecordMimeType() {
        return new String[]{AUDIO_MP3,AUDIO_3GPP, AUDIO_AMR};
    }

    public static String[] getSupportRecordTypeString(Context context) {
        return new String[]{
                context.getString(R.string.record_mp3),
                context.getString(R.string.record_3gpp),
                context.getString(R.string.record_amr)};
    }

    public static boolean isSupportRecordMimeType(String type) {
        return AUDIO_3GPP.equals(type) || AUDIO_AMR.equals(type) || AUDIO_MP3.equals(type);
    }

    public ErrorInfo startRecording(String requestType) {
        Log.d(TAG, "startRecording:" + requestType);
        mRequstType = requestType;
        ErrorInfo result = checkRecordEnvironment();
        if (result == ErrorInfo.NO_ERROR) {
            if (isSupportRecordMimeType(requestType)) {
                result = doStartRecording(requestType);
            } else {
                result = ErrorInfo.NOT_SUPPORT_TYPE;
            }
        }
        if (result != ErrorInfo.NO_ERROR) {
            releaseRecorder(true);
        }
        return result;
    }

    public ErrorInfo pauseRecording() {
        if (mRecorder == null) {
            return ErrorInfo.NO_INIT;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return ErrorInfo.NO_ERROR;
        }
        try {
            mRecorder.pause();
            mRecordDuration = mRecordDuration + (SystemClock.elapsedRealtime() - mStartTime);
            mStartTime = 0;
        } catch (Exception exception) {
            exception.printStackTrace();
            releaseRecorder(true);
            return ErrorInfo.INTERNAL_ERROR;
        }
        return ErrorInfo.NO_ERROR;
    }

    public ErrorInfo resumeRecording() {
        if (mRecorder == null) {
            return ErrorInfo.NO_INIT;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return ErrorInfo.NO_ERROR;
        }
        try {
            mRecorder.resume();
            mStartTime = SystemClock.elapsedRealtime();
        } catch (Exception exception) {
            exception.printStackTrace();
            releaseRecorder(true);
            return ErrorInfo.INTERNAL_ERROR;
        }
        return ErrorInfo.NO_ERROR;
    }
    public boolean recordFileExist(){
        if (mRecordFile!=null && mRecordFile.exists()){
            return true;
        }
        return false;
    }
    public ErrorInfo stopRecording(boolean isError) {
        if (mRecorder == null) {
            return ErrorInfo.NO_INIT;
        }

        long duration = 0;
        if (mStartTime > 0) {
            duration = mRecordDuration + (SystemClock.elapsedRealtime() - mStartTime);
        } else {
            duration = mRecordDuration;
        }
        if (duration < 1000) {
            return ErrorInfo.DURATION_TOO_SHORT;
        }
        mRecordDuration = duration;

        try {
            mRecorder.stop();
            //915813 make recording,and the changed the temp file by another process,which caused saving unnormal.
            if (!isError && isTempFileExists()) {
                File finalFile = new File(getRecordFileFullName());
                //bug 1210740 coverity scan:Exceptional return value of java.io.File.renameTo(File) ignored
                boolean result = mRecordFile.renameTo(finalFile);
                if (!result) {
                    mRecordFile.delete();
                    return ErrorInfo.OTHER_ERROR;
                }
                Log.d(TAG,"lq stopRecordingmRecordDuration = " + mRecordDuration + ", finalFile = " + finalFile );
                mUri = DataOpration.addToMediaDB(finalFile, mService, mRecordDuration, mRequstType, mService.getTagSize());

                /*bug 1000707 update duration from mediaplayer after adding to MediaDB successfully @{*/
                if (mUri != null) {
                    DurationDataUpdate dataUpdate = new DurationDataUpdate(getRecordFileFullName(), mUri);
                    handler.obtainMessage(MSG_UPDATE_DURATION_ON_DB, dataUpdate).sendToTarget();
                } else {
                    //bug 1178989 mUri may be null
                    mRecordFile.delete();
                    return ErrorInfo.INTERNAL_ERROR;
                }
                /*}@*/
            } else {
                mRecordFile.delete();
                return ErrorInfo.OTHER_ERROR;
            }
            //releaseRecorder(false);
        } catch (IllegalArgumentException e) {
            //bug 1174943 save recording in OTG, IllegalArgumentException will be throw,if SD card is formatted for internal storage and unplug it
            e.printStackTrace();
            return ErrorInfo.NO_ENOUGH_SPACE_SAVE_WAVE_TAG;
        } catch (Exception e) {
            e.printStackTrace();
            releaseRecorder(true);
            return ErrorInfo.INTERNAL_ERROR;
        }
        return ErrorInfo.NO_ERROR;
    }

    /*bug 1000707 update duration from mediaplayer after adding to MediaDB successfully @{*/
    private class DurationDataUpdate {
        public String mFileName;
        public Uri mDataUri;

        public DurationDataUpdate(String fileName, Uri dataUri) {
            this.mFileName = fileName;
            this.mDataUri = dataUri;
        }
    }

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case MSG_UPDATE_DURATION_ON_DB:
                    DurationDataUpdate dataUpdate = (DurationDataUpdate)msg.obj;
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            MediaPlayer mp = new MediaPlayer();
                            try {
                                mp.setDataSource(dataUpdate.mFileName);
                                mp.prepare();
                            } catch (Exception e) {
                                Log.d(TAG, e.toString());
                            }
                            int duration = mp.getDuration();
                            mp.release();

                            Log.d(TAG, "dataUpdate.mFileName = " + dataUpdate.mFileName + ", duration = " + duration);
                            ContentResolver resolver = ((Context)mService).getContentResolver();
                            ContentValues values = new ContentValues(1);
                            values.put(MediaStore.Audio.Media.DURATION, duration);
                            resolver.update(dataUpdate.mDataUri, values, null, null);
                        }
                    }).start();

                    break;
                default:
                    break;
            }
        }
    };
    /*}@*/

    private boolean isTempFileExists() {
        if (mRecordFile == null) {
            return false;
        }
        File updateRecordFile = new File(mRecordFile.getParent());
        File[] files = updateRecordFile.listFiles();
        if (files == null) {
            return false;
        }
        for (int i = 0; i < files.length; i++) {
            if (mRecordFile.getName().equals(files[i].getName())) {
                return true;
            }
            if (files[i].getName().contains(".tmp")) {
                Log.d(TAG, "delete temp filename =" + files[i].getName());
                files[i].delete();
            }
        }
        return false;
    }

    public ErrorInfo stopRecording() {
        return stopRecording(false);
    }

    public long getRecordDuration() {
        long nowDuration = 0;
        if (mStartTime > 0) {
            nowDuration = mRecordDuration + (SystemClock.elapsedRealtime() - mStartTime);
        } else {
            nowDuration = mRecordDuration;
        }
        return nowDuration;
    }

    private ErrorInfo doStartRecording(String requestType) {
        try {
            if (mRecordFile == null) {
                return ErrorInfo.NO_INIT;
            }
            mRecorder = new MediaRecorder();
            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mRecorder.setOutputFormat(FILE_FORMAT.get(requestType));
            mRecorder.setAudioEncoder(RECORDER_MAP.get(requestType));
            //BUG:896235 double track recording MP3 format
            if (AUDIO_MP3.equals(requestType)) {
                mRecorder.setAudioChannels(CHANNEL_STEREO);
            }
            mRecorder.setAudioSamplingRate(SAMPLERATE_MAP.get(requestType));
            mRecorder.setAudioEncodingBitRate(BITRATE_MAP.get(requestType));
            mRecorder.setOutputFile(mRecordFile.getAbsolutePath());
            mRecorder.setMaxFileSize(mMaxSize);
            if (mMaxDuration != -1) {
                mRecorder.setMaxDuration(mMaxDuration);
            }
            mRecorder.setOnInfoListener(mRecorderInfoListener);
            mRecorder.prepare();
            mAudioManager.requestAudioFocus(mAudioFocusListener,
                    AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            if (mAudioManager.isMusicActive()) {
                // bug915918 The recorder can work with video at the same time，modify the dalay time
                Thread.currentThread().sleep(20);
            }
            mRecorder.start();
            mStartTime = SystemClock.elapsedRealtime();
        } catch (Exception exception) {
            exception.printStackTrace();
            releaseRecorder(true);
            return ErrorInfo.INTERNAL_ERROR;
        }


        return ErrorInfo.NO_ERROR;
    }

    public Uri getUri() {
        return mUri;
    }

    public void releaseRecorder(boolean isError) {
        try {
            //bug 1262161 MediaRecorder will throw IllegalStateException if reset again
            mRecorder.reset();
            mRecorder.release();
        } catch (IllegalStateException | NullPointerException e) {
            e.printStackTrace();
        } finally {
            mRecorder = null;
        }
        if (isError && mRecordFile != null) {
            boolean result = mRecordFile.delete();
            if (!result)
                Log.d(TAG, "faild to delete temp record file =" + mRecordFile.getPath());
        }
        mRecordFile = null;
        mAudioManager.abandonAudioFocus(mAudioFocusListener);
        mMaxSize = -1;
        mMaxDuration = -1;
        mStartTime = 0;
        mRecordDuration = 0;
        mRequstType = DEFAULT_TYPE;
        mFileName = "";
        mParentPath = "";
        mUri = null;
    }

    private AudioManager.OnAudioFocusChangeListener mAudioFocusListener =
            new AudioManager.OnAudioFocusChangeListener() {

                @Override
                public void onAudioFocusChange(int i) {
                    switch (i) {
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                        case AudioManager.AUDIOFOCUS_LOSS:
                            Log.d(TAG, "stop by audiofocus loss:" + i);
                            mService.stopRecord();
                            //stopRecording();
                            break;
                    }
                }
            };

    private MediaRecorder.OnInfoListener mRecorderInfoListener
            = new MediaRecorder.OnInfoListener() {

        @Override
        public void onInfo(MediaRecorder mediaRecorder, int i, int i1) {
            if (i == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED
                    || i == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
                Log.d(TAG, "MediaRecorder info:" + i);
                mService.stopRecord();
            }
        }
    };

    private ErrorInfo checkRecordEnvironment() {
        //Unisoc 915301 check storage environment unnormal
        /*if (!StorageInfos.haveEnoughStorage(StorageInfos.getInternalStorageDirectory().getPath()) ) {
            return ErrorInfo.INTERNAL_NO_ENOUGH_SPACE;
        }*/

        if (StandardFrameworks.getInstances().isAudioSourceActive(MediaRecorder.AudioSource.MIC) ||
                StandardFrameworks.getInstances().isAudioSourceActive(MediaRecorder.AudioSource.CAMCORDER) ||
                StandardFrameworks.getInstances().isAudioSourceActive(MediaRecorder.AudioSource.VOICE_RECOGNITION)) {
            return ErrorInfo.MIC_IS_USING;
        }
        if (Utils.isInCallState(mService)) {
            return ErrorInfo.IS_IN_CALLING;
        }

        String selectPath = SettingActivity.getRecordSavePath(mService);
        /*if (!StorageInfos.haveEnoughStorage(selectPath)) {
            return ErrorInfo.NO_ENOUGH_SPACE;
        }*/
        if (!checkStorageSize(selectPath)) {
            return ErrorInfo.NO_ENOUGH_SPACE;
        }

        File pathFile = new File(selectPath);
        if (!pathFile.isDirectory() && !pathFile.mkdirs()) {
            return ErrorInfo.PATH_NOT_EXIST;
        }

        mParentPath = selectPath;

        makeRecordFile(pathFile, mRequstType);
        if (mFileName.equals("")) {
            return ErrorInfo.PATH_NOT_EXIST;
        }

        return ErrorInfo.NO_ERROR;
    }

    public String getRecordFileName() {
        return mFileName;
    }

    public String getRequstType() {
        return mRequstType;
    }

    public void setRecordFileName(String newName) {
        mFileName = newName;
    }

    public String getRecordFileFullName() {
        if (mRecordFile != null) {
            return mParentPath + File.separator + mFileName + FILE_EXTENSION.get(mRequstType);
        }
        return null;
    }

    public static String getFileExtension(String requestType) {
        return FILE_EXTENSION.get(requestType);
    }

    private void makeRecordFile(File pathFile, String requestType) {
        Date date = new Date();
        SimpleDateFormat fileNameFormatter = new SimpleDateFormat("yyyyMMdd-HHmmss");
        String fileName = fileNameFormatter.format(date);
        int i = 0;
        try {
            do {
                mFileName = fileName + (i == 0 ? "" : ("[" + i + "]"));

                mRecordFile = new File(pathFile.getPath() + File.separator + mFileName +
                        FILE_EXTENSION.get(requestType) + ".tmp");
                i++;
            } while (!mRecordFile.createNewFile());
        } catch (IOException e) {
            e.printStackTrace();
            mFileName = "";
        }
    }

    public String getTmpFilePath() {
        if (mRecordFile == null) {
            return "";
        }
        return mParentPath + File.separator;
    }

    public String getTmpFileName() {
        if (mRecordFile == null) {
            return "";
        }
        String filePath = mRecordFile.getPath();
        return filePath.substring(filePath.lastIndexOf("/") + 1, filePath.length());
    }

    private boolean checkStorageSize(String selectPath) {
        long storageSize = 0;
        Map<String, String> map = StorageInfos.getStorageInfo(selectPath);
        if (map != null) {
            if (Boolean.parseBoolean(map.get("isEnough"))) {
                storageSize = Long.parseLong(map.get("availableBlocks"));
            }else {
                return false;
            }
        }
        if (mMaxSize == -1) {
            mMaxSize = storageSize;
            return true;
        } else {
            Log.d(TAG, "already set MaxSize by setRecordMaxSize:" + mMaxSize);
            if (mMaxSize <= storageSize) {
                return true;
            }
        }
        return false;
    }

    public void setRecordMaxSize(long size) {
        mMaxSize = size;
    }

    public void setMaxDuration(int duration) {
        mMaxDuration = duration;
    }

    public float getCurrentWaveData() {
        if (mRecorder != null) {
            try {
                float amplitude = (float) mRecorder.getMaxAmplitude() / WAVE_BASE;
                if (amplitude != 0) {
                    float db = (float) (20 * Math.log10(amplitude));
                    Log.d(TAG, "getWaveData db = " + db);
                    if (db < 2) {
                        db = 2;
                    }
                    return db;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        //bug 1150214 return 1 when amplitude = 0 for saving wavedata
        return 1;
    }


}
