package com.cooper.wheellog;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import java.text.DateFormat;
import timber.log.Timber;
import android.os.Environment;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class WheelLog extends Application
{
    public static LocaleManager localeManager;

    @Override
    public void onCreate()
    {
        Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler());
        super.onCreate();

        //Timber.plant(new FileLoggingTree(getApplicationContext()));
        //Timber.plant(new Timber.DebugTree());
        if (BuildConfig.DEBUG)
        {
            Timber.plant(new Timber.DebugTree());
            Timber.plant(new FileLoggingTree(getApplicationContext()));
        }
    }

    @Override
    protected void attachBaseContext(Context base)
    {
        localeManager = new LocaleManager(base);
        super.attachBaseContext(LocaleManager.setLocale(base));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
        LocaleManager.setLocale(this);
    }
}

final class ExceptionHandler implements Thread.UncaughtExceptionHandler {

    private final DateFormat formatter = new SimpleDateFormat("dd.MM.yy HH:mm");
    private final DateFormat fileFormatter = new SimpleDateFormat("dd-MM-yy");
    private String versionName = "0";
    private int versionCode = 0;
    private final String stacktraceDir;
    private final Thread.UncaughtExceptionHandler previousHandler;

    ExceptionHandler() {
        previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        stacktraceDir = String.format("/Download/trace/");
    }

    static ExceptionHandler inContext() {
        return new ExceptionHandler();
    }

    static ExceptionHandler reportOnlyHandler() {
        return new ExceptionHandler();
    }

    @Override
    public void uncaughtException(Thread thread, Throwable exception) {
        final String state = Environment.getExternalStorageState();
        final Date dumpDate = new Date(System.currentTimeMillis());
        if (Environment.MEDIA_MOUNTED.equals(state)) {

            StringBuilder reportBuilder = new StringBuilder();
            reportBuilder
                    .append("\n\n\n")
                    .append(formatter.format(dumpDate)).append("\n")
                    .append(String.format("Version: %s (%d)\n", versionName, versionCode))
                    .append(thread.toString()).append("\n");
            processThrowable(exception, reportBuilder);

            File sd = Environment.getExternalStorageDirectory();
            File stacktrace = new File(
                    sd.getPath() + stacktraceDir,
                    "stacktrace.txt");
            File dumpdir = stacktrace.getParentFile();
            boolean dirReady = dumpdir.isDirectory() || dumpdir.mkdirs();
            if (dirReady) {
                FileWriter writer = null;
                try {
                    writer = new FileWriter(stacktrace, true);
                    writer.write(reportBuilder.toString());
                } catch (IOException e) {
                    // ignore
                    double s2 = 1;
                } catch (Exception e)
                {
                    double s1 = 1;
                    // ignore
                } finally {
                    try {
                        if (writer != null)
                            writer.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
        }
        //if(previousHandler != null)
        //    previousHandler.uncaughtException(thread, exception);
    }

    private void processThrowable(Throwable exception, StringBuilder builder) {
        if(exception == null)
            return;
        StackTraceElement[] stackTraceElements = exception.getStackTrace();
        builder
                .append("Exception: ").append(exception.getClass().getName()).append("\n")
                .append("Message: ").append(exception.getMessage()).append("\nStacktrace:\n");
        for(StackTraceElement element : stackTraceElements) {
            builder.append("\t").append(element.toString()).append("\n");
        }
        processThrowable(exception.getCause(), builder);
    }
}