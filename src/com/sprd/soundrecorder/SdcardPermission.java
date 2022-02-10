package com.sprd.soundrecorder;

import android.app.Activity;
import android.app.AlertDialog;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;

import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.os.Environment;
import android.os.EnvironmentEx;
import android.util.Log;

import com.android.soundrecorder.R;
import com.sprd.soundrecorder.data.DataOpration;
import com.sprd.soundrecorder.data.RecordListAdapter;
import com.sprd.soundrecorder.data.RecorderItem;
import com.sprd.soundrecorder.data.ViewHolder;
import com.sprd.soundrecorder.ui.RecordItemFragment;

import java.io.File;
import java.util.List;
import java.util.ArrayList;
import android.util.SparseArray;

public class SdcardPermission {
    private static final String TAG = SdcardPermission.class.getSimpleName();

    public static boolean judgeIncludeSdcardAndCallFile(SparseArray<RecorderItem> copyItems) {
        boolean result = false;

        if (copyItems ==null)
            return result;

        for (int i = 0; i < copyItems.size(); i++) {
            File del = new File(copyItems.valueAt(i).getData());

            if (del.getAbsolutePath().startsWith(EnvironmentEx.getExternalStoragePath().getAbsolutePath()) &&
                 del.getAbsolutePath().contains("voicecall")) {
                result = true;
            }
        }
        return result;
    }

    public static void requestSdRootDirectoryAccess(Activity activity, int requestCode) {
        for (StorageVolume volume : getVolumes(activity)) {
            File volumePath = volume.getPathFile();
            if (!volume.isPrimary() && (volumePath != null) &&
                    Environment.getExternalStorageState(volumePath).equals(Environment.MEDIA_MOUNTED) &&
                    volumePath.getAbsolutePath().startsWith(EnvironmentEx.getExternalStoragePath().getAbsolutePath())) {
                //bug:1094408 use new SAF issue to get SD write permission
                Intent intent = null;
                if (android.os.Build.VERSION.SDK_INT >= DataOpration.ANDROID_SDK_VERSION_DEFINE_Q) {
                     intent = volume.createOpenDocumentTreeIntent();
                } else {
                     intent = volume.createAccessIntent(null);
                }
                if (intent != null) {
                    activity.startActivityForResult(intent, requestCode);
                }
            }
        }
    }

    private static List<StorageVolume> getVolumes(Activity activity) {
        final StorageManager sm = (StorageManager)activity.getSystemService(Context.STORAGE_SERVICE);
        final List<StorageVolume> volumes = sm.getStorageVolumes();
        return volumes;
    }

    public static void getPersistableUriPermission(Uri uri, Intent data, Activity activity) {
        final int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION |
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        activity.getContentResolver().takePersistableUriPermission(uri, takeFlags);
    }

    public static boolean judgeRenameIsSdcardAndCallFile(String mFilePath) {
        boolean result = false;

        if (mFilePath.startsWith(EnvironmentEx.getExternalStoragePath().getAbsolutePath()) &&
                        mFilePath.contains("voicecall")) {
            result = true;
        }

        return result;
    }

    /**
     * Display the dialog, and show no sd write permission.
     * @param context  context
     * @param listener listener
     */
    public static void showNoSdWritePermission(Context context, DialogInterface.OnClickListener listener) {
        new AlertDialog.Builder(context).setCancelable(false)
            .setMessage(R.string.no_sd_write_permission)
            .setPositiveButton(R.string.confirm, listener)
            .create()
            .show();
    }

    /**
     * Display the dialog, and show no sd write permission on Aroidriod Q.
     * @param context  context
     * @param listener listener
     */
    //bug:1094408 use new SAF issue to get SD write permission
    public static void showNoSdCallFileWritePermission(Context context, DialogInterface.OnClickListener listener) {
        new AlertDialog.Builder(context).setCancelable(false)
            .setMessage(R.string.superuser_request_confirm)
            .setPositiveButton(R.string.confirm, listener)
            .create()
            .show();
    }
}

