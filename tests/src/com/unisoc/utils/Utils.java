package com.unisoc.soundrecorder.tests.utils;

import android.app.Activity;
import android.content.*;
import android.app.Instrumentation;
import android.content.Intent;
import android.test.ActivityInstrumentationTestCase;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;
import android.view.KeyEvent;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.content.ContentResolver;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;

import android.app.Instrumentation.ActivityMonitor;
import android.os.Handler;
import android.widget.ImageButton;
import android.widget.Button;

import com.android.soundrecorder.R;
import com.sprd.soundrecorder.RecorderActivity;
import com.sprd.soundrecorder.RecordListActivity;
import com.sprd.soundrecorder.data.*;
import com.sprd.soundrecorder.MultiChooseActivity;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * getMethod or getDeclaredField
 */
public class Utils {
    private static final String TAG = "Utils";

    static public Object getFileldObjByClassObj(Object obj, String fieldname) {
        Object object = null;
        Field fields = null;

        if (obj == null){
            return object;
        }

        if (obj.getClass() != null) {
            fields = getFieldByClass(obj.getClass(), fieldname);
        }

        if (fields != null) {
            try {
                object = fields.get(obj);
                return object;
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
        } else {
            fields = getFieldByClass((Class)obj, fieldname);
            if (fields != null) {
                try {
                    object = fields.get(obj);
                    return object;
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                }
            }
        }

        return object;
    }

    static public Object getFileldObjByClass(Class cls, String fieldname) {
        Object object = null;
        Field fields = null;

        if (cls == null){
            return object;
        }

        if (cls != null) {
            fields = getFieldByClass(cls, fieldname);
        }

        if (fields != null) {
            try {
                object = fields.get(cls);
                return object;
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
        } else {
            fields = getFieldByClass(cls, fieldname);
            if (fields != null) {
                try {
                    object = fields.get(cls);
                    return object;
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                }
            }
        }

        return object;
    }

    static public Field getFieldByClass(Class cls, String fieldname) {
        Field fields = null;

        while (cls != null) {
            try {
                fields = cls.getDeclaredField(fieldname);
                fields.setAccessible(true);
                return fields;
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
                cls = cls.getSuperclass();
            }
        }
        android.util.Log.d(TAG, "getFieldByClass: could not get fieldname: " + fieldname);
        return fields;
    }

    static public Object callFuncByClassObj(Object obj, String function) {
        Object object = null;

        try {
            Class clas = obj.getClass();
            Method method;
            try {
                method = clas.getMethod(function);
            } catch (NoSuchMethodException e) {
                method = clas.getDeclaredMethod(function);
            }
            method.setAccessible(true);
            object = method.invoke(obj);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            android.util.Log.d(TAG, "callFuncByClassObj: not found func: " + function);
            e.printStackTrace();
        }

        return object;
    }

    static public Object callFuncByClassObj(Object obj, String function, Class[] parameterTypes, Object... paramterValue) {
        Object object = null;

        try {
            Class clas = obj.getClass();
            Method method;
            try {
                method = clas.getMethod(function, parameterTypes);
            } catch (NoSuchMethodException e) {
                method = clas.getDeclaredMethod(function, parameterTypes);
            }
            method.setAccessible(true);
            object = method.invoke(obj, paramterValue);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            android.util.Log.d(TAG, "callFuncByClassObj: not found func: " + function);
            e.printStackTrace();
        }

        return object;
    }


    static public Object callFuncByClass(Class clas, String function) {
        Object object = null;

        try {
            Method method;
            try {
                method = clas.getMethod(function);
            } catch (NoSuchMethodException e) {
                method = clas.getDeclaredMethod(function);
            }
            method.setAccessible(true);
            object = method.invoke(clas);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            android.util.Log.d(TAG, "callFuncByClassObj: not found func: " + function);
            e.printStackTrace();
        }

        return object;
    }

    static public Object callFuncByClass(Class clas, String function, Class[] parameterTypes, Object... paramterValue) {
        Object object = null;

        try {
            Method method;
            try {
                method = clas.getMethod(function, parameterTypes);
            } catch (NoSuchMethodException e) {
                method = clas.getDeclaredMethod(function, parameterTypes);
            }
            method.setAccessible(true);
            object = method.invoke(clas, paramterValue);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            android.util.Log.d(TAG, "callFuncByClassObj: not found func: " + function);
            e.printStackTrace();
        }

        return object;
    }
 }

