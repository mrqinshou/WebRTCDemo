package com.qinshou.webrtcdemo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Author: MrQinshou
 * Email: cqflqinhao@126.com
 * Date: 2021/8/21 22:34
 * Description:
 */
public class ShowLogUtil {
    private static final String TAG = "daolema";
    /**
     * 日志分割长度，如果日志内容过长，Android Studio 的 Logcat 会打印不全，所以限定一个最大长度，自动分割
     */
    private static final int MAX_LENGTH = 1024 * 2;
    private static final SimpleDateFormat sSimpleDataFormatForFile = new SimpleDateFormat("yyyy-MM-dd-HH-00-00", Locale.getDefault());
    private static final SimpleDateFormat sSimpleDataFormatForLog = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
    private static LogLevel sLogLevel = LogLevel.DEBUG;
    private static String sPath = null;
    private static BufferedWriter sBufferedWriter = null;
    private static Calendar sCalendar = null;
    private static String sPackageName = "";

    public enum LogLevel {
        DEBUG("D"), INFO("I"), WARN("W"), ERROR("E"), NONE("N");

        private final String mLetter;

        LogLevel(String letter) {
            mLetter = letter;
        }

        public static LogLevel getByLetter(String letter) {
            for (LogLevel logLevel : values()) {
                if (TextUtils.equals(logLevel.mLetter, letter)) {
                    return logLevel;
                }
            }
            return null;
        }
    }

    /**
     * Author: MrQinshou
     * Email: cqflqinhao@126.com
     * Date: 2021/10/22 17:55
     * Description: 监听时间变化
     */
    private static final BroadcastReceiver mTimeTickBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // 监听到时间变化
            Calendar calendar = Calendar.getInstance();
            if (calendar.get(Calendar.MINUTE) == calendar.getActualMinimum(Calendar.MINUTE)) {
                // 新的一小时
                try {
                    if (sBufferedWriter != null) {
                        sBufferedWriter.flush();
                    }
                    File file = new File(sPath + File.separator + sSimpleDataFormatForFile.format(new Date()) + ".log");
                    file.getParentFile().mkdirs();
                    if (!file.exists()) {
                        file.createNewFile();
                    }
                    sBufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true)));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    /**
     * Author: MrQinshou
     * Email: cqflqinhao@126.com
     * Date:
     * Description: 获取调用堆栈
     */
    private static String getStackTraceElement() {
        for (StackTraceElement stackTraceElement : Thread.currentThread().getStackTrace()) {
            if (!stackTraceElement.isNativeMethod()
                    && !TextUtils.equals(stackTraceElement.getClassName(), Thread.class.getName())
                    && !TextUtils.equals(stackTraceElement.getClassName(), ShowLogUtil.class.getName())) {
                // Remove pkg name.
//                return stackTraceElement.toString().substring(stackTraceElement.getClassName().lastIndexOf(".") + 1);
                return "(" + stackTraceElement.getFileName() + ":" + stackTraceElement.getLineNumber() + ")";
            }
        }
        return null;
    }

    private synchronized static void writeFile(String level, String tag, String log) {
        if (sBufferedWriter == null) {
            return;
        }
        String currentTime = sSimpleDataFormatForLog.format(new Date());
        // 2023-02-20 23:15:40.156 20690-20690/com.qinshou.box I/daolema(HomepageFragment.kt:79): xxx
        String string = currentTime +
                " " +
                android.os.Process.myPid() +
                "-" +
                android.os.Process.myTid() +
                "/" +
                sPackageName +
                " " +
                level +
                "/" +
                tag +
                ": " +
                log;
        try {
            sBufferedWriter.write(string);
            sBufferedWriter.newLine();
            sBufferedWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void setLogLevel(LogLevel logLevel) {
        sLogLevel = logLevel;
    }

    public static void setPath(Context context, String path) {
        sPath = path;
        File file = new File(sPath + File.separator + sSimpleDataFormatForFile.format(new Date()) + ".log");
        file.getParentFile().mkdirs();
        try {
            if (!file.exists()) {
                file.createNewFile();
            }
            sBufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true)));
        } catch (IOException e) {
            e.printStackTrace();
        }
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_TIME_TICK);
        context.registerReceiver(mTimeTickBroadcastReceiver, intentFilter);
        try {
            PackageManager packageManager = context.getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(context.getPackageName(), PackageManager.GET_ACTIVITIES);
            sPackageName = packageInfo.packageName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void verbose(Object log) {
        if (log == null) {
            verbose(TAG, "null");
        } else {
            verbose(TAG, log.toString());
        }
    }

    public static void verbose(String tag, String log) {
        String stackTraceElement = getStackTraceElement();
        if (stackTraceElement != null) {
            tag += stackTraceElement;
        }
        int a = log.length() / MAX_LENGTH;
        for (int i = 0; i <= a; i++) {
            String subLog;
            if (i == a) {
                subLog = log.substring(i * MAX_LENGTH);
            } else {
                subLog = log.substring(i * MAX_LENGTH, (i + 1) * MAX_LENGTH);
            }
            Log.v(tag, subLog);
            writeFile("V", tag, subLog);
        }
    }

    public static void debug(Object log) {
        if (log == null) {
            debug(TAG, "null");
        } else {
            debug(TAG, log.toString());
        }
    }

    public static void debug(String tag, String log) {
        if (sLogLevel.ordinal() > LogLevel.DEBUG.ordinal()) {
            return;
        }
        String stackTraceElement = getStackTraceElement();
        if (stackTraceElement != null) {
            tag += stackTraceElement;
        }
        int a = log.length() / MAX_LENGTH;
        for (int i = 0; i <= a; i++) {
            String subLog;
            if (i == a) {
                subLog = log.substring(i * MAX_LENGTH);
            } else {
                subLog = log.substring(i * MAX_LENGTH, (i + 1) * MAX_LENGTH);
            }
            Log.d(tag, subLog);
            writeFile("D", tag, subLog);
        }
    }


    public static void info(Object log) {
        if (log == null) {
            info(TAG, "null");
        } else {
            info(TAG, log.toString());
        }
    }

    public static void info(String tag, String log) {
        if (sLogLevel.ordinal() > LogLevel.INFO.ordinal()) {
            return;
        }
        String stackTraceElement = getStackTraceElement();
        if (stackTraceElement != null) {
            tag += stackTraceElement;
        }
        int a = log.length() / MAX_LENGTH;
        for (int i = 0; i <= a; i++) {
            String subLog;
            if (i == a) {
                subLog = log.substring(i * MAX_LENGTH);
            } else {
                subLog = log.substring(i * MAX_LENGTH, (i + 1) * MAX_LENGTH);
            }
            Log.i(tag, subLog);
            writeFile("I", tag, subLog);
        }
    }

    public static void warning(Object log) {
        if (log == null) {
            warning(TAG, "null");
        } else {
            warning(TAG, log.toString());
        }
    }

    public static void warning(String tag, String log) {
        if (sLogLevel.ordinal() > LogLevel.WARN.ordinal()) {
            return;
        }
        String stackTraceElement = getStackTraceElement();
        if (stackTraceElement != null) {
            tag += stackTraceElement;
        }
        int a = log.length() / MAX_LENGTH;
        for (int i = 0; i <= a; i++) {
            String subLog;
            if (i == a) {
                subLog = log.substring(i * MAX_LENGTH);
            } else {
                subLog = log.substring(i * MAX_LENGTH, (i + 1) * MAX_LENGTH);
            }
            Log.w(tag, subLog);
            writeFile("W", tag, subLog);
        }
    }

    public static void error(Object log) {
        if (log == null) {
            error(TAG, "null");
        } else {
            error(TAG, log.toString());
        }
    }

    public static void error(String tag, String log) {
        if (sLogLevel.ordinal() > LogLevel.ERROR.ordinal()) {
            return;
        }
        String stackTraceElement = getStackTraceElement();
        if (stackTraceElement != null) {
            tag += stackTraceElement;
        }
        int a = log.length() / MAX_LENGTH;
        for (int i = 0; i <= a; i++) {
            String subLog;
            if (i == a) {
                subLog = log.substring(i * MAX_LENGTH);
            } else {
                subLog = log.substring(i * MAX_LENGTH, (i + 1) * MAX_LENGTH);
            }
            Log.e(tag, subLog);
            writeFile("E", tag, subLog);
        }
    }
}