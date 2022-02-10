package com.sprd.soundrecorder;


import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.telephony.TelephonyManager;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.SparseArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.soundrecorder.R;
import com.android.soundrecorder.Recorder;
import com.android.soundrecorder.SoundRecorder;
import com.sprd.soundrecorder.data.WaveDataManager;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LocalRecordFragment extends Fragment implements AdapterView.OnItemClickListener {
    private static final String TAG = "LocalRecordFragment";
    private TextView remindTextView = null;
    private ListView mListView;
    private Context mContext;
    private CursorRecorderAdapter mAdapter;
    //private String mLocalFileWhere;
    private LocalRecordFragment mFragment;
    private List<RecorderItem> mList = new ArrayList<RecorderItem>();
    private boolean mIsPause = false;
    public LocalPreviewPlayer mPlayer = null;
    private Handler mProgressRefresher = new Handler();
    private LinearLayout mPlayLayout = null;
    private ImageView mCurPlayButton = null;
    private MarkSeekBar mCurSeekBar = null;
    private ImageView mCurTagIcon = null;
    private TextView mCurRecordDuration = null;
    private TextView mCurTime = null;
    private TextView mCurTotalDuration = null;
    private int mLastClickPos = -1;
    public SparseArray<Integer> mTagHashMap;
    private AudioManager mAudioManager;
    private static final int FADEDOWN = 2;
    private static final int FADEUP = 3;
    private List<Integer> mRecordIDList = new ArrayList<Integer>();
    private View mPlayingItem = null;
    private boolean mPausedByTransientLossOfFocus;
    private boolean mPlayFromFocusCanDuck = false;

    public LocalRecordFragment() {
        super();
    }

    //private OnItemClickListener ItemClickListener = new OnItemClickListener() {

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
        if (mPlayer != null) {
            stopPlayback();
        }
        RecorderItem item = mAdapter.findItem(pos);
        Intent intent = new Intent(mContext, RecordingFilePlay.class);
        intent.putExtra("mime_type", item.mimeType);
        intent.putExtra("path", item.data);
        intent.putExtra("title", item.display_name);//bug 745240 the player interface fiel name show error
        intent.putExtra("id", item.id);
        intent.putExtra("duration", item.duration);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    //};
    private OnItemLongClickListener ItemLongClickListener = new OnItemLongClickListener() {

        @Override
        public boolean onItemLongClick(AdapterView<?> adapter, View v, int pos, long id) {
          /*
          if (mPlayer != null) {
                stopPlayback();
            }
            if (mAdapter != null) {
                Intent intent = null;
                intent = new Intent(mContext, MultiChoseActivity.class);
                intent.putExtra(MultiChoseActivity.QUERY_WHERE, mLocalFileWhere);
                intent.putExtra(MultiChoseActivity.FILE_POS, pos);
                startActivity(intent);
            } else {
                return false;
            }*/
            return true;
        }
    };


    @Override
    public void onAttach(Context mContext) {
        Log.d(TAG, "onAttach start");
        super.onAttach(mContext);
        this.mContext = mContext;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        //this.mContext = null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View artistView = inflater.inflate(R.layout.recording_file_list, container, false);
        remindTextView = (TextView) artistView.findViewById(R.id.emptylist);
        mListView = (ListView) artistView.findViewById(android.R.id.list);
        mListView.setOnItemClickListener(this);
        mListView.setOnItemLongClickListener(ItemLongClickListener);
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        registerReceiver();
        return artistView;
    }

    @Override
    public void onStart() {
        super.onStart();
    }
    private void detectionList() {//bug 753474 the record file list is not empty pop
        if (mAdapter.getCount() < 1) {
            remindTextView.setVisibility(View.VISIBLE);
        } else {
            remindTextView.setVisibility(View.GONE);
        }
    }
    private void queryFileAysnc() {
        AsyncTask<Void, Long, Void> task = new AsyncTask<Void, Long, Void>() {

            @Override
            protected Void doInBackground(Void... params) {
                mList = query();
                return null;
            }

            @Override
            protected void onProgressUpdate(Long... values) {

            }

            @Override
            protected void onPostExecute(Void result) {
                if (!mIsPause) {
                    Collections.reverse(mList);
                    mAdapter = new CursorRecorderAdapter(mList, true);
                    mListView.setAdapter(mAdapter);
                    detectionList();
                }
            }
        };
        task.execute((Void[]) null);
    }

    private ArrayList<RecorderItem> query() {
        final int INIT_SIZE = 10;
        ArrayList<RecorderItem> result =
                new ArrayList<RecorderItem>(INIT_SIZE);
        Cursor cur = null;
        try {
            StringBuilder where = new StringBuilder();
            /* SPRD: fix bug 513105 @{ */
            SharedPreferences systemVersionShare = mContext.getSharedPreferences(RecordingFileListTabUtils.SOUNDREOCRD_TYPE_AND_DTA, RecordingFileListTabUtils.MODE_PRIVATE);
            String systemVersion = systemVersionShare.getString(RecordingFileListTabUtils.SYSTEM_VERSION, "0");
            Log.d(TAG, "query(): systemVersion=" + systemVersion + ", currentVersion=" + android.os.Build.VERSION.RELEASE);
            if (systemVersion.equals("0")) {
                /* SPRD: fix bug 521597 @{ */
                File pathDir = null;
                File otgPathDir = null;
                String defaultExternalPath = "0";
                String defaultInternalPath = "0";
                String defaultOtgPath = "0";
                if (StorageInfos.isExternalStorageMounted()) {
                    pathDir = StorageInfos.getExternalStorageDirectory();
                    if (pathDir != null)
                        defaultExternalPath = pathDir.getPath() + Recorder.DEFAULT_STORE_SUBDIR;
                }
                pathDir = StorageInfos.getInternalStorageDirectory();
                if (pathDir != null)
                    defaultInternalPath = pathDir.getPath() + Recorder.DEFAULT_STORE_SUBDIR;
                //bug 663417 when connect the otg u volume the first entry filelist is empty
                otgPathDir = StorageInfos.getUSBStorage();
                if (otgPathDir != null) {
                    defaultOtgPath = otgPathDir.getPath() + Recorder.DEFAULT_STORE_SUBDIR;
                }

                Log.d(TAG, "query(): defaultExternalPath=" + defaultExternalPath + ", defaultInternalPath=" + defaultInternalPath + " defaultOtgPath=" + defaultOtgPath);

                where.append("(")
                        .append(MediaStore.Audio.Media.MIME_TYPE)
                        .append("='")
                        .append(SoundRecorder.AUDIO_AMR)
                        .append("' or ")
                        .append(MediaStore.Audio.Media.MIME_TYPE)
                        .append("='")
                        .append(SoundRecorder.AUDIO_3GPP)
                        .append("' or ")
                        .append(MediaStore.Audio.Media.MIME_TYPE)
                        .append("='")
                        .append(SoundRecorder.AUDIO_MP4)
                        .append("') and (")
                        .append(MediaStore.Audio.Media.DATA)
                        .append(" like '")
                        .append(defaultExternalPath)
                        .append("%' or ")
                        .append(MediaStore.Audio.Media.DATA)
                        .append(" like '")
                        .append(defaultInternalPath)
                        .append("%' or ")
                        .append(MediaStore.Audio.Media.DATA)
                        .append(" like '")
                        .append(defaultOtgPath)
                        .append("%')");
                /* @} */
            } else {
                where.append(MediaStore.Audio.Media.COMPOSER)
                        .append("='")
                        .append(SoundRecorder.COMPOSER)
                        .append("'");
            }
            /* @} */

            cur = mContext.getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[]{
                            RecorderItem._ID,
                            RecorderItem._DATA,
                            RecorderItem.SIZE,
                            RecorderItem.TITLE,
                            RecorderItem.DISPLAY_NAME,
                            RecorderItem.MOD_DATE,
                            RecorderItem.MIME_TYPE,
                            RecorderItem.DU_STRING,
                            RecorderItem.TAG_NUMBER},
                    where.toString(), null, null);
            //mLocalFileWhere = where.toString();
            // read cursor
            int index = -1;
            for (cur.moveToFirst(); !cur.isAfterLast(); cur.moveToNext()) {
                index = cur.getColumnIndex(RecorderItem._ID);
                // create recorder object
                long id = cur.getLong(index);
                RecorderItem item = new RecorderItem(id);
                // set "data" value
                index = cur.getColumnIndex(RecorderItem._DATA);
                item.data = cur.getString(index);
                // set "size" value
                index = cur.getColumnIndex(RecorderItem.SIZE);
                item.size = cur.getLong(index);
                // set "title" value
                index = cur.getColumnIndex(RecorderItem.TITLE);
                item.title = cur.getString(index);
                // SET "display name" value
                index = cur.getColumnIndex(RecorderItem.DISPLAY_NAME);
                item.display_name = cur.getString(index);
                // set "time" value
                index = cur.getColumnIndex(RecorderItem.MOD_DATE);
                item.time = cur.getLong(index);
                // set "mime-type" value
                index = cur.getColumnIndex(RecorderItem.MIME_TYPE);
                item.mimeType = cur.getString(index);
                // add to mData
                index = cur.getColumnIndex(RecorderItem.DU_STRING);
                item.duration = cur.getInt(index);
                Log.w("mytest", "duration:" + item.duration);
                index = cur.getColumnIndex(RecorderItem.TAG_NUMBER);
                item.tagNumber = cur.getInt(index);
                /* SPRD: fix bug 513105 @{ */
                Log.d(TAG, "query(): item is " + item.toString());
                if (!item.data.endsWith(".3gpp") && item.mimeType.equals(SoundRecorder.AUDIO_MP4))
                    continue;
                result.add(item);
                mRecordIDList.add(cur.getInt(0));
                /* @} */
            }
            if (systemVersion.equals("0") && mRecordIDList.size() > 0) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        ContentValues cv = new ContentValues();
                        cv.put(MediaStore.Audio.Media.COMPOSER, SoundRecorder.COMPOSER);
                        ContentResolver resolver = mContext.getContentResolver();
                        for (int i = 0; i < mRecordIDList.size(); i++) {
                            Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mRecordIDList.get(i));
                            Log.d(TAG, "query(): update COMPOSER to MediaStore, id=" + mRecordIDList.get(i));
                            resolver.update(uri, cv, null, null);
                        }
                    }
                }).start();
            }
            /* SPRD: fix bug 513105 @{ */
            if (!systemVersion.equals(android.os.Build.VERSION.RELEASE)) {
                SharedPreferences.Editor edit = systemVersionShare.edit();
                edit.putString(RecordingFileListTabUtils.SYSTEM_VERSION, android.os.Build.VERSION.RELEASE);
                edit.commit();
            }
            /* @} */
        } catch (Exception e) {
            Log.v(TAG, "RecordingFileListTabUtils.CursorRecorderAdapter failed; E: " + e);
        } finally {
            //Unisoc Bug 1132818 :Variable 'cur' going out of scope leaks the resource it refers to.
            if (cur != null) cur.close();
        }
        return result;
    }

    private Handler mPlayerHandler = new Handler() {
        float mCurrentVolume = 1.0f;

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Log.d(TAG, "handleMessage msg.what = " + msg.what);
            switch (msg.what) {
                case FADEDOWN:
                    mCurrentVolume -= .05f;
                    if (mCurrentVolume > .2f) {
                        mPlayerHandler.sendEmptyMessageDelayed(FADEDOWN, 10);
                    } else {
                        mCurrentVolume = .2f;
                    }
                    mPlayer.setVolume(mCurrentVolume, mCurrentVolume);
                    break;
                case FADEUP:
                    mCurrentVolume += .01f;
                    if (mCurrentVolume < 1.0f) {
                        mPlayerHandler.sendEmptyMessageDelayed(FADEUP, 10);
                    } else {
                        mCurrentVolume = 1.0f;
                    }
                    mPlayer.setVolume(mCurrentVolume, mCurrentVolume);
                    break;
            }
        }
    };
    private void changeTheReceive(){
        AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
       if (RecordingFileListTabUtils.isReceive&&!am.isBluetoothA2dpOn()){
            am.setMode(AudioManager.MODE_IN_CALL);
            am.setSpeakerphoneOn(false);
        }else{
            am.setMode(AudioManager.MODE_NORMAL);
            am.setSpeakerphoneOn(true);
        }
    }
    private void changeTheSpeaker(){
        AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        am.setMode(AudioManager.MODE_NORMAL);
        am.setSpeakerphoneOn(true);
    }
    private AudioManager.OnAudioFocusChangeListener mAudioFocusListener = new AudioManager.OnAudioFocusChangeListener() {
        public void onAudioFocusChange(int focusChange) {
            Log.d(TAG, "onAudioFocusChange focusChange = " + focusChange);
            if (mPlayer == null) {
                mAudioManager.abandonAudioFocus(this);
                return;
            }
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_LOSS:
                    mPausedByTransientLossOfFocus = false;
                    mPlayer.pause();
                    changeTheSpeaker();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    if (mPlayer.isPlaying()) {
                        mPausedByTransientLossOfFocus = true;
                        mPlayer.pause();
                        if (!isCallStatus()){//bug 766719
                            changeTheSpeaker();
                        }
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    mPlayFromFocusCanDuck = true;
                    mPlayerHandler.removeMessages(FADEUP);
                    mPlayerHandler.sendEmptyMessage(FADEDOWN);
                    break;
                case AudioManager.AUDIOFOCUS_GAIN:
                    if (mPausedByTransientLossOfFocus) {
                        mPausedByTransientLossOfFocus = false;
                        start();
                    }
                    if (mPlayFromFocusCanDuck) {
                        mPlayFromFocusCanDuck = false;
                        mPlayerHandler.removeMessages(FADEDOWN);
                        mPlayerHandler.sendEmptyMessage(FADEUP);
                    }
                    break;
            }
            updatePlayPause();
        }
    };

    @SuppressLint("StringFormatInvalid")
    public void updatePlayedDuration(int currentTime) {
        int second = Math.round((float) currentTime / 1000);
        if (second != 0) {
            mCurTime.setText(Utils.makeTimeString4MillSec(mContext, currentTime));
        } else {
            mCurTime.setText(String.format(mContext.getResources().getString(R.string.timer_format), 0, 0, 0));
        }
    }

    public void updatePlayPause() {
        ImageView b = mCurPlayButton;
        if (b != null && mPlayer != null) {
            if (mPlayer.isPlaying()) {
                b.setImageResource(R.drawable.custom_record_listpause_btn);
                b.setBackgroundResource(R.drawable.custom_record_listpause_btn);
                mPlayLayout.setContentDescription(mContext.getResources().getString(R.string.pause));
            } else {
                b.setImageResource(R.drawable.custom_record_listrecord_btn);
                b.setBackgroundResource(R.drawable.custom_record_listrecord_btn);
                mPlayLayout.setContentDescription(mContext.getResources().getString(R.string.resume_play));
                mProgressRefresher.removeCallbacksAndMessages(null);
            }
        }
    }

    public void updatePlayStop() {
        mCurSeekBar.setProgress(0);
        mCurSeekBar.setVisibility(View.GONE);
        mCurTagIcon.setVisibility(View.VISIBLE);
        mCurRecordDuration.setVisibility(View.VISIBLE);
        mCurTotalDuration.setVisibility(View.GONE);
        mCurTime.setVisibility(View.GONE);
        mCurPlayButton.setImageResource(R.drawable.custom_record_listrecord_btn);
        mCurPlayButton.setBackgroundResource(R.drawable.custom_record_listrecord_btn);
        mPlayLayout.setContentDescription(mContext.getResources().getString(R.string.start_play));
        if (mPlayer.isPrepared()) {
            mCurRecordDuration.setText(Utils.makeTimeString4MillSec(mContext, mPlayer.mDuration));
        }
        mProgressRefresher.removeCallbacksAndMessages(null);
    }

    public void start() {
        if (!mPlayer.isPrepared()) {//bug 665106 the big recording file is play error
            return;
        }
        if (!requestAudioFocus()) {
            Toast.makeText(mContext, R.string.no_allow_play_calling, Toast.LENGTH_SHORT).show();
            return;
        }
        changeTheReceive();
        mPlayer.start();
        if (mProgressRefresher != null) {
            mProgressRefresher.removeCallbacksAndMessages(null);
            mProgressRefresher.post(new LocalRecordFragment.ProgressRefresher());
        }
    }

    class ProgressRefresher implements Runnable {
        @Override
        public void run() {
            if (mPlayer != null && !mPlayer.mSeeking && mPlayer.mDuration != 0) {
                int currentTime = mPlayer.getCurrentPosition();
                mPlayer.mSeekBar.setDuration(mPlayer.mDuration);
                mPlayer.mSeekBar.setProgress(currentTime);
                updatePlayedDuration(currentTime);
            }
            mProgressRefresher.removeCallbacksAndMessages(null);
            if (mPlayer != null) {
                mProgressRefresher.postDelayed(new ProgressRefresher(), 200);
            }
        }
    }

    private boolean requestAudioFocus() {
        int audioFocus = mAudioManager.requestAudioFocus(mAudioFocusListener, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        if (audioFocus == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return true;
        }
        return false;
    }

    public class CursorRecorderAdapter extends BaseAdapter {
        private List<RecorderItem> mData = new ArrayList<RecorderItem>();
        private boolean hiddenFlag = true;

        CursorRecorderAdapter(List<RecorderItem> data, boolean hiddenFlag) {
            super();
            this.mData.clear();
            this.mData.addAll(data);
        }

        @Override
        public int getCount() {
            synchronized (this) {
                return mData.size();
            }
        }

        @Override
        public Object getItem(int pos) {
            synchronized (this) {
                return mData.get(pos);
            }
        }

        @Override
        public long getItemId(int pos) {
            long result = -1L;
            RecorderItem item = findItem(pos);
            if (item != null) result = item.id;
            return result;
        }

        private Uri mPlayingUri;

        private void getItemViews(View v) {
            mPlayingItem = v;
            mPlayLayout = (LinearLayout) mPlayingItem.findViewById(R.id.record_start);
            mCurPlayButton = (ImageView) mPlayingItem.findViewById(R.id.recode_icon);
            mCurTime = (TextView) mPlayingItem.findViewById(R.id.current_time);
            mCurSeekBar = (MarkSeekBar) mPlayingItem.findViewById(R.id.progress);
            mCurTotalDuration = (TextView) mPlayingItem.findViewById(R.id.total_duration);
            mCurTagIcon = (ImageView) mPlayingItem.findViewById(R.id.tag_icon);
            mCurRecordDuration = (TextView) mPlayingItem.findViewById(R.id.record_duration);
        }

        public void updatePlayItemInvisible(View v) {
            MarkSeekBar seekbar = (MarkSeekBar) v
                    .findViewById(R.id.progress);
            ImageView tagIcon = (ImageView) v
                    .findViewById(R.id.tag_icon);
            ImageView playButton = (ImageView) v
                    .findViewById(R.id.recode_icon);
            TextView CurTime = (TextView) v
                    .findViewById(R.id.current_time);
            TextView CurTotalDuration = (TextView) v
                .findViewById(R.id.total_duration);
            TextView  CurRecordDuration = (TextView) v
                .findViewById(R.id.record_duration);
            seekbar.setVisibility(View.GONE);
            CurTime.setVisibility(View.GONE);
            CurTotalDuration.setVisibility(View.GONE);
            CurRecordDuration.setVisibility(View.VISIBLE);
            tagIcon.setVisibility(View.VISIBLE);
            playButton.setImageResource(R.drawable.audio_play_image);
        }

        public void updatePlayItemVisible() {
            mCurSeekBar.setVisibility(View.VISIBLE);
            mCurTime.setVisibility(View.VISIBLE);
            mCurTotalDuration.setVisibility(View.VISIBLE);
            mCurRecordDuration.setVisibility(View.GONE);//bug 757593 the record file list is not show duration
            mCurTagIcon.setVisibility(View.GONE);
            mPlayer.setScrolSeekBar(mCurSeekBar);
            if (mPlayer != null && mPlayer.isPlaying()) {
                mCurPlayButton.setImageResource(R.drawable.custom_record_listpause_btn);
                mCurPlayButton.setBackgroundResource(R.drawable.custom_record_listpause_btn);
            } else {
                mCurPlayButton.setImageResource(R.drawable.custom_record_listrecord_btn);
                mCurPlayButton.setBackgroundResource(R.drawable.custom_record_listrecord_btn);
            }
            if (mProgressRefresher != null) {
                mProgressRefresher.removeCallbacksAndMessages(null);
                mProgressRefresher.post(new ProgressRefresher());
            }
        }

        @Override
        public View getView(final int pos, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater flater =
                        (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = flater.inflate(R.layout.recording_file_item, null);
                if (convertView == null)
                    throw new RuntimeException("inflater \"record_item.xml\" failed; pos == " + pos);
            }
            updatePlayItemInvisible(convertView);
            RecorderItem item = findItem(pos);
            if (item == null) throw new RuntimeException("findItem() failed; pos == " + pos);
            Uri nowUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, item.id);
            if (mPlayer != null && nowUri.equals(mPlayingUri)) {
                Log.d(TAG, "Now playing is " + mPlayingUri);
                getItemViews(convertView);
                updatePlayItemVisible();
            }
            TextView tv = null;
            tv = (TextView) convertView.findViewById(R.id.record_displayname);
            tv.setText(item.display_name);
            tv = (TextView) convertView.findViewById(R.id.record_duration);
            tv.setText(Utils.makeTimeString4MillSec(mContext, item.duration));
            tv = (TextView) convertView.findViewById(R.id.total_duration);
            tv.setText(Utils.makeTimeString4MillSec(mContext, item.duration));
            tv = (TextView) convertView.findViewById(R.id.record_time);
            tv.setText(item.getDate());
            ImageView iv = (ImageView) convertView.findViewById(R.id.tag_icon);
            if (item.tagNumber > 0) {
                iv.setBackgroundResource(R.drawable.tag_list);
            } else {
                iv.setBackgroundResource(R.color.transparent);
            }
            LinearLayout lay = (LinearLayout) convertView.findViewById(R.id.record_start);
            lay.setContentDescription(getResources().getString(R.string.start_play));
            lay.setTag(pos);
            lay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    View parent = (View) v.getParent();
                    if (pos != mLastClickPos && mPlayer != null) {
                        mLastClickPos = pos;
                        updatePlayStop();
                        mPlayer.release();
                        mPlayer = null;
                    }
                    mLastClickPos = pos;
                    if (mPlayer == null) {
                        if (requestAudioFocus()) {
                            getItemViews(parent);
                            mCurRecordDuration.setVisibility(View.GONE);
                            //mCurSeekBar.setVisibility(View.VISIBLE);
                            mCurTotalDuration.setVisibility(View.VISIBLE);
                            mCurTime.setVisibility(View.VISIBLE);
                            mCurTagIcon.setVisibility(View.GONE);
                            if (mPlayer == null) {
                                mPlayer = new LocalPreviewPlayer();
                                if (RecordingFileListTabUtils.isReceive && mAudioManager != null&&!mAudioManager.isBluetoothA2dpOn()) {
                                    mAudioManager.setMode(AudioManager.MODE_IN_CALL);
                                    mAudioManager.setSpeakerphoneOn(false);
                                } else if (mAudioManager != null) {
                                    mAudioManager.setMode(AudioManager.MODE_NORMAL);
                                    mAudioManager.setSpeakerphoneOn(true);
                                }
                                mPlayer.setActivity(mContext, LocalRecordFragment.this);
                                try {
                                    RecorderItem item = mAdapter.findItem(pos);
                                    Uri mUri = ContentUris
                                            .withAppendedId(
                                                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                                    item.id);
                                    mTagHashMap = WaveDataManager.getTagData(mContext, item.title, item.mimeType);
                                    mPlayingUri = mUri;
                                    mPlayer.setDataSourceAndPrepare(mUri);
                                    mPlayer.setUISeekBar(mCurSeekBar);
                                } catch (Exception ex) {
                                    Log.d(TAG, "Failed to open file: " + ex);
                                    if (mPlayer != null) {
                                        mPlayer.release();
                                        mPlayer = null;
                                    }
                                    Toast.makeText(mContext,
                                            R.string.playback_failed,
                                            Toast.LENGTH_SHORT).show();
                                    return;
                                }
                            }
                        } else {
                            Toast.makeText(mContext, R.string.no_allow_play_calling, Toast.LENGTH_SHORT).show();
                            return;
                        }
                    } else {
                        if (mPlayer.isPlaying()) {
                            mPlayer.pause();
                        } else {
                            Log.d(TAG, "isReceive =" + RecordingFileListTabUtils.isReceive + " mAudioManager=" + mAudioManager);
                            if (RecordingFileListTabUtils.isReceive && mAudioManager != null&&!mAudioManager.isBluetoothA2dpOn()) {
                                mAudioManager.setMode(AudioManager.MODE_IN_CALL);
                                mAudioManager.setSpeakerphoneOn(false);
                            } else if (mAudioManager != null) {
                                mAudioManager.setMode(AudioManager.MODE_NORMAL);
                                mAudioManager.setSpeakerphoneOn(true);
                            }
                            start();
                        }
                        updatePlayPause();
                    }
                }
            });
            return convertView;
        }


        private RecorderItem findItem(int pos) {
            RecorderItem result = null;
            Object obj = getItem(pos);
            if (obj != null && obj instanceof RecorderItem) {
                result = (RecorderItem) obj;
            } else {
                throw new RuntimeException("findItem() failed; pos == " + pos);
            }
            return result;
        }

        public void setFragment(LocalRecordFragment fragment) {
            mFragment = fragment;
        }
    }

    @SuppressWarnings("unused")
    private class RecorderItem {
        private final long id;
        private String data;
        private String mimeType;
        private long size;
        private String title;
        private String display_name;
        private long time;
        private int duration;
        private int tagNumber;

        private static final String _ID = MediaStore.Audio.Media._ID;
        private static final String SIZE = MediaStore.Audio.Media.SIZE;
        private static final String _DATA = MediaStore.Audio.Media.DATA;
        private static final String TITLE = MediaStore.Audio.Media.TITLE;
        private static final String DISPLAY_NAME = MediaStore.Audio.Media.DISPLAY_NAME;
        private static final String MOD_DATE = MediaStore.Audio.Media.DATE_MODIFIED;
        private static final String MIME_TYPE = MediaStore.Audio.Media.MIME_TYPE;
        private static final String DU_STRING = MediaStore.Audio.Media.DURATION;
        private static final String TAG_NUMBER = MediaStore.Audio.Media.BOOKMARK;

        private static final String AUDIO_AMR = "audio/amr";
        private static final String AUDIO_3GPP = "audio/3gpp";
        private static final double NUMBER_KB = 1024D;
        private static final double NUMBER_MB = NUMBER_KB * NUMBER_KB;

        RecorderItem(long id) {
            this.id = id;
        }

        RecorderItem(long id, String data, String mimeType) {
            this(id);
            this.data = data;
            this.mimeType = mimeType;
        }

        RecorderItem(long id, String data, String mimeType, long size, String title) {
            this(id, data, mimeType);
            this.size = size;
            this.title = title;
        }

        @SuppressLint("StringFormatMatches")
        public String getSize() {
            StringBuffer buff = new StringBuffer();
            if (size > 0) {
                String format = null;
                double calculate = -1D;
                if (size < NUMBER_KB) {
                    format = getResources().getString(R.string.list_recorder_item_size_format_b);
                    int calculate_b = (int) size;
                    buff.append(String.format(format, calculate_b));
                } else if (size < NUMBER_MB) {
                    format = getResources().getString(R.string.list_recorder_item_size_format_kb);
                    calculate = (size / NUMBER_KB);
                    DecimalFormat df = new DecimalFormat(".##");
                    String st = df.format(calculate);
                    buff.append(String.format(format, st));
                } else {
                    format = getResources().getString(R.string.list_recorder_item_size_format_mb);
                    calculate = (size / NUMBER_MB);
                    DecimalFormat df = new DecimalFormat(".##");
                    String st = df.format(calculate);
                    buff.append(String.format(format, st));
                }
            }
            return buff.toString();
        }

        /**
         * get the file's name
         *
         * @return file's name
         */
        public String getDisplayName() {
            return display_name;
        }

        public void setDisplayName(String displayName) {
            this.display_name = displayName;
        }

        public void setData(String data) {
            this.data = data;
        }

        public String getData() {
            return data;
        }

        /**
         * get the file's id
         *
         * @return file's id
         */
        public long getId() {
            return id;
        }
        /* @} */

        /**
         * get last file modify date
         *
         * @return last file modify date
         */
        public String getDate() {
            if (time > 0) {
                java.util.Date d = new java.util.Date(time * 1000);
                java.text.DateFormat formatter_date =
                        java.text.DateFormat.getDateInstance();
                return formatter_date.format(d);
            }
            return null;
        }

        /**
         * get last file modify time
         *
         * @return modify time
         */
        public String getTime() {
            if (time > 0) {
                java.util.Date d = new java.util.Date(time * 1000);
                java.text.DateFormat formatter_time =
                        java.text.DateFormat.getTimeInstance();
                return formatter_time.format(d);
            }
            return null;
        }

        public String getAlertMessage() {
            String msg =
                    getResources().getString(R.string.recording_file_delete_alert_message);
            String result = String.format(msg, (display_name != null ? display_name : ""));
            return result;
        }

        @Override
        public String toString() {
            StringBuffer buff = new StringBuffer();
            buff.append("id == ").append(id)
                    .append(" --- data == ").append(data)
                    .append(" --- mimeType == ").append(mimeType)
                    .append(" --- size == ").append(size)
                    .append(" --- title == ").append(title)
                    .append(" --- display_name == ").append(display_name)
                    .append(" --- time == ").append(time)
                    .append(" --- duration == ").append(duration);
            return buff.toString();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        queryFileAysnc();

    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onPause() {
        super.onPause();
        stopPlayback();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        getContext().unregisterReceiver(mExternalMountedReceiver);
    }
    private boolean isCallStatus(){//bug 766719
        TelephonyManager pm = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        @SuppressWarnings("WrongConstant")
        TelephonyManager pm1 = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE
                + "1");
        AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
       if ((pm != null && (pm.getCallState() == TelephonyManager.CALL_STATE_OFFHOOK ||
                pm.getCallState() == TelephonyManager.CALL_STATE_RINGING))
                || (pm1 != null && (pm1.getCallState() == TelephonyManager.CALL_STATE_OFFHOOK ||
                pm1.getCallState() == TelephonyManager.CALL_STATE_RINGING))
                || am.getMode() == AudioManager.MODE_IN_COMMUNICATION){
                    return true;
                }
                return false;
    }
    private void stopPlayback() {
        mProgressRefresher.removeCallbacksAndMessages(null);

        if (mPlayer != null) {
            updatePlayStop();
            if (!isCallStatus()) {//bug 766719

                mAudioManager.setMode(AudioManager.MODE_NORMAL);
                mAudioManager.setSpeakerphoneOn(true);
            }
            mPlayer.release();
            mPlayer = null;
            mAudioManager.abandonAudioFocus(mAudioFocusListener);
        }
    }

    public void onCompletion() {
        stopPlayback();
    }

    private BroadcastReceiver mExternalMountedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            queryFileAysnc();
        }
    };

    private void registerReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_MEDIA_REMOVED);
        intentFilter.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
        intentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        intentFilter.addDataScheme("file");
        mContext.registerReceiver(mExternalMountedReceiver, intentFilter);
    }
}
