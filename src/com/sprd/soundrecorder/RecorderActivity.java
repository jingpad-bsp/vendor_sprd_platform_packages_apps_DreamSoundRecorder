package com.sprd.soundrecorder;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.Display;
import android.view.WindowManager;
import com.android.soundrecorder.R;
import com.android.soundrecorder.RecordService;
import com.sprd.soundrecorder.data.DataOpration;
import com.sprd.soundrecorder.service.RecordingService;
import com.sprd.soundrecorder.service.SprdRecorder;
import com.sprd.soundrecorder.ui.RecordWaveView;
import android.app.ActivityManager.RecentTaskInfo;

import java.util.List;


/**
 * Created by jian.xu on 2017/10/23.
 */

public class RecorderActivity extends Activity {

    private static final String TAG = RecorderActivity.class.getSimpleName();
    private boolean mNeedRequestPermissions = false;
    private String mRequestedType = SprdRecorder.ANY_ANY;
    private long mMaxFileSize = -1;

    private static final boolean IS_SAVE_FILE_WHEN_CANEL_RENAME_DIALOG = true;
    private boolean isFromOtherApp = false;
    private RecordingService mService = null;
    private RecordingService.RecordState mRecordState = null;

    private ImageButton mRecordButton;
    private Button mTagAndSettingButton;
    private Button mStopAndViewButton;
    private TextView mRecordFileName;
    private TextView mStateMessage2;
    private TextView mTimerView;
    private Typeface mTypeface;
    private RelativeLayout mRelativeLayout;
    private RecordWaveView mWavesView;
    private boolean mIsServiceBind = false;

    private static final int UPDATE_RECORDING_TIME = 0;
    private static final int UPDATE_WAVEVIEW = 1;

    private static final int UPDATE_WAVEVIEW_INTERNAL = 100;
    private static final int MAX_TAG_SIZE = 99;
    private String mTimerFormat;
    private AlertDialog mAlertDialog = null;
    private Dialog mSaveDialog;
    private boolean isFromOnCreat = false;
    private boolean mReceiverTag = false;

    private static final int SOUNDPICKER_RESULT_CODE = 1;


    //UNISOC BUG:900673 Stop record normally when get the broadcast of SHUT_DOWN action
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_SHUTDOWN) && isRecorderActive()) {
                Log.d(TAG,"ACTION_SHUTDOWN stopRecorder");
                mService.stopRecord();
            }
        }
    };

    @Override
    public void onCreate(Bundle icycle) {
        Log.d(TAG, "on create");
        super.onCreate(icycle);

        //SPRD  876347 add clear AppTask when monkey test
        if (ActivityManager.isUserAMonkey()) {
            clearAppTasks();
        }

        if (Utils.isIntentFromSoundRecordWare(getIntent())) {
            finish();
            return;
        }

        //bug 1171357 Sound Recorder should not support multi window mode
        if (isInMultiWindowMode()) {
            Display display = getWindowManager().getDefaultDisplay();
            int height = display.getHeight();
            Utils.showToastWithText(RecorderActivity.this, null,
                    getString(R.string.exit_multiwindow_tips), Toast.LENGTH_SHORT, height/9);
            finish();
            return;
        }
        mNeedRequestPermissions = Utils.checkAndBuildPermissions(this);
        if (mNeedRequestPermissions) {
            //bug 1144071 there are two cases when reset app preferences
            SettingActivity.getRecordSavePath(this);
        }
        Intent intent = getIntent();
        if (intent != null) {
            String type = intent.getType();
            if (type != null) {
                if (SprdRecorder.isSupportRecordMimeType(type)) {
                    mRequestedType = type;
                } else if (SprdRecorder.AUDIO_ANY.equals(type) ||
                        SprdRecorder.ANY_ANY.equals(type)) {
                    mRequestedType = SprdRecorder.ANY_ANY;
                } else {
                    setResult(RESULT_CANCELED);
                    finish();
                    return;
                }
                isFromOtherApp = true;
            }
            String action = intent.getAction();
            if (Intent.ACTION_GET_CONTENT.equals(action)
                    || MediaStore.Audio.Media.RECORD_SOUND_ACTION.equals(action)) {
                isFromOtherApp = true;
                mMaxFileSize = intent.getLongExtra(MediaStore.Audio.Media.EXTRA_MAX_BYTES, -1);
            }
            isFromOnCreat = true;
        }

        Log.d(TAG, "isFromOtherApp:" + isFromOtherApp + ",mRequestedType:");
        setContentView(R.layout.main_overlay);
        View decorView = getWindow().getDecorView();
        int option = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        decorView.setSystemUiVisibility(option);
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        initResourceRefs();
        //bug：1064127  permissions error after allow all permissions when change the language
        dismissAlertDialog();
        mRecordState = new RecordingService.RecordState(
                RecordingService.State.INVALID_STATE, RecordingService.State.INVALID_STATE);
        if (!mReceiverTag) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(Intent.ACTION_SHUTDOWN);
            registerReceiver(mReceiver, intentFilter);
            mReceiverTag = true;
        }

        Log.d(TAG, "on create end");
    }

    //SPRD  876347 add clear AppTask when monkey test
    private void clearAppTasks() {
        if (isTaskRoot()) {
            Log.d(TAG, this + " is the root of task " + getTaskId() + ", don't need to clear task!");
            return;
        }
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        List<ActivityManager.AppTask> taskList = am.getAppTasks();

        Intent currentIntent = getIntent();
        for (ActivityManager.AppTask task : taskList) {
            //945275 thorws IllegalArgumentException
            RecentTaskInfo taskInfo = null;
            try {
                taskInfo = task.getTaskInfo();
            }catch (Exception ex) {
                ex.printStackTrace();
                this.finish();
                return;
            }
            Intent rootIntent = taskInfo.baseIntent;
            Log.d(TAG, "task id: "+task.getTaskInfo().id+", rootIntent="+rootIntent);
            if (!currentIntent.filterEquals(rootIntent)) {
                Log.d(TAG, "clearAppTasks task id: "+task.getTaskInfo().id);
                task.finishAndRemoveTask();
            }
        }
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart");
        super.onStart();
        if (mNeedRequestPermissions) {
            Log.d(TAG, "need request permissions before start RecordService!");
            return;
        }
        try{
            startService(new Intent(RecorderActivity.this, RecordingService.class));
        }catch (IllegalStateException e){
            Log.e(TAG, "run()-IllegalStateException");
            finish();
            return;
        }
        if (!bindService(new Intent(RecorderActivity.this, RecordingService.class),
                mServiceConnection, BIND_AUTO_CREATE)) {
            Log.e(TAG, "<onStart> fail to bind service");
            finish();
            return;
        }
        mIsServiceBind = true;
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        /*if (mNeedRequestPermissions) {
            Utils.checkAndBuildPermissions(this);
            Log.d(TAG, "need request permissions before onResume");
            return;
        }*/
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onstop");
        super.onStop();
        mHandler.removeMessages(UPDATE_RECORDING_TIME);
        mHandler.removeMessages(UPDATE_WAVEVIEW);
        if (isRecorderActive() && isFromOtherApp) {
            mService.doStopRecordSync();
            finish();
            stopService(new Intent(this, RecordService.class));
        }
        if (mService != null) {
            mService.setRecorderListener(null);
        }
        if (mIsServiceBind) {
            unbindService(mServiceConnection);
            mIsServiceBind = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //bug:963574 the savedialog  dismiss when the activity call ondestroy
        if (mSaveDialog != null) {
            mSaveDialog.dismiss();
            mSaveDialog = null;
        }
        //bug:1045288, dismiss mAlertDialog when the activity destroy to avoid IllegalArgumentException
        dismissAlertDialog();
        if (mReceiverTag) {
            unregisterReceiver(mReceiver);
            mReceiverTag = false;
        }

    }

    private void dismissAlertDialog() {
        if (mAlertDialog != null) {
            mAlertDialog.dismiss();
            mAlertDialog = null;
        }
    }

    //bug 1171357 Sound Recorder should not support multi window mode
    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
        super.onMultiWindowModeChanged(isInMultiWindowMode);
        if (isInMultiWindowMode) {
            Utils.showToastWithText(RecorderActivity.this, null,
                    getString(R.string.exit_multiwindow_tips), Toast.LENGTH_SHORT);
            finish();
            return;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        boolean resultsAllGranted = true;
        if (grantResults.length > 0) {
            for (int result : grantResults) {
                if (PackageManager.PERMISSION_GRANTED != result) {
                    resultsAllGranted = false;
                }
            }
        }
        //bug 964526 soundrecorder SecurityException crash
        if (resultsAllGranted && (grantResults.length > 0)) {
            mNeedRequestPermissions = false;
            Log.d(TAG, "<onRequestPermissionsResult> bind service");
            //Bug:911464 the app is not start but start service is exception
            try {
                startService(new Intent(this, RecordingService.class));
            } catch (IllegalStateException e){
                Log.e(TAG, "run()-IllegalStateException");
                finish();
                return;
            }
            if (!(bindService(new Intent(this, RecordingService.class),
                    mServiceConnection, BIND_AUTO_CREATE))) {
                Log.e(TAG, "<onStart> fail to bind service");
                finish();
                return;
            }
            mIsServiceBind = true;
        } else {
            mAlertDialog = new AlertDialog.Builder(this)
                    //bug 1186363 modifyy the clues
                    /*.setTitle(
                            getResources()
                                  .getString(R.string.error_app_internal))*/
                    .setMessage(getResources().getString(R.string.error_permissions))
                    .setCancelable(false)
                    .setOnKeyListener(new Dialog.OnKeyListener() {
                        @Override
                        public boolean onKey(DialogInterface dialog, int keyCode,
                                             KeyEvent event) {
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
                            })
                    .show();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                if (isFromOtherApp &&
                        (mRecordState.mNowState == RecordingService.State.STARTING_STATE
                                || mRecordState.mNowState == RecordingService.State.STOPPING_STATE)) {
                    Log.d(TAG, "not finish now");
                    return true;
                }

                if (isFromOtherApp && isRecorderActive()) {
                    mService.doStopRecordSync();
                    finish();
                }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (SOUNDPICKER_RESULT_CODE == requestCode) {
            if (data != null) {
                if (data.getData() != null) {
                    setResult(RESULT_OK, new Intent().setData(data.getData()));
                }
                this.finish();
            }
        }
    }


    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) {
            Log.e(TAG, "<onServiceConnected> Service connected");
            mService = ((RecordingService.SoundRecorderBinder) arg1).getService();
            initService();
            //bug:996640 not set third party flag when scheduled recording is started
            mService.setFromOtherApp(isFromOtherApp);
            mService.setmIsStopSync(false);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.e(TAG, "<onServiceDisconnected> Service dis connected");
            mService = null;
            finish();
        }
    };

    private void initResourceRefs() {
        mRecordButton = findViewById(R.id.recordButton);
        mRecordButton.setOnClickListener(mButtonClickListener);
        mTagAndSettingButton = findViewById(R.id.tagButton);
        mTagAndSettingButton.setContentDescription(getResources().getString(R.string.button_set));
        mTagAndSettingButton.setOnClickListener(mButtonClickListener);
        mStopAndViewButton = findViewById(R.id.stopButton);
        mStopAndViewButton.setOnClickListener(mButtonClickListener);
        mStopAndViewButton.setContentDescription(getResources().getString(R.string.button_list));

        mRecordButton.setImageResource(R.drawable.custom_record_btn);
        mRecordButton.setBackgroundResource(R.drawable.custom_record_btn);
        mRecordButton.setContentDescription(getResources().getString(R.string.start_record));

        mRecordFileName = findViewById(R.id.filename);
        mStateMessage2 = findViewById(R.id.stateMessage2);
        mTimerView = findViewById(R.id.timerView);

        mTypeface = Typeface.createFromAsset(getAssets(), "fonts/RobotoThin.ttf");
        mTimerView.setTypeface(mTypeface);

        mRelativeLayout = findViewById(R.id.wavesLayout);
        mWavesView = new RecordWaveView(this);
        mRelativeLayout.addView(mWavesView);
        mTimerFormat = getResources().getString(R.string.timer_format);
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "handleMessage:" + msg.what + ",mstate:" + mRecordState);
            switch (msg.what) {
                case UPDATE_RECORDING_TIME:
                    if (mRecordState.mNowState != RecordingService.State.SUSPENDED_STATE) {
                        mTimerView.setVisibility(View.VISIBLE);
                        updateRecordTimeView();
                        if (mRecordState.mNowState == RecordingService.State.RECORDING_STATE) {
                            mHandler.sendEmptyMessageDelayed(UPDATE_RECORDING_TIME, UPDATE_WAVEVIEW_INTERNAL);
                        }
                    } else {
                        if (mTimerView.getVisibility() == View.VISIBLE) {
                            mTimerView.setVisibility(View.INVISIBLE);
                            mHandler.sendEmptyMessageDelayed(UPDATE_RECORDING_TIME, 800);
                        } else {
                            mTimerView.setVisibility(View.VISIBLE);
                            mHandler.sendEmptyMessageDelayed(UPDATE_RECORDING_TIME, 300);
                        }
                    }
                    break;
                case UPDATE_WAVEVIEW:
                    updateWaveView();
                    if (mRecordState.mNowState == RecordingService.State.RECORDING_STATE) {
                        mHandler.sendEmptyMessageDelayed(UPDATE_WAVEVIEW, UPDATE_WAVEVIEW_INTERNAL);
                    }
                    break;
            }
        }
    };

    private void updateWaveView() {
        if (mService != null) {
            mWavesView.invalidate();
        }

    }

    private void updateRecordTimeView() {
        long time = 0;
        if (isRecorderActive()) {
            time = mService.getRecordingTime();
            Log.d(TAG, "updateRecordTimeView:" + time);
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
        String timeStr = String.format(mTimerFormat, hour, minute, second);
        mTimerView.setVisibility(View.VISIBLE);
        mTimerView.setText(timeStr);
    }

    private RecordingService.RecorderListener mRecorderListener
            = new RecordingService.RecorderListener() {

        @Override
        public void onStateChanged(RecordingService.RecordState stateCode) {
            Log.d(TAG, "onStateChanged:" + stateCode);
            if (mRecordState.mNowState == RecordingService.State.STARTING_STATE &&
                    stateCode.mNowState == RecordingService.State.RECORDING_STATE) {
                mWavesView.setWaveData(mService.getWaveDataList(), mService.getTagDataList());
            }
            mRecordState = stateCode;
            //bug988150,992338 when the recording file was saved automatically or resume record，dismiss the savedialog
            if((mRecordState.mNowState == RecordingService.State.RECORDING_STATE ||
                    mRecordState.mNowState == RecordingService.State.IDLE_STATE) &&
                    mSaveDialog != null) {
                mSaveDialog.dismiss();
            }
            updateUi();
            if (mRecordState.mLastState == RecordingService.State.STOPPING_STATE &&
                    mRecordState.mNowState == RecordingService.State.IDLE_STATE &&
                    isFromOtherApp) {
                Log.d(TAG, "stop from isFromOtherApp and finish activity");
                finish();
            }

        }

        @Override
        public void onRecordDataReturn(Uri uri) {
            Log.i(TAG, "onRecordDataReturn:" + uri);
            if (isFromOtherApp) {
                setResult(RESULT_OK, new Intent().setData(uri).setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
            }
        }

        @Override
        public void onError(SprdRecorder.ErrorInfo error) {
            showErrorDialog();
        }

    };

    private void initService() {
        if (mService == null) {
            return;
        }
        Log.i(TAG, "initService(),mstate:" + mRecordState);
        mService.setRecorderListener(mRecorderListener);
        //bug:922346 recording in background ,not reset
        mWavesView.setWaveData(mService.getWaveDataList(), mService.getTagDataList(), isFromOnCreat);
        mHandler.removeMessages(UPDATE_WAVEVIEW);
        mHandler.removeMessages(UPDATE_RECORDING_TIME);
        mHandler.sendEmptyMessage(UPDATE_WAVEVIEW);
        mHandler.sendEmptyMessage(UPDATE_RECORDING_TIME);
        if (isFromOtherApp && isFromOnCreat){
            if (mRecordState.mNowState == RecordingService.State.RECORDING_STATE || mRecordState.mNowState == RecordingService.State.SUSPENDED_STATE){
                mService.doStopRecordSync();
            }
        }
        isFromOnCreat = false;
    }

    private void showErrorDialog() {

    }

    private Button.OnClickListener mButtonClickListener = new Button.OnClickListener() {
        private Toast mToast;
        private long mLastTagTime;

        @Override
        public void onClick(View view) {
            if (mNeedRequestPermissions) {//bug 754095 the record stop excepiton
                return;
            }
            switch (view.getId()) {
                case R.id.recordButton:
                    startOrPauseRecord();
                    break;
                case R.id.tagButton:
                    if (mService != null && mService.getTagSize() >= MAX_TAG_SIZE) {
                        //bug:1144157  Toast  won't show after tag number reach the maximum quantity.
                        mToast = Utils.showToastWithText(RecorderActivity.this, mToast,
                                getString(R.string.tag_limit), Toast.LENGTH_SHORT);
                        return;
                    }
                    if ((SystemClock.elapsedRealtime() - mLastTagTime) > 1000) {
                        if (mRecordState.mNowState == RecordingService.State.IDLE_STATE) {
                            Intent intent = new Intent(RecorderActivity.this, SettingActivity.class);
                            intent.putExtra(SettingActivity.FROM_OTHER_APP,isFromOtherApp);
                            startActivity(intent);
                            return;
                        } else if (mRecordState.mNowState == RecordingService.State.RECORDING_STATE) {
                            mService.addRecordTag();
                            mLastTagTime = SystemClock.elapsedRealtime();
                            mTagAndSettingButton.setText(String.valueOf(mService.getTagSize()));
                        }
                    }
                    break;
                case R.id.stopButton:
                    if (mRecordState.mNowState == RecordingService.State.IDLE_STATE) {
                        if (isFromOtherApp) {
                            startActivityForResult(new Intent(RecorderActivity.this, SoundPicker.class), SOUNDPICKER_RESULT_CODE);
                        } else {
                            startActivity(new Intent(RecorderActivity.this, RecordListActivity.class));
                        }
                        return;
                    } else if (isRecorderActive()) {
                        if (isFromOtherApp) {
                            mService.doStopRecordSync();
                            finish();
                        } else if (SettingActivity.getIsAutoSave(RecorderActivity.this)) {
                            mService.stopRecord();
                        } else {
                            if (mRecordState.mNowState == RecordingService.State.RECORDING_STATE) {
                                mService.pauseRecord();
                            }
                            if (mSaveDialog != null) {
                                mSaveDialog.dismiss();
                            }
                            mSaveDialog = DataOpration.showRenameConfirmDialog(
                                    RecorderActivity.this, mService.getRecordFileFullPath(), null);
                        }
                    }

                    break;

            }
        }
    };

    private boolean isRecorderActive() {
        return mService != null
                && (mRecordState.mNowState == RecordingService.State.RECORDING_STATE
                || mRecordState.mNowState == RecordingService.State.SUSPENDED_STATE);
    }

    private void updateUi() {
        switch (mRecordState.mNowState) {
            case IDLE_STATE:
                mHandler.removeMessages(UPDATE_WAVEVIEW);
                mHandler.removeMessages(UPDATE_RECORDING_TIME);
                mRecordButton.setEnabled(true);
                mRecordButton.setImageResource(R.drawable.custom_record_btn);
                mRecordButton.setContentDescription(getResources().getString(R.string.start_record));
                mTagAndSettingButton.setEnabled(true);
                mTagAndSettingButton.setText("");
                mTagAndSettingButton.setContentDescription(getResources().getString(R.string.button_set));
                mTagAndSettingButton.setBackgroundResource(R.drawable.custom_record_set_btn);
                mStopAndViewButton.setEnabled(true);
                //mStopAndViewButton.setImageResource(R.drawable.custom_record_list_btn);
                mStopAndViewButton.setBackgroundResource(R.drawable.custom_record_list_btn);
                mStopAndViewButton.setContentDescription(getResources().getString(R.string.button_list));
                //todo:change string
                mStateMessage2.setText(getString(R.string.recording));
                mRecordFileName.setText("");
                mWavesView.invalidate();
                updateRecordTimeView();

                break;
            case RECORDING_STATE:
                mHandler.removeMessages(UPDATE_WAVEVIEW);
                mHandler.removeMessages(UPDATE_RECORDING_TIME);
                mHandler.sendEmptyMessage(UPDATE_RECORDING_TIME);
                mHandler.sendEmptyMessage(UPDATE_WAVEVIEW);
                mRecordButton.setEnabled(true);
                mRecordButton.setImageResource(R.drawable.custom_record_suspend_btn);
                mRecordButton.setContentDescription(getResources().getString(R.string.pause));
                mTagAndSettingButton.setEnabled(!isFromOtherApp);
                if (mService.getTagSize() > 0) {
                    mTagAndSettingButton.setText(String.valueOf(mService.getTagSize()));
                }
                mTagAndSettingButton.setBackgroundResource(R.drawable.custom_record_tag_btn);
                mTagAndSettingButton.setContentDescription(getResources().getString(R.string.button_tag));
                //mStopAndViewButton.setImageResource(R.drawable.custom_record_stop_btn);
                mStopAndViewButton.setBackgroundResource(R.drawable.custom_record_stop_btn);
                mStopAndViewButton.setEnabled(true);
                mStopAndViewButton.setContentDescription(getResources().getString(R.string.record_stop));
                mRecordFileName.setText(mService.getRecordFileName());
                mStateMessage2.setText(getString(R.string.soundrecording));
                break;
            case SUSPENDED_STATE:
                mHandler.removeMessages(UPDATE_WAVEVIEW);
                mHandler.removeMessages(UPDATE_RECORDING_TIME);
                mHandler.sendEmptyMessage(UPDATE_RECORDING_TIME);
                mRecordButton.setEnabled(true);
                mRecordButton.setImageResource(R.drawable.custom_record_play_btn);
                mRecordButton.setContentDescription(getResources().getString(R.string.resume_record));
                mTagAndSettingButton.setEnabled(false);
                if (mService.getTagSize() > 0) {
                    mTagAndSettingButton.setText(String.valueOf(mService.getTagSize()));
                }
                mTagAndSettingButton.setBackgroundResource(R.drawable.custom_record_tag_btn);
                //mStopAndViewButton.setImageResource(R.drawable.custom_record_stop_btn);
                mStopAndViewButton.setBackgroundResource(R.drawable.custom_record_stop_btn);
                mStopAndViewButton.setEnabled(true);
                mStateMessage2.setText(R.string.pauserecording);
                mRecordFileName.setText(mService.getRecordFileName());
                updateRecordTimeView();
                break;
            case STARTING_STATE:
            case STOPPING_STATE:
                mTagAndSettingButton.setEnabled(false);
                mRecordButton.setEnabled(false);
                mStopAndViewButton.setEnabled(false);
                break;
        }
    }

    /* bug906609 show wrong message when we abandon the recording of less than 1 sec @{*/
    public void renameOprateCancle(boolean renameOprate) {
        mService.renameOprateCancle(renameOprate);
    }
    /* }@ */

    public void stopRecord() {
        Log.d(TAG, "stop record,state:" + mRecordState);
        mService.stopRecord();
    }

    public void stopRecordNoSave() {
        Log.d(TAG, "stopRecordNoSave,state:" + mRecordState);
        mService.stopRecord(true);
    }

    public void stopRecord(String newName) {
        Log.d(TAG, "stop record,state:" + mRecordState + ",new name:" + newName);
        mService.stopRecord(newName);
    }

    private void startOrPauseRecord() {
        Log.d(TAG, "startOrPauseRecord,state:" + mRecordState + ",isFromOtherApp:" + isFromOtherApp);
        if (mService != null) {
            if (mRecordState.mNowState == RecordingService.State.IDLE_STATE) {
                if (isFromOtherApp) {
                    mService.startRecordingFromOtherApp(
                            mRequestedType.equals(SprdRecorder.ANY_ANY) ? SettingActivity.getRecordSetType(this) : mRequestedType,
                            mMaxFileSize);
                } else {
                    mService.startRecording(SettingActivity.getRecordSetType(this), mMaxFileSize);
                }
            } else if (mRecordState.mNowState == RecordingService.State.STARTING_STATE) {
                //do nothing
            } else if (mRecordState.mNowState == RecordingService.State.RECORDING_STATE) {
                mService.pauseRecord();
            } else if (mRecordState.mNowState == RecordingService.State.SUSPENDED_STATE) {
                mService.resumeRecord();
            }
        }
    }

}
