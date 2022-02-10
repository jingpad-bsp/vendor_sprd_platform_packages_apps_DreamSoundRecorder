package com.sprd.soundrecorder;

import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

import com.sprd.soundrecorder.frameworks.StandardFrameworks;
import com.sprd.soundrecorder.service.SprdRecorder;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
/* SPRD: fix bug 250201 @{ */

/**
 * Storage access to information related to the class of functions
 * Main fucntion: according to the corresponding access storage shceme for different information
 *
 * @author linying
 */
public class StorageInfos {

    /**
     * judge the internal storage state
     *
     * @return is mounted or not
     */
    public static boolean isInternalStorageMounted() {
        String state = StandardFrameworks.getInstances().getInternalStoragePathState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    /**
     * get the internal storage directory
     *
     * @return internal storage directory
     */
    public static File getInternalStorageDirectory() {
        return StandardFrameworks.getInstances().getInternalStoragePath();
    }

    /**
     * judge the External storage state
     *
     * @return is mounted or not
     */
    public static boolean isExternalStorageMounted() {
        String state = StandardFrameworks.getInstances().getExternalStoragePathState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    /**
     * get the External storage directory
     *
     * @return External storage directory
     */
    public static File getExternalStorageDirectory() {
        return StandardFrameworks.getInstances().getExternalStoragePath();
    }

    /**
     * Get the external storage path wherever sdcard is exist, or not.
     *
     * @return external storage path
     */
    public static String getExternalStorageDir() {
        return getExternalStorageDirectory().getAbsolutePath();
    }

    /**
     * judge whether the scheme for NAND
     *
     * @return true or false
     */
    public static boolean isInternalStorageSupported() {
        boolean support = false;
        if ("1".equals(StandardFrameworks.getInstances().
                getSystemProperties("ro.device.support.nand", "0"))/* ||
            "1".equals(SystemProperties.get("ro.device.support.mmc", "0"))*/) {
            support = true;
        }
        return support;
    }
    /* @} */

    /**
     * judge whether the path is in the external storage, or not
     *
     * @param path
     * @return
     */
    public static boolean isInExternalSDCard(String path) {
        if (path == null) {
            return false;
        }

        return StorageInfos.isExternalStorageMounted() && path.startsWith(StorageInfos.getExternalStorageDir());
    }

    public static boolean isPathExistAndCanWrite(String path) {
        if (path == null) {
            return false;
        }
        File filePath = new File(path);
        return filePath.exists() && filePath.canWrite();
    }

    /**
     * check the storage space is enough
     * internal available space < 5%
     * external availabel space < 50K return false
     *
     * @return true is enough,or false
     */
    public static boolean haveEnoughStorage(String path) {
        boolean isEnough = true;
        boolean isExternalUsed = StorageInfos.isInExternalSDCard(path);

        File savePath = null;
        if (isExternalUsed) {
            savePath = StorageInfos.getExternalStorageDirectory();
        } else {
            savePath = StorageInfos.getInternalStorageDirectory();
        }
        if (isInUsbCard(path)) {//bug 667315 the otg storage is full the pop is error
            savePath = getUSBStorage();
            Log.d("haveEnoughStorage", "sprd savepath=" + savePath);
        }
/*        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)
                && sdCard) {*/
        /** SPRD:Bug 617209 T-card set out into the internal storage, abnormal sound recorder settings  ( @{ */
        try {
            if (savePath != null) {
                final StatFs stat = new StatFs(savePath.getPath());
                final long blockSize = stat.getBlockSize();
                //final long totalBlocks = stat.getBlockCount();
                final long availableBlocks = stat.getAvailableBlocks();

                //long mTotalSize = totalBlocks * blockSize;
                long mAvailSize = availableBlocks * blockSize;
                // Log.w(TAG, "occupied space is up to "+((mAvailSize * 100) /
                // mTotalSize));
                // isEnough = (mAvailSize * 100) / mTotalSize > 5 ? true : false;
                isEnough = mAvailSize < 7000 * 1024 ? false : true;//bug 775891
                //        }
            }
        } catch (IllegalArgumentException e) {
            return false;
        }
        /** @} */
        return isEnough;
    }

    /**
     * check the storage space is enough
     * internal available space < 5%
     * external availabel space < 50K return false
     *
     * @return true is enough,or false
     */
    public static Map<String, String> getStorageInfo(String path) {
        Map<String, String> map = null;
        boolean isEnough = true;
        boolean isExternalUsed = StorageInfos.isInExternalSDCard(path);

        File savePath = null;
        if (isExternalUsed) {
            savePath = StorageInfos.getExternalStorageDirectory();
        } else {
            savePath = StorageInfos.getInternalStorageDirectory();
        }
        if (isInUsbCard(path)) {//bug 667315 the otg storage is full the pop is error
            savePath = getUSBStorage();
            Log.d("haveEnoughStorage", "sprd savepath=" + savePath);
        }
/*        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)
                && sdCard) {*/
        //SPRD:Bug 629593 T-card set out into the internal storage, abnormal sound recorder settings bengin
        try {
            if (savePath != null) {
                map = new HashMap<String, String>();
                final StatFs stat = new StatFs(savePath.getPath());
                final long blockSize = stat.getBlockSize();
                //final long totalBlocks = stat.getBlockCount();
                final long availableBlocks = stat.getAvailableBlocks();
                //long mTotalSize = totalBlocks * blockSize;
                long mAvailSize = availableBlocks * blockSize;
                map.put("availableBlocks", "" + (mAvailSize - 7000 * 1024));//bug 775891
                // Log.w(TAG, "occupied space is up to "+((mAvailSize * 100) /
                // mTotalSize));
                // isEnough = (mAvailSize * 100) / mTotalSize > 5 ? true : false;
                isEnough = mAvailSize < 7000 * 1024 ? false : true;//bug 775891
                map.put("isEnough", "" + isEnough);
                //        }
            }
        } catch (IllegalArgumentException e) {
            Log.d("StorageInfos", "StatFs is exception");
        }
        //SPRD:Bug 629593 end
        return map;
    }

    private static boolean isInUsbCard(String path) {//bug 667315 the otg storage is full the pop is error
        if (path == null) {
            return false;
        }

        return getUSBStorage() != null && path.startsWith(getUSBStorage().getPath());
    }

    //bug 663417 when connect the otg u volume the first entry filelist is empty
    public static File getUSBStorage() {
        File[] usblist = StandardFrameworks.getInstances().getUsbStoragePath();
        if (usblist == null || usblist.length == 0
                || usblist[0] == null) {
            return null;
        }
        return usblist[0];
    }

    public static boolean isPathMounted(String path) {
        //bug 1193089 Dereference null return value (NULL_RETURNS)
        String internalPath = getInternalStorageDefaultPath();
        String externalPath = getExternalStorageDefaultPath();
        if (externalPath != null && path.startsWith(externalPath)) {
            return isExternalStorageMounted();
        } else if ( internalPath != null && path.startsWith(internalPath)) {
            return isInternalStorageMounted();
        } else {
            return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState(new File(path)));
        }
    }

    public static String getExternalStorageDefaultPath() {
        File path = getExternalStorageDirectory();
        if (path != null) {
            return path.getPath() + SprdRecorder.DEFAULT_STORE_SUBDIR;
        }
        return null;
    }

    public static String getInternalStorageDefaultPath() {
        File path = StorageInfos.getInternalStorageDirectory();
        if (path != null) {
            return path.getPath() + SprdRecorder.DEFAULT_STORE_SUBDIR;
        }
        return null;
    }

    public static String getOtgStorageDefaultPath() {
        File path = StorageInfos.getUSBStorage();
        if (path != null) {
            return path.getPath() + SprdRecorder.DEFAULT_STORE_SUBDIR;
        }
        return null;
    }

    public static String getRecordDefaultPath(File file) {
        return file.getPath() + SprdRecorder.DEFAULT_STORE_SUBDIR;
    }
}
