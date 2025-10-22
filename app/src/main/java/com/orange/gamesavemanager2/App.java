package com.orange.gamesavemanager2;

import android.app.Application;
import android.util.Log;

import net.sqlcipher.database.SQLiteDatabase;

/**
 * 全局 Application：注册未捕获异常处理并初始化日志
 */
public class App extends Application implements Thread.UncaughtExceptionHandler {

    private Thread.UncaughtExceptionHandler previousHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        
        // 先初始化日志系统
        AppLogger.init(this);
        
        // 初始化SQLCipher native库
        try {
            SQLiteDatabase.loadLibs(this);
            Log.i("App", "SQLCipher library loaded successfully");
            AppLogger.i("App", "SQLCipher native库加载成功");
        } catch (Exception e) {
            Log.e("App", "Failed to load SQLCipher library", e);
            AppLogger.e("App", "SQLCipher native库加载失败: " + e.getMessage());
        }
        
        previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
        AppLogger.i("App", "Application started");
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        try {
            String message = Log.getStackTraceString(e);
            AppLogger.e("Crash", "Uncaught exception in thread: " + t.getName() + "\n" + message);
        } catch (Throwable ignored) { }
        if (previousHandler != null) {
            previousHandler.uncaughtException(t, e);
        }
    }
}








