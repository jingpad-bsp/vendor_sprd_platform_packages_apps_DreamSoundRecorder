package com.sprd.soundrecorder;

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
import android.media.AudioManager;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
//import androidx.appcompat.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Toast;


import com.android.soundrecorder.R;
import com.sprd.soundrecorder.ui.RecordItemFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jian.xu on 2017/10/13.
 */

public class RecordListActivity extends AppCompatActivity {
    private final static String TAG = RecordListActivity.class.getSimpleName();
    private SlidingTabLayout mTabLayout;
    private boolean mNeedRequestPermissions = false;
    private Toolbar mToolbar;
    private ViewPager mPageVp;
    private List<String> mPageTitle = new ArrayList<>();
    private FragmentAdapter mFragmentAdapter;
    private List<Fragment> mFragmentList = new ArrayList<>();
    private MenuItem mReceiverMenu = null;
    private boolean isReceiveMode = false;
    private List<Boolean> isPlayerPlaying = new ArrayList<Boolean>();
    private boolean mFlagSoundRecordItem = false;
    private boolean mFlagCallRecordItem = false;
    private static final String isCallList = "Callrecordlist";
    private static final String FRAGMENTS_TAG = "android:support:fragments";
    private boolean mIsFromCall = false;
    private Toast mToast = null;
    private long mLastTagTime;
    public static final String STAUS_PHONE_STATE = "android.intent.action.PHONE_STATE";
    private static final int HEADSETON = 1;
    private int mStatus = 0;
    private int mBluetoothHeadset = 0;

    public MenuItem getmReceiverMenu() {
        return mReceiverMenu;
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SHUTDOWN.equals(action)){
                Log.d(TAG, "power shut down");
                stopPlayback();
            }
        }
    };

    @Override
    protected void onCreate(Bundle icicle) {
        Log.d(TAG, "onCreate");
        if (icicle != null){
            //1227772 remove saved fragment, will new fragment in mFragmentAdapter
            icicle.remove(FRAGMENTS_TAG);
        }
        super.onCreate(icicle);
        mNeedRequestPermissions = Utils.checkAndBuildPermissions(this);
        if (mNeedRequestPermissions) {
            //bug 1144071 there are two cases when reset app preferences
            SettingActivity.getRecordSavePath(this);
        }
        checkBluetoothStatus();
        registHeadsetReceiver();
        /*if (!Utils.checkAndBuildPermissions(this)) {
            finish();
            return;
        }*/

        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        Intent intent = getIntent();
        if (intent != null) {
            mIsFromCall = intent.getBooleanExtra(isCallList, false);
        }

        setContentView(R.layout.activity_main);
//        View decorView = getWindow().getDecorView();
//        int option = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
//        decorView.setSystemUiVisibility(option);
//        getWindow().setStatusBarColor(getResources().getColor(R.color.status_bar_color));

        initViews();
        setSupportActionBar(mToolbar);

        mFragmentAdapter = new FragmentAdapter(
                this.getSupportFragmentManager(), mPageTitle, mFragmentList);
        mPageVp.setAdapter(mFragmentAdapter);
        mTabLayout.setCustomTabView(R.layout.custom_tab, 0);
        mTabLayout.setSelectedIndicatorColors(getResources().getColor(R.color.menu_color_blue));
        mTabLayout.setViewPager(mPageVp);
        mTabLayout.setFragmentList(mFragmentList);
        if (mIsFromCall) {
            mPageVp.setCurrentItem(1);
        } else {
            mPageVp.setCurrentItem(0);
        }
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        //bug:943183 back icon is not support the mirror shows
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        isPlayerPlaying.add(mFlagSoundRecordItem);
        isPlayerPlaying.add(mFlagCallRecordItem);
        mToolbar.setOnMenuItemClickListener(mMenuItemClick);
        mToolbar.setTitleTextColor(getResources().getColor(R.color.jingos_text));
        mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isExistPlayer()) {
                    stopPlayback();
                    return;
                }
                finish();
            }
        });
        //bug 1212532 stop play and set receive mode when shut down
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_SHUTDOWN);
        registerReceiver(mReceiver, intentFilter);

    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (isExistPlayer()) {
                stopPlayback();
            } else {
                finish();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void registHeadsetReceiver() {
        IntentFilter headsetFilter = new IntentFilter();
        headsetFilter.addAction(Intent.ACTION_HEADSET_PLUG);
        headsetFilter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        headsetFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        headsetFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        headsetFilter.addAction(STAUS_PHONE_STATE);
        registerReceiver(mHeadsetReceiver, headsetFilter);
    }

    private void initViews() {
        mTabLayout = (SlidingTabLayout) findViewById(R.id.tabs);
        mPageVp = (ViewPager) this.findViewById(R.id.id_page_vp);
        mToolbar = (Toolbar) findViewById(R.id.id_toolbar);

        mPageTitle.add(getResources().getString(R.string.local_record));
        mPageTitle.add(getResources().getString(R.string.call_record));

        Fragment localrecord = new RecordItemFragment();
        mFragmentList.add(localrecord);
        RecordItemFragment callrecord = new RecordItemFragment();
        callrecord.setRecordType(RecordItemFragment.RecordType.CALL_RECORD_ITEM);
        mFragmentList.add(callrecord);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d(TAG, "onCreateOptionsMenu start");
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.options_menu_overlay, menu);
        Log.d(TAG, "onCreateOptionsMenu end");
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        Log.d(TAG, "onPrepareOptionsMenu start");
        mReceiverMenu = menu.findItem(R.id.setting);
        if (!Utils.isCanSetReceiveMode(this)) {
            mReceiverMenu.setVisible(false);
        }
        if (!isReceiveMode) {
            mReceiverMenu.setIcon(R.drawable.ear_speak_gray);
        } else {
            mReceiverMenu.setIcon(R.drawable.volume_speak);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    protected void onPause() {
        super.onPause();
        //bug 1130994 not change the play mode when turn off the screen
        if (!Utils.isTopActivity(this, getLocalClassName())) {
            //setReceiveMode(false);
            //bug 1210417 rest receive mode and speaker when go out soundrecorder
            Utils.resetReceiveMode(this);
        }

    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mHeadsetReceiver);
        unregisterReceiver(mReceiver);
        super.onDestroy();
    }

    public void setPlayerPlaying(boolean isPlaying) {
        int index = mTabLayout.getCurrentPosition();
        isPlayerPlaying.set(index,isPlaying);
    }

    /*bug:985415 the receive mode can be changed when play the record file @{ */
    private boolean isPlaying() {
        List<Fragment> FragmentList = this.getSupportFragmentManager().getFragments();
        for (Fragment fragment : FragmentList) {
            if (((RecordItemFragment) fragment).isPlayingStatus()){
                return true;
            }
        }
        return false;
    }
    /*}@*/

    //BUG:901175 set flag about the state of play according to the recordtype
    public void setPlayerPlaying(boolean isPlaying, int index ) {
        isPlayerPlaying.set(index, isPlaying);
    }

    public void setReceiveMode(boolean enable) {
       Log.d(TAG, "setReceiveMode,isReceiveMode:" + isReceiveMode + ",enable:" + enable + ",isPlaying():" + isPlaying());
       if (enable) {
            if (!isReceiveMode && isPlaying() && Utils.isCanSetReceiveMode(this)) {
                mReceiverMenu.setIcon(R.drawable.volume_speak);
                Utils.setPlayInReceiveMode(this, true);
                isReceiveMode = true;
                Toast.makeText(getApplicationContext(), R.string.speak_open,
                        Toast.LENGTH_SHORT).show();
            }
        } else {
            if (isReceiveMode && !Utils.isInCallState(this)) {
                Utils.setPlayInReceiveMode(this, false);
                isReceiveMode = false;
                updateReceiverModeMenuIcon();
                Toast.makeText(getApplicationContext(), R.string.speak_close,
                        Toast.LENGTH_SHORT).show();
            }
        }

    }

    public boolean isReceiveModeList() {
        return isReceiveMode;
    }

    private void stopPlayback() {
        List<Fragment> FragmentList = this.getSupportFragmentManager().getFragments();
        for (Fragment fragment : FragmentList) {
            ((RecordItemFragment) fragment).clearPlayer();
        }
    }
    private void pausePlayback() {
        List<Fragment> FragmentList = this.getSupportFragmentManager().getFragments();
        for (Fragment fragment : FragmentList) {
            ((RecordItemFragment) fragment).pausePlayback();
        }
    }
    private boolean isPause() {
        List<Fragment> FragmentList = this.getSupportFragmentManager().getFragments();
        for (Fragment fragment : FragmentList) {
            if (((RecordItemFragment) fragment).isPauseStatus()){
                return true;
            }
        }
        return false;
    }

    private boolean isExistPlayer(){
        List<Fragment> FragmentList = this.getSupportFragmentManager().getFragments();
        for (Fragment fragment : FragmentList) {
            if (((RecordItemFragment) fragment).isExistPreviewPlayer()){
                return true;
            }
        }
        return false;
    }

    private BroadcastReceiver mHeadsetReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            Log.d(TAG, "mHeadsetReceiver,action:" + action);
            if (mReceiverMenu == null) {
                //bug 982865 prevent broadcasts from being sent out early
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
                        mReceiverMenu.setVisible(false);
                    } else {
                        mStatus = 0;
                        if (mBluetoothHeadset != HEADSETON) {
                            //mReceiverMenu.setVisible(true);
                        }
                    }
                    break;
                case AudioManager.ACTION_AUDIO_BECOMING_NOISY:
                    pausePlayback();
                    break;
                case BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED:
                    int connectionstate = intent.getIntExtra(BluetoothProfile.EXTRA_STATE,
                        BluetoothProfile.STATE_DISCONNECTED);
                    if (connectionstate == BluetoothProfile.STATE_DISCONNECTED) {
                        if (mStatus != HEADSETON) {
                            //mReceiverMenu.setVisible(true);
                        }
                        mBluetoothHeadset = 0;
                    } else if (connectionstate == BluetoothProfile.STATE_CONNECTED) {
                        pausePlayback();
                        setReceiveMode(false);
                        mReceiverMenu.setVisible(false);
                        mBluetoothHeadset = 1;
                    }
                    break;
                case BluetoothAdapter.ACTION_STATE_CHANGED:
                    int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                    if (state == BluetoothAdapter.STATE_OFF) {
                        if (am.isBluetoothA2dpOn()){
                            setReceiveMode(false);
                            //mReceiverMenu.setVisible(true);
                        }
                    }
                    break;
                case STAUS_PHONE_STATE:
                    // bug 999489,1109493 get Wired and bluetooth headset status
                    if (!Utils.isInCallState(getApplicationContext())&& mStatus != HEADSETON && mBluetoothHeadset != HEADSETON){
                        //bug 1180271 when hand up the phone ,return to the default play mode if the previewplayer is null
                        if (!isPlaying() && !isPause()) {
                            setReceiveMode(false);
                        }
                        //mReceiverMenu.setVisible(true);
                    }else {
                        mReceiverMenu.setVisible(false);
                    }
                    break;
            }
        }
    };

    /* bug 1109493 check the connection status of the bluetooth headset @{ */
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

    private Toolbar.OnMenuItemClickListener mMenuItemClick
            = new Toolbar.OnMenuItemClickListener() {

        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            String msg = "";
            switch (menuItem.getItemId()) {
                case R.id.setting:
                    if ((SystemClock.elapsedRealtime() - mLastTagTime) > 1000){
                        if (!isReceiveMode) {
                            setReceiveMode(true);
                        } else {
                            setReceiveMode(false);
                        }
                        mLastTagTime = SystemClock.elapsedRealtime();
                    }else if (isPlaying()){
                        mToast = Utils.showToastWithText(RecordListActivity.this, mToast, getString(R.string.mode_change), Toast.LENGTH_SHORT);
                    }
                    break;
            }
            return true;
        }
    };

    public class FragmentAdapter extends FragmentPagerAdapter {
        List<Fragment> fragmentList = new ArrayList<Fragment>();
        private List<String> title;

        public FragmentAdapter(FragmentManager fm, List<String> title,
                               List<Fragment> fragmentList) {
            super(fm);
            this.title = title;
            this.fragmentList = fragmentList;
        }

        @Override
        public Fragment getItem(int position) {
            Log.d(TAG, "FragmentAdapter get item:" + position);
            return fragmentList.get(position);
        }

        @Override
        public int getCount() {
            return fragmentList.size();
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return title.get(position);
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
        if (resultsAllGranted) {
            mNeedRequestPermissions = false;
        } else {
            new AlertDialog.Builder(this)
                    //.setTitle(getResources().getString(R.string.error_app_internal))
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




    /*bug 1178979 Receiver Mode Menu should be gray if it is not used }@ */
    public void updateReceiverModeMenuIcon() {
        int playFragmentIndex = 0;
        for (int i = 0; i < isPlayerPlaying.size(); i++) {
            if (isPlayerPlaying.get(i).booleanValue()) {
                playFragmentIndex = i;
            }
        }
        Log.d(TAG, "playFragmentIndex:" + playFragmentIndex);
        Fragment fragment = mFragmentAdapter.getItem(playFragmentIndex);
        if (!(fragment instanceof RecordItemFragment)) {
            return;
        }
        RecordItemFragment playingFragment = (RecordItemFragment) fragment;
        if (mReceiverMenu != null) {
            Log.d(TAG, "isReceiveMode:" + isReceiveMode);
            if (playingFragment.getmPreviewPlayer() == null || !isReceiveMode && playingFragment.isPauseStatus()) {
                mReceiverMenu.setIcon(R.drawable.ear_speak_gray);
            } else if (!isReceiveMode && playingFragment.isPlayingStatus()) {
                mReceiverMenu.setIcon(R.drawable.ear_speak);
            }
        }
    }
    /*}@ */

}
