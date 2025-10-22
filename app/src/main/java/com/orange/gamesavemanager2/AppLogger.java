package com.orange.gamesavemanager2;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * 应用内日志系统：内存环形缓存 + 文件持久化
 */
public final class AppLogger {
    private static final String TAG = "AppLogger";
    private static final int MAX_LINES = 500;
    private static final Deque<String> buffer = new ArrayDeque<>();
    private static final List<Listener> listeners = new ArrayList<>();
    private static volatile boolean initialized = false;
    private static Context appContext;
    private static File logFile;
    private static final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    public interface Listener {
        void onLog(String line);
    }

    private AppLogger() {}

    public static synchronized void init(Context context) {
        if (initialized) return;
        initialized = true;
        appContext = context.getApplicationContext();
        
        // 初始化日志文件
        try {
            File logDir = new File(appContext.getFilesDir(), "logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            logFile = new File(logDir, "app_log.txt");
            
            // 如果日志文件太大（超过1MB），清空它
            if (logFile.exists() && logFile.length() > 1024 * 1024) {
                logFile.delete();
            }
            
            // 从文件加载历史日志到内存
            loadFromFile();
            
            writeToFile("========== 应用启动 ==========");
        } catch (Exception e) {
            Log.e(TAG, "初始化日志文件失败", e);
        }
    }

    public static synchronized void addListener(Listener l) {
        if (l == null) return;
        for (String line : buffer) {
            l.onLog(line);
        }
        listeners.add(l);
    }

    public static synchronized void removeListener(Listener l) {
        listeners.remove(l);
    }

    private static synchronized void append(String level, String tag, String msg) {
        String time = timeFormat.format(new Date());
        String line = String.format("[%s][%s][%s] %s", time, level, tag, msg);
        
        // 添加到内存缓冲区
        if (buffer.size() >= MAX_LINES) {
            buffer.removeFirst();
        }
        buffer.addLast(line);
        
        // 写入文件
        writeToFile(line);
        
        // 输出到Logcat
        switch (level) {
            case "E":
                Log.e(tag, msg);
                break;
            case "W":
                Log.w(tag, msg);
                break;
            case "I":
                Log.i(tag, msg);
                break;
            default:
                Log.d(tag, msg);
                break;
        }
        
        // 通知监听器
        for (Listener l : listeners) {
            l.onLog(line);
        }
    }

    /**
     * 从文件加载历史日志到内存缓冲区
     */
    private static void loadFromFile() {
        if (logFile == null || !logFile.exists()) return;
        
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.FileReader(logFile))) {
            String line;
            java.util.ArrayList<String> lines = new java.util.ArrayList<>();
            
            // 读取所有行
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            
            // 只保留最后MAX_LINES行
            int startIndex = Math.max(0, lines.size() - MAX_LINES);
            for (int i = startIndex; i < lines.size(); i++) {
                buffer.addLast(lines.get(i));
            }
            
            Log.i(TAG, "从文件加载了 " + buffer.size() + " 条历史日志");
        } catch (Exception e) {
            Log.e(TAG, "从文件加载日志失败", e);
        }
    }

    private static void writeToFile(String line) {
        if (logFile == null) return;
        
        new Thread(() -> {
            try (FileWriter fw = new FileWriter(logFile, true);
                 PrintWriter pw = new PrintWriter(fw)) {
                pw.println(line);
            } catch (Exception e) {
                Log.e(TAG, "写入日志文件失败", e);
            }
        }).start();
    }

    public static void d(String tag, String msg) { append("D", tag, msg); }
    public static void i(String tag, String msg) { append("I", tag, msg); }
    public static void w(String tag, String msg) { append("W", tag, msg); }
    public static void e(String tag, String msg) { append("E", tag, msg); }

    public static synchronized String dumpAll() {
        StringBuilder sb = new StringBuilder();
        for (String line : buffer) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    public static File getLogFile() {
        return logFile;
    }

    /**
     * 读取完整的日志文件内容
     */
    public static String readFullLogFile() {
        if (logFile == null || !logFile.exists()) {
            return "日志文件不存在";
        }
        
        StringBuilder sb = new StringBuilder();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.FileReader(logFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (Exception e) {
            Log.e(TAG, "读取日志文件失败", e);
            return "读取日志文件失败: " + e.getMessage();
        }
        
        return sb.toString();
    }

    public static synchronized void clear() {
        buffer.clear();
        if (logFile != null && logFile.exists()) {
            logFile.delete();
        }
        writeToFile("========== 日志已清空 ==========");
    }
}
