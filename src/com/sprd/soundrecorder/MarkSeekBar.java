package com.sprd.soundrecorder;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.widget.SeekBar;

import com.android.soundrecorder.R;

import java.util.ArrayList;

public class MarkSeekBar extends SeekBar {
    private static final String TAG = "MarkSeekBar";
    private ArrayList<Mark> mMarks = new ArrayList<Mark>();
    private Paint mShapePaint;
    private Paint mPaintTag;
    private Rect mBarRect = new Rect();
    private int mDuration;
    private String playedTagColor = "#8b8b8b";
    private String tagColor = "#8b8b8b";
    private Bitmap mBitmap;
    private float mSpace;

    public MarkSeekBar(Context context) {
        super(context);
        init();
    }

    public MarkSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MarkSeekBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
    }

    private void init() {
        initDraw();
    }

    private void initDraw() {
        mShapePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintTag = new Paint();
        mSpace = getResources().getDimension(R.dimen.panit_line_space);
        mPaintTag.setStrokeWidth(mSpace * 0.4f);
        mBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.tag_recording);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mBarRect.left = getPaddingLeft();
        mBarRect.right = getWidth() - getPaddingRight();
        final boolean isLayoutRtl = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        float cx;
        float cy = getHeight() / 2 - getPaddingBottom() / 2 - 2;
        float barRectwidth = mBarRect.width() - 50;
        float radius;
        int markPostion;
        if (mMarks.size() > 0) {
            for (int i = 0; i < mMarks.size(); i++) {
                markPostion = mMarks.get(i).postion;
                if (getProgress() > markPostion) {
                    mShapePaint.setColor(Color.parseColor(playedTagColor));
                } else {
                    mShapePaint.setColor(Color.parseColor(tagColor));
                }
                if (isLayoutRtl) {
                    cx = mBarRect.right - barRectwidth
                            * (mMarks.get(i).postion / (float) mDuration);
                } else {
                    cx = mBarRect.left + barRectwidth
                            * (mMarks.get(i).postion / (float) mDuration);
                }
                radius = getResources().getDimension(R.dimen.panit_line_space) / 3;
                //canvas.drawCircle(cx, cy, radius, mShapePaint);
                canvas.drawBitmap(mBitmap, cx, cy, mPaintTag);
            }
        }

    }

    public void setMyPadding(int left, int top, int right, int bottom) {
        setPadding(left, top, right, bottom);
    }

    public void addMark(int postion) {
        mMarks.add(new Mark(postion));
    }

    public void clearAllMarks() {
        mMarks.clear();
    }

    public void setDuration(int duration) {
        mDuration = duration;
    }

    public class Mark extends RectF {
        public Mark(int postion) {
            this.postion = postion;
        }

        public int postion;

        @Override
        public String toString() {
            return "Mark [postion=" + postion + ", left=" + left + ", top="
                    + top + ", right=" + right + ", bottom=" + bottom + "]";
        }
    }
}
