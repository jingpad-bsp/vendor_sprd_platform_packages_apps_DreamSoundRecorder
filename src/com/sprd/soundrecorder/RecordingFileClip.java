package com.sprd.soundrecorder;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.soundrecorder.R;
import com.android.soundrecorder.RecordService;
import com.android.soundrecorder.SoundRecorder;
import com.coremedia.iso.boxes.Container;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.authoring.tracks.AppendTrack;
import com.googlecode.mp4parser.authoring.tracks.CroppedTrack;
import com.sprd.soundrecorder.data.WaveDataManager;
import com.sprd.soundrecorder.frameworks.StandardFrameworks;
import com.sprd.soundrecorder.ui.ClipWavesView;
import com.sprd.soundrecorder.ui.CreateSaveDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by donghua.wu on 2017/6/30.
 */

public class RecordingFileClip extends Activity {
    private static final String TAG = "RecordingFileClip";
    private static final String SAVE_PATH = Environment.getExternalStorageDirectory() + "/recordings/mp4Clip/";
    private static final int SAVE_FILE = 1;
    private static final int UPDATE_WAVE = 2;
    private static final int FADEDOWN = 3;
    private static final int FADEUP = 4;
    private static final int RECORD_PERMISSIONS_REQUEST_CODE = 200;

    private RelativeLayout mRelativeLayout;
    private ClipWavesView mClipWavesView;
    private Context mContext;
    private TextView mStartTime;
    private TextView mEndTime;
    private TextView mClipTime;
    private ImageButton mPlayButton;

    private String mTimerFormat;
    private String mRecordTitle;
    private String mTypeFromPath;
    private String mMimeType;
    private String mPath;
    private String timeStr;
    private File mTrimFilePath;
    private boolean moveStartLine = false;
    private boolean moveEndLine = false;
    private boolean mNeedRequestPermissions = false;
    private boolean mPausedByTransientLossOfFocus = false;
    private boolean mPlayFromFocusCanDuck = false;
    private Long mRecordID;
    private int startX = 0;
    private int moveX = 0;
    private int mHalfBitmapWidth;
    private int maxWidth;
    private int mRecordDuration;
    private int mSeekPosition;
    private int spaceTime = 0;
    private int mClipStartTime = 0;
    private int mClipEndTime = 0;
    private float startTimeValue;
    private float endTimeValue;
    private float clipTimeValue;
    private long sampleLengthMillis;

    private Toast mToast = null;
    private ClipFilePlayer mPlayer = null;
    private RecordService mRecordService;
    private AudioManager mAudioManager;
    private ActionBar actionBar;

    private MenuItem mMenuItemClip = null;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        getWindow().requestFeature(Window.FEATURE_ACTION_BAR);
        setContentView(R.layout.recording_file_clip);
        actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setElevation(0);
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mContext = getApplicationContext();
        mTrimFilePath = new File(SAVE_PATH);
        if (!mTrimFilePath.exists() || !mTrimFilePath.isDirectory()) {
            //bug 1186165 Coverity scan: Exceptional return value of java.io.File.mkdirs() ignored.
            if (!mTrimFilePath.mkdirs()) {
                Log.d(TAG, "mkdirs fail:"+ SAVE_PATH);
            }
        }
        IntentFilter iFilter = new IntentFilter();
        iFilter.addAction(Intent.ACTION_LOCALE_CHANGED);
        registerReceiver(mReceiver, iFilter);
        initResource();
    }

    private void initResource() {
        mPlayButton = (ImageButton) findViewById(R.id.play_button);
        mStartTime = (TextView) findViewById(R.id.start_time);
        mEndTime = (TextView) findViewById(R.id.stop_time);
        mClipTime = (TextView) findViewById(R.id.clip_time);

        mTimerFormat = getResources().getString(R.string.timer_format);
        timeStr = String.format(mTimerFormat, 0, 0, 0);
        mStartTime.setText(timeStr);
        mEndTime.setText(timeStr);
        mClipTime.setText(timeStr);

        mRelativeLayout = (RelativeLayout) findViewById(R.id.wavesLayout);
        mClipWavesView = new ClipWavesView(this);
        mRelativeLayout.addView(mClipWavesView);
        mClipWavesView.isRecordPlay = false;
        mClipWavesView.isFirstIn = true;
        mHalfBitmapWidth = mClipWavesView.mBitmap.getWidth() / 2;

        Intent intent = getIntent();
        mMimeType = intent.getStringExtra("mime_type");
        mPath = intent.getStringExtra("path");
        mTypeFromPath = mPath.substring(mPath.lastIndexOf("."));
        mRecordTitle = intent.getStringExtra("title");
        mRecordID = intent.getLongExtra("id", 0);
        mRecordDuration = intent.getIntExtra("duration", 0);
        Log.d(TAG, "mRecordTitle=" + mRecordTitle + ", mRecordID="
                + mRecordID + ", mRecordDuration=" + mRecordDuration);
        actionBar.setTitle(mRecordTitle + getResources().getString(R.string.clip_name_suffix));

        mClipEndTime = mRecordDuration;
        mEndTime.setText(Utils.makeTimeString4MillSec(RecordingFileClip.this, mClipEndTime));
        mClipTime.setText(Utils.makeTimeString4MillSec(RecordingFileClip.this,
                mClipEndTime - mClipStartTime));
        queryWaveAysnc(mRecordTitle);
        mPlayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playClipFile();
            }
        });

        mClipWavesView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (mClipWavesView.mWaveDataList.size() == 0) return true;
                maxWidth = mClipWavesView.getMeasuredWidth() - mHalfBitmapWidth;

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = (int) event.getX();
                        if (Math.abs(startX - mClipWavesView.startLineX) <= 50) {
                            moveStartLine = true;
                            moveEndLine = false;
                        } else if (Math.abs(startX - mClipWavesView.endLineX) <= 50) {
                            moveEndLine = true;
                            moveStartLine = false;
                        } else {
                            moveStartLine = false;
                            moveEndLine = false;
                        }
                        break;
                    case MotionEvent.ACTION_MOVE:
                        moveX = (int) event.getX();
                        if (moveStartLine && !moveEndLine) {
                            if (moveX <= 0) {
                                mClipWavesView.startLineX = mHalfBitmapWidth;
                            } else if (mClipWavesView.endLineX - moveX >= 100) {
                                mClipWavesView.startLineX = moveX + mHalfBitmapWidth;
                                if (mClipWavesView.isRecordPlay && mClipWavesView.startLineX > mClipWavesView.playingLineX) {
                                    mClipWavesView.isRecordPlay = false;
                                    if (mPlayer != null && mPlayer.isPlaying()) {
                                        mPlayer.pause();
                                        mSeekPosition = (int) (startTimeValue + 0.5);
                                        updatePlayPause();
                                        stopPlayback();
                                    }
                                }
                            }
                        } else if (moveEndLine && !moveStartLine) {
                            if (moveX >= maxWidth) {
                                mClipWavesView.endLineX = maxWidth;
                            } else if (moveX - mClipWavesView.startLineX >= 100) {
                                mClipWavesView.endLineX = moveX;
                                if (mClipWavesView.isRecordPlay && moveX < mClipWavesView.playingLineX) {
                                    mClipWavesView.isRecordPlay = false;
                                }
                            }
                        }
                        mClipWavesView.invalidate();
                        updateTimeView();
                        break;
                    case MotionEvent.ACTION_UP:
                        mSeekPosition = (int) (startTimeValue + 0.5);
                        break;
                }
                return true;
            }
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    public void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        unregisterReceiver(mReceiver);
        stopPlayback();
        super.onDestroy();
    }


    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive  intent.getAction(): " + intent.getAction());
            if (intent.getAction().equals(Intent.ACTION_LOCALE_CHANGED)) {
                stopPlayback();
            }
        }
    };

    private void stopPlayback() {
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
            mAudioManager.abandonAudioFocus(mAudioFocusListener);
        }
    }

    private void updateTimeView() {
        startTimeValue = (float) mClipWavesView.startLineX / maxWidth * mRecordDuration;
        if (startTimeValue < 500) {
            mStartTime.setText(timeStr);
        } else {
            mStartTime.setText(Utils.makeTimeString4MillSec(RecordingFileClip.this, (int) (startTimeValue + 0.5)));
        }
        endTimeValue = (float) mClipWavesView.endLineX / maxWidth * mRecordDuration;
        mEndTime.setText(Utils.makeTimeString4MillSec(RecordingFileClip.this, (int) (endTimeValue + 0.5)));
        clipTimeValue = endTimeValue - startTimeValue;
        mClipTime.setText(Utils.makeTimeString4MillSec(RecordingFileClip.this, (int) (clipTimeValue + 0.5)));
    }

    private void queryWaveAysnc(final String title) {
        AsyncTask<Void, Long, Void> task = null;
        if (task == null) {
            task = new AsyncTask<Void, Long, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    if (mClipWavesView != null) {
                        mClipWavesView.mWaveDataList = WaveDataManager.getWaveData(getApplicationContext(), title, mMimeType);
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Void result) {
                    mClipWavesView.invalidate();
                }
            };
        }
        task.execute((Void[]) null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        Log.e(TAG, "onActivityResult  requestCode= " + requestCode + " resultCode= " + resultCode);
        switch (requestCode) {
            case SAVE_FILE:
                if (resultCode == RESULT_OK) {
                    boolean checkState = intent.getBooleanExtra("check_state", true);
                    String newName = intent.getStringExtra("new_name");
                    newName = newName + mTypeFromPath;
                    File fileCheck = new File(mTrimFilePath + "/" + newName);
                    if (fileCheck.exists()) {
                        Toast.makeText(mContext, getString(R.string.clip_file_exit), Toast.LENGTH_LONG).show();
                        showSaveDialog();
                        return;
                    }
                    trimFile(mPath, startTimeValue / 1000.0, endTimeValue / 1000.0, newName);
                    if (!checkState) {
                        Log.e(TAG, "delete the original record file ...");
                        deleteFileAysnc();
                    }
                }
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.clip_save_menu, menu);
        mMenuItemClip = menu.findItem(R.id.item_save);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                if (mPlayer != null) {
                    stopPlayback();
                }
                onBackPressed();
                break;
            case R.id.item_save:
                showSaveDialog();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showSaveDialog() {
        String clipNewName = actionBar.getTitle().toString();
        String temp = "";
        File newFile = new File(mTrimFilePath + "/" + clipNewName + mTypeFromPath);
        for (int i = 1; i <= 1000; i++) {
            if (newFile.exists()) {
                temp = clipNewName + i;
                newFile = new File(mTrimFilePath + "/" + temp + mTypeFromPath);
            } else {
                if (!temp.isEmpty()) {
                    clipNewName = temp;
                }
                break;
            }
        }
        Intent intent = new Intent();
        intent.putExtra("title", clipNewName);
        intent.setClass(RecordingFileClip.this, CreateSaveDialog.class);
        startActivityForResult(intent, SAVE_FILE);
    }

    private void playClipFile() {
        if (mPlayer == null) {
            //bug 629393 reset application preferencesonce again into the play is error begin
            mNeedRequestPermissions = checkAndBuildPermissions();
            if (mNeedRequestPermissions) {
                return;
            }
            mPlayer = new ClipFilePlayer();
            mPlayer.setActivity(RecordingFileClip.this);
            try {
                Uri uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        mRecordID);
                mPlayer.setDataSourceAndPrepare(uri);
            } catch (Exception ex) {
                Log.d(TAG, "Failed to open file: " + ex);
                if (mPlayer != null) {// bug 664719 click the playbutton the background playing music is stop
                    mPlayer.release();
                    mPlayer = null;
                }
                if (mToast == null) {//bug 678855 the toast show too long
                    mToast = Toast.makeText(RecordingFileClip.this,
                            R.string.playback_failed, Toast.LENGTH_SHORT);
                }
                mToast.show();
                return;
            }
        } else {
            if (mPlayer.isPlaying()) {
                mSeekPosition = mPlayer.getCurrentPosition();
                mPlayer.pause();
            } else {
                start();
            }
            updatePlayPause();
        }
    }

    public void onCompletion() {
        updatePlayPause();
        stopPlayback();//bug 669664 the soundrecorder is not play when the play is completion
    }

    public void start() {
        //bug 1182418 Coverity scan: Dereference before null check
        if (mPlayer == null || !mPlayer.isPrepared()) {//bug 665106 the big recording file is play error
            return;
        }
        if (!requestAudioFocus()) {
            Toast.makeText(this.getApplicationContext(),
                    R.string.no_allow_play_calling, Toast.LENGTH_SHORT).show();
            return;
        }
        if (mPlayer != null) {
            mPlayer.seekTo(mSeekPosition);
        }
        mClipWavesView.isRecordPlay = true;
        mPlayer.start();
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
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    if (mPlayer.isPlaying()) {
                        mPausedByTransientLossOfFocus = true;
                        mPlayer.pause();
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
                if (mClipWavesView.mWaveDataList.size() != 0) {
                    spaceTime = mPlayer.mDuration / (2 * mClipWavesView.mWaveDataList.size()) - 2;
                    Message msg = new Message();
                    msg.what = UPDATE_WAVE;
                    mHandler.sendMessage(msg);
                }
                mPlayButton.setImageResource(R.drawable.suspended);
                mPlayButton.setContentDescription(getResources().getString(R.string.pause));
            } else {
                mPlayButton.setImageResource(R.drawable.play);
                if (mPlayer.mIsPrepared) {
                    mPlayButton.setContentDescription(getResources().getString(R.string.resume_play));
                } else {
                    mPlayButton.setContentDescription(getResources().getString(R.string.start_play));
                }
            }
        }
    }

    public void updateWavesView() {
        if (mPlayer != null) {
            if (mPlayer.isPlaying()) {
                if (mClipWavesView.mWaveDataList.size() != 0) {
                    mClipWavesView.playingLineX = mClipWavesView.startLineX +
                            (int) (maxWidth * (mPlayer.getCurrentPosition() - startTimeValue) / mRecordDuration);
                    mClipWavesView.invalidate();
                    mHandler.removeCallbacks(mUpdateWavesView);
                    mHandler.postDelayed(mUpdateWavesView, spaceTime);
                }
                if (endTimeValue != 0 && mPlayer.getCurrentPosition() >= endTimeValue) {
                    mPlayer.pause();
                    mSeekPosition = (int) (startTimeValue + 0.5);
                    updatePlayPause();
                    stopPlayback();
                }
            }
        } else {
            mClipWavesView.isRecordPlay = false;
            mClipWavesView.invalidate();
        }
    }

    private final Runnable mUpdateWavesView = new Runnable() {
        public void run() {
            updateWavesView();
        }
    };

    /**
     * Trims a file to get a result of the file from startTime to endTime.
     */

    private void trimFile(String filePath, double startTime, double endTime, String newName) {
        Log.d(TAG, "trimFile start: startTime:" + startTime + ", endTime:" + endTime);
        try {
            File inputFile = new File(filePath);
            Movie movie = MovieCreator.build(inputFile.getPath());
            List<Track> tracks = movie.getTracks();
            movie.setTracks(new LinkedList<Track>());
            double startTimeAfterSync = startTime;
            double endTimeAfterSync = endTime;
            boolean timeCorrected = false;
            for (Track track : tracks) {
                if (track.getSyncSamples() != null && track.getSyncSamples().length > 0) {
                    if (timeCorrected) {
                        // This exception here could be a false positive in case we have multiple tracks
                        // with sync samples at exactly the same positions. E.g. a single movie containing
                        // multiple qualities of the same video (Microsoft Smooth Streaming file)

                        throw new RuntimeException("The startTime has already been corrected by another track with SyncSample. Not Supported.");
                    }
                    startTimeAfterSync = correctTimeToSyncSample(track, startTimeAfterSync, false);
                    endTimeAfterSync = correctTimeToSyncSample(track, endTimeAfterSync, true);
                    timeCorrected = true;
                }
            }
            for (Track track : tracks) {
                long currentSample = 0;
                double currentTime = 0;
                double lastTime = -1;
                long startSample1 = -1;
                long endSample1 = -1;

                for (int i = 0; i < track.getSampleDurations().length; i++) {
                    long delta = track.getSampleDurations()[i];
                    if (currentTime > lastTime && currentTime <= startTimeAfterSync) {
                        // current sample is still before the new starttime
                        startSample1 = currentSample;
                    }
                    if (currentTime > lastTime && currentTime <= endTimeAfterSync) {
                        // current sample is after the new start time and still before the new endtime
                        endSample1 = currentSample;
                    }
                    lastTime = currentTime;
                    currentTime += (double) delta / (double) track.getTrackMetaData().getTimescale();
                    currentSample++;
                }
                movie.addTrack(new AppendTrack(new CroppedTrack(track, startSample1, endSample1)));
            }
            long start1 = System.currentTimeMillis();
            Container out = new DefaultMp4Builder().build(movie);
            long start2 = System.currentTimeMillis();
            File output = new File(mTrimFilePath + "/" + newName);
            FileOutputStream fos = new FileOutputStream(mTrimFilePath + "/" + newName);
            FileChannel fc = fos.getChannel();
            out.writeContainer(fc);
            fc.close();
            fos.close();
            long start3 = System.currentTimeMillis();
            insertFileAysnc(output);

            Log.d(TAG, "Building IsoFile took : " + (start2 - start1) + "ms");
            Log.d(TAG, "Writing IsoFile took  : " + (start3 - start2) + "ms");
        } catch (Exception e) {
            Toast.makeText(mContext, R.string.save_failed, Toast.LENGTH_LONG).show();
            Log.e(TAG, "trimFile failed: " + e);
            e.printStackTrace();
        }
    }

    /**
     * Syncs time with nearest sample, since trim can only be done from the start of a sample
     */

    private static double correctTimeToSyncSample(Track track, double cutHere, boolean next) {
        double[] timeOfSyncSamples = new double[track.getSyncSamples().length];
        long currentSample = 0;
        double currentTime = 0;
        for (int i = 0; i < track.getSampleDurations().length; i++) {
            long delta = track.getSampleDurations()[i];
            //bug:1182683 Coverity scan:Calling binarySearch without checking return value
            int index = Arrays.binarySearch(track.getSyncSamples(), currentSample + 1);

            if ( index >= 0) {
                // samples always start with 1 but we start with zero therefore +1
                timeOfSyncSamples[index] = currentTime;
            }
            currentTime += (double) delta / (double) track.getTrackMetaData().getTimescale();
            currentSample++;

        }
        double previous = 0;
        for (double timeOfSyncSample : timeOfSyncSamples) {
            if (timeOfSyncSample > cutHere) {
                if (next) {
                    return timeOfSyncSample;
                } else {
                    return previous;
                }
            }
            previous = timeOfSyncSample;
        }
        return timeOfSyncSamples[timeOfSyncSamples.length - 1];
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

    private Uri addToMediaDB(File file) {
        // SPRD: add
        if (file == null) return null;

        Resources res = getResources();
        ContentValues cv = new ContentValues();
        long current = SystemClock.elapsedRealtime();
        long modDate = file.lastModified();
        String fileName = file.getName();
        String title = fileName.substring(0, fileName.lastIndexOf("."));
        sampleLengthMillis = (int) (endTimeValue - startTimeValue);

        cv.put(MediaStore.Audio.Media.IS_MUSIC, "1");
        cv.put(MediaStore.Audio.Media.TITLE, title);
        cv.put(MediaStore.Audio.Media.DATA, file.getAbsolutePath());
        cv.put(MediaStore.Audio.Media.DATE_ADDED, (int) (current / 1000));
        cv.put(MediaStore.Audio.Media.DATE_MODIFIED, (int) (modDate / 1000));
        cv.put(MediaStore.Audio.Media.DURATION, sampleLengthMillis);
        cv.put(MediaStore.Audio.Media.MIME_TYPE, mMimeType);
        cv.put(MediaStore.Audio.Media.ARTIST,
                res.getString(R.string.audio_db_artist_name));
        cv.put(MediaStore.Audio.Media.ALBUM,
                res.getString(R.string.audio_db_album_name));
        cv.put(StandardFrameworks.getInstances().getMediaStoreAlbumArtist(),
                res.getString(R.string.audio_db_artist_name));
        cv.put(MediaStore.Audio.Media.COMPOSER, SoundRecorder.COMPOSER);
        //SPRD: add tag to MediaDB
        //cv.put(MediaStore.Audio.Media.BOOKMARK, mTagNumber);
        Log.d(TAG, "Inserting audio record: " + cv.toString());
        ContentResolver resolver = getContentResolver();
        Uri base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Log.d(TAG, "ContentURI: " + base);

        Uri result = null;
        final String[] ids = new String[]{MediaStore.Audio.Playlists._ID};
        final String where = MediaStore.Audio.AudioColumns.DATA + "=?";
        final String[] args = new String[]{file.getAbsolutePath()};
        Cursor cursor = null;
        try {
            cursor = this.getContentResolver().query(base, ids, where, args, null);
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
        if (cursor != null) {
            int id = -1;
            if (cursor.getCount() >= 1) {
                Log.v(TAG, "database has this record");
                cursor.moveToFirst();
                id = cursor.getInt(0);
                result = ContentUris.withAppendedId(base, id);
                resolver.update(result, cv, where, args);
            } else {
                Log.v(TAG, "insert this record");
                result = resolver.insert(base, cv);
            }
            cursor.close();
        } else {
            Log.v(TAG, "cursor null insert this record");
            result = resolver.insert(base, cv);
        }

        if (result == null) {
            Toast.makeText(getApplicationContext(), R.string.error_mediadb_new_record, Toast.LENGTH_SHORT)
                    .show();
            return null;
        }
        if (getPlaylistId(res) == -1) {
            createPlaylist(res, resolver);
        }
        int audioId = Integer.valueOf(result.getLastPathSegment());
        addToPlaylist(resolver, audioId, getPlaylistId(res));

        // Notify those applications such as Music listening to the
        // scanner events that a recorded audio file just created.
        sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, result));
        return result;
    }

    private int getPlaylistId(Resources res) {
        Uri uri = MediaStore.Audio.Playlists.getContentUri("external");
        final String[] ids = new String[]{MediaStore.Audio.Playlists._ID};
        final String where = MediaStore.Audio.Playlists.NAME + "=?";
        final String[] args = new String[]{"My recordings"};
        Cursor cursor = this.getContentResolver().query(uri, ids, where, args, null);

        //bug:1182028 Coverity scan: Calling a method on null object cursor
        int id = -1;
        if (cursor != null) {
            cursor.moveToFirst();
            if (!cursor.isAfterLast()) {
                id = cursor.getInt(0);
            }
            cursor.close();
        } else {
            Log.d(TAG, "query returns null");
        }

        return id;
    }

    private Uri createPlaylist(Resources res, ContentResolver resolver) {
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Audio.Playlists.NAME, "My recordings");
        Uri uri = resolver.insert(MediaStore.Audio.Playlists.getContentUri("external"), cv);
        if (uri == null) {
            Toast.makeText(getApplicationContext(), R.string.error_mediadb_new_record, Toast.LENGTH_SHORT)
                    .show();
        }
        return uri;
    }

    private void addToPlaylist(ContentResolver resolver, int audioId, long playlistId) {
        String[] cols = new String[]{
                "count(*)"
        };
        Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId);
        Cursor cur = resolver.query(uri, cols, null, null, null);
        cur.moveToFirst();
        final int base = cur.getInt(0);
        cur.close();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, Integer.valueOf(base + audioId));
        values.put(MediaStore.Audio.Playlists.Members.AUDIO_ID, audioId);
        resolver.insert(uri, values);
    }

    private void insertFileAysnc(final File file) {
        AsyncTask<Void, Long, Uri> task = new AsyncTask<Void, Long, Uri>() {
            @Override
            protected Uri doInBackground(Void... params) {
                Uri uri = addToMediaDB(file);
                return uri;
            }

            @Override
            protected void onProgressUpdate(Long... values) {
            }

            @Override
            protected void onPostExecute(Uri result) {
                Intent intentToList = new Intent(mContext, RecordingFileListTabUtils.class);
                mContext.startActivity(intentToList);
                /*if (result != null) {
                    String saveSuccess = getResources().getString(R.string.save_successful);
                    saveSuccess = String.format(saveSuccess, file.getAbsolutePath());
                    Toast.makeText(mContext, saveSuccess, Toast.LENGTH_LONG).show();
                }*/
            }
        };
        task.execute();
    }

    private void deleteFileAysnc() {
        AsyncTask<Void, Long, Void> task = new AsyncTask<Void, Long, Void>() {

            @Override
            protected Void doInBackground(Void... params) {
                if (StorageInfos.isExternalStorageMounted() || !StorageInfos.isInternalStorageSupported()) {
                    getContentResolver().delete(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mRecordID), null, null);
                } else {
                    getContentResolver().delete(ContentUris.withAppendedId(MediaStore.Audio.Media.INTERNAL_CONTENT_URI, mRecordID), null, null);
                }
                File del = new File(mPath);
                //bug:1182780 Coverity scan: the code in the if-then branch and after the if statement is identical
                if (del.exists()) {
                    if (!del.delete()) {
                        Log.d(TAG, "doInBackground, file delete fail: " + del.getName());
                    }
                }

                return null;
            }

            @Override
            protected void onProgressUpdate(Long... values) {
            }

            @Override
            protected void onPostExecute(Void result) {
                Log.i(TAG, "delete finished.");
            }
        };
        task.execute();
    }

}
