package com.sprd.soundrecorder;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.content.SharedPreferences;
import android.widget.Toast;
import android.os.Build;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothA2dp;
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.android.soundrecorder.R;
import com.sprd.soundrecorder.data.WaveDataManager;
import com.sprd.soundrecorder.ui.RecordWavesView;
import com.sprd.soundrecorder.RecordingFileListTabUtils;
import android.media.AudioManager;
import android.os.HandlerThread;
import java.lang.Thread;
import java.lang.InterruptedException;

import java.util.Iterator;

public class RecordingFilePlay extends Activity implements
        Button.OnClickListener {
    private String TAG = "RecordingFilePlay";
    private TextView mTimerView;
    private Typeface mTypeface;
    private String mTimerFormat;
    private TextView mDuration;
    private String mRecordTitle;
    private String mPath;
    private Long mRecordID;
    private int mRecordDuration;
    private RelativeLayout mRelativeLayout;
    private RecordWavesView mWavesView;
    ImageButton mPlayButton;
    Button mPreviousButton;
    Button mNextButton;
    private String mMimeType;
    boolean mIsRecordPlaying;
    private RecordingPlayer mPlayer = null;
    private AudioManager mAudioManager;
    private boolean mPausedByTransientLossOfFocus = false;
    private boolean mPlayFromFocusCanDuck = false;
    private MenuItem mMenuItemClip = null;
    private MenuItem mMenuItemSpeak = null;
    private int spaceTime = 0;
    private Iterator mIterator;
    private int mLocation = 0;
    private int mPosition = 0;
    private int mLastPosition = 0;
    private int mFirstTagPosition = -1;
    private int mFinalTagPosition;
    int startX = 0;
    int moveX = 0;
    private Toast mToast = null;//bug 678855 the toast show too long
    private Toast mToastPlay = null;//bug 690907 the toast show too long
    private static final int UPDATE_WAVE = 1;
    private static final int FADEDOWN = 2;
    private static final int FADEUP = 3;
    private boolean mNeedRequestPermissions = false;
    private static final int RECORD_PERMISSIONS_REQUEST_CODE = 200;
    private  boolean isReceive = false;
    private Handler mHandlerSpreak = new Handler();
    private boolean mIsPause = false;
    private boolean mIsHeadSet = false;
    private boolean mIsBtHeadSet = false;
    private boolean mIsaudioloss = false;
    final Handler mHandler = new Handler() {
        float mCurrentVolume = 1.0f;

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Log.d(TAG, "handleMessage msg.what = " + msg.what);
            switch (msg.what) {
                case UPDATE_WAVE:
                    updateWavesView();
                    break;
                case FADEDOWN:
                    mCurrentVolume -= .05f;
                    if (mCurrentVolume > .2f) {
                        mHandler.sendEmptyMessageDelayed(FADEDOWN, 10);
                    } else {
                        mCurrentVolume = .2f;
                    }
                    mPlayer.setVolume(mCurrentVolume, mCurrentVolume);
                    break;
                case FADEUP:
                    mCurrentVolume += .01f;
                    if (mCurrentVolume < 1.0f) {
                        mHandler.sendEmptyMessageDelayed(FADEUP, 10);
                    } else {
                        mCurrentVolume = 1.0f;
                    }
                    mPlayer.setVolume(mCurrentVolume, mCurrentVolume);
                    break;
            }
        }
    };

    /* SPRD: bug598309 Record should stop play when switch language. @{ */
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive  intent.getAction(): " + intent.getAction());
            if (intent.getAction().equals(Intent.ACTION_LOCALE_CHANGED)) {
                stopPlayback();
            }
        }
    };
    /* @} */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().requestFeature(Window.FEATURE_ACTION_BAR);
        setContentView(R.layout.recording_file_play);
         View decorView = getWindow().getDecorView();
        int option = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        decorView.setSystemUiVisibility(option);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setElevation(0);
        actionBar.setLogo(R.drawable.before_tag_disabled);
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        initResource();
        mRecordTitle = mRecordTitle.substring(0, mRecordTitle.lastIndexOf("."));
        actionBar.setTitle(mRecordTitle);
        IntentFilter iFilter = new IntentFilter();
        iFilter.addAction(Intent.ACTION_LOCALE_CHANGED);
        registerReceiver(mReceiver, iFilter);
        IntentFilter headsetFilter = new IntentFilter();
        headsetFilter.addAction(Intent.ACTION_HEADSET_PLUG);
        headsetFilter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        headsetFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mHeadsetReceiver, headsetFilter);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        registerReceiver(mReceiverHome, intentFilter);
    }

    private void queryWaveAysnc(final String title) {
        AsyncTask<Void, Long, Void> task = null;
        if (task == null) {
            task = new AsyncTask<Void, Long, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    if (mWavesView != null) {
                        String filename = title.substring(0, title.lastIndexOf("."));
                        mWavesView.mWaveDataList = WaveDataManager.getWaveData(getApplicationContext(), filename, mMimeType);
                        mWavesView.mTagHashMap = WaveDataManager.getTagData(getApplicationContext(), filename, mMimeType);
                    }
                    for (int i = 0; i < mWavesView.mTagHashMap.size(); i++) {
                        int value = mWavesView.mTagHashMap.valueAt(i);
                        if (value == 1) {
                            mFirstTagPosition = mWavesView.mTagHashMap.keyAt(i);
                        }
                        if (value == mWavesView.mTagHashMap.size()) {
                            mFinalTagPosition = mWavesView.mTagHashMap.keyAt(i);
                        }
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Void result) {
                    updateWavesView();
                }
            };
        }
        task.execute((Void[]) null);
    }
    private void getPlayReceiveFileType(){
        SharedPreferences recordSavePreferences = this.getSharedPreferences(
                RecordingFileListTabUtils.SOUNDREOCRD_TYPE_AND_DTA, MODE_PRIVATE);
        isReceive = recordSavePreferences.getBoolean(
            RecordSetting.FRAG_TAG_VOLUME_IS_RECEIVE, false);
    }
    private void savePlayReceivetype(){
        SharedPreferences recordSavePreferences = this.getSharedPreferences(
                RecordingFileListTabUtils.SOUNDREOCRD_TYPE_AND_DTA, MODE_PRIVATE);
        SharedPreferences.Editor edit = recordSavePreferences.edit();
        edit.putBoolean(RecordSetting.FRAG_TAG_VOLUME_IS_RECEIVE, isReceive);
        edit.commit();
    }
    private void initResource() {
        mPlayButton = (ImageButton) findViewById(R.id.playButton);
        mPreviousButton = (Button) findViewById(R.id.previousButton);
        mNextButton = (Button) findViewById(R.id.nextButton);
        mPlayButton.setImageResource(R.drawable.custom_record_play_btn);
        mPlayButton.setBackgroundResource(R.drawable.custom_record_play_btn);
        mPlayButton.setContentDescription(getResources().getString(R.string.start_play));
        mPreviousButton.setEnabled(false);
        mNextButton.setEnabled(false);

        mTimerView = (TextView) findViewById(R.id.timerView);
        mTypeface = Typeface.createFromAsset(getAssets(), "fonts/RobotoThin.ttf");
        mTimerView.setTypeface(mTypeface);

        mTimerFormat = getResources().getString(R.string.timer_format);
        String timeStr = String.format(mTimerFormat, 0, 0, 0);
        mTimerView.setText(timeStr);

        mDuration = (TextView) findViewById(R.id.record_duration);
        mDuration.setText(timeStr);

        mRelativeLayout = (RelativeLayout) findViewById(R.id.wavesLayout);
        mWavesView = new RecordWavesView(this);
        mRelativeLayout.addView(mWavesView);
        mWavesView.mIsRecordPlay = true;

        Intent intent = getIntent();
        mMimeType = intent.getStringExtra("mime_type");
        mPath = intent.getStringExtra("path");
        mRecordTitle = intent.getStringExtra("title");
        mRecordID = intent.getLongExtra("id", 0);
        mRecordDuration = intent.getIntExtra("duration", 0);
        Log.d(TAG, "mRecordTitle=" + mRecordTitle + ", mRecordID="
                + mRecordID + ", mRecordDuration=" + mRecordDuration);

        mDuration.setText(Utils.makeTimeString4MillSec(RecordingFilePlay.this,
                mRecordDuration));

        queryWaveAysnc(mRecordTitle);
        getPlayReceiveFileType();
        mPlayButton.setOnClickListener(this);
        mPreviousButton.setOnClickListener(this);
        mNextButton.setOnClickListener(this);

        mWavesView.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (mWavesView.mWaveDataList.size() == 0) return true;
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = (int) event.getX();
                        mLocation = mWavesView.mPosition;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        moveX = (int) event.getX();
                        mWavesView.mPosition = mLocation - (moveX - startX) / (int) mWavesView.mWavesInterval + 1;
                        if (mWavesView.mPosition < 0) {
                            mWavesView.mPosition = 0;
                        } else if (mWavesView.mPosition >= mWavesView.mWaveDataList.size()) {
                            mWavesView.mPosition = mWavesView.mWaveDataList.size() - 1;
                        }
                        mWavesView.invalidate();
                        mPosition = (int) (mRecordDuration * (float) (mWavesView.mPosition + 1) / mWavesView.mWaveDataList.size());
                        if (mPlayer != null && mPlayer.isPlaying()) {
                            mPlayer.seekTo(mPosition);
                        }
                        updateTimeView(mPosition);
                        break;
                    case MotionEvent.ACTION_UP:
                        break;
                }
                return true;
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.clip_menu, menu);
        mMenuItemClip = menu.findItem(R.id.item_clip);
        mMenuItemClip.setVisible(false);
        mMenuItemSpeak = menu.findItem(R.id.item_volume);
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        //if (am.isWiredHeadsetOn()||am.isBluetoothA2dpOn()) {
        if (!Utils.isCanSetReceiveMode(this)) {
            mMenuItemSpeak.setVisible(false);
        }
        if (!isReceive){
            mMenuItemSpeak.setIcon(R.drawable.ear_speak);
        }else {
            mMenuItemSpeak.setIcon(R.drawable.volume_speak);
        }
        return true;
    }
    private BroadcastReceiver mHeadsetReceiver = new BroadcastReceiver() {
       @Override
       public void onReceive(Context context, Intent intent) {
           String action = intent.getAction();
            if (action.equals(Intent.ACTION_HEADSET_PLUG)) {
                int headsetState = intent.getIntExtra("state", -1);
                 if (headsetState == 1&&mMenuItemSpeak!=null){
                     mMenuItemSpeak.setVisible(false);
                     mIsHeadSet = false;
                 }else if (mMenuItemSpeak!=null){
                     if (!mIsBtHeadSet){
                         //mMenuItemSpeak.setVisible(true);
                         mIsHeadSet = false;
                     }
					 if (mPlayer!=null&&mPlayer.isPlaying()&&mIsPause){
					 	backgroundPlayStopReceive();
					 }
                 }
            }else if (BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {//bug 768746
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if(BluetoothProfile.STATE_DISCONNECTED == adapter.getProfileConnectionState(BluetoothProfile.HEADSET)) {
                    if (mMenuItemSpeak!=null){
                        Log.d(TAG,"recorddingfileplay mMenuItemSpeakdisconnected="+mMenuItemSpeak);
                        if (!mIsHeadSet){
                            //mMenuItemSpeak.setVisible(true);
                            mIsBtHeadSet = false;
                        }
                        if (mIsPause){
                            backgroundPlayStopReceive();
                        }else if (mPlayer!=null&&mPlayer.isPlaying()){
                            changeTheReceive();
                        }
                    }
                }else if (BluetoothProfile.STATE_CONNECTED==adapter.getProfileConnectionState(BluetoothProfile.HEADSET)){
                    if (mMenuItemSpeak!=null){
                         AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                         Log.d(TAG,"recorddingfileplay CONNECTIONmMenuItemSpeak="+mMenuItemSpeak+" am.isBluetoothA2dpOn()="+am.isBluetoothA2dpOn());
                        if (isReceive){
                            mHandler.removeCallbacks(mAudioFocusSpeak);
                            mHandler.postDelayed(mAudioFocusSpeak, 100);
                        }
                        mMenuItemSpeak.setVisible(false);
                        mIsBtHeadSet = true;
                    }
                }
        }else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)){
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,BluetoothAdapter.ERROR);
            switch (state) {
        case BluetoothAdapter.STATE_OFF:
            if (mMenuItemSpeak!=null){
                if (!mIsHeadSet){
                    //mMenuItemSpeak.setVisible(true);
                    mIsBtHeadSet = false;
                }
                if (mIsPause){
                    backgroundPlayStopReceive();
                }else if (!mIsaudioloss){//bug 776713
                    changeTheReceive();
                }
                mIsaudioloss =false;
            }
            break;
        }
        }
       }
   };
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                if (mPlayer != null) {
                    stopPlayback();
                }
                onBackPressed();
                break;
        case R.id.item_clip:
            Intent intent = new Intent(this, RecordingFileClip.class);
            intent.putExtra("mime_type", mMimeType);
            intent.putExtra("path", mPath);
            intent.putExtra("title", mRecordTitle);
            intent.putExtra("id", mRecordID);
            intent.putExtra("duration", mRecordDuration);
            startActivity(intent);
            break;
        case R.id.item_volume:
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

           if (!isReceive){
                if (mPlayer != null&&mPlayer.isPlaying()){
                    am.setMode(AudioManager.MODE_IN_CALL);
                    am.setSpeakerphoneOn(false);
                }
                mMenuItemSpeak.setIcon(R.drawable.volume_speak);
                isReceive = true;
                savePlayReceivetype();
                Toast.makeText(getApplicationContext(), R.string.speak_open,
                                Toast.LENGTH_SHORT).show();
            }else{
                if (mPlayer != null&&mPlayer.isPlaying()){
                   am.setMode(AudioManager.MODE_NORMAL);
                   am.setSpeakerphoneOn(true); 
                }
                mMenuItemSpeak.setIcon(R.drawable.ear_speak);
                isReceive = false;
                savePlayReceivetype();
                Toast.makeText(getApplicationContext(), R.string.speak_close,
                                Toast.LENGTH_SHORT).show();
            }
            break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (mPlayer != null) {
                stopPlayback();
            }
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
    @Override
    protected void onResume() {
        super.onResume();
        mIsPause = false;
    }
    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
        mIsPause = true;
    }

    @Override
    public void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
    }
    private void backgroundPlayStopReceive(){//bug 753808 the speakmode with call interactive is error

        if (mPlayer != null&&mPlayer.isPlaying()&&isReceive&&mMenuItemSpeak.isVisible()&&!isCallStatus()) {//bug 766617 766731

                Toast.makeText(RecordingFilePlay.this,
                R.string.background_speak_close, Toast.LENGTH_SHORT).show();
                mPlayer.pause();
                changeTheSpeaker();
                updatePlayPause();
                mMenuItemSpeak.setIcon(R.drawable.ear_speak);
                isReceive = false;
                savePlayReceivetype();
        }
        
    }
    /* SPRD: bug598309 Record should stop play when switch language. @{ */
    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        unregisterReceiver(mHeadsetReceiver);
        unregisterReceiver(mReceiver);
        unregisterReceiver(mReceiverHome);
        super.onDestroy();
    }
    /* @} */

    private int queryDuration(String title) {
        int duration = 0;
        Cursor cur = null;
        try {
            StringBuilder where = new StringBuilder();
            where.append(MediaStore.Audio.Media.TITLE).append("='")
                    .append(title).append("'");

            cur = RecordingFilePlay.this.getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Audio.Media.DURATION},
                    where.toString(), null, null);

            // read cursor
            int index = -1;
            for (cur.moveToFirst(); !cur.isAfterLast(); cur.moveToNext()) {
                duration = cur.getInt(0);
            }
        } catch (Exception e) {
            Log.v(TAG, "RecordingFilePlay.queryFile failed; E: " + e);
        } finally {
            if (cur != null)
                cur.close();
        }
        return duration;
    }

    public void onClick(View button) {
        if (!button.isEnabled())
            return;

        switch (button.getId()) {
            case R.id.playButton:
                if (mPlayer == null) {
                    //bug 629393 reset application preferencesonce again into the play is error begin
                    mNeedRequestPermissions = checkAndBuildPermissions();
                    if (mNeedRequestPermissions) {
                        return;
                    }
                    if (requestAudioFocus()){
                        //bug 629393 end
                    mPlayer = new RecordingPlayer();
                    mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                    getPlayReceiveFileType();
                    AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                   if (isReceive&&!am.isBluetoothA2dpOn()){
                        am.setMode(AudioManager.MODE_IN_CALL);
                        am.setSpeakerphoneOn(false);
                    }else{
                        am.setMode(AudioManager.MODE_NORMAL);
                        am.setSpeakerphoneOn(true);
                    }
                    mPlayer.setActivity(RecordingFilePlay.this);
                    try {
                        Uri uri = ContentUris.withAppendedId(
                                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                mRecordID);
                        mPlayer.setDataSourceAndPrepare(uri);
                    } catch (Exception ex) {
                        Log.d(TAG, "Failed to open file: " + ex);
                        // bug 664719 click the playbutton the background playing music is stop
                        // bug 1186751  Coverity scan: Dereference before null check
                        mPlayer.release();
                        mPlayer = null;

                        if (mToast == null){//bug 678855 the toast show too long
                            mToast = Toast.makeText(RecordingFilePlay.this,
                                R.string.playback_failed, Toast.LENGTH_SHORT);
                        }
                        mToast.show();
                        return;
                    }
                    }else {
                            Toast.makeText(RecordingFilePlay.this, R.string.no_allow_play_calling, Toast.LENGTH_SHORT).show();
                            return;
                        }
                } else {
                    if (mPlayer.isPlaying()) {
                        mPlayer.pause();
                        mLastPosition = mWavesView.mPosition;
                    } else {
                        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                        if (!isCallStatus()&&!am.isBluetoothA2dpOn()){
                            getPlayReceiveFileType();
                            changeTheReceive();
                        }
                        start();
                    }
                    updatePlayPause();
                }
                break;
            case R.id.previousButton:
                if (mWavesView.mWaveDataList.size() == 0) return;
                for (int i = 0; i < mWavesView.mTagHashMap.size(); i++) {
                    if (mWavesView.mPosition >= mWavesView.mTagHashMap.keyAt(i)) {
                        mLocation = mWavesView.mTagHashMap.keyAt(i) - 2;
                        if (i==mWavesView.mTagHashMap.size()-1){//bug 672470 the soundrecord click the previousbutton is enable
                            if (mPlayer != null) {
                            mPosition = (int) (mPlayer.mDuration * (float) mLocation / mWavesView.mWaveDataList.size());
                            mPlayer.seekTo(mPosition);
                        }
                        return;
                        }
                    } else {
                        if (mPlayer != null) {
                            mPosition = (int) (mPlayer.mDuration * (float) mLocation / mWavesView.mWaveDataList.size());
                            mPlayer.seekTo(mPosition);
                        }
                        return;
                    }
                }
                break;
            case R.id.nextButton:
                if (mWavesView.mWaveDataList.size() == 0) return;

                for (int i = 0; i < mWavesView.mTagHashMap.size(); i++) {
                    if (mWavesView.mPosition < mWavesView.mTagHashMap.keyAt(i)) {
                        mLocation = mWavesView.mTagHashMap.keyAt(i) - 2;
                        if (mPlayer != null) {
                            mPosition = (int) (mPlayer.mDuration * (float) mLocation / mWavesView.mWaveDataList.size());
                            mPlayer.seekTo(mPosition);
                        }
                        return;
                    }
                }
                break;
        }
    }

    //bug 629393 reset application preferencesonce again into the play is error begin
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
            return false;
        }
        String[] permissionsToRequest = new String[numPermissionsToRequest];
        int permissionsRequestIndex = 0;
        Log.d(TAG, "sprdstart requestMicrophonePermission=" + requestMicrophonePermission + " " + "requestStoragePermission =" + requestStoragePermission + " " + "requestPhoneStatePermission =" + requestPhoneStatePermission);
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

    //bug 629393 end
    public void start() {
        //bug 1187236  Coverity scan:Dereference before null check
        if (mPlayer == null || !mPlayer.isPrepared()) {//bug 665106 the big recording file is play error
            return;
        }
        if (!requestAudioFocus()) {
            if (mToastPlay == null){//bug 690907 the toast show too long
                mToastPlay = Toast.makeText(this.getApplicationContext(),
                    R.string.no_allow_play_calling, Toast.LENGTH_SHORT);
            }
            mToastPlay.show();
            return;
        }
        if (mLastPosition != mWavesView.mPosition) {
            mPosition = (int) (mPlayer.mDuration * (float) (mWavesView.mPosition + 1) / mWavesView.mWaveDataList.size());
            mPlayer.seekTo(mPosition);
        }
        mPlayer.start();
    }
    private void changeTheReceive(){
        Log.d(TAG,"sprd changeTheSpeaker isReceive="+isReceive);
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
       if (isReceive){
            am.setMode(AudioManager.MODE_IN_CALL);
            am.setSpeakerphoneOn(false);
        }else{
            am.setMode(AudioManager.MODE_NORMAL);
            am.setSpeakerphoneOn(true);
        }
    }
    private boolean isCallStatus(){
        TelephonyManager pm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        @SuppressWarnings("WrongConstant")
        TelephonyManager pm1 = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE
                + "1");
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE); 
       if ((pm != null && (pm.getCallState() == TelephonyManager.CALL_STATE_OFFHOOK ||
                pm.getCallState() == TelephonyManager.CALL_STATE_RINGING))
                || (pm1 != null && (pm1.getCallState() == TelephonyManager.CALL_STATE_OFFHOOK ||
                pm1.getCallState() == TelephonyManager.CALL_STATE_RINGING))
                || am.getMode() == AudioManager.MODE_IN_COMMUNICATION){
                    return true;
                }
                return false;
    }
    private void changeTheSpeaker(){
        Log.d(TAG,"sprd changeTheSpeaker");
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am.getMode()!=AudioManager.MODE_NORMAL){
            am.setMode(AudioManager.MODE_NORMAL);
            am.setSpeakerphoneOn(true);
        }
    }
    private boolean requestAudioFocus() {
        int audioFocus = mAudioManager.requestAudioFocus(mAudioFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        if (audioFocus == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return true;
        }
        return false;
    }
    private final Runnable mAudioFocusSpeak = new Runnable() {
        public void run() {
                try {
                    AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    if (am.isBluetoothA2dpOn()){
                        changeTheSpeaker();
                    }else {
                       mHandler.postDelayed(mAudioFocusSpeak, 100); 
                    }
                } catch (IllegalStateException e) {
                    Log.i(TAG, "run()-IllegalStateException");
                    return;
                }
        }
    };
    private OnAudioFocusChangeListener mAudioFocusListener = new OnAudioFocusChangeListener() {
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
                    }
                    if (!isCallStatus()){//bug 777665
                        changeTheSpeaker();
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    mPlayFromFocusCanDuck = true;
                    mHandler.removeMessages(FADEUP);
                    mHandler.sendEmptyMessage(FADEDOWN);
                    break;
                case AudioManager.AUDIOFOCUS_GAIN:
                    if (mPausedByTransientLossOfFocus) {
                        mPausedByTransientLossOfFocus = false;
                        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                        if (!am.isBluetoothA2dpOn()){
                            getPlayReceiveFileType();
                            changeTheReceive();
                        }
                        start();
                    }
                    if (mPlayFromFocusCanDuck) {
                        mPlayFromFocusCanDuck = false;
                        mHandler.removeMessages(FADEDOWN);
                        mHandler.sendEmptyMessage(FADEUP);
                    }
                    break;
            }
            updatePlayPause();
        }
    };

    public void updatePlayPause() {
        if (mPlayButton != null && mPlayer != null) {
            if (mPlayer.isPlaying()) {
                if (mWavesView.mWaveDataList.size() != 0) {
                    spaceTime = mPlayer.mDuration / (2 * mWavesView.mWaveDataList.size()) - 2;
                    Message msg = new Message();
                    msg.what = UPDATE_WAVE;
                    mHandler.sendMessage(msg);
                }

                mPlayButton.setImageResource(R.drawable.custom_record_suspend_btn);
                mPlayButton.setBackgroundResource(R.drawable.custom_record_suspend_btn);
                mPlayButton.setContentDescription(getResources().getString(R.string.pause));
                updateTimeView(mPlayer.getCurrentPosition());
            } else {
                mHandler.removeCallbacks(mUpdateWavesView);
                mHandler.removeCallbacks(mUpdateTimeView);
                mPlayButton.setImageResource(R.drawable.custom_record_play_btn);
                mPlayButton.setBackgroundResource(R.drawable.custom_record_play_btn);
                if (mPlayer.mIsPrepared) {
                    mPlayButton.setContentDescription(getResources().getString(R.string.resume_play));
                } else {
                    mPlayButton.setContentDescription(getResources().getString(R.string.start_play));
                }

                setPreviousButtonEnabled(false);
                setNextButtonEnabled(false);
            }
        }
    }

    private void stopPlayback() {
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
            mAudioManager.abandonAudioFocus(mAudioFocusListener);
            if (!isCallStatus()){
               mAudioManager.setMode(AudioManager.MODE_NORMAL);
               mAudioManager.setSpeakerphoneOn(true); 
            }
        }
    }

    public void onCompletion() {
        updatePlayPause();
        updateWavesView();
        updateTimeView(0);
        stopPlayback();//bug 669664 the soundrecorder is not play when the play is completion
    }

    private final Runnable mUpdateWavesView = new Runnable() {
        public void run() {
            updateWavesView();
        }
    };

    private final Runnable mUpdateTimeView = new Runnable() {
        public void run() {
            if (mPlayer != null && mPlayer.isPlaying()) {
                updateTimeView(mPlayer.getCurrentPosition());
            }
        }
    };

    public void updateWavesView() {
        if (mPlayer != null && mPlayer.isPlaying()) {
            if (mWavesView.mWaveDataList.size() != 0) {
                mWavesView.mPosition = (int) (mWavesView.mWaveDataList.size() * (float) mPlayer.getCurrentPosition() / mPlayer.mDuration);
                mWavesView.invalidate();
                updateButton();
                mHandler.removeCallbacks(mUpdateWavesView);
                mHandler.postDelayed(mUpdateWavesView, spaceTime);
            }
        } else {
            mWavesView.mPosition = 0;
            mWavesView.invalidate();
        }
    }

    public void updateButton() {
        Log.d(TAG, "updateButton mWavesView.mPosition = " + mWavesView.mPosition + ", mFirstTagPosition = " + mFirstTagPosition);
        if (mWavesView.mPosition <= mFirstTagPosition || mFirstTagPosition == -1) {
            setPreviousButtonEnabled(false);
        } else {
            setPreviousButtonEnabled(true);
        }

        if (mWavesView.mPosition >= mFinalTagPosition) {
            setNextButtonEnabled(false);
        } else {
            setNextButtonEnabled(true);
        }
    }

    private void setPreviousButtonEnabled(boolean enable) {
        Drawable drawable;
        mPreviousButton.setEnabled(enable);
        if (enable) {
            mPreviousButton.setTextColor(Color.parseColor("#4b4c57"));
            drawable = this.getResources().getDrawable(R.drawable.before_tag_default);
        } else {
            mPreviousButton.setTextColor(Color.parseColor("#d3d3d3"));
            drawable = this.getResources().getDrawable(R.drawable.before_tag_disabled);
        }
        drawable.setBounds(0, 0, drawable.getMinimumWidth(), drawable.getMinimumHeight());
        mPreviousButton.setCompoundDrawables(drawable, null, null, null);
    }

    private void setNextButtonEnabled(boolean enable) {
        Drawable drawable;
        mNextButton.setEnabled(enable);
        if (enable) {
            mNextButton.setTextColor(Color.parseColor("#4b4c57"));
            drawable = this.getResources().getDrawable(R.drawable.next_tag_default);
        } else {
            mNextButton.setTextColor(Color.parseColor("#d3d3d3"));
            drawable = this.getResources().getDrawable(R.drawable.next_tag_disabled);
        }
        drawable.setBounds(0, 0, drawable.getMinimumWidth(), drawable.getMinimumHeight());
        mNextButton.setCompoundDrawables(null, null, drawable, null);
    }

    private void updateTimeView(int time) {
        if (time >= 500) {
            mTimerView.setText(Utils.makeTimeString4MillSec(RecordingFilePlay.this, time));
        } else {
            String timeStr = String.format(mTimerFormat, 0, 0, 0);
            mTimerView.setText(timeStr);
        }
        if (mPlayer != null && mPlayer.isPlaying()) {
            mHandler.postDelayed(mUpdateTimeView, 300);
        }
    }
        private BroadcastReceiver mReceiverHome = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) {
                String reason = intent.getStringExtra("reason");
                if (reason != null && (reason.equals("homekey") || reason.equals("recentapps"))) {
                    Log.i(TAG, "homekey action = Intent.ACTION_CLOSE_SYSTEM_DIALOGS " + reason);
                    backgroundPlayStopReceive();
                }
            }
        }
    };
}
