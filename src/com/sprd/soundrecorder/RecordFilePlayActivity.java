package com.sprd.soundrecorder;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.media.session.MediaSessionManager;
import android.view.KeyEvent;

import com.android.soundrecorder.R;
import com.sprd.soundrecorder.data.RecorderItem;
import com.sprd.soundrecorder.data.WaveDataManager;
import com.sprd.soundrecorder.ui.RecordWaveView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Created by jian.xu on 2017/10/16.
 */

public class RecordFilePlayActivity extends Activity {
    private final static String TAG = RecordFilePlayActivity.class.getSimpleName();
    public final static String PLAY_DATA = "play_data";
    private static final int RECORD_PERMISSIONS_REQUEST_CODE = 200;
    private ImageButton mPlayButton;
    private Button mPreviousButton;
    private Button mNextButton;
    private TextView mPlayTimeView;
    private Typeface mTypeface;
    private String mTimeFormat;
    private TextView mDurationView;
    private RecordWaveView mWaveView;
    private RecorderItem mPlayData;
    private RecordPreviewPlayer mPlayer;
    private MenuItem mClipMenuItem;
    private MenuItem mReceiverModeMenuItem;
    private AlertDialog mAlertDialog = null;
    //Requst for  897480 foe media button
    private MediaSessionManager mMediaSessionManager;
    private boolean mDown = false;
    private long mLastClickTime = 0;
    public static final String ACTION_HEADSET_PAUSE = "com.android.soundrecordermain.HEADSET.PAUSE";
    private static final int HEADSETON = 1;
    private int mStatus = 0;
    private int mBluetoothHeadset = 0;

    private boolean mIsRecentKeyEvent = false;
    private final String SYSTEM_DIALOG_REASON_KEY = "reason";
    private final String SYSTEM_DIALOG_REASON_RECENT_APPS = "recentapps";

    private boolean mMediaEject = false;
    private boolean mIsReceiveMode = false;
    private int mUpdateInterval = -1;
    private Toast mToast = null;
    private long mLastTagTime;
    private static final int UPDATE_VIEW = 1;
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "handleMessage msg.what = " + msg.what);
            switch (msg.what) {
                case UPDATE_VIEW:
                    updatePlayInfoView();
                    break;
                default:
                    break;
            }
        }
    };

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(action.equals(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)){
                String reason = intent.getStringExtra(SYSTEM_DIALOG_REASON_KEY);
                if (reason != null && reason.equals(SYSTEM_DIALOG_REASON_RECENT_APPS)) {
                    mIsRecentKeyEvent = true;
                    stopReceiveModeBackground();
                    mIsRecentKeyEvent = false;
                }
            } else if (action.equals(Intent.ACTION_SHUTDOWN)) {
                //bug 1206277 stop play and set receive mode when shut down
                Log.d(TAG, "power shut down");
                stopPlayback();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /*if (!Utils.checkAndBuildPermissions(this)) {
            finish();
            return;
        }*/
        getWindow().requestFeature(Window.FEATURE_ACTION_BAR);
        setContentView(R.layout.recording_file_play_new);
        View decorView = getWindow().getDecorView();
        int option = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        decorView.setSystemUiVisibility(option);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setElevation(0);
        actionBar.setLogo(R.drawable.before_tag_disabled);
        checkBluetoothStatus();
        initResource();
        /* bug 1110621 finish reordfileplayactivity when the current file is in SD or otg and the SD or otg is eject @{ */
        IntentFilter intentSrorageFilter = new IntentFilter();
        intentSrorageFilter.addAction(Intent.ACTION_MEDIA_EJECT);
        intentSrorageFilter.addDataScheme("file");
        registerReceiver(mStorageReceiver, intentSrorageFilter);
        /* }@ */

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        intentFilter.addAction(Intent.ACTION_SHUTDOWN);
        registerReceiver(mReceiver, intentFilter);
        registHeadsetReceiver();
        mMediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        mMediaSessionManager.setOnMediaKeyListener(mMediaKeyListener, null);
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
                                if (eventtime - mLastClickTime < 300) {

                                } else {
                                    // single click
                                    Intent fmIntent = new Intent(ACTION_HEADSET_PAUSE);
                                    RecordFilePlayActivity.this.sendBroadcast(fmIntent);
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
    public void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
        //bug:965092 remove updateview message when this activity lose focus
//        if (mHandler != null) {
//            mHandler.removeMessages(UPDATE_VIEW);
//        }
//        //bug:1000568,1038054  when the window loses focus ,pause playing if the device is in an interactive state
//        if ( Utils.screenOnOff(this)) {
//            Log.d(TAG, "onPause pausePlayback");
//            pausePlayback();
//        }
        stopReceiveModeBackground();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "on Destroy");
        super.onDestroy();
        //bug 1377747 stop play recording file and waveform display returns to the default state
        if (mPlayer != null ) {
            stopPlayback();
            updatePlayPositionView(0);
        }
        unregisterReceiver(mReceiver);
        unregisterReceiver(mStorageReceiver);
        unregisterReceiver(mHeadsetReceiver);
    }

    @Override
    public void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
        if (mAlertDialog != null) {
            mAlertDialog.dismiss();
            mAlertDialog = null;
        }
//        stopReceiveModeBackground();
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart");
        super.onStart();
        updatePlayInfoView();
    }

    private void initResource() {
        mPlayData = (RecorderItem) getIntent().getSerializableExtra(PLAY_DATA);
        if (mPlayData == null) {
            finish();
            return;
        }
        mPlayButton = findViewById(R.id.playButton);
        mPreviousButton = findViewById(R.id.previousButton);
        mNextButton = findViewById(R.id.nextButton);
        mPlayButton.setImageResource(R.drawable.custom_record_play_btn);
        mPlayButton.setContentDescription(getResources().getString(R.string.start_play));
        mPreviousButton.setEnabled(false);
        mNextButton.setEnabled(false);
        mPlayButton.setOnClickListener(mMyButtonOnClickListener);
        mPreviousButton.setOnClickListener(mMyButtonOnClickListener);
        mNextButton.setOnClickListener(mMyButtonOnClickListener);

        mPlayTimeView = findViewById(R.id.timerView);
        mTypeface = Typeface.createFromAsset(getAssets(), "fonts/RobotoThin.ttf");
        mPlayTimeView.setTypeface(mTypeface);
        mTimeFormat = getResources().getString(R.string.timer_format);
        //bug 1143536 under the Arab language,when play the recording file,the playing time flash
        String timeStr = String.format(Locale.US,mTimeFormat, 0, 0, 0);
        mPlayTimeView.setText(timeStr);
        mDurationView = findViewById(R.id.record_duration);
        mDurationView.setText(mPlayData.getDurationString(this));

        RelativeLayout mRelativeLayout = findViewById(R.id.wavesLayout);
        mWaveView = new RecordWaveView(this, true);
        mRelativeLayout.addView(mWaveView);
        if (!checkAndBuildPermissions()) {
            queryWaveDataAysnc();
        }else {
            //bug 1144071 there are two cases when reset app preferences
            SettingActivity.getRecordSavePath(this);
        }
    }

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

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case RECORD_PERMISSIONS_REQUEST_CODE: {
                boolean resultsAllGranted = true;
                if (grantResults.length > 0) {
                    for (int result : grantResults) {
                        if (PackageManager.PERMISSION_GRANTED != result) {
                            resultsAllGranted = false;
                        }
                    }
                    if (resultsAllGranted) {
                        queryWaveDataAysnc();
                    } else {
                        showConfirmDialog();
                    }
                } else {
                    showConfirmDialog();
                }
            }
        }
    }

    public void showConfirmDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                //.setTitle(getResources().getString(R.string.error_app_internal))
                .setMessage(getResources().getString(R.string.error_permissions))
                .setCancelable(false)
                .setOnKeyListener(new Dialog.OnKeyListener() {
                    @Override
                    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                        if (keyCode == KeyEvent.KEYCODE_BACK) {
                            finish();
                        }
                        return true;
                    }
                })
                .setPositiveButton(getResources().getString(R.string.dialog_dismiss),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        });
        mAlertDialog = builder.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.clip_menu, menu);
        mClipMenuItem = menu.findItem(R.id.item_clip);
        mClipMenuItem.setVisible(false);
        mReceiverModeMenuItem = menu.findItem(R.id.item_volume);
        mReceiverModeMenuItem.setIcon(R.drawable.ear_speak_gray);
        if (!Utils.isCanSetReceiveMode(this)) {
            mReceiverModeMenuItem.setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                break;
            case R.id.item_clip:
                Intent intent = new Intent(this, RecordingFileClip.class);
                /*intent.putExtra("mime_type", mMimeType);
                intent.putExtra("path", mPath);
                intent.putExtra("title", mRecordTitle);
                intent.putExtra("id", mRecordID);
                intent.putExtra("duration", mRecordDuration);*/
                startActivity(intent);
                break;
            case R.id.item_volume:
                if ((SystemClock.elapsedRealtime() - mLastTagTime) > 1000){
                    if (!mIsReceiveMode) {
                        setReceiveMode(true);
                    } else {
                        setReceiveMode(false);
                    }
                    mLastTagTime = SystemClock.elapsedRealtime();
                }else if (mPlayer != null && mPlayer.isPlaying()){
                    mToast = Utils.showToastWithText(this, mToast, getString(R.string.mode_change), Toast.LENGTH_SHORT);
                }
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void registHeadsetReceiver() {
        IntentFilter headsetFilter = new IntentFilter();
        headsetFilter.addAction(Intent.ACTION_HEADSET_PLUG);
        headsetFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        headsetFilter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        headsetFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        headsetFilter.addAction(RecordListActivity.STAUS_PHONE_STATE);
        headsetFilter.addAction(ACTION_HEADSET_PAUSE);
        registerReceiver(mHeadsetReceiver, headsetFilter);
    }

    /* bug 1110621 finish reordfileplayactivity when the current file is in SD ot otg and the SD or otg is eject @{ */
    private BroadcastReceiver  mStorageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String path = intent.getData().getPath();
            Log.d(TAG, "onReceive:" + action + ",path:" + path );
            if ( Intent.ACTION_MEDIA_EJECT.equals(action) &&  mPlayData != null && mPlayData.getData().startsWith(path) ) {
                mMediaEject = true;
                stopPlayback();
                finish();
                return;
            }
        }
    };
    /* }@ */

    private BroadcastReceiver mHeadsetReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            Log.d(TAG, "mHeadsetReceiver,action:" + action);
            if (mReceiverModeMenuItem == null) {
                if (HEADSETON == intent.getIntExtra("state", -1)) {
                    mStatus = 1;
                } else {
                    mStatus = 0;
                }
                return;
            }
            switch (action) {
                case Intent.ACTION_HEADSET_PLUG:
                    // bug 1021831 Type-c and wired headset complict
                    boolean isWiredHeadsetOn = am.isWiredHeadsetOn();
                    Log.d(TAG, " isWiredHeadsetOn > if true,setVisible false < :" + isWiredHeadsetOn);
                    if (isWiredHeadsetOn) {
                        mStatus = 1;
                        setReceiveMode(false);
                        mReceiverModeMenuItem.setVisible(false);
                    } else {
                        mStatus = 0;
                        if (mBluetoothHeadset != HEADSETON) {
                            //mReceiverModeMenuItem.setVisible(true);
                        }
                    }
                    break;
                case AudioManager.ACTION_AUDIO_BECOMING_NOISY:
                    pausePlayback();
                    break;
                case BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED:
                    //bug 1165764 sometime will get wrong bluetooth state
                    int connectionstate = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED);
                    if (connectionstate == BluetoothProfile.STATE_DISCONNECTED) {
                        if (mStatus != HEADSETON) {
                            //mReceiverModeMenuItem.setVisible(true);
                        }
                        mBluetoothHeadset = 0;
                    } else if (connectionstate == BluetoothProfile.STATE_CONNECTED) {
                        pausePlayback();
                        setReceiveMode(false);
                        mReceiverModeMenuItem.setVisible(false);
                        mBluetoothHeadset = 1;
                    }
                    break;
                case BluetoothAdapter.ACTION_STATE_CHANGED:
                    int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                    if (state == BluetoothAdapter.STATE_OFF) {
                        if (am.isBluetoothA2dpOn()){
                            setReceiveMode(false);
                            //mReceiverModeMenuItem.setVisible(true);
                        }
                    }
                    break;
                case RecordListActivity.STAUS_PHONE_STATE:
                    //bug 999489,1103729  get Wired and bluetooth headset status
                    if (!Utils.isInCallState(getApplicationContext()) && mStatus != HEADSETON && mBluetoothHeadset != HEADSETON){
                        //mReceiverModeMenuItem.setVisible(true);
                    }else{
                        mReceiverModeMenuItem.setVisible(false);
                    }
                    break;
                case ACTION_HEADSET_PAUSE:
                    if (mPlayer != null && mPlayer.isPlaying()) {
                        pausePlayback();
                    } else {
                        startPlayback();
                    }
                    break;
            }
        }
    };

    /* bug1109493 check the connection status of the bluetooth headset }@ */
    private void checkBluetoothStatus() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            mBluetoothHeadset = 0;
        } else if (bluetoothAdapter.isEnabled()) {
            int headset = bluetoothAdapter.getProfileConnectionState(BluetoothProfile.HEADSET);
            if (headset == BluetoothProfile.STATE_CONNECTED) {
                mBluetoothHeadset = 1;
            }
        }
    }
    /* }@ */

    private void setReceiveMode(boolean enable) {
        if (enable) {
            if (!mIsReceiveMode && mPlayer != null && mPlayer.isPlaying() && Utils.isCanSetReceiveMode(this)) {
                mReceiverModeMenuItem.setIcon(R.drawable.volume_speak);
                Utils.setPlayInReceiveMode(this, true);
                mIsReceiveMode = true;
                mToast = Utils.showToastWithText(this, mToast, getString(R.string.speak_open), Toast.LENGTH_SHORT);
            }
        } else {
            if (mIsReceiveMode && !Utils.isInCallState(this)) {
                Utils.setPlayInReceiveMode(this, false);
                mIsReceiveMode = false;
                updateReceiverModeMenuIcon();
                mToast = Utils.showToastWithText(this, mToast, getString(R.string.speak_close), Toast.LENGTH_SHORT);
            }
        }

    }

    private void stopReceiveModeBackground() {
        boolean isTopActivity = Utils.isTopActivity(this, getLocalClassName());
        if (mPlayer != null && !Utils.isInCallState(this) && !isTopActivity && !mMediaEject) {
            stopPlayback();
            //bug 1210417 rest receive mode and speaker when go out soundrecorder
            Utils.resetReceiveMode(this);
            updatePlayPositionView(0);
            mToast = Utils.showToastWithText(this, mToast, getString(R.string.background_speak_close), Toast.LENGTH_SHORT);
        }
    }

    private void queryWaveDataAysnc() {
        AsyncTask<Void, Long, Void> task = null;
        if (task == null) {
            task = new AsyncTask<Void, Long, Void>() {
                private List<Float> waveDataList = new ArrayList<>();
                public SparseArray<Integer> tagSparseArray = new SparseArray<>();

                @Override
                protected Void doInBackground(Void... params) {
                    if (mWaveView != null && mPlayData != null) {
                        Log.d(TAG, "mPlayData.getTitle() = " + mPlayData.getTitle()
                                + " , mPlayData.getMimeType() = " + mPlayData.getMimeType());
                        waveDataList = WaveDataManager.getWaveData(getApplicationContext(),
                                mPlayData.getTitle(), mPlayData.getMimeType());
                        tagSparseArray = WaveDataManager.getTagData(getApplicationContext(), mPlayData.getTitle(), mPlayData.getMimeType());
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Void result) {
                    mWaveView.setWaveData(waveDataList, tagSparseArray);
                    updatePlayInfoView();
                }
            };
        }
        task.execute((Void[]) null);
    }

    private void stopPlayback() {
        if (mPlayer != null) {
            mPlayer.setPlayerListener(null);
            mPlayer.stopPlayback();
            mPlayer = null;
        }
        if (mWaveView != null) {
            mWaveView.setOnTouchListener(null);
            updateButton();
        }
        setReceiveMode(false);
        updateReceiverModeMenuIcon();
        mMediaSessionManager.setOnMediaKeyListener(null, null);
        Log.d(TAG, "stopplayback,unsetOnMediaKeyListener");

    }

    private void pausePlayback() {
        if (mPlayer != null && mPlayer.isPlaying()) {//bug 801772
            mPlayer.pausePlayback();
            updateButton();
            updateReceiverModeMenuIcon();
        }
    }

    private void startPlayback() {
        if (mPlayer == null) {
            mPlayer = new RecordPreviewPlayer(RecordFilePlayActivity.this);
            try {
                mPlayer.setDataAndPrepare(mPlayData);
                mPlayer.setPlayerListener(mPlayerLisener);
                mPlayer.setmPlayerModeCallBack(mPlayerModeCallBack);
            } catch (IOException e) {
                e.printStackTrace();
                if (mPlayer != null) {
                    mPlayer.release();
                    mPlayer = null;
                }
                mToast = Utils.showToastWithText(this, mToast, getString(R.string.playback_failed), Toast.LENGTH_SHORT);
                finish();
            }
            //bug 1376531 The key events may earlier than onCreateOptionsMenu logic if running monkey
            if (mReceiverModeMenuItem != null)
                mReceiverModeMenuItem.setIcon(R.drawable.ear_speak);
        } else if (mPlayer.isPrepared()) {
            mPlayer.startPlayback();
            updateReceiverModeMenuIcon();
        }
        updatePlayInfoView();
        mMediaSessionManager.setOnMediaKeyListener(mMediaKeyListener, null);
        Log.d(TAG, "startPlayback,setOnMediaKeyListener");
    }

    /*bug 1178979 Receiver Mode Menu should be gray if it is not used }@ */
    private void updateReceiverModeMenuIcon() {
        if (mReceiverModeMenuItem != null) {
            if (mPlayer == null || (!mIsReceiveMode && mPlayer.isPaused())) {
                mReceiverModeMenuItem.setIcon(R.drawable.ear_speak_gray);
            } else if (!mIsReceiveMode && mPlayer.isPlaying()) {
                mReceiverModeMenuItem.setIcon(R.drawable.ear_speak);
            }
        }
    }
    /*}@ */

    private void updatePlayInfoView() {
        if (mPlayer != null) {
            if (mWaveView.isHaveWaveData()) {
                /* Bug 1425110 The actual playback time is 1s less than the actual recording time */
                if (mUpdateInterval == -1) {
                    mUpdateInterval = mPlayer.getDuration() / mWaveView.getWaveDataSize();
                    Log.d(TAG, "wavesize:" + mWaveView.getWaveDataSize() + "," +
                            "duration:" + mPlayer.getDuration() + "mUpdateInterval:" + mUpdateInterval);
                }
                mWaveView.setCurrentPosition((float) mPlayer.getCurrentPosition() / mPlayer.getDuration());
                mWaveView.invalidate();
            }
            /* Bug 1423084 After clearing the storage space, The play button shows stop and the time does not move but the sound is output @{ */
            updateButton();
            updatePlayPositionView(mPlayer.getCurrentPosition());
            /* Bug 1423084 }@ */
            if ((mPlayer.isPaused() || mPlayer.isPlaying()) && (mPlayer.getCurrentPosition() > mPlayer.getDuration())) {
                mWaveView.setCurrentPosition(1);
                updateButton();
                updatePlayPositionView(mPlayer.getDuration());
                mWaveView.invalidate();
                mHandler.removeMessages(UPDATE_VIEW);
                stopPlayback();
                return;
            } else if (mPlayer.isPlaying()) {
                mHandler.removeMessages(UPDATE_VIEW);
                mHandler.sendEmptyMessageDelayed(UPDATE_VIEW, mUpdateInterval);
            } else if (mPlayer.isPlaybackComp()) {
                Log.d(TAG, "Play complete position : " + mPlayer.getCurrentPosition());
                updateButton();
                updatePlayPositionView(mPlayer.getDuration());
                mWaveView.setCurrentPosition(1);
                mWaveView.invalidate();
            }
        } else {
            mWaveView.setCurrentPosition(0);
            updateButton();
            updatePlayPositionView(0);
            mWaveView.invalidate();
            /* Bug 1425110 }@ */
            mHandler.removeMessages(UPDATE_VIEW);
        }
    }

    private void updateButton() {

        if (mPlayer != null && (mPlayer.isPlaying() || mPlayer.isPrepared())) {
            updatePreviousButton(mWaveView.isHaveMoreTag(false));
            updateNextButton(mWaveView.isHaveMoreTag(true));
            if (mPlayer.isPlaying()) {
                mPlayButton.setImageResource(R.drawable.custom_record_suspend_btn);
                mPlayButton.setContentDescription(getResources().getString(R.string.pause));
            } else {
                mPlayButton.setImageResource(R.drawable.custom_record_play_btn);
                mPlayButton.setContentDescription(getResources().getString(R.string.resume_play));
            }
        } else {
            updatePreviousButton(false);
            updateNextButton(false);
            mPlayButton.setImageResource(R.drawable.custom_record_play_btn);
            mHandler.removeMessages(UPDATE_VIEW);
            mPreviousButton.setEnabled(false);
            mNextButton.setEnabled(false);
        }
    }

    private void updatePreviousButton(boolean enable) {
        Drawable drawable;
        mPreviousButton.setEnabled(enable);
        if (enable) {
            mPreviousButton.setTextColor(getColor(R.color.previous_next_button_enable));
            drawable = getDrawable(R.drawable.before_tag_default);
        } else {
            mPreviousButton.setTextColor(getColor(R.color.previous_next_button_disable));
            drawable = getDrawable(R.drawable.before_tag_disabled);
        }
        drawable.setBounds(0, 0, drawable.getMinimumWidth(), drawable.getMinimumHeight());
        mPreviousButton.setCompoundDrawables(drawable, null, null, null);
    }

    private void updateNextButton(boolean enable) {
        Drawable drawable;
        mNextButton.setEnabled(enable);
        if (enable) {
            mNextButton.setTextColor(getColor(R.color.previous_next_button_enable));
            drawable = this.getResources().getDrawable(R.drawable.next_tag_default);
        } else {
            mNextButton.setTextColor(getColor(R.color.previous_next_button_disable));
            drawable = this.getResources().getDrawable(R.drawable.next_tag_disabled);
        }
        drawable.setBounds(0, 0, drawable.getMinimumWidth(), drawable.getMinimumHeight());
        mNextButton.setCompoundDrawables(null, null, drawable, null);
    }

    private void updatePlayPositionView(int time) {
        if (time >= 500) {
            mPlayTimeView.setText(Utils.makeTimeString4MillSec(this, time));
        } else {
            String timeStr = String.format(Locale.US,mTimeFormat, 0, 0, 0);
            mPlayTimeView.setText(timeStr);
        }
    }

    private View.OnClickListener mMyButtonOnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.playButton:
                    if (mPlayer != null && mPlayer.isPlaying()) {
                        pausePlayback();
                    } else {
                        startPlayback();
                    }
                    break;
                case R.id.previousButton:
                    seekToNextTag(false);
                    break;
                case R.id.nextButton:
                    seekToNextTag(true);
                    break;
            }
        }
    };

    private void seekToNextTag(boolean direction) {
        if (mPlayer != null && mPlayer.isPrepared()) {
            mWaveView.moveToNextTag(direction, mPlayer.isPaused());
            mWaveView.invalidate();
            int playPosition = (int) (((float) mPlayer.getDuration()) * mWaveView.getCurretPlayProcess());
            updatePlayPositionView(playPosition);
            mPlayer.seekTo(playPosition);
            updateButton();
        }
    }

    private View.OnTouchListener mWaveViewTouchListener = new View.OnTouchListener() {
        private int startX = 0;
        private int playPosition = 0;

        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            Log.d(TAG, "motionEvent:" + motionEvent.getAction());
            if (!mWaveView.isHaveWaveData() || mPlayer == null || !mPlayer.isPrepared()) {
                return true;
            }
            switch (motionEvent.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = (int) motionEvent.getX();
                    break;
                case MotionEvent.ACTION_MOVE:
                    mWaveView.moveWaveView((int) motionEvent.getX() - startX);
                    startX = (int) motionEvent.getX();
                    mWaveView.invalidate();
                    playPosition = (int) (((float) mPlayer.getDuration()) * mWaveView.getCurretPlayProcess());
                    updatePlayPositionView(playPosition);
                    if (mPlayer.isPrepared()) {
                        mPlayer.seekTo(playPosition);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    updatePlayInfoView();//SPRD bug fix 896195
                    break;
            }
            return true;
        }
    };

    private RecordPreviewPlayer.PlayerListener mPlayerLisener = new RecordPreviewPlayer.PlayerListener() {
        @Override
        public void onStarted() {
            Log.d(TAG, "onPreparedStarted");
            updatePlayInfoView();
            mWaveView.setOnTouchListener(mWaveViewTouchListener);
            //bug 1171425 should set AudioManager's mode false if connected headset when getting audio focus
            if (mStatus == HEADSETON || mBluetoothHeadset == HEADSETON) {
                setReceiveMode(false);
            }
        }

        @Override
        public void onCompleted() {
            Log.d(TAG, "onCompleted");
            updatePlayInfoView();
            stopPlayback();
        }

        @Override
        public void onError() {
            mToast = Utils.showToastWithText(RecordFilePlayActivity.this,
                    mToast, getString(R.string.playback_failed), Toast.LENGTH_SHORT);
            finish();
        }

        @Override
        public void onPausedByAudioFocusLoss() {
            Log.d(TAG, "onPausedByAudioFocusLoss");
            setReceiveMode(false);
        }
    };

    //bug 1150763 the audio mode was MODE_IN_CALL and get audio focus aganin,set call audio mode
    private RecordPreviewPlayer.PlayModeCallBack mPlayerModeCallBack = new RecordPreviewPlayer.PlayModeCallBack() {
        @Override
        public boolean isCallMode() {
            return mIsReceiveMode;
        }
    };


}

