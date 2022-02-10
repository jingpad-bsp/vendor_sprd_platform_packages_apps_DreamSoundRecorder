package com.sprd.soundrecorder.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.NumberPicker;
import android.widget.NumberPicker.OnValueChangeListener;
import android.widget.RadioGroup;
import android.widget.TimePicker;
import android.widget.TimePicker.OnTimeChangedListener;

import com.android.soundrecorder.R;
import com.sprd.soundrecorder.RecordSetting;
import com.sprd.soundrecorder.RecordingFileListTabUtils;

import java.util.Calendar;

import static android.content.Context.MODE_PRIVATE;

public class TimeAndDurationPickerDialog extends AlertDialog implements OnClickListener,
        OnTimeChangedListener, RadioGroup.OnCheckedChangeListener {
    static final String TAG = "TimeAndDurationPickerDialog";
    private static final String HOUR = "hour";
    private static final String MINUTE = "minute";
    private static final String IS_24_HOUR = "is24hour";

    private final OnTimeSetListener mTimeSetListener;

    private final boolean mIs24HourView;

    private NumberPicker np1, np2, np3;
    private int mHourShow = 0;
    private int mMinShow = 0;
    private int mDefaultHour;
    private int mDefaultMin;
    private int mDurationIndex = 0;
    private int mDurationShow = 30;

    /**
     * The callback interface used to indicate the user is done filling in
     * the time (e.g. they clicked on the 'OK' button).
     */
    public interface OnTimeSetListener {
        public void onTimeSet(TimePicker view, int hourOfDay, int minute, int duration);
    }

    /**
     * Creates a new time picker dialog.
     *
     * @param context      the parent context
     * @param listener     the listener to call when the time is set
     * @param hourOfDay    the initial hour
     * @param minute       the initial minute
     * @param is24HourView whether this is a 24 hour view or AM/PM
     */
    public TimeAndDurationPickerDialog(Context context, OnTimeSetListener listener, int hourOfDay, int minute,
                                       boolean is24HourView) {
        this(context, 0, listener, hourOfDay, minute, is24HourView);
    }

    static int resolveDialogTheme(Context context, int resId) {
        if (resId == 0) {
            final TypedValue outValue = new TypedValue();
            context.getTheme().resolveAttribute(R.attr.alertDialogStyle, outValue, true);
            return outValue.resourceId;
        } else {
            return resId;
        }
    }

    /**
     * Creates a new time picker dialog with the specified theme.
     *
     * @param context      the parent context
     * @param themeResId   the resource ID of the theme to apply to this dialog
     * @param listener     the listener to call when the time is set
     * @param hourOfDay    the initial hour
     * @param minute       the initial minute
     * @param is24HourView Whether this is a 24 hour view, or AM/PM.
     */
    public TimeAndDurationPickerDialog(Context context, int themeResId, OnTimeSetListener listener,
                                       int hourOfDay, int minute, boolean is24HourView) {
        super(context, resolveDialogTheme(context, themeResId));


        mTimeSetListener = listener;
        mIs24HourView = is24HourView;
        getTimerTime();
        final Context themeContext = getContext();

        final TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.alertDialogStyle, outValue, true);
        final int layoutResId = outValue.resourceId;

        final LayoutInflater inflater = LayoutInflater.from(themeContext);
        final View view = inflater.inflate(R.layout.time_picker_dialog, null);

        setView(view);
        setTitle(R.string.starttimeandduration);
        setButton(BUTTON_POSITIVE, themeContext.getString(R.string.button_ok), this);
        setButton(BUTTON_NEGATIVE, themeContext.getString(R.string.button_cancel), this);
        if (!RecordSetting.mIsChecked) {//bug 740129 the timer set is not set time
            setDefaultTimerTime();
        }
        np1 = (NumberPicker) view.findViewById(R.id.np1);
        np1.setMinValue(0);
        np1.setMaxValue(23);
        if (!RecordSetting.mIsChecked) {
            np1.setValue(mDefaultHour);
        } else {
            np1.setValue(mHourShow);
        }
        np1.setOnValueChangedListener(new OnValueChangeListener() {

            @Override
            public void onValueChange(NumberPicker picker, int oldVal,
                                      int newVal) {
                mHourShow = newVal;
            }
        });

        np2 = (NumberPicker) view.findViewById(R.id.np2);
        np2.setMinValue(0);
        np2.setMaxValue(59);//bug 740094 the min show is selete 60
        if (!RecordSetting.mIsChecked) {
            np2.setValue(mDefaultMin);
        } else {
            np2.setValue(mMinShow);
        }
        np2.setOnValueChangedListener(new OnValueChangeListener() {

            @Override
            public void onValueChange(NumberPicker picker, int oldVal,
                                      int newVal) {
                mMinShow = newVal;
            }
        });
        np3 = (NumberPicker) view.findViewById(R.id.np3);
        String durationone = String.format(getContext().getResources().getString(R.string.hour_duration), "0.5");
        String durationtwo = String.format(getContext().getResources().getString(R.string.hour_duration), "1");
        String durationthree = String.format(getContext().getResources().getString(R.string.hour_duration), "2");
        String durationfour = String.format(getContext().getResources().getString(R.string.hour_duration), "3");
        String[] duration = {durationone, durationtwo, durationthree, durationfour};
        np3.setDisplayedValues(duration);
        np3.setMinValue(0);
        np3.setMaxValue(duration.length - 1);
        np3.setValue(mDurationIndex);
        np3.setOnValueChangedListener(new OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker picker, int oldVal,
                                      int newVal) {
                if (newVal == 1) {
                    mDurationShow = 60;
                } else if (newVal == 2) {
                    mDurationShow = 120;
                } else if (newVal == 3) {
                    mDurationShow = 180;
                } else {
                    mDurationShow = 30;
                }
            }
        });
    }

    @Override
    public void onTimeChanged(TimePicker view, int hourOfDay, int minute) {


    }

    private void setDefaultTimerTime() {
        Calendar c = Calendar.getInstance();
        mDefaultHour = c.get(Calendar.HOUR_OF_DAY) + 1;
        if (mDefaultHour > 24) {
            mDefaultHour = 24;
        }
        mDefaultMin = c.get(Calendar.MINUTE);
        mHourShow = mDefaultHour;//bug 740129 the timer set is not set time
        mMinShow = mDefaultMin;
        Log.d(TAG, " mDefaultHour=" + mDefaultHour + " mDefaultMin=" + mDefaultMin);
    }

    private void getTimerTime() {
        SharedPreferences recordSavePreferences = getContext().getSharedPreferences(
                RecordingFileListTabUtils.SOUNDREOCRD_TYPE_AND_DTA, MODE_PRIVATE);
        String Hour = recordSavePreferences.getString(RecordSetting.TIMER_RECORD_HOUR, "0");
        String Minute = recordSavePreferences.getString(RecordSetting.TIMER_RECORD_MINUTE, "0");
        String Duration = recordSavePreferences.getString(RecordSetting.TIMER_RECORD_DURATION, "0");
        Log.d(TAG, " getTimerHour=" + Hour + " Minute=" + Minute + " Duration=" + Duration);
        mHourShow = Integer.parseInt(Hour);
        mMinShow = Integer.parseInt(Minute);
        if (Duration.equals("0.5")) {
            mDurationIndex = 0;
        } else {
            mDurationIndex = Integer.parseInt(Duration);
        }
        if (Duration.equals("1")) {//bug 757817 the timerrecord duration show error
            mDurationShow = 60;
        } else if (Duration.equals("2")) {
            mDurationShow = 120;
        } else if (Duration.equals("3")) {
            mDurationShow = 180;
        } else {
            mDurationShow = 30;
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case BUTTON_POSITIVE:
                if (mTimeSetListener != null) {
                    mTimeSetListener.onTimeSet(null, mHourShow,
                            mMinShow, mDurationShow);
                }
                break;
            case BUTTON_NEGATIVE:
                if (mTimeSetListener != null) {
                    mTimeSetListener.onTimeSet(null, 0, 0, 0);
                }
                cancel();
                break;
        }
    }

    /**
     * Sets the current time.
     *
     * @param hourOfDay    The current hour within the day.
     * @param minuteOfHour The current minute within the hour.
     */
    public void updateTime(int hourOfDay, int minuteOfHour) {

    }

    @Override
    public Bundle onSaveInstanceState() {
        final Bundle state = super.onSaveInstanceState();
        /*state.putInt(HOUR, mTimePicker.getCurrentHour());
        state.putInt(MINUTE, mTimePicker.getCurrentMinute());
        state.putBoolean(IS_24_HOUR, mTimePicker.is24HourView());*/
        return state;
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        /*final int hour = savedInstanceState.getInt(HOUR);
        final int minute = savedInstanceState.getInt(MINUTE);
        mTimePicker.setIs24HourView(savedInstanceState.getBoolean(IS_24_HOUR));
        mTimePicker.setCurrentHour(hour);
        mTimePicker.setCurrentMinute(minute);*/
    }

    public TimePicker getTime() {
        return null;
    }

    public void onCheckedChanged(RadioGroup group, int checkedId) {
        /*if (checkedId == mRadioButton1.getId()) {
            mRecordDuration = 30;
        } else if (checkedId == mRadioButton2.getId()) {
            mRecordDuration = 60;
        } else if (checkedId == mRadioButton3.getId()) {
            mRecordDuration = 120;
        } else if (checkedId == mRadioButton4.getId()) {
            mRecordDuration = 180;
        }*/
    }
}
