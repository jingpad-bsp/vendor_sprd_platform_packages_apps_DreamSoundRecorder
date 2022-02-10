package com.sprd.soundrecorder.frameworks;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import android.media.AudioSystem;
import android.os.EnvironmentEx;
import android.os.SystemProperties;
import android.os.storage.VolumeInfo;
import android.media.RingtoneManager;

import android.os.Environment;
import android.os.storage.StorageManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.telephony.TelephonyManager;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Created by jian.xu on 2017/4/1.
 */

public class SprdFramewoks extends StandardFrameworks {

    private static final String TAG = "SprdFramewoks";

    //audio
    @Override
    public boolean isAudioSourceActive(int source) {
        return AudioSystem.isSourceActive(source);
    }

    //mediastore
    @Override
    public String getMediaStoreAlbumArtist() {
        return MediaStore.Audio.Media.ALBUM_ARTIST;
    }

    //storage
    @Override
    public HashMap<String, String> getUsbVolumnInfo(StorageManager storageManager) {
        HashMap<String, String> usbvolumns = new HashMap<>();
        List<VolumeInfo> volumes = storageManager.getVolumes();
        Collections.sort(volumes, VolumeInfo.getDescriptionComparator());
        for (VolumeInfo vol : volumes) {
            File file = vol.getPath();
            if (file != null && Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState(file))) {
                if (vol.disk != null && vol.disk.isUsb()) {
                    usbvolumns.put(storageManager.getBestVolumeDescription(vol), file.getPath());
                }
            }
        }
        return usbvolumns;
    }

    @Override
    public int getDefaultStorageLocation(Context context) {
        return -1;//Settings.Global.getInt(context.getContentResolver(),
                //Settings.Global.DEFAULT_STORAGE_LOCATION, -1);
    }

    @Override
    public String getInternalStoragePathState() {
        return EnvironmentEx.getInternalStoragePathState();
    }

    @Override
    public File getInternalStoragePath() {
        return EnvironmentEx.getInternalStoragePath();
    }

    @Override
    public String getExternalStoragePathState() {
        return EnvironmentEx.getExternalStoragePathState();
    }

    @Override
    public File getExternalStoragePath() {
        return EnvironmentEx.getExternalStoragePath();
    }

    //system
    @Override
    public String getSystemProperties(String name, String default_value) {
        return SystemProperties.get(name, default_value);
    }

    @Override
    public boolean getBooleanFromSystemProperties(String name, boolean default_value) {
        return SystemProperties.getBoolean(name, default_value);
    }
    //tele
    @Override
    public boolean getTeleHasIccCard(TelephonyManager tm, int slotId) {
        return tm.hasIccCard(slotId);
    }

    @Override
    public void setActualDefaultRingtoneUri(Context context, int type, Uri ringtoneUri, int phoneId) {
        //bug1236667 :not set audio as ringtone correctly
        int ringtoneType = (phoneId == 1) ? RingtoneManager.TYPE_RINGTONE1 : RingtoneManager.TYPE_RINGTONE;
        RingtoneManager.setActualDefaultRingtoneUri(context, ringtoneType, ringtoneUri);
    }

    @Override
    public File[] getUsbStoragePath() {//bug 663417 when connect the otg u volume the first entry filelist is empty
        return EnvironmentEx.getUsbdiskVolumePaths();
    }

    @Override
    public int getFLagReceiverIncludeBackground() {
        return Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND;
    }
}
