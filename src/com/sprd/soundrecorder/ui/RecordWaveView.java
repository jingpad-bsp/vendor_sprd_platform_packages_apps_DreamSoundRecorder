package com.sprd.soundrecorder.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.View;

import com.android.soundrecorder.R;

import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by jian.xu on 2017/10/16.
 */

public class RecordWaveView extends View {

    private String TAG = "RecordWaveView";
    private float mSpace;
    private float mWavesInterval;
    private Paint mPaintDate, mPaintLine, mPaintTag, mPaintText, mPaintBaseLine;
    private String mDateColor = "#ffffff";
    private String mTagColor = "#f24a09";
    private String mStandColor = "#5CCA7A";
    private List<Float> mWaveDataList = new ArrayList<>();
    private SparseArray<Integer> mTagSparseArray = new SparseArray<>();
    private boolean mIsRecordPlay = false;
    private Bitmap mBitmap;
    private Bitmap mLowBitmap;
    private Bitmap mWhiteBitmap;
    private int mPosition = 0;
    private int mLastPosition = 0;
    private int mCount = 0;
    private int mVisibleLines = 0;
    private float mWaveBaseValue;
    private int mLastSize = 0;
    private int mLastTag = 0;
    private int mTagHalfSize = 0;
    private int mSmallTagHalfSize = 0;
    private long mLastClickTime = 0L;
    private static final long INTERVALTIME = 500L;

    public RecordWaveView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public RecordWaveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RecordWaveView(Context context) {
        super(context);
        init();
    }

    public RecordWaveView(Context context, boolean isPlay) {
        super(context);
        init();
        mIsRecordPlay = isPlay;
    }

    private void init() {
        Resources res = getResources();
        mSpace = res.getDimension(R.dimen.panit_line_space);
        mWavesInterval = mSpace / 3;

        mPaintDate = new Paint();
        mPaintDate.setStrokeWidth(mSpace * 0.1f);
        mPaintDate.setColor(Color.parseColor(mDateColor));
        mPaintDate.setAlpha(100);

        mPaintBaseLine = new Paint();
        mPaintBaseLine.setStrokeWidth(mSpace * 0.2f);
        mPaintBaseLine.setColor(Color.parseColor(mDateColor));

        mPaintLine = new Paint();
        mPaintLine.setStrokeWidth(mSpace * 0.2f);
        mPaintLine.setColor(Color.parseColor(mStandColor));

        mPaintTag = new Paint();
        mPaintTag.setStrokeWidth(mSpace * 0.2f);
        mPaintTag.setColor(Color.parseColor(mTagColor));

        mPaintText = new Paint();
        mPaintText.setTextSize(mSpace * 0.8f);
        mPaintText.setColor(Color.WHITE);

        mBitmap = BitmapFactory.decodeResource(res, R.drawable.tag);
        mTagHalfSize = mBitmap.getWidth() / 2;
        mLowBitmap = BitmapFactory.decodeResource(res, R.drawable.taglow);
        mSmallTagHalfSize = mLowBitmap.getWidth() / 2;
        //mWhiteBitmap = BitmapFactory.decodeResource(res, R.drawable.whiteline);
        mWhiteBitmap = decodeResource(res, R.drawable.whiteline);
        invalidate();
    }

    //bug:1027504  not scale the initial background white line
    private Bitmap decodeResource(Resources resources, int id) {
        Bitmap bitmap = null;

        try {
            TypedValue value = new TypedValue();

            /* Bug1132820, after get raw resource, should close the inputstream */
            final InputStream is = resources.openRawResource(id, value);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inTargetDensity = value.density;

            if (is != null) {
                is.close();
            }

            bitmap = BitmapFactory.decodeResource(resources, id, opts);
        } catch (IOException e) {
            Log.e(TAG, "decodeResource: IOException: ", e);
        }

        return bitmap;
    }

    public void setWaveData(List<Float> waveDataList, SparseArray<Integer> tagArray) {
        reset();
        mWaveDataList = waveDataList;
        mTagSparseArray = tagArray;
    }

    /* bug:922346 recording in background ,not reset @{ */
    public void setWaveData(List<Float> waveDataList, SparseArray<Integer> tagArray, boolean needReset) {
        /* bug:1136743 Get value of mLastTag from Service in case mLastTag is set to 0 abnormally. @{ */
        if (tagArray != null) {
            mLastTag = tagArray.size();
            if (needReset && mLastTag == 0) {
                reset();
            }
        }
        /* }@ */

        mWaveDataList = waveDataList;
        mTagSparseArray = tagArray;
    }
    /* }@ */

    public void reset() {
        mCount = 0;
        mLastTag = 0;
        mLastSize = 0;
    }

    public int getWaveDataSize() {
        return mWaveDataList.size();
    }

    public boolean isHaveWaveData() {
        return mWaveDataList.size() > 0;
    }

    public void setCurrentPosition(float currentPosition) {
        mLastPosition = mPosition;
        mPosition = (int) (((float) mWaveDataList.size()) * currentPosition);
    }

    public float getCurretPlayProcess() {
        return (float) mPosition / (float) mWaveDataList.size();
    }

    public void moveWaveView(int moveLength) {
        mPosition = mPosition - (int) (moveLength / mWavesInterval);
        if (mPosition < 0) {
            mPosition = 0;
        } else if (mPosition > mWaveDataList.size() - 1) {
            mPosition = mWaveDataList.size() - 1;
        }
    }

    public void moveToNextTag(boolean direction, boolean isPause) {
        if (direction) {
            for (int i = 0; i < mTagSparseArray.size(); i++) {
                if (mPosition < mTagSparseArray.keyAt(i)) {
                    Log.d(TAG, "mPositon:" + mPosition + ",tag postion:" + mTagSparseArray.keyAt(i));
                    mPosition = mTagSparseArray.keyAt(i);
                    mPosition = mPosition < 0 ? 0 : mPosition;
                    break;
                }

            }
        } else {
            /*bug 990273 click the last tag continuously when play the recording file ,not return to the previous one @{*/
            long currentTime = System.currentTimeMillis();
            boolean isDoubleClick = currentTime - INTERVALTIME < mLastClickTime;
            mLastClickTime = currentTime;
            int j = 0;
            for (int i = mTagSparseArray.size() - 1; i >= 0; i--) {
                //1235700 click the last tag quickly when play the recording file ,not seek to right tag
                if (mPosition >= mTagSparseArray.keyAt(i) && !isPause) {
                    j++;
                    if (isDoubleClick) {
                        if (j == 2 || i==0) {
                            mPosition = mTagSparseArray.keyAt(i);
                            mPosition = mPosition < 0 ? 0 : mPosition;
                            break;
                        }
                    } else {
                        mPosition = mTagSparseArray.keyAt(i);
                        mPosition = mPosition < 0 ? 0 : mPosition;
                        break;
                    }
                }else if (mPosition > mTagSparseArray.keyAt(i) && isPause){
                    mPosition = mTagSparseArray.keyAt(i);
                    mPosition = mPosition < 0 ? 0 : mPosition;
                    break;
                }
            /* }@ */
            }
        }
    }

    /*
    * return is there more tags?
     * direction： true --> backward ;false -->forward
    * */
    public boolean isHaveMoreTag(boolean direction) {
        if (mTagSparseArray.size() == 0) {
            return false;
        }
        //Log.d(TAG, "mTagSparseArray:" + mTagSparseArray);
        if (direction) {
            return mPosition < mTagSparseArray.keyAt(mTagSparseArray.size() - 1);
        } else {
            return mPosition > mTagSparseArray.keyAt(0);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        ////bug 1210740 coverity scan:Dereference before null check
        if (mWaveDataList == null)
            return;
        Log.d(TAG, "ondraw: listsize:" + mWaveDataList.size() + "mTagSparseArray size: " + mTagSparseArray + "positon:" + mPosition);
        int middle_height = getHeight() / 2;
        int middle_width = getWidth() / 2;
        if (mWaveDataList.size() == 0) {
            //canvas.drawBitmap(mWhiteBitmap, 0, middle_height, mPaintBaseLine);
            canvas.drawBitmap(mWhiteBitmap,(getWidth() - mWhiteBitmap.getWidth()) / 2 ,middle_height , mPaintBaseLine);
        } else if (!mIsRecordPlay) {
            canvas.drawBitmap(mWhiteBitmap, middle_width, middle_height, mPaintBaseLine);
        }
        mWaveBaseValue = (float) getHeight() / 200;

        canvas.drawLine(middle_width, 0, middle_width, getHeight(), mPaintLine);

        if (mWaveDataList.size() == 0)
            return;

        int start = 0;
        int end = 0;

        if (mIsRecordPlay) {
            mVisibleLines = (int) (getWidth() / mWavesInterval);
            start = mPosition > mVisibleLines / 2 ? (mPosition - mVisibleLines / 2)
                    : 0;
            end = mWaveDataList.size() > (start + mVisibleLines) ? (start + mVisibleLines)
                    : mWaveDataList.size();
            // Bug:953910 avoid redrawing due to low memory
            if (mPosition == 0 || mCount != mPosition || mLastPosition == mPosition) {
                mCount = 0;
            } else {
                mCount = 1;
            }
            for (int i = start; i < end; i++) {
                canvas.drawLine(
                        middle_width + (i - mPosition) * mWavesInterval
                                - (mCount) * mWavesInterval / 2,
                        middle_height - mWaveBaseValue * mWaveDataList.get(i),
                        middle_width + (i - mPosition) * mWavesInterval
                                - (mCount) * mWavesInterval / 2,
                        middle_height + mWaveBaseValue * mWaveDataList.get(i),
                        mPaintDate);
                //bug:1107682 the tag position is not in the horizontal center position
                if (mTagSparseArray.indexOfKey(i) >= 0) {
                    canvas.drawBitmap(mLowBitmap, middle_width + (i - mPosition)
                            * mWavesInterval - (mCount) * mWavesInterval / 2
                            - mSmallTagHalfSize, middle_height - mSmallTagHalfSize, mPaintTag);
                }
            }
            mCount = mPosition;
        } else {
            if (mWaveDataList.size() != mLastSize) {
                mLastSize = mWaveDataList.size();
                mCount = 0;
                Log.d(TAG, "onDraw set mCount = 0");
            } else {
                mCount = 1;
                Log.d(TAG, "onDraw mCount = " + mCount);
            }
            mVisibleLines = (int) (getWidth() / (2 * mWavesInterval));
            start = mWaveDataList.size() > mVisibleLines ? (mWaveDataList
                    .size() - mVisibleLines) : 0;
            for (int i = start; i < mWaveDataList.size(); i++) {
                canvas.drawLine(
                        middle_width - (mWaveDataList.size() - i - 1)
                                * mWavesInterval - mCount * mWavesInterval / 2,
                        middle_height - mWaveBaseValue * mWaveDataList.get(i),
                        middle_width - (mWaveDataList.size() - i - 1)
                                * mWavesInterval - mCount * mWavesInterval / 2,
                        middle_height + mWaveBaseValue * mWaveDataList.get(i),
                        mPaintDate);
                if (mTagSparseArray.indexOfKey(i) >= 0) {
                    if (mTagSparseArray.indexOfKey(i) == mLastTag) {
                        canvas.drawBitmap(mBitmap,
                                middle_width - mBitmap.getWidth() / 2, middle_height - mTagHalfSize
                                , mPaintTag);
                        mLastTag++;
                    } else {
                        canvas.drawBitmap(mLowBitmap,
                                middle_width - (mWaveDataList.size() - i)
                                        * mWavesInterval - mCount * mWavesInterval
                                        / 2 - mSpace * 0.3f, middle_height - mSmallTagHalfSize
                                , mPaintTag);
                    }
                }
            }
        }
    }
}
