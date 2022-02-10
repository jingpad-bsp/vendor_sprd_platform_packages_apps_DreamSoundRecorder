package com.sprd.soundrecorder.data;


import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteFullException;
import android.net.Uri;
import android.os.SystemClock;
import android.os.EnvironmentEx;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.provider.DocumentsContract;
import android.preference.PreferenceManager;
import android.support.v4.provider.DocumentFile;

import com.android.soundrecorder.R;
import com.android.soundrecorder.Recorder;
import com.android.soundrecorder.SoundRecorder;
import com.sprd.soundrecorder.EmojiUtil;
import com.sprd.soundrecorder.RecorderActivity;
import com.sprd.soundrecorder.StorageInfos;
import com.sprd.soundrecorder.Utils;
import com.sprd.soundrecorder.frameworks.StandardFrameworks;
import com.sprd.soundrecorder.service.SprdRecorder;
import com.sprd.soundrecorder.MultiChooseActivity;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by jian.xu on 2017/10/10.
 */

public class DataOpration {
    private final static String TAG = DataOpration.class.getSimpleName();
    public final static String SOUNDREOCRD_TYPE_AND_DTA = "soundrecord.type.and.data";
    public final static String SYSTEM_VERSION = "systemVersion";
    public final static String[] DATA_COLUMN = new String[]{
            MediaStore.Audio.Media._ID, // 0
            MediaStore.Audio.Media.DATA, //1
            MediaStore.Audio.Media.SIZE, //2
            MediaStore.Audio.Media.TITLE, //3
            MediaStore.Audio.Media.DISPLAY_NAME, //4
            MediaStore.Audio.Media.DATE_MODIFIED, //5
            MediaStore.Audio.Media.MIME_TYPE, //6
            MediaStore.Audio.Media.DURATION, //7
            MediaStore.Audio.Media.BOOKMARK //8
    };
    public static final String CALL_COMPOSER = "Call Record";
    public static final String COMPOSER = "FMSoundRecorder";
    public final static String PREF_SDCARD_URI = "pref_saved_sdcard_uri";

    public enum RenamefailReason {
        OK,
        NO_FILE,
        SAME_NAME,
        OTHER
    }

    public final static int INPUT_MAX_LENGTH = 50;

    public final static int ANDROID_SDK_VERSION_DEFINE_Q = 29;

    private final static boolean SEARCH_WITH_PATH = false;

    private static String makeQueryString(Context context, boolean isCallData) {

        StringBuilder where = new StringBuilder();

        if (SEARCH_WITH_PATH) {
            SharedPreferences sharedPreferences = context.getSharedPreferences(SOUNDREOCRD_TYPE_AND_DTA, Context.MODE_PRIVATE);
            String systemVersion = sharedPreferences.getString(SYSTEM_VERSION, "0");
            if (systemVersion.equals("0")) {
                where.append("(");
                String[] mimetypes = Recorder.getSupportRecordMimeType();
                for (int i = 0; i < mimetypes.length; i++) {
                    where.append(MediaStore.Audio.Media.MIME_TYPE)
                            .append("='")
                            .append(mimetypes[i]);
                    if (i < mimetypes.length - 1) {
                        where.append("' or ");
                    } else {
                        where.append("') and (");
                    }
                }
                //Todo: get call recorder default path
                String defaultExternalPath = StorageInfos.getExternalStorageDefaultPath();
                String defaultInternalPath = StorageInfos.getInternalStorageDefaultPath();
                String defaultOtgPath = StorageInfos.getOtgStorageDefaultPath();

                if (defaultInternalPath != null) {
                    where.append(MediaStore.Audio.Media.DATA)
                            .append(" like '")
                            .append(defaultInternalPath);
                }

                if (StorageInfos.isExternalStorageMounted() && defaultExternalPath != null) {
                    where.append("%' or ")
                            .append(MediaStore.Audio.Media.DATA)
                            .append(" like '")
                            .append(defaultExternalPath);
                }

                if (defaultOtgPath != null) {
                    where.append("%' or ")
                            .append(MediaStore.Audio.Media.DATA)
                            .append(" like '")
                            .append(defaultOtgPath);
                }
                where.append("%')");
            }
        } else {
            where.append(MediaStore.Audio.Media.COMPOSER)
                    .append("='");
            if (isCallData) {
                where.append(CALL_COMPOSER);
            } else {
                where.append(COMPOSER);
            }
            where.append("'");

        }
        return where.toString();
    }

    public static ArrayList<RecorderItem> getRecorderData(final Context context, final boolean isCallData) {
        final ArrayList<RecorderItem> result = new ArrayList<RecorderItem>();

        String where = makeQueryString(context, isCallData);
        Log.d(TAG, "the query where string is " + where);
        Cursor cursor = null;
        String sortOder = MediaStore.Audio.Media.DATE_ADDED + " desc";
        try {
            cursor = context.getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    DATA_COLUMN, where, null, sortOder);
            if (cursor != null) {
                Log.d(TAG, "query count:" + cursor.getCount());
                for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                    RecorderItem item = new RecorderItem();
                    item.setId(cursor.getLong(0));
                    item.setData(cursor.getString(1));
                    item.setSize(cursor.getLong(2));
                    item.setTitle(cursor.getString(3));
                    item.setDisplayName(cursor.getString(4));
                    item.setDateModify(cursor.getLong(5));
                    item.setMimeType(cursor.getString(6));
                    item.setDuration(cursor.getInt(7));
                    item.setTagNumber(cursor.getInt(8));
                    result.add(item);
                }
            }
        } catch (Exception e) {
            Log.v(TAG, "RecordingFileListTabUtils.CursorRecorderAdapter failed; E: " + e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        if (SEARCH_WITH_PATH) {
            SharedPreferences sharedPreferences = context.getSharedPreferences(SOUNDREOCRD_TYPE_AND_DTA, Context.MODE_PRIVATE);
            String systemVersion = sharedPreferences.getString(SYSTEM_VERSION, "0");
            if (systemVersion.equals("0") && result.size() > 0) {
                new Thread(new Runnable() {

                    @Override
                    public void run() {
                        ContentValues cv = new ContentValues();
                        if (isCallData) {
                            cv.put(MediaStore.Audio.Media.COMPOSER, CALL_COMPOSER);
                        } else {
                            cv.put(MediaStore.Audio.Media.COMPOSER, COMPOSER);
                        }
                        ContentResolver resolver = context.getContentResolver();
                        for (int i = 0; i < result.size(); i++) {
                            Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, result.get(i).getId());
                            Log.d(TAG, "query(): update COMPOSER to MediaStore, id=" + result.get(i).getId());
                            resolver.update(uri, cv, null, null);
                        }
                    }
                }).start();
                SharedPreferences.Editor edit = sharedPreferences.edit();
                edit.putString(SYSTEM_VERSION, android.os.Build.VERSION.RELEASE);
                edit.commit();
            }
        }
        return result;
    }

    public static boolean deleteItems(Context context, SparseArray<RecorderItem> itemDatas) {
        //bug 1194126 if itemDatas's size is 0 should return false
        Log.d(TAG, "deleteItems select item :" + itemDatas.size());
        if (itemDatas.size() == 0) {
            return false;
        }
        StringBuilder where = new StringBuilder();
        where.append(MediaStore.Audio.Media._ID + " IN (");
        for (int i = 0; i < itemDatas.size(); i++) {
            where.append(itemDatas.valueAt(i).getId());
            if (i < itemDatas.size() - 1) {
                where.append(",");
            }
        }
        where.append(")");
        try {
            context.getContentResolver().delete(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, where.toString(), null);
        } catch (SQLiteFullException e) {
            Log.e(TAG, "Database or disk is full.");
            return false;
        }
        for (int i = 0; i < itemDatas.size(); i++) {
            File del = new File(itemDatas.valueAt(i).getData());

            if (del.getAbsolutePath().startsWith(EnvironmentEx.getExternalStoragePath().getAbsolutePath()) &&
                        del.getAbsolutePath().contains("voicecall")) {
                deleteFileSAF(getSavedSdcardUri(context), del.getAbsolutePath(), context);
            } else {
                if (!del.exists() || !del.delete()) {
                    continue;
                }
                WaveDataManager.delete(context, itemDatas.valueAt(i).getTitle(),
                    itemDatas.valueAt(i).getMimeType());
            }
        }
        return true;
    }

    public static RenamefailReason renameFile(Context context, RecorderItem data, String newtitle) {
        String oldDisplayName = data.getDisplayName();
        String extension = oldDisplayName.substring(oldDisplayName.lastIndexOf("."), oldDisplayName.length());
        String oldDataPath = data.getData();
        File oldFile = new File(oldDataPath);
        if (!oldFile.exists()) {
            Log.d(TAG, "old file is not exists");
            return RenamefailReason.NO_FILE;
        }
        String newDataPath = oldFile.getParent() + File.separator + newtitle + extension;
        File newFile = new File(newDataPath);
        String[] newDataPathDir = newDataPath.split("/");
        String newDataPathDirec = newDataPathDir[2].toLowerCase();
        if (newFile.exists() && newFile.isDirectory()) {
            return RenamefailReason.SAME_NAME;
        }
        //bug：992179 When a file is named with an existing file name, add the judgment that the file name already exists
        //bug 1192852 can use try-catch-resource to close cursor
        try (Cursor c = context.getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                DATA_COLUMN, MediaStore.Audio.Media.DATA + "=?", new String[]{newDataPath}, null)) {
            if (c != null && c.getCount() > 0) {
                /* Unisoc Bug 1132817 : variable c need to be closed after usage @{*/
                return RenamefailReason.OTHER;
                /* Bug 1132817 @}*/
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (newFile.getAbsolutePath().startsWith(EnvironmentEx.getExternalStoragePath().getAbsolutePath()) &&
                    newFile.getAbsolutePath().contains("voicecall")) {
            try {
                if (renameFileSAF(context, getSavedSdcardUri(context), oldDataPath, newFile)) {
                    ContentResolver resolver = context.getContentResolver();
                    //bug:1094408 use new SAF issue to get SD write permission
                    ContentValues values = new ContentValues(1);
                    values.put(MediaStore.Audio.Media.COMPOSER, CALL_COMPOSER);
                    resolver.update(MediaStore.Audio.Media.getContentUri(newDataPathDirec),
                            values,
                            MediaStore.Audio.Media.DATA + "=? AND " + MediaStore.Audio.Media.DISPLAY_NAME + "=?",
                            new String[]{newDataPath + "" ,newtitle + extension + ""});
                    return RenamefailReason.OK;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            try {
                if (oldFile.renameTo(newFile)) {
                    WaveDataManager.rename(context, data.getTitle(), newtitle);
                    ContentResolver resolver = context.getContentResolver();
                    ContentValues values = new ContentValues(3);
                    values.put(MediaStore.Audio.Media.DATA, newDataPath);
                    values.put(MediaStore.Audio.Media.TITLE, newtitle);
                    values.put(MediaStore.Audio.Media.DISPLAY_NAME, newtitle + extension);
                    if (android.os.Build.VERSION.SDK_INT < ANDROID_SDK_VERSION_DEFINE_Q || (newDataPathDirec.equals("emulated"))) {
                        resolver.update(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                values,
                                MediaStore.Audio.Media._ID + "=?",
                                new String[]{data.getId() + ""});
                    } else {
                        resolver.update(MediaStore.Audio.Media.getContentUri(newDataPathDirec),
                                values,
                                MediaStore.Audio.Media._ID + "=?",
                                new String[]{data.getId() + ""});
                    }
                    return RenamefailReason.OK;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return RenamefailReason.OTHER;
    }

    public static void shareFiles(Context context, ArrayList<Uri> uris) {
        boolean multiple = uris.size() > 1;
        Intent intent = new Intent(multiple ? Intent.ACTION_SEND_MULTIPLE : Intent.ACTION_SEND);
        if (multiple) {
            intent.setType("audio/*");
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        } else {
            intent.setType("audio/*");
            intent.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        }
        intent = Intent.createChooser(intent, context.getString(R.string.operate_share));
        intent.setFlags(intent.getFlags() |Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(intent);
    }

    public static Uri addToMediaDB(File file, Context context,
                                   long duration, String requestedType, int tagNumber) {
        if (file == null) return null;
        ContentValues cv = new ContentValues();
        long current = SystemClock.elapsedRealtime();
        String title;
        String fileName = file.getName();
        if (fileName.lastIndexOf(".") < 0) {
            title = fileName;
            Log.d(TAG, "addToMediaDBtitle =" + title);
        } else {
            Log.d(TAG, "addToMediaDBfileName =" + fileName);
            title = fileName.substring(0, fileName.lastIndexOf("."));
        }

        cv.put(MediaStore.Audio.Media.IS_DRM , "0");
        cv.put(MediaStore.Audio.Media.IS_MUSIC, "1");
        cv.put(MediaStore.Audio.Media.TITLE, title);
        cv.put(MediaStore.Audio.Media.DATA, file.getAbsolutePath());
        cv.put(MediaStore.Audio.Media.DATE_ADDED, (int) (current / 1000));
        cv.put(MediaStore.Audio.Media.DATE_MODIFIED, (int) (file.lastModified() / 1000));
        cv.put(MediaStore.Audio.Media.DURATION, duration);
        cv.put(MediaStore.Audio.Media.MIME_TYPE, requestedType);
        cv.put(MediaStore.Audio.Media.ARTIST,
                context.getString(R.string.audio_db_artist_name));
        cv.put(MediaStore.Audio.Media.ALBUM,
                context.getString(R.string.audio_db_album_name));
        cv.put(StandardFrameworks.getInstances().getMediaStoreAlbumArtist(),
                context.getString(R.string.audio_db_artist_name));
        cv.put(MediaStore.Audio.Media.COMPOSER, SoundRecorder.COMPOSER);
        cv.put(MediaStore.Audio.Media.BOOKMARK, tagNumber);

        Log.d(TAG, "Inserting audio record: " + cv.toString());
        Uri result;
        String[] filePathDir = file.getAbsolutePath().split("/");
        String filePathDirec = filePathDir[2].toLowerCase();
        try {
        if (android.os.Build.VERSION.SDK_INT < ANDROID_SDK_VERSION_DEFINE_Q || (filePathDirec.equals("emulated"))) {
                result = context.getContentResolver().insert(
                       MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cv);
        } else {
                result = context.getContentResolver().insert(
                       MediaStore.Audio.Media.getContentUri(filePathDirec), cv);
        }
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
        if (result == null) {
            return null;
        }

        if (getRecorderPlaylistId(context) == -1) {
            createPlaylist(context);
        }
        int audioId = Integer.valueOf(result.getLastPathSegment());
        addToPlaylist(context, audioId, getRecorderPlaylistId(context));

        return result;
    }

    public static String getRecordFileId(Context context, String title, String requestedType) {
        Log.d(TAG, " title = " + title + ", requestedType = " + requestedType);
        if (title != null && context != null) {
            Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            final String[] ids = new String[]{MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.MIME_TYPE};
            final String where = MediaStore.Audio.Media.TITLE + "=? " + "AND "
                    + MediaStore.Audio.Media.MIME_TYPE + "=?";
            final String[] args = new String[]{title, requestedType};
            Cursor cursor = context.getContentResolver().query(uri, ids, where, args, null);
            int id = -1;
            if (cursor != null) {
                if (cursor.moveToFirst() && !cursor.isAfterLast()) {
                    id = cursor.getInt(0);
                }
                cursor.close();
            }
            Log.d(TAG, "id = " + id);
            return String.valueOf(id);
        }
        return title;
    }

    private static int getRecorderPlaylistId(Context context) {
        Uri uri = MediaStore.Audio.Playlists.getContentUri("external");
        final String[] ids = new String[]{MediaStore.Audio.Playlists._ID};
        final String where = MediaStore.Audio.Playlists.NAME + "=?";
        final String[] args = new String[]{SprdRecorder.PLAYLIST_NAME};
        Cursor cursor = context.getContentResolver().query(uri, ids, where, args, null);
        if (cursor == null) {
            Log.v(TAG, "getRecorderPlaylistId returns null");
        }
        int id = -1;
        if (cursor != null) {
            cursor.moveToFirst();
            if (!cursor.isAfterLast()) {
                id = cursor.getInt(0);
            }
            cursor.close();
        }

        return id;
    }

    private static Uri createPlaylist(Context context) {
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Audio.Playlists.NAME, "My recordings");
        Uri uri = context.getContentResolver().insert(MediaStore.Audio.Playlists.getContentUri("external"), cv);
        if (uri == null) {
            Toast.makeText(context, R.string.error_mediadb_new_record, Toast.LENGTH_SHORT)
                    .show();
        }
        return uri;
    }

    private static void addToPlaylist(Context context, int audioId, long playlistId) {
        String[] cols = new String[]{
                MediaStore.Audio.Media._ID
        };
        Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId);
        Cursor cur = context.getContentResolver().query(uri, cols, null, null, null);
        //cur.moveToFirst();
        if (cur != null) {
            final int base = cur.getCount();
            cur.close();
            ContentValues values = new ContentValues();
            values.put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, Integer.valueOf(base + audioId));
            values.put(MediaStore.Audio.Playlists.Members.AUDIO_ID, audioId);
            context.getContentResolver().insert(uri, values);
        }
    }

    public static Dialog showRenameConfirmDialog(final Context context,
                                                 final String fileFullPath,
                                                 final RecorderItem item) {

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
        Log.d(TAG, "showRenameConfirmDialog, file:" + fileFullPath);
        String fileName = new File(fileFullPath).getName();
        int index = fileName.lastIndexOf(".");
        final String beforeName;
        final String mFileExtension;

        if (index > -1) {
            beforeName = fileName.substring(0, index);
            mFileExtension = fileName.substring(index);
        } else {
            return null;
        }

        final String parentPath = new File(fileFullPath).getParent();
        Log.d(TAG, "showRenameConfirmDialog,beforeName:" + beforeName + ",parentPath:" + parentPath + ",fileextension:" + mFileExtension);
        newName.setText(beforeName);
        newName.setLines(2);
        /* bug:957817 the limit toast show unnormal @{*/
        newName.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DataOpration.INPUT_MAX_LENGTH) {
            Toast toast = null;

            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest,int dstart, int dend) {
                int keep = DataOpration.INPUT_MAX_LENGTH - (dest.length() - (dend - dstart));
                int totallength = dest.length() - (dend - dstart) + (end - start);
                if (keep <= 0) {
                    //bug 1118174 not accumulate toast display time
                    toast = Utils.showToastWithText(context, toast,
                            context.getString(R.string.input_length_overstep), Toast.LENGTH_LONG);
                    return "";// do not change the original character
                } else if (keep >= end - start) {
                    return null;
                } else {
                    // add for bug 967698
                    if (totallength > DataOpration.INPUT_MAX_LENGTH) {
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
        /* @} */
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
                    // bug906609 show wrong message when we abandon the recording of less than 1 sec
                    ((RecorderActivity) context).renameOprateCancle(true);
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
                    ;
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
                        DataOpration.RenamefailReason result =
                                DataOpration.renameFile(context, item,
                                        fileName);
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
                    } else if (context instanceof RecorderActivity) {
                        ((RecorderActivity) context).stopRecord(fileName);
                    }
                    alertDialog.dismiss();
                }
            }

        });
        return alertDialog;
    }

    /* Bug913590, Use SAF to get SD write permission @{ */
    public static void deleteFileSAF(Uri uri, String mDataPath, Context context) {
        String[] s = DocumentsContract.getTreeDocumentId(uri).split(":");
        String RelativePath = mDataPath.substring(mDataPath.indexOf(s[0].toString()) + s[0].toString().length());

        Uri fileUri = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri) + RelativePath);

        try {
            DocumentsContract.deleteDocument(context.getContentResolver(), fileUri);
        } catch (Exception e) {
            Log.e(TAG, "could not delete document ", e);
        }
    }

    private static boolean renameFileSAF(Context context, Uri sdRootUri, String mOldFilePath, File mNewFile) {
        //bug 1192852
        /*boolean result = false;
        String[] s = DocumentsContract.getTreeDocumentId(sdRootUri).split(":");
        String RelativePath = mOldFilePath.substring(mOldFilePath.indexOf(s[0].toString()) + s[0].toString().length());
        Uri fileUri = DocumentsContract.buildDocumentUriUsingTree(sdRootUri,
                                     DocumentsContract.getTreeDocumentId(sdRootUri) + RelativePath);
        */
        DocumentFile mOldDocFile = getDocumentFileByPath(context, sdRootUri, mOldFilePath);
        if (mOldDocFile != null) {
            mOldDocFile.renameTo(mNewFile.getName());
        }


        return true;
    }

    public static DocumentFile getDocumentFileByPath(DocumentFile treeDocFile, String path) {
        DocumentFile document = treeDocFile;
        String[] parts = path.split("/");

        for (int i = 3; i < parts.length; i++) {
            if(document != null) {
                document = document.findFile(parts[i]);
            }
        }

        return document;
    }

    public static DocumentFile getDocumentFileByPath(Context context, Uri treeUri, String path) {
        DocumentFile treeDocument = DocumentFile.fromTreeUri(context, treeUri);

        return getDocumentFileByPath(treeDocument, path);
    }

    private static Uri getSavedSdcardUri(Context context) {
        try {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            String uri = sharedPreferences.getString(PREF_SDCARD_URI, null);

            if (uri != null) {
                return Uri.parse(uri);
            }
        } catch(Throwable e) {
            Log.e(TAG, "getSavedSdcardUri error, Throwable: ",e);
        }

        return null;
    }
    /* Bug913590 }@ */
}
