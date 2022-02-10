package com.sprd.soundrecorder.ui;

import android.content.Context;
import android.preference.SwitchPreference;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;


public class SmartSwitchPreference extends SwitchPreference {
    private static final String TAG = "SmartSwitchPreference";

    private Switch mSwitchView = null;
    private OnSwitchCheckedChangeListener mOnSwitchCheckedListener;
    private OnSwitchButtonCheckedChangeListener mOnSwitchButtonCheckedListener;

    public interface OnSwitchButtonCheckedChangeListener {
        public boolean OnSwitchButtonCheckedChanged(boolean checked);
    }

    public interface OnSwitchCheckedChangeListener {
        public boolean OnSwitchCheckedChanged(Switch compoundButton, boolean checked);
    }

    public void setOnSwitchCheckedChangeListener(OnSwitchCheckedChangeListener listener) {
        mOnSwitchCheckedListener = listener;
    }

    public void setOnSwitchButtonCheckedChangeListener(OnSwitchButtonCheckedChangeListener listener) {
        mOnSwitchButtonCheckedListener = listener;
    }

    public SmartSwitchPreference(Context context) {
        super(context);
    }

    public SmartSwitchPreference(Context context, AttributeSet set) {
        super(context, set, android.R.attr.switchPreferenceStyle);
    }

    public SmartSwitchPreference(Context context, AttributeSet set, int paramInt) {
        super(context, set, paramInt);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        View mView = super.onCreateView(parent);
        mSwitchView = (Switch) mView.findViewById(android.R.id.switch_widget);
        if (mSwitchView != null) {
            mSwitchView.setClickable(true);
            mSwitchView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_UP
                            || event.getAction() == MotionEvent.ACTION_CANCEL) {
                        if (mOnSwitchButtonCheckedListener != null) {
                            mOnSwitchButtonCheckedListener.OnSwitchButtonCheckedChanged(!mSwitchView.isChecked());
                        }
                    }
                    return true;
                }
            });
        }
        return mView;
    }

    public void toogle(boolean check) {
        if (mSwitchView != null) {
            handleSwitchChangeState(check);
        }
    }

    public void handleSwitchChangeState(boolean check) {
        if (mSwitchView != null) {
            mSwitchView.setChecked(check);
            if (mOnSwitchCheckedListener != null) {
                mOnSwitchCheckedListener.OnSwitchCheckedChanged(mSwitchView, check);
            }
        }
    }

    @Override
    public void onClick() {
        //do nothing
    }
}
