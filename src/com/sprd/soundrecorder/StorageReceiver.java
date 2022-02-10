package com.sprd.soundrecorder;

import com.android.soundrecorder.SoundRecorder;
import com.android.soundrecorder.RecordService;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.app.Notification;
import android.app.NotificationManager;
import com.sprd.soundrecorder.RecordSetting;
import com.android.soundrecorder.R;
import android.app.PendingIntent;
public class StorageReceiver extends BroadcastReceiver {

    private final static String TAG = "StorageReceiver";
    @Override
    public void onReceive(Context context, Intent intent) {
    Log.d("StorageReceiver", " onReceive intent = " + intent);
        if (Intent.ACTION_MEDIA_MOUNTED.equals(intent.getAction())){
            RecordSetting.changeStorePath(context);
        }
    }
}