package com.sprd.soundrecorder;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
//import androidx.appcompat.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothA2dp;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Toast;
import android.os.Handler;



import com.android.soundrecorder.R;
import java.util.ArrayList;
import java.util.List;


public class RecordingFileListTabUtils extends AppCompatActivity implements ServiceConnection {
    private static final String TAG = "FileListTabUtils";
    private ViewPager mPageVp;
    private SlidingTabLayout mtabs;
    private Toolbar mtoolbar;
    private MenuItem mMeunVolumeMode = null;
    public static boolean isReceive = false;
    private List<Fragment> mFragmentList = new ArrayList<Fragment>();
    private List<String> mTitle = new ArrayList<String>();
    private FragmentAdapter mFragmentAdapter;
    private LocalRecordFragment localrecord;
    public final static String SYSTEM_VERSION = "systemVersion";
    private CallRecordFragment callrecord;
    private static final int START_RECORDING_DIALOG_SHOW = 1;
    public final static String SAVE_RECORD_TYPE = "recordType";
    public final static String SAVE_RECORD_TYPE_ID = "recordTypeId";
    public final static String SOUNDREOCRD_TYPE_AND_DTA = "soundrecord.type.and.data";
    private final static int INITIALPAGE = 0;
    private boolean mIsTabPause =false;
    private Handler mHandlerSpreak = new Handler();

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.activity_main);
        View decorView = getWindow().getDecorView();
        int option = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        decorView.setSystemUiVisibility(option);
        getWindow().setStatusBarColor(Color.parseColor("#00b1d8"));
        initView();
        IntentFilter headsetFilter = new IntentFilter();
        headsetFilter.addAction(Intent.ACTION_HEADSET_PLUG);
        headsetFilter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        headsetFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mHeadsetTabReceiver, headsetFilter);
        setSupportActionBar(mtoolbar);
        mFragmentAdapter = new FragmentAdapter(
                this.getSupportFragmentManager(), mTitle, mFragmentList);
        mPageVp.setAdapter(mFragmentAdapter);
        mtabs.setCustomTabView(R.layout.custom_tab, 0);
        mtabs.setSelectedIndicatorColors(Color.parseColor("#ffffff"));
        mtabs.setViewPager(mPageVp);
        mPageVp.setCurrentItem(INITIALPAGE);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        mtoolbar.setNavigationIcon(R.drawable.ic_ab_back_holo_dark);
        mtoolbar.setOnMenuItemClickListener(onMenuItemClick);
        mtoolbar.setTitleTextColor(Color.parseColor("#ffffff"));
        mtoolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    @Override
    public void onSaveInstanceState(Bundle outcicle) {
        super.onSaveInstanceState(outcicle);
    }

    private void initView() {
        mtabs = (SlidingTabLayout) findViewById(R.id.tabs);
        mPageVp = (ViewPager) this.findViewById(R.id.id_page_vp);
        mtoolbar = (Toolbar) findViewById(R.id.id_toolbar);

        mTitle.add(getResources().getString(R.string.local_record));
        mTitle.add(getResources().getString(R.string.call_record));

        localrecord = new LocalRecordFragment();
        callrecord = new CallRecordFragment();
        mFragmentList.add(localrecord);
        mFragmentList.add(callrecord);
        getReceiveFileType();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        getReceiveFileType();
        mIsTabPause = false;
        if (null != mMeunVolumeMode) {
            if (!isReceive) {
                mMeunVolumeMode.setIcon(R.drawable.ear_speak);
            } else {
                mMeunVolumeMode.setIcon(R.drawable.volume_speak);
            }
        }
    }
    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
        mIsTabPause = true;
    }
    @Override
    protected void onDestroy() {
        unregisterReceiver(mHeadsetTabReceiver);
        super.onDestroy();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {

    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }

    private void saveReceivetype() {
        SharedPreferences recordSavePreferences = this.getSharedPreferences(
                SOUNDREOCRD_TYPE_AND_DTA, MODE_PRIVATE);
        SharedPreferences.Editor edit = recordSavePreferences.edit();
        edit.putBoolean(RecordSetting.FRAG_TAG_VOLUME_IS_RECEIVE, isReceive);
        edit.commit();
    }

    private void getReceiveFileType() {
        SharedPreferences recordSavePreferences = this.getSharedPreferences(
                SOUNDREOCRD_TYPE_AND_DTA, MODE_PRIVATE);
        isReceive = recordSavePreferences.getBoolean(
                RecordSetting.FRAG_TAG_VOLUME_IS_RECEIVE, false);
    }

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
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d(TAG, "onCreateOptionsMenu start");
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.options_menu_overlay, menu);
        Log.d(TAG, "onCreateOptionsMenu end");
        return true;
    }
    private void changeTheReceive(){
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
       if (isReceive){
            am.setMode(AudioManager.MODE_IN_CALL);
            am.setSpeakerphoneOn(false);
        }else{
            am.setMode(AudioManager.MODE_NORMAL);
            am.setSpeakerphoneOn(true);
        }
    }
    private void changeTheSpeaker(){
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am.getMode()!=AudioManager.MODE_NORMAL){
            am.setMode(AudioManager.MODE_NORMAL);
            am.setSpeakerphoneOn(true);
        }
    }
    private final Runnable mHandlerHeadSpreak = new Runnable() {
        public void run() {
                try {
                    AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    if (am.isBluetoothA2dpOn()){
                        changeTheSpeaker();
                    }else {
                       mHandlerSpreak.postDelayed(mHandlerHeadSpreak, 100);
                    }
                } catch (IllegalStateException e) {
                    Log.i(TAG, "run()-IllegalStateException");
                    return;
                }
        }
    };
    private BroadcastReceiver mHeadsetTabReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_HEADSET_PLUG)) {
                int headsetState = intent.getIntExtra("state", -1);
                if (headsetState == 1 && mMeunVolumeMode != null) {
                    mMeunVolumeMode.setVisible(false);
                } else if (mMeunVolumeMode != null) {
                    //mMeunVolumeMode.setVisible(true);
                }
            }else if (BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {//bug 768746
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if(BluetoothProfile.STATE_DISCONNECTED == adapter.getProfileConnectionState(BluetoothProfile.HEADSET)) {
                    if (mMeunVolumeMode!=null){
                        Log.d(TAG,"recorddingfileplayTab mMenuItemSpeakdisconnected="+mMeunVolumeMode);
                        if ((localrecord.mPlayer != null && localrecord.mPlayer.isPlaying()) || (callrecord.mPlayer != null && callrecord.mPlayer.isPlaying())){
                            changeTheReceive();
                        }
                       //mMeunVolumeMode.setVisible(true);
                    }
                }else if (BluetoothProfile.STATE_CONNECTED==adapter.getProfileConnectionState(BluetoothProfile.HEADSET)){
                    if (mMeunVolumeMode!=null){
                        mHandlerSpreak.removeCallbacks(mHandlerHeadSpreak);
                        mHandlerSpreak.postDelayed(mHandlerHeadSpreak, 100);               
                        mMeunVolumeMode.setVisible(false);
                    }
                }
        }else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)){
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,BluetoothAdapter.ERROR);
            switch (state) {
        case BluetoothAdapter.STATE_OFF:
            if (mMeunVolumeMode!=null){
                Log.d(TAG,"recorddingfileplay BluetoothAdapter="+mMeunVolumeMode);
                if (isReceive&&!mIsTabPause){//bug 776713
                    AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    am.setMode(AudioManager.MODE_IN_CALL);
                    am.setSpeakerphoneOn(false);
                }
                //mMeunVolumeMode.setVisible(true);
            }
            break;
        }
        }
        }
    };

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        Log.d(TAG, "onPrepareOptionsMenu start");
        mMeunVolumeMode = menu.findItem(R.id.setting);
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        //if (am.isWiredHeadsetOn()||am.isBluetoothA2dpOn()) {
        if (!Utils.isCanSetReceiveMode(this)) {    
            mMeunVolumeMode.setVisible(false);
        }
        if (!isReceive) {
            mMeunVolumeMode.setIcon(R.drawable.ear_speak);
        } else {
            mMeunVolumeMode.setIcon(R.drawable.volume_speak);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    private Toolbar.OnMenuItemClickListener onMenuItemClick = new Toolbar.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            String msg = "";
            switch (menuItem.getItemId()) {
                case R.id.setting:
                    AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    if (!isReceive) {
                        if ((localrecord.mPlayer != null && localrecord.mPlayer.isPlaying()) || (callrecord.mPlayer != null && callrecord.mPlayer.isPlaying())) {
                            am.setMode(AudioManager.MODE_IN_CALL);
                            am.setSpeakerphoneOn(false);
                        }
                        mMeunVolumeMode.setIcon(R.drawable.volume_speak);
                        isReceive = true;
                        saveReceivetype();
                        Toast.makeText(getApplicationContext(), R.string.speak_open,
                                Toast.LENGTH_SHORT).show();
                    } else {
                        if ((localrecord.mPlayer != null && localrecord.mPlayer.isPlaying()) || (callrecord.mPlayer != null && callrecord.mPlayer.isPlaying())) {
                            am.setMode(AudioManager.MODE_NORMAL);
                            am.setSpeakerphoneOn(true);
                        }
                        mMeunVolumeMode.setIcon(R.drawable.ear_speak);
                        isReceive = false;
                        saveReceivetype();
                        Toast.makeText(getApplicationContext(), R.string.speak_close,
                                Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
            return true;
        }
    };
}
 