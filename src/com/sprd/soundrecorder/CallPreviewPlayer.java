package com.sprd.soundrecorder;

import java.io.IOException;

import com.android.soundrecorder.R;

import android.media.MediaPlayer.OnPreparedListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.View;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Toast;
import android.content.Context;
public class CallPreviewPlayer extends MediaPlayer implements OnPreparedListener,
        OnErrorListener, OnCompletionListener {

    public static String TAG = "PreviewPlayer";
    private CallRecordFragment mActivity;
    public boolean mIsPrepared = false;
    public MarkSeekBar mSeekBar;
    public int mDuration;
    private Context mListContext;
    public boolean mSeeking = false;
    @Override
    public void onPrepared(MediaPlayer mp) {
        mIsPrepared = true;
        mSeekBar.setOnSeekBarChangeListener(mSeekListener);
        mActivity.start();
        mDuration = getDuration();
        addMarkInfo();//bug 613017 Play the file icon will appear on another file or will not appear play
        mActivity.updatePlayPause();
    }
    //bug 613017 Play the file icon will appear on another file or will not appear play
    private void addMarkInfo(){
        if (mDuration != 0) {
            mSeekBar.setMax(mDuration);
            mSeekBar.setDuration(mDuration);
            int tagSize = mActivity.mTagHashMap.size();
            for (int i = 0; i < tagSize; i++) {//bug626962 the music is ANR
                int key = mActivity.mTagHashMap.keyAt(i);
                float mark = (float)mActivity.mTagHashMap.keyAt(i)/mActivity.mTagHashMap.keyAt(tagSize-1);
                mSeekBar.addMark((int)(mDuration*mark));
            }
            mSeekBar.setVisibility(View.VISIBLE);
            if (!mSeeking) {
                mSeekBar.setProgress(getCurrentPosition());
            }
        }
    }
    //bug 1209101 coverity scan:Unchecked/unconfirmed cast from android.widget.SeekBar to com.sprd.soundrecorder.MarkSeekBar
    public void setScrolSeekBar(MarkSeekBar seekBar){
        mSeekBar = seekBar;
        mSeekBar.setOnSeekBarChangeListener(mSeekListener);
        mDuration = getDuration();
        addMarkInfo();
    }
    //bug 613017 end
    @Override
    public void onCompletion(MediaPlayer mp) {
        mIsPrepared = false;
        /** SPRD:Bug 618322 file play fail the sound recorder crash( @{ */
        if (null != mSeekBar){
            mSeekBar.setProgress(mDuration);
            mSeekBar.clearAllMarks();
        }
        /** @} */
        mActivity.onCompletion();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Toast.makeText(mListContext, R.string.playback_failed, Toast.LENGTH_SHORT).show();
        return false;
    }

    public void setUISeekBar(MarkSeekBar seekBar){
        mSeekBar = seekBar;
    }

    public void setDuration(int duration){
        mSeekBar.setDuration(duration);
    }

    public void setActivity(Context context,CallRecordFragment RecordFragment ) {
        mListContext = context;
        mActivity = RecordFragment;
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
        setDataSource(mListContext, uri);
        prepareAsync();
    }

    private OnSeekBarChangeListener mSeekListener = new OnSeekBarChangeListener() {
        public void onStartTrackingTouch(SeekBar bar) {
            mSeeking = true;
        }
        public void onProgressChanged(SeekBar bar, int progress, boolean fromuser) {
            if (!fromuser || !mIsPrepared) {
                return;
            }
            mActivity.updatePlayedDuration(progress);
            seekTo(progress);
        }
        public void onStopTrackingTouch(SeekBar bar) {
            mSeeking = false;
        }
    };

}
