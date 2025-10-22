package com.orange.gamesavemanager2;

import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;

import game.core.DBCipherManager;
import game.core.DatabaseConfig;
import game.core.SqlUtilManager;
import net.sqlcipher.database.SQLiteDatabase;

/**
 * 数据导出演示Activity - 展示数据库导出为JSON和明文DB功能
 */
public class DataExportActivity extends AppCompatActivity {

    private TextView tvContent;
    private Button btnExportJson;
    private Button btnExportPlainDb;
    private DBCipherManager dbManager;
    private SqlUtilManager sqlUtilManager;
    private String currentDbName;
    private String currentPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
        setContentView(R.layout.activity_data_export);
        
            tvContent = findViewById(R.id.tv_content);
            btnExportJson = findViewById(R.id.btn_export_json);
            btnExportPlainDb = findViewById(R.id.btn_export_plain_db);
            
            if (tvContent == null) {
                tvContent = new TextView(this);
                setContentView(tvContent);
            }
            
            initDatabase();
            setupButtons();
            
            // 获取当前数据库信息
            android.content.SharedPreferences prefs = getSharedPreferences("db_config", MODE_PRIVATE);
            String dbName = prefs.getString("db_name", "demo_database");
            
            tvContent.setText("当前数据库: " + dbName + "\n\n" +
                    "请选择导出方式:\n\n" +
                    "• 导出JSON：将数据库导出为JSON格式\n" +
                    "• 导出明文DB：导出未加密的SQLite数据库文件\n\n" +
                    "提示: 如果显示0个表，请先在'数据库操作演示'中创建数据库和表\n");
            
            Toast.makeText(this, "数据导出演示功能", Toast.LENGTH_SHORT).show();
            AppLogger.i("DataExport", "page_open");
        } catch (Exception e) {
            AppLogger.e("DataExport", "onCreate error: " + e.getMessage());
            Toast.makeText(this, "初始化失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void setupButtons() {
        if (btnExportJson != null) {
            btnExportJson.setOnClickListener(v -> exportAllData());
        }
        if (btnExportPlainDb != null) {
            btnExportPlainDb.setOnClickListener(v -> exportPlainDatabase());
        }
    }

    private void initDatabase() {
        try {
            // 从SharedPreferences读取数据库配置
            android.content.SharedPreferences prefs = getSharedPreferences("db_config", MODE_PRIVATE);
            currentDbName = prefs.getString("db_name", "demo_database");
            currentPassword = prefs.getString("db_password", "demo123");
            
            DatabaseConfig config = new DatabaseConfig.Builder()
                    .setDatabaseName(currentDbName)
                    .setPassword(currentPassword)
                    .setVersion(1)
                    .build();
            
            dbManager = DBCipherManager.getInstance(this, config);
            sqlUtilManager = dbManager.getSqlUtilManager();
            
            AppLogger.d("DataExport", "使用数据库: " + currentDbName);
        } catch (Exception e) {
            AppLogger.e("DataExport", "init error: " + e.getMessage());
        }
    }

    private void exportAllData() {
        new Thread(() -> {
            try {
                if (sqlUtilManager == null) {
                    appendLog("错误: SQL工具管理器未初始化\n");
                    return;
                }
                
                appendLog("=== 数据库完整导出 ===\n\n");
                
                // 导出整个数据库为JSON
                JSONObject databaseJson = sqlUtilManager.exportDatabaseToJson();
                
                if (databaseJson != null && databaseJson.length() > 0) {
                    appendLog("导出成功!\n");
                    appendLog("数据库包含 " + databaseJson.length() + " 个表\n\n");
                    
                    // 格式化显示JSON
                    String formattedJson = databaseJson.toString(2);
                    
                    // 如果JSON太长，只显示前2000字符
                    if (formattedJson.length() > 2000) {
                        appendLog("JSON数据 (前2000字符):\n");
                        appendLog(formattedJson.substring(0, 2000));
                        appendLog("\n\n... (数据已截断，总长度: " + formattedJson.length() + " 字符)\n");
                    } else {
                        appendLog("完整JSON数据:\n");
                        appendLog(formattedJson);
                    }
                } else {
                    appendLog("数据库中暂无数据\n");
                    appendLog("\n提示: 请先在'数据库操作演示'中创建表并插入数据\n");
                }
                
                AppLogger.i("DataExport", "export_complete");
                
            } catch (Exception e) {
                appendLog("\n导出数据失败: " + e.getMessage() + "\n");
                AppLogger.e("DataExport", "exportAllData error: " + e.getMessage());
            }
        }).start();
    }

    /**
     * 导出明文（未加密）数据库
     */
    private void exportPlainDatabase() {
        new Thread(() -> {
            try {
                clearLog();
                appendLog("=== 导出明文数据库 ===\n\n");
                
                if (dbManager == null) {
                    appendLog("错误: 数据库管理器未初始化\n");
                    return;
                }
                
                // 获取加密数据库文件路径
                File encryptedDbFile = getDatabasePath(currentDbName);
                if (!encryptedDbFile.exists()) {
                    appendLog("错误: 数据库文件不存在\n");
                    return;
                }
                
                appendLog("源数据库: " + encryptedDbFile.getAbsolutePath() + "\n");
                appendLog("数据库大小: " + formatFileSize(encryptedDbFile.length()) + "\n\n");
                
                // 导出路径（使用应用私有目录）
                File exportDir = new File(getExternalFilesDir(null), "exports");
                if (!exportDir.exists()) {
                    boolean created = exportDir.mkdirs();
                    appendLog("创建导出目录: " + (created ? "成功" : "失败") + "\n");
                }
                
                File plainDbFile = new File(exportDir, currentDbName + "_plain.db");
                
                // 如果文件已存在，先删除
                if (plainDbFile.exists()) {
                    appendLog("检测到已存在的文件，正在删除...\n");
                    boolean deleted = plainDbFile.delete();
                    if (!deleted) {
                        appendLog("警告: 删除旧文件失败\n");
                    }
                }
                
                appendLog("导出目标: " + plainDbFile.getAbsolutePath() + "\n\n");
                
                // 方法：手动读取数据并写入明文数据库
                appendLog("正在准备导出...\n");
                
                // 打开加密数据库（源）
                appendLog("正在打开加密数据库...\n");
                net.sqlcipher.database.SQLiteDatabase sourceDb = 
                    net.sqlcipher.database.SQLiteDatabase.openDatabase(
                        encryptedDbFile.getAbsolutePath(),
                        currentPassword,
                        null,
                        net.sqlcipher.database.SQLiteDatabase.OPEN_READONLY
                    );
                
                // 创建明文数据库（目标）
                appendLog("正在创建明文数据库...\n");
                android.database.sqlite.SQLiteDatabase targetDb = 
                    android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
                        plainDbFile,
                        null
                    );
                
                int tableCount = 0;
                int totalRows = 0;
                
                try {
                    // 开始事务
                    targetDb.beginTransaction();
                    
                    // 获取所有表
                    appendLog("正在读取表结构...\n");
                    android.database.Cursor tablesCursor = sourceDb.rawQuery(
                        "SELECT name, sql FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'",
                        null
                    );
                    
                    while (tablesCursor.moveToNext()) {
                        String tableName = tablesCursor.getString(0);
                        String createSql = tablesCursor.getString(1);
                        
                        appendLog("  - 表: " + tableName + "\n");
                        
                        // 创建表
                        targetDb.execSQL(createSql);
                        
                        // 复制数据
                        android.database.Cursor dataCursor = sourceDb.rawQuery(
                            "SELECT * FROM " + tableName,
                            null
                        );
                        
                        int rowCount = 0;
                        while (dataCursor.moveToNext()) {
                            StringBuilder columns = new StringBuilder();
                            StringBuilder values = new StringBuilder();
                            
                            for (int i = 0; i < dataCursor.getColumnCount(); i++) {
                                if (i > 0) {
                                    columns.append(", ");
                                    values.append(", ");
                                }
                                columns.append(dataCursor.getColumnName(i));
                                
                                int type = dataCursor.getType(i);
                                if (type == android.database.Cursor.FIELD_TYPE_NULL) {
                                    values.append("NULL");
                                } else if (type == android.database.Cursor.FIELD_TYPE_INTEGER) {
                                    values.append(dataCursor.getLong(i));
                                } else if (type == android.database.Cursor.FIELD_TYPE_FLOAT) {
                                    values.append(dataCursor.getDouble(i));
                                } else {
                                    values.append("'").append(
                                        dataCursor.getString(i).replace("'", "''")
                                    ).append("'");
                                }
                            }
                            
                            String insertSql = String.format("INSERT INTO %s (%s) VALUES (%s)",
                                tableName, columns.toString(), values.toString());
                            targetDb.execSQL(insertSql);
                            rowCount++;
                        }
                        dataCursor.close();
                        
                        appendLog("    复制了 " + rowCount + " 行数据\n");
                        tableCount++;
                        totalRows += rowCount;
                    }
                    tablesCursor.close();
                    
                    // 复制索引
                    appendLog("正在复制索引...\n");
                    android.database.Cursor indexCursor = sourceDb.rawQuery(
                        "SELECT sql FROM sqlite_master WHERE type='index' AND sql IS NOT NULL",
                        null
                    );
                    int indexCount = 0;
                    while (indexCursor.moveToNext()) {
                        try {
                            targetDb.execSQL(indexCursor.getString(0));
                            indexCount++;
                        } catch (Exception e) {
                            // 某些索引可能自动创建，忽略错误
                        }
                    }
                    indexCursor.close();
                    
                    targetDb.setTransactionSuccessful();
                    appendLog("\n成功复制 " + tableCount + " 个表，共 " + totalRows + " 行数据\n");
                    appendLog("复制了 " + indexCount + " 个索引\n");
                    
                } finally {
                    if (targetDb.inTransaction()) {
                        targetDb.endTransaction();
                    }
                    targetDb.close();
                    sourceDb.close();
                    appendLog("已关闭所有数据库连接\n");
                }
                
                if (tableCount == 0) {
                    appendLog("\n⚠ 警告: 数据库中没有用户表!\n\n");
                    appendLog("可能的原因:\n");
                    appendLog("1. 数据库是新创建的，还没有添加表\n");
                    appendLog("2. 您使用的是默认数据库 'demo_database'，但数据在其他数据库中\n\n");
                    appendLog("解决方法:\n");
                    appendLog("• 在'数据库操作演示'中创建数据库和表\n");
                    appendLog("• 确保创建表和导出使用的是同一个数据库\n\n");
                } else {
                    appendLog("✓ 导出成功!\n\n");
                }
                
                appendLog("明文数据库路径:\n" + plainDbFile.getAbsolutePath() + "\n\n");
                appendLog("文件大小: " + formatFileSize(plainDbFile.length()) + "\n\n");
                
                if (tableCount > 0) {
                    appendLog("注意: 此文件没有加密保护，请妥善保管！\n");
                }
                
                AppLogger.i("DataExport", "导出明文数据库: " + tableCount + " 个表, " + totalRows + " 行数据");
                
                final int finalTableCount = tableCount;
                runOnUiThread(() -> {
                    if (finalTableCount == 0) {
                        Toast.makeText(this, "导出完成，但数据库为空", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "导出成功: " + finalTableCount + " 个表", Toast.LENGTH_LONG).show();
                    }
                });
                
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                if (errorMsg == null) {
                    errorMsg = e.getClass().getSimpleName();
                }
                
                appendLog("\n✗ 导出失败!\n\n");
                appendLog("错误信息: " + errorMsg + "\n\n");
                
                // 提供常见错误的解决方案
                if (errorMsg.contains("unable to open database")) {
                    appendLog("可能的原因:\n");
                    appendLog("1. 目标文件已存在且被占用\n");
                    appendLog("2. 没有写入权限\n");
                    appendLog("3. 磁盘空间不足\n\n");
                    appendLog("建议: 重新尝试导出\n");
                } else if (errorMsg.contains("password")) {
                    appendLog("可能的原因: 数据库密码错误\n");
                } else if (errorMsg.contains("not a database")) {
                    appendLog("可能的原因: 数据库文件已损坏\n");
                }
                
                // 记录完整堆栈信息到日志
                AppLogger.e("DataExport", "exportPlainDatabase error: " + errorMsg);
                java.io.StringWriter sw = new java.io.StringWriter();
                e.printStackTrace(new java.io.PrintWriter(sw));
                AppLogger.e("DataExport", "Stack trace: " + sw.toString());
                
                runOnUiThread(() -> 
                    Toast.makeText(this, "导出失败，请查看详情", Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }
    
    private void clearLog() {
        runOnUiThread(() -> {
            if (tvContent != null) {
                tvContent.setText("");
            }
        });
    }

    private void appendLog(String message) {
        if (tvContent != null) {
            runOnUiThread(() -> tvContent.append(message));
        }
    }
    
    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
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
