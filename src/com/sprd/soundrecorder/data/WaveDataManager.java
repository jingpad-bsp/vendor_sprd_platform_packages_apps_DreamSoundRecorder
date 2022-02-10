package com.sprd.soundrecorder.data;

import android.content.Context;
import android.util.Log;
import android.util.SparseArray;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import com.sprd.soundrecorder.data.DataOpration;

/**
 * Created by jian.xu on 2017/3/30.
 */

public class WaveDataManager {
    private static final String TAG = "WaveDataManager";

    private StringBuffer mWaveTemp = new StringBuffer();
    private StringBuffer mTagTemp = new StringBuffer();

    private String mWaveDataFileName;
    private String mTagDataFileName;

    private BufferedWriter mWaveWriter;
    private BufferedWriter mTagWriter;

    private String mFileName;

    private long mBufferSize = 1024 * 1024;

    public long getmBufferSize() {
        return mBufferSize;
    }

    public void setmBufferSize(long mBufferSize) {
        this.mBufferSize = mBufferSize;
    }

    public static String getWaveDataFileName(Context context, String title) {
        return context.getFilesDir().toString() + "/wave_" + title;
    }

    public static String getTagDataFileName(Context context, String title) {
        return context.getFilesDir().toString() + "/tag_" + title;
    }

    public WaveDataManager(Context context, String title) {
        mFileName = title;
        mWaveDataFileName = getWaveDataFileName(context, title);
        mTagDataFileName = getTagDataFileName(context, title);
        Log.d(TAG, "WaveDataManager,Wavedata filename:" + mWaveDataFileName);
        Log.d(TAG, "WaveDataManager,Tagdata filename:" + mTagDataFileName);
        //check file exist
        File file = new File(mWaveDataFileName);
        if (file.exists()) {
            Log.d(TAG, "old wave data is exist,and remove it");
            boolean result = file.delete();
            if (!result) {
                Log.d(TAG, "faild to reomve old wave data");
            }
        }
        file = new File(mTagDataFileName);
        if (file.exists()) {
            Log.d(TAG, "old tag data is exist,and remove it");
            boolean result = file.delete();
            if (!result) {
                Log.d(TAG, "faild to reomve old tag data");
            }
        }
    }

    public String getWaveFileName() {
        return mFileName;
    }

    public static boolean rename(Context context, String oldtitle, String newtitle) {
        Log.d(TAG, "oldtitle = " + oldtitle + " , newtitle = " + newtitle);
        boolean result = true;
        File wavadatafile = new File(getWaveDataFileName(context, oldtitle));
        File tagdatafile = new File(getTagDataFileName(context, oldtitle));
        //bug 1209101 coverity scan:Exceptional return value of java.io.File.renameTo(File) ignored
        if (!wavadatafile.exists() ||
                !wavadatafile.renameTo(new File(getWaveDataFileName(context, newtitle)))) {
            result = false;
            Log.d(TAG, "file:" + getWaveDataFileName(context, oldtitle) +
                    " is not exists:" + !wavadatafile.exists() + ",or rename faild");
        }
        if (!tagdatafile.exists() ||
                !tagdatafile.renameTo(new File(getTagDataFileName(context, newtitle)))) {
            result = false;
            Log.d(TAG, "file:" + getTagDataFileName(context, oldtitle) +
                    " is not exists:" + !tagdatafile.exists() + ",or rename faild");
        }
        return result;
    }

    public static boolean delete(Context context, String title, String type) {
        boolean result = true;
        Log.d(TAG, "WaveData  delete title = " + title + " , type = " + type);
        String fileId = DataOpration.getRecordFileId(context, title, type);
        File wavadatafile = new File(getWaveDataFileName(context, fileId));
        File tagdatafile = new File(getTagDataFileName(context, fileId));

        if (!wavadatafile.exists() || !wavadatafile.delete()) {
            result = false;
            Log.d(TAG, "file:" + getWaveDataFileName(context, fileId) +
                    " is not exists:" + !wavadatafile.exists() + ",or delete faild");
        }
        if (!tagdatafile.exists() || !tagdatafile.delete()) {
            result = false;
            Log.d(TAG, "file:" + getTagDataFileName(context, fileId) +
                    " is not exists" + !tagdatafile.exists() + ",or delete faild");
        }
        return result;
    }

    public static boolean deleteOriginalWaveFile(Context context, String title) {
        boolean result = true;
        File wavadatafile = new File(getWaveDataFileName(context, title));
        File tagdatafile = new File(getTagDataFileName(context, title));

        if (!wavadatafile.exists() || !wavadatafile.delete()) {
            result = false;
            Log.d(TAG, "file:" + getWaveDataFileName(context, title) +
                    " is not exists:" + !wavadatafile.exists() + ",or delete faild");
        }
        if (!tagdatafile.exists() || !tagdatafile.delete()) {
            result = false;
            Log.d(TAG, "file:" + getTagDataFileName(context, title) +
                    " is not exists" + !tagdatafile.exists() + ",or delete faild");
        }
        return result;
    }

    public void addWaveData(float wave) {
        mWaveTemp.append(String.valueOf(wave) + "\r\n");
        try {
            if (mWaveWriter == null) {
                mWaveWriter = new BufferedWriter(new FileWriter(new File(mWaveDataFileName), true));
            }
            if (mWaveTemp.length() > mBufferSize) {
                Log.d(TAG, "addWaveData,write to file");
                mWaveWriter.write(mWaveTemp.toString());
                mWaveWriter.flush();
                mWaveTemp = new StringBuffer();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addTagData(int tag, long location) {
        mTagTemp.append(String.valueOf(location) + "*" + String.valueOf(tag) + "\r\n");
        try {
            if (mTagWriter == null) {
                mTagWriter = new BufferedWriter(new FileWriter(new File(mTagDataFileName), true));
            }
            if (mTagTemp.length() > mBufferSize) {
                Log.d(TAG, "addTagData,write to file");
                mTagWriter.write(mTagTemp.toString());
                mTagWriter.flush();
                mTagTemp = new StringBuffer();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static ArrayList getWaveData(Context context, String title, String type) {
        Log.d(TAG, "getWaveData  title = " + title + " , type = " + type);
        String fileId = DataOpration.getRecordFileId(context, title, type);
        ArrayList<Float> wavelist = new ArrayList<>();
        try (FileInputStream fin = new FileInputStream(getWaveDataFileName(context, fileId));
             BufferedReader in2 = new BufferedReader(new InputStreamReader(fin))) {
            String strTem;
            while ((strTem = in2.readLine()) != null) {
                wavelist.add(Float.valueOf(strTem));
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return wavelist;
    }

    public static SparseArray getTagData(Context context, String title, String type) {
        Log.d(TAG, "getTagData  title = " + title + " , type = " + type);
        String fileId = DataOpration.getRecordFileId(context, title, type);
	    SparseArray<Integer> tagarray = new SparseArray<>();
        try (FileInputStream fin = new FileInputStream(getTagDataFileName(context, fileId));
             BufferedReader in2 = new BufferedReader(new InputStreamReader(fin))) {
            String strTem;
            while ((strTem = in2.readLine()) != null) {
                String[] templist = strTem.split("\\*");
                if (templist.length == 2) {
                    tagarray.put(Integer.parseInt(templist[0]), Integer.parseInt(templist[1]));
                }
            }
        } catch (FileNotFoundException e) {
            //e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return tagarray;
    }

    public void flushData() {
        Log.d(TAG, "flushData");
        try {
            if (mWaveTemp.length() > 0) {
                mWaveWriter.write(mWaveTemp.toString());
                mWaveWriter.flush();
            }
            if (mTagTemp.length() > 0) {
                mTagWriter.write(mTagTemp.toString());
                mTagWriter.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (mWaveWriter != null) {
                    mWaveWriter.close();
                }
                if (mTagWriter != null) {
                    mTagWriter.close();
                }
                mWaveWriter = null;
                mTagWriter = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
