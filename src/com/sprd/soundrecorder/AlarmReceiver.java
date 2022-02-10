package com.sprd.soundrecorder;

import com.android.soundrecorder.SoundRecorder;
import com.android.soundrecorder.RecordService;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.os.Build;
import android.app.Notification;
import android.app.NotificationManager;
import com.sprd.soundrecorder.RecordSetting;
import com.android.soundrecorder.R;
import android.app.PendingIntent;
import java.lang.NullPointerException;
public class AlarmReceiver extends BroadcastReceiver {


    private final static String TAG = "AlarmReceiver";
    public final static String PREPARE_RECORD = "StartRecord";
    public final static String MISS_RECORD = "MissRecord";
    private static final int TIMER_STATUS = 2;
    public final static String TIME_RECORD = "TimeRecord";//bug 645211 After the restart time recording time has not yet to the phone status bar regular recording marks disappear
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("AlarmReceiver", " onReceive intent = " + intent);
        if (RecordSetting.TIMER_RECORD_START_ACTION.equals(intent.getAction())) {
            SharedPreferences timerRecordPreferences = context.getSharedPreferences(RecordSetting.SOUNDREOCRD_TYPE_AND_DTA, Context.MODE_PRIVATE);
            long recordStartTime = timerRecordPreferences.getLong(RecordSetting.TIMER_RECORD_TIME, 0);//bug 766440 
            Intent startRecord = new Intent(context, RecordService.class);
            RecordSetting.mSwitchoff = true;//bug 740071 the soundset interface show blank
            startRecord.putExtra(PREPARE_RECORD, true);
            startRecord.putExtra(RecordSetting.TIMER_RECORD_DURATION, intent.getIntExtra(RecordSetting.TIMER_RECORD_DURATION, 0));
            startRecord.putExtra(RecordSetting.TIMER_RECORD_TIME, recordStartTime);
            context.startService(startRecord);
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())||Intent.ACTION_LOCALE_CHANGED.equals(intent.getAction())) {
            SharedPreferences timerRecordPreferences = context.getSharedPreferences(RecordSetting.SOUNDREOCRD_TYPE_AND_DTA, Context.MODE_PRIVATE);
            boolean isChecked = timerRecordPreferences.getBoolean(RecordSetting.TIMER_RECORD_STATUS, false);
            long recordTime = timerRecordPreferences.getLong(RecordSetting.TIMER_RECORD_TIME, 0);
            Log.d(TAG, "receiveBootCompleted, recordTime = "+recordTime+", isChecked = "+isChecked+", System.currentTimeMillis() = "+System.currentTimeMillis());
            /* SPRD: bug595666 Fail to start timer record after reboot phone. @{ */
            if (isChecked) {
                if (recordTime <= System.currentTimeMillis()) {
                    Intent missRecord = new Intent(context, RecordService.class);
                    missRecord.putExtra(MISS_RECORD, true);
                    missRecord.putExtra(RecordSetting.TIMER_RECORD_TIME, recordTime);
                    if (Build.VERSION.SDK_INT >= 26) {//bug 709703 the phone reboot the timer notify crash
                        Log.d(TAG,"startForegroundService");
                        context.startForegroundService(missRecord);
                    }else {
                        context.startService(missRecord);
                    }
                } else {
                    //bug 1209101 coverity scan:Passing null pointer duration to parseFloat, which dereferences it
                    String duration = timerRecordPreferences.getString(RecordSetting.TIMER_RECORD_DURATION, "0");
                    int recordDuration = (int) (Float.parseFloat(duration) * 60);
                    RecordSetting.resetTimerRecord(context, recordTime, recordDuration);
                    //bug 645211 After the restart time recording time has not yet to the phone status bar regular recording marks disappear
                    Intent timeRecord = new Intent(context, RecordService.class);
                    timeRecord.putExtra(TIME_RECORD, true);
                    if (Build.VERSION.SDK_INT >= 26) {//bug 709703 the phone reboot the timer notify crash
                        Log.d(TAG,"startForegroundService");
                        context.startForegroundService(timeRecord);
                    }else {
                        context.startService(timeRecord);
                    }
                    //bug 645211 end
                }
            }
            /* @} */
        } else if (RecordService.CLOSERECORD_ACTION.equals(intent.getAction())){
            Log.d(TAG, "CLOSERECORD_ACTION is:");
            try{
                RecordSetting.cancelTimerRecord(context,true);
                RecordSetting.mSwitchoff = true;
            }catch (NullPointerException e){
                Log.d(TAG, "NullPointerException");
                NotificationManager notificationManager = (NotificationManager) context.getSystemService(context.NOTIFICATION_SERVICE);
                notificationManager.cancel(TIMER_STATUS);
            }
        }else if (Intent.ACTION_SHUTDOWN.equals(intent.getAction())) {//bug 700827
            RecordSetting.isShuntDown = true;
        }
    }
}