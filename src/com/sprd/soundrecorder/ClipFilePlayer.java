package com.sprd.soundrecorder;

import android.app.Activity;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.widget.Toast;

import com.android.soundrecorder.R;

import java.io.IOException;

public class ClipFilePlayer extends MediaPlayer implements OnPreparedListener,
        OnErrorListener, OnCompletionListener {

    public static String TAG = "ClipFilePlayer";
    private RecordingFileClip mActivity;
    public boolean mIsPrepared = false;
    public int mDuration;
    @Override
    public void onPrepared(MediaPlayer mp) {
        mIsPrepared = true;
        mDuration = getDuration();
        mActivity.start();
        mActivity.updatePlayPause();
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        mIsPrepared = false;
        mActivity.onCompletion();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Toast.makeText(mActivity, R.string.playback_failed, Toast.LENGTH_SHORT).show();
        return false;
    }

    public void seekProgress(int progress){
        seekTo(progress);
    }

    //bug 1209101 coverity scan:Unchecked/unconfirmed cast from android.app.Activity to com.sprd.soundrecorder.RecordingFileClip
    public void setActivity(RecordingFileClip activity) {
        mActivity = activity;
        setOnPreparedListener(this);
        setOnErrorListener(this);
        setOnCompletionListener(this);
    }

    boolean isPrepared() {
        return mIsPrepared;
    }

    public void setDataSourceAndPrepare(Uri uri)
            throws IllegalArgumentException, SecurityException,
            IllegalStateException, IOException {
        setDataSource(mActivity, uri);
        prepareAsync();
    }
}
