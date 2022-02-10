package com.sprd.soundrecorder;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.ContentObserver;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
//import androidx.appcompat.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.soundrecorder.R;
import com.sprd.soundrecorder.data.DataOpration;
import com.sprd.soundrecorder.data.RecordListAdapter;
import com.sprd.soundrecorder.data.RecorderItem;
import com.sprd.soundrecorder.data.ViewHolder;
import com.sprd.soundrecorder.ui.RecordItemFragment;
import com.sprd.soundrecorder.SdcardPermission;

import java.util.ArrayList;
import android.app.Activity;
import android.content.Intent;
import android.provider.DocumentsContract;
import android.net.Uri;
import android.os.storage.StorageManager;
import android.os.Environment;
import android.os.EnvironmentEx;
import android.os.storage.StorageVolume;
import java.util.List;
import java.io.File;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.content.SharedPreferences;
import android.widget.Button;
import android.widget.EditText;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.text.Spanned;
import android.annotation.SuppressLint;
import android.text.Editable;
import android.content.res.Configuration;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;

/**
 * Created by jian.xu on 2017/10/19.
 */

public class MultiChooseActivity extends AppCompatActivity {
    private static final String TAG = MultiChooseActivity.class.getSimpleName();
    public final static String QUERY_TYPE = "query_type";
    public final static String FILE_POS = "file_position";


    private ListView mListView;
    private Toolbar mToolBar;
    private TextView mToolBarTitle;
    private TextView mEmptyList;
    private MenuItem mShareMenuItem;
    private MenuItem mDeleteMeunItem;
    private MenuItem mSelectAllMeunSubItem;
    private MenuItem mSetRingMeunSubItem;
    private MenuItem mReNameMeunSubItem;
    private MenuItem mViewDetailsMeunSubItem;
    private Handler mMainHandler;

    private SparseArray<RecorderItem> mCheckedItems = new SparseArray<>();
    private AudioContentObserver mAudioObserver = new AudioContentObserver();
    private MyAdapter<RecorderItem> mAdapter;
    private RecordItemFragment.RecordType mRecordType = RecordItemFragment.RecordType.SOUND_RECORD_ITEM;

    private int mDefautCheckPos = 0;
    private Dialog mAlertDialog;

    private boolean hasExternalFile = false;
    private boolean mReceiverTag = false;
    private String mNewCallFileName = null;
    public final static String PREF_SDCARD_URI = "pref_saved_sdcard_uri";
    private static final int REQUEST_SDCARD_PERMISSION_DELETE = 1;
    private static final int REQUEST_SDCARD_PERMISSION_RENAME = 2;
    private static final int MAX_SHARE_NUM = 100;
    private Toast mToast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!RecordItemFragment.Ismulclick){
            finish();
            return;
        }
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.multi_choice_list_new);
//        View decorView = getWindow().getDecorView();
//        int option = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
//        decorView.setSystemUiVisibility(option);
//        getWindow().setStatusBarColor(getResources().getColor(R.color.status_bar_color));
        initResource();

        Intent intent = getIntent();
        if (intent != null) {
            mRecordType = (RecordItemFragment.RecordType) intent.getSerializableExtra(QUERY_TYPE);
            mDefautCheckPos = intent.getIntExtra(FILE_POS, 0);
        }
        mMainHandler = new Handler(getMainLooper());
        if (mAdapter == null) {
            mAdapter = new MyAdapter<>(this, R.layout.recording_file_item_choice);
        }
        mListView.setAdapter(mAdapter);
        queryFileAysnc(false);
        mAudioObserver.registerObserver();
        /* bug 1115537 ,1144241 refresh display when one file stored in SD or otg at least and the SD or otg is eject/Receiver not registered @{ */
        if (!mReceiverTag && mStorageReceiver != null) {
            IntentFilter intentSrorageFilter = new IntentFilter();
            intentSrorageFilter.addAction(Intent.ACTION_MEDIA_EJECT);
            intentSrorageFilter.addDataScheme("file");
            registerReceiver(mStorageReceiver, intentSrorageFilter);
            mReceiverTag = true;
        }

        /* }@ */
    }

    /*SPRD:937846 multichoose mune not update when return to this activity again @{*/
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG,"onResume");
        if (mSelectAllMeunSubItem != null) {
            if (mCheckedItems != null && mAdapter != null && mCheckedItems.size() == mAdapter.getData().size()) {
                mSelectAllMeunSubItem.setTitle(R.string.menu_recording_list_deselect_all);
            } else {
                mSelectAllMeunSubItem.setTitle(R.string.menu_recording_list_select_all);
            }
        }
    }
    /* @} */

    @Override
    protected void onStop() {
        super.onStop();
        //bug 1163480 dismiss dialog after switching languages
        if (!Utils.isTopActivity(this,getLocalClassName())) {
            dismissAlertDialog();
        }

    }

    private void dismissAlertDialog() {
        if (mAlertDialog != null) {
            mAlertDialog.dismiss();
            mAlertDialog = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        /* bug 1144241 Receiver not registered @{ */
        if (mReceiverTag && mStorageReceiver != null) {
            unregisterReceiver(mStorageReceiver);
            mReceiverTag = false;
        }
        /* }@ */
        if (mListView!=null){
            mListView.setAdapter(null);
            mAdapter = null;
            mAudioObserver.unregisterObserver();
        }
    }

    private void initResource() {
        mListView = (ListView) findViewById(android.R.id.list);
        mListView.setOnItemClickListener(mItemClickListener);
        mToolBar = (Toolbar) findViewById(R.id.id_toolbar);
        mToolBar.setOverflowIcon(getResources().getDrawable(R.drawable.ic_editor_menu));

//        mToolBar.setTitleTextColor(Color.parseColor("#333333"));
        setSupportActionBar(mToolBar);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        //bug:943183 back icon is not support the mirror shows
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        mToolBar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        mToolBar.setOnMenuItemClickListener(mToolBarMenuItemClickListener);
        mToolBarTitle = (TextView) findViewById(R.id.tv_toolbar);
        mEmptyList = (TextView) findViewById(R.id.emptylist);
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.mutli_choice, menu);
        mShareMenuItem = menu.findItem(R.id.item_share);
        mDeleteMeunItem = menu.findItem(R.id.item_delete);
        mSelectAllMeunSubItem = menu.findItem(R.id.sub_item_select_all);
        mReNameMeunSubItem = menu.findItem(R.id.sub_item_rename);
        mSetRingMeunSubItem = menu.findItem(R.id.sub_item_set_ring);
        mViewDetailsMeunSubItem = menu.findItem(R.id.sub_view_details);
        Log.d(TAG, "onCreateOptionsMenu end");
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        updateMenuItemVisiable();
        updateSeleceTitle();
        Log.d(TAG, "onCreateOptionsMenu end");
        return super.onCreateOptionsMenu(menu);
    }
    private void updateSeleceTitle(){
       int checkitemSize = mCheckedItems.size();
        if (checkitemSize == mAdapter.getData().size()) {
            mSelectAllMeunSubItem.setTitle(R.string.menu_recording_list_deselect_all);
        } else {
            mSelectAllMeunSubItem.setTitle(R.string.menu_recording_list_select_all);
        }
        //933925 update tile show
        mReNameMeunSubItem.setTitle(R.string.rename);
        mSetRingMeunSubItem.setTitle(R.string.set_as_ring);
        mViewDetailsMeunSubItem.setTitle(R.string.view_file_details);
        if (checkitemSize == 1) {
            mReNameMeunSubItem.setVisible(true);
            mSetRingMeunSubItem.setVisible(true);
            mViewDetailsMeunSubItem.setVisible(true);
        } else {
            mReNameMeunSubItem.setVisible(false);
            mSetRingMeunSubItem.setVisible(false);
            mViewDetailsMeunSubItem.setVisible(false);
        }
    }
    private void updateMenuItemVisiable() {
        int checkitemSize = mCheckedItems.size();
        if (mToolBarTitle != null) {
            mToolBarTitle.setText(String.valueOf(checkitemSize));
        }
        if (mReNameMeunSubItem == null) {
            return;
        }
        if (checkitemSize <= 0) {
            mShareMenuItem.setVisible(false);
            mDeleteMeunItem.setVisible(false);
        } else {
            mShareMenuItem.setVisible(true);
            mDeleteMeunItem.setVisible(true);
        }
    }

    private class MyAdapter<T> extends RecordListAdapter<T> {

        public MyAdapter(Context context, int listItemId) {
            super(context, listItemId);
        }

        @Override
        protected ViewHolder getViewHolder() {
            return new MyViewHolder();
        }

        @Override
        protected void initViewHolder(View listItem, ViewHolder viewHolder) {
            MyViewHolder vh = (MyViewHolder) viewHolder;
            vh.mDispalyNameView = listItem.findViewById(R.id.record_displayname);
            vh.mDurationView = listItem.findViewById(R.id.record_duration);
            vh.mDateView = listItem.findViewById(R.id.record_time);
            vh.mCheckBox = listItem.findViewById(R.id.recode_checkbox);
        }

        @Override
        protected void initListItem(final int position, View listItem, ViewGroup parent) {
            final RecorderItem data = (RecorderItem) getItem(position);
            final MyViewHolder vh = (MyViewHolder) listItem.getTag();
            vh.mDispalyNameView.setText(data.getDisplayName());
            vh.mDateView.setText(data.getDateString());
            vh.mDurationView.setText(data.getDurationString(MultiChooseActivity.this));


            if (mCheckedItems.indexOfKey(position) > -1) {
                vh.mCheckBox.setChecked(true);
            } else {
                vh.mCheckBox.setChecked(false);
            }
        }
    }

    private void queryFileAysnc(final boolean isFromAudioOberser) {
        AsyncTask<Void, Long, ArrayList<RecorderItem>> task =
                new AsyncTask<Void, Long, ArrayList<RecorderItem>>() {

                    @Override
                    protected ArrayList<RecorderItem> doInBackground(Void... params) {
                        return DataOpration.getRecorderData(MultiChooseActivity.this,
                                mRecordType == RecordItemFragment.RecordType.SOUND_RECORD_ITEM ? false : true);
                    }

                    @Override
                    protected void onProgressUpdate(Long... values) {

                    }

                    @Override
                    protected void onPostExecute(ArrayList<RecorderItem> data) {
                        if (!isDestroyed()) {
                            Log.d(TAG, "set data:" + data.size());
                            mAdapter.setData(data);
                            if (data.size()==0){
                                finish();
                                return;
                            }
                            if (data.size() > 0) {
                                mEmptyList.setVisibility(View.GONE);
                            } else {
                                mEmptyList.setVisibility(View.VISIBLE);
                            }
                            for (int i = 0; i < data.size(); i++) {
                                File del = new File(data.get(i).getData());
                                if (!del.getAbsolutePath().startsWith( "/storage/emulated/" ) )  {
                                    hasExternalFile = true;
                                    break;
                                }
                            }
                            if (!isFromAudioOberser && data.size() > mDefautCheckPos && mDefautCheckPos >= 0) {
                                mCheckedItems.put(mDefautCheckPos, data.get(mDefautCheckPos));
                                //Bug 1143997 Sets the currently selected item.
                                mListView.setSelection(mDefautCheckPos);
                                RecordItemFragment.Ismulclick =false;
                            } else {
                                mCheckedItems.clear();
                                updateSeleceTitle();
                            }
                            updateMenuItemVisiable();
                        }
                    }
                };
        task.execute((Void[]) null);
    }

    private class AudioContentObserver extends ContentObserver {
        public AudioContentObserver() {
            super(mMainHandler);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            Log.d(TAG, "AudioContentObserver audio change! reload data,selfchange:" + selfChange);
            queryFileAysnc(true);
        }

        public void registerObserver() {
            //bug 1184835 notify change and query file when rename to xxx.bin by document ui
            getContentResolver().registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    true, this);
        }

        public void unregisterObserver() {
            getContentResolver().unregisterContentObserver(this);
        }
    }

    private AdapterView.OnItemClickListener mItemClickListener =
            new AdapterView.OnItemClickListener() {

                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    MyViewHolder vh = (MyViewHolder) view.getTag();
                    if (vh.mCheckBox.isChecked()) {
                        vh.mCheckBox.setChecked(false);
                        mCheckedItems.remove(i);
                    } else {
                        vh.mCheckBox.setChecked(true);
                        mCheckedItems.put(i, mAdapter.getData().get(i));
                    }
                    updateMenuItemVisiable();
                }
            };
    private Toolbar.OnMenuItemClickListener mToolBarMenuItemClickListener =
            new Toolbar.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem menuItem) {
                    switch (menuItem.getItemId()) {
                        case R.id.item_share:
                            shareFiles();
                            break;
                        case R.id.item_delete:
                            showDeleteConfirmDialog();
                            break;
                        case R.id.sub_item_select_all:
                            //bug 1209101 coverity scan:comparison of String objects using == or !=
                            if (mSelectAllMeunSubItem.getTitle()
                                    .equals(getResources().getString(R.string.menu_recording_list_deselect_all))) {
                                mCheckedItems.clear();
                            } else {
                                mCheckedItems.clear();
                                ArrayList<RecorderItem> data = mAdapter.getData();
                                for (int i = 0; i < data.size(); i++) {
                                    mCheckedItems.put(i, data.get(i));
                                }
                            }
                            updateMenuItemVisiable();
                            mListView.invalidateViews();
                            break;
                        case R.id.sub_item_rename:
                            showRenameConfirmDialog();
                            break;
                        case R.id.sub_item_set_ring:
                            if (mCheckedItems.size() == 1) {
                                Utils.doChoiceRingtone(MultiChooseActivity.this, mCheckedItems.valueAt(0).getId());
                            }
                            break;
                        case R.id.sub_view_details:
                            if (mCheckedItems.size() == 1) {
                                showFileDetails(mCheckedItems.valueAt(0));
                            }
                            break;
                    }
                    return true;
                }
            };

    private void showFileDetails(RecorderItem data) {
        LayoutInflater flater =
                (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View filepathView = flater.inflate(R.layout.recording_file_details, null);
        TextView tvName = filepathView.findViewById(R.id.file_name_value);
        tvName.setText(data.getDisplayName());

        TextView tvPath = filepathView.findViewById(R.id.file_path_value);
        String absolutePath = data.getData();
        String filePath = absolutePath.substring(0, absolutePath.lastIndexOf('/'));
        tvPath.setText(filePath);

        TextView tvSize = filepathView.findViewById(R.id.file_size_value);
        tvSize.setText(data.getSizeString(this));

        TextView tvDate = filepathView.findViewById(R.id.file_date_value);
        tvDate.setText(data.getDateString());

        dismissAlertDialog();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        mAlertDialog = builder.setView(filepathView)
                .setNegativeButton(R.string.button_cancel,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dismissAlertDialog();
                            }
                        }).create();
        mAlertDialog.show();
    }

    private void showRenameConfirmDialog() {
        if (mCheckedItems.size() == 1) {
            dismissAlertDialog();
            mAlertDialog = showRenameConfirmDialogClone(this,
                    mCheckedItems.valueAt(0).getData(), mCheckedItems.valueAt(0));
        }
    }


    private void showDeleteConfirmDialog() {
        dismissAlertDialog();
        mAlertDialog = new AlertDialog.Builder(this)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setMessage(Utils.getNumberFormattedQuantityString(
                        MultiChooseActivity.this, R.plurals.confirm_delfile, mCheckedItems.size()))
                .setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                if (SdcardPermission.judgeIncludeSdcardAndCallFile(mCheckedItems.clone())) {
                                    SdcardPermission.requestSdRootDirectoryAccess(MultiChooseActivity.this, REQUEST_SDCARD_PERMISSION_DELETE);
                                } else {
                                    deleteFilesAsync();
                                }
                            }
                        }).create();
        mAlertDialog.setCanceledOnTouchOutside(false);
        mAlertDialog.show();
    }


    private void deleteFilesAsync() {
        AsyncTask<Void, Long, Boolean> task = new AsyncTask<Void, Long, Boolean>() {
            ProgressDialog progressDialog = null;
            private SparseArray<RecorderItem> copyItems = mCheckedItems.clone();

            @Override
            protected void onPreExecute() {
                progressDialog = new ProgressDialog(MultiChooseActivity.this);
                progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                //progressDialog.setIcon(android.R.drawable.ic_delete);
                progressDialog.setTitle(R.string.recording_file_delete_alert_title);
                progressDialog.setCancelable(false);
                //progressDialog.setMax(mCheckedItems.size());
                progressDialog.show();
            }

            @Override
            protected Boolean doInBackground(Void... params) {
                Log.i(TAG, "delete finished.");
                return DataOpration.deleteItems(MultiChooseActivity.this, copyItems);
            }

            @Override
            protected void onProgressUpdate(Long... values) {
                //pd.setProgress(values[0].intValue());
            }

            @Override
            protected void onPostExecute(Boolean result) {
                progressDialog.dismiss();
                Utils.showToastWithText(MultiChooseActivity.this, null,
                        result ? getString(R.string.recording_file_delete_success) : getString(R.string.recording_file_delete_failed),
                        Toast.LENGTH_SHORT);
            }
        };
        task.execute((Void[]) null);
    }

    private void shareFiles() {
        if (mCheckedItems.size() == 0) {
            return;
        }
        //bug 1171353, limit the number of recording files that can be shared.
        if (mCheckedItems.size() > MAX_SHARE_NUM) {
            mToast = Utils.showToastWithText(MultiChooseActivity.this, mToast,
                    getString(R.string.share_max_warning), Toast.LENGTH_SHORT);
        } else {
            ArrayList<Uri> sharedUris = new ArrayList<>();
            for (int i = 0; i < mCheckedItems.size(); i++) {
                sharedUris.add(ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mCheckedItems.valueAt(i).getId()));
            }
            DataOpration.shareFiles(this, sharedUris);
        }
        dismissAlertDialog();
    }

    private class MyViewHolder extends ViewHolder {
        public TextView mDispalyNameView;
        public TextView mDurationView;
        public TextView mDateView;
        public CheckBox mCheckBox;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Uri uri =null;
        if (data != null) {
            uri = data.getData();
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            sharedPreferences.edit().putString(PREF_SDCARD_URI, (uri == null) ? "" : uri.toString()).apply();
        }

        //bug :1149411 Coverity Warning, Not check the null returns,Passing null pointer uri to getTreeDocumentId, which dereferences it
        if (uri == null) {
            Log.e(TAG, "onActivityResult: uri is null, so do not delete  or rename");
            return;
        }

        //bug:1094408 use new SAF issue to get SD write permission
        String documentId = DocumentsContract.getTreeDocumentId(uri);
        if (!documentId.endsWith(":") || "primary:".equals(documentId) ) {
            SdcardPermission.showNoSdCallFileWritePermission(MultiChooseActivity.this, null);
            return;
        }

        if  (requestCode == REQUEST_SDCARD_PERMISSION_DELETE) {
            if  (resultCode == Activity.RESULT_OK) {
                SdcardPermission.getPersistableUriPermission(uri, data, MultiChooseActivity.this);
                deleteFilesAsync();
            //bug:1094408 use new SAF issue to get SD write permission
            } else {
                if (android.os.Build.VERSION.SDK_INT < DataOpration.ANDROID_SDK_VERSION_DEFINE_Q ) {
                    SdcardPermission.showNoSdWritePermission(MultiChooseActivity.this, null);
                } else {
                    SdcardPermission.showNoSdCallFileWritePermission(MultiChooseActivity.this, null);
                }
            }
        } else  if (requestCode == REQUEST_SDCARD_PERMISSION_RENAME) {
            if (resultCode == Activity.RESULT_OK) {
                 if (mNewCallFileName != null) {
                     SdcardPermission.getPersistableUriPermission(uri, data, MultiChooseActivity.this);
                     renameFile(this, mCheckedItems.valueAt(0), mNewCallFileName);
                 } else {
                     Log.e(TAG, "rename file error: mNewCallFileName is null");
                 }
            //bug:1094408 use new SAF issue to get SD write permission
            } else {
                if (android.os.Build.VERSION.SDK_INT < DataOpration.ANDROID_SDK_VERSION_DEFINE_Q ) {
                    SdcardPermission.showNoSdWritePermission(MultiChooseActivity.this, null);
                } else {
                    SdcardPermission.showNoSdCallFileWritePermission(MultiChooseActivity.this, null);
                }
            }
        }
        return;
    }

    private Dialog showRenameConfirmDialogClone(final Context context,
                             final String fileFullPath, final RecorderItem item) {
        if (fileFullPath==null){
            return null;
        }

        final Dialog alertDialog = new Dialog(context, 0);
        alertDialog.setContentView(R.layout.rename_save_dialog);
        Button okButton = alertDialog.findViewById(R.id.button_ok);
        Button cancelButton = alertDialog.findViewById(R.id.button_cancel);
        cancelButton.setText(R.string.rename_button);
        if (item != null) {
            ((TextView) alertDialog.findViewById(R.id.title)).setText(R.string.rename);
        }
        final EditText newName = alertDialog.findViewById(R.id.inputname);

        String fileName = new File(fileFullPath).getName();

        int index = fileName.lastIndexOf(".");
        final String beforeName;
        final String mFileExtension;

        if (index > -1) {
            beforeName = fileName.substring(0, index);
            mFileExtension = fileName.substring(index);

            Log.d(TAG, "beforeName:" + beforeName);
            Log.d(TAG, "mFileExtension:" + mFileExtension);
        } else {
            return null;
        }

        final String parentPath = new File(fileFullPath).getParent();
        Log.d(TAG, "showRenameConfirmDialogClone,beforeName:" + beforeName + ",parentPath:" + parentPath + ",fileextension:" + mFileExtension);
        newName.setText(beforeName);
        newName.setLines(2);
        //937648 the limit toast show unnormal
        newName.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DataOpration.INPUT_MAX_LENGTH) {
            Toast toast = null;

            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest,int dstart, int dend) {
                int keep = DataOpration.INPUT_MAX_LENGTH - (dest.length() - (dend - dstart));
                int totallength = dest.length() - (dend - dstart) + (end - start);
                if (keep <= 0) {
                    //bug 1107715 not accumulate toast display time
                    toast = Utils.showToastWithText(context, toast,
                            context.getString(R.string.input_length_overstep), Toast.LENGTH_LONG);
                    return "";// do not change the original character
                } else if (keep >= end - start) {
                    return null; // keep original
                } else {
                    //add for bug 967055
                    if (totallength > DataOpration.INPUT_MAX_LENGTH ) {
                        toast = Utils.showToastWithText(context, toast,
                                context.getString(R.string.input_length_overstep), Toast.LENGTH_LONG);
                    }
                    //Additional character length is less than the length of the remaining,
                    //Only add additional characters are part of it
                    keep += start;
                    if (Character.isHighSurrogate(source.charAt(keep - 1))) {
                        --keep;
                        if (keep == start) {
                            return "";
                        }
                    }
                    return source.subSequence(start, keep);
               }
           }
        }
        });
        newName.requestFocus();
        if (item == null) {
            alertDialog.setCancelable(false);
        }
        alertDialog.setCanceledOnTouchOutside(false);
        alertDialog.show();

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (context instanceof RecorderActivity) {
                    ((RecorderActivity) context).stopRecordNoSave();
                }
                alertDialog.dismiss();
            }
        });
        okButton.setOnClickListener(new View.OnClickListener() {
            Toast toast = null;

            @SuppressLint("StringFormatMatches")
            @Override
            public void onClick(View view) {

                String fileName = newName.getEditableText().toString().trim();
                String specialChar = Utils.fileNameFilter(fileName);
                String emojiChar = EmojiUtil.filterEmoji(fileName);

                if (fileName.isEmpty()) {
                    toast = Utils.showToastWithText(context,
                            toast, context.getString(R.string.filename_empty_error),
                            Toast.LENGTH_SHORT);
                } else if (fileName.equals(beforeName)) {
                    if (item == null && context instanceof RecorderActivity) {
                        ((RecorderActivity) context).stopRecord();
                        alertDialog.dismiss();
                    } else {
                        toast = Utils.showToastWithText(context,
                                toast, context.getString(R.string.filename_is_not_modified),
                                Toast.LENGTH_SHORT);
                    }
                } else if (!specialChar.isEmpty()) {
                    toast = Utils.showToastWithText(context,
                            toast, context.getString(R.string.illegal_chars_of_filename),
                            Toast.LENGTH_SHORT);
                } else if (EmojiUtil.containsEmoji(fileName)) {
                    toast = Utils.showToastWithText(context,
                            toast, context.getString(R.string.special_char_exist, emojiChar),
                            Toast.LENGTH_SHORT);
                } else if (item == null && new File(parentPath + "/" + fileName + mFileExtension).exists()) {
                    toast = Utils.showToastWithText(context,
                            toast, context.getString(R.string.file_rename),
                            Toast.LENGTH_SHORT);
                } else {
                    if (item != null) {
                        mNewCallFileName = fileName;

                        if (SdcardPermission.judgeRenameIsSdcardAndCallFile(fileFullPath)) {
                            SdcardPermission.requestSdRootDirectoryAccess(MultiChooseActivity.this, REQUEST_SDCARD_PERMISSION_RENAME);
                        } else {
                             DataOpration.RenamefailReason result =
                                DataOpration.renameFile(context, item, fileName);
                            if (result == DataOpration.RenamefailReason.OK) {
                                toast = Utils.showToastWithText(context,
                                    toast, context.getString(R.string.rename_save),
                                    Toast.LENGTH_SHORT);
                            } else {
                                Log.d(TAG, "rename fail:" + result);
                                toast = Utils.showToastWithText(context,
                                        toast, context.getString(R.string.rename_nosave),
                                        Toast.LENGTH_SHORT);
                            }
                        }

                    } else if (context instanceof RecorderActivity) {
                        ((RecorderActivity) context).stopRecord(fileName);
                    }
                    alertDialog.dismiss();
                }
            }

        });
        return alertDialog;
    }

    public void renameFile(Context context, RecorderItem data, String newtitle) {
        DataOpration.RenamefailReason result =
                DataOpration.renameFile(context, data, newtitle);

        if (result == DataOpration.RenamefailReason.OK) {
            Utils.showToastWithText(context,
                    null, context.getString(R.string.rename_save),
                    Toast.LENGTH_SHORT);
        } else {
            Log.d(TAG, "rename fail:" + result);
            Utils.showToastWithText(context,
                    null, context.getString(R.string.rename_nosave),
                    Toast.LENGTH_SHORT);
        }
    }
    /* Bug913590 }@ */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mAdapter.notifyDataSetChanged();
    }

    /* bug 1115537 refresh display when one file stored in SD or otg at least and the SD or otg is eject @{ */
    private BroadcastReceiver  mStorageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "onReceive:" + action );
            if ( Intent.ACTION_MEDIA_EJECT.equals(action) && hasExternalFile  ) {
                queryFileAysnc(true);
                dismissAlertDialog();
            }
        }
    };
    /* }@ */

}
