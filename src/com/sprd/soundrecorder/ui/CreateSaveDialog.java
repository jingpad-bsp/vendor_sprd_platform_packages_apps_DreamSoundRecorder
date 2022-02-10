package com.sprd.soundrecorder.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

import com.android.soundrecorder.R;

/**
 * Created by donghua.wu on 2017/7/6.
 */

public class CreateSaveDialog extends Activity {
    private static final String TAG = "CreateSaveDialog";

    private TextView mTitle, mTips;
    private EditText mNewName;
    private CheckBox mCheckBox;
    private Button mOKButton, mCancelButton;

    private String mDefaultName;
    private boolean mCheckState;

    private View.OnClickListener mSaveListener = new View.OnClickListener() {
        public void onClick(View view) {
            String newName = mNewName.getText().toString();
            if (newName == null || newName.isEmpty()) {
                newName = mDefaultName;
            }
            Intent intent = new Intent();
            intent.putExtra("check_state", mCheckState);
            intent.putExtra("new_name", newName);
            setResult(RESULT_OK, intent);
            finish();
        }
    };

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Log.d(TAG, "onCreate");
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.clip_save_dialog);
        getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT);

        Intent i = getIntent();
        mDefaultName = i.getStringExtra("title");
        init();
    }

    private void init() {
        mTitle = (TextView) findViewById(R.id.title);
        mTips = (TextView) findViewById(R.id.tips);
        mNewName = (EditText) findViewById(R.id.name);
        mCheckBox = (CheckBox) findViewById(R.id.checkbox);
        mOKButton = (Button) findViewById(R.id.button_ok);
        mCancelButton = (Button) findViewById(R.id.button_cancel);
        mNewName.setHint(mDefaultName);
        mCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mCheckState = isChecked;
            }
        });
        mCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        mOKButton.setOnClickListener(mSaveListener);
        mCheckBox.setChecked(true);
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        mTitle.setText(R.string.save_dialog_title);
        mTips.setText(R.string.keep_original_file);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
    }

}
