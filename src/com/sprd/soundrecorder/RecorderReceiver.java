package com.sprd.soundrecorder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import com.sprd.soundrecorder.service.RecordingService;

/**
 * Created by jian.xu on 2017/11/2.
 */

public class RecorderReceiver extends BroadcastReceiver {
    private final static String TAG = RecorderReceiver.class.getSimpleName();
    public final static String PREPARE_RECORD = "StartRecord";
    public final static String MISS_RECORD = "MissRecord";
    public final static String TIME_RECORD = "TimeRecord";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, " onReceive intent = " + intent);
        if (SettingActivity.TIMER_RECORD_START_ACTION.equals(intent.getAction())) {
            Intent startRecord = new Intent(context, RecordingService.class);
            startRecord.putExtra(PREPARE_RECORD, true);
            context.startService(startRecord);
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || Intent.ACTION_LOCALE_CHANGED.equals(intent.getAction())) {
            SettingActivity.RecordTimer recordTimer = SettingActivity.getStartRecordTime(context);
            if (recordTimer != null) {
                Log.d(TAG, "RecordTimer:" + recordTimer.timerString);
                if (recordTimer.startTimeMillis <= System.currentTimeMillis()) {
                    Intent missRecord = new Intent(context, RecordingService.class);
                    missRecord.putExtra(MISS_RECORD, true);
                    if (Build.VERSION.SDK_INT >= 26) {
                        Log.d(TAG, "startForegroundService");
                        context.startForegroundService(missRecord);
                    } else {
                        context.startService(missRecord);
                    }
                } else {
                    Intent timeRecord = new Intent(context, RecordingService.class);
                    timeRecord.putExtra(TIME_RECORD, true);
                    SettingActivity.setAlarmTimer(context, recordTimer);
                    if (Build.VERSION.SDK_INT >= 26) {
                        Log.d(TAG, "startForegroundService");
                        context.startForegroundService(timeRecord);
                    } else {
                        context.startService(timeRecord);
                    }
                }
            }
        }
    }
}
