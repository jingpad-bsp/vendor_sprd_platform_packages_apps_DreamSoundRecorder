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
import android.view.View;

import com.android.soundrecorder.R;

import java.util.ArrayList;
import java.util.List;

public class ClipWavesView extends View {
    private Paint mPaintDate, mStartLine, mEndLine, mPlayLine;
    private String mDateColor = "#c6c7d1";
    public Bitmap mBitmap;
    public List<Float> mWaveDataList;

    public boolean isFirstIn;
    public boolean isRecordPlay;
    public int playingLineX = 0;
    public int startLineX = 0;
    public int endLineX = 0;
    private int mVisibleLines = 0;
    private float mSpace;
    private float mWaveBaseValue;
    private float mWavesInterval;


    public ClipWavesView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public ClipWavesView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ClipWavesView(Context context) {
        super(context);
        init();
    }

    private void init() {
        Resources res = getResources();
        mSpace = res.getDimension(R.dimen.panit_line_space);
        mWavesInterval = mSpace / 3;

        mPaintDate = new Paint();
        mPaintDate.setStrokeWidth(mSpace * 0.2f);
        mPaintDate.setColor(Color.parseColor(mDateColor));

        mStartLine = new Paint();
        mStartLine.setStrokeWidth(mSpace * 0.2f);
        mStartLine.setColor(Color.YELLOW);

        mEndLine = new Paint();
        mEndLine.setStrokeWidth(mSpace * 0.2f);
        mEndLine.setColor(Color.YELLOW);

        mPlayLine = new Paint();
        mPlayLine.setStrokeWidth(mSpace * 0.2f);
        mPlayLine.setColor(Color.WHITE);

        mBitmap = BitmapFactory.decodeResource(res, R.drawable.clip_line);
        startLineX = mBitmap.getWidth() / 2;

        mWaveDataList = new ArrayList<Float>();

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {

        if (mWaveDataList == null || mWaveDataList.size() == 0)
            return;

        int start = 0;
        int end = 0;
        mWaveBaseValue = (float) getHeight() / (2 * 100);

        mWavesInterval = (float) (getWidth() - mBitmap.getWidth()) / (mWaveDataList.size());
        mVisibleLines = (int) (getWidth() / mWavesInterval);
        end = mVisibleLines > mWaveDataList.size() ? mWaveDataList.size() : mVisibleLines;
        for (int i = start; i < end; i++) {
            canvas.drawLine(
                    i * mWavesInterval + mBitmap.getWidth() / 2,
                    getHeight() / 2 - mWaveBaseValue * mWaveDataList.get(i),
                    i * mWavesInterval + mBitmap.getWidth() / 2,
                    getHeight() / 2 + mWaveBaseValue * mWaveDataList.get(i),
                    mPaintDate);
        }

        if (isFirstIn) {
            endLineX = getWidth() - mBitmap.getWidth() / 2;
            isFirstIn = false;
        }

        canvas.drawBitmap(mBitmap, startLineX - mBitmap.getWidth() / 2, mBitmap.getHeight() / 2, mStartLine);
        canvas.drawBitmap(mBitmap, endLineX - mBitmap.getWidth() / 2, mBitmap.getHeight() / 2, mEndLine);
        canvas.drawLine(startLineX, mBitmap.getHeight(), startLineX,
                getHeight() - mBitmap.getHeight() / 2, mStartLine);
        canvas.drawLine(endLineX, mBitmap.getHeight(), endLineX,
                getHeight() - mBitmap.getHeight() / 2, mEndLine);
        if (isRecordPlay) {
            canvas.drawLine(playingLineX, getHeight() / 2 - mWaveBaseValue * 70,
                    playingLineX, getHeight() / 2 + mWaveBaseValue * 70,
                    mPlayLine);
        }
    }
}
