package com.sprd.soundrecorder;

import android.content.ContentUris;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import com.android.soundrecorder.R;
import com.sprd.soundrecorder.data.RecorderItem;

import java.io.IOException;

/**
 * Created by jian.xu on 2017/10/11.
 */

public class RecordPreviewPlayer extends MediaPlayer implements
        MediaPlayer.OnPreparedListener,
        MediaPlayer.OnErrorListener,
        MediaPlayer.OnCompletionListener {
    private static final String TAG = RecordPreviewPlayer.class.getSimpleName();

    private boolean mIsPrepared = false;
    private boolean mIsPaused = false;
    private boolean mIsPlaybackComp = false;
    private Context mContext;
    private int mDuration;
    private PlayerListener mPlayerListener;
    private PlayModeCallBack mPlayModeCallBack;

    private RecorderItem mData;
    private AudioManager mAudioManager;
    private boolean mIsGetAudioFocus = false;

    private static final int FADEDOWN = 1;
    private static final int FADEUP = 2;
    private Toast mToast = null;

    private Handler mHandler = new Handler() {
        private float mCurrentVolume = 1.0f;

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Log.d(TAG, "handleMessage msg.what = " + msg.what);
            switch (msg.what) {
                case FADEDOWN:
                    mCurrentVolume -= .05f;
                    if (mCurrentVolume > .2f) {
                        mHandler.sendEmptyMessageDelayed(FADEDOWN, 10);
                    } else {
                        mCurrentVolume = .2f;
                    }
                    setVolume(mCurrentVolume, mCurrentVolume);
                    break;
                case FADEUP:
                    mCurrentVolume += .01f;
                    if (mCurrentVolume < 1.0f) {
                        mHandler.sendEmptyMessageDelayed(FADEUP, 10);
                    } else {
                        mCurrentVolume = 1.0f;
                    }
                    setVolume(mCurrentVolume, mCurrentVolume);
                    break;
            }
        }
    };

    public RecordPreviewPlayer(Context context) {
        super();
        mContext = context;
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        setOnPreparedListener(this);
        setOnErrorListener(this);
        setOnCompletionListener(this);
    }

    public void setDataAndPrepare(RecorderItem data)
            throws IllegalArgumentException, SecurityException,
            IllegalStateException, IOException {
        mData = data;
        Uri uri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                mData.getId());
        setDataSource(mContext, uri);
        prepareAsync();
    }

    private boolean requestAudioFocus() {
        int audioFocus = mAudioManager.requestAudioFocus(
                mAudioFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        if (audioFocus == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return true;
        }
        return false;
    }

    private AudioManager.OnAudioFocusChangeListener mAudioFocusListener =
            new AudioManager.OnAudioFocusChangeListener() {
                //public boolean mPausedByTransientLossOfFocus = false;
                public boolean mPauseByFocusCanDuck = false;

                @Override
                public void onAudioFocusChange(int focusChange) {
                    Log.d(TAG, "onAudioFocusChange focusChange = " + focusChange);
                    switch (focusChange) {
                        case AudioManager.AUDIOFOCUS_LOSS:
                            if (isPlaying()){
                                mIsGetAudioFocus = false;
                            }
                            if (isPlaying()||isPaused()){
                                pauseByAudioFocusLoss();
                            }
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                            if (isPlaying()){
                                mIsGetAudioFocus = false;
                            }
                            if (isPlaying()||isPaused()){
                                pauseByAudioFocusLoss();
                            }
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                            mPauseByFocusCanDuck = true;
                            mHandler.removeMessages(FADEUP);
                            mHandler.sendEmptyMessage(FADEDOWN);
                            break;
                        case AudioManager.AUDIOFOCUS_GAIN:
                            //bug 1223701 set call audio mode if the audio mode was MODE_IN_CALL
                            if (mPlayModeCallBack.isCallMode())
                                //bug 1150763 the audio mode was MODE_IN_CALL and get audio focus aganin,set call audio mode
                                Utils.setPlayInReceiveMode(mContext, true);
                            if (mIsGetAudioFocus == false) {
                                mIsGetAudioFocus = true;
                                startPlayback();
                            }
                            if (mPauseByFocusCanDuck) {
                                mPauseByFocusCanDuck = false;
                                mHandler.removeMessages(FADEDOWN);
                                mHandler.sendEmptyMessage(FADEUP);
                            }
                            break;
                    }
                }
            };

    public int startPlayback() {
        Log.d(TAG, "startPlayback");
        if (mIsPrepared) {
            mIsGetAudioFocus = requestAudioFocus();
            if (!mIsGetAudioFocus) {
                //bug 1180685 time to accumulate when calling
                mToast = Utils.showToastWithText(mContext, mToast, mContext.getString(R.string.no_allow_play_calling), Toast.LENGTH_SHORT);
                return -1;
            }
            start();
            mIsPaused = false;
            mIsPlaybackComp = false;
            if (mPlayerListener != null) {
                mPlayerListener.onStarted();
            }
            return 0;
        }
        return -1;
    }

    private void pauseByAudioFocusLoss() {
        pausePlayback();
        if (mPlayerListener != null) {
            mPlayerListener.onPausedByAudioFocusLoss();
        }
    }

    public void pausePlayback() {
        pause();
        mIsPaused = true;
    }

    @Override
    public boolean isPlaying() {
        if (mIsPrepared) {
            return super.isPlaying();
        } else {
            return false;
        }
    }

    public boolean isPaused() {
        return mIsPaused;
    }

    public boolean isPlaybackComp() {
        return mIsPlaybackComp;
    }

    public void stopPlayback() {
        Log.d(TAG, "stopPlayback,release all");
        mHandler.removeCallbacksAndMessages(null);
        stop();
        mIsPaused = false;
        mIsPrepared = false;
        mIsPlaybackComp = false;
        mAudioManager.abandonAudioFocus(mAudioFocusListener);
        mPlayerListener = null;
        release();
    }

    public RecorderItem getPlayData() {
        return mData;
    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        if (mPlayerListener != null) {
            mIsPlaybackComp = true;
            mPlayerListener.onCompleted();
        }
    }

    @Override
    public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
        Log.d(TAG, "onError:" + i);
        if (mPlayerListener != null) {
            mPlayerListener.onError();
        }
        return false;
    }

    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        Log.d(TAG, "onPrepared");
        mIsPrepared = true;
        mDuration = super.getDuration();
        Log.d(TAG, "duration:" + mDuration);
        startPlayback();
    }

    @Override
    public int getDuration() {
        return mDuration;
    }

    public void setPlayerListener(PlayerListener playerListener) {
        mPlayerListener = playerListener;
    }

    public boolean isPrepared() {
        return mIsPrepared;
    }


    public interface PlayerListener {
        void onStarted();

        void onCompleted();

        void onError();

        void onPausedByAudioFocusLoss();
    }

    public interface PlayModeCallBack{
        boolean isCallMode();
    }

    public void setmPlayerModeCallBack(PlayModeCallBack playModeCallBack) {
        this.mPlayModeCallBack = playModeCallBack;
    }
}
