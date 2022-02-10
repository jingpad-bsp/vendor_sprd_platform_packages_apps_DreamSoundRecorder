package com.sprd.soundrecorder.data;

import android.content.Context;

import com.android.soundrecorder.R;
import com.sprd.soundrecorder.Utils;

import java.io.Serializable;
import java.text.DecimalFormat;
import android.text.format.Formatter;

/**
 * Created by jian.xu on 2017/10/10.
 */

public class RecorderItem implements Serializable {
    private long mId;
    private String mData;
    private String mMimeType;
    private long mSize;
    private String mTitle;
    private String mDisplayName;
    private long mDateModify;
    private int mDuration;
    private int mTagNumber;

//    private static final double NUMBER_KB = 1024D;
//    private static final double NUMBER_MB = NUMBER_KB * NUMBER_KB;

    public long getId() {
        return mId;
    }

    public void setId(long id) {
        this.mId = id;
    }

    public String getData() {
        return mData;
    }

    public void setData(String data) {
        this.mData = data;
    }

    public String getMimeType() {
        return mMimeType;
    }

    public void setMimeType(String mimeType) {
        this.mMimeType = mimeType;
    }

    public long getSize() {
        return mSize;
    }

    public void setSize(long size) {
        this.mSize = size;
    }

    public String getSizeString(Context context) {
//        StringBuffer buff = new StringBuffer();
//        if (mSize > 0) {
//            String format = null;
//            double calculate = -1D;
//            if (mSize < NUMBER_KB) {
//                format = context.getString(R.string.list_recorder_item_size_format_b);
//                String calculate_b = String.valueOf(mSize);
//                buff.append(String.format(format, calculate_b));
//            } else if (mSize < NUMBER_MB) {
//                format = context.getString(R.string.list_recorder_item_size_format_kb);
//                calculate = (mSize / NUMBER_KB);
//                DecimalFormat df = new DecimalFormat(".##");
//                String st = df.format(calculate);
//                buff.append(String.format(format, st));
//            } else {
//                format = context.getString(R.string.list_recorder_item_size_format_mb);
//                calculate = (mSize / NUMBER_MB);
//                DecimalFormat df = new DecimalFormat(".##");
//                String st = df.format(calculate);
//                buff.append(String.format(format, st));
//            }
//       }
        //bug:900713 calls the system method to calculate the size
        return Formatter.formatFileSize(context, mSize);
    }

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String title) {
        this.mTitle = title;
    }

    public String getDisplayName() {
        return mDisplayName;
    }

    public void setDisplayName(String display_name) {
        this.mDisplayName = display_name;
    }

    public long getDateModify() {
        return mDateModify;
    }

    public void setDateModify(long time) {
        this.mDateModify = time;
    }

    public String getDateString() {
        if (mDateModify > 0) {
            java.util.Date d = new java.util.Date(mDateModify * 1000);
            java.text.DateFormat formatter_date =
                    java.text.DateFormat.getDateInstance();
            return formatter_date.format(d);
        }
        return null;
    }

    public int getDuration() {
        return mDuration;
    }

    public void setDuration(int duration) {
        this.mDuration = duration;
    }

    public String getDurationString(Context context) {
        return Utils.makeTimeString4MillSec(context, mDuration);
    }

    public int getTagNumber() {
        return mTagNumber;
    }

    public void setTagNumber(int tagNumber) {
        this.mTagNumber = tagNumber;
    }

    @Override
    public String toString() {
        StringBuffer buff = new StringBuffer();
        buff.append("id == ").append(mId)
                .append(" --- data == ").append(mData)
                .append(" --- mimeType == ").append(mMimeType)
                .append(" --- size == ").append(mSize)
                .append(" --- title == ").append(mTitle)
                .append(" --- display_name == ").append(mDisplayName)
                .append(" --- time == ").append(mDateModify)
                .append(" --- duration == ").append(mDuration);
        return buff.toString();
    }

}
