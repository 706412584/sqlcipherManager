package com.orange.gamesavemanager2;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.Locale;

import game.core.DBCipherManager;
import game.core.DatabaseConfig;
import net.sqlcipher.database.SQLiteDatabase;

/**
 * 数据库优化Activity - 展示数据库维护和优化功能
 */
public class DatabaseOptimizationActivity extends AppCompatActivity {

    private TextView tvContent;
    private DBCipherManager dbManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            setContentView(R.layout.activity_optimization);
            
            tvContent = findViewById(R.id.tv_content);
            if (tvContent == null) {
                tvContent = new TextView(this);
                setContentView(tvContent);
            }
            
            initDatabase();
            performOptimization();
            
            Toast.makeText(this, "数据库优化功能", Toast.LENGTH_SHORT).show();
            AppLogger.i("Optimization", "page_open");
        } catch (Exception e) {
            AppLogger.e("Optimization", "onCreate error: " + e.getMessage());
            Toast.makeText(this, "初始化失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void initDatabase() {
        try {
            // 从SharedPreferences读取数据库配置
            android.content.SharedPreferences prefs = getSharedPreferences("db_config", MODE_PRIVATE);
            String dbName = prefs.getString("db_name", "demo_database");
            String password = prefs.getString("db_password", "demo123");
            
            DatabaseConfig config = new DatabaseConfig.Builder()
                    .setDatabaseName(dbName)
                    .setPassword(password)
                    .setVersion(1)
                    .build();
            
            dbManager = DBCipherManager.getInstance(this, config);
            
            appendLog("数据库管理器初始化成功\n");
            appendLog("当前数据库: " + dbName + "\n\n");
            AppLogger.d("Optimization", "使用数据库: " + dbName);
        } catch (Exception e) {
            appendLog("数据库管理器初始化失败: " + e.getMessage() + "\n");
            AppLogger.e("Optimization", "init error: " + e.getMessage());
        }
    }

    private void performOptimization() {
        new Thread(() -> {
            try {
                if (dbManager == null) {
                    appendLog("错误: 数据库管理器未初始化\n");
                    return;
                }
                
                appendLog("=== 数据库优化分析 ===\n\n");
                
                // 1. 显示数据库文件大小
                File dbFile = getDatabasePath("demo_database");
                if (dbFile.exists()) {
                    long sizeInBytes = dbFile.length();
                    String sizeStr = formatFileSize(sizeInBytes);
                    appendLog("数据库文件大小: " + sizeStr + "\n");
                    appendLog("文件路径: " + dbFile.getAbsolutePath() + "\n\n");
                } else {
                    appendLog("数据库文件不存在\n\n");
                }
                
                // 2. 执行VACUUM优化
                appendLog("正在执行VACUUM优化...\n");
                boolean vacuumSuccess = dbManager.executeWithConnection((SQLiteDatabase db) -> {
                    db.execSQL("VACUUM");
                    return true;
                });
                
                if (vacuumSuccess) {
                    appendLog("✓ VACUUM优化完成\n");
                } else {
                    appendLog("✗ VACUUM优化失败\n");
                }
                
                // 3. 执行ANALYZE分析
                appendLog("\n正在执行ANALYZE分析...\n");
                boolean analyzeSuccess = dbManager.executeWithConnection((SQLiteDatabase db) -> {
                    db.execSQL("ANALYZE");
                    return true;
                });
                
                if (analyzeSuccess) {
                    appendLog("✓ ANALYZE分析完成\n");
                } else {
                    appendLog("✗ ANALYZE分析失败\n");
                }
                
                // 4. 显示优化后的文件大小
                if (dbFile.exists()) {
                    long newSizeInBytes = dbFile.length();
                    String newSizeStr = formatFileSize(newSizeInBytes);
                    appendLog("\n优化后文件大小: " + newSizeStr + "\n");
                }
                
                appendLog("\n=== 优化建议 ===\n");
                appendLog("• 定期执行VACUUM以回收空间\n");
                appendLog("• 为常用查询字段创建索引\n");
                appendLog("• 避免在主线程执行数据库操作\n");
                appendLog("• 使用事务批量插入数据\n");
                
                AppLogger.i("Optimization", "optimization_complete");
                
            } catch (Exception e) {
                appendLog("\n优化失败: " + e.getMessage() + "\n");
                AppLogger.e("Optimization", "performOptimization error: " + e.getMessage());
            }
        }).start();
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.2f KB", bytes / 1024.0);
        } else {
            return String.format(Locale.getDefault(), "%.2f MB", bytes / (1024.0 * 1024.0));
        }
    }

    private void appendLog(String message) {
        if (tvContent != null) {
            runOnUiThread(() -> tvContent.append(message));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbManager != null) {
            dbManager.closeAllConnections();
        }
    }
}
