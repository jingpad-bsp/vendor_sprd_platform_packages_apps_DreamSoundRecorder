package com.android.soundrecorder;

import android.os.StatFs;

import java.io.File;



/**
 * Calculates remaining recording time based on available disk space and
 * optionally a maximum recording file size.
 * <p>
 * The reason why this is not trivial is that the file grows in blocks
 * every few seconds or so, while we want a smooth countdown.
 */

public class RemainingTimeCalculator {
    public static final int UNKNOWN_LIMIT = 0;
    public static final int FILE_SIZE_LIMIT = 1;
    public static final int DISK_SPACE_LIMIT = 2;

    // which of the two limits we will hit (or have fit) first
    private int mCurrentLowerLimit = UNKNOWN_LIMIT;

    private File mSDCardDirectory;

    // State for tracking file size of recording.
    private File mRecordingFile;
    private long mMaxBytes;

    // Rate at which the file grows
    private int mBytesPerSecond;

    // time at which number of free blocks last changed
    private long mBlocksChangedTime;
    // number of available blocks at that time
    private long mLastBlocks;

    // time at which the size of the file has last changed
    private long mFileSizeChangedTime;
    // size of the file at that time
    private long mLastFileSize;

    /* SPRD: remove @{
    public RemainingTimeCalculator() {
        mSDCardDirectory = Environment.getExternalStorageDirectory();
    }*/

    /* SPRD: update for storage path @{ */
    public RemainingTimeCalculator(String path) {
        mSDCardDirectory = new File(path);
    }

    public void setStoragePath(File path) {
        mSDCardDirectory = path;
    }

    public void setStoragePath(String path) {
        setStoragePath(new File(path));
    }

    /**
     * If called, the calculator will return the minimum of two estimates:
     * how long until we run out of disk space and how long until the file
     * reaches the specified size.
     *
     * @param file     the file to watch
     * @param maxBytes the limit
     */

    public void setFileSizeLimit(File file, long maxBytes) {
        mRecordingFile = file;
        mMaxBytes = maxBytes;
    }

    /**
     * Resets the interpolation.
     */
    public void reset() {
        mCurrentLowerLimit = UNKNOWN_LIMIT;
        mBlocksChangedTime = -1;
        mFileSizeChangedTime = -1;
    }

    /**
     * Returns how long (in seconds) we can continue recording.
     */
    public long timeRemaining() {
        // Calculate how long we can record based on free disk space
        //StatFs fs = new StatFs(mSDCardDirectory.getAbsolutePath());
        StatFs fs = null;
        try {
            fs = new StatFs(mSDCardDirectory.getAbsolutePath());
        } catch (Exception e) {
            return 0;
        }
        long blocks = fs.getAvailableBlocks();
        long blockSize = fs.getBlockSize();
        long now = System.currentTimeMillis();

        if (mBlocksChangedTime == -1 || blocks != mLastBlocks) {
            mBlocksChangedTime = now;
            mLastBlocks = blocks;
        }

        /* The calculation below always leaves one free block, since free space
           in the block we're currently writing to is not added. This
           last block might get nibbled when we close and flush the file, but
           we won't run out of disk. */

        // at mBlocksChangedTime we had this much time
        if (mBytesPerSecond == 0) {
            if (SoundRecorder.AUDIO_AMR.equals(RecordService.mRequestedType)) {
                setBitRate(SoundRecorder.BIT_PER_SEC_FOR_AMR);
            } else if (SoundRecorder.AUDIO_3GPP.equals(RecordService.mRequestedType)) {
                setBitRate(SoundRecorder.BITRATE_3GPP);
            }else if (SoundRecorder.AUDIO_MP3.equals(RecordService.mRequestedType)){//SPRD:Bug 626265 add mp3 type in soundrecorder
                setBitRate(SoundRecorder.BITRATE_MP3);
            } else {
                throw new IllegalArgumentException(
                        "Invalid output file type requested");
            }
        }

        long result = mLastBlocks * blockSize / mBytesPerSecond;
        // so now we have this much time
        result -= (now - mBlocksChangedTime) / 1000;

        if (mRecordingFile == null) {
            mCurrentLowerLimit = DISK_SPACE_LIMIT;
            return result;
        }

        // If we have a recording file set, we calculate a second estimate
        // based on how long it will take us to reach mMaxBytes.

        mRecordingFile = new File(mRecordingFile.getAbsolutePath());
        long fileSize = mRecordingFile.length();
        if (mFileSizeChangedTime == -1 || fileSize != mLastFileSize) {
            mFileSizeChangedTime = now;
            mLastFileSize = fileSize;
        }

        long result2 = (mMaxBytes - fileSize) / mBytesPerSecond;
        result2 -= (now - mFileSizeChangedTime) / 1000;
        result2 -= 1; // just for safety

        mCurrentLowerLimit = result < result2
                ? DISK_SPACE_LIMIT : FILE_SIZE_LIMIT;

        return Math.min(result, result2);
    }

    /**
     * Indicates which limit we will hit (or have hit) first, by returning one
     * of FILE_SIZE_LIMIT or DISK_SPACE_LIMIT or UNKNOWN_LIMIT. We need this to
     * display the correct message to the user when we hit one of the limits.
     */
    public int currentLowerLimit() {
        return mCurrentLowerLimit;
    }

    /**
     * Is there any point of trying to start recording?
     */
    public boolean diskSpaceAvailable() {
        // SPRD： add
        if (mSDCardDirectory != null) {
            StatFs fs = new StatFs(mSDCardDirectory.getAbsolutePath());
            // keep one free block
            return fs.getAvailableBlocks() > 1;
        }
        return false;
    }

    /**
     * Sets the bit rate used in the interpolation.
     *
     * @param bitRate the bit rate to set in bits/sec.
     */
    public void setBitRate(int bitRate) {
        mBytesPerSecond = bitRate / 8;
    }
}
