package com.sprd.soundrecorder.frameworks;

import android.content.Context;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.File;
import java.util.HashMap;

/**
 * Created by jian.xu on 2017/4/1.
 */

public class StandardFrameworks {
    private static final String TAG = StandardFrameworks.class.getSimpleName();
    private static StandardFrameworks mInstance;

    synchronized public static StandardFrameworks getInstances() {
        if (mInstance == null) {
            try {
                Class c = Class.forName("com.sprd.soundrecorder.frameworks.SprdFramewoks");
                Object newInstance = c.newInstance();
                mInstance = (StandardFrameworks) newInstance;
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            if (mInstance == null) {
                Log.d(TAG, "use StandardFrameworks");
                mInstance = new StandardFrameworks();
            } else {
                Log.d(TAG, "use SprdFramewoks");
            }
        }
        return mInstance;

    }

    //audio
    public boolean isAudioSourceActive(int source) {
        return false;
        //// TODO: 2017/4/7
    }

    //mediastore
    public String getMediaStoreAlbumArtist() {
        return "album_artist";
    }

    //storage
    public HashMap<String, String> getUsbVolumnInfo(StorageManager storageManager) {
        //key:VolumeDescription,value:path
        HashMap<String, String> usbvolumns = new HashMap<>();
        return usbvolumns;
    }

    public int getDefaultStorageLocation(Context context) {
        return -1;
    }

    public String getInternalStoragePathState() {
        return Environment.getExternalStorageState();
    }

    public File getInternalStoragePath() {
        return Environment.getExternalStorageDirectory();
    }

    public String getExternalStoragePathState() {
        return Environment.getExternalStorageState(getExternalStoragePath());
    }

    public File getExternalStoragePath() {
        return new File("storage/sdcard0");
    }

    //system
    public String getSystemProperties(String name, String default_value) {
        return default_value;
    }

    public boolean getBooleanFromSystemProperties(String name, boolean default_value) {
        return default_value;
    }

    //tele
    public boolean getTeleHasIccCard(TelephonyManager tm, int slotId) {
        return tm.hasIccCard();
    }

    public void setActualDefaultRingtoneUri(Context context, int type, Uri ringtoneUri, int phoneId) {
        RingtoneManager.setActualDefaultRingtoneUri(context, type, ringtoneUri);
    }

    public File[] getUsbStoragePath() {//bug 663417 when connect the otg u volume the first entry filelist is empty
        return null;
    }

    public int getFLagReceiverIncludeBackground() {
        return 0x01000000;
    }
}
