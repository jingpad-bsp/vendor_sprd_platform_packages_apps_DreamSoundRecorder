package com.sprd.soundrecorder.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.media.session.MediaSessionManager;
import android.view.KeyEvent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;

import com.android.soundrecorder.R;
import com.sprd.soundrecorder.MultiChooseActivity;
import com.sprd.soundrecorder.RecordFilePlayActivity;
import com.sprd.soundrecorder.RecordListActivity;
import com.sprd.soundrecorder.RecordPreviewPlayer;
import com.sprd.soundrecorder.Utils;
import com.sprd.soundrecorder.data.DataOpration;
import com.sprd.soundrecorder.data.RecordListAdapter;
import com.sprd.soundrecorder.data.RecorderItem;
import com.sprd.soundrecorder.data.ViewHolder;
import com.sprd.soundrecorder.data.WaveDataManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;


/**
 * Created by jian.xu on 2017/10/11.
 */

public class RecordItemFragment extends Fragment {

    private static final String TAG = RecordItemFragment.class.getSimpleName();
    private static final String isRecordType = "recordtype";
    public enum RecordType {
        SOUND_RECORD_ITEM,
        CALL_RECORD_ITEM,
        INVALID_TYPE
    }

    private RecordType mRecordType = RecordType.SOUND_RECORD_ITEM;

    private TextView mEmptyListView;
    private ListView mListView;
    private Context mContext;
    private Handler mMainHandler;
    private AudioContentObserver mAudioObserver;
    private MyAdapter<RecorderItem> mAdapter;
    private RecordPreviewPlayer mPreviewPlayer;
    private MyViewHolder mPlayItemViewHolder;
    private SparseArray<Integer> mPlayTagHashMap = null;
    private int mTotalWaveSize;
    private MediaSessionManager mMediaSessionManager;
    private boolean mDown = false;
    private long mLastClickTime = 0;
    public static final String ACTION_HEADSET_PAUSE = "com.android.soundrecorder.HEADSET.PAUSE";

    public static boolean Ismulclick = false;
    private static final int UPDATE_PLAY_POSITION = 1;


    public RecordPreviewPlayer getmPreviewPlayer() {
        return mPreviewPlayer;
    }


    public RecordItemFragment() {
        super();
    }

    public void setRecordType(RecordType type) {
        mRecordType = type;
    }

    @Override
    public void onAttach(Context context) {
        Log.d(TAG, "onAttach start");
        super.onAttach(context);
        mContext = context;
        mMediaSessionManager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_HEADSET_PAUSE);
        mContext.registerReceiver(mHeadsetReceiver, filter);
        /* bug 1109103 refresh the interface when SD or otg is eject or mounted @{ */
        IntentFilter intentSrorageFilter = new IntentFilter();
        intentSrorageFilter.addAction(Intent.ACTION_MEDIA_EJECT);
        intentSrorageFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        intentSrorageFilter.addDataScheme("file");
        mContext.registerReceiver(mStorageReceiver, intentSrorageFilter);
        /* }@ */
    }

    @Override
    public void onDetach() {
        super.onDetach();
        clearPlayer();
        mContext.unregisterReceiver(mHeadsetReceiver);
        mContext.unregisterReceiver(mStorageReceiver);
    }

    private MediaSessionManager.OnMediaKeyListener mMediaKeyListener =
        new MediaSessionManager.OnMediaKeyListener() {
                @Override
                 public boolean onMediaKey(KeyEvent event) {
                        int keycode = event.getKeyCode();
                        int action = event.getAction();
                        long eventtime = event.getEventTime();
                        if ((keycode == KeyEvent.KEYCODE_HEADSETHOOK)
                                || (keycode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {
                            if (action == KeyEvent.ACTION_DOWN) {
                                if (mDown) {
                                } else if (event.getRepeatCount() == 0) {
                                    // only consider the first event in a sequence, not the repeat events,
                                    // so that we don't trigger in cases where the first event went to
                                    // a different app (e.g. when the user ends a phone call by
                                    // long pressing the headset button)
                                    // The service may or may not be running, but we need to send it
                                    // a command.
                                    if (eventtime - mLastClickTime < 300) {

                                    } else {
                                        // single click mute/unmute
                                        if (mRecordType == RecordType.SOUND_RECORD_ITEM) {
                                            Intent fmIntent = new Intent(ACTION_HEADSET_PAUSE);
                                            fmIntent.putExtra("recorder_type","0");
                                            mContext.sendBroadcast(fmIntent);
                                        }else if (mRecordType == RecordType.CALL_RECORD_ITEM) {
                                            Intent fmIntent = new Intent(ACTION_HEADSET_PAUSE);
                                            fmIntent.putExtra("recorder_type","1");
                                            mContext.sendBroadcast(fmIntent);
                                        }
                                        Log.d(TAG,"send headset pause");
                                        mLastClickTime = eventtime;
                                    }

                                    mDown = true;
                                }
                            } else {
                                mDown = false;
                            }
                        }
//                    }
                     return true;
                }
            };

    @Override
    public void onResume() {
        super.onResume();
        queryFileAysnc();
    }
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(isRecordType, mRecordType == RecordType.SOUND_RECORD_ITEM ? false : true);
    }
    @Override
    public void onViewStateRestored(Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState!=null){
            boolean iscall = savedInstanceState.getBoolean(isRecordType);
            if (iscall){
               mRecordType = RecordType.CALL_RECORD_ITEM;
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        //bug 1122231 continue to play when turn off the screen
       if (!Utils.isTopActivity(((RecordListActivity) getActivity()), ((RecordListActivity) getActivity()).getLocalClassName())) {
            if (mPreviewPlayer != null && mPreviewPlayer.isPlaying()) {
                Toast.makeText(mContext, R.string.background_speak_close, Toast.LENGTH_SHORT).show();
            }
            clearPlayer();
       }
    }

    public void pausePlayback() {
        if (mPreviewPlayer != null && mPreviewPlayer.isPlaying()) {
            mPreviewPlayer.pausePlayback();
            updatePlayViews();
            ((RecordListActivity) getActivity()).updateReceiverModeMenuIcon();
        }
    }
    public boolean isPauseStatus() {
        if (mPreviewPlayer != null && mPreviewPlayer.isPaused()) {
            return true;
        }
        return false;
    }

    /*bug:985415 the receive mode can be changed when play the record file @{ */
    public boolean isPlayingStatus() {
        if (mPreviewPlayer != null && mPreviewPlayer.isPlaying()) {
            return true;
        }
        return false;
    }
    /*}@*/

    public boolean isExistPreviewPlayer() {
        /*Bug 1385991 Click the back button twice to exit when clicking the recording in the list during the call */
        if (mPreviewPlayer != null && (mPreviewPlayer.isPlaying() || mPreviewPlayer.isPaused())) {
            return true;
        }
        return false;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View artistView = inflater.inflate(R.layout.recording_file_list, container, false);
        mEmptyListView = artistView.findViewById(R.id.emptylist);
        mListView = artistView.findViewById(android.R.id.list);
        mListView.setOnItemClickListener(mItemClickListener);
        mListView.setOnItemLongClickListener(mItemLongClickListener);
        mMainHandler = new Handler(mContext.getMainLooper());
        if (mAdapter == null) {
            mAdapter = new MyAdapter<>(mContext, R.layout.recording_file_item_new);
        }
        mListView.setAdapter(mAdapter);
        mAudioObserver = new AudioContentObserver();
        mAudioObserver.registerObserver();
        return artistView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mAdapter = null;
        mAudioObserver.unregisterObserver();
    }


    public void clearPlayer() {
        if (mPreviewPlayer != null) {
            mPreviewPlayer.setPlayerListener(null);
            mPreviewPlayer.stopPlayback();
            mPreviewPlayer = null;
        }
        if (mMediaSessionManager != null) {
            mMediaSessionManager.setOnMediaKeyListener(null, null);
        }
        ((RecordListActivity) getActivity()).setPlayerPlaying(false);
        mHandler.removeMessages(UPDATE_PLAY_POSITION);
        mPlayTagHashMap = null;
        setReceiveMode(false);
        updatePlayViews();
        ((RecordListActivity) getActivity()).updateReceiverModeMenuIcon();
    }

    private void queryFileAysnc() {
        AsyncTask<Void, Long, ArrayList<RecorderItem>> task =
                new AsyncTask<Void, Long, ArrayList<RecorderItem>>() {

                    @Override
                    protected ArrayList<RecorderItem> doInBackground(Void... params) {
                        return DataOpration.getRecorderData(getContext(),
                                mRecordType == RecordType.SOUND_RECORD_ITEM ? false : true);
                    }

                    @Override
                    protected void onProgressUpdate(Long... values) {

                    }

                    @Override
                    protected void onPostExecute(ArrayList<RecorderItem> data) {
                        if (!isDetached() && mAdapter != null) {
                            Log.d(TAG, "set data:" + data.size());
                            mAdapter.setData(data);
                            if (data.size() > 0) {
                                mEmptyListView.setVisibility(View.GONE);
                            } else {
                                mEmptyListView.setVisibility(View.VISIBLE);
                            }
                        }
                    }
                };
        task.execute((Void[]) null);
    }

    private class MyAdapter<T> extends RecordListAdapter<T> {

        public MyAdapter(Context context, int listItemId) {
            super(context, listItemId);
        }

        @Override
        protected ViewHolder getViewHolder() {
            return new MyViewHolder();
        }

        @Override
        protected void initViewHolder(View listItem, ViewHolder viewHolder) {
            MyViewHolder myViewHolder = (MyViewHolder) viewHolder;
            myViewHolder.mPlayButton = listItem.findViewById(R.id.recode_icon);
            myViewHolder.mPlayPositonView = listItem.findViewById(R.id.current_time);
            myViewHolder.mSeekBar = listItem.findViewById(R.id.progress);
            myViewHolder.mPlayDuration = listItem.findViewById(R.id.total_duration);
            myViewHolder.mTagIcon = listItem.findViewById(R.id.tag_icon);
            myViewHolder.mRecordDuration = listItem.findViewById(R.id.record_duration);
            myViewHolder.mDisplayName = listItem.findViewById(R.id.record_displayname);
            myViewHolder.mRecordDate = listItem.findViewById(R.id.record_time);
            myViewHolder.mPlayViewsLayout = listItem.findViewById(R.id.play_layout);
        }

        @Override
        protected void initListItem(int position, View listItem, ViewGroup parent) {
            final MyViewHolder vh = (MyViewHolder) listItem.getTag();
            final RecorderItem data = (RecorderItem) getItem(position);
            vh.hidePlayViews();
            if (mPreviewPlayer != null && mPreviewPlayer.isPrepared()) {
                if (mPreviewPlayer.getPlayData().getId() == data.getId()) {
                        vh.showPlayViews();
                        if (!mPreviewPlayer.isPlaying()){
                            vh.pausePlayViews();
                        }
                    //should set viewholder again
                    mPlayItemViewHolder = vh;
                    updatePlayViewHolder();
                }
            }
            vh.mDisplayName.setText(data.getDisplayName());
            vh.mRecordDuration.setText(data.getDurationString(mContext));
            vh.mPlayDuration.setText(data.getDurationString(mContext));

            vh.mRecordDate.setText(data.getDateString());
            //bug 1429365 After clearing the storage space, the tag will still be displayed
            if (data.getTagNumber() > 0 && new File(WaveDataManager.
                    getTagDataFileName(RecordItemFragment.this.mContext, String.valueOf(data.getId()))).exists()) {
                vh.mTagIcon.setVisibility(View.VISIBLE);
            } else {
                vh.mTagIcon.setVisibility(View.GONE);
            }
            vh.mPlayButton.setOnClickListener(new PlayButtonClickListener(vh, data));
        }
    }

    public class MyViewHolder extends ViewHolder {
        public LinearLayout mPlayViewsLayout;
        public TextView mDisplayName;
        public ImageView mPlayButton;
        public TextView mPlayPositonView;
        public MarkSeekBar mSeekBar;
        public TextView mPlayDuration;
        public ImageView mTagIcon;
        public TextView mRecordDuration;
        public TextView mRecordDate;

        public void hidePlayViews() {
            mPlayViewsLayout.setVisibility(View.GONE);
            mPlayButton.setImageResource(R.drawable.custom_record_listrecord_btn);
            mPlayButton.setBackgroundResource(R.drawable.custom_record_listrecord_btn);
        }

        public void showPlayViews() {
                mPlayViewsLayout.setVisibility(View.VISIBLE);
                mPlayButton.setImageResource(R.drawable.custom_record_listpause_btn);
                mPlayButton.setBackgroundResource(R.drawable.custom_record_listpause_btn);
        }

        public void pausePlayViews() {
            mPlayButton.setImageResource(R.drawable.custom_record_listrecord_btn);
            mPlayButton.setBackgroundResource(R.drawable.custom_record_listrecord_btn);
        }
    }

    private class PlayButtonClickListener implements View.OnClickListener {

        private MyViewHolder mViewHolder;
        private RecorderItem mData;

        public PlayButtonClickListener(MyViewHolder viewHolder, RecorderItem data) {
            super();
            mViewHolder = viewHolder;
            mData = data;
        }

        @Override
        public void onClick(View view) {
            if (mPreviewPlayer != null && mPreviewPlayer.getPlayData().getId() != mData.getId()) {
                clearPlayer();
            }
            Log.d(TAG, "on Click:" + mData.getId());
            if (!RecordItemFragment.this.isResumed()) {
                Log.d(TAG, "fragment is paused!");
                return;
            }
            if (mPreviewPlayer == null) {
                mPreviewPlayer = new RecordPreviewPlayer(mContext);
                try {
                    mPlayItemViewHolder = mViewHolder;
                    mPreviewPlayer.setPlayerListener(mPlayerLisener);
                    mPreviewPlayer.setDataAndPrepare(mData);
                    mPreviewPlayer.setmPlayerModeCallBack(mPlayerModeCallBack);
                    if ( mMediaSessionManager != null ) {
                        mMediaSessionManager.setOnMediaKeyListener(mMediaKeyListener, null);
                    }
                    ((RecordListActivity) getActivity()).setPlayerPlaying(true);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                ((RecordListActivity) getActivity()).getmReceiverMenu().setIcon(R.drawable.ear_speak);
            } else {
                if (mPreviewPlayer.isPlaying()) {
                    mPreviewPlayer.pausePlayback();
                    updatePlayViews();
                    ((RecordListActivity) getActivity()).setPlayerPlaying(false);
                    ((RecordListActivity) getActivity()).updateReceiverModeMenuIcon();
                } else {
                    mPreviewPlayer.startPlayback();
                    ((RecordListActivity) getActivity()).setPlayerPlaying(true);
                    updatePlayViews();
                    ((RecordListActivity) getActivity()).updateReceiverModeMenuIcon();
                }
            }
        }
    }



    private AdapterView.OnItemClickListener mItemClickListener = new AdapterView.OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
            clearPlayer();
            RecorderItem item = mAdapter.getItem(i);
            Intent intent = new Intent(mContext, RecordFilePlayActivity.class);
            intent.putExtra(RecordFilePlayActivity.PLAY_DATA, item);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        }
    };

    private AdapterView.OnItemLongClickListener mItemLongClickListener = new AdapterView.OnItemLongClickListener() {

        @Override
        public boolean onItemLongClick(AdapterView<?> adapter, View v, int pos, long id) {
            clearPlayer();
            //todo
            Intent intent = null;
            intent = new Intent(mContext, MultiChooseActivity.class);
            intent.putExtra(MultiChooseActivity.QUERY_TYPE, mRecordType);
            intent.putExtra(MultiChooseActivity.FILE_POS, pos);
            Ismulclick = true;
            startActivity(intent);
            return true;
        }
    };

    private class AudioContentObserver extends ContentObserver {

        public AudioContentObserver() {
            super(mMainHandler);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            Log.d(TAG, "AudioContentObserver audio change! reload data,selfchange:" + selfChange);
            queryFileAysnc();
        }

        public void registerObserver() {
            mContext.getContentResolver().registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    false, this);
        }

        public void unregisterObserver() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }
    }

    //bug 1150763 the audio mode was MODE_IN_CALL and get audio focus aganin,set call audio mode
    private RecordPreviewPlayer.PlayModeCallBack mPlayerModeCallBack = new RecordPreviewPlayer.PlayModeCallBack() {
        @Override
        public boolean isCallMode() {
            return ((RecordListActivity) getActivity()).isReceiveModeList();
        }
    };


    private RecordPreviewPlayer.PlayerListener mPlayerLisener = new RecordPreviewPlayer.PlayerListener() {

        @Override
        public void onStarted() {
            updatePlayViewHolder();
            updatePlayViews();
            if (mRecordType == RecordType.SOUND_RECORD_ITEM) {
                ((RecordListActivity) getActivity()).setPlayerPlaying(true, 0);
            } else if (mRecordType == RecordType.CALL_RECORD_ITEM){
                ((RecordListActivity) getActivity()).setPlayerPlaying(true, 1);
            }
            ((RecordListActivity) getActivity()).updateReceiverModeMenuIcon();
        }

        @Override
        public void onCompleted() {
            clearPlayer();
        }

        @Override
        public void onError() {
            clearPlayer();
        }

        @Override
        public void onPausedByAudioFocusLoss() {
            updatePlayViews();
            setReceiveMode(false);
            //BUG:901175 set flag about the state of play according to the recordtype
            if (mRecordType == RecordType.SOUND_RECORD_ITEM) {
                ((RecordListActivity) getActivity()).setPlayerPlaying(false, 0);
            } else if (mRecordType == RecordType.CALL_RECORD_ITEM){
                ((RecordListActivity) getActivity()).setPlayerPlaying(false, 1);
            }
            ((RecordListActivity) getActivity()).updateReceiverModeMenuIcon();
        }
    };

    private void setReceiveMode(boolean enable) {
        ((RecordListActivity) getActivity()).setReceiveMode(enable);
    }

    private void updatePlayViews() {
        if (mPlayItemViewHolder != null) {
            if (mPreviewPlayer != null && mPreviewPlayer.isPlaying()) {
                updatePlayPosition();
                Log.d(TAG, "showPlayViews");
                mPlayItemViewHolder.showPlayViews();

            } else if (mPreviewPlayer != null && mPreviewPlayer.isPaused()) {
                mPlayItemViewHolder.showPlayViews();
                mPlayItemViewHolder.pausePlayViews();
                Log.d(TAG, "pausePlayViews");
            } else {
                mPlayItemViewHolder.hidePlayViews();
                Log.d(TAG, "hidePlayViews");
            }
        }
    }

    private void updatePlayViewHolder() {
        RecorderItem data = mPreviewPlayer.getPlayData();
        int duration = mPreviewPlayer.getDuration();
        mPlayItemViewHolder.mSeekBar.clearAllMarks();
        mPlayItemViewHolder.mSeekBar.setMax(duration);
        if (mPlayTagHashMap == null) {
            queryTagInfoAsync(data.getTitle(), data.getMimeType());
        } else {
            addTagInfoToSeekbar(mPlayTagHashMap, mTotalWaveSize);
        }
        mPlayItemViewHolder.mSeekBar.setOnSeekBarChangeListener(mSeekListener);
    }

    private void addTagInfoToSeekbar(SparseArray<Integer> tagData, int totalSize) {
        int tagSize = tagData.size();
        for (int i = 0; i < tagSize; i++) {//bug626962 the music is ANR
            float mark = (float) tagData.keyAt(i) / (float) totalSize;
            Log.d(TAG, "add tag info :tag:" + tagData.keyAt(i) + "mark:" + mark);
            mPlayItemViewHolder.mSeekBar.addMark(mark);
        }
        mPlayTagHashMap = tagData;
        mTotalWaveSize = totalSize;
    }

    private void queryTagInfoAsync(final String title, final String mimeType) {
        AsyncTask<Void, Void, Void> task =
                new AsyncTask<Void, Void, Void>() {
                    SparseArray<Integer> tagData;
                    ArrayList<Float> waveData;

                    @Override
                    protected Void doInBackground(Void... params) {
                        Log.d(TAG, "get Tag:" + title);
                        tagData = WaveDataManager.getTagData(mContext, title, mimeType);
                        waveData = WaveDataManager.getWaveData(mContext, title, mimeType);
                        return null;
                    }

                    @Override
                    protected void onProgressUpdate(Void... values) {
                    }


                    @Override
                    protected void onPostExecute(Void values) {
                        if (!getActivity().isDestroyed()
                                && mPreviewPlayer != null
                                && mPreviewPlayer.isPrepared()
                                && tagData != null
                                && waveData != null) {
                            Log.d(TAG, "tag data size:" + tagData.size());
                            addTagInfoToSeekbar(tagData, waveData.size());
                        }
                    }

                };
        task.execute((Void[]) null);
    }

    @SuppressLint("StringFormatInvalid")
    private void updatePlayPosition() {
        //bug:955837 update playposition when move the seekbar on pause state
        if (mPreviewPlayer != null && (mPreviewPlayer.isPlaying() || mPreviewPlayer.isPaused())) {
            int currentTime = mPreviewPlayer.getCurrentPosition();
            mPlayItemViewHolder.mSeekBar.setProgress(currentTime);
            int second = Math.round((float) currentTime / 1000);
            if (second != 0) {
                mPlayItemViewHolder.mPlayPositonView.setText(Utils.makeTimeString4MillSec(mContext, currentTime));
            } else {
                mPlayItemViewHolder.mPlayPositonView.setText(
                        String.format(Locale.US,mContext.getResources().getString(R.string.timer_format), 0, 0, 0));
            }
            mHandler.sendEmptyMessageDelayed(UPDATE_PLAY_POSITION, 200);
        }
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UPDATE_PLAY_POSITION:
                    updatePlayPosition();
                    break;
            }
        }
    };

    private SeekBar.OnSeekBarChangeListener mSeekListener = new SeekBar.OnSeekBarChangeListener() {
        public void onStartTrackingTouch(SeekBar bar) {
        }

        public void onProgressChanged(SeekBar bar, int progress, boolean fromuser) {
        //bug:1000024 if mPreviewPlayer is null,return
            if (!fromuser || mPreviewPlayer == null || !mPreviewPlayer.isPrepared()) {
                return;
            }
            //mActivity.updatePlayedDuration(progress);
            mPreviewPlayer.seekTo(progress);
        }

        public void onStopTrackingTouch(SeekBar bar) {

        }
    };

    private BroadcastReceiver mHeadsetReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String recorderType = intent.getStringExtra("recorder_type");
            Log.d(TAG, "mHeadsetReceiver,action:" + action+" recorderType="+recorderType +" mRecordType="+mRecordType);
            if ((mRecordType == RecordType.SOUND_RECORD_ITEM && "0".equals(recorderType)) || (mRecordType == RecordType.CALL_RECORD_ITEM && "1".equals(recorderType))){
                switch (action) {
                    case ACTION_HEADSET_PAUSE:
                        if (mPreviewPlayer == null) {
                            return;
                        }
                        if (mPreviewPlayer != null && mPreviewPlayer.isPlaying()) {
                            mPreviewPlayer.pausePlayback();
                            updatePlayViews();
                            ((RecordListActivity) getActivity()).setPlayerPlaying(false);
                        } else {
                            mPreviewPlayer.startPlayback();
                            updatePlayViews();
                            ((RecordListActivity) getActivity()).setPlayerPlaying(true);
                       }
                       break;
                }
            }
        }
    };

    /* bug 1109103 refresh the interface when SD or otg is eject or mounted @{ */
    private BroadcastReceiver mStorageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive intent = " + intent);
            if (intent == null) {
                return;
            }
            String action = intent.getAction();
            switch (action) {
                case Intent.ACTION_MEDIA_MOUNTED:
                    queryFileAysnc();
                    break;
                case Intent.ACTION_MEDIA_EJECT:
                    String path = intent.getData().getPath();
                    if ( mPreviewPlayer != null &&  mPreviewPlayer.getPlayData().getData().startsWith(path)) {
                        clearPlayer();
                    }
                    queryFileAysnc();
                    break;
            }
        }
    };
    /* }@ */

    public void setOnMediaKeyListener(boolean on) {
        if (on) {
            if ( mMediaSessionManager != null && mPreviewPlayer != null) {
                mMediaSessionManager.setOnMediaKeyListener(mMediaKeyListener, null);
            }
        }else {
            if ( mMediaSessionManager != null ) {
                mMediaSessionManager.setOnMediaKeyListener(null, null);
            }
       }
   }

}
