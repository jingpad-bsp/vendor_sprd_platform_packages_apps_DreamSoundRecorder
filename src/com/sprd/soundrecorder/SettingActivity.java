package com.sprd.soundrecorder;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.Dialog;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.storage.StorageManager;
import android.os.EnvironmentEx;
import android.os.Environment;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.content.pm.PackageManager;
import android.util.Log;
import android.view.MenuItem;
import android.view.Window;
import android.view.KeyEvent;
import android.widget.TimePicker;
import android.widget.Toast;
import android.provider.DocumentsContract;
import android.net.Uri;

import com.android.soundrecorder.R;
import com.sprd.soundrecorder.frameworks.StandardFrameworks;
import com.sprd.soundrecorder.service.RecordingService;
import com.sprd.soundrecorder.service.SprdRecorder;
import com.sprd.soundrecorder.ui.SmartSwitchPreference;
import com.sprd.soundrecorder.ui.TimeAndDurationPickerDialog;
import com.sprd.soundrecorder.ui.TimePickerFragment;

import java.io.File;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;


/**
 * Created by jian.xu on 2017/10/30.
 */

public class SettingActivity extends Activity {
    private static final String TAG = SettingActivity.class.getSimpleName();

    private static final String PRE_FILE_TYPE_KEY = "pref_file_type_key";
    private static final String PRE_STORE_PATH_KEY = "pref_store_path_key";
    private static final String PRE_AUTO_SAVE_KEY = "pref_auto_save_key";
    private static final String PRE_SET_TIME_KEY = "pref_set_timer_key";
    private static final String PRE_SAVE_POWER_KEY = "pref_save_power_key";

    public final static String SOUNDREOCRD_TYPE_AND_DTA = "soundrecord.type.and.data";
    private final static String SAVE_RECORD_TYPE_INDEX = "recordType_index";
    private final static String SAVE_STORAGE_PATH = "storagePath";
    public final static String AUTO_SAVE_FILE_TYPE = "save_file";
    public final static String SAVE_POWER_MODE_ENABLE = "save_power";

    private final static String RECORDER_SAVE_POWER = "recorder.savepower.enable";

    private final static String TIMER_RECORD_TIME = "setRecordTime";
    private final static String TIMER_RECORD_DURATION = "setRecordDuration";
    public final static String FRAG_TAG_TIME_PICKER = "time_dialog";
    public final static String TIMER_RECORD_START_ACTION = "com.android.soundrecorder.timerrecord.START";
    public final static String FROM_OTHER_APP = "FromOtherApp";

    private final static String DEFAULT_INTERNAL_PATH = "/storage/emulated/0/recordings";

    private RecordSettingPreference mSettingPreference;
    private TimePickerFragment mTimePickerFragment;
    private RecordingService mService;
    private boolean fromOtherApp = false;
    private boolean mNeedRequestPermissions = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "oncreate");
        Intent intent = getIntent();
        fromOtherApp = intent.getBooleanExtra(FROM_OTHER_APP,false);
        mNeedRequestPermissions = Utils.checkAndBuildPermissions(this);
        getWindow().requestFeature(Window.FEATURE_ACTION_BAR);
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeAsUpIndicator(R.drawable.ic_ab_back_holo_dark);
        getWindow().getDecorView().setBackgroundColor(Color.WHITE);

        setContentView(R.layout.setting_layout);
        initPreference();

        IntentFilter iFilter = new IntentFilter();
        iFilter.addAction(Intent.ACTION_MEDIA_EJECT);
        iFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        iFilter.addDataScheme("file");
        registerReceiver(mStorageReceiver, iFilter);
        IntentFilter iFilter2 = new IntentFilter();
        iFilter2.addAction(RecordingService.CLOSE_TIMER_ACTION);
        registerReceiver(mCloserTimerReceiver, iFilter2);
        removeTimePicker();
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart");
        super.onStart();
        if (mNeedRequestPermissions) {
            Log.d(TAG, "need request permissions before start RecordService!");
            if (mSettingPreference != null){
                mSettingPreference.cancelTimerRecord();
            }
            return;
        }
        try{
            startService(new Intent(SettingActivity.this, RecordingService.class));
        }catch (IllegalStateException e){
            Log.e(TAG, "run()-IllegalStateException");
            finish();
            return;
        }
        if (!bindService(new Intent(SettingActivity.this, RecordingService.class),
                mServiceConnection, BIND_AUTO_CREATE)) {
            Log.e(TAG, "<onStart> fail to bind service");
            finish();
            return;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,String permissions[], int[] grantResults) {
        boolean resultsAllGranted = true;
        if (grantResults.length > 0) {
            for (int result : grantResults) {
                if (PackageManager.PERMISSION_GRANTED != result) {
                    resultsAllGranted = false;
                }
            }
        }
        if (resultsAllGranted) {
            mNeedRequestPermissions = false;
            Log.d(TAG, "<onRequestPermissionsResult> bind service");
            //bug:947966 soundrecorder happens IllegalStateException
            try {
                startService(new Intent(SettingActivity.this, RecordingService.class));
            } catch  (RuntimeException e) {
                Log.e(TAG, "Failed to start service");
                finish();
                return;
            }
            if (!(bindService(new Intent(SettingActivity.this, RecordingService.class),
                    mServiceConnection, BIND_AUTO_CREATE))) {
                Log.e(TAG, "<onStart> fail to bind service");
                finish();
                return;
            }
        }else {
            new AlertDialog.Builder(this)
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
    protected void onStop() {
        Log.d(TAG, "onstop");
        super.onStop();
        if (mService != null) {
            unbindService(mServiceConnection);
            mService = null;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {//bug 792430
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) {
            Log.e(TAG, "<onServiceConnected> Service connected");
            mService = ((RecordingService.SoundRecorderBinder) arg1).getService();
            mSettingPreference.mRecordTime = getStartRecordTime(SettingActivity.this);
            mSettingPreference.updateTimerSetPreference(false);
            if (mService != null) {
                mService.setTimeSetChangeListener(new RecordingService.TimeSetChangeListener() {
                    @Override
                    public void OnCloseTimerByNotification() {
                        mSettingPreference.mRecordTime = null;
                        mSettingPreference.updateTimerSetPreference();
                    }
                });
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.e(TAG, "<onServiceDisconnected> Service dis connected");
            mService = null;
            finish();
        }
    };

    private void initPreference() {
        final FragmentTransaction ft = getFragmentManager().beginTransaction();
        final Fragment prev = getFragmentManager().findFragmentById(R.id.setting_content);
        if (prev != null) {
            ft.remove(prev);
        }
        ft.commitAllowingStateLoss();
        getFragmentManager().executePendingTransactions();
        mSettingPreference = new RecordSettingPreference();
        getFragmentManager().beginTransaction()
                .add(R.id.setting_content, mSettingPreference)
                .commit();
        getFragmentManager().executePendingTransactions();
    }


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
                case Intent.ACTION_MEDIA_EJECT:
                    mSettingPreference.initSavePathPreference();
                    break;
            }
        }
    };

    private BroadcastReceiver mCloserTimerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive intent = " + intent);
            if (intent == null) {
                return;
            }
            if (RecordingService.CLOSE_TIMER_ACTION.equals(intent.getAction())) {
                mSettingPreference.cancelTimerRecord();
            }
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mStorageReceiver);
        unregisterReceiver(mCloserTimerReceiver);
    }

    public static class RecordSettingPreference extends PreferenceFragment
            implements Preference.OnPreferenceClickListener,
            Preference.OnPreferenceChangeListener,
            SmartSwitchPreference.OnSwitchButtonCheckedChangeListener {
        private ListPreference mFileTypePreference;
        private ListPreference mSavePathPreference;
        private SwitchPreference mAutoSavePreference;
        private SmartSwitchPreference mTimerRecordPreference;
        private SettingActivity mActivity;
        private ArrayList[] mCurrentStoregeEntries;
        private RecordTimer mRecordTime;

        private SharedPreferences mRecordPreferences;
        private SwitchPreference mSavePowerPreferences;

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            mActivity = (SettingActivity) activity;
            mRecordTime = mActivity.getStartRecordTime(mActivity);
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            Log.d(TAG, "onPreferenceClick, key = " + preference.getKey());
            switch (preference.getKey()) {
                case PRE_AUTO_SAVE_KEY:
                    if (mAutoSavePreference.isChecked()) {
                        saveIsAutoSave(mRecordPreferences, true);
                    } else {
                        saveIsAutoSave(mRecordPreferences, false);
                    }
                    return true;
                case PRE_SET_TIME_KEY:
                    mActivity.showTimerPickerDialog();
                    return true;
                case PRE_SAVE_POWER_KEY:
                    if (mSavePowerPreferences.isChecked()) {
                        saveSavePowerMode(mRecordPreferences, true);
                    } else {
                        saveSavePowerMode(mRecordPreferences, false);
                    }
                    return true;
            }
            return false;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.setting_preference);
            mRecordPreferences = mActivity.getSharedPreferences(SOUNDREOCRD_TYPE_AND_DTA, MODE_PRIVATE);

            mFileTypePreference = (ListPreference) findPreference(PRE_FILE_TYPE_KEY);
            mSavePathPreference = (ListPreference) findPreference(PRE_STORE_PATH_KEY);
            mAutoSavePreference = (SwitchPreference) findPreference(PRE_AUTO_SAVE_KEY);
            mSavePowerPreferences = (SwitchPreference) findPreference(PRE_SAVE_POWER_KEY);
            mTimerRecordPreference = (SmartSwitchPreference) findPreference(PRE_SET_TIME_KEY);
            if (mActivity.fromOtherApp){
                mSavePathPreference.setEnabled(false);
                mAutoSavePreference.setEnabled(false);
                mTimerRecordPreference.setEnabled(false);
            }else {
                mSavePathPreference.setEnabled(true);
                mAutoSavePreference.setEnabled(true);
                mTimerRecordPreference.setEnabled(true);
            }
            mFileTypePreference.setOnPreferenceChangeListener(this);
            mSavePathPreference.setOnPreferenceChangeListener(this);
            mAutoSavePreference.setOnPreferenceClickListener(this);
            mSavePowerPreferences.setOnPreferenceClickListener(this);
            mTimerRecordPreference.setOnPreferenceClickListener(this);
            mTimerRecordPreference.setOnSwitchButtonCheckedChangeListener(this);
            mAutoSavePreference.setChecked(getIsAutoSave(mRecordPreferences));
            mAutoSavePreference.setSummary(mActivity.getString(R.string.auto_save));

            initFileTypePreference();
            initSavePathPreference();
            updateTimerSetPreference(false);
            initSavePowerPreference();
        }

        private void initSavePowerPreference() {
            if (StandardFrameworks.getInstances().getBooleanFromSystemProperties(RECORDER_SAVE_POWER, false)) {
                mSavePowerPreferences.setChecked(getIsSavePowerMode(mRecordPreferences));
            } else {
                getPreferenceScreen().removePreference(mSavePowerPreferences);
            }
        }

        private void initFileTypePreference() {
            String[] entries = SprdRecorder.getSupportRecordTypeString(mActivity);
            String[] entryValues = SprdRecorder.getSupportRecordMimeType();
            int setIndex = getRecordSetTypeIndex(mRecordPreferences);
            mFileTypePreference.setEntries(entries);
            mFileTypePreference.setEntryValues(entryValues);
            mFileTypePreference.setValue(entryValues[setIndex]);
            mFileTypePreference.setSummary(entries[setIndex]);
            mFileTypePreference.setNegativeButtonText("");
        }

        private void updateFileTypePreference(String newValue) {
            int index = mFileTypePreference.findIndexOfValue(newValue);
            mFileTypePreference.setValue(SprdRecorder.getSupportRecordMimeType()[index]);
            mFileTypePreference.setSummary(SprdRecorder.getSupportRecordTypeString(mActivity)[index]);
            SharedPreferences.Editor edit = mRecordPreferences.edit();
            edit.putInt(SAVE_RECORD_TYPE_INDEX, index);
            edit.commit();
            Log.d(TAG, "updateFileTypePreference:" + mFileTypePreference.getSummary());
        }

        public void initSavePathPreference() {
            Dialog dialog = mSavePathPreference.getDialog();
            if (dialog != null) {
                dialog.dismiss();
            }
            mCurrentStoregeEntries = getCurrentSavePathEntries(mActivity);
            ArrayList<String> entries = (ArrayList<String>) mCurrentStoregeEntries[0];
            Log.d(TAG, "mCurrentStoregeEntries,entries:" + entries);
            ArrayList<String> entriesValues = (ArrayList<String>) mCurrentStoregeEntries[1];
            Log.d(TAG, "mCurrentStoregeEntries,entriesValues:" + entriesValues);
            String savePath = getRecordSavePathInternal(mActivity, mRecordPreferences, mCurrentStoregeEntries);
            Log.d(TAG, "save path:" + savePath);
            mSavePathPreference.setEntries(entries.toArray(new String[entries.size()]));
            mSavePathPreference.setEntryValues(entriesValues.toArray(new String[entriesValues.size()]));
            mSavePathPreference.setValue(savePath);
            mSavePathPreference.setNegativeButtonText("");
            mSavePathPreference.setSummary(entries.get(entriesValues.indexOf(savePath)));
        }

        private void updateSavePathPreference(String newValues) {
            int index = mSavePathPreference.findIndexOfValue(newValues);
            mSavePathPreference.setValue(((ArrayList<String>) mCurrentStoregeEntries[1]).get(index));
            mSavePathPreference.setSummary(((ArrayList<String>) mCurrentStoregeEntries[0]).get(index));
            SharedPreferences.Editor edit = mRecordPreferences.edit();
            edit.putString(SAVE_STORAGE_PATH, ((ArrayList<String>) mCurrentStoregeEntries[1]).get(index));
            edit.commit();
        }

        public void updateTimerSetPreference() {
            updateTimerSetPreference(true);
        }

        public void updateTimerSetPreference(boolean isShowToast) {
            Log.d(TAG, "lq mRecordTime=" + mRecordTime + " mActivity.mService=" + mActivity.mService);
            // Bug 1420877 After setting the timing recording, enter the setting timing interface again, and the timing button will go back
            if (mRecordTime != null) {
                setTimerRecord(mRecordTime, isShowToast);
                mTimerRecordPreference.setChecked(true);
                mTimerRecordPreference.toogle(true);
                mTimerRecordPreference.setSummary(mRecordTime.timerString);
            } else {
                Log.d(TAG, "record timer is disable");
                mTimerRecordPreference.setSummary("");
                mTimerRecordPreference.setChecked(false);
                mTimerRecordPreference.toogle(false);
                if (mActivity.mService != null) {
                    mActivity.mService.setTimerSet(null);
                }
            }
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object o) {
            Log.d(TAG, "preference:" + preference.getKey() + ",data:" + o);
            switch (preference.getKey()) {
                case PRE_FILE_TYPE_KEY:
                    updateFileTypePreference((String) o);
                    break;
                case PRE_STORE_PATH_KEY:
                    updateSavePathPreference((String) o);
                    break;
                case PRE_AUTO_SAVE_KEY:
                    saveIsAutoSave(mRecordPreferences, (boolean) o);
                    break;
            }
            return false;
        }

        private void setTimerRecord(RecordTimer setTimer, boolean isShowToast) {
            setAlarmTimer(mActivity,setTimer);
            saveStartRecordTime(mActivity, setTimer);
            Log.d(TAG, "likenk setTimerRecord isShowToast:" + isShowToast);
            if (isShowToast) {
                long timerDelta = setTimer.startTimeMillis - System.currentTimeMillis();
                String text = Utils.formatElapsedTimeUntilAlarm(mActivity, timerDelta);
                Toast.makeText(mActivity.getApplicationContext(), text, Toast.LENGTH_SHORT).show();
            }
            if (mActivity.mService != null) {
                mActivity.mService.setTimerSet(setTimer);
            }
        }

        private void cancelTimerRecord() {
            mRecordTime = null;
            saveStartRecordTime(mActivity, null);
            updateTimerSetPreference();
        }

        @Override
        public boolean OnSwitchButtonCheckedChanged(boolean checked) {
            Log.d(TAG, "SmartSwitchPreference,OnSwitchButtonCheckedChanged:" + checked);
            if (!checked) {
                cancelTimerRecord();
            } else {
                mActivity.showTimerPickerDialog();
            }
            return true;
        }

    }

    //bug 1171357 Sound Recorder should not support multi window mode
    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
        super.onMultiWindowModeChanged(isInMultiWindowMode);
        if (isInMultiWindowMode) {
            Utils.showToastWithText(SettingActivity.this, null,
                    getString(R.string.exit_multiwindow_tips), Toast.LENGTH_SHORT);
            finish();
            return;
        }
    }

    public static String getRecordSetType(Context context) {
        SharedPreferences recordSavePreferences = context.getSharedPreferences(SOUNDREOCRD_TYPE_AND_DTA, MODE_PRIVATE);
        return SprdRecorder.getSupportRecordMimeType()[getRecordSetTypeIndex(recordSavePreferences)];
    }

    public static int getRecordSetTypeIndex(SharedPreferences preferences) {
        return preferences.getInt(SAVE_RECORD_TYPE_INDEX, 0);
    }

    public static String getRecordSavePath(Context context) {
        SharedPreferences recordSavePreferences = context.getSharedPreferences(SOUNDREOCRD_TYPE_AND_DTA, MODE_PRIVATE);
        return getRecordSavePathInternal(context, recordSavePreferences, getCurrentSavePathEntries(context));
    }

    public static String getRecordFileFullPath(Context context, String filename, String mRequestType) {
        return getRecordSavePath(context) + File.separator + filename + SprdRecorder.getFileExtension(mRequestType);
    }

    public static boolean getIsAutoSave(Context context) {
        SharedPreferences recordSavePreferences = context.getSharedPreferences(SOUNDREOCRD_TYPE_AND_DTA, MODE_PRIVATE);
        return getIsAutoSave(recordSavePreferences);
    }

    public static boolean getIsAutoSave(SharedPreferences preferences) {
        return preferences.getBoolean(AUTO_SAVE_FILE_TYPE, true);
    }

    public static boolean getIsSavePowerMode(Context context) {
        SharedPreferences recordSavePreferences = context.getSharedPreferences(SOUNDREOCRD_TYPE_AND_DTA, MODE_PRIVATE);
        return getIsSavePowerMode(recordSavePreferences);
    }

    public static boolean getIsSavePowerMode(SharedPreferences preferences) {
        return preferences.getBoolean(SAVE_POWER_MODE_ENABLE, false);
    }

    private static void saveSavePowerMode(SharedPreferences preferences, boolean enable) {
        Log.d(TAG, "saveSavePowerMode:" + enable);
        SharedPreferences.Editor edit = preferences.edit();
        edit.putBoolean(SAVE_POWER_MODE_ENABLE, enable);
        edit.commit();
    }

    private static void saveIsAutoSave(SharedPreferences preferences, boolean enable) {
        Log.d(TAG, "saveIsAutoSave:" + enable);
        SharedPreferences.Editor edit = preferences.edit();
        edit.putBoolean(AUTO_SAVE_FILE_TYPE, enable);
        edit.commit();
    }

    private static String getRecordSavePathInternal(Context context,
                                                    SharedPreferences preferences,
                                                    ArrayList[] currentStorgeEntries) {
        String savePath = preferences.getString(SAVE_STORAGE_PATH, "");
        ArrayList<String> entries = (ArrayList<String>) currentStorgeEntries[0];
        ArrayList<String> entriesValues = (ArrayList<String>) currentStorgeEntries[1];
        if (savePath.isEmpty()
                || !StorageInfos.isPathExistAndCanWrite(savePath)
                || !StorageInfos.isPathMounted(savePath)) {
            int externalIndex = entries.indexOf(context.getString(R.string.external_storage));
            if (externalIndex > -1) {
                savePath = entriesValues.get(externalIndex);
            } else {
                savePath = entriesValues.get(0);
            }
            SharedPreferences.Editor edit = preferences.edit();
            edit.putString(SAVE_STORAGE_PATH, savePath);
            edit.commit();
        }
        return savePath;
    }

    public static ArrayList<String>[] getCurrentSavePathEntries(Context context) {
        ArrayList<String> entries = new ArrayList<>();
        ArrayList<String> entryValues = new ArrayList<>();
        entries.add(context.getString(R.string.internal_storage));
        //bug 1217059 Dereference null return value (NULL_RETURNS)
        String internalPath = StorageInfos.getInternalStorageDefaultPath();
        if (internalPath == null) {
            internalPath = DEFAULT_INTERNAL_PATH;
        }
        entryValues.add(createStorePath(internalPath));

        if (StorageInfos.isExternalStorageMounted()) {
            String appOwnDirSdcard = getAppOwnDirSdcard(context);

            if (appOwnDirSdcard != null) {
                entries.add(context.getString(R.string.external_storage));
                entryValues.add(createStorePath(appOwnDirSdcard));
            } else {
                Log.e(TAG, "ExternalStorage is mounted, but create sd directory error!");
            }
        }

        StorageManager storageManager = context.getSystemService(StorageManager.class);
        Map<String, String> map = StandardFrameworks.getInstances().getUsbVolumnInfo(storageManager);
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String appOwnDirOtg = getAppOwnDirOtg(context, entry.getValue());

            if (appOwnDirOtg != null) {
                entries.add(entry.getKey());
                entryValues.add(createStorePath(appOwnDirOtg));
            } else {
                Log.e(TAG, "Otg devices is mounted, but create otg directory error!");
            }
        }
        return new ArrayList[]{entries, entryValues};
    }

    /* Bug913590, Use SAF to get SD write permission @{ */
    public static String getAppOwnDirOtg(Context context, String otgIdPath) {
        String appPath = null;
        final List<File> androidMedias = new ArrayList<>();
        Collections.addAll(androidMedias, context.getExternalMediaDirs());

        for (File path: androidMedias) {
            if (path == null) {
                Log.e(TAG, "getAppOwnDirOtg, Path is null");
                continue;
            }
            if (path.getAbsolutePath().startsWith(otgIdPath) ) {
                appPath = path.getAbsolutePath() +File.separator + "recordings";
            }
        }

        return appPath;
    }

    public static String getAppOwnDirSdcard(Context context) {
        String appPath = null;

        final List<File> androidMedias = new ArrayList<>();
        Collections.addAll(androidMedias, context.getExternalMediaDirs());

        for (File path: androidMedias) {
            if (path == null) {
                Log.e(TAG, "getAppOwnDirSdcard, Path is null");
                continue;
            }
            if (EnvironmentEx.getExternalStoragePathState().equals(Environment.MEDIA_MOUNTED) &&
                    path.getAbsolutePath().startsWith(EnvironmentEx.getExternalStoragePath().getAbsolutePath()) ) {
                appPath = path.getAbsolutePath() +File.separator + "recordings";
            }
        }

        return appPath;
    }
    /* Bug913590 }@ */

    public static String createStorePath(String path) {
        File pathFile = new File(path);
        if (!pathFile.exists()) {
            //bug 1209101 coverity scan:Exceptional return value of java.io.File.mkdirs() ignored
            boolean result = pathFile.mkdirs();
            if (!result)
                Log.d(TAG, "faild to mkdirs");
        }
        return pathFile.toString();
    }

    public void showTimerPickerDialog() {
        if (isDestroyed()
                || (mTimePickerFragment != null && mTimePickerFragment.isAdded())) {
            return;
        }
        removeTimePicker();
        if (mTimePickerFragment == null) {
            mTimePickerFragment = new TimePickerFragment();
            mTimePickerFragment.setOnTimeSetListener(new TimeAndDurationPickerDialog.OnTimeSetListener() {
                @Override
                public void onTimeSet(TimePicker view, int hourOfDay, int minute, int duration) {
                    Log.d(TAG, "hour of day:" + hourOfDay + ",minute:" + minute + ",duration:" + duration);
                    if (duration != 0) {
                        mSettingPreference.mRecordTime =
                                new RecordTimer(SettingActivity.this, hourOfDay, minute, duration);
                        mSettingPreference.updateTimerSetPreference();
                    }
                }
            });
        }
        mTimePickerFragment.showAllowingStateLoss(getFragmentManager(), FRAG_TAG_TIME_PICKER);
        getFragmentManager().executePendingTransactions();
    }

    private void removeTimePicker() {
        FragmentManager manager = getFragmentManager();
        FragmentTransaction ft = manager.beginTransaction();
        Fragment prev = manager.findFragmentByTag(FRAG_TAG_TIME_PICKER);
        if (prev != null) {
            ft.remove(prev);
            ft.commitAllowingStateLoss();
        }
    }

    public static class RecordTimer implements Serializable {
        public int duration;
        public String timerString;
        public long startTimeMillis;

        public RecordTimer(Context context, int hourOfDay, int minute, int duration) {
            this.duration = duration;
            this.timerString = getSetTimerString(context, hourOfDay, minute, duration);
            this.startTimeMillis = getTimeMillis(hourOfDay, minute, duration);
        }

        public RecordTimer(Context context, long timeMillis, int duration) {
            this.duration = duration;
            this.startTimeMillis = timeMillis;
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            String formatString = format.format(timeMillis);
            Log.d(TAG, "RecordTimer,formatString from timeMillis:" + formatString);
            if (formatString != null) {
                String[] dataformats = formatString.split(" ");
                if (dataformats.length > 1) {
                    String[] hourminute = dataformats[1].split(":");
                    if (hourminute.length > 1) {
                        this.timerString = getSetTimerString(context,
                                Integer.valueOf(hourminute[0]), Integer.valueOf(hourminute[1]), duration);
                        Log.d(TAG, "RecordTimer from timeMillis timerString:" + timerString);
                    }
                }

            }
        }

        private long getTimeMillis(int hourOfDay, int minute, int duration) {
            Calendar c = Calendar.getInstance();
            int nowHour = c.get(Calendar.HOUR_OF_DAY);
            int nowMinute = c.get(Calendar.MINUTE);
            if (hourOfDay < nowHour
                    || (hourOfDay == nowHour && minute <= nowMinute)) {
                Log.d(TAG, "setTimerRecord add 1 day");
                c.add(Calendar.DAY_OF_YEAR, 1);
            }
            c.set(Calendar.HOUR_OF_DAY, hourOfDay);
            c.set(Calendar.MINUTE, minute);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            return c.getTimeInMillis();
        }

        public Set<String> getStringSet() {
            Set<String> stringSet = new HashSet<>();
            stringSet.add(String.valueOf(startTimeMillis));
            stringSet.add(String.valueOf(duration));
            return stringSet;
        }

        private static String getSetTimerString(Context context, int hourOfDay, int minute, int duration) {
            String hourString = hourOfDay < 10 ? "0" + hourOfDay : "" + hourOfDay;
            String minuteString = minute < 10 ? "0" + minute : "" + minute;
            String durationString;
            if (duration / 60 == 0) {
                durationString = "" + (float) duration / 60;
            } else {
                durationString = "" + duration / 60;
            }
            String[] durationHours = context.getResources().getStringArray(R.array.duration_hours);
            int index = duration > 60 ? 1 : 0;
            String timerString = String.format(
                    context.getString(R.string.timer_recording_detail),
                    hourString, minuteString, durationString + durationHours[index]);
            return timerString;
        }
    }


    public static void saveStartRecordTime(Context context, RecordTimer setTimer) {
        SharedPreferences timerRecordPreferences = context.getSharedPreferences(
                SOUNDREOCRD_TYPE_AND_DTA, MODE_PRIVATE);
        SharedPreferences.Editor edit = timerRecordPreferences.edit();
        Log.d(TAG, "save record timer:" + setTimer);
        if (setTimer != null) {
            Log.d(TAG, "save record timer,startMillis:" + setTimer.startTimeMillis + ",duration:" + setTimer.duration);
            edit.putInt(TIMER_RECORD_DURATION, setTimer.duration);
            edit.putLong(TIMER_RECORD_TIME, setTimer.startTimeMillis);
        } else {
            edit.putInt(TIMER_RECORD_DURATION, 0);
            edit.putLong(TIMER_RECORD_TIME, 0);
        }

        edit.commit();
    }

    public static RecordTimer getStartRecordTime(Context context) {
        SharedPreferences RecordPreferences = context.getSharedPreferences(
                SOUNDREOCRD_TYPE_AND_DTA, MODE_PRIVATE);
        int duration = RecordPreferences.getInt(TIMER_RECORD_DURATION, 0);
        long startTimeMillis = RecordPreferences.getLong(TIMER_RECORD_TIME, 0);
        if (duration != 0) {
            Log.d(TAG, "duration:" + duration + ",startTimeMillis:" + startTimeMillis);
            return new RecordTimer(context, startTimeMillis, duration);
        }
        return null;
    }

    public static void setAlarmTimer(Context context,RecordTimer setTimer){
        Intent intent = new Intent(TIMER_RECORD_START_ACTION);
        if (Build.VERSION.SDK_INT >= 26) {
            intent.addFlags(StandardFrameworks.getInstances().getFLagReceiverIncludeBackground());
        }
        intent.putExtra(RecorderReceiver.PREPARE_RECORD, true);
        PendingIntent pendingIntent = PendingIntent.getService(
                context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Log.d(TAG,"setTimerRecord ="+setTimer.startTimeMillis);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, setTimer.startTimeMillis, pendingIntent);
    }
}
