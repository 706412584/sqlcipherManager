package com.orange.gamesavemanager2;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;

/**
 * 主界面Activity - 游戏存档管理器演示
 * 提供各个功能模块的入口
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    // UI组件
    private Button btnDatabaseDemo;
    private Button btnTableManagement;
    private Button btnDataExport;
    private Button btnOptimization;
    private Button btnAdvancedTableMgmt;
    private Button btnClearMainLog;
    private Button btnViewLogFile;
    private TextView tvMainLog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupClickListeners();
    }

    /**
     * 初始化UI组件
     */
    private void initViews() {
        btnDatabaseDemo = findViewById(R.id.btn_database_demo);
        btnTableManagement = findViewById(R.id.btn_table_management);
        btnDataExport = findViewById(R.id.btn_data_export);
        btnOptimization = findViewById(R.id.btn_optimization);
        btnAdvancedTableMgmt = findViewById(R.id.btn_advanced_table_mgmt);
        btnClearMainLog = findViewById(R.id.btn_clear_main_log);
        btnViewLogFile = findViewById(R.id.btn_view_log_file);
        tvMainLog = findViewById(R.id.tv_main_log);
        
        if (tvMainLog != null) {
            // 加载历史日志
            String historyLogs = AppLogger.dumpAll();
            if (historyLogs != null && !historyLogs.isEmpty()) {
                tvMainLog.setText(historyLogs);
                // 延迟滚动到底部
                tvMainLog.post(() -> scrollToBottom());
            } else {
                tvMainLog.setText("暂无日志\n");
            }
            
            // 添加监听器接收新日志
            AppLogger.addListener(line -> runOnUiThread(() -> {
                if (tvMainLog != null) {
                    tvMainLog.append(line + "\n");
                    scrollToBottom();
                }
            }));
        }
    }
    
    /**
     * 滚动日志到底部
     */
    private void scrollToBottom() {
        if (tvMainLog != null && tvMainLog.getLayout() != null) {
            int scrollAmount = tvMainLog.getLayout().getLineTop(tvMainLog.getLineCount()) 
                             - tvMainLog.getHeight();
            if (scrollAmount > 0) {
                tvMainLog.scrollTo(0, scrollAmount);
            } else {
                tvMainLog.scrollTo(0, 0);
            }
        }
    }

    /**
     * 设置点击监听器
     */
    private void setupClickListeners() {
        btnDatabaseDemo.setOnClickListener(this);
        btnTableManagement.setOnClickListener(this);
        btnDataExport.setOnClickListener(this);
        btnOptimization.setOnClickListener(this);
        
        if (btnAdvancedTableMgmt != null) {
            btnAdvancedTableMgmt.setOnClickListener(this);
        }
        
        if (btnClearMainLog != null) {
            btnClearMainLog.setOnClickListener(this);
        }
        if (btnViewLogFile != null) {
            btnViewLogFile.setOnClickListener(this);
        }
    }

    @Override
    public void onClick(View v) {
        Intent intent = null;
        int id = v.getId();
        
        if (id == R.id.btn_database_demo) {
            // 跳转到数据库操作演示界面
            intent = new Intent(this, DatabaseDemoActivity.class);
        } else if (id == R.id.btn_table_management) {
            // 跳转到表管理演示界面
            intent = new Intent(this, TableManagementActivity.class);
        } else if (id == R.id.btn_data_export) {
            // 跳转到数据导出演示界面
            intent = new Intent(this, DataExportActivity.class);
        } else if (id == R.id.btn_optimization) {
            // 跳转到数据库优化界面
            intent = new Intent(this, DatabaseOptimizationActivity.class);
        } else if (id == R.id.btn_advanced_table_mgmt) {
            // 跳转到高级表管理界面
            intent = new Intent(this, AdvancedTableManagementActivity.class);
        } else if (id == R.id.btn_clear_main_log) {
            clearLog();
            return;
        } else if (id == R.id.btn_view_log_file) {
            showLogFilePath();
            return;
        }
        
        try {
            if (intent != null) {
                startActivity(intent);
                AppLogger.i("Main", "navigate: " + intent.getComponent());
            }
        } catch (Throwable e) {
            AppLogger.e("Main", "Navigation error: " + e.getMessage());
        }
    }

    /**
     * 清空日志
     */
    private void clearLog() {
        new AlertDialog.Builder(this)
                .setTitle("清空日志")
                .setMessage("确定要清空所有日志吗？")
                .setPositiveButton("确定", (dialog, which) -> {
                    AppLogger.clear();
                    if (tvMainLog != null) {
                        tvMainLog.setText("日志已清空\n");
                    }
                    Toast.makeText(this, "日志已清空", Toast.LENGTH_SHORT).show();
                    AppLogger.i("Main", "User cleared all logs");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 显示完整日志
     */
    private void showLogFilePath() {
        File logFile = AppLogger.getLogFile();
        if (logFile != null) {
            String path = logFile.getAbsolutePath();
            long fileSize = logFile.exists() ? logFile.length() : 0;
            
            new AlertDialog.Builder(this)
                    .setTitle("完整日志")
                    .setMessage("文件路径: " + path + "\n文件大小: " + formatFileSize(fileSize))
                    .setPositiveButton("查看日志", (dialog, which) -> {
                        showFullLogContent();
                    })
                    .setNeutralButton("复制路径", (dialog, which) -> {
                        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("日志路径", path);
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(this, "路径已复制到剪贴板", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("关闭", null)
                    .show();
        } else {
            Toast.makeText(this, "日志文件未初始化", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 显示完整日志内容
     */
    private void showFullLogContent() {
        new Thread(() -> {
            String fullLog = AppLogger.readFullLogFile();
            runOnUiThread(() -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("完整日志内容");
                
                // 创建一个ScrollView和TextView来显示日志
                android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
                TextView textView = new TextView(this);
                textView.setText(fullLog);
                textView.setTextSize(10);
                textView.setTypeface(android.graphics.Typeface.MONOSPACE);
                textView.setPadding(20, 20, 20, 20);
                scrollView.addView(textView);
                
                builder.setView(scrollView);
                builder.setPositiveButton("复制全部", (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("完整日志", fullLog);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(this, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show();
                });
                builder.setNegativeButton("关闭", null);
                builder.show();
            });
        }).start();
    }

    /**
     * 格式化文件大小
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
    }
}
