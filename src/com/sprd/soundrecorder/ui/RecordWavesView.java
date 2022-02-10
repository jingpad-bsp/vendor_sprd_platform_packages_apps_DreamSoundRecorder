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
import android.view.View;

import com.android.soundrecorder.R;

import java.util.ArrayList;
import java.util.List;

public class RecordWavesView extends View {
    private String TAG = "RecordWavesView";
    private float mSpace;
    public float mWavesInterval;
    private Paint mPaintDate, mPaintLine, mPaintTag, mPaintText,mPaintBaseLine;
    private String mDateColor = "#ffffff";
    private String mTagColor = "#f24a09";
    private String mStandColor = "#cdff05";
    public List<Float> mWaveDataList;
    public List<Integer> mTagList;
    public SparseArray<Integer> mTagHashMap;
    public boolean mIsRecordPlay;
    private Bitmap mBitmap;
    private Bitmap mLowBitmap;
    private Bitmap mWhiteBitmap;
    public int mPosition;
    public int mCount = 0;
    public int mVisibleLines = 0;
    public float mWaveBaseValue;
    public int mLastSize = 0;
    public int mLastTag = 0;
    public RecordWavesView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public RecordWavesView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RecordWavesView(Context context) {
        super(context);
        init();
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

        mWaveDataList = new ArrayList<Float>();
        mTagList = new ArrayList<Integer>();
        mTagHashMap = new SparseArray<>();

        mBitmap = BitmapFactory.decodeResource(res, R.drawable.tag);
        mLowBitmap = BitmapFactory.decodeResource(res, R.drawable.taglow);
        mWhiteBitmap = BitmapFactory.decodeResource(res, R.drawable.whiteline);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mWaveDataList.size()==0){
            canvas.drawBitmap(mWhiteBitmap,10, getHeight() / 3,mPaintBaseLine);
        }else if (!mIsRecordPlay){
            canvas.drawBitmap(mWhiteBitmap,getWidth() / 2, getHeight() / 3,mPaintBaseLine);
        }
        mWaveBaseValue = (float) getHeight() / (2 * 100);
        if (mIsRecordPlay){
            canvas.drawLine(getWidth() / 2, getHeight() / 2 - mWaveBaseValue * 70,
                getWidth() / 2, getHeight() / 2 + mWaveBaseValue * 100,
                mPaintLine);
        }else {
            canvas.drawLine(getWidth() / 2, getHeight() / 2 - mWaveBaseValue * 80,
                getWidth() / 2, getHeight() / 2 + mWaveBaseValue * 100,
                mPaintLine);
        }
        //bug:1187318 Coverity scan: Dereference before null check
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
            if (mPosition == 0 || mCount != mPosition) {
                mCount = 0;
            } else {
                mCount = 1;
            }
            for (int i = start; i < end; i++) {
                canvas.drawLine(
                        getWidth() / 2 + (i - mPosition) * mWavesInterval
                                - (mCount) * mWavesInterval / 2,
                        getHeight() / 3+50 - mWaveBaseValue * mWaveDataList.get(i),
                        getWidth() / 2 + (i - mPosition) * mWavesInterval
                                - (mCount) * mWavesInterval / 2, getHeight()
                                / 3+50 + mWaveBaseValue * mWaveDataList.get(i),
                        mPaintDate);

                if (mTagHashMap.indexOfKey(i) >= 0) {
                    canvas.drawBitmap(mLowBitmap, getWidth() / 2 + (i - mPosition)
                            * mWavesInterval - (mCount) * mWavesInterval / 2
                            - mSpace * 0.1f, getHeight() / 3+25, mPaintTag);
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
                        getWidth() / 2 - (mWaveDataList.size() - i - 1)
                                * mWavesInterval - mCount * mWavesInterval / 2,
                        getHeight() / 3 - mWaveBaseValue * mWaveDataList.get(i),
                        getWidth() / 2 - (mWaveDataList.size() - i - 1)
                                * mWavesInterval - mCount * mWavesInterval / 2,
                        getHeight() / 3 + mWaveBaseValue * mWaveDataList.get(i),
                        mPaintDate);
                if (mTagHashMap.indexOfKey(i) >= 0) {
                    if (mTagHashMap.indexOfKey(i) == mLastTag){
                        canvas.drawBitmap(mBitmap,
                            getWidth() / 2 - (mWaveDataList.size() - i)
                                    * mWavesInterval - mCount * mWavesInterval
                                    / 2 - mSpace * 0.3f, getHeight() / 3-15
                                    , mPaintTag);
                                    mLastTag ++;
                    }else {
                        canvas.drawBitmap(mLowBitmap,
                            getWidth() / 2 - (mWaveDataList.size() - i)
                                    * mWavesInterval - mCount * mWavesInterval
                                    / 2 - mSpace * 0.3f, getHeight() / 3-15
                                    , mPaintTag);
                    }
                }
            }
        }
    }
}
